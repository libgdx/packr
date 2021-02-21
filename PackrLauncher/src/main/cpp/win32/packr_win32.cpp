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
#include <codecvt>
#include <algorithm> // For string character replacement std::replace.

#include <packr.h>

#define RETURN_SUCCESS (0x00000000)

#define FULL_PATH_SIZE 32000

typedef LONG NT_STATUS;

typedef NT_STATUS (WINAPI *RtlGetVersionPtr)(PRTL_OSVERSIONINFOW);

using namespace std;

const char __CLASS_PATH_DELIM = ';';

extern "C"
{
__declspec(dllexport) DWORD NvOptimusEnablement = 1;
__declspec(dllexport) int AmdPowerXpressRequestHighPerformance = 1;
}

static void waitAtExit() {
    cout << "Press ENTER key to exit." << endl << flush;
    cin.get();
}

/**
 * If the '--console' argument is passed, then a new console is allocated, otherwise attaching to the parent console is attempted.
 *
 * @param argc the number of elements in {@code argv}
 * @param argv the list of arguments to parse for --console
 * @return true if the parent console was successfully attached to or a new console was allocated. false if no console could be acquired
 */
static bool attachToOrAllocateConsole(int argc, PTCHAR *argv) {
    bool allocConsole = false;

    // pre-parse command line here to have a console in case of command line parse errors
    for (int arg = 0; arg < argc && !allocConsole; arg++) {
        allocConsole = (argv[arg] != nullptr && wcsicmp(argv[arg], TEXT("--console")) == 0);
    }

    bool gotConsole = false;
    if (allocConsole) {
        FreeConsole();
        gotConsole = AllocConsole();
    } else {
        gotConsole = AttachConsole(ATTACH_PARENT_PROCESS);
    }

    if (gotConsole) {
        // Open C standard streams
        FILE *reusedThrowAwayHandle;
        freopen_s(&reusedThrowAwayHandle, "CONOUT$", "w", stdout);
        freopen_s(&reusedThrowAwayHandle, "CONOUT$", "w", stderr);
        freopen_s(&reusedThrowAwayHandle, "CONIN$", "r", stdin);
        cout.clear();
        clog.clear();
        cerr.clear();
        cin.clear();

        // Open the C++ wide streams
        HANDLE hConOut = CreateFile(TEXT("CONOUT$"),
                GENERIC_READ | GENERIC_WRITE,
                FILE_SHARE_READ | FILE_SHARE_WRITE,
                nullptr,
                OPEN_EXISTING,
                FILE_ATTRIBUTE_NORMAL,
                nullptr);
        HANDLE hConIn = CreateFile(TEXT("CONIN$"),
                GENERIC_READ | GENERIC_WRITE,
                FILE_SHARE_READ | FILE_SHARE_WRITE,
                nullptr,
                OPEN_EXISTING,
                FILE_ATTRIBUTE_NORMAL,
                nullptr);
        SetStdHandle(STD_OUTPUT_HANDLE, hConOut);
        SetStdHandle(STD_ERROR_HANDLE, hConOut);
        SetStdHandle(STD_INPUT_HANDLE, hConIn);
        wcout.clear();
        wclog.clear();
        wcerr.clear();
        wcin.clear();

        SetConsoleOutputCP(CP_UTF8);

        if (allocConsole) {
            atexit(waitAtExit);
        }
    }

    return gotConsole;
}

static void printLastError(const PTCHAR reason) {
    LPTSTR buffer;
    DWORD errorCode = GetLastError();

    FormatMessage(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
            nullptr,
            errorCode,
            MAKELANGID(LANG_ENGLISH, SUBLANG_DEFAULT),
            (LPTSTR) &buffer,
            0,
            nullptr);

    wstring_convert<codecvt_utf8_utf16<wchar_t>> converter;
    cerr << "Error code [" << errorCode << "] when trying to " << converter.to_bytes(reason) << ": " << converter.to_bytes(buffer) << endl;

    LocalFree(buffer);
}

static void catchFunction(int signo) {
    puts("Interactive attention signal caught.");
    cerr << "Caught signal " << signo << endl;
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
        int argc = 0;
        PTCHAR commandLine = GetCommandLine();
        PTCHAR *argv = CommandLineToArgvW(commandLine, &argc);
        attachToOrAllocateConsole(argc, argv);
        if (!setCmdLineArguments(argc, argv)) {
            cerr << "Failed to set the command line arguments" << endl;
            return EXIT_FAILURE;
        }

        launchJavaVM(defaultLaunchVMDelegate);
    } catch (exception &theException) {
        cerr << "Caught exception:" << endl;
        cerr << theException.what() << endl;
    } catch (...) {
        cerr << "Caught unknown exception:" << endl;
    }

    return 0;
}

