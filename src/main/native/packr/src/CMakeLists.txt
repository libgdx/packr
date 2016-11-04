if (FIPS_WINDOWS)
    fips_begin_app(packr windowed)
else()
    fips_begin_app(packr cmdline)
endif()

fips_setup_jni()

fips_files(packr.cpp packr.h)

if (FIPS_WINDOWS)
    fips_dir(win32)
    fips_files(packr_win32.cpp packr_win32.manifest)
    if (FIPS_MSVC)
        set(CMAKE_EXE_LINKER_FLAGS_RELEASE "${CMAKE_EXE_LINKER_FLAGS_RELEASE} /INCREMENTAL:no")
    endif()
    if (FIPS_WIN32)
        set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} /LARGEADDRESSAWARE")
    endif()
endif()

if (FIPS_OSX)
    fips_dir(macos)
    fips_files(packr_macos.cpp)
endif()

if (FIPS_LINUX)
    fips_dir(linux)
    fips_files(packr_linux.cpp)
    fips_libs(dl)
endif()

fips_deps(dropt)

fips_end_app()
