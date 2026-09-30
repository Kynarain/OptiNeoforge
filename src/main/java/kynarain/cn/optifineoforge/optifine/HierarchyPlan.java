/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.optifine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Decides which payload classes may have their Forge superclass rewritten onto the runtime's.
 *
 * <p>The loader does the rewriting, because it is the only place where the class is defined in the game
 * layer and a game-layer supertype therefore resolves. What it cannot do there is see the supertype: it
 * holds the class being transformed and the payload's copy of it, never the runtime's superclass, and a
 * transformer must not load a game class to look. So the one question that needs both artefacts -
 * whether the runtime's superclass has a constructor a subclass may chain to - is answered here, from
 * the jars, and travels with the payload.</p>
 *
 * <p>Two conditions come out of the measurement on 1.21.1, where this applies to exactly one class:</p>
 *
 * <pre>payload  net.minecraft.world.level.block.entity.BlockEntity extends
 *          net.minecraftforge.common.capabilities.CapabilityProvider&lt;BlockEntity&gt;
 * runtime  net.minecraft.world.level.block.entity.BlockEntity extends
 *          net.neoforged.neoforge.attachment.AttachmentHolder</pre>
 *
 * <p>the runtime's superclass must declare a no-argument constructor that a subclass can call, and the
 * payload class must name its old superclass nowhere except in constructor chains. The second is
 * re-checked by the loader on the tree it is about to install; the first cannot be, so it is decided
 * here and stated in the plan.</p>
 */
public final class HierarchyPlan {
	/** Where the patched game classes sit in the payload jar, as the other tools also read it. */
	private static final String PATCHED_ROOT = "srg/";
	/** The package Forge's API lives in, which is what a payload superclass has to come from to move. */
	private static final String FORGE_PACKAGE = "net/minecraftforge/";

	private HierarchyPlan() {
	}

	/**
	 * The plan: one {@code reparent &lt;class&gt; &lt;runtime superclass&gt; &lt;constructor&gt;} line per movable class.
	 *
	 * <p>The constructor is the descriptor the rewritten chain has to call, decided here rather than
	 * assumed because the runtime supertype may well want the argument the payload already passes.
	 * Measured across the lines: 1.21.1's {@code AttachmentHolder} and 20.4's are constructed with no
	 * arguments, so the payload's argument is dropped, while 1.20.2 has no {@code AttachmentHolder} at all
	 * and its {@code net.neoforged.neoforge.common.capabilities.CapabilityProvider} is constructed with
	 * the very class the payload passes - there the call keeps its argument and only the owner changes.
	 * A no-argument constructor is the fallback, never the assumption.</p>
	 *
	 * <p>Classes whose superclass differs for another reason are reported but not planned. The loader
	 * refuses those on its own terms - it keeps the runtime's class, which is always safe - and naming
	 * them here as well would mean two places deciding the same thing.</p>
	 */
	public static List<String> plan(Path payload, List<Path> runtimeJars) throws IOException {
		Map<String, ClassNode> runtime = readRuntime(runtimeJars);
		List<String> lines = new ArrayList<>();
		int moved = 0;
		int refusals = 0;
		try(ZipFile zip = new ZipFile(payload.toFile())) {
			for(Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
				ZipEntry entry = it.nextElement();
				if(entry.isDirectory() || !entry.getName().startsWith(PATCHED_ROOT)
						|| !entry.getName().endsWith(".class")) {
					continue;
				}
				String name = entry.getName().substring(PATCHED_ROOT.length(),
						entry.getName().length() - ".class".length());
				if(name.startsWith("net/optifine/")) {
					continue;
				}
				ClassNode runtimeNode = runtime.get(name);
				if(runtimeNode == null) {
					continue;
				}
				ClassNode payloadNode;
				try(InputStream stream = zip.getInputStream(entry)) {
					payloadNode = new ClassNode();
					new ClassReader(stream.readAllBytes()).accept(payloadNode, ClassReader.SKIP_DEBUG);
				}
				String forgeSuper = payloadNode.superName;
				if(forgeSuper == null || !forgeSuper.startsWith(FORGE_PACKAGE)) {
					continue;
				}
				String runtimeSuper = runtimeNode.superName;
				if(runtimeSuper == null || "java/lang/Object".equals(runtimeSuper)
						|| runtimeSuper.equals(forgeSuper)) {
					continue;
				}
				List<String> chains = chainsTo(payloadNode, forgeSuper);
				ClassNode superNode = runtime.get(runtimeSuper);
				String reason = refusal(superNode, payloadNode, forgeSuper, chains);
				if(reason != null) {
					refusals++;
					System.out.println("  no reparent for " + name + ": " + reason);
					continue;
				}
				String constructor = constructor(superNode, chains);
				lines.add("reparent\t" + name + "\t" + runtimeSuper + "\t" + constructor);
				moved++;
				System.out.println("  reparent " + name.replace('/', '.') + " onto " + runtimeSuper
						+ " via " + constructor + " (the payload extends " + forgeSuper + ")");
			}
		}
		System.out.println("reparent plan: " + moved + " class(es) movable, " + refusals + " refused");
		return lines;
	}

