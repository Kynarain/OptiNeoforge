/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.optifine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Supplies the Forge-era members of <em>game</em> classes that OptiFine's {@code Reflector} table
 * expects and this runtime does not have.
 *
 * <p>Where {@link ForgeApiShims} answers for the {@code net.minecraftforge.**} types NeoForge deleted,
 * this answers for the members Forge once added <em>to the game's own classes</em>. A missing one is
 * not a missing class: {@code Reflector.forgeItemTags.create(...)} resolves against
 * {@code net.minecraft.tags.ItemTags}, which exists, so the lookup fails quietly and the caller gets
 * null - measured in {@link ReflectorGaps}, which is the tool that finds these.</p>
 *
 * <p>Measured case, 1.21.11: OptiFine's patched {@code net.minecraft.world.item.DyeColor} builds its
 * two tag fields with</p>
 *
 * <pre>this.dyesTag = (TagKey) Reflector.ForgeItemTags_create.call("forge", "dyes/" + name);</pre>
 *
 * <p>and the table's {@code ForgeItemTags} is {@code new ReflectorClass(ItemTags.class)} with
 * {@code makeMethod("create", String.class, String.class)} - Forge's two-argument helper on the game's
 * own tag class. NeoForge kept only the one-argument form - {@code create(Identifier)} on 1.21.11 and
 * 26.1.2, {@code create(ResourceLocation)} on 1.21.9 and 1.21.10 - so the call answers null, both tag
 * fields of every {@code DyeColor} constant are null, and
 * {@code net.neoforged.neoforge.common.Tags$Items.DYES_BLACK} - which is
 * {@code DyeColor.BLACK.getTag()} - is null, which makes NeoForge's own
 * {@code TagConventionLogWarning.<clinit>} throw and the whole mod load fail with
 * {@code Mod loading failures have occurred}.</p>
 *
 * <p>The supplied body is not a stub: the tag fields are read by the game, so a null answer is worse
 * than no method at all. What it does is what this runtime's own class does - measured, not assumed:
 * NeoForge's {@code Tags$Items.tag(String)} is {@code ItemTags.create(Identifier.fromNamespaceAndPath
 * ("c", name))} and the runtime's own {@code DyeColor} constructor builds its tags the same way, so the
 * two-argument helper the Forge generation used to name conventional tags is answered with this
 * runtime's conventional namespace. The namespace is taken as given unless it is {@code forge}, the
 * Forge generation's name for that same set of tags.</p>
 *
 * <p>Usage: {@code ForgeEraMembers <payload jar> <runtime jar> [runtime jar...] [--dry-run]}. The
 * classes it writes go into the payload under {@code srg/}, so the mount point installs them exactly
 * like the classes OptiFine's own patches produce.</p>
 */
public final class ForgeEraMembers {
	/** The runtime's name for the conventional tag namespace the Forge generation called "forge". */
	private static final String CONVENTIONAL_NAMESPACE = "c";
	private static final String FORGE_ERA_NAMESPACE = "forge";

	private static final String ITEM_TAGS = "net/minecraft/tags/ItemTags";
	private static final String TAG_KEY = "net/minecraft/tags/TagKey";

	/**
	 * The two names this project's lines use for the resource-location class, newest first, and the one
	 * this run resolved. It is <em>not</em> a constant, and that is a measurement: 1.21.11 (and 26.1.2)
	 * renamed the class to {@code Identifier}, while 1.21.9 and 1.21.10 still call it
	 * {@code ResourceLocation} - checked in the archives themselves, {@code Identifier.class} is present
	 * in the 21.11.45 and 26.1.2.109 patched jars and absent from 21.10.64's and 21.9's, which carry
	 * {@code ResourceLocation.class} instead.
	 *
	 * <p>Hardcoding the newer name is exactly what broke the 1.21.10 line: the supplied
	 * {@code ItemTags.create} threw {@code NoClassDefFoundError: net/minecraft/resources/Identifier}
	 * inside {@code DyeColor.<clinit>}, FML answered by marking the mod file broken and refusing every
	 * later event to it ({@code Cowardly refusing to send event ... to a broken mod state}, 38 times), and
	 * OptiFine's sprite collection - which is event-driven - never ran, so the client sat in
	 * {@code [OptiFine] Waiting for model sprites} until the launch timed out. The API shape is the same
	 * on both names: {@code fromNamespaceAndPath(String, String)} returning itself, and
	 * {@code ItemTags.create(<that class>)} returning {@code TagKey}.
	 */
	private static final String IDENTIFIER = "net/minecraft/resources/Identifier";
	private static final String RESOURCE_LOCATION = "net/minecraft/resources/ResourceLocation";

