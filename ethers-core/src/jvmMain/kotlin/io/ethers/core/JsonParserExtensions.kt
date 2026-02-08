package io.ethers.core

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import io.ethers.core.types.Address
import io.ethers.core.types.Bloom
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import java.math.BigInteger

/**
 * Check if end of JSON object is reached.
 */
fun JsonParser.isNextTokenObjectEnd(): Boolean {
    val token = nextToken()
    return token == null || token == JsonToken.END_OBJECT
}

/**
 * Check if end of JSON array is reached.
 */
fun JsonParser.isNextTokenArrayEnd(): Boolean {
    val token = nextToken()
    return token == null || token == JsonToken.END_ARRAY
}

/**
 * Iterate over JSON objects and use [parseObjectValue] to read object value.
 */
inline fun JsonParser.forEachObjectField(parseObjectValue: (fieldName: String) -> Unit) {
    while (!isNextTokenObjectEnd()) {
        val field = currentName()
        nextToken()
        parseObjectValue(field)
    }
}

/**
 * Iterate over JSON array elements and use [parseArrayValue] to read array values.
 */
inline fun JsonParser.forEachArrayElement(parseArrayValue: () -> Unit) {
    while (!isNextTokenArrayEnd()) {
        parseArrayValue()
    }
}

/**
 * Verify if current field name equals provided [name]. If the names match, it advances to next token so callers can
 * immediately parse the value.
 * */
fun JsonParser.isField(name: String): Boolean {
    if (currentName() != name) {
        return false
    }
    nextToken()
    return true
}

/**
 * Execute [block], returning null if current token is null.
 */
inline fun JsonParser.ifNotNull(block: () -> Unit) {
    if (currentToken == JsonToken.VALUE_NULL) {
        return
    }
    block()
}

/**
 * Use [parseValue] to read current value, returning null if current token is null.
 */
inline fun <R> JsonParser.readOrNull(parseValue: JsonParser.() -> R): R? {
    if (currentToken == JsonToken.VALUE_NULL) {
        return null
    }
    return parseValue(this)
}

/**
 * Read current token as [clazz], returning null if current token is null.
 */
fun <R : Any> JsonParser.readValueOrNull(clazz: Class<R>): R? {
    return if (currentToken() == JsonToken.VALUE_NULL) {
        nextToken()
        null
    } else {
        readValueAs(clazz)
    }
}

/**
 * Read current token in hex as [Address].
 */
fun JsonParser.readAddress(): Address {
    val arr = readHexByteArray()
    if (arr.isEmpty()) {
        return Address.ZERO
    }
    return Address(arr)
}

/**
 * Read current token in hex as [Bytes].
 */
fun JsonParser.readBytes(): Bytes {
    val arr = readHexByteArray()
    if (arr.isEmpty()) {
        return Bytes.EMPTY
    }
    return Bytes(arr)
}

/**
 * Read current token in hex as [Bytes], or return null if hex string is empty.
 */
fun JsonParser.readBytesEmptyAsNull(): Bytes? {
    val bytes = readHexByteArray()
    if (bytes.isEmpty()) {
        return null
    }
    return Bytes(bytes)
}

/**
 * Read current token in hex as [Bloom].
 */
fun JsonParser.readBloom() = Bloom(readHexByteArray())

/**
 * Read current token in hex as [Hash].
 */
fun JsonParser.readHash() = Hash(readHexByteArray())

/**
 * Read current token in hex as to unsigned [BigInteger].
 */
fun JsonParser.readHexBigInteger(): BigInteger {
    val decoded = readHexByteArray()
    if (decoded.isEmpty()) {
        return BigInteger.ZERO
    }

    // hex numbers are unsigned
    return BigInteger(1, decoded)
}

/**
 * Read current token as hex or numeric [Long].
 * */
fun JsonParser.readAnyLong(): Long {
    if (isHexValue()) {
        return readHexLong()
    }
    return valueAsLong
}

/**
 * Read current token in hex as [Long].
 */
fun JsonParser.readHexLong() = readHexByteArray().toLong()

private fun ByteArray.toLong(): Long {
    if (isEmpty()) {
        return 0L
    }
    var value = 0L
    for (b in this) {
        value = (value shl 8) + (b.toInt() and 255)
    }
    return value
}

/**
 * Read current token in hex as [Int].
 */
fun JsonParser.readHexInt() = readHexByteArray().toInt()

private fun ByteArray.toInt(): Int {
    if (isEmpty()) {
        return 0
    }
    var value = 0
    for (b in this) {
        value = (value shl 8) + (b.toInt() and 255)
    }
    return value
}

/**
 * Read JSON array of hex tokens as list of [Hash].
 *
 */
fun JsonParser.readListOfHashes() = readListOf { readHash() }

