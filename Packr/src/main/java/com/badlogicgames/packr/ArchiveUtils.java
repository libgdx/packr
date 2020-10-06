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
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.jar.JarArchiveEntry;
import org.apache.commons.compress.archivers.jar.JarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.LONGFILE_GNU;

/**
 * Utility functions for working with archives.
 */
@SuppressWarnings("OctalInteger") public class ArchiveUtils {
	 public static final int DEFAULT_FILE_MODE = 00644;
	 public static final int DEFAULT_DIRECTORY_MODE = 00755;
	 public static final int DEFAULT_LINK_MODE = 00777;
	 public static final int ZIP_LINK_FLAG = 0120000;
	 public static final int OWNER_READ_BIT_MASK = 00400;
	 public static final int OWNER_WRITE_BIT_MASK = 00200;
	 public static final int OWNER_EXECUTE_BIT_MASK = 00100;
	 public static final int GROUP_READ_BIT_MASK = 00040;
	 public static final int GROUP_WRITE_BIT_MASK = 00020;
	 public static final int GROUP_EXECUTE_BIT_MASK = 00010;
	 public static final int OTHERS_READ_BIT_MASK = 00004;
	 public static final int OTHERS_WRITE_BIT_MASK = 00002;
	 public static final int OTHERS_EXECUTE_BIT_MASK = 00001;
	 private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	 /**
	  * No need for an instance, everything is static.
	  */
	 private ArchiveUtils () {
		  super();
	 }

	 /**
	  * Extracts an archive into {@code extractToDirectory}.
	  * <p>
	  * <b>NOTE:</b> Symbolic links are not handled.
	  *
	  * @param archivePath        the archive to extract
	  * @param extractToDirectory the directory to extract into
	  * @throws IOException         if an IO error occurs
	  * @throws CompressorException if a compression exception occurs
	  * @throws ArchiveException    if an archive exception occurs
	  */
	 public static void extractArchive (Path archivePath, Path extractToDirectory)
		 throws IOException, CompressorException, ArchiveException {
		  try (InputStream jdkInputStream = new BufferedInputStream(Files.newInputStream(archivePath))) {
				String compressorType = null;
				try {
					 compressorType = CompressorStreamFactory.detect(jdkInputStream);
				} catch (CompressorException exception) {
					 LOG.debug("Didn't detect any compression for archive " + archivePath + ": " + exception.getMessage());
				}
				InputStream decompressedJdkInputStream = jdkInputStream;
				if (compressorType != null) {
					 decompressedJdkInputStream = new BufferedInputStream(
						 CompressorStreamFactory.getSingleton().createCompressorInputStream(compressorType, jdkInputStream));
				}

				switch (ArchiveStreamFactory.detect(decompressedJdkInputStream)) {
				case ArchiveStreamFactory.ZIP:
					 if (compressorType != null) {
						  LOG.error("Cannot extract Zip archives that are wrapped in additional compression");
					 } else {
						  extractZipArchive(archivePath, extractToDirectory);
					 }
					 break;
				case ArchiveStreamFactory.JAR:
					 extractJarArchive(decompressedJdkInputStream, extractToDirectory);
					 break;
				case ArchiveStreamFactory.TAR:
					 extractTarArchive(decompressedJdkInputStream, extractToDirectory);
					 break;
				default:
					 LOG.error("No special handling for archive type " + archivePath
						 + ". Permissions and links will not be properly handled.");
					 extractGenericArchive(decompressedJdkInputStream, extractToDirectory);
					 break;
				}
		  }
	 }

	 /**
	  * Extracts an archive using {@link ArchiveStreamFactory#createArchiveInputStream(InputStream)} with no special handling of symbolic links or file
	  * permissions.
	  *
	  * @param inputStream        the archive input stream
	  * @param extractToDirectory the directory to extract the archive into
	  * @throws ArchiveException if an archive error occurs
	  * @throws IOException      if an IO error occurs
	  */
	 private static void extractGenericArchive (InputStream inputStream, Path extractToDirectory)
		 throws ArchiveException, IOException {
		  final ArchiveInputStream archiveInputStream = new ArchiveStreamFactory().createArchiveInputStream(inputStream);

		  ArchiveEntry entry;
		  while ((entry = archiveInputStream.getNextEntry()) != null) {
				if (!archiveInputStream.canReadEntryData(entry)) {
					 LOG.error("Failed to read archive entry " + entry);
					 continue;
				}

				Path entryExtractPath = extractToDirectory.resolve(getEntryAsPath(entry));
				if (entry.isDirectory()) {
					 Files.createDirectories(entryExtractPath);
				} else {
					 Files.createDirectories(entryExtractPath.getParent());
					 Files.copy(archiveInputStream, entryExtractPath, StandardCopyOption.REPLACE_EXISTING);
				}
				Files.setLastModifiedTime(entryExtractPath, FileTime.fromMillis(entry.getLastModifiedDate().getTime()));
		  }
	 }

