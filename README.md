# HomingMissiles 3.1.3

面向 **Paper / Purpur / Leaf 1.21.x** 的高伤害连续物理制导弓插件。插件逐 tick 修改箭的真实速度向量，不传送箭实体，因此保留惯性、转弯半径、原版碰撞与伤害流程。每名玩家最多同时维持 4 枚在途导弹。

玩家必须拉住制导弓，把视野内的一名有效玩家持续保持在标定框内，看到 `LOCK` 后才能松弦发射；未完成锁定会强制取消射击。导弹只追踪发射前手动标定的 UUID，不会在飞行中自动换人；锁定后进入独立的 192 格保持圈，以动态截击解持续预判目标。HUD 参考 FlightHud 的实际中心计算和飞行仪表语言，同时提供 Fabric 客户端逐帧渲染与纯服务端资源包降级。插件自动检测 Mod；安装时获得与准星严格同心的连续矢量 HUD，未安装时仍有居中像素 HUD/BossBar。完整 HUD 覆盖整个鞘翅飞行，玩家可关闭，但手动锁定进度始终保留。

> 当前发布线：`3.1.3`（双通道精确居中飞行 HUD，`config-version: 7`）
> 编译 API：Paper API `1.21.11-R0.1-SNAPSHOT`
> Java：21  
> 源码仓库：https://github.com/usermbzlj/HomingMissiles  
> 生产基线：Leaf `1.21.11`；特效层会在运行时适配 `FLASH` 的旧版无数据与新版 `Color` 数据签名。该记录不等于对所有 Paper 分支和未来版本的无条件兼容保证。

## 目录

