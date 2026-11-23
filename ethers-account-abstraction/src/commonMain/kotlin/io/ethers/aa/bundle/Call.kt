package io.ethers.aa.bundle

import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf

/**
 * A call a smart account makes on behalf of its owner, as decoded from a user operation's call data.
 */
data class Call(
    val to: Address,
    val value: BigInteger = bigIntegerOf(0),
    val data: Bytes = Bytes.EMPTY,
)
