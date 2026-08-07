[CmdletBinding()]
param(
    [string]$ConfigFile = "",
    [string]$KeyFile = "",
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail([string]$Message) {
    throw $Message
}

function Read-PropertiesFile([string]$Path) {
    $result = @{}
    $lineNumber = 0
    foreach ($rawLine in [System.IO.File]::ReadAllLines($Path, [System.Text.Encoding]::UTF8)) {
        $lineNumber++
        $line = $rawLine.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) {
            continue
        }
        $separator = $line.IndexOf('=')
        if ($separator -lt 1) {
            Fail "Malformed config line ${lineNumber}: expected key=value"
        }
        $key = $line.Substring(0, $separator).Trim().ToLowerInvariant()
        $value = $line.Substring($separator + 1).Trim()
        if ($result.ContainsKey($key)) {
            Fail "Duplicate config key: $key"
        }
        $result[$key] = $value
    }
    return $result
}

function Update-UploadConfig([string]$Path, [string]$IdentityPath) {
    $lines = [System.Collections.Generic.List[string]]::new()
    $identityFound = $false
    $batchFound = $false
    foreach ($line in [System.IO.File]::ReadAllLines($Path, [System.Text.Encoding]::UTF8)) {
        if ($line -match '^\s*identity_file\s*=') {
            $lines.Add("identity_file=$IdentityPath")
            $identityFound = $true
        } elseif ($line -match '^\s*batch_mode\s*=') {
            $lines.Add("batch_mode=true")
            $batchFound = $true
        } else {
            $lines.Add($line)
        }
    }
    if (-not $identityFound) {
        $lines.Add("identity_file=$IdentityPath")
    }
    if (-not $batchFound) {
        $lines.Add("batch_mode=true")
    }

    $temporary = "$Path.tmp-$PID"
    $encoding = [System.Text.UTF8Encoding]::new($false)
    try {
        [System.IO.File]::WriteAllText(
            $temporary,
            (($lines -join "`r`n") + "`r`n"),
            $encoding)
        Move-Item -LiteralPath $temporary -Destination $Path -Force
    } finally {
        if (Test-Path -LiteralPath $temporary) {
            Remove-Item -LiteralPath $temporary -Force
        }
    }
}

if ([string]::IsNullOrWhiteSpace($ConfigFile)) {
    $ConfigFile = Join-Path $PSScriptRoot "upload-config.properties"
}
if (-not (Test-Path -LiteralPath $ConfigFile -PathType Leaf)) {
    Fail "Upload config not found: $ConfigFile"
}
$ConfigFile = (Resolve-Path -LiteralPath $ConfigFile).Path
$config = Read-PropertiesFile $ConfigFile

$remoteHost = [string]$config["remote_host"]
$remoteUser = [string]$config["remote_user"]
$port = 0
if (-not [int]::TryParse([string]$config["port"], [ref]$port) -or
        $port -lt 1 -or $port -gt 65535) {
    Fail "Config port must be in the range 1..65535"
}
if ($remoteHost -notmatch '^[A-Za-z0-9.-]+$') {
    Fail "Config remote_host contains unsupported characters"
}
if ($remoteUser -notmatch '^[A-Za-z0-9._-]+$') {
    Fail "Config remote_user contains unsupported characters"
}

if ([string]::IsNullOrWhiteSpace($KeyFile) -and $config.ContainsKey("identity_file") -and
        -not [string]::IsNullOrWhiteSpace([string]$config["identity_file"])) {
    $KeyFile = [string]$config["identity_file"]
    if (-not [System.IO.Path]::IsPathRooted($KeyFile)) {
        $KeyFile = Join-Path (Split-Path -Parent $ConfigFile) $KeyFile
    }
}
if ([string]::IsNullOrWhiteSpace($KeyFile)) {
    $KeyFile = Join-Path $env:USERPROFILE ".ssh\homingmissiles_server_ed25519"
} elseif (-not [System.IO.Path]::IsPathRooted($KeyFile)) {
    $KeyFile = Join-Path (Get-Location).Path $KeyFile
}
$KeyFile = [System.IO.Path]::GetFullPath($KeyFile)
if ($KeyFile.Contains('"')) {
    Fail "Key path must not contain a quote"
}
$publicKeyFile = "$KeyFile.pub"
$endpoint = "${remoteUser}@${remoteHost}"

$ssh = Get-Command "ssh" -ErrorAction SilentlyContinue
$sshKeygen = Get-Command "ssh-keygen" -ErrorAction SilentlyContinue
if ($null -eq $ssh -or $null -eq $sshKeygen) {
    Fail "Windows OpenSSH Client is required"
}

Write-Host "[ssh-setup] Server: $endpoint port $port"
Write-Host "[ssh-setup] Key: $KeyFile"
if ($DryRun) {
    Write-Host "[ssh-setup] DRY RUN: would create/reuse the key, request the server password once, install the public key, verify key login, and update the local upload config"
    exit 0
}

