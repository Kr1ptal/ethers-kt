```
JMH version: 1.37
VM version: JDK 11.0.19, OpenJDK 64-Bit Server VM, 11.0.19+7
CPU: Apple M1 Max
OS: macOS 14.0
```

```
Benchmark                                                  Mode  Cnt      Score      Error   Units
FastHexBenchmark.decodeHexString                           avgt    3   3431,493 ± 2023,960   ns/op
FastHexBenchmark.decodeHexString:gc.alloc.rate             avgt    3   1119,095 ±  652,398  MB/sec
FastHexBenchmark.decodeHexString:gc.alloc.rate.norm        avgt    3   4024,000 ±    0,001    B/op
FastHexBenchmark.decodeHexString:gc.count                  avgt    3     56,000             counts
FastHexBenchmark.decodeHexString:gc.time                   avgt    3     33,000                 ms

FastHexBenchmark.encodeWithPrefixBytes                     avgt    3   2368,599 ±  244,010   ns/op
FastHexBenchmark.encodeWithPrefixBytes:gc.alloc.rate       avgt    3   6477,594 ±  666,412  MB/sec
FastHexBenchmark.encodeWithPrefixBytes:gc.alloc.rate.norm  avgt    3  16088,000 ±    0,001    B/op
FastHexBenchmark.encodeWithPrefixBytes:gc.count            avgt    3    220,000             counts
FastHexBenchmark.encodeWithPrefixBytes:gc.time             avgt    3    159,000                 ms
```
