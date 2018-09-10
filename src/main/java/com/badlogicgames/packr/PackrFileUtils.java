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

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipUtils;
import org.apache.commons.compress.utils.IOUtils;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.commons.FileUtilsV2_2;

import java.io.*;
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
	 * <p>
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

	static void unpack(File packedFile, File outputDir) throws IOException {
		String filename = packedFile.getName().toLowerCase();
		if(filename.endsWith(".zip")) {
			unZip(packedFile, outputDir);
		} else if(filename.endsWith(".tar.gz")) {
			File tarFile = unGzip(packedFile, outputDir);
			unTar(tarFile, outputDir);
			tarFile.delete();
		}
	}

	private static void unZip(File zipFile, File outputDir) {
		ZipUtil.unpack(zipFile, outputDir);
	}

	private static File unGzip(File targzFile, File outputDir) throws IOException {
		System.out.println("unGzip (" + targzFile + ", " + outputDir);
		String outFileName = GzipUtils.getUncompressedFilename(targzFile.getName());

		// code from https://commons.apache.org/proper/commons-compress/examples.html
		Path outputFile = outputDir.toPath().resolve(outFileName);
		try(InputStream fin = Files.newInputStream(targzFile.toPath());
			BufferedInputStream in = new BufferedInputStream(fin);
			OutputStream out = Files.newOutputStream(outputFile);
			GzipCompressorInputStream gzIn = new GzipCompressorInputStream(in)) {
			IOUtils.copy(gzIn, out);
		}

		return outputFile.toFile();
	}

	private static void unTar(File tarFile, File outputDir) throws IOException {
		System.out.println("unTar (" + tarFile + ", " + outputDir);
		// code from https://memorynotfound.com/java-tar-example-compress-decompress-tar-tar-gz-files/
		try (TarArchiveInputStream inputStream = new TarArchiveInputStream(new FileInputStream(tarFile))) {
			TarArchiveEntry entry;
			while((entry = inputStream.getNextTarEntry()) != null) {
				if (entry.isDirectory()) {
					continue;
				}
				File curfile = new File(outputDir, entry.getName());
				File parent = curfile.getParentFile();
				if (!parent.exists()) {
					parent.mkdirs();
				}
				IOUtils.copy(inputStream, new FileOutputStream(curfile));
			}
		}
	}


}
