# 版本矩阵(1.21.x 线)

本线覆盖 **Minecraft 1.21 – 1.21.11** —— OptiFine 在这条线上出过构建的全部十个版本(1.21.2 与 1.21.5 没有构建)。数据来源与核对方式写在文末。

## 十个版本各自的参数

| Minecraft | NeoForge | Java | OptiFine 构建数 | OptiFine 正式版 | OptiFine 最新 preview |
|---|---|---|---|---|---|
| `1.21` | `net.neoforged:neoforge:21.0.167` | 21 | 8(0 正式版 + 8 preview) | 无 | `preview_OptiFine_1.21_HD_U_J1_pre9.jar` |
| `1.21.1` | `net.neoforged:neoforge:21.1.250` | 21 | 8(1 正式版 + 7 preview) | `OptiFine_1.21.1_HD_U_J1.jar` | `preview_OptiFine_1.21.1_HD_U_J1_pre15.jar` |
| `1.21.3` | `net.neoforged:neoforge:21.3.97` | 21 | 13(1 正式版 + 12 preview) | `OptiFine_1.21.3_HD_U_J2.jar` | `preview_OptiFine_1.21.3_HD_U_J2_pre12.jar` |
| `1.21.4` | `net.neoforged:neoforge:21.4.157` | 21 | 18(1 正式版 + 17 preview) | `OptiFine_1.21.4_HD_U_J3.jar` | `preview_OptiFine_1.21.4_HD_U_J4_pre2.jar` |
| `1.21.6` | `net.neoforged:neoforge:21.6.20-beta` | 21 | 3(0 正式版 + 3 preview) | 无 | `preview_OptiFine_1.21.6_HD_U_J6_pre3.jar` |
| `1.21.7` | `net.neoforged:neoforge:21.7.25-beta` | 21 | 4(0 正式版 + 4 preview) | 无 | `preview_OptiFine_1.21.7_HD_U_J6_pre7.jar` |
| `1.21.8` | `net.neoforged:neoforge:21.8.54` | 21 | 10(0 正式版 + 10 preview) | 无 | `preview_OptiFine_1.21.8_HD_U_J6_pre16.jar` |
| `1.21.9` | `net.neoforged:neoforge:21.9.16-beta` | 21 | 2(0 正式版 + 2 preview) | 无 | `preview_OptiFine_1.21.9_HD_U_J7_pre2.jar` |
| `1.21.10` | `net.neoforged:neoforge:21.10.64` | 21 | 10(0 正式版 + 10 preview) | 无 | `preview_OptiFine_1.21.10_HD_U_J7_pre11.jar` |
| `1.21.11` | `net.neoforged:neoforge:21.11.45` | 21 | 18(2 正式版 + 16 preview) | `OptiFine_1.21.11_HD_U_J9.jar` | `preview_OptiFine_1.21.11_HD_U_J9_pre4.jar` |

两列 OptiFine 都只表示"该构建存在",不代表可用;这里也不表示正式版比 preview 更适合移植。

- NeoForge 版本是各线在 `maven.neoforged.net` 元数据里的**最新构建**(核对时间见文末),不是"已验证可用"的版本。NeoForge 的版本号是 `<MC 次版本>.<MC 修订号>.<构建号>`,所以 `21.11.45` 对应 1.21.11。
- **只出 beta 的三条线**:`21.6`、`21.7`、`21.9` 的元数据里**没有一个非 beta 版本**,最新构建分别是 `21.6.20-beta`、`21.7.25-beta`、`21.9.16-beta`。这三版只能拿 beta 版 NeoForge 测试。
- 骨架版本号 `0.1.0`,产物为 `OptifiNeoforge-<版本>+mc<MC 版本>.jar`。

## 全线共有的参数

| 项目 | 值 | 依据 |
|---|---|---|
| NeoForge 坐标 | `net.neoforged:neoforge:<版本>` | 这条线的十版都是独立的 `neoforge` 坐标,不再是 1.20.1 的 `forge` |
| Java | **21** | 整条线统一 |
| mod id | `optifineoforge` | 本项目 |
| 载入方式 | ModLauncher(NeoForge 自带) | OptiFine 的入口是 `cpw.mods.modlauncher.api.ITransformationService` |
| mod 元数据文件 | `META-INF/neoforge.mods.toml` | NeoForge 的现代格式;待实测确认 |
| 运行期命名空间 | 官方名(Mojang 名) | 预期值,确切切换点待确认 |
| 状态 | 骨架 | 没有可用产物,也没有实测记录 |

