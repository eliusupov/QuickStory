// MemDump - read a running process's main-module image out of memory.
//
// Why: the v84 MapleStory.exe is Themida-packed, so its .text on disk is compressed
// and cannot be pattern-searched. It unpacks itself into memory on every run.
// This reads those unpacked bytes back out. Read-only: OpenProcess(VM_READ|QUERY_INFO)
// + ReadProcessMemory. No debugger attach, no writes, no injection.
//
// Output blob offset 0 == module base VA, so for a client with ImageBase 0x400000,
// file offset = VA - 0x400000 (same arithmetic as a fully unpacked PE dump).
//
// Build: csc.exe /platform:x64 /out:MemDump.exe MemDump.cs
//
// Modes:
//   dump     --pid N | --name X  [--out F] [--base 0xHEX --size 0xHEX]
//   full     --pid N | --name X  [--out F]                   (whole user address space + .idx)
//   waitfull --pid N | --name X  --watch-va 0xHEX [--watch-size 4] [--timeout-sec 300]
//            [--poll-ms 300] [--out F]   (poll VA until non-zero, then run full capture)
//   scan  --blob F --blobbase 0xHEX --pat HEXBYTES        (count+list occurrences)
//   read  --blob F --blobbase 0xHEX --va 0xHEX --len N    (hex dump at a VA)
//
// full mode walks every committed readable region with VirtualQueryEx and writes a flat
// .bin of concatenated region bytes plus a sidecar .idx (one line per region:
// VA_base<TAB>size<TAB>file_offset) so a downstream RE agent can map any VA to a file
// offset. Still read-only: OpenProcess(VM_READ|QUERY_INFO) + ReadProcessMemory only.
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Security.Cryptography;

static class MemDump
{
    const uint PROCESS_QUERY_INFORMATION = 0x0400;
    const uint PROCESS_VM_READ = 0x0010;
    const uint MEM_COMMIT = 0x1000;
    const uint PAGE_NOACCESS = 0x01;
    const uint PAGE_GUARD = 0x100;

    [DllImport("kernel32.dll", SetLastError = true)]
    static extern IntPtr OpenProcess(uint access, bool inherit, int pid);
    [DllImport("kernel32.dll", SetLastError = true)]
    static extern bool ReadProcessMemory(IntPtr h, IntPtr addr, byte[] buf, IntPtr size, out IntPtr read);
    [DllImport("kernel32.dll", SetLastError = true)]
    static extern bool CloseHandle(IntPtr h);
    [DllImport("kernel32.dll", SetLastError = true)]
    static extern IntPtr VirtualQueryEx(IntPtr h, IntPtr addr, out MEMORY_BASIC_INFORMATION mbi, IntPtr len);

    [StructLayout(LayoutKind.Sequential)]
    struct MEMORY_BASIC_INFORMATION
    {
        public IntPtr BaseAddress, AllocationBase;
        public uint AllocationProtect;
        public IntPtr RegionSize;
        public uint State, Protect, Type;
    }

    static int Main(string[] a)
    {
        var arg = new Dictionary<string, string>();
        for (int i = 0; i < a.Length; i++)
            if (a[i].StartsWith("--")) arg[a[i].Substring(2)] = (i + 1 < a.Length && !a[i + 1].StartsWith("--")) ? a[++i] : "1";
        string mode = a.Length > 0 && !a[0].StartsWith("--") ? a[0] : "dump";
        try
        {
            if (mode == "dump") return Dump(arg);
            if (mode == "full") return Full(arg);
            if (mode == "waitfull") return WaitFull(arg);
            if (mode == "scan") return Scan(arg);
            if (mode == "read") return ReadVa(arg);
        }
        catch (Exception e) { Console.Error.WriteLine("ERROR: " + e.Message); return 3; }
        Console.Error.WriteLine("unknown mode " + mode);
        return 2;
    }

    static ulong Hex(string s) { s = s.Trim(); return s.StartsWith("0x") ? Convert.ToUInt64(s.Substring(2), 16) : ulong.Parse(s); }

    static int Dump(Dictionary<string, string> arg)
    {
        Process p;
        if (arg.ContainsKey("pid")) p = Process.GetProcessById(int.Parse(arg["pid"]));
        else
        {
            var ps = Process.GetProcessesByName(arg["name"]);
            if (ps.Length == 0) { Console.Error.WriteLine("no process named " + arg["name"]); return 1; }
            if (ps.Length > 1) Console.Error.WriteLine("WARN: " + ps.Length + " processes named " + arg["name"] + ", using pid " + ps[0].Id);
            p = ps[0];
        }

        ulong bas, size;
        if (arg.ContainsKey("base")) { bas = Hex(arg["base"]); size = Hex(arg["size"]); }
        else
        {
            // MainModule is unreliable across bitness; caller can override with --base/--size.
            var m = p.MainModule;
            bas = (ulong)m.BaseAddress.ToInt64();
            size = (ulong)m.ModuleMemorySize;
        }
        Console.WriteLine("pid=" + p.Id + " base=0x" + bas.ToString("X") + " size=0x" + size.ToString("X"));

        IntPtr h = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, false, p.Id);
        if (h == IntPtr.Zero) { Console.Error.WriteLine("OpenProcess failed, win32=" + Marshal.GetLastWin32Error()); return 1; }

