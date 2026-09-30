/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.optifine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Rewrites an OptiFine payload's SRG member names to the official ones NeoForge's runtime uses.
 *
 * <p>This is a build step, not a loader transformer, and that is the point: this project already
 * repacks OptiFine's jar and patches the game jar offline, so every class that needs new member names
 * is in hand before the game starts. Doing it here also sidesteps ModLauncher, whose transformers have
 * to declare their targets up front - and the classes to rewrite are thousands.</p>
 *
 * <p>{@link ClassRemapper} is used rather than editing references by hand because a payload's SRG
 * names appear in <em>declarations</em> as well as in references: OptiFine's replacement of a game
 * class declares its own members, and it declares them with SRG names, so a class that only had its
 * call sites rewritten would satisfy nobody. The remapper maps both through the same method, which is
 * what keeps a declaration and the calls to it consistent.</p>
 *
 * <p>Class names are left alone: since 1.17 the payload already uses official class names and only
 * the members are SRG. Descriptors are left alone for the same reason.</p>
 *
 * <p>On a line where OptiFine already speaks official names (1.20.6 and later) this finds no SRG
 * names and copies the jar through unchanged, so it is safe to leave in every line's pipeline.</p>
 */
public final class SrgRemap {
	/** SRG field and method names, which is exactly what has to be rewritten. */
	private static final Pattern SRG_NAME = Pattern.compile("^[mf]_\\d+_$");

	private SrgRemap() {
	}

	/** What one pass did, so a build can print something checkable instead of "done". */
	public static final class Report {
		int methodNames;
		int fieldNames;
		int stringConstants;
		int collisions;
		final Map<String, Integer> misses = new TreeMap<>();
		final Map<String, String> missSamples = new LinkedHashMap<>();

		public int rewritten() {
			return methodNames + fieldNames;
		}

		public int missed() {
			int total = 0;
			for(int count : misses.values()) {
				total += count;
			}
			return total;
		}

		/** SRG names found as string constants, which the remapper cannot touch. */
		public int stringConstants() {
			return stringConstants;
		}

		public String describe() {
			return "rewrote " + methodNames + " method and " + fieldNames + " field names, "
					+ missed() + " could not be resolved, " + collisions
					+ " renames refused because the class already has that name, " + stringConstants
					+ " SRG-shaped string constants were left alone";
		}
	}

	/**
	 * Whether an entry belongs to a namespace this project does not use.
	 *
	 * <p>OptiFine's jar holds two copies of every one of its own classes: {@code srg/**} against the
	 * renamed game and {@code notch/**} against the obfuscated one. Only {@code srg/**} is ever
	 * loaded - OptiFine's own transformer reads {@code patch/srg} and {@code srg} and never touches
	 * {@code notch} - and mixing the two is not a harmless redundancy: both copies carry the same
	 * class name, so the obfuscated one silently wins whichever record is written second. That is
	 * exactly what happened here, leaving {@code net.optifine.BlockPosM}'s supertype as {@code hx}
	 * and hiding every member it inherits.</p>
	 */
	static boolean isUnusedNamespace(String entryName) {
		return entryName.startsWith("notch/") || entryName.startsWith("patch/notch/");
	}

	/**
	 * Rewrites every class of {@code in} into {@code out}, resolving names against the runtime jars.
	 */
	public static Report rewrite(SrgMemberMap map, SrgMemberMap.RuntimeIndex runtime, Path in, Path out)
			throws IOException {
		Report report = new Report();
		Renamer renamer = new Renamer(map, runtime, report);

		try(ZipFile zip = new ZipFile(in.toFile());
				ZipOutputStream sink = new ZipOutputStream(Files.newOutputStream(out))) {
			for(Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
				ZipEntry entry = entries.nextElement();
				byte[] data;
				try(InputStream stream = zip.getInputStream(entry)) {
					data = stream.readAllBytes();
				}
				if(entry.getName().endsWith(".class") && !isUnusedNamespace(entry.getName())) {
					data = rewriteClass(data, renamer, report);
				}
				ZipEntry copy = new ZipEntry(entry.getName());
				copy.setTime(entry.getTime());
				sink.putNextEntry(copy);
				sink.write(data);
				sink.closeEntry();
			}
		}
		return report;
	}

	/** Rewrites one class body, and counts the SRG names that survive as string constants. */
	private static byte[] rewriteClass(byte[] data, Renamer renamer, Report report) {
		ClassReader reader = new ClassReader(data);
		ClassWriter writer = new ClassWriter(0);

		ClassNode node = new ClassNode();
		reader.accept(node, ClassReader.SKIP_DEBUG);
		// Declarations are checked against this class's own members, so it has to be in hand first.
		renamer.current = node;
		reader.accept(new ClassRemapper(writer, renamer), 0);

		for(MethodNode method : node.methods) {
			for(AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
				if(instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String text
						&& SRG_NAME.matcher(text).matches()) {
					report.stringConstants++;
				}
			}
		}
		return writer.toByteArray();
	}

