/*
 * OptiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optineoforge.optifine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Puts the members OptiFine's replacements drop back into the payload offline.
 *
 * <p>Every class OptiFine replaces is one it compiled itself, and whatever NeoForge added to that class
 * since is not in OptiFine's copy. On the ModLauncher lines this mod's own transformer copies them in as
 * the class loads, from the plan and donors {@link MemberRestorePlan} produces; if they are missing the
 * game dies one member at a time. Measured on 26.1.2, where nothing of ours runs at load time:</p>
 *
 * <pre>
 * java.lang.NoSuchMethodError: 'void com.mojang.blaze3d.pipeline.RenderTarget.&lt;init&gt;
 *   (java.lang.String, boolean, boolean)'
 *   at com.mojang.blaze3d.pipeline.MainTarget.&lt;init&gt;(MainTarget.java:25)
 *   at net.neoforged.neoforge.client.ClientHooks.instantiateMainTarget(ClientHooks.java:973)
 * </pre>
 *
 * <p>So the same plan is applied here, before the payload is shipped: the donor's members are copied in,
 * and every constructor that does not assign a restored field itself calls the donor's initialiser for it
 * - the donor carries that sequence as a static method, exactly as the runtime transformer expects to find
 * it. Class files are written back with recomputed stack sizes, because both the copied members and the
 * inserted calls change what the frames need.</p>
 *
 * <p>Two members are deliberately not taken from their donor; see {@link #EXACT}. On the ModLauncher lines
 * they are written by a fix of their own, and the same fix is applied here, because a donor body that
 * assigns a final field does not verify.</p>
 *
 * <p>Development aid: {@code RestoreMembers <payload jar> <plan> <donors dir> <out jar>}.</p>
 */
public final class RestoreMembers {
	/** Where the patched game classes sit in the payload jar, as the other tools also read it. */
	private static final String PATCHED_ROOT = "srg/";

	/**
	 * Members that must not be restored from a donor, because the donor body is not what the caller
	 * needs: on the ModLauncher lines a fix of its own provides them, and {@link #resourceManagerFix}
	 * does the same here.
	 */
	private static final Set<String> EXACT = Set.of(
			ReloadableResourceManagerFix.RESOURCE_MANAGER + " getListeners ()Ljava/util/List;",
			ReloadableResourceManagerFix.RESOURCE_MANAGER + " updateListenersFrom (Lnet/neoforged/neoforge/event/SortedReloadListenerEvent;)V");

	private RestoreMembers() {
	}

	/**
	 * The payload with the planned members restored.
	 *
	 * @return one line per class, for the build log
	 */
	public static List<String> restore(Path payload, Path planFile, Path donorsDir, Path out, List<Path> runtimeJars)
			throws IOException {
		// The union of interfaces belongs here rather than in the re-parenting pass, and 26.1.2 is where
		// that showed: the runtime's Font implements IFontExtension, whose abstract self() the payload only
		// gains from the restore below. Checking the interface before the members are back refused it, and
		// the class then kept a shim interface with no ellipsize() while NeoForge's ExtendedButton called
		// exactly that. After this pass the class is final, so the check answers for what actually ships.
		Map<String, ClassNode> runtime = runtimeJars.isEmpty() ? Map.of() : ReparentPayload.readRuntime(runtimeJars);
		Set<String> targets = new LinkedHashSet<>();
		int planned = 0;
		for(String line : Files.readString(planFile, StandardCharsets.UTF_8).split("\\R")) {
			String[] parts = line.trim().split(" ", 4);
			if(parts.length < 4) {
				continue;
			}
			if(EXACT.contains(parts[1] + " " + parts[2] + " " + parts[3])) {
				continue;
			}
			targets.add(parts[1]);
			planned++;
		}
		Map<String, ClassNode> donors = new TreeMap<>();
		List<String> report = new ArrayList<>();
		Path parent = out.toAbsolutePath().getParent();
		if(parent != null) {
			Files.createDirectories(parent);
		}
		int restoredTotal = 0;
		int withoutDonor = 0;
		try(ZipFile zip = new ZipFile(payload.toFile());
				ZipOutputStream target = new ZipOutputStream(Files.newOutputStream(out))) {
			for(Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
				ZipEntry entry = it.nextElement();
				String name = entry.getName();
				byte[] bytes = entry.isDirectory() ? new byte[0] : readAll(zip.getInputStream(entry));
				if(!entry.isDirectory() && name.startsWith(PATCHED_ROOT) && name.endsWith(".class")) {
					String internal = name.substring(PATCHED_ROOT.length(), name.length() - ".class".length());
					boolean wanted = targets.contains(internal)
							|| ReloadableResourceManagerFix.RESOURCE_MANAGER.equals(internal)
							|| runtime.containsKey(internal);
					if(wanted) {
						ClassNode node = new ClassNode();
						new ClassReader(bytes).accept(node, 0);
						int restored = 0;
						if(targets.contains(internal)) {
							ClassNode donor = donor(donorsDir, internal, donors);
							if(donor == null) {
								withoutDonor++;
								report.add("  no donor for " + internal.replace('/', '.')
										+ "; its dropped members stay missing");
							} else {
								restored = copyMembers(node, donor, internal);
							}
						}
						if(ReloadableResourceManagerFix.RESOURCE_MANAGER.equals(internal)) {
							restored += ReloadableResourceManagerFix.apply(node);
						}
						List<String> kept = ReparentPayload.unionInterfaces(node, runtime.get(internal), runtime);
						if(!kept.isEmpty()) {
							restored++;
							report.add("  kept the runtime's interface(s) on " + internal.replace('/', '.')
									+ ": " + String.join(", ", kept));
						}
						if(restored > 0) {
							ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
							node.accept(writer);
							bytes = writer.toByteArray();
							restoredTotal += restored;
							report.add("  restored " + restored + " member(s) in " + internal.replace('/', '.'));
						}
					}
				}
				ZipEntry copy = new ZipEntry(name);
				copy.setTime(entry.getTime());
				target.putNextEntry(copy);
				if(!entry.isDirectory()) {
					target.write(bytes);
				}
				target.closeEntry();
			}
		}
		report.add("restored " + restoredTotal + " member(s) across " + targets.size() + " class(es) from "
				+ planned + " planned; " + withoutDonor + " class(es) had no donor -> " + out);
		return report;
	}

