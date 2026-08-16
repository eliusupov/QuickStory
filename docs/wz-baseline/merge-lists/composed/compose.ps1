$ErrorActionPreference = "Stop"
$root = "D:\games\MapleStory\Server\Cosmic\.claude\worktrees\evan-dualblade\docs\wz-baseline\merge-lists"
$out  = "$root\composed"
New-Item -ItemType Directory -Force $out | Out-Null

$files = @{
  "Character" = @("04","05")
  "Item"      = @("04")
  "String"    = @("04","05","06","07","03f","08")
  "Morph"     = @("05")
  "Skill"     = @("05")
  "Map"       = @("06","07","08")
  "Mob"       = @("06","07","08")
  "Npc"       = @("06","08")
  "Reactor"   = @("06","08")
  "Sound"     = @("06","08")
  "Quest"     = @("09")
}

$perFile = @{
  "Character" = "# 254 rows. 04's 246 cosmetics + 05's 8 mount sprites. Provably disjoint. --force is NOT`r`n# used on this file: every root in composed\FORCE.txt is a String.wz path. Expect 6 refusals`r`n# (see README)."
  "Item"      = "# 391 rows, ticket 04 alone. Expect 0 refusals, exit 0."
  "String"    = "# 510 rows. REQUIRES --force composed\FORCE.txt (41 roots - 38 + ticket 08's 3).`r`n# Reusing the 38-root file silently reverts 08's three forced names. Expect 9 refusals."
  "Morph"     = "# 25 rows, ticket 05 alone. Expect 0 refusals, exit 0."
  "Skill"     = "# 27 rows, ticket 05 alone. The nine mount skills x 3 beginner jobs. Expect exit 0."
  "Map"       = "# 132 rows. 06's 34 (12 dependency rows FIRST, then 22 map images), 07's 3, 08's 95`r`n# (67 asset rows, then 22 map images, then 6 route rows that append onto EXISTING v83 maps).`r`n# THE ORDER INSIDE EACH TICKET BLOCK IS LOAD-BEARING - deps before the maps that draw them.`r`n# 08's twelve UNSAFE route rows are not here and are on COLLISION-DENY.txt; the tool refuses`r`n# that shape structurally as well (POSITIONAL ARRAY, see WZ-MERGE-PROCEDURE.md 4.4)."
  "Mob"       = "# 28 rows. 06's 17 + 07's 4 + 08's 7. Expect exit 0."
  "Npc"       = "# 15 rows. 06's 6 + 08's 9. Expect exit 0."
  "Reactor"   = "# 3 rows. 06's 2 + 08's 1. Expect exit 0."
  "Sound"     = "# 24 rows. 06's 1 (Bgm14.img/DragonRider) + 08's 23 Mob.img SFX banks. Needs the`r`n# post-03f verifier, which discounts BgmGL.img - unparseable in all three trees. Expect exit 0."
  "Quest"     = "# 252 rows, ticket 09 alone - 63 quest ids x Act/Check/QuestInfo/Say. No other ticket's`r`n# path list contains a single Quest.wz row (checked mechanically), so this file is 09's block`r`n# and nothing else. The 540 rows for the 135 22xxx Evan ids are ticket 13's and are NOT here.`r`n# 09 contributes no force rows. Expect 0 refusals, exit 0.`r`n#`r`n# The 132 add-list rows that write INTO live quests are on COLLISION-DENY.txt (03h), and the`r`n# positional-array gate refuses 123 of them structurally as well. Do not re-add them."
}

# Rows the COMPOSITION needs that no ticket's list carries. Not a place to smuggle content in:
# every row here must already be on `docs\wz-baseline\add-list\`, must be a pure addition, and
# must have a reason recorded. One entry so far.
#
# `WzMerge deps` resolves the assets a map REFERENCES, so it emitted Obj/effect.img/quest/gate/7
# for ticket 08's map and stopped there. v84 appends BOTH 6 and 7 to that array (add-list/Map.txt
# lines 581-582) and the live client has 0..5, so taking 7 alone leaves the array 0-5,7. The
# positional-array gate refuses that hole (WZ-MERGE-PROCEDURE.md 4.4); supplying 6 closes it.
$fill = @{
  "Map" = @("Map.wz/Obj/effect.img/quest/gate/6")
}

foreach ($name in $files.Keys) {
  $lines = @()
  $lines += "# COMPOSED INSTALL LIST - $name.wz. Built by 03f; 08 folded in by 03g, 09 by 03h,"
  $lines += "# from the committed per-ticket lists under ..\{04,05,06,07,03f,08,09}\ - those are the"
  $lines += "# source of truth, this file is a concatenation of them in ticket order with each"
  $lines += "# block's internal order preserved. Every row is unique across the whole composed set"
  $lines += "# and no row is an ancestor of another (checked, all eleven files). Regenerate, never edit."
  $lines += "#"
  $lines += ($perFile[$name] -split "`r`n")
  $lines += "#"
  $lines += "# Ticket 09 is folded in (03h): it owns Quest.wz outright and touches no other file, so"
  $lines += "# every list except Quest.paths.txt is byte-identical to 03g's. FORCE.txt stays at 41"
  $lines += "# roots, all String.wz - 09 forces nothing. Ticket 13 (Evan, the 540 22xxx rows) is next;"
  $lines += "# add `"13`" to the Quest entry of compose.ps1's `$files table and re-run."
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
  if ($fill.ContainsKey($name)) {
    $lines += "# ==== composition fill ($($fill[$name].Count) row(s)) - rows no ticket claimed that the"
    $lines += "#      composition NEEDS. Each is an add-list row, a pure addition, and is here"
    $lines += "#      because leaving it out puts a HOLE in a positional array (see below)."
    foreach ($r in $fill[$name]) { $lines += $r }
    $lines += ""
  }
  $lines += "# ==== ticket 13 appends here ===="
  [System.IO.File]::WriteAllLines("$out\$name.paths.txt", [string[]]$lines)
  $n = ($lines | Where-Object { $_.Trim() -ne '' -and -not $_.Trim().StartsWith('#') }).Count
  "{0,-10} {1,4} rows" -f $name, $n
}
