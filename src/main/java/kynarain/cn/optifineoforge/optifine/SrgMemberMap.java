/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.optifine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The SRG-to-official member names for the lines where OptiFine and NeoForge disagree.
 *
 * <p>OptiFine switched the member names in its payload from SRG to Mojang's official names at
 * 1.20.6. Below that the payload references members as {@code m_91087_} while NeoForge's runtime
 * from 20.2 onwards names them {@code runTick}, so those lines need the names rewritten. Two files
 * together hold the answer, and neither is enough alone:</p>
 *
 * <ul>
 *   <li>MCPConfig's {@code config/joined.tsrg} is {@code tsrg2 obf srg id} and is the only one whose
 *       srg column is real - {@code a net/minecraft/src/C_252363_ 252363}.</li>
 *   <li>NeoForm's {@code -mappings-merged.txt} is {@code tsrg2 obf official} and names the official
 *       side.</li>
 * </ul>
 *
 * <p>Both are keyed by the obfuscated name, so joining them on it yields SRG to official directly.
 * Class names need no rewriting - since 1.17 the payload already uses official class names and only
 * the members are SRG - so the table is keyed by the owner's official name.</p>
 *
 * <p>Three things about the join are easy to get wrong, and all three were measured here rather than
 * reasoned about:</p>
 *
 * <ul>
 *   <li><b>The descriptor has to be part of the join key.</b> Obfuscated method names are unique
 *       only per name <em>and</em> descriptor, so a class with two overloads both called {@code a}
 *       collides when the key is the name alone - that is how {@code m_118316_}, which is
 *       {@code getSprite}, was first read as {@code dumpContents}. Both files write descriptors in
 *       the obfuscated namespace ({@code (Lahg;)Lgen;}), so they compare directly - but only after
 *       the two spellings of a class name have been reduced to one, see {@link #canonical}.</li>
 *   <li><b>A reference's owner is not always the declaring class.</b> {@code invokevirtual
 *       BlockState.m_60734_} is legal for a member declared in a superclass, and the table is built
 *       from declaring classes, so a lookup that misses has to walk the hierarchy.</li>
 *   <li><b>A member line's columns are not fixed by the header.</b> The third column is the member id
 *       in one file and the member name in the other, so it - not the shape of the second column -
 *       is what says whether a descriptor is present at all. Getting that wrong cost four members
 *       their entry, all of them enum constants MCPConfig names {@code X}, {@code Y} and {@code Z}.</li>
 * </ul>
 *
 * <p>Descriptors are not part of the table's own key: one SRG name maps to one official name per
 * declaring owner regardless of overloads, and the reference's descriptor is already in the
 * runtime's namespace.</p>
 *
 * <p>Both mapping files are tsrg2, whose member lines come in more shapes than the header suggests:
 * {@code \t obf srg id} for a field, {@code \t obf descriptor srg id} for a method, and then
 * {@code \t\t static} and {@code \t\t 0 obfParam srgParam} lines that belong to the member above.
 * Counting leading tabs rather than columns is what keeps those from being read as members, which is
 * how this parser first crashed.</p>
 */
public final class SrgMemberMap {
	/** SRG field and method names, which is exactly what has to be rewritten. */
	private static final Pattern SRG_NAME = Pattern.compile("^[mf]_\\d+_$");

	/** A field descriptor, for the tsrg2 shape that carries one. */
	private static final Pattern FIELD_DESCRIPTOR = Pattern.compile("^(\\[+)?([BCDFIJSZ]|L[^;]+;)$");

	/** MCPConfig's third field column, the numeric member id, which a descriptor column never is. */
	private static final Pattern MEMBER_ID = Pattern.compile("^\\d+$");

	private final Map<String, Map<String, String>> fields;
	private final Map<String, Map<String, String>> methods;

	private SrgMemberMap(Map<String, Map<String, String>> fields, Map<String, Map<String, String>> methods) {
		this.fields = fields;
		this.methods = methods;
	}

