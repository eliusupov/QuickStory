$ErrorActionPreference = "Stop"
$root = "D:\games\MapleStory\Server\Cosmic\.claude\worktrees\evan-dualblade\docs\wz-baseline\merge-lists"
$out  = "$root\composed"
New-Item -ItemType Directory -Force $out | Out-Null

$files = @{
  "Character" = @("04","05")
  "Item"      = @("04")
  "String"    = @("04","05","06","07","03f")
  "Morph"     = @("05")
  "Skill"     = @("05")
  "Map"       = @("06","07")
  "Mob"       = @("06","07")
  "Npc"       = @("06")
  "Reactor"   = @("06")
  "Sound"     = @("06")
}

$perFile = @{
  "Character" = "# 254 rows. 04's 246 cosmetics + 05's 8 mount sprites. Provably disjoint. --force is NOT`r`n# used on this file: every root in composed\FORCE.txt is a String.wz path. Expect 6 refusals`r`n# (see README)."
  "Item"      = "# 391 rows, ticket 04 alone. Expect 0 refusals, exit 0."
  "String"    = "# 471 rows. REQUIRES --force composed\FORCE.txt (38 roots). Expect 9 refusals (see README)."
  "Morph"     = "# 25 rows, ticket 05 alone. Expect 0 refusals, exit 0."
  "Skill"     = "# 27 rows, ticket 05 alone. The nine mount skills x 3 beginner jobs. Expect exit 0."
  "Map"       = "# 37 rows. 06's 34 (12 dependency rows FIRST, then 22 map images) then 07's 3.`r`n# THE ORDER INSIDE EACH TICKET BLOCK IS LOAD-BEARING - deps before the maps that draw them."
  "Mob"       = "# 21 rows. 06's 17 + 07's 4. Expect exit 0."
  "Npc"       = "# 6 rows, ticket 06 alone. Expect exit 0."
  "Reactor"   = "# 2 rows, ticket 06 alone. Expect exit 0."
  "Sound"     = "# 1 row, ticket 06 alone. Needs the post-03f verifier - see README."
}

foreach ($name in $files.Keys) {
  $lines = @()
  $lines += "# COMPOSED INSTALL LIST - $name.wz. Built by ticket 03f from the committed per-ticket"
  $lines += "# lists under ..\{04,05,06,07,03f}\ - those are the source of truth, this file is a"
  $lines += "# concatenation of them in ticket order with each block's internal order preserved."
  $lines += "# Every row is unique across the whole composed set and no row is an ancestor of"
  $lines += "# another (checked, all ten files). Regenerate rather than hand-edit."
  $lines += "#"
  $lines += ($perFile[$name] -split "`r`n")
  $lines += "#"
  $lines += "# Ticket 08 is in flight and owns Map.wz, Mob.wz, Npc.wz, Reactor.wz, Sound.wz and the"
  $lines += "# String.wz/{Map,Mob,Npc}.img path lists. It appends its own '# ==== ticket 08 ====' block"
  $lines += "# at the foot of the relevant files; nothing above it needs to move."
  $lines += "#"
  $lines += "# Run book: ..\..\..\work-plan\WZ-MERGE-PROCEDURE.md section 5, one file at a time."
  $lines += ""
  foreach ($t in $files[$name]) {
    $p = "$root\$t\$name.paths.txt"
    if (-not (Test-Path $p)) { continue }
    $rows = Get-Content $p | Where-Object { $_.Trim() -ne '' -and -not $_.Trim().StartsWith('#') } | ForEach-Object { $_.Trim() }
    $lines += "# ==== ticket $t ($($rows.Count) rows, order preserved from $t\$name.paths.txt) ===="
    $lines += $rows
    $lines += ""
  }
  $lines += "# ==== ticket 08 appends here ===="
  [System.IO.File]::WriteAllLines("$out\$name.paths.txt", [string[]]$lines)
  $n = ($lines | Where-Object { $_.Trim() -ne '' -and -not $_.Trim().StartsWith('#') }).Count
  "{0,-10} {1,4} rows" -f $name, $n
}
