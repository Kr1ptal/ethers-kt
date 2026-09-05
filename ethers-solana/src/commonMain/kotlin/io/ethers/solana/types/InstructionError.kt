package io.ethers.solana.types

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/** The instruction-error wire enum; unknown future variants remain lossless. */
@Serializable(with = InstructionErrorSerializer::class)
sealed interface InstructionError {
    @Serializable
    enum class Simple(val wireName: String) : InstructionError {
        @SerialName("GenericError")
        GENERIC_ERROR("GenericError"),
        @SerialName("InvalidArgument")
        INVALID_ARGUMENT("InvalidArgument"),
        @SerialName("InvalidInstructionData")
        INVALID_INSTRUCTION_DATA("InvalidInstructionData"),
        @SerialName("InvalidAccountData")
        INVALID_ACCOUNT_DATA("InvalidAccountData"),
        @SerialName("AccountDataTooSmall")
        ACCOUNT_DATA_TOO_SMALL("AccountDataTooSmall"),
        @SerialName("InsufficientFunds")
        INSUFFICIENT_FUNDS("InsufficientFunds"),
        @SerialName("IncorrectProgramId")
        INCORRECT_PROGRAM_ID("IncorrectProgramId"),
        @SerialName("MissingRequiredSignature")
        MISSING_REQUIRED_SIGNATURE("MissingRequiredSignature"),
        @SerialName("AccountAlreadyInitialized")
        ACCOUNT_ALREADY_INITIALIZED("AccountAlreadyInitialized"),
        @SerialName("UninitializedAccount")
        UNINITIALIZED_ACCOUNT("UninitializedAccount"),
        @SerialName("UnbalancedInstruction")
        UNBALANCED_INSTRUCTION("UnbalancedInstruction"),
        @SerialName("ModifiedProgramId")
        MODIFIED_PROGRAM_ID("ModifiedProgramId"),
        @SerialName("ExternalAccountLamportSpend")
        EXTERNAL_ACCOUNT_LAMPORT_SPEND("ExternalAccountLamportSpend"),
        @SerialName("ExternalAccountDataModified")
        EXTERNAL_ACCOUNT_DATA_MODIFIED("ExternalAccountDataModified"),
        @SerialName("ReadonlyLamportChange")
        READONLY_LAMPORT_CHANGE("ReadonlyLamportChange"),
        @SerialName("ReadonlyDataModified")
        READONLY_DATA_MODIFIED("ReadonlyDataModified"),
        @SerialName("DuplicateAccountIndex")
        DUPLICATE_ACCOUNT_INDEX("DuplicateAccountIndex"),
        @SerialName("ExecutableModified")
        EXECUTABLE_MODIFIED("ExecutableModified"),
        @SerialName("RentEpochModified")
        RENT_EPOCH_MODIFIED("RentEpochModified"),
        @SerialName("NotEnoughAccountKeys")
        NOT_ENOUGH_ACCOUNT_KEYS("NotEnoughAccountKeys"),
        @SerialName("AccountDataSizeChanged")
        ACCOUNT_DATA_SIZE_CHANGED("AccountDataSizeChanged"),
        @SerialName("AccountNotExecutable")
        ACCOUNT_NOT_EXECUTABLE("AccountNotExecutable"),
        @SerialName("AccountBorrowFailed")
        ACCOUNT_BORROW_FAILED("AccountBorrowFailed"),
        @SerialName("AccountBorrowOutstanding")
        ACCOUNT_BORROW_OUTSTANDING("AccountBorrowOutstanding"),
        @SerialName("DuplicateAccountOutOfSync")
        DUPLICATE_ACCOUNT_OUT_OF_SYNC("DuplicateAccountOutOfSync"),
        @SerialName("InvalidError")
        INVALID_ERROR("InvalidError"),
        @SerialName("ExecutableDataModified")
        EXECUTABLE_DATA_MODIFIED("ExecutableDataModified"),
        @SerialName("ExecutableLamportChange")
        EXECUTABLE_LAMPORT_CHANGE("ExecutableLamportChange"),
        @SerialName("ExecutableAccountNotRentExempt")
        EXECUTABLE_ACCOUNT_NOT_RENT_EXEMPT("ExecutableAccountNotRentExempt"),
        @SerialName("UnsupportedProgramId")
        UNSUPPORTED_PROGRAM_ID("UnsupportedProgramId"),
        @SerialName("CallDepth")
        CALL_DEPTH("CallDepth"),
        @SerialName("MissingAccount")
        MISSING_ACCOUNT("MissingAccount"),
        @SerialName("ReentrancyNotAllowed")
        REENTRANCY_NOT_ALLOWED("ReentrancyNotAllowed"),
        @SerialName("MaxSeedLengthExceeded")
        MAX_SEED_LENGTH_EXCEEDED("MaxSeedLengthExceeded"),
        @SerialName("InvalidSeeds")
        INVALID_SEEDS("InvalidSeeds"),
        @SerialName("InvalidRealloc")
        INVALID_REALLOC("InvalidRealloc"),
        @SerialName("ComputationalBudgetExceeded")
        COMPUTATIONAL_BUDGET_EXCEEDED("ComputationalBudgetExceeded"),
        @SerialName("PrivilegeEscalation")
        PRIVILEGE_ESCALATION("PrivilegeEscalation"),
        @SerialName("ProgramEnvironmentSetupFailure")
        PROGRAM_ENVIRONMENT_SETUP_FAILURE("ProgramEnvironmentSetupFailure"),
        @SerialName("ProgramFailedToComplete")
        PROGRAM_FAILED_TO_COMPLETE("ProgramFailedToComplete"),
        @SerialName("ProgramFailedToCompile")
        PROGRAM_FAILED_TO_COMPILE("ProgramFailedToCompile"),
        @SerialName("Immutable")
        IMMUTABLE("Immutable"),
        @SerialName("IncorrectAuthority")
        INCORRECT_AUTHORITY("IncorrectAuthority"),
        @SerialName("BorshIoError")
        BORSH_IO_ERROR("BorshIoError"),
        @SerialName("AccountNotRentExempt")
        ACCOUNT_NOT_RENT_EXEMPT("AccountNotRentExempt"),
        @SerialName("InvalidAccountOwner")
        INVALID_ACCOUNT_OWNER("InvalidAccountOwner"),
        @SerialName("ArithmeticOverflow")
        ARITHMETIC_OVERFLOW("ArithmeticOverflow"),
        @SerialName("UnsupportedSysvar")
        UNSUPPORTED_SYSVAR("UnsupportedSysvar"),
        @SerialName("IllegalOwner")
        ILLEGAL_OWNER("IllegalOwner"),
        @SerialName("MaxAccountsDataAllocationsExceeded")
        MAX_ACCOUNTS_DATA_ALLOCATIONS_EXCEEDED("MaxAccountsDataAllocationsExceeded"),
        @SerialName("MaxAccountsExceeded")
        MAX_ACCOUNTS_EXCEEDED("MaxAccountsExceeded"),
        @SerialName("MaxInstructionTraceLengthExceeded")
        MAX_INSTRUCTION_TRACE_LENGTH_EXCEEDED("MaxInstructionTraceLengthExceeded"),
        @SerialName("BuiltinProgramsMustConsumeComputeUnits")
        BUILTIN_PROGRAMS_MUST_CONSUME_COMPUTE_UNITS("BuiltinProgramsMustConsumeComputeUnits"),
        @SerialName("BailOut")
        BAIL_OUT("BailOut"),
    }

