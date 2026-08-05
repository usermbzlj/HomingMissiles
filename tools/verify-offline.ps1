$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Build = Join-Path $Root "build"
$Stubs = Join-Path $Build "stubs"
$Classes = Join-Path $Build "classes"
$Tests = Join-Path $Build "test"

function Require-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "缺少命令：$Name。请安装 JDK 21，并确保其 bin 目录位于 PATH。"
    }
}

function Invoke-Checked([scriptblock]$Command, [string]$Step) {
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Step 失败，退出码：$LASTEXITCODE"
    }
}

function Write-ArgFile([System.IO.FileInfo[]]$Files, [string]$Path) {
    $Lines = $Files | ForEach-Object {
        '"' + $_.FullName.Replace('\', '\\') + '"'
    }
    Set-Content -Path $Path -Value $Lines -Encoding UTF8
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

Invoke-Checked { javac --release 21 -encoding UTF-8 -d $Stubs "@$StubArgs" } "编译 API 桩"
Invoke-Checked { javac --release 21 -encoding UTF-8 -Xlint:all -Werror -cp $Stubs -d $Classes "@$MainArgs" } "编译主代码"

Copy-Item (Join-Path $Root "src\main\resources\*") $Classes -Force

$TestClasspath = "$Stubs;$Classes"
Invoke-Checked { javac --release 21 -encoding UTF-8 -Xlint:all -Werror -cp $TestClasspath -d $Tests "@$TestArgs" } "编译测试"

$RuntimeClasspath = "$Stubs;$Classes;$Tests"
foreach ($Test in @("VectorMathTest", "CommandUtilTest", "SettingsUtilityTest")) {
    Invoke-Checked { java -cp $RuntimeClasspath "cn.yjj.homingmissiles.$Test" } "运行 $Test"
}

Write-Host "Offline verification: PASS" -ForegroundColor Green
