package io.ethers.solana.providers

import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonUnquotedLiteral

/** Preserve the caller's exact integer; range validation belongs to the RPC node. */
@OptIn(ExperimentalSerializationApi::class)
internal fun rpcInteger(value: BigInteger): JsonElement = JsonUnquotedLiteral(value.toString())
