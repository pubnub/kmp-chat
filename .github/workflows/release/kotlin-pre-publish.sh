set -e
echo "Update Swift Chat SDK checksum in Package.swift"
./gradlew -PENABLE_TARGET_IOS_ALL=true :podPublishReleaseXCFramework
find build/cocoapods/publish/release/PubNubChat.xcframework -exec touch -t 00000000 {} +
rm -f build/cocoapods/publish/release/PubNubChat.xcframework.zip
zip -X -vr build/cocoapods/publish/release/PubNubChat.xcframework.zip build/cocoapods/publish/release/PubNubChat.xcframework
touch -t 00000000 build/cocoapods/publish/release/PubNubChat.xcframework.zip
CHECKSUM=$(swift package compute-checksum /Users/wojciech.kalicinski/projects/pubnub-chat/build/cocoapods/publish/release/PubNubChat.xcframework.zip)
echo $CHECKSUM
sed -i.bak "s/checksum: \"[a-z0-9]*\"/checksum: \"$CHECKSUM\"/g" Package.swift
rm Package.swift.bak