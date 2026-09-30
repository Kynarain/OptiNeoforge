/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.optifine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Brings the slot layout of the payload's copy of a class up to the runtime's, when the runtime grew it.
 *
 * <p>Restoring a member is not always enough, and this tool exists because that was measured rather than
 * reasoned about. {@code ModelDiscovery$ModelWrapper} keeps its per-model values in an
 * {@code AtomicReferenceArray} whose size, the bound checked by its {@code slot(int)} helper and the
 * {@code SLOT_COUNT} constant are all compiled in from the same number. NeoForge added an eighth slot to it
 * ({@code KEY_ADDITIONAL_PROPERTIES}, with {@code getTopAdditionalProperties()} on
 * {@code ResolvedModelExtension}), while OptiFine's copy of the class was compiled against the seven-slot
 * vanilla layout. {@link RestoreMembers} therefore put the missing field back, with the runtime's own
 * initialiser - {@code slot(7)} - into a class that still said seven, and the class died in its static
 * initialiser:</p>
 *
 * <pre>java.lang.ExceptionInInitializerError
 *   at ModelDiscovery.&lt;init&gt;(ModelDiscovery.java:42)
 *   at ModelManager.discoverModelDependencies(ModelManager.java:201)
 * Caused by: java.lang.IndexOutOfBoundsException: Index 7 out of bounds for length 7
 *   at ModelDiscovery$ModelWrapper.slot(ModelDiscovery.java:212)
 *   at ModelDiscovery$ModelWrapper.&lt;clinit&gt;(ModelDiscovery.java:200)</pre>
 *
 * <p>The client then reported {@code Caught error loading resourcepacks, removing all selected
 * resourcepacks} and never left its loading screen. Dropping the class from the payload instead is not the
 * fix: it carries OptiFine's own {@code getContext()} and its Forge-era geometry context, and the class is
 * one of the two the payload has that mention {@code IGeometryBakingContext}.</p>
 *
 * <p>What this tool changes is only the three places that number appears - the field's constant value, the
 * bound in {@code slot(int)}, and the array size in the constructor - and it refuses to write anything
 * unless it finds all three, because a half-aligned class is a class that throws somewhere else.</p>
 *
 * <p>Usage: {@code SlotLayoutRepair <payload jar> <runtime jar> [runtime jar...] [--dry-run]}. ASCII-only.</p>
 */
public final class SlotLayoutRepair {
	/** The class name whose slot count the runtime grew, and why it is the one that is repaired. */
	private static final String MODEL_WRAPPER = "net/minecraft/client/resources/model/ModelDiscovery$ModelWrapper";
	/** The field carrying that count, and the helper whose bound has to agree with it. */
	private static final String SLOT_COUNT = "SLOT_COUNT";
	private static final String SLOT_METHOD = "slot";
	private static final String SLOT_METHOD_DESC = "(I)Lnet/minecraft/client/resources/model/ModelDiscovery$Slot;";
	private static final String CHECK_INDEX = "java/util/Objects";
	private static final String REFERENCE_ARRAY = "java/util/concurrent/atomic/AtomicReferenceArray";

	private SlotLayoutRepair() {
	}

