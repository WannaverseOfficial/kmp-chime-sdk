// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "_chime-sdk",
  platforms: [
    .iOS("15.0")
  ],
  products: [
    .library(
      name: "_chime-sdk",
      type: .none,
      targets: ["_chime-sdk"]
    )
  ],
  dependencies: [
    .package(
      url: "https://github.com/aws/amazon-chime-sdk-ios-spm.git",
      exact: "0.27.4"
    )
  ],
  targets: [
    .target(
      name: "_chime-sdk",
      dependencies: [
        .product(
          name: "AmazonChimeSDK",
          package: "amazon-chime-sdk-ios-spm"
        ),
        .product(
          name: "AmazonChimeSDKMedia",
          package: "amazon-chime-sdk-ios-spm"
        )
      ]
    )
  ]
)
