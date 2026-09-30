/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.optifine;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The classes a payload ships under a name the runtime does not use, and the name the runtime does use.
 *
 * <p>Nested class names are assigned by whoever built the artefact, and the two artefacts here were
 * built differently: OptiFine's payload takes its names from the obfuscated jar, the runtime's from
 * NeoForm. Measured on 1.20.1, and the case that put this in the startup path:</p>
 *
 * <pre>payload: net/minecraft/client/particle/ParticleEngine$ParticleDefinition
 * runtime: net/minecraft/client/particle/ParticleEngine$1ParticleDefinition
 * both   : record with (ResourceLocation, Optional&lt;List&lt;ResourceLocation&gt;&gt;)</pre>
 *
 * <p>One class, two names. Without a bridge the swapped {@code ParticleEngine} calls its own name, the
 * runtime never has that class, and ModLauncher - which only calls a transformer for a class the
 * runtime already has - never installs the payload's copy, so the call fails with
 * {@code NoSuchMethodError}. Shipping the payload's class <em>as</em> the runtime's name is the repair:
 * the index then names the class the runtime asks for.</p>
 *
 * <p>Which runtime class is the counterpart is decided by structure, not by guessing from the number:
 * same superclass, same multiset of field descriptors, same multiset of method descriptors. Member
 * <em>names</em> cannot be used - they are part of what differs - and a candidate set that is not
 * exactly one is left alone and reported, because a wrong pairing would install the wrong class.</p>
 */
public final class NestedNameBridge {
	/** Where the payload keeps the classes it replaces; the runtime keeps them at their own names. */
	private static final String PAYLOAD_ROOT = "srg/";
	/** OptiFine's own classes share that root and are not game classes. */
	private static final String OPTIFINE_PACKAGE = "net/optifine/";

	private NestedNameBridge() {
	}

	/** Payload class name to the runtime class name it must be shipped as, for the pairs found. */
	public static Map<String, String> pairs(Path payload, List<Path> runtimeJars) throws IOException {
		Map<String, ClassNode> payloadNodes = new TreeMap<>();
		try(ZipFile zip = new ZipFile(payload.toFile())) {
			for(Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
				ZipEntry entry = it.nextElement();
				String name = entry.getName();
				if(!name.startsWith(PAYLOAD_ROOT) || !name.endsWith(".class")) {
					continue;
				}
				String internal = name.substring(PAYLOAD_ROOT.length(), name.length() - ".class".length());
				if(internal.startsWith(OPTIFINE_PACKAGE) || internal.indexOf('$') < 0) {
					continue;
				}
				payloadNodes.put(internal, read(zip.getInputStream(entry)));
			}
		}
		if(payloadNodes.isEmpty()) {
			return Map.of();
		}

		// Which runtime names exist at all, and the nodes for the outer classes the payload mentions.
		Set<String> runtimeNames = new LinkedHashSet<>();
		Set<String> outerPrefixes = new LinkedHashSet<>();
		for(String internal : payloadNodes.keySet()) {
			outerPrefixes.add(internal.substring(0, internal.indexOf('$')) + "$");
		}
		Map<String, ClassNode> runtimeNodes = new TreeMap<>();
		for(Path jar : runtimeJars) {
			try(ZipFile zip = new ZipFile(jar.toFile())) {
				for(Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
					ZipEntry entry = it.nextElement();
					String name = entry.getName();
					if(!name.endsWith(".class")) {
						continue;
					}
					String internal = name.substring(0, name.length() - ".class".length());
					if(internal.indexOf('$') < 0 || !runtimeNames.add(internal)) {
						continue;
					}
					for(String prefix : outerPrefixes) {
						if(internal.startsWith(prefix)) {
							runtimeNodes.put(internal, read(zip.getInputStream(entry)));
							break;
						}
					}
				}
			}
		}

		Set<String> taken = new LinkedHashSet<>();
		Map<String, String> pairs = new LinkedHashMap<>();
		int unpaired = 0;
		for(Map.Entry<String, ClassNode> entry : payloadNodes.entrySet()) {
			String internal = entry.getKey();
			if(runtimeNames.contains(internal)) {
				continue; // same name on both sides: nothing to bridge
			}
			String prefix = internal.substring(0, internal.indexOf('$')) + "$";
			List<String> candidates = new ArrayList<>();
			List<String> matches = new ArrayList<>();
			for(Map.Entry<String, ClassNode> candidate : runtimeNodes.entrySet()) {
				if(!candidate.getKey().startsWith(prefix) || taken.contains(candidate.getKey())) {
					continue;
				}
				candidates.add(candidate.getKey());
				// Strict first: the same superclass and the same field and method descriptors. Then the
				// relaxed rule for records, whose two copies differ by construction in member *names*
				// while their component descriptors stay the same. This relaxation exists because the
				// strict rule silently declined on 1.20.2 - NeoForge's copy of that record has extra
				// members - and the launch then failed much later with an unrelated-looking
				// NoSuchMethodError.
				if(sameShape(entry.getValue(), candidate.getValue())
						|| sameFields(entry.getValue(), candidate.getValue())) {
					matches.add(candidate.getKey());
				}
			}
			if(matches.size() == 1) {
				pairs.put(internal, matches.get(0));
				taken.add(matches.get(0));
				continue;
			}
			// Anything not paired out loud: a silent decline resurfaces as a failure somewhere else.
			unpaired++;
			if(matches.isEmpty()) {
				System.err.println("unpaired nested name: " + internal + " (no runtime class of that name, "
						+ candidates.size() + " candidate(s) under " + prefix + ")");
				for(String candidate : candidates) {
					System.err.println("    " + candidate + " : "
							+ describe(entry.getValue(), runtimeNodes.get(candidate)));
				}
			} else {
				System.err.println("ambiguous bridge for " + internal + ": " + matches);
			}
		}
		if(unpaired > 0) {
			System.err.println("nested names left unpaired: " + unpaired);
		}
		return pairs;
	}

