# About
Packages your JAR, assets and a JVM for distribution on Windows, Linux and macOS, adding a native executable file to make it appear like a native app. Packr is most suitable for GUI applications, such as games made with [libGDX](http://libgdx.badlogicgames.com/).

On the topic of games, Packr version 2.4.2+ supports Java 14 and the [Z garbage collector](https://wiki.openjdk.java.net/display/zgc/Main) has been verified to work. Because who doesn't want GC pause times guaranteed to not exceed 10ms with work in progress for sub 1ms GC pauses. When bundling Java 14+ make sure to use `--useZgcIfSupportedOs` instead of passing `--vmargs XX:+UseZGC` because versions of Windows before Windows 10 1803 are not supported by the Z garbage collector.

Starting with Java 14, there's a new tool that is included with the JDK called [jpackage](https://docs.oracle.com/en/java/javase/14/jpackage/packaging-overview.html). There's a lot of overlap between jpackage and packr. Considering jpackage is supported by the broader OpenJDK community, it's worth looking into. It might be a better solution for your product.

# Download
The latest build is available for [download here](https://github.com/libgdx/packr/releases).

Resource artifacts are available at [Maven Central](https://mvnrepository.com/artifact/com.badlogicgames.packr)
   * Until Maven central publishing is working, the following Maven repository is available:
      * <http://artifactory.nimblygames.com/artifactory/ng-public/>

# Usage
You point packr at your JAR file(s) containing your code and assets, some configuration parameters, and a URL or local file location to a JDK build for your target platform.

Invoking packr from the command line may look like the following. For a more complete example look at the [PackrAllTestApp/packrAllTestApp.gradle.kts](./PackrAllTestApp/packrAllTestApp.gradle.kts):

```bash
java -jar packr-all.jar \
     --platform mac \
     --jdk OpenJDK11U-jre_x64_mac_hotspot_11.0.10_9.tar.gz \
     --useZgcIfSupportedOs \
     --executable myapp \
     --classpath myjar.jar \
     --mainclass com.my.app.MainClass \
     --vmargs -Xmx1G \
     --resources src/main/resources path/to/other/assets \
     --output out-mac
```

| Parameter | Meaning |
| --- | --- |
| platform | one of "windows64",  "linux64", "mac" |
| jdk | Directory, zip file, tar.gz file, or URL to an archive file of a JRE or Java 8 JDK with a JRE folder in it. Adopt OpenJDK 8, 11, and 15 are tested against <https://adoptopenjdk.net/releases.html>. You can also specify a directory to an unpacked JDK distribution. E.g. using ${java.home} in a build script.|
| executable | name of the native executable, without extension such as ".exe" |
| jrePath (optional) | path to the bundled JRE. By default, the JRE will be placed in a folder called "jre". |
| classpath | file locations of the JAR files to package |
| removelibs (optional) | file locations of JAR files to remove native libraries which do not match the target platform. See below for details. |
| mainclass | the fully qualified name of the main class, using dots to delimit package names |
| vmargs (optional) | list of arguments for the JVM, including leading dashes, e.g. "-Xmx1G" |
| useZgcIfSupportedOs (optional) | When bundling a Java 14+ JRE, the launcher will check if the operating system supports the [Z garbage collector](https://wiki.openjdk.java.net/display/zgc/Main) and use it. At the time of this writing, the supported operating systems are Linux, macOS, and Windows version 1803 (Windows 10 or Windows Server 2019) or later." |
| resources (optional) | list of files and directories to be packaged next to the native executable |
| minimizejre (optional) | Only use on Java 8 or lower. Minimize the JRE by removing directories and files as specified by an additional config file. Comes with a few config files out of the box. See below for details on the minimization config file. |
| output | the output directory. This must be an existing empty directory or a path that does not exist. Packr will create the directory if it doesn't exist but will fail if the path is not a directory or is not an empty directory. |
| cachejre (optional) | An optional directory to cache the result of JRE extraction and minimization. See below for details. |
| icon (optional, OS X) | location of an AppBundle icon resource (.icns file) |
| bundle (optional, OS X) | the bundle identifier of your Java application, e.g. "com.my.app" |
| verbose (optional) | prints more status information during processing, which can be useful for debugging |
| help | shows the command line interface help |

Alternatively, you can put all the command line arguments into a JSON file which might look like this:

```json
{
    "platform": "mac",
    "jdk": "/Users/badlogic/Downloads/OpenJDK8U-jdk_x64_mac_hotspot_8u252b09.tar.gz",
    "executable": "myapp",
    "classpath": [
        "myjar.jar"
    ],
    "removelibs": [
        "myjar.jar"
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
    "output": "out-mac"
}
```

You can then invoke the tool like this:

```bash
java -jar packr-all.jar my-packr-config.json
```

It is possible to combine a JSON configuration, and the command line. For single options, the command line parameter overrides the equivalent JSON option. For multi-options (e.g. `classpath` or `vmargs`), the options are merged.

This is an example which overrides the output folder and adds another VM argument. Note that the config file name is delimited by `--` because the option prior to it, `--vmargs`, allows multiple arguments:

```bash
java -jar packr-all.jar --output target/out-mac --vmargs -Xms256m -- my-packr-config.json
```

Finally, you can use packr from within your Java code. Just add the JAR file to your project, either manually, or via the following Gradle dependency:

```Kotlin
repositories {
   mavenCentral() // Packr artifacts will be published to Maven Central in the future
   maven(uri("https://oss.sonatype.org/content/repositories/snapshots/")) // Packr snapshot artifacts will be published to Maven Central in the future

   // The following repositories are available until artifacts can be published to Maven Central
   maven(uri("http://artifactory.nimblygames.com/artifactory/ng-public-snapshot/"))
   maven(uri("http://artifactory.nimblygames.com/artifactory/ng-public-release/"))
}
dependencies {
   implementation("com.badlogicgames.packr:packr:3.0.3")
}
```

To invoke packr, you need to create an instance of `PackrConfig` and pass it to `Packr.pack()`:

```java
PackrConfig config = new PackrConfig();
config.platform = PackrConfig.Platform.Windows32;
config.jdk = "/User/badlogic/Downloads/openjdk-for-mac.zip";
config.executable = "myapp";
config.classpath = Arrays.asList("myjar.jar");
config.removePlatformLibs = config.classpath;
config.mainClass = "com.my.app.MainClass";
config.vmArgs = Arrays.asList("-Xmx1G");
config.minimizeJre = "soft";
config.outDir = new java.io.File("out-mac");
config.useZgcIfSupportedOs = true;

new Packr().pack(config);
```

## macOS notarization and entitlements
The following entitlements when signing the PackrLauncher executable are known to work on macOS 10.15 (Catalina) and Java 14.
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>com.apple.security.cs.allow-jit</key>
	<true/>
	<key>com.apple.security.cs.allow-unsigned-executable-memory</key>
	<true/>
	<key>com.apple.security.cs.disable-executable-page-protection</key>
	<true/>
	<key>com.apple.security.cs.disable-library-validation</key>
	<true/>
	<key>com.apple.security.cs.allow-dyld-environment-variables</key>
	<true/>
</dict>
</plist> 
```

If all the bundled dylibs are signed, fewer entitlements might be possible. When using Java 8, `com.apple.security.cs.allow-unsigned-executable-memory`, and `com.apple.security.cs.disable-executable-page-protection` were not needed.

### Example macOS code signing and notarization command line steps
These steps assume you have an Apple developer account, have saved your Apple code signing certificate into Keychain and have generated an [app-specific password](https://support.apple.com/en-us/HT204397) for your Apple developer account, allowing you to pass your username and token as command line arguments. The example commands also assume you saved the app-specific password in your Keychain allowing these commands to run in an automated way, e.g., your CI pipeline can execute all these commands.
1. `codesign --sign <keychain id for certiticate> --verbose=10 --timestamp --force --options runtime --entitlements <path-to-entitlements-file> <path to exe or shared lib>`
   * You have to codesign every executable and shared library, --deep is for ["emergency repairs"](https://developer.apple.com/library/archive/technotes/tn2206/_index.html#//apple_ref/doc/uid/DTS40007919-CH1-TNTAG404).
2. `/usr/bin/ditto -c -k --keepParent <app path> <app path>.zip`
   * ditto is a commandline zip tool, any tool that creates a zip file from a directory can be used.
3. `xcrun altool --notarize-app --verbose --primary-bundle-id com.mydomain.myproduct --username '<username>' --password "@keychain:<app-specific password>" --file <app path>.zip`
   * If this step fails, it will exit with a non-zero return code and provide good output as to why it failed. E.g., "You must first sign the relevant contracts online."

**Optional steps, you can choose to wait for an email notification**
1. `xcrun altool --notarization-history 0 -u <username> -p "@keychain:<app-specific password>" --output-format xml`
   * This command grabs the history for the **last** call to `xcrun altool --notarize-app`, this will obviously fail if you're running multiple `xcrun altool --notarize-app` processes in parallel. You'll have to come up with a better way to parse the history.
2. Parse the XML output for the last request UUID, regex: `<string>(.*?)</string>`
3. In a loop, every minute check the notarization status.
   * `xcrun altool --notarization-info <parsed uuid> -u <username> -p "@keychain:<app-specific password>"`
4. Parse the output for the status, regex: `.*?Status:\s+(.*?)$`
5. When the status no longer matches `in progress` exit the loop.
6. If the `Status` did not end up as `success` the output will provide a description of what went wrong.
7. `xcrun stapler staple --verbose <app path>`

# Minimization
Unless you're stuck with using Java 8, it's best to create a minimized JRE using [jlink](https://docs.oracle.com/en/java/javase/11/tools/jlink.html). See [TestAppJreDist/testAppJreDist.gradle.kts](./TestAppJreDist/testAppJreDist.gradle.kts) for an example Gradle build script which generates JREs from downloaded JDKs.

## JRE
A standard OpenJDK 8 JRE is about 91 MiB unpacked. Packr helps you cut down on that size, thus also reducing the download size of your app.

To minimize the JRE that is bundled with your app, you have to specify a minimization configuration file via the `minimizejre` flag you supply to Packr. A minimization configuration is a JSON file containing paths to files and directories within the JRE to be removed.

As an example, have a look at the `soft` profile configuration:

```json
{
  "reduce": [
    {
      "archive": "jre/lib/rt.jar",
      "paths": [
        "com/sun/corba",
        "com/sun/jndi",
        "com/sun/media",
        "com/sun/naming",
        "com/sun/rowset",
        "sun/applet",
        "sun/corba",
        "sun/management"
      ]
    }
  ],
  "remove": [
    {
      "platform": "*",
      "paths": [
        "jre/lib/rhino.jar"
      ]
    },
    {
      "platform": "windows",
      "paths": [
        "jre/bin/*.exe",
        "jre/bin/client"
      ]
    }
  ]
}
```

This configuration will unpack `rt.jar`, remove all the listed packages and classes in `com.sun.*` and `sun.*`, then repack `rt.jar` again. By default, the JRE uses zero-compression on its JAR files to make application startup a little faster, so this step will reduce the size of `rt.jar` substantially.

Then, rhino.jar (about 1.1 MiB) and, in the JRE for Windows case, all executable files in `jre/bin/` and the folder `jre/bin/client/` will be removed.

Packr comes with two such configurations out of the box, [`soft`](./Packr/src/main/resources/minimize/soft). The `hard` profile removes a few more files, and repacks some additional JAR files.

## The "removelibs" option
Minimization aside, packr can remove all dynamic libraries which do not match the target platform from your project JAR file(s):

| platform | files removed |
| --- | --- |
| Windows | `*.dylib`, `*.so` |
| Linux | `*.dll`, `*.dylib` |
| MacOS | `*.dll`, `*.so` |

This step is optional. If you don't need it, just remove the configuration parameter to speed up packr. This step doesn't preserve the META-INF directory or files in the jar.

# Caching
Extracting and minimizing a JRE can take quite some time. When using the `cachejre` option, the result of these operations are cached in the given folder, and can be reused in subsequent runs of packr.

As of now, packr doesn't do any elaborate checks to validate the content of this cache folder. So if you update the JDK, or change the minimize profile, you need to empty or remove this folder manually to force a change.

# Output
## Windows
When packing for Windows, the following folder structure will be generated
```
outdir/
   myapp.exe
   myjar.jar
   myapp.json
   jre/
```

## Linux
```
outdir/
   myapp
   myjar.jar
   myapp.json
   jre/
```

## Mac OS X
```
outdir/
   Contents/
      Info.plist
      MacOS/
         myapp
      Resources/
         myjar.jar
         myapp.json
         jre/
         icons.icns [if config.icon is set]
```

You can further modify the Info.plist to your liking, e.g. add icons, a bundle identifier etc. If your `output` folder has the `.app` extension it will be treated as an application bundle by Mac OS X.

# Executable command line interface
By default, the native executables forward any command line parameters to your Java application's main() function. So, with the configurations above, `./myapp -x y.z` is passed as `com.my.app.MainClass.main(new String[] {"-x", "y.z" })`.

The executables themselves expose an own interface, which has to be enabled explicitly by passing `-c` or `--cli` as the **very first** parameter. In this case, the special delimiter parameter `--` is used to separate the native CLI from parameters to be passed to Java. In this case, the example above would be equal to `./myapp -c [arguments] -- -x y.z`.

Try `./myapp -c --help` for a list of available options.

> Note: On Windows, the executable does not show any output by default. Here you can use `myapp.exe -c --console [arguments]` to spawn a console window, making terminal output visible.

# Building from source code
If you want to modify the code invoke Gradle.

    $ ./gradlew clean assemble

This will create a `packr-VERSION-all.jar` file in `Packr/build/libs` directory, you may invoke as described in the Usage section above.

## Gradle project structure
The Gradle build is set up as a multi-project build. In order to fully build the multi-project you must have a compatible JRE (Java 8+) and [C/C++ build tools that the Gradle build can find](https://docs.gradle.org/current/userguide/building_cpp_projects.html#sec:cpp_supported_tool_chain).
 
### DrOpt Gradle sub-project
This is a downloaded and unzipped <https://github.com/jamesderlin/dropt/releases> version 1.1.1 source code with a Gradle script used to build it for consumption by the PackrLauncher Gradle project. The DrOpt source required a few modifications to get it compiling, namely some explicit casting in the C code.

### Packr Gradle sub-project
This is the Java code for creating application bundles that can use the native launcher executables. This project also builds the packr-all uber/shadow jar that works as an executable jar.

### PackrLauncher Gradle sub-project
This contains the platform native code for loading the JVM and starting the packr bundled application.

### PackrAllTestApp Gradle sub-project
This is an example Hello world style application that bundles itself using packr and is used as a high level test suite to help reduce breaking changes.

### TestAppJreDist Gradle sub-project
This project downloads JDKS 8, 11, and 14 and runs jlink on the 11 and 14 versions to create minimal JREs for use by PackrAllTestApp.

## Limitations
* Only Adopt OpenJDKs 8, 11, and 15 are tested (other JDKs probably work)
* Icons aren't set yet on Windows and Linux, you need to do that manually.
* Minimum platform requirement on MacOS is OS X 10.10 (Only 10.15 macOS Catalina is actively tested, there are users that report 10.14 works).
* JRE minimization is very conservative. Depending on your app, you can carve out stuff from a JRE yourself, disable minimization and pass your custom JRE to packr. If you're using Java 11+ you should create a JRE using [jlink](https://docs.oracle.com/en/java/javase/11/tools/jlink.html).
* On MacOS, the JVM is spawned in its own thread by default, which is a requirement of AWT. This does not work with code based on LWJGL3/GLFW, which needs the JVM be spawned on the main thread. You can enforce the latter with adding the `-XstartOnFirstThread` VM argument to your MacOS packr config.

# License & Contributions
The code is licensed under the [Apache 2 license](http://www.apache.org/licenses/LICENSE-2.0.html). By contributing to this repository, you automatically agree that your contribution can be distributed under the Apache 2 license by the author of this project. You will not be able to revoke this right once your contribution has been merged into this repository.

# Security
Distributing a bundled JVM has security implications, just like bundling any other runtimes like Mono, Air, etc. Make sure you understand the implications before deciding to use this tool. Here's a [discussion on the topic](http://www.reddit.com/r/gamedev/comments/24orpg/packr_package_your_libgdxjavascalajvm_appgame_for/ch99zk2).
