package io.ethers.providers.types

import java.util.concurrent.CompletableFuture

private typealias FutureResponse<R> = CompletableFuture<RpcResponse<R>>

data class BatchResponse2<R1, R2>(
    val response1: R1,
    val response2: R2,
)

data class BatchResponse3<R1, R2, R3>(
    val response1: R1,
    val response2: R2,
    val response3: R3,
)

data class BatchResponse4<R1, R2, R3, R4>(
    val response1: R1,
    val response2: R2,
    val response3: R3,
    val response4: R4,
)

data class BatchResponse5<R1, R2, R3, R4, R5>(
    val response1: R1,
    val response2: R2,
    val response3: R3,
    val response4: R4,
    val response5: R5,
)

data class BatchResponse6<R1, R2, R3, R4, R5, R6>(
    val response1: R1,
    val response2: R2,
    val response3: R3,
    val response4: R4,
    val response5: R5,
    val response6: R6,
)

data class BatchResponse7<R1, R2, R3, R4, R5, R6, R7>(
    val response1: R1,
    val response2: R2,
    val response3: R3,
    val response4: R4,
    val response5: R5,
    val response6: R6,
    val response7: R7,
)

data class BatchResponse8<R1, R2, R3, R4, R5, R6, R7, R8>(
    val response1: R1,
    val response2: R2,
    val response3: R3,
    val response4: R4,
    val response5: R5,
    val response6: R6,
    val response7: R7,
    val response8: R8,
)

data class BatchResponse9<R1, R2, R3, R4, R5, R6, R7, R8, R9>(
    val response1: R1,
    val response2: R2,
    val response3: R3,
    val response4: R4,
    val response5: R5,
    val response6: R6,
    val response7: R7,
    val response8: R8,
    val response9: R9,
)

data class BatchResponse10<R1, R2, R3, R4, R5, R6, R7, R8, R9, R10>(
    val response1: R1,
    val response2: R2,
    val response3: R3,
    val response4: R4,
    val response5: R5,
    val response6: R6,
    val response7: R7,
    val response8: R8,
    val response9: R9,
    val response10: R10,
)

data class BatchResponse11<R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11>(
    val response1: R1,
    val response2: R2,
    val response3: R3,
    val response4: R4,
    val response5: R5,
    val response6: R6,
    val response7: R7,
    val response8: R8,
    val response9: R9,
    val response10: R10,
    val response11: R11,
)

data class BatchResponse12<R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12>(
    val response1: R1,
    val response2: R2,
    val response3: R3,
    val response4: R4,
    val response5: R5,
    val response6: R6,
    val response7: R7,
    val response8: R8,
    val response9: R9,
    val response10: R10,
    val response11: R11,
    val response12: R12,
)

fun <R1, R2> batchRequest(
    r1: RpcRequest<R1>,
    r2: RpcRequest<R2>,
): BatchResponse2<FutureResponse<R1>, FutureResponse<R2>> {
    val batch = BatchRpcRequest(2)
    val f1 = r1.batch(batch)
    val f2 = r2.batch(batch)
    batch.sendAsync()

    return BatchResponse2(f1, f2)
}

fun <R1, R2> BatchResponse2<FutureResponse<R1>, FutureResponse<R2>>.await() =
    BatchResponse2(response1.join(), response2.join())

fun <R1, R2> BatchResponse2<RpcResponse<R1>, RpcResponse<R2>>.resultOrThrow() =
    BatchResponse2(response1.resultOrThrow(), response2.resultOrThrow())

fun <R1, R2, R3> batchRequest(
    r1: RpcRequest<R1>,
    r2: RpcRequest<R2>,
    r3: RpcRequest<R3>,
): BatchResponse3<FutureResponse<R1>, FutureResponse<R2>, FutureResponse<R3>> {
    val batch = BatchRpcRequest(3)
    val f1 = r1.batch(batch)
    val f2 = r2.batch(batch)
    val f3 = r3.batch(batch)
    batch.sendAsync()

    return BatchResponse3(f1, f2, f3)
}

