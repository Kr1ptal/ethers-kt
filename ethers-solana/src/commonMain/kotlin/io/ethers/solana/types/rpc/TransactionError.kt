@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.ethers.solana.types.rpc

import io.ethers.solana.types.ExtensibleJsonSerializer
import io.ethers.solana.types.MappedSerializer
import io.ethers.solana.types.OtherFieldsSerializer
import io.ethers.solana.types.RawJsonSerializer
import io.ethers.solana.types.TaggedJsonSerializer
import io.ethers.solana.types.U8Serializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import io.ethers.core.json.JsonElement as RawJson

/** Runtime errors shared by history, simulation and signature/log notifications. */
@Serializable(with = TransactionErrorSerializer::class)
sealed interface TransactionError {
    // Payload-free variants, named exactly as the protocol's Rust enum declares them.
    data object AccountInUse : TransactionError
    data object AccountLoadedTwice : TransactionError
    data object AccountNotFound : TransactionError
    data object ProgramAccountNotFound : TransactionError
    data object InsufficientFundsForFee : TransactionError
    data object InvalidAccountForFee : TransactionError
    data object AlreadyProcessed : TransactionError
    data object BlockhashNotFound : TransactionError
    data object CallChainTooDeep : TransactionError
    data object MissingSignatureForFee : TransactionError
    data object InvalidAccountIndex : TransactionError
    data object SignatureFailure : TransactionError
    data object InvalidProgramForExecution : TransactionError
    data object SanitizeFailure : TransactionError
    data object ClusterMaintenance : TransactionError
    data object AccountBorrowOutstanding : TransactionError
    data object WouldExceedMaxBlockCostLimit : TransactionError
    data object UnsupportedVersion : TransactionError
    data object InvalidWritableAccount : TransactionError
    data object WouldExceedMaxAccountCostLimit : TransactionError
    data object WouldExceedAccountDataBlockLimit : TransactionError
    data object TooManyAccountLocks : TransactionError
    data object AddressLookupTableNotFound : TransactionError
    data object InvalidAddressLookupTableOwner : TransactionError
    data object InvalidAddressLookupTableData : TransactionError
    data object InvalidAddressLookupTableIndex : TransactionError
    data object InvalidRentPayingAccount : TransactionError
    data object WouldExceedMaxVoteCostLimit : TransactionError
    data object WouldExceedAccountDataTotalLimit : TransactionError
    data object MaxLoadedAccountsDataSizeExceeded : TransactionError
    data object InvalidLoadedAccountsDataSizeLimit : TransactionError
    data object ResanitizationNeeded : TransactionError
    data object UnbalancedTransaction : TransactionError
    data object ProgramCacheHitMaxLimit : TransactionError
    data object CommitCancelled : TransactionError
    data object BailOut : TransactionError

    @Serializable(with = InstructionFailureSerializer::class)
    data class InstructionFailure(val instructionIndex: Int, val instructionError: InstructionError) : TransactionError

    @Serializable(with = DuplicateInstructionSerializer::class)
    data class DuplicateInstruction(val instructionIndex: Int) : TransactionError

    @KeepGeneratedSerializer
    @Serializable(with = InsufficientFundsForRentSerializer::class)
    data class InsufficientFundsForRent(
        @SerialName("account_index") @Serializable(with = U8Serializer::class) val accountIndex: Int,
        @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, RawJson> = emptyMap(),
    ) : TransactionError

    @KeepGeneratedSerializer
    @Serializable(with = ProgramExecutionTemporarilyRestrictedSerializer::class)
    data class ProgramExecutionTemporarilyRestricted(
        @SerialName("account_index") @Serializable(with = U8Serializer::class) val accountIndex: Int,
        @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, RawJson> = emptyMap(),
    ) : TransactionError

    @Serializable(with = UnknownTransactionErrorSerializer::class)
    data class Unknown(val raw: RawJson) : TransactionError

