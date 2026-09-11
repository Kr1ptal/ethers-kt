package io.ethers.solana.types.rpc

import io.ethers.solana.types.MappedSerializer
import io.ethers.solana.types.RawJsonSerializer
import io.ethers.solana.types.TaggedJsonSerializer
import io.ethers.solana.types.U32Serializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import io.ethers.core.json.JsonElement as RawJson

/** The instruction-error wire enum; unknown future variants remain lossless. */
@Serializable(with = InstructionErrorSerializer::class)
sealed interface InstructionError {
    // Payload-free variants, named exactly as the protocol's Rust enum declares them.
    data object GenericError : InstructionError
    data object InvalidArgument : InstructionError
    data object InvalidInstructionData : InstructionError
    data object InvalidAccountData : InstructionError
    data object AccountDataTooSmall : InstructionError
    data object InsufficientFunds : InstructionError
    data object IncorrectProgramId : InstructionError
    data object MissingRequiredSignature : InstructionError
    data object AccountAlreadyInitialized : InstructionError
    data object UninitializedAccount : InstructionError
    data object UnbalancedInstruction : InstructionError
    data object ModifiedProgramId : InstructionError
    data object ExternalAccountLamportSpend : InstructionError
    data object ExternalAccountDataModified : InstructionError
    data object ReadonlyLamportChange : InstructionError
    data object ReadonlyDataModified : InstructionError
    data object DuplicateAccountIndex : InstructionError
    data object ExecutableModified : InstructionError
    data object RentEpochModified : InstructionError
    data object NotEnoughAccountKeys : InstructionError
    data object AccountDataSizeChanged : InstructionError
    data object AccountNotExecutable : InstructionError
    data object AccountBorrowFailed : InstructionError
    data object AccountBorrowOutstanding : InstructionError
    data object DuplicateAccountOutOfSync : InstructionError
    data object InvalidError : InstructionError
    data object ExecutableDataModified : InstructionError
    data object ExecutableLamportChange : InstructionError
    data object ExecutableAccountNotRentExempt : InstructionError
    data object UnsupportedProgramId : InstructionError
    data object CallDepth : InstructionError
    data object MissingAccount : InstructionError
    data object ReentrancyNotAllowed : InstructionError
    data object MaxSeedLengthExceeded : InstructionError
    data object InvalidSeeds : InstructionError
    data object InvalidRealloc : InstructionError
    data object ComputationalBudgetExceeded : InstructionError
    data object PrivilegeEscalation : InstructionError
    data object ProgramEnvironmentSetupFailure : InstructionError
    data object ProgramFailedToComplete : InstructionError
    data object ProgramFailedToCompile : InstructionError
    data object Immutable : InstructionError
    data object IncorrectAuthority : InstructionError
    data object AccountNotRentExempt : InstructionError
    data object InvalidAccountOwner : InstructionError
    data object ArithmeticOverflow : InstructionError
    data object UnsupportedSysvar : InstructionError
    data object IllegalOwner : InstructionError
    data object MaxAccountsDataAllocationsExceeded : InstructionError
    data object MaxAccountsExceeded : InstructionError
    data object MaxInstructionTraceLengthExceeded : InstructionError
    data object BuiltinProgramsMustConsumeComputeUnits : InstructionError
    data object BailOut : InstructionError

    @Serializable(with = CustomInstructionErrorSerializer::class)
    data class Custom(val code: Long) : InstructionError

    /**
     * One protocol variant with two encodings: older validators tag it with a message, newer ones
     * emit the bare name, which reads back as a null [message].
     */
    @Serializable(with = BorshIoErrorSerializer::class)
    data class BorshIoError(val message: String? = null) : InstructionError

    @Serializable(with = UnknownInstructionErrorSerializer::class)
    data class Unknown(val raw: RawJson) : InstructionError

