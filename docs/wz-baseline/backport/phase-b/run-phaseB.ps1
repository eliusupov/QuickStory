# Phase B — rebuild the v84 client tree carrying the owner's content.
# Reproducible from scratch. Reads the live client READ-ONLY; writes only under $Stage.
#
#   .\run-phaseB.ps1 -Dry          dry run every archive, write conflicts, touch nothing
#   .\run-phaseB.ps1               real merge
#   .\run-phaseB.ps1 -Only Map     one archive
[CmdletBinding()]
param(
  [switch]$Dry,
  [string[]]$Only,
  [string]$Stage = 'D:\games\wz-stage\phaseB',
  [string]$Repo  = 'D:\games\MapleStory\Server\Cosmic\.claude\worktrees\evan-dualblade',
  [string]$Live  = 'D:\games\MapleStory',
  [string]$Base  = 'D:\games\wz-stage\v84-base'
)

$ErrorActionPreference = 'Stop'
$Wz   = Join-Path $Repo 'docs\wz-baseline\tool-merge\bin\Release\net10.0-windows\WzMerge.exe'
$Deny = Join-Path $Repo 'docs\wz-baseline\merge-lists\COLLISION-DENY.txt'

# Smallest first: a failure surfaces in seconds, and the 3.5 GB archive runs last and alone.
$order = 'Sound','Etc','Skill','Reactor','Item','UI','Quest','String','Npc','Mob','Map','Character'
if ($Only) { $order = $order | Where-Object { $Only -contains $_ } }

$phase = if ($Dry) { 'dry' } else { 'real' }
$rep   = Join-Path $Stage "reports\merge-$phase.tsv"
New-Item -ItemType Directory -Force -Path (Split-Path $rep) | Out-Null
"archive`trows`tforce`tadded`tforced`trefused`texit`tsec`tpeakMB" | Set-Content $rep -Encoding utf8

foreach ($a in $order) {
  $paths = Join-Path $Stage "lists\$a.paths.txt"
  if (-not (Test-Path $paths)) { continue }
  $force = Join-Path $Stage "lists\$a.force.txt"
  $conf  = Join-Path $Stage "reports\$a.$phase.conflicts.txt"
  $log   = Join-Path $Stage "reports\$a.$phase.log"
  $out   = if ($Dry) { '-' } else { Join-Path $Stage "out\$a.wz" }

  $argv = @('merge', "$Live\$a.wz", "$Stage\pre\$a.wz", $out, $paths, $conf, '--deny', $Deny)
  if (Test-Path $force) { $argv += @('--force', $force) }
  if (-not $Dry)        { $argv += @('--live',  "$Base\$a.wz") }

  $nRows  = (Get-Content $paths | Where-Object { $_ -notmatch '^\s*(#|$)' }).Count
  $nForce = if (Test-Path $force) { (Get-Content $force | Where-Object { $_ -notmatch '^\s*(#|$)' }).Count } else { 0 }

  Write-Host "==== $a  rows=$nRows force=$nForce  ($phase)"
  $t0 = Get-Date
  # ONE ARCHIVE PER PROCESS. Peak working set is sampled off the process object after exit.
  $p = Start-Process -FilePath $Wz -ArgumentList $argv -NoNewWindow -PassThru `
                     -RedirectStandardOutput $log -RedirectStandardError "$log.err" -WorkingDirectory $Repo
  $null = $p.Handle   # PS 5.1: cache the handle or ExitCode reads back empty
  # PeakWorkingSet64 is only valid while the process lives, so sample it.
  $peak = 0
  while (-not $p.HasExited) {
    try { $p.Refresh(); $w = [math]::Round($p.PeakWorkingSet64 / 1MB, 0); if ($w -gt $peak) { $peak = $w } } catch {}
    Start-Sleep -Milliseconds 200
  }
  $p.WaitForExit()
  $sec  = [math]::Round(((Get-Date) - $t0).TotalSeconds, 1)
  $code = $p.ExitCode

  $txt = Get-Content $log -Raw
  $added = $forced = $refused = ''
  if ($txt -match 'added\s+(\d+)')      { $added   = $Matches[1] }
  if ($txt -match '\(forced\s+(\d+)\)') { $forced  = $Matches[1] }
  if ($txt -match 'refused\s+(\d+)')    { $refused = $Matches[1] }

  "$a`t$nRows`t$nForce`t$added`t$forced`t$refused`t$code`t$sec`t$peak" | Add-Content $rep -Encoding utf8
  Write-Host "     added=$added forced=$forced refused=$refused exit=$code ${sec}s peak=${peak}MB"
  if ($code -notin 0,3,5) { throw "$a exited $code - stop and read $log" }
}

Write-Host "`n---- $rep ----"
Get-Content $rep