$keyDirectory = Split-Path -Parent $KeyFile
if (-not (Test-Path -LiteralPath $keyDirectory -PathType Container)) {
    New-Item -ItemType Directory -Path $keyDirectory -Force | Out-Null
}
if (-not (Test-Path -LiteralPath $KeyFile -PathType Leaf)) {
    if (Test-Path -LiteralPath $publicKeyFile) {
        Fail "Public key exists without its private key: $publicKeyFile"
    }
    Write-Host "[ssh-setup] Creating a dedicated passwordless automation key"
    $comment = "homingmissiles@$remoteHost"
    $arguments = '-t ed25519 -a 64 -f "' + $KeyFile + '" -N "" -C "' + $comment + '"'
    $process = Start-Process -FilePath $sshKeygen.Source -ArgumentList $arguments `
        -NoNewWindow -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        Fail "ssh-keygen failed with exit code $($process.ExitCode)"
    }
} else {
    Write-Host "[ssh-setup] Reusing the existing private key"
}

if (-not (Test-Path -LiteralPath $publicKeyFile -PathType Leaf)) {
    Write-Host "[ssh-setup] Rebuilding the missing public key"
    $publicKey = (& $sshKeygen.Source -y -f $KeyFile | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($publicKey)) {
        Fail "Could not derive the public key"
    }
    [System.IO.File]::WriteAllText(
        $publicKeyFile,
        ($publicKey + "`n"),
        [System.Text.UTF8Encoding]::new($false))
}

$publicKeyText = [System.IO.File]::ReadAllText(
    $publicKeyFile,
    [System.Text.Encoding]::UTF8).Trim()
if ($publicKeyText -notmatch '^ssh-ed25519 [A-Za-z0-9+/=]+(?: .*)?$') {
    Fail "The public key is not a valid ssh-ed25519 key"
}

Write-Host "[ssh-setup] Enter the server password once when prompted"
$commonArguments = @(
    "-p", [string]$port,
    "-o", "ConnectTimeout=15",
    "-o", "StrictHostKeyChecking=accept-new"
)
$remoteInstall = @'
set -eu
encoded_key="$1"
incoming_key="$(printf '%s' "$encoded_key" | base64 -d)"
umask 077
current_uid="$(id -u)"
mkdir -p "$HOME/.ssh"
touch "$HOME/.ssh/authorized_keys"
case "$incoming_key" in
    ssh-ed25519\ *) ;;
    *) echo "invalid public key received" >&2; exit 1 ;;
esac
grep -qxF -- "$incoming_key" "$HOME/.ssh/authorized_keys" || \
    printf '%s\n' "$incoming_key" >>"$HOME/.ssh/authorized_keys"
chmod go-w "$HOME" 2>/dev/null || true
chmod 700 "$HOME/.ssh"
chmod 600 "$HOME/.ssh/authorized_keys"
for checked_path in "$HOME" "$HOME/.ssh" "$HOME/.ssh/authorized_keys"; do
    path_owner="$(stat -c '%u' "$checked_path")"
    if [ "$path_owner" != "$current_uid" ] && [ "$path_owner" != 0 ]; then
        echo "unsafe owner for $checked_path: uid=$path_owner, expected $current_uid or 0" >&2
        exit 1
    fi
    stat -c 'SSH_PATH=%U:%G mode=%a path=%n' "$checked_path"
done
if command -v ssh-keygen >/dev/null 2>&1; then
    ssh-keygen -lf "$HOME/.ssh/authorized_keys" || true
fi
sshd_command="$(command -v sshd 2>/dev/null || true)"
if [ -z "$sshd_command" ] && [ -x /usr/sbin/sshd ]; then
    sshd_command=/usr/sbin/sshd
fi
if [ -n "$sshd_command" ]; then
    "$sshd_command" -T 2>/dev/null | \
        grep -E '^(pubkeyauthentication|authorizedkeysfile|strictmodes) ' || true
fi
printf 'SSH_KEY_INSTALLED=PASS\n'
'@
$encodedInstall = [Convert]::ToBase64String(
    [System.Text.Encoding]::ASCII.GetBytes($remoteInstall))
$encodedPublicKey = [Convert]::ToBase64String(
    [System.Text.Encoding]::ASCII.GetBytes($publicKeyText))
$remoteCommand = "printf '%s' '$encodedInstall' | base64 -d | sh -s -- '$encodedPublicKey'"
& $ssh.Source @commonArguments $endpoint $remoteCommand
if ($LASTEXITCODE -ne 0) {
    Fail "Could not install the public key"
}

$testArguments = @(
    "-p", [string]$port,
    "-i", $KeyFile,
    "-o", "IdentitiesOnly=yes",
    "-o", "BatchMode=yes",
    "-o", "ConnectTimeout=15",
    "-o", "StrictHostKeyChecking=yes"
)
& $ssh.Source @testArguments $endpoint "printf 'SSH_KEY_AUTH=PASS\n'"
if ($LASTEXITCODE -ne 0) {
    Fail "The key was installed, but non-interactive login verification failed"
}

Update-UploadConfig $ConfigFile $KeyFile
Write-Host "[ssh-setup] SSH setup completed"
Write-Host "[ssh-setup] upload-config.properties now uses this key with batch_mode=true"
Write-Host "[ssh-setup] Future upload command: .\tools\upload-homingmissiles-3.1.3.cmd"
