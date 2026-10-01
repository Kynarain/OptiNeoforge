# 发布用文案(1.20.x 线)

直接复制粘贴用的成品。**简要描述**用于 CurseForge 项目页的"简介"栏(以及 GitHub 仓库的 About);**详细描述**用于 CurseForge 项目正文。

> ⚠️ CurseForge 审核规则:描述与简介**可以有其它语言,但英文必须排在其前面**。所以本文件英文在前、中文在后;往 CF 粘贴时,每个字段里都先贴英文、再贴中文。
>
> 本文件覆盖 **1.20.x** 这一条线(1.20.1 – 1.20.6,Java 17,1.20.6 起 Java 21)。1.21.x 线(1.21 – 1.21.11)与 26.x 线(26.1.2)在各自分支上,各自的 `docs/DESCRIPTION.md` 是各自的成品;三条线的 jar **不能互相替代**。
> 版本号与产物名以 `gradle.properties` 的 `mod_version_base` 为准(当前 `2.0.0`,产物名 `OptifiNeoforge-2.0.0+mc<版本>.jar`);`release\version.ps1 show` 可随时核对。

---

## 一、简要描述(English)

> Load OptiFine on NeoForge. Put this mod and your own OptiFine jar into `mods/`; it patches OptiFine into the game at startup. **No OptiFine content is included** — the jar contains no OptiFine classes and no file taken from OptiFine.

**One-liner:**

> OptiFine on NeoForge, one jar per Minecraft version.

---

## 二、详细描述(English)

### OptifiNeoforge — OptiFine on NeoForge (1.20.x)

A client-side mod that brings **OptiFine** to **NeoForge**. Put OptifiNeoforge and **your own OptiFine jar** into `mods/` and it takes care of the rest: OptiFine's own patching is applied at startup and the patched game classes are fed into NeoForge's transformation pipeline — the same idea as OptiFabric on Fabric Loader.

#### What this project is not

* **Independent implementation.** The code is written for this project and released under **MPL-2.0**. It is **not** a re-upload, port or rebrand of OptiFabric: different mod loader, different implementation, different Minecraft versions.
* **No OptiFine content.** Unzip the jar and check: there are **zero `net/optifine` entries** and **no file copied from OptiFine**.
* **OptiFine is not bundled or redistributed.** It is copyright **sp614x** and has to be obtained by the user from the official site.
* **Not affiliated with or endorsed by** OptiFine or NeoForge.

#### Supported versions (1.20.x line)

One jar per Minecraft version — they are **not interchangeable**. The version is in the file name:
`OptifiNeoforge-2.0.0+mc1.20.4.jar`.

| Minecraft | NeoForge | Java | OptiFine build tested with this line |
|---|---|---|---|
| 1.20.1 | `47.1.106` (Forge-era coordinate `net.neoforged:forge:1.20.1-47.1.106`) | 17 | HD U I6 |
| 1.20.2 | `20.2.88` | 17 | HD U I7 **pre1** (preview) |
| 1.20.4 | `20.4.251` | 17 | HD U I7 |
| 1.20.6 | `20.6.141` | 21 | HD U J1 **pre18** (preview) |

The OptiFine column records the build each line was tested against; another build of the same OptiFine major version normally works, but it is not what was measured.

#### Installing

1. Install the **NeoForge** version matching your Minecraft version.
2. Put the matching **`OptifiNeoforge-2.0.0+mc<your version>.jar`** into `mods/`.
3. Put the matching **OptiFine jar** (downloaded by you) into `mods/` as well.
4. Launch with the Java version listed above.

There is no installer and nothing is written into your game files.

#### Known limitations

