# 现状(STATE)

一页式现状。每条结论都写**日期**与**证据在哪**;状态只有 `done` / `paused` / `pending`,不写"大概"。
发布前的执行清单见 `RELEASE-CHECKLIST.md`;逐条测量流水账在 rig 的
`STATUS-2026-09-27-modlauncher-sweep.md`(约 250 KB, 本文件是它的压缩版)。

## 0. 项目是什么

同一个 GitHub 仓库 `Kynarain/OptifiNeoforge` 的三个分支,共 **15 条线**(每条线 = 一个 Minecraft 版本):

| 检出 / 分支 | 线 | 构建方式 |
|---|---|---|
| `OptifiNeoforge` / `1.21.x` | 1.21、1.21.1、1.21.3、1.21.4、1.21.6、1.21.7、1.21.8、1.21.9、1.21.10、1.21.11 | 1.21~1.21.8 `-Pmountpoint=modlauncher`;1.21.9+ `-Pmountpoint=fml10` |
| `OptifiNeoforge-120x` / `1.20.x` | 1.20.1、1.20.2、1.20.4、1.20.6 | `-Pmodlauncher=10`(1.20.6 用 `=11` 且 `-Ptarget_java_version=21`) |
| `OptifiNeoforge-26x` / `26.x` | 26.1.2 | `-Pmountpoint=fml10` |

版本号基线三条线都是 **`2.0.0`**(`gradle.properties` 的 `mod_version_base`;只有 `release\version.ps1` 能改它)。
产物名 `OptifiNeoforge-2.0.0+mc<MC>.jar`。**发布不必升版**;升版会移动分支头、从而作废基于旧头的验收证据。

## 1. 门槛现状

| 门槛 | 状态 | 日期 | 证据 |
|---|---|---|---|
| 四检启动验收(VERDICT / Setting user / Sound engine started / 0 崩溃 + stderr 字节) | **done 15/15** | 2026-10-01 | rig 台账"续九十七"汇总表 |
| 真机建存档 + 进世界(`Preparing spawn area` 或 `joined the game`,0 新崩溃) | **done 15/15** | 2026-10-01 | 同上;1.20.4 stderr = **14481 B**(记录值);15 条线 natives 不匹配警告 **0**(续一百零五) |
| 光影包 + FXAA 像素判定 | **paused** | 2026-10-01 | 所有者指示"先保证 mod 能正常运行";既有结果见下 |
| 游戏复测期间独占机器 | **paused** | 2026-10-01 起 | 所有者在玩游戏(CS2) |
| 由仓库自己的 Gradle 重建 15 个产物 | **pending** | — | 需机器空闲;见 `RELEASE-CHECKLIST.md` §3 |

FXAA 既有结果(保留, 不再作为门槛继续投入):VISIBLE — 1.20.2、1.20.4、1.21、1.21.1、1.21.4(2026-10-01 成对测量);
VISIBLE(2026-09-23 测量, 本轮未重测)— 1.21.8、1.21.9、1.21.10、1.21.11;低于 2.0% 阈值 — 1.20.6、1.21.3;
NOT PROVEN(未测)— 1.20.1、1.21.6、1.21.7、26.1.2。

## 2. 曾经点名的问题, 现在各是什么状态

| 问题 | 状态 | 依据 |
|---|---|---|
| 1.20.2 建世界崩 `NoSuchMethodError BlockState.canSustainPlant` | **不再复现** | 2026-10-01:世界生成跑了(spawn area ×4)、进世界、日志 0 条方法错误、0 崩溃 |
| 1.21 资源重载不结束 / 声音引擎永不启动 | **不再复现** | 2026-10-01:`Sound engine started` 出现、重载完成、进世界 |
| 1.20.4 stderr 17841 而非 14481(natives 选择) | **已结清** | 2026-10-01:stderr = **14481 B**(记录值);`natives-for.ps1` 确由 `test-save-shaders.ps1` 按线调用(续一百零五) |
| 26.1.2 离线 payload 缺粒子修复 | **已关闭(改为装载时修复)** | 2026-10-01:构建期补丁撤掉, 处理器 `repairParticleProviderLookup` 在装载时改写并打 INFO;成品 jar 不再需要该补丁(续一百零一) |
| 1.21.9"光影配置异常" | **关闭(不可复现且非该线独有)** | 2026-10-01:1.21.9 与 1.21.10 的 `[Shaders]` 日志逐行一致(含两条 `fxaa_of_2x/4x.json` not-found)(续一百零二) |
| multiplayer `/register` | **paused(性质是测量缺口, 不是 mod 功能)** | 它是用户服务器上 AuthMe 的未登录提示;我当时未(也不会)在服务器上注册;缺的是"键盘注入→聊天栏回显"这条链路的证明(续一百零三) |
| 1.21.6 / 1.21.7 光影包不加载 | **open** | 09-23 记录, 本轮未复测 |

## 3. 构建流水线(2026-10-01 审计后)

- **粒子修复**:由 payload 处理器在装载时完成(单一归属),构建期为它写入 `/optifineoforge/runtime-location.txt`。
- **keep 计划**:由 `build-fml10-payload.ps1` 的 staging 单一路径写入;无 PayloadDrift 提案时由 `keep-additions-<line>.txt` **自己建立**计划。
- **payload 输入** `keep-additions-*.txt` / `stub-additions-*.txt`:已**纳入仓库版本控制**(`release\payload-inputs\`,按线归属;rig 里的副本作兜底),
  `build-fml10-payload.ps1` 会打印**实际用了哪个文件**。
- **发布脚本**:`build-release-jars.ps1` 与 `publish-github-releases.ps1` 的版本号都改为**按线读 `gradle.properties`**(不再写死),测量日期改为 `$MeasuredOn` 参数。
- **仍未版本控制的**:流水线脚本本身(`build-fml10-payload.ps1`、`launch*.ps1`、`test-save-shaders.ps1`、两个发布脚本 …)只在 rig 里, 而 **rig 不是 git 仓库**。这是结构性缺口,待定。

## 4. 已知陷阱(接手前先读)

- `jars-*` 里每个 ModLauncher 线都还有 **1.0.0 时代(09-22)的正确命名旧产物**。按名通配的脚本都**按 LastWriteTime 倒序取最新**,所以不会误用;
  但"取第一个匹配"的临时命令会拿到旧 jar。
- 2026-09-27 那次改名试验的残留已**隔离**:11 个拼错名 `OptiNeoforge-…-registered.jar` → `jars-STALE-typo-OptiNeoforge\`;
  15 个拼错名的发布暂存 jar → `release-stage-STALE-2026-09-27-typo-OptiNeoforge\`。两边都有 README 说明。
- `release-stage\` 现在是**空的**(旧的 15 个已隔离),所以任何发布运行都会逐条 `SKIPPED`,而不是拿到旧产物。
- 记录纪律:凡是"验证输出"必须**运行之后**贴(续一百一十记了一次我先写后测的自纠)。

## 5. 下一步(顺序固定)

1. 机器空闲后跑 `build-release-jars.ps1`,用仓库自己的 Gradle 重建 15 个产物到 `release-stage\`(名字按 2.0.0);
2. 用**这批** jar 重跑四检 + 建存档进世界,逐条记录;
3. FXAA 那半道门槛:由所有者解除暂停后测量,或明确"不作为门槛";
4. 只有 1–3 全绿,才在三个分支打 tag 并写 GitHub Release(说明按 `PUBLISHING.md`:没证到的写 `NOT PROVEN` + 原因)。
