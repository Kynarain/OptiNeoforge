/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.fml10;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import net.neoforged.neoforgespi.transformation.SimpleClassProcessor;
import net.neoforged.neoforgespi.transformation.SimpleTransformationContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The mount point for the FML 10 lines (1.21.9 / 1.21.10 / 1.21.11), where ModLauncher no longer
 * exists and OptiFine's own jar therefore has nothing to register with.
 *
 * <p>Why this class has to exist at all, measured rather than assumed: FML 10.0.36 has no
 * {@code cpw.mods.modlauncher} classes and no {@code ModDirTransformerDiscoverer} service, and the
 * main class of the 21.11.45 profile is {@code net.neoforged.fml.startup.Client}. OptiFine's
 * 1.21.11 build ({@code OptiFine_1.21.11_HD_U_J9.jar}) declares exactly one service file,
 * {@code META-INF/services/cpw.mods.modlauncher.api.ITransformationService}, so on these versions
 * nothing OptiFine ships can ever be called. Its 26.1.2 build does carry a
 * {@code ClassProcessor}, which is what made that line's mount point unnecessary - this class is
 * that same job for the three versions whose OptiFine builds predate the API.</p>
 *
 * <p>What it installs comes from the offline route, exactly as on 26.1.2: OptiFine ships patch data
 * ({@code patch/srg/**}); the rig applies it to the vanilla archive in advance and ships the
 * finished classes under {@code srg/} inside this same jar, together with the members NeoForge
 * added back, the re-parented hierarchies and the Forge API shims. So at runtime there is no
 * patching to do here: {@link #transform} copies the finished class over the game's own.</p>
 *
 * <p>Registered through {@code META-INF/services/net.neoforged.neoforgespi.transformation.ClassProcessor},
 * which is how FML 10 loads processors from a mod file
 * ({@code FMLLoader.createClassProcessorSet} -> {@code ServiceLoaderUtil.loadServices}).</p>
 */
public final class OptifinePayloadClassProcessor extends SimpleClassProcessor {
	private static final Logger LOGGER = LogManager.getLogger("OptifiNeoforge");

	/** Where the rig puts the finished game classes: OptiFine's own layout, one directory up. */
	private static final String PAYLOAD_ROOT = "srg/";

	/** Where the member restore plan's donor classes sit in this jar, as the rig lays them out. */
	private static final String DONOR_ROOT = "optifineoforge/donors/";

	/** The prefix {@code MemberRestorePlan} gives the synthetic methods that fill restored fields. */
	private static final String INITIALISER_MARKER = "$init$";

	/** Whether a donor method is a field initialiser, whichever build wrote its name. See
	 * kynarain.cn.optifineoforge.optifine.MemberRestorePlan.INITIALISER_MARKER for the measurement: keying
	 * recognition on a package-qualified prefix made the rename to OptifiNeoforge silently stop recognising
	 * the initialisers already on disk, and 1.21.9 died on a null Gui.layerManager. */
	private static boolean isInitialiser(String methodName) {
		return methodName.indexOf(INITIALISER_MARKER) >= 0;
	}

	/** The field an initialiser fills, or null when the name carries no marker. */
	private static String initialisedField(String methodName) {
		int at = methodName.indexOf(INITIALISER_MARKER);
		return at < 0 ? null : methodName.substring(at + INITIALISER_MARKER.length());
	}

	/**
	 * The finished classes, read once. Read from this class's own jar rather than through a
	 * resource lookup, because the payload sits at a path ({@code srg/}) that no package claims and
	 * a jar that is also a mod file is not always reachable by resource name.
	 */
	private static Map<String, byte[]> payload;

	private static int installed;

	private static int keptWhole;

	public OptifinePayloadClassProcessor() {
		LOGGER.info("OptifiNeoforge: OptifinePayloadClassProcessor constructed (FML 10 mount point)");
	}

	@Override
	public ProcessorName name() {
		return new ProcessorName("optifineoforge", "payload");
	}

	@Override
	public Set<Target> targets() {
		Set<Target> result = new TreeSet<>((left, right) -> left.className().compareTo(right.className()));
		for(String name : payload().keySet()) {
			// Binary names, with dots: this is what SimpleClassProcessor.handlesClass compares against
			// (SelectionContext.type().getClassName()) and what SimpleClassProcessor.Target's own
			// validation accepts (its constructor calls ClassDesc.of, which is a binary-name parser).
			// Passing the internal form with slashes threw
			//   IllegalArgumentException: Invalid class name: com/mojang/blaze3d/buffers/GpuBuffer$MappedView
			// out of NameValidation.validateClassName, inside handlesClass, on the very first class FML
			// asked about - so nothing was ever installed. The '$' of a nested class stays as it is:
			// ASM's Type.getClassName() keeps it too.
			result.add(new Target(name.replace('/', '.')));
		}
		// Classes the payload does not carry but a repair has to touch. FML only asks this processor about
		// the names it declares here, so a repair on a class with no payload copy is unreachable otherwise -
		// and net/minecraft/tags/ItemTags is exactly that case: OptiFine resolves a method on it by
		// reflection, and there is no OptiFine copy of the class to install.
		for(String name : REPAIR_ONLY_TARGETS) {
			result.add(new Target(name.replace('/', '.')));
		}
		return result;
	}

	/** Targets this processor repairs without installing anything over them. */
	private static final java.util.Set<String> REPAIR_ONLY_TARGETS =
			java.util.Set.of("net/minecraft/tags/ItemTags");

	@Override
	public void transform(ClassNode node, SimpleTransformationContext context) {
		probeLayers();
		byte[] bytes = payload().get(node.name);
		if(bytes == null) {
			repairLegacyTagCreator(node);
			return;
		}
		ClassNode finished = new ClassNode();
		new ClassReader(bytes).accept(finished, 0);
		if(keepWhole().contains(node.name)) {
			stubMissing(node);
			keptWhole++;
			LOGGER.info("OptiFine payload: " + node.name.replace('/', '.') + " is kept as the runtime's own "
					+ "class (keep plan), not replaced [" + keptWhole + " so far]");
			return;
		}
		copy(finished, node);
		int restored = restoreMembers(node);
		repairFrozenReloadListeners(node);
		repairSpriteCollection(node);
		repairParticleProviderLookup(node);
		installed++;
		LOGGER.info("OptiFine payload: installed " + node.name.replace('/', '.') + " (" + finished.fields.size()
				+ " fields, " + finished.methods.size() + " methods" + (restored == 0 ? "" : ", " + restored
				+ " restored from its donor") + ") [" + installed + " so far]");
	}

	/**
	 * Puts OptiFine's model-sprite collection on the call path unconditionally, which is the repair the
	 * 1.21.8 line carries in {@code MemberRestoreTransformer} for the same symptom.
	 *
	 * <p>Measured on 1.21.9, the client reaches the resource reload, starts it, and then does nothing else
	 * for as long as one is willing to wait - the only line it logs is</p>
	 *
	 * <pre>[OptiFine] Waiting for model sprites   (every 5 s, forever)</pre>
	 *
	 * <p>which is OptiFine's atlas stitch waiting on a flag that its model-sprite collection sets. The
	 * payload's own {@code ModelManager.discoverModelDependencies} does contain the call, but behind a
	 * branch - read out of the payload's bytecode:</p>
	 *
	 * <pre>106: invokestatic net/optifine/Config.isCustomItems:()Z
	 * 109: ifeq 121
	 * 114: invokevirtual ModelDiscovery.resolveCustomModels:()V
	 * 117: aload_0
	 * 118: invokestatic net/optifine/CustomItems.collectModelSprites:(Ljava/util/Map;)V
	 * 121: ...</pre>
	 *
	 * <p>so when {@code isCustomItems()} is false the collection never runs and the wait never ends. The
	 * call is put at the head of the method instead, where nothing can branch around it;
	 * {@code collectModelSprites} only walks a list and sets that flag, so running it in addition to the
	 * payload's own call costs nothing. The receiver is local 0 for a static method and local 1 for an
	 * instance one, the method being static here (checked, not assumed - the 1.21.8 line's version pushes
	 * local 0 unconditionally, which is only right while the method is static).</p>
	 */
	private static void repairSpriteCollection(ClassNode installed) {
		if(!"net/minecraft/client/resources/model/ModelManager".equals(installed.name)) {
			return;
		}
		int repaired = 0;
		for(MethodNode method : installed.methods) {
			if(!"discoverModelDependencies".equals(method.name) || method.instructions == null) {
				continue;
			}
			org.objectweb.asm.Type[] arguments = org.objectweb.asm.Type.getArgumentTypes(method.desc);
			if(arguments.length < 1 || !"Ljava/util/Map;".equals(arguments[0].getDescriptor())) {
				continue;
			}
			org.objectweb.asm.tree.InsnList call = new org.objectweb.asm.tree.InsnList();
			call.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD,
					(method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1));
			call.add(new org.objectweb.asm.tree.MethodInsnNode(Opcodes.INVOKESTATIC, "net/optifine/CustomItems",
					"collectModelSprites", "(Ljava/util/Map;)V", false));
			method.instructions.insert(call);
			method.maxStack = Math.max(method.maxStack, 1);
			repaired++;
		}
		if(repaired > 0) {
			LOGGER.info("OptiFine payload: OptiFine's model sprite collection is called unconditionally from "
					+ repaired + " discoverModelDependencies overload(s) of " + installed.name.replace('/', '.')
					+ ", because the payload's own call sits behind Config.isCustomItems()");
		} else {
			LOGGER.warn("OptiFine payload: no discoverModelDependencies with a Map first argument in "
					+ installed.name.replace('/', '.') + "; OptiFine's sprite collection stays off the call path "
					+ "and the atlas stitch will wait for it forever");
		}
	}

	/**
	 * Rewrites the one call in OptiFine's {@code ParticleEngine} that asks the runtime for a particle provider in
	 * a shape the runtime no longer has.
	 *
	 * <p>Measured on 1.21.10, on a multiplayer server, and it is what made the client unusable there - it died a
	 * few seconds into the world, the moment rain particles were created:</p>
	 *
	 * <pre>NoSuchMethodError: 'it.unimi.dsi.fastutil.ints.Int2ObjectMap
	 *     net.minecraft.client.particle.ParticleResources.getProviders()'
	 *   at ParticleEngine.makeParticle(ParticleEngine.java:76)
	 *   at ClientLevel.doAddParticle(...)  &lt;- WeatherEffectRenderer.tickRainParticles</pre>
	 *
	 * <p>OptiFine's copy of {@code makeParticle} does</p>
	 *
	 * <pre>getfield ParticleResources
	 * invokevirtual ParticleResources.getProviders()Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;
	 * getstatic    BuiltInRegistries.PARTICLE_TYPE
	 * invokeinterface Registry.getId(Object)I
	 * invokeinterface Int2ObjectMap.get(I)Object</pre>
	 *
	 * <p>while NeoForge's {@code ParticleResources} declares
	 * {@code Map&lt;ResourceLocation, ParticleProvider&lt;?&gt;&gt; getProviders()} - the int-keyed view is gone,
	 * and so is the Forge-era {@code getProvider(ParticleType)} that OptiFine would otherwise have used through
	 * its reflector (the log says so itself: "Method not present:
	 * net.minecraft.client.particle.ParticleResources.getProvider").</p>
	 *
	 * <p>The rewrite keeps the lookup and changes its shape: the provider is read from the runtime's map by the
	 * particle type's {@code ResourceLocation} instead of by registry id. That is the same provider the
	 * int-keyed map held - the registry id and the resource location name the same entry - so OptiFine's custom
	 * particle colours ({@code updateTerrainParticleColor}, {@code CustomColors}) stay on the path, which is why
	 * the class is not simply dropped for the runtime's copy.</p>
	 */
	private static void repairParticleProviderLookup(ClassNode installed) {
		if(!"net/minecraft/client/particle/ParticleEngine".equals(installed.name)) {
			return;
		}
		for(MethodNode method : installed.methods) {
			if(method.instructions == null) {
				continue;
			}
			org.objectweb.asm.tree.MethodInsnNode getProviders = null;
			org.objectweb.asm.tree.MethodInsnNode getId = null;
			org.objectweb.asm.tree.MethodInsnNode mapGet = null;
			for(org.objectweb.asm.tree.AbstractInsnNode instruction = method.instructions.getFirst();
					instruction != null; instruction = instruction.getNext()) {
				if(!(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call)) {
					continue;
				}
				if("getProviders".equals(call.name)
						&& "net/minecraft/client/particle/ParticleResources".equals(call.owner)
						&& call.desc.endsWith("Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;")) {
					getProviders = call;
				} else if("getId".equals(call.name) && "net/minecraft/core/Registry".equals(call.owner)) {
					getId = call;
				} else if("get".equals(call.name) && "it/unimi/dsi/fastutil/ints/Int2ObjectMap".equals(call.owner)) {
					mapGet = call;
				}
			}
			if(getProviders == null || getId == null || mapGet == null) {
				continue;
			}
			getProviders.desc = "()Ljava/util/Map;";
			getId.name = "getKey";
			// The return type is read from the line, never written here. A resource location changed name inside
			// this group's own span - 1.21.9 and 1.21.10 call it net.minecraft.resources.ResourceLocation and
			// 1.21.11 renamed it net.minecraft.resources.Identifier - and the first version of this repair
			// hardcoded ResourceLocation. Measured 2026-09-24 on 1.21.11, where every particle then died with
			//   NoSuchMethodError: 'net.minecraft.resources.ResourceLocation
			//       net.minecraft.core.Registry.getKey(java.lang.Object)'
			//     at ParticleEngine.makeParticle(ParticleEngine.java:74)
			//     at ClientLevel.doAddParticle(...)  <- GlowSquid.aiStep
			// because that runtime's Registry.getKey returns Identifier. The name arrives in
			// /optifineoforge/runtime-location.txt, which build-fml10-payload.ps1 writes from the runtime jar;
			// the payload's own copy of the class names neither, so it cannot be discovered from the class.
			getId.desc = "(Ljava/lang/Object;)L" + locationClass() + ";";
			mapGet.owner = "java/util/Map";
			mapGet.desc = "(Ljava/lang/Object;)Ljava/lang/Object;";
			LOGGER.info("OptiFine payload: " + installed.name.replace('/', '.') + "." + method.name
					+ " reads the particle provider through the runtime's Map keyed by resource location, because "
					+ "OptiFine's copy asks for the removed int-keyed getProviders()");
			return;
		}
		LOGGER.warn("OptiFine payload: " + installed.name.replace('/', '.') + " has no makeParticle shaped like the "
				+ "one this repair knows; its particle provider lookup is left as OptiFine compiled it, which "
				+ "throws NoSuchMethodError on the first particle");
	}

	/**
	 * One repair, on one class, for an ordering conflict between NeoForge and OptiFine.
	 *
	 * <p>Measured on 1.21.9: NeoForge collects the client's reload listeners through
	 * {@code AddClientReloadListenersEvent} inside {@code Minecraft}'s constructor, and the sorted result it
	 * stores is <em>unmodifiable</em> - {@code ReloadListenerSort.sort} ends in
	 * {@code Collections.unmodifiableList} (read out of {@code neoforge-21.9.16-beta-universal.jar}) and
	 * {@code ReloadableResourceManager.updateListenersFrom} assigns it straight to the field. OptiFine's own
	 * patch registers a listener a few lines later in the same constructor, from the call it injects into
	 * {@code Window.setDefaultErrorCallback}, so it lands on the frozen list:</p>
	 *
	 * <pre>UnsupportedOperationException
	 *   at java.util.Collections$UnmodifiableCollection.add
	 *   at net.minecraft.server.packs.resources.ReloadableResourceManager.registerReloadListener(...:43)
	 *   at net.optifine.util.TextureUtils.registerResourceListener(TextureUtils.java:412)
	 *   at com.mojang.blaze3d.platform.Window.setDefaultErrorCallback(Window.java:307)
	 *   at net.minecraft.client.Minecraft.&lt;init&gt;(Minecraft.java:669)</pre>
	 *
	 * <p>Nothing registers a listener after that point in NeoForge itself, which is why the freeze is
	 * invisible there and fatal here. The repair is at the freezing site rather than in
	 * {@code registerReloadListener}: the list NeoForge sorted is stored wrapped in a mutable copy, so the
	 * field keeps the contract vanilla's own constructor gives it - a list one may add to - while the
	 * ordering NeoForge computed is untouched, and the bytecode of the method that does the sorting is not
	 * rewritten at all. {@code new ArrayList&lt;&gt;(list)} on the stack is
	 * {@code NEW, DUP_X1, SWAP, INVOKESPECIAL}.</p>
	 *
	 * <p>The field is assignable here because this processor already clears {@code final} on a field the
	 * runtime's copy declares non-final, which it does for this one: the payload compiled
	 * {@code listeners} as {@code final} and NeoForge's class does not.</p>
	 */
	private static void repairFrozenReloadListeners(ClassNode installed) {
		if(!"net/minecraft/server/packs/resources/ReloadableResourceManager".equals(installed.name)) {
			return;
		}
		for(MethodNode method : installed.methods) {
			if(!"updateListenersFrom".equals(method.name) || method.instructions == null) {
				continue;
			}
			for(org.objectweb.asm.tree.AbstractInsnNode instruction = method.instructions.getFirst();
					instruction != null; instruction = instruction.getNext()) {
				if(!(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call)
						|| call.getOpcode() != Opcodes.INVOKESTATIC
						|| !"net/neoforged/neoforge/resource/ReloadListenerSort".equals(call.owner)
						|| !"sort".equals(call.name)) {
					continue;
				}
				org.objectweb.asm.tree.InsnList wrap = new org.objectweb.asm.tree.InsnList();
				wrap.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, "java/util/ArrayList"));
				wrap.add(new org.objectweb.asm.tree.InsnNode(Opcodes.DUP_X1));
				wrap.add(new org.objectweb.asm.tree.InsnNode(Opcodes.SWAP));
				wrap.add(new org.objectweb.asm.tree.MethodInsnNode(Opcodes.INVOKESPECIAL, "java/util/ArrayList",
						"<init>", "(Ljava/util/Collection;)V", false));
				method.instructions.insert(call, wrap);
				LOGGER.info("OptiFine payload: " + installed.name.replace('/', '.')
						+ ".updateListenersFrom stores the sorted listeners in a mutable copy, because "
						+ "NeoForge's sort returns an unmodifiable list and OptiFine registers a listener "
						+ "after it");
				return;
			}
			LOGGER.warn("OptiFine payload: " + installed.name.replace('/', '.')
					+ ".updateListenersFrom does not call ReloadListenerSort.sort as this repair expects; the "
					+ "listener list stays frozen");
			return;
		}
	}

	/**
	 * Puts back the two-argument {@code ItemTags.create(String, String)} that OptiFine resolves by reflection.
	 *
	 * <p>Measured on 1.21.9, and every step of this was read rather than guessed. OptiFine's {@code Reflector}
	 * holds</p>
	 *
	 * <pre>ForgeItemTags         = new ReflectorClass(ItemTags.class)
	 * ForgeItemTags_create  = ForgeItemTags.makeMethod("create", String.class, String.class)</pre>
	 *
	 * <p>and OptiFine's compiled {@code DyeColor} constructor calls it for every colour:</p>
	 *
	 * <pre>47: getstatic Reflector.ForgeItemTags_create
	 * 57: ldc "forge"                       // the namespace Forge used
	 * 70: invokevirtual ReflectorMethod.call([Object])   // null when the method is not there
	 * 76: putfield dyesTag:Lnet/minecraft/tags/TagKey;</pre>
	 *
	 * <p>but the 1.21.9 runtime has only {@code create(ResourceLocation)}, so the reflector reports
	 * "Method not present: net.minecraft.tags.ItemTags.create", the call returns null, and every
	 * {@code DyeColor.dyesTag} is null. That is what kills NeoForge's own mod construction, several steps
	 * later and in a class of its own:</p>
	 *
	 * <pre>663: putstatic Tags$Items.DYES_BLACK   (= DyeColor.BLACK.getTag())
	 * NullPointerException: Cannot invoke "net.minecraft.tags.TagKey.toString()" because "tag2" is null
	 *   at net.neoforged.neoforge.common.TagConventionLogWarning.createForgeMapEntry(...:556)
	 *   at net.neoforged.neoforge.common.TagConventionLogWarning.&lt;clinit&gt;(...:201)
	 *   at net.neoforged.neoforge.common.NeoForgeMod.&lt;init&gt;(NeoForgeMod.java:585)</pre>
	 *
	 * <p>{@code MissingTargets --stub} cannot see this one, and that is why the plans came back clean: the
	 * reference is a string in a reflector field, not a bytecode reference to a game member. So the method is
	 * generated here. The namespace is mapped the way the ModLauncher lines' {@code ConventionTags} maps it -
	 * NeoForge renamed the convention namespace from {@code forge} to {@code c} in 1.21, and without that the
	 * tag would be a different, empty tag rather than the one NeoForge's own {@code Tags} class holds.</p>
	 */
	private static void repairLegacyTagCreator(ClassNode installed) {
		if(!"net/minecraft/tags/ItemTags".equals(installed.name)) {
			return;
		}
		String legacy = "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/tags/TagKey;";
		if(hasMethod(installed.methods, "create", legacy)) {
			return;
		}
		// The delegate is found rather than named, because the class it takes changed name inside this line's
		// own span: 1.21.9 has create(ResourceLocation), 1.21.11 has create(Identifier). The first version of
		// this repair hardcoded ResourceLocation and therefore did nothing on 1.21.11, where it said so itself -
		//   OptiFine payload: net.minecraft.tags.ItemTags has no create(ResourceLocation) to delegate to;
		//   OptiFine's reflective tag lookup stays unresolved and every DyeColor tag stays null
		// - and the client then died in NeoForge's TagConventionLogWarning exactly as 1.21.9 had.
		MethodNode delegate = null;
		for(MethodNode method : installed.methods) {
			if(!"create".equals(method.name) || (method.access & Opcodes.ACC_STATIC) == 0) {
				continue;
			}
			if(org.objectweb.asm.Type.getArgumentTypes(method.desc).length == 1
					&& method.desc.endsWith(")Lnet/minecraft/tags/TagKey;")) {
				delegate = method;
				break;
			}
		}
		if(delegate == null) {
			LOGGER.warn("OptiFine payload: " + installed.name.replace('/', '.') + " has no one-argument static "
					+ "create to delegate to; OptiFine's reflective tag lookup stays unresolved and every "
					+ "DyeColor tag stays null");
			return;
		}
		String location = org.objectweb.asm.Type.getArgumentTypes(delegate.desc)[0].getInternalName();
		MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create", legacy, null, null);
		org.objectweb.asm.tree.LabelNode useAsIs = new org.objectweb.asm.tree.LabelNode();
		org.objectweb.asm.tree.LabelNode call = new org.objectweb.asm.tree.LabelNode();
		method.instructions.add(new org.objectweb.asm.tree.LdcInsnNode("forge"));
		method.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
		method.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String",
				"equals", "(Ljava/lang/Object;)Z", false));
		method.instructions.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFEQ, useAsIs));
		method.instructions.add(new org.objectweb.asm.tree.LdcInsnNode("c"));
		method.instructions.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.GOTO, call));
		method.instructions.add(useAsIs);
		method.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
		method.instructions.add(call);
		method.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
		method.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(Opcodes.INVOKESTATIC, location,
				"fromNamespaceAndPath", "(Ljava/lang/String;Ljava/lang/String;)L" + location + ";", false));
		method.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(Opcodes.INVOKESTATIC, installed.name,
				"create", "(L" + location + ";)Lnet/minecraft/tags/TagKey;", false));
		method.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ARETURN));
		method.maxStack = 2;
		method.maxLocals = 2;
		installed.methods.add(method);
		LOGGER.info("OptiFine payload: added the reflective tag creator " + installed.name.replace('/', '.')
				+ ".create(String, String) delegating to " + location.replace('/', '.')
				+ ".fromNamespaceAndPath + create, because OptiFine resolves it by name; \"forge\" is mapped onto "
				+ "NeoForge's \"c\"");
	}

	/**
	 * The classes the keep plan names with the whole-class form ({@code owner<TAB>*}), read once.
	 *
	 * <p>This is the plan the 1.20.x and 1.21.x lines already carry, and for the reason {@code PayloadDrift}
	 * writes into the file itself: a class whose compile-time constant the payload inlined into its own bodies
	 * cannot be reconciled member by member, because the inlined number is not a member. Measured on 1.21.9,
	 * the reload advanced to exactly this and stopped:</p>
	 *
	 * <pre>CompletionException: ExceptionInInitializerError
	 *   at …ModelDiscovery$ModelWrapper.&lt;clinit&gt;(ModelDiscovery.java:200)
	 *   at …ModelDiscovery.&lt;init&gt;(ModelDiscovery.java:42)
	 *   at …ModelManager.discoverModelDependencies(ModelManager.java:201)</pre>
	 *
	 * <p>and {@code PayloadDrift} names the constant: {@code SLOT_COUNT: payload=7 runtime=8}. The runtime's
	 * class is therefore loaded whole, and the log line says so per class, including the price: keeping the
	 * runtime's class also drops whatever OptiFine changed in it.</p>
	 */
	private static java.util.Set<String> keepWhole() {
		if(keepPlan != null) {
			return keepPlan;
		}
		java.util.Set<String> result = new java.util.LinkedHashSet<>();
		try(InputStream stream = OptifinePayloadClassProcessor.class
				.getResourceAsStream("/optifineoforge/keep-runtime.txt")) {
			if(stream == null) {
				LOGGER.info("OptiFine payload: no /optifineoforge/keep-runtime.txt in this jar; every class the "
						+ "payload has is installed");
			} else {
				java.io.BufferedReader reader = new java.io.BufferedReader(
						new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
				String line;
				while((line = reader.readLine()) != null) {
					if(line.startsWith("#") || line.isBlank()) {
						continue;
					}
					String[] parts = line.split("\\s+");
					if(parts.length == 2 && "*".equals(parts[1])) {
						result.add(parts[0]);
					}
				}
				LOGGER.info("OptiFine payload: keep plan: " + result.size() + " class(es) stay the runtime's own");
			}
		} catch(Throwable t) {
			LOGGER.warn("OptiFine payload: could not read the keep plan: " + t);
		}
		keepPlan = result;
		return keepPlan;
	}

	private static java.util.Set<String> keepPlan;

	/**
	 * The member restore pass, which is the half that keeping runtime members does not cover.
	 *
	 * <p>Measured on 1.21.9, and it is the reason this exists: {@code Gui} has a NeoForge field
	 * ({@code layerManager}, in the repository's own {@code member-restores.txt} for this line) that
	 * OptiFine's copy does not declare. Keeping the runtime's members puts the field back, and then
	 * NeoForge's own {@code initModdedOverlays} - also a kept runtime member - reads it and finds
	 * {@code null}:</p>
	 *
	 * <pre>NullPointerException: Cannot invoke "…GuiLayerManager.initModdedLayers()"
	 *     because "this.layerManager" is null
	 *   at net.minecraft.client.gui.Gui.initModdedOverlays(Gui.java:1604)
	 *   at net.neoforged.neoforge.client.ClientHooks.initClientHooks(...:984)</pre>
	 *
	 * <p>because the assignment NeoForge made lives in <em>its</em> constructor, and the payload's
	 * constructor replaced it. The repo's answer, from the {@code MemberRestoreTransformer} the
	 * ModLauncher lines use, is a plan plus donor classes: the plan names the members, and each donor
	 * carries them together with synthetic initialisers whose bodies are inlined here - static ones into
	 * the class's own {@code <clinit>} (a static final field may only be assigned from its own class's
	 * initialiser, so calling a helper is rejected with "Update to static final field"), instance ones
	 * into every constructor that does not assign that field itself.</p>
	 *
	 * <p>Returns how many members came from the donor, for the log line.</p>
	 */
	private static int restoreMembers(ClassNode installed) {
		if(!restoreTargets().contains(installed.name)) {
			return 0;
		}
		ClassNode donor = donor(installed.name);
		if(donor == null) {
			LOGGER.warn("OptiFine payload: no donor class for " + installed.name.replace('/', '.')
					+ "; its dropped members stay missing");
			return 0;
		}
		int restored = 0;
		boolean targetIsInterface = (installed.access & Opcodes.ACC_INTERFACE) != 0;
		for(FieldNode field : donor.fields) {
			if(hasField(installed.fields, field.name, field.desc)) {
				continue;
			}
			int access = field.access;
			if(targetIsInterface) {
				// The donor is written as a plain class, so its fields arrive without the rules that apply
				// in an interface - and final is cleared on purpose, because an initialiser fills them -
				// so the donor's own flags cannot be copied across as they are. Whatever the donor says,
				// an interface's fields have to be public static final or the JVM rejects the class
				// outright ("ClassFormatError: Illegal field modifiers"), which the 1.21.8 line measured.
				access = (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED))
						| Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
			}
			installed.fields.add(new FieldNode(access, field.name, field.desc, field.signature, field.value));
			restored++;
		}
		java.util.List<MethodNode> staticInitialisers = new java.util.ArrayList<>();
		java.util.List<MethodNode> initialisers = new java.util.ArrayList<>();
		for(MethodNode method : donor.methods) {
			if(isInitialiser(method.name)) {
				// The initialisers are *inlined* and never added to the class, and that is measured rather
				// than stylistic: they assign fields, and an instance field that is final may only be
				// assigned from a constructor. Calling one instead produced
				//
				//   IllegalAccessError: Update to non-static final field
				//     com.mojang.blaze3d.opengl.GlDevice.deviceProperties attempted from a different method
				//     (optifineoforge$init$deviceProperties) than the initializer method <init>
				//   at com.mojang.blaze3d.opengl.GlDevice.optifineoforge$init$deviceProperties(GlDevice.java)
				//   at com.mojang.blaze3d.opengl.GlDevice.<init>(GlDevice.java:95)
				//
				// on 1.21.9, which killed the client inside Minecraft's constructor. Inlined into the
				// constructor the same PUTFIELD is legal, and the receiver lines up without any rewriting:
				// the donor's initialiser takes the object as its local 0, and so does <init>.
				if(targetIsInterface) {
					// An interface is the one class these must not appear in at all: its fields are final
					// and its initialiser already fills them.
					continue;
				}
				if("()V".equals(method.desc)) {
					staticInitialisers.add(method);
				} else {
					initialisers.add(method);
				}
				continue;
			}
			if("<clinit>".equals(method.name)) {
				// The donor's static initialiser is NOT copied, and that was measured rather than cautious.
				// It initialises the donor's fields as the donor's class declares them, which is not how this
				// class declares them: measured on 1.21.9, BreezeWindLayer's payload copy has
				//
				//   private net.minecraft.resources.ResourceLocation TEXTURE_LOCATION;   (instance)
				//
				// while the runtime's copy has the same name and descriptor but static, and the runtime's
				// <clinit> reads it with GETSTATIC - so installing that initialiser next to the payload's
				// instance field made the class's own initialisation fail with
				//
				//   IncompatibleClassChangeError: Expected static field
				//     net.minecraft.client.renderer.entity.layers.BreezeWindLayer.TEXTURE_LOCATION
				//   at net.minecraft.client.renderer.entity.layers.BreezeWindLayer.<clinit>(BreezeWindLayer.java:18)
				//
				// during EntityRenderers.createEntityRenderers, which killed the resource reload. The static
				// values that do have to come back are the ones the plan names, and those arrive as the
				// initialiser methods handled below.
				LOGGER.info("OptiFine payload: " + installed.name.replace('/', '.') + " has no static "
						+ "initialiser of its own and the donor's is not copied (it initialises the runtime's "
						+ "field shapes, not this class's)");
				continue;
			}
			if(!hasMethod(installed.methods, method.name, method.desc)) {
				MethodNode copy = new MethodNode(method.access, method.name, method.desc, method.signature,
						method.exceptions == null ? null : method.exceptions.toArray(new String[0]));
				method.accept(copy);
				installed.methods.add(copy);
				restored++;
			}
		}
		inlineStaticInitialisers(installed, staticInitialisers);
		inlineInstanceInitialisers(installed, initialisers);
		if(restored > 0) {
			LOGGER.info("OptiFine payload: restored " + restored + " members in "
					+ installed.name.replace('/', '.') + " from its donor");
		}
		return restored;
	}

	/** Writes the donor's static initialiser bodies into the class's own {@code <clinit>}. */
	private static void inlineStaticInitialisers(ClassNode installed, java.util.List<MethodNode> initialisers) {
		if(initialisers.isEmpty()) {
			return;
		}
		MethodNode clinit = null;
		for(MethodNode method : installed.methods) {
			if("<clinit>".equals(method.name)) {
				clinit = method;
				break;
			}
		}
		org.objectweb.asm.tree.InsnList values = body(initialisers.get(0));
		if(values == null) {
			return;
		}
		for(int index = 1; index < initialisers.size(); index++) {
			org.objectweb.asm.tree.InsnList more = body(initialisers.get(index));
			if(more == null) {
				return;
			}
			values.add(more);
		}
		if(clinit == null) {
			clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
			clinit.instructions.add(values);
			clinit.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
			clinit.maxStack = 8;
			clinit.maxLocals = 0;
			installed.methods.add(clinit);
		} else {
			org.objectweb.asm.tree.AbstractInsnNode last = null;
			for(org.objectweb.asm.tree.AbstractInsnNode instruction = clinit.instructions.getFirst();
					instruction != null; instruction = instruction.getNext()) {
				if(instruction.getOpcode() == Opcodes.RETURN) {
					last = instruction;
				}
			}
			if(last == null) {
				LOGGER.warn("OptiFine payload: no return in the static initialiser of "
						+ installed.name.replace('/', '.') + "; " + initialisers.size() + " restored fields stay "
						+ "at their defaults");
				return;
			}
			clinit.instructions.insertBefore(last, values);
			clinit.maxStack = Math.max(clinit.maxStack, 8);
		}
		LOGGER.info("OptiFine payload: initialised " + initialisers.size() + " restored static fields in "
				+ installed.name.replace('/', '.'));
	}

	/**
	 * Writes the donor's instance initialiser bodies into every constructor that does not assign that field
	 * itself. A constructor restored from the donor already stored the field, and inlining the initialiser
	 * after it would overwrite the value it just stored with the initialiser's default.
	 */
	private static void inlineInstanceInitialisers(ClassNode installed, java.util.List<MethodNode> initialisers) {
		if(initialisers.isEmpty()) {
			return;
		}
		int constructors = 0;
		for(MethodNode constructor : installed.methods) {
			if(!"<init>".equals(constructor.name) || constructor.instructions == null) {
				continue;
			}
			java.util.Set<String> assigned = assignedFields(constructor, installed.name);
			org.objectweb.asm.tree.InsnList values = new org.objectweb.asm.tree.InsnList();
			int wanted = 0;
			for(MethodNode initialiser : initialisers) {
				String field = initialisedField(initialiser.name);
				if(assigned.contains(field)) {
					continue;
				}
				org.objectweb.asm.tree.InsnList more = body(initialiser);
				if(more == null) {
					return;
				}
				values.add(more);
				wanted++;
			}
			if(wanted == 0) {
				continue;
			}
			for(org.objectweb.asm.tree.AbstractInsnNode instruction = constructor.instructions.getFirst();
					instruction != null; instruction = instruction.getNext()) {
				if(instruction.getOpcode() == Opcodes.RETURN) {
					constructor.instructions.insertBefore(instruction, values);
				}
			}
			constructors++;
		}
		LOGGER.info("OptiFine payload: initialised " + initialisers.size() + " restored instance fields in "
				+ constructors + " constructor(s) of " + installed.name.replace('/', '.'));
	}

	/** One initialiser body, with its RETURN dropped, or null when an instruction could not be copied. */
	private static org.objectweb.asm.tree.InsnList body(MethodNode initialiser) {
		org.objectweb.asm.tree.InsnList values = new org.objectweb.asm.tree.InsnList();
		if(initialiser.instructions == null) {
			return values;
		}
		for(org.objectweb.asm.tree.AbstractInsnNode instruction = initialiser.instructions.getFirst();
				instruction != null; instruction = instruction.getNext()) {
			if(instruction.getOpcode() == Opcodes.RETURN) {
				continue;
			}
			org.objectweb.asm.tree.AbstractInsnNode copy = copyInstruction(instruction);
			if(copy == null) {
				LOGGER.warn("OptiFine payload: cannot inline " + initialiser.name
						+ ": an instruction of kind " + instruction.getClass().getSimpleName()
						+ " has no copy here; the field stays at its default");
				return null;
			}
			values.add(copy);
		}
		return values;
	}

	/** A copy of one instruction, or null for a kind this pass does not carry across. */
	private static org.objectweb.asm.tree.AbstractInsnNode copyInstruction(
			org.objectweb.asm.tree.AbstractInsnNode instruction) {
		if(instruction instanceof org.objectweb.asm.tree.InsnNode plain) {
			return new org.objectweb.asm.tree.InsnNode(plain.getOpcode());
		}
		if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call) {
			return new org.objectweb.asm.tree.MethodInsnNode(call.getOpcode(), call.owner, call.name, call.desc,
					call.itf);
		}
		if(instruction instanceof org.objectweb.asm.tree.FieldInsnNode field) {
			return new org.objectweb.asm.tree.FieldInsnNode(field.getOpcode(), field.owner, field.name,
					field.desc);
		}
		if(instruction instanceof org.objectweb.asm.tree.TypeInsnNode type) {
			return new org.objectweb.asm.tree.TypeInsnNode(type.getOpcode(), type.desc);
		}
		if(instruction instanceof org.objectweb.asm.tree.LdcInsnNode ldc) {
			return new org.objectweb.asm.tree.LdcInsnNode(ldc.cst);
		}
		if(instruction instanceof org.objectweb.asm.tree.IntInsnNode integer) {
			return new org.objectweb.asm.tree.IntInsnNode(integer.getOpcode(), integer.operand);
		}
		if(instruction instanceof org.objectweb.asm.tree.VarInsnNode local) {
			// Only local 0, which is the object in both the donor's initialiser and the constructor it is
			// inlined into, so the receiver lines up. Any other local belongs to the initialiser's own frame
			// and would collide with the constructor's parameters once inlined - measured on 1.21.9, where
			// omitting this kind altogether is what left Gui.layerManager null:
			//   cannot inline optifineoforge$init$layerManager: an instruction of kind VarInsnNode has no copy
			// The bodies the plan writes need exactly "aload_0; <value>; putfield", so this is the whole of
			// what is required; anything else is refused rather than silently miscompiled.
			if(local.var != 0) {
				return null;
			}
			return new org.objectweb.asm.tree.VarInsnNode(local.getOpcode(), local.var);
		}
		if(instruction instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode dynamic) {
			// A value built through a lambda is still one expression: SingleVariant$Unbaked.MAP_CODEC is
			// Variant.MAP_CODEC.xmap(lambda, lambda), and leaving the field at its default made every
			// blockstate in the game fail to load on 1.21.8, because NeoForge's own
			// BlockStateModel$Unbaked.CODEC falls back to it. The bootstrap method and its arguments are
			// shared rather than copied - they are immutable.
			return new org.objectweb.asm.tree.InvokeDynamicInsnNode(dynamic.name, dynamic.desc, dynamic.bsm,
					dynamic.bsmArgs.clone());
		}
		return null;
	}

	/** The names of the fields one constructor assigns on its own class. */
	private static java.util.Set<String> assignedFields(MethodNode constructor, String owner) {
		java.util.Set<String> assigned = new java.util.LinkedHashSet<>();
		for(org.objectweb.asm.tree.AbstractInsnNode instruction = constructor.instructions.getFirst();
				instruction != null; instruction = instruction.getNext()) {
			if(instruction instanceof org.objectweb.asm.tree.FieldInsnNode field
					&& field.getOpcode() == Opcodes.PUTFIELD && owner.equals(field.owner)) {
				assigned.add(field.name);
			}
		}
		return assigned;
	}

	/** The classes the member restore plan names, read once. */
	private static java.util.Set<String> restoreTargets() {
		if(restoreTargets != null) {
			return restoreTargets;
		}
		java.util.Set<String> result = new java.util.LinkedHashSet<>();
		try(InputStream stream = OptifinePayloadClassProcessor.class
				.getResourceAsStream("/optifineoforge/member-restores.txt")) {
			if(stream == null) {
				LOGGER.info("OptiFine payload: no /optifineoforge/member-restores.txt in this jar; no member is "
						+ "restored from a donor");
			} else {
				java.io.BufferedReader reader = new java.io.BufferedReader(
						new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
				int members = 0;
				String line;
				while((line = reader.readLine()) != null) {
					String[] parts = line.split("\\s+");
					if(parts.length >= 4 && ("F".equals(parts[0]) || "M".equals(parts[0]))) {
						result.add(parts[1]);
						members++;
					}
				}
				LOGGER.info("OptiFine payload: member restore plan: " + members + " members across "
						+ result.size() + " classes");
			}
		} catch(Throwable t) {
			LOGGER.warn("OptiFine payload: could not read the member restore plan: " + t);
		}
		restoreTargets = result;
		return restoreTargets;
	}

	private static java.util.Set<String> restoreTargets;

	/** The donor class for one name, from this jar, or null when the plan has none for it. */
	private static ClassNode donor(String name) {
		try {
			java.util.zip.ZipFile zip = payloadZip();
			if(zip == null) {
				return null;
			}
			java.util.zip.ZipEntry entry = zip.getEntry(DONOR_ROOT + name + ".class");
			if(entry == null) {
				return null;
			}
			try(InputStream stream = zip.getInputStream(entry)) {
				ClassNode node = new ClassNode();
				new ClassReader(stream.readAllBytes()).accept(node, 0);
				return node;
			}
		} catch(Throwable t) {
			LOGGER.warn("OptiFine payload: could not read the donor for " + name + ": " + t);
			return null;
		}
	}

	/**
	 * This jar, open, for the donor classes. The payload itself is read once by {@link #payload()} from
	 * its own code-source location rather than by resource lookup, for the reason stated there, and the
	 * donors need the same door: they sit under {@code optifineoforge/donors/} beside a plan that lists
	 * them, and a jar that is also a mod file is not always reachable by resource name on this classpath.
	 */
	private static java.util.zip.ZipFile payloadZip() {
		if(payloadArchive != null) {
			return payloadArchive;
		}
		try {
			URL own = OptifinePayloadClassProcessor.class.getProtectionDomain().getCodeSource().getLocation();
			payloadArchive = new java.util.zip.ZipFile(new File(own.toURI()));
		} catch(Throwable t) {
			LOGGER.error("OptiFine payload: could not open this jar for the donor classes - no member is "
					+ "restored", t);
		}
		return payloadArchive;
	}

	private static java.util.zip.ZipFile payloadArchive;

	/**
	 * Where OptiFine's own classes live, printed once, because the stack trace of the failure this
	 * answers is not enough to tell which loader it is.
	 *
	 * <p>Measured on 1.21.11, the failure is</p>
	 *
	 * <pre>NoClassDefFoundError: net/minecraft/world/level/storage/ValueOutput
	 *   at Class.getDeclaredMethods0(Native Method)
	 *   at net.optifine.reflect.ReflectorMethod.getMethods(ReflectorMethod.java:263)
	 *   at ... GameRenderer.frameInit(1304)
	 * Caused by: ClassNotFoundException: net.minecraft.world.level.storage.ValueOutput
	 *   at java.net.URLClassLoader.findClass(URLClassLoader.java:445)</pre>
	 *
	 * <p>{@code getDeclaredMethods0} resolves the parameter types of the class it is enumerating with
	 * <em>that class's own</em> defining loader, and the loader in the trace is a plain
	 * {@code URLClassLoader} - which is not what a class in a module layer would be. So the question is
	 * which loader defines {@code net.optifine.reflect.Reflector}, and whether it can see game classes
	 * at all. Both are printed here rather than reasoned about: each candidate loader, its parent chain,
	 * its URLs when it has any, and what it resolves the three names to.</p>
	 */
	private static void probeLayers() {
		if(probed) {
			return;
		}
		probed = true;
		// Off unless asked for, the same way the 1.20.x/1.21.x lines' reload probe is: a payload that
		// prints fifteen lines about class loaders on every launch is noise in a shipped mod, and the
		// question it answers is asked once. Set OPTIFINEOFORGE_DEBUG_LAYERS=1 for the run that needs it.
		if(System.getenv("OPTIFINEOFORGE_DEBUG_LAYERS") == null) {
			return;
		}
		try {
			reportLoader("own", OptifinePayloadClassProcessor.class.getClassLoader());
			reportLoader("system", ClassLoader.getSystemClassLoader());
			reportLoader("context", Thread.currentThread().getContextClassLoader());
		} catch(Throwable t) {
			LOGGER.warn("OptiFine classes probe failed: " + t);
		}
	}

	private static void reportLoader(String label, ClassLoader loader) {
		StringBuilder chain = new StringBuilder();
		for(ClassLoader step = loader; step != null; step = step.getParent()) {
			chain.append(step.getClass().getName());
			if(step instanceof java.net.URLClassLoader urls) {
				// Every URL of every loader in the chain, because the failure turns on exactly that:
				// ReflectorClass.getTargetClass() is "Class.forName(name)" with no loader, so the class
				// that resolves a game type is whichever loader defined ReflectorClass - and a second
				// copy of OptiFine's classes on a plain URL classpath would resolve game names through
				// a loader that never sees the game module layer. Printing only the top of the chain
				// hides precisely the loader that matters when it is not the top.
				for(URL url : urls.getURLs()) {
					chain.append(" [").append(url).append(']');
				}
			}
			chain.append(" -> ");
		}
		LOGGER.info("OptiFine classes probe: " + label + " = "
				+ (loader == null ? "bootstrap" : loader.getClass().getName()) + "; chain " + chain + "bootstrap");
		for(String name : new String[] {"net.optifine.reflect.Reflector",
				"net.optifine.reflect.ReflectorClass",
				"net.minecraft.world.level.storage.ValueOutput",
				"net.minecraft.client.renderer.GameRenderer"}) {
			try {
				Class<?> found = Class.forName(name, false, loader);
				LOGGER.info("OptiFine classes probe: " + label + " sees " + name + " -> module "
						+ found.getModule().getName() + ", defined by "
						+ (found.getClassLoader() == null ? "bootstrap" : found.getClassLoader().getClass().getName()));
			} catch(Throwable t) {
				LOGGER.info("OptiFine classes probe: " + label + " cannot load " + name + ": "
						+ t.getClass().getName() + ": " + t.getMessage());
			}
		}
	}

	private static boolean probed;

	/**
	 * Copies one class node over another, the same way OptiFine's own newer processor does it: every
	 * structural part is taken from the source, so nothing of the runtime's copy survives - a field
	 * or an interface left behind would be a class that compiles differently from the one OptiFine
	 * was tested with.
	 *
	 * <p>With one exception, and it is the rule this project paid for twice on the ModLauncher lines:
	 * a <em>member's</em> visibility keeps whichever of the two copies is wider. The class flags are
	 * OptiFine's, but NeoForge's access transformers widen members of its own copy, and NeoForge's
	 * code then relies on the widened form. Installing OptiFine's copy wholesale made
	 * {@code NeoForgeRenderTypes$Internal} fail with
	 * {@code IllegalAccessError: ... tried to access method 'RenderType create(String, RenderSetup)'},
	 * because OptiFine's compilation of that method is narrower than NeoForge's widened one.</p>
	 *
	 * <p>And a second exception, measured on 1.21.9 and not on the older lines: members the runtime has
	 * and the payload does not are <em>kept</em>, appended after OptiFine's own. Modern NeoForge patches
	 * the game's sources at build time rather than only transforming them at load time, so its copy of a
	 * class can carry members OptiFine's compilation never had. The failure that named this rule was</p>
	 *
	 * <pre>NoSuchMethodError: 'java.util.List
	 *     net.minecraft.server.packs.resources.ReloadableResourceManager.getListeners()'
	 *   at net.neoforged.neoforge.client.event.AddClientReloadListenersEvent.&lt;init&gt;(...:29)
	 *   at net.neoforged.neoforge.client.ClientHooks.initClientHooks(...:973)
	 *   at net.minecraft.client.Minecraft.&lt;init&gt;(Minecraft.java:665)</pre>
	 *
	 * <p>which is NeoForge's own method, called by NeoForge's own class, against a class this processor
	 * had just replaced with a copy that predates NeoForge's patch. Nothing of the runtime's survives in
	 * the parts OptiFine does define - that is what the paragraph above is about - but a member that
	 * exists <em>only</em> in the runtime has no OptiFine version to conflict with, and dropping it can
	 * only remove something some other NeoForge class was compiled against.</p>
	 */
	private static void copy(ClassNode source, ClassNode target) {
		Map<String, Integer> wasMethods = visibilityOfMethods(target.methods);
		Map<String, Integer> wasFields = visibilityOfFields(target.fields);
		String runtimeSuperName = target.superName;
		java.util.List<String> runtimeInterfaces = new java.util.ArrayList<>(target.interfaces);
		boolean targetIsInterface = (target.access & Opcodes.ACC_INTERFACE) != 0;
		for(FieldNode field : source.fields) {
			int merged = wider(field.access, wasFields.get(field.name + field.desc));
			if(targetIsInterface) {
				// An interface's fields have to be public static final whatever either copy says, and
				// the JVM rejects the class outright otherwise ("ClassFormatError: Illegal field
				// modifiers"), which is measured on the 1.21.x lines of this project.
				merged = (merged & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED))
						| Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
			} else if(!wasFields.containsKey(field.name + field.desc)
					|| (wasFields.get(field.name + field.desc) & Opcodes.ACC_FINAL) == 0) {
				// final is not inherited from the payload: NeoForge's access transformers strip it
				// from fields its own subclasses assign, and a swapped class carrying the payload's
				// final bit then rejects that assignment ("IllegalAccessError: Update to non-static
				// final field ... attempted from a different class"). static is deliberately left as
				// the payload has it.
				merged &= ~Opcodes.ACC_FINAL;
			}
			field.access = merged;
		}
		for(MethodNode method : source.methods) {
			method.access = wider(method.access, wasMethods.get(method.name + method.desc));
		}
		// Before the field copies below, because re-parenting can clear the payload's generic signature when
		// it still names the Forge superclass, and target.signature is taken from source.signature.
		String reparentedSuper = reparent(source, runtimeSuperName);
		target.version = source.version;
		target.access = source.access;
		target.name = source.name;
		target.signature = source.signature;
		target.superName = reparentedSuper;
		// Union, payload first: OptiFine's copy marks the class with its own Forge-era interfaces and the
		// runtime's marks it with NeoForge's, and a class can carry both lists. Dropping the runtime's is
		// what the missing getListeners() above is a symptom of; dropping OptiFine's would take away
		// markers its own code tests for.
		target.interfaces = union(source.interfaces, runtimeInterfaces);
		target.sourceFile = source.sourceFile;
		target.sourceDebug = source.sourceDebug;
		target.module = source.module;
		target.outerClass = source.outerClass;
		target.outerMethod = source.outerMethod;
		target.outerMethodDesc = source.outerMethodDesc;
		target.visibleAnnotations = source.visibleAnnotations;
		target.invisibleAnnotations = source.invisibleAnnotations;
		target.visibleTypeAnnotations = source.visibleTypeAnnotations;
		target.invisibleTypeAnnotations = source.invisibleTypeAnnotations;
		target.attrs = source.attrs;
		target.innerClasses = source.innerClasses;
		target.nestHostClass = source.nestHostClass;
		target.nestMembers = source.nestMembers;
		target.permittedSubclasses = source.permittedSubclasses;
		target.recordComponents = source.recordComponents;
		int keptFields = 0;
		int keptMethods = 0;
		for(FieldNode field : target.fields) {
			// Keyed by name and descriptor both: a field that exists in both with a different type is
			// OptiFine's to define, and appending the runtime's would be a duplicate-field error.
			if(hasField(source.fields, field.name, field.desc)) {
				continue;
			}
			source.fields.add(field);
			keptFields++;
		}
		for(MethodNode method : target.methods) {
			if(hasMethod(source.methods, method.name, method.desc)) {
				continue;
			}
			if("<clinit>".equals(method.name)) {
				// A static initialiser is written against the field shapes of its own class, and keeping it
				// wholesale is only safe while those shapes agree. Measured on 1.21.9, BreezeWindLayer's
				// payload copy declares
				//
				//   private net.minecraft.resources.ResourceLocation TEXTURE_LOCATION;   (instance, assigned in
				//                                                                        the constructor)
				//
				// while the runtime's declares the same name and descriptor as static final, and the runtime's
				// <clinit> reads it with GETSTATIC - keeping that method next to the payload's instance field
				// made the class fail its own initialisation with
				//
				//   IncompatibleClassChangeError: Expected static field
				//     net.minecraft.client.renderer.entity.layers.BreezeWindLayer.TEXTURE_LOCATION
				//   at …BreezeWindLayer.<clinit>(BreezeWindLayer.java:18)
				//
				// inside EntityRenderers.createEntityRenderers, which killed the resource reload.
				//
				// Dropping it unconditionally then broke the other half, also measured: static fields this
				// rule keeps from the runtime are *assigned* by this initialiser, so dropping it leaves them
				// null - NeoForge's own TagConventionLogWarning then died in its own <clinit> with
				//   NullPointerException: Cannot invoke "net.minecraft.tags.TagKey.toString()" because "tag2"
				//   is null
				// at TagConventionLogWarning.createForgeMapEntry, out of NeoForgeMod's constructor, which FML
				// reported as "NeoForge (neoforge) has failed to load correctly".
				//
				// So it is kept when every static access it makes to this class' own fields still resolves to
				// a static field here, and dropped when one does not.
				String problem = staticAccessProblem(method, source, target);
				if(problem != null) {
					LOGGER.info("OptiFine payload: " + target.name.replace('/', '.') + " keeps no runtime static "
							+ "initialiser: " + problem);
					continue;
				}
				LOGGER.info("OptiFine payload: " + target.name.replace('/', '.') + " keeps the runtime's static "
						+ "initialiser (every field it touches is static here too)");
			}
			source.methods.add(method);
			keptMethods++;
		}
		target.fields = source.fields;
		target.methods = source.methods;
		if(keptFields != 0 || keptMethods != 0) {
			// Logged rather than silent: this is the count that says how much of this class is the
			// runtime's rather than OptiFine's, and on a line where the two disagree it is the number
			// that explains a NoSuchMethodError.
			LOGGER.info("OptiFine payload: " + target.name.replace('/', '.') + " kept " + keptFields
					+ " runtime fields and " + keptMethods + " runtime methods the payload does not define");
		}
	}

	/**
	 * Which superclass the installed class gets, and the constructor chain that has to be rewritten for it.
	 *
	 * <p>Measured on 1.21.9, and this is the second half of the same story as the kept members above: the
	 * payload's {@code BlockEntity} extends {@code net.minecraftforge.common.capabilities
	 * .CapabilityProvider$BlockEntities} (OptiFine's compilation is Forge-era, and that class is one of
	 * the shims), while NeoForge's own copy extends
	 * {@code net.neoforged.neoforge.attachment.AttachmentHolder}, and NeoForge's
	 * {@code BlockEntity.setData} is</p>
	 *
	 * <pre>aload_0 ; invokevirtual setChanged()V
	 * aload_0 ; aload_1 ; aload_2
	 * invokespecial net/neoforged/neoforge/attachment/AttachmentHolder.setData(...)</pre>
	 *
	 * <p>so keeping that member inside a class that is <em>not</em> an {@code AttachmentHolder} produced
	 * {@code VerifyError: Bad invokespecial instruction: current class isn't assignable to reference
	 * class} at {@code BlockEntity.setData @7} - and via {@code FieldLocatorTypes}'s reflection it took
	 * {@code Reflector}'s class initialisation down with it. Taking the runtime's superclass alone is not
	 * enough either, and that was measured too: the payload's own constructor chains to the Forge
	 * superclass's constructor, so the class then fails with
	 * {@code VerifyError: Bad <init> method call ... Type 'net/minecraftforge/common/capabilities
	 * .CapabilityProvider$BlockEntities' is not assignable to 'net/minecraft/world/level/block/entity
	 * .BlockEntity'}. Both halves have to move together, which is what the {@code reparent.txt} plan the
	 * ModLauncher lines already use describes: {@code reparent <class> <runtime superclass>
	 * <constructor>}. The rig produces it with {@code HierarchyPlan}, run against the payload and the
	 * runtime's own jars - on 1.21.9 it finds exactly one movable class, this one, onto
	 * {@code AttachmentHolder} via {@code ()V}.</p>
	 *
	 * <p>Without a plan the payload's superclass is left alone, as it was before this rule existed: an
	 * unplanned rewrite is how the constructor mismatch above was produced.</p>
	 */
	private static String reparent(ClassNode source, String runtimeSuperName) {
		String[] planned = reparents().get(source.name);
		if(planned == null) {
			return source.superName;
		}
		String plannedSuper = planned[0];
		String plannedCtor = planned[1];
		String forgeSuper = source.superName;
		if(forgeSuper == null || !forgeSuper.startsWith("net/minecraftforge/")) {
			LOGGER.warn("OptiFine payload: " + source.name.replace('/', '.') + " is planned to re-parent but its "
					+ "copy extends " + forgeSuper + ", not a Forge type - left alone");
			return forgeSuper;
		}
		if(runtimeSuperName == null || !plannedSuper.equals(runtimeSuperName)) {
			LOGGER.warn("OptiFine payload: " + source.name.replace('/', '.') + " is planned onto " + plannedSuper
					+ " while the runtime's copy extends " + runtimeSuperName + " - the plan and the runtime do "
					+ "not match, so nothing is rewritten");
			return forgeSuper;
		}
		int rewritten = 0;
		for(MethodNode method : source.methods) {
			if(!"<init>".equals(method.name)) {
				continue;
			}
			for(org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions) {
				if(!(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call)
						|| call.getOpcode() != Opcodes.INVOKESPECIAL
						|| !forgeSuper.equals(call.owner) || !"<init>".equals(call.name)) {
					continue;
				}
				if("()V".equals(plannedCtor)) {
					// The arguments are already on the stack and the runtime's superclass takes none, so
					// they are discarded rather than the pushes deleted: whatever the payload evaluates
					// for them stays evaluated exactly as OptiFine wrote it.
					org.objectweb.asm.Type[] arguments = org.objectweb.asm.Type.getArgumentTypes(call.desc);
					for(int index = arguments.length - 1; index >= 0; index--) {
						method.instructions.insertBefore(call, new org.objectweb.asm.tree.InsnNode(
								arguments[index].getSize() == 2 ? Opcodes.POP2 : Opcodes.POP));
					}
				} else if(!plannedCtor.equals(call.desc)) {
					LOGGER.warn("OptiFine payload: " + source.name.replace('/', '.') + " is planned to call "
							+ plannedCtor + " on the runtime's superclass while its constructor calls "
							+ call.desc + " on the Forge one - nothing is rewritten");
					return forgeSuper;
				}
				call.owner = plannedSuper;
				call.desc = plannedCtor;
				call.itf = false;
				rewritten++;
			}
		}
		if(rewritten == 0) {
			LOGGER.warn("OptiFine payload: " + source.name.replace('/', '.') + " extends " + forgeSuper
					+ " and no constructor of it chains to that superclass - nothing is rewritten");
			return forgeSuper;
		}
		if(source.signature != null && source.signature.contains(forgeSuper)) {
			// The generic signature names the superclass it was compiled against; a stale one is not a
			// verification problem, only a lie to anything that reads it, so it goes.
			source.signature = null;
		}
		LOGGER.info("OptiFine payload: re-parented " + source.name.replace('/', '.') + " from the Forge type "
				+ forgeSuper.replace('/', '.') + " onto the runtime's " + plannedSuper.replace('/', '.')
				+ " (super(" + plannedCtor + "), " + rewritten + " constructor call(s) rewritten)");
		return plannedSuper;
	}

	/**
	 * The reparent plan, read once from this jar at the same path the ModLauncher lines use. The jar is
	 * read directly rather than by resource lookup for the same reason the payload is: {@code srg/} is
	 * where the classes sit and no package claims it, while this file does sit at a normal resource path,
	 * so a jar that is also a mod file can carry it.
	 */
	private static Map<String, String[]> reparents() {
		if(reparents != null) {
			return reparents;
		}
		Map<String, String[]> result = new LinkedHashMap<>();
		try(InputStream stream = OptifinePayloadClassProcessor.class
				.getResourceAsStream("/optifineoforge/reparent.txt")) {
			if(stream == null) {
				LOGGER.info("OptiFine payload: no /optifineoforge/reparent.txt in this jar; no class has its "
						+ "hierarchy rewritten");
			} else {
				java.io.BufferedReader reader = new java.io.BufferedReader(
						new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
				String line;
				while((line = reader.readLine()) != null) {
					String[] parts = line.split("\t");
					if(parts.length == 4 && "reparent".equals(parts[0])) {
						result.put(parts[1], new String[] {parts[2], parts[3]});
					}
				}
				LOGGER.info("OptiFine payload: " + result.size() + " re-parent plan entry(ies)");
			}
		} catch(Throwable t) {
			LOGGER.warn("OptiFine payload: could not read the re-parent plan: " + t);
		}
		reparents = result;
		return reparents;
	}

	private static Map<String, String[]> reparents;

	/** Both interface lists, payload first, each name once. */
	private static java.util.List<String> union(java.util.List<String> payload, java.util.List<String> runtime) {
		java.util.List<String> result = new java.util.ArrayList<>(payload);
		for(String name : runtime) {
			if(!result.contains(name)) {
				result.add(name);
			}
		}
		return result;
	}

	/**
	 * Why the runtime's static initialiser cannot be kept next to the payload's fields, or null when it can.
	 *
	 * <p>Every {@code GETSTATIC}/{@code PUTSTATIC} it makes on this class' own fields has to resolve, in the
	 * class as it will exist here, to a static field of the same name and descriptor. A field the payload does
	 * not declare at all is one this rule is adding from the runtime, so it is static by construction; a field
	 * the payload declares with the same name and descriptor but without {@code static} is the
	 * BreezeWindLayer case and the initialiser has to go.</p>
	 */
	private static String staticAccessProblem(MethodNode initialiser, ClassNode payload, ClassNode runtime) {
		if(initialiser.instructions == null) {
			return "it has no instructions to read";
		}
		for(org.objectweb.asm.tree.AbstractInsnNode instruction = initialiser.instructions.getFirst();
				instruction != null; instruction = instruction.getNext()) {
			if(!(instruction instanceof org.objectweb.asm.tree.FieldInsnNode field)
					|| !runtime.name.equals(field.owner)
					|| (field.getOpcode() != Opcodes.GETSTATIC && field.getOpcode() != Opcodes.PUTSTATIC)) {
				continue;
			}
			for(FieldNode declared : payload.fields) {
				if(declared.name.equals(field.name) && declared.desc.equals(field.desc)
						&& (declared.access & Opcodes.ACC_STATIC) == 0) {
					return "it reads or writes " + field.name + " as a static field while the payload declares it "
							+ "as an instance field";
				}
			}
		}
		return null;
	}

	/** Whether a field with this name and descriptor is already in the list. */
	private static boolean hasField(java.util.List<FieldNode> fields, String name, String desc) {
		for(FieldNode field : fields) {
			if(field.name.equals(name) && field.desc.equals(desc)) {
				return true;
			}
		}
		return false;
	}

	/** Whether a method with this name and descriptor is already in the list. */
	private static boolean hasMethod(java.util.List<MethodNode> methods, String name, String desc) {
		for(MethodNode method : methods) {
			if(method.name.equals(name) && method.desc.equals(desc)) {
				return true;
			}
		}
		return false;
	}

	/** The three access bits that say who may use a member; everything else in the word is not visibility. */
	private static final int VISIBILITY = Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED;

	/** Visibility of each declared method, keyed by name and descriptor, for the wider-of-two rule. */
	private static Map<String, Integer> visibilityOfMethods(java.util.List<MethodNode> methods) {
		Map<String, Integer> result = new java.util.HashMap<>();
		for(MethodNode method : methods) {
			result.putIfAbsent(method.name + method.desc, method.access);
		}
		return result;
	}

	private static Map<String, Integer> visibilityOfFields(java.util.List<FieldNode> fields) {
		Map<String, Integer> result = new java.util.HashMap<>();
		for(FieldNode field : fields) {
			result.putIfAbsent(field.name + field.desc, field.access);
		}
		return result;
	}

	/** {@code access}, with its visibility replaced by the wider of its own and {@code other}'s. */
	private static int wider(int access, Integer other) {
		if(other == null) {
			return access;
		}
		if(rank(other) <= rank(access)) {
			return access;
		}
		return (access & ~VISIBILITY) | (other & VISIBILITY);
	}

	/** public 3, protected 2, package 1, private 0 - what "wider" means for the merge above. */
	private static int rank(int access) {
		if((access & Opcodes.ACC_PUBLIC) != 0) {
			return 3;
		}
		if((access & Opcodes.ACC_PROTECTED) != 0) {
			return 2;
		}
		return (access & Opcodes.ACC_PRIVATE) != 0 ? 0 : 1;
	}

	private static synchronized Map<String, byte[]> payload() {		if(payload != null) {
			return payload;
		}
		Map<String, byte[]> read = new LinkedHashMap<>();
		try {
			URL own = OptifinePayloadClassProcessor.class.getProtectionDomain().getCodeSource().getLocation();
			LOGGER.info("OptiFine payload: reading finished classes from " + own);
			File file = new File(own.toURI());
			try (ZipFile zip = new ZipFile(file)) {
				java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
				while(entries.hasMoreElements()) {
					ZipEntry entry = entries.nextElement();
					String entryName = entry.getName();
					if(!entryName.startsWith(PAYLOAD_ROOT) || !entryName.endsWith(".class")) {
						continue;
					}
					String name = entryName.substring(PAYLOAD_ROOT.length(), entryName.length() - ".class".length());
					// OptiFine's own classes live under the same root and are loaded as ordinary
					// classes from the classes mod file, not installed over a game class.
					if(name.startsWith("net/optifine/") || name.startsWith("net/minecraftforge/")) {
						continue;
					}
					try (InputStream in = zip.getInputStream(entry)) {
						read.put(name, readAll(in));
					}
				}
			}
		} catch(Throwable t) {
			LOGGER.error("OptiFine payload: could not read the finished classes - nothing will be installed", t);
		}
		LOGGER.info("OptiFine payload: " + read.size() + " finished game classes");
		payload = read;
		return payload;
	}

	private static byte[] readAll(InputStream in) throws java.io.IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
		byte[] buffer = new byte[8192];
		int count;
		while((count = in.read(buffer)) > 0) {
			out.write(buffer, 0, count);
		}
		return out.toByteArray();
	}

	private static java.util.Map<String, java.util.List<String[]>> stubs;

	/** The internal name this runtime gives a resource location, from /optifineoforge/runtime-location.txt. */
	private static String locationClass;

	/**
	 * The class this runtime uses for a resource location, read once.
	 *
	 * <p>It is a build-time fact carried in the payload rather than a guess in code, because the name changed
	 * inside the FML 10 group's own span: 1.21.9 and 1.21.10 have {@code net.minecraft.resources.ResourceLocation}
	 * and 1.21.11 has {@code net.minecraft.resources.Identifier} instead (measured 2026-09-24: each runtime jar
	 * holds exactly one of the two, and that line's own mappings file names it 2377 times). The payload's copy of
	 * the class under repair names neither - it asks for the removed int-keyed view - so the name cannot be read
	 * out of the class itself, and a hardcoded one is wrong on one line or the other.</p>
	 */
	private static String locationClass() {
		if(locationClass != null) {
			return locationClass;
		}
		String name = null;
		try(java.io.InputStream stream = OptifinePayloadClassProcessor.class
				.getResourceAsStream("/optifineoforge/runtime-location.txt")) {
			if(stream != null) {
				java.io.BufferedReader reader = new java.io.BufferedReader(
						new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
				String line;
				while((line = reader.readLine()) != null) {
					line = line.trim();
					if(!line.isEmpty() && !line.startsWith("#")) {
						name = line;
						break;
					}
				}
			}
		} catch(Throwable t) {
			LOGGER.warn("OptiFine payload: could not read the runtime's resource-location class name: " + t);
		}
		if(name == null) {
			name = "net/minecraft/resources/ResourceLocation";
			LOGGER.warn("OptiFine payload: no /optifineoforge/runtime-location.txt in this jar, so a repair that "
					+ "needs a resource location assumes " + name.replace('/', '.') + "; on a line that renamed it, "
					+ "the repaired call fails with NoSuchMethodError the first time it runs");
		} else {
			LOGGER.info("OptiFine payload: this runtime's resource location is " + name.replace('/', '.'));
		}
		locationClass = name;
		return locationClass;
	}

	/** The members a KEPT class must still be given, from /optifineoforge/stubs.txt: owner, name, descriptor. */
	private static java.util.Map<String, java.util.List<String[]>> stubs() {
		if(stubs != null) {
			return stubs;
		}
		java.util.Map<String, java.util.List<String[]>> result = new java.util.HashMap<>();
		try(java.io.InputStream stream = OptifinePayloadClassProcessor.class
				.getResourceAsStream("/optifineoforge/stubs.txt")) {
			if(stream != null) {
				java.io.BufferedReader reader = new java.io.BufferedReader(
						new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
				String line;
				while((line = reader.readLine()) != null) {
					if(line.startsWith("#") || line.isBlank()) {
						continue;
					}
					String[] parts = line.split("\\s+");
					if(parts.length >= 3) {
						result.computeIfAbsent(parts[0], key -> new java.util.ArrayList<>())
								.add(new String[] {parts[1], parts[2], parts.length > 3 ? parts[3] : ""});
					}
				}
				LOGGER.info("OptiFine payload: stub list: "
						+ result.values().stream().mapToInt(java.util.List::size).sum() + " member(s) across "
						+ result.size() + " class(es)");
			}
		} catch(Throwable t) {
			LOGGER.warn("OptiFine payload: could not read the stub list: " + t);
		}
		stubs = result;
		return stubs;
	}

	/** Adds the listed members to a kept class when it lacks them; default bodies, as the ml11 loader does. */
	private static void stubMissing(org.objectweb.asm.tree.ClassNode node) {
		java.util.List<String[]> wanted = stubs().get(node.name);
		if(wanted == null) {
			return;
		}
		for(String[] member : wanted) {
			boolean present = false;
			for(org.objectweb.asm.tree.MethodNode existing : node.methods) {
				if(existing.name.equals(member[0]) && existing.desc.equals(member[1])) {
					present = true;
					break;
				}
			}
			if(present) {
				continue;
			}
			int stubAccess = org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
			if(member.length > 2 && "static".equals(member[2])) {
				// The call site decides: NeoForge calls clientPreProcessPacket with invokestatic, and an instance stub
				// answered that with "IncompatibleClassChangeError: Expected static method" (measured 2026-09-23).
				stubAccess |= org.objectweb.asm.Opcodes.ACC_STATIC;
			}
			org.objectweb.asm.tree.MethodNode stub = new org.objectweb.asm.tree.MethodNode(stubAccess, member[0],
					member[1], null, null);
			org.objectweb.asm.Type returnType = org.objectweb.asm.Type.getReturnType(member[1]);
			switch(returnType.getSort()) {
				case org.objectweb.asm.Type.VOID ->
						stub.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.RETURN));
				case org.objectweb.asm.Type.BOOLEAN, org.objectweb.asm.Type.BYTE, org.objectweb.asm.Type.CHAR,
						org.objectweb.asm.Type.SHORT, org.objectweb.asm.Type.INT -> {
					stub.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ICONST_0));
					stub.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.IRETURN));
				}
				case org.objectweb.asm.Type.LONG -> {
					stub.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.LCONST_0));
					stub.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.LRETURN));
				}
				case org.objectweb.asm.Type.FLOAT -> {
					stub.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.FCONST_0));
					stub.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.FRETURN));
				}
				case org.objectweb.asm.Type.DOUBLE -> {
					stub.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.DCONST_0));
					stub.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.DRETURN));
				}
				default -> {
					// Optional must not answer null: its callers write isPresent()/isEmpty() immediately.
					if("java/util/Optional".equals(returnType.getInternalName())) {
						stub.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
								"java/util/Optional", "empty", "()Ljava/util/Optional;", false));
					} else {
						stub.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ACONST_NULL));
					}
					stub.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ARETURN));
				}
			}
			node.methods.add(stub);
			LOGGER.info("OptiFine payload: stubbed " + node.name.replace('/', '.') + "." + member[0] + member[1]
					+ " on a class kept from the runtime");
		}
	}
}
