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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The Packr configuration can be read from command line, read from a JSON config file, or created from Java code directly.
 * <p>
 * Command line parameters can be used to override (single-argument parameters) or extend (multi-argument parameters) JSON settings.
 */
@SuppressWarnings({"WeakerAccess", "JavaDoc"}) public class PackrConfig {

	 public Platform platform;
	 public String jdk;
	 public String executable;
	 public List<String> classpath;
	 public List<String> removePlatformLibs;
	 public String mainClass;
	 public List<String> vmArgs;
	 public String minimizeJre;
	 public File cacheJre;
	 public List<File> resources;
	 public File outDir;
	 public File platformLibsOutDir;
	 public File iconResource;
	 public String bundleIdentifier;
	 public boolean verbose;
	 public boolean useZgcIfSupportedOs;
	 public String jrePath;

	 @SuppressWarnings("unused") public PackrConfig () {
		  super();
	 }

	 @SuppressWarnings("unused") public PackrConfig (Platform platform, String jdk, String executable, List<String> classpath, String mainClass, File outDir) {

		  this.platform = platform;
		  this.jdk = jdk;
		  this.executable = executable;
		  this.classpath = classpath;
		  this.mainClass = mainClass;
		  this.outDir = outDir;
	 }

	 public PackrConfig (PackrCommandLine commandLine) throws IOException {

		  verbose = commandLine.verbose();

		  // parse config file, if one is given

		  if (commandLine.isConfig()) {
				readConfigJson(commandLine.config());
		  }

		  // evaluate optional command line parameters
		  // if given, they override the config file settings

		  if (commandLine.platform() != null) {
				platform = Platform.byDesc(commandLine.platform());
		  }

		  if (commandLine.jdk() != null) {
				jdk = commandLine.jdk();
		  }

		  if (commandLine.executable() != null) {
				executable = commandLine.executable();
		  }

		  classpath = appendTo(classpath, commandLine.classpath());

		  removePlatformLibs = appendTo(removePlatformLibs, commandLine.removePlatformLibs());

		  if (commandLine.mainClass() != null) {
				mainClass = commandLine.mainClass();
		  }

		  vmArgs = appendTo(vmArgs, commandLine.vmArgs());

		  if (commandLine.minimizeJre() != null) {
				minimizeJre = commandLine.minimizeJre();
		  }

		  if (commandLine.cacheJre() != null) {
				cacheJre = commandLine.cacheJre();
		  }

		  resources = appendTo(resources, commandLine.resources());

		  if (commandLine.outDir() != null) {
				outDir = commandLine.outDir();
		  }

		  if (commandLine.platformLibsOutDir() != null) {
				platformLibsOutDir = commandLine.platformLibsOutDir();
		  }

		  if (commandLine.iconResource() != null) {
				iconResource = commandLine.iconResource();
		  }

		  if (commandLine.bundleIdentifier() != null) {
				bundleIdentifier = commandLine.bundleIdentifier();
		  }

		  if (commandLine.useZgcIfSupportedOs()) {
				useZgcIfSupportedOs = true;
		  }

		  jrePath = commandLine.jrePath();
	 }

	 private void readConfigJson (File configJson) throws IOException {

		  JsonObject json = JsonObject.readFrom(new String(Files.readAllBytes(configJson.toPath()), StandardCharsets.UTF_8));

		  if (json.get("platform") != null) {
				platform = Platform.byDesc(json.get("platform").asString());
		  }
		  if (json.get("jdk") != null) {
				jdk = json.get("jdk").asString();
		  }
		  if (json.get("executable") != null) {
				executable = json.get("executable").asString();
		  }
		  if (json.get("classpath") != null) {
				classpath = toStringArray(json.get("classpath").asArray());
		  }
		  if (json.get("removelibs") != null) {
				removePlatformLibs = toStringArray(json.get("removelibs").asArray());
		  }
		  if (json.get("mainclass") != null) {
				mainClass = json.get("mainclass").asString();
		  }
		  if (json.get("vmargs") != null) {
				List<String> vmArgs = toStringArray(json.get("vmargs").asArray());
				this.vmArgs = new ArrayList<>();
				for (String vmArg : vmArgs) {
					 if (vmArg.startsWith("-")) {
						  this.vmArgs.add(vmArg.substring(1));
					 } else {
						  this.vmArgs.add(vmArg);
					 }
				}
		  }
		  if (json.get("minimizejre") != null) {
				minimizeJre = json.get("minimizejre").asString();
		  }
		  if (json.get("cachejre") != null) {
				cacheJre = new File(json.get("cachejre").asString());
		  }
		  if (json.get("resources") != null) {
				resources = toFileArray(json.get("resources").asArray());
		  }
		  if (json.get("output") != null) {
				outDir = new File(json.get("output").asString());
		  }
		  if (json.get("libs") != null) {
				platformLibsOutDir = new File(json.get("libs").asString());
		  }
		  if (json.get("icon") != null) {
				iconResource = new File(json.get("icon").asString());
		  }
		  if (json.get("bundle") != null) {
				bundleIdentifier = json.get("bundle").asString();
		  }
		  if (json.get("useZgcIfSupportedOs") != null) {
				useZgcIfSupportedOs = json.get("useZgcIfSupportedOs").asBoolean();
		  }
	 }

	 private <T> List<T> appendTo (List<T> list, List<T> append) {
		  if (list == null) {
				return append != null ? append : new ArrayList<>();
		  }

		  if (append != null) {
				for (T item : append) {
					 boolean duplicate = false;
					 for (T cmp : list) {
						  if (cmp.equals(item)) {
								duplicate = true;
								break;
						  }
					 }
					 if (!duplicate) {
						  list.add(item);
					 }
				}
		  }

		  return list;
	 }

	 private List<String> toStringArray (JsonArray array) {
		  List<String> result = new ArrayList<>();
		  for (JsonValue value : array) {
				result.add(value.asString());
		  }
		  return result;
	 }

	 private List<File> toFileArray (JsonArray array) {
		  List<File> result = new ArrayList<>();
		  for (JsonValue value : array) {
				result.add(new File(value.asString()));
		  }
		  return result;
	 }

	 /**
	  * Sanity checks for configuration settings. Because users like to break stuff.
	  */
	 void validate () throws IOException {
		  validate(platform, "platform");
		  validate(jdk, "JDK");
		  validate(executable, "executable name");
		  validate(mainClass, "main class");
		  validate(outDir, "output folder");

		  if (outDir.exists()) {
				if (new File(".").equals(outDir)) {
					 throw new IOException("Output directory equals working directory, aborting");
				}
				if (new File("/").equals(outDir)) {
					 throw new IOException("Output directory points to root folder.");
				}
		  }

		  if (classpath.isEmpty()) {
				throw new IOException("Empty class path. Please check your commandline or configuration.");
		  }
	 }

	 private <T> void validate (T parameter, String name) throws IOException {
		  if (parameter == null) {
				throw new IOException("No " + name + " specified. Please check your commandline or configuration.");
		  }
	 }

	 public enum Platform {
		  Windows64("windows64"),
		  Linux64("linux64"),
		  MacOS("mac");

		  final String desc;

		  Platform (String desc) {
				this.desc = desc;
		  }

		  static Platform byDesc (String desc) throws IOException {
				for (Platform value : values()) {
					 if (value.desc.equalsIgnoreCase(desc)) {
						  return value;
					 }
				}
				throw new IOException("Invalid platform '" + desc + "'");
		  }
	 }

}
