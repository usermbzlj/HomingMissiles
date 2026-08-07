# 安装与升级

## 支持环境

- Java 21
- Paper、Purpur、Leaf 等 Paper API 1.21.x 分支
- 不支持纯原版、Fabric、Forge/NeoForge
- 当前不声明 Folia 兼容

## Windows 一键上传

正式构建完成后，在源码根目录运行：

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File ".\tools\upload-homingmissiles-3.1.3.ps1"
```

也可以直接运行 `tools\upload-homingmissiles-3.1.3.cmd`。上传器读取两个不会提交到 Git 的本地配置。首次使用时复制模板：

```powershell
Copy-Item .\tools\upload-config.example.properties .\tools\upload-config.properties
Copy-Item .\tools\deploy-config.example.properties .\tools\deploy-config.properties
```

若希望以后不再输入 SSH 密码，只需运行一次：

```powershell
.\tools\setup-server-ssh.cmd
```

该工具读取本地上传配置，生成专用密钥，提示一次现有服务器密码以安装公钥，验证免密登录，并自动写入 `identity_file` 与 `batch_mode=true`。它不会获得 root 权限，也不会部署插件。

在上传配置中填写 `remote_host`、`remote_user`、`port`、`remote_directory` 和可选的 `identity_file`；在部署配置中填写 `server_dir`、`service` 与 `startup_timeout`。仓库中的示例配置只包含通用占位值。上传器会把 `deploy-config.properties` 随发布包传到远端，root 替换脚本自动读取它。

上传内容包括插件 JAR、root 替换脚本、`SHA256SUMS.txt`、Leaf 1.21.11 HUD ZIP 及 SHA-1。上传器会执行本地哈希和 JAR 结构检查，仅传输一个临时 ZIP，然后通过 SSH 在远端隔离解压、二次校验并逐文件原子落位；它不会停服或运行 root 部署。客户端 Mod 作为 GitHub Release 玩家下载资产发布，不安装到服务端。

常用选项：

```powershell
# 只检查并显示操作，不连接服务器
.\tools\upload-homingmissiles-3.1.3.ps1 -DryRun

# 临时覆盖配置中的主机或端口
.\tools\upload-homingmissiles-3.1.3.ps1 `
  -RemoteHostName staging.example.com -Port 2222

# 不上传 HUD 包
.\tools\upload-homingmissiles-3.1.3.ps1 -SkipHudPack
```

不要把密码写进脚本、配置或命令行。若需要完全无人值守，应把本机公钥加入目标服务器用户的 `~/.ssh/authorized_keys`，并先人工核对服务器主机指纹。

## 推荐：root 一键事务式替换

上传完成后，以下文件位于上传配置指定的 `remote_directory`：

- `HomingMissiles-3.1.3.jar`
- `replace-homingmissiles-3.1.3.sh`
- `deploy-config.properties`

像素 HUD ZIP 不放入 `plugins/`。上传器会把 Leaf 1.21.11 HUD 包保存在同一个 `remote_directory`，之后仍需把它托管在玩家可访问的 HTTP(S) 地址；完整步骤见 [HUD_RESOURCE_PACK.md](HUD_RESOURCE_PACK.md)。资源包没有配置好时插件仍能运行，但会降级显示 BossBar。

然后由 root 执行上传器打印的准确命令，例如：

```bash
sudo bash /your/upload/directory/replace-homingmissiles-3.1.3.sh
```

脚本会读取 `deploy-config.properties` 的 `server_dir`，并查找 `WorkingDirectory` 与该目录完全相同的唯一 systemd 服务；不会仅凭名称猜测，更不会误停整个管理面板。也可以显式覆盖：

```bash
bash replace-homingmissiles-3.1.3.sh \
  --config /root/deploy-config.properties \
  --jar /root/upload/HomingMissiles-3.1.3.jar \
  --server-dir /opt/minecraft \
  --service minecraft.service
```

脚本在改动服务器前验证固定 SHA-256、ZIP 完整性及 `plugin.yml` 身份，并执行：

