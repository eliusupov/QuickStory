# Minimal read-only PE reader: machine, timestamp, sections, export names, import DLLs.
# Usage: powershell -File pe.ps1 <path> [-Imports]
param([Parameter(Mandatory=$true)][string]$Path, [switch]$Imports)

$b = [System.IO.File]::ReadAllBytes($Path)
function U16($o){ [BitConverter]::ToUInt16($b,$o) }
function U32($o){ [BitConverter]::ToUInt32($b,$o) }

if ((U16 0) -ne 0x5A4D) { throw "not MZ" }
$pe = U32 0x3C
if ((U32 $pe) -ne 0x00004550) { throw "not PE" }
$machine = U16 ($pe+4)
$nsec    = U16 ($pe+6)
$tstamp  = U32 ($pe+8)
$optsz   = U16 ($pe+20)
$opt     = $pe+24
$magic   = U16 $opt
$isPE32P = ($magic -eq 0x20B)
$ddOff   = if ($isPE32P) { $opt+112 } else { $opt+96 }
$secOff  = $opt + $optsz

$sections = @()
for ($i=0; $i -lt $nsec; $i++) {
  $s = $secOff + $i*40
  $sections += [pscustomobject]@{
    Name = ([Text.Encoding]::ASCII.GetString($b,$s,8)).Trim([char]0)
    VSize = U32 ($s+8); VAddr = U32 ($s+12); RawSize = U32 ($s+16); RawPtr = U32 ($s+20)
  }
}
function RVA2Off($rva){
  foreach ($s in $sections) {
    if ($rva -ge $s.VAddr -and $rva -lt ($s.VAddr + [Math]::Max($s.VSize,$s.RawSize))) {
      return $s.RawPtr + ($rva - $s.VAddr)
    }
  }
  return -1
}
function CStr($off){
  $e=$off; while ($e -lt $b.Length -and $b[$e] -ne 0) { $e++ }
  [Text.Encoding]::ASCII.GetString($b,$off,$e-$off)
}

$epoch = [datetime]'1970-01-01Z'
Write-Output ("FILE     " + $Path)
Write-Output ("MACHINE  0x{0:X4}  MAGIC 0x{1:X3}  SECTIONS {2}" -f $machine,$magic,$nsec)
Write-Output ("TIMEDATE {0}  ({1:u})" -f $tstamp, $epoch.AddSeconds($tstamp))
foreach ($s in $sections) { Write-Output ("SECTION  {0,-9} vaddr=0x{1:X8} vsize={2,-10} raw={3}" -f $s.Name,$s.VAddr,$s.VSize,$s.RawSize) }

# --- exports (data directory 0)
$expRva = U32 $ddOff
$expSz  = U32 ($ddOff+4)
if ($expRva -eq 0) { Write-Output "EXPORTS  none" }
else {
  $e = RVA2Off $expRva
  if ($e -lt 0) { Write-Output "EXPORTS  unmapped rva 0x$('{0:X}' -f $expRva)" }
  else {
    $nameRva = U32 ($e+12); $ordBase = U32 ($e+16)
    $nFuncs = U32 ($e+20); $nNames = U32 ($e+24)
    $addrRva = U32 ($e+28); $namesRva = U32 ($e+32); $ordsRva = U32 ($e+36)
    Write-Output ("EXPORTS  dll='{0}' base={1} funcs={2} names={3}" -f (CStr (RVA2Off $nameRva)),$ordBase,$nFuncs,$nNames)
    $no = RVA2Off $namesRva; $oo = RVA2Off $ordsRva; $ao = RVA2Off $addrRva
    for ($i=0; $i -lt $nNames; $i++) {
      $nm = CStr (RVA2Off (U32 ($no+$i*4)))
      $ord = (U16 ($oo+$i*2)) + $ordBase
      $fa = U32 ($ao + ((U16 ($oo+$i*2))*4))
      if ($fa -ge $expRva -and $fa -lt ($expRva+$expSz)) {
        Write-Output ("  EXP {0,5}  {1}  -> FORWARD {2}" -f $ord,$nm,(CStr (RVA2Off $fa)))
      } else {
        Write-Output ("  EXP {0,5}  {1}  @0x{2:X8}" -f $ord,$nm,$fa)
      }
    }
  }
}

if ($Imports) {
  $impRva = U32 ($ddOff+8)
  if ($impRva -eq 0) { Write-Output "IMPORTS  none" }
  else {
    $o = RVA2Off $impRva
    while ($true) {
      $nameRva = U32 ($o+12)
      if ($nameRva -eq 0 -and (U32 $o) -eq 0) { break }
      if ($nameRva -eq 0) { break }
      Write-Output ("  IMP {0}" -f (CStr (RVA2Off $nameRva)))
      $o += 20
    }
  }
}
