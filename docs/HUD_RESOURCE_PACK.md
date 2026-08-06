# 像素 HMD 资源包部署

HomingMissiles 的主 HUD 是参考 [VulpesStella/FlightHud](https://github.com/VulpesStella/FlightHud) 的飞行仪表布局、从零绘制的动态分层头戴显示器，不是聊天、标题或字符画。服务器只需要 Paper 系插件；玩家不需要安装 Fabric、Forge 或额外客户端模组，但必须允许服务器资源包。

一帧由多个同尺寸透明 PNG 字形通过负间距叠加而成。插件先对飞行遥测和目标屏幕坐标逐 tick 插值，再组合基础框架、72 档航向滑尺、25 档俯仰梯/人工地平线、81 档速度、97 档高度、65 档离地高度、49 区飞行矢量、98 个标定框状态、18 个标定进度状态、5 档挂点和 24 种来袭状态，共 535 个有效图层字形。单个元素只跨一个小档位过渡，不再切换整幅 HUD 贴图。

拉住制导弓时，黄色标定框跟随视锥内目标移动，进度条从 `SCAN` 进入 `ACQ`；持续保持到配置时长后，框体转绿并显示 `LOCK`。未安装/未接受资源包时，同一状态机降级为分段 BossBar，发射门禁不受 HUD 是否可见影响。

## 选择文件

执行 `mvn clean package` 后会生成：

| 服务端/客户端版本 | 文件 |
|---|---|
| 1.21.4 | `target/hud-packs/HomingMissiles-HUD-1.21.4.zip` |
| 1.21.11 | `target/hud-packs/HomingMissiles-HUD-1.21.11.zip` |

本次确定性构建的 SHA-1：

- 1.21.4：`5a82391f670622fecacd4c66ce8fdf13ab6c669d`
- 1.21.11：`ed91117f45ee420121a5f1d973380e369244a93b`

R4700G3 的 Leaf 1.21.11 应使用第二个文件和第二个校验值。每个 ZIP 旁边的 `.sha1` 文件也保存了配置所需的值；`HomingMissiles-HUD-preview.png` 用于人工验收，不应发给客户端。

## 独立托管

把 ZIP 上传到已有网站、对象存储或静态文件服务器。下载地址必须：

- 是客户端能直接访问的 `http://` 或 `https://` URL；
- 返回 ZIP 本体，而不是登录页、网盘预览页或 HTML；
- 修改文件后同步修改 URL 或 SHA-1，避免客户端继续使用旧缓存。

在 `plugins/HomingMissiles/config.yml` 中填写：

```yaml
hud:
  enabled: true
  pixel-overlay: true
  shooter-bossbar: true
  target-bossbar: true
  resource-pack:
    url: 'https://example.com/minecraft/HomingMissiles-HUD-1.21.11.zip'
    sha1: 'ed91117f45ee420121a5f1d973380e369244a93b'
    required: false
    prompt: '&b启用动态飞行导弹头显与座舱音效'
    assume-server-pack-provides-hud: false
```

执行 `/hbow reload` 后，让测试玩家重新进入服务器并接受资源包。插件会监听加载结果；成功后立即使用像素 HMD，拒绝、下载失败或校验失败时只使用 BossBar 降级界面。

生产服建议先保持 `required: false` 验证下载链路。确认 URL、SHA-1、反向代理和客户端版本均正常后，如果服务器策略要求所有玩家获得同一界面，再改为 `true`。

## 合并到已有全服资源包

若 `server.properties` 已经发送一个全服资源包，不应再建立相互覆盖的第二套字体资源。把 HUD ZIP 内的 `assets/homingmissiles/` 原样合并进现有资源包，保持 `homingmissiles:hud` 字体路径不变，然后设置：

```yaml
hud:
  resource-pack:
    url: ''
    sha1: ''
    assume-server-pack-provides-hud: true
```

这个开关表示插件相信当前全服包已经包含 HUD；若实际没有合并，客户端会显示缺字方框。

## 插件内置托管（可选）

没有现成 HTTP 文件服务时，可以把 Leaf 1.21.11 ZIP 放到
`plugins/HomingMissiles/hud/HomingMissiles-HUD-1.21.11.zip`，并启用内置的最小 HTTP 端点：

```yaml
hud:
  resource-pack:
    url: 'http://你的公网主机:25568/homingmissiles/hud-1.21.11.zip'
    sha1: 'ed91117f45ee420121a5f1d973380e369244a93b'
    self-host:
      enabled: true
      bind-address: '0.0.0.0'
      port: 25568
      path: '/homingmissiles/hud-1.21.11.zip'
```

该服务只暴露配置中的单一 ZIP 路径，只接受 GET/HEAD；启动前会把 ZIP 读入内存并核对 SHA-1。
它提供普通 HTTP，不负责 TLS、域名、端口映射或防火墙。务必从服务器外部实际下载并复核 SHA-1，才能确认玩家客户端可达。

## 验收

1. 转动视角时顶部航向带按 15° 档位移动，N/E/S/W 与三位航向值一致。
2. 抬头/低头时俯仰梯和人工地平线移动；玩家速度方向与视线不一致时，飞行矢量偏离中央准星。
3. 左侧速度、右侧绝对高度和右下离地高度会随玩家运动更新。
4. 发射者看到最多 4 个挂点占用，但不出现目标名、距离、方向或速度。
5. 被锁玩家看到来自八个离散方向之一的黄/橙/红威胁图形；同时作为射手时，挂点层与来袭层会共同显示。
6. 资源包就绪时，发射和锁定确认使用包内定制合成音，普通/临界来袭使用 CC0 衍生警报；未就绪时自动回退到原版 Minecraft 音效。
7. 导弹消失后 ActionBar 图形清空；拒绝资源包的测试客户端得到 BossBar 降级界面，战斗逻辑不受影响。

## 参考与音频版权边界

- FlightHud 仓库采用 GPL-3.0。本项目只参考其公开说明中的仪表构成和布局语言，没有复制该仓库的 SVG、GIF、PNG 或程序代码；本项目图形由 `tools/HudPackBuilder.java` 从零生成。
- 已检查 Bandai Namco 的 [*Ace Combat 7* 官方页面](https://en.bandainamcoent.eu/ace-combat/ace-combat-7-skies-unknown)、[官方 press assets](https://en.bandainamcoent.eu/press/5035) 与[系列原声公告](https://www.bandainamcoent.com/news/ace-combat-30th-anniversary)。官方提供的是宣传素材、商品/游戏内音乐播放器与商业原声入口，没有找到允许把游戏音效重新分发到 Minecraft 资源包的授权，因此没有下载、提取或打包任何 *Ace Combat 7* 音频。
- 发射声由弓弦/机械瞬态、低频点火体和滤波噪声尾流组成；锁定确认由四级上行的打击型谐波音与短和声音尾组成。两者均由 `tools/build-hud-audio.ps1` 确定性合成，不复制 Minecraft 原音频。
- 普通/临界来袭仍使用 Joth 与 yd 的 CC0 素材；其源文件、SHA-256、来源链接与完整 CC0 文本保存在 `src/main/hud/third-party/`。全部四个 OGG 均可用构建脚本和 FFmpeg 重建。
- 每个 HUD ZIP 都携带 `assets/homingmissiles/sounds/NOTICE.txt`，即使资源包脱离源码仓库单独分发，也不会丢失作者、来源和许可证说明。

## 常见故障

- **一直是 BossBar**：检查 `url` 是否为空、客户端是否接受、服务器日志是否记录资源包失败，以及 SHA-1 是否与 ZIP 一致。
- **出现方框**：资源包中缺少 `assets/homingmissiles/font/hud.json` 或纹理，或错误启用了 `assume-server-pack-provides-hud`。
- **图层横向错开**：确认没有旧版 `hud.json` 覆盖新包；新版字体必须包含 `U+E0FF` 的 `-193` 负间距 provider。
- **只有原版声音**：确认资源包成功加载，且 `assets/homingmissiles/sounds.json` 与四个 `sounds/hud/*.ogg` 均存在。
- **下载失败**：用另一台网络环境中的浏览器直接访问 URL；确认没有鉴权、跳转到 HTML、证书错误或防盗链。
- **更新后仍显示旧图**：重新计算 SHA-1 并更新配置，或更换 URL 文件名，然后重新连接。
