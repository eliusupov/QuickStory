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
#include <cstdlib>

// The last three are group K: the value is a config.ini scalar written verbatim rather
// than a function of the resolution, so those rows ignore HdFormula entirely.
enum HdKind { K_INT, K_BYTE, K_SHORT, K_DOUBLE, K_FILL, K_STR, K_BYTES, K_CAVE,
              K_DMGCAP32, K_DMGCAPD, K_SPDCAP };

// value = aW*W/2 + aH*H/2 + aWH*W*H + aM*MsgAmount + k
struct HdFormula { int aW, aH, aWH, aM, k; };

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

// config.ini [general] MsgAmount and [optional] setDamageCap / speedMovementCap.
// Defaults are Ezorsia's own, which on this client happen to equal v84's vanilla
// values -- so leaving them alone writes the bytes that are already there.
static int    g_msg    = 26;
static int    g_msgClamped = 0;   // the out-of-range value the owner actually wrote, if any
static double g_dmgCap = 199999.0;
static int    g_spdCap = 140;

static int Eval(const HdFormula& f) {
    return f.aW * g_w / 2 + f.aH * g_h / 2 + f.aWH * g_w * g_h + f.aM * g_msg + f.k;
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

// ---- useTubi --------------------------------------------------------------
// config.ini [optional] useTubi, default false (upstream's default, kept).
//
// Upstream is one unexplained line -- FillBytes(0x00485C32, 0x90, 2) -- and the author
// never says what it does anywhere in the project. Read off the disassembly instead:
//
// v84 0x0048903A is a two-argument boolean helper with 107 call sites. It returns TRUE
// only if a sub-check passes AND enough time has elapsed since a stored timestamp:
//
//     0048906B  2B 86 B0 20 00 00   sub eax, [esi+0x20B0]   ; eax = now - lastTime
//     00489071  3B 44 24 08         cmp eax, [esp+8]        ; vs the caller's threshold
//     00489075  7C 05               jl  0x48907C            ; <-- THIS is what tubi kills
//     00489077  6A 01 / 58          push 1 / pop eax        ; -> TRUE
//     0048907C  33 C0               xor eax, eax            ; -> FALSE
//
// The caller at 0x004F866D passes 0xC8, so that one is a 200 ms gate. NOPping the `jl`
// makes the FALSE path unreachable and the helper always returns TRUE. In plain terms:
// it removes a client-side minimum-delay check, so actions the client would otherwise
// throttle fire as fast as they are issued. The server still applies its own limits --
// this relaxes the client only.
//
// The v83 address is NOT carried across, and localhome.exe could not have told us the
// original instruction anyway: that image is a pre-patched repack whose bytes at
// 0x00485C32 already read 90 90. The v84 site was resolved from surrounding shape --
// the `push 1 / pop eax / jmp / xor eax,eax / pop esi / ret 8` tail hits exactly once
// per image, matches ordinally v83 0x00485C34 -> v84 0x00489077, and is byte-identical
// in both dumps. The guard below is the intact `jl`, so on any client where this is not
// that instruction we skip instead of writing.
static const BYTE kTubiJl[2] = { 0x7C, 0x05 };
static const DWORD kTubiAddr = 0x00489075;
static bool g_tubi = false, g_tubiDone = false, g_tubiMismatch = false;

static void ApplyTubi() {
    if (!g_tubi) return;
    MEMORY_BASIC_INFORMATION mbi{};
    if (!VirtualQuery((LPCVOID)kTubiAddr, &mbi, sizeof(mbi)) || mbi.State != MEM_COMMIT ||
        memcmp((const void*)kTubiAddr, kTubiJl, 2) != 0) { g_tubiMismatch = true; return; }
    g_tubiDone = PokeFill(kTubiAddr, 0x90, 2);
}

// ---- NoWhack ---------------------------------------------------------------
// config.ini [optional] NoWhack, default TRUE (owner's call; this is a deliberate
// deviation from v84, not a parity gap -- Big Bang is what removed the mechanic).
//
// Pre-Big-Bang, a ranged class whose target is inside the close-range box does NOT
// fire: the client substitutes a melee "whack" (bow/crossbow/gun swing, claw punch).
// Nothing in the wz data controls it and nothing on the server sees it -- the server
// only ever enforces a MAXIMUM distance (AbstractDealDamageHandler's DISTANCE_HACK).
// It is a branch in the client's attack dispatch:
//
//     009A88A2  call 0x788CF7          ; close-range predicate on [ebp-0x14]
//     009A88A7  test eax, eax
//     009A88AA  push esi x4            ; four zero args, shared by both paths
//     009A88AE  0F 85 77 01 00 00      ; jne 0x9A8A2B   <-- forced unconditional
//     009A88BC  call 0x98875D          ; the melee substitution we want skipped
//
// Both arms converge on 0x9A8A2B; the only difference is whether the melee call runs.
// Forcing the jne to jmp skips it, so the ranged attack goes out at any range.
//
// PROVENANCE: v83 0x009698BC "No Whack", credit Rulax, from the RaGEZONE v83 client
// address compilation archived at docs/04-v83-client-addresses.md. Resolved onto v84
// by tools/hd/resolve.py's T1 tier: the +-32B masked context signature hits EXACTLY
// ONCE in each of the two independent v84 memory dumps, both at 0x009A88AE, and the
// instruction there is byte-identical to v83's (same opcode, same rel32, same target
// offset). The guard below is that instruction, so any other client skips the write.
//
// The compilation lists a second op under the same heading -- Jmp(0x009516C2 ->
// 0x0095138F). It does NOT resolve onto v84: v84 rewrote that neighbourhood and the
// site's byte run is absent from both dumps. It is deliberately not shipped rather
// than guessed at. If close-range still whacks somewhere after this, that is the
// remaining lead.
static const BYTE kNoWhackJne[6] = { 0x0F, 0x85, 0x77, 0x01, 0x00, 0x00 };
static const BYTE kNoWhackJmp[6] = { 0xE9, 0x78, 0x01, 0x00, 0x00, 0x90 };
static const DWORD kNoWhackAddr = 0x009A88AE;
static bool g_noWhack = true, g_noWhackDone = false, g_noWhackMismatch = false;

static void ApplyNoWhack() {
    if (!g_noWhack) return;
    MEMORY_BASIC_INFORMATION mbi{};
    if (!VirtualQuery((LPCVOID)kNoWhackAddr, &mbi, sizeof(mbi)) || mbi.State != MEM_COMMIT ||
        memcmp((const void*)kNoWhackAddr, kNoWhackJne, sizeof(kNoWhackJne)) != 0) {
        g_noWhackMismatch = true; return;
    }
    g_noWhackDone = Poke(kNoWhackAddr, kNoWhackJmp, sizeof(kNoWhackJmp));
}

// ---- PetLootRange ----------------------------------------------------------
// config.ini [optional] PetLootRange, a multiplier on the box a pet loots from.
// Default 2.0 (owner: "VAC items in a radius twice the size of the pet itself").
//
// A pet's pickup range is ONE rectangle, built around the pet's own position once
// before the drop loop, and it is the whole range check. There is no separate
// "walk toward that drop" targeting radius -- the pet's movement is CVecCtrlPet
// following the owner -- so this rect IS the pet's reach:
//
//     CPet update 0x00720B39 -> CPet method 0x00722616 -> CDropPool 0x0050D652
//       0x0050D652 builds petRect, then per drop calls PtInRect (0x0050D7D2) with
//       the drop displayer's x/y; a hit stamps the drop and calls the PET_LOOT
//       packet builder 0x00722672 (push 0xAF -- what PetLootHandler parses).
//
//     0050D663  8B 4D 0C        mov  ecx, [ebp+0xC]    ; POINT* petPos
//     0050D666  8B 01           mov  eax, [ecx]        ; x
//     0050D668  8B 49 04        mov  ecx, [ecx+4]      ; y
//     0050D66B  8D 50 E7        lea  edx, [eax-0x19]   ; left   = x - 25
//     0050D66E  89 55 CC        mov  [ebp-0x34], edx
//     0050D671  8D 51 CE        lea  edx, [ecx-0x32]   ; top    = y - 50
//     0050D674  57              push edi
//     0050D675  8B 3D 68 0C C4 00
//     0050D67B  83 C0 19        add  eax, 0x19         ; right  = x + 25
//     0050D67E  83 C1 0A        add  ecx, 0x0A         ; bottom = y + 10
//
// Four inline imm8 operands. No .rdata constant, nothing shared, nothing to repoint,
// and every replacement is the same instruction length.
//
// The guard has to be all 31 bytes: the same -0x19/-0x32/+0x19/+0x0A idiom appears a
// second time at 0x0050D4CE, and that one is the PLAYER's own pickup box. A short
// signature would hit both.
//
// Untouched upstream, and the reason a large multiplier buys less than it looks: a
// 500 ms send throttle at 0x00722696 (cmp eax, 0x1F4) caps the loot packet rate.
//
// Each edge saturates at its own byte limit, not at one shared multiplier, so the
// mul is allowed past the point where the first edge pins. Ceilings, from -128..127:
//     left  25*mul <= 128  -> 5.12x      top    50*mul <= 128  -> 2.56x
//     right 25*mul <= 127  -> 5.08x      bottom 10*mul <= 127  -> 12.70x
// At 12.7 every edge is pinned: -128 / -128 / +127 / +127, a 255x255 px box. That is
// the largest box reachable without rewriting the instructions.
//
// ponytail: clamp per edge instead of capping the mul at the first edge to pin (2.56).
// A literal 4x would want top = -200, which needs the imm32 lea -- 33 bytes of code in
// a 22-byte window, i.e. a detour into a cave. Saturating gets a BIGGER box than 4x in
// both dimensions (255x255 vs 200x240); it just trades reach above the pet (128 vs 200)
// for reach to the sides and below. Drops rest on the ground, so that trade is free.
// If reach far ABOVE the pet ever matters, that is when the cave becomes worth writing.
static const DWORD kPetLootAddr = 0x0050D663;
static const BYTE kPetLootGuard[31] = {
    0x8B, 0x4D, 0x0C, 0x8B, 0x01, 0x8B, 0x49, 0x04, 0x8D, 0x50, 0xE7, 0x89, 0x55, 0xCC, 0x8D, 0x51,
    0xCE, 0x57, 0x8B, 0x3D, 0x68, 0x0C, 0xC4, 0x00, 0x83, 0xC0, 0x19, 0x83, 0xC1, 0x0A, 0x89,
};
// offsets into the guard of the four operand bytes, and their vanilla values in px
static const int kPetLootOff[4] = { 10, 16, 26, 29 };       // left, top, right, bottom
static const int kPetLootBase[4] = { -25, -50, 25, 10 };
static double g_petLootMul = 2.0;
static bool g_petLootDone = false, g_petLootMismatch = false;

// The edge value actually written, after the imm8 clamp. Reported, so the popup shows
// the real box and not the box the multiplier asked for.
static int PetLootEdge(int i) {
    int v = (int)(kPetLootBase[i] * g_petLootMul);
    if (v > 127) v = 127;
    if (v < -128) v = -128;
    return v;
}

static void ApplyPetLootRange() {
    if (g_petLootMul == 1.0) return;
    MEMORY_BASIC_INFORMATION mbi{};
    if (!VirtualQuery((LPCVOID)kPetLootAddr, &mbi, sizeof(mbi)) || mbi.State != MEM_COMMIT ||
        memcmp((const void*)kPetLootAddr, kPetLootGuard, sizeof(kPetLootGuard)) != 0) {
        g_petLootMismatch = true; return;
    }
    for (int i = 0; i < 4; ++i) {
        BYTE b = (BYTE)(PetLootEdge(i) & 0xFF);
        if (!Poke(kPetLootAddr + kPetLootOff[i], &b, 1)) return;
    }
    g_petLootDone = true;
}

// ---- LadderSpeed -----------------------------------------------------------
// config.ini [optional] LadderSpeed, a multiplier on the ladder/rope climb rate.
// Default 1.5 (owner: "all characters go up ladders fifty percent faster").
//
// Climbing is NOT a physics param -- Map/Physics.img has no ladder key, and none of
// the 19 doubles it loads is read on the ladder path. The client moves the character
// a flat 3 px per UpdateActive tick, with no elapsed-time term:
//
//     CVecCtrlUser::UpdateLadderMove   v84 0x00A1429A   (v83 0x009CC627)
//       reached only from CVecCtrlUser::UpdateActive (v84 0x00A13B6E) at 0x00A13D6C,
//       guarded by m_pLadder != NULL. Exactly one caller.
//
//     00A1435B  call 0x417012            ; GetKeyY() -> -1 / 0 / +1
//     00A14363  fild dword ptr [ebp-4]
//     00A1436C  DC 0D 68 49 B4 00        ; fmul qword ptr [0x00B44968]  <-- 3.0, the step
//     00A14372  fadd qword ptr [ebp-0xc] ; + Y
//     00A1437C  call 0x5457bf            ; SetY()
//
// The 3.0 at 0x00B44968 is a SHARED .rdata constant -- 13 instructions across the
// image read it, three of them the same ladder-step shape in three different vector
// controllers (0x00A1436C user, 0x00A0C513 pet, 0x00A17F3C remote). Patching the
// double would move all of them. So instead we repoint THIS ONE instruction's disp32
// at a double we own, in this DLL's data. One 4-byte write, one code path, nothing
// written into data anyone else reads.
//
// ponytail: the disp32 points into this DLL. Safe only because edits\ DLLs are
// LoadLibrary'd by the ijl15 proxy and never freed; if that ever changes, this has to
// become a copy into a client-owned constant slot instead.
static double g_ladderStep = 4.5;                                   // 3.0 * 1.5
static const BYTE kLadderFmul[6] = { 0xDC, 0x0D, 0x68, 0x49, 0xB4, 0x00 };
static const DWORD kLadderAddr = 0x00A1436C;
static double g_ladderMul = 1.5;
static bool g_ladderDone = false, g_ladderMismatch = false;

static void ApplyLadderSpeed() {
    if (g_ladderMul == 1.0) return;
    MEMORY_BASIC_INFORMATION mbi{};
    if (!VirtualQuery((LPCVOID)kLadderAddr, &mbi, sizeof(mbi)) || mbi.State != MEM_COMMIT ||
        memcmp((const void*)kLadderAddr, kLadderFmul, sizeof(kLadderFmul)) != 0) {
        g_ladderMismatch = true; return;
    }
    g_ladderStep = 3.0 * g_ladderMul;
    double* pStep = &g_ladderStep;
    g_ladderDone = Poke(kLadderAddr + 2, &pStep, sizeof(pStep));
}

// ---- PetLootWhileMoving ----------------------------------------------------
// config.ini [optional] PetLootWhileMoving, default true. Owner: "when the character
// moves the pet stops moving ... because I'm moving in a radius around it, it just
// stands there."
//
// The cause is not an owner-stance check. The pet has a sweep-to-drop direction that
// OVERRIDES its follow direction, and the override sits behind a 100 ms
// "follow target unchanged" cooldown. The follow target is ownerX +/- 160, so it
// changes on every tick the owner's X changes. Walking = the cooldown never expires
// = the sweep never fires, and the follow logic's own 80 px dead-band then leaves the
// pet standing still.
//
//     CDropPool::UpdatePetSweep 0x005110A8 writes CVecCtrlPet+0x25c = +/-1 at
//       0x00511411 for drops within ownerX +/- 125 (+/- 275 when CPet+0x14c is set).
//     CVecCtrlPet::UpdateActive 0x00A0B8BD (vtable 0x00B92730 slot 54), tail:
//
//     00A0C3C0  cmp  dword [ebx+0x25c], 0   ; sweep dir set by the drop pool?
//     00A0C3C9  je   0xA0C444
//     00A0C3CB  call [0xB41348]             ; timeGetTime
//     00A0C3D1  mov  ecx, [ebx+0x254]       ; t of last follow-target change
//     00A0C3D7  add  ecx, 0x64              ; +100 ms
//     00A0C3DA  cmp  eax, ecx
//     00A0C3DC  76 66  jbe 0xA0C444         ; <-- owner moved recently: SKIP the sweep
//     00A0C43E  mov  [ebx+0x26c], eax       ; direction := sweep dir
//     00A0C44E  call 0x9FF005               ; CVecCtrl::SetKeyDir
//
// NOPping the jbe lets the sweep win every tick. The leash the owner asked to KEEP is
// untouched: the 80 px follow dead-band (0x00A0C337) and the +/-300/+/-200
// teleport-to-owner box (0x00A0BCE7) are both left alone, and the sweep's candidate
// window is anchored to the OWNER, not the pet, so the pet cannot wander off.
//
// Packet rate is not a concern even though MovePetHandler validates nothing:
// CMovePath::IsReadyToSend 0x006A1502 needs 1000 ms accumulated (500 with [path+0x40])
// before MOVE_PET goes out -- a hard ceiling of ~2/s per pet, patched or not. PET_LOOT
// has its own 500 ms throttle at 0x00722696.
//
// v83 precedent: the same construct at 0x009C4C1D, the same 0x224 delta from
// CVecCtrlPet::EndUpdateActive (v83 0x009C4E41, v84 0x00A0C600).
//
// ponytail: NOP the gate rather than raise the 0x64. Raising it only widens the window
// in which a moving owner still suppresses the sweep; it never removes it.
static const DWORD kPetSweepAddr = 0x00A0C3CB;      // guard starts here
static const DWORD kPetSweepJbe = 0x00A0C3DC;       // the two bytes we overwrite
static const BYTE kPetSweepGuard[18] = {
    0xFF, 0x15, 0x48, 0x13, 0xB4, 0x00, 0x8B, 0x8B, 0x54, 0x02, 0x00, 0x00,
    0x83, 0xC1, 0x64, 0x3B, 0xC1, 0x76,
};
static bool g_petSweep = true, g_petSweepDone = false, g_petSweepMismatch = false;

static void ApplyPetLootWhileMoving() {
    if (!g_petSweep) return;
    MEMORY_BASIC_INFORMATION mbi{};
    if (!VirtualQuery((LPCVOID)kPetSweepAddr, &mbi, sizeof(mbi)) || mbi.State != MEM_COMMIT ||
        memcmp((const void*)kPetSweepAddr, kPetSweepGuard, sizeof(kPetSweepGuard)) != 0) {
        g_petSweepMismatch = true; return;
    }
    g_petSweepDone = PokeFill(kPetSweepJbe, 0x90, 2);
}

// ---- PetSweepRange / PetLootDelay ------------------------------------------
// The pet has TWO windows, and widening only the grab box (PetLootRange above) is
// what made the pet stop moving: it vacuumed every drop the instant it landed, the
// sweep never had a target left, and the follow dead-band parked it. Owner: "he
// should still move as if he's small". So keep the grab box modest and widen the
// window the pet TARGETS from instead.
//
//     CDropPool::UpdatePetSweep 0x005110A8, called from 0x0050DF51 every drop-pool
//     tick. Zeroes CVecCtrlPet+0x25c per pet at 0x00511136, then for each drop tests
//     each pet and finally sets the winner's +0x25c = (dropX > petX) ? +1 : -1 at
//     0x00511411. There is NO "close enough" stop value in the sweep -- the setle/dec
//     idiom at 0x005113F3 can only yield +1 or -1 -- so the pet walks until the grab
//     box takes the drop. That is exactly the behaviour asked for.
//
// X window, OWNER-anchored ([ebp-0x44], written once at 0x0051115B from CUserLocal):
//     00511215  8D 48 83   lea ecx,[eax-0x7d]   ; left  = ownerX - 125
//     00511218  83 C0 7D   add eax,0x7d         ; right = ownerX + 125
// Both are imm8/disp8 and pin at 128, but the client already has an imm32 widener
// sitting right there behind a two-byte je:
//     00511231  74 0B      je 0x51123E          ; taken unless CPet+0x14c is set
//     00511233  B8 96 00 00 00  mov eax,0x96    ; +/-150 extra
//     00511238  29 45 A4   sub [ebp-0x5c],eax   ; left  -= eax
//     0051123B  01 45 AC   add [ebp-0x54],eax   ; right += eax
// NOP the je and the widener is unconditional, with the amount a free imm32. So the
// X half-width becomes 125 + PetSweepRange, no cave, no instruction growth.
//
// Y window is PET-anchored and deliberately left alone (petY-50 .. petY+10, at
// 0x00511351 / 0x00511366). Widening it is what would send the pet chasing drops on
// other platforms, which it cannot path to.
//
// PetLootDelay: a drop is ignored until it is 3000 ms old. This is the "sometimes he
// just doesn't go" the owner reported. The same imm32 appears in BOTH paths and they
// have to move together, or the pet walks to a drop it is not yet allowed to grab:
//     0051133E  81 F9 B8 0B 00 00  cmp ecx,0xBB8  (sweep)  -> jl reject
//     0050D7B0  81 F9 B8 0B 00 00  cmp ecx,0xBB8  (grab)   -> jl reject
//
// All three guards verified unique and byte-identical in both v84 dumps, and no
// rel8/rel32/jcc32 at any decode alignment and no absolute dword targets any byte
// overwritten here.
//
// ponytail: default sweep half-width is 300 = the teleport-to-owner box at
// 0x00A0BCE7. Past that the pet gets yanked back the moment it arrives, so a bigger
// number buys nothing but teleport thrash. That box is the real ceiling, not the imm32.
static const DWORD kPetSweepGateAddr = 0x00511231;   // guard + the je we NOP
static const BYTE kPetSweepGateGuard[13] = {
    0x74, 0x0B, 0xB8, 0x96, 0x00, 0x00, 0x00, 0x29, 0x45, 0xA4, 0x01, 0x45, 0xAC,
};
static const int kPetSweepImmOff = 3;                // imm32 of `mov eax,0x96`
static const int kPetSweepBaseHalf = 125;            // the imm8 pair we leave alone

static const DWORD kPetAgeSweepAddr = 0x00511336;
static const BYTE kPetAgeSweepGuard[16] = {
    0x00, 0x00, 0x8B, 0x4D, 0xE4, 0x2B, 0x48, 0x54,
    0x81, 0xF9, 0xB8, 0x0B, 0x00, 0x00, 0x7C, 0x73,
};
static const DWORD kPetAgeGrabAddr = 0x0050D7A8;
static const BYTE kPetAgeGrabGuard[16] = {
    0x75, 0x5A, 0x8B, 0x4D, 0xF0, 0x2B, 0x48, 0x54,
    0x81, 0xF9, 0xB8, 0x0B, 0x00, 0x00, 0x7C, 0x4C,
};
static const int kPetAgeImmOff = 10;                 // imm32 of `cmp ecx,0xBB8`

static int g_petSweepExtra = 175;                    // 125 + 175 = 300
static int g_petLootDelay = 1000;                    // vanilla 3000
static bool g_petSweepRangeDone = false, g_petSweepRangeMismatch = false;
static bool g_petDelayDone = false, g_petDelayMismatch = false;

static bool GuardOk(DWORD addr, const BYTE* want, SIZE_T n) {
    MEMORY_BASIC_INFORMATION mbi{};
    return VirtualQuery((LPCVOID)addr, &mbi, sizeof(mbi)) && mbi.State == MEM_COMMIT &&
           memcmp((const void*)addr, want, n) == 0;
}

static void ApplyPetSweepRange() {
    if (g_petSweepExtra == 0) return;
    if (!GuardOk(kPetSweepGateAddr, kPetSweepGateGuard, sizeof(kPetSweepGateGuard))) {
        g_petSweepRangeMismatch = true; return;
    }
    if (!Poke(kPetSweepGateAddr + kPetSweepImmOff, &g_petSweepExtra, 4)) return;
    g_petSweepRangeDone = PokeFill(kPetSweepGateAddr, 0x90, 2);   // je -> nop nop, last
}

static void ApplyPetLootDelay() {
    if (g_petLootDelay == 3000) return;
    if (!GuardOk(kPetAgeSweepAddr, kPetAgeSweepGuard, sizeof(kPetAgeSweepGuard)) ||
        !GuardOk(kPetAgeGrabAddr, kPetAgeGrabGuard, sizeof(kPetAgeGrabGuard))) {
        g_petDelayMismatch = true; return;
    }
    if (!Poke(kPetAgeSweepAddr + kPetAgeImmOff, &g_petLootDelay, 4)) return;
    g_petDelayDone = Poke(kPetAgeGrabAddr + kPetAgeImmOff, &g_petLootDelay, 4);
}

// ---- PetSweepHeight --------------------------------------------------------
// config.ini [optional] PetSweepHeight, the vertical half-window the pet TARGETS
// drops within. Vanilla is petY-50 above and only petY+10 below, and that +10 is why
// a pet will not follow a drop down a slope: walking a slope changes the pet's Y, so
// anything more than 10 px downhill falls straight out of the candidate set and the
// sweep direction goes back to 0.
//
//     0051134E  8B 4D C8  mov ecx,[ebp-0x38]     ; petY
//     00511351  83 C1 CE  add ecx,-0x32          ; top    = petY - 50
//     00511354  3B C1     cmp eax,ecx
//     00511356  7E 5E     jle 0x5113B6           ; drop above the window: reject
//     00511363  8B 4D C8  mov ecx,[ebp-0x38]
//     00511366  83 C1 0A  add ecx,+0x0A          ; bottom = petY + 10
//     00511369  3B C1     cmp eax,ecx
//     0051136B  7D 49     jge 0x5113B6           ; drop below the window: reject
//
// Both imm8, one instruction each, no shared constant. Default 128 pins both:
// petY-128 .. petY+127, a 255 px tall window instead of 60.
//
// ponytail: symmetric, unlike vanilla. The asymmetry only made sense when the pet
// never left flat ground -- drops fall from above, so vanilla watched upward. Slopes
// need the downhill half, and there is no reason to spend a knob on each end.
//
// KNOWN COST, and the reason this is a knob and not a constant: the sweep has no
// reachability test. A drop on a platform below is now a candidate, the pet walks at
// it, cannot path there, and oscillates until the drop ages out or the teleport box
// pulls it back. Lower PetSweepHeight if that shows up more than the slopes are worth.
static const DWORD kPetSweepYAddr = 0x0051134E;
static const BYTE kPetSweepYGuard[31] = {
    0x8B, 0x4D, 0xC8, 0x83, 0xC1, 0xCE, 0x3B, 0xC1, 0x7E, 0x5E, 0x8B, 0x45, 0xD0, 0x8D, 0x48, 0x68,
    0xE8, 0xCE, 0x7E, 0xF1, 0xFF, 0x8B, 0x4D, 0xC8, 0x83, 0xC1, 0x0A, 0x3B, 0xC1, 0x7D, 0x49,
};
static const int kPetSweepYTopOff = 5;      // imm8 of `add ecx,-0x32`
static const int kPetSweepYBotOff = 26;     // imm8 of `add ecx,+0x0A`
static int g_petSweepH = 128;
static bool g_petSweepHDone = false, g_petSweepHMismatch = false;

static void ApplyPetSweepHeight() {
    if (g_petSweepH == 0) return;
    if (!GuardOk(kPetSweepYAddr, kPetSweepYGuard, sizeof(kPetSweepYGuard))) {
        g_petSweepHMismatch = true; return;
    }
    int up = g_petSweepH > 128 ? 128 : g_petSweepH;      // imm8 floor is -128
    int dn = g_petSweepH > 127 ? 127 : g_petSweepH;      // imm8 ceiling is +127
    BYTE t = (BYTE)((-up) & 0xFF), b = (BYTE)(dn & 0xFF);
    if (!Poke(kPetSweepYAddr + kPetSweepYTopOff, &t, 1)) return;
    g_petSweepHDone = Poke(kPetSweepYAddr + kPetSweepYBotOff, &b, 1);
}

// ---- PetLeashX -------------------------------------------------------------
// config.ini [optional] PetLeashX, the X half-width of the box the owner must stay
// inside or the pet teleports to him. Vanilla 300. Default 350, which buys the pet
// 50 px of slack past the 300 px it is allowed to walk for a drop (PetSweepRange),
// so a fetch at maximum range completes instead of being cut short by the snap.
// Owner: "I want the pet to be able to walk in a box from me as far as possible
// until teleport ... increase the teleport range by the width of two pets as they
// were in 1x, to accommodate left and right" -- a vanilla 1x pet box is 50 px wide.
//
// The leash is a RECT built on the stack, offset by the PET's position, then tested
// with PtInRect against the OWNER's live position:
//
//     00A0BBDD  C7 45 94 D4 FE FF FF  mov [ebp-0x6c],-300   ; left    <-- patched
//     00A0BBE4  C7 45 98 38 FF FF FF  mov [ebp-0x68],-200   ; top
//     00A0BBEB  C7 45 9C 2C 01 00 00  mov [ebp-0x64],+300   ; right   <-- patched
//     00A0BBF2  C7 45 A0 C8 00 00 00  mov [ebp-0x60],+200   ; bottom
//     00A0BC20  call [0xC49B54]       ; OffsetRect(&leash, petX, petY)
//     00A0BC40  call [0xC49B84]       ; PtInRect(&leash, ownerX, ownerY)
//     00A0BC48  jne 0xA0BD0A          ; inside -> normal follow; outside -> teleport
//
// The owner-ness of the tested point is proven, not assumed: [ebp-0x14] comes from
// CPet+0x90, and CPet+0x90 is read as a CUser* at 0x00720D17 (`mov eax,[ebx+0x90]` /
// `mov eax,[eax+0x570]`), the same +0x570 field the CUserLocal global at [0xC452E8]
// is read through at 0x004FDF59 and 0x00720A86. The point itself comes from a live
// GetPos virtual, not a cached spawn point or a foothold.
//
// Y is deliberately NOT touched. Follow has no pathfinding, and the snap is what
// recovers a pet stranded on the wrong foothold; vertical is where widening actually
// costs something. Only X was asked for.
//
// NOT the leash, all checked and left alone: the +/-50 rect2 at 0x00A0BBC1/C4 (asks
// whether the teleport DESTINATION is reachable ground), the 50.0 at .rdata
// 0x00B92620 (selects climb vs walk handling), the 0x1E/0xC8 pair at 0x00A0BB2C
// (a ~7-tick ladder timeout, not distance), and the 80/70/60 follow dead-band.
// Note the loader's old "teleport box at 0x00A0BCE7" note was wrong: that `6A 04` is
// the teleport's move-action stamp downstream, not the test.
//
// ponytail: two writes, not four. The paired knob is PetSweepRange -- with the leash
// at 350 and the sweep at 300 the sweep is the fetch limiter, which is the intended
// order. Raise the sweep only alongside this, never past it.
static const DWORD kPetLeashAddr = 0x00A0BBDD;
static const BYTE kPetLeashGuard[28] = {
    0xC7, 0x45, 0x94, 0xD4, 0xFE, 0xFF, 0xFF, 0xC7, 0x45, 0x98, 0x38, 0xFF, 0xFF, 0xFF,
    0xC7, 0x45, 0x9C, 0x2C, 0x01, 0x00, 0x00, 0xC7, 0x45, 0xA0, 0xC8, 0x00, 0x00, 0x00,
};
static const int kPetLeashLeftOff = 3;      // imm32 of `mov [ebp-0x6c],-300`
static const int kPetLeashRightOff = 17;    // imm32 of `mov [ebp-0x64],+300`
static int g_petLeashX = 350;
static bool g_petLeashDone = false, g_petLeashMismatch = false;

static void ApplyPetLeashX() {
    if (g_petLeashX == 300) return;
    if (!GuardOk(kPetLeashAddr, kPetLeashGuard, sizeof(kPetLeashGuard))) {
        g_petLeashMismatch = true; return;
    }
    int right = g_petLeashX, left = -g_petLeashX;
    if (!Poke(kPetLeashAddr + kPetLeashLeftOff, &left, 4)) return;
    g_petLeashDone = Poke(kPetLeashAddr + kPetLeashRightOff, &right, 4);
}

// ---- WorldMapWarp ----------------------------------------------------------
// config.ini [optional] WorldMapWarp, default true. Click a town dot on the World
// Map (W) and warp there. Owner: "I want to be able to click on any button this map
// and be teleported ... the command should also stay supported."
//
// It stays supported because this IS the command: we format "@warp <mapid>" and hand
// it to the client's own chat-send. The server sees an ordinary GENERAL_CHAT and
// CommandsExecutor does the rest. No new opcode, no handler, no server change at all,
// and @warp keeps its GM gate.
//
// Resolving the click needs no geometry of our own -- the client already hit-tests the
// dots for the hover tooltip:
//
//     CUIWorldMap vtable        0x00B93120   (ctor 0x00A31D9F writes it)
//     spot hover hit-test       0x00A362E3   (called from OnMouseMove 0x00A3682F)
//     hovered spot index        CUIWorldMap+0x5C0   (-1 = none; reset at 0x00A3594C)
//     spot array                CUIWorldMap+0x5D0   (0x44-byte records; stride proven
//                                                    by 0x00A35943 `add [ebp-0x28],0x44`)
//     spot.mapNo (ZArray<int>)  spot+0x2C
//
// spot+0x2C is verified by construction, not assumed: 0x00A35ECE takes `lea edi,[esi+0x2c]`,
// sizes that array to the wz child count, then 0x00A35F49 calls ZArray::operator[]
// (0x004D248E) and stores each int with `mov [eax],ebx`. 0x00A35FB9 then feeds element
// [0] to the map-name lookup 0x00535428 -- so ids[0] is the spot's canonical map id.
//
//     mapId = (*(int**)((BYTE*)spots + idx*0x44 + 0x2C))[0]
//
// Two writes:
//
// 1. 0x00A3649E `74 06` -> `90 90`. In the hover hit-test, the write of the hovered
//    index to +0x5C0 is gated on the spot having a preview-image string at spot+0x28.
//    The repo's WorldMap*.img carries no such per-spot key, so +0x5C0 would otherwise
//    never be set for towns and every click would read -1. NOPping is safe in both
//    worlds: the preview-draw path at 0x00A343A0 independently guards on the string,
//    and 0x00416179 (AddRef) is NULL-safe.
//
// 2. 0x00A36C8C, the rel32 of `call 0xA3688A`, repointed at our thunk. This is an
//    operand edit on an existing call -- the same class of change as PetLeashX, not a
//    Cave(). Reached only from the WM_LBUTTONUP arm of the window proc:
//
//     00A36C61  mov eax,[esp+4]
//     00A36C65  sub eax,0x202          ; WM_LBUTTONUP
//     00A36C6A  je 0xA36C88
//     00A36C88  add ecx,-4             ; ecx := CUIWorldMap* (msg iface is obj+4)
//     00A36C8B  call 0xA3688A          ; <-- repointed
//     00A36C90  ret 0x10
//
//    So the thunk gets `this` in ecx. CUIWorldMap::OnLButtonUp ends `c3 ret` (verified
//    at 0x00A36A7C) and takes no stack args, so tail-jumping to it is exact.
//
// Clicking a town does nothing in vanilla; the only existing click action is MapLink
// navigation, which OnLButtonUp drives off a DIFFERENT index (+0x5BC). We run it
// whenever we did not warp, so region links keep working byte-for-byte. When we DID
// warp we return instead, so a click cannot both warp and navigate.
//
// ponytail: chat injection rather than building the packet ourselves. SendChatMsg is
// one call and never reads its own `this` (0x005382D7 uses [ebp+8] only), whereas the
// packet route needs COutPacket's ctor/dtor, EncodeStr taking a ZXString BY VALUE, and
// sizeof(COutPacket) -- which nothing verifies. Same result, three times the surface.
// Known limit of this choice: the client's own mute flags ([0xC40C60] and
// [0xC40C68]+0x2094) make the send a silent no-op, same as typing the command.
typedef void (__thiscall* SendChatMsg_t)(void* pUser, void* pZXStr, int nOnlyBalloon);
typedef void (__thiscall* ZXDtor_t)(void* pZXStr);

static const DWORD kWmGateAddr = 0x00A3649A;              // guard; we NOP the je at +4
static const BYTE kWmGateGuard[6] = { 0x83, 0x7E, 0x28, 0x00, 0x74, 0x06 };
static const int kWmGateJeOff = 4;

static const DWORD kWmCallAddr = 0x00A36C88;              // guard; rel32 lives at +4
static const BYTE kWmCallGuard[11] = {
    0x83, 0xC1, 0xFC, 0xE8, 0xFA, 0xFB, 0xFF, 0xFF, 0xC2, 0x10, 0x00,
};
static const int kWmCallRelOff = 4;
static const DWORD kWmOnLButtonUp = 0x00A3688A;

static bool g_wmWarp = true, g_wmDone = false, g_wmMismatch = false;
static DWORD g_wmOrig = kWmOnLButtonUp;   // jmp target, kept in memory for the thunk
static BYTE g_wmWarped = 0;               // thunk<->asm handshake; UI thread only

// Returns nonzero if we sent a warp, in which case the click is consumed.
static BYTE __fastcall WorldMapTryWarp(BYTE* pWorldMap) {
    if (!pWorldMap) return 0;

    int idx = *(int*)(pWorldMap + 0x5C0);
    if (idx < 0) return 0;                       // nothing hovered

    BYTE* spots = *(BYTE**)(pWorldMap + 0x5D0);
    if (!spots) return 0;

    int* ids = *(int**)(spots + idx * 0x44 + 0x2C);
    if (!ids) return 0;                          // spot with an empty mapNo

    int mapId = ids[0];
    if (mapId <= 0 || mapId == 999999999) return 0;

    char cmd[32];
    wsprintfA(cmd, "@warp %d", mapId);

    void* zs = nullptr;                          // ZXString<char> is one char*
    g_Assign(&zs, cmd, -1);                      // -1 => strlen
    ((SendChatMsg_t)0x005382D7)(nullptr, &zs, 0);// never reads its own this
    ((ZXDtor_t)0x0040265E)(&zs);
    return 1;
}

static __declspec(naked) void WorldMapClickThunk() {
    __asm {
        push ecx                  // keep CUIWorldMap* across the call
        pushad
        pushfd
        mov  ecx, [esp + 0x24]    // pushfd(4) + pushad(0x20) -> the pushed ecx
        call WorldMapTryWarp      // __fastcall, arg already in ecx
        mov  g_wmWarped, al       // stash before popad clobbers eax
        popfd
        popad
        pop  ecx
        cmp  byte ptr g_wmWarped, 0
        jne  consumed
        jmp  dword ptr [g_wmOrig] // no warp: run MapLink navigation as usual
    consumed:
        ret                       // OnLButtonUp takes no stack args, so a bare ret matches
    }
}

static void ApplyWorldMapWarp() {
    if (!g_wmWarp) return;
    if (!GuardOk(kWmGateAddr, kWmGateGuard, sizeof(kWmGateGuard)) ||
        !GuardOk(kWmCallAddr, kWmCallGuard, sizeof(kWmCallGuard))) {
        g_wmMismatch = true; return;
    }

    // Order matters: repoint the call LAST, so the thunk can never run against a
    // half-patched hover gate.
    if (!PokeFill(kWmGateAddr + kWmGateJeOff, 0x90, 2)) return;

    DWORD site = kWmCallAddr + kWmCallRelOff;
    int rel = (int)((DWORD_PTR)&WorldMapClickThunk - (site + 4));
    g_wmDone = Poke(site, &rel, 4);
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
        // The damage cap is written twice by design: the client keeps an int copy for
        // the stat window and a double for the damage maths, and upstream sets both.
        case K_DMGCAP32: { int    v = (int)g_dmgCap; ok = Poke(p.addr, &v, 4); break; }
        case K_DMGCAPD:  { double v = g_dmgCap;      ok = Poke(p.addr, &v, 8); break; }
        case K_SPDCAP:   { int    v = g_spdCap;      ok = Poke(p.addr, &v, 4); break; }
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
    char msg[1024];   // 9 report lines and growing; 512 overflowed at PetSweepHeight
    // The archive line names WHICH of the three ways it can be off actually happened, so
    // one launch distinguishes "owner has no .wz" from "guard bytes differ" from
    // "mounted but the hook refused" -- without a second round trip.
    const char* arch = !g_useArchive   ? "off (no EzorsiaV2_UI.wz)"
                     : g_mountMismatch ? "FAILED (mount guard bytes differ)"
                     : !g_mounted      ? "FAILED (mount write refused)"
                     : g_hooked        ? "on"
                                       : "FAILED (mounted, GetString guard differs)";
    const char* whack = !g_noWhack        ? "off"
                      : g_noWhackMismatch ? "FAILED (guard bytes differ)"
                      : g_noWhackDone     ? "ON"
                                          : "FAILED (write refused)";
    const char* tubi = !g_tubi          ? "off"
                     : g_tubiMismatch   ? "FAILED (guard bytes differ)"
                     : g_tubiDone       ? "ON"
                                        : "FAILED (write refused)";
    const char* ladder = g_ladderMul == 1.0 ? "off"
                       : g_ladderMismatch   ? "FAILED (guard bytes differ)"
                       : g_ladderDone       ? "ON"
                                            : "FAILED (write refused)";
    const char* petloot = g_petLootMul == 1.0 ? "off"
                        : g_petLootMismatch    ? "FAILED (guard bytes differ)"
                        : g_petLootDone        ? "ON"
                                               : "FAILED (write refused)";
    const char* petmove = !g_petSweep       ? "off"
                        : g_petSweepMismatch ? "FAILED (guard bytes differ)"
                        : g_petSweepDone     ? "ON"
                                             : "FAILED (write refused)";
    const char* petrange = !g_petSweepExtra       ? "off"
                         : g_petSweepRangeMismatch ? "FAILED (guard bytes differ)"
                         : g_petSweepRangeDone     ? "ON"
                                                   : "FAILED (write refused)";
    const char* petdelay = g_petLootDelay == 3000 ? "off"
                         : g_petDelayMismatch      ? "FAILED (guard bytes differ)"
                         : g_petDelayDone          ? "ON"
                                                   : "FAILED (write refused)";
    const char* petheight = !g_petSweepH        ? "off"
                          : g_petSweepHMismatch  ? "FAILED (guard bytes differ)"
                          : g_petSweepHDone      ? "ON"
                                                 : "FAILED (write refused)";
    const char* petleash = g_petLeashX == 300 ? "off"
                         : g_petLeashMismatch  ? "FAILED (guard bytes differ)"
                         : g_petLeashDone      ? "ON"
                                               : "FAILED (write refused)";
    const char* wmap = !g_wmWarp     ? "off"
                     : g_wmMismatch  ? "FAILED (guard bytes differ)"
                     : g_wmDone      ? "ON"
                                     : "FAILED (write refused)";
    char clamp[64] = "";
    if (g_msgClamped) wsprintfA(clamp, " (clamped from %d)", g_msgClamped);
    wsprintfA(msg, "hd-res %dx%d: applied %d, skipped %d, byte-mismatch %d of %d"
                   "\narchive: %s | hooks %d, mismatch %d"
                   "\nMsgAmount %d%s | dmgCap %d | spdCap %d | useTubi %s | NoWhack %s"
                   "\nLadderSpeed %s (%d%%)"
                   "\nPetLootRange %s (%dx%d px box)"
                   "\nPetLootWhileMoving %s"
                   "\nPetSweepRange %s (ownerX +/- %d px)"
                   "\nPetLootDelay %s (%d ms)"
                   "\nPetSweepHeight %s (petY -%d .. +%d)"
                   "\nPetLeashX %s (ownerX +/- %d px)"
                   "\nWorldMapWarp %s"
                   "%s\n",
              g_w, g_h, g_applied, g_skipped, g_mismatch,
              (int)(sizeof(kHdPatches) / sizeof(kHdPatches[0])),
              arch, g_hooked, g_hookMismatch,
              g_msg, clamp, (int)g_dmgCap, g_spdCap, tubi, whack,
              ladder, (int)(g_ladderMul * 100),
              petloot, PetLootEdge(2) - PetLootEdge(0), PetLootEdge(3) - PetLootEdge(1),
              petmove,
              petrange, kPetSweepBaseHalf + g_petSweepExtra,
              petdelay, g_petLootDelay,
              petheight, g_petSweepH > 128 ? 128 : g_petSweepH,
                         g_petSweepH > 127 ? 127 : g_petSweepH,
              petleash, g_petLeashX,
              wmap,
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

    // MsgAmount is a real variable now, not a baked 26: gen_loader fits a coefficient
    // for it, so rows P220 and P227 track whatever is set here. CLAMPED to [6,255] --
    // config.ini's own comment says 6 is vanilla and "no more than 255 or you buffer
    // overflow", and reproducing an admitted overflow faithfully is not a feature.
    g_msg = GetPrivateProfileIntA("general", "MsgAmount", 26, cfg);
    if (g_msg > 255) { g_msgClamped = g_msg; g_msg = 255; }
    if (g_msg < 6)   { g_msgClamped = g_msg; g_msg = 6;   }

    // [optional] gameplay caps. These land on v84 literals that ALREADY hold Ezorsia's
    // defaults, so an untouched config writes the bytes that are there and changes
    // nothing; they exist so the knobs work at all.
    char buf[64]{};
    GetPrivateProfileStringA("optional", "setDamageCap", "199999.0", buf, sizeof(buf), cfg);
    g_dmgCap = atof(buf);
    if (g_dmgCap <= 0) g_dmgCap = 199999.0;
    g_spdCap = GetPrivateProfileIntA("optional", "speedMovementCap", 140, cfg);

    // useTubi: upstream's default is false, and it stays false. See ApplyTubi.
    GetPrivateProfileStringA("optional", "useTubi", "false", buf, sizeof(buf), cfg);
    g_tubi = (buf[0] == 't' || buf[0] == 'T' || buf[0] == '1');

    // NoWhack: default TRUE. Set NoWhack=false in config.ini for vanilla v84 behaviour.
    GetPrivateProfileStringA("optional", "NoWhack", "true", buf, sizeof(buf), cfg);
    g_noWhack = (buf[0] == 't' || buf[0] == 'T' || buf[0] == '1');

    // LadderSpeed: multiplier on the 3 px/tick climb step. 1.0 = vanilla (patch skipped).
    GetPrivateProfileStringA("optional", "LadderSpeed", "1.5", buf, sizeof(buf), cfg);
    g_ladderMul = atof(buf);
    if (g_ladderMul <= 0.0 || g_ladderMul > 10.0) g_ladderMul = 1.5;

    // PetLootRange: multiplier on the pet pickup box. 1.0 = vanilla (patch skipped).
    // Each edge clamps to its own imm8 limit, so 12.7 pins all four (255x255 px box).
    GetPrivateProfileStringA("optional", "PetLootRange", "12.7", buf, sizeof(buf), cfg);
    g_petLootMul = atof(buf);
    if (g_petLootMul < 1.0 || g_petLootMul > 12.7) g_petLootMul = 12.7;

    // PetLootWhileMoving: let the pet keep sweeping to drops while the owner walks.
    GetPrivateProfileStringA("optional", "PetLootWhileMoving", "true", buf, sizeof(buf), cfg);
    g_petSweep = (buf[0] == 't' || buf[0] == 'T' || buf[0] == '1');

    // PetSweepRange: px added to the 125 half-width the pet TARGETS drops within.
    // 0 = vanilla (patch skipped). 175 puts the window on the teleport leash, 300.
    g_petSweepExtra = GetPrivateProfileIntA("optional", "PetSweepRange", 175, cfg);
    if (g_petSweepExtra < 0 || g_petSweepExtra > 2000) g_petSweepExtra = 175;

    // PetLootDelay: ms a drop must sit before a pet may target or grab it.
    g_petLootDelay = GetPrivateProfileIntA("optional", "PetLootDelay", 1000, cfg);
    if (g_petLootDelay < 0 || g_petLootDelay > 10000) g_petLootDelay = 1000;

    // PetSweepHeight: vertical half-window the pet targets drops within, px.
    // 128 pins both imm8 ends (petY-128 .. petY+127). 0 = vanilla, patch skipped.
    g_petSweepH = GetPrivateProfileIntA("optional", "PetSweepHeight", 128, cfg);
    if (g_petSweepH < 0 || g_petSweepH > 128) g_petSweepH = 128;

    // PetLeashX: X half-width the owner may stray before the pet teleports to him.
    // Keep it above PetSweepRange+125 or a max-range fetch gets cut short.
    g_petLeashX = GetPrivateProfileIntA("optional", "PetLeashX", 350, cfg);
    if (g_petLeashX < 100 || g_petLeashX > 5000) g_petLeashX = 350;

    // WorldMapWarp: click a town dot on the World Map to warp there, via @warp.
    GetPrivateProfileStringA("optional", "WorldMapWarp", "true", buf, sizeof(buf), cfg);
    g_wmWarp = (buf[0] == 't' || buf[0] == 'T' || buf[0] == '1');

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
    ApplyTubi();
    ApplyNoWhack();
    ApplyLadderSpeed();
    ApplyPetLootRange();
    ApplyPetLootWhileMoving();
    ApplyPetSweepRange();
    ApplyPetLootDelay();
    ApplyPetSweepHeight();
    ApplyPetLeashX();
    ApplyWorldMapWarp();

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
