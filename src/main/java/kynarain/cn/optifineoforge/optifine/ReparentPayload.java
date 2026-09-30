/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.optifine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Rewrites a patched payload's hierarchies offline, where the other lines do it while the class loads.
 *
 * <p>OptiFine's copy of a game class is compiled against Forge, so where the class it replaces extends a
 * Forge API type its own copy extends that type too - on 26.1.2 the runtime's {@code BlockEntity} extends
 * {@code net.neoforged.neoforge.attachment.AttachmentHolder} while OptiFine's extends
 * {@code net.minecraftforge.common.capabilities.CapabilityProvider$BlockEntities}, an API NeoForge no
 * longer has at all. Installing OptiFine's copy then breaks NeoForge's own call sites, and it is measured
 * rather than feared:</p>
 *
 * <pre>
 * java.lang.VerifyError: Bad type on operand stack
 *   Location: net/neoforged/neoforge/attachment/AttachmentSync.onChunkSent(...)V &#64;82: invokestatic
 *   Reason: Type 'net/minecraft/world/level/block/entity/BlockEntity' (current frame, stack[0]) is not
 *           assignable to 'net/neoforged/neoforge/attachment/AttachmentHolder'
 * </pre>
 *
 * <p>On the ModLauncher lines this is one transformer step, because the transformer holds both copies of
 * the class it is installing. FML 11 has no place for that step here - OptiFine's own {@code ClassProcessor}
 * installs the classes and this mod contributes nothing at runtime on this line - so the same decision,
 * made by {@link HierarchyPlan} against the runtime jars, is applied to the payload before it is shipped.
 * The rules are the ones the loader enforces there, and every one of them is checked rather than assumed;
 * a class that fails any check is left exactly as it is and reported, because the alternative is a launch
 * that dies inside the JVM's verifier with no mention of the class that caused it.</p>
 *
 * <p>Development aid: {@code ReparentPayload <payload jar> <plan> <out jar> [runtime jar...]}. The runtime
 * jars are optional and only used to re-check the plan against the hierarchy that will really be loaded.</p>
 */
public final class ReparentPayload {
	/** Where the patched game classes sit in the payload jar, as the other tools also read it. */
	private static final String PATCHED_ROOT = "srg/";
	/** The package Forge's API lives in, which is what a payload superclass has to come from to move. */
	private static final String FORGE_PACKAGE = "net/minecraftforge/";

	private ReparentPayload() {
	}

	/** The plan: class name to {runtime superclass, constructor descriptor to call}. */
	public static Map<String, String[]> readPlan(Path plan) throws IOException {
		Map<String, String[]> result = new LinkedHashMap<>();
		for(String line : Files.readString(plan, StandardCharsets.UTF_8).split("\\R")) {
			String[] parts = line.split("\t");
			if(parts.length == 4 && "reparent".equals(parts[0])) {
				result.put(parts[1], new String[] {parts[2], parts[3]});
			}
		}
		return result;
	}

	/**
	 * The payload with its planned hierarchies rewritten.
	 *
	 * @return one line per class, for the build log
	 */
	public static List<String> rewrite(Path payload, Path planFile, Path out, List<Path> runtimeJars) throws IOException {
		Map<String, String[]> plan = readPlan(planFile);
		Map<String, ClassNode> runtime = readRuntime(runtimeJars);
		List<String> report = new java.util.ArrayList<>();
		Path parent = out.toAbsolutePath().getParent();
		if(parent != null) {
			Files.createDirectories(parent);
		}
		int rewritten = 0;
		int refused = 0;
		try(ZipFile zip = new ZipFile(payload.toFile());
				ZipOutputStream target = new ZipOutputStream(Files.newOutputStream(out))) {
			for(Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
				ZipEntry entry = it.nextElement();
				String name = entry.getName();
				byte[] bytes;
				if(!entry.isDirectory() && name.startsWith(PATCHED_ROOT) && name.endsWith(".class")) {
					String internal = name.substring(PATCHED_ROOT.length(), name.length() - ".class".length());
					String[] planned = plan.get(internal);
					bytes = readAll(zip.getInputStream(entry));
					ClassNode runtimeNode = runtime.get(internal);
					if(planned != null || runtimeNode != null) {
						ClassNode node = new ClassNode();
						new ClassReader(bytes).accept(node, 0);
						boolean changed = false;
						if(planned != null) {
							String problem = apply(node, planned, runtimeNode);
							if(problem == null) {
								changed = true;
								rewritten++;
								report.add("  reparented " + internal.replace('/', '.') + " onto " + planned[0]
										+ " (super(" + planned[1] + "))");
							} else {
								refused++;
								report.add("  NOT reparented " + internal.replace('/', '.') + ": " + problem);
							}
						}
						List<String> kept = unionInterfaces(node, runtimeNode, runtime);
						if(!kept.isEmpty()) {
							changed = true;
							report.add("  kept the runtime's interface(s) on " + internal.replace('/', '.')
									+ ": " + String.join(", ", kept));
						}
						if(changed) {
							ClassWriter writer = new ClassWriter(0);
							node.accept(writer);
							bytes = writer.toByteArray();
						}
					}
				} else {
					bytes = entry.isDirectory() ? new byte[0] : readAll(zip.getInputStream(entry));
				}
				ZipEntry copy = new ZipEntry(name);
				copy.setTime(entry.getTime());
				target.putNextEntry(copy);
				if(!entry.isDirectory()) {
					target.write(bytes);
				}
				target.closeEntry();
			}
		}
		report.add("reparented " + rewritten + " class(es), refused " + refused
				+ ", plan had " + plan.size() + " entr(ies) -> " + out);
		return report;
	}