	 /**
	  * Extracts a TAR archive. If the current platform supports POSIX permissions, the archive entry permissions are applied to the create file or directory.
	  * Symbolic and "hard" links are also support.
	  *
	  * @param inputStream        the archive input stream
	  * @param extractToDirectory the directory to extract the archive into
	  * @throws IOException if an IO error occurs
	  */
	 private static void extractTarArchive (InputStream inputStream, Path extractToDirectory) throws IOException {
		  final TarArchiveInputStream archiveInputStream = new TarArchiveInputStream(inputStream);

		  TarArchiveEntry entry;
		  while ((entry = archiveInputStream.getNextTarEntry()) != null) {
				if (!archiveInputStream.canReadEntryData(entry)) {
					 LOG.error("Failed to read archive entry " + entry);
					 continue;
				}

				Path entryExtractPath = extractToDirectory.resolve(getEntryAsPath(entry));

				if (entry.isLink()) {
					 Path linkTarget = Paths.get(entry.getLinkName());
					 Files.deleteIfExists(entryExtractPath);
					 Files.createLink(entryExtractPath, linkTarget);
				} else if (entry.isSymbolicLink()) {
					 Path linkTarget = Paths.get(entry.getLinkName());
					 Files.deleteIfExists(entryExtractPath);
					 Files.createSymbolicLink(entryExtractPath, linkTarget);
				} else {
					 if (entry.isDirectory()) {
						  Files.createDirectories(entryExtractPath);
					 } else {
						  Files.createDirectories(entryExtractPath.getParent());
						  Files.copy(archiveInputStream, entryExtractPath, StandardCopyOption.REPLACE_EXISTING);
					 }
				}
				setLastModifiedTime(entryExtractPath, FileTime.fromMillis(entry.getLastModifiedDate().getTime()));
				Set<PosixFilePermission> permissions = getPosixFilePermissions(entry);
				setPosixPermissions(entryExtractPath, permissions);
		  }
	 }

	 private static Set<PosixFilePermission> getPosixFilePermissions (final TarArchiveEntry entry) {
		  int mode = entry.getMode();
		  if (mode == 0) {
				if (entry.isSymbolicLink()) {
					 mode = DEFAULT_LINK_MODE;
				} else if (entry.isDirectory()) {
					 mode = DEFAULT_DIRECTORY_MODE;
				} else {
					 mode = DEFAULT_FILE_MODE;
				}
		  }
		  return getPosixFilePermissions(mode);
	 }

	 private static Set<PosixFilePermission> getPosixFilePermissions (final ZipArchiveEntry entry) {
		  int mode = entry.getUnixMode();
		  if (mode == 0) {
				if (entry.isUnixSymlink()) {
					 mode = DEFAULT_LINK_MODE;
				} else if (entry.isDirectory()) {
					 mode = DEFAULT_DIRECTORY_MODE;
				} else {
					 mode = DEFAULT_FILE_MODE;
				}
		  }
		  return getPosixFilePermissions(mode);
	 }

	 /**
	  * Converts a bit masked integer into a set of {@link PosixFilePermission}s.
	  *
	  * @param mode the permissions bit mask
	  * @return a set of permission enums based on {@code mode}
	  * @see #OWNER_READ_BIT_MASK
	  */
	 private static Set<PosixFilePermission> getPosixFilePermissions (final int mode) {
		  Set<PosixFilePermission> permissions = new HashSet<>();
		  if ((mode & OWNER_READ_BIT_MASK) != 0) {
				permissions.add(PosixFilePermission.OWNER_READ);
		  }
		  if ((mode & OWNER_WRITE_BIT_MASK) != 0) {
				permissions.add(PosixFilePermission.OWNER_WRITE);
		  }
		  if ((mode & OWNER_EXECUTE_BIT_MASK) != 0) {
				permissions.add(PosixFilePermission.OWNER_EXECUTE);
		  }
		  if ((mode & GROUP_READ_BIT_MASK) != 0) {
				permissions.add(PosixFilePermission.GROUP_READ);
		  }
		  if ((mode & GROUP_WRITE_BIT_MASK) != 0) {
				permissions.add(PosixFilePermission.GROUP_WRITE);
		  }
		  if ((mode & GROUP_EXECUTE_BIT_MASK) != 0) {
				permissions.add(PosixFilePermission.GROUP_EXECUTE);
		  }
		  if ((mode & OTHERS_READ_BIT_MASK) != 0) {
				permissions.add(PosixFilePermission.OTHERS_READ);
		  }
		  if ((mode & OTHERS_WRITE_BIT_MASK) != 0) {
				permissions.add(PosixFilePermission.OTHERS_WRITE);
		  }
		  if ((mode & OTHERS_EXECUTE_BIT_MASK) != 0) {
				permissions.add(PosixFilePermission.OTHERS_EXECUTE);
		  }
		  return permissions;
	 }

