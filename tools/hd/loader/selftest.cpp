// Load-and-run check for hd-res. Proves what a compile alone does not: that DllMain
// executes, walks all 288 rows through the byte guard, and returns without faulting.
//
// It must report applied 0 / skipped 0 / mismatch <all>, because this process is not the
// v84 client -- none of the v84 addresses hold the expected bytes. That IS the result
// being checked: the guard refuses to write into an image it does not recognise. If it
// ever reports a non-zero applied count here, the guard is broken.
//
// Built at /BASE:0x20000000 so the host image cannot overlap the 0x00400000-0x00C61463
// range the patch table addresses, which would let a coincidental byte match write into
// this process. Never link it at the default 0x400000.
//
//   cl /nologo /MT /DNDEBUG selftest.cpp /link /BASE:0x20000000 /OUT:hd-selftest.exe
//   (and build the DLL beside it with /DHD_SELFTEST so Report() also writes to stderr)
#include <windows.h>
#include <cstdio>

int main(int argc, char** argv) {
    const char* dll = argc > 1 ? argv[1] : "hd-res-selftest.dll";
    HMODULE h = LoadLibraryA(dll);
    if (!h) {
        fprintf(stderr, "FAIL: LoadLibrary(%s) -> error %lu\n", dll, GetLastError());
        return 1;
    }
    fprintf(stderr, "OK: %s loaded at %p, DllMain returned\n", dll, (void*)h);
    FreeLibrary(h);
    return 0;
}
