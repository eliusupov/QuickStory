# 13 - prove that NOTHING pre-existing in wz/ changed.
#
#   .\verify.ps1 <worktreeRoot> <scratchDir> [-Rev <baseline>]
#
# -Rev defaults to HEAD, which is correct only BEFORE this ticket's merge is committed. To
# re-verify the delivered commit, pass the commit before it:  -Rev <deliveredSha>~1
#
# Ticket 13 writes into wz/ in exactly two shapes:
#   (a) 44 whole new .img.xml files - structurally incapable of changing anything pre-existing,
#       but their EXISTENCE still has to be measured, or "44 new files" is an assertion;
#   (b) 21 spliced nodes into TWO files that already existed: String.wz/Map.img.xml (18 map
#       names under <imgdir name="etc">) and String.wz/Npc.img.xml (3 NPC names at the root).
# (b) is the only place a pre-existing node could have been damaged, and an empty conflicts
# list is not evidence about it - a write INTO an existing record produces an empty conflicts
# list too. So there are two proofs and neither is quoted alone:
#
#   PROOF 1 (whole tree, byte level). Recompute the git blob SHA-1 of every one of the ~22.4k
#     files wz/ held at the baseline, from the bytes on disk, and compare with the SHA the
#     baseline commit records. This does NOT ask `git diff`, which may answer from the index's
#     stat cache; it re-reads and re-hashes every file. Expected: exactly the two files in (b)
#     differ, nothing is missing, and the untracked set is exactly the 44 files of (a).
#
#   PROOF 2 (those two files, node by node). Parse both files and compare a canonical,
#     whitespace-independent digest of every pre-existing id's whole subtree against the
#     baseline blob. Catches a value edit, an added child, a removed child or a reordered child
#     inside any record that already existed - none of which PROOF 1 can localise and none of
#     which the merge's own conflicts list can see at all. For Map.img.xml this runs at BOTH
#     levels: the 12 top-level street categories, and then every id inside `etc`.
#
# Both proofs are demonstrated able to FAIL before either result is believed - see the
# self-checks at the bottom. PROOF 1's self-check mutates a real pre-existing file on disk and
# restores it in a finally block, verifying the restore by hash.
#
# Instrument traps inherited from merge-lists/33/verify.ps1, all still live here:
#   * core.autocrlf=true: the blob is LF, the working tree is CRLF. PROOF 1 uses
#     `git hash-object --stdin-paths`, which applies the same clean filter git would on
#     check-in, so the two SHAs are comparable; a raw SHA-1 of the disk bytes would report
#     every text file as changed. PROOF 2 converts the blob LF -> CRLF instead.
#   * PS 5.1: Set-Content -Encoding utf8 writes a BOM and Get-Content guesses ANSI on a
#     BOM-less UTF-8 file. Everything here is [IO.File] with an explicit no-BOM UTF8.
#   * cmd eats '^', so `<rev>^:path` silently returns the wrong blob. -Rev is resolved to a
#     full SHA by git first and only the SHA is handed onward.
param(
    [Parameter(Mandatory)][string]$Root,
    [Parameter(Mandatory)][string]$Scratch,
    [string]$Rev = 'HEAD'
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force $Scratch | Out-Null
$U8 = New-Object System.Text.UTF8Encoding $false
$fail = 0

$sha = (& git -C $Root rev-parse --verify "$Rev^{commit}").Trim()
if ($LASTEXITCODE -ne 0 -or $sha -notmatch '^[0-9a-f]{40}$') { throw "cannot resolve -Rev '$Rev'" }
"baseline: $Rev -> $sha"

# The two files this ticket splices into, and therefore the only two PROOF 1 may find changed.
$SPLICED = @('wz/String.wz/Map.img.xml', 'wz/String.wz/Npc.img.xml')

# ------------------------------------------------------------------ PROOF 1
# path -> blob sha recorded by the baseline commit, for everything under wz/.
function BaselineBlobs([string]$rev) {
    $map = New-Object 'System.Collections.Generic.Dictionary[string,string]' ([StringComparer]::Ordinal)
    foreach ($line in (& git -C $Root ls-tree -r $rev -- wz/)) {
        # <mode> SP blob SP <sha> TAB <path>
        if ($line -match '^\d+ blob ([0-9a-f]{40})\t(.+)$') { $map[$Matches[2]] = $Matches[1] }
    }
    , $map
}

# Recompute each path's blob sha FROM DISK, through git's own check-in filters.
# One `git hash-object` process for all of them; order of output == order of input.
function DiskBlobs([string[]]$paths, [string]$tag) {
    $listFile = Join-Path $Scratch "paths-$tag.txt"
    [IO.File]::WriteAllText($listFile, (($paths -join "`n") + "`n"), $U8)
    $out = Join-Path $Scratch "sha-$tag.txt"
    cmd /c "git -C ""$Root"" hash-object --stdin-paths < ""$listFile"" > ""$out""" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "git hash-object failed for $tag" }
    $shas = [IO.File]::ReadAllLines($out)
    if ($shas.Count -ne $paths.Count) { throw "hash-object returned $($shas.Count) lines for $($paths.Count) paths" }
    $map = New-Object 'System.Collections.Generic.Dictionary[string,string]' ([StringComparer]::Ordinal)
    for ($i = 0; $i -lt $paths.Count; $i++) { $map[$paths[$i]] = $shas[$i].Trim() }
    , $map
}

