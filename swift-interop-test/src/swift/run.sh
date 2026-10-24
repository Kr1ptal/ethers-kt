#!/usr/bin/env bash
# Compile every .swift file here against the EthersKt framework in $1, into $2, and run the result.
set -euo pipefail

framework_dir="$1"
out_dir="$2"
mkdir -p "$out_dir"

# The header is what Swift actually sees; keep a copy next to the binary, CI uploads it for review.
cp "$framework_dir/EthersKt.framework/Headers/EthersKt.h" "$out_dir/EthersKt.h"

xcrun swiftc -swift-version 5 -parse-as-library \
  -F "$framework_dir" -framework EthersKt \
  -Xlinker -rpath -Xlinker "$framework_dir" \
  -o "$out_dir/swift-interop-test" \
  ./*.swift

"$out_dir/swift-interop-test"
