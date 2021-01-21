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

package com.badlogicgames.packr;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Some file utility wrappers to check for function results, and to raise exceptions in case of error.
 */
class PackrFileUtils {
	 /**
	  * Changes the file {@code path} to executable.
	  *
	  * @param path the path to the file to change to executable
	  */
	 static void chmodX (File path) {
		  if (!path.setExecutable(true)) {
				System.err.println("Warning! Failed setting executable flag for: " + path);
		  }
	 }

	 /**
	  * Copies directories, preserving file attributes.
	  *
	  * @param sourceDirectory the directory to copy from
	  * @param targetDirectory the directory to copy into
	  *
	  * @throws IOException if an IO error occurs
	  */
	 static void copyDirectory (File sourceDirectory, File targetDirectory) throws IOException {
		  final Path sourcePath = Paths.get(sourceDirectory.toURI()).toRealPath();
		  final Path targetPath = Paths.get(targetDirectory.toURI());

		  Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
				@Override public FileVisitResult preVisitDirectory (Path dir, BasicFileAttributes attrs) throws IOException {
					 Path relative = sourcePath.relativize(dir);
					 Path target = targetPath.resolve(relative);
					 Files.createDirectories(target);
					 return FileVisitResult.CONTINUE;
				}

				@Override public FileVisitResult visitFile (Path file, BasicFileAttributes attrs) throws IOException {
					 // symbolic links
					 if (attrs.isSymbolicLink()) {
						  final Path linkTargetPath = Files.readSymbolicLink(file);
						  final Path linkTargetRelativePath;
						  if (linkTargetPath.isAbsolute()) {
								linkTargetRelativePath = sourcePath.relativize(linkTargetPath);
						  } else {
								linkTargetRelativePath = linkTargetPath;
						  }
						  Files.createSymbolicLink(targetPath.resolve(sourcePath.relativize(file)), linkTargetRelativePath);
					 } else {
						  Path relative = sourcePath.relativize(file);
						  Path target = targetPath.resolve(relative);
						  Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
					 }
					 return FileVisitResult.CONTINUE;
				}
		  });
	 }

	 /**
	  * Deletes all the content of a directory and the directory itself.
	  *
	  * @param directory the directory to delete
	  *
	  * @throws IOException if an IO error occurs
	  */
	 static void deleteDirectory (File directory) throws IOException {
		  Files.walkFileTree(directory.toPath(), new SimpleFileVisitor<Path>() {
				@Override public FileVisitResult visitFile (Path file, BasicFileAttributes attrs) throws IOException {
					 Files.deleteIfExists(file);
					 return super.visitFile(file, attrs);
				}

				@Override public FileVisitResult postVisitDirectory (Path dir, IOException exc) throws IOException {
					 Files.deleteIfExists(dir);
					 return super.postVisitDirectory(dir, exc);
				}
		  });
	 }
}
