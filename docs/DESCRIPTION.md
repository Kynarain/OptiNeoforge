# 发布用文案(简要描述 / 详细描述)

直接复制粘贴用的成品。**简要描述**用于 CurseForge 项目页的"简介"栏(以及 GitHub 仓库的 About);**详细描述**用于 CurseForge 项目正文。

> ⚠️ CurseForge 审核规则:描述与简介**可以有其它语言,但英文必须排在其前面**。所以本文件把英文放在前两节、中文放后两节;往 CF 粘贴时,每个字段里都先贴英文、再贴中文(只想用英文就只贴英文那节)。
> 简介建议不超过一句,用每个语言里的"一句版"最稳。
>
> 本文件覆盖 **1.21.x** 这一条线(1.21 – 1.21.11)。1.20.x 线(1.20.1 – 1.20.6)与 26.x 线(26.1.2)在各自分支上,各自的 `docs/DESCRIPTION.md` 是各自的成品;三条线的 jar 不能互相替代。

---

## 一、简要描述(English)

> Load OptiFine on NeoForge. Put this mod and your own OptiFine jar into `mods/`; it patches OptiFine into the game at startup. **No OptiFine content is included** — the jar contains no OptiFine classes and no file taken from OptiFine.

**One-liner:**

> OptiFine on NeoForge, one jar per Minecraft version.

---

## 二、详细描述(English)

### OptifiNeoforge — OptiFine on NeoForge

A client-side mod that brings **OptiFine** to **NeoForge**. Put OptifiNeoforge and **your own OptiFine jar** into `mods/` and it takes care of the rest: OptiFine's own patching is applied at startup and the patched game classes are fed into NeoForge's transformation pipeline — the same idea as OptiFabric on Fabric Loader.

#### What this project is not

Written plain, because these are the things a reviewer or a user needs to be able to check rather than take on trust:

* **Independent implementation.** The code is written for this project and released under **MPL-2.0**. It is **not** a re-upload, port or rebrand of OptiFabric: it targets a different mod loader (NeoForge, not Fabric Loader), is a different implementation, and supports different Minecraft versions. OptiFabric's *approach* — patching OptiFine into the game at runtime — is the same, and is credited in the README; no code is taken from it.
* **No OptiFine content.** Unzip the jar and check: there are **zero `net/optifine` entries** and **no file copied from OptiFine**. The anti-aliasing post-effect definitions shipped here are authored by this project; the shader stages they name come from the OptiFine jar *you* supply.
* **OptiFine is not bundled or redistributed.** It is copyright **sp614x** and has to be obtained by the user from the official site. This project contains nothing of it.
* **Not affiliated with or endorsed by** OptiFine or NeoForge.

#### Supported versions

One jar per Minecraft version — they are **not interchangeable**. The version is in the file name: `OptifiNeoforge-2.0.0+mc1.21.8.jar`.

| Minecraft | NeoForge | Java | OptiFine build you need |
|---|---|---|---|
| 1.21 | `21.0.167` | 21 | HD U J1 **pre9** (preview only) |
| 1.21.1 | `21.1.250` | 21 | HD U J1 |
| 1.21.3 | `21.3.97` | 21 | HD U J2 |
| 1.21.4 | `21.4.149` | 21 | HD U J3 |
| 1.21.6 | `21.6.20-beta` | 21 | HD U J6 **pre3** (preview only) |
| 1.21.7 | `21.7.25-beta` | 21 | HD U J6 **pre7** (preview only) |
| 1.21.8 | `21.8.54` | 21 | HD U J6 **pre16** (preview only) |
| 1.21.9 | `21.9.16-beta` | 21 | HD U J7 **pre2** (preview only) |
| 1.21.10 | `21.10.64` | 21 | HD U J7 **pre11** (preview only) |
| 1.21.11 | `21.11.45` | 21 | HD U J9 |

The 1.20.x line (1.20.1 – 1.20.6, Java 17) and the 26.x line (26.1.2, Java 25) are separate branches with their own jars.

#### Installing

1. Install the **NeoForge** version matching your Minecraft version.
2. Put the matching **`OptifiNeoforge-2.0.0+mc<your version>.jar`** into `mods/`.
3. Put the matching **OptiFine jar** (downloaded by you) into `mods/` as well.
4. Launch with the Java version listed above.

There is no installer and nothing is written into your game files.

#### Known limitations

