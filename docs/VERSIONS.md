# 版本矩阵(1.20.x 线)

本线覆盖 **Minecraft 1.20.1、1.20.2、1.20.4、1.20.6** —— OptiFine 在这条线上出过构建的全部四个版本。数据来源与核对方式写在文末。

## 四个版本各自的参数

| Minecraft | NeoForge | Java | OptiFine 构建数 | 运行期命名空间 | mod 元数据文件 |
|---|---|---|---|---|---|
| `1.20.1` | `net.neoforged:forge:1.20.1-47.1.106` | 17 | 14(2 正式版 + 12 preview) | SRG(待确认) | `META-INF/mods.toml`(待确认) |
| `1.20.2` | `net.neoforged:neoforge:20.2.93` | 17 | 1(0 正式版 + 1 preview) | SRG(待确认) | `META-INF/mods.toml`(待确认) |
| `1.20.4` | `net.neoforged:neoforge:20.4.251` | 17 | 11(1 正式版 + 10 preview) | **SRG(2026-09-19 实测)** | `META-INF/mods.toml`(待确认) |
| `1.20.6` | `net.neoforged:neoforge:20.6.141` | 21 | 3(0 正式版 + 3 preview) | **官方名(2026-09-19 实测)** | `META-INF/neoforge.mods.toml`(2026-09-19 实测:重打包后能加载) |

- NeoForge 版本是各线在 `maven.neoforged.net` 元数据里的**最新构建**(核对时间见文末),不是"已验证可用"的版本。
- **元数据文件名这一列在 1.20.1 / 1.20.2 / 1.20.4 上仍是预期值**;命名空间这一列现在有两个实测点(见下),分界
  落在 **1.20.6**,与原先的判断一致 —— 但这是量出来的,不是推出来的。
- 命名空间的实测方式(`MissingTargets`,对同一个运行时扫两份 OptiFine 载荷):1.20.4 是 **43 162 条游戏成员引用里
  3 178 条在运行时里找不到**(7.4%,即载荷写的是 SRG 名),1.20.6 是 **43 918 条里只有 23 条找不到**(0.05%,即
  官方名)。1.20.4 的启动失败也正好落在这里:`NoSuchMethodError: Component.m_237115_(String)`。
- 1.20.1 的 NeoForge 是 Forge 时代的产物,坐标是 `net.neoforged:forge:1.20.1-47.1.106`(该线最新的 `47.1.x`),与 1.20.2 起的 `net.neoforged:neoforge` 不同。

## 全线共有的参数

| 项目 | 值 | 依据 |
|---|---|---|
| mod id | `optifineoforge` | 本项目 |
| 产物 | `OptifiNeoforge-<版本>+mc<MC 版本>.jar` | 本项目 |
| 载入方式 | ModLauncher(NeoForge 自带) | OptiFine 的入口是 `cpw.mods.modlauncher.api.ITransformationService` |
| 骨架版本号 | `0.1.0` | 见 `docs/VERSIONING.md` |
| 状态 | 骨架 | 没有可用产物,也没有实测记录 |

## 1.20.1 的全部 OptiFine 构建(14 个)

**正式版**

| 补丁号 | 文件名 |
|---|---|
| `HD_U_I5` | `OptiFine_1.20.1_HD_U_I5.jar` |
| `HD_U_I6` | `OptiFine_1.20.1_HD_U_I6.jar` |

**preview**

| 补丁号 | 文件名 |
|---|---|
| `HD_U_I5_pre4` | `preview_OptiFine_1.20.1_HD_U_I5_pre4.jar` |
| `HD_U_I5_pre5` | `preview_OptiFine_1.20.1_HD_U_I5_pre5.jar` |
| `HD_U_I5_pre6` | `preview_OptiFine_1.20.1_HD_U_I5_pre6.jar` |
| `HD_U_I5_pre7` | `preview_OptiFine_1.20.1_HD_U_I5_pre7.jar` |
| `HD_U_I5_pre8` | `preview_OptiFine_1.20.1_HD_U_I5_pre8.jar` |
| `HD_U_I5_pre9` | `preview_OptiFine_1.20.1_HD_U_I5_pre9.jar` |
| `HD_U_I6_pre1` | `preview_OptiFine_1.20.1_HD_U_I6_pre1.jar` |
| `HD_U_I6_pre2` | `preview_OptiFine_1.20.1_HD_U_I6_pre2.jar` |
| `HD_U_I6_pre3` | `preview_OptiFine_1.20.1_HD_U_I6_pre3.jar` |
| `HD_U_I6_pre4` | `preview_OptiFine_1.20.1_HD_U_I6_pre4.jar` |
| `HD_U_I6_pre5` | `preview_OptiFine_1.20.1_HD_U_I6_pre5.jar` |
| `HD_U_I6_pre6` | `preview_OptiFine_1.20.1_HD_U_I6_pre6.jar` |

