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

import com.lexicalscope.jewel.cli.*;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.commons.IOUtils;

import java.io.*;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Takes a couple of parameters and a JRE and bundles them into a platform specific
 * distributable (zip on Windows and Linux, app bundle on Mac OS X).
 *
 * @author badlogic
 */
public class Packr {

	private PackrConfig config;

	@SuppressWarnings("WeakerAccess")
	public void pack(PackrConfig config) throws IOException {

		config.validate();
		this.config = config;

		PackrOutput output = new PackrOutput(config.outDir, config.outDir);

		cleanOrCreateOutputFolder(output);

		output = buildMacBundle(output);

		copyExecutableAndClasspath(output);

		writeConfig(output);

		copyJRE(output);

		copyResources(output);

		PackrReduce.minimizeJre(output, config);

		PackrReduce.removePlatformLibs(output, config);

		System.out.println("Done!");
	}

	private void cleanOrCreateOutputFolder(PackrOutput output) throws IOException {
		File folder = output.executableFolder;
		if (folder.exists()) {
			System.out.println("Cleaning output directory '" + folder.getAbsolutePath() + "' ...");
			PackrFileUtils.deleteDirectory(folder);
		} else {
			PackrFileUtils.mkdirs(folder);
		}
	}

	private PackrOutput buildMacBundle(PackrOutput output) throws IOException {

		if (config.platform != PackrConfig.Platform.MacOS) {
			return output;
		}

		// replacement strings for Info.plist
		Map<String, String> values = new HashMap<>();

		values.put("${executable}", config.executable);

		if (config.bundleIdentifier != null) {
			values.put("${bundleIdentifier}", config.bundleIdentifier);
		} else {
			values.put("${bundleIdentifier}", config.mainClass.substring(0, config.mainClass.lastIndexOf('.')));
		}

		// create folder structure

		File root = output.executableFolder;

		PackrFileUtils.mkdirs(new File(root, "Contents"));
		new FileWriter(new File(root, "Contents/Info.plist")).write(readResourceAsString("/Info.plist", values));

		File target = new File(root, "Contents/MacOS");
		PackrFileUtils.mkdirs(target);

		File resources = new File(root, "Contents/Resources");
		PackrFileUtils.mkdirs(resources);

		if (config.iconResource != null) {
			// copy icon to Contents/Resources/icons.icns
			if (config.iconResource.exists()) {
				PackrFileUtils.copyFile(config.iconResource, new File(resources, "icons.icns"));
			}
		}

		return new PackrOutput(target, resources);
	}

	private void copyExecutableAndClasspath(PackrOutput output) throws IOException {
		byte[] exe = null;
		String extension = "";

		switch (config.platform) {
			case Windows32:
				exe = readResource("/packr-windows.exe");
				extension = ".exe";
				break;
			case Windows64:
				exe = readResource("/packr-windows-x64.exe");
				extension = ".exe";
				break;
			case Linux32:
				exe = readResource("/packr-linux");
				break;
			case Linux64:
				exe = readResource("/packr-linux-x64");
				break;
			case MacOS:
				exe = readResource("/packr-mac");
				break;
		}

		System.out.println("Copying executable ...");

		try (OutputStream writer = new FileOutputStream(
				new File(output.executableFolder, config.executable + extension))) {

			writer.write(exe);
		}

		PackrFileUtils.chmodX(new File(output.executableFolder, config.executable + extension));

		System.out.println("Copying classpath(s) ...");
		for (String file : config.classpath) {
			File cpSrc = new File(file);
			File cpDst = new File(output.resourcesFolder, new File(file).getName());

			if (cpSrc.isFile()) {
				PackrFileUtils.copyFile(cpSrc, cpDst);
			} else if (cpSrc.isDirectory()) {
				PackrFileUtils.copyDirectory(cpSrc, cpDst);
			} else {
				System.err.println("Warning! Classpath not found: " + cpSrc);
			}
		}
	}

