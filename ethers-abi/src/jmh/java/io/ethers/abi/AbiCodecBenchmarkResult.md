```
JMH version: 1.37
VM version: JDK 11.0.19, OpenJDK 64-Bit Server VM, 11.0.19+7
CPU: Apple M1 Max
OS: macOS 14.2.1
```

```
Benchmark                                                    Mode  Cnt     Score     Error   Units
AbiCodecBenchmark.decodeArrayOfUint256                       avgt    3   828,224 ±  74,313   ns/op
AbiCodecBenchmark.decodeArrayOfUint256:gc.alloc.rate         avgt    3  3178,061 ± 284,855  MB/sec
AbiCodecBenchmark.decodeArrayOfUint256:gc.alloc.rate.norm    avgt    3  2760,000 ±   0,001    B/op
AbiCodecBenchmark.decodeArrayOfUint256:gc.count              avgt    3   157,000            counts
AbiCodecBenchmark.decodeArrayOfUint256:gc.time               avgt    3    64,000                ms

AbiCodecBenchmark.decodeComplex                              avgt    3   934,250 ±  30,170   ns/op
AbiCodecBenchmark.decodeComplex:gc.alloc.rate                avgt    3  1919,064 ±  62,012  MB/sec
AbiCodecBenchmark.decodeComplex:gc.alloc.rate.norm           avgt    3  1880,001 ±   0,001    B/op
AbiCodecBenchmark.decodeComplex:gc.count                     avgt    3    95,000            counts
AbiCodecBenchmark.decodeComplex:gc.time                      avgt    3    39,000                ms

AbiCodecBenchmark.decodeNestedFixedArray                     avgt    3  1978,722 ±  80,019   ns/op
AbiCodecBenchmark.decodeNestedFixedArray:gc.alloc.rate       avgt    3  2082,063 ±  84,235  MB/sec
AbiCodecBenchmark.decodeNestedFixedArray:gc.alloc.rate.norm  avgt    3  4320,001 ±   0,001    B/op
AbiCodecBenchmark.decodeNestedFixedArray:gc.count            avgt    3   103,000            counts
AbiCodecBenchmark.decodeNestedFixedArray:gc.time             avgt    3    42,000                ms

AbiCodecBenchmark.encodeArrayOfUint256                       avgt    3  1056,105 ±  93,421   ns/op
AbiCodecBenchmark.encodeArrayOfUint256:gc.alloc.rate         avgt    3  2203,351 ± 194,968  MB/sec
AbiCodecBenchmark.encodeArrayOfUint256:gc.alloc.rate.norm    avgt    3  2440,001 ±   0,001    B/op
AbiCodecBenchmark.encodeArrayOfUint256:gc.count              avgt    3   109,000            counts
AbiCodecBenchmark.encodeArrayOfUint256:gc.time               avgt    3    44,000                ms

AbiCodecBenchmark.encodeComplex                              avgt    3  2247,997 ± 164,335   ns/op
AbiCodecBenchmark.encodeComplex:gc.alloc.rate                avgt    3   695,739 ±  50,838  MB/sec
AbiCodecBenchmark.encodeComplex:gc.alloc.rate.norm           avgt    3  1640,001 ±   0,001    B/op
AbiCodecBenchmark.encodeComplex:gc.count                     avgt    3    34,000            counts
AbiCodecBenchmark.encodeComplex:gc.time                      avgt    3    17,000                ms

AbiCodecBenchmark.encodeNestedFixedArray                     avgt    3   838,152 ±  59,460   ns/op
AbiCodecBenchmark.encodeNestedFixedArray:gc.alloc.rate       avgt    3  3140,384 ± 222,666  MB/sec
AbiCodecBenchmark.encodeNestedFixedArray:gc.alloc.rate.norm  avgt    3  2760,000 ±   0,001    B/op
AbiCodecBenchmark.encodeNestedFixedArray:gc.count            avgt    3   156,000            counts
AbiCodecBenchmark.encodeNestedFixedArray:gc.time             avgt    3   103,000                ms
```