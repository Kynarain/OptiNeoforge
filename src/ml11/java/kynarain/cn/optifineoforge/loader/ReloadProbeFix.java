/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.loader;

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;

/**
 * Hands the reload listener list to {@link ReloadProbe} as the reload is created.
 *
 * <p>{@code createReload} loads {@code this.listeners} and passes it straight to
 * {@code SimpleReloadInstance.create}, so the list read there is exactly the list that will be run,
 * in that order. The probe is a duplicate left on the stack, so nothing about the reload changes.</p>
 *
 * <p>This is a diagnostic, not a repair: it is the measurement that says whether the null-model
 * failure on the first reload is an ordering problem at all. OptiFine replaces
 * {@code ReloadableResourceManager} too, so the list it uses is the one NeoForge's restored
 * {@code updateListenersFrom} was supposed to have replaced, and seeing the real order is the only
 * way to tell which of the two won.</p>
 */
public final class ReloadProbeFix implements ITransformer<ClassNode> {
	/** The class whose reload the probe watches. */
	static final String RELOADABLE = "net/minecraft/server/packs/resources/ReloadableResourceManager";
	/** The method that turns the list into a reload, and the field it reads to get it. */
	private static final String CREATE_RELOAD = "createReload";
	private static final String LISTENERS = "listeners";
	/** Where listeners enter the list, and where the list is replaced by the sorted one. */
	private static final String REGISTER = "registerReloadListener";
	private static final String UPDATE = "updateListenersFrom";
	private static final String PROBE = "kynarain/cn/optifineoforge/loader/ReloadProbe";

	/**
	 * The reload's own per-listener task, which is where a reload stops when one listener never finishes.
	 *
	 * <p>Called {@code lambda$of$0} in {@code SimpleReloadInstance}, and static with the listener as its
	 * fourth parameter - which is why the injection loads local 3. Instrumenting it is what turns "the reload
	 * never completes" into "this listener's task started and never returned", which is the difference
	 * between a diagnosis and a guess: on 1.21 the reload starts with its 28 listeners, the log's last
	 * activity is OptiFine's connected-texture parsing, and every thread is idle afterwards.</p>
	 */
	private static final String LISTENER_TASK = "lambda$of$0";

	/** {@code SimpleReloadInstance}, the class that owns the per-listener task. */
	private static final String SIMPLE_RELOAD = "net/minecraft/server/packs/resources/SimpleReloadInstance";

	/** The barrier itself: SimpleReloadInstance$1 implements PreparationBarrier, wait(T). Verified with javap. */
	private static final String BARRIER_IMPL = "net/minecraft/server/packs/resources/SimpleReloadInstance$1";

