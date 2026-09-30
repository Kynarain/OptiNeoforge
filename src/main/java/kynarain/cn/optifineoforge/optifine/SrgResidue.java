/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.optifine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
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
 * Lists every SRG-shaped member reference a rewritten payload still makes, and says why each survived.
 *
 * <p>{@link SrgRemap} reports a total ("91 could not be resolved") and five samples, which is enough to
 * know a pass was imperfect and not enough to know what to fix. This is the same pass with the name of
 * every survivor and the branch it fell down, grouped so that one line stands for a whole family:</p>
 *
 * <pre>13 x no table entry            the mapping join produced nothing for this owner
 * 52 x the member changed shape   the table translated it, the runtime does not declare the result
 * 10 x occupied in the payload     the rename was refused: this class already declares that name
 * 16 x other reference forms       a class-name owner, or a name that is not the one being called</pre>
 *
 * <p>It exists because the alternative is fixing one crash at a time. A residue is only visible when the
 * code path that carries it runs, so {@code Mob}'s {@code Level.m_7654_()} needed an entity to tick while
 * the same defect in {@code PacketUtils} and {@code ChunkMap$TrackedEntity} is still latent - the fourth
 * column of this output is what makes the latent ones visible before they crash.</p>
 *
 * <p>The classification mirrors the rewriter exactly rather than approximating it, because a residue is
 * only actionable when the reason is the rewriter's own: it walks the same hierarchy in the same order and
 * applies the same two tests (does the table translate the name, and does the runtime declare the
 * translation). The only extra check is the one that explains a residue the table <em>can</em> translate:
 * whether the payload class itself already declares that name and descriptor, which is the collision the
 * rewriter refuses by design.</p>
 */
public final class SrgResidue {
	/** SRG field and method names, which is exactly what a rewrite has to remove. */
	private static final Pattern SRG_NAME = Pattern.compile("^[mf]_\\d+_$");

	private SrgResidue() {
	}

	/** How many distinct references each reason covers, in the order a reader should care about them. */
	private static final String[] REASONS = {
			"no table entry",
			"the member changed shape",
			"occupied in the payload",
			"no owner class in the runtime",
			"resolvable, but the rewrite left it"
	};

	/**
	 * Development aid: {@code SrgResidue <mcp joined.tsrg> <neoform merged> <payload jar>
	 * <runtime jar> [more runtime jars...]}.
	 *
	 * <p>The payload may be the pre-rewrite jar, the rewritten one, or an assembled jar whose patched
	 * classes sit under a prefix - the entry name is irrelevant, only the class inside it is read.</p>
	 */
	public static void main(String[] args) throws IOException {
		if(args.length < 4) {
			System.err.println("usage: SrgResidue <mcp joined.tsrg> <neoform merged> <payload jar>"
					+ " <runtime jar> [more runtime jars...]");
			System.exit(2);
		}
		Path mcp = Path.of(args[0]);
		Path merged = Path.of(args[1]);
		Path payload = Path.of(args[2]);
		SrgMemberMap map = SrgMemberMap.build(mcp, merged);
		SrgMemberMap.RuntimeIndex runtime = new SrgMemberMap.RuntimeIndex();
		for(int index = 3; index < args.length; index++) {
			runtime.add(Path.of(args[index]));
		}
		// The payload contributes its own supertypes, exactly as it does to the rewrite: a reference through
		// one of OptiFine's own classes (BlockPosM extends BlockPos) is only reachable that way.
		runtime.addHierarchy(payload);
		System.out.println("runtime index: " + runtime.methods.size() + " methods, " + runtime.fields.size()
				+ " fields, " + runtime.superOf.size() + " classes with a superclass");

		Map<String, Integer> byReason = new TreeMap<>();
		// One entry per (reason, reference) so a reference made from forty classes is one line, and one entry
		// per class so the reader can see where the work is.
		Map<String, String> samples = new LinkedHashMap<>();
		Map<String, TreeSet<String>> classesOf = new TreeMap<>();
		int total = 0;
		int declared = 0;
		int live = 0;
		int inert = 0;

		// The classes this jar itself provides, and every member each of them declares. A residue's owner
		// being one of them is what separates a latent crash from a naming that is merely stale: a payload
		// class that declares a member under its SRG name and calls it under the same name is
		// self-consistent, while a reference into a game class the payload does not replace has nothing to
		// satisfy it once the game keeps the official name.
		Map<String, TreeSet<String>> declaredMembers = new LinkedHashMap<>();
		try(ZipFile zip = new ZipFile(payload.toFile())) {
			for(Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
				ZipEntry entry = entries.nextElement();
				if(!entry.getName().endsWith(".class") || SrgRemap.isUnusedNamespace(entry.getName())) {
					continue;
				}
				ClassNode node = new ClassNode();
				try(InputStream stream = zip.getInputStream(entry)) {
					new ClassReader(stream.readAllBytes()).accept(node, ClassReader.SKIP_DEBUG);
				}
				TreeSet<String> members = new TreeSet<>();
				declaredMembers.put(node.name, members);
				for(FieldNode field : node.fields) {
					members.add(field.name + ":" + field.desc);
					if(SRG_NAME.matcher(field.name).matches()) {
						declared++;
					}
				}
				for(MethodNode method : node.methods) {
					members.add(method.name + method.desc);
					if(SRG_NAME.matcher(method.name).matches()) {
						declared++;
					}
				}
			}
		}

		try(ZipFile zip = new ZipFile(payload.toFile())) {
			for(Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
				ZipEntry entry = entries.nextElement();
				if(!entry.getName().endsWith(".class") || SrgRemap.isUnusedNamespace(entry.getName())) {
					continue;
				}
				ClassNode node = new ClassNode();
				try(InputStream stream = zip.getInputStream(entry)) {
					new ClassReader(stream.readAllBytes()).accept(node, ClassReader.SKIP_DEBUG);
				}
				for(MethodNode method : node.methods) {
					for(AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
							instruction = instruction.getNext()) {
						String owner;
						String name;
						String descriptor;
						boolean isMethod;
						if(instruction instanceof MethodInsnNode call) {
							owner = call.owner;
							name = call.name;
							descriptor = call.desc;
							isMethod = true;
						} else if(instruction instanceof FieldInsnNode field) {
							owner = field.owner;
							name = field.name;
							descriptor = field.desc;
							isMethod = false;
						} else {
							continue;
						}
						if(!SRG_NAME.matcher(name).matches()) {
							continue;
						}
						total++;
						String reason = classify(map, runtime, node, owner, name, descriptor, isMethod);
						String reference = owner + "." + name + (isMethod ? descriptor : ":" + descriptor);
						if(reason != null) {
							byReason.merge(reason, 1, Integer::sum);
							// Live means nothing in this jar can answer the reference: the owner is a game
							// class, or a payload class that does not declare this member itself. Counted per
							// distinct reference, which is the unit a reader acts on.
							boolean answerable = answerableByPayload(runtime, declaredMembers, owner, name,
									descriptor, isMethod);
							String key = reason + " | " + reference;
							if(samples.putIfAbsent(key, node.name + " calls " + reference + "  [" + reason + "]  "
									+ (answerable ? "inert: this jar declares it" : "LIVE")) == null) {
								if(answerable) {
									inert++;
								} else {
									live++;
								}
							}
							classesOf.computeIfAbsent(key, unused -> new TreeSet<>()).add(node.name);
						}
					}
				}
			}
		}

		System.out.println("SRG-shaped member references: " + total);
		System.out.println("SRG-named members the payload still declares: " + declared);
		if(samples.isEmpty()) {
			System.out.println("residue: none - every SRG reference resolves to a runtime member");
			return;
		}
		System.out.println("residue: " + samples.size() + " distinct reference(s), of which " + live
				+ " nothing in the jar can answer (a latent NoSuchMethodError/NoSuchFieldError) and " + inert
				+ " are answered by the payload's own declaration");
		for(String reason : REASONS) {
			List<Map.Entry<String, String>> group = new ArrayList<>();
			for(Map.Entry<String, String> entry : samples.entrySet()) {
				if(entry.getKey().startsWith(reason + " | ")) {
					group.add(entry);
				}
			}
			if(group.isEmpty()) {
				continue;
			}
			System.out.println();
			System.out.println("  " + byReason.getOrDefault(reason, 0) + " x " + reason);
			for(Map.Entry<String, String> entry : group) {
				TreeSet<String> callers = classesOf.get(entry.getKey());
				System.out.println("    " + entry.getValue() + (callers.size() > 1
						? "   (from " + callers.size() + " classes)" : ""));
			}
		}
	}

