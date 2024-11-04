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
      url: "https://github.com/pubnub/kmp-chat/releases/download/0.8.3-dev/PubNubChat.xcframework.zip",
      checksum: "4b0154602aae95f77d26e054dcc5b360bac8a0a6c8fe6e1da9d9aadaafc5f050"
    )
  ]
)
