/*
 * OptifiNeoforge - loads OptiFine into NeoForge.
 * Licensed under MPL-2.0; see LICENSE at the repository root.
 */

package kynarain.cn.optifineoforge.optifine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The two version strings OptiFine declares inside its own {@code Config} class.
 *
 * <p>OptiFine does not put its version in the manifest in a form a loader can use, so nobody can
 * tell from the outside which build a jar is. {@code net.optifine.Config} - or {@code
 * notch/net/optifine/Config} in the older layout - declares both as {@code static final String}
 * constants, so reading them means reading two entries out of the class file's constant pool.
 * That is done here with plain JDK code instead of ASM: the two constants are all that is wanted,
 * and this way the class works anywhere, including in a tool that has no loader on its
 * classpath.</p>
 *
 * @param optifineVersion the value of {@code Config.VERSION}, e.g. {@code "1.21.11_HD_U_J9"}
 * @param minecraftVersion the value of {@code Config.MC_VERSION}, e.g. {@code "1.21.11"}
 */
public record OptifineConfig(String optifineVersion, String minecraftVersion) {

	/** The locations {@code Config} has had over the years, newest layout first. */
	private static final String[] CONFIG_PATHS = {
			"net/optifine/Config.class",
			"notch/net/optifine/Config.class",
			"srg/net/optifine/Config.class",
	};

	/** Reads the declared versions from {@code jar}. */
	public static OptifineConfig read(Path jar) throws IOException {
		try(ZipFile zip = new ZipFile(jar.toFile())) {
			for(String path : CONFIG_PATHS) {
				ZipEntry entry = zip.getEntry(path);
				if(entry == null) {
					continue;
				}

				try(InputStream stream = zip.getInputStream(entry)) {
					Map<String, String> constants = stringConstants(stream.readAllBytes());
					String version = constants.get("VERSION");
					String minecraft = constants.get("MC_VERSION");
					if(version != null && minecraft != null) {
						return new OptifineConfig(version, minecraft);
					}
					throw new IOException("Found " + path + " in " + jar.getFileName() + " but it declares no VERSION/MC_VERSION: " + constants);
				}
			}
		}
		throw new IOException("No OptiFine Config class in " + jar.getFileName() + " - not an OptiFine jar?");
	}

	/** Whether any of the known {@code Config} locations is present in {@code jar}. */
	public static boolean isOptifineJar(Path jar) {
		try(ZipFile zip = new ZipFile(jar.toFile())) {
			for(String path : CONFIG_PATHS) {
				if(zip.getEntry(path) != null) {
					return true;
				}
			}
			return false;
		} catch(IOException e) {
			return false;
		}
	}

	/**
	 * The string constants of every {@code static final String} field in a class file.
	 *
	 * <p>Only the constant pool and the field table are walked, and only {@code ConstantValue}
	 * attributes are looked at - enough to read the two version strings, and no more.</p>
	 */
	static Map<String, String> stringConstants(byte[] classFile) throws IOException {
		Cursor cursor = new Cursor(classFile);
		if(cursor.u4() != 0xCAFEBABEL) {
			throw new IOException("Not a class file");
		}
		cursor.u2(); // minor version
		cursor.u2(); // major version

		int poolCount = cursor.u2();
		String[] utf8 = new String[poolCount];
		// Field and method refs, class entries and so on are not needed; only Utf8 and String.
		int[] stringRef = new int[poolCount];
		Object[] constant = new Object[poolCount];

		for(int index = 1; index < poolCount; index++) {
			int tag = cursor.u1();
			switch(tag) {
				case 1 -> utf8[index] = cursor.utf8();
				case 7, 8, 16, 19, 20 -> {
					int value = cursor.u2();
					if(tag == 8) {
						stringRef[index] = value;
					}
				}
				case 15 -> cursor.skip(3); // MethodHandle
				case 3, 4, 9, 10, 11, 12, 17, 18 -> cursor.skip(4);
				case 5, 6 -> { // Long and Double take two constant pool slots
					cursor.skip(8);
					index++;
				}
				default -> throw new IOException("Unknown constant pool tag " + tag);
			}
		}

		cursor.u2(); // access flags
		cursor.u2(); // this class
		cursor.u2(); // super class
		int interfaces = cursor.u2();
		cursor.skip(interfaces * 2);

		int fields = cursor.u2();
		Map<String, String> constants = new HashMap<>();
		for(int field = 0; field < fields; field++) {
			cursor.u2(); // access flags
			int nameIndex = cursor.u2();
			cursor.u2(); // descriptor
			int attributes = cursor.u2();
			for(int attribute = 0; attribute < attributes; attribute++) {
				int attributeName = cursor.u2();
				int length = cursor.u4AsInt();
				int start = cursor.position();
				if("ConstantValue".equals(utf8[attributeName]) && length == 2) {
					int valueIndex = cursor.u2();
					String name = utf8[nameIndex];
					if(stringRef[valueIndex] != 0) {
						constants.put(name, utf8[stringRef[valueIndex]]);
					} else if(constant[valueIndex] != null) {
						constants.put(name, String.valueOf(constant[valueIndex]));
					}
				}
				cursor.position(start + length);
			}
		}

		return constants;
	}

	/** A big-endian reader over a class file, with the bounds checks javac would have given us. */
	private static final class Cursor {
		private final byte[] data;
		private int position;

		Cursor(byte[] data) {
			this.data = data;
		}

		int position() {
			return position;
		}

		void position(int position) {
			if(position < 0 || position > data.length) {
				throw new IndexOutOfBoundsException(position);
			}
			this.position = position;
		}

		void skip(int bytes) {
			position(position + bytes);
		}

		int u1() {
			check(1);
			return data[position++] & 0xFF;
		}

		int u2() {
			check(2);
			int value = ((data[position] & 0xFF) << 8) | (data[position + 1] & 0xFF);
			position += 2;
			return value;
		}

		long u4() {
			check(4);
			long value = ((long)(data[position] & 0xFF) << 24) | ((data[position + 1] & 0xFF) << 16)
					| ((data[position + 2] & 0xFF) << 8) | (data[position + 3] & 0xFF);
			position += 4;
			return value;
		}

		int u4AsInt() {
			return (int) u4();
		}

		String utf8() {
			int length = u2();
			check(length);
			// Modified UTF-8, but for the identifiers and version strings seen here plain UTF-8 works.
			String value = new String(data, position, length, StandardCharsets.UTF_8);
			position += length;
			return value;
		}

		private void check(int bytes) {
			if(position + bytes > data.length) {
				throw new IndexOutOfBoundsException("class file ends at " + data.length + ", wanted " + bytes + " more at " + position);
			}
		}
	}

	/** Convenience for the command line tool. */
	static List<String> describe(Path jar) {
		List<String> lines = new ArrayList<>();
		try {
			OptifineConfig config = read(jar);
			lines.add("optifine version: " + config.optifineVersion());
			lines.add("minecraft version: " + config.minecraftVersion());
		} catch(IOException e) {
			lines.add("optifine version: (unreadable: " + e.getMessage() + ")");
		}
		return lines;
	}

	/** Development aid: {@code OptifineConfig <jar> [more jars...]}. */
	public static void main(String[] args) {
		for(String arg : args) {
			System.out.println("# " + arg);
			describe(Path.of(arg)).forEach(line -> System.out.println("  " + line));
		}
	}
}