	 /**
	  * If the current platform supports POSIX permissions, they are applied to {@code path}.
	  *
	  * @param path        the path to apply {@code permissions} on if the current platform supports POSIX permissions
	  * @param permissions the permissions to apply to {@code path} if the current platform supports POSIX permissions
	  * @throws IOException if an IO error occurs
	  */
	 private static void setPosixPermissions (Path path, Set<PosixFilePermission> permissions) throws IOException {
		  if (Files.isSymbolicLink(path)) {
				return;
		  }
		  final PosixFileAttributeView posixFileAttributeView = Files.getFileAttributeView(path, PosixFileAttributeView.class, NOFOLLOW_LINKS);
		  if (posixFileAttributeView != null) {
				posixFileAttributeView.setPermissions(permissions);
		  }
	 }

	 /**
	  * Extracts a JAR archive. If the current platform supports POSIX permissions, the archive entry permissions are applied to the created file or directory.
	  * Symbolic links are also supported.
	  *
	  * @param inputStream        the archive input stream
	  * @param extractToDirectory the directory to extract the archive into
	  * @throws IOException if an IO error occurs
	  */
	 private static void extractJarArchive (InputStream inputStream, Path extractToDirectory) throws IOException {
		  final JarArchiveInputStream archiveInputStream = new JarArchiveInputStream(inputStream);

		  JarArchiveEntry entry;
		  while ((entry = archiveInputStream.getNextJarEntry()) != null) {
				if (!archiveInputStream.canReadEntryData(entry)) {
					 LOG.error("Failed to read archive entry " + entry);
					 continue;
				}
				extractZipEntry(extractToDirectory, archiveInputStream, entry);
		  }
	 }

	 /**
	  * Extracts a {@link ZipArchiveEntry}, creating files and directories that match the date modified, POSIX permissions, and symbolic link properties of the
	  * archive entry.
	  *
	  * @param extractToDirectory the directory to extract to
	  * @param archiveInputStream the archive input stream
	  * @param entry              the entry to extract
	  * @throws IOException if an IO error occurs
	  */
	 private static void extractZipEntry (Path extractToDirectory, InputStream archiveInputStream, ZipArchiveEntry entry)
		 throws IOException {
		  Path entryExtractPath = extractToDirectory.resolve(getEntryAsPath(entry));

		  if (entry.isUnixSymlink()) {
				final byte[] contentBuffer = new byte[8192];
				final int contentLength = IOUtils.readFully(archiveInputStream, contentBuffer);
				Path linkTarget = Paths.get(new String(contentBuffer, 0, contentLength, StandardCharsets.UTF_8));
				Files.deleteIfExists(entryExtractPath);
				Files.createSymbolicLink(entryExtractPath, linkTarget);
		  } else {
				if (entry.isDirectory()) {
					 Files.createDirectories(entryExtractPath);
				} else {
					 Files.createDirectories(entryExtractPath.getParent());
					 Files.copy(archiveInputStream, entryExtractPath, StandardCopyOption.REPLACE_EXISTING);
				}
		  }
		  setLastModifiedTime(entryExtractPath, entry.getLastModifiedTime());
		  Set<PosixFilePermission> permissions = getPosixFilePermissions(entry);
		  setPosixPermissions(entryExtractPath, permissions);
	 }

	 private static void setLastModifiedTime (Path path, FileTime lastModifiedTime) throws IOException {
		  if (Files.isSymbolicLink(path)) {
				return;
		  }
		  BasicFileAttributeView pathAttributeView = Files.getFileAttributeView(path, BasicFileAttributeView.class, NOFOLLOW_LINKS);
		  final BasicFileAttributes fileAttributes = pathAttributeView.readAttributes();
		  pathAttributeView.setTimes(lastModifiedTime, fileAttributes.lastAccessTime(), fileAttributes.creationTime());
	 }

