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

	private OptifineJarFixer() {
	}

	/** Whether the jar entry name is the class this fixer rewrites. */
	public static boolean handles(String entryName) {
		String name = entryName.endsWith(".class") ? entryName.substring(0, entryName.length() - ".class".length()) : entryName;
		return SERVICE.equals(name);
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
				method.instructions = stripSuffixesThenFile();
				method.tryCatchBlocks = new java.util.ArrayList<>();
				method.localVariables = null;
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

	/** Development aid: report what the fixer would do to a class file. */
	public static void main(String[] args) throws Exception {
		byte[] before = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[0]));
		byte[] after = fixServicePath(before);
		System.out.println("before " + before.length + " bytes, after " + after.length + " bytes, changed=" + (before.length != after.length || !java.util.Arrays.equals(before, after)));
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
