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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.zeroturnaround.zip.ZipUtil;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

/**
 * Takes a couple of parameters and a JRE and bundles them into a platform specific 
 * distributable (zip on Windows and Linux, app bundle on Mac OS X).
 * @author badlogic
 *
 */
public class Packr {
	public enum Platform {
		windows32,
		windows64,
		linux32,
		linux64,
		mac
	}
	
	public static class Config {
		public Platform platform;
		public String jdk;
		public String executable;
		public List<String> classpath = new ArrayList<String>();
		public String mainClass;
		public List<String> vmArgs = new ArrayList<String>();
		public String[] minimizeJre;
		public List<String> resources = new ArrayList<String>();
		public String outDir;
		public String iconResource;
		public String bundleIdentifier = "com.yourcompany.identifier";
	}
	
	public void pack(Config config) throws IOException {
		// create output dir
		File out = new File(config.outDir);
		File target = out;
		if(out.exists()) {
			if(new File(".").equals(out)) {
				System.out.println("Output directory equals working directory, aborting");
				System.exit(-1);
			}
			if(new File("/").equals(out)) {
				System.out.println("Output directory equals root, aborting");
				System.exit(-1);
			}
			
			System.out.println("Output directory '" + out.getAbsolutePath() + "' exists, deleting");
			FileUtils.deleteDirectory(out);
		}
		out.mkdirs();
		
		Map<String, String> values = new HashMap<String, String>();
		values.put("${executable}", config.executable);
		values.put("${bundleIdentifier}", config.bundleIdentifier);

		// if this is a mac build, let's create the app bundle structure
		if(config.platform == Platform.mac) {
			new File(out, "Contents").mkdirs();
			FileUtils.writeStringToFile(new File(out, "Contents/Info.plist"), readResourceAsString("/Info.plist", values));
			target = new File(out, "Contents/MacOS");
			target.mkdirs();
			File resources = new File(out, "Contents/Resources");
			resources.mkdirs();
			if(config.iconResource != null) {
				// copy icon to Contents/Resources/icons.icns
				File icons = new File(config.iconResource);
				if(icons.exists()) {
					FileUtils.copyFile(new File(config.iconResource), new File(resources, "icons.icns"));
				}
			}
		}
		
		// write jar, exe and config to target folder
		byte[] exe = null;
		String extension = "";
		switch(config.platform) {
			case windows32:
				exe = readResource("/packr-windows.exe");
				extension = ".exe";
				break;
			case windows64:
				exe = readResource("/packr-windows-x64.exe");
				extension = ".exe";
				break;
			case linux32:
				exe = readResource("/packr-linux");
				break;
			case linux64:
				exe = readResource("/packr-linux-x64");
				break;
			case mac:
				exe = readResource("/packr-mac");
				break;
		}
		FileUtils.writeByteArrayToFile(new File(target, config.executable + extension), exe);
		new File(target, config.executable + extension).setExecutable(true);
		for (String file : config.classpath) {
			FileUtils.copyFile(new File(file), new File(target, new File(file).getName()));
		}
		writeConfig(config, new File(target, "config.json"));
		
		// add JRE from local or remote zip file
		File jdkFile = null;		
		if(config.jdk.startsWith("http://") || config.jdk.startsWith("https://")) {
			System.out.println("Downloading JDK from '" + config.jdk + "'");
			jdkFile = new File(target, "jdk.zip");
			InputStream in = new URL(config.jdk).openStream();
			OutputStream outJdk = FileUtils.openOutputStream(jdkFile);
			IOUtils.copy(in, outJdk);
			in.close();
			outJdk.close();
		} else {
			jdkFile = new File(config.jdk);			
		}
		File tmp = new File(target, "tmp");
		tmp.mkdirs();
		System.out.println("Unpacking JRE");
		ZipUtil.unpack(jdkFile, tmp);
		File jre = searchJre(tmp);
		if(jre == null) {
			System.out.println("Couldn't find JRE in JDK, see '" + tmp.getAbsolutePath() + "'");
			System.exit(-1);
		}
		FileUtils.copyDirectory(jre, new File(target, "jre"));
		FileUtils.deleteDirectory(tmp);
		if(config.jdk.startsWith("http://") || config.jdk.startsWith("https://")) {
			jdkFile.delete();
		}
		
		// copy resources
		System.out.println("copying resources");
		copyResources(target, config.resources);
		
		// perform tree shaking		
		if(config.minimizeJre != null) {	
			minimizeJre(config, target);			
		}
		
		System.out.println("Done!");
	}
	
	private void writeConfig(Config config, File file) throws IOException {
		StringBuilder builder = new StringBuilder();
		builder.append("{\n");
		builder.append("  \"classPath\": [");
		
		{
			String delim = "\n";
			for (String f : config.classpath) {
				builder.append(delim).append("    \"" + new File(f).getName() + "\"");
				delim = ",\n";
			}
			builder.append("\n  ],\n");
		}
		
		builder.append("  \"mainClass\": \"" + config.mainClass + "\",\n");
		builder.append("  \"vmArgs\": [\n");
		for(int i = 0; i < config.vmArgs.size(); i++) {
			String vmArg = config.vmArgs.get(i);
			builder.append("    \"" + vmArg + "\"");
			if(i < config.vmArgs.size() - 1) {
				builder.append(",");
			}
			builder.append("\n");
		}
		builder.append("  ]\n");
		builder.append("}");
		FileUtils.writeStringToFile(file, builder.toString());
	}

