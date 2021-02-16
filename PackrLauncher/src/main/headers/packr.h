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

#include <functional>
#include <jni.h>
#include <dropt.h>
#include <string>

#if !defined(JNI_VERSION_1_8)
# define JNI_VERSION_1_8 0x00010008
#endif

typedef jint(JNICALL *GetDefaultJavaVMInitArgs)(void*);
typedef jint(JNICALL *CreateJavaVM)(JavaVM**, void**, void*);

typedef std::function<void* (void*)> LaunchJavaVMDelegate;
typedef std::function<void (LaunchJavaVMDelegate delegate, const JavaVMInitArgs& args)> LaunchJavaVMCallback;

#define defaultLaunchVMDelegate \
	[](LaunchJavaVMDelegate delegate, const JavaVMInitArgs&) { delegate(nullptr); }

extern "C" {
	/* configuration */
	extern bool verbose;

	/* platform-dependent constants */
	extern const char __CLASS_PATH_DELIM;

	/* platform-dependent functions */
	bool loadJNIFunctions(const dropt_char* jrePath, GetDefaultJavaVMInitArgs* getDefaultJavaVMInitArgs, CreateJavaVM* createJavaVM);
	const dropt_char* getExecutablePath(const dropt_char* argv0);

	bool changeWorkingDir(const dropt_char* directory);

	/* entry point for all platforms - called from main()/WinMain() */
	bool setCmdLineArguments(int argc, dropt_char** argv);
	void launchJavaVM(const LaunchJavaVMCallback& callback);

	bool isZgcSupported();
}
