# 发布流程

## 1. 版本准备

遵循语义化版本：

- PATCH：兼容修复和小幅内部优化；
- MINOR：向后兼容的新功能；
- MAJOR：命令、配置、PDC 或行为的不兼容变化。

同步更新：

- `pom.xml` 的 `<version>`
- `pom.xml` 的 `<finalName>`
- `src/main/resources/plugin.yml`
- `HomingMissilesPlugin.VERSION`
- `src/main/resources/config.yml` 注释
- `README.md`
- `CHANGELOG.md`

若 `config-version` 变化，还要提供迁移说明。

## 2. 清理工作区

不要把这些内容打入源码包：

```text
target/
build/
.idea/
.vscode/
*.iml
dev-server/
logs/
world*/
```

## 3. 严格验证

```bash
bash tools/verify-offline.sh
mvn -U clean package
```

Windows：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\verify-offline.ps1
mvn -U clean package
```

## 4. 检查 JAR

```bash
jar tf target/HomingMissiles-3.0.0.jar
```

必须包含：

```text
plugin.yml
config.yml
cn/yjj/homingmissiles/
META-INF/MANIFEST.MF
```

不得包含：

```text
org/bukkit/
io/papermc/
net/kyori/
stubs/
test/
```

检查版本：

```bash
unzip -p target/HomingMissiles-3.0.0.jar plugin.yml
unzip -p target/HomingMissiles-3.0.0.jar META-INF/MANIFEST.MF
```

同时检查 `target/hud-packs/` 中存在两个 ZIP、两个 `.sha1` 和预览 PNG。解压 ZIP 后必须包含 `pack.mcmeta`、`assets/homingmissiles/font/hud.json` 和像素纹理；人工确认预览中的透明背景、准星、四挂点和三种威胁颜色均无裁切。

## 5. 实机测试

至少在一个干净 Paper 系测试服完成 README 中的冒烟测试矩阵。

记录：

- 服务端品牌和版本；
- Java 版本；
- 启动日志；
- 并发玩家数；
- 最大在途箭数；
- `/hbow status verbose` 指标；
- 已知插件冲突。

## 6. 生成校验和

Linux：

```bash
sha256sum target/HomingMissiles-3.0.0.jar > SHA256SUMS.txt
sha1sum target/hud-packs/*.zip
```

PowerShell：

```powershell
Get-FileHash .\target\HomingMissiles-3.0.0.jar -Algorithm SHA256
Get-FileHash .\target\hud-packs\*.zip -Algorithm SHA1
```

## 7. 发布内容

建议同时发布：

- 插件 JAR；
- 对应游戏版本的像素 HUD 资源包及其 SHA-1；
- 快速安装包；
- 源码包；
- SHA-256；
- CHANGELOG；
- 兼容性和升级说明。

## 8. 回滚准备

生产部署前保留：

- 旧版 JAR；
- 旧 `plugins/HomingMissiles/config.yml`；
- 服务器世界和插件目录备份；
- 上一版校验和。

出现问题时完整停止服务器，恢复旧 JAR 和相匹配的配置，再启动。
