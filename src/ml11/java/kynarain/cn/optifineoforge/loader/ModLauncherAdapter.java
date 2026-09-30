/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.loader;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformer.Target;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The ModLauncher 11 flavour of the transformer adapter - the 1.20.6 build, and the shape the later
 * lines use as well.
 *
 * <p>Identical to the 10 flavour except in the two places the interface actually changed; the
 * corrections themselves live in {@link NodeTransformer} and are shared. Read with javap, not
 * recalled:</p>
 *
 * <pre>ModLauncher 10.0.9                     ModLauncher 11.0.2 / 11.0.4
 * Set&lt;ITransformer.Target&gt; targets()      Set&lt;ITransformer.Target&lt;T&gt;&gt; targets()
 * (no getTargetType)                     TargetType&lt;T&gt; getTargetType() - abstract
 * List&lt;ITransformer&gt; transformers()      List&lt;? extends ITransformer&lt;?&gt;&gt; transformers()</pre>
 *
 * <p>Under 11 the target type is asked for directly, which is why it exists at all there; under 10 the
 * type argument of the interface is what says it, and 10 rejects a class whose interface is not
 * parameterized - see the 10 flavour of this file for the disassembly that proves it.</p>
 */
public final class ModLauncherAdapter implements ITransformer<ClassNode> {
	private final NodeTransformer body;

	private ModLauncherAdapter(NodeTransformer body) {
		this.body = body;
	}

	/** The transformers this mod registers, in the order the service lists them. */
	public static List<ITransformer<ClassNode>> wrap(List<NodeTransformer> bodies) {
		List<ITransformer<ClassNode>> result = new ArrayList<>(bodies.size());
		for(NodeTransformer body : bodies) {
			result.add(new ModLauncherAdapter(body));
		}
		return result;
	}

	@Override
	public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
		return body.transform(input);
	}

	@Override
	public TransformerVoteResult castVote(ITransformerVotingContext context) {
		// Every correction here is unconditional: it is applied because the class is one this line
		// needs corrected, not because of what another mod did to it.
		return TransformerVoteResult.YES;
	}

	@Override
	public Set<Target<ClassNode>> targets() {
		Set<Target<ClassNode>> result = new LinkedHashSet<>();
		for(String name : body.targetClasses()) {
			result.add(Target.targetClass(name));
		}
		return result;
	}

	@Override
	public TargetType<ClassNode> getTargetType() {
		// The class as the runtime compiled it, not the pre-class stage: OptiFine's payload is applied
		// to classes, and this mod's corrections compare a class against the runtime's own version.
		return TargetType.CLASS;
	}

	@Override
	public String[] labels() {
		return new String[] {body.getClass().getSimpleName()};
	}
}
