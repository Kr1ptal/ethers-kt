package io.ethers.rlp

interface RlpEncodable {
    /**
     * Return RLP encoded byte array of this class.
     */
    fun toRlp(): ByteArray {
        return RlpEncoder(rlpSize(), isExactSize = true)
            .encode(this)
            .toByteArray()
    }

    /**
     * Return the RLP-encoded size of this class, without actually encoding it.
     * */
    fun rlpSize(): Int

    /**
     * Encode this class into provided [RlpEncoder].
     */
    fun rlpEncode(rlp: RlpEncoder)
}
