/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * The entry point of the 26.x line.
 *
 * <p>This is the skeleton the loader is built into, so nothing is wired up yet. The work that
 * will live behind it is described in {@code docs/PLAN.md}: run OptiFine's own patcher against
 * the game's jar, rebuild the lambdas its patches moved, cache the resulting classes, and hand
 * the patched Minecraft classes to NeoForge's class transformation pipeline so they replace the
 * vanilla ones.</p>
 *
 * <p>Note where the real work has to happen: mod construction runs long after classes have been
 * transformed, so the patched classes cannot be installed from this constructor. The loading
 * side therefore hangs off NeoForge's ModLauncher pipeline (the same place OptiFine's own Forge
 * integration uses), and this class is only the mod's identity and its later setup.</p>
 */
@Mod(OptifiNeoforge.MOD_ID)
public final class OptifiNeoforge {
	public static final String MOD_ID = "optifineoforge";

	public OptifiNeoforge(IEventBus modEventBus, ModContainer modContainer) {
		// Intentionally empty for now.
	}
}