function Proof1([string]$tag) {
    $base = BaselineBlobs $sha
    $present = @(); $absent = @()
    foreach ($p in $base.Keys) {
        if (Test-Path -LiteralPath (Join-Path $Root ($p -replace '/', '\'))) { $present += $p } else { $absent += $p }
    }
    $disk = DiskBlobs $present $tag
    $changed = @()
    foreach ($p in $present) { if ($disk[$p] -cne $base[$p]) { $changed += $p } }
    [pscustomobject]@{ Total = $base.Count; Changed = @($changed | Sort-Object); Missing = @($absent | Sort-Object) }
}

$p1 = Proof1 'run'
"P1 : $($p1.Total) baseline files under wz/ re-hashed from disk -> changed $($p1.Changed.Count), missing $($p1.Missing.Count)"
foreach ($c in $p1.Changed) { "P1 :   changed: $c" }
foreach ($m in $p1.Missing) { "P1 :   MISSING: $m" }
if ($p1.Missing.Count) { $fail++; 'P1 : FAIL - a file that existed at the baseline is gone' }
if (@(Compare-Object $p1.Changed $SPLICED -SyncWindow 0).Count) {
    $fail++; "P1 : FAIL - the changed set is not exactly the two spliced files"
}

# The added files, so "44 new files" is measured rather than asserted.
#
# Counted as "paths under wz/ that exist NOW and were not in the baseline tree", NOT as "untracked".
# Those two are the same number only BEFORE the merge is committed; afterwards the 44 are tracked
# and `git status` reports nothing. The first version of this script used untracked-only and the
# `-Rev <deliveredSha>~1` recipe printed in the ticket therefore FAILED the moment it was actually
# run against the delivered commit - found by running it rather than by reading it.
$baseKeys = (BaselineBlobs $sha).Keys
$nowTracked = @(& git -C $Root ls-files -- wz/)
$nowUntracked = @(& git -C $Root status --porcelain --untracked-files=all -- wz/ |
        Where-Object { $_ -match '^\?\? ' } | ForEach-Object { $_.Substring(3).Trim('"') })
$untracked = @($nowTracked + $nowUntracked | Where-Object { -not $baseKeys.Contains($_) } | Sort-Object -Unique)
$notXml = @($untracked | Where-Object { $_ -notmatch '\.img\.xml$' })
"P1 : files under wz/ added since the baseline: $($untracked.Count) (non-.img.xml: $($notXml.Count))"
if ($notXml.Count) { $fail++; "P1 : FAIL - unexpected untracked non-image files: $($notXml -join ',')" }
# 44 is hardcoded ON PURPOSE: it is this ticket's manifest total (28 Map + 14 Npc + 2 Mob),
# written down independently of the tree so that the tree can disagree with it. Deriving it from
# the files on disk would make the check compare the tree with itself.
if ($untracked.Count -ne 44) { $fail++; "P1 : FAIL - expected exactly 44 new .img.xml files, found $($untracked.Count)" }

# ------------------------------------------------------------------ PROOF 2
function Digest([System.Xml.XmlNode]$n) {
    # name + attributes sorted by name + element children in document order, recursively.
    # Whitespace-independent on purpose; catches any value edit, added/removed/reordered child.
    $sb = [System.Text.StringBuilder]::new()
    [void]$sb.Append('<').Append($n.Name)
    foreach ($a in ($n.Attributes | Sort-Object Name)) { [void]$sb.Append(' ').Append($a.Name).Append('=').Append($a.Value) }
    [void]$sb.Append('>')
    foreach ($c in $n.ChildNodes) { if ($c.NodeType -eq 'Element') { [void]$sb.Append((Digest $c)) } }
    [void]$sb.Append('</').Append($n.Name).Append('>')
    $sb.ToString()
}

# name -> digest of the whole subtree, for the element children of $container
# ('' = the document root, otherwise the name= of a direct child of the root).
function DigestMap([string]$xml, [string]$container) {
    $doc = New-Object System.Xml.XmlDocument
    $doc.PreserveWhitespace = $true
    $doc.LoadXml($xml)
    $parent = $doc.DocumentElement
    if ($container -ne '') {
        $parent = @($doc.DocumentElement.ChildNodes | Where-Object { $_.NodeType -eq 'Element' -and $_.GetAttribute('name') -ceq $container })[0]
        if (-not $parent) { throw "container '$container' not found" }
    }
    # Ordinal so two names differing only in case cannot collide (PS hashtables are not).
    $map = New-Object 'System.Collections.Generic.Dictionary[string,string]' ([StringComparer]::Ordinal)
    foreach ($c in $parent.ChildNodes) { if ($c.NodeType -eq 'Element') { $map[$c.GetAttribute('name')] = Digest $c } }
    , $map
}

function Compare2([string]$label, [string]$oldXml, [string]$newXml, [string]$container, [string[]]$expectedNew) {
    $o = DigestMap $oldXml $container
    $n = DigestMap $newXml $container
    $changed = @(); $missing = @()
    foreach ($k in $o.Keys) {
        if (-not $n.ContainsKey($k)) { $missing += $k; continue }
        if ($n[$k] -cne $o[$k]) { $changed += $k }
    }
    $new = @($n.Keys | Where-Object { -not $o.ContainsKey($_) } | Sort-Object)
    [pscustomobject]@{ Label = $label; Pre = $o.Count; Changed = @($changed | Sort-Object); Missing = @($missing | Sort-Object); New = $new; Expected = @($expectedNew | Sort-Object) }
}

function ReportCompare($r) {
    # Write-Host, not the pipeline: this function's only RETURN value is the failure count.
    Write-Host "P2 : $($r.Label) : pre-existing $($r.Pre) -> changed $($r.Changed.Count), missing $($r.Missing.Count); new $($r.New.Count) (expected $($r.Expected.Count))"
    if ($r.Changed.Count) { Write-Host "P2 :   CHANGED -> $($r.Changed -join ',')" }
    if ($r.Missing.Count) { Write-Host "P2 :   MISSING -> $($r.Missing -join ',')" }
    $bad = 0
    if ($r.Changed.Count -or $r.Missing.Count) { $bad++ }
    if (@(Compare-Object $r.New $r.Expected -SyncWindow 0).Count) {
        $bad++
        Write-Host "P2 :   FAIL - new-id set is not the expected set. new=[$($r.New -join ',')] expected=[$($r.Expected -join ',')]"
        if ($r.New.Count -eq 0) { Write-Host "P2 :   (0 new ids usually means the baseline IS the merged file - this proof would then be vacuous)" }
    }
    return $bad
}

function BlobText([string]$relPath) {
    $blobPath = Join-Path $Scratch ("blob-" + ($relPath -replace '[\\/]', '_'))
    cmd /c "git -C ""$Root"" cat-file blob ${sha}:$relPath > ""$blobPath""" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "git cat-file blob ${sha}:$relPath failed" }
    # LF blob -> the CRLF form the working tree holds.
    ([IO.File]::ReadAllText($blobPath, $U8) -replace "`r`n", "`n") -replace "`n", "`r`n"
}

# EIGHTEEN, not nineteen. 900090104 is merged as a MAP IMAGE but has no String.wz entry in v84 -
# there is nothing to add, so it is deliberately not here. fidelity.ps1 and
# V84EvanWorldNodeTest.EVAN_MAPS list 19 for that reason; do not "fix" the asymmetry.
$mapIds = 900010000, 900010100, 900010200, 900020100, 900020110, 900020200, 900020210, 900020220,
900030000, 900090000, 900090001, 900090002, 900090003, 900090004, 900090100, 900090101,
900090102, 900090103 | ForEach-Object { "$_" }
$npcIds = @('1013204', '1013205', '1013206')

$oldMap = BlobText 'wz/String.wz/Map.img.xml'
$newMap = [IO.File]::ReadAllText((Join-Path $Root 'wz\String.wz\Map.img.xml'), $U8)
$oldNpc = BlobText 'wz/String.wz/Npc.img.xml'
$newNpc = [IO.File]::ReadAllText((Join-Path $Root 'wz\String.wz\Npc.img.xml'), $U8)

# Map.img.xml, level 1: the street categories. `etc` MUST differ (we added into it); the other
# 11 must not, and none may vanish or appear.
$lvl1 = Compare2 'Map.img/<root>' $oldMap $newMap '' @()
"P2 : Map.img/<root> : pre-existing $($lvl1.Pre) categories -> changed $($lvl1.Changed.Count) [$($lvl1.Changed -join ',')], missing $($lvl1.Missing.Count), new $($lvl1.New.Count)"
if ($lvl1.Missing.Count -or $lvl1.New.Count) { $fail++; 'P2 : FAIL - a street category appeared or vanished' }
if (@(Compare-Object $lvl1.Changed @('etc') -SyncWindow 0).Count) {
    $fail++; "P2 : FAIL - categories other than 'etc' changed: $($lvl1.Changed -join ',')"
}

$fail += ReportCompare (Compare2 'Map.img/etc' $oldMap $newMap 'etc' $mapIds)
$fail += ReportCompare (Compare2 'Npc.img/<root>' $oldNpc $newNpc '' $npcIds)

# ------------------------------------------------------------- self-checks
# A check that can only print PASS is not a check. Both proofs are shown to reject a real
# corruption and to accept the real tree, in the same run, with the same code.

# SELF-CHECK 1 - PROOF 1 catches one changed byte in a pre-existing file it currently calls
# clean. Mutates on disk, so the restore is in a finally and is verified by hash.
$victim = 'wz/Npc.wz/1013000.img.xml'
$victimAbs = Join-Path $Root ($victim -replace '/', '\')
if ($p1.Changed -contains $victim) { throw "self-check 1: $victim is already reported changed" }
$origBytes = [IO.File]::ReadAllBytes($victimAbs)
$origSha = (Get-FileHash -LiteralPath $victimAbs -Algorithm SHA256).Hash
$caught = $false
try {
    [IO.File]::WriteAllBytes($victimAbs, ($origBytes + [byte[]](0x20)))
    $caught = @(Proof1 'selfcheck').Changed -contains $victim
}
finally {
    [IO.File]::WriteAllBytes($victimAbs, $origBytes)
}
$restored = ((Get-FileHash -LiteralPath $victimAbs -Algorithm SHA256).Hash -eq $origSha)
"SELF-CHECK 1: PROOF 1 calls the real tree clean AND catches a one-byte edit to $victim = $caught (restored = $restored)"
if (-not $caught) { $fail++; 'SELF-CHECK 1: FAIL - PROOF 1 cannot see a changed file, so its clean result means nothing' }
if (-not $restored) { $fail++; 'SELF-CHECK 1: FAIL - the victim file was NOT restored' }

# SELF-CHECK 2 - PROOF 2's digest catches an edit INSIDE a pre-existing record, which is the
# one failure mode PROOF 1 localises to a file but cannot attribute to a node, and which the
# merge's own conflicts list cannot see at all. In memory only; nothing is written.
# Scoped to the `etc` block, because Compare2 above only looks at etc's children: a mutation
# anywhere else is invisible to it BY DESIGN and would make this self-check a false alarm.
# (The first version of this file searched the whole document and landed on 106021100, which
# lives under another category - it reported "the comparator cannot see an edit" about an edit
# the comparator was never asked to look at.)
$etcStart = $newMap.IndexOf("`r`n  <imgdir name=`"etc`">`r`n")
if ($etcStart -lt 0) { throw 'self-check 2: no etc block' }
$etcEnd = $newMap.IndexOf("`r`n  </imgdir>", $etcStart)
if ($etcEnd -lt 0) { throw 'self-check 2: etc block never closes' }
$etcText = $newMap.Substring($etcStart, $etcEnd - $etcStart)
$anchor = @([regex]::Matches($etcText, '(?m)^    <imgdir name="(\d+)">\r\n      <string name="streetName" value="([^"]*)"') |
        Where-Object { $mapIds -notcontains $_.Groups[1].Value })[0]
if (-not $anchor) { throw 'self-check 2: no PRE-EXISTING <id>/streetName pair found inside etc' }
$victimId = $anchor.Groups[1].Value
$anchorAt = $etcStart + $anchor.Groups[2].Index
$mutated = $newMap.Insert($anchorAt, 'CORRUPTED_')
$mr = Compare2 'self-check 2' $oldMap $mutated 'etc' $mapIds
$m2ok = ($mr.Changed -contains $victimId)
"SELF-CHECK 2: PROOF 2 reports 0 changed on the real file AND flags a streetName edit to pre-existing id $victimId = $m2ok"
if (-not $m2ok) { $fail++; 'SELF-CHECK 2: FAIL - the digest comparator does not see an edit inside an existing record' }

# SELF-CHECK 3 - PROOF 2 catches a DROPPED child of a pre-existing record (the direction a
# "changed value" test does not cover).
$drop = @([regex]::Matches($etcText, '(?m)^    <imgdir name="(\d+)">\r\n(      <string name="streetName" value="[^"]*"/>\r\n)') |
        Where-Object { $mapIds -notcontains $_.Groups[1].Value })[0]
if (-not $drop) { throw 'self-check 3: no droppable child of a PRE-EXISTING id found inside etc' }
$mutated3 = $newMap.Remove($etcStart + $drop.Groups[2].Index, $drop.Groups[2].Length)
$m3ok = ((Compare2 'self-check 3' $oldMap $mutated3 'etc' $mapIds).Changed -contains $drop.Groups[1].Value)
"SELF-CHECK 3: PROOF 2 flags a dropped child of pre-existing id $($drop.Groups[1].Value) = $m3ok"
if (-not $m3ok) { $fail++; 'SELF-CHECK 3: FAIL - the digest comparator does not see a removed child' }

if ($fail) { "RESULT: FAIL ($fail)"; exit 1 } else { 'RESULT: PASS'; exit 0 }