	/**
	 * The rewriter, which refuses renames that would make the class unloadable.
	 *
	 * <p>Measured, not imagined: without this guard {@code ModelPart} came out with two methods named
	 * {@code getChild(String)ModelPart} - javap shows one before the rewrite and two after - and the JVM
	 * rejected the class with {@code ClassFormatError: Duplicate method name}. OptiFine's payload is a
	 * mixture: some members are already named the way the runtime names them and others are still SRG,
	 * so a rename can land on a name the class already declares. Refusing that rename keeps the class
	 * loadable; the member keeps its SRG name and is reported.</p>
	 */
	private static final class Renamer extends Remapper {
		private final SrgMemberMap map;
		private final SrgMemberMap.RuntimeIndex runtime;
		private final Report report;
		ClassNode current;

		Renamer(SrgMemberMap map, SrgMemberMap.RuntimeIndex runtime, Report report) {
			// The API-version constructor, and it matters - not for style. Changing it to the
			// no-argument one so that gradlew build would compile against ASM 9.8 (which has no
			// Remapper(int)) silently changed what the remap produces: the payload went from 4165109
			// to 4165610 bytes, and 1.21 then died with
			//   AbstractMethodError: Receiver class net.minecraft.client.particle.ParticleEngine$$Lambda
			//   does not define or inherit an implementation of 'ParticleProvider create(SpriteSet)'
			// at ParticleEngine.register. The two constructors do not mean the same thing on ASM
			// 9.10.1, so this stays exactly as the line was verified with; the Gradle build gets ASM
			// 9.10.1 through the resolution strategy in build.gradle instead.
			super(Opcodes.ASM9);
			this.map = map;
			this.runtime = runtime;
			this.report = report;
		}

		@Override
		public String mapMethodName(String owner, String name, String descriptor) {
			if(!SRG_NAME.matcher(name).matches()) {
				return name;
			}
			String official = resolve(map, runtime, owner, name, descriptor, true);
			if(official == null) {
				miss(report, map, runtime, owner, name, descriptor, true);
				return name;
			}
			if(occupied(owner, official, descriptor, true)) {
				report.collisions++;
				report.missSamples.putIfAbsent("would duplicate " + official,
						owner + "." + name + " -> " + official + descriptor);
				return name;
			}
			report.methodNames++;
			return official;
		}

		@Override
		public String mapFieldName(String owner, String name, String descriptor) {
			if(!SRG_NAME.matcher(name).matches()) {
				return name;
			}
			String official = resolve(map, runtime, owner, name, descriptor, false);
			if(official == null) {
				miss(report, map, runtime, owner, name, descriptor, false);
				return name;
			}
			if(occupied(owner, official, descriptor, false)) {
				report.collisions++;
				report.missSamples.putIfAbsent("would duplicate " + official,
						owner + "." + name + " -> " + official + descriptor);
				return name;
			}
			report.fieldNames++;
			return official;
		}

