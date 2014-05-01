packr
=====

Packages your JAR, assets and a JVM for distribution on Windows (ZIP), Linux (ZIP) and Mac OS X (.app), adding a native executable file to make it appear like the app is a native app. Packr is most suitable for GUI applications.

Usage
=====
You point packr at your JAR file (containing all your code and assets), a JSON config file (specifying parameters to the JVM and the main class) and a URL or local file location to an OpenJDK build for the platform you want to build. Invoking packr from the command line looks like this:

```
java -jar packr.jar -windows -jdk /path/to/the/jdk -executable name-of-executable -jar my-own-app.jar -config config.json
```

The first parameter indicates for which platform to build the distribution, valid parameters are `-windows`, `-linux`and `-mac`.

The parameter `-jdk` specifies the location of an OpenJDK ZIP file, a directory containing OpenJDK, or an URL to an OpenJDK ZIP file. You can find prebuild binaries at https://github.com/alexkasko/openjdk-unofficial-builds. Just download one for each platform you want to create a distribution for, then let this parameter point at the respective ZIP file. You can also directly specify an URL (redirects are not supported though!).

The parameter `-executable` specifies the name of the native executable to be put into the distribution, e.g. `mygame`, would become `mygame.exe` on Windows and `mygame` on Linux/Mac.

The parameter `-jar` specifies the location of the jar to be packaged. This must contain both your code and your assets.

The parameter `-config` specifies the JSON configuration file that specifies flags for the bundled JRE. Here's an example:

```
{
   "jar": "name-of-the-jar.jar",
   "mainClass": "com/badlogic/MyMainClass",
   "vmArgs": [
      "-Xmx1G"
   ]
}
```

This file will be packaged with the distribution and read by the native executable to start the embedded JVM.

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
