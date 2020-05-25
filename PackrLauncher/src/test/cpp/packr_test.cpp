#include "gtest/gtest.h"
#include "packr.h"

#ifdef _WIN32

#include <Windows.h>
#include <regex>

#endif

#define BUFFER_SIZE 4096

using namespace std;

TEST(PackrLauncherTests, test_nothing) {
    verbose = true;
    std::cout << "Hello world from a unit test in C++" << std::endl;
    ASSERT_EQ(0, 0);
}

#ifdef _WIN32

/**
 * Executes the program `systeminfo`, captures it's standard output and returns it as a string.
 *
 * @return the output from the `systeminfo` process
 */
string getSystemInfoOutput() {
    // read from child standard output
    HANDLE hChildStandardOutputAndErrorHandle = nullptr;
    HANDLE hReadPipeFromChildStandardOutputAndError = nullptr;

    // write to child standard input
    HANDLE hChildStandardInputHandle = nullptr;
    HANDLE hWritePipeToChildStandardInput = nullptr;

    SECURITY_ATTRIBUTES securityAttributes;
    securityAttributes.nLength = sizeof(SECURITY_ATTRIBUTES);
    securityAttributes.bInheritHandle = TRUE;
    securityAttributes.lpSecurityDescriptor = nullptr;

    PROCESS_INFORMATION processInformation;
    ZeroMemory(&processInformation, sizeof(PROCESS_INFORMATION));

    // create a pipe to write to the standard input of the child process
    if (!CreatePipe(&hChildStandardInputHandle, &hWritePipeToChildStandardInput, &securityAttributes, 0)) {
        std::cerr << "Failed to create pipe to write to the child standard input" << std::endl;
        throw exception("Failed to create pipe to write to the child standard input");
    }
    if (!SetHandleInformation(hWritePipeToChildStandardInput, HANDLE_FLAG_INHERIT, 0)) {
        std::cerr << "Failed to set handle information for write pipe to child standard input" << std::endl;
        throw exception("Failed to set handle information for write pipe to child standard input");
    }

    if (!CreatePipe(&hReadPipeFromChildStandardOutputAndError, &hChildStandardOutputAndErrorHandle, &securityAttributes, 0)) {
        std::cout << "Failed to create pipe for reading from child standard in" << std::endl;
        throw exception("Failed to create pipe for reading from child standard in");
    }

    if (!SetHandleInformation(hReadPipeFromChildStandardOutputAndError, HANDLE_FLAG_INHERIT, 0)) {
        std::cout << "Failed to set handle information " << GetLastError() << std::endl;
        throw exception("Failed to set handle information");
    }

    STARTUPINFO startupInfo;
    ZeroMemory(&startupInfo, sizeof(STARTUPINFO));
    startupInfo.cb = sizeof(STARTUPINFO);
    startupInfo.hStdError = hChildStandardOutputAndErrorHandle;
    startupInfo.hStdOutput = hChildStandardOutputAndErrorHandle;
    startupInfo.hStdInput = hChildStandardInputHandle;
    startupInfo.dwFlags |= static_cast<DWORD>(STARTF_USESTDHANDLES);

    if (!CreateProcess(nullptr,
                       (LPSTR) "systeminfo",     // command line
                       nullptr,          // process security attributes
                       nullptr,          // primary thread security attributes
                       TRUE,          // handles are inherited
                       static_cast<DWORD>(CREATE_NO_WINDOW) | static_cast<DWORD>(CREATE_NEW_PROCESS_GROUP),   // creation flags
                       nullptr,          // use parent's environment
                       nullptr,          // use parent's current directory
                       &startupInfo,  // STARTUPINFO pointer
                       &processInformation)) {
        std::cerr << "Failed to create process. " << GetLastError() << std::endl;
        throw exception("Failed to create process.");
    }

    CloseHandle(processInformation.hProcess);
    CloseHandle(processInformation.hThread);
    CloseHandle(hChildStandardInputHandle);
    CloseHandle(hChildStandardOutputAndErrorHandle);

    std::cout << "systeminfo processId=" << processInformation.dwProcessId << std::endl;

    std::string standardOutCapture;

    DWORD numberOfBytesRead;
    CHAR characterBuffer[BUFFER_SIZE];
    bool readSucceeded = FALSE;

    for (;;) {
        readSucceeded = ReadFile(hReadPipeFromChildStandardOutputAndError, characterBuffer, BUFFER_SIZE, &numberOfBytesRead, nullptr);
        if (!readSucceeded || numberOfBytesRead == 0) break;

        standardOutCapture.append(characterBuffer, numberOfBytesRead);
    }
    CloseHandle(hReadPipeFromChildStandardOutputAndError);
    CloseHandle(hWritePipeToChildStandardInput);

    return standardOutCapture;
}

/**
 * Parses output from `systeminfo` looking for the 'OS Version:' line.
 * @param systemInfoOutput the output from `systeminfo`
 * @return true if the OS version was found and the major version is >= 10 and the build number is >= 17134
 */
bool isSystemInfoParsedVersionWindows10Build17134OrLater(const string &systemInfoOutput) {
    // OS Version:                10.0.18363 N/A Build 18363
    regex versionRegularExpression(R"(OS\s+Version:\s+(\d+)\.(\d+)\.(\d+))");
    smatch versionMatches;
    bool version10Build17134OrLater = false;
    if (regex_search(systemInfoOutput, versionMatches, versionRegularExpression)) {
        std::cout << "Version regex match found" << endl;
        for (size_t versionMatchIndex = 0; versionMatchIndex < versionMatches.size(); ++versionMatchIndex) {
            std::cout << versionMatchIndex << ": '" << versionMatches[versionMatchIndex].str() << "'" << endl;
        }

        int majorVersion = strtol(versionMatches[1].str().c_str(), nullptr, 10);
        int minorVersion = strtol(versionMatches[2].str().c_str(), nullptr, 10);
        int buildNumber = strtol(versionMatches[3].str().c_str(), nullptr, 10);
        version10Build17134OrLater = (majorVersion >= 10 && buildNumber >= 17134) || (majorVersion >= 10 && minorVersion >= 1);
    } else {
        std::cout << "Match not found\n";
    }
    return version10Build17134OrLater;
}

#endif

TEST(PackrLauncherTests, test_zgc_supported) {
    verbose = true;
    bool zgcSupported = isZgcSupported();
    std::cout << "zgcSupported = " << zgcSupported << std::endl;
#ifdef __linux__
    ASSERT_EQ(true, zgcSupported);
#elif __APPLE__
    ASSERT_EQ(true, zgcSupported);
#elif _WIN32
    string systemInfoOutput = getSystemInfoOutput();
    std::cout << "Got output from systeminfo=" << systemInfoOutput << std::endl;
    bool windows10Build17134OrLater = isSystemInfoParsedVersionWindows10Build17134OrLater(systemInfoOutput);
    ASSERT_EQ(windows10Build17134OrLater, zgcSupported);
#endif
}
