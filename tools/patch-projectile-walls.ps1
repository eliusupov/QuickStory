<#
Runtime trial: targeted stars/arrows/bullets/magic pass through footholds. Client files stay intact.
Run elevated: .\tools\patch-projectile-walls.ps1 -ProcessId <gameplay PID>
Undo: same command with -Restore; closing the client also restores vanilla behavior.
Offline check: .\tools\patch-projectile-walls.ps1 -SelfTest
#>
[CmdletBinding()]
param([int] $ProcessId, [switch] $Restore, [switch] $SelfTest)
$ErrorActionPreference = 'Stop'
if (-not $SelfTest -and $ProcessId -le 0) { throw 'Supply the gameplay ProcessId.' }
$compiler = 'C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe'
$executable = Join-Path $env:TEMP 'QuickStory-ProjectileWalls.exe'
& $compiler /nologo /platform:x64 /out:$executable (Join-Path $PSScriptRoot 'projectile-walls\ProjectileWalls.cs')
if ($LASTEXITCODE -ne 0) { throw 'Compile failed.' }
if ($SelfTest) { & $executable test }
else { $operation = if ($Restore) { 'restore' } else { 'apply' }; & $executable $operation $ProcessId }
if ($LASTEXITCODE -ne 0) { throw 'Projectile patch refused; see helper output.' }
