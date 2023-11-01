```
JMH version: 1.36
VM version: JDK 11.0.19, OpenJDK 64-Bit Server VM, 11.0.19+7
CPU: Apple M1 Max
OS: macOS 13.3.1
```

```
Benchmark                                                     Mode  Cnt     Score     Error   Units
AbiCodecBenchmark.decodeArrayOfUint256                        avgt    3   925,444 ±  12,489   ns/op
AbiCodecBenchmark.decodeArrayOfUint256:·gc.alloc.rate         avgt    3  4468,198 ±  60,199  MB/sec
AbiCodecBenchmark.decodeArrayOfUint256:·gc.alloc.rate.norm    avgt    3  4336,000 ±   0,001    B/op
AbiCodecBenchmark.decodeArrayOfUint256:·gc.count              avgt    3   127,000            counts
AbiCodecBenchmark.decodeArrayOfUint256:·gc.time               avgt    3   109,000                ms

AbiCodecBenchmark.decodeComplex                               avgt    3   828,581 ±  42,227   ns/op
AbiCodecBenchmark.decodeComplex:·gc.alloc.rate                avgt    3  2347,964 ± 119,513  MB/sec
AbiCodecBenchmark.decodeComplex:·gc.alloc.rate.norm           avgt    3  2040,000 ±   0,001    B/op
AbiCodecBenchmark.decodeComplex:·gc.count                     avgt    3   116,000            counts
AbiCodecBenchmark.decodeComplex:·gc.time                      avgt    3    77,000                ms

AbiCodecBenchmark.decodeNestedFixedArray                      avgt    3  1048,004 ±  38,278   ns/op
AbiCodecBenchmark.decodeNestedFixedArray:·gc.alloc.rate       avgt    3  5547,237 ± 202,106  MB/sec
AbiCodecBenchmark.decodeNestedFixedArray:·gc.alloc.rate.norm  avgt    3  6096,000 ±   0,001    B/op
AbiCodecBenchmark.decodeNestedFixedArray:·gc.count            avgt    3   137,000            counts
AbiCodecBenchmark.decodeNestedFixedArray:·gc.time             avgt    3   167,000                ms


AbiCodecBenchmark.encodeArrayOfUint256                        avgt    3  1764,201 ±  38,928   ns/op
AbiCodecBenchmark.encodeArrayOfUint256:·gc.alloc.rate         avgt    3  1318,968 ±  29,124  MB/sec
AbiCodecBenchmark.encodeArrayOfUint256:·gc.alloc.rate.norm    avgt    3  2440,000 ±   0,001    B/op
AbiCodecBenchmark.encodeArrayOfUint256:·gc.count              avgt    3    66,000            counts
AbiCodecBenchmark.encodeArrayOfUint256:·gc.time               avgt    3    39,000                ms

AbiCodecBenchmark.encodeComplex                               avgt    3  2171,870 ±  15,574   ns/op
AbiCodecBenchmark.encodeComplex:·gc.alloc.rate                avgt    3   720,120 ±   5,181  MB/sec
AbiCodecBenchmark.encodeComplex:·gc.alloc.rate.norm           avgt    3  1640,000 ±   0,001    B/op
AbiCodecBenchmark.encodeComplex:·gc.count                     avgt    3    35,000            counts
AbiCodecBenchmark.encodeComplex:·gc.time                      avgt    3    21,000                ms

AbiCodecBenchmark.encodeNestedFixedArray                      avgt    3   849,844 ±  43,150   ns/op
AbiCodecBenchmark.encodeNestedFixedArray:·gc.alloc.rate       avgt    3  3097,170 ± 156,976  MB/sec
AbiCodecBenchmark.encodeNestedFixedArray:·gc.alloc.rate.norm  avgt    3  2760,000 ±   0,001    B/op
AbiCodecBenchmark.encodeNestedFixedArray:·gc.count            avgt    3   127,000            counts
AbiCodecBenchmark.encodeNestedFixedArray:·gc.time             avgt    3   135,000                ms
```