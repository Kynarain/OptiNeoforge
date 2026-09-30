/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.loader;

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;

/**
 * Puts back the listener accessor OptiFine's replacement of the resource manager drops.
 *
 * <p>The same shape as {@link RenderTargetFix}, one class further on: OptiFine replaces {@code
 * net.minecraft.server.packs.resources.ReloadableResourceManager} with its own compilation, which
 * keeps the private listener list but not the accessor NeoForge added to it. NeoForge's
 * {@code AddClientReloadListenersEvent} then cannot be built at all:</p>
 *
 * <pre>NoSuchMethodError: 'java.util.List ...ReloadableResourceManager.getListeners()'
 *   at net.neoforged.neoforge.client.event.AddClientReloadListenersEvent.&lt;init&gt;</pre>
 *
 * <p>Here the restoration is exact rather than approximate: the field is still there under the same
 * name and type, so the accessor is written as a plain getter for it.</p>
 */
public final class ReloadableResourceManagerFix implements ITransformer<ClassNode> {
	static final String RESOURCE_MANAGER = "net/minecraft/server/packs/resources/ReloadableResourceManager";
	private static final String GET_LISTENERS = "getListeners";
	private static final String GET_LISTENERS_DESC = "()Ljava/util/List;";
	/** The field both compilations keep. */
	private static final String LISTENERS_FIELD = "listeners";
	private static final String LISTENERS_DESC = "Ljava/util/List;";
	private static final String UPDATE_FROM = "updateListenersFrom";
	private static final String UPDATE_FROM_DESC = "(Lnet/neoforged/neoforge/event/SortedReloadListenerEvent;)V";
	private static final String SORTED_EVENT = "net/neoforged/neoforge/event/SortedReloadListenerEvent";
	/** NeoForge's own sorting helper, which the restored method delegates to. */
	private static final String RELOAD_LISTENER_SORT = "net/neoforged/neoforge/resource/ReloadListenerSort";

	public ReloadableResourceManagerFix() {
	}

	@Override
	public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
		if(!hasField(input, LISTENERS_FIELD)) {
			return input;
		}
		addGetter(input);
		addUpdateFrom(input);
		return input;
	}

	/** {@code public List<PreparableReloadListener> getListeners() { return this.listeners; } } */
	private static void addGetter(ClassNode input) {
		if(hasMethod(input, GET_LISTENERS, GET_LISTENERS_DESC)) {
			return;
		}

		MethodNode getter = new MethodNode(Opcodes.ACC_PUBLIC, GET_LISTENERS, GET_LISTENERS_DESC, null, null);
		InsnList body = getter.instructions;
		body.add(new VarInsnNode(Opcodes.ALOAD, 0));
		body.add(new FieldInsnNode(Opcodes.GETFIELD, input.name, LISTENERS_FIELD, LISTENERS_DESC));
		body.add(new InsnNode(Opcodes.ARETURN));
		getter.maxStack = 1;
		getter.maxLocals = 1;
		input.methods.add(getter);
	}

	/**
	 * NeoForge's version of this method assigns the sorted list to the field:
	 * {@code this.listeners = ReloadListenerSort.sort(event);}. OptiFine's copy declares the field
	 * final, and a {@code putfield} to a final field outside an initialiser is rejected by the
	 * verifier, so the same effect is reached by replacing the contents of the existing list.
	 *
	 * <p>Replacing, not adding: the first version of this method appended with {@code addAll}, which
	 * left the vanilla listeners NeoForge had already registered in place and put the sorted copy
	 * after them - 22 entries becoming 48. The reload then ran every vanilla listener twice, and the
	 * second pass over the sprites closed the ones the first had just built, which surfaced much later
	 * as {@code IllegalStateException: Image is not allocated} from the atlas upload. Clearing first
	 * is what makes this equal to an assignment while leaving the field final and the list mutable for
	 * the listeners registered after the event.</p>
	 */
	private static void addUpdateFrom(ClassNode input) {
		if(hasMethod(input, UPDATE_FROM, UPDATE_FROM_DESC)) {
			return;
		}

		MethodNode update = new MethodNode(Opcodes.ACC_PUBLIC, UPDATE_FROM, UPDATE_FROM_DESC, null, null);
		InsnList body = update.instructions;
		body.add(new VarInsnNode(Opcodes.ALOAD, 0));
		body.add(new FieldInsnNode(Opcodes.GETFIELD, input.name, LISTENERS_FIELD, LISTENERS_DESC));
		body.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "clear", "()V", true));
		body.add(new VarInsnNode(Opcodes.ALOAD, 0));
		body.add(new FieldInsnNode(Opcodes.GETFIELD, input.name, LISTENERS_FIELD, LISTENERS_DESC));
		body.add(new VarInsnNode(Opcodes.ALOAD, 1));
		body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RELOAD_LISTENER_SORT, "sort",
				"(L" + SORTED_EVENT + ";)Ljava/util/List;", false));
		body.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "addAll",
				"(Ljava/util/Collection;)Z", true));
		body.add(new InsnNode(Opcodes.POP));
		body.add(new InsnNode(Opcodes.RETURN));
		update.maxStack = 2;
		update.maxLocals = 2;
		input.methods.add(update);
	}

	private static boolean hasMethod(ClassNode node, String name, String descriptor) {
		for(MethodNode method : node.methods) {
			if(name.equals(method.name) && descriptor.equals(method.desc)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasField(ClassNode node, String name) {
		for(org.objectweb.asm.tree.FieldNode field : node.fields) {
			if(name.equals(field.name)) {
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
		return Set.of(Target.targetClass(RESOURCE_MANAGER));
	}

	@Override
	public TargetType<ClassNode> getTargetType() {
		return TargetType.CLASS;
	}
}
