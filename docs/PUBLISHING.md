# 发布流程

本文写的是**一条线一次发布**该怎么做。版本号的规则在 `docs/VERSIONING.md`,版本号只通过
`release/version.ps1` 修改。

## 现状(先说实话)

- **已经发布过:2026-09-18,`1.0.0` 的第一批。** 15 / 15 条线全部有实机启动记录(判据是 rig 的
  `VERDICT: STARTED` + `Setting user` + 本次运行没有崩溃报告,逐条数字写在 `docs/MATRIX.md`),
  所以按 `docs/VERSIONING.md` 从 `0.x` 升到 `1.0.0`,并且**一个 Minecraft 版本一个产物、一个标签**
  (jar 里的 `minecraft` 依赖是 `[<该版本>,<该版本>]`,版本号不同就不能混用)。
- **发布了 10 条,5 条已验证但没发 —— 而这五条的构建阻塞现在都已解除**(2026-09-18 下半,逐条实测):
  * `1.20.1` 与 `1.20.2` 原来都解不出依赖。前者那一代 NeoForge 是旧的 `net.neoforged:forge` 坐标,产物 POM 是
    `packaging=pom`、没有依赖、真正的文件带分类符(没有无分类符的 `.jar`,也没有 `.module`),而 ModDevGradle 要的是
    `net.neoforged:neoforge`;后者整个 `20.2.x` 系列都没发布 Gradle Module Metadata(实测 `20.2.88` 没有 `.module`,
    `20.4.251` 与 `20.6.141` 有),所以取不到 `neoforge-moddev-bundle` 变体。这两条现在**不应用**该插件,由 `java`
    插件加一张取自各版自身元数据的显式 `compileOnly` 清单构建 —— 本线源码没有一处 `net.minecraft` / `com.mojang` 的
    import,所以既不下载也不反编译 Minecraft,构建以秒计;代价是没有 `runClient` 开发运行。
  * `1.21.9` / `1.21.10` / `1.21.11` 原来死在 `:compileJava`(实测 1.21.11 报 100 个
    `错误: 程序包cpw.mods.modlauncher.api不存在`):实现 `cpw.mods.modlauncher.api.ITransformationService` 的那批类
    放在 `src/main`,而这三版 NeoForge 已经没有 ModLauncher。那批类现在移进 `src/ml11/java`,由 `-Pmountpoint` 按代次
    选择编不编(`modlauncher` 编,`fml10` 不编)。
- **"能构建"不等于"可发布":这五条仍然没发。** `1.20.1` / `1.20.2` 的产物与已发布的 `1.20.4` / `1.20.6` 同类
  (含 ModLauncher 转换服务,不含载荷);`1.21.9` – `1.21.11` 的产物只有加载器侧工具 + mod 骨架,因为那三条的挂载点是
  我们自己的 `ClassProcessor`,由 rig 从 `26.x` 分支的 `src/fml10` 编译**进载荷 jar**,不在 `1.21.x` 的 Gradle 构建里 ——
  那份载荷 jar 含 OptiFine 的类、按本文档不分发。这一轮**没有重跑任何一次实机启动**(那台机器上没有 rig),所以这五条的
  实机判据仍然只有 `docs/MATRIX.md` 里那一份;发布本身仍是独立的一步。
- **一处构建层面的改正,已发布的资产没有动**:`1.20.x` 线的元数据文件名分界已经量清(1.20.1 – 1.20.4 的 FML 只读
  `META-INF/mods.toml`,1.20.6 两个名字都读),而此前四个目标一律写 `neoforge.mods.toml` —— 也就是说已发布的 1.20.4
  产物里那份元数据,对 20.4.251 的 FML 等于不存在。现在写出的名字与各版自己的产物一致,Release 正文与已上传资产未改。
- **发布的是加载器侧,不是"装进 `mods/` 就能用"的成品。** Release 附的 jar 里没有任何 OptiFine 的类
  (见"不要做的事");能直接放进 `mods/` 的产物仍由 rig 的流水线用**用户自己的** OptiFine jar 合成 ——
  上面每一条验收数字都是这样量出来的。把那条流水线做成 Gradle 任务仍是待办。
- **构建依赖本地已有的 OptiFine jar。** 仓库**不包含、也不分发** OptiFine;`test-downloads/` 被
  `.gitignore` 排除,构建脚本从那里取输入。还没有"从镜像自动下载指定版本 OptiFine"的 CI 流程。
- **只有 GitHub Release 这一条渠道。** CurseForge / Modrinth 的账号、审核与上传脚本都还没有。
- **每一条线都要重跑一遍 neoform 流水线**(非默认 MC 目标要重新下载 MC、NeoForge 与整套库),而这一路上的下载会
  成段断连:2026-09-18 那轮里,1.21.8 第一次构建 15m10s 失败、1.20.6 10m28s、1.21.6 4m40s,重试几乎都能过
  (1.21.8 → 2m51s、1.20.6 → 40s),因为失败的那一次已经把产物落进缓存。**结论:构建失败先重试,并区分"网络"与
  "真构建不出来"** —— 只有后者才写进本文档上面那类"没发"的理由里。
