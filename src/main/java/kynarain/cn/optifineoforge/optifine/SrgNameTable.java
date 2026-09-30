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
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Writes the SRG-to-official member names the loader needs at transformation time.
 *
 * <p>Why this exists next to {@link SrgRemap}, which rewrites a payload offline. Rewriting the jars is not
 * enough on a line whose OptiFine build predates OptiFine's own switch to official member names, because
 * OptiFine's transformation service patches classes <em>at load time</em> from the {@code patch/srg/*.xdelta}
 * data inside its jar - binary deltas that rewriting the jar's {@code .class} entries cannot reach. Its
 * output therefore still carries SRG names, and on 1.21 that is exactly what a run hit:</p>
 *
 * <pre>NoSuchMethodError: 'ResourceMetadata Resource.m_215509_()'
 *   at SpriteResourceLoader.lambda$create$0(SpriteResourceLoader.java:32)</pre>
 *
 * <p>Measured with the loader's own {@code -Doptifineoforge.dump} switch: the class handed to the JVM
 * contains that reference while no entry in the shipped loader jar contains the string at all. So the
 * rename has to happen while transforming, and this is the offline half of it.</p>
 *
 * <p>The table is restricted to the owners the payload actually has, which is what keeps it small enough to
 * ship: names are only ever renamed inside a class being transformed, and the SRG names in those classes
 * belong to game classes the payload touches.</p>
 *
 * <p>Development aid: {@code SrgNameTable <mcp joined.tsrg> <neoform merged> <payload jar> <out file>}.</p>
 */
public final class SrgNameTable {
	private SrgNameTable() {
	}

	/** The shape of an SRG member name, the same one {@link SrgRemap} looks for. */
	private static final Pattern SRG_NAME = Pattern.compile("[fm]_\\d+_");

	public static void main(String[] args) throws IOException {
		if(args.length != 4) {
			System.err.println("usage: SrgNameTable <mcp joined.tsrg> <neoform merged> <payload jar>"
					+ " <out file>");
			System.exit(2);
		}
		SrgMemberMap map = SrgMemberMap.build(Path.of(args[0]), Path.of(args[1]));
		System.out.println("table: " + map.ownerCount() + " owners, " + map.fieldCount() + " field names, "
				+ map.methodCount() + " method names");

		// Sorted so two runs of the same inputs produce the same file, which is what lets it be compared.
		Map<String, Map<String, String>> byOwner = new TreeMap<>();
		int named = 0;
		int unresolved = 0;
		try(ZipFile zip = new ZipFile(args[2])) {
			for(Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements(); ) {
				ZipEntry entry = it.nextElement();
				if(!entry.getName().endsWith(".class") || SrgRemap.isUnusedNamespace(entry.getName())) {
					continue;
				}
				ClassNode node = new ClassNode();
				try(InputStream stream = zip.getInputStream(entry)) {
					new ClassReader(stream.readAllBytes()).accept(node, ClassReader.SKIP_DEBUG);
				}
				for(FieldNode field : node.fields) {
					if(SRG_NAME.matcher(field.name).matches()) {
						String official = map.field(node.name, field.name);
						if(official == null) {
							unresolved++;
						} else {
							byOwner.computeIfAbsent(node.name, key -> new TreeMap<>()).put(field.name, official);
							named++;
						}
					}
				}
				for(MethodNode method : node.methods) {
					if(SRG_NAME.matcher(method.name).matches()) {
						String official = map.method(node.name, method.name);
						if(official == null) {
							unresolved++;
						} else {
							byOwner.computeIfAbsent(node.name, key -> new TreeMap<>()).put(method.name, official);
							named++;
						}
					}
					// And the references, which matter more here than the declarations: what OptiFine's
					// patch data emits is mostly calls into game classes it does not declare - measured on
					// 1.21, where the failure was Resource.m_215509_() from inside an OptiFine-patched
					// caller. A table built from declarations alone misses exactly that name.
					if(method.instructions == null) {
						continue;
					}
					for(org.objectweb.asm.tree.AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
							insn = insn.getNext()) {
						String owner = null;
						String name = null;
						boolean isMethod = false;
						if(insn instanceof org.objectweb.asm.tree.MethodInsnNode call) {
							owner = call.owner;
							name = call.name;
							isMethod = true;
						} else if(insn instanceof org.objectweb.asm.tree.FieldInsnNode field) {
							owner = field.owner;
							name = field.name;
						}
						if(owner == null || !SRG_NAME.matcher(name).matches()) {
							continue;
						}
						String official = isMethod ? map.method(owner, name) : map.field(owner, name);
						if(official == null) {
							unresolved++;
						} else if(!byOwner.getOrDefault(owner, Map.of()).containsKey(name)) {
							byOwner.computeIfAbsent(owner, key -> new TreeMap<>()).put(name, official);
							named++;
						}
					}
				}
			}
		}

		StringBuilder text = new StringBuilder();
		for(Map.Entry<String, Map<String, String>> owner : byOwner.entrySet()) {
			for(Map.Entry<String, String> member : owner.getValue().entrySet()) {
				text.append(owner.getKey()).append('\t').append(member.getKey()).append('\t')
						.append(member.getValue()).append('\n');
			}
		}
		Files.writeString(Path.of(args[3]), text.toString(), StandardCharsets.UTF_8);
		System.out.println("wrote " + named + " name(s) across " + byOwner.size() + " owner(s) to " + args[3]
				+ (unresolved == 0 ? "" : ", " + unresolved + " SRG name(s) the table cannot resolve"));
	}
}
