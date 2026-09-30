/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.optifine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Lists every game member the payload calls that the runtime does not have.
 *
 * <p>Written for the failure that member restoration cannot fix. {@code BlockEntity.<init>} in OptiFine's
 * compilation calls {@code gatherCapabilities()}, NeoForge 20.4 replaced Forge's capability system with
 * attachments and deleted that method, and the restore plan was right not to restore it - a donor cannot
 * come from a runtime that has no such member:</p>
 *
 * <pre>NoSuchMethodError: 'void net.minecraft.world.level.block.entity.BlockEntity.gatherCapabilities()'</pre>
 *
 * <p>{@link SrgRemap} cannot see these either, because it only ever looks at SRG-shaped names
 * ({@code m_}/{@code f_}) while Forge API members keep their plain names. So this walks <em>every</em>
 * reference whose owner is a game class and asks the runtime, hierarchy included, whether the member is
 * there. What it prints is the complete set that {@code gatherCapabilities} is one of, which is what a
 * decision about shimming or stripping needs.</p>
 *
 * <p><b>Give it the whole runtime classpath, not just the game jar.</b> The report is a function of what
 * it is asked to resolve against, and the difference is not cosmetic. Measured on 1.21.8 with the game jar
 * alone: 126 references missing, of which almost all are library members that live in {@code libraries/}
 * ({@code com.mojang.serialization}, {@code datafixers}, {@code logging}, {@code jtracy}, {@code authlib}).
 * With the same payload against the game jar plus NeoForge's universal jar plus all 174 library jars: 16
 * missing, and those 16 are the real ones. Two consequences, both measured. The 804-line stub file the
 * short-classpath run wrote was mostly library members, so its size says nothing about how much is
 * actually missing - and the members that stop the client outright were among the few that mattered
 * ({@code GpuTexture.isStencilEnabled}, {@code BlockModelPart.layer}, {@code BlockEntity}'s capability
 * members). And the two outputs have to be used together: a member whose owner is in the payload is
 * stubbed <em>into the output jar</em>, while one whose owner is a runtime class can only be listed in
 * the file for the loader to add, so the payload handed to the loader has to be the stubbed one.</p>
 */
public final class MissingTargets {
	private MissingTargets() {
	}