	/** The official name for an SRG field name, or null when this declaring owner has no such field. */
	public String field(String owner, String srgName) {
		Map<String, String> perOwner = fields.get(owner);
		return perOwner == null ? null : perOwner.get(srgName);
	}

	/** The official name for an SRG method name, or null when this declaring owner has no such method. */
	public String method(String owner, String srgName) {
		Map<String, String> perOwner = methods.get(owner);
		return perOwner == null ? null : perOwner.get(srgName);
	}

	public int fieldCount() {
		return total(fields);
	}

	public int methodCount() {
		return total(methods);
	}

	public int ownerCount() {
		Set<String> owners = new HashSet<>(fields.keySet());
		owners.addAll(methods.keySet());
		return owners.size();
	}

	private static int total(Map<String, Map<String, String>> byOwner) {
		int count = 0;
		for(Map<String, String> perOwner : byOwner.values()) {
			count += perOwner.size();
		}
		return count;
	}

	/**
	 * Joins MCPConfig's {@code obf srg} with NeoForm's {@code obf official}.
	 *
	 * <p>A member is kept only when both files know the same obfuscated member, because only then is
	 * its official name known; anything else would be a name this tool cannot translate.</p>
	 */
	public static SrgMemberMap build(Path mcpJoinedTsrg, Path neoformMerged) throws IOException {
		Tsrg srg = parse(mcpJoinedTsrg);
		Tsrg official = parse(neoformMerged);
		Map<String, String> officialByKey = official.index();

		Map<String, Map<String, String>> fields = new HashMap<>();
		Map<String, Map<String, String>> methods = new HashMap<>();
		int unjoinedOwners = 0;
		int unjoinedMembers = 0;
		for(Owned owned : srg.members) {
			String officialOwner = official.classes.get(owned.obfOwner);
			if(officialOwner == null) {
				unjoinedOwners++;
				continue;
			}
			Member member = owned.member;
			String officialName = officialByKey.get(key(owned.obfOwner, member));
			if(officialName == null) {
				unjoinedMembers++;
				continue;
			}
			Map<String, Map<String, String>> byKind = member.method ? methods : fields;
			byKind.computeIfAbsent(officialOwner, owner -> new HashMap<>()).put(member.mappedName, officialName);
		}
		System.out.println("table: " + fields.size() + " owners with fields, " + methods.size()
				+ " with methods (" + total(fields) + " + " + total(methods) + " names); "
				+ unjoinedOwners + " classes and " + unjoinedMembers + " members had no counterpart");
		return new SrgMemberMap(fields, methods);
	}

	private static String key(String obfOwner, Member member) {
		return obfOwner + "|" + (member.method ? "M" : "F") + "|" + member.obfName + "|"
				+ (member.descriptor == null ? "" : member.descriptor);
	}

	/** One member line of a mapping file, still named in that file's left-hand namespace. */
	private static final class Member {
		final String obfName;
		final String mappedName;
		final boolean method;
		final String descriptor;

		Member(String obfName, String mappedName, boolean method, String descriptor) {
			this.obfName = obfName;
			this.mappedName = mappedName;
			this.method = method;
			this.descriptor = descriptor;
		}
	}

	/** A member together with the obfuscated class it belongs to. */
	private static final class Owned {
		final String obfOwner;
		final Member member;

		Owned(String obfOwner, Member member) {
			this.obfOwner = obfOwner;
			this.member = member;
		}
	}

	/** What one tsrg2 file says: obfuscated class names joined to that file's namespace, and members. */
	private static final class Tsrg {
		final Map<String, String> classes = new HashMap<>();
		final List<Owned> members = new ArrayList<>();

		/** {@code obfOwner|kind|obfMember|descriptor -> this file's name for that member}. */
		Map<String, String> index() {
			Map<String, String> byKey = new HashMap<>();
			for(Owned owned : members) {
				byKey.put(key(owned.obfOwner, owned.member), owned.member.mappedName);
			}
			return byKey;
		}
	}

