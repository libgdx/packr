package com.badlogicgames.packr;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.zeroturnaround.zip.ZipUtil;

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
	
	public void pack(Platform platform, String jdk, String executable, String jar, String config, String outDir) throws IOException {
		// create output dir
		File out = new File(outDir);
		File target = out;
		if(out.exists()) {
			System.out.println("Output directory '" + out.getAbsolutePath() + "' exists, deleting");
			FileUtils.deleteDirectory(out);
		}
		out.mkdirs();
		
		Map<String, String> values = new HashMap<String, String>();
		values.put("${executable}", executable);
		values.put("${bundleIdentifier}", "com.yourcompany.identifier"); // FIXME add as a param
		
		// if this is a mac build, let's create the app bundle structure
		if(platform == Platform.mac) {
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
		switch(platform) {
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
		FileUtils.writeByteArrayToFile(new File(target, executable + extension), exe);
		new File(target, executable + extension).setExecutable(true);
		FileUtils.copyFile(new File(jar), new File(target, new File(jar).getName()));
		FileUtils.copyFile(new File(config), new File(target, "config.json"));
		
		// add JRE from local or remote zip file
		File jdkFile = null;		
		if(jdk.startsWith("http://") || jdk.startsWith("https://")) {
			System.out.println("Downloading JDK from '" + jdk + "'");
			jdkFile = new File(target, "jdk.zip");
			InputStream in = new URL(jdk).openStream();
			OutputStream outJdk = FileUtils.openOutputStream(jdkFile);
			IOUtils.copy(in, outJdk);
			in.close();
			outJdk.close();
		} else {
			jdkFile = new File(jdk);			
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
		if(jdk.startsWith("http://") || jdk.startsWith("https://")) {
			jdkFile.delete();
		}
		System.out.println("Done!");
	}
	
	private File searchJre(File tmp) {
		if(tmp.getName().equals("jre") && tmp.isDirectory() && new File(tmp, "bin/java").exists()) {
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
	
	private String readResourceAsString(String resource) throws IOException {
		return IOUtils.toString(Packr.class.getResourceAsStream(resource), "UTF-8");
	}
	
	private String replace (String txt, Map<String, String> values) {
		for (String key : values.keySet()) {
			String value = values.get(key);
			txt = txt.replace(key, value);
		}
		return txt;
	}
	
	public static void main(String[] args) throws IOException {
		Map<String, String> arguments = parseArgs(args);
		new Packr().pack(Platform.valueOf(arguments.get("platform")), arguments.get("jdk"), arguments.get("executable"), arguments.get("jar"), arguments.get("config"), arguments.get("outdir"));
	}
	
	private static void error() {
		printHelp();
		System.exit(-1);
	}
	
	private static void printHelp() {
		System.out.println("Usage: packr <args>");
		System.out.println("-platform <windows|linux|mac>  ... operating system to pack for");
		System.out.println("-jdk <path-or-url>             ... path to a JDK to be bundled (needs to fit platform). Can be a folder, ZIP file or URL");
		System.out.println("-executable <name>             ... name of the executable, e.g. 'mygame', without extension");
		System.out.println("-jar <file>                    ... JAR file containing code and assets to be packed");
		System.out.println("-config <file>                 ... JSON config file to be packed");
		System.out.println("-outdir <dir>                  ... output directory");
	}
	
	private static Map<String, String> parseArgs (String[] args) {
		if (args.length != 12) {
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
		if(params.get("jar") == null) error();
		if(params.get("config") == null) error();
		if(params.get("outdir") == null) error();
		
		return params;
	}
}
