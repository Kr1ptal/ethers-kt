package io.ethers.rlp

interface RlpEncodable {
    /**
     * Return RLP encoded byte array of this class.
     */
    fun toRlp(): ByteArray {
        val encoder = RlpEncoder(rlpEncodedSize())
        return encoder.encode(this).toByteArray()
    }

    fun rlpEncodedSize(): Int

    /**
     * Encode this class into provided [RlpEncoder].
     */
    fun rlpEncode(rlp: RlpEncoder)
}
