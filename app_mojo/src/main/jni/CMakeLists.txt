
cmake_minimum_required(VERSION 3.22.1)

# ------ MojoExec ------

add_subdirectory(mojoexec)

# ------   GLFW   ------

set(GLFW_BUILD_WAYLAND  OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_X11      OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_ANDROID  ON  CACHE BOOL "" FORCE)
set(GLFW_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(GLFW_INSTALL        OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS   ON  CACHE BOOL "" FORCE)

add_subdirectory(glfw)

target_compile_options(glfw PRIVATE -Wno-nullability-completeness)

# ------   SDL   ------

set(SDL_SHARED       ON CACHE BOOL "" FORCE)
set(SDL_VIDEO        ON CACHE BOOL "" FORCE)
set(SDL_AUDIO		 ON CACHE BOOL "" FORCE)
set(SDL_STATIC       OFF CACHE BOOL "" FORCE)
set(SDL_CAMERA		 OFF CACHE BOOL "" FORCE)
set(SDL_TESTS		 OFF CACHE BOOL "" FORCE)
set(SDL_EXAMPLES	 OFF CACHE BOOL "" FORCE)
set(SDL3_MAINPROJECT OFF CACHE BOOL "" FORCE)
add_subdirectory(sdl)
target_link_libraries(SDL3-shared PRIVATE mojoexec)

# ------ FreeType ------

set(DISABLE_FORCE_DEBUG_POSTFIX ON CACHE BOOL "" FORCE)
set(CMAKE_DEBUG_POSTFIX "" CACHE STRING "" FORCE)
set(FT_DISABLE_ZLIB     ON CACHE BOOL "" FORCE)
set(FT_DISABLE_BZIP2    ON CACHE BOOL "" FORCE)
set(FT_DISABLE_PNG      ON CACHE BOOL "" FORCE)
set(FT_DISABLE_HARFBUZZ ON CACHE BOOL "" FORCE)
set(FT_DISABLE_BROTLI   ON CACHE BOOL "" FORCE)

if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/freetype/CMakeLists.txt")
    add_subdirectory(freetype)
elseif(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/../../../../freetype/CMakeLists.txt")
    add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/../../../../freetype" freetype_build)
else()
    message(FATAL_ERROR "FreeType source directory not found! Ensure git submodules are checked out.")
endif()

set_target_properties(freetype PROPERTIES
    NO_SONAME ON
    OUTPUT_NAME "freetype"
    DEBUG_POSTFIX ""
    ARCHIVE_OUTPUT_NAME "freetype"
    LIBRARY_OUTPUT_NAME "freetype"
    ARCHIVE_OUTPUT_NAME_DEBUG "freetype"
    LIBRARY_OUTPUT_NAME_DEBUG "freetype"
)
target_link_options(freetype PRIVATE "-Wl,-soname,libfreetype.so")

# ----------------------

project("MojoLauncher")

add_library(mojort SHARED
        jvm_hooks/emui_iterator_fix_hook.c
        jvm_hooks/java_exec_hooks.c
        jvm_hooks/lwjgl_dlopen_hook.c
        jre_launcher/jre_launcher.c
        jre_launcher/native_library_hook.c
        jre_launcher/elf_hinter.c
        jre_launcher/abort_wait.c
        tinyfd/tinyfd_bridge.c
        nfd/nfd_bridge.c
        utils.c
        stdio_is.c
        minibridge.c
        affinity.c
        telemetry_afk.c
        touch_bridge.c
        preboot_scanner.c
        libremobile/libremobile.cpp
)

set_source_files_properties(libremobile/libremobile.cpp PROPERTIES COMPILE_FLAGS "-Oz -flto -ffunction-sections -fdata-sections -fvisibility=hidden -fvisibility-inlines-hidden -fno-rtti -fno-exceptions")

target_include_directories(mojort PRIVATE . mojoexec/include mojoexec)
target_compile_options(mojort PRIVATE -fdata-sections -ffunction-sections)
target_link_options(mojort PRIVATE -Wl,--gc-sections -Wl,--strip-all)
target_link_libraries(mojort PRIVATE log mojoexec z freetype dl android)

add_library(mojoawt SHARED
		awt/awt.c
		awt/awt_window.c
		awt/awt_util.c
		awt/awt_input.c
		awt/awt_clipboard.c
        awt/awt_mapper.c
)

target_link_libraries(mojoawt PRIVATE android log)

add_library(awt_headless SHARED 
	awt_xawt/dummy.c
)

add_library(awt_xawt SHARED
        awt_xawt/xawt_fake.c
)
target_link_libraries(awt_xawt PRIVATE awt_headless)

# ------ MobileGlues info getter ------

add_library(mobileglues_info_getter SHARED
        mobileglues/mobileglues_info_getter.cpp
)

find_library(android-lib android)
find_library(log-lib log)

target_compile_options(mobileglues_info_getter PRIVATE
        -Oz
        -fdata-sections
        -ffunction-sections
        -fvisibility=hidden
        -fvisibility-inlines-hidden
        -fno-rtti
        -fno-exceptions
        -flto
)

target_link_options(mobileglues_info_getter PRIVATE
        -Wl,--gc-sections
        -Wl,--strip-all
        -flto
)

target_include_directories(mobileglues_info_getter PRIVATE
        mobileglues
)

target_link_libraries(mobileglues_info_getter
        ${android-lib}
        ${log-lib}
        dl
)

# ------ LWJGL 3.4.3 / Minecraft 26.3+ Native Libraries (vma) ------

add_library(lwjgl_vma SHARED
        vma/vma_shim.c
)
set_target_properties(lwjgl_vma PROPERTIES
        OUTPUT_NAME "lwjgl_vma"
)
target_compile_options(lwjgl_vma PRIVATE -Oz -fdata-sections -ffunction-sections)
target_link_options(lwjgl_vma PRIVATE -Wl,--gc-sections -Wl,--strip-all)
target_link_libraries(lwjgl_vma PRIVATE log)

add_library(vma SHARED
        vma/vma_shim.c
)
set_target_properties(vma PROPERTIES
        OUTPUT_NAME "vma"
)
target_compile_options(vma PRIVATE -Oz -fdata-sections -ffunction-sections)
target_link_options(vma PRIVATE -Wl,--gc-sections -Wl,--strip-all)
target_link_libraries(vma PRIVATE log)

# ------ LWJGL 3.4.3 / Minecraft 26.3+ Native Libraries (shaderc) ------

if(NOT DEFINED ANDROID_NDK)
    if(DEFINED CMAKE_ANDROID_NDK)
        set(ANDROID_NDK "${CMAKE_ANDROID_NDK}")
    elseif(DEFINED ENV{ANDROID_NDK_HOME})
        set(ANDROID_NDK "$ENV{ANDROID_NDK_HOME}")
    elseif(DEFINED ENV{ANDROID_NDK})
        set(ANDROID_NDK "$ENV{ANDROID_NDK}")
    elseif(DEFINED ENV{ANDROID_NDK_ROOT})
        set(ANDROID_NDK "$ENV{ANDROID_NDK_ROOT}")
    else()
        set(ANDROID_NDK "/data/data/com.termux/files/home/termux-ndk")
    endif()
endif()

set(SHADERC_SRC_DIR "${ANDROID_NDK}/sources/third_party/shaderc")
if(EXISTS "${SHADERC_SRC_DIR}/libs/c++_static/${ANDROID_ABI}/libshaderc.a")
    set(SHADERC_STATIC_LIB "${SHADERC_SRC_DIR}/libs/c++_static/${ANDROID_ABI}/libshaderc.a")
elseif(EXISTS "${SHADERC_SRC_DIR}/obj/local/${ANDROID_ABI}/libshaderc_combined.a")
    set(SHADERC_STATIC_LIB "${SHADERC_SRC_DIR}/obj/local/${ANDROID_ABI}/libshaderc_combined.a")
else()
    set(SHADERC_STATIC_LIB "${SHADERC_SRC_DIR}/libs/c++_static/${ANDROID_ABI}/libshaderc.a")
endif()

if(NOT EXISTS "${SHADERC_STATIC_LIB}")
    include(ProcessorCount)
    ProcessorCount(NCORES)
    if(NCORES EQUAL 0)
        set(NCORES 2)
    endif()
    message(STATUS "Building shaderc static library for ${ANDROID_ABI} from ${SHADERC_SRC_DIR} with ${NCORES} threads...")
    execute_process(
        COMMAND "${ANDROID_NDK}/ndk-build"
            -C "${SHADERC_SRC_DIR}"
            NDK_PROJECT_PATH=.
            APP_BUILD_SCRIPT=Android.mk
            APP_STL=c++_static
            APP_ABI=${ANDROID_ABI}
            APP_PLATFORM=android-23
            libshaderc_combined
            -j${NCORES}
        RESULT_VARIABLE SHADERC_BUILD_RES
        COMMAND_ECHO STDOUT
    )
    if(NOT SHADERC_BUILD_RES EQUAL 0)
        message(FATAL_ERROR "Failed to build shaderc static library for ${ANDROID_ABI} (exit code: ${SHADERC_BUILD_RES})")
    endif()
endif()

add_library(shaderc SHARED
    shaderc/shaderc_export.cpp
    shaderc/shaderc_lwj343_compat.cpp
)
set_target_properties(shaderc PROPERTIES
    OUTPUT_NAME "shaderc"
    LINKER_LANGUAGE CXX
)
target_compile_definitions(shaderc PRIVATE
    SHADERC_SHAREDLIB
    SHADERC_IMPLEMENTATION
)
target_compile_options(shaderc PRIVATE
    -fvisibility=default
    -Oz
    -fdata-sections
    -ffunction-sections
)
target_include_directories(shaderc PRIVATE
    "${SHADERC_SRC_DIR}/libshaderc/include"
    "${SHADERC_SRC_DIR}/libshaderc_util/include"
)
target_link_options(shaderc PRIVATE
    -Wl,--gc-sections
    -Wl,--whole-archive "${SHADERC_STATIC_LIB}" -Wl,--no-whole-archive
    -static-libstdc++
)
target_link_libraries(shaderc PRIVATE
    log dl m
)

add_library(utxent_shim SHARED utxent_shim.c)
target_link_libraries(utxent_shim PRIVATE dl)
