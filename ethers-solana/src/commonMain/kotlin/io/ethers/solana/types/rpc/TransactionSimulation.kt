@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.ethers.solana.types.rpc

import io.ethers.solana.types.ExtensibleJsonSerializer
import io.ethers.solana.types.OtherFieldsSerializer
import io.ethers.solana.types.U32Serializer
import io.ethers.solana.types.transaction.LoadedAddresses
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** A failed simulated transaction is a successful RPC response with a non-null [err]. */
@KeepGeneratedSerializer
@Serializable(with = TransactionSimulationSerializer::class)
data class TransactionSimulation(
    val err: TransactionError?,
    val logs: List<String>? = null,
    val unitsConsumed: BigInteger? = null,
    val returnData: ReturnData? = null,
    val accounts: List<AccountInfo?>? = null,
    val innerInstructions: List<InnerInstructions>? = null,
    val replacementBlockhash: LatestBlockhash? = null,
    @Serializable(with = U32Serializer::class) val loadedAccountsDataSize: Long? = null,
    val fee: BigInteger? = null,
    val preBalances: List<BigInteger>? = null,
    val postBalances: List<BigInteger>? = null,
    val preTokenBalances: List<TokenBalance>? = null,
    val postTokenBalances: List<TokenBalance>? = null,
    val loadedAddresses: LoadedAddresses? = null,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
) {
    val isSuccess: Boolean get() = err == null
}

object TransactionSimulationSerializer : ExtensibleJsonSerializer<TransactionSimulation>(TransactionSimulation.generatedSerializer(), { it.otherFields })