	/** Development aid: {@code MissingTargets <payload jar> <runtime jar> [more runtime jars...]}. */
	public static void main(String[] args) throws IOException {
		boolean fix = args.length > 0 && "--stub".equals(args[0]);
		// --stub <in jar> <out jar> <stubs file> <skipped prefixes, comma separated> <payload jar> <runtime...>
		//
		// The skipped prefixes are classes the build leaves out of the shipped payload even though OptiFine
		// patched them. They have to be skipped here as well, and that is not a detail: with OptiFine's
		// LoadingOverlay still counted as present, the call to LoadingOverlay.update() that OptiFine's
		// GameRenderer makes looked satisfiable, so it was never reported, never deferred and never stubbed -
		// and the shipped jar had neither the class nor the method.
		int base = fix ? 5 : 0;
		if(args.length < base + 2) {
			System.err.println("usage: MissingTargets [--stub <in jar> <out jar> <stubs file> <skipped prefixes>]"
					+ " <payload jar> <runtime jar> [more runtime jars...]");
			System.exit(2);
		}
		java.util.List<String> skippedPrefixes = new ArrayList<>();
		if(fix && !args[4].isBlank()) {
			for(String prefix : args[4].split(",")) {
				if(!prefix.isBlank()) {
					skippedPrefixes.add(prefix.trim());
				}
			}
		}
		java.util.function.Predicate<String> skip = name -> {
			for(String prefix : skippedPrefixes) {
				if(name.startsWith(prefix)) {
					return true;
				}
			}
			return false;
		};
		Path payload = Path.of(args[base]);
		SrgMemberMap.RuntimeIndex runtime = new SrgMemberMap.RuntimeIndex();
		for(int index = base + 1; index < args.length; index++) {
			runtime.add(Path.of(args[index]));
		}
		// The payload's own classes belong in the index as well, and this is the difference between a
		// useful report and a useless one: OptiFine *adds* members to the classes it replaces
		// (ModelPart.getChildModelDeep, Options.ofClouds, TextureAtlasSprite.spriteNormal), so a
		// reference to one of those is missing from the runtime while being perfectly satisfiable -
		// because the swapped class is what will be loaded. Indexing only the runtime reported 1259
		// "missing" references of which the great majority were OptiFine's own additions.
		runtime.addJdk();
		runtime.add(payload, skip);

		Map<String, Integer> counts = new TreeMap<>();
		Map<String, String> firstSeen = new LinkedHashMap<>();
		Map<String, Member> targets = new LinkedHashMap<>();
		int references = 0;
		int classes = 0;
		try(ZipFile zip = new ZipFile(payload.toFile())) {
			for(Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements(); ) {
				ZipEntry entry = it.nextElement();
				if(!entry.getName().endsWith(".class") || SrgRemap.isUnusedNamespace(entry.getName())) {
					continue;
				}
				ClassNode node = new ClassNode();
				try(InputStream stream = zip.getInputStream(entry)) {
					new ClassReader(stream.readAllBytes()).accept(node, ClassReader.SKIP_DEBUG);
				}
				if(skip.test(node.name)) {
					// Not shipped: its own references cannot matter, and reading them only produces noise.
					continue;
				}
				classes++;
				for(MethodNode method : node.methods) {
					for(AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
						String owner = null;
						String name = null;
						String desc = null;
						boolean isMethod = false;
						if(instruction instanceof MethodInsnNode call) {
							owner = call.owner;
							name = call.name;
							desc = call.desc;
							isMethod = true;
						} else if(instruction instanceof FieldInsnNode field) {
							owner = field.owner;
							name = field.name;
							desc = field.desc;
						}
						if(owner == null || !isGameClass(owner)) {
							continue;
						}
						references++;
						if(declares(runtime, owner, name, desc, isMethod)) {
							continue;
						}
						String key = owner + "." + name + (isMethod ? desc : ":" + desc);
						counts.merge(key, 1, Integer::sum);
						firstSeen.putIfAbsent(key, node.name);
						targets.put(key, new Member(owner, name, desc, isMethod));
					}
				}
			}
		}

		// DEBUG References to this one member, to see whether the scan reaches them at all.
		int debugTotal = 0;
		int debugReached = 0;
		for(String key : counts.keySet()) { if(key.contains("LoadingOverlay.update")) { debugReached++; } }
		System.out.println("DEBUG References scan: scanned classes=" + classes + " withLoadingOverlayUpdate=" + debugReached);
		List<Map.Entry<String, Integer>> ordered = new ArrayList<>(counts.entrySet());
		ordered.sort((left, right) -> Integer.compare(right.getValue(), left.getValue()));
		System.out.println("scanned " + classes + " classes, " + references
				+ " references to game members, " + ordered.size() + " of them missing from the runtime");
		int shown = 0;
		for(Map.Entry<String, Integer> entry : ordered) {
			if(shown++ >= 40) {
				System.out.println("  ... " + (ordered.size() - 40) + " more");
				break;
			}
			System.out.println("  " + entry.getValue() + "x  " + entry.getKey()
					+ "   (first seen in " + firstSeen.get(entry.getKey()) + ")");
		}
		if(fix) {
			// args[2] and not args[1]: args[1] is the input, and writing there while the same file is open
			// for reading truncated the jar and raised EOFException from deep inside ZipFile.
			System.out.println(stub(Path.of(args[base]), Path.of(args[2]), Path.of(args[3]), skip, targets.values()));
		}
	}

	/** One unsatisfiable member: the owner's internal name, the name, the descriptor and which it is. */
	private static final class Member {
		final String owner;
		final String name;
		final String desc;
		final boolean method;