- **断连的根因(2026-09-18 下半量到):坏的是 IPv4 那一侧。** `maven.neoforged.net` 在 CDN77 后面,同时有 AAAA 与
  A 记录;在这台机器上 `curl -4` 连发五次里两次握手直接失败、一次 20 秒超时,成功那两次各约 15 – 17 秒,而同一 URL
  `curl -6` **1.7 秒 200**。NeoForm 的下载器(Java HttpClient)走的正是坏的那一侧,所以报的是
  `SSLHandshakeException: Remote host terminated the handshake` 与 `ConnectException`。
  处置:`JAVA_TOOL_OPTIONS=-Djava.net.preferIPv6Addresses=true`(对所有 JVM 生效,包括 NeoForm fork 出来的
  `java.exe`);对已经停住的下载,按 `~/.gradle/caches/neoformruntime/artifacts/` 下的目标路径**直接用 IPv6 补种**最快 ——
  实测补种 `asm-commons-9.8.jar`(那个目录存在但是空的,于是每次构建都死在同一个文件上)与 1.20.4 的
  `client_mappings`(下到 2 390 577 字节后彻底停住)之后,两次构建立刻通过。
  所以:**遇到这类报错先看 IPv4/IPv6,再谈"重试"。**
- **在 PowerShell 里 `-P` 参数必须加引号。** 裸写 `-Pmc=1.20.6` 会被拆开,Gradle 报
  `Task '.20.6' not found in root project`(`cmd.exe` 下不加引号也可以)。各线 README 的示例命令已经改成带引号。
- `docs/VERSIONING.md` 建议"只有 OptiFine 预览构建可用"的那几条线先走预发布后缀;这次的 10 个标签都是
  正式版本号(`1.0.0+mc…`),OptiFine 构建是 preview 还是 release 写在每个 Release 的正文里 ——
  要不要改成预发布后缀是一件独立的决定,`release/version.ps1` 目前也不生成后缀。

## 前置条件

| 线 | Minecraft | NeoForge | Java | 分支 |
|---|---|---|---|---|
| 1.20.x | 1.20.1 / 1.20.2 / 1.20.4 / 1.20.6 | 见该分支 `gradle.properties` | 17 / 21 | `1.20.x` |
| 1.21.x | 1.21.1 … 1.21.11 | 见该分支 `gradle.properties` | 21 | `1.21.x` |
| 26.x | 26.1.2 | 见该分支 `gradle.properties` | 25 | `26.x` |

发布前必须确认:在**正确分支**上;`gradle.properties` 里的 `minecraft_version` 就是这次要发的版本;
以及**这条线真的跑过** `optifineoforge-test` 里的启动脚本(不要凭另一条线的结果代替)。

## 步骤

1. **升级版本号**(只这样做,不要手改 `gradle.properties`):

   ```
   .\release\version.ps1 show          # 现在是什么,产物会叫什么
   .\release\version.ps1 patch         # 或 minor / major,按 docs/VERSIONING.md 的档位表
   ```

   脚本会打印产物名,形如 `OptiNeoforge-0.1.1+mc26.1.2.jar`。

2. **把 `gradle.properties` 与这次改动的说明放在同一个提交里**,说明写清"为什么要升这一档"。

3. **构建**:

   ```
   .\gradlew build
   ```

   产物在 `build/libs/OptiNeoforge-<版本>+mc<Minecraft 版本>.jar`。构建前请把这条线对应的
   OptiFine jar 放进 `test-downloads/`(构建与重打包工具从那里取输入)。

   **注意(2026-09-15 实测)**:`gradlew build` 产出的是**只含加载器**的 jar —— 1.21.x 上为
   `OptiNeoforge-0.1.0+mc1.21.4.jar`(约 86 KB,内含 `kynarain/cn/optifineoforge/**` 与
   `META-INF/neoforge.mods.toml`)。真正能直接放进 `mods/` 的产物目前仍由 rig 的
   `build-rig-jar.ps1` 流水线产出:重打包 OptiFine → 打补丁 → 生成成员还原计划与供体 → 合并成
   `optifiNeoforge-combined.jar`。所以**这一步还不是完整的发布路径**,把那条流水线做成 Gradle
   任务是待办事项之一。

4. **记录**:把这一轮验证到的、以及验证失败的都写进 `docs/DEVELOPMENT.md`(那份文档就是本项目的
   实测记录,负面结果同样要记)。

5. **打标签并推送**:

   ```
   git tag -a v0.1.1+mc26.1.2 -m "26.x 0.1.1+mc26.1.2"
   git push origin v0.1.1+mc26.1.2
   ```

   标签名与 jar 版本一致,便于"某个版本号对某个产物"。

6. **创建 GitHub Release**,附上第 3 步的 jar,正文至少写:对应的 Minecraft 与 NeoForge 版本、
   这一版能做什么/不能做什么(尤其"加载器是否已在游戏中跑起来"),以及已知问题。

## 不要做的事

- **不要把 OptiFine 的 jar 提交或附进 Release。** 本项目只提供加载器侧的修补,OptiFine 由用户自己
  从官方渠道获取;仓库许可证是 MPL-2.0,但 OptiFine 的再分发条款与本项目无关。
- **不要在一条分支上为另一条线的 Minecraft 版本升版本号。** 三条线的版本号互不相关
  (`docs/VERSIONING.md`),`+mc...` 的编译信息就是为了防止"同一个版本号对应两个产物"。
- **不要在没有实机验证的情况下把 `0.x` 提升到 `1.0.0`。** 升到 `1.0.0` 是在断言"加载器确实能在
  游戏里跑起来",这要由一次真实的启动记录(rig 的 `VERDICT: STARTED` 与截图)来支撑。
