// archive.h -- Ezorsia v2 HD side-archive support for the v84 client.
//
// WHAT THE ARCHIVE IS (verified, not assumed):
//   EzorsiaV2_UI.wz is a WZ package (PKG1, "Package file v1.0 Copyright 2002 Wizet,
//   ZMS"), 1,370,002 bytes, md5 a0a05d073c0a2d0cd41290560c3f64b3 -- byte-identical to
//   the copy checked into github.com/444Ro666/MapleEzorsia-v2. Its 19 root entries
//   decode (one derived name-keystream, all 19 land on clean ASCII) to:
//       smap.img  zmap.img  StandardPDD.img  MapleEzorsiaV2wzfiles.img
//       UI Effect Sound Map Character Item TamingMob Etc Npc Reactor Skill Morph
//       String Mob Quest
//   That is Base.wz's exact shape -- the three root imgs plus a stub directory per
//   mountable archive -- with ONE payload img added. So it is not a "side" archive at
//   all: it is a drop-in REPLACEMENT for Base.wz that smuggles in the HD art.
//
// HOW EZORSIA ACTUALLY LOADS IT (this contradicts the note this file replaces):
//   NOT via CWvsApp::InitializeResMan. Upstream's InitializeResMan hook is a documented
//   no-op -- "resman hook that does nothing, kept for analysis and referrence //not
//   skilled enough to rewrite to load custom wz files" -- its body just calls the
//   original. Its two companion patches are dead too: the load-list count write
//   (v83 0x009F74EA+3) writes 0x0F over a vanilla 0x0F, and `WriteInt(...,16)` is
//   commented out in Client.cpp:604, so the redirected load-list array's 16th entry
//   ("EzorsiaV2_UI") is never reached by `cmp [ebp-0x18],0x0F / jge`.
//
//   The real mechanism is IWzNameSpace::GetItem. Upstream swaps the requested path for
//   L"EzorsiaV2_UI.wz" on the FIRST call only. v83 CWvsApp::InitializeResMan makes its
//   first GetItem call at 0x009F734D, and the wide string it pushes at 0x009F732A
//   (0x00B3F48C) is L"Base.wz" -- the full 16 bytes read 42 00 61 00 73 00 65 00 2e 00
//   77 00 7a 00 00 00. (An earlier revision of this comment said L"Base"; that came
//   from an ASCII read that stopped at the first NUL. The extension IS present, so the
//   substituted string having one is consistent.)
//
// WHY v2.0.0 CRASHED, AND WHAT IS STILL UNPROVEN:
//   v2.0.0 shipped the swap and the client threw com_error 0x80030002
//   (STG_E_FILENOTFOUND) at startup, before login, with the archive verified present at
//   the client root. The swap replaced Data_t::m_wstr with a static literal out of this
//   DLL's .rdata. That is NOT a valid string in the client's own representation:
//   Ztl_bstr_t::Data_t's destructor (v83 0x00402F5E) does `mov eax,[esi] / add eax,-4 /
//   push eax / call [0xBF0558]` -- it frees m_wstr MINUS FOUR, i.e. these are
//   length-prefixed strings owned by the client's allocator, not bare wchar_t*. So the
//   four bytes in front of the pointer are a length, and in front of a .rdata literal
//   they are whatever happens to sit there. A garbage length yields a garbage name and
//   the storage layer answers STG_E_FILENOTFOUND -- which is exactly the error seen.
//   That is a mechanism consistent with the evidence, NOT a proven root cause, and the
//   last one of these cost a crash. So v2.1.0 ships DIAGNOSTICS ONLY: the swap is
//   DELETED, not merely disabled, and the hook does nothing but read and print. See
//   DiagGetItem below -- it logs the length prefix specifically to settle this. The
//   next build gets written from that log, not from this paragraph.
//
// THE TWO HOOKS, RESOLVED THE SAME WAY THE 288 PATCHES WERE:
//   Masked-signature context unique in localhome.exe AND in both v84 dumps, landing on
//   the same VA in both, same_shape() on the prologue, inside the monotone delta
//   envelope of its resolved neighbours, and a call target in both dumps.
//
//     IWzNameSpace::GetItem     v83 0x005D995B -> v84 0x005EB5E6  (delta 0x11C8B,
//         envelope 0x104F3..0x14F39)   prologue b8 00 35 ae 00
//     StringPool::GetString     v83 0x0079E993 -> v84 0x007C3408  (delta 0x24A75,
//         envelope 0x21E78..0x25D47)   prologue b8 3e 65 b0 00
//     ZXString<char>::Assign    v83 0x00414617 -> v84 0x004150D8  (delta 0xAC1)
//         prologue 83 7c 24 04 00
//
//   The v84 GetItem body is instruction-for-instruction identical to v83; only absolute
//   operands differ. That is the shape proof, and it is also what lets the detour be
//   five bytes: every one of these three starts on a single self-contained instruction
//   (`mov eax,imm32` / `cmp dword ptr [esp+4],0`), so the trampoline is a straight copy
//   plus a jmp back. No MinHook, no length disassembler, no second hook engine fighting
//   the five DLLs already in edits\.
//
// ABI, read off the disassembly rather than guessed:
//   IWzNameSpace::GetItem  __fastcall(ecx=this, edx, [ebp+8]=result, [ebp+C]=sPath)
//     where the 4th argument IS the bstr Data_t* -- 0x005D997E `mov edi,[ebp+0xC]`
//     then 0x005D9985 `mov eax,[edi]` reads the wide string straight out of it.
//   Ztl_bstr_t::Data_t { +0 BSTR m_wstr; +4 IUnknown* m_pUnk; +8 long m_RefCount }
//     -- Data_t::Release (0x00402EA5) does `lea edi,[esi+8]` then InterlockedDecrement,
//     so the refcount is at +8 and the string pointer at +0.
//   ZXString<char>::Assign __thiscall(ecx=this, const char* s, int n), n = -1 => strlen.

