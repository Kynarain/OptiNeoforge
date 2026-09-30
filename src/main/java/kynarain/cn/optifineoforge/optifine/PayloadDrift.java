/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.optifine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

/**
 * Lists the payload classes whose own compilation disagrees with the runtime's, and writes the keep plan.
 *
 * <p>The payload is OptiFine's own patch of the game, and it is applied to the same vanilla obfuscated
 * client OptiFine was built against, so the classes it produces are internally consistent - but only with
 * each other, and only for the loader OptiFine was built for. This repository installs them over a
 * <em>NeoForge</em> runtime, which patches some of the same classes in its own direction, and the two
 * patches can disagree about structure. Where they do, nothing can be repaired member by member, because
 * the disagreement is already baked into the payload's bytecode.</p>
 *
 * <p>The signal this tool reports is a {@code static final} field with a {@code ConstantValue} that
 * differs between the two copies. Such a field is a compile-time constant, so <em>javac never emits a
 * read of it</em>: every use was inlined at compile time. A difference therefore means the payload's
 * bodies were compiled against a different value than the runtime has, and no restore or stub can reach
 * them. The measured case is 1.21.8:</p>
 *
 * <pre>ModelDiscovery$ModelWrapper.SLOT_COUNT   payload=7   runtime=8</pre>
 *
 * <p>NeoForge adds an eighth slot to the seven vanilla has. OptiFine's {@code slot(int)} is compiled with
 * {@code Objects.checkIndex(index, 7)} inlined and sizes its {@code AtomicReferenceArray} with the same 7,
 * so the restore plan's initialiser for the NeoForge-only field it restores -
 * {@code KEY_ADDITIONAL_PROPERTIES = slot(7)} - throws inside the class it was written into:</p>
 *
 * <pre>ExceptionInInitializerError
 *   at ModelDiscovery$ModelWrapper.slot(ModelDiscovery.java:212)
 *   at ModelDiscovery$ModelWrapper.&lt;clinit&gt;(ModelDiscovery.java:200)</pre>
 *
 * <p>Signature drift is reported too, for the same root cause: the two copies can declare the same method
 * name with different descriptors, which is how NeoForge's geometry API (an extra
 * {@code ContextMap} parameter) and OptiFine's Forge-targeted one (an {@code IGeometryBakingContext})
 * disagree. That case is <em>not</em> automatically repairable by dropping the class - the runtime's method
 * is usually the one the game calls and the payload's the one OptiFine calls, so both are needed - which is
 * why this is a report and not a verdict.</p>
 *
 * <p>Development aid:
 * {@code PayloadDrift <payload jar> <runtime jar> [more runtime jars...] [--plan <file>]
 * [--interfaces <file>]}. The plan file is a keep plan for {@code optifineoforge/keep-runtime.txt}: one
 * {@code owner<TAB>*} line per class with constant drift, each preceded by the fields that put it there.
 * It is a proposal to review, not a decision - keeping a class whole also drops whatever OptiFine changed
 * in it - so the printed report is the part to read. The interface file is a plan for
 * {@code optifineoforge/runtime-interfaces.txt}, in the loader's own {@code owner<TAB>interface} form, and
 * that one is a repair rather than a proposal: the interfaces in it are the runtime's own and the swapped
 * class has to implement them whatever else is decided.</p>
 */
public final class PayloadDrift {
	private PayloadDrift() {
	}

