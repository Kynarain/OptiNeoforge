package kynarain.cn.optifineoforge.optifine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Stops OptiFine's own field-locator recursion from walking off the end of a class hierarchy.
 *
 * <p>Measured on 1.21 (2026-09-23). The client reaches the title screen, starts its sound engine and produces no
 * crash report, yet its standard error carries eleven stack traces of the shape</p>
 *
 * <pre>NullPointerException: Cannot invoke "java.lang.Class.getDeclaredFields()" because "cls" is null
 *   at net.optifine.reflect.FieldLocatorName.getDeclaredField(FieldLocatorName.java:70)
 *   at net.optifine.reflect.FieldLocatorName.getDeclaredField(FieldLocatorName.java:81)
 *   at net.optifine.reflect.ReflectorField.getTargetField(ReflectorField.java:65)
 *   at net.minecraft.client.renderer.GameRenderer.frameInit(GameRenderer.java:1568)</pre>
 *
 * <p>The two stacked frames of the same method are the whole diagnosis. Disassembling the class (2582 bytes, taken
 * from the prepared jar's {@code srg/net/optifine/reflect/FieldLocatorName.class}) gives</p>
 *
 * <pre> 0: aload_1
 *  1: invokevirtual  Class.getDeclaredFields:()[Ljava/lang/reflect/Field;   &lt;- NPEs when cls is null
 * ...
 * 42: aload_1
 * 43: ldc            class java/lang/Object
 * 45: if_acmpne       57          &lt;- guard: only "cls == Object.class" is handled
 * 48: new            NoSuchFieldException
 * 56: athrow
 * 57: aload_0
 * 58: aload_1
 * 59: invokevirtual  Class.getSuperclass:()Ljava/lang/Class;
 * 62: aload_2
 * 63: invokevirtual  FieldLocatorName.getDeclaredField:(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;
 * 66: areturn                    &lt;- recurses with getSuperclass()</pre>
 *
 * <p>{@code Class.getSuperclass()} returns <em>null</em> for an interface (and for {@code Object}), and this method
 * guards only the {@code Object} case. So a reflector field whose owner is an interface recurses exactly one level
 * with {@code null} and then dereferences it - which is why the trace shows two frames of the same method and why
 * the failure is OptiFine's, not a class we failed to provide. Supplying stubs for the missing types cannot fix it:
 * the trigger is the <em>shape</em> of the owner class, not its absence.</p>
 *
 * <p>The repair substitutes {@code Object.class} for a null superclass at that one call site:</p>
 *
 * <pre>59: invokevirtual  Class.getSuperclass:()Ljava/lang/Class;
 *     ldc            class java/lang/Object
 *     invokestatic   java/util/Objects.requireNonNullElse:(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
 *     checkcast      java/lang/Class
 * 62: aload_2
 * ...</pre>
 *
 * <p>Passing {@code Object.class} reproduces OptiFine's own intended outcome exactly: the recursive call enumerates
 * {@code Object}'s declared fields, finds nothing, and throws {@code NoSuchFieldException} - the same thing the
 * existing {@code cls == Object.class} guard does. So an interface-owned field now resolves quietly to "absent"
 * instead of throwing, and {@code FieldLocatorName.getField} already handles that (it catches
 * {@code NoSuchFieldException} and returns null).</p>
 *
 * <p>Frame-neutral and stack-neutral by construction, which is the reason for this form rather than an early-return
 * guard: the inserted instructions are <em>straight-line</em>, so no new branch target exists and the existing
 * StackMapTable stays valid. A guard that returns early would introduce a jump to a new target and require frame
 * computation on a class that references types which are absent at build time - the same trap that made an inserted
 * {@code GOTO} fail verification in {@link ShadersPackLoadedRepair}. The highest stack depth is unchanged (three
 * slots at the call), and the maximum is left untouched.</p>
 *
 * <p>Applied by {@link OptifinePipeline#split} while OptiFine's own classes are written into the classpath jar (there
 * the {@code srg/} prefix has already been stripped), and available as a standalone tool for an already-prepared jar
 * (there the entries still carry the prefix, so both spellings are matched). A build in which the sequence does not
 * appear is reported and left byte-for-byte unchanged - it never guesses.</p>
 */
public final class FieldLocatorNullGuardRepair {
	/** The class as it is named inside the classpath jar, where {@code OptifinePipeline} has stripped the prefix. */
	public static final String ENTRY = "net/optifine/reflect/FieldLocatorName.class";
	/** The same class inside OptiFine's own prepared jar, where it still carries the variant prefix. */
	private static final String PREFIXED_ENTRY = "srg/" + ENTRY;
	/** The class that owns the recursion, as the bytecode refers to it. */
	private static final String OWNER = "net/optifine/reflect/FieldLocatorName";
	private static final String METHOD = "getDeclaredField";
	private static final String METHOD_DESC = "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;";
	private static final String CLASS = "java/lang/Class";
	private static final String GET_SUPERCLASS = "getSuperclass";
	private static final String GET_SUPERCLASS_DESC = "()Ljava/lang/Class;";
	private static final String OBJECT = "java/lang/Object";
	private static final String REQUIRE_NON_NULL_ELSE = "java/util/Objects";
	private static final String REQUIRE_NON_NULL_ELSE_DESC =
			"(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";

	private FieldLocatorNullGuardRepair() {
	}

	/** What one repair attempt did, for the caller's log. */
	public enum Outcome {
		/** The null superclass was given a value at the recursive call. */
		REPAIRED,
		/** This build does not have the unguarded recursion. */
		NOT_PRESENT,
		/** The class does not have the shape this repair knows, or has it more than once; it was left alone. */
		UNEXPECTED
	}

	/**
	 * Patches the recursive call if this class has the defect.
	 *
	 * @return the class to write, or {@code null} when nothing was changed
	 */
	public static byte[] apply(byte[] original) {
		ClassNode node = new ClassNode();
		new ClassReader(original).accept(node, 0);

		MethodNode target = null;
		for(MethodNode method : node.methods) {
			if(METHOD.equals(method.name) && METHOD_DESC.equals(method.desc)) {
				target = method;
				break;
			}
		}
		if(target == null || target.instructions == null) {
			return null;
		}

		// The whole call site, five instructions long: ALOAD 0 (this), ALOAD 1 (the class), the superclass lookup,
		// ALOAD 2 (the field name) and the recursive call. Requiring all five is what keeps this from firing on the
		// class's other, unrelated uses of getSuperclass; exactly one match is expected, and on anything else - none
		// or several - the honest answer is to change nothing.
		MethodInsnNode superclass = null;
		int matches = 0;
		for(AbstractInsnNode instruction = target.instructions.getFirst(); instruction != null;
				instruction = instruction.getNext()) {
			if(!(instruction instanceof VarInsnNode first) || first.getOpcode() != Opcodes.ALOAD
					|| first.var != 0) {
				continue;
			}
			AbstractInsnNode second = nextReal(first);
			AbstractInsnNode third = nextReal(second);
			AbstractInsnNode fourth = nextReal(third);
			AbstractInsnNode fifth = nextReal(fourth);
			if(!(second instanceof VarInsnNode classLoad) || classLoad.getOpcode() != Opcodes.ALOAD
					|| classLoad.var != 1) {
				continue;
			}
			if(!isSuperclass(third)) {
				continue;
			}
			if(!(fourth instanceof VarInsnNode nameLoad) || nameLoad.getOpcode() != Opcodes.ALOAD
					|| nameLoad.var != 2) {
				continue;
			}
			if(!isRecursion(fifth)) {
				continue;
			}
			matches++;
			superclass = (MethodInsnNode) third;
		}
		if(matches != 1 || superclass == null) {
			return null;
		}

		InsnList inserted = new InsnList();
		inserted.add(new LdcInsnNode(Type.getObjectType(OBJECT)));
		inserted.add(new MethodInsnNode(Opcodes.INVOKESTATIC, REQUIRE_NON_NULL_ELSE, "requireNonNullElse",
				REQUIRE_NON_NULL_ELSE_DESC, false));
		inserted.add(new TypeInsnNode(Opcodes.CHECKCAST, CLASS));
		target.instructions.insert(superclass, inserted);

		// No frame computation and no maximum recomputation: the inserted instructions add no branch target, and the
		// deepest the stack gets is the three slots the original call already needed.
		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		return writer.toByteArray();
	}

	private static boolean isSuperclass(AbstractInsnNode instruction) {
		return instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
				&& CLASS.equals(call.owner) && GET_SUPERCLASS.equals(call.name)
				&& GET_SUPERCLASS_DESC.equals(call.desc);
	}

	private static boolean isRecursion(AbstractInsnNode instruction) {
		return instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
				&& OWNER.equals(call.owner) && METHOD.equals(call.name) && METHOD_DESC.equals(call.desc);
	}

	/** The next real instruction, skipping the pseudo-nodes ASM keeps in the list (labels and line numbers). */
	private static AbstractInsnNode nextReal(AbstractInsnNode instruction) {
		AbstractInsnNode next = instruction == null ? null : instruction.getNext();
		while(next != null && next.getOpcode() < 0) {
			next = next.getNext();
		}
		return next;
	}

	/**
	 * Repairs one jar in place: {@code java ... FieldLocatorNullGuardRepair <jar>}.
	 *
	 * <p>For an already-prepared line, whose jar was written before this repair existed. A fresh run of the pipeline
	 * applies it through {@link OptifinePipeline#split} without this step. Both entry spellings are matched, because
	 * a prepared ModLauncher line keeps the {@code srg/} prefix that the pipeline strips.</p>
	 */
	public static void main(String[] args) throws IOException {
		if(args.length != 1) {
			System.err.println("usage: FieldLocatorNullGuardRepair <optifine-jar>");
			System.exit(2);
		}
		Path jar = Path.of(args[0]);
		List<String> wanted = Arrays.asList(ENTRY, PREFIXED_ENTRY);
		Path temporary = jar.resolveSibling(jar.getFileName() + ".repairing");
		boolean changed = false;
		int seen = 0;
		try(ZipFile zip = new ZipFile(jar.toFile());
				ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temporary))) {
			for(Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
				ZipEntry entry = it.nextElement();
				byte[] data;
				try(InputStream stream = zip.getInputStream(entry)) {
					data = stream.readAllBytes();
				}
				if(wanted.contains(entry.getName())) {
					seen++;
					byte[] repaired = apply(data);
					if(repaired != null) {
						data = repaired;
						changed = true;
						System.out.println("repaired " + entry.getName()
								+ ": the recursive getSuperclass() call can no longer pass null"
								+ " (an interface has no superclass, and only Object.class was guarded)");
					} else {
						System.out.println(entry.getName() + ": nothing to repair (no unguarded recursion)");
					}
				}
				ZipEntry copy = new ZipEntry(entry.getName());
				copy.setTime(entry.getTime());
				out.putNextEntry(copy);
				out.write(data);
				out.closeEntry();
			}
		}
		if(seen == 0) {
			Files.deleteIfExists(temporary);
			throw new IOException("no " + ENTRY + " (nor " + PREFIXED_ENTRY + ") in " + jar);
		}
		if(changed) {
			Files.move(temporary, jar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			System.out.println("written: " + jar);
		} else {
			Files.deleteIfExists(temporary);
		}
	}
}