## 1.20.2 的全部 OptiFine 构建(1 个)

**正式版**

无。

**preview**

| 补丁号 | 文件名 |
|---|---|
| `HD_U_I7_pre1` | `preview_OptiFine_1.20.2_HD_U_I7_pre1.jar` |

1.20.2 只有这一个构建,没有正式版。它能否作为移植对象尚未验证。

## 1.20.4 的全部 OptiFine 构建(11 个)

**正式版**

| 补丁号 | 文件名 |
|---|---|
| `HD_U_I7` | `OptiFine_1.20.4_HD_U_I7.jar` |

**preview**

| 补丁号 | 文件名 |
|---|---|
| `HD_U_I7_pre2` | `preview_OptiFine_1.20.4_HD_U_I7_pre2.jar` |
| `HD_U_I7_pre3` | `preview_OptiFine_1.20.4_HD_U_I7_pre3.jar` |
| `HD_U_I7_pre4` | `preview_OptiFine_1.20.4_HD_U_I7_pre4.jar` |
| `HD_U_I7_pre5` | `preview_OptiFine_1.20.4_HD_U_I7_pre5.jar` |
| `HD_U_I7_pre6` | `preview_OptiFine_1.20.4_HD_U_I7_pre6.jar` |
| `HD_U_I7_pre7` | `preview_OptiFine_1.20.4_HD_U_I7_pre7.jar` |
| `HD_U_I8_pre1` | `preview_OptiFine_1.20.4_HD_U_I8_pre1.jar` |
| `HD_U_I8_pre2` | `preview_OptiFine_1.20.4_HD_U_I8_pre2.jar` |
| `HD_U_I8_pre3` | `preview_OptiFine_1.20.4_HD_U_I8_pre3.jar` |
| `HD_U_I8_pre4` | `preview_OptiFine_1.20.4_HD_U_I8_pre4.jar` |

## 1.20.6 的全部 OptiFine 构建(3 个)

**正式版**

无。

**preview**

| 补丁号 | 文件名 |
|---|---|
| `HD_U_I9_pre1` | `preview_OptiFine_1.20.6_HD_U_I9_pre1.jar` |
| `HD_U_J1_pre17` | `preview_OptiFine_1.20.6_HD_U_J1_pre17.jar` |
| `HD_U_J1_pre18` | `preview_OptiFine_1.20.6_HD_U_J1_pre18.jar` |

1.20.6 只有 preview,而且跳过了 `I9` 与 `J1` 的绝大多数 pre 版本(镜像列表里只有 `I9_pre1` 与 `J1_pre17`/`J1_pre18`)。

## 下载(第三方镜像)

镜像地址是 `<MC 版本>/<type>/<patch>`,三个字段直接取自构建列表接口的 `type` 与 `patch`;**正式版与 preview 的路径都是同样四段**,区别在切分方式:正式版的 `type` 是 `HD_U`、`patch` 是字母号(如 `I6`),preview 的 `type` 是 `HD_U_<字母号>`、`patch` 是 `pre<n>`。请求会 302 跳到官方分发,实测返回 `200` 且 `Content-Type: application/java-archive`。

```powershell
# 1.20.1:正式版 I6
curl.exe -L -o OptiFine_1.20.1_HD_U_I6.jar `
  "https://bmclapi2.bangbang93.com/optifine/1.20.1/HD_U/I6"

# 1.20.1:最新 preview I6_pre6
curl.exe -L -o preview_OptiFine_1.20.1_HD_U_I6_pre6.jar `
  "https://bmclapi2.bangbang93.com/optifine/1.20.1/HD_U_I6/pre6"

# 1.20.2:唯一的构建(只有 preview)
curl.exe -L -o preview_OptiFine_1.20.2_HD_U_I7_pre1.jar `
  "https://bmclapi2.bangbang93.com/optifine/1.20.2/HD_U_I7/pre1"