#pragma once
#include <windows.h>

// ---- resolved addresses ---------------------------------------------------
// Each carries the first five bytes as read from the verified v84 image. Same
// discipline as HdPatch::expect: if the owner's client is not this build, the bytes
// differ, and we refuse to hook rather than corrupt an instruction boundary.
struct HdHook {
    const char* name;
    DWORD       addr;
    BYTE        expect[5];
    void*       detour;
    void**      orig;      // filled with the trampoline
};

typedef void* (__fastcall* GetItem_t)(void* pThis, void* edx, void* result, void* sPathData);
typedef void* (__fastcall* GetString_t)(void* pThis, void* edx, void* result,
                                        unsigned nIdx, char formal);
typedef void  (__thiscall* Assign_t)(void* pStr, const char* s, int n);

static GetItem_t   g_origGetItem   = nullptr;
static GetString_t g_origGetString = nullptr;
static Assign_t    g_Assign        = (Assign_t)0x004150D8;   // v84 ZXString<char>::Assign

// ---- state ----------------------------------------------------------------
static bool  g_useArchive     = false;   // EzorsiaV2_UI.wz present next to the exe
static bool  g_ownLoginFrame  = false;   // config.ini [optional] CustomLoginFrame
static bool  g_ownCashShop    = false;   // config.ini [optional] ownCashShopFrame
static int   g_hooked = 0, g_hookMismatch = 0;
static char  g_frameImg[80];             // ".../Common/frame1280", empty if unsupported W

static bool  g_diag        = false;  // hd-res.ini [general] diag=1  -> observe only
static HANDLE g_log = INVALID_HANDLE_VALUE;
static int   g_giOrdinal = 0;

// ---- diagnostics ----------------------------------------------------------
// Written line-at-a-time and flushed, because the interesting line is the LAST one
// before a crash and buffered output would be exactly the part we lose.
static void LogLine(const char* s) {
    if (g_log == INVALID_HANDLE_VALUE) return;
    DWORD wrote = 0;
    WriteFile(g_log, s, (DWORD)strlen(s), &wrote, NULL);
    FlushFileBuffers(g_log);
    OutputDebugStringA(s);
}

// Never fault while reporting a fault: every client pointer is checked before it is read.
static bool Readable(const void* p, SIZE_T n) {
    MEMORY_BASIC_INFORMATION mbi{};
    if (!p || !VirtualQuery(p, &mbi, sizeof(mbi)) || mbi.State != MEM_COMMIT) return false;
    if (mbi.Protect & (PAGE_NOACCESS | PAGE_GUARD)) return false;
    return (SIZE_T)((BYTE*)mbi.BaseAddress + mbi.RegionSize - (BYTE*)p) >= n;
}

