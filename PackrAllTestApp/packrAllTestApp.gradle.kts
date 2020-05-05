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

import org.apache.tools.ant.taskdefs.condition.Os
import org.apache.tools.ant.taskdefs.condition.Os.FAMILY_MAC
import org.apache.tools.ant.taskdefs.condition.Os.FAMILY_UNIX
import org.apache.tools.ant.taskdefs.condition.Os.FAMILY_WINDOWS
import org.apache.tools.ant.taskdefs.condition.Os.isFamily
import org.gradle.internal.jvm.Jvm
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

group = rootProject.group
version = rootProject.version

plugins {
   application
}

repositories {
   mavenCentral()
   jcenter()
   maven {
      url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
   }
   for (repositoryIndex in 0..10) {
      if (project.hasProperty("maven.repository.url.$repositoryIndex") && project.findProperty("maven.repository.isdownload.$repositoryIndex")
            .toString()
            .toBoolean()) {
         maven {
            url = uri(project.findProperty("maven.repository.url.$repositoryIndex") as String)
            if (project.hasProperty("maven.repository.username.$repositoryIndex")) {
               credentials {
                  username = project.findProperty("maven.repository.username.$repositoryIndex") as String
                  password = project.findProperty("maven.repository.password.$repositoryIndex") as String
               }
            }
         }
      }
   }
}

buildscript {
   dependencies {
      classpath("com.google.guava:guava:29.0-jre")
   }
}

java {
   sourceCompatibility = JavaVersion.VERSION_1_8
   targetCompatibility = JavaVersion.VERSION_1_8
}

/**
 * Configuration for consuming all the artifacts produced by TestAppJreDist
 */
val jdksAndJresFromJreDist = configurations.register("jdksAndJresFromJreDist")

/**
 * The configuration for depending on the Packr Launcher executables
 */
val packrAllArchive = configurations.register("packrAllArchive")
dependencies {
   // test
   testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")

   // logging
   val log4jVersion = "2.13.1"
   implementation("org.slf4j:slf4j-api:1.7.30")
   runtimeOnly("org.apache.logging.log4j:log4j-slf4j-impl:$log4jVersion")
   runtimeOnly("org.apache.logging.log4j:log4j-core:$log4jVersion")

   add(packrAllArchive.name, project(":Packr", "packrAll"))

   add(jdksAndJresFromJreDist.name, project(":TestAppJreDist", "jdksAndCurrentPlatformJlinkedJres"))
}

application {
   mainClassName = "com.nimblygames.packrtestapp.PackrAllTestApplication"
}

/**
 * Creates the Jar for this application
 */
val jarTask: TaskProvider<Jar> = tasks.named<Jar>(JavaPlugin.JAR_TASK_NAME) {
   @Suppress("UnstableApiUsage") manifest {
      attributes["Main-Class"] = application.mainClassName
   }
}

tasks.withType(Test::class).configureEach {
   useJUnitPlatform()
}

/**
 * Creates build/testApp directory with all the content that will be distributed/published to distribution platforms such as Steam.
 */
