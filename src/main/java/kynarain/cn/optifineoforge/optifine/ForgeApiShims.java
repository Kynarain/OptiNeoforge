/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.optifine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Supplies the Forge API types OptiFine's classes name but NeoForge does not have.
 *
 * <p>OptiFine is a Forge mod, and parts of it are written against Forge's own API - {@code
 * net.minecraftforge.client.extensions.IForgeVertexConsumer} and friends. NeoForge has no such
 * package: its equivalents live under {@code net.neoforged.neoforge.*} and are not
 * signature-compatible. The references are mostly in method signatures, and a signature that
 * cannot be resolved stops the launch before any mod runs, because FML walks the method signatures
 * of every class in the game layer while it is setting up the early window:</p>
 *
 * <pre>ClassNotFoundException: net.minecraftforge.client.extensions.IForgeVertexConsumer
 *   at DisplayWindow.updateModuleReads(DisplayWindow.java:618)</pre>
 *
 * <p>OptiFine does carry its own copies of those Forge classes - under {@code notch/} - but they are
 * compiled against the obfuscated game (their signatures name {@code gng}, {@code akv} and so on),
 * so putting them on the runtime classpath only moves the failure to the next unresolvable type.</p>
 *
 * <p>What is generated here instead is a stub per referenced type. Each stub carries the members
 * OptiFine's own classes read or call on that type, because a class name alone is not enough: the
 * first version of this file emitted empty classes and OptiFine's {@code SimpleBakedModel$Builder}
 * then died reading a field of one of them:</p>
 *
 * <pre>java.lang.NoSuchFieldError: Class net.minecraftforge.client.RenderTypeGroup does not have
 *   member field 'net.minecraftforge.client.RenderTypeGroup EMPTY'
 *   at SimpleBakedModel$Builder.&lt;init&gt;(SimpleBakedModel.java:215)</pre>
 *
 * <p>Members are filled with the do-nothing value for their type - null, zero, false - so a stub
 * satisfies the reference and answers something predictable. Where the answer matters, the member
 * needs a real implementation instead, and that is a separate decision deliberately not taken here:
 * a stub that quietly answers null is honest about being a stub.</p>
 *
 * <p>The stubs are derived from the OptiFine jar itself, so each one has the same kind - interface
 * or class - as OptiFine's own copy, which matters because a class is loaded against the supertype
 * it declares.</p>
 */
public final class ForgeApiShims {
	/** The package NeoForge does not provide, and the one the stubs have to live in. */
	private static final String FORGE_PACKAGE = "net/minecraftforge/";
	/** Where OptiFine keeps its own copy of those classes, which tells us the shape. */
	private static final String OPTIFINE_COPY = "notch/";
	private static final Pattern REFERENCE = Pattern.compile("net/minecraftforge/[A-Za-z0-9_/$]+");

	private ForgeApiShims() {
	}

	/**
	 * Every {@code net/minecraftforge} type named anywhere in the OptiFine jar, as internal names.
	 *
	 * <p>Both the classes and the patch payload are searched. The payload matters as much as the
	 * classes: OptiFine's patches make vanilla classes implement Forge interfaces - the first
	 * failure of this kind was {@code IForgeVertexConsumer} being added to
	 * {@code com.mojang.blaze3d.vertex.VertexConsumer} - and the patched class is what the loader
	 * then has to resolve. That name appears nowhere in the jar's classes, only in
	 * {@code patch/srg/.../VertexConsumer.class.xdelta}.</p>
	 *
	 * <p>Classes are read as bytes and searched rather than parsed: the constant pool holds these
	 * names, and searching the raw bytes catches the call sites as well as the signatures.</p>
	 */
	public static Set<String> referencedTypes(List<Path> jars) throws IOException {
		Set<String> names = new TreeSet<>();
		for(Path jar : jars) {
			forEachEntry(jar, (entryName, bytes) -> {
				String text = new String(bytes, StandardCharsets.ISO_8859_1);
				Matcher matcher = REFERENCE.matcher(text);
				while(matcher.find()) {
					names.add(matcher.group());
				}
			});
		}
		return names;
	}

	/**
	 * The members OptiFine's classes name on each Forge type: fields read, methods called.
	 *
	 * <p>Only real class files can be read this way, so the patch payload contributes nothing here -
	 * it is xdelta data, not bytecode. That is the right way round: a payload reference is a
	 * signature to satisfy, while the calls that need members are in the classes OptiFine ships.</p>
	 */
	public static Map<String, Shape> referencedMembers(List<Path> jars) throws IOException {
		Map<String, Shape> shapes = new LinkedHashMap<>();
		for(Path jar : jars) {
			readMembers(jar, shapes);
		}
		return shapes;
	}

