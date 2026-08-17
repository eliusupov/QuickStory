# Content-verify the ADDITIVE rows (removed-set + protect-set): every landed row's own subtree
# in the output must equal the owner's client, and must be ABSENT from the v84 base (that is what
# "the owner has it and v84 does not" means, and it is the discriminator here — no absence, no
# evidence). Rows are grouped by parent so one `hash` invocation covers every row under it.
param(
  [string]$Stage = 'D:\games\wz-stage\phaseB',
  [string]$Repo  = 'D:\games\MapleStory\Server\Cosmic\.claude\worktrees\evan-dualblade',
  [string]$Live  = 'D:\games\MapleStory',
  [string[]]$Only,
  [int]$SampleParents = 0        # 0 = exhaustive; >0 = check only this many parents
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

$T = [ordered]@{ checked=0; match=0; mismatch=0; missing=0; vacuous=0; parents=0 }
$bad = @()
foreach ($f in (Get-ChildItem "$Stage\reports\landed\*.landed.txt" | Sort-Object Name)) {
  $a = $f.BaseName -replace '\.landed$',''
  if ($Only -and $Only -notcontains $a) { continue }
  $force = @{}
  $ff = "$Stage\lists\$a.force.txt"
  if (Test-Path $ff) { Get-Content $ff | Where-Object { $_ -notmatch '^\s*(#|$)' } | ForEach-Object { $force[($_ -split "`t")[0]] = $true } }

  $byParent = @{}
  foreach ($r in (Get-Content $f.FullName | Where-Object { $_ -notmatch '^\s*(#|$)' })) {
    if ($force[$r]) { continue }                       # forced images are verified separately
    $p = $r.Substring(0, $r.LastIndexOf('/')); $n = $r.Substring($r.LastIndexOf('/') + 1)
    if (-not $byParent[$p]) { $byParent[$p] = @() }
    $byParent[$p] += $n
  }
  $parents = $byParent.Keys | Sort-Object
  if ($SampleParents -gt 0 -and $parents.Count -gt $SampleParents) {
    $parents = $parents | Get-Random -Count $SampleParents -SetSeed 11
  }
  $n0 = $T.checked
  foreach ($p in $parents) {
    $T.parents++
    $ho = HashKids "$Stage\out\$a.wz" $p
    $hl = HashKids "$Live\$a.wz"      $p
    $hs = HashKids "$Stage\pre\$a.wz" $p
    foreach ($k in $byParent[$p]) {
      $T.checked++
      if (-not $ho.ContainsKey($k))          { $T.missing++;  $bad += "$a`t$p/$k`tMISSING-FROM-OUTPUT"; continue }
      if ($hs.ContainsKey($k))               { $T.vacuous++;  $bad += "$a`t$p/$k`tALREADY-IN-V84-BASE" }
      if ($ho[$k] -eq $hl[$k])               { $T.match++ }
      else                                   { $T.mismatch++; $bad += "$a`t$p/$k`tCONTENT-DIFFERS-FROM-OWNER" }
    }
  }
  Write-Host ("  {0,-10} parents={1,-5} rows checked={2}" -f $a, $parents.Count, ($T.checked - $n0))
}
""
"additive-row content verification"
$T.GetEnumerator() | ForEach-Object { "  {0,-10} {1}" -f $_.Key, $_.Value }
$o = "$Stage\reports\VERIFY-additive.tsv"
if ($bad.Count) { $bad | Set-Content $o -Encoding utf8; "  anomalies -> $o ($($bad.Count))" } else { "  no anomalies" }