	/**
	 * Whether the payload's own classes declare the member this reference names, anywhere up the hierarchy.
	 */
	private static boolean answerableByPayload(SrgMemberMap.RuntimeIndex runtime,
			Map<String, TreeSet<String>> declaredMembers, String owner, String name, String descriptor,
			boolean method) {
		String wanted = name + (method ? descriptor : ":" + descriptor);
		for(String candidate : runtime.hierarchy(owner)) {
			TreeSet<String> members = declaredMembers.get(candidate);
			if(members != null && members.contains(wanted)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Why this reference would survive the rewrite, or null when it would not - the rewriter's own two tests,
	 * in its own order, plus the collision it refuses.
	 */
	private static String classify(SrgMemberMap map, SrgMemberMap.RuntimeIndex runtime, ClassNode caller,
			String owner, String name, String descriptor, boolean method) {
		boolean tableHit = false;
		boolean runtimeHas = false;
		boolean ownerKnown = false;
		for(String candidate : runtime.hierarchy(owner)) {
			if(runtime.has(candidate)) {
				ownerKnown = true;
			}
			String official = method ? map.method(candidate, name) : map.field(candidate, name);
			if(official == null) {
				continue;
			}
			tableHit = true;
			boolean present = method ? runtime.methods.contains(candidate + "." + official + descriptor)
					: runtime.fields.contains(candidate + "." + official + descriptor);
			if(present) {
				runtimeHas = true;
				// The rewriter's refusal: a rename that lands on a name this class already declares. Tested
				// against the declaration shape only, exactly as the rewriter tests it.
				if(caller.name.equals(owner) && occupies(caller, official, descriptor, method)) {
					return "occupied in the payload";
				}
				break;
			}
		}
		if(runtimeHas) {
			// Nothing left to explain: the rewriter had everything it needed and this reference is still SRG,
			// which means the reference never reached it rather than that it could not be translated.
			return "resolvable, but the rewrite left it";
		}
		if(!tableHit) {
			return "no table entry";
		}
		if(!ownerKnown) {
			return "no owner class in the runtime";
		}
		return "the member changed shape";
	}

	/** Whether the class declares this name and descriptor itself, which is what makes a rename a collision. */
	private static boolean occupies(ClassNode node, String official, String descriptor, boolean method) {
		if(method) {
			for(MethodNode candidate : node.methods) {
				if(candidate.name.equals(official) && candidate.desc.equals(descriptor)) {
					return true;
				}
			}
			return false;
		}
		for(FieldNode candidate : node.fields) {
			if(candidate.name.equals(official) && candidate.desc.equals(descriptor)) {
				return true;
			}
		}
		return false;
	}
}
