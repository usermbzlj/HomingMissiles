# Changelog

## 3.1.3 — 2026-08-07

- 修正手动锁定框的水平投影符号：目标在屏幕左侧时锁定框现在向左移动，目标在右侧时向右移动。
- 纯服务端 Title HUD 与 Fabric 客户端 HUD 共用同一修正后的投影结果，并加入朝南/朝北观察东西两侧目标的回归测试。

## 3.1.2 — 2026-08-07

- 在纯服务端 Title HUD 的 Adventure 组件上显式应用 `ShadowColor.none()`，关闭原版 Title 文字阴影，消除整套位图字形以固定偏移重复绘制造成的重影。
- 客户端 Fabric HUD 渲染路径和手动锁定逻辑保持不变。

## 3.1.1 — 2026-08-07

- 修正客户端 Mod 过度严格的 Fabric Loader 依赖声明：从错误的 `>=0.19.3` 降为 Fabric API 实际要求的 `>=0.17.3`。
- 确认 HomingMissiles HUD 不直接依赖 Loader 实现 API，并对 `0.17.3`、`0.18.4`、`0.19.3` 建立兼容构建矩阵。
- 现有 Loader `0.18.4` 用户无需升级到最新版即可加载 Mod；Minecraft `1.21.11`、Java 21 与 Fabric API `0.141.6+1.21.11` 要求不变。

## 3.1.0 — 2026-08-07

### Dual-path centered flight HUD

- Added an optional Fabric 1.21.11 client Mod with per-frame vector rendering anchored to the exact scaled-GUI midpoint used by the crosshair.
- Extended the full flight HUD across the entire Elytra flight instead of only bow draw/active missile states.
- Added continuous interpolation and fade transitions for telemetry, flight-path marker, acquisition box, lock progress and visibility.
- Added versioned plugin-message handshake/state channels; the Paper plugin detects the Mod and suppresses its resource-pack prompt automatically.
- Delayed pure-server fallback until the configurable handshake grace expires, then recommends the Mod and uses a centered Title bitmap font or BossBar fallback.
- Added persistent `/hbow hud on|off|toggle|status` and a synchronized client `H` key. Disabling the full HUD never hides mandatory manual-lock progress.
- Bundled the legal project audio set in both the resource pack and client Mod.
- Bumped configuration to version 7 with `hud.client-mod.download-url` and `detection-grace-ticks`.

## 3.0.0 — 2026-08-06

### Combat model

- Added a mandatory draw-to-lock gate: the shooter must hold an eligible player inside the configurable acquisition cone before releasing; an unlocked release is always cancelled.
- Bound every missile to the manually selected target UUID, persisted that UUID across chunk/server recovery, and removed post-launch automatic retargeting.
- Changed the default from dense salvos to high damage: 12 minimum arrow damage and a strict four-missile per-player in-flight cap.
- The four-missile cap cannot be bypassed by administrator permissions.
- Added independently configurable Flame, Infinity, Unbreaking/unbreakable and Power enchantments; all are enabled by default on newly issued bows.

### Native HUD and telemetry privacy

- Rebuilt the HUD around a FlightHud-inspired layered flight-instrument layout: heading tape, pitch ladder/artificial horizon, prograde vector, speed, altitude, height-above-ground, hardpoints and incoming-threat overlays.
- Expanded the deterministic bitmap atlas from 28 monolithic states to 535 effective composable layers, including 49-position acquisition/lock boxes and 18 progress states.
- Added per-tick interpolation for flight telemetry and target-screen coordinates, while reducing heading/pitch/speed/altitude bucket steps to eliminate abrupt full-frame texture changes.
- Rebuilt launch and lock confirmation as deterministic 48 kHz synthesis tailored to Minecraft's compact, dry feedback palette: launch layers a string/mechanical snap, low ignition body and filtered exhaust; lock uses four ascending percussive harmonic notes with a short resolved tail.
- Kept the Joth/yd CC0 incoming-warning clips, removed the rejected external launch/lock sources, and documented the complete FFmpeg synthesis recipe for reproducible tuning.
- No audio or visual assets were extracted from Ace Combat 7; clients without the pack retain the vanilla-sound fallback.
- Added an optional in-process HUD pack HTTP endpoint with an exact path, GET/HEAD allowlist, startup SHA-1 verification and an immutable in-memory payload.
- Shooter HMD exposes only a reticle and four outbound hardpoints; target identity, distance, direction and speed remain private.
- Target HMD exposes eight coarse directions and three urgency bands. BossBars remain only as an unavailable/declined-pack fallback.
- `/hbow inspect` self-service no longer leaks target telemetry.

