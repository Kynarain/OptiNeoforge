/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.loader;

import java.lang.reflect.Method;

/**
 * Turns the namespace OptiFine compiles into its tag lookups back into the one NeoForge uses.
 *
 * <p>OptiFine's Forge-era code asks for convention tags by the namespace Forge used at the time -
 * {@code ItemTags.create("forge", "dyes/black")}. NeoForge renamed that namespace from {@code forge}
 * to {@code c} in 1.21, so on a modern line the same request has to become {@code c:dyes/black} or
 * it names a tag that does not exist: the tag would come back empty, and it would no longer be the
 * tag NeoForge's own {@code Tags} class holds. The old namespace is the right answer on the 1.20
 * line, where NeoForge still called it {@code forge}.</p>
 *
 * <p>The version is read reflectively from {@code SharedConstants}, because this class is compiled
 * against nothing but ModLauncher, ASM and Log4j - it is part of the loader, which is not given the
 * game on its compile classpath. Both accessor shapes are tried, since the name of the game's
 * version accessor changed between the lines this project covers.</p>
 */
public final class ConventionTags {
	/** The namespace Forge used, and the one NeoForge uses from 1.21 on. */
	private static final String FORGE = "forge";
	private static final String NEOFORGE = "c";

	private static volatile String convention;

	private ConventionTags() {
	}

	/** Maps a Forge convention namespace onto the one the running NeoForge line actually uses. */
	public static String namespace(String namespace) {
		if(!FORGE.equals(namespace)) {
			return namespace;
		}
		String resolved = convention;
		if(resolved == null) {
			resolved = detect();
			convention = resolved;
		}
		return resolved;
	}

	/** {@code "c"} from 1.21 (and its 26.x successor) on, {@code "forge"} before that. */
	private static String detect() {
		int minor = minorVersion();
		if(minor < 0) {
			return NEOFORGE;
		}
		return minor >= 21 ? NEOFORGE : FORGE;
	}

	/** The minor part of the game version, or -1 when it cannot be read. */
	private static int minorVersion() {
		try {
			Class<?> sharedConstants = Class.forName("net.minecraft.SharedConstants");
			Object version = sharedConstants.getMethod("getCurrentVersion").invoke(null);
			String name = versionName(version);
			if(name == null) {
				return -1;
			}
			String[] parts = name.split("[.\\-+]");
			if(parts.length < 2) {
				return -1;
			}
			return Integer.parseInt(parts[1]);
		} catch(Throwable throwable) {
			return -1;
		}
	}

	/** The version's name, whichever of the two accessors this game version has. */
	private static String versionName(Object version) throws Exception {
		for(String accessor : new String[] {"name", "getName"}) {
			try {
				Method method = version.getClass().getMethod(accessor);
				Object name = method.invoke(version);
				if(name instanceof String text) {
					return text;
				}
			} catch(NoSuchMethodException ignored) {
				// Try the other accessor.
			}
		}
		return null;
	}
}
