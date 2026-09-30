/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.loader;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Reports the listeners a resource reload will run, in the order it will run them.
 *
 * <p>Silent unless {@code -Doptifineoforge.debug.reload=true} is set: this exists to answer one
 * question that the log cannot otherwise answer, namely whether {@code BlockRenderDispatcher} - which
 * asks {@code ModelManager} for baked models while it reloads - is reached before {@code ModelManager}
 * has run its own apply step. When it is, {@code ModelManager.getModel} falls back to its
 * {@code missingModel}, which apply has not set yet, and the caller gets a null model.</p>
 */
public final class ReloadProbe {
	private static final Logger LOGGER = LogManager.getLogger("OptifiNeoforge");
	/** The switch that turns this on; off by default so a shipped game logs nothing extra. */
	private static final String ENABLED = "optifineoforge.debug.reload";
	/** The same switch as an environment variable, for a launch that cannot be given -D arguments. */
	private static final String ENABLED_ENV = "OPTIFINEOFORGE_DEBUG_RELOAD";
	private static final String MODEL_MANAGER = "net.minecraft.client.resources.model.ModelManager";
	private static final String BLOCK_RENDER_DISPATCHER = "net.minecraft.client.renderer.block.BlockRenderDispatcher";
	private static final AtomicInteger RELOADS = new AtomicInteger();

	private ReloadProbe() {
	}

	/** One line per listener, with the two that matter marked. */
	public static void listeners(List<?> listeners) {
		if(!enabled()) {
			return;
		}
		StringBuilder report = new StringBuilder();
		report.append("reload ").append(RELOADS.incrementAndGet()).append(": ").append(listeners.size())
				.append(" listeners");
		for(int index = 0; index < listeners.size(); index++) {
			Object listener = listeners.get(index);
			String name = listener == null ? "null" : listener.getClass().getName();
			report.append("\n    ").append(index).append("  ").append(name)
					.append("  @").append(System.identityHashCode(listener))
					.append("  vanillaName=").append(vanillaName(listener));
			if(MODEL_MANAGER.equals(name)) {
				report.append("   <- bakes the models");
			} else if(BLOCK_RENDER_DISPATCHER.equals(name)) {
				report.append("   <- asks for baked models");
			}
		}
		LOGGER.info(report.toString());
	}

	/** Marks the moment a method starts, so the log shows the order the reload really ran in. */
	public static void enter(String label) {
		if(!enabled()) {
			return;
		}
		LOGGER.info("enter " + label);
	}

	/**
	 * Marks the moment a method finishes, with what the instance is left holding.
	 *
	 * <p>Only for a model class: the two fields named here are the ones the null-model failure turns
	 * on, and reading them says whether the bake produced anything at all. A {@code ModelBakery} has
	 * the unbaked missing model, a {@code ModelManager} has the baked one, and a
	 * {@code ModelBakery$BakingResult} has the one that gets handed over.</p>
	 */
	public static void leave(Object instance, String label) {
		if(!enabled()) {
			return;
		}
		if(instance == null) {
			LOGGER.info("leave " + label);
			return;
		}
		LOGGER.info("leave " + label + ": missingModel=" + describe(read(instance, "missingModel"))
				+ ", blockStates=" + size(read(instance, "bakedBlockStateModels")));
	}

	/** The same reading, for a value being returned rather than an instance finishing a method. */
	public static void result(Object value, String label) {
		if(!enabled()) {
			return;
		}
		LOGGER.info("result " + label + ": missingModel=" + describe(read(value, "missingModel"))
				+ ", blockStates=" + size(read(value, "blockStateModels")));
	}

	/** The value a method hands back, named - for questions about a call that answers null. */
	public static void value(Object value, String label) {
		if(!enabled()) {
			return;
		}
		LOGGER.info("value " + label + ": " + describe(value));
	}

	/** How many entries a map holds, for the registry the sort builds its graph from. */
	public static void count(java.util.Map<?, ?> map, String label) {
		if(!enabled()) {
			return;
		}
		LOGGER.info("count " + label + ": " + (map == null ? "null" : Integer.toString(map.size())));
	}

	/**
	 * How many nodes and edges the sort's graph holds.
	 *
	 * <p>The other inputs to {@code ReloadListenerSort.sort} were measured and agree between a run
	 * with OptiFine and one without, while the sorted result differs - 26 entries against 48 - so the
	 * graph is the one thing left that can differ. Asked reflectively because it is a guava type the
	 * loader does not compile against.</p>
	 */
	public static void graph(Object graph, String label) {
		if(!enabled()) {
			return;
		}
		if(graph == null) {
			LOGGER.info("graph " + label + ": null");
			return;
		}
		try {
			// Asked through guava's public interface rather than the object's own class: the
			// implementation is a package-private ForwardingGraph, and reflecting on that fails with
			// "cannot access a member of class com.google.common.graph.ForwardingGraph".
			ClassLoader loader = graph.getClass().getClassLoader();
			Class<?> type = Class.forName("com.google.common.graph.Graph", true, loader);
			Object nodes = type.getMethod("nodes").invoke(graph);
			Object edges = type.getMethod("edges").invoke(graph);
			// Counted by iterating, not by calling size(): the objects handed back are package-private
			// guava types (MapIteratorCache$1), which reflection refuses to touch.
			LOGGER.info("graph " + label + ": nodes=" + countOf(nodes) + ", edges=" + countOf(edges));
		} catch(Throwable cannotAsk) {
			LOGGER.info("graph " + label + ": ?" + cannotAsk);
		}
	}