	 private static Path getEntryAsPath (ArchiveEntry entry) throws IOException {
		  Path entryAsPath = Paths.get(entry.getName());
		  if (entryAsPath.isAbsolute()) {
				throw new IOException("Archive contained an absolute path as an entry");
		  }
		  return entryAsPath;
	 }

	 /**
	  * Extracts a Zip archive. If the current platform supports POSIX permissions, the archive entry permissions are applied to the created file or directory.
	  * Symbolic links are also supported.
	  *
	  * @param archivePath        the Zip archive path
	  * @param extractToDirectory the directory to extract the archive into
	  * @throws IOException if an IO error occurs
	  */
	 private static void extractZipArchive (Path archivePath, Path extractToDirectory) throws IOException {
		  try (final ZipFile zipFile = new ZipFile(archivePath.toFile())) {
				Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
				while (entries.hasMoreElements()) {
					 ZipArchiveEntry entry = entries.nextElement();
					 try (InputStream entryInputStream = zipFile.getInputStream(entry)) {
						  extractZipEntry(extractToDirectory, entryInputStream, entry);
					 }
				}
		  }
	 }

	 /**
	  * Creates a new archive from the contents in {@code directoryToArchive}.
	  *
	  * @param archiveType        the type of archive to create
	  * @param directoryToArchive the directory to archive the contents of
	  * @param archiveFile        the file to write the archive to
	  * @throws IOException      if an IO error occurs
	  * @throws ArchiveException if an archive error occurs
	  */
	 public static void createArchive (ArchiveType archiveType, Path directoryToArchive, Path archiveFile)
		 throws IOException, ArchiveException {
		  try (OutputStream fileOutputStream = new BufferedOutputStream(Files.newOutputStream(archiveFile));
			  ArchiveOutputStream archiveOutputStream = new ArchiveStreamFactory()
				  .createArchiveOutputStream(archiveType.getCommonsCompressName(), fileOutputStream)) {

				if (archiveType == ArchiveType.TAR) {
					 ((TarArchiveOutputStream)archiveOutputStream).setLongFileMode(LONGFILE_GNU);
				}

				Files.walkFileTree(directoryToArchive, new SimpleFileVisitor<Path>() {
					 @Override public FileVisitResult visitFile (Path file, BasicFileAttributes attrs) throws IOException {
						  createAndPutArchiveEntry(archiveType, archiveOutputStream, directoryToArchive, file);
						  archiveOutputStream.closeArchiveEntry();
						  return FileVisitResult.CONTINUE;
					 }

					 @Override public FileVisitResult preVisitDirectory (Path dir, BasicFileAttributes attrs) throws IOException {
						  if (Files.isSameFile(dir, directoryToArchive)) {
								return FileVisitResult.CONTINUE;
						  }

						  ArchiveEntry entry = archiveOutputStream
							  .createArchiveEntry(dir.toFile(), getEntryName(dir, directoryToArchive));
						  archiveOutputStream.putArchiveEntry(entry);
						  archiveOutputStream.closeArchiveEntry();
						  return FileVisitResult.CONTINUE;
					 }
				});

				archiveOutputStream.finish();
		  }
	 }

	 private static void createAndPutArchiveEntry (ArchiveType archiveType, ArchiveOutputStream archiveOutputStream,
		 Path directoryToArchive, Path filePathToArchive) throws IOException {
		  switch (archiveType) {
		  case ZIP: {
				ZipArchiveEntry entry = new ZipArchiveEntry(filePathToArchive.toFile(),
					getEntryName(filePathToArchive, directoryToArchive));
				entry.setUnixMode(getUnixMode(filePathToArchive));
				final boolean isSymbolicLink = Files.isSymbolicLink(filePathToArchive);
				if (isSymbolicLink) {
					 entry.setUnixMode(entry.getUnixMode() | ZIP_LINK_FLAG);
				}
				archiveOutputStream.putArchiveEntry(entry);
				if (isSymbolicLink) {
					 archiveOutputStream.write(
						 directoryToArchive.relativize(filePathToArchive.toRealPath()).toString().getBytes(StandardCharsets.UTF_8));
				} else {
					 Files.copy(filePathToArchive, archiveOutputStream);
				}
				break;
		  }
		  case TAR: {
				final boolean isSymbolicLink = Files.isSymbolicLink(filePathToArchive);
				TarArchiveEntry entry;
				if (isSymbolicLink) {
					 entry = new TarArchiveEntry(getEntryName(filePathToArchive, directoryToArchive), TarConstants.LF_SYMLINK);
					 entry.setLinkName(directoryToArchive.relativize(filePathToArchive.toRealPath()).toString());
				} else {
					 entry = new TarArchiveEntry(filePathToArchive.toFile(), getEntryName(filePathToArchive, directoryToArchive));
				}

				entry.setMode(getUnixMode(filePathToArchive));
				archiveOutputStream.putArchiveEntry(entry);

				if (!isSymbolicLink) {
					 Files.copy(filePathToArchive, archiveOutputStream);
				}
				break;
		  }
		  }
	 }

