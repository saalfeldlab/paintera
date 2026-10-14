# Verify an installed Paintera on Windows with some runtime checks, see verify-installer.sh for Linux and macOS
#
# usage: verify-installer.ps1 <install root>, e.g. "C:\Program Files\Paintera"
param([Parameter(Mandatory)][string]$Root)
$ErrorActionPreference = 'Stop'

function Fail($message) {
  Write-Host "::error::$message"
  exit 1
}

$work = New-Item -ItemType Directory -Path (Join-Path ([IO.Path]::GetTempPath()) "paintera-verify-$([guid]::NewGuid())")

$cfg = Get-ChildItem -Recurse -Path $Root -Filter Paintera.cfg | Select-Object -First 1
if (-not $cfg) { Fail "no Paintera.cfg below $Root" }
$appDir = $cfg.DirectoryName
$java = (Get-Command java -ErrorAction SilentlyContinue).Source
if (-not $java) { Fail "no java on the PATH" }
$launcher = Get-ChildItem -Recurse -Path $Root -Filter Paintera.exe | Select-Object -First 1
if (-not $launcher) { Fail "no Paintera.exe below $Root" }
Write-Host "app dir:  $appDir"
Write-Host "java:     $java"
Write-Host "launcher: $($launcher.FullName)"

# the system properties the installed launcher passes to the JVM, with $APPDIR expanded
$options = @(foreach ($line in Get-Content $cfg.FullName) {
  if ($line -like 'java-options=*') {
    foreach ($option in ($line.Substring('java-options='.Length).Trim() -split '\s+')) {
      if ($option -match '^(-D|-XX:)') { $option.Replace('$APPDIR', $appDir) }
    }
  }
})
Write-Host "options:  $options"

Write-Host "::group::native libraries"
& javac -cp "$appDir\*" -d $work.FullName "$PSScriptRoot\InstallerRuntimeCheck.java"
if ($LASTEXITCODE -ne 0) { Fail "javac exited with $LASTEXITCODE" }
& $java @options -cp "$($work.FullName);$appDir\*" InstallerRuntimeCheck
if ($LASTEXITCODE -ne 0) { Fail "the native library check exited with $LASTEXITCODE" }
Write-Host "::endgroup::"

# smoke test the executable
Write-Host "::group::launcher --help"
$out = Join-Path $work.FullName help.txt
$err = Join-Path $work.FullName help-error.txt
$process = Start-Process -FilePath $launcher.FullName -ArgumentList '--help' -PassThru `
  -RedirectStandardOutput $out -RedirectStandardError $err
if (-not $process.WaitForExit(180000)) {
  $process.Kill()
  Fail "Paintera --help did not exit within 3 minutes"
}
Get-Content $out, $err -ErrorAction SilentlyContinue
Write-Host "::endgroup::"
if ($process.ExitCode -ne 0) { Fail "Paintera --help exited with $($process.ExitCode)" }
if (-not (Select-String -Path $out -Pattern 'usage' -Quiet)) {
  Write-Host "::warning::no usage text in the output of Paintera --help"
}
Write-Host "OK: $Root"
