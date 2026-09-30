/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.loader;

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;


/**
 * Points OptiFine's pack scanning at {@link PackRoots} instead of {@code Path.toFile}.
 *
 * <p>One method needs this today: {@code ResUtils.collectFiles} calls {@code Path.toFile()} on a
 * {@code PathPackResources} root, which throws for a NeoForge mod's union filesystem path and takes
 * the initial resource reload, and the game, with it. The call site is redirected rather than the
 * method rewritten, because the rest of {@code collectFiles} - the pack classes it distinguishes,
 * the zip reading, the prefix and suffix filtering - is OptiFine behaviour worth keeping exactly as
 * it is. A scan of OptiFine's own classes finds only two that call {@code Path.toFile} at all: this
 * one, and {@code OptiFineTransformationService}, whose call the repack already rewrites.</p>
 */
public final class PackRootsFix implements NodeTransformer {
	/** The class whose pack scanning has to survive a union filesystem. */
	static final String RES_UTILS = "net.optifine.util.ResUtils";
	/** The call being replaced, and the one that replaces it. */
	private static final String PATH_TO_FILE = "java/nio/file/Path";
	private static final String TO_FILE = "toFile";
	private static final String TO_FILE_DESC = "()Ljava/io/File;";
	private static final String PACK_ROOTS = "kynarain/cn/optifineoforge/loader/PackRoots";
	private static final String PACK_ROOTS_DESC = "(Ljava/nio/file/Path;)Ljava/io/File;";

	@Override
	public ClassNode transform(ClassNode input) {
		int replaced = 0;
		for(MethodNode method : input.methods) {
			for(AbstractInsnNode instruction : method.instructions) {
				if(instruction instanceof MethodInsnNode call && isPathToFile(call)) {
					method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, PACK_ROOTS, TO_FILE,
							PACK_ROOTS_DESC, false));
					replaced++;
				}
			}
		}
		return input;
	}

	private static boolean isPathToFile(MethodInsnNode call) {
		return call.getOpcode() == Opcodes.INVOKEINTERFACE && PATH_TO_FILE.equals(call.owner)
				&& TO_FILE.equals(call.name) && TO_FILE_DESC.equals(call.desc);
	}


	@Override
	public Set<String> targetClasses() {
		return Set.of(RES_UTILS);
	}

}
