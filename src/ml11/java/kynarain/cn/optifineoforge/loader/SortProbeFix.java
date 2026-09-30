/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.loader;

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
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
 * Reports what {@code ReloadListenerSort.sort} is handed: the anchor and the registry size.
 *
 * <p>The reload listener list ends up holding the vanilla listeners twice when OptiFine is present
 * and once when it is not, with the same 22-entry input and the same name lookups in both runs - so
 * the remaining difference has to be the graph the sort builds, and this is the one input to that
 * graph worth seeing: {@code getLastVanillaListener()}, the node everything mod-added gets linked
 * to, plus how many entries the registry holds.</p>
 *
 * <p>Diagnostic only, silent unless {@code -Doptifineoforge.debug.reload=true} is set.</p>
 */
public final class SortProbeFix implements ITransformer<ClassNode> {
	/** NeoForge's sorter, and the method whose arguments are worth printing. */
	static final String SORT = "net.neoforged.neoforge.resource.ReloadListenerSort";
	private static final String METHOD = "sort";
	private static final String EVENT = "net/neoforged/neoforge/event/SortedReloadListenerEvent";
	private static final String PROBE = "kynarain/cn/optifineoforge/loader/ReloadProbe";

	@Override
	public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
		if(!SORT.equals(input.name.replace('/', '.'))) {
			return input;
		}
		// Counted and reported: with OptiFine installed, two of the four insertions in this method
		// never run while the other two do, and the first thing to know is whether this transformer
		// even saw one copy of the class with all of them, or two copies with part of them.
		int probed = 0;
		for(MethodNode method : input.methods) {
			if(!METHOD.equals(method.name)) {
				continue;
			}
			InsnList report = new InsnList();
			// ReloadProbe.value(event.getLastVanillaListener(), "lastVanillaListener")
			report.add(new VarInsnNode(Opcodes.ALOAD, 0));
			report.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, EVENT, "getLastVanillaListener",
					"()Lnet/minecraft/server/packs/resources/PreparableReloadListener;", false));
			report.add(new LdcInsnNode("lastVanillaListener"));
			report.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, "value",
					"(Ljava/lang/Object;Ljava/lang/String;)V", false));
			// ReloadProbe.count(event.getRegistry(), "registry")
			report.add(new VarInsnNode(Opcodes.ALOAD, 0));
			report.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, EVENT, "getRegistry", "()Ljava/util/Map;", false));
			report.add(new LdcInsnNode("registry"));
			report.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, "count",
					"(Ljava/util/Map;Ljava/lang/String;)V", false));
			// ReloadProbe.graph(event.getGraph(), "graph before sorting")
			report.add(new VarInsnNode(Opcodes.ALOAD, 0));
			report.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, EVENT, "getGraph",
					"()Lcom/google/common/graph/Graph;", false));
			report.add(new LdcInsnNode("graph before sorting"));
			report.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, "graph",
					"(Ljava/lang/Object;Ljava/lang/String;)V", false));
			method.instructions.insert(report);
			// ReloadProbe.list(<what sort returns>, "sort result")
			for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if(insn.getOpcode() != Opcodes.ARETURN) {
					continue;
				}
				InsnList result = new InsnList();
				result.add(new InsnNode(Opcodes.DUP));
				result.add(new LdcInsnNode("sort result"));
				result.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, "list",
						"(Ljava/lang/Object;Ljava/lang/String;)V", false));
				method.instructions.insertBefore(insn, result);
				probed++;
			}
			probed += 3; // the anchor, registry and graph report
		}
		org.apache.logging.log4j.LogManager.getLogger("OptifiNeoforge")
				.info("sort probe: insertion pass on " + input.name + ", insertions=" + probed);
		return input;
	}

	@Override
	public TransformerVoteResult castVote(ITransformerVotingContext context) {
		return TransformerVoteResult.YES;
	}

	@Override
	public Set<Target<ClassNode>> targets() {
		return Set.of(Target.targetClass(SORT));
	}

	@Override
	public TargetType<ClassNode> getTargetType() {
		return TargetType.CLASS;
	}
}
