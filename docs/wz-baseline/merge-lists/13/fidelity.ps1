# 13 - the 44 new .img.xml files are FAITHFUL to v84, not merely present.
#
#   .\fidelity.ps1 <worktreeRoot>
#
# verify.ps1 proves nothing pre-existing changed. It says nothing at all about what landed in the
# 44 new files - a truncated or half-written image passes it perfectly, because a new file cannot
# damage an old one. This is the other half.
#
# Method: for every merged image, `WzMerge dump <v84 .wz> <path> 30` (MapleLib's READER plus a
# six-line printer) is compared with the committed .img.xml (written by MapleLib's XmlSerializer -
# a different code path). Two measures per image:
#   * NODE COUNT: dump lines vs XML element lines. The serializer writes exactly one element per
#     line, open or self-closing, so these must be equal.
#   * NAME MULTISET: every `name=` in the XML vs every node name in the dump, sorted and compared
#     as a whole. Catches a node written under the wrong name, or two nodes swapped for one.
# Depth 30 with an assertion that the observed depth is under it, because `dump` truncates
# SILENTLY at its limit (ticket 33 hit this).
#
# SELF-CHECK first, and the script refuses to compare anything if it fails: a real merged image is
# mutated four ways in memory and each mutation must be caught. A comparator that cannot fail is
# not a measurement.
param(
    [Parameter(Mandatory)][string]$Root,
    [string]$V84 = 'D:\games\MapleStory\Server\porting-resources\wz-data\v84'
)
$ErrorActionPreference = 'Stop'
$U8 = New-Object System.Text.UTF8Encoding $false
$exe = Join-Path $Root 'docs\wz-baseline\tool-merge\bin\Release\net10.0-windows\WzMerge.exe'
$v84 = $V84
$fail = 0

$IMAGES = @()
foreach ($id in 900010000, 900010100, 900010200, 900020100, 900020110, 900020200, 900020210,
    900020220, 900030000, 900090000, 900090001, 900090002, 900090003, 900090004,
    900090100, 900090101, 900090102, 900090103, 900090104) {
    $IMAGES += , @('Map', "Map/Map9/$id.img", "wz\Map.wz\Map\Map9\$id.img.xml")
}
foreach ($id in 100030100, 100030101, 100030102, 100030103, 100030200, 100030300, 100030310,
    100030320, 100030400) {
    $IMAGES += , @('Map', "Map/Map1/$id.img", "wz\Map.wz\Map\Map1\$id.img.xml")
}
foreach ($id in 1013001, 1013002, 1013100, 1013101, 1013102, 1013103, 1013104, 1013105,
    1013200, 1013201, 1013202, 1013204, 1013205, 1013206) {
    $IMAGES += , @('Npc', "$id.img", "wz\Npc.wz\$id.img.xml")
}
foreach ($id in '1210111', '9300385') {
    $IMAGES += , @('Mob', "$id.img", "wz\Mob.wz\$id.img.xml")
}

# The ONLY normalisation applied, and it is scoped to floating-point types: `dump` prints a float
# through the default ToString ("1") and the XML serializer writes the round-trip form ("1.0").
# That is a representation difference in two printers, not a difference in the data. Ints, longs
# and strings are compared VERBATIM - normalising those would let a leading zero or a re-rendered
# id through, which is exactly the class of damage this script exists to find.
function NormNum([string]$type, [string]$value) {
    if ($type -notmatch '^(Float|Double|float|double)$') { return $value }
    [double]$d = 0
    if (-not [double]::TryParse($value, [Globalization.NumberStyles]::Float,
            [Globalization.CultureInfo]::InvariantCulture, [ref]$d)) { return $value }
    return $d.ToString('R', [Globalization.CultureInfo]::InvariantCulture)
}

# ---- dump side ------------------------------------------------------------
# "  <indent><name> [<WzType>] = <value>" ; the first line is the file banner, the second the image.
function DumpNodes([string]$wz, [string]$path) {
    $lines = & $exe dump (Join-Path $v84 "$wz.wz") $path 30
    if ($LASTEXITCODE -ne 0) { throw "dump failed for $wz.wz $path" }
    $names = [System.Collections.Generic.List[string]]::new()
    $scalars = [System.Collections.Generic.List[string]]::new()
    $maxIndent = 0
    foreach ($l in $lines) {
        if ($l -match '^[A-Z]:\\' -or $l -eq '') { continue }          # banner / blanks
        if ($l -notmatch '^(\s*)([^\s\[]+(?: [^\s\[]+)*) \[Wz') { continue }  # value continuation line
        $indent = $Matches[1].Length
        if ($indent -gt $maxIndent) { $maxIndent = $indent }
        $names.Add($Matches[2])
        # Scalar leaves, the half a name multiset cannot see. Kept to the five types that carry
        # every number and label in a map/npc/mob image; a canvas/UOL/vector is name-only here.
        if ($l -match '^\s*([^\s\[]+(?: [^\s\[]+)*) \[Wz(Int|String|Short|Float|Double|Long)Property\] = (.*)$') {
            $scalars.Add("$($Matches[1])`t$(NormNum $Matches[2] $Matches[3])")
        }
    }
    [pscustomobject]@{ Names = $names; Scalars = $scalars; MaxDepth = [int]($maxIndent / 2) }
}

