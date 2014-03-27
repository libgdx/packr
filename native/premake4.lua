solution "packr"
	configurations { "debug", "release" }

	project "packr"
		kind "ConsoleApp"
		language "C++"
		files { "**.h", "**.cpp" }

		configuration "debug"
			defines { "DEBUG" }
			flags { "Symbols" }

		configuration "release"
			defines { "NDEBUG" }
			flags { "Optimize" }