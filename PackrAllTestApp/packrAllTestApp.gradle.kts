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
import java.nio.file.Files
import java.nio.file.Path

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

java {
   sourceCompatibility = JavaVersion.VERSION_1_8
   targetCompatibility = JavaVersion.VERSION_1_8
}

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
}

application {
   mainClassName = "com.nimblygames.packrtestapp.PackrAllTestApplication"
}

/**
 * Sync the packr-all jar
 */
val syncPackrAllJar = tasks.register<Sync>("syncPackrAllJar") {
   dependsOn(packrAllArchive)

   from(packrAllArchive)
   into(File(buildDir, "packr"))
   rename { existingFilename ->
      when {
         existingFilename.startsWith("Packr-") && existingFilename.endsWith("-all.jar") -> {
            "packr-all.jar"
         }
         else -> {
            existingFilename
         }
      }
   }
}

/**
 * Creates the Jar for this application
 */
val jarTask: TaskProvider<Jar> = tasks.named<Jar>(JavaPlugin.JAR_TASK_NAME) {
   dependsOn(syncPackrAllJar)

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
   dependsOn(syncPackrAllJar)

   val outputDirectoryPath = buildDir.toPath().resolve("testApp")
   outputs.dir(outputDirectoryPath.toFile())

   inputs.files(configurations[JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME])
   inputs.dir(jarTask.get().destinationDirectory)

   doFirst {
      configurations[JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME].resolve().forEach {
         logger.info("runtime classpath=$it")
      }
   }

   doLast {
      val macContentPath = outputDirectoryPath.resolve("mac-content")
      Files.createDirectories(macContentPath)
      createMacContent(macContentPath)

      val linuxContentPath = outputDirectoryPath.resolve("linux-content")
      Files.createDirectories(linuxContentPath)
      createLinuxContent(linuxContentPath)

      val windowsContentPath = outputDirectoryPath.resolve("windows-content")
      Files.createDirectories(windowsContentPath)
      createWindowsContent(windowsContentPath)
   }
}

// karlfixme Run the executable specific to the currently running OS
tasks.register<Exec>("runTestApplication") {
   dependsOn(createTestDirectory)

   group = "Run Configuration"
   description = "Tests if all the gathered content for packr application will work"

   workingDir = createTestDirectory.get().outputs.files.singleFile

   // run packr exe
   executable = workingDir.toPath().resolve("PackrAllTestApp").toAbsolutePath().toString()

   // karlfixme scan an output file for correctness
}

tasks.named("check") {
   dependsOn(createTestDirectory)
}

/**
 * Creates or copies all the application files for macOs.
 * @param destination the directory to create the files in
 */
fun createMacContent(destination: Path) {
   createOsContent(FAMILY_MAC, destination)
}

/**
 * Creates or copies all the application files for Linux.
 * @param destination the directory to create the files in
 */
fun createLinuxContent(destination: Path) {
   createOsContent(FAMILY_UNIX, destination)
}

/**
 * Creates or copies all the application files for Windows.
 * @param destination the directory to create the files in
 */
fun createWindowsContent(destination: Path) {
   createOsContent(FAMILY_WINDOWS, destination)
}

/**
 * Creates and copies all the application files for the specified operating system [osFamily], and places them in [destination].
 * @param osFamily the OS to create the application content files for
 * @param destination the directory to place all generated [osFamily] files in
 */
fun createOsContent(osFamily: String, destination: Path) {
   createPackrContent(osFamily, destination)
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
fun createPackrContent(osFamily: String, destination: Path) {
   exec {
      executable = "$javaHomePath/bin/java"

      args("-jar")
      args(syncPackrAllJar.get().destinationDir.toPath().resolve("packr-all.jar").toAbsolutePath().toString())

      when (osFamily) {
         FAMILY_MAC -> {
            args("--platform")
            args("mac")

            args("--jdk")
            args(file("jdk8Archives/OpenJDK8U-jdk_x64_mac_hotspot_8u252b09.zip"))
         }
         FAMILY_UNIX -> {
            args("--platform")
            args("linux64")

            args("--jdk")
            args(file("jdk8Archives/OpenJDK8U-jdk_x64_linux_hotspot_8u252b09.zip"))
         }
         FAMILY_WINDOWS -> {
            args("--platform")
            args("windows64")

            args("--jdk")
            args(file("jdk8Archives/OpenJDK8U-jdk_x64_windows_hotspot_8u252b09.zip"))
         }
      }
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

      // karlfixme add and test "other resources"
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

   if (isFamily(FAMILY_UNIX)) {
      if (osFamily == FAMILY_UNIX || osFamily == FAMILY_MAC) {
         exec {
            executable = "chmod"
            args("+x")

            val javaRelativePathString = "jre/bin/java"
            args(destination.resolve(javaRelativePathString).toAbsolutePath().toString())
         }

         exec {
            executable = "chmod"
            args("+x")

            args(destination.resolve("PackrAllTestApp").toAbsolutePath().toString())
         }
      }
      if (osFamily == FAMILY_MAC) {
         exec {
            executable = "chmod"
            args("+x")

            args(destination.resolve("PackrAllTestApp.app").toAbsolutePath().toString())
         }
      }
   }
}


// karlfixme have check depend on running the packr exe