# Fabric 客户端 HUD Mod

`HomingMissiles-HUD-Fabric` 是可选的纯客户端 Mod。服务端仍然只安装 Paper 插件；玩家不安装 Mod 也能通过资源包或 BossBar 使用全部武器功能。

## 为什么推荐安装

- 每个渲染帧都从当前 `GuiGraphics.guiWidth()/guiHeight()` 重新计算屏幕中心，HUD 光学原点与原版准星使用同一个整数中心。
- HUD 在整个鞘翅飞行期间显示，不要求玩家正在拉弓。
- 航向、俯仰、速度、高度、离地高度、飞行矢量、目标框、锁定进度和显隐全部连续插值，没有整幅贴图硬切。
- 按 `H` 或执行 `/hbow hud` 可以开关完整飞行 HUD；手动锁定框和锁定进度始终保留。
- Mod 内置与服务端资源包相同的四个合法音频资源，不需要再次接受 HUD 资源包。

## 安装

客户端需要：

- Minecraft Java Edition `1.21.11`
- Fabric Loader `0.19.3` 或更新兼容版本
- Fabric API `0.141.6+1.21.11` 或更新兼容版本
- `HomingMissiles-HUD-Fabric-1.21.11-3.1.0.jar`

把 Fabric API 和本 Mod JAR 放入客户端 `.minecraft/mods/`。进入安装了 HomingMissiles 3.1.0 插件的服务器后，Mod 会通过 `homingmissiles:control` 主动握手；服务端检测成功后不会再发送 HUD 资源包，并在聊天中确认“逐帧精确居中模式”。

服务端无须安装 Fabric，也不要把客户端 Mod JAR 放进 `plugins/`。

## 构建

```powershell
cd client-mod
.\gradlew.bat build
```

正式客户端产物：

```text
client-mod/build/libs/HomingMissiles-HUD-Fabric-1.21.11-3.1.0.jar
```

不应发给玩家带 `-sources` 后缀的源码 JAR。

## 自动检测协议

- C2S：`homingmissiles:control`，协议版本 `1`，用于握手和 HUD 开关同步。
- S2C：`homingmissiles:hud_state`，协议版本 `1`，每 tick 发送锁定、挂点和来袭的最小状态。
- 飞行姿态与速度由客户端逐帧读取本地玩家数据；服务器不下发目标身份、名称、距离或速度。
- 登录后 `hud.client-mod.detection-grace-ticks` 内没有握手，插件才启动纯服务端资源包路径并推荐下载 Mod。

## 故障排查

- 没有出现 Mod 检测成功消息：确认客户端确实使用 Fabric 配置启动，并同时安装 Fabric API。
- H 键无效：在“选项 → 控制”中搜索 `HomingMissiles HUD`，检查按键冲突；也可以用 `/hbow hud toggle`。
- HUD 约两秒后消失：说明服务端状态包中断；检查服务端插件版本、代理是否允许自定义插件消息以及日志异常。
- 只看到锁定进度：这是完整 HUD 被关闭时的预期行为；执行 `/hbow hud on`。

## FlightHud 参考边界

本实现核对了 FlightHud 的公开源码行为，特别是以 GUI 缩放宽高一半作为中心、按 `isFallFlying()` 选择飞行显示态以及独立显示模式的设计。渲染器、协议、图形与插值代码均在本项目中重新实现，没有复制 FlightHud 的 Java、纹理或其他 GPL 资产。
