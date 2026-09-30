# OptifiNeoforge

在 **NeoForge** 上加载 **OptiFine** 的客户端模组,做法与 OptiFabric 在 Fabric Loader 上的做法相同:把 OptiFine 的 jar 和本模组一起放进 `mods/`,启动时由本模组运行 OptiFine 自带的补丁流程,并把打过补丁的 Minecraft 类接进 NeoForge 的类转换流程。

本分支是 **1.20.x 线**,覆盖 **Minecraft 1.20.1、1.20.2、1.20.4、1.20.6**(OptiFine 在这条线上出过构建的全部四个版本)。1.21.x 线(1.21 – 1.21.11)与 26.x 线(Minecraft 26.1.2)在各自的分支上独立开发,三条线的 jar 不能互相替代。

> **状态:四个版本全部实机验证,`1.0.0` 已发布(能用本分支的 Gradle 构建产出 jar 的那几条)。** 判据是**一次真实启动**(rig 的 `VERDICT: STARTED` + `Setting user` + 本次运行没有崩溃报告 + stderr 与记录一致),不是"能编译";逐条数字在 `docs/MATRIX.md`(项目的运行日志)。
>
> 两条如实写下的边界:**1.20.2 与 1.20.4 带已知的 Reflector 缺陷**(OptiFine 自己的 `Reflector` 表每次启动写 4 条 `NoClassDefFoundError` 到 stderr,异常被 OptiFine 吞掉,不阻断启动;1.20.1 与 1.20.6 没有这条);**1.20.4 的运行要求**是 `config/fml.toml` 里 `earlyWindowProvider = "none"`(否则 FML 的 early window 与 OptiFine 换装的渲染类同帧重入,`EXITED (15s)`)。

## 支持的版本

| Minecraft | NeoForge | 产物 | OptiFine 正式版 | OptiFine 最新 preview | Java | 状态(实测) |
|---|---|---|---|---|---|---|
| 1.20.1 | `net.neoforged:forge:1.20.1-47.1.106` | `OptifiNeoforge-1.0.0+mc1.20.1.jar` | `OptiFine_1.20.1_HD_U_I6.jar` | `preview_OptiFine_1.20.1_HD_U_I6_pre6.jar` | 17 | 已验证 · **未发布**(构建已通过,见下文;`[OptiFine]` 157 行、stderr 27 字节、无崩溃报告) |
| 1.20.2 | `20.2.88` | `OptifiNeoforge-1.0.0+mc1.20.2.jar` | 无 | `preview_OptiFine_1.20.2_HD_U_I7_pre1.jar` | 17 | 已验证 · **未发布**(构建已通过,见下文;`[OptiFine]` 239 行、stderr 14 625 字节、无崩溃报告) |
| 1.20.4 | `20.4.251` | `OptifiNeoforge-1.0.0+mc1.20.4.jar` | `OptiFine_1.20.4_HD_U_I7.jar` | `preview_OptiFine_1.20.4_HD_U_I8_pre4.jar` | 17 | 已验证 · 已发布(241 行、stderr 14 481 字节、无崩溃报告) |
| 1.20.6 | `20.6.141` | `OptifiNeoforge-1.0.0+mc1.20.6.jar` | 无 | `preview_OptiFine_1.20.6_HD_U_J1_pre18.jar` | 21 | 已验证 · 已发布(222 行、stderr 0 字节、无崩溃报告;这一版起 FML 拒绝原版 OptiFine jar,必须用本项目的重打包) |

两列 OptiFine 都只表示"该构建存在",不代表可用;这里也不表示正式版比 preview 更适合移植。

> **2026-09-19:四条线都已在重建的 rig 上跑过一遍。** 判据(rig 的 `VERDICT: STARTED` + `Setting user` +
> 声音引擎 + 本次运行 0 崩溃报告)在四条线上都成立;stderr 与记录的对照逐个不同,如实列在下面。
> 逐条的完整证据、被否掉的假设与 rig 侧的坑记在 `docs/MATRIX.md` 的第十八节到第三十九节。
> 1.20.1:`STARTED`、0 崩溃,**stderr 0 字节(记录 27)**、`[OptiFine]` 155 原始行(记录 157);
> 标题界面**没有**直接测到(`traceScreen` 在这条线上没有输出)。1.20.2:四条判据全绿,
> **stderr 14 625 = 记录** ✔,`setScreen` 直接确认标题界面,行数 247 对记录 239。

