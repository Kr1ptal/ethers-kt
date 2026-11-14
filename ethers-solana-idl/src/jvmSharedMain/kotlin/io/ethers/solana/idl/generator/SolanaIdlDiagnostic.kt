package io.ethers.solana.idl.generator

import java.io.Serializable

/**
 * A declaration of an IDL that [SolanaProgramBuilder] does not generate bindings for, and why.
 *
 * A declaration is either unsupported itself, or [dependsOn] one that is: a type containing an unsupported type, or
 * an account, event, constant or instruction using one, is left out with it. Its [support] is then that of the
 * declaration it depends on.
 *
 * @property kind the kind of declaration.
 * @property name the declaration's name in the IDL.
 * @property support why the declaration is not supported.
 * @property message what is not supported, naming the declaration, such as `type 'Pool' field 'status': ...`.
 * @property dependsOn the unsupported declaration this one depends on, such as `type 'Status'`, or null when this one
 * is unsupported itself.
 */
data class SolanaIdlDiagnostic @JvmOverloads constructor(
    val kind: Kind,
    val name: String,
    val support: Support,
    val message: String,
    val dependsOn: String? = null,
) : Serializable {
    enum class Kind(val label: String) {
        TYPE("type"),
        CONSTANT("constant"),
        ACCOUNT("account"),
        EVENT("event"),
        INSTRUCTION("instruction"),
    }

    enum class Support(val label: String) {
        /** The generator does not implement this form yet. */
        NOT_SUPPORTED_YET("not supported yet"),

        /** The IDL cannot describe the declaration's wire format, so no bindings are generated for it by design. */
        INTENTIONALLY_UNSUPPORTED("intentionally unsupported"),

        /** The declaration is not one a program can have: a layout Rust rejects or that contradicts its serialization, or a value outside its type. */
        INVALID("invalid"),
    }

    /** The [message] and how the declaration is supported, on one line. */
    override fun toString(): String = "$message [${support.label}]"

    private companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Thrown by a strict [SolanaProgramBuilder] when [program] has declarations it cannot generate, listing all their
 * [diagnostics].
 */
class SolanaIdlUnsupportedException(val program: String, val diagnostics: List<SolanaIdlDiagnostic>) :
    IllegalArgumentException(describe(program, diagnostics)) {
    companion object {
        /** A message listing [diagnostics] of [program], one per line. */
        @JvmStatic
        fun describe(program: String, diagnostics: List<SolanaIdlDiagnostic>): String = "program '$program': ${diagnostics.size} declaration(s) cannot be generated:\n" + diagnostics.joinToString("\n") { "- $it" }
    }
}
