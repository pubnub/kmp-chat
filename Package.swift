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
      url: "https://github.com/pubnub/kmp-chat/releases/download/chat-v0.9.0/PubNubChat.xcframework.zip",
      checksum: "e043957bd849c7243085368c0e64607cec6dc2e8db6c863f1a80c025d11f6497"
    )
  ]
)
