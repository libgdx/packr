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

package com.libgdx.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import java.net.URI

/**
 * URI for the GitHub Packr Maven repository.
 */
val gitHubPackrMavenUri: URI = URI("https://maven.pkg.github.com/libgdx/packr")

/**
 * Username for signing into GitHub Maven packages url [gitHubPackrMavenUri]. The username is loaded from the Gradle property or
 * environment variable `PACKR_GITHUB_MAVEN_USERNAME`.
 */
val Project.gitHubMavenUsername: String?
   get() {
      return findProperty("PACKR_GITHUB_MAVEN_USERNAME") as String? ?: System.getenv("PACKR_GITHUB_MAVEN_USERNAME")
   }

/**
 * Authentication token for signing into GitHub Maven packages url [gitHubPackrMavenUri]. The token is loaded from the Gradle
 * property or environment variable `PACKR_GITHUB_MAVEN_TOKEN`
 */
val Project.gitHubMavenToken: String?
   get() = this.findProperty("PACKR_GITHUB_MAVEN_TOKEN") as String? ?: System.getenv("PACKR_GITHUB_MAVEN_TOKEN")

/**
 * Adds the GitHub Maven repository for [gitHubPackrMavenUri] only if the [gitHubMavenUsername] is non null.
 */
fun RepositoryHandler.gitHubRepositoryForPackr(project: Project) {
   if (project.gitHubMavenUsername != null) {
      maven {
         url = gitHubPackrMavenUri
         credentials {
            username = project.gitHubMavenUsername
            password = project.gitHubMavenToken
         }
      }
   }
}

/**
 * Searches for Gradle properties of Maven repositories to publish to.
 *
 * The properties are:
 * * `maven.repository.url.n=<url>` // This is the Maven repository url
 * * `maven.repository.ispublishsnapshot.n=<boolean>` // true if snapshot builds should be published to this repository
 * * `maven.repository.ispublishrelease.n=<boolean>` // true if release builds should be published to this repository
 * * `maven.repository.ispublishpackr.n=<boolean>` // true if packr builds should be published to this repository
 * * `maven.repository.username.n=<username>` // The username for authenticating to the Maven repository
 * * `maven.repository.password.n=<password` // The token or password for authenticating to the Maven repository
 *
 * Example:
 * ```text
 * maven.repository.url.1=https://oss.sonatype.org/content/repositories/snapshots
 * maven.repository.username.1=tstark
 * maven.repository.password.1=mark42
 * maven.repository.ispublishsnapshot.1=true
 * maven.repository.ispublishrelease.1=false
 * maven.repository.ispublishpackr.1=true
 * ```
 */
fun RepositoryHandler.packrPublishRepositories(project: Project) {
   for (repositoryIndex in 0..10) {
      // @off
      if (project.hasProperty("maven.repository.url.$repositoryIndex")
          && ((project.findProperty("maven.repository.ispublishsnapshot.$repositoryIndex").toString().toBoolean() && project.isSnapshot)
              || (project.findProperty("maven.repository.ispublishrelease.$repositoryIndex").toString().toBoolean() && !project.isSnapshot))
          && project.findProperty("maven.repository.ispublishpackr.$repositoryIndex").toString().toBoolean()) {
         // @on
         maven {
            url = URI(project.findProperty("maven.repository.url.$repositoryIndex") as String)
            credentials {
               username = project.findProperty("maven.repository.username.$repositoryIndex") as String
               password = project.findProperty("maven.repository.password.$repositoryIndex") as String
            }
         }
      }
   }
}
