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
          pkgs = nixpkgs.legacyPackages.${system};
          jdk = pkgs.temurin-bin-21;
        in
        {
          default = pkgs.mkShell {
            packages = [ jdk ];

            shellHook = ''
              export JAVA_HOME="${jdk}"
              alias gradle=./gradlew
            '';
          };
        });
    };
}
