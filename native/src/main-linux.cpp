#include <launcher.h>
#include <string>
#include <sys/types.h>
#include <unistd.h>
#include <sys/stat.h>
#include <limits.h>
#include <stdio.h>
#include <string.h>

std::string getExecutableDir() {
	char dest[PATH_MAX];

	if (readlink("/proc/self/exe", dest, PATH_MAX) == -1)
		return std::string(".");
	else {
		strrchr(dest, '/')[0] = 0;
		return std::string(dest);
	}
}


int g_argc;
char** g_argv;


int main(int argc, char** argv) {
	g_argc = argc;
	g_argv = argv;

	launchVM(0);
	return 0;
}