# ---- xml side -------------------------------------------------------------
# One element per line; the root element is the image node the dump also prints.
function XmlNodes([string]$text) {
    $names = [System.Collections.Generic.List[string]]::new()
    $scalars = [System.Collections.Generic.List[string]]::new()
    foreach ($l in ($text -split "`r`n")) {
        if ($l -match '^\s*<\?xml') { continue }
        if ($l -match '^\s*</') { continue }
        if ($l -match '^\s*<[a-zA-Z]+ name="([^"]*)"') { $names.Add($Matches[1]) }
        elseif ($l -match '^\s*<[a-zA-Z]') { $names.Add('<unnamed>') }
        if ($l -match '^\s*<(int|string|short|float|double|long) name="([^"]*)" value="([^"]*)"\s*/>$') {
            # XML attribute escaping is the serializer's, not the data's - undo it before comparing.
            $v = $Matches[3] -replace '&lt;', '<' -replace '&gt;', '>' -replace '&quot;', '"' -replace '&apos;', "'" -replace '&#xD;', "`r" -replace '&#xA;', "`n" -replace '&amp;', '&'
            $scalars.Add("$($Matches[2])`t$(NormNum $Matches[1] $v)")
        }
    }
    [pscustomobject]@{ Names = $names; Scalars = $scalars }
}

function CompareImage([string]$label, $d, $x) {
    $bad = @()
    if ($d.Names.Count -ne $x.Names.Count) { $bad += "COUNT dump=$($d.Names.Count) xml=$($x.Names.Count)" }
    if ((($d.Names | Sort-Object) -join "`n") -cne (($x.Names | Sort-Object) -join "`n")) { $bad += 'NAME MULTISET differs' }
    if ($d.Scalars.Count -ne $x.Scalars.Count) { $bad += "SCALAR COUNT dump=$($d.Scalars.Count) xml=$($x.Scalars.Count)" }
    if ((($d.Scalars | Sort-Object) -join "`n") -cne (($x.Scalars | Sort-Object) -join "`n")) { $bad += 'SCALAR name=value MULTISET differs' }
    $bad
}

# ---- self-check -----------------------------------------------------------
$probe = $IMAGES[0]
$probeXml = [IO.File]::ReadAllText((Join-Path $Root $probe[2]), $U8)
$probeDump = DumpNodes $probe[0] $probe[1]
$control = CompareImage 'control' $probeDump (XmlNodes $probeXml)
# No `$` anchor: the file is CRLF and .NET's multiline `$` matches before the \n, i.e. AFTER the
# \r, so `/>$` never matches a line that ends `/>\r\n`.
$firstInt = [regex]::Match($probeXml, '(?m)^\s*<int name="[^"]*" value="(-?\d+)"/>')
if (-not $firstInt.Success) { throw 'self-check: no <int> leaf to corrupt in the probe image' }
$mutations = [ordered]@{
    'a node renamed'    = ($probeXml -creplace '(?m)^(\s*<\w+ name=")portal(")', '$1CORRUPT$2')
    'a node dropped'    = (($probeXml -split "`r`n" | Where-Object { $_ -notmatch '<\w+ name="foothold"' }) -join "`r`n")
    'a node duplicated' = ($probeXml -creplace '(?m)^(\s*)(<imgdir name="portal">)', "`$1`$2`r`n`$1`$2")
    'a SCALAR VALUE edited, name untouched' =
    $probeXml.Remove($firstInt.Groups[1].Index, $firstInt.Groups[1].Length).Insert($firstInt.Groups[1].Index, '999777')
}
$selfOk = ($control.Count -eq 0)
if (-not $selfOk) { "SELF-CHECK: FAIL - the unmutated control does not compare equal: $($control -join '; ')" }
foreach ($k in $mutations.Keys) {
    $bad = CompareImage $k $probeDump (XmlNodes $mutations[$k])
    "SELF-CHECK: $k -> caught = $($bad.Count -gt 0)  [$($bad -join '; ')]"
    if ($bad.Count -eq 0) { $selfOk = $false }
}
if (-not $selfOk) { 'RESULT: FAIL - comparator not proven, nothing was compared'; exit 2 }

# ---- the measurement ------------------------------------------------------
$totalNodes = 0; $totalScalars = 0
foreach ($img in $IMAGES) {
    $d = DumpNodes $img[0] $img[1]
    if ($d.MaxDepth -ge 29) { $fail++; "$($img[1]): FAIL - dump may have truncated (depth $($d.MaxDepth) at limit 30)" }
    $x = XmlNodes ([IO.File]::ReadAllText((Join-Path $Root $img[2]), $U8))
    $bad = CompareImage $img[1] $d $x
    $totalNodes += $d.Names.Count
    $totalScalars += $d.Scalars.Count
    if ($bad.Count) { $fail++; "$($img[1]): $($bad -join '; ')" }
}
"compared $($IMAGES.Count) images, $totalNodes nodes, $totalScalars scalar name=value pairs"
if ($fail) { "RESULT: FAIL ($fail)"; exit 1 } else { 'RESULT: PASS'; exit 0 }
