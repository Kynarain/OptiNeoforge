/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.loader;

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;

/**
 * Puts back the Forge-flavoured tag helper OptiFine calls and NeoForge does not have.
 *
 * <p>OptiFine compiles its copy of {@code net.minecraft.world.item.DyeColor} against Forge, and
 * Forge's {@code ItemTags} carries a two-argument {@code create(String namespace, String path)}
 * helper. OptiFine's DyeColor uses it for the convention dye tags:</p>
 *
 * <pre>this.dyesTag = ItemTags.create("forge", "dyes/" + name);   // via
 * net.optifine.reflect.Reflector.ForgeItemTags_create</pre>
 *
 * <p>NeoForge's {@code ItemTags} has only {@code create(ResourceLocation)}, so the reflective
 * lookup finds nothing, {@code call} answers {@code null}, and both tag fields end up null. That
 * alone would be survivable, but NeoForge builds its own convention tags out of them -
 * {@code TagConventionLogWarning} reads {@code Tags.Items.DYES_BLACK}, which is
 * {@code DyeColor.BLACK.getTag()} - and dies in its static initialiser:</p>
 *
 * <pre>java.lang.ExceptionInInitializerError
 *   at net.neoforged.neoforge.common.NeoForgeMod.&lt;init&gt;(NeoForgeMod.java:564)
 * Caused by: java.lang.NullPointerException: Cannot invoke "TagKey.toString()" because "tag2" is null
 *   at TagConventionLogWarning.createForgeMapEntry(TagConventionLogWarning.java:557)</pre>
 *
 * <p>NeoForge then refuses to hand its own mod state any further event ("Cowardly refusing to send
 * event ... to a broken mod state"), the client never registers its reload listeners, no block model
 * is ever baked, and the first block renderer that asks for one gets a null model - a failure that
 * looks nothing like its cause.</p>
 *
 * <p>The repair is to give the class the helper OptiFine asks for, delegating to the one NeoForge
 * does have, rather than to rewrite OptiFine's call sites: there may be more than one of them, and
 * the reflective lookup only needs the method to exist.</p>
 */
public final class TagHelperFix implements ITransformer<ClassNode> {
	/** The class OptiFine reflects into; the two tags it wants live here. */
	static final String ITEM_TAGS = "net.minecraft.tags.ItemTags";
	/** The Forge helper OptiFine looks for, and the NeoForge helper it delegates to. */
	private static final String FORGE_CREATE = "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/tags/TagKey;";
	private static final String NEAREST_CREATE = "(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;";
	private static final String RESOURCE_LOCATION = "net/minecraft/resources/ResourceLocation";
	/** Namespace mapping, kept in a class of its own because it has to agree with the loader's peers. */
	private static final String CONVENTION_TAGS = "kynarain/cn/optifineoforge/loader/ConventionTags";

	@Override
	public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
		if(hasMethod(input, FORGE_CREATE) || !hasMethod(input, NEAREST_CREATE)) {
			return input;
		}

		// public static TagKey<Item> create(String namespace, String path) {
		//     return create(ResourceLocation.fromNamespaceAndPath(ConventionTags.namespace(namespace), path));
		// }
		MethodNode helper = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create", FORGE_CREATE, null, null);
		helper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		helper.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CONVENTION_TAGS, "namespace",
				"(Ljava/lang/String;)Ljava/lang/String;", false));
		helper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
		helper.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RESOURCE_LOCATION, "fromNamespaceAndPath",
				"(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;", false));
		helper.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, input.name, "create", NEAREST_CREATE, false));
		helper.instructions.add(new InsnNode(Opcodes.ARETURN));
		helper.maxStack = 2;
		helper.maxLocals = 2;
		input.methods.add(helper);
		return input;
	}

	private static boolean hasMethod(ClassNode node, String descriptor) {
		for(MethodNode method : node.methods) {
			if("create".equals(method.name) && descriptor.equals(method.desc)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public TransformerVoteResult castVote(ITransformerVotingContext context) {
		return TransformerVoteResult.YES;
	}

	@Override
	public Set<Target<ClassNode>> targets() {
		return Set.of(Target.targetClass(ITEM_TAGS));
	}

	@Override
	public TargetType<ClassNode> getTargetType() {
		return TargetType.CLASS;
	}
}
