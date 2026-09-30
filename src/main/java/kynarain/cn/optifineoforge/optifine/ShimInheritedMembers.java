/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.optifine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Gives the Forge API shims the members the payload expects to inherit from them.
 *
 * <p>A shim is generated from the references the payload makes <em>to</em> a Forge type, and that misses
 * one whole kind of reference: a member the payload inherits rather than names. OptiFine's patch makes
 * {@code com.mojang.blaze3d.textures.GpuTexture} implement the Forge extension interface
 * {@code net.minecraftforge.client.extensions.IForgeGpuTexture}, and its subclasses implement
 * {@code isStencilEnabled()} - a method that exists nowhere else, because on Forge it is declared by that
 * interface. Nothing in the payload names the interface, so no shim generator can see it, and the call
 * site resolves against the class:</p>
 *
 * <pre>
 * java.lang.NoSuchMethodError: 'boolean com.mojang.blaze3d.textures.GpuTexture.isStencilEnabled()'
 *   at com.mojang.blaze3d.opengl.GlCommandEncoder.clearColorTexture(GlCommandEncoder.java:159)
 * </pre>
 *
 * <p>So this walks every method reference the payload makes, resolves it against the payload, the runtime
 * and the shims - hierarchy included, which is the part that matters - and where a reference cannot be
 * resolved at all <em>and</em> the owning type's hierarchy reaches a shim interface, writes the missing
 * member as a default method on that interface. A default method is the right repair rather than an
 * abstract one: the classes that care already override it, and a class that does not keeps the value the
 * stub returns instead of failing to link.</p>
 *
 * <p>Development aid:
 * {@code ShimInheritedMembers <payload jar> <shims dir> <out dir> <runtime jar> [more runtime jars...]}.
 * Shims it changes are written to the output directory, unchanged ones are copied, and everything it could
 * not resolve is printed.</p>
 */
public final class ShimInheritedMembers {
	/** Where the patched game classes sit in the payload jar, as the other tools also read it. */
	private static final String PATCHED_ROOT = "srg/";

	private ShimInheritedMembers() {
	}

	/** What one unresolved reference asked for. */
	private record Missing(String owner, String name, String desc, boolean field, String firstSeen) {
	}