	public static void main(String[] args) throws Exception {
		boolean dryRun = false;
		List<String> files = new ArrayList<>();
		for(String arg : args) {
			if("--dry-run".equals(arg)) {
				dryRun = true;
			} else {
				files.add(arg);
			}
		}
		if(files.size() < 2) {
			System.err.println("usage: SlotLayoutRepair <payload jar> <runtime jar> [runtime jar...] [--dry-run]");
			System.exit(2);
		}
		Path payload = Path.of(files.get(0));
		List<Path> runtime = new ArrayList<>();
		for(int index = 1; index < files.size(); index++) {
			runtime.add(Path.of(files.get(index)));
		}
		String entryName = "srg/" + MODEL_WRAPPER + ".class";
		byte[] runtimeBytes = findClass(runtime, MODEL_WRAPPER);
		if(runtimeBytes == null) {
			throw new IOException("no " + MODEL_WRAPPER + " in the runtime jars given");
		}
		Integer runtimeCount = constantValue(runtimeBytes, SLOT_COUNT);
		if(runtimeCount == null) {
			throw new IOException(MODEL_WRAPPER + " in the runtime has no " + SLOT_COUNT + " constant value");
		}

		List<String> lines = new ArrayList<>();
		boolean found = false;
		Set<String> written = new HashSet<>();
		Path temporary = payload.resolveSibling(payload.getFileName() + ".tmp");
		try(ZipFile source = new ZipFile(payload.toFile());
				ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temporary))) {
			for(java.util.Enumeration<? extends ZipEntry> it = source.entries(); it.hasMoreElements();) {
				ZipEntry entry = it.nextElement();
				String name = entry.getName();
				if(!written.add(name)) {
					continue;
				}
				if(entry.isDirectory()) {
					out.putNextEntry(new ZipEntry(name));
					out.closeEntry();
					continue;
				}
				byte[] bytes = readAll(source.getInputStream(entry));
				if(entryName.equals(name)) {
					found = true;
					bytes = repair(bytes, runtimeCount, lines);
				}
				out.putNextEntry(new ZipEntry(name));
				out.write(bytes);
				out.closeEntry();
			}
		}
		if(!found) {
			// Not every line's OptiFine build replaces this class - measured: the 1.21.11 payload has no
			// ModelDiscovery$ModelWrapper at all, and the 1.21.10/1.21.9 ones do - so absence is a result
			// rather than a failure, and the payload is written back untouched.
			Files.deleteIfExists(temporary);
			System.out.println("  " + MODEL_WRAPPER + " is not in this payload: this line's OptiFine build does"
					+ " not replace it, so there is no layout to align");
			return;
		}
		if(dryRun) {
			Files.deleteIfExists(temporary);
		} else {
			Files.move(temporary, payload, StandardCopyOption.REPLACE_EXISTING);
		}
		for(String line : lines) {
			System.out.println("  " + line);
		}
		System.out.println("slot layout repair: " + MODEL_WRAPPER + " aligned with the runtime's " + SLOT_COUNT
				+ " = " + runtimeCount + (dryRun ? " (dry run, not written)" : ""));
	}

	/** The payload class with the runtime's slot count, or unchanged when it already agrees. */
	private static byte[] repair(byte[] bytes, int runtimeCount, List<String> lines) throws IOException {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		Integer payloadCount = null;
		FieldNode countField = null;
		for(FieldNode field : node.fields) {
			if(SLOT_COUNT.equals(field.name) && field.value instanceof Integer value) {
				countField = field;
				payloadCount = value;
			}
		}
		if(payloadCount == null) {
			throw new IOException(MODEL_WRAPPER + " in the payload has no " + SLOT_COUNT + " constant value");
		}
		if(payloadCount == runtimeCount) {
			lines.add(MODEL_WRAPPER + " already has " + SLOT_COUNT + " = " + runtimeCount + ", nothing to align");
			return bytes;
		}
		countField.value = runtimeCount;
		lines.add(MODEL_WRAPPER + " " + SLOT_COUNT + ": " + payloadCount + " -> " + runtimeCount);
		boolean bound = false;
		boolean array = false;
		for(MethodNode method : node.methods) {
			if(method.instructions == null) {
				continue;
			}
			if(SLOT_METHOD.equals(method.name) && SLOT_METHOD_DESC.equals(method.desc)) {
				bound = replaceBeforeCheckIndex(method.instructions, runtimeCount);
				lines.add("  the bound in " + SLOT_METHOD + "(int) is now " + runtimeCount + ": " + bound);
			}
			if("<init>".equals(method.name)) {
				if(replaceArraySize(method.instructions, runtimeCount)) {
					array = true;
				}
			}
		}
		lines.add("  the AtomicReferenceArray size is now " + runtimeCount + ": " + array);
		if(!bound || !array) {
			// Refusing is the point: an aligned constant with a stale bound (or the other way round) is a
			// class that throws even later, and the failure would then be attributed to the wrong change.
			throw new IOException("could not find every place " + SLOT_COUNT + " reaches the payload's "
					+ MODEL_WRAPPER + " (bound found: " + bound + ", array size found: " + array
					+ "), so nothing was written");
		}
		ClassWriter writer = new ClassWriter(new ClassReader(bytes), ClassWriter.COMPUTE_MAXS);
		node.accept(writer);
		return writer.toByteArray();
	}

	/** The constant pushed immediately before the {@code Objects.checkIndex} call, set to {@code value}. */
	private static boolean replaceBeforeCheckIndex(InsnList instructions, int value) {
		for(AbstractInsnNode insn = instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if(insn instanceof MethodInsnNode call && CHECK_INDEX.equals(call.owner)
					&& "checkIndex".equals(call.name)) {
				AbstractInsnNode previous = insn.getPrevious();
				Integer current = intConstant(previous);
				// checkIndex(int, int) is called with the index first and the length second, so the bound
				// is the constant closest to the call - the one the tool looks at.
				if(current != null) {
					instructions.set(previous, integer(current, value));
					return true;
				}
			}
		}
		return false;
	}

	/** The constant handed to {@code new AtomicReferenceArray(int)}, set to {@code value}. */
	private static boolean replaceArraySize(InsnList instructions, int value) {
		for(AbstractInsnNode insn = instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if(!(insn instanceof TypeInsnNode type) || type.getOpcode() != Opcodes.NEW
					|| !REFERENCE_ARRAY.equals(type.desc)) {
				continue;
			}
			for(AbstractInsnNode next = insn.getNext(); next != null; next = next.getNext()) {
				if(next instanceof MethodInsnNode call && "<init>".equals(call.name)
						&& REFERENCE_ARRAY.equals(call.owner)) {
					Integer current = intConstant(next.getPrevious());
					if(current != null) {
						instructions.set(next.getPrevious(), integer(current, value));
						return true;
					}
					break;
				}
			}
		}
		return false;
	}

	/** The integer a constant instruction pushes, or null for anything else. */
	private static Integer intConstant(AbstractInsnNode insn) {
		if(insn instanceof InsnNode plain) {
			int opcode = plain.getOpcode();
			if(opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
				return opcode - Opcodes.ICONST_0;
			}
			return null;
		}
		if(insn instanceof IntInsnNode integer) {
			return integer.operand;
		}
		if(insn instanceof LdcInsnNode ldc && ldc.cst instanceof Integer value) {
			return value;
		}
		return null;
	}

	/** The same constant instruction with a new value, so nothing else about the code moves. */
	private static AbstractInsnNode integer(int was, int value) {
		if(was >= -1 && was <= 5 && value >= -1 && value <= 5) {
			return new InsnNode(Opcodes.ICONST_0 + value);
		}
		if(value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
			return new IntInsnNode(Opcodes.BIPUSH, value);
		}
		if(value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
			return new IntInsnNode(Opcodes.SIPUSH, value);
		}
		return new LdcInsnNode(value);
	}

	/** The ConstantValue of a static final int field of a class, or null when it has none. */
	private static Integer constantValue(byte[] bytes, String name) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		for(FieldNode field : node.fields) {
			if(name.equals(field.name) && field.value instanceof Integer value) {
				return value;
			}
		}
		return null;
	}

	private static byte[] findClass(List<Path> jars, String name) throws IOException {
		String entryName = name + ".class";
		for(Path jar : jars) {
			try(ZipFile zip = new ZipFile(jar.toFile())) {
				ZipEntry entry = zip.getEntry(entryName);
				if(entry != null) {
					return readAll(zip.getInputStream(entry));
				}
			}
		}
		return null;
	}

	private static byte[] readAll(InputStream stream) throws IOException {
		try(stream) {
			return stream.readAllBytes();
		}
	}
}
