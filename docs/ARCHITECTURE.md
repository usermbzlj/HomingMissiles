# 系统架构

## 1. 目标

HomingMissiles 的核心目标是：

- 多玩家、多箭独立制导；
- 只排除每支箭自己的射手；
- 不传送箭，保留真实碰撞；
- 配置可热重载且不产生半有效状态；
- 单箭异常不影响全局调度；
- 区块卸载和服务器生命周期可恢复或确定性清理；
- 管理命令可诊断运行状态。

## 2. 组件图

```text
HomingMissilesPlugin
├─ SettingsManager ──> PluginSettings
├─ MessageService
├─ HomingBowFactory
├─ HomingService ──> TrackedArrow / VectorMath
├─ HomingListener ──> HomingService
└─ HomingBowCommand ──> SettingsManager / HomingService / HomingBowFactory
```

`HomingMissilesPlugin` 是组合根。其他组件不应通过静态全局单例重新定位服务。

## 3. 状态所有权

### 内存状态

`HomingService` 拥有：

- `tracked`：箭 UUID → `TrackedArrow`
- `shooterCounts`：射手 UUID → 在途箭数量
- `lastLaunchTick`：射手 UUID → 最近发射服务 tick
- 调度性能指标
- 当前插件会话 ID

### 实体持久化状态

箭 PDC 保存：

| 键 | 类型 | 意义 |
|---|---|---|
| `homing_projectile` | BYTE | 该箭由插件管理 |
| `shooter_uuid` | STRING | 实际射手 UUID |
| `age_ticks` | INTEGER | 已飞行年龄 |
| `session_id` | STRING | 创建/接管该箭的插件会话 |

弓 PDC 保存：

| 键 | 类型 | 意义 |
|---|---|---|
| `homing_bow` | BYTE | 该物品是制导弓 |

这些键是存档兼容面。修改必须提供迁移。

## 4. 调度模型

`HomingService.start()` 创建同步重复任务。所有实体访问发生在主线程。

每 tick：

```text
safeTick
  ├─ 记录开始时间
  ├─ 对 tracked 创建稳定迭代视图
  ├─ processArrow(state)
  │   ├─ 验证箭实体
  │   ├─ 增加年龄并检查寿命
  │   ├─ 选取目标
  │   ├─ steerArrow
  │   ├─ notifyLockIfNeeded
  │   └─ spawn effects
  ├─ 单箭异常隔离
  └─ 更新诊断指标
```

不要让一个箭异常传播到调度任务边界，否则 Bukkit 会停止后续任务执行或持续刷异常。

## 5. 目标选择

候选目标必须满足：

- 在线且有效；
- 与箭同一世界；
- 不是射手；
- 没有 `homingmissiles.target.exempt`；
- 游戏模式符合配置；
- 在索敌范围内；
- 若开启 vanish 尊重，则射手可见目标；
- 若开启视线要求，则视线无遮挡。

动态重选不是每次无条件切换。新目标需要满足 `switch-advantage-blocks` 的距离优势，避免两名玩家距离接近时频繁抖动。

## 6. 飞行数学

设：

- 当前速度方向为 `v`;
- 期望目标方向为 `d`;
- 每 tick 最大转角为 `θ`;
- 当前速度大小为 `s`;
- 加速度为 `a`.

处理过程：

```text
newDirection = rotateTowards(v, d, θ)
newSpeed = clamp(s + a, minSpeed, maxSpeed)
newVelocity = newDirection × newSpeed
```

`VectorMath.rotateTowards` 使用轴角旋转，并处理：

- 当前方向为零；
- 目标方向为零；
- 完全同向；
- 完全反向；
- 浮点误差导致的 dot 超界；
- 非有限向量。

## 7. 配置一致性

`PluginSettings` 是不可变 record。服务每次读取 `SettingsManager.current()`，不会持有半解析 YAML。

重载流程：

```text
reloadConfig
→ 读取全部字段
→ 范围校验和交叉字段校验
→ 构造完整 PluginSettings
→ 原子替换 current
→ 返回警告列表
```

在线调参写回 YAML 后重新走同一加载路径，避免命令调参与手工配置产生两套规则。

## 8. 生命周期

### 启动

- 创建服务；
- 注册事件和命令；
- 启动 tick；
- 扫描已加载世界并恢复带 PDC 标记的箭。

### 区块卸载

- 从内存表移除；
- 保留实体 PDC；
- 维护射手计数；
- 等待区块重新加载。

### 区块加载

- 验证 PDC；
- 校验射手 UUID、寿命和限制；
- 恢复到内存表。

### 插件关闭

- 停止调度任务；
- 按 `remove-arrows-on-disable` 删除或保留在途箭；
- 清空内存状态。

## 9. 命令架构

`HomingBowCommand` 同时实现 `CommandExecutor` 与 `TabCompleter`。

入口行为：

- 生成错误参考号；
- 分发子命令；
- 权限检查；
- 用法和数值校验；
- 对未知命令给出编辑距离建议；
- 帮助和 Tab 只展示当前发送者可见项。

新增命令时，路由、帮助、补全、权限和文档必须一起更新。

## 10. 未来重构建议

当项目继续增长时，可优先考虑：

1. 将 `HomingBowCommand` 按子命令拆成命令对象。
2. 将 `HomingService` 拆为目标选择器、制导器、持久化仓库和诊断器。
3. 使用标准 `src/test/java` 与 JUnit 运行更完整的单元测试。
4. 增加 MockBukkit 或真实 Paper 集成测试。
5. 建立稳定事件/API 层供其他插件调用。
6. 为 Folia 建立独立调度适配层，而不是在当前同步服务上打补丁。
