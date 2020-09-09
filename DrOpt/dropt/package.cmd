@echo off

setlocal

if not defined DROPT_VERSION set DROPT_VERSION=%~1
if "%DROPT_VERSION%" == "" (
    (echo.No version number specified.) 1>&2
    (echo.Usage: %~n0 VERSION) 1>&2
    exit /b 1
)

:: Verify that everything builds.  This doesn't provide complete coverage,
:: but it should be good enough.
::

set MAKE=nmake /NOLOGO /f Makefile.vcwin32

echo.*** Building dropt ***
%MAKE% all
if ERRORLEVEL 1 exit /b 1

echo.*** Building dropt (DEBUG=1) ***
%MAKE% DEBUG=1 all
if ERRORLEVEL 1 exit /b 1

echo.*** Building dropt (_UNICODE=1) ***
%MAKE% _UNICODE=1 all
if ERRORLEVEL 1 exit /b 1

echo.*** Building dropt (DROPT_NO_STRING_BUFFERS=1) ***
%MAKE% DROPT_NO_STRING_BUFFERS=1 all
if ERRORLEVEL 1 exit /b 1

echo.*** Building dropt (DEBUG=1 _UNICODE=1 DROPT_NO_STRING_BUFFERS=1) ***
%MAKE% DEBUG=1 _UNICODE=1 DROPT_NO_STRING_BUFFERS=1 all
if ERRORLEVEL 1 exit /b 1

set FILES=include\dropt.h ^
          include\dropt_string.h ^
          include\droptxx.hpp ^
          src\dropt.c ^
          src\dropt_handlers.c ^
          src\dropt_string.c ^
          src\droptxx.cpp ^
          src\test_dropt.c ^
          INSTALL ^
          LICENSE ^
          Makefile.clang ^
          Makefile.gcc ^
          Makefile.vcwin32 ^
          README.html ^
          dropt_example.c ^
          droptxx_example.cpp ^
          gmake.mk

set PUBLISH_DIR_BASENAME=dropt-%DROPT_VERSION%
if exist "build\%PUBLISH_DIR_BASENAME%" rd /s /q "build\%PUBLISH_DIR_BASENAME%"
md "build\%PUBLISH_DIR_BASENAME%"
md "build\%PUBLISH_DIR_BASENAME%\include"
md "build\%PUBLISH_DIR_BASENAME%\src"
for %%x in (%FILES%) do (copy /y %%x "build\%PUBLISH_DIR_BASENAME%\%%x" > NUL)

pushd build

set ZIP_FILE=dropt-%DROPT_VERSION%.zip
set TAR_FILE=dropt-%DROPT_VERSION%.tar

echo.*** Packaging %ZIP_FILE% ***
7z a -tzip -mx=9 -mcu=on -r -- "%ZIP_FILE%" "%PUBLISH_DIR_BASENAME%"
if ERRORLEVEL 1 exit /b 1

echo.*** Packaging %TAR_FILE%.gz ***
tar -cv -f "%TAR_FILE%" "%PUBLISH_DIR_BASENAME%"
if ERRORLEVEL 1 exit /b 1

gzip -f -9 "%TAR_FILE%"
if ERRORLEVEL 1 exit /b 1
