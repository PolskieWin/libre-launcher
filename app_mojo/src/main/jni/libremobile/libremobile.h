#pragma once

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

const unsigned char* libre_glGetString(unsigned int name);
void* libre_eglGetProcAddress(const char *procname);

JNIEXPORT jboolean JNICALL
Java_git_artdeell_mojo_game_renderer_impl_LibreMobileRenderSpec_nativeSetupSnapdragon(JNIEnv *env, jclass clazz);

JNIEXPORT jboolean JNICALL
Java_git_artdeell_mojo_game_renderer_impl_LibreMobileRenderSpec_nativeSetupMobile(
        JNIEnv *env,
        jclass clazz,
        jboolean isAdreno,
        jboolean isMali,
        jboolean isXclipse);

#ifdef __cplusplus
}
#endif
