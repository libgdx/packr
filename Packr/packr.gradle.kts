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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

group = rootProject.group
version = "2.3.0-SNAPSHOT"

plugins {
   `maven-publish`
   application
   id("com.github.johnrengelman.shadow") version "5.2.0"
   signing
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
val packrLauncherExecutables = configurations.register("PackrLauncherExecutables")
dependencies {
   //
   implementation("org.zeroturnaround:zt-zip:1.10")
   implementation("com.lexicalscope.jewelcli:jewelcli:0.8.9")
   implementation("com.eclipsesource.minimal-json:minimal-json:0.9.1")

   // test
   testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")

   // logging
   val log4jVersion = "2.13.1"
   implementation("org.slf4j:slf4j-api:1.7.30")
   runtimeOnly("org.apache.logging.log4j:log4j-slf4j-impl:$log4jVersion")
   runtimeOnly("org.apache.logging.log4j:log4j-core:$log4jVersion")

   add(packrLauncherExecutables.name, "com.nimblygames.packr:packrLauncher-linux-x86-64:2.3.0-SNAPSHOT") {
      // Gradle won't download extension free files without this
      artifact {
         this.name = "packrLauncher-linux-x86-64"
         this.type = ""
      }
   }
   add(packrLauncherExecutables.name, "com.nimblygames.packr:packrLauncher-macos:2.3.0-SNAPSHOT") {
      // Gradle won't download extension free files without this
      artifact {
         this.name = "packrLauncher-macos"
         this.type = ""
      }
   }
   add(packrLauncherExecutables.name, "com.nimblygames.packr:packrLauncher-windows-x86-64:2.3.0-SNAPSHOT")
}

application {
   mainClassName = "com.badlogicgames.packr.Packr"
}

java {
   @Suppress("UnstableApiUsage") withJavadocJar()
   @Suppress("UnstableApiUsage") withSourcesJar()
}

/**
 * Sync the Packr launcher dependencies to the build directory for including into the Jar
 */
val syncPackrLaunchers = tasks.register<Sync>("syncPackrLaunchers") {
   from(packrLauncherExecutables)
   into(File(buildDir, "packrLauncher"))
   rename { existingFilename ->
      when {
         existingFilename.contains("linux") && existingFilename.contains("x86-64") -> {
            "packr-linux-x64"
         }
         existingFilename.contains("linux") && existingFilename.contains("x86") -> {
            "packr-linux"
         }
         existingFilename.contains("mac") -> {
            "packr-mac"
         }
         existingFilename.contains("windows") && existingFilename.contains("x86-64") -> {
            "packr-windows-x64.exe"
         }
         existingFilename.contains("windows") && existingFilename.contains("x86") -> {
            "packr-windows.exe"
         }
         else -> {
            existingFilename
         }
      }
   }
}

tasks.named<Jar>(JavaPlugin.JAR_TASK_NAME) {
   dependsOn(syncPackrLaunchers)

   @Suppress("UnstableApiUsage") manifest {
      attributes["Main-Class"] = application.mainClassName
   }

   from(File(buildDir, "packrLauncher"))
}

tasks.withType(Test::class).configureEach {
   useJUnitPlatform()
}

tasks.withType(ShadowJar::class).configureEach {
   dependsOn(syncPackrLaunchers)

   @Suppress("UnstableApiUsage") manifest {
      attributes["Main-Class"] = application.mainClassName
   }

   from(File(buildDir, "packrLauncher"))
}

/**
 * Is the packer version a snapshot or release?
 */
val isSnapshot = project.version.toString().contains("SNAPSHOT")

publishing {
   repositories {
      for (repositoryIndex in 0..10) {
         // @formatter:off
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
      register<MavenPublication>("${project.name}-all") {
         /*
          * Create a different artifact ID for the all package instead of using a classifier so that it doesn't get the same dependencies as the non uber jar version
          */
         artifact(tasks.named<ShadowJar>("shadowJar").get()) {
            classifier = ""
         }
         artifact(tasks.named("javadocJar").get())
         artifact(tasks.named("sourcesJar").get())

         groupId = project.group as String
         artifactId = project.name.toLowerCase() + "-all"
         version = project.version as String
         pom {
            name.set("Packr from libGdx")
            description.set("Forked version of libGdx Packr built and modified by Nimbly Games. This is the shadow (uber) jar version that can be executed with java -jar")
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
      register<MavenPublication>(project.name) {
         from(components["java"])
         artifactId = project.name.toLowerCase()
         pom {
            name.set("Packr from libGdx")
            description.set("Forked version of libGdx Packr built and modified by Nimbly Games")
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

signing.useGpgCmd()

if (isSnapshot) {
   logger.info("Skipping signing ")
} else {
   publishing.publications.configureEach {
      logger.info("Should sign publication ${this.name}")
      signing.sign(this)
   }
}