/**
 * Read JSON array of hex tokens as list of [clazz] elements.
 */
fun <T> JsonParser.readListOf(clazz: Class<T>) = readListOf { readValueAs(clazz) }

/**
 * Read JSON array of tokens as list of [R] elements decoded by [decoder], returning empty [List] if current token is null.
 */
inline fun <R> JsonParser.readListOf(decoder: JsonParser.() -> R): List<R> {
    if (currentToken == JsonToken.VALUE_NULL) {
        return emptyList()
    }

    if (nextToken() == JsonToken.END_ARRAY) {
        return emptyList()
    }

    val ret = ArrayList<R>()
    do {
        ret.add(decoder(this))
    } while (nextToken() != JsonToken.END_ARRAY)

    return ret
}

/**
 * Read JSON map using custom [keyParser] to extract keys and convert values to [valueClass], returning
 * empty [Map] if current token is null.
 *
 * Since keys are deserialized from field names, they are always strings and need a special parser.
 * If using annotations, must implement and set [com.fasterxml.jackson.databind.KeyDeserializer] as key deserializer.
 * */
inline fun <K, V> JsonParser.readMapOf(keyParser: (String) -> K, valueClass: Class<V>): Map<K, V> {
    if (currentToken == JsonToken.VALUE_NULL) {
        return emptyMap()
    }

    if (nextToken() == JsonToken.END_OBJECT) {
        return emptyMap()
    }

    val ret = HashMap<K, V>()
    do {
        ret[keyParser(currentName())] = nextToken().run { readValueAs(valueClass) }
    } while (!isNextTokenObjectEnd())

    return ret
}

/**
 * Read JSON map using custom [keyParser] to extract keys and custom [valueParser] to convert values, returning
 * empty [Map] if current token is null.
 *
 * Since keys are deserialized from field names, they are always strings and need a special parser.
 * If using annotations, must implement and set [com.fasterxml.jackson.databind.KeyDeserializer] as key deserializer.
 * */
inline fun <K, V> JsonParser.readMapOf(keyParser: (String) -> K, valueParser: JsonParser.() -> V): Map<K, V> {
    if (currentToken == JsonToken.VALUE_NULL) {
        return emptyMap()
    }

    if (nextToken() == JsonToken.END_OBJECT) {
        return emptyMap()
    }

    val ret = HashMap<K, V>()
    do {
        ret[keyParser(currentName())] = nextToken().run { valueParser() }
    } while (!isNextTokenObjectEnd())

    return ret
}

private val EMPTY_BYTES = ByteArray(0)

/**
 * Uses most optimal way to read and decode hex string from [JsonParser].
 * */
fun JsonParser.readHexByteArray(): ByteArray {
    if (!hasTextCharacters()) {
        val text = this.text
        if (text.isEmpty()) {
            return EMPTY_BYTES
        }
        if (text.length == 2 && (text == "0x" || text == "0X")) {
            return EMPTY_BYTES
        }
        return FastHex.decode(text)
    }

    val len = textLength
    if (len == 0) {
        return EMPTY_BYTES
    }

    val chars = textCharacters
    val offset = textOffset
    if (len == 2 && chars[offset] == '0' && (chars[offset + 1] == 'x' || (chars[offset + 1] == 'X'))) {
        return EMPTY_BYTES
    }

    return FastHex.decode(chars, offset, len)
}

/**
 * Return true if current token is hex string, false otherwise.
 * */
fun JsonParser.isHexValue(): Boolean {
    if (!hasTextCharacters()) {
        val text = this.text
        return text.length >= 2 && text[0] == '0' && (text[1] == 'x' || text[1] == 'X')
    }

    val len = textLength
    if (len == 0) {
        return false
    }

    val chars = textCharacters
    val offset = textOffset
    return len >= 2 && chars[offset] == '0' && (chars[offset + 1] == 'x' || (chars[offset + 1] == 'X'))
}

/**
 * Initialize [JsonParser] for reading.
 *
 * First must point to a token; if not pointing to one, advance. This occurs before first read from JsonParser,
 * as well as after clearing of current token.
 */
fun JsonParser.initForReading(): JsonParser {
    var t = currentToken
    if (t == null) {
        t = nextToken()
        if (t == null) {
            throw MismatchedInputException.from(this, null as Class<*>?, "No content to map due to end-of-input")
        }
    }
    return this
}

private val STRICT_MODE = System.getenv("ETHERS_JSON_STRICT_MODE") == "true"

/**
 * Handle unknown field by either skipping (default) or throwing exception. Strict mode is enabled by setting
 * environment variable `ETHERS_JSON_STRICT_MODE=true`.
 * */
fun JsonParser.handleUnknownField() {
    if (STRICT_MODE) {
        throw IllegalArgumentException("Unknown field ${currentName()}")
    }
    skipChildren()
}
