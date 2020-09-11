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

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.badlogicgames.packr.ArchiveUtils.ArchiveType.ZIP;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link ArchiveUtils}.
 */
class ArchiveUtilsTest {
	 /**
	  * Runs a simple test that creates an archive, extracts it and verifies the extracted output matches what was originally archived.
	  */
	 @Test public void testArchive (@TempDir Path tempDir) throws IOException, ArchiveException, CompressorException {
		  Path someDirectory = Files.createDirectories(tempDir.resolve("some-directory"));
		  String someFilename = "some-file.txt";
		  Files.write(someDirectory.resolve(someFilename), "Hello world\n".getBytes(StandardCharsets.UTF_8));
		  Path archiveZip = tempDir.resolve("archive.zip");
		  ArchiveUtils.createArchive(ZIP, someDirectory, archiveZip);

		  Path extractionDirectory = tempDir.resolve("extract");
		  Files.createDirectories(extractionDirectory);
		  ArchiveUtils.extractArchive(archiveZip, extractionDirectory);

		  assertEquals(new String(Files.readAllBytes(someDirectory.resolve(someFilename)), StandardCharsets.UTF_8),
			  new String(Files.readAllBytes(extractionDirectory.resolve(someFilename)), StandardCharsets.UTF_8),
			  "Extracted file contents should have matched original");
	 }

	 /**
	  * Adds the same entry to a Zip file to ensure that extraction handles duplicates properly.
	  */
	 @Test public void testArchiveDuplicateEntry (@TempDir Path tempDir) throws IOException, ArchiveException, CompressorException {
		  String someFilename = "some-file.txt";
		  Path someFilePath = tempDir.resolve(someFilename);
		  Files.write(someFilePath, "Hello world\n".getBytes(StandardCharsets.UTF_8));
		  Path archiveZip = tempDir.resolve("archive.zip");

		  // Create an archive, add entry, update file, add same entry
		  try (OutputStream fileOutputStream = new BufferedOutputStream(Files.newOutputStream(archiveZip));
			  ArchiveOutputStream archiveOutputStream = new ArchiveStreamFactory().createArchiveOutputStream(ZIP.getCommonsCompressName(), fileOutputStream)) {

				// Create an entry for some file
				ArchiveEntry entry = archiveOutputStream.createArchiveEntry(someFilePath.toFile(), someFilename);
				archiveOutputStream.putArchiveEntry(entry);
				Files.copy(someFilePath, archiveOutputStream);
				archiveOutputStream.closeArchiveEntry();

				// Update some file, and put it into the archive again
				Files.write(someFilePath, "Good bye\n".getBytes(StandardCharsets.UTF_8));
				entry = archiveOutputStream.createArchiveEntry(someFilePath.toFile(), someFilename);
				archiveOutputStream.putArchiveEntry(entry);
				Files.copy(someFilePath, archiveOutputStream);
				archiveOutputStream.closeArchiveEntry();

				archiveOutputStream.finish();
		  }

		  Path extractionDirectory = tempDir.resolve("extract");
		  Files.createDirectories(extractionDirectory);
		  ArchiveUtils.extractArchive(archiveZip, extractionDirectory);

		  assertEquals(new String(Files.readAllBytes(tempDir.resolve(someFilename)), StandardCharsets.UTF_8),
			  new String(Files.readAllBytes(extractionDirectory.resolve(someFilename)), StandardCharsets.UTF_8),
			  "Extracted file contents should have matched original");
	 }
}
