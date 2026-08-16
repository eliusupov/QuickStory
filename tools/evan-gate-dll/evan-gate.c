/* Ticket 01b fallback - NOP the Evan job gate at VA 0x00761714 inside MapleStory.exe.
 *
 * Loaded by dinput8.dll via config.ini `use_custom_dll_1`. NOT built by default: the primary
 * route is tools\patch-evan-gate.ps1, which needs no toolchain. Build this only if external
 * OpenProcess is blocked (Themida) or the client runs elevated and PowerShell cannot attach.
 *
 * Build (32-bit, x86 Native Tools Command Prompt):
 *     cl /nologo /MT /O2 /LD /Fe:EVANGATE.dll evan-gate.c kernel32.lib
 * or mingw-w64 i686:
 *     i686-w64-mingw32-gcc -shared -O2 -s -o EVANGATE.dll evan-gate.c
 *
 * NAME MATTERS. dinput8.dll at 0x10008BAF does strcmp(value, "CUSTOM.dll") and SKIPS loading
 * when they are equal - "CUSTOM.dll" is the sentinel that means "off". The file must be called
 * something else and config.ini must name it. Get the name wrong or the bitness wrong and
 * dinput8 pops "Failed to find the first custom dll file" and calls ExitProcess - a clean,
 * non-destructive failure.
 *
 * kernel32 only, no CRT calls: the DLL loads while Themida is still settling and CRT init
 * ordering is not worth the risk.
 */

#define WIN32_LEAN_AND_MEAN
#include <windows.h>

#define GATE_ADDR  0x00761714u
#define GATE_LEN   21
#define POLL_MS    250
#define POLL_MAX   240          /* 240 * 250ms = 60s */

static const unsigned char kPattern[GATE_LEN] = {
    0x83,0xF8,0x16, 0x0F,0x84,0xD7,0x00,0x00,0x00,
    0x81,0xFE,0xD1,0x07,0x00,0x00, 0x0F,0x84,0xCB,0x00,0x00,0x00
};
static const unsigned char kNops[GATE_LEN] = {
    0x90,0x90,0x90,0x90,0x90,0x90,0x90,0x90,0x90,0x90,0x90,
    0x90,0x90,0x90,0x90,0x90,0x90,0x90,0x90,0x90,0x90
};

static HMODULE g_self;
static char    g_log[MAX_PATH + 32];   /* headroom: filename is swapped for a longer one */

/* volatile on both sides: the read-back after the write must be a real load. Without it /O2
 * is entitled to fold `eq(p, kNops)` to true right after storing 0x90s, and the whole
 * Themida-re-encrypt check silently becomes a no-op. */
static int eq(volatile const unsigned char *a, volatile const unsigned char *b) {
    int i; for (i = 0; i < GATE_LEN; i++) if (a[i] != b[i]) return 0; return 1;
}

