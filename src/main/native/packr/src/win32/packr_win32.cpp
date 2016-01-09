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
#include <windows.h>

#include <io.h>
#include <fcntl.h>
#include <iostream>
#include <direct.h>

#include "../packr.h"

using namespace std;

const char __CLASS_PATH_DELIM = ';';

static void waitAtExit(void) {
	cout << "Press ENTER key to exit.";
	cin.get();
}

static bool attachToConsole(int argc, char** argv) {

	bool attach = false;

	// pre-parse command line here to have a console in case of command line parse errors
	for (int arg = 0; arg < argc && !attach; arg++) {
		attach = (argv[arg] != nullptr && stricmp(argv[arg], "--console") == 0);
	}

	if (attach) {

		FreeConsole();
		AllocConsole();

		freopen("CONIN$", "r", stdin);
		freopen("CONOUT$", "w", stdout);
		freopen("CONOUT$", "w", stderr);

		atexit(waitAtExit);
	}

	return attach;
}

static void printLastError() {

	LPTSTR buffer;
	DWORD errorCode = GetLastError();

	FormatMessage(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
				  nullptr, errorCode, MAKELANGID(LANG_ENGLISH, SUBLANG_DEFAULT), (LPTSTR) &buffer, 0, nullptr);

	cerr << "Error code [" << errorCode << "]: " << buffer;

	LocalFree(buffer);
}

int CALLBACK WinMain(
	HINSTANCE hInstance,
	HINSTANCE hPrevInstance,
	LPSTR lpCmdLine,
	int nCmdShow) {

	int argc = __argc;
	char** argv = __argv;

	attachToConsole(argc, argv);

	if (!setCmdLineArguments(argc, argv)) {
		return EXIT_FAILURE;
	}

	launchJavaVM(defaultLaunchVMDelegate);

	return 0;
}

int main(int argc, char** argv) {

	if (!setCmdLineArguments(argc, argv)) {
		return EXIT_FAILURE;
	}

	launchJavaVM(defaultLaunchVMDelegate);

	return 0;
}

bool loadJNIFunctions(GetDefaultJavaVMInitArgs* getDefaultJavaVMInitArgs, CreateJavaVM* createJavaVM) {

	HINSTANCE hinstLib = LoadLibrary(TEXT("jre\\bin\\server\\jvm.dll"));
	if (hinstLib == nullptr) {
		printLastError();
		return false;
	}

	*getDefaultJavaVMInitArgs = (GetDefaultJavaVMInitArgs) GetProcAddress(hinstLib, "JNI_GetDefaultJavaVMInitArgs");
	if (*getDefaultJavaVMInitArgs == nullptr) {
		printLastError();
		return false;
	}

	*createJavaVM = (CreateJavaVM) GetProcAddress(hinstLib, "JNI_CreateJavaVM");
	if (*createJavaVM == nullptr) {
		printLastError();
		return false;
	}

	return true;
}

const char* getExecutablePath(const char* argv0) {
	return argv0;
}

bool changeWorkingDir(const char* directory) {
	return _chdir(directory) == 0;
}
