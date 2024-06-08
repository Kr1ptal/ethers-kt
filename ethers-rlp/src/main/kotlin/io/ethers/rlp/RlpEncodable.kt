package io.ethers.rlp

interface RlpEncodable {
    /**
     * Return RLP encoded byte array of this class.
     */
    fun toRlp(): ByteArray {
        val sizer = RlpSizingEncoder().apply { rlpEncode(this) }
        return RlpBufferEncoder(ByteArray(sizer.size()), isCorrectlySized = true)
            .encode(this)
            .toByteArray()
    }

    /**
     * Encode this class into provided [RlpEncoder].
     */
    fun rlpEncode(rlp: RlpEncoder)
}