# 1.20.4:正式版 I7,以及最新 preview I8_pre4
curl.exe -L -o OptiFine_1.20.4_HD_U_I7.jar `
  "https://bmclapi2.bangbang93.com/optifine/1.20.4/HD_U/I7"
curl.exe -L -o preview_OptiFine_1.20.4_HD_U_I8_pre4.jar `
  "https://bmclapi2.bangbang93.com/optifine/1.20.4/HD_U_I8/pre4"

# 1.20.6:最新 preview J1_pre18
curl.exe -L -o preview_OptiFine_1.20.6_HD_U_J1_pre18.jar `
  "https://bmclapi2.bangbang93.com/optifine/1.20.6/HD_U_J1/pre18"
```

自查某个版本有没有构建(判据是返回的正文是否为空数组,不要只看状态码):

```powershell
curl.exe -s "https://bmclapi2.bangbang93.com/optifine/1.20.1"   # -> 14 条(I5、I6 与各自的 preview)
curl.exe -s "https://bmclapi2.bangbang93.com/optifine/1.20.2"   # -> 1 条(I7_pre1)
curl.exe -s "https://bmclapi2.bangbang93.com/optifine/1.20.3"   # -> []
curl.exe -s "https://bmclapi2.bangbang93.com/optifine/1.20.5"   # -> []
```

## 为什么这条线是这四个版本

OptiFine 在 1.20 系列里只发布了 1.20.1、1.20.2、1.20.4、1.20.6 的构建:**1.20.3 与 1.20.5 的构建列表都是空数组**,没有 OptiFine 就没有可移植的对象。NeoForge 侧这两版都有构建,但本项目只支持有 OptiFine 的版本。1.20.2 虽然只有唯一一个 preview,仍然在矩阵里,因为它确实存在构建。

## 这条线内部的五处分界(前三处已实测)

1. **NeoForge 坐标**:1.20.1 是 `net.neoforged:forge:1.20.1-47.1.106`,1.20.2 起是 `net.neoforged:neoforge`。
2. **Java 版本**:1.20.1 / 1.20.2 / 1.20.4 是 17,1.20.6 起是 21。
3. **元数据文件名**(实测):1.20.1 – 1.20.4 的 FML 只读 `META-INF/mods.toml`,1.20.6 两个名字都读。判据是各版 FML loader jar 里的字面常量,并与各版 NeoForge 自己产物里的文件名一致(见下表)。
4. **FML 的 API 包名**(实测):1.20.1 是 `net.minecraftforge.fml` + `net.minecraftforge.eventbus.api`;1.20.2 起是 `net.neoforged.fml` + `net.neoforged.bus.api`。判据是各版 FML jar 里的包结构 —— 47.1.106 的 loader jar 里没有 `net/neoforged/**`,20.2.88 的里没有 `net/minecraftforge/fml/**`。这决定了 mod 骨架源码要按哪一套 import 写,所以本分支按目标选源码根(`src/forge` 与 `src/neoforged`)。
5. **NeoForge 侧登记用的 mod id 与版本**(实测):1.20.1 登记为 `forge` / `47.1.106`(产物坐标却是 `1.20.1-47.1.106`),1.20.2 起是 `neoforge` / 与该版坐标同名。判据是各版 NeoForge 自己产物里的 `META-INF/mods.toml`。依赖块里写错 id 会去要一份该发行里不存在的 mod。

| 目标 | NeoForge 自己的元数据文件 | 登记 mod id | FML loader 读的名字 | OptiFine/ModLauncher |
|---|---|---|---|---|
| 1.20.1 | `META-INF/mods.toml` | `forge` | `mods.toml` | ModLauncher 10.0.9、fancymodloader 47.2.2 |
| 1.20.2 | `META-INF/mods.toml` | `neoforge` | `mods.toml` | ModLauncher 10.0.9、fancymodloader 1.0.16 |
| 1.20.4 | `META-INF/mods.toml` | `neoforge` | `mods.toml` | ModLauncher 10.0.9、fancymodloader 2.0.17 |
| 1.20.6 | `META-INF/neoforge.mods.toml` | `neoforge` | 两个名字都读 | ModLauncher 11、fancymodloader 3.0.45 |

因此这条线的四个产物各自独立构建、独立验证,**同一个 jar 不能跨版本使用**。

## 待确认(骨架阶段的已知缺口)

