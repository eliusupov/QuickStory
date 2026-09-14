// Owner customization, verified from two v84 memory captures on 2026-09-14.
// 0xA9153E intersects a shot with footholds, clips destination XY, and returns 0.
// Shoot 0x98E9D4 then clears [ebp-974]; magic 0x99320B clears [ebp-CC0].
// Those locals hold CMob pointers from 0x68F578; 0x67A525 supplies hit XY.
// Bypass BEFORE the query only with a selected mob, preserving the original XY.
// Other callers (generic geometry 0x996111, Monster Magnet 0x9AC017) stay intact.
static DWORD kProjectileQuery = 0x00A9153E;
static DWORD kShootTerrainReturn = 0x0098E9D9, kMagicTerrainReturn = 0x00993210;
static bool g_projectileTerrainDone = false;

__declspec(naked) static void ShootTerrain() {
    __asm {
        cmp dword ptr [ebp-0x974], 0
        je freeShot
        add esp, 0x14
        mov eax, 1
        jmp dword ptr [kShootTerrainReturn]
    freeShot:
        call dword ptr [kProjectileQuery]
        jmp dword ptr [kShootTerrainReturn]
    }
}
__declspec(naked) static void MagicTerrain() {
    __asm {
        cmp dword ptr [ebp-0xCC0], 0
        je freeShot
        add esp, 0x14
        mov eax, 1
        jmp dword ptr [kMagicTerrainReturn]
    freeShot:
        call dword ptr [kProjectileQuery]
        jmp dword ptr [kMagicTerrainReturn]
    }
}

struct ProjectileTerrainSite { DWORD origin; const BYTE* expect; int size; void* thunk; };
static const BYTE kShootTerrainExpect[] = {
    0xE8,0x65,0x2B,0x10,0x00,0x85,0xC0,0x75,0x07,0x83,0xA5,0x8C,0xF6,0xFF,0xFF,0x00
};
static const BYTE kMagicTerrainExpect[] = {
    0xE8,0x2E,0xE3,0x0F,0x00,0x85,0xC0,0x75,0x07,0x83,0xA5,0x40,0xF3,0xFF,0xFF,0x00
};
static const ProjectileTerrainSite kProjectileTerrainSites[] = {
    {0x0098E9D4, kShootTerrainExpect, sizeof(kShootTerrainExpect), ShootTerrain},
    {0x0099320B, kMagicTerrainExpect, sizeof(kMagicTerrainExpect), MagicTerrain}
};
static void ApplyProjectileTerrain() {
    // Startup loader contract: decrypted code, before gameplay executes these calls.
    // Validate BOTH sites before writing either; other client builds keep vanilla code.
    for (const auto& site : kProjectileTerrainSites) {
        MEMORY_BASIC_INFORMATION mbi{};
        if (!VirtualQuery((void*)site.origin, &mbi, sizeof(mbi)) || mbi.State != MEM_COMMIT ||
            mbi.Protect & (PAGE_NOACCESS | PAGE_GUARD) ||
            memcmp((void*)site.origin, site.expect, site.size) != 0) return;
    }
    for (const auto& site : kProjectileTerrainSites) {
        BYTE jump[5] = {0xE9};
        *(int*)(jump + 1) = (int)((DWORD_PTR)site.thunk - site.origin - 5);
        if (!Poke(site.origin, jump, sizeof(jump))) {
            for (const auto& undo : kProjectileTerrainSites) Poke(undo.origin, undo.expect, 5);
            return;
        }
    }
    g_projectileTerrainDone = true;
}

#ifdef HD_SELFTEST
static DWORD g_testTerrainThunk, g_testTerrainEsp;
static int g_testTerrainCalls, g_testTerrainStackMismatch;
__declspec(naked) static void TestTerrainQuery() {
    __asm {
        inc dword ptr [g_testTerrainCalls]
        xor eax, eax
        ret 0x14
    }
}
__declspec(naked) static int TestTerrainFrame(int target) {
    __asm {
        push ebp
        mov ebp, esp
        sub esp, 0xCC0
        mov eax, [ebp+8]
        mov [ebp-0x974], eax
        mov [ebp-0xCC0], eax
        lea eax, continuation
        mov [kShootTerrainReturn], eax
        mov [kMagicTerrainReturn], eax
        mov [g_testTerrainEsp], esp
        push 0
        push 0
        push 0
        push 0
        push 0
        jmp dword ptr [g_testTerrainThunk]
    continuation:
        cmp esp, [g_testTerrainEsp]
        je stackOK
        mov [g_testTerrainStackMismatch], 1
    stackOK:
        mov esp, ebp
        pop ebp
        ret
    }
}
extern "C" __declspec(dllexport) int ProjectileTerrainSelfTest() {
    if (g_projectileTerrainDone) return 0; // this host must never pass the client guards
    kProjectileQuery = (DWORD_PTR)TestTerrainQuery;
    for (const auto& site : kProjectileTerrainSites) {
        g_testTerrainThunk = (DWORD_PTR)site.thunk;
        for (int target = 0; target <= 1; ++target) {
            g_testTerrainCalls = g_testTerrainStackMismatch = 0;
            int result = TestTerrainFrame(target);
            if (result != target || g_testTerrainCalls != 1-target || g_testTerrainStackMismatch) return 0;
        }
    }
    return 1;
}
#endif
