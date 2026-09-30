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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Finds the OptiFine {@code Reflector} lookups whose target does not exist on this runtime.
 *
 * <p>This is the measurement behind a defect that cost a whole round of guessing: OptiFine's
 * {@code Reflector} is a reflection table, and every accessor answers <em>silently</em> when its
 * target is missing - {@code ReflectorMethod.call} returns null, {@code callBoolean} returns false,
 * {@code ReflectorField.getValue} returns null. A null that lands in a field of a game class is then
 * read by NeoForge's own code much later, and the failure surfaces nowhere near its cause.</p>
 *
 * <p>Measured case, 1.21.11: OptiFine's patched {@code net.minecraft.world.item.DyeColor} builds its
 * tag fields with</p>
 *
 * <pre>this.dyesTag = (TagKey) Reflector.ForgeItemTags_create.call("forge", "dyes/" + name);</pre>
 *
 * <p>and {@code Reflector.ForgeItemTags_create} is <em>not</em> a Forge class at all - the table
 * builds it as {@code new ReflectorClass(net.minecraft.tags.ItemTags.class)} plus
 * {@code makeMethod("create", String.class, String.class)}. Forge's copy of {@code ItemTags} had that
 * overload; NeoForge's has only {@code create(Identifier)}. So the lookup resolves to nothing,
 * {@code call} answers null, {@code DyeColor.BLACK.getTag()} is null, and
 * {@code net.neoforged.neoforge.common.Tags$Items.DYES_BLACK} - which is
 * {@code DyeColor.BLACK.getTag()}, not a {@code tag(...)} call like its neighbours - is null, which
 * makes NeoForge's own {@code TagConventionLogWarning.<clinit>} throw a NullPointerException and the
 * whole mod load fail.</p>
 *
 * <p>What it reads: the {@code Reflector} class's own {@code <clinit>}, which is straight-line code
 * that constructs the whole table, so the target of every field can be recovered from the bytecode
 * itself. What it prints: for each class of the payload, the Reflector entries it uses, whether the
 * target exists in the runtime jars given, and whether the answer is stored into a field - which is
 * the shape that turns a silent null into a corrupted game class.</p>
 *
 * <p>Usage: {@code ReflectorGaps <optifine jar> <payload jar> <runtime jar> [runtime jar...]},
 * optionally followed by {@code --dump} to list every entry rather than only the gaps.</p>
 */
public final class ReflectorGaps {
	private static final String REFLECTOR = "net/optifine/reflect/Reflector";

	/**
	 * The opcodes that end the expression a {@code getstatic Reflector.x} belongs to, so the walk from
	 * the field read to the call on it stops instead of attributing a later, unrelated call to it.
	 */
	private static final Set<Integer> STOPS_THE_WALK = Set.of(
			Opcodes.RETURN, Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN,
			Opcodes.ARETURN, Opcodes.ATHROW, Opcodes.GOTO, Opcodes.PUTFIELD, Opcodes.PUTSTATIC,
			Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.ASTORE,
			Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE,
			Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE,
			Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE, Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE,
			Opcodes.IFNULL, Opcodes.IFNONNULL, Opcodes.TABLESWITCH, Opcodes.LOOKUPSWITCH,
			Opcodes.MONITORENTER, Opcodes.MONITOREXIT);

	private ReflectorGaps() {
	}

	/** One entry of the Reflector table, as recovered from its {@code <clinit>}. */
	private record Entry(String kind, String owner, String name, List<String> params) {
		String describe() {
			return kind + " " + owner + "." + name + (params.isEmpty() ? "" : params.toString());
		}
	}

	/** One payload class's use of one Reflector entry. */
	private record Use(String payloadClass, String field, boolean storedIntoField) {
	}

