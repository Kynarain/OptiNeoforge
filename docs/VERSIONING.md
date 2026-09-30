# 版本号规则(1.21.x 线)

本项目每个 jar 的版本号遵循 **[语义化版本 2.0.0](https://semver.org/lang/zh-CN/)**,并用 `+` 追加该产物对应的 Minecraft 版本作为**编译信息**(SemVer §10):

```
<MAJOR>.<MINOR>.<PATCH>+mc<Minecraft 版本>
例如  OptifiNeoforge-1.0.0+mc1.21.11.jar
```

## 现在处于 0.x

加载器还没有跑通,所以这条线的十个产物都停在 `0.1.0`,从 `+mc1.21` 到 `+mc1.21.11` 各一份:

```
OptifiNeoforge-0.1.0+mc1.21.jar
OptifiNeoforge-0.1.0+mc1.21.1.jar
OptifiNeoforge-0.1.0+mc1.21.3.jar
OptifiNeoforge-0.1.0+mc1.21.4.jar
OptifiNeoforge-0.1.0+mc1.21.6.jar
OptifiNeoforge-0.1.0+mc1.21.7.jar
OptifiNeoforge-0.1.0+mc1.21.8.jar
OptifiNeoforge-0.1.0+mc1.21.9.jar
OptifiNeoforge-0.1.0+mc1.21.10.jar
OptifiNeoforge-0.1.0+mc1.21.11.jar
```

0.x 表示仍在开发中,任何东西都可能变。**第一个在真实游戏里跑起来的产物才升到 `1.0.0`**,从那时起下面的规则才生效。同一个逻辑改动要在十个版本上都发布时,十个产物各自按自己的验证进度跟上,不必同时进入 `1.x`。

## 三条线各自版本号

`1.20.x`、`1.21.x`、`26.x` 三条分支的版本号**互不相干**。同一个逻辑改动落在三条线上时,各自按自己的节奏升版;某一版的行为只改了一条线,就只升那一条线的产物。

同一条线**内部**的不同 MC 版本也可以停在不同的版本号:比如只有 1.21.1 那版跑通了、1.21.6 那版还卡在 OptiFine 的光影缺陷上,就只升 1.21.1 的产物。`+mc...` 把版本号与 MC 版本绑成对,所以不会出现"同一个版本号对应两个产物"的情况。

## 什么算哪一档

| 改动 | 档位 | 例子 |
|---|---|---|
| 需要用户改 mods 目录、改 mod id、改元数据,或不再支持某个 MC 版本 | **MAJOR** | 改 mod id;要求 OptiFine 换一个放置位置;放弃 1.21.9(该版本 NeoForge 只有 beta) |
| 新增支持的 MC 版本;新增能在游戏里用到的功能 | **MINOR** | 1.21.x 线内新增一个 MC 版本;新增一个让光影包正常加载的修正 |
| 只修正行为、不改变使用方式 | **PATCH** | 修某个类的补丁冲突;修缓存失效判断 |

## 预发布

给特定构建做验证时用 SemVer 的预发布后缀,例如 `1.1.0-rc.1+mc1.21.8`。预发布版本**不发布**到 CurseForge / Modrinth,只在 GitHub Release 上挂出来。这条线的 1.21、1.21.6、1.21.7、1.21.8、1.21.9、1.21.10 六版只有 OptiFine 的 preview 构建可用,它们的验证版本适合先走预发布。

## 修改方式

版本号只通过 `release/version.ps1` 修改(随骨架后续提交加入),不要手改 `gradle.properties`。发布流程见 `docs/PUBLISHING.md`。
