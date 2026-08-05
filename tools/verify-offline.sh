#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
rm -rf "$ROOT/build"
mkdir -p "$ROOT/build/stubs" "$ROOT/build/classes" "$ROOT/build/test"
find "$ROOT/stubs/src/main/java" -name '*.java' -print0 | xargs -0 javac --release 21 -encoding UTF-8 -d "$ROOT/build/stubs"
find "$ROOT/src/main/java" -name '*.java' -print0 | xargs -0 javac --release 21 -encoding UTF-8 -Xlint:all -Werror -cp "$ROOT/build/stubs" -d "$ROOT/build/classes"
cp "$ROOT/src/main/resources/"* "$ROOT/build/classes/"
find "$ROOT/test" -name '*.java' -print0 | xargs -0 javac --release 21 -encoding UTF-8 -Xlint:all -Werror -cp "$ROOT/build/stubs:$ROOT/build/classes" -d "$ROOT/build/test"
for test in VectorMathTest CommandUtilTest SettingsUtilityTest HudFormatTest; do
  java -cp "$ROOT/build/stubs:$ROOT/build/classes:$ROOT/build/test" "cn.yjj.homingmissiles.$test"
done
echo "Offline verification: PASS"