	/** Parses a tsrg2 file, keeping the obfuscated names as the join keys. */
	private static Tsrg parse(Path path) throws IOException {
		Tsrg file = new Tsrg();
		String current = null;
		for(String line : Files.readAllLines(path)) {
			if(line.isEmpty() || line.startsWith("#") || line.startsWith("tsrg2")) {
				continue;
			}
			if(line.charAt(0) != '\t') {
				String[] tokens = line.trim().split("\\s+");
				if(tokens.length >= 2) {
					current = tokens[0];
					file.classes.put(tokens[0], tokens[1]);
				} else {
					current = null;
				}
				continue;
			}
			if(line.length() > 1 && line.charAt(1) == '\t') {
				// A modifier or parameter line that belongs to the member above it.
				continue;
			}
			if(current == null) {
				continue;
			}
			Member member = memberOf(line.trim().split("\\s+"));
			if(member != null) {
				file.members.add(new Owned(current, member));
			}
		}
		return file;
	}

	/** Reads one member line, or null when it is not readable as one. */
	private static Member memberOf(String[] tokens) {
		if(tokens.length < 2) {
			return null;
		}
		boolean method = tokens[1].startsWith("(");
		// A field line has three shapes and the third column is what tells them apart, not the second:
		// MCPConfig writes "obf srg id", the obf->official table this project generates writes
		// "obf official", and a NeoForm-style line would carry the type as "obf descriptor srg id". Reading
		// the descriptor by shape alone made the enum constants MCPConfig calls X / Y / Z look like boolean
		// fields with the id as their name. The id column is always numeric, so it decides.
		boolean withDescriptor = method || (tokens.length >= 3
				&& !MEMBER_ID.matcher(tokens[2]).matches() && FIELD_DESCRIPTOR.matcher(tokens[1]).matches());
		if(withDescriptor) {
			// obf descriptor name [id]
			return new Member(tokens[0], tokens[2], method, canonical(tokens[1]));
		}
		// obf name [id], a field line with no descriptor
		return new Member(tokens[0], tokens[1], false, null);
	}

	/**
	 * The one spelling of a descriptor both files have to agree on, because the join is keyed by it.
	 *
	 * <p>A JVM descriptor names its classes with {@code /} and never with {@code .}, so the two are not two
	 * spellings of one descriptor - a key built from one can never match a key built from the other.
	 * Measured on 1.20.2: Mojang's own mappings leave some classes unrenamed
	 * ({@code net.minecraft.server.MinecraftServer -> net.minecraft.server.MinecraftServer},
	 * {@code com.mojang.blaze3d.platform.GlStateManager -> com.mojang.blaze3d.platform.GlStateManager}), the
	 * obf->official table this rig generates writes those obfuscated names into descriptors as they are, and
	 * all 102 members whose signature mentions such a class therefore never joined MCPConfig's side. The
	 * rewrite then had no official name for them and left the SRG name in the reference - which is a
	 * {@code NoSuchMethodError} the first time that code runs, not a table entry quietly missing.</p>
	 */
	private static String canonical(String descriptor) {
		if(descriptor == null || descriptor.indexOf('.') < 0) {
			return descriptor;
		}
		StringBuilder canonical = new StringBuilder(descriptor.length());
		boolean inClassName = false;
		for(int index = 0; index < descriptor.length(); index++) {
			char character = descriptor.charAt(index);
			if(character == 'L') {
				inClassName = true;
			} else if(character == ';') {
				inClassName = false;
			}
			canonical.append(inClassName && character == '.' ? '/' : character);
		}
		return canonical.toString();
	}

	/**
	 * The members and hierarchy of the runtime jars, which is what a rewritten reference has to hit.
	 *
	 * <p>Package-private rather than private because {@link SrgRemap} needs the same index: a
	 * reference's owner is not always the declaring class, so the rewriter resolves through this
	 * hierarchy exactly as the verifier does.</p>
	 */
	static final class RuntimeIndex {
		final Set<String> methods = new HashSet<>();
		final Set<String> fields = new HashSet<>();
		final Map<String, String> superOf = new HashMap<>();
		final Map<String, List<String>> interfacesOf = new HashMap<>();