	/** Development aid: see the class comment for the argument list. */
	public static void main(String[] args) throws IOException {
		List<Path> runtimeJars = new ArrayList<>();
		Path plan = null;
		Path interfacePlan = null;
		Path accessPlan = null;
		List<Path> atSources = new ArrayList<>();
		Path payload = null;
		for(int index = 0; index < args.length; index++) {
			if("--plan".equals(args[index])) {
				if(++index >= args.length) {
					System.err.println("--plan needs a file");
					System.exit(2);
				}
				plan = Path.of(args[index]);
			} else if("--interfaces".equals(args[index])) {
				if(++index >= args.length) {
					System.err.println("--interfaces needs a file");
					System.exit(2);
				}
				interfacePlan = Path.of(args[index]);
			} else if("--access".equals(args[index])) {
				if(++index >= args.length) {
					System.err.println("--access needs a file");
					System.exit(2);
				}
				accessPlan = Path.of(args[index]);
			} else if("--at".equals(args[index])) {
				if(++index >= args.length) {
					System.err.println("--at needs a file or jar");
					System.exit(2);
				}
				atSources.add(Path.of(args[index]));
			} else if(payload == null) {
				payload = Path.of(args[index]);
			} else {
				runtimeJars.add(Path.of(args[index]));
			}
		}
		if(payload == null || runtimeJars.isEmpty()) {
			System.err.println("usage: PayloadDrift <payload jar> <runtime jar> [more runtime jars...]"
					+ " [--plan <file>] [--interfaces <file>]");
			System.exit(2);
		}

		// First jar wins, which is the rule the rig assembles the runtime view with: NeoForge's client
		// overlay over NeoForm's srg client, so the overlay's version of a class is the runtime's.
		Map<String, Shape> runtime = new LinkedHashMap<>();
		for(Path jar : runtimeJars) {
			read(jar, runtime);
		}
		System.out.println("runtime: " + runtime.size() + " classes from " + runtimeJars.size() + " jar(s)");
		if(!atSources.isEmpty()) {
			readAccessTransformers(atSources);
		}

		Map<String, List<String>> constantDrift = new TreeMap<>();
		Map<String, List<String>> signatureDrift = new TreeMap<>();
		Map<String, List<String>> hierarchyDrift = new TreeMap<>();
		Map<String, List<String>> accessDrift = new TreeMap<>();
		Map<String, Integer> missingFromPayload = new TreeMap<>();
		int compared = 0;
		int payloadOnly = 0;
		int payloadClasses = 0;
		try(ZipFile zip = new ZipFile(payload.toFile())) {
			for(Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements(); ) {
				ZipEntry entry = it.nextElement();
				if(!entry.getName().endsWith(".class") || SrgRemap.isUnusedNamespace(entry.getName())) {
					continue;
				}
				ClassNode node = new ClassNode();
				try(InputStream stream = zip.getInputStream(entry)) {
					new ClassReader(stream.readAllBytes()).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
				}
				payloadClasses++;
				Shape was = runtime.get(node.name);
				if(was == null) {
					payloadOnly++;
					continue;
				}
				compared++;
				// The runtime's interfaces the payload does not implement. This is the second defect the
				// keep plan exists for, and the one that cannot be repaired by swapping members: NeoForge
				// patches a game class to implement its own extension interface and its code then casts to
				// it, and a payload compiled for the loader OptiFine was built for does not know about it.
				// Measured on 1.21.8: UnbakedGeometry extends
				// net.neoforged.neoforge.client.extensions.UnbakedGeometryExtension at runtime, whose
				// default bake(..., ContextMap) is the route the runtime's own ModelWrapper calls, so
				// dropping that interface leaves the model bake with
				//   NoSuchMethodError: 'QuadCollection UnbakedGeometry.bake(..., ContextMap)'.
				for(String iface : was.interfaces) {
					if(node.interfaces == null || !node.interfaces.contains(iface)) {
						hierarchyDrift.computeIfAbsent(node.name, key -> new ArrayList<>())
								.add("runtime-only interface " + iface);
					}
				}
				for(FieldNode field : node.fields) {
					if(field.value == null || (field.access & Opcodes.ACC_STATIC) == 0) {
						continue;
					}
					Object mine = was.constants.get(field.name + " " + field.desc);
					if(mine != null && !Objects.equals(mine, field.value)) {
						constantDrift.computeIfAbsent(node.name, key -> new ArrayList<>())
								.add(field.name + ": payload=" + show(field.value) + " runtime=" + show(mine));
					}
				}
				// Members this runtime has widened and the payload has not. NeoForge's access transformers
				// run on the runtime's copy, and its own code then compiles against the wider one, so a
				// payload member that is narrower fails at the first access:
				//   IllegalAccessError: class NeoForgeRenderTypes$Internal tried to access protected field
				//     RenderStateShard.RENDERTYPE_ENTITY_SOLID_SHADER
				// The loader's own rule takes the wider of the payload and of the class it was handed, and
				// on a line where OptiFine's transformer replaced that class first, both are the payload's
				// - which is why this list has to come from the runtime jar offline.
				for(org.objectweb.asm.tree.MethodNode method : node.methods) {
					checkVisibility(method.access, was, method.name, method.desc, accessDrift, node.name,
						accessTargetFor(node.name, method.name, method.desc));
				}
				for(FieldNode field : node.fields) {
					checkVisibility(field.access, was, field.name, field.desc, accessDrift, node.name,
						accessTargetFor(node.name, field.name, field.desc));
				}
				for(Map.Entry<String, Set<String>> entry2 : was.descriptors.entrySet()) {					Set<String> mine = descriptorsOf(node, entry2.getKey());
					if(mine.isEmpty()) {
						missingFromPayload.merge(node.name, entry2.getValue().size(), Integer::sum);
						continue;
					}
					Set<String> theirs = new TreeSet<>(entry2.getValue());
					theirs.removeAll(mine);
					if(!theirs.isEmpty()) {
						signatureDrift.computeIfAbsent(node.name, key -> new ArrayList<>())
								.add(entry2.getKey() + ": runtime-only " + theirs + ", payload has " + mine);
					}
				}
			}
		}

		System.out.println("payload: " + payloadClasses + " class(es), " + compared
				+ " of them also in the runtime, " + payloadOnly + " OptiFine additions");
		System.out.println("constant drift: " + constantDrift.size() + " class(es) whose inlined constants"
				+ " disagree - no member-level repair reaches these");
		for(Map.Entry<String, List<String>> entry : constantDrift.entrySet()) {
			System.out.println("  " + entry.getKey());
			for(String detail : entry.getValue()) {
				System.out.println("      " + detail);
			}
		}
		System.out.println("signature drift: " + signatureDrift.size() + " class(es) where a member name"
				+ " exists on both sides with a descriptor only one of them has");
		for(Map.Entry<String, List<String>> entry : signatureDrift.entrySet()) {
			System.out.println("  " + entry.getKey());
			for(String detail : entry.getValue()) {
				System.out.println("      " + detail);
			}
		}
		System.out.println("hierarchy drift: " + hierarchyDrift.size() + " class(es) that do not implement an"
				+ " interface this runtime's copy does");
		for(Map.Entry<String, List<String>> entry : hierarchyDrift.entrySet()) {
			System.out.println("  " + entry.getKey());
			for(String detail : entry.getValue()) {
				System.out.println("      " + detail);
			}
		}
		int missing = missingFromPayload.values().stream().mapToInt(Integer::intValue).sum();
		System.out.println("members the runtime has and the payload lacks: " + missing + " across "
				+ missingFromPayload.size() + " class(es) (MemberRestorePlan restores these)");
		int shown = 0;
		for(Map.Entry<String, Integer> entry : missingFromPayload.entrySet()) {
			if(shown++ >= 15) {
				System.out.println("      ... " + (missingFromPayload.size() - 15) + " more");
				break;
			}
			System.out.println("      " + entry.getValue() + "  " + entry.getKey());
		}

		if(plan != null) {
			StringBuilder text = new StringBuilder();
			text.append("# Keep plan written by PayloadDrift, ").append(constantDrift.size())
					.append(" class(es).\n")
					.append("# Every class below has a compile-time constant the payload inlined into its own bodies\n")
					.append("# that this runtime spells differently, so the two copies cannot be reconciled member by\n")
					.append("# member and the runtime's class is loaded whole. Read the drift lines: keeping a class\n")
					.append("# also drops whatever OptiFine changed in it.\n");
			for(Map.Entry<String, List<String>> entry : constantDrift.entrySet()) {
				for(String detail : entry.getValue()) {
					text.append("#   ").append(detail).append('\n');
				}
				text.append(entry.getKey()).append('\t').append("*").append('\n');
			}
			Files.writeString(plan, text.toString());
			System.out.println("keep plan: " + plan + " (" + constantDrift.size() + " line(s))");
		}
		if(interfacePlan != null) {
			StringBuilder text = new StringBuilder();
			int lines = 0;
			int classes = 0;
			for(Map.Entry<String, List<String>> entry : hierarchyDrift.entrySet()) {
				if(constantDrift.containsKey(entry.getKey())) {
					// Kept whole by the other plan, so nothing is swapped for it and its interfaces are
					// the runtime's already.
					continue;
				}
				classes++;
				for(String detail : entry.getValue()) {
					String iface = detail.substring("runtime-only interface ".length());
					text.append(entry.getKey()).append('\t').append(iface).append('\n');
					lines++;
				}
			}
			Files.writeString(interfacePlan, text.toString());
			System.out.println("interface plan: " + interfacePlan + " (" + lines + " line(s) across "
					+ classes + " class(es))");
		}
		System.out.println("access drift: " + accessDrift.size() + " class(es) with a member this runtime"
				+ " widened and the payload did not - inaccessible from NeoForge's own code without a plan");
		int shownAccess = 0;
		for(Map.Entry<String, List<String>> entry : accessDrift.entrySet()) {
			for(String detail : entry.getValue()) {
				if(shownAccess++ >= 10) {
					break;
				}
				System.out.println("  " + entry.getKey() + "  " + detail);
			}
		}
		if(accessPlan != null) {
			StringBuilder text = new StringBuilder();
			int lines = 0;
			for(Map.Entry<String, List<String>> entry : accessDrift.entrySet()) {
				for(String detail : entry.getValue()) {
					// "<name>\t<descriptor>\t<access>\t<note>" -> owner, name, descriptor, access.
					String[] parts = detail.split("\t");
					text.append(entry.getKey()).append('\t').append(parts[0]).append('\t')
							.append(parts[1]).append('\t').append(parts[2]).append('\n');
					lines++;
				}
			}
			Files.writeString(accessPlan, text.toString());
			System.out.println("access plan: " + accessPlan + " (" + lines + " line(s))");
		}
	}