## 1.21 的全部 OptiFine 构建(8 个)

**正式版**

无。

**preview**

| 补丁号 | 文件名 |
|---|---|
| `HD_U_J1_pre1` | `preview_OptiFine_1.21_HD_U_J1_pre1.jar` |
| `HD_U_J1_pre2` | `preview_OptiFine_1.21_HD_U_J1_pre2.jar` |
| `HD_U_J1_pre3` | `preview_OptiFine_1.21_HD_U_J1_pre3.jar` |
| `HD_U_J1_pre4` | `preview_OptiFine_1.21_HD_U_J1_pre4.jar` |
| `HD_U_J1_pre5` | `preview_OptiFine_1.21_HD_U_J1_pre5.jar` |
| `HD_U_J1_pre6` | `preview_OptiFine_1.21_HD_U_J1_pre6.jar` |
| `HD_U_J1_pre8` | `preview_OptiFine_1.21_HD_U_J1_pre8.jar` |
| `HD_U_J1_pre9` | `preview_OptiFine_1.21_HD_U_J1_pre9.jar` |

1.21 这一版没有正式版;列表里从 `pre6` 直接跳到 `pre8`(镜像返回的就是这 8 条)。

## 1.21.1 的全部 OptiFine 构建(8 个)

**正式版**

| 补丁号 | 文件名 |
|---|---|
| `HD_U_J1` | `OptiFine_1.21.1_HD_U_J1.jar` |

**preview**

| 补丁号 | 文件名 |
|---|---|
| `HD_U_J1_pre9` | `preview_OptiFine_1.21.1_HD_U_J1_pre9.jar` |
| `HD_U_J1_pre10` | `preview_OptiFine_1.21.1_HD_U_J1_pre10.jar` |
| `HD_U_J1_pre11` | `preview_OptiFine_1.21.1_HD_U_J1_pre11.jar` |
| `HD_U_J1_pre12` | `preview_OptiFine_1.21.1_HD_U_J1_pre12.jar` |
| `HD_U_J1_pre13` | `preview_OptiFine_1.21.1_HD_U_J1_pre13.jar` |
| `HD_U_J1_pre14` | `preview_OptiFine_1.21.1_HD_U_J1_pre14.jar` |
| `HD_U_J1_pre15` | `preview_OptiFine_1.21.1_HD_U_J1_pre15.jar` |

## 1.21.3 的全部 OptiFine 构建(13 个)

**正式版**

| 补丁号 | 文件名 |
|---|---|
| `HD_U_J2` | `OptiFine_1.21.3_HD_U_J2.jar` |

**preview**

| 补丁号 | 文件名 |
|---|---|
| `HD_U_J2_pre1` | `preview_OptiFine_1.21.3_HD_U_J2_pre1.jar` |
| `HD_U_J2_pre2` | `preview_OptiFine_1.21.3_HD_U_J2_pre2.jar` |
| `HD_U_J2_pre3` | `preview_OptiFine_1.21.3_HD_U_J2_pre3.jar` |
| `HD_U_J2_pre4` | `preview_OptiFine_1.21.3_HD_U_J2_pre4.jar` |
| `HD_U_J2_pre5` | `preview_OptiFine_1.21.3_HD_U_J2_pre5.jar` |
| `HD_U_J2_pre6` | `preview_OptiFine_1.21.3_HD_U_J2_pre6.jar` |
| `HD_U_J2_pre7` | `preview_OptiFine_1.21.3_HD_U_J2_pre7.jar` |
| `HD_U_J2_pre8` | `preview_OptiFine_1.21.3_HD_U_J2_pre8.jar` |
| `HD_U_J2_pre9` | `preview_OptiFine_1.21.3_HD_U_J2_pre9.jar` |
| `HD_U_J2_pre10` | `preview_OptiFine_1.21.3_HD_U_J2_pre10.jar` |
| `HD_U_J2_pre11` | `preview_OptiFine_1.21.3_HD_U_J2_pre11.jar` |
| `HD_U_J2_pre12` | `preview_OptiFine_1.21.3_HD_U_J2_pre12.jar` |

