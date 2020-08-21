/*
 * Copyright 2020 See AUTHORS file
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

@file:Suppress("UnstableApiUsage")

import org.gradle.internal.jvm.Jvm
import java.nio.file.Path

group = rootProject.group
version = rootProject.version

plugins {
   `cpp-application`
   `cpp-unit-test`
   xcode
   `microsoft-visual-cpp-compiler`
   `visual-studio`
   `maven-publish`
   signing
}

repositories {
   jcenter()
   maven {
      url = uri("https://repo.gradle.org/gradle/libs-snapshots-local/")
   }
}

/**
 * Path to the JVM that Gradle is running in
 */
val javaHomePathString: String = Jvm.current().javaHome.absolutePath

/**
 * Where to output executable files
 */
val distributionDirectoryPath: Path = buildDir.toPath().resolve("distribute")


/**
 * The combined platform MacOS executable file path
 */
val macOsLipoOutputFilePath: Path = distributionDirectoryPath.resolve("packrLauncher-macos")

/**
 * Combines the executables into a combined platform executable. Currently only 64 bit architecture is built, so it doesn't do anything.
 */
val macOsLipo = tasks.register<Exec>("macOsLipo") {
   workingDir = distributionDirectoryPath.toFile()
   outputs.file(macOsLipoOutputFilePath.toFile())
   executable = "lipo"
   args("-create")
   args("-output")
   args(macOsLipoOutputFilePath.fileName.toString())
}

/**
 * Configuration for holding the release executables that are built
 */
val currentOsExecutableZip = tasks.register<Zip>("currentOsExecutableZip") {}

tasks.withType(CppCompile::class).configureEach {
   source.from(fileTree(file("src/main/cpp")) {
      include("**/*.c")
   })
}

dependencies {
   implementation(project(":DrOpt"))
   testImplementation("org.gradle.cpp-samples:googletest:1.9.0-gr4-SNAPSHOT")
}

/**
 * Stores a mapping from the registered publication names to the operating system family for generating the name in the published POM file.
 */
val operatingSystemFamilyByPublicationName: MutableMap<String, OperatingSystemFamily> = mutableMapOf<String, OperatingSystemFamily>()

/**
 * Linux x86 is no longer built because it's impossible to find a survey that shows anyone running x86 Linux.
 * MacOS x86 is no longer built because it requires and older version of Xcode and Apple makes it too difficult to install on newer versions of Mac
 * Windows x86 is no longer built because the Adopt OpenJDK has crash failures.
 */
val targetPlatformsToBuild = listOf(machines.windows.x86_64, machines.linux.x86_64, machines.macOS.x86_64)

