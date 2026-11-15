```
JMH version: 1.37
VM version: JDK 17.0.18, OpenJDK 64-Bit Server VM, 17.0.18+8
CPU: AMD Ryzen 5 3600 6-Core Processor
OS: Linux
```

Turning a WebSocket text message into the value its call or subscription receives. `tree` is what `WsClient` did
before it decoded responses directly; `direct` is the lower bound, decoding straight into the value with a
deserializer known up front; `client` is what `WsClient` does now.

| Fixture | tree | direct | client | tree (KB/op) | direct (KB/op) | client (KB/op) |
|---|---:|---:|---:|---:|---:|---:|
| logs, 500-log response (µs) | 1232 | 908 | 895 | 1918 | 1228 | 1229 |
| log, one-log notification (µs) | 3.38 | 2.62 | 3.41 | 5.8 | 3.7 | 5.8 |
| account, Solana account notification (µs) | 16.8 | 16.4 | 16.7 | 29.1 | 27.8 | 29.1 |

Responses to calls now decode at the lower bound: the scan stops at the `id`, the second key in practice.
Notifications still go through the tree. The account notification is dominated by decoding its 13,516 base64
characters, so skipping the tree saves little there.

```
Benchmark                                     (fixture)  Mode  Cnt        Score     Error   Units
WsMessageBenchmark.client                          logs  avgt    5      895.075 ±  23.207   us/op
WsMessageBenchmark.client:gc.alloc.rate.norm       logs  avgt    5  1229424.031 ± 238.147    B/op
WsMessageBenchmark.client                           log  avgt    5        3.409 ±   0.015   us/op
WsMessageBenchmark.client:gc.alloc.rate.norm        log  avgt    5     5792.020 ±   0.001    B/op
WsMessageBenchmark.client                       account  avgt    5       16.730 ±   0.275   us/op
WsMessageBenchmark.client:gc.alloc.rate.norm    account  avgt    5    29128.099 ±   0.002    B/op
WsMessageBenchmark.direct                          logs  avgt    5      908.338 ±  18.954   us/op
WsMessageBenchmark.direct:gc.alloc.rate.norm       logs  avgt    5  1227742.474 ±  90.480    B/op
WsMessageBenchmark.direct                           log  avgt    5        2.622 ±   0.060   us/op
WsMessageBenchmark.direct:gc.alloc.rate.norm        log  avgt    5     3664.016 ±   0.001    B/op
WsMessageBenchmark.direct                       account  avgt    5       16.351 ±   0.221   us/op
WsMessageBenchmark.direct:gc.alloc.rate.norm    account  avgt    5    27760.097 ±   0.002    B/op
WsMessageBenchmark.tree                            logs  avgt    5     1232.493 ±  11.764   us/op
WsMessageBenchmark.tree:gc.alloc.rate.norm         logs  avgt    5  1917987.983 ±  37.777    B/op
WsMessageBenchmark.tree                             log  avgt    5        3.375 ±   0.019   us/op
WsMessageBenchmark.tree:gc.alloc.rate.norm          log  avgt    5     5792.020 ±   0.001    B/op
WsMessageBenchmark.tree                         account  avgt    5       16.750 ±   0.219   us/op
WsMessageBenchmark.tree:gc.alloc.rate.norm      account  avgt    5    29128.100 ±   0.001    B/op
```
