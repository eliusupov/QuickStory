# 33 — "any quest that exists in one of the three files but not the others, list".
# Two questions, kept apart because they answer different things:
#   (1) v84 itself: which of ITS quest ids are not in all three of QuestInfo/Check/Act.
#       This is what the ticket asks for and it is a property of the source, not of the merge.
#   (2) the merged tree: same question, so the pre-existing asymmetries this tree already had
#       are visible next to v84's and nobody blames the merge for them.
# Say.img is included only to state whether it agrees - Quest.java:116-118 never opens it.
#
# ponytail: hashtable, not an array of HashSets. `$a = foreach (...) { $someHashSet }` unrolls
# every set into its elements, and the first draft of this script then reported "QuestInfo=1"
# and 3,021 inconsistent ids. It was the script, not the data.
#
#   .\v84-crossimage.ps1 <scratchDirWithV84Dumps> <worktreeRoot>
param(
    [Parameter(Mandatory)][string]$Dumps,
    [Parameter(Mandatory)][string]$Root
)
$ErrorActionPreference = 'Stop'

function Ids($lines, [string]$rx) {
    $s = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($l in $lines) { if ($l -match $rx) { [void]$s.Add($Matches[1]) } }
    , $s
}

function Report([string]$label, [hashtable]$sets, [string[]]$names) {
    $union = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($n in $names) { foreach ($i in $sets[$n]) { [void]$union.Add($i) } }
    "$label"
    "  sizes: $(($names | ForEach-Object { "$_=$($sets[$_].Count)" }) -join '  ')   union=$($union.Count)"
    $odd = @(foreach ($id in $union) {
            $has = @(foreach ($n in $names) { if ($sets[$n].Contains($id)) { $n } })
            if ($has.Count -ne $names.Count) { [pscustomobject]@{ Id = $id; In = ($has -join ',') } }
        })
    "  ids NOT present in all $($names.Count): $($odd.Count)"
    $odd | Sort-Object { [long]$_.Id } | ForEach-Object { "    $($_.Id)  present in: $($_.In)" }
    ''
}

$three = @('QuestInfo', 'Check', 'Act')
$four = @('QuestInfo', 'Check', 'Act', 'Say')

$v84 = @{}
foreach ($i in $four) { $v84[$i] = Ids (Get-Content "$Dumps\v84-$i.txt") '^  (\S+) \[' }
Report 'v84 source - the three images Quest.java reads' $v84 $three
Report 'v84 source - plus Say.img, which Quest.java never opens' $v84 $four

$ours = @{}
foreach ($i in $three) { $ours[$i] = Ids (Get-Content "$Root\wz\Quest.wz\$i.img.xml") '^  <imgdir name="([^"]+)"' }
Report 'this tree, after the merge' $ours $three
