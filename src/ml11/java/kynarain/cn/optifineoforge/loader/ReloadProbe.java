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

	/**
	 * One listener's reload task has begun, paired with {@link #finished}.
	 *
	 * <p>The pair is what names the listener a reload stops on. A task that reports started and never
	 * finishes is the one holding the reload open - and that is a thing no thread dump can show once the
	 * task's thread has gone idle, which is exactly the state 1.21 settles into: 28 listeners, no thread in
	 * the reload code, everything parked.</p>
	 */
	public static void started(Object listener) {
		if(!enabled()) {
			return;
		}
		running++;
		LOGGER.info("listener task started (" + running + " in flight, " + Thread.currentThread().getName()
				+ "): " + describe(listener));
	}

	/** The same task returned. */
	public static void finished(Object listener) {
		if(!enabled()) {
			return;
		}
		running--;
		LOGGER.info("listener task finished (" + running + " in flight): " + describe(listener));
	}

	/**
	 * A listener has reached the reload barrier, so the reload can proceed once every listener has.
	 *
	 * <p>Paired with the started/finished pair, this is what names the listener that holds a reload open:
	 * vanilla waits for all of them, so the one never listed here is the one responsible.</p>
	 */
	public static void reachedBarrier(Object listener) {
		if(!enabled()) {
			return;
		}
		reached++;
		LOGGER.info("barrier reached (" + reached + " of " + running + " in flight): " + describe(listener));
	}

	/** How many listeners have reached the barrier in this reload. */
	private static volatile int reached;

	/**
	 * A listener task has reached the reload barrier, reported with the thread it runs on.
	 *
	 * <p>The barrier wait receives the value the listener hands over, not the listener, so naming the
	 * listener that never arrives takes the thread instead: the started lines carry both the listener and
	 * its thread, and the thread that started a task and never appears here is the one holding the reload
	 * open.</p>
	 */
	public static void barrierReached() {
		if(!enabled()) {
			return;
		}
		// The whole stack, not a guessed caller: the reload path runs through wrappers and lambdas, so the
		// first useful frame is not the listener - measured, and it is why this logs every frame instead.
		// The listener whose class appears in no arrival is the one that never got here.
		StringBuilder frames = new StringBuilder();
		for(StackTraceElement frame : Thread.currentThread().getStackTrace()) {
			String name = frame.getClassName();
			if(name.startsWith("java.") || name.startsWith("jdk.") || name.startsWith("kynarain.")) {
				continue;
			}
			frames.append(name).append('|');
		}
		LOGGER.info("barrier stack on " + Thread.currentThread().getName() + ": " + frames);	}
	/**
	 * Watches the future one listener's reload returns, naming it when the future completes.
	 *
	 * <p>The way to name the listener a reload stops on: every listener's task is started by the reload, so
	 * the started lines already carry all the names, and the one whose future never completes is missing
	 * from these lines. Walking the stack instead does not work - the reload path goes through wrappers and
	 * lambdas, so the frames there are RenderSystem and ResourceManagerReloadListener rather than the
	 * listener, which was measured before this was written.</p>
	 */
	public static void watching(java.util.concurrent.CompletableFuture<?> future, Object listener) {
		if(!enabled()) {
			return;
		}
		String name = describe(listener);
		future.whenComplete((value, error) -> LOGGER.info("listener future completed: " + name
				+ (error == null ? "" : " with " + error)));
	}
	/**
	 * One listener has entered its own reload method, named by the class the plan listed.
	 *
	 * <p>This is the listener side of the correlation, and it exists because the barrier side carries no
	 * identity: the arrival stack is wrappers all the way down and the barrier wait receives only the value
	 * the listener hands over. The class list comes from a previous run - the started lines name every
	 * listener - so the one whose reload entry is missing here is the one that never arrives.</p>
	 */
	public static void reloadEntered(String owner) {
		if(!enabled()) {
			return;
		}
		reloads++;
		LOGGER.info("reload entered (" + reloads + "): " + owner);
	}

	/** How many listener reload methods have been entered in this run. */
	private static volatile int reloads;
	/**
	 * The transformer was handed this class. Paired with {@link #reloadEntered}, this separates the two
	 * remaining stories about a listener that never gets marked: either it was never delivered to this
	 * transformer, or it was delivered and the injection found nothing to attach to.
	 */
	public static void saw(String owner) {
		if(!enabled()) {
			return;
		}
		LOGGER.info("delivered: " + owner);
	}
	/**
	 * One marked moment in the model-loading order, on the log's own timestamp.
	 *
	 * <p>The only entry point here that is <em>not</em> gated on {@code optifineoforge.debug.reload}, and
	 * deliberately so: every other method has to decide for itself whether it was wanted, because the
	 * call is already in the class whether or not anyone asked for it. This one exists only where the
	 * instrumentation was asked for - {@code PatchedClassTransformer.traceModelLoading} writes the call
	 * under {@code -Doptifineoforge.traceModels=true} - so a second switch here would only be a way to
	 * ask for the instrumentation and then hear nothing from it, which is how a previous probe on this
	 * branch (ReloadProbeFix, whose flag was left off) came back silent and was read as "the class was
	 * never delivered".</p>
	 *
	 * <p>The message is a plain marker rather than a formatted state dump because the question it
	 * answers is one of <em>order</em>: {@code CustomItems.updateIcons} waits in a loop for the flag
	 * {@code CustomItems.loadModels} sets, and the only way to tell "the setter never ran" from "the
	 * setter ran and something reset it" is to see the two moments against each other with the sleep in
	 * between. Parent's thread dump on 1.21 shows the waiting half; this is the half that was missing.</p>
	 */
	public static void mark(String label) {
		LOGGER.info("trace " + label + " [CustomItems.modelsLoaded=" + modelsLoaded() + "]");
		watchFlag();
	}

	/**
	 * The same, for a call site inside a loop, reported at most {@code limit} times.
	 *
	 * <p>The constructor that loads the models calls one method once per registered item, which is a
	 * thousand-odd times on a real registry. Marking every turn would bury the log and change the run's
	 * timing; marking none would leave the longest part of the constructor invisible. So each label gets
	 * its own counter and the first few turns are reported - enough to show the loop was entered, and the
	 * marker after it shows the loop finished.</p>
	 */
	public static void markLimited(String label, int limit) {
		int seen = MARK_COUNTS.merge(label, 1, Integer::sum);
		if(seen > limit) {
			return;
		}
		LOGGER.info("trace " + label + " (" + seen + " of at most " + limit + ")"
				+ " [CustomItems.modelsLoaded=" + modelsLoaded() + "]");
		watchFlag();
	}

	/** How many times each label has been reported, so a marker in a loop stays readable. */
	private static final java.util.concurrent.ConcurrentHashMap<String, Integer> MARK_COUNTS =
			new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Watches OptiFine's flag and reports every change, because "set" and "still set" are different facts.
	 *
	 * <p>The wait in {@code CustomItems.updateIcons} polls every 100 ms, so a flag that is set at all is
	 * normally noticed on the next turn. A worker still polling a minute later therefore means one of two
	 * things, and the thread dump cannot tell them apart: either the setter never ran, or it ran and
	 * something <em>reset</em> the flag inside the same 100 ms window. The reset exists -
	 * {@code CustomItems.update()} sets it to false and is reached from
	 * {@code TextureUtils.resourcesPreReload}, which is what OptiFine's own pre-reload listener calls
	 * while the same reload is still running - so the window is real, and only a timeline settles it.</p>
	 *
	 * <p>Started on the first marker and only then, so a run with no model activity costs nothing. The
	 * class loader is captured from the thread that first reached a marker, which is a game-layer worker:
	 * this jar sits below the game layer and cannot name {@code net.optifine.CustomItems} itself, but the
	 * layer that runs the game can.</p>
	 */
	private static void watchFlag() {
		synchronized(FLAG_WATCH) {
			if(flagWatchStarted) {
				return;
			}
			flagWatchStarted = true;
		}
		ClassLoader gameLayer = Thread.currentThread().getContextClassLoader();
		Thread watcher = new Thread(() -> {
			String last = modelsLoaded(gameLayer);
			LOGGER.info("trace flag watch: CustomItems.modelsLoaded starts as " + last);
			for(int turn = 0; turn < FLAG_WATCH_TURNS; turn++) {
				try {
					Thread.sleep(50L);
				} catch(InterruptedException stopped) {
					Thread.currentThread().interrupt();
					return;
				}
				String now = modelsLoaded(gameLayer);
				if(!now.equals(last)) {
					LOGGER.info("trace flag watch: CustomItems.modelsLoaded " + last + " -> " + now);
					last = now;
				}
			}
			LOGGER.info("trace flag watch: stopped watching after " + (FLAG_WATCH_TURNS / 20) + "s");
		}, "OptiFineFlagWatch");
		watcher.setDaemon(true);
		watcher.start();
	}

	/** How long the watcher runs: 50 ms a turn, so 12000 turns is ten minutes. */
	private static final int FLAG_WATCH_TURNS = 12000;

	private static final Object FLAG_WATCH = new Object();

	private static boolean flagWatchStarted;

	/** The flag's value as a string, or why it could not be read - never a bare "?". */
	private static String modelsLoaded() {
		return modelsLoaded(Thread.currentThread().getContextClassLoader());
	}

	private static String modelsLoaded(ClassLoader loader) {
		try {
			Class<?> items = Class.forName("net.optifine.CustomItems", false, loader);
			java.lang.reflect.Field field = items.getDeclaredField("modelsLoaded");
			field.setAccessible(true);
			return String.valueOf(field.get(null));
		} catch(Throwable cannotAsk) {
			String reason = cannotAsk.getClass().getSimpleName();
			if(cannotAsk.getMessage() != null) {
				reason += "(" + cannotAsk.getMessage() + ")";
			}
			return "?" + reason;
		}
	}

	/** Listener tasks in flight, so a reload that stalls with none in flight is visible as such. */
	private static volatile int running;

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
