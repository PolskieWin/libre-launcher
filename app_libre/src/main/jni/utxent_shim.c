#define _GNU_SOURCE
#include <dlfcn.h>
#include <string.h>

__attribute__((visibility("default"), used)) void setutxent(void) {}
__attribute__((visibility("default"), used)) void* getutxent(void) { return NULL; }
__attribute__((visibility("default"), used)) void endutxent(void) {}

__attribute__((visibility("default"), used))
void* dlsym(void* handle, const char* symbol) {
    if (symbol != NULL) {
        if (strcmp(symbol, "setutxent") == 0) return (void*)&setutxent;
        if (strcmp(symbol, "getutxent") == 0) return (void*)&getutxent;
        if (strcmp(symbol, "endutxent") == 0) return (void*)&endutxent;
    }
    typedef void* (*dlsym_fn)(void*, const char*);
    static dlsym_fn real_dlsym = NULL;
    if (!real_dlsym) {
        // Resolve real dlsym via RTLD_NEXT without calling dlsym recursively
        real_dlsym = (dlsym_fn)__builtin_return_address(0); // placeholder cleared below
        real_dlsym = NULL;
        // On Android Bionic, libdl.so exports dlsym; dlopen("libdl.so") returns its handle
        // Since our dlsym is called, look up via RTLD_NEXT using a local struct/symbol or __loader_dlsym
        extern void* __loader_dlsym(void*, const char*, const void*) __attribute__((weak));
        if (__loader_dlsym) {
            return __loader_dlsym(handle, symbol, __builtin_return_address(0));
        }
    }
    return real_dlsym ? real_dlsym(handle, symbol) : NULL;
}
