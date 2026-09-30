```
JMH version: 1.37
VM version: JDK 17.0.16, OpenJDK 64-Bit Server VM, 17.0.16+8
CPU: Apple M4 Max
OS: macOS 26.5.1
```

```
Benchmark                                                               (fixture)  Mode  Cnt        Score        Error   Units
SolanaTransactionCodecBenchmark.decodeEnvelope                     legacy-typical  avgt    3      196.338 ±     20.321   ns/op
SolanaTransactionCodecBenchmark.decodeEnvelope:gc.alloc.rate.norm  legacy-typical  avgt    3     1840.001 ±      0.001    B/op
SolanaTransactionCodecBenchmark.decodeEnvelope                       legacy-large  avgt    3      748.505 ±    667.055   ns/op
SolanaTransactionCodecBenchmark.decodeEnvelope:gc.alloc.rate.norm    legacy-large  avgt    3     4616.004 ±      0.004    B/op
SolanaTransactionCodecBenchmark.decodeEnvelope                         v0-typical  avgt    3      340.825 ±     95.081   ns/op
SolanaTransactionCodecBenchmark.decodeEnvelope:gc.alloc.rate.norm      v0-typical  avgt    3     2808.002 ±      0.001    B/op
SolanaTransactionCodecBenchmark.decodeEnvelope                           v0-large  avgt    3      758.495 ±    533.677   ns/op
SolanaTransactionCodecBenchmark.decodeEnvelope:gc.alloc.rate.norm        v0-large  avgt    3     4728.004 ±      0.004    B/op
SolanaTransactionCodecBenchmark.decodeEnvelope                         v1-typical  avgt    3      722.384 ±    113.011   ns/op
SolanaTransactionCodecBenchmark.decodeEnvelope:gc.alloc.rate.norm      v1-typical  avgt    3     4456.004 ±      0.001    B/op
SolanaTransactionCodecBenchmark.decodeEnvelope                           v1-large  avgt    3     1575.223 ±    455.225   ns/op
SolanaTransactionCodecBenchmark.decodeEnvelope:gc.alloc.rate.norm        v1-large  avgt    3    11568.009 ±      0.003    B/op

SolanaTransactionCodecBenchmark.decodeVerified                     legacy-typical  avgt    3   754335.514 ± 662706.487   ns/op
SolanaTransactionCodecBenchmark.decodeVerified:gc.alloc.rate.norm  legacy-typical  avgt    3  1338452.501 ±   1298.597    B/op
SolanaTransactionCodecBenchmark.decodeVerified                       legacy-large  avgt    3   760225.948 ± 198005.461   ns/op
SolanaTransactionCodecBenchmark.decodeVerified:gc.alloc.rate.norm    legacy-large  avgt    3  1342756.550 ±   1312.462    B/op
SolanaTransactionCodecBenchmark.decodeVerified                         v0-typical  avgt    3   715311.341 ±  28829.487   ns/op
SolanaTransactionCodecBenchmark.decodeVerified:gc.alloc.rate.norm      v0-typical  avgt    3  1339849.545 ±    922.259    B/op
SolanaTransactionCodecBenchmark.decodeVerified                           v0-large  avgt    3   728366.382 ± 122044.442   ns/op
SolanaTransactionCodecBenchmark.decodeVerified:gc.alloc.rate.norm        v0-large  avgt    3  1342862.237 ±    864.625    B/op
SolanaTransactionCodecBenchmark.decodeVerified                         v1-typical  avgt    3   700096.179 ±  14667.460   ns/op
SolanaTransactionCodecBenchmark.decodeVerified:gc.alloc.rate.norm      v1-typical  avgt    3  1342495.910 ±   1581.213    B/op
SolanaTransactionCodecBenchmark.decodeVerified                           v1-large  avgt    3   743214.006 ±  99646.518   ns/op
SolanaTransactionCodecBenchmark.decodeVerified:gc.alloc.rate.norm        v1-large  avgt    3  1353967.253 ±   1197.491    B/op

SolanaTransactionCodecBenchmark.encodeCached                       legacy-typical  avgt    3       15.626 ±      7.076   ns/op
SolanaTransactionCodecBenchmark.encodeCached:gc.alloc.rate.norm    legacy-typical  avgt    3      480.000 ±      0.001    B/op
SolanaTransactionCodecBenchmark.encodeCached                         legacy-large  avgt    3       42.791 ±      1.948   ns/op
SolanaTransactionCodecBenchmark.encodeCached:gc.alloc.rate.norm      legacy-large  avgt    3     1248.000 ±      0.001    B/op
SolanaTransactionCodecBenchmark.encodeCached                           v0-typical  avgt    3       22.220 ±      0.090   ns/op
SolanaTransactionCodecBenchmark.encodeCached:gc.alloc.rate.norm        v0-typical  avgt    3      712.000 ±      0.001    B/op
SolanaTransactionCodecBenchmark.encodeCached                             v0-large  avgt    3       40.123 ±      0.753   ns/op
SolanaTransactionCodecBenchmark.encodeCached:gc.alloc.rate.norm          v0-large  avgt    3     1248.000 ±      0.001    B/op
SolanaTransactionCodecBenchmark.encodeCached                           v1-typical  avgt    3       35.471 ±      2.564   ns/op
SolanaTransactionCodecBenchmark.encodeCached:gc.alloc.rate.norm        v1-typical  avgt    3     1200.000 ±      0.001    B/op
SolanaTransactionCodecBenchmark.encodeCached                             v1-large  avgt    3       95.082 ±     41.171   ns/op
SolanaTransactionCodecBenchmark.encodeCached:gc.alloc.rate.norm          v1-large  avgt    3     3392.001 ±      0.001    B/op

SolanaTransactionCodecBenchmark.encodeFresh                        legacy-typical  avgt    3       68.358 ±      7.387   ns/op
SolanaTransactionCodecBenchmark.encodeFresh:gc.alloc.rate.norm     legacy-typical  avgt    3      936.000 ±      0.001    B/op
SolanaTransactionCodecBenchmark.encodeFresh                          legacy-large  avgt    3      166.218 ±     33.580   ns/op
SolanaTransactionCodecBenchmark.encodeFresh:gc.alloc.rate.norm       legacy-large  avgt    3     2472.001 ±      0.001    B/op
SolanaTransactionCodecBenchmark.encodeFresh                            v0-typical  avgt    3      118.118 ±     10.091   ns/op
SolanaTransactionCodecBenchmark.encodeFresh:gc.alloc.rate.norm         v0-typical  avgt    3     1400.001 ±      0.001    B/op
SolanaTransactionCodecBenchmark.encodeFresh                              v0-large  avgt    3      180.659 ±     47.399   ns/op
SolanaTransactionCodecBenchmark.encodeFresh:gc.alloc.rate.norm           v0-large  avgt    3     2496.001 ±      0.001    B/op
SolanaTransactionCodecBenchmark.encodeFresh                            v1-typical  avgt    3      166.555 ±     35.650   ns/op
SolanaTransactionCodecBenchmark.encodeFresh:gc.alloc.rate.norm         v1-typical  avgt    3     2376.001 ±      0.001    B/op
SolanaTransactionCodecBenchmark.encodeFresh                              v1-large  avgt    3      392.755 ±     20.767   ns/op
SolanaTransactionCodecBenchmark.encodeFresh:gc.alloc.rate.norm           v1-large  avgt    3     6760.002 ±      0.001    B/op
```
