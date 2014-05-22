/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
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

#include <launcher.h>
#include <windows.h>
#include <string>
#include <stdio.h>
#include <string.h>
#include <direct.h>

std::string getExecutableDir() {
	HMODULE hModule = GetModuleHandleW(NULL);
	WCHAR path[1024];
	GetModuleFileNameW(hModule, path, MAX_PATH);
	char dest[2048];
	char defChar = ' ';
	WideCharToMultiByte(CP_ACP,0,path,-1, dest,260,&defChar, NULL);
	strrchr(dest, '\\')[0] = 0;
	return std::string(dest);
}

bool changeWorkingDir(std::string dir) {
    return _chdir(dir.c_str()) == 0;
}

int g_argc;
char** g_argv;

void parseCommandLine() {
	LPWSTR* argv = CommandLineToArgvW(GetCommandLineW(), &g_argc);
	if(argv != NULL) {
		g_argv = new char*[g_argc];
		char defChar = ' ';
		for(int i = 0; i < g_argc; i++) {
			int len = wcslen(argv[i]) * 2;
			g_argv[i] = new char[len + 1];
			WideCharToMultiByte(CP_ACP, 0, argv[i], -1, g_argv[i], len, &defChar, NULL);
			g_argv[i][len - 1] = '\0';
		}
	} else {
		g_argc = 1;
		g_argv = NULL;
	}
	LocalFree(argv);
}

void cleanupCommandLine() {
	if(g_argv != NULL) {
		for(int i = 0; i < g_argc; i++) {
			delete[] g_argv[i];
		}
		delete[] g_argv;
	}
}

int WINAPI wWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, PWSTR pCmdLine, int nCmdShow) {
	parseCommandLine();
	launchVM(0);
	cleanupCommandLine();
	return 0;
}
