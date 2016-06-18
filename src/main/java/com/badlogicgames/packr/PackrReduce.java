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

import com.eclipsesource.json.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.zeroturnaround.zip.ZipUtil;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Functions to reduce package size for both classpath JARs, and the bundled JRE.
 */
class PackrReduce {

	static void minimizeJre(File output, PackrConfig config) throws IOException {
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

				File fileNoExt = needsUnpack
						? new File(output, path.contains(".") ? path.substring(0, path.lastIndexOf('.')) : path)
						: file;

				if (needsUnpack) {
					if (config.verbose) {
						System.out.println("  # Unpacking '" + file.getPath() + "' ...");
					}
					ZipUtil.unpack(file, fileNoExt);
				}

				JsonArray removeArray = reduce.asObject().get("paths").asArray();
				for (JsonValue remove : removeArray) {
					File removeFile = new File(fileNoExt, remove.asString());
					if (removeFile.exists()) {
						if (removeFile.isDirectory()) {
							FileUtils.deleteDirectory(removeFile);
						} else {
							PackrFileUtils.delete(removeFile);
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

					long beforeLen = file.length();
					PackrFileUtils.delete(file);

					ZipUtil.pack(fileNoExt, file);
					FileUtils.deleteDirectory(fileNoExt);

					long afterLen = file.length();

					if (config.verbose) {
						System.out.println("  # " + beforeLen / 1024 + " kb -> " + afterLen / 1024 + " kb");
					}
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

	private static boolean matchPlatformString(String platform, PackrConfig config) {
		return "*".equals(platform) || config.platform.desc.contains(platform);
	}

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
			FileUtils.deleteDirectory(file);
		} else {
			PackrFileUtils.delete(file);
		}
	}

	private static JsonObject readMinimizeProfile(PackrConfig config) throws IOException {

		JsonObject json = null;

		if (new File(config.minimizeJre).exists()) {
			json = JsonObject.readFrom(FileUtils.readFileToString(new File(config.minimizeJre)));
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

	static void removePlatformLibs(File output, PackrConfig config) throws IOException {
		System.out.println("Removing foreign platform libs ...");

		// let's remove any shared libs not used on the platform, e.g. libGDX/LWJGL natives
		for (String classpath : config.classpath) {
			File jar = new File(output, new File(classpath).getName());
			File jarDir = new File(output, jar.getName() + ".tmp");

			if (config.verbose) {
				if (jar.isDirectory()) {
					System.out.println("  # Classpath '" + classpath + "' is a directory");
				} else {
					System.out.println("  # Unpacking '" + classpath + "' ...");
				}
			}

			if (!jar.isDirectory()) {
				ZipUtil.unpack(jar, jarDir);
			}

			Set<String> extensions = new HashSet<String>();

			switch (config.platform) {
				case Windows32:
				case Windows64:
					extensions.add(".dylib");
					extensions.add(".so");
					break;
				case Linux32:
				case Linux64:
					extensions.add(".dylib");
					extensions.add(".dll");
					break;
				case MacOS:
					extensions.add(".dll");
					extensions.add(".so");
					break;
			}

			for (Object obj : FileUtils.listFiles(jarDir, TrueFileFilter.INSTANCE , TrueFileFilter.INSTANCE)) {
				File file = new File(obj.toString());
				for (String extension: extensions) {
					if (file.getName().endsWith(extension)) {
						if (config.verbose) {
							System.out.println("  # Removing '" + file.getPath() + "'");
						}
						PackrFileUtils.delete(file);
					}
				}
			}

			if (!jar.isDirectory()) {
				if (config.verbose) {
					System.out.println("  # Repacking '" + classpath + "' ...");
				}

				long beforeLen = jar.length();
				PackrFileUtils.delete(jar);

				ZipUtil.pack(jarDir, jar);
				FileUtils.deleteDirectory(jarDir);

				long afterLen = jar.length();
				if (config.verbose) {
					System.out.println("  # " + beforeLen / 1024 + " kb -> " + afterLen / 1024 + " kb");
				}
			}
		}
	}

}
