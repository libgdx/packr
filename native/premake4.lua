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
		files { "**.h", "**.cpp" }
		includedirs { "include", "include/jni-headers" }

		configuration "debug"
			defines { "DEBUG" }
			flags { "Symbols" }

		configuration "release"
			defines { "NDEBUG" }
			flags { "Optimize" }

		--- windows ---
		configuration { "windows" }
			defines { "WINDOWS" }		
			includedirs { "include/jni-headers/win32" }

		--- linux ---
		configuration { "linux" }
			defines { "LINUX" }		
			includedirs { "include/jni-headers/linux" }

		--- mac os x ---
		configuration { "macosx" }			
			defines { "MACOSX" }
			LIBJVM_DIR = JAVA_HOME .. "/jre/lib/server/"
			printf(LIBJVM_DIR);
			includedirs { "include/jni-headers/mac" }
			libdirs { LIBJVM_DIR }
			links { "jvm" }
			linkoptions { "-framework CoreFoundation", "-rpath @executable_path/jre/lib/server" }
