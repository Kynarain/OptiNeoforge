/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.optifine;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Repairs the parts of OptiFine that assume a plain jar on disk.
 *
 * <p>OptiFine finds its own jar by asking for the code source of one of its classes and turning
 * that URL into a file. On Forge the mod jar is a normal file, so that works. Under NeoForge the
 * classes come out of a union filesystem, and the URL looks like</p>
 *
 * <pre>union:/.../mods/optifine-...jar%23214!/</pre>
 *
 * <p>- a path with an index suffix and a trailing bang. OptiFine strips neither, then hands
 * {@code ...jar#214} to {@code ZipFile}, which fails with
 * {@code NoSuchFileException}, and ModLauncher aborts the whole launch because a transformation
 * service failed to load:</p>
 *
 * <pre>Error loading OptiFine ZIP file: ... java.nio.file.NoSuchFileException: ...jar#214
 * InvalidLauncherSetupException: Invalid Services found OptiFine</pre>
 *
 * <p>The repair is to make {@code optifine.OptiFineTransformationService.toFile(URI)} cut both
 * suffixes off the path before building the file. Everything else in the class is left as it
 * is.</p>
 */
public final class OptifineJarFixer {
	/** The class whose {@code toFile} turns a union URL into a path OptiFine can open. */
	private static final String SERVICE = "optifine/OptiFineTransformationService";
	private static final String TO_FILE = "toFile";
	private static final String TO_FILE_DESC = "(Ljava/net/URI;)Ljava/io/File;";
	/** The class that builds a SecureJarHandler metadata object with the older generation's signature. */
	private static final String JAR_CLASS = "optifine/OptiFineJar";
	private static final String METADATA_OWNER = "cpw/mods/jarhandling/impl/SimpleJarMetadata";
	/** Third parameter: the set itself under 2.1.10, a supplier of it under 2.1.24. */
	private static final String METADATA_OLD_CTOR =
			"(Ljava/lang/String;Ljava/lang/String;Ljava/util/Set;Ljava/util/List;)V";
	private static final String METADATA_NEW_CTOR =
			"(Ljava/lang/String;Ljava/lang/String;Ljava/util/function/Supplier;Ljava/util/List;)V";
	private static final String SET_SUPPLIER = "kynarain/cn/optifineoforge/loader/SetSupplier";
	/** Where the set handed to that constructor comes from, and so where the wrapper is inserted. */
	private static final String METADATA_SET_PRODUCER_OWNER = "cpw/mods/jarhandling/SecureJar";
	private static final String METADATA_SET_PRODUCER_NAME = "getPackages";

	private OptifineJarFixer() {
	}

	/** Whether the jar entry name is a class this fixer rewrites. */
	public static boolean handles(String entryName) {
		String name = entryName.endsWith(".class") ? entryName.substring(0, entryName.length() - ".class".length()) : entryName;
		return SERVICE.equals(name) || JAR_CLASS.equals(name);
	}

	/** The repair this entry needs, which is none for anything else in the jar. */
	public static byte[] fix(String entryName, byte[] classBytes) {
		String name = entryName.endsWith(".class") ? entryName.substring(0, entryName.length() - ".class".length()) : entryName;
		if(SERVICE.equals(name)) {
			return fixServicePath(classBytes);
		}
		if(JAR_CLASS.equals(name)) {
			return fixJarMetadataCall(classBytes);
		}
		return classBytes;
	}

	/**
	 * Replaces the body of the lambda that builds the metadata, rather than inserting into it.
	 *
	 * <p>The wrapper has to turn the set {@code getPackages()} returns into the {@code Supplier} the
	 * newer signature wants, and inserting that in the middle of the existing sequence did not verify,
	 * even though the emitted instructions were exactly the intended ones - measured by disassembling
	 * the patched class:</p>
	 *
	 * <pre> 8: SecureJar.getPackages()Ljava/util/Set;
	 * 13: new SetSupplier; 16: dup_x1; 17: &lt;init&gt;(Set)V
	 * 20: new ArrayList ...
	 * 27: SimpleJarMetadata.&lt;init&gt;(String, String, Supplier, List)V</pre>
	 *
	 * <p>and yet {@code VerifyError: Type uninitialized 13 ... is not assignable to 'java/util/Set'}. The
	 * instructions are right, so the frames are the problem: the writer recomputes them, and for
	 * {@code SetSupplier} - which the platform class loader cannot see - the common-superclass fallback
	 * answers {@code java/lang/Object}, so the recomputed frame and the instructions disagree and the
	 * verifier believes the frame.</p>
	 *
	 * <p>So the method is written whole. This lambda is self-contained and has no side effects to lose,
	 * which is what makes replacement safe here and unsafe for {@code toFile} (where it deleted a static
	 * field assignment):</p>
	 *
	 * <pre>private static JarMetadata lambda$1(SecureJar jar) {
	 *     return new SimpleJarMetadata("net.optifine", null, new SetSupplier(jar.getPackages()), new ArrayList&lt;&gt;());
	 * }</pre>
	 *
	 * <p>The rewrite happens only for a method that still calls the old signature, so a line whose
	 * SecureJarHandler never had it is left exactly as it is.</p>
	 */
	private static byte[] fixJarMetadataCall(byte[] classBytes) {
		ClassNode node = new ClassNode();
		ClassReader reader = new ClassReader(classBytes);
		reader.accept(node, 0);

		boolean patched = false;
		for(MethodNode method : node.methods) {
			if(!containsOldMetadataCall(method)) {
				continue;
			}
			method.instructions = metadataConstruction();
			method.tryCatchBlocks = new java.util.ArrayList<>();
			method.localVariables = null;
			patched = true;
		}
		if(!patched) {
			return classBytes;
		}
		ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		node.accept(writer);
		return writer.toByteArray();
	}

	/** Whether this method still builds the metadata with the set itself as the third argument. */
	private static boolean containsOldMetadataCall(MethodNode method) {
		for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if(insn instanceof MethodInsnNode call && METADATA_OWNER.equals(call.owner)
					&& "<init>".equals(call.name) && METADATA_OLD_CTOR.equals(call.desc)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * <pre>
	 * new SimpleJarMetadata("net.optifine", null, new SetSupplier(jar.getPackages()), new ArrayList&lt;&gt;())
	 * </pre>
	 */
	private static InsnList metadataConstruction() {
		InsnList list = new InsnList();
		list.add(new TypeInsnNode(Opcodes.NEW, METADATA_OWNER));
		list.add(new InsnNode(Opcodes.DUP));
		list.add(new LdcInsnNode("net.optifine"));
		list.add(new InsnNode(Opcodes.ACONST_NULL));
		list.add(new VarInsnNode(Opcodes.ALOAD, 0));
		list.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, METADATA_SET_PRODUCER_OWNER,
				METADATA_SET_PRODUCER_NAME, "()Ljava/util/Set;", true));
		// A single call, not new/dup: the set is already on the stack and the JVM wants an argument on
		// top of an invokespecial, so arranging the wrapper by hand got the shape wrong twice. Measured
		// with the frame dump: "Type uninitialized 13 (stack[6]) is not assignable to 'java/util/Set'".
		list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, SET_SUPPLIER, "of",
				"(Ljava/util/Set;)Ljava/util/function/Supplier;", false));
		list.add(new TypeInsnNode(Opcodes.NEW, "java/util/ArrayList"));
		list.add(new InsnNode(Opcodes.DUP));
		list.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false));
		list.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, METADATA_OWNER, "<init>", METADATA_NEW_CTOR, false));
		list.add(new InsnNode(Opcodes.ARETURN));
		return list;
	}

	/**
	 * Rewrites {@code toFile} in the service class so it strips the union filesystem's suffixes.
	 *
	 * @param classBytes the class as it is in the OptiFine jar
	 * @return the rewritten class, or the input unchanged when the method was not found
	 */
	public static byte[] fixServicePath(byte[] classBytes) {
		ClassNode node = new ClassNode();
		ClassReader reader = new ClassReader(classBytes);
		reader.accept(node, 0);

		boolean patched = false;
		for(MethodNode method : node.methods) {
			if(TO_FILE.equals(method.name) && TO_FILE_DESC.equals(method.desc)) {
				// Two shapes of this method exist, and they need opposite treatment. In the flavour
				// whose non-union branch builds the file from the URI itself - 1.20.1 - the body is
				// already right for plain files and the union path only needs the trailing "!" cut;
				// replacing that body deletes the static ofZipFileUrl assignment its own
				// getResourceUrl depends on, and the launch then dies much later, far from here,
				// reading a class through a union path:
				//   FileSystemNotFoundException ... Jar$JarModuleDataProvider.open
				// In the flavour that builds it from uri.getPath() - 1.20.4, 1.21.4 - the "#<index>!"
				// stays in the string and the whole method is replaced, which is what fixed
				// "NoSuchFileException: ...jar#177" there.
				if(!insertBangStrip(method)) {
					method.instructions = stripSuffixesThenFile();
					method.tryCatchBlocks = new java.util.ArrayList<>();
					method.localVariables = null;
				}
				patched = true;
			}
		}
		if(!patched) {
			return classBytes;
		}

		// Frames have to be recomputed, not just stack sizes: the rewritten method has branches,
		// and this class file is modern enough that the verifier expects stack map frames at their
		// targets. The frames only ever involve java.lang.String, int and java.io.File, so the
		// common-superclass lookup never has to load a game class.
		ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		node.accept(writer);
		return writer.toByteArray();
	}

	/** A writer that answers the frame computation from the platform class loader. */
	private static final class SafeClassWriter extends ClassWriter {
		SafeClassWriter(ClassReader reader, int flags) {
			super(reader, flags);
		}

		@Override
		protected String getCommonSuperClass(String type1, String type2) {
			try {
				ClassLoader loader = ClassLoader.getPlatformClassLoader();
				Class<?> first = Class.forName(type1.replace('/', '.'), false, loader);
				Class<?> second = Class.forName(type2.replace('/', '.'), false, loader);
				if(first.isAssignableFrom(second)) {
					return type1;
				}
				if(second.isAssignableFrom(first)) {
					return type2;
				}
				if(first.isInterface() || second.isInterface()) {
					return "java/lang/Object";
				}
				do {
					first = first.getSuperclass();
				} while(first != null && !first.isAssignableFrom(second));
				return first == null ? "java/lang/Object" : first.getName().replace('.', '/');
			} catch(ClassNotFoundException | LinkageError e) {
				return "java/lang/Object";
			}
		}
	}

	/**
	 * Cuts the union filesystem's trailing {@code !} off the path OptiFine already computed, by adding
	 * instructions after the {@code #}-strip the original performs and leaving everything else alone.
	 *
	 * <p>Found rather than assumed: the {@code #}-strip is the {@code substring} call that follows the
	 * {@code "#"} constant, and the value it stores is the local the rest of the method reads. The
	 * temporary slot is taken past {@code maxLocals}, so nothing the original uses can be clobbered.</p>
	 *
	 * @return whether the strip was inserted, i.e. whether this OptiFine handles the union scheme itself
	 */
	private static boolean insertBangStrip(MethodNode method) {
		// The gate is which shape this is, and it is visible in the method: this flavour's non-union
		// branch builds the file from the URI itself - new File(URI) - so that branch is already
		// correct and only the union path needs the extra cut. The other flavour builds it from
		// uri.getPath(), which leaves "#<index>!" inside the string when the scheme is not "union" -
		// that is the "NoSuchFileException: ...jar#177" this fixer was written for - and there the
		// whole method has to be replaced. Measured on both jars rather than assumed from sizes.
		boolean buildsFileFromUri = false;
		for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if(insn instanceof MethodInsnNode call && "java/io/File".equals(call.owner)
					&& "<init>".equals(call.name) && "(Ljava/net/URI;)V".equals(call.desc)) {
				buildsFileFromUri = true;
				break;
			}
		}
		if(!buildsFileFromUri) {
			return false;
		}

		AbstractInsnNode hashStripEnd = null;
		for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if(!(insn instanceof LdcInsnNode ldc) || !"#".equals(ldc.cst)) {
				continue;
			}
			// "#" ... substring(II)String ... astore <path local>
			AbstractInsnNode cursor = insn;
			VarInsnNode stored = null;
			for(int step = 0; step < 6 && cursor != null; step++) {
				cursor = cursor.getNext();
				if(cursor instanceof MethodInsnNode call && "substring".equals(call.name)) {
					AbstractInsnNode after = cursor.getNext();
					if(after instanceof VarInsnNode var && var.getOpcode() == Opcodes.ASTORE) {
						stored = var;
					}
				}
			}
			if(stored != null) {
				hashStripEnd = stored;
				break;
			}
		}
		if(hashStripEnd == null) {
			return false;
		}
		int slot = Math.max(method.maxLocals, 1);
		method.maxLocals = slot + 1;
		int pathLocal = ((VarInsnNode) hashStripEnd).var;
		LabelNode afterBang = new LabelNode();

		InsnList list = new InsnList();
		list.add(new VarInsnNode(Opcodes.ALOAD, pathLocal));
		list.add(new LdcInsnNode("!"));
		list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "indexOf", "(Ljava/lang/String;)I", false));
		list.add(new VarInsnNode(Opcodes.ISTORE, slot));
		list.add(new VarInsnNode(Opcodes.ILOAD, slot));
		list.add(new JumpInsnNode(Opcodes.IFLT, afterBang));
		list.add(new VarInsnNode(Opcodes.ALOAD, pathLocal));
		list.add(new InsnNode(Opcodes.ICONST_0));
		list.add(new VarInsnNode(Opcodes.ILOAD, slot));
		list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false));
		list.add(new VarInsnNode(Opcodes.ASTORE, pathLocal));
		list.add(afterBang);
		method.instructions.insert(hashStripEnd, list);
		return true;
	}

	/**
	 * <pre>
	 * String path = uri.getPath();
	 * int bang = path.indexOf('!');
	 * if (bang &gt;= 0) path = path.substring(0, bang);
	 * int hash = path.lastIndexOf('#');
	 * if (hash &gt;= 0) path = path.substring(0, hash);
	 * path = path.replace("file:", "");
	 * return new File(path);
	 * </pre>
	 *
	 * <p>{@code getPath()} rather than {@code toString()}: the URL has already been through
	 * {@code URI.create}, so the interesting {@code %} escapes are decoded there, and the literal
	 * {@code #} is what has to be cut off - the encoded form would not be found.</p>
	 */
	private static InsnList stripSuffixesThenFile() {
		InsnList list = new InsnList();
		LabelNode afterBang = new LabelNode();
		LabelNode afterHash = new LabelNode();

		list.add(new VarInsnNode(Opcodes.ALOAD, 0));
		list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/net/URI", "getPath", "()Ljava/lang/String;", false));
		list.add(new VarInsnNode(Opcodes.ASTORE, 1));

		// the union filesystem's trailing "!" (and anything after it)
		list.add(new VarInsnNode(Opcodes.ALOAD, 1));
		list.add(new LdcInsnNode("!"));
		list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "indexOf", "(Ljava/lang/String;)I", false));
		list.add(new VarInsnNode(Opcodes.ISTORE, 2));
		list.add(new VarInsnNode(Opcodes.ILOAD, 2));
		list.add(new JumpInsnNode(Opcodes.IFLT, afterBang));
		list.add(new VarInsnNode(Opcodes.ALOAD, 1));
		list.add(new InsnNode(Opcodes.ICONST_0));
		list.add(new VarInsnNode(Opcodes.ILOAD, 2));
		list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false));
		list.add(new VarInsnNode(Opcodes.ASTORE, 1));
		list.add(afterBang);

		// the "#<index>" SecureJar adds to the jar path
		list.add(new VarInsnNode(Opcodes.ALOAD, 1));
		list.add(new LdcInsnNode("#"));
		list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "lastIndexOf", "(Ljava/lang/String;)I", false));
		list.add(new VarInsnNode(Opcodes.ISTORE, 2));
		list.add(new VarInsnNode(Opcodes.ILOAD, 2));
		list.add(new JumpInsnNode(Opcodes.IFLT, afterHash));
		list.add(new VarInsnNode(Opcodes.ALOAD, 1));
		list.add(new InsnNode(Opcodes.ICONST_0));
		list.add(new VarInsnNode(Opcodes.ILOAD, 2));
		list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false));
		list.add(new VarInsnNode(Opcodes.ASTORE, 1));
		list.add(afterHash);

		// a plain file: URL keeps its prefix when it comes through this path
		list.add(new VarInsnNode(Opcodes.ALOAD, 1));
		list.add(new LdcInsnNode("file:"));
		list.add(new LdcInsnNode(""));
		list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "replace",
				"(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;", false));
		list.add(new VarInsnNode(Opcodes.ASTORE, 1));

		list.add(new TypeInsnNode(Opcodes.NEW, "java/io/File"));
		list.add(new InsnNode(Opcodes.DUP));
		list.add(new VarInsnNode(Opcodes.ALOAD, 1));
		list.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false));
		list.add(new InsnNode(Opcodes.ARETURN));
		return list;
	}

	/** Development aid: report what the fixer would do to a class file, and write the result beside it. */
	public static void main(String[] args) throws Exception {
		byte[] before = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[0]));
		byte[] after = fixServicePath(before);
		System.out.println("before " + before.length + " bytes, after " + after.length + " bytes, changed=" + (before.length != after.length || !java.util.Arrays.equals(before, after)));
		java.nio.file.Path written = java.nio.file.Path.of(args[0] + ".patched");
		java.nio.file.Files.write(written, after);
		System.out.println("wrote " + written);
		ClassNode node = new ClassNode();
		new ClassReader(after).accept(node, 0);
		for(MethodNode method : node.methods) {
			if(TO_FILE.equals(method.name)) {
				System.out.println("patched " + method.name + method.desc + " now has " + method.instructions.size() + " instructions");
				for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					System.out.println("  " + insn.getClass().getSimpleName() + " " + insn);
				}
			}
		}
	}
}
