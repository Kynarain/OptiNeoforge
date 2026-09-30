/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.optifine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Runs OptiFine's own patcher and splits what it produces into the two things a loader needs.
 *
 * <p>OptiFine patches Minecraft by shipping xdelta diffs and applying them itself. Its entry point
 * for that is {@code optifine.Patcher}, which takes the game jar and the OptiFine jar and writes a
 * third jar holding OptiFine's classes together with the patched game classes - in two namespace
 * variants, {@code srg/} and {@code notch/}. The variant OptiFine's own transformer reads is
 * {@code srg/}, so that is the one taken here; the other is dropped.</p>
 *
 * <p>What comes out of the split is exactly what the rest of the loader wants:</p>
 * <ul>
 *   <li>a classpath jar with OptiFine's own classes and its resources, prefixes stripped;</li>
 *   <li>the patched game classes, keyed by their internal name, ready to replace the vanilla ones.</li>
 * </ul>
 *
 * <p>On Minecraft 26.1 and newer the game ships unobfuscated and OptiFine's payload is already in
 * the runtime names, so the patched classes can be used as they are. On the older lines the payload
 * is in SRG names and, where the runtime does not use SRG, a remapping stage has to run in between -
 * that is a separate step and is not done here.</p>
 *
 * <p>Nothing in this class touches a loader: it runs offline, given two jars. {@link #main} is a
 * development aid that does exactly that.</p>
 */
public final class OptifinePipeline {
	/** Where OptiFine keeps its own classes inside the patched jar, and where the patch data lives. */
	private static final String SRG = "srg/";
	private static final String NOTCH = "notch/";

	/** Game classes live under these packages; everything else under {@code srg/} is OptiFine's own. */
	private static final String[] GAME_PACKAGES = {"net/minecraft/", "com/mojang/"};

	/**
	 * Entries of the patched jar that are OptiFine's installer rather than its runtime.
	 *
	 * <p>They are dropped for two reasons: we run the patcher ourselves, so its machinery is not
	 * needed in the game, and a loader that still sees {@code optifine/Installer.class} refuses the
	 * jar outright. The name is a prefix, so the installer's frame classes go with its entry point.</p>
	 */
	private static final String[] INSTALLER_ENTRIES = {
			"optifine/Installer",
			"optifine/Patcher",
			"optifine/Differ",
			"optifine/xdelta/",
			"optifine/json/",
	};

	private OptifinePipeline() {
	}

	/**
	 * What the patcher produced, split in two.
	 *
	 * @param classpathJar the jar holding OptiFine's classes and resources
	 * @param gameClasses the patched game classes, internal name to bytes
	 * @param entries the patcher output's entry count, for logging
	 * @param dropped the entries left out, for logging
	 */
	public record Split(Path classpathJar, Map<String, byte[]> gameClasses, int entries, int dropped) {

		/** How many of the patched classes are Minecraft's own. */
		public int minecraftClasses() {
			int count = 0;
			for(String name : gameClasses.keySet()) {
				if(name.startsWith("net/minecraft/")) {
					count++;
				}
			}
			return count;
		}
	}

	/**
	 * Runs {@code optifine.Patcher.process(minecraftJar, optifineJar, output)} in a class loader
	 * over the OptiFine jar, exactly as OptiFabric does on Fabric.
	 *
	 * <p>The OptiFine jar is loaded through its own loader rather than the caller's so that its
	 * classes never leak into the game's class space, and so the same code works whether OptiFine
	 * is on our classpath or not.</p>
	 */
	public static void patch(Path minecraftJar, Path optifineJar, Path output) throws IOException {
		try(URLClassLoader loader = new URLClassLoader(new URL[] {optifineJar.toUri().toURL()}, OptifinePipeline.class.getClassLoader())) {
			Class<?> patcher = loader.loadClass("optifine.Patcher");
			Method process = patcher.getDeclaredMethod("process", java.io.File.class, java.io.File.class, java.io.File.class);
			process.invoke(null, minecraftJar.toFile(), optifineJar.toFile(), output.toFile());
		} catch(ReflectiveOperationException e) {
			Throwable cause = e.getCause() == null ? e : e.getCause();
			throw new IOException("OptiFine's patcher failed on " + minecraftJar.getFileName() + " with " + optifineJar.getFileName(), cause);
		}
	}

	/**
	 * Splits the patcher's output jar into a classpath jar and the patched game classes.
	 *
	 * <p>Only the {@code srg/} variant is read (see the class comment): entries in it are collected
	 * as game classes when they belong to the game, and written to the classpath jar with the prefix
	 * removed when they are OptiFine's own. The {@code notch/} variant is dropped, and so is
	 * OptiFine's installer (see {@link #INSTALLER_ENTRIES}). Everything else the jar carries -
	 * OptiFine's assets, shader data, docs, and any service file it ships - is copied over, because
	 * that is what OptiFine needs at runtime.</p>
	 */
	public static Split split(Path patchedJar, Path classpathJar) throws IOException {
		Map<String, byte[]> gameClasses = new TreeMap<>();
		int entries = 0;
		int dropped = 0;

		Path parent = classpathJar.toAbsolutePath().getParent();
		if(parent != null) {
			Files.createDirectories(parent);
		}

		try(ZipFile zip = new ZipFile(patchedJar.toFile());
				ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(classpathJar))) {
			for(Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
				ZipEntry entry = it.nextElement();
				entries++;
				String name = entry.getName();

				if(NOTCH.equals(prefixOf(name)) || isInstallerEntry(name)) {
					dropped++;
					continue;
				}

				String stripped = name.startsWith(SRG) ? name.substring(SRG.length()) : name;
				if(entry.isDirectory()) {
					continue;
				}
				if(isGameClass(stripped)) {
					gameClasses.put(stripped, read(zip.getInputStream(entry)));
				} else {
					ZipEntry copy = new ZipEntry(stripped);
					copy.setTime(entry.getTime());
					out.putNextEntry(copy);
					try(InputStream stream = zip.getInputStream(entry)) {
						stream.transferTo(out);
					}
					out.closeEntry();
				}
			}
		}

		return new Split(classpathJar, gameClasses, entries, dropped);
	}

	/** Whether a name is part of OptiFine's installer or of the patcher we run ourselves. */
	static boolean isInstallerEntry(String name) {
		for(String prefix : INSTALLER_ENTRIES) {
			if(name.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	/** Whether a stripped name is a class the game itself declares, rather than one OptiFine adds. */
	public static boolean isGameClass(String name) {
		if(!name.endsWith(".class")) {
			return false;
		}
		for(String pkg : GAME_PACKAGES) {
			if(name.startsWith(pkg)) {
				return true;
			}
		}
		return false;
	}

	private static String prefixOf(String name) {
		int slash = name.indexOf('/');
		return slash < 0 ? "" : name.substring(0, slash + 1);
	}

	/** Counts entries per top-level directory, for logging and for the development aid. */
	public static Map<String, Integer> roots(Path jar) throws IOException {
		Map<String, Integer> roots = new TreeMap<>();
		try(ZipFile zip = new ZipFile(jar.toFile())) {
			for(Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
				ZipEntry entry = it.nextElement();
				if(!entry.isDirectory()) {
					roots.merge(prefixOf(entry.getName()), 1, Integer::sum);
				}
			}
		}
		return roots;
	}

	private static byte[] read(InputStream stream) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		stream.transferTo(buffer);
		return buffer.toByteArray();
	}

	/**
	 * Development aid: {@code OptifinePipeline <minecraft jar> <optifine jar> <work dir>} patches,
	 * splits and reports what it got. Nothing here is needed at runtime.
	 */
	public static void main(String[] args) throws IOException {
		if(args.length != 3) {
			System.err.println("usage: OptifinePipeline <minecraft jar> <optifine jar> <work dir>");
			System.exit(2);
		}
		Path minecraftJar = Path.of(args[0]);
		Path optifineJar = Path.of(args[1]);
		Path workDir = Path.of(args[2]);
		Files.createDirectories(workDir);
		OptifineConfig config = OptifineConfig.read(optifineJar);
		System.out.println("OptiFine " + config.optifineVersion() + " for Minecraft " + config.minecraftVersion());

		Path patched = workDir.resolve("optifine-patched.jar");
		long start = System.currentTimeMillis();
		patch(minecraftJar, optifineJar, patched);
		System.out.println("patched in " + (System.currentTimeMillis() - start) + " ms -> " + patched + " (" + Files.size(patched) + " bytes)");
		System.out.println("roots: " + roots(patched));

		Path classpathJar = workDir.resolve("optifine-classpath.jar");
		Split split = split(patched, classpathJar);
		System.out.println("split: " + split.entries() + " entries in, " + split.dropped() + " dropped");
		System.out.println("classpath jar: " + classpathJar + " (" + Files.size(classpathJar) + " bytes)");
		System.out.println("patched game classes: " + split.gameClasses().size()
				+ " (" + split.minecraftClasses() + " net/minecraft)");
		split.gameClasses().entrySet().stream().limit(5).forEach(e -> System.out.println("  " + e.getKey() + "  " + e.getValue().length + " bytes"));
	}
}
