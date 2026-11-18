```
JMH version: 1.37
VM version: JDK 17.0.18, OpenJDK 64-Bit Server VM, 17.0.18+8
CPU: AMD Ryzen 5 3600 6-Core Processor
OS: Linux
```

Before is the byte-loop reader and writer that parsed every wide integer through a reversed byte
copy and used the stdlib's validating UTF-8 codec for every string; after is the current code.

| Benchmark | Fixture | Before (ns/op) | After (ns/op) | Before (B/op) | After (B/op) |
|---|---|---:|---:|---:|---:|
| decode | account | 219.7 | 111.2 | 1080 | 584 |
| decode | integers | 3718.2 | 2517.5 | 13456 | 11056 |
| decode | ascii | 719.7 | 530.2 | 5776 | 1552 |
| decode | unicode | 1454.1 | 1465.1 | 6416 | 6416 |
| encode | account | 349.0 | 136.5 | 1304 | 712 |
| encode | integers | 3934.0 | 2162.2 | 8320 | 6160 |
| encode | ascii | 3086.3 | 1068.2 | 8408 | 2904 |
| encode | unicode | 4134.6 | 2468.7 | 10376 | 2728 |

```
Benchmark                                      (fixture)  Mode  Cnt      Score    Error   Units
BorshCodecBenchmark.decode                       account  avgt    5    111.159 ±  0.905   ns/op
BorshCodecBenchmark.decode:gc.alloc.rate.norm    account  avgt    5    584.001 ±  0.001    B/op
BorshCodecBenchmark.decode                      integers  avgt    5   2517.546 ± 25.672   ns/op
BorshCodecBenchmark.decode:gc.alloc.rate.norm   integers  avgt    5  11056.015 ±  0.001    B/op
BorshCodecBenchmark.decode                         ascii  avgt    5    530.192 ±  2.683   ns/op
BorshCodecBenchmark.decode:gc.alloc.rate.norm      ascii  avgt    5   1552.003 ±  0.001    B/op
BorshCodecBenchmark.decode                       unicode  avgt    5   1465.106 ± 19.062   ns/op
BorshCodecBenchmark.decode:gc.alloc.rate.norm    unicode  avgt    5   6416.009 ±  0.001    B/op
BorshCodecBenchmark.encode                       account  avgt    5    136.548 ±  0.897   ns/op
BorshCodecBenchmark.encode:gc.alloc.rate.norm    account  avgt    5    712.001 ±  0.001    B/op
BorshCodecBenchmark.encode                      integers  avgt    5   2162.183 ± 19.841   ns/op
BorshCodecBenchmark.encode:gc.alloc.rate.norm   integers  avgt    5   6160.013 ±  0.001    B/op
BorshCodecBenchmark.encode                         ascii  avgt    5   1068.200 ±  6.111   ns/op
BorshCodecBenchmark.encode:gc.alloc.rate.norm      ascii  avgt    5   2904.006 ±  0.001    B/op
BorshCodecBenchmark.encode                       unicode  avgt    5   2468.720 ± 19.764   ns/op
BorshCodecBenchmark.encode:gc.alloc.rate.norm    unicode  avgt    5   2728.015 ±  0.001    B/op
```
