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

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.compressors.CompressorException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static com.badlogicgames.packr.ArchiveUtils.ArchiveType.ZIP;

/**
 * Functions to reduce package size for both classpath JARs, and the bundled JRE.
 */
class PackrReduce {

   /**
    * Tries to shrink the size of the JRE by deleting unused files and possibly removing items from the included jars of the JRE.
    *
    * @param output the directory to save the minimized JRE into
    * @param config the options for minimizing the JRE
    *
    * @throws IOException if an IO error occurs
    * @throws ArchiveException if an archive error occurs
    * @throws CompressorException if a compression error occurs
    */
   static void minimizeJre(File output, PackrConfig config) throws IOException, CompressorException, ArchiveException {
      if (config.minimizeJre == null) {
         return;
      }

      System.out.println("Minimizing JRE ...");

      JsonObject minimizeJson = readMinimizeProfile(config);
      if (minimizeJson != null) {
         if (config.verbose) {
            System.out.println("  # Removing files and directories in profile '" + config.minimizeJre + "' ...");
         }

         JsonArray reduceArray = minimizeJson.get("reduce").asArray();
         for (JsonValue reduce : reduceArray) {
            String path = reduce.asObject().get("archive").asString();
            File file = new File(output, path);

            if (!file.exists()) {
               if (config.verbose) {
                  System.out.println("  # No file or directory '" + file.getPath() + "' found, skipping");
               }
               continue;
            }

            boolean needsUnpack = !file.isDirectory();

            File fileNoExt = needsUnpack ? new File(output, path.contains(".") ? path.substring(0, path.lastIndexOf('.')) : path) : file;

            if (needsUnpack) {
               if (config.verbose) {
                  System.out.println("  # Unpacking '" + file.getPath() + "' ...");
               }
               ArchiveUtils.extractArchive(file.toPath(), fileNoExt.toPath());
            }

            JsonArray removeArray = reduce.asObject().get("paths").asArray();
            for (JsonValue remove : removeArray) {
               File removeFile = new File(fileNoExt, remove.asString());
               if (removeFile.exists()) {
                  if (removeFile.isDirectory()) {
                     PackrFileUtils.deleteDirectory(removeFile);
                  } else {
                     Files.deleteIfExists(removeFile.toPath());
                  }
               } else {
                  if (config.verbose) {
                     System.out.println("  # No file or directory '" + removeFile.getPath() + "' found");
                  }
               }
            }

            if (needsUnpack) {
               if (config.verbose) {
                  System.out.println("  # Repacking '" + file.getPath() + "' ...");
               }

               createZipFileFromDirectory(config, file, fileNoExt);
            }
         }

         JsonArray removeArray = minimizeJson.get("remove").asArray();
         for (JsonValue remove : removeArray) {
            String platform = remove.asObject().get("platform").asString();

            if (!matchPlatformString(platform, config)) {
               continue;
            }

            JsonArray removeFilesArray = remove.asObject().get("paths").asArray();
            for (JsonValue removeFile : removeFilesArray) {
               removeFileWildcard(output, removeFile.asString(), config);
            }
         }
      }
   }

   /**
    * Creates a new zip file {@code zipFileOutput} from the directory {@code directoryToZipAndThenDelete}. After the Zip file is successfully created, {@code
    * directoryToZipAndThenDelete} is deleted.
    *
    * @param config configuration information
    * @param zipFileOutput the Zip file to create
    * @param directoryToZipAndThenDelete the contents to put into the created Zip file
    *
    * @throws IOException if an IO error occurs
    * @throws ArchiveException if an archive error occurs
    */
   private static void createZipFileFromDirectory(PackrConfig config, File zipFileOutput, File directoryToZipAndThenDelete)
         throws IOException, ArchiveException {
      long beforeLen = zipFileOutput.length();
      Files.deleteIfExists(zipFileOutput.toPath());

      ArchiveUtils.createArchive(ZIP, directoryToZipAndThenDelete.toPath(), zipFileOutput.toPath());
      PackrFileUtils.deleteDirectory(directoryToZipAndThenDelete);

      long afterLen = zipFileOutput.length();

      if (config.verbose) {
         System.out.println("  # " + beforeLen / 1024 + " kb -> " + afterLen / 1024 + " kb");
      }
   }

