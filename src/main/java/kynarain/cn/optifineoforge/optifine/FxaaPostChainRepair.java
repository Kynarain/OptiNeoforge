package kynarain.cn.optifineoforge.optifine;

import java.nio.charset.StandardCharsets;

/**
 * Points OptiFine's FXAA post chain at the vertex stage this game version actually ships, so FXAA can compile.
 *
 * <p>Measured 2026-09-24 on 1.21.9, with FXAA requested for real (optionsshaders.txt {@code antialiasingLevel=2},
 * the option whose GUI labels are "FXAA 2x"/"FXAA 4x"): the client logs</p>
 *
 * <pre>[Render thread/ERROR] [com.mojang.blaze3d.opengl.GlDevice/]: Couldn't find source for VERTEX shader (minecraft:post/blit)
 * [Render thread/ERROR] [com.mojang.blaze3d.opengl.GlDevice/]: Couldn't compile pipeline minecraft:fxaa_of_2x/1: vertex shader minecraft:post/blit was invalid</pre>
 *
 * <p>FXAA is therefore engaged - OptiFine ships its chain at the post-1.21.6 location
 * ({@code assets/minecraft/post_effect/fxaa_of_2x.json} plus its own
 * {@code assets/minecraft/shaders/post/fxaa_of_2x.{vsh,fsh}}), the game found the JSON and started building the
 * pipeline - and then the second pass fails, because it asks for a vertex shader source
 * {@code minecraft:post/blit} that does not exist: 1.21.9 ships {@code assets/minecraft/shaders/post/blit.fsh} (the
 * fragment stage) but no {@code post/blit.vsh}, and every one of the game's own post-effect passes uses
 * {@code minecraft:core/screenquad} as its vertex stage instead - for example its own blit pass in
 * {@code post_effect/entity_outline.json} is exactly {@code core/screenquad} + {@code post/blit} + {@code BlitConfig}.
 * FXAA stops silently; nothing crashes.</p>
 *
 * <p>So the repair is one field, in the blit pass of both chains: {@code vertex_shader} becomes
 * {@code minecraft:core/screenquad}, which turns OptiFine's blit pass into the same pair the game itself uses. The
 * FXAA pass is left alone - it names OptiFine's own {@code post/fxaa_of_2x} vertex stage, which this jar carries and
 * which compiled (the log named pass {@code /1}, not {@code /0}).</p>
 *
 * <p>Applied by {@link OptifinePipeline#split} while OptiFine's resources are written into the classpath jar, and
 * reported either way: on a line whose JSON already names a vertex stage it does not touch anything.</p>
 */
public final class FxaaPostChainRepair {
	/** The two chains OptiFine ships, at the location the post-1.21.6 resource layout uses. */
	public static final String[] ENTRIES = {
		"assets/minecraft/post_effect/fxaa_of_2x.json",
		"assets/minecraft/post_effect/fxaa_of_4x.json"
	};

	/** OptiFine's own vertex stages for those chains - the ones written for the pre-1.21.6 pipeline. */
	public static final String[] VERTEX_ENTRIES = {
		"assets/minecraft/shaders/post/fxaa_of_2x.vsh",
		"assets/minecraft/shaders/post/fxaa_of_4x.vsh"
	};

	/**
	 * What the old vertex stage is recognised by: it takes the quad from a vertex attribute the new pipeline does not
	 * supply. Measured on the 1.21.9 build - {@code in vec4 Position} plus {@code ProjMat}, while the game's own
	 * {@code core/screenquad.vsh} builds the quad from {@code gl_VertexID} and outputs only {@code texCoord}.
	 */
	private static final String OLD_VERTEX_MARKER = "in vec4 Position";