	/**
	 * The rewrite itself, with the same checks the loader makes on the ModLauncher lines.
	 *
	 * @return null on success, or why this class must not be moved
	 */
	static String apply(ClassNode node, String[] planned, ClassNode runtimeNode) {
		String plannedSuper = planned[0];
		String plannedCtor = planned[1];
		String forgeSuper = node.superName;
		if(forgeSuper == null || !forgeSuper.startsWith(FORGE_PACKAGE)) {
			return "the plan says to re-parent it but its copy extends " + forgeSuper + ", not a Forge type";
		}
		if(runtimeNode != null && !plannedSuper.equals(runtimeNode.superName)) {
			return "the plan re-parents it onto " + plannedSuper + " while the runtime's copy extends "
					+ runtimeNode.superName + ", so the plan and the runtime do not match";
		}
		int calls = 0;
		for(MethodNode method : node.methods) {
			if(!"<init>".equals(method.name)) {
				continue;
			}
			for(AbstractInsnNode instruction : method.instructions) {
				if(!(instruction instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESPECIAL
						|| !forgeSuper.equals(call.owner) || !"<init>".equals(call.name)) {
					continue;
				}
				if("()V".equals(plannedCtor)) {
					// The arguments are already on the stack and the runtime's superclass takes none, so
					// they are discarded rather than the pushes deleted: whatever the class evaluates for
					// them is still evaluated exactly as OptiFine wrote it.
					Type[] arguments = Type.getArgumentTypes(call.desc);
					for(int index = arguments.length - 1; index >= 0; index--) {
						method.instructions.insertBefore(call,
								new InsnNode(arguments[index].getSize() == 2 ? Opcodes.POP2 : Opcodes.POP));
					}
				} else if(!plannedCtor.equals(call.desc)) {
					return "the plan calls " + plannedCtor + " on the runtime's superclass while the payload's "
							+ "constructor calls " + call.desc + " on the Forge one, so the arguments do not "
							+ "line up";
				}
				call.owner = plannedSuper;
				call.desc = plannedCtor;
				call.itf = false;
				calls++;
			}
		}
		if(calls == 0) {
			return "the payload's copy of it extends " + forgeSuper + " and no constructor of it chains to "
					+ "that superclass, so the hierarchy cannot be rewritten";
		}
		String survivor = namesOldSuper(node, forgeSuper);
		if(survivor != null) {
			return "the payload's copy of it extends " + forgeSuper + " and its body still names that type ("
					+ survivor + "), which would not resolve afterwards";
		}
		node.superName = plannedSuper;
		if(node.signature != null && node.signature.contains(forgeSuper)) {
			// The generic signature names the superclass it was compiled against; a stale one is not a
			// verification problem, only a lie to anything that reads it, so it goes.
			node.signature = null;
		}
		return null;
	}

	/**
	 * Adds the interfaces the runtime's copy implements and the payload's does not.
	 *
	 * <p>Measured on 26.1.2, where NeoForge's own {@code ExtendedButton} died with
	 * {@code NoSuchMethodError: Font.ellipsize(FormattedText, int)}. Neither copy declares that method:
	 * the runtime's {@code Font implements net.neoforged.neoforge.client.extensions.IFontExtension}, whose
	 * default methods are the only place it exists, while OptiFine's copy implements
	 * {@code net.minecraftforge.client.extensions.IForgeFont} - the Forge interface, which this build
	 * answers with an empty shim. Installing OptiFine's copy therefore threw away the interface that
	 * carries the method NeoForge calls. The ModLauncher lines solved the same problem inside the
	 * transformer ("kept the runtime's 1 interface(s) on ..."); offline it belongs here, where both
	 * copies are at hand.</p>
	 *
	 * <p>Only interfaces whose abstract methods the payload already declares are added: adding one whose
	 * methods are missing would turn a clear NoSuchMethodError into an AbstractMethodError later. What
	 * that refuses is reported rather than dropped.</p>
	 *
	 * @return the interfaces added, for the build log
	 */
	static List<String> unionInterfaces(ClassNode node, ClassNode runtimeNode, Map<String, ClassNode> runtime) {
		List<String> added = new java.util.ArrayList<>();
		if(runtimeNode == null || runtimeNode.interfaces == null) {
			return added;
		}
		for(String name : runtimeNode.interfaces) {
			if(node.interfaces.contains(name)) {
				continue;
			}
			ClassNode itf = runtime.get(name);
			if(itf == null) {
				continue;
			}
			String missing = missingAbstract(node, itf, runtime, new java.util.LinkedHashSet<>());
			if(missing != null) {
				continue;
			}
			node.interfaces.add(name);
			added.add(name.replace('/', '.'));
		}
		return added;
	}

	/** The first abstract method of an interface (or its parents) the class does not declare. */
	private static String missingAbstract(ClassNode node, ClassNode itf, Map<String, ClassNode> runtime,
			Set<String> visited) {
		if(!visited.add(itf.name)) {
			return null;
		}
		for(MethodNode method : itf.methods) {
			if((method.access & Opcodes.ACC_ABSTRACT) == 0 || "<clinit>".equals(method.name)) {
				continue; // a default or static method is carried by the interface itself
			}
			if(findMethod(node, method.name, method.desc) == null) {
				return method.name + method.desc;
			}
		}
		for(String parent : itf.interfaces) {
			ClassNode parentNode = runtime.get(parent);
			if(parentNode != null) {
				String missing = missingAbstract(node, parentNode, runtime, visited);
				if(missing != null) {
					return missing;
				}
			}
		}
		return null;
	}

	private static MethodNode findMethod(ClassNode node, String name, String desc) {
		for(MethodNode method : node.methods) {
			if(method.name.equals(name) && method.desc.equals(desc)) {
				return method;
			}
		}
		return null;
	}

	/** The first place a class still names the old superclass, or null when it names it nowhere. */
	private static String namesOldSuper(ClassNode node, String internalName) {
		String fieldType = "L" + internalName + ";";
		for(MethodNode method : node.methods) {
			for(AbstractInsnNode instruction : method.instructions) {
				if(instruction instanceof MethodInsnNode call && (internalName.equals(call.owner)
						|| call.desc.contains(fieldType))) {
					return "a call to " + call.name + call.desc;
				}
				if(instruction instanceof FieldInsnNode field && (internalName.equals(field.owner)
						|| field.desc.contains(fieldType))) {
					return "a read of " + field.name;
				}
			}
		}
		return null;
	}

	/** The runtime classes, by internal name, from the jars the build compares against. */
	static Map<String, ClassNode> readRuntime(List<Path> jars) throws IOException {
		Map<String, ClassNode> result = new TreeMap<>();
		Set<String> seen = new LinkedHashSet<>();
		for(Path jar : jars) {
			try(ZipFile zip = new ZipFile(jar.toFile())) {
				for(Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
					ZipEntry entry = it.nextElement();
					if(entry.isDirectory() || !entry.getName().endsWith(".class")) {
						continue;
					}
					String internal = entry.getName().substring(0, entry.getName().length() - ".class".length());
					if(!seen.add(internal)) {
						continue;
					}
					ClassNode node = new ClassNode();
					new ClassReader(readAll(zip.getInputStream(entry))).accept(node, ClassReader.SKIP_CODE);
					result.put(internal, node);
				}
			}
		}
		return result;
	}

	private static byte[] readAll(InputStream stream) throws IOException {
		try(stream) {
			return stream.readAllBytes();
		}
	}

	public static void main(String[] args) throws IOException {
		if(args.length < 3) {
			System.err.println("usage: ReparentPayload <payload jar> <plan> <out jar> [runtime jar...]");
			System.exit(2);
		}
		List<Path> runtime = new java.util.ArrayList<>();
		for(int index = 3; index < args.length; index++) {
			runtime.add(Path.of(args[index]));
		}
		rewrite(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), runtime)
				.forEach(System.out::println);
	}
}
