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

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Utility functions for working with archives.
 */
public class ArchiveUtils {
   /**
    * No need for an instance, everything is static.
    */
   private ArchiveUtils() {
      super();
   }

   /**
    * Extracts and archive into {@code extractToDirectory}.
    *
    * @param archivePath the archive to extract
    * @param extractToDirectory the directory to extract into
    *
    * @throws IOException if an IO error occurs
    * @throws CompressorException if a compression exception occurs
    * @throws ArchiveException if an archive exception occurs
    */
   public static void extractArchive(Path archivePath, Path extractToDirectory) throws IOException, CompressorException, ArchiveException {
      try (InputStream jdkInputStream = new BufferedInputStream(Files.newInputStream(archivePath))) {
         String compressorType = null;
         try {
            compressorType = CompressorStreamFactory.detect(jdkInputStream);
         } catch (CompressorException exception) {
            System.out.println("Didn't detect any compression for archive " + archivePath + ": " + exception.getMessage());
         }
         InputStream decompressedJdkInputStream = jdkInputStream;
         if (compressorType != null) {
            decompressedJdkInputStream = CompressorStreamFactory.getSingleton().createCompressorInputStream(compressorType, jdkInputStream);
         }

         final ArchiveInputStream archiveInputStream = new ArchiveStreamFactory().createArchiveInputStream(decompressedJdkInputStream);
         ArchiveEntry entry;
         while ((entry = archiveInputStream.getNextEntry()) != null) {
            if (!archiveInputStream.canReadEntryData(entry)) {
               System.out.println("Failed to read archive entry " + entry);
               continue;
            }
            Path entryExtractPath = extractToDirectory.resolve(entry.getName());
            if (entry.isDirectory()) {
               Files.createDirectories(entryExtractPath);
            } else {
               Files.createDirectories(entryExtractPath.getParent());
               Files.copy(archiveInputStream, entryExtractPath);
            }
         }
      }

   }

   /**
    * Creates a new archive from the contents in {@code directoryToArchive}.
    *
    * @param archiveType the type of archive to create
    * @param directoryToArchive the directory to archive the contents of
    * @param archiveFile the file to write the archive to
    *
    * @throws IOException if an IO error occurs
    * @throws ArchiveException if an archive error occurs
    */
   public static void createArchive(ArchiveType archiveType, Path directoryToArchive, Path archiveFile) throws IOException, ArchiveException {
      try (OutputStream fileOutputStream = new BufferedOutputStream(Files.newOutputStream(archiveFile));
           ArchiveOutputStream archiveOutputStream = new ArchiveStreamFactory().createArchiveOutputStream(archiveType.getCommonsCompressName(),
                 fileOutputStream)) {

         Files.walkFileTree(directoryToArchive, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
               ArchiveEntry entry = archiveOutputStream.createArchiveEntry(file.toFile(), getEntryName(file, directoryToArchive));
               archiveOutputStream.putArchiveEntry(entry);
               Files.copy(file, archiveOutputStream);
               archiveOutputStream.closeArchiveEntry();
               return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
               ArchiveEntry entry = archiveOutputStream.createArchiveEntry(dir.toFile(), getEntryName(dir, directoryToArchive));
               archiveOutputStream.putArchiveEntry(entry);
               archiveOutputStream.closeArchiveEntry();
               return FileVisitResult.CONTINUE;
            }
         });

         archiveOutputStream.finish();
      }
   }

   /**
    * Creates a relative entry name and replaces all backslashes with forward slash.
    *
    * @param path the path to make relative to {@code rootDirectory}
    * @param rootDirectory the root directory to use to generate the relative entry name
    *
    * @return the entry name ({@code path} relative to {@code rootDirectory} with backslashes replaced)
    */
   private static String getEntryName(Path path, Path rootDirectory) {
      return rootDirectory.relativize(path).toString().replaceAll("\\\\", "/");
   }

   /**
    * Archive types available for creation.
    */
   public enum ArchiveType {
      /**
       * A zip archive.
       */
      ZIP(ArchiveStreamFactory.ZIP);

      private final String commonsCompressName;

      /**
       * Create a new ArchiveType enum with the given name that maps into {@link ArchiveStreamFactory}.
       *
       * @param commonsCompressName the matching name from {@link ArchiveStreamFactory}
       */
      ArchiveType(final String commonsCompressName) {
         this.commonsCompressName = commonsCompressName;
      }

      /**
       * The archive name to use in {@link ArchiveStreamFactory}.
       *
       * @return the archive name
       */
      public String getCommonsCompressName() {
         return commonsCompressName;
      }
   }
}
