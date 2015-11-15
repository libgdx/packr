#include "../packr.h"

#include <dlfcn.h>
#include <iostream>
#include <limits.h>
#include <stdio.h>
#include <string.h>
#include <sys/types.h>
#include <unistd.h>

using namespace std;

const char __CLASS_PATH_DELIM = ':';

int main(int argc, char** argv) {

    if (!setCmdLineArguments(argc, argv)) {
        return EXIT_FAILURE;
    }

    launchJavaVM(nullptr);

    return 0;
}

bool loadJNIFunctions(GetDefaultJavaVMInitArgs* getDefaultJavaVMInitArgs, CreateJavaVM* createJavaVM) {

#if defined(__LP64__)
    void* handle = dlopen("jre/lib/amd64/server/libjvm.so", RTLD_LAZY);
#else
    void* handle = dlopen("jre/lib/i386/server/libjvm.so", RTLD_LAZY);
#endif
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

const char* getExecutableName(const char* argv0) {

    static char buf[PATH_MAX];
    uint32_t size = sizeof(buf);

	if (readlink("/proc/self/exe", buf, size) == -1) {
        return argv0;
	}

    const char* delim = strrchr(buf, '/');
    if (delim != nullptr) {
        return ++delim;
    }

    return argv0;
}

bool changeWorkingDir(const char* directory) {
	return chdir(directory) == 0;
}
