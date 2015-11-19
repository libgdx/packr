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
#include "../packr.h"

#include <dlfcn.h>
#include <iostream>
#include <pthread.h>
#include <CoreFoundation/CoreFoundation.h>
#include <sys/param.h>
#include <unistd.h>

using namespace std;

const char __CLASS_PATH_DELIM = ':';

void sourceCallBack(void* info) {

}

int main(int argc, char** argv) {

    if (!setCmdLineArguments(argc, argv)) {
        return EXIT_FAILURE;
    }

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
    pthread_create(&vmthread, &thread_attr, launchJavaVM, 0);
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

    CFRunLoopSourceRef sourceRef = CFRunLoopSourceCreate (NULL, 0, &sourceContext);
    CFRunLoopAddSource (CFRunLoopGetCurrent(),sourceRef,kCFRunLoopCommonModes);
    CFRunLoopRun();

    return 0;
}

bool loadJNIFunctions(GetDefaultJavaVMInitArgs* getDefaultJavaVMInitArgs, CreateJavaVM* createJavaVM) {

    void* handle = dlopen("jre/lib/jli/libjli.dylib", RTLD_LAZY);
    if (handle == NULL) {
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

const char* getExecutablePath(const char* argv0) {

    static char buf[MAXPATHLEN];
    uint32_t size = sizeof(buf);

    if (_NSGetExecutablePath(buf, &size) == -1) {
        return argv0;
    }

    return buf;
}

bool changeWorkingDir(const char* directory) {
	return chdir(directory) == 0;
}
