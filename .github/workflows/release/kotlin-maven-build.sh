set -e
echo "Build Kotlin and Swift Chat SDK module artifacts"
./gradlew -PENABLE_TARGET_IOS_ALL=true :podPublishReleaseXCFramework assemble
rm -f build/cocoapods/publish/release/PubNubChat.xcframework.zip
pushd build/cocoapods/publish/release
zip -X -vr PubNubChat.xcframework.zip PubNubChat.xcframework
popd
CHECKSUM=$(swift package compute-checksum build/cocoapods/publish/release/PubNubChat.xcframework.zip)
mkdir build/gh_artifacts
mv build/cocoapods/publish/release/PubNubChat.xcframework.zip build/gh_artifacts/
echo "$CHECKSUM" > build/gh_artifacts/PubNubChat.xcframework.checksum.txt