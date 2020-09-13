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

package com.badlogicgames.packr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class PackrTest {

	 @Test void verifyEmptyOrCreateOutputFolderCreated (@TempDir Path tempDirectoryPath) throws IOException {
		  Packr packr = new Packr();
		  Path outputPath = tempDirectoryPath.resolve("output");
		  PackrOutput packrOutput = new PackrOutput(outputPath.toFile(), outputPath.toFile());
		  packr.verifyEmptyOrCreateOutputFolder(packrOutput);
		  assertTrue(Files.exists(outputPath));
	 }

	 @Test void verifyEmptyOrCreateOutputFolderEmpty (@TempDir Path tempDirectoryPath) throws IOException {
		  Packr packr = new Packr();
		  Path outputPath = tempDirectoryPath.resolve("output");
		  Files.createDirectories(outputPath);
		  PackrOutput packrOutput = new PackrOutput(outputPath.toFile(), outputPath.toFile());
		  packr.verifyEmptyOrCreateOutputFolder(packrOutput);
		  assertTrue(Files.exists(outputPath));
		  try (Stream<Path> walk = Files.walk(outputPath, 1)) {
				assertEquals(walk.count(), 1);
		  }
	 }

	 @Test void verifyEmptyOrCreateOutputFolderThrowsNotEmpty (@TempDir Path tempDirectoryPath) throws IOException {
		  Packr packr = new Packr();
		  Path outputPath = tempDirectoryPath.resolve("output");
		  Files.createDirectories(outputPath);
		  Path someFileThatIsInOutputDir = outputPath.resolve("someFile.txt");
		  Files.write(someFileThatIsInOutputDir, "Hello world!".getBytes(StandardCharsets.UTF_8));
		  PackrOutput packrOutput = new PackrOutput(outputPath.toFile(), outputPath.toFile());
		  try {
				packr.verifyEmptyOrCreateOutputFolder(packrOutput);
		  } catch (IOException exception) {
				assertTrue(exception.getMessage().contains("is not empty"));
				return;
		  }
		  fail("Should have thrown a not empty exception");
	 }

	 @Test void verifyEmptyOrCreateOutputFolderThrowsIsFile (@TempDir Path tempDirectoryPath) throws IOException {
		  Packr packr = new Packr();
		  Path outputPath = tempDirectoryPath.resolve("output");
		  Files.write(outputPath, "Hello world!".getBytes(StandardCharsets.UTF_8));
		  PackrOutput packrOutput = new PackrOutput(outputPath.toFile(), outputPath.toFile());
		  try {
				packr.verifyEmptyOrCreateOutputFolder(packrOutput);
		  } catch (IOException exception) {
				assertTrue(exception.getMessage().contains("is not a directory"));
				return;
		  }
		  fail("Should have thrown a not empty exception");
	 }
}
