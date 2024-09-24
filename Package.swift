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
      url: "https://github.com/pubnub/kmp-chat/releases/download/0.8.0/PubNubChat.xcframework.zip",
      checksum: "edf1fd649d3fe6257f18179a13a47724d5d177bf2d728ce4f75fa3c7a7873fc7"
    )
  ]
)
