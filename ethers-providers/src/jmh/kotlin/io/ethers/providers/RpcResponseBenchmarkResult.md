```
JMH version: 1.37
VM version: JDK 17.0.18, OpenJDK 64-Bit Server VM, 17.0.18+8
CPU: AMD Ryzen 5 3600 6-Core Processor
OS: Linux
```

Turning a JSON-RPC response body into the provider's result, by each route kotlinx offers. `text` is only the
body-to-`String` step that `current` and `direct` include. Solana account data is decoded with `FastBase64`, as on
master; its roughly 550,000 base64 characters take about 300 µs of each `accounts` route.

kotlinx.serialization 1.11.0, the version the project uses:

| Fixture | current | source | direct | sourceDirect | text |
|---|---:|---:|---:|---:|---:|
| block (µs) | 974 | 2335 | 914 | 2406 | 147 |
| logs (µs) | 1801 | 3893 | 1256 | 3499 | 224 |
| accounts (µs) | 1162 | 4860 | 1124 | 4919 | 375 |
| block (MB/op) | 2.14 | 1.22 | 2.14 | 1.22 | 0.92 |
| logs (MB/op) | 3.30 | 1.90 | 2.62 | 1.22 | 1.40 |
| accounts (MB/op) | 3.56 | 1.30 | 3.54 | 1.29 | 2.28 |