   /**
    * Checks {@code platform} matches what is specified in the {@code config}.
    *
    * @param platform the platform to check against {@code config}
    * @param config check if the platform is in this config
    *
    * @return true if {@code platform} is the same as specified in {@code config}
    */
   private static boolean matchPlatformString(String platform, PackrConfig config) {
      return "*".equals(platform) || config.platform.desc.contains(platform);
   }

   /**
    * Deletes the path named {@code removeFileWildcard} int directory {@code output}, or if {@code removeFileWildcard} contains a *, it looks for paths in
    * {@code output} that start and end with those parts of {@code removeWildcard}.
    *
    * @param output the directory to look for {@code removeFileWildcard} named paths in and delete them
    * @param removeFileWildcard either the exact name of a sub-path in {@code output} to delete, or paths that match the parts of a single wildcard
    *       pattern
    * @param config the packr config
    *
    * @throws IOException if an IO error occurs
    */
   private static void removeFileWildcard(File output, String removeFileWildcard, PackrConfig config) throws IOException {
      if (removeFileWildcard.contains("*")) {
         String removePath = removeFileWildcard.substring(0, removeFileWildcard.indexOf('*') - 1);
         String removeSuffix = removeFileWildcard.substring(removeFileWildcard.indexOf('*') + 1);

         File[] files = new File(output, removePath).listFiles();
         if (files != null) {
            for (File file : files) {
               if (removeSuffix.isEmpty() || file.getName().endsWith(removeSuffix)) {
                  removeFile(file, config);
               }
            }
         } else {
            if (config.verbose) {
               System.out.println("  # No matching files found in '" + removeFileWildcard + "'");
            }
         }
      } else {
         removeFile(new File(output, removeFileWildcard), config);
      }
   }

   /**
    * Deletes the file or directory {@code file}.
    *
    * @param file the file or directory to delete
    * @param config packr configuration
    *
    * @throws IOException if an IO error occurs
    */
   private static void removeFile(File file, PackrConfig config) throws IOException {
      if (!file.exists()) {
         if (config.verbose) {
            System.out.println("  # No file or directory '" + file.getPath() + "' found");
         }
         return;
      }

      if (config.verbose) {
         System.out.println("  # Removing '" + file.getPath() + "'");
      }

      if (file.isDirectory()) {
         PackrFileUtils.deleteDirectory(file);
      } else {
         Files.deleteIfExists(file.toPath());
      }
   }

   /**
    * Loads the minimize configuration from {@link PackrConfig#minimizeJre} in {@code config}.
    *
    * @param config the config to find the minimize configuration for and load
    *
    * @return the minimize config in JSON format
    *
    * @throws IOException if an IO error occurs
    */
   private static JsonObject readMinimizeProfile(PackrConfig config) throws IOException {
      JsonObject json = null;

      if (new File(config.minimizeJre).exists()) {
         json = JsonObject.readFrom(new String(Files.readAllBytes(Paths.get(config.minimizeJre)), StandardCharsets.UTF_8));
      } else {
         InputStream in = Packr.class.getResourceAsStream("/minimize/" + config.minimizeJre);
         if (in != null) {
            json = JsonObject.readFrom(new InputStreamReader(in));
         }
      }

      if (json == null && config.verbose) {
         System.out.println("  # No minimize profile '" + config.minimizeJre + "' found");
      }

      return json;
   }

