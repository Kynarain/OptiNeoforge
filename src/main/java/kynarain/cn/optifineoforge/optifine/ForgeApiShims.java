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
import org.objectweb.asm.FieldVisitor;
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
		// own copy - and then the interface fallback stands.
		try(ZipFile zip = new ZipFile(jars.get(jars.size() - 1).toFile())) {
			// Every Forge API type OptiFine ships goes in, whether or not a reference scan saw it. The scan
			// only sees names written into descriptors and instructions, and that is not the whole need:
			// ModLauncher walks a class's superclasses and interfaces while transforming it, and that walk
			// needs the type present even when no bytecode names it. Measured on 1.21.8, after the shapes
			// and the members were already right:
			//
			//   RuntimeException: Cannot find class net/minecraftforge/common/extensions/IForgeEntity
			//     at cpw.mods.modlauncher.TransformerClassWriter.computeHierarchyFromFile
			//
			// IForgeEntity is in OptiFine's own jar under notch/net/minecraftforge/**, and it was in
			// neither the scan's 60 types nor the synthesised set.
			java.util.Set<String> wanted = new java.util.LinkedHashSet<>(names);
			for(Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
				String entryName = entries.nextElement().getName();
				if(entryName.startsWith(OPTIFINE_COPY + FORGE_PACKAGE) && entryName.endsWith(".class")) {
					wanted.add(entryName.substring(OPTIFINE_COPY.length(), entryName.length() - ".class".length()));
				}
			}
			for(String name : wanted) {
				// When OptiFine does ship its own copy of the type, that copy is where the members come
				// from: it is the Forge API OptiFine was compiled against, and synthesising from call sites
				// alone loses every member it declares that OptiFine's own calls do not name on the type
				// itself, because a call can be written against a subtype and still resolve through here.
				// Measured on 1.21.8, where the gap cost a launch: IForgeGpuTexture.isStencilEnabled is
				// called as GpuTexture.isStencilEnabled, so the synthesised interface had no such method
				// and the client died with
				//
				//   NoSuchMethodError: 'boolean com.mojang.blaze3d.textures.GpuTexture.isStencilEnabled()'
				//
				// out of GlCommandEncoder.clearColorTexture - with OptiFine's own GpuTexture installed.
				//
				// The copy's members are taken, not its bytes: its own signatures are written in OptiFine's
				// obfuscated game namespace, and shipping it whole fails the other way round, measured as
				// "NoClassDefFoundError: avs" once a class that implements one of these is defined. So only
				// the members whose signatures name a packaged type are copied.
				Shape shape = shapes.getOrDefault(name, new Shape());
				byte[] own = readOwnCopy(zip, name);
				if(own != null) {
					addOwnMembers(own, shape);
				}
				boolean asInterface = shape.mustBeClass() ? false
						: (shape.callsThroughInterface() || (!declaresItself(shape, name) && isInterface(zip, name)));
				stubs.put(name + ".class", stub(name, asInterface, shape));
				if(asInterface && needsNoop(name, shape)) {
					// The factory answers an instance of the type it belongs to, and a self-typed static
					// constant is one too, so a shell for that type is needed beside it; written into the same
					// map so the two travel into the jar together.
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

	/**
	 * Adds the members OptiFine's own copy of a Forge type declares, skipping the ones written against its
	 * obfuscated game namespace.
	 *
	 * <p>Only declarations are read, never method bodies: the shell this feeds declares the same methods as
	 * abstract, so nothing of the copy's own code travels, and a signature naming {@code avs} - OptiFine's
	 * name for a game class inside {@code notch/} - is dropped rather than reproduced.</p>
	 */
	private static void addOwnMembers(byte[] classBytes, Shape shape) {
		new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
				if(cleanDescriptor(desc)) {
					shape.fields.putIfAbsent(name + " " + desc,
							new FieldReference(name, desc, (access & Opcodes.ACC_STATIC) != 0));
				}
				return null;
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
				// Constructors and the static initialiser are the shell's own business, not members to
				// reproduce: it writes a constructor and, when it has fields to fill, a static initialiser.
				if(("<init>".equals(name) || "<clinit>".equals(name)) && cleanDescriptor(desc)) {
					return null;
				}
				if(cleanDescriptor(desc)) {
					shape.methods.putIfAbsent(name + " " + desc,
							// A declaration read from OptiFine's own copy, not a call site, so it carries no
							// reference form: false. Only the handover's own calls can say "interface".
							new MethodReference(name, desc, (access & Opcodes.ACC_STATIC) != 0, false));
				}
				return null;
			}
		}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
	}

	/**
	 * Whether a descriptor names only types this jar can resolve.
	 *
	 * <p>The test is whether the type is in a package at all: OptiFine's obfuscated game classes are single
	 * segments ({@code avs}, {@code fmk}), while everything this jar can reach - {@code java/lang},
	 * {@code net/minecraft}, {@code net/minecraftforge}, {@code com/mojang} - has a slash in it.</p>
	 */
	private static boolean cleanDescriptor(String desc) {
		if(desc == null) {
			return true;
		}
		for(int index = 0; index < desc.length(); index++) {
			if(desc.charAt(index) != 'L') {
				continue;
			}
			int end = desc.indexOf(';', index);
			if(end < 0) {
				return false;
			}
			String type = desc.substring(index + 1, end);
			if(!type.contains("/")) {
				return false;
			}
			index = end;
		}
		return true;
	}

	/**
	 * OptiFine's own copy of a Forge API type, when it ships one, or null.
	 *
	 * <p>It ships them under the same {@code notch/} prefix as its game classes, which is why they are easy
	 * to miss: the prefix suggests obfuscated game bytecode, but a Forge API type keeps its real package
	 * there. Measured on 1.21.8, five classes name {@code isStencilEnabled} and one of them is
	 * {@code notch/net/minecraftforge/client/extensions/IForgeGpuTexture.class} inside OptiFine's own jar.
	 * Taking that copy is both more complete and more honest than guessing at the interface.</p>
	 */
	private static byte[] readOwnCopy(ZipFile zip, String name) {
		ZipEntry own = zip.getEntry(OPTIFINE_COPY + name + ".class");
		if(own == null) {
			return null;
		}
		try(InputStream stream = zip.getInputStream(own)) {
			return readAll(stream);
		} catch(IOException e) {
			return null;
		}
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
		// The no-argument constructor is written only when the recorded uses do not already name one: the
		// loop below emits every recorded method, and a recorded "<init> ()V" would be a second copy of the
		// same method. That is not a warning but a rejection, measured on 1.21.8:
		//
		//   ClassFormatError: Duplicate method name "<init>" with signature "()V" in class file
		//     net/minecraftforge/common/capabilities/CapabilityProvider$BlockEntities
		boolean recordedNoArgConstructor = shape.methods.values().stream()
				.anyMatch(method -> "<init>".equals(method.name) && "()V".equals(method.desc));
		if(!isInterface && !recordedNoArgConstructor) {
			writeConstructor(writer, internalName, "()V", tracksEmptiness);
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
		for(MethodReference method : shape.methods.values()) {
			if("<clinit>".equals(method.name)) {
				// The shell writes its own static initialiser when it has fields to fill, so a recorded one
				// would be a second copy of the same method - rejected rather than warned about, measured on
				// 1.21.8: "ClassFormatError: Duplicate method name "<clinit>" with signature "()V" in class
				// file net/minecraftforge/client/model/ForgeFaceData".
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
				// A static factory that hands back a value of the type that declares it must not answer null:
				// the caller's very next instruction uses the result. Measured on 1.20.6, where
				// IClientBlockExtensions.of(BlockState) answered null and OptiFine's ParticleEngine calls
				// addHitEffects/addDestroyEffects on it immediately, so the first attack or block break of a
				// session died with
				//   NullPointerException: Cannot invoke "...addHitEffects(...)" because the return value of
				//   "...IClientBlockExtensions.of(BlockState)" is null
				//   at ParticleEngine.addBlockHitEffects(ParticleEngine.java:823) <- Minecraft.continueAttack
				// Every line whose payload names these Forge extension interfaces carries the same shim - the
				// 1.21.x jars reference IClientBlockExtensions in 3 classes and IClientFluidTypeExtensions in
				// 4 - so this is one rule, not one case. An interface cannot be instantiated, hence the Noop.
				body.visitCode();
				String implementation = isInterface ? internalName + "$Noop" : internalName;
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
			// An interface cannot be instantiated, so an interface's own constant is the Noop implementation
			// beside it; a class's is an instance of itself.
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
	 * <p>It exists because an interface shell cannot answer a static factory of its own type with an instance -
	 * an interface cannot be instantiated - and answering null crashes the caller one instruction later (see the
	 * comment on the factory body in {@link #stub}). It implements the methods OptiFine was seen calling on the
	 * type; anything else it does not declare throws {@code AbstractMethodError} if it is ever invoked, which is
	 * no worse than the {@code NoSuchMethodError} a null would have produced.</p>
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
		body.visitMaxs(2, Math.max(1, Type.getArgumentsAndReturnSizes(desc) >> 2));
		body.visitEnd();
	}

	/**
	 * A constructor that does nothing but chain to {@code Object}, for whatever arguments it takes.
	 *
	 * <p>When the shell tracks emptiness, the no-argument constructor is the empty constant and every
	 * other one is a value OptiFine built, so which constructor ran is recorded for {@code isEmpty}.</p>
	 *
	 * <p>A shell never extends anything but {@code Object}, and that is a measured limit rather than a
	 * choice: this jar's module sits below the game layer, so a shim extending a game-layer type cannot
	 * resolve it - {@code NoClassDefFoundError: net/neoforged/neoforge/attachment/AttachmentHolder} from
	 * {@code ModuleClassLoader.loadFromModule}. A payload class that extends a Forge type therefore has
	 * its superclass rewritten onto the runtime's by the loader instead, where the class is defined in
	 * the game layer and the reference resolves.</p>
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
		 * "interface", and the JVM refuses the call if a class is what resolves. It is the only piece of
		 * evidence about the kind that comes from the handover rather than from a guess, so it wins over
		 * {@link #declaresItself} - see {@link #generate}. Measured on this branch's own jars: the payload's
		 * ParticleEngine calls {@code IClientBlockExtensions.of} through an InterfaceMethodref and calls
		 * {@code addHitEffects} with invokeinterface, while the shipped shell was a class.</p>
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
