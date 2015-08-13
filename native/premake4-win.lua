solution "packr"
	configurations { "debug", "release" }

	project "packr"
		kind "WindowedApp"
		language "C++"
		-- buildoptions { "-Wall" }
		files { "**.h", "src/launcher.cpp", "src/main-windows.cpp" }
		includedirs { "include", "include/jni-headers", "include/jni-headers/win32" }
		defines { "WINDOWS" }
		flags { "StaticRuntime", "WinMain" }

		configuration "debug"
		         defines { "DEBUG" }
		         flags { "Symbols" }
 
		configuration "release"
		         defines { "NDEBUG" }
		         flags { "Optimize" }    
