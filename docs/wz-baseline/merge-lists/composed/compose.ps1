$ErrorActionPreference = "Stop"
# 03i: was hard-coded to a worktree path, so it could not run from master after that branch
# merged. This script lives in <repo>\docs\wz-baseline\merge-lists\composed, so derive both.
$root = Split-Path -Parent $PSScriptRoot
$out  = $PSScriptRoot
New-Item -ItemType Directory -Force $out | Out-Null

$files = @{
  "Character" = @("04","05")
  "Item"      = @("04")
  "String"    = @("04","05","06","07","03f","08","10")
  "Morph"     = @("05")
  "Skill"     = @("05","10")
  "Map"       = @("06","07","08")
  "Mob"       = @("06","07","08")
  "Npc"       = @("06","08")
  "Reactor"   = @("06","08")
  "Sound"     = @("06","08")
  "Quest"     = @("09")
  "Etc"       = @("10")
  "UI"        = @("10")
}

$perFile = @{
  "Character" = "# 254 rows. 04's 246 cosmetics + 05's 8 mount sprites. Provably disjoint. --force is NOT`r`n# used on this file: every root in composed\FORCE.txt is a String.wz path. Expect 6 refusals`r`n# (see README)."
  "Item"      = "# 391 rows, ticket 04 alone. Expect 0 refusals, exit 0."
  "String"    = "# 580 rows. REQUIRES --force composed\FORCE.txt, 41 roots = COLLISION-FORCE.txt's 37`r`n# + 03f's Npc.img/9201144 + ticket 08's 3. (The old wording here said '38 + 08's 3', which is 41`r`n# by luck and 37+3=40 by arithmetic; 03f's row was the one going unnamed.) Reusing the 37-root`r`n# COLLISION-FORCE.txt silently reverts four forced names. Expect 9 refusals.`r`n# Ticket 10 adds 70 Skill.img rows (Evan) and forces NOTHING - the 18 Evan Eqp/Dragon and`r`n# Eqp/Taming names it would otherwise want are ticket 05's force roots, already on FORCE.txt."
  "Morph"     = "# 25 rows, ticket 05 alone. Expect 0 refusals, exit 0."
  "Skill"     = "# 39 rows. 05's 27 (the nine mount skills x 3 beginner jobs) + 10's 12 Evan roots:`r`n# 2001.img, the ten job images 2200/2210-2218, and Skill.wz/Dragon - a whole new WzDirectory,`r`n# the only one in the whole composition. Two consequences, both expected, neither a fault:`r`n#   binary side: a WzDirectory row is DeepCloned and is NOT content-checked (PROCEDURE 5.4).`r`n#                Ticket 10 covered that gap another way - 'WzMerge hash' on the merged Dragon`r`n#                directory equals v84's own digest exactly.`r`n#   XML side:    'WzMerge xml' refuses it - a directory row has no .img segment and cannot map`r`n#                to an XML file - so the Skill.wz XML run is 'added 38, refused 1', EXIT 3.`r`n#                Right refusal: wz\Skill.wz\ is flat .img.xml, the server never reads Dragon."
  "Map"       = "# 133 rows: 132 from the tickets plus 1 composition fill (see the bottom of the file).`r`n# 06's 34 (12 dependency rows FIRST, then 22 map images), 07's 3, 08's 95`r`n# (67 asset rows, then 22 map images, then 6 route rows that append onto EXISTING v83 maps).`r`n# THE ORDER INSIDE EACH TICKET BLOCK IS LOAD-BEARING - deps before the maps that draw them.`r`n# 08's twelve UNSAFE route rows are not here and are on COLLISION-DENY.txt; the tool refuses`r`n# that shape structurally as well (POSITIONAL ARRAY, see WZ-MERGE-PROCEDURE.md 4.4)."
  "Mob"       = "# 28 rows. 06's 17 + 07's 4 + 08's 7. Expect exit 0."
  "Npc"       = "# 15 rows. 06's 6 + 08's 9. Expect exit 0."
  "Reactor"   = "# 3 rows. 06's 2 + 08's 1. Expect exit 0."
  "Sound"     = "# 24 rows. 06's 1 (Bgm14.img/DragonRider) + 08's 23 Mob.img SFX banks. Needs the`r`n# post-03f verifier, which discounts BgmGL.img - unparseable in all three trees. Expect exit 0."
  "Quest"     = "# 252 rows, ticket 09 alone - 63 quest ids x Act/Check/QuestInfo/Say. No other ticket's`r`n# path list contains a single Quest.wz row (checked mechanically), so this file is 09's block`r`n# and nothing else. The 540 rows for the 135 22xxx Evan ids are ticket 13's and are NOT here.`r`n# 09 contributes no force rows. Expect 0 refusals, exit 0.`r`n#`r`n# The 132 add-list rows that write INTO live quests are on COLLISION-DENY.txt (03h), and the`r`n# positional-array gate refuses 123 of them structurally as well. Do not re-add them."
  "Etc"       = "# 4 rows, ticket 10 alone - MakeCharInfo.img's Evan block (add-list/Etc.txt:10481-10484).`r`n# Etc.wz enters the composition here for the first time: ticket 04 declined ALL 10,634 of its`r`n# add-list roots (10,459 Commodity Bonus fields, 1,518 dead shop SNs) and left this block to the`r`n# Evan branch. It is creation-UI DATA; the creation FLOW is ticket 15. The server reads it -`r`n# MakeCharInfoValidator:17-23 builds one MakeCharInfo per creatable class out of this image.`r`n# Expect 0 refusals, exit 0."
  "UI"        = "# 2 rows, ticket 10 alone. UI.wz enters the composition here for the first time, and is`r`n# emphatically NOT a bulk import: WZ-MERGE-PROCEDURE.md 11 keeps UI.wz out of scope with exactly`r`n# one stated exception, 'take SkillEx / SkillMacroEx only, never bulk'. This list IS that`r`n# exception and nothing else - 59 of the 61 UI.wz add-list roots are left, including every`r`n# Login.img RaceSelect/BtEvan + NewCharEvan row (ticket 15's) and Equip/{Bt,}DragonEquip, which`r`n# ticket 10 first took and then gave back to ticket 14 on review: criterion 4 is 'spawns, follows,`r`n# and moves', not 'can be equipped', so nothing in 10 earned them. Expect 0 refusals, exit 0."
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

$total = 0
foreach ($name in $files.Keys) {
  $lines = @()
  $lines += "# COMPOSED INSTALL LIST - $name.wz. Built by 03f; 08 folded in by 03g, 09 by 03h, 10 by 10."
  $lines += "# Source of truth is the committed per-ticket lists under ..\{04,05,06,07,03f,08,09,10}\;"
  $lines += "# this file is a concatenation of them in ticket order with each block's internal order"
  $lines += "# preserved. Every row is unique across the whole composed set and no row is an ancestor"
  $lines += "# of another (checked: 1,750 rows across all THIRTEEN files). Regenerate, never edit."
  $lines += "#"
  $lines += ($perFile[$name] -split "`r`n")
  $lines += "#"
  $lines += "# Ticket 10 is folded in (the Evan tracer): +12 Skill, +70 String, +4 Etc, +2 UI = 88 rows,"
  $lines += "# bringing Etc.wz and UI.wz into the composition for the first time. It forces nothing, so"
  $lines += "# FORCE.txt stays at 41 roots, all String.wz. The other nine lists are unchanged in content."
  $lines += "# Ticket 13 (Evan's world, the 540 22xxx Quest rows and the 29 Evan map images) is next;"
  $lines += "# add `"13`" to the Quest and Map entries of compose.ps1's `$files table and re-run."
  $lines += "#"
  $lines += "# Run book: ..\..\..\work-plan\WZ-MERGE-PROCEDURE.md section 5, one file at a time."
  $lines += ""
  foreach ($t in $files[$name]) {
    $p = "$root\$t\$name.paths.txt"
    # 03i: `continue` on a missing file used to be silent, so a renamed or unmerged ticket
    # directory dropped its whole block and the composed list still looked plausible. Not every
    # ticket touches every .wz, so absence is legal - but it has to be visible.
    if (-not (Test-Path $p)) { Write-Host ("  no {0}\{1}.paths.txt - ticket {1} contributes nothing to {2}" -f $t, $name, $name); continue }
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

  # Ticket 10: every emitted row MUST be rooted at this file. Sounds tautological; it is not.
  # A `n inside a double-quoted $perFile string is a NEWLINE, so a comment block that used a
  # backtick as a quote mark silently broke in two and shipped its own second half as a manifest
  # row - which WzMerge then reported as "row is rooted at <prose>, not Skill.wz". The composed
  # list stayed plausible and the merge stayed exit 3. Cheaper to assert the invariant than to
  # ban backticks by convention.
  $emitted = $lines | Where-Object { $_.Trim() -ne '' -and -not $_.Trim().StartsWith('#') }
  $stray = $emitted | Where-Object { -not $_.StartsWith("$name.wz/") }
  if ($stray) { throw "$name.paths.txt has $($stray.Count) row(s) not rooted at $name.wz/ - a header comment leaked into the manifest:`r`n  " + ($stray -join "`r`n  ") }

  [System.IO.File]::WriteAllLines("$out\$name.paths.txt", [string[]]$lines)
  $n = $emitted.Count
  "{0,-10} {1,4} rows" -f $name, $n
  $total += $n
}

# 03i: the one number that catches a silently-dropped block. Bump it in the same commit that adds
# a ticket to $files - never to make this line pass.
#   1662  tickets 04-09 (03i), what WZ-MERGE-PROCEDURE.md 4.4 and the composed README were written
#         against
# + 88    ticket 10 (Skill 12, String 70, Etc 4, UI 2)
# = 1750
$expect = 1750
if ($total -ne $expect) { throw "composed total is $total rows, expected $expect. A ticket block is missing, or one was added without updating `$expect." }
"{0,-10} {1,4} rows TOTAL (asserted)" -f "ALL", $total
