# 寮€鍙戣褰?26.x 绾?

鏈枃浠惰褰曟湰绾胯嚜宸辩殑瀹炴祴,浠ュ強璺ㄧ嚎鍏辩敤鐨勪袱涓皟鐮旂粨璁虹殑鍏ュ彛:`docs/RESEARCH-optifine.md`(OptiFine jar 鐨勭粨鏋勪笌琛ヤ竵鏈哄埗,7 涓瀯寤洪€愪釜鎷嗗紑鐪?涓?`docs/RESEARCH-neoforge.md`(NeoForge/FML 渚ф瘡涓増鏈厑璁镐粈涔?銆?
## 鎬庝箞璇?OptiFine 鐨?jar

`src/main/java/kynarain/cn/optifineoforge/optifine/OptifineJar.java` 涓?`OptifineConfig.java` 鍙緷璧?JDK,涓嶄緷璧?Minecraft銆丯eoForge 鎴栦换浣?loader,鎵€浠ュ彲浠ョ洿鎺?`javac` 缂栬瘧鍚庡鐫€鐪熷疄 jar 璺?

```powershell
javac -d out src\main\java\kynarain\cn\optifineoforge\optifine\*.java
java -cp out kynarain.cn.optifineoforge.optifine.OptifineJar <OptiFine jar 璺緞>
java -cp out kynarain.cn.optifineoforge.optifine.OptifineConfig <OptiFine jar 璺緞>
```

OptiFine 鐨?jar 涓嶈繘浠撳簱(`test-downloads/` 宸插拷鐣?銆備笅杞借蛋绗笁鏂归暅鍍?娉ㄦ剰**瀹冭繑鍥炵殑 302 閲?`Location` 鏄浉瀵硅矾寰?*,`curl -L` 涓嶄竴瀹氳窡寰椾笅鍘?鐩存帴鐢ㄩ暅鍍忕殑 maven 璺緞鏇寸ǔ:

```powershell
curl.exe -sSL -o preview_OptiFine_26.1.2_HD_U_K1_pre2.jar `
  "https://bmclapi2.bangbang93.com/maven/com/optifine/26.1.2/preview_OptiFine_26.1.2_HD_U_K1_pre2.jar"
```

## 瀹炴祴:7 涓瀯寤虹殑缁撴瀯(2026-09-14)

| OptiFine 鏋勫缓 | 澶у皬 | 鏉＄洰 | 鍏冩暟鎹?| ModLauncher 鏈嶅姟 | `notch/` | `srg/` | `patch/` |
|---|---|---|---|---|---|---|---|
| 1.20.1 HD_U_I6 | 7,145,205 | 6493 | `mods.toml` | 鏈?| 703 | 635 | 4888 |
| 1.20.4 HD_U_I7 | 7,232,045 | 6568 | `mods.toml` | 鏈?| 711 | 643 | 4948 |
| 1.21.1 HD_U_J1 | 7,322,249 | 6594 | `mods.toml` | 鏈?| 736 | 660 | 4924 |
| 1.21.6 HD_U_J6_pre3 | 7,518,992 | 6928 | `mods.toml` | 鏈?| 773 | 709 | 5172 |
| 1.21.7 HD_U_J6_pre7 | 7,587,441 | 6986 | `mods.toml` | 鏈?| 776 | 712 | 5224 |
| 1.21.11 HD_U_J9 | 8,045,105 | 7351 | `mods.toml` | 鏈?| 827 | 756 | 5496 |
| 26.1.2 HD_U_K1_pre2 | 7,797,229 | 7356 | `mods.toml` | 鏈?| 829 | 759 | 5490 |

1. **鍏ㄩ兘鏄畨瑁呭櫒褰㈡€?*:甯?`patch/`(xdelta 宸垎鍖?+ 鍚屽悕 `.md5`)涓?`optifine/Installer`銆?2. **鍏冩暟鎹竴寰嬫槸 Forge 鐨?* `META-INF/mods.toml`(`modLoader="javafml"`,`loaderVersion="[14,)"`,`modId="optifine"`),**娌℃湁涓€涓瀯寤哄甫 `neoforge.mods.toml`** 鈥斺€?鍖呮嫭 26.1.2銆?3. **姣忎釜鏋勫缓閮芥敞鍐?ModLauncher 鏈嶅姟**:`META-INF/services/cpw.mods.modlauncher.api.ITransformationService` 鈫?`optifine.OptiFineTransformationService`,杞瀷鍣ㄦ槸 `optifine.OptiFineTransformer`(26.1.2 閲屾敼鍚嶄负 `OptiFineBaseTransformer`,甯搁噺鍊间笉鍙?,鍙緷璧?ModLauncher銆丄SM 涓?log4j銆?4. **`patch/` 涓嬫湁涓変釜鍓嶇紑**,涓嶆槸涓€涓?`patch/srg/`銆乣patch/notch/`銆乣patch/assets/`(1.20.1:824 / 822 / 3242 鏉?銆傝浆鍨嬪櫒鍙 `patch/srg/` 涓?`srg/`,**浠庝笉纰?`notch/`**銆?5. **琛ヤ竵璐熻浇鐨勫懡鍚嶇┖闂村湪 1.20.6 鎹㈣繃涓€娆?*:1.20.1 / 1.20.2 / 1.20.4 鐨勮礋杞介噷鏄?*鐪熸鐨?SRG 鎴愬憳鍚?*(`f_127499_` 涔嬬被),OptiFine 鑷繁鐨?`srg/net/optifine/**` 涔熸槸;浠?**1.20.6** 璧疯礋杞戒笌鑷绫婚噷**涓€涓?SRG 鎴愬憳閮芥病鏈?*,宸茬粡鏄?Mojang 瀹樻柟鍚?鈥斺€?`srg/` 浠庨偅鏃惰捣鍙槸娌跨敤鐨勭洰褰曞悕銆?6. **26.1.2 璧板緱鏇磋繙**:`patch/srg` 涓?`patch/notch` 鐨?566 涓悓鍚嶈礋杞?*閫愬瓧鑺傜浉鍚?*,璐熻浇閲屾病鏈変换浣曟贩娣嗗悕,`patch2.cfg` 鎶?srg 鏄犲皠鎴愭亽绛?瀹冨悓鏃?*鑷甫 NeoForge 鑷繁鐨?SPI**:`META-INF/services/net.neoforged.neoforgespi.transformation.ClassProcessor` 涓?`...locating.IModFileCandidateLocator` 閮芥寚鍚?`optifine.OptiFineClassProcessor`,manifest 閲岃繕鏈?`FMLModType: LIBRARY`銆?
## 26.1.2:瀹㈡埛绔?jar 涓庣绾胯ˉ涓佸疄娴?2026-09-14)

鏈満浠?Mojang 瀹樻柟娓呭崟鍙栧埌 26.1.2 鐨?client jar(38,113,927 瀛楄妭,30,675 涓潯鐩?,纭瀹?*鏈贩娣?*:`net/minecraft/client/Minecraft.class` 瀛樺湪,褰㈠ `a.class` 鐨勬贩娣嗙被 **0 涓?*銆?
鐢ㄥ悓涓€濂楀弽灏勮皟鐢ㄥ瀹冭窇 OptiFine 鐨勮ˉ涓佸櫒:

| 椤圭洰 | 缁撴灉 |
|---|---|
| 鑰楁椂 | 1.5 绉?|
| 杈撳嚭 | 7,898,538 瀛楄妭,4611 涓潯鐩?|
| 杈撳嚭鐨勭洰褰曟瀯鎴?| `assets/` 1785銆乣notch/` 1395銆乣srg/` 1325銆乣optifine/` 53銆乣doc/` 39,鍙︽湁 `patch.cfg` / `patch2.cfg` |
| 琚ˉ涓佺殑娓告垙绫?| 972 涓?鍚嶅瓧閮芥槸瀹樻柟鍚?涓?娓告垙鏈贩娣?涓€鑷? |
| 鎶ラ敊 | 鏃?閫€鍑虹爜 0 |

涔熷氨鏄:**鏈贩娣嗙殑 26.1.2 涓?OptiFine 鑷繁鐨勮ˉ涓佸櫒鍙互姝ｅ父宸ヤ綔,浜у嚭鐨勮ˉ涓佺被宸茬粡鏄繍琛屾湡鐢ㄧ殑瀹樻柟鍚?鈥斺€?杩欎竴绾夸笉闇€瑕佷换浣曢噸鏄犲皠**銆傝繖鏄暣鏉￠摼涓婄浜屼釜琚绾块獙璇佺殑鐜妭(绗竴涓槸 1.20.1,瑙?`docs/RESEARCH-optifine.md` 涓庝笂琛?銆?
## 琛ヤ竵浜х墿鐨勬媶鍒?2026-09-14)

琛ヤ竵鍣ㄧ殑杈撳嚭杩樿鎷嗘垚涓や唤,`OptifinePipeline.split` 鍋氳繖浠朵簨:涓€浠界粰 classpath(OptiFine 鑷繁鐨勭被涓庤祫婧?,涓€浠芥槸琚ˉ涓佺殑娓告垙绫?鎸夊唴閮ㄥ悕绱㈠紩,鐢ㄤ簬椤舵浛鍘熺増)銆備袱鏉¤鍒?

- **鍙彇 `srg/` 閭ｄ竴浠藉彉浣?* 鈥斺€?杞瀷鍣ㄨ鐨勫氨鏄畠,`notch/` 閭ｄ唤涓㈠純,鍏嶅緱澶氬嚭涓€濂楀悓鍚嶇殑绫汇€?- **涓㈡帀瀹夎鍣?*:`optifine/Installer*`銆乣optifine/Patcher*`銆乣optifine/Differ*`銆乣optifine/xdelta/**`銆乣optifine/json/**` 鈥斺€?鎴戜滑鑷繁璺戣ˉ涓佸櫒,杩愯鏈熶笉闇€瑕佸畠浠?鑰?loader 鍙鐪嬭 `optifine/Installer.class` 灏变細鎷掔粷鏁翠釜 jar(瑙?`docs/RESEARCH-neoforge.md`)銆傚叾浣欎竴鍒?OptiFine 鐨?`assets/`銆乣doc/`銆乣META-INF/services/**`)鍘熸牱淇濈暀銆?
瀹炴祴:

| | 26.1.2 | 1.20.1 |
|---|---|---|
| 琛ヤ竵鑰楁椂 | 1.5 绉?| 1.2 绉?|
| 琛ヤ竵鍣ㄨ緭鍑?| 7,898,538 瀛楄妭 / 4611 鏉＄洰 | 6,638,739 瀛楄妭 / 4049 鏉＄洰 |
| 鎷嗗垎鍚?classpath jar | 2,789,483 瀛楄妭 / 2616 鏉＄洰(`assets/` 1785銆乣net/optifine/` 759銆乣doc/` 39銆佹牴 `optifine/` 19銆乣META-INF/` 5) | 2,638,138 瀛楄妭 / 2489 鏉＄洰 |
| 琚ˉ涓佺殑娓告垙绫?| **566**(鍏朵腑 `net/minecraft` 486) | **412**(鍏朵腑 `net/minecraft` 354) |
| `optifine/Installer.class` | 涓嶅瓨鍦?宸插墧闄? | 涓嶅瓨鍦?宸插墧闄? |

鎷嗗嚭鏉ョ殑绫绘槸鍚?*鍙獙璇?*(JVM 鏍￠獙閫氳繃)杩樻病娴?鈥斺€?閭ｉ渶瑕?ASM 鎴?`ClassLoader` 灞傞潰鐨勬鏌?浠ュ強鐪熸満鍔犺浇銆?
## 棣栨鐪熸満瀹炴祴:NeoForge 21.4.149(Minecraft 1.21.4,2026-09-14)

鏈満瑁呭ソ浜?JDK 21 涓?25(`C:\Users\kynar\.jdks\`),浜庢槸鍙互鐪熺殑鍚姩涓€娆?NeoForge銆傜敤鐨勬槸鐢ㄦ埛鍚姩鍣ㄩ噷宸叉湁鐨?`1.21.4-NeoForge_21.4.149`(FML 6.0.18銆丮odLauncher 11.0.4),閰嶄竴涓嫭绔嬬殑娓告垙鐩綍涓庣嫭绔嬬殑 `mods/`,涓嶅姩鐢ㄦ埛鑷繁鐨勯厤缃€傛祴璇曡剼鏈湪浠撳簱澶栫殑 `C:\Users\kynar\IdeaProjects\optifineoforge-test\launch-neoforge.ps1`銆?
**瀵圭収(鏃犳ā缁?**:鍚姩鍒版爣棰樼晫闈?40 绉?`Sound engine started`,鏃犲穿婧冩姤鍛?鈥斺€?娴嬭瘯鍙版湰韬彲鐢ㄣ€?
**鎶婇噸鏂版墦鍖呰繃鐨?OptiFine 1.21.4(J3)鏀捐繘 `mods/`**,渚濇鍑虹幇涓変釜闂,鍓嶄袱涓凡瑙ｅ喅:

1. **`NoSuchFileException: ...optifine-1.21.4-for-neoforge.jar#214`** 鈥斺€?OptiFine 鐢ㄨ嚜宸辩被鐨?code source 鎵?jar(`getProtectionDomain().getCodeSource().getLocation()`),鍦?NeoForge 涓嬭繖鏄?union 鏂囦欢绯荤粺璺緞 `union:/...jar%23214!/`,瀹冨彧鍘绘帀缁撳熬鐨?`!`,鐣欎笅 `#214`,浜庢槸 `ZipFile` 鎵撲笉寮€,ModLauncher 鐩存帴鍒ゅ畾 `InvalidLauncherSetupException: Invalid Services found OptiFine`銆?   鈫?**淇硶**:`OptifineJarFixer` 閲嶅啓 `optifine.OptiFineTransformationService.toFile(URI)`,鎶?`!` 涔嬪悗涓?`#` 涔嬪悗鐨勯儴鍒嗕竴骞舵埅鎺?閲嶅啓鍚庣殑鏂规硶瑕侀噸绠楁爤甯?鍚﹀垯 JVM 鎶?`Expected stackmap frame at this location`)銆傛墦鍖呮椂鑷姩搴旂敤銆?2. 淇ソ涔嬪悗,**OptiFine 鑷繁鐨勮浆鎹㈡湇鍔″湪 NeoForge 涓婅窇璧锋潵浜?*:

   ```
   [optifine.OptiFineTransformationService]: OptiFine ZIP file: ...\mods\optifine-1.21.4-for-neoforge.jar
   [optifine.OptiFineTransformer]: Target.PRE_CLASS is available
   [optifine.OptiFineTransformer]: Forge JAR not available
   [optifine.OptiFineTransformationService]: OptiFineTransformationService.transformers
   [optifine.OptiFineTransformer]: Targets: 474
   ```

   鍗?鏈嶅姟琚彂鐜般€乯ar 琚墦寮€銆?*474 涓ˉ涓佺洰鏍囨敞鍐屾垚鍔?*,ModLauncher 涓嶅啀鎷掔粷銆傝繖鏄暣鏉¤矾绾跨殑绗竴涓湡鏈鸿瘉鎹€?3. **涓嬩竴涓嫤璺殑闂(灏氭湭瑙ｅ喅)**:FML 鐨勬棭鏈熺獥鍙ｉ樁娈?`DisplayWindow.updateModuleReads` 浼氬幓鎵柟娉曠鍚?鑰?OptiFine 鐨勭被寮曠敤浜?**Forge 鐨?API 绫诲瀷** `net.minecraftforge.client.extensions.IForgeVertexConsumer` 鈥斺€?NeoForge 涓婅繖涓寘涓嶅瓨鍦?`net.neoforged.neoforge.*`),浜庢槸 `ClassNotFoundException` 璁╁惎鍔ㄤ腑姝?瑙佷笅涓€鑺?銆?
## Forge API 涓庢々绫?2026-09-14)

**鍏抽敭鍙戠幇:寮曠敤涓嶅湪绫婚噷,鑰屽湪琛ヤ竵璐熻浇閲屻€?* `IForgeVertexConsumer` 杩欎釜鍚嶅瓧鍦ㄦ暣涓?jar 鐨?`.class` 閲屼竴娆￠兘涓嶅嚭鐜?鍙嚭鐜板湪 `patch/srg/com/mojang/blaze3d/vertex/VertexConsumer.class.xdelta` 鈥斺€?涔熷氨鏄?**OptiFine 鐨勮ˉ涓佽鍘熺増 `VertexConsumer` 鍘诲疄鐜?Forge 鐨勬帴鍙?*銆傛墍浠ュ彧鎵被鏂囦欢鏄笉澶熺殑,蹇呴』杩?`patch/**` 涓€璧锋壂銆?
澶勭悊鍔炴硶(`ForgeApiShims` + `OptifineJar` 涓ゅ):

- 鎵?jar 閲?*鎵€鏈?*鏉＄洰(璺宠繃 `assets/`銆乣doc/` 涓?`notch/`),鏀堕泦鍏ㄩ儴 `net/minecraftforge/**` 鍚嶅瓧 鈥斺€?1.21.4 J3 涓婃槸 **55 涓?*;
- 涓烘瘡涓悕瀛楃敓鎴愪竴涓?*绌烘々绫?*(鎺ュ彛杩樻槸绫?鎸?OptiFine 鑷甫鐨勯偅浠?`notch/<鍚屽悕>` 鐨勮闂爣蹇楀喅瀹?,鎵撹繘浜ょ粰 loader 鐨?jar;
- 椤哄甫**涓㈡帀鏁翠釜 `notch/` 鏍?*(1.21.4 涓婃槸 768 涓潯鐩?:閭ｆ槸娣锋穯鍛藉悕绌洪棿鐨勫彉浣?杞瀷鍣ㄤ粠涓嶈瀹?鑰屽畠鐨勭鍚嶆寚鍚戞贩娣嗗悗鐨勬父鎴忕被鍨?`gng`銆乣akv`),鐣欑潃鍙細鎶婂悓涓€涓け璐ユ尓鍒颁笅涓€涓被鍨嬨€?
**缁撴灉:鍚姩瓒婅繃浜嗙鍚嶆壂鎻忛樁娈?OptiFine 鐪熺殑璺戣捣鏉ヤ簡**:

```
[OptiFine] (Reflector) Class not present: net.minecraftforge.common.extensions.IForgeEntity
[OptiFine] OptiFine_1.21.4_HD_U_J3
[OptiFine] Build: 20250209-131348
[OptiFine] OS: Windows 11 (amd64) version 10.0
[OptiFine] Java: 21.0.12.1, Eclipse Adoptium
[OptiFine] OpenGL: NVIDIA GeForce RTX 4060 Laptop GPU/PCIe/SSE2, version 3.2.0 NVIDIA 591.86
```

**涓嬩竴涓け璐?宸插畾浣?鏈В鍐?**:娓告垙鍦?`Minecraft.<init>` 宕?

```
java.lang.NoSuchMethodError: 'void com.mojang.blaze3d.pipeline.RenderTarget.<init>(boolean, boolean)'
	at com.mojang.blaze3d.pipeline.MainTarget.<init>(MainTarget.java:22)
```

鏂瑰悜:OptiFine 鐨勮ˉ涓佹敼杩?`RenderTarget` 鐨勬瀯閫犲嚱鏁?鑰?`MainTarget` 杩欎竴渚т粛鏄師鐗堝舰鐘?鈥斺€?涓よ€呰**鎴愬**搴旂敤銆傚彲鑳芥槸琛ヤ竵搴旂敤椤哄簭(OptiFine 鐨勮浆鍨嬪櫒涓?NeoForge 鑷繁鐨勮浆鍨嬪櫒鍦ㄥ悓涓€鎵圭被涓婂厛鍚庤繍琛?銆佷篃鍙兘鏌愪釜 xdelta 娌℃湁鎴愬姛搴旂敤銆備笅涓€姝ュ氨鏄妸杩欎釜閰嶅闂鏌ユ竻妤氥€?
### 鏌ユ竻浜?OptiFine 鐨勬暣绫绘浛鎹細涓㈡帀 NeoForge 渚ц繕鍦ㄧ敤鐨勬垚鍛?
瀵规瘮涓変唤 `RenderTarget`(閮芥槸 javap 鍑烘潵鐨?:

| 鏉ユ簮 | 鏋勯€犲嚱鏁?|
|---|---|
| NeoForge 杩愯鏃?NeoForm `client-1.21.4-...-srg.jar`) | `RenderTarget(boolean)`銆乣RenderTarget(boolean, boolean)` 鈥斺€?涓€鍙傞偅涓彧鏄浆璋冧簩鍙?|
| NeoForge 鐨?`MainTarget` | 璋?`super(ZZ)` |
| OptiFine 琛ヤ竵浜х墿(`srg/com/mojang/blaze3d/pipeline/RenderTarget.class`) | **鍙湁 `RenderTarget(boolean)`** |

涔熷氨鏄:OptiFine 鐢?*鑷繁缂栬瘧鐨勪竴浠?* RenderTarget 椤舵帀浜?NeoForge 鐨勯偅浠?鑰岄偅浠芥槸鎸?Forge 鐨勫舰鐘剁紪鐨?灏戜簡 NeoForge 璋冪敤鏂硅鐨勯噸杞姐€傚悓涓€涓ā寮忚繕浼氫涪瀛楁 鈥斺€?淇帀鏋勯€犲嚱鏁颁箣鍚?涓嬩竴涓敊璇氨鏄?NeoForge 鐨?`MainTarget.allocateDepthAttachment` 鎵句笉鍒板瓧娈?`useStencil`(OptiFine 閭ｄ唤鎶婂畠鏀瑰悕鎴愪簡 `stencilEnabled`)銆?
**澶勭悊鍔炴硶**:鍔犱竴涓垜浠嚜宸辩殑 ModLauncher 杞瀷鏈嶅姟(`OptifiNeoforgeTransformationService` + `RenderTargetFix`),鍦?`TargetType.CLASS` 闃舵鎶?OptiFine 涓㈡帀鐨勬垚鍛樿ˉ鍥炲幓(鐩墠鏄偅涓簩鍙傛瀯閫犲嚱鏁?杞皟涓€鍙?銆傚疄娴?

```
[OptifiNeoforge/]: OptifiNeoforgeTransformationService.onLoad, alongside [mixin, OptiFine, fml, OptifiNeoforge]
[OptifiNeoforge/]: OptifiNeoforgeTransformationService.transformers
```

鏈嶅姟涓?OptiFine 鐨勬湇鍔″悓鏃惰鍔犺浇,`NoSuchMethodError: RenderTarget.<init>(ZZ)` 娑堝け,澶辫触鐐规帹杩涘埌涓嬩竴涓己澶辨垚鍛?`NoSuchFieldError: ... MainTarget does not have member field 'boolean useStencil'`)銆?*缁撹:淇鏂瑰悜瀵逛簡,鍙槸瑕侀€愰」琛ラ綈 OptiFine 鏇挎崲鎺夌殑鎴愬憳**(瀛楁 + 鏂规硶),杩欎竴绫讳慨澶嶅彲浠ョ户缁寜鍚屾牱鏂瑰紡鍔犮€?
### 杩欐槸涓€涓彲閲嶅鐨?琛ユ垚鍛?娴佹按绾?
淇帀鏋勯€犲嚱鏁板悗,鍚屼竴涓ā寮忓張鍑虹幇浜嗕袱娆?姣忔閮芥槸涓€涓?NeoForge 渚ф柊澧炵殑鎴愬憳琚?OptiFine 鐨勬浛鎹㈢増鏈涪鎺?

| # | 鎶ラ敊 | 涓㈠け鐨勬垚鍛?| 鎴戜滑鐨勪慨澶?|
|---|---|---|---|
| 1 | `NoSuchMethodError: RenderTarget.<init>(ZZ)` | 浜屽弬鏋勯€犲嚱鏁?| 琛ヤ竴涓浆璋冧竴鍙傜殑鏋勯€犲嚱鏁?|
| 2 | `NoSuchFieldError: MainTarget does not have member field 'boolean useStencil'` | 瀛楁 `useStencil` | 琛ュ瓧娈?骞跺湪琛ュ嚭鏉ョ殑鏋勯€犲嚱鏁伴噷璧嬪€?|
| 3 | `NoSuchMethodError: ReloadableResourceManager.getListeners()` | 璁块棶鍣?`getListeners()` | 琛ヤ竴涓繑鍥?`listeners` 瀛楁鐨?getter(瀛楁鍚嶄笌绫诲瀷閮芥病鍙?鎵€浠ユ槸绮剧‘杩樺師) |
| 4 | `NoSuchMethodError: ReloadableResourceManager.updateListenersFrom(SortedReloadListenerEvent)` | 鎺掑簭鏇存柊鏂规硶 | 鎸?NeoForge 鐨勫疄鐜拌浆璋?`ReloadListenerSort.sort(event)`;OptiFine 閭ｄ唤鎶?`listeners` 澹版槑鎴?final,`putfield` 浼氳鏍￠獙鍣ㄦ嫆缁?鎵€浠ユ敼鎴愬線鍘熷垪琛ㄩ噷 `addAll` |
| 5(褰撳墠) | `NoSuchMethodError: net.minecraft.client.gui.Gui.initModdedOverlays()` | NeoForge 缁?`Gui` 鍔犵殑鏂规硶 | 鏈鐞?|

姣忎慨涓€涓?鍚姩灏卞線鍓嶈蛋涓€姝?15 绉?鈫?20 绉?鈫?45 绉?,**璇存槑鏂瑰悜姝ｇ‘銆佷笖澶辫触鐐规槸鏈夐檺鐨?*銆備笅涓€杞鍋氱殑鏄妸"閫愪釜宕┿€侀€愪釜淇?鎹㈡垚**鎴愭壒鎺ㄥ**:鍒楀嚭 OptiFine 鏇挎崲鎺夌殑鎵€鏈夌被(琛ヤ竵璐熻浇閲?`patch/srg/**` 鐨勯偅浜?,閫愪釜涓庤繍琛屾椂(A=NeoForm 鐨?client jar)瀵规瘮鎴愬憳闆嗗悎,鑷姩鍒楀嚭"杩愯鏃舵湁鐨勩€丱ptiFine 閭ｄ唤娌℃湁鐨?鎴愬憳娓呭崟,鍐嶆寜鍚屼竴濂楄鍒欐壒閲忚ˉ榻?瀛楁鐩存帴琛ャ€乬etter/setter 鎸夊悓鍚嶅瓧娈佃ˉ銆佸叾浣欏厛琛ユ姏寮傚父鐨勬々骞惰褰?銆?
- **FML 6 涓嶆帴鍙?`mods/` 閲屾病鏈夊厓鏁版嵁鐨?jar**(鏃ュ織:`not a valid mod file`)鈥斺€?transformer jar 涓嶉渶瑕?mod 鍏冩暟鎹?杩欐潯鑰佽鐭╁湪杩欓噷涓嶆垚绔嬨€傛祴璇曞彴鐨勫仛娉曟槸鎶婃垜浠殑鏈嶅姟绫诲杩涢偅涓凡缁忚鎺ュ彈鐨?OptiFine jar,骞舵妸鏈嶅姟鏂囦欢**杩藉姞**涓€琛?涓€涓湇鍔℃枃浠跺彲浠ュ垪澶氫釜瀹炵幇)銆?- **PowerShell 鍙橀噺鍚嶄笉鍖哄垎澶у皬鍐?*:`$Out` 涓?`$out` 鏄悓涓€涓彉閲?鑴氭湰閲屽悓鏃剁敤浣滆緭鍑鸿矾寰勪笌娴佸璞℃椂浼氫簰鐩歌鐩栥€?- **.NET Framework 鐨?`ZipFile.CreateFromDirectory` 鍐欏嚭鐨勬潯鐩悕鐢ㄥ弽鏂滄潬**,鑰?SecureJar 鐨?union 鏂囦欢绯荤粺鍙姝ｆ枩鏉?琛ㄧ幇涓?`UnionFileSystem$NoSuchFileException: kynarain/cn/.../RenderTargetFix.class`銆傛墜宸ユ寜 `/` 鍐欐潯鐩嵆鍙€?
## 灏氭湭纭

- 涓婇潰绗?3 鏉′箣澶栬繕鏈夊灏?`net.minecraftforge.*` 寮曠敤闇€瑕佸鐞?1.21.4 鐨?OptiFine 閲屾湁涓€鎵?銆?- 1.20.6 鈥?1.21.8 杩欐潯鍖洪棿鏄惁閮藉儚 1.21.4 涓€鏍疯兘琚?NeoForge 鎺ュ彈(1.20.6 璧?FML 杩樹細棰濆鎷掔粷"鍘熺増 OptiFine jar",鎴戜滑宸插墧闄ゅ畨瑁呭櫒鍏ュ彛,浣嗘湭閫愪釜瀹炴祴)銆?- 琛ヤ竵绫昏 NeoForge 搴旂敤鍚?OptiFine 杩愯鏈熺殑 MD5 鏍￠獙鏄惁浠嶇劧閫氳繃銆?- 1.21.9 鍙婁互鍚?ModLauncher 宸蹭笉瀛樺湪,蹇呴』鏀圭敤 NeoForge 鐨?`ClassProcessor`,杩欐潯璺繕娌¤瘯銆?
## 杩欏鍔犺浇鍣ㄦ剰鍛崇潃浠€涔?
| MC | OptiFine 璐熻浇鐨勫懡鍚嶇┖闂?| NeoForge 杩愯鏈熷懡鍚嶇┖闂?| 缁撹 |
|---|---|---|---|
| 1.20.1 | SRG | SRG(`net.neoforged:forge:47.x`) | 璺戣ˉ涓佸櫒鍚庣敤 `srg/` 閭ｄ唤鍗冲彲,**涓嶉渶瑕侀噸鏄犲皠** |
| 1.20.2 / 1.20.4 | SRG | 瀹樻柟鍚?20.2 璧? | 璺戣ˉ涓佸櫒鍚庤**鎶?SRG 閲嶆槧灏勬垚瀹樻柟鍚?* |
| 1.20.6 鈥?1.21.8 | 瀹樻柟鍚?| 瀹樻柟鍚?| 璐熻浇鐩存帴鍙敤;鍏冩暟鎹繀椤绘敼鍐?**杩欎竴鍖洪棿浠嶈兘澶嶇敤 OptiFine 鑷繁鐨?ModLauncher 杞崲鏈嶅姟**(鈮?FML 9) |
| 1.21.9 鈥?1.21.11 | 瀹樻柟鍚?| 瀹樻柟鍚?| **ModLauncher 宸茶 NeoForge 绉婚櫎**,鑰岃繖涓夌増 OptiFine 鏃㈡病鏈?`ClassProcessor` 涔熸病鏈夊彲鐢ㄧ殑鍏ュ彛 鈫?鍙兘鐢辨垜浠窇琛ヤ竵鍣ㄥ苟瀹炵幇 `ClassProcessor` |
| 26.1.2 | 瀹樻柟鍚?鏈贩娣? | 瀹樻柟鍚?鏈贩娣? | OptiFine **鑷甫 NeoForge 鐨?`ClassProcessor`**,鎴戜滑鐨勫伐浣滄槸閲嶆柊鎵撳寘 jar(鍏冩暟鎹?+ 鍘绘帀瀹夎鍣ㄥ叆鍙?骞舵妸琛ヤ竵绫绘帴涓?|

涓ゆ潯宸茬煡鐨勭‖绾︽潫(瑙?`docs/RESEARCH-neoforge.md`):浠?**1.20.6** 璧?FML 鏈変竴鏉￠拡瀵瑰師鐗?OptiFine jar 鐨勬嫆缁濊鍒?鎺㈤拡鏄?`optifine/Installer.class`),鎵€浠ュ繀椤婚噸鏂版墦鍖?**1.21.3** 璧?OptiFine 澹版槑鐨?`loaderVersion` 浼氭牎楠屽け璐?鍏冩暟鎹繀椤绘敼鍐欍€?
## 灏氭湭纭

- `notch/net/minecraftforge/**`(70 涓被,鎬昏绾?56鈥?4 KB)鏄畬鏁村疄鐜拌繕鏄々;1.21.11 鐨?`srg/net/optifine/shaders/ShadersRender` 閲屾湁**纭紩鐢?* `net/minecraftforge/client/event/ViewportEvent$ComputeCameraAngles`,鑰?NeoForge 涓婃槸 `net.neoforged.neoforge.*` 鈥斺€?杩欐槸宸茬煡鐨勭涓€涓繀鐒惰淇殑鐐广€?- 1.20.6 璧?璐熻浇宸叉槸瀹樻柟鍚?杩欐潯瑙勫垯鏄粠 7 涓瀯寤哄鎺ㄧ殑,`1.21`銆乣1.21.3`銆乣1.21.4`銆乣1.21.8`銆乣1.21.9`銆乣1.21.10` 鏈€愪釜纭銆?- OptiFine 杩愯鏈熶細鐢?MD5 鏍￠獙琛ヤ竵缁撴灉,鑰?NeoForge 鑷繁涔熶細鏀瑰師鐗堢被 鈥斺€?涓よ€呭彔鍔犲悗鏍￠獙鏄惁杩橀€氳繃,鍙兘鍦ㄧ湡鏈轰笂鐪嬨€?- 鏈満娌℃湁 JDK 25,26.1.2 鐨?NeoForge 瀹炰緥杩樿捣涓嶆潵,鍥犳"鑳戒笉鑳借繘娓告垙"杩欎竴灞傚皻鏈獙璇併€?
### 成批补齐:132 个成员,一次修完(2026-09-14)

把"逐个崩、逐个修"换成了离线推导 + 运行时批量补齐:

- `MemberRestorePlan`(离线工具)拿**补丁产物**与**运行时**的 NeoForm client jar 逐个类比对成员集合,输出"运行时有的、OptiFine 那份没有的"清单;
- 实测:**比对 248 个被替换的类,得到 134 个待补成员**(`Gui` 一个类就占 16 个);
- 清单作为资源打进 jar(`optifineoforge/member-restores.txt`),`MemberRestoreTransformer` 在 `TargetType.CLASS` 阶段按清单补:字段按原类型补,方法补一个返回类型默认值的桩,**每个桩都打日志**(所以"哪些只是不崩、哪些是真还原"始终可见);
- 需要真实语义的两个成员(`getListeners`、`updateListenersFrom`)排除在批量之外,仍由各自的 fix 精确还原,因此不依赖 ModLauncher 的转型器执行顺序。

**结果:游戏越过了整个 `Minecraft.<init>`,进入加载界面的渲染循环**(`NeoForgeLoadingOverlay.render` → `DisplayWindow.render`),启动时长从 45 秒推到 60 秒。当前失败点是 FML 自己的早期窗口渲染器抛 `IllegalStateException: Already building.` —— 位置在 FML 内部,方向是某个被补成默认值的桩(或 OptiFine 替换掉的渲染相关类)让它进了不一致状态。下一步:把桩清单与这个失败点对上,优先把与渲染/缓冲区有关的成员从"默认值桩"升级成真实实现。

### 当前阻塞点:被补成"默认值"的桩不够,FML 的早期显示进了不一致状态(2026-09-14)

批量补齐之后启动走到了 `NeoForgeLoadingOverlay.render` → FML 的 `DisplayWindow.render`,然后抛:

```
java.lang.IllegalStateException: Already building.
	at net.neoforged.fml.earlydisplay.SimpleBufferBuilder.begin(SimpleBufferBuilder.java:152)
	at net.neoforged.fml.earlydisplay.RenderElement.renderText(RenderElement.java:252)
```

`-Dfml.earlyprogresswindow=false` 没有效果(NeoForge 21.4 仍然建自己的加载覆盖层)。查了清单里与渲染相关的条目,一共 7 个,其中 4 个是 NeoForge 的 GL 状态备份功能:

```
M com/mojang/blaze3d/platform/GlStateManager _backupGlState (Lnet/neoforged/neoforge/client/GlStateBackup;)V
M com/mojang/blaze3d/platform/GlStateManager _restoreGlState (Lnet/neoforged/neoforge/client/GlStateBackup;)V
M com/mojang/blaze3d/systems/RenderSystem backupGlState (Lnet/neoforged/neoforge/client/GlStateBackup;)V
M com/mojang/blaze3d/systems/RenderSystem restoreGlState (Lnet/neoforged/neoforge/client/GlStateBackup;)V
M com/mojang/blaze3d/vertex/VertexFormatElement$Usage getExtensionInfo ()Lnet/neoforged/fml/common/asm/enumextension/ExtensionInfo;
M com/mojang/blaze3d/vertex/VertexFormatElement findNextId ()I
M net/minecraft/client/renderer/chunk/SectionCompiler compile (...)
```

**判断**:把这四个备份/恢复方法补成"什么都不做的桩"是不对的 —— 它们是渲染状态机的一部分。下一步不该继续加桩,而是**把桩换成真身**:

> 在离线阶段从运行时类里把缺失成员的**原始字节码**抽出来(字段 + 方法体),打成"供体(donor)类"随 jar 一起发;运行时转型器把这些成员连同方法体整体复制进 OptiFine 的替换版本。这样"补回去"就是真的补回去,而不是"让它不崩"。少数方法体的常量池会指向 OptiFine 改名过的成员而失效,这类会在日志里显形,再单独处理。

这条是下一轮的第一件事。

### 供体类:把"补桩"换成"补真身"(2026-09-14)

`MemberRestorePlan` 现在不只输出清单,还**为每个受影响的类生成一个供体(donor)类**:里面只放 OptiFine 丢掉的那些成员 —— 字段带声明,方法带**原始字节码**。供体的内部名就是被修类的名字(所以复制过去的方法体里的 `this` 字段访问能对上),但存放在 `optifineoforge/donors/<类名>.class`,不会被当成那个类加载。1.21.4 上是 **57 个供体、106 KB**。

`MemberRestoreTransformer` 改为从供体复制成员(字段 + 方法体),不再造默认值桩。

**但直接复制会出问题**:复制过来的方法体可能引用 OptiFine 改名过的成员,这时整个类都过不了校验(`VerifyError: Error exists in the bytecode`)。所以生成供体时加了一道**静态校验**:遍历方法体里所有指向本类的字段/方法引用,只要有一个在替换版本里不存在,就**降级成默认值桩并打日志**。实测降级的有 `Gui.initModdedOverlays`、`Gui.renderHealthLevel`、`Camera.getRoll`、`ClientLevel.getModelData` 等(共十几处),其余照抄真身。

**测试台还差一步**:供体作为资源要能被 `getResourceAsStream` 找到,而 union 文件系统需要**目录条目**(之前给服务类踩过同一个坑)。当前日志里成片的 `No donor class for ...` 就是这个原因 —— 打进 combined jar 时要为供体路径补目录条目。修好这一步才能看出供体复制的真实效果。

### 供体的第一次实测:补真身并不总是更好(2026-09-14)

供体打包修好之后(目录条目 + 命令行默认走 `prepareForLoader` 的完整流程),供体真的被读到了(日志里不再有 `No donor class for ...`),`RenderSystem`、`Gui`(16 个)、`ClientLevel`(9 个)等类的成员都是**带原始方法体**补进去的。

但出现**回退**:

```
java.lang.IllegalStateException: Rendersystem called from wrong thread
```

`RenderSystem.backupGlState` / `restoreGlState` 的原始方法体里有线程断言,而这里是在早期启动的主线程上被调用的 —— 于是"真身"比"什么都不做的桩"**更早**把启动打断(桩版本能跑到 60 秒,供体版本 10 秒就死)。

**结论:补什么、怎么补,要按成员区分**,不能一刀切:

- 兜底做法(默认值桩)在"只是被人读一下"的成员上是对的;
- 供体真身在"有实际副作用、且调用环境一致"的成员上是对的;
- 而带线程/环境断言的成员(如 `RenderSystem` 的两个状态方法)两边都不对:桩破坏状态机,真身断言失败。这类需要**按调用环境改写**(把断言去掉,或改成 `GlStateManager` 的直接调用)。

下一步:给供体生成加一条**按成员分类**的规则 —— 方法体里出现 `assertOnRenderThread` 一类环境断言的,先剥掉这层断言再复制(而不是退回桩),并把这批方法在日志里单列。

### 定位一次误判:"wrong thread" 其实是次生错误(2026-09-14)

加了断言剥离(复制方法体前去掉 `RenderSystem.assert*` 这类无参环境断言)之后,启动仍是 10 秒退出,报的还是那句 `Rendersystem called from wrong thread`。**但这次把完整栈翻出来了,发现它不是根因**:

```
java.lang.IllegalStateException: Rendersystem called from wrong thread
  at com.mojang.blaze3d.systems.RenderSystem.constructThreadException(RenderSystem.java:142)
  at com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread(RenderSystem.java:136)
  at com.mojang.blaze3d.systems.RenderSystem.getCapsString(RenderSystem.java:564)
  at net.minecraft.SystemReport.setDetail(SystemReport.java:70)
  at net.minecraft.client.Minecraft.fillSystemReport(Minecraft.java:2334)
  at net.minecraft.client.Minecraft.fillReport(Minecraft.java:2307)
  at net.minecraft.client.main.Main.main(Main.java:200)
```

`Main.main` 走进 `fillReport` 说明**在它之前就已经有一次异常**,而这次断言失败发生在**组装崩溃报告**的时候(主线程读 GL 能力字符串)。也就是说:真正要查的是它之前那个异常,而这个"wrong thread"只是它的影子。

教训:失败的栈要**从头看**,不能只看最后一条消息 —— 上一步据此得出的"供体真身更差"的结论需要重新验证。下一步:在完整 stderr 里找 `Main.main` 之前的第一处异常(可能在 mod 加载或 `Minecraft.run` 的早期),再决定成员的补法。

### 更正:并没有"供体更差"这回事(2026-09-14)

把两次运行的崩溃报告按时间对齐之后,上一节的结论是错的:

| 运行 | 启动 | 崩溃报告 | 描述 |
|---|---|---|---|
| 22:50:43 桩版本 | →22:51:37 | crash-…22.51.37 | `Rendering overlay` / `Already building.` |
| 22:54:53 供体版本 | →22:55:06 | crash-…22.55.06 | `Rendering overlay` / `Already building.` |

**两次都到达同一个阻塞点**(FML 早期显示的 `SimpleBufferBuilder.begin`),只是到达它的耗时不同(55 秒 vs 14 秒,差别来自资源/库加载的波动,不是补法导致)。所以:

- **"供体真身让启动从 60 秒退化到 10 秒"是不成立的**,那是把"最后一条日志消息"当成了根因;
- 断言剥离与 4 个 GL 状态成员的排除仍然保留(它们各自有依据),但**当前唯一的阻塞点自始至终没变**:FML 加载覆盖层的缓冲区状态机。

顺带确认两件事:

- `-Dfml.earlyprogresswindow=false` 在 NeoForge 21.4 上**无效**(jar 里也找不到可用的开关字符串),所以没法用配置绕过早期显示;
- FML 的早期显示**完全用自己的类**(`SimpleBufferBuilder` / `SimpleFont` / `Format` / `Mode`)与 LWJGL,不用 Minecraft 的 `VertexFormat`/`GlStateManager` —— 因此问题不在"OptiFine 替换了哪个渲染类",而在**某一帧的绘制中途抛了异常**,把 `building` 留成了 true(下一帧的第一次 `begin` 就炸)。下一轮要抓的是**第一帧里被吞掉的那个异常**。

### 供体校验再补一条:不许复制 `super` 调用(2026-09-14)

完整日志翻出了**真正的第一处异常** —— 不是 `Already building`,而是**类校验失败**:

```
java.lang.VerifyError: Bad invokespecial instruction: current class isn't assignable to reference class.
Caused by: java.lang.ExceptionInInitializerError: Exception java.lang.VerifyError ...
Caused by: java.lang.NoClassDefFoundError: Could not initialize class net.optifine.reflect.Reflector
```

出错的字节码是 `aload_0; invokevirtual …; aload_0; aload_1; aload_2; invokespecial …; areturn` —— 典型的"复制过来的方法体里有一句 `super.xxx(...)`"。**OptiFine 那份的父类未必和运行时那份一样**,于是一句 `invokespecial` 指向的父类对不上,整个类过不了校验。

修法:供体校验再加一条 —— 方法体里凡是 `INVOKESPECIAL` 且 owner 不是本类的(即 `super` 调用),只有在**替换版本的父类与运行时一致**时才允许复制,否则降级成桩。

修掉之后 `VerifyError` 消失,启动回到同一个阻塞点(FML 加载覆盖层的 `Already building.`),而且这次是**正常的崩溃报告**,不是连锁的初始化失败。**目前 1.21.4 上唯一的阻塞点就是它。**

### 突破:FML 早期显示可以用配置关掉,游戏随即跑进资源加载(2026-09-14)

翻 `FMLConfig$ConfigValue` 的枚举常量时发现它除了窗口尺寸,还有两个开关:`earlyWindowControl` 与 `earlyWindowProvider`,而配置来自**游戏目录的 `config/fml.toml`**。写上:

```toml
earlyWindowControl = false
```

再启动,那个纠缠了十几轮的 `SimpleBufferBuilder.begin -> Already building.` **直接消失**,失败点整个换了一层:

```
Description: Rendering overlay
java.lang.NullPointerException: Cannot invoke "BakedModel.getParticleIcon()" because the return value
  of "BlockModelShaper.getBlockModel(BlockState)" is null
	at LiquidBlockRenderer.setupSprites(LiquidBlockRenderer.java:43)
	at BlockRenderDispatcher.onResourceManagerReload(BlockRenderDispatcher.java:157)
	at ResourceManagerReloadListener...
	at Minecraft.runTick(Minecraft.java:1211) -> Minecraft.run
```

也就是说:**游戏已经在跑主循环、在重载资源、在烘焙模型**了 —— 不再是启动早期的类加载/签名扫描问题,而是"某个模型查不到"。方向很明确:清单里就有 `ModelManager`(4 个成员)、`BlockRenderDispatcher`(4 个)、`LiquidBlockRenderer`(3 个)、`SimpleBakedModel`(5 个)这些与模型烘焙直接相关的类,它们的成员是**桩**(返回 null)还是真身,决定了这条链能不能通 —— 正是"按成员区分补法"要解决的那类。

结论进 `docs/DEVELOPMENT.md`,并记下测试台要求:`config/fml.toml` 里 `earlyWindowControl = false` 是跑 1.21.4 的必要条件。

### 模型链的现状:该补的成员大多降级成了桩(2026-09-14)

关掉早期窗口之后暴露出来的那批 null 模型,和清单里"降级成桩"的成员高度重合。查了一遍,`ModelBlockRenderer` / `BlockRenderDispatcher` / `LiquidBlockRenderer` / 各 `blockentity` 渲染器上**十几个成员都没能复制真身**,原因是同一句:

```
stub (body would not verify): net/minecraft/client/renderer/block/ModelBlockRenderer.tesselateBlock(...)
stub (body would not verify): net/minecraft/client/renderer/block/BlockRenderDispatcher.renderBatched(...)
stub (body would not verify): net/minecraft/client/renderer/block/LiquidBlockRenderer.shouldRenderFace(...)
...
```

也就是说:**这些方法体引用了别的、同样被 OptiFine 丢掉的成员**,所以校验不过,只能退化成"返回默认值"。逐个手补不可持续。

**下一步的做法(传递闭包)**:如果方法体 X 引用了本类里缺失的成员 Y,而 Y 在运行时类里存在,那就**把 Y 也一起补进来**,再重新校验 —— 反复直到不再有新的引用或达到上限。这样"校验不过"就不再等于"退化成桩",而是"把这条依赖链补齐"。真正的边界条件是环引用与上限,两者都要显式处理并打日志。

### 闭包的第一版只跟了本类引用 —— 而这批失败是**跨类**的(2026-09-14)

按上一节的方案加了传递闭包(方法体引用本类缺失成员时,把那个成员也补进来,反复直到稳定)。构建与启动都正常,**但清单大小没变(仍是 132 个),也没有打印任何 `closure added`** —— 说明这些"校验不过"的方法体,**引用的本类成员其实都已在清单里**。

于是原因只能是另一类:**它们引用的是别的类上的成员,而那些类同样被 OptiFine 替换过、同样缺成员**。例如 `ModelBlockRenderer.tesselateBlock` 会去调 `BlockRenderDispatcher`/`BakedModel` 上的方法,OptiFine 版的那些类里未必有。

所以闭包要**跨类**做:

1. 先把"补丁产物里的每个类"与"运行时对应的类"各建一份成员表;
2. 遍历所有待补成员的方法体,收集它引用的**(owner, 名字, 描述符)** —— 不限本类;
3. 只要 owner 在补丁产物里(即也是被替换的类)、而它的替换版本缺这个成员、运行时里又有,就**把该成员加到那个类的清单里**;
4. 重复直到不再变化。

这一版闭包代码留在仓库里(它是跨类版本的前置),下一轮把它从"只看本类"扩成"走整张引用图"。

### 跨类闭包做好了,但它也不是原因(2026-09-14)

把闭包从"只看本类"改成"跟着引用的 **owner** 走"(第一版把引用一律当成落在本类,等于没跨类),现在会真的跨类补齐了 —— 实测 `closure added 1`,清单从 132 变成 133。

**但模型链的 null 依旧**,失败点一字未变。所以那些"校验不过"的方法体,**不是因为引用的成员缺失**:

- 它们引用的成员,要么在替换版本里本来就有,要么在运行时类里根本找不到(所以闭包无从可补);
- 那就意味着失败来自别处 —— 描述符/父类/字段可见性/调用点形态,或者方法体里用了 OptiFine 那份类**不具备的泛型签名与接口**。

**下一轮该做的是把这个判断换成证据**:拿一个具体的"校验不过"的方法(例如 `ModelBlockRenderer.tesselateBlock`),把**复制后的类**交给校验器(ASM 的 `CheckClassAdapter`,或直接 `ClassLoader.defineClass` 看 VerifyError 的详细信息),"到底是什么让它不过"就不再靠猜。

### 用校验器取证:真身其实能过,是我们的判据太严(2026-09-14)

新工具 `DonorVerifier` 把"替换版本 + 全部缺失成员(带原始方法体)"合成一个类,交给 ASM 的 `CheckClassAdapter` 校验,直接把"为什么不过"打出来。第一条结论就很关键:

```
### net/minecraft/client/renderer/block/LiquidBlockRenderer
  verifies clean with every body copied in          ← 全部复制进去是能过的!
### net/minecraft/client/renderer/block/ModelBlockRenderer
  ClassNotFoundException: net.minecraftforge.client.extensions.IForgeBakedModel   ← 只是校验器看不到我们的桩类
```

也就是说,`referencesOnlyExisting` 的判据(要求被引用的成员**已经存在于替换版本**)过于严格:实际运行时,那些成员**本来就会被计划补进去**(闭包补的),所以方法体能过。改成"对着**补完计划之后的类**判断"后,一批原本降级成桩的方法重新变成了真身。

### 新暴露的问题:补回来的**字段**没人初始化

失败点随即往前移了一格(这次是 `Initializing game`):

```
java.lang.NullPointerException: Cannot invoke "GuiLayerManager.initModdedLayers()" because "this.layerManager" is null
	at net.minecraft.client.gui.Gui.initModdedOverlays(Gui.java:1457)
	at net.neoforged.neoforge.client.ClientHooks.initClientHooks(ClientHooks.java:1015)
```

`layerManager` 是 NeoForge 给 `Gui` 加的字段 —— 我们**补了字段**(所以不再是 `NoSuchFieldError`),但**没人在构造函数里初始化它**。之前它是桩方法、根本不解引用,所以看不出来;换成真身之后立刻显形。

**下一步**:补字段时一并补"初始化" —— 在运行时类的构造器里找到给该字段 `putfield` 的那段指令,把它照搬进替换版本的构造器(而不是猜一个默认值)。这和"构造函数重载"是同一类问题的两个面。

### 补字段的初始化(2026-09-14)

按上一节的方案做了:供体里为每个补回来的**实例字段**多带一个静态方法

```
public static void optifineoforge$init$<字段>(<类> self)
```

它的方法体就是**从运行时类的构造器里原样搬出来的那段初始化指令**(例如 `self.layerManager = new GuiLayerManager()`),只搬"直线段"——遇到跳转/标签/switch 就放弃,涉及除 slot 0 以外局部变量的也放弃(否则搬过去就不成立),放弃时打印 `no safe initialiser for field ...`。

转型器把这批初始化方法一起放进类里,并在**每个构造函数的每个 `RETURN` 之前**插入调用。实测确实生效:

```
[OptifiNeoforge]: Initialised 1 restored fields in net/minecraft/client/gui/Gui
[OptifiNeoforge]: Initialised 1 restored fields in net/minecraft/client/gui/Font
[OptifiNeoforge]: Initialised 1 restored fields in net/minecraft/client/multiplayer/ClientLevel
[OptifiNeoforge]: Initialised 1 restored fields in net/minecraft/client/resources/model/ModelManager
```

**当前新问题(未解决)**:转型器在读取某个供体时抛异常(`MemberRestoreTransformer.donor:161`),把这次启动打断了 —— 需要看完整消息确认是哪个供体、以及是不是新加的初始化方法让那个类文件写坏了(例如被搬的指令段其实不完整)。

### 两个供体侧的修复:栈帧与 final 字段(2026-09-14)

**① 搬初始化指令时不能带上栈帧。** 把构造器里的初始化片段搬到供体的静态方法里时,原片段可能夹着 `FrameNode`;搬过去之后帧与新的方法上下文对不上,**写入时直接抛 `ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1`**,把整个启动打断。现在遇到 `FrameNode` 就当作片段边界(直线段本来不需要帧)。

顺带做了一件一直缺的事:**离线校验器**。`CheckDonors`(单文件 Java,用 ASM 把供体里每个方法单独写一遍)一次性指出是 `SimpleBakedModel` 的供体坏了 —— 这类问题本来要跑一次完整启动才能看到,现在离线几秒就能定位。

**② 补回来的字段不能带 `final`。** 初始化由我们的静态方法执行,而 JVM 规定**非静态 final 字段只能在本类的 `<init>` 里赋值**:

```
IllegalAccessError: Update to non-static final field ModelManager.modelBakery attempted from a different
  method (optifineoforge$init$modelBakery) than the initializer method <init>
```

补字段时去掉 `final` 即可(语义上仍是"构造期赋值一次")。

修完这两处,启动回到**同一个模型链阻塞点**,但路面已经明显不同:真身比例大幅提高(`Gui` 17 个成员 + 1 个字段初始化、`ModelManager` 5 个成员 + 1 个字段初始化、`ClientLevel` 10 个成员……),而且**不再有 VerifyError / IllegalAccessError / 供体读取失败**这类结构性错误。

### 两个真正的第一因:Forge 标签助手与 union 文件系统(2026-09-14 晚)

模型链的 null 一直不是第一因,下面两条才是 —— 两条都**不是**"缺成员",所以成员补全这一步永远看不到它们。

**① `ItemTags.create(String, String)`:Forge 的助手,NeoForge 没有。** 完整栈第一次读完才看清:

```
Failed to create mod instance. ModID: neoforge, class net.neoforged.neoforge.common.NeoForgeMod
Caused by: NullPointerException: Cannot invoke "TagKey.toString()" because "tag2" is null
  at TagConventionLogWarning.createForgeMapEntry(TagConventionLogWarning.java:557)
  at TagConventionLogWarning.<clinit>(TagConventionLogWarning.java:200)
```

`<clinit>` 第 200 行是 `Tags.Items.DYES_BLACK`,而它是 `DyeColor.BLACK.getTag()`。OptiFine 的 DyeColor 构造器里写着:

```
Reflector.ForgeItemTags_create.call("forge", "dyes/" + name)  ->  this.dyesTag
```

`ForgeItemTags_create` 是 `net.minecraft.tags.ItemTags.create(String, String)`,Forge 给 `ItemTags` 补的助手。NeoForge 的 `ItemTags` 只有 `create(ResourceLocation)`,反射找不到方法就返回 null,于是两个标签字段都是 null。NeoForge 自己把这个值当作约定标签,静态初始化直接 NPE,`neoforge` 这个 mod 构造失败,接下来就是上百行

```
Cowardly refusing to send event net.neoforged.neoforge.client.event.* to a broken mod state
```

NeoForge 的客户端事件全部不再派发(模型烘焙相关的事件也在其中),所以"模型是 null"是这条链的末端症状。

修法是**把方法补回去**,而不是改 OptiFine 的调用点(调用点可能不止一处,而反射只需要方法存在):新增 `TagHelperFix`,向 `net.minecraft.tags.ItemTags` 注入 `create(String, String)`,内部委托 NeoForge 已有的 `create(ResourceLocation)`;命名空间 `forge` 交给 `ConventionTags` 按版本映射(`1.21+` → `c`, `1.20.x` → `forge`),这样 OptiFine 拿到的标签与 NeoForge 自己的 `Tags` 完全一致。修复后 `Failed to create mod instance` 与 `Cowardly refusing` 全部消失,启动第一次越过 mod 构造。

**② `Path.toFile()`:在 union 文件系统上会抛。** 下一个栈是:

```
java.lang.UnsupportedOperationException: Path not associated with default file system.
  at java.nio.file.Path.toFile(Path.java:772)
  at net.optifine.util.ResUtils.collectFiles(ResUtils.java:104)
  at net.optifine.CustomItems.update(CustomItems.java:168)
  at net.optifine.util.TextureUtils.resourcesPreReload(TextureUtils.java:315)
```

`ResUtils.collectFiles` 按包类型分流:目录包走 `File` 遍历,zip 包走解压,而 `PathPackResources` 只做 `root.toFile()`。Forge 时代 mod 资源就是磁盘目录,这一句成立;NeoForge 的 mod 资源在 jar 的 union 文件系统里,这一句直接抛异常,而且发生在**初始资源重载**里,游戏在构造 `Minecraft` 时就死了。

修法:新增 `PackRootsFix`(只改调用点,保留 OptiFine 其余分流逻辑)与 `PackRoots.toFile`,依次尝试三条路 —— `path.toFile()`;从 union 字符串(`union:/.../mod.jar%23214!/assets`)还原出底层 jar 或目录并交给 OptiFine 的 zip 分支;都不行就把整棵子树复制到临时目录(退出时清理)。全失败返回 null,OptiFine 视为"这个包没有可用文件" —— 丢一个自定义物品目录远好过拒绝启动。全 jar 扫描确认 OptiFine 里只有两个类调用 `Path.toFile`:`ResUtils` 与 `OptiFineTransformationService`(后者的调用在重打包阶段已被 `OptifineJarFixer` 改写)。

修完这两条,rig 判定标记第一次走到 **`Sound engine started`**,资源重载跑完,崩溃点推进到渲染加载界面。

**新的第一因(随后修掉,见下一节):`ClientLanguage.componentStorage` 补回来了但没有值。** 计划为 `net/minecraft/client/resources/language/ClientLanguage` 补了字段 `componentStorage` 和两个方法(`appendFrom`、`getComponent`,都来自 NeoForge 客户端 jar 的供体),但 `Initialised ... restored fields` 里没有这个类:供体的赋值在 3 参构造器里是 `this.componentStorage = <第 3 个参数>`,片段读了局部变量(参数),按现有"只允许 slot 0"的规则被拒,字段保持 null,供体版 `getComponent` 一读就 NPE:

```
NullPointerException: Cannot invoke "java.util.Map.get(Object)" because "this.componentStorage" is null
  at ClientLanguage.getComponent(ClientLanguage.java:103)
```

值得记下的是 OptiFine 的 `ClientLanguage` 是 **1.21.1 形状**(只有 2 参构造器、`appendFrom(String, List, Map)`、`getLanguageData()`),既没有 `componentStorage` 也没有 `loadFromJson` —— 说明这两个是 NeoForge 补丁成员,数据源在 NeoForge 自己的装载路径里。顺带确认了"空 map 默认值"是安全的:NeoForge 的 `TranslatableContents.decompose` 拿到 null 就退回字符串查找(`getOrDefault`),并不会因此出错。

### 补字段的第三个来源:委托构造器里的默认值(2026-09-14 晚)

上面那条最后选了更小的做法:**值直接从"本类自己的短构造器"里取**。NeoForge 的 `ClientLanguage` 有两个构造器,3 参的那个 `this.componentStorage = <第 3 个参数>`,而 2 参的那个用 `Map.of()` 委托过去 —— 也就是说**当类里没有 3 参构造器时,NeoForge 自己认定的默认值就是 `Map.of()`**。

`MemberRestorePlan` 现在这样处理:赋值右边**正好是一个参数加载**时(其余情形仍走原来的"直线段搬迁"),去找同类的另一个构造器对同一个 `<init>` 的委托调用,把那个位置上的实参取出来当默认值。实参只接受"单条、不需要从栈上取东西"的指令(常量、`GETSTATIC`、`NEW`、局部变量加载、零参 `INVOKESTATIC`),否则放弃并保持原样 —— 宁可留空也不猜。对 `ClientLanguage` 生成的正是:

```
public static void optifineoforge$init$componentStorage(ClientLanguage self) {
    self.componentStorage = Map.of();
}
```

这里还有一个小坑值得记:**局部变量加载必须算作"压栈而不弹栈"**。第一版把 `ILOAD/ALOAD` 一律判为不合格,于是"先读参数再委托"的构造器整个被否掉,`Map.of()` 那一路根本没被看到 —— 表现为供体里就是没有初始化方法。查这种问题不需要启动游戏,`javap` 供体类文件一眼就能看到。

### 现状:游戏能启动并停在主循环(2026-09-14 晚)

补上这条之后 rig 的判定第一次是 **`STARTED`**(不是崩溃):`OpenAL initialized` → `Sound engine started`,贴图集逐个建成并伴随 OptiFine 自己的日志(**`[OptiFine] Animated sprites: 0`**、`[OptiFine] Scaled too small texture: minecraft:missingno`),进程持续运行到 rig 主动结束,并留下了截图。

首轮资源重载仍然报一次错,但**没有把游戏打掉**:

```
NullPointerException: BakedModel.getParticleIcon() ... BlockModelShaper.getBlockModel(BlockState) is null
  at LiquidBlockRenderer.setupSprites(LiquidBlockRenderer.java:43)
  at BlockRenderDispatcher.onResourceManagerReload(BlockRenderDispatcher.java:157)
  at ResourceManagerReloadListener.lambda$reload$0(ResourceManagerReloadListener.java:16)
```

随后是 `Caught error loading resourcepacks, removing all selected resourcepacks`,游戏自己重试第二轮重载,第二轮贴图集正常建完 —— 也就是说**模型确实被烘焙过,只是第一轮里 `BlockRenderDispatcher` 的应用阶段跑在了 `ModelManager.apply` 之前**(两边的 `missingModel` 都只在 `apply(ReloadState, ProfilerFiller)` 里赋值,`javap` 已确认),`getModel` 于是拿 `missingModel` 这个 null 当兜底返回。第二轮能好,说明问题出在**第一轮的监听器集合/顺序**上,而不是模型数据本身。

这条是下一轮的第一件事:把 `ReloadableResourceManager`(OptiFine 也替换了它,`ReloadableResourceManagerFix` 补的正是 NeoForge 的 `getListeners`/`updateListenersFrom`)在首轮重载时实际使用的监听器顺序打出来,与 `ModelManager`/`BlockRenderDispatcher` 的应有次序对照。

### 空模型的真因:烘焙出来的缺失模型本身就是 null(2026-09-14 深夜)

上一节那条猜测**被实测推翻**:顺序没问题,烘焙也确实跑了。为了看清这一点加了两个**默认关闭**的探针(打开方式:`-Doptifineoforge.debug.reload=true` 或环境变量 `OPTIFINEOF..._DEBUG_RELOAD=true`):

- `ReloadProbeFix`:在 `ReloadableResourceManager.createReload` 读 `listeners` 的地方插一条 `DUP` + 调用,把这次重载**将要按序执行的监听器清单**打出来;
- `ModelProbeFix`:在 `ModelManager.apply` 打 enter/leave(退出时反射读 `missingModel` 与 `blockStates`),在 `ModelBakery.bakeModels` 打进去时未烘焙模型、返回时烘焙结果,在 `BlockRenderDispatcher.onResourceManagerReload` 打 enter。

实测(1.21.4,`logs/run-probe7-1214`):

```
reload 1: 50 listeners
8   net.minecraft.client.resources.model.ModelManager          <- bakes the models
11  net.minecraft.client.renderer.block.BlockRenderDispatcher  <- asks for baked models
...
result ModelBakery (unbaked): missingModel=BlockModel
result ModelBakery.bakeModels: missingModel=null, blockStates=27870
enter BlockRenderDispatcher.onResourceManagerReload
```

三条结论:

1. **顺序正确**:`ModelManager` 在第 8 位、`BlockRenderDispatcher` 在第 11 位,`apply` 也真的执行了(而且把 27870 个方块状态装进了 `bakedBlockStateModels`)。所以"应用阶段跑在烘焙之前"不成立。
2. **`missingModel` 在烘焙那一刻就已经是 null**:同一批 worker 线程里,`ModelBakery` 自己的未烘焙缺失模型是 `BlockModel`(正常),但 `bakeModels` 返回的 `BakingResult.missingModel` 是 null;`ModelManager.apply` 只是把这个 null 原样搬进字段。于是 `getModel` 对**不在那 27870 项里的状态**(例如流体)返回 null —— `LiquidBlockRenderer.setupSprites` 一取 `getParticleIcon()` 就 NPE。
3. **监听器清单里每个原版监听器出现了两次**(0–21 与 24–45 是同一批,48/49 才是 OptiFine 的)。也就是 NeoForge 排序后的清单被**追加**到了原版清单后面,而不是替换。这本身就是要修的缺陷(每个原版监听器会跑两遍,烘焙也确实跑了两遍:两个 worker 各打了一次)。

顺带记一个**诊断陷阱**,它浪费了两次运行:`ITransformer.transform` 拿到的 `ClassNode.name` 是**斜杠内部名**,而 ModLauncher 的 `Target.targetClass` 用的是**点号名**。把两者混用会让探针静默失效(日志里 `probed 0`),看起来就像"这个类根本没被转换"。判据是同一行日志里打印的类名 —— 带斜杠的就是内部名。

下一步很明确:`bakeWithTopModelValues`(NeoForge 给 `UnbakedModel` 加的静态助手)会把调用转给 `IUnbakedModelExtension.bake(TextureSlots, ModelBaker, ModelState, boolean, boolean, ItemTransforms, ContextMap)`(七个参数、最后一个 `ContextMap`,由 `UnbakedModel` 继承的 NeoForge 扩展接口声明)。OptiFine **没有**替换 `BlockModel`(补丁清单里没有它的 xdelta),所以 null 是从 NeoForge 这条路上出来的 —— 要读的是 `IUnbakedModelExtension` 里那个默认实现的函数体(它不在 `neoforge-...-client.jar` 里,得去别的 NeoForge 产物里找),确认它在什么条件下返回 null。

### 空模型的真因找到了:被我们自己"打成桩"的方法(2026-09-14 深夜)

顺着上一节继续打探针,`BlockModel.bake`(七参、带 `ContextMap`)每一次返回都是 `null`:

```
value BlockModel.bake (...ContextMap;)Lnet/minecraft/client/resources/model/BakedModel;: null
value UnbakedModel.bakeWithTopModelValues (...): null
```

也就是说 null 不是"传丢了",而是从烘焙里出来的。`IUnbakedModelExtension`(在 `neoforge-...-universal.jar` 里)的默认实现只是老老实实转调六参 `UnbakedModel.bake`,NeoForge 的 `BlockModel.bake(七参)` 最后一行是 `SimpleBakedModel.bakeElements(...)` —— 于是问题落在这个**静态助手**上。

查供体就一目了然:

```
public static BakedModel bakeElements(...);
Code:
0: aconst_null
1: areturn
```

**它是被 `MemberRestorePlan` 打成桩的**:`referencesOnlyExisting` 判定"函数体引用了不存在的成员"时,会写一个返回默认值的桩,而桩对对象返回类型就是 `aconst_null`。这条路径一个模型都活不下来 —— 这既是空模型的真因,也是"补回来的成员看着对了、其实全是空壳"这类问题的样本。

**为什么会被判不合格?** 规则里有一条"super 调用只有在被复制进去的类的父类相同时才成立"(防止 `VerifyError: Bad invokespecial`)。但它的写法把**所有** `INVOKESPECIAL` 都当成 super 调用,包括 `new SimpleBakedModel$Builder(...)` 这种**构造别的类**的调用 —— 而 `SimpleBakedModel$Builder` 恰好被 OptiFine 替换过,于是这一句被误判,进而把整个 `bakeElements` 打成了桩。

修法只有一行语义:构造器调用不是 super 调用(`invokespecial` 但名字是 `<init>`,作用在刚分配出来的别的类的对象上,跟被复制进去的类的父类无关)。改完之后:

- 构建输出里 **`stub (body would not verify)` 一行都没有了**(此前有,只是没被我打印出来看);
- 供体里的 `SimpleBakedModel.bakeElements` 是真正的函数体(会 `new SimpleBakedModel$Builder(...)`、逐个 `bakeFace`、最后 `builder.build(...)`)。

这套"静默打桩"值得当作一条教训记下来:**桩是最后的兜底,但它的失败方式是静默的**,只有拿到具体症状(模型全空)才暴露。以后每次构建都该看一眼有哪些桩,而不是让它悄悄存在。

修完后旧症状消失(`BlockModelShaper.getBlockModel` 返回 null 的 NPE 不再出现、`Cowardly refusing ... broken mod state` 也没了),但紧接着换成一个**明确得多**的错误 —— OptiFine 替换过的 `SimpleBakedModel$Builder` 构造器里读了 Forge 类:

```
java.lang.NoSuchFieldError: Class net.minecraftforge.client.RenderTypeGroup does not have member
  field 'net.minecraftforge.client.RenderTypeGroup EMPTY'
  at net.minecraft.client.resources.model.SimpleBakedModel$Builder.<init>(SimpleBakedModel.java:215)
  at net.minecraft.client.resources.model.SimpleBakedModel.bakeElements(SimpleBakedModel.java:100)
```

我们为 Forge 类生成的**空壳**(`ForgeApiShims`)只有类名、没有成员,而 OptiFine 的代码要读 `RenderTypeGroup.EMPTY` 这个静态字段。这就是下一步:让 Forge 空壳带上 OptiFine 真正引用的成员(字段给常量/占位实例,方法给默认返回),而不是只有一个空类 —— 否则每修好一处就会撞上下一个。

另外记一句好消息:这一轮 OptiFine 的功能已经在跑 —— 日志里有 `[OptiFine] ConnectedTextures: optifine/ctm/default/00_glass_white/glass_white.properties`、`CustomItems: Registering sprites`、`BetterGrass: Parsing default configuration`,贴图集也在正常拼接。

### 三处连着的修复,模型终于真的烘焙出来了(2026-09-15 凌晨)

**① Forge 空壳不再是空壳。** `ForgeApiShims` 现在除了类名,还带上 OptiFine 的类**真正读写的成员**:扫 OptiFine jar 里每个 class 的常量池(ASM `visitFieldInsn`/`visitMethodInsn`),把 owner 是 `net/minecraftforge/**` 的字段与方法按签名收集起来,生成到对应空壳里 —— 字段给默认值(null/0/false),方法给默认返回;接口的实例方法保持抽象(谎称能答不如说答不了),引用到的构造器补一个只调 `super()` 的。

这里有个**关键的顺序问题**:需要成员的引用在**打完补丁的游戏类**里(例如 OptiFine 替换后的 `SimpleBakedModel$Builder` 读 `RenderTypeGroup.EMPTY`),而补丁输出是在重打包之后才有的。所以 rig 的构建脚本改成在 patch 步骤之后、用 `ForgeApiShims <输出目录> <patched jar> <OptiFine jar>` **重新生成**一遍空壳,并在合并时丢掉重打包阶段生成的那批空壳(否则会被"只添加不覆盖"的逻辑挡住)。实测:55 个 Forge 类型、54 个成员,`RenderTypeGroup` 现在有 `EMPTY`、`isEmpty()`、`block()`。

**② 构造器也要补。** 修完 ①,下一个错误是:

```
NoSuchMethodError: 'void SimpleBakedModel.<init>(List, Map, boolean, boolean, boolean,
  TextureAtlasSprite, ItemTransforms, net.neoforged.neoforge.client.RenderTypeGroup)'
```

这是 NeoForge 给 `SimpleBakedModel` 加的重载(第八个参数是 **NeoForge** 的 `RenderTypeGroup`),OptiFine 的替换版本只有 **Forge** 类型的那个同名重载;而我们补回来的 `SimpleBakedModel$Builder.build(...)` 正是要调 NeoForge 这个。计划里原先一句 `continue` 把所有 `<init>` 都跳过了("constructors are handled by the targeted fixes"),现在改成**构造器也进计划**,但有一条硬规则:**构造器不合格就不补,绝不打桩** —— 空构造器不会连 `super`,整个类都过不了验证,补一个坏的还不如不补(会打印 `constructor not restorable`)。计划成员数从 124 涨到 132(含这个构造器)。

顺带修了一个会**互相打架**的地方:补字段的初始化方法此前会被插进**每个**构造器的每个 `RETURN` 之前,包括刚补进来的那个构造器 —— 而那个构造器自己已经赋了值,初始化方法再把默认值写回去,等于把值抹掉。现在只对那些**没有自己赋值**的字段插初始化调用。

**结果(实测)**:模型链彻底通了 ——

```
result ModelBakery.bakeModels: missingModel=SimpleBakedModel, blockStates=27870
leave ModelManager.apply: missingModel=SimpleBakedModel, blockStates=27870
[OptiFine] Animated sprites: 2
Created: 1024x1024x4 minecraft:textures/atlas/blocks.png-atlas
Sound engine started
```

`missingModel` 是一个真的 `SimpleBakedModel`(不再是 null),方块贴图集 1024x1024 也建出来了,rig 判定 **`STARTED`**。

**新的第一因(未解决)**:贴图集拼接报错,游戏随后自行恢复:

```
net.minecraft.ReportedException: Stitching texture atlas
Caused by: java.lang.IllegalStateException: Image is not allocated.
```

这条大概率在 OptiFine 的自定义贴图/连接纹理路径上(`CustomItems`/`ConnectedTextures` 这一轮已经在跑),下一轮从这里开始。

### 贴图集上传失败的定位(2026-09-15 凌晨)

又加了一个探针(`NativeImageProbeFix`,默认关闭):在 `NativeImage.close` 打一条带**短栈**的 trace,并在 `NativeImage.upload` 打 enter。一次运行拿到 31402 次 `close`、40283 次 `upload`,按调用者分组:

```
  30634  net.minecraft.client.renderer.texture.SpriteContents.close
    230  SpriteContents$InterpolationData.close
    176  SpriteContents.rescale
    160  atlas.sources.LazyLoadedImage.release
```

再对 `SpriteContents.close` 的调用者分组,只有一个:

```
  30634  net.minecraft.client.renderer.texture.TextureAtlas.clearTextureData
```

完整栈长这样(一次实例):

```
at com.mojang.blaze3d.platform.NativeImage.close
at net.minecraft.client.renderer.texture.SpriteContents$InterpolationData.close
at net.minecraft.client.renderer.texture.SpriteContents$Ticker.close
at net.minecraft.client.renderer.texture.TextureAtlasSprite$1.close
at net.minecraft.client.renderer.texture.TextureAtlas.clearTextureData
at net.minecraft.client.renderer.texture.TextureAtlas.upload
at net.minecraft.client.resources.model.AtlasSet$StitchResult.upload
at net.minecraft.client.resources.model.ModelManager.apply
```

也就是说:**`TextureAtlas.upload` 在 `clearTextureData()` 里关掉的,正是它接下来要上传的那批精灵** —— `clearTextureData` 本意是释放**上一批**精灵的 ticker 与插值数据,这里却关到了新的那批,于是 `SpriteContents.uploadFirstFrame` 一上传就撞上 `Image is not allocated.`(30k 这个数量级也和方块贴图集的精灵数吻合)。

最可能的原因就是上一节记下的那个缺陷:**监听器清单里每个原版监听器出现了两次**。同一批精灵被两轮上传/清理交叉处理时,第二轮的 `clearTextureData` 关掉的正是第一轮刚建好的精灵。下一轮第一件事:查清这份清单为什么会重复 —— 需要对照 OptiFine 替换后的 `Minecraft`(Forge 时代那版)与 NeoForge 的注册顺序,以及 `ReloadableResourceManager.updateListenersFrom` 到底该替换还是追加。

### 重复清单的来源缩到 NeoForge 自己的排序里(2026-09-15 凌晨)

探针扩到 `registerReloadListener`(打印被注册的监听器)与 `updateListenersFrom`(替换前后各打印一次清单长度)。实测序列非常干净:

```
registerReloadListener: LanguageManager … PeriodicNotificationManager   ← 22 个原版监听器
size before updateListenersFrom: 22
size after  updateListenersFrom: 48      ← 确实是"替换",不是追加
registerReloadListener: (匿名类) ×2                                      ← OptiFine 的 TextureUtils$1/$2
reload 1: 50 listeners
```

于是先前的猜测被排除两条,也定位到真正的范围:

- **`updateListenersFrom` 是对的**(替换而非追加)。离线读供体字节码就是这个: `this.listeners = ReloadListenerSort.sort(event)` —— 供体是 NeoForge 自己的实现,一行不差。
- **`Minecraft` 没有被 OptiFine 替换**(补丁清单里没有它),所以那 22 次注册就是游戏自己的。
- **重复发生在排序结果内部**:替换后立刻就是 48 条,而 48 = 原版 22 + 2 个 NeoForge lambda + **原版 22 又一遍** + ObjLoader/AnimationLoader;最后 OptiFine 的 2 个匿名监听器是在替换之后再注册的,于是变成 50。

调用链也读清了:`ClientHooks.initClientHooks(Minecraft, ReloadableResourceManager)` 里 `new AddClientReloadListenersEvent(resourceManager)` → `ModLoader.postEvent(event)` → `resourceManager.updateListenersFrom(event)`,而这个事件的构造器就是把 `ReloadableResourceManager.getListeners()`(那 22 个)交给 `SortedReloadListenerEvent`。

也就是:**当 `getListeners()` 里已经有原版监听器时,NeoForge 的这条排序路径会把它们再算一遍**。下一轮要读的是 `SortedReloadListenerEvent` 的图/注册表构建与 `ReloadListenerSort.sortListeners`,确认原版那一组是从哪里第二次进来的,再决定修在我们这边(例如事件构造前让 `getListeners()` 只给出模组新增的那些)还是在替换类上补一个等价物。

### 排序内部:两个待验的机制(2026-09-15 凌晨)

上面两条路都读过了,结论是**它们都不可能凭空产生重复**,于是把范围压到两种机制之一,下一轮用一个探针就能分开:

- `SortedReloadListenerEvent` 的构造器(`neoforge-...-universal.jar`)对传进来的清单逐个调用
  `addListener(nameLookup.apply(listener), listener)`,而 `addListener` 往 `LinkedHashMap registry` 里放 —— **同一个 key 只会覆盖,不会重复**;
- `ReloadListenerSort.sortListeners` 最后是
  `TopologicalSort.topologicalSort(graph, comparingInt(order))`,输入是 guava 的 `MutableGraph`,而 guava 图是**节点的集合,重复节点会被合并**。

也就是说,要让排序结果里出现两遍原版监听器,只能是下面二者之一:

1. **两遍的 key 不同**(例如 `getNameForClass` 前后返回不同的名字),于是 `registry` 与图中同时留下两组、每组 22 个;
2. **NeoForge 内部还有一份原版监听器的来源**(例如 `VanillaClientListeners` 自己往图里注册了一遍),与游戏注册的那 22 个各自成组。

区分办法很直接:在 `updateListenersFrom` 返回处把结果清单**按对象身份**(`System.identityHashCode`)打出来 —— 如果两组 22 个的 identity 相同,就是机制 1;如果 identity 不同,就是机制 2(两组是不同实例)。这比继续读字节码快得多。

### 实测:是**同一个对象**出现了两次(2026-09-15 凌晨)

探针打上了 identity,结果很干净:

```
8   net.minecraft.client.resources.model.ModelManager  @2007522934   <- bakes the models
32  net.minecraft.client.resources.model.ModelManager  @2007522934   <- bakes the models
```

两组里对应位置的 identity **完全相同** —— 所以不是"两份不同的实例",而是**同一批对象被排进了结果两次**:机制 1 那一侧。50 条里只有 28 个不同的类名,也印证了这一点。

结合前面读到的两段实现(事件构造器用 `LinkedHashMap` 注册、`sortListeners` 走 guava 图),最合理的解释是:**结果 = 「按原版名字排列的那一组」前缀 + 「走拓扑排序得到的那一组」**,而后者本来也应该包含模组监听器;当事件构造时传进来的 `getListeners()` **已经含那 22 个原版监听器**时,它们就同时出现在这两部分里。

也就是说,问题可能不在"谁注册了两次",而在**事件构造的时刻**:这一个运行里 22 次注册发生在 `updateListenersFrom` 之前。下一轮要做一次**对照测量**:用同一套探针,在**只装 NeoForge、不打 OptiFine 补丁**的情况下跑一遍(需要一个只含 loader 与探针、不含 OptiFine 的 jar),看原版 NeoForge 在这个时刻 `getListeners()` 是不是空的、`updateListenersFrom` 前后各是多少 —— 这能直接判定"顺序被谁改了",而不必继续猜。

**顺序这条线索也断了。** 读 NeoForge 自己的 `Minecraft` 字节码,22 次 `registerReloadListener` 的偏移是 971–1916,而 `ClientHooks.initClientHooks` 在 **2192** —— 也就是说**原版 NeoForge 也是先注册、后建事件**,和我们看到的完全一样。所以"顺序被改动"不成立,重复是在**原版就会发生的那条路径**里产生的,差别只可能在**输入的内容**上:`VanillaClientListeners` 认得那些原版监听器类时才会把它们排除在拓扑排序之外,只有在它认不出时才会被排第二遍。于是对照测量仍然值得做,但要看的是"识别"而不是"顺序"。

## Control measurement 2026-09-15: the duplicate listener list is OptiFine's doing

The same probes, the same code, on the same profile, with only the loader jar in
mods/ and no OptiFine at all (built by build-loader-jar.ps1, launched with
-ModsFrom; note that -NoMods drops the probe jar too, which made the first
attempt a run with no instrumentation):

    register x22 vanilla listeners
    size before updateListenersFrom: 22
    size after  updateListenersFrom: 26      <- 22 + 2 NeoForge lambdas + ObjLoader + AnimationLoader
    reload 1: 26 listeners

With OptiFine installed the same three lines read 22, 22 and 48, and the reload
runs 50 listeners. Same input list, same code path, different result - so the
duplication is caused by OptiFine being present, not by NeoForge's ordinary
sorting, and the earlier question of who registers twice is answered: nobody
does. What differs is how the sort treats the listeners, and ReloadListenerSort
.sortListeners shows where that decision is made: for every entry in the registry
it calls needsToBeLinkedToVanilla(nameLookup, graph, listener) and adds an edge
when the answer is yes, then topologically sorts the graph. A listener whose name
cannot be resolved is therefore linked into the graph a second time, which is how
one object ends up in the reload twice.

The next measurement is that name resolution: ReloadProbe now prints
vanillaName=<resolved> per listener by calling VanillaClientListeners
.getNameForClass reflectively, and reports the failure reason instead of a bare
"?" - on the control run the call itself fails, so the first thing to fix is the
probe's own reflective lookup (wrong method signature or visibility), and then
compare the resolved names between the two runs.
## 2026-09-15: the name lookup resolves fine, so that is not the cause either

ReloadProbe now prints the vanilla name NeoForge's sort resolves for every
listener (asked reflectively through the listener's own class loader, because the
loader jar cannot see NeoForge's classes). Both runs, same probe:

    control, no OptiFine:  0 ClientNeoForgeMod$$Lambda vanillaName=null
                           1 BrandingControl$$Lambda  vanillaName=null
                           2 LanguageManager          vanillaName=minecraft:language_manager
                           ...  22 before updateListenersFrom, 26 after, reload 26
                           (4 nulls: the two NeoForge lambdas + ObjLoader + AnimationLoader)

    with OptiFine:         0 LanguageManager          vanillaName=minecraft:language_manager
                           1 TextureManager           vanillaName=minecraft:texture_manager
                           ...  22 before updateListenersFrom, 48 after, reload 50
                           (6 nulls)

So every vanilla listener resolves to the same name in both runs, and the
hypothesis that needsToBeLinkedToVanilla misjudges them because their name does
not resolve is wrong. The input to the sort is the same 22 entries, the lookups
agree, and yet the sorted result carries the vanilla block twice. A topological
sort over 24-26 nodes cannot produce 48 entries unless the graph it is handed is
shaped differently, and the graph is built in SortedReloadListenerEvent from the
passed list plus the dependencies added for it, with sortListeners linking each
registry entry to getLastVanillaListener() when needsToBeLinkedToVanilla says so.

So the next measurement is that anchor rather than the names: log what
getLastVanillaListener() returns (and the registry size) in both runs. With
OptiFine that answer is the one thing that can still differ, since everything else
in the path has now been shown to agree.
## 2026-09-15: identical inputs, different output - the difference is inside the sort

SortProbeFix logs what ReloadListenerSort.sort is handed, and the two runs agree
on every input:

    control, no OptiFine:  size before 22, registry 26, lastVanillaListener PeriodicNotificationManager, after 26
    with OptiFine:         size before 22, registry 26, lastVanillaListener PeriodicNotificationManager, after 48

Same registry (26 entries), same anchor class, same 22-entry list going in, and
the sorted result is 26 in one run and 48 in the other. A topological sort over
the same 26-node registry cannot return 48 entries, so the difference is not in
what the sort is given but in how it treats it: sortListeners asks
needsToBeLinkedToVanilla(nameLookup, graph, listener) per registry entry and edges
the graph when the answer is yes, and since the name lookups were already shown to
agree between the runs, the remaining variable is the graph - the dependencies
registered before the sort ran.

So the next probe belongs on the graph rather than on the listeners: node count,
edge count, and needsToBeLinkedToVanilla's answer per entry. Printing the anchor's
class was one step short of printing its identity, which should be included too -
the class matched, and only identity rules out a different instance of it.
## 2026-09-15: 26 nodes cannot sort into 48 entries

Both runs now have their sort inputs measured:

    control:   before 22, registry 26, anchor PeriodicNotificationManager, graph nodes=26 edges=23, after 26
    OptiFine:  before 22, registry 26, anchor PeriodicNotificationManager, (graph line missing), after 48

The control's graph has 26 nodes - exactly the registry - and sorts into 26
entries. With OptiFine the registry is the same 26 and the result is 48, so the
extra 22 entries are not extra nodes: a topological sort over 26 nodes cannot
return 48 of them unless the traversal appends a node more than once, which is
what a recursive implementation does when the graph contains a cycle.

So the next measurement is the shape that only OptiFine produces: whether
sortListeners' linking step creates a cycle (it asks
needsToBeLinkedToVanilla per registry entry and edges the entry to the anchor when
the answer is yes), and whether FML's TopologicalSort reports or silently walks
one. The graph line itself did not appear in the OptiFine run, so the probe needs
one more look there too - it printed on the control, so the insertion is sound and
something about that run skipped it.
## 2026-09-15: FML's sort is Kahn's algorithm, so it cannot emit 48 from 26

TopologicalSort.topologicalSort (loader-6.0.18.jar) computes in-degrees, queues
every node whose in-degree is zero, then repeatedly removes one from the queue,
adds it to the result, decrements its successors' in-degrees and enqueues each
successor that reaches zero - a Map.remove immediately after the Queue.add keeps a
node from being enqueued twice. Every node therefore lands in the result at most
once, and a cycle cannot be walked silently: the method declares
IllegalArgumentException and has a throwCyclePresentException helper for it.

So the previous round's reading is wrong as well: the 48 entries cannot be the
sort emitting nodes more than once. Either the sort is handed a graph with more
than 26 nodes, or the 48 entries are not the sort's return value at all. The next
probe separates those two by logging the size of the list sort() itself returns,
inside sort(), and by getting the graph numbers out of the OptiFine run - that
line printed on the control and not there, which is itself unexplained and needs
to be fixed before its absence is read as anything.
## 2026-09-15: with OptiFine, two probes inside sort() print nothing while the others do

SortProbeFix now inserts three calls at the head of ReloadListenerSort.sort - the
anchor, the registry size, the graph shape - and one before its return, the size of
the list it hands back. On the control run all four print. On the OptiFine run only
the first two do:

    OptiFine run:  value lastVanillaListener: PeriodicNotificationManager
                   count registry: 26
                   (no graph line, no sort-result line)
                   size before updateListenersFrom: 22
                   size after updateListenersFrom: 48

The graph probe logs on both its success and its failure path, and the sort-result
probe cannot throw on a list, so neither can be silent by accident: with OptiFine
present those two insertions are not running at all, while two insertions in the
same instruction list, in the same method, do run. That is now the most informative
thing in the picture and it has to be explained before anything else is inferred
from it - including whether the 48-entry result really comes from this method.

A plausible direction, not yet checked: the class may be loaded and transformed
twice in that run (once early, before all of this service's transformers are in
place, once later), leaving the running copy with only part of the insertions. That
is testable cheaply by logging which insertions each transformed copy received
(the transform method already logs the class it saw), instead of guessing from the
absence of output.
## 2026-09-15: 22 + 26 = 48 - the running updateListenersFrom appends

The four probes inside ReloadListenerSort.sort all print once the combined jar is
rebuilt (they had been missing because that jar predated them - worth remembering
before reading silence as evidence):

    with OptiFine:  graph before sorting: nodes=26, edges=23
                    list sort result: 26 of UnmodifiableRandomAccessList
                    size after updateListenersFrom: 48

The graph is identical to the control's and the sort returns the same 26 entries.
The difference appears inside updateListenersFrom itself: the list goes from 22 to
48, which is 22 + 26 - the sorted result appended to the list that was already
there, rather than replacing it. The donor body this project plans for that method
is NeoForge's own, "this.listeners = sort(event)", which replaces.

The likely reason the donor body is not the one running: MemberRestoreTransformer
copies a donor member only when the class does not already have it, and OptiFine's
replacement of ReloadableResourceManager appears to bring its own
updateListenersFrom - so the copy is skipped and the appending variant survives.

That closes the chain measured over the last rounds: an appended list holds every
vanilla listener twice, the same sprites are then loaded and cleared in two
interleaved passes, and TextureAtlas.clearTextureData closes the sprites the same
upload call is about to upload, which is the "Image is not allocated" failure.

The fix to make next: restore a planned member by replacing an existing body of the
same name and descriptor, not only adding it when absent.
## 2026-09-15: two fixes, and the first clean resource reload

Fix 1 - ReloadableResourceManagerFix wrote updateListenersFrom with addAll, which
appended the sorted list to the listeners NeoForge had already registered (22 -> 48)
instead of replacing them. Clearing first measures 22 before, 26 returned by the
sort, 26 after, and the reload runs 28 listeners instead of 50. The reason the
method could not simply assign the field is real and stays: OptiFine's copy declares
it final, so the contents are replaced rather than the field assigned.

Fix 2 - the Forge shell for net.minecraftforge.client.RenderTypeGroup exposed EMPTY
as null, and OptiFine calls isEmpty() on it. ForgeApiShims now gives every static
field whose type is its own class an instance in <clinit> (that is what such a
constant is), and a shell that is asked isEmpty() records which constructor produced
it: the no-argument one means it stands for the empty constant, any other means a
group OptiFine built, so isEmpty() answers correctly instead of guessing.

Measured on the 1.21.4 rig after both fixes: reload 1 runs 28 listeners, the
1024x1024 block atlas is created, Sound engine started, and the log holds no
"Caught error loading resourcepacks", no NullPointerException, no "Failed to wait"
and no "Image is not allocated" - the atlas failure that started this whole line of
investigation is gone.

## 2026-09-15:1.20.4 基线能启动,以及两侧命名空间的字节码级实测

安装器这次第 1 轮就完成,三个派生 jar(`-srg` / `-slim` / `-extra`)与 `neoforge-20.4.251-client.jar`
都已生成。基线启动:

    launch-neoforge.ps1 -Profile neoforge-20.4.251 -NoMods -JavaExe "<jdk-17>\bin\java.exe"
    → VERDICT: STARTED (40s, marker: Sound engine started)
      ModLauncher 10.0.9+10.0.9+main.dcd20f30 / Java 17.0.15
      NeoForge mod loading, version 20.4.251, for MC 1.20.4 with MCP 20240627.114801

也就是说 1.20.4 这一行的**实例本身没有问题**,后面再出问题都是 mod 的问题。

命名空间这一次量的是常量池,不是目录名。对 `srg/net/optifine/Config.class` 取 `javap -v -p` 输出,
按常量池条目类型给 `m_\d{4,6}_` / `f_\d{4,6}_` 分类:

| 构建 | `Methodref`/`Fieldref` | `String` |
|---|---|---|
| 1.20.1 I6 | 49 | 0 |
| 1.20.4 I7 | 47 | 0 |
| 1.21.4 J3 | 0 | 0 |

带 SRG 名的是**引用**不是字符串,所以 1.20.4 的类确实是照 SRG 命名的 Minecraft 编译的,
不是"自带一张对照表"。对面 NeoForge 20.4 的运行时取

    javap -p client-1.20.4-20240627.114801-srg.jar 里的 net.minecraft.world.item.Item
    → getId / byId / builtInRegistryHolder / onUseTick / BY_BLOCK / MAX_STACK_SIZE

**全是官方名,一个 `m_` 都没有**;文件名里的 `srg` 只是安装器那一步留下的名字,不代表内容。

顺带否证了一条捷径:想用 `patch/srg/*.class.md5` 反推"补丁是针对哪个 jar 打的",427 个 md5 对
NeoForm 官方 jar、vanilla 混淆 jar、slim、extra、`neoforge-*-client.jar`、`neoforge-*-universal.jar`
全部 0 命中,`patch/notch` 对 vanilla 混淆 jar 也是 0/426 —— **这些 md5 不是输入的校验和**,不能用它判定命名空间。

NeoForm 1.20.4 自带的两份映射也查了:`-mappings.txt` 是 `tsrg2 obf srg id` 但 srg 列与 obf 列**逐字相同**
(`cuz cuz 1657`)且全文无 `m_` 名,`-mappings-merged.txt` 是 `tsrg2 left right` = obf→官方名。
所以重映射要用的 SRG↔official 表**不在 NeoForm 里**,来源问题见 `docs/MATRIX.md` 末节。

## 2026-09-15:SRG→official 表建成并全量验证通过(3844/3844)

新增离线工具 `SrgMemberMap`,输入 MCPConfig 的 `config/joined.tsrg`(`tsrg2 obf srg id`)与 NeoForm 的
`-mappings-merged.txt`(`tsrg2 obf official`),以**混淆名**为连接键合成 SRG→official 成员表:

    table: 5742 owners with fields, 7428 with methods (35232 + 65393 names);
           0 classes and 4 members had no counterpart

连接率是 0 缺口,说明两份映射覆盖同一批混淆名。然后用它去改写 OptiFine 1.20.4 **全部**类里的每一个
SRG 引用,再逐个问 NeoForge 20.4 运行时是否真有这个成员:

    runtime index: 69571 methods, 35583 fields, 7794 classes with a superclass
    SRG references: 3844
    resolved:       3844 (100.00%)  = 3248 on the referenced class + 596 through a superclass
    unresolved:     0
    wrote 1330 rewrite pairs to build-tools/srg1204.tsv (86412 bytes)

**100% 命中,而且只需要 1330 条改写对 / 86 KB** —— 完整表有 10 万个名字,真正被引用的只有千余条,
所以这张表可以随 jar 发出去,不必在用户机器上生成。

过程中踩到两个坑,都记下来,因为它们都是"看起来对"的错误:

1. **连接键必须带描述符。** 混淆方法名只在"名字 + 描述符"上唯一,一个类里两个重载都叫 `a` 时,
   只按名字连接就会互相覆盖。表现是 `m_118316_`(实际是 `getSprite`)被解析成了 `dumpContents`,
   于是 841 条引用报"宿主类在、成员不在"。两份文件的描述符都在混淆命名空间(`(Lahg;)Lgen;`),
   可以直接比较。
2. **引用的 owner 不一定是声明类。** `invokevirtual BlockState.m_60734_` 引用一个声明在父类里的成员
   是合法的,而表是按声明类建的,所以查不到时要沿继承链上溯。修好后正好补上那 596 条
   (先前全部计入"表里没有这个条目")。

验证器里的继承链上溯只是**测量**用的;真正发给 loader 的表按**引用里写的 owner** 建键
(改写只改成员名,不动 owner),这样 loader 侧不需要类层次、也就不会提前加载任何类。

compile/run 方式(ASM 9.8 取自 Gradle 缓存,用 jdk-21):

    javac -cp asm-9.8.jar;asm-tree-9.8.jar -d build-tools/srgmap src/main/java/.../SrgMemberMap.java
    java -cp "build-tools/srgmap;<asm>" kynarain.cn.optifineoforge.optifine.SrgMemberMap \
      --emit build-tools/srg1204.tsv <mcp1204-joined.tsrg> <...-mappings-merged.txt> \
      OptiFine_1.20.4_HD_U_I7.jar client-1.20.4-...-srg.jar neoforge-20.4.251-client.jar

**下一步**:把 emit 接进 `OptifinePipeline`(打补丁之后跑,因为补丁产物 `srg/net/minecraft/**` 自己也带
SRG 引用),再在 loader 的 transformer 里用 ASM `Remapper` 按这张表改写成员名。1.20.2 用
`mcp_config-1.20.2.zip` 同样生成一份(已确认该坐标存在,HTTP 200)。

## 2026-09-15:改写器跑完,1.20.4 载荷里的 SRG 名归零

上面那句"在 loader 的 transformer 里改写"是**错的方案,已放弃**。这个项目本来就在离线阶段重打包
OptiFine 的 jar、离线跑 `optifine.Patcher`,需要改名的类在游戏启动前就全都在手上;而 ModLauncher 的
transformer 必须**预先声明 targets**,需要改的类有几千个,声明不现实。所以改名做成**构建步骤**
(`SrgRemap`),loader 侧一行都不用改,而且对每条线都能同样地跑(1.20.6 起的线找不到 SRG 名,原样复制)。

用 `ClassRemapper` 而不是手改引用,原因是**载荷里的 SRG 名同时出现在声明和引用两处**:

    before: SRG references: 3844        SRG-named members the payload still declares: 108
    after : SRG references: 0           SRG-named members the payload still declares: 0
    rewrote 3457 method and 1400 field names, 0 could not be resolved
    0 SRG-shaped string constants were left alone

那 108 个声明正是只改引用会漏掉的东西:OptiFine 替换掉的游戏类**自己声明**成员,`net.optifine.BlockPosM`
里就有 `m_123341_()I` 覆盖 `Vec3i.getX()`。只把调用点改掉而留着 SRG 的声明,等于把"覆盖"悄悄变成"另一个
方法",编译得通、运行不报错、行为错。`ClassRemapper` 对声明和引用走同一个方法,所以两边必然一致。

**踩到的坑:同一个类名在 jar 里有两份。** OptiFine 的 jar 给自家每个类都存了两份 —— `srg/**` 对着重命名
过的游戏,`notch/**` 对着混淆过的游戏。第一版只按类名建超类表,于是两份互相覆盖,`notch/` 那份赢了:

    superOf(net/optifine/BlockPosM) = hx        ← 混淆名,继承链到此断掉
    hierarchy(net/optifine/BlockPosM) = [net/optifine/BlockPosM, hx]

结果是 371 个名字报"表里没有这个条目",而表里其实都有 —— `BlockPosM extends net.minecraft.core.BlockPos`
这一跳根本没走到 `Vec3i`。`SrgRemap.isUnusedNamespace` 现在显式跳过 `notch/**`,这一条也写进了注释,
因为"同一份数据在 jar 里有第二份、而且会悄悄赢"不是能从代码看出来的事。

**还剩什么**:把 `SrgRemap` 接进 rig 的构建流程(在丢掉 `notch/` 之后、合并之前跑),用 1.20.4 打出
合并 jar 并实机启动。改名本身已经验证完毕,不再是未知数。

同一天补的一个保险:改写器现在会先数**运行时**里还有多少成员是 SRG 名,大于 0 就拒绝改写并退出 2。
理由是这条命令迟早会被顺手用在 1.20.1 那一行上,而那一行两边本来就都是 SRG 名,改写会把每个成员改成
那一行的游戏根本没有的名字 —— 报错会出现在很久之后,和原因完全不像。实测:

    输入 OptiFine_1.20.1_HD_U_I6.jar + 1.20.1 运行时(client-1.20.1-...-srg.jar + forge-1.20.1-47.1.106-client.jar)
    → refusing to rewrite: this runtime is SRG-named (49667 members match m_/f_)
    → 退出码 2,没有产生输出 jar

这个数字顺便从另一个方向印证了整条结论:1.20.1 运行时里有 **49667** 个 SRG 名成员,1.20.4 运行时里是
**0** 个。所谓"1.20.2/1.20.4 这一带两边不同构",在两个方向上都是量出来的。

## 2026-09-15:打补丁要的是**混淆**jar,不是 SRG jar(修正上一条推断)

把 1.20.4 接进 rig 时第一步就停住:

    OptiFine's patcher failed on client-1.20.4-20240627.114801-srg.jar
    Caused by: java.io.IOException: Base resource not found: eon.class
        at optifine.Patcher.applyPatch(Patcher.java:148)

`eon` 是**混淆名**(`eon` = `com/mojang/blaze3d/pipeline/RenderTarget`),而给进去的是官方名的 NeoForm jar,
所以它找不到 base。之前那句"补丁需要该版本的 SRG 命名 jar"是**错的**:OptiFine 的 `Patcher.applyPatch`
用 `getPatchBase(...)` 把补丁条目名换算成**混淆 base 名**,再在输入 jar 里找 —— 它要的是**原版混淆 jar**,
也就是启动器 `versions/1.20.4/1.20.4.jar` 那一份(OptiFine 自己的安装器就是往版本 jar 里打补丁)。

把输入换成原版混淆 jar 之后,同一个补丁步骤立刻通过:

    patched in 1271 ms -> optifine-patched.jar (6,724,767 bytes)
    roots: {=9, META-INF/=3, assets/=1787, doc/=39, notch/=1137, optifine/=49, srg/=1070}
    patched game classes: 427 (369 net/minecraft)

顺带说明 1.21.4 那一行之前为什么没暴露这个问题:`test-downloads/1.21.4-client.jar` 其实是**原版混淆 jar**
(5723 个两字母根类名,`net/minecraft/*` 只有 29 条),一直就是对的输入。**两边一致,不是特例。**

## 2026-09-15:补丁产物也是 SRG 名,所以改名要跑在补丁之后

`optifine-patched.jar` 里 `srg/com/mojang/blaze3d/pipeline/RenderTarget.class` 的成员是
`f_166194_`、`f_83915_`、`f_83919_` —— **补丁产物(427 个游戏类)是 SRG 名**,和 OptiFine 自家类一样。
所以 `SrgRemap` 必须作用在**补丁产物**上,而且这是主战场:

    rewrote 21089 method and 17455 field names, 107 could not be resolved
    0 SRG-shaped string constants were left alone

对比只改 OptiFine 自家类时的 3457 + 1400,补丁类贡献了 **85% 的改名量**。这也是个顺序约束:
`MemberRestorePlan` 是拿补丁产物和 NeoForge 官方名运行时比的,所以**改名必须排在计划之前**,否则整个计划
都是拿 SRG 名去比官方名,结论没有意义。

剩下 107 个(38544 里的 0.28%)已经查到具体原因,不是表的问题:

    candidate 顺序 net/minecraft/core/BlockPos$MutableBlockPos -> below
                  net/minecraft/core/BlockPos                 -> below
                  net/minecraft/core/Vec3i                    -> below

同一个 SRG 名会被**协变覆写**的多个声明共用(`Vec3i`/`BlockPos`/`MutableBlockPos` 的 `below()` 都叫
`m_7495_`,只是 id 不同),所以"名字在表里、但该类并不声明它"不是终点,必须继续往上走一层。修掉之后
那 49 条"成员形状变了"里还剩 48 条属于另一类,见下。

**另一类(字段族)是类名不一致**:Mojang 的映射把局部 record 叫 `net/minecraft/client/gui/Gui$1DisplayEntry`
(`$1` 前缀),而 OptiFine 打出来的补丁类是 `Gui$DisplayEntry`。对运行时验一下:

    Gui$1DisplayEntry.class            在运行时: 有
    Gui$DisplayEntry.class             在运行时: 无
    MultiBufferSource$BufferSource     在运行时: 有
    MultiBufferSource$1BufferSource    在运行时: 无

两边各有各的叫法,不是单向偏差。规模也不大:369 个补丁过的 `net/minecraft` 类里,只有 **5 个**的名字
运行时没有 —— `Util$3`、`Util$4`、`Gui$DisplayEntry`、`ParticleEngine$ParticleDefinition`、
`LevelChunkSection$BlockCounter`。

**下一步**:架一座类名桥 —— 补丁条目名(`patch/srg/<OptiFine 的名字>.class.xdelta`)与它换算出的混淆 base
名是一对,把它和 merged 的混淆→官方名串起来,就得到"OptiFine 名 → 运行时名";用它修掉那 5 个类、并让
表按运行时的类名查。然后就能接着跑 `MemberRestorePlan`、Forge shims、loader 并实机启动。

rig 那边同时修了一处:`build-rig-jar.ps1` 原来手写四个 ASM jar(9.8),没有 `asm-commons`,于是
`ClassRemapper`/`Remapper` 直接编译不过;现在按模块名解析,自动带上 `asm-commons`,版本也跟
`gradle.properties` 的 `asm_version=9.10.1` 一致。

## 2026-09-15:1.20.4 第一次实机启动 —— 起来了,而且是"OptiFine 在场"起来的

rig 现在能在 1.20.4 上跑完整条构建链(新增第 3b 步:打完补丁、做计划**之前**先跑 `SrgRemap`),产出

    mods-stage-1204/optifiNeoforge-combined.jar  5,868,899 bytes, 5940 entries, 24 donors, 48 shims

构建各步的输出:

    patched in 1223 ms -> optifine-patched.jar (6,724,767 bytes)
    patched game classes: 427 (369 net/minecraft)
    rewrote 21471 method and 17455 field names, 107 could not be resolved
    compared 235 replaced classes (835 without a runtime counterpart), 52 members to restore
    48 referenced Forge types in 16 packages, 44 members named on them  -> 48 stubs

`48 referenced Forge types` 说明这一行**需要** Forge shims(与 1.20.1 相反 —— 那一行的 NeoForge 自带
Forge API,所以 `-ForgeStubs $false`;20.4 的运行时里 `net/minecraftforge` 是 0 个)。

启动结果:

    VERDICT: STARTED (40s, marker: Sound engine started)
      OptiFineTransformationService.onLoad / OptiFine ZIP file: mods/optifiNeoforge-combined.jar
      OptiFineTransformer: Targets: 427
      OptifiNeoforgeTransformationService.onLoad, alongside [mixin, OptiFine, mixin-synthetic-package, fml, OptifiNeoforge]
      Member restore plan: 52 members across 24 classes
      Initialised 1 restored fields in net/minecraft/client/multiplayer/ClientLevel
      Restored 1 members in net/minecraft/client/multiplayer/ClientLevel from its donor
      NeoForge mod loading, version 20.4.251, for MC 1.20.4

**但 stderr 有 12575 行异常,而且原因正是这一行预言过的那个**:

    java.io.IOException: Base resource not found: fcn.class
        at optifine.Patcher.applyPatch(Patcher.java:148)
        at optifine.OptiFineTransformer.getOptiFineResourcePatched(OptiFineTransformer.java:441)
        at optifine.OptiFineTransformer.transform(OptiFineTransformer.java:195)
    (fcn、gdy、fcn$a、geg、eqf、ewu ... 一个类一条,直到 12575 行)

`fcn` 是**混淆名**。也就是说 OptiFine 的**运行时** transformer 也在按"混淆 base 名"找资源,而 NeoForge
的运行时给它的类是**官方名**的 —— 和离线打补丁时那个 `Base resource not found: eon.class` 是同一个原因,
只是这次发生在类的加载路径上。异常被 ModLauncher 逐类吞掉,原类照用,所以游戏能起来,但**OptiFine 的
补丁实际没有生效**:它只是被加载了,没有被织进去。

这一步把 1.20.2/1.20.4 剩下的设计问题问清楚了:**不能再指望 OptiFine 的运行时 transformer 去打补丁**。
补丁必须由我们在离线阶段打完、改名完,然后把**结果类**放进 jar,让 `OptiFineTransformer` 直接取到成品
(它只有在存在 `patch/srg` 条目时才会去 `getOptiFineResourcePatched`,所以接下来要验的是:把
`patch/srg/**` 从发布 jar 里去掉、把 `srg/<official path>` 换成我们改好名的成品类,它是否会直接返回成品)。
这也正是前面几轮把离线改名做扎实的用处 —— 那条路现在成了唯一可走的路。

另外记两处 rig 的坑,都曾把"工具写 stderr"变成"看起来像编译失败":
`javac` 的 deprecation note 和 `git worktree add` 的进度输出都会让 `$ErrorActionPreference='Stop'` 直接
中止构建;现在 rig 用 `Continue` + 显式检查 `$LASTEXITCODE`,并且 `SrgRemap` 改用 `Remapper(Opcodes.ASM9)`
构造器(无参构造在 ASM 9.10 里已弃用)。`-Out` 的父目录现在也会自动创建 —— 缺目录以前表现为 zip 写入器
深处的 NullReferenceException,而不是"目录不存在"。

## 2026-09-15:把成品类装进 jar —— 异常风暴消失,但撞上模块包冲突

按上一节的结论做:构建出"成品载荷" = 打完补丁并改好名的类(`srg/` 1070 个,含 427 个游戏类),去掉
`notch/**`,作为组合 jar 的底。这一步**确实解决了运行时打补丁的问题**:

    改名前(第 68 轮那种装法): stderr 12575 行,Base resource not found 逐类刷屏
    成品载荷装法:              stderr 8 行,  Base resource not found = 0

也就是说:OptiFine 的运行时 transformer 不再去打那些打不上的补丁,游戏里用的就是我方改好名的类。

但启动换成了另一个失败,而且分两步才看清:

1. `NoSuchFileException: ...optifiNeoforge-combined.jar#177` → `Invalid Services found OptiFine`。
   读出 `OptiFineTransformationService.onLoad` 的字节码,第 103–110 行是:
   `ofZipFileUrl = codeSource.getLocation()` → 日志打印 URL → `ofZipFileUrl.getPath()` → 去掉 `file:` →
   `toFile(URI)` → `new File(...)` → **第 109 行 `new ZipFile(path)`**。URL 是
   `union:/.../jar%23177!/`,URI 解码后变成 `...jar#177`,ZipFile 自然打不开。
   这正是 `OptifineJarFixer` 存在的原因(它把 `toFile` 改成先剪掉 `%23N` 和后缀 `!/`);第 68 轮的底是
   **重打包过的** jar,`OptifineJar` 已经把那个类修好了,而我这次的底来自**打过补丁的** jar,里面那份
   `optifine/OptiFineTransformationService.class` 是**原版**的。把修好的那份从重打包 jar 里覆盖回去之后:

       OptiFine ZIP file: C:\...\optifiNeoforge-combined.jar     ← onLoad 过了

2. 紧接着,类加载层起不来:

       java.lang.module.ResolutionException: Modules srg and minecraft export package
       net.minecraft.client.renderer.block to module mixinsynthetic

   把**游戏类**放进 mod jar,我方模块就宣称导出 `net.minecraft.*` 这些包,而 `minecraft` 模块已经导出了
   它们,模块解析器直接拒绝。这和 1.20.1 那次 `Module optifine contains package
   net.minecraftforge.eventbus.api` 是同一类问题:jar 的模块不能重复导出游戏包。

**所以"把成品类平铺进 jar"这条路走不通**,但第 1 条已经证明另一件事:只要类能被加载,**补丁不再需要
OptiFine 的运行时 transformer**。下一步据此调整:成品类改放在**不会与游戏包冲突的路径**下(例如
`optifineoforge/patched/net/minecraft/...`),由**我方 loader 的 transformer** 在游戏类加载时把内容换成
成品(OptiFine 替换类的那套机制本来就在这个项目里),这样既避开模块冲突,又不再依赖它自己的 patch 路径。

顺带一个否定结果:试过 `-KeepPatchData` 想让"保留 patch 数据"和"不保留"做对比,结果两次产出**字节数完全
相同** —— 因为 `Patcher.process` 的输出 jar 里**根本没有 `patch/` 目录**(它的 roots 只有
`assets, doc, notch, optifine, srg`)。这个开关量不到任何东西,已在 rig 注释里写明。

## 2026-09-15:成品类改放安全路径 + 我方 transformer 换装 —— 补丁终于真的生效了

按上一节的结论换了装法:成品类不再平铺在 `srg/` 下,而是放到 `optifineoforge/patched/`
(**427 个**),`srg/` 只留 OptiFine 自己的类(643 个),另写一份 `optifineoforge/patched-index.txt`
(因为 ModLauncher 的 transformer 必须**先**声明 targets)。新增 `PatchedClassTransformer`(在 `loader`
包里,注册在**最前**,这样后面的 `MemberRestoreTransformer` 是在换装后的类上补 NeoForge 的成员):

    Patched-class targets: 427
    Replaced net.minecraft.client.gui.GuiGraphics with OptiFine's patched version (10 fields, 84 methods)
    Replaced net.minecraft.client.renderer.RenderType with OptiFine's patched version (68 fields, 114 methods)
    ... 实际换装 112 个类,stderr 0 行,OptiFineTransformer: Targets: 0(它自己不再需要打补丁)

**两个坑,都是"失败被静默"的类型:**

1. 索引最初写成**带 `.class` 的条目路径**,而 `Target.targetClass` 要的是类名,于是没有任何 target 匹配,
   `transform()` **一次都没被调用**,日志里也看不出异常("Replaced" 0 条)。现在索引写裸类名,并且
   读取端也兼容 `.class` 后缀;索引缺失时改成打一条 info(以前是静默 `Set.of()`)。
2. `SrgRemap` 会造出**重复方法**。实测 `ModelPart`:

       改名前: getChild(String) 出现 1 次(另有 getChildModelDeep/getChildDeep)
       改名后: getChild(String) 出现 2 次   → ClassFormatError: Duplicate method name "getChild"

   原因是 OptiFine 的载荷是**混的**:一部分成员已经是运行时的名字,另一部分还是 SRG 名,于是改名可能落到
   类里已经存在的名字上。现在改写器会检查"目标名字 + 描述符是否已被本类声明",是则**拒绝这次改名**并计数:

       rewrote 21435 method and 17449 field names, 107 could not be resolved,
       42 renames refused because the class already has that name

**接着撞上的是新问题,而且只有补丁真的生效了才会遇到**:

    java.lang.IllegalAccessError: class net.optifine.config.SliderableValueSetInt cannot access its
    superinterface net.minecraft.client.OptionInstance$SliderableValueSet
    (net.optifine.config.SliderableValueSetInt is in module srg ...;
     net.minecraft.client.OptionInstance$SliderableValueSet is in module minecraft@1.20.4 ...)

OptiFine 的类实现了游戏里的接口,而两者分属**不同模块**:我们的 jar 是 `srg` 模块,游戏是 `minecraft`
模块。之前几轮碰不到它,是因为那时 OptiFine 的补丁根本没生效(第 68 轮反倒是"干净启动"),这条路径
(OptiFine 的 `OptionInstance` 补丁代码在 `Options.<init>` 里被调用)以前从不执行。

### 这一轮把可能的原因逐个排掉了,只剩模块导出这一条

- **不是"我们的 jar 没被 FML 当成 mod"**:实测三条线的日志里我们的 jar 都不在
  `Found mod file` 列表里 —— 1.21.4 那条**已验证可用**的线也一样(它只有 2 条 `Found mod file`,
  都是游戏 jar 和 neoforge universal),而它的 `OptiFineTransformer: Targets: 474` 正常工作。
  所以"不在 mod 列表"是本项目的常态,不是这次的病因。
- **不是 manifest**:原版 OptiFine 1.20.4 与我们打出来的 jar(1.20.4 与 1.21.4 两份)的
  `META-INF/MANIFEST.MF` 都是 `Automatic-Module-Name: optifine`。**但报错里的模块名是 `srg`**,
  而 `srg` 正好是 jar 里那个 union 根目录的名字 —— SecureJar 把 `srg/` 当成包根,模块名随之而来。
- **不是残留的旧 jar**:`game1204/mods/` 里只有我们那一个 jar。
- **不是 1.20.4 独有**:四个 OptiFine 构建(1.20.1 / 1.20.4 / 1.21.1 / 1.21.4)都带
  `srg/net/optifine/config/SliderableValueSetInt.class`,而且**都带
  `patch/srg/net/minecraft/client/OptionInstance$SliderableValueSet.class.xdelta`** —— 也就是说
  OptiFine 连这个接口自己都要打补丁。差别只在于 1.20.4 这条线现在真的把补丁放进去了,于是这条路径
  第一次被执行到。
- **ModLauncher 的公开 API 给不出导出**:`IModuleLayerManager` 只有 `getLayer(Layer)`,
  `ModuleLayerHandler` 有 `updateLayer(Layer, Consumer<LayerInfo>)`,但
  `ModuleLayerHandler$LayerInfo` 是个包私有 record,字段只有 `layer` 和 `cl` —— **没有
  `ModuleLayer.Controller`**,所以拿不到 `addExports`。而 `--add-exports` 命令行参数只作用于 boot 层
  的解析,对 ModLauncher 动态建出来的 GAME 层无效。

**下一步(按可行性排序)**:①反射拿 `ModuleLayer` 的私有 `controller` 字段(`Unsafe` 或
`privateLookupIn`)再 `addExports(gameModule, pkg, ourModule)`,在第一个类被 transform 时做一次;
②想办法让 FML 把我们的 jar 当成 mod 模块(FML 会给 mod 模块导出游戏包),需要看 FML 2.0.17 到底按什么
条件导出;③确认 FML 是否只给 GAME 层的模块导出,而 transformer jar 落在 PLUGIN 层 —— 若是,则应把
成品类的宿主换到 mod 层。**这一轮没有落地修复,只把范围收窄到这三条。**

### 紧接一轮:三条候选里两条被否掉,并且找到了关键的不对称

- **①反射 layer 的 controller:不可能。** `javap -p java.lang.ModuleLayer`(JDK 17)显示它的字段只有
  `cf / parents / nameToModule / allLayers / modules / servicesCatalog / CLV` —— **根本没有
  `controller` 字段**。Controller 只在 `ModuleLayer.defineModules*` 返回时存在,之后无处可取,这是
  JDK 的设计。`Module.addExports` 又是 `java.lang` 包私有,没有 `--add-opens java.base/java.lang`
  就到不了。①作废。
- **②让 FML 把我们当 mod:加元数据不够。** 往 jar 里补了一份 `META-INF/neoforge.mods.toml`
  (与 `mods.toml` 同内容)后照旧启动,`Found mod file` 仍然只有那 3 条,报错一字不变。所以"没被当成
  mod"不是某个元数据文件名的问题。
- **FML 2.0.17 自己从不调用 `addExports`。** 把 `loader-2.0.17.jar` 里**每一个** class 的字节扫一遍找
  `addExports`:命中 **0** 个。也就是说这条线的 FML 根本没有"给谁导出游戏包"这一步。

**关键的不对称(下一轮的抓手)**:1.21.4 那条**已验证**的线上,OptiFine 的补丁是生效的
(`Targets: 474`),而它的 `OptionInstance` 补丁**同样引用了** `SliderableValueSetInt`:

    [1.21.4-patched] srg/net/minecraft/client/OptionInstance.class: references SliderableValueSetInt = True
    [1.20.4-patched] srg/net/minecraft/client/OptionInstance.class: references SliderableValueSetInt = True

而 1.21.4 的日志里 `IllegalAccessError`=0。**同一段 OptiFine 代码在 21.4 上能过模块检查、在 2.0.17 上
不能**,所以这不是"OptiFine 的写法有问题",而是**两个 FML 版本给的模块访问不同** —— 21.x 给了,2.0.17 没给,
而且给的地方不在 `loader-2.0.17.jar` 里(下一个要扫的是 `securejarhandler` 与 `neoforge` 那两个 jar)。

**下一轮的第一步(测量,不是猜)**:在 loader 里加一个探针(照 `ReloadProbe` 那套,用系统属性开关),
把**运行时真实的模块图**打出来:游戏模块的包列表、`gameModule.isExported(pkg, ourModule)` 的结果、
`ourModule.canRead(gameModule)`、以及我们模块的名字与所在层。现在缺的正是这个 —— 前面全是"从错误消息
反推"。有了它,才知道该补 export 还是该补 read,还是该换个层放成品类。

### 再一轮:接口的可见性不是问题,问题回到"谁给我们 export",并找到一条可做的路

继续量了三件事:

1. **`OptionInstance$SliderableValueSet` 在原版运行时里是包私有的**
   (`interface net.minecraft.client.OptionInstance$SliderableValueSet<T>`,没有 `public`),
   1.20.4 与 1.21.4 都一样。
2. **OptiFine 的补丁把它改成了 `public`**,而且我们的成品类确实换上了它:

       1.20.4-patched(raw)  : public interface net.minecraft.client.OptionInstance$SliderableValueSet<T>
       1.20.4-shipped       : public interface net.minecraft.client.OptionInstance$SliderableValueSet<T>
       1.21.4-patched       : public interface net.minecraft.client.OptionInstance$SliderableValueSet<T>
       index 里 OptionInstance 相关条目:... OptionInstance$SliderableValueSet, ...

   所以**不是可见性问题**;实现一个 public 接口,卡住的只能是包导出。
3. **这条线上没有任何组件调用 `addExports`**:`loader-2.0.17.jar` 里 0 命中,`securejarhandler`、
   `neoforge`、`coremods`、`JarJarFileSystems`、`accesstransformers` 里也是 0 命中,只有 ModLauncher 的
   **TestingLaunchHandlerService** 提到过它。也就是说游戏模块的导出**不是靠调用 API 加的**。

**由此得到的(还待验证的)结论与做法**:FML 2.0.17 大概率是在**构造游戏模块的 ModuleDescriptor 时**
把各个包 `exports` 给"它认识的 mod 模块"(所以代码里看不到 `addExports`)。我们的 jar 因为在
`META-INF/services/cpw.mods.modlauncher.api.ITransformationService` 里声明了服务,被 ModLauncher 当作
**transformer jar** 收走,FML 于是从不把它当 mod 看 —— 这也解释了为什么往 jar 里补
`neoforge.mods.toml` 毫无作用:**不是文件名的问题,是这个 jar 根本不在 mod 扫描的结果里**。

**下一轮要做的实验(拆成两个 jar)**:

- **载荷 mod jar**:OptiFine 自己的类放在**自然路径**(`net/optifine/**`,不要 `srg/` 联合根)、带
  `META-INF/mods.toml`、**不放任何服务文件** —— 这样它是一个纯粹的 mod,FML 会把它当 mod 模块并给它
  导出游戏包,`net.optifine.config.SliderableValueSetInt` 实现 `net.minecraft.client` 的接口便合法;
- **transformer jar**:只放我们的 loader 类与 `optifineoforge/patched/**`(这些类只被当**字节**读,
  从不作为类从那里加载)+ 服务文件。

这个实验能一次说清"导出是不是按 mod 模块给的":如果错误消失,结论成立且问题一起解决;如果错误变成别
的样子,那也能立刻排除这条线。

### 决定性的一轮:探针否掉了模块理论,真正的病因是**访问标志没被复制**

先照计划加了模块图探针(`PatchedClassTransformer.logModulesOnce`,只读模块元数据、不加载任何游戏类),
第一次启动就把结论打出来了:

    module graph: our module is optifine, layer manager captured
    module graph: GAME layer holds minecraft srg mixinsynthetic neoforge mixinextras.neoforge
    module graph: optifine -> minecraft: reads it = false, net.minecraft.client is exported to it = true
    module graph: srg      -> minecraft: reads it = true,  net.minecraft.client is exported to it = true
    module graph: neoforge -> minecraft: reads it = true,  net.minecraft.client is exported to it = true

**承载那个类的模块 `srg` 既能读 `minecraft`,也拿到了 `net.minecraft.client` 的导出** —— 两条边都齐,
JVM 却仍然报 `IllegalAccessError`。模块理论因此被自己的测量否掉(前两轮那些"谁给 export"的推断全部作废,
幸好没有照着它们改代码)。

真正的病因是 `PatchedClassTransformer` **只换了 superName / interfaces / signature / fields / methods,
没有换 `access`**:OptiFine 的补丁把 `OptionInstance$SliderableValueSet` 从包私有改成 `public`,正是为了让
它自己 `net.optifine.config` 里的类能实现它;不复制访问标志,换装后的接口仍是包私有,于是"实现一个包私有
接口"非法 —— 而 JVM 的报错在两侧模块不同名时**一定会带上模块信息**,把我引向了错误的方向。

改法分两步,而第二步恰好由第一步的报错自己指出来:

1. 先只复制可见性三位 → `IllegalAccessError` 消失(换装数 112 → 119),紧接着是镜像问题:
   `IncompatibleClassChangeError: class net.optifine.config.SliderPercentageOptionOF cannot inherit from
   final class net.minecraft.client.OptionInstance` —— OptiFine 的补丁还把 `final` 去掉了。
2. 于是整字复制 `input.access = patched.access`(OptiFine 的那份编译就是该运行时下这个类的版本)。

结果:

    swapped: 182 个类(61 → 112 → 119 → 182),stderr 0 行

**下一个错误浮出水面,而它指向的是我方的遗留**:一个**没被改名的 SRG 名**活了下来:

    java.lang.NoSuchMethodError: 'net.minecraft.client.renderer.texture.TextureManager
      net.minecraft.client.Minecraft.m_91097_()'
        at TRANSFORMER/srg/net.optifine.Config.getTextureManager(Config.java:1187)

`m_91097_` 本该被 `SrgRemap` 改成 `getTextureManager`。它属于那 107 个未解析里的一类,下一轮直接从
"为什么 `Minecraft.m_91097_` 没被改写"查起(表里有它吗?是被碰撞保护拒了,还是类别名不一致那一族),
这比继续追新错误更值。

### 结果就是碰撞保护写错了范围(已修),而且 OptiFine 真的跑起来了

`m_91097_` 的答案在上一轮那段堆栈里就写着:`net.optifine.Config.getTextureManager` —— **OptiFine 自己也
有个叫 `getTextureManager` 的方法**。碰撞保护本意是"不要把一个成员改名成这个类已经有的名字",但它被
`mapMethodName` 在**引用**上也会调用,而引用与声明只能靠 owner 区分:声明时 owner 是正在改写的类,引用时
owner 是被调用的类。当时的实现只看"当前类有没有这个名字",于是把 `Config` 里对
`Minecraft.m_91097_()` 的**调用**也拒了,留下了一个运行时并不存在的 SRG 名。修法是一行条件:
`occupied()` 只在 `node.name.equals(owner)` 时才判碰撞。

    refused: 42 → 10(其中 32 条是本来不该拒的引用),rewrote 21461 方法名

启动结果:**swapped 184(stderr 0 行)**,而且 OptiFine 已经真的在跑了 —— 它出现在自己的崩溃报告里:

    OptiFine Version: OptiFine_1.20.4_HD_U_I7
    OptiFine Build: 20240317-172634
    Shaders: null

下一个错误换了一类,而且这次**不是"计划漏了",而是"运行时根本没有"**:

    java.lang.NoSuchMethodError: 'void net.minecraft.world.level.block.entity.BlockEntity.gatherCapabilities()'
      at net.minecraft.world.level.block.entity.BlockEntity.<init>(BlockEntity.java:47)

量了两边:OptiFine 那份 `BlockEntity` 里 `gatherCapabilities`/`setData`/`removeData` **一个都没有**,而
NeoForge 运行时里**只有 `setData`/`removeData`,没有 `gatherCapabilities`**。原因是 NeoForge 20.4 用
attachment 体系(`setData`/`removeData`)取代了 Forge 的 capability 体系,`gatherCapabilities` 被删掉了,
而 OptiFine 那份是照 **Forge** 编译的,`<init>` 里还在叫它。计划没恢复它是**对的** —— 运行时没有的东西
无法作为 donor。这是 `ForgeApiShims` 那一类问题(Forge API 在 NeoForge 上不存在),不是成员恢复问题。

**下一轮的起点(具体到可执行)**:对**改名后**的载荷做一遍引用扫描,列出"owner 是游戏类、而该成员在
运行时不存在"的全部引用 —— 那份清单就是这一类待处理项的完整集合(`gatherCapabilities` 只是其中第一条),
再决定是加空实现还是把调用点去掉。

### 清单做出来了,而且收得很小:10 条引用、3 个 owner

新增离线工具 `MissingTargets`:扫过载荷里**每一条**对游戏成员的引用(`SrgRemap` 看不到这类,因为它只
看 `m_`/`f_` 形状的名字,而 Forge API 成员用的是普通名字),沿继承链问运行时有没有。两次收敛很关键:

   只索引运行时:            1118 个类、43162 条引用,**1259** 条"缺失"
   再把载荷自己的类也索引:  148 条
     —— 差额是 OptiFine **自己加进被替换类里的成员**(ModelPart.getChildModelDeep、
        Options.ofClouds、TextureAtlasSprite.spriteNormal ...):运行时没有,但换装后的类里有,
        调用完全成立。不索引载荷就会把它们全报成问题。
   再索引完整库 classpath + 把 JDK 祖先视为存在:  **10 条**
     —— 差额是 datafixers/Codec/LogUtils/authlib 这些**别的 jar**里的类(不在客户端 jar 里),
        以及 `Direction.ordinal()` 这类从 `java.lang.Enum` 继承来的方法。

最终 10 条,分成三组,每组性质不同:

   8x  net/minecraft/world/level/block/entity/BlockEntity
         gatherCapabilities()V / invalidateCaps()V / deserializeCaps(...)V / serializeCaps()... 
         getCapabilities()Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;
         requestModelDataUpdate()V
       → Forge 的 capability 体系,NeoForge 20.4 用 attachment 取代后删掉了;`getCapabilities` 的返回
         类型甚至就是 **`net/minecraftforge`** 的类。这属于 `ForgeApiShims` 那一类(Forge API 在
         NeoForge 上不存在),不是成员恢复。
   2x  net/minecraft/launchwrapper/LaunchClassLoader.registerTransformer(String)V
       → 来自 `optifine/OptiFineForgeTweaker`,那是 OptiFine 给 Forge 安装器用的 tweaker 类,在
         NeoForge 上根本不会被加载(manifest 里的 `TweakClass` 指向它)。可以无视,也可以在重打包时
         与 installer 类一起删掉。
   5x  com/mojang/blaze3d/platform/NativeImage$WriteCallback
         free()V / address()J / getData(JI)Ljava/nio/ByteBuffer;
       → 这一组还没定性,下一轮要先看是接口在两边声明不同,还是 OptiFine 那份编译对着别的版本。

**下一轮**:给 BlockEntity 那 8 个成员出**空实现**(走 `ForgeApiShims` 那条路,因为运行时没有 donor),
顺手看 `NativeImage$WriteCallback` 那 5 条到底是什么,然后继续启动。

### 空实现step + 成员可见性取宽者 —— 1.20.4 跑起来了

`MissingTargets` 加了 `--stub` 模式:把**运行时和载荷都没有**的成员按返回类型补一个默认值实现
(void→RETURN、对象→ACONST_NULL、数字→0)。**接口里补的是 default 方法而不是抽象方法** —— 给接口加抽象
方法会把一次缺调用变成别处的 AbstractMethodError。rig 里成为 3b2 步:

    scanned 1118 classes, 43162 references, 10 missing
    stubbed 9 members on {NativeImage$WriteCallback=3, BlockEntity=6},
            1 left alone because their owner is a runtime class rather than a payload class

接着换装数从 184 涨到 224,而新错误正好是**上一轮那条教训的反面**:

    IllegalAccessError: NeoForgeRenderTypes$Internal tried to access method 'RenderType.create(...)'

上一轮学到"访问标志要跟着 OptiFine 走",但那只对**类**成立;对**成员**,游戏的版本可能被 access
transformer 放宽过,NeoForge 自己的代码依赖那个放宽。整字照搬 OptiFine 的 `create()` 把它narrow回去,
于是 NeoForge 调不动了。现在是:**类标志取 OptiFine 的,成员可见性取两者中更宽的那个**
(`rank`:public 3 > protected 2 > package 1 > private 0)。

改完之后这一轮的结果是**质变**:

    swapped: 271 个类
    reloads: 2                      ← 资源重载跑到第 2 次
    stdout: 14874 行(其中 OpenGL 行 7067、Shaders 14、connected textures 6)
    VERDICT: TIMEOUT (261s)         ← 不是崩溃,是浏览器窗口一直活着被计时器掐掉
    crash-reports 目录里最新的一份仍是上一轮 08:22 的 —— **这一轮没有产生崩溃报告**

stderr 只剩 4 条 `NoClassDefFoundError`(其中一条是 `net.minecraft.world.item.ItemStack`),加上 stdout 里
1 条 `Caught error`;都不致命,游戏继续跑。这是 1.20.4 这条线第一次进入"能玩的状态",而不是"启动即崩"。

**下一轮该做的**:把那 4 条 NoClassDefFoundError 的完整堆栈读出来(现在只看到尾部),确认它们是不是
同一类问题(某个换装类的字段描述符在模块尚未就绪时被解析);然后按 1.21.4 那条线的验收清单核对
(贴图集、模型烘焙、资源重载无错),把 1.20.4 从"能启动"推进到"行为与已验证线一致"。

### 剩下那个真问题查清了:Forge **扩展接口**上继承来的成员在 shim 里丢了

4 条异常读完,全是同一形状且**不致命** —— OptiFine 的反射(`ReflectorMethod.getMethod`)在解析
`BlockState`/`ItemStack` 时经 builtin loader 抛出 `ClassNotFoundException`,被吞掉、该可选特性跳过。
真正的问题在 stdout 里那一条 `Caught error loading resourcepacks`(整批资源包被移除,也就是**模型烘焙没了**):

    NoSuchMethodError: 'BakedModel ModelBaker.bake(ResourceLocation, ModelState, Function)'
      at net.minecraft.client.renderer.block.model.MultiVariant.bake(MultiVariant.java:71)
      at ...multipart.MultiPart.bake
      at ...model.ModelBakery$ModelBakerImpl.bake

两边都量了:

- 运行时的 `ModelBaker` 是 **NeoForge 版**:只有 `getModel(ResourceLocation)` 和
  `bake(ResourceLocation, ModelState)`(两参数);
- 我们发出去的 `ModelBaker`(`ModelBaker` 确实被换装了,日志里有这一行)是 OptiFine 版:
  `public interface ModelBaker extends net.minecraftforge.client.extensions.IForgeModelBaker`,
  自己只声明那两个方法 —— **三参数的 `bake` 是从那个 Forge 扩展接口继承来的**;
- 而那个 shim 是**空的**:

      105  net/minecraftforge/client/extensions/IForgeModelBaker.class
       99  net/minecraftforge/client/extensions/IForgeFont.class
      104  net/minecraftforge/client/extensions/IForgePoseStack.class

  一百来字节 = 没有成员的接口。`ForgeApiShims` 的规则是"每个被引用的 Forge 类型出一个 stub,成员取
  **OptiFine 的类直接点名在它上面的**字段与方法";而这里 OptiFine 调用的是
  `ModelBaker.bake(三参数)`(owner 是 `ModelBaker`),**从没在 `IForgeModelBaker` 上点过名**,于是它是空的,
  那条继承来的方法就消失了 —— 结果 `ModelBakery$ModelBakerImpl`(换装后**确实**有那个三参数方法)
  也救不了 `invokeinterface`,因为接口里查不到。

**要修什么(下一轮)**:让这些 shim **接口**补上"载荷里的实现类提供了、而运行时接口没有"的公开方法
(按实现类去反推),这样 `bake(三参数)` 会出现在接口上,调用可解析。这是个通病,不止 `ModelBaker` 一处:
凡是 OptiFine 通过 Forge 扩展接口继承成员的地方都会这样。修完应当能把 `Caught error loading
resourcepacks` 去掉,恢复模型烘焙 —— 那正是 1.21.4 那条线验收时用过的判据之一。

顺带记下 shim 名单里的一处可疑现象:`net/minecraftforge/cl.class`、`cli.class`、`clien.class`、
`client/m.class` 这类**名字明显被截断**的条目也在 48 个 shim 里。它们同样只有七十来字节,不影响本次
诊断(真正相关的 `IForgeModelBaker` 名字是完整的),但下一轮顺手确认一下是否是生成时截断。

### 真相不是 shim 空,而是**换装接口时把超接口列表整个换掉了**

继续查下去发现前面的推断还差一层。把两份运行时的 `ModelBaker` 都 javap 出来对比:

    NeoForm 的:  interface ModelBaker { getModel(ResourceLocation); bake(ResourceLocation, ModelState); }
    NeoForge 的: interface ModelBaker extends net.neoforged.neoforge.client.extensions.IModelBakerExtension
                 { getModel(ResourceLocation); bake(ResourceLocation, ModelState); }

**三参数的 `bake` 在 NeoForge 那边是从它自己的扩展接口 `IModelBakerExtension` 继承来的**,而 OptiFine 那份
`ModelBaker` 继承的是 **Forge** 的对应物 `IForgeModelBaker`(我们的空 shim)。两边各自"借"了不同扩展接口的
方法,而我在换装时 `input.interfaces = patched.interfaces` —— **把运行时的超接口列表整个替换掉了**,
于是运行时自己的调用方(`MultiVariant.bake` 是按 `IModelBakerExtension` 编译的)再也找不到那条路:

    NoSuchMethodError: 'BakedModel ModelBaker.bake(ResourceLocation, ModelState, Function)'

修法与前面几条同源——**取并集而不是替换**(只在接口上做:给类加超接口会要求它实现那些抽象方法,
而换装后的类体未必有):

    if((patched.access & ACC_INTERFACE) != 0) {
        merged = patched.interfaces + input.interfaces 中缺的那些
        input.interfaces = merged
    }

结果:**`Caught error: 0`** —— 资源包不再被整批移除,模型烘焙这一关过了(换装数 271 → 274)。

紧接着冒出的是新的一层,而且这次是**真崩溃**(上一轮那次没有崩溃报告):

    java.lang.IllegalStateException: Already building.
      at fml_earlydisplay/SimpleBufferBuilder.begin
      at ...DisplayWindow.paintFramebuffer
      at neoforge/NeoForgeLoadingOverlay.render(NeoForgeLoadingOverlay.java:84)
      Description: Rendering overlay

原因方向很清楚:**OptiFine 换装了 `LoadingOverlay`**(日志里有 `Replaced ...LoadingOverlay ... (25 fields,
12 methods)`),它的加载画面代码与 NeoForge 的 early-display 叠在一起,`SimpleBufferBuilder` 被重入。
这也解释了为什么上一轮"跑得久却没崩":那时 OptiFine 的加载画面补丁因接口解析失败没有真正执行。
**下一轮(最后一轮)**:从 `NeoForgeLoadingOverlay` 与 OptiFine 那份 `LoadingOverlay` 的关系入手,
先确认是不是同一帧里两条渲染路径都进了 early display;并把这一轮的结论、当前三条线的状态与剩余
范围的实话写清楚。

## 2026-09-15(续):1.20.4 跑起来了,而且 OptiFine 的着色器系统在初始化

转折点是**发现了我自己工具里的一个 bug**,而不是新知识。

把 `LoadingOverlay` 留在运行时那版之后,游戏死在:

    NoSuchMethodError: 'void net.minecraft.client.gui.screens.LoadingOverlay.update()'
      at GameRenderer.render(GameRenderer.java:1311)

读字节码才看清这是一对:

    880: instanceof  LoadingOverlay
    900: invokevirtual LoadingOverlay.update:()V     ← 在 OptiFine 版的 GameRenderer 里

`update()` 只存在于 **OptiFine 那版** `LoadingOverlay`(运行时两份都没有),所以"留运行时那版"等于拆了这一
对。正确做法是把这对里缺的方法**在类加载时补回运行时那版** —— 正是上一轮刚搭好、却一直没生效的
"延迟 stub"机制。它没生效的原因有两层,都在工具里:

1. **分析读的 jar 与发布的 jar 不是同一个**:stub pass 跑在排除之前,索引里还留着 OptiFine 那版
   `LoadingOverlay`(带 `update()`),引用于是"看起来能满足"。现在 `MissingTargets` 接受跳过前缀
   (`--stub <in> <out> <stubs> <skip> ...`),索引、扫描、stub 三处共用同一份清单;不发布的类也不再被扫描。
2. **一条把整个工具废掉的规则**:JDK 祖先被当成"一定有"。本意是别把 `Direction.ordinal()`(继承自
   `java.lang.Enum`)误报,但**每个类上面都有 `java/lang/Object`**,走链一碰到它就返回"存在"——报告因此
   长期只有 10 条,`LoadingOverlay.update()` 从未被报出、也从未被 stub。改法不是再调启发式,而是**真的把
   JDK 索引进来**:通过 `jrt:/` 读 `java.base`/`java.desktop`/`java.logging`(12998 个类),
   `declares()` 里那条特例整个删掉。

修完前后的对比很能说明问题:

    错误规则下:68 条"缺失",其中 ordinal()/getMessage()/add() 这类 JDK 继承的全是假阳性
    正确索引后:24 条,真正的问题才浮出来:
               BakedModel.getQuads(...,ModelData,...) / getRenderTypes(...,ModelData) /
               useAmbientOcclusion(...)   ← Forge 形状的模型 API
               BlockEntity 的 capability 成员、NativeImage$WriteCallback 的三个方法
               LoadingOverlay.update()V 与 isFadeOut()Z   ← 就是上面那一对缺的

本轮结果:

    3b2/5  stubbed 21 members on {NativeImage$WriteCallback=3, BakedModel=9, ModelBaker=2,
                                  BlockEntity=6, BlockState=1}, 3 left for the loader
    load   Runtime stubs to add: 3 members across 2 classes
           Stubbed net.minecraft.client.gui.screens.LoadingOverlay.update()V
           Stubbed net.minecraft.client.gui.screens.LoadingOverlay.isFadeOut()Z

启动:**STARTED (41s, marker: Sound engine started)**,而且 rig 这次**截到了图**(`screen.png`,70 KB ——
前面几轮一直报 `handle is invalid`,因为根本没有窗口)。量化:

    swapped: 282 个类   reloads: 2   stdout: 21132 行(其中 OpenGL 10153)
    [Shaders] OpenGL Version: 3.2.0 NVIDIA 591.86 / GL_MAX_DRAW_BUFFERS: 8   ← OptiFine 着色器系统在初始化
    18 条 "Created:"、Shaders 14 行、Connected textures 6 行

剩下一个失败,比之前具体得多:

    IllegalStateException: Failed to create model for minecraft:skull
      → Caught error loading resourcepacks(整批资源包被移除)

也就是说**模型烘焙大体通了**,只剩 `minecraft:skull` 一个模型。**下一轮**:查它为什么失败(它与 OptiFine
换装的 `SkullBlockRenderer`/`SkullModel` 有关),并确认 `BakedModel` 那 9 个 Forge 形状成员返回默认值是否
正是原因 —— 那 9 个 stub 让调用"能过",但语义上未必对。

## 2026-09-15(再续):skull 模型修好,而真正的拦路虎是 **FML 的 early window**

### skull 的病因:OptiFine 的 `getChild` 按 id 找子节点,而 id 没人设

    IllegalStateException: Failed to create model for minecraft:skull
      at SkullBlockRenderer.createSkullRenderers:66
    Caused by: NullPointerException: ... "this.head" is null
      at net.minecraft.client.model.dragon.DragonHeadModel.<init>:21

读字节码:`DragonHeadModel` 的构造函数是 `this.head = root.getChild("head"); this.jaw = this.head.getChild("jaw")`,
而 **OptiFine 那版 `ModelPart.getChild(String)` 不是 `children.get(name)`** —— 它遍历 `children.keySet()`,
用 `name.equals(child.getId())` 匹配!`getId()` 读的是 OptiFine 新加的一个 `id` 字段,只有**它自己那套烘焙
代码**才会 `setId`;而**OptiFine 根本没有 patch `PartDefinition`**(查过,`patch/srg` 里没有它)。
于是在这条线上:运行时烘的模型 → 没人设 id → 每次 `getChild` 都返回 null → 第一个要取子模型的
`minecraft:skull` 就炸了。Forge 上这对是配套的,这里不是。

修法是把"**保留游戏自己的实现**"做成机制(与 stub 列表正好互为镜像):`keep-runtime.txt` 列出
`owner/name/desc`,loader 在换装后把这些成员的身体换回运行时那版。

    3c/5  keep-runtime: 1 members keep the game's body
    load  Members keeping the game's body: 1
    → 'Caught error': 0(资源包不再被移除,skull 建得出来)

### 然后真正的拦路虎浮出来:FML 的 early display 重入

模型一修好,游戏就走到更后面,撞上:

    IllegalStateException: Already building.
      at fml_earlydisplay/SimpleBufferBuilder.begin
      at ...DisplayWindow.paintFramebuffer → DisplayWindow.render
      at neoforge/NeoForgeLoadingOverlay.render(NeoForgeLoadingOverlay.java:84)

**这一串帧里没有一行 OptiFine 代码** —— 全是 FML/NeoForge 自己的。查 rig 的启动脚本发现它**早就想关掉
这个窗口**,但关错了开关:

    -Dfml.earlyprogresswindow=false        ← 这是 Forge 时代的属性,FML 2.0.17 不认

FML 2.0.17 把它做成了**配置文件**:`config/fml.toml` 里的 `earlyWindowProvider`(默认 `"fmlearlywindow"`)。
改成 `"none"` 之后:

    VERDICT: STARTED (40s, marker: Sound engine started)     ← 截图这次 1.2 MB
    swapped: 313 个类   'Caught error': 0   崩溃报告:无(比 22:10 更晚的一份都没有)
    Sound engine started 1 / Setting user 1 / Created: 13 / Shaders 14 行 / Connected textures 3 行
    early-display 帧:0

也就是说 1.20.4 现在**进到标题画面、OptiFine 的着色器与连接材质在跑、没有任何崩溃**。这也解释了前面几轮
那些"位置不定的 overlay 崩溃":那扇窗一直是开着的,只是每次撞上去的时机不同。

**这条线的运行要求写进 `docs/MATRIX.md`**:1.20.2/1.20.4 需要 `config/fml.toml` 里
`earlyWindowProvider = "none"`(FML 的 early window 与 OptiFine 换装的渲染类在同一帧里互相重入)。
这不是我们能在 mod 里设的 —— FMLConfig 在 mod 之前就读完了。

**顺带一个反复踩到的坑**:PowerShell 的 `Set-Content -Encoding UTF8` 会写 BOM,而 TOML 解析器直接报
`Invalid bare key: \ufeffEarly`。改配置/源码一律用 `[System.IO.File]::WriteAllText(..., UTF8Encoding($false))`。
## 2026-09-19(重建后的 rig):26.1.2 在本机第一次复跑,停在哪一步

本节的数字都来自本轮在**重新搭起来的** rig(`optifineoforge-test`)上真跑的两次;GitHub 上 README 里
"3478 行 `[OptiFine]`、`processClass` 795 次"那次记录来自更早的会话,本轮没有复现到那一步。

**已经量到的**

* rig 装上 Minecraft 26.1.2 + NeoForge `26.1.2.109`(FML **11.0.15**、Java **25**),
  原版客户端 38,113,927 字节;这一版的 profile 主类仍是 `net.neoforged.fml.startup.Client`,
  所以 1.21.x 那套 FML 10 启动器可以直接用(给它加了 `-JavaExe`,这一线要 JDK 25)。
* **基线**(mods 里只有修过元数据的 OptiFine):`VERDICT: STARTED` + `Setting user` + `Sound engine started`
  + 无崩溃报告 + stderr 107 字节 —— 与"不带任何 mod"的对照跑相同。**实例本身可用**。
* 元数据:OptiFine 的 `META-INF/mods.toml` 必须换成 `neoforge.mods.toml`(loaderVersion `[1,)`);
  **`optifine/Patcher`、`optifine/xdelta/**`、`optifine/json/**` 不能删** —— 删了以后
  `OptiFineBaseTransformer.<init>` 抛 `NoClassDefFoundError: optifine/Patcher`,处理器实例化失败
  (`ServiceConfigurationError: Provider optifine.OptiFineClassProcessor could not be instantiated`),
  OptiFine 完全不生效。
* 处理器需要"基底类":不把成品类放进它的 jar 时,它对每个目标报
  `java.io.IOException: Base resource not found: net/minecraft/…`(一次跑 866,068 字节 stderr)。
  把本仓库离线流水线产出的 `srg/**` 成品类(566 个,net/minecraft 486 —— 与 `docs/DEVELOPMENT.md`
  记的 566 / 486 一致)并进 OptiFine 的 jar 之后,处理器开始真的安装它们,`[OptiFine]` 开始打印。

**一条新的加载器陷阱(与 1.21.9 那轮同源)**

`optifine-own-classes.jar`(OptiFine 自己的类 + Forge shim)被 FML 当成**早期服务罐**:

```
Found 4 early service jars (out of 103)
Loading FML Early Services:
 - …/loader-11.0.15.jar
 - …/earlydisplay-11.0.15.jar
 - mods/optifine-26.1.2-neoforge.jar
 - mods/optifine-own-classes.jar          <- 不该在这里
```

因为流水线拆出的 classpath jar **原样保留 `META-INF/services/**`**,而 OptiFine 的两个 NeoForge SPI
服务文件正在其中。早期服务的类由一个**看不见游戏类**的加载器加载,于是
`net.optifine.reflect.Reflector.<clinit>` 抛 `NoClassDefFoundError: net.minecraft.world.level.chunk.ChunkAccess`,
刚打补丁的 `net.minecraft.util.Mth.<clinit>` 抛 `net/optifine/util/MathUtils` 找不到。
从第二个罐子里剔除这两个服务文件后 `[OptiFine]` 开始打印(5 行),stderr 回到 107 字节。

**卡在哪:换父类**

```
net.neoforged.fml.ModLoadingException: Loading errors encountered:
	- NeoForge (neoforge) has failed to load correctly
	  java.lang.VerifyError: Bad type on operand stack
	    Location: net/neoforged/neoforge/attachment/AttachmentSync.onChunkSent(...)V @82: invokestatic
	    Reason: Type 'net.minecraft.world.level.block.entity.BlockEntity' ... is not assignable to
	            'net/neoforged/neoforge/attachment/AttachmentHolder'
```

1.21.9 / 1.21.10 / 1.21.11 上这一步由**加载期**读 `reparent.txt` 修;26.1.2 的挂载点是 OptiFine 自己的
处理器,它不做这件事,所以**换父类(含构造器 chain 重写)、成员回填、shim 都必须在离线阶段写进
并进 OptiFine jar 的 `srg/**` 成品类**。这就是本项目在这一线上的那份工作,本轮还没有做,
所以 26.1.2 **没有通过验收**。
## 2026-09-19(重建后的 rig):26.1.2 在这一版上跑通,配方与实测数字

上一次记录是更早会话里的 3478 行 / `processClass` 795 次。这一轮在**重新搭起来的** rig 上从头装配了一遍,
**四项判据全部复现**,并且 `processClass` 次数与那次记录**完全一致**:

```
===== VERDICT: STARTED (started: Setting user + Sound engine started)
  Setting user        : True
  Sound engine started: True
  new crash reports   : 0
  stderr bytes        : 107      <- 与"不带任何 mod"的对照跑同一行、同长度
  [OptiFine] lines    : 352
  processClass 行数    : 795     <- 与更早的记录相同
  handlesClass 行数    : 7369
  OpenGL API ERROR    : 0 行
```

**如实写下的两点差异**:① `[OptiFine]` 行数这一次是 **352**,不是记录里的 3478 —— 我没有解释这个差别
(没有那份旧日志可比);`processClass` 与 `handlesClass` 的次数则是对得上的。
② 导航栏里的 `[OptiFine] OptiFine_26.1.2_...` 版本横幅这一次**没有**出现(1.21.9/10/11 那三条线都打了),
但处理器的 `processClass` 明确在跑(见下)。stderr 的 107 字节是 log4j 的
`Advanced terminal features are not available in this environment` 一行,**本模组自己写 0 字节**;
对照跑同样只有这一行(另一次对照量到 104,差别只是时间戳尾数的位数)。

### 这一轮装配出来的配方(每一步都是量出来的)

1. **NeoForge `26.1.2.109`** 用安装器装(Java **25**,FML **11.0.15**)。注意这台机器上
   `maven.neoforged.net` 的 **IPv4 连不上、IPv6 能连**(`curl -6` 拿到 200),安装器自己的下载器用 IPv4
   时会报 `failed 1`。
2. **OptiFine `preview_OptiFine_26.1.2_HD_U_K1_pre2.jar`**(7,797,229 字节,与 `docs/DEVELOPMENT.md`
   记的字节数一致)从第三方镜像取。
3. **元数据**:`META-INF/mods.toml`(Forge 时代,`loaderVersion="[14,)"`)换成 `neoforge.mods.toml`
   (`loaderVersion="[1,)"`);`META-INF/services/cpw.mods.modlauncher.api.ITransformationService` 去掉
   (这一版没有 ModLauncher)。**`optifine/Patcher`、`optifine/xdelta/**`、`optifine/json/**` 必须保留** ——
   删掉它们,`OptiFineBaseTransformer.<init>` 立刻 `NoClassDefFoundError: optifine/Patcher`,
   处理器实例化失败,OptiFine 完全不生效。
4. **离线流水线**(全部来自本仓库,`src/main/java/kynarain/cn/optifineoforge/optifine/`):
   `OptifinePipeline` → 566 个成品类(net/minecraft 486);
   `HierarchyPlan` → `reparent.txt`(1 个类:`BlockEntity` 到 `AttachmentHolder`,构造器 `()V`);
   `ReparentPayload` → 换父类 + 构造器 chain 重写 + 保留运行时的接口(工具自己的日志逐条列出);
   `MemberRestorePlan` → 224 条计划 / 73 个 donor;`RestoreMembers` → **256 个成员 / 72 个类**。
5. **把这些成品并进 OptiFine 的 jar**:`srg/**`(成品类)**和 `assets/**`**(OptiFine 打过的资源)。
   两者缺一不可,而且都是量出来的:
   * 只并类不并资源 → 每个资源都要走 `optifine.Patcher.applyPatch`,于是
     `java.io.IOException: Base resource not found: assets/minecraft/models/block/template_glass_pane_*.json`
     刷 13,367 字节 stderr(判据要求 stderr 与对照一致,所以那一次不算通过);并上 `assets/**` 之后回到 107 字节。
   * 不并任何东西 → 每个类都报 `Base resource not found: net/minecraft/…`(一次跑 866,068 字节 stderr),
     OptiFine 全程不生效(0 行 `[OptiFine]`)。
6. **第二个 mod 罐子 `optifine-own-classes.jar`**(OptiFine 自己的类 + Forge shim),并且**必须剔除
   `META-INF/services/net.neoforged.**`** —— 那两个服务文件一在,FML 就把这个罐子当**早期服务罐**,
   其中的类由一个看不见游戏类的加载器加载,于是 `net.optifine.reflect.Reflector.<clinit>` 抛
   `NoClassDefFoundError: net.minecraft.world.level.chunk.ChunkAccess`、刚打补丁的
   `net.minecraft.util.Mth.<clinit>` 抛 `net/optifine/util/MathUtils` 找不到。
   (`OptifinePipeline` 拆出的 classpath jar 原样保留 `META-INF/services/**`,所以这一步是必须的。)
7. **启动**:这一版 profile 的主类仍是 `net.neoforged.fml.startup.Client`,所以 1.21.x 那套
   `launch-fml10.ps1` 直接用(给它加了 `-JavaExe`,这一线要 JDK 25;入口点用全反射的
   `DiagnosticClientAny`,因为官方入口点把启动期异常交给一个什么都不打印的模态对话框)。
   mods 里放的就是上面那两个罐子 —— **本项目的加载器不在其中**,挂载点是 OptiFine 自带的
   `OptiFineClassProcessor`。
## 2026-09-20:光影包实测(六个包,全部通过)

用户要求"自己下载安装多个光影包测试"。做法与结论如下,全部是本机真机跑出来的。

### 怎么装、怎么选

光影包从 **Modrinth API**(`api.modrinth.com`,这台机器 IPv4/IPv6 都通)取,
按 `versions:26.1.2` 过滤后取各项目最新版,放进 `<游戏目录>\shaderpacks\`。选中哪个包由
**`<游戏目录>\optionsshaders.txt`** 决定——这是从 OptiFine 自己的类里读出来的,不是猜的:
`net/optifine/shaders/Shaders` 里有 `optionsshaders.txt` 常量、以
`new File(Minecraft.getInstance().gameDirectory, "optionsshaders.txt")` 构造,
键名来自 `net/optifine/shaders/config/EnumShaderOption.SHADER_PACK`(`getPropertyKey()` =
**`shaderPack`**,默认值空串),值就是 `shaderpacks\` 里的条目名(含 `.zip`)。
rig 侧脚本 `test-shaderpack.ps1` 做"装包 → 写配置 → 启动 → 从日志取数"这一整条,并逐包打印判据。

### 26.1.2 上六个包的结果(均为真机一次运行)

| 光影包 | 版本 | 判据(四项) | 包是否加载 | `[OptiFine]` 行 | `[Shaders]` 行 | GLSL 编译错误 | GL 错误 |
|---|---|---|---|---|---|---|---|
| MakeUp-UltraFast | 9.5e | 全中 | 是 | 3436 | 80 | 0 | 0 |
| Complementary Reimagined | r5.9.3 | 全中 | 是 | 569 | 106 | 0 | 0 |
| BSL Shaders | v10.1.5 | 全中 | 是 | 388 | 72 | 0 | 0 |
| Photon | v1.3b | 全中 | 是 | 1194 | 185 | 0 | 0 |
| Rethinking Voxels | r0.1-beta9 | 全中 | 是 | 566 | 98 | 0 | 0 |
| Solas Shader | V3.7b | 全中 | 是 | 3386 | 84 | 0 | 0 |

"判据全中"= `VERDICT: STARTED` + `Setting user` + `Sound engine started` + **本次运行无崩溃报告**;
六次的 stderr 都是 **107 字节**,即与"不带任何 mod"的对照跑同一行 log4j 环境告警。
"包是否加载"取自日志里 OptiFine 自己的那行 `[Shaders] Loaded shaderpack: <包名>`。

**顺带把一个之前"没有解释"的数字解释掉了**:README 里 26.1.2 的旧记录写的是 `[OptiFine]` **3478 行**,
而我在无光影包时量到 352 行、当时记为"差别没有解释"。装上光影包后这一线量到 **3436 行** ——
3478 属于"开着光影包"的那次记录,352 属于"没开"的那次。两者不是矛盾,是两种情况。
## 26.1.2 离线补上粒子提供者查找(新工具 `ParticleProviderRepair`)

这条线发的是**成品载荷**、走 OptiFine 自己的类处理器,没有加载期变换,所以 `ParticleEngine.makeParticle`
那个缺陷只能在载荷 jar 里离线改。缺陷本身已在 1.21.10 联机实测到:进世界几秒后下雨粒子一生成客户端就死,

```
NoSuchMethodError: 'Int2ObjectMap ParticleResources.getProviders()'
  at ParticleEngine.makeParticle(ParticleEngine.java:76)
  at ClientLevel.doAddParticle(...)   <- WeatherEffectRenderer.tickRainParticles
```

OptiFine 的副本按"注册表 id 索引的 `Int2ObjectMap`"取值,而运行时的 `ParticleResources` 只剩
`Map<ResourceLocation, ParticleProvider<?>> getProviders()`。工具把它改成按粒子类型的资源名取值:

| 原 | 改为 |
|---|---|
| `ParticleResources.getProviders()Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;` | `ParticleResources.getProviders()Ljava/util/Map;` |
| `Registry.getId(Object)I` | `Registry.getKey(Object)Lnet/minecraft/resources/Identifier;` |
| `Int2ObjectMap.get(I)Object` | `java/util/Map.get(Object)Object` |

取到的是同一个 provider(注册表 id 与资源名指向同一条),所以 OptiFine 的自定义粒子颜色/贴图仍在链路上
—— 这也是为什么不是直接丢弃这个类、改用运行时的副本。注意 26.x 的返回类型是 `Identifier`(该版本已把
`ResourceLocation` 改名),此处必须按本版真实签名写,不能照抄别线的描述符。

**形状**照 `SpriteCollectionRepair`(同为离线双胞胎):`main(<payload jar> [--dry-run])`,`ZipFile` 读、
只改写 `srg/net/minecraft/client/particle/ParticleEngine.class` 一个条目、其余原样写回临时文件再
`REPLACE_EXISTING`;载荷里没有该类、或没有该形状的方法时,**明确打到 stdout 并原样写回**,不静默跳过。

**已核实**(父会话独立复核,不只看工具自证):

- 与备份逐条目对比:两边都 9536 条,无增无删,**只有那一个类的内容不同**。因此 jar 体积
  `10018170 -> 10170792` 的变化是重新压缩,不是多塞了内容 —— jar 体积不能当有效性判据,逐条目 diff 才是。
- `javap` 该类的三处调用确已改写(见上表),且用的是 26.x 真实的 `Identifier`。

**待办(不要当成已完成)**:

- **运行期那一半还没跑**:真机进 26.1.2 世界、让雨粒子生成,这条缺陷才算在这条线上验完。
- **必须把该工具接进这条线的装配步骤**:现在这条线的载荷是手工装配的(见上文配方),不把这一步写进
  配方,重建时这个修复就会丢。接线后要在本文件里补上实际调用的命令与它的输出。
- 备份留在 `jars-26.1.2\optifine-26.1.2-neoforge.jar.before-particle-fix`,便于逐条目复算。
### 发布前的一项校验(26.1.2 载荷)

`ParticleProviderRepair` 是幂等的,而且**不静默**:载荷已经修好时,它会明说"没有可修的地方",并报出
`makeParticle` 当前的形状。实测(rig 侧 `repair-26.1.2-payload.ps1 -DryRun`):

```
no method of ... ParticleEngine reads the particle provider through
ParticleResources.getProviders()/Registry.getId()/Int2ObjectMap.get(),
so nothing was repaired and the payload is left as it is
makeParticle calls ParticleResources.getProviders()Ljava/util/Map;
```

所以**重建这条线的载荷之后、发布之前**,先跑一次 `repair-26.1.2-payload.ps1 -DryRun`:

- 期望看到 "nothing was repaired" + `makeParticle calls ...getProviders()Ljava/util/Map;`
  —— 这才说明这次重建把修复带上了;
- 如果它反过来报"修了三处",说明这次重建**丢了**修复,必须先真跑一次(并复算逐条目 diff 只有
  `srg/net/minecraft/client/particle/ParticleEngine.class` 一个条目不同),才能继续发布。

该脚本在改写后会自己出证据:与 `.before-particle-fix` 的逐条目对比(条目数一致、无增删、只能差一个
条目,否则以非零退出明确判"不是干净的修复"),外加该类的 `javap`。**jar 体积不是判据**:首次改写时
10018170 -> 10170792 字节的变化纯粹来自重新压缩。