	private void writeConfig(PackrOutput output) throws IOException {

		StringBuilder builder = new StringBuilder();
		builder.append("{\n");
		builder.append("  \"classPath\": [");

		String delim = "\n";
		for (String f : config.classpath) {
			builder.append(delim).append("    \"").append(new File(f).getName()).append("\"");
			delim = ",\n";
		}
		builder.append("\n  ],\n");

		builder.append("  \"mainClass\": \"").append(config.mainClass).append("\",\n");
		builder.append("  \"vmArgs\": [\n");

		for (int i = 0; i < config.vmArgs.size(); i++) {
			String vmArg = config.vmArgs.get(i);
			builder.append("    \"");
			if (!vmArg.startsWith("-")) {
				builder.append("-");
			}
			builder.append(vmArg).append("\"");
			if (i < config.vmArgs.size() - 1) {
				builder.append(",");
			}
			builder.append("\n");
		}
		builder.append("  ]\n");
		builder.append("}");

		try (Writer writer = new FileWriter(new File(output.resourcesFolder, "config.json"))) {
			writer.write(builder.toString());
		}
	}

	private void copyJRE(PackrOutput output) throws IOException {

		File jdkFile;
		boolean fetchFromRemote = config.jdk.startsWith("http://") || config.jdk.startsWith("https://");

		// add JRE from local or remote zip file
		if (fetchFromRemote) {
			System.out.println("Downloading JDK from '" + config.jdk + "' ...");
			jdkFile = new File(output.resourcesFolder, "jdk.zip");
			InputStream in = new URL(config.jdk).openStream();
			OutputStream outJdk = new FileOutputStream(jdkFile);
			IOUtils.copy(in, outJdk);
			in.close();
			outJdk.close();
		} else {
			jdkFile = new File(config.jdk);
		}

		System.out.println("Unpacking JRE ...");
		File tmp = new File(output.resourcesFolder, "tmp");
		PackrFileUtils.mkdirs(tmp);

		if (jdkFile.isDirectory()) {
			PackrFileUtils.copyDirectory(jdkFile, tmp);
		} else {
			ZipUtil.unpack(jdkFile, tmp);
		}

		File jre = searchJre(tmp);
		if (jre == null) {
			throw new IOException("Couldn't find JRE in JDK, see '" + tmp.getAbsolutePath() + "'");
		}

		PackrFileUtils.copyDirectory(jre, new File(output.resourcesFolder, "jre"));
		PackrFileUtils.deleteDirectory(tmp);

		if (fetchFromRemote) {
			PackrFileUtils.delete(jdkFile);
		}
	}

	private File searchJre(File tmp) {
		if (tmp.getName().equals("jre") && tmp.isDirectory()
				&& (new File(tmp, "bin/java").exists() || new File(tmp, "bin/java.exe").exists())) {
			return tmp;
		}

		File[] childs = tmp.listFiles();
		if (childs != null) {
			for (File child : childs) {
				if (child.isDirectory()) {
					File found = searchJre(child);
					if (found != null) {
						return found;
					}
				}
			}
		}

		return null;
	}

	private void copyResources(PackrOutput output) throws IOException {
		if (config.resources != null) {
			System.out.println("Copying resources ...");

			for (File file : config.resources) {
				if (!file.exists()) {
					throw new IOException("Resource '" + file.getAbsolutePath() + "' doesn't exist");
				}

				if (file.isFile()) {
					PackrFileUtils.copyFile(file, new File(output.resourcesFolder, file.getName()));
				}

				if (file.isDirectory()) {
					File target = new File(output.resourcesFolder, file.getName());
					PackrFileUtils.mkdirs(target);
					PackrFileUtils.copyDirectory(file, target);
				}
			}
		}
	}

	private byte[] readResource(String resource) throws IOException {
		return IOUtils.toByteArray(Packr.class.getResourceAsStream(resource));
	}

	private String readResourceAsString(String resource, Map<String, String> values) throws IOException {
		String txt = IOUtils.toString(Packr.class.getResourceAsStream(resource), "UTF-8");
		return replace(txt, values);
	}

	private String replace(String txt, Map<String, String> values) {
		for (String key : values.keySet()) {
			String value = values.get(key);
			txt = txt.replace(key, value);
		}
		return txt;
	}

	public static void main(String[] args) {

		try {

			PackrCommandLine commandLine = CliFactory.parseArguments(PackrCommandLine.class, args);

			if (commandLine.help()) {
				return;
			}

			new Packr().pack(new PackrConfig(commandLine));

		} catch (ArgumentValidationException e) {
			for (ValidationFailure failure : e.getValidationFailures()) {
				System.err.println(failure.getMessage());
			}
			System.exit(-1);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

}