## 1.21.4 的全部 OptiFine 构建(18 个)

**正式版**

| 补丁号 | 文件名 |
|---|---|
| `HD_U_J3` | `OptiFine_1.21.4_HD_U_J3.jar` |

**preview**

| 补丁号 | 文件名 |
|---|---|
| `HD_U_J3_pre1` | `preview_OptiFine_1.21.4_HD_U_J3_pre1.jar` |
| `HD_U_J3_pre2` | `preview_OptiFine_1.21.4_HD_U_J3_pre2.jar` |
| `HD_U_J3_pre3` | `preview_OptiFine_1.21.4_HD_U_J3_pre3.jar` |
| `HD_U_J3_pre4` | `preview_OptiFine_1.21.4_HD_U_J3_pre4.jar` |
| `HD_U_J3_pre5` | `preview_OptiFine_1.21.4_HD_U_J3_pre5.jar` |
| `HD_U_J3_pre6` | `preview_OptiFine_1.21.4_HD_U_J3_pre6.jar` |
| `HD_U_J3_pre7` | `preview_OptiFine_1.21.4_HD_U_J3_pre7.jar` |
| `HD_U_J3_pre8` | `preview_OptiFine_1.21.4_HD_U_J3_pre8.jar` |
| `HD_U_J3_pre9` | `preview_OptiFine_1.21.4_HD_U_J3_pre9.jar` |
| `HD_U_J3_pre10` | `preview_OptiFine_1.21.4_HD_U_J3_pre10.jar` |
| `HD_U_J3_pre11` | `preview_OptiFine_1.21.4_HD_U_J3_pre11.jar` |
| `HD_U_J3_pre12` | `preview_OptiFine_1.21.4_HD_U_J3_pre12.jar` |
| `HD_U_J3_pre13` | `preview_OptiFine_1.21.4_HD_U_J3_pre13.jar` |
| `HD_U_J3_pre14` | `preview_OptiFine_1.21.4_HD_U_J3_pre14.jar` |
| `HD_U_J3_pre15` | `preview_OptiFine_1.21.4_HD_U_J3_pre15.jar` |
| `HD_U_J4_pre1` | `preview_OptiFine_1.21.4_HD_U_J4_pre1.jar` |
| `HD_U_J4_pre2` | `preview_OptiFine_1.21.4_HD_U_J4_pre2.jar` |

`J4` 只有 preview,没有正式版,所以 1.21.4 的最新正式版仍是 `J3`。

## 1.21.6 的全部 OptiFine 构建(3 个)

**正式版**

无。

**preview**

| 补丁号 | 文件名 |
|---|---|
| `HD_U_J6_pre1` | `preview_OptiFine_1.21.6_HD_U_J6_pre1.jar` |
| `HD_U_J6_pre2` | `preview_OptiFine_1.21.6_HD_U_J6_pre2.jar` |
| `HD_U_J6_pre3` | `preview_OptiFine_1.21.6_HD_U_J6_pre3.jar` |

三个构建**都**带有光影缺陷,见 `README.md` 的已知限制。

## 1.21.7 的全部 OptiFine 构建(4 个)

**正式版**

无。

**preview**

| 补丁号 | 文件名 |
|---|---|
| `HD_U_J6_pre4` | `preview_OptiFine_1.21.7_HD_U_J6_pre4.jar` |
| `HD_U_J6_pre5` | `preview_OptiFine_1.21.7_HD_U_J6_pre5.jar` |
| `HD_U_J6_pre6` | `preview_OptiFine_1.21.7_HD_U_J6_pre6.jar` |
| `HD_U_J6_pre7` | `preview_OptiFine_1.21.7_HD_U_J6_pre7.jar` |

四个构建**都**带有光影缺陷;1.21.6 与 1.21.7 合计七个构建,行为一致,降级不能规避。

## 1.21.8 的全部 OptiFine 构建(10 个)

**正式版**

无。

**preview**

