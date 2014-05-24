solution "packr"
	configurations { "release" }

	project "packr"
		kind "ConsoleApp"
		language "C++"
		buildoptions { "-Wall" }
		files { "**.h", "src/launcher.cpp" }
		includedirs { "include", "include/jni-headers" }

		defines { "NDEBUG" }
		flags { "Optimize" }

		defines { "LINUX" }
		includedirs { "include/jni-headers/linux" }
		links { "dl" }
		files { "src/main-linux.cpp" }

		platforms { "x32", "x64" }