    @Serializable(with = CustomInstructionErrorSerializer::class)
    data class Custom(val code: Long) : InstructionError

    /** Older validators include a message; newer ones emit the simple BorshIoError variant. */
    @Serializable(with = BorshIoErrorSerializer::class)
    data class BorshIoError(val message: String) : InstructionError

    @Serializable(with = UnknownInstructionErrorSerializer::class)
    data class Unknown(val raw: JsonElement) : InstructionError
}

object InstructionErrorSerializer : KSerializer<InstructionError> {
    override val descriptor = JsonElement.serializer().descriptor
    override fun deserialize(decoder: Decoder): InstructionError {
        val input = decoder as JsonDecoder
        val value = input.decodeJsonElement()
        if (value is JsonPrimitive && value.isString) {
            return InstructionError.Simple.entries.firstOrNull { it.wireName == value.content } ?: InstructionError.Unknown(value)
        }
        if (value !is JsonObject || value.size != 1) return InstructionError.Unknown(value)
        return when (value.keys.single()) {
            "Custom" -> input.json.decodeFromJsonElement(CustomInstructionErrorSerializer, value)
            "BorshIoError" -> input.json.decodeFromJsonElement(BorshIoErrorSerializer, value)
            else -> InstructionError.Unknown(value)
        }
    }
    override fun serialize(encoder: Encoder, value: InstructionError) {
        when (value) {
            is InstructionError.Simple -> encoder.encodeSerializableValue(InstructionError.Simple.serializer(), value)
            is InstructionError.Custom -> encoder.encodeSerializableValue(CustomInstructionErrorSerializer, value)
            is InstructionError.BorshIoError -> encoder.encodeSerializableValue(BorshIoErrorSerializer, value)
            is InstructionError.Unknown -> encoder.encodeSerializableValue(UnknownInstructionErrorSerializer, value)
        }
    }
}

object CustomInstructionErrorSerializer : TaggedJsonSerializer<InstructionError.Custom>("Custom", MappedSerializer(U32Serializer, InstructionError::Custom, { it.code }))
object BorshIoErrorSerializer : TaggedJsonSerializer<InstructionError.BorshIoError>("BorshIoError", MappedSerializer(String.serializer(), InstructionError::BorshIoError, { it.message }))
object UnknownInstructionErrorSerializer : MappedSerializer<JsonElement, InstructionError.Unknown>(ExactJsonSerializer, InstructionError::Unknown, { it.raw })