	public static void main(String[] args) throws Exception {
		boolean dump = false;
		List<String> files = new ArrayList<>();
		for(String arg : args) {
			if("--dump".equals(arg)) {
				dump = true;
			} else {
				files.add(arg);
			}
		}
		if(files.size() < 3) {
			System.err.println("usage: ReflectorGaps <optifine jar> <payload jar> <runtime jar> [runtime jar...] [--dump]");
			System.exit(2);
		}
		Path optifine = Path.of(files.get(0));
		Path payload = Path.of(files.get(1));
		List<Path> runtime = new ArrayList<>();
		for(int index = 2; index < files.size(); index++) {
			runtime.add(Path.of(files.get(index)));
		}

		Map<String, Entry> table = readTable(optifine);
		System.out.println("Reflector table: " + table.size() + " entries recovered from <clinit>");
		long methods = table.values().stream().filter(entry -> "method".equals(entry.kind())).count();
		long fields = table.values().stream().filter(entry -> "field".equals(entry.kind())).count();
		long classes = table.values().stream().filter(entry -> "class".equals(entry.kind())).count();
		System.out.println("  methods " + methods + ", fields " + fields + ", classes " + classes);

		Set<String> runtimeNames = new TreeSet<>();
		for(Path jar : runtime) {
			runtimeNames.addAll(classNames(jar));
		}
		Map<String, ClassNode> runtimeClasses = new TreeMap<>();
		Set<String> missingOwners = new TreeSet<>();

		List<Use> uses = new ArrayList<>();
		int scanned = 0;
		for(Map.Entry<String, byte[]> classEntry : payloadClasses(payload).entrySet()) {
			scanned++;
			uses.addAll(uses(classEntry.getKey(), classEntry.getValue()));
		}
		System.out.println("payload classes scanned: " + scanned + ", Reflector entry uses: " + uses.size());

		Map<String, List<Use>> byEntry = new TreeMap<>();
		for(Use use : uses) {
			byEntry.computeIfAbsent(use.field(), key -> new ArrayList<>()).add(use);
		}

		int gaps = 0;
		int storedGaps = 0;
		Set<String> reportedOwners = new TreeSet<>();
		StringBuilder lines = new StringBuilder();
		for(Map.Entry<String, List<Use>> entryUse : byEntry.entrySet()) {
			Entry entry = table.get(entryUse.getKey());
			if(entry == null) {
				lines.append("UNKNOWN ENTRY (not recovered from <clinit>): ").append(entryUse.getKey())
						.append(" used by ").append(entryUse.getValue().size()).append(" class(es)")
						.append('\n');
				continue;
			}
			String owner = entry.owner();
			boolean present;
			if(owner.startsWith("net/minecraftforge/")) {
				// The Forge API types are supplied by this project's own shims; whether the member
				// exists is a different question (and answered by ForgeApiShims' report).
				continue;
			}
			if(!runtimeNames.contains(owner)) {
				missingOwners.add(owner);
				present = false;
			} else {
				ClassNode node = runtimeClasses.computeIfAbsent(owner, name -> readFrom(runtime, name));
				present = node != null && hasMember(node, entry);
			}
			if(present && !dump) {
				continue;
			}
			if(!present) {
				gaps++;
				boolean stored = entryUse.getValue().stream().anyMatch(Use::storedIntoField);
				if(stored) {
					storedGaps++;
				}
				reportedOwners.add(owner);
				lines.append(stored ? "STORED GAP " : "GAP        ")
						.append(entryUse.getKey()).append(" -> ").append(entry.describe())
						.append(stored ? "  [answer is stored into a field]" : "")
						.append('\n');
			}
			for(Use use : entryUse.getValue()) {
				lines.append("    used by ").append(use.payloadClass())
						.append(use.storedIntoField() ? " (stored into a field)" : "").append('\n');
			}
		}

		System.out.print(lines);
		System.out.println("entries whose target is missing: " + gaps + " (of which " + storedGaps
				+ " store the answer into a field)");
		System.out.println("missing target classes: " + reportedOwners.size()
				+ (reportedOwners.isEmpty() ? "" : " -> " + String.join(", ", reportedOwners)));
		System.out.println("owners absent from the runtime jars: " + missingOwners.size()
				+ (missingOwners.isEmpty() ? "" : " -> " + String.join(", ", missingOwners)));
	}

	/** Whether the runtime class declares the member the entry names. */
	private static boolean hasMember(ClassNode node, Entry entry) {
		if("method".equals(entry.kind())) {
			String desc = Type.getMethodDescriptor(Type.VOID_TYPE, entry.params().stream()
					.map(Type::getType).toArray(Type[]::new));
			String descWithoutReturn = desc.substring(0, desc.indexOf(')') + 1);
			for(MethodNode method : node.methods) {
				if(method.name.equals(entry.name()) && method.desc.startsWith(descWithoutReturn)) {
					return true;
				}
			}
			return false;
		}
		if("field".equals(entry.kind())) {
			for(FieldNode field : node.fields) {
				if(field.name.equals(entry.name())) {
					return true;
				}
			}
			return false;
		}
		return true;
	}