### Long-range interceptor guidance

- Split initial acquisition (`80` blocks) from post-lock retention (`192` blocks), preventing distant accidental captures while preserving a real long-range lock.
- Added analytical intercept-time prediction with bounded fallback lead when an Elytra target temporarily outruns the missile.
- Added an irreversible terminal motor: it ignites after sustained lock or repeated range opening, with independently configurable acceleration and maximum speed.

### Effects and audio

- Rebuilt launch, powered-flight, lock, impact and self-destruct effects around an exhaust flame, white-smoke wake, lock flash, shockwave and layered blast.
- Fixed Paper/Leaf 1.21.11 `FLASH` particles by supplying their required `Color` data; effect API failures now disable only the affected stage instead of deleting missiles or interrupting bow events.
- Preserved legacy boolean feedback settings (`false`/`true`) as aliases for `off`/the default mode during 3.0 configuration upgrades.
- Removed target-head marker particles.
- Replaced note-block beeps with layered firework/crossbow/beacon/sculk/warden cues and spatialized the recurring warning toward the nearest threat.
- Bumped configuration to version 6.

### Release engineering

- Promoted the plugin release version to 3.0.0 and enabled timestamp-stable Maven JAR output.
- Added a root-oriented Linux replacement tool with a pinned release hash, duplicate detection, config preservation, atomic installation, startup verification and automatic JAR rollback.
- Added an auditable `SHA256SUMS.txt` for the release artifact.
- Maven packaging now builds both HUD resource-pack variants and a visual preview without external image tooling.

## 2.0.0+hud — 2026-08-06

### Combat feedback

- Added Ace Combat–style continuous lock HUD (`config-version: 3`).
- Shooter ActionBar: `TRACK` with target, distance, cardinal direction.
- Locked target ActionBar: `MISSILE` warning with distance, direction, shooter.
- Continuous target warning beep; interval shortens as the missile closes in.
- New `hud.*` settings and `messages.hud-shooter` / `messages.hud-target`.
- Guidance, targeting, and launch flow unchanged.

## 2.0.0 Documentation Revision — 2026-08-06

### Developer handoff

- Expanded README with exact JDK/Maven requirements and source build commands.
- Documented formal Maven build versus offline stub verification.
- Added architecture, development workflow, release checklist, contribution guide and Windows verification script.
- Added extension recipes, runtime invariants, manual smoke-test matrix and compatibility boundaries.
- No runtime plugin behavior changed in this documentation revision.

## 2.0.0 — 2026-08-06

### Command UX

- Added contextual `/hbow` dashboard.
- Added paginated and permission-aware `/hbow help`.
- Added detailed help per subcommand.
- Added typo suggestions and strict usage feedback.
- Added permission-aware Tab completion.
- Added `/hbow get`, `inspect`, `clear`, `preset`, `tune`, and `version`.
- Added verbose runtime diagnostics.
- Moved combat feedback to configurable Action Bar/chat/off channels.

### Reliability

- Split project into lifecycle, settings, messaging, item, listener, service, model and utility layers.
- Added validated immutable settings snapshots and atomic reload behavior.
- Added global/per-player limits, cooldown and bypass permission.
- Added disabled-world handling and target exemption permission.
- Added persistent projectile/shooter/age state.
- Added startup and chunk-load recovery, plus chunk-unload suspension.
- Added per-arrow exception isolation so one malformed projectile cannot kill the scheduler.
- Added finite-vector checks and zero/opposite-direction edge handling.
- Added strict offline compilation and logic tests.

### Compatibility

- Kept `/hbow give`, `/hbow reload`, and `/hbow status` compatible.
- Kept the same PDC identity for previously issued homing bows.

## 1.0.0

- Initial multi-player, multi-arrow physical homing implementation.
