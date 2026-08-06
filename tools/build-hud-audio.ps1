$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Source = Join-Path $Root "src\main\hud\third-party"
$Output = Join-Path $Root "src\main\hud\audio"
$Work = Join-Path $Root "target\hud-audio-build"

if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) {
    throw "FFmpeg is required to build the HUD audio assets."
}

$ExpectedHashes = [ordered]@{
    "yd-short-alarm.ogg" = "dc76a67748c9cef0b91913fafbe47cf8ee4499c4f813dbe12e028d2806f1eab8"
    "joth-7-space-sounds.mp3" = "b1038d8d24f09f94ec62dcb0ddf36f8394f8ef158a9d489c840be613d54ae935"
}

foreach ($Entry in $ExpectedHashes.GetEnumerator()) {
    $Path = Join-Path $Source $Entry.Key
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Missing vendored third-party source: $Path"
    }
    $Actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($Actual -ne $Entry.Value) {
        throw "Source hash mismatch for $($Entry.Key): expected $($Entry.Value), got $Actual"
    }
}

if (Test-Path -LiteralPath $Work) {
    $ResolvedWork = (Resolve-Path -LiteralPath $Work).Path
    $ResolvedTarget = (Resolve-Path -LiteralPath (Join-Path $Root "target")).Path
    if (-not $ResolvedWork.StartsWith($ResolvedTarget + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clean work directory outside target: $ResolvedWork"
    }
    Remove-Item -LiteralPath $ResolvedWork -Recurse -Force
}
New-Item -ItemType Directory -Path $Work, $Output -Force | Out-Null

function Invoke-Ffmpeg([string[]]$FfmpegArgs) {
    & ffmpeg -hide_banner -loglevel error -y @FfmpegArgs
    if ($LASTEXITCODE -ne 0) {
        throw "FFmpeg failed with exit code $LASTEXITCODE"
    }
}

# Launch is intentionally compact and dry to sit beside Minecraft's native bow,
# crossbow and firework sounds: a decaying string/ignition body, a filtered pink
# noise exhaust layer and a very short high-frequency mechanical snap.
$LaunchTone = "aevalsrc=exprs='0.72*sin(2*PI*(82*t-16*t*t))*exp(-7*t)+0.34*sin(2*PI*238*t)*exp(-19*t)+0.12*sin(2*PI*476*t)*exp(-22*t)+0.20*sin(2*PI*(135*t+150*t*t))*exp(-5*t)':s=48000:d=1.15"
Invoke-Ffmpeg @(
    "-f", "lavfi", "-i", $LaunchTone,
    "-f", "lavfi", "-i", "anoisesrc=color=pink:amplitude=0.72:duration=1.15:sample_rate=48000:seed=20260807",
    "-f", "lavfi", "-i", "anoisesrc=color=white:amplitude=0.85:duration=0.08:sample_rate=48000:seed=20260808",
    "-filter_complex", "[0:a]highpass=f=48,lowpass=f=4200,volume=-2dB[body];[1:a]highpass=f=130,lowpass=f=5200,tremolo=f=24:d=0.18,afade=t=in:st=0:d=0.012,afade=t=out:st=0.48:d=0.62,volume=-5dB[burn];[2:a]highpass=f=1100,lowpass=f=9500,afade=t=out:st=0.025:d=0.055,volume=-7dB[snap];[body][burn][snap]amix=inputs=3:duration=longest:dropout_transition=0:normalize=0,acompressor=threshold=-18dB:ratio=3.5:attack=3:release=70,loudnorm=I=-18:TP=-3:LRA=5,afade=t=out:st=0.84:d=0.31,volume=1dB[out]",
    "-map", "[out]", "-ac", "1", "-ar", "48000", "-c:a", "libvorbis", "-q:a", "6",
    "-fflags", "+bitexact", "-flags:a", "+bitexact", "-serial_offset", "0",
    (Join-Path $Work "launch.ogg")
)

# Lock confirmation uses four ascending, percussive harmonic notes. Their pitch
# relationship and short decay deliberately resemble a polished note-block/UI
# cue without copying any Minecraft sample.
$LockTone1 = "aevalsrc=exprs='0.78*(sin(2*PI*659.25*t)+0.20*sin(2*PI*1318.5*t))*exp(-21*t)':s=48000:d=0.18"
$LockTone2 = "aevalsrc=exprs='0.78*(sin(2*PI*880*t)+0.20*sin(2*PI*1760*t))*exp(-21*t)':s=48000:d=0.18"
$LockTone3 = "aevalsrc=exprs='0.78*(sin(2*PI*1108.73*t)+0.20*sin(2*PI*2217.46*t))*exp(-19*t)':s=48000:d=0.20"
$LockTone4 = "aevalsrc=exprs='0.65*sin(2*PI*1318.51*t)*exp(-10*t)+0.24*sin(2*PI*1977.77*t)*exp(-12*t)+0.10*sin(2*PI*2637.02*t)*exp(-14*t)':s=48000:d=0.34"
Invoke-Ffmpeg @(
    "-f", "lavfi", "-i", $LockTone1,
    "-f", "lavfi", "-i", $LockTone2,
    "-f", "lavfi", "-i", $LockTone3,
    "-f", "lavfi", "-i", $LockTone4,
    "-filter_complex", "[0:a]adelay=0,volume=-6dB[n0];[1:a]adelay=105,volume=-5dB[n1];[2:a]adelay=210,volume=-4dB[n2];[3:a]adelay=320,volume=-2dB[n3];[n0][n1][n2][n3]amix=inputs=4:duration=longest:dropout_transition=0:normalize=0,highpass=f=240,lowpass=f=8500,aecho=0.8:0.15:22:0.08,loudnorm=I=-18:TP=-3:LRA=4,afade=t=out:st=0.57:d=0.10,volume=2dB[out]",
    "-map", "[out]", "-ac", "1", "-ar", "48000", "-c:a", "libvorbis", "-q:a", "6",
    "-fflags", "+bitexact", "-flags:a", "+bitexact", "-serial_offset", "0",
    (Join-Path $Work "lock_confirm.ogg")
)

Invoke-Ffmpeg @(
    "-i", (Join-Path $Source "joth-7-space-sounds.mp3"),
    "-af", "atrim=start=11.10:end=11.33,asetpts=PTS-STARTPTS,loudnorm=I=-17:TP=-2:LRA=5,volume=-12dB,afade=t=out:st=0.17:d=0.06",
    "-ac", "1", "-ar", "48000", "-c:a", "libvorbis", "-q:a", "5",
    "-fflags", "+bitexact", "-flags:a", "+bitexact", "-serial_offset", "0",
    (Join-Path $Work "missile_warning.ogg")
)

Invoke-Ffmpeg @(
    "-i", (Join-Path $Source "yd-short-alarm.ogg"),
    "-af", "atrim=start=0.18:end=0.62,asetpts=PTS-STARTPTS,loudnorm=I=-14:TP=-1.5:LRA=5,afade=t=out:st=0.36:d=0.08",
    "-ac", "1", "-ar", "48000", "-c:a", "libvorbis", "-q:a", "5",
    "-fflags", "+bitexact", "-flags:a", "+bitexact", "-serial_offset", "0",
    (Join-Path $Work "missile_critical.ogg")
)

foreach ($Name in @("launch", "lock_confirm", "missile_warning", "missile_critical")) {
    Copy-Item -LiteralPath (Join-Path $Work "$Name.ogg") `
        -Destination (Join-Path $Output "$Name.ogg") -Force
}

Get-ChildItem -LiteralPath $Output -Filter *.ogg | Sort-Object Name |
    Select-Object Name, Length, @{Name = "SHA256"; Expression = {
        (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }} | Format-Table -AutoSize
