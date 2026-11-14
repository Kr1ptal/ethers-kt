package io.ethers.solana.program

import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.unwrapOrReturn
import io.ethers.solana.codec.SolanaAccountField
import io.ethers.solana.codec.SolanaDecodeError

/** Fields to fetch and a transformation to run after they decode successfully. */
class SolanaAccountProjection<T, R> internal constructor(
    fields: List<SolanaAccountField<T, *>>,
    internal val decode: (SolanaAccountSlice<T>) -> Result<R, SolanaDecodeError>,
) {
    internal val fields: List<SolanaAccountField<T, *>> = fields.distinct()

    /** Transform the decoded value. Exceptions thrown by [transform] propagate to the caller. */
    fun <V> map(transform: (R) -> V): SolanaAccountProjection<T, V> = SolanaAccountProjection(fields) { slice -> decode(slice).map(transform) }
}

/** Helpers inherited by the generated account's Fields object, available inside select lambdas. */
open class SolanaAccountProjectionScope<T> {
    /** Select a raw slice, retaining its address, context and account metadata. */
    fun fields(vararg fields: SolanaAccountField<T, *>): SolanaAccountProjection<T, SolanaAccountSlice<T>> = SolanaAccountProjection(fields.asList()) { success(it) }

    /** Decode the selected fields, then apply [transform]. Decode errors skip the transformation. */
    fun <A, R> combine(
        a: SolanaAccountField<T, A>,
        transform: (A) -> R,
    ): SolanaAccountProjection<T, R> = SolanaAccountProjection(listOf(a)) { slice ->
        val aValue = slice.decode(a).unwrapOrReturn { return@SolanaAccountProjection failure(it) }
        success(transform(aValue))
    }

    /** Decode the selected fields, then apply [transform]. Decode errors skip the transformation. */
    fun <A, B, R> combine(
        a: SolanaAccountField<T, A>,
        b: SolanaAccountField<T, B>,
        transform: (A, B) -> R,
    ): SolanaAccountProjection<T, R> = SolanaAccountProjection(listOf(a, b)) { slice ->
        val aValue = slice.decode(a).unwrapOrReturn { return@SolanaAccountProjection failure(it) }
        val bValue = slice.decode(b).unwrapOrReturn { return@SolanaAccountProjection failure(it) }
        success(transform(aValue, bValue))
    }

    /** Decode the selected fields, then apply [transform]. Decode errors skip the transformation. */
    fun <A, B, C, R> combine(
        a: SolanaAccountField<T, A>,
        b: SolanaAccountField<T, B>,
        c: SolanaAccountField<T, C>,
        transform: (A, B, C) -> R,
    ): SolanaAccountProjection<T, R> = SolanaAccountProjection(listOf(a, b, c)) { slice ->
        val aValue = slice.decode(a).unwrapOrReturn { return@SolanaAccountProjection failure(it) }
        val bValue = slice.decode(b).unwrapOrReturn { return@SolanaAccountProjection failure(it) }
        val cValue = slice.decode(c).unwrapOrReturn { return@SolanaAccountProjection failure(it) }
        success(transform(aValue, bValue, cValue))
    }

    /** Decode the selected fields, then apply [transform]. Decode errors skip the transformation. */
    fun <A, B, C, D, R> combine(
        a: SolanaAccountField<T, A>,
        b: SolanaAccountField<T, B>,
        c: SolanaAccountField<T, C>,
        d: SolanaAccountField<T, D>,
        transform: (A, B, C, D) -> R,
    ): SolanaAccountProjection<T, R> = SolanaAccountProjection(listOf(a, b, c, d)) { slice ->
        val aValue = slice.decode(a).unwrapOrReturn { return@SolanaAccountProjection failure(it) }
        val bValue = slice.decode(b).unwrapOrReturn { return@SolanaAccountProjection failure(it) }
        val cValue = slice.decode(c).unwrapOrReturn { return@SolanaAccountProjection failure(it) }
        val dValue = slice.decode(d).unwrapOrReturn { return@SolanaAccountProjection failure(it) }
        success(transform(aValue, bValue, cValue, dValue))
    }

    /** Decode the selected fields, then apply [transform]. Decode errors skip the transformation. */
    fun <A, B, C, D, E, R> combine(
        a: SolanaAccountField<T, A>,
        b: SolanaAccountField<T, B>,
        c: SolanaAccountField<T, C>,
        d: SolanaAccountField<T, D>,
        e: SolanaAccountField<T, E>,
        transform: (A, B, C, D, E) -> R,
    ): SolanaAccountProjection<T, R> = SolanaAccountProjection(listOf(a, b, c, d, e)) { slice ->
        val aValue = slice.decode(a).unwrapOrReturn { return@SolanaAccountProjection failure(it) }
        val bValue = slice.decode(b).unwrapOrReturn { return@SolanaAccountProjection failure(it) }
        val cValue = slice.decode(c).unwrapOrReturn { return@SolanaAccountProjection failure(it) }
        val dValue = slice.decode(d).unwrapOrReturn { return@SolanaAccountProjection failure(it) }
        val eValue = slice.decode(e).unwrapOrReturn { return@SolanaAccountProjection failure(it) }
        success(transform(aValue, bValue, cValue, dValue, eValue))
    }
}
