<#
.SYNOPSIS
    Build an isolated, localhost-routed GMS v84 client from GMSSetupv84.exe. Reproducible end to end.

.DESCRIPTION
    Ticket 20 (v84 recon / instrument-proofing). Every stage asserts before it acts:

      0. Guard      - snapshot the LIVE v83 client, and re-verify it at the end. This script must
                      never modify D:\games\MapleStory\. If it does, the run fails loudly.
      1. Verify     - SHA256 the installer against a known-good value.
      2. Carve      - the installer is a PE with TWO spanned MSZip cabinets appended
                      (MapleStory_1.cab + MapleStory_2.cab). Carve them at verified offsets.
      3. Extract    - 7-Zip follows the volume chain automatically from volume 1.
      4. Cross-check- extracted .wz must byte-match porting-resources\wz-data\v84\.
      5. Bypass     - install the Chronicle20/gms-83-dll GMS-84.1 ijl15.dll proxy + edits\.
      6. Route      - redirect.ini maps Nexon 63.251.217.2/.3/.4 -> 127.0.0.1:8484.

    The offsets in step 2 are hardcoded but SELF-CHECKING: the script asserts the MSCF magic and
    the cbCabinet field at each offset, so a different installer build fails rather than producing
    silent garbage. This matters - a previous tool in this project punched a five-index hole in an
    array and still reported clean.

.NOTES
    ABSOLUTE RULE: D:\games\MapleStory\ is the owner's live v83 client. READ-ONLY. Never written.
#>
[CmdletBinding()]
param(
    [string]$Installer   = 'D:\games\MapleStory\Server\porting-resources\clients\GMSSetupv84.exe',
    [string]$EvidenceWz  = 'D:\games\MapleStory\Server\porting-resources\wz-data\v84',
    [string]$LiveV83     = 'D:\games\MapleStory',
    [string]$Root        = 'D:\games\MSv84',
    [string]$SevenZip    = 'C:\Program Files\7-Zip\7z.exe',
    [switch]$SkipExtract,      # bypass/routing only; assumes client already extracted
    [switch]$Force             # re-extract over an existing client dir
)

$ErrorActionPreference = 'Stop'
function Step($m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "  [OK]   $m" -ForegroundColor Green }
function Die($m)  { Write-Host "  [FAIL] $m" -ForegroundColor Red; throw $m }

# Known-good SHA256 of GMSSetupv84.exe (1,846,289,344 bytes), measured 2026-08-16.
$INSTALLER_SHA = '6F0D3C355F8E6D7B2C2E94A9F87CD188E778807B1559ED3A2EDDE20CB0F5B51F'

# Cabinet layout inside the installer, verified by MSCF header scan.
$CABS = @(
    @{ Name = 'MapleStory_1.cab'; Offset = 6553734L;    Size = 1048576000L }
    @{ Name = 'MapleStory_2.cab'; Offset = 1055129734L; Size = 791157066L  }
)

# Client edits to deploy. Deliberately minimal: what it takes to reach the login screen,
# routed to localhost, nothing more. Add others from bypass\GMS-84.1\edits\ if wanted.
$EDITS = @(
    'bypass-1.0.0.dll'      # Themida / security hooks. REQUIRED.
    'redirect-1.0.0.dll'    # Winsock WSPPROC_TABLE hook -> localhost. REQUIRED.
    'redirect.ini'          # its config. REQUIRED.
    'no-patcher-1.0.0.dll'  # skip the patcher, whose Nexon servers are long dead. REQUIRED.
    'window-mode-1.0.0.dll' # windowed, so a failed launch does not trap the desktop.
    'skip-logo-1.0.0.dll'   # fewer moving parts between launch and login screen.
)

$clientDir = Join-Path $Root 'client'
$cabDir    = Join-Path $Root '_cab'
$bypassDir = Join-Path $Root 'bypass\GMS-84.1'

