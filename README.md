packr
=====

Packages your JAR, assets and a JVM for distribution on Windows (ZIP), Linux (ZIP) and Mac OS X (.app), adding a native executable file to make it appear like the app is a native app. Packr is most suitable for GUI applications.

#### [Download Packr](http://libgdx.badlogicgames.com/packr/) (jar-with-dependencies)

Usage
=====
You point packr at your JAR file (containing all your code and assets), a JSON config file (specifying parameters to the JVM and the main class) and a URL or local file location to an OpenJDK build for the platform you want to build. Invoking packr from the command line may look like this:

```bash
java -jar packr-1.0-SNAPSHOT-jar-with-dependencies.jar \
     -platform mac \
     -jdk "openjdk-1.7.0-u45-unofficial-icedtea-2.4.3-macosx-x86_64-image.zip" \
     -executable myapp \
     -appjar myapp.jar \
     -mainclass "com/my/app/MainClass"
     -vmargs "-Xmx1G"
     -resources pom.xml;src/main/resources \
     -minimizejre true
     -outdir out
```

| Parameter | Meaning |
| --- | --- |
| platform | one of "windows", "linux", "mac" |
| jdk | ZIP file location or URL to an OpenJDK build containing a JRE. Prebuild JDKs can be found at https://github.com/alexkasko/openjdk-unofficial-builds |
| executable | name of the native executable, without extension such as ".exe" |
| appjar | file location of the JAR to package |
| mainclass | the fully qualified name of the main class, using forward slashes to delimit package names |
| vmargs | list of arguments for the JVM, separated by `;`, e.g. "-Xmx1G" |
| outdir | output directory |
| resources (optional) | list of files and directories to be packaged next to the native executable, separated by `;`.
| minimizejre | true or false, if true this will cut out a ton of usually unnecessary stuff, see Packr.java, method #minimizeJre() |

Alternatively, you can put all the command line arguments into a JSON file which might look like this:

> my-packaging-config.json
```json
{
    "platform": "mac",
    "jdk": "/Users/badlogic/Downloads/openjdk-1.7.0-u45-unofficial-icedtea-2.4.3-macosx-x86_64-image.zip",
    "executable": "myapp",
    "appjar": "target/myapp.jar",
    "mainclass": "/com/my/app/MainClass",
    "vmargs": [
       "-Xmx1G"
    ],
    "resources": [
        "pom.xml",
        "src/main/resources"
    ],
    "minimizejre": true,
    "outdir": "out-mac"
}
```

You can then invoke the tool like this:

```bash
java -jar packr-1.0-SNAPSHOT-jar-with-dependencies my-packaging-config.json
```

Finally, you can use packr from within your code. Just add the JAR file to your project, either manually, or via the following Maven dependency:

```xml
<dependency>
   <groupId>com.badlogicgames.packr</groupId>
   <artifactId>packr</artifactId>
   <version>1.0</version>
</dependency>
```

To invoke packr, you need to create an instance of `Config` and pass it to `Packr#pack()`

```java
Config config = new Config();
config.platform = Platform.windows;
config.jdk = "/User/badlogic/Downloads/openjdk-for-mac.zip";
config.executable = "myapp";
config.jar = "myjar.jar";
config.mainClass = "com/my/app/MainClass";
config.vmArgs = Arrays.asList("-Xmx1G");
config.minimizeJre = true;
config.outDir = "out-mac";

new Packr().pack(config)
```

Output
======
When packing for Windows, the following folder structure will be generated

```
outdir/
   executable.exe
   yourjar.jar
   config.json
   jre/
```

Linux (64-bit!)

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
```

You can futher modify the Info.plist to your liking, e.g. add icons, a bundle identifier etc. If your `outdir` has the `.app` extension it will be treated as an application bundle by Mac OS X.

Building
========
If you only modify the Java code, it's sufficient to invoke Maven

```
mvn clean package
```

This will create a `packr-VERSION.jar` file in `target` which you can invoke as described in the Usage section above.

If you want to compile the exe files used by packr, install premake, Visual Studio 2010 Express on Windows, Xcode on Mac OS X and GCC on Linux, then invoke the build-xxx scripts in the `natives/` folder. Each script will create an executable file for the specific platform and place it under src/main/resources.

Limitations
===========

  * Icons aren't set yet on any platform, need to do that manually.
  * Windows is 32-bit only, Linux is 64-bit only, Mac OS X is 64-bit only
