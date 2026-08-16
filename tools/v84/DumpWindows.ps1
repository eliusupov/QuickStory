# DumpWindows.ps1 - enumerate top-level windows of a process and all their child controls,
# reading the actual text. Reads dialog contents WITHOUT stealing focus or screenshotting,
# which matters because the owner is using this machine.
param([string]$ProcName)

Add-Type @'
using System;
using System.Text;
using System.Collections.Generic;
using System.Runtime.InteropServices;
public class Win {
    public delegate bool EnumProc(IntPtr h, IntPtr l);
    [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr l);
    [DllImport("user32.dll")] public static extern bool EnumChildWindows(IntPtr p, EnumProc cb, IntPtr l);
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowTextW(IntPtr h, StringBuilder s, int n);
    [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetClassNameW(IntPtr h, StringBuilder s, int n);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L,T,R,B; }

    public static string Text(IntPtr h){ var sb=new StringBuilder(1024); GetWindowTextW(h,sb,1024); return sb.ToString(); }
    public static string Cls(IntPtr h){ var sb=new StringBuilder(256); GetClassNameW(h,sb,256); return sb.ToString(); }

    public static List<IntPtr> TopLevel(uint pid){
        var r=new List<IntPtr>();
        EnumWindows((h,l)=>{ uint p; GetWindowThreadProcessId(h, out p); if(p==pid) r.Add(h); return true; }, IntPtr.Zero);
        return r;
    }
    public static List<IntPtr> Children(IntPtr parent){
        var r=new List<IntPtr>();
        EnumChildWindows(parent,(h,l)=>{ r.Add(h); return true; }, IntPtr.Zero);
        return r;
    }
}
'@ -ErrorAction SilentlyContinue

$procs = @(Get-Process -Name $ProcName -ErrorAction SilentlyContinue)
if (-not $procs) { Write-Output "no '$ProcName' process running"; return }
foreach ($p in $procs) {
    Write-Output "=== pid $($p.Id) ($($p.ProcessName)) ==="
    foreach ($h in [Win]::TopLevel([uint32]$p.Id)) {
        $r = New-Object Win+RECT; [void][Win]::GetWindowRect($h, [ref]$r)
        $vis = [Win]::IsWindowVisible($h)
        $t = [Win]::Text($h); $c = [Win]::Cls($h)
        $w = $r.R - $r.L; $ht = $r.B - $r.T
        if (-not $vis -and $t -eq '' ) { continue }
        Write-Output ("  [top] cls='{0}' visible={1} {2}x{3} @({4},{5}) text='{6}'" -f $c,$vis,$w,$ht,$r.L,$r.T,$t)
        foreach ($ch in [Win]::Children($h)) {
            $ct = [Win]::Text($ch); $cc = [Win]::Cls($ch)
            if ($ct -ne '') { Write-Output ("      [child] cls='{0}' text='{1}'" -f $cc, $ct) }
        }
    }
}
