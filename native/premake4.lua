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
			includedirs { "include/jni-headers/linux" }
			links { "dl" }
			files { "src/main-linux.cpp" }

		--- mac os x ---
		configuration { "macosx" }
			defines { "MACOSX" }
			includedirs { "include/jni-headers/mac" }
			files { "src/main-mac.cpp" }
			links { "dl" }
			linkoptions { "-framework CoreFoundation -ldl" }
