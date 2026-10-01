# 设计与里程碑(1.20.x 线)

## 目标

让 OptiFine 在 **NeoForge** 上工作,做法与 OptiFabric 在 Fabric Loader 上一样:不去重新实现 OptiFine,而是

1. 用 OptiFine 自带的补丁器把它自己的补丁打进原版客户端;
2. 重建补丁类里被搬走的 lambda;
3. 按目标版本的命名空间把结果对齐;
4. 把打过补丁的 Minecraft 类交给 NeoForge 的类转换流程,让它们顶替原版类。

OptiFabric 在 Fabric 侧走的是 `GameTransformer.patchedClasses`。NeoForge 侧对应的位置还没有最终确定,这是本线第一个要解决的问题(见下)。

本线要覆盖 **1.20.1 / 1.20.2 / 1.20.4 / 1.20.6** 四个 MC 版本。四个版本的 NeoForge 坐标、Java 版本与运行期命名空间各不相同,所以里程碑的每一条都要在四个版本上分别判据,不能由一个版本的结果外推。

## 本线的额外差异

| 方面 | 1.20.x 线的情况 |
|---|---|
| NeoForge 坐标 | 1.20.1 是 Forge 时代的 `net.neoforged:forge:1.20.1-47.1.106`;1.20.2 起是 `net.neoforged:neoforge`(`20.2.93` / `20.4.251` / `20.6.141`) |
| Java | 1.20.1 / 1.20.2 / 1.20.4 是 17,1.20.6 是 21 —— 同一条线内不同 JDK,CI 要分两套 |
| mod 元数据文件 | 1.20.4 及更早预期读 `META-INF/mods.toml`(OptiFine 自带的就是这份),1.20.6 及之后预期读 `META-INF/neoforge.mods.toml`;确切切换点**待确认** |
| 运行期命名空间 | 预期整条线是 **SRG**;官方名大约从 1.20.5/1.21 前后开始,1.20.6 属于哪一侧**待确认** |
| OptiFine 供给 | 1.20.2 只有 `I7_pre1` 一个 preview;1.20.3 与 1.20.5 完全没有 OptiFine |
| 补丁形式 | 1.20.1 – 1.20.4 的 OptiFine 是 Forge 时代的产物,补丁流程与 1.20.6 之后可能不同,需要逐版确认 |

这条线**内部**就跨越了 Forge 时代的 NeoForge 与独立的 `20.x` 系列,这是它与 1.21.x 线最大的不同:1.21.x 线内部各版本的加载器行为基本一致,而这里可能需要在同一份代码里容纳两套元数据与命名空间处理。

## 与 Fabric 线的关键差异

| 方面 | OptiFabric(Fabric) | 本项目(NeoForge) |
|---|---|---|
| 补丁时机 | Fabric Loader 的 `GameTransformer`,在 Mixin 之前 | ModLauncher / NeoForge 的转换流程,顺序需要核实 |
| OptiFine 自身 | 纯字节码补丁 + 自己的类,没有 loader 集成 | **自带 Forge 时代的 loader 集成**(`optifine.OptiFineTransformationService`) |
| 元数据 | 不涉及 | OptiFine 的 jar 里是 `META-INF/mods.toml`;1.20.4 及更早可能直接可用,1.20.6 及之后预期要换成 `neoforge.mods.toml` |
| 命名空间 | official → intermediary | 这条线预期是 **SRG**(与 intermediary 不同,需要目标版本自己的映射表) |
| 第三方补丁 | 只有 Fabric API 的 mixin | NeoForge 自己也会改原版类,补丁需要合并 |

## 两条可选路线

**路线 A —— 让 OptiFine 自己的 ModLauncher 服务跑起来。**
OptiFine 的 `optifine.OptiFineTransformationService` 只依赖 `cpw.mods.modlauncher.*`(`ITransformationService`、`ITransformer<ClassNode>`、`SecureJar`),不引用 `net.minecraftforge.*`。理论上只要让 NeoForge 发现并加载这个服务、并把它的元数据修好,补丁流程就能原样工作。这条路线在 1.20.x 线上**更值得先试**:这条线的 NeoForge 本来就脱胎于 Forge,发现第三方 `ITransformationService` 的路径可能还在。代价是:顺序、投票(`castVote`)、与 NeoForge 自身补丁的合并都不在我们手里。

**路线 B —— 自己跑补丁器,自己交出补丁类(像 OptiFabric)。**
在 `preLaunch` 阶段调用 `optifine.Patcher` 打补丁、重建 lambda、对齐命名空间,然后把结果交给 NeoForge 的转换 API。可控性最高,代价是工作量大,而且要先弄清 NeoForge 允不允许整类顶替。**1.20.x 线的 SRG 命名空间对齐要在这一层自己做**,没有现成的 intermediary 映射可借。

骨架阶段两条都留着:**先用最小代价验证路线 A 能不能成立**(它是"能不能跑"的问题),同时按路线 B 的形态组织代码(补丁器调用、缓存、fixer 框架都放在 `core` 里,不依赖具体挂载点)。

## 里程碑

| # | 内容 | 完成判据 |
|---|---|---|
| M0 | 骨架:三条分支、版本矩阵、构建配置、文档 | 本提交(文档部分)与随后的构建配置提交 |
| M1 | 路线 A 可行性:修好 OptiFine jar 的元数据,让 NeoForge 认它 | **四个版本各自**能启动到标题界面,日志里能看到 OptiFine 的转换服务被加载 |
| M2 | 补丁管线:调用 `optifine.Patcher`,建立缓存(`<游戏目录>/.optifine/<OptiFine 版本>/`) | 首次启动完成补丁,二次启动走缓存 |
| M3 | 补丁类注入 + fixer 框架:对齐命名空间(SRG),补回被搬走的方法、处理与 NeoForge 自身补丁的重叠 | 逐版本进世界不崩,方块/物品/区块渲染正常 |
| M4 | 完整兼容:光影包、抗锯齿、连接纹理;第三方模组(尤其依赖 NeoForge 渲染钩子的) | 与 OptiFabric 在 Fabric 上的验收口径对齐,四个版本分别验收 |
| M5 | 发布:版本脚本、发布说明、CurseForge / Modrinth 元数据 | 能一条命令出包并发布,四个产物各自打包 |

每条线按同样的里程碑推进,但各自独立验收。本线的 M1 要先回答"切换点在哪":元数据文件名与命名空间的分界一旦确认,四个版本才能各自定下实现。

## 提交纪律

- 分支互相独立:`1.20.x`、`1.21.x`、`26.x` 各自有自己的 `README`、矩阵、构建配置和文档,不做跨分支的合并,也不跨线复制版本号。
- 线内也要按版本立据:`1.20.1` 与 `1.20.6` 的结论不能互相顶替,写文档时要说清是哪一个版本。
- 文档与实测口径要一致:没在真实游戏里验证过的东西写成计划,不写成结论。
- OptiFine 的 jar 不进仓库,也不随产物分发。

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
