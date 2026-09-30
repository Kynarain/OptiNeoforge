/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * The entry point of the 1.20.x line, for the targets whose FML uses the neoforged package names.
 *
 * <p>This file and its twin under {@code src/forge/java} declare the same class and exactly one of
 * them is compiled, the same arrangement {@code src/ml10} and {@code src/ml11} use for the
 * transformer adapter. Which one is decided by the target, because the names changed inside this
 * line: 1.20.1 (NeoForge 47.1.106) is still {@code net.minecraftforge.fml} and
 * {@code net.minecraftforge.eventbus.api}, while 1.20.2 (NeoForge 20.2.88) onwards is
 * {@code net.neoforged.fml} and {@code net.neoforged.bus.api}. See the twin for the measurement.</p>
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
