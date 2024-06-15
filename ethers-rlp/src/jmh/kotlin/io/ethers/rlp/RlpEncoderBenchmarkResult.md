```
JMH version: 1.37
VM version: JDK 11.0.19, OpenJDK 64-Bit Server VM, 11.0.19+7
CPU: Apple M1 Max
OS: macOS 14.5
```

```
Benchmark                                                 Mode  Cnt     Score     Error   Units
RlpEncoderBenchmark.encodeDynamicSize                     avgt    3   331,136 ±  25,403   ns/op
RlpEncoderBenchmark.encodeDynamicSize:gc.alloc.rate       avgt    3  9699,805 ± 746,057  MB/sec
RlpEncoderBenchmark.encodeDynamicSize:gc.alloc.rate.norm  avgt    3  3368,000 ±   0,001    B/op
RlpEncoderBenchmark.encodeDynamicSize:gc.count            avgt    3   230,000            counts
RlpEncoderBenchmark.encodeDynamicSize:gc.time             avgt    3   142,000                ms

RlpEncoderBenchmark.encodeExactSize                       avgt    3   258,963 ±   1,655   ns/op
RlpEncoderBenchmark.encodeExactSize:gc.alloc.rate         avgt    3  5155,654 ±  32,931  MB/sec
RlpEncoderBenchmark.encodeExactSize:gc.alloc.rate.norm    avgt    3  1400,000 ±   0,001    B/op
RlpEncoderBenchmark.encodeExactSize:gc.count              avgt    3   146,000            counts
RlpEncoderBenchmark.encodeExactSize:gc.time               avgt    3    70,000                ms
```