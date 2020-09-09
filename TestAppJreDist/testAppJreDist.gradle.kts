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

import com.google.common.hash.Hashing
import com.google.common.io.BaseEncoding
import org.apache.tools.ant.taskdefs.condition.Os
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
@Suppress("SpellCheckingInspection") val jvmRemoteArchiveInformationList = listOf(
      // Linux x86-64 Java 8
      JvmRemoteArchiveInformation(uri("https://github.com/AdoptOpenJDK/openjdk8-binaries/releases/download/jdk8u252-b09/OpenJDK8U-jre_x64_linux_hotspot_8u252b09.tar.gz").toURL(),
            "a93be303ed62398dba9acb0376fb3caf8f488fcde80dc62d0a8e46256b3adfb1"),

      // Linux x86-64 Java 11
      JvmRemoteArchiveInformation(uri("https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.7%2B10/OpenJDK11U-jdk_x64_linux_hotspot_11.0.7_10.tar.gz").toURL(),
            "ee60304d782c9d5654bf1a6b3f38c683921c1711045e1db94525a51b7024a2ca"),

      // Linux x86-64 Java 14
      JvmRemoteArchiveInformation(uri("https://github.com/AdoptOpenJDK/openjdk14-binaries/releases/download/jdk-14.0.1%2B7/OpenJDK14U-jdk_x64_linux_hotspot_14.0.1_7.tar.gz").toURL(),
            "9ddf9b35996fbd784a53fff3e0d59920a7d5acf1a82d4c8d70906957ac146cd1"),

      // macOS x86-64 Java 8
      JvmRemoteArchiveInformation(uri("https://github.com/AdoptOpenJDK/openjdk8-binaries/releases/download/jdk8u252-b09.1/OpenJDK8U-jre_x64_mac_hotspot_8u252b09.tar.gz").toURL(),
            "f8206f0fef194c598de6b206a4773b2e517154913ea0e26c5726091562a034c8"),

      // macOs x86-64 Java 11
      JvmRemoteArchiveInformation(uri("https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.7%2B10/OpenJDK11U-jdk_x64_mac_hotspot_11.0.7_10.tar.gz").toURL(),
            "0ab1e15e8bd1916423960e91b932d2b17f4c15b02dbdf9fa30e9423280d9e5cc"),

      // macOs x86-64 Java 14
      JvmRemoteArchiveInformation(uri("https://github.com/AdoptOpenJDK/openjdk14-binaries/releases/download/jdk-14.0.1%2B7/OpenJDK14U-jdk_x64_mac_hotspot_14.0.1_7.tar.gz").toURL(),
            "b11cb192312530bcd84607631203d0c1727e672af12813078e6b525e3cce862d"),

      // Windows x86-64 Java 8
      JvmRemoteArchiveInformation(uri("https://github.com/AdoptOpenJDK/openjdk8-binaries/releases/download/jdk8u252-b09.1/OpenJDK8U-jre_x64_windows_hotspot_8u252b09.zip").toURL(),
            "582f58290c66d6fb7e437a92a91695d25386e2497f684715e6e1b8702a69a804"),

      // Windows x86-64 Java 11
      JvmRemoteArchiveInformation(uri("https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.7%2B10.2/OpenJDK11U-jdk_x64_windows_hotspot_11.0.7_10.zip").toURL(),
            "61e99ff902e02c83b6c48172968593ee05ae183a39e5ef13a44bd4bf7eb2ce8b"),

      // Windows x86-64 Java 14
      JvmRemoteArchiveInformation(uri("https://github.com/AdoptOpenJDK/openjdk14-binaries/releases/download/jdk-14.0.1%2B7.1/OpenJDK14U-jdk_x64_windows_hotspot_14.0.1_7.zip").toURL(),
            "935e9121ddc83e5ac82ff73bd7a4b94f25824c7a66964ef7cb3b57098ae05599"))

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
   if ((Os.isFamily(Os.FAMILY_MAC) && getJdkOsFamily(jvmDownloadFilename) != Os.FAMILY_MAC) || (!Os.isFamily(Os.FAMILY_MAC) && !Os.isFamily(getJdkOsFamily(
            jvmDownloadFilename)))) {
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

   // If the JVM is Java 8 or doesn't match the current platform simply pass on the archive as because jlink cannot create a JRE for it
   // @formatter:off
   if (getJvmVersion(jvmArchiveFilePath.fileName.toString()) == JavaVersion.VERSION_1_8
       || (Os.isFamily(Os.FAMILY_MAC) && getJdkOsFamily(jvmArchiveFilePath.fileName.toString()) != Os.FAMILY_MAC)
       || (!Os.isFamily(Os.FAMILY_MAC) && !Os.isFamily(getJdkOsFamily(jvmArchiveFilePath.fileName.toString())))) {
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

      doFirst {
         // jlink requires the directory to not exist when it runs
         Files.deleteIfExists(jlinkPath)
      }

      doLast {
         val jdkPath = Files.walk(extractedJkdPath).use { fileStream ->
            fileStream.filter { Files.isDirectory(it) && !Files.isSameFile(extractedJkdPath, it) }
               .findFirst()
               .orElseThrow { throw GradleException("Couldn't find nested extraction directory in $extractedJkdPath") }
         }
         val jlinkExecutablePath: Path =
               findJlinkExecutable(extractJdkTask.get().destinationDir.toPath())
               ?: throw GradleException("Couldn't find a suitable jlink for JDK ${extractJdkTask.get().destinationDir}")

         exec {
            executable = jlinkExecutablePath.toAbsolutePath().toString()
            args("--module-path")
            args(jdkPath.resolve("jmods").toAbsolutePath().toString())
            args("--output")
            args(jlinkPath.toAbsolutePath().toString())
            args("--add-modules")
            args("java.base")
         }
      }
   }
   jlinkTasks.add(jlinkJdkTask)
}

/**
 * Directory to store the zip files of all the jlink processed JREs for the current platform.
 */
val jlinkZipOutputDirectoryPath: Path = buildDir.toPath().resolve("jlink-zip-output")

/*
 * Zip up every jlink created JRE.
 */
jlinkTasks.forEach { jlinkTask ->
   val zipJlinkJreTask = tasks.register<Zip>("zip${jlinkTask.name.capitalize()}") {
      dependsOn(jlinkTask)

      val jlinkOutputDirectoryFile = jlinkTask.get().outputs.files.singleFile

      from(jlinkOutputDirectoryFile)
      archiveFileName.set(jlinkOutputDirectoryFile.name.replace("jdk_", "jlink-jre_") + ".zip")
      destinationDirectory.set(jlinkZipOutputDirectoryPath.toFile())
   }
   artifacts.add(jdksAndCurrentPlatformJlinkedJres.name, zipJlinkJreTask)
   tasks.named("check").configure { dependsOn(zipJlinkJreTask) }
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