	/**
	 * Recovers the Reflector table by walking {@code Reflector.<clinit>} with a small symbolic stack.
	 *
	 * <p>The initialiser is straight-line code - {@code new ReflectorClass}, a class literal or a name,
	 * then zero or more {@code makeMethod}/{@code makeField} calls, then a {@code putstatic} - so a
	 * stack simulation over it recovers every entry's target class, member name and parameter types
	 * without loading anything. Instructions the simulation does not model poison the stack rather
	 * than guess: an entry that cannot be recovered is reported as unknown, which is honest, while a
	 * wrong target would be a false measurement.</p>
	 */
	private static Map<String, Entry> readTable(Path optifineJar) throws IOException {
		byte[] bytes = readEntry(optifineJar, REFLECTOR + ".class");
		if(bytes == null) {
			throw new IOException("no " + REFLECTOR + " in " + optifineJar);
		}
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		Map<String, Entry> table = new LinkedHashMap<>();
		MethodNode clinit = null;
		for(MethodNode method : node.methods) {
			if("<clinit>".equals(method.name)) {
				clinit = method;
			}
		}
		if(clinit == null) {
			return table;
		}
		List<Object> stack = new ArrayList<>();
		for(AbstractInsnNode instruction : clinit.instructions) {
			switch(instruction.getOpcode()) {
				case Opcodes.NEW:
					stack.add(new Object());
					break;
				case Opcodes.DUP:
					if(!stack.isEmpty()) {
						stack.add(stack.get(stack.size() - 1));
					}
					break;
				case Opcodes.POP:
					if(!stack.isEmpty()) {
						stack.remove(stack.size() - 1);
					}
					break;
				case Opcodes.LDC:
					Object constant = ((LdcInsnNode)instruction).cst;
					stack.add(constant instanceof Type type ? new ClassLiteral(type) : constant);
					break;
				case Opcodes.ICONST_M1: case Opcodes.ICONST_0: case Opcodes.ICONST_1:
				case Opcodes.ICONST_2: case Opcodes.ICONST_3: case Opcodes.ICONST_4: case Opcodes.ICONST_5:
					stack.add(instruction.getOpcode() - Opcodes.ICONST_0);
					break;
				case Opcodes.BIPUSH: case Opcodes.SIPUSH:
					stack.add(((IntInsnNode)instruction).operand);
					break;
				case Opcodes.ANEWARRAY: {
					int count = popInt(stack);
					stack.add(new ClassArray(count));
					break;
				}
				case Opcodes.AASTORE: {
					Object value = pop(stack);
					int index = popInt(stack);
					Object array = pop(stack);
					if(array instanceof ClassArray classArray && index >= 0 && index < classArray.values.length) {
						classArray.values[index] = value;
					}
					break;
				}
				case Opcodes.CHECKCAST: case Opcodes.NOP:
				case Opcodes.ALOAD: case Opcodes.ASTORE:
					break;
				case Opcodes.INVOKESPECIAL: {
					MethodInsnNode call = (MethodInsnNode)instruction;
					if(("net/optifine/reflect/ReflectorClass".equals(call.owner)
							|| "net/optifine/reflect/ReflectorMethod".equals(call.owner)
							|| "net/optifine/reflect/ReflectorField".equals(call.owner)
							|| "net/optifine/reflect/ReflectorConstructor".equals(call.owner))
							&& "<init>".equals(call.name)) {
						List<Object> arguments = popArguments(stack, call.desc, false);
						pop(stack); // the uninitialised instance
						Entry built = constructed(call.owner, arguments);
						if(built == null) {
							poison(stack);
						} else {
							stack.add(built);
						}
					} else {
						poison(stack);
					}
					break;
				}
				case Opcodes.INVOKEVIRTUAL: case Opcodes.INVOKESTATIC: case Opcodes.INVOKEINTERFACE: {
					MethodInsnNode call = (MethodInsnNode)instruction;
					boolean isStatic = instruction.getOpcode() == Opcodes.INVOKESTATIC;
					List<Object> arguments = popArguments(stack, call.desc, !isStatic);
					Object receiver = isStatic ? null : pop(stack);
					stack.add(apply(call, receiver, arguments));
					break;
				}
				case Opcodes.PUTSTATIC: {
					FieldInsnNode field = (FieldInsnNode)instruction;
					Object value = pop(stack);
					if(REFLECTOR.equals(field.owner)) {
						Entry entry = asEntry(value);
						if(entry != null) {
							table.put(field.name, entry);
						}
					}
					break;
				}
				case Opcodes.GETSTATIC: {
					FieldInsnNode field = (FieldInsnNode)instruction;
					Entry known = REFLECTOR.equals(field.owner) ? table.get(field.name) : null;
					stack.add(known == null ? new Object() : known);
					break;
				}
				case Opcodes.RETURN: case Opcodes.ARETURN:
					break;
				default:
					// Anything not modelled here would shift the stack silently, so the parse stops
					// being trustworthy: poison it, which makes every later entry unknown rather than
					// wrong. Reported through the counts printed at the end.
					poison(stack);
					break;
			}
		}
		return table;
	}

