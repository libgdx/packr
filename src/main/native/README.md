# packr-native

## About

*packr-native* is a drop-in replacement to the native executables shipped with [packr][packr]. Essentially it is 'just' a rewrite, plus a bunch of additional features.

## What's different?

Some mentionable changes to *packr*:

- More options and a different strategy to setup classpaths:
  - Instead of passing ```-Djava.class.path=...``` to the JVM to be created, which appears to be unreliable on some systems, it uses JNI functions to register classpaths using ```java.net.URLClassLoader```.
  - Registering multiple classpaths is supported. This replaces the ```"jar": "myapp.jar"``` option.

    ```json
    {
        "classPath": [
          "myapp.jar",
          "someother.jar"
        ]
    }
    ```

  - For debugging, classpaths can be imported from Gradle configuration files, which can be found at ```{YourProject}/desktop/build/tmp/compileJava/java-compiler-args.txt``` for *libGDX* projects.

    The following example is a valid classpath to run your *libGDX* project with *packr-native*, the working directory set to ```./core/assets```, and without building a distribution JAR first:

    ```json
    {
        "classPath": [
            "../../desktop/build/classes/main",
            "../../desktop/build/tmp/compileJava/java-compiler-args.txt"
        ]
    }
    ```

  - As a result of the classpath changes, ```mainClass``` must be a qualified Java class name now (just replace ```/``` with ```.```):

    ```json
    {
        "mainClass": "com.my.app.MainClass"
    }
    ```

- A configuration option to create the JVM with ```JNI_VERSION_1_8``` (default is ```JNI_VERSION_1_6```).

    ```json
    {
        "jniVersion": 8
    }
    ```

- In addition to forwarding command line arguments to the Java application, there are some arguments available for the native executable as well.
  - The format is ```{executable} [options] [-- [java-arguments]]```. Everything after ```--``` is forwarded as argument to the Java application.
  - *-h, --help* to show the command line options available.
  - *--cwd={directory}* to change the working directory.
  - *--config={your-config.json}* to change the configuration file to load.
  - *-v, --verbose* to output even more information for troubleshooting.
  - *--console* to spawn & attach a terminal window to make log/error messages actually visible [Windows only].
  - *--version* to print version information.

Some changes on the technical level you may or may not like (though I won't care):

- Uses the [sajson][sajson] library instead of picojson to parse JSON.
- Uses the [dropt][dropt] library to parse command line options.
- Uses [fips][fips] instead of premake as its build tool, which is more flexible by supporting more compilers and build configurations out of the box.
- All dependencies, including JNI header files, are kept in external repositories.

## Configuration

This is a minimal example close to the original packr:

```json
{
    "classPath": [
        "myapp.jar"
    ],
    "mainClass": "com.my.app.MainClass",
    "vmArgs": [
        "-Xmx512m"
    ]
}
```

[dropt]: https://github.com/code-disaster/dropt
[fips]: http://floooh.github.io/fips/index.html
[packr]: https://github.com/libgdx/packr
[sajson]: https://github.com/chadaustin/sajson
