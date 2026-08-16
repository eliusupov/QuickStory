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
//   dump  --pid N | --name X  [--out F] [--base 0xHEX --size 0xHEX]
//   scan  --blob F --blobbase 0xHEX --pat HEXBYTES        (count+list occurrences)
//   read  --blob F --blobbase 0xHEX --va 0xHEX --len N    (hex dump at a VA)
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