* **1.21.6 and 1.21.7: the shader pack does not load.** Shaders and FXAA are unavailable on those two versions (the available OptiFine builds for them fail inside OptiFine itself when a pack is enabled).
* **1.21 writes four `NoClassDefFoundError` lines to stderr on every launch.** OptiFine's Reflector swallows them; startup is not affected.
* **Multiplayer has not been tested.** The attempt to exercise it never landed: the chat command was never actually delivered into the client, so the keystroke/chat path is itself unproven. Nothing here says multiplayer does not work - it says it has not been measured, and no registration action was taken on any server.
* **FXAA pixel verdicts, as of 2026-10-01** (this is a per-version measurement, not a blanket claim): **VISIBLE** on 1.20.2, 1.20.4, 1.21, 1.21.1 and 1.21.4 (same-scene frame pairs measured 2026-10-01); **VISIBLE** on 1.21.8, 1.21.9, 1.21.10 and 1.21.11 (measured 2026-09-23, not re-measured since); **below the 2.0% edge-energy threshold** on 1.20.6 and 1.21.3 (same-scene pairs, 2026-10-01) - recorded as no visible effect rather than as a pass; **not proven** on 1.20.1, 1.21.6, 1.21.7 and 26.1.2. The project owner de-prioritised FXAA on 2026-10-01 ("do not focus on FXAA, first make sure the mod runs"), so the unmeasured entries are open, not failures.

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

### OptifiNeoforge —— 在 NeoForge 上加载 OptiFine

这是一个把 **OptiFine** 带到 **NeoForge** 的客户端模组。把 OptifiNeoforge 与你**自备的 OptiFine jar** 一起放进 `mods/`,启动时由本模组执行 OptiFine 自带的补丁流程,并把打过补丁的游戏类接进 NeoForge 的类转换管线 —— 与 OptiFabric 在 Fabric Loader 上的做法同源。

#### 本项目不是什么(这些都可以自行核对)

* **独立实现**:代码为本项目自写,以 **MPL-2.0** 发布;它**不是** OptiFabric 的重传、移植或换皮 —— 目标加载器不同(NeoForge,而非 Fabric Loader)、实现不同、支持的 MC 版本也不同。做法(把 OptiFine 在运行时补进游戏)相同,已在 README 中致谢,但**没有取用其代码**。
* **不含 OptiFine 内容**:开包即可核对 —— 产物内 **`net/optifine` 条目为 0**,也**没有任何取自 OptiFine 的文件**;随包发布的抗锯齿后处理定义由本项目撰写,其中引用的着色器阶段来自**你自备的** OptiFine jar。
* **不再分发 OptiFine**:OptiFine 版权归 **sp614x** 所有,须由用户自行从官网获取。
* **未获 OptiFine 或 NeoForge 认可或支持。**

#### 支持的版本

每个 Minecraft 版本一个 jar,**互不通用**,版本写在文件名里(如 `OptifiNeoforge-2.0.0+mc1.21.8.jar`)。对照表见英文节的表格;1.20.x 线(1.20.1 – 1.20.6,Java 17)与 26.x 线(26.1.2,Java 25)在各自分支上,各有自己的 jar。

#### 安装

1. 装好与你 Minecraft 版本对应的 **NeoForge**;
2. 把对应版本的 `OptifiNeoforge-2.0.0+mc<你的版本>.jar` 放进 `mods/`;
3. 把你**自己下载的**对应版本 OptiFine jar 也放进 `mods/`;
4. 用上表所列的 Java 版本启动。

没有安装器,也不会写入你的游戏文件。

#### 已知限制

* **1.21.6 / 1.21.7:光影包不加载** —— 这两版可用的 OptiFine 构建在启用光影包时会在 OptiFine 自身内部失败,因此这两版无法使用光影与 FXAA;
* **1.21 每次启动会往 stderr 写 4 条 `NoClassDefFoundError`**(被 OptiFine 自己吞掉,不影响启动);
* **多人游戏未测**:当时那次尝试**没有落地** —— 聊天命令根本没被送进客户端, 所以"键盘/聊天输入"这条链路本身也未被证明。这里说的是**没测过**, 不是"不能用"; 且**没有在任何服务器上执行注册动作**。
* **FXAA 像素判定(截至 2026-10-01, 逐版本测量而非笼统结论)**: **可见** —— 1.20.2、1.20.4、1.21、1.21.1、1.21.4(2026-10-01 成对测量);**可见** —— 1.21.8、1.21.9、1.21.10、1.21.11(2026-09-23 测量, 之后未重测);**低于 2.0% 边缘能量阈值** —— 1.20.6、1.21.3(2026-10-01 成对, 记为"无可测效果"而不是通过);**未证明** —— 1.20.1、1.21.6、1.21.7、26.1.2。项目所有者于 2026-10-01 指示暂不重视 FXAA("先保证 mod 能正常运行"), 因此未测的那些是**未决**而不是失败。

#### 链接

* **下载:** https://github.com/Kynarain/OptifiNeoforge/releases
* **问题反馈 / 源码:** https://github.com/Kynarain/OptifiNeoforge
* **OptiFine(必需,但不随本模组分发):** https://optifine.net/downloads
