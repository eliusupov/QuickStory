<#
.SYNOPSIS
  Ticket 01b/11 - NOP BOTH Evan job gates in the running MapleStory.exe.

.DESCRIPTION
  MapleStory.exe is Themida-packed: its code section is compressed on disk (raw 0x2DD000 <<
  virtual 0x7F8000), so the gates only exist in memory after unpacking. This patches them there.

  THERE ARE TWO GATES, NOT ONE (ticket 11). Both test the same thing - skillid/10000/100 == 22,
  or skillid/10000 == 2001 - and both reject Evan:

    0x00761714  CSkillInfo::GetSkillLevel  -> returns level 0        (the gate 01b found)
    0x0075C776  CSkillInfo::GetSkill       -> returns a NULL entry   (ticket 11; 85 call sites)

  Patching only 0x00761714 achieves nothing on its own: GetSkillLevel's fall-through path calls
  GetSkill at 0x007617BA to fetch the entry, gets NULL from the second gate, and returns 0 anyway.
  Worse, it makes GetSkillLevel report level 1 for Evan's four beginner-common ids
  (20011009/10/11, 20011020) while leaving *ppEntry NULL, which is a crash waiting for any caller
  that branches on level and then dereferences the entry. At least one skill-icon draw path does
  exactly that unguarded: 0x008AA04D calls GetSkill and passes the result straight to 0x008F25D0,
  which dereferences it at 0x008F2600 with no null check.

  Polls each address until its expected pattern appears, writes the patch and reads back to
  confirm. Polling replaces the config.ini "sleepTime" tuning knob - start this script before
  (or after) launching the game and it waits for the right moment on its own.

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
    [switch] $SelfTest,             # verify the constants against local.exe on disk, then exit
    [switch] $Watch                 # stay resident: re-patch every client launch, forever
)

$ErrorActionPreference = 'Stop'

# --- constants -------------------------------------------------------------------------------
# File offset in local.exe (an unpacked memory dump of this same image, where file offset == RVA)
# + ImageBase 0x400000. ASLR is off (DllCharacteristics 0x0000), so the VAs are fixed.
#
# Gate 1, CSkillInfo::GetSkillLevel @0x007616F6 - blanket NOP; both jz land on the same "return 0"
# at 0x007617F4, so removing them falls through to the real lookup.
#
# Gate 2, CSkillInfo::GetSkill @0x0075C755 - surgical, NOT a blanket NOP. The bytes are
#   83 F8 16 | 74 08 | 81 FE D1 07 00 00 | 75 04 | 33 C0 EB 2E
#   cmp eax,22 ; jz reject ; cmp esi,2001 ; jnz normal ; xor eax,eax (reject) ; jmp exit
# NOPing all of it would fall INTO the reject and return NULL for every skill in the game. So:
# kill the jz (74 08 -> 90 90) and make the jnz unconditional (75 04 -> EB 04, +4 from 0x0075C783
# = 0x0075C787, the normal path). Same length in and out, so the read-back verify still works.
# ORDER IS A SAFETY PROPERTY, NOT A STYLE CHOICE. The loop below writes gates in array order and
# stops at the first one that times out, so whichever comes first is the one that can be left
# applied on its own. GetSkillLevel-alone is the crash state described above (level 1 + NULL
# entry -> unguarded deref). GetSkill-alone is harmless: real entries come back, GetSkillLevel
# still returns 0, so Evan skills merely stay inert. Patch the harmless-if-partial one FIRST.
$DumpPath = 'D:\games\MapleStory\local.exe'
$Gates = @(
    @{ Name  = 'GetSkill'
       VA    = 0x0075C776
       Off   = 0x0035C776
       Read  = [byte[]]@(0x83,0xF8,0x16,0x74,0x08,0x81,0xFE,0xD1,0x07,0x00,0x00,0x75,0x04,0x33,0xC0,0xEB,0x2E)
       Write = [byte[]]@(0x83,0xF8,0x16,0x90,0x90,0x81,0xFE,0xD1,0x07,0x00,0x00,0xEB,0x04,0x33,0xC0,0xEB,0x2E) }
    @{ Name  = 'GetSkillLevel'
       VA    = 0x00761714
       Off   = 0x00361714
       Read  = [byte[]]@(0x83,0xF8,0x16,0x0F,0x84,0xD7,0x00,0x00,0x00,
                         0x81,0xFE,0xD1,0x07,0x00,0x00,0x0F,0x84,0xCB,0x00,0x00,0x00)
       Write = [byte[]]@(0x90) * 21 }
)

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

    foreach ($g in $Gates) {
        $n = $g.Read.Length
        $onDisk = $d[$g.Off..($g.Off + $n - 1)]
        if ((Hex $onDisk) -ne (Hex $g.Read)) { Log "SELFTEST FAIL: $($g.Name) local.exe@0x$('{0:X}' -f $g.Off) = $(Hex $onDisk)"; $fail++ }
        else { Log "SELFTEST ok: $($g.Name) pattern matches local.exe at 0x$('{0:X}' -f $g.Off)" }

        if ($g.VA -ne ($g.Off + 0x400000)) { Log "SELFTEST FAIL: $($g.Name) VA/offset mismatch"; $fail++ }
        else { Log "SELFTEST ok: $($g.Name) VA 0x$('{0:X}' -f $g.VA) == offset + ImageBase" }

        if ($g.Write.Length -ne $n) { Log "SELFTEST FAIL: $($g.Name) length mismatch"; $fail++ }
        else { Log "SELFTEST ok: $($g.Name) $n bytes in, $n bytes out" }

        if ((Hex $g.Write) -eq (Hex $g.Read)) { Log "SELFTEST FAIL: $($g.Name) patch is a no-op"; $fail++ }

        # the pattern must be unique in the dump, otherwise "search" and "seek" disagree
        $hits = 0
        for ($i = 0; $i -le $d.Length - $n; $i++) {
            if ($d[$i] -ne $g.Read[0]) { continue }
            $same = $true
            for ($k = 1; $k -lt $n; $k++) { if ($d[$i+$k] -ne $g.Read[$k]) { $same = $false; break } }
            if ($same) { $hits++ }
        }
        if ($hits -ne 1) { Log "SELFTEST FAIL: $($g.Name) pattern occurs $hits times, expected 1"; $fail++ }
        else { Log "SELFTEST ok: $($g.Name) pattern is unique in the image" }
    }

    # The GetSkill patch is the only one whose correctness is arithmetic rather than "all 0x90".
    # Assert the jump it rewrites actually lands on the normal path, or the patch returns NULL for
    # EVERY skill in the game instead of enabling Evan.
    $g2 = $Gates | Where-Object { $_.Name -eq 'GetSkill' }
    $jmpAt   = $g2.VA + 11           # the 75 04 / EB 04 site, 0x0075C781
    $landsOn = $jmpAt + 2 + $g2.Write[12]
    if ($g2.Write[11] -ne 0xEB -or $landsOn -ne 0x0075C787) {
        Log "SELFTEST FAIL: GetSkill jmp lands on 0x$('{0:X}' -f $landsOn), expected 0x0075C787"; $fail++
    } else { Log 'SELFTEST ok: GetSkill jmp lands on the normal path at 0x0075C787' }
    if ($g2.Write[3] -ne 0x90 -or $g2.Write[4] -ne 0x90) { Log 'SELFTEST FAIL: GetSkill jz not NOPed'; $fail++ }
    else { Log 'SELFTEST ok: GetSkill jz-to-reject is NOPed' }

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

