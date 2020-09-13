# Release 3.0.0-SNAPSHOT
1. Refactored code to fit better into libGdx/packr parent repository.
1. Fixed an issue where extracting an archive with duplicate entries would fail.
1. The packr-all Jar is available from GitHub packages <https://github.com/libgdx/packr/packages>.
1. The output directory specified by `--output` must be an empty directory, or a path that does not exist.
   * Packr will no longer delete the output directory and then populate it.
# Release 2.7.0
1. Fixed a Gradle script error where it was bundling the release builds with debug info on Linux and macOS.
   * For Linux this reduces the executable size from ~722K to ~95K.
1. Compile with `-no-pie` on Linux to work around a Nautilus bug.
   * <https://stackoverflow.com/questions/41398444/gcc-creates-mime-type-application-x-sharedlib-instead-of-application-x-applicati>
   * <https://stackoverflow.com/questions/34519521/why-does-gcc-create-a-shared-object-instead-of-an-executable-binary-according-to?noredirect=1&lq=1>
1. Added compile flags `/Os`, `/Gw`, `/Gy` on Windows.
   * Combined with the new linker flags, this reduced the executable size.
1. Added `/opt:icf`, `/opt:ref` linker flags on Windows.
   * Combined with the new compiler flags, this reduced the executable size.
1. Updated Gradle wrapper to version 6.5.1.
# Release 2.6.4
1. Fixed an issue with uncaught exception handlers not being called for the main thread.
   * dispatchUncaughtException is called on the main thread if an exception is detected after calling the main method.
# Release 2.6.3
1. Support macOS versions down to 10.10 Yosemite
   * Added compiler flag `-mmacosx-version-min=10.10`
# Release 2.6.2
1. Resolves an issue for newer JVMs that rely on vcruntime140.dll (The Visual C++ 2017 Redistributable).
   * If loading the jvm.dll fails on Windows, then PackrLauncher searches for a vcruntime*.dll file in "jre/bin" and loads that library and attempts to load the jvm.dll again. This resolves an issue where the jvm.dll can't be loaded on Windows systems that don't have the Visual C++ 2017 Redistributable installed. 
# Release 2.6.0
1. Added support for unicode directories on Windows.
   * This resolves issues where PackrLauncher is running from a directory with international characters in it.
2. Fixed `--console` on Windows.
   * When running from a Windows Explorer shortcut, a new console is popped up and if `--verbose` was also specified then all debug output shows up in the console window.
3. If the PackrLauncher parent process has a console, PackrLauncher attaches to it.
   * This allows debug output when PackrLauncher is launched from a command prompt window, without the need for passing `--console`.
   
# Release 2.5.0
1. Added `useZgcIfSupportedOs` flag making it easier to use the Z garbage collector when bundling Java 14+.
   * The launcher executable will detect if the running operating system supports the Z garbage collector and use it.
2. Added [Google Test](https://github.com/google/googletest) C++ test framework to the [PackrLauncher code](PackrLauncher/src/test/cpp).
3. Updated Gradle wrapper to 6.4.1.
4. Updated to C++14 as the minimum supported C++ version.

# Release 2.4.2
1. Added support for Java 11 and 14
   * Including jlink created JREs
   * Note: Packr cannot take a module and load it into the classpath of the created JVM, a Jar is still needed. It should be possible to create a jlink JRE from your custom module and have that work with packr but it's untested.

# Release 2.3.0
1. Converted packr to use Gradle
1. Include DrOpt source for easier building
1. Include sajson.h for easier building
   * <https://github.com/chadaustin/sajson/tree/791799ad90f7179f132ea2f53b90ef98f1d399a2>
   * From inspecting the old fips config at <https://github.com/code-disaster/fips-sajson>
1. Support tar.gz files
1. Support macOS signing and notarization of the executable
1. Load the msvcr*.dll that the JRE ships with instead of always trying to load msvcr100.dll
1. Removed linux32 platform
   * Linux x86 is no longer built because it's impossible to find a survey that shows anyone running x86 Linux.
1. Remove macOS x86 (32-bit) support
   * macOS x86 is no longer built because it requires and older version of Xcode and Apple makes it difficult to install on newer versions of macOS
1. Remove windows32 platform
   * Windows x86 is no longer built because the Adopt OpenJDK 8u242 and 8u252 have crash failures.

# Release 2.1-SNAPSHOT
- Compiles with Java 8 now. It's 2018, folks!
- Refactored I/O to use NIO and try-with-resources where appropriate. Removed dependency on Apache commons I/O.
- Print usage (--help) if no command line arguments are given.
- Added more validation checks to configuration parameters.
- Fixed crash when classpath is a directory. (#90)
- Added "cachejre" option to cache results of JRE extract & minimize steps.
- Added "removelibs" option to specify JAR files which are subject for removal of platform libraries. If this parameter isn't used, it defaults to a copy of "classpath", which is the old behaviour.
- Added NvOptimusEnablement and AmdPowerXpressRequestHighPerformance symbols. (#114)

# Release 2.0-SNAPSHOT and before
- Please check the Git log, or search the libGDX forums.
