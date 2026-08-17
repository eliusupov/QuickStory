# Second pass over the OVERLAP conflicts: a collision on a top-level child such as `info` is
# coarse — the two sides may still have moved DIFFERENT FIELDS inside it. Descend one level into
# each overlapping child and re-classify. Only rows with a small overlap are drilled; a row where
# both sides rewrote a hundred ids is a wholesale decision either way.
param(
  [string]$Stage = 'D:\games\wz-stage\phaseB',
  [string]$Repo  = 'D:\games\MapleStory\Server\Cosmic\.claude\worktrees\evan-dualblade',
  [string]$Live  = 'D:\games\MapleStory',
  [string]$BaseV84 = 'D:\games\wz-stage\v84-base',
  [string]$BaseV83 = 'D:\games\MapleStory\Server\porting-resources\wz-data\v83-stock',
  [string[]]$Only,
  [int]$MaxOverlap = 4
)
$ErrorActionPreference = 'Stop'
$W = Join-Path $Repo 'docs\wz-baseline\tool-merge\bin\Release\net10.0-windows\WzMerge.exe'
function HashKids([string]$wz, [string]$path) {
  # A path present in one tree and not another is DATA here, not an error: `hash` prints
  # NOT FOUND on stderr and exits 1, which PS 5.1 would otherwise turn into a terminating error.
  $ErrorActionPreference = 'SilentlyContinue'
  $h = @{}
  foreach ($l in (& $W hash $wz $path 2>$null)) {
    $p = $l -split '\s+', 2
    if ($p.Count -eq 2 -and $p[1] -notlike 'TOTAL*') { $h[$p[1].Trim()] = $p[0] }
  }
  return $h
}
$rows = Import-Csv "$Stage\reports\CONFLICTS-136.tsv" -Delimiter "`t" |
        Where-Object { $_.verdict -eq 'OVERLAP' -and
                       ($_.overlapChildren -split ',').Count -le $MaxOverlap }
$out = @(); $sum = @{}
foreach ($r in $rows) {
  if ($Only -and $Only -notcontains $r.archive) { continue }
  $a = $r.archive
  $deepHis = @(); $deepTheirs = @(); $deepBoth = @()
  foreach ($c in ($r.overlapChildren -split ',')) {
    $p = "$($r.image)/$c"
    $hl = HashKids "$Live\$a.wz"    $p
    $h4 = HashKids "$BaseV84\$a.wz" $p
    $h3 = HashKids "$BaseV83\$a.wz" $p
    $keys = @(@($hl.Keys) + @($h4.Keys) + @($h3.Keys) | Sort-Object -Unique)
    if ($keys.Count -eq 0) { $deepBoth += $c; continue }   # a leaf: the collision is the value
    foreach ($k in $keys) {
      $hisM = ($hl[$k] -ne $h3[$k]); $theirM = ($h4[$k] -ne $h3[$k])
      if ($hisM   -and -not $theirM) { $deepHis    += "$c/$k" }
      if ($theirM -and -not $hisM)   { $deepTheirs += "$c/$k" }
      if ($hisM   -and $theirM)      { $deepBoth   += "$c/$k" }
    }
  }
  $v = if ($deepBoth.Count -eq 0) { 'DISJOINT-AT-FIELD' } else { 'TRUE-OVERLAP' }
  $sum["$($a)/$v"] = 1 + [int]$sum["$($a)/$v"]
  $out += [pscustomobject]@{
    archive = $a; image = $r.image; deepVerdict = $v
    hisOnlyFields    = ($deepHis    -join ',')
    v84OnlyFields    = ($deepTheirs -join ',')
    collidingFields  = ($deepBoth   -join ',')
  }
}
$dst = "$Stage\reports\CONFLICTS-drill.$($Only -join '-').tsv"
$out | Export-Csv $dst -Delimiter "`t" -NoTypeInformation -Encoding utf8
""
$sum.GetEnumerator() | Sort-Object Name | ForEach-Object { "  {0,-32} {1}" -f $_.Key, $_.Value }
"  drilled $($out.Count) -> $dst"