	/** What the drift check needs from one copy of a class. */
	private static final class Shape {
		final Map<String, Object> constants = new LinkedHashMap<>();
		/** Method name to the set of descriptors it is declared with. */
		final Map<String, Set<String>> descriptors = new LinkedHashMap<>();
		final List<String> interfaces = new ArrayList<>();
		String superName;
		/** {@code name desc} to the access word, for the visibility comparison. */
		final Map<String, Integer> memberAccess = new LinkedHashMap<>();
	}

	/** public 3, protected 2, package 1, private 0 - what "wider" means, as in the loader. */
	private static int rank(int access) {
		if((access & Opcodes.ACC_PUBLIC) != 0) {
			return 3;
		}
		if((access & Opcodes.ACC_PROTECTED) != 0) {
			return 2;
		}
		if((access & Opcodes.ACC_PRIVATE) != 0) {
			return 0;
		}
		return 1;
	}

	private static Set<String> descriptorsOf(ClassNode node, String name) {
		Set<String> result = new TreeSet<>();
		for(org.objectweb.asm.tree.MethodNode method : node.methods) {
			if(method.name.equals(name)) {
				result.add(method.desc);
			}
		}
		return result;
	}

	/** Reads one jar into {@code into}, keeping what is already there - the first jar wins. */
	private static void read(Path jar, Map<String, Shape> into) throws IOException {
		try(ZipFile zip = new ZipFile(jar.toFile())) {
			for(Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements(); ) {
				ZipEntry entry = it.nextElement();
				if(!entry.getName().endsWith(".class") || SrgRemap.isUnusedNamespace(entry.getName())) {
					continue;
				}
				ClassNode node = new ClassNode();
				try(InputStream stream = zip.getInputStream(entry)) {
					new ClassReader(stream.readAllBytes()).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
				}
				Shape shape = into.computeIfAbsent(node.name, key -> new Shape());
				if(!shape.descriptors.isEmpty() || !shape.constants.isEmpty()) {
					// Already contributed by an earlier, higher-priority jar; a second copy must not add
					// descriptors to it, or a class present in both the overlay and the srg jar would look
					// like it declares both sets.
					continue;
				}
				for(FieldNode field : node.fields) {
					if(field.value != null && (field.access & Opcodes.ACC_STATIC) != 0) {
						shape.constants.put(field.name + " " + field.desc, field.value);
					}
				}
				for(org.objectweb.asm.tree.MethodNode method : node.methods) {
					shape.descriptors.computeIfAbsent(method.name, key -> new LinkedHashSet<>()).add(method.desc);
					shape.memberAccess.put(method.name + " " + method.desc, method.access);
				}
				shape.superName = node.superName;
				if(node.interfaces != null) {
					shape.interfaces.addAll(node.interfaces);
				}
				for(FieldNode field : node.fields) {
					shape.memberAccess.put(field.name + " " + field.desc, field.access);
				}
			}
		}
	}

