# 发布流程(三条线共用)

`docs/VERSIONING.md` 说"发布流程见 `docs/PUBLISHING.md`",而这个文件此前**不存在**,三个检出与 rig 里也**没有任何
publish 实现**。本文就是那一步,配上两个可重复执行的脚本,让发布从"一下午手点 API"变成一条命令。

## 发布到哪

三个检出是**同一个 GitHub 仓库**(`Kynarain/OptifiNeoforge`)的三个分支,产物各按自己的 MC 版本发一个 Release:

| 检出 / 分支 | 负责的线 |
|---|---|
| `OptifiNeoforge` / `1.21.x` | 1.21、1.21.1、1.21.3、1.21.4、1.21.6、1.21.7、1.21.8、1.21.9、1.21.10、1.21.11 |
| `OptifiNeoforge-120x` / `1.20.x` | 1.20.1、1.20.2、1.20.4、1.20.6 |
| `OptifiNeoforge-26x` / `26.x` | 26.1.2 |

tag 形如 `v1.0.0+mc<MC 版本>`,**指向构建该 jar 的那次提交**(不是默认分支的头,也不是"最新"这个模糊概念)。

## 两条命令

```powershell
# 1. 用每条线自己的仓库/分支/配对重建 jar,收集到 release-stage\
powershell -NoProfile -ExecutionPolicy Bypass -File build-release-jars.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File build-release-jars.ps1 -Only 1.21.9,1.21.10

# 2. 建 tag / 建或更新 Release / 替换 asset(先 -WhatIfOnly 看一眼)
powershell -NoProfile -ExecutionPolicy Bypass -File publish-github-releases.ps1 -WhatIfOnly
powershell -NoProfile -ExecutionPolicy Bypass -File publish-github-releases.ps1
```

产物**必须**来自仓库自己的 Gradle(`gradlew jar -Pmc=… -Pneoforge=… <mount 开关>`),**绝不**上传 rig 里
`jars-<mc>\optifine-*.jar` 之类:那些带着 OptiFine 打过补丁的游戏类,`build-fml10-payload.ps1` 里对 FML 10 载荷
也写了同样的"不分发"。

## 每条线的配对不是猜的

`build-release-jars.ps1` 里的表就是 rig 启动测试用的那套配对(`retest-all.ps1` 的行表),所以"发布用的 jar"和
"验收过的 jar"不可能对不上 NeoForge 版本。两处**必须**显式写、容易错的地方:

* **1.20.1 的 NeoForge 是 `47.1.106`**,坐标是 Forge 时代的 `net.neoforged:forge:1.20.1-47.1.106` ——
  `net.neoforged:neoforge:1.20.1-47.1.106` **不存在**,写错会在解析依赖时就失败;
* **1.20.6** 同时要 `-Pmodlauncher=11 -Ptarget_java_version=21`(它这条线上第一个用 Java 21 编的产物);
* 1.20.1~1.20.4 用 `-Pmodlauncher=10`,1.21~1.21.8 用 `-Pmountpoint=modlauncher`,1.21.9 及以后用
  `-Pmountpoint=fml10`。

## 三条必须遵守的规则(每条都有实测代价)

1. **asset 名里的 `+` 必须转义**。上传走的是查询串,未转义的 `+` 在那里表示**空格**,GitHub 再把空格转成 `.`。
   第一次执行时 `OptifiNeoforge-1.0.0+mc1.20.1.jar` 就这样被发成了 `OptifiNeoforge-1.0.0.mc1.20.1.jar`;
   脚本现在用 `[uri]::EscapeDataString` 处理,并且**删除该 Release 上所有** `OptifiNeoforge-1.0.0*` 资产再上传,
   免得两种命名同时挂着、下载者分不清哪个是当前版本。
2. **先删旧 jar 再构建**。`build-release-jars.ps1` 每次都删掉 `build\libs\<目标名>` 再跑 Gradle:
   "文件还在"不等于"这次构建出来的",这正是线上曾经挂着 09-17 旧构建(159~161 KB,而当时已是 180~218 KB)的成因。
3. **检出必须干净**。脚本会打印分支、头提交与未提交改动数,脏树会明确告警 —— 从脏树构建出来的 jar 不是分支头。

## 说明里必须写验证状态

每个 Release 的说明由脚本从状态表生成,逐条列出**实测到什么**:

| 门 | 含义 |
|---|---|
| 四项启动验收 + stderr 对比 | 真机启动(VERDICT / Setting user / Sound engine started / 无崩溃报告),且 stderr 等于该线记录值 |
| 存档 + 光影 | 在真游戏里建/开存档并在其中加载光影包 |
| FXAA 像素判定 | `fxaa-check.ps1` 对同一场景的一对帧给出的判定 |

**没有证到的就写"NOT PROVEN"并给出原因**(例如"两次运行取景不同,故不存在判定"),而不是留白或含糊过去。

## 关于 prerelease

`docs/VERSIONING.md` 规定:预发布版本**不**发 CurseForge / Modrinth,只挂 GitHub Release。本项目当前的做法是
**全部按正式 Release 发布**,把未验证项写进说明 —— 这是 2026-09-23 明确选择的发布口径,与 VERSIONING.md 里
"验证用的构建适合先走预发布"的用法不同,**这一点是知情的偏离,记在此处以免日后被误读为疏漏**。

## 一次真实发布的记录(2026-09-23)

15 条线全部重建并发布:`1.20.1/1.20.2/1.21.9/1.21.10/1.21.11` **新建了 tag 与 Release**(此前连 tag 都没有),
其余 10 条**替换了旧资产**(旧的是 09-17 构建,159~161 KB)。处理 `+` 转义后,15 个 Release 各 1 个资产、
命名与大小均核对一致。

## 2026-09-27 本轮验收的测量条件:并发(客户端的拥有者正在游玩)

本机在跑一条不属于 rig 的客户端(PCL 启动的 Fabric 1.20.6,`gameDir` 指向客户端的拥有者自己的
`.minecraft`)。rig 的"一次只跑一个客户端"保护因此把验收行判成 **BLOCKED**——那不是关于本 mod 的结果。
经客户端拥有者明确同意,本轮验收按 `-AllowConcurrent` 执行,理由与边界写清楚:

- **可以并发**:四检读的是本次运行自己的启动器日志(`VERDICT: STARTED`、`Setting user`、
  `Sound engine started`、崩溃报告数)与本次运行的 stderr 字节数。机器上另一个客户端不改变这些值。
- **不可并发**:FXAA 的像素取证需要窗口与前台,必须独占。凡在并发期间做的四检,都在结果里标注;
  FXAA 的成对帧只在独占条件下采集,绝不与另一个客户端同时进行。
- 并发会带来负载,因此每一行的启动预算从默认 120 s 放宽到 240 s;放宽的是**等待上限**,
  不是判定标准(四检的判据没有变松)。

> 纪律:测量条件本身就是结果的一部分。“并发下通过的”与“独占下通过的”必须能一眼区分,
> 否则这份验收记录会在下一次引用时变成误导。
