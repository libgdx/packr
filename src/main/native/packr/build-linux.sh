#!/bin/sh
./fips set config linux-make-release
./fips clean && ./fips build

if [ `getconf LONG_BIT` = "64" ]
then
    echo "Copying 64 bit executable ..."
    cp ../fips-deploy/packr-native/linux-make-release/packr ../../resources/packr-linux-x64
else
    echo "Copying 32 bit executable ..."
    cp ../fips-deploy/packr-native/linux-make-release/packr ../../resources/packr-linux
fi
