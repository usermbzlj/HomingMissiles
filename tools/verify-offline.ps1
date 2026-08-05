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

function Write-ArgFile([System.IO.FileInfo[]]$Files, [string]$Path) {
    $Lines = $Files | ForEach-Object {
        '"' + $_.FullName.Replace('\', '\\') + '"'
    }
    # javac @argfile rejects UTF-8 BOM
    [System.IO.File]::WriteAllLines($Path, $Lines, [System.Text.UTF8Encoding]::new($false))
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

$StubArgs = Join-Path $Build "stubs.args"
$MainArgs = Join-Path $Build "main.args"
$TestArgs = Join-Path $Build "test.args"

Write-ArgFile $StubSources $StubArgs
Write-ArgFile $MainSources $MainArgs
Write-ArgFile $TestSources $TestArgs

Invoke-Checked { javac --release 21 -encoding UTF-8 -d $Stubs "@$StubArgs" } "compile stubs"
Invoke-Checked { javac --release 21 -encoding UTF-8 -Xlint:all -Werror -cp $Stubs -d $Classes "@$MainArgs" } "compile main"

Copy-Item (Join-Path $Root "src\main\resources\*") $Classes -Force

$TestClasspath = "$Stubs;$Classes"
Invoke-Checked { javac --release 21 -encoding UTF-8 -Xlint:all -Werror -cp $TestClasspath -d $Tests "@$TestArgs" } "compile tests"

$RuntimeClasspath = "$Stubs;$Classes;$Tests"
foreach ($Test in @("VectorMathTest", "CommandUtilTest", "SettingsUtilityTest")) {
    Invoke-Checked { java -cp $RuntimeClasspath "cn.yjj.homingmissiles.$Test" } "run $Test"
}

Write-Host "Offline verification: PASS" -ForegroundColor Green
