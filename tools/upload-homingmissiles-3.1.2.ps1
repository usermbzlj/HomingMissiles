[CmdletBinding()]
param(
    [string]$ConfigFile = "",
    [string]$RemoteHostName = "",
    [string]$RemoteUser = "",
    [Nullable[int]]$Port = $null,
    [string]$RemoteDirectory = "",
    [string]$IdentityFile = "",
    [switch]$SkipHudPack,
    [switch]$BatchMode,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ReleaseVersion = "3.1.2"
$ReleaseJarName = "HomingMissiles-3.1.2.jar"
$InstallerName = "replace-homingmissiles-3.1.2.sh"
$HudPackName = "HomingMissiles-HUD-1.21.11.zip"
$ChecksumName = "SHA256SUMS.txt"
$DeployConfigName = "deploy-config.properties"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Fail([string]$Message) {
    throw $Message
}

function Require-File([string]$Path, [string]$BuildHint) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Fail "Missing file: $Path. $BuildHint"
    }
}

function Get-HashLower([string]$Path, [string]$Algorithm) {
    $hashAlgorithm = [System.Security.Cryptography.HashAlgorithm]::Create($Algorithm)
    if ($null -eq $hashAlgorithm) {
        Fail "Unsupported hash algorithm: $Algorithm"
    }
    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $bytes = $hashAlgorithm.ComputeHash($stream)
        return ([System.BitConverter]::ToString($bytes) -replace '-', '').ToLowerInvariant()
    } finally {
        $stream.Dispose()
        $hashAlgorithm.Dispose()
    }
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
            Fail "Malformed properties line ${lineNumber}: expected key=value"
        }
        $key = $line.Substring(0, $separator).Trim().ToLowerInvariant()
        $value = $line.Substring($separator + 1).Trim()
        if ($result.ContainsKey($key)) {
            Fail "Duplicate properties key: $key"
        }
        $result[$key] = $value
    }
    return $result
}

function Read-BooleanSetting($Config, [string]$Key, [bool]$DefaultValue) {
    if (-not $Config.ContainsKey($Key)) {
        return $DefaultValue
    }
    switch ($Config[$Key].ToLowerInvariant()) {
        "true" { return $true }
        "false" { return $false }
        default { Fail "Upload config $Key must be true or false" }
    }
}

function Format-Command([string]$Executable, [string[]]$Arguments) {
    $display = foreach ($argument in $Arguments) {
        if ($argument -match '^[A-Za-z0-9_./:@=,+-]+$') {
            $argument
        } else {
            '"' + $argument.Replace('"', '\"') + '"'
        }
    }
    return $Executable + " " + ($display -join " ")
}

function Invoke-Native(
        [string]$Executable,
        [string[]]$Arguments,
        [string]$Description,
        [string]$DisplayOverride = "") {
    if ([string]::IsNullOrEmpty($DisplayOverride)) {
        Write-Host ("[upload] " + (Format-Command $Executable $Arguments))
    } else {
        Write-Host "[upload] $DisplayOverride"
    }
    if ($DryRun) {
        return
    }
    & $Executable @Arguments
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        Fail "$Description failed with exit code $exitCode"
    }
}

function Add-ZipFile($Archive, [string]$Path) {
    $entry = $Archive.CreateEntry(
        [System.IO.Path]::GetFileName($Path),
        [System.IO.Compression.CompressionLevel]::Optimal)
    $entry.LastWriteTime = [DateTimeOffset]::FromUnixTimeSeconds(315532800)
    $inputStream = [System.IO.File]::OpenRead($Path)
    try {
        $outputStream = $entry.Open()
        try {
            $inputStream.CopyTo($outputStream)
        } finally {
            $outputStream.Dispose()
        }
    } finally {
        $inputStream.Dispose()
    }
}

if ([string]::IsNullOrWhiteSpace($ConfigFile)) {
    $ConfigFile = Join-Path $PSScriptRoot "upload-config.properties"
}
if (-not (Test-Path -LiteralPath $ConfigFile -PathType Leaf)) {
    Fail "Upload config not found: $ConfigFile. Copy tools/upload-config.example.properties and edit the local copy."
}
$ConfigFile = (Resolve-Path -LiteralPath $ConfigFile).Path
$uploadConfig = Read-PropertiesFile $ConfigFile
$allowedConfigKeys = @(
    "remote_host", "remote_user", "port", "remote_directory",
    "identity_file", "include_hud_pack", "batch_mode")
