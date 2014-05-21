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

		defines { "MACOSX" }
		includedirs { "include/jni-headers/mac" }
		files { "src/main-mac.cpp" }
		linkoptions { "-framework CoreFoundation" }

		platforms { "native" }
