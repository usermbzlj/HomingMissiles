# Contributing

感谢参与 HomingMissiles 开发。

## 提交前

1. 基于最新可发布分支创建功能分支。
2. 阅读 `README.md`、`docs/DEVELOPMENT.md` 和 `docs/ARCHITECTURE.md`。
3. 不改变既有命令、权限、配置或 PDC 协议，除非变更说明明确要求。
4. 为数值算法和命令工具增加测试。
5. 同步更新用户文档。

## 必须通过

```bash
bash tools/verify-offline.sh
mvn clean package
```

并在 Paper 系测试服完成相关实机场景。

## Pull Request 应包含

- 问题背景；
- 行为变化；
- 配置/权限变化；
- 兼容性影响；
- 测试步骤和结果；
- 性能影响；
- 回滚方式。

## 代码审查重点

- 是否在主线程访问 Bukkit 实体；
- 是否破坏每箭独立射手身份；
- 是否维护 `tracked` 与 `shooterCounts` 一致；
- 是否可能写入 NaN/Infinity 速度；
- 是否造成高频聊天或日志；
- 是否有无界集合或实体强引用；
- 是否修改 PDC 键；
- 是否同时更新帮助、Tab 补全和权限；
- 是否能安全处理无效配置与实体卸载。

## 安全报告

不要在公开 issue 中发布可直接利用的服务器安全漏洞细节。先私下联系维护者，并提供：

- 受影响版本；
- 复现步骤；
- 日志或最小复现；
- 影响范围；
- 建议修复。