	private void minimizeJre(Config config, File outDir) throws IOException {
		// remove stuff from the JRE
		System.out.println("minimizing JRE");
		System.out.println("unpacking rt.jar");
		ZipUtil.unpack(new File(outDir, "jre/lib/rt.jar"), new File(outDir, "jre/lib/rt"));
		
		if(config.platform == Platform.windows32 || config.platform == Platform.windows64) {
			FileUtils.deleteDirectory(new File(outDir, "jre/bin/client"));
			for(File file: new File(outDir, "jre/bin").listFiles()) {
				if(file.getName().endsWith(".exe")) file.delete();
			}
		} else {
			FileUtils.deleteDirectory(new File(outDir, "jre/bin"));
		}
		for(String minimizedDir : config.minimizeJre) {
			minimizedDir = minimizedDir.trim();
			File file = new File(outDir, minimizedDir);
			try {
				if(file.isDirectory()) FileUtils.deleteDirectory(new File(outDir, minimizedDir));
				else file.delete();
			} catch (Exception e) {
				System.out.println("Failed to delete file " + file.getPath() + ": " + e.getMessage());
			}
		}
		new File(outDir, "jre/lib/rhino.jar").delete();
		
		System.out.println("packing rt.jar");
		new File(outDir, "jre/lib/rt.jar").delete();
		ZipUtil.pack(new File(outDir, "jre/lib/rt"), new File(outDir, "jre/lib/rt.jar"));
		FileUtils.deleteDirectory(new File(outDir, "jre/lib/rt"));
		
		// let's remove any shared libs not used on the platform, e.g. libgdx/lwjgl natives
		for (String classpath : config.classpath) {
			File jar = new File(outDir, new File(classpath).getName());
			File jarDir = new File(outDir, jar.getName()+ ".tmp");
			ZipUtil.unpack(jar, jarDir);
		
			Set<String> extensions = new HashSet<String>();
			if(config.platform != Platform.linux32 && config.platform != Platform.linux64) { extensions.add(".so"); }
			if(config.platform != Platform.windows32 && config.platform != Platform.windows64) { extensions.add(".dll"); }
			if(config.platform != Platform.mac) { extensions.add(".dylib"); }
			
			for(Object obj: FileUtils.listFiles(jarDir, TrueFileFilter.INSTANCE , TrueFileFilter.INSTANCE )) {
				File file = new File(obj.toString());
				for(String extension: extensions) {
					if(file.getName().endsWith(extension)) file.delete();
				}
			}
			
			jar.delete();
			ZipUtil.pack(jarDir, jar);
			FileUtils.deleteDirectory(jarDir);
		}
	}

	private void copyResources(File targetDir, List<String> resources) throws IOException {
		for(String resource: resources) {
			File file = new File(resource);
			if(!file.exists()) {
				System.out.println("resource '" + file.getAbsolutePath() + "' doesn't exist");
				System.exit(-1);
			}
			if(file.isFile()) {
				FileUtils.copyFile(file, new File(targetDir, file.getName()));
			}
			if(file.isDirectory()) {
				File target = new File(targetDir, file.getName());
				target.mkdirs();
				FileUtils.copyDirectory(file, target);
			}
		}
	}
	
	private File searchJre(File tmp) {
		if(tmp.getName().equals("jre") && tmp.isDirectory() && (new File(tmp, "bin/java").exists() || new File(tmp, "bin/java.exe").exists())) {
			return tmp;
		} else {
			for(File child: tmp.listFiles()) {
				if(child.isDirectory()) {
					File found = searchJre(child);
					if(found != null) return found;
				}
			}
			return null;
		}
	}

	private byte[] readResource(String resource) throws IOException {
		return IOUtils.toByteArray(Packr.class.getResourceAsStream(resource));
	}
	
	private String readResourceAsString(String resource, Map<String, String> values) throws IOException {
		String txt = IOUtils.toString(Packr.class.getResourceAsStream(resource), "UTF-8");
		return replace(txt, values);
	}
	
	private String replace (String txt, Map<String, String> values) {
		for (String key : values.keySet()) {
			String value = values.get(key);
			txt = txt.replace(key, value);
		}
		return txt;
	}
	