| 补丁号 | 文件名 |
|---|---|
| `HD_U_J6_pre7` | `preview_OptiFine_1.21.8_HD_U_J6_pre7.jar` |
| `HD_U_J6_pre8` | `preview_OptiFine_1.21.8_HD_U_J6_pre8.jar` |
| `HD_U_J6_pre9` | `preview_OptiFine_1.21.8_HD_U_J6_pre9.jar` |
| `HD_U_J6_pre10` | `preview_OptiFine_1.21.8_HD_U_J6_pre10.jar` |
| `HD_U_J6_pre11` | `preview_OptiFine_1.21.8_HD_U_J6_pre11.jar` |
| `HD_U_J6_pre12` | `preview_OptiFine_1.21.8_HD_U_J6_pre12.jar` |
| `HD_U_J6_pre13` | `preview_OptiFine_1.21.8_HD_U_J6_pre13.jar` |
| `HD_U_J6_pre14` | `preview_OptiFine_1.21.8_HD_U_J6_pre14.jar` |
| `HD_U_J6_pre15` | `preview_OptiFine_1.21.8_HD_U_J6_pre15.jar` |
| `HD_U_J6_pre16` | `preview_OptiFine_1.21.8_HD_U_J6_pre16.jar` |

`J6` 系列横跨三个版本:1.21.6 是 `pre1`–`pre3`,1.21.7 是 `pre4`–`pre7`,1.21.8 是 `pre7`–`pre16`。`J6_pre7` 在 1.21.7 与 1.21.8 上同时存在,只靠下载路径里的 MC 版本段区分。

## 1.21.9 的全部 OptiFine 构建(2 个)

**正式版**

无。

**preview**

| 补丁号 | 文件名 |
|---|---|
| `HD_U_J7_pre1` | `preview_OptiFine_1.21.9_HD_U_J7_pre1.jar` |
| `HD_U_J7_pre2` | `preview_OptiFine_1.21.9_HD_U_J7_pre2.jar` |

## 1.21.10 的全部 OptiFine 构建(10 个)

**正式版**

无。

**preview**

| 补丁号 | 文件名 |
|---|---|
| `HD_U_J7_pre2` | `preview_OptiFine_1.21.10_HD_U_J7_pre2.jar` |
| `HD_U_J7_pre3` | `preview_OptiFine_1.21.10_HD_U_J7_pre3.jar` |
| `HD_U_J7_pre4` | `preview_OptiFine_1.21.10_HD_U_J7_pre4.jar` |
| `HD_U_J7_pre5` | `preview_OptiFine_1.21.10_HD_U_J7_pre5.jar` |
| `HD_U_J7_pre6` | `preview_OptiFine_1.21.10_HD_U_J7_pre6.jar` |
| `HD_U_J7_pre7` | `preview_OptiFine_1.21.10_HD_U_J7_pre7.jar` |
| `HD_U_J7_pre8` | `preview_OptiFine_1.21.10_HD_U_J7_pre8.jar` |
| `HD_U_J7_pre9` | `preview_OptiFine_1.21.10_HD_U_J7_pre9.jar` |
| `HD_U_J7_pre10` | `preview_OptiFine_1.21.10_HD_U_J7_pre10.jar` |
| `HD_U_J7_pre11` | `preview_OptiFine_1.21.10_HD_U_J7_pre11.jar` |

1.21.9 与 1.21.10 共用 `J7` 系列号:1.21.9 拿到 `pre1`/`pre2`,1.21.10 从 `pre2` 续到 `pre11`。两版的 `J7_pre2` 同名,只靠 MC 版本区分(下载路径里的版本段不同)。

## 1.21.11 的全部 OptiFine 构建(18 个)

**正式版**

| 补丁号 | 文件名 |
|---|---|
| `HD_U_J8` | `OptiFine_1.21.11_HD_U_J8.jar` |
| `HD_U_J9` | `OptiFine_1.21.11_HD_U_J9.jar` |

**preview**