		Member(String owner, String name, String desc, boolean method) {
			this.owner = owner;
			this.name = name;
			this.desc = desc;
			this.method = method;
		}
	}

	/**
	 * Gives the unsatisfiable members a default-valued implementation and writes the payload back out.
	 *
	 * <p>A no-op is the honest repair here. These members are Forge API that NeoForge deleted - the
	 * capability members on BlockEntity, whose work NeoForge's attachment system now does through
	 * setData/removeData, which the restore plan does put back - so a call to them should do nothing
	 * rather than fail. Restoring them from a donor is impossible by definition: the runtime has no such
	 * member to copy.</p>
	 *
	 * <p>In an interface the member is added as a default method, not an abstract one. Adding an abstract
	 * method to an interface would break every class that already implements it, turning one missing call
	 * into an AbstractMethodError somewhere else.</p>
	 */
	private static String stub(Path in, Path out, Path stubFile, java.util.function.Predicate<String> skip, java.util.Collection<Member> members) throws IOException {
		Map<String, List<Member>> byOwner = new LinkedHashMap<>();
		for(Member member : members) {
			if(member.method) {
				byOwner.computeIfAbsent(member.owner, key -> new ArrayList<>()).add(member);
			}
		}
		int added = 0;
		int skipped = 0;
		Map<String, Integer> touched = new TreeMap<>();
		try(ZipFile source = new ZipFile(in.toFile());
				java.util.zip.ZipOutputStream sink = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(out))) {
			for(Enumeration<? extends ZipEntry> it = source.entries(); it.hasMoreElements(); ) {
				ZipEntry entry = it.nextElement();
				byte[] data;
				try(InputStream stream = source.getInputStream(entry)) {
					data = stream.readAllBytes();
				}
				// Entry names carry the union root ('srg/...'), while a member's owner is the class name,
				// so the match is made on the class name read out of the file rather than the path.
				if(entry.getName().endsWith(".class") && !SrgRemap.isUnusedNamespace(entry.getName())) {
					ClassNode node = new ClassNode();
					new ClassReader(data).accept(node, 0);
					List<Member> wanted = skip.test(node.name) ? null : byOwner.get(node.name);
					if(wanted != null) {
						boolean isInterface = (node.access & Opcodes.ACC_INTERFACE) != 0;
						for(Member member : wanted) {
							if(hasMethod(node, member.name, member.desc)) {
								continue;
							}
							node.methods.add(defaultBody(member.name, member.desc, isInterface));
							added++;
						}
						touched.put(node.name, wanted.size());
						ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
						node.accept(writer);
						data = writer.toByteArray();
					}
				}
				ZipEntry copy = new ZipEntry(entry.getName());
				copy.setTime(entry.getTime());
				sink.putNextEntry(copy);
				sink.write(data);
				sink.closeEntry();
			}
		}
		// A member whose owner is not in this jar cannot be stubbed here: the owner is a runtime class, and
		// that one has to be given the member while it loads. Those are listed for the loader instead.
		StringBuilder deferred = new StringBuilder();
		for(String owner : byOwner.keySet()) {
			if(touched.containsKey(owner)) {
				continue;
			}
			for(Member member : byOwner.get(owner)) {
				deferred.append(owner).append('\t').append(member.name).append('\t').append(member.desc).append('\n');
				skipped++;
			}
		}
		java.nio.file.Files.writeString(stubFile, deferred);
		return "stubbed " + added + " members on " + touched + (skipped == 0 ? "" : ", " + skipped
				+ " left for the loader because their owner is a runtime class (" + stubFile.getFileName() + ")");
	}

	private static boolean hasMethod(ClassNode node, String name, String desc) {
		for(MethodNode method : node.methods) {
			if(method.name.equals(name) && method.desc.equals(desc)) {
				return true;
			}
		}
		return false;
	}

	/** A method returning the default value for its return type; a default method when in an interface. */
	private static MethodNode defaultBody(String name, String desc, boolean isInterface) {
		int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC;
		MethodNode method = new MethodNode(access, name, desc, null, null);
		org.objectweb.asm.tree.InsnList body = method.instructions;
		org.objectweb.asm.Type returnType = org.objectweb.asm.Type.getReturnType(desc);
		switch(returnType.getSort()) {
			case org.objectweb.asm.Type.VOID -> body.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
			case org.objectweb.asm.Type.BOOLEAN, org.objectweb.asm.Type.BYTE, org.objectweb.asm.Type.CHAR,
					org.objectweb.asm.Type.SHORT, org.objectweb.asm.Type.INT -> {
				body.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_0));
				body.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IRETURN));
			}
			case org.objectweb.asm.Type.LONG -> {
				body.add(new org.objectweb.asm.tree.InsnNode(Opcodes.LCONST_0));
				body.add(new org.objectweb.asm.tree.InsnNode(Opcodes.LRETURN));
			}
			case org.objectweb.asm.Type.FLOAT -> {
				body.add(new org.objectweb.asm.tree.InsnNode(Opcodes.FCONST_0));
				body.add(new org.objectweb.asm.tree.InsnNode(Opcodes.FRETURN));
			}
			case org.objectweb.asm.Type.DOUBLE -> {
				body.add(new org.objectweb.asm.tree.InsnNode(Opcodes.DCONST_0));
				body.add(new org.objectweb.asm.tree.InsnNode(Opcodes.DRETURN));
			}
			default -> {
				body.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ACONST_NULL));
				body.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ARETURN));
			}
		}
		return method;
	}

	private static boolean isGameClass(String owner) {
		return owner.startsWith("net/minecraft/") || owner.startsWith("com/mojang/");
	}

	/**
	 * Members a JDK ancestor legitimately provides, since the JDK cannot be indexed here.
	 *
	 * <p>This rule started as "any java/** ancestor counts as present", which was far too broad and quietly
	 * broke the tool: every class has {@code java/lang/Object} in its hierarchy, so the walk reached it and
	 * answered "present" for <em>everything</em>. OptiFine's call to {@code LoadingOverlay.update()} - a
	 * method only OptiFine's own copy of that class declares - was therefore never reported, never stubbed,
	 * and killed the game at runtime. Only the inherited members that actually matter are listed now, and
	 * {@code Enum.ordinal()} - the case the rule was added for - is one of them.</p>
	 */
	private static final java.util.Set<String> JDK_MEMBERS = java.util.Set.of(
			"<init>", "equals", "hashCode", "toString", "getClass", "clone", "finalize", "notify", "notifyAll",
			"wait", "ordinal", "name", "compareTo", "getDeclaringClass", "describeConstable", "values",
			"valueOf", "iterator", "hasNext", "next", "size", "isEmpty");

	/**
	 * Whether the runtime declares the member, on the owner or anywhere up its hierarchy.
	 *
	 * <p>Every candidate is looked up, JDK ancestors included, because {@link SrgMemberMap.RuntimeIndex#addJdk()}
	 * indexes java.base: {@code Direction.ordinal()} resolves through {@code java/lang/Enum} like any other
	 * inherited member. The special case that used to sit here answered "present" for anything with an
	 * {@code Object} above it - which is every class - and that is what hid OptiFine's call to
	 * {@code LoadingOverlay.update()} from this report, and from the stub pass after it.</p>
	 */
	private static boolean declares(SrgMemberMap.RuntimeIndex runtime, String owner, String name, String desc,
			boolean method) {
		for(String candidate : runtime.hierarchy(owner)) {
			String key = candidate + "." + name + desc;
			if(method ? runtime.methods.contains(key) : runtime.fields.contains(key)) {
				return true;
			}
		}
		return false;
	}

	/** Kept so the class compiles standalone against the same helpers the other tools use. */
	static Map<String, Integer> emptyCounts() {
		return new HashMap<>();
	}
}
