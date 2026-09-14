// Dedicated non-game x86 host: proxy and embedded Intel DLL load in this directory.
#include <windows.h>
#include <cstdio>
#include <cstring>

int main(int argc, char** argv) {
    if (argc != 2) return 1;
    HMODULE proxy = LoadLibraryA(argv[1]);
    if (!proxy) { fprintf(stderr, "LoadLibrary failed: %lu\n", GetLastError()); return 1; }
    const char* names[] = {"ijlGetLibVersion", "ijlInit", "ijlFree", "ijlRead",
                           "ijlWrite", "ijlErrorStr", "LoadDLLsFromDirectory"};
    for (int i = 0; i < 7; ++i) {
        auto named = GetProcAddress(proxy, names[i]);
        auto ordinal = GetProcAddress(proxy, MAKEINTRESOURCEA(i + 1));
        if (!named || named != ordinal) {
            fprintf(stderr, "FAIL: ordinal %d != %s\n", i + 1, names[i]); return 1;
        }
    }
    using InitFree = int (__stdcall*)(void*);
    using Version = const void* (__stdcall*)();
    using Error = const char* (__stdcall*)(int);
    auto init = (InitFree)GetProcAddress(proxy, MAKEINTRESOURCEA(2));
    auto release = (InitFree)GetProcAddress(proxy, MAKEINTRESOURCEA(3));
    auto version = (Version)GetProcAddress(proxy, MAKEINTRESOURCEA(1));
    auto error = (Error)GetProcAddress(proxy, MAKEINTRESOURCEA(6));
    // 0x4E68 comes from the captured client's exact screenshot stack buffer.
    BYTE properties[0x4E68]{};
    if (!version()) { fputs("FAIL: IJL version\n", stderr); return 1; }
    const char* text = error(0);
    if (!text || !*text) { fputs("FAIL: IJL error text\n", stderr); return 1; }
    for (int i = 0; i < 20; ++i) {
        memset(properties, 0, sizeof(properties));
        int initialized = init(properties);
        if (initialized) { fprintf(stderr, "FAIL: IJL init %d\n", initialized); return 1; }
        int freed = release(properties);
        if (freed) { fprintf(stderr, "FAIL: IJL free %d\n", freed); return 1; }
    }
    fputs("PASS: 7 ordinal/name bindings; version/error calls; 20 actual x86 IJL init/free cycles\n", stderr);
    FreeLibrary(proxy);
    return 0;
}