		/**
		 * Whether renaming would collide with a member this class already declares.
		 *
		 * <p>Only declarations can collide, and <b>this check used to run for references too</b>, because
		 * {@code mapMethodName} is called for both and the two are told apart by the owner: for a
		 * declaration the owner is the class being rewritten, for a reference it is the class being
		 * called. Getting that wrong left {@code Minecraft.m_91097_()} unrenamed inside
		 * {@code net.optifine.Config}, whose own method is also called {@code getTextureManager}, so the
		 * rewritten class called an SRG name that no longer exists:</p>
		 *
		 * <pre>NoSuchMethodError: 'TextureManager net.minecraft.client.Minecraft.m_91097_()'
		 *   at net.optifine.Config.getTextureManager(Config.java:1187)</pre>
		 */
		private boolean occupied(String owner, String official, String descriptor, boolean method) {
			ClassNode node = current;
			if(node == null || !node.name.equals(owner)) {
				return false;
			}
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

	/**
	 * Finds the official name for an SRG name, walking the owner's ancestors.
	 *
	 * <p>The walk is needed because a reference may name a subclass for a member declared higher up,
	 * and the table is built from declaring classes.</p>
	 */
	private static String resolve(SrgMemberMap map, SrgMemberMap.RuntimeIndex runtime, String owner, String name,
			String descriptor, boolean method) {
		for(String candidate : runtime.hierarchy(owner)) {
			String official = method ? map.method(candidate, name) : map.field(candidate, name);
			if(official == null) {
				continue;
			}
			// The name only counts as resolved when the runtime really has the member: a payload
			// compiled against a different game build would otherwise be renamed into a second
			// failure instead of being reported as it is.
			boolean present = method ? runtime.methods.contains(candidate + "." + official + descriptor)
					: runtime.fields.contains(candidate + "." + official + descriptor);
			if(present) {
				return official;
			}
			// A hit that the runtime does not declare here is not the end of the search. SRG names are
			// shared by covariant overrides - Vec3i, BlockPos and MutableBlockPos all call their
			// override of below() m_7495_ - so the name can be present on the referenced class while
			// the declaration, and therefore the runtime member, lives higher up. Bailing out here
			// left 49 members renamed-or-not depending on which class the reference happened to name.
		}
		return null;
	}

	/** Records why a name could not be rewritten, distinguishing a missing entry from a moved member. */
	private static void miss(Report report, SrgMemberMap map, SrgMemberMap.RuntimeIndex runtime, String owner,
			String name, String descriptor, boolean method) {
		boolean known = false;
		for(String candidate : runtime.hierarchy(owner)) {
			if((method ? map.method(candidate, name) : map.field(candidate, name)) != null) {
				known = true;
				break;
			}
		}
		String reason = known ? "the member changed shape" : "no table entry for the " + (method ? "method" : "field");
		report.misses.merge(reason, 1, Integer::sum);
		report.missSamples.putIfAbsent(reason, owner + "." + name + descriptor);
	}

	/**
	 * Development aid: {@code SrgRemap <mcp joined.tsrg> <neoform merged> <in jar> <out jar>
	 * <runtime jar> [more runtime jars...]}.
	 *
	 * <p>Point the result at {@link SrgMemberMap}'s verifier to check the job: a payload that has been
	 * rewritten properly reports zero remaining SRG references.</p>
	 */
	public static void main(String[] args) throws IOException {
		if(args.length < 5) {
			System.err.println("usage: SrgRemap <mcp joined.tsrg> <neoform merged> <in jar> <out jar>"
					+ " <runtime jar> [more runtime jars...]");
			System.exit(2);
		}
		SrgMemberMap map = SrgMemberMap.build(Path.of(args[0]), Path.of(args[1]));
		SrgMemberMap.RuntimeIndex runtime = new SrgMemberMap.RuntimeIndex();
		for(int index = 4; index < args.length; index++) {
			runtime.add(Path.of(args[index]));
		}
		// The payload contributes its own supertypes, so an inherited member reached through one of
		// OptiFine's own classes (BlockPosM extends BlockPos) can still be found.
		runtime.addHierarchy(Path.of(args[2]));
		System.out.println("runtime index: " + runtime.methods.size() + " methods, " + runtime.fields.size()
				+ " fields, " + runtime.superOf.size() + " classes with a superclass");

		// A runtime that names its own members in SRG is the 1.20.1 shape, where OptiFine's payload and
		// the game already agree and rewriting would be the mistake. Refusing loudly is the point: run
		// by habit on the wrong line, this would otherwise rename every member to a name that line's
		// game does not have, which reads as an unrelated failure much later.
		if(srgNamedMembers(runtime) > 0) {
			System.err.println("refusing to rewrite: this runtime is SRG-named (" + srgNamedMembers(runtime)
					+ " members match m_/f_), so the payload needs no rewriting on this line");
			System.exit(2);
		}
		Report report = rewrite(map, runtime, Path.of(args[2]), Path.of(args[3]));
		System.out.println(report.describe());
		for(Map.Entry<String, Integer> entry : report.misses.entrySet()) {
			System.out.println("  " + entry.getValue() + " x " + entry.getKey());
		}
		List<String> samples = new ArrayList<>(report.missSamples.values());
		for(int index = 0; index < Math.min(5, samples.size()); index++) {
			System.out.println("  sample: " + samples.get(index));
		}
	}

	/** How many of the runtime's members still carry SRG names. */
	private static int srgNamedMembers(SrgMemberMap.RuntimeIndex runtime) {
		int count = 0;
		for(String member : runtime.methods) {
			if(SRG_NAME.matcher(member.substring(member.lastIndexOf('.') + 1, member.indexOf('('))).matches()) {
				count++;
			}
		}
		for(String member : runtime.fields) {
			int dot = member.lastIndexOf('.');
			String name = member.substring(dot + 1);
			int colon = name.indexOf(':');
			if(SRG_NAME.matcher(colon < 0 ? name : name.substring(0, colon)).matches()) {
				count++;
			}
		}
		return count;
	}
}
