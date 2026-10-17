{
  description = "ethers-kt dev shell";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/25.11";
  };

  outputs = { self, nixpkgs }:
    let
      supportedSystems = [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];
      forAllSystems = nixpkgs.lib.genAttrs supportedSystems;
    in
    {
      devShells = forAllSystems (system:
        let
          pkgs = import nixpkgs {
            inherit system;
            config = {
              allowUnfree = true;
              android_sdk.accept_license = true;
            };
          };
          jdk = pkgs.temurin-bin-21;
          androidSdk = (pkgs.androidenv.composeAndroidPackages {
            # Match compileSdk and the Android Gradle plugin's build tools.
            platformVersions = [ "36" ];
            buildToolsVersions = [ "36.0.0" ];
            includeEmulator = false;
            includeSystemImages = false;
            includeNDK = false;
          }).androidsdk;
        in
        {
          default = pkgs.mkShell {
            packages = [ jdk androidSdk ];

            ANDROID_HOME = "${androidSdk}/libexec/android-sdk";
            ANDROID_SDK_ROOT = "${androidSdk}/libexec/android-sdk";

            shellHook = ''
              export JAVA_HOME="${jdk}"
              alias gradle=./gradlew
            '';
          };
        });
    };
}
