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
	const char* getExecutablePath(const char* argv0);
	bool changeWorkingDir(const char* directory);

	/* entry point for all platforms - called from main()/WinMain() */
	bool setCmdLineArguments(int argc, char** argv);
	void* launchJavaVM(void*);

}
