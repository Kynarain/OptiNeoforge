/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import cpw.mods.modlauncher.api.IModuleLayerManager;

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
public final class PatchedClassTransformer implements NodeTransformer {
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
	 * The interfaces each swapped class has to carry, from {@code optifineoforge/runtime-interfaces.txt}:
	 * one line per interface, {@code owner<TAB>interface}, both internal names, with {@code #} comments and
	 * blank lines ignored. Written by the build's {@code PayloadDrift} step read straight out of the runtime
	 * jar.
	 *
	 * <p>This is a port from the 1.21.x loader, and the port <em>is</em> the repair on this line: the 1.20.2
	 * jar has carried this plan since it was built - 15 entries, {@code BlockState} among them - and this
	 * loader never read it, so it was never applied. NeoForge adds members to a game class by having the
	 * <em>runtime</em> weave an extension interface into it, so those members exist only as inherited
	 * interface methods and sit in no jar the offline tools could copy them out of. Replacing the class with
	 * OptiFine's compilation therefore drops all of them at once, and the loss stays invisible until the
	 * game calls one:</p>
	 *
	 * <pre>java.lang.NoSuchMethodError: 'boolean net.minecraft.world.level.block.state.BlockState.canSustainPlant(
	 *   net.minecraft.world.level.BlockGetter, net.minecraft.core.BlockPos, net.minecraft.core.Direction,
	 *   net.neoforged.neoforge.common.IPlantable)'
	 *   at net.minecraft.world.level.block.BushBlock.canSurvive(BushBlock.java:34)
	 *   at net.minecraft.world.level.levelgen.blockpredicates.WouldSurvivePredicate.test  (world generation)</pre>
	 *
	 * <p>Measured on 1.20.2 / NeoForge 20.2.88: "Exception initializing level" in
	 * {@code crash-2026-09-20_06.57.39-server.txt}, thrown the moment a world was created, while the run's
	 * own Reflector lines named the same missing members from the other side
	 * ({@code IForgeBlockState.getLightEmission}, {@code getSoundType}, {@code getStateAtViewpoint},
	 * {@code shouldDisplayFluidOverlay}) and the log carried no {@code Injected ... runtime interface(s)}
	 * line at all. Putting the interfaces back also restores what NeoForge's own callers were compiled
	 * against: its code casts a game class to its extension interface, so a class without it fails there
	 * too.</p>
	 */
	private static final String RUNTIME_INTERFACES = "/optifineoforge/runtime-interfaces.txt";

	/** {@code owner} to the runtime interfaces that must be present on it. */
	private static final Map<String, List<String>> RUNTIME_INTERFACES_BY_CLASS = loadRuntimeInterfaces();

