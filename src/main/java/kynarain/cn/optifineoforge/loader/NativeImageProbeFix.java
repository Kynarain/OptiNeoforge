/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.loader;

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;


/**
 * Marks {@code NativeImage.close} and {@code NativeImage.upload}, with a short stack for the close.
 *
 * <p>The atlas upload fails with {@code IllegalStateException: Image is not allocated}, which means
 * the {@code NativeImage} being uploaded has already been closed - {@code pixels} is back to zero.
 * The question is who closed it, and a stack at {@code close} answers that directly.</p>
 *
 * <p>Diagnostic only, and silent unless {@code -Doptifineoforge.debug.reload=true} is set.</p>
 */
public final class NativeImageProbeFix implements NodeTransformer {
	/** The class OptiFine replaces, and the two methods worth watching. */
	static final String NATIVE_IMAGE = "com.mojang.blaze3d.platform.NativeImage";
	private static final String CLOSE = "close";
	private static final String UPLOAD = "upload";
	private static final String PROBE = "kynarain/cn/optifineoforge/loader/ReloadProbe";

	@Override
	public ClassNode transform(ClassNode input) {
		if(!NATIVE_IMAGE.equals(input.name.replace('/', '.'))) {
			return input;
		}
		for(MethodNode method : input.methods) {
			if(CLOSE.equals(method.name) && "()V".equals(method.desc)) {
				method.instructions.insert(report("trace", "NativeImage.close"));
			} else if(UPLOAD.equals(method.name) && method.desc.endsWith(")V")) {
				method.instructions.insert(report("enter", "NativeImage.upload"));
			}
		}
		return input;
	}

	private static InsnList report(String probeMethod, String label) {
		InsnList list = new InsnList();
		list.add(new LdcInsnNode(label));
		list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, probeMethod, "(Ljava/lang/String;)V", false));
		return list;
	}


	@Override
	public Set<String> targetClasses() {
		return Set.of(NATIVE_IMAGE);
	}

}
