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
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Makes OptiFine's {@code ParticleEngine.makeParticle} read the particle provider out of the map the runtime
 * actually has.
 *
 * <p>This is the offline twin of the repair the FML 10 lines run at load time
 * ({@code OptifinePayloadClassProcessor.repairParticleProviderLookup}), applied here because the 26.1.2 line
 * ships finished classes rather than transforming them from this project's own code. The measurement behind it is
 * unchanged - made on 1.21.10, on a multiplayer server, and it is what made the client unusable there: it died a
 * few seconds into the world, the moment rain particles were created:</p>
 *
 * <pre>NoSuchMethodError: 'it.unimi.dsi.fastutil.ints.Int2ObjectMap
 *     net.minecraft.client.particle.ParticleResources.getProviders()'
 *   at ParticleEngine.makeParticle(ParticleEngine.java:76)
 *   at ClientLevel.doAddParticle(...)  &lt;- WeatherEffectRenderer.tickRainParticles</pre>
 *
 * <p>OptiFine's copy of {@code makeParticle} does, in this order:</p>
 *
 * <pre>getfield     ParticleEngine.resourceManager
 * invokevirtual ParticleResources.getProviders()Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;
 * getstatic    BuiltInRegistries.PARTICLE_TYPE
 * invokeinterface Registry.getId(Object)I
 * invokeinterface it/unimi/dsi/fastutil/ints/Int2ObjectMap.get(I)Object
 * checkcast    ParticleProvider</pre>
 *
 * <p>while the runtime's {@code ParticleResources} declares
 * {@code Map&lt;Identifier, ParticleProvider&lt;?&gt;&gt; getProviders()} - the int-keyed view is gone - so the very
 * first instruction of the lookup throws. Measured on 26.1.2 out of
 * {@code minecraft-client-patched-26.1.2.109.jar}: {@code ParticleResources} has a single
 * {@code providers} field of type {@code java.util.Map} and its {@code getProviders} returns that map;
 * the Forge-era {@code getProvider(ParticleType)} OptiFine would otherwise have used through its reflector is
 * gone too (the log on the older line says so itself: "Method not present:
 * net.minecraft.client.particle.ParticleResources.getProvider").</p>
 *
 * <p>The rewrite keeps the lookup and changes its shape: the provider is read from the runtime's map by the
 * particle type's name instead of by registry id. That is the same provider the int-keyed map held - the
 * registry id and the resource name both name the same entry - so OptiFine's custom particle colours
 * ({@code updateTerrainParticleColor}, {@code CustomColors}) stay on the path, which is why the class is not
 * simply dropped in favour of the runtime's copy.</p>
 *
 * <p><strong>One name differs from the load-time repair, and it is a rename rather than a different
 * rewrite.</strong> 26.x renamed {@code net.minecraft.resources.ResourceLocation} to
 * {@code net.minecraft.resources.Identifier}: measured on 26.1.2, the client jar holds one
 * {@code net/minecraft/resources/Identifier.class} and no {@code ResourceLocation} class at all, and the
 * NeoForge-patched {@code Registry} declares {@code Identifier getKey(T)}, which erases to
 * {@code (Ljava/lang/Object;)Lnet/minecraft/resources/Identifier;}. Asking for the
 * {@code ResourceLocation} descriptor the 1.21.x lines use would therefore trade one
 * {@code NoSuchMethodError} for another. Everything else in the rewrite is the load-time repair's, instruction
 * for instruction.</p>
 *
 * <p>The rewrite is checkable against the runtime's own class: NeoForge's patched
 * {@code ParticleEngine.makeParticle} (read out of the same 26.1.2 jar) is exactly</p>
 *
 * <pre>getfield      resourceManager
 * invokevirtual ParticleResources.getProviders()Ljava/util/Map;
 * getstatic     BuiltInRegistries.PARTICLE_TYPE
 * invokeinterface Registry.getKey(Object)Identifier
 * invokeinterface java/util/Map.get(Object)Object
 * checkcast     ParticleProvider</pre>
 *
 * <p>which is what this tool writes into OptiFine's body. OptiFine's own guarded
 * {@code Reflector.ForgeParticleResources_getProvider} call a few instructions later is left untouched.</p>
 *
 * <p>The class this tool rewrites is the class the runtime installs: the 26.1.2 payload declares
 * {@code META-INF/services/net.neoforged.neoforgespi.transformation.ClassProcessor} ->
 * {@code optifine.OptiFineClassProcessor}, and OptiFine's own
 * {@code OptiFineBaseTransformer.transform(ClassNode, String)} replaces the game's class wholesale with
 * {@code srg/&lt;internal name&gt;.class} from this jar (read out of the payload with {@code javap}; the xdelta
 * under {@code patch/srg/} is only the fallback for a name that has no such class file). So repairing this
 * entry is repairing what the game loads. Whether rain particles then work in a real world on 26.1.2 is a
 * separate test that needs the game and is <em>not</em> claimed here.</p>
 *
 * <p>No instruction is inserted or removed, so the stack depth is unchanged - each substituted call takes and
 * returns one slot in the same position ({@code Int2ObjectMap} -&gt; {@code Map}, {@code int} -&gt;
 * {@code Identifier}) - and no stack map frame of {@code makeParticle} names either replaced type (measured on
 * the payload: the frames are an append of {@code ParticleProvider}, a {@code same}, and a
 * {@code same_locals_1_stack_item} of {@code Particle}). The class is therefore written with
 * {@code COMPUTE_MAXS} only, and the frames OptiFine compiled in are copied through unchanged.</p>
 *
 * <p>Usage: {@code ParticleProviderRepair <payload jar> [--dry-run]}. A payload that has no
 * {@code ParticleEngine}, or one whose {@code ParticleEngine} has no method with that triple, is reported on
 * stdout and left exactly as it is. ASCII-only file.</p>
 */
public final class ParticleProviderRepair {
	/** The class whose particle provider lookup is repaired. It is installed over the game's own. */
	private static final String PARTICLE_ENGINE = "net/minecraft/client/particle/ParticleEngine";
	/** The runtime class whose int-keyed provider view was removed. */
	private static final String PARTICLE_RESOURCES = "net/minecraft/client/particle/ParticleResources";
	/** The registry OptiFine asks for the numeric id of a particle type. */
	private static final String REGISTRY = "net/minecraft/core/Registry";
	/** The removed int-keyed map, as it appears in the payload's descriptors. */
	private static final String INT_KEYED_MAP = "it/unimi/dsi/fastutil/ints/Int2ObjectMap";
	/** The map the runtime returns, keyed by the particle type's name. */
	private static final String PROVIDERS_DESC = "()Ljava/util/Map;";
	/** The registry's name lookup, whose erasure carries the 26.x type name. */
	private static final String REGISTRY_LOOKUP_DESC =
			"(Ljava/lang/Object;)Lnet/minecraft/resources/Identifier;";
	/** The map's own get, which takes and returns Object. */
	private static final String MAP_GET_DESC = "(Ljava/lang/Object;)Ljava/lang/Object;";

	private ParticleProviderRepair() {
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
			System.err.println("usage: ParticleProviderRepair <payload jar> [--dry-run]");
			System.exit(2);
		}
		Path payload = Path.of(files.get(0));
		String entryName = "srg/" + PARTICLE_ENGINE + ".class";
		Set<String> written = new HashSet<>();
		Path temporary = payload.resolveSibling(payload.getFileName() + ".tmp");
		List<String> repaired = new ArrayList<>();
		boolean found = false;
		boolean changed = false;
		int stored = 0;
		try(ZipFile source = new ZipFile(payload.toFile());
				ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temporary))) {
			for(java.util.Enumeration<? extends ZipEntry> it = source.entries(); it.hasMoreElements();) {
				ZipEntry entry = it.nextElement();
				String name = entry.getName();
				// Duplicate directory entries exist in some of these jars (the build steps add META-INF/services/
				// more than once) and ZipOutputStream refuses a repeated name, so the first copy wins. Measured on
				// this payload: 9536 entries, none of them a duplicate and none of them a directory.
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
					changed = result.changed();
				}
				if(entry.getMethod() == ZipEntry.STORED) {
					// Reported rather than silently recompressed: a nested jar has to stay STORED for the jar-in-jar
					// loaders, and this writer, like the sprite repair's, deflates everything it copies. Measured on
					// this payload: 0 stored entries.
					stored++;
				}
				out.putNextEntry(new ZipEntry(name));
				out.write(bytes);
				out.closeEntry();
			}
		}
		if(!found) {
			// A payload that does not carry the particle engine has no lookup to repair; that is a result, not a
			// failure, and the jar is written back untouched.
			Files.deleteIfExists(temporary);
			System.out.println("  " + PARTICLE_ENGINE + " is not in this payload, so there is no particle provider "
					+ "lookup to repair");
			return;
		}
		if(dryRun || !changed) {
			// Nothing was repaired means nothing is written: a payload whose makeParticle is not shaped like the
			// one this repair knows is left byte for byte as it was found.
			Files.deleteIfExists(temporary);
		} else {
			Files.move(temporary, payload, StandardCopyOption.REPLACE_EXISTING);
		}
		if(stored > 0) {
			System.out.println("  " + stored + " entries are STORED and were written back deflated");
		}
		for(String line : repaired) {
			System.out.println("  " + line);
		}
		System.out.println(changed
				? "particle provider repair: 1 method of " + PARTICLE_ENGINE
						+ (dryRun ? " (dry run, not written)" : "")
				: "particle provider repair: nothing was repaired, the payload jar is left as it is");
	}

	/** The repaired bytes, one line per finding, and whether anything changed at all. */
	private record Repair(byte[] bytes, List<String> lines, boolean changed) {
	}

	/**
	 * Rewrites the one triple of calls that reads a particle provider by registry id, in the first method that
	 * carries it. A class in which no method carries that triple is written back unchanged and said so, rather
	 * than silently passed over.
	 */
	private static Repair repair(byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		List<String> lines = new ArrayList<>();
		List<String> shape = new ArrayList<>();
		boolean hasMakeParticle = false;
		for(MethodNode method : node.methods) {
			if(method.instructions == null) {
				continue;
			}
			if("makeParticle".equals(method.name)) {
				hasMakeParticle = true;
			}
			MethodInsnNode getProviders = null;
			MethodInsnNode getId = null;
			MethodInsnNode mapGet = null;
			for(AbstractInsnNode instruction : method.instructions) {
				if(!(instruction instanceof MethodInsnNode call)) {
					continue;
				}
				if("getProviders".equals(call.name) && PARTICLE_RESOURCES.equals(call.owner)) {
					shape.add(method.name + " calls ParticleResources.getProviders" + call.desc);
				}
				if("getProviders".equals(call.name) && PARTICLE_RESOURCES.equals(call.owner)
						&& call.desc.endsWith("L" + INT_KEYED_MAP + ";")) {
					getProviders = call;
				} else if("getId".equals(call.name) && REGISTRY.equals(call.owner)) {
					getId = call;
				} else if("get".equals(call.name) && INT_KEYED_MAP.equals(call.owner)) {
					mapGet = call;
				}
			}
			if(getProviders == null || getId == null || mapGet == null) {
				continue;
			}
			getProviders.desc = PROVIDERS_DESC;
			getId.name = "getKey";
			getId.desc = REGISTRY_LOOKUP_DESC;
			mapGet.owner = "java/util/Map";
			mapGet.desc = MAP_GET_DESC;
			lines.add(PARTICLE_ENGINE + "." + method.name + " reads the particle provider through the runtime's "
					+ "Map keyed by the particle type's Identifier, because OptiFine's copy asks for the removed "
					+ "int-keyed ParticleResources.getProviders()");
			// COMPUTE_MAXS only: the substituted calls take and return one slot where the old ones did, so no
			// stack map frame changes and the frames OptiFine compiled in are kept byte for byte.
			ClassWriter writer = new ClassWriter(new ClassReader(bytes), ClassWriter.COMPUTE_MAXS);
			node.accept(writer);
			return new Repair(writer.toByteArray(), lines, true);
		}
		lines.add("no method of " + PARTICLE_ENGINE + " reads the particle provider through "
				+ "ParticleResources.getProviders()/Registry.getId()/Int2ObjectMap.get(), so nothing was repaired "
				+ "and the payload is left as it is");
		if(!hasMakeParticle) {
			lines.add("the class has no makeParticle method at all");
		}
		if(shape.isEmpty()) {
			lines.add("no ParticleResources.getProviders call is present in the class");
		} else {
			lines.addAll(shape);
		}
		return new Repair(bytes, lines, false);
	}

	private static byte[] readAll(InputStream stream) throws IOException {
		try(stream) {
			return stream.readAllBytes();
		}
	}
}