	/**
	 * Copies the donor's members the class does not have, and calls the donor's field initialisers.
	 *
	 * <p>Two initialiser shapes travel in a donor, and which one a field needs is decided by the plan
	 * rather than here. An instance initialiser takes the object and is called from every constructor that
	 * does not assign that field itself; a no-argument one is a static field's initialiser, and its
	 * instructions are <em>inlined</em> into the class's own static initialiser, because a static final
	 * field may only be assigned from there - a call to a helper that writes it is rejected with
	 * {@code IllegalAccessError: Update to static final field ... attempted from a different method than the
	 * initializer method}. Interfaces get neither: their fields are final and their own initialiser already
	 * fills them.</p>
	 *
	 * @return how many members were added
	 */
	static int copyMembers(ClassNode node, ClassNode donor, String internalName) {
		int restored = 0;
		for(FieldNode field : donor.fields) {
			if(!hasField(node, field.name, field.desc)) {
				node.fields.add(new FieldNode(field.access, field.name, field.desc, field.signature, field.value));
				restored++;
			}
		}
		List<String> initialisers = new ArrayList<>();
		List<String> staticInitialisers = new ArrayList<>();
		boolean isInterface = (node.access & Opcodes.ACC_INTERFACE) != 0;
		for(MethodNode method : donor.methods) {
			if(!hasMethod(node, method.name, method.desc)) {
				MethodNode copy = new MethodNode(method.access, method.name, method.desc, method.signature,
						method.exceptions == null ? null : method.exceptions.toArray(new String[0]));
				method.accept(copy);
				node.methods.add(copy);
				restored++;
			}
			if(method.MemberRestorePlan.isInitialiser(name) && !isInterface) {
				if("()V".equals(method.desc)) {
					staticInitialisers.add(method.name);
				} else {
					initialisers.add(method.name);
				}
			}
		}
		if(!staticInitialisers.isEmpty()) {
			MethodNode clinit = null;
			for(MethodNode method : node.methods) {
				if("<clinit>".equals(method.name)) {
					clinit = method;
					break;
				}
			}
			InsnList values = new InsnList();
			for(String name : staticInitialisers) {
				for(MethodNode method : node.methods) {
					if(name.equals(method.name) && "()V".equals(method.desc) && method.instructions != null) {
						for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
							if(insn.getOpcode() == Opcodes.RETURN) {
								continue;
							}
							AbstractInsnNode copy = copy(insn);
							if(copy != null) {
								values.add(copy);
							}
						}
						break;
					}
				}
			}
			if(clinit == null) {
				clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
				clinit.instructions.add(values);
				clinit.instructions.add(new InsnNode(Opcodes.RETURN));
				node.methods.add(clinit);
			} else {
				AbstractInsnNode last = null;
				for(AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if(insn.getOpcode() == Opcodes.RETURN) {
						last = insn;
					}
				}
				if(last != null) {
					clinit.instructions.insertBefore(last, values);
				}
			}
		}
		if(initialisers.isEmpty()) {
			return restored;
		}
		// A restored field needs the assignment NeoForge's own class would have made. The donor carries
		// that sequence as a static method, and every constructor calls it once - except for the fields
		// that constructor assigns itself, where the call would overwrite the value it just stored.
		for(MethodNode constructor : node.methods) {
			if(!"<init>".equals(constructor.name) || constructor.instructions == null) {
				continue;
			}
			Set<String> assigned = assignedFields(constructor, internalName);
			List<String> wanted = new ArrayList<>();
			for(String name : initialisers) {
				if(!assigned.contains(MemberRestorePlan.initialisedField(name))) {
					wanted.add(name);
				}
			}
			if(wanted.isEmpty()) {
				continue;
			}
			for(AbstractInsnNode insn = constructor.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if(insn.getOpcode() != Opcodes.RETURN) {
					continue;
				}
				InsnList call = new InsnList();
				for(String name : wanted) {
					// One receiver push per call, and that is a measured correction rather than a style
					// choice: a single ALOAD 0 in front of the whole list is only correct while a
					// constructor needs one restored field. The first constructor that needed two -
					// BlockModelWrapper's three-argument one on 1.21.10, which restores renderType and
					// modelLocation - produced
					//   VerifyError: Operand stack underflow
					//     Location: BlockModelWrapper.<init>(List, List, ModelRenderProperties)V @12: invokestatic
					// because the second INVOKESTATIC found the stack empty, and the class was then
					// unusable: the resource reload died with it and the client never left its loading
					// screen. Every class on the other lines needed at most one initialiser per
					// constructor, which is why nothing caught it.
					call.add(new VarInsnNode(Opcodes.ALOAD, 0));
					call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, internalName, name,
							"(L" + internalName + ";)V", false));
				}
				constructor.instructions.insertBefore(insn, call);
			}
		}
		return restored;
	}

	/**
	 * A copy of one instruction of a value-producing run, or null for a kind the plan never extracts.
	 *
	 * <p>Instructions cannot be moved from the donor's list into another class's - they carry their own
	 * links and belong to the donor's list - so the kinds a value is made of are reconstructed.</p>
	 */
	private static AbstractInsnNode copy(AbstractInsnNode insn) {
		if(insn instanceof InsnNode plain) {
			return new InsnNode(plain.getOpcode());
		}
		if(insn instanceof MethodInsnNode call) {
			return new MethodInsnNode(call.getOpcode(), call.owner, call.name, call.desc, call.itf);
		}
		if(insn instanceof FieldInsnNode field) {
			return new FieldInsnNode(field.getOpcode(), field.owner, field.name, field.desc);
		}
		if(insn instanceof TypeInsnNode type) {
			return new TypeInsnNode(type.getOpcode(), type.desc);
		}
		if(insn instanceof LdcInsnNode ldc) {
			return new LdcInsnNode(ldc.cst);
		}
		if(insn instanceof IntInsnNode integer) {
			return new IntInsnNode(integer.getOpcode(), integer.operand);
		}
		if(insn instanceof InvokeDynamicInsnNode dynamic) {
			// A value built through a lambda is still one expression, and leaving the field at its default
			// makes everything that reads it fail later. The bootstrap method and its arguments are shared
			// rather than copied: they are immutable, and ASM writes them into this class's own bootstrap
			// table as it writes the instruction.
			return new InvokeDynamicInsnNode(dynamic.name, dynamic.desc, dynamic.bsm, dynamic.bsmArgs.clone());
		}
		return null;
	}

	/**
	 * The listener accessor and updater OptiFine's replacement of the resource manager drops.
	 *
	 * <p>The same shape as the fix the ModLauncher lines carry: OptiFine's compilation keeps the private
	 * listener list but not the accessor NeoForge added to it, so NeoForge's
	 * {@code AddClientReloadListenersEvent} cannot be built at all
	 * ({@code NoSuchMethodError: ...ReloadableResourceManager.getListeners()}). Here the restoration is
	 * exact rather than approximate - the field is still there under the same name - so the accessor is a
	 * plain getter for it, and the updater replaces the list's contents rather than assigning to the field,
	 * which OptiFine declares final and the verifier therefore rejects a {@code putfield} to.</p>
	 */
	static final class ReloadableResourceManagerFix {
		static final String RESOURCE_MANAGER = "net/minecraft/server/packs/resources/ReloadableResourceManager";
		private static final String GET_LISTENERS = "getListeners";
		private static final String GET_LISTENERS_DESC = "()Ljava/util/List;";
		/** The field both compilations keep. */
		private static final String LISTENERS_FIELD = "listeners";
		private static final String LISTENERS_DESC = "Ljava/util/List;";
		private static final String UPDATE_FROM = "updateListenersFrom";
		private static final String UPDATE_FROM_DESC = "(Lnet/neoforged/neoforge/event/SortedReloadListenerEvent;)V";
		private static final String SORTED_EVENT = "net/neoforged/neoforge/event/SortedReloadListenerEvent";
		/** NeoForge's own sorting helper, which the restored method delegates to. */
		private static final String RELOAD_LISTENER_SORT = "net/neoforged/neoforge/resource/ReloadListenerSort";

		private ReloadableResourceManagerFix() {
		}

		/** @return how many methods were written, 0 when the class is not the shape this fix needs */
		static int apply(ClassNode node) {
			if(!hasFieldNamed(node, LISTENERS_FIELD)) {
				return 0;
			}
			int written = 0;
			if(!hasMethod(node, GET_LISTENERS, GET_LISTENERS_DESC)) {
				MethodNode getter = new MethodNode(Opcodes.ACC_PUBLIC, GET_LISTENERS, GET_LISTENERS_DESC, null, null);
				InsnList body = getter.instructions;
				body.add(new VarInsnNode(Opcodes.ALOAD, 0));
				body.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, LISTENERS_FIELD, LISTENERS_DESC));
				body.add(new InsnNode(Opcodes.ARETURN));
				getter.maxStack = 1;
				getter.maxLocals = 1;
				node.methods.add(getter);
				written++;
			}
			if(!hasMethod(node, UPDATE_FROM, UPDATE_FROM_DESC)) {
				MethodNode update = new MethodNode(Opcodes.ACC_PUBLIC, UPDATE_FROM, UPDATE_FROM_DESC, null, null);
				InsnList body = update.instructions;
				body.add(new VarInsnNode(Opcodes.ALOAD, 0));
				body.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, LISTENERS_FIELD, LISTENERS_DESC));
				body.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "clear", "()V", true));
				body.add(new VarInsnNode(Opcodes.ALOAD, 0));
				body.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, LISTENERS_FIELD, LISTENERS_DESC));
				body.add(new VarInsnNode(Opcodes.ALOAD, 1));
				body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RELOAD_LISTENER_SORT, "sort",
						"(L" + SORTED_EVENT + ";)Ljava/util/List;", false));
				body.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "addAll",
						"(Ljava/util/Collection;)Z", true));
				body.add(new InsnNode(Opcodes.POP));
				body.add(new InsnNode(Opcodes.RETURN));
				update.maxStack = 2;
				update.maxLocals = 2;
				node.methods.add(update);
				written++;
			}
			return written;
		}
	}

	/** The donor class for a target, or null when the build wrote none for it. */
	private static ClassNode donor(Path donorsDir, String internalName, Map<String, ClassNode> cache) throws IOException {
		if(cache.containsKey(internalName)) {
			return cache.get(internalName);
		}
		Path file = donorsDir.resolve(internalName + ".class");
		ClassNode node = null;
		if(Files.isRegularFile(file)) {
			node = new ClassNode();
			new ClassReader(Files.readAllBytes(file)).accept(node, 0);
		}
		cache.put(internalName, node);
		return node;
	}

	private static boolean hasField(ClassNode node, String name, String descriptor) {
		for(FieldNode field : node.fields) {
			if(name.equals(field.name) && descriptor.equals(field.desc)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasFieldNamed(ClassNode node, String name) {
		for(FieldNode field : node.fields) {
			if(name.equals(field.name)) {
				return true;
			}
		}
		return false;
	}

	/** The names of the fields one constructor assigns on its own class. */
	private static Set<String> assignedFields(MethodNode constructor, String owner) {
		Set<String> assigned = new LinkedHashSet<>();
		if(constructor.instructions == null) {
			return assigned;
		}
		for(AbstractInsnNode insn = constructor.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if(insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD
					&& owner.equals(field.owner)) {
				assigned.add(field.name);
			}
		}
		return assigned;
	}

	private static boolean hasMethod(ClassNode node, String name, String descriptor) {
		for(MethodNode method : node.methods) {
			if(name.equals(method.name) && descriptor.equals(method.desc)) {
				return true;
			}
		}
		return false;
	}

	private static byte[] readAll(InputStream stream) throws IOException {
		try(stream) {
			return stream.readAllBytes();
		}
	}

	public static void main(String[] args) throws IOException {
		if(args.length < 4) {
			System.err.println("usage: RestoreMembers <payload jar> <plan> <donors dir> <out jar> [runtime jar...]");
			System.exit(2);
		}
		List<Path> runtime = new ArrayList<>();
		for(int index = 4; index < args.length; index++) {
			runtime.add(Path.of(args[index]));
		}
		restore(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), runtime)
				.forEach(System.out::println);
	}
}
