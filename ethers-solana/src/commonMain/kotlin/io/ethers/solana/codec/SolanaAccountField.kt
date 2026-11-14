package io.ethers.solana.codec

import kotlin.jvm.JvmField

/**
 * A field of account type [T], [size] bytes at [offset] into the payload after the discriminator, read as [V].
 *
 * Generated account types declare one for each field whose offset and size are the same in every account, for
 * selecting a slice of the account data with `SolanaAccountSelection` and filtering accounts by value. A field of a
 * fixed-size struct type is a subclass with a descriptor for each field of the struct, and a fixed-size array a
 * [SolanaAccountArrayField] describing each of its elements, so nested fields are reached as paths such as
 * `Fields.rewards[1].amount`, at offsets that include any padding a zero-copy layout places before them.
 *
 * Descriptors are equal when they have the same [name], [offset] and [size], so a path built twice is one field.
 */
open class SolanaAccountField<T, V>(
    @JvmField val name: String,
    @JvmField val offset: Int,
    @JvmField val size: Int,
    private val reader: (BorshReader) -> V,
    private val writer: BorshWriter.(V) -> Unit,
) {
    init {
        require(offset >= 0) { "Offset must not be negative" }
        require(size > 0) { "Size must be positive" }
    }

    /** Read this field from [reader], positioned at its first byte. */
    fun read(reader: BorshReader): V = this.reader(reader)

    /** Write [value] of this field to [writer], as the bytes it takes in the account data. */
    fun write(writer: BorshWriter, value: V) = writer.(this.writer)(value)

    /** Whether this field's bytes lie within [other]'s, such as a field of a struct [other] describes. */
    internal fun isWithin(other: SolanaAccountField<*, *>): Boolean = offset >= other.offset && offset + size <= other.offset + other.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is SolanaAccountField<*, *> && name == other.name && offset == other.offset && size == other.size
    }

    override fun hashCode(): Int = (name.hashCode() * 31 + offset) * 31 + size

    override fun toString(): String = "SolanaAccountField($name, offset=$offset, size=$size)"
}

/**
 * A fixed-size array field of account type [T], read as [V]: [length] elements of [elementSize] bytes each, from
 * [offset]. [get] describes one element, as an [E] at that element's offset.
 */
class SolanaAccountArrayField<T, V, out E : SolanaAccountField<T, *>>(
    name: String,
    offset: Int,
    @JvmField val length: Int,
    @JvmField val elementSize: Int,
    reader: (BorshReader) -> V,
    writer: BorshWriter.(V) -> Unit,
    private val element: (name: String, offset: Int) -> E,
) : SolanaAccountField<T, V>(name, offset, checkedSize(length, elementSize), reader, writer) {
    /**
     * The element at [index], named `name[index]`, at [offset] plus [index] times [elementSize].
     *
     * @throws IndexOutOfBoundsException if [index] is negative or not below [length].
     */
    operator fun get(index: Int): E {
        if (index < 0 || index >= length) throw IndexOutOfBoundsException("Index $index out of bounds for $name of length $length")
        return element("$name[$index]", offset + index * elementSize)
    }

    private companion object {
        fun checkedSize(length: Int, elementSize: Int): Int {
            require(length > 0) { "Length must be positive" }
            require(elementSize > 0) { "Element size must be positive" }
            return length * elementSize
        }
    }
}