	/** Adds what one jar's classes call on Forge types to the shapes being collected. */
	private static void readMembers(Path jar, Map<String, Shape> shapes) throws IOException {
		try(ZipFile zip = new ZipFile(jar.toFile())) {
			for(Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
				ZipEntry entry = it.nextElement();
				String entryName = entry.getName();
				if(entry.isDirectory() || !entryName.endsWith(".class") || entryName.startsWith(OPTIFINE_COPY)) {
					continue;
				}
				byte[] bytes = readAll(zip.getInputStream(entry));
				try {
					new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
						@Override
						public MethodVisitor visitMethod(int access, String name, String desc, String signature,
								String[] exceptions) {
							return new MethodVisitor(Opcodes.ASM9) {
								@Override
								public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDesc) {
									if(!owner.startsWith(FORGE_PACKAGE)) {
										return;
									}
									record(owner, shapes).fields.put(fieldName + " " + fieldDesc,
											new FieldReference(fieldName, fieldDesc,
													opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC));
								}

								@Override
								public void visitMethodInsn(int opcode, String owner, String methodName, String methodDesc,
										boolean isInterface) {
									if(!owner.startsWith(FORGE_PACKAGE)) {
										return;
									}
									record(owner, shapes).methods.put(methodName + " " + methodDesc,
											new MethodReference(methodName, methodDesc, opcode == Opcodes.INVOKESTATIC));
								}
							};
						}
					}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
				} catch(RuntimeException unreadable) {
					// A class file that will not parse names no members: the type-level pass still
					// covers it, because that one searches bytes rather than parsing.
				}
			}
		}
	}

	/** The shape being collected for one Forge type. */
	private static Shape record(String owner, Map<String, Shape> shapes) {
		return shapes.computeIfAbsent(owner, name -> new Shape());
	}

	/** A stub class file per referenced type, keyed by internal name, ready to be added to the jar. */
	public static Map<String, byte[]> generate(List<Path> jars) throws IOException {
		Set<String> names = referencedTypes(jars);
		Map<String, Shape> shapes = referencedMembers(jars);
		Map<String, byte[]> stubs = new LinkedHashMap<>();
		// The shape of a type (interface or class) comes from OptiFine's own copy, which ships with
		// the OptiFine jar rather than with the patched classes.
		try(ZipFile zip = new ZipFile(jars.get(jars.size() - 1).toFile())) {
			for(String name : names) {
				Shape shape = shapes.getOrDefault(name, new Shape());
				stubs.put(name + ".class", stub(name, isInterface(zip, name), shape));
			}
		}
		return stubs;
	}

	/** Whether OptiFine's own copy of this type is an interface. */
	private static boolean isInterface(ZipFile zip, String name) {
		ZipEntry own = zip.getEntry(OPTIFINE_COPY + name + ".class");
		if(own == null) {
			// Nothing to go on: an interface is the safer shape, since a class that implements an
			// empty interface still verifies while the reverse does not hold for every use.
			return true;
		}
		try(InputStream stream = zip.getInputStream(own)) {
			return (new ClassReader(readAll(stream)).getAccess() & Opcodes.ACC_INTERFACE) != 0;
		} catch(IOException e) {
			return true;
		}
	}

	private static byte[] stub(String internalName, boolean isInterface, Shape shape) {
		ClassWriter writer = new ClassWriter(0);
		int access = Opcodes.ACC_PUBLIC | (isInterface ? Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT : Opcodes.ACC_SUPER);
		writer.visit(Opcodes.V17, access, internalName, null, "java/lang/Object", null);
		boolean tracksEmptiness = !isInterface && shape.methods.containsKey("isEmpty ()Z");
		if(tracksEmptiness) {
			// A shell that is asked isEmpty() can answer it correctly if it remembers which
			// constructor produced it: the no-argument one means "the EMPTY constant", the one
			// OptiFine calls means a group it built itself.
			writer.visitField(Opcodes.ACC_PRIVATE, EMPTY_FLAG, "Z", null, null).visitEnd();
		}
		// Every member is written once, and the shell's own no-argument constructor is the first entry
		// in that record. The payload referencing <init>()V as well is what produced two of them:
		//   ClassFormatError: Duplicate method name "<init>" with signature "()V" in class file
		//   net/minecraftforge/client/model/ForgeFaceData
		// which killed the launch inside OptiFine's Reflector, on its way to loading that class.
		java.util.Set<String> written = new java.util.HashSet<>();
		if(!isInterface) {
			writeConstructor(writer, internalName, "()V", tracksEmptiness);
			written.add("<init>()V");
		}
		java.util.List<String> selfTypedConstants = new java.util.ArrayList<>();
		for(FieldReference field : shape.fields.values()) {
			int fieldAccess = Opcodes.ACC_PUBLIC | (field.isStatic ? Opcodes.ACC_STATIC : 0);
			if(isInterface) {
				fieldAccess |= Opcodes.ACC_STATIC | Opcodes.ACC_FINAL; // interfaces only have those
			}
			writer.visitField(fieldAccess, field.name, field.desc, null, null).visitEnd();
			if(field.isStatic && ("L" + internalName + ";").equals(field.desc)) {
				// A static field of its own type on an API class is a constant the API defines - Forge's
				// RenderTypeGroup.EMPTY is one - and a null constant is what OptiFine then calls
				// isEmpty() on. Each gets an instance in <clinit> instead.
				selfTypedConstants.add(field.name);
			}
		}
		// The same member can arrive from more than one place - a constructor the payload references
		// and the one this shell already has are the case that produced a class the JVM refused.
		for(MethodReference method : shape.methods.values()) {
			if(!written.add(method.name + method.desc)) {
				continue;
			}
			if("<init>".equals(method.name)) {
				if(!isInterface) {
					writeConstructor(writer, internalName, method.desc, tracksEmptiness);
				}
				continue;
			}
			int methodAccess = Opcodes.ACC_PUBLIC | (method.isStatic ? Opcodes.ACC_STATIC : 0);
			if(isInterface && !method.isStatic) {
				// A call on the interface itself cannot be answered, and an abstract method is the
				// shape that says so instead of pretending.
				writer.visitMethod(methodAccess | Opcodes.ACC_ABSTRACT, method.name, method.desc, null, null).visitEnd();
				continue;
			}
			if(tracksEmptiness && "isEmpty".equals(method.name) && "()Z".equals(method.desc)) {
				MethodVisitor isEmpty = writer.visitMethod(methodAccess, method.name, method.desc, null, null);
				isEmpty.visitCode();
				isEmpty.visitVarInsn(Opcodes.ALOAD, 0);
				isEmpty.visitFieldInsn(Opcodes.GETFIELD, internalName, EMPTY_FLAG, "Z");
				isEmpty.visitInsn(Opcodes.IRETURN);
				isEmpty.visitMaxs(1, 1);
				isEmpty.visitEnd();
				continue;
			}
			MethodVisitor body = writer.visitMethod(methodAccess, method.name, method.desc, null, null);
			body.visitCode();
			switch(Type.getReturnType(method.desc).getSort()) {
				case Type.VOID -> body.visitInsn(Opcodes.RETURN);
				case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> {
					body.visitInsn(Opcodes.ICONST_0);
					body.visitInsn(Opcodes.IRETURN);
				}
				case Type.LONG -> {
					body.visitInsn(Opcodes.LCONST_0);
					body.visitInsn(Opcodes.LRETURN);
				}
				case Type.FLOAT -> {
					body.visitInsn(Opcodes.FCONST_0);
					body.visitInsn(Opcodes.FRETURN);
				}
				case Type.DOUBLE -> {
					body.visitInsn(Opcodes.DCONST_0);
					body.visitInsn(Opcodes.DRETURN);
				}
				default -> {
					body.visitInsn(Opcodes.ACONST_NULL);
					body.visitInsn(Opcodes.ARETURN);
				}
			}
			int locals = Type.getArgumentsAndReturnSizes(method.desc) >> 2;
			body.visitMaxs(2, Math.max(1, locals));
			body.visitEnd();
		}
		if(!selfTypedConstants.isEmpty()) {
			// The constants are built by calling the no-argument constructor, so the shell has to have
			// one even when the payload never referenced it.
			if(written.add("<init>()V")) {
				writeConstructor(writer, internalName, "()V", tracksEmptiness);
			}
			MethodVisitor clinit = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
			clinit.visitCode();
			for(String constant : selfTypedConstants) {
				clinit.visitTypeInsn(Opcodes.NEW, internalName);
				clinit.visitInsn(Opcodes.DUP);
				clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, internalName, "<init>", "()V", false);
				clinit.visitFieldInsn(Opcodes.PUTSTATIC, internalName, constant, "L" + internalName + ";");
			}
			clinit.visitInsn(Opcodes.RETURN);
			clinit.visitMaxs(2, 0);
			clinit.visitEnd();
		}
		writer.visitEnd();
		return writer.toByteArray();
	}

	/** The name of the field a shell uses to remember that it stands for the empty constant. */
	private static final String EMPTY_FLAG = "optifineoforge$empty";

	/**
	 * A constructor that does nothing but chain to {@code Object}, for whatever arguments it takes.
	 *
	 * <p>When the shell tracks emptiness, the no-argument constructor is the empty constant and every
	 * other one is a value OptiFine built, so which constructor ran is recorded for {@code isEmpty}.</p>
	 */
	private static void writeConstructor(ClassWriter writer, String internalName, String desc, boolean trackEmptiness) {
		MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", desc, null, null);
		constructor.visitCode();
		constructor.visitVarInsn(Opcodes.ALOAD, 0);
		constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		if(trackEmptiness) {
			constructor.visitVarInsn(Opcodes.ALOAD, 0);
			constructor.visitInsn("()V".equals(desc) ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
			constructor.visitFieldInsn(Opcodes.PUTFIELD, internalName, EMPTY_FLAG, "Z");
		}
		constructor.visitInsn(Opcodes.RETURN);
		constructor.visitMaxs(2, Math.max(1, Type.getArgumentsAndReturnSizes(desc) >> 2));
		constructor.visitEnd();
	}

	private static void forEachEntry(Path jar, EntryVisitor visitor) throws IOException {
		try(ZipFile zip = new ZipFile(jar.toFile())) {
			for(Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
				ZipEntry entry = it.nextElement();
				String entryName = entry.getName();
				if(entry.isDirectory() || entryName.startsWith("assets/") || entryName.startsWith("doc/")) {
					continue;
				}
				if(entryName.startsWith(OPTIFINE_COPY)) {
					// OptiFine's own obfuscated-namespace copies are dropped from the jar we ship,
					// so nothing they name has to resolve.
					continue;
				}
				visitor.visit(entryName, readAll(zip.getInputStream(entry)));
			}
		}
	}

	private interface EntryVisitor {
		void visit(String entryName, byte[] bytes);
	}

	private static byte[] readAll(InputStream stream) throws IOException {
		try(stream) {
			return stream.readAllBytes();
		}
	}

	/** A short summary of what the shims would cover. */
	public static String describe(List<Path> jars) throws IOException {
		Set<String> names = referencedTypes(jars);
		Set<String> packages = new LinkedHashSet<>();
		int members = 0;
		for(String name : names) {
			int lastSlash = name.lastIndexOf('/');
			packages.add(lastSlash < 0 ? name : name.substring(0, lastSlash));
		}
		for(Shape shape : referencedMembers(jars).values()) {
			members += shape.fields.size() + shape.methods.size();
		}
		return names.size() + " referenced Forge types in " + packages.size() + " packages, " + members
				+ " members named on them";
	}

	/** The members named on one Forge type. */
	public static final class Shape {
		final Map<String, FieldReference> fields = new LinkedHashMap<>();
		final Map<String, MethodReference> methods = new LinkedHashMap<>();

		/** How many members are named on this type. */
		public int size() {
			return fields.size() + methods.size();
		}
	}

	private record FieldReference(String name, String desc, boolean isStatic) {
	}

	private record MethodReference(String name, String desc, boolean isStatic) {
	}

	/**
	 * Development aid: {@code ForgeApiShims <out dir> <jar> [jar...]} writes the shims for the class
	 * references found in those jars; {@code ForgeApiShims report <jar> [jar...]} lists them.
	 */
	public static void main(String[] args) throws Exception {
		if(args.length == 0) {
			System.err.println("usage: ForgeApiShims <out dir|report> <jar> [jar...]");
			System.exit(2);
		}
		boolean report = "report".equals(args[0]);
		List<Path> jars = new java.util.ArrayList<>();
		for(int index = 1; index < args.length; index++) {
			jars.add(Path.of(args[index]));
		}
		if(jars.isEmpty()) {
			System.err.println("usage: ForgeApiShims <out dir|report> <jar> [jar...]");
			System.exit(2);
		}
		Set<String> names = referencedTypes(jars);
		Map<String, Shape> shapes = referencedMembers(jars);
		System.out.println(describe(jars));
		if(report) {
			for(String name : names) {
				Shape shape = shapes.get(name);
				System.out.println("  " + name + (shape == null ? "" : "  (" + shape.size() + " members)"));
				if(shape != null && args.length > 3) {
					shape.fields.values().forEach(field -> System.out.println("      field " + field));
					shape.methods.values().forEach(method -> System.out.println("      call  " + method));
				}
			}
			return;
		}
		Path out = Path.of(args[0]);
		Map<String, byte[]> stubs = generate(jars);
		for(Map.Entry<String, byte[]> entry : stubs.entrySet()) {
			Path target = out.resolve(entry.getKey());
			java.nio.file.Files.createDirectories(target.getParent());
			java.nio.file.Files.write(target, entry.getValue());
		}
		System.out.println("wrote " + stubs.size() + " stubs to " + out);
	}
}
