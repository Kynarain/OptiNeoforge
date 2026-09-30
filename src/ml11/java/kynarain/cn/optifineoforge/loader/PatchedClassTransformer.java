/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformer.Target;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;

/**
 * Puts OptiFine's own compilation of a game class in place of NeoForge's.
 *
 * <p>This exists because of a dead end worth writing down. On these lines OptiFine's patches are
 * deltas against the <em>obfuscated</em> game jar, and its runtime transformer applies them by asking
 * for the obfuscated base class as a resource - which NeoForge's runtime, being officially named,
 * cannot provide. Every single target fails:</p>
 *
 * <pre>java.io.IOException: Base resource not found: fcn.class
 *   at optifine.Patcher.applyPatch(Patcher.java:148)
 *   at optifine.OptiFineTransformer.getOptiFineResourcePatched(OptiFineTransformer.java:441)</pre>
 *
 * <p>So the patching is done offline instead, where the obfuscated jar still exists, and the finished
 * classes travel in this jar. They cannot simply be flattened into it at their real paths - that makes
 * this jar's module export packages the {@code minecraft} module already exports, and the module layer
 * refuses to resolve it:</p>
 *
 * <pre>java.lang.module.ResolutionException: Modules srg and minecraft export package
 *   net.minecraft.client.renderer.block to module mixinsynthetic</pre>
 *
 * <p>Hence the storage path: the finished classes live under {@code optifineoforge/patched/}, whose
 * entries claim no game package, and this transformer puts their content into the class the game asked
 * for. {@code optifineoforge/patched-index.txt} lists which ones there are, because a ModLauncher
 * transformer has to declare its targets before it is ever called.</p>
 *
 * <p>Only the members and the type hierarchy are taken from OptiFine's version; the class's own name
 * and version stay as the game asked for them. What NeoForge added to the class and OptiFine's
 * compilation lacks is put back by {@link MemberRestoreTransformer}, which is why this transformer has
 * to run first.</p>
 */
public final class PatchedClassTransformer implements ITransformer<ClassNode> {
	private static final Logger LOGGER = LogManager.getLogger("OptifiNeoforge");
	/** Where the finished classes are stored, so that no game package is claimed by this module. */
	static final String PREFIX = "/optifineoforge/patched/";
	private static final String INDEX = "/optifineoforge/patched-index.txt";

	/**
	 * The members a runtime class has to be given while it loads, produced by the offline stub pass.
	 *
	 * <p>These are the references nothing can satisfy: not the runtime, not the payload. When the owner is
	 * a payload class the add can happen offline, and the file is then empty for it; when the owner is a
	 * runtime class - OptiFine's call into a method only its own copy of the class declares - it has to
	 * happen here, on the class as it is defined.</p>
	 */
	private static final String STUBS = "/optifineoforge/stubs.txt";

	/** Owner internal name to the members it needs, in order. */
	private static final Map<String, List<String[]>> STUBS_BY_OWNER = loadStubs();

	private static Map<String, List<String[]>> loadStubs() {
		Map<String, List<String[]>> result = new LinkedHashMap<>();
		try(InputStream stream = PatchedClassTransformer.class.getResourceAsStream(STUBS)) {
			if(stream == null) {
				return Map.of();
			}
			for(String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				String[] parts = line.split("\t");
				if(parts.length == 3) {
					result.computeIfAbsent(parts[0], key -> new ArrayList<>()).add(new String[] {parts[1], parts[2]});
				}
			}
		} catch(IOException e) {
			LOGGER.warn("could not read " + STUBS + ": " + e);
		}
		LOGGER.info("Runtime stubs to add: " + result.values().stream().mapToInt(List::size).sum()
				+ " members across " + result.size() + " classes");
		return result;
	}

	/** Adds the stubs listed for this class, if it is one of them and lacks them. */
	private static void stubMissing(ClassNode input) {
		List<String[]> wanted = STUBS_BY_OWNER.get(input.name);
		if(wanted == null) {
			return;
		}
		for(String[] member : wanted) {
			if(hasMethod(input, member[0], member[1])) {
				continue;
			}
			input.methods.add(defaultBody(member[0], member[1], (input.access & Opcodes.ACC_INTERFACE) != 0));
			LOGGER.info("Stubbed " + input.name.replace('/', '.') + "." + member[0] + member[1]);
		}
	}

