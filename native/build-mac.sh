premake4 --file=premake4-mac.lua xcode3
xcodebuild -alltargets clean
xcodebuild -configuration release
cp build/release/packr ../src/main/resources/packr-mac
