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
      JvmRemoteArchiveInformation(uri("https://github.com/AdoptOpenJDK/openjdk8-binaries/releases/download/jdk8u275-b01/OpenJDK8U-jdk_x64_linux_hotspot_8u275b01.tar.gz").toURL(),
            "06fb04075ed503013beb12ab87963b2ca36abe9e397a0c298a57c1d822467c29"),

      // Linux x86-64 Java 11
      JvmRemoteArchiveInformation(uri("https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.10%2B9/OpenJDK11U-jdk_x64_linux_hotspot_11.0.10_9.tar.gz").toURL(),
            "ae78aa45f84642545c01e8ef786dfd700d2226f8b12881c844d6a1f71789cb99"),

      // Linux x86-64 Java 11 JRE
      JvmRemoteArchiveInformation(uri("https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.10%2B9/OpenJDK11U-jre_x64_linux_hotspot_11.0.10_9.tar.gz").toURL(),
            "25fdcf9427095ac27c8bdfc82096ad2e615693a3f6ea06c700fca7ffb271131a"),

      // Linux x86-64 Java 15
      JvmRemoteArchiveInformation(uri("https://github.com/AdoptOpenJDK/openjdk15-binaries/releases/download/jdk-15.0.1%2B9/OpenJDK15U-jdk_x64_linux_hotspot_15.0.1_9.tar.gz").toURL(),
            "61045ecb9434e3320dbc2c597715f9884586b7a18a56d29851b4d4a4d48a2a5e"),

      // macOS x86-64 Java 8
      JvmRemoteArchiveInformation(uri("https://github.com/AdoptOpenJDK/openjdk8-binaries/releases/download/jdk8u275-b01/OpenJDK8U-jdk_x64_mac_hotspot_8u275b01.tar.gz").toURL(),
            "4afd2b3d21b625392fe4501e9445d1125498e6e7fb78042495c04e7cfc1b5e69"),

      // macOs x86-64 Java 11
      JvmRemoteArchiveInformation(uri("https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.10%2B9/OpenJDK11U-jdk_x64_mac_hotspot_11.0.10_9.tar.gz").toURL(),
            "ee7c98c9d79689aca6e717965747b8bf4eec5413e89d5444cc2bd6dbd59e3811"),

      // macOs x86-64 Java 11 JRE
      JvmRemoteArchiveInformation(uri("https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.10%2B9/OpenJDK11U-jre_x64_mac_hotspot_11.0.10_9.tar.gz").toURL(),
            "215e94323d7c74fe31e5383261e3bfc8e9ca3dc03212738c48d29868b02fe875"),

      // macOs x86-64 Java 15
      JvmRemoteArchiveInformation(uri("https://github.com/AdoptOpenJDK/openjdk15-binaries/releases/download/jdk-15.0.1%2B9.1/OpenJDK15U-jdk_x64_mac_hotspot_15.0.1_9.tar.gz").toURL(),
            "b8c2e2ad31f3d6676ea665d9505b06df15e23741847556612b40e3ee329fc046"),

      // Windows x86-64 Java 8
      JvmRemoteArchiveInformation(uri("https://github.com/AdoptOpenJDK/openjdk8-binaries/releases/download/jdk8u275-b01/OpenJDK8U-jdk_x64_windows_hotspot_8u275b01.zip").toURL(),
            "cfce82307ef498a98155a44ca472873174094aa148ce33ca40b029a0d9bf8bee"),

      // Windows x86-64 Java 11
      JvmRemoteArchiveInformation(uri("https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.10%2B9/OpenJDK11U-jdk_x64_windows_hotspot_11.0.10_9.zip").toURL(),
            "d92722551cb6ff9b8a63c12a92d7ccacfd4c17e9159f6c7eb427a3a776049af8"),

      // Windows x86-64 Java 11 JRE
      JvmRemoteArchiveInformation(uri("https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.10%2B9/OpenJDK11U-jre_x64_windows_hotspot_11.0.10_9.zip").toURL(),
            "bab0d47f4764520a96890d00ef5f27d3eb350f77e8dd15e6adf560993fb12595"),

      // Windows x86-64 Java 15
      JvmRemoteArchiveInformation(uri("https://github.com/AdoptOpenJDK/openjdk15-binaries/releases/download/jdk-15.0.1%2B9/OpenJDK15U-jdk_x64_windows_hotspot_15.0.1_9.zip").toURL(),
            "0cd7e61b0a37186902062a822caa0e14662b676c245b7ebe541f115f3c45681a"))

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
      jdkFilenameLowercase.contains("openjdk15u") -> {
         JavaVersion.VERSION_15
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
            getJdkOsFamily(jvmDownloadFilename)))) {
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
    */
   // @formatter:off
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
      pathStream.filter { path -> path.fileName.toString().startsWith("jlink") && path.parent.fileName.toString() == "bin" }
         .findFirst()
         .orElse(null)
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
            args("--add-modules")
            args("java.desktop")
            args("--add-modules")
            args("jdk.unsupported")
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
