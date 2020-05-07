# Release 2.4.0
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
