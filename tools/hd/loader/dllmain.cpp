// hd-res: HD resolution patch for the v84 client, as an edits\ DLL.
//
// LOADER CONTRACT (established by reading D:\games\MSv84\client\ijl15.dll, read-only):
//   MapleStory.exe statically imports ijl15.dll. That proxy hooks GetStartupInfoA --
//   the call the packed client makes once it has finished unpacking -- and from that
//   hook calls its exported LoadDLLsFromDirectory("edits/") with the mask "*.dll",
//   LoadLibrary'ing every DLL it finds. The edit DLLs export nothing; all of them do
//   their work in DllMain(DLL_PROCESS_ATTACH). Strings in the proxy that establish
//   this: "edits/", "\\*.dll", "Loaded: %s", "Failed to load: %s",
//   "LoadDLLsFromDirectory", "[GetStartupInfoA_Hook].".
//
//   So: drop this DLL in edits\, and DllMain runs at exactly the moment the client's
//   code is decrypted in memory and has not begun executing the patched paths. That
//   is the whole reason this design does not need Detours, dinput8.dll, or a
//   config.ini sleepTime race. There is ONE injection path and the proxy owns it.
//
// WHY NO MinHook IN v1:
//   Ezorsia attaches 20 Detours hooks. Fourteen are anti-tamper/CRC bypass
//   (0x0044E88E MyGetProcAddress, 0x009F4E54 Crc32_GetCrc32_VMTable), socket-connect
//   redirection (0x00494CA3/0x00494D07/0x00494D2F CClientSocket::Connect), and CWvsApp
//   lifecycle rewrites that only exist because Ezorsia mods a PACKED client. On the
//   v84 route those jobs already belong to bypass-1.0.0.dll, redirect-1.0.0.dll,
//   no-patcher-1.0.0.dll, skip-logo-1.0.0.dll and window-mode-1.0.0.dll. Adding a
//   second hook engine over the same functions is how you get the intermittent
//   11001 "host cannot be reached" launches.
//   The resolution patch set needs ZERO function hooks: every code cave here is a raw
//   5-byte E9 into our own naked thunk, which is not a hook engine's job.
//
// v2 ADDS THE ARCHIVE, AND STILL NEEDS NO MinHook:
//   v1 said the EzorsiaV2_UI.wz archive "requires exactly two: CWvsApp::InitializeResMan
//   and StringPool::GetString". That was wrong on the first of the two. Upstream's
//   InitializeResMan hook is an explicit no-op and its load-list patches are dead code
//   (see archive.h for the evidence). The archive is actually mounted by swapping the
//   path of the FIRST IWzNameSpace::GetItem call -- the Base root open -- because the
//   archive is a Base.wz clone. So v2 hooks IWzNameSpace::GetItem and
//   StringPool::GetString, and both targets begin with a single self-contained 5-byte
//   instruction, so a memcpy trampoline is the whole engine. See archive.h.
//
// SAFETY: this DLL writes to the loaded image only. It never touches a file on disk,
// never writes to the registry, and never calls SetCurrentDirectory -- so it cannot
// disturb the shared HKLM\...\Wizet\MapleStory\ExecPath value that a client launch
// rewrites.

#include <windows.h>
#include <cstdio>

enum HdKind { K_INT, K_BYTE, K_SHORT, K_DOUBLE, K_FILL, K_STR, K_BYTES, K_CAVE };

struct HdFormula { int aW, aH, aWH, k; };   // value = aW*W/2 + aH*H/2 + aWH*W*H + k

struct HdPatch {
    DWORD     addr;      // v84 VA, verified present in both memory dumps
    int       size;      // bytes written (for K_CAVE: the NOP run length)
    HdKind    kind;
    HdFormula f;
    char      group;     // ticket 30 s2 group
    const char* id;      // row id in tools/hd/data/v84-patchset.json
    DWORD     v83;       // the Ezorsia address this came from, for auditing
    int       nexp;      // bytes of `expect` that are meaningful (0 = do not check)
    BYTE      expect[8]; // what these bytes read in the verified v84 image
};

#include "hd_patches.inc"

