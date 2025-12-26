package io.ethers.json

import java.io.InputStream

/**
 * Interface for encoding and decoding JSON.
 *
 * This abstraction allows swapping JSON implementations without affecting the rest of the codebase.
 */
interface JsonCodec {
    /**
     * Encode an object to a JSON string.
     */
    fun <T> encode(value: T): String

    /**
     * Encode an object to a JSON byte array.
     */
    fun <T> encodeToBytes(value: T): ByteArray

    /**
     * Decode a JSON string to an object of the specified type.
     */
    fun <T> decode(json: String, type: Class<T>): T

    /**
     * Decode a JSON byte array to an object of the specified type.
     */
    fun <T> decode(json: ByteArray, type: Class<T>): T

    /**
     * Decode a JSON input stream to an object of the specified type.
     */
    fun <T> decode(json: InputStream, type: Class<T>): T
}
