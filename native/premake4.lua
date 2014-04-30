JAVA_HOME = os.getenv("JAVA_HOME")
if not JAVA_HOME then
	printf("ERROR: set JAVA_HOME to your JDK directory, e.g. /Library/Java/JavaVirtualMachines/jdk1.7.0_51.jdk/Contents/Home");
	printf("No trailing slash in the path!");
	os.exit()
end

solution "packr"
	configurations { "debug", "release" }

	project "packr"
		kind "ConsoleApp"
		language "C++"		
		buildoptions { "-Wall" }
		files { "**.h", "src/launcher.cpp" }
		includedirs { "include", "include/jni-headers" }

		configuration "debug"
			defines { "DEBUG" }
			flags { "Symbols" }

		configuration "release"
			defines { "NDEBUG" }
			flags { "Optimize" }

		--- windows ---
		configuration { "windows" }
			kind "WindowedApp"
			defines { "WINDOWS" }		
			includedirs { "include/jni-headers/win32" }
			files { "src/main-windows.cpp" }
            flags { "WinMain" }
			
		--- linux ---
		configuration { "linux" }
			defines { "LINUX" }
			LIBJVM_DIR = JAVA_HOME .. "/jre/lib/amd64/server/"
			printf(LIBJVM_DIR);
			includedirs { "include/jni-headers/linux" }
			files { "src/main-linux.cpp" }
			libdirs { LIBJVM_DIR }
			links { "jvm" }
			linkoptions { "-Wl,-rpath,'$$ORIGIN/jre/lib/amd64/server'" }

		--- mac os x ---
		configuration { "macosx" }			
			defines { "MACOSX" }
			LIBJVM_DIR = JAVA_HOME .. "/jre/lib/server/"
			printf(LIBJVM_DIR);
			includedirs { "include/jni-headers/mac" }
			files { "src/main-mac.cpp" }
			libdirs { LIBJVM_DIR }
			links { "jvm" }
			linkoptions { "-framework CoreFoundation", "-rpath @executable_path/jre/lib/server" }