   /**
    * Remove any dynamic libraries that don't match {@link PackrConfig#platform}.
    *
    * @param output the output configuration
    * @param config the packr configuration
    * @param removePlatformLibsFileFilter addition files to remove if they match
    *
    * @throws IOException if an IO error occurs
    * @throws ArchiveException if an archive error occurs
    * @throws CompressorException if a compression error occurs
    */
   static void removePlatformLibs(PackrOutput output, PackrConfig config, Predicate<File> removePlatformLibsFileFilter)
         throws IOException, CompressorException, ArchiveException {
      if (config.removePlatformLibs == null || config.removePlatformLibs.isEmpty()) {
         return;
      }

      boolean extractLibs = config.platformLibsOutDir != null;
      File libsOutputDir = null;
      if (extractLibs) {
         libsOutputDir = new File(output.executableFolder, config.platformLibsOutDir.getPath());
         Files.createDirectories(libsOutputDir.toPath());
      }

      System.out.println("Removing foreign platform libs ...");

      Set<String> extensions = new HashSet<>();
      String libExtension;

      switch (config.platform) {
         case Windows32:
         case Windows64:
            extensions.add(".dylib");
            extensions.add(".dylib.git");
            extensions.add(".dylib.sha1");
            extensions.add(".so");
            extensions.add(".so.git");
            extensions.add(".so.sha1");
            libExtension = ".dll";
            break;
         case Linux32:
         case Linux64:
            extensions.add(".dll");
            extensions.add(".dll.git");
            extensions.add(".dll.sha1");
            extensions.add(".dylib");
            extensions.add(".dylib.git");
            extensions.add(".dylib.sha1");
            libExtension = ".so";
            break;
         case MacOS:
            extensions.add(".dll");
            extensions.add(".dll.git");
            extensions.add(".dll.sha1");
            extensions.add(".so");
            extensions.add(".so.git");
            extensions.add(".so.sha1");
            libExtension = ".dylib";
            break;
         default:
            throw new IllegalStateException();
      }

      // let's remove any shared libs not used on the platform, e.g. libGDX/LWJGL natives
      for (String classpath : config.removePlatformLibs) {
         File jar = new File(output.resourcesFolder, new File(classpath).getName());
         File jarDir = new File(output.resourcesFolder, jar.getName() + ".tmp");

         if (config.verbose) {
            if (jar.isDirectory()) {
               System.out.println("  # JAR '" + jar.getName() + "' is a directory");
            } else {
               System.out.println("  # Unpacking '" + jar.getName() + "' ...");
            }
         }

         if (!jar.isDirectory()) {
            ArchiveUtils.extractArchive(jar.toPath(), jarDir.toPath());
         } else {
            jarDir = jar; // run in-place for directories
         }

         File[] files = jarDir.listFiles();
         if (files != null) {
            for (File file : files) {
               boolean removed = false;
               if (removePlatformLibsFileFilter.test(file)) {
                  if (config.verbose) {
                     System.out.println("  # Removing '" + file.getPath() + "' (filtered)");
                  }
                  Files.deleteIfExists(file.toPath());
                  removed = true;
               }
               if (!removed) {
                  for (String extension : extensions) {
                     if (file.getName().endsWith(extension)) {
                        if (config.verbose) {
                           System.out.println("  # Removing '" + file.getPath() + "'");
                        }
                        Files.deleteIfExists(file.toPath());
                        removed = true;
                        break;
                     }
                  }
               }
               if (!removed && extractLibs) {
                  if (file.getName().endsWith(libExtension)) {
                     if (config.verbose) {
                        System.out.println("  # Extracting '" + file.getPath() + "'");
                     }
                     File target = new File(libsOutputDir, file.getName());
                     Files.copy(file.toPath(), target.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                     Files.deleteIfExists(file.toPath());
                  }
               }
            }
         }

         if (!jar.isDirectory()) {
            if (config.verbose) {
               System.out.println("  # Repacking '" + jar.getName() + "' ...");
            }

            createZipFileFromDirectory(config, jar, jarDir);
         }
      }
   }

}
