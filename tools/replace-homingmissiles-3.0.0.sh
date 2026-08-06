#!/usr/bin/env bash
# HomingMissiles 3.0.0 transactional replacement tool for Linux servers.
# Run as root. Existing plugin configuration is never overwritten.

set -Eeuo pipefail
IFS=$'\n\t'
umask 077

readonly RELEASE_VERSION="3.0.0"
readonly RELEASE_JAR_NAME="HomingMissiles-3.0.0.jar"
readonly EXPECTED_SHA256="fa090d31cd93dcd44c47a3937d65167e921d73f87538dceafb42ec26bc1f1fb6"
readonly EXPECTED_MAIN="cn.yjj.homingmissiles.HomingMissilesPlugin"
readonly DEFAULT_CONFIG_NAME="deploy-config.properties"

SOURCE_JAR=""
SERVER_DIR=""
SERVICE=""
INSTALL_ONLY=0
STARTUP_TIMEOUT=120
CONFIG_FILE=""
CONFIG_EXPLICIT=0

WORK_DIR=""
PLUGIN_DIR=""
DEST_JAR=""
STAGED_JAR=""
BACKUP_DIR=""
DEPLOY_LOG=""
TRANSACTION_STARTED=0
COMMITTED=0
SERVICE_WAS_ACTIVE=0
NEW_SERVICE_STARTED=0
BACKED_UP_NAMES=()
ZIP_BACKEND=""

usage() {
    cat <<'USAGE'
HomingMissiles 3.0.0 一键替换工具

零参数用法：
  # 把 JAR、脚本和 deploy-config.properties 放在同一目录后执行：
  sudo bash replace-homingmissiles-3.0.0.sh

脚本默认读取同目录的 HomingMissiles-3.0.0.jar 与 deploy-config.properties。

脚本会按服务器 WorkingDirectory 自动识别唯一的 systemd 服务。也可以显式指定：
  bash replace-homingmissiles-3.0.0.sh \
    --jar /root/HomingMissiles-3.0.0.jar \
    --server-dir /opt/minecraft \
    --service minecraft.service

如果脚本与 HomingMissiles-3.0.0.jar 放在同一目录，可以省略 --jar。

服务器已由你手动完整停止且不受 systemd 管理时：
  sudo bash replace-homingmissiles-3.0.0.sh \
    --jar /root/HomingMissiles-3.0.0.jar \
    --server-dir /opt/minecraft \
    --install-only

参数：
  --jar PATH            待安装的 3.0.0 JAR；可省略并使用上述查找顺序
  --config PATH         部署配置；默认读取脚本同目录的 deploy-config.properties
  --server-dir PATH     Minecraft 根目录；命令行值覆盖部署配置
  --service UNIT        显式指定 systemd 服务；省略时按 WorkingDirectory 自动识别
  --install-only        只安装，不操作或启动服务；服务器必须已经停止
  --timeout SECONDS     等待停服和启动验证的秒数，默认 120，范围 30..900
  -h, --help            显示帮助

安全保证：
  * 固定 SHA-256、ZIP 完整性、plugin.yml 名称/版本/主类三重校验
  * 排他锁、服务目录核对、残留 Java 进程检查
  * 旧 JAR 移入 plugins/.homingmissiles-backups/，不覆盖 config.yml
  * 同文件系统原子替换；启动或插件启用验证失败时自动回滚
USAGE
}

timestamp() {
    date '+%Y-%m-%d %H:%M:%S%z'
}

log() {
    local level="$1"
    shift
    local line
    line="[$(timestamp)] [$level] $*"
    printf '%s\n' "$line"
    if [[ -n "$DEPLOY_LOG" ]]; then
        printf '%s\n' "$line" >>"$DEPLOY_LOG" 2>/dev/null || true
    fi
}

die() {
    log ERROR "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "缺少必需命令：$1"
}