	/** The descriptors of the payload's constructor calls into its Forge superclass, in order. */
	private static List<String> chainsTo(ClassNode payload, String forgeSuper) {
		List<String> descs = new ArrayList<>();
		for(MethodNode method : payload.methods) {
			if(!"<init>".equals(method.name)) {
				continue;
			}
			for(AbstractInsnNode instruction : method.instructions) {
				if(instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
						&& forgeSuper.equals(call.owner) && "<init>".equals(call.name)
						&& !descs.contains(call.desc)) {
					descs.add(call.desc);
				}
			}
		}
		return descs;
	}

	/** Which constructor the rewritten chain calls: the payload's own when the runtime has it, else (). */
	private static String constructor(ClassNode runtimeSuper, List<String> chains) {
		for(String desc : chains) {
			if(hasConstructor(runtimeSuper, desc)) {
				return desc;
			}
		}
		return "()V";
	}

	/** Whether a class declares this constructor with a visibility a subclass may use. */
	private static boolean hasConstructor(ClassNode node, String desc) {
		if(node == null) {
			return false;
		}
		for(MethodNode method : node.methods) {
			if("<init>".equals(method.name) && desc.equals(method.desc)
					&& (method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0) {
				return true;
			}
		}
		return false;
	}

	/** Why this class may not be moved, or null when it may. */
	private static String refusal(ClassNode runtimeSuper, ClassNode payload, String forgeSuper,
			List<String> chains) {
		if(chains.isEmpty()) {
			return "no constructor of it chains to " + forgeSuper;
		}
		if(runtimeSuper == null) {
			return "the runtime superclass is not in the jars handed over";
		}
		String constructor = constructor(runtimeSuper, chains);
		if(!hasConstructor(runtimeSuper, constructor)) {
			return "the runtime superclass " + runtimeSuper.name + " declares no constructor a subclass "
					+ "may call that matches " + chains + ", nor a no-argument one";
		}
		String overridden = finalOverride(payload, runtimeSuper);
		if(overridden != null) {
			return "it declares " + overridden + ", which the runtime superclass " + runtimeSuper.name
					+ " declares final, so the move would fail with IncompatibleClassChangeError";
		}
		for(MethodNode method : payload.methods) {
			if(!"<init>".equals(method.name)) {
				continue;
			}
			for(AbstractInsnNode instruction : method.instructions) {
				if(instruction instanceof MethodInsnNode call
						&& call.getOpcode() == Opcodes.INVOKESPECIAL
						&& forgeSuper.equals(call.owner)) {
					// The constructor chain itself names the old superclass, and that is the one place
					// the loader rewrites. Anything else breaks resolution after the move.
					continue;
				}
				if(instruction instanceof MethodInsnNode call
						&& (forgeSuper.equals(call.owner) || call.desc.contains("L" + forgeSuper + ";"))) {
					return "the body calls " + call.name + call.desc + " on the old superclass";
				}
				if(instruction instanceof FieldInsnNode field
						&& (forgeSuper.equals(field.owner) || field.desc.contains("L" + forgeSuper + ";"))) {
					return "the body reads " + field.name + " from the old superclass";
				}
			}
		}
		return null;
	}

	/**
	 * A member the payload declares and the runtime superclass declares {@code final}, or null.
	 *
	 * <p>This is the check 1.20.2 needs and 1.21.1 does not. Both lines' payloads extend Forge's
	 * {@code CapabilityProvider} and both runtimes extend a NeoForge replacement for it, but 20.2's
	 * {@code net.neoforged.neoforge.common.capabilities.CapabilityProvider} declares
	 * {@code serializeCaps()} final while OptiFine's {@code BlockEntity} overrides it, so joining them
	 * fails at the first instantiation:</p>
	 *
	 * <pre>IncompatibleClassChangeError: class net.minecraft.world.level.block.entity.BlockEntity
	 *   overrides final method net.neoforged.neoforge.common.capabilities.CapabilityProvider.serializeCaps()</pre>
	 *
	 * <p>An overriding member is what to look for, not a name collision: {@code final} on a method the
	 * payload only calls is harmless, and a method the payload declares is exactly what the JVM rejects.
	 * Private and static members are skipped, since neither can be overridden.</p>
	 */
	private static String finalOverride(ClassNode payload, ClassNode runtimeSuper) {
		// The immediate superclass only, and deliberately: a final method is declared where it is
		// inherited from, and for these replacements that is the class itself. A deeper one is reported
		// by the JVM at load, which is still better than an unexplained crash in the middle of startup.
		for(MethodNode method : payload.methods) {
			if((method.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) != 0) {
				continue;
			}
			if(declaresFinal(runtimeSuper, method)) {
				return method.name + method.desc;
			}
		}
		return null;
	}

	private static boolean declaresFinal(ClassNode node, MethodNode method) {
		for(MethodNode candidate : node.methods) {
			if(candidate.name.equals(method.name) && candidate.desc.equals(method.desc)
					&& (candidate.access & Opcodes.ACC_FINAL) != 0) {
				return true;
			}
		}
		return false;
	}

	/** Every class of the runtime's game, by internal name: superclass and constructors are all we need. */
	private static Map<String, ClassNode> readRuntime(List<Path> jars) throws IOException {
		Map<String, ClassNode> result = new LinkedHashMap<>();
		for(Path jar : jars) {
			try(ZipFile zip = new ZipFile(jar.toFile())) {
				for(Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
					ZipEntry entry = it.nextElement();
					if(entry.isDirectory() || !entry.getName().endsWith(".class")) {
						continue;
					}
					try(InputStream stream = zip.getInputStream(entry)) {
						ClassNode node = new ClassNode();
						new ClassReader(stream.readAllBytes()).accept(node,
								ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
						result.putIfAbsent(node.name, node);
					}
				}
			}
		}
		return result;
	}

	/**
	 * {@code HierarchyPlan <payload jar> <plan file> <runtime jar> [runtime jar...]} writes the plan and
	 * prints what it decided, so a refusal is visible in the build log rather than only in a launch.
	 */
	public static void main(String[] args) throws IOException {
		if(args.length < 3) {
			System.err.println("usage: HierarchyPlan <payload jar> <plan file> <runtime jar> [jar...]");
			System.exit(2);
		}
		List<Path> runtime = new ArrayList<>();
		for(int index = 2; index < args.length; index++) {
			runtime.add(Path.of(args[index]));
		}
		List<String> lines = plan(Path.of(args[0]), runtime);
		Files.write(Path.of(args[1]), lines, StandardCharsets.UTF_8);
		System.out.println("wrote " + args[1] + " (" + lines.size() + " line(s))");
	}
}
