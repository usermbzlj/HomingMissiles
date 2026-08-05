# Changelog

## 2.0.0+combat-hud-vfx — 2026-08-06

### Combat model

- Changed the default from dense salvos to high damage: 12 minimum arrow damage and a strict four-missile per-player in-flight cap.
- The four-missile cap cannot be bypassed by administrator permissions.
- Added independently configurable Flame, Infinity, Unbreaking/unbreakable and Power enchantments; all are enabled by default on newly issued bows.

### Native HUD and telemetry privacy

- Replaced continuous ActionBar text with native segmented BossBars.
- Shooter HUD exposes only four outbound channel slots; target identity, distance, direction and speed remain private.
- Target HUD exposes only threat count and an intentionally imprecise proximity gauge.
- `/hbow inspect` self-service no longer leaks target telemetry.

### Effects and audio

- Rebuilt launch, powered-flight, lock, impact and self-destruct effects around an exhaust flame, white-smoke wake, lock flash, shockwave and layered blast.
- Removed target-head marker particles.
- Replaced note-block beeps with layered firework/crossbow/beacon/sculk/warden cues and spatialized the recurring warning toward the nearest threat.
- Bumped configuration to version 4.

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
