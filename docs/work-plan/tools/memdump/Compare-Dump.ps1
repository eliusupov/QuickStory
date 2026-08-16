# Compare a MemDump blob (offset 0 == module base VA) against the PE file on disk,
# section by section. Prints identical-byte ratio per section.
#   - a section that matches ~100% proves the read landed on the right image at the right address
#   - a section that matches poorly is either relocated, self-modified, or (for Themida) unpacked
param(
  [Parameter(Mandatory=$true)][string]$File,
  [Parameter(Mandatory=$true)][string]$Blob,
  [int]$SampleMax = 0   # 0 = compare every byte
)

$f = [IO.File]::ReadAllBytes($File)
$b = [IO.File]::ReadAllBytes($Blob)

$peOff = [BitConverter]::ToInt32($f, 0x3C)
if ([BitConverter]::ToUInt32($f, $peOff) -ne 0x00004550) { throw "not a PE" }
$nSec  = [BitConverter]::ToUInt16($f, $peOff + 6)
$optSz = [BitConverter]::ToUInt16($f, $peOff + 20)
$opt   = $peOff + 24
$magic = [BitConverter]::ToUInt16($f, $opt)
$imageBase = if ($magic -eq 0x20b) { [BitConverter]::ToUInt64($f, $opt + 24) } else { [uint64][BitConverter]::ToUInt32($f, $opt + 28) }
Write-Output ("file={0}  ImageBase=0x{1:X}  sections={2}  blobLen=0x{3:X}" -f (Split-Path $File -Leaf), $imageBase, $nSec, $b.Length)

$secTbl = $opt + $optSz
for ($i = 0; $i -lt $nSec; $i++) {
  $s = $secTbl + $i * 40
  $name  = ([Text.Encoding]::ASCII.GetString($f, $s, 8)).TrimEnd([char]0)
  $vsize = [BitConverter]::ToUInt32($f, $s + 8)
  $vaddr = [BitConverter]::ToUInt32($f, $s + 12)
  $rsize = [BitConverter]::ToUInt32($f, $s + 16)
  $rptr  = [BitConverter]::ToUInt32($f, $s + 20)

  $n = [Math]::Min([Math]::Min($vsize, $rsize), [Math]::Max(0, $b.Length - $vaddr))
  $n = [Math]::Min($n, [Math]::Max(0, $f.Length - $rptr))
  $same = 0; $zero = 0
  for ($k = 0; $k -lt $n; $k++) {
    if ($b[$vaddr + $k] -eq $f[$rptr + $k]) { $same++ }
    if ($b[$vaddr + $k] -eq 0) { $zero++ }
  }
  $pct  = if ($n) { [Math]::Round(100.0 * $same / $n, 2) } else { 0 }
  $zpct = if ($n) { [Math]::Round(100.0 * $zero / $n, 2) } else { 0 }
  Write-Output ("{0,-9} VA=0x{1:X6} VSize=0x{2:X6} Raw=0x{3:X6}@0x{4:X6}  cmp={5,-9} same={6,6}%  memzero={7,6}%" -f $name, $vaddr, $vsize, $rsize, $rptr, $n, $pct, $zpct)
}