/* append msg (and optionally a hex dump of GATE_LEN bytes) to the log next to this DLL */
static void logline(const char *msg, volatile const unsigned char *bytes) {
    static const char hexd[] = "0123456789ABCDEF";
    char buf[512]; int n = 0, i;
    HANDLE h;
    SYSTEMTIME st;

    GetLocalTime(&st);
    buf[n++] = '[';
    buf[n++] = (char)('0' + st.wHour / 10);   buf[n++] = (char)('0' + st.wHour % 10);   buf[n++] = ':';
    buf[n++] = (char)('0' + st.wMinute / 10); buf[n++] = (char)('0' + st.wMinute % 10); buf[n++] = ':';
    buf[n++] = (char)('0' + st.wSecond / 10); buf[n++] = (char)('0' + st.wSecond % 10);
    buf[n++] = ']'; buf[n++] = ' ';
    for (i = 0; msg[i] && n < 400; i++) buf[n++] = msg[i];
    if (bytes) {
        buf[n++] = ' ';
        for (i = 0; i < GATE_LEN; i++) {
            buf[n++] = hexd[bytes[i] >> 4];
            buf[n++] = hexd[bytes[i] & 0xF];
            buf[n++] = ' ';
        }
    }
    buf[n++] = '\r'; buf[n++] = '\n';

    h = CreateFileA(g_log, FILE_APPEND_DATA, FILE_SHARE_READ | FILE_SHARE_WRITE, NULL,
                    OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (h != INVALID_HANDLE_VALUE) {
        DWORD w;
        SetFilePointer(h, 0, NULL, FILE_END);
        WriteFile(h, buf, (DWORD)n, &w, NULL);
        CloseHandle(h);
    }
}

/* is the whole range committed and readable? avoids faulting on a page Themida has not
 * decompressed yet, which would take the client down with us. */
static int readable(void) {
    MEMORY_BASIC_INFORMATION mbi;
    if (!VirtualQuery((LPCVOID)GATE_ADDR, &mbi, sizeof mbi)) return 0;
    if (mbi.State != MEM_COMMIT) return 0;
    if (mbi.Protect & (PAGE_NOACCESS | PAGE_GUARD)) return 0;
    return (GATE_ADDR + GATE_LEN) <= ((UINT_PTR)mbi.BaseAddress + mbi.RegionSize);
}

static DWORD WINAPI worker(LPVOID unused) {
    volatile unsigned char *p = (volatile unsigned char *)GATE_ADDR;
    unsigned char last[GATE_LEN];
    int tries, have_last = 0;
    DWORD old = 0, tmp = 0;

    (void)unused;
    logline("start, target VA 0x00761714, 21 bytes", NULL);

    for (tries = 0; tries < POLL_MAX; tries++) {
        if (!readable()) { Sleep(POLL_MS); continue; }

        if (!have_last || !eq(p, last)) {
            int i; for (i = 0; i < GATE_LEN; i++) last[i] = p[i];
            have_last = 1;
            logline("read:", last);
        }

        if (eq(p, kNops)) { logline("RESULT: already 21x 90 - gate is patched.", NULL); return 0; }

        /* GUARD: never write over bytes we do not recognise. Wrong build, wrong address, or
         * Themida has not decrypted here yet - all three mean "do not touch". */
        if (!eq(p, kPattern)) { Sleep(POLL_MS); continue; }

        logline("GUARD PASS: expected gate pattern found.", NULL);

        /* section is already RWX (0xE0000040); call this anyway, cheap insurance */
        if (!VirtualProtect((LPVOID)GATE_ADDR, GATE_LEN, PAGE_EXECUTE_READWRITE, &old))
            logline("VirtualProtect failed, writing anyway", NULL);

        { int i; for (i = 0; i < GATE_LEN; i++) p[i] = 0x90; }

        if (old) VirtualProtect((LPVOID)GATE_ADDR, GATE_LEN, old, &tmp);
        FlushInstructionCache(GetCurrentProcess(), (LPCVOID)GATE_ADDR, GATE_LEN);

        if (eq(p, kNops)) { logline("RESULT: PATCHED and verified.", NULL); return 0; }

        /* Themida re-verified or re-encrypted the region. Keep trying. */
        logline("verify FAILED, read back:", p);
        have_last = 0;
        Sleep(POLL_MS);
    }

    logline("RESULT: gave up after 60s. Last bytes seen:", have_last ? last : NULL);
    return 0;
}

BOOL WINAPI DllMain(HINSTANCE hinst, DWORD reason, LPVOID reserved) {
    (void)reserved;
    if (reason == DLL_PROCESS_ATTACH) {
        char *slash = NULL, *c;
        DisableThreadLibraryCalls(hinst);
        g_self = (HMODULE)hinst;
        GetModuleFileNameA(g_self, g_log, MAX_PATH);
        for (c = g_log; *c; c++) if (*c == '\\') slash = c;
        if (slash) lstrcpyA(slash + 1, "evan-gate-dll.log"); else lstrcpyA(g_log, "evan-gate-dll.log");
        /* all real work off the loader lock */
        CloseHandle(CreateThread(NULL, 0, worker, NULL, 0, NULL));
    }
    return TRUE;
}