	/**
	 * @return one line per shim it changed, plus what it could not resolve
	 */
	public static List<String> fill(Path payload, Path shimsDir, Path outDir, List<Path> runtimeJars) throws IOException {
		Map<String, ClassNode> index = new TreeMap<>();
		Set<String> shimNames = new LinkedHashSet<>();
		Set<String> namelessShims = new LinkedHashSet<>();
		// The payload first: its copy of a class is the one that will be loaded, and therefore the one
		// whose supertypes the call sites resolve against.
		Map<String, Path> payloadEntries = new LinkedHashMap<>();
		try(ZipFile zip = new ZipFile(payload.toFile())) {
			for(Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
				ZipEntry entry = it.nextElement();
				if(entry.isDirectory() || !entry.getName().startsWith(PATCHED_ROOT)
						|| !entry.getName().endsWith(".class")) {
					continue;
				}
				String internal = entry.getName().substring(PATCHED_ROOT.length(),
						entry.getName().length() - ".class".length());
				index.put(internal, read(new ClassReader(readAll(zip.getInputStream(entry)))));
				payloadEntries.put(internal, null);
			}
		}
		for(Path jar : runtimeJars) {
			try(ZipFile zip = new ZipFile(jar.toFile())) {
				for(Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
					ZipEntry entry = it.nextElement();
					if(entry.isDirectory() || !entry.getName().endsWith(".class")) {
						continue;
					}
					String internal = entry.getName().substring(0, entry.getName().length() - ".class".length());
					if(index.containsKey(internal)) {
						continue;
					}
					index.put(internal, read(new ClassReader(readAll(zip.getInputStream(entry)))));
				}
			}
		}
		for(Path file : Files.walk(shimsDir).filter(Files::isRegularFile).toList()) {
			if(!file.getFileName().toString().endsWith(".class")) {
				continue;
			}
			String internal = shimsDir.relativize(file).toString().replace('\\', '/');
			internal = internal.substring(0, internal.length() - ".class".length());
			// A file literally called ".class" yields a name ending in '/', which is a directory and not
			// a class. It is produced by the shim generator when a referenced type comes out with an
			// empty simple name, and resolving it here threw
			//   NoSuchFileException: ...\forge-shims-filled\net\minecraftforge\client\event\.class
			// which aborted the whole pass and left the payload with a single shim instead of 57. Skipped
			// and counted rather than silently dropped: the generator's own report is where that name
			// has to be fixed, and a skip that says nothing would hide it.
			if(internal.isEmpty() || internal.endsWith("/")) {
				namelessShims.add(internal);
				continue;
			}
			index.put(internal, read(new ClassReader(Files.readAllBytes(file))));
			shimNames.add(internal);
		}
		if(!namelessShims.isEmpty()) {
			System.out.println("skipped " + namelessShims.size() + " shim file(s) whose name is not a class: "
					+ namelessShims);
		}

		// What the payload asks for and neither the payload, the runtime nor the shims can answer.
		Map<String, Missing> missing = new LinkedHashMap<>();
		Set<String> payloadClassNames = new LinkedHashSet<>(payloadEntries.keySet());
		for(String internal : payloadClassNames) {
			ClassNode node = index.get(internal);
			for(MethodNode method : node.methods) {
				if(method.instructions == null) {
					continue;
				}
				for(AbstractInsnNode insn : method.instructions) {
					if(insn instanceof MethodInsnNode call) {
						if(call.getOpcode() == Opcodes.INVOKEDYNAMIC || resolves(call.owner, call.name, call.desc, index)) {
							continue;
						}
						missing.putIfAbsent(call.owner + "." + call.name + call.desc,
								new Missing(call.owner, call.name, call.desc, false, internal));
					} else if(insn instanceof FieldInsnNode field) {
						if(resolvesField(field.owner, field.name, field.desc, index)) {
							continue;
						}
						missing.putIfAbsent(field.owner + "." + field.name + ":" + field.desc,
								new Missing(field.owner, field.name, field.desc, true, internal));
					}
				}
			}
		}

		// Which shim interface each unresolved reference resolves through, and what to add to it.
		Map<String, Set<String>> additions = new TreeMap<>();
		List<String> report = new ArrayList<>();
		for(Missing entry : missing.values()) {
			if(entry.field()) {
				report.add("  unresolved field " + entry.owner() + "." + entry.name() + " " + entry.desc()
						+ " (from " + entry.firstSeen() + ") - not a shim method, left alone");
				continue;
			}
			String shim = firstShimInHierarchy(entry.owner(), index, shimNames);
			if(shim == null) {
				report.add("  unresolved " + entry.owner() + "." + entry.name() + entry.desc()
						+ " (from " + entry.firstSeen() + ") - its hierarchy reaches no shim");
				continue;
			}
			additions.computeIfAbsent(shim, key -> new LinkedHashSet<>()).add(entry.name() + entry.desc());
		}

		Files.createDirectories(outDir);
		int written = 0;
		for(String shim : shimNames) {
			ClassNode node = index.get(shim);
			Set<String> wanted = additions.get(shim);
			boolean changed = false;
			if(wanted != null && (node.access & Opcodes.ACC_INTERFACE) != 0) {
				for(String signature : wanted) {
					int split = signature.indexOf('(');
					String name = signature.substring(0, split);
					String desc = signature.substring(split);
					if(hasMethod(node, name, desc)) {
						continue;
					}
					node.methods.add(defaultMethod(node, name, desc));
					changed = true;
				}
			} else if(wanted != null) {
				report.add("  " + shim.replace('/', '.') + " is not an interface; " + wanted.size()
						+ " member(s) the payload expects from it were not written");
			}
			// Rewritten either way: an untouched class written back through ASM is still the same class,
			// and one code path is one thing to get wrong.
			Files.createDirectories(outDir.resolve(shim).getParent());
			Files.write(outDir.resolve(shim + ".class"), write(node));
			written++;
			if(changed) {
				report.add("  " + shim.replace('/', '.') + ": added " + wanted.size() + " default method(s)");
			}
		}
		report.add("walked " + payloadClassNames.size() + " payload class(es), " + missing.size()
				+ " unresolved reference(s), " + additions.size() + " shim(s) extended, " + written
				+ " shim class(es) written to " + outDir);
		return report;
	}

