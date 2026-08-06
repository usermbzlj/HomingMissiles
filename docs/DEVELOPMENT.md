# 开发工作流

本文面向准备修改 HomingMissiles 源码的维护者。

## 1. 环境基线

- JDK 21
- Maven 3.9+
- UTF-8
- Paper/Purpur/Leaf 1.21.x 测试服
- 推荐 IDE：IntelliJ IDEA 或 VS Code + Java 扩展

确认 Maven 使用的 Java：

```bash
mvn -version
```

输出中的 Java version 必须是 21。

## 2. 日常循环

```text
修改源码
→ 运行离线严格验证
→ Maven 正式构建
→ 复制到开发服
→ 完整重启
→ 进行目标场景测试
→ 检查 latest.log 与 /hbow status verbose
```

命令：

```bash
bash tools/verify-offline.sh
mvn clean package
cp target/HomingMissiles-3.0.0.jar /path/to/dev-server/plugins/
```

Windows：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\verify-offline.ps1
mvn clean package
Copy-Item .\target\HomingMissiles-3.0.0.jar C:\path\to\dev-server\plugins\ -Force
```

Maven 的 `package` 阶段还会以无界面 Java AWT 执行 `tools/HudPackBuilder.java`，在 `target/hud-packs/` 生成 1.21.4/1.21.11 两个确定性资源包、SHA-1 旁车文件和预览 PNG。构建器生成 535 个有效可叠加位图字形（含细粒度仪表、标定框与进度），并把 `src/main/hud/audio/` 的两条定制合成音、两条 CC0 衍生警报、来源 NOTICE 与 `sounds.json` 一起封装。字体、纹理或声音改动必须同时检查 ZIP 内容、哈希和预览图。

如需重建音频，先安装带 `libvorbis` 的 FFmpeg，再执行 `powershell -ExecutionPolicy Bypass -File .\tools\build-hud-audio.ps1`。脚本会确定性合成发射/锁定反馈，核对两条 vendored CC0 警报源的 SHA-256，再完成裁剪、滤波、淡化、响度规整和高质量 OGG 编码。普通 Maven 构建直接封装已生成的 OGG，不依赖 FFmpeg。

修改 Linux 替换脚本后，在 WSL/Linux 执行 `bash tools/test-replacement-script.sh`。测试在 `/tmp` 创建隔离的伪服务器，并覆盖首次安装、重复运行、配置保留和注入故障后的精确回滚。

修改 Windows 上传器后，至少执行 `powershell.exe -NoProfile -File tools/upload-homingmissiles-3.0.0.ps1 -DryRun`，并用 Windows PowerShell 解析器检查语法。上传器、配置模板和 CMD 包装器保持 ASCII 内容，以兼容 Windows PowerShell 5 与中文工作区路径。不得提交 `tools/upload-config.properties` 或 `tools/deploy-config.properties`；只维护可公开的 `.example.properties`。

## 3. IDE 导入

以 Maven 项目打开根目录的 `pom.xml`。

IntelliJ IDEA：

1. Open 项目根目录。
2. 等待 Maven 同步。
3. Project SDK 选择 JDK 21。
4. Language level 使用 21。
5. 不要把 `stubs/` 标为生产源码。
6. `test/` 是离线脚本使用的轻量测试目录，不是 Bukkit 运行时代码。

## 4. 调试原则

### 不要异步访问 Bukkit 实体

当前制导循环运行在服务器主线程。异步线程只适合纯计算且必须与实体快照隔离。不要在异步任务中直接调用：

- `Player`
- `World`
- `Entity`
- `Location` 的世界相关操作
- Bukkit API 的大多数方法

### 不要缓存失效实体

区块卸载时，`EntitiesUnloadEvent` 会把箭从内存跟踪表挂起。不要把额外的强引用放入长期集合，否则可能造成失效引用或区块无法正确回收。

### 不要绕过统一状态入口

新增或删除箭状态时使用：

```text
addState
removeState
clearWhere
```

避免直接修改 `tracked` 而忘记维护 `shooterCounts`。

### 不要把高频反馈写入聊天

每 tick 或频繁事件应使用 Action Bar、粒子或限频日志。聊天只适合命令结果和重要错误。

## 5. 配置开发

`SettingsManager.reload()` 负责把 YAML 转换为完整的不可变 `PluginSettings`。

新增字段时需要同时维护：

- 默认配置；
- 读取逻辑；
- 安全范围；
- 运行时记录；
- 重载行为；
- 文档；
- 必要测试。

非法配置不应让插件处于半加载状态。能够修正的值应在内存中修正并记录警告；无法解析的 YAML 应保留旧配置快照。

## 6. 命令开发

新增命令必须同时提供：

- 权限节点；
- 用法；
- 帮助摘要；
- 详细帮助；
- 参数错误提示；
- Tab 补全；
- 控制台/玩家执行边界；
- 文档；
- 最接近拼写建议是否合理。

命令处理器最外层已经生成错误编号并记录堆栈。不要在子命令里吞掉无法处理的异常。

## 7. 制导算法开发

当前基本模型：

1. 读取箭当前速度。
2. 用相对位置、目标速度和当前导弹速度求截击时间，并夹紧在最小/最大预判范围。
3. 无正实数解时按最大提前量追赶，而不是退化为尾追目标当前位置。
4. 根据锁定时间和连续拉远计数决定是否不可逆地点燃后程发动机。
5. 计算期望方向并使用有限最大角速度旋转当前方向。
6. 按当前推进段的加速度更新速度大小。
7. 夹紧到当前推进段的最小/最大速度。
8. 检查向量有限性并写回箭速度。

保持“方向”和“速度大小”两个概念分离，避免简单线性叠加造成无上限加速或近距离振荡。

## 8. 性能检查

成本大致随以下因素增长：

```text
拉弓玩家数量 × 同世界玩家数量 + 在途箭数量 × 已绑定目标验证成本
```

高成本选项：

- 大首次捕获圈或锁后保持圈；
- 高全服箭数；
- 视线检测；
- 每 tick 粒子；
- 导弹尾焰、烟迹与冲击波粒子。

实机观察：

```text
/hbow status verbose
```

若平均 tick 耗时持续升高：

1. 关闭视觉效果验证是否是粒子成本；
2. 关闭视线检测；
3. 降低索敌范围；
4. 降低全服箭数上限；
5. 用 spark 分析主线程热点；
6. 避免在手动标定候选循环中分配大量临时对象。

## 9. 日志与错误编号

命令未捕获异常会生成8位错误编号。玩家可以提供编号，维护者在 `logs/latest.log` 中搜索：

```bash
grep -n "编号=XXXXXXXX" logs/latest.log
```

单箭制导异常应包含箭 UUID，并只清理该箭。

## 10. 代码风格

- Java 21。
- 类职责单一。
- 配置运行时对象优先使用 record。
- 对外返回不可变集合或副本。
- UUID 作为实体身份，不以玩家名作为持久身份。
- 公开方法注明线程预期。
- 不提交 IDE 缓存、构建输出、测试服世界和服务器日志。
- 新的数值算法必须覆盖零值、反向、非有限数和上限边界。

## 11. 推荐分支策略

```text
main        可发布状态
develop     可选的集成分支
feature/*   功能分支
fix/*       修复分支
release/*   发布准备
```

提交信息示例：

```text
feat: add configurable target priority
fix: prevent stale target after world change
docs: document Maven build and handoff workflow
test: cover zero-speed steering
```