load_deploy_config() {
    local file="$1" raw line key value line_number=0
    local -A config_seen=()
    while IFS= read -r raw || [[ -n "$raw" ]]; do
        ((line_number += 1))
        line="${raw%$'\r'}"
        line="${line#"${line%%[![:space:]]*}"}"
        line="${line%"${line##*[![:space:]]}"}"
        [[ -z "$line" || "$line" == \#* ]] && continue
        [[ "$line" == *=* ]] || die "部署配置第 $line_number 行格式错误：应为 key=value"
        key="${line%%=*}"
        value="${line#*=}"
        key="${key#"${key%%[![:space:]]*}"}"
        key="${key%"${key##*[![:space:]]}"}"
        value="${value#"${value%%[![:space:]]*}"}"
        value="${value%"${value##*[![:space:]]}"}"
        [[ -n "$key" ]] || die "部署配置第 $line_number 行键名为空"
        [[ -z "${config_seen[$key]+x}" ]] || die "部署配置包含重复键：$key"
        config_seen["$key"]=1
        case "$key" in
            server_dir)
                SERVER_DIR="$value"
                ;;
            service)
                SERVICE="$value"
                ;;
            startup_timeout)
                STARTUP_TIMEOUT="$value"
                ;;
            *)
                die "部署配置包含未知键：$key"
                ;;
        esac
    done <"$file"
}

select_zip_backend() {
    if command -v unzip >/dev/null 2>&1; then
        ZIP_BACKEND="unzip"
    elif command -v python3 >/dev/null 2>&1; then
        ZIP_BACKEND="python3"
    else
        die "缺少 JAR 校验工具：请安装 unzip 或 python3"
    fi
}

zip_test() {
    local jar="$1"
    if [[ "$ZIP_BACKEND" == unzip ]]; then
        unzip -tqq "$jar" >/dev/null 2>&1
    else
        python3 - "$jar" <<'PY'
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as archive:
    bad = archive.testzip()
    if bad is not None:
        raise SystemExit("corrupt entry: " + bad)
PY
    fi
}

zip_read_plugin_yml() {
    local jar="$1"
    if [[ "$ZIP_BACKEND" == unzip ]]; then
        unzip -p "$jar" plugin.yml 2>/dev/null
    else
        python3 - "$jar" <<'PY'
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as archive:
    sys.stdout.buffer.write(archive.read("plugin.yml"))
PY
    fi
}

canonical_existing_path() {
    realpath -e -- "$1"
}

safe_remove_work_dir() {
    if [[ -n "$WORK_DIR" && -d "$WORK_DIR" ]]; then
        case "$WORK_DIR" in
            /tmp/homingmissiles-deploy.*|/var/tmp/homingmissiles-deploy.*)
                rm -rf -- "$WORK_DIR"
                ;;
            *)
                log ERROR "拒绝清理不符合安全前缀的临时目录：$WORK_DIR"
                ;;
        esac
    fi
}

wait_for_service_inactive() {
    local state
    local deadline=$((SECONDS + STARTUP_TIMEOUT))
    while (( SECONDS < deadline )); do
        state="$(systemctl is-active "$SERVICE" 2>/dev/null || true)"
        case "$state" in
            inactive|failed)
                return 0
                ;;
            active|activating|deactivating|reloading)
                sleep 1
                ;;
            *)
                return 1
                ;;
        esac
    done
    return 1
}

java_pids_in_server_dir() {
    local proc pid cwd exe base comm argv0 argv0_base
    for proc in /proc/[0-9]*; do
        [[ -d "$proc" ]] || continue
        cwd="$(readlink -f -- "$proc/cwd" 2>/dev/null || true)"
        [[ "$cwd" == "$SERVER_DIR" ]] || continue
        exe="$(readlink -f -- "$proc/exe" 2>/dev/null || true)"
        base="${exe##*/}"
        comm=""
        IFS= read -r comm <"$proc/comm" 2>/dev/null || true
        argv0=""
        IFS= read -r -d '' argv0 <"$proc/cmdline" 2>/dev/null || true
        argv0_base="${argv0##*/}"
        if [[ "$base" == java || "$base" == java-* || "$base" == java[0-9]* \
                || "$comm" == java || "$argv0_base" == java || "$argv0_base" == java-* \
                || "$argv0_base" == java[0-9]* ]]; then
            pid="${proc##*/}"
            printf '%s ' "$pid"
        fi
    done
}

