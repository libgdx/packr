#!/bin/sh
./fips set config linux-make-release
./fips clean && ./fips build

set arch = $(uname -m | grep '64')

if $arch; then
  cp ../fips-deploy/packr-native/linux-make-release/packr ../../resources/packr-linux-x64
else
  cp ../fips-deploy/packr-native/linux-make-release/packr ../../resources/packr-linux
fi