    companion object {
        /** Every variant the wire encodes as a bare name, in the order the protocol declares them. */
        private val WIRE_NAMES: Map<InstructionError, String> = linkedMapOf(
            GenericError to "GenericError",
            InvalidArgument to "InvalidArgument",
            InvalidInstructionData to "InvalidInstructionData",
            InvalidAccountData to "InvalidAccountData",
            AccountDataTooSmall to "AccountDataTooSmall",
            InsufficientFunds to "InsufficientFunds",
            IncorrectProgramId to "IncorrectProgramId",
            MissingRequiredSignature to "MissingRequiredSignature",
            AccountAlreadyInitialized to "AccountAlreadyInitialized",
            UninitializedAccount to "UninitializedAccount",
            UnbalancedInstruction to "UnbalancedInstruction",
            ModifiedProgramId to "ModifiedProgramId",
            ExternalAccountLamportSpend to "ExternalAccountLamportSpend",
            ExternalAccountDataModified to "ExternalAccountDataModified",
            ReadonlyLamportChange to "ReadonlyLamportChange",
            ReadonlyDataModified to "ReadonlyDataModified",
            DuplicateAccountIndex to "DuplicateAccountIndex",
            ExecutableModified to "ExecutableModified",
            RentEpochModified to "RentEpochModified",
            NotEnoughAccountKeys to "NotEnoughAccountKeys",
            AccountDataSizeChanged to "AccountDataSizeChanged",
            AccountNotExecutable to "AccountNotExecutable",
            AccountBorrowFailed to "AccountBorrowFailed",
            AccountBorrowOutstanding to "AccountBorrowOutstanding",
            DuplicateAccountOutOfSync to "DuplicateAccountOutOfSync",
            InvalidError to "InvalidError",
            ExecutableDataModified to "ExecutableDataModified",
            ExecutableLamportChange to "ExecutableLamportChange",
            ExecutableAccountNotRentExempt to "ExecutableAccountNotRentExempt",
            UnsupportedProgramId to "UnsupportedProgramId",
            CallDepth to "CallDepth",
            MissingAccount to "MissingAccount",
            ReentrancyNotAllowed to "ReentrancyNotAllowed",
            MaxSeedLengthExceeded to "MaxSeedLengthExceeded",
            InvalidSeeds to "InvalidSeeds",
            InvalidRealloc to "InvalidRealloc",
            ComputationalBudgetExceeded to "ComputationalBudgetExceeded",
            PrivilegeEscalation to "PrivilegeEscalation",
            ProgramEnvironmentSetupFailure to "ProgramEnvironmentSetupFailure",
            ProgramFailedToComplete to "ProgramFailedToComplete",
            ProgramFailedToCompile to "ProgramFailedToCompile",
            Immutable to "Immutable",
            IncorrectAuthority to "IncorrectAuthority",
            AccountNotRentExempt to "AccountNotRentExempt",
            InvalidAccountOwner to "InvalidAccountOwner",
            ArithmeticOverflow to "ArithmeticOverflow",
            UnsupportedSysvar to "UnsupportedSysvar",
            IllegalOwner to "IllegalOwner",
            MaxAccountsDataAllocationsExceeded to "MaxAccountsDataAllocationsExceeded",
            MaxAccountsExceeded to "MaxAccountsExceeded",
            MaxInstructionTraceLengthExceeded to "MaxInstructionTraceLengthExceeded",
            BuiltinProgramsMustConsumeComputeUnits to "BuiltinProgramsMustConsumeComputeUnits",
            BailOut to "BailOut",
        )
        private val BY_WIRE_NAME: Map<String, InstructionError> = WIRE_NAMES.entries.associate { it.value to it.key }

        /** Variants carrying no payload, which is every one the wire writes as a bare name. */
        val PAYLOAD_FREE: List<InstructionError> = WIRE_NAMES.keys.toList()

        /** The bare name the wire uses for [error], or null when it carries a payload. */
        fun wireNameOf(error: InstructionError): String? = WIRE_NAMES[error]

        /** The variant [wireName] names, or null when the protocol has added one this library predates. */
        fun fromWireName(wireName: String): InstructionError? = BY_WIRE_NAME[wireName]
    }
}

private const val BORSH_IO_ERROR = "BorshIoError"

object InstructionErrorSerializer : KSerializer<InstructionError> {
    override val descriptor = JsonElement.serializer().descriptor
    override fun deserialize(decoder: Decoder): InstructionError {
        val input = decoder as JsonDecoder
        val value = input.decodeJsonElement()
        if (value is JsonPrimitive && value.isString) {
            if (value.content == BORSH_IO_ERROR) return InstructionError.BorshIoError()
            return InstructionError.fromWireName(value.content) ?: InstructionError.Unknown(RawJson(value.toString()))
        }
        if (value !is JsonObject || value.size != 1) return InstructionError.Unknown(RawJson(value.toString()))
        return when (value.keys.single()) {
            "Custom" -> input.json.decodeFromJsonElement(CustomInstructionErrorSerializer, value)
            "BorshIoError" -> input.json.decodeFromJsonElement(BorshIoErrorSerializer, value)
            else -> InstructionError.Unknown(RawJson(value.toString()))
        }
    }
    override fun serialize(encoder: Encoder, value: InstructionError) {
        when (value) {
            is InstructionError.Custom -> encoder.encodeSerializableValue(CustomInstructionErrorSerializer, value)
            is InstructionError.BorshIoError -> when (value.message) {
                null -> (encoder as JsonEncoder).encodeJsonElement(JsonPrimitive(BORSH_IO_ERROR))
                else -> encoder.encodeSerializableValue(BorshIoErrorSerializer, value)
            }
            is InstructionError.Unknown -> encoder.encodeSerializableValue(UnknownInstructionErrorSerializer, value)
            else -> (encoder as JsonEncoder).encodeJsonElement(
                JsonPrimitive(requireNotNull(InstructionError.wireNameOf(value)) { "Unnamed instruction error $value" }),
            )
        }
    }
}

object CustomInstructionErrorSerializer : TaggedJsonSerializer<InstructionError.Custom>("Custom", MappedSerializer(U32Serializer, InstructionError::Custom, { it.code }))
object BorshIoErrorSerializer : TaggedJsonSerializer<InstructionError.BorshIoError>("BorshIoError", MappedSerializer(String.serializer(), InstructionError::BorshIoError, { requireNotNull(it.message) }))
object UnknownInstructionErrorSerializer : MappedSerializer<RawJson, InstructionError.Unknown>(RawJsonSerializer, InstructionError::Unknown, { it.raw })
