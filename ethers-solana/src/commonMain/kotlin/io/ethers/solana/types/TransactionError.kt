@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.ethers.solana.types

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

/** Runtime errors shared by history, simulation and signature/log notifications. */
@Serializable(with = TransactionErrorSerializer::class)
sealed interface TransactionError {
    @Serializable
    enum class Simple(val wireName: String) : TransactionError {
        @SerialName("AccountInUse")
        ACCOUNT_IN_USE("AccountInUse"),
        @SerialName("AccountLoadedTwice")
        ACCOUNT_LOADED_TWICE("AccountLoadedTwice"),
        @SerialName("AccountNotFound")
        ACCOUNT_NOT_FOUND("AccountNotFound"),
        @SerialName("ProgramAccountNotFound")
        PROGRAM_ACCOUNT_NOT_FOUND("ProgramAccountNotFound"),
        @SerialName("InsufficientFundsForFee")
        INSUFFICIENT_FUNDS_FOR_FEE("InsufficientFundsForFee"),
        @SerialName("InvalidAccountForFee")
        INVALID_ACCOUNT_FOR_FEE("InvalidAccountForFee"),
        @SerialName("AlreadyProcessed")
        ALREADY_PROCESSED("AlreadyProcessed"),
        @SerialName("BlockhashNotFound")
        BLOCKHASH_NOT_FOUND("BlockhashNotFound"),
        @SerialName("CallChainTooDeep")
        CALL_CHAIN_TOO_DEEP("CallChainTooDeep"),
        @SerialName("MissingSignatureForFee")
        MISSING_SIGNATURE_FOR_FEE("MissingSignatureForFee"),
        @SerialName("InvalidAccountIndex")
        INVALID_ACCOUNT_INDEX("InvalidAccountIndex"),
        @SerialName("SignatureFailure")
        SIGNATURE_FAILURE("SignatureFailure"),
        @SerialName("InvalidProgramForExecution")
        INVALID_PROGRAM_FOR_EXECUTION("InvalidProgramForExecution"),
        @SerialName("SanitizeFailure")
        SANITIZE_FAILURE("SanitizeFailure"),
        @SerialName("ClusterMaintenance")
        CLUSTER_MAINTENANCE("ClusterMaintenance"),
        @SerialName("AccountBorrowOutstanding")
        ACCOUNT_BORROW_OUTSTANDING("AccountBorrowOutstanding"),
        @SerialName("WouldExceedMaxBlockCostLimit")
        WOULD_EXCEED_MAX_BLOCK_COST_LIMIT("WouldExceedMaxBlockCostLimit"),
        @SerialName("UnsupportedVersion")
        UNSUPPORTED_VERSION("UnsupportedVersion"),
        @SerialName("InvalidWritableAccount")
        INVALID_WRITABLE_ACCOUNT("InvalidWritableAccount"),
        @SerialName("WouldExceedMaxAccountCostLimit")
        WOULD_EXCEED_MAX_ACCOUNT_COST_LIMIT("WouldExceedMaxAccountCostLimit"),
        @SerialName("WouldExceedAccountDataBlockLimit")
        WOULD_EXCEED_ACCOUNT_DATA_BLOCK_LIMIT("WouldExceedAccountDataBlockLimit"),
        @SerialName("TooManyAccountLocks")
        TOO_MANY_ACCOUNT_LOCKS("TooManyAccountLocks"),
        @SerialName("AddressLookupTableNotFound")
        ADDRESS_LOOKUP_TABLE_NOT_FOUND("AddressLookupTableNotFound"),
        @SerialName("InvalidAddressLookupTableOwner")
        INVALID_ADDRESS_LOOKUP_TABLE_OWNER("InvalidAddressLookupTableOwner"),
        @SerialName("InvalidAddressLookupTableData")
        INVALID_ADDRESS_LOOKUP_TABLE_DATA("InvalidAddressLookupTableData"),
        @SerialName("InvalidAddressLookupTableIndex")
        INVALID_ADDRESS_LOOKUP_TABLE_INDEX("InvalidAddressLookupTableIndex"),
        @SerialName("InvalidRentPayingAccount")
        INVALID_RENT_PAYING_ACCOUNT("InvalidRentPayingAccount"),
        @SerialName("WouldExceedMaxVoteCostLimit")
        WOULD_EXCEED_MAX_VOTE_COST_LIMIT("WouldExceedMaxVoteCostLimit"),
        @SerialName("WouldExceedAccountDataTotalLimit")
        WOULD_EXCEED_ACCOUNT_DATA_TOTAL_LIMIT("WouldExceedAccountDataTotalLimit"),
        @SerialName("MaxLoadedAccountsDataSizeExceeded")
        MAX_LOADED_ACCOUNTS_DATA_SIZE_EXCEEDED("MaxLoadedAccountsDataSizeExceeded"),
        @SerialName("InvalidLoadedAccountsDataSizeLimit")
        INVALID_LOADED_ACCOUNTS_DATA_SIZE_LIMIT("InvalidLoadedAccountsDataSizeLimit"),
        @SerialName("ResanitizationNeeded")
        RESANITIZATION_NEEDED("ResanitizationNeeded"),
        @SerialName("UnbalancedTransaction")
        UNBALANCED_TRANSACTION("UnbalancedTransaction"),
        @SerialName("ProgramCacheHitMaxLimit")
        PROGRAM_CACHE_HIT_MAX_LIMIT("ProgramCacheHitMaxLimit"),
        @SerialName("CommitCancelled")
        COMMIT_CANCELLED("CommitCancelled"),
        @SerialName("BailOut")
        BAIL_OUT("BailOut"),
    }

