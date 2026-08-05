# 配置参考

当前配置版本为 `5`。执行 `/hbow reload` 会校验并原子替换内存设置，不会覆盖原文件。

## tracking

- `range`：首次捕获距离，默认 `80`。导弹只在这个近圈内建立新锁定。
- `lock-retention-range`：锁定保持距离，默认 `192`，且不能小于 `range`。已锁目标即使飞出捕获圈，仍会在保持圈内被追踪。
- `max-lifetime-ticks`：飞行寿命。20 tick = 1秒。
- `activation-delay-ticks`：离弦后延迟索敌，防止立即反折。
- `turn-rate-degrees-per-tick`：每 tick 最大转向角。越大越灵敏。
- `acceleration-per-tick`：巡航段每 tick 速度变化。
- `min-speed` / `max-speed`：巡航速度下限和上限，默认 `1.2` / `3.4` 格/tick。
- `lead-ticks`：动态截击解的最小预判时间。
- `max-lead-ticks`：动态截击解的最大预判时间；目标当前速度高于导弹、没有实数截击解时，会向这个上限提前瞄准。
- `terminal-boost.delay-ticks`：持续锁定达到该时长后点燃后程发动机。
- `terminal-boost.escape-trigger-ticks`：目标连续拉远达到该时长后提前点火。
- `terminal-boost.acceleration-per-tick` / `max-speed`：后程加速度与最高速度，默认 `0.075` / `5.6` 格/tick。
- `dynamic-retargeting`：允许切换到明显更近的目标。
- `switch-advantage-blocks`：切换目标所需距离优势，防抖。
- `require-line-of-sight`：要求无遮挡；开启会增加成本。
- `target-creative` / `target-spectator`：是否追踪对应模式。
- `respect-vanish`：尊重 `Player#canSee`，兼容常见隐身插件。
- `no-gravity`：关闭箭重力。

## limits

- `max-tracked-arrows`：全服在途上限，默认 128。
- `max-tracked-per-player`：单玩家在途上限，默认且硬上限为 4；可以设得更低，不能设得更高。
- `launch-cooldown-ticks`：发射冷却。
- `cancel-rejected-shot`：拒绝制导时取消射击，避免变成普通箭。

`homingmissiles.bypass.limits` 只绕过全服上限和冷却，不绕过每人 4 枚的硬上限。

## combat

- `minimum-arrow-damage`：默认 `12.0`，将导弹改成高伤害武器。如果原版/附魔已算出更高伤害则保留更高值；设为 `-1` 可完全保留原版伤害。

## item

`item.name` 和 `item.lore` 控制新发放弓的外观。`item.enchantments` 仅影响之后由 `/hbow get` 或 `/hbow give` 新建的物品，不会在重载时静默修改已有弓。

```yaml
item:
  enchantments:
    flame: true
    infinity: true
    unbreakable: true
    power-level: 5
```

- `flame`：火矢 I。
- `infinity`：无限 I；玩家仍需按原版规则持有至少一支箭。
- `unbreakable`：耐久 III，并写入真正的“不可损坏”物品标记。
- `power-level`：力量等级，`0` 关闭，最大 `5`。

## visual / effects / audio

- `visual.particles`：导弹特效总开关。
- `visual.particle-interval-ticks`：飞行尾迹刷新间隔。
- `visual.glowing-arrow`：是否让箭实体发光。
- `effects.launch`：点火闪光、尾焰和起飞烟团。
- `effects.impact`：冲击波、爆闪、火焰、碎屑和重烟。
- `effects.self-destruct`：燃尽闪光、云团和远距熄火声。
- `audio.launch-sound`：火箭与弩机组合发射声。
- `audio.lock-sounds`：锁定建立/来袭建立的分层提示声。

特效不再在目标头顶生成电火花标记；视觉信息集中在真实导弹实体和弹道上。警报也不再使用音符盒蜂鸣。

## feedback

