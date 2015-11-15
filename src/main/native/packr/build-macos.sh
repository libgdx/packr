#!/bin/sh
./fips set config osx-xcode-release
./fips clean && ./fips build
cp ../fips-deploy/packr-native/osx-xcode-release/packr ../../resources/packr-mac
