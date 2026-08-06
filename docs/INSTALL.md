# 安装与升级

## 支持环境

- Java 21
- Paper、Purpur、Leaf 等 Paper API 1.21.x 分支
- 不支持纯原版、Fabric、Forge/NeoForge
- 当前不声明 Folia 兼容

## 推荐：root 一键事务式替换

在 R4700G3 上，把两个文件都上传至 `/home/pell/`：

- `/home/pell/HomingMissiles-3.0.0.jar`
- `/home/pell/replace-homingmissiles-3.0.0.sh`

像素 HUD ZIP 不放入 `plugins/`。Leaf 1.21.11 使用 `target/hud-packs/HomingMissiles-HUD-1.21.11.zip`，并把它托管在玩家可访问的 HTTP(S) 地址；完整步骤见 [HUD_RESOURCE_PACK.md](HUD_RESOURCE_PACK.md)。资源包没有配置好时插件仍能运行，但会降级显示 BossBar。

然后由 root 执行：

```bash
bash /home/pell/replace-homingmissiles-3.0.0.sh
```

该主机的默认服务器目录是 `/home/NextGeneration/McThuner`。脚本会查找 `WorkingDirectory` 与该目录完全相同的唯一 systemd 服务；不会仅凭名称猜测，更不会误停整个管理面板。其他主机或路径可以显式覆盖：

```bash
bash replace-homingmissiles-3.0.0.sh \
  --jar /root/upload/HomingMissiles-3.0.0.jar \
  --server-dir /opt/minecraft \
  --service minecraft.service
```

脚本在改动服务器前验证固定 SHA-256、ZIP 完整性及 `plugin.yml` 身份，并执行：

1. 排他锁与 systemd 工作目录核对；
2. 停服并确认服务器目录中没有残留 Java 进程；
3. 把所有检测到的旧 HomingMissiles JAR 移入 `plugins/.homingmissiles-backups/<UTC时间>-<PID>/`；
4. 快照但不覆盖现有 `plugins/HomingMissiles/config.yml`；
5. 在 `plugins/` 同一文件系统内原子安装 3.0.0；
6. 恢复服务，并在 `latest.log` 或 systemd journal 中验证 3.0.0 启用标记；
7. 任一步失败时移走新 JAR、恢复旧 JAR，并在服务原先运行时重新启动旧服务。

发布 JAR 的固定校验值：

```text
SHA-256  26a477b0e1087f4d95a503a27ae99f9a4284d2fb44fa76739d9db8e69fe90906
```

服务器由面板而不是独立 systemd 单元管理时，脚本会在发现运行中的 Java 进程后安全退出。先通过面板完整停服，再使用：

```bash
bash /home/pell/replace-homingmissiles-3.0.0.sh \
  --install-only
```

`--install-only` 不会启动服务器；完成后需手动启动并检查日志。所有参数见 `bash replace-homingmissiles-3.0.0.sh --help`。

## 安装成品 JAR

1. 完整停止目标 Minecraft 服务。
2. 确认自己操作的是正确服务的 `plugins/` 目录。
3. 删除旧版同名 JAR，不要同时保留多个版本。
4. 放入 `HomingMissiles-3.0.0.jar`。
5. 确认服务器用户至少拥有读取权限。
6. 完整启动服务。

Linux 示例：

```bash
install -m 0644 HomingMissiles-3.0.0.jar /path/to/server/plugins/HomingMissiles-3.0.0.jar
```

## 从源码构建后安装

在源码根目录：

```bash
mvn -U clean package
install -m 0644 target/HomingMissiles-3.0.0.jar /path/to/server/plugins/
```

Windows PowerShell：

```powershell
mvn -U clean package
Copy-Item .\target\HomingMissiles-3.0.0.jar C:\path\to\server\plugins\ -Force
```

## 启动验证

日志应出现：

```text
[HomingMissiles] Loading server plugin HomingMissiles v3.0.0
[HomingMissiles] Enabling HomingMissiles v3.0.0
[HomingMissiles] HomingMissiles 3.0.0 已启用
```

进游戏执行：

```text
/hbow version
/hbow help
/hbow status verbose
/hbow get
```

随后在 `plugins/HomingMissiles/config.yml` 补齐 `hud.resource-pack.url` 与 `.sha1`，执行 `/hbow reload` 并让测试玩家重新连接。替换脚本有意保留旧配置，因此从 4 版升级时这一步不会被脚本代替。

## 升级

1. 停止服务器。
2. 备份旧 JAR 和 `plugins/HomingMissiles/config.yml`。
3. 删除旧 JAR。
4. 放入新 JAR。
5. 若配置版本变化，先让新版本生成默认配置，再迁移自定义项。
6. 启动并检查日志中的配置警告。
7. 不要同时启用旧命令方块制导系统。

从配置版本 4 升至 5 时必须手工迁移 `tracking.lock-retention-range`、`tracking.terminal-boost.*`、`tracking.max-lead-ticks` 与 `hud.resource-pack.*`。缺失字段会使用内存默认值，不会导致停服，但旧 YAML 不会被静默改写。

## 常见问题

### 插件未加载

检查：

```bash
grep -iE "HomingMissiles|ERROR|Exception" logs/latest.log | tail -n 100
```

可能原因：

- JAR 放错了另一个服务的 `plugins/`；
- Java 不是21；
- 服务端不是 Paper API 分支；
- JAR 损坏；
- 文件权限不足；
- 同时存在多个版本。

### 命令不存在

插件没有成功启用，或者命令被其他插件覆盖。先执行：

```text
/plugins
```

再检查日志。

### 箭不追踪

确认：

- 使用的是 `/hbow get` 或 `/hbow give` 发放的弓；
- 有至少一个有效的其他玩家；
- 目标不在豁免权限中；
- 当前世界没有禁用；
- 没有达到个人或全服上限；
- 旧命令方块系统已关闭。

### 行为不连贯

检查服务器 TPS、`/hbow status verbose` 和 spark。插件每 tick 同步更新箭；服务器本身低 TPS 时视觉更新也会降低。

### 只有 BossBar，没有像素 HUD

BossBar 是资源包未就绪时的安全降级。核对资源包 URL、SHA-1、玩家接受状态和版本，并参考 [HUD_RESOURCE_PACK.md](HUD_RESOURCE_PACK.md)。

### 不要使用的方式

- 不要使用 Minecraft `/reload` 替代服务器重启；
- 不要用第三方热加载器反复卸载本插件；
- 不要在生产服直接覆盖正在使用的 JAR；
- 不要以为 SSH 会自动访问 Windows 的 `C:\` 路径，上传需使用 SCP/SFTP。
