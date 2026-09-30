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
						public void visit(int version, int access, String name, String signature, String superName,
								String[] interfaces) {
							// Extending a Forge type is one of the two uses that force the stub to be a
							// class: the JVM rejects a class whose superclass is an interface, which is
							// how the first of the two failures above presented itself.
							if(superName != null && superName.startsWith(FORGE_PACKAGE)) {
								record(superName, shapes).extended = true;
							}
							super.visit(version, access, name, signature, superName, interfaces);
						}

						@Override
						public MethodVisitor visitMethod(int access, String name, String desc, String signature,
								String[] exceptions) {
							return new MethodVisitor(Opcodes.ASM9) {
								@Override
								public void visitTypeInsn(int opcode, String type) {
									// `new` on a Forge type is the other: an interface cannot be
									// instantiated, which is how the second failure presented itself.
									if(opcode == Opcodes.NEW && type.startsWith(FORGE_PACKAGE)) {
										record(type, shapes).instantiated = true;
									}
								}
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
									if("<init>".equals(methodName)) {
										// A constructor call means an instance was built, so a class.
										record(owner, shapes).instantiated = true;
									}
									record(owner, shapes).methods.put(methodName + " " + methodDesc,
											new MethodReference(methodName, methodDesc,
													opcode == Opcodes.INVOKESTATIC, isInterface));
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
		// The shape of a type is decided from how it is used first; see Shape.mustBeClass(). Only when
		// nothing in the handover shows a class-only use does the older rule apply - reading OptiFine's
		// own copy, which for a Forge type never exists - and then the interface fallback stands.
		//
		// The strongest evidence of all is the reference form the caller wrote. A call site whose constant
		// pool entry is an InterfaceMethodref - ASM reports that as isInterface - can only resolve against
		// an interface, and a class there raises
		//   IncompatibleClassChangeError: Found class ..., but interface was expected
		// Measured on 1.20.2, 1.20.4 and 1.21.8: their payload's ParticleEngine calls
		// IClientBlockExtensions.of through an InterfaceMethodref and calls addHitEffects with
		// invokeinterface, while the shipped shell was a class - because declaresItself() forced one for the
		// DUMMY field Forge declares on that interface. A self-typed static field no longer forces a class:
		// an interface can fill it in its own <clinit> with the same Noop implementation the factory returns.
		try(ZipFile zip = new ZipFile(jars.get(jars.size() - 1).toFile())) {
			for(String name : names) {
				Shape shape = shapes.getOrDefault(name, new Shape());
				boolean asInterface = shape.mustBeClass() ? false
						: (shape.callsThroughInterface() || (!declaresItself(shape, name) && isInterface(zip, name)));
				stubs.put(name + ".class", stub(name, asInterface, shape));
				if(asInterface && needsNoop(name, shape)) {
					// The factory answers an instance of the type it belongs to, and a self-typed static
					// constant is one too, so a shell for that type is needed as well; written beside it so
					// the two travel together into the jar.
					stubs.put(name + "$Noop.class", noopImplementation(name, shape));
				}
			}
		}
		return stubs;
	}

	/**
	 * Whether the shape declares a field of the type's own type.
	 *
	 * <p>Such a field is one the stub has to fill in itself - {@code RenderTypeGroup.EMPTY} is the case
	 * that found this - and filling it means constructing an instance, which an interface cannot do. It
	 * failed as the stub's own static initialiser rather than at a call site:</p>
	 *
	 * <pre>java.lang.InstantiationError: net.minecraftforge.client.RenderTypeGroup
	 *   at net.minecraftforge.client.RenderTypeGroup.&lt;clinit&gt;</pre>
	 */
	private static boolean declaresItself(Shape shape, String name) {
		String self = "L" + name + ";";
		for(FieldReference field : shape.fields.values()) {
			if(self.equals(field.desc())) {
				return true;
			}
		}
		return false;
	}

	/** Whether OptiFine's own copy of this type is an interface. */
	private static boolean isInterface(ZipFile zip, String name) {		ZipEntry own = zip.getEntry(OPTIFINE_COPY + name + ".class");
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
			if(method.isStatic && selfTyped(method.desc, internalName)) {
				// A static factory that hands back a value of the type that declares it must not answer
				// null, because the caller's very next instruction uses the result. Measured on 1.20.6:
				// IClientBlockExtensions.of(BlockState) returned null and OptiFine's ParticleEngine calls
				// addHitEffects/addDestroyEffects on it immediately, so the client died with
				//   NullPointerException: Cannot invoke "...addHitEffects(...)" because the return value of
				//   "...IClientBlockExtensions.of(BlockState)" is null
				//   at ParticleEngine.addBlockHitEffects(ParticleEngine.java:823) <- Minecraft.continueAttack
				// on the first attack or block break of a session. An interface cannot be instantiated, so
				// an interface gets a do-nothing implementation of itself beside it.
				String implementation = isInterface ? internalName + "$Noop" : internalName;
				body.visitCode();
				body.visitTypeInsn(Opcodes.NEW, implementation);
				body.visitInsn(Opcodes.DUP);
				body.visitMethodInsn(Opcodes.INVOKESPECIAL, implementation, "<init>", "()V", false);
				body.visitInsn(Opcodes.ARETURN);
				body.visitMaxs(2, Math.max(1, Type.getArgumentsAndReturnSizes(method.desc) >> 2));
				body.visitEnd();
				continue;
			}
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
			// one even when the payload never referenced it - unless it is an interface, which cannot be
			// instantiated at all and whose constant is the Noop implementation beside it instead.
			if(!isInterface && written.add("<init>()V")) {
				writeConstructor(writer, internalName, "()V", tracksEmptiness);
			}
			String implementation = isInterface ? internalName + "$Noop" : internalName;
			MethodVisitor clinit = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
			clinit.visitCode();
			for(String constant : selfTypedConstants) {
				clinit.visitTypeInsn(Opcodes.NEW, implementation);
				clinit.visitInsn(Opcodes.DUP);
				clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, implementation, "<init>", "()V", false);
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

	/** Whether a method with this descriptor hands back the very type that declares it. */
	private static boolean selfTyped(String desc, String internalName) {
		return ("L" + internalName + ";").equals(Type.getReturnType(desc).getDescriptor());
	}

	/** Whether any static call recorded on this type returns the type itself. */
	private static boolean hasSelfTypedFactory(String internalName, Shape shape) {
		for(MethodReference method : shape.methods.values()) {
			if(method.isStatic && selfTyped(method.desc, internalName)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether this shell has to have a concrete implementation of itself beside it.
	 *
	 * <p>Two things need one: a static factory that hands the type back (it must not answer null), and a
	 * static field of the type's own type (the API's own constant, which a null would break just as badly).</p>
	 */
	private static boolean needsNoop(String internalName, Shape shape) {
		if(hasSelfTypedFactory(internalName, shape)) {
			return true;
		}
		String self = "L" + internalName + ";";
		for(FieldReference field : shape.fields.values()) {
			if(field.isStatic() && self.equals(field.desc())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A concrete do-nothing implementation of one interface shell, named {@code <interface>$Noop}.
	 *
	 * <p>It exists because an interface shell cannot answer a static factory of its own type with an
	 * instance - an interface cannot be instantiated - and answering null crashes the caller, which is
	 * what happened on 1.20.6 (see the comment on the factory body in {@link #stub}). The methods it
	 * implements are the ones OptiFine was seen calling on the type; anything else it does not declare
	 * throws {@code AbstractMethodError} if it is ever invoked, which is no worse than the
	 * {@code NoSuchMethodError} a null would have produced one instruction later.</p>
	 */
	private static byte[] noopImplementation(String internalName, Shape shape) {
		String noop = internalName + "$Noop";
		ClassWriter writer = new ClassWriter(0);
		writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, noop, null, "java/lang/Object",
				new String[] {internalName});
		writeConstructor(writer, noop, "()V", false);
		java.util.Set<String> written = new java.util.HashSet<>();
		written.add("<init>()V");
		for(MethodReference method : shape.methods.values()) {
			if(method.isStatic || "<init>".equals(method.name) || !written.add(method.name + method.desc)) {
				continue;
			}
			writeDoNothing(writer, Opcodes.ACC_PUBLIC, method.name, method.desc);
		}
		writer.visitEnd();
		return writer.toByteArray();
	}

	/** A method whose body is the do-nothing value for its return type. */
	private static void writeDoNothing(ClassWriter writer, int access, String name, String desc) {
		MethodVisitor body = writer.visitMethod(access, name, desc, null, null);
		body.visitCode();
		switch(Type.getReturnType(desc).getSort()) {
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
		int locals = Type.getArgumentsAndReturnSizes(desc) >> 2;
		body.visitMaxs(2, Math.max(1, locals));
		body.visitEnd();
	}

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

		/** Set when a class is seen extending this type, which only a class allows. */
		boolean extended;

		/** Set when a class is seen constructing this type, which only a class allows. */
		boolean instantiated;

		/**
		 * Whether this type has to be emitted as a class rather than an interface.
		 *
		 * <p>Measured rather than assumed, because the rule before this was neither: the shape used to be
		 * read from OptiFine's own copy of the type, under {@code notch/} - and for a Forge API type that
		 * copy cannot exist, since OptiFine ships game classes and not Forge's. The lookup therefore never
		 * found anything and every stub came out an interface. Two of them then failed in the game, both
		 * reported from a real launch on 21.4.149:</p>
		 *
		 * <pre>IncompatibleClassChangeError: class net.minecraft.world.level.block.entity.BlockEntity
		 *   has interface net.minecraftforge.common.capabilities.CapabilityProvider as super class
		 * java.lang.InstantiationError: net.minecraftforge.client.RenderTypeGroup</pre>
		 *
		 * <p>Both uses are recorded while the referenced classes are read, so the decision comes from how
		 * the type is really used instead of from a fallback.</p>
		 */
		boolean mustBeClass() {
			return extended || instantiated;
		}

		/**
		 * Whether any recorded call site referenced this type as an interface.
		 *
		 * <p>{@code InterfaceMethodref} is a promise about the type: the caller's constant pool says
		 * "interface", and the JVM refuses the call if a class is what resolves. This is the only piece of
		 * evidence about the kind that comes from the handover rather than from a guess, so it wins over
		 * {@link #declaresItself} - see {@link #generate}.</p>
		 */
		boolean callsThroughInterface() {
			for(MethodReference method : methods.values()) {
				if(method.interfaceRef()) {
					return true;
				}
			}
			return false;
		}

		/** How many members are named on this type. */
		public int size() {
			return fields.size() + methods.size();
		}
	}

	private record FieldReference(String name, String desc, boolean isStatic) {
	}

	private record MethodReference(String name, String desc, boolean isStatic, boolean interfaceRef) {
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
