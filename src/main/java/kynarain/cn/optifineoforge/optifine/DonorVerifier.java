/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.optifine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;

/**
 * Answers "why does this copied body not verify?" with a verifier instead of a guess.
 *
 * <p>When a body is refused, it is replaced by a stub returning a default value, and the reason is
 * only ever printed as "body would not verify". Guessing at that cost several rounds: first the
 * closure was blamed for missing members in the same class, then for missing members in other
 * classes, and neither was the cause. What is actually wrong is a question for a verifier, so this
 * tool builds the class exactly as the transformer would - replacement plus donor members, bodies
 * included, no stubbing - and hands it to ASM's verifier, printing what it objects to.</p>
 *
 * <p>The class to look at is found by asking for it by name, so a member that keeps being stubbed
 * can be tracked down without another launch.</p>
 */
public final class DonorVerifier {
	private static final String PATCHED_ROOT = "srg/";

	private DonorVerifier() {
	}

	/** The class as it would be if every missing member were copied in with its original body. */
	public static byte[] merged(Path patchedJar, Path runtimeJar, String internalName) throws IOException {
		ClassNode replacement = read(patchedJar, PATCHED_ROOT + internalName + ".class");
		ClassNode runtime = read(runtimeJar, internalName + ".class");
		if(replacement == null || runtime == null) {
			return null;
		}

		for(FieldNode field : runtime.fields) {
			if(findField(replacement, field.name, field.desc) == null && !field.name.startsWith("$") && !field.name.startsWith("this$")) {
				replacement.fields.add(new FieldNode(field.access, field.name, field.desc, field.signature, field.value));
			}
		}
		for(MethodNode method : runtime.methods) {
			if("<init>".equals(method.name) || "<clinit>".equals(method.name)
					|| method.name.startsWith("lambda$") || method.name.startsWith("access$")) {
				continue;
			}
			if(findMethod(replacement, method.name, method.desc) == null) {
				MethodNode copy = new MethodNode(method.access, method.name, method.desc, method.signature,
						method.exceptions == null ? null : method.exceptions.toArray(new String[0]));
				method.accept(copy);
				replacement.methods.add(copy);
			}
		}

		ClassWriter writer = new ClassWriter(0);
		replacement.accept(writer);
		return writer.toByteArray();
	}

	/** Runs ASM's verifier over the merged class and returns its complaints, one per line. */
	public static List<String> verify(byte[] classBytes, ClassLoader loader) {
		StringWriter output = new StringWriter();
		try {
			CheckClassAdapter.verify(new ClassReader(classBytes), loader, false, new PrintWriter(output));
		} catch(Throwable t) {
			output.write("verifier threw " + t);
		}
		List<String> lines = new ArrayList<>();
		for(String line : output.toString().split("\\R")) {
			if(!line.isBlank()) {
				lines.add(line.trim());
			}
		}
		return lines;
	}

	private static ClassNode read(Path jar, String entryName) throws IOException {
		try(ZipFile zip = new ZipFile(jar.toFile())) {
			ZipEntry entry = zip.getEntry(entryName);
			if(entry == null) {
				return null;
			}
			try(InputStream stream = zip.getInputStream(entry)) {
				ClassNode node = new ClassNode();
				new ClassReader(stream.readAllBytes()).accept(node, 0);
				return node;
			}
		}
	}

	private static MethodNode findMethod(ClassNode node, String name, String desc) {
		for(MethodNode method : node.methods) {
			if(method.name.equals(name) && method.desc.equals(desc)) {
				return method;
			}
		}
		return null;
	}

	private static FieldNode findField(ClassNode node, String name, String desc) {
		for(FieldNode field : node.fields) {
			if(field.name.equals(name) && field.desc.equals(desc)) {
				return field;
			}
		}
		return null;
	}

	/** A loader over the runtime jar and the NeoForge jar next to it, for type resolution. */
	private static ClassLoader gameLoader(Path runtimeJar) {
		List<java.net.URL> urls = new ArrayList<>();
		try {
			urls.add(runtimeJar.toUri().toURL());
			Path parent = runtimeJar.toAbsolutePath().getParent();
			if(parent != null) {
				try(java.util.stream.Stream<Path> stream = java.nio.file.Files.list(parent)) {
					for(Path candidate : stream.toList()) {
						if(candidate.toString().endsWith(".jar") && !candidate.equals(runtimeJar)) {
							urls.add(candidate.toUri().toURL());
						}
					}
				}
			}
		} catch(IOException e) {
			System.err.println("could not build a game loader: " + e);
		}
		return new java.net.URLClassLoader(urls.toArray(new java.net.URL[0]), DonorVerifier.class.getClassLoader());
	}
	/** Development aid: {@code DonorVerifier <patched jar> <runtime jar> <class name> [more names...]}. */
	public static void main(String[] args) throws IOException {
		if(args.length < 3) {
			System.err.println("usage: DonorVerifier <patched jar> <runtime jar> <class name> [more...]");
			System.exit(2);
		}
		Path patched = Path.of(args[0]);
		Path runtime = Path.of(args[1]);
		for(int index = 2; index < args.length; index++) {
			String name = args[index];
			byte[] merged = merged(patched, runtime, name);
			System.out.println("### " + name + (merged == null ? " (not found)" : "  " + merged.length + " bytes"));
			if(merged == null) {
				continue;
			}
			// The verifier has to be able to resolve the game's own types, so it gets a loader over
			// the runtime jar (and the NeoForge jar beside it) rather than this one.
			List<String> problems = verify(merged, gameLoader(runtime));
			if(problems.isEmpty()) {
				System.out.println("  verifies clean with every body copied in");
			} else {
				for(String line : problems.subList(0, Math.min(12, problems.size()))) {
					System.out.println("  " + line);
				}
				System.out.println("  (" + problems.size() + " lines)");
			}
		}
	}
}
