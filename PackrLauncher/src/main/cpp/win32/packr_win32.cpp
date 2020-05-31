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
#ifdef _WIN32

#include <Windows.h>
#include <processenv.h>

#include <io.h>
#include <fcntl.h>
#include <iostream>
#include <direct.h>

#include <csignal>
#include <cstdio>
#include <cstdlib>

#include <packr.h>

#define RETURN_SUCCESS (0x00000000)
#ifdef UNICODE
#define stringCompareIgnoreCase wcsicmp
#define stringCopy wcscpy
#define stringConcatenate wcscat
#else
#define stringCompareIgnoreCase stricmp
#define stringCopy strcpy
#define stringConcatenate strcat
#endif

typedef LONG NT_STATUS, *P_NT_STATUS;

typedef NT_STATUS (WINAPI *RtlGetVersionPtr)(PRTL_OSVERSIONINFOW);

using namespace std;

const char __CLASS_PATH_DELIM = ';';

extern "C"
{
__declspec(dllexport) DWORD NvOptimusEnablement = 1;
__declspec(dllexport) int AmdPowerXpressRequestHighPerformance = 1;
}

static void waitAtExit() {
    cout << "Press ENTER key to exit." << endl;
    cin.get();
}

static bool attachToConsole(int argc, PTCHAR *argv) {
    bool attach = false;

    // pre-parse command line here to have a console in case of command line parse errors
    for (int arg = 0; arg < argc && !attach; arg++) {
        attach = (argv[arg] != nullptr && stringCompareIgnoreCase(argv[arg], TEXT("--console")) == 0);
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

static void printLastError(const PTCHAR reason) {
    LPTSTR buffer;
    DWORD errorCode = GetLastError();

    FormatMessage(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                  nullptr, errorCode, MAKELANGID(LANG_ENGLISH, SUBLANG_DEFAULT), (LPTSTR) &buffer, 0, nullptr);

    cerr << "Error code [" << errorCode << "] when trying to " << reason << ": " << buffer << endl;

    LocalFree(buffer);
}

static void catchFunction(int signo) {
    puts("Interactive attention signal caught.");
    cerr << "Caught signal " << signo << endl;
}

static void atExitListener() {
	cout << "Exiting" << endl;
}

bool g_showCrashDialog = false;

LONG WINAPI crashHandler(EXCEPTION_POINTERS * /*ExceptionInfo*/) {
    cerr << "Unhandled windows exception occurred" << endl;

    return g_showCrashDialog ? EXCEPTION_CONTINUE_SEARCH : EXCEPTION_EXECUTE_HANDLER;
}

static void clearEnvironment() {
	cout << "clearEnvironment" << endl;
    _putenv_s("_JAVA_OPTIONS", "");
    _putenv_s("JAVA_TOOL_OPTIONS", "");
    _putenv_s("CLASSPATH", "");
}

static void registerSignalHandlers() {
    void (*code)(int);
    code = signal(SIGINT, catchFunction);
    if (code == SIG_ERR) {
        cerr << "Failed to listen to SIGINT" << endl;
    }
    code = signal(SIGILL, catchFunction);
    if (code == SIG_ERR) {
        cerr << "Failed to listen to SIGILL" << endl;
    }
    code = signal(SIGFPE, catchFunction);
    if (code == SIG_ERR) {
        cerr << "Failed to listen to SIGFPE" << endl;
    }
    code = signal(SIGSEGV, catchFunction);
    if (code == SIG_ERR) {
        cerr << "Failed to listen to SIGSEGV" << endl;
    }
    code = signal(SIGTERM, catchFunction);
    if (code == SIG_ERR) {
        cerr << "Failed to listen to SIGTERM" << endl;
    }
    code = signal(SIGBREAK, catchFunction);
    if (code == SIG_ERR) {
        cerr << "Failed to listen to SIGBREAK" << endl;
    }
    code = signal(SIGABRT, catchFunction);
    if (code == SIG_ERR) {
        cerr << "Failed to listen to SIGABRT" << endl;
    }
    code = signal(SIGABRT_COMPAT, catchFunction);
    if (code == SIG_ERR) {
        cerr << "Failed to listen to SIGABRT_COMPAT" << endl;
    }

    atexit(atExitListener);

    SetUnhandledExceptionFilter(crashHandler);
}

int CALLBACK WinMain(
        HINSTANCE hInstance,
        HINSTANCE hPrevInstance,
        LPSTR lpCmdLine,
        int nCmdShow) {
    registerSignalHandlers();
    clearEnvironment();
    try {
#ifdef UNICODE
        int argc = 0;
        PTCHAR commandLine = GetCommandLine();
        PTCHAR* argv = CommandLineToArgvW(commandLine, &argc);
#else
        int argc = __argc;
        PTCHAR* argv = __argv;
#endif
        attachToConsole(argc, argv);
        if (!setCmdLineArguments(argc, argv)) {
            cerr << "Failed to set the command line arguments" << endl;
            return EXIT_FAILURE;
        }

        cout << "launchJavaVM" << endl;
        launchJavaVM(defaultLaunchVMDelegate);
    } catch (std::exception &theException) {
        cerr << "Caught exception:" << endl;
	    cerr << theException.what() << endl;
    } catch (...) {
        cerr << "Caught unknown exception:" << endl;
    }

    return 0;
}

#ifdef UNICODE
int wmain(int argc, wchar_t **argv) {
#else
int main(int argc, char **argv) {
#endif
    registerSignalHandlers();
    clearEnvironment();
    if (!setCmdLineArguments(argc, argv)) {
        return EXIT_FAILURE;
    }

    launchJavaVM(defaultLaunchVMDelegate);

    return 0;
}

bool loadJNIFunctions(GetDefaultJavaVMInitArgs *getDefaultJavaVMInitArgs, CreateJavaVM *createJavaVM) {
    LPCTSTR jvmDLLPath = TEXT("jre\\bin\\server\\jvm.dll");
    HINSTANCE hinstLib = LoadLibrary(jvmDLLPath);
    if (hinstLib == nullptr) {
        DWORD errorCode = GetLastError();
        if (verbose) {
            cout << "Last error code " << errorCode << endl;
        }
        if (errorCode == 126) {
            // "The specified module could not be found."
            // load msvcr*.dll from the bundled JRE, then try again
            if (verbose) {
                cout << "Failed to load jvm.dll. Trying to load msvcr*.dll first ..." << endl;
            }

            WIN32_FIND_DATA FindFileData;
            HANDLE hFind = nullptr;
            TCHAR msvcrPath[MAX_PATH];

            hFind = FindFirstFile(TEXT("jre\\bin\\msvcr*.dll"), &FindFileData);
            if (hFind == INVALID_HANDLE_VALUE) {
                if (verbose) {
                    cout << "Couldn't find msvcr*.dll file." << "FindFirstFile failed " << GetLastError() << "."
                         << endl;
                }
            } else {
                FindClose(hFind);
                if (verbose) {
                    cout << "Found msvcr*.dll file " << FindFileData.cFileName << endl;
                }
                stringCopy(msvcrPath, TEXT("jre\\bin\\"));
                stringConcatenate(msvcrPath, FindFileData.cFileName);
                HINSTANCE hinstVCR = LoadLibrary(msvcrPath);
                if (hinstVCR != nullptr) {
                    hinstLib = LoadLibrary(jvmDLLPath);
                    if (verbose) {
                        cout << "Loaded library " << msvcrPath << endl;
                    }
                } else {
                    if (verbose) {
                        cout << "Failed to load library " << FindFileData.cFileName << endl;
                    }
                }
            }
        }
    }

    if (hinstLib == nullptr) {
        printLastError(TEXT("load jvm.dll"));
        return false;
    }

    *getDefaultJavaVMInitArgs = (GetDefaultJavaVMInitArgs) GetProcAddress(hinstLib, "JNI_GetDefaultJavaVMInitArgs");
    if (*getDefaultJavaVMInitArgs == nullptr) {
        printLastError(TEXT("obtain JNI_GetDefaultJavaVMInitArgs address"));
        return false;
    }

    *createJavaVM = (CreateJavaVM) GetProcAddress(hinstLib, "JNI_CreateJavaVM");
    if (*createJavaVM == nullptr) {
        printLastError(TEXT("obtain JNI_CreateJavaVM address"));
        return false;
    }

    return true;
}

const dropt_char *getExecutablePath(const dropt_char *argv0) {
    return argv0;
}

bool changeWorkingDir(const dropt_char *directory) {
    return SetCurrentDirectory(directory) == 0;
}

/**
 * In Java 14, Windows 10 1803 is required for ZGC, see https://wiki.openjdk.java.net/display/zgc/Main#Main-SupportedPlatforms
 * for more information. Windows 10 1803 is build 17134.
 * @return true if the Windows version is 10 build 17134 or higher
 */
bool isZgcSupported() {
    // Try to get the Windows version from RtlGetVersion
    HMODULE ntDllHandle = ::GetModuleHandleW(L"ntdll.dll");
    if (ntDllHandle) {
        auto rtlGetVersionFunction = (RtlGetVersionPtr) ::GetProcAddress(ntDllHandle, "RtlGetVersion");
        if (rtlGetVersionFunction != nullptr) {
            RTL_OSVERSIONINFOW versionInformation = {0};
            versionInformation.dwOSVersionInfoSize = sizeof(versionInformation);
            if (RETURN_SUCCESS == rtlGetVersionFunction(&versionInformation)) {
                if (verbose) {
                    std::cout << "versionInformation.dwMajorVersion=" << versionInformation.dwMajorVersion
                              << ", versionInformation.dwMinorVersion=" << versionInformation.dwMinorVersion
                              << ", versionInformation.dwBuildNumber=" << versionInformation.dwBuildNumber
                              << std::endl;
                }
                return (versionInformation.dwMajorVersion >= 10 && versionInformation.dwBuildNumber >= 17134)
                       || (versionInformation.dwMajorVersion >= 10 && versionInformation.dwMinorVersion >= 1);
            } else {
                if (verbose) {
                    std::cout << "RtlGetVersion didn't work" << std::endl;
                }
            }
        }
    }
    return false;
}

#endif