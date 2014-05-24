premake4 --file=premake4-linux.lua gmake
make clean config=release32
make config=release32
mv packr ../src/main/resources/packr-linux
make clean config=release64
make config=release64
mv packr ../src/main/resources/packr-linux-x64
