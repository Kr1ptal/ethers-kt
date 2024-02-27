```
JMH version: 1.37
VM version: JDK 11.0.19, OpenJDK 64-Bit Server VM, 11.0.19+7
CPU: Apple M1 Max
OS: macOS 14.2.1
```

```
Benchmark                                                    Mode  Cnt     Score     Error   Units
AbiCodecBenchmark.decodeArrayOfUint256                       avgt    3  1019,999 ±  29,203   ns/op
AbiCodecBenchmark.decodeArrayOfUint256:gc.alloc.rate         avgt    3  2535,615 ±  72,612  MB/sec
AbiCodecBenchmark.decodeArrayOfUint256:gc.alloc.rate.norm    avgt    3  2712,000 ±   0,001    B/op
AbiCodecBenchmark.decodeArrayOfUint256:gc.count              avgt    3   126,000            counts
AbiCodecBenchmark.decodeArrayOfUint256:gc.time               avgt    3    57,000                ms

AbiCodecBenchmark.decodeComplex                              avgt    3   814,171 ± 124,935   ns/op
AbiCodecBenchmark.decodeComplex:gc.alloc.rate                avgt    3  1827,351 ± 279,250  MB/sec
AbiCodecBenchmark.decodeComplex:gc.alloc.rate.norm           avgt    3  1560,000 ±   0,001    B/op
AbiCodecBenchmark.decodeComplex:gc.count                     avgt    3    90,000            counts
AbiCodecBenchmark.decodeComplex:gc.time                      avgt    3    44,000                ms

AbiCodecBenchmark.decodeNestedFixedArray                     avgt    3  1362,670 ±  77,406   ns/op
AbiCodecBenchmark.decodeNestedFixedArray:gc.alloc.rate       avgt    3  2653,830 ± 150,421  MB/sec
AbiCodecBenchmark.decodeNestedFixedArray:gc.alloc.rate.norm  avgt    3  3792,000 ±   0,001    B/op
AbiCodecBenchmark.decodeNestedFixedArray:gc.count            avgt    3   132,000            counts
AbiCodecBenchmark.decodeNestedFixedArray:gc.time             avgt    3    48,000                ms

AbiCodecBenchmark.encodeArrayOfUint256                       avgt    3  1182,551 ±  25,325   ns/op
AbiCodecBenchmark.encodeArrayOfUint256:gc.alloc.rate         avgt    3  1967,720 ±  42,167  MB/sec
AbiCodecBenchmark.encodeArrayOfUint256:gc.alloc.rate.norm    avgt    3  2440,000 ±   0,001    B/op
AbiCodecBenchmark.encodeArrayOfUint256:gc.count              avgt    3    97,000            counts
AbiCodecBenchmark.encodeArrayOfUint256:gc.time               avgt    3    52,000                ms

AbiCodecBenchmark.encodeComplex                              avgt    3  2650,098 ±  18,780   ns/op
AbiCodecBenchmark.encodeComplex:gc.alloc.rate                avgt    3   590,161 ±   4,275  MB/sec
AbiCodecBenchmark.encodeComplex:gc.alloc.rate.norm           avgt    3  1640,000 ±   0,001    B/op
AbiCodecBenchmark.encodeComplex:gc.count                     avgt    3    30,000            counts
AbiCodecBenchmark.encodeComplex:gc.time                      avgt    3    24,000                ms

AbiCodecBenchmark.encodeNestedFixedArray                     avgt    3   929,830 ±  19,009   ns/op
AbiCodecBenchmark.encodeNestedFixedArray:gc.alloc.rate       avgt    3  2830,722 ±  57,932  MB/sec
AbiCodecBenchmark.encodeNestedFixedArray:gc.alloc.rate.norm  avgt    3  2760,000 ±   0,001    B/op
AbiCodecBenchmark.encodeNestedFixedArray:gc.count            avgt    3   139,000            counts
AbiCodecBenchmark.encodeNestedFixedArray:gc.time             avgt    3    87,000                ms
```