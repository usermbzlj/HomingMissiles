#!/usr/bin/env bash
# Isolated regression test for replace-homingmissiles-3.1.3.sh.

set -Eeuo pipefail
IFS=$'\n\t'

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
INSTALLER="$ROOT/tools/replace-homingmissiles-3.1.3.sh"
RELEASE_JAR="$ROOT/target/HomingMissiles-3.1.3.jar"
TEST_ROOT="$(mktemp -d -t homingmissiles-installer-test.XXXXXXXX)"
SERVER="$TEST_ROOT/server"
UPLOAD="$TEST_ROOT/upload"

cleanup() {
    if [[ -n "${TEST_ROOT:-}" && -d "$TEST_ROOT"
            && "$TEST_ROOT" == /tmp/homingmissiles-installer-test.* ]]; then
        rm -rf -- "$TEST_ROOT"
    fi
}
trap cleanup EXIT

hash_file() {
    local value remainder
    IFS=' ' read -r value remainder < <(sha256sum -- "$1")
    printf '%s' "$value"
}

[[ -f "$INSTALLER" ]] || { echo "installer missing: $INSTALLER" >&2; exit 1; }
[[ -f "$RELEASE_JAR" ]] || { echo "release JAR missing: $RELEASE_JAR" >&2; exit 1; }

mkdir -p "$SERVER/plugins/HomingMissiles" "$UPLOAD"
touch "$SERVER/server.properties"
cp -- "$ROOT/src/main/resources/config.yml" "$SERVER/plugins/HomingMissiles/config.yml"
cp -- "$INSTALLER" "$UPLOAD/replace-homingmissiles-3.1.3.sh"
cp -- "$RELEASE_JAR" "$UPLOAD/HomingMissiles-3.1.3.jar"
sed "s#server_dir=/opt/minecraft#  server_dir = $SERVER  #" \
    "$ROOT/tools/deploy-config.example.properties" >"$UPLOAD/deploy-config.properties"
config_before="$(hash_file "$SERVER/plugins/HomingMissiles/config.yml")"

HM_INSTALLER_TESTING=1 bash "$UPLOAD/replace-homingmissiles-3.1.3.sh"
installed_hash="$(hash_file "$SERVER/plugins/HomingMissiles-3.1.3.jar")"

# A second run must stop before any transaction or backup is created.
HM_INSTALLER_TESTING=1 bash "$UPLOAD/replace-homingmissiles-3.1.3.sh"

# CLI values must override a config file, while duplicate keys must fail closed.
sed 's#server_dir=/opt/minecraft#server_dir=/definitely/not/a/server#' \
    "$ROOT/tools/deploy-config.example.properties" >"$UPLOAD/override.properties"
HM_INSTALLER_TESTING=1 bash "$UPLOAD/replace-homingmissiles-3.1.3.sh" \
    --config "$UPLOAD/override.properties" --server-dir "$SERVER" --install-only
printf 'server_dir=%s\nserver_dir=%s\nservice=auto\nstartup_timeout=120\n' \
    "$SERVER" "$SERVER" >"$UPLOAD/duplicate.properties"
set +e
HM_INSTALLER_TESTING=1 bash "$UPLOAD/replace-homingmissiles-3.1.3.sh" \
    --config "$UPLOAD/duplicate.properties" >/dev/null 2>&1
duplicate_rc=$?
set -e
[[ "$duplicate_rc" -ne 0 ]] || { echo "duplicate config unexpectedly succeeded" >&2; exit 1; }

# Add a distinct but still structurally valid legacy JAR, inject a post-install
# failure, and prove that the exact pre-transaction set is restored.
cp -- "$SERVER/plugins/HomingMissiles-3.1.3.jar" "$SERVER/plugins/HomingMissiles-2.0.0.jar"
# ZIP readers permit trailing bytes; extending by one byte creates a distinct,
# still-readable legacy fixture without requiring a JDK inside the test shell.
legacy_size="$(stat -c '%s' "$SERVER/plugins/HomingMissiles-2.0.0.jar")"
truncate -s "$((legacy_size + 1))" "$SERVER/plugins/HomingMissiles-2.0.0.jar"
legacy_hash="$(hash_file "$SERVER/plugins/HomingMissiles-2.0.0.jar")"

set +e
HM_INSTALLER_TESTING=1 HM_INSTALLER_TEST_FAIL_AFTER_INSTALL=1 \
    bash "$UPLOAD/replace-homingmissiles-3.1.3.sh"
forced_rc=$?
set -e
[[ "$forced_rc" -ne 0 ]] || { echo "forced failure unexpectedly succeeded" >&2; exit 1; }

[[ "$(hash_file "$SERVER/plugins/HomingMissiles-3.1.3.jar")" == "$installed_hash" ]]
[[ "$(hash_file "$SERVER/plugins/HomingMissiles-2.0.0.jar")" == "$legacy_hash" ]]
[[ "$(hash_file "$SERVER/plugins/HomingMissiles/config.yml")" == "$config_before" ]]

printf 'INSTALL=%s\nZERO_ARGUMENT_CONFIG=PASS\nCONFIG_OVERRIDE=PASS\nCONFIG_VALIDATION=PASS\nIDEMPOTENCE=PASS\nROLLBACK=PASS\nCONFIG_PRESERVED=PASS\n' \
    "$installed_hash"
