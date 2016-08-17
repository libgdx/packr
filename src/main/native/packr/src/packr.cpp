/*******************************************************************************
 * Copyright 2015 See AUTHORS file.
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
#include "packr.h"

#include <dropt.h>
#include <sajson.h>

#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

using namespace std;

static bool verbose = false;
static string workingDir;
static string executableName;
static string configurationPath("config.json");

static size_t cmdLineArgc = 0;
static char** cmdLineArgv = nullptr;

#define verify(env, pointer) \
	if (checkExceptionAndResult(env, pointer)) return EXIT_FAILURE;

static bool checkExceptionAndResult(JNIEnv* env, void* pointer) {
	if (env->ExceptionOccurred() != nullptr) {
		env->ExceptionDescribe();
		env->ExceptionClear();
		return true;
	}
	return pointer == nullptr;
}

static int loadStaticMethod(JNIEnv* env, const vector<string>& classPath, string className, jclass* resultClass, jmethodID* resultMethod) {

	//! Method to retrieve 'static void main(String[] args)' from a user-defined class path.
	//! The original 'packr' passes "-Djava.class.path=<path-to-jar>" as an argument during
	//! initialization of the JVM. For some reason this didn't work for me on *some* systems.
	//!
	//! This method uses JNI voodoo to get the thread context classloader, construct a file
	//! URL, point it to the user JAR, then use the classloader to load the application class'
	//! static main() method.
	//!
	//! References:
	//! http://stackoverflow.com/questions/20328012/c-plugin-jni-java-classpath
	//! http://www.java-gaming.org/index.php/topic,6516.0

	size_t cp = 0;
	size_t numCp = classPath.size();

	if (verbose) {
		cout << "Adding " << numCp << " classpaths ..." << endl;
	}

	jclass urlClass = env->FindClass("java/net/URL");
	verify(env, urlClass);

	jobjectArray urlArray = env->NewObjectArray(numCp, urlClass, nullptr);
	verify(env, urlArray);

	for (string classPathURL : classPath) {

		if (verbose) {
			cout << "  # " << classPathURL << endl;
		}

		jstring urlStr = env->NewStringUTF(classPathURL.c_str());
		verify(env, urlStr);

		// URL url = new File("{classPathURL}").toURI().toURL();

		jclass fileClass = env->FindClass("java/io/File");
		verify(env, fileClass);

		jmethodID fileCtor = env->GetMethodID(fileClass, "<init>", "(Ljava/lang/String;)V");
		verify(env, fileCtor);

		jobject file = env->NewObject(fileClass, fileCtor, urlStr);
		verify(env, file);

		jmethodID toUriMethod = env->GetMethodID(fileClass, "toURI", "()Ljava/net/URI;");
		verify(env, toUriMethod);

		jobject uri = env->CallObjectMethod(file, toUriMethod);
		verify(env, uri);

		jclass uriClass = env->FindClass("java/net/URI");
		verify(env, uriClass);

		jmethodID toUrlMethod = env->GetMethodID(uriClass, "toURL", "()Ljava/net/URL;");
		verify(env, toUrlMethod);

		jobject url = env->CallObjectMethod(uri, toUrlMethod);
		verify(env, url);

		env->SetObjectArrayElement(urlArray, cp++, url);
	}

	// Thread thread = Thread.currentThread();

	jclass threadClass = env->FindClass("java/lang/Thread");
	verify(env, threadClass);

	jmethodID threadGetCurrent = env->GetStaticMethodID(threadClass, "currentThread", "()Ljava/lang/Thread;");
	verify(env, threadGetCurrent);

	jobject thread = env->CallStaticObjectMethod(threadClass, threadGetCurrent);
	verify(env, thread);

	// ClassLoader contextClassLoader = thread.getContextClassLoader();

	jmethodID threadGetLoader = env->GetMethodID(threadClass, "getContextClassLoader", "()Ljava/lang/ClassLoader;");
	verify(env, threadGetLoader);

	jobject contextClassLoader = env->CallObjectMethod(thread, threadGetLoader);
	verify(env, contextClassLoader);

    // URLClassLoader urlClassLoader = new URLClassLoader(urlArray, contextClassLoader);
    jclass urlClassLoaderClass = env->FindClass("java/net/URLClassLoader");
    verify(env, urlClassLoaderClass);
	
	jmethodID urlClassLoaderCtor = env->GetMethodID(urlClassLoaderClass, "<init>", "([Ljava/net/URL;Ljava/lang/ClassLoader;)V");
	verify(env, urlClassLoaderCtor);

	jobject urlClassLoader = env->NewObject(urlClassLoaderClass, urlClassLoaderCtor, urlArray, contextClassLoader);
	verify(env, urlClassLoader);
    
    // Set the contextClassLoader so that if the main-class loads classes later it has the right class loader.
    // Tested with springframework that uses the context class loader to load resources from the Jar.
    
    // Thread.currentThread().setContextClassLoader(urlClassLoader)
    jmethodID threadSetLoader = env->GetMethodID(threadClass, "setContextClassLoader", "(Ljava/lang/ClassLoader;)V");
    verify(env, threadSetLoader);
    env->CallVoidMethod(thread, threadSetLoader, urlClassLoader);

	// Class<?> mainClass = urlClassLoader.loadClass(<main-class-name>)

	jmethodID loadClass = env->GetMethodID(urlClassLoaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
	verify(env, loadClass);

	jstring mainClassNameUTF = env->NewStringUTF(className.c_str());
	verify(env, mainClassNameUTF);

	jobject mainClass = env->CallObjectMethod(urlClassLoader, loadClass, mainClassNameUTF);
	verify(env, mainClass);

	// method: 'void main(String[])'

	jmethodID mainMethod = env->GetStaticMethodID((jclass) mainClass, "main", "([Ljava/lang/String;)V");
	verify(env, mainMethod);

	*resultClass = (jclass) mainClass;
	*resultMethod = mainMethod;

	return 0;
}

static sajson::document readConfigurationFile(string fileName) {

	ifstream in(fileName.c_str(), std::ios::in | std::ios::binary);
	string content((istreambuf_iterator<char>(in)), (istreambuf_iterator<char>()));

	sajson::document json = sajson::parse(sajson::literal(content.c_str()));
	return json;
}

static bool hasJsonValue(sajson::value jsonObject, const char* key, sajson::type expectedType) {
	size_t index = jsonObject.find_object_key(sajson::literal(key));
	if (index == jsonObject.get_length()) {
		return false;
	}
	sajson::value value = jsonObject.get_object_value(index);
	return value.get_type() == expectedType;
}

static sajson::value getJsonValue(sajson::value jsonObject, const char* key) {
	size_t index = jsonObject.find_object_key(sajson::literal(key));
	return jsonObject.get_object_value(index);
}

static vector<string> extractClassPath(const sajson::value& classPath) {

	size_t count = classPath.get_length();
	vector<string> paths;

	for (size_t cp = 0; cp < count; cp++) {

		string classPathURL = classPath.get_array_element(cp).as_string();

		// TODO: don't just test for file extension
		if (classPathURL.rfind(".txt") != classPathURL.length() - 4) {

			paths.push_back(classPathURL);

		} else {

			ifstream txt(classPathURL.c_str());
			string line;

			while (!txt.eof()) {

				txt >> line;

				if (line.find("-classpath") == 0) {

					txt >> line;

					istringstream iss(line);
					string path;

					while (getline(iss, path, __CLASS_PATH_DELIM)) {
						paths.push_back(path);
					}

					break;
				}

			}

			txt.close();

		}

	}

	return paths;
}

string getExecutableDirectory(const char* executablePath) {

	const char* delim = strrchr(executablePath, '/');
	if (delim == nullptr) {
		delim = strrchr(executablePath, '\\');
	}

	if (delim != nullptr) {
		return string(executablePath, delim - executablePath);
	}

	return string("");
}

string getExecutableName(const char* executablePath) {

	const char* delim = strrchr(executablePath, '/');
	if (delim == nullptr) {
		delim = strrchr(executablePath, '\\');
	}

	if (delim != nullptr) {
		return string(++delim);
	}

	return string(executablePath);
}

bool setCmdLineArguments(int argc, char** argv) {

	const char* executablePath = getExecutablePath(argv[0]);
	workingDir = getExecutableDirectory(executablePath);
	executableName = getExecutableName(executablePath);

	dropt_bool showHelp = 0;
	dropt_bool showVersion = 0;
	dropt_char* cwd = nullptr;
	dropt_char* config = nullptr;
	dropt_bool _verbose = 0;
	dropt_bool _console = 0;
	dropt_bool _cli = 0;

	dropt_option options[] = {
		{ 'c', "cli", "Enables this command line interface.", NULL, dropt_handle_bool, &_cli, dropt_attr_optional_val },
		{ 'h',  "help", "Shows help.", NULL, dropt_handle_bool, &showHelp, dropt_attr_halt },
		{ '?', NULL, NULL, NULL, dropt_handle_bool, &showHelp, dropt_attr_halt | dropt_attr_hidden },
		{ '\0', "version", "Shows version information.", NULL, dropt_handle_bool, &showVersion, dropt_attr_halt },
		{ '\0', "cwd", "Sets the working directory.", NULL, dropt_handle_string, &cwd, dropt_attr_optional_val },
		{ '\0', "config", "Specifies the configuration file.", "config.json", dropt_handle_string, &config, dropt_attr_optional_val },
		{ 'v', "verbose", "Prints additional information.", NULL, dropt_handle_bool, &_verbose, dropt_attr_optional_val },
		{ '\0', "console", "Attachs a console window. [Windows only]", NULL, dropt_handle_bool, &_console, dropt_attr_optional_val },
		{ 0, NULL, NULL, NULL, NULL, NULL, 0 }
	};

	dropt_context* droptContext = dropt_new_context(options);

	if (droptContext == nullptr) {
		cerr << "Error: failed to parse command line!" << endl;
		exit(EXIT_FAILURE);
	}

	if (argc > 1) {

		char** remains = nullptr;

		if ((strcmp("--cli", argv[1]) == 0) || (strcmp("-c", argv[1]) == 0)) {

			// only parse command line if the first argument is "--cli"

			remains = dropt_parse(droptContext, -1, &argv[1]);

			if (dropt_get_error(droptContext) != dropt_error_none) {
				cerr << dropt_get_error_message(droptContext) << endl;
				exit(EXIT_FAILURE);
			}

			if (showHelp) {

				cout << "Usage: " << executableName << " [java arguments]" << endl;
				cout << "       " << executableName << " -c [options] [-- [java arguments]]" << endl;
				cout << endl << "Options:" << endl;

				dropt_print_help(stdout, droptContext, nullptr);

			} else if (showVersion) {

				cout << executableName << " version " << PACKR_VERSION_STRING << endl;

			} else {

				// evalute parameters

				verbose = _verbose != 0;

				if (cwd != nullptr) {
					cout << "Using working directory " << cwd << " ..." << endl;
					workingDir = string(cwd);
				}

				if (config != nullptr) {
					cout << "Using configuration file " << config << " ..." << endl;
					configurationPath = string(config);
				}

			}

		} else {
			// treat all arguments as "remains"
			remains = &argv[1];
		}

		// count number of unparsed arguments

		char** cnt = remains;
		while (*cnt != nullptr) {
			cmdLineArgc++;
			cnt++;
		}

		// copy unparsed arguments

		cmdLineArgv = new char*[cmdLineArgc];
		cmdLineArgc = 0;

		while (*remains != nullptr) {
			cmdLineArgv[cmdLineArgc] = strdup(*remains);
			cmdLineArgc++;
			remains++;
		}

	}

	dropt_free_context(droptContext);

	return showHelp == 0 && showVersion == 0;
}

void launchJavaVM(LaunchJavaVMCallback callback) {

	// change working directory

	if (!workingDir.empty()) {
		if (verbose) {
			cout << "Changing working directory to " << workingDir << " ..." << endl;
		}
		if (!changeWorkingDir(workingDir.c_str())) {
			cerr << "Warning: failed to change working directory to " << workingDir << endl;
		}
	}

	// read settings

	sajson::document json = readConfigurationFile(configurationPath);

	if (!json.is_valid()) {
		cerr << "Error: failed to load configuration: " << configurationPath << endl;
		exit(EXIT_FAILURE);
	}

	sajson::value jsonRoot = json.get_root();

	// load JVM library, get function pointers

	cout << "Loading JVM runtime library ..." << endl;

	GetDefaultJavaVMInitArgs getDefaultJavaVMInitArgs = nullptr;
	CreateJavaVM createJavaVM = nullptr;

	if (!loadJNIFunctions(&getDefaultJavaVMInitArgs, &createJavaVM)) {
		cerr << "Error: failed to load VM runtime library!" << endl;
		exit(EXIT_FAILURE);
	}

	// get default init arguments

	JavaVMInitArgs args;
	args.version = JNI_VERSION_1_6;
	args.options = nullptr;
	args.nOptions = 0;
	args.ignoreUnrecognized = JNI_TRUE;

	if (hasJsonValue(jsonRoot, "jniVersion", sajson::TYPE_INTEGER)) {
		sajson::value jniVersion = getJsonValue(jsonRoot, "jniVersion");
		switch (jniVersion.get_integer_value()) {
			case 8:
				args.version = JNI_VERSION_1_8;
				break;
			default:
				args.version = JNI_VERSION_1_6;
				break;
		}
	}

	if (getDefaultJavaVMInitArgs(&args) < 0) {
		cerr << "Error: failed to load default Java VM arguments!" << endl;
		exit(EXIT_FAILURE);
	}

	// fill VM options

	cout << "Passing VM options ..." << endl;

	size_t vmArgc = 0;
	JavaVMOption* options = nullptr;

	if (hasJsonValue(jsonRoot, "vmArgs", sajson::TYPE_ARRAY)) {
		sajson::value vmArgs = getJsonValue(jsonRoot, "vmArgs");
		vmArgc = vmArgs.get_length();

		options = new JavaVMOption[vmArgc];
		for (size_t vmArg = 0; vmArg < vmArgc; vmArg++) {
			string vmArgValue = vmArgs.get_array_element(vmArg).as_string();
			cout << "  # " << vmArgValue << endl;
			options[vmArg].optionString = strdup(vmArgValue.c_str());
			options[vmArg].extraInfo = nullptr;
		}
	}

	args.nOptions = vmArgc;
	args.options = options;

	/*
		Reroute JVM creation through platform-dependent code.

		On OS X this is used to decide if packr needs to spawn an additional thread, and create
		its own RunLoop.

		Done as lambda to capture local variables, and remain in function scope.
	*/

	callback([&](void*) {

		// create JVM

		JavaVM* jvm = nullptr;
		JNIEnv* env = nullptr;

		cout << "Creating Java VM ..." << endl;

		if (createJavaVM(&jvm, (void**) &env, &args) < 0) {
			cout << "Error: failed to create Java VM!" << endl;
			exit(EXIT_FAILURE);
		}

		// create array of arguments to pass to Java main()

		cout << "Passing command line arguments ..." << endl;

		jobjectArray appArgs = env->NewObjectArray(cmdLineArgc, env->FindClass("java/lang/String"), nullptr);
		for (size_t i = 0; i < cmdLineArgc; i++) {
			cout << "  # " << cmdLineArgv[i] << endl;
			jstring arg = env->NewStringUTF(cmdLineArgv[i]);
			env->SetObjectArrayElement(appArgs, i, arg);
		}

		// load main class & method from classpath

		cout << "Loading JAR file ..." << endl;

		if (!hasJsonValue(jsonRoot, "mainClass", sajson::TYPE_STRING)) {
			cerr << "Error: no 'mainClass' element found in config!" << endl;
			exit(EXIT_FAILURE);
		}

		if (!hasJsonValue(jsonRoot, "classPath", sajson::TYPE_ARRAY)) {
			cerr << "Error: no 'classPath' array found in config!" << endl;
			exit(EXIT_FAILURE);
		}

		const string main = getJsonValue(jsonRoot, "mainClass").as_string();
		sajson::value jsonClassPath = getJsonValue(jsonRoot, "classPath");
		vector<string> classPath = extractClassPath(jsonClassPath);

		jclass mainClass = nullptr;
		jmethodID mainMethod = nullptr;

		if (loadStaticMethod(env, classPath, main, &mainClass, &mainMethod) != 0) {
			cerr << "Error: failed to load/find main class " << main << endl;
			exit(EXIT_FAILURE);
		}

		// call main() method

		cout << "Invoking static " << main << ".main() function ..." << endl;

		env->CallStaticVoidMethod(mainClass, mainMethod, appArgs);

		// cleanup

		for (size_t vmArg = 0; vmArg < vmArgc; vmArg++) {
			free(options[vmArg].optionString);
		}

		delete[] options;

		for (size_t cmdLineArg = 0; cmdLineArg < cmdLineArgc; cmdLineArg++) {
			free(cmdLineArgv[cmdLineArg]);
		}

		delete[] cmdLineArgv;

		// blocks this thread until the Java main() method exits

		jvm->DestroyJavaVM();

		cout << "Destroyed Java VM ..." << endl;

		return nullptr;
	}, args);
}