application {
   targetMachines.set(targetPlatformsToBuild)

   binaries.configureEach(CppExecutable::class.java) {
      logger.debug("Configuring executable ${this.name}")

      val binaryToolChain = toolChain
      val binaryCompileTask = compileTask.get()

      addJvmHeaders(binaryCompileTask, this)

      val binaryLinkTask: LinkExecutable = linkTask.get()

      //
      binaryCompileTask.macros["PACKR_VERSION_STRING"] = "\"" + (project.version as String) + "\""

      // Create a single special publication from lipo on MacOS since that allows combining multiple architectures into a single binary
      val publicationName =
            "packrLauncher-${targetMachine.operatingSystemFamily.name}${if (!targetMachine.operatingSystemFamily.isMacOs) "-${targetMachine.architecture.name}" else ""}"
      if (binaryCompileTask.isOptimized && publishing.publications.findByName(publicationName) == null) {
         logger.info("executableFile = ${executableFile.get()}")

         operatingSystemFamilyByPublicationName[publicationName] = targetMachine.operatingSystemFamily
         publishing.publications.register<MavenPublication>(publicationName) {
            val artifactFile: File = if (targetMachine.operatingSystemFamily.isMacOs) macOsLipoOutputFilePath.toFile() else executableFile.get().asFile
            artifact(artifactFile) {
               if (targetMachine.operatingSystemFamily.isMacOs) {
                  builtBy(macOsLipo)
               } else {
                  builtBy(executableFileProducer)
               }

               groupId = project.group as String
               version = project.version as String
               artifactId = publicationName
            }
         }

         // Add the executable to the current OS produced configuration
         currentOsExecutableZip.configure {
            dependsOn(executableFileProducer)
            if (targetMachine.operatingSystemFamily.isMacOs) {
               dependsOn(macOsLipo)
               from(macOsLipoOutputFilePath) {
                  rename(".*", publicationName)
               }
            } else {
               from(executableFile) {
                  rename(".*", publicationName)
               }
            }
         }
      }

      // Add another target to lipo
      if (binaryCompileTask.isOptimized && targetMachine.operatingSystemFamily.isMacOs) {
         macOsLipo.configure {
            dependsOn(executableFileProducer)
            inputs.file(executableFile)
            args("-arch")
            when (targetMachine.architecture.name) {
               MachineArchitecture.X86 -> args("i386")
               MachineArchitecture.X86_64 -> args("x86_64")
               else -> throw GradleException("Don't know the lipo -arch flag for architecture ${targetMachine.architecture.name}")
            }
            args(executableFile.get().asFile.absolutePath)
         }
      }

      if (binaryToolChain is VisualCpp) {
         binaryCompileTask.macros["DLL_EXPORT"] = null
         binaryCompileTask.macros["_WIN32"] = null
         binaryCompileTask.macros["WIN32"] = null
         binaryCompileTask.macros["UNICODE"] = null
         binaryCompileTask.macros["_UNICODE"] = null
         binaryCompileTask.macros["_WIN32_WINNT_WINXP=0x0501"] = null // target windows xp

         binaryCompileTask.compilerArgs.add("/EHs")
         binaryCompileTask.compilerArgs.add("/MT")
         binaryCompileTask.compilerArgs.add("/nologo")
         binaryCompileTask.compilerArgs.add("/std:c++14")
         binaryCompileTask.compilerArgs.add("/utf-8")

         binaryLinkTask.linkerArgs.add("/nologo")

         binaryLinkTask.linkerArgs.add("/SUBSYSTEM:WINDOWS")
         binaryLinkTask.linkerArgs.add("User32.lib")
         binaryLinkTask.linkerArgs.add("Shell32.lib")
      } else if (binaryToolChain is Gcc) {
         binaryCompileTask.compilerArgs.add("-fPIC")
         binaryCompileTask.compilerArgs.add("-c")
         binaryCompileTask.compilerArgs.add("-fmessage-length=0")
         binaryCompileTask.compilerArgs.add("-Wwrite-strings")
         binaryCompileTask.compilerArgs.add("-std=c++14")
         binaryCompileTask.compilerArgs.add("-no-pie")
         binaryCompileTask.compilerArgs.add("-fno-pie")

         // compiler linux
         if (targetPlatform.targetMachine.architecture.name == MachineArchitecture.X86) {
            binaryCompileTask.compilerArgs.add("-m32")
         }
         if (targetPlatform.targetMachine.architecture.name == MachineArchitecture.X86_64) {
            binaryCompileTask.compilerArgs.add("-m64")
         }

         // compiler osx
         if (targetPlatform.targetMachine.operatingSystemFamily.isMacOs) {
            binaryCompileTask.includes(file("/System/Library/Frameworks/JavaVM.framework/Versions/A/Headers"))
         }

         binaryLinkTask.linkerArgs.add("-ldl")
         binaryLinkTask.linkerArgs.add("-no-pie")
         binaryLinkTask.linkerArgs.add("-fno-pie")
      } else if (binaryToolChain is Clang) {
         binaryCompileTask.compilerArgs.add("-fPIC")
         binaryCompileTask.compilerArgs.add("-c")
         binaryCompileTask.compilerArgs.add("-fmessage-length=0")
         binaryCompileTask.compilerArgs.add("-Wwrite-strings")
         if (targetMachine.operatingSystemFamily.isMacOs) {
            binaryCompileTask.compilerArgs.add("-mmacosx-version-min=${rootProject.ext["macOsMinimumVersion"]}")
            binaryLinkTask.linkerArgs.add("-mmacosx-version-min=${rootProject.ext["macOsMinimumVersion"]}")
         }

         binaryCompileTask.compilerArgs.add("-std=c++14")

         binaryLinkTask.linkerArgs.add("-ldl")

         if (targetPlatform.targetMachine.operatingSystemFamily.isMacOs) {
            binaryLinkTask.linkerArgs.add("-framework")
            binaryLinkTask.linkerArgs.add("CoreFoundation")
         }
      }
   }
}

