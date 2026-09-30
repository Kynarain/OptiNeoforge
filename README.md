# OptifiNeoforge

在 **NeoForge** 上加载 **OptiFine** 的客户端模组,做法与 OptiFabric 在 Fabric Loader 上的做法相同:把 OptiFine 的 jar 和本模组一起放进 `mods/`,启动时由本模组运行 OptiFine 自带的补丁流程,并把打过补丁的 Minecraft 类接进 NeoForge 的类转换流程。

本分支是 **1.21.x 线**,覆盖 **Minecraft 1.21 – 1.21.11**(OptiFine 出过构建的全部十个版本)。1.20.x 线(1.20.1 – 1.20.6)与 26.x 线(Minecraft 26.1.2)在各自的分支上独立开发,三条线的 jar 不能互相替代。

> **状态:十个版本全部实机验证,`1.0.0` 已发布。** 判据是**一次真实启动**(rig 的 `VERDICT: STARTED` +
> `Setting user` + 本次运行没有崩溃报告 + stderr 与记录一致),不是"能编译"。逐条的实测数字在
> [`docs/MATRIX.md`](../../blob/1.20.x/docs/MATRIX.md)(项目的运行日志)里,发布的是其中能用本分支的 Gradle
> 构建产出 jar 的那几条(见每个 Release 的正文)。
>
> 两条如实写下的边界:**1.21.6 / 1.21.7 的验收不含"启用光影包"**(这两版的 OptiFine 只有预览构建,启用光影包会
> 崩在 OptiFine 自己内部,见 `docs/PLAN.md`);**1.21 带已知的 Reflector 缺陷**(OptiFine 的 `J1_pre9` 构建每次
> 启动写 4 条 `NoClassDefFoundError` 到 stderr,异常被 OptiFine 吞掉,不阻断启动)。

## 支持的版本

| Minecraft | NeoForge | 产物 | OptiFine 正式版 | OptiFine 最新 preview | Java | 状态(实测) |
|---|---|---|---|---|---|---|
| 1.21 | `21.0.167` | `OptifiNeoforge-1.0.0+mc1.21.jar` | 无 | `preview_OptiFine_1.21_HD_U_J1_pre9.jar` | 21 | 已验证 · 已发布(`[OptiFine]` 252 行、stderr 14 141 字节、无崩溃报告) |
| 1.21.1 | `21.1.250` | `OptifiNeoforge-1.0.0+mc1.21.1.jar` | `OptiFine_1.21.1_HD_U_J1.jar` | `preview_OptiFine_1.21.1_HD_U_J1_pre15.jar` | 21 | 已验证 · 已发布(223 行、stderr 0 字节、无崩溃报告;本轮本机复现四项判据一致、232 行,见下) |
| 1.21.3 | `21.3.97` | `OptifiNeoforge-1.0.0+mc1.21.3.jar` | `OptiFine_1.21.3_HD_U_J2.jar` | `preview_OptiFine_1.21.3_HD_U_J2_pre12.jar` | 21 | 已验证 · 已发布(225 行、stderr 0 字节、无崩溃报告;本轮本机复现四项判据一致、225 行,见下) |
| 1.21.4 | `21.4.149` | `OptifiNeoforge-1.0.0+mc1.21.4.jar` | `OptiFine_1.21.4_HD_U_J3.jar` | `preview_OptiFine_1.21.4_HD_U_J4_pre2.jar` | 21 | 已验证 · 已发布(232 行、stderr 0 字节、无崩溃报告;本轮本机复现四项判据一致、232 行,见下) |
| 1.21.6 | `21.6.20-beta` | `OptifiNeoforge-1.0.0+mc1.21.6.jar` | 无 | `preview_OptiFine_1.21.6_HD_U_J6_pre3.jar` | 21 | 已验证 · 已发布(340 行、stderr 0 字节、无崩溃报告;**不含启用光影包**;本轮本机复现四项判据一致、347 行,见下) |
| 1.21.7 | `21.7.25-beta` | `OptifiNeoforge-1.0.0+mc1.21.7.jar` | 无 | `preview_OptiFine_1.21.7_HD_U_J6_pre7.jar` | 21 | 已验证 · 已发布(340 行、stderr 0 字节、无崩溃报告;**不含启用光影包**;本轮本机复现四项判据一致、347 行,见下) |
| 1.21.8 | `21.8.54` | `OptifiNeoforge-1.0.0+mc1.21.8.jar` | 无 | `preview_OptiFine_1.21.8_HD_U_J6_pre16.jar` | 21 | 已验证 · 已发布(337 行、stderr 0 字节、无崩溃报告;本轮本机复现四项判据一致、344 行,见下) |
| 1.21.9 | `21.9.16-beta` | `OptifiNeoforge-1.0.0+mc1.21.9.jar` | 无 | `preview_OptiFine_1.21.9_HD_U_J7_pre2.jar` | 21 | 已验证 · **未发布**(365 行、stderr 0 字节、无崩溃报告,两次一致) |
| 1.21.10 | `21.10.64` | `OptifiNeoforge-1.0.0+mc1.21.10.jar` | 无 | `preview_OptiFine_1.21.10_HD_U_J7_pre11.jar` | 21 | 已验证 · **未发布**(356 行、stderr 0 字节、无崩溃报告;未为该线改一行处理器代码) |
| 1.21.11 | `21.11.45` | `OptifiNeoforge-1.0.0+mc1.21.11.jar` | `OptiFine_1.21.11_HD_U_J9.jar` | `preview_OptiFine_1.21.11_HD_U_J9_pre4.jar` | 21 | 已验证 · **未发布**(273 行、stderr 107 字节 **与其无 mod 对照跑逐字节相同**、无崩溃报告) |

`1.0.0` 的含义按 `docs/VERSIONING.md`:它由这一条线自己的实机启动记录支撑,不是"功能完备"的断言。
表中 NeoForge 一列是**这条线实际验证用的版本**(1.21.4 用的是 `21.4.149`,不是镜像里更新的 `21.4.157`)。

**1.21.9 / 1.21.10 / 1.21.11:构建阻塞已解除,但仍未发布。** 原来的原因写在"构建而不是验证"上,本轮复现了
它:这三版的 NeoForge 去掉了 ModLauncher,而本分支把实现 `cpw.mods.modlauncher.api.ITransformationService`
的那批类放在 `src/main`,于是 `:compileJava` 直接失败 —— 2026 在这台机器上复现 1.21.11(`21.11.45`),
`错误: 程序包cpw.mods.modlauncher.api不存在` 共 **100 个**,全部落在实现 ModLauncher 接口的那批类上。

现在这批类移到了 `src/ml11/java`,由 `-Pmountpoint` 决定编不编:`modlauncher`(1.21 – 1.21.8)编,
`fml10`(1.21.9 起)不编。三条线的构建因此都能通过,实测各一次:`1.21.9`(`21.9.16-beta`)、
`1.21.10`(`21.10.64`)、`1.21.11`(`21.11.45`),产物都在 `build/libs/`。

`fml10` 产出的 jar 里是**加载器侧工具 + mod 骨架**,没有挂载点:这三条线的挂载点是我们自己的
`ClassProcessor`(`26.x` 分支的 `src/fml10`,由 rig 编译**进载荷 jar**),而那份载荷 jar 含 OptiFine 的类、
按 `docs/PUBLISHING.md` 不分发 —— 这与 `26.x` 为 26.1.2 发布的产物是同一类东西。

**1.21.9 的挂载点这一轮在本机跑通了**(FML 10 线,即没有 ModLauncher 的那一版),而且跑了两次,四次判据一致:

```
VERDICT: STARTED   Setting user: True   Sound engine started: True
new crash reports: 0   stderr bytes: 0   [OptiFine] lines: 365
```

挂载点这一轮也**搬进了本分支的构建**:`src/fml10/java` 与 `src/fml10/resources` 在
`-Pmountpoint=fml10` 时编进 `sourceSets.main`,所以被测试的那个类至少属于一次提交。两件必须一起读进去的事:

* 这一版的 rig 关掉了 **FML 的早期加载画面**(`earlyWindowControl=false`,FML 自己的开关)。开着它时
  FML 的早期画面与 OptiFine 的贴图工作抢同一个 GL 上下文,客户端死在
  `SimpleBufferBuilder "Already building"`(一次跑里 7195 条 GL 错误)。**其它线的跑法里这个画面是开着的**,
  所以这条线的成绩按这个前提读。
* 启动入口是 rig 的诊断入口点(同一条 `Entrypoint.startup` 管道)。原因见 `docs/PLAN.md`:FML 10 的官方入口点
  `net.neoforged.fml.startup.Client` 把启动期异常交给一个**模态对话框**,什么也不打印、也不写崩溃报告。