一次性发射、锁定、拒绝反馈可选：

- `actionbar`：默认，不刷聊天。
- `chat`：发送聊天消息。
- `off`：关闭该类文字反馈。

射手首锁使用新的 `messages.guidance-link`，目标告警使用 `messages.inbound-warning`。代码不会给射手消息模板传入目标名、距离、方向或速度；旧版含遥测占位符的消息键不会再被调用。`/hbow inspect` 自检同样只显示通道占用。

## hud

HUD 的主路径是项目自带资源包中的 `homingmissiles:hud` 位图字体：插件向 ActionBar 发送一个私有字形，客户端用透明 PNG 绘制像素化头戴显示器。它不是由文本字符拼出的假仪表，也不要求安装 Fabric/Forge 客户端模组。资源包没有就绪时才使用 BossBar 降级。

- `enabled`：HUD 与来袭警报总开关。
- `pixel-overlay`：资源包就绪时启用像素 HMD。
- `shooter-bossbar` / `target-bossbar`：资源包未加载时是否显示降级仪表。
- `resource-pack.url`：玩家客户端可直接访问的 HUD ZIP HTTP(S) 地址。
- `resource-pack.sha1`：对应 ZIP 的 40 位 SHA-1，用于完整性校验和客户端缓存。
- `resource-pack.required` / `prompt`：是否强制加载以及确认提示。
- `resource-pack.assume-server-pack-provides-hud`：若已经把 `assets/homingmissiles` 合并进全服资源包，则设为 `true`，插件不再单独发送 ZIP。
- `warning-audio`：从最接近导弹方向播放空间化幽匿/心跳组合音。
- `warning-min-interval-ticks` / `warning-max-interval-ticks`：终端阶段与远距阶段的警报间隔。

射手只得到青色准星和 4 个挂点占用；目标身份、距离、方向、速度以及精确锁定参数都不会发给射手。被追踪者得到八个离散方向和远/中/近三级威胁图形。完整部署见 [HUD_RESOURCE_PACK.md](HUD_RESOURCE_PACK.md)。

## worlds

`disabled` 填写禁止使用制导弓的世界名称：

```yaml
worlds:
  disabled:
    - lobby
    - world_nether
```

## lifecycle

- `remove-arrows-on-disable: true`：插件关闭时删除在途箭，最稳妥。
- `remove-arrows-on-disable: false`：保留实体和 PDC 状态，重新启用或区块加载时恢复。
- `recover-arrows-on-enable`：是否接管带插件持久化标记的箭。

## 性能建议

默认每人最多 4 枚已显著降低密集弹幕成本。中大型服可以进一步：

```yaml
limits:
  max-tracked-arrows: 64
  max-tracked-per-player: 4
visual:
  particle-interval-ticks: 2
```

如果 `/hbow status verbose` 中平均调度耗时持续高于约 2～3ms，优先降低全服上限、提高粒子间隔、关闭视线检测或缩短索敌范围。

## 预设对应值

### balanced

```yaml
turn-rate-degrees-per-tick: 8.0
acceleration-per-tick: 0.025
min-speed: 1.2
max-speed: 3.4
terminal-boost:
  acceleration-per-tick: 0.075
  max-speed: 5.6
lead-ticks: 4.0
max-lead-ticks: 24.0
```

### agile

```yaml
turn-rate-degrees-per-tick: 14.0
acceleration-per-tick: 0.025
min-speed: 1.3
max-speed: 3.8
terminal-boost:
  acceleration-per-tick: 0.1
  max-speed: 6.2
lead-ticks: 5.0
max-lead-ticks: 28.0
```

### realistic

```yaml
turn-rate-degrees-per-tick: 4.5
acceleration-per-tick: 0.018
min-speed: 1.0
max-speed: 3.0
terminal-boost:
  acceleration-per-tick: 0.06
  max-speed: 5.0
lead-ticks: 6.0
max-lead-ticks: 30.0
```
