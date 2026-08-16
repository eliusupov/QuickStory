# switch-client.ps1 - point the shared Wizet ExecPath at one client or the other.
#
# WHY THIS EXISTS
# Both the v83 Ezorsia client (D:\games\MapleStory) and the v84 client
# (D:\games\MSv84\client) read the SAME machine-wide registry value:
#
#     HKLM\SOFTWARE\WOW6432Node\Wizet\MapleStory\ExecPath
#
# MapleStory.exe writes its own directory there when it runs. So launching one
# client silently repoints the other, and the loser then looks for its WZ data -
# and, for Ezorsia, its config.ini - in the wrong folder and dies before it can
# draw a window. That is not a crash you can diagnose from the client; it just
# never appears.
#
# There is only one ExecPath, so the two clients genuinely cannot coexist. Flip
# it before switching clients.
#
# REQUIRES ELEVATION - HKLM is not user-writable.
#   Right-click PowerShell -> Run as administrator, then:
#     powershell -ExecutionPolicy Bypass -File <this file> -Client v83
#
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('v83', 'v84', 'show')]
    [string]$Client
)

$key = 'HKLM:\SOFTWARE\WOW6432Node\Wizet\MapleStory'
$paths = @{
    v83 = 'D:\games\MapleStory'        # Ezorsia V2 HD client - the working fallback
    v84 = 'D:\games\MSv84\client'      # the v84 migration target
}

if (-not (Test-Path $key)) { Write-Error "Registry key not found: $key"; exit 1 }

$current = (Get-ItemProperty -Path $key -Name ExecPath -ErrorAction SilentlyContinue).ExecPath
Write-Output "current ExecPath : $current"

if ($Client -eq 'show') {
    $hkcu = (Get-ItemProperty -Path 'HKCU:\SOFTWARE\Wizet\MapleStory' -Name ExecPath -ErrorAction SilentlyContinue).ExecPath
    Write-Output "HKCU  ExecPath   : $hkcu"
    exit 0
}

$want = $paths[$Client]
if (-not (Test-Path $want)) { Write-Error "Client directory does not exist: $want"; exit 1 }

try {
    Set-ItemProperty -Path $key -Name ExecPath -Value $want -ErrorAction Stop
} catch {
    Write-Error "Write failed - are you running elevated? $($_.Exception.Message)"
    exit 1
}

# Read it back rather than assume the write took.
$now = (Get-ItemProperty -Path $key -Name ExecPath).ExecPath
if ($now -ne $want) { Write-Error "Verify failed: wanted '$want', got '$now'"; exit 1 }
Write-Output "new     ExecPath : $now   ($Client)"
