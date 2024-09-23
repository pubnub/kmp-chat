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
      url: "https://github.com/pubnub/kmp-chat/releases/download/0.0.1-DEV/PubNubChat.xcframework.zip",
      checksum: "45025af8f30f692cbdebcca916c364ddb4bf52676ae2344b5d61607e588177b4"
    )
  ]
)
