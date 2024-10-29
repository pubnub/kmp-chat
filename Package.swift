// swift-tools-version: 5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
  name: "PubNubChat",
  platforms: [.iOS(.v14)],
  products: [
    .library(
      name: "PubNubChat",
      targets: ["PubNubChatRemoteBinaryPackage"]),
  ],
  targets: [
    .binaryTarget(
      name: "PubNubChatRemoteBinaryPackage",
      url: "https://github.com/pubnub/kmp-chat/releases/download/0.8.201-dev/PubNubChat.xcframework.zip",
      checksum: "c81df7f9514d39e6fd1813dee9c6ef966b4bcf62f21ad670f7a8e29bc92d4cc9"
    )
  ]
)
