<#
.SYNOPSIS
  Ticket 01b - NOP the Evan job gate at VA 0x00761714 in the running MapleStory.exe.

.DESCRIPTION
  MapleStory.exe is Themida-packed: its code section is compressed on disk (raw 0x2DD000 <<
  virtual 0x7F8000), so the gate only exists in memory after unpacking. This patches it there.

  Polls the address until the expected 21-byte pattern appears, then writes 21x 0x90 and reads
  back to confirm. Polling replaces the config.ini "sleepTime" tuning knob - start this script
  before (or after) launching the game and it waits for the right moment on its own.

  Touches nothing on disk. Rollback = close the game.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\patch-evan-gate.ps1 -DryRun
  powershell -ExecutionPolicy Bypass -File tools\patch-evan-gate.ps1
#>
[CmdletBinding()]
param(
    [string] $ProcessName  = 'MapleStory',
    [int]    $WaitProcess  = 120,   # seconds to wait for the process to appear
    [int]    $Timeout      = 180,   # seconds to keep polling the address once attached
    [switch] $DryRun,               # read and report only, never write
    [switch] $SelfTest              # verify the constants against local.exe on disk, then exit
)

$ErrorActionPreference = 'Stop'

# --- constants -------------------------------------------------------------------------------
# file offset 0x361714 in local.exe (an unpacked memory dump of this same image, where
# file offset == RVA) + ImageBase 0x400000. ASLR is off (DllCharacteristics 0x0000).
$TargetVA   = 0x00761714
$Pattern    = [byte[]]@(0x83,0xF8,0x16,0x0F,0x84,0xD7,0x00,0x00,0x00,
                        0x81,0xFE,0xD1,0x07,0x00,0x00,0x0F,0x84,0xCB,0x00,0x00,0x00)
$Nops       = [byte[]]@(0x90) * 21
$DumpPath   = 'D:\games\MapleStory\local.exe'
$DumpOffset = 0x361714

$LogFile = Join-Path $PSScriptRoot 'evan-gate-patch.log'
function Log($msg) {
    $line = "[{0}] {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $msg
    Write-Host $line
    Add-Content -Path $LogFile -Value $line -Encoding utf8
}
function Hex($bytes) { if ($null -eq $bytes) { '<null>' } else { ($bytes | ForEach-Object { '{0:X2}' -f $_ }) -join ' ' } }

# --- self-test: constants vs the on-disk unpacked dump ----------------------------------------
if ($SelfTest) {
    $fail = 0
    $d = [System.IO.File]::ReadAllBytes($DumpPath)
    $onDisk = $d[$DumpOffset..($DumpOffset + 20)]
    if ((Hex $onDisk) -ne (Hex $Pattern)) { Log "SELFTEST FAIL: local.exe@0x361714 = $(Hex $onDisk)"; $fail++ }
    else { Log "SELFTEST ok: pattern matches local.exe at 0x$('{0:X}' -f $DumpOffset)" }

    if ($TargetVA -ne ($DumpOffset + 0x400000)) { Log "SELFTEST FAIL: VA/offset mismatch"; $fail++ }
    else { Log "SELFTEST ok: VA 0x$('{0:X}' -f $TargetVA) == offset + ImageBase" }

    if ($Nops.Length -ne $Pattern.Length) { Log "SELFTEST FAIL: length mismatch"; $fail++ }
    else { Log "SELFTEST ok: 21 bytes in, 21 bytes out" }

    # the pattern must be unique in the dump, otherwise "search" and "seek" disagree
    $hits = 0
    for ($i = 0; $i -le $d.Length - 21; $i++) {
        if ($d[$i] -eq 0x83 -and $d[$i+1] -eq 0xF8 -and $d[$i+2] -eq 0x16 -and $d[$i+3] -eq 0x0F -and
            $d[$i+4] -eq 0x84 -and $d[$i+9] -eq 0x81 -and $d[$i+10] -eq 0xFE -and $d[$i+11] -eq 0xD1) { $hits++ }
    }
    if ($hits -ne 1) { Log "SELFTEST FAIL: pattern occurs $hits times, expected 1"; $fail++ }
    else { Log "SELFTEST ok: pattern is unique in the image" }

    if ($fail) { Log "SELFTEST: $fail failure(s)"; exit 1 }
    Log 'SELFTEST: all pass'; exit 0
}

