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

import com.badlogicgames.packr.ArchiveUtils.ArchiveType;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.badlogicgames.packr.ArchiveUtils.ArchiveType.TAR;
import static com.badlogicgames.packr.ArchiveUtils.ArchiveType.ZIP;
import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for {@link ArchiveUtils}.
 */
class ArchiveUtilsTest {
	 private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	 /**
	  * Runs a simple test that creates an archive, extracts it and verifies the extracted output matches what was originally archived.
	  */
	 @Test public void testZipArchive (@TempDir Path tempDir) throws IOException, ArchiveException, CompressorException {
		  createAndExtractSimpleArchive(tempDir, ZIP);
	 }

	 /**
	  * Runs a simple test that creates a TAR archive, extracts it and verifies the extracted output matches what was originally archived.
	  */
	 @Test public void testTarArchive (@TempDir Path tempDir) throws IOException, ArchiveException, CompressorException {
		  createAndExtractSimpleArchive(tempDir, TAR);
	 }

	 private void createAndExtractSimpleArchive (Path tempDir, ArchiveType archiveType)
		 throws IOException, ArchiveException, CompressorException {
		  Path someDirectory = Files.createDirectories(tempDir.resolve("some-directory"));
		  String someFilename = "some-file.txt";
		  Files.write(someDirectory.resolve(someFilename), "Hello world\n".getBytes(StandardCharsets.UTF_8));
		  Path archiveTar = tempDir.resolve("archive");
		  ArchiveUtils.createArchive(archiveType, someDirectory, archiveTar);

		  Path extractionDirectory = tempDir.resolve("extract");
		  Files.createDirectories(extractionDirectory);
		  ArchiveUtils.extractArchive(archiveTar, extractionDirectory);

		  assertEquals(new String(Files.readAllBytes(someDirectory.resolve(someFilename)), StandardCharsets.UTF_8),
			  new String(Files.readAllBytes(extractionDirectory.resolve(someFilename)), StandardCharsets.UTF_8),
			  "Extracted file contents should have matched original");
	 }

	 /**
	  * Creates a Zip archive with a symbolic link, extracts it and verifies the extracted output matches what was originally archived.
	  */
	 @Test public void testSymbolicLinkZip (@TempDir Path tempDir) throws IOException, ArchiveException, CompressorException {
		  createAndExtractArchiveWithSymbolicLink(tempDir, ZIP);
	 }

	 /**
	  * Creates a Tar archive with a symbolic link, extracts it and verifies the extracted output matches what was originally archived.
	  */
	 @Test public void testSymbolicLinkTar (@TempDir Path tempDir) throws IOException, ArchiveException, CompressorException {
		  createAndExtractArchiveWithSymbolicLink(tempDir, TAR);
	 }

	 private void createAndExtractArchiveWithSymbolicLink (Path tempDir, ArchiveType archiveType)
		 throws IOException, ArchiveException, CompressorException {
		  assumeCreatedSymbolicLink(tempDir);

		  Path someDirectory = Files.createDirectories(tempDir.resolve("some-directory"));
		  String someFilename = "some-file.txt";
		  Path someFilePath = someDirectory.resolve(someFilename);
		  Files.write(someFilePath, "Hello world\n".getBytes(StandardCharsets.UTF_8));

		  String someSymbolicLinkFilename = "some-symbolic-link.txt";
		  Path someSymbolicLink = someDirectory.resolve(someSymbolicLinkFilename);
		  Files.createSymbolicLink(someSymbolicLink, someFilePath);

		  Path archiveZip = tempDir.resolve("archive");
		  ArchiveUtils.createArchive(archiveType, someDirectory, archiveZip);

		  Path extractionDirectory = tempDir.resolve("extract");
		  Files.createDirectories(extractionDirectory);
		  ArchiveUtils.extractArchive(archiveZip, extractionDirectory);

		  assertEquals(new String(Files.readAllBytes(someFilePath), StandardCharsets.UTF_8),
			  new String(Files.readAllBytes(extractionDirectory.resolve(someFilename)), StandardCharsets.UTF_8),
			  "Extracted file contents should have matched original");
		  assertTrue(Files.exists(extractionDirectory.resolve(someSymbolicLinkFilename)),
			  "Symbolic link wasn't created when extracting some-symbolic-link.txt" + ".");
		  assertTrue(Files.isSymbolicLink(extractionDirectory.resolve(someSymbolicLinkFilename)),
			  "Path some-symbolic-link.txt should be a symbolic link but it isn't.");
		  assertEquals(new String(Files.readAllBytes(extractionDirectory.resolve(someFilename)), StandardCharsets.UTF_8),
			  new String(Files.readAllBytes(extractionDirectory.resolve(someSymbolicLinkFilename)), StandardCharsets.UTF_8),
			  "Extracted file 'some-file.txt' and the symbolic link 'some-symbolic-link.txt' contents should have matched.");
		  assertTrue(Files.isSameFile(extractionDirectory.resolve(someFilename).toRealPath(),
			  extractionDirectory.resolve(someSymbolicLinkFilename).toRealPath()),
			  "The real path of the link=" + extractionDirectory.resolve(someSymbolicLinkFilename) + ", realpath=" + extractionDirectory
				  .resolve(someSymbolicLinkFilename).toRealPath() + " should have pointed to path=" + extractionDirectory.resolve(someFilename).toRealPath());
		  assertTrue(Files.isSameFile(extractionDirectory.resolve(someFilename), extractionDirectory.resolve(someSymbolicLinkFilename)),
			  "The extracted file some-file.txt and the symbolic link some-symbolic-link.txt should be the same file.");
	 }

