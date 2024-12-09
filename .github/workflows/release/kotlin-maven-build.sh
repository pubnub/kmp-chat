set -e
echo "Build Kotlin and Swift Chat SDK module artifacts"
./gradlew -PENABLE_TARGET_IOS_ALL=true :podPublishReleaseXCFramework assemble
find build/cocoapods/publish/release/PubNubChat.xcframework -exec touch -t 00000000 {} +
rm -f build/cocoapods/publish/release/PubNubChat.xcframework.zip
zip -X -vr build/cocoapods/publish/release/PubNubChat.xcframework.zip build/cocoapods/publish/release/PubNubChat.xcframework
touch -t 00000000 build/cocoapods/publish/release/PubNubChat.xcframework.zip
CHECKSUM=$(swift package compute-checksum build/cocoapods/publish/release/PubNubChat.xcframework.zip)
echo $CHECKSUM
mkdir build/gh_artifacts
mv build/cocoapods/publish/release/PubNubChat.xcframework.zip build/gh_artifacts/