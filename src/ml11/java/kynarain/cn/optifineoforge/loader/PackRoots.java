/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.loader;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Answers {@code File} for resource paths that have no {@code File} of their own.
 *
 * <p>OptiFine's {@code ResUtils.collectFiles} sorts resource packs by class - a directory pack and a
 * zip pack are handled by walking or unzipping a {@code java.io.File} - and for a
 * {@code PathPackResources} it just calls {@code Path.toFile()} on the pack root. On Forge that
 * worked, because a mod's pack root was a directory on the default filesystem. On NeoForge a mod's
 * resources live in the jar's union filesystem, and {@code toFile} is not merely wrong there, it
 * throws:</p>
 *
 * <pre>java.lang.UnsupportedOperationException: Path not associated with default file system.
 *   at java.nio.file.Path.toFile(Path.java:772)
 *   at net.optifine.util.ResUtils.collectFiles(ResUtils.java:104)
 *   at net.optifine.CustomItems.update(CustomItems.java:168)
 *   at net.optifine.util.TextureUtils.resourcesPreReload(TextureUtils.java:315)</pre>
 *
 * <p>That happens inside the initial resource reload, so the game dies while constructing
 * {@code Minecraft} - before any of the model work that OptiFine does later on.</p>
 *
 * <p>Three answers are tried, in order. The ordinary one is the path's own {@code toFile}, which
 * covers every pack that really is a directory. Next, a union path usually still names the jar it
 * came from ({@code union:/...&#47;mod.jar%23214!/assets}), and the jar itself is a perfectly good
 * {@code File} - handing that back sends OptiFine down its zip branch, which is what a jar-shaped
 * pack wants anyway. Last, the subtree is copied into a temporary directory, so that a union of
 * several jars, or any other filesystem without files, still produces something walkable. When even
 * that fails the answer is null, which OptiFine reads as "this pack has nothing to offer": losing a
 * custom-item folder is a far smaller loss than refusing to start.</p>
 */
public final class PackRoots {
	private static final String UNION = "union:";
	/** The directory a copy made by {@link #materialise} went to, removed when the game exits. */
	private static volatile Path copiedRoot;

	private PackRoots() {
	}

	/** A {@code File} for a pack root, or null when this path cannot be one. */
	public static File toFile(Path path) {
		if(path == null) {
			return null;
		}
		try {
			return path.toFile();
		} catch(RuntimeException notDefaultFileSystem) {
			// Expected for a mod jar: carry on with the fallbacks.
		}
		File named = unwrap(path.toString());
		if(named != null && (named.isDirectory() || named.isFile())) {
			return named;
		}
		return materialise(path);
	}

	/**
	 * The file a union path names, without the filesystem decoration around it:
	 * {@code union:/C:/mods/example.jar%23214!/assets} becomes {@code C:\mods\example.jar}.
	 */
	static File unwrap(String text) {
		if(text == null || !text.startsWith(UNION)) {
			return null;
		}
		String rest = text.substring(UNION.length());
		int payload = rest.indexOf("!/");
		if(payload >= 0) {
			rest = rest.substring(0, payload);
		} else if(rest.endsWith("!")) {
			rest = rest.substring(0, rest.length() - 1);
		}
		int suffix = rest.indexOf('%');
		if(suffix >= 0) {
			rest = rest.substring(0, suffix);
		}
		if(rest.length() > 2 && rest.charAt(0) == '/' && rest.charAt(2) == ':') {
			rest = rest.substring(1);
		}
		if(rest.isEmpty()) {
			return null;
		}
		return new File(rest);
	}

	/** Copies the whole subtree into a temporary directory, and answers that directory. */
	private static File materialise(Path path) {
		try {
			Path root = copyRoot();
			Path target = root.resolve(Integer.toHexString(path.toString().hashCode()));
			if(Files.isDirectory(target)) {
				return target.toFile();
			}
			Files.createDirectories(target);
			copyTree(path, target);
			return target.toFile();
		} catch(IOException | RuntimeException failed) {
			return null;
		}
	}

	/** One temporary directory for every copy this run makes, removed on exit. */
	private static synchronized Path copyRoot() throws IOException {
		Path root = copiedRoot;
		if(root == null) {
			Path created = Files.createTempDirectory("optifineoforge-packs");
			copiedRoot = created;
			Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteQuietly(created), "optifineoforge-pack-cleanup"));
			root = created;
		}
		return root;
	}

	/** Walks the union path itself, which works, instead of the {@code File} path, which does not. */
	private static void copyTree(Path source, Path target) throws IOException {
		try (Stream<Path> entries = Files.walk(source)) {
			for(Path entry : (Iterable<Path>) entries::iterator) {
				Path destination = target.resolve(source.relativize(entry).toString());
				if(Files.isDirectory(entry)) {
					Files.createDirectories(destination);
				} else if(Files.isRegularFile(entry)) {
					Files.createDirectories(destination.getParent());
					Files.copy(entry, destination, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		} catch(UncheckedIOException wrapped) {
			throw wrapped.getCause();
		}
	}

	private static void deleteQuietly(Path root) {
		try (Stream<Path> entries = Files.walk(root)) {
			entries.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch(IOException ignored) {
					// Best effort: a leftover temporary directory is harmless.
				}
			});
		} catch(IOException | RuntimeException ignored) {
			// Best effort.
		}
	}
}
