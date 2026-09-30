/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.optifine;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The classes of a payload that must not be installed, because the payload's copy of the name is not a
 * patch of the runtime's class with that name.
 *
 * <p>The case this exists for is numbered nested classes. {@code Outer$N} numbers are assigned by
 * whoever produced the artefact, and the two artefacts are produced differently: OptiFine's payload
 * takes its names from the obfuscated jar, while the runtime's come from NeoForm. Measured on 1.20.1:
 * the runtime's {@code net.minecraft.Util$9} extends {@code java.lang.Thread} while the payload's
 * {@code Util$9} is the BiFunction cache class behind {@code Util.memoize}. Installing the payload's
 * copy broke the code that builds the thread:</p>
 *
 * <pre>VerifyError: Bad type on operand stack
 *   Location: net/minecraft/Util.m_137584_()V @13: invokevirtual
 *   Reason: Type 'net/minecraft/Util$9' is not assignable to 'java/lang/Thread'</pre>
 *
 * <p>The decision has to be per family rather than per class, which is why this runs at build time
 * rather than inside the transformer. An outer class and its {@code $N} members are one compilation:
 * the payload's family is internally consistent, the runtime's family is internally consistent, and
 * mixing them is consistent with neither - refusing only the mismatched member produced
 * "Type 'net/minecraft/Util$7' is not assignable to 'java/lang/Thread'" instead, because the payload's
 * {@code Util} then ran against the runtime's {@code Util$7}. So a family is skipped whole when any of
 * its members is a different class. Nothing outside the family refers to those members (they are
 * private to their outer class), so skipping the family costs its patches and breaks no caller.</p>
 *
 * <p>What the check compares is the superclass: OptiFine patches bodies, not hierarchies. The rule is
 * applied only to names containing {@code $}, because on an ordinary class a changed superclass is
 * real - OptiFine's {@code BlockEntity} extends Forge's {@code CapabilityProvider} while NeoForge's
 * extends {@code AttachmentHolder}, and the generated shim is what makes that swap work.</p>
 *
 * <p>{@link #main} prints the family names to skip, one per line, which the build feeds to the steps
 * that decide what to ship - so the same decision reaches both the payload and the transformer's
 * target index, and cannot drift between them.</p>
 */
public final class NestedFamilyGuard {
	/** Where the payload keeps the classes it replaces; the runtime keeps them at their own names. */
	private static final String PAYLOAD_ROOT = "srg/";
	/** OptiFine's own classes share that root and are not game classes: never a family to skip. */
	private static final String OPTIFINE_PACKAGE = "net/optifine/";

	private NestedFamilyGuard() {
	}

	/** The family names, dotted-free internal form, in a stable order. */
	public static List<String> familiesToSkip(Path payload, List<Path> runtimeJars) throws IOException {
		Map<String, String> payloadSupers = new TreeMap<>();
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
				try(InputStream stream = zip.getInputStream(entry)) {
					payloadSupers.put(internal, superName(stream.readAllBytes()));
				}
			}
		}

		Map<String, String> runtimeSupers = new LinkedHashMap<>();
		for(Path jar : runtimeJars) {
			try(ZipFile zip = new ZipFile(jar.toFile())) {
				for(String internal : payloadSupers.keySet()) {
					if(runtimeSupers.containsKey(internal)) {
						continue;
					}
					ZipEntry entry = zip.getEntry(internal + ".class");
					if(entry == null) {
						continue;
					}
					try(InputStream stream = zip.getInputStream(entry)) {
						runtimeSupers.put(internal, superName(stream.readAllBytes()));
					}
				}
			}
		}

		Set<String> families = new LinkedHashSet<>();
		int noCounterpart = 0;
		for(Map.Entry<String, String> entry : payloadSupers.entrySet()) {
			String internal = entry.getKey();
			if(!runtimeSupers.containsKey(internal)) {
				noCounterpart++;
				continue;
			}
			if(!sameName(entry.getValue(), runtimeSupers.get(internal))) {
				families.add(internal.substring(0, internal.indexOf('$')));
			}
		}
		if(noCounterpart > 0) {
			// Only a report: a payload member with no runtime counterpart is one OptiFine adds, and
			// there is nothing to disagree with.
			System.err.println("note: " + noCounterpart + " numbered classes have no runtime counterpart");
		}
		return new ArrayList<>(families);
	}

	private static String superName(byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
		return node.superName;
	}

	private static boolean sameName(String left, String right) {
		return left == null ? right == null : left.equals(right);
	}

	public static void main(String[] args) throws IOException {
		// usage: NestedFamilyGuard <payload jar> <runtime jar> [more runtime jars...]
		if(args.length < 2) {
			System.err.println("usage: NestedFamilyGuard <payload jar> <runtime jar> [more...]");
			System.exit(2);
		}
		List<Path> runtime = new ArrayList<>();
		for(int index = 1; index < args.length; index++) {
			runtime.add(Path.of(args[index]));
		}
		List<String> families = familiesToSkip(Path.of(args[0]), runtime);
		for(String family : families) {
			System.out.println(family);
		}
		System.err.println("families to skip: " + families.size()
				+ (families.isEmpty() ? "" : " (" + String.join(", ", families) + ")"));
	}
}
