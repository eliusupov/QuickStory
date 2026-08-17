# Assemble the deliverable: a complete v84 client WZ tree carrying the owner's content.
#   tree\ = the v84 stock base, with the 12 merged archives laid over it,
#           plus EzorsiaV2_UI.wz which exists only in his client and has no stock counterpart.
# Nothing is installed. This writes only under $Stage.
param(
  [string]$Stage = 'D:\games\wz-stage\phaseB',
  [string]$Base  = 'D:\games\wz-stage\v84-base',
  [string]$Live  = 'D:\games\MapleStory'
)
$ErrorActionPreference = 'Stop'
$tree = Join-Path $Stage 'tree'
New-Item -ItemType Directory -Force -Path $tree | Out-Null

Copy-Item "$Base\*.wz" $tree -Force                       # 17 stock archives
Copy-Item "$Stage\out\*.wz" $tree -Force                  # 12 merged, over the stock copy
Copy-Item "$Live\EzorsiaV2_UI.wz" $tree -Force            # live-only, no stock baseline (ticket 30)

$man = Join-Path $Stage 'reports\TREE-MANIFEST.tsv'
"file`tbytes`tsha256`tsource" | Set-Content $man -Encoding utf8
foreach ($f in (Get-ChildItem "$tree\*.wz" | Sort-Object Name)) {
  $src = if (Test-Path "$Stage\out\$($f.Name)") { 'merged'    }
         elseif ($f.Name -eq 'EzorsiaV2_UI.wz') { 'live-only' }
         else                                   { 'v84-stock' }
  "{0}`t{1}`t{2}`t{3}" -f $f.Name, $f.Length, (Get-FileHash $f -Algorithm SHA256).Hash, $src |
    Add-Content $man -Encoding utf8
}
Get-Content $man
