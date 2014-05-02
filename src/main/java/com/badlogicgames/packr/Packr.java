package com.badlogicgames.packr;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
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
		public String config;
		public String treeshake;
		public List<String> excludeJre = new ArrayList<String>();
		public List<String> includeJre = new ArrayList<String>();
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
		FileUtils.copyFile(new File(config.config), new File(target, "config.json"));
		
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
		if(config.treeshake != null) {
			System.out.println("shaking trees");
			treeshake(new File(target, "jre"), config.treeshake, config.includeJre, config.excludeJre);
		}
		
		System.out.println("Done!");
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
	
	private void treeshake(File jreDir, String treeshake, List<String> includeJre, List<String> excludeJre) {
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
			config.config = arguments.get("config");
			config.outDir = arguments.get("outdir");
			config.treeshake = arguments.get("threeshake");
			if(arguments.get("excludejre") != null) config.excludeJre = Arrays.asList(arguments.get("excludejre").split(";"));
			if(arguments.get("includejre") != null) config.includeJre = Arrays.asList(arguments.get("includejre").split(";"));
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
				config.config = json.get("config").asString();
				config.outDir = json.get("outdir").asString();
				if(json.get("treeshake") != null) {
					config.treeshake = json.get("treeshake").asString();
				}
				if(json.get("excludejre") != null) {
					config.excludeJre = toStringArray(json.get("excludejre").asArray());
				}
				if(json.get("includejre") != null) {
					config.includeJre = toStringArray(json.get("includejre").asArray());
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
		System.out.println("-platform <windows|linux|mac>   ... operating system to pack for");
		System.out.println("-jdk <path-or-url>              ... path to a JDK to be bundled (needs to fit platform).");
		System.out.println("                                   Can be a ZIP file or URL to a ZIP file");
		System.out.println("-executable <name>              ... name of the executable, e.g. 'mygame', without extension");
		System.out.println("-appjar <file>                     ... JAR file containing code and assets to be packed");
		System.out.println("-config <file>                  ... JSON config file to be packed");
		System.out.println("-treeshake <mainclass>          ... whether to perform tree shaking on the JRE rt.jar.");
		System.out.println("                                    Any dependencies of the main class will be kept");
		System.out.println("-excludejre <files-and-classes> ... list of files, directories, packages and classes to exclude");
		System.out.println("                                    from the final JRE. Entries are separated by a ;");
		System.out.println("-includejre <files-and-classes> ... list of files, directories, packages and classes to exclude");
		System.out.println("                                    from the final JRE. Entries are separated by a ;");
		System.out.println("-resources <files-and-folders>  ... additional files and folders to be packed next to the");
		System.out.println("                                    executable. Entries are separated by a ;");
		System.out.println("-outdir <dir>                   ... output directory");
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
		if(params.get("config") == null) error();
		if(params.get("outdir") == null) error();
		
		return params;
	}
}