fun <R1, R2, R3> BatchResponse3<FutureResponse<R1>, FutureResponse<R2>, FutureResponse<R3>>.await() =
    BatchResponse3(response1.join(), response2.join(), response3.join())

fun <R1, R2, R3> BatchResponse3<RpcResponse<R1>, RpcResponse<R2>, RpcResponse<R3>>.resultOrThrow() =
    BatchResponse3(response1.resultOrThrow(), response2.resultOrThrow(), response3.resultOrThrow())

fun <R1, R2, R3, R4> batchRequest(
    r1: RpcRequest<R1>,
    r2: RpcRequest<R2>,
    r3: RpcRequest<R3>,
    r4: RpcRequest<R4>,
): BatchResponse4<FutureResponse<R1>, FutureResponse<R2>, FutureResponse<R3>, FutureResponse<R4>> {
    val batch = BatchRpcRequest(4)
    val f1 = r1.batch(batch)
    val f2 = r2.batch(batch)
    val f3 = r3.batch(batch)
    val f4 = r4.batch(batch)
    batch.sendAsync()

    return BatchResponse4(f1, f2, f3, f4)
}

fun <R1, R2, R3, R4> BatchResponse4<FutureResponse<R1>, FutureResponse<R2>, FutureResponse<R3>, FutureResponse<R4>>.await() =
    BatchResponse4(response1.join(), response2.join(), response3.join(), response4.join())

fun <R1, R2, R3, R4> BatchResponse4<RpcResponse<R1>, RpcResponse<R2>, RpcResponse<R3>, RpcResponse<R4>>.resultOrThrow() =
    BatchResponse4(
        response1.resultOrThrow(),
        response2.resultOrThrow(),
        response3.resultOrThrow(),
        response4.resultOrThrow(),
    )

fun <R1, R2, R3, R4, R5> batchRequest(
    r1: RpcRequest<R1>,
    r2: RpcRequest<R2>,
    r3: RpcRequest<R3>,
    r4: RpcRequest<R4>,
    r5: RpcRequest<R5>,
): BatchResponse5<FutureResponse<R1>, FutureResponse<R2>, FutureResponse<R3>, FutureResponse<R4>, FutureResponse<R5>> {
    val batch = BatchRpcRequest(5)
    val f1 = r1.batch(batch)
    val f2 = r2.batch(batch)
    val f3 = r3.batch(batch)
    val f4 = r4.batch(batch)
    val f5 = r5.batch(batch)
    batch.sendAsync()

    return BatchResponse5(f1, f2, f3, f4, f5)
}

fun <R1, R2, R3, R4, R5> BatchResponse5<FutureResponse<R1>, FutureResponse<R2>, FutureResponse<R3>, FutureResponse<R4>, FutureResponse<R5>>.await() =
    BatchResponse5(response1.join(), response2.join(), response3.join(), response4.join(), response5.join())

fun <R1, R2, R3, R4, R5> BatchResponse5<RpcResponse<R1>, RpcResponse<R2>, RpcResponse<R3>, RpcResponse<R4>, RpcResponse<R5>>.resultOrThrow() =
    BatchResponse5(
        response1.resultOrThrow(),
        response2.resultOrThrow(),
        response3.resultOrThrow(),
        response4.resultOrThrow(),
        response5.resultOrThrow(),
    )

fun <R1, R2, R3, R4, R5, R6> batchRequest(
    r1: RpcRequest<R1>,
    r2: RpcRequest<R2>,
    r3: RpcRequest<R3>,
    r4: RpcRequest<R4>,
    r5: RpcRequest<R5>,
    r6: RpcRequest<R6>,
): BatchResponse6<FutureResponse<R1>, FutureResponse<R2>, FutureResponse<R3>, FutureResponse<R4>, FutureResponse<R5>, FutureResponse<R6>> {
    val batch = BatchRpcRequest(6)
    val f1 = r1.batch(batch)
    val f2 = r2.batch(batch)
    val f3 = r3.batch(batch)
    val f4 = r4.batch(batch)
    val f5 = r5.batch(batch)
    val f6 = r6.batch(batch)
    batch.sendAsync()

    return BatchResponse6(f1, f2, f3, f4, f5, f6)
}

