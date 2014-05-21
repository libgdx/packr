premake4 --file=premake4-win.lua vs2010
msbuild packr.sln /p:Configuration=release /p:Platform=Win32 /t:Clean,Build
move packr.exe ..\src\main\resources\packr-windows.exe
REM msbuild packr.sln /p:Configuration=release /p:Platform=x64 /t:Clean,Build
REM move packr.exe ..\src\main\resources\packr-windows-x64.exe