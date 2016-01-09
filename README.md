# packr

Packages your JAR, assets and a JVM for distribution on Windows, Linux and Mac OS X, adding a native executable file to make it appear like the app is a native app. Packr is most suitable for GUI applications, such as games made with [libGDX](http://libgdx.badlogicgames.com/).

#### [Download Packr](http://bit.ly/packrgdx)

## Usage

You point packr at your JAR file (containing all your code and assets), a JSON config file (specifying parameters to the JVM and the main class) and a URL or local file location to an OpenJDK build for the platform you want to build. Invoking packr from the command line may look like this:

```bash
java -jar packr.jar \
     -platform mac \
     -jdk "openjdk-1.7.0-u45-unofficial-icedtea-2.4.3-macosx-x86_64-image.zip" \
     -executable myapp \
     -classpath myapp.jar \
     -mainclass "com.my.app.MainClass" \
     -vmargs "-Xmx1G" \
     -resources src/main/resources;path/to/other/assets \
     -minimizejre "soft" \
     -outdir out
```

| Parameter | Meaning |
| --- | --- |
| platform | one of "windows32", "windows64", "linux32", "linux64", "mac" |
| jdk | ZIP file location or URL to an OpenJDK build containing a JRE. Prebuild JDKs can be found at https://github.com/alexkasko/openjdk-unofficial-builds |
| executable | name of the native executable, without extension such as ".exe" |
| icon (optional, OS X) | location of an AppBundle icon resource (.icns file) |
| classpath | file locations of the JAR files to package, separated by `;` |
| bundleidentifier (optional, OS X) | the bundle identifier of your Java application, e.g. "com.my.app" |
| mainclass | the fully qualified name of the main class, using dots to delimit package names |
| vmargs | list of arguments for the JVM, separated by `;`, e.g. "-Xmx1G" |
| outdir | output directory |
| resources (optional) | list of files and directories to be packaged next to the native executable, separated by `;`.
| minimizejre | minimize the JRE by removing directories and files as specified by the config file. Comes with two config files out of the box called "soft" and "hard". See below for details on the minimization config file. |

Alternatively, you can put all the command line arguments into a JSON file which might look like this:

```json
{
    "platform": "mac",
    "jdk": "/Users/badlogic/Downloads/openjdk-1.7.0-u45-unofficial-icedtea-2.4.3-macosx-x86_64-image.zip",
    "executable": "myapp",
    "classpath": [
        "myapp.jar"
    ],
    "mainclass": "com.my.app.MainClass",
    "vmargs": [
       "-Xmx1G"
    ],
    "resources": [
        "src/main/resources",
        "path/to/other/assets"
    ],
    "minimizejre": "soft",
    "outdir": "out-mac"
}
```

You can then invoke the tool like this:

```bash
java -jar packr.jar my-packr-config.json
```

Finally, you can use packr from within your code. Just add the JAR file to your project, either manually, or via the following Maven dependency:

```xml
<dependency>
   <groupId>com.badlogicgames.packr</groupId>
   <artifactId>packr</artifactId>
   <version>2.0</version>
</dependency>
```

To invoke packr, you need to create an instance of `Config` and pass it to `Packr.pack()`

```java
Config config = new Config();
config.platform = Platform.windows;
config.jdk = "/User/badlogic/Downloads/openjdk-for-mac.zip";
config.executable = "myapp";
config.classpath = Arrays.asList("myjar.jar");
config.mainClass = "com.my.app.MainClass";
config.vmArgs = Arrays.asList("-Xmx1G");
config.minimizeJre = new String[] { "jre/lib/rt/com/sun/corba", "jre/lib/rt/com/sun/jndi" };
config.outDir = "out-mac";

new Packr().pack(config)
```

## Minimization

A standard JRE weighs about 90mb unpacked and about 50mb packed. Packr helps you cut down on that size, thus also reducing the download size of your app.

To minimize the JRE that is bundled with your app, you have to specify a minimization configuration file via the `minimizejre` flag you supply to Packr. Such a minimization configuration contains the names of files and directories within the JRE to be removed, one per line in the file. E.g.:

```
jre/lib/rhino.jar
jre/lib/rt/com/sun/corba
```

This will remove the rhino.jar (about 1.1MB) and all the packages and classes in com.sun.corba from the rt.jar file. To specify files and packages to be removed from the JRE, simply prepend them with `jre/lib/rt/`.

Packr comes with two such configurations out of the box, [`soft`](https://github.com/libgdx/packr/blob/master/src/main/resources/minimize/soft) and [`hard`](https://github.com/libgdx/packr/blob/master/src/main/resources/minimize/hard)

Additionally, Packr will compress the rt.jar file. By default, the JRE uses zero-compression on the rt.jar file to make application startup a little faster.

## Output

When packing for Windows, the following folder structure will be generated

```
outdir/
   executable.exe
   yourjar.jar
   config.json
   jre/
```

Linux

```
outdir/
   executable
   yourjar.jar
   config.json
   jre/
```

Mac OS X

```
outdir/
   Contents/
      Info.plist
      MacOS/
         executable
         yourjar.jar
         config.json
         jre/
      Resources/
         icons.icns [if config.icon is set]
```

You can further modify the Info.plist to your liking, e.g. add icons, a bundle identifier etc. If your `outdir` has the `.app` extension it will be treated as an application bundle by Mac OS X.

## Executable command line interface

By default, the native executables forward any command line parameters to your Java application's main() function. So, with the configurations above, `./myapp -x y.z` is passed as `com.my.app.MainClass.main(new String[] {"-x", "y.z" })`.

The executables themselves expose an own interface, which has to be explicitly enabled by passing `-c` or `--cli` as the **very first** parameter. In this case, a special delimiter parameter `--` is used to separate the native CLI from parameters to be passed to Java. In this case, the example above would be equal to `./myapp -c [arguments] -- -x y.z`.

Try `./myapp -c --help` for a list of available options. They are also listed [here](https://github.com/libgdx/packr/blob/master/src/main/native/README.md#command-line-interface).

> Note: On Windows, the executable does not show any output by default. Here you can use `myapp.exe -c --console --help` to spawn a console window, making terminal output visible.

## Building

If you want to modify the Java code only, it's sufficient to invoke Maven.

```
mvn clean package
```

This will create a `packr-VERSION.jar` file in `target` which you can invoke as described in the Usage section above.

If you want to compile the native executables, please follow [these instructions](https://github.com/libgdx/packr/blob/master/src/main/native/README.md). Each of the build scripts will create executable files for the specific platform and copy them to src/main/resources.

## Limitations

  * Icons aren't set yet on Windows and Linux, you need to do that manually.
  * Minimum platform requirement on MacOS is OS X 10.7.
  * JRE minimization is very conservative. Depending on your app, you can carve out stuff from a JRE yourself, disable minimization and pass your custom JRE to packr.
  * On MacOS, the JVM is spawned in its own thread by default, which is a requirement of AWT. This does not work with code based on LWJGL3/GLFW, which needs the JVM be spawned on the main thread. You can enforce the latter with the `-XstartOnFirstThread` VM argument in your packr config.

## License & Contributions

The code is licensed under the [Apache 2 license](http://www.apache.org/licenses/LICENSE-2.0.html). By contributing to this repository, you automatically agree that your contribution can be distributed under the Apache 2 license by the author of this project. You will not be able to revoke this right once your contribution has been merged into this repository.

## Security

Distributing a bundled JVM has security implications, just like bundling any other runtimes like Mono, Air, etc. Make sure you understand the implications before deciding to use this tool. Here's a [discussion on the topic](http://www.reddit.com/r/gamedev/comments/24orpg/packr_package_your_libgdxjavascalajvm_appgame_for/ch99zk2).
