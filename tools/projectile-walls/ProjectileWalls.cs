// Owner-directed v84 customization. Runtime only: never launches the game or edits client files.
// Captured twice from PID 43180 on 2026-09-14, flat image base 0x400000.
// CWvsPhysicalSpace2D 0xA9153E clips destination XY at A91831/A91842 and returns zero.
// Shoot: 68F578 -> [ebp-974], 67A525 supplies hit XY; failure at 98E9DD clears target.
// Magic: 68F578 -> [ebp-CC0], 67A525 supplies hit XY; failure at 993214 clears target.
// Other callers (996111 generic geometry, 9AC017 Monster Magnet) are left intact.
// Untargeted shoot/magic still call the original.
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Diagnostics;
using System.Linq;
using System.Runtime.InteropServices;

static class ProjectileWalls {
    const uint Query = 0xA9153E;
    sealed class Site {
        public uint Address;
        public int TargetOffset;
        public byte[] Before, After;
        public Site(uint address, int targetOffset, string before, string after) {
            Address = address; TargetOffset = targetOffset;
            Before = Hex(before); After = Hex(after);
        }
        public byte[] Original { get { return Jump(0xE8, Address, Query); } }
    }
    static readonly Site[] Sites = {
        new Site(0x98E9D4, -0x974, "8B8DB8EEFFFF", "85C0750783A58CF6FFFF00"),
        new Site(0x99320B, -0xCC0, "8B8DD8ECFFFF", "85C0750783A540F3FFFF00")
    };
    [DllImport("kernel32.dll", SetLastError=true)] static extern IntPtr OpenProcess(uint access, bool inherit, int pid);
    [DllImport("kernel32.dll", SetLastError=true)] static extern bool CloseHandle(IntPtr handle);
    [DllImport("kernel32.dll", SetLastError=true)] static extern bool ReadProcessMemory(IntPtr process, IntPtr address, byte[] data, UIntPtr size, out UIntPtr read);
    [DllImport("kernel32.dll", SetLastError=true)] static extern bool WriteProcessMemory(IntPtr process, IntPtr address, byte[] data, UIntPtr size, out UIntPtr written);
    [DllImport("kernel32.dll", SetLastError=true)] static extern IntPtr VirtualAllocEx(IntPtr process, IntPtr address, UIntPtr size, uint allocation, uint protection);
    [DllImport("kernel32.dll", SetLastError=true)] static extern bool VirtualProtectEx(IntPtr process, IntPtr address, UIntPtr size, uint protection, out uint old);
    [DllImport("kernel32.dll", SetLastError=true)] static extern bool FlushInstructionCache(IntPtr process, IntPtr address, UIntPtr size);
    [DllImport("kernel32.dll", SetLastError=true)] static extern IntPtr OpenThread(uint access, bool inherit, uint id);
    [DllImport("kernel32.dll", SetLastError=true)] static extern bool Wow64GetThreadContext(IntPtr thread, byte[] context);
    [DllImport("ntdll.dll")] static extern int NtSuspendProcess(IntPtr process);
    [DllImport("ntdll.dll")] static extern int NtResumeProcess(IntPtr process);