val createTestDirectory: TaskProvider<Task> = tasks.register("createTestDirectory") {
   dependsOn(configurations[JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME])
   dependsOn(jarTask)
   dependsOn(packrAllArchive)
   dependsOn(jdksAndJresFromJreDist)

   val outputDirectoryPath = buildDir.toPath().resolve("testApp")
   outputs.dir(outputDirectoryPath.toFile())

   inputs.files(configurations[JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME])
   inputs.files(packrAllArchive)
   inputs.dir(jarTask.get().destinationDirectory)

   doFirst {
      configurations[JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME].resolve().forEach {
         logger.info("runtime classpath=$it")
      }
   }

   doLast {
      val jvmArchivesToRunPackrOn = CopyOnWriteArrayList<Path>()
      jdksAndJresFromJreDist.get().resolve().parallelStream().forEach {
         val path = it.toPath()
         if (Files.isSameFile(jdkArchiveDirectory, path)) return@forEach
         val pathnameLowerCase = path.fileName.toString().toLowerCase()
         if (!(pathnameLowerCase.endsWith(".zip") || pathnameLowerCase.endsWith(".gz")) || !pathnameLowerCase.contains("jdk")) {
            logger.info("Skipping path=$path in jdksAndJresFromJreDist")
            return@forEach
         }
         jvmArchivesToRunPackrOn.add(path)
      }
      jvmArchivesToRunPackrOn.parallelStream().forEach { path ->
         logger.info("Running packr against $path")
         val fileNameNoExtension = path.fileName.toString().substring(0, path.fileName.toString().indexOf('.'))
         val packrOutputDirectory = outputDirectoryPath.resolve(fileNameNoExtension)
         val osFamily = when {
            fileNameNoExtension.contains("linux") -> FAMILY_UNIX
            fileNameNoExtension.contains("mac") -> FAMILY_MAC
            fileNameNoExtension.contains("windows") -> FAMILY_WINDOWS
            else -> throw GradleException("Not sure how to test $path from ${project(":TestAppJreDist").name}")
         }
         createPackrContent(path, osFamily, packrOutputDirectory)

         // Execute each generated packr bundle that is compatible with the current OS
         if (isFamily(osFamily) && !(isFamily(FAMILY_MAC) && Objects.equals(osFamily, FAMILY_UNIX))) {
            logger.info("Executing packr in ${packrOutputDirectory.toAbsolutePath()}")
            val standardOutputCapture = ByteArrayOutputStream()
            val execResult = exec {
               workingDir = packrOutputDirectory.toFile()
               environment("PATH", "")
               environment("LD_LIBRARY_PATH", "")
               @Suppress("SpellCheckingInspection") environment("DYLD_LIBRARY_PATH", "")

               // run packr exe
               executable = if (isFamily(FAMILY_MAC)) {
                  workingDir.toPath().resolve("Contents").resolve("MacOS").resolve("PackrAllTestApp").toAbsolutePath().toString()
               } else {
                  workingDir.toPath().resolve("PackrAllTestApp").toAbsolutePath().toString()
               }
               args("-c")
               args("--verbose")
               args("--")

               standardOutput = standardOutputCapture
               isIgnoreExitValue = true
            }
            val outputAsString = standardOutputCapture.toByteArray().toString(Charsets.UTF_8)
            logger.info("Captured standard output:\n$outputAsString")

            if (!outputAsString.contains("Hello world!")) {
               throw GradleException("Packr bundle in $packrOutputDirectory didn't execute properly, output did not contain hello world")
            }
            if (!outputAsString.contains("Loaded resource line: My resource!")) {
               throw GradleException("Packr bundle in $packrOutputDirectory didn't execute properly, output did not contain My resource!")
            }
            execResult.assertNormalExitValue()
         }
      }
   }
}

tasks.named("check") {
   dependsOn(createTestDirectory)
}

/**
 * Gradle property specifying where the JDK archives directory is
 */
val jdkArchiveProperty = findProperty("jdk.archive.directory") as String?

/**
 * Path to a directory containing any number of JDK archives to test.
 */
val jdkArchiveDirectory: Path = if (jdkArchiveProperty == null) {
   Paths.get(System.getProperty("java.io.tmpdir")).resolve(System.getProperty("user.name")).resolve("jdk-archives")
} else {
   Paths.get(jdkArchiveProperty)
}

/**
 * The path to JAVA_HOME
 */
val javaHomePath: String = Jvm.current().jre?.homeDir?.absolutePath ?: Jvm.current().javaHome.absolutePath

/**
 *  Run libGdx Packr which creates minimized JREs and platform specific executables for running PackrAllTestApp.
 *  @param osFamily The OS to generate Packr files for. Either [Os.FAMILY_MAC], [Os.FAMILY_UNIX], or [Os.FAMILY_WINDOWS]
 *  @param destination The directory to write the files in
 *
 */
fun createPackrContent(jdkPath: Path, osFamily: String, destination: Path) {
   exec {
      executable = "$javaHomePath/bin/java"

      args("-jar")
      args(packrAllArchive.get().resolve().first().absolutePath)

      when (osFamily) {
         FAMILY_MAC -> {
            args("--platform")
            args("mac")
         }
         FAMILY_UNIX -> {
            args("--platform")
            args("linux64")
         }
         FAMILY_WINDOWS -> {
            args("--platform")
            args("windows64")
         }
      }
      args("--jdk")
      args(jdkPath.toFile())
      args("--executable")
      args("PackrAllTestApp")
      args("--classpath")
      Files.walk(jarTask.get().destinationDirectory.asFile.get().toPath()).use { pathStream ->
         pathStream.forEach { path ->
            if (Files.isSameFile(jarTask.get().destinationDirectory.asFile.get().toPath(), path)) return@forEach
            args(path.toAbsolutePath().toString())
         }
      }
      configurations[JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME].resolve().forEach {
         args(it.absolutePath)
      }
      args("--resources")
      args(projectDir.toPath().resolve("application-resources").toAbsolutePath().toString())

      args("--minimizejre")
      args("soft")
      args("--mainclass")
      args("com.nimblygames.packrtestapp.PackrAllTestApplication")
      args("--vmargs")
      args("Xms64M")
      args("--vmargs")
      args("Xmx128M")
      args("--vmargs")
      args("Dsun.java2d.noddraw=true")
      args("--vmargs")
      args("XstartOnFirstThread")
      args("--output")
      args(destination.toAbsolutePath().toString())
   }
}
