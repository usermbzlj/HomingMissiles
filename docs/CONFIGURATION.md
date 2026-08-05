# 配置参考

## tracking

- `range`：索敌距离。建议 24～96。
- `max-lifetime-ticks`：飞行寿命。20 tick = 1秒。
- `activation-delay-ticks`：离弦后延迟索敌，防止立即反折。
- `turn-rate-degrees-per-tick`：每tick最大转向角。越大越灵敏。
- `acceleration-per-tick`：每tick速度变化。
- `min-speed` / `max-speed`：速度下限和上限。
- `lead-ticks`：目标位置预判。
- `dynamic-retargeting`：允许切换到明显更近的目标。
- `switch-advantage-blocks`：切换目标所需距离优势，防抖。
- `require-line-of-sight`：要求无遮挡；开启会增加成本。
- `target-creative` / `target-spectator`：是否追踪对应模式。
- `respect-vanish`：尊重 `Player#canSee`，兼容常见隐身插件。
- `no-gravity`：关闭箭重力。

## limits

- `max-tracked-arrows`：全服在途上限。
- `max-tracked-per-player`：单玩家在途上限。
- `launch-cooldown-ticks`：发射冷却。
- `cancel-rejected-shot`：拒绝制导时取消射击，避免变成普通箭。

拥有 `homingmissiles.bypass.limits` 的玩家不受箭数和冷却限制。

## worlds

`disabled` 填写禁止使用制导弓的世界名称：

```yaml
worlds:
  disabled:
    - lobby
    - world_nether
```

## feedback

可选值：

- `actionbar`：默认，不刷聊天。
- `chat`：发送聊天消息。
- `off`：关闭该类文字反馈。

## lifecycle

- `remove-arrows-on-disable: true`：插件关闭时删除在途箭，最稳妥。
- `remove-arrows-on-disable: false`：保留实体和PDC状态，重新启用或区块加载时恢复。
- `recover-arrows-on-enable`：是否接管带插件持久化标记的箭。

## 性能建议

小型服：默认值通常足够。

中大型服建议：

```yaml
limits:
  max-tracked-arrows: 128
  max-tracked-per-player: 16
visual:
  particle-interval-ticks: 2
  target-marker-particles: false
```

如果 `/hbow status verbose` 中平均调度耗时持续高于约2～3ms，优先：

1. 降低全服上限。
2. 提高粒子间隔。
3. 关闭目标标记粒子。
4. 不开启视线检测。
5. 缩短索敌范围。

## 预设对应值

### balanced

```yaml
turn-rate-degrees-per-tick: 8.0
acceleration-per-tick: 0.015
min-speed: 1.0
max-speed: 2.8
lead-ticks: 4.0
```

### agile

```yaml
turn-rate-degrees-per-tick: 14.0
acceleration-per-tick: 0.025
min-speed: 1.1
max-speed: 3.2
lead-ticks: 5.0
```

### realistic

```yaml
turn-rate-degrees-per-tick: 4.5
acceleration-per-tick: 0.008
min-speed: 0.9
max-speed: 2.4
lead-ticks: 6.0
```
