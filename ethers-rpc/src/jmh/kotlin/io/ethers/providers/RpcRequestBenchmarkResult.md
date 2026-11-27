```
JMH version: 1.37
VM version: JDK 17.0.18, OpenJDK 64-Bit Server VM, 17.0.18+8
CPU: AMD Ryzen 5 3600 6-Core Processor
OS: Linux
```

Writing the text of a JSON-RPC request, with params built as `Provider` builds them. `send*` builds and encodes the
params and writes the request around them (a request sent for the first time); `resend*` writes the request around
params encoded before (a retry, the same call in another batch, or params a caching wrapper encoded for its key);
`build*` only builds the params.

- `master`: params are an `Array<*>` converted to a `JsonElement` by runtime type on every send, and the request is a
  `JsonObject` tree turned into text. It can't encode a `TracerConfig` param at all, so it has no `traceCall`, and it
  keeps nothing, so it has no `resend`. Measured with the same benchmarks ported to `Array<*>` params.
- `base`: `RpcParams` as on `ArtificialPB/rpc-params`: built through `RpcParamsBuilder`, each param encoded to a
  `JsonElement`, kept as a `JsonArray`, and the request still a `JsonObject` tree turned into text.
- `final`: the fixed-arity `rpcParams` build their two arrays directly; all params are encoded with one JSON writer
  into the text of their array, which is kept as a `String`; the request is written around it with a pre-sized
  `StringBuilder`.

| Request | master (ns) | base (ns) | final (ns) | master (B/op) | base (B/op) | final (B/op) |
|---|---:|---:|---:|---:|---:|---:|
| send `eth_getBalance` (Address, "latest") | 582 | 574 | 230 | 1832 | 1972 | 680 |
| send `eth_call` (CallRequest, "latest") | 1180 | 1243 | 564 | 3520 | 3672 | 1560 |
| send `debug_traceCall` (CallRequest, "latest", TracerConfig) | - | 2012 | 939 | - | 5608 | 2236 |
| send `eth_blockNumber` (no params) | 295 | 305 | 68 | 832 | 792 | 232 |
| resend `eth_getBalance` | - | 407 | 71 | - | 1072 | 336 |
| resend `eth_call` | - | 615 | 79 | - | 1656 | 608 |
| resend `debug_traceCall` | - | 848 | 82 | - | 1736 | 776 |
| build `rpcParams(address, "latest")` | - | 52 | 6.0 | - | 216 | 72 |
| build `buildRpcParams { 3 params }` | - | 60 | 30 | - | 256 | 176 |

A resend now only copies the kept params text into the request, so it costs about the same whatever the params are.