static void DiagGetItem(int n, void* sPathData) {
    char line[512], text[192] = "<unreadable>";
    DWORD prefix = 0xFFFFFFFF;
    LONG  refc = -1;
    const wchar_t* w = NULL;
    if (Readable(sPathData, 12)) {
        w = *(const wchar_t**)sPathData;
        refc = *(LONG*)((BYTE*)sPathData + 8);
        // The four bytes IN FRONT of the string: the client's length prefix. This is the
        // field the v2.0.0 hypothesis turns on, so it is logged raw.
        if (Readable((const BYTE*)w - 4, 4)) prefix = *(const DWORD*)((const BYTE*)w - 4);
        if (Readable(w, 2)) {
            int cch = WideCharToMultiByte(CP_ACP, 0, w, -1, text, sizeof(text) - 1, NULL, NULL);
            if (cch <= 0) strcpy(text, "<unconvertible>");
        }
    }
    wsprintfA(line, "[%03d] call  Data_t=%p m_wstr=%p lenprefix=%lu(0x%08lX) ref=%ld \"%s\"\r\n",
              n, sPathData, (void*)w, prefix, prefix, refc, text);
    LogLine(line);
}

static void DiagRet(int n, void* result) {
    char line[128];
    unsigned vt = 0xFFFF;
    if (Readable(result, 2)) vt = *(unsigned short*)result;
    wsprintfA(line, "[%03d] ret   vt=%u\r\n", n, vt);
    LogLine(line);
}

// ---- 5-byte detour --------------------------------------------------------
// Deliberately minimal: these three prologues are each ONE instruction of exactly five
// bytes and contain no rip-relative or branch operand, so relocating them is a memcpy.
// ponytail: no general hook engine; if a future target's first instruction is not
// 5 self-contained bytes, that is the moment to vendor MinHook, not before.
static bool InstallHook(HdHook& h) {
    // Both refusals count, so "hooks 0 of 2, mismatch 0" can only ever mean the archive
    // was switched off -- never that an address quietly went missing.
    MEMORY_BASIC_INFORMATION mbi{};
    if (!VirtualQuery((LPCVOID)h.addr, &mbi, sizeof(mbi)) || mbi.State != MEM_COMMIT ||
        memcmp((const void*)h.addr, h.expect, 5) != 0) { ++g_hookMismatch; return false; }

    BYTE* tramp = (BYTE*)VirtualAlloc(NULL, 16, MEM_COMMIT | MEM_RESERVE,
                                      PAGE_EXECUTE_READWRITE);
    if (!tramp) return false;
    memcpy(tramp, (const void*)h.addr, 5);                     // the displaced instruction
    tramp[5] = 0xE9;
    *(int*)(tramp + 6) = (int)((DWORD_PTR)(h.addr + 5) - ((DWORD_PTR)tramp + 5) - 5);

    BYTE jmp[5];
    jmp[0] = 0xE9;
    *(int*)(jmp + 1) = (int)((DWORD_PTR)h.detour - h.addr - 5);
    DWORD old = 0;
    if (!VirtualProtect((LPVOID)h.addr, 5, PAGE_EXECUTE_READWRITE, &old)) return false;
    memcpy((void*)h.addr, jmp, 5);
    VirtualProtect((LPVOID)h.addr, 5, old, &old);
    FlushInstructionCache(GetCurrentProcess(), (LPCVOID)h.addr, 5);

    *h.orig = tramp;
    ++g_hooked;
    return true;
}

// ---- hook 1: OBSERVE the namespace lookups --------------------------------
// v2.0.0 swapped the path here and crashed the client. That swap is deleted rather
// than left behind a flag: writing a .rdata literal into a field the client frees at
// ptr-4 cannot be made correct by gating it. The replacement has to build the string
// the way the client does, and the log this hook produces is what tells us how.
//
// So this is now a pure observer -- it reads, prints, and calls the original. A build
// that changes nothing is safe for the owner to launch, which is the entire point.
static void* __fastcall GetItem_hook(void* pThis, void* edx, void* result, void* sPathData) {
    int n = ++g_giOrdinal;
    // Bounded so a long session cannot fill the disk; startup is all we need and it is
    // the first few dozen calls. A "call" line with no matching "ret" is the one that
    // threw -- that is how the log identifies the failing lookup.
    bool show = g_diag && n <= 400;
    if (show) DiagGetItem(n, sPathData);
    void* r = g_origGetItem(pThis, edx, result, sPathData);
    if (show) DiagRet(n, result);
    return r;
}

