package io.ethers.providers.types

import io.ethers.core.Result

private typealias PendingResponse<R, E> = BatchRpcResponse<Result<R, E>>

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

fun <R1, R2, E1 : Result.Error, E2 : Result.Error> batchRequest(
    r1: RpcRequest<R1, E1>,
    r2: RpcRequest<R2, E2>,
): BatchResponse2<PendingResponse<R1, E1>, PendingResponse<R2, E2>> {
    val batch = BatchRpcRequest(2)
    val f1 = r1.batch(batch)
    val f2 = r2.batch(batch)
    batch.sendDeferred()

    return BatchResponse2(f1, f2)
}

suspend fun <R1, R2, E1 : Result.Error, E2 : Result.Error> BatchResponse2<PendingResponse<R1, E1>, PendingResponse<R2, E2>>.await() = BatchResponse2(response1.await(), response2.await())

fun <R1, R2, E1 : Result.Error, E2 : Result.Error> BatchResponse2<Result<R1, E1>, Result<R2, E2>>.unwrap() = BatchResponse2(response1.unwrap(), response2.unwrap())

fun <R1, R2, R3, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error> batchRequest(
    r1: RpcRequest<R1, E1>,
    r2: RpcRequest<R2, E2>,
    r3: RpcRequest<R3, E3>,
): BatchResponse3<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>> {
    val batch = BatchRpcRequest(3)
    val f1 = r1.batch(batch)
    val f2 = r2.batch(batch)
    val f3 = r3.batch(batch)
    batch.sendDeferred()

    return BatchResponse3(f1, f2, f3)
}

suspend fun <R1, R2, R3, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error> BatchResponse3<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>>.await() = BatchResponse3(response1.await(), response2.await(), response3.await())

fun <R1, R2, R3, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error> BatchResponse3<Result<R1, E1>, Result<R2, E2>, Result<R3, E3>>.unwrap() = BatchResponse3(response1.unwrap(), response2.unwrap(), response3.unwrap())

fun <R1, R2, R3, R4, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error> batchRequest(
    r1: RpcRequest<R1, E1>,
    r2: RpcRequest<R2, E2>,
    r3: RpcRequest<R3, E3>,
    r4: RpcRequest<R4, E4>,
): BatchResponse4<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>> {
    val batch = BatchRpcRequest(4)
    val f1 = r1.batch(batch)
    val f2 = r2.batch(batch)
    val f3 = r3.batch(batch)
    val f4 = r4.batch(batch)
    batch.sendDeferred()

    return BatchResponse4(f1, f2, f3, f4)
}

suspend fun <R1, R2, R3, R4, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error> BatchResponse4<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>>.await() = BatchResponse4(response1.await(), response2.await(), response3.await(), response4.await())

fun <R1, R2, R3, R4, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error> BatchResponse4<Result<R1, E1>, Result<R2, E2>, Result<R3, E3>, Result<R4, E4>>.unwrap() = BatchResponse4(
    response1.unwrap(),
    response2.unwrap(),
    response3.unwrap(),
    response4.unwrap(),
)

fun <R1, R2, R3, R4, R5, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error> batchRequest(
    r1: RpcRequest<R1, E1>,
    r2: RpcRequest<R2, E2>,
    r3: RpcRequest<R3, E3>,
    r4: RpcRequest<R4, E4>,
    r5: RpcRequest<R5, E5>,
): BatchResponse5<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>> {
    val batch = BatchRpcRequest(5)
    val f1 = r1.batch(batch)
    val f2 = r2.batch(batch)
    val f3 = r3.batch(batch)
    val f4 = r4.batch(batch)
    val f5 = r5.batch(batch)
    batch.sendDeferred()

    return BatchResponse5(f1, f2, f3, f4, f5)
}

suspend fun <R1, R2, R3, R4, R5, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error> BatchResponse5<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>>.await() = BatchResponse5(response1.await(), response2.await(), response3.await(), response4.await(), response5.await())

