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
#ifdef __APPLE__

#include <packr.h>

#include <dlfcn.h>
#include <iostream>
#include <pthread.h>
#include <CoreFoundation/CoreFoundation.h>
#include <sys/param.h>
#include <unistd.h>

#include <ftw.h>

using namespace std;

const char __CLASS_PATH_DELIM = ':';

void sourceCallBack(void* info) {

}


/*
    Simple wrapper to call std::function from a C-style function
    signature. Usually one would use func.target<c-func>() to do
    conversion, but I failed to get this compiling with XCode.
*/
static LaunchJavaVMDelegate s_delegate = NULL;
void* launchVM(void* param) {
    return s_delegate(param);
}

int main(int argc, char** argv) {

    if (!setCmdLineArguments(argc, argv)) {
        return EXIT_FAILURE;
    }

    launchJavaVM([](LaunchJavaVMDelegate delegate, const JavaVMInitArgs& args) {

        for (jint arg = 0; arg < args.nOptions; arg++) {
            const char* optionString = args.options[arg].optionString;
            if (strcmp("-XstartOnFirstThread", optionString) == 0) {

                if (verbose) {
                    cout << "Starting JVM on main thread (-XstartOnFirstThread found) ..." << endl;
                }

                delegate(nullptr);
                return;
            }
        }

        // copy delegate; see launchVM() for remarks
        s_delegate = delegate;

        CFRunLoopSourceContext sourceContext;
        pthread_t vmthread;
        struct rlimit limit;
        size_t stack_size = 0;
        int rc = getrlimit(RLIMIT_STACK, &limit);
        if (rc == 0) {
            if (limit.rlim_cur != 0LL) {
                stack_size = (size_t)limit.rlim_cur;
            }
        }

        pthread_attr_t thread_attr;
        pthread_attr_init(&thread_attr);
        pthread_attr_setscope(&thread_attr, PTHREAD_SCOPE_SYSTEM);
        pthread_attr_setdetachstate(&thread_attr, PTHREAD_CREATE_DETACHED);
        if (stack_size > 0) {
            pthread_attr_setstacksize(&thread_attr, stack_size);
        }
        pthread_create(&vmthread, &thread_attr, launchVM, 0);
        pthread_attr_destroy(&thread_attr);

        /* Create a a sourceContext to be used by our source that makes */
        /* sure the CFRunLoop doesn't exit right away */
        sourceContext.version = 0;
        sourceContext.info = NULL;
        sourceContext.retain = NULL;
        sourceContext.release = NULL;
        sourceContext.copyDescription = NULL;
        sourceContext.equal = NULL;
        sourceContext.hash = NULL;
        sourceContext.schedule = NULL;
        sourceContext.cancel = NULL;
        sourceContext.perform = &sourceCallBack;

        CFRunLoopSourceRef sourceRef = CFRunLoopSourceCreate(NULL, 0, &sourceContext);
        CFRunLoopAddSource(CFRunLoopGetCurrent(), sourceRef, kCFRunLoopCommonModes);
        CFRunLoopRun();

    });

    return 0;
}

char libJliSearchPath[PATH_MAX];
int searchForLibJli(const char *filename, const struct stat *statptr, int fileflags, struct FTW *pfwt){
    if(fileflags == FTW_F && strstr(filename, "libjli.dylib")){
        if(verbose) {
            cout << "fileSearch found libjli! filename=" << filename << ", lastFileSearch=" << libJliSearchPath << endl;
        }
        strcpy(libJliSearchPath, filename);
        return 1;
    }
    return 0;
}

bool loadJNIFunctions(const dropt_char* jrePath, GetDefaultJavaVMInitArgs* getDefaultJavaVMInitArgs, CreateJavaVM* createJavaVM) {
    libJliSearchPath[0] = 0;
    nftw(jrePath, searchForLibJli, 5, FTW_CHDIR | FTW_DEPTH | FTW_MOUNT);

    string libJliAbsolutePath;
    char currentWorkingDirectoryPath[MAXPATHLEN];
    if (getcwd(currentWorkingDirectoryPath, sizeof(currentWorkingDirectoryPath))) {
        libJliAbsolutePath.append(currentWorkingDirectoryPath).append("/");
    }
    libJliAbsolutePath.append(libJliSearchPath);
    if(verbose) {
        cout << "Loading libjli=" << libJliAbsolutePath << endl;
    }
    void* handle = dlopen(libJliAbsolutePath.c_str(), RTLD_LAZY);
    if (handle == nullptr) {
        cerr << dlerror() << endl;
        return false;
    }

    *getDefaultJavaVMInitArgs = (GetDefaultJavaVMInitArgs) dlsym(handle, "JNI_GetDefaultJavaVMInitArgs");
    *createJavaVM = (CreateJavaVM) dlsym(handle, "JNI_CreateJavaVM");

    if ((*getDefaultJavaVMInitArgs == nullptr) || (*createJavaVM == nullptr)) {
        cerr << dlerror() << endl;
        return false;
    }

    return true;
}

extern "C" {
    int _NSGetExecutablePath(char* buf, uint32_t* bufsize);
}

const dropt_char* getExecutablePath(const dropt_char* argv0) {

    static char buf[MAXPATHLEN];
    uint32_t size = sizeof(buf);

    // first, try to obtain the MacOS bundle resources folder

    char resourcesDir[MAXPATHLEN];
    bool foundResources = false;

    CFBundleRef bundle = CFBundleGetMainBundle();
    if (bundle != NULL) {
        CFURLRef resources = CFBundleCopyResourcesDirectoryURL(bundle);
        if (resources != NULL) {
            foundResources = CFURLGetFileSystemRepresentation(resources, true, (UInt8*) resourcesDir, size);
            CFRelease(resources);
        }
    }

    // as a fallback, default to the executable path

    char executablePath[MAXPATHLEN];
    bool foundPath = _NSGetExecutablePath(executablePath, &size) != -1;

    // mangle path and executable name; the main application divides them again

    if (foundResources && foundPath) {
        const char* executableName = strrchr(executablePath, '/') + 1;
        strcpy(buf, resourcesDir);
        strcat(buf, "/");
        strcat(buf, executableName);
        if (verbose) {
            cout << "Using bundle resource folder [1]: " << resourcesDir << "/[" << executableName << "]" << endl;
        }
    } else if (foundResources) {
        strcpy(buf, resourcesDir);
        strcat(buf, "/packr");
        if (verbose) {
            cout << "Using bundle resource folder [2]: " << resourcesDir << endl;
        }
    } else if (foundPath) {
        strcpy(buf, executablePath);
        if (verbose) {
            cout << "Using executable path: " << executablePath << endl;
        }
    } else {
        strcpy(buf, argv0);
        if (verbose) {
            cout << "Using [argv0] path: " << argv0 << endl;
        }
    }

    return buf;
}

bool changeWorkingDir(const dropt_char* directory) {
    return chdir(directory) == 0;
}

bool isZgcSupported() {
    return true;
}

#endif
