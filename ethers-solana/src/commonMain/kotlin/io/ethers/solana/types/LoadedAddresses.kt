@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(U64Serializer::class)

package io.ethers.solana.types

import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Addresses resolved from lookup tables. */
@KeepGeneratedSerializer
@Serializable(with = LoadedAddressesSerializer::class)
data class LoadedAddresses(
    val writable: List<SolanaAddress>,
    val readonly: List<SolanaAddress>,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
)

object LoadedAddressesSerializer : ExtensibleJsonSerializer<LoadedAddresses>(LoadedAddresses.generatedSerializer(), { it.otherFields })