	/**
	 * The replacement vertex stage: the game's own quad construction, with OptiFine's {@code posPos} kept.
	 *
	 * <p>Its fragment stage declares {@code in vec2 texCoord} and {@code in vec4 posPos}, so the stage has to produce
	 * both. The quad and {@code texCoord} are copied from this version's {@code core/screenquad.vsh} (the triangle
	 * spans the screen with {@code texCoord} running 0..1 across it), and the {@code posPos} arithmetic is OptiFine's
	 * own, unchanged - it only ever needed {@code texCoord} and the {@code SamplerInfo} block, both of which the new
	 * pipeline still provides (the game's own post shaders use that block too).</p>
	 */
	private static final String NEW_VERTEX = String.join("\n",
		"#version 330",
		"",
		"layout(std140) uniform SamplerInfo",
		"{",
		"    vec2 OutSize;",
		"    vec2 InSize;",
		"};",
		"",
		"layout(std140) uniform FxaaConfig",
		"{",
		"    float SubPixelShift;",
		"    float SpanMax;",
		"    float ReduceMul;",
		"};",
		"",
		"out vec2 texCoord;",
		"out vec4 posPos;",
		"",
		"void main()",
		"{",
		"    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);",
		"    gl_Position = vec4(uv * vec2(2.0, 2.0) + vec2(-1.0, -1.0), 0.0, 1.0);",
		"    texCoord = uv;",
		"",
		"    posPos.xy = texCoord.xy + (0.5 / OutSize * vec2(0.5 - SubPixelShift));",
		"    posPos.zw = texCoord.xy - (0.5 / OutSize * vec2(0.5 + SubPixelShift));",
		"}",
		"");

	/** The blit pass as OptiFine writes it, and what the game's own blit passes use. */
	private static final String BLIT_PAIR = "\"vertex_shader\": \"minecraft:post/blit\",";
	private static final String SCREENQUAD = "\"vertex_shader\": \"minecraft:core/screenquad\",";

	private FxaaPostChainRepair() {
	}

	/**
	 * Rewrites the blit pass's vertex stage if this file still asks for the missing {@code post/blit} one.
	 *
	 * @return the file to write, or {@code null} when nothing was changed
	 */
	public static byte[] apply(byte[] original) {
		String text = new String(original, StandardCharsets.UTF_8);
		if(!text.contains(BLIT_PAIR)) {
			return null;
		}
		// Verified against both chains as OptiFine ships them: the exact string occurs once per file, in the blit
		// pass (the FXAA pass names "minecraft:post/fxaa_of_2x"/"fxaa_of_4x" as its vertex stage), so a blind
		// replacement of this one line cannot touch the pass that already compiles.
		String replaced = text.replace(BLIT_PAIR + "\n", SCREENQUAD + "\n")
				.replace(BLIT_PAIR + "\r\n", SCREENQUAD + "\r\n");
		if(replaced.equals(text)) {
			return null;
		}
		return replaced.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Replaces OptiFine's FXAA vertex stage if it is the pre-1.21.6 one.
	 *
	 * <p>Measured 2026-09-24 on 1.21.9: with only the blit pass repaired, turning FXAA on produced a near-black frame
	 * (mean luma 11.8 against 134.2 with FXAA off, taken from the game's own screenshots, no capture tooling in the
	 * path), and pointing the FXAA pass at the game's {@code core/screenquad} as well brought the frame back (mean
	 * luma 182) - which identified this shader, not the chain, as the cause. {@code screenquad} alone is not the fix
	 * either: it outputs only {@code texCoord}, so the fragment stage's {@code posPos} would be undefined and the FXAA
	 * would sample the wrong places (that diagnostic frame was visibly too bright). This keeps both.</p>
	 *
	 * @return the file to write, or {@code null} when nothing was changed
	 */
	public static byte[] applyVertex(byte[] original) {
		String text = new String(original, StandardCharsets.UTF_8);
		if(!text.contains(OLD_VERTEX_MARKER)) {
			return null;
		}
		return NEW_VERTEX.getBytes(StandardCharsets.UTF_8);
	}
}