	/**
	 * The entry a {@code Reflector*} constructor produces.
	 *
	 * <p>A {@code ReflectorClass} carries its target class - either a class literal (which OptiFine's
	 * table uses for the game's own classes, and which is how the {@code ItemTags} case was found) or a
	 * class name. A {@code ReflectorMethod}/{@code ReflectorField} built directly names its member on
	 * the {@code ReflectorClass} it is handed.</p>
	 */
	private static Entry constructed(String owner, List<Object> arguments) {
		if("net/optifine/reflect/ReflectorClass".equals(owner)) {
			if(arguments.size() != 1) {
				return null;
			}
			Object target = arguments.get(0);
			if(target instanceof ClassLiteral literal) {
				return new Entry("class", literal.type().getInternalName(), "<class>", List.of());
			}
			if(target instanceof String name) {
				return new Entry("class", name.replace('.', '/'), "<class>", List.of());
			}
			return null;
		}
		Entry reflectorClass = arguments.isEmpty() ? null : asEntry(arguments.get(0));
		if(reflectorClass == null) {
			return null;
		}
		boolean isMethod = "net/optifine/reflect/ReflectorMethod".equals(owner)
				|| "net/optifine/reflect/ReflectorConstructor".equals(owner);
		String name = arguments.size() > 1 && arguments.get(1) instanceof String text ? text : null;
		if(name == null) {
			return null;
		}
		List<String> params = new ArrayList<>();
		for(int index = 2; index < arguments.size(); index++) {
			if(arguments.get(index) instanceof ClassArray array) {
				for(Object value : array.values) {
					params.add(typeName(value));
				}
			}
		}
		return new Entry(isMethod ? "method" : "field", reflectorClass.owner(), name, params);
	}

	private static Object apply(MethodInsnNode call, Object receiver, List<Object> arguments) {
		Entry owner = asEntry(receiver);
		if(owner == null || !"net/optifine/reflect/ReflectorClass".equals(call.owner)) {
			return new Object();
		}
		switch(call.name) {
			case "makeMethod": {
				String name = arguments.size() > 0 && arguments.get(0) instanceof String text ? text : null;
				List<String> params = new ArrayList<>();
				if(arguments.size() > 1 && arguments.get(1) instanceof ClassArray array) {
					for(Object value : array.values) {
						params.add(typeName(value));
					}
				}
				return name == null ? new Object()
						: new Entry("method", owner.owner(), name, params);
			}
			case "makeField": {
				String name = arguments.size() > 0 && arguments.get(0) instanceof String text ? text : null;
				return name == null ? new Object() : new Entry("field", owner.owner(), name, List.of());
			}
			case "makeConstructor":
				return new Entry("method", owner.owner(), "<init>", List.of());
			case "resolve":
				return owner;
			default:
				return owner;
		}
	}

	private static String typeName(Object value) {
		if(value instanceof ClassLiteral literal) {
			// A method descriptor, not an internal name: the parameter list is turned straight back
			// into the descriptor of the method being looked for.
			return literal.type().getDescriptor();
		}
		return String.valueOf(value);
	}

	/** A recovered Reflector entry, used as the symbolic value of a {@code Reflector*} object. */
	private static Entry asEntry(Object value) {
		return value instanceof Entry entry ? entry : null;
	}

	/** A class literal lifted by {@code ldc}, kept as a type rather than a Class object. */
	private record ClassLiteral(Type type) {
	}

	/** The parameter array under construction: {@code anewarray Class} then {@code aastore}s. */
	private static final class ClassArray {
		private final Object[] values;

		private ClassArray(int size) {
			this.values = new Object[Math.max(0, size)];
		}
	}

	/**
	 * The value produced by constructing a {@code Reflector*} object: for a {@code ReflectorClass} it
	 * carries the target class name, so that a later {@code makeMethod} can be resolved against it.
	 */
	private record TableEntry(String className, String constructor, List<Object> arguments) {
	}

	private static List<Object> popArguments(List<Object> stack, String desc, boolean hasReceiver) {
		List<Object> arguments = new ArrayList<>();
		for(Type argument : Type.getArgumentTypes(desc)) {
			arguments.add(0, argument.getSize() == 2 ? pop(stack) : pop(stack));
		}
		return arguments;
	}

	private static int popInt(List<Object> stack) {
		Object value = pop(stack);
		return value instanceof Integer number ? number : -1;
	}

	private static Object pop(List<Object> stack) {
		return stack.isEmpty() ? new Object() : stack.remove(stack.size() - 1);
	}

