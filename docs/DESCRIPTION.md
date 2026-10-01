# 发布用文案(26.x 线)

直接复制粘贴用的成品。**简要描述**用于 CurseForge 项目页的"简介"栏(以及 GitHub 仓库的 About);**详细描述**用于 CurseForge 项目正文。

> ⚠️ CurseForge 审核规则:描述与简介**可以有其它语言,但英文必须排在其前面**。所以本文件英文在前、中文在后;往 CF 粘贴时,每个字段里都先贴英文、再贴中文。
>
> 本文件覆盖 **26.x** 这一条线(目前只有 26.1.2)。1.20.x 线(1.20.1 – 1.20.6)与 1.21.x 线(1.21 – 1.21.11)在各自分支上,各自的 `docs/DESCRIPTION.md` 是各自的成品;三条线的 jar **不能互相替代**。
> 版本号与产物名以 `gradle.properties` 的 `mod_version_base` 为准(当前 `2.0.0`,产物名 `OptifiNeoforge-2.0.0+mc<版本>.jar`);`release\version.ps1 show` 可随时核对。

---

## 一、简要描述(English)

> Load OptiFine on NeoForge. Put this mod and your own OptiFine jar into `mods/`; it patches OptiFine into the game at startup. **No OptiFine content is included** — the jar contains no OptiFine classes and no file taken from OptiFine.

**One-liner:**

> OptiFine on NeoForge, one jar per Minecraft version.

---

## 二、详细描述(English)

### OptifiNeoforge — OptiFine on NeoForge (26.x)

A client-side mod that brings **OptiFine** to **NeoForge**. Put OptifiNeoforge and your OptiFine jar into `mods/` and it takes care of the rest: OptiFine's own patching is applied at startup and the patched game classes are fed into NeoForge's transformation pipeline.

On this line Minecraft renamed its client pipeline (FML 10), so the loader mounts OptiFine's patched classes through `-Pmountpoint=fml10` instead of the ModLauncher-shaped mount used by the older lines. That is a loader-side difference, not a user-visible one: installation is the same.

#### What this project is not

* **Independent implementation.** The code is written for this project and released under **MPL-2.0**. It is **not** a re-upload, port or rebrand of OptiFabric: different mod loader, different implementation, different Minecraft versions.
* **No OptiFine content in the released jar.** Unzip the jar and check: there are **zero `net/optifine` entries** and **no file copied from OptiFine**.
* **OptiFine is not bundled or redistributed.** It is copyright **sp614x** and has to be obtained by the user from the official site.
* **Not affiliated with or endorsed by** OptiFine or NeoForge.

#### Supported versions (26.x line)

One jar per Minecraft version — they are **not interchangeable**. The version is in the file name:
`OptifiNeoforge-2.0.0+mc26.1.2.jar`.

| Minecraft | NeoForge | Java | OptiFine build tested with this line |
|---|---|---|---|
| 26.1.2 | `26.1.2.109` | 25 | HD U **K1 pre2** (preview) |

That OptiFine entry is not taken from a file name: it is the build string found inside the loader's prepared OptiFine classpath jar for this line (`work\26.1.2\optifine-classpath.jar`), read on 2026-10-01. Use the OptiFine jar for your Minecraft version; the preview K1 build is what this line was measured against.

#### Installing

1. Install **NeoForge `26.1.2.109`**.
2. Put **`OptifiNeoforge-2.0.0+mc26.1.2.jar`** into `mods/`.
3. Put your own OptiFine jar for 26.1.2 into `mods/` as well.
4. Launch with **Java 25**.

There is no installer and nothing is written into your game files.

#### Known limitations

* **Multiplayer has not been tested.** The attempt to exercise it never landed: the chat command was never actually delivered into the client, so the keystroke/chat path is itself unproven. This says "not measured", not "does not work"; no registration action was taken on any server.
* **FXAA is not proven on this line** (no same-scene frame pair was measured). The project owner de-prioritised FXAA on 2026-10-01, so this is open, not a failure.
* Shaders and shader packs come from your OptiFine jar; this project only carries the loader.

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

### OptifiNeoforge —— 在 NeoForge 上加载 OptiFine(26.x 线)

这是一个把 **OptiFine** 带到 **NeoForge** 的客户端模组。把 OptifiNeoforge 与你自备的 OptiFine jar 一起放进 `mods/`,启动时由本模组执行 OptiFine 自带的补丁流程,并把打过补丁的游戏类接进 NeoForge 的类转换管线。

这一代 Minecraft 换了客户端管线(FML 10),所以加载器用 `-Pmountpoint=fml10` 挂载 OptiFine 的补丁类,而不是旧线那种 ModLauncher 形状的挂载点。这是加载器内部差异,**对安装方式没有影响**。

#### 本项目不是什么(这些都可以自行核对)

* **独立实现**:代码为本项目自写,以 **MPL-2.0** 发布;不是 OptiFabric 的重传、移植或换皮;
* **发布产物不含 OptiFine 内容**:开包即可核对 —— `net/optifine` 条目为 0,也没有任何取自 OptiFine 的文件;
* **不再分发 OptiFine**:版权归 **sp614x**,须由用户自行从官网获取;
* **未获 OptiFine 或 NeoForge 认可或支持。**

#### 支持的版本(26.x 线)

每个 Minecraft 版本一个 jar,**互不通用**,版本写在文件名里(如 `OptifiNeoforge-2.0.0+mc26.1.2.jar`)。对照表见英文节(NeoForge 与 Java)。**本项目实测所使用的 OptiFine 构建是 `HD U K1 pre2`(预览版)** —— 这不是从文件名抄的, 而是 2026-10-01 从加载器为该线准备的 OptiFine 类路径 jar(`work\26.1.2\optifine-classpath.jar`)内部的类字符串里读出来的。请使用与你 Minecraft 版本对应的 OptiFine jar;本线实测所对的是 K1 的这个预览构建。

#### 安装

1. 装好 **NeoForge `26.1.2.109`**;
2. 把 **`OptifiNeoforge-2.0.0+mc26.1.2.jar`** 放进 `mods/`;
3. 把你自备的 **26.1.2 对应 OptiFine jar** 也放进 `mods/`;
4. 用 **Java 25** 启动。

没有安装器,也不会写入你的游戏文件。

#### 已知限制

* **多人游戏未测**:当时那次尝试**没有落地** —— 聊天命令根本没被送进客户端, 所以"键盘/聊天输入"这条链路本身也未被证明。这里说的是**没测过**, 不是"不能用"; 且**没有在任何服务器上执行注册动作**。
* **本线的 FXAA 未证明**(没有成对同场景帧测量)。项目所有者 2026-10-01 指示暂不重视 FXAA, 因此这是**未决**而不是失败。
* 光影与光影包由你自备的 OptiFine jar 提供;本项目只承载加载器。

#### 链接

* **下载:** https://github.com/Kynarain/OptifiNeoforge/releases
* **问题反馈 / 源码:** https://github.com/Kynarain/OptifiNeoforge
* **OptiFine(必需,但不随本模组分发):** https://optifine.net/downloads