fun <R1, R2, R3, R4, R5, R6> BatchResponse6<FutureResponse<R1>, FutureResponse<R2>, FutureResponse<R3>, FutureResponse<R4>, FutureResponse<R5>, FutureResponse<R6>>.await() =
    BatchResponse6(
        response1.join(),
        response2.join(),
        response3.join(),
        response4.join(),
        response5.join(),
        response6.join(),
    )

fun <R1, R2, R3, R4, R5, R6> BatchResponse6<RpcResponse<R1>, RpcResponse<R2>, RpcResponse<R3>, RpcResponse<R4>, RpcResponse<R5>, RpcResponse<R6>>.resultOrThrow() =
    BatchResponse6(
        response1.resultOrThrow(),
        response2.resultOrThrow(),
        response3.resultOrThrow(),
        response4.resultOrThrow(),
        response5.resultOrThrow(),
        response6.resultOrThrow(),
    )

fun <R1, R2, R3, R4, R5, R6, R7> batchRequest(
    r1: RpcRequest<R1>,
    r2: RpcRequest<R2>,
    r3: RpcRequest<R3>,
    r4: RpcRequest<R4>,
    r5: RpcRequest<R5>,
    r6: RpcRequest<R6>,
    r7: RpcRequest<R7>,
): BatchResponse7<FutureResponse<R1>, FutureResponse<R2>, FutureResponse<R3>, FutureResponse<R4>, FutureResponse<R5>, FutureResponse<R6>, FutureResponse<R7>> {
    val batch = BatchRpcRequest(7)
    val f1 = r1.batch(batch)
    val f2 = r2.batch(batch)
    val f3 = r3.batch(batch)
    val f4 = r4.batch(batch)
    val f5 = r5.batch(batch)
    val f6 = r6.batch(batch)
    val f7 = r7.batch(batch)
    batch.sendAsync()

    return BatchResponse7(f1, f2, f3, f4, f5, f6, f7)
}

fun <R1, R2, R3, R4, R5, R6, R7> BatchResponse7<FutureResponse<R1>, FutureResponse<R2>, FutureResponse<R3>, FutureResponse<R4>, FutureResponse<R5>, FutureResponse<R6>, FutureResponse<R7>>.await() =
    BatchResponse7(
        response1.join(),
        response2.join(),
        response3.join(),
        response4.join(),
        response5.join(),
        response6.join(),
        response7.join(),
    )

fun <R1, R2, R3, R4, R5, R6, R7> BatchResponse7<RpcResponse<R1>, RpcResponse<R2>, RpcResponse<R3>, RpcResponse<R4>, RpcResponse<R5>, RpcResponse<R6>, RpcResponse<R7>>.resultOrThrow() =
    BatchResponse7(
        response1.resultOrThrow(),
        response2.resultOrThrow(),
        response3.resultOrThrow(),
        response4.resultOrThrow(),
        response5.resultOrThrow(),
        response6.resultOrThrow(),
        response7.resultOrThrow(),
    )

fun <R1, R2, R3, R4, R5, R6, R7, R8> batchRequest(
    r1: RpcRequest<R1>,
    r2: RpcRequest<R2>,
    r3: RpcRequest<R3>,
    r4: RpcRequest<R4>,
    r5: RpcRequest<R5>,
    r6: RpcRequest<R6>,
    r7: RpcRequest<R7>,
    r8: RpcRequest<R8>,
): BatchResponse8<FutureResponse<R1>, FutureResponse<R2>, FutureResponse<R3>, FutureResponse<R4>, FutureResponse<R5>, FutureResponse<R6>, FutureResponse<R7>, FutureResponse<R8>> {
    val batch = BatchRpcRequest(8)
    val f1 = r1.batch(batch)
    val f2 = r2.batch(batch)
    val f3 = r3.batch(batch)
    val f4 = r4.batch(batch)
    val f5 = r5.batch(batch)
    val f6 = r6.batch(batch)
    val f7 = r7.batch(batch)
    val f8 = r8.batch(batch)
    batch.sendAsync()

    return BatchResponse8(f1, f2, f3, f4, f5, f6, f7, f8)
}