	/** A one-line reason why two classes were not taken for each other, for the report above. */
	private static String describe(ClassNode left, ClassNode right) {
		if(!sameName(left.superName, right.superName)) {
			return "superclass " + left.superName + " vs " + right.superName;
		}
		if(!fieldDescriptors(left).equals(fieldDescriptors(right))) {
			return "field descriptors differ (" + fieldDescriptors(left).size() + " vs "
					+ fieldDescriptors(right).size() + ")";
		}
		return "field descriptors match; method descriptors differ";
	}

	/**
	 * Whether two classes are the same class under different names: same superclass, same field
	 * descriptors, same method descriptors. Names are deliberately not compared.
	 */
	private static boolean sameShape(ClassNode left, ClassNode right) {
		return sameName(left.superName, right.superName)
				&& descriptorsOf(left).equals(descriptorsOf(right));
	}

	/** The relaxed rule: same superclass and the same component descriptors, member names aside. */
	private static boolean sameFields(ClassNode left, ClassNode right) {
		return sameName(left.superName, right.superName)
				&& !fieldDescriptors(left).isEmpty()
				&& fieldDescriptors(left).equals(fieldDescriptors(right));
	}

	private static Set<String> fieldDescriptors(ClassNode node) {
		Set<String> all = new TreeSet<>();
		for(FieldNode field : node.fields) {
			all.add(field.desc);
		}
		return all;
	}

	private static Set<String> descriptorsOf(ClassNode node) {
		Set<String> all = new TreeSet<>(fieldDescriptors(node));
		for(MethodNode method : node.methods) {
			all.add("M " + method.desc);
		}
		return all;
	}

	private static boolean sameName(String left, String right) {
		return left == null ? right == null : left.equals(right);
	}

	private static ClassNode read(InputStream stream) throws IOException {
		ClassNode node = new ClassNode();
		new ClassReader(stream.readAllBytes()).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
		return node;
	}

