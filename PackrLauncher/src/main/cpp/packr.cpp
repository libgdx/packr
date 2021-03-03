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

#include "dropt.h"
#include "sajson.h"

#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>
#include <memory>
#include <cstring>

#include <locale>
#include <codecvt>
#ifdef UNICODE
#define stringCompare wcscmp
#define findLastCharacter wcsrchr
#else
#define stringCompare strcmp
#define findLastCharacter strrchr
#endif

using namespace std;

bool verbose = false;

/**
 * UTF-8 encoded working directory.
 */
static string workingDir;
static string executableName;
static string configurationPath;

static size_t cmdLineArgc = 0;

/**
 * UTF-8 encoded command line options for passing to the JVM.
 */
static char **cmdLineArgv = nullptr;

#define verify(env, pointer) \
    if (checkExceptionAndResult(env, pointer)) return EXIT_FAILURE;

static bool checkExceptionAndResult(JNIEnv *env, void *pointer) {
    if (env->ExceptionOccurred() != nullptr) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        return true;
    }
    return pointer == nullptr;
}

static int loadStaticMethod(JNIEnv *env, const vector<string> &classPath, const string &className, jclass *resultClass, jmethodID *resultMethod) {

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
    verify(env, urlClass)

    jobjectArray urlArray = env->NewObjectArray(numCp, urlClass, nullptr);
    verify(env, urlArray)

    for (const string &classPathURL : classPath) {

        if (verbose) {
            cout << "  # " << classPathURL << endl;
        }

        jstring urlStr = env->NewStringUTF(classPathURL.c_str());
        verify(env, urlStr)

        // URL url = new File("{classPathURL}").toURI().toURL();

        jclass fileClass = env->FindClass("java/io/File");
        verify(env, fileClass)

        jmethodID fileCtor = env->GetMethodID(fileClass, "<init>", "(Ljava/lang/String;)V");
        verify(env, fileCtor)

        jobject file = env->NewObject(fileClass, fileCtor, urlStr);
        verify(env, file)

        jmethodID toUriMethod = env->GetMethodID(fileClass, "toURI", "()Ljava/net/URI;");
        verify(env, toUriMethod)

        jobject uri = env->CallObjectMethod(file, toUriMethod);
        verify(env, uri)

        jclass uriClass = env->FindClass("java/net/URI");
        verify(env, uriClass)

        jmethodID toUrlMethod = env->GetMethodID(uriClass, "toURL", "()Ljava/net/URL;");
        verify(env, toUrlMethod)

        jobject url = env->CallObjectMethod(uri, toUrlMethod);
        verify(env, url)

        env->SetObjectArrayElement(urlArray, cp++, url);
    }

    // Thread thread = Thread.currentThread();

    jclass threadClass = env->FindClass("java/lang/Thread");
    verify(env, threadClass)

    jmethodID threadGetCurrent = env->GetStaticMethodID(threadClass, "currentThread", "()Ljava/lang/Thread;");
    verify(env, threadGetCurrent)

    jobject thread = env->CallStaticObjectMethod(threadClass, threadGetCurrent);
    verify(env, thread)

    // ClassLoader contextClassLoader = thread.getContextClassLoader();

    jmethodID threadGetLoader = env->GetMethodID(threadClass, "getContextClassLoader", "()Ljava/lang/ClassLoader;");
    verify(env, threadGetLoader)

    jobject contextClassLoader = env->CallObjectMethod(thread, threadGetLoader);
    verify(env, contextClassLoader)

    // URLClassLoader urlClassLoader = new URLClassLoader(urlArray, contextClassLoader);

    jclass urlClassLoaderClass = env->FindClass("java/net/URLClassLoader");
    verify(env, urlClassLoaderClass)

    jmethodID urlClassLoaderCtor = env->GetMethodID(urlClassLoaderClass, "<init>", "([Ljava/net/URL;Ljava/lang/ClassLoader;)V");
    verify(env, urlClassLoaderCtor)

    jobject urlClassLoader = env->NewObject(urlClassLoaderClass, urlClassLoaderCtor, urlArray, contextClassLoader);
    verify(env, urlClassLoader)

    // thread.setContextClassLoader(urlClassLoader)

    jmethodID threadSetLoader = env->GetMethodID(threadClass, "setContextClassLoader", "(Ljava/lang/ClassLoader;)V");
    verify(env, threadSetLoader)

    env->CallVoidMethod(thread, threadSetLoader, urlClassLoader);

    // Class<?> mainClass = urlClassLoader.loadClass(<main-class-name>)

    jmethodID loadClass = env->GetMethodID(urlClassLoaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    verify(env, loadClass)

    jstring mainClassNameUTF = env->NewStringUTF(className.c_str());
    verify(env, mainClassNameUTF)

    jobject mainClass = env->CallObjectMethod(urlClassLoader, loadClass, mainClassNameUTF);
    verify(env, mainClass)

    // method: 'void main(String[])'

    jmethodID mainMethod = env->GetStaticMethodID((jclass) mainClass, "main", "([Ljava/lang/String;)V");
    verify(env, mainMethod)

    *resultClass = (jclass) mainClass;
    *resultMethod = mainMethod;

    return 0;
}

static sajson::document readConfigurationFile(const string &fileName) {
#ifdef UNICODE
    wstring_convert<std::codecvt_utf8_utf16<wchar_t>> converter;
    wstring fileNameWstring = converter.from_bytes(fileName);
    // On Windows, fstream's constructor accepts a wchar_t file name that is unicode.
    std::fstream in(fileNameWstring.c_str(), std::ios::in | std::ios::binary);
#else
    ifstream in(fileName.c_str(), std::ios::in | std::ios::binary);
#endif
    string content = string((istreambuf_iterator<char>(in)), (istreambuf_iterator<char>()));
    sajson::document json = sajson::parse(sajson::literal(content.c_str()));
    return json;
}

static bool hasJsonValue(sajson::value jsonObject, const char *key, sajson::type expectedType) {
    size_t index = jsonObject.find_object_key(sajson::literal(key));
    if (index == jsonObject.get_length()) {
        return false;
    }
    sajson::value value = jsonObject.get_object_value(index);
    return value.get_type() == expectedType;
}

static sajson::value getJsonValue(sajson::value jsonObject, const char *key) {
    size_t index = jsonObject.find_object_key(sajson::literal(key));
    return jsonObject.get_object_value(index);
}

static vector<string> extractClassPath(const sajson::value &classPath) {

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

/**
 * Searches for the last / or \ and returns all prior characters.
 *
 * @param executablePath to get the parent directory of
 * @return the parent directory of {@code executablePath}. The returned string is UTF-8 encoded.
 */
string getExecutableDirectory(const dropt_char *executablePath) {
    const dropt_char *lastSlash = findLastCharacter(executablePath, DROPT_TEXT_LITERAL('/'));
    if (lastSlash == nullptr) {
        lastSlash = findLastCharacter(executablePath, DROPT_TEXT_LITERAL('\\'));
    }

    if (lastSlash != nullptr) {
#ifdef UNICODE
        wstring_convert<std::codecvt_utf8_utf16<wchar_t>> converter;
        return converter.to_bytes(executablePath, lastSlash);
#else
        return string(executablePath, lastSlash - executablePath);
#endif
    }

    return string("");
}

/**
 * Finds the last path element by searching for a / or \, all text found after the last / is returned. If a / or \ isn't found then {@code executablePath} is returned UTF-8 encoded in a string.
 *
 * @param executablePath the path to find the executable in
 * @return UTF-8 encoded executable name from {@code executablePath}
 */
string getExecutableName(const dropt_char *executablePath) {
    const dropt_char *delim = findLastCharacter(executablePath, DROPT_TEXT_LITERAL('/'));
    if (delim == nullptr) {
        delim = findLastCharacter(executablePath, DROPT_TEXT_LITERAL('\\'));
    }

    if (delim != nullptr) {
#ifdef UNICODE
        wstring_convert<std::codecvt_utf8_utf16<wchar_t>> converter;
        return converter.to_bytes(++delim);
#else
        return string(++delim);
#endif
    }

#ifdef UNICODE
    wstring_convert<std::codecvt_utf8_utf16<wchar_t>> converter;
    string utf8Path = converter.to_bytes(executablePath);
    return utf8Path;
#else
    return string(executablePath);
#endif
}

/**
 * Strips ".exe" suffix from the executable name and appends ".json".
 * @param executableName the UTF-8 encoded executable name
 * @return UTF-8 encoded configuration path
 */
string getDefaultConfigurationPath(string& executableName) {
    wstring_convert<codecvt_utf8_utf16<wchar_t>> converter;
    wstring executableNameWstring = converter.from_bytes(executableName);
    wstring exeSuffix = wstring(L".exe");
    bool hasExeSuffix = executableNameWstring.size() >= exeSuffix.size() &&
        executableNameWstring.compare(executableNameWstring.size() - exeSuffix.size(),
            exeSuffix.size(), exeSuffix) == 0;
    wstring appName;
    if (hasExeSuffix)
        appName = executableNameWstring.substr(0, executableNameWstring.size() - exeSuffix.size());
    else
        appName = executableNameWstring;
    wstring defaultConfigurationPath = appName + L".json";
    return converter.to_bytes(defaultConfigurationPath);
}

bool setCmdLineArguments(int argc, dropt_char **argv) {
    const dropt_char *executablePath = getExecutablePath(argv[0]);
    workingDir = getExecutableDirectory(executablePath);
    executableName = getExecutableName(executablePath);
    string defaultConfigurationPath = getDefaultConfigurationPath(executableName);
    const dropt_char* defaultConfigurationPathDroptChar;
#ifdef UNICODE
    wstring_convert<std::codecvt_utf8_utf16<wchar_t>> converter;
    defaultConfigurationPathDroptChar = converter.from_bytes(defaultConfigurationPath).c_str();
#else
    defaultConfigurationPathDroptChar = defaultConfigurationPath.c_str();
#endif

    dropt_bool showHelp = 0;
    dropt_bool showVersion = 0;
    dropt_char *cwd = nullptr;
    dropt_char *config = nullptr;
    dropt_bool _verbose = 0;
    dropt_bool _console = 0;
    dropt_bool _cli = 0;

    dropt_option options[] = {{'c',
                               DROPT_TEXT_LITERAL("cli"),
                               DROPT_TEXT_LITERAL("Enables this command line interface."),
                               nullptr,
                               dropt_handle_bool,
                               &_cli,
                               dropt_attr_optional_val},
                              {'h', DROPT_TEXT_LITERAL("help"), DROPT_TEXT_LITERAL("Shows help."), nullptr, dropt_handle_bool, &showHelp, dropt_attr_halt},
                              {'?',
                               nullptr,
                               nullptr,
                               nullptr,
                               dropt_handle_bool,
                               &showHelp,
                               static_cast<unsigned long>(dropt_attr_halt) | static_cast<unsigned long>(dropt_attr_hidden)},
                              {'\0',
                               DROPT_TEXT_LITERAL("version"),
                               DROPT_TEXT_LITERAL("Shows version information."),
                               nullptr,
                               dropt_handle_bool,
                               &showVersion,
                               dropt_attr_halt},
                              {'\0',
                               DROPT_TEXT_LITERAL("cwd"),
                               DROPT_TEXT_LITERAL("Sets the working directory."),
                               nullptr,
                               dropt_handle_string,
                               &cwd,
                               dropt_attr_optional_val},
                              {'\0',
                               DROPT_TEXT_LITERAL("config"),
                               DROPT_TEXT_LITERAL("Specifies the configuration file."),
                               defaultConfigurationPathDroptChar,
                               dropt_handle_string,
                               &config,
                               dropt_attr_optional_val},
                              {'v',
                               DROPT_TEXT_LITERAL("verbose"),
                               DROPT_TEXT_LITERAL("Prints additional information."),
                               nullptr,
                               dropt_handle_bool,
                               &_verbose,
                               dropt_attr_optional_val},
                              {'\0',
                               DROPT_TEXT_LITERAL("console"),
                               DROPT_TEXT_LITERAL("Attaches a console window. [Windows only]"),
                               nullptr,
                               dropt_handle_bool,
                               &_console,
                               dropt_attr_optional_val},
                              {0, nullptr, nullptr, nullptr, nullptr, nullptr, 0}};

    dropt_context *droptContext = dropt_new_context(options);

    if (droptContext == nullptr) {
        cerr << "Error: failed to parse command line!" << endl;
        exit(EXIT_FAILURE);
    }

    if (argc > 1) {

        dropt_char **remains;

        if ((stringCompare(DROPT_TEXT_LITERAL("--cli"), argv[1]) == 0) || (stringCompare(DROPT_TEXT_LITERAL("-c"), argv[1]) == 0)) {
            // only parse command line if the first argument is "--cli" or "-c"

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
                // evaluate parameters
                verbose = _verbose != 0;

                if (cwd != nullptr) {
                    if (verbose) {
                        cout << "Using working directory " << cwd << " ..." << endl;
                    }
                    workingDir = string((char *) cwd);
                }

                if (config != nullptr) {
#ifdef UNICODE
                    configurationPath = converter.to_bytes(wstring(config));
#else
                    configurationPath = string(config);
#endif
                    if (verbose) {
                        cout << "Using custom configuration file " << configurationPath << " ..." << endl;
                    }
                }
                else {
                    if (verbose) {
                        cout << "Using default configuration file " << defaultConfigurationPath << " ..." << endl;
                    }
                    configurationPath = defaultConfigurationPath;
                }
            }
        } else {
            // treat all arguments as "remains"
            remains = &argv[1];
            if (verbose) {
                cout << "Using default configuration file " << defaultConfigurationPath << " ..." << endl;
            }
            configurationPath = defaultConfigurationPath;
        }

        // count number of unparsed arguments
        dropt_char **cnt = remains;
        while (*cnt != nullptr) {
            cmdLineArgc++;
            cnt++;
        }

        // copy unparsed arguments
        cmdLineArgv = new char *[cmdLineArgc];
        cmdLineArgc = 0;

        while (*remains != nullptr) {
#ifdef UNICODE
            string utf8CommandLineArgument = converter.to_bytes(*remains);
            cmdLineArgv[cmdLineArgc] = strdup(utf8CommandLineArgument.c_str());
#else
            cmdLineArgv[cmdLineArgc] = strdup(*remains);
#endif
            cmdLineArgc++;
            remains++;
        }
    } else {
		configurationPath = defaultConfigurationPath;
	}

    dropt_free_context(droptContext);

    return showHelp == 0 && showVersion == 0;
}

void launchJavaVM(const LaunchJavaVMCallback &callback) {
    // change working directory
    if (!workingDir.empty()) {
        if (verbose) {
            cout << "Changing working directory to " << workingDir << " ..." << endl;
        }
#ifdef UNICODE
        wstring_convert<std::codecvt_utf8_utf16<wchar_t>> converter;
        wstring workingDirectoryUnicode = converter.from_bytes(workingDir);
        if (!changeWorkingDir(workingDirectoryUnicode.c_str())) {
            cerr << "Warning: failed to change working directory (unicode) to '" << workingDir << "'" << endl;
        }
#else
        if (!changeWorkingDir(workingDir.c_str())) {
            cerr << "Warning: failed to change working directory to " << workingDir << endl;
        }
#endif
    }

    // read settings
    sajson::document json = readConfigurationFile(configurationPath);

    if (!json.is_valid()) {
        cerr << "Error: failed to load configuration: " << configurationPath << endl;
        exit(EXIT_FAILURE);
    }

    sajson::value jsonRoot = json.get_root();

    // load JVM library, get function pointers
    if (verbose) {
        cout << "Loading JVM runtime library ..." << endl;
    }

    GetDefaultJavaVMInitArgs getDefaultJavaVMInitArgs = nullptr;
    CreateJavaVM createJavaVM = nullptr;
    wstring trimmedJrePathWstring;
    string trimmedJrePathString;
    const dropt_char* jrePath = nullptr;
    if (hasJsonValue(jsonRoot, "jrePath", sajson::TYPE_STRING)) {
        const string originalJrePathString = getJsonValue(jsonRoot, "jrePath").as_string();
        wstring_convert<std::codecvt_utf8_utf16<wchar_t>> converter;
        wstring originalJrePathWstring = converter.from_bytes(originalJrePathString);
        trimmedJrePathWstring = originalJrePathWstring;
        // Removes trailing slash.
        if (L'/' == trimmedJrePathWstring.back())
            trimmedJrePathWstring.pop_back();
#ifdef UNICODE
        jrePath = trimmedJrePathWstring.c_str();
#else
        trimmedJrePathString = converter.to_bytes(trimmedJrePathWstring);
        jrePath = trimmedJrePathString.c_str();
#endif
    }
    else {
        jrePath = DROPT_TEXT_LITERAL("jre");
    }
    if (!loadJNIFunctions(jrePath, &getDefaultJavaVMInitArgs, &createJavaVM)) {
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
            case 8:args.version = JNI_VERSION_1_8;
                break;
            default:args.version = JNI_VERSION_1_6;
                break;
        }
    }

    if (getDefaultJavaVMInitArgs(&args) < 0) {
        cerr << "Error: failed to load default Java VM arguments!" << endl;
        exit(EXIT_FAILURE);
    }

    // fill VM options
    if (verbose) {
        cout << "Passing VM options ..." << endl;
    }

    vector<JavaVMOption> optionsVector;
    vector<unique_ptr<char *>> optionStrings;

    if (verbose) {
        cout
                << "isZgcSupported()="
                << isZgcSupported()
                << ", hasJsonValue(jsonRoot, \"useZgcIfSupportedOs\", sajson::TYPE_TRUE)="
                << hasJsonValue(jsonRoot, "useZgcIfSupportedOs", sajson::TYPE_TRUE)
                << endl;
    }
    if (isZgcSupported() && hasJsonValue(jsonRoot, "useZgcIfSupportedOs", sajson::TYPE_TRUE)) {
        JavaVMOption unlockExperimental;
        unlockExperimental.optionString = (char *) "-XX:+UnlockExperimentalVMOptions";
        unlockExperimental.extraInfo = nullptr;
        optionsVector.push_back(unlockExperimental);
        JavaVMOption useZGC;
        useZGC.optionString = (char *) "-XX:+UseZGC";
        useZGC.extraInfo = nullptr;
        optionsVector.push_back(useZGC);
    }

    if (hasJsonValue(jsonRoot, "vmArgs", sajson::TYPE_ARRAY)) {
        sajson::value vmArgs = getJsonValue(jsonRoot, "vmArgs");

        for (size_t vmArg = 0; vmArg < vmArgs.get_length(); vmArg++) {
            string vmArgValue = vmArgs.get_array_element(vmArg).as_string();
            if (verbose) {
                cout << "  # " << vmArgValue << endl;
            }
            JavaVMOption option;
            optionStrings.push_back(make_unique<char *>(strdup(vmArgValue.c_str())));
            option.optionString = *optionStrings.back();
            option.extraInfo = nullptr;
            optionsVector.push_back(option);
        }
    }

    args.nOptions = optionsVector.size();
    args.options = &optionsVector[0];

    if (verbose) {
        cout << "Passing VM options:" << endl;
        for (int optionIndex = 0; optionIndex < args.nOptions; optionIndex++) {
            cout << "  " << args.options[optionIndex].optionString << endl;
        }
    }

    /*
        Reroute JVM creation through platform-dependent code.

        On OS X this is used to decide if packr needs to spawn an additional thread, and create
        its own RunLoop.

        Done as lambda to capture local variables, and remain in function scope.
    */

    callback([&](void *) {

        // create JVM

        JavaVM *jvm = nullptr;
        JNIEnv *env = nullptr;

        if (verbose) {
            cout << "Creating Java VM ..." << endl;
        }

        if (createJavaVM(&jvm, (void **) &env, &args) < 0) {
            cerr << "Error: failed to create Java VM!" << endl;
            exit(EXIT_FAILURE);
        }

        // create array of arguments to pass to Java main()

        if (verbose) {
            cout << "Passing command line arguments ..." << endl;
        }

        jobjectArray appArgs = env->NewObjectArray(cmdLineArgc, env->FindClass("java/lang/String"), nullptr);
        for (size_t i = 0; i < cmdLineArgc; i++) {
            if (verbose) {
                cout << "  # " << cmdLineArgv[i] << endl;
            }
            jstring arg = env->NewStringUTF(cmdLineArgv[i]);
            env->SetObjectArrayElement(appArgs, i, arg);
        }

        // load main class & method from classpath

        if (verbose) {
            cout << "Loading JAR file ..." << endl;
        }

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

        if (verbose) {
            cout << "Invoking static " << main << ".main() function ..." << endl;
        }

        env->CallStaticVoidMethod(mainClass, mainMethod, appArgs);
        jboolean exceptionOccurred = env->ExceptionCheck();
        if (verbose) {
            cout << "Checked for an exception from the main method, exceptionOccurred=" << (bool) exceptionOccurred << endl;
        }
        if (exceptionOccurred) {
            if (verbose) {
                cout << "Calling java.lang.Thread#dispatchUncaughtException(Throwable) on main thread" << endl;
            }
            jthrowable throwable = env->ExceptionOccurred();
            // Thread thread = Thread.currentThread();
            jclass threadClass = env->FindClass("java/lang/Thread");
            if (threadClass == nullptr) {
                cerr << "Couldn't load thread class";
                return nullptr;
            }
            jmethodID threadGetCurrent = env->GetStaticMethodID(threadClass, "currentThread", "()Ljava/lang/Thread;");
            if (threadGetCurrent == nullptr) {
                cerr << "Couldn't load current thread method";
                return nullptr;
            }
            jobject thread = env->CallStaticObjectMethod(threadClass, threadGetCurrent);
            if (thread == nullptr) {
                cerr << "Couldn't load thread current";
                return nullptr;
            }
            // call java.lang.Thread#dispatchUncaughtException(Throwable)
            jmethodID dispatchMethodId = env->GetMethodID(threadClass, "dispatchUncaughtException", "(Ljava/lang/Throwable;)V");
            if(threadClass== nullptr){
                cerr << "Couldn't find method dispatchUncaughtException";
                return nullptr;
            }
            env->CallVoidMethod(thread, dispatchMethodId, throwable);
            env->ExceptionClear();
        }

        // cleanup
        for (size_t cmdLineArg = 0; cmdLineArg < cmdLineArgc; cmdLineArg++) {
            free(cmdLineArgv[cmdLineArg]);
        }

        delete[] cmdLineArgv;

        // blocks this thread until the Java main() method exits

        jvm->DestroyJavaVM();

        if (verbose) {
            cout << "Destroyed Java VM ..." << endl;
        }

        return nullptr;
    }, args);
}
