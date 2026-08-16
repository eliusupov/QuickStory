# 33 — derive the additive path list: v84 quest ids MINUS the ids this tree already has.
# ponytail: no new merge engine. WzMerge already has an `xml` mode with the additive gate and
# the right serializer (it is what ticket 09 used); this only computes its <pathsFile>.
#
#   .\ids.ps1 <scratchDirWithV84Dumps> <worktreeRoot> <outDir>
param(
    [Parameter(Mandatory)][string]$Dumps,
    [Parameter(Mandatory)][string]$Root,
    [Parameter(Mandatory)][string]$Out
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force $Out | Out-Null

# PS 5.1 trap: Set-Content -Encoding utf8 writes a BOM. PowerShell strips it again on read, so
# nothing here notices - but Java does not, and V84EvanQuestDataTest read the first path row as
# "﻿Quest.wz/QuestInfo.img/22000". Everything this script emits goes out BOM-less.
$U8 = New-Object System.Text.UTF8Encoding $false
function Emit([string]$path, $lines) { [IO.File]::WriteAllLines($path, [string[]]@($lines), $U8) }

$images = 'QuestInfo', 'Check', 'Act'

function V84Ids([string]$img) {
    # `WzMerge dump <wz> <Img>.img 1` prints two header lines then "  <id> [WzSubProperty]".
    $ids = @()
    foreach ($l in Get-Content "$Dumps\v84-$img.txt") {
        if ($l -match '^  (\S+) \[') { $ids += $Matches[1] }
    }
    $ids
}

function OurIds([string]$img) {
    # Top level of the server XML is exactly one <imgdir> per quest id at 2-space indent.
    $ids = @()
    foreach ($l in Get-Content "$Root\wz\Quest.wz\$img.img.xml") {
        if ($l -match '^  <imgdir name="([^"]+)"') { $ids += $Matches[1] }
    }
    $ids
}

$rows = @()
$report = @()
$v84All = @{}
$ourAll = @{}
foreach ($img in $images) {
    $v84 = V84Ids $img
    $our = OurIds $img
    $v84All[$img] = [System.Collections.Generic.HashSet[string]]::new([string[]]$v84)
    $ourAll[$img] = [System.Collections.Generic.HashSet[string]]::new([string[]]$our)
    if ($v84.Count -ne $v84All[$img].Count) { throw "$img : duplicate id in v84 dump" }
    if ($our.Count -ne $ourAll[$img].Count) { throw "$img : duplicate id in server XML" }

    $new = $v84 | Where-Object { -not $ourAll[$img].Contains($_) }
    $gone = $our | Where-Object { -not $v84All[$img].Contains($_) }
    $report += "$img : v84=$($v84.Count) ours=$($our.Count) toAdd=$($new.Count) oursNotInV84=$($gone.Count)"
    foreach ($id in $new) { $rows += "Quest.wz/$img.img/$id" }
    Emit "$Out\$img.new-ids.txt" $new
    Emit "$Out\$img.ours-not-in-v84.txt" $gone
}

Emit "$Out\Quest.paths.txt" $rows

# Cross-image consistency of the ids we are ADDING: v84 itself is not uniform, and
# hasScriptRequirement keys off Check alone, so an id present in QuestInfo but not Check
# is a real behavioural difference, not a cosmetic one. Report it rather than invent a node.
$union = [System.Collections.Generic.HashSet[string]]::new()
foreach ($img in $images) { foreach ($id in $v84All[$img]) { if (-not $ourAll[$img].Contains($id)) { [void]$union.Add($id) } } }
$incon = foreach ($id in $union) {
    $has = foreach ($img in $images) { if ($v84All[$img].Contains($id)) { $img } }
    if ($has.Count -ne 3) { "$id present in: $($has -join ',')" }
}
Emit "$Out\INCONSISTENT.txt" @($incon)
$report += "path rows: $($rows.Count)"
$report += "ids added (union): $($union.Count)"
$report += "ids not present in all three v84 images: $(@($incon).Count)"
Emit "$Out\SUMMARY.txt" $report          # Emit, not Tee-Object: Tee is the one write that would bypass $U8
$report
