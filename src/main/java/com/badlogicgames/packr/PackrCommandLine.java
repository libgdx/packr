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

import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

import java.io.File;
import java.util.List;

/**
 * Packr command line interface.
 *
 * Use 'java -jar packr[-X.Y-SNAPSHOT].jar --help' to show this command line help.
 */
public interface PackrCommandLine {

	@Option(helpRequest = true, description = "display help", shortName = "h", longName = "help")
	boolean help();

	@Option(description = "print additional information to console", shortName = "v", longName = "verbose")
	boolean verbose();

	@Unparsed
	File config();

	boolean isConfig();

	@Option(description = "target operating system", longName = "platform", defaultToNull = true)
	String platform();

	@Option(description = "file path or URL to a JDK to be bundled", longName = "jdk", defaultToNull = true)
	String jdk();

	@Option(description = "name of native executable, without extension", longName = "executable", defaultToNull = true)
	String executable();

	@Option(description = "JAR file(s) containing code and assets to be packed", longName = "classpath", defaultToNull = true)
	List<String> classpath();

	@Option(description = "fully qualified main class name, e.g. com.badlogic.MyApp", longName = "mainclass", defaultToNull = true)
	String mainClass();

	@Option(description = "arguments passed to the JVM, e.g. Xmx1G, without dashes", longName = "vmargs", defaultToNull = true)
	List<String> vmArgs();

	@Option(description = "minimize JRE by removing folders and files specified in config file", longName = "minimizejre", defaultToNull = true)
	String minimizeJre();

	@Option(description = "additional files and folders to be packed next to the executable", longName = "resources", defaultToNull = true)
	List<File> resources();

	@Option(description = "output directory", longName = "output", defaultToNull = true)
	File outDir();

	@Option(description = "file containing icon resources (needs to fit platform, OS X only)", longName = "icon", defaultToNull = true)
	File iconResource();

	@Option(description = "bundle identifier, e.g. com.badlogic (used for Info.plist on OS X)", longName = "bundle", defaultToNull = true)
	String bundleIdentifier();

}