		/** Adds a game jar: its members are the ones a rewritten reference has to hit, and its hierarchy. */
		void add(Path jar) throws IOException {
			add(jar, name -> false);
		}

		/**
		 * Indexes the JDK's own classes, so an inherited member can be answered instead of guessed.
		 *
		 * <p>References such as {@code ListTag.add(Object)} (inherited from {@code AbstractList}) or
		 * {@code ResourceLocationException.getMessage()} (from {@code Throwable}) cannot be resolved from any
		 * jar, and the guess that replaced this - "a java/** ancestor means present" - made every class look
		 * satisfied, because every class has {@code java/lang/Object} above it. Reading java.base through the
		 * jrt filesystem costs a walk and removes the guesswork.</p>
		 */
		void addJdk() throws IOException {
			java.nio.file.FileSystem jrt;
			try {
				jrt = java.nio.file.FileSystems.getFileSystem(java.net.URI.create("jrt:/"));
			} catch(Throwable e) {
				System.err.println("jdk index unavailable: " + e);
				return; // no jrt image: the caller falls back to whatever it has indexed
			}
			int indexed = 0;
			for(String module : new String[] {"java.base", "java.desktop", "java.logging"}) {
				Path root = jrt.getPath("/modules/" + module);
				if(!Files.exists(root)) {
					System.err.println("jdk module not in the image: " + module);
					continue;
				}
				try(java.util.stream.Stream<Path> files = Files.walk(root)) {
					for(Path file : files.toList()) {
						if(!file.toString().endsWith(".class")) {
							continue;
						}
						ClassNode node = new ClassNode();
						try(InputStream stream = Files.newInputStream(file)) {
							new ClassReader(stream.readAllBytes()).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
						}
						recordHierarchy(node);
						for(FieldNode field : node.fields) {
							fields.add(node.name + "." + field.name + field.desc);
						}
						for(MethodNode method : node.methods) {
							methods.add(node.name + "." + method.name + method.desc);
						}
						indexed++;
					}
				}
			}
			System.err.println("jdk index: " + indexed + " classes");
		}

		/**
		 * The same, leaving out classes the build will not ship.
		 *
		 * <p>A class the payload leaves out is not a source of members: counting it makes a reference look
		 * satisfiable when nothing will provide it. OptiFine's {@code LoadingOverlay.update()}, called by its
		 * own {@code GameRenderer}, stayed invisible for exactly that reason - the class was still in the jar
		 * the analysis read, and gone from the jar that shipped.</p>
		 */
		void add(Path jar, java.util.function.Predicate<String> skip) throws IOException {
			forEachClass(jar, node -> {
				if(skip.test(node.name)) {
					return;
				}
				recordHierarchy(node);
				for(FieldNode field : node.fields) {
					fields.add(node.name + "." + field.name + field.desc);
				}
				for(MethodNode method : node.methods) {
					methods.add(node.name + "." + method.name + method.desc);
				}
			});
		}

		/**
		 * Adds only what a jar says about supertypes, not what it declares.
		 *
		 * <p>The payload being rewritten has to contribute its own hierarchy: OptiFine's classes
		 * extend and implement game classes ({@code BlockPosM extends BlockPos}), and an inherited
		 * member is only reachable by walking through them. Its declarations must not join the member
		 * sets, though - those are the runtime's, and they are what the rewrite is checked against.</p>
		 */
		void addHierarchy(Path jar) throws IOException {
			forEachClass(jar, this::recordHierarchy);
		}

		private void recordHierarchy(ClassNode node) {
			if(node.superName != null) {
				superOf.put(node.name, node.superName);
			}
			if(node.interfaces != null && !node.interfaces.isEmpty()) {
				interfacesOf.put(node.name, node.interfaces);
			}
		}

