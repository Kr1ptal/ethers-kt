```
JMH version: 1.37
VM version: JDK 17.0.18, OpenJDK 64-Bit Server VM, 17.0.18+8
CPU: AMD Ryzen 5 3600 6-Core Processor
OS: Linux
```

Turning a WebSocket text message into the value its call or subscription receives. `tree` is what `WsClient` did
before it decoded messages directly; `direct` is the lower bound, decoding straight into the value with a
deserializer known up front; `client` is what `WsClient` does now.

| Fixture | tree | direct | client | tree (KB/op) | direct (KB/op) | client (KB/op) |
|---|---:|---:|---:|---:|---:|---:|
| logs, 500-log response (µs) | 1238 | 881 | 930 | 1918 | 1228 | 1229 |
| log, one-log notification (µs) | 3.32 | 2.69 | 3.14 | 5.8 | 3.7 | 3.7 |
| account, Solana account notification (µs) | 16.7 | 16.9 | 17.0 | 29.1 | 27.8 | 27.8 |

Responses and notifications decode without a tree. The scan that picks the deserializer stops at a response's `id`,
and reads a notification's `subscription` backwards when it is written last, as Solana writes it after a large
`result`. The account notification is dominated by decoding its 13,516 base64 characters, so skipping the tree saves
little there.

```
Benchmark                                     (fixture)  Mode  Cnt        Score     Error   Units
WsMessageBenchmark.client                          logs  avgt    5      929.877 ±  14.791   us/op
WsMessageBenchmark.client:gc.alloc.rate.norm       logs  avgt    5  1229444.778 ± 184.578    B/op
WsMessageBenchmark.client                           log  avgt    5        3.143 ±   0.010   us/op
WsMessageBenchmark.client:gc.alloc.rate.norm        log  avgt    5     3696.019 ±   0.001    B/op
WsMessageBenchmark.client                       account  avgt    5       16.986 ±   0.094   us/op
WsMessageBenchmark.client:gc.alloc.rate.norm    account  avgt    5    27792.101 ±   0.001    B/op
WsMessageBenchmark.direct                          logs  avgt    5      880.871 ±  20.476   us/op
WsMessageBenchmark.direct:gc.alloc.rate.norm       logs  avgt    5  1227740.943 ±  85.151    B/op
WsMessageBenchmark.direct                           log  avgt    5        2.690 ±   0.012   us/op
WsMessageBenchmark.direct:gc.alloc.rate.norm        log  avgt    5     3712.016 ±   0.001    B/op
WsMessageBenchmark.direct                       account  avgt    5       16.887 ±   0.119   us/op
WsMessageBenchmark.direct:gc.alloc.rate.norm    account  avgt    5    27760.101 ±   0.001    B/op
WsMessageBenchmark.tree                            logs  avgt    5     1238.159 ±  10.652   us/op
WsMessageBenchmark.tree:gc.alloc.rate.norm         logs  avgt    5  1917987.989 ±  37.848    B/op
WsMessageBenchmark.tree                             log  avgt    5        3.320 ±   0.023   us/op
WsMessageBenchmark.tree:gc.alloc.rate.norm          log  avgt    5     5792.020 ±   0.001    B/op
WsMessageBenchmark.tree                         account  avgt    5       16.669 ±   0.138   us/op
WsMessageBenchmark.tree:gc.alloc.rate.norm      account  avgt    5    29128.099 ±   0.001    B/op
```
