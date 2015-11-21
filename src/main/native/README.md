# packr - native Java VM launcher application

## About

This document describes more technical parts of the *native code of packr*. Please refer to the main README for general information.

## Building the native code

[fips][fips], a build wrapper on top of CMake, is used to compile the native code.

### Requirements

- Python 2.7.9
- CMake 2.8.11 or better
- A suitable compiler toolchain for every platform
- A JDK installation is required to fetch the JNI headers from.

More information can be found in the fips documentation.

### Preparation

The minimal steps to get going:

```shell
# navigate to native project folder
> cd ./src/main/native/packr
# install fips
> ./fips
# fetch all dependencies
> ./fips fetch
```

### Compiling

There are a few scripts for conveniently compiling each version:

`build-windows-vs2013.bat`: compiles Windows 32 & 64 bit using Visual Studio 2013. Supposed to be run from the VS command prompt.

`build-linux.sh`: compiles Linux 32 & 64 bit using Gnu C++. Expects to be run on a 64 bit Linux host.

`build-macos.sh`: compiles a universal 32+64 bit binary using XCode.

As their last step, each of those scripts copies the resulting executable to the `./src/main/resources` folder.

Please have a look at the scripts themselves, and refer to the fips documentation to learn more, e.g. how to use different compiler toolchains.

### Dependencies

- Uses the [sajson][sajson] library to parse JSON.
- Uses the [dropt][dropt] library to parse command line options.
- All dependencies are kept as fips modules, in external GitHub repositories.
- JNI headers are located automatically via a CMake command, which usually means it's looking for a JDK version installed on the host system.

## Additional config.json options

### Classpaths

For debugging, classpaths can be imported from Gradle configuration files, which can be found at ```{YourProject}/desktop/build/tmp/compileJava/java-compiler-args.txt``` for a *libGDX* project.

The following example is a valid classpath to run your *libGDX* project with the *packr* executable, with the working directory set to ```./core/assets```, and without building a distribution JAR first:

```json
{
    "classPath": [
        "../../desktop/build/classes/main",
        "../../desktop/build/tmp/compileJava/java-compiler-args.txt"
    ]
}
```

This option is of course not meant to be used to ship consumer versions with.

### JNI version

There's a configuration option to invoke the JVM with ```JNI_VERSION_1_8``` (default is ```JNI_VERSION_1_6```). This only affects the JNI interface, and shouldn't be needed for most use cases.

```json
{
    "jniVersion": 8
}
```

## Command line interface

In addition to forwarding command line arguments to the Java application, there are some arguments available for the native executable as well.

The full format is `{executable} -c [options] [-- [java-arguments]]`. The `-c` or `--cli` option must be **the first argument** to enable the CLI. Everything after ```--``` is forwarded to the Java application.

If the first argument is **anything but `-c`**, the full command line is forwarded to Java, so the format is interpreted as `{executable} [java-arguments]`.

CLI commands available:

- *-h, --help* to show the command line options available.
- *--cwd={directory}* to change the working directory. By default, the working directory is set to the location of the executable.
- *--config={your-config.json}* to change the configuration file to load. Must be an absolute path, or a path relative to the working directory.
- *-v, --verbose* to output even more information for troubleshooting.
- *--console* to spawn & attach a terminal window to make log/error messages actually visible [Windows only]. Alternatively you can pipe output to a text file, e.g. `{executable} [options] > log.txt`.
- *--version* to print version information.

[dropt]: https://github.com/code-disaster/dropt
[fips]: http://floooh.github.io/fips/index.html
[packr]: https://github.com/libgdx/packr
[sajson]: https://github.com/chadaustin/sajson
