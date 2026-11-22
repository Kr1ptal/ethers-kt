```
JMH version: 1.37
VM version: JDK 17.0.18, OpenJDK 64-Bit Server VM, 17.0.18+8
CPU: AMD Ryzen 5 3600 6-Core Processor
OS: Linux
```

Mainnet accounts through their generated bindings, next to the RPC steps in front of them: `rpcJson` parses a
`getAccountInfo` value as the provider does, of which `base64` (the multiplatform decoder the provider uses) is a
part; `base64Jdk` is the JDK's decoder for comparison.

Encode, before and after sizing the writer from the codec's fixed encoded size:

| Fixture | Before (ns/op) | After (ns/op) | Before (B/op) | After (B/op) |
|---|---:|---:|---:|---:|
| pool | 1839.5 | 1370.3 | 5840 | 1712 |
| lbPair | 748.8 | 586.3 | 3072 | 1008 |
| binArray | 15171.0 | 10344.4 | 47344 | 14496 |

```
Benchmark                                               (fixture)  Mode  Cnt      Score     Error   Units
GeneratedAccountBenchmark.base64                             pool  avgt    5   2413.534 ±  33.293   ns/op
GeneratedAccountBenchmark.base64:gc.alloc.rate.norm          pool  avgt    5   3640.014 ±   0.001    B/op
GeneratedAccountBenchmark.base64                           lbPair  avgt    5   1325.970 ±  34.990   ns/op
GeneratedAccountBenchmark.base64:gc.alloc.rate.norm        lbPair  avgt    5   2144.008 ±   0.001    B/op
GeneratedAccountBenchmark.base64                         binArray  avgt    5  15430.084 ± 142.452   ns/op
GeneratedAccountBenchmark.base64:gc.alloc.rate.norm      binArray  avgt    5  23688.092 ±   0.001    B/op
GeneratedAccountBenchmark.base64Jdk                          pool  avgt    5   1297.400 ±   5.928   ns/op
GeneratedAccountBenchmark.base64Jdk:gc.alloc.rate.norm       pool  avgt    5   3640.008 ±   0.001    B/op
GeneratedAccountBenchmark.base64Jdk                        lbPair  avgt    5    758.155 ±  16.292   ns/op
GeneratedAccountBenchmark.base64Jdk:gc.alloc.rate.norm     lbPair  avgt    5   2144.005 ±   0.001    B/op
GeneratedAccountBenchmark.base64Jdk                      binArray  avgt    5   8432.838 ± 161.811   ns/op
GeneratedAccountBenchmark.base64Jdk:gc.alloc.rate.norm   binArray  avgt    5  23688.050 ±   0.001    B/op
GeneratedAccountBenchmark.decode                             pool  avgt    5   1414.968 ±   9.525   ns/op
GeneratedAccountBenchmark.decode:gc.alloc.rate.norm          pool  avgt    5   3056.008 ±   0.001    B/op
GeneratedAccountBenchmark.decode                           lbPair  avgt    5    668.930 ±   5.393   ns/op
GeneratedAccountBenchmark.decode:gc.alloc.rate.norm        lbPair  avgt    5   2152.004 ±   0.001    B/op
GeneratedAccountBenchmark.decode                         binArray  avgt    5  13369.136 ± 152.408   ns/op
GeneratedAccountBenchmark.decode:gc.alloc.rate.norm      binArray  avgt    5  36536.080 ±   0.001    B/op
GeneratedAccountBenchmark.encode                             pool  avgt    5   1370.258 ±   9.849   ns/op
GeneratedAccountBenchmark.encode:gc.alloc.rate.norm          pool  avgt    5   1712.008 ±   0.001    B/op
GeneratedAccountBenchmark.encode                           lbPair  avgt    5    586.278 ±  15.267   ns/op
GeneratedAccountBenchmark.encode:gc.alloc.rate.norm        lbPair  avgt    5   1008.003 ±   0.001    B/op
GeneratedAccountBenchmark.encode                         binArray  avgt    5  10344.420 ±  82.205   ns/op
GeneratedAccountBenchmark.encode:gc.alloc.rate.norm      binArray  avgt    5  14496.062 ±   0.001    B/op
GeneratedAccountBenchmark.rpcJson                            pool  avgt    5   5009.152 ±  75.740   ns/op
GeneratedAccountBenchmark.rpcJson:gc.alloc.rate.norm         pool  avgt    5   8904.030 ±   0.001    B/op
GeneratedAccountBenchmark.rpcJson                          lbPair  avgt    5   3858.120 ±  12.717   ns/op
GeneratedAccountBenchmark.rpcJson:gc.alloc.rate.norm       lbPair  avgt    5   6552.023 ±   0.001    B/op
GeneratedAccountBenchmark.rpcJson                        binArray  avgt    5  22195.131 ± 115.149   ns/op
GeneratedAccountBenchmark.rpcJson:gc.alloc.rate.norm     binArray  avgt    5  40408.132 ±   0.001    B/op
```