> **2026-09-19 在本机重建的 rig 上复现**:原始 rig 已不在本机,这一轮从零重建了一个等价 rig,用**用户自己的**
> `preview_OptiFine_1.20.6_HD_U_J1_pre18.jar` 现场生成载荷,并真的起了一次客户端。1.20.6 的判据里,`STARTED`、
> `Setting user`、0 崩溃报告与 **stderr 0 字节**都与上表**逐字一致**,但 `[OptiFine]` 行数是 **231 而不是 222**:
> 差 9 行,两次独立运行都是 231。同一台机器上 1.21 / 1.21.1 也是 +9、1.21.6 / 1.21.7 / 1.21.8 是 +7,而
> 1.21.3 / 1.21.4 与记录完全相等 —— 记录里的 `latest.log` 不在仓库里,所以多出来的是哪几行无法从这边归因。
> **1.20.2 与 1.20.4 在本机也通过了**(同一套 rig,用户自己的 OptiFine jar 现场生成载荷)。
> 1.20.2 用 `preview_OptiFine_1.20.2_HD_U_I7_pre1.jar`:`STARTED` + `Setting user` ✓ + 声音引擎 ✓ +
> **0 崩溃报告**,stderr **14 625 字节 = 记录值** ✔;标题界面由 `setScreen` 追踪确认。
> 这条线文档里被标为"最薄的一环",实际修了三处:重打包用的 OptiFine jar 也要做 SRG→官方名改名;
> 整个 `net/minecraft/Util`/`Util$*` 家族(16 个类)必须整类保留运行时的版本 —— 那个唯一的 preview 载荷内部自相矛盾
> (它的 `Util` 调 `Util$5.<init>(Path)` 而它的 `Util$5` 只声明无参构造,改名**之前**就是错的);以及文档里早写过的
> `ModelPart.getChild` 保留游戏侧实现(`Failed to create model for minecraft:skull`)。
> 1.20.4 用 `OptiFine_1.20.4_HD_U_I7.jar`:
> `VERDICT: STARTED`、`Setting user` ✓、声音引擎 ✓、**本次运行 0 崩溃报告**,stderr **14 481 字节 = 记录值**
> (三次里两次逐字节相同,另一次 14 643)。标题界面这一条由游戏自己的 `setScreen` 追踪确认(`TitleScreen`,之后没再切过界面);
> **窗口截图给的是过期帧**(两次相隔 40 秒抓帧统计完全相同,窗口未聚焦时不重绘),所以这一条不是像素确认。
> `[OptiFine]` 行数与记录差得远(730 合并值 = 365 原始 vs 记录 241),**原因未查明**。
> 走到这里要修的一串东西(顺序、SRG 改名、删除成员、访问计划、同类接口换装)与每一步的实测证据都在
> `docs/MATRIX.md` 的第十八节到第二十九节;rig 侧的坑(共用的 `natives\` 会串 LWJGL 3.3.2/3.3.3、崩溃的 JVM 会占着
> `glfw.dll`、以 `-` 开头的 JVM 参数要走 `RIG_EXTRA_JVM`)记在同一处的第二十八节。

- mod id `optifineoforge`,仅客户端。
- **一个 jar 只对应一个 MC 版本**:这条线跨了四个版本,NeoForge 坐标、Java 版本与运行期命名空间在每个版本上都不同,不能混用,也不能拿别的线的 jar 顶替。
- Java 在这条线上**不统一**:1.20.1 / 1.20.2 / 1.20.4 是 **17**,1.20.6 起是 **21**。
- NeoForge 坐标也跨了一个时代:1.20.1 是 `net.neoforged:forge:1.20.1-47.1.106`,1.20.2 起才是独立的 `net.neoforged:neoforge`(`20.x.y`)。
- **1.20.2 的 OptiFine 只有一个 preview**(`I7_pre1`);**1.20.3 与 1.20.5 没有任何 OptiFine 构建**,因此不在支持范围内。
- 四个版本合计 29 个 OptiFine 构建,逐条列在 `docs/VERSIONS.md`。
- 这条线**内部**的分界比别的线多(`docs/VERSIONS.md` 列了五处),其中落点各不相同、都要逐版本处理的这三处是:
  **FML 的包名**(1.20.1 是 `net.minecraftforge.fml` + `net.minecraftforge.eventbus.api`,1.20.2 起是
  `net.neoforged.fml` + `net.neoforged.bus.api`)、**元数据文件名**(1.20.1 – 1.20.4 读 `META-INF/mods.toml`,
  1.20.6 两个名字都读;见构建一节)、以及 **NeoForge 侧登记用的 mod id 与版本**(1.20.1 是 `forge` / `47.1.106`,
  1.20.2 起是 `neoforge` / 与坐标同名)。前两处已按实测写进构建,**第三处是本次新查出来的**:它意味着
  1.20.1 的产物如果照抄别的版本去依赖 `neoforge`,会去要一份那份发行里根本没有的 mod。
  运行期命名空间(SRG 还是官方名)**已在 2026-09-19 量出**(见 `docs/VERSIONS.md`):同一工具对同一个运行时扫两份
  载荷,1.20.4 是 43 162 条引用里 3 178 条找不到(7.4%,SRG 名),1.20.6 是 43 918 条里 23 条找不到(0.05%,
  官方名)—— 分界就在 1.20.6。

## 安装

发布的是**加载器侧**(jar 里没有任何 OptiFine 的类)。要得到能直接放进 `mods/` 的成品,按本项目的 rig 流水线
对**你自己下载的、与该 MC 版本严格一致**的 OptiFine jar 做重打包、SRG→官方名重写、打补丁与成员回填
(1.20.6 起还必须换掉 FML 拒绝的那份元数据),然后把产物放进这个版本自己的 `mods/` 目录,用对应的 **NeoForge**
版本启动;不要用启动器注入 OptiFine 的版本(那会与本模组重复)。1.20.4 上还要把 `config/fml.toml` 的
`earlyWindowProvider` 设成 `none`。上面每一条验收数字都是这样量出来的。

## 构建

需要 **JDK 17**(1.20.1 / 1.20.2 / 1.20.4)或 **JDK 21**(1.20.6),按目标版本选。仓库根目录就是 Gradle 项目,
目标版本用 `-Pmc` 切换,并且**必须**同时给出该版本的 NeoForge 与 ModLauncher 代次(`-Pneoforge` /
`-Pmodlauncher`),这样"没验证过的配对"不会看起来像支持:

```powershell
.\gradlew build                                                                            # 默认目标:1.20.4 / 20.4.251 / ML 10
.\gradlew build "-Pmc=1.20.6" "-Pneoforge=20.6.141" "-Pmodlauncher=11" "-Ptarget_java_version=21"
.\gradlew build "-Pmc=1.20.2" "-Pneoforge=20.2.88" "-Pmodlauncher=10"                      # 不走 ModDevGradle
.\gradlew build "-Pmc=1.20.1" "-Pneoforge=1.20.1-47.1.106" "-Pmodlauncher=10"              # 同上
```

**在 PowerShell 里 `-P...` 必须加引号**:不加时 `1.20.6` 会被拆开,报
`Task '.20.6' not found in root project`(Gradle 9.6.1 实测);`cmd.exe` 下不加引号也可以。

产物为 `build/libs/OptifiNeoforge-<版本>+mc<MC 版本>.jar`,例如 `OptifiNeoforge-1.0.0+mc1.20.6.jar`。
**四个目标现在都能构建。**

### 1.20.1 与 1.20.2 走另一条路(不经过 ModDevGradle)

这两条原来构建不出来,原因在 NeoForge 自己发布的元数据里,不在本项目:1.20.1 的坐标是 Forge 时代的
`net.neoforged:forge:1.20.1-47.1.106`,它的 POM 是 `packaging=pom` 且没有依赖,真正的文件带分类符
(`-universal.jar` / `-userdev.jar` / `-installer.jar`,没有不带分类符的 `.jar`,也没有 `.module`),
而 ModDevGradle 要的是 `net.neoforged:neoforge`;整个 `20.2.x` 系列则**一个都没有**发布 Gradle Module
Metadata(实测 `20.2.88` 没有 `.module`,`20.4.251` 与 `20.6.141` 有),所以那条线没有
`neoforge-moddev-bundle` 变体可解。

**这两条不需要 Minecraft**:本分支源码里没有一处 `net.minecraft` 或 `com.mojang` 的 import,只用到
ModLauncher、FML、ASM 与 log4j,所以它们的编译类路径就是一张短的显式清单,坐标取自各版 NeoForge 自己的
元数据(`20.2.88` 的 POM、`1.20.1-47.1.106` 的 userdev `config.json`)——既不下载也不反编译 Minecraft,
每次构建几秒。代价是这两条没有 `runClient` 开发运行,只做编译与打包。

### 元数据文件名、以及依赖里登记的 mod id,都随目标变

实测(读各发行自己的 `META-INF/mods.toml`,以及各版 FML loader jar 里的字面常量):

| 目标 | 该版 NeoForge 自己的元数据文件 | 它登记的 mod id | FML loader 读的名字 | 本构建写出的名字 |
|---|---|---|---|---|
| 1.20.1 (47.1.106) | `META-INF/mods.toml` | `forge` | `mods.toml` | `mods.toml` |
| 1.20.2 (20.2.88) | `META-INF/mods.toml` | `neoforge` | `mods.toml` | `mods.toml` |
| 1.20.4 (20.4.251) | `META-INF/mods.toml` | `neoforge` | `mods.toml` | `mods.toml` |
| 1.20.6 (20.6.141) | `META-INF/neoforge.mods.toml` | `neoforge` | 两个名字都读 | `neoforge.mods.toml` |

模板在源码树里叫 `META-INF/neoforge.mods.toml`,写进 jar 时按目标改名;依赖块里的 mod id 与版本同样随目标变,
1.20.1 上写 `forge` / `47.1.106`,其余三版写 `neoforge` 与该版的坐标同名。

**其中 1.20.4 这一格是本次改正的**:那份 jar 以前带的是 `neoforge.mods.toml`,而 20.4.251 的 FML 只读
`mods.toml`,所以那份元数据对它等于不存在。现在四个版本写出的名字都与该版 NeoForge 自己的产物一致。这是
**构建层面**的改正,没有重跑实机启动(手上没有 rig),所以已发布 jar 的正文说明没有跟着改。

## 工作原理(计划)

OptiFine 的 Forge 侧入口是一个 ModLauncher 服务:`META-INF/services/cpw.mods.modlauncher.api.ITransformationService` 指向 `optifine.OptiFineTransformationService`,它再通过 `optifine.OptiFineTransformer`(实现 ModLauncher 的 `ITransformer<ClassNode>`)把补丁类插进加载流程。NeoForge 同样跑在 ModLauncher 上,所以这条路可以复用,但需要处理四件事:

1. **元数据**:OptiFine 的 jar 里是 Forge 时代的 `META-INF/mods.toml`。**切换点已经实测**:1.20.1 – 1.20.4 的 FML 只读 `META-INF/mods.toml`,1.20.6 两个名字都读(见"构建"一节的表),本分支写出的名字已经按目标选;
2. **命名空间**:1.20.x 时代 Forge/NeoForge 的运行期名预期是 **SRG**,而 OptiFine 的补丁按它发布时的命名空间存放,错配会整段失效;
3. **补丁重叠**:NeoForge 自己也会改原版类,两边的改动需要按顺序合并,而不是互相顶掉;
4. **线内的版本分叉**:1.20.1 走 `net.neoforged:forge` 坐标,1.20.6 起要 JDK 21,四个版本的构建与元数据要分别配置,不能指望一份配置覆盖整条线。

设计细节与里程碑见 `docs/PLAN.md`。

## 已知限制

- **发布的是加载器侧,不是"装进 `mods/` 就能用"的成品**:Release 附的 jar 里没有 OptiFine 的类,成品要按上面的流水线用你自己的 OptiFine jar 合成。
- **1.20.3 与 1.20.5 没有 OptiFine 构建**,这两版不在支持范围内;1.20.2 只有一个 preview(`I7_pre1`),它已经实测通过(见上表),代价是那 4 条 Reflector `NoClassDefFoundError`。
- 这条线跨了 Forge 时代的 NeoForge(1.20.1)与独立的 `20.x` 系列,元数据文件名与运行期命名空间都可能在这条线**内部**发生变化,所以四个版本要分别验证,不能由一个版本的结果外推。**注意别把两件事混起来**:本次查清的是 **FML 的 API 包名**(1.20.1 是 `net.minecraftforge.fml`,1.20.2 起是 `net.neoforged.fml`),这是编译期的事;下面那条讲的是 **Minecraft 类的运行期名**(SRG 还是官方名),那是另一件事,仍未确认。
- 若 1.20.6 一侧的运行期名已经是官方名而不是 SRG,那么本线内部就需要两套命名空间处理 —— 这一侧属于待确认事项。
- 与 OptiFabric 一样,**不包含、也不分发 OptiFine 本体**:OptiFine 的 jar 由用户自行获取,本项目只把它当作补丁来源。
- **这条线的实测记录在 `docs/MATRIX.md`**(2026-09-18 的回归:四个版本各自对着自己记录的基线逐项一致)。构建列表里"存在"不等于"可用",正式版也不代表比 preview 更适配 —— 这两句仍然成立,区别只是现在每个版本都有自己的启动数字了。

## 许可与致谢

- 本项目遵循 **MPL-2.0**(`LICENSE`)。**实现为本项目自写**;与 [OptiFabric](https://github.com/Chocohead/OptiFabric)(作者 Modmuss50、Chocohead)相同的是**做法本身** —— 把 OptiFine 在运行时补进游戏 —— 这一点在此致谢,但两者是不同的加载器(Fabric Loader 对应 NeoForge)、不同的实现,本项目**不是** OptiFabric 的移植或重传。2026-09-24 逐文件核对:源码中没有来自 OptiFabric 的署名或移植片段(源码里的 "Ported from …" 均指本项目自己分支之间的移植),故按事实表述。
- **不包含、也不分发 OptiFine 本体**,OptiFine 版权归 sp614x 所有,请自行获取。
- 各版本的构建列表、NeoForge 坐标与下载命令见 `docs/VERSIONS.md`,版本号规则见 `docs/VERSIONING.md`,设计与里程碑见 `docs/PLAN.md`。