fun <R1, R2, R3, R4, R5, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error> BatchResponse5<Result<R1, E1>, Result<R2, E2>, Result<R3, E3>, Result<R4, E4>, Result<R5, E5>>.unwrap() = BatchResponse5(
    response1.unwrap(),
    response2.unwrap(),
    response3.unwrap(),
    response4.unwrap(),
    response5.unwrap(),
)

fun <R1, R2, R3, R4, R5, R6, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error, E6 : Result.Error> batchRequest(
    r1: RpcRequest<R1, E1>,
    r2: RpcRequest<R2, E2>,
    r3: RpcRequest<R3, E3>,
    r4: RpcRequest<R4, E4>,
    r5: RpcRequest<R5, E5>,
    r6: RpcRequest<R6, E6>,
): BatchResponse6<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>, PendingResponse<R6, E6>> {
    val batch = BatchRpcRequest(6)
    val f1 = r1.batch(batch)
    val f2 = r2.batch(batch)
    val f3 = r3.batch(batch)
    val f4 = r4.batch(batch)
    val f5 = r5.batch(batch)
    val f6 = r6.batch(batch)
    batch.sendDeferred()

    return BatchResponse6(f1, f2, f3, f4, f5, f6)
}

suspend fun <R1, R2, R3, R4, R5, R6, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error, E6 : Result.Error> BatchResponse6<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>, PendingResponse<R6, E6>>.await() = BatchResponse6(
    response1.await(),
    response2.await(),
    response3.await(),
    response4.await(),
    response5.await(),
    response6.await(),
)

fun <R1, R2, R3, R4, R5, R6, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error, E6 : Result.Error> BatchResponse6<Result<R1, E1>, Result<R2, E2>, Result<R3, E3>, Result<R4, E4>, Result<R5, E5>, Result<R6, E6>>.unwrap() = BatchResponse6(
    response1.unwrap(),
    response2.unwrap(),
    response3.unwrap(),
    response4.unwrap(),
    response5.unwrap(),
    response6.unwrap(),
)

fun <R1, R2, R3, R4, R5, R6, R7, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error, E6 : Result.Error, E7 : Result.Error> batchRequest(
    r1: RpcRequest<R1, E1>,
    r2: RpcRequest<R2, E2>,
    r3: RpcRequest<R3, E3>,
    r4: RpcRequest<R4, E4>,
    r5: RpcRequest<R5, E5>,
    r6: RpcRequest<R6, E6>,
    r7: RpcRequest<R7, E7>,
): BatchResponse7<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>, PendingResponse<R6, E6>, PendingResponse<R7, E7>> {
    val batch = BatchRpcRequest(7)
    val f1 = r1.batch(batch)
    val f2 = r2.batch(batch)
    val f3 = r3.batch(batch)
    val f4 = r4.batch(batch)
    val f5 = r5.batch(batch)
    val f6 = r6.batch(batch)
    val f7 = r7.batch(batch)
    batch.sendDeferred()

    return BatchResponse7(f1, f2, f3, f4, f5, f6, f7)
}

suspend fun <R1, R2, R3, R4, R5, R6, R7, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error, E6 : Result.Error, E7 : Result.Error> BatchResponse7<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>, PendingResponse<R6, E6>, PendingResponse<R7, E7>>.await() = BatchResponse7(
    response1.await(),
    response2.await(),
    response3.await(),
    response4.await(),
    response5.await(),
    response6.await(),
    response7.await(),
)

fun <R1, R2, R3, R4, R5, R6, R7, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error, E6 : Result.Error, E7 : Result.Error> BatchResponse7<Result<R1, E1>, Result<R2, E2>, Result<R3, E3>, Result<R4, E4>, Result<R5, E5>, Result<R6, E6>, Result<R7, E7>>.unwrap() = BatchResponse7(
    response1.unwrap(),
    response2.unwrap(),
    response3.unwrap(),
    response4.unwrap(),
    response5.unwrap(),
    response6.unwrap(),
    response7.unwrap(),
)