	private static void poison(List<Object> stack) {
		stack.clear();
	}

	/** Every {@code Reflector} field a class reads, and whether the call's answer goes into a field. */
	private static List<Use> uses(String className, byte[] bytes) {
		ClassNode node = new ClassNode();
		try {
			new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		} catch(RuntimeException unreadable) {
			return List.of();
		}
		List<Use> result = new ArrayList<>();
		for(MethodNode method : node.methods) {
			AbstractInsnNode[] instructions = method.instructions.toArray();
			for(int index = 0; index < instructions.length; index++) {
				AbstractInsnNode instruction = instructions[index];
				if(instruction.getOpcode() != Opcodes.GETSTATIC
						|| !REFLECTOR.equals(((FieldInsnNode)instruction).owner)) {
					continue;
				}
				String field = ((FieldInsnNode)instruction).name;
				// Walk forward over the argument list to the call on the entry itself. Almost anything
				// can appear between them - OptiFine builds the arguments with string concatenation
				// (an invokedynamic), reads locals and boxes primitives - so the walk is bounded and
				// stops on the opcodes that definitely end the expression instead of listing the ones
				// that may occur. Missing the call is what made the DyeColor case invisible in the
				// first version of this report: an invokedynamic between the field read and the call
				// ended the walk before it.
				for(int next = index + 1; next < instructions.length && next < index + 60; next++) {
					AbstractInsnNode candidate = instructions[next];
					int opcode = candidate.getOpcode();
					if(opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKESTATIC
							|| opcode == Opcodes.INVOKEINTERFACE) {
						MethodInsnNode call = (MethodInsnNode)candidate;
						if("net/optifine/reflect/ReflectorMethod".equals(call.owner)
								|| "net/optifine/reflect/ReflectorField".equals(call.owner)
								|| "net/optifine/reflect/ReflectorClass".equals(call.owner)
								|| "net/optifine/reflect/ReflectorConstructor".equals(call.owner)) {
							result.add(new Use(className, field, storesAfter(instructions, next + 1)));
						}
						break;
					}
					if(STOPS_THE_WALK.contains(opcode)) {
						break;
					}
				}
			}
		}
		return result;
	}

	/** Whether the call's answer is written into a field of this class (the shape that hurts). */
	private static boolean storesAfter(AbstractInsnNode[] instructions, int from) {
		for(int index = from; index < instructions.length && index < from + 3; index++) {
			int opcode = instructions[index].getOpcode();
			if(opcode == Opcodes.PUTSTATIC || opcode == Opcodes.PUTFIELD) {
				return true;
			}
			if(opcode != Opcodes.CHECKCAST && opcode != Opcodes.NOP) {
				return false;
			}
		}
		return false;
	}

	/** The classes of the payload that replace a game class (the ones shipped under {@code srg/}). */
	private static Map<String, byte[]> payloadClasses(Path payloadJar) throws IOException {
		Map<String, byte[]> result = new TreeMap<>();
		try(ZipFile zip = new ZipFile(payloadJar.toFile())) {
			for(Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
				ZipEntry entry = it.nextElement();
				String name = entry.getName();
				if(entry.isDirectory() || !name.startsWith("srg/") || !name.endsWith(".class")) {
					continue;
				}
				String className = name.substring("srg/".length());
				if(className.startsWith("net/optifine/") || className.startsWith("net/minecraftforge/")) {
					continue;
				}
				result.put(className, readAll(zip.getInputStream(entry)));
			}
		}
		return result;
	}

	private static Set<String> classNames(Path jar) throws IOException {
		Set<String> names = new LinkedHashSet<>();
		try(ZipFile zip = new ZipFile(jar.toFile())) {
			for(Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
				String name = it.nextElement().getName();
				if(name.endsWith(".class")) {
					names.add(name.substring(0, name.length() - ".class".length()));
				}
			}
		}
		return names;
	}

	private static ClassNode readFrom(List<Path> jars, String name) {
		for(Path jar : jars) {
			try {
				byte[] bytes = readEntry(jar, name + ".class");
				if(bytes != null) {
					ClassNode node = new ClassNode();
					new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
					return node;
				}
			} catch(IOException ignored) {
				// Try the next jar.
			}
		}
		return null;
	}

	private static byte[] readEntry(Path jar, String name) throws IOException {
		try(ZipFile zip = new ZipFile(jar.toFile())) {
			ZipEntry entry = zip.getEntry(name);
			return entry == null ? null : readAll(zip.getInputStream(entry));
		}
	}

	private static byte[] readAll(InputStream stream) throws IOException {
		try(stream) {
			return stream.readAllBytes();
		}
	}
}