	/** Records one member whose runtime copy is more visible than the payload's, if it is. */
	private static void checkVisibility(int payloadAccess, Shape runtime, String name, String desc,
			Map<String, List<String>> drift, String owner, Integer accessTarget) {
		Integer theirs = runtime.memberAccess.get(name + " " + desc);
		int target = 0;
		boolean any = false;
		if(theirs != null) {
			target = theirs;
			any = true;
		}
		// NeoForge's own access transformers widen members at load time, and those are not visible in the
		// runtime jar at all - which is how this check missed the one field that mattered:
		//   public net.minecraft.client.renderer.RenderStateShard *
		// leaves RenderStateShard.RENDERTYPE_ENTITY_SOLID_SHADER protected static final in the jar while
		// FML makes it public at load time, so NeoForgeRenderTypes$Internal may reach it. Anything the
		// config widens is therefore treated as if the runtime had it that wide already.
		if(accessTarget != null && (!any || rank(accessTarget) > rank(target))) {
			target = accessTarget;
			any = true;
		}
		if(!any || rank(target) <= rank(payloadAccess)) {
			return;
		}
		drift.computeIfAbsent(owner, key -> new ArrayList<>())
				.add(name + "\t" + desc + "\t" + target + "\tpayload " + payloadAccess + " runtime " + target);
	}

