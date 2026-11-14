package io.ethers.solana.idl.generator

import com.squareup.kotlinpoet.ClassName
import java.io.Serializable
import java.util.TreeMap

/**
 * Generator configuration for one program, beyond what its IDL declares. Every target is named as the IDL names it,
 * and [SolanaProgramBuilder] rejects targets the IDL does not declare.
 *
 * - [customCodecs]: IDL type name to a handwritten codec. The type's Kotlin class is still generated from the IDL, but
 *   its companion delegates reading and writing to the codec. This is how types declared with custom serialization
 *   generate.
 * - [typeSubstitutions]: IDL type name to an existing Kotlin type and its codec. The type gets no generated class, and
 *   every use of it takes the substituted type.
 * - [renames]: a declaration target, `<kind>:<path>`, to the Kotlin name it takes, see [RENAME_KINDS].
 * - [accountDefaults]: `<instruction>.<account path>` to the base58 address an instruction account's parameter takes
 *   by default. The path descends through account groups with `.`, e.g. `swap.vaults.input`.
 *
 * Maps are kept sorted by target, so equal configurations compare, hash and serialize alike in any insertion order,
 * and generation never depends on it.
 */
class GeneratorOverrides @JvmOverloads constructor(
    customCodecs: Map<String, CustomCodec> = emptyMap(),
    typeSubstitutions: Map<String, TypeSubstitution> = emptyMap(),
    renames: Map<String, String> = emptyMap(),
    accountDefaults: Map<String, String> = emptyMap(),
) : Serializable {
    val customCodecs: Map<String, CustomCodec> = TreeMap(customCodecs)
    val typeSubstitutions: Map<String, TypeSubstitution> = TreeMap(typeSubstitutions)
    val renames: Map<String, String> = TreeMap(renames)
    val accountDefaults: Map<String, String> = TreeMap(accountDefaults)

    /** Whether this configures nothing. */
    fun isEmpty(): Boolean = customCodecs.isEmpty() && typeSubstitutions.isEmpty() && renames.isEmpty() && accountDefaults.isEmpty()

    /** These overrides with [type] read and written by [codec]. */
    fun withCustomCodec(type: String, codec: CustomCodec) = merge(GeneratorOverrides(customCodecs = mapOf(type to codec)))

    /** These overrides with [type] replaced by [substitution]. */
    fun withTypeSubstitution(type: String, substitution: TypeSubstitution) = merge(GeneratorOverrides(typeSubstitutions = mapOf(type to substitution)))

    /** These overrides with [target] renamed to [name]. */
    fun withRename(target: String, name: String) = merge(GeneratorOverrides(renames = mapOf(target to name)))

    /** These overrides with the instruction account at [target] defaulting to [address]. */
    fun withAccountDefault(target: String, address: String) = merge(GeneratorOverrides(accountDefaults = mapOf(target to address)))

    /**
     * The overrides of both this and [other]. A target both configure must be configured alike in both.
     *
     * @throws IllegalArgumentException if a target is configured differently in each.
     */
    fun merge(other: GeneratorOverrides): GeneratorOverrides = GeneratorOverrides(
        merge("custom codec", customCodecs, other.customCodecs),
        merge("type substitution", typeSubstitutions, other.typeSubstitutions),
        merge("rename", renames, other.renames),
        merge("account default", accountDefaults, other.accountDefaults),
    )

    override fun equals(other: Any?): Boolean = other is GeneratorOverrides &&
        customCodecs == other.customCodecs &&
        typeSubstitutions == other.typeSubstitutions &&
        renames == other.renames &&
        accountDefaults == other.accountDefaults

    override fun hashCode(): Int = listOf(customCodecs, typeSubstitutions, renames, accountDefaults).hashCode()

    override fun toString(): String = "GeneratorOverrides(customCodecs=$customCodecs, typeSubstitutions=$typeSubstitutions, renames=$renames, accountDefaults=$accountDefaults)"

    /**
     * A handwritten codec for an IDL type whose class is generated: [codec] is the fully qualified name of a Kotlin
     * `object` implementing `SolanaCodec<T>`, where `T` is the generated class. Package segments start in lower case
     * and class segments in upper case, e.g. `com.example.Codecs.Compressed`.
     *
     * The generator cannot see what the codec writes, so it relies on what is declared here:
     * - [size]: the number of bytes the codec reads and writes for every value. Declare it only if it holds for every
     *   value. Without it the type has a variable size: account fields after it have no known offset, so get no
     *   filter method or slice descriptor, and it cannot be a field of a zero-copy type.
     * - [align]: its alignment, which a field of an unpacked `#[repr(C)]` zero-copy type needs. Requires [size].
     *
     * Where the IDL lays the type out itself (Borsh, or a supported zero-copy layout), declared values must match it.
     */
    data class CustomCodec @JvmOverloads constructor(
        val codec: String,
        val size: Int? = null,
        val align: Int? = null,
    ) : Serializable {
        init {
            requireClassName(codec, "codec")
            requireLayout(size, align)
        }

        private companion object {
            private const val serialVersionUID = 1L
        }
    }

    /**
     * An existing Kotlin type in place of an IDL type: [type] is the fully qualified name of a Kotlin class without
     * type parameters, and [codec] that of a Kotlin `object` implementing `SolanaCodec<type>`, named as for
     * [CustomCodec.codec]. [size] and [align] are declared as for a [CustomCodec].
     */
    data class TypeSubstitution @JvmOverloads constructor(
        val type: String,
        val codec: String,
        val size: Int? = null,
        val align: Int? = null,
    ) : Serializable {
        init {
            requireClassName(type, "type")
            requireClassName(codec, "codec")
            requireLayout(size, align)
        }

        private companion object {
            private const val serialVersionUID = 1L
        }
    }

    companion object {
        private const val serialVersionUID = 1L

        /**
         * Parse overrides for any number of programs from [json], an object keyed by the program name each IDL
         * declares:
         *
         * ```json
         * {
         *   "showcase": {
         *     "customCodecs": { "Compressed": { "codec": "com.example.CompressedCodec", "size": 8 } },
         *     "typeSubstitutions": { "Amount": { "type": "com.example.Amount", "codec": "com.example.AmountCodec", "size": 8, "align": 8 } },
         *     "renames": { "type:Mode": "SwapMode", "instructionAccount:swap.vaults.input": "inputVault" },
         *     "accountDefaults": { "swap.referrer": "<base58 address>" }
         *   }
         * }
         * ```
         *
         * Every section is optional, and unknown keys are rejected. A JSON object must not name a key twice: the
         * parser keeps only the last.
         *
         * @throws IllegalArgumentException if [json] is not of this form, or an entry is invalid on its own.
         */
        @JvmStatic
        fun parse(json: String): Map<String, GeneratorOverrides> = parseGeneratorOverrides(json)

        /** [left] and [right], merging the overrides of a program both configure, see [merge]. */
        @JvmStatic
        fun mergeAll(left: Map<String, GeneratorOverrides>, right: Map<String, GeneratorOverrides>): Map<String, GeneratorOverrides> {
            val merged = TreeMap(left)
            for ((program, overrides) in right) {
                merged[program] = try {
                    merged[program]?.merge(overrides) ?: overrides
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("program '$program': ${e.message}", e)
                }
            }
            return merged
        }

        /**
         * The kinds of rename target, each `<kind>:<path>` with the path as the IDL names it:
         * - `type:<Type>`, `account:<Account>`: a type's class; an account's class is its type's.
         * - `field:<Type>.<field>`, `field:<Type>.<Variant>.<field>`: a field of a struct, or of an enum variant. A
         *   tuple field is named by its index, e.g. `field:<Type>.0`. An event's fields are its type's.
         * - `variant:<Type>.<Variant>`: an enum variant's class.
         * - `instruction:<instruction>`: the base name `Name` of an instruction, from which its `NameArgs`,
         *   `NameAccounts` and `Instruction.Name` classes, `name` builder and `NAME` descriptor follow.
         * - `argument:<instruction>.<argument>`: an instruction argument.
         * - `instructionAccount:<instruction>.<account path>`: an instruction account's property, or an account
         *   group's property and class; the path descends through groups with `.`.
         * - `event:<Event>`, `error:<Error>`, `constant:<CONSTANT>`.
         */
        @JvmField
        val RENAME_KINDS: List<String> = listOf(
            "type",
            "account",
            "field",
            "variant",
            "instruction",
            "argument",
            "instructionAccount",
            "event",
            "error",
            "constant",
        )

        private fun <V> merge(kind: String, a: Map<String, V>, b: Map<String, V>): Map<String, V> {
            val merged = TreeMap(a)
            for ((target, value) in b) {
                val previous = merged.put(target, value)
                require(previous == null || previous == value) { "conflicting $kind for '$target': $previous and $value" }
            }
            return merged
        }

        /** The Kotlin class [name] names, e.g. `com.example.Outer.Inner`. */
        internal fun className(name: String): ClassName = ClassName.bestGuess(name)

        private fun requireClassName(name: String, what: String) {
            val valid = name.split('.').all { isKotlinIdentifier(it) } &&
                runCatching { className(name) }.getOrNull().let { it != null && it.canonicalName == name && it.packageName.isNotEmpty() }
            require(valid) { "$what '$name' is not a fully qualified Kotlin class name, e.g. com.example.Name" }
        }

        private fun requireLayout(size: Int?, align: Int?) {
            require(size == null || size >= 0) { "size $size must not be negative" }
            require(align == null || size != null) { "align $align requires a size" }
            require(align == null || (align > 0 && align and (align - 1) == 0)) { "align $align must be a positive power of two" }
        }
    }
}

private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")

/** Kotlin's hard keywords, which cannot name a declaration without backticks. */
private val HARD_KEYWORDS = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null",
    "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var", "when",
    "while",
)

/** Whether [name] can name a Kotlin declaration as is. */
internal fun isKotlinIdentifier(name: String): Boolean = IDENTIFIER.matches(name) && name !in HARD_KEYWORDS && name.any { it != '_' }
