# The 136 three-way CONFLICT images: v84 edited the image AND so did the owner.
# For each, digest its DIRECT CHILDREN in all three trees and say which children each side moved.
#   his    = child differs live vs v83-stock
#   theirs = child differs v84  vs v83-stock
# DISJOINT  -> the two edits are in different children; keeping both is possible (child-level merge)
# OVERLAP   -> the same child moved on both sides; a genuine decision, no mechanical answer
# SAME-EDIT -> both sides made the identical change; nothing to decide
param(
  [string]$Stage = 'D:\games\wz-stage\phaseB',
  [string]$Repo  = 'D:\games\MapleStory\Server\Cosmic\.claude\worktrees\evan-dualblade',
  [string]$Live  = 'D:\games\MapleStory',
  [string]$BaseV84 = 'D:\games\wz-stage\v84-base',
  [string]$BaseV83 = 'D:\games\MapleStory\Server\porting-resources\wz-data\v83-stock',
  [string[]]$Only
)
$ErrorActionPreference = 'Stop'
$W = Join-Path $Repo 'docs\wz-baseline\tool-merge\bin\Release\net10.0-windows\WzMerge.exe'

function HashKids([string]$wz, [string]$path) {
  $h = @{}
  foreach ($l in (& $W hash $wz $path 2>$null)) {
    $p = $l -split '\s+', 2
    if ($p.Count -eq 2 -and $p[1] -notlike 'TOTAL*') { $h[$p[1].Trim()] = $p[0] }
  }
  return $h
}

$conf = Import-Csv "$Stage\lists\MANIFEST.tsv" -Delimiter "`t" |
        Where-Object { $_.set -eq 'live-edited-CONFLICT' }
$out = @(); $sum = @{}
foreach ($g in ($conf | Group-Object archive)) {
  if ($Only -and $Only -notcontains $g.Name) { continue }
  $a = $g.Name
  foreach ($r in $g.Group) {
    $img = $r.path
    $hl = HashKids "$Live\$a.wz"     $img
    $h4 = HashKids "$BaseV84\$a.wz"  $img
    $h3 = HashKids "$BaseV83\$a.wz"  $img
    $keys = @(@($hl.Keys) + @($h4.Keys) + @($h3.Keys) | Sort-Object -Unique)
    $his = @(); $theirs = @()
    foreach ($k in $keys) {
      if ($hl[$k] -ne $h3[$k]) { $his    += $k }
      if ($h4[$k] -ne $h3[$k]) { $theirs += $k }
    }
    $both = @($his | Where-Object { $theirs -contains $_ })
    $same = @($both | Where-Object { $hl[$_] -eq $h4[$_] })
    $verdict = if ($both.Count -eq 0)                  { 'DISJOINT'  }
               elseif ($same.Count -eq $both.Count)    { 'SAME-EDIT' }
               else                                    { 'OVERLAP'   }
    $sum[$verdict] = 1 + [int]$sum[$verdict]
    $out += [pscustomobject]@{
      archive = $a; image = $img; verdict = $verdict
      hisChildren = ($his -join ','); v84Children = ($theirs -join ',')
      overlapChildren = ($both -join ','); identicalOverlap = ($same -join ',')
    }
  }
  Write-Host ("  {0,-10} {1} image(s)" -f $a, $g.Count)
}
$dst = "$Stage\reports\CONFLICTS-136.tsv"
if (Test-Path $dst) { $out | Export-Csv "$dst.part" -Delimiter "`t" -NoTypeInformation -Encoding utf8 }
else                { $out | Export-Csv $dst        -Delimiter "`t" -NoTypeInformation -Encoding utf8 }
""
$sum.GetEnumerator() | Sort-Object Name | ForEach-Object { "  {0,-10} {1}" -f $_.Key, $_.Value }
"  total     $($out.Count)"
