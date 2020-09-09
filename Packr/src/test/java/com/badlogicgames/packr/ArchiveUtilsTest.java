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

import com.badlogicgames.packr.ArchiveUtils.ArchiveType;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.compressors.CompressorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link ArchiveUtils}.
 */
class ArchiveUtilsTest {
   /**
    * Runs a simple test that creates an archive, extracts it and verifies the extracted output matches what was originally archived.
    */
   @SuppressWarnings("JavaDoc") @Test public void testArchive(@TempDir Path tempDir) throws IOException, ArchiveException, CompressorException {
      Path someDirectory = Files.createDirectories(tempDir.resolve("some-directory"));
      String someFilename = "some-file.txt";
      Files.write(someDirectory.resolve(someFilename), "Hello world\n".getBytes(StandardCharsets.UTF_8));
      Path archiveZip = tempDir.resolve("archive.zip");
      ArchiveUtils.createArchive(ArchiveType.ZIP, someDirectory, archiveZip);

      Path extractionDirectory = tempDir.resolve("extract");
      Files.createDirectories(extractionDirectory);
      ArchiveUtils.extractArchive(archiveZip, extractionDirectory);

      assertEquals(new String(Files.readAllBytes(someDirectory.resolve(someFilename)), StandardCharsets.UTF_8),
            new String(Files.readAllBytes(extractionDirectory.resolve(someFilename)), StandardCharsets.UTF_8),
            "Extracted file contents should have matched original");
   }
}