fun <R1, R2, R3, R4, R5, R6, R7, R8, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error, E6 : Result.Error, E7 : Result.Error, E8 : Result.Error> batchRequest(
    r1: RpcRequest<R1, E1>,
    r2: RpcRequest<R2, E2>,
    r3: RpcRequest<R3, E3>,
    r4: RpcRequest<R4, E4>,
    r5: RpcRequest<R5, E5>,
    r6: RpcRequest<R6, E6>,
    r7: RpcRequest<R7, E7>,
    r8: RpcRequest<R8, E8>,
): BatchResponse8<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>, PendingResponse<R6, E6>, PendingResponse<R7, E7>, PendingResponse<R8, E8>> {
    val batch = BatchRpcRequest(8)
    val f1 = r1.batch(batch)
    val f2 = r2.batch(batch)
    val f3 = r3.batch(batch)
    val f4 = r4.batch(batch)
    val f5 = r5.batch(batch)
    val f6 = r6.batch(batch)
    val f7 = r7.batch(batch)
    val f8 = r8.batch(batch)
    batch.sendDeferred()

    return BatchResponse8(f1, f2, f3, f4, f5, f6, f7, f8)
}

suspend fun <R1, R2, R3, R4, R5, R6, R7, R8, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error, E6 : Result.Error, E7 : Result.Error, E8 : Result.Error> BatchResponse8<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>, PendingResponse<R6, E6>, PendingResponse<R7, E7>, PendingResponse<R8, E8>>.await() = BatchResponse8(
    response1.await(),
    response2.await(),
    response3.await(),
    response4.await(),
    response5.await(),
    response6.await(),
    response7.await(),
    response8.await(),
)

fun <R1, R2, R3, R4, R5, R6, R7, R8, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error, E6 : Result.Error, E7 : Result.Error, E8 : Result.Error> BatchResponse8<Result<R1, E1>, Result<R2, E2>, Result<R3, E3>, Result<R4, E4>, Result<R5, E5>, Result<R6, E6>, Result<R7, E7>, Result<R8, E8>>.unwrap() = BatchResponse8(
    response1.unwrap(),
    response2.unwrap(),
    response3.unwrap(),
    response4.unwrap(),
    response5.unwrap(),
    response6.unwrap(),
    response7.unwrap(),
    response8.unwrap(),
)

fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error, E6 : Result.Error, E7 : Result.Error, E8 : Result.Error, E9 : Result.Error> batchRequest(
    r1: RpcRequest<R1, E1>,
    r2: RpcRequest<R2, E2>,
    r3: RpcRequest<R3, E3>,
    r4: RpcRequest<R4, E4>,
    r5: RpcRequest<R5, E5>,
    r6: RpcRequest<R6, E6>,
    r7: RpcRequest<R7, E7>,
    r8: RpcRequest<R8, E8>,
    r9: RpcRequest<R9, E9>,
): BatchResponse9<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>, PendingResponse<R6, E6>, PendingResponse<R7, E7>, PendingResponse<R8, E8>, PendingResponse<R9, E9>> {
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
    batch.sendDeferred()

    return BatchResponse9(f1, f2, f3, f4, f5, f6, f7, f8, f9)
}

suspend fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error, E6 : Result.Error, E7 : Result.Error, E8 : Result.Error, E9 : Result.Error> BatchResponse9<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>, PendingResponse<R6, E6>, PendingResponse<R7, E7>, PendingResponse<R8, E8>, PendingResponse<R9, E9>>.await() = BatchResponse9(
    response1.await(),
    response2.await(),
    response3.await(),
    response4.await(),
    response5.await(),
    response6.await(),
    response7.await(),
    response8.await(),
    response9.await(),
)

fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error, E6 : Result.Error, E7 : Result.Error, E8 : Result.Error, E9 : Result.Error> BatchResponse9<Result<R1, E1>, Result<R2, E2>, Result<R3, E3>, Result<R4, E4>, Result<R5, E5>, Result<R6, E6>, Result<R7, E7>, Result<R8, E8>, Result<R9, E9>>.unwrap() = BatchResponse9(
    response1.unwrap(),
    response2.unwrap(),
    response3.unwrap(),
    response4.unwrap(),
    response5.unwrap(),
    response6.unwrap(),
    response7.unwrap(),
    response8.unwrap(),
    response9.unwrap(),
)

fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error, E6 : Result.Error, E7 : Result.Error, E8 : Result.Error, E9 : Result.Error, E10 : Result.Error> batchRequest(
    r1: RpcRequest<R1, E1>,
    r2: RpcRequest<R2, E2>,
    r3: RpcRequest<R3, E3>,
    r4: RpcRequest<R4, E4>,
    r5: RpcRequest<R5, E5>,
    r6: RpcRequest<R6, E6>,
    r7: RpcRequest<R7, E7>,
    r8: RpcRequest<R8, E8>,
    r9: RpcRequest<R9, E9>,
    r10: RpcRequest<R10, E10>,
): BatchResponse10<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>, PendingResponse<R6, E6>, PendingResponse<R7, E7>, PendingResponse<R8, E8>, PendingResponse<R9, E9>, PendingResponse<R10, E10>> {
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
    batch.sendDeferred()

    return BatchResponse10(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10)
}

suspend fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error, E6 : Result.Error, E7 : Result.Error, E8 : Result.Error, E9 : Result.Error, E10 : Result.Error> BatchResponse10<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>, PendingResponse<R6, E6>, PendingResponse<R7, E7>, PendingResponse<R8, E8>, PendingResponse<R9, E9>, PendingResponse<R10, E10>>.await() = BatchResponse10(
    response1.await(),
    response2.await(),
    response3.await(),
    response4.await(),
    response5.await(),
    response6.await(),
    response7.await(),
    response8.await(),
    response9.await(),
    response10.await(),
)

fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error, E6 : Result.Error, E7 : Result.Error, E8 : Result.Error, E9 : Result.Error, E10 : Result.Error> BatchResponse10<Result<R1, E1>, Result<R2, E2>, Result<R3, E3>, Result<R4, E4>, Result<R5, E5>, Result<R6, E6>, Result<R7, E7>, Result<R8, E8>, Result<R9, E9>, Result<R10, E10>>.unwrap() = BatchResponse10(
    response1.unwrap(),
    response2.unwrap(),
    response3.unwrap(),
    response4.unwrap(),
    response5.unwrap(),
    response6.unwrap(),
    response7.unwrap(),
    response8.unwrap(),
    response9.unwrap(),
    response10.unwrap(),
)

fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error, E6 : Result.Error, E7 : Result.Error, E8 : Result.Error, E9 : Result.Error, E10 : Result.Error, E11 : Result.Error> batchRequest(
    r1: RpcRequest<R1, E1>,
    r2: RpcRequest<R2, E2>,
    r3: RpcRequest<R3, E3>,
    r4: RpcRequest<R4, E4>,
    r5: RpcRequest<R5, E5>,
    r6: RpcRequest<R6, E6>,
    r7: RpcRequest<R7, E7>,
    r8: RpcRequest<R8, E8>,
    r9: RpcRequest<R9, E9>,
    r10: RpcRequest<R10, E10>,
    r11: RpcRequest<R11, E11>,
): BatchResponse11<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>, PendingResponse<R6, E6>, PendingResponse<R7, E7>, PendingResponse<R8, E8>, PendingResponse<R9, E9>, PendingResponse<R10, E10>, PendingResponse<R11, E11>> {
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
    batch.sendDeferred()

    return BatchResponse11(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11)
}

suspend fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error, E6 : Result.Error, E7 : Result.Error, E8 : Result.Error, E9 : Result.Error, E10 : Result.Error, E11 : Result.Error> BatchResponse11<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>, PendingResponse<R6, E6>, PendingResponse<R7, E7>, PendingResponse<R8, E8>, PendingResponse<R9, E9>, PendingResponse<R10, E10>, PendingResponse<R11, E11>>.await() = BatchResponse11(
    response1.await(),
    response2.await(),
    response3.await(),
    response4.await(),
    response5.await(),
    response6.await(),
    response7.await(),
    response8.await(),
    response9.await(),
    response10.await(),
    response11.await(),
)

fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error, E6 : Result.Error, E7 : Result.Error, E8 : Result.Error, E9 : Result.Error, E10 : Result.Error, E11 : Result.Error> BatchResponse11<Result<R1, E1>, Result<R2, E2>, Result<R3, E3>, Result<R4, E4>, Result<R5, E5>, Result<R6, E6>, Result<R7, E7>, Result<R8, E8>, Result<R9, E9>, Result<R10, E10>, Result<R11, E11>>.unwrap() = BatchResponse11(
    response1.unwrap(),
    response2.unwrap(),
    response3.unwrap(),
    response4.unwrap(),
    response5.unwrap(),
    response6.unwrap(),
    response7.unwrap(),
    response8.unwrap(),
    response9.unwrap(),
    response10.unwrap(),
    response11.unwrap(),
)

fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error, E6 : Result.Error, E7 : Result.Error, E8 : Result.Error, E9 : Result.Error, E10 : Result.Error, E11 : Result.Error, E12 : Result.Error> batchRequest(
    r1: RpcRequest<R1, E1>,
    r2: RpcRequest<R2, E2>,
    r3: RpcRequest<R3, E3>,
    r4: RpcRequest<R4, E4>,
    r5: RpcRequest<R5, E5>,
    r6: RpcRequest<R6, E6>,
    r7: RpcRequest<R7, E7>,
    r8: RpcRequest<R8, E8>,
    r9: RpcRequest<R9, E9>,
    r10: RpcRequest<R10, E10>,
    r11: RpcRequest<R11, E11>,
    r12: RpcRequest<R12, E12>,
): BatchResponse12<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>, PendingResponse<R6, E6>, PendingResponse<R7, E7>, PendingResponse<R8, E8>, PendingResponse<R9, E9>, PendingResponse<R10, E10>, PendingResponse<R11, E11>, PendingResponse<R12, E12>> {
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
    batch.sendDeferred()

    return BatchResponse12(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12)
}

suspend fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error, E6 : Result.Error, E7 : Result.Error, E8 : Result.Error, E9 : Result.Error, E10 : Result.Error, E11 : Result.Error, E12 : Result.Error> BatchResponse12<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>, PendingResponse<R6, E6>, PendingResponse<R7, E7>, PendingResponse<R8, E8>, PendingResponse<R9, E9>, PendingResponse<R10, E10>, PendingResponse<R11, E11>, PendingResponse<R12, E12>>.await() = BatchResponse12(
    response1.await(),
    response2.await(),
    response3.await(),
    response4.await(),
    response5.await(),
    response6.await(),
    response7.await(),
    response8.await(),
    response9.await(),
    response10.await(),
    response11.await(),
    response12.await(),
)

fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, E1 : Result.Error, E2 : Result.Error, E3 : Result.Error, E4 : Result.Error, E5 : Result.Error, E6 : Result.Error, E7 : Result.Error, E8 : Result.Error, E9 : Result.Error, E10 : Result.Error, E11 : Result.Error, E12 : Result.Error> BatchResponse12<Result<R1, E1>, Result<R2, E2>, Result<R3, E3>, Result<R4, E4>, Result<R5, E5>, Result<R6, E6>, Result<R7, E7>, Result<R8, E8>, Result<R9, E9>, Result<R10, E10>, Result<R11, E11>, Result<R12, E12>>.unwrap() = BatchResponse12(
    response1.unwrap(),
    response2.unwrap(),
    response3.unwrap(),
    response4.unwrap(),
    response5.unwrap(),
    response6.unwrap(),
    response7.unwrap(),
    response8.unwrap(),
    response9.unwrap(),
    response10.unwrap(),
    response11.unwrap(),
    response12.unwrap(),
)