    @Serializable(with = InstructionFailureSerializer::class)
    data class InstructionFailure(val instructionIndex: Int, val instructionError: InstructionError) : TransactionError

    @Serializable(with = DuplicateInstructionSerializer::class)
    data class DuplicateInstruction(val instructionIndex: Int) : TransactionError

    @KeepGeneratedSerializer
    @Serializable(with = InsufficientFundsForRentSerializer::class)
    data class InsufficientFundsForRent(
        @SerialName("account_index") @Serializable(with = U8Serializer::class) val accountIndex: Int,
        @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
    ) : TransactionError

    @KeepGeneratedSerializer
    @Serializable(with = ProgramExecutionTemporarilyRestrictedSerializer::class)
    data class ProgramExecutionTemporarilyRestricted(
        @SerialName("account_index") @Serializable(with = U8Serializer::class) val accountIndex: Int,
        @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
    ) : TransactionError

    @Serializable(with = UnknownTransactionErrorSerializer::class)
    data class Unknown(val raw: JsonElement) : TransactionError
}

object TransactionErrorSerializer : KSerializer<TransactionError> {
    override val descriptor = JsonElement.serializer().descriptor
    override fun deserialize(decoder: Decoder): TransactionError {
        val input = decoder as JsonDecoder
        val value = input.decodeJsonElement()
        if (value is JsonPrimitive && value.isString) {
            return TransactionError.Simple.entries.firstOrNull { it.wireName == value.content } ?: TransactionError.Unknown(value)
        }
        if (value !is JsonObject || value.size != 1) return TransactionError.Unknown(value)
        return when (value.keys.single()) {
            "InstructionError" -> input.json.decodeFromJsonElement(InstructionFailureSerializer, value)
            "DuplicateInstruction" -> input.json.decodeFromJsonElement(DuplicateInstructionSerializer, value)
            "InsufficientFundsForRent" -> input.json.decodeFromJsonElement(InsufficientFundsForRentSerializer, value)
            "ProgramExecutionTemporarilyRestricted" -> input.json.decodeFromJsonElement(ProgramExecutionTemporarilyRestrictedSerializer, value)
            else -> TransactionError.Unknown(value)
        }
    }

    override fun serialize(encoder: Encoder, value: TransactionError) {
        when (value) {
            is TransactionError.Simple -> encoder.encodeSerializableValue(TransactionError.Simple.serializer(), value)
            is TransactionError.InstructionFailure -> encoder.encodeSerializableValue(InstructionFailureSerializer, value)
            is TransactionError.DuplicateInstruction -> encoder.encodeSerializableValue(DuplicateInstructionSerializer, value)
            is TransactionError.InsufficientFundsForRent -> encoder.encodeSerializableValue(InsufficientFundsForRentSerializer, value)
            is TransactionError.ProgramExecutionTemporarilyRestricted -> encoder.encodeSerializableValue(ProgramExecutionTemporarilyRestrictedSerializer, value)
            is TransactionError.Unknown -> encoder.encodeSerializableValue(UnknownTransactionErrorSerializer, value)
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
object UnknownTransactionErrorSerializer : MappedSerializer<JsonElement, TransactionError.Unknown>(ExactJsonSerializer, TransactionError::Unknown, { it.raw })
