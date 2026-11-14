```
JMH version: 1.37
VM version: JDK 17.0.18, OpenJDK 64-Bit Server VM, 17.0.18+8
CPU: AMD Ryzen 5 3600 6-Core Processor
OS: Linux
```

Decoding random data of each size from a `String`: `fast` is `FastBase64`, `stdlib` the multiplatform
`kotlin.io.encoding.Base64` it replaces, `jdk` is `java.util.Base64` for comparison. Both of the others copy the
`String` into bytes before decoding, which is why they allocate about 2.3 times the output.

| Size (bytes) | fast (ns/op) | stdlib (ns/op) | jdk (ns/op) | fast (B/op) | stdlib (B/op) |
|---:|---:|---:|---:|---:|---:|
| 32 | 41.0 | 69.4 | 48.0 | 48 | 112 |
| 165 | 128.5 | 164.7 | 138.0 | 184 | 424 |
| 1544 | 1228.0 | 2429.8 | 1309.5 | 1560 | 3640 |
| 10136 | 7485.0 | 15491.3 | 8352.3 | 10152 | 23688 |
| 65536 | 62240.8 | 92704.4 | 56306.3 | 65552 | 152952 |

Tried and not kept, as no faster across sizes: lookup tables pre-shifted per position with one validity check per
quantum, the same check with the plain table, copying the characters into a char buffer in chunks to avoid
`CharSequence.get`, and a loop driven by the output index.

```
Benchmark                                      (size)  Mode  Cnt       Score      Error   Units
FastBase64Benchmark.fast                           32  avgt    5      41.002 ±    0.435   ns/op
FastBase64Benchmark.fast:gc.alloc.rate.norm        32  avgt    5      48.000 ±    0.001    B/op
FastBase64Benchmark.fast                          165  avgt    5     128.503 ±    5.417   ns/op
FastBase64Benchmark.fast:gc.alloc.rate.norm       165  avgt    5     184.001 ±    0.001    B/op
FastBase64Benchmark.fast                         1544  avgt    5    1227.964 ±    6.308   ns/op
FastBase64Benchmark.fast:gc.alloc.rate.norm      1544  avgt    5    1560.007 ±    0.001    B/op
FastBase64Benchmark.fast                        10136  avgt    5    7485.040 ±   48.340   ns/op
FastBase64Benchmark.fast:gc.alloc.rate.norm     10136  avgt    5   10152.044 ±    0.001    B/op
FastBase64Benchmark.fast                        65536  avgt    5   62240.778 ± 3066.596   ns/op
FastBase64Benchmark.fast:gc.alloc.rate.norm     65536  avgt    5   65552.372 ±    0.018    B/op
FastBase64Benchmark.jdk                            32  avgt    5      48.016 ±    0.307   ns/op
FastBase64Benchmark.jdk:gc.alloc.rate.norm         32  avgt    5     112.000 ±    0.001    B/op
FastBase64Benchmark.jdk                           165  avgt    5     138.003 ±    1.812   ns/op
FastBase64Benchmark.jdk:gc.alloc.rate.norm        165  avgt    5     424.001 ±    0.001    B/op
FastBase64Benchmark.jdk                          1544  avgt    5    1309.462 ±   15.710   ns/op
FastBase64Benchmark.jdk:gc.alloc.rate.norm       1544  avgt    5    3640.008 ±    0.001    B/op
FastBase64Benchmark.jdk                         10136  avgt    5    8352.253 ±  100.481   ns/op
FastBase64Benchmark.jdk:gc.alloc.rate.norm      10136  avgt    5   23688.050 ±    0.001    B/op
FastBase64Benchmark.jdk                         65536  avgt    5   56306.299 ±  356.487   ns/op
FastBase64Benchmark.jdk:gc.alloc.rate.norm      65536  avgt    5  152952.337 ±    0.011    B/op
FastBase64Benchmark.stdlib                         32  avgt    5      69.443 ±    0.147   ns/op
FastBase64Benchmark.stdlib:gc.alloc.rate.norm      32  avgt    5     112.000 ±    0.001    B/op
FastBase64Benchmark.stdlib                        165  avgt    5     164.722 ±    0.486   ns/op
FastBase64Benchmark.stdlib:gc.alloc.rate.norm     165  avgt    5     424.001 ±    0.001    B/op
FastBase64Benchmark.stdlib                       1544  avgt    5    2429.806 ±  110.079   ns/op
FastBase64Benchmark.stdlib:gc.alloc.rate.norm    1544  avgt    5    3640.014 ±    0.001    B/op
FastBase64Benchmark.stdlib                      10136  avgt    5   15491.328 ±  296.602   ns/op
FastBase64Benchmark.stdlib:gc.alloc.rate.norm   10136  avgt    5   23688.092 ±    0.002    B/op
FastBase64Benchmark.stdlib                      65536  avgt    5   92704.422 ± 1149.431   ns/op
FastBase64Benchmark.stdlib:gc.alloc.rate.norm   65536  avgt    5  152952.552 ±    0.007    B/op
```