        var outBuf = new byte[size];
        int okPages = 0, failPages = 0;
        const int PAGE = 0x1000;
        var chunk = new byte[PAGE];
        for (ulong off = 0; off < size; off += PAGE)
        {
            IntPtr got;
            int n = (int)Math.Min((ulong)PAGE, size - off);
            if (ReadProcessMemory(h, new IntPtr((long)(bas + off)), chunk, new IntPtr(n), out got) && got.ToInt64() == n)
            { Array.Copy(chunk, 0, outBuf, (long)off, n); okPages++; }
            else failPages++;
        }
        CloseHandle(h);

        string outPath = arg.ContainsKey("out") ? arg["out"] : ("dump_" + p.Id + ".bin");
        File.WriteAllBytes(outPath, outBuf);
        using (var sha = SHA256.Create())
            Console.WriteLine("sha256=" + BitConverter.ToString(sha.ComputeHash(outBuf)).Replace("-", "").ToLower());
        Console.WriteLine("pages ok=" + okPages + " fail=" + failPages + " -> " + outPath);
        return failPages > 0 && okPages == 0 ? 1 : 0;
    }

    static Process ResolveProc(Dictionary<string, string> arg)
    {
        if (arg.ContainsKey("pid")) return Process.GetProcessById(int.Parse(arg["pid"]));
        var ps = Process.GetProcessesByName(arg["name"]);
        if (ps.Length == 0) { Console.Error.WriteLine("no process named " + arg["name"]); return null; }
        if (ps.Length > 1) Console.Error.WriteLine("WARN: " + ps.Length + " processes named " + arg["name"] + ", using pid " + ps[0].Id);
        return ps[0];
    }

    // Walk the whole user address space and dump every committed, readable region.
    // Emits a flat .bin (concatenated region bytes) + a .idx sidecar mapping VA_base -> file_offset.
    static int Full(Dictionary<string, string> arg)
    {
        Process p = ResolveProc(arg);
        if (p == null) return 1;
        IntPtr h = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, false, p.Id);
        if (h == IntPtr.Zero) { Console.Error.WriteLine("OpenProcess failed, win32=" + Marshal.GetLastWin32Error()); return 1; }

        string outPath = arg.ContainsKey("out") ? arg["out"] : ("full_" + p.Id + ".bin");
        string idxPath = Path.ChangeExtension(outPath, ".idx");
        Console.WriteLine("pid=" + p.Id + " mode=full -> " + outPath);

        const int PAGE = 0x1000;
        // 32-bit target user space lives below 4GB; stop the walk there. Compiled x64 so
        // IntPtr/MBI are 64-bit and VirtualQueryEx returns 64-bit addresses for the WOW64 target.
        const ulong MAX = 0x100000000UL;
        int mbiSize = Marshal.SizeOf(typeof(MEMORY_BASIC_INFORMATION));
        long fileOff = 0;
        int regions = 0, skipUncommitted = 0, skipGuardNoaccess = 0, failPages = 0;
        var chunk = new byte[PAGE];

        using (var fs = new FileStream(outPath, FileMode.Create, FileAccess.Write))
        using (var idx = new StreamWriter(idxPath, false))
        {
            idx.WriteLine("# VA_base\tsize\tfile_offset  (all hex, no 0x). pid=" + p.Id);
            ulong addr = 0;
            while (addr < MAX)
            {
                MEMORY_BASIC_INFORMATION mbi;
                if (VirtualQueryEx(h, new IntPtr((long)addr), out mbi, new IntPtr(mbiSize)) == IntPtr.Zero) break;
                ulong bas = (ulong)mbi.BaseAddress.ToInt64();
                ulong sz = (ulong)mbi.RegionSize.ToInt64();
                if (sz == 0) break;
                bool committed = mbi.State == MEM_COMMIT;
                bool readable = (mbi.Protect & PAGE_GUARD) == 0 && (mbi.Protect & PAGE_NOACCESS) == 0;
                if (!committed) skipUncommitted++;
                else if (!readable) skipGuardNoaccess++;
                else
                {
                    // Read page-by-page; zero-fill unreadable pages so offset within the region
                    // stays exact (file_offset + (VA - VA_base)). Failures are rare but tolerated.
                    var buf = new byte[sz];
                    for (ulong o = 0; o < sz; o += PAGE)
                    {
                        int n = (int)Math.Min((ulong)PAGE, sz - o);
                        IntPtr got;
                        if (ReadProcessMemory(h, new IntPtr((long)(bas + o)), chunk, new IntPtr(n), out got) && got.ToInt64() == n)
                            Array.Copy(chunk, 0, buf, (long)o, n);
                        else failPages++;
                    }
                    fs.Write(buf, 0, buf.Length);
                    idx.WriteLine(bas.ToString("X") + "\t" + sz.ToString("X") + "\t" + fileOff.ToString("X"));
                    fileOff += (long)sz;
                    regions++;
                }
                ulong next = bas + sz;
                if (next <= addr) break; // guard against no-forward-progress
                addr = next;
            }
        }
        CloseHandle(h);
        Console.WriteLine("regions=" + regions + " bytes=0x" + fileOff.ToString("X") + " (" + fileOff + ")");
        Console.WriteLine("skipped: uncommitted=" + skipUncommitted + " guard/noaccess=" + skipGuardNoaccess + " unreadable_pages=" + failPages);
        Console.WriteLine("idx=" + idxPath);
        return regions == 0 ? 1 : 0;
    }

    // Poll a watched VA until it reads non-zero, then run the same full-mode capture.
    // Fixes the timing problem: CChannelSelectDlg isn't instantiated at an arbitrary
    // capture instant, so we wait for its pointer to go non-zero before dumping.
    static int WaitFull(Dictionary<string, string> arg)
    {
        Process p = ResolveProc(arg);
        if (p == null) return 1;
        if (!arg.ContainsKey("watch-va")) { Console.Error.WriteLine("waitfull needs --watch-va"); return 2; }
        ulong watchVa = Hex(arg["watch-va"]);
        int watchSize = arg.ContainsKey("watch-size") ? int.Parse(arg["watch-size"]) : 4;
        int timeoutSec = arg.ContainsKey("timeout-sec") ? int.Parse(arg["timeout-sec"]) : 300;
        int pollMs = arg.ContainsKey("poll-ms") ? int.Parse(arg["poll-ms"]) : 300;

        IntPtr h = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, false, p.Id);
        if (h == IntPtr.Zero) { Console.Error.WriteLine("OpenProcess failed, win32=" + Marshal.GetLastWin32Error()); return 1; }

        Console.WriteLine("pid=" + p.Id + " waitfull watch-va=0x" + watchVa.ToString("X") + " size=" + watchSize + " timeout=" + timeoutSec + "s");
        var buf = new byte[watchSize];
        var sw = System.Diagnostics.Stopwatch.StartNew();
        long lastBeat = -2000;
        ulong val = 0;
        bool fired = false;
        while (sw.Elapsed.TotalSeconds < timeoutSec)
        {
            IntPtr got;
            if (ReadProcessMemory(h, new IntPtr((long)watchVa), buf, new IntPtr(watchSize), out got) && got.ToInt64() == watchSize)
            {
                val = 0;
                for (int i = 0; i < watchSize && i < 8; i++) val |= (ulong)buf[i] << (8 * i);
                if (val != 0) { fired = true; break; }
            }
            if (sw.ElapsedMilliseconds - lastBeat >= 2000)
            {
                Console.WriteLine("waiting for channel dialog... VA=0x" + val.ToString("X") + " (" + (int)sw.Elapsed.TotalSeconds + "s/" + timeoutSec + "s)");
                lastBeat = sw.ElapsedMilliseconds;
            }
            System.Threading.Thread.Sleep(pollMs);
        }
        CloseHandle(h);

        if (!fired)
        {
            Console.Error.WriteLine("FAILED: watched VA stayed 0 for " + timeoutSec + "s -- dialog never appeared. Make sure the channel grid is on screen.");
            return 1;
        }
        Console.WriteLine("watched value non-zero: 0x" + val.ToString("X") + " -- capturing now.");
        return Full(arg);
    }

    static byte[] ParsePat(string s)
    {
        s = s.Replace(" ", "").Replace("-", "");
        var b = new byte[s.Length / 2];
        for (int i = 0; i < b.Length; i++) b[i] = Convert.ToByte(s.Substring(i * 2, 2), 16);
        return b;
    }

    static int Scan(Dictionary<string, string> arg)
    {
        byte[] blob = File.ReadAllBytes(arg["blob"]);
        ulong bb = Hex(arg["blobbase"]);
        byte[] pat = ParsePat(arg["pat"]);
        int count = 0;
        for (int i = 0; i + pat.Length <= blob.Length; i++)
        {
            int j = 0; while (j < pat.Length && blob[i + j] == pat[j]) j++;
            if (j == pat.Length) { count++; if (count <= 20) Console.WriteLine("0x" + (bb + (ulong)i).ToString("X8")); }
        }
        Console.WriteLine("count=" + count);
        return 0;
    }

    static int ReadVa(Dictionary<string, string> arg)
    {
        byte[] blob = File.ReadAllBytes(arg["blob"]);
        ulong bb = Hex(arg["blobbase"]);
        ulong va = Hex(arg["va"]);
        int len = arg.ContainsKey("len") ? int.Parse(arg["len"]) : 16;
        long off = (long)(va - bb);
        if (off < 0 || off + len > blob.Length) { Console.Error.WriteLine("VA out of blob range"); return 1; }
        var sb = new System.Text.StringBuilder();
        for (int i = 0; i < len; i++) sb.Append(blob[off + i].ToString("X2")).Append(' ');
        Console.WriteLine(sb.ToString().Trim());
        return 0;
    }
}