	public static void main(String[] args) throws IOException {
		if(args.length > 1) {
			Map<String, String> arguments = parseArgs(args);
			Config config = new Config();
			config.platform = Platform.valueOf(arguments.get("platform"));
			config.jdk = arguments.get("jdk");
			config.executable = arguments.get("executable");
			config.classpath =  Arrays.asList(arguments.get("classpath").split(";"));
			config.iconResource = arguments.get("icon");
			config.mainClass = arguments.get("mainclass");
			if(arguments.get("bundleidentifier") != null) {
				config.bundleIdentifier = arguments.get("bundleidentifier");
			}
			if(arguments.get("vmargs") != null) {
				config.vmArgs = Arrays.asList(arguments.get("vmargs").split(";"));
			}
			config.outDir = arguments.get("outdir");
			if(arguments.get("minimizejre") != null) {
				if(new File(arguments.get("minimizejre")).exists()) {
					config.minimizeJre = FileUtils.readFileToString(new File(arguments.get("minimizejre"))).split("\r?\n");
				} else {
					InputStream in = Packr.class.getResourceAsStream("/minimize/" + arguments.get("minimizejre"));
					if(in != null) {
						config.minimizeJre = IOUtils.toString(in).split("\r?\n");
						in.close();
					} else {
						config.minimizeJre = new String[0];
					}
				}
			}
			if(arguments.get("resources") != null) config.resources = Arrays.asList(arguments.get("resources").split(";"));
			new Packr().pack(config);
		} else {
			if(args.length == 0) {
				printHelp();
			} else {
				JsonObject json = JsonObject.readFrom(FileUtils.readFileToString(new File(args[0])));
				Config config = new Config();
				config.platform = Platform.valueOf(json.get("platform").asString());
				config.jdk = json.get("jdk").asString();
				config.executable = json.get("executable").asString();
				config.classpath = toStringArray(json.get("classpath").asArray());
				if(json.get("icon") != null) {
					config.iconResource = json.get("icon").asString();
				}
				config.mainClass = json.get("mainclass").asString();
				if(json.get("bundleidentifier") != null) {
					config.bundleIdentifier = json.get("bundleidentifier").asString();
				}
				if(json.get("vmargs") != null) {
					config.vmArgs = toStringArray(json.get("vmargs").asArray());
				}
				config.outDir = json.get("outdir").asString();
				if(json.get("minimizejre") != null) {
					if(new File(json.get("minimizejre").asString()).exists()) {
						config.minimizeJre = FileUtils.readFileToString(new File(json.get("minimizejre").asString())).split("\r?\n");
					} else {
						InputStream in = Packr.class.getResourceAsStream("/minimize/" + json.get("minimizejre"));
						if(in != null) {
							config.minimizeJre = IOUtils.toString(in).split("\r?\n");
							in.close();
						} else {
							config.minimizeJre = new String[0];
						}
					}
				}
				if(json.get("resources") != null) {
					config.resources = toStringArray(json.get("resources").asArray());
				}
				new Packr().pack(config);
			}
		}
	}
	
	private static List<String> toStringArray(JsonArray array) {
		List<String> result = new ArrayList<String>();
		for(JsonValue value: array) {
			result.add(value.asString());
		}
		return result;
	}
	
	private static void error() {
		printHelp();
		System.exit(-1);
	}
	
	private static void printHelp() {
		System.out.println("Usage: packr <args>");
		System.out.println("-platform <windows32|windows64|linux32|linux64|mac>");
		System.out.println("                                     ... operating system to pack for");
		System.out.println("-jdk <path-or-url>                   ... path to a JDK to be bundled (needs to fit platform)");
		System.out.println("                                         Can be a ZIP file or URL to a ZIP file");
		System.out.println("-executable <name>                   ... name of the executable, e.g. 'mygame', without extension");
		System.out.println("-classpath <file.jar>                ... JAR file containing code and assets to be packed");
		System.out.println("                                         Can contain multiple JAR files, separated by ;");
		System.out.println("-icon <file>                         ... file containing icon resources (needs to fit platform)");
		System.out.println("                                         Only supported on OS X (.icns)");
		System.out.println("-mainclass <main-class>              ... fully qualified main class name, e.g. com/badlogic/MyApp");
		System.out.println("-bundleidentifier <identifier>       ... bundle identifier, e.g. com.badlogic");
		System.out.println("                                         Only used for Info.plist on OS X");
		System.out.println("-vmargs <args>                       ... arguments passed to the JVM, e.g. -Xmx1G, separated by ;");
		System.out.println("-minimizejre <configfile>            ... minimize the JRE by removing folders and files specified in the config file");
		System.out.println("                                         three config files come with packr: 'soft' and 'hard' which may or may not break your app");
		System.out.println("-resources <files-and-folders>       ... additional files and folders to be packed next to the");
		System.out.println("                                         executable. Entries are separated by a ;");
		System.out.println("-outdir <dir>                        ... output directory");
	}
	
	private static Map<String, String> parseArgs (String[] args) {
		if (args.length < 12) {
			error();
		}

		Map<String, String> params = new HashMap<String, String>();
		for (int i = 0; i < args.length; i += 2) {
			String param = args[i].replace("-", "");
			String value = args[i + 1];
			params.put(param, value);
		}
		
		if(params.get("platform") == null) error();
		if(params.get("jdk") == null) error();
		if(params.get("executable") == null) error();
		if(params.get("classpath") == null) error();
		if(params.get("mainclass") == null) error();
		if(params.get("outdir") == null) error();
		
		return params;
	}
}
