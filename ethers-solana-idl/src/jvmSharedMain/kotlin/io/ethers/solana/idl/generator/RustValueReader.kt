package io.ethers.solana.idl.generator

/**
 * Reads the value of an IDL constant, which Anchor records as the Rust `Debug` output of the constant's expression,
 * such as `[1, 2, 3]`, `Some("abc")`, `Point { x: 1, y: -2 }` or `Variant(5)`. Legacy IDLs parsed from source record
 * the expression's token text instead, which has the same shape for literals but spaces between tokens, such as
 * `Some (5)`, so whitespace between tokens is ignored.
 *
 * The value is split into punctuation (`[ ] ( ) { } , :`), string literals and words: maximal runs of any other
 * characters, such as `-12`, `1.5e-7`, `None`, a variant name or a base58 address. What a word must be is decided by
 * the declared type the caller reads it as, so an expression such as `10 * 2`, a cast or a reference to another
 * constant fails as an unexpected word.
 *
 * Every error is an [IllegalArgumentException] prefixed with [where].
 */
internal class RustValueReader(private val text: String, private val where: String) {
    private class Token(val kind: Kind, val value: String, val start: Int, val end: Int)

    private enum class Kind { PUNCT, STRING, WORD }

    private val tokens = tokenize()
    private var index = 0

    /** Whether the next token is [char]. */
    fun atPunct(char: Char): Boolean = tokens.getOrNull(index)?.let { it.kind == Kind.PUNCT && it.value[0] == char } ?: false

    /** Consume the next token if it is [char]. */
    fun tryPunct(char: Char): Boolean = atPunct(char).also { if (it) index++ }

    /** Consume [char], or fail. */
    fun punct(char: Char) {
        if (!tryPunct(char)) unexpected("'$char'")
    }

    /** Consume the next word, or fail describing it as [expected]. */
    fun word(expected: String): String {
        val token = tokens.getOrNull(index)
        if (token == null || token.kind != Kind.WORD) unexpected(expected)
        index++
        return token.value
    }

    /** Consume the next string literal, unescaped, or fail. */
    fun string(): String {
        val token = tokens.getOrNull(index)
        if (token == null || token.kind != Kind.STRING) unexpected("a string literal")
        index++
        return token.value
    }

    /** Fail unless every token was consumed. */
    fun end() {
        if (index < tokens.size) unexpected("the end of the value")
    }

    /** Fail at the last consumed token, or the start. */
    fun fail(message: String): Nothing = throw IllegalArgumentException("$where: $message, in value `$text`")

    /** Fail with the next token being unexpected where [expected] should be. */
    fun unexpected(expected: String): Nothing {
        val token = tokens.getOrNull(index)
        val got = if (token == null) "the end of the value" else "'${text.substring(token.start, token.end)}'"
        throw IllegalArgumentException(
            "$where: expected $expected but got $got at offset ${token?.start ?: text.length} of value `$text`; " +
                "only literal values are supported, not expressions, casts or references to other constants",
        )
    }

    private fun tokenize(): List<Token> {
        val tokens = ArrayList<Token>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c in PUNCTUATION -> tokens.add(Token(Kind.PUNCT, c.toString(), i, ++i))
                c == '"' -> {
                    val start = i
                    val value = StringBuilder()
                    i++
                    while (true) {
                        if (i >= text.length) throw IllegalArgumentException("$where: unterminated string literal at offset $start of value `$text`")
                        val s = text[i++]
                        when (s) {
                            '"' -> break
                            '\\' -> i = escape(i, value)
                            else -> value.append(s)
                        }
                    }
                    tokens.add(Token(Kind.STRING, value.toString(), start, i))
                }
                else -> {
                    val start = i
                    while (i < text.length && !text[i].isWhitespace() && text[i] !in PUNCTUATION && text[i] != '"') i++
                    tokens.add(Token(Kind.WORD, text.substring(start, i), start, i))
                }
            }
        }
        return tokens
    }

    /**
     * Append the escape whose character follows a backslash at [start] to [out], returning the index after it. Only
     * Rust's string escapes are accepted: the ones `Debug` writes (`\n \r \t \\ \0 \" \u{…}`) and `\'` and `\xNN`.
     */
    private fun escape(start: Int, out: StringBuilder): Int {
        fun invalid(): Nothing = throw IllegalArgumentException("$where: unsupported escape at offset ${start - 1} of value `$text`")
        if (start >= text.length) invalid()
        when (text[start]) {
            'n' -> out.append('\n')
            'r' -> out.append('\r')
            't' -> out.append('\t')
            '\\' -> out.append('\\')
            '0' -> out.append('\u0000')
            '"' -> out.append('"')
            '\'' -> out.append('\'')
            'x' -> {
                val code = text.substring(start + 1, minOf(start + 3, text.length)).takeIf { it.length == 2 }?.toIntOrNull(16)
                if (code == null || code > 0x7f) invalid()
                out.append(code.toChar())
                return start + 3
            }
            'u' -> {
                val close = text.indexOf('}', start)
                if (start + 1 >= text.length || text[start + 1] != '{' || close < 0) invalid()
                val digits = text.substring(start + 2, close).replace("_", "")
                val code = digits.takeIf { it.length in 1..6 }?.toIntOrNull(16)
                if (code == null || code > 0x10FFFF || code in 0xD800..0xDFFF) invalid()
                out.appendCodePoint(code)
                return close + 1
            }
            else -> invalid()
        }
        return start + 1
    }

    private companion object {
        const val PUNCTUATION = "[](){},:"
    }
}