// ---- hook 2: point four UI strings at the archive's img -------------------
// Upstream remaps exactly these four StringPool ids. Note its `case 1307:` has no
// break when ownLoginFrame is set and falls through into `case 1301:`, assigning the
// cash-shop background to the login frame; written correctly here.
static const char* ArchivePathFor(unsigned nIdx) {
    if (!g_useArchive) return nullptr;
    switch (nIdx) {
    case 1307: return (!g_ownLoginFrame && g_frameImg[0]) ? g_frameImg : nullptr;
    case 1301: return g_ownCashShop ? nullptr : "MapleEzorsiaV2wzfiles.img/Base/backgrnd";
    case 1302: return g_ownCashShop ? nullptr : "MapleEzorsiaV2wzfiles.img/Base/backgrnd1";
    case 5361: return g_ownCashShop ? nullptr : "MapleEzorsiaV2wzfiles.img/Base/backgrnd2";
    default:   return nullptr;
    }
}

static void* __fastcall GetString_hook(void* pThis, void* edx, void* result,
                                       unsigned nIdx, char formal) {
    void* ret = g_origGetString(pThis, edx, result, nIdx, formal);
    if (const char* rep = ArchivePathFor(nIdx))
        if (ret) g_Assign(ret, rep, -1);
    return ret;
}

static HdHook kHdHooks[] = {
    { "IWzNameSpace::GetItem", 0x005EB5E6, { 0xB8, 0x00, 0x35, 0xAE, 0x00 },
      (void*)&GetItem_hook,   (void**)&g_origGetItem },
    { "StringPool::GetString", 0x007C3408, { 0xB8, 0x3E, 0x65, 0xB0, 0x00 },
      (void*)&GetString_hook, (void**)&g_origGetString },
};

// Only the widths upstream ships a frame for; anything else keeps the vanilla frame
// rather than pointing at an img node that does not exist.
static void SetArchiveParams(int w, bool haveWz, bool ownLogin, bool ownCash) {
    g_useArchive    = haveWz;
    g_ownLoginFrame = ownLogin;
    g_ownCashShop   = ownCash;
    g_frameImg[0]   = 0;
    if (w == 1024 || w == 1280 || w == 1366 || w == 1600 || w == 1920)
        wsprintfA(g_frameImg, "MapleEzorsiaV2wzfiles.img/Common/frame%d", w);
}

// v2.1.0 installs the GetItem OBSERVER and nothing else.
//
// StringPool::GetString stays in kHdHooks above so test_hd.py keeps checking its guard
// bytes against both dumps, but it is deliberately not installed: remapping four UI
// strings onto MapleEzorsiaV2wzfiles.img is pointless while the img is not mounted, and
// every hook that is live is a hook that can be blamed for the next crash. It goes back
// in with the mount, once the log says how to build the path string.
// ZXString<char>::Assign is the least independently corroborated of the three addresses
// (it resolved on a forward-only window). It is not called by this build, but its guard
// bytes are checked and reported anyway: that turns the owner's launch into a free
// confirmation of the address on the REAL client, instead of one more thing taken on
// faith when the mount is rebuilt.
static const BYTE kAssign[5] = { 0x83, 0x7C, 0x24, 0x04, 0x00 };

static void InstallArchiveHooks(const char* logPath) {
    if (!g_diag) return;                      // diag=0 -> byte-identical to v1 behaviour
    g_log = CreateFileA(logPath, GENERIC_WRITE, FILE_SHARE_READ, NULL,
                        CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    LogLine("hd-res 2.1.0 diagnostic: IWzNameSpace::GetItem observer, NO swap.\r\n"
            "lenprefix is the DWORD at m_wstr-4 -- the client frees m_wstr-4, so this is\r\n"
            "its length field. For a real client string it should equal the byte length.\r\n");

    char line[128];
    bool assignOk = Readable((const void*)g_Assign, 5)
                    && memcmp((const void*)g_Assign, kAssign, 5) == 0;
    wsprintfA(line, "ZXString<char>::Assign @%p guard %s\r\n",
              (void*)g_Assign, assignOk ? "MATCHES" : "DIFFERS <-- address is wrong");
    LogLine(line);

    for (HdHook& h : kHdHooks)
        if (h.orig == (void**)&g_origGetItem) InstallHook(h);
    wsprintfA(line, "GetItem observer %s\r\n\r\n",
              g_hooked ? "installed" : "REFUSED (guard bytes differ)");
    LogLine(line);
}
