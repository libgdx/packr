/*
 * Copyright 2020 Nimbly Games, LLC
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
version = "2.2.0-SNAPSHOT"

plugins {
   `cpp-application`
   xcode
   `microsoft-visual-cpp-compiler`
   `visual-studio`
   `maven-publish`
}

repositories {
   jcenter()
}

/**
 * Path to the JVM that Gradle is running in
 */
val javaHomePathString: String = Jvm.current().javaHome.absolutePath

/**
 * Where to output executable files
 */
val distributionDirectoryPath = buildDir.toPath().resolve("distribute")


/**
 * The combined platform MacOS executable file path
 */
val macOsLipoOutputFilePath: Path = distributionDirectoryPath.resolve("packrLauncher-macos")

/**
 * Combines the x86 and x86-64 executables into a combined platform executable
 */
val macOsLipo = tasks.register<Exec>("macOsLipo") {
   workingDir = distributionDirectoryPath.toFile()
   outputs.file(macOsLipoOutputFilePath.toFile())
   executable = "lipo"
   args("-create")
   args("-output")
   args(macOsLipoOutputFilePath.fileName.toString())
}

tasks.withType(CppCompile::class).configureEach {
   source.from(fileTree(file("src/main/cpp")) {
      include("**/*.c")
   })
}

dependencies {
   implementation(project(":DrOpt"))
}

application {
   targetMachines.set(listOf(machines.windows.x86,
         machines.windows.x86_64,
         machines.linux.x86,
         machines.linux.x86_64,
         machines.macOS.x86,
         machines.macOS.x86_64))

   toolChains.forEach { toolChain ->
      if (toolChain is VisualCpp) {
         toolChain.setInstallDir(File("C:/Program Files (x86)/Microsoft Visual Studio/2017/Community"))
         toolChain.setWindowsSdkDir(File("C:/Program Files/Microsoft SDKs/Windows/v7.1"))
      }
   }

   binaries.configureEach(CppExecutable::class.java) {
      logger.debug("Configuring executable ${this.name}")

      val binaryToolChain = toolChain
      val binaryCompileTask = compileTask.get()

      binaryCompileTask.includes(file("$javaHomePathString/include"))

      val binaryLinkTask: LinkExecutable = linkTask.get()

      val isMacOs = targetMachine.operatingSystemFamily.name == OperatingSystemFamily.MACOS
      // Create a single special publication from lipo on MacOS since that allows combining multliple platforms into a single binary
      if (binaryCompileTask.isOptimized && (!isMacOs || targetMachine.architecture.name == MachineArchitecture.X86_64)) {
         logger.info("binaryLinkTask.linkedFile = ${binaryLinkTask.linkedFile.get()}")

         val publicationName = "packrLauncher-${targetMachine.operatingSystemFamily.name}${if (!isMacOs) "-${targetMachine.architecture.name}" else ""}"
         publishing.publications.register<MavenPublication>(publicationName) {
            val artifactFile = if (isMacOs) macOsLipoOutputFilePath.toFile() as Any else binaryLinkTask.linkedFile
            artifact(artifactFile) {
               if (isMacOs) {
                  builtBy(macOsLipo)
               } else {
                  builtBy(binaryLinkTask)
               }

               groupId = project.group as String
               version = project.version as String
               artifactId = publicationName
            }
         }
      }

      if (binaryToolChain is VisualCpp) {
         binaryCompileTask.includes(file("$javaHomePathString/include/win32"))

         binaryCompileTask.macros["DLL_EXPORT"] = null
         binaryCompileTask.macros["_WIN32"] = null
         binaryCompileTask.macros["WIN32"] = null
         binaryCompileTask.macros["UNICODE"] = null
         binaryCompileTask.macros["_UNICODE"] = null
         binaryCompileTask.macros["_WIN32_WINNT_WINXP=0x0501"] = null // target windows xp

         binaryCompileTask.compilerArgs.add("/EHs")
         binaryCompileTask.compilerArgs.add("/MT")
         binaryCompileTask.compilerArgs.add("/nologo")

         binaryLinkTask.linkerArgs.add("/nologo")

         if (targetMachine.architecture.name == MachineArchitecture.X86) {
            binaryLinkTask.linkerArgs.add("/MACHINE:X86")
         } else if (targetPlatform.targetMachine.architecture.name == MachineArchitecture.X86_64) {
            binaryLinkTask.linkerArgs.add("/MACHINE:X86_64")
         }
         binaryLinkTask.linkerArgs.add("User32.lib")
      } else if (binaryToolChain is Gcc) {
         binaryCompileTask.compilerArgs.add("-fPIC")
         binaryCompileTask.compilerArgs.add("-c")
         binaryCompileTask.compilerArgs.add("-fmessage-length=0")
         binaryCompileTask.compilerArgs.add("-Wwrite-strings")

         // compiler linux
         if (targetPlatform.targetMachine.architecture.name == MachineArchitecture.X86) {
            binaryCompileTask.compilerArgs.add("-m32")
         }
         if (targetPlatform.targetMachine.architecture.name == MachineArchitecture.X86_64) {
            binaryCompileTask.compilerArgs.add("-m64")
         }

         if (targetPlatform.targetMachine.operatingSystemFamily.isLinux) {
            binaryCompileTask.includes(file("/usr/lib/jvm/java-8-openjdk-amd64/include/"))
            binaryCompileTask.includes(file("/usr/lib/jvm/java-8-openjdk-amd64/include/linux/"))
         }

         // compiler osx
         if (targetPlatform.targetMachine.operatingSystemFamily.isMacOs) {
            binaryCompileTask.includes(file("/System/Library/Frameworks/JavaVM.framework/Versions/A/Headers"))
         }

         binaryLinkTask.linkerArgs.add("-ldl")
      } else if (binaryToolChain is Clang) {
         binaryCompileTask.compilerArgs.add("-fPIC")
         binaryCompileTask.compilerArgs.add("-c")
         binaryCompileTask.compilerArgs.add("-fmessage-length=0")
         binaryCompileTask.compilerArgs.add("-Wwrite-strings")

         binaryCompileTask.includes(file("$javaHomePathString/include/darwin"))

         binaryCompileTask.compilerArgs.add("-std=c++11")

         binaryLinkTask.linkerArgs.add("-ldl")
      }

      if (binaryCompileTask.isOptimized) {
         if (targetMachine.operatingSystemFamily.isMacOs) {
            macOsLipo.configure {
               dependsOn(binaryLinkTask)
               inputs.file(binaryLinkTask.linkedFile)
                  args("-arch")
                  if (targetMachine.architecture.name == MachineArchitecture.X86) {
                     args("i386")
                  } else if (targetMachine.architecture.name == MachineArchitecture.X86_64) {
                     args("x86_64")
                  }
               args(binaryLinkTask.linkedFile.get().asFile.absolutePath)
            }
         }
      }
   }
}

publishing {
   repositories {
      for (publishRepositoryIndex in 0..10) {
         if (project.hasProperty("maven.publish.repository.url.$publishRepositoryIndex")) {
            maven {
               url = uri(project.findProperty("maven.publish.repository.url.$publishRepositoryIndex") as String)
               credentials {
                  username = project.findProperty("maven.publish.repository.username.$publishRepositoryIndex") as String
                  password = project.findProperty("maven.publish.repository.password.$publishRepositoryIndex") as String
               }
            }
         }
      }
   }
   publications {
      configureEach {
         if (this is MavenPublication) {
            pom {
               name.set("Packr from libGdx (Nimbly Games)")
               description.set("Forked version of libGdx Packr")
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
