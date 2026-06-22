package io.ethers.rlp

// Ideally, these should be defined as "byte", but java does not support unsigned bytes.
// Could also use kotlin's UByte, but that would require more casting for comparisons.
internal const val RLP_STRING_SHORT = 0x80 // range [0x80, 0xb7]
internal const val RLP_STRING_LONG = 0xb7 // range [0xb8, 0xbf]
internal const val RLP_LIST_SHORT = 0xc0 // range [0xc0, 0xf7]
internal const val RLP_LIST_LONG = 0xf7 // range [0xf8, 0xff]

internal const val RLP_NULL = RLP_STRING_SHORT
internal const val MAX_SHORT_LENGTH = 55