foreach ($key in $uploadConfig.Keys) {
    if ($allowedConfigKeys -notcontains $key) {
        Fail "Unknown upload config key: $key"
    }
}

if (-not $PSBoundParameters.ContainsKey("RemoteHostName")) {
    $RemoteHostName = [string]$uploadConfig["remote_host"]
}
if (-not $PSBoundParameters.ContainsKey("RemoteUser")) {
    $RemoteUser = [string]$uploadConfig["remote_user"]
}
if (-not $PSBoundParameters.ContainsKey("RemoteDirectory")) {
    $RemoteDirectory = [string]$uploadConfig["remote_directory"]
}
if (-not $PSBoundParameters.ContainsKey("Port")) {
    $configuredPort = 0
    if (-not [int]::TryParse([string]$uploadConfig["port"], [ref]$configuredPort)) {
        Fail "Upload config port must be an integer"
    }
    $Port = $configuredPort
}
$identityFromConfig = $false
if (-not $PSBoundParameters.ContainsKey("IdentityFile") -and $uploadConfig.ContainsKey("identity_file")) {
    $IdentityFile = [string]$uploadConfig["identity_file"]
    $identityFromConfig = $true
}
$includeHudPack = Read-BooleanSetting $uploadConfig "include_hud_pack" $true
if ($SkipHudPack) {
    $includeHudPack = $false
}
$configuredBatchMode = Read-BooleanSetting $uploadConfig "batch_mode" $false
if (-not $PSBoundParameters.ContainsKey("BatchMode")) {
    $BatchMode = $configuredBatchMode
}
$SkipHudPack = -not $includeHudPack

if ($null -eq $Port -or $Port -lt 1 -or $Port -gt 65535) {
    Fail "Port must be in the range 1..65535"
}

if ($RemoteHostName -notmatch '^[A-Za-z0-9.-]+$') {
    Fail "RemoteHostName contains unsupported characters"
}
if ($RemoteUser -notmatch '^[A-Za-z0-9._-]+$') {
    Fail "RemoteUser contains unsupported characters"
}
if ($RemoteDirectory -notmatch '^/[A-Za-z0-9._/-]+$' -or $RemoteDirectory -eq "/") {
    Fail "RemoteDirectory must be a safe absolute path other than /"
}
$RemoteDirectory = $RemoteDirectory.TrimEnd('/')
$remoteSegments = @($RemoteDirectory.Split('/') | Where-Object { $_ -ne "" })
if ($remoteSegments -contains "." -or $remoteSegments -contains "..") {
    Fail "RemoteDirectory must not contain . or .. path segments"
}

$scpCommand = Get-Command "scp" -ErrorAction SilentlyContinue
$sshCommand = Get-Command "ssh" -ErrorAction SilentlyContinue
if ($null -eq $scpCommand -or $null -eq $sshCommand) {
    Fail "OpenSSH scp/ssh is required. Enable the Windows OpenSSH Client feature."
}

$releaseJar = Join-Path $Root "target\$ReleaseJarName"
$installer = Join-Path $Root "tools\$InstallerName"
$checksum = Join-Path $Root $ChecksumName
$deployConfig = Join-Path $PSScriptRoot $DeployConfigName
$hudPack = Join-Path $Root "target\hud-packs\$HudPackName"
$hudChecksum = "$hudPack.sha1"
$buildHint = "Run Maven clean package first."

Require-File $releaseJar $buildHint
Require-File $installer "Restore the release tools from Git."
Require-File $checksum "Restore $ChecksumName from Git."
Require-File $deployConfig "Copy tools/deploy-config.example.properties and edit the local copy."
if (-not $SkipHudPack) {
    Require-File $hudPack $buildHint
    Require-File $hudChecksum $buildHint
}

