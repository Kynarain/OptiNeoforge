/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.fml10;

import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The marker that gets this jar into FML 10's early-service pass, and nothing else.
 *
 * <p>Measured rather than guessed, and the measurement is the whole reason this class exists:
 * {@code net.neoforged.fml.loading.EarlyServiceDiscovery.SERVICES} is exactly
 * {@code {IModFileCandidateLocator, IModFileReader, IDependencyLocator, GraphicsBootstrapper,
 * ImmediateWindowProvider}} - a {@code ClassProcessor} is <em>not</em> in it. A jar in {@code mods/}
 * that declares only a {@code ClassProcessor} is therefore not pre-loaded, its classes are not on the
 * launch context when {@code FMLLoader.createClassProcessorSet} runs, and its processor is never
 * called: measured on 1.21.11, where the payload jar loaded as a mod file
 * ({@code OptiFine 1.0.0 (optifine)}) and the run reached the title screen with zero
 * {@code [OptiFine]} lines and no processor log at all.</p>
 *
 * <p>This is also what OptiFine's own 26.1.2 build does: its jar declares both
 * {@code ClassProcessor} and {@code IModFileCandidateLocator}, and only the second one is what the
 * early pass looks for. {@link #findCandidates} deliberately locates nothing - the payload jar is
 * found by the ordinary {@code mods/} scan from its own metadata - and says so in the log, so that a
 * line about it is never mistaken for evidence that it discovered a file.</p>
 */
public final class OptifinePayloadLocator implements IModFileCandidateLocator {
	private static final Logger LOGGER = LogManager.getLogger("OptifiNeoforge");

	@Override
	public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
		LOGGER.info("OptifiNeoforge: early service jar recognised; the payload jar itself is discovered"
				+ " from its own metadata, so this locator adds nothing");
	}
}
