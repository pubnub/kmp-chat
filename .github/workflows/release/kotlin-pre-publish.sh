set -e
echo "Update Swift Chat SDK checksum in Package.swift"
./gradlew -PENABLE_TARGET_IOS_ALL=true :podPublishReleaseXCFramework
find build/cocoapods/publish/release/PubNubChat.xcframework -exec touch -t 00000000 {} +
rm -f build/cocoapods/publish/release/PubNubChat.xcframework.zip
pushd build/cocoapods/publish/release
zip -X -vr PubNubChat.xcframework.zip PubNubChat.xcframework
touch -t 00000000 PubNubChat.xcframework.zip
popd
CHECKSUM=$(swift package compute-checksum build/cocoapods/publish/release/PubNubChat.xcframework.zip)
echo $CHECKSUM
sed -i.bak "s/checksum: \"[a-z0-9]*\"/checksum: \"$CHECKSUM\"/g" Package.swift
rm Package.swift.bak