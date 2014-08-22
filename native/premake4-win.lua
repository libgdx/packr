solution "packr"
	configurations { "release" }

	project "packr"
		kind "ConsoleApp"
		language "C++"
		-- buildoptions { "-Wall" }
		files { "**.h", "src/launcher.cpp" }
		includedirs { "include", "include/jni-headers" }

		defines { "NDEBUG" }
		flags { "Optimize" }

		kind "WindowedApp"
		defines { "WINDOWS" }
		includedirs { "include/jni-headers/win32" }
		files { "src/main-windows.cpp" }
		linkoptions { "/LARGEADDRESSAWARE" }
		flags { "WinMain", "StaticRuntime", "NoManifest" }

		platforms { "x32", "x64" }