discover_systemd_services() {
    local unit unit_workdir list_output
    local -A seen=()
    SERVICE_CANDIDATES=()
    command -v systemctl >/dev/null 2>&1 || return 2
    list_output="$(systemctl list-units --type=service --all --no-legend --plain --no-pager 2>/dev/null \
        | awk '{print $1}')" || return 2
    while IFS= read -r unit; do
        [[ "$unit" =~ ^[A-Za-z0-9_.@:-]+\.service$ ]] || continue
        unit_workdir="$(systemctl show "$unit" --property=WorkingDirectory --value 2>/dev/null || true)"
        [[ -n "$unit_workdir" && "$unit_workdir" == /* && -d "$unit_workdir" ]] || continue
        unit_workdir="$(canonical_existing_path "$unit_workdir")"
        if [[ "$unit_workdir" == "$SERVER_DIR" && -z "${seen[$unit]:-}" ]]; then
            seen[$unit]=1
            SERVICE_CANDIDATES+=("$unit")
        fi
    done <<<"$list_output"
}

is_homingmissiles_metadata() {
    local jar="$1"
    local metadata
    metadata="$(zip_read_plugin_yml "$jar" 2>/dev/null)" || return 1
    grep -Eiq "^[[:space:]]*name:[[:space:]]*['\"]?HomingMissiles['\"]?[[:space:]]*(#.*)?$" <<<"$metadata"
}

validate_release_jar() {
    local jar="$1"
    local actual_hash metadata

    zip_test "$jar" || die "JAR ZIP 完整性校验失败：$jar"
    actual_hash="$(sha256sum -- "$jar" | awk '{print tolower($1)}')"
    [[ "$actual_hash" == "$EXPECTED_SHA256" ]] || die \
        "JAR SHA-256 不匹配；期望 $EXPECTED_SHA256，实际 $actual_hash。拒绝安装。"

    metadata="$(zip_read_plugin_yml "$jar" 2>/dev/null)" || die "JAR 中缺少 plugin.yml"
    grep -Eiq "^[[:space:]]*name:[[:space:]]*['\"]?HomingMissiles['\"]?[[:space:]]*(#.*)?$" <<<"$metadata" \
        || die "plugin.yml 的 name 不是 HomingMissiles"
    grep -Eiq "^[[:space:]]*version:[[:space:]]*['\"]?3\.0\.0['\"]?[[:space:]]*(#.*)?$" <<<"$metadata" \
        || die "plugin.yml 的 version 不是 3.0.0"
    grep -Eiq "^[[:space:]]*main:[[:space:]]*['\"]?cn\.yjj\.homingmissiles\.HomingMissilesPlugin['\"]?[[:space:]]*(#.*)?$" <<<"$metadata" \
        || die "plugin.yml 的 main 不是 $EXPECTED_MAIN"
}

find_installed_homing_jars() {
    local candidate base
    INSTALLED_JARS=()
    while IFS= read -r -d '' candidate; do
        base="$(basename -- "$candidate")"
        if [[ -L "$candidate" ]]; then
            if [[ "$base" =~ ^[Hh][Oo][Mm][Ii][Nn][Gg][Mm][Ii][Ss][Ss][Ii][Ll][Ee][Ss].*\.[Jj][Aa][Rr]$ ]]; then
                die "发现同名符号链接，拒绝跟随：$candidate"
            fi
            continue
        fi
        if [[ "$base" =~ ^[Hh][Oo][Mm][Ii][Nn][Gg][Mm][Ii][Ss][Ss][Ii][Ll][Ee][Ss].*\.[Jj][Aa][Rr]$ ]] \
                || is_homingmissiles_metadata "$candidate"; then
            INSTALLED_JARS+=("$candidate")
        fi
    done < <(find "$PLUGIN_DIR" -mindepth 1 -maxdepth 1 \( -type f -o -type l \) -iname '*.jar' -print0)
}

capture_log_position() {
    local latest="$SERVER_DIR/logs/latest.log"
    LOG_ID_BEFORE=""
    LOG_SIZE_BEFORE=0
    if [[ -f "$latest" ]]; then
        LOG_ID_BEFORE="$(stat -c '%d:%i' -- "$latest")"
        LOG_SIZE_BEFORE="$(stat -c '%s' -- "$latest")"
    fi
}

new_log_has_start_marker() {
    local latest="$SERVER_DIR/logs/latest.log"
    local current_id current_size start_byte
    [[ -f "$latest" ]] || return 1
    current_id="$(stat -c '%d:%i' -- "$latest")"
    current_size="$(stat -c '%s' -- "$latest")"
    start_byte=1
    if [[ -n "$LOG_ID_BEFORE" && "$current_id" == "$LOG_ID_BEFORE" && "$current_size" -ge "$LOG_SIZE_BEFORE" ]]; then
        start_byte=$((LOG_SIZE_BEFORE + 1))
    fi
    tail -c "+$start_byte" -- "$latest" 2>/dev/null \
        | grep -aE 'Enabling HomingMissiles v3\.0\.0|HomingMissiles 3\.0\.0 .*已启用' >/dev/null
}

journal_has_start_marker() {
    command -v journalctl >/dev/null 2>&1 || return 1
    journalctl -u "$SERVICE" --since "@$SERVICE_START_EPOCH" --no-pager -o cat 2>/dev/null \
        | grep -aE 'Enabling HomingMissiles v3\.0\.0|HomingMissiles 3\.0\.0 .*已启用' >/dev/null
}

start_and_verify_new_release() {
    local deadline state
    capture_log_position
    SERVICE_START_EPOCH="$(date +%s)"
    log INFO "启动 systemd 服务：$SERVICE"
    systemctl start "$SERVICE" || die "systemd 无法启动 $SERVICE"
    NEW_SERVICE_STARTED=1
    deadline=$((SECONDS + STARTUP_TIMEOUT))

    while (( SECONDS < deadline )); do
        state="$(systemctl is-active "$SERVICE" 2>/dev/null || true)"
        if [[ "$state" == active ]] && (new_log_has_start_marker || journal_has_start_marker); then
            log INFO "服务处于 active，且已观察到 HomingMissiles 3.0.0 启用标记。"
            return 0
        fi
        if [[ "$state" == failed ]]; then
            die "服务在启动期间进入 failed 状态"
        fi
        sleep 1
    done
    die "在 ${STARTUP_TIMEOUT}s 内未同时确认服务 active 和插件 3.0.0 启用标记"
}

rollback() {
    local name failed_artifact rollback_ok=1
    log WARN "部署未完成，开始自动回滚。"

    if [[ -n "$SERVICE" && $NEW_SERVICE_STARTED -eq 1 ]]; then
        systemctl stop "$SERVICE" >/dev/null 2>&1 || rollback_ok=0
        wait_for_service_inactive >/dev/null 2>&1 || rollback_ok=0
    fi

    if [[ -n "$BACKUP_DIR" && -d "$BACKUP_DIR" ]]; then
        if [[ -n "$DEST_JAR" && -f "$DEST_JAR" ]]; then
            failed_artifact="$BACKUP_DIR/failed-$RELEASE_JAR_NAME"
            [[ ! -e "$failed_artifact" ]] || failed_artifact="$failed_artifact.$$"
            mv -T -- "$DEST_JAR" "$failed_artifact" || rollback_ok=0
        fi
        if [[ -n "$STAGED_JAR" && -f "$STAGED_JAR" ]]; then
            failed_artifact="$BACKUP_DIR/failed-staged-$RELEASE_JAR_NAME"
            [[ ! -e "$failed_artifact" ]] || failed_artifact="$failed_artifact.$$"
            mv -T -- "$STAGED_JAR" "$failed_artifact" || rollback_ok=0
        fi
        for name in "${BACKED_UP_NAMES[@]}"; do
            if [[ -f "$BACKUP_DIR/$name" ]]; then
                mv -T -- "$BACKUP_DIR/$name" "$PLUGIN_DIR/$name" || rollback_ok=0
            fi
        done
        sync -f "$PLUGIN_DIR" >/dev/null 2>&1 || true
    fi

    if [[ -n "$SERVICE" && $SERVICE_WAS_ACTIVE -eq 1 ]]; then
        log INFO "重新启动回滚后的原服务：$SERVICE"
        systemctl start "$SERVICE" >/dev/null 2>&1 || rollback_ok=0
    fi

    if (( rollback_ok == 1 )); then
        log WARN "自动回滚已完成；失败的 3.0.0 文件保存在备份目录中供排查。"
    else
        log ERROR "自动回滚遇到错误。旧 JAR 仍保存在：$BACKUP_DIR"
    fi
}

on_exit() {
    local rc=$?
    trap - EXIT INT TERM
    set +e
    if (( rc != 0 && TRANSACTION_STARTED == 1 && COMMITTED == 0 )); then
        rollback
    fi
    safe_remove_work_dir
    exit "$rc"
}

trap on_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
CONFIG_FILE="$script_dir/$DEFAULT_CONFIG_NAME"
arguments=("$@")
for ((argument_index = 0; argument_index < ${#arguments[@]}; argument_index++)); do
    if [[ "${arguments[argument_index]}" == --config ]]; then
        (( argument_index + 1 < ${#arguments[@]} )) || die "--config 缺少路径"
        CONFIG_FILE="${arguments[argument_index + 1]}"
        CONFIG_EXPLICIT=1
        ((argument_index += 1))
    fi
done
if [[ -f "$CONFIG_FILE" ]]; then
    CONFIG_FILE="$(canonical_existing_path "$CONFIG_FILE")"
    load_deploy_config "$CONFIG_FILE"
elif (( CONFIG_EXPLICIT == 1 )); then
    die "部署配置不存在：$CONFIG_FILE"
fi

while (( $# > 0 )); do
    case "$1" in
        --jar)
            (( $# >= 2 )) || die "--jar 缺少路径"
            SOURCE_JAR="$2"
            shift 2
            ;;
        --server-dir)
            (( $# >= 2 )) || die "--server-dir 缺少路径"
            SERVER_DIR="$2"
            shift 2
            ;;
        --config)
            (( $# >= 2 )) || die "--config 缺少路径"
            shift 2
            ;;
        --service)
            (( $# >= 2 )) || die "--service 缺少服务名"
            SERVICE="$2"
            shift 2
            ;;
        --install-only)
            INSTALL_ONLY=1
            shift
            ;;
        --timeout)
            (( $# >= 2 )) || die "--timeout 缺少秒数"
            STARTUP_TIMEOUT="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            die "未知参数：$1（使用 --help 查看用法）"
            ;;
    esac
done

[[ "$STARTUP_TIMEOUT" =~ ^[0-9]+$ ]] || die "--timeout 必须是整数"
(( STARTUP_TIMEOUT >= 30 && STARTUP_TIMEOUT <= 900 )) || die "--timeout 范围必须为 30..900 秒"
[[ "$SERVICE" != auto ]] || SERVICE=""
if (( INSTALL_ONLY == 1 )); then
    [[ -z "$SERVICE" ]] || die "--install-only 不能与 --service 同时使用"
fi

if (( EUID != 0 )) && [[ "${HM_INSTALLER_TESTING:-0}" != 1 ]]; then
    die "必须使用 root 运行（例如 sudo bash $0 ...）"
fi

for command_name in realpath sha256sum awk grep find stat install flock mv cp sync readlink tail date; do
    require_command "$command_name"
done
select_zip_backend

if [[ -z "$SOURCE_JAR" ]]; then
    if [[ -f "$script_dir/$RELEASE_JAR_NAME" ]]; then
        SOURCE_JAR="$script_dir/$RELEASE_JAR_NAME"
    elif [[ -f "$PWD/$RELEASE_JAR_NAME" ]]; then
        SOURCE_JAR="$PWD/$RELEASE_JAR_NAME"
    else
        die "未找到 $RELEASE_JAR_NAME；已检查脚本目录和当前目录"
    fi
fi
[[ -f "$SOURCE_JAR" ]] || die "JAR 不存在或不是普通文件：$SOURCE_JAR"
SOURCE_JAR="$(canonical_existing_path "$SOURCE_JAR")"

if [[ -z "$SERVER_DIR" ]]; then
    if [[ -n "$SERVICE" ]]; then
        require_command systemctl
        SERVER_DIR="$(systemctl show "$SERVICE" --property=WorkingDirectory --value 2>/dev/null || true)"
        [[ -n "$SERVER_DIR" && "$SERVER_DIR" == /* ]] \
            || die "无法从 systemd WorkingDirectory 安全确定服务器目录，请显式提供 --server-dir"
    else
        die "未配置服务器目录；请设置 deploy-config.properties 的 server_dir 或使用 --server-dir"
    fi
fi
[[ -d "$SERVER_DIR" ]] || die "服务器目录不存在：$SERVER_DIR"
SERVER_DIR="$(canonical_existing_path "$SERVER_DIR")"
[[ "$SERVER_DIR" != / ]] || die "拒绝把根目录 / 当作服务器目录"
[[ -f "$SERVER_DIR/server.properties" ]] || die "服务器目录缺少 server.properties：$SERVER_DIR"
[[ -d "$SERVER_DIR/plugins" ]] || die "服务器目录缺少 plugins/：$SERVER_DIR"
PLUGIN_DIR="$(canonical_existing_path "$SERVER_DIR/plugins")"
[[ "$PLUGIN_DIR" != / ]] || die "解析后的 plugins 目录不能是 /"
DEST_JAR="$PLUGIN_DIR/$RELEASE_JAR_NAME"

if (( INSTALL_ONLY == 0 )) && [[ -z "$SERVICE" ]]; then
    running_before_detection="$(java_pids_in_server_dir)"
    if discover_systemd_services; then
        case "${#SERVICE_CANDIDATES[@]}" in
            1)
                SERVICE="${SERVICE_CANDIDATES[0]}"
                log INFO "自动识别到 systemd 服务：$SERVICE"
                ;;
            0)
                if [[ -n "$running_before_detection" ]]; then
                    die "检测到服务器 Java 进程（PID: $running_before_detection），但没有 WorkingDirectory 精确匹配的 systemd 服务；可能由面板管理，拒绝在线覆盖。请先通过面板完整停服，再加 --install-only 运行。"
                fi
                INSTALL_ONLY=1
                log WARN "服务器当前无 Java 进程，也没有匹配的 systemd 服务；自动采用 install-only，不会启动服务器。"
                ;;
            *)
                die "发现多个 WorkingDirectory 匹配的 systemd 服务：${SERVICE_CANDIDATES[*]}；请用 --service 明确指定"
                ;;
        esac
    elif [[ -n "$running_before_detection" ]]; then
        die "检测到服务器 Java 进程（PID: $running_before_detection），但 systemd 不可用；拒绝在线覆盖。请先完整停服，再加 --install-only 运行。"
    else
        INSTALL_ONLY=1
        log WARN "systemd 不可用且服务器当前无 Java 进程；自动采用 install-only，不会启动服务器。"
    fi
fi

if [[ -n "$SERVICE" ]]; then
    require_command systemctl
    [[ "$SERVICE" =~ ^[A-Za-z0-9_.@:-]+$ && "$SERVICE" != -* ]] || die "非法 systemd 服务名：$SERVICE"
    load_state="$(systemctl show "$SERVICE" --property=LoadState --value 2>/dev/null || true)"
    [[ "$load_state" == loaded ]] || die "systemd 单元不存在或未加载：$SERVICE"
fi

if [[ -n "$SERVICE" ]]; then
    unit_workdir="$(systemctl show "$SERVICE" --property=WorkingDirectory --value 2>/dev/null || true)"
    if [[ -n "$unit_workdir" && "$unit_workdir" == /* && -d "$unit_workdir" ]]; then
        unit_workdir="$(canonical_existing_path "$unit_workdir")"
        [[ "$unit_workdir" == "$SERVER_DIR" ]] || die \
            "安全检查失败：服务 WorkingDirectory=$unit_workdir，但 --server-dir=$SERVER_DIR"
    fi
fi

WORK_DIR="$(TMPDIR=/tmp mktemp -d -t homingmissiles-deploy.XXXXXXXX)"
chmod 0700 "$WORK_DIR"
cp -- "$SOURCE_JAR" "$WORK_DIR/$RELEASE_JAR_NAME"
SOURCE_JAR="$WORK_DIR/$RELEASE_JAR_NAME"
validate_release_jar "$SOURCE_JAR"
log INFO "已验证 HomingMissiles $RELEASE_VERSION，SHA-256=$EXPECTED_SHA256"
log INFO "目标服务器：$SERVER_DIR"

exec {LOCK_FD}>"$SERVER_DIR/.homingmissiles-deploy.lock"
flock -n "$LOCK_FD" || die "另一个 HomingMissiles 部署正在进行"

find_installed_homing_jars
if (( ${#INSTALLED_JARS[@]} == 1 )) && [[ "${INSTALLED_JARS[0]}" == "$DEST_JAR" ]]; then
    installed_hash="$(sha256sum -- "$DEST_JAR" | awk '{print tolower($1)}')"
    if [[ "$installed_hash" == "$EXPECTED_SHA256" ]]; then
        log INFO "目标已是经过校验的 HomingMissiles 3.0.0；无需停服或重复替换。"
        COMMITTED=1
        exit 0
    fi
fi

owner_uid="$(stat -c '%u' -- "$SERVER_DIR")"
owner_gid="$(stat -c '%g' -- "$SERVER_DIR")"
if (( ${#INSTALLED_JARS[@]} > 0 )); then
    owner_uid="$(stat -c '%u' -- "${INSTALLED_JARS[0]}")"
    owner_gid="$(stat -c '%g' -- "${INSTALLED_JARS[0]}")"
fi

if [[ -n "$SERVICE" ]]; then
    service_state="$(systemctl is-active "$SERVICE" 2>/dev/null || true)"
    case "$service_state" in
        active)
            SERVICE_WAS_ACTIVE=1
            ;;
        inactive|failed)
            log WARN "服务当前为 $service_state；替换后将保持不启动。"
            ;;
        *)
            die "服务处于不安全的过渡状态：$service_state"
            ;;
    esac
fi

TRANSACTION_STARTED=1
if (( SERVICE_WAS_ACTIVE == 1 )); then
    log INFO "停止 systemd 服务：$SERVICE"
    systemctl stop "$SERVICE" || die "systemd 停止服务失败：$SERVICE"
    wait_for_service_inactive || die "服务在 ${STARTUP_TIMEOUT}s 内未停止"
fi

remaining_java="$(java_pids_in_server_dir)"
[[ -z "$remaining_java" ]] || die "服务器目录仍有 Java 进程（PID: $remaining_java），拒绝覆盖正在使用的 JAR"

backup_stamp="$(date -u '+%Y%m%dT%H%M%SZ')"
BACKUP_DIR="$PLUGIN_DIR/.homingmissiles-backups/$backup_stamp-$$"
mkdir -p -- "$BACKUP_DIR"
chmod 0700 "$PLUGIN_DIR/.homingmissiles-backups" "$BACKUP_DIR"
DEPLOY_LOG="$BACKUP_DIR/deploy.log"
printf 'release=%s\nexpected_sha256=%s\nserver_dir=%s\nservice=%s\n' \
    "$RELEASE_VERSION" "$EXPECTED_SHA256" "$SERVER_DIR" "${SERVICE:-install-only}" \
    >"$BACKUP_DIR/deployment.properties"

if [[ -f "$PLUGIN_DIR/HomingMissiles/config.yml" ]]; then
    cp -a -- "$PLUGIN_DIR/HomingMissiles/config.yml" "$BACKUP_DIR/config.yml.preserved"
    log INFO "已快照现有 config.yml；原配置不会被覆盖。"
fi

for installed_jar in "${INSTALLED_JARS[@]}"; do
    jar_name="$(basename -- "$installed_jar")"
    old_hash="$(sha256sum -- "$installed_jar" | awk '{print tolower($1)}')"
    printf 'old_jar=%s sha256=%s\n' "$jar_name" "$old_hash" >>"$BACKUP_DIR/deployment.properties"
    mv -T -- "$installed_jar" "$BACKUP_DIR/$jar_name"
    BACKED_UP_NAMES+=("$jar_name")
    log INFO "已备份旧插件：$jar_name ($old_hash)"
done

STAGED_JAR="$PLUGIN_DIR/.${RELEASE_JAR_NAME}.new.$$"
install -o "$owner_uid" -g "$owner_gid" -m 0644 -- "$SOURCE_JAR" "$STAGED_JAR"
staged_hash="$(sha256sum -- "$STAGED_JAR" | awk '{print tolower($1)}')"
[[ "$staged_hash" == "$EXPECTED_SHA256" ]] || die "写入目标磁盘后哈希异常"
[[ ! -e "$DEST_JAR" ]] || die "原子替换前目标文件意外重新出现：$DEST_JAR"
mv -T -- "$STAGED_JAR" "$DEST_JAR"
STAGED_JAR=""
if command -v restorecon >/dev/null 2>&1; then
    restorecon -F "$DEST_JAR" >/dev/null 2>&1 || log WARN "restorecon 未成功；请检查 SELinux 上下文"
fi
sync -f "$PLUGIN_DIR"
log INFO "3.0.0 JAR 已原子安装：$DEST_JAR"

if [[ "${HM_INSTALLER_TESTING:-0}" == 1 && "${HM_INSTALLER_TEST_FAIL_AFTER_INSTALL:-0}" == 1 ]]; then
    die "测试注入：安装后强制失败"
fi

if (( SERVICE_WAS_ACTIVE == 1 )); then
    start_and_verify_new_release
elif (( INSTALL_ONLY == 1 )); then
    log WARN "install-only 已完成；脚本未启动服务器，请在确认后手动启动并检查日志。"
else
    log WARN "服务原本未运行，因此保持停止状态；JAR 身份与哈希已验证。"
fi

final_hash="$(sha256sum -- "$DEST_JAR" | awk '{print tolower($1)}')"
[[ "$final_hash" == "$EXPECTED_SHA256" ]] || die "最终 JAR 哈希验证失败"
printf 'result=success\ninstalled_jar=%s\ninstalled_sha256=%s\ncompleted_at=%s\n' \
    "$DEST_JAR" "$final_hash" "$(timestamp)" >>"$BACKUP_DIR/deployment.properties"
COMMITTED=1
log INFO "部署成功。备份与审计记录：$BACKUP_DIR"
