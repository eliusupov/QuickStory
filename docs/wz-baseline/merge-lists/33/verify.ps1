# 33 — prove that NOTHING pre-existing changed. Two independent proofs, because
# "the conflicts list was empty" is not evidence: a write INTO an existing record is
# the failure mode, and it produces an empty conflicts list too.
#
# Proof A (bytes): strip exactly the top-level <imgdir> blocks whose name is in the
#   added-id list, and assert the remainder is BYTE-identical to HEAD's blob. If the merge
#   had touched one byte of any other quest, or nested an added block inside an existing
#   one, the remainder differs.
# Proof B (parse): load HEAD's blob and the new file with an XML parser and compare a
#   canonical digest of every pre-existing quest id's whole subtree. Also proves they parse.
#
# Two traps this walked into, both instrument faults rather than data faults, both left
# documented because the next person will hit them:
#   * this repo has core.autocrlf=true, so the BLOB is LF and the WORKING TREE is CRLF.
#     A raw `git cat-file blob` is 24,402 bytes short of the file it came from, exactly the
#     CR count. The reference below is the blob with LF -> CRLF; the sizes then match to
#     the byte. Diffing against the unconverted blob reports every file as changed.
#   * PS 5.1: Set-Content -Encoding utf8 writes a BOM and Get-Content without an explicit
#     encoding decodes a BOM-less UTF-8 file as ANSI. Everything here is [IO.File] with an
#     explicit no-BOM UTF8, and the blob comes through cmd's byte-exact redirect.
#
#   .\verify.ps1 <worktreeRoot> <addedIdListDir> <scratchDir>
param(
    [Parameter(Mandatory)][string]$Root,
    [Parameter(Mandatory)][string]$Ids,
    [Parameter(Mandatory)][string]$Scratch
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force $Scratch | Out-Null
$U8 = New-Object System.Text.UTF8Encoding $false
$fail = 0

function Digest([System.Xml.XmlNode]$n) {
    # Canonical: name, every attribute sorted by name, recursively over element children.
    # Whitespace-independent on purpose - a reformat is not a data change - but it must
    # still catch any value edit, added child or removed child.
    $sb = [System.Text.StringBuilder]::new()
    [void]$sb.Append('<').Append($n.Name)
    foreach ($a in ($n.Attributes | Sort-Object Name)) { [void]$sb.Append(' ').Append($a.Name).Append('=').Append($a.Value) }
    [void]$sb.Append('>')
    foreach ($c in $n.ChildNodes) { if ($c.NodeType -eq 'Element') { [void]$sb.Append((Digest $c)) } }
    [void]$sb.Append('</').Append($n.Name).Append('>')
    $sb.ToString()
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
    $blobPath = Join-Path $Scratch "HEAD-$img.blob"
    $oldPath = Join-Path $Scratch "HEAD-$img.img.xml"
    cmd /c "git -C ""$Root"" cat-file blob HEAD:$rel > ""$blobPath""" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "git cat-file blob HEAD:$rel failed" }
    # LF blob -> CRLF working-tree form, the form the file on disk is in.
    $oldText = ([IO.File]::ReadAllText($blobPath, $U8) -replace "`r`n", "`n") -replace "`n", "`r`n"
    [IO.File]::WriteAllText($oldPath, $oldText, $U8)
    $headTexts[$img] = $oldText

    $added = [System.Collections.Generic.HashSet[string]]::new([string[]](Get-Content "$Ids\$img.new-ids.txt"))
    $newText = [IO.File]::ReadAllText($newPath, $U8)

    # ---- Proof A ----
    $res = StripTop $newText $added
    $rb = $res[0]; $stripped = $res[1]
    $okA = ($rb -ceq $oldText)
    if (-not $okA) { $fail++ }
    "A  $img : stripped $stripped/$($added.Count) added top-level blocks; remainder identical to HEAD = $okA  (chars after strip=$($rb.Length), HEAD=$($oldText.Length))"
    if ($stripped -ne $added.Count) { $fail++; "A  $img : FAIL - not every added id was found as a TOP-LEVEL block" }

    # ---- Proof B ----
    $dNew = New-Object System.Xml.XmlDocument; $dNew.PreserveWhitespace = $true; $dNew.Load($newPath)
    $dOld = New-Object System.Xml.XmlDocument; $dOld.PreserveWhitespace = $true; $dOld.Load($oldPath)
    $mapOld = @{}; foreach ($c in $dOld.DocumentElement.ChildNodes) { if ($c.NodeType -eq 'Element') { $mapOld[$c.GetAttribute('name')] = Digest $c } }
    $mapNew = @{}; foreach ($c in $dNew.DocumentElement.ChildNodes) { if ($c.NodeType -eq 'Element') { $mapNew[$c.GetAttribute('name')] = Digest $c } }
    $changed = @(); $missing = @()
    foreach ($k in $mapOld.Keys) {
        if (-not $mapNew.ContainsKey($k)) { $missing += $k; continue }
        if ($mapNew[$k] -cne $mapOld[$k]) { $changed += $k }
    }
    $newIds = @($mapNew.Keys | Where-Object { -not $mapOld.ContainsKey($_) })
    if ($changed.Count -or $missing.Count) { $fail++ }
    "B  $img : pre-existing ids $($mapOld.Count) -> changed $($changed.Count), missing $($missing.Count); new ids $($newIds.Count)"
    if ($changed.Count) {
        "B  $img : CHANGED -> $(($changed | Select-Object -First 20) -join ',')"
        $k = $changed[0]; $a = $mapOld[$k]; $b = $mapNew[$k]
        $i = 0; while ($i -lt [Math]::Min($a.Length, $b.Length) -and $a[$i] -ceq $b[$i]) { $i++ }
        "B  $img : first divergence in '$k' at char $i`n     HEAD: ...$($a.Substring([Math]::Max(0,$i-60), [Math]::Min(160, $a.Length-[Math]::Max(0,$i-60))))`n     NEW : ...$($b.Substring([Math]::Max(0,$i-60), [Math]::Min(160, $b.Length-[Math]::Max(0,$i-60))))"
    }
    if ($missing.Count) { "B  $img : MISSING -> $($missing -join ',')" }
    $unexpected = @($newIds | Where-Object { -not $added.Contains($_) })
    if ($unexpected.Count) { $fail++; "B  $img : UNEXPECTED new ids -> $($unexpected -join ',')" }
}