    companion object {
        /** Every variant the wire encodes as a bare name, in the order the protocol declares them. */
        private val WIRE_NAMES: Map<TransactionError, String> = linkedMapOf(
            AccountInUse to "AccountInUse",
            AccountLoadedTwice to "AccountLoadedTwice",
            AccountNotFound to "AccountNotFound",
            ProgramAccountNotFound to "ProgramAccountNotFound",
            InsufficientFundsForFee to "InsufficientFundsForFee",
            InvalidAccountForFee to "InvalidAccountForFee",
            AlreadyProcessed to "AlreadyProcessed",
            BlockhashNotFound to "BlockhashNotFound",
            CallChainTooDeep to "CallChainTooDeep",
            MissingSignatureForFee to "MissingSignatureForFee",
            InvalidAccountIndex to "InvalidAccountIndex",
            SignatureFailure to "SignatureFailure",
            InvalidProgramForExecution to "InvalidProgramForExecution",
            SanitizeFailure to "SanitizeFailure",
            ClusterMaintenance to "ClusterMaintenance",
            AccountBorrowOutstanding to "AccountBorrowOutstanding",
            WouldExceedMaxBlockCostLimit to "WouldExceedMaxBlockCostLimit",
            UnsupportedVersion to "UnsupportedVersion",
            InvalidWritableAccount to "InvalidWritableAccount",
            WouldExceedMaxAccountCostLimit to "WouldExceedMaxAccountCostLimit",
            WouldExceedAccountDataBlockLimit to "WouldExceedAccountDataBlockLimit",
            TooManyAccountLocks to "TooManyAccountLocks",
            AddressLookupTableNotFound to "AddressLookupTableNotFound",
            InvalidAddressLookupTableOwner to "InvalidAddressLookupTableOwner",
            InvalidAddressLookupTableData to "InvalidAddressLookupTableData",
            InvalidAddressLookupTableIndex to "InvalidAddressLookupTableIndex",
            InvalidRentPayingAccount to "InvalidRentPayingAccount",
            WouldExceedMaxVoteCostLimit to "WouldExceedMaxVoteCostLimit",
            WouldExceedAccountDataTotalLimit to "WouldExceedAccountDataTotalLimit",
            MaxLoadedAccountsDataSizeExceeded to "MaxLoadedAccountsDataSizeExceeded",
            InvalidLoadedAccountsDataSizeLimit to "InvalidLoadedAccountsDataSizeLimit",
            ResanitizationNeeded to "ResanitizationNeeded",
            UnbalancedTransaction to "UnbalancedTransaction",
            ProgramCacheHitMaxLimit to "ProgramCacheHitMaxLimit",
            CommitCancelled to "CommitCancelled",
            BailOut to "BailOut",
        )
        private val BY_WIRE_NAME: Map<String, TransactionError> = WIRE_NAMES.entries.associate { it.value to it.key }

        /** Variants carrying no payload, which is every one the wire writes as a bare name. */
        val PAYLOAD_FREE: List<TransactionError> = WIRE_NAMES.keys.toList()

        /** The bare name the wire uses for [error], or null when it carries a payload. */
        fun wireNameOf(error: TransactionError): String? = WIRE_NAMES[error]

        /** The variant [wireName] names, or null when the protocol has added one this library predates. */
        fun fromWireName(wireName: String): TransactionError? = BY_WIRE_NAME[wireName]
    }
}

object TransactionErrorSerializer : KSerializer<TransactionError> {
    override val descriptor = JsonElement.serializer().descriptor
    override fun deserialize(decoder: Decoder): TransactionError {
        val input = decoder as JsonDecoder
        val value = input.decodeJsonElement()
        if (value is JsonPrimitive && value.isString) {
            return TransactionError.fromWireName(value.content) ?: TransactionError.Unknown(RawJson(value.toString()))
        }
        if (value !is JsonObject || value.size != 1) return TransactionError.Unknown(RawJson(value.toString()))
        return when (value.keys.single()) {
            "InstructionError" -> input.json.decodeFromJsonElement(InstructionFailureSerializer, value)
            "DuplicateInstruction" -> input.json.decodeFromJsonElement(DuplicateInstructionSerializer, value)
            "InsufficientFundsForRent" -> input.json.decodeFromJsonElement(InsufficientFundsForRentSerializer, value)
            "ProgramExecutionTemporarilyRestricted" -> input.json.decodeFromJsonElement(ProgramExecutionTemporarilyRestrictedSerializer, value)
            else -> TransactionError.Unknown(RawJson(value.toString()))
        }
    }

