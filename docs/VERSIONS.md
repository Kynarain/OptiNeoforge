# 版本矩阵(26.x 线)

本线只对应 **Minecraft 26.1.2**。数据来源与核对方式写在表后。

| 项目 | 值 | 依据 |
|---|---|---|
| Minecraft | `26.1.2` | OptiFine 只有这一版的构建 |
| NeoForge | `26.1.2.109` | `maven.neoforged.net` 元数据里 `26.1` 线的最新构建 |
| Java | **25** | 26.1.2 自身的运行要求 |
| 运行期命名空间 | 官方名(未混淆) | 26.1 起游戏不再混淆,没有映射表 |
| mod 元数据文件 | `META-INF/neoforge.mods.toml` | NeoForge 的现代格式;待实测确认 |
| 载入方式 | **FancyModLoader 的 `ClassProcessor`**,不是 ModLauncher | 2026-09-24 实测:NeoForge 26.1.2 / 26.2 的依赖里**已经没有 ModLauncher**;OptiFine 的 jar 同时带 `net.neoforged.neoforgespi.transformation.ClassProcessor` 与 `cpw.mods.modlauncher.api.ITransformationService`,实际生效的挂载点是前者(OptiFine 自己的 processor) |
| mod id | `optifineoforge` | 本项目 |
| 产物 | `OptiNeoforge-<版本>+mc26.1.2.jar` | 本项目 |

## 26.1.2 的全部 OptiFine 构建

| 类型 | 补丁号 | 文件名 |
|---|---|---|
| preview | `HD_U_K1_pre1` | `preview_OptiFine_26.1.2_HD_U_K1_pre1.jar` |
| preview | `HD_U_K1_pre2` | `preview_OptiFine_26.1.2_HD_U_K1_pre2.jar` |

下载(第三方镜像,会 302 跳到官方分发;注意 preview 的路径是四段):

```powershell
curl.exe -L -o preview_OptiFine_26.1.2_HD_U_K1_pre2.jar `
  "https://bmclapi2.bangbang93.com/optifine/26.1.2/HD_U_K1/pre2"
```

自查某个版本有没有构建(判据是返回的正文是否为空数组,不要只看状态码):

```powershell
curl.exe -s "https://bmclapi2.bangbang93.com/optifine/26.1.2"   # -> pre1 / pre2
curl.exe -s "https://bmclapi2.bangbang93.com/optifine/26.1.1"   # -> []
curl.exe -s "https://bmclapi2.bangbang93.com/optifine/26.2"     # -> pre1   (2026-09-24 复核:原文写的是 [],已过时)
curl.exe -s "https://bmclapi2.bangbang93.com/optifine/26.3"     # -> []
```

## 为什么这条线原本只有一版,以及 26.2 的现状(2026-09-24 更正)

`26.1`、`26.1.1` 在 OptiFine 的构建列表里是空数组 —— 没有 OptiFine 就没有可移植的对象。

**但"26.2 没有 OptiFine 构建"这句已经过时**:OptiFine 于 **2026-09-22** 发布了
`preview_OptiFine_26.2_HD_U_K2_pre1.jar`(**7,783,636 字节**,SHA-256 `DB05B25F8AA5AC688A77354680EF939D4F3135350E64E7CCFE5996CFAF925927`),
本机已下载并核验(7340 条目、759 个 `srg/` 类、含 `srg/net/optifine/Config.class`)。**26.2.1 与 26.3 至今仍是一个构建都没有。**

### 26.2 这条线的状态与决定

| | |
|---|---|
| NeoForge | `26.2.0.88`(26.2 线最新),已装入 rig |
| Java | 25 |
| 产物 | 已建出:`optifine-payload-fml10.jar`(3.26 MB)、`optifine-own-classes.jar`(2.77 MB)、`OptiNeoforge-1.0.0+mc26.2.jar`(173 KB) |
| 四项验收 | ❌ **未通过,未通过的原因已知且不在 OptiFine 侧** |
| 决定 | ⏸ **暂停,等 OptiFine 为 26.2 发布新构建后再继续** |

**未通过的实测原因(我们自己的管线问题,不是 OptiFine)**:载荷里的游戏类取自尊原版客户端,而 NeoForge 的类要求
`BlockEntity` 实现它加的 `AttachmentHolder`,于是启动即

```
VerifyError: Bad type on operand stack
  Location: net/neoforged/neoforge/attachment/AttachmentSync.syncBlockEntityUpdates(BlockEntity, List)V
  Reason: Type 'BlockEntity' is not assignable to 'AttachmentHolder'