* **Multiplayer has not been tested.** The attempt to exercise it never landed: the chat command was never actually delivered into the client, so the keystroke/chat path is itself unproven. This says "not measured", not "does not work"; no registration action was taken on any server.
* **FXAA is not proven on this line.** 1.20.2, 1.20.4 and 1.20.6 were attempted; where a same-scene pair was measured the verdict is recorded, and 1.20.6 came out below the 2.0% edge-energy threshold (recorded as no visible effect, not as a pass). 1.20.1 has no pair. The project owner de-prioritised FXAA on 2026-10-01, so these are open, not failures.
* Shaders and shader packs are provided by your OptiFine jar; this project only carries the loader.

#### Links

* **Downloads:** https://github.com/Kynarain/OptifiNeoforge/releases
* **Issues / source:** https://github.com/Kynarain/OptifiNeoforge
* **OptiFine (required, not included):** https://optifine.net/downloads

---

## 三、简要描述(中文)

> 在 NeoForge 上加载 OptiFine:把本模组和你**自备的** OptiFine jar 一起放进 `mods/` 即可,**不包含任何 OptiFine 内容** —— 产物内没有 OptiFine 的类,也没有任何取自 OptiFine 的文件。

**一句版:**

> 在 NeoForge 上加载 OptiFine,每个 Minecraft 版本一个 jar。

---

## 四、详细描述(中文)

### OptifiNeoforge —— 在 NeoForge 上加载 OptiFine(1.20.x 线)

这是一个把 **OptiFine** 带到 **NeoForge** 的客户端模组。把 OptifiNeoforge 与你**自备的 OptiFine jar** 一起放进 `mods/`,启动时由本模组执行 OptiFine 自带的补丁流程,并把打过补丁的游戏类接进 NeoForge 的类转换管线。

#### 本项目不是什么(这些都可以自行核对)

* **独立实现**:代码为本项目自写,以 **MPL-2.0** 发布;不是 OptiFabric 的重传、移植或换皮 —— 加载器不同、实现不同、支持的 MC 版本也不同;
* **不含 OptiFine 内容**:开包即可核对 —— 产物内 `net/optifine` 条目为 0,也没有任何取自 OptiFine 的文件;
* **不再分发 OptiFine**:版权归 **sp614x**,须由用户自行从官网获取;
* **未获 OptiFine 或 NeoForge 认可或支持。**

#### 支持的版本(1.20.x 线)

每个 Minecraft 版本一个 jar,**互不通用**,版本写在文件名里(如 `OptifiNeoforge-2.0.0+mc1.20.4.jar`)。对照表见英文节的表格(NeoForge 版本、Java 版本、实测使用的 OptiFine 构建)。1.21.x 线与 26.x 线在各自分支上,各有自己的 jar。

#### 安装

1. 装好与你 Minecraft 版本对应的 **NeoForge**;
2. 把对应版本的 `OptifiNeoforge-2.0.0+mc<你的版本>.jar` 放进 `mods/`;
3. 把你**自己下载的**对应版本 OptiFine jar 也放进 `mods/`;
4. 用上表所列的 Java 版本启动。

没有安装器,也不会写入你的游戏文件。

#### 已知限制

* **多人游戏未测**:当时那次尝试**没有落地** —— 聊天命令根本没被送进客户端, 所以"键盘/聊天输入"这条链路本身也未被证明。这里说的是**没测过**, 不是"不能用"; 且**没有在任何服务器上执行注册动作**。
* **本线的 FXAA 未证明**:1.20.2 / 1.20.4 / 1.20.6 做过尝试, 凡有成对测量的都记下了判定, 其中 1.20.6 低于 2.0% 边缘能量阈值(记为"无可测效果", 不算通过);1.20.1 没有成对测量。项目所有者 2026-10-01 指示暂不重视 FXAA, 因此这些是**未决**而不是失败。
* 光影与光影包由你自备的 OptiFine jar 提供;本项目只承载加载器。

#### 链接

* **下载:** https://github.com/Kynarain/OptifiNeoforge/releases
* **问题反馈 / 源码:** https://github.com/Kynarain/OptifiNeoforge
* **OptiFine(必需,但不随本模组分发):** https://optifine.net/downloads