    static byte[] Hex(string s) { return Enumerable.Range(0, s.Length / 2).Select(i => Convert.ToByte(s.Substring(i * 2, 2), 16)).ToArray(); }
    static void Require(bool ok, string what) { if (!ok) throw new InvalidOperationException(what); }
    static void Native(bool ok, string what) { if (!ok) throw new Win32Exception(Marshal.GetLastWin32Error(), what); }
    static IntPtr Ptr(uint address) { return new IntPtr((long)address); }
    static byte[] Jump(byte opcode, uint from, uint to) {
        return new byte[] { opcode }.Concat(BitConverter.GetBytes(unchecked((int)(to - from - 5)))).ToArray();
    }
    static byte[] Cave(Site site, uint address) {
        var code = new List<byte>();
        code.AddRange(Hex("83BD")); code.AddRange(BitConverter.GetBytes(site.TargetOffset)); code.Add(0);
        code.AddRange(Hex("740D")); // no target: call the vanilla geometry query below
        code.AddRange(Hex("83C414B801000000")); // callee would pop five args; report unobstructed
        code.AddRange(Jump(0xE9, address + (uint)code.Count, site.Address + 5));
        code.AddRange(Jump(0xE8, address + (uint)code.Count, Query));
        code.AddRange(Jump(0xE9, address + (uint)code.Count, site.Address + 5));
        return code.ToArray();
    }
    static byte[] Read(IntPtr process, uint address, int size) {
        var bytes = new byte[size]; UIntPtr read;
        Native(ReadProcessMemory(process, Ptr(address), bytes, (UIntPtr)size, out read) && read.ToUInt64() == (ulong)size, "ReadProcessMemory");
        return bytes;
    }
    static void Write(IntPtr process, uint address, byte[] bytes) {
        UIntPtr written;
        Native(WriteProcessMemory(process, Ptr(address), bytes, (UIntPtr)bytes.Length, out written) && written.ToUInt64() == (ulong)bytes.Length, "WriteProcessMemory");
        Native(FlushInstructionCache(process, Ptr(address), (UIntPtr)bytes.Length), "FlushInstructionCache");
        Require(Read(process, address, bytes.Length).SequenceEqual(bytes), "Patch read-back mismatch");
    }
    static uint ExistingCave(IntPtr process, Site site) {
        Require(Read(process, site.Address - (uint)site.Before.Length, site.Before.Length).SequenceEqual(site.Before), "Caller prefix mismatch");
        Require(Read(process, site.Address + 5, site.After.Length).SequenceEqual(site.After), "Caller suffix mismatch");
        var bytes = Read(process, site.Address, 5);
        if (bytes.SequenceEqual(site.Original)) return 0;
        Require(bytes[0] == 0xE9, "Unknown collision hook; refusing to overwrite");
        uint address = unchecked(site.Address + 5 + (uint)BitConverter.ToInt32(bytes, 1));
        var expected = Cave(site, address);
        Require(Read(process, address, expected.Length).SequenceEqual(expected), "Unknown collision cave; refusing to overwrite");
        return address;
    }
    static void CheckThreadPositions(Process client, params uint[] hooks) {
        foreach (ProcessThread thread in client.Threads) {
            IntPtr handle = OpenThread(0x8, false, (uint)thread.Id);
            Native(handle != IntPtr.Zero, "OpenThread");
            try {
                // WOW64_CONTEXT from winnt.h: CONTROL flags at 0, Eip at 184, 716 bytes.
                var context = new byte[716]; BitConverter.GetBytes(0x10001).CopyTo(context, 0);
                Native(Wow64GetThreadContext(handle, context), "Wow64GetThreadContext");
                uint ip = BitConverter.ToUInt32(context, 184);
                Require(!hooks.Any(a => ip >= a && ip < a + 5), "Thread is executing the hook instruction; retry later");
            } finally { CloseHandle(handle); }
        }
    }
    static void Apply(int pid, bool restore) {
        var client = Process.GetProcessById(pid);
        Require(client.ProcessName == "MapleStory", "PID is not MapleStory");
        IntPtr process = OpenProcess(0xC38, false, pid); // query, VM read/write/operation, suspend/resume
        Native(process != IntPtr.Zero, "OpenProcess (run this helper as Administrator)");
        bool suspended = false;
        var changed = new List<Site>();
        uint[] caves = new uint[Sites.Length];
        try {
            Require(NtSuspendProcess(process) >= 0, "Could not pause client"); suspended = true;
            CheckThreadPositions(client, Sites.Select(s => s.Address).ToArray());
            for (int i = 0; i < Sites.Length; i++) caves[i] = ExistingCave(process, Sites[i]);
            if (!restore && caves.Any(c => c == 0)) {
                IntPtr allocation = VirtualAllocEx(process, IntPtr.Zero, (UIntPtr)4096, 0x3000, 0x04);
                Native(allocation != IntPtr.Zero, "VirtualAllocEx");
                Require(allocation.ToInt64() > 0 && allocation.ToInt64() <= uint.MaxValue - 4096, "Cave address does not fit x86");
                for (int i = 0; i < Sites.Length; i++) if (caves[i] == 0) {
                    caves[i] = (uint)allocation.ToInt64() + (uint)(i * 64);
                    Write(process, caves[i], Cave(Sites[i], caves[i]));
                }
                uint old;
                Native(VirtualProtectEx(process, allocation, (UIntPtr)4096, 0x20, out old), "Protect caves executable/read-only");
            }
            for (int i = 0; i < Sites.Length; i++) {
                Site site = Sites[i]; byte[] desired = restore ? site.Original : Jump(0xE9, site.Address, caves[i]);
                if (Read(process, site.Address, 5).SequenceEqual(desired)) continue;
                uint old; Native(VirtualProtectEx(process, Ptr(site.Address), (UIntPtr)5, 0x40, out old), "Protect hook writable");
                changed.Add(site);
                try { Write(process, site.Address, desired); }
                finally { uint unused; Native(VirtualProtectEx(process, Ptr(site.Address), (UIntPtr)5, old, out unused), "Restore hook protection"); }
            }
            Console.WriteLine("PID {0}: {1}; {2} changed, {3} already correct; read-back verified", pid, restore ? "RESTORED" : "APPLIED", changed.Count, Sites.Length - changed.Count);
            // Keep caves until process exit: an in-flight vanilla call may return into a cave.
        } catch {
            // Failure is all-or-nothing: undo any written sites before resuming gameplay.
            foreach (Site site in changed.AsEnumerable().Reverse()) {
                int i = Array.IndexOf(Sites, site); uint old;
                Native(VirtualProtectEx(process, Ptr(site.Address), (UIntPtr)5, 0x40, out old), "Rollback hook protection");
                try { Write(process, site.Address, restore ? Jump(0xE9, site.Address, caves[i]) : site.Original); }
                finally { uint unused; Native(VirtualProtectEx(process, Ptr(site.Address), (UIntPtr)5, old, out unused), "Restore rollback protection"); }
            }
            throw;
        } finally {
            if (suspended) Require(NtResumeProcess(process) >= 0, "Could not resume client");
            CloseHandle(process); client.Dispose();
        }
    }
    static void Test() {
        int tests = 0;
        foreach (Site site in Sites) foreach (uint address in new uint[] { 0x10000000, 0x70000000 }) foreach (int target in new int[] { 0, 1 }) {
            byte[] code = Cave(site, address); int pc = 0, popped = 0, eax = 0; bool zero = false, called = false;
            for (int steps = 0; steps < 8; steps++) {
                if (code[pc] == 0x83 && code[pc + 1] == 0xBD) {
                    Require(BitConverter.ToInt32(code, pc + 2) == site.TargetOffset && code[pc + 6] == 0, "Target guard changed");
                    zero = target == 0; pc += 7;
                } else if (code[pc] == 0x74) { pc += zero ? 2 + (sbyte)code[pc + 1] : 2; }
                else if (code[pc] == 0x83 && code[pc + 1] == 0xC4) { popped += code[pc + 2]; pc += 3; }
                else if (code[pc] == 0xB8) { eax = BitConverter.ToInt32(code, pc + 1); pc += 5; }
                else if (code[pc] == 0xE8 || code[pc] == 0xE9) {
                    uint destination = unchecked(address + (uint)pc + 5 + (uint)BitConverter.ToInt32(code, pc + 1));
                    if (code[pc] == 0xE8) { Require(destination == Query, "Free shot called wrong query"); called = true; popped += 20; eax = 0; pc += 5; }
                    else { Require(destination == site.Address + 5, "Wrong caller continuation"); break; }
                } else throw new Exception("Unexpected cave instruction");
            }
            bool bypass = target != 0;
            Require(popped == 20 && called == !bypass && eax == (bypass ? 1 : 0), "Targeted/free shot behavior changed"); tests++;
        }
        Console.WriteLine("PASS: {0} machine-code flow checks (targeted/free, all callers, two cave bases)", tests);
    }
    static int Main(string[] args) {
        try {
            if (args.Length == 1 && args[0] == "test") { Test(); return 0; }
            Require(args.Length == 2 && (args[0] == "apply" || args[0] == "restore"), "Usage: ProjectileWalls.exe test | apply PID | restore PID");
            Apply(int.Parse(args[1]), args[0] == "restore"); return 0;
        } catch (Exception ex) { Console.Error.WriteLine("REFUSED: " + ex.Message); return 1; }
    }
}
