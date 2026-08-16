# ClientProbe.ps1 - launch the v84 client and record what it ACTUALLY does.
# Tracks by process NAME (not just the launched PID) because a launcher stub can exit
# immediately while the real app runs on under a different PID - the exact failure mode
# that made this project misread "0-second lifetime" as "crash".
param(
    [string]$Exe = 'D:\games\MSv84\client\MapleStory.exe',
    [int]$Seconds = 30,
    [switch]$NoKill
)
$name = [IO.Path]::GetFileNameWithoutExtension($Exe)
$dir  = Split-Path -Parent $Exe

$pre = @(Get-Process -Name $name -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id)
Write-Output "pre-existing '$name' processes: $($pre.Count)"

$t0 = Get-Date
$p = Start-Process -FilePath $Exe -WorkingDirectory $dir -PassThru
Write-Output "launched pid=$($p.Id) at $($t0.ToString('HH:mm:ss.fff'))"

$pidsSeen = @{}; $windows = @{}; $conns = @(); $errWindows = @{}
for ($i = 0; $i -lt ($Seconds * 4); $i++) {
    Start-Sleep -Milliseconds 250
    $t = ((Get-Date) - $t0).TotalSeconds

    foreach ($a in @(Get-Process -Name $name -ErrorAction SilentlyContinue)) {
        if (-not $pidsSeen.ContainsKey($a.Id)) { $pidsSeen[$a.Id] = [Math]::Round($t,2) }
        try { $a.Refresh() } catch {}
        if ($a.MainWindowHandle -ne 0 -and -not $windows.ContainsKey($a.Id)) {
            $windows[$a.Id] = "t=$([Math]::Round($t,2))s title='$($a.MainWindowTitle)'"
        }
    }
    # any dialog box the client throws (Themida errors, "cannot connect", etc.)
    foreach ($w in @(Get-Process -ErrorAction SilentlyContinue | Where-Object {
            $_.MainWindowHandle -ne 0 -and $_.StartTime -gt $t0 -and $_.ProcessName -ne 'powershell' })) {
        $k = "$($w.ProcessName)#$($w.Id)"
        if (-not $errWindows.ContainsKey($k)) { $errWindows[$k] = "t=$([Math]::Round($t,2))s title='$($w.MainWindowTitle)'" }
    }
    foreach ($c in @(Get-NetTCPConnection -ErrorAction SilentlyContinue |
            Where-Object { $pidsSeen.ContainsKey($_.OwningProcess) })) {
        $sig = "$($c.OwningProcess) -> $($c.RemoteAddress):$($c.RemotePort) [$($c.State)]"
        if ($conns -notcontains $sig) { $conns += $sig; Write-Output "  t=$([Math]::Round($t,2))s CONN $sig" }
    }
}

Write-Output "`n----- RESULT -----"
Write-Output "distinct '$name' PIDs seen : $($pidsSeen.Count)  -> $(($pidsSeen.GetEnumerator() | ForEach-Object { "$($_.Key)@$($_.Value)s" }) -join ', ')"
Write-Output "windows on '$name'         : $($windows.Count)"
$windows.GetEnumerator() | ForEach-Object { Write-Output "    pid $($_.Key): $($_.Value)" }
Write-Output "new windowed processes     :"
$errWindows.GetEnumerator() | ForEach-Object { Write-Output "    $($_.Key): $($_.Value)" }
Write-Output "TCP connections            : $($conns.Count)"
$conns | ForEach-Object { Write-Output "    $_" }
$alive = @(Get-Process -Name $name -ErrorAction SilentlyContinue | Where-Object { $pre -notcontains $_.Id })
Write-Output "still alive at end         : $($alive.Count) -> $($alive.Id -join ',')"

if (-not $NoKill) {
    foreach ($a in $alive) { try { Stop-Process -Id $a.Id -Force -ErrorAction SilentlyContinue } catch {} }
    if ($alive.Count) { Write-Output "cleanup: killed $($alive.Count)" }
}
