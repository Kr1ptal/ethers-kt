```
JMH version: 1.37
VM version: JDK 17.0.18, OpenJDK 64-Bit Server VM, 17.0.18+8
CPU: AMD Ryzen 5 3600 6-Core Processor
OS: Linux
```

What `WsMessageScanner` reads of each WebSocket message to pick its deserializer, before `WsClient` decodes it.
`before` is the scanner as first written; `after` is with these two changes:

- An `id` written after the `result`, as Agave and Nethermind write it, is read backwards from the end of the message
  rather than reached by skipping the whole result.
- A value is skipped only to reach the next key, so `params`, which a notification reads into, is not skipped first.

| Fixture | before (ns) | after (ns) |
|---|---:|---:|
| response, `eth_blockNumber` | 43.7 | 40.9 |
| logs-id-first, 500 logs, `id` first (geth) | 40.5 | 42.2 |
| logs-id-last, 500 logs, `id` last (Nethermind) | 85,387 | 51.9 |
| accounts-id-last, 100 Solana accounts, `id` last (Agave) | 14,055 | 51.4 |
| log, one-log `eth_subscription` notification | 443 | 171 |
| account, Solana `accountNotification` | 105 | 109 |

None of them allocate. For scale, decoding the 500-log response takes about 900 µs, so a scan of 85 µs was about a
tenth of it, and is now negligible.

Before:

```
Benchmark                                                 (fixture)  Mode  Cnt      Score      Error   Units
WsMessageScannerBenchmark.scan                             response  avgt    5     43.727 ±    0.922   ns/op
WsMessageScannerBenchmark.scan:gc.alloc.rate.norm          response  avgt    5     ≈ 10⁻⁴               B/op
WsMessageScannerBenchmark.scan                        logs-id-first  avgt    5     40.463 ±    0.794   ns/op
WsMessageScannerBenchmark.scan:gc.alloc.rate.norm     logs-id-first  avgt    5     ≈ 10⁻⁴               B/op
WsMessageScannerBenchmark.scan                         logs-id-last  avgt    5  85387.334 ± 1869.044   ns/op
WsMessageScannerBenchmark.scan:gc.alloc.rate.norm      logs-id-last  avgt    5      0.504 ±    0.011    B/op
WsMessageScannerBenchmark.scan                     accounts-id-last  avgt    5  14055.177 ±  968.483   ns/op
WsMessageScannerBenchmark.scan:gc.alloc.rate.norm  accounts-id-last  avgt    5      0.083 ±    0.006    B/op
WsMessageScannerBenchmark.scan                                  log  avgt    5    443.492 ±    4.828   ns/op
WsMessageScannerBenchmark.scan:gc.alloc.rate.norm               log  avgt    5      0.003 ±    0.001    B/op
WsMessageScannerBenchmark.scan                              account  avgt    5    105.349 ±    0.652   ns/op
WsMessageScannerBenchmark.scan:gc.alloc.rate.norm           account  avgt    5      0.001 ±    0.001    B/op
```

After:

```
Benchmark                                                 (fixture)  Mode  Cnt    Score    Error   Units
WsMessageScannerBenchmark.scan                             response  avgt    5   40.918 ±  0.194   ns/op
WsMessageScannerBenchmark.scan:gc.alloc.rate.norm          response  avgt    5   ≈ 10⁻⁴             B/op
WsMessageScannerBenchmark.scan                        logs-id-first  avgt    5   42.235 ±  0.326   ns/op
WsMessageScannerBenchmark.scan:gc.alloc.rate.norm     logs-id-first  avgt    5   ≈ 10⁻⁴             B/op
WsMessageScannerBenchmark.scan                         logs-id-last  avgt    5   51.881 ±  1.126   ns/op
WsMessageScannerBenchmark.scan:gc.alloc.rate.norm      logs-id-last  avgt    5   ≈ 10⁻⁴             B/op
WsMessageScannerBenchmark.scan                     accounts-id-last  avgt    5   51.418 ±  0.423   ns/op
WsMessageScannerBenchmark.scan:gc.alloc.rate.norm  accounts-id-last  avgt    5   ≈ 10⁻⁴             B/op
WsMessageScannerBenchmark.scan                                  log  avgt    5  171.452 ±  2.031   ns/op
WsMessageScannerBenchmark.scan:gc.alloc.rate.norm               log  avgt    5    0.001 ±  0.001    B/op
WsMessageScannerBenchmark.scan                              account  avgt    5  108.711 ±  1.727   ns/op
WsMessageScannerBenchmark.scan:gc.alloc.rate.norm           account  avgt    5    0.001 ±  0.001    B/op
```