	/** Every access word an AT line may start with, mapped to the bit it sets. */
	private static final Map<String, Integer> AT_WORDS = Map.of(
			"public", Opcodes.ACC_PUBLIC,
			"protected", Opcodes.ACC_PROTECTED,
			"private", Opcodes.ACC_PRIVATE,
			"default", 0);

	/** {@code owner} to the access an AT line gives every member of it, from {@code <class> *} lines. */
	private static final Map<String, Integer> AT_ALL_MEMBERS = new LinkedHashMap<>();

	/** {@code owner} and {@code name desc} to the access an AT line gives that one member. */
	private static final Map<String, Integer> AT_BY_MEMBER = new LinkedHashMap<>();

	/**
	 * Reads NeoForge's access transformer config, from a file or from inside a jar.
	 *
	 * <p>The three shapes a line comes in are all in {@code neoforge-<version>-universal.jar}'s
	 * {@code META-INF/accesstransformer.cfg}: {@code public <class> *} for every member,
	 * {@code protected-f <class> <field>} for one field, and
	 * {@code public <class> <method>(<args>)<return>} for one method. The trailing {@code -f} and
	 * {@code -m} narrow a line to fields or methods; both are accepted and ignored here, because a member
	 * name is only ever looked up among the members the payload actually has.</p>
	 */
	private static void readAccessTransformers(List<Path> sources) throws IOException {
		for(Path source : sources) {
			List<String> lines;
			if(source.getFileName().toString().endsWith(".jar")) {
				lines = new ArrayList<>();
				try(java.util.zip.ZipFile zip = new java.util.zip.ZipFile(source.toFile())) {
					java.util.zip.ZipEntry entry = zip.getEntry("META-INF/accesstransformer.cfg");
					if(entry == null) {
						System.out.println("no META-INF/accesstransformer.cfg in " + source.getFileName());
						continue;
					}
					try(InputStream stream = zip.getInputStream(entry)) {
						for(String line : new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
							lines.add(line);
						}
					}
				}
			} else {
				lines = Files.readAllLines(source);
			}
			for(String raw : lines) {
				String line = raw;
				int hash = line.indexOf('#');
				if(hash >= 0) {
					line = line.substring(0, hash);
				}
				line = line.trim();
				if(line.isEmpty()) {
					continue;
				}
				String[] parts = line.split("\\s+");
				if(parts.length < 2) {
					continue;
				}
				String word = parts[0];
				int dash = word.indexOf('-');
				if(dash > 0) {
					word = word.substring(0, dash);
				}
				Integer access = AT_WORDS.get(word);
				if(access == null || parts.length < 3) {
					continue;
				}
				String owner = parts[1].replace('.', '/');
				if("*".equals(parts[2])) {
					AT_ALL_MEMBERS.merge(owner, access, (left, right) -> rank(right) > rank(left) ? right : left);
					continue;
				}
				String member = parts[2];
				String name = member;
				String descriptor = "";
				int paren = member.indexOf('(');
				if(paren >= 0) {
					name = member.substring(0, paren);
					descriptor = descriptorOf(member.substring(paren));
				}
				AT_BY_MEMBER.put(owner + "|" + name + "|" + descriptor, access);
			}
		}
		System.out.println("access transformers: " + AT_ALL_MEMBERS.size() + " class(es) and "
				+ AT_BY_MEMBER.size() + " named member(s)");
	}

