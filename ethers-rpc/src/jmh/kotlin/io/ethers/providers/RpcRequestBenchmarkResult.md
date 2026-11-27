```
JMH version: 1.37
VM version: JDK 17.0.18, OpenJDK 64-Bit Server VM, 17.0.18+8
CPU: AMD Ryzen 5 3600 6-Core Processor
OS: Linux
```

Writing a JSON-RPC request up to the UTF-8 bytes handed to Ktor, with params built as `Provider` builds them. `send*`
builds and encodes the params and writes the request around them (a request sent for the first time); `resend*` writes
the request around params encoded before (a retry, the same call in another batch, or params a caching wrapper encoded
for its key); `build*` only builds the params. `address*` encodes an `Address` as a JSON string, the first time and
again.

- `master`: the hand-written serializers of `CallRequest`, `TracerConfig` and the others build a `JsonObject` tree that
  the JSON writer then walks; params are kept as a `String`, the request is built in a `StringBuilder` as a `String`,
  and Ktor converts that `String` into new UTF-8 bytes (`encodeToByteArray()` here, as Ktor does for a `String` body or
  `Frame.Text(String)`).
- `final`: those serializers write their fields straight to the encoder; params are kept as their UTF-8 bytes, and the
  request is written into one `ByteArray` of its exact size, which is what Ktor sends.

| Request | master (ns) | final (ns) | master (B/op) | final (B/op) |
|---|---:|---:|---:|---:|
| send `eth_getBalance` (Address, "latest") | 232 | 198 | 816 | 536 |
| send `eth_call` (CallRequest, "latest") | 576 | 391 | 1820 | 960 |
| send `debug_traceCall` (CallRequest, "latest", TracerConfig) | 953 | 768 | 2588 | 1360 |
| send `eth_blockNumber` (no params) | 73 | 45 | 312 | 80 |
| resend `eth_getBalance` | 82 | 48 | 472 | 136 |
| resend `eth_call` | 86 | 46 | 880 | 272 |
| resend `debug_traceCall` | 111 | 61 | 1128 | 352 |
| build `rpcParams(address, "latest")` | 5.8 | 6.3 | 72 | 80 |
| build `buildRpcParams { 3 params }` | 16 | 17 | 152 | 160 |
| `Address` encoded the first time | 103 | 106 | 392 | 392 |
| `Address` encoded again (hex cached) | 71 | 72 | 216 | 216 |
| creating an `Address` (part of the first-time case) | 1.9 | 2.1 | 24 | 24 |

Streaming the serializers saves about 160 ns and 470 B on a `CallRequest`, and about 140 ns and 740 B on
`debug_traceCall`'s `CallRequest` plus `TracerConfig` (the `a` candidate below against `master`, which differ only in
that). Writing bytes saves the rest: a resend now allocates only the request's bytes. `RpcParams` holds one more field,
for the kept bytes, which is the 8 B more in `build*`.

`Address` caches its hex text, so encoding the same `Address` again skips the hex encoding: about 30 ns and 152 B per
`Address` (first time minus again minus creating it). Request params go through that cached `toString()`; nothing on the
request path converts an `Address`, `Hash` or `Bytes` to hex without it.