# ---------------------------------------------------------------- 0. guard
Step '0. Snapshot live v83 client (must be untouched at the end)'
if (-not (Test-Path $LiveV83)) { Die "live v83 client not found at $LiveV83" }
$liveBefore = Get-ChildItem $LiveV83 -File | Sort-Object Name |
    ForEach-Object { [pscustomobject]@{ N = $_.Name; H = (Get-FileHash $_.FullName -Algorithm SHA256).Hash } }
Ok "hashed $($liveBefore.Count) live files"

if ($Root.TrimEnd('\') -like ($LiveV83.TrimEnd('\') + '*')) {
    Die "refusing to run: output root '$Root' is inside the live client '$LiveV83'"
}
Ok "output root '$Root' is outside the live client"

if (-not $SkipExtract) {
    # ------------------------------------------------------------ 1. verify
    Step '1. Verify installer'
    if (-not (Test-Path $Installer)) { Die "installer not found: $Installer" }
    $len = (Get-Item $Installer).Length
    $sha = (Get-FileHash $Installer -Algorithm SHA256).Hash
    if ($sha -ne $INSTALLER_SHA) { Die "installer SHA256 mismatch`n    expected $INSTALLER_SHA`n    actual   $sha" }
    Ok "GMSSetupv84.exe $len bytes, SHA256 matches"

    # ------------------------------------------------------------ 2. carve
    Step '2. Carve spanned cabinets'
    if ((Test-Path $clientDir) -and -not $Force) {
        Die "client dir already exists: $clientDir  (re-run with -Force to overwrite, or -SkipExtract)"
    }
    New-Item -ItemType Directory -Force -Path $cabDir | Out-Null
    $fs = [IO.File]::OpenRead($Installer)
    try {
        foreach ($c in $CABS) {
            # self-check the offset before trusting it
            $fs.Position = $c.Offset
            $hdr = [byte[]]::new(16); $null = $fs.Read($hdr, 0, 16)
            $magic = -join ($hdr[0..3] | ForEach-Object { [char]$_ })
            if ($magic -ne 'MSCF') { Die "no MSCF magic at offset $($c.Offset) for $($c.Name); got '$magic'" }
            $cb = [BitConverter]::ToUInt32($hdr, 8)
            if ($cb -ne $c.Size) { Die "$($c.Name): cbCabinet=$cb but expected $($c.Size)" }

            $out = Join-Path $cabDir $c.Name
            $fs.Position = $c.Offset
            $os = [IO.File]::Create($out)
            try {
                $buf = [byte[]]::new(4MB); $left = $c.Size
                while ($left -gt 0) {
                    $got = $fs.Read($buf, 0, [Math]::Min($buf.Length, $left))
                    if ($got -le 0) { Die "short read carving $($c.Name), $left bytes remaining" }
                    $os.Write($buf, 0, $got); $left -= $got
                }
            } finally { $os.Close() }
            Ok "$($c.Name)  offset=$($c.Offset)  size=$($c.Size)  MSCF+cbCabinet verified"
        }
    } finally { $fs.Close() }

    # ------------------------------------------------------------ 3. extract
    Step '3. Extract cabinet set'
    if (-not (Test-Path $SevenZip)) { Die "7-Zip not found at $SevenZip" }
    if (Test-Path $clientDir) { Remove-Item $clientDir -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $clientDir | Out-Null
    & $SevenZip x (Join-Path $cabDir 'MapleStory_1.cab') "-o$clientDir" -y | Out-Null
    if ($LASTEXITCODE -ne 0) { Die "7-Zip failed with exit code $LASTEXITCODE" }
    $n = (Get-ChildItem $clientDir -Recurse -File).Count
    if ($n -ne 52) { Die "expected 52 extracted files, got $n" }
    Ok "extracted $n files to $clientDir"
}

# ---------------------------------------------------------------- 4. cross-check
Step '4. Cross-check extracted .wz against hash-verified evidence set'
$wz = Get-ChildItem $EvidenceWz -Filter *.wz | Sort-Object Name
if ($wz.Count -eq 0) { Die "no .wz found in $EvidenceWz" }
$bad = 0
foreach ($f in $wz) {
    $t = Join-Path $clientDir $f.Name
    if (-not (Test-Path $t)) { Write-Host "  [FAIL] missing $($f.Name)" -ForegroundColor Red; $bad++; continue }
    if ((Get-FileHash $f.FullName -Algorithm SHA256).Hash -ne (Get-FileHash $t -Algorithm SHA256).Hash) {
        Write-Host "  [FAIL] hash mismatch $($f.Name)" -ForegroundColor Red; $bad++
    }
}
if ($bad) { Die "$bad of $($wz.Count) .wz did not match the evidence set" }
Ok "$($wz.Count)/$($wz.Count) .wz byte-identical to $EvidenceWz"

# ---------------------------------------------------------------- 5. bypass
Step '5. Install gms-83-dll GMS-84.1 proxy and edits'
if (-not (Test-Path $bypassDir)) { Die "bypass release not found: $bypassDir" }
$proxy = Join-Path $bypassDir 'ijl15.dll'
if (-not (Test-Path $proxy)) { Die "proxy ijl15.dll not found in $bypassDir" }

$stock = Join-Path $clientDir 'ijl15.dll'
$bak   = Join-Path $clientDir 'ijl15.dll.bak'
if ((Test-Path $stock) -and -not (Test-Path $bak)) {
    Copy-Item $stock $bak
    Ok "backed up stock ijl15.dll -> ijl15.dll.bak"
}
Copy-Item $proxy $stock -Force
Ok "installed proxy ijl15.dll ($((Get-Item $stock).Length) bytes)"

$editsDst = Join-Path $clientDir 'edits'
New-Item -ItemType Directory -Force -Path $editsDst | Out-Null
foreach ($e in $EDITS) {
    $src = Join-Path $bypassDir "edits\$e"
    if (-not (Test-Path $src)) { Die "edit not found in release: $e" }
    Copy-Item $src (Join-Path $editsDst $e) -Force
    Ok "edits\$e"
}
$skipped = (Get-ChildItem (Join-Path $bypassDir 'edits') -File | Where-Object { $EDITS -notcontains $_.Name }).Name
if ($skipped) { Write-Host "  [note] not deployed: $($skipped -join ', ')" -ForegroundColor DarkGray }

# ---------------------------------------------------------------- 6. routing
Step '6. Verify localhost routing config'
$ini = Join-Path $editsDst 'redirect.ini'
$txt = Get-Content $ini -Raw
Write-Host ($txt -split "`r?`n" | ForEach-Object { "    $_" }) -Separator "`n"
foreach ($needle in @('63.251.217.2', '63.251.217.3', '63.251.217.4', 'RedirectIP=127.0.0.1', 'RedirectPort=8484')) {
    if ($txt -notmatch [regex]::Escape($needle)) { Die "redirect.ini missing expected entry: $needle" }
}
Ok 'redirect.ini maps all three Nexon IPs -> 127.0.0.1:8484'

# ---------------------------------------------------------------- 0b. guard re-check
Step '0b. Re-verify the live v83 client was not modified'
$liveAfter = Get-ChildItem $LiveV83 -File | Sort-Object Name |
    ForEach-Object { [pscustomobject]@{ N = $_.Name; H = (Get-FileHash $_.FullName -Algorithm SHA256).Hash } }
$diff = Compare-Object $liveBefore $liveAfter -Property N, H
if ($diff) { $diff | Format-Table -AutoSize; Die 'LIVE v83 CLIENT WAS MODIFIED - this must never happen' }
Ok "live v83 client unchanged: 0 differences across $($liveAfter.Count) files"

Write-Host "`nv84 client ready: $clientDir" -ForegroundColor Green
Write-Host "launch with:     $clientDir\MapleStory.exe" -ForegroundColor Green
