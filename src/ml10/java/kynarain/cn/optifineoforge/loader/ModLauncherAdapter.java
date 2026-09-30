/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.loader;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformer.Target;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The ModLauncher 10 flavour of the transformer adapter - the 1.20.1, 1.20.2 and 1.20.4 build.
 *
 * <p>The corrections themselves live in {@link NodeTransformer} and are the same on every line; this
 * class is the part that has to differ, because ModLauncher's transformer interface changed between
 * the generations a 1.20.x branch serves. Read with javap, not recalled:</p>
 *
 * <pre>ModLauncher 10.0.9                     ModLauncher 11.0.2 / 11.0.4
 * Set&lt;ITransformer.Target&gt; targets()      Set&lt;ITransformer.Target&lt;T&gt;&gt; targets()
 * (no getTargetType)                     TargetType&lt;T&gt; getTargetType() - abstract
 * List&lt;ITransformer&gt; transformers()      List&lt;? extends ITransformer&lt;?&gt;&gt; transformers()</pre>
 *
 * <p>Why a real class rather than a dynamic proxy, which would have kept this to one source file: it
 * does not work under 10, and the failure is measured rather than assumed. Gathering transformers
 * groups them by the type argument of the interface:</p>
 *
 * <pre>TransformationServiceDecorator.lambda$gatherTransformers$0:
 *   t.getClass().getGenericInterfaces() -> must contain a ParameterizedType
 *   whose raw type is ITransformer; its first type argument is the group key
 *   otherwise: RuntimeException("How did a non-transformer get here????")</pre>
 *
 * <p>A proxy's {@code getGenericInterfaces()} is the raw interface - {@code java.lang.reflect.Proxy}
 * defines the interfaces it is given, without the type arguments - so every proxy transformer died in
 * that classifier, which is exactly the error this trial produced. Hence two source roots, the same
 * class name in each, and the build compiling only the line's own:
 * {@code src/ml10/java} and {@code src/ml11/java}.</p>
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
	public Set<Target> targets() {
		Set<Target> result = new LinkedHashSet<>();
		for(String name : body.targetClasses()) {
			result.add(Target.targetClass(name));
		}
		return result;
	}

	@Override
	public String[] labels() {
		return new String[] {body.getClass().getSimpleName()};
	}
}
