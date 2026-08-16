# Dump the stock v84 base tree to flat TSVs for parity comparison against the server's
# extracted wz/ XML tree.
#
#   powershell -File tools\parity\dump-v84.ps1 -OutDir <dir> [-V84 D:\games\wz-stage\v84-base]
#
# ponytail: no incremental logic, no caching. The whole sweep is ~12 minutes and the inputs
# never change (v84-base is hash-pinned by docs/wz-baseline/backport/v84-base-tree.sha256),
# so "delete the dir and re-run" is the only recovery path anyone needs.
param(
  [Parameter(Mandatory=$true)][string]$OutDir,
  [string]$V84 = 'D:\games\wz-stage\v84-base',
  [string]$Exe = "$PSScriptRoot\WzValues\bin\Release\net10.0-windows\WzValues.exe"
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path $Exe)) { throw "build first: dotnet build -c Release --project $PSScriptRoot\WzValues" }
New-Item -ItemType Directory -Force $OutDir | Out-Null

# wz file, depth, path regex (empty = keep everything). The regex only filters what is
# PRINTED; the walk always reaches the stated depth.
$jobs = @(
  # equip ids are image names: Character.wz/Cap/01002000.img
  @('Character.wz', 0, '\.img$'),
  # item ids are sub-nodes of a bucket image: Item.wz/Consume/0200.img/02000000
  # (Item.wz/Pet holds one image per id, caught by the same \.img$ alternative)
  @('Item.wz',      3, '(\.img$|\.img/[0-9]+$|\.img/[0-9]+/info/(price|slotMax|tuc|reqLevel|cash|only|quest|timeLimited)$)'),
  # every scalar under Mob.wz/<id>.img/info - the rebalance question
  @('Mob.wz',       2, '(\.img$|\.img/info/[^/]+$)'),
  @('Npc.wz',       1, '(\.img$|\.img/info/[^/]+$)'),
  @('Reactor.wz',   0, '\.img$'),
  # map life placements: Map.wz/Map/Map1/100000000.img/life/0/{id,type}
  @('Map.wz',       3, '(Map/Map[0-9]/[0-9]+\.img$|\.img/life/[0-9]+/(id|type|hide)$|\.img/info/(mapName|link|town|returnMap)$)'),
  @('String.wz',    3, ''),
  @('Etc.wz',       3, ''),
  @('Quest.wz',     3, '')
)

foreach ($j in $jobs) {
  $wz, $depth, $rx = $j
  $src = Join-Path $V84 $wz
  $dst = Join-Path $OutDir ("v84." + ($wz -replace '\.wz$','') + ".tsv")
  if (-not (Test-Path $src)) { Write-Host "SKIP (missing): $src"; continue }
  Write-Host "== $wz depth=$depth"
  & $Exe $src $dst $depth $rx
  if ($LASTEXITCODE -ne 0) { throw "$wz failed with $LASTEXITCODE" }
}
Write-Host "done -> $OutDir"