kotlinx.serialization 1.13.0, whose kotlinx-io reader has an ASCII fast path (Kotlin/kotlinx.serialization#3225):

| Fixture | current | source | direct | sourceDirect |
|---|---:|---:|---:|---:|
| block (µs) | 947 | 930 | 956 | 998 |
| logs (µs) | 1647 | 1778 | 1236 | 1548 |
| accounts (µs) | 1148 | 1889 | 1092 | 1886 |
| block (MB/op) | 2.16 | 1.24 | 2.16 | 1.24 |
| logs (MB/op) | 3.34 | 1.95 | 2.62 | 1.22 |
| accounts (MB/op) | 3.55 | 1.31 | 3.55 | 1.31 |

1.11.0:

```
Benchmark                                             (fixture)  Mode  Cnt        Score     Error   Units
RpcResponseBenchmark.current                              block  avgt    5      974.287 ±  13.096   us/op
RpcResponseBenchmark.current:gc.alloc.rate.norm           block  avgt    5  2137190.084 ±   2.253    B/op
RpcResponseBenchmark.current                               logs  avgt    5     1801.293 ±  16.186   us/op
RpcResponseBenchmark.current:gc.alloc.rate.norm            logs  avgt    5  3295977.231 ±  55.691    B/op
RpcResponseBenchmark.current                           accounts  avgt    5     1161.598 ±  15.991   us/op
RpcResponseBenchmark.current:gc.alloc.rate.norm        accounts  avgt    5  3556923.304 ±  36.042    B/op
RpcResponseBenchmark.direct                               block  avgt    5      913.877 ±  20.131   us/op
RpcResponseBenchmark.direct:gc.alloc.rate.norm            block  avgt    5  2136752.955 ±  28.621    B/op
RpcResponseBenchmark.direct                                logs  avgt    5     1256.059 ±   9.590   us/op
RpcResponseBenchmark.direct:gc.alloc.rate.norm             logs  avgt    5  2620799.622 ±   0.721    B/op
RpcResponseBenchmark.direct                            accounts  avgt    5     1124.339 ±  20.490   us/op
RpcResponseBenchmark.direct:gc.alloc.rate.norm         accounts  avgt    5  3539555.043 ±  35.671    B/op
RpcResponseBenchmark.source                               block  avgt    5     2335.300 ±  39.792   us/op
RpcResponseBenchmark.source:gc.alloc.rate.norm            block  avgt    5  1219973.896 ±   0.264    B/op
RpcResponseBenchmark.source                                logs  avgt    5     3892.883 ± 135.857   us/op
RpcResponseBenchmark.source:gc.alloc.rate.norm             logs  avgt    5  1898847.113 ±   1.070    B/op
RpcResponseBenchmark.source                            accounts  avgt    5     4860.303 ±  11.789   us/op
RpcResponseBenchmark.source:gc.alloc.rate.norm         accounts  avgt    5  1300604.816 ±   0.549    B/op
RpcResponseBenchmark.sourceDirect                         block  avgt    5     2405.576 ±  87.355   us/op
RpcResponseBenchmark.sourceDirect:gc.alloc.rate.norm      block  avgt    5  1219534.554 ±   2.611    B/op
RpcResponseBenchmark.sourceDirect                          logs  avgt    5     3499.400 ±  44.965   us/op
RpcResponseBenchmark.sourceDirect:gc.alloc.rate.norm       logs  avgt    5  1223676.747 ±   0.472    B/op
RpcResponseBenchmark.sourceDirect                      accounts  avgt    5     4919.012 ± 145.609   us/op
RpcResponseBenchmark.sourceDirect:gc.alloc.rate.norm   accounts  avgt    5  1294349.229 ±   1.101    B/op
RpcResponseBenchmark.text                                 block  avgt    5      147.067 ±   5.202   us/op
RpcResponseBenchmark.text:gc.alloc.rate.norm              block  avgt    5   920736.876 ±   0.031    B/op
RpcResponseBenchmark.text                                  logs  avgt    5      224.196 ±  18.184   us/op
RpcResponseBenchmark.text:gc.alloc.rate.norm               logs  avgt    5  1397225.336 ±   0.109    B/op
RpcResponseBenchmark.text                              accounts  avgt    5      374.707 ±   3.809   us/op
RpcResponseBenchmark.text:gc.alloc.rate.norm           accounts  avgt    5  2280266.233 ±   0.022    B/op
```

1.13.0:

```
Benchmark                                             (fixture)  Mode  Cnt        Score     Error   Units
RpcResponseBenchmark.current                              block  avgt    5      946.630 ±  15.938   us/op
RpcResponseBenchmark.current:gc.alloc.rate.norm           block  avgt    5  2160385.429 ±  32.309    B/op
RpcResponseBenchmark.current                               logs  avgt    5     1647.496 ±  19.321   us/op
RpcResponseBenchmark.current:gc.alloc.rate.norm            logs  avgt    5  3343983.790 ±  51.153    B/op
RpcResponseBenchmark.current                           accounts  avgt    5     1148.397 ±  22.564   us/op
RpcResponseBenchmark.current:gc.alloc.rate.norm        accounts  avgt    5  3554614.029 ±  42.709    B/op
RpcResponseBenchmark.direct                               block  avgt    5      955.986 ±  18.781   us/op
RpcResponseBenchmark.direct:gc.alloc.rate.norm            block  avgt    5  2159897.357 ±  31.126    B/op
RpcResponseBenchmark.direct                                logs  avgt    5     1236.090 ±  22.397   us/op
RpcResponseBenchmark.direct:gc.alloc.rate.norm             logs  avgt    5  2620799.603 ±   1.551    B/op
RpcResponseBenchmark.direct                            accounts  avgt    5     1092.438 ±  10.957   us/op
RpcResponseBenchmark.direct:gc.alloc.rate.norm         accounts  avgt    5  3547482.785 ±  33.461    B/op
RpcResponseBenchmark.source                               block  avgt    5      929.788 ±  12.360   us/op
RpcResponseBenchmark.source:gc.alloc.rate.norm            block  avgt    5  1243161.181 ±  31.325    B/op
RpcResponseBenchmark.source                                logs  avgt    5     1778.071 ±  11.956   us/op
RpcResponseBenchmark.source:gc.alloc.rate.norm             logs  avgt    5  1946849.041 ±  55.253    B/op
RpcResponseBenchmark.source                            accounts  avgt    5     1888.973 ±  18.882   us/op
RpcResponseBenchmark.source:gc.alloc.rate.norm         accounts  avgt    5  1312571.269 ±   0.231    B/op
RpcResponseBenchmark.sourceDirect                         block  avgt    5      997.910 ±  13.563   us/op
RpcResponseBenchmark.sourceDirect:gc.alloc.rate.norm      block  avgt    5  1242673.760 ±  31.826    B/op
RpcResponseBenchmark.sourceDirect                          logs  avgt    5     1548.162 ±  18.628   us/op
RpcResponseBenchmark.sourceDirect:gc.alloc.rate.norm       logs  avgt    5  1223665.240 ±   0.235    B/op
RpcResponseBenchmark.sourceDirect                      accounts  avgt    5     1886.305 ±  46.914   us/op
RpcResponseBenchmark.sourceDirect:gc.alloc.rate.norm   accounts  avgt    5  1305434.214 ±  59.957    B/op
RpcResponseBenchmark.text                                 block  avgt    5      146.871 ±   2.008   us/op
RpcResponseBenchmark.text:gc.alloc.rate.norm              block  avgt    5   920736.875 ±   0.012    B/op
RpcResponseBenchmark.text                                  logs  avgt    5      222.034 ±   5.537   us/op
RpcResponseBenchmark.text:gc.alloc.rate.norm               logs  avgt    5  1397225.323 ±   0.033    B/op
RpcResponseBenchmark.text                              accounts  avgt    5      376.106 ±   3.318   us/op
RpcResponseBenchmark.text:gc.alloc.rate.norm           accounts  avgt    5  2280266.241 ±   0.020    B/op
```
