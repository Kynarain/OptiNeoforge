package kynarain.cn.optifineoforge.optifine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Restores the branch OptiFine's 1.21.9 build lost, which is why that one line could never select a shader pack.
 *
 * <p>Measured 2026-09-24, and the measurement is the reason this repair exists rather than a guess about our own
 * code. On 1.21.9 the client logs</p>
 *
 * <pre>[Shaders] Load shaders configuration.
 * [Shaders] No shaderpack loaded.</pre>
 *
 * <p>while the same game directory, the same {@code optionsshaders.txt} bytes (59, identical to 1.21.10's), the same
 * OptiFine class and the same shader pack loaded fine on 1.21.10 and 1.21.11. Probing the running client (rig
 * {@code ShaderStateProbe}, then an injected print inside {@code Shaders.loadShaderPack}) showed every input correct
 * at the instant of the call - {@code configFile} and {@code shaderPacksDir} the right absolute paths and both
 * existing, the property exactly {@code MakeUp-UltraFast-9.5e.zip} (25 chars, no trailing space),
 * {@code endsWith(".zip")} true, {@code new File(shaderPacksDir, name).isFile()} true, {@code getShaderPack} entered
 * and returning a live {@code ShaderPackZip} - and the log still said no pack had been loaded.</p>
 *
 * <p>The bytecode says why. In 1.21.9's {@code loadShaderPack()} the lookup's result is assigned and then discarded
 * by the very next two instructions, because the {@code else} block of {@code if (skip != 0)} was emitted without a
 * jump over it:</p>
 *
 * <pre>174: aload_3
 * 175: invokestatic  getShaderPack:(Ljava/lang/String;)Lnet/optifine/shaders/IShaderPack;
 * 178: putstatic     shaderPack
 * 181: getstatic     shaderPack
 * 184: ifnull        191
 * 187: iconst_1
 * 188: goto          192
 * 191: iconst_0
 * 192: putstatic     shaderPackLoaded           &lt;- the lookup's outcome
 * 195: iconst_0                                 &lt;- reached by falling through AND by the "skip" jump
 * 196: putstatic     shaderPackLoaded           &lt;- so it is always false
 * 199: getstatic     shaderPackLoaded
 * 202: ifeq          219
 * 219: ldc           "No shaderpack loaded."</pre>
 *
 * <p>1.21.10 (OptiFine HD U J7 pre11) and 1.21.11 (J9) go straight from 192 to
 * {@code 195: getstatic shaderPackLoaded} - the {@code else} body and its jump are simply not there - which is why
 * only 1.21.9 is affected. optifine.net lists exactly two 1.21.9 builds (J7 pre1 and pre2, both 01.10.2025) and no
 * newer one, so there is no fixed build to move the line to and the repair has to be ours.</p>
 *
 * <p>The repair inserts the missing jump: a {@code GOTO} to the instruction after the clear, placed where the
 * lookup's outcome is still on the field. The {@code skip} path is untouched - it jumps to the clear and executes it
 * as intended - and no new branch target is introduced (the target is already one, from the {@code skip} jump and its
 * own {@code goto}), so the original stack map frames stay valid and the class is written without recomputation.</p>
 *
 * <p>Applied by {@link OptifinePipeline#split} while OptiFine's own classes are written into the classpath jar, so
 * every FML 10 line prepared from this repository carries it. On a line whose class does not have the defect the
 * method reports that it found nothing to do and returns the class unchanged - it never guesses.</p>
 */
public final class ShadersPackLoadedRepair {
	/** OptiFine's own class, at the path it has inside the classpath jar (prefix already stripped). */
	public static final String ENTRY = "net/optifine/shaders/Shaders.class";
	private static final String OWNER = "net/optifine/shaders/Shaders";
	private static final String LOAD_SHADER_PACK = "loadShaderPack";
	private static final String FIELD = "shaderPackLoaded";
	private static final String FIELD_DESC = "Z";

	private ShadersPackLoadedRepair() {
	}

	/** What one repair attempt did, for the caller's log. */
	public enum Outcome {
		/** The missing jump was inserted. */
		REPAIRED,
		/** The defect is not in this class - nothing was written. */
		NOT_PRESENT,
		/** The class does not have the shape this repair knows; it was left alone. */
		UNEXPECTED
	}

	/**
	 * Inserts the branch if this class has the defect.
	 *
	 * @return the class to write, or {@code null} when nothing was changed
	 */
	public static byte[] apply(byte[] original) {
		ClassNode node = new ClassNode();
		new ClassReader(original).accept(node, 0);

		MethodNode target = null;
		for(MethodNode method : node.methods) {
			if(LOAD_SHADER_PACK.equals(method.name) && "()V".equals(method.desc)) {
				target = method;
				break;
			}
		}
		if(target == null || target.instructions == null) {
			return null;
		}

		// The exact sequence, five instructions long, so that it cannot be confused with any other clear of the
		// field: PUTSTATIC shaderPackLoaded, ICONST_0, PUTSTATIC shaderPackLoaded (the lost else block), then
		// GETSTATIC shaderPackLoaded, IFEQ (the check that decides which line is logged). Exactly one match is
		// expected; on the fixed builds there is none and on anything unexpected there may be several, and in both
		// cases the honest answer is to change nothing.
		FieldInsnNode outcome = null;
		InsnNode clearValue = null;
		FieldInsnNode clear = null;
		LabelNode noPack = null;
		int matches = 0;
		for(AbstractInsnNode instruction = target.instructions.getFirst(); instruction != null;
				instruction = instruction.getNext()) {
			AbstractInsnNode second = nextReal(instruction);
			AbstractInsnNode third = nextReal(second);
			AbstractInsnNode fourth = nextReal(third);
			AbstractInsnNode fifth = nextReal(fourth);
			if(!isField(instruction) || !(second instanceof InsnNode clearNode)
					|| clearNode.getOpcode() != Opcodes.ICONST_0 || !isField(third)
					|| !isRead(fourth) || !(fifth instanceof JumpInsnNode branch)
					|| branch.getOpcode() != Opcodes.IFEQ) {
				continue;
			}
			matches++;
			outcome = (FieldInsnNode) instruction;
			clearValue = clearNode;
			clear = (FieldInsnNode) third;
			noPack = branch.label;
		}
		if(matches != 1) {
			return null;
		}

		// The label the clear starts at: the one between the lookup's PUTSTATIC and the ICONST_0. It is walked back
		// to from the ICONST_0 rather than taken from the iteration position, which is the label *before* the
		// lookup - measured on the first version of this repair, which then found no jump to retarget and reported
		// "nothing to repair" on a class that plainly had the defect.
		LabelNode clearEntry = null;
		for(AbstractInsnNode previous = clearValue.getPrevious(); previous != null; previous = previous.getPrevious()) {
			if(previous instanceof LabelNode label) {
				clearEntry = label;
				break;
			}
		}
		if(clearEntry == null || noPack == null) {
			return null;
		}

		// The jump that implements the antialiasing/fabulous skip, found by the label it targets: the clear is also
		// its destination, which is why the label sits between the lookup and the ICONST_0.
		JumpInsnNode skip = null;
		for(AbstractInsnNode instruction = target.instructions.getFirst(); instruction != null;
				instruction = instruction.getNext()) {
			if(instruction instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFNE
					&& jump.label == clearEntry) {
				skip = jump;
				break;
			}
		}
		if(skip == null) {
			return null;
		}

		// Frame-neutral by construction: the clear is deleted, and the skip jump is pointed at the "No shaderpack
		// loaded." branch, which is already a branch target (of the IFEQ that reads the field) and therefore already
		// carries a stack map frame. An inserted GOTO to the instruction after the clear was tried first and failed
		// verification - that instruction is reached by fall-through in OptiFine's own code and has no declared
		// frame, so a jump to it is a VerifyError.
		skip.label = noPack;
		target.instructions.remove(clearValue);
		target.instructions.remove(clear);

		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		return writer.toByteArray();
	}

	/**
	 * The next real instruction, skipping the pseudo-nodes ASM keeps in the list.
	 *
	 * <p>This is not cosmetic. Measured on the 1.21.9 class in a classpath jar: the clear the repair looks for sits
	 * behind a label, because it is also the target of the {@code ifne} that implements the antialiasing/fabulous
	 * skip - so {@code getNext()} from the lookup's {@code PUTSTATIC} returns a {@code LabelNode}, not the
	 * {@code ICONST_0}, and a repair that demanded immediate adjacency found nothing and silently changed nothing.</p>
	 */
	private static AbstractInsnNode nextReal(AbstractInsnNode instruction) {
		AbstractInsnNode next = instruction == null ? null : instruction.getNext();
		while(next != null && next.getOpcode() < 0) {
			next = next.getNext();
		}
		return next;
	}

	private static boolean isField(AbstractInsnNode instruction) {
		return instruction instanceof FieldInsnNode field && OWNER.equals(field.owner) && FIELD.equals(field.name)
				&& FIELD_DESC.equals(field.desc) && field.getOpcode() == Opcodes.PUTSTATIC;
	}

	/** GETSTATIC of the same field - the check that reads what the lookup produced. */
	private static boolean isRead(AbstractInsnNode instruction) {
		return instruction instanceof FieldInsnNode field && OWNER.equals(field.owner) && FIELD.equals(field.name)
				&& FIELD_DESC.equals(field.desc) && field.getOpcode() == Opcodes.GETSTATIC;
	}

	/**
	 * Repairs one classpath jar in place: {@code java ... ShadersPackLoadedRepair <optifine-classpath.jar>}.
	 *
	 * <p>For rebuilding an already-prepared line, whose classpath jar was written before this repair existed. A fresh
	 * run of the pipeline applies it through {@link OptifinePipeline#split} without this step.</p>
	 */
	public static void main(String[] args) throws IOException {
		if(args.length != 1) {
			System.err.println("usage: ShadersPackLoadedRepair <optifine-classpath.jar>");
			System.exit(2);
		}
		Path jar = Path.of(args[0]);
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
				if(ENTRY.equals(entry.getName())) {
					seen++;
					byte[] repaired = apply(data);
					if(repaired != null) {
						data = repaired;
						changed = true;
						System.out.println("repaired " + entry.getName()
								+ ": inserted the jump its 1.21.9 build is missing after the shader-pack lookup");
					} else {
						System.out.println(entry.getName() + ": nothing to repair (this build has the branch)");
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
			throw new IOException("no " + ENTRY + " in " + jar);
		}
		if(changed) {
			Files.move(temporary, jar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			System.out.println("written: " + jar);
		} else {
			Files.deleteIfExists(temporary);
		}
	}
}
