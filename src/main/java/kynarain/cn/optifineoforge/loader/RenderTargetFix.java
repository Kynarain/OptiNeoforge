/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.loader;

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;


/**
 * Puts back the constructor OptiFine's replacement of a class drops.
 *
 * <p>When OptiFine replaces a game class outright, it does so with its own compilation of that
 * class - and that compilation is a Forge compilation. NeoForge's copy of the class can declare
 * something OptiFine's does not, and then the two disagree about an ordinary call. The first case
 * found is {@code com.mojang.blaze3d.pipeline.RenderTarget}: NeoForge's class has both
 * {@code RenderTarget(boolean)} and {@code RenderTarget(boolean, boolean)}, the one-argument form
 * being a plain delegation to the two-argument one, and NeoForge's {@code MainTarget} calls the
 * two-argument form. OptiFine's replacement has only the one-argument constructor, so the game dies
 * the moment a target is created:</p>
 *
 * <pre>NoSuchMethodError: 'void com.mojang.blaze3d.pipeline.RenderTarget.&lt;init&gt;(boolean, boolean)'
 *   at com.mojang.blaze3d.pipeline.MainTarget.&lt;init&gt;(MainTarget.java:22)</pre>
 *
 * <p>The repair is to add the missing constructor to the replaced class, delegating to the one that
 * is there, rather than to rewrite the caller: the caller is NeoForge's own code and there may be
 * more than one of them.</p>
 */
public final class RenderTargetFix implements NodeTransformer {
	/** The class OptiFine replaces, and the constructor NeoForge's callers expect. */
	static final String RENDER_TARGET = "com.mojang.blaze3d.pipeline.RenderTarget";
	private static final String ONE_ARG = "(Z)V";
	private static final String TWO_ARG = "(ZZ)V";
	/** A field NeoForge's own code reads and OptiFine's compilation of the class renamed. */
	private static final String STENCIL_FIELD = "useStencil";

	@Override
	public ClassNode transform(ClassNode input) {
		boolean addedField = addStencilField(input);
		if(hasConstructor(input, TWO_ARG)) {
			return input;
		}
		if(!hasConstructor(input, ONE_ARG)) {
			return input;
		}

		// public RenderTarget(boolean useDepth, boolean useStencil) {
		//     this(useDepth);
		//     this.useStencil = useStencil;
		// }
		MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", TWO_ARG, null, null);
		InsnList body = constructor.instructions;
		body.add(new VarInsnNode(Opcodes.ALOAD, 0));
		body.add(new VarInsnNode(Opcodes.ILOAD, 1));
		body.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, input.name, "<init>", ONE_ARG, false));
		if(addedField) {
			body.add(new VarInsnNode(Opcodes.ALOAD, 0));
			body.add(new VarInsnNode(Opcodes.ILOAD, 2));
			body.add(new FieldInsnNode(Opcodes.PUTFIELD, input.name, STENCIL_FIELD, "Z"));
		}
		body.add(new InsnNode(Opcodes.RETURN));
		constructor.maxStack = 2;
		constructor.maxLocals = 3;
		input.methods.add(constructor);
		return input;
	}

	/** Adds the field NeoForge's code reads, unless OptiFine's copy kept it. Returns whether it was added. */
	private static boolean addStencilField(ClassNode node) {
		for(FieldNode field : node.fields) {
			if(STENCIL_FIELD.equals(field.name)) {
				return false;
			}
		}
		node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, STENCIL_FIELD, "Z", null, null));
		return true;
	}

	private static boolean hasConstructor(ClassNode node, String descriptor) {
		for(MethodNode method : node.methods) {
			if("<init>".equals(method.name) && descriptor.equals(method.desc)) {
				return true;
			}
		}
		return false;
	}


	@Override
	public Set<String> targetClasses() {
		return Set.of(RENDER_TARGET);
	}

}