# --- win32 -----------------------------------------------------------------------------------
Add-Type -Namespace W -Name K32 -MemberDefinition @'
[DllImport("kernel32.dll", SetLastError=true)]
public static extern IntPtr OpenProcess(uint dwDesiredAccess, bool bInheritHandle, int dwProcessId);
[DllImport("kernel32.dll", SetLastError=true)]
public static extern bool ReadProcessMemory(IntPtr h, IntPtr addr, byte[] buf, int size, out int read);
[DllImport("kernel32.dll", SetLastError=true)]
public static extern bool WriteProcessMemory(IntPtr h, IntPtr addr, byte[] buf, int size, out int written);
[DllImport("kernel32.dll", SetLastError=true)]
public static extern bool VirtualProtectEx(IntPtr h, IntPtr addr, UIntPtr size, uint newProt, out uint oldProt);
[DllImport("kernel32.dll", SetLastError=true)]
public static extern bool FlushInstructionCache(IntPtr h, IntPtr addr, UIntPtr size);
[DllImport("kernel32.dll", SetLastError=true)]
public static extern bool CloseHandle(IntPtr h);
'@

$PROCESS_ACCESS      = 0x0438  # VM_OPERATION | VM_READ | VM_WRITE | QUERY_INFORMATION
$PAGE_EXECUTE_RW     = 0x40

Log "=== ticket 01b runtime patch === target VA 0x$('{0:X8}' -f $TargetVA), 21 bytes, DryRun=$DryRun"

# --- attach ----------------------------------------------------------------------------------
$deadline = (Get-Date).AddSeconds($WaitProcess)
do {
    $proc = Get-Process -Name $ProcessName -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($proc) { break }
    Start-Sleep -Milliseconds 500
} while ((Get-Date) -lt $deadline)

if (-not $proc) { Log "ABORT: no process named '$ProcessName' within ${WaitProcess}s"; exit 2 }
Log "attached to PID $($proc.Id) ($($proc.ProcessName))"

$h = [W.K32]::OpenProcess($PROCESS_ACCESS, $false, $proc.Id)
if ($h -eq [IntPtr]::Zero) {
    $e = [Runtime.InteropServices.Marshal]::GetLastWin32Error()
    Log "ABORT: OpenProcess failed, win32 error $e. Error 5 = access denied: run this PowerShell as Administrator (the client may be elevated), or Themida is blocking handle access - fall back to the CUSTOM.dll route."
    exit 3
}

# --- poll, guard, write, verify ---------------------------------------------------------------
$addr    = [IntPtr]::new($TargetVA)
$size    = [UIntPtr]::new(21)
$buf     = New-Object byte[] 21
$lastSeen = ''
$exit    = 4
$deadline = (Get-Date).AddSeconds($Timeout)

try {
    while ((Get-Date) -lt $deadline) {
        $read = 0
        if (-not [W.K32]::ReadProcessMemory($h, $addr, $buf, 21, [ref]$read) -or $read -ne 21) {
            if ($lastSeen -ne 'unreadable') { Log "waiting: address not readable yet (win32 $([Runtime.InteropServices.Marshal]::GetLastWin32Error()))"; $lastSeen = 'unreadable' }
            Start-Sleep -Milliseconds 250; continue
        }

        $seen = Hex $buf
        if ($seen -ne $lastSeen) { Log "read: $seen"; $lastSeen = $seen }

        if ($seen -eq (Hex $Nops)) { Log 'RESULT: already 21x 90 - gate is patched.'; $exit = 0; break }

        if ($seen -ne (Hex $Pattern)) { Start-Sleep -Milliseconds 250; continue }   # GUARD: never write over unknown bytes

        Log 'GUARD PASS: expected gate pattern found at 0x00761714.'
        if ($DryRun) { Log 'RESULT: dry run - address confirmed, nothing written.'; $exit = 0; break }

        $old = 0
        if (-not [W.K32]::VirtualProtectEx($h, $addr, $size, $PAGE_EXECUTE_RW, [ref]$old)) {
            Log "VirtualProtectEx failed (win32 $([Runtime.InteropServices.Marshal]::GetLastWin32Error())) - section is already RWX, writing anyway"
        }
        $written = 0
        $ok = [W.K32]::WriteProcessMemory($h, $addr, $Nops, 21, [ref]$written)
        $werr = [Runtime.InteropServices.Marshal]::GetLastWin32Error()
        if ($old) { $dummy = 0; [void][W.K32]::VirtualProtectEx($h, $addr, $size, $old, [ref]$dummy) }
        [void][W.K32]::FlushInstructionCache($h, $addr, $size)

        if (-not $ok -or $written -ne 21) { Log "write failed (win32 $werr, wrote $written) - retrying"; Start-Sleep -Milliseconds 250; continue }

        [void][W.K32]::ReadProcessMemory($h, $addr, $buf, 21, [ref]$read)
        $back = Hex $buf
        if ($back -eq (Hex $Nops)) { Log "RESULT: PATCHED and verified - 21x 90 at 0x00761714."; $exit = 0; break }

        Log "verify FAILED, read back: $back - Themida re-verified or re-encrypted the region, retrying"
        $lastSeen = $back
        Start-Sleep -Milliseconds 250
    }
    if ($exit -ne 0) { Log "RESULT: gave up after ${Timeout}s. Last bytes seen: $lastSeen" }
}
finally { [void][W.K32]::CloseHandle($h) }

exit $exit
