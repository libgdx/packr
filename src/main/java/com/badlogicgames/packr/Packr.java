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

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CliFactory;
import com.lexicalscope.jewel.cli.ValidationFailure;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Takes a couple of parameters and a JRE and bundles them into a platform specific
 * distributable (zip on Windows and Linux, app bundle on Mac OS X).
 * @author badlogic
 *
 */
public class Packr {

	private PackrConfig config;

	public void pack(PackrConfig config) throws IOException {

		config.validate();
		this.config = config;

		File output = config.outDir;

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

	private void cleanOrCreateOutputFolder(File output) throws IOException {
		if (output.exists()) {
			System.out.println("Cleaning output directory '" + output.getAbsolutePath() + "' ...");
			FileUtils.deleteDirectory(output);
		} else {
			PackrFileUtils.mkdirs(output);
		}
	}

	private File buildMacBundle(File output) throws IOException {

		if (config.platform != PackrConfig.Platform.MacOS) {
			return output;
		}

		// replacement strings for Info.plist
		Map<String, String> values = new HashMap<String, String>();

		values.put("${executable}", config.executable);

		if (config.bundleIdentifier != null) {
			values.put("${bundleIdentifier}", config.bundleIdentifier);
		} else {
			values.put("${bundleIdentifier}", config.mainClass.substring(0, config.mainClass.lastIndexOf('.')));
		}

		// create folder structure

		PackrFileUtils.mkdirs(new File(output, "Contents"));
		FileUtils.writeStringToFile(new File(output, "Contents/Info.plist"), readResourceAsString("/Info.plist", values));

		File target = new File(output, "Contents/MacOS");
		PackrFileUtils.mkdirs(target);

		File resources = new File(output, "Contents/Resources");
		PackrFileUtils.mkdirs(resources);

		if (config.iconResource != null) {
			// copy icon to Contents/Resources/icons.icns
			if (config.iconResource.exists()) {
				FileUtils.copyFile(config.iconResource, new File(resources, "icons.icns"));
			}
		}

		return target;
	}

	private void copyExecutableAndClasspath(File output) throws IOException {
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
		FileUtils.writeByteArrayToFile(new File(output, config.executable + extension), exe);
		PackrFileUtils.chmodX(new File(output, config.executable + extension));

		System.out.println("Copying classpath(s) ...");
		for (String file : config.classpath) {
			File cpSrc = new File(file);
			File cpDst = new File(output, new File(file).getName());

			if (cpSrc.isFile()) {
				FileUtils.copyFile(cpSrc, cpDst);
			} else if (cpSrc.isDirectory()) {
				FileUtils.copyDirectory(cpSrc, cpDst);
			} else {
				System.err.println("Warning! Classpath not found: " + cpSrc);
			}
		}
	}

	private void writeConfig(File output) throws IOException {

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

		FileUtils.writeStringToFile(new File(output, "config.json"), builder.toString());
	}

	private void copyJRE(File output) throws IOException {

		File jdkFile;
		boolean fetchFromRemote = config.jdk.startsWith("http://") || config.jdk.startsWith("https://");

		// add JRE from local or remote zip file
		if (fetchFromRemote) {
			System.out.println("Downloading JDK from '" + config.jdk + "' ...");
			jdkFile = new File(output, "jdk.zip");
			InputStream in = new URL(config.jdk).openStream();
			OutputStream outJdk = FileUtils.openOutputStream(jdkFile);
			IOUtils.copy(in, outJdk);
			in.close();
			outJdk.close();
		} else {
			jdkFile = new File(config.jdk);
		}

		System.out.println("Unpacking JRE ...");
		File tmp = new File(output, "tmp");
		PackrFileUtils.mkdirs(tmp);
		ZipUtil.unpack(jdkFile, tmp);

		File jre = searchJre(tmp);
		if (jre == null) {
			throw new IOException("Couldn't find JRE in JDK, see '" + tmp.getAbsolutePath() + "'");
		}

		FileUtils.copyDirectory(jre, new File(output, "jre"));
		FileUtils.deleteDirectory(tmp);

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

	private void copyResources(File output) throws IOException {
		if (config.resources != null) {
			System.out.println("Copying resources ...");

			for (File file : config.resources) {
				if (!file.exists()) {
					throw new IOException("Resource '" + file.getAbsolutePath() + "' doesn't exist");
				}

				if (file.isFile()) {
					FileUtils.copyFile(file, new File(output, file.getName()));
				}

				if (file.isDirectory()) {
					File target = new File(output, file.getName());
					PackrFileUtils.mkdirs(target);
					FileUtils.copyDirectory(file, target);
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
