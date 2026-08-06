# 命令与权限

根命令：`/homingbow`  
别名：`/hbow`、`/制导弓`

## 玩家面板

### `/hbow`

显示主手是否为有效制导弓、自己的在途箭数量和常用命令入口。

## 帮助

### `/hbow help [页码|子命令]`

- `/hbow help`：第1页。
- `/hbow help 2`：第2页。
- `/hbow help tune`：查看在线调参的完整说明。
- 只显示发送者有权限使用的命令。

权限：`homingmissiles.command.help`

## 领取与发放

### `/hbow get [数量]`

给自己领取制导弓。背包满时安全掉落在脚下。

权限：`homingmissiles.command.get`

### `/hbow give <玩家> [数量]`

向在线玩家发放制导弓。兼容1.0.0命令形式。

权限：`homingmissiles.command.give`

数量上限：`commands.max-give-amount`

## 状态与诊断

### `/hbow status`

显示全服/个人在途箭数、索敌范围、转向角和速度区间。

权限：`homingmissiles.command.status`

### `/hbow status verbose`

额外显示：

- 当前tick处理箭数
- 平均和峰值调度耗时
- 被隔离的单箭异常数量
- 服务运行tick
- 活跃射手排名
- 禁用世界

权限：`homingmissiles.command.status.verbose`

### `/hbow inspect [玩家]`

显示：

- 主手是否为有效制导弓
- 在途箭数量
- 自检时不会显示目标、距离、方向、速度或世界等遥测
- 管理员检查其他玩家时才显示每支箭的诊断细节

检查自己：`homingmissiles.command.inspect.self`  
检查他人：`homingmissiles.command.inspect.others`

## 清场

### `/hbow clear mine`

删除自己的全部在途制导箭。`/hbow clear` 和 `/hbow cancel` 默认等价于此命令。

权限：`homingmissiles.command.clear.own`

### `/hbow clear all`

删除全服全部在途制导箭。

### `/hbow clear player <玩家>`

删除某个在线玩家发射的全部在途箭。

### `/hbow clear world <世界>`

删除指定世界中的全部在途箭。

以上三个管理范围权限：`homingmissiles.command.clear.admin`

## 在线调参

### `/hbow tune`

显示所有可在线修改的参数、当前值、允许范围和意义。

### `/hbow tune <参数> <数值>`

修改参数，立即写入 `config.yml` 并生效。只允许白名单参数，并执行严格上下界校验。

可调参数：

- `range`
- `retention-range`
- `lifetime`
- `delay`
- `turn`
- `acceleration`
- `min-speed`
- `max-speed`
- `terminal-acceleration`
- `terminal-max-speed`
- `lead`
- `max-lead`
- `switch-advantage`
- `lock-time`
- `lock-cone`

### `/hbow tune reset <参数>`

重置一个参数。

### `/hbow tune reset all`

重置全部在线可调参数。

权限：`homingmissiles.command.tune`

## 预设

### `/hbow preset balanced`

均衡默认参数。

### `/hbow preset agile`

更快转向、更高速度，适合近距离高机动玩法。

### `/hbow preset realistic`

转向更慢、预判更强，具有更明显的转弯半径。

权限：`homingmissiles.command.preset`

## 配置重载

### `/hbow reload`

重新读取配置。加载过程会先完整构建新配置对象；若YAML解析异常，旧配置继续保留。越界值会使用安全修正并逐条显示警告。

权限：`homingmissiles.command.reload`

## 版本

### `/hbow version`

别名：`/hbow about`

权限：`homingmissiles.command.version`

## 功能权限

| 权限 | 默认 | 意义 |
|---|---:|---|
| `homingmissiles.use` | 所有人 | 使用制导弓 |
| `homingmissiles.target.exempt` | 无 | 不会被选为目标 |
| `homingmissiles.bypass.limits` | OP | 绕过全服上限和冷却；每人 4 枚硬上限不可绕过 |
| `homingmissiles.admin` | OP | 继承主要管理权限 |