| 补丁号 | 文件名 |
|---|---|
| `HD_U_J8_pre1` | `preview_OptiFine_1.21.11_HD_U_J8_pre1.jar` |
| `HD_U_J8_pre2` | `preview_OptiFine_1.21.11_HD_U_J8_pre2.jar` |
| `HD_U_J8_pre3` | `preview_OptiFine_1.21.11_HD_U_J8_pre3.jar` |
| `HD_U_J8_pre4` | `preview_OptiFine_1.21.11_HD_U_J8_pre4.jar` |
| `HD_U_J8_pre5` | `preview_OptiFine_1.21.11_HD_U_J8_pre5.jar` |
| `HD_U_J8_pre6` | `preview_OptiFine_1.21.11_HD_U_J8_pre6.jar` |
| `HD_U_J8_pre7` | `preview_OptiFine_1.21.11_HD_U_J8_pre7.jar` |
| `HD_U_J8_pre8` | `preview_OptiFine_1.21.11_HD_U_J8_pre8.jar` |
| `HD_U_J8_pre9` | `preview_OptiFine_1.21.11_HD_U_J8_pre9.jar` |
| `HD_U_J8_pre10` | `preview_OptiFine_1.21.11_HD_U_J8_pre10.jar` |
| `HD_U_J8_pre11` | `preview_OptiFine_1.21.11_HD_U_J8_pre11.jar` |
| `HD_U_J8_pre12` | `preview_OptiFine_1.21.11_HD_U_J8_pre12.jar` |
| `HD_U_J9_pre1` | `preview_OptiFine_1.21.11_HD_U_J9_pre1.jar` |
| `HD_U_J9_pre2` | `preview_OptiFine_1.21.11_HD_U_J9_pre2.jar` |
| `HD_U_J9_pre3` | `preview_OptiFine_1.21.11_HD_U_J9_pre3.jar` |
| `HD_U_J9_pre4` | `preview_OptiFine_1.21.11_HD_U_J9_pre4.jar` |

1.21.11 是本线唯一有**两个正式版**的版本(`J8` 与 `J9`),上表的正式版一列取 `J9`。

## 下载(第三方镜像)

镜像地址是 `<MC 版本>/<type>/<patch>`,三个字段直接取自构建列表接口的 `type` 与 `patch`;**正式版与 preview 的路径都是同样四段**,区别在切分方式:正式版的 `type` 是 `HD_U`、`patch` 是字母号(如 `J9`),preview 的 `type` 是 `HD_U_<字母号>`、`patch` 是 `pre<n>`。请求会 302 跳到官方分发,实测返回 `200` 且 `Content-Type: application/java-archive`。

```powershell
# 1.21:最新 preview J1_pre9(这一版没有正式版)
curl.exe -L -o preview_OptiFine_1.21_HD_U_J1_pre9.jar `
  "https://bmclapi2.bangbang93.com/optifine/1.21/HD_U_J1/pre9"

# 1.21.1:正式版 J1
curl.exe -L -o OptiFine_1.21.1_HD_U_J1.jar `
  "https://bmclapi2.bangbang93.com/optifine/1.21.1/HD_U/J1"

# 1.21.3:正式版 J2
curl.exe -L -o OptiFine_1.21.3_HD_U_J2.jar `
  "https://bmclapi2.bangbang93.com/optifine/1.21.3/HD_U/J2"

# 1.21.4:正式版 J3
curl.exe -L -o OptiFine_1.21.4_HD_U_J3.jar `
  "https://bmclapi2.bangbang93.com/optifine/1.21.4/HD_U/J3"

# 1.21.6 / 1.21.7:只有 preview,且都带光影缺陷
curl.exe -L -o preview_OptiFine_1.21.6_HD_U_J6_pre3.jar `
  "https://bmclapi2.bangbang93.com/optifine/1.21.6/HD_U_J6/pre3"
curl.exe -L -o preview_OptiFine_1.21.7_HD_U_J6_pre7.jar `
  "https://bmclapi2.bangbang93.com/optifine/1.21.7/HD_U_J6/pre7"

# 1.21.8:最新 preview J6_pre16
curl.exe -L -o preview_OptiFine_1.21.8_HD_U_J6_pre16.jar `
  "https://bmclapi2.bangbang93.com/optifine/1.21.8/HD_U_J6/pre16"

# 1.21.9 / 1.21.10:只有 preview
curl.exe -L -o preview_OptiFine_1.21.9_HD_U_J7_pre2.jar `
  "https://bmclapi2.bangbang93.com/optifine/1.21.9/HD_U_J7/pre2"
curl.exe -L -o preview_OptiFine_1.21.10_HD_U_J7_pre11.jar `
  "https://bmclapi2.bangbang93.com/optifine/1.21.10/HD_U_J7/pre11"