	/** Turns an AT's source-shaped parameter list into a JVM descriptor. */
	private static String descriptorOf(String parameters) {
		StringBuilder result = new StringBuilder("(");
		String body = parameters.substring(1, parameters.lastIndexOf(')'));
		String rest = parameters.substring(parameters.lastIndexOf(')') + 1);
		if(!body.isBlank()) {
			for(String argument : body.split(",")) {
				result.append(typeOf(argument.trim()));
			}
		}
		return result.append(')').append(typeOf(rest.trim())).toString();
	}

	private static String typeOf(String type) {
		if(type.endsWith("[]")) {
			return "[" + typeOf(type.substring(0, type.length() - 2));
		}
		switch(type) {
			case "void": return "V";
			case "boolean": return "Z";
			case "byte": return "B";
			case "char": return "C";
			case "short": return "S";
			case "int": return "I";
			case "long": return "J";
			case "float": return "F";
			case "double": return "D";
			default: return "L" + type.replace('.', '/') + ";";
		}
	}

	/** The access an AT config gives this member, or null when no line covers it. */
	private static Integer accessTargetFor(String owner, String name, String desc) {
		Integer named = AT_BY_MEMBER.get(owner + "|" + name + "|" + desc);
		Integer whole = AT_ALL_MEMBERS.get(owner);
		if(named == null) {
			return whole;
		}
		if(whole == null) {
			return named;
		}
		return rank(named) > rank(whole) ? named : whole;
	}

	/** A constant, with the string form quoted so 7 and "7" cannot be confused. */
	private static String show(Object value) {
		return value instanceof String ? "\"" + value + "\"" : String.valueOf(value);
	}
}
