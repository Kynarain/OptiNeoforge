/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.optifine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Puts OptiFine's model-sprite collection on the call path of every
 * {@code ModelManager.discoverModelDependencies} this payload ships.
 *
 * <p>This is the offline twin of the repair the 1.21.x lines run at load time
 * ({@code MemberRestoreTransformer.repairSpriteCollection}), applied here because the FML 10 lines ship finished
 * classes rather than transforming them at run time. The measurement behind it is unchanged:</p>
 *
 * <pre>OptiFine  CustomItems.registerIcons(atlas)
 *            while (!CustomItems.modelSpritesUpdated.get()) { sleep(100); dbg("Waiting for model sprites"); }
 *          CustomItems.collectModelSprites(map)   &lt;- the only writer of that flag
 *            called from ModelManager.discoverModelDependencies, behind a branch</pre>
 *
 * <p>Two things can take that call off the call path on this project's runtimes, and both are measured rather
 * than assumed:</p>
 *
 * <ol>
 *   <li>NeoForge <em>grows the signature</em> of {@code discoverModelDependencies} by a
 *       {@code StandaloneModelLoader$LoadedModels} argument, so a runtime caller compiled against the grown
 *       descriptor never reaches OptiFine's own body. That overload is restored from the runtime by
 *       {@link RestoreMembers} and therefore carries no call at all - measured on 1.21.10 and 1.21.11, where
 *       {@code member-restores.txt} lists
 *       {@code discoverModelDependencies (Ljava/util/Map;...LoadedClientInfos;Lnet/neoforged/...$LoadedModels;)}
 *       and the restored body is a standalone implementation.</li>
 *   <li>In OptiFine's own body the call sits behind {@code if(Config.isCustomItems())}, so it is skipped
 *       whenever that condition does not hold at the moment the method runs.</li>
 * </ol>
 *
 * <p>When neither path sets the flag, nothing fails: the client reaches its loading screen and then sits there,
 * printing {@code [OptiFine] Waiting for model sprites} every five seconds - measured on 1.21.10, where the
 * thread dump puts the waiting thread in
 * {@code CustomItems.registerIcons <- TextureUtils.registerCustomSprites <- TextureAtlas.preStitch <-
 * SpriteLoader.lambda$loadAndStitch$7 <- SimpleReloadInstance.lambda$prepareTasks$0}, and the game never
 * reaches {@code Sound engine started}.</p>
 *
 * <p>The repair inserts the call at the head of <em>every</em> {@code discoverModelDependencies} overload whose
 * first argument is the map, which is the argument both signatures share. Calling it twice costs nothing -
 * {@code collectModelSprites} walks the custom-item list and sets the flag, and its own body is idempotent.</p>
 *
 * <p>Usage: {@code SpriteCollectionRepair <payload jar> [--dry-run]}. ASCII-only file.</p>
 */
public final class SpriteCollectionRepair {
	/** The class whose model discovery is the call path. It is installed over the game's own. */
	private static final String MODEL_MANAGER = "net/minecraft/client/resources/model/ModelManager";
	/** OptiFine's sprite collection, and the class that owns it (loaded from the classes mod file). */
	private static final String CUSTOM_ITEMS = "net/optifine/CustomItems";
	private static final String COLLECT_SPRITES = "collectModelSprites";
	private static final String COLLECT_DESC = "(Ljava/util/Map;)V";

	private SpriteCollectionRepair() {
	}

