#include <launcher.h>
#include <string>
#include <windows.h>
#include <stdio.h>
#include <string.h>

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


int g_argc;
char** g_argv;

int WINAPI wWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, PWSTR pCmdLine, int nCmdShow) {
	g_argc = 0;
	g_argv = 0;

	launchVM(0);
	return 0;
}
