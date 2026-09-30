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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Works out which members OptiFine's replacements drop, before the game runs.
 *
 * <p>Every class OptiFine replaces is one it compiled itself, against Forge - and Forge's copy of
 * that class is not NeoForge's. Whatever NeoForge added to the class since is gone, and the game
 * meets it one member at a time. So they are found here instead: take the classes OptiFine produces
 * (the patcher's output) and the classes the game actually has (the NeoForge client jar), and list
 * what is in one and not the other.</p>
 *
 * <p>Finding them is not enough, because a restored body has to verify. A body that names another
 * dropped member - in its own class or, just as often, in a class that was replaced too - does not
 * load at all, so the search follows those references and restores what they point at, across
 * classes, until nothing new appears. What is left is written out as a donor class per affected
 * class: the members with their original bodies, for the transformer to copy in.</p>
 *
 * <p>The plan is a plain text file, one member per line:</p>
 *
 * <pre>
 * F &lt;class&gt; &lt;field name&gt; &lt;field descriptor&gt;
 * M &lt;class&gt; &lt;method name&gt; &lt;method descriptor&gt;
 * </pre>
 */
public final class MemberRestorePlan {
	/** The patcher writes OptiFine's classes here. */
	private static final String PATCHED_ROOT = "srg/";
	/** Where the environment assertions live that a copied body has to be freed of. */
	private static final String RENDER_SYSTEM = "com/mojang/blaze3d/systems/RenderSystem";
	/** How far the reference closure may grow before it gives up. */
	private static final int MAX_CLOSURE_ROUNDS = 8;
	/** The name restored fields are initialised through, added to every constructor. */
	public static final String INITIALISER_PREFIX = "optifineoforge$init$";

	/**
	 * The only part of an initialiser method's name a reader may depend on, because a name is not a stable
	 * interface across a rename. Measured 2026-09-27: the donors the rig built on 2026-09-19 carry
	 * "optifineoforge$init$&lt;field&gt;", the recognisers had just been changed to the new prefix, and the
	 * initialisers stopped being recognised - they were installed as ordinary methods, the fields they fill
	 * stayed null, and 1.21.9 died inside Minecraft's constructor on a null Gui.layerManager. Recognition
	 * keys on the marker alone; generation keeps writing the prefixed name.
	 */
	public static final String INITIALISER_MARKER = "$init$";

	/** Whether a method name is one of these initialisers, whichever build wrote it. */
	public static boolean isInitialiser(String methodName) {
		return methodName.indexOf(INITIALISER_MARKER) >= 0;
	}

	/** The field an initialiser fills, or null when the name carries no marker. */
	public static String initialisedField(String methodName) {
		int at = methodName.indexOf(INITIALISER_MARKER);
		return at < 0 ? null : methodName.substring(at + INITIALISER_MARKER.length());
	}

	/**
	 * Members whose donor body must not be used, only stubbed.
	 *
	 * <p>NeoForge's GL state backup is called before the render thread is registered, and a body that
	 * does the real work walks into code that asserts otherwise, while the do-nothing stub merely
	 * leaves the state alone. Neither is right; restoring the state machine properly is separate
	 * work, so these four keep the stub.</p>
	 */
	private static final Set<String> STUB_ONLY = Set.of(
			"com/mojang/blaze3d/systems/RenderSystem backupGlState (Lnet/neoforged/neoforge/client/GlStateBackup;)V",
			"com/mojang/blaze3d/systems/RenderSystem restoreGlState (Lnet/neoforged/neoforge/client/GlStateBackup;)V",
			"com/mojang/blaze3d/platform/GlStateManager _backupGlState (Lnet/neoforged/neoforge/client/GlStateBackup;)V",
			"com/mojang/blaze3d/platform/GlStateManager _restoreGlState (Lnet/neoforged/neoforge/client/GlStateBackup;)V");

	private MemberRestorePlan() {
	}

	/** The lines of a plan: what the runtime classes have and OptiFine's replacements do not. */
	public static List<String> plan(Path patchedJar, Path runtimeJar) throws IOException {
		return plan(patchedJar, runtimeJar, null);
	}

	/** The same, and additionally a donor class per affected class when {@code donorDir} is given. */
	public static List<String> plan(Path patchedJar, Path runtimeJar, Path donorDir) throws IOException {
		if(donorDir != null) {
			Files.createDirectories(donorDir);
		}

		// Every replaced class and its runtime counterpart, held together: the closure below has to
		// look at classes other than the one it started from.
		Map<String, ClassNode> replacements = new LinkedHashMap<>();
		Map<String, ClassNode> runtimes = new LinkedHashMap<>();
		int skipped = 0;
		try(ZipFile patched = new ZipFile(patchedJar.toFile()); ZipFile runtime = new ZipFile(runtimeJar.toFile())) {
			for(Enumeration<? extends ZipEntry> it = patched.entries(); it.hasMoreElements();) {
				ZipEntry entry = it.nextElement();
				if(entry.isDirectory() || !entry.getName().startsWith(PATCHED_ROOT) || !entry.getName().endsWith(".class")) {
					continue;
				}
				String internalName = entry.getName().substring(PATCHED_ROOT.length(), entry.getName().length() - ".class".length());
				replacements.put(internalName, read(patched.getInputStream(entry)));
				ZipEntry counterpart = runtime.getEntry(internalName + ".class");
				if(counterpart == null) {
					skipped++; // a class OptiFine adds rather than replaces
					continue;
				}
				runtimes.put(internalName, read(runtime.getInputStream(counterpart)));
			}
		}

		Map<String, List<FieldNode>> fields = new LinkedHashMap<>();
		Map<String, List<MethodNode>> methods = new LinkedHashMap<>();
		for(Map.Entry<String, ClassNode> entry : runtimes.entrySet()) {
			ClassNode replacement = replacements.get(entry.getKey());
			fields.put(entry.getKey(), missingFields(replacement, entry.getValue()));
			methods.put(entry.getKey(), missingMethods(replacement, entry.getValue()));
		}

		int added = closeOverReferences(replacements, runtimes, fields, methods);

		List<String> lines = new ArrayList<>();
		for(Map.Entry<String, ClassNode> entry : runtimes.entrySet()) {
			String internalName = entry.getKey();
			List<FieldNode> classFields = fields.get(internalName);
			List<MethodNode> classMethods = methods.get(internalName);
			for(FieldNode field : classFields) {
				lines.add("F " + internalName + " " + field.name + " " + field.desc);
			}
			for(MethodNode method : classMethods) {
				lines.add("M " + internalName + " " + method.name + " " + method.desc);
			}
			if(donorDir != null && !(classFields.isEmpty() && classMethods.isEmpty())) {
				writeDonor(donorDir, internalName, replacements.get(internalName), entry.getValue(), classFields,
						classMethods, runtimeJar);
			}
		}

		System.out.println("compared " + runtimes.size() + " replaced classes (" + skipped + " without a runtime counterpart), "
				+ lines.size() + " members to restore, closure added " + added
				+ (donorDir == null ? "" : ", donors written to " + donorDir));
		return lines;
	}

	/**
	 * Restores what the restored bodies refer to, across classes, until nothing new appears.
	 *
	 * <p>A copied body often names a member that OptiFine dropped - sometimes one of its own class,
	 * more often one of another replaced class, since the replaced classes call each other. Following
	 * those references is the difference between a body that verifies and a body that has to fall
	 * back to returning a default value, which is how null block models reached the renderer. The
	 * search is bounded by rounds, and a member is only ever added once, so a cycle stops adding
	 * rather than looping.</p>
	 *
	 * @return how many members the closure added
	 */
	private static int closeOverReferences(Map<String, ClassNode> replacements, Map<String, ClassNode> runtimes,
			Map<String, List<FieldNode>> fields, Map<String, List<MethodNode>> methods) {
		int added = 0;
		for(int round = 0; round < MAX_CLOSURE_ROUNDS; round++) {
			int before = added;
			for(Map.Entry<String, List<MethodNode>> entry : methods.entrySet()) {
				String owner = entry.getKey();
				ClassNode ownerReplacement = replacements.get(owner);
				ClassNode ownerRuntime = runtimes.get(owner);
				if(ownerReplacement == null || ownerRuntime == null) {
					continue;
				}
				for(MethodNode method : new ArrayList<>(entry.getValue())) {
					if(method.instructions == null) {
						continue;
					}
					for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
						String target;
						String name;
						String desc;
						boolean isField;
						if(insn instanceof FieldInsnNode field) {
							target = field.owner; name = field.name; desc = field.desc; isField = true;
						} else if(insn instanceof MethodInsnNode call) {
							target = call.owner; name = call.name; desc = call.desc; isField = false;
							if("<init>".equals(name) || "<clinit>".equals(name)) {
								continue;
							}
						} else {
							continue;
						}
						// The reference may be to any replaced class, not only to the one whose body
						// it came from - and it is that class's replacement which has to gain the
						// member for the body to verify.
						ClassNode targetReplacement = replacements.get(target);
						ClassNode targetRuntime = runtimes.get(target);
						if(targetReplacement == null || targetRuntime == null) {
							continue;
						}
						if(hasMember(targetReplacement, name, desc) || isPlanned(fields, methods, target, name, desc)) {
							continue;
						}
						if(isField) {
							FieldNode candidate = findField(targetRuntime, name, desc);
							if(candidate != null) {
								fields.get(target).add(candidate);
								added++;
							}
						} else {
							MethodNode candidate = findMethod(targetRuntime, name, desc);
							if(candidate != null) {
								methods.get(target).add(candidate);
								added++;
							}
						}
					}
				}
			}
			if(added == before) {
				break;
			}
		}
		return added;
	}

	private static boolean isPlanned(Map<String, List<FieldNode>> fields, Map<String, List<MethodNode>> methods,
			String owner, String name, String desc) {
		if(desc.startsWith("(")) {
			for(MethodNode method : methods.get(owner)) {
				if(method.name.equals(name) && method.desc.equals(desc)) {
					return true;
				}
			}
			return false;
		}
		for(FieldNode field : fields.get(owner)) {
			if(field.name.equals(name) && field.desc.equals(desc)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasMember(ClassNode node, String name, String desc) {
		if(desc.startsWith("(")) {
			return findMethod(node, name, desc) != null;
		}
		return findField(node, name, desc) != null;
	}

	private static MethodNode findMethod(ClassNode node, String name, String desc) {
		for(MethodNode method : node.methods) {
			if(method.name.equals(name) && method.desc.equals(desc)) {
				return method;
			}
		}
		return null;
	}

	private static FieldNode findField(ClassNode node, String name, String desc) {
		for(FieldNode field : node.fields) {
			if(field.name.equals(name) && field.desc.equals(desc)) {
				return field;
			}
		}
		return null;
	}

	private static List<FieldNode> missingFields(ClassNode mine, ClassNode theirs) {
		Map<String, FieldNode> present = new TreeMap<>();
		for(FieldNode field : mine.fields) {
			present.put(field.name + " " + field.desc, field);
		}
		List<FieldNode> missing = new ArrayList<>();
		for(FieldNode field : theirs.fields) {
			// Enum constants and compiler-generated fields are not worth restoring.
			// Capture fields of lambdas are compiler-generated and carry values only the lambda that
			// owns them knows: restoring one as null hands that lambda a null it never expected. Gui
			// and Util were full of these, and NeoForge's own mod failed to construct with
			// "Cannot invoke TagKey.toString() because tag2 is null" - a captured tag, filled back in
			// as null.
			boolean synthetic = field.name.startsWith("$") || field.name.startsWith("this$")
					|| field.name.startsWith("val$") || (field.access & Opcodes.ACC_SYNTHETIC) != 0;
			if(!present.containsKey(field.name + " " + field.desc) && !synthetic) {
				missing.add(field);
			}
		}
		return missing;
	}

	/** Whether a class declares any member at all with this name, whatever its descriptor. */
	private static boolean hasMemberNamed(ClassNode node, String name) {
		for(MethodNode method : node.methods) {
			if(method.name.equals(name)) {
				return true;
			}
		}
		return false;
	}

	private static List<MethodNode> missingMethods(ClassNode mine, ClassNode theirs) {		Map<String, MethodNode> present = new TreeMap<>();
		for(MethodNode method : mine.methods) {
			present.put(method.name + " " + method.desc, method);
		}
		List<MethodNode> missing = new ArrayList<>();
		for(MethodNode method : theirs.methods) {
			if("<clinit>".equals(method.name)) {
				continue; // a class initialiser cannot be copied without the class it initialises
			}
			if(method.name.startsWith("lambda$") || method.name.startsWith("access$")) {
				// Synthetics are skipped because the two artefacts number their lambdas independently:
				// the same name can be two different methods, and then the payload's body is the right
				// one to keep. That is a risk only while the name exists at all, and insisting on it
				// outright was wrong in the other direction. Measured on 1.21.8: NeoForge's
				// RenderPipelines.registerCustomPipelines is restored - the payload is compiled against
				// vanilla, which has no such method - and its body calls
				// lambda$registerCustomPipelines$0, which the payload has nowhere. The class was then
				// installed with a restored method whose callee was missing, and the launch died with
				//
				//   NoSuchMethodError: 'void RenderPipelines.lambda$registerCustomPipelines$0(RenderPipeline)'
				//
				// So a synthetic is restored exactly when its name appears nowhere in the payload.
				if(hasMemberNamed(mine, method.name)) {
					continue;
				}
			}
			String key = method.name + " " + method.desc;
			if(!present.containsKey(key)) {
				// Constructors count. NeoForge adds overloads of its own - SimpleBakedModel gained an
				// eight-argument constructor taking a NeoForge RenderTypeGroup - and code restored
				// from NeoForge calls them, so a replacement without them fails at the first bake.
				missing.add(method);
			}
		}
		return missing;
	}

	/** A class file carrying only the dropped members, bodies included. */
	private static void writeDonor(Path donorDir, String internalName, ClassNode replacement, ClassNode runtime,
			List<FieldNode> fields, List<MethodNode> methods, Path runtimeJar) throws IOException {
		// The initialiser extraction reads the class again from the jar rather than trusting the node in
		// hand, and it has to: measured on 1.21.6, the runtime's RenderSystem as held by this pass has a
		// static initialiser with twenty assignments where the class in the jar has twenty-one - the one
		// building PIPELINE_MODIFIERS is not among them - so the value that NeoForge's own class gives
		// that field was refused as unextractable while the identical shape on 1.21.8 was accepted. A
		// fresh read is the same class the game loads, and that is what the value has to come from.
		ClassNode fresh = null;
		try {
			fresh = readFrom(runtimeJar, internalName);
		} catch(IOException e) {
			System.out.println("  could not re-read " + internalName + " for its static initialisers: " + e);
		}
		ClassNode forValues = fresh == null ? runtime : fresh;		ClassNode donor = new ClassNode();
		donor.version = runtime.version;
		donor.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER;
		donor.name = internalName;
		donor.superName = runtime.superName;
		donor.interfaces = new ArrayList<>(runtime.interfaces);
		for(FieldNode field : fields) {
			// Final is dropped: a final instance field may only be assigned from the class's own
			// <init>, and the initialisation is carried out by a restored method, which the JVM
			// rejects with "Update to non-static final field ... attempted from a different method".
			donor.fields.add(new FieldNode(widened(field.access) & ~Opcodes.ACC_FINAL, field.name, field.desc,
					field.signature, field.value));
			if((field.access & Opcodes.ACC_STATIC) == 0) {
				MethodNode initialiser = initialiser(runtime, internalName, field);
				if(initialiser != null) {
					donor.methods.add(initialiser);
				} else {
					System.out.println("  no safe initialiser for field " + internalName + "." + field.name);
				}
			} else {
				// A restored static field needs its value as much as an instance field does, and its
				// assignment lives in the class's static initialiser - the one part of the runtime's
				// class a swap always replaces. Measured on 1.21.8, where leaving it out produced
				//
				//   NullPointerException: Cannot invoke "PipelineModifierStack.apply(RenderPipeline)"
				//     because "com.mojang.blaze3d.systems.RenderSystem.PIPELINE_MODIFIERS" is null
				//
				// while RenderSystem was drawing its first frame. The field is NeoForge's, the payload's
				// class initialiser knows nothing about it, and the plan had restored the declaration
				// without the value.
				MethodNode initialiser = staticInitialiser(forValues, replacement, internalName, field);
				if(initialiser != null) {
					donor.methods.add(initialiser);
				} else if(!"I".equals(field.desc) && !"Z".equals(field.desc) && !"F".equals(field.desc)
						&& !"J".equals(field.desc) && !"D".equals(field.desc)) {
					// Primitives are the case that is fine without one: a primitive field restored as
					// zero is what the payload's own code expects when it never reads NeoForge's use of
					// it, and a reference field restored as null is not worth reporting either - but an
					// object field that NeoForge's code then dereferences is, so it is said out loud.
					System.out.println("  no safe static initialiser for field " + internalName + "." + field.name);
				}
			}
		}
		// The mirror image of a restored field, and the one that cost a launch on 1.21.6: a field
		// OptiFine's own compilation declares and the runtime has never heard of is initialised by the
		// payload's own constructors, and a constructor restored *from the runtime* was compiled against
		// a class that has no such field - so it leaves it at its default. Measured there on
		// com/mojang/blaze3d/pipeline/RenderTarget:
		//
		//   payload <init>(String,Z)   : this.enabled = true
		//   payload resize(II)         : if(!this.enabled) { set sizes; return; }  // no buffers at all
		//   runtime <init>(String,ZZ)  : restored, sets label/useDepth/useStencil, never 'enabled'
		//
		// NeoForge's TextureTarget calls the three-argument constructor, so every render target the
		// frame graph allocated had enabled=false, resize() returned before createBuffers(), and the
		// blur pass died on the first frame with
		//
		//   NullPointerException: Cannot invoke "GpuTexture.getFormat()" because "textureIn" is null
		//     at GlCommandEncoder.verifyColorTexture, from RenderTargetDescriptor.prepare
		//
		// Only reached for a class the plan hands a constructor to, which is what keeps it narrow: the
		// initialiser is called from the constructors that do not assign the field themselves, and
		// every constructor OptiFine compiled does.
		if(methods.stream().anyMatch(method -> "<init>".equals(method.name))) {
			for(FieldNode own : replacement.fields) {
				if((own.access & Opcodes.ACC_STATIC) != 0 || hasMember(runtime, own.name, own.desc)) {
					continue;
				}
				MethodNode initialiser = initialiser(replacement, internalName, own);
				if(initialiser != null) {
					donor.methods.add(initialiser);
				}
			}
		}
		for(MethodNode method : methods) {
			boolean fits = referencesOnlyExisting(method, internalName, replacement, fields, methods)
					&& !STUB_ONLY.contains(internalName + " " + method.name + " " + method.desc);
			if(!fits && "<init>".equals(method.name)) {
				// A constructor cannot be stubbed: an empty one never chains to super, so the class
				// would not verify at all, and a caller that needed the constructor is no worse off
				// without it than with a broken one. Left out, and said so.
				System.out.println("  constructor not restorable: " + internalName + "." + method.desc);
				continue;
			}
			MethodNode copy = new MethodNode(widened(method.access), method.name, method.desc, method.signature,
					method.exceptions == null ? null : method.exceptions.toArray(new String[0]));
			method.accept(copy);
			if(fits) {
				int stripped = stripEnvironmentAsserts(copy);
				if(stripped > 0) {
					System.out.println("  assertions dropped (" + stripped + "): " + internalName + "." + method.name + method.desc);
				}
			} else {
				copy.instructions = stubBody(method.desc);
				copy.tryCatchBlocks.clear();
				copy.localVariables = null;
				System.out.println("  stub (body would not verify): " + internalName + "." + method.name + method.desc);
			}
			donor.methods.add(copy);
		}

		Path target = donorDir.resolve(internalName + ".class");
		Files.createDirectories(target.getParent());
		ClassWriter writer = new ClassWriter(0);
		donor.accept(writer);
		Files.write(target, writer.toByteArray());
	}

	/** Whether every reference the body makes to its own class is present in the replacement. */
	private static boolean referencesOnlyExisting(MethodNode method, String internalName, ClassNode replacement,
			List<FieldNode> plannedFields, List<MethodNode> plannedMethods) {
		if(method.instructions == null) {
			return false;
		}
		for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			String owner = null;
			String name = null;
			String desc = null;
			if(insn instanceof FieldInsnNode field) {
				owner = field.owner; name = field.name; desc = field.desc;
			} else if(insn instanceof MethodInsnNode call) {
				owner = call.owner; name = call.name; desc = call.desc;
				// A super call only verifies if the class it is copied into has the same superclass.
				// OptiFine's compilation of a class can extend something else entirely, and then the
				// copied body fails with "Bad invokespecial instruction: current class isn't
				// assignable to reference class".
				//
				// A constructor call is not a super call, even though both are invokespecial: it acts
				// on a freshly allocated object of some other class, and nothing about the class it
				// is copied into can make it fail. Refusing those cost real bodies - NeoForge's
				// SimpleBakedModel.bakeElements builds its result through
				// SimpleBakedModel$Builder's constructor, and the refusal turned the method that every
				// model bake goes through into a stub that answered null.
				boolean superCall = call.getOpcode() == Opcodes.INVOKESPECIAL && !"<init>".equals(call.name);
				if(superCall && owner != null && !owner.equals(internalName) && !owner.equals(replacement.superName)) {
					return false;
				}
			}
			if(owner == null || !owner.equals(internalName)) {
				continue;
			}
			// The member may be one the plan already restores: the class this body lands in will have
			// it, so the body verifies. Checking against the raw replacement refused bodies that are
			// in fact fine - ASM's verifier accepts them once the plan is applied, and
			// LiquidBlockRenderer is the case that showed it.
			if(!hasMember(replacement, name, desc) && !isPlannedMember(plannedFields, plannedMethods, name, desc)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Removes the no-argument environment assertions from a copied body.
	 *
	 * <p>They are static calls with no arguments, so dropping the instruction leaves the stack
	 * balanced; only calls whose name starts with {@code assert} and which return void are touched.</p>
	 *
	 * @return how many were dropped
	 */
	private static int stripEnvironmentAsserts(MethodNode method) {
		if(method.instructions == null) {
			return 0;
		}
		int dropped = 0;
		for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null;) {
			AbstractInsnNode next = insn.getNext();
			if(insn instanceof MethodInsnNode call && "()V".equals(call.desc) && call.name.startsWith("assert")
					&& RENDER_SYSTEM.equals(call.owner)) {
				method.instructions.remove(insn);
				dropped++;
			}
			insn = next;
		}
		return dropped;
	}

	/** Whether one instruction is the {@code aload_0} a field store pushes its receiver with. */
	private static boolean isReceiverPush(AbstractInsnNode insn) {
		return insn instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == 0;
	}

	/** The body a stub gets: return the default value for the return type. */
	private static InsnList stubBody(String descriptor) {
		InsnList body = new InsnList();
		String returns = descriptor.substring(descriptor.lastIndexOf(')') + 1);
		switch(returns) {
			case "V" -> body.add(new InsnNode(Opcodes.RETURN));
			case "J" -> { body.add(new InsnNode(Opcodes.LCONST_0)); body.add(new InsnNode(Opcodes.LRETURN)); }
			case "D" -> { body.add(new InsnNode(Opcodes.DCONST_0)); body.add(new InsnNode(Opcodes.DRETURN)); }
			case "F" -> { body.add(new InsnNode(Opcodes.FCONST_0)); body.add(new InsnNode(Opcodes.FRETURN)); }
			case "Z", "B", "C", "S", "I" -> { body.add(new InsnNode(Opcodes.ICONST_0)); body.add(new InsnNode(Opcodes.IRETURN)); }
			default -> { body.add(new InsnNode(Opcodes.ACONST_NULL)); body.add(new InsnNode(Opcodes.ARETURN)); }
		}
		return body;
	}

	/**
	 * The same member, but reachable: a dropped member was usually reachable from the code that calls
	 * it, so a private or package-private copy would only move the failure to IllegalAccessError.
	 */
	/**
	 * The sequence that initialises one field, lifted out of the runtime class's constructor.
	 *
	 * <p>A restored field is nobody's responsibility: NeoForge added it, OptiFine's compilation of the
	 * class knows nothing about it, and so it stays null until the {@code putfield} that would have
	 * set it is found and copied. That is what happened to {@code Gui.layerManager}: the stub never
	 * touched it, the real body called {@code initModdedLayers()} on it at once.</p>
	 *
	 * <p>Only straight-line initialisations are lifted - the usual {@code this.x = new Y(...)} or
	 * {@code this.x = argument} shape, touching no local but slot 0 - because a slice with a branch in
	 * it cannot be moved without also moving the code the branch came from. Anything else is left
	 * null and said so, rather than guessed at.</p>
	 *
	 * <p>A field that is only ever assigned from a constructor parameter has no slice to lift: the
	 * value belongs to a caller, and OptiFine's compilation of the class need not even have that
	 * constructor. {@code ClientLanguage.componentStorage} is the case that found this - NeoForge
	 * added the field, its three-argument constructor fills it from an argument, and OptiFine's
	 * two-argument constructor never had one. There the value is taken from the class's own shorter
	 * constructor instead: NeoForge's two-argument {@code ClientLanguage} delegates with
	 * {@code Map.of()}, so that is the default a replacement without the longer constructor gets.
	 * The substitute is only accepted when the delegating call's arguments are each a single
	 * instruction that needs nothing from the stack, which keeps the reasoning visible.</p>
	 *
	 * @return a static method taking the instance, or {@code null} when nothing safe was found
	 */
	private static MethodNode initialiser(ClassNode runtime, String internalName, FieldNode field) {
		for(MethodNode constructor : runtime.methods) {
			if(!"<init>".equals(constructor.name) || constructor.instructions == null) {
				continue;
			}
			for(AbstractInsnNode insn = constructor.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if(!(insn instanceof FieldInsnNode store) || store.getOpcode() != Opcodes.PUTFIELD) {
					continue;
				}
				if(!internalName.equals(store.owner) || !field.name.equals(store.name) || !field.desc.equals(store.desc)) {
					continue;
				}

				AbstractInsnNode previous = skipPseudo(insn.getPrevious());
				if(previous instanceof VarInsnNode load && isLoad(load.getOpcode()) && load.var > 0) {
					// this.field = <constructor parameter>: nothing in this constructor can stand in
					// for the caller's value, so borrow the one the class passes when delegating here.
					List<AbstractInsnNode> delegated = delegatedArgument(runtime, internalName, constructor, load.var - 1);
					if(delegated != null) {
						return initialiserFrom(internalName, field, delegated);
					}
					continue;
				}

				// The value itself: counted, exactly as for a static field, and it has to be counted
				// rather than walked to the previous label. A {@code putfield} consumes two values -
				// the receiver and the value - and the label-walk this used to do ran straight past
				// the value into the receiver's own {@code aload_0}, which the wrapper pushes again:
				//
				//   aload_0            <- the wrapper's own push
				//   aload_0; iconst_1  <- the slice, receiver and value
				//   putfield enabled
				//
				// That leaves a live reference on the stack at {@code return} and would not verify.
				// Counting stops after the one value the field is given, so the receiver is never
				// part of the slice. Measured on 1.21.6's RenderTarget, where the payload's own
				// constructor writes {@code this.enabled = true} on the first line and therefore has
				// no label between the two.
				List<AbstractInsnNode> counted = valueRun(insn);
				if(counted != null) {
					return initialiserFrom(internalName, field, counted);
				}

				// Walk back to the start of the straight-line run that produced the value, for the
				// shapes the counting walk refuses - an expression that reads {@code this}, whose
				// {@code aload_0} is then the start of the value and not the receiver.
				List<AbstractInsnNode> slice = new ArrayList<>();
				boolean safe = true;
				for(AbstractInsnNode back = insn.getPrevious(); back != null; back = back.getPrevious()) {
					if(back instanceof LabelNode || back instanceof JumpInsnNode || back instanceof TableSwitchInsnNode
							|| back instanceof LookupSwitchInsnNode || back instanceof LineNumberNode || back instanceof FrameNode) {
						break;
					}
					if(back instanceof VarInsnNode var && var.var != 0) {
						safe = false; // depends on a constructor argument or another local
						break;
					}
					slice.add(0, back);
				}
				if(safe && !slice.isEmpty() && !isReceiverPush(slice.get(0))) {
					return initialiserFrom(internalName, field, slice);
				}
			}
		}
		return null;
	}

	/** Wraps a value-producing run into the assignment helper the transformer calls. */
	private static MethodNode initialiserFrom(String internalName, FieldNode field, List<AbstractInsnNode> value) {
		MethodNode initialiser = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
				INITIALISER_PREFIX + field.name, "(L" + internalName + ";)V", null, null);
		initialiser.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		for(AbstractInsnNode step : value) {
			initialiser.instructions.add(step);
		}
		initialiser.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, internalName, field.name, field.desc));
		initialiser.instructions.add(new InsnNode(Opcodes.RETURN));
		initialiser.maxStack = 8;
		initialiser.maxLocals = 1;
		return initialiser;
	}

	/**
	 * The static counterpart: the value the runtime's {@code <clinit>} gives a static field.
	 *
	 * <p>Only a straight-line run with no local variable reads is taken, exactly as for an instance
	 * field, and the wrapper has no arguments because a static field is assigned from nothing but the
	 * expression itself. The transformer calls it from the target class's static initialiser - after the
	 * payload's own code, so that what OptiFine's initialiser sets is not overwritten by it.</p>
	 *
	 * @return a static method taking no arguments, or {@code null} when no safe assignment was found
	 */
	private static MethodNode staticInitialiser(ClassNode runtime, ClassNode payload, String internalName, FieldNode field) {
		// The static initialiser first, then only the methods it reaches. Both halves are measured.
		//
		// The second half is 1.21.6: RenderSystem.PIPELINE_MODIFIERS is assigned in a static helper
		// rather than in <clinit>, and searching only <clinit> left the field null and the client dead on
		// its first frame with "PIPELINE_MODIFIERS is null". But searching *every* method is wrong in the
		// other direction, and 26.1.2 is where that showed:
		//
		//   DebugScreenEntries.MODDED_ENTRIES_REGISTERED = true;   // inside registerModdedDebugEntries()
		//
		// is a flag the method *sets*, not a value the class starts with. Lifting it into the payload's
		// own <clinit> made the flag true before anything registered, and NeoForge's own call then died
		// with "IllegalStateException: Already registered modded debug entries!" during
		// Minecraft.<init>. A value is only an initial value if the class initialiser can reach it.
		List<MethodNode> candidates = new ArrayList<>();
		MethodNode clinit = findMethod(runtime, "<clinit>", "()V");
		if(clinit != null) {
			candidates.add(clinit);
			candidates.addAll(reachedFrom(runtime, clinit));
		}
		for(MethodNode method : candidates) {
			if(method.instructions == null) {
				continue;
			}
			for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if(!(insn instanceof FieldInsnNode store) || store.getOpcode() != Opcodes.PUTSTATIC) {
					continue;
				}
				if(!internalName.equals(store.owner) || !field.name.equals(store.name)
						|| !field.desc.equals(store.desc)) {
					continue;
				}
				List<AbstractInsnNode> value = valueRun(insn);
				if(value != null) {
					if(clinit != null && method == clinit && isReadLater(clinit, insn, internalName, field)) {
						// The runtime assigns this field and then *fills* it, so the assignment alone is
						// the wrong value to ship: it is the empty container, not the populated one.
						List<AbstractInsnNode> derived = populatedView(payload, clinit, internalName, field);
						if(derived != null) {
							return staticInitialiserFrom(internalName, field, derived);
						}
						System.out.println("  field " + internalName + "." + field.name
								+ " is written again after its assignment and that fill cannot be lifted; it"
								+ " ships at its constructed value only");
					}
					return staticInitialiserFrom(internalName, field, value);
				}
			}
		}
		return null;
	}

	/** Whether the same field is read again later in the same method it was assigned in. */
	private static boolean isReadLater(MethodNode method, AbstractInsnNode store, String internalName, FieldNode field) {
		for(AbstractInsnNode insn = store.getNext(); insn != null; insn = insn.getNext()) {
			if(insn instanceof FieldInsnNode read && read.getOpcode() == Opcodes.GETSTATIC
					&& internalName.equals(read.owner) && field.name.equals(read.name)
					&& field.desc.equals(read.desc)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The value of a field the runtime builds and then fills, rebuilt from the read-only view of it.
	 *
	 * <p>Measured on 26.1.2, where the runtime's static initialiser ends with</p>
	 *
	 * <pre>799: new HashMap; 806: putstatic PROFILES_MUTABLE      // the assignment, all a value run sees
	 * 809: getstatic PROFILES_MUTABLE; ...; 816: Map.put(DEFAULT, ...)
	 * 822: getstatic PROFILES_MUTABLE; ...; 829: Map.put(PERFORMANCE, ...)
	 * 835: getstatic PROFILES_MUTABLE; 838: Collections.unmodifiableMap; 841: putstatic PROFILES</pre>
	 *
	 * <p>Lifting only the assignment shipped an <em>empty</em> map, and NeoForge's own
	 * {@code RegisterDebugEntriesEvent} then died on the missing {@code DEFAULT} entry with
	 * {@code NullPointerException: Map.size() ... "m" is null}, inside {@code Minecraft.<init>}. The
	 * fills themselves cannot be lifted as they stand - they read locals built earlier in the same
	 * initialiser - but the view they end up in can be read instead: the last statement above makes
	 * {@code PROFILES} an unmodifiable view of exactly this map, so a mutable copy of that view is the
	 * value the class was meant to start with. The payload has {@code PROFILES} of its own, assigned by
	 * its own initialiser before this code runs, which is what makes the substitution sound.</p>
	 *
	 * @return the instructions building that copy, or {@code null} when the shape is not there
	 */
	private static List<AbstractInsnNode> populatedView(ClassNode payload, MethodNode clinit, String internalName,
			FieldNode field) {
		if(clinit.instructions == null) {
			return null;
		}
		for(AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if(!(insn instanceof MethodInsnNode call) || !"java/util/Collections".equals(call.owner)
					|| !"unmodifiableMap".equals(call.name)) {
				continue;
			}
			AbstractInsnNode source = skipPseudo(call.getPrevious());
			if(!(source instanceof FieldInsnNode read) || read.getOpcode() != Opcodes.GETSTATIC
					|| !internalName.equals(read.owner) || !field.name.equals(read.name)
					|| !field.desc.equals(read.desc)) {
				continue;
			}
			AbstractInsnNode target = skipPseudo(call.getNext());
			if(!(target instanceof FieldInsnNode store) || store.getOpcode() != Opcodes.PUTSTATIC
					|| !internalName.equals(store.owner)) {
				continue;
			}
			// The copy is built out of that field, so the payload has to declare it: it is the one this
			// code runs against, and a field only the runtime has would not resolve here.
			if(!hasMember(payload, store.name, store.desc)) {
				continue;
			}
			List<AbstractInsnNode> run = new ArrayList<>();
			run.add(new TypeInsnNode(Opcodes.NEW, "java/util/HashMap"));
			run.add(new InsnNode(Opcodes.DUP));
			run.add(new FieldInsnNode(Opcodes.GETSTATIC, internalName, store.name, store.desc));
			run.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>",
					"(Ljava/util/Map;)V", false));
			return run;
		}
		return null;
	}

	/**
	 * The class's own methods one method can reach, itself excluded.
	 *
	 * <p>Only calls whose owner is this class count: a value built inside another class belongs to that
	 * class's initialisation, not to this one, and copying its code here would run it at the wrong time.</p>
	 */
	private static List<MethodNode> reachedFrom(ClassNode runtime, MethodNode start) {
		List<MethodNode> found = new ArrayList<>();
		List<MethodNode> queue = new ArrayList<>();
		queue.add(start);
		for(int i = 0; i < queue.size(); i++) {
			MethodNode method = queue.get(i);
			if(method.instructions == null) {
				continue;
			}
			for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if(!(insn instanceof MethodInsnNode call) || !runtime.name.equals(call.owner)) {
					continue;
				}
				MethodNode target = findMethod(runtime, call.name, call.desc);
				if(target == null || target == start || found.contains(target) || queue.contains(target)) {
					continue;
				}
				found.add(target);
				queue.add(target);
			}
		}
		return found;
	}

	/** One class out of a jar, for a value that has to come from the class the game will load. */
	private static ClassNode readFrom(Path jar, String internalName) throws IOException {
		try(ZipFile zip = new ZipFile(jar.toFile())) {
			ZipEntry entry = zip.getEntry(internalName + ".class");
			if(entry == null) {
				return null;
			}
			return read(zip.getInputStream(entry));
		}
	}

	/**
	 * The instructions that produce the one value a store consumes, or null when that cannot be said.
	 *
	 * <p>The walk goes backwards from the store and stops as soon as the run has produced exactly one
	 * value, which is a rule about the code rather than about where statements happen to be. The earlier
	 * version walked back to the previous label and refused the run if it crossed a local variable read,
	 * and a static initialiser with no label between two statements then swallowed the previous one's
	 * instructions - so on 1.21.6 the assignment that builds NeoForge's pipeline modifier stack was
	 * refused while the identical shape on 1.21.8 was accepted:</p>
	 *
	 * <pre>192: new        PipelineModifierStack
	 * 195: dup
	 * 196: invokespecial PipelineModifierStack.&lt;init&gt;()V
	 * 199: putstatic  RenderSystem.PIPELINE_MODIFIERS</pre>
	 *
	 * <p>Counting values instead stops after {@code new} - +1 for the new, +1 for the dup, -1 for the
	 * constructor - and leaves exactly the three instructions that make the expression. Anything whose
	 * effect is not known here ends the walk: a local read still means the value depends on code outside
	 * the run, which is what the instance path refuses as well.</p>
	 */
	private static List<AbstractInsnNode> valueRun(AbstractInsnNode store) {
		List<AbstractInsnNode> slice = new ArrayList<>();
		int produced = 0;
		for(AbstractInsnNode back = store.getPrevious(); back != null; back = back.getPrevious()) {
			if(back instanceof LabelNode || back instanceof JumpInsnNode || back instanceof TableSwitchInsnNode
					|| back instanceof LookupSwitchInsnNode || back instanceof LineNumberNode
					|| back instanceof FrameNode) {
				break;
			}
			Integer delta = stackDelta(back);
			if(delta == null) {
				return null;
			}
			slice.add(0, back);
			produced += delta;
			if(produced == 1) {
				return slice;
			}
			if(produced > 1) {
				return null; // more than the store consumes: this run is not one expression
			}
		}
		return null;
	}

	/** The opcodes immediately before a store, as text, for a refusal to be readable. */
	/** How many values an instruction leaves behind, or null when that is not known here. */
	private static Integer stackDelta(AbstractInsnNode insn) {
		switch(insn.getOpcode()) {
			case Opcodes.ACONST_NULL:
			case Opcodes.ICONST_M1:
			case Opcodes.ICONST_0:
			case Opcodes.ICONST_1:
			case Opcodes.ICONST_2:
			case Opcodes.ICONST_3:
			case Opcodes.ICONST_4:
			case Opcodes.ICONST_5:
			case Opcodes.LCONST_0:
			case Opcodes.LCONST_1:
			case Opcodes.FCONST_0:
			case Opcodes.FCONST_1:
			case Opcodes.FCONST_2:
			case Opcodes.DCONST_0:
			case Opcodes.DCONST_1:
			case Opcodes.BIPUSH:
			case Opcodes.SIPUSH:
			case Opcodes.LDC:
			case Opcodes.NEW:
			case Opcodes.GETSTATIC:
			case Opcodes.DUP:
				return 1;
			case Opcodes.CHECKCAST:
			case Opcodes.INSTANCEOF:
				return 0;
			case Opcodes.INVOKESPECIAL:
			case Opcodes.INVOKEVIRTUAL:
			case Opcodes.INVOKESTATIC:
			case Opcodes.INVOKEINTERFACE:
			case Opcodes.INVOKEDYNAMIC:
				return invoke(insn);
			default:
				return null;
		}
	}

	/** The net values an invocation leaves: its result minus its arguments and receiver. */
	private static Integer invoke(AbstractInsnNode insn) {
		if(insn instanceof InvokeDynamicInsnNode call) {
			// An invokedynamic has no receiver - the bootstrap method supplies the target - so its own
			// arguments are the descriptor's and nothing else is consumed. Refusing it cost a real
			// value: on 1.21.8 the run that builds
			//
			//   SingleVariant$Unbaked.MAP_CODEC = Variant.MAP_CODEC.xmap(lambda, lambda)
			//
			// is two invokedynamics around one call, the counting walk stopped at the first of them as
			// an unknown instruction, and the field was restored as a declaration with no value. The
			// client then failed every blockstate in the game with
			//
			//   NullPointerException: Cannot invoke "MapCodec.decode(...)" because
			//     "this.val$fallbackCodec" is null
			//       at NeoForgeExtraCodecs$1.decode
			//
			// because NeoForge's BlockStateModel$Unbaked.CODEC falls back to exactly that field, and
			// NeoForge's anonymous codec had captured the null at construction time. Inlining the run
			// is safe here: the bootstrap methods it names are SingleVariant$Unbaked's own constructor
			// and accessor, which OptiFine's copy of the class has as well.
			int dynamic = -Type.getArgumentTypes(call.desc).length;
			if(Type.getReturnType(call.desc).getSort() != Type.VOID) {
				dynamic++;
			}
			return dynamic;
		}
		if(!(insn instanceof MethodInsnNode call)) {
			return null;
		}
		int delta = -Type.getArgumentTypes(call.desc).length;
		if(call.getOpcode() != Opcodes.INVOKESTATIC) {
			delta--; // the receiver
		}
		if(Type.getReturnType(call.desc).getSort() != Type.VOID) {
			delta++;
		}
		return delta;
	}

	/** Wraps a value-producing run into the static assignment helper the transformer calls. */
	private static MethodNode staticInitialiserFrom(String internalName, FieldNode field,
			List<AbstractInsnNode> value) {
		MethodNode initialiser = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
				INITIALISER_PREFIX + field.name, "()V", null, null);
		for(AbstractInsnNode step : value) {
			initialiser.instructions.add(step);
		}
		initialiser.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, internalName, field.name, field.desc));
		initialiser.instructions.add(new InsnNode(Opcodes.RETURN));
		initialiser.maxStack = 8;
		initialiser.maxLocals = 0;
		return initialiser;
	}

	/**
	 * The value another constructor of the same class passes for one argument of {@code assigning}.
	 *
	 * @param argumentIndex the argument, counted from zero, that the delegated constructor assigns
	 * @return the single instruction producing it, or {@code null} when no such constructor exists
	 */
	private static List<AbstractInsnNode> delegatedArgument(ClassNode runtime, String internalName,
			MethodNode assigning, int argumentIndex) {
		Type[] arguments = Type.getArgumentTypes(assigning.desc);
		if(argumentIndex < 0 || argumentIndex >= arguments.length) {
			return null;
		}
		for(MethodNode delegator : runtime.methods) {
			if(!"<init>".equals(delegator.name) || delegator == assigning || delegator.instructions == null) {
				continue;
			}
			for(AbstractInsnNode insn = delegator.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if(!(insn instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESPECIAL
						|| !internalName.equals(call.owner) || !"<init>".equals(call.name)
						|| !assigning.desc.equals(call.desc)) {
					continue;
				}
				List<AbstractInsnNode> producers = simpleArguments(call, arguments.length);
				if(producers == null) {
					continue;
				}
				AbstractInsnNode producer = producers.get(argumentIndex);
				if(producer instanceof VarInsnNode) {
					continue; // the delegating constructor only forwards another parameter
				}
				List<AbstractInsnNode> value = new ArrayList<>();
				value.add(producer);
				return value;
			}
		}
		return null;
	}

	/** The instructions producing a call's arguments, in argument order, or null if not each simple. */
	private static List<AbstractInsnNode> simpleArguments(AbstractInsnNode call, int count) {
		List<AbstractInsnNode> backwards = new ArrayList<>();
		for(AbstractInsnNode back = call.getPrevious(); back != null && backwards.size() < count; back = back.getPrevious()) {
			back = skipPseudo(back);
			if(back == null) {
				break;
			}
			if(!pushesWithoutPopping(back)) {
				return null;
			}
			backwards.add(back);
		}
		if(backwards.size() < count) {
			return null;
		}
		List<AbstractInsnNode> producers = new ArrayList<>(count);
		for(int argument = 0; argument < count; argument++) {
			producers.add(backwards.get(count - 1 - argument));
		}
		return producers;
	}

	/** Whether one instruction puts a value on the stack while needing nothing from it. */
	private static boolean pushesWithoutPopping(AbstractInsnNode insn) {
		if(insn instanceof VarInsnNode load) {
			return isLoad(load.getOpcode()); // a parameter or local, put there by whoever called in
		}
		if(insn instanceof InsnNode constant) {
			int opcode = constant.getOpcode();
			return opcode == Opcodes.ACONST_NULL || (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5)
					|| opcode == Opcodes.LCONST_0 || opcode == Opcodes.LCONST_1
					|| (opcode >= Opcodes.FCONST_0 && opcode <= Opcodes.FCONST_2)
					|| opcode == Opcodes.DCONST_0 || opcode == Opcodes.DCONST_1;
		}
		if(insn instanceof IntInsnNode immediate) {
			return immediate.getOpcode() == Opcodes.BIPUSH || immediate.getOpcode() == Opcodes.SIPUSH;
		}
		if(insn instanceof LdcInsnNode) {
			return true;
		}
		if(insn instanceof FieldInsnNode field) {
			return field.getOpcode() == Opcodes.GETSTATIC;
		}
		if(insn instanceof TypeInsnNode type) {
			return type.getOpcode() == Opcodes.NEW;
		}
		if(insn instanceof MethodInsnNode call) {
			return call.getOpcode() == Opcodes.INVOKESTATIC
					&& Type.getArgumentTypes(call.desc).length == 0
					&& Type.getReturnType(call.desc).getSort() != Type.VOID;
		}
		return false;
	}

	private static boolean isLoad(int opcode) {
		return opcode >= Opcodes.ILOAD && opcode <= Opcodes.ALOAD;
	}

	/** The next instruction that is real code rather than position information. */
	private static AbstractInsnNode skipPseudo(AbstractInsnNode insn) {
		while(insn instanceof LineNumberNode || insn instanceof FrameNode) {
			insn = insn.getPrevious();
		}
		return insn;
	}

	private static boolean isPlannedMember(List<FieldNode> plannedFields, List<MethodNode> plannedMethods, String name, String desc) {
		if(desc.startsWith("(")) {
			for(MethodNode method : plannedMethods) {
				if(method.name.equals(name) && method.desc.equals(desc)) {
					return true;
				}
			}
			return false;
		}
		for(FieldNode field : plannedFields) {
			if(field.name.equals(name) && field.desc.equals(desc)) {
				return true;
			}
		}
		return false;
	}

	private static int widened(int access) {
		return (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
	}

	private static ClassNode read(InputStream stream) throws IOException {
		try(stream) {
			ClassNode node = new ClassNode();
			new ClassReader(stream.readAllBytes()).accept(node, 0);
			return node;
		}
	}

	/** Grouped for a readable summary: class to number of members. */
	public static Map<String, Integer> summarise(List<String> lines) {
		Map<String, Integer> perClass = new TreeMap<>();
		for(String line : lines) {
			String[] parts = line.split(" ");
			if(parts.length >= 2) {
				perClass.merge(parts[1], 1, Integer::sum);
			}
		}
		return perClass;
	}

	/** Development aid: {@code MemberRestorePlan <patched jar> <runtime jar> <out file> [donor dir]}. */
	public static void main(String[] args) throws IOException {
		if(args.length < 3) {
			System.err.println("usage: MemberRestorePlan <patched jar> <runtime jar> <out file> [donor dir]");
			System.exit(2);
		}
		List<String> lines = plan(Path.of(args[0]), Path.of(args[1]), args.length > 3 ? Path.of(args[3]) : null);
		Files.write(Path.of(args[2]), lines, StandardCharsets.UTF_8);
		System.out.println("wrote " + args[2]);
		Map<String, Integer> perClass = summarise(lines);
		perClass.forEach((name, count) -> System.out.println("  " + count + "  " + name));
		System.out.println("distinct classes: " + new TreeSet<>(perClass.keySet()).size());
	}
}
