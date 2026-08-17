# Phase B, conflict resolution pass — the owner's "Maximum v84 parity" decision.
# Merges HIS side of the A/B/C-keep rows onto the phase-B tree (which already carries v84).
# Reads the live client READ-ONLY; writes only under $Stage\conflicts.
#
#   .\resolve-conflicts.ps1 -Dry      dry run every archive, write conflicts, touch nothing
#   .\resolve-conflicts.ps1           real merge  -> $Stage\conflicts\out
[CmdletBinding()]
param(
  [switch]$Dry,
  [string[]]$Only,
  [string]$Stage = 'D:\games\wz-stage\phaseB',
  [string]$Repo  = 'D:\games\MapleStory\Server\Cosmic\.claude\worktrees\evan-dualblade',
  [string]$Live  = 'D:\games\MapleStory'
)
$ErrorActionPreference = 'Stop'
$Wz   = Join-Path $Repo 'docs\wz-baseline\tool-merge\bin\Release\net10.0-windows\WzMerge.exe'
$Deny = Join-Path $Repo 'docs\wz-baseline\merge-lists\COLLISION-DENY.txt'
$C    = Join-Path $Stage 'conflicts'

# Smallest first. Character/Map/Mob carry the only heavy opens and run last.
$order = 'Etc','Quest','Skill','String','Item','Character','Mob','Map'
if ($Only) { $order = $order | Where-Object { $Only -contains $_ } }

New-Item -ItemType Directory -Force -Path "$C\pre","$C\out","$C\reports" | Out-Null

$phase = if ($Dry) { 'dry' } else { 'real' }
$rep   = "$C\reports\merge-$phase.tsv"
"archive`trows`tadded`tforced`trefused`texit`tsec`tpeakMB" | Set-Content $rep -Encoding utf8

foreach ($a in $order) {
  $paths = "$C\lists\$a.paths.txt"
  if (-not (Test-Path $paths)) { continue }
  $force = "$C\lists\$a.force.txt"

  # The target must be a byte-identical copy of --live: here "live" is the phase-B tree this
  # pass edits, not the client. The client is the merge SOURCE and is never written.
  if (-not $Dry -or -not (Test-Path "$C\pre\$a.wz")) {
    Copy-Item "$Stage\tree\$a.wz" "$C\pre\$a.wz" -Force
  }
  $out = if ($Dry) { '-' } else { "$C\out\$a.wz" }
  $log = "$C\reports\$a.$phase.log"

  $argv = @('merge', "$Live\$a.wz", "$C\pre\$a.wz", $out, $paths,
            "$C\reports\$a.$phase.conflicts.txt", '--deny', $Deny, '--force', $force)
  if (-not $Dry) { $argv += @('--live', "$Stage\tree\$a.wz") }

  $nRows = (Get-Content $paths | Where-Object { $_ -notmatch '^\s*(#|$)' }).Count
  Write-Host "==== $a  rows=$nRows  ($phase)"
  $t0 = Get-Date
  # ONE ARCHIVE PER PROCESS. Never in parallel: Character/Map/Mob each want GBs.
  $p = Start-Process -FilePath $Wz -ArgumentList $argv -NoNewWindow -PassThru `
                     -RedirectStandardOutput $log -RedirectStandardError "$log.err" -WorkingDirectory $Repo
  $null = $p.Handle
  $peak = 0
  while (-not $p.HasExited) {
    try { $p.Refresh(); $w = [math]::Round($p.PeakWorkingSet64 / 1MB, 0); if ($w -gt $peak) { $peak = $w } } catch {}
    Start-Sleep -Milliseconds 200
  }
  $p.WaitForExit()
  $sec = [math]::Round(((Get-Date) - $t0).TotalSeconds, 1); $code = $p.ExitCode

  $txt = Get-Content $log -Raw
  $added = $forced = $refused = ''
  if ($txt -match 'added\s+(\d+)')      { $added   = $Matches[1] }
  if ($txt -match '\(forced\s+(\d+)\)') { $forced  = $Matches[1] }
  if ($txt -match 'refused\s+(\d+)')    { $refused = $Matches[1] }

  "$a`t$nRows`t$added`t$forced`t$refused`t$code`t$sec`t$peak" | Add-Content $rep -Encoding utf8
  Write-Host "     added=$added forced=$forced refused=$refused exit=$code ${sec}s peak=${peak}MB"
  if ($code -notin 0,3,5) { throw "$a exited $code - stop and read $log" }
}

Write-Host "`n---- $rep ----"
Get-Content $rep
