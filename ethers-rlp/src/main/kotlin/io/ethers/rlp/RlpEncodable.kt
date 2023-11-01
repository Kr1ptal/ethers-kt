package io.ethers.rlp

interface RlpEncodable {
    /**
     * Return RLP encoded byte array of this class.
     */
    fun toRlp() = RlpEncoder().apply { rlpEncode(this) }.toByteArray()

    /**
     * Encode this class into provided [RlpEncoder].
     */
    fun rlpEncode(rlp: RlpEncoder)
}
