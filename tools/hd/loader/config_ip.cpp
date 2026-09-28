#include <windows.h>
#include <stdio.h>
#include <string.h>

// ponytail: depends on ijl's filename order; if that changes, move this sync into the proxy.
BOOL WINAPI DllMain(HINSTANCE dll, DWORD reason, LPVOID) {
    if (reason != DLL_PROCESS_ATTACH) return TRUE;
    DisableThreadLibraryCalls(dll);

    char path[MAX_PATH]{};
    DWORD n = GetModuleFileNameA(NULL, path, MAX_PATH);
    if (!n || n >= MAX_PATH) return TRUE;
    char* slash = strrchr(path, '\\');
    if (!slash) return TRUE;
    slash[1] = 0;

    char config[MAX_PATH]{}, redirect[MAX_PATH]{}, ip[64]{}, extra;
    wsprintfA(config, "%sconfig.ini", path);
    wsprintfA(redirect, "%sedits\\redirect.ini", path);
    if (GetFileAttributesA(redirect) == INVALID_FILE_ATTRIBUTES) return TRUE;
    n = GetPrivateProfileStringA("general", "ServerIP_Address", "", ip, sizeof(ip), config);
    if (!n || n >= sizeof(ip) - 1) return TRUE;

    unsigned int a, b, c, d;
    if (sscanf(ip, "%3u.%3u.%3u.%3u%c", &a, &b, &c, &d, &extra) != 4
            || a > 255 || b > 255 || c > 255 || d > 255) return TRUE;

    WritePrivateProfileStringA("Main", "RedirectIP", ip, redirect);
    return TRUE;
}
