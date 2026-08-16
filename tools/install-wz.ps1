<#
Copies a staged WZ set into the live client and proves the copy landed.

  .\install-wz.ps1 -From 10c -Files Item,Quest,Map,Mob,Morph,Npc,Sound,Reactor
  .\install-wz.ps1 -Restore -Files Item,Quest        # put the backup copies back
  .\install-wz.ps1 -Status                           # what differs from the backup

ponytail: no backup step - Server\_backup\client-v83-EzorsiaV2-2026-08-15 is the
rollback and is never written to. Restore is a copy out of it.
#>
[CmdletBinding(DefaultParameterSetName = 'Install')]
param(
    [Parameter(ParameterSetName = 'Install', Mandatory)][string]$From,
    [Parameter(ParameterSetName = 'Install', Mandatory)]
    [Parameter(ParameterSetName = 'Restore', Mandatory)][string[]]$Files,
    [Parameter(ParameterSetName = 'Restore', Mandatory)][switch]$Restore,
    [Parameter(ParameterSetName = 'Status', Mandatory)][switch]$Status
)

$ErrorActionPreference = 'Stop'
$Live   = 'D:\games\MapleStory'
$Stage  = 'D:\games\MapleStory\Server\wz-merge'
$Backup = 'D:\games\MapleStory\Server\_backup\client-v83-EzorsiaV2-2026-08-15'

function Hash($p) { (Get-FileHash $p -Algorithm SHA256).Hash }   # foreground only - see STATUS.md

if ($Status) {
    Get-ChildItem $Live -Filter *.wz | ForEach-Object {
        $b = Join-Path $Backup $_.Name
        $state = if (-not (Test-Path $b)) { 'NO-BACKUP' }
                 elseif ((Hash $_.FullName) -eq (Hash $b)) { 'SAME' } else { 'DIFF' }
        [pscustomobject]@{ File = $_.Name; State = $state; MB = [math]::Round($_.Length / 1MB, 2) }
    } | Format-Table -AutoSize
    return
}

# A live client holds the archives open; a partial copy is how you corrupt a 600 MB file.
$busy = Get-Process -Name MapleStory, local, localhome -ErrorAction SilentlyContinue
if ($busy) { throw "Client is running (PID $($busy.Id -join ', ')). Close it first." }

$src = if ($Restore) { $Backup } else { Join-Path $Stage $From }
if (-not (Test-Path $src)) { throw "Source not found: $src" }

# Resolve and check every file up front - a half-applied set is worse than none.
$plan = foreach ($f in $Files) {
    $name = if ($f -like '*.wz') { $f } else { "$f.wz" }
    $s = Join-Path $src $name
    if (-not (Test-Path $s)) { throw "Missing in source: $s" }
    [pscustomobject]@{ Name = $name; Src = $s; Dst = Join-Path $Live $name }
}

foreach ($p in $plan) {
    $srcHash = Hash $p.Src
    Copy-Item $p.Src $p.Dst -Force
    $dstHash = Hash $p.Dst
    if ($srcHash -ne $dstHash) { throw "VERIFY FAILED for $($p.Name): $srcHash != $dstHash" }
    $vs = if ((Test-Path (Join-Path $Backup $p.Name)) -and
              $dstHash -eq (Hash (Join-Path $Backup $p.Name))) { 'now SAME as backup' } else { 'DIFF from backup' }
    "{0,-14} <- {1}  ok, {2}" -f $p.Name, (Split-Path $src -Leaf), $vs
}
"`n$($plan.Count) file(s) installed and hash-verified."