How the bytes are built was chosen from these candidates, all on the streaming serializers, each writing byte-identical
requests (checked in the benchmark's setup), measured in a separate benchmark with the same settings:

- `a`: params kept as a `String`, the request built as a `String` in a `StringBuilder`, then `encodeToByteArray()`: what
  `master` does plus Ktor's conversion.
- `b` (adopted): params kept as UTF-8 bytes, converted once from kotlinx's `String`; the request written into one
  exactly-sized `ByteArray` around them.
- `c`: params encoded straight to bytes with kotlinx-serialization-json-io into a kotlinx-io `Buffer`, then the request
  written as in `b`. A resend is `b`'s.
- `d`: params kept as a `String` only; the request written into one exactly-sized `ByteArray`, copying the params'
  characters one by one.
- `d2`: params kept as a `String` only, converted to bytes on every send, then the request written as in `b`. A send is
  `b`'s.
- `e` (JVM only, for reference): kotlinx's JVM `encodeToStream` into a `ByteArrayOutputStream`, then as in `b`.

| Candidate | send `eth_getBalance` | send `eth_call` | send `debug_traceCall` | send `eth_blockNumber` | resend `eth_getBalance` | resend `eth_call` | resend `debug_traceCall` |
|---|---:|---:|---:|---:|---:|---:|---:|
| `a` | 233 ns, 800 B | 416 ns, 1352 B | 811 ns, 1852 B | 59 ns, 288 B | 72 ns, 448 B | 76 ns, 856 B | 102 ns, 1104 B |
| `b` | 215 ns, 560 B | 390 ns, 984 B | 739 ns, 1384 B | 45 ns, 80 B | 49 ns, 136 B | 47 ns, 272 B | 61 ns, 352 B |
| `c` | 270 ns, 488 B | 603 ns, 768 B | 1032 ns, 1088 B | 41 ns, 80 B | as `b` | as `b` | as `b` |
| `d` | 231 ns, 488 B | 572 ns, 768 B | 875 ns, 1088 B | 27 ns, 80 B | 75 ns, 136 B | 169 ns, 272 B | 217 ns, 352 B |
| `d2` | as `b` | as `b` | as `b` | as `b` | 57 ns, 208 B | 59 ns, 488 B | 78 ns, 648 B |
| `e` | 324 ns, 768 B | 709 ns, 1048 B | 1173 ns, 1896 B | 41 ns, 80 B | as `b` | as `b` | as `b` |

`b` is the fastest for every request with params, sent or resent, and its resend allocates only the request. `c` and `d`
allocate a little less on a send, as they never hold the params' bytes apart from the request, but kotlinx-io's writer
(`c`) is 25 to 55% slower, and copying characters one by one (`d`) makes a resend 1.5 to 3.6 times slower than `b`'s
array copy. `d2` converts the kept `String` on every resend. `encode()` still returns a `String`: with `b`, a request that
is only sent keeps just the bytes, and the `String` is decoded from them, once, only if `encode()` or `toString()` is
called.

master:

```
Benchmark                                                   Mode  Cnt      Score     Error   Units
RpcRequestBenchmark.addressCachedEncode                     avgt   20     71.155 ±   0.304   ns/op
RpcRequestBenchmark.addressCachedEncode:gc.alloc.rate.norm  avgt   20    216.000 ±   0.001    B/op
RpcRequestBenchmark.addressCreate                           avgt   20      1.905 ±   0.078   ns/op
RpcRequestBenchmark.addressCreate:gc.alloc.rate.norm        avgt   20     24.000 ±   0.001    B/op
RpcRequestBenchmark.addressFirstEncode                      avgt   20    103.468 ±   0.198   ns/op
RpcRequestBenchmark.addressFirstEncode:gc.alloc.rate.norm   avgt   20    392.001 ±   0.001    B/op
RpcRequestBenchmark.buildGetBalance                         avgt   20      5.758 ±   0.157   ns/op
RpcRequestBenchmark.buildGetBalance:gc.alloc.rate.norm      avgt   20     72.000 ±   0.001    B/op
RpcRequestBenchmark.buildTraceCall                          avgt   20     16.128 ±   0.189   ns/op
RpcRequestBenchmark.buildTraceCall:gc.alloc.rate.norm       avgt   20    152.000 ±   0.001    B/op
RpcRequestBenchmark.resendCall                              avgt   20     85.982 ±   0.465   ns/op
RpcRequestBenchmark.resendCall:gc.alloc.rate.norm           avgt   20    880.001 ±   0.001    B/op
RpcRequestBenchmark.resendGetBalance                        avgt   20     82.425 ±   3.273   ns/op
RpcRequestBenchmark.resendGetBalance:gc.alloc.rate.norm     avgt   20    472.000 ±   0.001    B/op
RpcRequestBenchmark.resendTraceCall                         avgt   20    110.724 ±   0.650   ns/op
RpcRequestBenchmark.resendTraceCall:gc.alloc.rate.norm      avgt   20   1128.001 ±   0.001    B/op
RpcRequestBenchmark.sendBlockNumber                         avgt   20     72.687 ±   4.007   ns/op
RpcRequestBenchmark.sendBlockNumber:gc.alloc.rate.norm      avgt   20    312.000 ±   0.001    B/op
RpcRequestBenchmark.sendCall                                avgt   20    575.687 ±   3.433   ns/op
RpcRequestBenchmark.sendCall:gc.alloc.rate.norm             avgt   20   1820.003 ±  10.691    B/op
RpcRequestBenchmark.sendGetBalance                          avgt   20    231.769 ±   6.917   ns/op
RpcRequestBenchmark.sendGetBalance:gc.alloc.rate.norm       avgt   20    816.001 ±   0.001    B/op
RpcRequestBenchmark.sendTraceCall                           avgt   20    952.985 ±  18.638   ns/op
RpcRequestBenchmark.sendTraceCall:gc.alloc.rate.norm        avgt   20   2588.006 ±  32.073    B/op
```

final:

```
Benchmark                                                   Mode  Cnt      Score     Error   Units
RpcRequestBenchmark.addressCachedEncode                     avgt   20     71.572 ±   0.221   ns/op
RpcRequestBenchmark.addressCachedEncode:gc.alloc.rate.norm  avgt   20    216.000 ±   0.001    B/op
RpcRequestBenchmark.addressCreate                           avgt   20      2.082 ±   0.144   ns/op
RpcRequestBenchmark.addressCreate:gc.alloc.rate.norm        avgt   20     24.000 ±   0.001    B/op
RpcRequestBenchmark.addressFirstEncode                      avgt   20    105.747 ±   1.846   ns/op
RpcRequestBenchmark.addressFirstEncode:gc.alloc.rate.norm   avgt   20    392.001 ±   0.001    B/op
RpcRequestBenchmark.buildGetBalance                         avgt   20      6.285 ±   0.207   ns/op
RpcRequestBenchmark.buildGetBalance:gc.alloc.rate.norm      avgt   20     80.000 ±   0.001    B/op
RpcRequestBenchmark.buildTraceCall                          avgt   20     16.716 ±   0.059   ns/op
RpcRequestBenchmark.buildTraceCall:gc.alloc.rate.norm       avgt   20    160.000 ±   0.001    B/op
RpcRequestBenchmark.resendCall                              avgt   20     45.868 ±   0.172   ns/op
RpcRequestBenchmark.resendCall:gc.alloc.rate.norm           avgt   20    272.000 ±   0.001    B/op
RpcRequestBenchmark.resendGetBalance                        avgt   20     48.235 ±   0.243   ns/op
RpcRequestBenchmark.resendGetBalance:gc.alloc.rate.norm     avgt   20    136.000 ±   0.001    B/op
RpcRequestBenchmark.resendTraceCall                         avgt   20     61.265 ±   1.185   ns/op
RpcRequestBenchmark.resendTraceCall:gc.alloc.rate.norm      avgt   20    352.000 ±   0.001    B/op
RpcRequestBenchmark.sendBlockNumber                         avgt   20     44.762 ±   0.492   ns/op
RpcRequestBenchmark.sendBlockNumber:gc.alloc.rate.norm      avgt   20     80.000 ±   0.001    B/op
RpcRequestBenchmark.sendCall                                avgt   20    391.297 ±  20.663   ns/op
RpcRequestBenchmark.sendCall:gc.alloc.rate.norm             avgt   20    960.002 ±   0.001    B/op
RpcRequestBenchmark.sendGetBalance                          avgt   20    198.195 ±   9.084   ns/op
RpcRequestBenchmark.sendGetBalance:gc.alloc.rate.norm       avgt   20    536.001 ±   0.001    B/op
RpcRequestBenchmark.sendTraceCall                           avgt   20    768.302 ±  18.133   ns/op
RpcRequestBenchmark.sendTraceCall:gc.alloc.rate.norm        avgt   20   1360.005 ±   0.001    B/op
```