	public static void main(String[] args) throws IOException {
		// usage: NestedNameBridge <payload jar> <runtime jar> [more runtime jars...]
		//        NestedNameBridge --rewrite <in jar> <out jar> <runtime jar> [more...]
		if(args.length > 0 && "--rewrite".equals(args[0])) {
			if(args.length < 4) {
				System.err.println("usage: NestedNameBridge --rewrite <in jar> <out jar> <runtime jar> [more...]");
				System.exit(2);
			}
			List<Path> jars = new ArrayList<>();
			for(int index = 3; index < args.length; index++) {
				jars.add(Path.of(args[index]));
			}
			Map<String, String> found = pairs(Path.of(args[1]), jars);
			int rewritten = rewrite(Path.of(args[1]), Path.of(args[2]), found);
			System.err.println("rewrote references in " + rewritten + " classes for " + found.size() + " paired name(s)");
			return;
		}
		if(args.length < 2) {
			System.err.println("usage: NestedNameBridge <payload jar> <runtime jar> [more...]");
			System.err.println("       NestedNameBridge --rewrite <in jar> <out jar> <runtime jar> [more...]");
			System.exit(2);
		}
		List<Path> runtime = new ArrayList<>();
		for(int index = 1; index < args.length; index++) {
			runtime.add(Path.of(args[index]));
		}
		Map<String, String> pairs = pairs(Path.of(args[0]), runtime);
		for(Map.Entry<String, String> pair : pairs.entrySet()) {
			System.out.println(pair.getKey() + "\t" + pair.getValue());
		}
		System.err.println("bridged nested names: " + pairs.size());
		for(Map.Entry<String, String> pair : pairs.entrySet()) {
			System.err.println("  " + pair.getKey() + " -> " + pair.getValue());
		}
	}

	/**
	 * Copies {@code in} to {@code out} with every reference to a paired name rewritten to the name the
	 * runtime uses, and with the paired classes themselves carrying the runtime's name internally.
	 *
	 * <p>Both halves are needed, and the launch is what proved it: shipping the payload's class under
	 * the runtime's name alone moved the class and left the payload's own <em>references</em> spelling
	 * the old name - the swapped {@code ParticleEngine} still called
	 * {@code ParticleEngine$ParticleDefinition}, so after the move not even that name existed. The
	 * rewrite is the same ASM machinery {@link SrgRemap} uses for member names, applied to class names.
	 *
	 * @return the number of classes whose bytes changed
	 */
	public static int rewrite(Path in, Path out, Map<String, String> pairs) throws IOException {
		if(pairs.isEmpty()) {
			java.nio.file.Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			return 0;
		}
		Path parent = out.toAbsolutePath().getParent();
		if(parent != null) {
			java.nio.file.Files.createDirectories(parent);
		}
		int changed = 0;
		try(ZipFile source = new ZipFile(in.toFile());
				java.util.zip.ZipOutputStream sink = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(out))) {
			for(Enumeration<? extends ZipEntry> it = source.entries(); it.hasMoreElements();) {
				ZipEntry entry = it.nextElement();
				byte[] data;
				try(InputStream stream = source.getInputStream(entry)) {
					data = stream.readAllBytes();
				}
				String name = entry.getName();
				if(name.endsWith(".class") && !entry.isDirectory()) {
					String internal = name.substring(0, name.length() - ".class".length());
					// The pairs are keyed by class name, without the payload root the entry carries. Looking
					// them up with the root included silently missed and renamed nothing: the file kept its
					// old name while the class inside - remapped from the constant pool, where the name has
					// no root - got the runtime's. ModLauncher looks classes up by entry path, so it found
					// the runtime's own copy instead of ours and the launch failed much later with
					// NoSuchMethodError on an SRG-named accessor.
					String lookup = internal.startsWith(PAYLOAD_ROOT) ? internal.substring(PAYLOAD_ROOT.length()) : internal;
					String renamed = pairs.getOrDefault(lookup, lookup);
					byte[] mapped = remap(data, pairs);
					if(!java.util.Arrays.equals(mapped, data)) {
						changed++;
					}
					data = mapped;
					name = (internal.startsWith(PAYLOAD_ROOT) ? PAYLOAD_ROOT : "") + renamed + ".class";
				}
				ZipEntry copy = new ZipEntry(name);
				copy.setTime(entry.getTime());
				sink.putNextEntry(copy);
				sink.write(data);
				sink.closeEntry();
			}
		}
		return changed;
	}

	private static byte[] remap(byte[] bytes, Map<String, String> pairs) {
		ClassNode node = new ClassNode();
		ClassReader reader = new ClassReader(bytes);
		reader.accept(new org.objectweb.asm.commons.ClassRemapper(node, new org.objectweb.asm.commons.Remapper() {
			@Override
			public String map(String internalName) {
				return pairs.getOrDefault(internalName, internalName);
			}
		}), 0);
		org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0);
		node.accept(writer);
		return writer.toByteArray();
	}
}
