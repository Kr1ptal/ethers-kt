```
JMH version: 1.37
VM version: JDK 17.0.16, OpenJDK 64-Bit Server VM, 17.0.16+8
CPU: Apple M4 Max
OS: macOS 26.5.1
```

```
Benchmark                                                               (fixture)  Mode  Cnt        Score        Error   Units
SolanaTransactionCodecBenchmark.decodeEnvelope                     legacy-typical  avgt    3      531.643 ±    123.766   ns/op
SolanaTransactionCodecBenchmark.decodeEnvelope:gc.alloc.rate.norm  legacy-typical  avgt    3     4192.003 ±      0.001    B/op
SolanaTransactionCodecBenchmark.decodeEnvelope                       legacy-large  avgt    3     2382.262 ±    474.736   ns/op
SolanaTransactionCodecBenchmark.decodeEnvelope:gc.alloc.rate.norm    legacy-large  avgt    3     9536.013 ±      0.025    B/op
SolanaTransactionCodecBenchmark.decodeEnvelope                         v0-typical  avgt    3     1292.774 ±    727.927   ns/op
SolanaTransactionCodecBenchmark.decodeEnvelope:gc.alloc.rate.norm      v0-typical  avgt    3     6592.008 ±      0.004    B/op
SolanaTransactionCodecBenchmark.decodeEnvelope                           v0-large  avgt    3     2691.145 ±    206.378   ns/op
SolanaTransactionCodecBenchmark.decodeEnvelope:gc.alloc.rate.norm        v0-large  avgt    3    10080.016 ±      0.003    B/op
SolanaTransactionCodecBenchmark.decodeEnvelope                         v1-typical  avgt    3     2535.995 ±    578.246   ns/op
SolanaTransactionCodecBenchmark.decodeEnvelope:gc.alloc.rate.norm      v1-typical  avgt    3    10040.015 ±      0.003    B/op
SolanaTransactionCodecBenchmark.decodeEnvelope                           v1-large  avgt    3     7253.889 ±   1145.828   ns/op
SolanaTransactionCodecBenchmark.decodeEnvelope:gc.alloc.rate.norm        v1-large  avgt    3    31904.043 ±      0.009    B/op

SolanaTransactionCodecBenchmark.decodeVerified                     legacy-typical  avgt    3  1465170.048 ±  56894.931   ns/op
SolanaTransactionCodecBenchmark.decodeVerified:gc.alloc.rate.norm  legacy-typical  avgt    3  2677986.325 ±   1118.295    B/op
SolanaTransactionCodecBenchmark.decodeVerified                       legacy-large  avgt    3  1458352.256 ± 374333.720   ns/op
SolanaTransactionCodecBenchmark.decodeVerified:gc.alloc.rate.norm    legacy-large  avgt    3  2687154.982 ±   1887.050    B/op
SolanaTransactionCodecBenchmark.decodeVerified                         v0-typical  avgt    3  1439458.992 ± 144975.167   ns/op
SolanaTransactionCodecBenchmark.decodeVerified:gc.alloc.rate.norm      v0-typical  avgt    3  2681371.608 ±   2146.619    B/op
SolanaTransactionCodecBenchmark.decodeVerified                           v0-large  avgt    3  1482123.842 ± 457352.155   ns/op
SolanaTransactionCodecBenchmark.decodeVerified:gc.alloc.rate.norm        v0-large  avgt    3  2687685.673 ±   2033.878    B/op
SolanaTransactionCodecBenchmark.decodeVerified                         v1-typical  avgt    3  1484144.257 ±  49496.802   ns/op
SolanaTransactionCodecBenchmark.decodeVerified:gc.alloc.rate.norm      v1-typical  avgt    3  2687437.300 ±   1715.118    B/op
SolanaTransactionCodecBenchmark.decodeVerified                           v1-large  avgt    3  1528240.325 ± 628685.250   ns/op
SolanaTransactionCodecBenchmark.decodeVerified:gc.alloc.rate.norm        v1-large  avgt    3  2720314.853 ±   1525.653    B/op

SolanaTransactionCodecBenchmark.encodeCached                       legacy-typical  avgt    3      116.192 ±     53.973   ns/op
SolanaTransactionCodecBenchmark.encodeCached:gc.alloc.rate.norm    legacy-typical  avgt    3     2256.001 ±      0.001    B/op
SolanaTransactionCodecBenchmark.encodeCached                         legacy-large  avgt    3       94.587 ±     56.409   ns/op
SolanaTransactionCodecBenchmark.encodeCached:gc.alloc.rate.norm      legacy-large  avgt    3     3792.001 ±      0.001    B/op
SolanaTransactionCodecBenchmark.encodeCached                           v0-typical  avgt    3       95.499 ±     57.610   ns/op
SolanaTransactionCodecBenchmark.encodeCached:gc.alloc.rate.norm        v0-typical  avgt    3     2720.001 ±      0.001    B/op
SolanaTransactionCodecBenchmark.encodeCached                             v0-large  avgt    3      100.369 ±    213.597   ns/op
SolanaTransactionCodecBenchmark.encodeCached:gc.alloc.rate.norm          v0-large  avgt    3     3792.001 ±      0.001    B/op
SolanaTransactionCodecBenchmark.encodeCached                           v1-typical  avgt    3       93.459 ±     51.864   ns/op
SolanaTransactionCodecBenchmark.encodeCached:gc.alloc.rate.norm        v1-typical  avgt    3     3696.001 ±      0.001    B/op
SolanaTransactionCodecBenchmark.encodeCached                             v1-large  avgt    3      391.207 ±     87.175   ns/op
SolanaTransactionCodecBenchmark.encodeCached:gc.alloc.rate.norm          v1-large  avgt    3    18216.002 ±      0.001    B/op

SolanaTransactionCodecBenchmark.encodeFresh                        legacy-typical  avgt    3      468.498 ±    175.732   ns/op
SolanaTransactionCodecBenchmark.encodeFresh:gc.alloc.rate.norm     legacy-typical  avgt    3     4648.003 ±      0.001    B/op
SolanaTransactionCodecBenchmark.encodeFresh                          legacy-large  avgt    3     1915.510 ±    272.218   ns/op
SolanaTransactionCodecBenchmark.encodeFresh:gc.alloc.rate.norm       legacy-large  avgt    3     8888.011 ±      0.001    B/op
SolanaTransactionCodecBenchmark.encodeFresh                            v0-typical  avgt    3      786.737 ±     80.009   ns/op
SolanaTransactionCodecBenchmark.encodeFresh:gc.alloc.rate.norm         v0-typical  avgt    3     5984.005 ±      0.001    B/op
SolanaTransactionCodecBenchmark.encodeFresh                              v0-large  avgt    3     2114.434 ±   1342.748   ns/op
SolanaTransactionCodecBenchmark.encodeFresh:gc.alloc.rate.norm           v0-large  avgt    3     9176.013 ±      0.008    B/op
SolanaTransactionCodecBenchmark.encodeFresh                            v1-typical  avgt    3     1988.111 ±    210.478   ns/op
SolanaTransactionCodecBenchmark.encodeFresh:gc.alloc.rate.norm         v1-typical  avgt    3     8792.012 ±      0.002    B/op
SolanaTransactionCodecBenchmark.encodeFresh                              v1-large  avgt    3     5289.656 ±    170.941   ns/op
SolanaTransactionCodecBenchmark.encodeFresh:gc.alloc.rate.norm           v1-large  avgt    3    35968.031 ±      0.001    B/op
```
