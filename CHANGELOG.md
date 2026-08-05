# Changelog

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
