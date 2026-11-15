```
JMH version: 1.37
VM version: JDK 17.0.18, OpenJDK 64-Bit Server VM, 17.0.18+8
CPU: AMD Ryzen 5 3600 6-Core Processor
OS: Linux
```

Turning a WebSocket text message into the value its call or subscription receives. `tree` is what `WsClient` does
today; `direct` is the lower bound, decoding straight into the value with a deserializer known up front.

| Fixture | tree | direct | tree (KB/op) | direct (KB/op) |
|---|---:|---:|---:|---:|
| logs, 500-log response (µs) | 1273 | 927 | 1918 | 1228 |
| log, one-log notification (µs) | 3.24 | 2.73 | 5.8 | 3.7 |
| account, Solana account notification (µs) | 16.9 | 17.3 | 29.1 | 27.8 |

The account notification is dominated by decoding its 13,516 base64 characters, so skipping the tree saves little.

```
Benchmark                                     (fixture)  Mode  Cnt        Score    Error   Units
WsMessageBenchmark.direct                          logs  avgt    5      927.294 ± 16.196   us/op
WsMessageBenchmark.direct:gc.alloc.rate.norm       logs  avgt    5  1227714.165 ± 93.252    B/op
WsMessageBenchmark.direct                           log  avgt    5        2.729 ±  0.075   us/op
WsMessageBenchmark.direct:gc.alloc.rate.norm        log  avgt    5     3664.016 ±  0.001    B/op
WsMessageBenchmark.direct                       account  avgt    5       17.349 ±  0.850   us/op
WsMessageBenchmark.direct:gc.alloc.rate.norm    account  avgt    5    27760.103 ±  0.005    B/op
WsMessageBenchmark.tree                            logs  avgt    5     1273.045 ± 29.859   us/op
WsMessageBenchmark.tree:gc.alloc.rate.norm         logs  avgt    5  1917988.514 ± 42.562    B/op
WsMessageBenchmark.tree                             log  avgt    5        3.236 ±  0.061   us/op
WsMessageBenchmark.tree:gc.alloc.rate.norm          log  avgt    5     5792.019 ±  0.001    B/op
WsMessageBenchmark.tree                         account  avgt    5       16.850 ±  0.190   us/op
WsMessageBenchmark.tree:gc.alloc.rate.norm      account  avgt    5    29128.100 ±  0.001    B/op
```
