#pragma once

#include <jni.h>

#define PACKR_VERSION_STRING "2.0beta1"

#if !defined(JNI_VERSION_1_8)
# define JNI_VERSION_1_8 0x00010008
#endif

typedef jint(JNICALL *GetDefaultJavaVMInitArgs)(void*);
typedef jint(JNICALL *CreateJavaVM)(JavaVM**, void**, void*);

extern "C" {

	/* platform-dependent constants */
	extern const char __CLASS_PATH_DELIM;

	/* platform-dependent functions */
	bool loadJNIFunctions(GetDefaultJavaVMInitArgs* getDefaultJavaVMInitArgs, CreateJavaVM* createJavaVM);
	const char* getExecutableName(const char* argv0);
	bool changeWorkingDir(const char* directory);

	/* entry point for all platforms - called from main()/WinMain() */
	bool setCmdLineArguments(int argc, char** argv);
	void* launchJavaVM(void*);

}