- **`mods.toml` → `neoforge.mods.toml` 的确切切换点:已结清**(见上"五处分界"第 3 条)。1.20.1 – 1.20.4 只读 `META-INF/mods.toml`,1.20.6 两个名字都读 —— 也就是说 20.2.x / 20.4.x **并不**接受 `neoforge.mods.toml`,原先"预期只读旧名"这一半是对的,而"是否已经两种都认"这一问的答案是否。
- **运行期命名空间的确切切换点:已量到两个端点(2026-09-19)。** 用 `MissingTargets` 拿同一个运行时扫两份载荷:1.20.4 是 43 162 条游戏成员引用里 **3 178 条找不到**(7.4% → SRG 名),1.20.6 是 43 918 条里 **23 条找不到**(0.05% → 官方名)。所以切换点在 **1.20.4 与 1.20.6 之间**,1.20.6 属于官方名那一侧(原先"大约从 1.20.5/1.21 前后"的预期方向对、落点偏晚)。1.20.1 与 1.20.2 仍**未实测**,只能按 1.20.4 类推。**别与"五处分界"第 4 条混起来**:那条查的是 FML 的 **API 包名**(编译期的事),这条是 Minecraft 类的 **运行期名**(补丁负载按哪套命名空间存放),两者互不相干。
- **元数据的字段要求**:各版本 `mods.toml` / `neoforge.mods.toml` 的必填字段(`loaderVersion` 的取值范围、`modLoader` 取值等)需要对着对应 NeoForge 版本的文档核对。**已核对一部分**:读各版 NeoForge 自己产物里的元数据,`loaderVersion` 分别是 1.20.1 的 `[24,]`、20.2.88 与 20.4.251 的 `[1,]`、20.6.141 的 `[3,]`,`modLoader` 都是 `javafml`,登记的 mod id 见上表。本分支模板写 `loaderVersion = "[1,)"`,对四者都成立 —— 它声明的是"本 mod 接受哪些 FML",不是"本 mod 要求哪个 FML"。
- **OptiFine 的 ModLauncher 服务是否还会被自动发现:1.20.4 与 1.20.6 已实测为"会"。** 两个版本的启动日志里都有 `OptiFineTransformationService.onLoad` / `initialize`,且 1.20.6 上服务列表是 `[mixin, OptiFine, mixin-synthetic-package, fml, OptifiNeoforge]` —— 我们自己的服务排在 OptiFine 之后,与 1.21.x 线观测到的顺序一致。无需改走 NeoForge 自己的转换 API。
- **OptiFine 侧的补丁负载**:`optifine.Patcher` 在本仓库的离线管线里对 1.20.4 与 1.20.6 两版客户端都按老流程工作(产出 `srg/**` 下的补丁类,1.20.4 427 个 / 1.20.6 426 个游戏类),命名空间那一问见上面第一行的实测。
- **1.20.2 的唯一 preview**:`I7_pre1` 是否带上完整补丁负载、能否作为移植对象,未验证;这是本线最薄的一环。
- **1.20.1 的 `47.1.x` 与 20.x 的差异边界:部分结清。** 已实测相同:两者都用 `cpw.mods:modlauncher:10.0.9`,元数据文件名都是 `META-INF/mods.toml`。已实测不同:FML 的 API 包名、事件总线坐标(`net.minecraftforge:eventbus` 对 `net.neoforged:bus`)、登记的 mod id(`forge` 对 `neoforge`)。这三处本分支都已按目标处理(源码根 + 显式类路径 + 元数据占位符)。类转换 API 一侧是否还有别的差异,仍未核实。
- **"正式版"不等于"可用"**:构建列表只说明构建存在,不代表能在 NeoForge 上跑通。本线四条都已有实机启动记录,逐条见 `docs/MATRIX.md`。

## 数据来源

- OptiFine 构建列表:`https://bmclapi2.bangbang93.com/optifine/<MC 版本>`(`type`、`patch`、`filename` 三个字段);下载路径按 `<MC 版本>/<type>/<patch>` 拼出,已用 `curl.exe -I` 逐个核对到 302 目标文件名。
- NeoForge 版本:`https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml`(20.2.93 / 20.4.251 / 20.6.141)与 `https://maven.neoforged.net/releases/net/neoforged/forge/maven-metadata.xml`(1.20.1-47.1.106,该线最新 `47.1.x`)。元数据 `<lastUpdated>` 为 `20260913090722`。
- 核对时间:2026-09-14。
