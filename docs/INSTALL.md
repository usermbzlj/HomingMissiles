# 安装与升级

## 支持环境

- Java 21
- Paper、Purpur、Leaf 等 Paper API 1.21.x 分支
- 不支持纯原版、Fabric、Forge/NeoForge
- 当前不声明 Folia 兼容

## 安装成品 JAR

1. 完整停止目标 Minecraft 服务。
2. 确认自己操作的是正确服务的 `plugins/` 目录。
3. 删除旧版同名 JAR，不要同时保留多个版本。
4. 放入 `HomingMissiles-2.0.0.jar`。
5. 确认服务器用户至少拥有读取权限。
6. 完整启动服务。

Linux 示例：

```bash
install -m 0644 HomingMissiles-2.0.0.jar /path/to/server/plugins/HomingMissiles-2.0.0.jar
```

## 从源码构建后安装

在源码根目录：

```bash
mvn -U clean package
install -m 0644 target/HomingMissiles-2.0.0.jar /path/to/server/plugins/
```

Windows PowerShell：

```powershell
mvn -U clean package
Copy-Item .\target\HomingMissiles-2.0.0.jar C:\path\to\server\plugins\ -Force
```

## 启动验证

日志应出现：

```text
[HomingMissiles] Loading server plugin HomingMissiles v2.0.0
[HomingMissiles] Enabling HomingMissiles v2.0.0
[HomingMissiles] HomingMissiles 2.0.0 已启用
```

进游戏执行：

```text
/hbow version
/hbow help
/hbow status verbose
/hbow get
```

## 升级

1. 停止服务器。
2. 备份旧 JAR 和 `plugins/HomingMissiles/config.yml`。
3. 删除旧 JAR。
4. 放入新 JAR。
5. 若配置版本变化，先让新版本生成默认配置，再迁移自定义项。
6. 启动并检查日志中的配置警告。
7. 不要同时启用旧命令方块制导系统。

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

### 不要使用的方式

- 不要使用 Minecraft `/reload` 替代服务器重启；
- 不要用第三方热加载器反复卸载本插件；
- 不要在生产服直接覆盖正在使用的 JAR；
- 不要以为 SSH 会自动访问 Windows 的 `C:\` 路径，上传需使用 SCP/SFTP。
