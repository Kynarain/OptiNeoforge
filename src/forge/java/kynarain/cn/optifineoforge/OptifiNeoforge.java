/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;

/**
 * The entry point of the 1.20.x line, for the targets that still use the Forge package names.
 *
 * <p>This file and its twin under {@code src/neoforged/java} declare the same class and exactly one
 * of them is compiled, the same arrangement {@code src/ml10} and {@code src/ml11} already use for
 * the transformer adapter. The reason is not a preference: measured, NeoForge 47.1.106's FML lives
 * in {@code net.minecraftforge.fml} and its event bus is {@code net.minecraftforge:eventbus}, whose
 * package is {@code net.minecraftforge.eventbus.api}. There is no {@code net.neoforged.fml} or
 * {@code net.neoforged.bus} class anywhere in that distribution - the universal jar has no
 * {@code net/neoforged/**} entry at all - so the neoforged spelling of these three imports simply
 * does not resolve against it. 1.20.2 (NeoForge 20.2.88) already uses the neoforged names, so the
 * boundary is between those two versions, not at the start of the line.</p>
 *
 * <p>Nothing else about the skeleton differs: the constructor parameters are the same pair FML
 * injects on both sides of the rename.</p>
 */
@Mod(OptifiNeoforge.MOD_ID)
public final class OptifiNeoforge {
	public static final String MOD_ID = "optifineoforge";

	public OptifiNeoforge(IEventBus modEventBus, ModContainer modContainer) {
		// Intentionally empty for now.
	}
}
