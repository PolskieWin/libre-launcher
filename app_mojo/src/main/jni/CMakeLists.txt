
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
    VERSION ""
    SOVERSION ""
    OUTPUT_NAME "freetype"
    DEBUG_POSTFIX ""
    ARCHIVE_OUTPUT_NAME "freetype"
    LIBRARY_OUTPUT_NAME "freetype"
    ARCHIVE_OUTPUT_NAME_DEBUG "freetype"
    LIBRARY_OUTPUT_NAME_DEBUG "freetype"
)

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

target_include_directories(mojort PRIVATE .)
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
