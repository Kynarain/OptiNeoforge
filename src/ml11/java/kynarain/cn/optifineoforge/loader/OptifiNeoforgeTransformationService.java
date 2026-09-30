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
import cpw.mods.modlauncher.api.ITransformer;
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
	public List<? extends ITransformer<?>> transformers() {
		LOGGER.info("OptifiNeoforgeTransformationService.transformers");
		// PatchedClassTransformer is first on purpose: on lines whose payload is applied offline it puts
		// OptiFine's compilation of a game class in place, and MemberRestoreTransformer then adds back the
		// members NeoForge's own version has. On 1.21.4 the payload is applied by OptiFine's own
		// transformer instead, and this one registers no targets at all because the index is absent.
		// Anything that has to repair the class after the runtime's members are back belongs in
		// MemberRestoreTransformer rather than in a transformer of its own: ModLauncher consults one
		// transformer per target, so a second one declaring a class this pass already declares is built
		// and then never invoked.
		// ReloadProbeFix is last, after MemberRestoreTransformer, and that position is the whole reason it
		// works at all on 1.21: it instruments ReloadableResourceManager.createReload, OptiFine's payload
		// copy of that class declares no createReload at all (only the leftover lambda body), and the method
		// only appears once the member restore has put the runtime's version back. Ahead of the restore it
		// found nothing to instrument and stayed silent - measured, with the property verified against the
		// constant that enables it. It is a diagnostic and silent unless that property is set, so its
		// position costs nothing on the lines that do not use it.
		return List.of(new PatchedClassTransformer(), new RenderTargetFix(), new ReloadableResourceManagerFix(),
				new TagHelperFix(),
				new PackRootsFix(), new ModelProbeFix(), new NativeImageProbeFix(),
				new SortProbeFix(), new MemberRestoreTransformer(), new ReloadProbeFix());
	}
}
