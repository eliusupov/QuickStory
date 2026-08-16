# 33 — prove that NOTHING pre-existing changed. Two proofs, because neither one is sound alone,
# and "the conflicts list was empty" is not evidence at all: a write INTO an existing record is
# the failure mode, and it produces an empty conflicts list too.
#
#   .\verify.ps1 <worktreeRoot> <addedIdListDir> <scratchDir> [-Rev <baseline>]
#
# -Rev defaults to HEAD, which is only correct BEFORE the merge is committed. To re-verify the
# delivered commit, pass the commit before it:
#   .\verify.ps1 <root> <root>\docs\wz-baseline\merge-lists\33 <scratch> -Rev 9ea79e6f3~1
#
# Proof A (text): strip exactly the top-level <imgdir> blocks whose name is in the added-id list,
#   and assert the remainder is identical to the baseline blob.
# Proof B (parse): compare a canonical digest of every pre-existing quest id's whole subtree, and
#   assert the set of NEW ids is exactly the added-id list. Also proves the files parse.
#
# WHY BOTH, stated precisely, because the first version of this file claimed A alone covered it:
#   * A is blind to a block that is XML-nested inside a pre-existing quest but written at the SAME
#     2-space indent, because the nested block's own close line is consumed as A's strip
#     terminator and the host quest's real close survives into the remainder. A reports
#     "stripped 135/135, identical = True" on a file where quest 1000 has grown a 22100 subtree.
#     SELF-CHECK 3 builds exactly that file and demonstrates it: A passes, B fails.
#   * B is blind to a DUPLICATED pre-existing block, because its per-id digest map is keyed by
#     name and the duplicate silently overwrites its own key. A catches that one (extra bytes).
#   So: A is byte-exact everywhere outside the 135 stripped ranges, therefore any modification has
#   to live inside one of them; inside a range it is either XML-nested in a pre-existing quest
#   (B's digest moves) or an extra top-level element (B's new-id set moves). The PAIR is the proof.
#   Neither half may be quoted on its own.
#
# Scope: this proves nothing pre-existing CHANGED. It does not prove the 405 added nodes match
# v84 - A ignores the stripped ranges entirely and B only checks their names.
#
# Traps this walked into, all instrument faults, left documented because the next person will hit them:
#   * core.autocrlf=true here, so the BLOB is LF and the WORKING TREE is CRLF. A raw
#     `git cat-file blob` is 24,402 bytes short of the file it came from, exactly the CR count.
#     The baseline below is the blob with LF -> CRLF; the sizes then match to the byte. Diffing
#     against the unconverted blob reports every file as changed.
#   * PS 5.1: Set-Content -Encoding utf8 writes a BOM, and Get-Content without an explicit
#     encoding decodes a BOM-less UTF-8 file as ANSI. Everything here is [IO.File] with explicit
#     no-BOM UTF8, and the blob comes through cmd's byte-exact redirect.
#   * cmd /c eats '^' as its escape character, so `git cat-file blob <sha>^:path` silently
#     returns the WRONG blob with no error. -Rev is resolved to a full SHA by git first, and only
#     the SHA is ever handed to cmd.
param(
    [Parameter(Mandatory)][string]$Root,
    [Parameter(Mandatory)][string]$Ids,
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

function Digest([System.Xml.XmlNode]$n) {
    # Canonical: name, every attribute sorted by name, recursively over element children in
    # document order. Whitespace-independent on purpose - a reformat is not a data change - but it
    # catches any value edit, added child, removed child or reordered child.
    $sb = [System.Text.StringBuilder]::new()
    [void]$sb.Append('<').Append($n.Name)
    foreach ($a in ($n.Attributes | Sort-Object Name)) { [void]$sb.Append(' ').Append($a.Name).Append('=').Append($a.Value) }
    [void]$sb.Append('>')
    foreach ($c in $n.ChildNodes) { if ($c.NodeType -eq 'Element') { [void]$sb.Append((Digest $c)) } }
    [void]$sb.Append('</').Append($n.Name).Append('>')
    $sb.ToString()
}

# id -> digest of its whole subtree, for every top-level child. Case-sensitive so two ids that
# differ only in case cannot collide (PS hashtables are case-insensitive by default).
function DigestMap([string]$xml) {
    $doc = New-Object System.Xml.XmlDocument
    $doc.PreserveWhitespace = $true
    $doc.LoadXml($xml)
    $map = New-Object 'System.Collections.Generic.Dictionary[string,string]' ([StringComparer]::Ordinal)
    foreach ($c in $doc.DocumentElement.ChildNodes) { if ($c.NodeType -eq 'Element') { $map[$c.GetAttribute('name')] = Digest $c } }
    , $map
}

# Strip the top-level <imgdir name="X"> blocks whose X is in $set.
# Returns @(remainderJoinedWithCRLF, blocksRemoved).
function StripTop([string]$text, $set) {
    $keep = [System.Collections.Generic.List[string]]::new()
    $skipping = $false; $stripped = 0
    foreach ($l in ($text -split "`r`n")) {
        if ($skipping) { if ($l -eq '  </imgdir>') { $skipping = $false }; continue }
        if ($l -match '^  <imgdir name="([^"]+)">$' -and $set.Contains($Matches[1])) { $skipping = $true; $stripped++; continue }
        $keep.Add($l)
    }
    return @(($keep -join "`r`n"), $stripped)
}

$headTexts = @{}
foreach ($img in 'QuestInfo', 'Check', 'Act') {
    $rel = "wz/Quest.wz/$img.img.xml"
    $newPath = Join-Path $Root ($rel -replace '/', '\')
    $blobPath = Join-Path $Scratch "base-$img.blob"
    cmd /c "git -C ""$Root"" cat-file blob ${sha}:$rel > ""$blobPath""" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "git cat-file blob ${sha}:$rel failed" }
    # LF blob -> CRLF working-tree form, the form the file on disk is in.
    $oldText = ([IO.File]::ReadAllText($blobPath, $U8) -replace "`r`n", "`n") -replace "`n", "`r`n"
    $headTexts[$img] = $oldText

    $added = [System.Collections.Generic.HashSet[string]]::new([string[]](Get-Content "$Ids\$img.new-ids.txt"))
    $newText = [IO.File]::ReadAllText($newPath, $U8)

    # ---- Proof A ----
    $res = StripTop $newText $added
    $rb = $res[0]; $stripped = $res[1]
    $okA = ($rb -ceq $oldText)
    if (-not $okA) { $fail++ }
    "A  $img : stripped $stripped (expected $($added.Count)); remainder identical to baseline = $okA  (chars after strip=$($rb.Length), baseline=$($oldText.Length))"
    # Fires in BOTH directions - too few means an added id is not a top-level block, too many means
    # one was written twice. The old message only named the first and would have mis-diagnosed the second.
    if ($stripped -ne $added.Count) { $fail++; "A  $img : FAIL - stripped $stripped top-level blocks, expected $($added.Count)" }

    # ---- Proof B ----
    $mapNew = DigestMap $newText
    $mapOld = DigestMap $oldText
    $changed = @(); $missing = @()
    foreach ($k in $mapOld.Keys) {
        if (-not $mapNew.ContainsKey($k)) { $missing += $k; continue }
        if ($mapNew[$k] -cne $mapOld[$k]) { $changed += $k }
    }
    $newIds = @($mapNew.Keys | Where-Object { -not $mapOld.ContainsKey($_) })
    if ($changed.Count -or $missing.Count) { $fail++ }
    "B  $img : pre-existing ids $($mapOld.Count) -> changed $($changed.Count), missing $($missing.Count); new ids $($newIds.Count)"
    # Without this, B against a baseline that IS the merged file reports "changed 0, missing 0"
    # - a vacuous self-diff that reads exactly like a pass.
    if ($newIds.Count -ne $added.Count) {
        $fail++; "B  $img : FAIL - $($newIds.Count) new ids, expected $($added.Count). If this is 0, the baseline is the merged file itself and B proved nothing."
    }
    if ($changed.Count) {
        "B  $img : CHANGED -> $(($changed | Select-Object -First 20) -join ',')"
        $k = $changed[0]; $a = $mapOld[$k]; $b = $mapNew[$k]
        $i = 0; while ($i -lt [Math]::Min($a.Length, $b.Length) -and $a[$i] -ceq $b[$i]) { $i++ }
        "B  $img : first divergence in '$k' at char $i`n     BASE: ...$($a.Substring([Math]::Max(0,$i-60), [Math]::Min(160, $a.Length-[Math]::Max(0,$i-60))))`n     NEW : ...$($b.Substring([Math]::Max(0,$i-60), [Math]::Min(160, $b.Length-[Math]::Max(0,$i-60))))"
    }
    if ($missing.Count) { "B  $img : MISSING -> $($missing -join ',')" }
    $unexpected = @($newIds | Where-Object { -not $added.Contains($_) })
    if ($unexpected.Count) { $fail++; "B  $img : UNEXPECTED new ids -> $($unexpected -join ',')" }
}

# ---------------------------------------------------------------- self-checks
# A check that can only print PASS is not a check. Each of these mutates the REAL merged
# Check.img.xml and runs the REAL StripTop / DigestMap, and each asserts BOTH halves: that the
# unmutated file passes (the positive control) and that the mutated one is rejected. Without the
# positive control these print True even when the baseline is meaningless - which is exactly what
# happened when the script was re-run after the merge was committed.
$cur = [IO.File]::ReadAllText((Join-Path $Root 'wz\Quest.wz\Check.img.xml'), $U8)
$addedC = [System.Collections.Generic.HashSet[string]]::new([string[]](Get-Content "$Ids\Check.new-ids.txt"))
$baseC = $headTexts['Check']
$aPasses = { param($t) (StripTop $t $addedC)[0] -ceq $baseC }

# 1. one character of an lvmin inside a pre-existing quest
$i = $cur.IndexOf('<int name="lvmin" value="')
if ($i -lt 0) { throw 'self-check 1: no lvmin found' }
$m1 = $cur.Remove($i + 25, 1).Insert($i + 25, '7')
$ok1 = (& $aPasses $cur) -and -not (& $aPasses $m1)
"SELF-CHECK 1: A accepts the real file AND rejects a one-character edit to a pre-existing quest = $ok1"
if (-not $ok1) { $fail++ }

# 2. an added block re-nested one level DEEPER inside a pre-existing quest (4-space indent)
$s22 = $cur.IndexOf("`r`n  <imgdir name=`"22100`">`r`n")
$e22 = $cur.IndexOf("`r`n  </imgdir>", $s22)
if ($s22 -lt 0 -or $e22 -lt 0) { throw 'self-check 2: cannot locate the 22100 block' }
$blk = $cur.Substring($s22, $e22 + 13 - $s22)          # 13 = "`r`n  </imgdir>".Length
$anchor = $cur.IndexOf("`r`n  </imgdir>")               # end of the first top-level quest
$m2 = $cur.Insert($anchor, ($blk -replace "`r`n", "`r`n  "))
$ok2 = (& $aPasses $cur) -and -not (& $aPasses $m2)
"SELF-CHECK 2: A accepts the real file AND rejects a 22xxx block nested at 4-space indent = $ok2"
if (-not $ok2) { $fail++ }

# 3. THE HOLE IN A, demonstrated rather than described: the same block nested inside the same
# pre-existing quest but left at 2-space indent. A cannot see it. B must.
$m3 = $cur.Insert($anchor, $blk)
$aBlind = (& $aPasses $m3)
$m3Map = DigestMap $m3
$baseMap = DigestMap $baseC
$hostId = ([regex]::Match($cur, '^  <imgdir name="([^"]+)">', 'Multiline')).Groups[1].Value
$bCatches = $m3Map[$hostId] -cne $baseMap[$hostId]
"SELF-CHECK 3: same-indent nesting into pre-existing quest '$hostId' -> A blind = $aBlind, B catches it = $bCatches"
if (-not $aBlind) { "  (A is no longer blind here - the note in this file's header is now stronger than the code; re-read it)" }
if (-not $bCatches) { $fail++; "SELF-CHECK 3: FAIL - the pair is not sound, B did not catch what A cannot see" }

if ($fail) { "RESULT: FAIL ($fail)"; exit 1 } else { "RESULT: PASS" }
