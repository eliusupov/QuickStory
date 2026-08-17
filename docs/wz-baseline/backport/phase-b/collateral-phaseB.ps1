# "Only the named rows changed" — acceptance criterion 1, executed rather than asserted.
#
# Digest the v84 base and the merged output at the archive root, then descend ONLY where the
# digests differ, until image level. Everything that never differs is proven untouched by one
# comparison, so the cost is bounded by what the merge actually moved.
# Finally: every changed image must be one the manifest named. Anything else is collateral.
param(
  [string]$Stage = 'D:\games\wz-stage\phaseB',
  [string]$Repo  = 'D:\games\MapleStory\Server\Cosmic\.claude\worktrees\evan-dualblade',
  [string[]]$Only
)
$ErrorActionPreference = 'Stop'
$W = Join-Path $Repo 'docs\wz-baseline\tool-merge\bin\Release\net10.0-windows\WzMerge.exe'

function HashKids([string]$wz, [string]$path) {
  $h = [ordered]@{}
  foreach ($l in (& $W hash $wz $path)) {
    $p = $l -split '\s+', 2
    if ($p.Count -eq 2 -and $p[1] -notlike 'TOTAL*') { $h[$p[1].Trim()] = $p[0] }
  }
  return $h
}

$report = @()
foreach ($f in (Get-ChildItem "$Stage\lists\*.paths.txt" | Sort-Object Name)) {
  $a = $f.BaseName -replace '\.paths$',''
  if ($Only -and $Only -notcontains $a) { continue }
  $named = @{}
  foreach ($r in (Get-Content $f.FullName | Where-Object { $_ -notmatch '^\s*(#|$)' })) {
    # the image a row lands in: the first segment ending .img, else the row itself
    $seg = $r -split '/'; $img = $null
    for ($i = 0; $i -lt $seg.Count; $i++) { if ($seg[$i] -like '*.img') { $img = ($seg[0..$i] -join '/'); break } }
    if (-not $img) { $img = $r }
    $named[$img] = $true
  }

  $pre = "$Stage\pre\$a.wz"; $out = "$Stage\out\$a.wz"
  $changed = @(); $probes = 0
  $queue = New-Object System.Collections.Queue
  $queue.Enqueue("$a.wz")
  while ($queue.Count) {
    $p = $queue.Dequeue(); $probes += 2
    $hp = HashKids $pre $p
    $ho = HashKids $out $p
    foreach ($k in $ho.Keys) {
      $child = "$p/$k"
      if (-not $hp.Contains($k)) { $changed += "$child`tNEW"; continue }
      if ($hp[$k] -eq $ho[$k])   { continue }          # subtree proven identical, stop here
      if ($k -like '*.img')      { $changed += "$child`tCHANGED" }
      else                       { $queue.Enqueue($child) }
    }
    foreach ($k in $hp.Keys) { if (-not $ho.Contains($k)) { $changed += "$p/$k`tDISAPPEARED" } }
  }

  $collateral = @($changed | Where-Object { $named[(($_ -split "`t")[0])] -ne $true })
  $gone       = @($changed | Where-Object { $_ -like "*`tDISAPPEARED" })
  Write-Host ("  {0,-10} probes={1,-4} changed images={2,-5} collateral={3} disappeared={4}" -f `
              $a, $probes, $changed.Count, $collateral.Count, $gone.Count)
  $report += "$a`t$probes`t$($changed.Count)`t$($collateral.Count)`t$($gone.Count)"
  if ($collateral.Count) { $collateral | Select-Object -First 20 | ForEach-Object { "      COLLATERAL $_" } }
  $changed | Set-Content "$Stage\reports\$a.changed-images.txt" -Encoding utf8
}
""
"archive`tprobes`tchangedImages`tcollateral`tdisappeared"
$report
$report | Set-Content "$Stage\reports\VERIFY-collateral.tsv" -Encoding utf8