	 private void assumeCreatedSymbolicLink (Path tempDir) throws IOException {
		  Path targetOfTestLink = tempDir.resolve("test-file.txt");
		  final Path linkPath = tempDir.resolve("test-link.txt");
		  Files.createFile(targetOfTestLink);
		  boolean createdLink = false;
		  try {
				Files.createSymbolicLink(linkPath, targetOfTestLink);
				createdLink = true;
		  } catch (Throwable throwable) {
				LOG.error("Failed to create a symbolic link.", throwable);
		  }
		  Files.deleteIfExists(targetOfTestLink);
		  Files.deleteIfExists(linkPath);
		  assumeTrue(createdLink, "Couldn't create a symbolic link, skipping test");
	 }

	 /**
	  * Adds the same entry to a Zip file to ensure that extraction handles duplicates properly.
	  */
	 @Test public void testArchiveDuplicateEntry (@TempDir Path tempDir)
		 throws IOException, ArchiveException, CompressorException {
		  String someFilename = "some-file.txt";
		  Path someFilePath = tempDir.resolve(someFilename);
		  Files.write(someFilePath, "Hello world\n".getBytes(StandardCharsets.UTF_8));
		  Path archiveZip = tempDir.resolve("archive.zip");

		  // Create an archive, add entry, update file, add same entry
		  try (OutputStream fileOutputStream = new BufferedOutputStream(Files.newOutputStream(archiveZip));
			  ArchiveOutputStream archiveOutputStream = new ArchiveStreamFactory()
				  .createArchiveOutputStream(ZIP.getCommonsCompressName(), fileOutputStream)) {

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

	 /**
	  * Tests extracting an externally created TAR.
	  * <pre>
	  * {@code
	  * tar -tvf test-symlink-permissions.tar
	  * drwxr-xr-x ksabo/ksabo       0 2020-10-02 23:35 some-dir/
	  * lrwxrwxrwx ksabo/ksabo       0 2020-10-02 23:35 some-dir/symlink-to-some-file.txt -> ../some-file.txt
	  * -rw-r--r-- ksabo/ksabo      12 2020-10-02 23:34 some-file.txt
	  * -rwxr-xr-x ksabo/ksabo      37 2020-10-02 23:36 some-script.sh
	  * }
	  * </pre>
	  */
	 @Test public void testExternalTarExtraction (@TempDir Path tempDir)
		 throws IOException, ArchiveException, CompressorException {
		  assumeCreatedSymbolicLink(tempDir);

		  Path archiveFilePath = tempDir.resolve("archive-file.tar");
		  Files.copy(ArchiveUtilsTest.class.getResourceAsStream("/test-symlink-permissions.tar"), archiveFilePath);
		  Path extractIntoPath = tempDir.resolve("extract-into");
		  ArchiveUtils.extractArchive(archiveFilePath, extractIntoPath);

		  Path someDir = extractIntoPath.resolve("some-dir");
		  Path symlinkToSomeFile = someDir.resolve("symlink-to-some-file.txt");
		  Path someFile = extractIntoPath.resolve("some-file.txt");
		  Path someScript = extractIntoPath.resolve("some-script.sh");

		  assertTrue(Files.exists(someDir), "some-dir wasn't extracted from the Tar");
		  assertTrue(Files.isDirectory(someDir), "some-dir wasn't extracted as a directory");
		  assertPosixPermissions(someDir, OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_READ, GROUP_EXECUTE, OTHERS_READ,
			  OTHERS_EXECUTE);

		  assertTrue(Files.exists(symlinkToSomeFile), "symlink-to-some-file.txt wasn't extracted from the Tar");
		  assertTrue(Files.isSymbolicLink(symlinkToSomeFile), "symlink-to-some-file.txt wasn't extracted as a symbolic link");
		  assertPosixPermissions(symlinkToSomeFile, OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_READ, GROUP_WRITE, GROUP_EXECUTE,
			  OTHERS_READ, OTHERS_WRITE, OTHERS_EXECUTE);

		  assertTrue(Files.exists(someFile), "some-file.txt wasn't extracted from the Tar");
		  assertTrue(Files.isRegularFile(someFile), "some-file.txt wasn't extracted as a file");
		  assertPosixPermissions(someFile, OWNER_READ, OWNER_WRITE, GROUP_READ, OTHERS_READ);

		  assertTrue(Files.exists(someScript), "some-script.sh wasn't extracted from the Tar");
		  assertTrue(Files.isRegularFile(someScript), "some-script.sh wasn't extracted as a file");
		  assertPosixPermissions(someScript, OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_READ, GROUP_EXECUTE, OTHERS_READ,
			  OTHERS_EXECUTE);
	 }

	 private void assertPosixPermissions (Path path, PosixFilePermission... permissions) throws IOException {
		  final PosixFileAttributeView fileAttributeView = Files
			  .getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
		  if (fileAttributeView == null) {
				return;
		  }
		  Set<PosixFilePermission> permissionSet = new LinkedHashSet<>(Arrays.asList(permissions));
		  assertEquals(permissionSet, fileAttributeView.readAttributes().permissions(),
			  "Permissions for path=" + path + ", don't match expected.");
	 }
}
