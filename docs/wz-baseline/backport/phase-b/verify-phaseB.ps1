# Phase B content verification. EXIT CODES ARE NOT EVIDENCE — this compares content digests.
#
# For every forced (live-edited) image, three digests:
#   out   = the merged tree            must equal LIVE
#   live  = the owner's client         the reference
#   stock = the v84 base               must NOT equal out
# plus the discriminator: live != stock, or "out == live" proves nothing.
#
# `hash <wz> <dir>` emits one digest per direct child, so one invocation covers a whole
# directory of images. Runs per parent directory, three trees, one process at a time.
param(
  [string]$Stage = 'D:\games\wz-stage\phaseB',
  [string]$Repo  = 'D:\games\MapleStory\Server\Cosmic\.claude\worktrees\evan-dualblade',
  [string]$Live  = 'D:\games\MapleStory',
  [string]$Base  = 'D:\games\wz-stage\v84-base',
  [string[]]$Only
)
$ErrorActionPreference = 'Stop'
$W = Join-Path $Repo 'docs\wz-baseline\tool-merge\bin\Release\net10.0-windows\WzMerge.exe'

function HashDir([string]$wz, [string]$dirPath) {
  $h = @{}
  foreach ($l in (& $W hash $wz $dirPath)) {
    $p = $l -split '\s+', 2
    if ($p.Count -eq 2 -and $p[1] -notlike 'TOTAL*') { $h[$p[1].Trim()] = $p[0] }
  }
  return $h
}

$rows = @()
$T = [ordered]@{ checked=0; restored=0; stillStock=0; wrong=0; missing=0; vacuous=0; sibDropped=0; dirs=0 }

foreach ($f in (Get-ChildItem "$Stage\lists\*.force.txt" | Sort-Object Name)) {
  $a = $f.BaseName -replace '\.force$',''
  if ($Only -and $Only -notcontains $a) { continue }
  $imgs = Get-Content $f.FullName | Where-Object { $_ -notmatch '^\s*(#|$)' } | ForEach-Object { ($_ -split "`t")[0] }
  # group by parent directory
  $byDir = @{}
  foreach ($i in $imgs) {
    $d = $i.Substring(0, $i.LastIndexOf('/')); $n = $i.Substring($i.LastIndexOf('/') + 1)
    if (-not $byDir[$d]) { $byDir[$d] = @() }
    $byDir[$d] += $n
  }
  foreach ($d in ($byDir.Keys | Sort-Object)) {
    $T.dirs++
    $ho = HashDir "$Stage\out\$a.wz" $d
    $hl = HashDir "$Live\$a.wz"      $d
    $hs = HashDir "$Base\$a.wz"      $d
    foreach ($n in $byDir[$d]) {
      $T.checked++
      $o = $ho[$n]; $l = $hl[$n]; $s = $hs[$n]
      if (-not $o)      { $T.missing++;    $rows += "$a`t$d/$n`tMISSING-FROM-OUTPUT" ; continue }
      if ($l -eq $s)    { $T.vacuous++;    $rows += "$a`t$d/$n`tVACUOUS-live-equals-stock" }
      if ($o -eq $l)    { $T.restored++ }
      elseif ($o -eq $s){ $T.stillStock++; $rows += "$a`t$d/$n`tSTILL-STOCK-edit-lost" }
      else              { $T.wrong++;      $rows += "$a`t$d/$n`tWRONG-neither" }
    }
    # sibling check: every child the v84 base had in this directory must still be there
    foreach ($k in $hs.Keys) { if (-not $ho.ContainsKey($k)) { $T.sibDropped++; $rows += "$a`t$d/$k`tSIBLING-DROPPED" } }
  }
  Write-Host ("  {0,-10} {1,5} images over {2} dir(s)" -f $a, $imgs.Count, $byDir.Keys.Count)
}

""
"forced-image content verification"
$T.GetEnumerator() | ForEach-Object { "  {0,-12} {1}" -f $_.Key, $_.Value }
$out = "$Stage\reports\VERIFY-forced.tsv"
if ($rows.Count) { $rows | Set-Content $out -Encoding utf8; "  anomalies -> $out" }
else { "  no anomalies" ; "" | Set-Content $out -Encoding utf8 }