fun <R1, R2, R3, R4, R5, R6, R7, R8> BatchResponse8<FutureResponse<R1>, FutureResponse<R2>, FutureResponse<R3>, FutureResponse<R4>, FutureResponse<R5>, FutureResponse<R6>, FutureResponse<R7>, FutureResponse<R8>>.await() =
    BatchResponse8(
        response1.join(),
        response2.join(),
        response3.join(),
        response4.join(),
        response5.join(),
        response6.join(),
        response7.join(),
        response8.join(),
    )

fun <R1, R2, R3, R4, R5, R6, R7, R8> BatchResponse8<RpcResponse<R1>, RpcResponse<R2>, RpcResponse<R3>, RpcResponse<R4>, RpcResponse<R5>, RpcResponse<R6>, RpcResponse<R7>, RpcResponse<R8>>.resultOrThrow() =
    BatchResponse8(
        response1.resultOrThrow(),
        response2.resultOrThrow(),
        response3.resultOrThrow(),
        response4.resultOrThrow(),
        response5.resultOrThrow(),
        response6.resultOrThrow(),
        response7.resultOrThrow(),
        response8.resultOrThrow(),
    )

fun <R1, R2, R3, R4, R5, R6, R7, R8, R9> batchRequest(
    r1: RpcRequest<R1>,
    r2: RpcRequest<R2>,
    r3: RpcRequest<R3>,
    r4: RpcRequest<R4>,
    r5: RpcRequest<R5>,
    r6: RpcRequest<R6>,
    r7: RpcRequest<R7>,
    r8: RpcRequest<R8>,
    r9: RpcRequest<R9>,
): BatchResponse9<FutureResponse<R1>, FutureResponse<R2>, FutureResponse<R3>, FutureResponse<R4>, FutureResponse<R5>, FutureResponse<R6>, FutureResponse<R7>, FutureResponse<R8>, FutureResponse<R9>> {
    val batch = BatchRpcRequest(9)
    val f1 = r1.batch(batch)
    val f2 = r2.batch(batch)
    val f3 = r3.batch(batch)
    val f4 = r4.batch(batch)
    val f5 = r5.batch(batch)
    val f6 = r6.batch(batch)
    val f7 = r7.batch(batch)
    val f8 = r8.batch(batch)
    val f9 = r9.batch(batch)
    batch.sendAsync()

    return BatchResponse9(f1, f2, f3, f4, f5, f6, f7, f8, f9)
}

fun <R1, R2, R3, R4, R5, R6, R7, R8, R9> BatchResponse9<FutureResponse<R1>, FutureResponse<R2>, FutureResponse<R3>, FutureResponse<R4>, FutureResponse<R5>, FutureResponse<R6>, FutureResponse<R7>, FutureResponse<R8>, FutureResponse<R9>>.await() =
    BatchResponse9(
        response1.join(),
        response2.join(),
        response3.join(),
        response4.join(),
        response5.join(),
        response6.join(),
        response7.join(),
        response8.join(),
        response9.join(),
    )

fun <R1, R2, R3, R4, R5, R6, R7, R8, R9> BatchResponse9<RpcResponse<R1>, RpcResponse<R2>, RpcResponse<R3>, RpcResponse<R4>, RpcResponse<R5>, RpcResponse<R6>, RpcResponse<R7>, RpcResponse<R8>, RpcResponse<R9>>.resultOrThrow() =
    BatchResponse9(
        response1.resultOrThrow(),
        response2.resultOrThrow(),
        response3.resultOrThrow(),
        response4.resultOrThrow(),
        response5.resultOrThrow(),
        response6.resultOrThrow(),
        response7.resultOrThrow(),
        response8.resultOrThrow(),
        response9.resultOrThrow(),
    )

fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, R10> batchRequest(
    r1: RpcRequest<R1>,
    r2: RpcRequest<R2>,
    r3: RpcRequest<R3>,
    r4: RpcRequest<R4>,
    r5: RpcRequest<R5>,
    r6: RpcRequest<R6>,
    r7: RpcRequest<R7>,
    r8: RpcRequest<R8>,
    r9: RpcRequest<R9>,
    r10: RpcRequest<R10>,
): BatchResponse10<FutureResponse<R1>, FutureResponse<R2>, FutureResponse<R3>, FutureResponse<R4>, FutureResponse<R5>, FutureResponse<R6>, FutureResponse<R7>, FutureResponse<R8>, FutureResponse<R9>, FutureResponse<R10>> {
    val batch = BatchRpcRequest(10)
    val f1 = r1.batch(batch)
    val f2 = r2.batch(batch)
    val f3 = r3.batch(batch)
    val f4 = r4.batch(batch)
    val f5 = r5.batch(batch)
    val f6 = r6.batch(batch)
    val f7 = r7.batch(batch)
    val f8 = r8.batch(batch)
    val f9 = r9.batch(batch)
    val f10 = r10.batch(batch)
    batch.sendAsync()

    return BatchResponse10(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10)
}

fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, R10> BatchResponse10<FutureResponse<R1>, FutureResponse<R2>, FutureResponse<R3>, FutureResponse<R4>, FutureResponse<R5>, FutureResponse<R6>, FutureResponse<R7>, FutureResponse<R8>, FutureResponse<R9>, FutureResponse<R10>>.await() =
    BatchResponse10(
        response1.join(),
        response2.join(),
        response3.join(),
        response4.join(),
        response5.join(),
        response6.join(),
        response7.join(),
        response8.join(),
        response9.join(),
        response10.join(),
    )

fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, R10> BatchResponse10<RpcResponse<R1>, RpcResponse<R2>, RpcResponse<R3>, RpcResponse<R4>, RpcResponse<R5>, RpcResponse<R6>, RpcResponse<R7>, RpcResponse<R8>, RpcResponse<R9>, RpcResponse<R10>>.resultOrThrow() =
    BatchResponse10(
        response1.resultOrThrow(),
        response2.resultOrThrow(),
        response3.resultOrThrow(),
        response4.resultOrThrow(),
        response5.resultOrThrow(),
        response6.resultOrThrow(),
        response7.resultOrThrow(),
        response8.resultOrThrow(),
        response9.resultOrThrow(),
        response10.resultOrThrow(),
    )

fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11> batchRequest(
    r1: RpcRequest<R1>,
    r2: RpcRequest<R2>,
    r3: RpcRequest<R3>,
    r4: RpcRequest<R4>,
    r5: RpcRequest<R5>,
    r6: RpcRequest<R6>,
    r7: RpcRequest<R7>,
    r8: RpcRequest<R8>,
    r9: RpcRequest<R9>,
    r10: RpcRequest<R10>,
    r11: RpcRequest<R11>,
): BatchResponse11<FutureResponse<R1>, FutureResponse<R2>, FutureResponse<R3>, FutureResponse<R4>, FutureResponse<R5>, FutureResponse<R6>, FutureResponse<R7>, FutureResponse<R8>, FutureResponse<R9>, FutureResponse<R10>, FutureResponse<R11>> {
    val batch = BatchRpcRequest(11)
    val f1 = r1.batch(batch)
    val f2 = r2.batch(batch)
    val f3 = r3.batch(batch)
    val f4 = r4.batch(batch)
    val f5 = r5.batch(batch)
    val f6 = r6.batch(batch)
    val f7 = r7.batch(batch)
    val f8 = r8.batch(batch)
    val f9 = r9.batch(batch)
    val f10 = r10.batch(batch)
    val f11 = r11.batch(batch)
    batch.sendAsync()

    return BatchResponse11(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11)
}

fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11> BatchResponse11<FutureResponse<R1>, FutureResponse<R2>, FutureResponse<R3>, FutureResponse<R4>, FutureResponse<R5>, FutureResponse<R6>, FutureResponse<R7>, FutureResponse<R8>, FutureResponse<R9>, FutureResponse<R10>, FutureResponse<R11>>.await() =
    BatchResponse11(
        response1.join(),
        response2.join(),
        response3.join(),
        response4.join(),
        response5.join(),
        response6.join(),
        response7.join(),
        response8.join(),
        response9.join(),
        response10.join(),
        response11.join(),
    )

fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11> BatchResponse11<RpcResponse<R1>, RpcResponse<R2>, RpcResponse<R3>, RpcResponse<R4>, RpcResponse<R5>, RpcResponse<R6>, RpcResponse<R7>, RpcResponse<R8>, RpcResponse<R9>, RpcResponse<R10>, RpcResponse<R11>>.resultOrThrow() =
    BatchResponse11(
        response1.resultOrThrow(),
        response2.resultOrThrow(),
        response3.resultOrThrow(),
        response4.resultOrThrow(),
        response5.resultOrThrow(),
        response6.resultOrThrow(),
        response7.resultOrThrow(),
        response8.resultOrThrow(),
        response9.resultOrThrow(),
        response10.resultOrThrow(),
        response11.resultOrThrow(),
    )

fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12> batchRequest(
    r1: RpcRequest<R1>,
    r2: RpcRequest<R2>,
    r3: RpcRequest<R3>,
    r4: RpcRequest<R4>,
    r5: RpcRequest<R5>,
    r6: RpcRequest<R6>,
    r7: RpcRequest<R7>,
    r8: RpcRequest<R8>,
    r9: RpcRequest<R9>,
    r10: RpcRequest<R10>,
    r11: RpcRequest<R11>,
    r12: RpcRequest<R12>,
): BatchResponse12<FutureResponse<R1>, FutureResponse<R2>, FutureResponse<R3>, FutureResponse<R4>, FutureResponse<R5>, FutureResponse<R6>, FutureResponse<R7>, FutureResponse<R8>, FutureResponse<R9>, FutureResponse<R10>, FutureResponse<R11>, FutureResponse<R12>> {
    val batch = BatchRpcRequest(12)
    val f1 = r1.batch(batch)
    val f2 = r2.batch(batch)
    val f3 = r3.batch(batch)
    val f4 = r4.batch(batch)
    val f5 = r5.batch(batch)
    val f6 = r6.batch(batch)
    val f7 = r7.batch(batch)
    val f8 = r8.batch(batch)
    val f9 = r9.batch(batch)
    val f10 = r10.batch(batch)
    val f11 = r11.batch(batch)
    val f12 = r12.batch(batch)
    batch.sendAsync()

    return BatchResponse12(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12)
}

fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12> BatchResponse12<FutureResponse<R1>, FutureResponse<R2>, FutureResponse<R3>, FutureResponse<R4>, FutureResponse<R5>, FutureResponse<R6>, FutureResponse<R7>, FutureResponse<R8>, FutureResponse<R9>, FutureResponse<R10>, FutureResponse<R11>, FutureResponse<R12>>.await() =
    BatchResponse12(
        response1.join(),
        response2.join(),
        response3.join(),
        response4.join(),
        response5.join(),
        response6.join(),
        response7.join(),
        response8.join(),
        response9.join(),
        response10.join(),
        response11.join(),
        response12.join(),
    )

fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12> BatchResponse12<RpcResponse<R1>, RpcResponse<R2>, RpcResponse<R3>, RpcResponse<R4>, RpcResponse<R5>, RpcResponse<R6>, RpcResponse<R7>, RpcResponse<R8>, RpcResponse<R9>, RpcResponse<R10>, RpcResponse<R11>, RpcResponse<R12>>.resultOrThrow() =
    BatchResponse12(
        response1.resultOrThrow(),
        response2.resultOrThrow(),
        response3.resultOrThrow(),
        response4.resultOrThrow(),
        response5.resultOrThrow(),
        response6.resultOrThrow(),
        response7.resultOrThrow(),
        response8.resultOrThrow(),
        response9.resultOrThrow(),
        response10.resultOrThrow(),
        response11.resultOrThrow(),
        response12.resultOrThrow(),
    )
