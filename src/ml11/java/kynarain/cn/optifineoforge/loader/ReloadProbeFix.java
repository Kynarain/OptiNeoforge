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

	@Override
	public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
		for(MethodNode method : input.methods) {
			if(CREATE_RELOAD.equals(method.name)) {
				probeCreateReload(input, method);
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
		return Set.of(Target.targetClass(RELOADABLE));
	}

	@Override
	public TargetType<ClassNode> getTargetType() {
		return TargetType.CLASS;
	}
}