	private static Map<String, List<String>> loadRuntimeInterfaces() {
		Map<String, List<String>> result = new LinkedHashMap<>();
		try(InputStream stream = PatchedClassTransformer.class.getResourceAsStream(RUNTIME_INTERFACES)) {
			if(stream == null) {
				// Absent is not a failure: the plan is written per line, and a line with no payload copy of a
				// runtime-patched class has nothing for it to say.
				LOGGER.info("No " + RUNTIME_INTERFACES + " in this jar; no interface is added to a class");
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

	/** Null-safe class-name comparison: a class with no superclass equals only another such class. */
	private static boolean sameName(String left, String right) {
		return left == null ? right == null : left.equals(right);
	}

	/**
	 * Rewrites the payload's superclass onto the runtime's, when the build planned that for this class,
	 * and returns {@code null} on success or the reason it must not be done.
	 *
	 * <p>Three things have to hold, and each one is checked rather than assumed. The payload's superclass
	 * has to be one of the Forge types this jar supplies - a shim, with no state and no superclass of its
	 * own - because only then is discarding it lossless. The class has to be on the build's plan, which is
	 * where the runtime superclass was inspected: it must have a constructor a subclass may chain to,
	 * which is either the payload's own call or a no-argument one, and the loader cannot look at a game
	 * class to find that out. And the class body must not name the old superclass anywhere except in
	 * those constructor calls: a {@code super.something()} call site names its owner, and once the class
	 * no longer extends that type the call cannot resolve.</p>
	 *
	 * <p>The constructor descriptor travels in the plan for a measured reason: 1.20.4's {@code
	 * AttachmentHolder} takes no arguments so the payload's argument is dropped, while 1.20.2 has no
	 * {@code AttachmentHolder} at all and its {@code net.neoforged.neoforge.common.capabilities.
	 * CapabilityProvider} is constructed with the class the payload passes anyway - there the call keeps
	 * its argument and only the owner changes.</p>
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
		// from the same build: the runtime's class has to be the one the plan was measured against.
		if(!plannedSuper.equals(runtime.superName)) {
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
	 * Why the payload's copy of a class must not be installed, or {@code null} when it may be.
	 *
	 * <p>The question is not whether the two copies name the same superclass but whether the payload's
	 * hierarchy, as it will really be loaded, still reaches the type the runtime's callers were compiled
	 * against. Keeping {@code java.lang.Object} needs nothing: every class reaches it. Anything else has
	 * to be found on the payload's own chain, and a Forge API type on that chain is supplied by this jar
	 * as a shim - {@code /net/minecraftforge/...} below - whose own superclass the plan re-parents, so
	 * the walk sees the type the game will actually see.
	 *
	 * <p>The walk is deliberately limited to this jar's resources. Asking a class loader for a game class
	 * from inside a transformer is the one thing that reliably produces an unusable failure, and this
	 * jar's module cannot read the game layer anyway.
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

	/**
	 * The first place a class still names a type, as a description, or null when it names it nowhere.
	 *
	 * <p>Instruction operands are searched because a member reference names its owner and a type
	 * instruction names a class; strings are deliberately not, since OptiFine has string constants
	 * holding class names that it looks up itself, and those are not affected by a rewrite of the
	 * hierarchy.</p>
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
			}
		}
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
				// 1.20.1 has no plan at all, for the measured reason that its payload needs none: the
				// Forge package on that line is the runtime's own, so the hierarchies already agree.
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
				method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
				method.instructions.add(new InsnNode(Opcodes.ARETURN));
			}
		}
		return method;
	}

	/** The three access bits that say who may use a class; everything else in the word is not visibility. */
	private static final int VISIBILITY = Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED;

	private static final Set<String> TARGETS = loadTargets();

	private static Set<String> loadTargets() {
		Set<String> targets = new HashSet<>();
		try(InputStream stream = PatchedClassTransformer.class.getResourceAsStream(INDEX)) {
			if(stream == null) {
				// Lines where OptiFine already speaks the runtime's names need none of this, and the
				// index is simply absent there.
				LOGGER.info("No " + INDEX + " in this jar; no classes will be swapped in");
				return Set.of();
			}
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
		} catch(IOException e) {
			LOGGER.warn("could not read " + INDEX + ": " + e);
		}
		// The plan's owners are targets even when no payload replaces them, and this is the half of the port
		// that makes the injection above reach the class at all: ModLauncher calls a transformer only for the
		// classes it declares, so a class that is in no payload is never offered here and its interfaces
		// could not be added however early the call sits. Measured on this line, by intersecting the jar's
		// own two files: all 15 owners in runtime-interfaces.txt are also in patched-index.txt, so this adds
		// nothing to the 1.20.2 target count today - it is here because that agreement is a property of the
		// payload rather than of the plan, and the 1.21.x line needed it for real (there the stub file names
		// a class that is in no payload at all).
		addFirstField(targets, RUNTIME_INTERFACES);
		// A traced class has to be a target even when there is no payload for it, because this transformer is
		// only called for its targets. Measured on 1.20.4: net.minecraft.client.Minecraft is not in the index,
		// so -Doptifineoforge.traceScreen=true produced no output at all and the tracer looked broken.
		//
		// The properties are read here rather than through the constants below on purpose: TARGETS is
		// initialised before those constants, so reading them would still see their default values and the
		// widening would silently do nothing - which is what the first version of this did (the target count
		// stayed at 427).
		if(Boolean.getBoolean("optifineoforge.traceScreen")) {
			targets.add("net.minecraft.client.Minecraft");
		}
		if(Boolean.getBoolean("optifineoforge.traceCrash")) {
			targets.add("net.minecraft.CrashReport");
		}
		String traced = System.getProperty("optifineoforge.traceInit");
		if(traced != null && !"*".equals(traced.trim())) {
			for(String candidate : traced.split(",")) {
				if(!candidate.isBlank()) {
					targets.add(candidate.trim().replace('/', '.'));
				}
			}
		}
		LOGGER.info("Patched-class targets: " + targets.size());
		return Set.copyOf(targets);
	}

	/**
	 * Adds the owner column of a tab separated plan file, dotted, to {@code targets}.
	 *
	 * <p>Reads the resource rather than a loaded plan, and that is deliberate: {@code TARGETS} is
	 * initialised before the plan constants declared below it, so going through one of those would see its
	 * default value instead of the file - the mistake the tracing properties in {@link #loadTargets} carry a
	 * note about, where it silently left the count at 427.</p>
	 */
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
	 * {@code -Doptifineoforge.skipPayload=true} delivers no patched class at all, which exists to bisect a
	 * failure between "the payload was swapped in" and everything else the loader does. It is needed
	 * because a crash whose crash report is itself fatal hides which half is at fault: measured on 1.20.4,
	 * the client dies inside {@code Minecraft}'s constructor and OptiFine's own {@code CrashReporter} then
	 * dies reading {@code Minecraft.getInstance().gameDirectory}, so the original throwable is never
	 * printed. The stubs still run - they are what unswapped runtime classes need - but no class is
	 * replaced by OptiFine's copy.
	 */
	private static final boolean SKIP_PAYLOAD = Boolean.getBoolean("optifineoforge.skipPayload");

	/**
	 * {@code -Doptifineoforge.traceInit=<internal name>[,<internal name>...]} prints a stack trace at the
	 * start of that class's static initialiser, and at the start of every method of it that calls
	 * {@code net/optifine/Config}. It answers "who touches OptiFine's Config, and how early" without a
	 * debugger in the loop, which is what the 1.20.4 failure needed: Config is initialised during
	 * Minecraft's constructor, its own static initialiser pulls in net.optifine.shaders.Shaders, and Shaders
	 * cannot be initialised before the game instance exists. The trace goes through System.err, which the
	 * game redirects into its own log.
	 *
	 * <p>Only classes this transformer delivers can be traced. Measured on 1.20.4: OptiFine's own classes do
	 * not come through here at all - naming {@code net/optifine/Config} produces no trace and no "Replaced"
	 * line - so the traced side has to be a game class.</p>
	 */
	private static final String TRACE_INIT = System.getProperty("optifineoforge.traceInit");

	/**
	 * {@code -Doptifineoforge.traceCrash=true} prints the throwable that starts a crash report, before
	 * anything else touches it. It exists because on 1.20.4 the report never materialises: OptiFine's own
	 * crash callback (CrashReporter.extendCrashReport) reaches net.optifine.shaders.Shaders, whose
	 * {@code <clinit>} reads {@code Minecraft.getInstance().gameDirectory} - null for a crash during
	 * startup - so the report dies and the original throwable is never printed anywhere. Injecting a
	 * {@code printStackTrace()} into {@code CrashReport.forThrowable} recovers it, since every crash goes
	 * through that factory.
	 */
	private static final boolean TRACE_CRASH = Boolean.getBoolean("optifineoforge.traceCrash");

	private static final String CRASH_REPORT = "net/minecraft/CrashReport";

	/**
	 * {@code -Doptifineoforge.traceScreen=true} prints every screen the client switches to, by injecting a
	 * line at the start of {@code Minecraft.setScreen(Screen)}. It exists because a screenshot cannot settle
	 * "which screen is the client on": measured on 1.20.4, two captures 70 seconds apart both looked like the
	 * Mojang loading overlay (mean 233,73,8x, one colour at 84%) while the game's own log showed texture
	 * atlases being built and the Realms availability check that the title screen starts - and the render
	 * thread was alive in its frame loop, so it was not a reload barrier. Printing the screen name is the
	 * measurement that does not depend on what a window happens to have painted.
	 */
	private static final boolean TRACE_SCREEN = Boolean.getBoolean("optifineoforge.traceScreen");

	/**
	 * {@code -Doptifineoforge.strictInterfaceKeep=true} restores the older, blunter rule: never install a
	 * payload copy of an interface the runtime adds members to, whatever kind the payload's copy is.
	 *
	 * <p>The refusal is now limited to the case that actually breaks the class format - a payload
	 * <em>class</em> replacing a runtime <em>interface</em>. Measured on 1.20.4, the blunt form left
	 * {@code net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader} as a copy carrying SRG member
	 * names (its dump showed f_260482_ / m_292996_ / m_294584_), while the payload's copy of the same class is
	 * an interface with the same shape in the runtime's own names - so a caller delivered with official names
	 * could not resolve {@code create(Collection)}.</p>
	 */
	private static final boolean STRICT_INTERFACE_KEEP = Boolean.getBoolean("optifineoforge.strictInterfaceKeep");

	@Override
	public ClassNode transform(ClassNode input) {
		logModulesOnce();
		// Stubs first and unconditionally, because some belong to runtime classes that are never swapped -
		// the case that used to fall through the gap. OptiFine's GameRenderer calls
		// LoadingOverlay.update(), a method only OptiFine's own LoadingOverlay declares, so leaving the
		// overlay unswapped to avoid its clash with NeoForge's loading screen broke the pair:
		//   NoSuchMethodError: 'void net.minecraft.client.gui.screens.LoadingOverlay.update()'
		// Giving the runtime's overlay that one method keeps both halves working.
		stubMissing(input);
		// And the runtime interfaces, before anything else here decides what this class will be. The
		// placement is the one ordering requirement of the port and it is not cosmetic: every path below
		// hands back the class the game will really use, and three of them - skipPayload, a whole-class keep,
		// and a class with no payload copy - return without ever reaching the swap. A class the runtime wove
		// an interface into is just as broken when this transformer passes it through untouched as when it
		// replaces it, because in both cases the interface is not there; the 1.21.x loader puts the call in
		// exactly this spot for the same measured reason, and the reason the class handed over is not a
		// usable source of the list holds here too: OptiFine's transformation service registers before this
		// one and its own compilation of the class never heard of NeoForge's extension interfaces, so a
		// class arriving here has lost them either way.
		injectPlannedInterfaces(input);
		// The tracers also run here, before the payload lookup, because the classes most worth tracing are
		// often ones OptiFine does not patch at all: measured on 1.20.4, net.minecraft.client.Minecraft has no
		// payload copy, so a tracer called only from the swap path never ran and the screen switch it was
		// supposed to report produced no output at all.
		traceInit(input);
		traceCrash(input);
		traceScreen(input);
		if(SKIP_PAYLOAD) {
			return finish(input);
		}
		if(KEEP_RUNTIME_CLASSES.contains(input.name)) {
			// The whole class stays the runtime's, which is the only form of the keep plan that can express
			// "OptiFine's copy of this class must not be delivered at all". Needed for a class whose patched
			// copy does something the runtime cannot survive, where keeping a member body or deleting a
			// member is not enough because the offending code is the class's own static initialiser.
			// Measured on 1.20.4: OptiFine's GlDebug.<clinit> calls its own makeIgnoredErrors, that method
			// reads net.optifine/Config, Config's static initialiser pulls in net.optifine.shaders.Shaders,
			// and Shaders.<clinit> reads Minecraft.getInstance().gameDirectory - which is null while the
			// crash report that triggered it is being written, so the report never appears.
			int carried = carryPayloadOnlyMembers(input);
			LOGGER.info("Kept the runtime's whole " + input.name.replace('/', '.')
					+ " instead of OptiFine's patched copy"
					+ (carried > 0 ? ", plus " + carried + " member(s) only the payload declares" : ""));
			return finish(input);
		}
		ClassNode patched;
		try(InputStream stream = PatchedClassTransformer.class.getResourceAsStream(PREFIX + input.name + ".class")) {
			if(stream == null) {
				return finish(input);
			}
			patched = new ClassNode();
			new ClassReader(stream.readAllBytes()).accept(patched, 0);
		} catch(IOException e) {
			LOGGER.warn("could not read the patched " + input.name + ": " + e);
			return finish(input);
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
		// it. Measured on this branch, both shapes occur - 1.20.4's runtime BlockEntity extends
		// net.neoforged.neoforge.attachment.AttachmentHolder while the payload's extends
		// net.minecraftforge.common.capabilities.CapabilityProvider, and 1.20.2 has no AttachmentHolder at
		// all and extends net.neoforged.neoforge.common.capabilities.CapabilityProvider instead - and
		// either way NeoForge's own call sites need the runtime's type:
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
		// The rewrite is applied when the build planned it. When it did not, the swap goes ahead exactly
		// as this branch's verified lines have always done it, and the reason is logged rather than acted
		// on: refusing the swap here would be a change to behaviour that was verified, and the two
		// measured cases differ. 1.20.4's BlockEntity can be moved and is; 1.20.2's cannot, because its
		// runtime superclass declares serializeCaps() final while OptiFine's class overrides it -
		//
		//   IncompatibleClassChangeError: class BlockEntity overrides final method
		//     net.neoforged.neoforge.common.capabilities.CapabilityProvider.serializeCaps()
		//
		// - and 1.20.2 is verified to start with that class swapped in as it is, Forge superclass and all.
		// The plan tool refuses it, no line is written for it, and nothing here changes.
		if(!sameName(patched.superName, input.superName)) {
			String problem = reparent(patched, input);
			if(problem == null) {
				problem = hierarchyProblem(patched.superName, input.superName);
			}
			if(problem != null) {
				LOGGER.info("Swapping " + input.name.replace('/', '.') + " with its own hierarchy: " + problem);
			}
		}

		// An interface that the runtime adds members to is left as the runtime has it, and this is a
		// measured rule rather than caution. An interface carries no constructor, so the only place a
		// static field of it can be assigned is its static initialiser - and installing OptiFine's copy
		// replaces that initialiser with one that knows nothing about NeoForge's fields. The plan
		// restores the declarations so that the members exist, but the donor of an interface carries no
		// initialiser to restore the values with (the transformer refuses to call one in an interface),
		// and what is left is a field the runtime would have filled and that nothing fills here.
		//
		// There is also a blunt form of the same failure, which is how this was found on the 1.21.x line
		// rather than predicted: the donors are written as plain classes, so a restored interface field
		// arrives with the class's modifiers -
		//
		//   java.lang.ClassFormatError: Illegal field modifiers in class BlockStateModel$Unbaked: 0x9
		//
		// 0x9 being public static with no final, which no interface field may be. Normalising the
		// modifiers would have made the class loadable and left it broken in the way described above, so
		// the class is not installed at all.
		if(((patched.access & Opcodes.ACC_INTERFACE) != 0 && (input.access & Opcodes.ACC_INTERFACE) == 0
				|| STRICT_INTERFACE_KEEP) && RESTORED_CLASSES.contains(patched.name)) {
			LOGGER.info("Left " + patched.name.replace('/', '.') + " alone: the runtime adds members to that "
					+ "interface, and installing OptiFine's copy would replace the static initialiser that "
					+ "fills them");
			return finish(input);
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
		// An interface added this way obliges the class to implement its abstract methods, and that is
		// why the addition is safe: an abstract method of an injected interface is implemented by the
		// runtime class OptiFine compiled instead of, so it is a member of the runtime class that the
		// payload lacks - exactly what MemberRestorePlan restores from the donor classes.
		//
		// This branch kept the union to interfaces only, on evidence that a class-level union broke its
		// verified lines: 1.20.4 reached the title screen and then failed to load BlockState and
		// ItemStack, and 1.20.2 stopped starting. That evidence was re-measured later and is confounded -
		// the BlockState/ItemStack NoClassDefFoundError is this family's known OptiFine-build Reflector
		// defect and is present in the *baseline* run of both lines, byte for byte (14,481 / 14,631
		// bytes, same three heads). So the rule is ported here from the 1.21.x line and measured again on
		// all four 1.20.x lines; the measurement decides, and docs/MATRIX.md records it.
		List<String> kept = new ArrayList<>(patched.interfaces == null ? List.<String>of() : patched.interfaces);
		int injected = 0;
		for(String name : input.interfaces) {
			if(!kept.contains(name)) {
				kept.add(name);
				injected++;
			}
		}
		input.interfaces = kept;
		if((patched.access & Opcodes.ACC_INTERFACE) == 0 && injected > 0) {
			LOGGER.info("Kept the runtime's " + injected + " interface(s) on " + input.name.replace('/', '.')
					+ ": " + (patched.interfaces == null ? 0 : patched.interfaces.size()) + " from the payload, "
					+ kept.size() + " in place");
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
		input.fields = fields;
		input.methods = methods;
		// The planned runtime stubs have to be applied a second time here. decide() calls stubMissing() before it
		// reads the payload, because some stub owners are classes that are never swapped - but for a class that is
		// swapped, the assignment above replaces the runtime's whole member list with the payload's, and the stub
		// added a moment earlier goes with it. stubMissing() adds only what is absent, so this is idempotent.
		//
		// Measured on the sibling branch, where the same shape cost three lines their launch: with the repair plan
		// reparenting net/minecraft/world/level/block/entity/BlockEntity onto
		// net/neoforged/neoforge/attachment/AttachmentHolder, the payload's BlockEntity.<init> calls
		// this.gatherCapabilities() - a member of the Forge superclass the reparent drops - the member was in the
		// stub list the loader received (verified with javap inside the shipped jar), the first stubMissing() did
		// add it, and this assignment threw it away, so the client died at "Initializing game" with
		//   NoSuchMethodError: 'void net.minecraft.world.level.block.entity.BlockEntity.gatherCapabilities()'
		// at BlockEntity.<init>. After the second call the three lines pass all four acceptance checks again, and
		// OptiFine reaches 377/232/225 log lines instead of 28/27/27 (they had been dying one second in).
		stubMissing(input);
		keepRuntimeBodies(input, originalMethods);
		dropMembers(input);
		applyAccessPlan(input);
		traceInit(input);
		traceCrash(input);
		traceScreen(input);
		LOGGER.info("Replaced " + input.name.replace('/', '.') + " with OptiFine's patched version ("
				+ fields.size() + " fields, " + methods.size() + " methods)");
		return finish(input);
	}

	/**
	 * {@code -Doptifineoforge.dump=<dir>} writes every class this transformer delivers, after all of its
	 * passes, into that directory as {@code <internal name>.class}.
	 *
	 * <p>It answers questions about the bytes that are actually delivered rather than about the jar they came
	 * from. Measured on 1.20.4: net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader declares
	 * {@code public static create(Collection)} in the runtime interface <em>and</em> in the payload, the
	 * caller is delivered with an InterfaceMethodref to exactly that descriptor, and the game still fails to
	 * resolve it - so the delivered class has to be read back.</p>
	 */
	private static final String DUMP_DIR = System.getProperty("optifineoforge.dump");

	private static ClassNode finish(ClassNode node) {
		if(DUMP_DIR == null) {
			return node;
		}
		try {
			java.nio.file.Path target = java.nio.file.Path.of(DUMP_DIR, node.name + ".class");
			java.nio.file.Files.createDirectories(target.getParent());
			ClassWriter writer = new ClassWriter(0);
			node.accept(writer);
			java.nio.file.Files.write(target, writer.toByteArray());
		} catch(Exception e) {
			LOGGER.warn("could not dump " + node.name + ": " + e);
		}
		return node;
	}

	/**
	 * Members this runtime widened and the payload did not, from {@code optifineoforge/runtime-access.txt} as
	 * {@code owner<TAB>name<TAB>desc<TAB>access}.
	 *
	 * <p>NeoForge widens members with its own access transformer at load time, so those widenings are in no
	 * jar the offline tools can see; PayloadDrift reads them out of {@code META-INF/accesstransformer.cfg} and
	 * the build embeds the result. Measured on 1.20.4 without it:
	 * {@code IllegalAccessError: class net.neoforged.neoforge.client.NeoForgeRenderTypes$Internal tried to
	 * access method 'net.minecraft.client.renderer.RenderType$...'} during ClientHooks.initClientHooks, from
	 * inside Minecraft's constructor - the same failure the 1.21 line hit with RenderType.create.</p>
	 */
	private static final String RUNTIME_ACCESS = "/optifineoforge/runtime-access.txt";

	/** {@code owner|name|desc} to the wider access word. */
	private static final Map<String, Integer> ACCESS_PLAN = loadAccessPlan();

	private static Map<String, Integer> loadAccessPlan() {
		Map<String, Integer> result = new HashMap<>();
		try(InputStream stream = PatchedClassTransformer.class.getResourceAsStream(RUNTIME_ACCESS)) {
			if(stream == null) {
				return Map.of();
			}
			for(String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				String[] parts = line.split("\t");
				if(parts.length == 4) {
					try {
						result.put(parts[0] + "|" + parts[1] + "|" + parts[2], Integer.decode(parts[3].trim()));
					} catch(NumberFormatException e) {
						LOGGER.warn("unreadable access in " + RUNTIME_ACCESS + ": " + line);
					}
				}
			}
		} catch(IOException e) {
			LOGGER.warn("could not read " + RUNTIME_ACCESS + ": " + e);
		}
		if(!result.isEmpty()) {
			LOGGER.info("Access plan: " + result.size() + " member(s) the runtime widened");
		}
		return Map.copyOf(result);
	}

	/** Widen the delivered members the plan names, by the same wider-of-two rule the swap itself uses. */
	private static void applyAccessPlan(ClassNode input) {
		if(ACCESS_PLAN.isEmpty()) {
			return;
		}
		int applied = 0;
		for(MethodNode method : input.methods) {
			Integer wanted = ACCESS_PLAN.get(input.name + "|" + method.name + "|" + method.desc);
			if(wanted != null) {
				int narrowed = method.access;
				method.access = wider(method.access, wanted);
				applied += method.access == narrowed ? 0 : 1;
			}
		}
		for(FieldNode field : input.fields) {
			Integer wanted = ACCESS_PLAN.get(input.name + "|" + field.name + "|" + field.desc);
			if(wanted != null) {
				int narrowed = field.access;
				field.access = wider(field.access, wanted);
				applied += field.access == narrowed ? 0 : 1;
			}
		}
		if(applied > 0) {
			LOGGER.info("Widened " + applied + " member(s) of " + input.name.replace('/', '.')
					+ " to the runtime's access");
		}
	}

	/** Prints the screen the client switches to, at the moment it switches. */
	private static void traceScreen(ClassNode input) {
		if(!TRACE_SCREEN || !"net/minecraft/client/Minecraft".equals(input.name)) {
			return;
		}
		for(MethodNode method : input.methods) {
			if(!"setScreen".equals(method.name) || !"(Lnet/minecraft/client/gui/screens/Screen;)V".equals(method.desc)) {
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
			// String.valueOf, not getClass().getName(): setScreen is called with null as a matter of
			// course. NeoForge's ClientHooks.popGuiLayer does it whenever the last GUI layer is popped,
			// which is exactly what ReceivingLevelScreen.onClose does when a quick-play world finishes
			// loading, and the unconditional getClass() then threw
			//
			//   NullPointerException: Cannot invoke "Object.getClass()" because "<parameter1>" is null
			//     at Minecraft.setScreen <- ClientHooks.popGuiLayer <- Screen.onClose
			//     <- ReceivingLevelScreen.tick
			//
			// one second AFTER a successful join - measured on 1.20.6, and it is why every tracer log of
			// this rig stops at ReceivingLevelScreen. valueOf answers "null" instead, needs no branch (so
			// no stack map frames have to be computed for the injected sequence) and keeps the class name
			// in the line for every screen that is not null.
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

	/** Prints the throwable a crash report is built from, at the moment it is built. */
	private static void traceCrash(ClassNode input) {
		if(!TRACE_CRASH || !CRASH_REPORT.equals(input.name)) {
			return;
		}
		for(MethodNode method : input.methods) {
			if(!"forThrowable".equals(method.name)
					|| !"(Ljava/lang/Throwable;Ljava/lang/String;)Lnet/minecraft/CrashReport;".equals(method.desc)) {
				continue;
			}
			InsnList trace = new InsnList();
			trace.add(new VarInsnNode(Opcodes.ALOAD, 0));
			trace.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable", "printStackTrace", "()V", false));
			method.instructions.insert(trace);
			LOGGER.info("Tracing the throwable behind every crash report");
		}
	}

	/** Puts a stack trace in front of the traced class's initialiser and of its Config call sites. */
	private static void traceInit(ClassNode input) {
		if(TRACE_INIT == null || !traced(input.name)) {
			return;
		}
		int traced = 0;
		for(MethodNode method : input.methods) {
			boolean initialiser = "<clinit>".equals(method.name);
			if(initialiser || callsOptiFineConfig(method)) {
				injectTrace(method, input.name);
				traced++;
			}
		}
		if(traced > 0) {
			LOGGER.info("Tracing " + traced + " method(s) of " + input.name.replace('/', '.')
					+ " that run during startup or call OptiFine's Config");
		}
	}

	private static boolean traced(String internalName) {
		for(String candidate : TRACE_INIT.split(",")) {
			// "*" traces every class this transformer delivers that calls Config, which is how the 1.20.4
			// question was answered after naming classes produced no trace: guessing which of the delivered
			// classes reaches Config first is what the trace is for.
			if("*".equals(candidate.trim()) || candidate.trim().equals(internalName)) {
				return true;
			}
		}
		return false;
	}

	/** Whether this method touches {@code net/optifine/Config} - by a call or by a field access. */
	private static boolean callsOptiFineConfig(MethodNode method) {
		if(method.instructions == null) {
			return false;
		}
		for(AbstractInsnNode instruction : method.instructions) {
			// Both shapes matter, and the first version of this only looked at calls: measured on 1.20.4,
			// tracing every delivered class that *calls* Config left the crash unexplained, and a static
			// field of Config is just as good a way to initialise it.
			if(instruction instanceof MethodInsnNode call && call.owner.startsWith("net/optifine/Config")) {
				return true;
			}
			if(instruction instanceof FieldInsnNode field && field.owner.startsWith("net/optifine/Config")) {
				return true;
			}
		}
		return false;
	}

	private static void injectTrace(MethodNode method, String owner) {
		InsnList trace = new InsnList();
		// A plain line first: the first version of this injected only printStackTrace, and its absence could
		// have meant either "the site never ran" or "stderr is not where this ends up". The line settles
		// which of the two it is.
		trace.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;"));
		trace.add(new LdcInsnNode("OPF-TRACE " + owner.replace('/', '.') + "." + method.name));
		trace.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println",
				"(Ljava/lang/String;)V", false));
		trace.add(new TypeInsnNode(Opcodes.NEW, "java/lang/Throwable"));
		trace.add(new InsnNode(Opcodes.DUP));
		trace.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Throwable", "<init>", "()V", false));
		trace.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable", "printStackTrace", "()V", false));
		method.instructions.insert(trace);
	}

	/**
	 * The classes the member restore plan has something to put back into, read from the same file the
	 * transformer that does the restoring reads. Only the owner of each line matters here.
	 */
	private static final String MEMBER_RESTORES = "/optifineoforge/member-restores.txt";

	private static final Set<String> RESTORED_CLASSES = loadRestoredClasses();

	private static Set<String> loadRestoredClasses() {
		Set<String> result = new HashSet<>();
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

	private static boolean loggedModules;

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
	 */
	private static final String KEEP_RUNTIME = "/optifineoforge/keep-runtime.txt";

	/** {@code owner|name|desc} for each member that keeps the game's body. */
	private static final Set<String> KEEP_RUNTIME_MEMBERS = loadKeepRuntime();

	/** Owners whose whole class stays the runtime's, from the {@code owner<TAB>*} form of the same file. */
	private static final Set<String> KEEP_RUNTIME_CLASSES = loadKeepRuntimeClasses();

	private static Set<String> loadKeepRuntimeClasses() {
		Set<String> result = new HashSet<>();
		try(InputStream stream = PatchedClassTransformer.class.getResourceAsStream(KEEP_RUNTIME)) {
			if(stream == null) {
				return Set.of();
			}
			for(String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				String[] parts = line.split("\t");
				if(parts.length == 2 && "*".equals(parts[1].trim())) {
					result.add(parts[0].trim());
				}
			}
		} catch(IOException e) {
			LOGGER.warn("could not read " + KEEP_RUNTIME + " for whole-class keeps: " + e);
		}
		if(!result.isEmpty()) {
			LOGGER.info("Classes keeping the runtime's whole version: " + result.size());
		}
		return Set.copyOf(result);
	}

	private static Set<String> loadKeepRuntime() {
		Set<String> result = new HashSet<>();
		try(InputStream stream = PatchedClassTransformer.class.getResourceAsStream(KEEP_RUNTIME)) {
			if(stream == null) {
				return Set.of();
			}
			for(String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				String[] parts = line.split("\t");
				if(parts.length == 3) {
					result.add(parts[0] + "|" + parts[1] + "|" + parts[2]);
				}
			}
		} catch(IOException e) {
			LOGGER.warn("could not read " + KEEP_RUNTIME + ": " + e);
		}
		if(!result.isEmpty()) {
			LOGGER.info("Members keeping the game's body: " + result.size());
		}
		return Set.copyOf(result);
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

	/** The prefix the member restore plan gives the helpers it generates; those are never carried over. */
	private static final String RESTORE_PREFIX = "optifineoforge$init$";

	/**
	 * Carries over the members a kept class has only in its payload copy, and reports how many.
	 *
	 * <p>Keeping a class whole is an all-or-nothing decision, and it can be one member too much. Measured on
	 * 1.20.2: {@code net.minecraft.Util} is kept because OptiFine's compilation of it does not fit the
	 * runtime's anonymous classes, but OptiFine's own classes call members only the payload's {@code Util}
	 * declares, so a kept class leaves those call sites unresolved and the game dies on the first one:</p>
	 *
	 * <pre>NoSuchMethodError: 'java.util.concurrent.ExecutorService net.minecraft.Util.getCapeExecutor()'
	 *   at net.minecraft.client.renderer.texture.HttpTexture.getExecutor(HttpTexture.java:346)
	 *   at net.optifine.player.CapeUtils.downloadCape(CapeUtils.java:71)
	 *   at net.minecraft.client.player.AbstractClientPlayer.&lt;init&gt; -&gt; LocalPlayer.&lt;init&gt;
	 *   -&gt; MultiPlayerGameMode.createPlayer -&gt; ClientPacketListener.handleLogin</pre>
	 *
	 * <p>which aborts player creation inside the login packet, so the client ticks forever with a null player
	 * (32774 {@code NullPointerException}s in 60 s, thirteen per frame) and never leaves the loading screen.
	 * The runtime never calls those members itself - only the payload's classes do - which is why carrying
	 * them over is the safe direction.</p>
	 *
	 * <p>Constructors and the static initialiser are not carried: those are exactly the shapes that disagree
	 * between the two builds (measured on 1.20.1, {@code Util$9} is a {@code Thread} in the runtime and a
	 * {@code BiFunction} in the payload). Static fields are carried with the payload's own initialisation
	 * statement for them, cut out of its static initialiser at the {@code PUTSTATIC} that ends it, so a
	 * carried {@code getCapeExecutor()} does not find a null - measured: the payload initialises
	 * {@code CAPE_EXECUTOR} as {@code makeExecutor("Cape")}, and {@code makeExecutor} is the runtime's own.</p>
	 */
	private static int carryPayloadOnlyMembers(ClassNode input) {
		ClassNode payload;
		try(InputStream stream = PatchedClassTransformer.class.getResourceAsStream(PREFIX + input.name + ".class")) {
			if(stream == null) {
				return 0;
			}
			payload = new ClassNode();
			new ClassReader(stream.readAllBytes()).accept(payload, 0);
		} catch(IOException e) {
			return 0;
		}
		if(!input.name.equals(payload.name) || payload.methods == null || payload.fields == null) {
			return 0;
		}
		int carried = 0;
		Set<String> carriedFields = new HashSet<>();
		for(FieldNode field : payload.fields) {
			if((field.access & Opcodes.ACC_FINAL) != 0 && (field.access & Opcodes.ACC_STATIC) == 0) {
				// A non-static final field may only be assigned from the class's own constructor, so carrying
				// one over without the payload's constructor produces an initialiser the JVM refuses:
				//   IllegalAccessError: Update to non-static final field net.minecraft.Util$9.cache attempted
				//   from a different method (optifineoforge$init$cache) than the initializer method <init>
				// Measured on 1.20.2, 22:22, as a client that died inside Main.main. Constructors are not
				// carried, so such a field is left alone.
				LOGGER.info("Not carrying the payload's final field " + input.name.replace('/', '.') + "."
						+ field.name + ": only the payload's own constructor may assign it, and constructors are"
						+ " not carried over");
				continue;
			}
			boolean present = false;
			for(FieldNode existing : input.fields) {
				if(existing.name.equals(field.name) && existing.desc.equals(field.desc)) {
					present = true;
					break;
				}
			}
			if(present) {
				continue;
			}
			input.fields.add(new FieldNode(field.access, field.name, field.desc, field.signature, field.value));
			if((field.access & Opcodes.ACC_STATIC) != 0) {
				carriedFields.add(field.name);
			}
			carried++;
			LOGGER.info("Carried " + input.name.replace('/', '.') + "." + field.name + " " + field.desc
					+ " from the payload into the class kept from the runtime");
		}
		int initialised = carriedFields.isEmpty() ? 0 : carryStaticInitialisers(input, payload, carriedFields);
		for(MethodNode method : payload.methods) {
			if("<init>".equals(method.name) || "<clinit>".equals(method.name)
					|| method.name.startsWith(RESTORE_PREFIX) || hasMethod(input, method.name, method.desc)) {
				// The restore plan's own generated helpers are not carried: they exist to assign fields of the
				// payload's copy of the class, and against the kept class they are exactly the shape that
				// fails - see the note on final fields above, which is the failure this exclusion was
				// measured from.
				continue;
			}
			MethodNode copy = new MethodNode(method.access, method.name, method.desc, method.signature,
					method.exceptions == null ? null : method.exceptions.toArray(new String[0]));
			method.accept(copy);
			input.methods.add(copy);
			carried++;
			LOGGER.info("Carried " + input.name.replace('/', '.') + "." + method.name + method.desc
					+ " from the payload into the class kept from the runtime");
		}
		if(initialised > 0) {
			LOGGER.info("Initialised " + initialised + " carried static field(s) in "
					+ input.name.replace('/', '.') + " from the payload's own static initialiser");
		}
		return carried;
	}

	/**
	 * Appends the payload's initialisation statements for the static fields just carried into the kept class.
	 *
	 * <p>The statement for one field is the instruction run that ends in writing it, starting after the
	 * previous write: that is the granularity a kept class can accept without importing the payload's static
	 * initialiser wholesale, which is the one thing that measurably cannot be taken from the payload (it is
	 * where the two builds' anonymous classes disagree). A run whose instructions cannot be copied is skipped
	 * rather than delivered half-written, and the field then keeps its default.</p>
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
					// and their presence is not a reason to give up. Measured the hard way: requiring a copy
					// of every node silently dropped the statement that fills Util.CAPE_EXECUTOR (the payload's
					// static initialiser carries line numbers), the field stayed null, and the cape download
					// then failed with
					//   NullPointerException at java.util.concurrent.CompletableFuture.screenExecutor
					// inside TextureManager.register from AbstractClientPlayer.<init>.
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

	/**
	 * Members a swapped-in payload class must not declare at all, read from
	 * {@code optifineoforge/drop-members.txt} as {@code owner<TAB>name<TAB>desc}.
	 *
	 * <p>This is the one repair neither of the other two plans can express. The keep plan puts the game's
	 * body back over a payload member, which needs the runtime class to have that member; the member
	 * restore plan adds members the runtime has and the payload lacks. Neither can take a member away, and
	 * a member that must not exist for the class to load at all cannot be fixed by keeping or restoring
	 * anything.</p>
	 *
	 * <p>Measured on 1.20.4, and measured outside this loader as well: OptiFine's own patch for
	 * {@code net.minecraft.client.player.AbstractClientPlayer} declares {@code getX()}, {@code getY()} and
	 * {@code getZ()} overrides, while the 1.20.4 runtime declares all three {@code final} on
	 * {@code net.minecraft.world.entity.Entity} - vanilla's obfuscated class, the NeoForge client jar and
	 * the {@code -srg} jar all agree on that. Taking the payload's copy of that class out of the jar and
	 * loading it with jshell against the runtime reproduces the game's failure exactly:
	 * {@code IncompatibleClassChangeError: class ... AbstractClientPlayer overrides final method
	 * ... Entity.getY()D}. The three methods are OptiFine's own additions (the vanilla obfuscated class
	 * declares none of them), so leaving them out is dropping OptiFine's copies of methods the JVM will not
	 * accept, and every caller resolves to the runtime's inherited one instead.</p>
	 */
	private static final String DROP_MEMBERS = "/optifineoforge/drop-members.txt";

	/** {@code owner|name|desc} for each member that is left out of the delivered class. */
	private static final Set<String> DROPPED_MEMBERS = loadDropped();

	private static Set<String> loadDropped() {
		Set<String> result = new HashSet<>();
		try(InputStream stream = PatchedClassTransformer.class.getResourceAsStream(DROP_MEMBERS)) {
			if(stream == null) {
				return Set.of();
			}
			for(String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				String[] parts = line.split("\t");
				if(parts.length == 3) {
					result.add(parts[0] + "|" + parts[1] + "|" + parts[2]);
				}
			}
		} catch(IOException e) {
			LOGGER.warn("could not read " + DROP_MEMBERS + ": " + e);
		}
		if(!result.isEmpty()) {
			LOGGER.info("Members dropped from the payload: " + result.size());
		}
		return Set.copyOf(result);
	}

	/** Removes the listed members from the class that is about to be defined. */
	private static void dropMembers(ClassNode input) {
		if(DROPPED_MEMBERS.isEmpty()) {
			return;
		}
		for(int index = input.methods.size() - 1; index >= 0; index--) {
			MethodNode method = input.methods.get(index);
			if(DROPPED_MEMBERS.contains(input.name + "|" + method.name + "|" + method.desc)) {
				input.methods.remove(index);
				LOGGER.info("Dropped " + input.name.replace('/', '.') + "." + method.name + method.desc
						+ " so the class can be defined against this runtime");
			}
		}
		for(int index = input.fields.size() - 1; index >= 0; index--) {
			FieldNode field = input.fields.get(index);
			if(DROPPED_MEMBERS.contains(input.name + "|" + field.name + "|" + field.desc)) {
				input.fields.remove(index);
				LOGGER.info("Dropped " + input.name.replace('/', '.') + "." + field.name + " " + field.desc);
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
				layer.findModule("minecraft").ifPresent(game -> {
					// Both edges matter and they are granted separately, which is the whole reason this
					// probe exists: an export to a module that cannot read the game is worth nothing, and
					// the first measurement showed exactly that shape - the export went to 'optifine'
					// while the payload actually lives in 'srg'.
					for(String candidate : new String[] {"optifine", "srg", "neoforge"}) {
						layer.findModule(candidate).ifPresent(module -> LOGGER.info(
								"module graph: " + candidate + " -> minecraft: reads it = " + module.canRead(game)
										+ ", net.minecraft.client is exported to it = "
										+ game.isExported("net.minecraft.client", module)
										+ ", net.minecraft.resources is exported to it = "
										+ game.isExported("net.minecraft.resources", module)));
					}
				});
			});
		} catch(Throwable t) {
			LOGGER.warn("module probe failed: " + t);
		}
	}


	@Override
	public Set<String> targetClasses() {
		return TARGETS;
	}
}