	 private static int getUnixMode (Path file) throws IOException {
		  PosixFileAttributeView fileAttributeView = Files.getFileAttributeView(file, PosixFileAttributeView.class, NOFOLLOW_LINKS);
		  if (fileAttributeView == null) {
				if (Files.isSymbolicLink(file)) {
					 return DEFAULT_LINK_MODE;
				} else if (Files.isDirectory(file)) {
					 return DEFAULT_DIRECTORY_MODE;
				}
				return DEFAULT_FILE_MODE;
		  }
		  int mode = 0;
		  Set<PosixFilePermission> permissions = fileAttributeView.readAttributes().permissions();
		  if (permissions.contains(PosixFilePermission.OWNER_READ)) {
				mode |= OWNER_READ_BIT_MASK;
		  }
		  if (permissions.contains(PosixFilePermission.OWNER_WRITE)) {
				mode |= OWNER_WRITE_BIT_MASK;
		  }
		  if (permissions.contains(PosixFilePermission.OWNER_EXECUTE)) {
				mode |= OWNER_EXECUTE_BIT_MASK;
		  }
		  if (permissions.contains(PosixFilePermission.GROUP_READ)) {
				mode |= GROUP_READ_BIT_MASK;
		  }
		  if (permissions.contains(PosixFilePermission.GROUP_WRITE)) {
				mode |= GROUP_WRITE_BIT_MASK;
		  }
		  if (permissions.contains(PosixFilePermission.GROUP_EXECUTE)) {
				mode |= GROUP_EXECUTE_BIT_MASK;
		  }
		  if (permissions.contains(PosixFilePermission.OTHERS_READ)) {
				mode |= OTHERS_READ_BIT_MASK;
		  }
		  if (permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
				mode |= OTHERS_WRITE_BIT_MASK;
		  }
		  if (permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) {
				mode |= OTHERS_EXECUTE_BIT_MASK;
		  }
		  LOG.debug("Unix mode of file=" + file + ", mode=" + Integer.toOctalString(mode) + ", permissions=" + permissions);
		  return mode;
	 }

	 /**
	  * Creates a relative entry name and replaces all backslashes with forward slash.
	  *
	  * @param path the path to make relative to {@code rootDirectory}
	  * @param rootDirectory the root directory to use to generate the relative entry name
	  *
	  * @return the entry name ({@code path} relative to {@code rootDirectory} with backslashes replaced)
	  */
	 private static String getEntryName (Path path, Path rootDirectory) throws IOException {
		  final Path rootDirectoryRealPath = rootDirectory.toRealPath(NOFOLLOW_LINKS);
		  final Path pathRealPath = path.toRealPath(NOFOLLOW_LINKS);
		  LOG.debug("Creating relative path for pathRealPath=" + pathRealPath + " using rootDirectoryRealPath=" + rootDirectoryRealPath + ".");
		  String entryName = rootDirectoryRealPath.relativize(pathRealPath).toString().replaceAll("\\\\", "/");
		  LOG.error("Creating entry name from path=" + path + ", rootDirectory=" + rootDirectory + ", entryName=" + entryName);
		  return entryName;
	 }

	 /**
	  * Archive types available for creation.
	  */
	 public enum ArchiveType {
		  /**
			* A Zip archive.
			*/
		  ZIP(ArchiveStreamFactory.ZIP),

		  /**
			* A TAR archive.
			*/
		  TAR(ArchiveStreamFactory.TAR);

		  private final String commonsCompressName;

		  /**
			* Create a new ArchiveType enum with the given name that maps into {@link ArchiveStreamFactory}.
			*
			* @param commonsCompressName the matching name from {@link ArchiveStreamFactory}
			*/
		  ArchiveType (final String commonsCompressName) {
				this.commonsCompressName = commonsCompressName;
		  }

		  /**
			* The archive name to use in {@link ArchiveStreamFactory}.
			*
			* @return the archive name
			*/
		  public String getCommonsCompressName () {
				return commonsCompressName;
		  }
	 }
}