$deploySettings = Read-PropertiesFile $deployConfig
$allowedDeployKeys = @("server_dir", "service", "startup_timeout")
foreach ($key in $deploySettings.Keys) {
    if ($allowedDeployKeys -notcontains $key) {
        Fail "Unknown deploy config key: $key"
    }
}
$configuredServerDirectory = [string]$deploySettings["server_dir"]
if ($configuredServerDirectory -notmatch '^/[A-Za-z0-9._/-]+$' -or $configuredServerDirectory -eq "/") {
    Fail "Deploy config server_dir must be a safe absolute path other than /"
}
$serverSegments = @($configuredServerDirectory.Split('/') | Where-Object { $_ -ne "" })
if ($serverSegments -contains "." -or $serverSegments -contains "..") {
    Fail "Deploy config server_dir must not contain . or .. path segments"
}
$configuredService = [string]$deploySettings["service"]
if ($configuredService -ne "auto" -and
        $configuredService -notmatch '^[A-Za-z0-9_.@:-]+$') {
    Fail "Deploy config service must be auto or a safe systemd unit name"
}
$configuredTimeout = 0
if (-not [int]::TryParse([string]$deploySettings["startup_timeout"], [ref]$configuredTimeout) -or
        $configuredTimeout -lt 30 -or $configuredTimeout -gt 900) {
    Fail "Deploy config startup_timeout must be in the range 30..900"
}

$checksumText = Get-Content -LiteralPath $checksum -Raw
$jarMatch = [regex]::Match(
    $checksumText,
    '(?im)^([0-9a-f]{64})[ \t]+HomingMissiles-3\.1\.2\.jar\s*$')
if (-not $jarMatch.Success) {
    Fail "$ChecksumName does not contain the 3.1.2 JAR checksum"
}
$expectedJarHash = $jarMatch.Groups[1].Value.ToLowerInvariant()
$actualJarHash = Get-HashLower $releaseJar "SHA256"
if ($actualJarHash -ne $expectedJarHash) {
    Fail "JAR checksum mismatch: expected=$expectedJarHash actual=$actualJarHash"
}

$installerText = Get-Content -LiteralPath $installer -Raw
$installerHashMatch = [regex]::Match(
    $installerText,
    'EXPECTED_SHA256="([0-9a-f]{64})"')
if (-not $installerHashMatch.Success -or
        $installerHashMatch.Groups[1].Value.ToLowerInvariant() -ne $expectedJarHash) {
    Fail "The installer does not pin the same JAR SHA-256"
}

$expectedHudHash = ""
if (-not $SkipHudPack) {
    $hudChecksumText = Get-Content -LiteralPath $hudChecksum -Raw
    $hudMatch = [regex]::Match(
        $hudChecksumText,
        '(?im)^([0-9a-f]{40})[ \t]+HomingMissiles-HUD-1\.21\.11\.zip\s*$')
    if (-not $hudMatch.Success) {
        Fail "$HudPackName.sha1 is malformed"
    }
    $expectedHudHash = $hudMatch.Groups[1].Value.ToLowerInvariant()
    $actualHudHash = Get-HashLower $hudPack "SHA1"
    if ($actualHudHash -ne $expectedHudHash) {
        Fail "HUD checksum mismatch: expected=$expectedHudHash actual=$actualHudHash"
    }
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($releaseJar)
try {
    $jarEntries = @($zip.Entries | ForEach-Object { $_.FullName })
    foreach ($requiredEntry in @("plugin.yml", "config.yml", "cn/yjj/homingmissiles/HomingMissilesPlugin.class")) {
        if ($jarEntries -notcontains $requiredEntry) {
            Fail "Release JAR is missing $requiredEntry"
        }
    }
} finally {
    $zip.Dispose()
}

$identityArguments = @()
if (-not [string]::IsNullOrWhiteSpace($IdentityFile)) {
    $identityPath = $IdentityFile
    if ($identityFromConfig -and -not [System.IO.Path]::IsPathRooted($identityPath)) {
        $identityPath = Join-Path (Split-Path -Parent $ConfigFile) $identityPath
    }
    $resolvedIdentity = (Resolve-Path -LiteralPath $identityPath).Path
    $identityArguments = @("-i", $resolvedIdentity)
}
$commonArguments = @(
    "-o", "ConnectTimeout=15",
    "-o", "ServerAliveInterval=15",
    "-o", "ServerAliveCountMax=3",
    "-o", "StrictHostKeyChecking=accept-new"
) + $identityArguments
if ($BatchMode) {
    $commonArguments += @("-o", "BatchMode=yes")
}
$scpArguments = @("-P", [string]$Port) + $commonArguments
$sshArguments = @("-p", [string]$Port) + $commonArguments
$remoteEndpoint = "${RemoteUser}@${RemoteHostName}"
$stamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$remoteBundle = "$RemoteDirectory/.homingmissiles-upload-$ReleaseVersion-$stamp-$PID.zip"
$localBundle = Join-Path ([System.IO.Path]::GetTempPath()) (
    "homingmissiles-upload-$ReleaseVersion-$stamp-$PID.zip")

$remoteScript = @'
set -Eeuo pipefail
IFS=$'\n\t'

bundle="$1"
dest="$2"
expected_jar="$3"
expected_hud="$4"
include_hud="$5"

required_commands=(realpath mktemp sha256sum awk grep bash install mv rm)
if [[ "$include_hud" == 1 ]]; then
    required_commands+=(sha1sum)
fi
for command_name in "${required_commands[@]}"; do
    command -v "$command_name" >/dev/null 2>&1 || {
        echo "missing remote command: $command_name" >&2
        exit 1
    }
done

dest_real="$(realpath -e -- "$dest")"
bundle_real="$(realpath -e -- "$bundle")"
case "$bundle_real" in
    "$dest_real"/.homingmissiles-upload-3.1.2-*.zip) ;;
    *) echo "unsafe upload bundle path: $bundle_real" >&2; exit 1 ;;
