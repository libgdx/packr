call fips set config win32-vs2013-release
call fips clean
call fips build
call copy ..\fips-deploy\packr\win32-vs2013-release\packr.exe ..\..\resources\packr-windows.exe

call fips set config win64-vs2013-release
call fips clean
call fips build
call copy ..\fips-deploy\packr\win64-vs2013-release\packr.exe ..\..\resources\packr-windows-x64.exe
