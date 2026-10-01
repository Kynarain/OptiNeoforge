# 发布清单(release checklist)

本文件是**发布前的唯一清单**:列出要发布的产物、每一项门槛的当前状态与证据位置、以及发布的先后顺序。
状态只有三种写法:**done**(有证据, 附证据指针)、**paused**(用户明确要求暂停)、**pending**(还没做)。
不写"大概通过"。

## 1. 要发布的产物(三条线, 15 个)

当前三条线的基线都是 `mod_version_base=2.0.0`(`gradle.properties`),因此产物名是
`OptifiNeoforge-2.0.0+mc<MC 版本>.jar`。**发布不需要升版**;见 §4 的升版规则。

| 仓库(线) | `minecraft_version` | 产物 |
|---|---|---|
| `OptifiNeoforge-120x`(1.20.x) | 1.20.4 | `OptifiNeoforge-2.0.0+mc1.20.1.jar` |
| | | `OptifiNeoforge-2.0.0+mc1.20.2.jar` |
| | | `OptifiNeoforge-2.0.0+mc1.20.4.jar` |
| | | `OptifiNeoforge-2.0.0+mc1.20.6.jar` |
| `OptifiNeoforge`(1.21.x) | 1.21.4 | `OptifiNeoforge-2.0.0+mc1.21.jar` |
| | | `OptifiNeoforge-2.0.0+mc1.21.1.jar` |
| | | `OptifiNeoforge-2.0.0+mc1.21.3.jar` |
| | | `OptifiNeoforge-2.0.0+mc1.21.4.jar` |
| | | `OptifiNeoforge-2.0.0+mc1.21.6.jar` |
| | | `OptifiNeoforge-2.0.0+mc1.21.7.jar` |
| | | `OptifiNeoforge-2.0.0+mc1.21.8.jar` |
| | | `OptifiNeoforge-2.0.0+mc1.21.9.jar` |
| | | `OptifiNeoforge-2.0.0+mc1.21.10.jar` |
| | | `OptifiNeoforge-2.0.0+mc1.21.11.jar` |
| `OptifiNeoforge-26x`(26.x) | 26.1.2 | `OptifiNeoforge-2.0.0+mc26.1.2.jar` |

发布渠道:**目前只有 GitHub Release**(见 `PUBLISHING.md`:CurseForge / Modrinth 的账号与上传脚本都还没有)。
CurseForge 项目的恢复由本人在网页端处理(恢复时填 名称 `OptifiNeoforge`、slug `optifineoforge`)。

## 2. 门槛与现状

### 2.1 四检启动验收(每条线)

判定:`VERDICT: STARTED` + `Setting user` + `Sound engine started` + **0 个新崩溃报告** + 记录 stderr 字节数。

- 状态:**done**(15/15)。每条线的四条与 latest.log 字节数逐条记录在
  `optifineoforge-test/STATUS-2026-09-27-modlauncher-sweep.md` 的汇总表(2026-10-01, 续九十七)。

### 2.2 真机建存档 + 进世界(每条线)

判定:真实客户端用该线的 jar 进入世界(有 `Preparing spawn area` 或 `joined the game`),**0 个新崩溃**。

- 状态:**done**(15/15),同一张汇总表。1.20.4 的 stderr 为记录值 **14481 B**;15 条线的
  `Incompatible Java and native library versions detected` 警告均为 **0**(2026-10-01, 续一百零五)。

### 2.3 光影包 + FXAA(每条线)

判定:在有光影包的条件下进世界、取帧,并给出 FXAA 的可见性判定。

- 状态:**paused**(用户 2026-10-01 指示:"不需要重视 fxaa,先保证 mod 能正常运行")。
- 已记录的既有结果(保留, 不再作为门槛继续投入):可见 5 条(1.20.2 / 1.20.4 / 1.21 / 1.21.1 / 1.21.4),
  有效但低于 2.0% 阈值 2 条(1.20.6 / 1.21.3),未测成 1 条(1.21.6,原因是暂停菜单污染取帧,判据与原因已查明)。
- 结论:**这一节没有 done 之前, 不发布**。

### 2.4 游戏复测的机器占用

- 状态:**paused**。用户 2026-10-01 起暂停游戏复测(在玩 CS2),期间不启动任何客户端、也不跑重构建(避免抢占 CPU/GPU)。

### 2.5 由仓库自己的流水线出包

判定:每个产物都由本仓库 `gradlew` 构建出来,产物名与 `release\version.ps1 show` 报告的一致。

- 状态:**pending**。`build\libs` 里有各线近期产物(2026-10-01 07:xx–08:xx),但**本轮没有为发布重新构建**
  (机器在被游戏占用,重构建会掉帧)。恢复后要做:三条线各跑一次构建、逐个核对 §1 的名字与哈希。
- 注意(实测):FML 10 线的 payload 由 `build-fml10-payload.ps1` 组装,其中
  **粒子修复由处理器在装载时完成**(单一归属)、**keep 计划由 staging 单一路径写入**
  (`keep plan: created from keep-additions-<line>.txt`,2026-10-01 续一百零四)。两者都不再是手工步骤。

## 3. 发布顺序(恢复复测后照此执行)

1. 三条线各自 `gradlew` 构建,核对 §1 的 15 个产物名与哈希 → 记录;
2. 用**这批 jar** 重跑 §2.1 与 §2.2(每条线),记录四检 + 进世界 + 0 新崩溃;
3. 补 §2.3:用户解除暂停后按既有成对流程给出 FXAA 判定,或由用户明确"这一节不作为门槛";
4. 只有 1-3 全绿,才在三个仓库上打 tag、写 GitHub Release(说明按英文在前的规则,见 `DESCRIPTION.md`);
5. **发布前不要动版本号**:见 §4。

## 4. 升版规则(`docs/VERSIONING.md` 的摘要)

- 产物版本 = `<MAJOR>.<MINOR>.<PATCH>+mc<MC 版本>`;三条线各自独立升版,同线内不同 MC 版本也可停在不同版本号。
- **MAJOR**:需要用户改 mods 目录 / 改 mod id / 改元数据,或不再支持某个 MC 版本。
- **MINOR**:新增支持的 MC 版本,或新增用户能在游戏里用到的功能。
- **PATCH**:只改行为、不改使用方式。
- 升版**只能用** `release\version.ps1`(`show` / `major` / `minor` / `patch` / `-Base`);只有 `1.0.0` 才用 `-Base` 明确设定。
- **升版会移动分支头** ⇒ 会作废基于旧头的验收证据。因此:要么不升版直接发布当前 `2.0.0`,
  要么**先升版、再跑 §3 的第 2 步**。不要"先验收、后升版、直接发"。