	/** A default method returning the default value of its return type. */
	private static MethodNode defaultMethod(ClassNode owner, String name, String desc) {
		MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, desc, null, null);
		InsnList body = method.instructions;
		Type result = Type.getReturnType(desc);
		switch(result.getSort()) {
			case Type.VOID -> body.add(new InsnNode(Opcodes.RETURN));
			case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT ->
					body.add(new InsnNode(Opcodes.ICONST_0));
			case Type.LONG -> body.add(new InsnNode(Opcodes.LCONST_0));
			case Type.FLOAT -> body.add(new InsnNode(Opcodes.FCONST_0));
			case Type.DOUBLE -> body.add(new InsnNode(Opcodes.DCONST_0));
			default -> body.add(new InsnNode(Opcodes.ACONST_NULL));
		}
		switch(result.getSort()) {
			case Type.VOID -> {
			}
			case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> body.add(new InsnNode(Opcodes.IRETURN));
			case Type.LONG -> body.add(new InsnNode(Opcodes.LRETURN));
			case Type.FLOAT -> body.add(new InsnNode(Opcodes.FRETURN));
			case Type.DOUBLE -> body.add(new InsnNode(Opcodes.DRETURN));
			default -> body.add(new InsnNode(Opcodes.ARETURN));
		}
		method.maxStack = result.getSize();
		method.maxLocals = 1;
		for(Type argument : Type.getArgumentTypes(desc)) {
			method.maxLocals += argument.getSize();
		}
		return method;
	}

	private static byte[] write(ClassNode node) {
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		node.accept(writer);
		return writer.toByteArray();
	}

	private static ClassNode read(ClassReader reader) {
		ClassNode node = new ClassNode();
		reader.accept(node, ClassReader.SKIP_FRAMES);
		return node;
	}

	/** Whether the member exists on the type or anywhere above it. */
	private static boolean resolves(String owner, String name, String desc, Map<String, ClassNode> index) {
		return walk(owner, name, desc, index, new LinkedHashSet<>());
	}

	private static boolean walk(String owner, String name, String desc, Map<String, ClassNode> index,
			Set<String> visited) {
		if(owner == null || !visited.add(owner)) {
			return false;
		}
		ClassNode node = index.get(owner);
		if(node == null) {
			// A type outside everything handed to this tool - java.lang.Object at the end of every walk,
			// or a library this build does not index. Nothing can be concluded about its members, so the
			// answer is "not found": reporting a reference that really is satisfied costs a line in a log,
			// while assuming it is satisfied is what hid the one member this tool was written for.
			return false;
		}
		if(hasMethod(node, name, desc)) {
			return true;
		}
		if(walk(node.superName, name, desc, index, visited)) {
			return true;
		}
		if(node.interfaces != null) {
			for(String parent : node.interfaces) {
				if(walk(parent, name, desc, index, visited)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean resolvesField(String owner, String name, String desc, Map<String, ClassNode> index) {
		return walkField(owner, name, desc, index, new LinkedHashSet<>());
	}

	private static boolean walkField(String owner, String name, String desc, Map<String, ClassNode> index,
			Set<String> visited) {
		if(owner == null || !visited.add(owner)) {
			return false;
		}
		ClassNode node = index.get(owner);
		if(node == null) {
			return false;
		}
		for(org.objectweb.asm.tree.FieldNode field : node.fields) {
			if(field.name.equals(name) && field.desc.equals(desc)) {
				return true;
			}
		}
		if(walkField(node.superName, name, desc, index, visited)) {
			return true;
		}
		if(node.interfaces != null) {
			for(String parent : node.interfaces) {
				if(walkField(parent, name, desc, index, visited)) {
					return true;
				}
			}
		}
		return false;
	}

	/** The first shim interface on the type's hierarchy, or null when it reaches none. */
	private static String firstShimInHierarchy(String owner, Map<String, ClassNode> index, Set<String> shimNames) {
		Set<String> visited = new LinkedHashSet<>();
		List<String> queue = new ArrayList<>();
		queue.add(owner);
		while(!queue.isEmpty()) {
			String current = queue.remove(0);
			if(current == null || !visited.add(current)) {
				continue;
			}
			ClassNode node = index.get(current);
			if(node == null) {
				continue;
			}
			if(shimNames.contains(current) && (node.access & Opcodes.ACC_INTERFACE) != 0) {
				return current;
			}
			queue.add(node.superName);
			if(node.interfaces != null) {
				queue.addAll(node.interfaces);
			}
		}
		return null;
	}

	private static boolean hasMethod(ClassNode node, String name, String desc) {
		for(MethodNode method : node.methods) {
			if(method.name.equals(name) && method.desc.equals(desc)) {
				return true;
			}
		}
		return false;
	}

	private static byte[] readAll(InputStream stream) throws IOException {
		try(stream) {
			return stream.readAllBytes();
		}
	}

	public static void main(String[] args) throws IOException {
		if(args.length < 4) {
			System.err.println("usage: ShimInheritedMembers <payload jar> <shims dir> <out dir> <runtime jar>"
					+ " [more runtime jars...]");
			System.exit(2);
		}
		List<Path> runtime = new ArrayList<>();
		for(int index = 3; index < args.length; index++) {
			runtime.add(Path.of(args[index]));
		}
		fill(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), runtime).forEach(System.out::println);
	}
}
