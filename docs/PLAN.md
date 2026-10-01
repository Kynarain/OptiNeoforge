# 设计与里程碑(26.x 线)

## 目标

让 OptiFine 在 **NeoForge** 上工作,做法与 OptiFabric 在 Fabric Loader 上一样:不去重新实现 OptiFine,而是

1. 用 OptiFine 自带的补丁器把它自己的补丁打进原版客户端;
2. 重建补丁类里被搬走的 lambda;
3. 按目标版本的命名空间把结果对齐;
4. 把打过补丁的 Minecraft 类交给 NeoForge 的类转换流程,让它们顶替原版类。

OptiFabric 在 Fabric 侧走的是 `GameTransformer.patchedClasses`。NeoForge 侧对应的位置还没有最终确定,这是本线第一个要解决的问题(见下)。

## 与 Fabric 线的关键差异

| 方面 | OptiFabric(Fabric) | 本项目(NeoForge) |
|---|---|---|
| 补丁时机 | Fabric Loader 的 `GameTransformer`,在 Mixin 之前 | ModLauncher / NeoForge 的转换流程,顺序需要核实 |
| OptiFine 自身 | 纯字节码补丁 + 自己的类,没有 loader 集成 | **自带 Forge 时代的 loader 集成**(`optifine.OptiFineTransformationService`) |
| 元数据 | 不涉及 | OptiFine 的 jar 里是 `META-INF/mods.toml`,NeoForge 期望自己的那份 |
| 命名空间 | official → intermediary | 26.x 线未混淆(官方名);1.20.x/1.21.x 线要按各自的 SRG/正式名处理 |
| 第三方补丁 | 只有 Fabric API 的 mixin | NeoForge 自己也会改原版类,补丁需要合并 |

## 两条可选路线

**路线 A —— 让 OptiFine 自己的 ModLauncher 服务跑起来。**
OptiFine 的 `optifine.OptiFineTransformationService` 只依赖 `cpw.mods.modlauncher.*`(`ITransformationService`、`ITransformer<ClassNode>`、`SecureJar`),不引用 `net.minecraftforge.*`。理论上只要让 NeoForge 发现并加载这个服务、并把它的元数据修好,补丁流程就能原样工作。代价是:顺序、投票(`castVote`)、与 NeoForge 自身补丁的合并都不在我们手里。

**路线 B —— 自己跑补丁器,自己交出补丁类(像 OptiFabric)。**
在 `preLaunch` 阶段调用 `optifine.Patcher` 打补丁、重建 lambda、对齐命名空间,然后把结果交给 NeoForge 的转换 API。可控性最高,代价是工作量大,而且要先弄清 NeoForge 允不允许整类顶替。

骨架阶段两条都留着:**先用最小代价验证路线 A 能不能成立**(它是"能不能跑"的问题),同时按路线 B 的形态组织代码(补丁器调用、缓存、fixer 框架都放在 `core` 里,不依赖具体挂载点)。

## 里程碑

| # | 内容 | 完成判据 |
|---|---|---|
| M0 | 骨架:三条分支、版本矩阵、构建配置、文档 | 本提交 |
| M1 | 路线 A 可行性:修好 OptiFine jar 的元数据,让 NeoForge 认它 | 游戏能启动到标题界面,日志里能看到 OptiFine 的转换服务被加载 |
| M2 | 补丁管线:调用 `optifine.Patcher`,建立缓存(`<游戏目录>/.optifine/<OptiFine 版本>/`) | 首次启动完成补丁,二次启动走缓存 |
| M3 | 补丁类注入 + fixer 框架:对齐命名空间、补回被搬走的方法、处理与 NeoForge 自身补丁的重叠 | 进世界不崩,方块/物品/区块渲染正常 |
| M4 | 完整兼容:光影包、抗锯齿、连接纹理;第三方模组(尤其依赖 NeoForge 渲染钩子的) | 与 OptiFabric 在 Fabric 上的验收口径对齐 |
| M5 | 发布:版本脚本、发布说明、CurseForge / Modrinth 元数据 | 能一条命令出包并发布 |

每条线按同样的里程碑推进,但各自独立验收。

## 提交纪律

- 分支互相独立:`1.20.x`、`1.21.x`、`26.x` 各自有自己的 `README`、矩阵、构建配置和文档,不做跨分支的合并。
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