	/** How many elements an iterable holds, without asking its own class anything. */
	private static int countOf(Object values) {
		if(!(values instanceof Iterable<?> iterable)) {
			return -1;
		}
		int count = 0;
		for(Object ignored : iterable) {
			count++;
		}
		return count;
	}

	/**
	 * How many elements a collection holds, for the list the sort hands back.
	 *
	 * <p>The reload ends up with 48 entries when OptiFine is installed and 26 when it is not, while
	 * the sort is handed the same 26-entry registry and FML's sort is Kahn's algorithm, which cannot
	 * emit a node twice. Measuring the returned list itself says whether the difference is already
	 * in the sort's answer or appears after it.</p>
	 */
	public static void list(Object values, String label) {
		if(!enabled()) {
			return;
		}
		LOGGER.info("list " + label + ": " + countOf(values) + " of " + describe(values));
	}

	/** How many listeners a list holds, for following the list through registration. */
	public static void size(List<?> listeners, String label) {		if(!enabled()) {
			return;
		}
		LOGGER.info("size " + label + ": " + (listeners == null ? "null" : Integer.toString(listeners.size())));
	}

	/** Who called in, briefly - for a method that should not be running yet. */
	public static void trace(String label) {
		if(!enabled()) {
			return;
		}
		StringBuilder report = new StringBuilder("trace " + label);
		StackTraceElement[] frames = Thread.currentThread().getStackTrace();
		int shown = 0;
		for(StackTraceElement frame : frames) {
			String type = frame.getClassName();
			if(type.startsWith("java.") || type.startsWith("jdk.") || type.startsWith("kynarain.")) {
				continue;
			}
			report.append("\n    at ").append(type).append('.').append(frame.getMethodName());
			if(++shown == 8) {
				break;
			}
		}
		LOGGER.info(report.toString());
	}

	private static Object read(Object instance, String name) {
		for(Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
			try {
				java.lang.reflect.Field field = type.getDeclaredField(name);
				field.setAccessible(true);
				return field.get(instance);
			} catch(NoSuchFieldException | IllegalAccessException | RuntimeException absent) {
				// Try the superclass, then give up: this is a diagnostic.
			}
		}
		return null;
	}

	private static String describe(Object value) {
		return value == null ? "null" : value.getClass().getSimpleName();
	}

	private static String size(Object value) {
		return value instanceof java.util.Map<?, ?> map ? Integer.toString(map.size()) : "n/a";
	}

	/**
	 * The vanilla name NeoForge's sort resolves for a listener, or "?" when it cannot ask.
	 *
	 * <p>{@code ReloadListenerSort.sortListeners} decides with {@code needsToBeLinkedToVanilla}
	 * whether a listener has to be linked into the graph after the last vanilla one, and that
	 * decision goes through this name lookup. A listener whose name resolves is left where it is; one
	 * that does not is linked again, which is how the same object can end up in the reload twice.
	 * Asked reflectively because the loader does not compile against NeoForge.</p>
	 */
	private static String vanillaName(Object listener) {
		if(listener == null) {
			return "n/a";
		}
		try {
			// Resolved through the listener's own class loader: this jar sits in the transformer
			// layer and cannot see NeoForge's classes itself, while a game listener can.
			ClassLoader loader = listener.getClass().getClassLoader();
			Class<?> lookup = Class.forName("net.neoforged.neoforge.client.resources.VanillaClientListeners",
					true, loader);
			Object name = lookup.getMethod("getNameForClass", Class.class).invoke(null, listener.getClass());
			return String.valueOf(name);
		} catch(Throwable cannotAsk) {
			// Say what went wrong rather than "?": a probe that hides its own failure is worse than
			// no probe, and this lookup is reached reflectively so it can fail in several ways.
			String reason = cannotAsk.getClass().getSimpleName();
			if(cannotAsk.getMessage() != null) {
				reason += "(" + cannotAsk.getMessage() + ")";
			}
			return "?" + reason;
		}
	}

	static boolean enabled() {
		return Boolean.getBoolean(ENABLED) || "true".equalsIgnoreCase(System.getenv(ENABLED_ENV));
	}
}