	/**
	 * Rewrites the payload's superclass onto the runtime's, when the build planned that for this class,
	 * and returns {@code null} on success or the reason it must not be done.
	 *
	 * <p>Three things have to hold, and each one is checked rather than assumed. The payload's superclass
	 * has to be one of the Forge types this jar supplies - a shim, with no state and no superclass of its
	 * own - because only then is discarding it lossless. The class has to be on the build's plan, which is
	 * where the runtime superclass was inspected: it must have a constructor a subclass may chain to, and
	 * the loader cannot look at a game class to find that out. And the class body must not name the old
	 * superclass anywhere except in those constructor calls: a {@code super.something()} call site names
	 * its owner, and once the class no longer extends that type the call cannot resolve.</p>
	 *
	 * <p>Measured on 1.21.1, the shape this exists for is exactly one class -
	 * {@code BlockEntity extends CapabilityProvider<BlockEntity>}, whose constructor does
	 * {@code aload_0; ldc class BlockEntity; invokespecial CapabilityProvider.<init>(Ljava/lang/Class;)V}
	 * and whose body names the old superclass nowhere else. The Forge superclass stores a reference to
	 * the object for its capability lookups; NeoForge's {@code AttachmentHolder} keeps its map on the
	 * object itself, so dropping the argument is the correct translation, not a shortcut. It matters
	 * that the class is not simply left alone: OptiFine's compilation of it carries its own state
	 * ({@code nbtTag}, {@code nbtTagUpdateMs}) and {@code requestModelDataUpdate()}, which the runtime's
	 * version has no reason to have.</p>
	 */
	private static String reparent(ClassNode patched, ClassNode runtime) {
		String[] planned = REPARENTS_BY_CLASS.get(patched.name);
		if(planned == null) {
			return null;
		}
		String plannedSuper = planned[0];
		String plannedCtor = planned[1];
		String forgeSuper = patched.superName;
		if(forgeSuper == null || !forgeSuper.startsWith("net/minecraftforge/")) {
			return "the plan says to re-parent it but its copy extends " + forgeSuper + ", not a Forge type";
		}
		// The plan was made from the same jars, so a disagreement means the plan and the runtime are not
		// from the same build: the runtime's class has to be the one the plan was measured against. A null
		// reference means the caller established that what it holds is not the runtime's copy at all - see
		// the caller's note - and then there is nothing here to disagree with.
		if(runtime != null && !plannedSuper.equals(runtime.superName)) {
			return "the plan re-parents it onto " + plannedSuper + " while the runtime's copy extends "
					+ runtime.superName + ", so the plan and the runtime do not match";
		}
		// The shim has to be a root, or it is not one of ours to discard.
		boolean ours = PatchedClassTransformer.class.getResource("/" + forgeSuper + ".class") != null;
		String shimSuper = ownSuperName(forgeSuper);
		if(!ours || (shimSuper != null && !"java/lang/Object".equals(shimSuper))) {
			return "the payload's copy of it extends " + forgeSuper + ", which this jar does not supply as "
					+ "a shim of its own";
		}
		int rewritten = 0;
		for(MethodNode method : patched.methods) {
			if(!"<init>".equals(method.name)) {
				continue;
			}
			for(AbstractInsnNode instruction : method.instructions) {
				if(!(instruction instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESPECIAL
						|| !forgeSuper.equals(call.owner) || !"<init>".equals(call.name)) {
					continue;
				}
				if("()V".equals(plannedCtor)) {
					// The arguments are already on the stack, and the runtime's superclass takes none:
					// they are discarded rather than the pushes being deleted, which keeps whatever the
					// class evaluates for them evaluated exactly as OptiFine wrote it.
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
				rewritten++;
			}
		}
		if(rewritten == 0) {
			return "the payload's copy of it extends " + forgeSuper + " and no constructor of it chains to "
					+ "that superclass, so the hierarchy cannot be rewritten";
		}
		String survivor = namesOldSuper(patched, forgeSuper);
		if(survivor != null) {
			return "the payload's copy of it extends " + forgeSuper + " and its body still names that type ("
					+ survivor + "), which would not resolve afterwards";
		}
		patched.superName = plannedSuper;
		if(patched.signature != null && patched.signature.contains(forgeSuper)) {
			// The generic signature names the superclass it was compiled against; a stale one is not a
			// verification problem, only a lie to anything that reads it, so it goes.
			patched.signature = null;
		}
		LOGGER.info("Re-parented " + patched.name.replace('/', '.') + " from the Forge type " + forgeSuper
				+ " onto the runtime's " + plannedSuper + " (super(" + plannedCtor + "), " + rewritten
				+ " constructor call(s) rewritten)");
		return null;
	}

	/**
	 * The re-parent plan, {@code class name to the runtime superclass and the constructor to call},
	 * produced by the build's {@code HierarchyPlan} step. Written there and not decided here because the
	 * decision needs the runtime superclass, which a transformer cannot look at: it holds two copies of
	 * one class, never the class above them.
	 */
	private static final String REPARENTS = "/optifineoforge/reparent.txt";

	private static final Map<String, String[]> REPARENTS_BY_CLASS = loadReparents();

	private static Map<String, String[]> loadReparents() {
		Map<String, String[]> result = new LinkedHashMap<>();
		try(InputStream stream = PatchedClassTransformer.class.getResourceAsStream(REPARENTS)) {
			if(stream == null) {
				// Lines whose payload already speaks NeoForge's hierarchy - 1.20.1, where the Forge
				// package is the runtime's own - have nothing to move and no plan.
				LOGGER.info("No " + REPARENTS + " in this jar; no class has its hierarchy rewritten");
				return Map.of();
			}
			for(String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				String[] parts = line.split("\t");
				// Four fields, because the constructor is part of the decision: a runtime superclass may
				// want the argument the payload passes rather than none at all.
				if(parts.length == 4 && "reparent".equals(parts[0])) {
					result.put(parts[1], new String[] {parts[2], parts[3]});
				}
			}
		} catch(IOException e) {
			LOGGER.warn("could not read " + REPARENTS + ": " + e);
		}
		LOGGER.info("Hierarchy rewrites planned: " + result.size());
		return Map.copyOf(result);
	}

	/**
	 * The first place a class still names a type, as a description, or null when it names it nowhere.
	 *
	 * <p>Instruction operands and the class's own metadata are both searched: a member reference names
	 * its owner, a type instruction names a class, and a local variable table or an annotation can name
	 * either. Strings are deliberately not searched - OptiFine has string constants holding class names
	 * it looks up itself, and those are not affected by a rewrite of the hierarchy.</p>
	 */
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
				if(instruction instanceof TypeInsnNode type && internalName.equals(type.desc)) {
					return "a " + type.getOpcode() + " of the type";
				}
			}
		}
		return null;
	}

	/** Null-safe class-name comparison: a class with no superclass equals only another such class. */
	private static boolean sameName(String left, String right) {
		return left == null ? right == null : left.equals(right);
	}

	/**
	 * Why the payload's copy of a class must not be installed, or {@code null} when it may be.
	 *
	 * <p>The question is not whether the two copies name the same superclass but whether the payload's
	 * hierarchy, as it will really be loaded, still reaches the type the runtime's callers were compiled
	 * against. Keeping {@code java.lang.Object} needs nothing: every class reaches it. Anything else has
	 * to be found on the payload's own chain, and a Forge API type on that chain is supplied by this jar
	 * as a shim - {@code /net/minecraftforge/...} below - whose own superclass the build re-parents, so
	 * the walk sees the type the game will actually see.
	 *
	 * <p>The walk is deliberately limited to this jar's resources. Asking a class loader for a game class
	 * from inside a transformer is the one thing that reliably produces an unusable failure, and no
	 * shim's superclass is a game class anyway.
	 */
	private static String hierarchyProblem(String payloadSuper, String runtimeSuper) {
		if(sameName(payloadSuper, runtimeSuper)) {
			return null;
		}
		if(runtimeSuper == null || "java/lang/Object".equals(runtimeSuper)) {
			return null;
		}
		String current = payloadSuper;
		for(int hops = 0; hops < 16 && current != null; hops++) {
			if(current.equals(runtimeSuper)) {
				return null;
			}
			current = ownSuperName(current);
		}
		return "the payload's copy of it extends " + payloadSuper + " and that chain does not reach "
				+ runtimeSuper + ", which the runtime's version extends";
	}

	/** The superclass of a class this jar ships outside the payload tree, or null if it ships none. */
	private static String ownSuperName(String internalName) {
		try(InputStream stream = PatchedClassTransformer.class.getResourceAsStream("/" + internalName + ".class")) {
			return stream == null ? null : new ClassReader(stream.readAllBytes()).getSuperName();
		} catch(IOException e) {
			return null;
		}
	}

	private static boolean hasMethod(ClassNode node, String name, String desc) {
		for(MethodNode method : node.methods) {
			if(method.name.equals(name) && method.desc.equals(desc)) {
				return true;
			}
		}
		return false;
	}

	/** A method that returns the default value for its return type; a default method in an interface. */
	private static MethodNode defaultBody(String name, String desc, boolean isInterface) {
		MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, name, desc, null, null);
		org.objectweb.asm.Type returnType = org.objectweb.asm.Type.getReturnType(desc);
		switch(returnType.getSort()) {
			case org.objectweb.asm.Type.VOID -> method.instructions.add(new InsnNode(Opcodes.RETURN));
			case org.objectweb.asm.Type.BOOLEAN, org.objectweb.asm.Type.BYTE, org.objectweb.asm.Type.CHAR,
					org.objectweb.asm.Type.SHORT, org.objectweb.asm.Type.INT -> {
				method.instructions.add(new InsnNode(Opcodes.ICONST_0));
				method.instructions.add(new InsnNode(Opcodes.IRETURN));
			}
			case org.objectweb.asm.Type.LONG -> {
				method.instructions.add(new InsnNode(Opcodes.LCONST_0));
				method.instructions.add(new InsnNode(Opcodes.LRETURN));
			}
			case org.objectweb.asm.Type.FLOAT -> {
				method.instructions.add(new InsnNode(Opcodes.FCONST_0));
				method.instructions.add(new InsnNode(Opcodes.FRETURN));
			}
			case org.objectweb.asm.Type.DOUBLE -> {
				method.instructions.add(new InsnNode(Opcodes.DCONST_0));
				method.instructions.add(new InsnNode(Opcodes.DRETURN));
			}
			default -> {
				// An Optional-returning stub must not answer null: every caller of one writes
				// `x().isPresent()` / `isEmpty()` / `orElse(...)`, so null turns the missing member into a
				// NullPointerException one frame later instead of removing the failure. Measured on 1.21
				// (2026-09-22): the payload's GameRenderer.tryTakeScreenshotIfNeeded calls
				// IntegratedServer.m_182649_()Ljava/util/Optional;, the runtime does not declare that SRG name
				// (SrgResidue lists it), and the run died at GameRenderer.java:1238. Optional.empty() is the
				// only honest default here - it says "nothing", which is what a stub can truthfully say.
				if("java/util/Optional".equals(returnType.getInternalName())) {
					method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Optional",
							"empty", "()Ljava/util/Optional;", false));
					method.instructions.add(new InsnNode(Opcodes.ARETURN));
				} else {
					method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
					method.instructions.add(new InsnNode(Opcodes.ARETURN));
				}
			}
		}
		return method;
	}

	/** The three access bits that say who may use a class; everything else in the word is not visibility. */
	private static final int VISIBILITY = Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED;

	private static final Set<String> TARGETS = loadTargets();

	/**
	 * Every class this transformer has to be called for, which is not the same as every class it swaps.
	 *
	 * <p>The index alone is not enough, and the gap was measured rather than reasoned about. ModLauncher
	 * calls a transformer only for the classes it declares, so a class that is not in the payload was never
	 * offered here - and that silently disabled the other two things this transformer does to a class it
	 * does <em>not</em> swap: the runtime interfaces from the interface plan, which exist precisely for
	 * classes that arrive stripped of them, and the runtime stubs, whose owners are runtime classes by
	 * definition. On 1.21.8 the stub file's own deferred entry names a class that is in no payload at all.
	 * So the set is the union of every plan's owners.</p>
	 */
	private static Set<String> loadTargets() {
		Set<String> targets = new HashSet<>();
		try(InputStream stream = PatchedClassTransformer.class.getResourceAsStream(INDEX)) {
			if(stream == null) {
				// Lines where OptiFine already speaks the runtime's names need none of this, and the
				// index is simply absent there.
				LOGGER.info("No " + INDEX + " in this jar; no classes will be swapped in");
			} else {
				for(String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
					String name = line.trim();
					if(name.isEmpty()) {
						continue;
					}
					// Accept both the bare class name and the entry path: the index first shipped as entry
					// paths, and the target factory then received 'Foo.class' as a class name, so no target
					// ever matched and the transformer was silently never called.
					if(name.endsWith(".class")) {
						name = name.substring(0, name.length() - ".class".length());
					}
					targets.add(name.replace('/', '.'));
				}
			}
		} catch(IOException e) {
			LOGGER.warn("could not read " + INDEX + ": " + e);
		}
		int swapped = targets.size();
		addFirstField(targets, KEEP_RUNTIME);
		addFirstField(targets, RUNTIME_INTERFACES);
		addFirstField(targets, STUBS);
		// A traced class has to be a target even when there is no payload for it, because this transformer
		// is only called for its targets. Measured on 1.20.4: net.minecraft.client.Minecraft is not in the
		// index, so -Doptifineoforge.traceScreen=true produced no output at all and the tracer looked
		// broken. The property is read here rather than through the constant below on purpose: TARGETS is
		// initialised before those constants, so reading one would still see its default value and the
		// widening would silently do nothing.
		if(Boolean.getBoolean(TRACE_SCREEN)) {
			targets.add(SCREEN_OWNER.replace('/', '.'));
		}
		LOGGER.info("Patched-class targets: " + swapped + " to swap, " + targets.size() + " in all");
		return Set.copyOf(targets);
	}

	/** Adds the owner column of a tab separated plan file, dotted, to {@code targets}. */
	private static void addFirstField(Set<String> targets, String resource) {
		try(InputStream stream = PatchedClassTransformer.class.getResourceAsStream(resource)) {
			if(stream == null) {
				return;
			}
			for(String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				String text = line.trim();
				if(text.isEmpty() || text.startsWith("#")) {
					continue;
				}
				String owner = text.split("\t")[0].trim();
				if(!owner.isEmpty()) {
					targets.add(owner.replace('/', '.'));
				}
			}
		} catch(IOException e) {
			LOGGER.warn("could not read " + resource + ": " + e);
		}
	}

	/**
	 * The prefix the member restore plan gives the helpers it generates, whose bodies must never be
	 * carried into a kept class; see {@link #addPayloadMembers}.
	 */
	private static final String RESTORE_PREFIX = "optifineoforge$init$";

	/**
	 * Gives a class the loader keeps the members the payload declares and this copy does not have.
	 *
	 * <p>The mirror of the member restore, and it exists because keeping a class is not the same as leaving
	 * it alone: the payload's <em>callers</em> are still installed, and they call whatever their own
	 * compilation saw. Measured on 1.21, where two decisions meet:</p>
	 *
	 * <pre>SpriteLoader is replaced by the payload
	 * SpriteResourceLoader is left as the runtime has it (the interface rule above)
	 *   -&gt; NoSuchMethodError: SpriteResourceLoader.create(java.util.Collection)
	 *      at SpriteLoader.loadAndStitch(SpriteLoader.java:187)</pre>
	 *
	 * <p>{@code MissingTargets} cannot report this by construction: it indexes the payload as well as the
	 * runtime, on the reasoning that a swapped class is what will be loaded - and the whole point here is
	 * that this class is not swapped. So the members come from the payload's own copy, which this
	 * transformer already holds.</p>
	 *
	 * <p>Interface fields are normalised rather than copied, for the reason recorded on the swap path: the
	 * JVM rejects any other modifier combination outright
	 * ({@code ClassFormatError: Illegal field modifiers in class ...: 0x9}). A method with a body copied
	 * into an interface needs no change - a non-abstract, non-static interface method is a default method
	 * by definition.</p>
	 *
	 * <p>Three exclusions and one addition come from the 1.20.x branch (its {@code 8db6879}), where they
	 * were measured on 1.20.2 rather than reasoned about, and the measurement is the same shape as this
	 * line's {@code SpriteResourceLoader} one: {@code net.minecraft.Util} is kept because OptiFine's
	 * compilation of it does not fit the runtime's anonymous classes, OptiFine's own
	 * {@code HttpTexture} then calls {@code Util.getCapeExecutor()} - a member only the payload's
	 * {@code Util} declares - and the failure is</p>
	 *
	 * <pre>NoSuchMethodError: 'java.util.concurrent.ExecutorService net.minecraft.Util.getCapeExecutor()'
	 *   at net.minecraft.client.renderer.texture.HttpTexture.getExecutor(HttpTexture.java:346)
	 *   at net.optifine.player.CapeUtils.downloadCape(CapeUtils.java:71)
	 *   at net.minecraft.client.player.AbstractClientPlayer.&lt;init&gt;</pre>
	 *
	 * <p>which aborts player creation inside the login packet, so the client ticks forever with a null
	 * player and never leaves the loading screen. The exclusions are a non-static final field, a
	 * generated {@code optifineoforge$init$} helper (both rejected later as
	 * {@code IllegalAccessError: Update to non-static final field ... attempted from a different method}),
	 * and the class's own constructors and static initialiser - the two shapes that are <em>known</em> to
	 * disagree between the builds. What is added is the payload's own initialisation statement for every
	 * static field carried over, because a carried declaration without its value is a field nothing ever
	 * fills.</p>
	 */
	private static void addPayloadMembers(ClassNode input, ClassNode patched) {
		if(patched == null) {
			return;
		}
		boolean isInterface = (input.access & Opcodes.ACC_INTERFACE) != 0;
		int fields = 0;
		int methods = 0;
		// The static fields carried over, remembered for the initialisations below: a carried declaration
		// is only half of what the payload had, and the value lives in the payload's own static
		// initialiser - a part this class deliberately does not take wholesale.
		Set<String> carriedStaticFields = new HashSet<>();
		for(FieldNode field : patched.fields) {
			if((field.access & Opcodes.ACC_FINAL) != 0 && (field.access & Opcodes.ACC_STATIC) == 0) {
				// A non-static final field may only be assigned from the class's own constructor, so
				// carrying one over without the payload's constructor produces an initialiser the JVM
				// refuses:
				//   IllegalAccessError: Update to non-static final field net.minecraft.Util$9.cache
				//   attempted from a different method (optifineoforge$init$cache) than the initializer
				//   method <init>
				// measured on 1.20.2 as a client that died inside Main.main. Constructors are not carried,
				// so such a field is left alone - and the same reasoning is why the generated
				// optifineoforge$init$ helpers are excluded below.
				LOGGER.info("Not carrying the payload's final field " + input.name.replace('/', '.') + "."
						+ field.name + ": only the payload's own constructor may assign it, and constructors"
						+ " are not carried over");
				continue;
			}
			if(hasField(input, field.name, field.desc)) {
				continue;
			}
			int access = field.access;
			if(isInterface) {
				access = (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED))
						| Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
			}
			input.fields.add(new FieldNode(access, field.name, field.desc, field.signature, field.value));
			if((field.access & Opcodes.ACC_STATIC) != 0) {
				carriedStaticFields.add(field.name);
			}
			fields++;
		}
		int initialised = carriedStaticFields.isEmpty() ? 0
				: carryStaticInitialisers(input, patched, carriedStaticFields);
		for(MethodNode method : patched.methods) {
			if("<init>".equals(method.name) || "<clinit>".equals(method.name)
					|| method.name.startsWith(RESTORE_PREFIX) || hasMethod(input, method.name, method.desc)) {
				// Constructors and the static initialiser are the two shapes that are known to disagree
				// between the two builds: measured on 1.20.1, the runtime's Util$9 extends Thread while the
				// payload's is the BiFunction cache class behind Util.memoize, so neither can be taken from
				// the other copy. The restore plan's generated helpers are excluded on top of that, and the
				// exclusion was measured: they assign fields of the payload's copy, and against the kept
				// class they are exactly the shape the final-field note above describes.
				continue;
			}
			MethodNode copy = new MethodNode(method.access, method.name, method.desc, method.signature,
					method.exceptions == null ? null : method.exceptions.toArray(new String[0]));
			method.accept(copy);
			input.methods.add(copy);
			methods++;
		}
		if(fields > 0 || methods > 0) {
			LOGGER.info("Gave " + input.name.replace('/', '.') + " the payload's " + fields + " field(s) and "
					+ methods + " method(s), because the payload's own callers are still installed");
		}
		if(initialised > 0) {
			LOGGER.info("Initialised " + initialised + " carried static field(s) in "
					+ input.name.replace('/', '.') + " from the payload's own static initialiser");
		}
	}

	/**
	 * Appends the payload's initialisation statements for the static fields just carried into the kept class.
	 *
	 * <p>The statement for one field is the instruction run that ends in writing it, starting after the
	 * previous write: that is the granularity a kept class can accept without importing the payload's static
	 * initialiser wholesale, which is the one thing that measurably cannot be taken from the payload (it is
	 * where the two builds' anonymous classes disagree). A run whose instructions cannot be copied is skipped
	 * rather than delivered half-written, and the field then keeps its default.</p>
	 *
	 * <p>Pseudo-instructions are skipped rather than treated as an uncopyable node, and that skip is a
	 * measurement rather than tidiness: on 1.20.2, requiring a copy of every node silently dropped the
	 * statement that fills {@code Util.CAPE_EXECUTOR} - the payload's static initialiser carries line
	 * numbers - the field stayed null, and the cape download then failed with
	 * {@code NullPointerException at java.util.concurrent.CompletableFuture.screenExecutor} inside
	 * {@code TextureManager.register} from {@code AbstractClientPlayer.<init>}.</p>
	 *
	 * @return how many statements were appended
	 */
	private static int carryStaticInitialisers(ClassNode input, ClassNode payload, Set<String> fields) {
		MethodNode source = null;
		for(MethodNode method : payload.methods) {
			if("<clinit>".equals(method.name) && "()V".equals(method.desc)) {
				source = method;
				break;
			}
		}
		if(source == null || source.instructions == null) {
			return 0;
		}
		List<InsnList> statements = new ArrayList<>();
		AbstractInsnNode start = source.instructions.getFirst();
		for(AbstractInsnNode insn = start; insn != null; insn = insn.getNext()) {
			if(!(insn instanceof FieldInsnNode write) || write.getOpcode() != Opcodes.PUTSTATIC
					|| !input.name.equals(write.owner) || !fields.contains(write.name)) {
				continue;
			}
			InsnList statement = new InsnList();
			boolean complete = true;
			for(AbstractInsnNode step = start; step != null; step = step.getNext()) {
				if(step.getOpcode() < 0) {
					// Labels, line numbers and stack map frames are pseudo-instructions: they are not copied,
					// and their presence is not a reason to give up.
					continue;
				}
				AbstractInsnNode copy = copyInstruction(step);
				if(copy == null) {
					complete = false;
					break;
				}
				statement.add(copy);
				if(step == insn) {
					break;
				}
			}
			if(complete) {
				statements.add(statement);
			}
			start = insn.getNext();
		}
		if(statements.isEmpty()) {
			return 0;
		}
		InsnList values = new InsnList();
		for(InsnList statement : statements) {
			values.add(statement);
		}
		MethodNode clinit = null;
		for(MethodNode method : input.methods) {
			if("<clinit>".equals(method.name) && "()V".equals(method.desc)) {
				clinit = method;
				break;
			}
		}
		if(clinit == null || clinit.instructions == null) {
			MethodNode created = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
			created.instructions.add(values);
			created.instructions.add(new InsnNode(Opcodes.RETURN));
			created.maxStack = 8;
			created.maxLocals = 0;
			input.methods.add(created);
			return statements.size();
		}
		AbstractInsnNode lastReturn = null;
		for(AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if(insn.getOpcode() == Opcodes.RETURN) {
				lastReturn = insn;
			}
		}
		if(lastReturn == null) {
			return 0;
		}
		clinit.instructions.insertBefore(lastReturn, values);
		clinit.maxStack = Math.max(clinit.maxStack, 8);
		return statements.size();
	}

	/** A copy of one instruction, or null for a kind this repair does not carry over. */
	private static AbstractInsnNode copyInstruction(AbstractInsnNode insn) {
		if(insn instanceof InsnNode plain) {
			return new InsnNode(plain.getOpcode());
		}
		if(insn instanceof MethodInsnNode call) {
			return new MethodInsnNode(call.getOpcode(), call.owner, call.name, call.desc, call.itf);
		}
		if(insn instanceof FieldInsnNode field) {
			return new FieldInsnNode(field.getOpcode(), field.owner, field.name, field.desc);
		}
		if(insn instanceof TypeInsnNode type) {
			return new TypeInsnNode(type.getOpcode(), type.desc);
		}
		if(insn instanceof LdcInsnNode ldc) {
			return new LdcInsnNode(ldc.cst);
		}
		if(insn instanceof IntInsnNode integer) {
			return new IntInsnNode(integer.getOpcode(), integer.operand);
		}
		if(insn instanceof InvokeDynamicInsnNode dynamic) {
			return new InvokeDynamicInsnNode(dynamic.name, dynamic.desc, dynamic.bsm, dynamic.bsmArgs.clone());
		}
		if(insn instanceof VarInsnNode var) {
			return new VarInsnNode(var.getOpcode(), var.var);
		}
		return null;
	}


	private static boolean hasField(ClassNode node, String name, String desc) {
		for(FieldNode field : node.fields) {
			if(field.name.equals(name) && field.desc.equals(desc)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
		ClassNode result = decide(input, context);
		renameSrgMembers(result);
		traceModelLoading(result);
		dump(result);
		return result;
	}

	/**
	 * Writes markers into the two classes whose order decides whether the reload on 1.21 ever finishes.
	 *
	 * <p>Off unless {@code -Doptifineoforge.traceModels=true}, and it can only be written here: a
	 * ModLauncher transformer has to declare its targets before it runs, and these two classes are
	 * already declared by this one, so a transformer of its own would be built and never called - the
	 * trap this repository has already recorded for {@code ModelManager}.</p>
	 *
	 * <p>What it is for. On 1.21 the client reaches its tick loop and the initial resource reload never
	 * completes: no {@code Created: ...-atlas} line, no {@code Sound engine started}, no crash report,
	 * and the rig's dump catches one worker in</p>
	 *
	 * <pre>CustomItems.updateIcons (CustomItems.java:292)
	 *   -> TextureUtils.registerCustomSprites (TextureUtils.java:473)
	 *   -> TextureAtlas.preStitch (TextureAtlas.java:294)
	 *   -> SpriteLoader.lambda$loadAndStitch$6 (SpriteLoader.java:200)</pre>
	 *
	 * <p>and that line is a busy-wait: the bytecode of {@code updateIcons} is
	 * {@code while(!modelsLoaded.get()) Config.sleep(100);} and a poll every 100 ms means a flag that
	 * ever became true is seen within 100 ms - so a worker still polling 109 seconds in says the setter
	 * has never run at all. The setter is {@code CustomItems.loadModels}, whose only caller is
	 * {@code TextureUtils.registerCustomModels}, whose only caller is the last instructions of
	 * OptiFine's own {@code ModelBakery.<init>} - a constructor this transformer installs, because the
	 * payload for this line carries it and the index swaps it in.</p>
	 *
	 * <p>So "does that constructor run, and does it get as far as the call that sets the flag" is the
	 * whole open question, and the three markers it plants in the constructor answer it without a
	 * guess: entering without reaching the call means it threw earlier, reaching the call without
	 * returning from it means {@code registerCustomModels} itself threw, and all three means the flag
	 * was set and the failure is somewhere else entirely. The atlas side gets the same treatment on
	 * {@code preStitch}, so the two are ordered against each other rather than argued about.</p>
	 */
	private static void traceModelLoading(ClassNode node) {
		if(node == null || !Boolean.getBoolean(TRACE_MODELS)) {
			return;
		}
		if(MODEL_BAKERY.equals(node.name)) {
			traceModelBakery(node);
		} else if(TEXTURE_ATLAS.equals(node.name)) {
			traceMethod(node, "preStitch", "TextureAtlas.preStitch",
					new String[] {TEXTURE_UTILS, REGISTER_CUSTOM_SPRITES});
		}
	}

	/**
	 * {@code -Doptifineoforge.traceScreen=true} prints every screen the client switches to, by injecting a
	 * line at the start of {@code Minecraft.setScreen(Screen)}.
	 *
	 * <p>Ported from the 1.20.x branch, where it exists because a screenshot cannot settle "which screen is
	 * the client on": measured on 1.20.4, two captures 70 seconds apart both looked like the Mojang loading
	 * overlay while the game's own log showed texture atlases being built and the render thread alive in its
	 * frame loop. Printing the screen name is the measurement that does not depend on what a window happens
	 * to have painted, and it is the only way a run that ends before its first world frame says where it got
	 * to.</p>
	 *
	 * <p>The print goes through {@code String.valueOf}, not {@code getClass().getName()}, and that is the
	 * half of the port that was measured rather than copied: {@code setScreen} is called with null as a
	 * matter of course, NeoForge's {@code ClientHooks.popGuiLayer} does it whenever the last GUI layer is
	 * popped, and the unconditional {@code getClass()} then threw
	 * {@code NullPointerException: Cannot invoke "Object.getClass()" because "<parameter1>" is null} one
	 * second <em>after</em> a successful join on 1.20.6 - which is why every tracer log of that rig stopped
	 * at {@code ReceivingLevelScreen}. {@code valueOf} answers "null" instead, needs no branch (so no stack
	 * map frames have to be computed for the injected sequence) and keeps the class name in the line for
	 * every screen that is not null.</p>
	 *
	 * <p>It runs before the payload lookup, next to the other tracers, because the class it instruments is
	 * often one OptiFine does not patch at all: {@code net.minecraft.client.Minecraft} has no payload copy
	 * on several lines, so a tracer called only from the swap path would never have run - measured on 1.20.4,
	 * where the property produced no output at all until the target was registered from the property.</p>
	 */
	private static void traceScreen(ClassNode input) {
		if(!Boolean.getBoolean(TRACE_SCREEN) || !SCREEN_OWNER.equals(input.name)) {
			return;
		}
		for(MethodNode method : input.methods) {
			if(!SET_SCREEN.equals(method.name) || !SET_SCREEN_DESC.equals(method.desc) || method.instructions == null) {
				continue;
			}
			InsnList trace = new InsnList();
			trace.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;"));
			trace.add(new TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"));
			trace.add(new InsnNode(Opcodes.DUP));
			trace.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false));
			trace.add(new LdcInsnNode("OPF-SCREEN "));
			trace.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
					"(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
			trace.add(new VarInsnNode(Opcodes.ALOAD, 1));
			trace.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf",
					"(Ljava/lang/Object;)Ljava/lang/String;", false));
			trace.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
					"(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
			trace.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString",
					"()Ljava/lang/String;", false));
			trace.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println",
					"(Ljava/lang/String;)V", false));
			method.instructions.insert(trace);
			LOGGER.info("Tracing every screen switch in Minecraft.setScreen");
		}
	}

	/** The switch the screen tracer hangs on; read from the property, never from a constant. */
	private static final String TRACE_SCREEN = "optifineoforge.traceScreen";

	/** The class that owns the screen switch, and the member it happens in. */
	private static final String SCREEN_OWNER = "net/minecraft/client/Minecraft";
	private static final String SET_SCREEN = "setScreen";
	private static final String SET_SCREEN_DESC = "(Lnet/minecraft/client/gui/screens/Screen;)V";

	/**
	 * Inserts a list after an instruction, which {@code InsnList.insert(location, ...)} does not do.
	 *
	 * <p>Worth its own method because the trap is invisible: {@code insert(location, insns)} inserts
	 * <em>before</em> the location, and a marker written with it lands on the wrong side of the call it
	 * is meant to bracket - a probe that reports "returned from X" before X ran. The repository's own
	 * ReloadProbeFix uses that form for its {@code DUP}-based probes, which is one of the reasons its
	 * output has never been trustworthy; this one says what it means.</p>
	 */
	private static void insertAfter(MethodNode method, AbstractInsnNode location, InsnList added) {
		AbstractInsnNode next = location.getNext();
		if(next == null) {
			method.instructions.add(added);
		} else {
			method.instructions.insertBefore(next, added);
		}
	}

	/** The switch the whole trace hangs on; nothing is written into a class unless it is set. */
	private static final String TRACE_MODELS = "optifineoforge.traceModels";

	/** The class that sets OptiFine's {@code CustomItems.modelsLoaded}. */
	private static final String MODEL_BAKERY = "net/minecraft/client/resources/model/ModelBakery";

	/** The class that waits for it. */
	private static final String TEXTURE_ATLAS = "net/minecraft/client/renderer/texture/TextureAtlas";

	/** OptiFine's own holder of the flag, and the call that sets it. */
	private static final String TEXTURE_UTILS = "net/optifine/util/TextureUtils";
	private static final String REGISTER_CUSTOM_MODELS = "registerCustomModels";
	private static final String REGISTER_CUSTOM_SPRITES = "registerCustomSprites";

	/** Our probe class - the game layer may call it, because it reads the module this jar is. */
	private static final String PROBE = "kynarain/cn/optifineoforge/loader/ReloadProbe";

	/**
	 * Marks the constructor's own steps: entry, the call that sets the flag, and each normal return.
	 *
	 * <p>Whether the call is there at all is a fact about the class the JVM is about to verify, not
	 * about the jar on disk, so it is logged at transform time as well - a run whose log lacks this line
	 * is a run where this class never reached the transformer.</p>
	 */
	private static void traceModelBakery(ClassNode node) {
		for(MethodNode method : node.methods) {
			if(!"<init>".equals(method.name) || method.instructions == null) {
				continue;
			}
			// Collected before anything is inserted: inserting after an instruction the walk is standing on
			// makes the inserted node the next one the walk sees, which is how a marker ends up marking
			// itself.
			List<AbstractInsnNode> returns = new ArrayList<>();
			Map<String, List<AbstractInsnNode>> calls = new LinkedHashMap<>();
			for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if(insn.getOpcode() == Opcodes.RETURN) {
					returns.add(insn);
				} else if(insn instanceof MethodInsnNode call) {
					calls.computeIfAbsent(call.owner + "." + call.name, key -> new ArrayList<>()).add(insn);
				}
			}
			LOGGER.info("trace: the installed " + node.name.replace('/', '.') + ".<init>" + method.desc
					+ " calls " + REGISTER_CUSTOM_MODELS + ": "
					+ calls.containsKey(TEXTURE_UTILS + "." + REGISTER_CUSTOM_MODELS));
			for(String[] step : MODEL_BAKERY_STEPS) {
				List<AbstractInsnNode> found = calls.get(step[0] + "." + step[1]);
				if(found == null) {
					continue;
				}
				// The per-item call runs once for every registered item, so it is reported a few times and
				// the marker after the loop is what says the loop ended.
				boolean repeated = step[2].equals(ITEM_LOAD);
				for(AbstractInsnNode call : found) {
					method.instructions.insertBefore(call, repeated
							? limitedMark("ModelBakery.<init> -> " + step[2], 2)
							: mark("ModelBakery.<init> -> " + step[2]));
					insertAfter(method, call, repeated
							? limitedMark("ModelBakery.<init> <- " + step[2], 2)
							: mark("ModelBakery.<init> <- " + step[2]));
				}
			}
			for(AbstractInsnNode insn : returns) {
				method.instructions.insertBefore(insn, mark("ModelBakery.<init> returning normally"));
			}
			method.instructions.insert(mark("ModelBakery.<init> entered" + method.desc));
			method.maxStack = Math.max(method.maxStack, 2);
		}
	}

	/** The per-item call, the one step whose marker has to be rate limited. */
	private static final String ITEM_LOAD = "loadItemModelAndDependencies (once per item)";

	/**
	 * The steps of the model-loading constructor the trace brackets.
	 *
	 * <p>{@code owner, name, what to call it}. Every one of them is a call the constructor makes between
	 * its entry and the flag setter, so a run that shows the entry marker and then stops partway through
	 * this table names the call that threw without needing a second guess - which matters because the
	 * throw is invisible: it lands in a {@code CompletableFuture} nobody joins, so the client logs
	 * nothing, writes no crash report and keeps ticking.</p>
	 */
	private static final String[][] MODEL_BAKERY_STEPS = {
			{"net/minecraft/util/profiling/ProfilerFiller", "push", "ProfilerFiller.push"},
			{MODEL_BAKERY, "loadBlockModel", "loadBlockModel (the builtin/missing model)"},
			{MODEL_BAKERY, "registerModel", "registerModel"},
			{MODEL_BAKERY, "registerModelAndLoadDependencies", "registerModelAndLoadDependencies"},
			{"net/minecraft/client/resources/model/BlockStateModelLoader", "<init>",
					"BlockStateModelLoader.<init>"},
			{"net/minecraft/client/resources/model/BlockStateModelLoader", "loadAllBlockStates",
					"BlockStateModelLoader.loadAllBlockStates"},
			{"net/minecraft/client/resources/model/BlockStateModelLoader", "getModelGroups",
					"BlockStateModelLoader.getModelGroups"},
			{MODEL_BAKERY, "loadItemModelAndDependencies", ITEM_LOAD},
			{MODEL_BAKERY, "loadSpecialItemModelAndDependencies", "loadSpecialItemModelAndDependencies"},
			{MODEL_BAKERY, "getModel", "ModelBakery.getModel"},
			{"net/optifine/reflect/ReflectorMethod", "call",
					"ReflectorMethod.call (ForgeHooksClient.onRegisterAdditionalModels)"},
			{TEXTURE_UTILS, REGISTER_CUSTOM_MODELS, "TextureUtils." + REGISTER_CUSTOM_MODELS},
	};

	/** A rate-limited call to {@link ReloadProbe#markLimited}. */
	private static InsnList limitedMark(String label, int limit) {
		InsnList call = new InsnList();
		call.add(new LdcInsnNode(label));
		call.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, limit));
		call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, "markLimited", "(Ljava/lang/String;I)V",
				false));
		return call;
	}

	/** Marks a named method's entry, every normal return, and optionally its call to a named method. */
	private static void traceMethod(ClassNode node, String name, String label, String[] called) {
		for(MethodNode method : node.methods) {
			if(!name.equals(method.name) || method.instructions == null) {
				continue;
			}
			List<AbstractInsnNode> returns = new ArrayList<>();
			List<AbstractInsnNode> calls = new ArrayList<>();
			for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if(insn.getOpcode() == Opcodes.RETURN) {
					returns.add(insn);
				} else if(called != null && insn instanceof MethodInsnNode call
						&& called[0].equals(call.owner) && called[1].equals(call.name)) {
					calls.add(insn);
				}
			}
			LOGGER.info("trace: the installed " + node.name.replace('/', '.') + "." + name + method.desc
					+ " is instrumented");
			for(AbstractInsnNode insn : returns) {
				method.instructions.insertBefore(insn, mark(label + " returning"));
			}
			for(AbstractInsnNode call : calls) {
				method.instructions.insertBefore(call, mark(label + " reached " + called[1]));
				insertAfter(method, call, mark(label + " returned from " + called[1]));
			}
			method.instructions.insert(mark(label + " entered" + method.desc));
			method.maxStack = Math.max(method.maxStack, 2);
		}
	}

	/** A call to {@link ReloadProbe#mark}, ready to be inserted in front of an instruction. */
	private static InsnList mark(String label) {
		InsnList call = new InsnList();
		call.add(new LdcInsnNode(label));
		call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, "mark", "(Ljava/lang/String;)V", false));
		return call;
	}

	/**
	 * The SRG names the loader has to rewrite while transforming, {@code owner name official}.
	 *
	 * <p>Rewriting the jars cannot do this, and that is measured rather than assumed. On a line whose
	 * OptiFine build predates OptiFine's switch to official member names, OptiFine's transformation service
	 * patches classes <em>at load time</em> from the {@code patch/srg/*.xdelta} data inside its jar - binary
	 * deltas, which rewriting the jar's {@code .class} entries cannot reach. Its output therefore still
	 * carries SRG names, and the loader is the only place left to fix them. On 1.21 the class the JVM
	 * verified contained</p>
	 *
	 * <pre>net/minecraft/server/packs/resources/Resource
	 *   .m_215509_()Lnet/minecraft/server/packs/resources/ResourceMetadata;</pre>
	 *
	 * <p>while no entry of the shipped loader jar contained that string at all; the run died with
	 * {@code NoSuchMethodError: 'ResourceMetadata Resource.m_215509_()'} and the sound engine never started.
	 * {@code SrgNameTable} writes this table offline, limited to the owners the payload's own classes
	 * declare or reference - which is what keeps it a few hundred kilobytes instead of the whole mapping.</p>
	 */
	private static final String SRG_TABLE = "/optifineoforge/srg-to-official.txt";

	/** The shape of an SRG member name, the same one the offline tools look for. */
	private static final java.util.regex.Pattern SRG_NAME = java.util.regex.Pattern.compile("[fm]_\\d+_");

	/** {@code owner} to its {@code srg} to {@code official} member names. */
	private static final Map<String, Map<String, String>> SRG_NAMES = loadSrgNames();

	private static Map<String, Map<String, String>> loadSrgNames() {
		Map<String, Map<String, String>> result = new LinkedHashMap<>();
		try(InputStream stream = PatchedClassTransformer.class.getResourceAsStream(SRG_TABLE)) {
			if(stream == null) {
				return Map.of();
			}
			for(String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				String text = line.trim();
				if(text.isEmpty() || text.startsWith("#")) {
					continue;
				}
				String[] parts = text.split("\t");
				if(parts.length != 3) {
					continue;
				}
				result.computeIfAbsent(parts[0], key -> new LinkedHashMap<>()).put(parts[1], parts[2]);
			}
		} catch(IOException e) {
			LOGGER.warn("could not read " + SRG_TABLE + ": " + e);
		}
		int names = result.values().stream().mapToInt(Map::size).sum();
		if(names > 0) {
			LOGGER.info("SRG names to rewrite while transforming: " + names + " across " + result.size()
					+ " owner(s)");
		}
		return Map.copyOf(result);
	}

	/** The official name for an SRG-shaped member of {@code owner}, or null when the table has none. */
	private static String officialName(String owner, String name) {
		if(!SRG_NAME.matcher(name).matches()) {
			return null;
		}
		Map<String, String> names = SRG_NAMES.get(owner);
		return names == null ? null : names.get(name);
	}

	/**
	 * Rewrites the SRG-shaped member names a class references, where the table knows them.
	 *
	 * <p>Only names of that shape are touched, and only where the table answers for the owner, so this is
	 * inert on every line whose OptiFine already speaks official names - all of them but 1.21.</p>
	 *
	 * <p>The rewrite is skipped where the member it would rewrite <em>is declared by the copy this jar
	 * installs</em>, and that exception is the repair for the 1.21 hang. See
	 * {@link #declaredByInstalledPayload} for the measurement; in short, a reference into a class we swap
	 * must keep the name that class declares, because the declaration is deliberately never renamed
	 * (renaming declarations is what emptied 1.21's log and killed {@code Reflector.<clinit>} - the comment
	 * below records it), and on this line the payload holds two different methods that the table's entry
	 * collapses onto one name.</p>
	 */
	private static void renameSrgMembers(ClassNode node) {
		// On by default, off with -Doptifineoforge.renameSrg=false. The first attempt at this guard was
// written as !("false".equals(...)), which reads correctly and is inverted: an unset property is not
// "false", so the negation was true and the pass returned every time - measured as a run that loaded
// the table and renamed nothing, while the same build with the property set to true renamed five names
// and removed the registration failure. The README carries that discrepancy; this is its cause.
		if(SRG_NAMES.isEmpty() || node == null
				|| "false".equals(System.getProperty("optifineoforge.renameSrg"))) {
			return;
		}
		int renamed = 0;
		int kept = 0;
		// References only, never declarations, and that is a measured correction rather than caution. With
		// declarations renamed as well, 1.21 went from 299 [OptiFine] lines at the title screen to 0 and died
		// inside OptiFine's own Reflector.<clinit>: OptiFine's classes name their own members in the same
		// m_/f_ shape this table uses, so rewriting a declaration rewrites OptiFine's own name for it. What
		// its patch data gets wrong is what it CALLS, and those members are declared by the runtime classes
		// the table describes - so only the call sites are touched.
		for(MethodNode method : node.methods) {
			if(method.instructions == null) {
				continue;
			}
			for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if(insn instanceof MethodInsnNode call) {
					String official = officialName(call.owner, call.name);
					if(official != null) {
						if(declaredByInstalledPayload(call.owner, call.name)) {
							kept++;
						} else {
							call.name = official;
							renamed++;
						}
					}
				} else if(insn instanceof FieldInsnNode fieldInsn) {
					String official = officialName(fieldInsn.owner, fieldInsn.name);
					if(official != null) {
						if(declaredByInstalledPayload(fieldInsn.owner, fieldInsn.name)) {
							kept++;
						} else {
							fieldInsn.name = official;
							renamed++;
						}
					}
				} else if(insn instanceof InvokeDynamicInsnNode dynamic) {
					// A lambda's target travels as a handle in the bootstrap arguments, so a name there
					// needs the same treatment as a call instruction - and a lambda body is exactly where
					// 1.21's failure came from.
					for(int index = 0; index < dynamic.bsmArgs.length; index++) {
						if(dynamic.bsmArgs[index] instanceof Handle handle
								&& SRG_NAME.matcher(handle.getName()).matches()) {
							String official = officialName(handle.getOwner(), handle.getName());
							if(official != null) {
								if(declaredByInstalledPayload(handle.getOwner(), handle.getName())) {
									kept++;
								} else {
									dynamic.bsmArgs[index] = new Handle(handle.getTag(), handle.getOwner(),
											official, handle.getDesc(), handle.isInterface());
									renamed++;
								}
							}
						}
					}
				}
			}
		}
		if(renamed > 0) {
			LOGGER.info("Rewrote " + renamed + " SRG name(s) in " + node.name.replace('/', '.')
					+ ": OptiFine's patch data emits them");
		}
		if(kept > 0) {
			LOGGER.info("Kept " + kept + " SRG name(s) in " + node.name.replace('/', '.')
					+ ": the copy this jar installs declares those members under them");
		}
	}

	/**
	 * Whether this jar installs a copy of {@code owner} that declares {@code name} under that name.
	 *
	 * <p>This is the guard that keeps OptiFine's own call graph intact, and it exists because the rename
	 * above silently redirected a call to a different method. Measured on 1.21, and it is the whole
	 * defect:</p>
	 *
	 * <p>The payload's {@code ModelBakery} declares <em>two</em> methods the table's entry
	 * {@code ModelBakery m_119364_ -> loadBlockModel} collapses onto one name -
	 * {@code private m_119364_(ResourceLocation) throws IOException}, which is the vanilla builtin-aware
	 * loader ({@code builtin/generated} to {@code GENERATION_MARKER}, {@code builtin/entity} to
	 * {@code BLOCK_ENTITY_MARKER}, {@code builtin/*} through {@code BUILTIN_MODELS}), and
	 * {@code public loadBlockModel(ResourceLocation)}, which is OptiFine's own resource loader and returns
	 * <em>null</em> after warning when the resource is not there. The constructor's call for the missing
	 * model is on the SRG name; renaming it sent that call to the other method, which found no resource for
	 * {@code minecraft:builtin/missing}, logged</p>
	 *
	 * <pre>[OptiFine] Error loading model: minecraft:builtin/missing
	 * [OptiFine] java.io.FileNotFoundException: minecraft:builtin/missing</pre>
	 *
	 * <p>and returned null. The null then went into the runtime's {@code BlockStateModelLoader} constructor,
	 * which builds the missing model's group with {@code List.of(missingModel)} - and that rejects a null
	 * element - so the constructor died there, before its last step
	 * {@code TextureUtils.registerCustomModels(this)}, the only thing that
	 * sets {@code CustomItems.modelsLoaded}. OptiFine's {@code CustomItems.updateIcons}, reached from
	 * {@code TextureAtlas.preStitch} on an atlas-stitching worker, is
	 * {@code while(!modelsLoaded.get()) Config.sleep(100)}, so it waited for ever: the client kept ticking,
	 * no crash report was written, and the resource reload never finished.</p>
	 *
	 * <p>Three conditions make the skip safe, and each is checked rather than assumed:</p>
	 *
	 * <ul>
	 * <li>the owner has a payload copy in this jar, so the question "what does the loaded class declare" has
	 * an answer here at all. For every other owner - {@code Resource} is the one that mattered, since
	 * {@code Resource.m_215509_()} is what this pass was written for - the runtime's copy is what is loaded
	 * and it declares the official name, so the rewrite still happens;</li>
	 * <li>that copy is the one loaded: a class on the keep plan, and an interface whose members are restored
	 * from its donor, are deliberately loaded as the runtime has them, so OptiFine's declarations are not
	 * there to resolve against and the rewrite must still happen. Both rules are the same ones
	 * {@link #decide} applies;</li>
	 * <li>the member really is declared by it. A reference to an SRG name nothing declares keeps the
	 * rewrite, because the official name is then the only one that resolves.</li>
	 * </ul>
	 *
	 * <p>The alternative was to bound OptiFine's wait instead - the flag is unreachable, so a timeout would
	 * let the reload finish - and it is the wrong repair here: the constructor has a real defect under it
	 * (a null missing model, which the shipped game would then bake against), the wait is OptiFine's own
	 * liveness check for the same information, and bounding it would buy the reload at the cost of exactly
	 * what it waits for: OptiFine's custom item sprites, silently missing for that reload.</p>
	 *
	 * <p>One case this predicate does not cover, and it is worth naming rather than leaving to be
	 * discovered: {@link #decide} can also refuse a payload class because its hierarchy does not reach the
	 * runtime's superclass, and that refusal is a runtime comparison this method cannot make. A class left
	 * alone that way keeps its own SRG-named declarations out of the loaded class, so a reference into it
	 * would need the official name after all. No class on 1.21 is left alone for that reason - the three its
	 * log reports are interfaces whose members are restored from a donor, which the rule below does cover -
	 * and the line that would need it is the one whose log shows a "Left ... alone: the payload's copy of it
	 * extends ..." message.</p>
	 */
	private static boolean declaredByInstalledPayload(String owner, String name) {
		return declaredNames(owner).contains(name);
	}

	/**
	 * The member names the payload copy of {@code owner} declares, or an empty set when this jar does not
	 * install that copy.
	 *
	 * <p>Cached per owner: a transformer is called once per class, but a class can hold hundreds of
	 * references and the same owners recur, and reading and parsing a class file per reference would be
	 * work with no answer in it.</p>
	 */
	private static Set<String> declaredNames(String owner) {
		return PAYLOAD_DECLARATIONS.computeIfAbsent(owner, key -> {
			ClassNode payload = new ClassNode();
			try(InputStream stream = PatchedClassTransformer.class.getResourceAsStream(PREFIX + key + ".class")) {
				if(stream == null) {
					return Set.of();
				}
				new ClassReader(stream.readAllBytes()).accept(payload,
						ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			} catch(IOException e) {
				LOGGER.warn("could not read the payload copy of " + key + ": " + e);
				return Set.of();
			}
			// The same two rules decide() applies before it installs anything: a class the keep plan keeps,
			// and an interface whose members the plan restores, are left as the runtime has them.
			if(KEEP_RUNTIME_CLASSES.contains(key)) {
				return Set.of();
			}
			if((payload.access & Opcodes.ACC_INTERFACE) != 0 && RESTORED_CLASSES.contains(key)) {
				return Set.of();
			}
			Set<String> names = new HashSet<>();
			for(MethodNode method : payload.methods) {
				names.add(method.name);
			}
			for(FieldNode field : payload.fields) {
				names.add(field.name);
			}
			return Set.copyOf(names);
		});
	}

	/** {@code owner} internal name to the member names its installed payload copy declares. */
	private static final Map<String, Set<String>> PAYLOAD_DECLARATIONS =
			new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Writes the class this transformer hands back, when {@code -Doptifineoforge.dump=<dir>} is set.
	 *
	 * <p>Added because reading the jars on disk stopped being enough: on 1.21 a run failed with
	 * {@code NoSuchMethodError: Resource.m_215509_()} thrown from
	 * {@code SpriteResourceLoader.lambda$create$0}, and a byte scan of both shipped jars found the string
	 * {@code m_215509_} in neither - so the offending copy is made at load time, somewhere between the jar
	 * and the class the JVM verifies. Guessing which step did it was a mistake worth not repeating; this
	 * answers it by writing the final bytes out.</p>
	 */
	private static void dump(ClassNode node) {
		String directory = System.getProperty("optifineoforge.dump");
		if(directory == null || node == null || node.name == null) {
			return;
		}
		try {
			Path target = Path.of(directory, node.name + ".class");
			Files.createDirectories(target.getParent());
			ClassWriter writer = new ClassWriter(0);
			node.accept(writer);
			Files.write(target, writer.toByteArray());
		} catch(Exception e) {
			LOGGER.warn("could not dump " + node.name + ": " + e);
		}
	}

	private ClassNode decide(ClassNode input, ITransformerVotingContext context) {
		logModulesOnce();
		// Stubs first and unconditionally, because some belong to runtime classes that are never swapped -
		// the case that used to fall through the gap. OptiFine's GameRenderer calls
		// LoadingOverlay.update(), a method only OptiFine's own LoadingOverlay declares, so leaving the
		// overlay unswapped to avoid its clash with NeoForge's loading screen broke the pair:
		//   NoSuchMethodError: 'void net.minecraft.client.gui.screens.LoadingOverlay.update()'
		// Giving the runtime's overlay that one method keeps both halves working.
		stubMissing(input);
		// And the runtime interfaces, before anything decides whether this class is swapped at all. They
		// cannot be read off the class being handed over, and that is measured rather than cautious: on
		// 1.21.8 OptiFine's transformation service registers before this one, its patch of a class is its
		// own compilation of that class, and its compilation never heard of NeoForge's extension
		// interfaces - so a class arriving here has lost them whether or not this transformer replaces it.
		// What that cost, on the line it was measured: the runtime's own ModelWrapper calls
		// UnbakedGeometry.bake(..., ContextMap), a method only UnbakedGeometryExtension declares.
		injectPlannedInterfaces(input);
		// And the tracers, also before the payload lookup, for the same reason the interface injection sits
		// here: the classes most worth tracing are often ones OptiFine does not patch at all - measured on
		// 1.20.4, net.minecraft.client.Minecraft has no payload copy, so a tracer called only from the swap
		// path never ran and the screen switch it was supposed to report produced no output at all.
		traceScreen(input);
		ClassNode patched;
		try(InputStream stream = PatchedClassTransformer.class.getResourceAsStream(PREFIX + input.name + ".class")) {
			if(stream == null) {
				return input;
			}
			patched = new ClassNode();
			new ClassReader(stream.readAllBytes()).accept(patched, 0);
		} catch(IOException e) {
			LOGGER.warn("could not read the patched " + input.name + ": " + e);
			return input;
		}

		// The keep plan's blunt form, and it comes before every other decision about this class for that
		// reason: a class listed here cannot be repaired member by member, so nothing below should be
		// reached for it. See KEEP_RUNTIME_CLASSES for the measurement that put ModelDiscovery$ModelWrapper
		// on the list. The runtime stubs above have already been applied, and they must be - the payload is
		// not installed, but this class as the runtime has it is still the one OptiFine's classes call.
		if(KEEP_RUNTIME_CLASSES.contains(input.name)) {
			LOGGER.info("Left " + input.name.replace('/', '.') + " alone: the keep plan keeps this runtime's"
					+ " copy whole, because a constant the payload inlined into its own bodies does not match"
					+ " the one here");
			addPayloadMembers(input, patched);
			return input;
		}

		// A payload class is only a patch of this one if it really is the same class, and the superclass
		// is where that assumption is checked. Two different things make the two copies disagree.
		//
		// Numbered nested names: those numbers are assigned by whoever built the artefact, and the two
		// artefacts here were built differently - OptiFine's payload takes its names from the obfuscated
		// jar, the runtime's from NeoForm. Measured on 1.20.1: the runtime's net.minecraft.Util$9 extends
		// java.lang.Thread, while the payload's Util$9 is the BiFunction cache class behind Util.memoize.
		// Installing it broke the class that uses it:
		//
		//   VerifyError: Bad type on operand stack
		//     Location: net/minecraft/Util.m_137584_()V @13: invokevirtual
		//     Reason: Type 'net/minecraft/Util$9' is not assignable to 'java/lang/Thread'
		//
		// NeoForge's own hierarchy changes: on a Forge-targeted line the payload extends the Forge type
		// OptiFine was compiled against, while the runtime class extends the NeoForge type that replaced
		// it. Measured on 1.21.1 - payload BlockEntity extends
		// net.minecraftforge.common.capabilities.CapabilityProvider, runtime BlockEntity extends
		// net.neoforged.neoforge.attachment.AttachmentHolder - and the swap then fails far from here:
		//
		//   VerifyError: Bad type on operand stack
		//     Location: net/neoforged/neoforge/attachment/AttachmentSync.onChunkSent(...) @82: invokestatic
		//     Reason: Type 'net/minecraft/world/level/block/entity/BlockEntity' is not assignable to
		//             'net/neoforged/neoforge/attachment/AttachmentHolder'
		//
		// The second case is repaired here, on the class being swapped, and where the repair happens is
		// the whole lesson: Forge's API types travel with this jar as shims, so the obvious fix looks
		// like re-parenting the shim - make CapabilityProvider extend AttachmentHolder - and it cannot
		// work. This jar's module sits *below* the game layer, and a module reads the layers under it,
		// not the ones above: the shim's own superclass then fails to resolve,
		//
		//   NoClassDefFoundError: net/neoforged/neoforge/attachment/AttachmentHolder
		//     at cpw.mods.cl.ModuleClassLoader.loadFromModule(ModuleClassLoader.java:311)
		//
		// while the game layer reads this one without any help (the same log line says
		// "optifine -> minecraft: reads it = false" and the swap still resolves every shim it names).
		// So the payload's superclass is rewritten onto the runtime's instead, and {@link #reparent}
		// does that, including the constructor's super call.
		// A class the plan means to move is attempted even when the two supers already agree, because
		// agreement does not mean the reference is the runtime's copy. Where OptiFine's own transformer has
		// already replaced the class, what arrives here carries the Forge superclass too - measured on
		// 1.21.8, where the guard that used to be here skipped the whole block on that basis and left
		// BlockEntity on a Forge superclass, so NeoForge's own code failed verification against it:
		//
		//   VerifyError: Bad type on operand stack
		//     Type 'net/minecraft/world/level/block/entity/BlockEntity' is not assignable to
		//     'net/neoforged/neoforge/attachment/AttachmentHolder'
		//
		// HierarchyPlan measured the plan from these same jars, so when the reference cannot be used the
		// plan is applied on its own measurement. The reference is unusable exactly when it already extends
		// the Forge type the payload extends, which is the only way the two can agree while the runtime's
		// own copy does not, and each outcome is logged rather than inferred from two silences.
		boolean planned = REPARENTS_BY_CLASS.containsKey(input.name);
		boolean referenceIsRuntime = !planned || !sameName(patched.superName, input.superName);
		if(planned) {
			LOGGER.info("Reparent plan covers " + input.name.replace('/', '.') + ": the payload extends "
					+ patched.superName + ", the class handed over extends " + input.superName
					+ (referenceIsRuntime
							? " - so the plan is checked against that copy"
							: " - equal, so that copy is not the runtime's and the plan is applied on its own"
									+ " measurement"));
		}
		if(planned || !referenceIsRuntime) {
			String problem = reparent(patched, referenceIsRuntime ? input : null);
			if(problem == null && referenceIsRuntime) {
				problem = hierarchyProblem(patched.superName, input.superName);
			}
			if(problem != null) {
				LOGGER.info("Left " + input.name.replace('/', '.') + " alone: " + problem);
				return input;
			}
		}

		// An interface that the runtime adds members to is left as the runtime has it, and this is a
		// measured rule rather than caution. An interface carries no constructor, so the only place a
		// static field of it can be assigned is its static initialiser - and installing OptiFine's copy
		// replaces that initialiser with one that knows nothing about NeoForge's fields. Measured on
		// 1.21.8, where the runtime's BlockStateModel$Unbaked declares SINGLE_MODEL_CODEC and
		// WEIGHTED_MODEL_CODEC that NeoForge's own BlockStateModelHooks reads while registering block
		// state models. The plan restores the declarations so that the members exist, but the donor of an
		// interface carries no initialiser to restore the values with, and what is left is a field the
		// runtime would have filled and that nothing fills here.
		//
		// There is also a blunt form of the same failure, which is how this was found rather than
		// predicted: the donors are written as plain classes, so a restored interface field arrives with
		// the class's modifiers -
		//
		//   java.lang.ClassFormatError: Illegal field modifiers in class BlockStateModel$Unbaked: 0x9
		//
		// 0x9 being public static with no final, which no interface field may be. Normalising the
		// modifiers would have made the class loadable and left it broken in the way described above, so
		// the class is not installed at all.
		if((patched.access & Opcodes.ACC_INTERFACE) != 0 && RESTORED_CLASSES.contains(patched.name)) {
			LOGGER.info("Left " + patched.name.replace('/', '.') + " alone: the runtime adds members to that "
					+ "interface, and installing OptiFine's copy would replace the static initialiser that "
					+ "fills them");
			addPayloadMembers(input, patched);
			return input;
		}

		// Content in place rather than returning OptiFine's node: the transformers after this one in
		// the chain, ours included, are handed the same node and expect the class they were told about.
		input.superName = patched.superName;
		// The access flags matter as much as the members, and taking only part of the word was the mistake.
		// Forcing the visibility bits fixed "cannot access its superinterface" - OptiFine makes
		// OptionInstance$SliderableValueSet public so that its own class may implement it - and the very
		// next launch failed the mirror image: "cannot inherit from final class OptionInstance", because
		// OptiFine's patch also drops final so that SliderPercentageOptionOF may extend it. OptiFine's
		// compilation is the version of this class for this runtime, so its whole access word wins.
		input.access = patched.access;
		// Interfaces are additive, not a replacement. OptiFine's compilation of an interface extends the
		// Forge extension interface while the runtime's extends NeoForge's own, and the two carry
		// different inherited members - so replacing the list outright removed a route the runtime's own
		// callers were compiled against:
		//   runtime ModelBaker extends net.neoforged.neoforge.client.extensions.IModelBakerExtension
		//   OptiFine ModelBaker extends net.minecraftforge.client.extensions.IForgeModelBaker
		//   NoSuchMethodError: 'BakedModel ModelBaker.bake(ResourceLocation, ModelState, Function)'
		// Keeping both lets either route resolve.
		//
		// The same holds for a class, and there the runtime's interfaces are not a detail: NeoForge
		// patches a game class to implement its extension interface and its own code then casts to it,
		// so a swapped class that drops the interface dies at the first cast instead:
		//   runtime BlockEntity implements net.neoforged.neoforge.common.extensions.IBlockEntityExtension
		//   payload BlockEntity implements net.minecraftforge.common.extensions.IForgeBlockEntity
		//   ClassCastException: BlockEntity cannot be cast to IBlockEntityExtension
		// An interface added this way obliges the class to implement its abstract methods, and this is
		// why the addition is safe anyway: an abstract method of an injected interface is implemented by
		// the runtime class OptiFine compiled instead of, so it is a member of the runtime class that
		// the payload lacks - exactly what MemberRestorePlan restores from the donor classes. Measured
		// for the one abstract method IBlockEntityExtension declares, getPersistentData(): the plan for
		// this line restores BlockEntity.customPersistentData, the field NeoForge's implementation reads.
		List<String> kept = new ArrayList<>(patched.interfaces == null ? List.<String>of() : patched.interfaces);
		int injected = 0;
		for(String name : input.interfaces) {
			if(!kept.contains(name)) {
				kept.add(name);
				injected++;
			}
		}
		// The plan's interfaces are already on `input` - injectPlannedInterfaces ran before the payload was
		// even looked at, and it has to, because the class that needs them is not always a swapped one. The
		// loop above therefore keeps them without knowing it, which is the point: one place decides, and
		// the swap cannot undo it.
		input.interfaces = kept;
		if((patched.access & Opcodes.ACC_INTERFACE) == 0 && injected > 0) {
			LOGGER.info("Kept the runtime's " + injected + " interface(s) on " + input.name.replace('/', '.')
					+ ": " + patched.interfaces.size() + " from the payload, " + kept.size() + " in place");
		}
		input.signature = patched.signature;
		// Members are not the same story as the class. Taking OptiFine's word for the class flags is
		// right - it changes them on purpose - but for a member the game's own copy may have been widened
		// by an access transformer, and NeoForge's code then relies on that. Replacing RenderType wholesale
		// made NeoForgeRenderTypes\$Internal fail:
		//   IllegalAccessError: ... tried to access method 'RenderType.create(...)'
		// OptiFine's compilation has create() narrower than NeoForge's widened version, so each member
		// keeps whichever of the two visibilities is wider.
		Map<String, Integer> wasMethods = visibilityOf(input.methods);
		Map<String, Integer> wasFields = visibilityOfFields(input.fields);
		// Snapshotted before the swap: a few members must keep the game's body, and by the time the swap is
		// done the originals are only reachable through this list.
		List<MethodNode> originalMethods = new ArrayList<>(input.methods);
		List<FieldNode> fields = new ArrayList<>(patched.fields);
		List<MethodNode> methods = new ArrayList<>(patched.methods);
		for(FieldNode field : fields) {
			Integer wasField = wasFields.get(field.name + field.desc);
			int mergedField = wider(field.access, wasField);
			if((input.access & Opcodes.ACC_INTERFACE) != 0) {
				// An interface's fields have to be public static final whatever either copy says, and the
				// JVM rejects the class outright otherwise. Measured, back when final was cleared for
				// every field: "ClassFormatError: Illegal field modifiers in class
				// com/mojang/blaze3d/vertex/VertexConsumer: 0x9", where 0x9 is public final.
				mergedField = (mergedField & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED))
						| Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
			} else if(wasField == null || (wasField & Opcodes.ACC_FINAL) == 0) {
				// final is not inherited from the payload, and this is the narrow form of a rule that was
				// wrong twice before it was right. NeoForge's access transformers strip final from fields
				// its own subclasses assign, and then a swapped class carrying the payload's final bit
				// rejects that assignment:
				//
				//   IllegalAccessError: Update to non-static final field
				//     net.minecraftforge.client.ForgeRenderTypes$CustomizableTextureState.f_110131_
				//     attempted from a different class
				//
				// That class extends RenderStateShard$TextureStateShard - measured with javap - so the
				// field is inherited from a class OptiFine does patch, and the write is NeoForge's own
				// constructor. static is deliberately left as the payload has it: taking it from the
				// runtime as well produced the VertexFormat failure above.
				mergedField &= ~Opcodes.ACC_FINAL;
			}
			field.access = mergedField;
		}
		for(MethodNode method : methods) {
			method.access = wider(method.access, wasMethods.get(method.name + method.desc));
		}
		// And the runtime's own widened word, which the two lines above cannot see when OptiFine replaced
		// the class before this transformer ran. See RUNTIME_ACCESS.
		int floored = 0;
		for(FieldNode field : fields) {
			Integer floorWord = runtimeAccessOf(input.name, field.name, field.desc);
			if(floorWord != null) {
				int before = field.access;
				field.access = wider(field.access, floorWord);
				if(field.access != before) {
					floored++;
				}
			}
		}
		for(MethodNode method : methods) {
			Integer floorWord = runtimeAccessOf(input.name, method.name, method.desc);
			if(floorWord != null) {
				int before = method.access;
				method.access = wider(method.access, floorWord);
				if(method.access != before) {
					floored++;
				}
			}
		}
		if(floored > 0) {
			LOGGER.info("Widened " + floored + " member(s) of " + input.name.replace('/', '.')
					+ " to the runtime's access from the plan");
		}
		input.fields = fields;
		input.methods = methods;
		// The planned runtime stubs have to be applied a second time here, and this is measured rather than
		// defensive. `decide()` calls stubMissing() before it even looks at the payload, because some stub
		// owners are classes that are never swapped - but for a class that IS swapped, the assignment above
		// replaces the runtime's whole member list with the payload's, and the stub added a moment earlier goes
		// with it. stubMissing() only adds what is absent, so calling it again is idempotent.
		//
		// Measured on 1.21 / 1.21.1 / 1.21.3 (2026-09-22): the repair plan reparents
		//   net/minecraft/world/level/block/entity/BlockEntity -> net/neoforged/neoforge/attachment/AttachmentHolder
		// and the payload's BlockEntity.<init> calls this.gatherCapabilities(), a method that came from the Forge
		// superclass the reparent drops (the runtime's BlockEntity does not declare it either). The member was in
		// the stub list the loader received for those lines, the first stubMissing() did add it to the runtime
		// node, and this assignment threw it away again - the client died at "Initializing game" with
		//   java.lang.NoSuchMethodError: 'void net.minecraft.world.level.block.entity.BlockEntity.gatherCapabilities()'
		//   at BlockEntity.<init>(BlockEntity.java:59/60)
		// (crash-2026-09-22_03.56.08-client.txt and its 1.21.1/1.21.3 twins, all after the stub was verified to be
		// inside the shipped jar's optifineoforge/stubs.txt).
		stubMissing(input);
		keepRuntimeBodies(input, originalMethods);
		LOGGER.info("Replaced " + input.name.replace('/', '.') + " with OptiFine's patched version ("
				+ fields.size() + " fields, " + methods.size() + " methods)");
		return input;
	}

	/**
	 * The classes the member restore plan has something to put back into, read from the same file the
	 * transformer that does the restoring reads. Only the owner of each line matters here.
	 */
	private static final String MEMBER_RESTORES = "/optifineoforge/member-restores.txt";

	private static final Set<String> RESTORED_CLASSES = loadRestoredClasses();

	private static Set<String> loadRestoredClasses() {		Set<String> result = new HashSet<>();
		try(InputStream stream = PatchedClassTransformer.class.getResourceAsStream(MEMBER_RESTORES)) {
			if(stream == null) {
				return Set.of();
			}
			for(String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				String[] parts = line.trim().split(" ", 4);
				if(parts.length >= 4) {
					result.add(parts[1]);
				}
			}
		} catch(IOException e) {
			LOGGER.warn("could not read " + MEMBER_RESTORES + ": " + e);
		}
		LOGGER.info("Classes with members to restore: " + result.size());
		return Set.copyOf(result);
	}

	/** Adds the interfaces the plan requires of {@code input}, and says so when it changes anything. */
	private static void injectPlannedInterfaces(ClassNode input) {
		List<String> wanted = RUNTIME_INTERFACES_BY_CLASS.get(input.name);
		if(wanted == null) {
			return;
		}
		List<String> interfaces = input.interfaces == null ? new ArrayList<>() : new ArrayList<>(input.interfaces);
		int added = 0;
		for(String name : wanted) {
			if(!interfaces.contains(name)) {
				interfaces.add(name);
				added++;
			}
		}
		if(added > 0) {
			input.interfaces = interfaces;
			LOGGER.info("Injected " + added + " runtime interface(s) on " + input.name.replace('/', '.')
					+ " from the interface plan: " + wanted);
		}
	}

	private static boolean loggedModules;

	/**
	 * Members whose runtime copy this line's access transformers widened, {@code owner name desc access}.
	 *
	 * <p>The visibility rule in {@link #transform} takes the wider of the payload's own member and of the
	 * one on the class it was handed - and on a line where OptiFine's transformer replaced that class
	 * first, both of those are the payload's. The runtime's widened copy is then nowhere in reach, and
	 * NeoForge's own code, which was compiled against the wider one, fails at the first access. Measured on
	 * 1.21:</p>
	 *
	 * <pre>IllegalAccessError: class NeoForgeRenderTypes$Internal tried to access protected field
	 *   RenderStateShard.RENDERTYPE_ENTITY_SOLID_SHADER</pre>
	 *
	 * <p>{@code PayloadDrift} writes this list from the runtime jar, offline, which is the only place that
	 * copy is available. The field is a floor, not a replacement: the payload's own flags still win where
	 * they are wider.</p>
	 */
	private static final String RUNTIME_ACCESS = "/optifineoforge/runtime-access.txt";

	/** {@code owner|name|desc} to the access word the runtime's copy carries. */
	private static final Map<String, Integer> RUNTIME_ACCESS_BY_MEMBER = loadRuntimeAccess();

	private static Map<String, Integer> loadRuntimeAccess() {
		Map<String, Integer> result = new LinkedHashMap<>();
		try(InputStream stream = PatchedClassTransformer.class.getResourceAsStream(RUNTIME_ACCESS)) {
			if(stream == null) {
				return Map.of();
			}
			for(String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				String text = line.trim();
				if(text.isEmpty() || text.startsWith("#")) {
					continue;
				}
				String[] parts = text.split("\t");
				if(parts.length != 4) {
					LOGGER.warn("Ignoring a " + RUNTIME_ACCESS + " line that is not 'owner name desc"
							+ " access': " + text);
					continue;
				}
				try {
					result.put(parts[0] + "|" + parts[1] + "|" + parts[2], Integer.parseInt(parts[3].trim()));
				} catch(NumberFormatException e) {
					LOGGER.warn("Ignoring an access word that is not a number: " + text);
				}
			}
		} catch(IOException e) {
			LOGGER.warn("could not read " + RUNTIME_ACCESS + ": " + e);
		}
		if(!result.isEmpty()) {
			LOGGER.info("Runtime access from the plan: " + result.size() + " member(s)");
		}
		return Map.copyOf(result);
	}

	/** The plan's access word for this member, or null when it is not listed. */
	private static Integer runtimeAccessOf(String owner, String name, String desc) {
		return RUNTIME_ACCESS_BY_MEMBER.get(owner + "|" + name + "|" + desc);
	}

	/**
	 * The interfaces each swapped class must implement, taken from the runtime's copy of it rather than
	 * from the class this transformer is handed.
	 *
	 * <p>One line per interface, {@code owner<TAB>interface}, both internal names; {@code #} comments and
	 * blank lines are ignored. Written by {@code PayloadDrift}, which reads it straight out of the runtime
	 * jar. See the injection site in {@link #transform} for why the class handed over is not a safe source
	 * for this list - it is the measured reason a class the runtime's own code casts to an extension
	 * interface ended up not implementing it.</p>
	 */
	private static final String RUNTIME_INTERFACES = "/optifineoforge/runtime-interfaces.txt";

	/** {@code owner} to the runtime interfaces that must be present on it. */
	private static final Map<String, List<String>> RUNTIME_INTERFACES_BY_CLASS = loadRuntimeInterfaces();

	private static Map<String, List<String>> loadRuntimeInterfaces() {
		Map<String, List<String>> result = new LinkedHashMap<>();
		try(InputStream stream = PatchedClassTransformer.class.getResourceAsStream(RUNTIME_INTERFACES)) {
			if(stream == null) {
				return Map.of();
			}
			for(String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				String text = line.trim();
				if(text.isEmpty() || text.startsWith("#")) {
					continue;
				}
				String[] parts = text.split("\t");
				if(parts.length != 2) {
					LOGGER.warn("Ignoring a " + RUNTIME_INTERFACES + " line that is not 'owner interface': "
							+ text);
					continue;
				}
				result.computeIfAbsent(parts[0].trim(), key -> new ArrayList<>()).add(parts[1].trim());
			}
		} catch(IOException e) {
			LOGGER.warn("could not read " + RUNTIME_INTERFACES + ": " + e);
		}
		int count = result.values().stream().mapToInt(List::size).sum();
		if(count > 0) {
			LOGGER.info("Runtime interfaces from the plan: " + count + " across " + result.size()
					+ " class(es)");
		}
		return Map.copyOf(result);
	}

	/**
	 * Members whose bodies must stay the game's own, even though OptiFine's copy declares them too.
	 *
	 * <p>The mirror of the stub list. Some of OptiFine's methods assume a partner it does not ship: its
	 * {@code ModelPart.getChild(String)} looks each child up by {@code child.getId()} - an id held in a
	 * field OptiFine added - and that id is only ever set by OptiFine's own compilation of the baking code.
	 * OptiFine patches no {@code PartDefinition} at all, so on this line the runtime bakes the parts, no ids
	 * are ever set, every lookup returns null, and the first model that asks for a child dies with
	 * "this.head is null" while {@code minecraft:skull} is being built. Keeping the game's plain
	 * {@code children.get(name)} is the repair, and it is enough: the callers only want the child.</p>
	 *
	 * <p>Two line forms, tab separated: {@code owner name desc} keeps one method's body,
	 * {@code owner *} keeps the whole class. Lines starting with {@code #} are comments. An owner is an
	 * internal name, with slashes. The file is optional and absent on every line with nothing to say.</p>
	 */
	private static final String KEEP_RUNTIME = "/optifineoforge/keep-runtime.txt";

	/** Two forms of the same decision: {@code owner|name|desc} per member, and whole classes by name. */
	private record KeepPlan(Set<String> members, Set<String> classes) {
	}

	private static final KeepPlan KEEP_PLAN = loadKeepRuntime();

	/** {@code owner|name|desc} for each member that keeps the game's body. */
	private static final Set<String> KEEP_RUNTIME_MEMBERS = KEEP_PLAN.members();

	/**
	 * Classes the plan keeps whole: the runtime's copy is installed and the payload's is dropped.
	 *
	 * <p>The member form above repairs one body. This form exists for a class where no member-level repair
	 * can work, and the mechanism that puts it out of reach is a constant <em>inlined</em> into the
	 * payload's bytecode. Measured on 1.21.8, {@code ModelDiscovery$ModelWrapper}: the payload carries
	 * {@code private static final int SLOT_COUNT = 7} and the runtime 8, because NeoForge adds an eighth
	 * slot to the seven vanilla has ({@code KEY_ADDITIONAL_PROPERTIES}, read by
	 * {@code getTopAdditionalProperties()}). {@code SLOT_COUNT} is inlined into {@code slot(int)}, which
	 * guards with {@code Objects.checkIndex(index, SLOT_COUNT)}, so the restored field's own initialiser -
	 * the plan restores {@code KEY_ADDITIONAL_PROPERTIES = slot(7)}, inlined into the class's static
	 * initialiser - dies inside the class it was restored into:</p>
	 *
	 * <pre>ExceptionInInitializerError
	 *   at ModelDiscovery$ModelWrapper.slot(ModelDiscovery.java:212)   &lt;- the payload's, bound 7
	 *   at ModelDiscovery$ModelWrapper.&lt;clinit&gt;(ModelDiscovery.java:200)</pre>
	 *
	 * <p>and the same 7 sizes the class's {@code AtomicReferenceArray fixedSlots}, so the restored slot has
	 * nowhere to live even once the guard is repaired. The two copies cannot be reconciled member by member;
	 * the runtime's class is the one NeoForge's own callers are compiled against, so it is the one that is
	 * loaded. What the payload adds to it - {@code context} and {@code getContext()} - is dropped with it,
	 * and that is measured to cost nothing: a scan of all 2578 payload classes found that pair referenced
	 * only inside that class, while the runtime reaches the same behaviour through
	 * {@code UnbakedGeometryExtension.bake(..., ContextMap)}, which is the route the rest of the game calls.
	 * The keep plan is written by {@code PayloadDrift}, which reports this disagreement offline.</p>
	 */
	private static final Set<String> KEEP_RUNTIME_CLASSES = KEEP_PLAN.classes();

	private static KeepPlan loadKeepRuntime() {
		Set<String> members = new HashSet<>();
		Set<String> classes = new HashSet<>();
		try(InputStream stream = PatchedClassTransformer.class.getResourceAsStream(KEEP_RUNTIME)) {
			if(stream == null) {
				return new KeepPlan(Set.of(), Set.of());
			}
			for(String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				String text = line.trim();
				if(text.isEmpty() || text.startsWith("#")) {
					continue;
				}
				String[] parts = text.split("\t");
				if(parts.length == 3) {
					members.add(parts[0] + "|" + parts[1] + "|" + parts[2]);
				} else if(parts.length == 2 && "*".equals(parts[1].trim())) {
					classes.add(parts[0].trim());
				} else {
					LOGGER.warn("Ignoring a " + KEEP_RUNTIME + " line that is neither 'owner name desc' nor"
							+ " 'owner *': " + text);
				}
			}
		} catch(IOException e) {
			LOGGER.warn("could not read " + KEEP_RUNTIME + ": " + e);
		}
		if(!members.isEmpty()) {
			LOGGER.info("Members keeping the game's body: " + members.size());
		}
		if(!classes.isEmpty()) {
			LOGGER.info("Classes keeping the game's copy whole: " + classes.size());
		}
		return new KeepPlan(Set.copyOf(members), Set.copyOf(classes));
	}

	/** Puts the game's own version of the listed members back over the swapped-in ones. */
	private static void keepRuntimeBodies(ClassNode input, List<MethodNode> originals) {
		if(KEEP_RUNTIME_MEMBERS.isEmpty()) {
			return;
		}
		for(MethodNode original : originals) {
			if(!KEEP_RUNTIME_MEMBERS.contains(input.name + "|" + original.name + "|" + original.desc)) {
				continue;
			}
			for(int index = 0; index < input.methods.size(); index++) {
				MethodNode candidate = input.methods.get(index);
				if(candidate.name.equals(original.name) && candidate.desc.equals(original.desc)) {
					input.methods.set(index, original);
					LOGGER.info("Kept the game's body of " + input.name.replace('/', '.') + "."
							+ original.name + original.desc);
					break;
				}
			}
		}
	}

	/** Visibility of each declared method, keyed by name and descriptor, for the wider-of-two rule. */
	private static Map<String, Integer> visibilityOf(List<MethodNode> methods) {
		Map<String, Integer> result = new LinkedHashMap<>();
		for(MethodNode method : methods) {
			result.putIfAbsent(method.name + method.desc, method.access);
		}
		return result;
	}

	private static Map<String, Integer> visibilityOfFields(List<FieldNode> fields) {
		Map<String, Integer> result = new LinkedHashMap<>();
		for(FieldNode field : fields) {
			result.putIfAbsent(field.name + field.desc, field.access);
		}
		return result;
	}

	/** {@code access}, with its visibility replaced by the wider of its own and {@code other}'s. */
	private static int wider(int access, Integer other) {
		if(other == null) {
			return access;
		}
		int mine = rank(access);
		int theirs = rank(other);
		if(theirs <= mine) {
			return access;
		}
		return (access & ~VISIBILITY) | (other & VISIBILITY);
	}

	/** public 3, protected 2, package 1, private 0 - what "wider" means for the merge above. */
	private static int rank(int access) {
		if((access & Opcodes.ACC_PUBLIC) != 0) {
			return 3;
		}
		if((access & Opcodes.ACC_PROTECTED) != 0) {
			return 2;
		}
		return (access & Opcodes.ACC_PRIVATE) != 0 ? 0 : 1;
	}

	/**
	 * Prints the real module graph once, the first time a class is transformed.
	 *
	 * <p>Added because the access failure was being reasoned about backwards, from an error message:
	 * {@code IllegalAccessError: class net.optifine.config.SliderableValueSetInt cannot access its
	 * superinterface net.minecraft.client.OptionInstance$SliderableValueSet (鈥?is in module srg 鈥? 鈥?is
	 * in module minecraft@1.20.4 鈥?}. Reading the graph says directly whether the game's package is not
	 * exported to us, or whether we cannot read the game module at all - two different repairs.</p>
	 *
	 * <p>Only module metadata is touched: no game class is loaded, which would be a bad thing to do from
	 * inside a transformer.</p>
	 */
	private static void logModulesOnce() {
		if(loggedModules) {
			return;
		}
		loggedModules = true;
		try {
			Module ours = PatchedClassTransformer.class.getModule();
			IModuleLayerManager layers = OptifiNeoforgeTransformationService.layers();
			LOGGER.info("module graph: our module is " + ours.getName() + ", layer manager "
					+ (layers == null ? "not captured" : "captured"));
			if(layers == null) {
				return;
			}
			layers.getLayer(IModuleLayerManager.Layer.GAME).ifPresent(layer -> {
				StringBuilder names = new StringBuilder();
				for(Module module : layer.modules()) {
					names.append(module.getName()).append(' ');
				}
				LOGGER.info("module graph: GAME layer holds " + names.toString().trim());
				// Which layer holds us, and which holds the payload, is the question the Reflector
				// defect turns on: a module name can exist in more than one layer, and the layer whose
				// reads matter is the one that owns the class doing the reflecting. ModLauncher 11 has
				// four layers (BOOT / SERVICE / PLUGIN / GAME) and mod files live in PLUGIN.
				for(IModuleLayerManager.Layer which : IModuleLayerManager.Layer.values()) {
					layers.getLayer(which).ifPresent(other -> {
						StringBuilder otherNames = new StringBuilder();
						for(Module module : other.modules()) {
							otherNames.append(module.getName())
									.append(module.getPackages().contains("net.optifine.reflect") ? "(PAYLOAD)" : "")
									.append(' ');
						}
						LOGGER.info("module graph: " + which + " layer holds " + otherNames.toString().trim());
					});
				}
				layer.findModule("minecraft").ifPresent(game -> {
					// Both edges matter and they are granted separately, which is the whole reason this
					// probe exists: an export to a module that cannot read the game is worth nothing, and
					// the first measurement showed exactly that shape - the export went to 'optifine'
					// while the payload actually lives in 'srg'.
					for(String candidate : new String[] {"optifine", "srg", "neoforge"}) {
						layer.findModule(candidate).ifPresent(module -> {
							LOGGER.info(
								"module graph: " + candidate + " -> minecraft: reads it = " + module.canRead(game)
										+ ", net.minecraft.client is exported to it = "
										+ game.isExported("net.minecraft.client", module)
										+ ", net.minecraft.resources is exported to it = "
										+ game.isExported("net.minecraft.resources", module));
							// The Reflector defect, measured instead of guessed. OptiFine's Reflector
							// reflects method signatures and the JVM then has to load the types it finds
							// - and four of those loads fail with ClassNotFoundException even though the
							// classes plainly exist, which is what these four packages are: the exact
							// ones named by the failing reflectors on this family of lines
							// (BlockState, PoseStack, BlockEntityWithoutLevelRenderer, ItemStack).
							// Metadata only: nothing is loaded, so this cannot itself change the run.
							for(String pkg : new String[] {"net.minecraft.world.level.block.state",
									"com.mojang.blaze3d.vertex", "net.minecraft.client.renderer",
									"net.minecraft.world.item", "net.optifine.reflect"}) {
								LOGGER.info("module graph: package " + pkg + " : owned by minecraft = "
										+ game.getPackages().contains(pkg)
										+ ", exported to " + candidate + " = " + game.isExported(pkg, module)
										+ ", owned by " + candidate + " = " + module.getPackages().contains(pkg));
							}
						});
					}
				});
			});
		} catch(Throwable t) {
			LOGGER.warn("module probe failed: " + t);
		}
	}


	@Override
	public Set<Target<ClassNode>> targets() {
		Set<Target<ClassNode>> result = new java.util.LinkedHashSet<>();
		for(String name : TARGETS) {
			result.add(Target.targetClass(name));
		}
		return result;
	}

	@Override
	public TransformerVoteResult castVote(ITransformerVotingContext context) {
		// Unconditional, as on the 1.20.x line: the class is corrected because this line needs it
		// corrected, not because of what another mod did to it.
		return TransformerVoteResult.YES;
	}

	@Override
	public TargetType<ClassNode> getTargetType() {
		return TargetType.CLASS;
	}
}

