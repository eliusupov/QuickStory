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
//   MinHook is needed only if the EzorsiaV2_UI.wz side archive is later wanted, which
//   requires exactly two: CWvsApp::InitializeResMan and StringPool::GetString. The
//   hook table below is deliberately empty; wire MinHook in only when those land.
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
};

#include "hd_patches.inc"

static int g_w = 1280, g_h = 720;
static int g_applied = 0, g_skipped = 0;

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
// diffs the displaced sequence between images and prints the ones that differ. As of
// this table, 30 caves resolve, all tile their NOP run, and TWO need an edit:
//
//   AlwaysViewRestoreFix  0x00642105 -> 0x0065797A
//       v83: test eax,eax ; je 0x64210F ; mov ecx,[eax] ; push eax
//       v84: same, but `je 0x657984`               -> retarget the je
//   AdjustStatusBarInput  0x008D217C -> 0x00906EBE
//       v83: push 0x16 ; push edi ; lea ecx,[esi+0x0CD0]
//       v84: push 0x16 ; push edi ; lea ecx,[esi+0x0D08]   -> CUIStatusBar grew 0x38
//
// A third, AdjustStatusBarBG (v83 0x008D1F65 -> v84 0x00906D39), cannot be ported as
// written at all: v84 recompiled it from a vtable call with an inline struct copy into
// a direct thiscall, so its 5-byte NOP run no longer tiles. It needs 3 NOPs and a body
// of `push nStatusBarY ; push edi ; jmp 0x00906D3C`. Not in the table yet.
// #include "codecaves.h"

static void ApplyAll() {
    for (const HdPatch& p : kHdPatches) {
        bool ok = false;
        switch (p.kind) {
        case K_INT:   { int   v = Eval(p.f);          ok = Poke(p.addr, &v, 4); break; }
        case K_SHORT: { short v = (short)Eval(p.f);   ok = Poke(p.addr, &v, 2); break; }
        case K_BYTE:  { BYTE  v = (BYTE)Eval(p.f);    ok = Poke(p.addr, &v, 1); break; }
        case K_FILL:  { ok = PokeFill(p.addr, (BYTE)p.f.k, p.size); break; }
        case K_CAVE:  /* wired up once codecaves.h is included; see above */ break;
        default: break;   // K_DOUBLE / K_STR / K_BYTES are group A/B/K, not shipped
        }
        ok ? ++g_applied : ++g_skipped;
    }
}

BOOL APIENTRY DllMain(HMODULE h, DWORD reason, LPVOID) {
    if (reason != DLL_PROCESS_ATTACH) return TRUE;
    DisableThreadLibraryCalls(h);

    char ini[MAX_PATH]{};
    GetModuleFileNameA(h, ini, MAX_PATH);
    if (char* s = strrchr(ini, '\\')) strcpy(s + 1, "hd-res.ini");
    g_w = GetPrivateProfileIntA("general", "width", 1280, ini);
    g_h = GetPrivateProfileIntA("general", "height", 720, ini);
    // The whole table is fitted for even W/H; odd values silently truncate the /2 terms.
    g_w &= ~1; g_h &= ~1;

    ApplyAll();
    return TRUE;
}