		private static void forEachClass(Path jar, java.util.function.Consumer<ClassNode> action) throws IOException {
			try(ZipFile zip = new ZipFile(jar.toFile())) {
				for(java.util.Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
					ZipEntry entry = entries.nextElement();
					if(!entry.getName().endsWith(".class") || SrgRemap.isUnusedNamespace(entry.getName())) {
						continue;
					}
					ClassNode node = new ClassNode();
					try(InputStream stream = zip.getInputStream(entry)) {
						new ClassReader(stream.readAllBytes()).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
					}
					action.accept(node);
				}
			}
		}

		boolean has(String owner) {
			return superOf.containsKey(owner) || interfacesOf.containsKey(owner);
		}

		/** The owner and then its ancestors, breadth first, for members declared higher up. */
		List<String> hierarchy(String owner) {
			List<String> order = new ArrayList<>();
			Set<String> seen = new HashSet<>();
			Deque<String> queue = new ArrayDeque<>();
			queue.add(owner);
			while(!queue.isEmpty()) {
				String current = queue.poll();
				if(!seen.add(current)) {
					continue;
				}
				order.add(current);
				String parent = superOf.get(current);
				if(parent != null) {
					queue.add(parent);
				}
				List<String> interfaces = interfacesOf.get(current);
				if(interfaces != null) {
					queue.addAll(interfaces);
				}
			}
			return order;
		}
	}