static int g_w = 1280, g_h = 720;
static int g_applied = 0, g_skipped = 0, g_mismatch = 0, g_report = 0;

static int Eval(const HdFormula& f) {
    return f.aW * g_w / 2 + f.aH * g_h / 2 + f.aWH * g_w * g_h + f.k;
}

// Write with the page temporarily writable, and restore protection. Ezorsia's
// Memory:: helpers do the same; the difference is this one refuses to write
// anywhere that is not committed, instead of faulting inside a __try.
static bool Poke(DWORD addr, const void* src, SIZE_T n) {
    MEMORY_BASIC_INFORMATION mbi{};
    if (!VirtualQuery((LPCVOID)addr, &mbi, sizeof(mbi)) || mbi.State != MEM_COMMIT)
        return false;
    DWORD old = 0;
    if (!VirtualProtect((LPVOID)addr, n, PAGE_EXECUTE_READWRITE, &old))
        return false;
    memcpy((void*)addr, src, n);
    VirtualProtect((LPVOID)addr, n, old, &old);
    FlushInstructionCache(GetCurrentProcess(), (LPCVOID)addr, n);
    return true;
}

static bool PokeFill(DWORD addr, BYTE v, int n) {
    MEMORY_BASIC_INFORMATION mbi{};
    if (!VirtualQuery((LPCVOID)addr, &mbi, sizeof(mbi)) || mbi.State != MEM_COMMIT)
        return false;
    DWORD old = 0;
    if (!VirtualProtect((LPVOID)addr, n, PAGE_EXECUTE_READWRITE, &old)) return false;
    memset((void*)addr, v, n);
    VirtualProtect((LPVOID)addr, n, old, &old);
    FlushInstructionCache(GetCurrentProcess(), (LPCVOID)addr, n);
    return true;
}

// A code cave is nops + E9 rel32, exactly as Memory::CodeCave does it. The NOP count
// is verified offline to cover a whole number of instructions in the v84 image, and
// the cave's jmp-back target is origin + that count -- see hd_patches.inc's
// HD_<name>_ORIGIN / HD_<name>_RETN pairs.
static bool Cave(DWORD origin, int nops, void* body) {
    if (!PokeFill(origin, 0x90, nops)) return false;
    BYTE jmp[5];
    jmp[0] = 0xE9;
    *(int*)(jmp + 1) = (int)((DWORD_PTR)body - origin - 5);
    return Poke(origin, jmp, 5);
}

// ---- caves ----------------------------------------------------------------
// codecaves.h is taken from github.com/444Ro666/MapleEzorsia-v2 VERBATIM and each
// `dwXxxRetn` replaced with HD_Xxx_RETN from hd_patches.inc. Do NOT retype the asm:
// it is ~600 lines of naked __asm and a transcription slip there is a silent crash.
//
// A cave body is NOT v84-neutral, though. It REPLAYS the instructions it displaced, so
// wherever v84 displaced something different the asm has to be edited too. verify.py
// diffs the displaced sequence and prints the ones that differ. 31 caves resolve, all
// tile their NOP run, nothing branches into any displaced range, and exactly TWO need
// their body edited:
//
//   AdjustStatusBarInput  0x008D217C -> HD_AdjustStatusBarInput_ORIGIN (9 bytes)
//       v83 body: push nStatusBarY ; push edi ; lea ecx,[esi+0x0CD0]
//       v84:      ... lea ecx,[esi + HD_AdjustStatusBarInput_MEMBER]     (0xD08)
//   AdjustStatusBarBG     0x008D1F65 -> HD_AdjustStatusBarBG_ORIGIN (9 bytes, NOT 5)
//       v83 body: push nStatusBarY ; movsd ; push 0
//       v84 body: push nStatusBarY ; push edi ; lea ecx,[esi + HD_AdjustStatusBarBG_MEMBER]
//       v84 recompiled this from a vtable call taking two ZXStrings BY VALUE into a
//       direct thiscall taking them BY POINTER, so the v83 displaced run does not exist
//       in v84. It becomes the same shape as AdjustStatusBarInput: 9 displaced bytes,
//       4 NOPs after the jmp, retn HD_AdjustStatusBarBG_RETN.
//
// AlwaysViewRestoreFix needs NO edit, despite v83 `je 0x64210F` vs v84 `je 0x657984`:
// that is one relative branch, `74 06` in both images, and the cave body does not even
// replay it -- it inverts the test onto a local label. Phase 1 called for a re-point
// because the check compared capstone's rendered ABSOLUTE targets. It is fixed now.
//
// The CUIStatusBar member shift is NOT uniform -- do not assume one number. Members
// below ~0xC40 moved +0x30 (0xA90 -> 0xAC0), those above moved +0x38 (0xCD0 -> 0xD08).
// Re-check any struct offset baked into a body against verify.py's printed sequence.
#include "cave_params.h"   // shims + codecaves.h + SetCaveParams()
#include "archive.h"       // EzorsiaV2_UI.wz mount + StringPool remap (v2)

