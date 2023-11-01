```
JMH version: 1.36
VM version: JDK 11.0.19, OpenJDK 64-Bit Server VM, 11.0.19+7
CPU: Apple M1 Max
OS: macOS 13.3.1
```

```
Benchmark                                             Mode  Cnt      Score     Error   Units
JsonBenchmark.jacksonAnnotations                       avgt    3  20401,700 ± 252,018   ns/op
JsonBenchmark.jacksonAnnotations:·gc.alloc.rate        avgt    3   2522,699 ±  31,154  MB/sec
JsonBenchmark.jacksonAnnotations:·gc.alloc.rate.norm   avgt    3  53968,001 ±   0,001    B/op
JsonBenchmark.jacksonAnnotations:·gc.count             avgt    3     61,000            counts
JsonBenchmark.jacksonAnnotations:·gc.time              avgt    3     73,000                ms

JsonBenchmark.jacksonDeserializer                      avgt    3  16637,743 ± 514,143   ns/op
JsonBenchmark.jacksonDeserializer:·gc.alloc.rate       avgt    3   1743,426 ±  53,914  MB/sec
JsonBenchmark.jacksonDeserializer:·gc.alloc.rate.norm  avgt    3  30416,001 ±   0,001    B/op
JsonBenchmark.jacksonDeserializer:·gc.count            avgt    3     72,000            counts
JsonBenchmark.jacksonDeserializer:·gc.time             avgt    3     94,000                ms

JsonBenchmark.jacksonReflection                        avgt    3  19541,721 ± 963,919   ns/op
JsonBenchmark.jacksonReflection:·gc.alloc.rate         avgt    3   2633,730 ± 129,768  MB/sec
JsonBenchmark.jacksonReflection:·gc.alloc.rate.norm    avgt    3  53968,001 ±   0,001    B/op
JsonBenchmark.jacksonReflection:·gc.count              avgt    3     90,000            counts
JsonBenchmark.jacksonReflection:·gc.time               avgt    3    147,000                ms

JsonBenchmark.kotlinSerialization                      avgt    3  23758,079 ± 543,340   ns/op
JsonBenchmark.kotlinSerialization:·gc.alloc.rate       avgt    3   1431,253 ±  32,782  MB/sec
JsonBenchmark.kotlinSerialization:·gc.alloc.rate.norm  avgt    3  35656,001 ±   0,001    B/op
JsonBenchmark.kotlinSerialization:·gc.count            avgt    3     39,000            counts
JsonBenchmark.kotlinSerialization:·gc.time             avgt    3     54,000                ms

JsonBenchmark.kotshi                                   avgt    3  31696,999 ± 606,216   ns/op
JsonBenchmark.kotshi:·gc.alloc.rate                    avgt    3    923,306 ±  17,659  MB/sec
JsonBenchmark.kotshi:·gc.alloc.rate.norm               avgt    3  30688,001 ±   0,001    B/op
JsonBenchmark.kotshi:·gc.count                         avgt    3     27,000            counts
JsonBenchmark.kotshi:·gc.time                          avgt    3     43,000                ms
```