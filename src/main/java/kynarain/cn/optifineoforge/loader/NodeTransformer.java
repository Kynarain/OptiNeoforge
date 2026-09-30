/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.loader;

import org.objectweb.asm.tree.ClassNode;

import java.util.Set;

/**
 * One correction this mod makes to a game class, without saying anything about the loader that
 * calls it.
 *
 * <p>The bodies of the transformers used to implement ModLauncher's {@code ITransformer} directly,
 * and that is no longer possible for a branch that covers more than one line. Measured difference
 * between the two generations a 1.20.x branch has to serve:</p>
 *
 * <pre>ModLauncher 10.0.9 (1.20.1, 1.20.2, 1.20.4)
 *   targets() returns Set&lt;ITransformer.Target&gt;, a plain nested class
 *   no getTargetType()
 *
 * ModLauncher 11.0.2, 11.0.4 (1.20.6, 1.21.x, 26.x)
 *   targets() returns Set&lt;ITransformer.Target&lt;T&gt;&gt;, a record
 *   getTargetType() is abstract; a concrete transformer must implement it</pre>
 *
 * <p>So {@code getTargetType()} is the one thing a class cannot have and lack at the same time, and
 * the bodies - the expensive part, and the part that is about Minecraft rather than about the
 * loader - are exactly the part that does not differ. They implement this interface instead, and
 * {@link ModLauncherCompat} presents them to whichever loader is running.</p>
 */
public interface NodeTransformer {
	/** The class names to be called for, dotted, as ModLauncher spells them. */
	Set<String> targetClasses();

	/** The correction itself. The node handed in is the class as the runtime compiled it. */
	ClassNode transform(ClassNode input);
}