# ---- Self-check: proof A must FAIL on input it should reject. A check that can only
# print PASS is not a check. Two mutations of the CURRENT merged Check.img.xml:
#   1. one character changed inside a pre-existing quest
#   2. an added 22xxx block nested INSIDE a pre-existing quest instead of at top level
# Both must come out "different from HEAD".
$cur = [IO.File]::ReadAllText((Join-Path $Root 'wz\Quest.wz\Check.img.xml'), $U8)
$addedC = [System.Collections.Generic.HashSet[string]]::new([string[]](Get-Content "$Ids\Check.new-ids.txt"))

$i = $cur.IndexOf('<int name="lvmin" value="')
if ($i -lt 0) { throw 'self-check: no lvmin found' }
$m1 = $cur.Remove($i + 25, 1).Insert($i + 25, '7')
$ok1 = ((StripTop $m1 $addedC)[0] -cne $headTexts['Check'])
"SELF-CHECK 1: proof A rejects a one-character value edit to a pre-existing quest = $ok1"
if (-not $ok1) { $fail++ }

$s22 = $cur.IndexOf("`r`n  <imgdir name=`"22100`">`r`n")
$e22 = $cur.IndexOf("`r`n  </imgdir>", $s22)
if ($s22 -lt 0 -or $e22 -lt 0) { throw 'self-check: cannot locate the 22100 block' }
$blk = $cur.Substring($s22, $e22 + 14 - $s22)
$anchor = $cur.IndexOf("`r`n  </imgdir>")           # end of the first top-level quest
$m2 = $cur.Insert($anchor, ($blk -replace "`r`n", "`r`n  "))
$ok2 = ((StripTop $m2 $addedC)[0] -cne $headTexts['Check'])
"SELF-CHECK 2: proof A rejects a 22xxx block nested inside a pre-existing quest = $ok2"
if (-not $ok2) { $fail++ }

if ($fail) { "RESULT: FAIL ($fail)"; exit 1 } else { "RESULT: PASS" }