// Which cave body belongs to which patch row. Only the shipped caves appear; the rest of
// codecaves.h compiles but is never jumped to, so it is unreachable dead code.
struct HdCave { const char* id; void (*body)(); DWORD origin; int nops; };
static const HdCave kHdCaves[] = {
#define HD_CAVE(name, id) { id, name, HD_##name##_ORIGIN, \
                            HD_##name##_RETN - HD_##name##_ORIGIN },
#include "hd_caves.inc"
#undef HD_CAVE
};

// Every address in this table was verified against two memory dumps of ONE build of
// the v84 client. If the owner's client is not that build -- a different sub-version,
// or an edits\ DLL that relocated something -- an address that was a `mov eax,0x1FC`
// is now the middle of some other instruction, and writing there corrupts it silently.
// So: refuse unless the bytes still read what the offline check saw. Being skipped is
// a visibly wrong screen; being wrong is a crash the owner cannot diagnose.
static bool Expected(const HdPatch& p) {
    if (p.nexp <= 0) return true;                 // nothing recorded -> nothing to check
    MEMORY_BASIC_INFORMATION mbi{};
    if (!VirtualQuery((LPCVOID)p.addr, &mbi, sizeof(mbi)) || mbi.State != MEM_COMMIT)
        return false;
    return memcmp((const void*)p.addr, p.expect, (SIZE_T)p.nexp) == 0;
}

static void ApplyAll() {
    for (const HdPatch& p : kHdPatches) {
        if (!Expected(p)) { ++g_mismatch; continue; }
        bool ok = false;
        switch (p.kind) {
        case K_INT:   { int   v = Eval(p.f);          ok = Poke(p.addr, &v, 4); break; }
        case K_SHORT: { short v = (short)Eval(p.f);   ok = Poke(p.addr, &v, 2); break; }
        case K_BYTE:  { BYTE  v = (BYTE)Eval(p.f);    ok = Poke(p.addr, &v, 1); break; }
        case K_FILL:  { ok = PokeFill(p.addr, (BYTE)p.f.k, p.size); break; }
        case K_CAVE:  {
            for (const HdCave& c : kHdCaves)
                if (!strcmp(c.id, p.id)) { ok = Cave(c.origin, c.nops, (void*)c.body); break; }
            break;
        }
        default: break;   // K_DOUBLE / K_STR / K_BYTES are group A/B/K, not shipped
        }
        ok ? ++g_applied : ++g_skipped;
    }
}

// The counts are the whole result of a gated manual test, so make them visible without
// requiring a debugger. Nothing is written to disk -- see the SAFETY note at the top.
static void Report() {
    char msg[420];
    // The archive line names WHICH of the three ways it can be off actually happened, so
    // one launch distinguishes "owner has no .wz" from "guard bytes differ" from
    // "mounted but the hook refused" -- without a second round trip.
    const char* arch = !g_useArchive   ? "off (no EzorsiaV2_UI.wz)"
                     : g_mountMismatch ? "FAILED (mount guard bytes differ)"
                     : !g_mounted      ? "FAILED (mount write refused)"
                     : g_hooked        ? "on"
                                       : "FAILED (mounted, GetString guard differs)";
    wsprintfA(msg, "hd-res %dx%d: applied %d, skipped %d, byte-mismatch %d of %d"
                   "\narchive: %s | hooks %d, mismatch %d"
                   "%s\n",
              g_w, g_h, g_applied, g_skipped, g_mismatch,
              (int)(sizeof(kHdPatches) / sizeof(kHdPatches[0])),
              arch, g_hooked, g_hookMismatch,
              g_diag ? " | diag ON" : "");
    OutputDebugStringA(msg);
#ifdef HD_SELFTEST
    fputs(msg, stderr);   // selftest.cpp build only; the shipped DLL has no CRT output
#endif
    if (g_report) MessageBoxA(NULL, msg, "hd-res", MB_OK | MB_ICONINFORMATION);
}

