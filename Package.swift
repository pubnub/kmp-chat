// swift-tools-version: 5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
  name: "PubNubChat",
  platforms: [
    .iOS(.v14),
    .macOS(.v11),
    .tvOS(.v14)
  ],
  products: [
    .library(
      name: "PubNubChat",
      targets: ["PubNubChatRemoteBinaryPackage"]),
  ],
  targets: [
    .binaryTarget(
      name: "PubNubChatRemoteBinaryPackage",
      url: "https://github.com/pubnub/kmp-chat/releases/download/kotlin-1.0.0/PubNubChat.xcframework.zip",
      checksum: "a8bdfbaeefcedf5452b99af484f52e7af07c6d3b4d7832fb71171808f1ca53c0"
    )
  ]
)