**FML 10 的三条线(1.21.9 / 1.21.10 / 1.21.11)在本机全部跑通**,判据逐项一致(见上表):三条线都
`VERDICT: STARTED` + `Setting user` + `Sound engine started` + 本次运行无崩溃报告;1.21.9 / 1.21.10 的 stderr
是 0 字节,1.21.11 的 107 字节与它自己的**无 mod 对照跑逐字节相同**(同一行 log4j 环境告警)。
1.21.10 与 1.21.11 **没有为它们改一行处理器代码**:成员恢复、reparent、keep-runtime、stub、精灵集合修复、
反射式 tag creator 全部原样生效(1.21.11 上只把 tag creator 的委托目标从写死的 `ResourceLocation`
改成动态查找,因为那一版把这个类改名成了 `Identifier`)。

三条线共同的 rig 前提,必须一起读:

* **关掉 FML 的早期加载画面**(FML 自己的 `earlyWindowControl=false`)。开着它时 FML 的早期画面与 OptiFine
  的贴图工作抢同一个 GL 上下文,三条线都会死在 `SimpleBufferBuilder "Already building"`(一次跑 7195 条 GL 错误)。
  其它线的跑法里这个画面是开着的。
* 启动入口是 rig 的诊断入口点 `DiagnosticClientAny`:FML 10 的官方入口点把启动期异常交给一个**模态对话框**,
  什么也不打印、也不写崩溃报告。这个类不引用任何 FML API(全反射),因为它要同时适配 loader 10.0.14 与 10.0.32
  (`startup` 的返回类型从 `FMLLoader` 变成了 `Entrypoint$StartupResult`)。
* 这一版 **26.1.2 仍未验证**:它在 `26.x` 分支上,用 OptiFine 自带的 ClassProcessor,本轮没有碰。

**本节只改了构建,没有改任何实机结论**:这台机器上没有 rig,本轮**没有**重跑启动,
所以这三条的实机判据仍然只有 `docs/MATRIX.md` 里那一份。发布本身也仍是独立的一步(需要一条启动记录与
Release 正文),尚未做。

### 本轮本机复现的六条线(2026-09-19)

同一条 rig 流程(离线换类 + 成员回填 + 重定父类 + 运行时 stub + 两条计划)在这六条线上各跑了一遍。判据是
`Setting user` + `Sound engine started` + 本次运行无新崩溃报告 + stderr;`[OptiFine]` 行数是 `latest.log` 里的原始条数:

| 线 | NeoForge | OptiFine | 四项判据 | `[OptiFine]` 行数 | README 记录 | 图集 `Created:` |
|---|---|---|---|---|---|---|
| 1.21.1 | `21.1.250` | `OptiFine_1.21.1_HD_U_J1` | 通过 | 232 | 223 | 14 |
| 1.21.3 | `21.3.97` | `OptiFine_1.21.3_HD_U_J2` | 通过 | **225** | 225(一致) | 14 |
| 1.21.4 | `21.4.149` | `OptiFine_1.21.4_HD_U_J3` | 通过 | **232** | 232(一致) | — |
| 1.21.6 | `21.6.20-beta` | `preview_OptiFine_1.21.6_HD_U_J6_pre3` | 通过 | 347 | 340 | 13 |
| 1.21.7 | `21.7.25-beta` | `preview_OptiFine_1.21.7_HD_U_J6_pre7` | 通过 | 347 | 340 | 13 |
| 1.21.8 | `21.8.54` | `preview_OptiFine_1.21.8_HD_U_J6_pre16` | 通过 | 344 | 337 | 13 |

1.21.4 那一行是用**本轮改动后的加载器代码**重建并复跑的,目的是确认共用的加载器代码没有被改坏 —— 行数与记录逐字一致。
六条线都**没有重新发布**,表里的"已发布"仍指原有产物。

**1.21.6 / 1.21.7 与 1.21.8 是同一个冲突,机制也同一套**:`PayloadDrift` 在这两版上量到的常量分歧都是
`ModelDiscovery$ModelWrapper.SLOT_COUNT` 负载 7 / 运行时 8(NeoForge 在 21.6 之前就加了第 8 个槽位),于是这两条线也用
同一份 `keep-runtime.txt`(整类保留 + 删掉 OptiFine jar 里的补丁条目)与各自的接口计划(13 条)通过 —— 这正是"计划由工具
实测产出"的价值:同一个冲突在三条线上自动重现,而不是每条线重新查一遍。1.21.7 上 `PayloadDrift` 还报出第二个常量分歧
`MappableRingBuffer.BUFFER_COUNT` 负载 5 / 运行时 3,它**故意不进** keep plan:3 是原版的值,5 是 OptiFine 自己的改动,
且该类的数组与取模都自洽、外部只调方法,留下它才是对的(1.21.8 上同一处也这么判)。

**一条 OptiFine 侧的坑,记下来免得下次再查**:1.21.1 与 1.21.3 这两个构建(J1 / J2)在**没有 `optionsof.txt`** 的游戏目录上会自己崩 ——
`Options.loadOfOptions` 抛 `ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1`,堆栈整个落在 OptiFine 自己加的方法里,
`Minecraft.<init>` 就停住,之后的错误界面又会在 `Font.ellipsize` 上二次崩,现场只剩两次崩溃报告。放入一份有效的
`optionsof.txt`(这两条线用的是从 1.21.4 目录拿来的同一份,1826 字节)后即通过。1.21.4 / 1.21.6 / 1.21.7 / 1.21.8 的游戏目录
里本来就有这个文件,所以它们的 `-Fresh` 复跑没有暴露这一点。这是 OptiFine 的行为,不是加载器的判据,但如果要给别人复现
步骤,这一步要写进去。

