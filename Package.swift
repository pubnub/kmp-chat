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
      url: "https://github.com/pubnub/kmp-chat/releases/download/kotlin-0.14.0/PubNubChat.xcframework.zip",
      checksum: "2f90920d9831451d32ce0e46aa8d35df8573569ff120ad0b3de13144b6a08185"
    )
  ]
)
