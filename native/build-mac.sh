premake4 xcode3
xcodebuild -alltargets clean
xcodebuild -configuration release
cp build/release/packr ../src/main/resources/packr-mac