	/** The name {@link #IDENTIFIER} or {@link #RESOURCE_LOCATION} this run's runtime actually has. */
	private static String resourceLocation;

	private ForgeEraMembers() {
	}

	/** One Forge-era member of a game class, and why the runtime does not have it. */
	private record Member(String owner, String name, String desc, String reason) {
	}

	/**
	 * The members this tool knows how to supply, each with the measurement that says the runtime is
	 * missing it and the reason the answer has to be real rather than a default value.
	 */
	private static List<Member> table() {
		List<Member> members = new ArrayList<>();
		members.add(new Member(ITEM_TAGS, "create", "(Ljava/lang/String;Ljava/lang/String;)L" + TAG_KEY + ";",
				"Forge's two-argument ItemTags.create; NeoForge kept only create(Identifier)."
						+ " OptiFine's DyeColor stores the answer in a field, so null there fails the mod load."));
		return members;
	}

	public static void main(String[] args) throws Exception {
		boolean dryRun = false;
		List<String> files = new ArrayList<>();
		for(String arg : args) {
			if("--dry-run".equals(arg)) {
				dryRun = true;
			} else {
				files.add(arg);
			}
		}
		if(files.size() < 2) {
			System.err.println("usage: ForgeEraMembers <payload jar> <runtime jar> [runtime jar...] [--dry-run]");
			System.exit(2);
		}
		Path payload = Path.of(files.get(0));
		List<Path> runtime = new ArrayList<>();
		for(int index = 1; index < files.size(); index++) {
			runtime.add(Path.of(files.get(index)));
		}
		resourceLocation = resolveResourceLocation(runtime);
		System.out.println("  the runtime's resource-location class: " + resourceLocation);

		List<String> supplied = new ArrayList<>();
		List<String> skipped = new ArrayList<>();
		List<String> alreadyInPayload = payloadEntries(payload);
		// The payload jars this project builds carry a few directory entries twice (the build steps
		// add META-INF/services/ unconditionally), and ZipOutputStream refuses a duplicate name - so
		// the first copy of each name wins and the repeats are dropped. Measured: without this, the
		// rewrite dies on "duplicate entry: META-INF/services/" and the payload is left untouched.
		java.util.Set<String> written = new java.util.HashSet<>();
		Path temporary = payload.resolveSibling(payload.getFileName() + ".tmp");
		try(ZipFile source = new ZipFile(payload.toFile());
				ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temporary))) {
			for(java.util.Enumeration<? extends ZipEntry> it = source.entries(); it.hasMoreElements();) {
				ZipEntry entry = it.nextElement();
				String name = entry.getName();
				if(!written.add(name)) {
					continue;
				}
				if(entry.isDirectory()) {
					// A jar's directory entries are written too: the jar contents abstraction reports a
					// class whose package has no directory of its own as not found.
					out.putNextEntry(new ZipEntry(name));
					out.closeEntry();
					continue;
				}
				byte[] bytes = readAll(source.getInputStream(entry));
				for(Member member : table()) {
					if(name.equals("srg/" + member.owner() + ".class")) {
						// A payload copy of the class exists: add the member to the payload's own
						// bytes rather than to the runtime's, so nothing of the payload is lost.
						byte[] withMember = addMember(bytes, member);
						if(withMember != null) {
							bytes = withMember;
							supplied.add(member.owner() + "." + member.name() + member.desc()
									+ "  (into the payload's own copy)");
						}
					}
				}
				copy(out, name, bytes);
			}
			// And the ones the payload does not carry: the runtime class comes in from the jars given,
			// with the member added, at the path the mount point reads.
			for(Member member : table()) {
				if(alreadyInPayload.contains("srg/" + member.owner() + ".class")) {
					continue;
				}
				byte[] runtimeBytes = findClass(runtime, member.owner());
				if(runtimeBytes == null) {
					skipped.add(member.owner() + ": no copy in the runtime jars given");
					continue;
				}
				byte[] withMember = addMember(runtimeBytes, member);
				if(withMember == null) {
					skipped.add(member.owner() + "." + member.name() + member.desc()
							+ ": the runtime class already declares it");
					continue;
				}
				copy(out, "srg/" + member.owner() + ".class", withMember);
				written.add("srg/" + member.owner() + ".class");
				supplied.add(member.owner() + "." + member.name() + member.desc()
						+ "  (runtime class " + runtimeBytes.length + " -> " + withMember.length + " bytes)");
			}
		}
		if(dryRun) {
			Files.deleteIfExists(temporary);
		} else {
			Files.move(temporary, payload, StandardCopyOption.REPLACE_EXISTING);
		}
		for(Member member : table()) {
			System.out.println("  reason: " + member.owner() + "." + member.name() + " - " + member.reason());
		}
		for(String line : supplied) {
			System.out.println("  supplied " + line);
		}
		for(String line : skipped) {
			System.out.println("  nothing to do: " + line);
		}
		System.out.println("supplied " + supplied.size() + " Forge-era member(s) into " + payload
				+ (dryRun ? " (dry run, not written)" : ""));
	}

	/** Every entry name of a jar, read before the jar is rewritten. */
	private static List<String> payloadEntries(Path jar) throws IOException {
		List<String> names = new ArrayList<>();
		try(ZipFile zip = new ZipFile(jar.toFile())) {
			for(java.util.Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
				names.add(it.nextElement().getName());
			}
		}
		return names;
	}

	/** The class's bytes with the member added, or null when it is already declared. */
	private static byte[] addMember(byte[] bytes, Member member) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		for(MethodNode method : node.methods) {
			if(method.name.equals(member.name()) && method.desc.equals(member.desc())) {
				return null;
			}
		}
		if(!ITEM_TAGS.equals(member.owner()) || !"create".equals(member.name())) {
			throw new IllegalStateException("no body is defined for " + member.owner() + "." + member.name());
		}
		// The body is written from the reader to the writer so that every other part of the runtime
		// class survives byte for byte - it replaces the runtime's own class at run time, so anything
		// dropped here would be a silent change to the game.
		ClassReader reader = new ClassReader(bytes);
		ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
		reader.accept(writer, 0);
		MethodVisitor body = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, member.name(),
				member.desc(), null, null);
		body.visitCode();
		body.visitVarInsn(Opcodes.ALOAD, 0);
		body.visitLdcInsn(FORGE_ERA_NAMESPACE);
		body.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals",
				"(Ljava/lang/Object;)Z", false);
		org.objectweb.asm.Label keepGiven = new org.objectweb.asm.Label();
		org.objectweb.asm.Label namespaceReady = new org.objectweb.asm.Label();
		body.visitJumpInsn(Opcodes.IFEQ, keepGiven);
		body.visitLdcInsn(CONVENTIONAL_NAMESPACE);
		body.visitJumpInsn(Opcodes.GOTO, namespaceReady);
		body.visitLabel(keepGiven);
		body.visitVarInsn(Opcodes.ALOAD, 0);
		body.visitLabel(namespaceReady);
		body.visitVarInsn(Opcodes.ALOAD, 1);
		body.visitMethodInsn(Opcodes.INVOKESTATIC, resourceLocation, "fromNamespaceAndPath",
				"(Ljava/lang/String;Ljava/lang/String;)L" + resourceLocation + ";", false);
		body.visitMethodInsn(Opcodes.INVOKESTATIC, ITEM_TAGS, "create",
				"(L" + resourceLocation + ";)L" + TAG_KEY + ";", false);
		body.visitInsn(Opcodes.ARETURN);
		body.visitMaxs(0, 0);
		body.visitEnd();
		writer.visitEnd();
		return writer.toByteArray();
	}

	/**
	 * Which of the two names the runtime jars given actually contain. The newer name wins when both are
	 * present, so the 1.21.11 / 26.1.2 output stays byte-for-byte what it was before this tool learned
	 * the older name; a runtime with neither is refused rather than guessed at, because a body naming a
	 * class that does not exist is precisely the failure recorded above.
	 */
	private static String resolveResourceLocation(List<Path> runtime) throws IOException {
		boolean hasIdentifier = false;
		boolean hasResourceLocation = false;
		for(Path jar : runtime) {
			try(ZipFile zip = new ZipFile(jar.toFile())) {
				hasIdentifier |= zip.getEntry(IDENTIFIER + ".class") != null;
				hasResourceLocation |= zip.getEntry(RESOURCE_LOCATION + ".class") != null;
			}
		}
		if(hasIdentifier) {
			return IDENTIFIER;
		}
		if(hasResourceLocation) {
			return RESOURCE_LOCATION;
		}
		throw new IOException("neither " + IDENTIFIER + " nor " + RESOURCE_LOCATION
				+ " is in the runtime jars given, so the supplied body cannot be written");
	}

	private static byte[] findClass(List<Path> jars, String name) throws IOException {
		for(Path jar : jars) {
			try(ZipFile zip = new ZipFile(jar.toFile())) {
				ZipEntry entry = zip.getEntry(name + ".class");
				if(entry != null) {
					return readAll(zip.getInputStream(entry));
				}
			}
		}
		return null;
	}

	private static void copy(ZipOutputStream out, String name, byte[] bytes) throws IOException {
		out.putNextEntry(new ZipEntry(name));
		out.write(bytes);
		out.closeEntry();
	}

	private static byte[] readAll(InputStream stream) throws IOException {
		try(stream) {
			return stream.readAllBytes();
		}
	}
}
