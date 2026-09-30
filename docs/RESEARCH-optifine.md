# RESEARCH-optifine — OptiFine jar 结构调研（供 OptifiNeoforge 使用）

本文档只做事实记录：全部结论来自对下载到的 OptiFine jar 做 **zip 条目清单、`META-INF` 读取、`javap -p -c -constants -v` 反汇编、以及 xdelta 负载内部 ASCII 常量探测**。
未运行 Gradle；未执行任何改变 git 状态的命令；本文件是本次任务唯一新增/修改的文件。
构建产物来源：`https://bmclapi2.bangbang93.com/optifine/<MC version>/<type>/<patch>`（例：`/optifine/1.21.11/HD_U/J9`、`/optifine/26.1.2/HD_U_K1/pre2`），版本清单取自 `https://bmclapi2.bangbang93.com/optifine/<MC version>`（JSON，字段 `type`/`patch`/`filename`/`forge` 即 URL 中间两段）。

调研用 jar 位于仓库之外的临时目录（`%TEMP%\optifi-research\`），其中 `1.21.11 HD_U_J9`、`26.1.2 HD_U_K1_pre2` 两份沿用父 agent 已下载的文件。

---

## 0. 一句话结论

**全局最重要的结构差异是"补丁负载的命名空间在 1.20.6 换了"**：1.20.1 / 1.20.2 / 1.20.4 的 `patch/srg/**` 负载是**真正的 SRG 成员名**（如 `f_127499_`、`m_137502_`），而 **1.20.6 起变成 Mojang 官方名**（`LOGGER`、`saveFile`、`trackingStackTrace`）；目录名 `srg/` 从此只是历史遗留，不再代表 SRG。26.1.2 进一步新增 NeoForge 原生 SPI（`net.neoforged.neoforgespi.transformation.ClassProcessor` + `net.neoforged.neoforgespi.locating.IModFileCandidateLocator`），并让 `patch/srg` 与 `patch/notch` 变成**同一份字节**。

---

## 1. 构建对照表（本次实际检查的 7 份构建）

| MC 版本 | 构建 (type/patch) | 文件名 | 大小 (bytes) | `buildof.txt` | 形态 | mod 元数据 | `META-INF/services/*` | `patch/srg` vs `patch/notch` | `patch/srg` 负载命名空间 | 运行时 transformer |
|---|---|---|---|---|---|---|---|---|---|---|
| 1.20.1 | HD_U / I6 | `OptiFine_1.20.1_HD_U_I6.jar` | 7,145,205 | `20231221-120401` | installer | 仅 `META-INF/mods.toml` | `cpw.mods.modlauncher.api.ITransformationService` | 412 / 411 xdelta，同名 6 | **SRG 成员名**（`f_127499_`） | `optifine.OptiFineTransformationService` |
| 1.20.2 | HD_U_I7 / pre1 | `preview_OptiFine_1.20.2_HD_U_I7_pre1.jar` | 7,192,269 | `20231221-121621` | installer | 仅 `mods.toml` | 同上 | 423 / 422，同名 6 | **SRG 成员名** | 同上 |
| 1.20.4 | HD_U / I7 | `OptiFine_1.20.4_HD_U_I7.jar` | 7,232,045 | `20240317-172634` | installer | 仅 `mods.toml` | 同上 | 427 / 426，同名 6 | **SRG 成员名** | 同上 |
| 1.20.6 | HD_U_J1 / pre18 | `preview_OptiFine_1.20.6_HD_U_J1_pre18.jar` | 7,330,328 | `20240927-190741` | installer | 仅 `mods.toml` | 同上 | 426 / 425，同名 6 | **官方名（Mojang）**，SRG 命中 0/40 | 同上 |
| 1.21.1 | HD_U / J1 | `OptiFine_1.21.1_HD_U_J1.jar` | 7,322,249 | `20240924-134746` | installer | 仅 `mods.toml` | 同上 | 425 / 424，同名 6 | **官方名** | 同上 |
| 1.21.11 | HD_U / J9 | `OptiFine_1.21.11_HD_U_J9.jar` | 8,045,116 | `20260205-190839` | installer | 仅 `mods.toml` | 同上 | 568 / 567，同名 13 | **官方名** | 同上 |
| 26.1.2 | HD_U_K1 / pre2 | `preview_OptiFine_26.1.2_HD_U_K1_pre2.jar` | 7,797,229 | `20260622-221942` | installer | `mods.toml` + `FMLModType: LIBRARY` | 3 个（含 2 个 NeoForge SPI） | 566 / 566，**566 份同名且字节完全相同** | **官方名** | `optifine.OptiFineTransformationService` + `optifine.OptiFineClassProcessor` |

说明与校正：

- **父 agent 转述的"xdelta 负载只在 `patch/srg/`"需要修正**：**每一份构建都同时存在 `patch/srg/`、`patch/notch/`、`patch/assets/` 三棵子树**（例 1.20.1：`patch/srg`=824 条、`patch/notch`=822 条、`patch/assets`=3242 条，`patch/` 合计 4888 条）。`patch/notch/` 的文件名在 ≤1.21.11 是**混淆名**（`patch/notch/o.class.xdelta`、`patch/notch/ama$a.class.xdelta`），在 26.1.2 与 `patch/srg` 完全同名。
- 父 agent 的其余四条（全是 installer 形态、只有 `mods.toml`、OptiFine 自身类在 `notch/` 与 `srg/` 各有一份、游戏类只在 `notch/` 下，`net/optifine/Config.class` 两种变体都有）与本次观察一致；本次补充：仅 `notch/net/minecraft/client/ClientBrandRetriever.class` 一个游戏类出现在 `notch/` 顶层（其余游戏类只在 `patch/**` 里以 xdelta 形式存在）。
- 提示里给出的 1.21.11 已知件是 `preview_OptiFine_1.21.11_HD_U_J9.jar`（8,045,105 bytes），我检查的是发布件 `OptiFine_1.21.11_HD_U_J9.jar`（8,045,116 bytes）。两者相差 11 字节，**不能确认它们内容相同**（构建清单里 J9 同时有 pre1..pre4 与发布 J9）。

---

## 2. 问题 2（重点）：补丁负载瞄准哪个命名空间

### 2.1 `OptiFineTransformer` 的常量（`javap -p -constants` 逐字读出）

1.20.1 / 1.21.1 / 1.21.11 的 `optifine.OptiFineTransformer`：

```java
public static final java.lang.String PREFIX_SRG          = "srg/";
public static final java.lang.String SUFFIX_CLASS        = ".class";
public static final java.lang.String PREFIX_PATCH_SRG    = "patch/srg/";
public static final java.lang.String SUFFIX_CLASS_XDELTA = ".class.xdelta";
public static final java.lang.String PREFIX_OPTIFINE     = "optifine/";
```

26.1.2 把这些常量**搬家**到父类 `optifine.OptiFineBaseTransformer`（`OptiFineTransformer extends OptiFineBaseTransformer`，自身不再声明常量）；常量名与取值完全相同（`javap -v` 里可见 `ConstantValue: String srg/`、`ConstantValue: String patch/srg/`）。

**没有任何 `PREFIX_PATCH_NOTCH` / `notch/` 常量**——运行期只认 SRG 变体。

### 2.2 `patchMap` / `patterns` 与文件名模式

- `OptiFineTransformer.<init>(ZipFile, IEnvironment)` 内调用 `optifine.Patcher.getConfigurationMap(ZipFile)` → `getConfigurationPatterns(Map)`；由 `javap -c` 的常量池可见 `Methodref optifine/Patcher.getConfigurationMap:(Ljava/util/zip/ZipFile;)Ljava/util/Map;` 与 `getConfigurationPatterns:(Ljava/util/Map;)[Ljava/util/regex/Pattern;`。
- `patterns` 来自 `patch.cfg` / `patch2.cfg` / `patch3.cfg` 中 `键=值` 行的**键**（正则），`patchMap` 是同一行的**值**。`Patcher.getPatchBase(String, Pattern[], Map)` 用 `patterns` 匹配补丁逻辑名，命中后取 `patchMap` 的值作为**基包中的真实条目名**。
- 文件名模式（逐字，全版本一致）：`patch/srg/<Class>.class.xdelta` 配 `patch/srg/<Class>.class.md5`；`patch/notch/<Name>.class.xdelta` 配同名 `.md5`；`patch/assets/**.json.xdelta` 配 `.md5`。`.md5` 内容是打补丁后结果的 ASCII 十六进制 MD5（`Patcher.process` 里用 `optifine/HashUtils.getHashMd5` + `toHexString` 比对，不匹配抛 `MD5 not matching, name:`）。

### 2.3 目标类名与"哪种变体被真正使用"

`targets()` 的字节码（1.21.11）：

1. `getResourceNames("srg/", ".class")` 与 `getResourceNames("patch/srg/", ".class.xdelta")` 取并集；
2. `Utils.removePrefix(name, {"srg/", "patch/srg/"})`、`Utils.removeSuffix(name, {".class", ".class.xdelta"})`；
3. 丢弃以 `net/optifine/` 开头的名字；
4. 其余每个名字交给 `getTargetClass(String)`。

`getTargetClass` → 若 `hasTargetPreClass` 为真则 `ITransformer$Target.targetPreClass(name)`，否则 `Target.targetClass(name)`。`hasTargetPreClass(IEnvironment)` 读 `IEnvironment.Keys.MLSPEC_VERSION`（`IEnvironment$Keys.MLSPEC_VERSION`），取版本串第一段 `parseInt`，**`>= 7` 才为真**。

`transform(ClassNode, ITransformerVotingContext)`：

- `name = context.getClassName().replace('.', '/')`；
- 先试 `getOptiFineResource("srg/" + name + ".class")`：命中则 `loadClass()` + `AccessFixer.fixMemberAccess(node, 原节点)` 做**整类替换**（这是 `net/optifine/**` 自身类的路径，也是 `targets()` 排除 `net/optifine/` 的原因）；
- 未命中才走 xdelta 路径（`patch/srg/...class.xdelta`）。

**谁的补丁前缀被谁读取（对 `optifine/*.class` 全量扫描 ASCII 常量，三份构建结果一致）**：

| 类 | 引用到的前缀 |
|---|---|
| `optifine/Installer.class` | `notch/` |
| `optifine/OptiFineClassTransformer.class` | `notch/` |
| `optifine/OptiFineTransformationService.class` | `srg/` |
| `optifine/OptiFineTransformer.class` | `patch/srg` + `srg/` |
| `optifine/OptiFineBaseTransformer.class`（仅 26.1.2） | `patch/srg` + `srg/` |

→ **ModLauncher/NeoForge 路线只消耗 `srg/` + `patch/srg/`；`notch/` + `patch/notch/` 属于 installer / LaunchWrapper 路线**（与 `MANIFEST.MF` 的 `TweakClass: optifine.OptiFineForgeTweaker` 对应）。26.1.2 的 `OptiFineTransformer` 与 `OptiFineBaseTransformer` **两份都出现**，说明旧路径保留、新路径并存。

### 2.4 负载内容实证：命名空间逐版本探测

对每个 `patch/srg/net/minecraft/*.class.xdelta` 抽 40 份，把负载解码成可打印 ASCII 串，再匹配 SRG 成员名正则 `\b[fm]_\d+_\b`：

| 版本 | `patch/srg` 抽样 40 份中含 SRG 成员名 | 样例（`patch/srg/net/minecraft/CrashReport.class.xdelta` 内可见串） |
|---|---|---|
| 1.20.1 I6 | **38 / 40** | `net/minecraft/CrashReport`、`CrashReport.java`、`f_127499_`、`f_241641_`、`f_127500_`、`Ljava/util/List;` |
| 1.20.2 I7_pre1 | **35 / 40** | `f_127499_`、`f_241641_`、`f_127500_`、`f_127503_` |
| 1.20.4 I7 | **35 / 40** | `f_127499_`、`f_241641_`、`f_127500_` |
| 1.20.6 J1_pre18 | **0 / 40** | `LOGGER`、`DATE_TIME_FORMATTER`、`title`、`details`、`saveFile`、`trackingStackTrace`、`systemReport` |
| 1.21.1 J1 | **0 / 40** | 同上（官方名） |
| 1.21.11 J9 | **0 / 40** | 同上（官方名） |
| 26.1.2 K1_pre2 | **0 / 40** | `reported`、`title`、`saveFile`、`net/optifine/reflect/Reflector`（官方名） |

同样的探测用在 **OptiFine 自身类** `srg/net/optifine/*.class`（抽样 60 份）上，得到**完全相同的分界**：1.20.1/1.20.2/1.20.4 = 39/60 含 SRG 成员名；1.20.6/1.21.1/1.21.11/26.1.2 = **0/60**。
→ 结论：**1.20.4 及以前，OptiFine 打包的一切（补丁结果 + 自身类）都是 SRG 成员名；1.20.6 起全部是 Mojang 官方名。**

另外用 `patch2.cfg`（SRG 变体的基包映射）交叉验证基包命名空间：

- 1.20.1：`srg/com/mojang/blaze3d/pipeline/RenderTarget.class=egv.class`（基包条目是**混淆名**）
- 1.21.1：`srg/com/mojang/blaze3d/pipeline/RenderTarget.class=ezv.class`（同上）
- 1.21.11：`srg/com/mojang/blaze3d/buffers/GpuBuffer.class=com/mojang/blaze3d/buffers/GpuBuffer.class`（同名）
- 26.1.2：`srg/com/mojang/blaze3d/buffers/GpuBuffer.class=com/mojang/blaze3d/buffers/GpuBuffer.class`（同名）
- `patch.cfg` 映射规则（三份都一样，26.1.2 多一条）：`notch/([a-z$0-9]+.class)=$1`、`notch/(com/mojang/.*.class)=$1`、**26.1.2 额外有 `notch/(net/minecraft/.*.class)=$1`**
- `patch/notch/*` 中混淆名条目数：1.20.1 `390/411`、1.21.1 `403/424`、1.21.11 `536/567`、**26.1.2 `0/566`**（全部是 `net/...`、`com/...`）

→ 26.1.2 的 `patch/notch` 键已经是去混淆类名，配合"`patch/srg` 与 `patch/notch` 566 份**字节完全相同**"，可判定该版本的基包是去混淆的、且 SRG 与官方**类名相等**。
（**UNCONFIRMED**：我未下载/检查 26.1.2 的原版 client jar，因此"Mojang 在 26.1.2 发布去混淆 jar"只是从上述补丁键与映射推得，未见 jar 本身。）

### 2.5 对"是否需要 SRG→官方 重映射"的直接回答

| MC 版本 | `patch/srg` 补丁结果 | `srg/net/optifine/**` 自身类 | 结论 |
|---|---|---|---|
| 1.20.1 / 1.20.2 / 1.20.4 | **SRG 成员名** | **SRG 成员名** | 若目标运行时用官方名（NeoForge 20.2+），必须做 SRG→official 重映射（需要该版本 SRG↔official 映射表） |
| 1.20.6 / 1.21.1 / 1.21.11 / 26.1.2 | 官方名 | 官方名 | 无需成员级重映射；只需类名/元数据层面的适配 |

---

## 3. 问题 1：jar 布局

**顶层条目（7 份构建同构）**：`patch/`、`notch/`、`srg/`、`assets/`、`optifine/`、`doc/`，加 9 个根文件 `patch.cfg`、`patch2.cfg`、`buildof.txt`、`changelog.txt`、`files.txt`、`flattening_ids.txt`、`ToCheck.txt`、`launchwrapper-of-2.3.jar`、`launchwrapper-of.txt`。

| 前缀 | 含义 | 1.20.1 | 1.21.1 | 1.21.11 | 26.1.2 |
|---|---|---|---|---|---|
| `patch/` | xdelta 补丁包（`srg`/`notch`/`assets` 三棵子树） | 4888 | 4924 | 5496 | 5490 |
| `notch/` | 混淆名命名空间下 OptiFine 自身类（含 `notch/net/minecraftforge/**`） | 703 | 736 | 827 | 829 |
| `srg/` | SRG/官方名命名空间下 OptiFine 自身类（`srg/net/optifine/**`） | 635 | 660 | 756 | 759 |
| `assets/` | 资源（本次未逐条分类，**UNCONFIRMED**） | 166 | 174 | 172 | 172 |
| `optifine/` | 引导/安装器/transformer 类 | 50 | 49 | 49 | 53 |
| `doc/` | 39 条（未逐条检查，**UNCONFIRMED**） | 39 | 39 | 39 | 39 |

**`META-INF`**：全部 7 份都只有 `MANIFEST.MF` + `mods.toml` + services 目录；**没有任何一份含 `META-INF/neoforge.mods.toml`**（与父 agent 结论一致）。

全部 `META-INF/services/*` 清单（逐字）：

| 版本 | service 文件 | 内容 |
|---|---|---|
| 1.20.1 / 1.20.2 / 1.20.4 / 1.20.6 / 1.21.1 / 1.21.11 | `META-INF/services/cpw.mods.modlauncher.api.ITransformationService` | `optifine.OptiFineTransformationService` |
| 26.1.2 | 同上 | 同上 |
| 26.1.2 | `META-INF/services/net.neoforged.neoforgespi.transformation.ClassProcessor` | `optifine.OptiFineClassProcessor` |
| 26.1.2 | `META-INF/services/net.neoforged.neoforgespi.locating.IModFileCandidateLocator` | `optifine.OptiFineClassProcessor` |

**`MANIFEST.MF` 字段**（1.20.1 与 26.1.2 完全相同，除最后一行）：

```
Manifest-Version: 1.0
Ant-Version: Apache Ant 1.9.14
Created-By: 1.8.0_121-b13 (Oracle Corporation)
Automatic-Module-Name: optifine
Main-Class: optifine.InstallerFrame
TweakClass: optifine.OptiFineForgeTweaker
TweakOrder: -1000
FMLModType: LIBRARY        <-- 仅 26.1.2 有
```

**manifest 里没有构建时间戳**——"build timestamp" 只能取根文件 `buildof.txt`（形如 `20260622-221942`）或 zip 条目 mtime（两者一致，例：26.1.2 的 `META-INF/mods.toml` mtime = `2026-06-22 22:19:58`）。

`META-INF/mods.toml` 在全部 7 份里内容同构（注释极多）：`modLoader="javafml"`、`loaderVersion="[14,)"`、`license="All rights reserved"`、`[[mods]]`、`modId="optifine"`、`version="1.0.0"`、`displayName="OptiFine"`、`displayURL="https://optifine.net"`、`authors="sp614x"`；`[[dependencies.optifine]]` 段**整段被注释掉**。→ 该文件在 1.20.1..26.1.2 之间**没有随版本变化**。

`optifine/Installer.class` **7 份全有**，且 `Main-Class: optifine.InstallerFrame` 都在 → **每一份下载到的 jar 都是 installer 形态**（同时又是可当 mod 用的 jar）。

---

## 4. 问题 3：运行时 Forge / NeoForge 触点

对 `srg/**` 与 `optifine/**` 下所有 `.class` 做常量池 ASCII 扫描（子串匹配，**无法区分 `net/minecraftforge/...` 与 `notch/net/minecraftforge/...`**）：

| 版本 | 引用 `net/minecraftforge` 的类 | 引用 `net/neoforged` 的类 | 引用 `net/minecraft/launchwrapper` | 引用 `cpw/mods/modlauncher` |
|---|---|---|---|---|
| 1.20.1 I6 | 2：`srg/net/optifine/reflect/Reflector`、`srg/net/optifine/reflect/ReflectorForge` | 0 | 3：`optifine/OptiFineClassTransformer`、`OptiFineForgeTweaker`、`OptiFineTweaker` | 3：`optifine/OptiFineJar`、`OptiFineTransformationService`、`OptiFineTransformer` |
| 1.21.1 J1 | 3：`Reflector`、`ReflectorForge`、`srg/net/optifine/shaders/ShadersRender` | 0 | 同上 3 | 2：`OptiFineTransformationService`、`OptiFineTransformer` |
| 1.21.11 J9 | 3：`Reflector`、`ShadersRender`、`srg/net/optifine/util/ResUtils`（**无 `ReflectorForge`**） | 0 | 同上 3 | 同上 2 |
| 26.1.2 K1_pre2 | 3：`Reflector`、`ShadersRender`、`ResUtils` | **2：`optifine/OptiFineClassProcessor`、`optifine/VirtualJarContents`** | 同上 3 | 同上 2 |

- `optifine.Patcher` **不在任何一行里** → 它不依赖 ModLauncher / Forge / LaunchWrapper（见问题 4）。
- `optifine/reflect/ReflectorForge.class` 在**顶层 `optifine/` 下不存在**（任何版本）；它只以 `srg/net/optifine/reflect/ReflectorForge.class` 和 `notch/net/optifine/reflect/ReflectorForge.class` 两种变体存在（提示中"jar 携带 `optifine/reflect/ReflectorForge`"应理解为后两者；`srg` 变体 7 份全有，`notch` 变体 7 份也全有）。
- **ReflectorForge 是否纯反射**：1.21.11 的 `srg/net/optifine/reflect/ReflectorForge.class`（11,221 bytes）常量池里**一个 `net/minecraftforge` 都没有**，只调用 `net/optifine/reflect/Reflector*` 全家（`ReflectorClass.exists`、`Reflector.getFieldValue`、`Reflector.call`、`ReflectorClass.<init>(Ljava/lang/Class;)V`）→ 该版本**纯反射、Forge 缺失可存活**。而 1.20.1 的同名类（15,327 bytes）含 3 处 `net/minecraftforge` 子串（**UNCONFIRMED**：未对 1.20.1 该类做完整常量池 dump，无法判断是类名字符串还是有硬 `Fieldref`/`Methodref`）。
- **硬链接证据（`javap -v` 常量池逐行）**：1.21.11 `net.optifine.shaders.ShadersRender` 里有
  `#21 = Utf8 net/minecraftforge/client/event/ViewportEvent$ComputeCameraAngles`、`#22 = Class #21`、`#23 = Utf8 net/minecraftforge/client/event/ViewportEvent`、`#24 = Class #23`
  → 这是**对 Forge 类的硬 `Class` 常量**（不是反射探测），NeoForge 下该 FQN 不存在（NeoForge 改名为 `net.neoforged.neoforge.*`），一旦该方法被执行就有链接失败风险。
- 反射探测型证据：1.21.11 `net.optifine.reflect.Reflector` 常量池里是 `Utf8 MinecraftForge`（供 `Class.forName` 用的字符串）。
- `notch/net/minecraftforge/**`：1.20.1 = 67 个类 / 53,355 bytes，1.21.1 = 75 / 62,323，1.21.11 = 70 / 55,864，26.1.2 = 70 / 64,430；单类最小 138–146 bytes、最大 2657–5477 bytes。→ 体量上**不可能**是完整 Forge API 实现，绝大多是极小的垫片/接口类（**UNCONFIRMED**：未逐个反编译确认哪些是空壳、哪些有真实实现）。它们被 OptiFine 自身代码引用（`srg/net/optifine/**` 常量池里出现 `net/minecraftforge` 子串即为佐证，但见上面的子串歧义说明）。

---

## 5. 问题 4（重点）：patcher

### 5.1 公共 API（1.20.1 / 1.21.1 / 26.1.2 三份 `javap -p` 输出**逐字相同**）

```java
public class optifine.Patcher {
  public static final java.lang.String CONFIG_FILE;    // = "patch.cfg"
  public static final java.lang.String CONFIG_FILE2;   // = "patch2.cfg"
  public static final java.lang.String CONFIG_FILE3;   // = "patch3.cfg"
  public static final java.lang.String PREFIX_PATCH;   // = "patch/"
  public static final java.lang.String SUFFIX_DELTA;   // = ".xdelta"
  public static final java.lang.String SUFFIX_MD5;     // = ".md5"
  public optifine.Patcher();
  public static void main(java.lang.String[]) throws java.lang.Exception;
  public static void process(java.io.File, java.io.File, java.io.File) throws java.lang.Exception;
  public static byte[] applyPatch(java.lang.String, byte[], java.util.regex.Pattern[], java.util.Map<java.lang.String, java.lang.String>, optifine.IResourceProvider) throws java.io.IOException, optifine.xdelta.PatchException;
  public static java.util.regex.Pattern[] getConfigurationPatterns(java.util.Map<java.lang.String, java.lang.String>);
  public static java.util.Map<java.lang.String, java.lang.String> getConfigurationMap(java.util.zip.ZipFile) throws java.io.IOException;
  public static java.util.Map<java.lang.String, java.lang.String> getConfigurationMap(java.util.zip.ZipFile, java.lang.String) throws java.io.IOException;
  public static java.lang.String getPatchBase(java.lang.String, java.util.regex.Pattern[], java.util.Map<java.lang.String, java.lang.String>);
}
```

（`CONFIG_FILE*` / `PREFIX_PATCH` / `SUFFIX_*` 是 `static final` 但非编译期常量，`javap -constants` 不给值；`"patch.cfg"`/`"patch2.cfg"`/`"patch3.cfg"`/`"patch/"`/`".xdelta"`/`".md5"` 均在 `getConfigurationMap`/`applyPatch`/`process` 的 `javap -c` 字符串常量里逐字可见。）

### 5.2 `process(File, File, File)` 的输入/输出（从字节码读出）

`main` 里的用法串逐字为：`Usage: Patcher <base.jar> <diff.jar> <mod.jar>`。`process` 的执行序列：

1. `ZipFile(arg1)` → `getConfigurationMap(ZipFile)` → `getConfigurationPatterns(Map)`；**配置文件来自 arg1** → **arg1 = diff jar = OptiFine jar 本体**。
2. `new ZipOutputStream(new FileOutputStream(arg2))` → **arg2 = 输出 jar**。
3. `ZipFile(arg0)` + `new ZipResourceProvider(ZipFile)` → **arg0 = 基包（原版客户端 jar）**。
4. 遍历 diff jar 的 `ZipEntry`：
   - `name.startsWith("patch/") && name.endsWith(".xdelta")` → 去前缀/后缀得逻辑名（如 `srg/net/minecraft/CrashReport.class`）→ `applyPatch(name, 补丁字节, patterns, patchMap, 基包 provider)` → 与 `patch/<name>.md5`（ASCII）比 MD5，不等则 `throw new Exception("MD5 not matching, name:" + ...)` → `putNextEntry(new ZipEntry(...))` + `write(byte[])` + `closeEntry()`，并 `Utils.dbg("Mod:" + name)`；
   - `name.startsWith("patch/") && name.endsWith(".md5")` → 原样复制到输出，`Utils.dbg("Same:" + name)`；
   - 其它条目：字节码里只看到跳到循环尾（**未观察到写出**）→ **输出 jar 只含补丁产物与 `.md5`**；"diff jar 的非 patch/ 条目是否也被复制" 我未能从字节码完全确认（**UNCONFIRMED**）。
5. `applyPatch` 内部：`Utils.removePrefix(name, "/")` → `getPatchBase(name, patterns, patchMap)`（解析不到抛 `IOException("No patch base, name:")`）→ 从 `IResourceProvider.getResourceStream(baseName)` 取基类字节（缺失抛 `IOException("Base resource not found:")`）→ **`new optifine.xdelta.GDiffPatcher(补丁字节, 基类 InputStream, 输出 OutputStream)`** → 返回补丁后的类字节。

### 5.3 是否需要混淆客户端 jar

**需要，且这正是 `patch/notch` 存在的理由**：`patch.cfg` 的规则 `notch/([a-z$0-9]+.class)=$1` 把逻辑名映射回**混淆条目名**；`patch2.cfg` 在 1.20.1/1.21.1 里把 `srg/com/mojang/blaze3d/pipeline/RenderTarget.class` 映射到 `egv.class`/`ezv.class`。也就是说 **xdelta 的 source 是原版客户端 jar 里的类字节**（`patch/srg` 变体的 source 也是混淆类，只是产物是去混淆/SRG 命名的类）。
26.1.2 的映射变成同名（`...GpuBuffer.class=...GpuBuffer.class`）且 `patch/notch` 无混淆条目 → 该版本基包是去混淆的。

### 5.4 是否由 xdelta 驱动 / xdelta 辅助类清单

**是 xdelta（GDiff 格式）驱动，不是整类替换**：`applyPatch` 唯一的补丁引擎是 `optifine.xdelta.GDiffPatcher(byte[], InputStream, OutputStream)`。`optifine/xdelta/` 包（1.20.1 与 1.21.11 条目清单完全相同）：

```
optifine/xdelta/BitArray.class              optifine/xdelta/EratosthenesPrimes.class
optifine/xdelta/ByteArraySeekableSource.class  optifine/xdelta/GDiffPatcher.class
optifine/xdelta/Checksum.class                 optifine/xdelta/GDiffWriter.class
optifine/xdelta/DebugDiffWriter.class          optifine/xdelta/PatchException.class
optifine/xdelta/Delta.class                    optifine/xdelta/RandomAccessFileSeekableSource.class
optifine/xdelta/DeltaException.class           optifine/xdelta/SeekableSource.class
optifine/xdelta/DiffWriter.class               optifine/xdelta/SeekableSourceInputStream.class
optifine/xdelta/licence.txt                    optifine/xdelta/SimplePrime.class
```

（"整类替换"只发生在**另一条路径**上：ModLauncher transformer 先找 `srg/<class>.class`，命中就整类替换，那是 OptiFine 自身 `net/optifine/**` 的类，不是游戏类。）

### 5.5 能否被我们自己的代码独立调用

**能，而且是推荐做法。** 证据：`optifine.Patcher.class` 的常量池里**不含** `cpw/mods/modlauncher`、`net/minecraftforge`、`net/neoforged`、`net/minecraft/launchwrapper`（问题 3 的逐类扫描结果）；它只依赖 `optifine.xdelta.*`、`optifine.Utils`、`optifine.HashUtils`、`optifine.ZipResourceProvider`、`optifine.IResourceProvider`、`optifine.IOptiFineResourceLocator`——全部在同 jar 内。

给 loader 的两点实操含义：

- **不必调用 `process`**（它是"整包安装器"，输出一个完整 jar）。更合适的是逐类调 `Patcher.applyPatch(String logicalName, byte[] delta, Pattern[] patterns, Map<String,String> patchMap, IResourceProvider baseProvider)`（public static），自己掌控输出（内存/目标 jar）。
- 只需要把 OptiFine jar 用 `URLClassLoader`（或自定义 ClassLoader）加载并调用其静态方法；不需要 Forge/NeoForge 类在 classpath 上。这与 OptiFabric 的做法一致（OptiFabric 在 Fabric 上自己跑 patcher）。

---

## 6. 问题 5：逐版本差异（(a) installer/预抽 mod jar (b) mods.toml/neoforge.mods.toml (c) transformation service (d) 补丁存储 (e) 大小与构建时间）

- **(a)** 7 份**全部是 installer 形态**：都含 `patch/`、`optifine/Installer.class`，`MANIFEST.MF` 的 `Main-Class` 都是 `optifine.InstallerFrame`，`TweakClass` 都是 `optifine.OptiFineForgeTweaker`。没有观察到"预抽好的 mod jar"形态。
- **(b)** 7 份**全部只有 `META-INF/mods.toml`**；`neoforge.mods.toml` **一份都没有**。26.1.2 额外在 `MANIFEST.MF` 里加了 `FMLModType: LIBRARY`。
- **(c)** `optifine.OptiFineTransformationService` **在所有 7 份里都存在且类名不变**（1.20.1 时它直接 `implements cpw.mods.modlauncher.api.ITransformationService`；26.1.2 时改为 `extends optifine.OptiFineBaseTransformerService implements ITransformationService`）。26.1.2 另外新增 `optifine/OptiFineClassProcessor.class`（`extends OptiFineBaseTransformerService implements net.neoforged.neoforgespi.transformation.ClassProcessor, net.neoforged.neoforgespi.locating.IModFileCandidateLocator`）、`optifine/OptiFineBaseTransformer.class`、`optifine/OptiFineBaseTransformerService.class`、`optifine/VirtualJarContents.class`（`implements net.neoforged.fml.jarcontents.JarContents`）；1.20.1 独有 `optifine/OptiFineJar.class`（后续版本消失）。
- **(d)** 补丁存储结构没变（`patch/{srg,notch,assets}/**.xdelta` + `.md5`，`patch.cfg`/`patch2.cfg`/`patch3.cfg`），但**内容命名空间在 1.20.6 变了**（第 2.4 节），**26.1.2 让 srg/notch 两份负载字节趋同**（566/566 全同）。
- **(e)** 见第 1 节表：大小 7.14 MB（1.20.1）→ 7.32 MB（1.21.1）→ 8.05 MB（1.21.11）→ 7.80 MB（26.1.2）；`buildof.txt` 依次 `20231221-120401`、`20231221-121621`、`20240317-172634`、`20240927-190741`、`20240924-134746`、`20260205-190839`、`20260622-221942`。**manifest 无时间戳**，上述时间取自根文件 `buildof.txt`（与 zip 条目 mtime 一致）。

### 26.1.2 的 NeoForge 新路径（`javap -c` 读出）

- `optifine.OptiFineBaseTransformer.getSrgClassPath(String)` 的字节码 = `name.replace('.','/')` 后与常量拼接，`BootstrapMethods` 里的 recipe 逐字为 **`srg/\u0001.class`** → 即 `"srg/" + name.replace('.','/') + ".class"`。
- `OptiFineClassProcessor.handlesClass(SelectionContext)`：`getSrgClassPath(ctx.type().getClassName())` → `getOptiFineResourceStream(该路径)`，非 null 返回 `true`。
- `OptiFineClassProcessor.processClass(TransformationContext)`：`OptiFineBaseTransformer.transform(ctx.node(), ctx.type().getClassName())` → `copyClassNode(newNode, ctx.node())` → 返回 `ClassProcessor$ComputeFlags.COMPUTE_FRAMES`。
- `findCandidates(ILaunchContext, IDiscoveryPipeline)` 里出现字符串 `OptiFineClassProcessor.init()`、`optifine`、`class_processor`、`create`、`true`、**`/srg`**（配合 `VirtualJarContents`，即把 `srg/` 那棵树作为虚拟 jar 内容暴露给 NeoForge）——**具体注册语义未逐条反汇编确认（UNCONFIRMED）**。

---

## 7. 问题 6（重点）：逐版本判定

前提（本项目待办、与 jar 无关）：NeoForge 侧要用 `neoforge.mods.toml` 才能识别为 mod；本次检查的所有构建都没有该文件，所以**无论哪个版本，"元数据修好"这一步都必需**。
（**UNCONFIRMED**：本次未检查 NeoForge 自身实现对 `mods.toml` 的处理；建议 loader 两种文件名都注入。）

| 版本 | OptiFine 自带 `ITransformationService` 能否原样跑在 NeoForge 上？ | 必须我们自己跑 `Patcher` 并注入补丁类的场景 | 具体会坏在哪 |
|---|---|---|---|
| 1.20.1 (I6) | **可能可以**（for NeoForge 20.1 这类 SRG 运行时的分支）——补丁与自身类都是 SRG 成员名，与 SRG 运行时同族（**UNCONFIRMED**：未验证 NeoForge 20.1 的运行时命名） | 若目标运行时用官方名，则不能只靠自带 transformer | 元数据（无 `neoforge.mods.toml`）；`MLSPEC_VERSION` 分支（`>=7` 走 `targetPreClass`）在目标环境的真实值未验证 |
| 1.20.2 (I7_pre1) | **不能**（若 NeoForge 20.2 用官方名） | **必须**：`patch/srg/**` 与 `srg/net/optifine/**` 都是 SRG 成员名，需 SRG→official 重映射后再注入 | 命名空间不匹配（`f_127499_` / `m_137502_` 在官方名下不存在）；元数据 |
| 1.20.4 (I7) | **不能**（同上） | **必须**：同 1.20.2 | 同上 |
| 1.20.6 (J1_pre18) | **值得一试**：补丁与自身类已是官方名，理论上可直接由自带 transformer 在内存中打补丁 | 备选：自己跑 `Patcher.applyPatch` 注入 | 元数据；Forge 类名硬链接（`net/minecraftforge/**`）；`MLSPEC`/target 阶段语义未验证 |
| 1.21.1 (J1) | 同上（官方名） | 同上 | 同上 |
| 1.21.11 (J9) | 同上（官方名） | 同上 | 同上；`ShadersRender` 里对 `net/minecraftforge/client/event/ViewportEvent$ComputeCameraAngles` 的硬 `Class` 常量在 NeoForge 下不存在 |
| 26.1.2 (K1_pre2) | **最有可能**：自带 `OptiFineClassProcessor` 已经面向 NeoForge 的 `ClassProcessor`/`IModFileCandidateLocator` SPI 编写，且补丁是官方名、`patch/srg` == `patch/notch` | 一般不需要自己跑 patcher | 元数据（`mods.toml` vs `neoforge.mods.toml`；不过它已带 `FMLModType: LIBRARY`）；NeoForge SPI 版本兼容性未验证 |

**逐版本"会坏在哪"的公共项**（所有版本）：

1. **元数据**：只有 `mods.toml`，没有 `neoforge.mods.toml`。
2. **`TweakClass: optifine.OptiFineForgeTweaker`**：`optifine/OptiFineForgeTweaker.class` `implements net.minecraft.launchwrapper.ITweaker`，NeoForge 不提供 launchwrapper（jar 自带根文件 `launchwrapper-of-2.3.jar`）。只要 NeoForge 不解析 `TweakClass`，它就只是惰性残留（**UNCONFIRMED**：未验证 NeoForge 是否忽略该 manifest 字段）。
3. **Forge 类硬链接**：`srg/net/optifine/shaders/ShadersRender` 等类里有 `net/minecraftforge/**` 的硬 `Class` 常量（1.21.11 已核实），NeoForge 改包名为 `net.neoforged.neoforge.**`。
4. **`notch/` + `patch/notch/` 在 NeoForge 路线下不会被使用**（transformer 只认 `srg/` + `patch/srg/`），因此"游戏类只在 `notch/` 下出现了 1 个"这件事不影响 mods/ 路线。

---

## 8. 对 loader（OptifiNeoforge）的意义

1. **两条路线要分开设计**：
   - 路线 A（复用 OptiFine 自带 `OptiFineTransformationService`）：只需修元数据（注入 `neoforge.mods.toml` / `FMLModType`），让 ModLauncher 的 transformer 自己用 `patch/srg/**` 在内存里打补丁。前提是**目标运行时的类字节与补丁 source 同族**（1.20.6+ 官方名，成立；1.20.1 需 SRG 运行时）。
   - 路线 B（自己跑 patcher，OptiFabric 风格）：用 `URLClassLoader` 加载 OptiFine jar，调 `optifine.Patcher.getConfigurationMap(ZipFile)` + `getConfigurationPatterns(Map)` + **逐类** `optifine.Patcher.applyPatch(String, byte[], Pattern[], Map, IResourceProvider)`，补丁字节从 `patch/srg/<Class>.class.xdelta` 读取，source 从我们提供的类字节读。`process()` 不必用。
2. **1.20.1/1.20.2/1.20.4 是硬骨头**：OptiFine 给的东西全是 SRG 成员名。要在官方名运行时上用，必须有 SRG↔official 映射并在应用补丁后再做一次成员重映射（或改用运行时即 SRG 的方案）。1.20.6 起该问题消失。
3. **26.1.2 是另一个物种**：它自带 NeoForge SPI 实现（`ClassProcessor` + `IModFileCandidateLocator` + `VirtualJarContents`），目标是 NeoForge 自己的类处理管线，而不是 ModLauncher 的 `ITransformer`；两条路径（`OptiFineTransformationService` 与 `OptiFineClassProcessor`）在同一 jar 里并存。
4. **最小可行第一步**：对 1.20.6+ 的版本，先只做"元数据注入 + 让自带 transformer 工作"的验证；对 1.20.1/1.20.2/1.20.4，直接规划路线 B + 重映射。
5. **可以在仓库外先做无风险实验**：`Patcher.applyPatch` 是纯静态、无 Forge/ModLauncher 依赖，先写一个独立小程序验证"补丁→类字节→`javap` 可读"这条路，再决定 transformer 复用与否。

---

## 9. UNCONFIRMED 清单（含原因）

1. **未检查的版本**：`1.21`、`1.21.3`、`1.21.6`、`1.21.7`、`1.21.8`、`1.21.9`、`1.21.10` 完全未下载检查（时间预算与 BMCLAPI 超时）；`1.21.4 HD_U_J3` 下载被截断（3,674,112 bytes，"找不到中央目录结尾记录"），**不可用**。→ 这些版本上"1.20.6 起官方名"的规律只是**外推**，未逐版验证。
2. **26.1.2 基包是否真去混淆**：结论由 `patch.cfg`/`patch2.cfg` 映射与 `patch/notch` 文件名推得，未下载 26.1.2 原版 client jar 验证。
3. **NeoForge 侧语义**：未检查任何 NeoForge jar，因此"NeoForge 20.2+ 只认 `neoforge.mods.toml`"、"NeoForge 20.1 版运行时是 SRG 名"、"NeoForge 忽略 `TweakClass`"三条均未在本会话验证（父 agent 的 scratch 目录里有 `toml_neoforge-*.jar` / `neoforge-*.jar`，可由其确认）。
4. **`MLSPEC_VERSION` 在各目标环境的实际值**：`hasTargetPreClass` 的 `>=7` 分支只从字节码读出，未在任何 ModLauncher 实现上实测。
5. **`patch/notch` 在运行期是否真的只会被 installer/LaunchWrapper 读取**：依据是 `optifine/Installer.class` 与 `optifine/OptiFineClassTransformer.class` 的常量池引用 `notch/`，未反汇编 `OptiFineClassTransformer.transform` 逐步确认其前缀拼接。
6. **`Patcher.process` 输出 jar 的完整条目集合**：字节码显示 `.xdelta` 产物与 `.md5` 被写出；非 `patch/` 条目是否复制未能完全确认。
7. **`notch/net/minecraftforge/**` 是完整实现还是空壳**：只统计了类数与体积（70 个类约 56–64 KB，单类 138–5477 bytes），未逐个反编译。
8. **1.20.1 的 `srg/net/optifine/reflect/ReflectorForge` 是否含硬 Forge 引用**：只做了子串计数（3 处），未做完整常量池 dump；1.21.11 同名类已确认 0 处、纯反射。
9. **`assets/` 与 `doc/` 两棵子树的用途**：只统计了条目数（166–174 / 39），未逐条查看。
10. **1.21.11 `OptiFine_1.21.11_HD_U_J9.jar`（本次检查，8,045,116 bytes）与提示中的 `preview_OptiFine_1.21.11_HD_U_J9.jar`（8,045,105 bytes）是否内容等价**：相差 11 字节，未对比。
11. **`OptiFineClassProcessor.findCandidates` 的注册语义**：只读到字符串常量（`OptiFineClassProcessor.init()`、`optifine`、`class_processor`、`create`、`true`、`/srg`）与 `VirtualJarContents implements net.neoforged.fml.jarcontents.JarContents` 的签名，未逐步反汇编。