	/**
	 * Development aid: proves the table over a whole payload instead of a sample.
	 *
	 * <p>{@code SrgMemberMap <mcp joined.tsrg> <neoform merged> <payload jar> <runtime jar> [more jars]}
	 * walks every SRG reference in the payload's classes, translates it, and asks whether the runtime
	 * actually has that member. The numbers this prints are the whole argument for the rewrite.</p>
	 *
	 * <p>With {@code --emit <file>} first it also writes the pairs the loader needs, which are the
	 * referenced owner rather than the declaring one: rewriting a reference only ever changes the
	 * member's name, so a table keyed by the owner as written in the bytecode means the loader needs
	 * no class hierarchy and therefore loads nothing early. Point it at the patched payload as well
	 * as the raw jar - the payload's own replaced classes carry SRG references too.</p>
	 */
	public static void main(String[] args) throws IOException {
		boolean emit = args.length > 0 && "--emit".equals(args[0]);
		int base = emit ? 2 : 0;
		if(args.length < base + 4) {
			System.err.println("usage: SrgMemberMap [--emit <file>] <mcp joined.tsrg> <neoform merged>"
					+ " <payload jar> <runtime jar> [more jars...]");
			System.exit(2);
		}
		Path mcp = Path.of(args[base]);
		Path merged = Path.of(args[base + 1]);
		Path payload = Path.of(args[base + 2]);
		SrgMemberMap map = build(mcp, merged);
		RuntimeIndex runtime = new RuntimeIndex();
		for(int index = base + 3; index < args.length; index++) {
			runtime.add(Path.of(args[index]));
		}
		System.out.println("runtime index: " + runtime.methods.size() + " methods, " + runtime.fields.size()
				+ " fields, " + runtime.superOf.size() + " classes with a superclass");

		Map<String, Integer> reasons = new TreeMap<>();
		Map<String, String> samples = new LinkedHashMap<>();
		Map<String, String> rewrite = new TreeMap<>();
		int total = 0;
		int direct = 0;
		int inherited = 0;
		int declared = 0;
		String firstDeclared = null;
		try(ZipFile zip = new ZipFile(payload.toFile())) {
			for(java.util.Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
				ZipEntry entry = entries.nextElement();
				if(!entry.getName().endsWith(".class") || SrgRemap.isUnusedNamespace(entry.getName())) {
					continue;
				}
				ClassNode node = new ClassNode();
				try(InputStream stream = zip.getInputStream(entry)) {
					new ClassReader(stream.readAllBytes()).accept(node, ClassReader.SKIP_DEBUG);
				}
				// A payload's SRG names appear in declarations as well as references, so both have to
				// be counted: a jar that names its own members in SRG satisfies nobody even when every
				// call site has been fixed.
				for(FieldNode field : node.fields) {
					if(SRG_NAME.matcher(field.name).matches()) {
						declared++;
						if(firstDeclared == null) {
							firstDeclared = node.name + "." + field.name + " " + field.desc;
						}
					}
				}
				for(MethodNode method : node.methods) {
					if(SRG_NAME.matcher(method.name).matches()) {
						declared++;
						if(firstDeclared == null) {
							firstDeclared = node.name + "." + method.name + method.desc;
						}
					}
				}
				List<String[]> references = new ArrayList<>();
				collect(node, references);
				for(String[] reference : references) {
					String owner = reference[0];
					String name = reference[1];
					String desc = reference[2];
					boolean method = "M".equals(reference[3]);
					total++;
					String officialName = null;
					String declaring = null;
					for(String candidate : runtime.hierarchy(owner)) {
						String found = method ? map.method(candidate, name) : map.field(candidate, name);
						if(found != null) {
							officialName = found;
							declaring = candidate;
							break;
						}
					}
					if(officialName == null) {
						bump(reasons, "no table entry");
						samples.putIfAbsent("no entry", owner + "." + name + " " + desc);
						continue;
					}
					boolean present = method ? runtime.methods.contains(declaring + "." + officialName + desc)
							: runtime.fields.contains(declaring + "." + officialName + desc);
					if(present) {
						if(declaring.equals(owner)) {
							direct++;
						} else {
							inherited++;
						}
						rewrite.put(owner + "\t" + (method ? "M" : "F") + "\t" + name, officialName);
						continue;
					}
					bump(reasons, runtime.has(owner) ? "owner present, member absent" : "owner class absent");
					samples.putIfAbsent("member absent", owner + "." + name + " -> " + declaring + "."
							+ officialName + desc);
				}
			}
		}

		int resolved = direct + inherited;
		System.out.println("SRG references: " + total);
		System.out.println("resolved:       " + resolved + " ("
				+ String.format("%.2f", total == 0 ? 0.0 : 100.0 * resolved / total) + "%)"
				+ "  = " + direct + " on the referenced class + " + inherited + " through a superclass");
		System.out.println("unresolved:     " + (total - resolved));
		System.out.println("SRG-named members the payload still declares: " + declared
				+ (firstDeclared == null ? "" : "  (first: " + firstDeclared + ")"));
		for(Map.Entry<String, Integer> entry : reasons.entrySet()) {
			System.out.println("  " + entry.getValue() + " x " + entry.getKey());
		}
		for(Map.Entry<String, String> entry : samples.entrySet()) {
			System.out.println("  sample (" + entry.getKey() + "): " + entry.getValue());
		}
		if(emit) {
			Path out = Path.of(args[1]);
			StringBuilder text = new StringBuilder();
			for(Map.Entry<String, String> entry : rewrite.entrySet()) {
				text.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n');
			}
			Files.writeString(out, text);
			System.out.println("wrote " + rewrite.size() + " rewrite pairs to " + out + " ("
					+ text.length() + " bytes)");
		}
	}

	private static void bump(Map<String, Integer> counts, String reason) {
		counts.merge(reason, 1, Integer::sum);
	}

	/** Collects the SRG-named game member references of one class. */
	private static void collect(ClassNode node, List<String[]> into) {
		for(MethodNode method : node.methods) {
			for(AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
				if(instruction instanceof FieldInsnNode field) {
					add(into, field.owner, field.name, field.desc, false);
				} else if(instruction instanceof MethodInsnNode call) {
					add(into, call.owner, call.name, call.desc, true);
				}
			}
		}
	}

	private static void add(List<String[]> into, String owner, String name, String desc, boolean method) {
		if(owner.startsWith("net/minecraft/") && SRG_NAME.matcher(name).matches()) {
			into.add(new String[] {owner, name, desc, method ? "M" : "F"});
		}
	}
}
