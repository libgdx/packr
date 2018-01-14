#!/bin/sh

if [ `getconf LONG_BIT` = "64" ]
then

    echo "Building 64 bit executable"
    ./fips set config linux-make-release
    ./fips clean && ./fips build

    echo "Copying 64 bit executable ..."
    cp ../fips-deploy/packr/linux-make-release/packr ../../resources/packr-linux-x64

    echo "Building 32 bit executable"
    ./fips set config packr-linux32-make-release
    ./fips clean && ./fips build

    echo "Copying 32 bit executable ..."
    cp ../fips-deploy/packr/packr-linux32-make-release/packr ../../resources/packr-linux

else

    echo "Building 32 bit executable"
    ./fips set config linux-make-release
    ./fips clean && ./fips build

    echo "Copying 32 bit executable ..."
    cp ../fips-deploy/packr/linux-make-release/packr ../../resources/packr-linux

fi