```

根因是准备载荷时 `-RuntimeJar` 传了**原版** `versions\26.2\26.2.jar`,而 26.x 正确的运行期视图是 NeoForge 安装器产出的
`libraries\net\neoforged\minecraft-client-patched\26.2.0.88\minecraft-client-patched-26.2.0.88.jar`;用原版算出的层次计划里
当然没有 `AttachmentHolder`。对比:26.1.2 的 `work\` 里有 `optifine-patched-reparented.jar`,而 26.2 目前只有 `stubbed`。
**恢复这条线时的第一步就是把这条修掉并复测。**

### 26.2 光影不可用 —— OptiFine 该构建自身的限制(不是待办修复)

OptiFine 26.2 的这个构建把光影包加载**无条件取消**:`Shaders.loadShaderPack` 里那段字节码直接把它关掉,日志是
`[Shaders] No shaderpack loaded.`。姊妹项目 OptiFabric 已实测并记录(其 `docs/DEVELOPMENT.md` 第 3 条、`PORT_26.x.md` 第 4 条):
**把它强行打开会得到 `Loaded shaderpack` 加"只有粒子的画面"**,所以那段字节码原样保留才是对的。

因此 26.2 这条线的验收口径里**不能包含光影与 FXAA** —— 它们在该 OptiFine 构建上不可用,应写成已知限制并附日志证据,
而不是记为"未测"或"失败"。

### 恢复时要做的事(备忘)

1. 用 patched client 重算运行期视图 → 重新准备载荷 → 确认生成 `optifine-patched-reparented.jar` 且其中 `BlockEntity` 带 `AttachmentHolder`;
2. 重跑四项验收 + 存档测试(**不含光影**);
3. 新增线必须先做一次**不带 `-NoEarlyWindow`** 的引导启动以生成 `config/fml.toml`,否则启动器按设计拒绝
   (`no fml.toml … - run once without -NoEarlyWindow first`);`retest-all.ps1` 目前不检测也不自动做这一步,**该缺口应补上**;
4. 引导与验收都要用**版本无关的** `DiagnosticClientAny` 且传 `-JavaExe <JDK 25>`:`DiagnosticClient` 依赖会随 FML 变化的
   `startup(...)` 签名,在 26.2 上会 `NoSuchMethodError`。

## 待确认(骨架阶段的已知缺口)

- `neoforge.mods.toml` 的具体字段要求(`loaderVersion` 的取值范围、`modLoader` 取值)需要对着 NeoForge 26.1.2 的文档核对。
- NeoForge 26.1.2 的 ModLauncher 是否仍会从 `mods/` 里发现第三方的 `ITransformationService`(Forge 时代由 `ModDirTransformerDiscoverer` 负责),还是必须改用 NeoForge 自己的转换 API —— 见 `docs/RESEARCH-neoforge.md`。
- OptiFine 的 26.1.2 构建用的是哪种命名空间的补丁负载,以及它的 `optifine.Patcher` 在未混淆的游戏 jar 上是否仍按老流程工作 —— 见 `docs/RESEARCH-optifine.md`。

## 数据来源

- OptiFine 构建列表:`https://bmclapi2.bangbang93.com/optifine/versionlist`(497 个 MC 版本)与 `https://bmclapi2.bangbang93.com/optifine/<MC 版本>`。
- NeoForge 版本:`https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml`。
- 核对时间:2026-09-14。
