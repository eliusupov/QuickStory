# FakeV84Login.ps1 - minimal MapleStory "hello" responder announcing version 84.
# Byte layout copied from Cosmic's PacketCreator.getHello (PacketCreator.java:600-610):
#     short 0x0E            length of remainder
#     short mapleVersion    84
#     short 1               length of patch string
#     byte  49              '1'
#     4 bytes recvIv
#     4 bytes sendIv
#     byte  8               locale
# Decode order matches gms-83-dll bypass/socket_hooks.cpp decode_handshake().
# Purpose: prove the v84 client accepts a v84 handshake and renders its login screen,
# without touching the owner's running v83 Cosmic server on 8484.
param([int]$Port = 8485, [int]$Version = 84, [int]$Seconds = 60, [int]$Hold = 25, [string]$Log = "$env:TEMP\fakev84.log")

function Hello([int]$ver) {
    $recvIv = [byte[]]@(0x52, 0x30, 0x78, 0x61)
    $sendIv = [byte[]]@(0x14, 0x33, 0x2A, 0x7B)
    $p = New-Object Collections.Generic.List[byte]
    $p.AddRange([BitConverter]::GetBytes([int16]0x0E))
    $p.AddRange([BitConverter]::GetBytes([int16]$ver))
    $p.AddRange([BitConverter]::GetBytes([int16]1))
    $p.Add(49)
    $p.AddRange($recvIv)
    $p.AddRange($sendIv)
    $p.Add(8)
    return $p.ToArray()
}

$pkt = Hello $Version
"[$(Get-Date -f HH:mm:ss.fff)] hello packet ($($pkt.Length) bytes): $(($pkt | ForEach-Object { $_.ToString('x2') }) -join ' ')" | Set-Content $Log

$l = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Parse('127.0.0.1'), $Port)
try { $l.Start() } catch { "[$(Get-Date -f HH:mm:ss.fff)] LISTEN FAILED: $($_.Exception.Message)" | Add-Content $Log; return }
"[$(Get-Date -f HH:mm:ss.fff)] listening on 127.0.0.1:$Port announcing version $Version" | Add-Content $Log

$deadline = (Get-Date).AddSeconds($Seconds)
while ((Get-Date) -lt $deadline) {
    if ($l.Pending()) {
        $c = $l.AcceptTcpClient()
        "[$(Get-Date -f HH:mm:ss.fff)] ACCEPTED from $($c.Client.RemoteEndPoint)" | Add-Content $Log
        $s = $c.GetStream()
        $s.Write($pkt, 0, $pkt.Length); $s.Flush()
        "[$(Get-Date -f HH:mm:ss.fff)]   sent hello v$Version" | Add-Content $Log
        # read whatever the client replies (it will be AES-OFB encrypted; we only care that it replies)
        $c.ReceiveTimeout = 4000
        try {
            $b = [byte[]]::new(512); $n = $s.Read($b, 0, $b.Length)
            if ($n -gt 0) {
                "[$(Get-Date -f HH:mm:ss.fff)]   client replied $n bytes: $((($b[0..([Math]::Min($n,32)-1)]) | ForEach-Object { $_.ToString('x2') }) -join ' ')" | Add-Content $Log
            } else { "[$(Get-Date -f HH:mm:ss.fff)]   client sent nothing" | Add-Content $Log }
        } catch { "[$(Get-Date -f HH:mm:ss.fff)]   no reply within timeout" | Add-Content $Log }
        # keep the socket open; closing it makes the client abort the login screen
        Start-Sleep -Seconds $Hold
        $c.Close()
        "[$(Get-Date -f HH:mm:ss.fff)]   closed" | Add-Content $Log
    }
    Start-Sleep -Milliseconds 100
}
$l.Stop()
"[$(Get-Date -f HH:mm:ss.fff)] stopped" | Add-Content $Log
