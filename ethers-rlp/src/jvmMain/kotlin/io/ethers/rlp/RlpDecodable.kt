package io.ethers.rlp

interface RlpDecodable<T> {
    /**
     * Decode RLP-encoded [data] and return instance of [T], or null if decoding fails.
     */
    fun rlpDecode(data: ByteArray): T? {
        if (data.isEmpty()) {
            return null
        }

        return rlpDecode(RlpDecoder(data))
    }

    /**
     * Decode [rlp] into an instance of [T], or null if decoding fails.
     */
    fun rlpDecode(rlp: RlpDecoder): T?
}
