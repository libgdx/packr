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
	public static enum Platform {
		windows,
		linux,
		mac
	}
	
	public static class Config {
		public Platform platform;
		public String jdk;
		public String executable;
		public String jar;
		public String mainClass;
		public List<String> vmArgs = new ArrayList<String>();
		public String[] minimizeJre;
		public List<String> resources = new ArrayList<String>();
		public String outDir;
	}
	
	public void pack(Config config) throws IOException {
		// create output dir
		File out = new File(config.outDir);
		File target = out;
		if(out.exists()) {
			System.out.println("Output directory '" + out.getAbsolutePath() + "' exists, deleting");
			FileUtils.deleteDirectory(out);
		}
		out.mkdirs();
		
		Map<String, String> values = new HashMap<String, String>();
		values.put("${executable}", config.executable);
		values.put("${bundleIdentifier}", "com.yourcompany.identifier"); // FIXME add as a param
		
		// if this is a mac build, let's create the app bundle structure
		if(config.platform == Platform.mac) {
			new File(out, "Contents").mkdirs();
			FileUtils.writeStringToFile(new File(out, "Contents/Info.plist"), readResourceAsString("/Info.plist", values));
			target = new File(out, "Contents/MacOS");
			target.mkdirs();
			new File(out, "Contents/Resources").mkdirs();
			// FIXME copy icons
		}
		
		// write jar, exe and config to target folder
		byte[] exe = null;
		String extension = "";
		switch(config.platform) {
			case windows:
				exe = readResource("/packr-windows.exe");
				extension = ".exe";
				break;
			case linux:
				exe = readResource("/packr-linux");
				break;
			case mac:
				exe = readResource("/packr-mac");
				break;
		}
		FileUtils.writeByteArrayToFile(new File(target, config.executable + extension), exe);
		new File(target, config.executable + extension).setExecutable(true);
		FileUtils.copyFile(new File(config.jar), new File(target, new File(config.jar).getName()));
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
		builder.append("   \"jar\": \"" + new File(config.jar).getName() + "\",\n");
		builder.append("   \"mainClass\": \"" + config.mainClass + "\",\n");
		builder.append("   \"vmArgs\": [\n");
		for(int i = 0; i < config.vmArgs.size(); i++) {
			String vmArg = config.vmArgs.get(i);
			builder.append("      \"" + vmArg + "\"");
			if(i < config.vmArgs.size() - 1) {
				builder.append(",");
			}
			builder.append("\n");
		}
		builder.append("   ]");
		builder.append("}");
		FileUtils.writeStringToFile(file, builder.toString());
	}

	private void minimizeJre(Config config, File outDir) throws IOException {
		// remove stuff from the JRE
		System.out.println("minimizing JRE");
		System.out.println("unpacking rt.jar");
		ZipUtil.unpack(new File(outDir, "jre/lib/rt.jar"), new File(outDir, "jre/lib/rt"));
		
		if(config.platform == Platform.windows) {
			FileUtils.deleteDirectory(new File(outDir, "jre/bin/client"));
			for(File file: new File(outDir, "jre/bin").listFiles()) {
				if(file.getName().endsWith(".exe")) file.delete();
			}
		} else {
			FileUtils.deleteDirectory(new File(outDir, "jre/bin"));
		}
		for(String minimizedDir : config.minimizeJre) {
			FileUtils.deleteDirectory(new File(outDir, minimizedDir));
		}
		new File(outDir, "jre/lib/rhino.jar").delete();
		
		System.out.println("packing rt.jar");
		new File(outDir, "jre/lib/rt.jar").delete();
		ZipUtil.pack(new File(outDir, "jre/lib/rt"), new File(outDir, "jre/lib/rt.jar"));
		FileUtils.deleteDirectory(new File(outDir, "jre/lib/rt"));
		
		// let's remove any shared libs not used on the platform, e.g. libgdx/lwjgl natives
		File jar = new File(outDir, new File(config.jar).getName());
		File jarDir = new File(outDir, jar.getName()+ ".tmp");
		ZipUtil.unpack(jar, jarDir);
		
		Set<String> extensions = new HashSet<String>();
		if(config.platform == Platform.linux) { extensions.add(".dylib"); extensions.add(".dll"); }
		if(config.platform == Platform.windows) { extensions.add(".dylib"); extensions.add(".so"); }
		if(config.platform == Platform.mac) { extensions.add(".so"); extensions.add(".dll"); }
		
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
			config.jar = arguments.get("appjar");
			config.mainClass = arguments.get("mainclass");
			if(arguments.get("vmargs") != null) {
				config.vmArgs = Arrays.asList(arguments.get("vmargs").split(";"));
			}
			config.outDir = arguments.get("outdir");
			if(arguments.get("minimizejre").equals("hard") || arguments.get("minimizejre").equals("true"))
			{
				config.minimizeJre = new String[] {
				"jre/lib/rt/com/sun/corba", "jre/lib/rt/com/sun/jmx", "jre/lib/rt/com/sun/jndi", 
				"jre/lib/rt/com/sun/media", "jre/lib/rt/com/sun/naming",
				"jre/lib/rt/com/sun/org", "jre/lib/rt/com/sun/rowset",
				"jre/lib/rt/com/sun/script", "jre/lib/rt/com/sun/xml", "jre/lib/rt/sun/applet",
				"jre/lib/rt/sun/corba", "jre/lib/rt/sun/management"
				};
			}
			else if(arguments.get("minimizejre").equals("soft"))
			{
				config.minimizeJre = new String[] {
				"jre/lib/rt/com/sun/corba", "jre/lib/rt/com/sun/jndi", 
				"jre/lib/rt/com/sun/media", "jre/lib/rt/com/sun/naming",
			        "jre/lib/rt/com/sun/rowset",
			        "jre/lib/rt/sun/applet",
				"jre/lib/rt/sun/corba", "jre/lib/rt/sun/management"
				};
			}
			
			else if(arguments.get("minimizejre").equals("false"))
			{
				config.minimizeJre = null;
			}
			else if(arguments.get("minimizejre") != null)
			{
				config.minimizeJre = FileUtils.readFileToString(new File(arguments.get("minimizejre"))).split("\r?\n");
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
				config.jar = json.get("appjar").asString();
				config.mainClass = json.get("mainclass").asString();
				if(json.get("vmargs") != null) {
					for(JsonValue val: json.get("vmargs").asArray()) {
						config.vmArgs.add(val.asString());
					}
				}
				config.outDir = json.get("outdir").asString();
				if(json.get("minimizejre").asString().equals("hard") || json.get("minimizejre").asString().equals("true"))
				{
					config.minimizeJre = new String[] {
					"jre/lib/rt/com/sun/corba", "jre/lib/rt/com/sun/jmx", "jre/lib/rt/com/sun/jndi", 
					"jre/lib/rt/com/sun/media", "jre/lib/rt/com/sun/naming",
					"jre/lib/rt/com/sun/org", "jre/lib/rt/com/sun/rowset",
					"jre/lib/rt/com/sun/script", "jre/lib/rt/com/sun/xml", "jre/lib/rt/sun/applet",
					"jre/lib/rt/sun/corba", "jre/lib/rt/sun/management"
					};
				}
				else if(json.get("minimizejre").asString().equals("soft"))
				{
					config.minimizeJre = new String[]{
					"jre/lib/rt/com/sun/corba", "jre/lib/rt/com/sun/jndi", 
					"jre/lib/rt/com/sun/media", "jre/lib/rt/com/sun/naming",
					"jre/lib/rt/com/sun/rowset",
					"jre/lib/rt/sun/applet",
					"jre/lib/rt/sun/corba", "jre/lib/rt/sun/management"
					};
				}
				else if(json.get("minimizejre").asString().equals("false"))
				{
					config.minimizeJre = null;
				}
				else if(json.get("minimizejre") != null) {
					config.minimizeJre = FileUtils.readFileToString(new File(json.get("minimizejre").asString())).split("\r?\n");
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
		System.out.println("-platform <windows|linux|mac>        ... operating system to pack for");
		System.out.println("-jdk <path-or-url>                   ... path to a JDK to be bundled (needs to fit platform).");
		System.out.println("                                         Can be a ZIP file or URL to a ZIP file");
		System.out.println("-executable <name>                   ... name of the executable, e.g. 'mygame', without extension");
		System.out.println("-appjar <file>                       ... JAR file containing code and assets to be packed");
		System.out.println("-mainclass <main-class>              ... fully qualified main class name, e.g. com/badlogic/MyApp");
		System.out.println("-vmargs <args>                       ... arguments passed to the JVM, e.g. -Xmx1G, separated by ;");
		System.out.println("-minimizejre <false|file|soft|hard>  ... minimize the JRE, can remove unneeded folders but could break your app.");
		System.out.println("                                         Can be false, a config file or how hard to push things out (soft or hard, true is an alias for hard).");
		System.out.println("                                         Config files are just lists of folders in rt.jar to remove, one per line.");
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
		if(params.get("appjar") == null) error();
		if(params.get("mainclass") == null) error();
		if(params.get("outdir") == null) error();
		
		return params;
	}
}