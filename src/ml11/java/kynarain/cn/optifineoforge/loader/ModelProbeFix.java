/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.loader;

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
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
 * Marks the start of the model bake and of the render dispatcher's reload, so their order is visible.
 *
 * <p>{@link ReloadProbeFix} shows the listener list, which is the order a reload is meant to run in.
 * This shows what actually happened: whether {@code ModelManager.apply} - the only place either
 * class sets {@code missingModel} - finished before {@code BlockRenderDispatcher} asked for a baked
 * model. When a reloaded dispatcher gets a null model, the answer is one of two things, and the log
 * now tells them apart: apply had not run yet, or apply ran and produced nothing.</p>
 */
public final class ModelProbeFix implements ITransformer<ClassNode> {
	/** The class that bakes, and the method that fills in the models. */
	static final String MODEL_MANAGER = "net.minecraft.client.resources.model.ModelManager";
	private static final String APPLY = "apply";
	/** The class that asks for them while it reloads. */
	static final String BLOCK_RENDER_DISPATCHER = "net.minecraft.client.renderer.block.BlockRenderDispatcher";
	private static final String ON_RELOAD = "onResourceManagerReload";
	/** The class that does the baking, and the method that hands the models over. */
	static final String MODEL_BAKERY = "net.minecraft.client.resources.model.ModelBakery";
	private static final String BAKE_MODELS = "bakeModels";
	/** The interface whose static bake helper answers the missing model, and the class it bakes. */
	static final String UNBAKED_MODEL = "net.minecraft.client.resources.model.UnbakedModel";
	static final String BLOCK_MODEL = "net.minecraft.client.renderer.block.model.BlockModel";
	private static final String PROBE = "kynarain/cn/optifineoforge/loader/ReloadProbe";
	private static final org.apache.logging.log4j.Logger LOGGER =
			org.apache.logging.log4j.LogManager.getLogger("OptifiNeoforge");

	@Override
	public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
		// ModLauncher matches targets by dotted name but hands over a node whose name is internal.
		String name = input.name.replace('/', '.');
		int probed = 0;
		if(MODEL_MANAGER.equals(name)) {
			probed = probeApply(input);
		} else if(BLOCK_RENDER_DISPATCHER.equals(name)) {
			probed = probeOnReload(input);
		} else if(MODEL_BAKERY.equals(name)) {
			probed = probeBakeModels(input);
		} else if(UNBAKED_MODEL.equals(name)) {
			probed = probeReturns(input, "UnbakedModel.bakeWithTopModelValues");
		} else if(BLOCK_MODEL.equals(name)) {
			probed = probeReturns(input, "BlockModel.bake");
		}
		if(ReloadProbe.enabled()) {
			StringBuilder names = new StringBuilder();
			for(MethodNode method : input.methods) {
				names.append(' ').append(method.name).append(method.desc.substring(0, method.desc.indexOf(')')));
			}
			LOGGER.info("probe saw " + input.name + ", " + input.methods.size() + " methods, probed " + probed
					+ ":" + names);
		}
		return input;
	}

	/** enter at the top of apply, leave before each return, with what the instance now holds. */
	private static int probeApply(ClassNode input) {
		int probed = 0;
		for(MethodNode method : input.methods) {
			if(!APPLY.equals(method.name) || !method.desc.endsWith(")V")) {
				continue;
			}
			probed++;
			String label = "ModelManager.apply";
			method.instructions.insert(call("enter", new LdcInsnNode(label)));
			for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if(insn.getOpcode() != Opcodes.RETURN) {
					continue;
				}
				InsnList leaving = new InsnList();
				leaving.add(new VarInsnNode(Opcodes.ALOAD, 0));
				leaving.add(new LdcInsnNode(label));
				leaving.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, "leave",
						"(Ljava/lang/Object;Ljava/lang/String;)V", false));
				method.instructions.insertBefore(insn, leaving);
			}
		}
		return probed;
	}

	/** enter at the top of the dispatcher's reload, which is where a null model would be read. */
	private static int probeOnReload(ClassNode input) {
		int probed = 0;
		for(MethodNode method : input.methods) {
			if(!ON_RELOAD.equals(method.name) || !method.desc.endsWith(")V")) {
				continue;
			}
			probed++;
			method.instructions.insert(call("enter", new LdcInsnNode("BlockRenderDispatcher.onResourceManagerReload")));
		}
		return probed;
	}

	/** The unbaked missing model going in, and the baked one coming out of the bake. */
	private static int probeBakeModels(ClassNode input) {
		int probed = 0;
		for(MethodNode method : input.methods) {
			if(!BAKE_MODELS.equals(method.name)) {
				continue;
			}
			probed++;
			InsnList entering = new InsnList();
			entering.add(new VarInsnNode(Opcodes.ALOAD, 0));
			entering.add(new LdcInsnNode("ModelBakery (unbaked)"));
			entering.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, "result",
					"(Ljava/lang/Object;Ljava/lang/String;)V", false));
			method.instructions.insert(entering);
			for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if(insn.getOpcode() != Opcodes.ARETURN) {
					continue;
				}
				InsnList leaving = new InsnList();
				leaving.add(new InsnNode(Opcodes.DUP));
				leaving.add(new LdcInsnNode("ModelBakery.bakeModels"));
				leaving.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, "result",
						"(Ljava/lang/Object;Ljava/lang/String;)V", false));
				method.instructions.insertBefore(insn, leaving);
			}
		}
		return probed;
	}

	/** Names what every method returning a model hands back, so a null answer can be placed. */
	private static int probeReturns(ClassNode input, String label) {
		int probed = 0;
		for(MethodNode method : input.methods) {
			boolean bakes = "bake".equals(method.name) || "bakeWithTopModelValues".equals(method.name);
			Type returned = Type.getReturnType(method.desc);
			boolean isModel = returned.getSort() == Type.OBJECT && returned.getInternalName().contains("BakedModel");
			if(!bakes || !isModel) {
				continue;
			}
			probed++;
			for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if(insn.getOpcode() != Opcodes.ARETURN) {
					continue;
				}
				InsnList naming = new InsnList();
				naming.add(new InsnNode(Opcodes.DUP));
				naming.add(new LdcInsnNode(label + " " + method.desc));
				naming.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, "value",
						"(Ljava/lang/Object;Ljava/lang/String;)V", false));
				method.instructions.insertBefore(insn, naming);
			}
		}
		return probed;
	}

	private static InsnList call(String name, LdcInsnNode label) {
		InsnList list = new InsnList();
		list.add(label);
		list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, name, "(Ljava/lang/String;)V", false));
		return list;
	}

	@Override
	public TransformerVoteResult castVote(ITransformerVotingContext context) {
		return TransformerVoteResult.YES;
	}

	@Override
	public Set<Target<ClassNode>> targets() {
		return Set.of(Target.targetClass(MODEL_MANAGER), Target.targetClass(BLOCK_RENDER_DISPATCHER),
				Target.targetClass(MODEL_BAKERY), Target.targetClass(UNBAKED_MODEL), Target.targetClass(BLOCK_MODEL));
	}

	@Override
	public TargetType<ClassNode> getTargetType() {
		return TargetType.CLASS;
	}
}