BOOL APIENTRY DllMain(HMODULE h, DWORD reason, LPVOID) {
    if (reason != DLL_PROCESS_ATTACH) return TRUE;
    DisableThreadLibraryCalls(h);

    // hd-res.ini sits next to this DLL and stays supported: it is what the owner
    // already has (width/height/report), and it supplies the defaults below.
    char ini[MAX_PATH]{};
    GetModuleFileNameA(h, ini, MAX_PATH);
    if (char* s = strrchr(ini, '\\')) strcpy(s + 1, "hd-res.ini");
    g_w = GetPrivateProfileIntA("general", "width", 1280, ini);
    g_h = GetPrivateProfileIntA("general", "height", 720, ini);
    g_report = GetPrivateProfileIntA("general", "report", 0, ini);

    // Ezorsia's own config.ini lives at the CLIENT ROOT, next to MapleStory.exe, and
    // wins where it sets a key. Same file and same key names Ezorsia itself reads, so
    // there is one config to maintain, not two. (Its remaining keys -- MsgAmount,
    // setDamageCap, speedMovementCap, useTubi, useV62_ExpTable, use_custom_dll_*, and
    // ServerIP_Address / WindowedMode / RemoveLogos which belong to the existing
    // redirect, window-mode and skip-logo DLLs -- are not wired here; see README.)
    char root[MAX_PATH]{}, cfg[MAX_PATH]{}, wz[MAX_PATH]{};
    GetModuleFileNameA(NULL, root, MAX_PATH);
    if (char* s = strrchr(root, '\\')) s[1] = 0;
    wsprintfA(cfg, "%sconfig.ini", root);
    wsprintfA(wz,  "%sEzorsiaV2_UI.wz", root);
    if (GetFileAttributesA(cfg) != INVALID_FILE_ATTRIBUTES) {
        g_w = GetPrivateProfileIntA("general", "width", g_w, cfg);
        g_h = GetPrivateProfileIntA("general", "height", g_h, cfg);
    }
    // The whole table is fitted for even W/H; odd values silently truncate the /2 terms.
    g_w &= ~1; g_h &= ~1;

    // The archive is opt-in by mere presence, exactly as Ezorsia decides it. No file
    // there means the two hooks below stay inert, so a bad copy cannot break a launch:
    // the owner reverts by renaming EzorsiaV2_UI.wz.
    SetArchiveParams(g_w,
                     GetFileAttributesA(wz) != INVALID_FILE_ATTRIBUTES,
                     GetPrivateProfileIntA("optional", "CustomLoginFrame", 0, cfg) != 0,
                     GetPrivateProfileIntA("optional", "ownCashShopFrame", 0, cfg) != 0);

    // MUST run after the ini read and before ApplyAll: codecaves.h's myWidth/myHeight
    // are static initialisers evaluated at 800x600, and every cave body reads globals
    // derived from them.
    SetCaveParams(g_w, g_h);
    ApplyAll();

    // diag=1 installs a read-only observer on IWzNameSpace::GetItem and writes
    // hd-res-diag.log next to this DLL. diag=0 (the default) installs no hooks at all,
    // which is exactly the v1 behaviour that launched cleanly.
    g_diag = GetPrivateProfileIntA("general", "diag", 0, ini) != 0;
    char logPath[MAX_PATH]{};
    GetModuleFileNameA(h, logPath, MAX_PATH);
    if (char* s = strrchr(logPath, '\\')) strcpy(s + 1, "hd-res-diag.log");
    InstallArchiveHooks(logPath);

    Report();
    return TRUE;
}
