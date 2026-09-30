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
