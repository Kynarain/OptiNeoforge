# OptifiNeoforge

在 **NeoForge** 上加载 **OptiFine** 的客户端模组,做法与 OptiFabric 在 Fabric Loader 上的做法相同:把 OptiFine 的 jar 和本模组一起放进 `mods/`,启动时由本模组运行 OptiFine 自带的补丁流程,并把打过补丁的 Minecraft 类接进 NeoForge 的类转换流程。

本分支是 **26.x 线**,对应 **Minecraft 26.1.2**。1.20.x 线(1.20.1 – 1.20.6)与 1.21.x 线(1.21 – 1.21.11)在各自的分支上独立开发,三条线的 jar 不能互相替代。

> **状态:26.1.2 实机验证,`1.0.0` 已发布。** 判据是**一次真实启动**(两次独立复跑数字一致):`VERDICT: STARTED (40s, Sound engine started)`、`Setting user`、`[OptiFine]` 3478 行、`OptiFine: processClass` 795 次、连接材质 40 行、光影配置 101 行、**没有崩溃报告**,stderr 107 字节且与"不带任何 mod"的对照跑逐字节相同(那是 FML 终端日志组件自己的一行告警,本模组写 0 字节)。
>
> **这一条线的验证方式与另外两条线不同,如实说明**:26.1.2 上 NeoForge 已经没有 ModLauncher,而 OptiFine 的 `K1_pre2` **自带** `ClassProcessor`,所以挂载点是 OptiFine 自己的 —— 那次启动里**没有装本项目的 loader**。本项目贡献的是离线流水线(打补丁、改父类、成员回填、生成 shim),它装配出 OptiFine 的 processor 所安装的载荷。因此 Release 附的 jar 是这套工具与 mod 骨架,**不是**那次启动里真正跑起来的组件。

## 支持的版本

| Minecraft | NeoForge | 产物 | OptiFine 构建 | Java | 状态(实测) |
|---|---|---|---|---|---|
| 26.1.2 | `26.1.2.109` | `OptifiNeoforge-1.0.0+mc26.1.2.jar` | `preview_OptiFine_26.1.2_HD_U_K1_pre2.jar` | 25 | 已验证 · 已发布(`processClass` **795 次**、stderr 107 字节 = 对照跑、无崩溃报告;挂载点是 OptiFine 自带的。**2026-09-20 六个光影包实测全部加载并编译通过**,见 `docs/DEVELOPMENT.md`;旧记录里的 3478 行就是开光影包的那一次,352 行是不开的那一次) |

- mod id `optifineoforge`,仅客户端。
- 26.1 起游戏**未混淆**,官方名即运行名,因此这一线没有映射表要处理。
- 26.1.2 的 OptiFine 只有 preview 构建;完整构建列表见 `docs/VERSIONS.md`。
- 26.1.2 之外的版本(26.1、26.1.1、26.2+)目前没有 OptiFine 构建,没有可移植的对象。

## 安装

这一线目前的成品由 rig 的离线流水线装配(它需要**你自己下载的** OptiFine `K1_pre2` 与这一版的原版 jar),
挂载点是 OptiFine 自带的 `ClassProcessor`。Release 附的 jar 是那套工具与骨架,单独放进 `mods/` 不会启动
OptiFine;把这条线做成"装进 `mods/` 就能用"的加载器仍是待办。

## 构建

需要 **JDK 25**(26.1.2 自身的要求)。仓库根目录就是 Gradle 项目:

```powershell
.\gradlew build
```

产物为 `build/libs/OptifiNeoforge-<版本>+mc26.1.2.jar`。

## 工作原理(按实测修正)

OptiFine 的 Forge 侧入口是一个 ModLauncher 服务(`META-INF/services/cpw.mods.modlauncher.api.ITransformationService`
指向 `optifine.OptiFineTransformationService`),但 **FML 11 没有 ModLauncher**,这条线复用不了它 —— 实测到的
事实是:OptiFine 的 `K1_pre2` 自带一个 `OptiFineClassProcessor`,它按新的一代 `ClassProcessor` 接口把类装进
游戏层。所以这一线走的是**离线路线**:先在对的原版 jar 上应用 OptiFine 的 xdelta 补丁树,再处理三件事 ——

1. **元数据**:OptiFine 的 jar 里是 Forge 时代的 `META-INF/mods.toml`,要换成 NeoForge 自己的那一份,
   并把 FML 拒绝的安装器条目与 `notch/` 去掉;
2. **继承关系与成员**:OptiFine 的补丁产物里,有些类的父类在 NeoForge 上已被删除或替换
   (`BlockEntity` 就是其一),另一些类被 NeoForge 加过成员 —— 这两件事分别由改父类与按供体回填处理;
3. **Forge API shim**:载荷里点名的 Forge API 类型与它期望继承的成员,由工具生成。

工具跑在**构建期**(不在游戏里),产物是 OptiFine 的 processor 要装的载荷。设计与里程碑见 `docs/PLAN.md`,
两个前置调研见 `docs/RESEARCH-neoforge.md` 与 `docs/RESEARCH-optifine.md`。

## 许可与致谢

- 本项目遵循 **MPL-2.0**(`LICENSE`)。**实现为本项目自写**;与 [OptiFabric](https://github.com/Chocohead/OptiFabric)(作者 Modmuss50、Chocohead)相同的是**做法本身** —— 把 OptiFine 在运行时补进游戏 —— 这一点在此致谢,但两者是不同的加载器(Fabric Loader 对应 NeoForge)、不同的实现,本项目**不是** OptiFabric 的移植或重传。2026-09-24 逐文件核对:源码中没有来自 OptiFabric 的署名或移植片段(源码里的 "Ported from …" 均指本项目自己分支之间的移植),故按事实表述。
- **不包含、也不分发 OptiFine 本体**,OptiFine 版权归 sp614x 所有,请自行获取。