unitTest {
   targetMachines.set(targetPlatformsToBuild)

   binaries.configureEach(CppTestExecutable::class.java) {
      val binaryLinkTask = linkTask.get()
      if (toolChain is Gcc) {
         if (targetMachine.operatingSystemFamily.isLinux) {
            binaryLinkTask.linkerArgs.add("-lpthread")
         }
         binaryLinkTask.linkerArgs.add("-ldl")
         binaryLinkTask.linkerArgs.add("-no-pie")
         binaryLinkTask.linkerArgs.add("-fno-pie")
      } else if (toolChain is Clang) {
         binaryLinkTask.linkerArgs.add("-ldl")
      } else if (toolChain is VisualCpp) {
         compileTask.get().macros["UNICODE"] = null
         compileTask.get().macros["_UNICODE"] = null
         binaryLinkTask.linkerArgs.add("/SUBSYSTEM:CONSOLE")
         binaryLinkTask.linkerArgs.add("Shell32.lib")
      }

      if (targetMachine.operatingSystemFamily.isMacOs) {
         binaryLinkTask.linkerArgs.add("-framework")
         binaryLinkTask.linkerArgs.add("CoreFoundation")
      }

      addJvmHeaders(compileTask.get(), this)
   }
}

tasks.withType(RunTestExecutable::class).configureEach {
   workingDir = buildDir.toPath().resolve("cppTestDirectory").toFile()
}

artifacts {
   add(configurations.register("currentOsExecutables").name, currentOsExecutableZip)
}

/**
 * Is the packer launcher version a snapshot or release?
 */
val isSnapshot = project.version.toString().contains("SNAPSHOT")

publishing {
   repositories {
      for (repositoryIndex in 0..10) {
         // @formatter:off
            @Suppress("SpellCheckingInspection")
            if (project.hasProperty("maven.repository.url.$repositoryIndex")
                    && ((project.findProperty("maven.repository.ispublishsnapshot.$repositoryIndex").toString().toBoolean() && isSnapshot)
                            || (project.findProperty("maven.repository.ispublishrelease.$repositoryIndex").toString().toBoolean() && !isSnapshot))) {
                // @formatter:on
               maven {
                  url = uri(project.findProperty("maven.repository.url.$repositoryIndex") as String)
                  credentials {
                     username = project.findProperty("maven.repository.username.$repositoryIndex") as String
                     password = project.findProperty("maven.repository.password.$repositoryIndex") as String
                  }
               }
            }
      }
   }
   publications {
      configureEach {
         if (this is MavenPublication) {
            val publicationOperatingSystemFamily = operatingSystemFamilyByPublicationName[name]
            if (publicationOperatingSystemFamily != null) {
               pom {
                  val osName = when {
                     publicationOperatingSystemFamily.isLinux -> "Linux"
                     publicationOperatingSystemFamily.isMacOs -> "macOS"
                     publicationOperatingSystemFamily.isWindows -> "Windows"
                     else -> throw IllegalArgumentException("Unknown OS $publicationOperatingSystemFamily")
                  }
                  name.set("Packr native launcher for $osName")
                  description.set("A native executable for launching a JVM app, making it appear like a native app.")
                  url.set("https://nimblygames.com/")
                  licenses {
                     license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                     }
                  }
                  developers {
                     developer {
                        id.set("KarlSabo")
                        name.set("Karl Sabo")
                        email.set("karl@nimblygames.com")
                     }
                  }
                  scm {
                     connection.set("scm:git:https://github.com/karlsabo/packr")
                     developerConnection.set("scm:git:https://github.com/karlsabo/packr")
                     url.set("https://github.com/karlsabo/packr")
                  }
               }
            }
         }
      }
   }
}

signing.useGpgCmd()

if (isSnapshot) {
   logger.info("Skipping signing ")
} else {
   publishing.publications.configureEach {
      logger.info("Should sign publication ${this.name}")
      signing.sign(this)
   }
}

afterEvaluate {
   tasks.withType(PublishToMavenRepository::class).configureEach {
      if (name.startsWith("publishMain")) {
         logger.info("Disabling CPP plugin publishing task ${this.name}")
         enabled = false
      }
   }
}

/**
 * Adds JVM include header paths to [binaryCompileTask].
 */
fun addJvmHeaders(binaryCompileTask: CppCompile, cppBinary: CppBinary) {
   binaryCompileTask.includes(file("${javaHomePathString}/include"))
   when {
      cppBinary.targetMachine.operatingSystemFamily.isLinux -> {
         binaryCompileTask.includes(file("${javaHomePathString}/include/linux"))
      }
      cppBinary.targetMachine.operatingSystemFamily.isMacOs -> {
         binaryCompileTask.includes(file("${javaHomePathString}/include/darwin"))
      }
      cppBinary.targetMachine.operatingSystemFamily.isWindows -> {
         binaryCompileTask.includes(file("${javaHomePathString}/include/win32"))
      }
   }
}
