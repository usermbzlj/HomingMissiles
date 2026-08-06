# Third-party HUD audio sources

The launch and lock-confirmation sounds do not use third-party recordings.
They are synthesized deterministically by `tools/build-hud-audio.ps1` from
documented oscillators, filtered noise, envelopes and mixing stages designed
to fit Minecraft's compact, dry sound palette.

Only the two incoming-missile warning events retain external source material:

| Vendored file | Author / source | Used for | Source SHA-256 |
|---|---|---|---|
| `joth-7-space-sounds.mp3` | Joth, [7 Space Sounds](https://opengameart.org/content/7-space-sounds), a pulse from “Warning!” | `missile_warning.ogg` | `b1038d8d24f09f94ec62dcb0ddf36f8394f8ef158a9d489c840be613d54ae935` |
| `yd-short-alarm.ogg` | yd, [Short alarm](https://opengameart.org/content/short-alarm) | `missile_critical.ogg` | `dc76a67748c9cef0b91913fafbe47cf8ee4499c4f813dbe12e028d2806f1eab8` |

Both source pages mark their downloads as CC0 1.0. The complete CC0 legal
text is stored in `CC0-1.0.txt` and is also available from
[Creative Commons](https://creativecommons.org/publicdomain/zero/1.0/legalcode.txt).

The build script verifies both external source hashes before doing any work.
It then synthesizes launch and lock confirmation, edits the two warning clips,
and exports all four events as 48 kHz mono OGG Vorbis files. No audio is
extracted from Minecraft, *Ace Combat 7* or another commercial game.
