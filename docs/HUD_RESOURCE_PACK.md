# 像素 HMD 资源包部署

HomingMissiles 的主 HUD 是资源包 PNG 绘制的飞机头戴显示器，不是聊天、标题或字符画。服务器只需要 Paper 系插件；玩家不需要安装 Fabric、Forge 或额外客户端模组，但必须允许服务器资源包。

## 选择文件

执行 `mvn clean package` 后会生成：

| 服务端/客户端版本 | 文件 |
|---|---|
| 1.21.4 | `target/hud-packs/HomingMissiles-HUD-1.21.4.zip` |
| 1.21.11 | `target/hud-packs/HomingMissiles-HUD-1.21.11.zip` |

本次确定性构建的 SHA-1：

- 1.21.4：`72f2934100fba4ab78ef7b5c13a721d2032be777`
- 1.21.11：`edcfb113244acb76099699c831fff495874ab909`

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
    sha1: 'edcfb113244acb76099699c831fff495874ab909'
    required: false
    prompt: '&b启用像素化导弹头显'
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

## 验收

1. 发射者看到青色中央准星和最多 4 个挂点占用，不出现目标名、距离、方向或速度。
2. 被锁玩家看到来自八个离散方向之一的黄/橙/红威胁图形。
3. 同时是射手和被锁目标时，来袭警告优先，避免重要威胁被发射状态遮住。
4. 导弹消失后 ActionBar 图形清空。
5. 拒绝资源包的测试客户端得到 BossBar 降级界面，战斗逻辑不受影响。

## 常见故障

- **一直是 BossBar**：检查 `url` 是否为空、客户端是否接受、服务器日志是否记录资源包失败，以及 SHA-1 是否与 ZIP 一致。
- **出现方框**：资源包中缺少 `assets/homingmissiles/font/hud.json` 或纹理，或错误启用了 `assume-server-pack-provides-hud`。
- **下载失败**：用另一台网络环境中的浏览器直接访问 URL；确认没有鉴权、跳转到 HTML、证书错误或防盗链。
- **更新后仍显示旧图**：重新计算 SHA-1 并更新配置，或更换 URL 文件名，然后重新连接。
