$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Build = Join-Path $Root "build"
$Stubs = Join-Path $Build "stubs"
$Classes = Join-Path $Build "classes"
$Tests = Join-Path $Build "test"

function Require-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Missing command: $Name. Install JDK 21 and ensure its bin directory is on PATH."
    }
}

function Invoke-Checked([scriptblock]$Command, [string]$Step) {
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE"
    }
}

Require-Command "java"
Require-Command "javac"

if (Test-Path $Build) {
    Remove-Item $Build -Recurse -Force
}
New-Item $Stubs, $Classes, $Tests -ItemType Directory -Force | Out-Null

$StubSources = Get-ChildItem (Join-Path $Root "stubs\src\main\java") -Recurse -Filter *.java
$MainSources = Get-ChildItem (Join-Path $Root "src\main\java") -Recurse -Filter *.java
$TestSources = Get-ChildItem (Join-Path $Root "test") -Recurse -Filter *.java

# Pass source paths directly. javac @argfiles use the JVM platform charset, which
# is often GBK on Chinese Windows and corrupts UTF-8 argument files containing
# a Chinese workspace path.
Invoke-Checked { javac --release 21 -encoding UTF-8 -d $Stubs $StubSources.FullName } "compile stubs"
Invoke-Checked { javac --release 21 -encoding UTF-8 -Xlint:all -Werror -cp $Stubs -d $Classes $MainSources.FullName } "compile main"

Copy-Item (Join-Path $Root "src\main\resources\*") $Classes -Force

$TestClasspath = "$Stubs;$Classes"
Invoke-Checked { javac --release 21 -encoding UTF-8 -Xlint:all -Werror -cp $TestClasspath -d $Tests $TestSources.FullName } "compile tests"

$RuntimeClasspath = "$Stubs;$Classes;$Tests"
foreach ($Test in @("VectorMathTest", "GuidanceMathTest", "CommandUtilTest", "SettingsUtilityTest", "HudFormatTest", "ParticleCompatibilityTest", "HudPackServerTest")) {
    Invoke-Checked { java -cp $RuntimeClasspath "cn.yjj.homingmissiles.$Test" } "run $Test"
}

Write-Host "Offline verification: PASS" -ForegroundColor Green