	public static void main(String[] args) throws Exception {
		boolean dryRun = false;
		List<String> files = new ArrayList<>();
		for(String arg : args) {
			if("--dry-run".equals(arg)) {
				dryRun = true;
			} else {
				files.add(arg);
			}
		}
		if(files.size() != 1) {
			System.err.println("usage: SpriteCollectionRepair <payload jar> [--dry-run]");
			System.exit(2);
		}
		Path payload = Path.of(files.get(0));
		String entryName = "srg/" + MODEL_MANAGER + ".class";
		Set<String> written = new HashSet<>();
		Path temporary = payload.resolveSibling(payload.getFileName() + ".tmp");
		List<String> repaired = new ArrayList<>();
		boolean found = false;
		try(ZipFile source = new ZipFile(payload.toFile());
				ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temporary))) {
			for(java.util.Enumeration<? extends ZipEntry> it = source.entries(); it.hasMoreElements();) {
				ZipEntry entry = it.nextElement();
				String name = entry.getName();
				// Duplicate directory entries exist in these jars (the build steps add META-INF/services/
				// more than once) and ZipOutputStream refuses a repeated name, so the first copy wins.
				if(!written.add(name)) {
					continue;
				}
				if(entry.isDirectory()) {
					out.putNextEntry(new ZipEntry(name));
					out.closeEntry();
					continue;
				}
				byte[] bytes = readAll(source.getInputStream(entry));
				if(entryName.equals(name)) {
					found = true;
					Repair result = repair(bytes);
					bytes = result.bytes();
					repaired.addAll(result.lines());
				}
				out.putNextEntry(new ZipEntry(name));
				out.write(bytes);
				out.closeEntry();
			}
		}
		if(!found) {
			// A payload that does not carry the model manager has no call path to repair; that is a result,
			// not a failure, and the jar is written back untouched.
			Files.deleteIfExists(temporary);
			System.out.println("  " + MODEL_MANAGER + " is not in this payload, so there is no call path to repair");
			return;
		}
		if(dryRun) {
			Files.deleteIfExists(temporary);
		} else {
			Files.move(temporary, payload, StandardCopyOption.REPLACE_EXISTING);
		}
		for(String line : repaired) {
			System.out.println("  " + line);
		}
		System.out.println("sprite collection repair: " + repaired.size() + " overload(s) of "
				+ MODEL_MANAGER + ".discoverModelDependencies" + (dryRun ? " (dry run, not written)" : ""));
	}

	/** The repaired bytes and one line per overload, for the build log. */
	private record Repair(byte[] bytes, List<String> lines) {
	}

	/**
	 * Inserts {@code CustomItems.collectModelSprites(map)} at the head of every overload of the method whose
	 * first argument is that map. A class that has no such overload is written back unchanged and said so,
	 * rather than silently passed over.
	 */
	private static Repair repair(byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		List<String> lines = new ArrayList<>();
		int overloads = 0;
		for(MethodNode method : node.methods) {
			if(!"discoverModelDependencies".equals(method.name) || method.instructions == null) {
				continue;
			}
			if(!hasMapFirstArgument(method)) {
				continue;
			}
			overloads++;
			// Reported, not "fixed": the guarded call OptiFine compiled in is left where it is, so the only
			// difference this makes to a body that already collected sprites is that it now does so first.
			boolean already = false;
			for(AbstractInsnNode instruction : method.instructions) {
				if(instruction instanceof MethodInsnNode call && CUSTOM_ITEMS.equals(call.owner)
						&& COLLECT_SPRITES.equals(call.name)) {
					already = true;
				}
			}
			InsnList head = new InsnList();
			head.add(new VarInsnNode(Opcodes.ALOAD, 0));
			head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CUSTOM_ITEMS, COLLECT_SPRITES, COLLECT_DESC, false));
			method.instructions.insert(head);
			method.maxStack = Math.max(method.maxStack, 1);
			lines.add("collectModelSprites added to the head of discoverModelDependencies" + method.desc
					+ (already ? " (OptiFine's own guarded call is still there)" : " (the restored runtime "
							+ "overload, which had no call at all)"));
		}
		if(overloads == 0) {
			lines.add("no discoverModelDependencies overload with a map first argument: nothing was repaired");
			return new Repair(bytes, lines);
		}
		// COMPUTE_MAXS only: inserting a call at the head does not change any stack map frame, and a full
		// recomputation would rewrite frames that this class needs byte-for-byte as OptiFine compiled them.
		ClassWriter writer = new ClassWriter(new ClassReader(bytes), ClassWriter.COMPUTE_MAXS);
		node.accept(writer);
		return new Repair(writer.toByteArray(), lines);
	}

	/** Whether the first argument is the map, which is the one argument both signatures share. */
	private static boolean hasMapFirstArgument(MethodNode method) {
		Type[] arguments = Type.getArgumentTypes(method.desc);
		return arguments.length >= 1 && "Ljava/util/Map;".equals(arguments[0].getDescriptor());
	}

	private static byte[] readAll(InputStream stream) throws IOException {
		try(stream) {
			return stream.readAllBytes();
		}
	}
}