int wmain(int argc, wchar_t **argv) {
    SetConsoleOutputCP(CP_UTF8);
   registerSignalHandlers();
   clearEnvironment();
   if (!setCmdLineArguments(argc, argv)) {
      return EXIT_FAILURE;
   }

   launchJavaVM(defaultLaunchVMDelegate);

   return 0;
}

void addDllDirectory(LPCWSTR directory) {
   wstring_convert<codecvt_utf8_utf16<wchar_t>> converter;
   TCHAR directoryFullPath[FULL_PATH_SIZE] = TEXT("");
   if (GetFullPathName(directory, FULL_PATH_SIZE, directoryFullPath, nullptr) == 0) {
      printLastError(TEXT("get the JRE bin absolute path"));
   } else {
      if (AddDllDirectory(directoryFullPath) == nullptr) {
         printLastError(TEXT("add DLL search directory"));
      } else if (verbose) {
         cout << "Added DLL search directory " << converter.to_bytes(directoryFullPath) << endl;
      }
   }
}

/**
 * Loads every library found using the pattern {@code libraryPattern}.
 * @param libraryPattern the search pattern to use to locate shared libaries to load
 */
void loadLibraries(const TCHAR* libraryPattern) {
   WIN32_FIND_DATA FindFileData;
   HANDLE hFind = nullptr;
   TCHAR libraryPath[MAX_PATH];

   wstring_convert<codecvt_utf8_utf16<wchar_t>> converter;

   hFind = FindFirstFile(libraryPattern, &FindFileData);
   if (hFind == INVALID_HANDLE_VALUE) {
      if (verbose) {
         cout << "Couldn't find " << converter.to_bytes(libraryPattern) << " file." << "FindFirstFile failed " << GetLastError() << "." << endl;
      }
      return;
   }
   do {
      if (LoadLibraryEx(FindFileData.cFileName, nullptr, LOAD_LIBRARY_SEARCH_DEFAULT_DIRS) == nullptr) {
         if (verbose) {
            cout << "Failed to load DLL " << converter.to_bytes(FindFileData.cFileName) << "." << endl;
         }
      } else if (verbose) {
         cout << "Loaded DLL " << converter.to_bytes(FindFileData.cFileName) << "." << endl;
      }
   } while (FindNextFile(hFind, &FindFileData));

   FindClose(hFind);
}

bool loadJNIFunctions(const dropt_char* jrePath, GetDefaultJavaVMInitArgs *getDefaultJavaVMInitArgs, CreateJavaVM *createJavaVM) {
   wstring backslashedJrePath = wstring(jrePath);
   std::replace(backslashedJrePath.begin(), backslashedJrePath.end(), L'/', L'\\');
   addDllDirectory((backslashedJrePath + L"\\bin").c_str());
   addDllDirectory((backslashedJrePath + L"\\bin\\server").c_str());

   // Load every shared library in jre/bin because awt.dll doesn't load its dependent libraries using the correct search paths
   wstring allDllsWstring = backslashedJrePath + L"\\bin\\*.dll";
   const dropt_char* allDlls = allDllsWstring.c_str();
   loadLibraries(allDlls);

   TCHAR jvmDllFullPath[FULL_PATH_SIZE] = TEXT("");
   if (GetFullPathName((backslashedJrePath + L"\\bin\\server\\jvm.dll").c_str(), FULL_PATH_SIZE, jvmDllFullPath, nullptr) == 0) {
      printLastError(TEXT("get the jvm.dll absolute path"));
      return false;
   }
   HINSTANCE hinstLib = LoadLibraryEx(jvmDllFullPath, nullptr, LOAD_LIBRARY_SEARCH_DEFAULT_DIRS);

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
    BOOL currentDirectory = SetCurrentDirectory(directory);
    if(currentDirectory == 0){
        printLastError(TEXT("Failed to change the working directory"));
    }
    return currentDirectory != 0;
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
                    cout
                            << "versionInformation.dwMajorVersion="
                            << versionInformation.dwMajorVersion
                            << ", versionInformation.dwMinorVersion="
                            << versionInformation.dwMinorVersion
                            << ", versionInformation.dwBuildNumber="
                            << versionInformation.dwBuildNumber
                            << endl;
                }
                return (versionInformation.dwMajorVersion >= 10 && versionInformation.dwBuildNumber >= 17134)
                       || (versionInformation.dwMajorVersion >= 10 && versionInformation.dwMinorVersion >= 1);
            } else {
                if (verbose) {
                    cout << "RtlGetVersion didn't work" << endl;
                }
            }
        }
    }
    return false;
}

#endif
