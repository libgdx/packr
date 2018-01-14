#!/bin/sh
./fips set config packr-osx-xcode-release
./fips clean && ./fips build
cp ../fips-deploy/packr/packr-osx-xcode-release/packr ../../resources/packr-mac
