/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.loader;

import java.util.List;
import java.util.Set;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The loader side of OptifiNeoforge: the repairs that have to happen while classes are being
 * transformed.
 *
 * <p>This is a ModLauncher transformation service rather than a mod entry point, because that is
 * where the work is: OptiFine's own integration is one (see
 * {@code optifine.OptiFineTransformationService}), and the repairs here have to run on the same
 * classes at the same stage. Registering it needs nothing but the service file
 * {@code META-INF/services/cpw.mods.modlauncher.api.ITransformationService} inside a jar in
 * {@code mods/} - a jar does not have to be a mod to be found this way, which is exactly how
 * OptiFine's own jar gets loaded.</p>
 *
 * <p>Nothing here patches the game by itself: the patched classes come from OptiFine. What this
 * service adds is the small set of corrections that only apply once OptiFine is in the picture.</p>
 */
public class OptifiNeoforgeTransformationService implements ITransformationService {
	private static final Logger LOGGER = LogManager.getLogger("OptifiNeoforge");

	@Override
	public String name() {
		return "OptifiNeoforge";
	}

	@Override
	public void initialize(IEnvironment environment) {
		LOGGER.info("OptifiNeoforgeTransformationService.initialize");
	}

	@Override
	public List<Resource> beginScanning(IEnvironment environment) {
		return List.of();
	}

	@Override
	public List<Resource> completeScan(IModuleLayerManager layerManager) {
		layers = layerManager;
		return List.of();
	}

	/** The layer manager, kept so a transformer can inspect the module graph once classes are loading. */
	private static volatile IModuleLayerManager layers;

	static IModuleLayerManager layers() {
		return layers;
	}

	@Override
	public void onLoad(IEnvironment environment, Set<String> otherServices) throws IncompatibleEnvironmentException {
		LOGGER.info("OptifiNeoforgeTransformationService.onLoad, alongside " + otherServices);
	}

	@Override
	public List transformers() {
		LOGGER.info("OptifiNeoforgeTransformationService.transformers");
		// PatchedClassTransformer is first on purpose: it puts OptiFine's compilation of a game class in
		// place, and MemberRestoreTransformer then adds back the members NeoForge's own version has.
		//
		// The raw List is deliberate and measured: it overrides List<ITransformer> under ModLauncher 10
		// and List<? extends ITransformer<?>> under 11, and it was compiled against both jars. The
		// bodies below are loader-neutral; ModLauncherAdapter is the line's own transformer flavour.
		return ModLauncherAdapter.wrap(List.of(new PatchedClassTransformer(), new RenderTargetFix(),
				new ReloadableResourceManagerFix(), new TagHelperFix(),
				new PackRootsFix(), new ReloadProbeFix(), new ModelProbeFix(), new NativeImageProbeFix(),
				new SortProbeFix(), new MemberRestoreTransformer()));
	}
}