# 1.21.11:正式版 J9(另有 J8)
curl.exe -L -o OptiFine_1.21.11_HD_U_J9.jar `
  "https://bmclapi2.bangbang93.com/optifine/1.21.11/HD_U/J9"
```

自查某个版本有没有构建(判据是返回的正文是否为空数组,不要只看状态码):

```powershell
curl.exe -s "https://bmclapi2.bangbang93.com/optifine/1.21"      # -> 8 条(全是 preview)
curl.exe -s "https://bmclapi2.bangbang93.com/optifine/1.21.2"    # -> []
curl.exe -s "https://bmclapi2.bangbang93.com/optifine/1.21.5"    # -> []
curl.exe -s "https://bmclapi2.bangbang93.com/optifine/1.21.11"   # -> 18 条(J8、J9 与各自的 preview)
```

## 为什么这条线是这十个版本

OptiFine 在 1.21 系列里没有发布 **1.21.2 与 1.21.5** 的构建,这两个版本的构建列表都是空数组 —— 没有 OptiFine 就没有可移植的对象。其余十个版本都有构建,因此都在矩阵里。1.21 的 `J1` 系列没有正式版,但 preview 也算构建。

## 待确认(骨架阶段的已知缺口)

- **运行期命名空间**:这条线预期已经是官方(Mojang)名,不再是 SRG,但**确切切换点没有核实** —— 它大约落在 1.20.5/1.21 前后,而本线的起点正好在附近。若 1.21 或 1.21.1 仍在 SRG 一侧,补丁管线就要按版本分支。
- **`neoforge.mods.toml` 的字段要求**:`loaderVersion` 的取值范围、`modLoader` 取值等需要对着每个 NeoForge 版本的文档核对,不能只按一个版本写死。
- **OptiFine 的 ModLauncher 服务是否还会被自动发现**:Forge 时代由 `ModDirTransformerDiscoverer` 从 `mods/` 里发现第三方的 `ITransformationService`。NeoForge 21.x 是否保留这条发现路径未实测;若不支持,只能改走 NeoForge 自己的转换 API(见 `docs/PLAN.md` 的路线 A/B)。
- **beta 版 NeoForge 的影响面**:1.21.6 / 1.21.7 / 1.21.9 只有 `-beta` 构建(分别最新 `21.6.20-beta` / `21.7.25-beta` / `21.9.16-beta`),这些版本上的行为是否稳定、后续会不会有非 beta 版本,都不确定。
- **1.21.6 / 1.21.7 的光影缺陷归属**:缺陷本身来自 OptiFine 的预览构建(缺少前置的 `setParentTexture` 关联),但"什么时候能修好"取决于 OptiFine 是否还会出新构建;本项目这一侧能做什么(例如把关联补回去)尚未评估。
- **同号系列的边界**:1.21.9 与 1.21.10 共用 `J7` 系列号,1.21.6 – 1.21.8 共用 `J6` 系列号。同号系列里的构建能否互相替代、OptiFine 是否有版本自校验,未验证。
- **"正式版"不等于"可用"**:构建列表只说明构建存在,不代表能在 NeoForge 上跑通;本线尚无任何实测记录。
- **`-beta` NeoForge 的坐标可用性**:本表只核对了 `maven.neoforged.net` 上的版本号存在,没有核对对应版本的 ModLauncher 版本、是否要求额外仓库(GitHub Packages)等构建细节。

## 数据来源

- OptiFine 构建列表:`https://bmclapi2.bangbang93.com/optifine/<MC 版本>`(`type`、`patch`、`filename` 三个字段);下载路径按 `<MC 版本>/<type>/<patch>` 拼出,已用 `curl.exe -I` 逐个核对到 302 目标文件名。
- NeoForge 版本:`https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml`。本线用到的十个版本都在该文件里;`21.6`、`21.7`、`21.9` 三段的全部条目都以 `-beta` 结尾,其余各段取最新的非 beta 条目。元数据 `<lastUpdated>` 为 `20260913090722`。
- 核对时间:2026-09-14。
