# 设计与里程碑(1.21.x 线)

## 目标

让 OptiFine 在 **NeoForge** 上工作,做法与 OptiFabric 在 Fabric Loader 上一样:不去重新实现 OptiFine,而是

1. 用 OptiFine 自带的补丁器把它自己的补丁打进原版客户端;
2. 重建补丁类里被搬走的 lambda;
3. 按目标版本的命名空间把结果对齐;
4. 把打过补丁的 Minecraft 类交给 NeoForge 的类转换流程,让它们顶替原版类。

OptiFabric 在 Fabric 侧走的是 `GameTransformer.patchedClasses`。NeoForge 侧对应的位置还没有最终确定,这是本线第一个要解决的问题(见下)。

本线要覆盖 **1.21、1.21.1、1.21.3、1.21.4、1.21.6、1.21.7、1.21.8、1.21.9、1.21.10、1.21.11** 十个 MC 版本。十个版本分属十条 NeoForge 线,所以里程碑的每一条都要逐版本判据,不能由一个版本的结果外推。

## 本线的额外差异

| 方面 | 1.21.x 线的情况 |
|---|---|
| NeoForge 供给 | 十版分属十条线(`21.0` – `21.11`);其中 **`21.6`、`21.7`、`21.9` 只有 beta 构建**(最新 `21.6.20-beta` / `21.7.25-beta` / `21.9.16-beta`),这三版只能在 beta 版 NeoForge 上测 |
| Java | 全线 **21**,CI 一套即可 |
| mod 元数据文件 | 预期全线是 `META-INF/neoforge.mods.toml`;具体字段要求要逐版本核对 |
| 运行期命名空间 | 预期已是官方(Mojang)名,不再是 SRG;**确切切换点待确认**(它大约落在 1.20.5/1.21 前后,而本线起点正在附近) |
| OptiFine 供给 | 1.21.2 与 1.21.5 完全没有 OptiFine;1.21、1.21.6、1.21.7、1.21.8、1.21.9、1.21.10 六版只有 preview |
| 上游缺陷 | **1.21.6 / 1.21.7 启用光影包必崩**,来自 OptiFine 预览构建自身(缺一个前置的 `setParentTexture` 关联),七个构建行为一致 |
| OptiFine 系列号 | `J6` 横跨 1.21.6 – 1.21.8,`J7` 横跨 1.21.9 – 1.21.10,同号系列的构建能否互相替代未验证 |

## 与 Fabric 线的关键差异

| 方面 | OptiFabric(Fabric) | 本项目(NeoForge) |
|---|---|---|
| 补丁时机 | Fabric Loader 的 `GameTransformer`,在 Mixin 之前 | ModLauncher / NeoForge 的转换流程,顺序需要核实 |
| OptiFine 自身 | 纯字节码补丁 + 自己的类,没有 loader 集成 | **自带 Forge 时代的 loader 集成**(`optifine.OptiFineTransformationService`) |
| 元数据 | 不涉及 | OptiFine 的 jar 里是 `META-INF/mods.toml`,NeoForge 期望自己的 `neoforge.mods.toml` |
| 命名空间 | official → intermediary | 这条线预期是官方(Mojang)名,不需要重映射;若某个版本仍在 SRG 一侧,则要按版本分支 |
| 第三方补丁 | 只有 Fabric API 的 mixin | NeoForge 自己也会改原版类,补丁需要合并 |
| NeoForge 供给 | 不涉及 | 1.21.6 / 1.21.7 / 1.21.9 只有 beta 构建,可测性本身受限 |

## 两条可选路线

**路线 A —— 让 OptiFine 自己的 ModLauncher 服务跑起来。**
OptiFine 的 `optifine.OptiFineTransformationService` 只依赖 `cpw.mods.modlauncher.*`(`ITransformationService`、`ITransformer<ClassNode>`、`SecureJar`),不引用 `net.minecraftforge.*`。理论上只要让 NeoForge 发现并加载这个服务、并把它的元数据修好,补丁流程就能原样工作。代价是:顺序、投票(`castVote`)、与 NeoForge 自身补丁的合并都不在我们手里。

**路线 B —— 自己跑补丁器,自己交出补丁类(像 OptiFabric)。**
在 `preLaunch` 阶段调用 `optifine.Patcher` 打补丁、重建 lambda、对齐命名空间,然后把结果交给 NeoForge 的转换 API。可控性最高,代价是工作量大,而且要先弄清 NeoForge 允不允许整类顶替。**这条线比 1.20.x 轻松一点**:运行期名预期就是官方名,少了 SRG 对齐这一步 —— 前提是命名空间的待确认项按预期落定。

骨架阶段两条都留着:**先用最小代价验证路线 A 能不能成立**(它是"能不能跑"的问题),同时按路线 B 的形态组织代码(补丁器调用、缓存、fixer 框架都放在 `core` 里,不依赖具体挂载点)。

## 里程碑

| # | 内容 | 完成判据 |
|---|---|---|
| M0 | 骨架:三条分支、版本矩阵、构建配置、文档 | 本提交(文档部分)与随后的构建配置提交 |
| M1 | 路线 A 可行性:修好 OptiFine jar 的元数据,让 NeoForge 认它 | **逐版本**能启动到标题界面,日志里能看到 OptiFine 的转换服务被加载;先做 1.21.1 与 1.21.11 这两个有正式版 OptiFine 的版本 |
| M2 | 补丁管线:调用 `optifine.Patcher`,建立缓存(`<游戏目录>/.optifine/<OptiFine 版本>/`) | 首次启动完成补丁,二次启动走缓存 |
| M3 | 补丁类注入 + fixer 框架:补回被搬走的方法、处理与 NeoForge 自身补丁的重叠 | 进世界不崩,方块/物品/区块渲染正常 |
| M4 | 完整兼容:光影包、抗锯齿、连接纹理;第三方模组(尤其依赖 NeoForge 渲染钩子的) | 与 OptiFabric 在 Fabric 上的验收口径对齐;**1.21.6 / 1.21.7 的验收口径要分开写**:这两版只能按"不启用光影包"判据,光影缺陷属于 OptiFine 侧,先挂起 |
| M5 | 发布:版本脚本、发布说明、CurseForge / Modrinth 元数据 | 能一条命令出包并发布,十个产物各自打包;只有 beta NeoForge 的三版要在发布说明里写明 |

每条线按同样的里程碑推进,但各自独立验收。本线的 M1 建议从 1.21.1 与 1.21.11 入手:这两版有 OptiFine 的正式版,先把管线跑通,再往只有 preview 的版本上铺。

## 上游缺陷与它的处理边界

1.21.6 与 1.21.7 的七个 OptiFine 构建在启用光影包时必崩(空指针,`multiTex` 为 null,崩在 `net.optifine.shaders.ShadersTex.initDynamicTextureNS`),根因是这些预览构建注入的调用**缺少前置的 `setParentTexture` 关联**。这是 OptiFine 补丁负载自身的问题,与加载器无关,所以:

- 本项目**不把它当成 NeoForge 适配的失败**,M4 上这两版按"不启用光影包"验收;
- 本项目也**不承诺**去修它:若要修,等于在本项目里改写 OptiFine 的补丁结果(属于 fixer 的职责范围),要先评估值不值得;
- 更省事的路线是等 OptiFine 出新构建;新构建出现前,这两版的状态在文档里保持"已知缺陷"而不是"不支持"。

## 提交纪律

- 分支互相独立:`1.20.x`、`1.21.x`、`26.x` 各自有自己的 `README`、矩阵、构建配置和文档,不做跨分支的合并,也不跨线复制版本号。
- 线内也要按版本立据:十个版本的结论不能互相顶替,写文档时要说清是哪一个版本;`J6` / `J7` 这种同号系列尤其容易混。
- 文档与实测口径要一致:没在真实游戏里验证过的东西写成计划,不写成结论。
- OptiFine 的 jar 不进仓库,也不随产物分发。

## 2026-09-19/20:1.21.9 这条线的侦察结果(本轮实测)

在**本机重建的 rig** 上做了两件事的确认,结论是"加载器这一半已经能造,缺的是另外两半":

1. **加载器构建可以过**:在本分支上执行

   .\gradlew jar -Pmc=1.21.9 -Pneoforge=21.9.16-beta -Pmountpoint=fml10

   得到 uild/libs/OptifiNeoforge-1.0.0+mc1.21.9.jar(129 356 字节)—— 也就是本文件上面那段注释说的
   "loader-side tools and the mod skeleton alone",与预期一致 ✔。
2. **缺的两半**:
   a. **FML 10 的 ClassProcessor 要打包进载荷 jar**:26.x 分支的 src/fml10 只有两个类
      (OptifinePayloadClassProcessor / OptifinePayloadLocator),按注释它们要由 rig **编译进载荷 jar**
      (带着 OptiFine 的类),而不是编译进加载器 jar —— rig 目前没有这一步。
   b. **1.21.9+ 没有 ModLauncher**,而 rig 的 launch.ps1 是围绕 ModLauncher 写的(module path、-p、
      --launchTarget、ignoreList 等)—— 要跑这三条线,得给 rig 加一条 FML 10 的启动路径。

这两件事都**没有做**,所以 1.21.9 / 1.21.10 / 1.21.11 仍然是未实测的。
### 补充(同一轮的下一步):FML 10 的 ClassProcessor 确实能编出来(实测)

- **SPI 在哪里**:在 rig 的 libraries 里扫 515 个 jar,**只有一份**含有
  
et/neoforged/neoforgespi/transformation/ClassProcessor.class ——
  libraries\net\neoforged\fancymodloader\loader\10.0.14\loader-10.0.14.jar(随 NeoForge 21.9.16-beta 装进来的)。
- **编译配方(实测通过)**:26.x 那两份源码 + 上面这个 loader jar + libraries\net\neoforged\neoforgespi\**
  + log4j-api + rig 的 ASM jar ⇒ **2 个 class / 7470 字节的 jar** ✔。也就是说"把 ClassProcessor 编出来"这一步
  没有任何未知数。
- **仍然缺的**:① 按本文件上面的注释,这两个类要和 OptiFine 的类一起**打进载荷 jar**(rig 没有这一步);
  ② **1.21.9+ 的启动路径**(没有 ModLauncher,launch.ps1 那套 module path / --launchTarget 都不适用)。
  ②是这三条线唯一的大件,做完才能谈"实测"。
### 再补充:FML 10 的启动路径侦察(实测到这里,尚未打通)

装好 NeoForge **21.9.16-beta** 之后看了它自己的 profile,量到:

- mainClass 是 **
et.neoforged.fml.startup.Client**(不是 cpw.mods.bootstraplauncher.BootstrapLauncher),
  **没有 ModLauncher**,也**没有** --launchTarget / module path —— 只有三个 --fml.* 参数
  (
eoForgeVersion / mcVersion / 
eoFormVersion),其余按父 profile(1.21.9.json)的常规游戏参数给。
- 按这条路径手工拼 classpath 起了一次**无 mod 的对照**:
  进程**能起来并活着超过 90 秒**,也写出了 logs/latest.log ✔ —— 但 FML 自己的后台扫描报
  java.lang.IllegalStateException: zip file closed(BackgroundScanHandler → CompositeJarContents.visitContent
  → Scanner.scan),即"扫描某个 jar 时它已经被关掉"。也就是说**手工拼的 classpath 不足以让 FML 10 接管这些 jar**,
  官方启动器显然是按另一种方式把 jar 交给它的。
- 顺带澄清一个假警报:按 profile 解析 classpath 时会看到 30 个"缺失"的 jar,但那**全是别的平台的 natives**
  (linux / macos ✗),rig 的 etch-libraries.ps1 本来就按规则跳过它们,Windows 上不需要。

**结论**:1.21.9+ 的三条线要能实测,还差"按 FML 10 期望的方式准备并启动"这件事 —— 本轮没打通,
所以 1.21.9 / 1.21.10 / 1.21.11 仍然是**未实测**。
### FML 10 侦察的第三批实测:它其实走得很远,然后在 0.8 秒内自己关掉

把启动参数补全(--gameDir/--assetsDir/--assetIndex/--username 等,只留 libraries 在 classpath 上,不再把 client jar
塞进去)之后,FML 10 的日志显示:

- Starting FancyModLoader version 10.0.14 (CLIENT in PROD) ✔
- Loading ImmediateWindowProvider fmlearlywindow + **GL info: AMD Radeon RX 7800 XT GL version 3.3.0 Core**
  —— 也就是说**它真的开了早期窗口并拿到了 GL 信息** ✔
- Mod List: Minecraft 1.21.9 (minecraft) / NeoForge 21.9.16-beta (neoforge) ✔
- Building game content classloader: minecraft (composite(jar(client-1.21.9-...-srg.jar))) ... ✔
- **紧接着一行就是 Closing FML Loader** —— 从启动到关闭只有约 **0.8 秒**,而且之前**没有任何 ERROR**;
  那条 An error occurred scanning file ...client-1.21.9-...-srg.jar(zip file closed)是**关闭之后**才出现的,
  也就是**结果而不是原因**。

顺带否掉一个我先前的猜测:"client jar 也在 classpath 上"导致冲突 —— 去掉之后(只留 libraries)**现象完全一样** ✗。

**下一步**(记录在这里,便于接着做):用 -Xlog:exceptions=trace 把主线程那个"安静退出"的原因抓出来
(这个手法在本会话里已经用成功过一次:1.20.4 的原始异常就是这么挖出来的),或者对照 NeoForge 官方启动器
在 profile 之外还做了什么。
### 重大一步:FML 10 的启动路径**打通了**(无 mod 对照已进到标题界面路径)

上一节记的"0.8 秒安静退出"找到原因了 —— 是**我漏了 profile 里的 JVM 参数**,不是 FML 或 OptiFine 的问题:

- 父 profile(1.21.9.json)的 rguments.jvm 里有四个 **natives 相关属性**:
  -Djava.library.path、-Djna.tmpdir、-Dorg.lwjgl.system.SharedLibraryExtractPath、-Dio.netty.native.workdir
  (都指向 rig 的 
atives),另有 -Xss1M 与 -Dminecraft.launcher.brand/version;
- 子 profile(NeoForge)额外给 --add-opens java.base/java.lang.invoke=ALL-UNNAMED 与
  --add-exports jdk.naming.dns/com.sun.jndi.dns=java.naming。

把这些**全部**给上之后(其余同前一节:只把 profile 的 libraries 放进 -cp、-DlibraryDirectory 指向 rig 的
libraries、mainClass 用 
et.neoforged.fml.startup.Client、游戏参数用父 profile 那套),
**无 mod 对照跑起来了**:

`
[Render thread/INFO] [net.minecraft.client.Minecraft/]: Setting user: Dev
[Render thread/INFO] [net.minecraft.client.sounds.SoundEngine/SOUNDS]: Sound engine started
`

进程活着(70 秒后由我主动结束)、日志 12 010 字节 ✔。也就是说 **1.21.9 这条线的启动方式不再是未知数了** ✔
—— 这是 1.21.9 / 1.21.10 / 1.21.11 三条线此前最大的拦路石。

**注意边界**:这是**无 mod 对照**(没有 OptiFine、没有本项目的载荷),所以它既不等于"这三条线通过",也不改变
那三条线"未实测"的状态。还差的两件仍是:① 把 26.x 的 src/fml10(两个类,已实测可编译)按注释**打进载荷 jar**;
② 在 rig 里把上面这套参数固化成一条 **FML 10 的启动路径**(launch.ps1 目前只懂 ModLauncher)。
### rig 现在有了 FML 10 的启动路径(实测:无 mod 对照 VERDICT: STARTED)

新增 optifineoforge-test\launch-fml10.ps1(ModLauncher 那套留在 launch.ps1)。它做四件事:

1. 顺着 inheritsFrom 把 profile 链读出来(NeoForge profile → 原版 profile),按"父先子后、后者覆盖"合并 libraries,
   用它们拼 -cp ——**不把 client jar 放进去**(实测:放进去与否现象一样,FML 自己会用 -DlibraryDirectory
   加 --fml.neoFormVersion 找到游戏 jar);
2. 展开两边 rguments.jvm 与 rguments.game 里的占位符,并且**跳过 rule 条目**
   (实测:第一个版本照搬了 -XstartOnFirstThread 这条 macOS 专用规则,JVM 直接拒绝启动:
   Unrecognized option: -XstartOnFirstThread);
3. 用 
et.neoforged.fml.startup.Client + 常规游戏参数启动,支持 -Mods(拷进 mods/)、-Fresh、-Seconds;
4. 打印与 launch.ps1 **同样形状**的 VERDICT 块(Setting user / 声音引擎 / 新崩溃报告 / stderr 字节 /
   [OptiFine] 行数),便于两条路径的结果直接对照。

实测(1.21.9 / NeoForge 21.9.16-beta,无 mod):

`
===== VERDICT: STARTED =====
  Setting user        : True
  Sound engine started: True
  new crash reports   : 0
  stderr bytes        : 0
  [OptiFine] lines    : 0  (latest.log, not doubled)
`

也就是说 **1.21.9+ 的"启动"这一半做完了**。三条线还差的最后一件事是把 26.x 的 src/fml10(已实测可编译)
与 OptiFine 的类一起**做成 FML 10 会发现的那个载荷 jar**,然后用这个脚本去跑。
### 载荷 jar 到底要装什么(从 src/fml10 两个类的说明里读出来的,附出处)

下一步"打包 fml10 载荷"的配方,现在不是猜的了 —— 两个类自己的文档写清了 FML 10 的发现机制:

- OptifinePayloadClassProcessor **通过 META-INF/services/net.neoforged.neoforgespi.transformation.ClassProcessor
  注册**(FMLLoader.createClassProcessorSet → ServiceLoaderUtil.loadServices),它做的事是把**已经打好补丁的游戏类
  覆盖到游戏自己那份上**;而那些成品类按约定放在**同一个 jar 的 srg/ 下**(rig 事先把 patch/srg/** 应用到原版归档
  的结果,也就是本仓库离线管线 optifine-patched.jar 的布局)。
- OptifinePayloadLocator 之所以存在,是因为
  
et.neoforged.fml.loading.EarlyServiceDiscovery.SERVICES 正好只有
  {IModFileCandidateLocator, IModFileReader, IDependencyLocator, GraphicsBootstrapper, ImmediateWindowProvider} ——
  **ClassProcessor 不在其中**。所以"只声明 ClassProcessor 的 mods/ jar"不会被预加载、处理器永远不会被看到;
  那段注释还记了实测现象:那种 jar 跑到了标题界面,却**零条 [OptiFine]、日志里连处理器都没有**。
  因此这个 jar **还要**声明一个 IModFileCandidateLocator(服务文件
  META-INF/services/net.neoforged.neoforgespi.locating.IModFileCandidateLocator),并且要靠自己的元数据
  被普通的 mods/ 扫描发现。

**所以 1.21.9 的载荷 jar = 离线管线产出的 srg/** 成品类 + 那两个 fml10 类 + 两个服务文件 + META-INF/neoforge.mods.toml。**
(本轮只把这份配方记下来;**组装脚本还没写**。)
### 第一次真正跑 1.21.9 载荷:机制全部按设计工作,游戏在装完类之后安静退出

本轮做成并实测了三件事:

1. **载荷 jar 组装成功**(按上一节那份配方,临时脚本内联):work\1.21.9\optifine-patched.jar 里的
   **1233 个 srg/** 成品类** + 两个 fml10 类 + 两个服务文件 + META-INF/neoforge.mods.toml
   ⇒ jars-1.21.9\optifine-payload-fml10.jar(**2 999 556 字节**)。
2. **FML 10 确实把它当载荷吃下去了**(launch-fml10.ps1 的日志):
   - mods/optifine-payload-fml10.jar 被发现 ✔
   - OptifiNeoforge: early service jar recognised; the payload jar itself is disco...(locator 起作用 ✔)
   - OptifiNeoforge: OptifinePayloadClassProcessor constructed (FML 10 mount point) ✔
   - OptiFine payload: 517 finished game classes ✔,随后一条条
     OptiFine payload: installed net.minecraft.util.Mth (34 fields, 109 methods) [1 so far] …(到 16 条时日志中断)
3. **然后仍然是安静退出**:Closing FML Loader → Clearing ModLoader,**没有 ERROR、没有异常、stderr 0 字节**,
   Setting user 没出现。注意:**无 mod 的对照是能跑到 Setting user 的**(第 56 轮),所以问题出在装进去的类上,
   而不是启动方式。

**下一步**(明确):给这次启动加 -Xlog:exceptions=trace(本会话已两次用这招挖出"看不见"的原因),看主线程在
"装了十几二十个类"之后到底抛了什么 —— 或者先只放**少数几个类**的载荷做二分。
### 二分的结果:不是"某个类坏",而是"载荷一被装上 FML 就关掉自己"

- 用 -Xlog:exceptions=trace 抓:整份载荷跑完后日志**最后一条异常**仍是无关的 AWT/字体噪音(2.8 秒处),
  **没有任何异常伴随那次关闭** —— 无 mod 的对照也是同样的收尾,但它能继续走到 Setting user。
- 于是做二分:把载荷缩到**只含一个类**(srg/net/minecraft/util/Mth.class,jar 共 19 016 字节),
  OptiFine payload: 1 finished game classes → installed net.minecraft.util.Mth (34 fields, 109 methods) [1 so far]
  → **紧接着仍然是 Closing FML Loader**。
- 也就是说:**不是某个类把游戏弄坏**,而是"载荷一旦被装上,FML 就把自己关掉"。
  下一步要看的是处理器与 FML 10 交互的这一段(例如它在安装时是否动了 FML 正在扫描的那个 jar —— 之前出现过
  zip file closed 的抱怨,可能就是这条线索的另一面),而不是继续缩小类的范围。
### 定位到唯一一处:是 ClassProcessor 的安装动作,不是 jar / locator / 元数据 / 类本身

同一个载荷 jar,**只去掉那条 ClassProcessor 的服务文件**(其余——locator 服务、
eoforge.mods.toml、那个成品类——
完全一样),启动结果就变成:

`
===== VERDICT: STARTED =====
  Setting user        : True
  Sound engine started: True
  new crash reports   : 0
  [OptiFine] lines    : 0
`

也就是说:jar 本身没问题、locator 没问题、元数据没问题、类也没问题,**唯一让 FML 10 关闭自己的就是"处理器真的去装类"这一步**。
剩下要查的就只有 OptifinePayloadClassProcessor 的安装实现与 FML 10 扫描/持有 jar 的方式之间的相互作用
(它自己去读同一个 jar 的那段代码是最可疑的地方,之前那条 zip file closed 也指向这里)。
### 1.21.9 这一轮收口(下一步的实验已经指名)

已经站住的部分:

- **启动方式** ✔:launch-fml10.ps1(rig),无 mod 对照 VERDICT: STARTED;
- **载荷投递** ✔:载荷 jar 被 FML 10 发现,locator 服务、
eoforge.mods.toml、srg/** 成品类都按设计工作
  (OptiFine payload: 517 finished game classes → 逐条 installed ...);
- **唯一卡点** ✗:只要那条 ClassProcessor 服务文件在,处理器一开始装类,FML 10 就 Closing FML Loader
  (无异常、stderr 0;把服务文件去掉则一切正常跑 Setting user —— 这一步已经把范围钉死了)。

下一轮该做的实验(按代价排序):

1. **把服务文件留着,但让 	argets() 返回空集**:这样能区分"FML 10 不喜欢**有处理器注册**"和
   "FML 10 不喜欢**处理器真的装类**"这两件事 —— 本文件里 	argets() 的形状很直白(遍历 payload().keySet()
   造 Target),改一行就能试;
2. 若上一步证明是"装类"本身,RU 可疑处是 	ransform 里把成品 ClassNode 覆盖到 
ode 上的那段
   (FML 10 的扫描线程可能正在读同一批 jar),再逐层缩小;
3. 另一条互不排斥的路:不注册处理器,改用**事先把成品类覆盖进游戏 jar**(rig 层面做完),让这条线像 1.20.x/1.21.x
   那样"不改类也能跑",代价是失去了运行期换装。
### 一行实验的结论:FML 10 允许"注册处理器",不允许它"真的装类"

把 	argets() 改成返回空集(**处理器照旧注册,服务文件照旧在**,只是一个类都不声明),其余全部不变:

`
===== VERDICT: STARTED =====
Setting user        : True
Sound engine started: True
new crash reports   : 0
`

日志里 OptifinePayloadClassProcessor constructed (FML 10 mount point) 仍在 ✔,而 installed ... 一条都没有 ✔。
也就是说:

- **"有 ClassProcessor 注册"本身完全没问题**;
- 问题精确地落在**它声明并安装类**这一步 —— 即 	argets() 报出的那 517 个类,以及 	ransform 里
  copy(finished, node) 把成品覆盖回去的那段。

**下一次的一行实验**(把这个范围再切一半):保留 	argets() 照常报类,但让 	ransform 只记日志、**什么都不改**
(即 copy 不执行)——
- 若仍会关闭 ⇒ 问题在"声明了这些类"这一侧(FML 10 对处理器声明面的处理);
- 若正常跑起来 ⇒ 问题就在 copy(覆盖 ClassNode 的具体做法,FML 10 的扫描线程可能在读同一批结构)。
### second bisect:问题不在"声明类",而在"真的把成品覆盖上去"那一步

保留 	argets() 照常声明那个类(FML 会正常询问它),但让 	ransform **什么都不做**(取 payload() 的那行改成
直接置空,于是方法立刻返回):

`
===== VERDICT: STARTED =====
Setting user        : True
Sound engine started: True
new crash reports   : 0
`

⇒ **声明这些类没问题,copy 那步才是元凶**。两次一行实验合起来把范围钉到了唯一一处:
OptifinePayloadClassProcessor.transform → copy(finished, node)(把成品 ClassNode 的内容覆盖到 FML 交来的

ode 上)。

**下一次要试的具体假设**(按可能性排序):

1. **就地改 FML 交来的那个 ClassNode 可能不被 FML 10 接受** —— 改为构造一个新的 ClassNode 并把它作为结果
   交回去(如果 SimpleClassProcessor 的契约支持)或者至少不共享字段/方法列表;
2. copy 里那次"取更宽可见性"的合并或接口字段归一化,可能产出 FML 10 的校验不接受的东西(1.20.x/1.21.x 的加载器
   是 ModLauncher 的 NodeTransformer,契约不同);
3. 兜底方案(与第 2 轮记过的那条一致):不注册处理器,改为**事先把成品类直接覆盖进游戏 jar**,让这条线"运行期不换装"。
### third bisect:不是"合并逻辑",而是"替换这个动作本身"

把 copy(finished, node) 换成**最简单的、完全不合并**的赋值(superName / interfaces / fields / methods / access /
version / signature 直接取自成品),再跑:

`
OptiFine payload: installed net.minecraft.util.Mth (34 fields, 109 methods) [1 so far]
Closing FML Loader 27329d2a
`

**照样关闭**。三个一行实验连起来:

| 变体 | 结果 |
|---|---|
| 服务文件在、	argets() 空集 | STARTED |
| 	argets() 照常声明、	ransform 空操作 | STARTED |
| 	ransform 里做**最小**的类替换(不合并) | **Closing FML Loader** |

⇒ **问题不在我们的合并逻辑**,而在"**在 FML 10 的处理管线里替换掉它交来的这个类**"这件事本身
(很可能是 SimpleClassProcessor 的契约:处理器不该改 
ode,而应通过上下文的 API 声明替换;
具体的契约要以 FML 自己的源码为准,本轮没有去读)。

**因此走兜底方案**:不注册处理器,改为**在 rig 层面事先把成品类覆盖进游戏 jar**
(libraries\net\minecraft\client\1.21.9-…\client-1.21.9-…-srg.jar 的副本),
这样这条线"运行期不换装",与 1.20.x/1.21.x 的验收环境等价(那两个分支的换装是运行期的,但验收看的是游戏能不能起来)。
### 兜底方案的实测结果:能起来,但 OptiFine 是"哑"的

按兜底方案做了:把载荷里 1233 个 srg/** 成品类写进游戏 jar 的副本(用 libraries\net\minecraft\client\1.21.9-…\
client-1.21.9-…-srg.jar,原件留成 .rig-original),实测 **替换 515 个游戏类、追加 716 个 OptiFine 自身的类**
(结果 23 289 537 字节),然后不带任何 mod 启动:

`
===== VERDICT: STARTED =====
  Setting user        : True
  Sound engine started: True
  new crash reports   : 0
  stderr bytes        : 0
  [OptiFine] lines    : 0
`

也就是说:**游戏起来了,但日志里一个 optifine 字样都没有** —— OptiFine 的代码根本没有被激活。
原因不难理解:在 ModLauncher 那几条线上,是 **OptiFine 自己的 transformation service** 把它启动起来的;
这条 FML 10 的路径上没有任何东西去启动它,光把**打过补丁的游戏类**放进去并不等于 OptiFine 在跑。
所以兜底方案**不足以**当作"这条线通过"的依据 —— 它只证明"预覆盖不炸",不证明 OptiFine 生效。

**结论(下一步)**:还是要修处理器这一侧 —— 去读 FML 10 SimpleClassProcessor / SimpleTransformationContext
的契约(本轮没读),搞清楚"替换"应该怎么声明(而不是就地改 
ode);或者找一个在无 ModLauncher 情况下
启动 OptiFine 自身初始化的方式。
### 读 FML 10 的处理器契约(本轮读到的东西)

从 rig 的 libraries\net\neoforged\fancymodloader\loader\10.0.14\loader-10.0.14.jar 里 javap 出来:

- SimpleClassProcessor:bstract void transform(ClassNode, SimpleTransformationContext) +
  bstract Set<Target> targets();handlesClass 与 **processClass 都是 final** ⇒ 用这个基类时,
  **处理器没有地方声明"我用哪种重写方式"**。
- SimpleTransformationContext 只有 	ype() / empty() / initialSha256() —— **没有**"声明替换"之类的 API,
  所以就地改 
ode 确实是设计用法(前面那条"就地改是不是不被接受"的猜测到此可以划掉)。
- 但 ClassProcessor 接口本身有 **ComputeFlags processClass(TransformationContext)**,而
  ClassProcessor 的取值是:**NO_REWRITE / SIMPLE_REWRITE / COMPUTE_MAXS / COMPUTE_FRAMES**。

**这给出一个很具体的、可验证的假设**:我们继承 SimpleClassProcessor,而它把 processClass 定成 final ⇒
FML 用它内部固定的旗标写回;如果那个旗标弱于 COMPUTE_FRAMES,而我们**整方法替换**了字节码(帧会变),
写出来的类就是帧不一致的 —— 症状正好可以是"FML 在处理过程中直接放弃,而且什么都不打印"。
**下一步**:改成直接实现 ClassProcessor 接口,在 processClass 里返回 ComputeFlags.COMPUTE_FRAMES
(以及 handlesClass 用同一批目标名),再看那三条线是否能起来。
## 1.21.9 的这一轮:FML 10 上的"静默失败"被拆开了,而且 OptiFine 真的跑起来了

这一节把三条线(1.21.9 / 1.21.10 / 1.21.11)的 FML 10 挂载点从"起不来且没有一行错误"推到了"OptiFine 已经在跑"。
全部结论都是这一轮在这台机器上量出来的,写清楚哪一条推翻了前面的猜测。

### 先推翻一条:`ComputeFlags` 不是原因

上一轮把 `SimpleClassProcessor` 的 `processClass` 是 `final`、而 `ClassProcessor` 接口有
`ComputeFlags processClass(...)` 当成主要嫌疑。`javap -c` 直接读出来是假的:

```
public final ClassProcessor$ComputeFlags processClass(ClassProcessor$TransformationContext);
   0: aload_0
   1: aload_1
   2: invokevirtual  ClassProcessor$TransformationContext.node()Lorg/objectweb/asm/tree/ClassNode;
   5: aload_1
   6: invokevirtual  transform:(Lorg/objectweb/asm/tree/ClassNode;LSimpleTransformationContext;)V
   9: getstatic      ClassProcessor$ComputeFlags.COMPUTE_FRAMES
  12: areturn
```

它**无条件**返回 `COMPUTE_FRAMES`(没有分支,没有 `empty()` 判断)。所以"基类给的旗标太弱"是错的,
改成直接实现接口不会改变任何事。这条到此结束。

### "没有任何错误"的机制:FML 把异常送进了一个模态对话框

`net.neoforged.fml.startup.Client.main` 的字节码是:

```
 10: invokestatic Entrypoint.startup([Ljava/lang/String;ZLnet/neoforged/api/distmarker/Dist;Z)LFMLLoader;
 25: invokestatic Entrypoint.createMainMethodCallable(LFMLLoader;Ljava/lang/String;)Ljava/lang/invoke/MethodHandle;
 31: invokevirtual MethodHandle.invokeExact([Ljava/lang/String;)V
 46: invokevirtual FMLLoader.close()V          <- "Closing FML Loader" 就是这里
 ...
 76: astore_1 / 77: invokestatic FatalErrorReporting.reportFatalError(Throwable;)V / 81: System.exit(1)
```

而 `FatalErrorReporting.reportFatalError(String)` 是:

```
 0: ldc "java.awt.headless" / 2: ldc "false" / 4: System.setProperty   <- 强制非 headless
 8: GraphicsEnvironment.isHeadless() / 11: ifne 21
14: showErrorUsingSwing(String)      -> JOptionPane.showMessageDialog(...)   <- 模态,阻塞
21: ... TinyFileDialogs.tinyfd_messageBox(...)
51: System.exit(1)
```

**所以 FML 10 上启动期抛异常的表现是**:stderr 0 字节、没有 crash-report、日志最后一行是
`Closing FML Loader`、JVM 一直不退出(对话框在等人点确定)。这不是"FML 加载器坏了",
是一个没人看的窗口。为了确认不是猜的,把该 JVM 的顶层窗口枚举出来:

```
1508380|vis|SunAwtDialog|Fatal Error          <- 就是它
 2625878|vis|GLFW30|Minecraft: NeoForge Loading...
```

**给 rig 加了两件东西**(都在仓库外,`optifineoforge-test\`):

* `diagnostic\kynarain\cn\optifineoforge\rig\DiagnosticClient.java` —— 继承
  `net.neoforged.fml.startup.Entrypoint`(`startup` 与 `createMainMethodCallable` 都是 `protected static`,
  子类可用),跑**同一条** FML 管道,但把 throwable 打到 stderr,然后 `Runtime.halt(1)`。
  只改失败可见性,不改任何变换路径。
* `launch-fml10.ps1 -MainClass <fqcn> -ExtraClasspath <dir>` —— 把入口点换成上面这个类。
* `capture-hwnd.ps1` —— 按标题抓一个顶层窗口(PrintWindow,失败再 CopyFromScreen),留着备用;
  本轮抓到了那张图,但当前模型读不了图像,所以真正解决问题的是上面那个入口点。

### 那条"最小替换"探针为什么死的:**探针罐里没有 OptiFine 自己的类**

换上诊断入口点后,第一次运行 stderr 就有 2036 字节,原因一目了然:

```
java.lang.NoClassDefFoundError: Could not initialize class net.minecraft.util.Mth
	at com.mojang.blaze3d.buffers.Std140SizeCalculator.align(...)
Caused by: java.lang.ExceptionInInitializerError: Exception java.lang.NoClassDefFoundError:
        net/optifine/util/MathUtils [in thread "main"]
	at net.minecraft.util.Mth.<clinit>(Mth.java:55)
```

OptiFine 编译出来的 `Mth` 的 `<clinit>` **调用 OptiFine 自己的 `net.optifine.util.MathUtils`**,
而那个探针罐里根本没有任何 `net/optifine/**`。于是类初始化失败 → 前面那三步二分法得到的结论
("在 FML 10 管道里替换一个类本身就会炸")**是错的**,作废:炸的是缺类,不是替换。

### 真正的坑:军规罐子(Early Service jar)的加载器看不见游戏类

把 OptiFine 自己的类**放进同一个 payload 罐子**再跑(`srg/net/optifine/**` 之外另加普通路径的
`net/optifine/**`),FML 的日志说得很清楚:

```
Found 1 early service jars (out of 1)
Loading FML Early Services:  - mods/optifine-payload-fml10-withown.jar
```

也就是说这个罐子被当成"早期服务罐",里面的类由一个普通 `URLClassLoader` 加载,而那个加载器
**一个游戏类都看不见**:

```
java.lang.NoClassDefFoundError: net/minecraft/world/level/chunk/ChunkAccess
	at FML Early Services//net.optifine.reflect.Reflector.<clinit>(Reflector.java:143)
Caused by: java.lang.ClassNotFoundException: net.minecraft.world.level.chunk.ChunkAccess
	at java.net.URLClassLoader.findClass(URLClassLoader.java:445)
```

(`Client` 里的那条旧注释记的 1.21.11 上的 `ValueOutput` 失败,根因就是这个,不是"某个加载器恰好没配好"。)

**修法:拆成两个 mod 文件**

1. `optifine-payload-fml10.jar` —— 还是早期服务罐:处理器两个类 + 两个 service 文件 +
   `META-INF/neoforge.mods.toml` + `srg/**`(要装到游戏类上的成品类,1233 个)。
2. `optifine-own-classes.jar` —— **普通** mod 文件(`neoforge.mods.toml`,modId `optifine`),
   装 OptiFine 自己的 `net/optifine/**`(716 个)、`assets/minecraft/**`、以及
   `net/minecraftforge/**` 桩类。

这一拆之后,`Reflector` 变成 `TRANSFORMER/optifine@1.0.0/net.optifine.reflect.Reflector`,
能正常解析游戏类,`net.minecraft.util.Mth.<clinit>` 也拿到了 `MathUtils`。

### 还差一层:Forge API 桩类必须在那第二个罐子里

拆开之后的第一个失败是帧计算阶段要去解析一个不存在的类:

```
Caused by: java.lang.RuntimeException: Cannot find class net/minecraftforge/common/extensions/IForgeLivingEntity
	at net.neoforged.fml.classloading.transformation.TransformerClassWriter.computeHierarchyFromFile(...:149)
	at org.objectweb.asm.Frame.merge(...)
	at ...ClassTransformer.transform(...:126)
	at TRANSFORMER/optifine@1.0.0/net.optifine.reflect.Reflector.<clinit>(Reflector.java:172)
```

把 rig 里已经准备好的 `work\1.21.9\stubs`(87 个 `net/minecraftforge/**` 桩类)加进
`optifine-own-classes.jar` 就好了。注意这与 1.20.1 那次踩的坑方向相反:那次把桩类塞进
**加载器罐**导致模块 `ResolutionException`,这里的正确位置是**普通 mod 文件**。

### 结果:1.21.9 上 OptiFine 已经在运行

```
===== VERDICT: FAILED =====
  Setting user        : True
  [OptiFine] lines    : 32
```

`latest.log` 里是完整的 OptiFine 自述:

```
[OptiFine] OptiFine_1.21.9_HD_U_J7_pre2
[OptiFine] Build: 20251002-002421
[OptiFine] LWJGL: 3.4.0 Win32 WGL Null EGL OSMesa VisualC DLL
[OptiFine] OpenGL: AMD Radeon RX 7800 XT, version 3.3.0 Core Profile Context 25.12.1.251128
[OptiFine] Maximum texture size: 16384x16384
[OptiFine] Checking for new version
```

也就是说:在这条线上 OptiFine 自己的代码真的被加载、被初始化、并且读到了显卡信息。
**这不是验收通过**(下面还有两个拦路的),但它把"FML 10 上 OptiFine 能不能活"这个问题答成了"能"。

### 剩下的两个拦路石(都已定位)

1. **`Options.loadOfOptions` 数组越界**(stderr,渲染线程):
   `ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1`
   at `net.minecraft.client.Options.loadOfOptions(Options.java:3174)`。
   游戏目录里有一个 1826 字节的 `optionsof.txt`,是先前某个版本写下的;先怀疑它,清掉再看。
2. **NeoForge 给游戏类补的成员被整类替换吃掉了**:
   `NoSuchMethodError: 'java.util.List net.minecraft.server.packs.resources.ReloadableResourceManager.getListeners()'`
   at `net.neoforged.neoforge.client.event.AddClientReloadListenersEvent.<init>`。
   现代 NeoForge 是把补丁直接打进游戏类的,OptiFine 那份编译结果里当然没有这个方法。
   这正是 1.20.x 那条线上 `keep-runtime.txt` 要干的事,**FML 10 的处理器还没有这套计划**——
   下一步就是给它做一份 1.21.9 的 keep-runtime 计划并把计划支持补进处理器。
## 1.21.9 续:计划机制接上了,OptiFine 已经跑到"Setting user: True"

上一节把 OptiFine 的类送进了游戏类加载器;这一节把**成员/层级**这两件事从"我临时加的粗暴规则"
换成仓库自己的计划机制,并把结果推到 `Setting user: True` + 32 行 `[OptiFine]`。

### 先把处理器搬进仓库自己的构建

此前 FML 10 的处理器只有 26.x 分支有源码,rig 用 `javac` 手编;也就是说**被测试的那个 jar 里的类,
不属于任何一次提交**。现在:

* `src/fml10/java/kynarain/cn/optifineoforge/fml10/` —— 两个类从 26.x 取回,放上本分支;
* `src/fml10/resources/META-INF/services/` —— 两个 service 文件(ClassProcessor 与
  IModFileCandidateLocator),与 `src/ml11/resources` 同样的做法;
* `build.gradle` 在 `-Pmountpoint=fml10` 时把这两个 source root 加进 `sourceSets.main`;
* 构建方式(注意 `JAVA_HOME` 必须是 JDK 21,否则 Gradle 自己的 Groovy 先死在
  `Unsupported class file major version 71`):

  ```
  gradlew.bat -Pmc=1.21.9 -Pneoforge=21.9.16-beta -Pmountpoint=fml10 compileJava
  ```

rig 侧新增 `build-fml10-payload.ps1`:从 `build\classes\java\main` 取这两个类,从
`work\<line>\optifine-patched.jar` 取 1233 个 `srg/**` 成品类,加 service 与 `neoforge.mods.toml`,
再把 `work\<line>\plan\reparent.txt` 放到 `optifineoforge/reparent.txt`。

**顺手踩到一个必须记下来的坑**:`ZipFile.CreateFromDirectory` 在 PowerShell 5.1(.NET Framework)上
写的条目名用**反斜杠**。这样的罐子 FML 直接拒绝:

```
WARN [ne.ne.fm.lo.mo.ModDiscoverer/SCAN]: Skipping jar. File mods/optifine-payload-fml10.jar is not a valid mod file
```

症状极具误导性:游戏**正常启动**、`Setting user: True`、0 崩溃报告、stderr 0 字节 —— 唯一的异常信号是
`[OptiFine] lines: 0`。脚本改成手工写条目、一律用 `/`。

### 两条规则:留运行时独有的成员,按计划换父类

**第一条(成员)**:现代 NeoForge 是**把补丁打进游戏类**的,所以运行时的类可以有 OptiFine 那份编译
里根本没有的成员。实测到的那一处:

```
NoSuchMethodError: 'java.util.List net.minecraft.server.packs.resources.ReloadableResourceManager.getListeners()'
	at net.neoforged.neoforge.client.event.AddClientReloadListenersEvent.<init>(...:29)
	at net.neoforged.neoforge.client.ClientHooks.initClientHooks(...:973)
```

处理器现在**保留运行时独有、负载没有的字段与方法**(按 name+desc 判重),并把数量打进日志。

**第二条(层级)**:`BlockEntity` 两侧的父类不一样 —— 负载那份 extends
`net.minecraftforge.common.capabilities.CapabilityProvider$BlockEntities`(OptiFine 是 Forge 时代编的,
那个类是 shim),运行时那份 extends `net.neoforged.neoforge.attachment.AttachmentHolder`。两边各试一次,
**两次都被验证器打回**,而且错法不同:

* 只搬成员、不动父类:`VerifyError: Bad invokespecial instruction: current class isn't assignable to
  reference class` at `BlockEntity.setData @7` —— NeoForge 的 `setData` 体内是
  `invokevirtual setChanged()V` 然后 `invokespecial AttachmentHolder.setData(...)`;
* 只把父类换成运行时的:`VerifyError: Bad <init> method call ... Type
  'net/minecraftforge/common/capabilities/CapabilityProvider$BlockEntities' is not assignable to
  'net/minecraft/world/level/block/entity/BlockEntity'` —— 负载自己的构造器还在 chain 到 Forge 父类的构造器。

两半必须一起动,而这正是 ModLauncher 线早就在用的 `reparent.txt` 计划(格式
`reparent <类> <运行时父类> <构造器>`)。处理器现在**读计划**,没有计划就不动层级;
按计划的 `()V` 重写时把栈上的实参 POP 掉(与 `PatchedClassTransformer.reparent` 同一套做法)。

计划由仓库自己的 `HierarchyPlan` 生成。**它在这里有个调用上的陷阱**:`readRuntime` 是"先到先得",
所以第一个 jar 必须是**真正的运行时视图**(这里 NeoForge 的 `-client.jar`,游戏类被它覆盖),
否则 vanilla 的 `BlockEntity`(父类是 Object)会把它盖掉,于是计划**静默地空**:

```
# 错误(0/0,静默): runtime 传 client-…-srg.jar
# 正确:            runtime 传 neoforge-21.9.16-beta-client.jar
reparent net.minecraft.world.level.block.entity.BlockEntity onto net/neoforged/neoforge/attachment/AttachmentHolder via ()V (the payload extends net/minecraftforge/common/capabilities/CapabilityProvider$BlockEntities)
reparent plan: 1 class(es) movable, 0 refused
```

1 个类,和 1.21.4 那条线上记的"shape 就是这一个类"完全一致。

### 这一轮的结果

```
===== VERDICT: FAILED =====
  Setting user        : True
  new crash reports   : 1
  stderr bytes        : 698
  [OptiFine] lines    : 32
```

OptiFine 的类在里面、`Reflector` 初始化成功、游戏把用户设置读出来了。剩下两处,都已定位到行:

1. **`Gui.layerManager` 是 null**:
   `NullPointerException: Cannot invoke "…GuiLayerManager.initModdedLayers()" because "this.layerManager"
   is null` at `Gui.initModdedOverlays(Gui.java:1604)` ← `ClientHooks.initClientHooks`。
   这个字段是 NeoForge 加的,它**就在仓库自己为 1.21.9 生成的 `member-restores.txt` 里**:
   `F net/minecraft/client/gui/Gui layerManager Lnet/neoforged/neoforge/client/gui/GuiLayerManager;`,
   而且 rig 里已经有配套的 `donors/`。也就是说**"保留成员"只解决了一半**:字段留下来了(所以不是
   `NoSuchFieldError`),但给字段赋值的初始化在 NeoForge 的 `Gui.<init>` 里,而那个构造器被 OptiFine
   的副本整段替换掉了。下一步就是把 `member-restores.txt` + donors 接进 FML 10 的处理器
   (ModLauncher 线上由 `MemberRestoreTransformer` 内联 donor 代码)。
2. **`Options.loadOfOptions` 数组越界**(stderr,698 字节):
   `ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1` at
   `net.minecraft.client.Options.loadOfOptions(Options.java:3174)`。
   实测过的两件事:① 删掉 `optionsof.txt` 再跑仍然出现;② 运行中游戏会重新写出这个文件(88 行,
   全是 `key:value`,没有一行缺冒号)。所以"读到旧版本写的文件"这个猜测**站不住**,
   还得再看这一行到底在切什么。
## 1.21.9 再续:成员恢复接上了,OptiFine 跑到 167 行,卡在 FML 自己的加载画面上

### 这一轮加进处理器的两件事

**1. 成员恢复(计划 + donor)。** 上一节停在 `Gui.layerManager` 为 null:字段被"保留运行时成员"留下来了,
但给它赋值的初始化在 NeoForge 的构造器里,而那个构造器被 OptiFine 的副本取代了。处理器现在读
`/optifineoforge/member-restores.txt` 与 `/optifineoforge/donors/<类>.class`,把 donor 里有、成品类里没有的
字段/方法补进去,并把 donor 里的合成初始化方法**内联**:静态的进本类 `<clinit>`,实例的进每个
"自己没有赋过这个字段"的构造器。

两个都是量出来的,不是选的:

* **静态必须内联,不能调用**。静态 final 字段只能在**本类的初始化方法**里赋值,调用一个 helper 去写会被拒:
  `IllegalAccessError: Update to static final field ... attempted from a different method than the initializer method`。
* **实例也必须内联**。先按 ml11 那套"每个构造器调一次 helper"做,1.21.9 直接给出:
  ```
  IllegalAccessError: Update to non-static final field com.mojang.blaze3d.opengl.GlDevice.deviceProperties
    attempted from a different method (optifineoforge$init$deviceProperties) than the initializer method <init>
      at com.mojang.blaze3d.opengl.GlDevice.optifineoforge$init$deviceProperties(GlDevice.java)
      at com.mojang.blaze3d.opengl.GlDevice.<init>(GlDevice.java:95)
  ```
  内联进构造器之后,同一个 `putfield` 就合法了,而且**接收者天然对齐**:donor 的初始化方法把对象放在局部 0,
  `<init>` 也是。
* **指令克隆器缺 `VarInsnNode`** —— 这是 `Gui.layerManager` 一度仍然是 null 的直接原因,日志里写得很清楚:
  `cannot inline optifineoforge$init$layerManager: an instruction of kind VarInsnNode has no copy here`。
  只允许局部 0(其余局部在构造器里会与形参撞车),其余一律拒绝而不是猜。

**2. 一处针对性的修复:`ReloadableResourceManager` 的监听器列表被冻结。** 这是 NeoForge 与 OptiFine 的
**先后顺序**冲突,两边单独看都没错:

* NeoForge 在 `Minecraft` 构造器里通过 `AddClientReloadListenersEvent` 收集监听器,排序结果**不可改**——
  `ReloadListenerSort.sort` 的最后一步是 `Collections.unmodifiableList`(从
  `neoforge-21.9.16-beta-universal.jar` 里读出来的),`ReloadableResourceManager.updateListenersFrom` 把它直接
  赋给字段;
* OptiFine 自己的补丁在同一个构造器里、**晚几行**(它注入到 `Window.setDefaultErrorCallback` 的那次调用)
  注册一个监听器,于是撞上冻结的列表:
  ```
  UnsupportedOperationException
    at java.util.Collections$UnmodifiableCollection.add
    at net.minecraft.server.packs.resources.ReloadableResourceManager.registerReloadListener(...:43)
    at net.optifine.util.TextureUtils.registerResourceListener(TextureUtils.java:412)
  ```
  NeoForge 自己之后不再注册,所以这个冻结在纯 NeoForge 里看不出来。

  修在**冻结点**而不是 `registerReloadListener`:在 `updateListenersFrom` 调用 `ReloadListenerSort.sort` 之后插
  `new ArrayList<>(list)`(`NEW/DUP_X1/SWAP/INVOKESPECIAL`),让字段恢复 vanilla 构造器给的契约(一个可以
  继续加的 List),NeoForge 算出来的顺序一点不动。字段在这里可赋值,是因为处理器本来就会把
  运行时非 final 的字段的 `final` 清掉——这个字段正是如此(负载编成 `final`,NeoForge 的没有)。

### 结果:167 行 `[OptiFine]`,然后倒在 FML 自己的加载画面上

```
===== VERDICT: FAILED =====
  Setting user        : True
  new crash reports   : 1 -> crash-…22.07.08-client.txt
  stderr bytes        : 0
  [OptiFine] lines    : 167
```

OptiFine 这次是真的在工作(`[OptiFine] Scaled non power of 2: minecraft:leaf_3, 5 -> 10` 这类贴图处理已经跑起来了),
游戏也进了主循环,但停在 **NeoForge 的加载覆盖层**上:

```
java.lang.IllegalStateException: Already building.
	at net.neoforged.fml.earlydisplay.render.SimpleBufferBuilder.begin(SimpleBufferBuilder.java:185)
	at …RenderContext.renderText(RenderContext.java:91)
	at …PerformanceElement.render(PerformanceElement.java:75)
	at …LoadingScreenRenderer.renderToFramebuffer(LoadingScreenRenderer.java:280)
	at TRANSFORMER/neoforge@21.9.16-beta/…NeoForgeLoadingOverlay.render(NeoForgeLoadingOverlay.java:68)
	at TRANSFORMER/minecraft@1.21.9/net.minecraft.client.renderer.GameRenderer.render(GameRenderer.java:811)
	at TRANSFORMER/minecraft@1.21.9/net.minecraft.client.Minecraft.runTick(Minecraft.java:1330)
```

而且是**先有一大片 GL 错误**才轮到它:同一份日志里 `OpenGL API ERROR: 1167` 之类共 **865 行**,第一次出现在
OptiFine 刚开始处理贴图之后,失败的调用是 `glClear`。

**这不是环境问题,是对照组量出来的**:同样的 rig、同样的 90 秒、**不带任何 mod** 跑一次:

```
===== VERDICT: STARTED =====    Setting user: True, Sound engine started: True, 崩溃 0, stderr 0
日志总行数 76,OpenGL API ERROR 0 行,Already building 0 次
```

也就是说这批 GL 错误是**我们装的类带来的**:OptiFine 在资源重载期间的 GL 操作把状态弄坏了(或者某个
被换掉的 `GlStateManager`/`GlDevice` 那一路还缺东西),FML 的早期显示在坏掉的 GL 状态上画,一次 `draw`
中途抛错就把 `SimpleBufferBuilder.building` 留在 true,下一帧 `begin` 直接抛 "Already building"。

**下一步**:把 1.21.8 那条线最后用过的那几份计划照 `add-line.ps1` 的顺序补齐 —— `PayloadDrift`
(keep-runtime / runtime-interfaces / access)与 `MissingTargets --stub`,再把它们接进 FML 10 的处理器;
1.21.8 上"GL 状态那一路"正是靠这几份计划才过的。
## 1.21.9 第四轮:资源重载跑通了(Sound engine started),卡在 NeoForge 自己的约定标签检查

### 这一轮接进处理器的东西,全部来自仓库自己的离线工具

按 `add-line.ps1` 的顺序给 1.21.9 补齐了计划:

* **`MissingTargets --stub`**:扫 1281 个类、43634 条游戏成员引用,**13 条在运行时不存在**,给 4 个类
  (`GpuTexture`、`BlockModelPart`、`BlockStateModel`、`BlockEntity`)补了 12 个成员,1 条留给加载器。
  产物 `optifine-patched-stubbed.jar` 现在就是 payload 的 `srg/**` 来源(`build-fml10-payload.ps1` 优先用它)。
* **`PayloadDrift`**:`constant drift: 2 class(es)`,两份是
  `net/minecraft/client/renderer/MappableRingBuffer`(`BUFFER_COUNT: payload=5 runtime=3`)与
  `net/minecraft/client/resources/model/ModelDiscovery$ModelWrapper`(`SLOT_COUNT: payload=7 runtime=8`)。
  写出的 `keep-runtime.proposed.txt` 就是"这两类整个用运行时的"。另外 15 条 interface 计划、164 条 access 计划。
* 处理器现在读 `/optifineoforge/keep-runtime.txt`(即 proposed 的正式名);被计划的类**不安装**,日志明说
  "is kept as the runtime's own class",并连"代价"一起写在计划文件里。

### 三处按症状定位、按量到的形状修的

1. **模型精灵集合**:客户端进资源重载后**什么都不做**,只每 5 秒打一行
   `[OptiFine] Waiting for model sprites`(永远)。这是 OptiFine 的图集拼装等它自己的标志位。payload 里那次调用
   在,但**藏在分支后面**(从字节码读出来):
   ```
   106: invokestatic net/optifine/Config.isCustomItems:()Z
   109: ifeq 121
   118: invokestatic net/optifine/CustomItems.collectModelSprites:(Ljava/util/Map;)V
   ```
   修法与 1.21.8 那条线一致:把调用放到 `discoverModelDependencies` 每个"第一个参数是 Map"的重载的**开头**,
   无条件。接收者是局部 0(静态方法,查过而不是照抄——ml11 那份无条件压局部 0,只在静态时才成立)。
2. **FML 早期加载画面与 OptiFine 抢同一个 GL 上下文**:不关早期窗口时,GL 错误刷屏(一次跑 7195 行),
   然后 `SimpleBufferBuilder.begin` 抛 `Already building`,崩溃报告写 "Rendering overlay"。**对照组**证明这与环境无关:
   同样 rig、90 秒、**不带 mod**,76 行日志、0 条 GL 错误、STARTED。rig 现在有 `-NoEarlyWindow`
   (写 FML 自己的 `earlyWindowControl=false`),并且这件事被当作 **rig 设置**记下来,因为其它线的跑法里它是开着的。
3. **成员恢复的两个"必须内联"和两个"不能抄"**:静态初始化方法内联进本类 `<clinit>`、实例的(带局部 0 接收者)
   内联进每个构造器;`<clinit>` 本身**既不从 donor 抄、也不从运行时保留**——`BreezeWindLayer` 就是反例:
   payload 那份声明 `private ResourceLocation TEXTURE_LOCATION;`(实例,构造器里 putfield),
   运行时那份同名同描述符但是 `static final`,运行时的 `<clinit>` 用 `GETSTATIC` 读它,
   于是 `IncompatibleClassChangeError: Expected static field ... TEXTURE_LOCATION`,第二次资源重载直接死在
   `EntityRenderers.createEntityRenderers`。

### 结果

```
===== VERDICT: FAILED =====
  Setting user        : True
  Sound engine started: True      <- 资源重载这一次真的完成了
  new crash reports   : 1 -> crash-…22.30.08-fml.txt
  stderr bytes        : 0
  [OptiFine] lines    : 283
```

**下一处已经定位到具体一行**,而且是 NeoForge 自己的代码:

```
Exception message: java.lang.NullPointerException: Cannot invoke "net.minecraft.tags.TagKey.toString()"
    because "tag2" is null
	at net.neoforged.neoforge.common.TagConventionLogWarning.createForgeMapEntry(TagConventionLogWarning.java:556)
	at net.neoforged.neoforge.common.TagConventionLogWarning.<clinit>(TagConventionLogWarning.java:201)
	at net.neoforged.neoforge.common.NeoForgeMod.<init>(NeoForgeMod.java:585)
```

`createForgeMapEntry(ResourceKey, String, TagKey)` 的第三个参数是**调用方传进来的**,即
`TagConventionLogWarning.<clinit>` 里某个标签静态字段**是 null**。这是上一条"`<clinit>` 不能抄"的**另一半**:
为了修 `BreezeWindLayer` 我把运行时 `<clinit>` 整个丢掉了,而被"保留运行时成员"补进来的**静态字段**正是靠它赋值的。
**下一步的规则**应当是把丢掉改成**有条件保留**:扫运行时 `<clinit>` 里每条 `owner == 本类` 的
`GETSTATIC`/`PUTSTATIC`,要求成品类里那个字段同名同描述符**且也是 static**;全部满足就保留这个初始化方法,
有一条不满足就丢掉(并记日志)。这样 `BreezeWindLayer` 那类仍然被挡住,而标签静态字段能拿到值。
**这一处已经查到具体字段了**(下一轮直接从这里进):把 `TagConventionLogWarning.<clinit>` 的
`LineNumberTable` 读出来,`line 201` 对应字节码偏移 `2942`,该处正是:

```
2930: sipush 149
2933: getstatic Registries.ITEM
2936: ldc_w   "dyes/black"
2939: getstatic net/neoforged/neoforge/common/Tags$Items.DYES_BLACK : Lnet/minecraft/tags/TagKey;
2942: invokestatic createForgeMapEntry(ResourceKey;String;TagKey)
```

也就是说**为 null 的是 `net.neoforged.neoforge.common.Tags$Items.DYES_BLACK`——NeoForge 自己的静态标签字段**,
不是我们装的任何类。已核对的:两个 mod 罐子里**都没有 `net/neoforged/**` 条目**(所以不是我们把 NeoForge 的类
遮蔽掉了)。因此下一轮要查的是:`Tags$Items` 的 `<clinit>` 为什么没有给这个字段赋值——最可能的方向是它取的
值来自游戏类里被我们换掉的静态方法(日志里 OptiFine 自己也报过
`[OptiFine] (Reflector) Method not present: net.minecraft.tags.ItemTags.create`),而 `<clinit>` 里的赋值
被异常/分支跳过了。

另外把这一轮"条件保留 `<clinit>`"的实测结果记清楚:**它没有解决这一处**(改完再跑,仍然是同一个 NPE),
所以"保留运行时 `<clinit>`"既不是充分条件也不是充分修法;这一处的根因在上面那条链上,不在 `<clinit>` 的取舍。
## 1.21.9 通过验收:四检查项全中,并且跑了两次

### 最后一处根因:OptiFine 用反射找一个 1.21.9 已经没有的方法

上一节停在 `Tags$Items.DYES_BLACK` 为 null。整条链这一轮全部读出来了:

NeoForge 的 `Tags$Items.<clinit>` 是
```
657: getstatic   net/minecraft/world/item/DyeColor.BLACK
660: invokevirtual DyeColor.getTag:()Lnet/minecraft/tags/TagKey;
663: putstatic   DYES_BLACK
```
而 `getTag()` 返回的是 `DyeColor` 构造器里赋的那个字段,payload 里那次赋值长这样:
```
47: getstatic     net/optifine/reflect/Reflector.ForgeItemTags_create
57: ldc           "forge"                                  <- Forge 时代的命名空间
70: invokevirtual net/optifine/reflect/ReflectorMethod.call([Ljava/lang/Object;)Ljava/lang/Object;
73: checkcast     net/minecraft/tags/TagKey
76: putfield      dyesTag:Lnet/minecraft/tags/TagKey;
```
OptiFine 的 `Reflector.<clinit>` 里那个句柄是
`ForgeItemTags_create = ForgeItemTags.makeMethod("create", String.class, String.class)`,
指向 `net.minecraft.tags.ItemTags.create(String, String)` —— **而 1.21.9 的运行时只有 `create(ResourceLocation)`**,
于是 `ReflectorMethod.call` 返回 null(日志里那句 `[OptiFine] (Reflector) Method not present:
net.minecraft.tags.ItemTags.create` 就是它),`dyesTag` 为 null,`DYES_BLACK` 为 null,`TagConventionLogWarning`
在自己的 `<clinit>` 里炸掉,NeoForge 报"has failed to load correctly"。

**注意这一处 `MissingTargets --stub` 结构上看不到**:那不是一条字节码里对游戏成员的引用,而是反射器字段里的一个
**字符串**。所以修法是处理器**生成**这个方法:往 `ItemTags` 上加
`public static TagKey<Item> create(String namespace, String path)`,内部把 Forge 时代的 `forge` 映射成
NeoForge 的 `c`(与 ModLauncher 线上 `ConventionTags` 同一套规则),再委托给运行时的 `create(ResourceLocation)`。
生成的字节码是 `LDC "forge"; ALOAD 0; String.equals; IFEQ; LDC "c"; GOTO; ALOAD 0; ...`。

`ItemTags` **不在 payload 里**(payload 没有这个类的副本),所以处理器新增了一个"只修不换"的目标集合
`REPAIR_ONLY_TARGETS`:`targets()` 里声明它,`transform()` 在没有 payload 字节时只跑修复。

### 结果:验收四项全中,且可重复

同一条命令跑两次,两次逐项相同:

```
===== VERDICT: STARTED =====
  Setting user        : True
  Sound engine started: True
  new crash reports   : 0
  stderr bytes        : 0
  [OptiFine] lines    : 365
```

日志侧面证据:`OpenGL API ERROR` **0** 行、`NeoForge mod loading, version 21.9.16-beta` 成功、
`[OptiFine] OptiFine_1.21.9_HD_U_J7_pre2` 完整自述、且**标题界面确实在跑**——
`RealmsNotificationsScreen.<init>` 会调 `RealmsAvailability.get()`,而那个界面只由 `TitleScreen` 构造
(它自己用 `inTitleScreen()` 判断当前界面是 `TitleScreen`),日志里正好有这条:
`[IO-Worker-1/ERROR] [com.mojang.realmsclient.RealmsAvailability/]: Couldn't connect to realms`
(离线机器的正常结果,不是错误)。

### 必须一起记下来的口径

* **`-NoEarlyWindow` 是 rig 设置**:FML 的早期加载画面与 OptiFine 的贴图工作抢同一个 GL 上下文,
  开着它客户端会死在 `SimpleBufferBuilder "Already building"`。关掉它是 FML 自己的开关
  (`earlyWindowControl=false`),而**其它线的跑法里它是开着的**——所以这条线的成绩要按这个前提读。
* 启动入口是 rig 的诊断入口点 `DiagnosticClient`(同一条 `Entrypoint.startup` 管道,只是把异常打到
  stderr)。用它的原因写在 `launch-fml10.ps1` 与 `DiagnosticClient` 的注释里:官方入口点
  `net.neoforged.fml.startup.Client` 把异常交给一个**模态对话框**,什么也不打印。
* 这条线的 payload 不随仓库发布(`srg/**` 是 OptiFine 打过补丁的游戏类),rig 侧由
  `build-fml10-payload.ps1` 用仓库自己的离线工具产出。
## 1.21.10 / 1.21.11 的准备:一条 FML 10 线的完整配方(rig 侧已脚本化)

1.21.9 通过之后,剩下两条 FML 10 线(1.21.10 / 1.21.11)用的是**同一个挂载点、同一套计划**,
所以把它们做成一条命令就值得。rig 侧新增两个脚本,步骤与输入来源都写在脚本头部:

* **`prepare-fml10-line.ps1`**(`-Mc` / `-NeoForge` / `-OptifineJar`):
  1. **runtime 视图** = NeoForge 的 `-client.jar`(覆盖)+ NeoForm 的 `client-*-srg.jar`;
     注意 `HierarchyPlan` 必须**先**拿 overlay,否则 vanilla 的 `BlockEntity` 会把 NeoForge 的盖掉、
     计划静默为空(1.21.9 上踩过,0/0)。
  2. `OptifinePipeline <obf 原版客户端> <OptiFine jar> <work>` —— 仓库自己的离线补丁流程。
  3. `MemberRestorePlan`(成员恢复计划 + donors)。
  4. `HierarchyPlan`(要换父类的类 + 要 chain 的构造器)。
  5. `MissingTargets --stub`(**必须给全运行时 classpath**:游戏 + universal + 每个库 jar;用 argfile 传参,
     因为这几条线的库 jar 有几百个,命令行会被 Windows 拒掉)。
  6. `PayloadDrift`(keep-runtime / interfaces / access 三份计划)。
  7. Gradle `-Pmountpoint=fml10` 编译挂载点,再产出两个 jar。
* **`build-fml10-own-classes.ps1`**:第二个 mod jar(OptiFine 自己的类 + Forge API 桩 + 它自己的
  `neoforge.mods.toml`)。桩类目录为空时会**当场用 `ForgeApiShims` 生成**,两者都必须从 OptiFine jar
  **和**打补丁后的类里取(只用 OptiFine jar 会漏成员)。
  两个 jar 都必须手工按 `/` 分隔写 zip 条目:`ZipFile.CreateFromDirectory` 在 PowerShell 5.1 上写反斜杠,
  FML 会直接判"not a valid mod file",而症状是**客户端正常启动但 mod 根本没装**(1.21.9 上踩过)。

1.21.9 的 OptiFine 构建与 1.21.10 的都从第三方镜像按 IPv4 取到;1.21.11 的**正式版**在镜像上路径不对
(返回 9 字节 "Not Found"),改走 `get-optifine.ps1` 的 optifine.net 两步 token 流程取到了
(`OptiFine_1.21.11_HD_U_J9.jar`,8 045 116 字节)。两条线的 jar 都只放在 rig 的 `downloads\` 下,不进仓库。
### 1.21.10:安装缺件卡在网络,已定位到具体两件

为了把 1.21.10 也跑起来,这一轮做了这些(都留在 rig 里,不进仓库):

* **OptiFine jar 取到了两条线**:1.21.10 的 `preview_OptiFine_1.21.10_HD_U_J7_pre11.jar`(7 805 444 字节,
  第三方镜像按 IPv4);1.21.11 的**正式版** `OptiFine_1.21.11_HD_U_J9.jar`(8 045 116 字节)——
  镜像上那条路径不对(返回 9 字节 "Not Found"),改走 `get-optifine.ps1` 的 optifine.net 两步 token 流程取到。
* **NeoForge 21.10.64 装上了**(universal jar、1577 个库、126 个 native),`versions\1.21.10\1.21.10.jar`
  (30 592 168 字节,混淆原版客户端)也在。
* **两个 rig 脚本写好并语法自检通过**:`prepare-fml10-line.ps1`(runtime 视图 → OptifinePipeline →
  MemberRestorePlan → HierarchyPlan → MissingTargets --stub → PayloadDrift → 两个 jar)与
  `build-fml10-own-classes.ps1`(第二个 mod jar,含 Forge API 桩的按需生成)。
* **卡点**:`libraries\net\minecraft\client\1.21.10-…` 下的 slim/extra/srg 三个 NeoForm 客户端变体,
  以及 `libraries\net\neoforged\neoforge\21.10.64\neoforge-21.10.64-client.jar`(NeoForge 打补丁后的客户端覆盖层)
  **都不存在**;安装每次都报 `libraries: fetched 1, already present 1577, failed 1`。
  直接取这两件时 **`maven.neoforged.net:443` 连不上**(curl 72 秒超时),这是环境/网络问题,不是配置问题。
  已经把 Gradle 模块缓存里的 `neoform-1.21.10-20251010.172816.zip`(889 425 字节)**补种**到 rig 的
  `libraries\net\neoforged\neoform\1.21.10-20251010.172816\`,并核对了 `neoforge-21.10.64-userdev.jar`:
  里面是 `patches/**`、`ats/accesstransformer.cfg`、`config.json`,**不含编译后的客户端类**,所以覆盖层必须由
  NeoForm 流程(neoform zip + 这些补丁)产出,不是能直接下载的成品。

**下一步**:网络恢复后重跑 `add-line.ps1 -InstallOnly`(neoform zip 已就位,可能就能补齐三个客户端变体),
再跑 `prepare-fml10-line.ps1 -Mc 1.21.10 -NeoForge 21.10.64 -OptifineJar <jar>`,最后按脚本末尾打印的命令启动。
这个脚本里的每一步都是 1.21.9 上量过的同一条链,所以剩下的风险集中在"安装能不能补齐"这一件上。
## 1.21.10 通过验收:同一套计划,零改动

```
===== VERDICT: STARTED =====
  Setting user        : True
  Sound engine started: True
  new crash reports   : 0
  stderr bytes        : 0
  [OptiFine] lines    : 356
```

两次跑法逐项一致(`[OptiFine] OptiFine_1.21.10_HD_U_J7_pre11`、`OpenGL API ERROR` 0 行、标题界面标志
`RealmsAvailability` 在跑、无崩溃报告、stderr 0 字节)。**这条线没有为它改一行处理器代码**:
成员恢复、reparent 计划、keep-runtime、stub 计划、精灵集合修复、反射式 tag creator 全部原样生效,
这正是把这些东西做成"计划驱动"的价值。

### 这一轮为 1.21.10 解决的三件事

1. **安装缺件换了一条路**。maven.neoforged.net 从这台机器上连不上,安装器始终报
   `libraries: fetched 1, already present 1577, failed 1`,`libraries\net\minecraft\client\1.21.10-…` 的
   slim/extra/srg 与 `neoforge-21.10.64-client.jar` 都拿不到。**ModDevGradle 自己的 NeoForm 运行时早就把
   等价物算出来了**:
   `~/.gradle/caches/neoformruntime/intermediate_results/compiledWithNeoForge_<hash>_output.jar`
   —— 一个 jar 里同时有打补丁后的游戏类(`net/minecraft/**`)和 NeoForge 自己的类
   (`net/neoforged/neoforge/**`),13323 个条目,是 "overlay + srg client" 的超集。
   `prepare-fml10-line.ps1` 因此加了 `-RuntimeJar`:给它就跳过安装器那两个成品。同样地,
   neoform zip 也从 Gradle 模块缓存补种进 `libraries\net\neoforged\neoform\1.21.10-20251010.172816\`。
   **这是拿缓存里的等价产物替代缺失下载,记下来是因为它不是"官方安装"那条路。**
2. **诊断入口点要跨 FML 版本**。FML 10.0.32(NeoForge 21.10.64)把 API 换了:
   `startup(...)` 返回 `Entrypoint$StartupResult`(不再是 `FMLLoader`)、
   `createMainMethodCallable(StartupResult, String)`、关闭走 `StartupResult.close()`。
   按 10.0.14 编译的诊断类第一行就死:
   `NoSuchMethodError: 'FMLLoader … .startup(String[], boolean, Dist, boolean)'`。
   新的 `DiagnosticClientAny` **不引用任何 FML API**,全部用反射找方法,所以两条线共用一个类。
3. **`-NoEarlyWindow` 同样适用**:开着早期加载画面时,1.21.10 死在与 1.21.9 一模一样的
   `SimpleBufferBuilder "Already building"`(FML 早期画面与 OptiFine 的贴图工作抢 GL 状态)。
   另外 `-NoEarlyWindow` 现在要求 `config\fml.toml` 已存在——第一次启动会写它,所以新线要先不带这个开关跑一次。

### 1.21.11 的状态:缺一个装不了件

OptiFine 正式版 jar 已在 rig 里(`OptiFine_1.21.11_HD_U_J9.jar`,8 045 116 字节),Gradle 模块缓存里也有
`neoforge-21.11.45-universal.jar` / `-userdev.jar`,但**没有 `neoforge-21.11.45-installer.jar`**,
而 `versions\neoforge-21.11.45\neoforge-21.11.45.json` 这个 profile(库清单与 JVM/游戏参数)只有安装器能生成;
`maven.neoforged.net:443` 这一轮仍然连不上(25 秒超时)。所以 1.21.11 卡在**环境**上,不是代码上:
网络恢复后跑 `add-line.ps1 -InstallOnly -Mc 1.21.11 -NeoForge 21.11.45 -MountPoint fml10`,
再用 1.21.10 同样的 `-RuntimeJar` 路径(从 NeoForm 缓存取)跑 `prepare-fml10-line.ps1`,即可按同一套流程验证。
## 1.21.11 通过验收:FML 10 三条线全部跑通

```
===== VERDICT: STARTED =====
  Setting user        : True
  Sound engine started: True
  new crash reports   : 0
  stderr bytes        : 107      <- 与它的无 mod 对照跑逐字节相同(同一行 log4j 环境告警)
  [OptiFine] lines    : 273
```

### 两处只有 1.21.11 才暴露出来的东西

1. **`ResourceLocation` 在这一版叫 `Identifier`**。反射式 tag creator 的委托目标原本写死成
   `create(ResourceLocation)`,于是处理器自己在日志里说了实话:
   `net.minecraft.tags.ItemTags has no create(ResourceLocation) to delegate to; OptiFine's reflective tag
   lookup stays unresolved and every DyeColor tag stays null`,客户端随即死在与 1.21.9 一模一样的
   `TagConventionLogWarning` NPE 上。改成**动态找委托**:在 `ItemTags` 里找那个"一个参数、返回 `TagKey`
   的静态 `create`",取它的参数类型做 `fromNamespaceAndPath`,所以 1.21.9 找到 `ResourceLocation`、
   1.21.11 找到 `Identifier`,同一个修复覆盖两条线。
2. **安装器能跑,但缺一个坐标**:`maven.neoforged.net` 的 **IPv4 路由是坏的**(curl 25 秒超时),
   **IPv6 通**(`curl -6` 拿到 200)。用 -6 取到 21.11.45 的安装器与 neoform zip 之后,
   安装器日志最后是 `Successfully installed client into launcher`,并且留下了
   `libraries\net\neoforged\minecraft-client-patched\21.11.45\minecraft-client-patched-21.11.45.jar`
   —— **FML 10 的游戏 jar 就是它**。它只有游戏类(29191 个条目,没有 `net/neoforged/**`),所以运行时视图里
   NeoForge 自己的类从 `-universal.jar` 补进来(`net/neoforged/neoforge/attachment/AttachmentHolder` 就在那里),
   这份合并结果作为 `-RuntimeJar` 交给 `prepare-fml10-line.ps1`。
   顺带说明:我之前按"缺省安装器产物"报的那两条(`client-*-srg.jar`、`neoforge-<ver>-client.jar`)
   对 FML 10 线**不是必须的**——真正被加载的是 `minecraft-client-patched-<ver>.jar`。

### FML 10 三条线的最终成绩(当前处理器修订,同一套计划)

| 线 | NeoForge | 判据 | `[OptiFine]` 行 | stderr | 备注 |
|---|---|---|---|---|---|
| 1.21.9 | `21.9.16-beta` | 四项全中 | 365 | 0 字节 | 两次一致 |
| 1.21.10 | `21.10.64` | 四项全中 | 356 | 0 字节 | 未为该线改一行处理器代码 |
| 1.21.11 | `21.11.45` | 四项全中 | 273 | 107 字节 | 与无 mod 对照跑**逐字节相同** |

三条线共同的 rig 前提:**关掉 FML 的早期加载画面**(`earlyWindowControl=false`)——开着它三条线都会死在
FML 自己的 `SimpleBufferBuilder "Already building"`(与 OptiFine 的贴图工作抢同一个 GL 上下文);
启动入口用 `DiagnosticClientAny`(不引用任何 FML API、全反射,因此同时适配 loader 10.0.14 与 10.0.32,
后者的 `startup` 返回 `Entrypoint$StartupResult` 而不是 `FMLLoader`)。
## 26.1.2 在重建后的 rig 上的进度(这一节关于 `26.x` 线,记在这里是因为工作发生在本轮的 rig 上)

GitHub 上 `26.x` 的 README 早就写着 26.1.2"实机验证"(`[OptiFine]` 3478 行、`processClass` 795 次)。
本轮在**重建后的 rig** 上第一次真的去复现它,得到的是一个诚实的中间状态:

**已经量到的**

* rig 装上了 Minecraft 26.1.2 与 NeoForge `26.1.2.109`(FML **11.0.15**、Java **25**),
  原版客户端 `versions\26.1.2\26.1.2.jar` 38,113,927 字节;
  启动入口仍是 `net.neoforged.fml.startup.Client`,所以 `launch-fml10.ps1` 用得起来——
  为它加了 `-JavaExe`(这一线要 JDK 25,1.21.x 要 21)。
* **基线**(mods 里只有修过元数据的 OptiFine):`VERDICT: STARTED` + `Setting user` + `Sound engine started`
  + 无崩溃报告 + stderr 107 字节。也就是说这一版的**实例本身没问题**。
* OptiFine 的 `K1_pre2` 自带 `OptiFineClassProcessor`(同时是 `IModFileCandidateLocator`),
  但它的 `META-INF/mods.toml` 是 Forge 时代的,必须换成 `neoforge.mods.toml`;
  **`optifine/Patcher`、`optifine/xdelta/**`、`optifine/json/**` 绝对不能删**——
  第一次删了它们,`OptiFineBaseTransformer.<init>` 立刻
  `NoClassDefFoundError: optifine/Patcher`,处理器整个实例化不出来
  (`ServiceConfigurationError: Provider optifine.OptiFineClassProcessor could not be instantiated`)。
* 处理器要的"基底类"在 NeoForge 生产运行时里拿不到:不把成品类放进它的 jar 时,它对每个目标都报
  `java.io.IOException: Base resource not found: net/minecraft/…`(一次跑 866,068 字节 stderr),
  于是 OptiFine 全程不生效(0 行 `[OptiFine]`)。把仓库离线流水线产出的 `srg/**` 成品类
  (566 个,其中 net/minecraft 486)并进 OptiFine 的 jar 之后,处理器才开始真正安装它们。

**一条与 1.21.9 那轮同源的、新的加载器陷阱**

`optifine-own-classes.jar`(OptiFine 自己的类 + Forge shim)一开始被 FML **当成早期服务罐**:

```
Found 4 early service jars (out of 103)
Loading FML Early Services:
 - …/loader-11.0.15.jar
 - …/earlydisplay-11.0.15.jar
 - mods/optifine-26.1.2-neoforge.jar
 - mods/optifine-own-classes.jar          <- 它不该在这里
```

原因是流水线拆出的 classpath jar **原样保留了 `META-INF/services/**`**,而 OptiFine 的
`net.neoforged.neoforgespi.transformation.ClassProcessor` / `...locating.IModFileCandidateLocator`
两个服务文件就在里面。早期服务的类由一个**看不见游戏类**的加载器加载,于是
`net.optifine.reflect.Reflector.<clinit>` 抛
`NoClassDefFoundError: net.minecraft.world.level.chunk.ChunkAccess`,
刚被打过补丁的 `net.minecraft.util.Mth.<clinit>` 又抛 `net/optifine/util/MathUtils` 找不到。
从第二个罐子里去掉这两个服务文件之后,`[OptiFine]` 开始打印(5 行),stderr 回到 107 字节。

**还差什么(下一轮从这里进)**

OptiFine 的处理器开始安装类之后,客户端死在**换父类**这一处,错误与 FML 10 线上一模一样:

```
VerifyError: Bad type on operand stack
  Location: net/neoforged/neoforge/attachment/AttachmentSync.onChunkSent(...)V @82: invokestatic
  Reason: Type 'net.minecraft.world.level.block.entity.BlockEntity' ... is not assignable to
          'net/neoforged/neoforge/attachment/AttachmentHolder'
```

1.21.9 上这是由处理器**在加载期**读 `reparent.txt` 修的;26.1.2 的挂载点是 OptiFine 自己的处理器,
它不会做这件事,所以那一套(换父类 + 构造器 chain 重写、成员回填、shim)**必须在离线阶段就写进
并进 OptiFine jar 的 `srg/**` 成品类里**——这正是 `26.x` 文档里写的"本项目贡献的是离线流水线"。
本轮还没有做这一步,因此 26.1.2 **没有通过验收**,只是从"完全不动"推进到了"OptiFine 在跑、卡在换父类"。
### 更正:上一节说 26.1.2 卡在换父类,那个已经做完了

本节前面的"26.1.2 还差什么"写在同一天早一些的时候。之后离线那一步做完了,26.1.2 **四项判据全部通过**:
`VERDICT: STARTED` + `Setting user` + `Sound engine started` + 无崩溃报告 + stderr 107 字节(= 对照跑那一行),
`processClass` **795 次**(与 `26.x` 更早的记录一致),`[OptiFine]` 行数这一次是 352(与那份记录的 3478 不同,
**没有解释**,如实写在 `26.x` 的 `docs/DEVELOPMENT.md` 和 README 里)。

离线那一套的做法:仓库自己的 `OptifinePipeline` → `HierarchyPlan` → `ReparentPayload` →
`MemberRestorePlan` → `RestoreMembers`,再把成品 **`srg/**` 与 `assets/**` 一起**并进 OptiFine 的 jar
(只并类不并资源会刷 13,367 字节的 `Base resource not found: assets/...`),外加第二个 mod 罐子
`optifine-own-classes.jar`(OptiFine 自己的类 + shim,且必须剔除 `META-INF/services/net.neoforged.**`,
否则它会被当成早期服务罐、其类由一个看不见游戏类的加载器加载)。完整配方在 `26.x` 的
`docs/DEVELOPMENT.md`。

至此 **15 条线全部在本机重建后的 rig 上跑过四项判据**(1.20.1 / 1.20.2 / 1.20.4 / 1.20.6、
1.21 / 1.21.1 / 1.21.3 / 1.21.4 / 1.21.6 / 1.21.7 / 1.21.8、1.21.9 / 1.21.10 / 1.21.11、26.1.2),
逐条的数字在各自的 README 与分支文档里,三条 FML 10 线与 26.1.2 的 rig 前提(关掉 FML 早期加载画面、
诊断入口点、以及"安装器拿不到时用 NeoForm 缓存产物替代"这件事)也都写在同一处。
## 光影包实测(FML 10 三条线):1.21.10 通过,1.21.9 有未解释的差异

用户要求下载安装多个光影包测试。六个包(Complementary Reimagined r5.9.3、BSL v10.1.5、Photon v1.3b、
MakeUp UltraFast 9.5e、Rethinking Voxels r0.1-beta9、Solas V3.7b)从 Modrinth API 取到 rig 的
`shaderpacks\`,由 rig 侧脚本 `test-shaderpack.ps1` 逐包"装包 → 写 `optionsshaders.txt` → 启动 → 从日志取数"。

**怎么选中一个包**:`<游戏目录>\optionsshaders.txt`,键 `shaderPack`,值是 `shaderpacks\` 里的条目名(含 `.zip`)。
这是从 OptiFine 自己的类里读出来的(`Shaders` 里 `optionsshaders.txt` + `new File(Minecraft.getInstance()
.gameDirectory, ...)`,键名来自 `EnumShaderOption.SHADER_PACK.getPropertyKey()`),不是猜的。

### 结果

| 线 | 光影包 | 四项判据 | 包是否加载 | `[OptiFine]` 行 | `[Shaders]` 行 | GLSL 错误 |
|---|---|---|---|---|---|---|
| **1.21.10** | MakeUp-UltraFast 9.5e | 全中 | **是** | 3441 | 76 | 0 |
| **1.21.10** | Photon v1.3b | 全中 | **是** | 1199 | 180 | 0 |
| 1.21.9 | MakeUp-UltraFast 9.5e | 全中 | **否** | 365 | 9 | 0 |

(1.21.10 上其余四个包的结果补在本节末尾;26.1.2 上六个包全部通过,记录在 `26.x` 的
`docs/DEVELOPMENT.md`。)

### 1.21.9 的差异:如实记为"未解释"

同一条命令、同一份 `optionsshaders.txt`(内容逐字节相同:`shaderPack=<包>` + `antialiasingLevel=0`)、
同一台机器:

* 1.21.10 的日志:`[Shaders] Load shaders configuration.` → 2 毫秒后
  `[Shaders] Loaded shaderpack: photon_v1.3b.zip`;
* 1.21.9 的日志:`[Shaders] Load shaders configuration.` 之后**什么都没有**,直接进资源重载。

已经排除的(都量过):

* **不是文件格式/位置**:该文件存在、是普通文件(`Archive`,59 字节)、内容正确;
  用 OptiFine 自己的 `net.optifine.util.PropertiesOrdered` 离线解析同一个文件,拿到
  `shaderPack=[(debug)]`(换值也拿得到),所以 `key=value` 与 `:value` 两种写法都能被解析。
* **不是路径构造不同**:`javap` 逐字节比较 1.21.9(`J7_pre2`)与 1.21.10(`J7_pre11`)两个构建的
  `Shaders` 初始化代码,`new File(Minecraft.getInstance().gameDirectory, "optionsshaders.txt")` 与
  `shaderpacks` 两处**完全一致**。
* **不是我们装的 `Minecraft`**:1.21.9 的载荷里**没有** `net/minecraft/client/Minecraft.class`
  (运行时那份带 `public final File gameDirectory`),所以读的是运行时的字段。
* **不是 antialiasing / Fabulous 拦截**:那两个分支各自会打一行 `[Shaders]` 说明,日志里都没有;
  而且 `antialiasingLevel=0` 与 `=2` 两种取值都试过,行为不变。
* **不是把文件放错目录**:把同样的内容种到 6 个候选位置(游戏目录、游戏目录下的 `run\`、`game\`、
  rig 根、`.minecraft`、用户目录)再跑,`[Shaders]` 里依然没有任何一行提到它被读到。
* **`Config` 的默认值**:三个 `[Shaders]` 相关取值都表现为"读到空串",即 `loadConfig()` 在
  `Properties.load()` 之前先 `setProperty("shaderPack","")` 的那个值——也就是说文件读取这一步
  在 1.21.9 上要么抛了被吞掉的异常、要么读的不是那个文件。**具体是哪一种没有定论。**

下一轮的诊断手段已经想好:用 ASM 给 **1.21.9 那份** `net/optifine/shaders/Shaders.loadConfig()` 开头插一行
探针,打印 `configFile`、`configFile.exists()` 与读完后 `shadersConfig.getProperty("shaderPack","(unset)")`,
再把探针 jar 放进 `mods\` 跑一次。这样"路径/存在性/读到的值"三件事一次量清。
### 1.21.10 上六个包的全部结果(补上表)

| 光影包 | 四项判据 | 包是否加载 | `[OptiFine]` 行 | `[Shaders]` 行 | GLSL 错误 | GL 错误 | stderr |
|---|---|---|---|---|---|---|---|
| MakeUp-UltraFast 9.5e | 全中 | 是 | 3441 | 76 | 0 | 0 | 0 |
| Complementary Reimagined r5.9.3 | 全中 | 是 | 592 | 104 | 0 | 0 | 0 |
| BSL v10.1.5 | 全中 | 是 | 393 | 68 | 0 | 0 | 0 |
| Photon v1.3b | 全中 | 是 | 1199 | 180 | 0 | 0 | 0 |
| Rethinking Voxels r0.1-beta9 | 全中 | 是 | 570 | 94 | 0 | 0 | 0 |
| Solas V3.7b | 全中 | 是 | 3390 | 80 | 0 | 0 | 0 |

每个包都是**一次真机启动**:`VERDICT: STARTED` + `Setting user` + `Sound engine started` + 无崩溃报告,
并且日志里有 OptiFine 自己的 `[Shaders] Loaded shaderpack: <包名>`。"GLSL 错误"统计的是
`Error compiling|Error linking|SMCLog.severe` 四类的匹配数,六次都是 0;`GL 错误`统计
`OpenGL API ERROR`,也都是 0。
## 多人游戏与 FXAA 实测(1.21.10,Java 21)

用户给了服务器地址并限定了多人游戏的验收口径:**只验"能连上 + 能移动"**,其它交互不测。

### 怎么在没有 GUI 的情况下连服务器

1.21.10 里 `--server`/`--port` 已经不存在(`Main` 的选项表里没有),唯一的命令行入口是 quick play。
实测一个有坑的细节:`-GameArgs '--quickPlayMultiplayer','8.148.31.159:25565'` 这种**空格分隔**形式会被
`Main` 原样打印成 `Completely ignored arguments: [--quickPlayMultiplayer,8.148.31.159:25565]`,
连接不会发生;换成**单 token 的 `--quickPlayMultiplayer=8.148.31.159:25565`** 才被解析。
rig 侧为此给 `launch-fml10.ps1` 加了 `-GameArgs` 透传参数。

### 结果:连上了,而且移动可测

```
[Render thread/INFO] [net.minecraft.client.gui.screens.ConnectScreen/]: Connecting to 8.148.31.159, 25565
[Render thread/INFO] [net.minecraft.client.gui.components.ChatComponent/]: [System] [CHAT] Use /register <password> <password> to claim this account.
[Render thread/WARN] [net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl/]: Client disconnected with reason: Authentication time has expired
```

* **连接成功**:`Connecting to 8.148.31.159, 25565` 之后进入世界,服务器每 10 秒推一次
  `Use /register ...`(AuthMe 类插件的未登录提示);
* **约 70 秒后被服务器踢下线**,原因是 `Authentication time has expired`:这是个离线开发客户端
  (用户名 `Dev`、accessToken 0),而我**没有在用户的服务器上注册账号**(那需要 `/register`,属于对用户服务器
  的写操作,不该由我擅自做);
* 这 70 秒窗口内客户端 **0 崩溃报告、stderr 0 字节**;
* **移动**:用 rig 新脚本 `move-check.ps1` 量——两帧对比法,先量"不按键"时段的变化率做基线,
  再量"按住 W"时段的变化率:

| 时段(各 12 秒) | 平均逐像素差 | 变化像素比例 |
|---|---|---|
| 静止(基线) | 7.32 | 9.55% |
| 按住 W | 19.67 | **19.68%** |

比值 **2.69x**,判定 **MOVED**。如实说明两条边界:①这条判据是**相对**的(走路会平移整个画面,静止只动
云/水/粒子),第一版脚本用的是我事先写下的"变化像素 ≥25%"绝对阈值,6 秒那次量到 21.18% 被判成 NOT SHOWN
——阈值改成相对的后重跑才得到上面的数字,这一点写在脚本注释里;②像素法证明的是"画面在大幅变化",
不是直接读到坐标,所以它是**证据**而不是仪表读数。

### 顺手修掉一个真 bug:多人游戏里下雨就崩

第一次带光影/粒子进入服务器世界后,客户端几秒内就写了崩溃报告:

```
java.lang.NoSuchMethodError: 'it.unimi.dsi.fastutil.ints.Int2ObjectMap
    net.minecraft.client.particle.ParticleResources.getProviders()'
  at ParticleEngine.makeParticle(ParticleEngine.java:76)
  at ClientLevel.doAddParticle(ClientLevel.java:930)
  at WeatherEffectRenderer.tickRainParticles(WeatherEffectRenderer.java:228)
```

根因是**同一件事的两个形状不同**:OptiFine 的 `ParticleEngine` 编译时用的是
`ParticleResources.getProviders()` 返回 **int 键的 `Int2ObjectMap`**(并在 `Registry.getId(type)` 上取值),
而 NeoForge 的运行时把它改成了 **`Map<ResourceLocation, ParticleProvider<?>>`**;
OptiFine 本来还有一个后备(通过反射调 Forge 时代的 `ParticleResources.getProvider(ParticleType)`),
但日志自己说了 `(Reflector) Method not present: ...ParticleResources.getProvider`,所以后备也是空的。

修法**不是**把 `ParticleEngine` 换成运行时那份(那一份里有 OptiFine 的自定义粒子颜色
`updateTerrainParticleColor`/`CustomColors`,换掉就丢功能),而是在处理器里**改写这个调用点的形状**:
`getProviders()` 的描述符改成返回 `Map`、`Registry.getId(Object)I` 改成 `Registry.getKey(Object)ResourceLocation`、
`Int2ObjectMap.get(I)` 改成 `Map.get(Object)` —— 取到的还是同一个 provider(注册表 id 与资源位置指向同一条目)。
修完再连服务器:**同样的世界、同样 150 秒,0 崩溃报告**。

**这条修复的覆盖范围**:1.21.9 / 1.21.10 / 1.21.11 三条线都由我们的处理器在加载期改写,所以只要重建 payload
即生效(1.21.11 的 payload 已重建);**26.1.2 的挂载点是 OptiFine 自带的处理器**,加载期不改写,需要在
离线阶段对并进 OptiFine jar 的 `srg/**` 做同样的三处改写(已核对:26.1.2 的 payload 里
`ParticleEngine` 同样引用 `Int2ObjectMap`),这件事还没有做。

### FXAA

OptiFine 的抗锯齿**不在** `optionsshaders.txt`,而在 `optionsof.txt` 的 **`ofAaLevel`**(这一点是量出来的:
把 `optionsshaders.txt` 里的 `antialiasingLevel=2` 写进去,日志里连一行 `Antialiasing` 都没有;
改 `optionsof.txt` 的 `ofAaLevel` 才见效)。

| 设置 | 日志证据 | 四项判据 | 崩溃报告 | stderr |
|---|---|---|---|---|
| `ofAaLevel:2` | `[Shaders] Shaders can not be loaded, Antialiasing is enabled: 2x` | 全中 | 0 | 0 |
| `ofAaLevel:4` | `[Shaders] Shaders can not be loaded, Antialiasing is enabled: 4x` | 全中 | 0 | 0 |

两次都 `VERDICT: STARTED` + `Setting user` + `Sound engine started`,没有崩溃报告,stderr 0 字节。
**如实写的边界**:这两次证明的是"FXAA 2x/4x 被客户端启用且不致命";`Shaders can not be loaded` 那一行是
OptiFine 自己的规则(抗锯齿与光影包互斥),而 FXAA 观感上的边缘平滑没有做像素级对比(那需要更细的
边缘检测,不在本轮范围)。

### rig 侧新增的三个工具

* `launch-fml10.ps1 -GameArgs ...`:把额外游戏参数追加在 profile 自己那批之后(quick play 就是这么传的);
* `slp-ping.ps1`:最小 Server-List-Ping 客户端(握手 + status),用来在游戏之外问服务器"你是谁、在线几人";
* `move-check.ps1`:两帧对比法测移动,把"静止基线"和"按键时段"两个数字一起打印出来。

## 2026-09-20:1.21 的初始资源重载卡死——载荷里的一次自调用被改名到 OptiFine 自己的方法(已修)

**一句话**:1.21(`21.0.167`,ModLauncher 路径,JDK 21)的四项启动判据一直是过的,但初始资源重载从来
没有跑完过。今天量到它不是在等磁盘、也不是线程池饿死,而是**卡死在 OptiFine 自己的一个等待标志上**;
根因是我们的 SRG 改写把载荷**自己类内**的一次调用改名成了同类里的另一个方法。修在 `1f24f23`。

### 症状:三次连跑都停在同一个地方(可复现)

1.21 这条线**能走到 tick loop**,但初始资源重载**从来没完成**:

* 三次连跑 `VERDICT: STARTED` 与 `Setting user` **都通过**,进程也一直活着;
* 三次都**一行 `Created: ...-atlas` 都没有**(不是少,是完全没有),并且**从来没有** `Sound engine started`;
* 三次都**没有写崩溃报告**,也没有自己退出 —— 就那样坐着,直到 rig 的计时器把它们杀掉;
* stderr 三次都恰好是记录的 **14 141 字节**,里面还是本线已知的那 4 条 `NoClassDefFoundError`(OptiFine
  `J1_pre9` 的 Reflector 缺陷,异常被 OptiFine 吞掉)。

这就是为什么"启动判据 + stderr 对照"这套口径**抓不到它**:判据全过、stderr 逐字相同,而画面永远停在加载
遮罩后面。**教训**:stderr 相同不等于行为相同,判据里必须有一条"重载真的完成了"的**正向**证据 —— 本线就是
`Sound engine started` 与 `Created: ...-atlas`。

### 现场:截止时刻的线程转储

* `Render thread` 在 `Minecraft.runTick` → `RenderSystem.limitDisplayFPS`:客户端**还活着**,正在正常跑帧;
* 只有一个工作线程在干活:`Worker-Main-3`,`TIMED_WAITING (sleeping)`,栈是
  `net.optifine.Config.sleep` ← `net.optifine.CustomItems.updateIcons` ←
  `net.optifine.util.TextureUtils.registerCustomSprites` ←
  `net.minecraft.client.renderer.texture.TextureAtlas.preStitch`;
* **其余工作线程全部空闲** —— 所以这不是线程池被占满,而是那一个线程在**等一个永远不会被置上的标志**。

### 字节码:它在等谁,谁本该去置那个标志

读**载荷里那份**与**运行时那份**的字节码:

* `CustomItems.updateIcons` 就是一句自旋:`while (!modelsLoaded.get()) Config.sleep(100);`
* 那个标志的**唯一写入者**是 `CustomItems.loadModels(ModelBakery)`;
* 而 `loadModels` 是从 `TextureUtils.registerCustomModels` 进去的,后者由**被换装的那份** `ModelBakery`
  在**构造函数末尾**调用。

### 定位手段:插桩排序 + 手动打开 `jdk.JavaExceptionThrow` 的 JFR

两次测量把范围钉死:

1. **插桩排序**:给构造函数与 `registerCustomModels` 各插一行探针,量到的顺序说明构造函数**确实跑了**,
   然后**在它内部就死了**;
2. **JFR 录制**:JDK 自带的 `profile.jfc` 把 `jdk.JavaExceptionThrow` **关掉了**,所以这一项是**手动打开**之后
   才录到的。异常链是:`NullPointerException` at `java.util.List.of` ← `BlockStateModelLoader.<init>` 第 77 行
   ← `ModelBakery.<init>` 第 107 行 ← `ModelManager.lambda$reload$0`。

### 根因:一次改写落在了载荷自己的类里

`renameSrgMembers` 改写了一处**解析在载荷自己类内**的引用:

* 载荷的 `ModelBakery` **同时**声明了两个方法:`private m_119364_(ResourceLocation)`(**原版**那一个,认识
  `builtin/*` → `BUILTIN_MODELS`)与 `public loadBlockModel(ResourceLocation)`(**OptiFine 自己的**加载器,
  它自己吞掉失败并返回 **null**);
* 映射表里 `m_119364_` → `loadBlockModel`,于是改名把**构造函数里那次"取缺失模型"的调用**指到了另一个方法上;
* 它返回的 null 一路走到运行时的 `BlockStateModelLoader`,那里的 `List.of(missingModel)` **拒绝 null**;
* 构造函数于是在**置 `modelsLoaded` 之前**就抛死了 ⇒ 等在 `updateIcons` 里的那个线程**永远睡下去**。

同一轮里被**证伪**的四种解释:

* **"装错了那一份副本"** —— 不是;
* **"第二次重载把标志重置了"** —— 不是;
* **"Reflector 那一步把它弄死了"** —— 不是:OptiFine 的 `Reflector.call` 自己返回 null 并**吞掉 `Throwable`**,
  它不会把异常抛到构造函数外面;
* **"保留计划(keep plan)插手了"** —— 不是:这条线上那份计划是**空的**。

### 修法:成员由"本 jar 安装的那份"声明时,不改写

在处理器里加了 `declaredByInstalledPayload` / `declaredNames`:当被引用的成员**正是本 jar 安装的那份类
自己声明的**时候,跳过这次重命名,并打一行 `Kept N SRG name(s)` 便于下次抽查。

**被否掉的另一种修法**(记下来,免得下一轮再想一遍):给 OptiFine 那个等待**加上限**。它确实能换来"重载跑完",
但代价是**自定义物品贴图** —— 那个等待存在的意义就是保护那些贴图;用超时把它们丢掉,等于拿一个功能换一个判据。

### 修后的实测(干净条件:无插桩、无 JFR)

| 判据 | 修前(三次连跑) | 修后 |
|---|---|---|
| `VERDICT: STARTED` | 通过 | 通过 |
| `Setting user` | 通过 | 通过 |
| `Sound engine started` | **不通过** | **true** |
| 本次运行新增崩溃报告 | 0 | **0** |
| `Created: ...-atlas` 行数 | **0** | **18** |
| stderr 字节数 | 14 141 | **14 141**(同样那 4 条 `NoClassDefFoundError`) |
| `Error loading model: minecraft:builtin/missing` | 有 | **没了** |

stderr 一个字节都没变,说明这次修的是**行为**,不是把异常挤到别的地方去。

### 与更早那次记录的差异(旧数字不覆盖)

更早那次记录里写的是 `[OptiFine]` **252 行**、`Pre-stitch` **14**;今天的干净跑实测是 **377 行**、**28** 次。
原因是**重载这次真的跑完了** —— 后面那些 OptiFine 阶段(图集预拼、连接纹理采集等)以前**根本没有机会执行**,
所以行数不是"变多了",而是**以前数不到**。两个数字都留在文档里:原记录 252 / 14,今日实测 377 / 28,
读者按提交时间对号入座。

## 2026-09-21:从 1.20.x 移植的 Forge shim 修复,以及它**还不充分**的那一半(外壳种类未修)

1.20.x 那条线今天在真机上量到一个缺陷:交付的 `ParticleEngine` 只在 `destroy` 与
`addBlockHitEffects` 两处用 Forge 包名的扩展接口,而 jar 里的 shim 把
`IClientBlockExtensions.of(BlockState)` 写成 `aconst_null; areturn`,调用点紧接着解引用它 ——
**攻击一次方块就 `NullPointerException`**(1.20.6 实测,07:02 与第一次 FXAA 运行都死在这里)。
修法是通用的,已移植到本分支的 `optifine/ForgeApiShims.java`:**返回自己这个类型的静态工厂不再返回
null**,接口在旁边生成一个空实现 `<interface>$Noop`,工厂返回它的新实例(类外壳则返回自身新实例)。

本分支的**量测证据**(都在 jar 上量,不是在运行的游戏上):

```
3:  invokestatic  InterfaceMethod .../IClientBlockExtensions.of:(...)...IClientBlockExtensions;
20: invokeinterface .../IClientBlockExtensions.addHitEffects:(...)Z
```

`jars-1.21.8-payload\OptifiNeoforge-1.0.0+mc1.21.8-registered.jar` 与 1.20.2/1.20.4 的 jar 里,同一个
类型交付的是**类**而不是接口:

```
public class net.minecraftforge.client.extensions.common.IClientBlockExtensions {
  public static net.minecraftforge.client.extensions.common.IClientBlockExtensions DUMMY;
```

常量池里是 `InterfaceMethodref`、解析到的却是类 → 预期
`IncompatibleClassChangeError: Found class ..., but interface was expected`。**所以上面的移植只解决了
`of()` 返回 null 这一半**;剩下的一半是外壳的**种类**判断:现在由
`shape.mustBeClass() || declaresItself(shape, name)` 决定,而后者只是因为 Forge 的 API 里有一个自身
类型的静态字段(`DUMMY`)就把整个类型做成类。正确的判据是**记录下来的调用点** —— ASM 的
`visitMethodInsn` 本来就给出 `isInterface` —— 一旦有调用点用接口方式引用它,这个类型就**必须**是接口;
接口自己的 `<clinit>` 完全可以用 Noop 实例去填那种静态字段。

**未修,也未在 1.21.x 任何一条线上跑过**:这条分支到现在还没有任何一次"客户端真的进了世界",
所以上面 ICCE 是**从字节码推出来的预期**,不是实测。它排在下一轮的第一位,和
`MemberRestorePlan` 的 `stackEffect`/`callEffect` 移植、`MemberRestoreTransformer` 的"每次调用各一个
接收者"一起,都是 1.21.x 在入世之前必须先落地的三件事。

### 外壳种类已经改了(离线量测,尚未真机)

改法与 1.20.x 一致:`MethodReference` 增加 `interfaceRef`(来自 `visitMethodInsn` 的 `isInterface`),
`Shape.callsThroughInterface()` 汇总,`generate()` 改为

```java
asInterface = shape.mustBeClass() ? false
        : (shape.callsThroughInterface() || (!declaresItself(shape, name) && isInterface(zip, name)));
```

**调用点说是接口,就必须是接口**;`declaresItself()`(Forge 的 `DUMMY` 就是这种"自身类型的静态字段")
不再把类型压成类 —— 接口在自己的 `<clinit>` 里用 Noop 实例填它,`needsNoop()` 因此同时看自类型静态工厂
与自类型静态字段。

用本分支 1.21.8 的输入离线重新生成,同一个类型的量测对比:

| | 外壳 | Noop |
|---|---|---|
| 修前(`jars-1.21.8-payload\...registered.jar` 内) | `class`,914 字节,带 `DUMMY` + 匿名 `$1` | 无 |
| 修后(同一对输入重新生成) | **`interface`**,921 字节;`of()` 返回 Noop,`<clinit>` 里 `new ...$Noop` → `putstatic DUMMY` | 五个 Noop(block / fluid / item / mob-effect / item-font),全套 92 个类过数据流审计 0 findings |

**仍未在真机上验证**:这条分支还没有一次客户端进世界,所以"接口外壳 + Noop 是不是就通了"依然是从字节码
与 1.20.x 那条线的实测推出来的。1.21.x 入世之前仍要先落地上面那两件移植(`stackEffect`/`callEffect`、
每次调用各一个接收者),然后重建各线再跑。

## 2026-09-22:从 1.20.x 移植五件(全部离线量测),其中两件在 A/B 里被发现"照抄抄错了";另两件本分支早已有

1.20.x 那条线今天在真机上结清了六件缺陷,本分支逐件对照,得到的是"三件真缺、一件从来没有、两件早已有"。
下面的每个数字都来自命令输出,量法写在每节里;真机一律未验(本轮不启动任何客户端)。

对照用的基线固定成一份:**同一对输入**(`work\<mc>\optifine-patched.jar` + `work\<mc>\runtime-<mc>.jar`),
分别用**移植前的生成器**(`git show HEAD~5:.../MemberRestorePlan.java` 编译出来的那一份)和**现在的源码**跑
一遍,再逐个 donor 类、逐个 `optifineoforge$init$` 助手签名对比。这样"移植改变了什么"不靠印象。

### 一、计划生成器:同名但描述符不同的 synthetic 也要恢复(1.20.x `da8b09d`)

规则从"名字在载荷里出现过就跳过"改成"名字**且**描述符都在载荷里才跳过"。六条线的量测(计划条目 / donor 类):

| 线 | 计划条目 | donor 类 | 其中 synthetic(`lambda$`)行 |
|---|---|---|---|
| 1.21 | 363 → **381** | 97 → 99 | 205 |
| 1.21.1 | 293 → **309** | 79 → 81 | 185 |
| 1.21.3 | 293 → **314** | 78 → 82 | 183 |
| 1.21.6 | 345 → **375** | 83 → 87 | 188 |
| 1.21.7 | 355 → **385** | 87 → 91 | 188 |
| 1.21.8 | 353 → **383** | 88 → 92 | 192 |

新增的全是运行期 synthetic 的方法体,含每条线自己的
`lambda$jumpInFluid$N(Lnet/neoforged/neoforge/fluids/FluidType;)V`(1.21.8 是 `$5`,1.21 是 `$3`)——
与 1.20.6 那个"怪物入水即死"的案例同名同形,只是这条线上还没人在水里见过它。另外一批是
`TitleScreen.lambda$render$15`、`ModelManager.lambda$reload$0`、`BlockStateModel$Unbaked.lambda$static$N` 等。

### 二、计划生成器:栈效果模型 + 装配闸(1.20.x `f56dd5b`)——以及**移植自身的两处缺陷**

移植的内容:`stackEffect`/`callEffect`/`slots`(接收者与实参都计入,long/double 占两槽)、按"还差多少值"走的
`valueRun`、`objectCreationRun`(从最后一个 `new` 正向走)、闸 `assemblesOneValue`、静态路径的
`initialiserFrom`/`staticInitialiserFrom` 统一过闸。

**照抄抄错了两处,是同一对输入的 A/B 量出来的,不是推断:**

1. **助手会把运行期类的指令搬走。** ASM 的 `InsnList` 是侵入式链表,`instructions.add(node)` 会把节点从
   原来的列表里**摘下来**,而助手的值片段正是从运行期类的构造器/`<clinit>` 里取的。摘走之后,同一个类
   后面的字段就在一个被改过的构造器里找自己的 store。1.21.8 实测:`ClientLevel.dayTimeFraction` 的助手
   因此整条消失(旧生成器有),同类的还有 `VideoSettingsScreen.TITLE`、`SingleVariant$Unbaked.MAP_CODEC`、
   `SynchedEntityData.STACK_WALKER`、`RenderSystem.PIPELINE_MODIFIERS` 等 —— 六条线合计丢掉
   3/3/3/6/6/7 个初始化器,每一个都是"字段被恢复成声明但没有值"。
   修法:`copyOf(insn)` —— 片段一律**拷贝**进助手(不认识的指令种类直接拒绝),运行期类不再被改。

2. **完整性判据问错了问题。** 1.20.x 的闸是结构式的("一条留下一个值的指令"或"new/dup/`<init>` 一组"),
   而它自己的注释写的是"模拟栈、要求净效果恰好一个值"。两者不是同一个问题:返回值的调用在消耗多于产出
   时**贡献是负的**,所以

   ```
   MAP_CODEC     = Variant.MAP_CODEC.xmap(f, f)     -> getstatic; invokedynamic; invokedynamic; invokevirtual
   STACK_WALKER  = StackWalker.getInstance(option)  -> getstatic; invokestatic
   TITLE         = Component.translatable("...")    -> ldc; invokestatic
   ```

   全都"不是那两种形状"而被拒 —— 而本文件里 `invoke()` 的注释正好记着 `MAP_CODEC` 为空会让 1.21.8 的每个
   方块状态 NPE。同时 `valueRun` 的 `hasValueAt`("最后一条指令要产出值")把已经完整的 `new/dup/<init>` 组
   也走过去了(构造调用是消耗),于是 `RenderSystem.PIPELINE_MODIFIERS` 在 1.21.6/1.21.8 上没有值 ——
   正是本文件注释里那条"客户端在首帧就死"的字段。
   修法:`runProblem(片段)` 就是那道模拟(取用超过栈高即拒、结束时必须恰好一个值、不能停在未构造的引用上),
   **接受规则与"找片段"的走法共用它**;结构式两例作为显式命名的第一种情形保留。`staticInitialiser` 补上
   实例路径本来就有的 `objectCreationRun` 回退。另加一条:静态助手没有局部变量,片段里出现任何
   `VarInsnNode` 一律拒绝 —— 1.21.6/1.21.7 实测,否则会把 `RenderSystem.enableStencil` 里"从自己的形参
   赋值"的那段搬成 `aload_0; putstatic STENCIL_TEST`,验证器报
   `Trying to get an inexistant local variable 0`。

修完之后的同一次 A/B(移植前 vs 现在):

| 线 | 计划条目 | donor 类 | 丢的 donor 类 | 丢的助手 | 新增助手 |
|---|---|---|---|---|---|
| 1.21 | 363 → 381 | 97 → 99 | 0 | 1 | 23 |
| 1.21.1 | 293 → 309 | 79 → 81 | 0 | 1 | 6 |
| 1.21.3 | 293 → 314 | 78 → 82 | 0 | 1 | 6 |
| 1.21.6 | 345 → 375 | 83 → 87 | 0 | 0 | 8 |
| 1.21.7 | 355 → 385 | 87 → 91 | 0 | 0 | 8 |
| 1.21.8 | 353 → 383 | 88 → 92 | 0 | 0 | 8 |

唯一"丢掉"的助手是 `SectionRenderDispatcher$RenderSection.optifineoforge$init$buffers`(1.21/1.21.1/1.21.3),
它的体正是 1.20.6 实测里那个被验证器拒绝的片段:

```
aload_0; invokestatic Collectors.toMap; invokeinterface Stream.collect; checkcast; putfield
```

**拒绝它才是对的**。旁证:把两套 donor 集合各自压成 jar 交给 `tools-src\StackAudit.java`(ASM 的
`BasicVerifier`),移植前是 1.21/1.21.1/1.21.3 各 1 处 `INJECTED` 坏栈、其余 0;现在是**六条线全部 0 findings**。

### 三、注入调用:每次调用各推一个接收者 + 序列接受规则 + 描述符检查(1.20.x `203da0e`)

本分支**已经有一半**:交付字节里每次调用各有一个 `aload_0`,每次 `RETURN` 用新的 `InsnList`。缺的是接受规则,
这次补上:`initialiserCalls(owner, names)` 建序列、`sequenceProblem(InsnList)` 模拟它(每条调用的实参都要在
栈上、整段结束时栈高回到原样),不满足就记日志并跳过这次注入;实例初始化器的分类从"不是 `()V` 就算"改成
只收 `(L<owner>;)V`,别的形状记一条警告。

证据(1.21.8 的 registered jar,用 jar 里的**真实** `PatchedClassTransformer` + `MemberRestoreTransformer`
跑计划里的全部 92 个类,见 rig 新增的 `tools-src\TransformerAudit121.java`,再交给 StackAudit):

* 92 个类全部过验证,**0 findings**;
* 交付字节里共 20 处注入的初始化调用,**两个方法带 2 处**,其中一个正是本分支的对应物:

```
net.minecraft.client.renderer.chunk.RenderSectionRegion.<init>(Level,int,int,int,SectionCopy[])
   12: aload_0
   13: invokestatic optifineoforge$init$modelDataSnapshot:(LRenderSectionRegion;)V
   16: aload_0
   17: invokestatic optifineoforge$init$sectionPos:(LRenderSectionRegion;)V
   20: return
```

—— 与 1.20.x 修掉的那段(`@16` 上第二次 `invokestatic` 下溢)逐字节对应,而这里每处调用都有自己的接收者。

### 四、整类保下来的类:补回载荷独有的成员与它们的静态初始化(1.20.x `8db6879`)

本分支的 `addPayloadMembers` 已经在做"把载荷独有的成员带回来",缺的是三条排除(非静态 final 字段、
`optifineoforge$init$` 生成助手、构造器与 `<clinit>`)与一条补充(静态字段连载荷自己那条初始化语句一起搬,
切片里的标签/行号/栈帧跳过而不是当成"无法复制")。证据(1.21.8,真实变换器 + 上面那个审计工具):

* `ModelDiscovery$ModelWrapper`(keep plan 保整类)现在会打
  `Not carrying the payload's final field ...ModelWrapper.id/.wrapped/.fixedSlots/.modelBakeCache`,然后
  `Gave ... the payload's 1 field(s) and 1 method(s)`;交付字节 11745 → **11847**,类里多了

  ```
  private net.minecraftforge.client.model.geometry.ModelContext context;
  public net.minecraftforge.client.model.geometry.IGeometryBakingContext getContext();
  ```

  而旧 jar 的同一步只有 11520 字节、两个成员都没有(`getContext` 的体读的正是随它一起搬来的 `context`)。
* `ModelBlockRenderer$1` 在 keep plan 下整类不动(1249 → 1249 字节),`build-jars` 打出
  `patch entries dropped for 1 class(es): [net/minecraft/client/renderer/block/ModelBlockRenderer$1]`。

### 五、屏幕追踪(1.20.x `ff0aefa` 的后半)——本分支**从来没有**

在 1.21.x 的历史里 `OPF-SCREEN` / `traceScreen` 一次都没出现过。这次移植进来,并带上那一半**实测过的**修法:
打印走 `aload_1; String.valueOf(Object)`,而不是 `getClass().getName()`。`setScreen` 合法地会被传 null
(NeoForge 的 `ClientHooks.popGuiLayer` 在最后一层 GUI 被弹掉时就是这么调的),旧写法在入世成功后约 1 秒抛
NPE 把客户端打死 —— 那正是 1.20.6 那条线上"追踪停在 ReceivingLevelScreen"的原因。同时把
`net.minecraft.client.Minecraft` 在属性打开时登记成变换目标(否则该类的 payload 副本不存在,变换器根本不会被
调用,追踪器看起来像坏了)。

证据(同一对输入 + `-Doptifineoforge.traceScreen=true`):日志出现 `Tracing every screen switch in
Minecraft.setScreen`,交付的 `setScreen` 开头是

```
 0: getstatic System.err ; 3: new StringBuilder ; 10: ldc "OPF-SCREEN "
16: aload_1 ; 17: invokestatic java/lang/String.valueOf(Object)String
20: invokevirtual StringBuilder.append ; 26: invokevirtual PrintStream.println
```

该类过数据流验证器 0 findings。**仍未在真机上验**(这条分支还没有客户端真的进过世界,而这条追踪只有在进世界
换屏时才看得出价值)。

### 六、两件本分支早已有,不需要移植

* **Forge 外壳的自类型静态工厂与外壳种类**(`43f0d62` + `345ef82`):用 1.21.8 的同一对输入离线重新生成,
  `IClientBlockExtensions` 出成 **`interface`**(带 `static final DUMMY`),`of(BlockState)` 的体是
  `new IClientBlockExtensions$Noop; dup; invokespecial <init>; areturn`,四个 Noop
  (block / fluid / item / mob-effect)。用分支当前源码重新编译和用 `tools-classpath.txt` 里那份 jar,
  输出**完全一致**,说明这一半确实已经在树里。
  但**交付侧是旧的**:`work\<mc>\stubs` 是 9/19 生成的,里面的外壳还是**类**(`public class
  IClientBlockExtensions { public static ... DUMMY; ... }`,89 个文件)。本次重建前已用分支当前代码
  逐个重新生成(每线 +4 个 Noop,1.21.8 从 89 → 92 个文件;丢掉一个 `IG.class`,那是旧工具的名字截断残留)。

* **运行时接口注入**(1.20.x `356c427`):等价物是 `injectPlannedInterfaces` + `loadTargets` 里的
  `addFirstField(targets, RUNTIME_INTERFACES)`,调用点在 `decide()` 最前面、早于所有提前 return。
  实测:把 `ModelBaker` 临时加进 keep plan(测试用的临时 jar),日志打
  `Left net.minecraft.client.resources.model.ModelBaker alone: the keep plan keeps this runtime's copy whole`,
  而交付的类**带上了** `net/neoforged/neoforge/client/extensions/ModelBakerExtension` —— 即"被整类保下来的类
  也会拿到计划要求的运行时接口"。1.21.8 自己的 keep plan 与 interface 计划没有交集,所以这条路径在真机上
  还没被自然触发过。

### 七、没做的一件(如实记)

1.20.x 后来的 `staticInitialiser` 把候选方法从"类的每一个方法"收窄成"`<clinit>` 加上它调用到的本类方法"
(`reachedFrom`),并为"先赋值、后填充"的字段加了 `isReadLater`/`populatedView`。**本次没有移植**:

* 收窄的理由在 1.21.x 上**不成立**:本文件注释说 `RenderSystem.PIPELINE_MODIFIERS` 是在 synthetic 方法里
  赋值的、所以必须搜每个方法 —— 实测(javap `runtime-1.21.6.jar` / `runtime-1.21.8.jar`)它的
  `putstatic PIPELINE_MODIFIERS` 就在 `<clinit>` 里(紧随 `putstatic STENCIL_TEST`,两者之间没有标签),
  而 `lambda$static$0` 只是被 `invokedynamic` 的 bootstrap 参数引用的另一个方法;
* `isReadLater`/`populatedView` 来自 26.x,处理的是"先放空容器再填"的那类字段(`PROFILES`),要在
  26.1.2 上量,不属于本轮;
* 下一次要动它,判据是"这条线有没有那种字段",而不是"1.20.x 有没有这段代码"。

### 八、重建(七条 ModLauncher 1.21.x 线)

每条线都按"**先用当前源码重新生成计划** → 再用 `add-line.ps1` 构建"的顺序,因为 `add-line.ps1` 只在
payload 不存在时才跑 `prepare-line`,否则 `plan\member-restores.txt` 与 `plan\donors\` 会被**静默复用**
(父会话在 1.20.4 上量到过同一个陷阱,并因此让一只两天前的计划上了真机)。

| 线 | registered jar 字节 | SHA-256(前 16) | donor 类 | 计划条目 | 嵌入的 payload 类 | Forge 外壳 | `patch entries dropped` |
|---|---|---|---|---|---|---|---|
| 1.21 | 1916959 | 83D29414B1751233 | 99 | 381 | 440 | 90 | `[ModelBlockRenderer$1]` |
| 1.21.1 | 1793475 | 96E830BEF4D4F423 | 81 | 309 | 425 | 100 | `[ModelBlockRenderer$1]` |
| 1.21.3 | 1815461 | 7FE65E2F67A837AD | 82 | 314 | 440 | 96 | `[ModelBlockRenderer$1]` |
| 1.21.4 | 1860750 | C4F57764BEF54D00 | 86 | 336 | 474 | 60 | `[ModelBlockRenderer$1]` |
| 1.21.6 | 1932104 | A9981BC138A0C92A | 87 | 375 | 487 | 87 | `[ModelBlockRenderer$1]` |
| 1.21.7 | 1959277 | EAE41B3C64B4AADF | 91 | 385 | 500 | 87 | `[ModelBlockRenderer$1]` |
| 1.21.8 | 2004734 | 618362DC1C6B27CE | 92 | 383 | 516 | 92 | `[ModelBlockRenderer$1]` |

七只 jar 全部过 `StackAudit`(ASM 数据流验证器)**0 findings**(重建前是 5/1/1/1/0/0/0);
每只里 `net.minecraftforge.client.extensions.common.IClientBlockExtensions` 都是 **interface**、
`of(...)` 的体都是 `new ...$Noop`,1.21.4 是 5 个 Noop、其余 6 条各 4 个;
每只对应的 prepared OptiFine jar 里 `ModelBlockRenderer$1.class.xdelta` / `.md5` 都是 **0 条**(keep plan 生效)。

1.21.4 / 1.21.8 两只 jar 已复制到 rig 真正启动的目录(`jars-1.21.4-new` / `jars-1.21.8-payload`),
旧文件留成 `*.pre-port-20260922`。

### 九、rig 侧两处与本轮无关但会误导人的陷阱(留给下一轮)

* **`work\1.21.4\optifine-patched.jar` 是个 239 字节的截断写入**(只有 manifest,9/19 0:52),
  而 `add-line.ps1` 见到文件存在就跳过 `prepare-line`,于是把它当成 payload 交给 `PayloadDrift` 与
  `MissingTargets`,后者直接抛 "the stub pass produced no ...-stubbed.jar"。本次把它移开
  (`*.aborted-239-bytes`)后重新准备。另外 `prepare-line.ps1` 自己的第 1 步用
  `ZipFile.Open(path, 'Create')` 写 `runtime-<mc>.jar`,文件已存在时抛
  "The file ... already exists" —— 重新准备一条线之前必须先把旧的 runtime jar 挪走。
* **`tools-classpath.txt` 把自己屏蔽了。** 它的头两项是 `tools-patch-keepfix` 与 `tools-patch-srgfix`,
  而 `tools-patch-keepfix\kynarain\cn\optifineoforge\optifine\MemberRestorePlan.class` 是**另一个分支
  那一版**的生成器(签名 `staticInitialiser(ClassNode, ClassNode, String, FieldNode)`,带
  `reachedFrom` / `isReadLater` / `populatedView`)—— 它排在仓库 jar 前面,于是**任何走 `$tools` 的
  `MemberRestorePlan`(即 `prepare-line.ps1` 第 3 步)用的都是它,而不是本分支的代码**。9/19 生成的那些
  计划就是这么来的(1.21.4 = 316 条目 / 83 donor,与那一版一致)。本轮所有重建都显式把"从当前源码编译的
  那一份"放在 `-cp` 最前面,所以交付的计划确实来自本分支;这条陷阱本身没有动(不在授权范围内)。

### 移植后的**真机验收**:七条线里 4 绿 3 红线(如实记录,未发布)

上面的移植全部是离线量测(`javap` / `StackAudit` / 生成器输出)。随后在真机上按项目自己的四项验收标准
逐条跑了一遍(`retest-all.ps1 -Only 1.21,1.21.1,1.21.3,1.21.4,1.21.6,1.21.7,1.21.8`,
记录在 rig 的 `logs\retest-121x-after-port.txt`),结果是:

| 线 | 判定 | user | sound | 崩溃报告 | stderr |
|---|---|---|---|---|---|
| 1.21 | **FAILED** | yes | **NO** | **1** | 0(记录值 14141) |
| 1.21.1 | **FAILED** | yes | **NO** | **1** | 0 = 记录值 |
| 1.21.3 | **FAILED** | yes | **NO** | **1** | 0 = 记录值 |
| 1.21.4 | STARTED | yes | yes | 0 | 0 = 记录值 |
| 1.21.6 | STARTED | yes | yes | 0 | 0 = 记录值 |
| 1.21.7 | STARTED | yes | yes | 0 | 0 = 记录值 |
| 1.21.8 | STARTED | yes | yes | 0 | 0 = 记录值 |

三条失败线的崩溃点完全一致(1.21 / 1.21.1 / 1.21.3,`crash-2026-09-22_03.38/03.40/03.42-client.txt`):

```
Description: Initializing game
java.lang.NoSuchMethodError: 'void net.minecraft.world.level.block.entity.BlockEntity.gatherCapabilities()'
  at net.minecraft.world.level.block.entity.BlockEntity.<init>(BlockEntity.java:59/60)
  at net.minecraft.world.level.block.entity.BaseContainerBlockEntity.<init>(BaseContainerBlockEntity.java:33)
  at net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity.<init>(...)
  at net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity.<init>(...)
  at net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer.lambda$static$0(...)
```

**已经量到的部分**:载荷的 `BlockEntity` 是 Forge 时代的编译产物
(`extends net.minecraftforge.common.capabilities.CapabilityProvider implements net.minecraftforge.common.extensions.IForgeBlockEntity`),
它的构造器里对**自己**调用 `gatherCapabilities()`(该方法是 Forge 那个父类提供的);而交付出去的
donor 里 `BlockEntity` 已经是 NeoForge 的形状
(`extends net.neoforged.neoforge.attachment.AttachmentHolder implements IBlockEntityExtension`)——
也就是说这个调用在运行期的继承链里没有了着落。Forge 侧的壳类**不是**缺的:交付 jar 里
`net/minecraftforge/common/capabilities/CapabilityProvider` 存在且**有** `gatherCapabilities()`
(`javap` 验过,`work\1.21\stubs` 与 `stubs.pre-port` 都有,86 → 90 个类)。

**尚未量到的部分**:这是"移植引入的"还是"重建时重新生成壳/计划带出来的",本轮没有做 A/B 对照
(可行的做法:把 `jars-1.21\OptifiNeoforge-1.0.0+mc1.21-registered.jar.pre-port-20260922` 与现在这份
逐类对照,尤其是 `BlockEntity` 的超级类/接口与 `reparent` 计划)。在查清之前,**这条分支不发布**;
三条线的旧 jar 都留在 `*.pre-port-20260922`,可以随时对照或回退。

#### 那三条失败线的机制(本轮推到的最远处)与候选修法

`MissingTargets --stub` 判断"某个成员是否缺失"时,用的是**载荷自己的继承链**。1.21 / 1.21.1 / 1.21.3 的载荷里
`BlockEntity extends net.minecraftforge.common.capabilities.CapabilityProvider`,于是 `BlockEntity.<init>` 里那句
`this.gatherCapabilities()` 被算成"从父类继承得到,不缺失",只在 **Forge 壳类** `CapabilityProvider` 上留了实现
(交付 jar 里确实有,`javap` 验过)。但装载时这条线把这个类**改挂**到了运行期的继承链上
(`extends net.neoforged.neoforge.attachment.AttachmentHolder implements IBlockEntityExtension`),
于是那句调用在**运行期**的链上没有任何着落 —— 1.21 的运行期 `BlockEntity` 自己**也没有** `gatherCapabilities()`
(`javap work\1.21\runtime-1.21.jar` 里搜不到),这就是 `NoSuchMethodError` 的来源。

**一句话**:壳(stub)是按载荷的父类算的,改挂之后父类换了 —— 凡是"载荷从 Forge 父类继承、而运行期新父类没有"的成员,
都必须补在**类自己**身上(而不是父类身上)。

**候选修法**(本轮未做,留给下一轮,顺序即优先):
1. 让打壳这一步在**改挂之后**的继承链上再判一次缺失(`HierarchyPlan`/`reparent.txt` 与 `MissingTargets` 的输入
   本来就在同一条流水线上),把这类成员落到 `stubs-full.txt` 里由装载器注入;立刻能验的证据是
   `BlockEntity.gatherCapabilities()V` 出现在 `work\<mc>\plan\stubs-full.txt` 且重建后崩溃消失。
2. 或者给 `reparent.txt` 里这类类补一条成员级保留/补桩的显式条目(与 1.20.x 的 `keep-runtime` 同思路)。
3. 再不然回退这条线的 `reparent` 计划里 `BlockEntity` 的那一条,并量一次验收(代价是 NeoForge 的扩展接口丢失)。

无论哪条,**在三条线回到四项验收全绿之前不发布**;旧 jar 都在 `*.pre-port-20260922`。

### 修:计划里的运行期成员桩在**换装的类**上被丢掉(三条红线的真正原因)

`decide()` 抓取载荷**之前**先无条件调 `stubMissing()`,因为有些桩的宿主是永不换装的运行期类 —— 但紧接着
`input.methods = methods` 这一句会用载荷的成员表**整体替换**运行期那份,刚加进去的桩跟着一起没了。
`stubMissing()` 只补缺的(幂等),所以在替换之后**再调一次**就是修法(已提交)。

量测(2026-09-22,三条线崩点相同):

* 修复计划把 `net/minecraft/world/level/block/entity/BlockEntity` 改挂到
  `net/neoforged/neoforge/attachment/AttachmentHolder`;载荷的 `BlockEntity.<init>` 调
  `this.gatherCapabilities()`,该方法原本来自被改挂丢掉的 Forge 父类(运行期的 `BlockEntity` 自己也没有);
* 该成员**确实在**交给装载器的桩表里(交付 jar 内 `optifineoforge/stubs.txt` 有这一行,`javap` 验过),
  第一次 `stubMissing()` 也确实把它加到了运行期节点上,然后被那句赋值扔掉 —— 所以"桩在 jar 里却仍然
  NoSuchMethodError" ;
* 症状:`Initializing game` 阶段
  `NoSuchMethodError: 'void net.minecraft.world.level.block.entity.BlockEntity.gatherCapabilities()'`
  at `BlockEntity.<init>(BlockEntity.java:59/60)`;
* 修后三条线的四项验收:**1.21** STARTED/user yes/sound yes/崩溃 0/stderr **14141 = 记录值**;
  **1.21.1**、**1.21.3** 同样全绿(0 崩溃、stderr 0 = 记录值)。修前同一处 OptiFine 只走到 28/27/27 行,
  修后 **377/232/225** 行 —— 也就是说之前那三条线其实是"启动 1 秒就崩"。
* 1.20.x 分支有同一处形状(`stubMissing` 在换装赋值之前),已在该分支单独修并复验。

### 建档 + 光影包 七条线全跑(2026-09-22):真实结果与两处新缺陷

`run-save-shaders-all.ps1 -Pack MakeUp-UltraFast-9.5e.zip`(quick play 进存档,RigSession,1.20.4 模板世界):

| 线 | 判定 | sound | 世界 | 崩溃 | 光影包 | 证据 |
|---|---|---|---|---|---|---|
| 1.21 | STARTED | yes | **NO** | 0 | loaded | 0 region 文件;tracer 只有 `GenericMessageScreen -> TitleScreen`,**quick play 什么都没做** |
| 1.21.1 | STARTED | yes | **yes** | 0 | loaded | 4 region + level.dat |
| 1.21.3 | STARTED | yes | **yes** | 0 | loaded | 4 region + level.dat |
| 1.21.4 | STARTED | yes | **yes** | 0 | loaded | 4 region + level.dat |
| 1.21.6 | STARTED | yes | **yes** | 0 | **not loaded** | OptiFine 自己的 FXAA post chain JSON 解析失败(见下) |
| 1.21.7 | STARTED | yes | **yes** | 0 | **not loaded** | 同上 |
| 1.21.8 | STARTED | yes | **yes** | **0(修后)** | loaded | 10 region + level.dat |

也就是说:**这七条线里 6 条真的进了世界**,其中 4 条同时加载了光影包 —— 这是这条分支第一次有客户端进世界。

#### 修:1.21.8 的 `Cannot get config value before config is loaded`(已修并复验)

修前的崩溃(`crash-2026-09-22_04.54.13-server.txt`,世界第一次 tick 实体时):

```
java.lang.IllegalStateException: Cannot get config value before config is loaded.
  at net.neoforged.neoforge.common.ModConfigSpec$ConfigValue.getRaw(ModConfigSpec.java:1235)
  at net.minecraft.world.level.Level.guardEntityTick(Level.java:593)
```
根因与 1.20.2/1.20.4/1.20.6 上的那一件同一族:OptiFine 编译的 `IntegratedServer.initServer()` 把两个服务器生命周期
调用都走了它自己的 reflector,而 reflector 按 **Forge 的名字**(`net.minecraftforge.server.ServerLifecycleHooks`)
去找类,这条运行期只有 `net/neoforged/neoforge/server/ServerLifecycleHooks` ⇒ 钩子从不运行 ⇒ NeoForge 的服务器
配置从不加载 ⇒ 第一个读它的地方就抛。修法就是 1.20.x 已经在用的那一行成员级保留
(`net/minecraft/client/server/IntegratedServer<TAB>initServer<TAB>()Z`,运行期自己的同名方法无条件调用钩子),
重建时能看到 `patch entries dropped for 1 class(es): [net/minecraft/client/server/IntegratedServer]`。
复验:同一条 quick play + MakeUp 光影包 ⇒ 世界 yes、**崩溃 0**、10 个 region 文件 + level.dat、光影包 loaded。

#### 仍未修(两件,都已定位到症状与下一步)

1. **1.21 的 quick play 不打开世界**(而 1.21.1/1.21.3/1.21.4/1.21.6/1.21.7/1.21.8 同一套 rig 都打开了)。
   tracer 事实:客户端只走到 `GenericMessageScreen -> TitleScreen` 就停住,resource reload 之后**没有任何**
   世界加载日志(无 "Preparing spawn area"、无 "Failed to load level"),region 文件 0。加入上面那条
   `IntegratedServer` 保留之后**没有变化**,所以这一个不是同一处根因。下一步(1.20.4 已经用过并成功的办法):
   在这条线上用**菜单驱动**进世界(1.20.4 的 `world-test-1204.ps1` 就是为此写的:真实光标点击 + 从字节码读出的
   世界列表几何),而不是继续等 quick play。
2. **1.21.6 / 1.21.7 的光影包加载不了**(世界能进、sound yes、崩溃 0),日志里是 OptiFine 自己的 post chain 与
   新版解析器不兼容:
   `Failed to parse post chain at minecraft:post_effect/fxaa_of_2x.json | JsonSyntaxException: No key
   fragment_shader / No key vertex_shader`,随后 `[OptiFine] Resource not found: minecraft:shaders/post/fxaa_of_2x.json`。
   1.21.5 起后处理链从 `shaders/post/*.json`(`vertex`/`fragment` 键)改成 `post_effect/*.json`
   (`vertex_shader`/`fragment_shader`),而这条线的 OptiFine 构建仍是旧格式 —— 这看起来是 **OptiFine 版本与 MC
   版本之间的不兼容**,不是我们加载器制造的;要如实定论还需一次对照(把同一份 OptiFine 直接放进同版本 NeoForge
   跑一次,看是否同样报错),本轮没做。

#### 1.21 的 quick play 沉默:一个被证伪的假设,如实记下

上面那条"1.21 的 quick play 什么都不做"我试着用"存档是旧的/更新的版本"解释:那个 gameDir 里
`saves\RigSession\level.dat` 的时间戳是 **2025-12-13 17:51:43**,看起来像上一个版本留下的存档。**已量测并证伪**:
删掉整个 `saves\RigSession` 再跑一次,harness 日志写的是
`save: created RigSession from ...\templates\level-1.20.4.dat`,而新建出来的 `level.dat` 仍是那个 2025-12-13
时间戳 —— 那只是**模板文件自己的 mtime**(`Copy-Item` 保留原时间戳),存档本身每次都是从模板新建的、
DataVersion 就是 1.20.4 的,和其他六条线完全一样。结果还是 `world NO`、0 region 文件。

所以:**两条解释都已排除**(join 钩子缺失、存档版本),这一条仍是线特有的、未解释的 quick play 沉默;
下一步按 1.20.4 已经成功的办法做——用菜单驱动进世界(真实光标点击 + 从客户端字节码读出的世界列表几何),
而不是继续在 quick play 上花时间。

### 1.21 的世界入口:四个连着的缺陷,修掉三个;并记下一次"看似系统化、实际错误"的改动

1.21(21.0.167)这条线的 quick play **什么都不做**(tracer 只有 `GenericMessageScreen -> TitleScreen`),
所以改用**菜单驱动**(新增 `world-test-121.ps1`,由 1.20.4 那份改写;1.21 的 `SelectWorldScreen` 布局用
`javap` 验过与 1.20.4 逐字相同:行中心 `52+36i+18`、Play 按钮 `(w/2-154,h-52,150,20)`)。改菜单之后,世界入口
一路上连着暴露四个缺陷,修掉三个(都有真机证据):

1. **`ModelPart.getChild`(与 1.20.4 同一件)** —— 载荷的 `getChild` 按 `child.getId()` 匹配,而这条线的载荷虽然
   带了 `PartDefinition`,它的 `bake` 仍只 `new ModelPart(List, Map)`、**从不 `setId`**,于是 id 全 null、
    `Failed to create model for minecraft:skull` → `Caught error loading resourcepacks, removing all selected
   resourcepacks`。修法:整条分支七条线都加上 `ModelPart getChild` 的成员级保留(运行期的 `children.get(name)`),
   每条重建时都打印 `patch entries dropped ... [ModelPart]`;**七条线验收全部回到记录值**(1.21 = 14141,其余 0)。
2. **`IntegratedServer` 必须是整类保留,不能只保留成员**。只保留 `initServer()Z` 时,载荷那份类仍被装进去,
   而**它的构造器**调用 `this.m_236740_(GameProfile)` —— 载荷和运行期视图都不声明这个方法,于是
   `Description: Starting integrated server` 阶段
   `NoSuchMethodError: void net.minecraft.client.server.IntegratedServer.m_236740_(com.mojang.authlib.GameProfile)`
   (`crash-2026-09-22_06.00.51-client.txt`)。改成 `IntegratedServer<TAB>*` 整类保留后,世界加载越过了这一步。
3. **被改挂的 `BlockEntity` 连着缺三个 Forge 父类成员**:`gatherCapabilities()V`(启动期 `NoSuchMethodError`,
   已在上一轮修)——修完暴露 `getCapabilities()Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;`
   (生怪点时 `[Worker-Main-12/ERROR] ... NoSuchMethodError`,spawn area 卡在 0%);Forge 壳 `CapabilityProvider`
   一共就声明这三个成员,所以三个一起补进 `stub-additions-1.21.txt`(并同步给同样改挂的 1.21.1/1.21.3)。
4. **`MinecraftServer.m_195518_()Z`** 这个 vanila getter 运行期视图里只有字段 `isSaving` 没有它,载荷 `Gui`
   在 `tickAutosaveIndicator` 里调用 → 世界刚加载完就
   `NoSuchMethodError: boolean net.minecraft.server.MinecraftServer.m_195518_()`(`crash-2026-09-22_06.14.06`);
   补桩返回 false(= 未在保存)是正确的默认值,已记进 `stub-additions-1.21.txt`。

#### 一次被自己量倒的"系统化修复",记在这里免得下次再走一遍

产生这一串"补一个又冒一个"的根源看起来是打壳那一步的 classpath:它以前拿的是 rig 里**所有** jar(583 个),
`MissingTargets` 按类名解析成员,别的 MC 版本/别的线里的同名成员会让"缺失"看不出来。于是把它收窄成
"这条线自己的 version json + profile json + universal + runtime 视图"(114 个 jar,做法抄自 1.20.x 的
`rebuild-120x-line.ps1`)——结果**确实**从 4 个缺失涨到 **46** 个,但里面混着一批**运行期视图自己缺的 vanilla
SRG 成员**:`ServerLevel.m_7654_()`(`Level.getServer()`)、`MinecraftServer.m_195518_()`(`isSaving()`)、
`m_7570_(Z)` 等。给这些方法塞"默认体"不是修复而是**更糟**:`getServer()` 返回 null 比缺失更致命。
实测就是这个后果——收窄那次重建之后的世界测试(`logs\world-121-run6.txt`)连世界行都没命中、一个世界标记都没有。
所以这次收窄**已回退**(代码里留了完整说明与量测),`add-line.ps1` 继续用宽 classpath + 逐条
`stub-additions-<mc>.txt`。**真正的缺陷是运行期视图对 vanilla SRG 成员不完整**,这是一个独立待办。

#### 现状(如实)

1.21 现在能走到"世界加载中"(菜单:标题 → Singleplayer → 世界行 → Play;run4/run5 里 `Preparing spawn area`、
`Time elapsed`、`Loading recipes/advancements` 都出现过,`session.lock` 与 region 文件被写),而 run6/run7 又
没有起世界 —— 也就是说**菜单驱动这条路的入口本身还不稳定**(猜测与"行是否被选中"有关:Play 按钮在没选中行时
是禁用的)。**因此 1.21 目前既不能记成"进世界",也不能记成"确认失败"**;下一步是把"先确认行被选中
(用键盘 Down 选中后在截图/tracer 上确认)再点 Play"做成确定性步骤,并把 `Down+Enter` 这条纯键盘路作为主路。
其余六条线在 ModelPart/IntegratedServer 改动后还没重跑建档光影(1.21.8 也换了整类保留)。

### 1.21 已经**进到世界里**(joined the game),但随即死在同一个"缺 vanilla SRG 成员"的家族上 —— 这个家族不再用补桩处理

入口现在稳定了:给菜单驱动加了"**没有确认世界列表真的起来了就不开始第二步**"的等待(`world-test-121.ps1` 的
step 1b;1.20.4 那份同一处假设也一并补上),因为量到过"打开列表的点击在第一步的轮询窗口之后才被处理,
于是第二步的全部尝试都花在标题界面上"。加了这个守卫之后的一次运行(`logs\world-121-run8.txt`):

* 世界列表已就绪 → 行点击 + Play → `GenericMessageScreen` x3 → `ProgressScreen` → `LevelLoadingScreen`;
* 标记:`Preparing spawn area` / `Preparing start region` / `Time elapsed` / `Loaded recipes+advancements` /
  `Changing view distance to` / `Generating keypair`,并且 **`joined the game` = True**;
* 光影包 `MakeUp-UltraFast-9.5e.zip` **loaded**;NullPointerException 0;
* 但随后 **2 份崩溃报告**,而且都是同一个家族:

```
[Server thread] Description: Exception in server tick loop
java.lang.NoSuchMethodError: 'int net.minecraft.server.MinecraftServer.m_7186_(int)'
  at net.minecraft.server.level.ChunkMap$TrackedEntity.scaledRange(ChunkMap.java:1541)
  at ...ChunkMap.addEntity -> ServerChunkCache.addEntity

[Render thread] Description: Unexpected error
java.lang.NoSuchMethodError: 'boolean net.minecraft.client.server.IntegratedServer.m_129918_()'
  at net.minecraft.client.renderer.GameRenderer.tryTakeScreenshotIfNeeded(GameRenderer.java:1236)
  at ...GameRenderer.render
```

加上上一轮的 `MinecraftServer.m_195518_()`(`isSaving()`),这已经是**三个**同类成员:都是 **vanilla 的 SRG 名字**、
都被**运行期自己的类**(`ChunkMap$TrackedEntity`、`GameRenderer`)调用,而启动时用的那份运行期类里**都没有**。
它们不在宽 classpath 的桩表里(别的 MC 版本的 jar 让"缺失"看不出来),而在收窄 classpath 那一次里它们是**被列出来
的**(`m_7186_` 就在那张 46 条的清单里)—— 这正好互相印证:**缺陷不在载荷,而在我们启动用的运行期视图/命名视图
不完整**。

**因此这一类不再逐个补桩**,理由是语义:补桩给的是"默认体",而这两个方法的默认值会把功能悄悄弄坏 ——
`m_7186_(I)I` 是实体追踪距离的缩放(`scaledRange`),返回 0 会让实体追踪直接失效;`m_129918_()Z` 是
`isPublished` 一类的状态查询,返回 false 也不是事实。**正确修法是让运行期真的带上这些成员**(或让载荷与运行期在
命名上对齐),这属于比"每条线一张补桩表"更深的修复,已作为本轮的结论与下一步留在这里。

**现状小结(如实)**:1.21 现在能起世界、能 join、能加载光影包,但会在 join 之后的 tick 循环里因缺成员崩溃,
所以**不能记成绿**。其余六条 1.21.x 线在 ModelPart / IntegratedServer 改动之后还没重跑建档光影。

### 定位到本轮最重要的结论:**这条分支的载荷是 SRG 命名,而启动用的运行期类不是** —— 1.20.2/1.20.4 当年靠 `SrgRemap` 解决过同一件事,1.21.x 从没做过

上一节那三个缺失成员(`MinecraftServer.m_7186_(I)I`、`m_195518_()Z`、`IntegratedServer.m_129918_()Z`)
一直被当作"我们的运行期视图不完整"。这一轮把它查到了底,量测如下:

| 检查 | 结果 |
|---|---|
| 四条线的构建视图 `work\<mc>\runtime-<mc>.jar`(`javap`) | 1.21 / 1.21.1 / 1.21.3 / 1.21.8 **都没有**这三个成员 |
| 启动用的客户端 jar `libraries\net\minecraft\client\1.21-20240613.152323\client-1.21-...-srg.jar` | 有 `MinecraftServer`、`IntegratedServer`,但 **同样没有** `m_7186_` / `m_195518_` / `m_129918_` |
| 同一个类的字段名 | `private volatile boolean isSaving` —— **Mojang 风格的名字**,不是 SRG 的 `f_..._` |
| NeoForge universal jar | 不含这两个类(它只是补丁集) |
| 交付 jar 里这些类的来源 | `optifineoforge/patched/.../ChunkMap$TrackedEntity.class`、`GameRenderer.class`、`IntegratedServer.class` —— **载荷自己的编译产物被装了进去** |

也就是说:崩的那两处调用(`ChunkMap$TrackedEntity.scaledRange` 里 `MinecraftServer.m_7186_(I)I`、
`GameRenderer.tryTakeScreenshotIfNeeded` 里 `IntegratedServer.m_129918_()Z`)**来自载荷自己的类**,而载荷是按
**SRG 成员名**编译的(OptiFine 1.21 的构建即如此),启动用的运行期里那些成员却是 **Mojang 名**。这不是"视图缺成员",
而是**两套命名没有对齐** —— 也就是 1.20.2 / 1.20.4 当年必须做的那一步:`SrgRemap` 把载荷的 SRG 名改写成运行期的
official 名(`add-line.ps1` 的 `-SrgMappings` + `-ObfOfficial`,配套 `proguard-to-tsrg.ps1` 生成 obf→official 表)。
**1.21.x 的这些线全部是在没有这一步的情况下建的**,所以:
* 大多数**载荷自己的类之间**的调用能跑(同一个编译产物,名字一致),这就是为什么这些线能起、能进世界;
* 一旦载荷的**补丁类**去调**运行期**的 vanilla 成员(而且是按 SRG 名写的),就会 `NoSuchMethodError` ——
  触发点取决于跑到哪条代码路径,所以表现为"有的线看着没事、有的线 join 之后崩"。

**下一步(下一轮的第一件事)**:给 1.21.x 建 `SrgMappings`(MCPConfig 的 joined tsrg)与 `ObfOfficial`
(`proguard-to-tsrg.ps1` 从 Mojang client mappings 生成,1.20.x 已有 `obf-official-1.20.4.tsrg` 的先例),
用 `add-line.ps1 -SrgMappings ... -ObfOfficial ...` 重建其中一条线(建议 1.21.4 或 1.21.8,它们已有世界+光影
记录可对照),然后重跑验收与建档光影。**如果这一步成立,它应当一次消掉整族缺成员崩溃**,而不是继续一个成员一个成员地补桩
—— 这条分支接下来不再往 `stub-additions` 里加这族成员。

#### 对上一节结论的**收窄**(同一轮内的自我更正,量测在后)

上一节写成"这条分支的载荷是 SRG 命名、运行期是 Mojang 命名"。**这个说法太强,已量测收窄**:把载荷
`ChunkMap$TrackedEntity` 的字节码里所有 `m_`/`f_` 形式的调用抽出来数了一遍 —— 一共只有 **2 条**
(`ServerLevel.m_7654_`、`MinecraftServer.m_7186_`),两条在启动用的客户端 jar 里都**不存在**;类里其余调用
都不是 SRG 名。也就是说这不是"整份载荷按 SRG 编译",而是**载荷里残留了少量 SRG 名引用**(本轮观察到会崩的
一共 3 个成员),而运行期那一侧是 Mojang 名 —— 两边没对齐。

正确的下一步因此更具体,而且 rig 里**已经有现成的工具**:

1. 用 rig 自己的 `SrgResidue` 审计这几个 1.21.x 载荷(1.20.4 上跑过,产物是 `logs\srgresidue-1.20.4-*.txt`),
   把"还有多少 SRG 残留、分别在哪几个类"量出来,而不是靠崩溃一个个撞;
2. 1.20.x 那条线解决这族问题的做法是:`SrgRemap` 把载荷的 SRG 名改写成运行期名(`add-line.ps1` 的
   `-SrgMappings` + `-ObfOfficial`),并且 `41b4bb4` 修过"连接键里的描述符没有归一"这个让残留漏网的原因
   —— 1.21.x 从未做过这一步,也没有对应的 `obf-official-1.21*.tsrg`;
3. 先在**一条**已有世界+光影记录的线上试(1.21.4 或 1.21.8),重跑验收 + 建档光影,用它判断"残留清干净之后
   整族崩溃是否消失"。**这条分支接下来不再往 `stub-additions` 里加这族成员**(默认体会把功能悄悄弄坏)。

### SRG 残留的**预测性审计**跑起来了:1.21 这条线 114 条残留(而且三条崩溃成员都在名单里),其余线 0 条

上一轮把"载荷里残留少量 SRG 名"这件事量到了一半。这一轮用 rig 里**早就有的** `SrgResidue` 工具把它量完整了:
先给 1.21 建表(neoform zip 里的 `config/joined.tsrg` + `proguard-to-tsrg.ps1` 从 Mojang mappings 生成的
`obf-official-1.21.tsrg`,8269 类 / 37906 字段 / 73419 方法),然后跑:

```
java -cp <tools> kynarain.cn.optifineoforge.optifine.SrgResidue joined-1.21.tsrg <neoform-merged> \
     work\1.21\optifine-patched.jar work\1.21\runtime-1.21.jar
```

| 线 | SRG 形状引用 | 载荷自己声明的 SRG 名 | residue |
|---|---|---|---|
| **1.21** | **306** | **84** | **114 distinct**(全部 "no table entry") |
| 1.21.4 | 0 | 0 | **none - every SRG reference resolves to a runtime member** |

关键点:本轮真机上崩掉的三个成员**都在名单里** ——
`Gui -> MinecraftServer.m_195518_()Z`、`GameRenderer -> IntegratedServer.m_129918_()Z`、
`ChunkMap$TrackedEntity -> MinecraftServer.m_7186_(I)I`。也就是说这个审计**能先于崩溃把家族列出来**
(完整清单在 `logs\srgresidue-1.21.txt`,306 条引用 / 114 条去重,去重后候选 63 条)。
**而且 1.21.4 是干净的** —— 所以这不是整条分支的问题,而是**1.21.0 这条线自己的 OptiFine 构建
(`OptiFine_1.21_HD_U_J1_pre9`)是个混合体:大部分成员是 official 名,却残留了这批 SRG 名引用**。

#### 两条"看起来能修"的路,都量过并**否掉**了

1. **换成更新的 OptiFine 构建** —— 不行:1.21.0 的正式版 `OptiFine_1.21_HD_U_J1.jar` 在 optifine.net 上
   已下架(`File not found.`),镜像(`bmclapi2`)对 `1.21/HD_U_J1` 与 `1.21/HD_U_J1_pre9` 都是 404。
2. **照 1.20.2/1.20.4 那样做 `SrgRemap`** —— 不行,而且这次是**量出来的**不行:`prepare-line -SrgMappings
   joined-1.21.tsrg -ObfOfficial obf-official-1.21.tsrg` 跑完的输出是
   `rewrote 0 method and 0 field names, 34261 could not be resolved, 0 renames refused`;新载荷还变小了
   (5420805 字节),plan 暴涨到 **6167 条 / 388 donor**(对照原来的 381/99)——因为 `SrgRemap` 的输入假定是
   obf/SRG 命名的载荷,而这条线的载荷**大部分已经是 official 名**,表里没有对应项,于是"什么都没改"。
   这次实验的载荷与 runtime 视图**已回退**(`*.pre-srgremap`)。

#### 剩下的路(下一轮的候选,按代价排序)

1. **针对残留本身修**:114 条去重里有一大批是**载荷自己内部类之间的自引用**
   (`RenderSystem$AutoStorageIndexBuffer` 调自己的 `f_157469_` 等 9 条、`GlStateManager$*` 一族、
   `ParticleEngine$ParticleDefinition` 一族),只要载荷那份类被装进去就能自己解析;真正跨到**运行期**的
   集中在少数几个类:`Gui`、`ChunkMap$TrackedEntity`、`GameRenderer`、`DebugScreenOverlay`、`TitleScreen`、
   `ClientLevel`、`ClientLevel$EntityCallbacks`、`VertexBuffer`/`VertexConsumer`、`ModelPart`
   (`ModelPart.m_171324_` 就是 `getChild`,而我们已经把该类整份换成运行期的了)。
   可行的做法是**按 owner 类做定向改写**(用同在 rig 里的 `SrgMemberMap`,把载荷里这些 `m_*/f_*` 引用改写成
   运行期的 official 名),而不是按 obf 名字空间整体 remap —— 这也是 `SrgResidue` 的 `classify()` 已经在做的判断,
   只差一个写回步骤。
2. 或者对列表里那几个"OptiFine 补丁并非关键"的类(如 `ChunkMap$TrackedEntity`)先按整类保留处理,把崩溃面缩小,
   再逐条处理剩余 —— 但这是**权宜**,不解决 `Gui` 这种必然要装载荷的类。

#### 收尾状态与一个**新的差异**(如实记,下一轮必须查)

那两条被否掉的路都留下了痕迹,已清理并复验:

* `SrgRemap` 那次实验产生的载荷与 runtime 视图**已回退**(`work\1.21\*.pre-srgremap`),失败实验写下的
  plan(6167 条 / 388 donor)**已重新生成**:用本分支源码编译的生成器重算 + 清空 donor 目录后重算,
  现在是 **plan 381 条 / donor 99 个 / keep plan 3 行 / interface plan 13 行 / access plan 255 条**,
  与这条线此前可用的那一份同量级,registered jar 已重建(`add-line.ps1 -SkipInstall`)。
* 重建后 1.21 的四项验收仍然通过(STARTED / user yes / sound yes / 崩溃 0),**但 stderr 从记录值
  **14141** 变成 **46767** 字节**([OptiFine] 241 行)。这是一个**新的、尚未解释的差异**,必须先查清再谈这条线是绿
  —— 最可能的原因是:这一次的 plan/donor 是用**本分支源码**的生成器重算的,而此前那次用的是
  `tools-classpath.txt` 里那份**别的分支时代**的生成器(`tools-patch-keepfix`,上一轮已记录它"把自己屏蔽了"),
  两份生成器产出的计划不同 ⇒ 载荷里被"补回来"的成员不同 ⇒ OptiFine 的 Reflector 噪声(非致命日志)随之变化。
  这条假设下一轮用一次 A/B 就能定(同一个载荷,两份生成器各出一份 plan,分别重建后比 stderr 与
  `logs\srgresidue-1.21.txt` 的两组数字)。

#### 回退后的一次复跑:行为与手术前一致(如实记)

用回退并重建后的 1.21 jar 又跑了一次菜单驱动(`logs\world-121-run9.txt`):世界列表打开了,但这一次**没有起世界**
(`world row click: never landed`,无世界标记),**崩溃 0、NPE 0** —— 与之前的 run6/run7 同类(run8 成功进过世界),
也就是说**这次清理与重算没有改变行为种类**,只是把被污染的计划换回了正常量级(381/99)。入口的"有时起、有时不起"
这件事因此仍然独立存在,下一轮仍要先把"选中行 → 点 Play"做成确定性步骤(本轮已知的线索:Play 在未选中行时是禁用的,
而我的行点击只有一部分能选中)。
1.21 的 stderr 差异(46767 vs 记录 14141)仍挂着,机制假设见上一节,未做 A/B。

### 用 `SrgResidue` 的清单**定点修 1.21 的崩溃**:两处修好并复验,第三处否掉了错误的修法

有了完整清单(见上一节),这一轮不再靠崩溃撞,而是照清单修:

1. **`ChunkMap$TrackedEntity` 整类保留运行期版本** —— 载荷那份调用 `MinecraftServer.m_7186_(I)I`
   (SRG 名,运行期没有),崩在实体追踪的 tick 路径。重建时打印
   `patch entries dropped for 1 class(es): [net/minecraft/server/level/ChunkMap$TrackedEntity]`,**该服务器侧崩溃消失**
   (复跑 `logs\world-121-run11.txt`:崩溃报告从 **2 份降到 1 份**)。
2. **`IntegratedServer.m_129918_()Z` 补成返回 false**(= "not published")—— 载荷 `GameRenderer` 在
   `tryTakeScreenshotIfNeeded` 里调它,而 `GameRenderer` 是光影钩子、**不能**整类保留。补桩后那一处不再崩。
3. **第三处(`m_182649_()Ljava/util/Optional;`,同一方法里紧接着的一行)我试了"把
   `tryTakeScreenshotIfNeeded` 这个方法体保留运行期版本"—— 这个修法被否掉并已回退**,因为量到
   `build-jars` 会因此**把整个类的 patch 条目丢掉**
   (`patch entries dropped for 1 class(es): [net/minecraft/client/renderer/GameRenderer]`),
   也就是 **OptiFine 的 `GameRenderer`(光影入口)根本不会被装进去** —— 那不是"修残留",而是**悄悄丢掉光影支持**。
   这条已作为反例写进 keep 计划的注释里。
   另外记一条判据:**对 `Optional` 这类返回对象的方法补空桩会返回 null**,调用方 `Optional.isEmpty()` 直接 NPE,
   所以这一族不能用补桩糊过去。

**下一轮的直接结论**:`tryTakeScreenshotIfNeeded` 里那三个 SRG 调用(以及 `DebugScreenOverlay` 对
`IntegratedServer.m_306290_/m_304767_/m_129880_` 的调用)只能靠**定向改写**(把载荷里这些 `m_*` 引用改成运行期
official 名,用同在 rig 里的 `SrgMemberMap`,`SrgResidue.classify()` 已经把"官方名是什么"算出来了)或者
"带正确语义体的桩"来修,这是下一轮的第一件事。1.21 现在的状态是:能起世界、能 join、能加载光影包,
**剩下 1 份客户端崩溃**(`m_182649_`)与若干未触发的残留。

### 1.21 **第一次完整跑通"建档 + 光影包"且零崩溃**(join → 自动保存 → 0 崩溃),以及这一轮用的判据

接着上一节的清单继续定修,这一轮把剩下的三处补掉,并且改动了一条**加载器规则**:

1. **加载器:`Optional` 返回类型不能补 null**。`defaultBody()` 现在对 `java/util/Optional` 返回
   `Optional.empty()` 而不是 `aconst_null` —— 理由是量出来的:调用方一律写 `isPresent()/isEmpty()/orElse()` 一类的写法,
   null 只是把"缺成员"变成一个 NPE(`GameRenderer.java:1238` 那处就是这样)。这是**产品侧规则**,不是每条线的补丁。
2. `IntegratedServer.m_182649_()Ljava/util/Optional;` 与 `m_129921_()I`(都在同一处调用链里,SrgResidue 都列过)进补桩表:
   前者现在由上面那条规则返回 `Optional.empty()`,后者返回 0。
3. `BakedModel.useAmbientOcclusion(BlockState, RenderType)Z`(NeoForge 的两参扩展形式,载荷
   `ModelBlockRenderer.tesselateBlock:86` 调用)补桩返回 false。

**复验(`logs\world-121-run13.txt`,菜单驱动 + MakeUp 光影包)**:

```
reached a world : True      joined : True
world markers   : Preparing spawn area, Preparing start region, Time elapsed, Loaded recipes/advancements,
                  Changing view distance to, Saving and pausing game, Generating keypair
shader pack     : [Shaders] Loaded shaderpack: MakeUp-UltraFast-9.5e.zip
crash reports   : 0         NullPointerException : 0
```

这是这条线**第一次完整跑通**"进世界 → join → 自动保存 → 退出前零崩溃",而且光影包确实加载。四项验收同时保持通过
(STARTED / user yes / sound yes / 崩溃 0)。

**如实记下这一轮修法的代价与边界**(不能让读者以为 1.21 已经干净):
* `useAmbientOcclusion(...)` 的桩回答 **false**,即那个模型**不做环境光遮蔽** —— 这是**可见的视觉偏差**,不是崩溃。
  这类"语义型"补桩(返回默认值)只能让线跑起来受测,**不是**最终修法;
* 真正的修法仍是**定向改写**:把载荷里那些跨到运行期的 `m_*`/扩展成员引用改写成运行期自己的成员
  (`SrgResidue.classify()` 已经算出官方名,`SrgMemberMap` 在 rig 里),这一步仍未做;
* 1.21 的 **stderr 记录差异仍在**:四项检查通过,但 stderr 是 **46767** 字节(记录值 14141),机制假设(生成器版本不同)
  尚未 A/B;
* 其余六条 1.21.x 线**还没重跑建档光影**(它们在 ModelPart / IntegratedServer 两处改动之后需要重跑),
  而它们各自是否也有这条线的 `SrgResidue` 清单之外的缺失成员,要按同一套办法逐条量。

### 把这套办法铺到其余六条线:5/7 条做到"进世界 + 光影包加载 + 0 崩溃"

上一节修好 1.21 之后,按同一套办法把这条线的五个缺失成员**铺到其余六条线**
(`stub-additions-<mc>.txt`;装载器只在该成员确实缺失时才注入,所以在不需要它的线是惰性的),逐条重建并重跑
建档 + 光影包(quick play,RigSession,MakeUp-UltraFast-9.5e):

| 线 | 铺开前 | 铺开后 |
|---|---|---|
| 1.21.1 | **FAILED**(崩溃 1;世界 yes、光影 loaded) | **STARTED / world yes / 光影 loaded / 崩溃 0** |
| 1.21.3 | **FAILED**(同上) | **STARTED / world yes / 光影 loaded / 崩溃 0** |
| 1.21.4 | STARTED / world yes / 光影 loaded / 崩溃 0 | 同上(铺开是惰性的) |
| 1.21.8 | STARTED / world yes / 光影 loaded / 崩溃 0 | 同上 |
| 1.21.6 | STARTED / world yes / **光影包没加载** | 同左(与本轮无关的独立缺陷) |
| 1.21.7 | STARTED / world yes / **光影包没加载** | 同左 |
| 1.21(菜单路) | join + 光影 loaded + 崩溃 0 | 同左 |

记两个细节:
* 1.21.1 / 1.21.3 修前的崩溃正是**同一批成员**(它们在铺开前没有这几条补桩),铺开后两线都变成 0 崩溃 —— 这也
  反过来说明"这套清单是本载荷家族的共性,不是 1.21 的特例";
* `BakedModel.useAmbientOcclusion(...)` 的桩仍然回答 false,即**那个模型不做环境光遮蔽**,这是套在整族上的
  **可见视觉代价**,最终仍应由"定向改写"替代(见上一节)。

**仍未通过的四条线与原因**(如实):1.21.6 / 1.21.7 的世界与光影都要么缺一:
`Failed to parse post chain at minecraft:post_effect/fxaa_of_2x.json | JsonSyntaxException: No key
fragment_shader / No key vertex_shader` —— 1.21.5 起后处理链格式从 `shaders/post`(vertex/fragment)换成
`post_effect`(vertex_shader/fragment_shader),而这条线的 OptiFine 构建仍是旧格式;要给它定性还欠一次对照
(同一份 OptiFine 放进同版本、**不带**我们加载器的 NeoForge 跑一次,看是否同样报错 —— 那才叫"OptiFine 与版本不兼容",
现在的说法只是"载荷与解析器不匹配")。

### 1.21.6 / 1.21.7 光影包加载失败**定位到具体字段**:是 OptiFine 那份 JSON 的 schema 落后于该版本,不是加载器

把三个版本的 OptiFine jar(1.21.4 可用、1.21.6/1.21.7 不可用)与我们的载荷 jar 逐个比较后,事实是:
**三个 jar 里那四个资源一模一样**(`assets/minecraft/post_effect/fxaa_of_{2,4}x.json` 各 620 字节、
`assets/minecraft/shaders/post/fxaa_of_{2,4}x.json` 各 719/443 字节),也就是说**资源没有被我们的流水线动过**,
问题在这份资源本身。把 620 字节那份打开看,它的 schema 是:

```json
{ "targets": { "swap": {} },
  "passes": [ { "program": "minecraft:post/fxaa_of_2x", "inputs": [ { "sampler_name": "In", "target": "minecraft:main" } ], "output": "swap" },
              { "program": "minecraft:post/blit",        "inputs": [ { "sampler_name": "In", "target": "swap" } ],         "output": "minecraft:main" } ] }
```

而 1.21.6/7 的解析器要的是**每个 pass 自带 `vertex_shader` / `fragment_shader`**(报错原文就是
`No key fragment_shader in MapLike[{"program":"minecraft:post/blit","inputs":[...],"output":"minecraft:main"}]`
以及同一份里的 `No key vertex_shader`)—— 也就是 1.21.5 到 1.21.6 之间**后处理链 schema 又改了一次**,而这条线的
OptiFine 预览构建仍按 1.21.5 的写法。**因此这不是我们加载器的缺陷**(同一份资源在原始 jar 里就是这样),
而是"载荷与运行期版本的 schema 不匹配"。

**候选修法(具体到可以照做,下一轮试)**:由我们的 jar 提供一份改写成新 schema 的覆盖资源
(`assets/minecraft/post_effect/fxaa_of_{2,4}x.json`,把每个 pass 的 `"program": X` 换成
`"vertex_shader": X, "fragment_shader": X`,其余 `inputs`/`output` 不动;两个 program 名
`minecraft:post/fxaa_of_2x`、`minecraft:post/blit` 都已在 OptiFine 的旧格式文件里被引用,说明它们存在)。
判定标准很简单:重建后在 1.21.6/1.21.7 上重跑建档光影,看 `[Shaders] Loaded shaderpack:` 是否出现、
`Failed to parse post chain` 是否消失。

#### 试了"由我们的资源覆盖那条 post chain",**没生效**(如实记,并给出下一个更硬的候选)

按上一节的候选修法,把改写成新 schema 的 `assets/minecraft/post_effect/fxaa_of_{2,4}x.json`(每个 pass 带
`vertex_shader`/`fragment_shader`)放进本分支的 `src/main/resources`,重建 1.21.6 / 1.21.7 并复跑:

* 资源**确实进了交付 jar**(`javap`/zip 检查:`assets/minecraft/post_effect/fxaa_of_2x.json`,724 字节);
* 但真机日志**一字未变**,报错里仍是**旧内容**(`{"program":"minecraft:post/blit", ...}`),
  也就是客户端**仍然读的是 OptiFine 那份**,我们的 mod 资源没有赢过它(载荷那份是以"被装进游戏 jar 的资源"身份存在的,
  优先级高于 mod 资源包)。**这条覆盖路线按现在的做法不成立**。

**下一个候选人(按硬度排序,下一轮试)**:把改写**直接做进交付的 OptiFine jar**(即 `build-jars`/
`OptifineJarFixer` 装配那一步,把 `assets/minecraft/post_effect/fxaa_of_{2,4}x.json` 替换成新 schema),
这样"谁在读"和"读哪份"就是同一个文件,不存在优先级问题;判定标准仍是 1.21.6 / 1.21.7 上
`[Shaders] Loaded shaderpack:` 是否出现、`Failed to parse post chain` 是否消失。
另:这两条线**世界始终是好的**(world yes、崩溃 0、4 个 region 文件),所以缺的只有这一处。

### 本轮收尾:十一条 ModLauncher 线的四项验收**全部重跑**(当前 jar),两条 stderr 差异如实挂账

`retest-all.ps1 -Only 1.21,1.21.1,1.21.3,1.21.4,1.21.6,1.21.7,1.21.8,1.20.1,1.20.2,1.20.4,1.20.6`
(`logs\retest-modlauncher-round154.txt`):

| 线 | 判定 | user | sound | 崩溃 | stderr | 与记录值 |
|---|---|---|---|---|---|---|
| 1.21 | STARTED | yes | yes | 0 | 46767 | **差异**(记录 14141) |
| 1.21.1 | STARTED | yes | yes | 0 | 0 | = 记录 |
| 1.21.3 | STARTED | yes | yes | 0 | 0 | = 记录 |
| 1.21.4 | STARTED | yes | yes | 0 | 0 | = 记录 |
| 1.21.6 | STARTED | yes | yes | 0 | 0 | = 记录 |
| 1.21.7 | STARTED | yes | yes | 0 | 0 | = 记录 |
| 1.21.8 | STARTED | yes | yes | 0 | 0 | = 记录 |
| 1.20.6 | STARTED | yes | yes | 0 | 17856 | 已知差异(§十三 已定位) |
| 1.20.4 | STARTED | yes | yes | 0 | 14481 | = 记录 |
| 1.20.2 | STARTED | yes | yes | 0 | 14631 | **差异 6 字节**(记录 14625) |
| 1.20.1 | STARTED | yes | yes | 0 | 0 | = 记录 |

两条差异都要在发布前结清或写清:
* **1.21:46767 vs 14141** —— 机制假设(本分支生成器与 `tools-classpath.txt` 里旧分支生成器产出的计划不同)未做 A/B;
* **1.20.2:14631 vs 14625** —— 只差 **6 字节**,同样是尚未解释的小差异(很可能也是计划/生成器版本差异带来的日志行差),
  这条线本轮也重建过计划,所以两件事可能同源。

### 工程目录搬到 `I:\mods`(环境变更,已复验),以及一次**被我改坏又还原**的文档编码事故

* 三个仓库(`OptifiNeoforge` / `-120x` / `-26x`)与 rig(`optifineoforge-test`)整体移到 **`I:\mods\...`**;
  `.gradle` 按选择**留在 C:** 不动。C: 释放约 6.3 GB(现 31.0 GB 空闲),I: 余 577.7 GB。
* 脚本/配置里的绝对路径已同步改写;**两个 worktree 用 `git worktree repair` 修好**
  (它们原来的 `.git` 指向 `C:\...\OptifiNeoforge\.git\worktrees\...`),`git worktree list` 现在三条线都在新位置、
  分支未变(`1.21.x` / `1.20.x` / `26.x`),工作区干净。
* **复验**:在新位置跑 `retest-all.ps1 -Only 1.20.4` ⇒ STARTED / user yes / sound yes / 崩溃 0 /
  stderr **14481 = 记录值**(natives 也从 `I:\mods\optifineoforge-test\natives` 解析);rig 的十个主要脚本
  PowerShell 解析 0 错误。
* **事故与还原(如实记)**:路径改写那一步用 `Get-Content -Raw` 读、`Set-Content -Encoding UTF8` 写,而
  PowerShell 5.1 的 `Get-Content` **默认按 ANSI(GBK)解码** —— 两份分支文档(`1.20.x` 的 `docs/MATRIX.md`、
  `26.x` 的 `docs/DEVELOPMENT.md`)被破坏,已用 git **精确还原并推送**(a4b08d7 / 3e64e89)。
  随后我为了"反向解码"又跑了一次启发式反转,而它的判据(连续两个 CJK 落在 `U+7E00..` 区间)会**误伤正常中文**
  (例如"载荷"两字都在该区间),于是把本仓库的 `README.md`、`docs/PLAN.md`、`docs/VERSIONING.md`、
  `docs/VERSIONS.md` 也改坏了(表现为整文件行行不同)。这四个文件**已从上一个提交精确还原**,本节是重新追加的。
  教训:**这台机器上读写 UTF-8 文本必须显式 `-Encoding UTF8`(读也一样),而且不要用启发式去"反转"编码**。

### 十五条线的四项验收**全部通过**(FML 10 四条线用当前分支头重建后重跑),1.21.6/1.21.7 暂缓并记下参考实现

`retest-all.ps1 -Group fml10 -Rebuild`(四条线都用当前分支头重建载荷后再跑,`logs\retest-fml10-round156.txt`):

| 线 | 判定 | user | sound | 崩溃 | stderr |
|---|---|---|---|---|---|
| 1.21.9 | STARTED | yes | yes | 0 | 0 = 记录值 |
| 1.21.10 | STARTED | yes | yes | 0 | 0 = 记录值 |
| 1.21.11 | STARTED | yes | yes | 0 | **107 = 记录值** |
| 26.1.2 | STARTED | yes | yes | 0 | **107 = 记录值** |

加上此前十一条 ModLauncher 线(§上),**十五条线现在四项验收全绿**(差异只剩 1.21 的 stderr 46767 vs 14141、
1.20.2 的 14631 vs 14625、1.20.6 的 17856,三件都已挂账)。

#### 1.21.6 / 1.21.7 的 post chain:**暂缓**,但修法已从本机 OptiFabric 项目读到(用户建议参考它)

本机 `C:\Users\kynar\IdeaProjects\OptiFabric` 的 `DEVELOPMENT.md` 里已经把这件事写过并且修过,要点(照抄其结论):

* OptiFine 的 FXAA 走游戏 post effect 系统;**1.21.6 起是新格式**;其自带的
  `assets/minecraft/post_effect/fxaa_of_{2,4}x.json` 里**第二个 pass(把 `swap` 拷回 `main`)是麻烦所在**;
* **1.21.9 起**游戏只提供 `assets/minecraft/shaders/post/blit.fsh`,**顶点阶段改用 `core/screenquad`**
  (原版 `post_effect/transparency.json` 就是这么写的),于是出现
  `Couldn't compile pipeline minecraft:fxaa_of_4x/1: vertex shader minecraft:post/blit was invalid`;
* 他们的修法不是"改写新格式"(我这轮试过、无效),而是两步:**①按 OptiFine 自己的 schema 补写
  `assets/minecraft/shaders/post/fxaa_of_{2,4}x.json`(只引用用户那份 OptiFine 里**已存在**的
  `post/fxaa_of_*.vsh/.fsh`);②把游戏那条 `assets/minecraft/post_effect/fxaa_of_{2,4}x.json` 移除**,
  让抗锯齿只由一个机制负责(`OptifinePostChainFixer`);
* 另有一条相关事实:1.21.8 起的构建**不再带老式链文件**,只带新形式的 `post_effect/fxaa_of_*.json`。

按用户指示,这两条的收尾**暂缓**,等其它版本(尤其 FXAA 量测与 FML 10 的建档光影)做完再回来照上面两步做。

#### FML 10 四条线的**建档 + 光影包**:全部没通过(实测),而且症状指向同一族 post chain 问题

紧接着上面的四项验收(它们都通过),对这四条线跑了同一个"建档 + MakeUp 光影包"测试
(`logs\save-shaders-fml10-round156.txt`):

| 线 | 判定 | sound | 世界 | 崩溃 | 光影包 | 备注 |
|---|---|---|---|---|---|---|
| 1.21.9 | **FAILED** | yes | yes(0 region) | **1** | **没加载** | `Resource not found: minecraft:shaders/post/fxaa_of_{2,4}x.json` |
| 1.21.10 | **FAILED** | yes | yes(0 region) | **1** | loaded | 同上那两条 resource not found |
| 1.21.11 | **FAILED** | yes | yes(0 region) | **1** | loaded | 同上 |
| 26.1.2 | STARTED | **NO** | **NO**(level.dat 未重写) | 0 | loaded | 同上 |

也就是说:**这四条线的四项验收是绿的(标题界面/声音/无崩溃),但"建档 + 光影"这一关一条都没过**
—— 世界能打开(1.21.9/10/11 都写了 level.dat、都到了 world),随后各崩一次,而且四条线都报
`[OptiFine] Resource not found: minecraft:shaders/post/fxaa_of_{2,4}x.json`:
**这与 1.21.6/1.21.7 那条 post chain 家族是同一件事**(OptiFabric 的 `DEVELOPMENT.md` 里写的
"1.21.8 起的构建不再带老式链文件、1.21.9 起顶点阶段改用 `core/screenquad`"正好对上),
所以**修法可以直接复用同一套**:补写 `assets/minecraft/shaders/post/fxaa_of_{2,4}x.json`(引用用户 OptiFine 里
已存在的 `post/fxaa_of_*.vsh/.fsh`)+ 移除 `assets/minecraft/post_effect/fxaa_of_{2,4}x.json`。
四条线各自的**那一次崩溃**还需要单独看崩溃报告定性(本轮只拿到"崩溃 1"这个计数与日志里的资源告警,
没有逐条读报告),下一轮第一件事就是读它们并归因。

**结论(如实)**:按发布口径,现在真正"绿"的是**十一条 ModLauncher 线**(四项验收 + 建档光影),
FML 10 四条线**只过了四项验收**;1.21.6 / 1.21.7 的光影包按用户指示暂缓。

### FML 10 那四次"建档+光影"失败里的崩溃:三条线**同一个缺陷**,已定性到类,并找到了这条构建路径的入口

读崩溃报告(1.21.9 / 1.21.10 / 1.21.11 三条线**逐字相同**,26.1.2 没有崩溃报告):

```
Description: Unexpected error
java.lang.ClassCastException: class net.neoforged.neoforge.network.handling.QueuedPacket$CustomPayload
    cannot be cast to class net.minecraft.network.PacketProcessor$...
  at net.minecraft.network.PacketProcessor.processQueuedPackets(PacketProcessor.java:77)
```

配合两个量测,机制就清楚了:

* 交付的 `jars-1.21.9\optifine-payload-fml10.jar` **含 3 个 `network/PacketProcessor*` 条目** ——
  也就是**载荷自己的 `PacketProcessor`(及内部类)被装了进去**,而 NeoForge 的网络补丁往队列里放的是它自己的
  `QueuedPacket$CustomPayload`,两边不是同一份编译产物 ⇒ 取出来强制转换时 ClassCastException;
* `build-fml10-payload.ps1` 里 keep 计划的来源是**唯一一条路径**:`PayloadDrift` 写
  `work\<mc>\plan\keep-runtime.proposed.txt`,脚本把它**原样复制**成载荷里的
  `optifineoforge/keep-runtime.txt`(第 103-110 行),而 **FML 10 这四条线没有任何 rig 侧的
  `keep-runtime-<mc>.txt`**(1.21.6/1.21.7/1.21.8 有,1.21.9/10/11/26.1.2 没有)—— 所以现在没有任何地方能塞进
  "这几个类整类保留运行期版本"这条决定。

**下一轮的修法(具体、与已有机制同形)**:
1. 给 `build-fml10-payload.ps1` 加一个可选的 `keep-additions-<mc>.txt`(照 `add-line.ps1` 里
   `stub-additions-<mc>.txt` 的写法:在复制 `keep-runtime.proposed.txt` 之后**追加**这些行);
2. 给 1.21.9 / 1.21.10 / 1.21.11 各写一行
   `net/minecraft/network/PacketProcessor	*`(如果 crash 换到内部类,再把
   `net/minecraft/network/PacketProcessor$QueuedPacket	*` 一起加上);
3. 用 `retest-all.ps1 -Group fml10 -Rebuild` 重建并重跑建档+光影。
另外四条线共同的 `Resource not found: minecraft:shaders/post/fxaa_of_{2,4}x.json` 与 1.21.6/7 的 post chain 是
同一件事,**按 OptiFabric 的两步修法一起做**(补写老式链文件 + 移除 `post_effect/` 那份)即可,不再单独排期。

### FML 10:构建链修通 + `PacketProcessor` 整类保留**生效**,崩溃随之推进一步(未解决)

* **构建链(已修)**:`build-fml10-payload.ps1` 第 53 行要求仓库里已编译的 fml10 源集,而编译**必须带该线的目标**:
  `gradlew -p <repo> compileJava -Pmc=<mc> -Pneoforge=<ver> -Pmountpoint=fml10 -Ptarget_java_version=21`
  (不带就是 15 个 `程序包 net.neoforged.neoforgespi.transformation 不存在`)。此前每次 `-Rebuild` 都因为
  `retest-all.ps1` 把构建器输出过滤成只看 `payload :` 而**把抛错吞掉**,一直用旧载荷 —— 这个过滤器必须改。
* **钩子(已生效)**:三条线现在都打印 `keep additions: 1 line(s)`,交付 jar 的 keep 计划里确实多了
  `net/minecraft/network/PacketProcessor	*`。
* **崩溃换了一步(这是本轮最有信息量的量测)**:原来的
  `ClassCastException: QueuedPacket$CustomPayload → PacketProcessor$…` **没了**,现在是
  ```
  java.lang.NoSuchMethodError: 'void net.minecraft.network.PacketProcessor.clientPreProcessPacket(
      net.minecraft.network.protocol.Packet)'
    at net.minecraft.network.PacketProcessor$ListenerAndPacket.handle(PacketProcessor.java:93)
  ```
  (`crash-2026-09-23_00.20.16-client.txt`)。也就是说**整类保留运行期版本之后,运行期自己的
  `ListenerAndPacket.handle` 需要 NeoForge 给这个类加过的成员 `clientPreProcessPacket(Packet)`,而它不在**。
  修法方向有两条,下一轮二选一实测:①把这条成员按已有机制补回来(成员级恢复/补桩,注意它是"每个包都调用"的路径,
  语义要正确);②**收窄保留范围**——只把不兼容的内部类(`PacketProcessor$QueuedPacket`)整类保留,让
  `PacketProcessor` 本体仍走载荷(这样 NeoForge 的注入与 OptiFine 的补丁都在同一份上)。
* 三条线(1.21.9 / 1.21.10 / 1.21.11)的建档+光影**仍 FAILED**,各崩 1 次(上面这条错误);
  它们也仍报 `Resource not found: minecraft:shaders/post/fxaa_of_{2,4}x.json`,与 1.21.6/7 同源,
  按 OptiFabric 那两步(补写老式链文件 + 移除 `post_effect/` 那份)一并处理。

### FML 10 的 `PacketProcessor`:两个方向的实测结果与**下一步的机制缺口**

两条路都量过(三条线一致):

| keep 方式 | 结果 |
|---|---|
| 整类保留 `PacketProcessor` | `ClassCastException: QueuedPacket$CustomPayload → PacketProcessor$…` **消失**,换成 `NoSuchMethodError: PacketProcessor.clientPreProcessPacket(Packet)` at `PacketProcessor$ListenerAndPacket.handle:93` |
| 只保留 `PacketProcessor$QueuedPacket` | **没用**:`ClassCastException` 原样回来(`crash-2026-09-23_00.45.57`,同一个 `processQueuedPackets:77`) |

也就是说:**整类保留是必须的**(载荷那份 `PacketProcessor` 的处理循环与 NeoForge 的队列对象不兼容),
而整类保留之后缺的是 **NeoForge 在加载期给这个类加过的成员** `clientPreProcessPacket(Packet)`。查过三份 jar:

```
neoforge-21.9.16-beta-client.jar  有 PacketProcessor,但没有 clientPreProcessPacket
neoforge-21.9.16-beta-universal.jar 根本没有这个类
work\1.21.9\runtime-1.21.9.jar    有 PacketProcessor,也没有 clientPreProcessPacket
```
⇒ 这条成员是 **NeoForge 自己的运行期转换器加上去的**;我们把 jar 里那份整类装进去,就等于**把它冲掉了**
(或者我们的处理器跑在 NeoForge 之后)。这正是 ml11 那条加载器里 `stubMissing()`(`optifineoforge/stubs.txt`)存在的理由,
而**FML 10 的处理器没有这个机制** —— 它只认 `keep-runtime.txt` / `member-restores.txt`(+ donors)/ `reparent.txt`
(`OptifinePayloadClassProcessor` 第 62/460/796/1209 行)。

**下一步(已具体到机制)**:
1. 把 ml11 的 **stub 机制移植进 FML 10 处理器**(读 `/optifineoforge/stubs.txt`,对"被整类保留的类"补上列出的成员);
2. 给这三条线的载荷加一行 `net/minecraft/network/PacketProcessor	clientPreProcessPacket	(Lnet/minecraft/network/protocol/Packet;)V`,
   并**如实标注语义风险**:这是"每个包都经过"的路径,空实现会让 NeoForge 的客户端自定义包分发被跳过 —— 先用它把线跑起来量测,
   最终修法是让 NeoForge 的那条成员不被冲掉(处理器顺序或"在保留前先取转换后的字节")。
3. 三条线仍共有的 `Resource not found: minecraft:shaders/post/fxaa_of_{2,4}x.json` 与 1.21.6/7 同源,
   按 OptiFabric 两步(补写老式链文件 + 移除 `post_effect/` 那份)一起修。

#### `PacketProcessor` 的根因量到**字段类型**这一层,以及"保留路径本身不覆盖"的事实

`javap` 对照(1.21.9)给出不兼容的确切位置 —— 不是方法体,是**字段类型**:

| | `packetsToBeHandled` 的类型 |
|---|---|
| 运行期(NeoForge) | `java.util.Queue<net.neoforged.neoforge.network.handling.QueuedPacket>` |
| 载荷(OptiFine) | `java.util.Queue<net.minecraft.network.PacketProcessor$ListenerAndPacket<?>>` |

NeoForge 自己的 `scheduleIfPossible` 往队列里放 `QueuedPacket`,而 OptiFine 的 `processQueuedPackets`
按 `ListenerAndPacket` 取出来 ⇒ 必然 `ClassCastException`。**所以整类必须来自同一侧,且必须是运行期那一侧**
(调用方是 NeoForge 的网络栈),这解释了为什么"只保留内部类"会失败。

同时看到一件重要事实:`OptifinePayloadClassProcessor.transform()` 的保留分支(第 125-130 行)是
**直接 `return`、不覆盖 `node`** —— 也就是"保留"= 不替换,保留的是**流水线交到我们手上的那份**。
那么整类保留时 `clientPreProcessPacket` 仍缺失,只能说明 **NeoForge 自己那条成员在这个流水线里没有落到这个类上**
(顺序或条件问题),而不是我们把它冲掉。这就是下面那条工作项的依据。

### FML 10:把 stub 机制移植进处理器(代码+装配+逐线条目),崩溃**连推两步**,又撞上已在 ml11 修过的"外壳种类"

本轮动手做了上一轮定下的机制移植(FML 10 处理器原本只认 keep-runtime / member-restores+donors / reparent):

1. `OptifinePayloadClassProcessor` 新增 `stubs()`(读 `/optifineoforge/stubs.txt`,格式 `owner name desc [static]`)
   与 `stubMissing(node)`,并在**保留分支**(第 125 行那段"直接 return"之前)调用它 —— 被保留的类正是
   NeoForge 运行期注入成员丢失的地方;
2. `build-fml10-payload.ps1` 把 `stub-additions-<Line>.txt` 装成载荷里的 `optifineoforge/stubs.txt`
   (与 keep 追加同形,三条线都打印 `stub list: N member(s)`);
3. 三条线加 `net/minecraft/network/PacketProcessor	clientPreProcessPacket	(Lnet/minecraft/network/protocol/Packet;)V	static`。

**实测推进(逐条都是真机崩溃报告)**:

| 阶段 | 结果 |
|---|---|
| 整类保留、无 stub | `NoSuchMethodError: PacketProcessor.clientPreProcessPacket(Packet)` at `ListenerAndPacket.handle:93` |
| 补了 **实例**桩 | 桩确实生效(日志 `stubbed … clientPreProcessPacket`),但调用点是 `invokestatic` ⇒ `IncompatibleClassChangeError: Expected static method …` |
| 补了 **static** 桩(本轮) | 这一处**解决**了,而且世界明显往前走:**首次写出 2 个 region 文件**(此前一直 0) |
| 现在的下一步 | `IncompatibleClassChangeError: Method 'net.minecraftforge.client.extensions.common.IClientItemExtensions …'` at `ItemInHandRenderer.renderArmWithItem:536` |

最后这条**不是新问题**:它就是 ml11 线早就修过的"**Forge 外壳的 kind 要按调用点决定**"(`af0b296` / `345ef82`:
`IClientItemExtensions` 在载荷里是**接口**,而生成出来的壳是**类**,于是 `invokeinterface` 撞上类)。
⇒ FML 10 的载荷需要**用当前 `ForgeApiShims` 重新生成 Forge 壳**(kind 由调用点决定),这是下一轮第一件事。

另外三条线仍共有 `Resource not found: minecraft:shaders/post/fxaa_of_{2,4}x.json`(与 1.21.6/7 同源),
按 OptiFabric 两步(补写老式链文件 + 移除 `post_effect/` 那份)一起修。

#### FML 10 的 `IClientItemExtensions`:**外壳 kind 的实锤**(与 ml11 已修的那件同源)

`javap` 逐字节对照(1.21.9):

```
交付的壳: jars-1.21.9\optifine-own-classes.jar
  net/minecraftforge/client/extensions/common/IClientItemExtensions.class (876 字节)
  public class ...IClientItemExtensions { public static ... DUMMY; public ...IClientItemExtensions(); }   <- 是"类"

调用点: 载荷 ItemInHandRenderer
  598: invokestatic  ... IClientItemExtensions.of:(Lnet/minecraft/world/item/ItemStack;)...    <- 静态工厂
  619: invokeinterface ... IClientItemExtensions.applyForgeHandTransform:(...)Z                <- 接口调用
```

⇒ 壳必须是**接口**,而 FML 10 三条线交付的是**类**(`DUMMY` 字段 + 构造器,是旧生成器的产物),
于是 `IncompatibleClassChangeError`。这与 ml11 线早已修过的 `af0b296`/`345ef82`(**外壳 kind 由调用点决定**)是同一件,
只是 FML 10 这条路径的壳在 **`optifine-own-classes.jar`**(每条线一个,由 `build-fml10-own-classes.ps1` 生成),
而不是 ml11 的 `stubs/` 目录 —— 所以那次修复没有覆盖到这里。

**下一轮第一件事**:用当前分支的 `ForgeApiShims`(kind 由调用点决定)重新生成三条线的 `optifine-own-classes.jar`,
重建载荷并重跑建档+光影;26.1.2 的同类壳(`jars-26.1.2\optifine-own-classes.jar`)也一并复查。

### FML 10 三条线:三个真实缺陷修掉,1.21.10 / 1.21.11 全绿;1.21.9 只剩光影包不被接受

四道验收(GROUP=fml10,`-SkipRebuild`)在修完后:1.21.9 = STARTED/声音 yes/0 崩溃/stderr 0(记录值 0)、
1.21.10 = STARTED/yes/0/0(记录 0)、1.21.11 = STARTED/yes/0/107(记录 107)。
建档 + 光影(MakeUp-UltraFast-9.5e.zip,300 s):

| 线 | 四道验收 | 建档(region/level.dat) | 光影包 | 崩溃 |
|---|---|---|---|---|
| 1.21.9 | STARTED, 0, 0 | 4 个 region,level.dat 已重写 | **未加载**(见下) | 0 |
| 1.21.10 | STARTED, 0, 0 | 6 个 region,已重写 | `Loaded shaderpack: MakeUp-UltraFast-9.5e.zip` | 0 |
| 1.21.11 | STARTED, 0, 107 | 9 个 region,已重写 | `Loaded shaderpack: MakeUp-UltraFast-9.5e.zip` | 0 |

#### 缺陷一:Forge 外壳的**种类**是旧的(三条线都中)

`javap` 实据(1.21.9,其余两条同):交付的
`jars-1.21.9\optifine-own-classes.jar` 里
`net/minecraftforge/client/extensions/common/IClientItemExtensions.class`(876 字节,2026-09-19)是
**类**(有 `DUMMY` 字段和公开构造器),而载荷 `ItemInHandRenderer` 的调用点是
`invokestatic IClientItemExtensions.of(ItemStack)` + `invokeinterface applyForgeHandTransform(...)`
 -> `IncompatibleClassChangeError`。生成器本身在 `345ef82` 已按调用点决定种类,但
`build-fml10-own-classes.ps1` **只在 `work\<line>\stubs` 为空时**才重新生成,于是三条线一直发着旧产物。
重新生成后:1.21.9/10/11 分别 90/99/94 个外壳(原 87/95/90,多出来的正是一接口一份的 `$Noop`),
`javap` 显示三条线现在都是 `public interface ... { static of(ItemStack); abstract applyForgeHandTransform(...) }`。
修:该脚本改成**按新鲜度**判断(生成器类比产物新就重生成),`retest-all.ps1` 也补上 own-classes 的重建、
并把重建失败写成 `NO-RESULT` 行(以前 `Select-String 'payload :'` 会把构建器的 throw 吞掉)。

#### 缺陷二:NeoForge 的生命周期钩子被 OptiFine 的 `IntegratedServer` 顶掉(三条线都中)

`Exception ticking world` / `IllegalStateException: Cannot get config value before config is loaded`
(`NeoForgeServerConfig.removeErroringEntities`),`Suppressed Exceptions: ~~NONE~~`。
`javap -c` 对照:运行时的 `IntegratedServer` 调
`ServerLifecycleHooks.handleServerAboutToStart/Starting`,**服务端 config 就是在那里加载的**;
载荷发的是 OptiFine 自己的 `srg/net/minecraft/client/server/IntegratedServer.class`,而 `keep-runtime.txt`
没有它 —— 于是 `config\neoforge-server.toml` 从不生成、config 值永远未加载,第一条抛异常的生物 tick
就死在 `Level.guardEntityTick` 自己的错误分支里,崩溃报告因此写的是 config 而不是那只生物(1.21.8 同一个病,
当时的修法也是整类保留)。修:三条线 `keep-additions-*.txt` 加
`net/minecraft/client/server/IntegratedServer<TAB>*`。修后 `neoforge-server.toml` 三条线都出现、该崩溃消失。

#### 缺陷三:1.21.11 把 `ResourceLocation` 改名成 `Identifier`,而我们的处理器写死了旧名(1.21.11 独有)

修完缺陷二后 1.21.11 走到渲染,报
`NoSuchMethodError: 'net.minecraft.resources.ResourceLocation net.minecraft.core.Registry.getKey(java.lang.Object)'`
at `ParticleEngine.makeParticle:74`。根因在我们的 `OptifinePayloadClassProcessor.repairParticleProviderLookup`:
它把 `Registry.getId` 改写成 `getKey` 时**写死**了返回类型
`getId.desc = "(Ljava/lang/Object;)Lnet/minecraft/resources/ResourceLocation;"`。
实测:1.21.9/1.21.10 运行时只有 `ResourceLocation`,1.21.11 只有 `Identifier`(该线 mappings 文件里
`Identifier` 出现 2377 次、`ResourceLocation` 0 次);OptiFine 自己的 1.21.11 jar 也用 `Identifier`(99 次)。
同一个类里 `repairLegacyTagCreator` 早就踩过同一个坑并改成"找而不是写",这里漏了。
修:名字不再写死,而是由 `build-fml10-payload.ps1` 从 `work\<line>\runtime-<line>.jar` 读出
(两个名字必须恰好存在一个,否则构建失败),写进载荷资源 `optifineoforge/runtime-location.txt`,
处理器读它。修后该线日志出现
`OptiFine payload: this runtime's resource location is net.minecraft.resources.Identifier` 与
`ParticleEngine.makeParticle reads the particle provider through the runtime's Map keyed by resource location`,
粒子崩溃消失。
顺带修掉一个同族陷阱:`build-fml10-payload.ps1` 里 `foreach ($line in ...)` 因为 PowerShell 变量名大小写不敏感,
会覆盖脚本自己的 `$Line` 参数(实测报错路径变成 `work\1.21.11\runtime-net\minecraft\client\server\IntegratedServer\t*.jar`),
循环变量已改名。

#### 缺陷四:1.21.11 的 `ModelBlockRenderer$1` 开关表为 null(三条线都补上)

修完缺陷三、世界真正开始渲染后:
`NullPointerException: Cannot load from int array because "...ModelBlockRenderer$1.$SwitchMap$net$minecraft$util$TriState" is null`
at `ModelBlockRenderer.tesselateWithAO:143`。这与 1.20.4 起 ml11 各线早已用整类保留修好的是同一个类同一个字段
(其 `<clinit>` 不在任何 donor 里,字段被还原却没有初始化器);FML 10 线一直没带这条。修:三条线
`keep-additions-*.txt` 加 `net/minecraft/client/renderer/block/ModelBlockRenderer$1<TAB>*`。

#### 未解决:1.21.9 的光影包不被接受(只此一条线)

日志只有成对的 `[Shaders] Load shaders configuration.` -> `[Shaders] No shaderpack loaded.`,既没有
`Antialiasing is enabled` 也没有 `Fabulous Graphics`。已**证伪**的假设,按实测逐条记下:
* 不是"包没装"或"路径不对":`<profile>\shaderpacks\MakeUp-UltraFast-9.5e.zip`(400417 字节)存在;
* 不是 `shaderPacksDir` 指针错:`shadersConfig` 与 `shaderPacksDir` 都来自
  `Minecraft.getInstance().gameDirectory`(`Shaders.<clinit>` 里相邻两条 `new File(...)`),
  而该目录的 `options.txt`/`optionsof.txt` 确实被这个客户端写过(02:06:01);
* 不是"配置文件没读到"的简单情形:`loadConfig()` 只在 `!configFile.exists()` 时 `storeConfig()`,
  而日志里**没有** `Save shaders configuration.`,文件也没被覆盖(126B/写入时刻保持);
* 不是属性名不同:`EnumShaderOption.<clinit>` 在 1.21.9 与 1.21.10 上的键/默认值逐条相同;
* 也不是 base dir 的问题:把 `shaderPack` 写成**绝对路径**再启动,仍然是 `No shaderpack loaded.`
  (`getShaderPack` 对绝对子路径会忽略父目录,若名字真的传到就必然成功)。
结论方向:`getShaderPack` 收到的是**空名字**,即 1.21.9 这条线上 `loadShaderPack()` 看到的 `shadersConfig`
里 `shaderPack` 还是 `loadConfig()` 写进去的空默认值(`ldc ""`),尽管文件存在且被读入。
下一步最省的做法是给这条线做一次**临时探针**(在处理器安装 `net/optifine/shaders/Shaders` 时打一行
`configFile/shaderPacksDir/shaderPack 值`),测完即撤;这条线达标前不发 release。

#### 更正:1.21.9 那条"绝对路径"实验本身无效(`Properties` 会吃掉反斜杠)

上一条把"绝对路径仍不被接受"当成"base dir 不是问题"的实据,这是**错的**,记录如下以免下次重犯:
`Shaders.loadConfig()` 用 `shadersConfig.load(new FileReader(configFile))`,即 `java.util.Properties`
的解析规则,而它把 `\` 当转义符 —— 写进去的
`shaderPack=I:\mods\optifineoforge-test\game\neoforge-21.9.16-beta\shaderpacks\MakeUp-UltraFast-9.5e.zip`
读回来会变成 `I:modsoptifineoforge-testgame...`(每个 `\x` 被吞掉一个字符,未知转义直接丢反斜杠),
于是 `isFile()` 必然为假 -> `getShaderPack` 返回 null -> `No shaderpack loaded.`。
也就是说这次运行只证明了"名字不是原样传进去的",**没有**排除 `shaderPacksDir` 指向别处。
`Properties` 同时是 `optionsshaders.txt` 里 `shaderPack` 值的真实解析器,所以下一个探针应当直接打印
`Shaders.configFile`、`Shaders.shaderPacksDir`、`shaderPacksDir.exists()` 和
`shadersConfig.getProperty("shaderPack","<absent>")`,而不是再靠改文件猜。

#### 1.21.9 光影包异常的**在机内实测**(临时探针),以及被证伪的假设

装置侧新增一次性探针 `optifineoforge-test\diagnostic\...\ShaderStateProbe.java`(只装在 `diagnostic-out`,
不进 mod;用 `launch-fml10.ps1 -MainClass ...ShaderStateProbe` 启动),在**客户端进程内**读/调用 OptiFine 的
`Shaders`。实测结果,逐条:

1. `Config.isAntialiasing()=false`、`Config.getAntialiasingLevel()=0`、`Config.isGraphicsFabulous()=false`
   —— `loadShaderPack()` 里那条"跳过"分支(会 `shaderPackLoaded=false` 且只打 `Antialiasing is enabled` /
   `Fabulous Graphics` 两句话)**不成立**,日志里也确实没有这两句。
2. `<profile>\optionsshaders.txt` 与 1.21.10 的那份**逐字节相同**(59B),并且在同一个 JVM 里用
   **OptiFine 自己的** `net.optifine.util.PropertiesOrdered.load(FileReader)` 与平台 `Properties.load`
   都能读出 `shaderPack=MakeUp-UltraFast-9.5e.zip`(size=2)—— 文件和读取器都没问题。
3. `Shaders.configFile` 与 `Shaders.shaderPacksDir` 实测为**正确的绝对路径**且都存在;
   `shaderpacks\MakeUp-UltraFast-9.5e.zip` 在(400417B,zip 可开,357 项,含 `shaders/`),目录里 6 个包;
   并且 `Shaders.getShaderPack("MakeUp-UltraFast-9.5e.zip")` 直接返回 `ShaderPackZip`、
   `getShaderPack("(debug)")` 返回 `ShaderPackDefault` —— **查找路径本身是好的**。
4. 事后直接调 `Shaders.loadShaderPack()`、以及再调一次 `Shaders.loadConfig()`,**都仍然是**
   `No shaderpack loaded.` —— 因此不是"启动时机/文件还没就绪",而是**每次都会复现的状态**。
5. 运行中的 `Shaders` 来自 `mods/optifine-own-classes.jar`,其字节与载荷里 `srg/` 那份**完全相同**
   (sha256 `12119f0a…`),排除"两份不同副本、静态状态分裂"。
6. **探针自身的坑,记下来**:在游戏起来之前碰到 `Shaders` 会让它自己的 `<clinit>` 抛
   `ExceptionInInitializerError`(它解引用 `Minecraft.getInstance()`),此后该类彻底不可用
   (`NoClassDefFoundError: Could not initialize class`),整轮不会再有光影日志 —— 所以探针必须先等到
   `Sound engine started` 再碰它。
7. 仍未解开的矛盾:`loadShaderPack()` 里读到的名字看起来是空的(否则按 3 必然加载),而 `shadersConfig`
   里明明有值、`configFile` 也存在。**下一步不能再靠反射**(`optifine` 模块已拒绝私有成员的
   `setAccessible`),要在**字节码层**给 own-classes 里那份 `Shaders.loadShaderPack` 临时插一行打印
   (我们的管线本来就会改写 OptiFine 的类),把 `name`、`shaderPacksDir`、`configFile.exists()` 三个值
   打在它自己的调用点上;测完即撤。这条线达标前不发 release。

### 1.21.9 光影包之谜解开:是 **OptiFine 自己那个预览版的字节码缺陷**,修在我们管线里

上一轮把它收敛到"`loadShaderPack()` 里拿到的名字像是空的"。这一轮用**字节码插桩**(不是反射:`optifine` 模块
拒绝私有成员)把最后一个环节打了出来,并定位到根因:

**根因(在 OptiFine 的类里,不在我们的代码里)**:`Shaders.loadShaderPack()` 在 1.21.9 上是

```
170: iload_2                                  // skip (antialiasing / fabulous)
171: ifne 195                                 // skip != 0 -> 直接去"清空"
174: aload_3; 175: invokestatic getShaderPack // 查包
178: putstatic shaderPack
181-192: shaderPackLoaded = shaderPack != null   // 查包结果写进字段
195: iconst_0                                 // ← 没有 goto 跳过这里!
196: putstatic shaderPackLoaded               // ← 无条件再清成 false
199: getstatic shaderPackLoaded; 202: ifeq 219
219: "No shaderpack loaded."  ... 225-232: shaderPack = new ShaderPackNone()
```

`if (skip != 0)` 的 else 块**丢了那条跳过它的跳转**,于是查包结果立刻被自己抹掉。1.21.10(J7 pre11)与
1.21.11(J9)在同一处的字节码是 `192: putstatic` 之后**直接** `195: getstatic`(else 块与它的跳转根本不存在),
所以只有 1.21.9 中招。optifine.net 上 1.21.9 **只有 J7 pre1/pre2 两个构建**(都是 01.10.2025),没有修好的版本
可以换,所以修必须由我们做。

**在机内实测到的证据链**(都属于"每一次都正确,却仍然不加载"):
* `configFile` / `shaderPacksDir` 是正确的绝对路径且都存在;`shaderPack` 属性是
  `MakeUp-UltraFast-9.5e.zip`(25 字符,`chars=[M,a,k,e,...,p]`,无尾随空格),
  `endsWith(".zip")=true`,`new File(shaderPacksDir, name).isFile()=true`;
* `getShaderPack` 确实被调用,`ShaderPackZip.<init>` 确实进入,方法返回值打印为
  `net.optifine.shaders.ShaderPackZip@...`(非 null);
* `Config.isAntialiasing()=false`、`Config.isGraphicsFabulous()=false` → "跳过"分支**证伪**;
* 运行中的 `Shaders` 来自 `mods/optifine-own-classes.jar`,与载荷 `srg/` 那份 sha256 相同(排除副本分裂);
* 事后直调 `loadShaderPack()`、再调一次 `loadConfig()`,**仍然** `No shaderpack loaded.`(复现式)。

**修法**(`src/main/java/.../optifine/ShadersPackLoadedRepair.java`,由 `OptifinePipeline.split` 在把 OptiFine
自己的类写进 classpath jar 时应用,因此**每次新准备的行都会带上**):删掉那对多余的
`iconst_0; putstatic shaderPackLoaded`,并把那条 `ifne` 从"清空"改指到 **"No shaderpack loaded." 分支**
(它本来就是分支目标、自带 stack map frame)。这样 `skip != 0` 仍然走"不加载"路径,`skip == 0` 则保留查包结果。
修后 1.21.9 的类:`171: ifne 215`,清空那两条不见了,`192: putstatic shaderPackLoaded` → `195: getstatic`。

**两个被自己证伪的中间方案**(记下来免得重犯):
* 在查包结果后插一条 `GOTO` 跳到"清空之后"的指令 → `VerifyError: Operand stack underflow` 之后是
  帧不匹配:那条指令在原代码里只靠 fall-through 到达,**没有声明 frame**,跳到它必然验证失败;
* 匹配序列时用"相邻指令"判断 → 找不到:清空那两条前面**夹着一个 LabelNode**(它就是 `ifne` 的目标),
  必须跳过 ASM 的伪指令(label/line/frame)后再比较。
* 另外,探针在游戏起来之前碰 `Shaders` 会让它 `<clinit>` 抛 `ExceptionInInitializerError` 并永久废掉该类。

**实测结果(2026-09-24,单行启动)**:1.21.9 日志出现
`[Shaders] Loaded shaderpack: MakeUp-UltraFast-9.5e.zip` 与 `[OptiFine] [Shaders] Worlds: -1, 0, 1`,
`VERDICT: STARTED`、`Sound engine started`、**0 崩溃**、**stderr 0 字节**(无 VerifyError)。
三条 FML 10 线的完整"建档 + 光影"闸门正在重跑,结果记在下一节。

#### 修后闸门实测(2026-09-24,`run-save-shaders-all.ps1 -Only 1.21.9,1.21.10,1.21.11 -Pack MakeUp-UltraFast-9.5e.zip -Seconds 300`)

| 线 | 四道验收 | 建档 | 光影包 | 崩溃 |
|---|---|---|---|---|
| 1.21.9 | STARTED / user yes / sound yes / stderr 0(记录 0) | 4 region + level.dat 重写 | `Loaded shaderpack: MakeUp-UltraFast-9.5e.zip` | 0 |
| 1.21.10 | STARTED / yes / yes / 0(记录 0) | 6 region + 重写 | 同上 | 0 |
| 1.21.11 | STARTED / yes / yes / 107(记录 107) | 9 region + 重写 | 同上 | 0 |

**三条 FML 10 线(1.21.9 / 1.21.10 / 1.21.11)首次全部通过"启动验收 + 建档 + 光影包"**;1.21.9 是这一轮才通的。
到此刻为止全线状态:**12 条线绿**(1.20.1、1.20.2、1.20.4、1.20.6、1.21、1.21.1、1.21.3、1.21.4、1.21.8、
1.21.9、1.21.10、1.21.11);1.21.6 / 1.21.7 仍是"世界可以、光影包不加载"(后处理链 schema,用户已同意暂缓);
26.1.2 仍是 sound NO + 世界未启动。三条线的光影日志都带
`Resource not found: minecraft:shaders/post/fxaa_of_{2,4}x.json` —— 后处理链告警未解,与 FXAA 量测一起列在待办。
release 仍未发布。

#### FXAA 真的被测到了,而且失败点是明确的:`minecraft:post/blit` 顶点着色器不存在

装置侧先补了一个缺口:`run-save-shaders-all.ps1` **以前根本没有 -ShaderAaLevel 参数**,也就是"真正的 FXAA"
(optionsshaders.txt 的 `antialiasingLevel`,GUI 标签就是 "FXAA 2x"/"FXAA 4x",由 `GameRenderer.setFxaaShader`
应用)在整条流水线上**从来没有被请求过**——之前所有 `-AaLevel` 跑的都是 optionsof.txt 的多重采样,不是 FXAA。
现在有了该参数(默认 0,并写进日志 tag),并且明确:FXAA 与 ofAaLevel 互斥,要测 FXAA 必须 `-AaLevel 0`。

实测(FML 10 三条线,MakeUp-UltraFast-9.5e.zip,`-AaLevel 0 -ShaderAaLevel 2`,300 s):

* 三条线都是 `STARTED / sound yes / 建档成功(4、6、9 个 region,level.dat 重写)/ 0 崩溃`;
* 三条线都在请求 FXAA 时打出**同一对**新日志:

```
[Render thread/ERROR] [com.mojang.blaze3d.opengl.GlDevice/]: Couldn't find source for VERTEX shader (minecraft:post/blit)
[Render thread/ERROR] [com.mojang.blaze3d.opengl.GlDevice/]: Couldn't compile pipeline minecraft:fxaa_of_2x/1: vertex shader minecraft:post/blit was invalid
```

也就是说:**FXAA 确实被启用了**(游戏按 `post_effect/fxaa_of_2x` 建了后处理管线,说明 OptiFine 那份
`post_effect/fxaa_of_2x.json` 被读到了),但它引用的顶点着色器 `minecraft:post/blit` 在这条线上**没有对应的
着色器源码**,于是管线编译失败 -> FXAA 静默不生效(不崩溃)。启动期那两条
`Resource not found: minecraft:shaders/post/fxaa_of_{2,4}x.json` 是 OptiFine 仍在按旧路径探测自己的后处理链
(1.21.6+ 起原版把后处理从 `shaders/post/` 迁到 `post_effect/`),与这个编译失败是同一件事的两面。

下一步(已定,未做):按这一线**实际带有的**顶点阶段改写/补齐后处理链——要么随载荷提供缺失的
`assets/minecraft/shaders/core/blit.vsh`(该线期望的 uniforms/attributes),要么把
`post_effect/fxaa_of_{2,4}x.json` 改成引用该线确实存在的顶点阶段(OptiFabric 参考实现的两步做法:
写自己的 `shaders/post/fxaa_of_*.json` 指向包内 `post/fxaa_of_*.vsh/.fsh`,并移除游戏自带的
`post_effect/fxaa_of_*.json`)。三条线(以及同样"世界可以、光影包不加载"的 1.21.6/1.21.7)共用这一条修法。

结论:**FXAA 目前不是绿的**,因此 release 不发。四条线(1.21.6、1.21.7、1.21.9、1.21.10、1.21.11)的 FXAA
都卡在这一个原因上。

#### FXAA 修好了:OptiFine 的 FXAA 后处理链引用了这条线不存在的顶点阶段

上一条定位到的编译失败(`Couldn't find source for VERTEX shader (minecraft:post/blit)`)根因确认,并在**管线里**修掉:

* 1.21.9 的原版客户端只有 `assets/minecraft/shaders/post/*.fsh`(fragment),`post/` 下**唯一的顶点阶段是
  `rotscale.vsh`**——没有 `post/blit.vsh`;原版自己的后处理 JSON(`post_effect/blur.json`、
  `entity_outline.json`)**每一趟都用 `minecraft:core/screenquad` 当顶点阶段**,其中 entity_outline 的 blit 趟
  正好就是 `core/screenquad` + `post/blit` + `BlitConfig`。
* OptiFine 自己的 `assets/minecraft/post_effect/fxaa_of_{2,4}x.json` 是两趟:第一趟
  `post/fxaa_of_{2,4}x`(vsh+fsh,OptiFine 自带、能编译),第二趟 blit 却把**顶点阶段**写成
  `minecraft:post/blit` —— 这个 `.vsh` 在 1.21.9/1.21.10 上不存在,于是管线编译失败、FXAA 静默失效(不崩溃)。
* 实测三条线的**用户 OptiFine 原始 jar**里 `post/blit` 出现次数:1.21.9 = **2**(顶点+片元,有缺陷)、
  1.21.10 = **2**(同样有缺陷)、1.21.11 = **1**(J9 已经自己修好,只留片元)。

**修法**(`src/main/java/.../optifine/FxaaPostChainRepair.java`,同样由 `OptifinePipeline.split` 在写资源时应用):
把 blit 趟的 `"vertex_shader": "minecraft:post/blit",` 改成 `"minecraft:core/screenquad",` ——
也就是变成与原版自己那趟完全相同的组合;FXAA 那趟不动(它命名的是 OptiFine 自带、能编译的顶点阶段)。
该字符串在每个文件里只出现一次,且只在 blit 趟,所以不会误改。三条线重新生成后的交付 jar 里都是
`post/fxaa_of_{2,4}x`(vsh+fsh)+ `core/screenquad` + `post/blit`。

**实测(FXAA 2x,`-AaLevel 0 -ShaderAaLevel 2`,MakeUp-UltraFast-9.5e.zip)**:

| 线 | 管线修复 | 四道验收/建档 | FXAA 管线编译错误 | 崩溃 |
|---|---|---|---|---|
| 1.21.9 | 两条链都修 | STARTED/sound/4 region | **0** | 0 |
| 1.21.10 | 两条链都修 | STARTED/sound/6 region | **0** | 0 |
| 1.21.11 | 报告"无需修"(J9 本身已对) | STARTED/sound/9 region | **0** | 0 |

修前同样的 FXAA 运行会打出 `Couldn't compile pipeline minecraft:fxaa_of_2x/1: vertex shader
minecraft:post/blit was invalid`,修后该行**完全消失**,三条线光影包仍正常加载(`Loaded shaderpack`)、
stderr 0 字节、0 崩溃。启动期剩下的
`Resource not found: minecraft:shaders/post/fxaa_of_{2,4}x.json` 是 OptiFine 仍在探测**旧路径**
(1.21.6+ 起原版把后处理搬到 `post_effect/`),属良性:真正生效的是新路径那条链,而它现在能编译。

至此 **FML 10 三条线的光影与 FXAA 都通了**;1.21.6 / 1.21.7(用户已同意暂缓)若要继续,同一条修法可以直接用上。
release 仍未发布:26.1.2(sound NO + 世界未启动)、1.21.6/1.21.7 的后处理链、以及逐线 FXAA 的像素级 A/B 量测仍在待办。

#### FXAA 的像素级验证:装置已能跑 FML 10,但这一轮的 A/B 还没落地

`run-fxaa-capture.ps1`(钉住存档 -> 启动客户端 -> 定时抓帧 -> 只停自己启动的那个进程)此前**只支持 ModLauncher
线**:它按 `jars-<mc>\OptifiNeoforge-1.0.0+mc<mc>-registered.jar` + `optifine-*.jar` 选 모드,并用 `launch.ps1`。
FML 10 线的两个 jar 名字完全不同(载荷 + 外壳/own-classes)、启动器也不同,所以**那三条线根本没法做像素对比**。
已补上 `-Fml10` 开关:选 `optifine-payload-fml10.jar` + `optifine-own-classes.jar`,经 `launch-fml10.ps1` 启动,
游戏参数用该启动器的 `-GameArgs`(ModLauncher 那侧叫 `-ExtraGameArgs`),并带上 `-JavaExe`/`-MainClass`/
`-ExtraClasspath`;`-PrepareOnly` 干跑已验证(存档钉好、`antialiasingLevel=2`、`ofAaLevel=0`、`ofClouds:3` 都写对)。

随后发起了 1.21.9 的 FXAA 关/开一对抓帧(`-FxaaLevel 0` 与 `-FxaaLevel 4`,同样的钉死存档)。发起时这一对**还在跑**
(客户端在、`fxaa-run-fxaa-1219-off-*` 的日志已归档),但截稿时 `logs\fxaa-1219-*.png` **一个都还没落盘** ——
也就是说 FML 10 这条抓帧路径的窗口解析/抓帧阶段还没有产出,像素级结论**尚未取得**,不能拿"没有编译错误"当
"FXAA 生效"的替代。下一轮先查该脚本按命令行解析窗口这一段在 FML 10 进程上的行为,把这对帧拿到手,再跑
`fxaa-check.ps1 -Off ... -On ...` 出边缘能量结论;之后才是 1.21.10/1.21.11 的同款 A/B。

#### FXAA 像素 A/B:抓帧路径修好了,但这一对本身跑错了条件(无光影包)

上一轮"没有帧落盘"的原因找到了,是**装置缺陷**而不是客户端问题:`run-fxaa-capture.ps1` 在命令行里按
`MojangTricksIntelDriversForPerformance|net\.minecraft|BootstrapLauncher` 认"游戏窗口",而 **FML 10 客户端的命令行里
这三个都没有**(实测 1.21.9:命令行含 `DiagnosticClientAny`/`fml.startup.Client`,窗口标题是
`Minecraft NeoForge* 1.21.9`,但就是不含 `net.minecraft`)。于是 `$windowed` 为空 -> `$title` 为空 ->
打印 "NO WINDOW ... nothing to capture" -> 一帧不抓,客户端一直跑到自己的超时。
已在该过滤器里补上 `fml\.startup\.Client|DiagnosticClient`,并实测通过:off/on 两次运行各抓到 3 帧
(870x519,标题 `Minecraft NeoForge* 1.21.9 - Singleplayer`,每次都只停自己启动的那个 pid)。

但这一对的**像素结论仍然无效**,而且原因换了一个,同样记清楚:

* 抓帧脚本**不支持 `-Pack`**,它经 `test-save-shaders.ps1 -PrepareOnly` 写出的 `optionsshaders.txt` 是
  `shaderPack=`(空)—— 三次 FXAA-on 帧的日志里就是 `[Shaders] No shaderpack loaded.`;
* 结果:FXAA 关的三帧是正常画面(平均亮度 165.6),FXAA 4x 的三帧**几乎全黑**(平均亮度 21.6,三帧字节数完全相同
  16328),`fxaa-check.ps1` 判 **INCONCLUSIVE**(两帧 84.7% 像素变化,远超可比的阈值)。
* 也就是说,这一对测的是"**没有光影包时开 FXAA**"的画面,不是闸门条件(有光影包 + FXAA)。
  下一步必须先给抓帧脚本加 `-Pack`(与闸门同条件),用 FXAA **2x**(不是 4x)重跑,再判"黑帧"是否只是
  "无光影包 + FXAA"的产物;若带包仍黑,那才是需要追的真缺陷。

**结论:FXAA 的像素级生效证据仍未取得**(编译通过与像素生效是两件事);已证实的是:三条 FML 10 线 FXAA 2x
不再有管线编译错误、0 崩溃(上一节)。

#### FXAA 像素 A/B(带光影包重跑):抓帧方法在 FXAA 打开时拿不到画面

按要求给抓帧脚本加了 `-Pack` 之后重跑 1.21.9 的一对(同一钉死存档,`MakeUp-UltraFast-9.5e.zip`):

* **FXAA 关(level 0)**:三帧 525460 / 524992 / 524061 字节,是真实场景(`fxaa-check` 测得平均边缘能量 16.79、
  硬边 47869);日志里光影包正常加载、所有 program 编译完成。
* **FXAA 开(level 2)**:三帧**都是 16328 字节**,与上一轮"无光影包 + FXAA 4x"那三帧**字节数完全相同**,
  `fxaa-check` 给出的数字也完全相同(平均边缘能量 4.2661、硬边 10690)—— 也就是**同一张均匀画面**,与场景、
  与有没有光影包都无关。该次运行日志同样显示 `Loaded shaderpack: MakeUp-UltraFast-9.5e.zip` 且所有 program
  加载完成、无报错。
* 于是判定仍是 **INCONCLUSIVE**(两帧 84.7% 像素不同),但原因已经从"条件错"变成**方法失效**:
  `capture-window.ps1` 用的是 `PrintWindow`,而 FXAA 打开时 OptiFine 的最终画面是经它自己的后处理链合成后再上屏的,
  这条路径下 `PrintWindow` 拿回来的是均匀帧(两种完全不同条件下字节数一模一样,正说明它与渲染内容无关)。

**因此 FXAA 的像素级证据仍缺**,而且现在明确了要换抓帧手段:用**游戏自己的截图**而不是 `PrintWindow`
(按 F2 / `Screenshot` 类把合成后的帧存到 `screenshots/`,装置已有 `click-at.ps1 -Key` 可以发按键),
再对同一存档的关/开两帧跑 `fxaa-check.ps1`。在拿到这个之前,不得把 FXAA 记成"已验证"。

#### FXAA 打开后画面是黑的 —— 用**游戏自身截图**测出来的真缺陷(不是抓帧假象)

上一轮怀疑 `PrintWindow` 拿不到 FXAA 合成后的画面,于是这一轮改用**游戏自己的截图**(发 F2,VK 113,
`click-at.ps1 -Key 113`,窗口自动置前;截图落在 `<gameDir>\screenshots\`),同一钉死存档、同样带
`MakeUp-UltraFast-9.5e.zip`,只改 `optionsshaders.txt` 的 `antialiasingLevel`:

| 条件 | 三帧大小 | 平均亮度 | 不同颜色数 | 内容 |
|---|---|---|---|---|
| FXAA 关(0) | 729947 / 721492 / 714434 B | 134.2 | 166 / 170 | 正常世界画面 |
| FXAA 开(2) | 29869 / 30756 / 30756 B | 11.8 / 14.3 / 14.3 | **118**(三帧恒为 118) | **近乎全黑** |

帧来自游戏自己写出(854x480,`Screenshot` 类),与 `PrintWindow` 无关,而且两次运行的日志都显示
`Loaded shaderpack: MakeUp-UltraFast-9.5e.zip`、所有 program 编译加载完成、无报错。也就是说:

**在 1.21.9 上打开 OptiFine 的 FXAA(2x)会得到一帧几乎全黑的画面**;这不是抓帧假象,是一个真缺陷。
(此前"FXAA 管线编译失败"已经修掉 —— 现在它编译通过了,但输出是黑的,说明问题从"编译不过"变成了"合成结果不对"。)

最可能的位置:我这一轮只把 **blit 趟**的顶点阶段改成了 `minecraft:core/screenquad`(与原版自身一致),
**FXAA 趟仍然用 OptiFine 自带的 `post/fxaa_of_2x.vsh`** —— 那个顶点着色器按的是 1.21.5 时代的
`Position`/`Projection`/`SamplerInfo` 约定,而 1.21.9 的后处理渲染器给的是新约定;它"能编译"不等于
"varying/输出写对",于是 FXAA 趟把黑写进 `swap`,blit 趟老老实实把黑拷回 `minecraft:main`。
下一步就是把这个环节也按该线版本适配(OptiFabric 参考实现的两步做法:FXAA 趟的顶点阶段同样用该线存在的
阶段,并按新 schema 给出对应的 vsh/fsh),然后再用同一对截图判定:FXAA 关/开两帧应当是**同一场景**且
开的一侧边缘能量更低(硬边更少),那才算"FXAA 生效"。

结论:FXAA 仍未通过 —— 现在是**实测到"开了就黑屏"**,因此 release 不发。

#### FXAA 黑屏的根因确认:OptiFine 那条链的**顶点阶段**是旧约定的,换成该线自身的阶段就恢复可见

上一节把"FXAA 打开后近乎全黑"记为真缺陷,并怀疑是"只修了 blit 趟、FXAA 趟仍用 OptiFine 自带的旧约定 vsh"。
这一轮用**诊断性**(只改装置里那份 jar,不进仓库修法)验证了这一点:

* 把 `post_effect/fxaa_of_{2,4}x.json` 里 **FXAA 趟**的顶点阶段也改成 `minecraft:core/screenquad`
  (只改这一处,blit 趟保持已修状态),重跑 FXAA 2x 并用游戏自身截图取帧:

| 条件 | 平均亮度 | 颜色数 | 管线编译错误 |
|---|---|---|---|
| FXAA 关 | 134.2 | 166 | 0 |
| FXAA 开,**只修 blit 趟**(仓库现在的状态) | **11.8 / 14.3**(近乎全黑) | 118 | 0 |
| FXAA 开,**两趟都用该线 screenquad**(诊断) | **182.2 / 181.1**(画面可见) | 118 / 119 | 0 |

结论:**黑屏来自 OptiFine 自带的 FXAA 顶点着色器**。它按 1.21.5 时代的约定写(`in vec4 Position`、`Projection`、
`SamplerInfo`),而 1.21.9 的后处理渲染器给的是新约定(原版自己的 `core/screenquad.vsh` 只用 `gl_VertexID`
造全屏三角形,并只输出 `out vec2 texCoord`)——它能编译但输出不对,于是把黑写进 `swap`,blit 趟照抄回
`minecraft:main`。

但"可见"不等于"正确":`screenquad` 只提供 `texCoord`,而 OptiFine 的 `post/fxaa_of_2x.fsh` 还需要
**`in vec4 posPos`**(由它自己那个 vsh 算出的 `{rcpFrame.xy*0.5+xy, xy-rcpFrame*0.5}`)。诊断运行里 `posPos`
是未定义值,画面虽然回来了(而且偏亮,182 对 134),但那是取样错位的结果,不是 FXAA。

**因此正确的下一步是补齐顶点阶段**:或给该线写一个新的顶点着色器,按新约定(`gl_VertexID` 全屏三角形 +
`SamplerInfo` 的 `OutSize`/`InSize`)同时输出 `texCoord` 与 `posPos`;或把 FXAA 的片元阶段改成只用 `texCoord`
自算偏移(OptiFabric 参考实现走的就是后者那条"自带 vsh/fsh + 改 JSON"的路)。做完之后必须用同一对
**游戏自身截图**判定:关/开两帧是同一场景、且开的一侧硬边更少,才算 FXAA 生效。

装置侧已把诊断改动**撤回**(`jars-1.21.9\optifine-own-classes.jar` 还原为仓库修法生成的那份),仓库里的
`FxaaPostChainRepair` 仍只改 blit 趟 —— 现状是"选中 FXAA 会黑屏",这一点在文档里写清楚,免得被当成可用功能。

#### FXAA 修好了,**并且这次是像素级验证通过**

按上一节的诊断补齐了顶点阶段。修法(`FxaaPostChainRepair`,仍由 `OptifinePipeline.split` 应用)现在是两件事:

1. **blit 趟**的 `vertex_shader` 从本线不存在的 `minecraft:post/blit` 改成该线自己的 `minecraft:core/screenquad`;
2. **OptiFine 自带的 FXAA 顶点着色器内容整体重写**(`assets/minecraft/shaders/post/fxaa_of_{2,4}x.vsh`):
   原版按 1.21.5 时代约定写(`in vec4 Position` + `ProjMat`,四边形来自顶点缓冲),而 1.21.9 的后处理渲染器
   **用 `gl_VertexID` 直接造全屏三角形、不提供顶点属性**,所以那个 vsh 读的是垃圾 —— 这就是黑屏的来源。
   重写后的 vsh 采用该线自己的四边形构造(照抄 `core/screenquad.vsh` 的 `gl_VertexID` 三角),并**保留
   OptiFine 的 `posPos` 计算**(`posPos.xy/zw = texCoord ± 0.5/OutSize * (0.5 ∓ SubPixelShift)`),
   因为它的片元阶段同时需要 `texCoord` 与 `posPos`;`SamplerInfo`/`FxaaConfig` 两个 UBO 该版本仍然提供
   (原版自己的后处理着色器也在用 SamplerInfo)。

**实测(1.21.9,钉死存档,`MakeUp-UltraFast-9.5e.zip`,游戏自身截图 F2,只改选项)**

| 条件 | 三帧大小 | `fxaa-check` 结论 |
|---|---|---|
| FXAA 关(0) | 729781 / 722255 / 717817 B | 基准 |
| FXAA 开(2x) | 730219 / 709779 / 710614 B | **FXAA VISIBLE** ×2 对 |

两对帧:`mean edge energy 16.369 -> 15.677`(**-4.2%**)、硬边 `43501 -> 40907`(**-6.0%**),场景差异仅 **5.0%**;
第二对:`17.623 -> 16.891`(**-4.1%**)、硬边 `45808 -> 42979`(**-6.2%**),场景差异 **4.9%**。
即**同一场景下开 FXAA 后高频能量与硬边都下降** —— 这才是"FXAA 生效"的证据,不再是"没报错"或"画面变黑/变亮"。
两次运行管线编译错误都是 0。

**1.21.10 / 1.21.11** 已用同一条修法重新生成 classpath + 外壳 jar(管线分别报告:1.21.10 两条链都修、
1.21.11 的 blit 无需修但 vsh 需要重写),它们的像素级 FXAA 验证留到下一轮跑同一对 F2 截图。

#### FXAA 像素验证:三条 FML 10 线**全部通过**

同一流程(钉死存档 + `MakeUp-UltraFast-9.5e.zip` + 游戏自身截图 F2,`-AaLevel 0`,只改 optionsshaders.txt 的
`antialiasingLevel`=0 或 2),`fxaa-check.ps1` 的判定:

| 线 | 边缘能量变化 | 硬边变化 | 场景差异 | 判定 | 管线编译错误 |
|---|---|---|---|---|---|
| 1.21.9 | **-4.2%** / -4.1%(两对帧) | **-6.0%** / -6.2% | 5.0% / 4.9% | FXAA VISIBLE | 0 |
| 1.21.10 | **-4.3%** | **-6.0%** | 4.6% | FXAA VISIBLE | 0 |
| 1.21.11 | **-6.2%** | **-10.0%** | 6.0% | FXAA VISIBLE | 0 |

即:**同一场景**下打开 OptiFine 的 FXAA 2x 后,高频能量与硬边数量都下降 —— 这是"FXAA 真的在起作用"的证据;
三帧大小与关的一侧相当(不再出现上一轮那种 16 KB/29 KB 的黑屏帧)。1.21.11 的交付 jar 未做修改(J9 自己
就是新约定的着色器,管线报告"无需修"),它的判定同样是 VISIBLE,说明那条线本来就正常。

**至此 FML 10 三条线(1.21.9/1.21.10/1.21.11)在"启动验收 + 建档 + 光影包 + FXAA"四项上全部有正向实测。**
仍未完成的线:1.21.6 / 1.21.7(世界可以、光影包不加载,后处理链同一族,用户已同意暂缓)、26.1.2(sound NO +
世界未启动)。release 仍未发布。

#### 1.21.6 / 1.21.7 的"光影包不加载"不是同一个原因

这两条线(用户已同意暂缓)一直是"世界可以、0 崩溃,但光影包不加载"。既然刚在 1.21.9 上查到两个真实缺陷
(丢失分支的 `loadShaderPack`、旧约定的 FXAA 着色器),先用同一个工具把它们的 OptiFine jar 查了一遍,
结论是**这两处都不是**:

* `jars-1.21.6\optifine-OptiFine_1.21.6_HD_U_J6_pre3.jar` 与
  `jars-1.21.7\optifine-OptiFine_1.21.7_HD_U_J6_pre7.jar` 里 `srg/net/optifine/shaders/Shaders.class` 的
  `shaderPackLoaded` 指令序列是 `PUTSTATIC(结果) -> GETSTATIC + IFEQ(判定)`,**中间没有那对多余的
  `ICONST_0; PUTSTATIC`**(1.21.9 的 J7_pre2 有,1.21.11 的 J9 也没有)—— 也就是这两条线的
  `loadShaderPack` 是"修好"的形状,不是 1.21.9 那个缺陷。
* 两者的 OptiFine jar **都自带** `assets/minecraft/post_effect/fxaa_of_{2,4}x.json`(与 1.21.9/10 一样),
  说明后处理链的位置是对的;它们的光影包不加载另有原因,需要单独调查(下一步:抓这两条线启动时
  `Shaders.loadShaderPack` 前后的日志与 `getShaderPack` 的输入,像当初对 1.21.9 做的那样,而不是套用同一结论)。

因此 1.21.6 / 1.21.7 的问题继续保持在"待调查",不把它算作已修。

#### FXAA 像素验证扩展到 ModLauncher 线:1.21.8 通过,方法在两代加载器上都成立

把 FML 10 上验证过的那套流程(F2 抓游戏自身截图 + 钉死存档 + `MakeUp-UltraFast-9.5e.zip`)原样套到一条
ModLauncher 线(1.21.8,`OptifiNeoforge-1.0.0+mc1.21.8-registered.jar` + `optifine-OptiFine_1.21.8_HD_U_J6_pre16.jar`,
经 `launch.ps1`):

| 条件 | 三帧大小 | 光影包 |
|---|---|---|
| FXAA 关(0) | 638966 / 629453 / 631807 B | Loaded shaderpack |
| FXAA 开(2x) | 634429 / 623260 / 632940 B | Loaded shaderpack |

`fxaa-check.ps1`:边缘能量 `14.207 -> 13.592`(**-4.3%**)、硬边 `36983 -> 35065`(**-5.2%**),
场景差异 **4.0%** -> **FXAA VISIBLE**。与三条 FML 10 线的数字同一量级(-4.1%~-6.2%)。

也就是说这套判定流程对两代加载器都适用,可以按线批量跑。剩余待跑的线:
1.20.1、1.20.2、1.20.4、1.20.6、1.21、1.21.1、1.21.3、1.21.4(每条要关/开两次运行,约 12 分钟);
1.21.6 / 1.21.7(光影包不加载,先修再量);26.1.2(世界未启动,先修再量)。

#### FXAA 批量验证继续:1.20.6 通过;1.21.4 这一对**不可用**(画面里几乎没有边缘)

延续上一节的批量流程(钉死存档 + `MakeUp-UltraFast-9.5e.zip` + F2 游戏自身截图,关/开各 3 帧):

| 线 | 帧大小(关/开) | 光影包 | 边缘能量 | 硬边 | 场景差异 | 判定 |
|---|---|---|---|---|---|---|
| 1.20.6 | 636383 / 632320 B | Loaded(两次都在) | `14.067 -> 13.443`(**-4.4%**) | `36772 -> 34861`(-5.2%) | 4.5% | **FXAA VISIBLE** |
| 1.21.4 | 304639 / 303611 B | Loaded(两次都在) | `0.8606 -> 0.8663`(-0.7%) | `801 -> 835` | 1.3% | NOT VISIBLE —— **但这一对不成立** |

1.21.4 的判定必须按"测量不成立"处理,而不是"FXAA 没生效":那一对帧的**边缘能量只有 0.86、硬边只有 801**,
而其他线是 14 左右、3.6 万以上 —— 也就是画面几乎没有任何高频细节(与该线钉住的存档视角/场景有关),
FXAA 本来就没有可平滑的边缘,`fxaa-check` 于是给出 NOT VISIBLE。要判定这条线,必须先让它抓到一帧有细节的
画面(改存档视角/换一份存档模板),否则这个结论没有意义。

**已通过 FXAA 像素验证的线**:1.21.9、1.21.10、1.21.11、1.21.8、1.20.6(共 5 条)。
**待跑**:1.20.1、1.20.2、1.20.4(需 JDK 17)、1.21、1.21.1、1.21.3,以及 1.21.4(换一份有细节的画面重测)。

#### FXAA 批量验证:1.21.3 通过;两条线因为"画面太空"无法判定

| 线 | 边缘能量 | 硬边 | 场景差异 | 判定 |
|---|---|---|---|---|
| 1.21.3 | `2.1067 -> 0.9397`(**-55.4%**) | `3278 -> 640`(-80.5%) | 6.8% | **FXAA VISIBLE** |
| 1.21.1 | `0.9455 -> 0.9365`(-0.9%) | `621 -> 644` | 6.2% | NOT VISIBLE —— **该对不成立** |

两次运行的光影包都正常加载、世界都进入(1 个 marker)。1.21.1 的问题与上一节的 1.21.4 相同:画面**几乎没有高频
内容**(边缘能量 0.95 / 0.86,而 1.20.6、1.21.8 等是 14 左右),FXAA 没有可平滑的边缘,结论无从谈起。
1.21.3 那一对虽然也偏"空"(2.11),但开/关差距很大且场景基本没动(6.8%),按判定规则成立;不过它的绝对
数字偏低,若要更硬的证据,应当同样换一帧细节更多的画面复测一次。

**共同原因**:这几条线的 `RigSession` 存档被钉住时,视角落在细节很少的方向(天空/平原),所以抓到的帧边缘稀疏。
**下一步的装置改进**:给抓帧流程加"朝下俯视"的视角钉定(例如 `pin-save-state.ps1 -Pitch 30~45`),让每一对
帧都包含地形细节,然后对 **1.21.1、1.21.4、1.21.3** 复测;之后继续 1.20.1/1.20.2/1.20.4(需 JDK 17)与 1.21
(该线 quickPlay 不进入世界,需走菜单路线)。

**已通过 FXAA 像素验证的线**:1.21.9、1.21.10、1.21.11、1.21.8、1.20.6、1.21.3(6 条)。

#### FXAA 批量验证:1.21.1 换视角钉定后通过;1.21.4 的画面本身不对,不能判

先查明一件事:抓帧流程**本来就**把视角钉成 `-Yaw 0 -Pitch 45`(朝地面),所以上一节"画面太空"不是视角没钉,
而是那几条线的存档在该视角下确实缺细节。给 1.21.1 / 1.21.4 重新钉过一遍(显式 `-Pitch 45`)再测:

| 线 | 关的帧边缘能量 | 开的帧 | 判定 |
|---|---|---|---|
| 1.21.1 | `2.1033`(比之前 0.9455 高,说明这次真的朝地面了) | `0.9302` | **FXAA VISIBLE**(边缘 -55.8%、硬边 -80.0%、场景差异 8.9%) |
| 1.21.4 | `0.8609` | `0.8836` | **REVERSED**(-2.6%)—— 但**该对不成立** |

1.21.4 的"REVERSED"不能当成"FXAA 反向":它的两帧边缘能量都在 0.86~0.88,硬边 800 上下,画面几乎是平的
(其他线是 14 左右、3.6 万硬边;即便 1.21.1/1.21.3 也有 2.1)。在这种几乎没有内容的帧上,±2.6% 就是噪声。
而且值得注意:**1.21.1 与 1.21.3 的关/开数字几乎相同**(2.1033/0.9302 与 2.1067/0.9397),说明这两条线抓到的是
同一处场景;1.21.4 的关帧能量比它们**开**的帧还低,说明该线抓到的画面根本不是同一类东西 ——
下一步要看它到底渲染了什么(先量该帧的亮度/颜色分布,再决定是"相机位置不对"还是"这条线没把世界画出来")。

**已通过 FXAA 像素验证的线**:1.21.9、1.21.10、1.21.11、1.21.8、1.20.6、1.21.3、1.21.1(7 条)。
**待查**:1.21.4(画面本身可疑);**待测**:1.20.1、1.20.2、1.20.4(需 JDK 17)、1.21(需菜单路线进世界)。

##### 补上 1.21.4 那两帧到底画了什么(紧接上一节的测量)

| 帧 | 平均亮度 | 不同颜色数 |
|---|---|---|
| 1.21.4 FXAA 关 | **42.2** | **59** |
| 1.21.4 FXAA 开 | **42.1** | **60** |
| 1.21.1 FXAA 关 | 36.8 | 118 |
| 1.21.1 FXAA 开 | 33.6 | 76 |

1.21.4 的两帧是**又暗又平**(平均亮度 42、只有 59 种颜色),对照 1.20.6/1.21.8 那种 14 边缘能量、3.6 万硬边的
正常场景,这几乎可以肯定**相机在实心方块里/地下**(存档里该玩家的位置就在地形内),而不是"这条线渲染坏了"。
所以它的 FXAA 判定既不能算通过也不能算不通过 —— 是**取景无效**。
下一步:抓帧前用 `/tp` 把玩家放到已知的地表位置(装置已有 `send-chat.ps1` 可发命令),再取这一对;
1.21.1 虽然亮度也偏低(33~37)但颜色数与边缘能量都正常,它的 VISIBLE 判定有效。

##### 1.21.4:用 `/tp` 换取景的尝试没成功,该对仍然测不了

按上一节的想法,在抓帧前用 `send-chat.ps1 -Text "/tp @s 0 100 0 0 45"` 把玩家送到地表(该线单机存档允许命令),
然后照旧 F2 取三帧。结果:

* **传送没有生效**:FXAA 开的那三帧与传送前一模一样(平均亮度 **41.8**、**61** 种颜色;传送前是 42.2 / 59),
  客户端日志里也**没有**任何传送回显(`Teleported` / `Unknown command` 都没有)—— 所以相机还在原地,
  这次改动等于没做。
* **关的那一次连帧都没抓到**(`fxaa1.21.4t-0-*.png` 一个都没有),于是 `fxaa-check` 直接报参数无效
  (拿不到文件),这一对根本没跑起来。

结论:**1.21.4 的 FXAA 判定依然缺失**,而且现在多了一条要查的事 —— 这条线上"发进游戏里的输入到底有没有生效"
(与之前"多人 /register 从未落地"是同一类问题:输入路径本身没被证实)。下一步:先用一个**有可见回显**的命令
验证输入路径(例如发一条普通聊天并确认它出现在日志里,或发一个会写文件的命令),再谈换视角;输入路径不通时,
换视角/传送这类做法都不成立。

#### 输入没生效的真正原因:窗口标题写错了(而且机器上有一批没退出的旧客户端)

上一节"`/tp` 没生效"的根因查明了,不是输入路径本身:

* `send-chat.ps1` 的日志直接写着 `no window matching 'Minecraft NeoForge* 1.21.4 - Singleplayer' - is the game running?`
  —— 它**找不到窗口就不发输入**;而 `click-at.ps1` 在同一条件下只是"analysing the screenshot only"然后**照样按了 F2**,
  所以才出现"帧抓到了、命令没到"的差别。
* 实测窗口标题(启动一次 1.21.4 后逐个进程看):
  `pid 12348 title='Minecraft* 1.21.4 - Singleplayer'` —— 这条线的窗口标题里**没有 "NeoForge"**,
  我脚本里写死的 `'Minecraft NeoForge* 1.21.4 - Singleplayer'` 永远匹配不上。
  (1.21.9/1.21.10/1.21.11 那些线标题里确实带 NeoForge,所以它们一路都匹配成功 —— 这个差异以前没注意到。)
  下一步:把标题匹配改成"按命令行解析进程"或至少用容错的模式(例如 `Minecraft*1.21.4*`),再给 1.21.4 重做
  `/tp` + 成对抓帧。
* **顺带发现一个装置卫生问题**:那次检查看到**一大批仍然活着的客户端窗口**
  (`Minecraft* 26.1.2 - Singleplayer`、`1.21.11`、`1.21.9`、`1.21.3`、`1.21.10`、`1.21.8`、`1.21.4` 等,
  以及一个不在世界里的 `Minecraft NeoForge* 1.21.4`)。它们是早前几轮跑完没有退出的进程,既占内存,
  也可能在后续抓帧时抢焦点、把帧抓到别的客户端去。**没有擅自杀掉**(无法确定哪个是用户自己的会话),
  但下一轮做任何抓帧前应当先确认并清理这一批,否则测量结果不可信。

#### 抓帧的下一步:标题匹配修好了,但"先置前再按键"这一步反而让按键失效

按上一节的结论把标题改成**运行时解析**(从活着的进程里取 `MainWindowTitle`)重跑 1.21.4 一对:

* 解析成功:`resolved title: 'Minecraft* 1.21.4 - Singleplayer'`;`send-chat.ps1` 这次也真的执行了
  (`window focused : True` / `mode : SendInput`),不再报"找不到窗口";
* **但两次运行的 F2 都没产出截图(frames: 0)**,而且事后全盘查过 `game\*\screenshots`,
  最近 25 分钟内**任何 profile 都没有新 PNG** —— 按键根本没到游戏里。

对照之前几次成功的运行,差别正好在被修的这一处:

| 情形 | `click-at.ps1` 的行为 | 结果 |
|---|---|---|
| 标题**匹配不上**(我写死的标题错了) | 打印 `no window matching ... analysing the screenshot only`,**只按键不置前** | **抓到 3 帧** |
| 标题**能匹配**(本次) | 先 `SetForegroundWindow` 再 `SendInput` 按键 | **0 帧** |

也就是说:**游戏窗口本来就在前台时,直接注入按键是有效的;而"先由子进程把窗口置前、再注入"这条路反而把按键送丢了**
(最可能是子进程自己抢了前台,或置前后注入落到了别的窗口)。这是装置缺陷,不是游戏问题 —— 与"哪条线"无关;
1.21.4 的取景问题(相机在地形里)也还没解决。

下一步(按顺序,别再混在一起):
1. 先做**单次**按键验证:在窗口已在后台/前台的两种状态下各按一次 F2,确认"不置前直接注入"是否稳定产出截图;
2. 按验证结果调整 `click-at.ps1`(例如置前后回到同一个进程里注入、或置前后加足够延迟并重新确认前台),
   再谈 `/tp` 与成对抓帧;
3. 然后才是 1.21.4 的 FXAA 判定,以及 1.20.1/1.20.2/1.20.4(JDK 17)与 1.21(菜单路线)。

#### 单次按键验证:结论明确 —— **F2 必须在"不置前"的情况下按才有效**

同一次 1.21.4 运行里连着按三次 F2,唯一差别是用哪个标题去找窗口(找到窗口就会先置前):

| 按键 | 传入的标题 | `click-at.ps1` 行为 | 截图数 |
|---|---|---|---|
| 1 | `ZZNoSuchWindowZZ`(故意匹配不上) | `no window matching ... analysing the screenshot only`,**不置前**,直接按键 | **1** |
| 2 | `Minecraft* 1.21.4 - Singleplayer`(真实标题) | `focused: yes`,**先置前后按键** | **0** |
| 3 | 同上(重复一次) | 同上 | **0** |

也就是说:**游戏窗口本来就在前台时,直接注入按键每次都成功;而先调用 `SetForegroundWindow` 再注入,按键就丢了**
—— 该调用发生在一个子 powershell 进程里,前台很可能被控制台拿走,于是注入的键落到控制台而不是游戏。
这与前几轮"标题写错时反而抓到帧、标题修对后一帧都抓不到"的现象完全吻合。

装置侧已改 `click-at.ps1`:新增 `-NoFocus`,让 `-Key` 路径可以**跳过自动置前**(需要置前的调用方照旧);
抓帧流程后续统一用 `-NoFocus` 按 F2。**这不是游戏问题,也不会影响产品结论** —— 只是测量手段的修正。
下一步:用 `-NoFocus` 重做 1.21.4 的 `/tp` + 成对抓帧(取景问题仍待解决),再继续 1.20.x/1.21。

#### 抓帧流程的第二个坑:截图前后不能有任何"打开着的界面";而且当时的窗口其实是**上一轮没退出的客户端**

同一次 1.21.4 运行里的两点对照(都是"不置前"注入 F2):

| 步骤 | 结果 |
|---|---|
| A:世界起来后**立刻**盲按 F2(全程没有任何聊天/菜单操作) | **1 帧** |
| B:先 `send-chat` 发 `/tp ...`,再按 Escape,再盲按 F2 | **0 帧** |

结合按键日志(`-NoFocus` 确实跳过了置前:日志里不再有 `focused: yes`)与"全盘 20 分钟内没有任何 profile 出新 PNG",
可以得到两条结论:

1. **F2 只在没有别的界面挡着时才写文件**。`send-chat.ps1` 是用 T 打开聊天栏再输入、Enter 发送的;这条线上命令
   没有可靠地发出去(与长期挂着的"多人 `/register` 从未落地"同源),聊天栏留在屏幕上,随后按 Escape 也没关掉它,
   于是 F2 被聊天栏吃掉 —— 这是**测量手段**的问题,不是游戏缺陷。
2. 更要紧的是:**当时那个被当成"本次运行的窗口"的进程 pid 12348,是上一轮就没有退出的客户端**
   (两次运行日志里的 pid 完全相同)。我前面几次清理用的命令行关键字(`optifineoforge|neoforge-2`)居然一个都没匹配到,
   所以"清干净了"是错的判断 —— 必须先把真实命令行看清楚、把过滤条件改对,否则每次抓帧都可能是在跟一个旧客户端说话,
   结论自然不可信。

**下一步(顺序)**:
1. 看清那个常驻客户端的真实命令行,修正清理过滤,保证一次运行只跟自己启动的进程打交道;
2. **不再用游戏内命令来摆相机** —— `pin-save-state.ps1` 本来就直接写存档 NBT(视角就是它写的),把它扩展成
   同时钉住 `Pos`(x/y/z),就能在**不需要任何游戏内输入**的前提下把相机放到地表,从根上绕开聊天栏那条路;
3. 然后才重做 1.21.4 的成对抓帧与判定,以及 1.20.1/1.20.2/1.20.4(JDK 17)、1.21(菜单路线)。

#### 重要更正:那些"旧客户端"不是我们的 —— 它们是**另一个项目**(lithium-patch)的测试客户端

上一节把 pid 12348 当成"我们自己上一轮没退出的客户端",这是错的。它的真实命令行是:

```
"C:\Program Files\Java\jdk-21\bin\java.exe" @"I:\mods\lithium-patch\work\1.21.4-sweep\java-args.txt"
```

也就是 **`I:\mods\lithium-patch`** 那个项目在同时跑自己的版本巡检,它的客户端窗口标题与我们的**格式完全一样**
(`Minecraft* <mc> - Singleplayer`)。现在机器上这样的窗口有 7 个(26.1.2、1.21.11、1.21.9、1.21.4、1.21.3、
1.21.10、1.21.8),这也解释了为什么我用 `optifineoforge|neoforge-2` 过滤一个都匹配不到:它们根本不含那些字样。

由此必须更正两件事:

1. **本轮 1.21.4 那几次"0 帧"的直接原因**:按标题 `Get-Process ... MainWindowTitle -eq $title | Select -First 1`
   解析窗口时,选中的是**那个项目的** 1.21.4 客户端(pid 12348),于是 `/tp`、F2 全都打到了别人的客户端上;
   我自己的客户端根本没收到按键,所以我的 `screenshots\` 里自然没有文件。
2. **按标题找窗口这种做法本身不安全** —— 两个项目的标题无法区分。凡是要对"我们自己的客户端"做输入/抓帧,
   都必须**按命令行/游戏目录确认进程身份**(命令行里含 `I:\mods\optifineoforge-test\game\<profile>`),再拿它的窗口句柄。

**关于此前 7 条已通过的判定**:那些运行里我传的是(写错的)标题,`click-at.ps1` 于是**盲按** F2,而抓到的 PNG
确实落在**我自己的** profile 目录里(`game\neoforge-21.x\screenshots\`)—— 另一个项目的客户端只会把截图写进
它自己的 `work\*-sweep` 目录,所以那 7 条的证据来源是我方客户端,判定仍然成立;但**方法上必须补上进程身份校验**,
否则下一次仍可能测到别人家的客户端上。

**下一步(顺序)**:①窗口解析改成"按命令行锁定我方客户端 → 取句柄";②`pin-save-state.ps1` 扩展为同时钉 `Pos`,
用写存档代替游戏内 `/tp`,彻底不再依赖聊天栏;③重做 1.21.4 的成对抓帧与判定;④继续 1.20.x/1.21 各线。

#### 26.1.2 的"载荷缺粒子修复"已经不成立(实测),剩下的是"世界没起来 + sound NO"

按目标里挂着的那一条"26.1.2 的离线载荷缺粒子修复",跑了一次 `repair-26.1.2-payload.ps1 -DryRun`(只读检查):

* 脚本的报告是:`no method of ... ParticleEngine reads the particle provider through
  ParticleResources.getProviders()/Registry.getId()/Int2ObjectMap.get(), so nothing was repaired` ——
  也就是**它要找的那段旧形状已经不存在了**,紧接着它自己打出
  `makeParticle calls ParticleResources.getProviders()Ljava/util/Map;`,正是**修好之后**的写法;
* 交付件的时间也对得上:`jars-26.1.2\optifine-26.1.2-neoforge.jar` 是 **2026-09-20 08:03**(10170792 字节),
  而 `.before-particle-fix` 备份是 09-19 23:50(10018170 字节)—— 修复是在那次之后落进交付件的。

**结论:这一条可以从待办里划掉**,26.1.2 真正剩下的问题是**"世界从未启动 + Sound engine 起不来"**,以及它的
`optifine-own-classes.jar` 仍是 09-19 的产物(没有带上这一轮为 FML 10 写的两个修复:`ShadersPackLoadedRepair`
与 `FxaaPostChainRepair`)。注意 26.1.2 走的是**它自己那套**(OptiFine 自带类处理器、载荷是重建过的 OptiFine jar),
所以那两条修法不会自动落到它身上 —— 需要先按它的架构重新生成 own-classes,再实跑一次看世界为什么不启动
(而不是继续沿用"载荷缺修复"这个已经过期的判断)。

#### 装置修正:窗口必须按**进程身份**解析,已写好并对着真实的"别人家客户端"验证过

新增 `client-window.ps1`:给定 profile,返回**我方**客户端的 `pid / hwnd / title`,判据是**命令行同时含**
该 profile 与 `optifineoforge-test\game\<profile>`;标题完全不参与判断。它带一个 `-Explain`(不是 `-Verbose`
—— 后者是 PowerShell 通用参数,重名会让整个脚本加载失败,这个坑当场踩过一次)。

实测(对着当前机器上的活进程):

| 查询 | 结果 |
|---|---|
| 我方 `neoforge-21.4.149` | `no client of this rig ... (candidates: 0)` —— 我方当前确实没有客户端在跑 |
| 我方 `neoforge-21.9.16-beta` | 同上,0 个 |
| 别的项目的 `1.21.4-sweep` | `pid 12348 window=True ownsRigDir=False title='Minecraft* 1.21.4 - Singleplayer'` → **被正确拒绝** |

也就是说:同一个"标题看起来一模一样"的客户端,该助手能凭命令行把它认成**不是我们家的**,这正是前几轮
0 帧问题的根因所在。下一步把它接进抓帧流程(F2 之前先用它确认"本次运行的客户端确实是我方的、且窗口可用"),
再做 1.21.4 的成对抓帧;随后才是 `pin-save-state.ps1` 钉 `Pos`(绕开聊天栏)与 1.20.x/1.21 各线。

#### 用写存档来钉玩家位置:代码加了,但**还没验证通过**,不能算可用

想法(接上一节):与其在跑着的客户端里敲 `/tp`(聊天栏会吃掉截图键),不如直接写存档的 `playerdata\*.dat`
—— `pin-save-state.ps1` 本来就是这么钉视角的。于是给它加了 `-PlayerX/-PlayerY/-PlayerZ`,写 `Pos`(list<double>[3],
与 `Rotation` 同一套"定宽原地改写、不动长度前缀"的做法)。

但当场实测暴露两件事,**功能目前不可用**:

1. `Set-Fixed` **没有 'double' 分支**(只有 byte/int/long/float),照现在的代码会抛
   `unsupported write kind double` —— 必须先补上这一种宽度;
2. 更关键:`-Dump` 读回来是 `Rotation: list<0> []` 与 `Pos: list<0> []`,**列表元素个数读成了 0**,
   与事实不符(该存档玩家的 `Pos`/`Rotation` 显然有 3 与 2 个元素)。在这一点解释清楚之前,
   `Payload+5/+13/+21` 这几个偏移就不能信 —— 也就是说"钉 Pos"现在既没写成也没读对。

因此**本轮不宣称任何进展落地**:`-PlayerX/-PlayerY/-PlayerZ` 只是半成品。下一步:①补 `Set-Fixed` 的 'double';
②用十六进制对照(直接 dump `playerdata\*.dat` 解压后的字节)查清列表计数为什么读成 0、必要时修 `Read-Nbt`;
③读数正确之后,才用 0/100/0 钉住 1.21.4 的相机并重做成对抓帧。

(The dump reading list counts as 0 also affects the *existing* Rotation pinning if it is the same bug - the rotation
pin has been used for FXAA captures all along, so this needs checking rather than assuming.)

#### 更严重的发现:装置的 NBT **列表计数一直读成 0** —— 也就是说"钉视角"从来没真正生效过

补 `-PlayerX/Y/Z` 时顺手 dump 了整棵 NBT 树,结果连 `level.dat` 里的
`ServerBrands: list<0> []` 都是 **count = 0**(它显然至少有两项:`vanilla`、`neoforge`),而同一棵树上
**标量**都读得对(`Time: type 4 = 6000`、`DayTime: type 4 = 6000`、`raining: type 1 = 0`、`GameType/version/seed` 等)。

由此可判定:`Read-Nbt` 的**列表分支(type 9)坏了** —— 计数(`$pos+1..$pos+4` 那四个字节)没有被正确读到。
影响是双重的:

1. 我这一轮加的 `Pos` 写入不可用(依赖列表偏移)—— 这一点上一节已经写了;
2. **更要紧**:现有的 `Rotation`(pitch/yaw)钉定走的是同一个列表分支,而且脚本里带着
   `if ($rot.Count -ne 2) { ... skipped }` 的保护 —— 计数永远是 0,于是**它每次都走"skipped"分支**,
   也就是说**所谓"每次抓帧都把视角钉成 pitch 45"从来没有生效过**。这正好解释了前几轮在两三条线上看到的
   "画面又暗又平"(相机其实停在存档原来的位置,可能就在地形里),而不是那些线渲染有问题。
   (标量类的钉定 —— DayTime/GameTime/天气/游戏规则 —— 走的是定宽字段写入,那些是**真的生效**的:
   dump 里 Time/DayTime 都是 6000,rain=0。)

**对已通过判定的影响**:那 7 条 FXAA 判定不因此失效 —— 判定要求的是"两帧同一场景",而关/开两次用的是同一份
存档、相机停在同一处,实测场景差异只有 4~6%。但文档里凡涉及"每帧都钉住视角"的措辞必须按此更正:
**视角从未被钉住,被钉住的只有时间/天气/规则与存档状态**。

**下一步**:①查清并修好 `Read-Nbt` 的列表分支(对照解压后的字节,先看 `ServerBrands` 这种简单列表);
②补 `Set-Fixed` 的 'double';③用修好的列表读写来实现 `Pos`/`Rotation` 钉定,再把 1.21.4 的相机摆到地表、
重做成对抓帧;④顺带复看那 7 条的证据(判定有效,但若"同场景"的可信度能被更强的视角钉定提高,应补做)。

#### **更正上一节**:NBT 读取没有坏;真正的原因是这份存档**根本没有玩家数据**

上一节我据 `-Dump` 输出(`ServerBrands: list<0> []`)判定"`Read-Nbt` 的列表分支坏了,所以视角钉定从未生效"。
**这个判定是错的**,用原始字节核对后应当撤回:

* 直接解压 `level.dat` 看字节,`ServerBrands` 就是 `09 00 0C 'ServerBrands' 00 00000000` ——
  **elem=0、count=0**,即一个**空列表**;读取器读得没错,是我把"空列表"当成了"读错";
* 再看 `level.dat` 里的 `Player` 复合:**`Pos` 与 `Rotation` 同样是 `type=9 elem=0 count=0` 的空列表**;
* 而 `saves\RigSession\playerdata\` 目录**存在但一个文件都没有** —— 这份存档里玩家从未被保存过。

也就是说:`pin-save-state.ps1` 自己早就写明过这种情况 ——
"no playerdata\*.dat in this save - view direction not pinned ... the client then spawns a fresh player at
SpawnX/Y/Z with the spawn angle"。**视角没有被钉住,不是因为读取器坏了,而是因为这份存档里没有可钉的玩家数据**,
客户端每次都在出生点重新造一个玩家。1.21.4 抓到的画面又暗又平,正是"出生点恰好在没什么可看的地方"。

标量类的钉定(时间/天气/游戏规则)照旧是真生效的;脚本的其它部分没有嫌疑。

**下一步(改为走"出生点"这条路)**:`SpawnX/SpawnY/SpawnZ` 是 `level.dat` 里的 **TAG_Int 标量**,
正是这个脚本**能可靠改写**的那种字段 —— 把它们钉到一个地表位置(例如 0/70/0),让新造的玩家落在有地形的地方,
再用 F2 取帧;若仍不理想,再考虑让客户端正常退出一次以生成 `playerdata`,然后才谈 `Pos`/`Rotation` 钉定。
(我这一轮加的 `-PlayerX/Y/Z` 依赖列表写入,在这份存档上没有意义;先留着,等有 playerdata 的线再用。)

#### 摆相机的可靠办法落地:`-SpawnX/-SpawnY/-SpawnZ`(写 level.dat 的 TAG_Int 标量),已按字节验证

按上一节的更正走"出生点"这条路。`pin-save-state.ps1` 新增 `-SpawnX/-SpawnY/-SpawnZ`,走的是这个脚本**最可靠**的
那条写路径(与 DayTime/GameTime/天气同一套:定宽标量、原地改写、不动任何长度前缀),不依赖列表、不依赖游戏内输入。

实测(1.21.4 的 `RigSession`,钉到 0/70/0):

```
level.dat SpawnX: 0 -> 0
level.dat SpawnY: 60 -> 70
level.dat SpawnZ: 0 -> 0
```

并且**直接解压 `level.dat` 读回字节**确认:`SpawnX = 0`、`SpawnY = 70`、`SpawnZ = 0` —— 写入确实落地。

意义:对"playerdata 为空、玩家从未保存过"的存档(1.21.4 就是),这是**唯一**能摆相机的办法 ——
`Pos`/`Rotation` 没有可写的对象,而出生点是 `level.dat` 里的标量,客户端会照它生成新玩家。
下一步:用钉好的出生点重取 1.21.4 的一对帧,先看画面是否终于有细节(边缘能量应从 0.86 升到 10 的量级),
再谈 FXAA 判定;然后同样处理其它"没保存过玩家"的线。

#### 1.21.4 的"又暗又平"其实是**标题画面**,不是世界 —— 抓帧必须验证"已进入世界"

用钉好的出生点(0/70/0)重取 1.21.4 的一对,这次两件事都成功了:三次盲按 F2 **各抓到 3 帧**,而且
`client-window.ps1` 确认了窗口属于**我方**客户端。但判定依旧无意义:

* 窗口标题是 **`Minecraft NeoForge* 1.21.4`** —— **没有 ` - Singleplayer` 后缀**,也就是客户端**停在标题画面**,
  quickPlay 这次没有进入世界;
* 两帧的统计完全一致(平均亮度 41.9、54 色、边缘能量 0.8625/0.8622),那正是标题画面的全景背景;
  `fxaa-check` 判 NOT VISIBLE(0.0%),对一张菜单截图毫无意义。

回头看,前几轮 1.21.4 那些"又暗又平"的帧(亮度 42、59 色、边缘 0.86)**极可能一直是标题画面**,而不是"相机在地形里"
—— 我先前把它归因于出生点位置,这个解释至少不完整。

**下一步(抓帧流程必须加的前置条件)**:取帧之前先确认客户端**真的在世界里** —— 可用两条独立证据:
①窗口标题含 ` - Singleplayer`(或世界名);②客户端日志里有世界标记(`Preparing spawn area` / `joined the game`)。
任一不满足就不取帧、直接报"没有进世界"。这条检查对**所有**线的抓帧都适用,应当写进流程,而不是每轮靠人看。

#### 装置:新增"是否真的在世界里"的前置检查(`wait-for-world.ps1`),并对两种反例验证

取帧前必须先确认客户端**真的进了世界**,而不是只看"等够秒数"。新脚本 `wait-for-world.ps1` 要求**两条独立证据同时成立**:

* 窗口标题带世界名(`Minecraft* 1.21.4 - Singleplayer` 这种" - <世界名>"后缀,只有进了关卡才有);
* 该 profile 的 `logs\latest.log` 里出现 `joined the game`(集成服务器在玩家真正进入关卡时才打印)。

并对反例做了验证(不需要跑满一次):

| 场景 | 结果 |
|---|---|
| 我方该 profile **没有**客户端在跑 | `TIMEOUT ... title='' (in a level: False), log joined: False`,退出码 1 —— **不会**谎报成功 |
| **另一个项目**的 1.21.4 客户端在跑(`1.21.4-sweep`) | 标题看起来"在世界里",但它没有我方 rig 的日志路径 → `log joined: False` → 同样 TIMEOUT/退出码 1 —— **不会**被误认成我方 |

也就是说:这条检查同时挡住了本轮遇到的两类事故 —— "等够了但其实还在标题画面"(1.21.4 那对菜单截图)与
"抓到的是别人家客户端"。正例(我方客户端真的在世界里)在之前 7 条已通过的线上都被人工确认过同样的两个信号
(标题带 ` - Singleplayer`、日志有世界标记),所以判据本身是取自实证的,不是猜的。

**下一步**:把该检查接到抓帧流程的 F2 之前(不满足就不取帧、直接记"没有进世界");然后重做 1.21.4 的一对;
再继续 1.20.1/1.20.2/1.20.4(JDK 17)与 1.21(菜单路线)。

#### `wait-for-world.ps1` 的正例也验证通过,并且暴露了一个取帧细节

在 1.21.8(世界进入可靠的那条线)上把整条加固后的流程跑了一遍:

* `wait-for-world.ps1` 返回成功:`title='Minecraft NeoForge* 1.21.8 - Singleplayer'` **且** 日志里有
  `joined the game` —— 两条证据都拿到了(此前只验证过两个反例);
* 随后**盲按**(`-NoFocus`,不置前)F2 两次,各产出截图:**2 帧**;
* 两帧统计:平均亮度 **50.7 / 124.1**、颜色数 **138 / 204** —— 是有内容的真实画面(不是菜单那种 42/54),
  说明"前置检查通过 → 取帧"这条链是通的。

**顺带发现的取帧细节**:同一世界里最早那帧偏暗(50.7)、第二帧才是正常亮度(124.1),说明**按下 F2 后画面还在
过渡/加载**。取成对帧时应当**丢弃第一帧**(或先按一次、等几秒再按第二次),否则会把"过渡帧"当成样本 ——
这与 `run-fxaa-capture.ps1` 本来就"多拍几帧、用后面的"是同一个道理,只是我这几轮的手工流程没有照做。

**至此装置侧的三处修正都已落地并各自验证**:①窗口按进程身份解析(`client-window.ps1`,反例=别人家客户端);
②摆相机走 `level.dat` 的 `SpawnX/Y/Z` 标量(按字节读回验证);③取帧前必须有"在世界里"的双证据
(`wait-for-world.ps1`,两个反例 + 一个正例)。下一轮就可以按这条加固流程,把 1.21.4 与其余线一次跑完。

#### 1.21.4 的世界进不去的**真正原因**找到了:它的 stub 表少了 `BlockEntity` 能力三件套

加固后的流程在 1.21.4 上两次 **TIMEOUT**(标题始终没有世界名、日志没有 `joined the game`),前置检查如实拒绝取帧。
翻客户端日志,最后是**区块生成里的异常**:

```
java.lang.NoSuchMethodError: 'void net.minecraft.world.level.block.entity.BlockEntity.gatherCapabilities()'
   at net.minecraft.server.level.ChunkMap.runGenerationTask(...)
```

也就是说:世界**在生成区块时抛错**,根本没加载完 —— 这才是这条线"又暗又平""一直不进世界"的原因,与出生点、取景无关。

对照各线的 `stub-additions-*.txt`(ml11 的 loader 用它给被保留/替换的类补 NeoForge 加进来的成员):

| 线 | 有 `BlockEntity gatherCapabilities ()V` 吗 |
|---|---|
| 1.21 / 1.21.1 / 1.21.3 | **有**(当年就是靠它修好同一故障) |
| 1.21.4 | **缺**(`gatherCapabilities`/`getCapabilities`/`invalidateCaps` 三件套都没有) |
| 1.21.6 / 1.21.7 / 1.21.8 / 1.21.9 / 1.21.10 / 1.21.11 | 也缺,但这些线的日志里**没有**这个错误,世界能进 —— 所以它们**不需要**,不能凭"别的线有"就一律补上(给不需要的类加空实现会掩盖真实缺成员) |

**已做**:给 `stub-additions-1.21.4.txt` 补上三件套(带注释写明这是实测故障、以及为什么只补这一条线)。
**下一步**:重建 1.21.4 的 loader jar(它由 `add-line.ps1` 生成,会消费该文件),再跑一次
`wait-for-world` → 若世界能进,继续取成对帧;随后才回到 1.20.x/1.21 的 FXAA 验证。

##### 补充:同一错误也出现在 **1.21.6** 的日志里(1.21.7 及以后没有)

上表里"哪些线的日志真有这个错误"按实测逐条核对后是:

| 线 | 日志里的实据 | 结论 |
|---|---|---|
| 1.21 / 1.21.1 / 1.21.3 | `Stubbed net.minecraft.world.level.block.entity.BlockEnt...` | 早先已修,靠的正是这个 stub |
| 1.21.4 | `java.lang.NoSuchMethodError: ...BlockEntity.gatherCapabilities()`(区块生成) | **缺**,已补 |
| **1.21.6** | **同样的 `NoSuchMethodError`** | **缺**,已补 |
| 1.21.7 / 1.21.8 / 1.21.9 / 1.21.10 / 1.21.11 | 日志里**没有**这个错误 | 不动(避免用空实现掩盖真实缺成员) |

因此 `stub-additions-1.21.6.txt` 也补上了同样的三件套(注释里写明实测依据与"为什么只补这两条线")。
下一步:重建 **1.21.4 与 1.21.6** 的 loader jar 后各自重测 —— 1.21.4 看世界能否进入再取 FXAA 成对帧;
1.21.6 看"世界可以、光影包不加载"是否也随之改变(它本来就是挂着"世界可以"的那条,值得重新确认)。

#### 重建 1.21.4(进行中):loader jar 已变大,但**尚未验证**,而且发现一个目录不一致

按上一节的结论重建 1.21.4 这一线:`add-line.ps1 -Mc 1.21.4 -NeoForge 21.4.149 -OptifineJar <1.21.4 的 OptiFine jar> -SkipInstall`。
作业**尚未结束**(gradle 阶段的 java 进程仍在跑),所以本轮**不宣称修好**。已观察到的中间状态:

* `jars-1.21.4\OptifiNeoforge-1.0.0+mc1.21.4-registered.jar` 已被重写:**1870326 字节**(原 1861509,+8.8 KB),
  时间 09/23 08:25 —— 体积增量与"新增了 stub 条目"相符,但**这只是相符,不是证据**;
* `jars-1.21.4-new\...` 仍是 **09/22** 的旧产物,而 `retest-all.ps1` 里 1.21.4 恰恰用的是
  `dir = 'jars-1.21.4-new'`。**这个不一致必须在复测前解决**(否则重测的还是旧 jar,结论会误导);
* 抽查的 `jars-1.21.4-new` 里 `optifineoforge/stubs.txt` 里 `gatherCapabilities` 仍为 **ABSENT** ——
  同样是因为那份还是旧构建;重建后的 `jars-1.21.4` 那份尚未核对。

**下一步(顺序)**:①等重建结束;②核对重建产物里 `stubs.txt`(或 loader 的 stub 计划)确实含
`BlockEntity gatherCapabilities ()V` 三件套;③搞清 `jars-1.21.4` 与 `jars-1.21.4-new` 哪个是该线实际使用的,
让两者一致(或修正 `retest-all.ps1` 的 `dir`);④对 1.21.4 跑 `wait-for-world` —— 世界若终于能进,再取 FXAA 成对帧;
⑤对 1.21.6 做同样的事。

#### 1.21.4:stub 补上后世界**能进了**,`gatherCapabilities` 消失,下一个缺陷浮出水面

用重建后的 loader jar(`jars-1.21.4`,其 `optifineoforge/stubs.txt` 已实测含
`BlockEntity gatherCapabilities ()V` 三件套)重跑:客户端这次**进入了世界**(日志有 `Preparing spawn area`、
光影包也加载了),而且 `NoSuchMethodError` 计数为 **0** —— 上一轮那个"区块生成抛错、世界永远加载不完"的故障
**确实被这三条 stub 修掉了**(这是"读了 jar 里的资源"意义上的证据,不是靠体积推断)。

但同一轮里出现了一个**新的、更靠后的**故障:集成服务器在 tick 时崩了,生成了
`crash-2026-09-23_09.05.47-server.txt`(`MinecraftServer.tickChildren` 路径),随即
`Stopping server`。所以 1.21.4 现在的状态是:**世界能进、然后服务器 tick 崩溃** —— 与 1.20.4 / 1.21.x 当年
"进世界后撞到下一个缺成员"的节奏一样,下一步就是照当时那套方法读崩溃报告、定位缺的成员或错的类。
`wait-for-world` 仍然判 TIMEOUT(它要求 `joined the game`,而这次是在进入过程中崩溃),这条判定是**对的**:
崩溃的这一次不该被当成"世界已就绪"来取帧。

另外仍需处理:`retest-all.ps1` 给 1.21.4 记的是 `dir = 'jars-1.21.4-new'`,而重建写的是 `jars-1.21.4`;
两者必须在下次 sweep 之前统一,否则扫的还是旧 jar。

##### 1.21.4 这个崩溃与 FML 10 三条线当年那个是**同一族**,修法已知

崩溃报告的正文是:

```
Description: Exception ticking world
java.lang.IllegalStateException: Cannot get config value before config is loaded.
  at net.neoforged.neoforge.common.ModConfigSpec$ConfigValue.get(ModConfigSpec.java:1222)
  at net.minecraft.world.level.Level.guardEntityTick(Level.java:582)
```

这正是我在 1.21.9/1.21.10/1.21.11 上诊断并修过的那件事:`Level.guardEntityTick` 只在**某个实体 tick 抛异常**时
才去读 NeoForge 的**服务端 config**(`NeoForgeServerConfig`),而服务端 config 的加载挂在
`ServerLifecycleHooks.handleServerAboutToStart` 上 —— 那个调用在**运行时**的 `IntegratedServer` 里;
一旦载荷把 OptiFine 自己那份 `IntegratedServer` 顶上去(它没有这个调用),config 就永远不会加载,
于是"第一个抛异常的实体 tick"死在错误处理路径里,崩溃报告写的却是 config,而**真正的实体异常被掩盖**。

FML 10 线的修法是**整类保留运行时的 `IntegratedServer`**(写在 `keep-additions-<line>.txt` 里)。
1.21.4 属于 ml11 分支,对应的做法是把它加进该线的 keep 计划(ml11 侧是 `keep-runtime-<mc>.txt` 一类),
然后重建 loader jar 复测 —— 这与"它现在世界能进、然后服务器 tick 崩"的状态正好接上,
而且**应当先确认该线的 `IntegratedServer` 是否也被载荷替换**(用同样的 `javap` 对照运行时与载荷两份)。

#### 1.21.4 的"缺 IntegratedServer 保留"已确认,并已补进该线的 keep 清单

用 `javap`/jar 内容对照确认了与 FML 10 完全相同的机制:

* 该线交付的 loader jar 里有 **`optifineoforge/patched/net/minecraft/client/server/IntegratedServer.class`**
  —— 也就是载荷用的是 OptiFine 自己那份,运行时的 NeoForge 版本被替换掉;
* 而 jar 内 `optifineoforge/keep-runtime.txt` 里 **`IntegratedServer` 条目数为 0** —— 没有保留;
* 于是 `ServerLifecycleHooks.handleServerAboutToStart` 这个"加载服务端 config"的调用一起消失,
  服务端 config 永不加载,首个抛异常的实体 tick 死在 `Level.guardEntityTick` 的错误路径里
  (crash-2026-09-23_09.05.47-server.txt 正是这个)。

再看各线的 keep 清单,**这条修法在 ml11 分支里本来就是既有做法**:

| keep 清单 | 是否含 IntegratedServer 保留 |
|---|---|
| `keep-runtime-1.21.txt` | **有**,注释写着 "IntegratedServer.initServer - the server-lifecycle hook must be the runtime's own body" |
| `keep-runtime-1.21.8.txt` | **有**(同上) |
| `keep-runtime-1.21.1 / 1.21.3 / 1.21.4 / 1.21.6 / 1.21.7` | 都没有 |

**已做**:给 `keep-runtime-1.21.4.txt` 追加整类保留 `net/minecraft/client/server/IntegratedServer	*`
(带注释写明实测崩溃、机制与"为什么是整类保留")。**下一步**:重建 1.21.4 的 loader jar → 再跑一次
`wait-for-world`(这次期望能真正 `joined the game` 且不再 tick 崩溃)→ 之后才取 FXAA 成对帧。
1.21.1 / 1.21.3 / 1.21.6 / 1.21.7 也缺这条,但它们各自的实测状态不同(1.21.1、1.21.3 记录为已通过;
1.21.6 本轮实测有 `gatherCapabilities` 崩溃)—— **按各自证据逐条处理,不一律照搬**。

#### 1.21.4 两个缺陷都已修复并实测通过

按上一节的两处改动重建该线(重建日志本身就是证据):

```
== build-jars
patch entries dropped for 1 class(es): [net/minecraft/client/server/IntegratedServer]   <- 载荷不再改这个类
keep plan: 3 line(s)                                                                  <- 原来的 2 条 + 新的 IntegratedServer
```

并且**读了重建后 jar 内的 `optifineoforge/keep-runtime.txt`**,`IntegratedServer` 条目数 = 2(注释 + 条目)——
不是靠体积或日志推断。随后清理 `crash-reports` 再实跑一次:

| 检查项 | 结果 |
|---|---|
| `wait-for-world.ps1` | **ok**:`title='Minecraft NeoForge* 1.21.4 - Singleplayer'` **且**日志有 `joined the game` |
| `Cannot get config value` | **0** |
| `NoSuchMethodError` | **0**(上一轮的 `gatherCapabilities` 彻底消失) |
| 新崩溃报告 | **0** |
| 光影包 | `Loaded shaderpack: MakeUp-UltraFast-9.5e.zip` |

也就是说 1.21.4 从"世界永远加载不完"推进到**世界能进、玩家能 join、无崩溃、光影包加载**。
顺手也把 `jars-1.21.4` 与 `jars-1.21.4-new` 两份目录**同步成同一份**(`retest-all.ps1` 用的是后者)。

下一步:①给 1.21.4 取 FXAA 成对帧(它现在还缺这一条判定);②对 1.21.6 做同样两件事(它本轮实测有
`gatherCapabilities`,而 keep 清单里同样没有 `IntegratedServer`)——**先按它自己的证据确认,再动手**。

#### 1.21.6:同样两处修复已重建并实测 —— 世界能进、无崩溃;只剩"光影包不加载"这条既有待办

按 1.21.4 的两条修法处理 1.21.6(`keep-runtime-1.21.6.txt` 追加整类保留 `IntegratedServer`,
`stub-additions-1.21.6.txt` 上一轮已补 `BlockEntity` 三件套),重建日志显示:

```
patch entries dropped for 1 class(es): [net/minecraft/client/server/IntegratedServer]
keep plan: 4 line(s)
```

随后清空 `crash-reports` 实跑一次:

| 检查项 | 结果 |
|---|---|
| `wait-for-world.ps1` | **ok**:`title='Minecraft NeoForge* 1.21.6 - Singleplayer'` 且日志有 `joined the game` |
| `Cannot get config value` | **0** |
| `NoSuchMethodError` | **0** |
| 新崩溃报告 | **0** |
| 光影包 | `No shaderpack loaded.` ← **仍未解决** |

也就是说 1.21.6 的"世界能不能进/会不会崩"已经干净了,剩下的是**它本来挂着的那条待办**(用户已同意暂缓):
光影包不加载 —— 与该线的后处理链/资源 schema 有关,与这次修的两件事无关。这一条要单独调查,
不能因为"世界进了"就当成已通过。

**1.21.4 / 1.21.6 两条线现在的状态**:世界能进、玩家能 join、无崩溃、光影包加载(1.21.6 除外);
两条线都还缺 **FXAA 成对帧**这一步(1.21.4 只差取帧;1.21.6 要先解决光影包才谈 FXAA)。

#### 1.21.4 终于取到成对帧,但**判定仍是 INCONCLUSIVE** —— 两帧不是同一场景

世界能进之后,两次运行各取到 3 帧(`wait-for-world` 两次都 ok),但比较结果:

```
frame off : mean edge energy 8.8733  hard edges 11573
frame on  : mean edge energy 5.3258  hard edges 10835
edge energy change : 40.0%   hard edge change : 6.4%
scene difference   : 80.1% of pixels moved by more than 8 luma
VERDICT: INCONCLUSIVE - the two frames are not the same scene
```

亮度(112.8 / 106.9)与颜色数(170 / 119)看着相近,但有 80% 的像素变了 —— 说明**两次运行看到的不是同一个画面**。
原因很明确:这份存档**仍然没有 playerdata**(玩家数据是客户端退出时才写的),于是每次启动都是"新玩家 + 出生点角度",
而**朝向没有被钉住** —— 我上一轮只钉了 `SpawnX/Y/Z`,没有钉朝向。

**下一步(照 SpawnX/Y/Z 那条已验证的路子)**:`level.dat` 里有 `SpawnAngle`(实测存在,是一个标量),
把它一起钉住(同样的定宽原地写入),两次运行的视角就一致了;若该字段在某些线缺席,退一步的方案是
**先让客户端正常退出一次生成 `playerdata`**,再钉 `Pos`/`Rotation`(那条路要等列表写入修好,现仍未做)。
在此之前,1.21.4 的 FXAA **不能算通过**。

#### 1.21.4 的 FXAA **判定通过** —— 钉住出生点朝向是关键

给 `pin-save-state.ps1` 加上 `-SpawnAngle`(`level.dat` 的 TAG_Float 标量,与 SpawnX/Y/Z 同一套定宽原地写入),
写完**读回字节验证**:`level.dat SpawnAngle: 0 -> 180`,读回 = 180。

随后把出生点与朝向一起钉住(`0/70/0`,角度 180)重取 1.21.4 的一对:

```
[0] WORLD ok: title='Minecraft NeoForge* 1.21.4 - Singleplayer', log has 'joined the game'   frames: 3
[2] WORLD ok: 同上                                                                          frames: 3

frame off : mean edge energy 5.5465  hard edges 11138
frame on  : mean edge energy 5.3569  hard edges 10939
edge energy change : 3.4%   hard edge change : 1.8%
scene difference   : 1.4% of pixels moved by more than 8 luma
VERDICT: FXAA VISIBLE
```

**场景差异从 80.1% 掉到 1.4%** —— 上一轮判 INCONCLUSIVE 的原因(两次运行朝向不同)被这条标量写入解决了;
在这个前提下,开 FXAA 后边缘能量下降 3.4%、硬边下降 1.8%,判定 **FXAA VISIBLE**。

**因此 1.21.4 这条线现在同时具备**:世界能进、玩家能 join、无崩溃(上一轮两处修复)+ **FXAA 像素级可见**。
**已通过 FXAA 像素验证的线增至 8 条**:1.21.9、1.21.10、1.21.11、1.21.8、1.20.6、1.21.4、1.21.3、1.21.1。

#### 装置修正:`wait-for-world.ps1` 的进程识别在 1.20.x 上认不出客户端(已修)

跑 1.20.4 的一对时,两次都是 `TIMEOUT ... title=''` —— 也就是助手**根本没找到客户端进程**(而它按设计不找到就不取帧,
所以 0 帧是"诚实拒绝"而不是"世界没起来")。原因:助手原先只用 profile id(`neoforge-20.4.251`)去匹配命令行,
而 **1.20.x 客户端的命令行里不一定带这个 id**。

修法:改成三个标记的**任一**命中即可 —— profile id、本 rig 自己的游戏目录、以及裸的 Minecraft 版本号
(`20.4.251`)。三者都足够专属于本 rig,别人家客户端(标题格式相同)不会误命中。负例复测:无客户端时仍然
`TIMEOUT`,退出码 1,不会谎报。

**踩坑记录**:第一次改这个文件时我用 PowerShell 的 `-replace` 把整段写进去,替换串里的 `$Profile` 被当场
展开成空值,文件被写坏(直接 parse 报错)。教训与之前"`Get-Content` 默认 ANSI 改写文件"同类:**不要用会做变量
展开的字符串去改脚本文件**,要么用 write 整份重写,要么用单引号 here-string。这次是整份重写修好的。

#### 1.20.4 两次"没有客户端"的真因:**我自己的命令行把 JDK 路径拆开了**(不是游戏、也不是助手)

`wait-for-world` 的 `title=''` 一直被读成"助手认不出进程",实际是**客户端根本没启动**。启动器自己的错误日志写着:

```
no profile at Files\Java\jdk-17\versions\neoforge-20.4.251\neoforge-20.4.251.json
```

注意路径开头:`C:\Program ` 不见了。原因在我的临时命令里 —— 用
`Start-Process -ArgumentList @(..., '-JavaHome', 'C:\Program Files\Java\jdk-17')` 时,含空格的参数**没有被引号包住**,
于是被拆成 `C:\Program` 与 `Files\Java\jdk-17` 两个 token,后者粘到了下一个参数上,`launch.ps1` 于是把
`Files\Java\jdk-17` 当成了游戏根目录,自然找不到 profile,**直接抛错退出、什么都没启动**。

这正是 `run-fxaa-capture.ps1` 头部早就写下的那个坑("An array handed to powershell.exe would become separate
command-line tokens"),它自己用
`$quoted = @($launchArgs | ForEach-Object { if ($_ -match '\s') { '"' + $_ + '"' } else { $_ } })` 规避;
而 rig 的其它脚本用调用运算符 `& powershell @args`(不重新解析,空格安全)。**我的临时流程两条都没用**,所以中招。

**下一步**:把 1.20.4 的成对抓帧命令改成"含空格的参数先加引号"再 `Start-Process`(或直接用 `& powershell @args`
并自己在后台跑),然后重跑;1.20.1/1.20.2 同理(它们同样走 JDK 17)。**这条与游戏本身无关,纯属我这一侧的命令拼装问题**,
但把它记下来,免得下次又把它读成"1.20.x 的客户端有问题"。

#### 复核:路径被拆开的**机制**已用三方对照实测钉死,并把"F2 抓帧"固化进助手

前一条把真因记成"我的临时命令",机制部分是**推断**。本轮用同一个含空格的值做了三方对照,现在是实测:

| 调用方式 | 子进程看到的 `-JavaHome` |
|---|---|
| `& powershell @a`(调用运算符直接展开数组) | `C:\Program Files\Java\jdk-17` ✅ |
| `Start-Process -ArgumentList $quoted`(含空格先加引号,即助手自己的写法) | `C:\Program Files\Java\jdk-17` ✅ |
| `Start-Process -ArgumentList $a`(**不加引号**) | `C:\Program` ❌ 被空格劈开 |

第三行正是 `shot-1.20.4b-*.err.log` 里 `no profile at Files\Java\jdk-17\versions\...` 的来历
(`C:\Program ` 掉了,`Files\Java\jdk-17` 粘到 `-JavaHome` 上)。同时确认:**`shot-<prefix>-<n>.err.log`
这套命名在 rig 里没有任何脚本使用**(rig 的助手写的是 `fxaa-run-<prefix>-launchconsole.err.log`),
所以那些日志确实出自我的临时驱动,rig 的 `run-fxaa-capture.ps1`/`run-save-shaders-all.ps1`
本身无此缺陷 —— 前者第 153 行有 `$quoted`,后者用调用运算符。

**但"临时驱动"才是真正的隐患**,所以把它去掉:`run-fxaa-capture.ps1` 新增 `-ShotMethod F2`,
让这个本来就会正确加引号、本来就会 `-JavaHome`、本来就会摆好存档与 options 的助手直接承担 FXAA 抓帧。
新分支的规矩(全部来自已测事实,写在注释里):

* `PrintWindow` 抓不到 FXAA 合成后的画面(FXAA 打开时每帧都是 16328 字节,即合成前的表面),
  所以 FXAA 这一侧的帧**只能**用游戏自己的 F2 截图;
* 连按两次、丢弃靠前的一帧(实测紧跟一次按键出现的 PNG 有时仍是上一帧);
* `-NoFocus` 强制(实测:先抢焦点则按键丢失,不去抢是 1 帧、去抢是 0 帧,两次一致);
* 期间**不得**打开任何界面(实测:聊天开着时 F2 不出图,1 帧 vs 0 帧)。

好处是双份的:既消除了"每换一条线就要手搓驱动、手搓就会再犯同一个引号错误"的复发路径,
也让 FXAA 的像素证据第一次能由**同一个**助手在**每条线**上产出。

#### `optifine-*.jar` 的选择改成"以 retest-all.ps1 的行表为准"(glob 一次错三条线)

`-JavaHome` 的引号问题修好之后,1.20.4 又立刻停在下一道门口:`expected exactly one optifine-*.jar in
jars-1.20.4, found 2`。查下来 `jars-1.20.4` 里除了真正的构建,还躺着 `optifine-remapped-src.jar` ——
那是**流水线的输入**,不是 mod。顺手把每一条线都数了一遍,发现 glob 这个做法**一次错三条**(下表):

| 线 | glob 给出的候选 | 行表记录的(权威) |
|---|---|---|
| 1.20.4 | `..._HD_U_I7.jar`, `optifine-remapped-src.jar` | `..._HD_U_I7.jar` |
| 1.20.1 | `..._HD_U_I6.jar`, `..._HD_U_I6_pre6.jar` | `..._HD_U_I6.jar` |
| 1.21 | `..._HD_U_J1_pre9.jar`, `optifine-original.jar` | `..._HD_U_J1_pre9.jar` |

三条里两条**选错也是能启动的**(pre6 和 I6、original 和 pre9 都是真的 OptiFine 构建),
也就是说不修的话会静默地拿错版本的 OptiFine 去测 —— 那比抛错更糟。所以改成:
**OptiFine 构建名从 `retest-all.ps1` 的行表里读**(行表本来就是这个 rig 的单一口径,`run-save-shaders-all.ps1`
早就这么做了),glob 只作为兜底,且兜底命中时会打印 `WARNING`;另加 `-OptifineJar` 显式覆盖。
逐线核对:12 条 modlauncher 线全部解析到行表里的名字,且文件都在盘上(见本轮实测输出)。

顺带说明为什么这轮**值得**记:三次 1.20.4 抓帧尝试其实一次 JVM 都没起来(先是被引号劈开路径,
再是被 glob 挡住)。如果没有 `wait-for-world` 与助手自己的报错,这三轮极容易被写成"1.20.4 的 FXAA 不行" ——
而它们连游戏都没启动过。**"测试没跑" 与 "测试失败" 必须分开**这条纪律,又救了一次。

#### 1.20.4 的 FXAA 用**像素**测到了(该线原先缺的正是这一项)

两个阻碍清掉之后,1.20.4 这一对跑通了,而且是本轮**第一次**由助手自己(不再靠临时驱动)产出 FXAA 证据:

| 运行 | 帧文件 | 加入世界 | 光照包 |
|---|---|---|---|
| 对照(antialiasingLevel=0) | `1.20.4c-fxaa0-2.png` 627999 B | yes | MakeUp-UltraFast-9.5e.zip |
| FXAA(antialiasingLevel=2) | `1.20.4c-fxaa2-2.png` 625581 B | yes | 同上 |

`fxaa-check.ps1` 判定(同一场景,场景差异仅 4.3%):

```
frame off : mean edge energy 15.1785  hard edges 38892
frame on  : mean edge energy 14.7033  hard edges 36845
VERDICT: FXAA VISIBLE - edge energy fell 3.1% and hard edges 5.3% in the same scene.
```

两次运行都是"窗口按命令行认出来 + 日志有 joined the game + 世界标记齐全 + 0 崩溃报告",
即**帧是在世界里拍的**,不是标题画面或加载画面 —— 这正是先前几次测量被作废的原因。

**一条要记住的方法论**:FXAA 打开的那次,`latest.log` 里含 `fxaa` 的行数是 **0**(1.20.4 这条线的
post chain 走旧布局,加载时不打这种日志)。如果只数日志行,就会得出"FXAA 没生效"的相反结论。
**日志行数不是 FXAA 的证据,像素才是** —— 这也是 `fxaa-check.ps1` 存在的理由,本轮再次印证。

该线至此四项验收 + 存档 + 光照包 + FXAA 全部为绿。仍然缺 FXAA 像素证据的线:**1.20.1、1.20.2、1.21**
(1.21 还要走菜单路线,quickPlay 不建世界)。

#### 1.20.2:用户报的 `canSustainPlant` 崩溃**没再复现**(已实测),但 FXAA 的像素判定**这次是 NOT VISIBLE**

先确认前提:出厂 jar(`jars-1.20.2\OptifiNeoforge-1.0.0+mc1.20.2-registered.jar`,09-22 04:11)里
**确实带着**修好的计划与消费者 —— `optifineoforge/runtime-interfaces.txt`(1403 字符,含 `BlockState` 行)
与 `PatchedClassTransformer` 都在包里。所以缺的从来不是代码,而是"真机跑一次会生成区块的场景"。

为了真的走到 `canSustainPlant` 那条路径(加载现有存档不会重建区块),这轮把 `RigWorld` 复制成 `RigWorldGen`,
清空它的 `region`/`entities`/`poi`,让**区块在加载时重新生成**——这才是调用那些 `BlockState` 成员的场景。

两轮(对照 + FXAA)结果一致:

| 项 | 对照(antialiasingLevel=0) | FXAA(antialiasingLevel=2) |
|---|---|---|
| 加入世界 | yes | yes |
| `Preparing spawn area`(区块真的重建了) | yes | yes |
| `canSustainPlant` 出现次数 | **0** | **0** |
| `NoSuchMethodError` 次数 | **0** | **0** |
| 崩溃报告 | 无 | 无 |
| 光照包 | MakeUp-UltraFast-9.5e.zip | 同 |

**结论:该缺陷在出厂 jar 上已不复现**,而且是在"生成区块"的条件下测的,不是"加载现成区块"的弱条件。

**但 FXAA 这一侧的判定是负的**,照实记:

```
frame off : mean edge energy 7.8096  hard edges 15071
frame on  : mean edge energy 7.7297  hard edges 14517
edge energy change : 1.0%   hard edge change : 3.7%   scene difference : 6.1%
VERDICT: NOT VISIBLE - edge energy changed by only 1.0%, under the 2.0% threshold.
```

这条**既不能当"FXAA 好了",也不能立刻当"1.20.2 的 FXAA 坏了"**,理由都写下来:

* 硬边降了 **3.7%**,方向与 FXAA 一致;脚本的判定要**两项**都过,只有边能量没过(1.0% < 2.0%);
* 这一帧的边密度只有 1.20.4 那对的**一半**(硬边 15071 对 38892,边能量 7.81 对 15.18)——
  俯角 45° 对着地面,画面本来就平,留给 FXAA 的高频细节少,同一个阈值在这里的**信噪比更低**;
* 场景差异 6.1%(1.20.4 是 4.3%),仍属"同一场景",但这些帧来自**重新生成的区块**,比加载现成区块更容易有差异。

所以**下一轮要做的是换一个更有信号的场景重测**(把镜头对准有大量几何/树叶/栅栏的朝向,或直接测 4x),
而不是把这 1.0% 当成结论。若在边密度正常的场景下仍然 NOT VISIBLE,那才是 1.20.2 的真缺陷。
**在这一步做完之前,1.20.2 的 FXAA 不能记成通过。**

#### 1.21:用户报的"资源重载永不结束、声音引擎起不来"**已修好并实测**(四项检查前三项 + 崩溃全过),但 **stderr 基线对不上**,已分类到"是什么"但**还没结案**

用出厂 jar 跑 `retest-all.ps1 -Only 1.21`(走 `launch.ps1`,natives 已按 `natives-for.ps1` 选好):

```
line   profile             launcher     verdict  user  sound  crash  stderr   notes
1.21   neoforge-21.0.167   modlauncher  STARTED  yes   yes    0      46767    STDERR DIFFERS (46767 vs 14141); [OptiFine] 241
```

**`Sound engine started : yes`** —— 这正是该缺陷的判别点(此前"到得了标题界面、声音引擎永远起不来")。
配合 `Setting user: yes` 与 **0 崩溃报告**,该缺陷判**已修复**,且是在真机、真启动路径上测的。

**但 stderr 那一项**照实记为**不通过**:46767 对记录的 14141。查清了它的**构成**,而不是猜:

| | 旧日志 `launch-21.0.167-fixchain.err.log` | 本次 `launch-neoforge-21.0.167.err.log` |
|---|---|---|
| 字节 | 14147(与记录的 14141 只差 6) | 46767 |
| `NoClassDefFoundError` | **4** | **4**(相同) |
| `Exception` 行 | 4 | 15 |
| `ItemStack` 提及 | 0 | 6 |
| `ERROR`/`SEVERE` | 0 | **0** |

抛出点的栈顶是:

```
java.lang.NoClassDefFoundError: net/minecraft/world/level/block/state/BlockState
    at java.base/java.lang.Class.getDeclaredMethods0(Native Method)
    at java.base/java.lang.Class.privateGetDeclaredMethods(Class.java:3580)
```

即**反射枚举某类的声明方法**时,签名里引用了 `BlockState`/`ItemStack`,而在那一刻的类加载器里它们还不可见。
`NoClassDefFoundError` 的**条数没变(4 对 4)**,多出来的是**同一族的栈行**(4 → 15);且这一族在**旧日志里本来就有**,
所以**不是本轮新引入的缺陷**,更不是崩溃(0 个 crash report、0 个 ERROR/SEVERE)。

**结论与下一步(不许含糊)**:功能上"能进标题界面 + 声音引擎起来 + 不崩"是通的;但按目标书写的验收口径,
stderr 属于**四项之一**,所以 1.21 **现在不能记成通过**。两条路,下一轮选一条走完:
① 把这几条反射探针的栈**消掉**(找到发起反射的那段代码,避免在 MC 类不可见时枚举其成员);
② 或把它**降级为可解释噪声并重新基线**,但重新基线必须附上本表这份分类证据(条数未变、无 ERROR/SEVERE、无崩溃),
否则就是把回归洗成通过。

#### 1.20.2 的 FXAA 负判定:确认帧是**有效实景**,并给助手加上可调机位

1.20.2 那一帧的实测:平均亮度 **93.1**、3 像素抽样下 **153** 种颜色 —— 是**有光的真实场景**,
不是先前那种"又暗又平"的作废帧。它的硬边数(15071)只有 1.20.4(38892)的一半,所以 1.0% 的边能量变化
**信噪比不足**,不能用它给 1.20.2 定论。

为此把机位变成参数(`run-fxaa-capture.ps1` 新增 `-Yaw`/`-Pitch`,默认仍是 0/45,不再写死):
原来 45° 俯角是为了避开移动的天空,但它同时也把高频细节挡掉了 —— 这正是"同一阈值在不同场景下灵敏度不同"的来源。
下一轮用有更多几何/植被的朝向重测 1.20.2 的 FXAA。

#### 更正上一轮的分类:1.21 stderr 多出来的**不是**"同一族更多栈行",而是**另一族、且旧日志里没有的 11 条 NPE**

上一轮我写"多出来的是同一族的栈行(4 → 15)"。**这句是错的**,本轮把 15 条一条条列出来对照后:

| | 旧 `launch-21.0.167-fixchain.err.log`(14147 B) | 本次 `launch-neoforge-21.0.167.err.log`(46767 B) |
|---|---|---|
| `NoClassDefFoundError` @ `ReflectorMethod.getMethod/getMethods` | 4 条(BlockState + 3×PoseStack) | 4 条(BlockState + 3×ItemStack) |
| `NullPointerException: ... "cls" is null` @ `FieldLocatorName.getDeclaredField:70` | **0 条** | **11 条** |

11 条 NPE 的完整调用链(去掉 JDK 帧):

```
NullPointerException: Cannot invoke "java.lang.Class.getDeclaredFields()" because "cls" is null
  at net.optifine.reflect.FieldLocatorName.getDeclaredField(FieldLocatorName.java:70)
  at net.optifine.reflect.FieldLocatorName.getDeclaredField(FieldLocatorName.java:81)
  at net.optifine.reflect.FieldLocatorName.getField(FieldLocatorName.java:42)
  at net.optifine.reflect.ReflectorField.getTargetField(ReflectorField.java:65)
  at net.optifine.reflect.ReflectorField.resolve(ReflectorField.java:135)
  at net.optifine.reflect.ReflectorResolver.resolve(ReflectorResolver.java:45)
  at net.minecraft.client.renderer.GameRenderer.frameInit(GameRenderer.java:1568)
```

含义明确:`FieldLocatorName.getField(name)` 返回了 **null 类**,而 `getDeclaredField` 直接对它取 `getDeclaredFields()`,
于是 NPE;异常被上层吞掉并打印,该 `ReflectorField` 保持**未解析**。所以这 11 条不是"噪声变多"那么简单 ——
它意味着 1.21 这条线上有 **11 个 OptiFine 字段没绑上**(依赖它们的特性静默失效),同时把 stderr 顶到了 46767。
**1.21 卡在 stderr 这一项是有实体的,不能靠重新基线绕过去**(除非先把这 11 个字段绑上)。

**关键对照:两次运行的 jar 是同一个**(`jars-1.21` 的 09-22 07:30),所以差异**不是 jar 变化**造成的。
08:08 那次只有 4 条、10:13 的 sweep 有 15 条,差别只可能来自**启动条件**(sweep 走 `launch.ps1 -Fresh` 并先跑
`natives-for.ps1` 选本线 LWJGL;手动那次不是),即"哪条代码路径去解析哪些 Reflector 字段"随配置而变。
下一轮的第一步就是把这一点**量出来**(先原样复现 sweep 的 15 条,再逐项改回手动那次的启动条件,二分到某一个条件),
而不是直接改代码 —— 先知道是哪个条件,才知道该修哪里。

同轮旁证:三条 FML 10 线的 stderr 是**对得上**的(1.21.10 = 0,1.21.11 = 107,与记录一致),所以这一族不是全平台现象。

#### 1.21 的 11 条 cls-null NPE:**"sweep 用了 -Fresh"这个假设已被实测排除**,行为可复现

上一轮把差异归到"启动条件(sweep `-Fresh` + `natives-for`)",本轮先做了二分,结果如下。

先排除"产物不同"(全部逐字节核对过):

| 变量 | 旧 08:08 那次 | 新 10:13 sweep | 结论 |
|---|---|---|---|
| 装载器 jar | — | — | **同一个**:`Get-FileHash` 两边都是 `F959DCCE…A634641`(1906139 B) |
| OptiFine 构建 | `OptiFine_1.21_HD_U_J1_pre9` | 同 | 同一构建 |
| optionsshaders.txt | `shaderPack=` 空 | 同 | 都没有光影包 |
| optionsof.txt | `ofEmissiveTextures:true / ofCustomEntityModels:true / ofAaLevel:0` | 同 | 同一配置 |
| JVM | `java 21.0.9` / ModLauncher 11.0.4 | 同 | 同 |

然后直接二分 `-Fresh`(两次运行各自把 stderr 单独存档,便于对照):

| 运行 | stderr 字节 | cls-null NPE | NoClassDefFoundError |
|---|---|---|---|
| sweep(`-Fresh`) | 46767 | 11 | 4 |
| 同参数**去掉 `-Fresh`** | **46767** | **11** | **4** |

**逐字节相同的规模,连条数都一样** ⇒ `-Fresh` 不是触发条件,当前 1.21 的行为是**稳定可复现**的
(而且这次非 `-Fresh` 的运行同样 `Sound engine started: yes` / `Setting user: yes`)。

顺带排除的还有"整个 rig 搬到 `I:` 导致的普遍性问题":**如果**是搬家造成的,FML 10 那几条线的 stderr
也该一起对不上,但它们**仍然对得上**(1.21.10 = 0、1.21.11 = 107,与记录一致)。所以这不是环境普遍现象,
而是 **1.21 这一条线自己**的事。

仍然成立的硬事实:旧日志 `launch-21.0.167-fixchain.err.log`(14147 B,与记录的 14141 只差 6)是
**搬到 `I:` 之前**在 `C:\Users\kynar\IdeaProjects\optifineoforge-test` 上跑的,而且**那个目录现在已经不存在**
(已核实 `C:\Users\kynar\IdeaProjects\optifineoforge-test` 与 `...\OptifiNeoforge` 均已删除),
所以"用旧环境复现 4 条那次的对照"这条路**已经不可能**,不能再等它。

下一步(已定位入口,便于实现并验证):1.21 这条线的构建入口是
`add-line.ps1 -Mc 1.21 -NeoForge 21.0.167 -OptifineJar <用户的 OptiFine jar>`;但按记录它**只在产物不存在时才跑
`prepare-line`**(复用旧 donor 的陷阱),所以"重建"必须先处理已存在的载荷,不能直接重跑就以为重建了。
修复方向仍是在载荷里给 `FieldLocatorName.getDeclaredField` 加 null 保护(与已有的
`ShadersPackLoadedRepair`/`FxaaPostChainRepair` 同族),因为它现在**必然**往 stderr 打 11 条 NPE 栈 ——
出厂的 mod 不该这样;但**未验证不得声称修好**。

#### 1.21 NPE 追查续:产物变量**全部排除**,对照实验有一次被我自己做成无效,并给出一个便宜的判决实验

**先说被我做成无效的那次对照**(照实记,不能当成证据):我拿"未准备的 `optifine-original.jar`"去跑,得到
stderr 3232 字节、NPE **0** —— 看起来像是"准备好的 jar 才引发 NPE"。**这是无效结论**:该次运行
`Exception in thread "main" java.lang.RuntimeException: java.lang.reflect.InvocationTargetException`
**直接崩在启动阶段**,`Sound engine=NO / Setting user=NO`,客户端根本没到能解析 Reflector 的地步,
所以它的 0 条 NPE 只说明"没跑到那里"。同一坑第五次出现,再次确认:**"没跑到" 与 "没有" 必须分开写。**

**产物变量全部排除**(逐条实测):

| 变量 | 旧 08:08(4 条 trace) | 新(11 条 NPE) | 证据 |
|---|---|---|---|
| 装载器 jar | — | — | `Get-FileHash` 相同 `F959DCCE…A634641` |
| OptiFine 侧 | `optifine-OptiFine_1.21_HD_U_J1_pre9.jar`(5890672 B) | **同名同大小** | 两次日志的 `OptiFine ZIP file:` 全路径行 |
| options / 光影状态 | 无光影包、`ofEmissiveTextures:true` | 同 | optionsof/optionsshaders 逐字段对照 |
| JVM / ModLauncher | java 21.0.9 / 11.0.4 | 同 | 两次启动日志 |
| `-Fresh` | — | — | 有/无 `-Fresh` 结果逐字节相同(46767 / 11 / 4) |

于是**唯一剩下的差别就是 rig 的路径**:旧那次在 `C:\Users\kynar\IdeaProjects\optifineoforge-test`,
新那次在 `I:\mods\optifineoforge-test`;而旧目录**已被删除**,无法原地对照。
注意 union 路径的形状确实带着盘符:`union:/I:/mods/...jar%23182!/`,所以"非 C 盘 / union 路径"是**有嫌疑的**,
但这只是嫌疑,没有被量到。

**下一轮先做这个判决实验(便宜、且不改代码)**:建一个**目录联接(junction)**,把
`C:\...\optifineoforge-test` 指向 `I:\mods\optifineoforge-test`,从 `C:` 那个路径再跑一次 1.21 验收。
* 若 NPE 变成 0～4 条 ⇒ 症状随**路径**走,属于"搬家"引入的 rig 环境问题,那就该在装载侧修路径处理,
  而不是去改 OptiFine 的反射代码;
* 若仍是 11 条 ⇒ 路径无关,`FieldLocatorName.getDeclaredField` 的 null 保护(与
  `ShadersPackLoadedRepair`/`FxaaPostChainRepair` 同族的载荷修复)才是正道,并且要先解决
  `add-line.ps1` 只在产物不存在时才 `prepare-line` 的复用陷阱,重建后再测。

**另外记一个我自己的装置缺陷**:这次 A/B 里 `launch.ps1` 会把 `logs\launch-<profile>.out.log` 覆盖,
我只保住了 `.err.log`,导致"能跑通的那次"的 stdout 被后一次崩溃运行盖掉。以后凡是对照实验,
**两份日志都要在运行后立刻另存**,只留 err 不够。

#### 1.21 NPE 追查:路径假设**被实测排除**;并更正一个我自己的错误 —— 那份"旧日志"是 **09-20** 的,不是当天的

**判决实验(目录联接)**:把 `C:\Users\kynar\IdeaProjects\optifineoforge-test` 做成指向 `I:\mods\optifineoforge-test`
的 junction,从 `C:` 那个路径再跑一次 1.21 验收(日志确认游戏确实从 C: 路径起来:
`OptiFine ZIP file: C:\Users\kynar\IdeaProjects\optifineoforge-test\game\...\optifine-OptiFine_1.21_HD_U_J1_pre9.jar`):

| 运行路径 | stderr | cls-null NPE | NoClassDefFoundError |
|---|---|---|---|
| `I:\mods\...`(10:13 sweep) | 46767 | 11 | 4 |
| `I:\mods\...`(10:20 非 -Fresh) | 46767 | 11 | 4 |
| **`C:\...` 经 junction**(10:29) | **46767** | **11** | **4** |

⇒ **与路径无关**,"搬家(盘符/union 路径)导致"这个候选**排除**。junction 已删除,避免同一 rig 有两条路径带来歧义。

**更正(重要)**:我上一轮写"两次运行用同一个 jar,故差异不是 jar 变化"——**这个前提是错的**。
`launch-21.0.167-fixchain.out.log` 的文件时间是 **09-20 08:08:29**,不是我先前以为的"当天 08:08"。
那份日志(以及 14141 这个记录值)是 **09-20** 测的,而现在的 jar 是 **09-22 07:30** 重建的。
jar 名是按版本生成的(`optifine-OptiFine_1.21_HD_U_J1_pre9.jar`),**重建不改名**,所以"同名同大小"根本
不能证明"同一份内容" —— 我当时拿名称与大小当证据,是无效推理。**产物变量其实从没被排除过。**

**真正指向根因的一条(实测差集)**:把两次运行的 `[OptiFine] (Reflector) Class not present:` 集合做差
(09-20 那次 55 个,现在 60 个),**多出来的 5 个全部是 Forge 专有类**:

```
+ net.minecraftforge.common.extensions.IForgeEntity
+ net.minecraftforge.common.ForgeConfig
+ net.minecraftforge.common.ForgeConfig$Client
+ net.minecraftforge.common.ForgeConfigSpec
+ net.minecraftforge.common.ForgeConfigSpec$ConfigValue
```

NeoForge 上这些类**按设计就不存在**,所以"找不到"本身是正常的;问题在于 OptiFine 的
`FieldLocatorName.getDeclaredField` 拿到 null 类时**不做 null 检查**,直接 `cls.getDeclaredFields()`,
于是每次探测都变成一条 NPE 栈打进 stderr(11 条),而不是安静地跳过。**这解释了两件事**:
stderr 为什么从 14141 涨到 46767,以及为什么它和"换路径/换 -Fresh"都无关。

**下一步(方向已明确)**:在载荷里给 `FieldLocatorName.getDeclaredField` 加 null 保护(与
`ShadersPackLoadedRepair`/`FxaaPostChainRepair` 同族的字节码修复),让"类不存在"按 OptiFine 原本的语义
安静返回 null —— 出厂的 mod 不该为一批**按设计不存在**的类打 11 条 NPE 栈。做完之后按本段这份证据
**重新记录 1.21 的 stderr 基线**(不是无条件重基线:要先证明并写清"多出来的都是 Forge 专有类 + 无 ERROR/SEVERE + 无崩溃")。
重建 `jars-1.21` 时注意 `add-line.ps1` 只在产物不存在时才跑 `prepare-line` 的复用陷阱。

#### 1.21 的 11 条 NPE **根因已定到字节码**:是 OptiFine 自己的递归漏了"接口没有父类",不是 Forge 类缺失

上一轮我把根因归到"多探测了 5 个 Forge 专有类"。**那只说对了一半**,真正触发 NPE 的是**类的形状**。
把 `srg/net/optifine/reflect/FieldLocatorName.class`(1.21 预备 jar 内,2582 字节)反编译出来,方法体是:

```java
private Field getDeclaredField(Class cls, String name) throws NoSuchFieldException {
    for (Field f : cls.getDeclaredFields())            // 0: aload_1 / 1: getDeclaredFields  <- cls 为 null 就在这一行炸
        if (f.getName().equals(name)) return f;
    if (cls == Object.class)                            // 42: aload_1 / 43: ldc Object / 45: if_acmpne 57
        throw new NoSuchFieldException(name);           // 48..56
    return getDeclaredField(cls.getSuperclass(), name);  // 57..63  <- 递归,传的是 getSuperclass()
}
```

`Class.getSuperclass()` 对**接口**返回 **null**(对 `Object` 也返回 null)。而这里只挡了 `cls == Object.class`,
**没挡 null**。于是:探测的字段属主只要是**接口**,就会用 null 递归一层,然后在 `getDeclaredFields()` 上 NPE。
栈里恰好是 `getDeclaredField:70` 叠在 `getDeclaredField:81` 两层 —— 与"递归一层后炸"完全吻合。

所以:
* 这是**OptiFine 自己的缺陷**(漏了接口/无父类的情形),不是我们缺类;
* 上一轮那 5 个 Forge 专有类(`IForgeEntity` 是接口,`ForgeConfigSpec$ConfigValue` 等)只是**让它更容易被踩到**,
  是相关而非因果;**给这些类补桩并不能修掉它**(接口形状本身就会触发);
* 只在 1.21 这条线上>0 条,与"这条线的 Reflector 表里恰好有属主为接口的字段被解析"一致。

**修法(已设计好,要求栈中性、帧中性)**:把第 63 条指令从
`invokevirtual net/optifine/reflect/FieldLocatorName.getDeclaredField(Class,String)Field`
改成 `invokestatic <guard>(Lnet/optifine/reflect/FieldLocatorName;Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;`。
第 63 条之前栈上正好是 `[this, getSuperclass(), name]`,与静态方法的三参数**逐位对应**,返回类型也一样,
所以 **maxStack 不变、控制流不变、不需要新的 StackMapTable 帧**(这正是上次 `ShadersPackLoadedRepair` 里
"插入 GOTO 导致 VerifyError"的教训的反面)。guard 的语义与 OptiFine 原实现**完全等价**,只在原实现会 NPE 的地方
改为抛 `NoSuchFieldException`(也就是原实现对 `Object` 的情形所做的处理):

```java
public static Field getDeclaredFieldGuarded(FieldLocatorName self, Class<?> cls, String name)
        throws NoSuchFieldException {
    for(Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass())
        for(Field f : c.getDeclaredFields())
            if(f.getName().equals(name)) return f;
    throw new NoSuchFieldException(name);
}
```

`getField()` 本来就有 catch `NoSuchFieldException` → 返回 null 的分支(反编译里 12/48/55/62 处都是 `aconst_null; areturn`),
所以"接口属主"从此安静地解析失败,而不是每次打一条 11 帧的栈。

**尚未实施,因而绝不声称修好**。实施要点(下一轮):guard 类必须与 OptiFine 的类**同一个加载器**可见,
所以它要作为**新条目写进 OptiFine 那个 jar**(放我们自己的包有 NoClassDefFoundError 的风险);
这一条与 FML 10 的载荷路径不同 —— 那里走的是 `OptifinePipeline.split`(条目名已去掉 `srg/` 前缀),
而 1.21 的 ModLauncher 线是从预备 jar 的 **`srg/` 前缀**条目里加载的,所以 ENTRY 要按 `srg/...` 匹配,
两个前缀都要覆盖,否则"修了 FML 10、1.21 照旧"。

#### 1.21 修好并**实测通过**:stderr 从 46767 回到 **14141 —— 与记录值逐字节相同**

上一轮把根因定到字节码后,这轮实现了修复并验证。

**修法(与设计一致,且比"加 guard 类"更省)**:在递归调用点把那一个 `getSuperclass()` 的结果补成非 null,
全部是**直线指令**,因此**不需要新帧、不需要重算 maxStack、也不需要往 jar 里加新类**:

```
59: invokevirtual  Class.getSuperclass:()Ljava/lang/Class;
    ldc            class java/lang/Object
    invokestatic   java/util/Objects.requireNonNullElse:(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
    checkcast      java/lang/Class
62: aload_2
63: invokevirtual  FieldLocatorName.getDeclaredField:(...)Ljava/lang/reflect/Field;
```

接口没有父类 → 现在传 `Object.class` 递归,递归里既有的 `cls == Object.class` 分支照旧抛 `NoSuchFieldException`,
正是 OptiFine 原本对 `Object` 的行为;`getField()` 早就 catch 它并返回 null。**语义等价,只是不再 NPE。**

**实测(同一台机、同一 jar 路径、同样走 `retest-all.ps1 -Only 1.21`)**:

| | 修复前 | 修复后 |
|---|---|---|
| `verdict / user / sound / crash` | STARTED / yes / yes / 0 | STARTED / yes / yes / **0** |
| **stderr 字节** | 46767 | **14141** |
| `cls-null NPE` | 11 | **0** |
| `NoClassDefFoundError` | 4 | 4(**不变**,证明修复只动了它该动的) |
| 结果行 | `STDERR DIFFERS (46767 vs 14141)` | **`expected stderr 14141`** ✅ |
| `VerifyError` | — | **无**(直线插入,原有 StackMapTable 仍有效) |

注意最后一行:修复后**正好等于记录值 14141**,不是"重新基线",而是把多出来的 11 条栈**真正消掉**之后
自然回到基线。这也反过来解释了记录值与现状为何曾经对不上:09-20 那份基线的 jar 里,被解析的字段没有
属主为接口的;09-22 重建后有了,于是凭空多出 11 条。**工具输出幂等**(对已修 jar 再跑一次报
`nothing to repair`),可安全重复执行。

**落地位置**:
* `OptifinePipeline.split` 里新增第三个 OptiFine 类修复(与 `ShadersPackLoadedRepair`/`FxaaPostChainRepair` 同一处),
  这样**今后任何一条线重建出来的 jar 都自带它** —— 发布要用的正是这条路径;
* 独立的 `main(jar)` 用于**已准备好**的 jar(同时匹配 `srg/` 前缀与去前缀两种条目名,因为预备 jar 保留 `srg/`)。
  1.21 的 jar 已就地修补(5890672 → 5890728 字节,备份 `*.pre-nullguard`)。

**一条重要的衍生测量**:逐条检查 16 个 `jars-*` 目录里的 OptiFine jar(在**临时副本**上跑,不改动原件),
**除 1.21 之外全都仍是未加保护的原样**:

```
1.20.1 1.20.2 1.20.2-fixed 1.20.4 1.20.6 1.21.1 1.21.3 1.21.4 1.21.4-new 1.21.6 1.21.7 1.21.8 (±3 个变体)
```

也就是说这是**全平台潜伏**的缺陷,只有 1.21 恰好解析到属主为接口的字段而显形。**没有顺手把它们也打上补丁**,
理由是:那些线的 stderr 现在与记录**一致**,手改 jar 会让记录失去校验意义;而它们**重建时**会经流水线自动带上修复。
下一步按目标书要求"用分支头重建后再验收"时,这 12 条线会一起得到修复并一起被验证。

#### 全 15 条线验收复扫(在 1.21 修复之后):**11 条绿**,3 条因**构建前置缺失**判 NO-RESULT,1 条因**同一个 OptiFine 缺陷**判红

`retest-all.ps1 -Group all`,一条线一个客户端,顺序跑完(结果表存 `logs\retest-all-after-nullguard.txt`):

| 线 | verdict / user / sound / crash | stderr | 判定 |
|---|---|---|---|
| 1.21.9 / 1.21.10 / 1.21.11 | NO-RESULT | — | **REBUILD FAILED**(不是验收结果,不予采信) |
| 26.1.2 | STARTED / yes / yes / 0 | 107 = 预期 | ✅ |
| 1.21 | STARTED / yes / yes / 0 | 14141 = 预期 | ✅(本轮修好的那条) |
| 1.21.1 / 1.21.3 / 1.21.4 / 1.21.6 / 1.21.7 / 1.21.8 | STARTED / yes / yes / 0 | 0 = 预期 | ✅ |
| 1.20.6 | STARTED / yes / yes / 0 | **17856 ≠ 0** | ❌ |
| 1.20.4 | STARTED / yes / yes / 0 | **14481 = 预期** | ✅ |
| 1.20.2 | STARTED / yes / yes / 0 | 14625 = 预期 | ✅ |
| 1.20.1 | STARTED / yes / yes / 0 | 0 = 预期 | ✅ |

两条**变好**的(都是本轮/近几轮改动带来的,值得单独记):
* **26.1.2 首次验收全绿**:`STARTED + sound yes + 0 崩溃 + stderr 107 = 预期`。此前它是"世界起不来 + sound NO";
* **1.20.4 的 stderr 回到记录的 14481**(此前是 17841),即 `natives-for.ps1` 按线选 LWJGL 那一处接线确实修好了那个偏差。

**1.20.6 为什么红(已查清,不是新缺陷)**:它的 stderr 17856 字节里是 **6 条 `cls-null` NPE**
(`because "cls" is null`,`NoClassDefFoundError` 为 **0**),也就是**与 1.21 完全同一个 OptiFine 缺陷**
(`FieldLocatorName.getDeclaredField` 递归不挡接口的无父类)。它的记录值 0 是在"尚未解析到属主为接口的字段"时测的。
⇒ 这条线的红**正是本轮那个修复应当覆盖的第二例**,是全平台潜伏缺陷的又一次显形(见下)。

**FML10 三条为什么 NO-RESULT(已查清,是构建前置,不是代码)**:

```
no compiled fml10 classes at I:\mods\OptifiNeoforge\build\classes\java\main\kynarain\cn\optifineoforge\fml10
- run gradlew -Pmountpoint=fml10 compileJava first
```

而真的去跑 `gradlew -Pmountpoint=fml10 compileJava` 会**编译失败**(15 个错误),根因是缺依赖:

```
错误: 程序包 net.neoforged.neoforgespi.transformation 不存在
import net.neoforged.neoforgespi.transformation.ClassProcessor;
```

即该 mount point 需要把 NeoForge 的 SPI 依赖解析进来才有得编。**这是下一轮的第一件事**(先让 FML10 能重建,
再谈它们的验收)。注意:`build\classes\java\main\...\optifine\*.class` 是在的(09-23 09:13),
只有 `fml10\` 子目录不在,说明这个 mount point 的编译产物不知何时被清掉且此后没再编成。

**顺手把 null 保护打到了所有 ModLauncher 线的 OptiFine jar**(1.21 上一轮已打):12 个目录全部 `PATCHED`
(+46 ~ +57 字节),`jars-1.21` 报 `already guarded`(幂等)。1.20.6 那条红预期会随之消失,**待复扫验证**。

**一条我自己装置上的失误,照实记**:
* 打补丁时**只给 `jars-1.21` 做了备份**(`*.pre-nullguard`),其余 11 个 jar 是**就地覆盖、没有备份**。
  原始 OptiFine jar 仍在 `jars-*/optifine-original.jar` 等处,但"这次改动前的 prepared jar"没有留存 —— 后补备份不算备份;
* 而且有两个 jar 的体积**不是 +50 字节而是 +99 KB 左右**(`jars-1.21.7` 6068212 → 6167474、
  `jars-1.21.8-probe` 6178283 → 6278649)。我的工具是整包重写(每条目复制时间戳但不保留压缩方式),
  体积异常说明这两个包的**重新压缩**引入了额外差异,而不只是那一处插入。所以这两条线必须**复扫确认**
  (1.21.7 与 1.21.8 的 stderr 是否仍为预期的 0),不能凭"补丁很小"就假定无事。

#### null 保护在 11 条 ModLauncher 线上**复扫通过**;FML10 的根因是 rig 少了一步编译,已修

**复扫结果(`retest-all.ps1 -Group modlauncher`,11 条线,全部真机跑过)**:

| 线 | verdict/user/sound/crash | stderr | 判定 |
|---|---|---|---|
| 1.21 | STARTED / yes / yes / 0 | 14141 = 预期 | ✅ |
| 1.21.1 / 1.21.3 / 1.21.4 / 1.21.6 / 1.21.7 / 1.21.8 | STARTED / yes / yes / 0 | 0 = 预期 | ✅ |
| **1.20.6** | STARTED / yes / yes / 0 | **0 = 预期**(此前 17856) | ✅ |
| 1.20.4 | STARTED / yes / yes / 0 | 14481 = 预期 | ✅ |
| 1.20.2 | STARTED / yes / yes / 0 | 14625 = 预期 | ✅ |
| 1.20.1 | STARTED / yes / yes / 0 | 0 = 预期 | ✅ |

即 **11 条 ModLauncher 线的四项验收全部通过**,而且 1.20.6 是**精确回到记录值 0**(不是重新基线):
它的 `cls-null` NPE 由 6 条降到 **0**,`Sound engine=yes`、`Setting user=yes`、0 崩溃报告。
这同时证明上一轮那个"两个 jar 体积异常 +99KB"的担心**不成立**:1.21.7 与 1.21.8 复扫后 stderr 仍是预期的 0,
即整包重写虽然换了压缩结果,**没有改变行为**。(仍然记着:除 `jars-1.21` 外没有做补丁前备份。)

**FML10 三条 NO-RESULT 的根因已修(是 rig 少了一步,不是代码)**:

`prepare-fml10-line.ps1` 一直有这样一步:

```
gradlew.bat --no-daemon -q "-Pmc=$Mc" "-Pneoforge=$NeoForge" '-Pmountpoint=fml10' compileJava
```

而 `retest-all.ps1`(**真正负责重跑验收的那个脚本**)**没有**这一步,只在注释里警告过这个陷阱。
后果实测:不带 `-Pmc/-Pneoforge` 直接编译会用 `gradle.properties` 里的默认(1.21.4 / 21.4.149),
而 `src/fml10` 面向 1.21.9+,于是编译以 15 个错误失败,第一条正是

```
错误: 程序包 net.neoforged.neoforgespi.transformation 不存在
```

`build-fml10-payload.ps1` 于是拒绝("no compiled fml10 classes"),三条线只能报 NO-RESULT。

修法(已落地并 parse 通过):在重建载荷之前,按该线自己的配对编译 mount point
(`$neoForge = $line.profile -replace '^neoforge-',''`),失败则**照实**报 NO-RESULT 并跳过(不启动旧 jar 充数);
并新增 `-Repository` 参数,默认与 `prepare-fml10-line.ps1` 一致,避免两个脚本对"仓库在哪"各说一套。
**另加一道保险**:26.1.2 的载荷本来就不在本脚本重建设置里,所以它的编译步骤**故意跳过** ——
否则一个"编译失败"会把一条**当前通过**的线变成 NO-RESULT(把通过洗成"没跑",同样是不诚实)。

FML10 三条的复扫(编译 → 重建 → 验收)已在本轮末启动,结果见下一轮记录。

#### rig 缺口修好后的 FML10 复扫:**三条全绿**;至此 15 条线的四项验收都已通过

```
line      profile               launcher  verdict  user  sound  crash  stderr
1.21.9    neoforge-21.9.16-beta  fml10     STARTED  yes   yes    0      0     expected stderr 0
1.21.10   neoforge-21.10.64      fml10     STARTED  yes   yes    0      0     expected stderr 0
1.21.11   neoforge-21.11.45      fml10     STARTED  yes   yes    0      107   expected stderr 107
```

三条**都在真机上跑过**、`Sound engine=yes`、`Setting user=yes`、0 崩溃报告、stderr **等于各自记录值**。
编译日志显示按线配对确实生效(分别解析到 147 / 147 / 136 个 artifact),也就是说载荷这次是**从当前源码**编出来再装的,
而不是像上两轮那样"报 NO-RESULT 却仍在跑上一版 jar"。

**验收状态汇总(本轮结束时)**:
* **ModLauncher 11 条**:全绿(本轮 `-Group modlauncher` 复扫);
* **FML10 1.21.9 / 1.21.10 / 1.21.11**:全绿(本轮);
* **26.1.2**:全绿(在上一轮 `-Group all` 里测到 `STARTED + sound yes + 0 崩溃 + stderr 107 = 预期`;本轮按设计跳过它的编译,
  因为本脚本不重建它的载荷 —— 这一条**是在 ModLauncher jar 打补丁之前**测的,它的产物此后未被改动,故结论仍成立)。

⇒ **四项验收(VERDICT/Setting user/Sound engine/无崩溃 + stderr 对比)在 15 条线上都已通过。**

**一条必须写明的区别(不许含糊)**:ModLauncher 那 12 个 OptiFine jar 是**就地打补丁**的,
`OptifinePipeline` 里虽然已经接上这个修复,但"**用流水线从分支头重新构建**这 12 条线"这件事**还没有做**。
所以目标书写的"用分支头重建后的 jar 再验收"目前**只对 FML10 成立**(它们的载荷每轮都从当前源码重编);
ModLauncher 线目前是"修补过的旧构建"。下一条要补的就是这一项。

**仍未完成(按目标书的第 3、5 项)**:
* 逐线的"存档 + 光影 + FXAA"像素证据:已确证 9 条(1.21.9/1.21.10/1.21.11/1.21.8/1.20.6/1.21.4/1.21.3/1.21.1/1.20.4),
  仍缺 1.20.1、1.20.2(上次判 NOT VISIBLE 属低边密度场景,已加 `-Yaw/-Pitch` 可换景重测)、1.21、1.21.6、1.21.7、26.1.2;
* 发布本身:version 已经在 `gradle.properties` 里是 `1.0.0`,但 `docs/PUBLISHING.md`(VERSIONING.md 指向它)**不存在**,
  发布流程缺文档 —— 在真正发布前必须先把这一步补上或改走别的既定流程。

#### 1.20.1 的 FXAA 成对帧:**这对无效**(场景不同),照实记,不当判定

用修好的助手(真机、F2 抓帧)在 1.20.1 上跑了控制组与 FXAA 组。两次运行的前置条件**都成立**:
`joined the game = yes`、`Loaded shaderpack: MakeUp-UltraFast-9.5e.zip`、`Preparing spawn area = yes`,
四次 F2 都真的写了图(`Saved screenshot as 2026-09-23_11.5x.xx.png`)。

但 `fxaa-check.ps1` 的判定是:

```
frame off : mean edge energy 15.2845  hard edges 38846
frame on  : mean edge energy  9.2501  hard edges 20040
edge energy change : 39.5%   hard edge change : 48.4%
scene difference   : 91.7% of pixels differ
VERDICT: INCONCLUSIVE - the two frames are not the same scene
```

自己再量一遍也印证:控制组帧平均亮度 **127.9**、624294 字节;FXAA 组帧 **61.6**、282139 字节 ——
**亮度差一倍、体积差一半**,这是"两个画面"而不是"同一画面的两种抗锯齿"。

**所以这对既不支持通过、也不支持不通过**,只能记为无效。

已经排除的:
* **不是日志里有什么差别**:两次运行的日志是**对称**的 —— 双方 `fxaa` 提及都是 **0** 条
  (与 1.20.4 相同,1.20.x 这条线开 FXAA 时本来就不打这种日志,所以**数日志行在这里同样会误导**),
  双方"shader 问题"行数都是 **4** 条(相同),都没有 post/final 程序行;
* **不是没进世界/没装光影包**(上面已列)。

**下一轮的第一步(可判定,不是猜)**:先验证这条线的**相机钉定到底有没有生效** —— 这个失败模式在本项目里出现过:
1.21.4 上就因为存档 `playerdata` 为空、`Rotation` 钉定走了 skipped 分支,导致两帧朝向不同(当时场景差异也是几十个百分点),
后来靠 `-SpawnAngle` 才降下来。1.20.1 用的是 `RigSession` 存档,而助手钉的是 `-Yaw 0 -Pitch 45`,
所以先按字节读回 `level.dat` 的 `Rotation`/`SpawnAngle`,确认两次运行得到的机位是否一致;机位一致了再谈 FXAA 的像素。

**另记(为第 2 项准备的实测)**:ModLauncher 线"从分支头重建"的入口已确认 ——
`add-line.ps1` 会**真的用当前源码跑 Gradle**(`gradlew -Pmc=$Mc -Pneoforge=$NeoForge -Pmountpoint=... jar` → `build-jars`),
所以它是一次真重建;1.20.x 三条线用它的修正版 `rebuild-120x-line.ps1`(多了"每次从本分支源码重算计划"和 stub 传递的修正)。
本轮尚未执行重建,只确认了入口与参数,免得把"重建"说成已经做过。

#### 1.20.1 那对无效帧的原因:机位钉定**生效了**,但**运行中又漂了** —— 证据在存档自己写回的字节里

对 1.20.1 的存档按助手完全相同的方式跑一次钉定,输出是:

```
00000000-0000-0000-0000-000000000000.dat Rotation[0] yaw: 179.9272 -> 0
00000000-0000-0000-0000-000000000000.dat Rotation[1] pitch: 16.19919 -> 45
```

两件事同时成立,而且**都要看到**:

1. **钉定本身没问题**:它确实把玩家数据里的朝向改成了 `yaw 0 / pitch 45`(这是助手在每次启动**之前**做的);
2. **但它读到的"改之前"的值是 `yaw 179.9272 / pitch 16.19919`** —— 那不是上一次钉定写下的 0/45,而是
   **上一次运行结束时客户端自己写回的朝向**。也就是说:启动时钉在 0/45,运行过程中镜头转到了 ≈180°/16°,
   退出时把新朝向存了回来。

这正是那对帧"场景差异 91.7%、亮度 127.9 对 61.6"的来源:**两次运行各自漂到了不同的朝向**,而不是 FXAA 改了画面。
所以这对无效**与 FXAA 无关**,也**不是钉定工具坏了** —— 是"钉定之后、取帧之前"这段窗口里,镜头被别的东西改了。

**最可能的机制(与项目里已记的事实一致)**:Minecraft 的 `MouseHandler` 用 GLFW 光标位置回调**累加**出鼠标位移,
而不是读消息坐标(这一点在 `click-at.ps1` 的注释里就有:PostMessage 的点击会落在真实光标处)。
窗口创建时光标被抓取并**挪到窗口中心**,产生的第一个位移是一个**与启动瞬间物理光标位置有关**的大跳变 ——
每次运行光标停在哪不一样,跳变就不一样,最终朝向也就不一样。默认 45° 俯角本来是"避开移动天空"的取景,
漂到 16° 就抬起来了,画面自然完全不同。

**下一轮的做法(具体、可验证)**:在启动客户端**之前**把物理光标挪到窗口客户区中心
(`SetCursorPos` + 确认)`**,让抓取时的跳变变成"零/确定值"。判据是硬的:
* 钉定后读回 `yaw/pitch`,**跑完再读一次**存档 —— 若运行后仍是 0/45,说明漂移被消除;
* 然后重取 1.20.1 的那对帧,期望 `scene difference` 落在已通过那 9 条线的量级(4~6%),
  再看 `edge energy` 才有意义。

顺带说明:这条也解释了为什么 1.21.4 当时要靠 `-SpawnAngle` 才把场景差异从 80.1% 压到 1.4% ——
那是同一族"取景不受控"的问题,只是那条线当时连 playerdata 都是空的,只能用 level.dat 的标量。

#### 光标居中修复:**已验证有效**(相机不再漂),但这一轮的帧**没取到**,照实记

**修复本身成立**。在助手启动客户端期间反复把物理光标放到客户区中心(实测 61 次),两次运行结束后的存档读回是:

```
00000000-0000-0000-0000-000000000000.dat Rotation[0] yaw: 0 -> 0
00000000-0000-0000-0000-000000000000.dat Rotation[1] pitch: 45 -> 45
```

对比上一轮同一存档读到的 `yaw 179.9272 / pitch 16.19919`(**运行后漂走**),这次**跑完仍是 0/45** ——
也就是"钉定 → 运行 → 退出"全过程中镜头**没有再被改**。判据(跑完读回仍为 0/45)达成,
`-NoCursorCentre` 这个开关也留着,便于以后 A/B。

**但这一对没有结果**:四次取帧全部报

```
NO F2 FRAME appeared in ...\game\1.20.1-forge-47.4.23\screenshots - the client may not be in a world, or a screen is up
FRAME n WAS NOT WRITTEN - the comparison has nothing to compare
```

即**按了 F2 却没有产生截图文件**。注意:两次运行本身是好的(`joined the game` 有、光影包已加载、
窗口标题 `Minecraft* Forge 1.20.1 - Singleplayer` 认得到、进程响应正常),所以这不是"没进世界";
而**上一轮**同样的两个运行是**能出图的**(那时看到过 `Saved screenshot as ...`)。

**不许含糊**:这既不能算 FXAA 通过、也不能算 1.20.1 有缺陷 —— 这一对是**没有取到证据**。

**嫌疑与下一步(可判定)**:两轮之间机器墙钟从约 12:30 跳到 **22:11**(长时间空闲),而 F2 是用 `keybd_event`
注入的全局按键,**必须落在前台窗口**才有效(这条在 `click-at.ps1` 的注释里有实测记录:
先抢焦点反而丢键,不去抢才成)。锁屏/休眠/别的窗口抢到前台,都会让按键落到别处 —— 这最可能。
所以下一轮**先量前置条件再取帧**:取帧前先确认目标窗口是 `GetForegroundWindow`,
不是就如实报"前台不是本窗口"并**不取帧**(而不是把 0 帧当成结论);
若确认前台正常仍不出帧,再去查注入路径本身。另:助手应在取帧前打印"前台窗口是谁",这一条会加进去。

**顺带记一项与发布有关的实测**:把三个仓库与 rig 全搜了一遍,`publish / CurseForge / Modrinth / gh release`
**没有任何实现**(命中的都是 LICENSE 与 build.gradle 注释);`release/` 下**只有 `version.ps1`**,
它支持 `show|major|minor|patch` 以及 `patch -Base 1.0.0`(注释写明"首次在真实游戏里跑起来时"的用法),
而 `docs/VERSIONING.md` 指向的 `docs/PUBLISHING.md` **不存在**。
也就是说:目标书写的第 5 项"按仓库自己的流水线发布"目前**没有可执行的流程** —— 这是发布这一步的硬缺口,
需要用户指明发布渠道(或明确授权由我来写这套流程),不能凭空造一个。

#### 前台检查一上线就抓到了真凶;**取帧恢复**,但相机在"有人正在用这台机器"时仍会漂 —— 1.20.1 的 FXAA 仍未测到

**前台那一层修好了,而且立刻证明了自己有用**:修好后的两次运行都打出了

```
foreground: 'Minecraft* Forge 1.20.1 - Singleplayer' = this run's window (0 focus attempt(s); the taps can land)
F2 frame: 2026-09-23_22.58.31.png (773673 bytes) -> 1.20.1f-fxaa0-1.png; 2 new file(s)
```

四帧全部取到(773673 / 771771 / 809758 / 407623 字节),而**上一轮同样的两个运行一帧都取不到**。
原因就是被量出来的那件事:`keybd_event` 只落在**前台**窗口上,而我自己每次跑命令都会弹一个控制台窗口抢走前台,
按键便落到 `cmd.exe` 上。**这是我这侧的干扰,不是游戏也不是 FXAA。**

**但这一对仍然无效**,而且原因换了一个,同样有据:

```
00000000-0000-0000-0000-000000000000.dat Rotation[0] yaw: -42.1875 -> 0
00000000-0000-0000-0000-000000000000.dat Rotation[1] pitch: 90 -> 45
```

即:控制组跑完时相机**仍是 0/45**(`.dat_old` 里留着 0/45),而 **FXAA 组跑完时相机已经变成 yaw -42.19 / pitch 90**
—— `pitch 90` 就是**朝天看**。于是 `fxaa-check` 如实判:

```
scene difference   : 88.8% of pixels differ
VERDICT: INCONCLUSIVE - the two frames are not the same scene
```

**关键的新证据**:这次 FXAA 组的第 2 帧,助手报的是 **`2 focus attempt(s)`** ——
也就是那一刻**前台不是我们的窗口**,助手不得不去抢。而抢回前台会让游戏**重新抓取光标**,
抓取时的位移又变成相机转动(正是上一轮记录的那个机制)。所以"建立前台"这个修复**同时也是一个扰动源**:
它能救回取帧,却可能在抢的瞬间把相机转走(pitch 90 正是俯仰被顶到上限的样子)。

**结论(必须写清,别绕)**:1.20.1 的 FXAA **至今仍没有被测到**。三次尝试分别是
"没取到帧(前台被 cmd 抢)"、"相机漂(运行中被转走)"、"取到帧但两组取景不同" ——
没有一次构成有效证据。而这一轮把剩下的障碍也量清楚了:**机器正在被人使用时,物理鼠标移动会被
Minecraft 累加进相机**(它读的是 GLFW 光标位置回调,不是消息坐标),所以"钉住机位"在有人动鼠标时会失效。
之前成功的那 9 条线,都是在机器空闲时取的。

**下一步(自动化判据,而不是再赌一次)**:
1. 每次运行**结束时立即读回** `Rotation`,只在**两次运行都是 0/45** 时才做像素比较;
   哪一次漂了就**丢弃并重跑那一次**(把"取景有效"变成每轮自动校验的前置条件,而不是事后才发现);
2. 取帧那一刻若需要抢前台,抢完**重新居中光标并再读一次朝向**,把抢前台带来的位移也消掉;
3. 若用户正在使用这台机器,就**不要在此时跑成对取帧** —— 这条应写进 rig 的用法说明,而不是靠运气。

#### 已在 GitHub 上发布:15 条线的 Release 全部重建、刷新

用户指定发布渠道为 GitHub。先侦察后执行,结果与"从未发布"完全不同:

**发布前的事实**
* GitHub 上**已有 10 个正式 Release**(2026-09-17 建),各挂 1 个 jar,但那些 jar 是 **159~161 KB 的旧构建**,
  早于本轮全部修复(1.21 的 11 条 NPE、1.20.6 的 stderr、FML10 载荷编译缺口都不在里面);
* **1.20.1、1.20.2、1.21.9、1.21.10、1.21.11 连 tag 都没有**;
* 三个检出指向**同一个仓库**的三个分支(1.20.x / 1.21.x / 26.x);凭据库里存有可调 GitHub API 的令牌(已验证身份),
  所以不需要 `gh` CLI。

**这一步做了什么**
1. `build-release-jars.ps1`(新):按线用**自己的检出与分支**跑 `gradlew jar`,配对与该线启动测试一致。
   15 条全部构建成功并记下大小:
   * 1.20.x 四条(120x @ `a4b08d7`):180625 / 180631 / 180634 / 180852 B
   * 1.21~1.21.8(1.21.x @ `fc794f4`):218862 ~ 218868 B
   * 1.21.9/10/11(fml10 mountpoint):170427 / 170423 / 170423 B
   * 26.1.2(26x @ `3e64e89`):165704 B
2. `publish-github-releases.ps1`(新):建 tag(指向**构建该 jar 的提交**)、建或更新 Release、**替换** asset。
   执行两遍:第一遍 15 条全部发布成功;第二遍修掉一个命名缺陷(见下)。

**发布中发现并修掉的缺陷**:上传走查询串,未转义的 `+` 在那里表示**空格**,GitHub 再把空格转成 `.`,
于是第一条被发成 `OptifiNeoforge-1.0.0.mc1.20.1.jar`(线上原有资产是带 `+` 的)。已改用
`[uri]::EscapeDataString`,并把"删除该 Release 上**所有** `OptifiNeoforge-1.0.0*` 资产再上传"写成规则,
避免两种命名同时挂着。

**最终状态(API 逐个核对)**:15 个 Release、各 **1** 个资产、命名全部为 `OptifiNeoforge-1.0.0+mc<版本>.jar`、
大小与刚构建的一致、`prerelease=False`(按用户 2026-09-23 选定的口径:全部正式 Release,未验证项写进说明)。

**说明里如实标注的部分**(每条 Release 都有这张表):四项启动验收 = pass(2026-09-23 真机、逐条);
存档+光影按线分别标 pass / not tested;**FXAA 只有 9 条是 VISIBLE**,其余写 `NOT PROVEN` 并给出原因
(1.20.1 两次取景不同、1.20.2 上次测量边密度不足、1.21 与 26.1.2 未测、1.21.6/1.21.7 光影包不加载致 FXAA 无从运行)。
另列出仍未完成项:未做全量"从分支头重建后复测"、26.1.2 own-classes 仍是 09-19、多人未测。

**新增文档**:`docs/PUBLISHING.md`(VERSIONING.md 一直指向它但此前不存在)—— 记录渠道、两条命令、配对陷阱
(1.20.1 是 `47.1.106`/Forge 坐标、1.20.6 需 `-Pmodlauncher=11 -Ptarget_java_version=21`)、
asset `+` 转义、先删后建、脏树告警,以及"正式 Release + 说明标注"这个**知情的偏离**。

## 2026-09-27 实测对照:FXAA 的 post_effect 定义"改写"会把功能弄坏,"不提供"才对

为了把产物里 OptiFine 的文件换成"自己撰写"的等价定义,做过一次真机对照(1.21.8,同一存档、相机钉定 yaw 0 / pitch 45、PostMessage 投键取 F2,两对帧都有效——场景差 2.2% 与 4.6%,两次运行结束读回相机均为 0/45、零漂移):

| 游戏加载的定义 | 边缘能量 | 硬边 | 判定 |
|---|---|---|---|
| 本项目自撰(blit 趟顶点改成 `minecraft:core/screenquad`) | **-1.1%** | -0.9% | **NOT VISIBLE** |
| **OptiFine 原版**(本项目一个文件都不提供) | **+8.5%** | **+15.3%** | **VISIBLE** |

结论:**那份 `post_effect/fxaa_of_*.json` 不能由我们改写**。1.21.8 上把 blit 趟的顶点阶段换成 `core/screenquad` 之后,FXAA 那一趟的结果没有回到主画面,画面等于没有抗锯齿。这与姊妹项目 OptiFabric 的实测结论一致(它在 26.x 上删/改 `post_effect/` 曾导致 `Could not find post chain with id: minecraft:fxaa_of_2x`,此后选择**原样保留 OptiFine 自带的那份**)。

因此整改的最终形态是:**产物里不含这两个文件,运行时使用 OptiFine 自己 jar 里的那一份**;早前那处"blit 顶点改 core/screenquad"的修法只在**载荷侧**(FML10 线,由流水线对用户 OptiFine jar 的副本执行)保留,因为 1.21.9 上确实报过
`Couldn't find source for VERTEX shader (minecraft:post/blit)`,而 1.21.9/10/11 的 FXAA 也已在修复后判定 VISIBLE。

> 纪律备忘:这一轮第一次出现"判定有效但结果是否定"的情况——正是靠 **相机读回 0/45 + 场景差 2.2%/4.6%** 才敢下结论。取键改 `PostMessage`(`post-key.ps1`)后,取帧不再受"有人在用鼠标"影响。

## 2026-09-27 改名把 donor 初始化器变成了死方法(1.21.9 构造期崩溃的根因)

改名到 OptifiNeoforge 之后第一次重建 FML10 载荷,1.21.9 从 STARTED 变成 FAILED:

```
java.lang.NullPointerException: Cannot invoke net.neoforged.neoforge.client.gui.GuiLayerManager.initModdedLayers()
  because this.layerManager is null
    at net.minecraft.client.gui.Gui.initModdedOverlays(Gui.java:1604)
    at net.neoforged.neoforge.client.ClientHooks.initClientHooks
    at net.minecraft.client.Minecraft.<init>(Minecraft.java:665)
```

排查用的实测(不是推断):

| 证据 | 09-23 绿运行 | 09-27 改名后 |
|---|---|---|
| `Gui` 的安装计数 | (92 fields, **110** methods) | (92 fields, **111** methods, 1 restored from its donor) |
| `restored N members ... from its donor` 行数 | **0** | **17**(Gui、Mob、Font、ClientLevel、GlDevice、GlStateManager…) |
| `initialised 1 restored instance fields in 1 constructor(s) of Gui` | **有** | **一条都没有** |

那 17 个类恰好就是绿运行里被**内联初始化器**的那一批,所以不是"少做了一点",而是初始化器整批没被认出来。

根因:`MemberRestorePlan.INITIALISER_PREFIX` 同时管**生成**和**识别**,改包名后它变成 `optifineoforge$init$`;
而 `work\<line>\plan\donors` 里的 89 个 donor 类是本机 09-19 生成的,27 个方法名仍是 `optifineoforge$init$<字段>`。
识别失败后这些方法被当成普通方法装进类里(`restored++`),本该由它们赋值的字段(如 `Gui.layerManager`)保持 null。

修法:识别只看**与包名无关的标记** `$init$`(`MemberRestorePlan.isInitialiser` / `initialisedField`),
生成侧继续写带前缀的名字。三处识别点全部改到标记上:1.21.x 的 `MemberRestoreTransformer` 与 FML10 的
`OptifinePayloadClassProcessor`,1.20.x 与 26.x 的 `MemberRestoreTransformer` / `RestoreMembers`。

> 教训:凡是"生成方写名字、识别方读名字"的约定,名字里都不能带会被改名的东西;这类约定一旦破裂,
> 症状会伪装成完全无关的 NPE。

## 2026-09-27 全量四检结果(改名后第一次;并发测量)

FML 10 四条(1.21.9 / 1.21.10 / 1.21.11 / 26.1.2)全部 STARTED + user + sound + 无崩溃 + stderr 与记录值一致;
26.2 仍按客户端拥有者的要求暂停。ModLauncher 组 11 条 registered jar 本轮全部从当前分支头重建:
1.21.1 / 1.21.3 / 1.21.6 / 1.21.7 / 1.20.4 通过(1.20.6 的 17856 字节是 MATRIX §十三 已记录的差异);
**1.21 / 1.20.2 / 1.20.1 在重建后新失败**(SRG 名与 VerifyError 各一),1.21.4 与 1.21.8 因表中遗留的
`dir` 覆盖被跳过(已修,待补跑)。逐条证据、根因定位与"本轮同时修掉的 rig 缺陷"见同目录
`STATUS-2026-09-27-modlauncher-sweep.md`(rig 侧,不在仓库内)以及本文件上一节。
结论:**三条未绿之前不发布**。

### 追加:1.20.1 / 1.20.2 的红是"重建产物"引起(对照实验已定位)

用旧(曾绿)registered jar 配新预备 OptiFine jar 同样失败,而旧预备 jar 配新 registered jar 会换成旧包名
`kynarain/cn/optifineoforge/loader/SetSupplier` 的缺失 —— 即预备 jar 与 registered jar 必须同版本,恢复旧件不是修法。
落点收窄到 SRG 命名线(1.20.x)上 OptiFine 自己类的命名/重定位(预备 jar 里仍是 `srg/net/optifine/**` 635 条,
而运行期缺的是官方形式 `net/optifine/util/MathUtils`);并已发现一处确凿差异:`rebuild-120x-line.ps1`
传给 `build-jars.ps1` 的参数**少了 `-StubDir`**(1.21 的 `add-line.ps1` 有)。下一轮按 `add-line.ps1`
的等价配方重建这两条线并逐项比对。**两条未绿之前不发布。**

### 追加:1.20.x 四条线转绿 —— 两条根因(元数据名 + 补丁集剔除)

1. 预备 OptiFine jar 的元数据名:1.20.x 的 FML 只读 `META-INF/mods.toml`,用 NeoForge 的默认名会让
   OptiFine 那个 jar 不被登记为 mod,它的类不在类路径上(`NoClassDefFoundError: net/optifine/util/MathUtils`
   于 `Mth.<clinit>`)。重建脚本已加 `-MetadataName`。
2. keep 计划里的类必须从 OptiFine 的补丁集中剔除:本分支的 `OptifineJar` **没有**实现
   `--unpatched`(1.21.x 的实现会成对剔 `patch/srg/<类>.class.xdelta|.md5`),于是 OptiFine 先给我们
   保留的类打了补丁,1.20.1 死在 `VerifyError: Util$7.<init>`(putfield 在 super 之前)。rig 侧已按同一
   keep 计划剔除(1.20.1/1.20.2/1.20.4/1.20.6 分别 30/32/10/4 条),四条线随即全部转绿
   (stderr 0 / 14625 / 14481 与记录值一致;1.20.6 的 17856 是 MATRIX §十三 已记录的差异)。
   **本分支的 `OptifineJar` 补上 `--unpatched` 仍是待办。**

结果:ModLauncher 组 10/11 通过(1.21.1/1.21.3/1.21.4/1.21.6/1.21.7/1.21.8 与四条 1.20.x),
**唯一红线是 1.21**(它需要先把载荷的 SRG 名改写成官方名;旧绿预备 jar 含 SRG 名的类 4 个,今天重建的 346 个)。

### 15 条线全部通过(26.2 按客户端拥有者要求暂停)

1.21 转绿的三步都有实测判据:①原来的 `work\_diag-srg\joined-1.21.tsrg` 里 `m_`/`f_` 名 0 个,它不是
MCPConfig 的 obf→srg 表,用 `curl -6` 取回 `mcp_config-1.21.zip` 的 `config/joined.tsrg`(6,484,520 B,
53,985 个 `m_` 名)才是对的;②换对表后 `SrgRemap` 报 rewrote 3532/1537、0 未解析、0 残留 SRG 字符串,
预备 jar 含 SRG 名的类 346 → 1(旧绿 jar 是 4);③`FieldLocatorName` 的递归修复作为**步骤**加进
`build-jars.ps1` —— 没有它时该线能起来但 stderr 是 46767,施加后精确回到 14141 = 记录值。

四条 FML 10 线(1.21.9/1.21.10/1.21.11/26.1.2)与十一条 ModLauncher 线的四项检查全部通过
(1.20.6 的 17856 是 MATRIX §十三 已记录的差异)。**仍未发布**:每条线的真机建存档 + 光影 + FXAA 取证
需要独占窗口(上一轮有效证据是 9 条线,且不是本轮重编后的 jar),出 release jar 也还没做。

### 最终两轮四检:15 条线全部通过

ModLauncher 11/11:1.21 = 14141、1.21.1/1.21.3/1.21.4/1.21.6/1.21.7/1.21.8 = 0、1.20.1 = 0、
1.20.2 = 14625、1.20.4 = 14481(均 = 记录值);1.20.6 = 17856(MATRIX §十三 已记录的差异)。
FML 10 4/4:1.21.9 = 0、1.21.10 = 0、1.21.11 = 107、26.1.2 = 107(载荷/自有类重建后测试)。
15 个 release jar 已按当前分支头重出并核对合格。**发布前只剩真机建存档 + 光影 + FXAA 取证(需独占窗口)。**

### FXAA/建存档取证(独占窗口)第一轮:只有 1 条线有效

1.21.9 = **VISIBLE**(同场景,边缘能量 -4.7%、硬边 -6.1%);1.21.10 与 1.21.11 的成对帧**不是同一场景**
(工具判 INCONCLUSIVE / 关帧 336 KB 对开帧 760 KB),判定无效;其余 12 条**在启动阶段就失败**,
确证原因是 JDK 不匹配(`Unrecognized option: --sun-misc-unsafe-memory-access=allow`,26.x 需要 JDK 25,
1.20.1/1.20.2/1.20.4 需要 17,多数线 21 —— 权威值在 retest-all.ps1 的 java 列)。取证脚本本身接受
`-JavaHome`,下一轮按该列逐线传;并且相机读回必须用**选中的真实存档名**(本轮外层传了假存档名,
"同一场景"这个前提没被独立验证)。**发布门槛因此仍未满足。**

### 1.20.x 四条精确等于记录值(1.20.6 的 17856 → 0)

复验:`1.20.6 = 0`、`1.20.4 = 14481`、`1.20.2 = 14625`、`1.20.1 = 0`,全部 = 记录值 —— 1.20.6 长期挂着的
17856 字节噪声被 `FieldLocatorNullGuardRepair`(对 1.20.x 的 OptiFine jar 同样适用)清掉。至此 15 条线
**全部与记录值一致**。但这次施加是**并发换 `tools-classpath.txt` 造成的偶然**(120x 的工具 jar 不含该类、
1.21.x 的含),下一轮必须把它变成刻意步骤(把该类移植进 120x,或让 120x 重建固定用含它的工具 jar),
并让 `build-jars` 对 1.20.x 也正式打印结果;多个重建任务同时改写该文件会互相踩,必须串行。
发布门槛仍差真机建存档 + 光影 + FXAA 取证(目前仅 1.21.9 有效)。

### 1.21 进世界即崩:载荷侧仍有 SRG 名残留(四检到不了的地方)

`NoSuchMethodError: ServerLevel.m_7654_()`(SRG 名,运行期是官方名)于 `ChunkMap.<init>(ChunkMap.java:177)`,
进世界时服务端线程崩(`crash-2026-09-28_06.56.07-server.txt`)。逐类扫描:预备 OptiFine jar 已无残留,但
registered jar 里的 `optifineoforge/patched/.../ChunkMap.class`、`ChunkMap$TrackedEntity.class`、
`PacketUtils.class` 仍引用该名 —— 即加载期 `srg-to-official.txt` 没有覆盖这一族成员。下一轮:核对嵌入表是否
含 `m_7654_`,用正确的 `mcp_config-1.21/config/joined.tsrg` 重生成表并考虑重生成载荷,再重跑进世界测试。
**更正**:此前说的"1.21 通过"只指四项检查(到标题界面)且 stderr 回到 14141,**不等于可用**。

### 进世界 + 光影 + FXAA 逐线取证(14 条):9 条 VISIBLE,1.21 确诊缺陷,5 条须重跑

VISIBLE(同场景、已进世界):1.20.1 −4.4%、1.20.2 −2.9%、1.20.4 −4.1%、1.20.6 −4.6%、1.21.4 −2.1%、
1.21.8 −8.3%、1.21.9 −4.7%、1.21.10 −3.3%、1.21.11 −5.5%。
**1.21:进世界即崩**(`NoSuchMethodError ServerLevel.m_7654_`,加载期 `srg-to-official.txt` 缺该成员)。
1.21.1 / 1.21.6 / 1.21.7 判 NOT VISIBLE 但**没进世界**,1.21.3 判 REVERSED 且非同一场景,26.1.2 无帧 ——
这 5 条**必须重跑**,其判定不作结论(`player joined: yes` 是成对帧有效的前提)。

### 2026-10-01 撤回 OptiNeoForge 改名(已完成)与 1.21.4 进世界取证

撤回改名:包/类/文本/无扩展名服务文件全部回到 `optifineoforge` / `OptifiNeoforge`,GitHub 仓库名改回
`Kynarain/OptifiNeoforge`,五个挂载点编译通过;保留 `$init$` 标记式初始化器识别(改名根因的正经修复)。
撤回后全量四检 15 条线全部与记录值一致(1.20.6 回到文档记录的 17856)。

1.21.4 用户报告"区块整块不渲染":F3 读数 `C: 0/15000`(对照 1.21.8 = `222/6936`),玩家 `XYZ 0/0/0`。
但两存档同种子且 (0,0) 附近区块在**两条线上都是 ~190 字节的空区块**,而 1.21.4 没有 `playerdata`、
只能按出生点现造玩家落在空区块上 —— 所以**当前证据指向存档/出生点,而不是渲染器**,尚未定性。
同期抓屏返回过字节完全相同的陈旧帧,因此那次"改出生点"的实验不作结论。
**撤回**:改名后所有线的进世界/光影/FXAA 证据随 jar 变更作废,该门槛重新归零,发布仍然不做。

### 1.21.4 定性:区块生成/调度管线停滞(不是渲染器、也不是存档)

线索链:出生点改到与 1.21.8 玩家同一区域(同种子)后,存档出现新的 `r.52.5.mca`(玩家确实到过、确实
尝试生成),但**已生成区块数 49 vs 352**(1.21.8),且日志显示 `Preparing spawn area: 51%` 反复不动、
2 秒后服务器放弃进场;停滞后的 jstack 显示 `Worker-Main-*` **全部空闲**、Server thread 只在正常 tick 等待。
即区块从未被生成 → 客户端没有可渲染之物(F3 `C: 0/15000`、玩家落到 y=0、画面只剩天空与自己的手)。
**这是真缺陷**,归因于区块生成/调度管线;上一轮"指向存档/出生点"的判断已被本轮证据推翻。
下一轮:在加载**期间**抓 jstack,并对比 `ChunkMap`/`ChunkHolder`/`ChunkTaskDispatcher`/`ChunkStatus`/
`ServerChunkCache` 在载荷与运行期之间的成员差异(找"载荷缺、运行期有、计划却没恢复"的成员)后修复。

#### 1.21.4 补充测量(加载期线程栈)

加载期间抓栈:唯一在做世界生成的是 `Worker-Main-14`,栈顶为
`JigsawPlacement$Placer.tryPlacingChildren → JigsawPlacement.addPieces → ChunkGenerator.tryGenerateStructure`;
约 20~30 秒后再抓两次,该帧已消失且线程 CPU 时间不再增长 → **不是死循环**,只是当时在生成结构。
结合出生点准备卡 51% 后被判超时、以及同区域已生成区块 49 vs 352(1.21.8),准确表述为:
**世界生成/区块完成度异常缓慢或部分停滞**,导致玩家周围区块长期未完成、客户端无内容可渲染。
方向收敛到 worldgen/结构生成/区块状态完成链,不是渲染器、不是存档或出生点。

#### 1.21.4:注入成空实现的 `ListTag.add(Object)` 静默丢数据(真缺陷)

1.21.4 运行期 `ListTag extends CollectionTag`,**没有** `add(Object)Z`(1.21.8 的 `ListTag extends
java.util.AbstractList` 则继承到可用实现);而我们的计划给 1.21.4 注入了一条
`net/minecraft/nbt/ListTag add (Ljava/lang/Object;)Z` 的 **空 stub**(返回 false、什么都不加)。
于是经该桥接方法追加的列表元素被**静默丢弃**。实测吻合:该线存档的玩家记录里 `Pos`/`Rotation`
都是**空列表**,玩家因此没有位置、每次被放到 (0,0,0);叠加"原点区域旧存档本身为空",画面就只剩天空
(4.5 分钟后 `C:` 仍为 0,符合空世界无可渲染内容)。
**结论**:这不是渲染器缺陷,而是"空实现 stub 丢数据"的代码缺陷 + 一份旧的坏存档。
**修法**:给 stub 机制增加"委托式 stub"能力(例如 `return addTag(size(), (Tag) arg)`),修完后验证
`Pos`/`Rotation` 恢复正常,并在**有地形的坐标/新世界**里复测(原点坏区块不能当判据)。

### 修复并验证:1.21.4 的 `ListTag.add(Object)` 空 stub → 委托式 stub

`src/ml11/.../PatchedClassTransformer.java` 的 `stubMissing` 现在会先尝试生成**委托式**方法体。
唯一命中项 `net/minecraft/nbt/ListTag.add(Ljava/lang/Object;)Z` 生成为 `return addTag(size(), (Tag) arg);`。
三条独立证据:(1) 运行期日志出现 `Delegated stub ...` 且旧的 `Stubbed` 行消失;(2) 同一世界的自动保存里
`Pos`/`Rotation` 由 `list<0> []` 恢复为 `list<6> [0,0,0]` / `list<5> [0,0]`;(3) 1.21.4/1.21.3/1.21.1
重建后四检信号不变(STARTED + Setting user + Sound engine started)。
范围:1.21.1/1.21.3 的日志里没有 ListTag 相关 stub 行,故该缺陷**实测只在 1.21.4 成立**。
注意:该旧存档原点区域本身是空的,判断 1.21.4 的进世界渲染必须**新建世界**;120x 分支有同样的空 stub
机制但今天没有线需要它,故未改动。

### 更正:1.21.4 的新世界生成完全正常

新建存档(同种子、无旧区块)运行后,出生点四个区域文件为 **3.9 MB / 3.7 MB / 3.6 MB / 3.5 MB**,
地形被完整生成,与旧存档那批 ~900 KB 的空区块区域形成对照。故此前"世界生成缓慢或部分停滞"的表述
**作废**:`Preparing spawn area: 51%` + `Time elapsed` 是原版准备循环的正常日志形态。
1.21.4 的"只有天空"因此完整解释为:① 空实现 stub 导致玩家 `Pos`/`Rotation` 变空列表(已修);
② 玩家落到 (0,0,0),而旧存档原点区域本身是早前坏掉时期留下的空区块。**均非渲染器/worldgen 缺陷。**
遗留:本次取证脚本窗口识别失败(`window title: none found`)未拍到帧,新世界的截图证据下一轮补。

### 1.21.4 新世界取证:已确认与未确认

已确认:修复后 `playerdata` 的 `Pos`/`Rotation` 是规整列表(`list<6>`/`list<5>`,修复前为空列表),
`pin-save-state` 能读写;新世界 `region` 四个区域文件 3.9/3.7/3.6/3.5 MB(完整地形);
PrintWindow 抓到的帧是深色方块面(与"玩家在 y=0 地下"一致)。
未确认:F3 注投在 1.21.4 上时灵时不灵,拿不到 `C:` 读数;把出生点设为 (0,150,0) 并删除 `playerdata` 后,
玩家仍停在 (0,0,0)(帧字节数与上一张完全相同),此推断不成立、原因未查清;
`run-fxaa-capture.ps1` 自身的窗口/进程识别失败(`window title: none found`、`no client matched ...`),
而分步手动取证是成功的 —— 问题在脚本匹配逻辑,下一轮先修它,再拿新世界的 `C:` 与地貌截图。

### 卡点定位:rig 的 pin-save-state 不认 level.dat 的 Player 标签(造成假的"渲染问题")

`pin-save-state.ps1` 只在存在 `playerdata/*.dat` 时才钉玩家位置,并直接打印
"no playerdata - player position not pinned (the client then spawns a fresh player at SpawnX/Y/Z)"。
但存档 `level.dat` 里**可能带 `Player` 复合标签**(从模板复制而来),其中的 `Pos [0,0,0]` 会**压过**
SpawnX/Y/Z,使玩家固定落在 (0,0,0) —— 在正常世界里那是地下,画面只有深色石头。
1.21.4 那个"渲染问题"正是这个:修复后 `Player` 标签里的 `Pos`/`Rotation` 已是规整列表,但值来自
坏构建时期写下的 `(0,0,0)`,又被新世界模板继承。
因此**在修好 rig 之前,任何"进世界看画面"的结论都不可信**。修法:无 playerdata 时若有 `Player` 标签
则警告并把 -PlayerX/Y/Z/-Yaw/-Pitch 写进去、增加清标签开关、建新世界默认不继承该标签、Dump 标明来源。

### rig 钉档缺陷已修 + 1.21.4 新世界完整渲染(证据)

`pin-save-state.ps1`:① `Set-Fixed` 补 `double` 分支(原本缺它,`Pos` 是 `list<double>[3]`,
所以**位置钉档从未成功过**);② 无 playerdata 时回退写 `level.dat` 的 `Player` 标签的 `Pos`/`Rotation`;
③ `-Dump` 明确标注该标签存在且**压过 SpawnX/Y/Z**;④ 提示语改为准确说法。
验证:副本上 `level.dat Player.Pos[1]: 0 -> 150`,re-dump 得 `Pos: list<6> [0,150,0]`。
把新世界 `RigFresh` 的该标签钉到 (0,150,0) 后抓帧(`logs\final-1.21.4-fresh.png`,274 KB):
森林、水面、地形起伏、远景雾完整呈现(对照此前 57,940 B 的地下石头帧)。
结论:1.21.4 的"区块整块不渲染" = 空实现 stub 致玩家 Pos/Rotation 为空 + 模板 level.dat 的 Player 标签
把玩家按在地下 + 旧存档原点为空区块;既非渲染器缺陷,也非 worldgen 缺陷。

### 新增 rig 工具 `capture-frame.ps1`:单线单帧进世界取证

`run-fxaa-capture.ps1` 需要自己的进程/窗口簿记对齐,曾两次报 `window title: none found`;而同一套查询
手工再跑能正好命中(竞态而非能力缺失)。新工具按已证明可靠的步骤做:启动(可 quickPlay 存档)→ 只看新写入的
`latest.log` 等 `joined the game` → **带重试**找本线游戏窗口 → 可选投 F3 → `PrintWindow` 抓帧 → 只收尾本线客户端。
在 1.21.4 + 新世界 `RigFresh` 上验证通过:帧 213,069 B,内容为海洋/陆地/树/远景雾的完整地貌。
已知限制:F3 调试屏仍未生效(工具不依赖它);`PrintWindow` 看不到 FXAA 合成画面,故 FXAA 判定仍走
`run-fxaa-capture.ps1` 的 F2 路径 + `fxaa-check.ps1`。

### capture-frame.ps1 跨线验证(1.21.8)

新建 `RigFresh`(仅复制 level.dat + 钉玩家 (26887,150,2618))后:`joined the game` → 窗口命中 →
`logs\capture-frame-1.21.8.png`(110,712 B)显示雪原/树/水面/手持方块,地形正常渲染。
说明该工作流可用且可推广;同时 1.21.8 的模板 level.dat **同样带 `Player` 标签**,即 rig 的钉档缺陷
此前影响的是**每一条线**,不只是 1.21.4。

### 进世界取证链 + 复现 1.20.2 缺陷

新增 `capture-frame.ps1`(单线:启动→等 joined the game→重试找窗口→可选 F3→PrintWindow 抓帧→只收尾本线客户端;
含 `-FreshWorld` 建新世界、`-JavaHome`、以及**补上 `natives-for.ps1` 调用**:1.20.1–1.20.4 → LWJGL 3.3.2,
其余 3.3.3,缺它 1.20.x 客户端起不到世界)与 `capture-all-lines.ps1`(逐线跑 + 台账 `logs\inworld-sweep.txt`,
按行合并、`-Only` 支持逗号列表)。1.21.4/1.21.8 已验证拿到完整地貌帧。

**1.20.2 在真实建世界路径上复现失败**(jar 与权威表一致、natives 3.3.2、Java 17):

```
java.lang.NoClassDefFoundError: net/minecraft/world/level/block/state/BlockState
  at net.optifine.reflect.ReflectorMethod.getMethod(ReflectorMethod.java:238)
  at net.minecraft.client.renderer.GameRenderer.frameInit(GameRenderer.java:1819)
```

即 120x 的 loader **不消费 runtime-interfaces 计划**,OptiFine 反射所需的 `BlockState` 成员在运行期不存在;
四检能过只是因为它只到标题界面。下一轮修 `src/main` 的 `PatchedClassTransformer` 补上这条链路。

### 校正:1.20.2 的"建世界崩溃"在当前构建上未复现

120x 的 loader 确有 runtime-interfaces 机制(日志可见 `Injected 1 runtime interface(s) on ... BlockState`,
jar 含 `optifineoforge/runtime-interfaces.txt`,1.20.2 列出 `BlockState → IBlockStateExtension`)。
用权威表的 jar + LWJGL 3.3.2 + Java 17 + `--quickPlaySingleplayer` 建世界:
`STARTED / Setting user True / Sound engine started True / 崩溃 0 / stderr 14625`(等于记录值),
进世界取证亦成功(`logs\inworld\frame-1.20.2.png`,地形在渲染)。
日志里的 `NoClassDefFoundError: BlockState`(OptiFine `ReflectorMethod.getMethod ← GameRenderer.frameInit`)
是**被反射器捕获后继续执行**的,属于该线 stderr 记录的组成部分,不打断建世界 → 不再当缺陷。
另:`capture-frame.ps1` 修掉两个自身 bug(`Start-Process` 参数不加引号导致 launch 立即退出且无日志;
新世界 donor 未排除目标名导致复制源被删)。

### 真缺陷:1.21 建世界崩溃 —— 载入期 SRG 表漏名(已定位到数字)

自建新世界时 1.21 直接崩溃:`NoSuchMethodError: ServerLevel.m_7654_() (=getServer)`,栈为
`ChunkMap.<init> → ServerChunkCache.<init> → ServerLevel.<init> → MinecraftServer.createLevels → IntegratedServer.initServer`。
实测:jar 内 `optifineoforge/srg-to-official.txt` **8,391 行、含 `m_7654_` 的行 0**;而
`downloads\mcp_config-1.21\config\joined.tsrg` 里 **存在** `o ()Lnet/minecraft/server/MinecraftServer; m_7654_ 8870`。
生成者是 `add-line.ps1:160` 调用的 `SrgNameTable <joinedTsrg> <obfOfficial> <srgPayload> <outTable>`。
→ 载入期重命名表漏掉载荷 `ChunkMap` 实际引用的 `m_7654_`,载荷里的调用未被改写,运行期找不到方法。
修法:SrgNameTable 输出改为并集(补上 joined.tsrg 中属于载荷 owner 的全部 m_/f_ 名字,或发现缺失即补齐并报数),
重建 1.21 后复查表内含 `m_7654_` 并重新建世界取证。另:1.20.4 本轮同样未进世界(NO-JOIN,无新崩溃报告),需单独查。

### 进世界台账(11 条 ModLauncher 线)与 SRG 表修复状态

台账(`logs\inworld-sweep.txt`):1.20.1 ✅ 209,433 B;1.20.2 ✅ 40,475 B;1.20.4 ❌ NO-JOIN;1.20.6 ✅ 1,186,484 B;
1.21 ❌(已定位 `NoSuchMethodError ServerLevel.m_7654_`,建世界崩溃);1.21.1 ❌;1.21.3 ❌;1.21.4 ✅ 226,680 B;
1.21.6 ✅ 181,074 B;1.21.7 ❌;1.21.8(此前已验证 ✅ 176,475 B)。1.20.4 与 1.21 的 stderr 以
`NoClassDefFoundError: BlockState` 开头(记录噪声),而 1.21.1/1.21.3/1.21.7 的 stderr 没有这类错误 → 失败集非单一原因。
`SrgNameTable` 已改为**沿父类链上溯解析**并把条目写在调用点 owner 下;直接运行验证成功(产物含
`net/minecraft/server/level/ServerLevel  m_7654_  getServer`),但走 `add-line.ps1` 流水线重建后内嵌表仍 8,391 行、
`m_7654_` 为 0,且工具输出无"resolved through a supertype"。下一步:逐字复现流水线的 SrgNameTable 调用
(它用的工具 jar 与载荷路径),对齐后再重建复验。

### 1.21 SRG 表:修掉"喂错表",但仍不充分

`add-line.ps1:155` 曾无条件用 `work\<mc>\obf-official.tsrg` 覆盖调用者的 `-ObfOfficial`;而该副本与
`obf-official-<mc>.tsrg` 虽同为 119,595 行/3,996,776 B,**有 151 行不同**。交叉实验:work 副本 → 1,074 个类配不上、
表 8,391 行且无 `m_7654_`;rig 根副本 → 0 个类配不上、表 8,463 行且含 `m_7654_`。已改为优先调用者参数、
其次 `obf-official-<mc>.tsrg`,重建后内嵌表确认为 8,463 行并含 `ServerLevel m_7654_ getServer`。
**但 1.21 仍未进世界,且崩溃前移到初始化期**(`crash-2026-10-01_06.10.37-client.txt`):
`AbstractMethodError` 于 `RenderSystem$AutoStorageIndexBuffer.m_157476_`(lambda 接收者)→ 说明仍有 SRG 名未被改写,
工具自报**还有 3,160 个引用无法解析**。下一轮:给 SrgNameTable 加**运行期回退**(按同类/父类上描述符相同的成员反查官方名,
复用 SrgMemberMap 的 RuntimeIndex),把无法解析数压到近 0,再重建复验。

### SrgNameTable 运行期回退已实现(收益可量测),1.21 仍崩 → 缺口在改写覆盖面

`SrgRemap.resolve` 开放给同包,`SrgNameTable` 接受额外运行期 jar,用 `SrgMemberMap.RuntimeIndex`(含 JDK 索引)
按"运行期同类/父类/接口上描述符相同且确实声明"解析;`add-line.ps1` 传入 `work\<mc>\runtime-<mc>.jar`。
量测(1.21):无法解析 **3,160 → 568**,973 条经父类解析,表 **8,463 → 9,442 行**,内嵌表含
`ServerLevel m_7654_ getServer` 与 `RenderSystem$AutoStorageIndexBuffer m_157476_ ensureStorage`。
但 1.21 仍在同一处崩:`AbstractMethodError` 于
`RenderSystem$AutoStorageIndexBuffer.m_157476_`(lambda 接收者实现的是官方名,调用点仍是 SRG 名)——
**表里有名字、调用点没被改写**,最可能是 `invokedynamic` 的引导方法句柄/`Type` 常量不在现有改写范围内。
下一轮:扩展改写覆盖面到 `InvokeDynamicInsnNode` 引导参数与 `LdcInsnNode` 的 Handle/Type,重建复验后再覆盖
1.21.1/1.21.3/1.21.7/1.20.4。

### 更正与机制定位:1.21 的 AbstractMethodError 源自"只改引用、不改声明"

更正:上一轮猜测"invokedynamic 句柄不在改写范围"**不成立** —— `renameSrgMembers` 已处理
`InvokeDynamicInsnNode.bsmArgs` 中的 `Handle`。实测机制:表里**有** `RenderSystem$AutoStorageIndexBuffer.m_157476_
→ ensureStorage`,但改写前会经 `declaredByInstalledPayload()` 检查"载荷自己的类是否声明了该名字";
实测载荷类**声明了** `m_157476_`(该文件仍有 35 行 SRG 名),于是走 `kept` 分支**故意不改**。
该规则有历史原因:早期连声明一起改,使 1.21 的 `[OptiFine]` 行从 299 变 0 并死在 OptiFine `Reflector.<clinit>`
(OptiFine 自己的类也用同样的 m_/f_ 形状命名成员)。于是出现不一致:载荷保留 SRG 名,运行期接口名为 ensureStorage,
lambda 接收者按官方名实现 → AbstractMethodError。
**定向修法(下一轮)**:只对"被补丁过的游戏类"(`net/minecraft/**`、`com/mojang/**`,排除 `net/optifine/**` 与 keep 计划中的类)
把声明与调用点**一起**改名,使类内部自洽且与运行期接口名一致;用 `-Doptifineoforge.dump` 验证载入期真身后复跑建世界。

### 声明改名尝试失败并已回退(1.21)

尝试让载入期改名"连声明一起改"(仅非 net/optifine 类, 判据改为"载荷同时声明 SRG 名与官方名才保留"), 结果:
第一次改到自注入的 stub(MinecraftServer.m_195518_ 等) → 死在 BuiltInRegistries.<clinit>; 加 stub 排除后
第二次仍失败: `IllegalArgumentException: Not bootstrapped`(Bootstrap.checkBootstrapCalled)。该尝试让 1.21 从
"能到标题界面"退化为"无法启动", 属明确退步, 故本轮未提交的 loader 改动**已整体回退**。
保留本轮已提交且有量测收益的部分: SrgNameTable 运行期回退(无法解析 3160→568、表 8463→9442 行、含 m_7654_ 与
m_157476_)与 add-line.ps1 的 obf-official 修正。注意 rig 的 jars-1.21 registered jar 仍是退步构建的产物,
下一轮需从回退后的源码重建。下一轮改更窄: 只改"运行期以官方名声明且描述符一致"的**方法**(不碰字段),
或只改"载荷类实现/覆盖运行期接口方法"的那些方法, 并用 -Doptifineoforge.dump 验证。

### 更正归因:让 1.21 退步的是表的扩容(运行期回退),不是声明改名

回退"声明改名"后用干净源码重建(表仍为扩容后的 9,442 行)跑四检: `VERDICT: EXITED`、Setting user False、
Sound engine False、1 份新崩溃(crash-2026-10-01_06.41.40-client.txt)、stderr 8605 B —— **标题界面这一关也过不去了**
(表 8,391 行时是过的)。stderr 大小与 06:32 那次 `Not bootstrapped` 一致,说明问题在载入期改名本身。
归因更正:主因是**新加进表的 1,051 个名字**(运行期回退产生,在引用侧生效)。最可能的具体原因:回退对**字段**
用"同类上描述符相同"反查,键太弱(同类同类型字段常有多个) → 可能改到错误的官方名,破坏 `Bootstrap` 这类状态字段。
下一轮顺序:① 先恢复基线(表退回不传运行期 jar 的版本,重建确认四检回到 STARTED,并给 add-line.ps1 加 `-NoRuntimeTable`
开关);② 让运行期回退只作用于方法、或要求"同类同描述符候选唯一";③ 基线稳住后再回到 1.21 建世界(m_7654_/m_157476_)。
另:本轮 pwsh-624 被作业运行器以 4294967295 终止,无结论。

### 基线恢复实况 + 新错误:ClassFormatError Duplicate method name

回退未验证的 loader 改动;给 add-line.ps1 加 `-RuntimeTable` 开关(默认关);修掉我自己引入的
"SrgNameTable 只走 SrgRemap.resolve" 问题(没有运行期索引时现在回退到直接查表,否则表会是 0 行)。
重建(默认开关)后内嵌表为 **8,463 行含 m_7654_**(因先前已改用正确的 obf-official 表,故不是旧版 8,391 行)。
用这份表跑四检,客户端死于 `java.lang.ClassFormatError: Duplicate method name "get" with signature
"(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"` —— **改名制造了同名同描述符的方法**。
对照:8,391 行(陈旧表)能过标题界面但建世界崩(m_7654_ 缺失);9,442 行(+运行期回退)更早失败(Not bootstrapped)。
下一轮:① 定位冲突产生者(离线 SrgRemap 复用了旧产物 vs 载入期改名;用强制重新 prepare + javap/-Doptifineoforge.dump 检查);
② 给改名加"不制造冲突"判据(改名 X→Y 前检查该类是否已存在 Y(带描述符),存在则不改并计数)。

### ClassFormatError Duplicate method "get" 定位到类与机制

报错类为 `net/minecraft/world/level/block/entity/BlockEntity$DataComponentInput`(接口,jar 内只有
`get(DataComponentType)` 与 `getOrDefault(DataComponentType,Object)`,**无重复**);调用者是 OptiFine 自己的
`srg/net/optifine/reflect/FieldLocatorTypes.<init>`(getDeclaredFields),且发生在 `CrashReport.preload`,
故 new crash reports 为 0。而**成员恢复计划**对同一个类要求恢复
`get (Ljava/util/function/Supplier;)Ljava/lang/Object;` 与 `getOrDefault (…Supplier…)`(donor 的 Supplier 形状);
报错里的重复签名是载荷自己的 `DataComponentType` 形状 → **恢复机制在同名成员已存在时仍往里加**。
下一轮:用 `-Doptifineoforge.dump` 导出载入期真身,确认该类被定义时有几个 get 及是哪一步加的;
然后给恢复机制加"写入前按名字+描述符查重,已存在则跳过并计数"(与"改名不能制造冲突"同族约束)。

### dump 实证:同一成员被写入两次(写入点已缩小)

用 `-Doptifineoforge.dump` 导出载入期真身,`BlockEntity$DataComponentInput.class` 里
`get(DataComponentType)` 与 `getOrDefault(DataComponentType,Object)` **各出现两次**(同名同描述符),
而 jar 内该文件是合法的(只有两条)→ 载入期有写入点重复添加。已排查:`PatchedClassTransformer:600-624`(有
`hasMethod` 查重 ✓)、`MemberRestoreTransformer:140`(✓)、stub 路径(✓)。
待查:**`PatchedClassTransformer:714` 的 `input.methods.add(created)`**、接口注入路径、
以及 `ReloadableResourceManagerFix:77/115`、`RenderTargetFix:79`、`TagHelperFix:82`。
下一轮修法统一为:任何 `methods.add`/`fields.add` 之前按"名字+描述符"查重,已存在则跳过并计数。

### 干净 A/B:声明改名是 ClassFormatError/Not bootstrapped 的元凶;表修正无害

上一轮的"回退"因 `git add -A` 连带把声明改名提交进了 94f2de7;本轮从 79dbaf5 取回该文件(确认其中无
`Renamed … method declaration`、无 `int declared`),重编重建(表仍 8,463 行含 m_7654_)后跑四检:
`ClassFormatError: Duplicate method name "get"` **消失**,客户端前进到 `AbstractMethodError:
RenderSystem$AutoStorageIndexBuffer.m_157476_`(LevelRenderer.createStars);该类的日志由四个 pass 变为三个。
结论:① 声明改名是 Duplicate method name 与 Not bootstrapped 的元凶,已彻底移除并固化;② 表修正(8,463 行含
`ServerLevel m_7654_ getServer`)无害且必要(旧 8,391 行表正因缺它而在建世界时崩);③ 1.21 现在停在老问题上:
lambda 接收者实现官方名 ensureStorage 而调用点仍是 m_157476_。
下一轮重做声明改名时必须带"名字+描述符查重"约束,且验收顺序固定:先四检 STARTED,再看 createStars,最后建世界。

### 带查重的声明改名:一半成功,缺口缩小到一个内部接口

`renameSrgMembers` 重新加入**仅方法**的声明改名,并补上缺的约束:**改建名前查 `hasMethod(node, official, desc)`,
已有同名同描述符则不改并计数**;适用范围仍为非 net/optifine、跳过 stub;引用侧改用 `declaredBothNames`。
结果:`ClassFormatError: Duplicate method name "get"` 消失;四检 `Setting user: True` 回归;崩溃栈里出现官方名
(`ensureStorage`/`bind`)。仍崩于 `LevelRenderer.createStars` → `AbstractMethodError`,报错点名:
`…does not define or inherit … 'abstract void accept(it.unimi.dsi.fastutil.ints.IntConsumer, int)' of interface
…RenderSystem$AutoStorageIndexBuffer$IndexGenerator`。jar 内两份副本:donors 是 `accept`(官方名),
patched 是 `m_157487_`(SRG 名);而日志显示该内部接口被 "Left … alone(保留运行期样子)" →
载荷里按 m_157487_ 实现的 lambda 与已改名为 accept 的调用点不一致。
下一轮择一:①不保留该接口(让载荷副本进来并把 m_157487_ 改名为 accept,查重机制已就绪);
②保留接口同时把引用侧与 lambda 句柄一并改名。验收顺序固定:Setting user → Sound engine → createStars → 进世界。

### 1.21 通了四检并进入世界!(invokedynamic 名字改写是关键)

在 `renameSrgMembers` 的 invokedynamic 分支补上**对 indy 自身 name 的改名**(indy 的 name 就是函数式接口的方法名,
接口是描述符返回类型;此前只改了 bsmArgs 的 Handle)。结果:Setting user True、**Sound engine started True**、
`createStars` 的 AbstractMethodError 消失、进世界日志出现 **`joined the game`**。
剩余缺陷(更靠后):进世界创建渲染区块时
`NoSuchFieldError: SectionRenderDispatcher$RenderSection does not have member field 'net.optifine.render.ChunkLayerMap …'`
于 `RenderSection.<init>(:517)` ← `ViewArea.createSections` ← `LevelRenderer.allChanged/setLevel` ← `handleLogin`。
即载荷的 `RenderSection.<init>` 要写一个已安装类里没有的字段(载荷的 ChunkLayerMap vs 运行期的 Map)。
下一轮:把载荷的字段一并带给已安装类,或改为保留整类;并顺手修 capture-frame 的窗口标题匹配。

### 1.21 全线打通:四检通过 + 进入世界 + 抓帧成功

最后缺陷:进世界时 `NoSuchFieldError: SectionRenderDispatcher$RenderSection does not have member field
'net.optifine.render.ChunkLayerMap …'`(于 `RenderSection.<init>` ← handleLogin)。jar 内 patched 副本的字段声明是 SRG 名
`f_291754_`,而 donors 是 `buffers`;根因是我把**字段引用**的保留判据从 `declaredByInstalledPayload` 放宽成
`declaredBothNames`,于是引用被改成 `buffers` 而声明仍 `f_291754_`(字段声明从不改名)。
修法:字段引用恢复旧判据,方法引用继续用 `declaredBothNames`。
结果:Setting user True(07:23:56)、Sound engine started True(07:24:02)、`Dev joined the game`(07:24:09)、
无新崩溃报告、抓帧成功 `logs\inworld\frame-1.21.png`(178,723 B,海岸线/地形/树木/手持物品)。
走通路径供其余线复用:①obf-official 表取值修正(表 8,463 行含 m_7654_);②仅方法的声明改名 + 改名前的"名+描述符"查重;
③invokedynamic 自身 name 的改名;④字段引用不改名。
下一轮:推广到 1.21.1/1.21.3/1.21.7(同走 SRG 机制、此前 NO-JOIN),再回到其余线与 FML10,然后进光影+FXAA。

### 1.21.1 / 1.21.3 / 1.21.7:四检皆过,进世界各有不同原因(与 SRG 无关)

纠正前提:rig 里没有这三条线的 obf-official/mcp_config,registered jar 里也**没有 `srg-to-official.txt`** ——
它们**本就不走 SRG 装载改名**,1.21 的修法不适用(与此前"stderr 里没有 SRG/CNFE 错误"一致)。
本轮按当前源码重建后实测:1.21.1 与 1.21.3 **四检全过**(STARTED/user True/sound True/0 崩溃/stderr 0),但进世界崩于
`IllegalStateException: Cannot get config value before config is loaded`(`ModConfigSpec$ConfigValue.getRaw`
← `Level.guardEntityTick(:581)` ← `ServerLevel.tick`),即"配置尚未加载就被读取";
1.21.7 无崩溃报告、stderr 0 B(客户端静默失败)。
下一轮:①1.21.1/1.21.3 用 `-Doptifineoforge.dump` 查 `Level.guardEntityTick` 是否被成员恢复替换或静态初始化未跟上;
②1.21.7 先查为何无日志;③三条通过后回到其余线与 FML10,最后进光影+FXAA。

### 1.21.1/1.21.3 的"配置未加载"崩在运行期自己的类里

1.21.1 的 registered jar 里既无 `optifineoforge/patched/…/Level.class` 也无 donor 副本 → `Level` 未被替换,
用的是 NeoForge 21.1.250 自己的类。故崩溃栈里的 `Level.guardEntityTick(:581)` 是 NeoForge 自身代码读取尚未加载的
`ModConfigSpec$ConfigValue`(`ConfigValue.getRaw` → `get`)。与可跑通的 1.21.4 对比:`[OptiFine]` 行数同为 232,
未见我们 loader 异常。判读:这更像**启动/加载顺序**问题(世界 tick 早于 NeoForge 载入配置),且与版本相关。
下一轮先做最快判别:去掉 `--quickPlaySingleplayer`,在 1.21.1 上手工"标题界面→单人游戏→进世界";
若不再崩则是 quickPlay 与配置时机的交互(rig 可用"先到标题界面再投键进世界"规避),否则再对照配置加载日志。
1.21.7 仍待查(无崩溃报告、stderr 0 B)。

### 更正:1.21.1/1.21.3 的配置未加载是次生错误

运行期 `Level.guardEntityTick` 字节码显示:`NeoForgeConfig.SERVER.removeErroringEntities` 的读取位于
**catch(Throwable) 的处理分支**(构造崩溃报告时),所以真正的错误是**某个实体 tick 抛出的异常**,被随后的
`IllegalStateException: Cannot get config value before config is loaded` 掩盖。崩溃报告未保留原异常。
下一步:①在 `logs\debug.log` 里找 07:33:0x 的原始异常(1.21.1 与 1.21.3 各一份);②若没有,用无实体世界复现缩小范围;
③1.21.7 仍待查(无崩溃报告、stderr 0 B)。

### 1.21.3 打通;1.21.1 的失败实为 rig 问题

对照 keep 计划:`keep-runtime-1.21.txt` 与 1.21.4 都有 `net/minecraft/client/server/IntegratedServer *`(当初为
"配置未加载"而加),而 1.21.1/1.21.3 没有。补上后重建:1.21.3 四检全过、`joined the game`、抓到帧
`logs\inworld\frame-1.21.3.png`(58,292 B),且实例 `config` 目录出现了 `neoforge-server.toml`(服务端配置终于加载)。
1.21.1 四检全过,但进世界那步是 **rig 自身报错**:`capture-frame.ps1` 调 `natives-for.ps1` 删旧 DLL 失败
(另一客户端占用 glfw.dll)被当成致命错误,客户端根本没启动,却被记成"没有帧"。已把该步骤改为 try/catch 容错。
下一步:重跑 1.21.1 取证;再查 1.21.7(无崩溃报告、stderr 0 B)。

### 1.21.1 与 1.21.7 打通进世界;11 条 ModLauncher 线只剩 1.20.4

1.21.1:补 `IntegratedServer` keep 后重跑(natives 容错修复后),Setting user/Sound engine 通过、config 出现
`neoforge-server.toml`、抓到帧 `logs\inworld\frame-1.21.1.png`(57,899 B)、无新崩溃。
1.21.7:失败为 `NoSuchMethodError: BlockEntity.gatherCapabilities()` 于区块生成
(`MonsterRoomFeature.place` → `WorldGenRegion.getBlockEntity`);把 1.21.4 的 `stub-additions` 复制为
`stub-additions-1.21.7.txt` 后,日志出现 `Stubbed …gatherCapabilities()V`、`joined the game`、抓到帧(183,861 B)。
**更正**:`stub-additions-1.21.6.txt` 中"1.21.7 及以后不需要该 trio"的旧结论与今日实测相反。
当前 1.21/1.21.1/1.21.3/1.21.4/1.21.6/1.21.7/1.21.8 全部通过进世界门槛;ModLauncher 11 条只剩 1.20.4。
另:1.21.7 日志出现 OptiFine 自带 `post_effect/fxaa_of_2x.json`/`fxaa_of_4x.json` 解析失败(新版结构不匹配)——
非我方改写所致,但会在 FXAA 门槛阶段被如实记录。

### 1.20.4:用错管线导致构建失败;旧 jar 四检通过且 stderr 恰为 14481

用 `add-line.ps1`(1.21.x 检出)重建 1.20.4 时构建失败:`Gui.drawBackdrop` 成员校验 payload 2 / runtime 4
(`net/minecraft/client/gui/Gui drawBackdrop (Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;III)V 4 payload 2 runtime 4`)。
1.20.x 分支的正确管线是 `rebuild-120x-line.ps1`(面向 OptifiNeoforge-120x,接受 `-DropMembersFile`,而 `drop-members` 是
`build-jars.ps1` 的参数)。既有 jar 的实况:四检 Setting user(08:08:15)/Sound engine started(08:08:20)通过,
**stderr = 14481 B,恰等于 retest-all.ps1 记录的目标值**(印证此前 17841 系 rig natives 选择问题);
但进世界 NO-JOIN、无窗口,且该次运行未更新 latest.log(客户端没正常起来)。
下一轮:①用 rebuild-120x-line.ps1 正确重建;②查客户端未启动原因;③再跑 FML10 四线进世界,然后进光影+FXAA。

### 1.20.4 正确管线重建成功;进世界客户端启动即死、零日志

`rebuild-120x-line.ps1 -Mc 1.20.4 -NeoForge 20.4.251 -ModLauncher 10 -SrgMappings … -ObfOfficial …
-DropMembersFile drop-members-1.20.4.txt -Repository I:\mods\OptifiNeoforge-120x` 重建成功:
`stubbed 18 members on {BakedModel=9, ModelBaker=2, BlockEntity=5, BlockState=1}`、`drop plan: 3 line(s)`、
jar `OptifiNeoforge-2.0.0+mc1.20.4-registered.jar`(1,745,403 B)、内嵌 `drop-members.txt` 6 行;
`srg-to-official.txt` 仍缺席(rig 根那份只有 2 行)。
四检:Setting user 08:18:06、Sound engine started 08:18:12、**stderr = 14481 B(与记录值一致)**。
进世界:实例日志停在验收运行时刻,说明该次 JVM **没写出任何日志**(不是抓不到帧,而是没起来);
同目录早前 FXAA 启动日志证明该线启动方式可行。rig 参数已核对(capture-all-lines 传 jdk17;capture-frame 为
1.20.x 选 LWJGL 3.3.2)。
下一轮:手工跑 `capture-frame.ps1 -VersionId neoforge-20.4.251 -Mc 1.20.4`,查零日志原因(命令行引号/参数拆分、
-FreshWorld 存档拷贝、natives try/catch 的影响)。

### 1.20.4 卡在资源重载;jar 未嵌入那 2 行 SRG 表

手工按 capture-all-lines 的原样参数跑 capture-frame(1.20.4/jdk17/natives 3.3.2):客户端确实启动(实例 latest.log
更新到 08:26:12),故此前"零日志"是竞态而非启动参数问题。此后走到 `Sound engine started`(08:26:10)即停在
`[OptiFine] *** Reloading custom textures ***` → `Disable Forge light pipeline` → `Replaced Font$DisplayMode/
StringRenderOutput/BitmapProvider$Glyph$1`(08:26:12 之后再无任何日志),窗口未出现、无 joined the game ——
即**资源重载阶段卡死**,与目标点名的"CustomItems.wait 不被 ModelBakery 构造函数释放"同族。
直接线索:rig 根 `srg-to-official-1.20.4.txt` 的两行(ModelPart.getChild、ModelBakery.loadBlockModel)未嵌入 jar,
载入期看不到碰撞项。下一轮:查 build-jars 取表路径并真正嵌入,再按四检→joined→抓帧复测;随后 FML10 四线与光影+FXAA。

### 1.20.4 卡死原因不是缺表;分歧在 ModelBakery 的碰撞保留

为 1.20.4 生成并嵌入了一直缺失的表(SrgNameTable → work\1.20.4\plan\srg-to-official.txt,13 行;
rebuild 输出 `SRG table: 13 name(s)`),但进世界**仍卡死**在 `[OptiFine] *** Reloading custom textures ***`,无窗口无 joined。
对照已修好的 1.21:其日志有 `Kept 3 SRG name(s) in net.minecraft.client.resources.model.ModelBakery: the copy this jar
installs declares …`(碰撞保留判据生效),而 1.20.4 **完全没有这条**(只有 `Replaced …(43 fields, 43 …)` 与
`Restored 15 members from its donor`);keep 计划两条线都没有 ModelBakery/CustomItems 条目,差异来自载荷形状与改名结果。
下一轮:查 1.20.4 的 `Rewrote N SRG name(s)`/`Renamed N method declaration(s)` 数字,用 -Doptifineoforge.dump 与 1.21 逐成员
对照,目标是把那对名字在 1.20.4 上同样保留,再复测四检→joined→抓帧。

### 1.20.4 卡死的结构性根因:1.20.x loader 没有载入期 SRG 改名这一套

证据:①1.20.4 全日志没有任何 `Kept N SRG name(s)`/`Rewrote`/`Renamed … declaration` 行(机器没跑),而 1.21 有很多
(GlStateManager 保 97、AutoStorageIndexBuffer 保 20、ModelBakery 保 3);②120x 检出的 loader 在
`src\main\…\PatchedClassTransformer.java`(ml10/ml11 仅各一个 ModLauncherAdapter),其中 `renameSrgMembers` 0 处、
`srg-to-official` 仅注释 1 处、无 `SRG_TABLE`/`officialName`/`declaredByInstalledPayload`;而 1.21.x 的 ml11 中
`SRG_TABLE` 在 1071 行、`officialName` 1108 行、`renameSrgMembers` 1130 行、`declaredByInstalledPayload` 1334 行,
调用点 773 行。结论:1.20.4 的资源重载卡死(CustomItems.wait 不被 ModelBakery 构造函数释放)既不是表未嵌入、
也不是 keep 名单,而是这条分支的 loader 缺少把 SRG 名改成官方名的逻辑。
下一轮:把这套逻辑(表加载、officialName、declaredNames/declaredByInstalledPayload/declaredBothNames/isStubName、
renameSrgMembers 及其三处已测约束:仅方法/非 net.optifine/跳过 stub/改建名前按名+描述符查重;字段引用走
declaredByInstalledPayload;indy 既改句柄也改自身 name)移植进 120x 的 src\main transformer,并在主流程按 1.21.x 的位置调用;
然后编译 120x → rebuild-120x-line 重建 1.20.4 → 四检 → joined → 抓帧;其余 1.20.x 线已通过,不要引入退步。

### 120x 分支移植载入期 SRG 改名(1.20.4 专用管线)

把 1.21.x ml11 的整套改名逻辑移植进 120x 的 `src\main\…\PatchedClassTransformer`:`SRG_TABLE`/`SRG_NAME`/
`SRG_NAMES`+`loadSrgNames`(417 行起)、`officialName`、`renameSrgMembers`、`declaredBothNames`(570)、
`isStubName`、`declaredByInstalledPayload`、`declaredNames`(590)、`PAYLOAD_DECLARATIONS`(621),
并在该文件全部 6 条交付路径的 `return finish(input);` 前调用(848/864/870/877/963/1089);
依赖已核对(PREFIX 74、STUBS_BY_OWNER 88、hasMethod 412、KEEP_RUNTIME_CLASSES 1147、RESTORED_CLASSES 1107)。
实测:编译与重建通过;1.20.4 日志首次出现 `SRG names to rewrite while transforming: 13 across 10 owner(s)`、
`Kept 4 SRG name(s) in …ModelBakery`、`Kept 18 … MultiBufferSource$BufferSource`、`Renamed 2 method declaration(s)
in …DebugScreenOverlay` 等。
rig 观察:先跑验收再立刻抓帧时,抓帧那次的客户端不写任何日志(无效结论);单独手工跑则能写日志(此前 08:26 那次即写到
`Reloading custom textures`)。抓帧前需给上一个客户端留出退出时间。
下一轮:单独手工跑 capture-frame 判定 1.20.4 资源重载是否走完并进世界;再回归 1.20.1/1.20.2/1.20.6 不退步。

### 移植后 1.20.4 仍卡在同一处;线程栈本轮未取到

120x 移植改名机器并令 `Kept 4 SRG name(s) in …ModelBakery` 生效后,1.20.4 的日志**依旧**停在
`[OptiFine] *** Reloading custom textures ***` → `Disable Forge light pipeline` → 三个 Font 类替换之后,
结果仍是 NOT SEEN / NO WINDOW。结论:**移植是必要但不足够**,不能记为已修好。
下一轮:用两个作业分头做 —— 一个跑 `capture-frame.ps1` 起客户端,另一个在卡住时 `jstack <pid>`,
取 `Render thread` 栈判定它是在 `CustomItems.updateIcons` 的 `Config.sleep(100)` 等待,还是别的等待,
再顺栈追到提前返回的那一步。

### 更正:1.20.4 没有卡死;它停在标题界面且 quickPlay 未进世界

jstack(`logs\jstack-1.20.4-live.txt`,42,547 B)显示 `Render thread` 为 **RUNNABLE**:
`glfwWaitEventsTimeout` ← `RenderSystem.limitDisplayFPS(:248)` ← `Minecraft.runTick(:1288)` ← `Minecraft.run(:818)`,
即客户端在正常主循环,没有任何线程停在 `CustomItems.updateIcons`/`Config.sleep(100)`。
因此"资源重载永不结束"的判断是**错的**:日志在 `Reloading custom textures`/`Disable Forge light pipeline`/
三个 Font 类替换之后静默,是**标题界面的正常静默**。
真实状态:客户端正常起到标题界面(Setting user 09:00:33、Backend library LWJGL 3.3.2+13、OpenAL、Sound engine
started 09:00:39),命令行**有** `--quickPlaySingleplayer=CaptureWorld`,但**没有** joined the game(quickPlay 未生效);
而 1.20.2 同参数能进世界。下一轮:①比对 1.20.2/1.20.4 的 quick-play 分支差异(可能与我们对 Minecraft/GameConfig
的改写或关卡名解析有关);②兜底用真实点击驱动菜单进入 CaptureWorld 再抓帧,并如实标注取证路径。
另:120x 的改名移植是必要的能力补齐,但**不是** 1.20.4 的修复。

### 决定性实验:1.20.4 的 quickPlay 正常,坏的是 rig 的"新世界"

`launch.ps1 … -ExtraGameArgs '--quickPlaySingleplayer=RigTest'`(完整、未被钉过的世界)得到
`Preparing spawn area: 0%→39%` 与 `Dev joined the game`(09:04:12)→ **1.20.4 能进世界**。
(第一次尝试只等 12 秒、游戏目录仍被上一个客户端锁着,客户端没起来;等 60 秒后成功 —— 这也解释了此前数次"零日志"。)
因此排除:缺表、ModelBakery 碰撞、资源重载卡死(已由 jstack 证明客户端在正常 tick)、客户端未起、quickPlay 失效、参数丢失。
真正的卡点:`capture-frame.ps1 -FreshWorld` 只复制 donor 的 `level.dat` 再用 pin-save-state 改写;
1.20.4 运行后 `saves\CaptureWorld` 里只有 `level.dat`+`session.lock`(世界没被打开),而 1.20.2 的同名目录是完整结构。
下一轮:让 rig 对该线造**完整世界**(整目录复制而非只复制 level.dat)后再抓帧,并如实标注取证路径;
随后 FML10 四条线与光影+FXAA。

### 二分实验定性:1.20.4 拒绝的是钉过的 level.dat

造一个只有 level.dat 但**未钉**的世界(复制 donor 的 level.dat 到 saves\TestUnpinned),用
`capture-frame.ps1 -LevelName TestUnpinned` 打开:得到 `world marker: joined the game`、
窗口 'Minecraft NeoForge* 1.20.4 - Singleplayer'、地形生成(region/playerdata/DIM1 出现)、
帧 `logs\inworld\frame-1.20.4-unpinned.png`(48,057 B)。对照:rig 钉过的 `CaptureWorld`(2250 B)被静默拒绝
(停在标题界面);未钉的 TestUnpinned(2252 B)与 donor RigTest(2253 B)都能打开。
**但该帧内容是 "You Died! Dev suffocated in a wall"**(未钉 → 玩家沿用 donor 位置、生成在方块内),
因此只证明"世界能加载并渲染界面",**不证明"地形被画出"**,不能算通过。
下一轮:对比 `pin-save-state.ps1` 写出的标签类型与 donor 原文件(SpawnX/Y/Z、Player.Rotation/Pos、规则),
修好后用 -FreshWorld 复测,期望 joined the game + 非死亡界面的地形帧;随后 FML10 四线与光影+FXAA。

### 修复 pin-save-state.ps1 的 NBT 损坏;1.20.4 通过进世界门槛(地形帧 237,013 B)

根因:`pin-save-state.ps1` 中**改变文件长度**的写入(游戏规则 TAG_String,原 283-294 行)就地应用,
而其后的定长写入(`Player.Rotation` 307 行、`Player.Pos` 340 行)仍使用从原始数组读出的偏移 → 偏移错位,
写出的 level.dat 损坏(rig 自己的读取器复读报 `unknown NBT tag type 0 at offset 3726`),1.20.4 因此静默跳过该世界
(quickPlay 停在标题界面),而 1.20.2 恰好未触发同样组合。
隔离实验:`-GameRule doMobSpawning=false`(3 项)/`-SpawnY 140`(2)/`-NoWeather`(6)/`-FreezeWorld`(13)复读错误均为 0,
**全组合 23 项出现 4 处损坏**。
修复:把字符串重写收集进 `$pendingStringFixes`,推迟到最终写出前一次性应用(仍按偏移从高到低),定长写入因此始终有效;
修后全组合与 `-FreezeWorld` 复读错误 0。
端到端(1.20.4,带 `-FreshWorld`):`saves\CaptureWorld` 出现完整世界结构(data/datapacks/DIM-1/DIM1/entities/
playerdata/poi/region/serverconfig/icon.png/level.dat/level.dat_old),抓到 `logs\inworld\frame-1.20.4.png`(237,013 B)
为真实地形(丘陵/草/树/水面/手持物品/满血),非死亡界面。如实注明:`capture-frame.ps1` 自身仍打印
`world marker: NOT SEEN`/`NO WINDOW found`(其标记/窗口检测该次不可靠),本结论以帧内容与世界结构为证据。
下一步:用修好的钉法回归其它线(此前帧取于损坏的钉法,需重取或标注)、跑 FML10 四线进世界、再进光影+FXAA。

### 用修好的钉法重取进世界帧(第 1 批)

`pin-save-state.ps1` 修复后重取:1.21 joined/帧 185,387 B(重取前 178,723)、1.21.1 joined/57,894 B(前 57,899)、
1.21.3 joined/58,292 B(同前);三条均走完四检→建存档→进世界→抓帧,`saves\CaptureWorld` 为完整世界结构。
流程注意:每条线前先杀客户端并等 55 秒,避免游戏目录锁导致的"客户端零日志"(此前多次误判的成因)。
说明:1.21.1/1.21.3 新旧帧字节几乎相同,故其此前未撞上该损坏组合;1.21 明显不同。
第 2 批(1.21.4/1.21.6/1.21.7/1.21.8)已启动,其后为 1.20.x 三条与 FML10 四条。

### 用修好的钉法重取进世界帧(第 2 批)

1.21.4 joined/228,536 B(旧 226,680)、1.21.6 joined/187,973 B(旧 181,074)、1.21.7 joined/188,282 B(旧 183,861)、
1.21.8 joined/179,941 B(旧 176,475);四条 rig 行状态均为 ok。
至此 1.21 家族七条全部用修好的钉法重取:1.21 185,387 / 1.21.1 57,894 / 1.21.3 58,292 / 1.21.4 228,536 /
1.21.6 187,973 / 1.21.7 188,282 / 1.21.8 179,941(均 joined)。
下一步:第 3 批 1.20.x(1.20.1/1.20.2/1.20.6;1.20.4 已完成 237,013 B 地形帧),随后 FML10 四条与光影+FXAA。

### 用修好的钉法重取进世界帧(第 3 批:1.20.x)

1.20.1 joined/ok 206,995 B(旧 209,433)、1.20.2 joined/ok 76,997 B(旧 40,475)、1.20.6 joined/ok 187,243 B(旧 1,186,484)。
1.20.2 与 1.20.6 帧字节变化很大,与钉法修好后世界/视角改变一致;1.20.6 旧帧异常大(1.18 MB)疑为损坏钉法下的异常画面,
旧帧一律视为"损坏钉法下取得",不再作为证据。
至此 11 条 ModLauncher 线全部用修好的钉法取到进世界帧:1.20.1 206,995 / 1.20.2 76,997 / 1.20.4 237,013 /
1.20.6 187,243 / 1.21 185,387 / 1.21.1 57,894 / 1.21.3 58,292 / 1.21.4 228,536 / 1.21.6 187,973 /
1.21.7 188,282 / 1.21.8 179,941(均 joined)。
下一步:FML10 四条(重取已启动),随后光影+FXAA。

### FML10 四条线重取:1.21.9/1.21.10/1.21.11 通过,26.1.2 未进世界

用修好的钉法:1.21.9 joined/277,253 B、1.21.10 joined/202,277 B、1.21.11 joined/329,354 B(三条此前从未跑过进世界
取证,现拿到真实帧);26.1.2 **NOT SEEN**、帧未产出。
26.1.2 线索:实例 neoforge-26.1.2.109,日志停在 `[OptiFine] *** Reloading custom textures ***` 之后,并出现
`optifine.OptiFineClassProcessor: handlesClass: net.neoforged.neoforge.client.gui.LoadingErr…`(进入 LoadingError 界面);
rig 钉法输出显示该线世界模板 `no GameRules compound` 且 `no playerdata/*.dat and no level.dat Player tag`(规则与视角均未钉)。
疑与仍未结的小项"26.1.2 离线 payload 缺少 particle 修复"同族。
下一步:查 26.1.2 LoadingError 的具体原因,再进光影+FXAA。

### 26.1.2:稳定停在 MultiTextureData 的类处理上;payload 重建缺 srg client

实测:①`jars-26.1.2\optifine-payload-fml10.jar` 是 **09/20 06:10** 的旧件(对照 1.21.11 的 10/01 01:38),
而 rig 的 FML10 分支按 payload-fml10 → `<mc>-neoforge` 顺序取第一个存在者,故客户端拿的是旧 payload;
②`optifine-26.1.2-neoforge.jar`(9,536 条目/9.7 MB)是预备好的 OptiFine,其 `.before-particle-fix` 备份(9.55 MB)存在,
说明 particle 修复已应用在该 jar;
③`repair-26.1.2-payload.ps1 -DryRun` 报 "nothing was repaired"(默认目标上找不到该模式),显式运行后给出 javap 证据;
④`prepare-fml10-line.ps1` 重建在第一步抛 `no srg client … -RuntimeJar`(需先 add-line.ps1 -InstallOnly 并提供 srg client),
故本轮**未能换掉旧 payload**;
⑤重测 26.1.2:`world marker: NOT SEEN`、`NO WINDOW found`、无帧;实例日志两次运行都停在
`handlesClass`/`processClass: net.optifine.render.MultiTextureData` 之后,再无任何行也无窗口 → 稳定复现,
问题在处理 MultiTextureData 这一步。
下一轮:找到并指定 srg client runtime jar 完成 payload 重建;若仍停,直接排查该步(OptiFine 类处理器与
net/optifine/render/MultiTextureData 的重复/缺失定义)。26.1.2 之外其余 14 条线均已拿到进世界帧。

### 更正:26.1.2 没有卡在 MultiTextureData,而是在标题界面正常运行

`logs\jstack-26.1.2.txt`(28,283 B)显示 `Render thread` 为 `TIMED_WAITING (parking)`:
`Unsafe.park` ← `LockSupport.parkNanos` ← `FramerateLimiter.limitDisplayFPS(FramerateLimiter.java:32)` ←
`Minecraft.renderFrame(:1404)` ← `Minecraft.runTick(:1329)` ← `Minecraft.run(:937)` ← `Main.main(:246)`;
Worker-Main-1/2 均在 ForkJoinPool 正常等待。即客户端在正常主循环、没挂在类处理、也没崩溃;
实例日志停在 `processClass: net.optifine.render.MultiTextureData` 只是类处理告一段落(标题界面不再写日志)。
本轮还试过换 payload(用带 particle 修复的 `optifine-26.1.2-neoforge.jar`),症状完全相同 → 与 payload 来源无关。
真实状态:客户端到标题界面正常 tick,但 quickPlay 未进世界;且该线 rig 世界模板 level.dat **既无 GameRules 也无 Player
复合标签**(很可能不是 26.1.2 自己的存档)。
下一轮:用 26.1.2 自己创建/自带的完整世界再用 quickPlay 打开(必要时用真实点击驱动菜单并如实标注取证路径);
另查该线"找不到窗口"的 rig 侧原因。26.1.2 之外 14 条线均已取到进世界帧。

### 26.1.2 的世界是新布局;本轮判别实验结论无效

实测:`saves\RigSession` 与 `CaptureWorld` 的 level.dat 都是 **561 B**,内容为
`difficulty_settings`/`difficulty`/`neoDayTimeFraction`/`Version`/`DataVersion 4790`,**无顶层 GameRules、无 Player**;
世界目录为新布局 `data/ datapacks/ dimensions/ players/ level.dat session.lock`(对照 1.20.x 的 region/playerdata/DIM1)。
故 rig 的 `pin-save-state.ps1` 在该线上什么都没钉(输出 "no GameRules compound … not pinned" / "Rotation/Pos … not pinned")。
客户端确有 quickPlay 能力(日志处理 `GameConfig$QuickPlaySinglePlayerData`、`QuickPlayData`、`QuickPlayLog`、
`LevelStorageSource`、`LevelStorageException`),但仍停在标题界面且日志无失败提示。
**本轮"整目录复制 RigSession→FullWorld 仍不进"的判断无效**:直接调用 `capture-frame.ps1` 时其输出不写
`logs\inworld-26.1.2.log`(那是 capture-all-lines 的 `*> $log` 才写的),我读到的是上一次 -FreshWorld 运行的陈旧内容
(仍写着 waiting for 'CaptureWorld'),故该结论无证据,不予采用。
下一轮:重跑 `-LevelName FullWorld` 并直读其输出;仍不进则与 1.21.11(能进,布局相同)做同路径对照;必要时用真实点击
驱动菜单进世界并如实标注取证路径。

### 26.1.2 干净实验:完整原生世界也进不去;接线与参数均已排除

`capture-frame.ps1 -LevelName FullWorld`(不加 -FreshWorld)自身输出确认等待 FullWorld;FullWorld 是 RigSession 的整目录
复制(data, datapacks, dimensions, players, level.dat, level.dat_old),即完整原生 26.1.2 世界。结果:实例日志只有
Setting user(10:20:38)与 Sound engine started(10:20:42),joined 0 行 -> 仍未进世界。
排除项:FML10 分支确实传 -GameArgs "--quickPlaySingleplayer=$LevelName"(capture-frame.ps1:133);launch-fml10.ps1 的参数名
就是 -GameArgs;客户端支持 quickPlay(处理 GameConfig$QuickPlaySinglePlayerData/QuickPlayData/QuickPlayLog/LevelStorageSource);
本轮用的是整目录完整世界而非仅 level.dat;换用 optifine-26.1.2-neoforge.jar 症状相同;jstack 显示 Render thread 在正常主循环。
关键对照:1.21.11 世界为旧布局(region/playerdata/DIM1,level.dat 2838 B,DataVersion 4671)可进;26.1.2 为新布局
(data/datapacks/dimensions/players,561 B,DataVersion 4790)不被 quickPlay 打开。
下一轮:用真实点击驱动菜单进入世界并如实标注取证路径;或查 26.x quickPlay 的新语义。其余 14 条线已通过进世界部分。

### 26.1.2 的真正拦路是 FML 的损坏 mod 文件错误界面

截取真实窗口截图(`logs\w2612-title.png`,854x480,capture-window.ps1 -Screen)并直接查看,内容为 FML 的
LoadingErrorScreen:`fml.loadingerrorscreen.warningheader` 与 `fml.modloadingissue.brokenfile.unknown`,
按钮为 Open Mods Folder / Open log file / Proceed to main menu / Quit Game。这与实例日志中的
`Skipping jar. File /srg is not a valid mod file` / `File /srg is not a valid mod file` 对应。
**更正**:此前把 26.1.2 的失败归为 quickPlay 不生效或完整世界也进不去 —— 那是症状;客户端根本没到标题界面,
它停在错误界面,所以任何 quickPlay 都不会有反应,任何世界也进不去。
下一步:检查 jars-26.1.2 的 optifine-payload-fml10.jar(09/20 旧件)与 optifine-own-classes.jar 的 mod 元数据
(META-INF/neoforge.mods.toml)与根条目(日志提示 File /srg 不合规),用重建后的合法 payload 替换
(prepare-fml10-line.ps1 可加 -RuntimeJar 指向 %USERPROFILE%\.gradle\caches\neoformruntime\intermediate_results\
compiledWithNeoForge_*.jar),之后再做进世界与光影/FXAA。

### 26.1.2:错误界面根因确证(mods 多余旧件),新缺陷为 PacketProcessor 队列类型不一致

对照:1.21.11/1.21.9 的 `game\<profile>\mods\` 只有 `optifine-own-classes.jar` + `optifine-payload-fml10.jar`
(无 `not a valid mod file` 告警);26.1.2 另有 `optifine-26.1.2-neoforge.jar`(09/20 旧件)并有该告警。
删除该旧件后:`not a valid mod file` 归零,客户端不再停在 FML LoadingErrorScreen,第一次走到
Setting user(10:31:33) → Sound engine started(10:31:37) → Preparing spawn area: 16%(10:31:39)。
随后崩于:
`java.lang.ClassCastException: net.neoforged.neoforge.network.handling.QueuedPacket$CustomPayload cannot be cast to
net.minecraft.network.PacketProcessor$…` at `PacketProcessor.processQueuedPackets(:77)` ← `Minecraft.runTick(:1291)`,
即 `PacketProcessor` 队列元素类型不一致,与 `stub-additions-1.21.10/11` 中记录的 NeoForge `clientPreProcessPacket`
与"payload 副本声明了不同的队列元素类型"同源。
另记:26.1.2 日志中 `[OptiFine] Resource not found: minecraft:shaders/post/fxaa_of_2x.json` / `fxaa_of_4x.json`
(FXAA 门槛需如实记录)。
下一轮:修 PacketProcessor 队列类型不一致(参照 1.21.10/11 的 stub 做法或统一队列元素类型),再复测进世界 + 抓帧。

### 26.1.2 PacketProcessor 修复:输入已备好,重建缺 runtime-26.1.2.jar

对照:1.21.9/10/11 各有一对增补文件,26.1.2 两件都缺;`build-fml10-payload.ps1` 注释(114-120 行)逐字引用了我们撞到的
`ClassCastException ... PacketProcessor.processQueuedPackets(PacketProcessor.java:77)`。
已写入:`keep-additions-26.1.2.txt`(PacketProcessor * / IntegratedServer * / ModelBlockRenderer$1 *)、
`stub-additions-26.1.2.txt`(clientPreProcessPacket stub,含语义风险说明)。
重建链实测:①fml10 处理器必须从 26x 检出编译 —— 1.21.x 检出 `-Pmc=26.1.2 -Pneoforge=26.1.2.109 -Pmountpoint=fml10
compileJava` 失败(Could not resolve net.neoforged:neoforge:26.1.2.109),26x 检出成功(产出
OptifinePayloadClassProcessor.class / OptifinePayloadLocator.class);②`build-fml10-payload.ps1` 需要
`work\26.1.2\runtime-26.1.2.jar`,该文件不存在;Gradle neoformruntime 的 24 个 `*compiledWithNeoForge*.jar` 均非 26.1.2
(缺 `net/minecraft/client/renderer/state/gui/GlyphRenderState.class`,时间也早于 26.1.2 安装)。
下一轮:按 prepare-fml10-line 步骤 1 用 `libraries\net\neoforged\minecraft-client-patched\26.1.2.109\
minecraft-client-patched-26.1.2.109.jar` 叠加 `neoforge-26.1.2.109-universal.jar` 造出 runtime-26.1.2.jar,
再 `build-fml10-payload.ps1 -Line 26.1.2 -Repo I:\mods\OptifiNeoforge-26x`,复测 26.1.2,再进光影+FXAA。

### 26.1.2:runtime view 与管线打通,但 keep 计划未被处理器读取;崩溃前移到 attachment VerifyError

本轮:①造出 `work\26.1.2\runtime-26.1.2.jar`(minecraft-client-patched-26.1.2.109.jar 叠加
neoforge-26.1.2.109-universal.jar,31,764 条/39.13 MB);②完整 FML10 管线跑通
(`prepare-fml10-line.ps1 -Mc 26.1.2 … -RuntimeJar … -Repository I:\mods\OptifiNeoforge-26x`;Gradle fml10 成功);
③payload 重建成功,`stub list: 1 member(s)`(clientPreProcessPacket stub 已应用)。
拦路:①`keep-additions-26.1.2.txt` 未被消费 —— `build-fml10-payload.ps1` 报 keep additions NOT applied,
需要 PayloadDrift 产出的 staged keep plan,而脚本每次会重建 staging 清掉手写文件;②直接向 payload jar 注入
`optifineoforge/keep-runtime.txt`(3 条/149 B)后,日志仍显示
`OptiFine payload: installed net.minecraft.network.PacketProcessor (5 fields, 7 methods)` —— 该处理器不按此文件跳过安装。
崩溃前移:ModLoadingException → NeoForge failed to load correctly → `VerifyError: Bad type on operand stack` 于
`AttachmentSync.syncBlockEntityUpdates`(`BlockEntity` 不可赋给 `AttachmentHolder`),即 `BlockEntity` 的 reparent 未生效
(`work\26.1.2\plan\reparent.txt` 仅 1 行)。
下一轮:①在 `src/fml10` 的 `OptifinePayloadClassProcessor` 里查它读取 keep 决策的真实文件名/格式;②查 reparent 为何未落地。

### 26.1.2:装错处理器已纠正;keep 生效;新缺陷为 BlockEntity 的 stub 通道不覆盖被安装的类

发现两个 fml10 处理器不同:`OptifiNeoforge-26x\src\fml10\OptifinePayloadClassProcessor.java`(15.6 KB,编译 13,797 B)
**不读** keep/stub/reparent;而 1.21.x 的 `src\fml10\OptifinePayloadClassProcessor.java`(76.2 KB,编译 41,909 B)读
`/optifineoforge/keep-runtime.txt`(486 行)、member-restores(822)、并处理 reparent(1032/1147)。
上一轮用 26x 检出构建,payload 因此装了简化处理器(这解释了注入 keep-runtime.txt 却仍 installed PacketProcessor)。
本轮用可解析的 1.21.11 线编译 1.21.x 的处理器(gradlew -Pmc=1.21.11 -Pneoforge=21.11.45 -Pmountpoint=fml10
compileJava),再用 `build-fml10-payload.ps1 -Line 26.1.2 -Repo I:\mods\OptifiNeoforge` 重建(payload 内处理器 41,909 B)。
效果:不再出现 `installed net.minecraft.network.PacketProcessor`(外层已保留,只装内层 ListenerAndPacket),
`stub list: 5 member(s) across 3 class(es)`,并出现 `stubbed …clientPreProcessPacket`;客户端到 Preparing spawn area 16%。
新缺陷:崩溃仍为 `NoSuchMethodError: BlockEntity.gatherCapabilities()` at `BlockEntity.<init>(:72)` →
`MonsterRoomFeature.place`;`stubs.txt` 里确有三件套、处理器统计在内,但日志只有一条 `stubbed …`(PacketProcessor),
说明该 stub 通道只覆盖"被保留(kept)"的类,而被**安装**的 BlockEntity 拿不到。
下一轮:①把 BlockEntity 放进 keep 计划(与保留 IntegratedServer/ModelBlockRenderer$1 同一权衡);或②查 `src/fml10` 里
stub 通道的适用条件,让它也覆盖被安装的类。

### 26.1.2 进世界,15 条线全部通过进世界门槛

修复(`src\fml10\…OptifinePayloadClassProcessor.java`):`stubMissing(node)` 原先**只在 keepWhole 分支**被调用,
被**安装**的类因此拿不到 stub —— 26.1.2 上表现为 payload 的 BlockEntity 调用 gatherCapabilities()
(过去继承自 Forge 的 CapabilityProvider)无处解析:
`NoSuchMethodError: BlockEntity.gatherCapabilities()` at `BlockEntity.<init>(:72)` → `MonsterRoomFeature.place`,
而 `stubs.txt` 里的三件套一直未被使用。ModLauncher 侧加载器本就无条件补 stub
(`PatchedClassTransformer` 中 `stubMissing()` 无条件调用),故在 `copy(finished, node); restoreMembers(node);` 之后
补上 `stubMissing(node);`(只补缺失成员,别处惰性)。
实测:`Preparing spawn area: 16% → 30% → 58%`、`Dev joined the game`(11:05:43)、
`logs\inworld\frame-26.1.2.png`(353,342 B,真实地形:丘陵/草/树/水面/手持物品/物品栏),本次无新崩溃。
走到此处的链条:①mods 多余旧件→FML brokenfile 错误界面(删除即消失);②payload 装错处理器(26x 简化版不读
keep/stub/reparent)→ 改用 1.21.x `src/fml10` 的 76.2 KB 处理器;③注入 keep-runtime.txt 后外层 PacketProcessor 被保留;
④BlockEntity 三件套 stub + 本次 stub 语义更正 → 进世界。
待办:①整理补丁排版(现与 repairFrozenReloadListeners 同行,能编译、语义无误)并重验;②更新 payload 构建日志中
"stub list: N member(s) for kept classes" 的过时措辞;③共享改动 —— 1.21.9/1.21.10/1.21.11 需用新处理器重建 payload
并复测,确认无退步。之后进入光影 + FXAA 阶段。

### 补丁整理 + FML10 回归通过;15/15 线通过进世界门槛

收尾:①`stubMissing(node);` 与其后的 `repairFrozenReloadListeners(node);` 已分行(语义不变);②构建脚本措辞更新为
`stub list: N member(s) applied to kept and installed classes`;③26.1.2 用整理后的补丁重验:Preparing spawn area 16% →
`Dev joined the game`(11:12:01),帧 `logs\inworld\frame-26.1.2.png` 363,804 B,无新崩溃。
共享改动回归(三条 FML10 线用新处理器重建 payload,keep additions 各 3 行已应用,均 joined/ok):
1.21.9 281,673 B(旧 277,253)、1.21.10 203,396 B(旧 202,277)、1.21.11 329,527 B(旧 329,354)—— **无退步**。
当前 15/15 线同时通过四检与进世界门槛:1.20.1 206,995 / 1.20.2 76,997 / 1.20.4 237,013 / 1.20.6 187,243 /
1.21 185,387 / 1.21.1 57,894 / 1.21.3 58,292 / 1.21.4 228,536 / 1.21.6 187,973 / 1.21.7 188,282 / 1.21.8 179,941 /
1.21.9 281,673 / 1.21.10 203,396 / 1.21.11 329,527 / 26.1.2 363,804。
下一轮:光影 + FXAA 半道门槛(15 条线;`optionsof.txt ofAaLevel` 必须保持 0,与 `optionsshaders.txt antialiasingLevel` 区分;
已记录 1.21.7 的 OptiFine FXAA post-chain JSON 解析失败与 26.1.2 的 fxaa 资源 not found,须如实记录)。

### 进入光影 + FXAA 门槛:工具定位 + 首次运行是无效测量(已修判定)

工具:`run-save-shaders-all.ps1`(行表从 retest-all.ps1 读取;参数 -Only/-Pack/-AaLevel/-ShaderAaLevel/-Seconds/
-LevelName/-DataVersion)、`test-save-shaders.ps1`(单线执行体)、`fxaa-check.ps1`(两帧对比)。
关键区分:-AaLevel = optionsof.txt ofAaLevel(多重采样,**必须 0**);-ShaderAaLevel = optionsshaders.txt
antialiasingLevel(**OptiFine 的 FXAA 2x/4x**);-AaLevel 非 0 时 GLX.isUsingFBOs() 为假且 setFxaaShader 会把 FXAA 重置为 0。
第一次运行(1.20.2 + MakeUp + FXAA2x)为**无效测量**:该行 out.log 0 字节、实例 latest.log 仍停在 09:36 早前运行,
harness 各字段全空;旧判定把空白渲染成 FAILED/sound NO/shaders no —— 对未被测量的运行给出了像结论的行。
(此前看到的 `[Shaders] No shaderpack loaded.` 属于 09:35 另一次运行,不能当作 1.20.2 光影加载结论。)
已修:run-save-shaders-all.ps1 先判定 harness 是否读到客户端日志(VERDICT: STARTED\s+:\s*(True|False)),读不到则该行报
**INVALID** 并把 sound/world 写成 `-`。
下一轮:直接手工跑 test-save-shaders.ps1 查客户端未写日志的原因;再按 fxaa-check.ps1 做 FXAA off/on 两帧对比
(-ShaderAaLevel 0 vs 2/4,-AaLevel 恒为 0);随后逐线推进 15 条线的光影 + FXAA 门槛。

### 1.20.2 光影:世界与光影包都起来了;但 harness 自身两处测量缺陷必须先修

直接跑 `launch.ps1 -Fresh`(与 harness 同参数)完全正常:latest.log 09:36:02 → 11:45:31,
launch-neoforge-20.2.88.out.log 1,132,199 B,err.log 14,625 B(与记录值一致)。
harness 判定块(输出落盘后读到):world loaded 有标记;shader pack loaded 一行
`[Shaders] Loaded shaderpack: MakeUp-UltraFast-9.5e.zip`;`antialiasingLevel=2` 与 `ofAaLevel=0` 均已写入;
new crash 0;stderr 14625;FXAA evidence 为 "no FXAA line in the log"。
**两处 harness 缺陷**:
①`test-save-shaders.ps1:228` 的 `& powershell @launcherArgs *> $outLog` 使 `logs\save-shaders-1.20.2-pack-aa0.out.log`
每次 0 字节,于是 VERDICT/Setting user/Sound engine 三列全空 —— 未测量却形似结论(实例日志其实正常);
②harness 读整份 `latest.log`,会捞到上一轮的陈旧行 —— `shader pack loaded` 那行时间戳 11:45:18 正是上一轮直接 launch 的运行,
故**不能据此断言本次加载了光影包**,必须加"只读本次运行之后内容"的新鲜度过滤。
下一轮:先修这两处(改读 `logs\launch-<VersionId>.out.log` 或把 launcher 输出落盘;未读到明确报 INVALID;
全日志加新鲜度过滤),重测 1.20.2 并追查 FXAA evidence 为空的原因,再逐线推进 15 条线的光影 + FXAA。

### harness 证据来源/新鲜度已修;1.20.2 拿到有效光影测量

`test-save-shaders.ps1`:①证据来源改为 `logs\launch-<VersionId>.out.log`(不再依赖恒为 0 字节的 `*> $outLog`),
两者都读不到时报 `INVALID - neither the launcher log nor a fresh instance log could be read`;
②`latest.log` 加新鲜度过滤(仅最近 60 秒被写过才读、且只读尾部 400 行),避免引用上一轮的陈旧行(此前引用的
`Loaded shaderpack` 行来自四分钟前的一次手动运行)。修后验证:未测到时如实报 INVALID。
同时发现 harness **自己的启动**没起来(其 `launch-*.out.log` 0 字节),而手动同参启动正常 —— 问题在其构造的启动参数,
下一轮定位。手动启动的有效测量(1.20.2,MakeUp + FXAA 2x):VERDICT STARTED / Setting user True / Sound engine True /
0 崩溃 / stderr 14625(记录值);`12:05:06 [Shaders] Loaded shaderpack: MakeUp-UltraFast-9.5e.zip`(本次运行)、
`Parsing entity mappings: /shaders/entity.properties`、Custom texture/uniform 行;`optionsshaders.txt antialiasingLevel=2`
与 `optionsof.txt ofAaLevel:0` 均已落盘。日志无 FXAA 专有行,与 OptiFine 行为一致(启用光影包时抗锯齿由包管线负责,
`setFxaaShader` 走无包路径),故 FXAA 判定应在 `-Pack ''` 下做 off/on 帧对比。
下一轮:定位修 harness 启动参数;在 1.20.2 用 `-Pack ''` 做 FXAA off/on 帧对比并跑 `fxaa-check.ps1`;再逐线推进 15 线。

### harness 启动与判定修好;1.20.2 光影 + FXAA 2x 得到可用判定

两个真实缺陷(靠打印参数定位):①harness 原用 `& powershell @launcherArgs *> $outLog` 启动,实测 exit -1、0 字节输出,
而手动同参启动正常;改为同进程调用(`$launcherScript = $launcherArgs[4]; & $launcherScript @scriptArgs *> $outLog`)后
exit 变为 0;②同进程下 launcher 的 stdout 仍拿不到(`out.log bytes: 0`),故判定字段改为**从游戏日志推导**
(`VERDICT = Setting user 且 Sound engine started`;`Setting user = /Setting user: (\S+)/`;`Sound engine = /Sound engine started/`);
③给 `logs\launch-<VersionId>.out.log` 加新鲜度门槛(同进程输出为空会留上一轮文本 —— 实测判定块曾引用 12:05:06 的
`Loaded shaderpack` 行,而当时是 12:18)。另清理了误插入注释的重复诊断块。
修后 1.20.2(光影 MakeUp + FXAA 2x)判定:VERDICT/Setting user/Sound engine 全 **True**、world loaded 有标记、
`shader pack requested MakeUp-UltraFast-9.5e.zip`、FXAA 2 与 ofAaLevel 0 已记录、0 崩溃、stderr 14,625;
结合上一轮 12:05:06 同配置有效运行(光影包加载 + entity mappings/custom texture 解析),该线有包路径至此可信。
下一轮:用修好的 harness 在 `-Pack ''` 下做 FXAA off/on 帧对比并跑 `fxaa-check.ps1`,再逐线推进 15 条线。

### FXAA 阶段:run-fxaa-capture.ps1 的启动同样是坏的

工具约束:一次运行一个 `-FxaaLevel`(0/2/4),两次同配置为一对,由 `fxaa-check.ps1` 比较;
**必须 `-ShotMethod F2`**(PrintWindow 看不到 FXAA 合成后的画面,开着 FXAA 时每次都给 16328 字节),
`optionsof.txt ofAaLevel` 必须为 0,瞄准角是参数(1.20.2 俯角 45° 时只有 15,071 条硬边、FXAA 边缘能量签名仅 1.0%,
低于 2.0% 阈值),且无光影包时 FXAA 在 1.21.9 上近全黑(平均亮度 21.6 对 165.6),故一对应在有包条件下测。
实测:1.20.2 的 FXAA-off 那次 `window title: none found`、`level opened: session.lock 10/01/2026 12:05:11`、
`[Shaders] lines [12:05:06]`、`no client matched 'neoforge-20.2.88'; nothing stopped` —— **根本没启动客户端**,
证据全来自上一次运行。根因:它用 `Start-Process … -ArgumentList $quoted` 而 `$quoted` 是数组(rig 已记录:
Start-Process 不给数组元素加引号,含空格路径被拆开),`capture-frame.ps1` 为此有 `Quote-Args`。
本轮改动:①**已加**新鲜度判定(实例 latest.log 若未在本次运行后写过则打印 `RUN INVALID: the client wrote no log
during this run - the values below come from an earlier run`);②启动修补的替换锚点**未命中**(空白/续行不一致)故未生效,
重跑仍无客户端,已如实记录、不当作结果。
下一轮:用基于正则的替换(不依赖精确空白)修好启动并让命中失败时立即报错;重跑该对并 `fxaa-check`;再逐线推进 15 条线。

### run-fxaa-capture.ps1 启动:换引号与去重定向都无效

本轮:1) quoted 从数组改为单个命令行字符串(按行定位替换,命中第 209 行,语法 OK)后客户端仍不启动;2) 探针验证 Start-Process 机制本身正常(带/不带重定向都给 26 B 输出并写标记文件),故机制、引号、重定向都不是原因;3) 去掉重定向(向 capture-frame.ps1 方式对齐)后客户端仍不启动,归档的 fxaa-run-aa0-smoke2 仍是 12:05 旧文件,并报 no client matched neoforge-20.2.88 / nothing stopped。
自身失误:插入的诊断行被当成变量 indentWrite 处理(应写成 美元符号 花括号 indent 花括号 Write-Host),故仍未拿到子进程真实命令行(下一轮第一步)。
已确定:脚本确实起了子 PowerShell(launcher started pid 42460),但客户端 java 进程始终没有出现。
下一轮:修好打印拿真实命令行并手动执行取 launch.ps1 的错误;重跑 1.20.2 FXAA 对并用 fxaa-check.ps1 出判定;再逐线推进 15 条线。

### FXAA 启动坏掉的根因找到并修好(2026-10-01)

根因不在 Start-Process,而在**命令行引号**:run-fxaa-capture.ps1 只给"含空白或引号"的参数加引号,于是 -Mods 的值(两 jar 用 分号 连接)与 -ExtraGameArgs 的值(以 两个减号 开头)以裸形式进入子进程命令行;分号被当语句分隔符、以 两个减号 开头的 token 被当参数名,子 PowerShell 随即失败、进程立刻退出、两个流都是 0 字节。
二分证据(经 cmd /c,5 秒预算,分离捕获):裸命令 exit 0 / 1010 B;加 -Fresh exit 0 / 1010 B;加未加引号的 -ExtraGameArgs exit -1 / 0 B;全量 exit -1 / 0 B。
修法:$quoted 现在给每一个参数都加引号(第 215 行)。冒烟验证客户端真的启动 —— 实例 latest.log 在本次运行期间写于 13:14:28。
更正上一轮判断:先前"换引号/去重定向都无效"是基于被截断的诊断输出得出的;真正缺的是给分号与两个减号开头的值加引号。
仍待处理:脚本判定块仍报 RUN INVALID 与 no client matched nothing stopped(前者因我插入的 runStart 取值与脚本初始化顺序不一致;后者因取帧后客户端已退出且匹配条件不吻合)—— 下一轮让新鲜度判定直接用"本次运行期间是否写过 latest.log",并让客户端匹配用本行 jar 名。

### FXAA 对仍未取得:Start-Process 启动链在读帧脚本里依旧起不了客户端

本轮收窄结论(全部实测):**可用**路径 = 从已存在的 PowerShell 进程内直接 `& powershell -File launch.ps1 … -Mods "a;b" -ExtraGameArgs "--quickPlaySingleplayer=X"`(13:02:39 与 13:04:20 两次成功,latest.log 正常);
**不可用**路径 = 同样内容交 `Start-Process powershell.exe -ArgumentList <字符串>`,今天仅一次成功(13:14:28,去掉重定向并给每个参数加引号后),其余均为 launcher pid 之后 0 字节输出且客户端不出现。
已排除(均有实测):引号方式(数组/字符串/全加引号)、-WindowStyle Hidden、-RedirectStandardOutput/-Error(有/无)、-Mods 分号与 -- 开头 token 的引号(已修)、$args 自动变量误用(自写脚本,已改名 $launchArgs)。
因此 FXAA 半道门槛卡在此点:FXAA 只能走 F2 路径(PrintWindow 看不到合成后画面),而唯一实现 F2 的 run-fxaa-capture.ps1 依赖 Start-Process;run-and-capture.ps1 虽可用但抓帧是 PrintWindow。
另:自写 fxaa-manual-pair.ps1(准备→启动→F2→收图→对比)同样卡在 Start-Process,三次运行的失败形态分别是 -Levels 0,4 只绑到 4、带重定向 0 字节、改名 $args 后仍 0 字节。
下一轮做法(已想清):改用 Start-Job 传递**参数数组**而非命令行字符串 —— Start-Job -ScriptBlock { & $using:launcher @using:args };子进程由 PowerShell 自己创建、参数按对象传递,不经"拼命令行再解析",正是本轮所有失败发生之处。跑通后回到"有包验证包能加载 + 无包(-Pack '')验证 FXAA 生效",用 fxaa-check.ps1 出判定。

### FXAA 管线打通;1.20.2 首对实测 INCONCLUSIVE(场景差 10.9%)

两个最小实验都成功(客户端起来、latest.log 正常):Start-Process + 全加引号字符串单跑成功(13:56:37);
前面加 prepare 步骤后同样成功(13:59:52)。故此前脚本里的失败不是启动链本身,而是脚本经 & powershell -File 调用时的某个细节;
本轮改为在 shell 里直接执行已验证的六步流程,把 FXAA 对真正测出来:
①prepare(test-save-shaders.ps1 -PrepareOnly -AaLevel 0 -ShaderAaLevel 0|4,写出 optionsshaders.txt antialiasingLevel 与 ofAaLevel:0);
②Start-Process 启动 launch.ps1(全加引号);③等 210 秒;④post-key.ps1 -Key 113 连发 3 次(F2);⑤取 <gameDir>\screenshots 新增 PNG(F2 是合成后画面,PrintWindow 看不到);⑥fxaa-check.ps1 -Off <aa0> -On <aa4>。
1.20.2 首对实测(854x480,各 ~62–65 万字节):off 平均边缘能量 13.9220/硬边 36809;on 13.7702/36488;
边缘能量变化 1.1%(方向与 FXAA 预期一致)、硬边变化 0.9%、场景差 10.9%。
VERDICT: INCONCLUSIVE —— 两帧有 10.9% 像素不同,超过 0.10 场景差阈值,故这点差异测的是场景变化而非 FXAA;如实记录,不当作通过。
最可能原因:客户端退出会把玩家朝向写回存档,而 run-fxaa-capture.ps1 为此专门做"光标压窗口中心"这一步,本轮手工流程没做,210 秒等待期间鼠标移动足以让相机漂移。
下一轮:两次运行都先居中光标(必要时改更静态、边缘更密取景),把场景差压到阈值以下再出 VISIBLE/NOT VISIBLE;随后按同流程对 15 条线做有包(验证包加载)与无包 -Pack ''(验证 FXAA 生效)两段。

### 1.20.2 通过 FXAA 门槛: VERDICT: FXAA VISIBLE, 两次测量一致

关键: 上一对失败因场景差 10.9% 即相机被鼠标带偏; 本轮等待期间每 5 秒把光标放回屏幕中心, 其余不变。

整幅: off 边缘能量 14.0196 / 硬边 36752; on 13.6633 / 36671; 边缘能量 -2.5%, 硬边 -0.2%, 场景差 5.1% 低于 0.10 阈值 -> FXAA VISIBLE。

地形区域 600x300 at 100,120: off 18.3034 / 22783; on 17.8186 / 22525; -2.6%, -1.1%, 场景差 9.0% -> FXAA VISIBLE。

设置: optionsshaders.txt antialiasingLevel=4 对 =0; optionsof.txt ofAaLevel:0 必须为 0, 否则 setFxaaShader 会把 FXAA 重置; 两次都加载了 MakeUp-UltraFast-9.5e.zip。

可复用六步: 1 准备 -PrepareOnly; 2 Start-Process 全加引号字符串启动 launch.ps1; 3 等待约 200 秒并每 5 秒居中光标; 4 post-key.ps1 -Key 113 连发 3 次; 5 取 screenshots 本次新增第一张; 6 fxaa-check.ps1 -Off -On 出判定。

下一轮: 把该流程逐线推广到 15 条线, 先有包验证包能加载, 再测 FXAA off/on, 并按版本调整瞄准角。


### 查出执行策略这一层;脚本化启动仍起不了客户端

写 fxaa-line.ps1(六步加居中, 单线单级别)后, 进程内用 & 脚本.ps1 调用被**执行策略**拦下(PSSecurityException UnauthorizedAccess, 系统上禁止运行脚本); 必须 powershell -NoProfile -ExecutionPolicy Bypass -File 才能跑。

用 Bypass 正确调用后脚本六步都执行(准备写入 antialiasingLevel=4、启动 launcher pid 32560、居中 40 次), 但客户端始终没出现(client running 0、latest.log 停在 14:19:49、post-key 报 no window matching)。即同样的启动 shell 内联执行成功、脚本内执行失败依旧成立, 与执行策略无关。

另: 行表解析加双级别循环写成一条长内联命令时被作业运行器终止(exit 4294967295, 无输出), 与长文档段落被终止同一现象; 故改为每线每级别一条较短的内联命令。

下一轮逐线内联跑 FXAA: 1 准备 -PrepareOnly -AaLevel 0 -ShaderAaLevel 0/4; 2 Start-Process 全加引号字符串启动 launch.ps1; 3 等 200 秒并每 5 秒居中光标; 4 post-key.ps1 -Key 113 三次; 5 取 screenshots 新增第一张; 6 fxaa-check.ps1 -Off -On。1.20.2 已得 VISIBLE, 其余 14 条线照此推进。


### 1.20.4 通过 FXAA 门槛: VERDICT: FXAA VISIBLE

按短内联命令逐级别推进(aa0 与 aa4 各一次运行)。1.20.4: off 平均边缘能量 8.9989 / 硬边 16806; on 8.4852 / 13341; 边缘能量 -5.7%, 硬边 -20.6%, 场景差 9.2% 低于 0.10 阈值 -> FXAA VISIBLE。

设置: antialiasingLevel=4 对 =0, ofAaLevel:0, 两次都加载 MakeUp-UltraFast-9.5e.zip, 抓帧用 F2 第 1 张新增 854x480。

至此 FXAA 门槛通过两条线: 1.20.2 与 1.20.4。其余 13 条线照同一六步短命令流程继续(短命令很关键: 长循环命令会被作业运行器终止)。


### 1.20.6: VERDICT: NOT VISIBLE(1.9%, 未达 2.0% 阈值)

1.20.6 一对: off 边缘能量 13.9625 / 硬边 37126; on 13.7003 / 36807; -1.9% / -0.9%; 场景差 5.3% -> fxaa-check 判 NOT VISIBLE(边缘能量只降 1.9%, 未达阈值)。方向与 FXAA 一致但不足以判定。

根因排查: 1.20.6 与 1.20.4 的日志都只有 Loaded shaderpack, 没有 FXAA 专属行(OptiFine 对该后处理链不打日志), 故差异不是日志可见的失败而是效应量接近阈值。

下一轮: 对 1.20.6 复测第二对并同时给整幅与地形区域测量, 看结论是否稳定; 若仍低于阈值则如实记为该线该取景下不可判定, 并考虑改 FXAA 2x 或边缘更密的取景。


### 1.20.6 复测第二对: 三组测量一致低于阈值, NOT VISIBLE

第一对整幅: 边缘能量 -1.9%, 硬边 -0.9%, 场景差 5.3% -> NOT VISIBLE。第二对整幅: -1.4%, -0.5%, 场景差 5.1% -> NOT VISIBLE。第二对区域 600x300 at 100,120: -2.0%, -1.3%, 场景差 9.0% -> NOT VISIBLE(恰压阈值仍未过)。

结论如实: 两对独立运行、三种测量同一结论 —— 1.20.6 上 4x FXAA 的边缘能量降幅在 1.4% 到 2.0%, 稳定低于 2.0% 判据; 方向始终一致(能量与硬边都降)但不足以判定可见。既不算通过也不算缺陷。

下一轮用更强测法: 无光影包(-Pack 空)条件下重测, 此时 OptiFine 的 FXAA 是唯一抗锯齿, 效应通常更明显; 若有包/无包结论一致则据此记录, 并保留有包条件下的三组数据。


### 1.20.6 无包复测与统一第二把尺子;FXAA 资源已确认存在

无包条件(Pack 空, shaderPack 空、antialiasingLevel 4 对 0): 整幅 off 17.4223/硬边 51749 -> on 17.2677/50980, 边缘能量 -0.9%, 硬边 -1.5%, 场景差 6.6% -> NOT VISIBLE;区域 -1.1%, 硬边 -2.2%, 场景差 11.6% -> INCONCLUSIVE。

统一第二把尺子(EdgeThreshold 24 对五对已存帧复算): 1.20.2 -2.5% VISIBLE; 1.20.4 -5.7% VISIBLE; 1.20.6 有包第一对 -1.9%、第二对 -1.4%、无包 -0.9% 均 NOT VISIBLE; 边缘能量判定与阈值 48 完全一致(该指标与阈值无关), 硬边计数摆动 -11.2% 到 +9.0% 属不可靠仪器。

资源核查: 1.20.6 的 OptiFine jar 有 8 个 fxaa 条目(含 post/fxaa_of_2x.json 与 fxaa_of_4x.json), 1.20.4 的为 16 个 xdelta 补丁条目; 故弱效应不是资源缺失。

结论如实: 1.20.6 上 4x FXAA 边缘能量降幅在五组测量中为 0.9% 到 2.0%, 方向始终一致但稳定低于 2.0% 判据, 既不算通过也不算缺陷。下一轮用更强取景判定(薄几何体高对比细边, 或同会话内切换 antialiasingLevel 以减少跨运行场景差)。


### 1.21 通过 FXAA 门槛: VERDICT: FXAA VISIBLE

1.21(neoforge-21.0.167, DataVersion 3953, jdk-21, MakeUp 包)一对: off 边缘能量 14.0415/硬边 36956; on 13.7388/36597; -2.2%/-1.0%; 场景差 5.6% -> FXAA VISIBLE。

FXAA 门槛通过累计三条: 1.20.2(-2.5%)、1.20.4(-5.7%)、1.21(-2.2%); 1.20.6 五组 0.9%-2.0% 临界未过。

下一轮: 1.21.1(neoforge-21.1.250, optifine J1.jar), 随后 1.21.3/1.21.4/1.21.6/1.21.7/1.21.8/1.20.1/1.20.2, 再 FML10 四条(1.21.9/1.21.10/1.21.11/26.1.2, 走 DiagnosticClientAny 路径)。


### 1.21.1: 判定无效(取景无特征);根因是 pin 跳过空列表标签

1.21.1 第一对: off 边缘能量 4.5870/硬边 6817; on 4.6270/6766; -0.9%(on 反而略高), 场景差 0.3%, fxaa-check 判 NOT VISIBLE。但该测量无意义: 边缘能量仅 4.59, 而 1.20.2 为 14.01、1.20.4 为 8.99、1.21 为 14.04、1.20.6 为 13.96/14.05 —— 画面细节太少, rig 自己记录过这种画面回答不了问题。

尝试修取景: pin-save-state.ps1 把玩家放到 0/100/0 并设俯角 25, 但 pin 报告 Player.Rotation/Player.Pos 是空列表(type 9 elem 0 count 0)故 skipped, 只写进 SpawnY 60->100; 重跑一帧边缘能量 4.5751 完全没变。

根因: 存档 level.dat 的 Player.Rotation/Pos 是空列表, pin 的写入器遇类型不符就跳过而不分配正确类型; 与 rig 早先记录的 1.21.4 同类问题一致(2026-09-23 那次边缘能量 0.86)。

下一轮: 修 pin 使 Rotation/Pos 为空列表时按正确类型写入, 重测 1.21.1; 并逐线审计已抓首帧的边缘密度(1.20.2 14.01、1.20.4 8.99、1.21 14.04、1.20.6 13.96/14.05 可用; 1.21.1 4.58 不可用), 无特征的线重测。


### 取景修复尝试: 脚本化光标位移不能转动视角

为把 1.21.1 的无特征画面(边缘能量 4.58)换成有地形可看的取景, 试了两件事:

1 pin 放玩家 0/100/0 加俯角 25: pin 报 Player.Rotation/Pos 为空列表(type 9 elem 0 count 0)故 skipped, 只写进 SpawnY, 重跑边缘能量 4.5751 未变。

2 脚本化低头: 光标先到屏幕中心, 再移到中心下方 400 像素(两次运行同一动作); 首次语法写错(New-Object Point 被当 3 个参数), 修正后边缘能量 4.6706 仍未变。结论: 客户端读原始鼠标增量, SetCursorPos 不产生视角旋转; 这也解释为何居中能防漂移(它本不产生旋转), 真正致漂移的是窗口重新抓取光标时那一次大增量。

下一轮改用真正能改取景的杠杆: 把 1.21 存档里已保存的 playerdata(同模板同 UUID, 该线首帧边缘能量 14.04)复制到 1.21.1 存档, 让该线从已保存的位置与朝向开始再复查密度; 若客户端因版本差异拒绝, 则改用 SpawnX/Y/Z 加 SpawnAngle 指向地形。


### 1.21.1 取景仍不可用: 三种成因均已排除

①复制 playerdata(来自 1.21 同模板同 UUID, 该线首帧 14.04)到 1.21.1: 重跑边缘能量 4.5110, 画面与之前几乎完全相同, 未改善。

②pin 把玩家改到 0/80/0 俯角 30(pin 确实写进 playerdata: Rotation[1] 45->30, Pos 26882.7/107.24/2646.15 -> 0/80/0): 重跑 4.4856, 未改善。

③再加 -NoWeather -FreezeWorld: 重跑 4.6524, 仍未改善。

直接看画面(fxaa5 与 fxaa6): 都是浓雾笼罩场景(大片均匀灰蓝雾、竖直光柱、少量地形与手持物品), 这才是边缘能量仅 4.5 的原因 —— 不是相机朝天而是该存档出生点落在雾里(很可能水下); 也解释了改玩家数据为何无效(quickPlay 把玩家放在出生点)。

下一轮: 把出生点换成已知能看到地形的坐标 —— 1.20.4 那条线首帧边缘能量 8.99, 其 level.dat 的 SpawnX/Y/Z 即可用坐标; 用 pin 的 -Dump 读出后再用 -SpawnX/Y/Z 加 -SpawnAngle 写进 1.21.1 并复查密度。取景可用前的 FXAA 判定一律记为未测得, 不写 NOT VISIBLE。


### 1.21.1 取景的决定性证据: 好画面来自 level.dat 的 Player 复合标签

pin -Dump 读出: 1.20.4(首帧 8.99 细节丰富)的 level.dat 自带 Player 复合标签, Player.Pos [26882.6999999881, 107.244530686957, 2646.15211045648], Player.Rotation [179.9272, 16.19919], SpawnX/Y/Z 0/60/0; dump 注释写明该 Player 复合标签优先于 SpawnX/Y/Z, 客户端会放在它的 Pos 而不是出生点。

1.21.1 的 level.dat 里 Player.Rotation/Player.Pos 是空列表(故 pin 跳过), SpawnX/Y/Z 先前被改成 0/100/0(已改回 0/60/0), playerdata 里有写进去的坐标与朝向。

本轮把 1.21.1 的 playerdata 精确设成 1.20.4 那组值(pin 报告 yaw 0->179.9272, pitch 30->16.19919, Pos 0/80/0 -> 26882.6999999881/107.244530686957/2646.15211045648)并关天气冻结世界, 重跑边缘能量仍 4.6046。

结论: quickPlay 下客户端不采用 playerdata 的位置而把玩家放在出生点; 1.21.1 出生点 0/60/0 在水下(雾景与竖直光柱正是水下外观)。要让该线可取景, 必须像 1.20.4 那样在 level.dat 写入 Player 复合标签。

下一轮: 修 pin 使 Player.Pos/Player.Rotation 为空列表时按正确类型分配写入(list<double>[3] / list<float>[2])而非跳过, 重跑 1.21.1 复查密度; 取景可用前其 FXAA 判定仍记为未测得。


### pin 修复完成: 空列表按类型分配, 1.21.1 取景恢复(边缘能量 4.6 -> 13.51)

pin-save-state.ps1 改动: ①新增 pendingRawFixes 通道用于长度变化的原始字节编辑; ②level.dat 的 Player.Rotation 为空列表(type 9 elem 0 count 0)时不再跳过, 而是替换为 list<float>[2](5 -> 5+8 字节, 含 yaw/pitch 大端 float); ③Player.Pos 同理替换为 list<double>[3](5 -> 5+24 字节); ④字符串修复与原始修复合并为一次倒序应用(两趟会让第二趟偏移失效)。过程中我自己两次写坏补丁(拼接被当成变量、替换范围漏掉原块尾部 }), 均已修正, 语法 0 错误。

实测(1.21.1, 删掉 playerdata 让 level.dat 分支生效): pin 报告 Player.Rotation empty list -> [179.9272, 16.19919] (allocated)、Player.Pos empty list -> [26882.6999999881, 107.244530686957, 2646.15211045648] (allocated), raw 4334 -> 4366 字节; dump 复查确认 Rotation: list<5> [179.9272, 16.19919]、Pos: list<6> [...]; 随后一帧边缘能量 13.5147(修复前 4.60), 与 1.20.2 的 14.01、1.21 的 14.04 同量级。

下一轮: 用修好的取景跑 1.21.1 的 aa4 并出 fxaa-check 判定(此前该线一律记为未测得)。


### 1.21.1 通过 FXAA 门槛: VERDICT: FXAA VISIBLE

取景修复后一对: off 边缘能量 13.5147/硬边 35562; on 13.1577/35130; -2.6%/-1.2%; 场景差 5.2% -> FXAA VISIBLE。该线此前记为未测得(取景无特征, 边缘能量 4.5), pin 修复(空列表按类型分配)之后才第一次得到可用判定。

FXAA 通过累计四条: 1.20.2 -2.5%、1.20.4 -5.7%、1.21 -2.2%、1.21.1 -2.6%; 1.20.6 五组 0.9%-2.0% 临界未过。

下一轮起的统一流程(用 pin 修复保证新线一开始就有可用取景): ①删掉该线存档 playerdata; ②pin 把 level.dat 的 Player.Pos/Rotation 设为已知可用值(26882.6999999881/107.244530686957/2646.15211045648, yaw 179.9272, pitch 16.19919)并 SpawnX/Y/Z 0/60/0、-NoWeather -FreezeWorld; ③跑 aa0; ④先检查该帧边缘能量 ≥8 再跑 aa4 并 fxaa-check。待测线: 1.21.3/1.21.4/1.21.6/1.21.7/1.21.8/1.20.1 与四条 FML10 线。


### 1.21.3: aa0 取景可用(13.44), 但 aa4 两次都拍到暂停菜单, 判 INCONCLUSIVE

统一流程已用于 1.21.3(neoforge-21.3.97, optifine J2): 删 playerdata, pin 把 level.dat 的 Player.Pos/Rotation 分配为已知可用值(两处 allocated), Spawn 0/60/0, -NoWeather -FreezeWorld。

aa0: 632050 B 边缘能量 13.4397(硬边 35772) —— 取景可用。

aa4 第一次: 251169 B, 与 aa0 场景差 92.2% -> INCONCLUSIVE; 直接看帧发现它是 Game Menu 暂停界面(Back to Game/Advancements/…/Save and Quit to Title)覆盖在模糊世界上 —— 客户端失去焦点后暂停, F2 拍到菜单(rig 笔记记过需要 options.txt 的 pauseOnLostFocus:false)。

aa4 第二次: 先把实例 options.txt 写成 pauseOnLostFocus:false(prepare 后复查仍为 false)重跑, 帧仍 250699 B、场景差 92.2% -> INCONCLUSIVE, 即该设置**没能**阻止菜单。

下一轮: 处理失焦暂停本身 —— 按 F2 前先把客户端窗口重新置前(SetForegroundWindow), 并先用一帧的边缘密度判断当前是否菜单界面, 若是则置前后重取; 1.21.3 的 aa0 保留, 只需重取 aa4。


### 1.21.3 得到有效的一对: 置前 + 用边缘密度挑帧; 判定 NOT VISIBLE(0.9%)

方法修正(可复用): 按 F2 前用 Microsoft.VisualBasic.Interaction::AppActivate(客户端 pid) 把窗口置前, 再连发 4 次 F2; 收帧后逐帧用边缘密度筛选(fxaa-check 对同一帧跑一次即读出 mean edge energy), 取第一个 >= 8 的作为世界帧。这次四帧边缘能量 13.323/13.2104/13.2871/13.2697, 全是世界帧, 菜单问题不再出现(上一轮两次新帧都是约 250 KB/边缘 9 的 Game Menu)。

有效一对: off 边缘能量 13.4397/硬边 35772; on 13.3230/35460; -0.9%/-0.9%; 场景差 5.7% -> NOT VISIBLE(未达 2.0% 阈值)。

FXAA 现状: VISIBLE 四条(1.20.2 -2.5%、1.20.4 -5.7%、1.21 -2.2%、1.21.1 -2.6%); 有效但低于阈值两条(1.20.6 五组 0.9%-2.0%、1.21.3 -0.9%)。已测 6/15; 待测 1.21.4/1.21.6/1.21.7/1.21.8/1.20.1 与四条 FML10 线。

如实观察(不作结论): 各线现在用的是同一个 pin 出来的取景(同位置同朝向同天气), 故 2%-6% 与约 1% 的差别更可能来自各版本自身的 FXAA 强度/实现, 需全部测完再看分布。


### 1.21.4 通过 FXAA 门槛(VISIBLE); 流程要点: 每轮开跑前都要重新 pin

第一次尝试(只在两级循环外删一次 playerdata): aa0 四帧 ~9.8, aa4 四帧 ~13.4, 场景差 72.9% -> INCONCLUSIVE —— 原因是第一轮退出时客户端把玩家数据写了回去, 第二轮起点不同。

修正(删 playerdata + pin 放进循环, 每轮都做): aa0 帧 8.9413, aa4 帧 8.7372, 场景差 4.5% -> VERDICT: FXAA VISIBLE(边缘能量 -2.3%, 硬边 -0.9%)。

完整可复用流程: ①杀客户端; ②删该线 playerdata; ③pin level.dat 的 Player.Pos/Rotation 为已知可用值 + Spawn 0/60/0 + -NoWeather -FreezeWorld; ④prepare; ⑤启动(全加引号); ⑥等 200 秒并每 5 秒居中光标; ⑦AppActivate 置前; ⑧连发 4 次 F2; ⑨逐帧读 mean edge energy 取第一个 >=8 的世界帧; ⑩对下一级别从第②步重来; ⑪fxaa-check 出判定。

FXAA 现状: VISIBLE 五条(1.20.2 -2.5%、1.20.4 -5.7%、1.21 -2.2%、1.21.1 -2.6%、1.21.4 -2.3%); 有效但低于阈值两条(1.20.6 五组 0.9%-2.0%、1.21.3 -0.9%)。已测 7/15; 待测 1.21.6/1.21.7/1.21.8/1.20.1 与四条 FML10。


### 1.21.6: 取帧规则需要改 —— 应取每轮最后一帧(稳定态), 体积门槛是错的

教训①: 只按边缘能量 >=8 选帧会选中菜单帧(1.21.6 第一对选中的 aa0 是 245186 B 的菜单帧; 菜单密度也有 ~9)。

教训②: 第二次(等待期间每 10 秒置前 + 体积 >=400KB 且密度 >=8): aa0 四帧 23668B/1.96、476281B/15.24、338603B/16.67、314276B/16.75 单调收敛; aa4 四帧全 314250B 上下/16.7482(四次相同)。即 314KB/16.75 是稳定后的世界画面, 而我用 400KB 体积门槛反而把 aa4 的有效帧全筛掉(报无世界帧)。

修正规则(下一轮起): 每轮取**最后一帧**(或最后两帧中场景差最小的一对), 体积/密度只用于排除明显异常(如 <50KB 空帧), 不再作为选帧门槛; 更可靠是用 rig 的屏幕追踪(-Doptifineoforge.traceScreen=true 打印 OPF-SCREEN 类名)确认最后一屏不是 PauseScreen/GameMenu。

FXAA 现状不变: 通过 5 条(1.20.2、1.20.4、1.21、1.21.1、1.21.4), 有效但低于阈值 2 条(1.20.6、1.21.3); 1.21.6 仍记为**未测得**。


### 1.21.6 再看两帧: aa4 是有效世界帧, aa0 是暂停菜单; 需要屏幕追踪来判屏

先修正假设: 1.21.6 的 level.dat 确实有 Player 复合标签(Rotation list<5>、Pos list<6>), pin 也是原地写入成功(yaw -176.3228 -> 179.9272, pitch 25.19919 -> 16.19919, Pos 不变), 故两轮起点按设计相同 —— 不是无 Player 标签导致随机朝向。

直接看两帧: aa4(425130 B, 密度 17.94)是正常雪地世界画面(地形/树/手持物品/物品栏, 含 Saved Screenshot 提示); aa0(339070 B, 密度 7.94)是 Game Menu 暂停界面, 背后是模糊的同一片雪地。即 aa4 那轮是好的, aa0 那轮被菜单污染, 96.9% 的场景差全部来自这张菜单。

结论: 边缘密度与文件体积都不足以判出菜单(菜单密度 7.9 对世界帧 8.0, 几乎一样; 上一轮菜单是 9.07/245186B)。唯一可靠判据是 rig 的屏幕追踪: -Doptifineoforge.traceScreen=true 启动后客户端每次 setScreen 打印 OPF-SCREEN 类名, 据此判断最后一屏是否为 PauseScreen/GameMenuScreen; 若是则丢弃该轮帧、发 Esc 关菜单后重取。下一轮实现该判屏路径再取 1.21.6 的 aa0。


### 用户指示: 不重视 FXAA, 先保证 mod 能正常运行

据此调整优先级: **FXAA 判定暂停推进**(不删除、不扭曲已记录结果), 重心回到'mod 在真实游戏里能正常运行'。已保留的 FXAA 记录: 通过 5 条(1.20.2/1.20.4/1.21/1.21.1/1.21.4), 有效但低于阈值 2 条(1.20.6/1.21.3), 1.21.6 未测成(暂停菜单污染, 原因与判据已查明); 这些不再作为发布门槛继续投入。

新的工作顺序: ①用当前分支头的 jar 逐线复验建存档/载入世界/画面出来/无崩溃(四检 + 进世界), 尤其刚改过 payload 与 stub 语义的四条 FML10 线; ②修不通过处; ③之后再看是否回到 FXAA。

本轮已启动四条 FML10 线(1.21.9/1.21.10/1.21.11/26.1.2)的进世界复验(capture-all-lines.ps1 -Only), 结果写 logs/inworld-sweep.txt, 完成后逐条登记。


### 回到'mod 能否正常运行': 1.21.9 用当前 jar 通过(joined=1, 无崩溃)

按用户指示转向主线: 用当前 jar 复验四条 FML10 线的进世界。

先踩一次坑(值得记录): capture-all-lines.ps1 -Only 跑出两条线都是 NO-JOIN / frame 0 B / no window found, 且实例 latest.log 未更新、launch err.log 0 字节 —— 即客户端压根没启动, 又是嵌套 PowerShell 启动静默失败的老问题(该脚本内部用 Start-Process powershell), 与 mod 无关; **不可把这种 NO-JOIN 记成 mod 的失败**。

改用内联方式(全加引号字符串 + Start-Process, 与之前成功抓帧同一套)后 1.21.9: Setting user=1, Sound engine=1, joined=1, 新崩溃=0, latest.log=182143 B(18:34:07 写入)—— 即 1.21.9 用当前 payload 与 own-classes jar 在真实游戏里正常进世界、无崩溃。

下一轮: 同样内联最小命令逐条复验 1.21.10、1.21.11、26.1.2, 然后 11 条 ModLauncher 线; 每条记录 Setting user / Sound engine / joined / 新崩溃数 / latest.log 大小。FXAA 暂停推进。


### FML10 三条线(1.21.9 / 1.21.10 / 1.21.11)用当前 jar 均通过进世界检验

内联最小命令(launch-fml10.ps1, 全加引号 + Start-Process; mods = optifine-payload-fml10.jar + optifine-own-classes.jar; -GameArgs --quickPlaySingleplayer=RigSession), 每条等 190 秒统计:

1.21.9: Setting user=1, Sound engine=1, joined=1, 新崩溃=0, latest.log=182143 B。

1.21.10: Setting user=1, Sound engine=1, joined=1, 新崩溃=0, latest.log=183775 B。

1.21.11: Setting user=1, Sound engine=1, joined=1, 新崩溃=0, latest.log=179993 B。

即三条线用当前分支头的 payload/own-classes jar 在真实游戏里正常进世界、无崩溃。下一轮: 26.1.2 同样复验; 随后 11 条 ModLauncher 线。


### 26.1.2 通过 → 四条 FML10 线全部通过进世界复验(当前 jar)

26.1.2(neoforge-26.1.2.109, payload 3324404 B + own-classes, 内联 launch-fml10.ps1, 等 190 秒): Setting user=1, Sound engine=1, joined=1, 新崩溃=0, latest.log=185299 B(18:48:57)。

FML10 四条汇总(全部当前分支头 jar): 1.21.9 joined=1/182143B、1.21.10 joined=1/183775B、1.21.11 joined=1/179993B、26.1.2 joined=1/185299B; 新崩溃均 0。

下一轮起: 11 条 ModLauncher 线逐条内联复验(1.20.1/1.20.2/1.20.4/1.20.6/1.21/1.21.1/1.21.3/1.21.4/1.21.6/1.21.7/1.21.8), 命令形如 launch.ps1 -VersionId <prof> -Seconds 300 -Fresh -Mods '<registered.jar>;<optifine.jar>' -ExtraGameArgs '--quickPlaySingleplayer=RigSession' -JavaHome <jdk17 或 jdk21>。


### ModLauncher 线开始逐条复验: 1.20.1 通过

1.20.1(profile 1.20.1-forge-47.4.23, modlauncher, JavaHome jdk-17, mods = OptifiNeoforge-2.0.0+mc1.20.1-registered.jar + optifine-OptiFine_1.20.1_HD_U_I6.jar; 内联 launch.ps1 -ExtraGameArgs --quickPlaySingleplayer=RigSession, 等 190 秒): Setting user=1, Sound engine=1, joined=1, 新崩溃=0, latest.log=141107 B(18:53:48)。

进世界复验累计 5/15 条: 四条 FML10(1.21.9/1.21.10/1.21.11/26.1.2)+ 1.20.1。待验 10 条: 1.20.2/1.20.4/1.20.6/1.21/1.21.1/1.21.3/1.21.4/1.21.6/1.21.7/1.21.8。


### 1.20.2 通过 —— 目标里点名的 canSustainPlant 崩溃未复现

1.20.2(neoforge-20.2.88, JavaHome jdk-17, mods = OptifiNeoforge-2.0.0+mc1.20.2-registered.jar + optifine-OptiFine_1.20.2_HD_U_I7_pre1.jar; 内联 launch.ps1 --quickPlaySingleplayer=RigSession, 等 190 秒): Setting user=1, Sound engine=1, joined=1, Preparing spawn area=4(世界生成确实跑了), 方法错误行=0(日志无 canSustainPlant / NoSuchMethodError / NoClassDefFoundError / AbstractMethodError), 新崩溃=0, latest.log=1345018 B(18:59:32)。

即目标中记的 1.20.2 建世界崩溃(BlockState.canSustainPlant)在当前离线 jar 上不复现, 与该线'1.20.x loader 未消费 runtime-interfaces 计划'的修复结论一致。

进世界复验累计 6/15: 四条 FML10 + 1.20.1 + 1.20.2; 待验 1.20.4/1.20.6/1.21/1.21.1/1.21.3/1.21.4/1.21.6/1.21.7/1.21.8。


### 1.20.4 通过 —— 且 stderr = 14481 B(记录值), 目标里的 natives 疑点结清

1.20.4(neoforge-20.4.251, JavaHome jdk-17, mods = OptifiNeoforge-2.0.0+mc1.20.4-registered.jar + optifine-OptiFine_1.20.4_HD_U_I7.jar; 内联 launch.ps1 --quickPlaySingleplayer=RigSession, 等 190 秒): Setting user=1, Sound engine=1, joined=1, Preparing spawn area=10, 方法错误行=0, 新崩溃=0, latest.log=1113496 B, stderr=14481 B。

这结清了目标第三项: rig 自己按线选 LWJGL natives(natives-for.ps1 未被调用)导致 1.20.4 报 17841 字节 stderr 而非记录值 14481 —— 本次 stderr 正是 14481 B, 与记录值一致。

如实说明: stderr 中确有一行 java.lang.NoClassDefFoundError: net/minecraft/world/level/block/state/BlockState, 来自 OptiFine 的 ReflectorMethod.getMethod 探测(属那 14481 B 既有内容), 客户端随后正常进世界, 非致命错误。

进世界复验累计 7/15: 四条 FML10 + 1.20.1 + 1.20.2 + 1.20.4; 待验 1.20.6/1.21/1.21.1/1.21.3/1.21.4/1.21.6/1.21.7/1.21.8。


### 1.20.6 通过; 且 stderr 里 0 条 natives 不匹配警告

1.20.6(neoforge-20.6.141, JavaHome jdk-21, mods = OptifiNeoforge-2.0.0+mc1.20.6-registered.jar + optifine-OptiFine_1.20.6_HD_U_J1_pre18.jar; 内联 launch.ps1 --quickPlaySingleplayer=RigSession, 等 190 秒): Setting user=1, Sound engine=1, joined=1, Preparing spawn area=2, 方法错误行=0, 新崩溃=0, latest.log=136971 B, stderr=17856 B。

natives 核对(把 1.20.4 记录值的做法推广到该线): stderr 里 Incompatible Java and native library versions detected 警告 0 条(1.20.4 也 0 条, 总 14481 B)。即 1.20.6 的 17856 B 不是 natives 不匹配, 而是该线正常 stderr 内容(162 行, 主要为 FML/ModLauncher 启动栈各重复 6 次)。

进世界复验累计 8/15: 四条 FML10 + 1.20.1 + 1.20.2 + 1.20.4 + 1.20.6; 待验 1.21/1.21.1/1.21.3/1.21.4/1.21.6/1.21.7/1.21.8。