**1.21 这一轮没有跑起来,而拦住它的是一条与既有记录不符的实测事实。** 这条线的补丁负载是 **SRG 成员名**(`f_21340_`、
`m_91087_`),而运行时是官方名(`DATA_MOB_FLAGS_ID`、`runTick`):实测 `Mob` 两侧的字段名逐条对不上,于是 `MissingTargets`
报出 3281 个"运行时没有的"引用、其中 2094 个只能留给加载器,成员回填计划也从其它线的 293 条涨到 **6251 条** —— 这一版
**不能**按离线换类直接用。`SrgMemberMap` 的注释把分界线记在 **1.20.6**("OptiFine switched the member names in its payload
from SRG to Mojang's official names at 1.20.6"),但这台机器上的 `preview_OptiFine_1.21_HD_U_J1_pre9` 仍然是 SRG 名,**这条
记录至少对 J1_pre9 不成立**。要让这条线能跑,需要把 `SrgRemap` 那一步接进 rig,而它要两张表:MCPConfig 的 `joined.tsrg`
(本机有)和 NeoForm 的 `-mappings-merged.txt`(本机没有,要用 installertools 的合并步骤生成)。这一步没做,所以 1.21 本轮的
状态是"未跑",不是"失败",也不是"通过"。

**试着把这一步补上时,又量到三件事**(都记在这里,因为下一步从这里开始):

1. `SrgRemap` 要在 **ASM 9.10.1** 上跑,rig 的工具 classpath 是 9.8,在 9.8 上它直接抛
   `NoSuchMethodError: 'void org.objectweb.asm.commons.Remapper.<init>(int)'` —— 源码注释里写了这件事(`super(Opcodes.ASM9)`
   正是为 9.10.1 写的,而 Gradle 侧用解析策略拿 9.10.1)。按这个版本另建一份 classpath 后它就跑起来了。
2. 第二张表可以从 Mojang 的 `minecraft_1.21_client_mappings.txt`(proguard 形状)转出来,转换脚本在 rig 里
   (`proguard-to-tsrg.ps1`)。第一版转出来联接失败:与 MCPConfig 的 `joined.tsrg` 做联接时 **54084 个成员在另一侧没有对应**,
   `SrgRemap` 于是只改写 4588 个方法名、17527 个字段名,**17620 个无法解析**。查出两个原因,都是转换脚本自己的:
   ① 两侧的描述符**不在同一个命名空间** —— `joined.tsrg` 写的是混淆类型(`a (Lakr;)Lgql;`),从 proguard 的 Java 类型直接
   转出来的是官方类型(`()Lnet/minecraft/resources/ResourceLocation;`),而 `SrgMemberMap` 的键包含描述符,于是永远对不上;
   ② 更隐蔽的一处:脚本里把形参表存进了 `$args`,那是 PowerShell 自己的自动变量,赋值被吞掉,于是表里**每个方法都变成无参**。
   两处修好后,联接的落空数从 54084 降到 **82**,改写变成 21803 个方法名 + 17527 个字段名,**无法解析的只剩 395**
   (253 个字段"表里没有"、113 个方法同因、29 个"成员换了形状")。
3. **要改写两个 jar,不是一个**。只改写补丁游戏类不够:OptiFine 自己的类(最终进 OptiFine jar 的那些)同样用 SRG 成员名,
   实测第一次启动死在 `srg/net/optifine/render/RenderEnv.<init>` 的
   `NoSuchFieldError: ... Direction does not have member field 'net.minecraft.core.Direction[] f_122346_'`。对
   **prepared OptiFine jar** 再跑一次 `SrgRemap` 后,那一步的改写是 3528 个方法名 + 1537 个字段名,无法解析 4 个。
4. 改写之后这条线的规模立刻回到正常:成员回填计划从 **6251 条 / 356 类**降到 **363 条 / 97 类**,"运行时没有的引用"从
   **3281** 降到 **62**(其中 46 条补进 payload、12 条留给加载器),`PayloadDrift` 的常量分歧为 0(不需要 keep plan)。

**1.21 现在的实测进度:到标题界面,但没过判据。** 用上面这条链跑起来后,`Setting user` 为 True、stderr 0 字节,但
`Sound engine started` 为 False,并在 `Minecraft.<init>` 里崩了一次:

```
IllegalAccessError: class net.neoforged.neoforge.client.NeoForgeRenderTypes$Internal tried to access
  protected field net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ENTITY_SOLID_SHADER
```

这是加载器注释里已经写过的那类"访问权限谁更宽"的问题,只是这次踩在字段上:规则取"负载与交上来的那份里更宽的",而这条线上
OptiFine 自己的转换器**先**替掉了 `RenderStateShard`(本行日志里能看到它被换掉),于是交上来的那份就是 OptiFine 的,
两边都是 `protected`,运行时的 AT 加宽结果看不到 —— 和 1.21.8 上"接口计划为什么必须存在"是同一个根因。

**于是加了第三条计划(access plan),它解决了一类问题,但没解决这一处。** 做法与另外两条一样,由 `PayloadDrift` 离线量:
`owner<TAB>name<TAB>desc<TAB>access`,取"负载的可见性比运行时的窄"的成员,加载器把它当作可见性下限(负载自己的 flags 更宽时
仍以负载为准)。这条线上它量到 **15 个类、81 个成员**,日志里确实生效了(`Widened 63 member(s) of RenderType`,
`ParticleEngine.register` 等),**但 `RenderStateShard` 不在名单里** —— 原因查清了:

- 运行时 jar 里 `RenderStateShard.RENDERTYPE_ENTITY_SOLID_SHADER` 就是 `protected static final`,所以离线比对看不到任何分歧;
- 真正把它变宽的是 **NeoForge 自己的 AT 配置**:`neoforge-21.0.167-universal.jar` 的 `META-INF/accesstransformer.cfg` 里有
  `public net.minecraft.client.renderer.RenderStateShard *`,由 FML 在加载期应用,而不是烘进那个 jar。

所以下一步要把 **AT 配置**当成第三个来源(`PayloadDrift` 现在只比两个 jar),或者让转换器真正拿到 AT 之后的运行时类。

**AT 这条来源已经补上了,那一处也就没了;卡点换成了下一处。** `PayloadDrift` 现在接受 `--at <jar 或 cfg>`:直接读
`neoforge-<ver>-universal.jar` 里的 `META-INF/accesstransformer.cfg`,把里面 `<class> *`、`<access>-f <class> <field>`、
`<access> <class> <method>(<args>)<return>` 三种写法都收进来,并**与运行时 jar 的可见性取更宽的那个**当作目标。实测这条线上
AT 配置给出 2 个类 + 422 个具名成员,access plan 从 81 行涨到 **255 行**(`RenderStateShard` 一个类就 140 条:96 个字段 + 44 个方法),
其中就包含 `RENDERTYPE_ENTITY_SOLID_SHADER`。重跑后 `IllegalAccessError` 消失,`[OptiFine]` 行数从 60 涨到 74 —— 这一处**确实修好了**。

新的卡点是另一类,而且能一句话说清:**负载的调用方需要一个"被我们故意不替换"的类所提供的、只存在于负载里的成员**。实测:

```
NoSuchMethodError: SpriteResourceLoader.create(java.util.Collection)
  at SpriteLoader.loadAndStitch(SpriteLoader.java:187)
```

日志同一段里两件事同时发生:`SpriteLoader` 被负载替换,而 `SpriteResourceLoader` 被**按接口规则留着运行时的副本**
("运行时给这个接口加了成员,装负载会连带换掉填这些字段的静态初始化器")。于是负载版 `SpriteLoader` 调的是 OptiFine 那一代
`create(Collection)`,留下的运行时副本没有这个重载。`MissingTargets` 看不到这类问题,因为它**刻意把负载也索引进去**
(理由见它的注释:被替换的类自己能满足这些引用)—— 而这里那个类恰恰没有被替换。要修就是给"被留着的类"补上负载需要的成员
(方向与 `MemberRestorePlan` 相反),这一步没做。

**1.21 本轮结论:仍未通过。** `Setting user` 通过、stderr 0 字节、`Sound engine started` 不通过、本次运行 1 份崩溃报告。

**后来这一处修好了,而"卡住"的读法也纠正了。** 加载器现在会给"被留着的类"补上负载需要的成员(与 `MemberRestorePlan` 相反的方向,
从负载自己的副本取):实测那一处 `SpriteResourceLoader.create(Collection)` 消失,`[OptiFine]` 行数 74 → **299**,日志一路走到
`GameRenderer.render`,也就是说**画面已经在渲染**。同时纠正一个误判:先前按"日志不再增长 + 没有图集"读成"卡在资源重载",线程转储
说明不是 —— `Render thread` 停在 `RenderSystem.limitDisplayFPS` 的 `glfwWaitEventsTimeout` 上,那正是**空闲的渲染循环**,其余
Worker 线程都在等活。真正的缺口是**声音引擎没起来**,而原因在日志里是独立的一条:

```
[modloading-worker-0/FATAL] Failed to wait for future Registration events, 1 errors found
  -> NoSuchMethodError: 'ResourceMetadata Resource.m_215509_()'
```

**调用点要的是 SRG 名 `m_215509_`** —— 也就是上面那条"表不完整"的直接后果:自制的映射表在这份游戏负载上还剩 **395 个**引用没改写
(OptiFine 自身 jar 上剩 4 个),注册阶段踩到的就是这个。所以 1.21 下一步**不是**继续改加载器,而是把映射表换成完整的
(NeoForm 的 `MERGE_MAPPINGS`,用 installertools),让剩余引用归零 —— `SrgRemap` 的验收口径本来就是"改写正确的负载不剩 SRG 引用"。

**但这条推断有一处对不上,记下来给下一轮。** 栈里的调用点是
`net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader.lambda$create$0(SpriteResourceLoader.java:32)`,而按字节扫描,
**两个成品 jar 里都没有 `m_215509_` 这个字符串**:加载器 jar 里那个类的条目(`optifineoforge/patched/.../SpriteResourceLoader.class`)
含的是 `metadata`(官方名),prepared OptiFine jar 同样为 0。也就是说这条引用不是我们发出去的那份类里的,来源还没查到。
它对应的映射本身是存在的、也是对的(实测:`joined.tsrg` 的 `f ()Laug; m_215509_` ↔ 自制表里的 `f ()Laug; metadata`,
官方名 `ResourceMetadata`),所以"表里缺这条"不是原因。下一轮的第一步应该是**把加载期真正被装载的那份类 dump 出来**
(在转换器里把最终 `ClassNode` 写到临时文件),而不是继续猜;在那之前,这条线的"395 个未改写引用导致注册失败"只是**未被证实的解释**,
不能当成结论。

**查到了,而且它解释了整簇 1.21 的现象。** 加载器加了 `-Doptifineoforge.dump=<dir>`:设置后转换器把**最终交给 JVM 的那份类**写到磁盘。
用它 dump 出来(258 个类)之后,`SpriteResourceLoader.class` 里**确实**有这一段:

```
net/minecraft/server/packs/resources/Resource.m_215509_()Lnet/minecraft/server/packs/resources/ResourceMetadata;
```

注意形状:**成员名是 SRG(`m_215509_`),而描述符是官方名**。而加载器 jar 里**没有任何一个条目**含这个字符串(整包扫过,0 命中),
所以它只能是**加载期产生的**。产生它的正是 OptiFine 自己的转换服务:它按 jar 里 `patch/srg/**.xdelta` 把类打出来,
**这些补丁数据是二进制 delta,改写 jar 里的 `.class` 条目碰不到它**,于是它打出来的类仍带 SRG 名 —— 这条线的负载本来就是 SRG 名的
(见上文,这个构建早于 OptiFine 的切换点),补丁数据自然也是。这也解释了为什么其余六条线没事:它们的补丁数据是官方名的,
所以它们打出来的类不需要再改一次名。

由此,1.21 的正确下一步不是"再改 jar",而是**让加载器在转换时用一份 SRG→official 成员表改写它拿到的类**(表由离线工具生成、
随加载器 jar 发布),或者更窄地把这一类类的补丁条目去掉(keep plan 那套工具)。前者更合适:它一次覆盖所有"OptiFine 自己打出来"
的类,而不是逐个列。

**这条路线已经搭起来了,但第一次实测是失败的,因此默认关着。** 三样东西落地:① 离线工具 `SrgNameTable`
(从 SRG 负载的**声明与引用**两侧收集,落到 `owner<TAB>srg<TAB>official`;实测 1.21:9918 条 / 1027 个 owner,另有 3422 条表里无解,
嵌入加载器 jar 的是 8391 行);② 加载器在读表后在转换时改写(字段/方法**声明**、`FieldInsn`/`MethodInsn` 引用、
以及 `invokedynamic` 引导参数里的 `Handle`);③ rig 侧自动生成并随 jar 打包。

**打开它(`-Doptifineoforge.renameSrg=true`)以后这条线反而退回去了**:`[OptiFine]` 行数从 299 掉到 **0**,并且死在
OptiFine 自己的 `Reflector.<clinit>` 里(模块类加载器找不到类)。原因事后看很清楚:**OptiFine 自己的类也用 `m_`/`f_` 这个名字形状
命名自己的成员**,于是"连声明一起改写"把 OptiFine 自己的名字也改掉了。所以现在它默认关闭,下一次尝试应当是**只改写引用**
(补丁数据打错的是它**调用**的名字,而那些成员由表里描述的游戏类声明)。关闭后这条线回到了改动前的状态(原始 299 行、
stderr 14 141 字节、`Setting user` 通过、本次运行无崩溃报告、`Sound engine started` 仍不通过)。

这条记录的意义是把"下一步"从猜想变成了一个**已经被证伪过的具体做法**:表是对的(它确实包含 `Resource.m_215509_ → metadata`),
错的是改写范围。

**改成"只改写引用"以后,这一步**确实**救回了注册失败,但默认开关的复现没对上,两件事都记在这里。** 用
`-Doptifineoforge.renameSrg=true` 跑那一次:

| 判据 | 只改引用(开) | 关(基线) |
|---|---|---|
| 注册阶段 FATAL | **0** | 1 |
| 硬性链接错误(`NoSuchMethodError` 等) | **0** | 有(反射器报错) |
| `Setting user` | 通过 | 通过 |
| stderr 字节 | 14141 | 14141 |
| 本次运行崩溃报告 | 0 | 0 |
| `[OptiFine]` 原始行数 | 236 | 299 |
| `Sound engine started` | 仍不通过 | 仍不通过 |

也就是说:**注册失败(以及它带来的"声音引擎起不来")被这一步修掉了**,而 `Sound engine started` 仍然没有出现 —— 这条线现在
不再"响亮地失败",而是**安静地停在资源重载之后**(日志最后是 OptiFine 的 `ConnectedTextures` 解析,没有图集 `Created:` 行,
也没有 `SOUNDS` 行,同时没有任何错误)。这本身是下一轮要查的问题,而且比之前好查。

**对不上的地方**:把默认值从"关"改成"开"之后(源码里是 `!("false".equals(System.getProperty(...)))`),同样的构建流程重跑,
日志显示表**已加载**、却**一次改写都没有发生**(`Rewrote ... SRG name` 0 行),FATAL 也回来了 —— 与上面那次"开着跑"的结果不一致。
这处矛盾**没有解释**,所以现在**不宣称这一改动默认生效**:正确状态是"它由 `-Doptifineoforge.renameSrg=true` 手动打开,
打开时实测修掉了注册失败"。下一轮第一件事是**把两次的加载器 jar 对比出来**(哪一处不同),而不是再猜。

**这处矛盾已经查清,而且原因就在那行开关里。** 原来的守卫写成 `!("false".equals(System.getProperty(...)))` —— 读起来像"默认开",
实际是反的:属性**未设置**时它不是 `"false"`,于是取反为真、函数直接 return,一次都不改写。实测两边的差别正是这个:
属性设为 `true` 时日志有 5 行 `Rewrote ... SRG name`、FATAL 为 0;改成那个"默认开"的写法后变成 0 行改写、FATAL 回来。
守卫改对之后(未设置即生效,`-Doptifineoforge.renameSrg=false` 关闭),**默认状态下复现了同样的结果**:
改写 5 处、注册 FATAL **0**、`Setting user` 通过、stderr **14 141 字节**、原始 `[OptiFine]` **236** 行、本次运行无崩溃报告。

**1.21 现在剩下的唯一缺口是 `Sound engine started`,而且它是"安静"的**:日志停在 OptiFine 的 `ConnectedTextures` 解析之后,
既没有图集 `Created:` 行、也没有 `SOUNDS` 行,同时**没有任何错误**。

**截图纠正了一个更早的判断:它没有到标题界面,而是停在加载遮罩上。** 用 rig 的 `capture-window.ps1`(`PrintWindow`,不依赖窗口是否在前台)
抓这一状态下的窗口:`870x519`、均值 RGB **231,72,81**、量化色桶 48、最常见颜色占 **84%** —— 与之前量到的加载遮罩
(237,78,87、单色 84%)是同一个特征,而对照标题界面是 79,80,76 / 99 桶。所以这条线的真实状态是:**资源重载没走完,画面停在加载遮罩上**,
图集没建、声音引擎没起,而日志里连一条错误都没有。

这也意味着更早那句"线程转储显示它在空闲渲染循环里,所以不是卡住"**下得太宽松**:加载遮罩的渲染循环同样是空闲的,
`glfwWaitEventsTimeout` 说明的是"没有待处理的输入",不是"已经到标题界面"。**区分这两者只能靠截图**,这一点已经写进上面的复现清单。

**卡在哪一步也量出来了:不是死锁,是"没人干活"。** 在这个状态下再抓一次线程转储:线程状态分布是 *runnable 25 / waiting on
condition 45 / Object.wait 1*,**没有任何 BLOCKED 线程**,也**没有任何线程的栈落在资源重载相关代码里**
(`PreparableReloadListener`/`SimpleReloadInstance`/`CachedSupplier`/`ProfiledReloadInstance` 全无命中);`Render thread` 停在
`Minecraft.runTick(Minecraft.java:1220)` 的 `limitDisplayFPS` 上 —— 也就是游戏主循环还活着,而**重载这件事没有任何在飞的活**。
这更像"某个 future 永远不会被完成",而不是死锁或某个 worker 卡住。

顺着这条线,加载器里本来就有针对这块的诊断:`ReloadProbe` + `ReloadProbeFix`(把重载监听器列表按顺序打出来,
回答"是不是顺序问题"),开关是 `-Doptifineoforge.debug.reload=true`(已核对常量名,就是这个)。**但打开它跑了一遍,输出是空的**:
探针没有触发。这个结果本身有用 —— 说明探针插桩的那个方法(`ReloadableResourceManager.createReload`)**并不是这条线上真正跑重载的那个**
(日志里 OptiFine 的 `Reloading ResourceManager` 与 CTM 解析证明重载确实发生了)。下一轮从这里开始:先确认这条线上
`createReload` 的**声明者/调用者到底是谁**(OptiFine 的替换类、还是另一个同名方法),再决定探针该插在哪里。

**查清了,而且它把问题从"重载实现"移到了别处。** 运行时那份 `ReloadableResourceManager` **有** `createReload`
(描述符 `(Executor,Executor,CompletableFuture,List)ReloadInstance`),而**负载里那份根本没有这个方法** ——
`javap` 出来的成员里只剩一个 `lambda$createReload$0(List)`,也就是 OptiFine 的编译把 `createReload` 弄丢了、只留下它的 lambda 体。
于是装上之后真正跑的 `createReload` 是**从 donor(运行时那份)补回来的**;探针之所以没输出,也就可以理解了:探针插桩时匹配的是名字,
而它插的是**负载那份**(那个方法不存在),补回来的那份是另一个 transformer 后来才写进去的。

这条事实把结论收紧了一步:**这条线上跑的重载实现是运行时的,不是 OptiFine 的**,所以"重载永远不完成"不可能来自 OptiFine 的重载代码;
而线程转储又显示没有任何线程在重载代码里 —— 两者合起来只剩两种可能:要么某个监听器以某种方式既不完成也不占线程(例如它提交的任务
从未被某个 executor 执行),要么重载其实**已经完成**、只是 NeoForge 的加载界面没有被撤下(红屏本身就是它的加载/错误界面,
之前量到的 84% 单色红与 1.21.8 那次加载遮罩是同一个特征)。下一轮第一件事就是**把这两种可能分开**:在重载的 future 完成路径上加一条日志
(或在探针里补上对"补回来的 createReload"的插桩),这是判定性的,不需要再猜。

**探针的沉默本身是个加载器缺陷,修好之后它直接把范围缩到了一个监听器。** 原因是**顺序**:`ReloadProbeFix` 在转换器列表里排在
`MemberRestoreTransformer` **前面**,而它插桩的 `createReload` 恰恰是**被回填之后才存在**的方法 —— 排在前面时它找不到目标,于是静默。
把 `ReloadProbeFix` 移到 `MemberRestoreTransformer` **之后**(它只是诊断,不打开属性就不产生任何改动)以后,同一套 jar、同一个属性,
探针开始输出:

```
reload 1: 28 listeners
0  net.neoforged.neoforge.client.loading.ClientModLoader$$Lambda/...
1  net.neoforged.neoforge.internal.BrandingControl$$Lambda/...
2  net.minecraft.client.resources.language.LanguageManager
3  net.minecraft.client.renderer.texture.TextureManager
4  net.minecraft.client.sounds.SoundManager
5  net.minecraft.client.resources.SplashManager
6  net.minecraft.client.gui.font.FontManager
...
```

(每一行后面的 `vanillaName=?ClassNotFoundException(...VanillaClientListeners)` 是探针自己的取名逻辑在报错,与本次失败无关。)

于是上一轮那两种可能里的第一种被证实:**重载确实被创建、确实带着 28 个监听器开始跑了**(`reload 1: 28 listeners`),
`SoundManager` 也在名单里(它的 `SoundEngine` 是在自己的 apply 步骤里建的,那一步显然没轮到),
而日志的最后一段活动正是 OptiFine 的 `ConnectedTextures` 解析,之后再无输出 —— 判据仍是 `Setting user` 通过、`Sound engine started` 与图集为 0。
**下一步很具体**:让探针在**每个监听器的 prepare/apply 完成时**各打一行(或在重载 future 的完成路径上打标记),
就能点出"卡住的是哪一个监听器",而不是继续从"没有输出"反推。

**做了,而结果比"某一个监听器卡住"更强:28 个任务全部开始,一个都没有结束。** 探针现在也插桩
`SimpleReloadInstance.lambda$of$0`(每个监听器的那次任务;它是静态方法、监听器是第 4 个参数,所以注入读 local 3),
在任务入口与每个 `RETURN` 前各打一行。实测:`listener task started` **28** 行,`listener task finished` **0** 行,
日志里最后几条是"27、28 in flight"。

这解释了为什么线程转储里"看不到任何线程在重载代码里":28 个任务**全都停在同一个地方**,而那个地方的栈是
`CompletableFuture`/`LockSupport` 的内部帧,不含 `PreparableReloadListener` 这类名字 —— 我上一轮按类名去找,自然是空的;
"45 个 waiting on condition"里就有它们。vanilla 的重载给每个监听器一个 `PreparationBarrier`,**每个都必须到齐**,只要有一个
永远不调用它,其余全部原地等待 —— 这正是"全部 started、零 finished"的形状。

**所以卡点不在"哪个监听器算得慢",而在"哪一个监听器没有到达那一次 barrier"**。下一步因此变成了一个很小的插桩:
在 `lambda$of$0` 里对 `PreparableReloadListener$PreparationBarrier.wait(...)` 的调用**之后**再打一行,列出**已经到达 barrier** 的监听器;
28 个里缺的那一个就是答案。这一轮到此为止是因为上下文用尽,不是因为这条线查不动了。

**接着做了,但这一针打偏了 —— 记下来,免得下次重复。** 在 `lambda$of$0` 里找 barrier 调用并打点之后,实测
`barrier reached: 0`(而 `listener task started` 仍是 28、`finished` 仍是 0)。**这不是"没人到达 barrier",是插桩位置错了**:
`javap` 出来看,`lambda$of$0` 自己**不调用** barrier,它只是把 barrier 作为参数传给
`PreparableReloadListener.reload(barrier, ...)` —— 真正调用 `barrier.wait(...)` 的是**每个监听器自己的 `reload` 实现**。
所以那一条 0 什么也不能说明,不能当作结论。

真正该插的类是 `SimpleReloadInstance$1`(实测它就是 `PreparableReloadListener$PreparationBarrier` 的实现,方法签名 `public <T> CompletableFuture<T> wait(T)`)。
它拿到的参数是监听器交上来的值、不是监听器本身,所以要点名"缺的是哪一个监听器",办法是**在 `wait` 里打出当前线程名**,
再和 `listener task started` 那组日志(每条都带监听器名字)按线程对上:任务开始过、却从未在 `wait` 里出现过的那个线程,
对应的监听器就是答案,而且这不需要改任何监听器的代码。

这一轮真正站得住的两条实测:① **28 个监听器任务全部开始、零个结束**(所以是"全部停在同一个地方",不是某一个卡住);
② **`SimpleReloadInstance` 不在负载里**(payload 里 0 个条目),也就是说跑的是运行时那份,我们的插桩确实生效
(否则不会有那 28 行)。

**把插桩挪到真正的 barrier 上之后,答案缩到了"一个监听器"。** 实测(同一套 jar、同一个属性):

| 计数 | 值 |
|---|---|
| `listener task started` | **28** |
| `barrier reached` | **27** |
| `listener task finished` | **0** |

也就是说 **28 个监听器里有 27 个到达了 barrier,只有 1 个从未到达**,其余全部在等它 —— 这正是"全部 started、零 finished"的成因,
而且把嫌疑范围从一个集合缩到了一个监听器。**但"是哪一个"还没定下来**:我试着在 `barrierReached()` 里走栈、取第一个非 JDK、
非本工程的帧当调用者,结果拿到的是 `com.mojang.blaze3d.systems.RenderSystem`、`net.minecraft.server.packs.resources.ResourceManagerReloadListener`
和一次 `?` —— 重载路径上有一层包装/lambda,栈帧里出现的不是真正的监听器类,所以这个办法**不足以点名**,不能当结论用。

下一轮定这一个监听器有两条现成的路:① 在 27 次到达里把**整个栈**打出来,与已有的 28 个 `listener task started` 名字做差集,
没出现过的那个就是它;② 换个插桩点 —— 例如在 `SimpleReloadInstance.lambda$of$0` 里,对 `listener.reload(...)` 返回的 future
挂一个 `whenComplete`(不是改逻辑,只是加日志),谁没有走到 complete 就是谁。两条都不需要猜。

**第 ② 条做了,结果把第 ① 条变成唯一可行的那条。** 在 `lambda$of$0` 里给每个 `listener.reload(...)` 返回的 future 挂上
`whenComplete` 之后,实测:**完成数 0**(而 started 仍是 28、barrier 到达仍是 27)。这个 0 是**全局的**:到达 barrier 的 27 个监听器
都在等"全部到齐"才被放行,所以它们的 future 一个都不会完成 —— 于是"started 减去 completed"这个差集**等于全部 28 个**,
一个名字都点不出来(实测正是如此:28 个名字原样列出来)。换句话说:**用"完成了没有"来筛是筛不动的,因为卡点是全局的**,
能筛的只有"到没到 barrier",而到达那一侧的身份拿不到。

所以只剩第 ① 条:**在 27 次到达时把整个栈打出来**,与 28 个 `listener task started` 名字求差 —— 没有在任何一次到达栈里出现过的
那个监听器就是答案。(另有一条同样干净、但要动 rig 的路:离线把人实现 `PreparableReloadListener` 的类列出来做成一份计划,
让探针在每个类的 `reload` 入口打一行 —— 那是本项目一贯的做法:名单由离线工具量出来,而不是在运行期猜。)

**第 ① 条也失败了,而且是干净地失败:到达栈里根本没有任何监听器的类名。** 实测:27 次到达、每次打完整栈,把它们拼起来与 25 个
(去重后的)监听器名字求差,**差集是全部 25 个** —— 也就是说没有任何一次到达的栈里出现过任何一个监听器类。原因和上一次单帧尝试
给的一样:重载路径上每个监听器都是**穿过包装类**走到 barrier 的(栈里出现的是 `RenderSystem`、`ResourceManagerReloadListener`
这类中间层),具体是哪个监听器在栈上**根本不出现**。所以运行期这两条路(单帧、整栈)都被排除,而且不是因为实现没写好,而是因为
信息不在那里。

**剩下唯一一条:身份从监听器那一侧拿。** 而那一侧恰好是有的 —— `listener task started` 那 28 行就是 `lambda$of$0` 里拿到的监听器本身。
所以下一轮的做法是把身份**关联**起来而不是去找:离线把实现 `PreparableReloadListener` 的类列成一份计划(从运行时 jar 里扫出来),
让探针在每个类 `reload` 的入口打一行;27 行里有、28 行里没有的那一个就是答案 —— 与本项目其它计划一样,名单由离线工具量出来。

**这条也搭好了,但第一次跑是 0 命中,原因已查明且很小。** 计划机制本身可用:rig 把上一轮日志里的监听器名字收成 23 个类,
打成 `optifineoforge/reload-listeners.txt`(实测 build-jars 输出 `listener plan: 23 class(es)`),探针按计划把每个类加进 targets
并在 `reload` 入口打一行。实测:`started 25, entered 0` —— **一行都没有**。原因是**名字形状对不上**:日志里
`listener task started ...: LanguageManager` 给的是**简单类名**,而探针比较的是 `input.name`,也就是**内部名**
`net/minecraft/client/resources/language/LanguageManager`;两边永远不相等,所以计划里的类一个都没被标记。
这不是机制问题,是那一份名单要**用全限定名** —— 收名字时把每个简单名在运行时 jar 里解析成内部名即可(条目名后缀匹配,不需要新工具)。
修好这一处之后再跑一次,就能得到"27 个里哪一个没进来"的答案;这一轮到此为止是上下文用尽,不是这条线又断了。

**名字修好之后立刻从 0 变成 4,而剩下的 18 个暴露了第二个形状问题。** 把 23 个简单名逐个在运行时 jar 里解析成内部名
(22 个解析成功,`ObjLoader` 不在那个 jar 里 —— 它是 OptiFine/NeoForge 那边加的)之后,实测 `entered` **从 0 变成 4** ✔,
说明这条路是通的。但 22 个计划类里只有 4 个被标记,原因是**这些监听器不是自己声明 `reload` 的**:它们实现的是
`ResourceManagerReloadListener`(一个接口),真正的入口是它提供的 default `reload` 与子类自己的 `onResourceManagerReload`,
所以按方法名 `reload` 去插桩,18 个类里根本没有这个方法 ✗。少数自己声明 `reload` 的(例如 `ModelManager`、`ParticleEngine`
这类)才会命中,这也解释了 4 这个数。

下一轮的小改动因此很明确:**对计划里的类同时按 `reload` 与 `onResourceManagerReload` 两个名字插桩**(两个名字都试,
哪个存在插哪个),之后的差集才是可信的"27 里缺哪一个"。

**这个改动做了,但一个都没多出来 —— 而且我给出的解释经不起检查,所以照实记。** 加第二个入口名之后实测仍是
`entered 4`,进去的四个是 `TextureManager`、`FontManager`、`ModelManager`、`ParticleEngine`。我先给出的解释是
"ModLauncher 每个目标只问一个 transformer,而这 18 个已被更早的 transformer 声明",但**核对下来不成立**:22 个监听器里只有
**8** 个出现在别处的计划文件(member-restores / 接口 / 访问 / stubs)里,另外 **14** 个没有任何别的地方声明它们,却同样没有被标记。
所以"18 个为什么没被标记"**目前没有成立的解释**,不能按上一条写。

还没验证的那个假设是我自己引进来的:`Target.targetClass(...)` 到底接受**点分名**还是**内部名**? 计划文件在上一轮被我从简单名
改成了**内部名**(带 `/`),而 `Target.targetClass` 按 ModLauncher 的惯例要的是点分名 —— 如果它不接受内部名,那么计划里的类
匹配不上,而"4 个命中"就只是碰巧(它们同时被更早的 transformer 声明并因此以别的方式被装载/记录)。
**下一轮第一件事就是这个**:把这 22 个名字按点分写进计划再跑一次,看 `entered` 是否从 4 变多。在此之前,这一段的结论保持为
"插桩没有覆盖到大多数监听器,原因未定"。

**点分这个假设也被证伪了 —— `entered` 仍然是 4,命中集合一模一样。** 计划改成 `Target.targetClass(owner.replace('/','.'))`
(与仓库里 `PatchedClassTransformer` 自己的写法一致)之后重跑,结果不变:只有 `TextureManager`、`FontManager`、`ModelManager`、
`ParticleEngine` 四个被标记,其余 18 个一行都没有。所以"名字形状"不是原因,**这一点现在是实测而不是推测**。

到这一步为止,关于这 18 个**被排除**的解释有三个,而且都是被实测排除的:① 完成度不是判据(全局阻塞,28 个 future 全不完成);
② 到达栈里没有身份(整栈都是包装类);③ 名字形状(点分/内部名,两种都试过,结果一样)。剩下**没有**被验证的是"类到底有没有被交给这个
transformer":下一个动作不该再猜,而应当**直接量**——在 `PatchedClassTransformer` 与 `ReloadProbeFix` 里对同一批类各打一行
"我看到了这个类",一次运行就能看出是被别的 transformer 先接走(与 `Nickname` 式的顺序问题一致),还是根本没被交给任何人。



















这一轮 1.21 的实测账: `Setting user` 通过、本次运行崩溃报告 **0**、stderr **14 141 字节(与记录逐字相同)**,`Sound engine started` 不通过,
`[OptiFine]` 原始 **299** 行(记录 252),图集 `Created:` 0(这条线的日志里本来也没有这一行)。



**另一处 rig 侧的坑**:`add-line.ps1` 每次都会用**原始** OptiFine jar 重新生成 prepared jar,所以对 prepared jar 做过的改写会被
它覆盖 —— 实测第一次重跑就退回到 `NoSuchFieldError: Direction.f_122346_`。这条线上正确的顺序是:prepare-line → 改写补丁游戏类 →
build-jars(生成 prepared jar)→ **再改写 prepared jar** → 启动。



### 复现一次启动需要什么(本轮量出来的 rig 要求)

上面每个数字都出自同一套流程,而流程自身这一轮也被量出几条硬性要求 —— 写在这里,免得下一个人或下一轮重新踩:

1. **加载器 jar 必须为当前这一版重新构建**。借别的线的产物、或用改动加载器代码之前的旧产物,都会表现成"加载器 bug":
   实测 1.21.1 用旧产物跑,挂在 NeoForge 自己的 `AttachmentSync.onChunkSent`,报
   `VerifyError: Type 'BlockEntity' is not assignable to 'AttachmentHolder'` —— 而那正是重定父类那条修正在修的东西。
2. **`MissingTargets --stub` 要给足运行时 classpath**(游戏 jar + NeoForge universal + 库 jar)。给少了会得到一份几乎全是
   库成员的 804 行 stub 文件,真正要补的十几个成员(例如 `BlockModelPart.layer()`)被埋在里面;而且交给加载器的 payload
   必须是**补齐之后**的那一个。这条命令行会超出 Windows 上限(220 多个 jar),要用 Java 的 `@argfile` 传。
3. **转换器的 `targets` 必须包含各计划里的类**,不能只取负载索引:不在负载里的类不会被它看到,接口计划与运行时 stub
   会静默失效。
4. **游戏目录里要有 `optionsof.txt`**(见上文,OptiFine J1/J2 的坑)。
5. 启动参数要 `earlyWindowProvider=none`,并且**不要最小化窗口**、`options.txt` 里 `enableVsync=false`;否则渲染线程会
   卡在 `glfwSwapBuffers`,读起来像"没起来"。
6. `[OptiFine]` 行数按 `latest.log` 原始条数记录;rig 打印的那个数是三个来源合并后的匹配数,正好是它的两倍。

第 1、2、3 条已经写进 rig 的 `add-line.ps1`:一条命令把"下载原版客户端 → 装 NeoForge(含安装器 IPv6 补种重试)→
跑离线管线 → 生成两条计划 → 补运行时 stub → 按目标构建加载器 jar → 组装两个 jar → 放 `optionsof.txt`"串起来,
剩下的只有 `launch.ps1` 与判读。

### 1.21.8 的根因:负载与运行时的结构性冲突

**1.21.8:四项判据在本机复现通过(`[OptiFine]` 344 行,记录为 337)。** 本轮按**离线换类**路线
重建了这一版(`preview_OptiFine_1.21.8_HD_U_J6_pre16` + NeoForge `21.8.54`),第一次跑停在 `Setting user` 之后:模型重载抛
`ExceptionInInitializerError`(索引 7 越界,长度 7),没有图集、没有声音引擎。根因由新增的离线工具 `PayloadDrift` 定位:
**补丁负载是 OptiFine 为它自己的基线编出来的,与这条 NeoForge 线的运行时在结构上不一致**,而这类不一致成员级回填够不到。

| 类 | 冲突 | 现象 |
|---|---|---|
| `ModelDiscovery$ModelWrapper` | `SLOT_COUNT` 负载 7 / 运行时 8(NeoForge 加了第 8 个槽位 `KEY_ADDITIONAL_PROPERTIES`) | 该常量内联进 `slot(int)` 的 `Objects.checkIndex` 与 `fixedSlots` 的数组长度,回填计划写进去的 `KEY_ADDITIONAL_PROPERTIES = slot(7)` 在该类自己的 `<clinit>` 里越界 |
| 14 个类 | 负载缺运行时才有的 NeoForge 扩展接口(如 `UnbakedGeometry` 缺 `UnbakedGeometryExtension`) | 运行时的 `ModelWrapper` 调用 `UnbakedGeometry.bake(..., ContextMap)`,而该方法只由这个扩展接口声明 |

两处都不是"某个成员写错",而是**已内联的常量**和**不在被替换类里的接口**:前者改不了,后者加不上。所以本轮加了两条
由 `PayloadDrift` 离线实测生成的计划:

- `keep-runtime.txt`(`owner<TAB>*`):整类保留运行时的副本,并把该类的补丁条目从 OptiFine jar 里删掉。只做前者不够 ——
  实测 OptiFine 的转换服务注册在**前面**(顺序 `[mixin, OptiFine, fml, OptifiNeoforge]`),而它打出来的类是它自己的编译结果,
  于是"不装负载"留下的恰恰是 OptiFine 的那一份;补丁条目删掉之后,运行时的类才会被加载。
- `runtime-interfaces.txt`(`owner<TAB>interface`):把运行时自己的扩展接口补回去,对**所有**被转换的类生效,不只是被替换的那些
  (上面这张表的第二行就是没补的结果)。

同轮量到并修正的两处 rig 问题:① `MissingTargets --stub` 必须给足运行时 classpath(游戏 jar + NeoForge universal + 174 个库 jar)。
只给游戏 jar 时它报 126 个缺失、写出 804 行 stub 文件,而其中几乎全是库成员;给全之后只剩 16 个,而这 16 个里有
`BlockModelPart.layer()` —— 少了它模型烘焙在 `SingleVariant.<init>` 就崩;补出来的 payload 才是要交给加载器的那一个。
② 转换器的 `targets` 不能只取负载索引,否则不在负载里的类根本不会被它看到,接口计划与运行时 stub 会静默失效。

口径说明:rig 的 harness 打印的 `[OptiFine] lines` 是 stdout、stderr 与 `latest.log` 三个来源**合并后**的匹配数,同一批行
会被计两次,所以它显示的是上面那个数的两倍(1.21.8 显示 688、1.21.4 显示 464);文档里所有版本记录的都是 `latest.log` 的
原始条数。688 与 337 曾经看起来像两倍关系,核实后不是:原始条数为 344,与 337 只差 7 行。

那一轮 1.21.8 还截图确认了"确实有画面":窗口标题 `Minecraft NeoForge* 1.21.8`(标题在两版都一样,不是判据),
`PrintWindow` 抓到的帧均值 RGB 85,83,81、量化色桶 125、最常见颜色只占 15%;对照组(标题界面)是 79,80,76 / 99 桶,
而卡在加载遮罩时抓到的是均值 237,78,87、单色占 84% 的红屏。1.21.8 这一帧与对照组同类,**不是**加载遮罩。
截图只说明"画面有内容",不等于标题界面逐像素正确 —— 这一项按"有画面"记录,不按"界面正确"记录。

两列 OptiFine 都只表示"该构建存在",不代表可用;这里也不表示正式版比 preview 更适合移植。

- mod id `optifineoforge`,仅客户端,全线要求 **Java 21**。
- **一个 jar 只对应一个 MC 版本**:十个版本分属十条 NeoForge 线,元数据与命名空间要按版本各自处理,不能混用,也不能拿别的线的 jar 顶替。
- **三个版本的 NeoForge 只有 beta**:1.21.6(`21.6.20-beta`)、1.21.7(`21.7.25-beta`)、1.21.9(`21.9.16-beta`)这三条线在 `maven.neoforged.net` 上**没有任何非 beta 构建**,所以这三个产物只能用 beta 版 NeoForge 测试,用户也要在启动器里允许 beta 版本。
- **1.21.2 与 1.21.5 没有任何 OptiFine 构建**,不在支持范围内。
- OptiFine 只出到 preview 的版本有不少:1.21、1.21.6、1.21.7、1.21.8、1.21.9、1.21.10 这六版的构建全是 preview。
- 十个版本合计 94 个 OptiFine 构建,逐条列在 `docs/VERSIONS.md`。

## 安装

发布的是**加载器侧**(jar 里没有任何 OptiFine 的类)。要得到能直接放进 `mods/` 的成品,按本项目的 rig 流水线
对**你自己下载的、与该 MC 版本严格一致**的 OptiFine jar 做重打包与打补丁(1.21.1 / 1.21.3 / 1.21.8 上还包含
离线换类与成员回填,1.21.4 走 OptiFine 自己的运行期补丁),然后把产物放进这个版本自己的 `mods/` 目录,
用对应的 **NeoForge** 版本启动(1.21.6 / 1.21.7 / 1.21.9 上要允许 beta 版本)。不要用启动器注入 OptiFine 的版本
(那会与本模组重复)。上面每一条验收数字都是这样量出来的。

## 构建

需要 **JDK 21**(整条线统一)。仓库根目录就是 Gradle 项目,目标版本用 `-Pmc` 切换(非默认目标必须同时给
`-Pneoforge` 与 `-Pmountpoint`,以免"没验证过的配对看起来像支持"):

```powershell
.\gradlew build                                                                   # 默认目标:1.21.4 / 21.4.149
.\gradlew build "-Pmc=1.21.8"  "-Pneoforge=21.8.54"  "-Pmountpoint=modlauncher"   # 这一代还带 ModLauncher
.\gradlew build "-Pmc=1.21.11" "-Pneoforge=21.11.45" "-Pmountpoint=fml10"         # 这一代已经没有 ModLauncher
```

**在 PowerShell 里 `-P...` 必须加引号**:不加时 `-Pmc=1.21.11` 会被拆开,报
`Task '.21.11' not found in root project`(Gradle 9.6.1 实测);`cmd.exe` 下不加引号也可以。

`-Pmountpoint` 决定编哪个挂载点源码根:1.21 – 1.21.8 的 NeoForge 还带 ModLauncher,编 `src/ml11/java`
(实现 `ITransformationService` 的转换服务与各 transformer);1.21.9 起 NeoForge 已经没有 ModLauncher、
`cpw.mods.modlauncher.api` 随之消失,那批类编不过,所以 `fml10` 不编任何挂载点根。

产物为 `build/libs/OptifiNeoforge-<版本>+mc<MC 版本>.jar`,例如 `OptifiNeoforge-1.0.0+mc1.21.8.jar`。
**十条线的目标现在都能构建,而且十条各实测构建过一次**;两种挂载点的产物内容不同:

| Minecraft | NeoForge | `-Pmountpoint` | jar 大小 | 条目 | `loader/**` |
|---|---|---|---|---|---|
| 1.21 | 21.0.167 | `modlauncher` | 161 233 字节 | 55 | 16 个类 |
| 1.21.1 | 21.1.250 | `modlauncher` | 161 234 字节 | 55 | 16 个类 |
| 1.21.3 | 21.3.97 | `modlauncher` | 161 235 字节 | 55 | 16 个类 |
| 1.21.4 | 21.4.149 | `modlauncher` | 161 235 字节 | 55 | 16 个类 |
| 1.21.6 | 21.6.20-beta | `modlauncher` | 161 238 字节 | 55 | 16 个类 |
| 1.21.7 | 21.7.25-beta | `modlauncher` | 161 239 字节 | 55 | 16 个类 |
| 1.21.8 | 21.8.54 | `modlauncher` | 161 235 字节 | 55 | 16 个类 |
| 1.21.9 | 21.9.16-beta | `fml10` | 109 769 字节 | 39 | **0 个类** |
| 1.21.10 | 21.10.64 | `fml10` | 109 765 字节 | 39 | **0 个类** |
| 1.21.11 | 21.11.45 | `fml10` | 109 765 字节 | 39 | **0 个类** |

十条的离线工具都是 32 个类。字节数的差异只是元数据里那几行字符串的长短,不是代码差异:`1.21.9` 比同组的
另外两条大 4 字节,正是 `21.9.16-beta` 比 `21.10.64` / `21.11.45` 长 4 个字符。

`fml10` 的 jar 只有加载器侧工具与 mod 骨架,因为这三条线的挂载点是我们自己的 `ClassProcessor`,由 rig 编译
**进载荷 jar**。十个 jar 里都**没有** OptiFine 的类,也都**不含** `META-INF/services/`,所以都不是"放进 `mods/`
就能用"的成品 —— 这一点与 `docs/PUBLISHING.md` 对已发布 jar 的说明一致。构建通过**不等于**能跑:这台机器上
没有 rig,本轮没有重跑实机启动,十条的实机判据仍然只有 `docs/MATRIX.md` 里那一份。

## 工作原理(计划)

OptiFine 的 Forge 侧入口是一个 ModLauncher 服务:`META-INF/services/cpw.mods.modlauncher.api.ITransformationService` 指向 `optifine.OptiFineTransformationService`,它再通过 `optifine.OptiFineTransformer`(实现 ModLauncher 的 `ITransformer<ClassNode>`)把补丁类插进加载流程。NeoForge 同样跑在 ModLauncher 上,所以这条路可以复用,但需要处理三件事:

1. **元数据**:OptiFine 的 jar 里是 Forge 时代的 `META-INF/mods.toml`,NeoForge 需要它自己的那一份 `META-INF/neoforge.mods.toml`;
2. **命名空间**:这条线的运行期名预期已是官方(Mojang)名,不再是 SRG,可能不需要重映射;但切换点的确切位置**尚未确认**(它落在 1.20.5/1.21 前后,本线起点正在附近),错配会整段失效;
3. **补丁重叠**:NeoForge 自己也会改原版类,两边的改动需要按顺序合并,而不是互相顶掉。

设计细节与里程碑见 `docs/PLAN.md`。

## 已知限制

- **发布的是加载器侧,不是"装进 `mods/` 就能用"的成品**:Release 附的 jar 里没有 OptiFine 的类,成品要按上面的流水线用你自己的 OptiFine jar 合成。
- **1.21.2 与 1.21.5 没有 OptiFine 构建**,这两版不在支持范围内。
- **1.21.6 / 1.21.7 启用光影包会崩**,这是 OptiFine 侧自身的缺陷:这两版可以在不启用光影包时正常启动、正常渲染(标题界面无异常、无崩溃报告),但只要启用光影包,游戏就会在启动阶段崩:

  ```
  java.lang.NullPointerException: Cannot read field "norm" because "multiTex" is null
    at net.optifine.shaders.ShadersTex.initDynamicTextureNS(...)
  ```

  原因是这两版的 OptiFine 预览构建给纹理初始化插入的调用**缺少一个前置的 `setParentTexture` 关联**,而被调用的 `initDynamicTextureNS` 会直接解引用 `getMultiTexID()` 的结果。这两版可用的 OptiFine 构建共七个(1.21.6 三个 + 1.21.7 四个),行为一致,降级到更早的 preview 不能规避。**这是 OptiFine 补丁负载自身的问题,与本模组的加载器适配无关**;来源见下。
- 上面这条已经在**本线自己**的 1.21.6 / 1.21.7 上复现并确认过(验收因此不含启用光影包),不再是照搬姊妹项目的结论;1.21.7 的四个预览构建行为一致。另外 1.21 带一条已知的 Reflector 缺陷(OptiFine `J1_pre9` 每次启动往 stderr 写 4 条 `NoClassDefFoundError`,不影响启动)——两条都记在 `docs/MATRIX.md`。
- 这条线的 1.21.6 / 1.21.7 / 1.21.9 只能用 **beta 版 NeoForge**,beta 本身的变动会增加排查噪声。
- 与 OptiFabric 一样,**不包含、也不分发 OptiFine 本体**:OptiFine 的 jar 由用户自行获取,本项目只把它当作补丁来源。**这句是可核对的**:发布产物的 `net/optifine/**` 条目数为 **0**;曾经有两份源自 OptiFine 文件的 FXAA 后处理定义随 jar 分发,现在已改由本项目自己撰写(见 `docs/PUBLISHING.md`),因此产物里不含任何取自 OptiFine 的文件。

## 许可与致谢

- 本项目遵循 **MPL-2.0**(`LICENSE`)。**实现为本项目自写**;与 [OptiFabric](https://github.com/Chocohead/OptiFabric)(作者 Modmuss50、Chocohead)相同的是**做法本身** —— 把 OptiFine 在运行时补进游戏 —— 这一点在此致谢,但两者是不同的加载器(Fabric Loader 对应 NeoForge)、不同的实现,本项目**不是** OptiFabric 的移植或重传。2026-09-24 逐文件核对:源码中没有来自 OptiFabric 的署名或移植片段(源码里的 "Ported from …" 均指本项目自己分支之间的移植),故按事实表述。
- **不包含、也不分发 OptiFine 本体**:发布产物内 `net/optifine/**` 为 0 条,也没有任何取自 OptiFine 的文件;OptiFine 版权归 sp614x 所有,请自行获取。本项目未获 OptiFine 或 NeoForge 认可或支持。
- 各版本的构建列表、NeoForge 坐标与下载命令见 `docs/VERSIONS.md`,版本号规则见 `docs/VERSIONING.md`,设计与里程碑见 `docs/PLAN.md`。

### 补充记录:第 27 轮那次"投递"测量是空的(2026-09-19)

这一轮给探针加了 `ReloadProbe.saw(String)`(`delivered: <类名>`),并在 `ReloadProbeFix.transform` 开头对计划里的类调用它,
想看那 18 个监听器到底有没有被交给这个 transformer。实测 `delivered 0` 而 `entered 4` —— 两个数不可能同时为真,于是去查插桩本身:
**源码里没有那行调用,编译出来的 class 里也没有那个方法引用**(两处都查了)。`ReloadProbe.java` 那半落地了,`ReloadProbeFix.java`
里调用那半被 PowerShell 的字符串替换**静默吞掉**(锚点没匹配,替换什么也没做,grep 与编译都不会报错)。

所以 `delivered 0` **不是证据**;结论仍是上一轮的:`entered 4` 不变,18 个监听器为什么没被标记**原因未定**。教训具体且已被踩到过近似形态:
**在把"没有输出"当成结论之前,先确认插桩真的落地**(源码 grep 一次 + class 文件里查一次那个字符串,本轮就是这么查出来的)。
下一轮第一件事:用真正的编辑(而不是字符串替换)把那行调用放进去,先确认它出现在 class 里,再启动。

### 补充记录:投递不是问题,插桩点才是(第 28 轮)

把 `saw()` 的调用用真正的编辑放进去之后(并在**编译产物**里查过那个字符串确实存在,这是上一轮缺的那一步),实测:

| 计数 | 值 |
|---|---|
| `delivered`(计划里的类被交给这个 transformer) | **22 / 22** |
| `reload entered`(插桩真的挂上了) | **4** |

也就是说:**每一个计划类都被交到了这个 transformer 手上**(`delivered 22`,`in the plan but never delivered` 为空),
问题在**插桩点选错了**:18 个类里既没有 `reload` 也没有 `onResourceManagerReload` 这两个名字的方法。
这也把上一轮"18 个为什么没被标记"**从未定变成了已定**:不是投递,也不是名字形状,而是**它们根本没有那两个方法名** ——
这条线上大多数客户端监听器继承的是 `SimplePreparableReloadListener`,真正的入口是它定义的 **`prepare` / `apply`**,
`reload` 是基类里那个不再被重写的方法。下一轮只需再加一个名字(`prepare`),`entered` 就应该从 4 涨到 22,
那时的差集才是可信的"27 里缺哪一个监听器"。

### 补充记录:第三个入口名让命中从 4 变成 10(第 29 轮)

按上一轮的结论加了 `prepare`(并在编译产物里查过这个名字确实存在)之后,实测:

| 计数 | 值 |
|---|---|
| `delivered` | 22 / 22(不变) |
| `reload entered` | **4 → 10** |
| `started` | 25 |

方向是对的,但还没到位:`prepare` 覆盖了 6 个,剩下 15 个仍未标记(其中 `ObjLoader`、`BrandingControl`、`ClientModLoader`
不是可直接插桩的类,真正待查的是 12 个)。剩下的这 12 个既没有 `reload`、`onResourceManagerReload`,也没有 `prepare` ——
而这正好指向 `SimplePreparableReloadListener` 的另一半:**它同时定义 `prepare` 与 `apply`,多数监听器只实现其中一个**,
另一个用基类的空实现,所以"只插桩要覆盖的入口"这一步还差 `apply` 这个名字。下一轮加上 `apply`(第四种名字)后,
`entered` 应当接近 22,那时的 `started - entered` 才是可信的"哪一个监听器从未到达 barrier"。

### 补充记录:第四种入口名 `apply` 既没多命中、又把这一次跑坏了(第 30 轮,已回退)

按上一轮的推断加了 `apply` 之后,实测**两项都不好**:`entered` 仍是 **10**(没有多标记任何一个),而 `barrier reached`
从 27 掉到 **0** —— 也就是说这一次运行在到达重载 barrier 之前就停了。**这个改动已经回退**(条件里只剩
`reload` / `onResourceManagerReload` / `prepare` 三个名字),回到上一轮那份**已实测**的状态:`delivered 22`、`entered 10`、
`arrived 27`、`started 25`。文件里还留着那个不再被引用的 `APPLY` 常量(无害,但下次清理时应当删掉它和它的注释 —— 注释里写的
推断已经被这次实测否掉了)。

回退后的状态**没有重新跑一遍确认**(上下文用尽),但它是逐字回到上一轮已经量过的那份代码,所以上面那组数字仍然成立;
被否掉的是"再加一个 `apply` 就能盖住剩下 12 个"这个推断 —— 现在它是**被实测否定的**,而不是待验证的。

这条线到这里的状态因此是:**1.21 仍未通过**(注册失败已修掉、`Setting user` 通过、stderr 与记录逐字相同 14 141 字节、本次运行无崩溃报告),
剩下的唯一缺口是"28 个监听器里有 1 个从不到达 barrier";定位它的工具已经能覆盖 10 个,而且**每加一个名字都要重新量一次**,
不能按推断直接叠。