How the params are encoded was chosen from these candidates, each writing byte-identical requests (checked against the
`base` output in the benchmark's setup), measured in a separate benchmark with the same settings. All but `whole`
write the request around the params text with a `StringBuilder`; the `final` column above also includes checking whether
the method name needs escaping, which these leave out.

| Candidate | `eth_getBalance` | `eth_call` | `debug_traceCall` | `eth_blockNumber` | resend `eth_getBalance` / `eth_call` |
|---|---:|---:|---:|---:|---:|
| `perParam`: one `encodeToString` per param, joined with commas | 232 ns, 888 B | 624 ns, 2556 B | 1060 ns, 3504 B | 44 ns, 184 B | 48 ns, 296 B / 60 ns, 568 B |
| `onePass`: all params with one writer, a list serializer (adopted) | 196 ns, 640 B | 534 ns, 1508 B | 946 ns, 2160 B | 44 ns, 184 B | 48 ns, 296 B / 60 ns, 568 B |
| `whole`: the whole request with one writer, a class serializer | 297 ns, 464 B | 687 ns, 1064 B | 1046 ns, 1472 B | 168 ns, 336 B | same as a send: nothing to keep |

`whole` allocates the least, as it never holds the params as a separate string, but it is the slowest in time: the
kotlinx writer quotes every key it writes, and is costly to start even for no params. Nor can it keep anything, so a
resend costs a full send again, 4 to 11 times a kept `onePass` resend. `onePass` is the fastest in time for every
request, allocates about a third less than `perParam`, and keeps the params text for any resend.

master:

```
Benchmark                                                Mode  Cnt     Score    Error   Units
RpcRequestBenchmark.sendBlockNumber                     avgt   20   294.539 ±  6.511   ns/op
RpcRequestBenchmark.sendBlockNumber:gc.alloc.rate.norm  avgt   20   832.002 ±  0.001    B/op
RpcRequestBenchmark.sendCall                            avgt   20  1179.762 ± 11.082   ns/op
RpcRequestBenchmark.sendCall:gc.alloc.rate.norm         avgt   20  3520.007 ±  0.001    B/op
RpcRequestBenchmark.sendGetBalance                      avgt   20   581.868 ±  3.059   ns/op
RpcRequestBenchmark.sendGetBalance:gc.alloc.rate.norm   avgt   20  1832.003 ±  0.001    B/op
```

base:

```
Benchmark                                                 Mode  Cnt     Score    Error   Units
RpcRequestBenchmark.buildGetBalance                      avgt   20    52.418 ±  0.145   ns/op
RpcRequestBenchmark.buildGetBalance:gc.alloc.rate.norm   avgt   20   216.000 ±  0.001    B/op
RpcRequestBenchmark.buildTraceCall                       avgt   20    59.832 ±  0.106   ns/op
RpcRequestBenchmark.buildTraceCall:gc.alloc.rate.norm    avgt   20   256.000 ±  0.001    B/op
RpcRequestBenchmark.resendCall                           avgt   20   615.467 ± 14.214   ns/op
RpcRequestBenchmark.resendCall:gc.alloc.rate.norm        avgt   20  1656.004 ±  0.001    B/op
RpcRequestBenchmark.resendGetBalance                     avgt   20   407.386 ±  2.495   ns/op
RpcRequestBenchmark.resendGetBalance:gc.alloc.rate.norm  avgt   20  1072.002 ±  0.001    B/op
RpcRequestBenchmark.resendTraceCall                      avgt   20   847.972 ± 15.732   ns/op
RpcRequestBenchmark.resendTraceCall:gc.alloc.rate.norm   avgt   20  1736.005 ±  0.001    B/op
RpcRequestBenchmark.sendBlockNumber                      avgt   20   304.952 ±  6.478   ns/op
RpcRequestBenchmark.sendBlockNumber:gc.alloc.rate.norm   avgt   20   792.002 ±  0.001    B/op
RpcRequestBenchmark.sendCall                             avgt   20  1242.535 ±  4.614   ns/op
RpcRequestBenchmark.sendCall:gc.alloc.rate.norm          avgt   20  3672.007 ±  0.001    B/op
RpcRequestBenchmark.sendGetBalance                       avgt   20   573.826 ±  2.692   ns/op
RpcRequestBenchmark.sendGetBalance:gc.alloc.rate.norm    avgt   20  1972.003 ± 10.691    B/op
RpcRequestBenchmark.sendTraceCall                        avgt   20  2011.510 ± 26.758   ns/op
RpcRequestBenchmark.sendTraceCall:gc.alloc.rate.norm     avgt   20  5608.012 ±  0.001    B/op
```

final:

```
Benchmark                                                 Mode  Cnt      Score     Error   Units
RpcRequestBenchmark.buildGetBalance                      avgt   20      6.021 ±   0.322   ns/op
RpcRequestBenchmark.buildGetBalance:gc.alloc.rate.norm   avgt   20     72.000 ±   0.001    B/op
RpcRequestBenchmark.buildTraceCall                       avgt   20     30.086 ±   0.135   ns/op
RpcRequestBenchmark.buildTraceCall:gc.alloc.rate.norm    avgt   20    176.000 ±   0.001    B/op
RpcRequestBenchmark.resendCall                           avgt   20     78.958 ±   2.371   ns/op
RpcRequestBenchmark.resendCall:gc.alloc.rate.norm        avgt   20    608.000 ±   0.001    B/op
RpcRequestBenchmark.resendGetBalance                     avgt   20     71.301 ±   0.226   ns/op
RpcRequestBenchmark.resendGetBalance:gc.alloc.rate.norm  avgt   20    336.000 ±   0.001    B/op
RpcRequestBenchmark.resendTraceCall                      avgt   20     81.579 ±   0.254   ns/op
RpcRequestBenchmark.resendTraceCall:gc.alloc.rate.norm   avgt   20    776.000 ±   0.001    B/op
RpcRequestBenchmark.sendBlockNumber                      avgt   20     68.133 ±   0.597   ns/op
RpcRequestBenchmark.sendBlockNumber:gc.alloc.rate.norm   avgt   20    232.000 ±   0.001    B/op
RpcRequestBenchmark.sendCall                             avgt   20    563.678 ±   4.897   ns/op
RpcRequestBenchmark.sendCall:gc.alloc.rate.norm          avgt   20   1560.003 ±   0.001    B/op
RpcRequestBenchmark.sendGetBalance                       avgt   20    229.530 ±   1.601   ns/op
RpcRequestBenchmark.sendGetBalance:gc.alloc.rate.norm    avgt   20    680.001 ±   0.001    B/op
RpcRequestBenchmark.sendTraceCall                        avgt   20    939.022 ±  21.104   ns/op
RpcRequestBenchmark.sendTraceCall:gc.alloc.rate.norm     avgt   20   2236.006 ±  32.073    B/op
```
