# StormProbe.ps1 - find out WHO is spawning the MapleStory processes during a respawn storm.
#
# ClientProbe.ps1 established THAT the v84 client storms (19 PIDs in 25s). It cannot say WHY,
# because it only counts processes. This one captures, for every client-family process that
# appears: its PID, its PARENT pid and parent name, its command line, and its exit code.
#
# That distinction is the whole diagnosis:
#   MapleStory.exe   spawned by MapleStory.exe  -> the client is relaunching ITSELF
#   MapleStory.exe   spawned by ASPLnchr/GameLauncher/Patcher -> a launcher loop
#   MapleStory.exe   spawned by explorer/powershell only, then dying -> it just crashes, no loop
#
# INSTRUMENT NOTE, learned the hard way on this project: tracked-PID lifetime and exit code are
# LYING instruments here - notepad.exe on Win11 exits in 0.44s with code 0 while the real app runs
# under a different PID. So this records the whole family by name and never trusts a single PID.
param(
    [string]$Exe     = 'D:\games\MSv84\client\MapleStory.exe',
    [int]$Seconds    = 20,
    [string[]]$Names = @('MapleStory', 'ASPLnchr', 'GameLauncher', 'Patcher')
)

$dir = Split-Path -Parent $Exe
$t0  = Get-Date
$seen = @{}

Write-Output "watching $($Names -join ', ') for ${Seconds}s from $($t0.ToString('HH:mm:ss.fff'))"
Write-Output "launching $Exe"
$null = Start-Process -FilePath $Exe -WorkingDirectory $dir -PassThru

$filter = ($Names | ForEach-Object { "Name='$_.exe'" }) -join ' OR '

for ($i = 0; $i -lt ($Seconds * 10); $i++) {
    Start-Sleep -Milliseconds 100
    foreach ($proc in @(Get-CimInstance Win32_Process -Filter $filter -ErrorAction SilentlyContinue)) {
        if ($seen.ContainsKey($proc.ProcessId)) { continue }
        $t = [Math]::Round(((Get-Date) - $t0).TotalSeconds, 2)

        $parentName = '?'
        try {
            $par = Get-CimInstance Win32_Process -Filter "ProcessId=$($proc.ParentProcessId)" -ErrorAction Stop
            if ($par) { $parentName = $par.Name }
        } catch { $parentName = '<gone>' }

        $seen[$proc.ProcessId] = $true
        $cmd = $proc.CommandLine
        if ($cmd -and $cmd.Length -gt 160) { $cmd = $cmd.Substring(0, 160) + '...' }
        Write-Output ("t={0,6}s  pid={1,-6} {2,-14} parent={3,-6} ({4})  cmd={5}" -f `
            $t, $proc.ProcessId, $proc.Name, $proc.ParentProcessId, $parentName, $cmd)
    }
}

Write-Output ""
Write-Output "----- SUMMARY -----"
Write-Output "distinct processes seen: $($seen.Count)"
$alive = @(Get-CimInstance Win32_Process -Filter $filter -ErrorAction SilentlyContinue)
Write-Output "still alive: $($alive.Count) -> $(($alive | ForEach-Object { "$($_.Name)#$($_.ProcessId)" }) -join ', ')"
foreach ($a in $alive) { try { Stop-Process -Id $a.ProcessId -Force -ErrorAction SilentlyContinue } catch {} }
Write-Output "(cleanup attempted; Themida-packed processes may refuse - re-check manually)"
