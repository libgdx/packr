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
 */

import com.google.common.hash.Hashing
import com.google.common.io.BaseEncoding
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.tasks.bundling.Compression.GZIP
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import com.google.common.io.Files as GuavaFiles

group = rootProject.group
version = rootProject.version

plugins {
   base
}

repositories {
   mavenCentral()
   maven {
      url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
   }
   for (repositoryIndex in 0..10) {
      if (project.hasProperty("maven.repository.url.$repositoryIndex") && project.findProperty("maven.repository.isdownload.$repositoryIndex").toString()
            .toBoolean()
      ) {
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

/**
 * A configuration containing jlink processed JREs for the current platform and JDKs for other platforms
 */
val jdksAndCurrentPlatformJlinkedJres = configurations.register("jdksAndCurrentPlatformJlinkedJres")

/**
 * Gradle property specifying where the JDK archives directory is
 */
val jdkArchiveProperty = findProperty("jdk.archive.directory") as String?

/**
 * Grabs the last path entry from the URL and returns that as the filename.
 * @return The filename of the URL download location
 */
fun getFilenameFromJdkUrl(jdkUrlArchive: URL) = jdkUrlArchive.path.substring(jdkUrlArchive.path.lastIndexOf('/') + 1)

/**
 * Path to a directory containing any number of JDK archives to test.
 */
val jdkArchiveDirectory: Path = if (jdkArchiveProperty == null) {
   Paths.get(System.getProperty("java.io.tmpdir")).resolve(System.getProperty("user.name")).resolve("jdk-archives")
} else {
   Paths.get(jdkArchiveProperty)
}

/**
 * Holds the information needed to download and verify a JVM.
 */
data class JvmRemoteArchiveInformation(
   /**
    * The URL for downloading the JVM
    */
   val archiveUrl: URL,
   /**
    * The SHA 256 of the download
    */
   val archiveSha256: String
)

/**
 * List of JREs and JDKs to download and use with PackAllTestApp. The URLs are parsed for specific infomation so if that format changes the code will need to be updated.
 *
 * JREs are downloaded for Java 8 versions because there's no need for the JDKs
 *
 * JDKs are downloaded for Java 11 and 14 so that minimal JREs can be generated using jlink
 */
@Suppress("SpellCheckingInspection") val jvmRemoteArchiveInformationList = listOf( // Linux x86-64 Java 8
   JvmRemoteArchiveInformation(
      uri("https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u332-b09/OpenJDK8U-jdk_x64_linux_hotspot_8u332b09.tar.gz").toURL(),
      "adc13a0a0540d77f0a3481b48f10d61eb203e5ad4914507d489c2de3bd3d83da"
   ),

   // Linux x86-64 Java 11
   JvmRemoteArchiveInformation(
      uri("https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.15%2B10/OpenJDK11U-jdk_x64_linux_hotspot_11.0.15_10.tar.gz").toURL(),
      "5fdb4d5a1662f0cca73fec30f99e67662350b1fa61460fa72e91eb9f66b54d0b"
   ),

   // Linux x86-64 Java 11 JRE
   JvmRemoteArchiveInformation(
      uri("https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.15%2B10/OpenJDK11U-jre_x64_linux_hotspot_11.0.15_10.tar.gz").toURL(),
      "22831fd097dfb39e844cb34f42064ff26a0ada9cd13621d7b8bca8e9b9d3a5ee"
   ),

   // Linux x86-64 Java 17
   JvmRemoteArchiveInformation(
      uri("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.3%2B7/OpenJDK17U-jdk_x64_linux_hotspot_17.0.3_7.tar.gz").toURL(),
      "81f5bed21077f9fbb04909b50391620c78b9a3c376593c0992934719c0de6b73"
   ),

   // macOS x86-64 Java 8
   JvmRemoteArchiveInformation(
      uri("https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u332-b09/OpenJDK8U-jdk_x64_mac_hotspot_8u332b09.tar.gz").toURL(),
      "a75e8182bb8e77a02c7b4d9f93120c64c1988e2c415b3646d4f4496544e87291"
   ),

   // macOs x86-64 Java 11
   JvmRemoteArchiveInformation(
      uri("https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.15%2B10/OpenJDK11U-jdk_x64_mac_hotspot_11.0.15_10.tar.gz").toURL(),
      "ebd8b9553a7b4514599bc0566e108915ce7dc95d29d49a9b10b8afe4ab7cc9db"
   ),

   // macOs x86-64 Java 11 JRE
   JvmRemoteArchiveInformation(
      uri("https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.15%2B10/OpenJDK11U-jre_x64_mac_hotspot_11.0.15_10.tar.gz").toURL(),
      "0a5419a45fe3680610ff15afa7d854c9b79579550327d14d616ea8ccd0e89505"
   ),

   // macOs x86-64 Java 17
   JvmRemoteArchiveInformation(
      uri("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.3%2B7/OpenJDK17U-jdk_x64_mac_hotspot_17.0.3_7.tar.gz").toURL(),
      "a5db5927760d2864316354d98ff18d18bec2e72bfac59cd25a416ed67fa84594"
   ),

   // Windows x86-64 Java 8
   JvmRemoteArchiveInformation(
      uri("https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u332-b09/OpenJDK8U-jdk_x64_windows_hotspot_8u332b09.zip").toURL(),
      "780bc92292e3f9899235457189d7aa6943833c9f426d104931d399bc404c89d3"
   ),

   // Windows x86-64 Java 8 JRE
   JvmRemoteArchiveInformation(
      uri("https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u332-b09/OpenJDK8U-jre_x64_windows_hotspot_8u332b09.zip").toURL(),
      "e85179ac15fb70ade453ab0997b1a115c0067a77d5e9a9b9ce4464bd417f6716"
   ),

   // Windows x86-64 Java 11
   JvmRemoteArchiveInformation(
      uri("https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.15%2B10/OpenJDK11U-jdk_x64_windows_hotspot_11.0.15_10.zip").toURL(),
      "866edfc9c0bb2c88b5648626af3bf82513f56d072721d0d517de5797fd829fef"
   ),

   // Windows x86-64 Java 11 JRE
   JvmRemoteArchiveInformation(
      uri("https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.15%2B10/OpenJDK11U-jre_x64_windows_hotspot_11.0.15_10.zip").toURL(),
      "606b43b51acff1ff7d77cfd253c8a13e73dda3cb638f4e9d2f74d09a9c1401fb"
   ),

   // Windows x86-64 Java 17
   JvmRemoteArchiveInformation(
      uri("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.3%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.3_7.zip").toURL(),
      "595e361d1bbb627fe4a496e3c26c2a1562d118776310cfeb9ae8845e2906d9ab"
   )
)

/**
 * The directory where JDKs processed with jlink are stored.
 */
val jlinkProcessedJdkOutputDirectoryPath: Path = buildDir.toPath().resolve("processedJdks")

/**
 * Parses the filename to determine the Java version of the JDK.
 */
fun getJvmVersion(jdkFilename: String): JavaVersion {
   val jdkFilenameLowercase = jdkFilename.toLowerCase()
   return when {
      jdkFilenameLowercase.contains("openjdk8u") -> {
         JavaVersion.VERSION_1_8
      }
      jdkFilenameLowercase.contains("openjdk11u") -> {
         JavaVersion.VERSION_11
      }
      jdkFilenameLowercase.contains("openjdk14u") -> {
         JavaVersion.VERSION_14
      }
      jdkFilenameLowercase.contains("openjdk17u") -> {
         JavaVersion.VERSION_17
      }
      else -> throw GradleException("Unknown Java version for JDK $jdkFilename")
   }
}

/**
 * Parses the filename to determine the Java OS of the JDK.
 * @see [Os]
 */
fun getJdkOsFamily(jdkFilename: String): String {
   val jdkFilenameLowercase = jdkFilename.toLowerCase()
   return when {
      jdkFilenameLowercase.contains("linux") -> {
         Os.FAMILY_UNIX
      }
      jdkFilenameLowercase.contains("mac") -> {
         Os.FAMILY_MAC
      }
      jdkFilenameLowercase.contains("windows") -> {
         Os.FAMILY_WINDOWS
      }
      else -> throw GradleException("Unknown Java OS for JDK $jdkFilename")
   }
}

/*
 * List of JDKs to download
 * task - Download JDKs from the internet
 * foreach JDK to download - create task to extract it
 * foreach jdk to download - create task to jlink it
 * foreach jdk to download - create task to zip/tar.gz the jlink
 * foreach jdk to download - create artifact from zip/tar.gz task
 */
/**
 * List of download tasks for all the JVMs.
 *
 * Download tasks are only created for the current platform.
 */
val downloadJvmTasks = mutableListOf<TaskProvider<Task>>()
jvmRemoteArchiveInformationList.forEach { (jvmArchiveUrl, jvmArchiveSha256) ->
   val jvmDownloadFilename = getFilenameFromJdkUrl(jvmArchiveUrl)

   // Skip platforms that aren't the current running platform
   if ((Os.isFamily(Os.FAMILY_MAC) && getJdkOsFamily(jvmDownloadFilename) != Os.FAMILY_MAC) || (!Os.isFamily(Os.FAMILY_MAC) && !Os.isFamily(
         getJdkOsFamily(jvmDownloadFilename)
      ))
   ) {
      return@forEach
   }

   val downloadTask = tasks.register("download${jvmDownloadFilename.substring(0, jvmDownloadFilename.indexOf('.'))}") {
      inputs.property(jvmArchiveUrl.toString(), jvmArchiveSha256)

      if (Paths.get(jvmDownloadFilename).isAbsolute) {
         throw GradleException("Failed to parse filename from JDK download url $jvmArchiveUrl")
      }
      val jvmDownloadLocation = jdkArchiveDirectory.resolve(jvmDownloadFilename)
      outputs.file(jvmDownloadLocation.toFile())

      doLast {
         logger.info("Checking JDK file $jvmDownloadLocation")
         if (Files.exists(jvmDownloadLocation)) {
            logger.info("jvmDownloadLocation $jvmDownloadLocation already exists, checking SHA 256")
            val downloadArchiveSha256Hex = getFileSha256HexEncoded(jvmDownloadLocation)
            if (jvmArchiveSha256.toLowerCase() == downloadArchiveSha256Hex.toLowerCase()) {
               logger.info("SHA256 for $jvmDownloadLocation matches")
            } else {
               logger.info("SHA256 for $jvmDownloadLocation of $downloadArchiveSha256Hex does not match source $jvmArchiveSha256")
               downloadAndVerifySha256(jvmArchiveUrl, jvmDownloadLocation, jvmArchiveSha256)
            }
         } else {
            logger.info("jvmDownloadLocation $jvmDownloadLocation does not exist")
            downloadAndVerifySha256(jvmArchiveUrl, jvmDownloadLocation, jvmArchiveSha256)
         }
      }
   }
   downloadJvmTasks.add(downloadTask)
}

/**
 * List of tasks that will extract JDKs for the current platform so they can execute jlink
 */
val extractJdkTasks = mutableListOf<TaskProvider<Copy>>()

/**
 * Directory to extract all current platform JDKs into
 */
val jdksExtractionPath: Path = buildDir.toPath().resolve("jdks-extracted")
downloadJvmTasks.forEach { downloadTaskProvider ->
   val jvmArchiveFilePath = downloadTaskProvider.get().outputs.files.singleFile.toPath()

   /*
    * If the JVM is Java 8, already a JRE, or doesn't match the current platform, pass the archive as is. Java 8 or a JRE don't
    * have jlink, and jlink has to run on the matching platform
    */ // @formatter:off
   if (getJvmVersion(jvmArchiveFilePath.fileName.toString()) == JavaVersion.VERSION_1_8 || (Os.isFamily(Os.FAMILY_MAC) && getJdkOsFamily(
            jvmArchiveFilePath.fileName.toString()) != Os.FAMILY_MAC) || (!Os.isFamily(Os.FAMILY_MAC) && !Os.isFamily(
            getJdkOsFamily(jvmArchiveFilePath.fileName.toString()))) || jvmArchiveFilePath.fileName.toString()
          .contains("-jre_")) {
      // @formatter:on
      artifacts.add(jdksAndCurrentPlatformJlinkedJres.name, jvmArchiveFilePath.toFile()) {
         builtBy(downloadTaskProvider.get())
      }
      return@forEach
   }

   val extractTask = tasks.register<Copy>("extract${downloadTaskProvider.name.capitalize()}") {
      dependsOn(downloadTaskProvider)
      if (jvmArchiveFilePath.fileName.toString().toLowerCase().contains(".tar")) {
         from(tarTree(jvmArchiveFilePath.toFile()))
      } else {
         from(zipTree(jvmArchiveFilePath.toFile()))
      }

      val directoryName = jvmArchiveFilePath.fileName.toString().substring(0, jvmArchiveFilePath.fileName.toString().indexOf('.'))
      destinationDir = jdksExtractionPath.resolve(directoryName).toFile()
   }
   extractJdkTasks.add(extractTask)
}

/**
 * Locates the jlink executable inside a JDK directory
 */
fun findJlinkExecutable(jdkToJlinkDirectory: Path): Path? {
   return Files.walk(jdkToJlinkDirectory).use { pathStream ->
      pathStream.filter { path -> path.fileName.toString().startsWith("jlink") && path.parent.fileName.toString() == "bin" }.findFirst().orElse(null)
   }
}

/**
 * Directory where all JREs are saved that were created using jlink.
 */
val jlinkOutputDirectoryPath: Path = buildDir.toPath().resolve("jlink-output")

/**
 * List of all tasks that will execute jlink creating a JRE for the current platform.
 */
val jlinkTasks = mutableListOf<TaskProvider<Task>>()
extractJdkTasks.forEach { extractJdkTask ->
   val jlinkJdkTask = tasks.register("jlink${extractJdkTask.name.capitalize()}") {
      dependsOn(extractJdkTask)

      inputs.dir(extractJdkTask.get().destinationDir)

      val extractedJkdPath = extractJdkTask.get().destinationDir.toPath()
      val jlinkPath = jlinkOutputDirectoryPath.resolve(extractedJkdPath.fileName)
      outputs.dir(jlinkPath.toFile())

      doFirst { // jlink requires the directory to not exist when it runs
         Files.deleteIfExists(jlinkPath)
      }

      doLast {
         val jdkPath = Files.walk(extractedJkdPath).use { fileStream ->
            fileStream.filter { Files.isDirectory(it) && !Files.isSameFile(extractedJkdPath, it) }.findFirst()
               .orElseThrow { throw GradleException("Couldn't find nested extraction directory in $extractedJkdPath") }
         }
         val jlinkExecutablePath: Path = findJlinkExecutable(extractJdkTask.get().destinationDir.toPath())
            ?: throw GradleException("Couldn't find a suitable jlink for JDK ${extractJdkTask.get().destinationDir}")

         exec {
            executable = jlinkExecutablePath.toAbsolutePath().toString()
            args("--module-path")
            args(jdkPath.resolve("jmods").toAbsolutePath().toString())
            args("--output")
            args(jlinkPath.toAbsolutePath().toString())
            args("--add-modules")
            args("java.base")
            args("--add-modules")
            args("java.desktop")
         }
      }
   }
   jlinkTasks.add(jlinkJdkTask)
}

/**
 * Directory to store the tar files of all the jlink processed JREs for the current platform.
 */
val jlinkTarOutputDirectoryPath: Path = buildDir.toPath().resolve("jlink-tar-output")

/*
 * Tar up every jlink created JRE.
 */
jlinkTasks.forEach { jlinkTask ->
   val tarJlinkJreTask = tasks.register<Tar>("tar${jlinkTask.name.capitalize()}") {
      dependsOn(jlinkTask)

      compression = GZIP

      val jlinkOutputDirectoryFile = jlinkTask.get().outputs.files.singleFile

      from(jlinkOutputDirectoryFile)
      archiveFileName.set(jlinkOutputDirectoryFile.name.replace("jdk_", "jlink-jre_") + ".tar.gz")
      destinationDirectory.set(jlinkTarOutputDirectoryPath.toFile())
   }
   artifacts.add(jdksAndCurrentPlatformJlinkedJres.name, tarJlinkJreTask)
   tasks.named("check").configure { dependsOn(tarJlinkJreTask) }
}

/**
 * Creates a hex encoded (base16) version of the SHA 256 for the [file]
 */
@Suppress("UnstableApiUsage") fun getFileSha256HexEncoded(file: Path): String {
   val sha256 = GuavaFiles.asByteSource(file.toFile()).hash(Hashing.sha256())
   return BaseEncoding.base16().encode(sha256.asBytes())
}

/**
 * Downloads the resource [url] into [file], overwriting if it already exists
 */
fun downloadHttpUrlToFile(url: URL, file: Path) {
   logger.info("Downloading $url to $file")
   val connection = url.openConnection() as HttpURLConnection
   connection.useCaches = false
   connection.doOutput = false
   connection.doInput = true
   connection.requestMethod = "GET"
   connection.connect()
   connection.inputStream.use {
      Files.copy(it, file, REPLACE_EXISTING)
   }
}

/**
 * Downloads the content at [url] into [file] and throws an exception if the downloaded SHA 256 does not match [fileSha256Hex]
 */
fun downloadAndVerifySha256(url: URL, file: Path, fileSha256Hex: String) {
   downloadHttpUrlToFile(url, file)
   val downloadedSha256Hex = getFileSha256HexEncoded(file)
   if (fileSha256Hex.toLowerCase() != downloadedSha256Hex.toLowerCase()) {
      throw GradleException("Downloaded $url but its SHA 256 is invalid")
   }
}