esac

stage="$(mktemp -d "$dest_real/.homingmissiles-stage-3.1.2.XXXXXXXX")"
case "$stage" in
    "$dest_real"/.homingmissiles-stage-3.1.2.*) ;;
    *) echo "unsafe staging path: $stage" >&2; exit 1 ;;
esac
temporary_files=()
cleanup() {
    local path
    for path in "${temporary_files[@]:-}"; do
        [[ -z "$path" ]] || rm -f -- "$path"
    done
    if [[ -n "${stage:-}" && -d "$stage" && "$stage" == "$dest_real"/.homingmissiles-stage-3.1.2.* ]]; then
        rm -rf -- "$stage"
    fi
    rm -f -- "$bundle_real"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if command -v unzip >/dev/null 2>&1; then
    unzip -q "$bundle_real" -d "$stage"
elif command -v python3 >/dev/null 2>&1; then
    python3 - "$bundle_real" "$stage" <<'PY'
import pathlib
import sys
import zipfile

bundle = pathlib.Path(sys.argv[1])
stage = pathlib.Path(sys.argv[2]).resolve()
with zipfile.ZipFile(bundle) as archive:
    for item in archive.infolist():
        name = pathlib.PurePosixPath(item.filename)
        if name.is_absolute() or ".." in name.parts or len(name.parts) != 1:
            raise SystemExit("unsafe ZIP entry: " + item.filename)
    archive.extractall(stage)
PY
else
    echo "remote host needs unzip or python3" >&2
    exit 1
fi

cd "$stage"
required=(HomingMissiles-3.1.2.jar replace-homingmissiles-3.1.2.sh SHA256SUMS.txt deploy-config.properties)
if [[ "$include_hud" == 1 ]]; then
    required+=(HomingMissiles-HUD-1.21.11.zip HomingMissiles-HUD-1.21.11.zip.sha1)
fi
for name in "${required[@]}"; do
    [[ -f "$name" ]] || { echo "missing bundle file: $name" >&2; exit 1; }
done

actual_jar="$(sha256sum -- HomingMissiles-3.1.2.jar | awk '{print tolower($1)}')"
[[ "$actual_jar" == "$expected_jar" ]] || { echo "remote JAR hash mismatch" >&2; exit 1; }
grep -Eiq "^${expected_jar}[[:space:]]+HomingMissiles-3\.1\.2\.jar[[:space:]]*$" SHA256SUMS.txt
grep -Fq "readonly EXPECTED_SHA256=\"$expected_jar\"" replace-homingmissiles-3.1.2.sh
bash -n replace-homingmissiles-3.1.2.sh

if [[ "$include_hud" == 1 ]]; then
    actual_hud="$(sha1sum -- HomingMissiles-HUD-1.21.11.zip | awk '{print tolower($1)}')"
    [[ "$actual_hud" == "$expected_hud" ]] || { echo "remote HUD hash mismatch" >&2; exit 1; }
    grep -Eiq "^${expected_hud}[[:space:]]+HomingMissiles-HUD-1\.21\.11\.zip[[:space:]]*$" \
        HomingMissiles-HUD-1.21.11.zip.sha1
fi

promote() {
    local name="$1" mode="$2" temporary
    temporary="$dest_real/.$name.upload-$$"
    temporary_files+=("$temporary")
    install -m "$mode" -- "$stage/$name" "$temporary"
    mv -fT -- "$temporary" "$dest_real/$name"
}

promote HomingMissiles-3.1.2.jar 0644
promote replace-homingmissiles-3.1.2.sh 0755
promote SHA256SUMS.txt 0644
promote deploy-config.properties 0600
if [[ "$include_hud" == 1 ]]; then
    promote HomingMissiles-HUD-1.21.11.zip 0644
    promote HomingMissiles-HUD-1.21.11.zip.sha1 0644
fi

final_jar="$(sha256sum -- "$dest_real/HomingMissiles-3.1.2.jar" | awk '{print tolower($1)}')"
[[ "$final_jar" == "$expected_jar" ]] || { echo "promoted JAR hash mismatch" >&2; exit 1; }
printf 'UPLOAD=PASS\nREMOTE_DIR=%s\nJAR_SHA256=%s\n' "$dest_real" "$final_jar"
if [[ "$include_hud" == 1 ]]; then
    printf 'HUD_SHA1=%s\n' "$expected_hud"
fi
printf 'NEXT=sudo bash %s/replace-homingmissiles-3.1.2.sh\n' "$dest_real"
'@

$archive = $null
$remoteUploadAttempted = $false
try {
    if (Test-Path -LiteralPath $localBundle) {
        Remove-Item -LiteralPath $localBundle -Force
    }
    $archive = [System.IO.Compression.ZipFile]::Open(
        $localBundle,
        [System.IO.Compression.ZipArchiveMode]::Create)
    Add-ZipFile $archive $releaseJar
    Add-ZipFile $archive $installer
    Add-ZipFile $archive $checksum
    Add-ZipFile $archive $deployConfig
    if (-not $SkipHudPack) {
        Add-ZipFile $archive $hudPack
        Add-ZipFile $archive $hudChecksum
    }
    $archive.Dispose()
    $archive = $null

    Write-Host "[upload] Local validation passed"
    Write-Host "[upload] JAR SHA-256: $expectedJarHash"
    if (-not $SkipHudPack) {
        Write-Host "[upload] HUD SHA-1: $expectedHudHash"
    }

    $remoteUploadAttempted = $true
    Invoke-Native $scpCommand.Source `
        ($scpArguments + @($localBundle, "${remoteEndpoint}:$remoteBundle")) `
        "SCP upload"

    # PowerShell here-strings use the host platform's line endings. Normalize
    # before base64 transport so Bash never receives a CR in tokens such as
    # `set -Eeuo pipefail` when the uploader runs from Windows.
    $normalizedRemoteScript = $remoteScript.Replace("`r`n", "`n").Replace("`r", "`n")
    $encodedScript = [Convert]::ToBase64String(
        [System.Text.Encoding]::ASCII.GetBytes($normalizedRemoteScript))
    $includeHud = if ($SkipHudPack) { "0" } else { "1" }
    $remoteCommand = "printf '%s' '$encodedScript' | base64 -d | bash -s -- " +
        "'$remoteBundle' '$RemoteDirectory' '$expectedJarHash' '$expectedHudHash' '$includeHud'"
    Invoke-Native $sshCommand.Source `
        ($sshArguments + @($remoteEndpoint, $remoteCommand)) `
        "Remote verification and promotion" `
        "ssh -p $Port $remoteEndpoint <verify and atomically promote release bundle>"

    if ($DryRun) {
        Write-Host "[upload] DRY RUN complete; no remote files were changed"
    } else {
        Write-Host "[upload] Release upload completed and verified"
        Write-Host "[upload] Next: ssh -p $Port $remoteEndpoint"
        Write-Host "[upload] Then: sudo bash $RemoteDirectory/$InstallerName"
    }
} catch {
    if ($remoteUploadAttempted -and -not $DryRun) {
        $cleanupCommand = "rm -f -- '$remoteBundle'"
        try {
            & $sshCommand.Source @sshArguments $remoteEndpoint $cleanupCommand
        } catch {
            Write-Warning "Could not remove the incomplete remote upload bundle"
        }
    }
    throw
} finally {
    if ($null -ne $archive) {
        $archive.Dispose()
    }
    if (Test-Path -LiteralPath $localBundle) {
        Remove-Item -LiteralPath $localBundle -Force
    }
}
