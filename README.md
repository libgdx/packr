packr
=====

Packages your JAR, assets and a JVM for distribution on Windows (ZIP), Linux (ZIP) and Mac OS X (.app), adding a native executable file to make it appear like the app is a native app. Packr is most suitable for GUI applications.

#### [Download Packr](http://libgdx.badlogicgames.com/packr/) (jar-with-dependencies)

Usage
=====
You point packr at your JAR file (containing all your code and assets), a JSON config file (specifying parameters to the JVM and the main class) and a URL or local file location to an OpenJDK build for the platform you want to build. Invoking packr from the command line may look like this:

```
java -jar packr-1.0-SNAPSHOT-jar-with-dependencies.jar \
     -platform mac \
     -jdk "openjdk-1.7.0-u45-unofficial-icedtea-2.4.3-macosx-x86_64-image.zip" \
     -executable myapp \
     -jar target/packr-1.0-SNAPSHOT.jar \
     -config native/config.json \
     -resources pom.xml;src/main/resources \
     -outdir out 
```

| Parameter | Meaning |
| --- | --- |
| platform | one of "windows", "linux", "mac" |
| jdk | ZIP file location or URL to an OpenJDK build containing a JRE |
| executable | name of the native executable, without extension such as ".exe" |
| jar | file location of the JAR to package |
| config | file location of the "config.json" file to be packaged, see below |
| outdir | output directory |
| resources (optional) | list of files and directories to be packaged next to the native executable, separated by `;`.

When the native executable is started, it tries to find `config.json` specified via the `-config` flag, parse it and use the information contained in it to start the bundled JRE. Here's an example:

```
{
    "jar": "packr-1.0-SNAPSHOT.jar",
    "mainClass": "com/badlogicgames/packr/TestApp",
    "vmArgs": [
        "-Xmx512M"
    ]
}
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
  * If you want a smaller JDK you have to currently clean up jre/rt.jar yourself. I'll try implementing tree shaking asap.
