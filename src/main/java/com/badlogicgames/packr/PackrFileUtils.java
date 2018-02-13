/*******************************************************************************
 * Copyright 2014 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogicgames.packr;

import org.zeroturnaround.zip.commons.FileUtilsV2_2;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Some file utility wrappers to check for function results, and to raise exceptions in case of error.
 */
class PackrFileUtils {

  static void mkdirs(File path) throws IOException {
    if (!path.mkdirs()) {
      throw new IOException("Can't create folder(s): " + path);
    }
  }

  static void chmodX(File path) {
    if (!path.setExecutable(true)) {
      System.err.println("Warning! Failed setting executable flag for: " + path);
    }
  }

  static void delete(File path) throws IOException {
    if (!path.delete()) {
      throw new IOException("Can't delete file or folder: " + path);
    }
  }

  /**
   * Copies directories, preserving file attributes.
   *
   * The {@link org.zeroturnaround.zip.commons.FileUtilsV2_2#copyDirectory(File, File)} function does not
   * preserve file attributes.
   */
  static void copyDirectory(File sourceDirectory, File targetDirectory) throws IOException {

    final Path sourcePath = Paths.get(sourceDirectory.toURI()).toRealPath();
    final Path targetPath = Paths.get(targetDirectory.toURI());

    Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        Path relative = sourcePath.relativize(dir);
        Path target = targetPath.resolve(relative);
        File folder = target.toFile();
        if (!folder.exists()) {
          mkdirs(folder);
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Path relative = sourcePath.relativize(file);
        Path target = targetPath.resolve(relative);
        Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  static void deleteDirectory(File directory) throws IOException {
    FileUtilsV2_2.deleteDirectory(directory);
  }

  static void copyFile(File source, File target) throws IOException {
    Path sourcePath = Paths.get(source.toURI());
    Path targetPath = Paths.get(target.toURI());
    Files.copy(sourcePath, targetPath, StandardCopyOption.COPY_ATTRIBUTES);
  }

}
