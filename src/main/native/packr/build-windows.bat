rem call fips set config win32-vs2013-release
rem call fips clean
rem call fips build
call copy ..\fips-deploy\packr-native\win32-vs2013-release\packr.exe ..\..\resources\packr-windows.exe

rem call fips set config win64-vs2013-release
rem call fips clean
rem call fips build
call copy ..\fips-deploy\packr-native\win64-vs2013-release\packr.exe ..\..\resources\packr-windows-x64.exe