- [给服务器管理员：快速安装](#给服务器管理员快速安装)
- [给开发者：从零构建](#给开发者从零构建)
- [验证与测试](#验证与测试)
- [本地开发服务器](#本地开发服务器)
- [项目结构](#项目结构)
- [运行时调用链](#运行时调用链)
- [核心设计约束](#核心设计约束)
- [常见扩展入口](#常见扩展入口)
- [配置、命令与权限](#配置命令与权限)
- [发布流程](#发布流程)
- [接手开发清单](#接手开发清单)
- [兼容性与已知边界](#兼容性与已知边界)

---

## 给服务器管理员：快速安装

生产服推荐把插件和替换脚本上传到服务器；像素 HMD 资源包还需放到玩家能够访问的 HTTP(S) 地址：

- `target/HomingMissiles-3.1.3.jar`
- `tools/replace-homingmissiles-3.1.3.sh`
- `target/hud-packs/HomingMissiles-HUD-1.21.11.zip`（Leaf 1.21.11）
- `client-mod/build/libs/HomingMissiles-HUD-Fabric-1.21.11-3.1.3.jar`（推荐给玩家）

在 Windows 源码目录中执行一条命令即可上传完整发布包：

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File ".\tools\upload-homingmissiles-3.1.3.ps1"
```

也可以直接运行 `tools\upload-homingmissiles-3.1.3.cmd`。连接信息来自不会提交到 Git 的 `tools/upload-config.properties`，Minecraft 目录与服务信息来自同样被忽略的 `tools/deploy-config.properties`；仓库只保留两个 `.example.properties` 通用模板。上传器会把发布文件和部署配置打成一次性传输包，在远端隔离解压、复核并逐文件原子落位。它不会停服或执行 root 安装。

第一次配置免密 SSH 时只需运行 `tools\setup-server-ssh.cmd`；它会要求一次现有服务器密码，随后自动验证密钥并更新本地上传配置。

上传成功后，上传器会打印 root 应执行的准确命令，例如：

```bash
sudo bash /your/upload/directory/replace-homingmissiles-3.1.3.sh
```

脚本默认读取同目录的 JAR 与 `deploy-config.properties`，并按配置中的服务器目录精确匹配唯一的 systemd 服务。命令行 `--jar`、`--config`、`--server-dir` 和 `--service` 可以覆盖配置。脚本会固定校验 JAR 的 SHA-256
`4a6139236075998f7d1f1ebbb5ca27c9babf141900abdfdf087abc3d9fd6a343`，自动停服、备份旧 JAR、原子替换、重启并验证插件启用；任一步失败都会尝试恢复旧 JAR 和原服务状态。现有 `plugins/HomingMissiles/config.yml` 不会被覆盖。

如果不使用脚本，必须手工完成：完全停服、移走所有旧版 HomingMissiles JAR、放入 3.1.3 JAR、完整启动。不要使用 Minecraft 的 `/reload` 代替重启，也不要在 `plugins/` 中同时保留多个版本。

在日志中确认：

```text
[HomingMissiles] Enabling HomingMissiles v3.1.3
[HomingMissiles] HomingMissiles 3.1.3 已启用
```

进入游戏执行：

```text
/hbow version
/hbow help
/hbow status verbose
/hbow get
```

完整安装与故障排查见 [`docs/INSTALL.md`](docs/INSTALL.md)。

---

## 给开发者：从零构建

### 1. 必需环境

正式构建需要：

| 工具 | 要求 | 用途 |
|---|---|---|
| JDK | 21 | 编译插件和运行测试 |
| Maven | 建议 3.9 或更高 | 解析 Paper API 并生成发布 JAR |
| Git | 可选但推荐 | 版本管理和协作 |
| Paper/Purpur/Leaf 测试服 | 1.21.x | 实机联调 |

首次 Maven 构建需要网络访问：

- Maven Central
- PaperMC Maven 仓库

先确认环境：

```bash
java -version
mvn -version
```

两条命令都应显示正在使用 Java 21。仅安装 JRE 不够，必须是包含 `javac` 的 JDK。

### 2. 获取源码

从 Git 仓库获取时：

```bash
git clone https://github.com/usermbzlj/HomingMissiles.git
cd HomingMissiles
```

使用源码压缩包时，解压后进入包含 `pom.xml` 的目录：

```bash
cd HomingMissiles
```

### 3. 正式构建

Linux、macOS、WSL：

```bash
mvn -U clean package
```

Windows PowerShell：

```powershell
mvn -U clean package
```

成功后发布产物位于：

```text
target/HomingMissiles-3.1.3.jar
target/hud-packs/HomingMissiles-HUD-1.21.4.zip
target/hud-packs/HomingMissiles-HUD-1.21.11.zip
target/hud-packs/HomingMissiles-HUD-preview.png
```

`target/original-*.jar`、`build/classes/`、`build/stubs/` 都不是正式服务器插件产物。服务器中只应安装 `target/HomingMissiles-3.1.3.jar`。

跳过测试仅用于临时排查，不应作为发布流程：

```bash
mvn -U clean package -DskipTests
```

> 当前仓库的纯逻辑测试由 `tools/verify-offline.*` 运行，不依赖 Maven Surefire。正式发布前应同时执行 Maven 构建和离线严格验证。

### 4. 常见构建失败

#### `mvn: command not found`

安装 Maven，或在 IDE 中配置 Maven Home。不要把 Maven 命令粘贴进 Minecraft 控制台。

#### `release version 21 not supported`

当前 Maven 实际使用的不是 JDK 21。检查：

```bash
mvn -version
```

Linux 可临时指定：

```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean package
```

Windows PowerShell 可临时指定：

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn clean package
```

#### Paper API 无法下载

确认服务器或开发机能够访问 `https://repo.papermc.io/`，然后重试：

```bash
mvn -U clean package
```

代理、镜像或企业 Maven 配置应写入用户级 `~/.m2/settings.xml`，不要把私人凭据提交到仓库。

---

## 验证与测试

项目有两条不同的验证链路。

### A. 正式 Maven 构建

```bash
mvn -U clean package
```

它负责：

- 解析真实 Paper API；
- 编译主代码；
- 复制 `plugin.yml` 与 `config.yml`；
- 生成可安装 JAR，以及两个版本的像素 HUD 资源包和预览图。

### B. 无网络严格验证

Linux、macOS、WSL：

```bash
bash tools/verify-offline.sh
```

Windows PowerShell：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\verify-offline.ps1
```

离线验证负责：

- 使用仓库内的最小 Bukkit API 桩编译；
- 开启 `-Xlint:all -Werror`；
- 运行向量边界测试；
- 运行命令纠错与 Tab 前缀测试；
- 运行配置格式工具测试；
- 运行动态截击/后程点火以及 HUD 图层区间、航向/俯仰/速度/高度量化、方向分区、威胁等级和警报节奏测试。

离线 API 桩只用于编译和纯逻辑测试，**不能证明真实 Paper 运行兼容性，也不会被打入发布 JAR**。

### C. 发布前人工冒烟测试

至少完成以下场景：

1. 插件能够在干净测试服启动，日志无异常。
2. `/hbow help`、Tab 补全、权限过滤正确。
3. 普通弓不会触发制导，插件发放的弓能够触发。
4. 单人射箭时不会锁定自己。
5. 两名及以上玩家同时使用时，每支箭只排除自己的射手。
6. 多箭并发时不会串目标或停止调度。
7. 目标死亡、离线、跨世界、进入被排除模式后导弹停止制导，但不会自动重选其他玩家。
8. 箭正常撞墙、命中、自毁，不发生瞬移。
9. 达到个人/全服上限和冷却时反馈准确。
10. 拉弓时只有把有效玩家保持在中央视锥内满 16 tick 才出现 `LOCK`；提前松弦会强制取消，完成后松弦只追踪标定对象。
11. 第五枚导弹必定被拒绝，即使射手拥有 `homingmissiles.bypass.limits`；默认命中伤害不低于 12。
12. 新发放的弓默认带火矢、无限、耐久 III、不可损坏与力量 V；关闭任一 `item.enchantments.*` 后重新发放可验证对应变化。
13. 接受资源包后，标定框/进度以及航向、速度、高度、俯仰和飞行矢量逐 tick 平滑更新；发射者听到定制合成的发射/锁定反馈，被锁玩家看到方向/威胁层并听到来自 CC0 素材的逐步加急空间警报。
14. 资源包拒绝/失败时正确降级为 BossBar；重新接受并加载后恢复像素 HMD。
15. `/hbow reload` 面对非法配置时保留可用设置并给出警告。
16. 区块卸载、重新加载和服务器重启行为符合 `lifecycle` 配置。
17. `/hbow status verbose` 的平均调度耗时没有持续异常增长。

---

## 本地开发服务器

推荐为开发单独准备测试服，不要直接在生产服热替换。

示例目录：

```text
dev-server/
├─ paper.jar
├─ plugins/
├─ server.properties
└─ eula.txt
```

构建并复制：

```bash
mvn clean package
cp target/HomingMissiles-3.1.3.jar /path/to/dev-server/plugins/
```

Windows PowerShell：

```powershell
mvn clean package
Copy-Item .\target\HomingMissiles-3.1.3.jar C:\path\to\dev-server\plugins\ -Force
```

然后完整启动测试服。开发中不要依赖 `/reload`：

- `/hbow reload` 只重载本插件配置；
- Minecraft `/reload` 主要用于数据包；
- 第三方插件管理器的热卸载/热加载可能留下调度任务、实体或类加载器状态；
- 修改 Java 代码后应完整重启服务器。

建议调试命令：

```text
/hbow status verbose
/hbow inspect <玩家>
/hbow clear all
/hbow tune
```

日志位置：

```text
logs/latest.log
```

---

## 项目结构

```text
HomingMissilesPlugin/
├─ pom.xml
├─ README.md
├─ CONTRIBUTING.md
├─ CHANGELOG.md
├─ LICENSE.txt
├─ src/main/java/cn/yjj/homingmissiles/
│  ├─ HomingMissilesPlugin.java
│  ├─ command/
│  │  └─ HomingBowCommand.java
│  ├─ config/
│  │  ├─ PluginSettings.java
│  │  └─ SettingsManager.java
│  ├─ item/
│  │  └─ HomingBowFactory.java
│  ├─ listener/
│  │  └─ HomingListener.java
│  ├─ model/
│  │  └─ TrackedArrow.java
│  ├─ service/
│  │  ├─ HomingService.java
│  │  ├─ ManualLockService.java
│  │  └─ LockHudService.java
│  └─ util/
│     ├─ CommandUtil.java
│     ├─ GuidanceMath.java
│     ├─ HudFormat.java
│     ├─ MessageService.java
│     └─ VectorMath.java
├─ src/main/resources/
│  ├─ plugin.yml
│  └─ config.yml
├─ src/main/hud/audio/
│  ├─ launch.ogg
│  ├─ lock_confirm.ogg
│  ├─ missile_warning.ogg
│  └─ missile_critical.ogg
├─ src/main/hud/third-party/
│  ├─ README.md
│  ├─ CC0-1.0.txt
│  └─ 可审计的原始下载文件
├─ test/
│  └─ cn/yjj/homingmissiles/
├─ stubs/
│  └─ src/main/java/org/bukkit/
├─ tools/
│  ├─ verify-offline.sh
│  ├─ verify-offline.ps1
│  ├─ HudPackBuilder.java
│  ├─ build-hud-audio.ps1
│  ├─ test-replacement-script.sh
│  ├─ replace-homingmissiles-3.1.3.sh
│  ├─ deploy-config.example.properties
│  ├─ upload-config.example.properties
│  ├─ setup-server-ssh.ps1 / .cmd
│  ├─ upload-homingmissiles-3.1.3.ps1
│  └─ upload-homingmissiles-3.1.3.cmd
├─ client-mod/                 # Fabric 1.21.11 纯客户端逐帧 HUD
└─ docs/
   ├─ INSTALL.md
   ├─ COMMANDS.md
   ├─ CONFIGURATION.md
   ├─ HUD_RESOURCE_PACK.md
   ├─ DEVELOPMENT.md
   ├─ ARCHITECTURE.md
   └─ RELEASING.md
```

### 各模块职责

| 模块 | 职责 |
|---|---|
| `HomingMissilesPlugin` | 组合根与生命周期：创建服务、注册监听器/命令、启动和关闭调度器 |
| `SettingsManager` | 读取、校验、修正、保存与原子替换配置快照 |
| `PluginSettings` | 不可变运行时设置记录 |
| `HomingBowFactory` | 创建和识别带 PDC 身份的制导弓 |
| `HomingListener` | 连接 Bukkit 事件与领域服务 |
| `HomingService` | 箭状态、目标选择、每 tick 制导、持久化恢复、限制和诊断 |
| `ManualLockService` | 拉弓期间的视锥筛选、锁定进度/容错与单次目标消费 |
| `LockHudService` | 检测客户端 Mod、同步玩家 HUD 偏好/武器状态、延迟发送资源包、组合纯服务端居中 HMD、维护 BossBar 与空间警报 |
| `TrackedArrow` | 单支在途箭的目标观测、拉远计数和后程点火状态 |
| `HomingBowCommand` | 命令路由、权限、帮助、参数校验和 Tab 补全 |
| `MessageService` | 消息模板、占位符、聊天与 Action Bar 输出 |
| `GuidanceMath` | 动态截击时间解与后程发动机点火判定 |
| `HudFormat` | 535 个图层字形、负间距叠层、细粒度飞行仪表、49 区标定框、锁定进度、八方向与警报节奏映射 |
| `VectorMath` | 有限角速度转向与数值边界保护 |
| `CommandUtil` | 前缀过滤、编辑距离和命令建议 |

更详细的组件关系见 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)。

---

## 运行时调用链

### 插件启用

```text
onEnable
  → saveDefaultConfig
  → SettingsManager.reload
  → 创建 MessageService / HomingBowFactory / LockHudService / HomingService
  → 注册 HomingListener
  → 注册 HomingBowCommand 与 TabCompleter
  → HomingService.start
  → recoverLoadedArrows
```

### 玩家射箭

```text
EntityShootBowEvent
  → 检查射手、弓身份和权限
  → 消费 ManualLockService 已完成的单次锁定
  → HomingService.tryTrack
  → 检查世界、冷却、个人/全服上限
  → 写入射手和已标定目标 UUID 的箭 PDC 状态
  → 加入 tracked 状态表
```

### 每 tick 制导

```text
Bukkit 主线程调度
  → LockHudService.beginTick
  → ManualLockService.tick（拉弓、视锥候选、锁定进度）
  → 遍历 tracked 箭
  → 单箭 try/catch 隔离
  → 校验实体、世界和寿命
  → 只验证发射前标定目标的保持圈，不重新选人
  → 动态截击时间解与目标预判位置
  → 需要时不可逆地点燃后程发动机
  → VectorMath.rotateTowards
  → 限制速度并写回 velocity
  → 首锁提示（一次性）+ reportLock
  → LockHudService.endTick（像素 HMD / 降级 BossBar / 空间化来袭音）
```

### 命中与生命周期

```text
ProjectileHitEvent → 移除跟踪状态 → 清理 PDC → 播放命中特效
EntitiesUnloadEvent → 持久化并从内存挂起
EntitiesLoadEvent → 验证 PDC 后恢复
onDisable → 按 lifecycle 设置删除或保留在途箭
```

---

## 核心设计约束

接手开发时应保持以下不变量：

1. **箭实体不使用传送实现制导。**  
   制导只能通过修改真实速度向量完成，否则客户端表现会跳动，碰撞也会失真。

2. **所有 Bukkit 实体访问都在服务器主线程完成。**  
   当前服务使用同步 tick 任务。不要把 `Player`、`World`、`Entity` 操作直接移入异步线程。

3. **每支箭只排除自己的射手。**  
   射手身份保存在 `TrackedArrow` 和箭 PDC 中，不能用全局临时标签替代。

4. **配置对运行时是不可变快照。**  
   先完整解析并校验新的 `PluginSettings`，再替换 `current`。不要边读取 YAML 边执行制导。

5. **单箭故障不能终止全局调度器。**  
   新逻辑应继续保持单箭异常隔离和无效实体清理。

6. **PDC 键属于兼容性协议。**  
   当前关键键：
   - 物品：`homing_bow`
   - 箭：`homing_projectile`
   - 射手：`shooter_uuid`
   - 标定目标：`target_uuid`
   - 年龄：`age_ticks`
   - 会话：`session_id`

   修改或删除这些键前必须设计迁移，否则旧弓和遗留箭会失效。

7. **状态计数必须一致。**  
   修改 `tracked` 时必须同步维护 `shooterCounts`，并走统一的 `addState` / `removeState` 路径。

8. **数值必须有限。**  
   写回箭速度前必须防止 `NaN`、无穷和零向量边界扩散。

---

## 常见扩展入口

### 新增配置项

按顺序修改：

1. `src/main/resources/config.yml`
2. `PluginSettings`
3. `SettingsManager.reload`
4. 必要时加入 `TUNABLES` 或预设
5. 使用该设置的服务代码
6. `docs/CONFIGURATION.md`
7. 配置边界测试

### 新增子命令

按顺序修改：

1. `HomingBowCommand.dispatch`
2. 新的 `handle...` 方法
3. `registerHelp`
4. `onTabComplete`
5. `src/main/resources/plugin.yml` 权限
6. `docs/COMMANDS.md`
7. 命令工具测试或实机权限测试

### 修改手动标定与目标验证

入口位于：

```text
ManualLockService
HomingService.selectTarget
HomingService.isValidTarget
```

修改后必须重新测试：

- 自身排除；
- 世界一致性；
- 创造/旁观模式；
- `target.exempt`；
- `Player#canSee`；
- 视线检测；
- 视锥边缘与断锁容错；
- 未锁松弦强制取消；
- 发射后不切换到其他 UUID。

### 修改飞行算法

入口位于：

```text
HomingService.steerArrow
VectorMath.rotateTowards
```

至少覆盖：

- 同方向；
- 90°转向；
- 180°反向；
- 当前速度为零；
- 目标方向为零；
- 极低/极高速度；
- 目标近距离交叉；
- 多箭并发。

### 提供公共 API

当前项目没有稳定的公共 Java API。其他插件不应直接依赖内部类。若未来开放 API，应：

- 建立独立 `api` 包；
- 明确线程规则；
- 使用接口和事件，不暴露内部可变集合；
- 引入语义化版本承诺；
- 为 API 兼容性增加测试。

---

## 配置、命令与权限

- 完整命令：[`docs/COMMANDS.md`](docs/COMMANDS.md)
- 完整配置：[`docs/CONFIGURATION.md`](docs/CONFIGURATION.md)（含手动标定、远距保持、后程加速、`hud.*` 与 `config-version: 7`）
- Fabric 客户端 Mod：[`docs/CLIENT_MOD.md`](docs/CLIENT_MOD.md)
- 像素 HUD 部署：[`docs/HUD_RESOURCE_PACK.md`](docs/HUD_RESOURCE_PACK.md)
- 安装和排错：[`docs/INSTALL.md`](docs/INSTALL.md)
- 开发工作流：[`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md)
- 变更记录：[`CHANGELOG.md`](CHANGELOG.md)

升级后，旧配置缺失的新字段会使用 7 版安全默认值并在重载时给出版本警告；文件不会被自动覆盖。7 版新增 Mod 自动检测和个人 HUD 开关。要启用纯服务端像素 HMD，仍需补齐 `hud.resource-pack.url` 与 `sha1`，或把资源合并进全服资源包后启用 `assume-server-pack-provides-hud`。

常用命令：

```text
/hbow help
/hbow hud [on|off|toggle|status]
/hbow get [数量]
/hbow give <玩家> [数量]
/hbow status [verbose]
/hbow inspect [玩家]
/hbow clear [mine|all|player|world] [名称]
/hbow preset <balanced|agile|realistic>
/hbow tune [show|reset|参数 数值]
/hbow reload
/hbow version
```

---

## 发布流程

发布前不要只把 IDE 生成的 class 文件压缩成 JAR。完整步骤见 [`docs/RELEASING.md`](docs/RELEASING.md)。

最小流程：

```bash
bash tools/verify-offline.sh
mvn -U clean package
jar tf target/HomingMissiles-3.1.3.jar
sha256sum target/HomingMissiles-3.1.3.jar
sha1sum target/hud-packs/*.zip
```

版本号至少需要同步检查：

- `pom.xml`
- `pom.xml` 中的 `finalName`
- `src/main/resources/plugin.yml`
- `HomingMissilesPlugin.VERSION`
- `src/main/resources/config.yml` 头部
- `README.md`
- `CHANGELOG.md`

发布前必须确认 JAR 中不包含：

```text
org/bukkit/
io/papermc/
net/kyori/
```

这些 API 应由服务器提供。

---

## 接手开发清单

新维护者建议依次完成：

1. 阅读本 README。
2. 阅读 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)。
3. 执行离线严格验证。
4. 执行 Maven 正式构建。
5. 在干净测试服完成冒烟测试。
6. 用 `/hbow status verbose` 观察调度成本。
7. 检查 `plugin.yml` 的命令和权限是否与代码一致。
8. 检查 `config.yml`、`PluginSettings` 和 `SettingsManager` 字段是否一致。
9. 确认没有修改既有 PDC 键，或已经提供迁移。
10. 在生产部署前备份旧 JAR 与 `plugins/HomingMissiles/config.yml`。

协作规范见 [`CONTRIBUTING.md`](CONTRIBUTING.md)。

---

## 兼容性与已知边界

- 目标服务端：Paper、Purpur、Leaf 等 Paper API 分支。
- 不适用于纯原版、Fabric、Forge 或 NeoForge 服务端。
- 当前不声明 Folia 兼容。Folia 需要区域调度器和实体调度器适配，不能只修改 `folia-supported` 声明。
- 当前依赖同步主线程 tick；极高箭数会线性增加成本。
- `require-line-of-sight` 会增加方块射线检查开销。
- `respect-vanish` 依赖 `Player#canSee`，具体效果取决于隐身插件实现。
- 插件未提供数据库，也不跨服务器同步箭状态。
- 插件没有稳定公共 API。
- 服务器离线模式和以 root 用户运行属于服务器安全配置问题，与本插件功能无关，但生产环境不推荐。

---

## 许可

MIT License。详见 [`LICENSE.txt`](LICENSE.txt)。