	@Override
	public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
		if(LISTENERS_TO_MARK.contains(input.name)) {
			// The delivery half of the pair: this transformer was handed the class at all. Without it an
			// absent reloadEntered line cannot be told apart from a class that never arrived here.
			ReloadProbe.saw(input.name.replace('/', '.'));
		}
		for(MethodNode method : input.methods) {
			if((RELOAD.equals(method.name) || RESOURCE_MANAGER_RELOAD.equals(method.name)
					|| PREPARE.equals(method.name))
					&& LISTENERS_TO_MARK.contains(input.name)) {
				InsnList call = new InsnList();
				call.add(new LdcInsnNode(input.name.replace('/', '.')));
				call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, "reloadEntered", "(Ljava/lang/String;)V", false));
				method.instructions.insert(call);
			} else if(CREATE_RELOAD.equals(method.name)) {
				probeCreateReload(input, method);
			} else if("wait".equals(method.name) && BARRIER_IMPL.equals(input.name)) {
				InsnList call = new InsnList();
				call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, "barrierReached", "()V", false));
				method.instructions.insert(call);
			} else if(LISTENER_TASK.equals(method.name) && SIMPLE_RELOAD.equals(input.name)) {
				probeListenerTask(method);
			} else if(REGISTER.equals(method.name)) {
				// Who is registered, and when - the list ends up holding some of them twice.
				InsnList call = new InsnList();
				call.add(new VarInsnNode(Opcodes.ALOAD, 1));
				call.add(new LdcInsnNode("registerReloadListener"));
				call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, "value",
						"(Ljava/lang/Object;Ljava/lang/String;)V", false));
				method.instructions.insert(call);
			} else if(UPDATE.equals(method.name)) {
				probeSize(input, method, "before updateListenersFrom");
				for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if(insn.getOpcode() == Opcodes.RETURN) {
						insertSize(input, method, insn, "after updateListenersFrom");
					}
				}
			}
		}
		return input;
	}

	/** Marks one listener's task as started, and as finished when it returns. */
	private static void probeListenerTask(MethodNode method) {
		// Every listener's future is watched, so the one that never completes is the one missing from the
		// completion lines - see ReloadProbe.watching for why the stack cannot answer this instead.
		for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if(insn instanceof MethodInsnNode call && RELOAD.equals(call.name) && call.desc.endsWith(")Ljava/util/concurrent/CompletableFuture;")) {
				InsnList hook = new InsnList();
				hook.add(new InsnNode(Opcodes.DUP));
				hook.add(new VarInsnNode(Opcodes.ALOAD, 3));
				hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, "watching",
						"(Ljava/util/concurrent/CompletableFuture;Ljava/lang/Object;)V", false));
				method.instructions.insert(insn, hook);
			}
		}		method.instructions.insert(taskCall("started"));
		probeBarrier(method);
		for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if(insn.getOpcode() == Opcodes.RETURN) {
				method.instructions.insertBefore(insn, taskCall("finished"));
			}
		}
	}

	/**
	 * Marks the listeners that reach the reload's barrier.
	 *
	 * <p>Vanilla hands each listener a {@code PreparationBarrier} and waits for every one of them, so a
	 * single listener that never calls it holds the whole reload open - measured on 1.21 as 28 tasks started
	 * and none finished. Listing the ones that do arrive names the one that does not, which is the whole
	 * question at that point.</p>
	 */
	private static void probeBarrier(MethodNode method) {
		for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if(insn instanceof MethodInsnNode call && BARRIER.equals(call.owner) && BARRIER_WAIT.equals(call.name)) {
				method.instructions.insert(insn, barrierCall());
			}
		}
	}

	/** {@code PreparableReloadListener$PreparationBarrier}, and the method everyone has to reach. */
	private static final String BARRIER = "net/minecraft/server/packs/resources/PreparableReloadListener$PreparationBarrier";

	private static final String BARRIER_WAIT = "wait";

	/**
	 * The classes that implement PreparableReloadListener, harvested offline.
	 *
	 * <p>A transformer has to declare its targets before any of them runs, and a listener class cannot be
	 * discovered from inside one - so the list is measured instead: a previous run's "listener task started"
	 * lines name every listener the reload uses, and the rig turns those names into this file. This is the
	 * listener side of the correlation; the barrier side has no identity to give, which two attempts
	 * established.</p>
	 */
	private static final String LISTENER_PLAN = "/optifineoforge/reload-listeners.txt";

	private static final java.util.Set<String> LISTENERS_TO_MARK = loadListenerPlan();

	private static java.util.Set<String> loadListenerPlan() {
		java.util.Set<String> result = new java.util.LinkedHashSet<>();
		try(java.io.InputStream stream = ReloadProbeFix.class.getResourceAsStream(LISTENER_PLAN)) {
			if(stream == null) {
				return java.util.Set.of();
			}
			for(String line : new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
				String text = line.trim();
				if(!text.isEmpty() && !text.startsWith("#")) {
					result.add(text);
				}
			}
		} catch(java.io.IOException e) {
			return java.util.Set.of();
		}
		return java.util.Set.copyOf(result);
	}
	/** The per-listener entry point whose future decides when the reload can move on. */
	private static final String RELOAD = "reload";

	/**
	 * The other entry point a listener may have instead of reload.
	 *
	 * <p>Most listeners implement {@code ResourceManagerReloadListener}, an interface whose default reload
	 * delegates to the implementor own onResourceManagerReload - so injecting at a method named reload finds
	 * nothing in eighteen of twenty-two planned classes, which is what the previous run measured. Both names
	 * are tried; whichever the class declares is the one that gets marked.</p>
	 */
	private static final String RESOURCE_MANAGER_RELOAD = "onResourceManagerReload";

	/**
	 * The third entry point, and the one most of this line's listeners actually have.
	 *
	 * <p>Measured: all 22 planned classes are delivered to this transformer and only 4 accept the injection,
	 * because the rest declare neither reload nor onResourceManagerReload - they extend
	 * {@code SimplePreparableReloadListener}, whose entry point is prepare, with reload left as the base
	 * class method nothing overrides. Adding this name is what should take the marking from 4 to 22.</p>
	 */
	private static final String PREPARE = "prepare";

	/**
	 * The fourth entry point, and the other half of {@code SimplePreparableReloadListener}.
	 *
	 * <p>Measured after prepare was added: the marking went from 4 to 10, and twelve listeners still
	 * declared none of reload, onResourceManagerReload or prepare. Those are the ones implementing only the
	 * apply side of SimplePreparableReloadListener, leaving prepare to the base class - the mirror of the
	 * listeners prepare already covered.</p>
	 */
	private static final String APPLY = "apply";



	private static InsnList barrierCall() {
		InsnList call = new InsnList();
		call.add(new VarInsnNode(Opcodes.ALOAD, 3));
		call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, "reachedBarrier", "(Ljava/lang/Object;)V", false));
		return call;
	}

	private static InsnList taskCall(String probeMethod) {
		InsnList call = new InsnList();
		call.add(new VarInsnNode(Opcodes.ALOAD, 3));
		call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, probeMethod, "(Ljava/lang/Object;)V", false));
		return call;
	}

	/** Hands the list the reload will actually run to the probe. */
	private static void probeCreateReload(ClassNode input, MethodNode method) {
		for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if(!(insn instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETFIELD
					|| !input.name.equals(field.owner) || !LISTENERS.equals(field.name)) {
				continue;
			}
			InsnList probe = new InsnList();
			probe.add(new InsnNode(Opcodes.DUP));
			probe.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, "listeners", "(Ljava/util/List;)V", false));
			method.instructions.insert(insn, probe);
			return;
		}
	}

	private static void probeSize(ClassNode input, MethodNode method, String label) {
		method.instructions.insert(sizeCall(input, label));
	}

	private static void insertSize(ClassNode input, MethodNode method, AbstractInsnNode before, String label) {
		method.instructions.insertBefore(before, sizeCall(input, label));
	}

	/** Reads the manager's own listener list and reports how long it is. */
	private static InsnList sizeCall(ClassNode input, String label) {
		InsnList call = new InsnList();
		call.add(new VarInsnNode(Opcodes.ALOAD, 0));
		call.add(new FieldInsnNode(Opcodes.GETFIELD, input.name, LISTENERS, "Ljava/util/List;"));
		call.add(new LdcInsnNode(label));
		call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, "size",
				"(Ljava/util/List;Ljava/lang/String;)V", false));
		return call;
	}

	@Override
	public TransformerVoteResult castVote(ITransformerVotingContext context) {
		return TransformerVoteResult.YES;
	}

	@Override
	public Set<Target<ClassNode>> targets() {
		Set<Target<ClassNode>> result = new java.util.LinkedHashSet<>();
		result.add(Target.targetClass(RELOADABLE));
		result.add(Target.targetClass(SIMPLE_RELOAD));
		result.add(Target.targetClass(BARRIER_IMPL));
		for(String owner : LISTENERS_TO_MARK) {
			// Dotted, which is the convention this repository already uses for targets - PatchedClassTransformer
			// builds its own set with name.replace('/', '.'). The plan carries internal names, and passing
			// them through unchanged is why only four of twenty-two planned listeners were ever marked.
			result.add(Target.targetClass(owner.replace('/', '.')));
		}
		return result;
	}

	@Override
	public TargetType<ClassNode> getTargetType() {
		return TargetType.CLASS;
	}
}