    override fun serialize(encoder: Encoder, value: TransactionError) {
        when (value) {
            is TransactionError.InstructionFailure -> encoder.encodeSerializableValue(InstructionFailureSerializer, value)
            is TransactionError.DuplicateInstruction -> encoder.encodeSerializableValue(DuplicateInstructionSerializer, value)
            is TransactionError.InsufficientFundsForRent -> encoder.encodeSerializableValue(InsufficientFundsForRentSerializer, value)
            is TransactionError.ProgramExecutionTemporarilyRestricted -> encoder.encodeSerializableValue(ProgramExecutionTemporarilyRestrictedSerializer, value)
            is TransactionError.Unknown -> encoder.encodeSerializableValue(UnknownTransactionErrorSerializer, value)
            else -> (encoder as JsonEncoder).encodeJsonElement(
                JsonPrimitive(requireNotNull(TransactionError.wireNameOf(value)) { "Unnamed transaction error $value" }),
            )
        }
    }
}

object InstructionFailureSerializer : TaggedJsonSerializer<TransactionError.InstructionFailure>("InstructionError", InstructionFailureTupleSerializer)

private object InstructionFailureTupleSerializer : KSerializer<TransactionError.InstructionFailure> {
    override val descriptor = JsonArray.serializer().descriptor
    override fun deserialize(decoder: Decoder): TransactionError.InstructionFailure {
        val input = decoder as JsonDecoder
        val tuple = input.decodeJsonElement().jsonArray
        require(tuple.size == 2) { "Expected instruction index and error" }
        return TransactionError.InstructionFailure(input.json.decodeFromJsonElement(U8Serializer, tuple[0]), input.json.decodeFromJsonElement(InstructionError.serializer(), tuple[1]))
    }
    override fun serialize(encoder: Encoder, value: TransactionError.InstructionFailure) {
        val output = encoder as JsonEncoder
        output.encodeJsonElement(JsonArray(listOf(output.json.encodeToJsonElement(U8Serializer, value.instructionIndex), output.json.encodeToJsonElement(InstructionError.serializer(), value.instructionError))))
    }
}

object DuplicateInstructionSerializer : TaggedJsonSerializer<TransactionError.DuplicateInstruction>("DuplicateInstruction", MappedSerializer(U8Serializer, TransactionError::DuplicateInstruction, { it.instructionIndex }))
object InsufficientFundsForRentSerializer : TaggedJsonSerializer<TransactionError.InsufficientFundsForRent>("InsufficientFundsForRent", RentErrorFieldsSerializer)
object ProgramExecutionTemporarilyRestrictedSerializer : TaggedJsonSerializer<TransactionError.ProgramExecutionTemporarilyRestricted>("ProgramExecutionTemporarilyRestricted", RestrictedErrorFieldsSerializer)
private object RentErrorFieldsSerializer : ExtensibleJsonSerializer<TransactionError.InsufficientFundsForRent>(TransactionError.InsufficientFundsForRent.generatedSerializer(), { it.otherFields })
private object RestrictedErrorFieldsSerializer : ExtensibleJsonSerializer<TransactionError.ProgramExecutionTemporarilyRestricted>(TransactionError.ProgramExecutionTemporarilyRestricted.generatedSerializer(), { it.otherFields })
object UnknownTransactionErrorSerializer : MappedSerializer<RawJson, TransactionError.Unknown>(RawJsonSerializer, TransactionError::Unknown, { it.raw })