1. 排他锁与 systemd 工作目录核对；
2. 停服并确认服务器目录中没有残留 Java 进程；
3. 把所有检测到的旧 HomingMissiles JAR 移入 `plugins/.homingmissiles-backups/<UTC时间>-<PID>/`；
4. 快照但不覆盖现有 `plugins/HomingMissiles/config.yml`；
5. 在 `plugins/` 同一文件系统内原子安装 3.1.3；
6. 恢复服务，并在 `latest.log` 或 systemd journal 中验证 3.1.3 启用标记；
7. 任一步失败时移走新 JAR、恢复旧 JAR，并在服务原先运行时重新启动旧服务。

发布 JAR 的固定校验值：

```text
SHA-256  4a6139236075998f7d1f1ebbb5ca27c9babf141900abdfdf087abc3d9fd6a343
```

服务器由面板而不是独立 systemd 单元管理时，脚本会在发现运行中的 Java 进程后安全退出。先通过面板完整停服，再使用：

```bash
sudo bash /your/upload/directory/replace-homingmissiles-3.1.3.sh \
  --install-only
```

`--install-only` 不会启动服务器；完成后需手动启动并检查日志。所有参数见 `bash replace-homingmissiles-3.1.3.sh --help`。

## 安装成品 JAR

1. 完整停止目标 Minecraft 服务。
2. 确认自己操作的是正确服务的 `plugins/` 目录。
3. 删除旧版同名 JAR，不要同时保留多个版本。
4. 放入 `HomingMissiles-3.1.3.jar`。
5. 确认服务器用户至少拥有读取权限。
6. 完整启动服务。

Linux 示例：

```bash
install -m 0644 HomingMissiles-3.1.3.jar /path/to/server/plugins/HomingMissiles-3.1.3.jar
```

## 从源码构建后安装

在源码根目录：

```bash
mvn -U clean package
install -m 0644 target/HomingMissiles-3.1.3.jar /path/to/server/plugins/
```

Windows PowerShell：

```powershell
mvn -U clean package
Copy-Item .\target\HomingMissiles-3.1.3.jar C:\path\to\server\plugins\ -Force
```

## 启动验证

日志应出现：

```text
[HomingMissiles] Loading server plugin HomingMissiles v3.1.3
[HomingMissiles] Enabling HomingMissiles v3.1.3
[HomingMissiles] HomingMissiles 3.1.3 已启用
```

进游戏执行：

```text
/hbow version
/hbow help
/hbow status verbose
/hbow get
```

随后在 `plugins/HomingMissiles/config.yml` 补齐 `targeting.manual-lock.*`、`hud.resource-pack.url` 与 `.sha1`，执行 `/hbow reload` 并让测试玩家重新连接。替换脚本有意保留旧配置，因此升级时这一步不会被脚本代替。

## 升级

1. 停止服务器。
2. 备份旧 JAR 和 `plugins/HomingMissiles/config.yml`。
3. 删除旧 JAR。
4. 放入新 JAR。
5. 若配置版本变化，先让新版本生成默认配置，再迁移自定义项。
6. 启动并检查日志中的配置警告。
7. 不要同时启用旧命令方块制导系统。

从配置版本 4 升至 5 时必须手工迁移 `tracking.lock-retention-range`、`tracking.terminal-boost.*`、`tracking.max-lead-ticks` 与 `hud.resource-pack.*`。缺失字段会使用内存默认值，不会导致停服，但旧 YAML 不会被静默改写。

从配置版本 5 升至 6 时新增 `targeting.manual-lock.duration-ticks`、`cone-degrees`、`break-cone-degrees` 与 `break-grace-ticks`。6 版发射行为改为强制手动标定，`tracking.dynamic-retargeting` 和 `switch-advantage-blocks` 仅保留兼容读取。旧文件缺字段时会采用 16 tick、10°/16° 与 4 tick 容错的内存默认值，但仍建议显式迁移后再上线。

从配置版本 6 升至 7 时新增 `hud.client-mod.download-url` 与 `detection-grace-ticks`。旧配置缺少这两项时，插件会自动采用 v3.1.3 GitHub Release 页面与 40 tick 检测窗口。玩家个人 HUD 开关存于 PDC，不需要迁移 YAML。

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