Log "=== ticket 01b/11 runtime patch === $($Gates.Count) gates: $(($Gates | ForEach-Object { "$($_.Name)@0x$('{0:X8}' -f $_.VA)" }) -join ', '), DryRun=$DryRun, Watch=$Watch"

# The launcher spawns SEVERAL client processes within a few seconds, and the one that survives is
# not necessarily the first. Tracking a single PID patched the wrong process and then blocked on it.
# So: patch every client process we see, once each, and never wait on any one of them.
# MapleStory.exe ONLY. local/localhome(.evan).exe are ImpREC memory dumps, not clients: they
# self-relaunch with "GameLaunching" forever, each process dying in under a second, and never open
# a window. Watching them buried the log under ~2 bogus patches/second.
$ClientNames = @('MapleStory')

function Invoke-GatePatch {
    param($proc, [int]$AttemptSeconds)

    $h = [W.K32]::OpenProcess($PROCESS_ACCESS, $false, $proc.Id)
    if ($h -eq [IntPtr]::Zero) {
        $e = [Runtime.InteropServices.Marshal]::GetLastWin32Error()
        Log "SKIP PID $($proc.Id): OpenProcess failed, win32 $e (5 = access denied - run as Administrator)"
        return 3
    }
    Log "attached to PID $($proc.Id) ($($proc.ProcessName))"

# --- poll, guard, write, verify -----------------------------------------------------------------
# Every gate must land. A partial patch is worse than none: gate 1 alone makes GetSkillLevel
# report level 1 for Evan's beginner-common ids while GetSkill still hands out NULL entries.
$done = 0

try {
  foreach ($g in $Gates) {
    # Short per-GATE attempt, not per-process: the address is unreadable until Themida finishes
    # unpacking, and the outer scan loop retries anyway. Sharing one budget across gates meant the
    # first gate ate it and the second timed out on every first pass. A long block here would
    # starve every other client process.
    $deadline = (Get-Date).AddSeconds($AttemptSeconds)
    $n        = $g.Read.Length
    $addr     = [IntPtr]::new($g.VA)
    $size     = [UIntPtr]::new($n)
    $buf      = New-Object byte[] $n
    $lastSeen = ''
    $hit      = $false

    while ((Get-Date) -lt $deadline) {
        $read = 0
        if (-not [W.K32]::ReadProcessMemory($h, $addr, $buf, $n, [ref]$read) -or $read -ne $n) {
            if ($lastSeen -ne 'unreadable') { Log "waiting on $($g.Name): address not readable yet (win32 $([Runtime.InteropServices.Marshal]::GetLastWin32Error()))"; $lastSeen = 'unreadable' }
            Start-Sleep -Milliseconds 250; continue
        }

        $seen = Hex $buf
        if ($seen -ne $lastSeen) { Log "read $($g.Name): $seen"; $lastSeen = $seen }

        if ($seen -eq (Hex $g.Write)) { Log "RESULT: $($g.Name) gate at 0x$('{0:X8}' -f $g.VA) is already patched."; $hit = $true; break }

        if ($seen -ne (Hex $g.Read)) { Start-Sleep -Milliseconds 250; continue }   # GUARD: never write over unknown bytes

        Log "GUARD PASS: $($g.Name) gate pattern found at 0x$('{0:X8}' -f $g.VA)."
        if ($DryRun) { Log "RESULT: dry run - $($g.Name) address confirmed, nothing written."; $hit = $true; break }

        $old = 0
        if (-not [W.K32]::VirtualProtectEx($h, $addr, $size, $PAGE_EXECUTE_RW, [ref]$old)) {
            Log "VirtualProtectEx failed (win32 $([Runtime.InteropServices.Marshal]::GetLastWin32Error())) - section is already RWX, writing anyway"
        }
        $written = 0
        $ok = [W.K32]::WriteProcessMemory($h, $addr, $g.Write, $n, [ref]$written)
        $werr = [Runtime.InteropServices.Marshal]::GetLastWin32Error()
        if ($old) { $dummy = 0; [void][W.K32]::VirtualProtectEx($h, $addr, $size, $old, [ref]$dummy) }
        [void][W.K32]::FlushInstructionCache($h, $addr, $size)

        if (-not $ok -or $written -ne $n) { Log "write failed (win32 $werr, wrote $written) - retrying"; Start-Sleep -Milliseconds 250; continue }

        [void][W.K32]::ReadProcessMemory($h, $addr, $buf, $n, [ref]$read)
        $back = Hex $buf
        if ($back -eq (Hex $g.Write)) { Log "RESULT: $($g.Name) PATCHED and verified at 0x$('{0:X8}' -f $g.VA) - $back"; $hit = $true; break }

        Log "verify FAILED for $($g.Name), read back: $back - Themida re-verified or re-encrypted the region, retrying"
        $lastSeen = $back
        Start-Sleep -Milliseconds 250
    }

    if (-not $hit) { Log "TIMEOUT on $($g.Name) at 0x$('{0:X8}' -f $g.VA)"; break }
    $done++
  }
}
finally { [void][W.K32]::CloseHandle($h) }

if ($done -eq $Gates.Count) { return 0 }
Log "INCOMPLETE: $done of $($Gates.Count) gates patched in PID $($proc.Id) - this process is NOT Evan-safe"
return 4
}   # end Invoke-GatePatch

# --- scan ---------------------------------------------------------------------------------------
$seenPids = @{}          # PID -> $true once patched, so we never re-patch or re-log the same process
$patched  = 0
$deadline = if ($Watch) { [datetime]::MaxValue } else { (Get-Date).AddSeconds($WaitProcess) }

while ((Get-Date) -lt $deadline) {
    $procs = Get-Process -ErrorAction SilentlyContinue |
             Where-Object { $ClientNames -contains $_.ProcessName -and -not $seenPids.ContainsKey($_.Id) }

    foreach ($p in $procs) {
        # A per-attempt slice: enough for a process that has finished unpacking, short enough that
        # a stuck one does not hide its siblings. Unfinished processes come back around next scan.
        $rc = Invoke-GatePatch -proc $p -AttemptSeconds ([math]::Min(5, $Timeout))
        if ($rc -eq 0) {
            $seenPids[$p.Id] = $true
            $patched++
            Log "WATCH: $patched process(es) patched. Watching for more."
        }
    }

    if (-not $Watch -and $patched -gt 0) { break }
    Start-Sleep -Milliseconds 500
}

if ($patched -eq 0) { Log "RESULT: nothing patched. Client names searched: $($ClientNames -join ', ')"; exit 2 }
exit 0
