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
      url: "https://github.com/pubnub/kmp-chat/releases/download/kotlin-0.9.2/PubNubChat.xcframework.zip",
      checksum: "ba90004881639c1ae7e05cf93d68554a77717f8f7c89515a1894cce03a557122"
    )
  ]
)
