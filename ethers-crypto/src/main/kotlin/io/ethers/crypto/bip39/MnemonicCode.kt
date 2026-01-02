package io.ethers.crypto.bip39

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA512
import io.ethers.crypto.Hashing

/**
 * Mnemonic code for generating deterministic keys.
 *
 * Implementation of [BIP-0039](https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki).
 * */
class MnemonicCode @JvmOverloads constructor(
    val words: List<String>,
    val wordList: MnemonicWordList = MnemonicWordListEnglish,
) {
    /**
     * Create a mnemonic code from a string of words, separated by provided [MnemonicWordList.separator].
     *
     * @param wordString String of words, separated by provided [MnemonicWordList.separator].
     * @param wordList Word list to use. Default is [MnemonicWordListEnglish].
     * */
    @JvmOverloads
    constructor(
        wordString: String,
        wordList: MnemonicWordList = MnemonicWordListEnglish,
    ) : this(wordString.split(wordList.separator), wordList)

    init {
        if (words.isEmpty()) {
            throw IllegalArgumentException("Word list is empty.")
        }
        if (words.size % 3 > 0) {
            throw IllegalArgumentException("Word list size must be multiple of three words.")
        }

        // validates entropy - will throw if invalid
        getEntropy()
    }

    /**
     * Create a binary seed from the mnemonic. We use the PBKDF2 function with a mnemonic sentence (in UTF-8 NFKD) used
     * as the password and the string "mnemonic" + passphrase (again in UTF-8 NFKD) used as the salt. The iteration
     * count is set to 2048 and HMAC-SHA512 is used as the pseudo-random function. The length of the derived key
     * is 512 bits (= 64 bytes).
     *
     * This seed can be later used to generate deterministic wallets using BIP-32 or similar methods.
     *
     * @param passphrase Optional passphrase. If not present, an empty string will be used.
     * */
    @JvmOverloads
    fun getSeed(passphrase: String = ""): ByteArray {
        val input = words.joinToString(separator = wordList.separator.toString()).toByteArray(Charsets.UTF_8)
        val salt = "mnemonic$passphrase".toByteArray(Charsets.UTF_8)

        val secretDerivation = CryptographyProvider.Default
            .get(PBKDF2)
            .secretDerivation(
                digest = SHA512,
                iterations = SEED_ITERATIONS,
                outputSize = SEED_KEY_SIZE.bytes,
                salt = salt,
            )

        return secretDerivation.deriveSecretBlocking(input).toByteArray()
    }

    /**
     * Convert mnemonic word list to original entropy value.
     */
    fun getEntropy(): ByteArray {
        // Look up all the words in the list and construct the
        // concatenation of the original entropy and the checksum.
        val concatLenBits = words.size * 11
        val concatBits = BooleanArray(concatLenBits)
        for ((wordIndex, word) in words.withIndex()) {
            // Find the words index in the wordlist.
            val ndx = wordList.words.binarySearch(word)
            if (ndx < 0) {
                throw RuntimeException("Invalid mnemonic word '$word' for provided word list '$wordList'")
            }

            // Set the next 11 bits to the value of the index.
            for (ii in 0..10) {
                concatBits[(wordIndex * 11) + ii] = (ndx and (1 shl (10 - ii))) != 0
            }
        }
        val checksumLengthBits = concatLenBits / 33
        val entropyLengthBits = concatLenBits - checksumLengthBits

        // Extract original entropy as bytes.
        val entropy = ByteArray(entropyLengthBits / 8)
        for (ii in entropy.indices) {
            for (jj in 0..7) {
                if (concatBits[(ii * 8) + jj]) {
                    entropy[ii] = (entropy[ii].toInt() or (1 shl (7 - jj))).toByte()
                }
            }
        }

        // Take the digest of the entropy.
        val hash = Hashing.sha256(entropy)
        val hashBits = bytesToBits(hash)

        // Check all the checksum bits.
        for (i in 0 until checksumLengthBits) {
            if (concatBits[entropyLengthBits + i] != hashBits[i]) {
                throw RuntimeException("Checksum mismatch, invalid mnemonic")
            }
        }
        return entropy
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MnemonicCode

        return words == other.words
    }

    override fun hashCode(): Int {
        return words.hashCode()
    }

    companion object {
        private const val SEED_ITERATIONS = 2048
        private const val SEED_KEY_SIZE = 64 // 512 bits

        /**
         * Create a new mnemonic code from random entropy.
         *
         * @param bitsOfEntropy The number of bits of entropy (128, 160, 192, 224, or 256).
         * @param wordList Word list to use. Default is [MnemonicWordListEnglish].
         */
        @JvmStatic
        @JvmOverloads
        fun fromRandomEntropy(
            bitsOfEntropy: Int = 256,
            wordList: MnemonicWordList = MnemonicWordListEnglish,
        ): MnemonicCode {
            val entropy = Hashing.generateRandomBytes(bitsOfEntropy / 8)
            return fromEntropy(entropy, wordList)
        }

        /**
         * Convert entropy data to mnemonic word list. Valid entropy lengths are 128-256 bits, in 32 bit increments.
         */
        @JvmStatic
        @JvmOverloads
        fun fromEntropy(entropy: ByteArray, wordList: MnemonicWordList = MnemonicWordListEnglish): MnemonicCode {
            if (entropy.isEmpty()) {
                throw IllegalArgumentException("Entropy is empty.")
            }
            if (entropy.size % 4 > 0) {
                throw IllegalArgumentException("Entropy length not multiple of 32 bits.")
            }
            if (entropy.size < 16) {
                throw IllegalArgumentException("Entropy length too short, must be at least 128 bits.")
            }
            if (entropy.size > 32) {
                throw IllegalArgumentException("Entropy length too long, must be at most 256 bits.")
            }

            // We take initial entropy of ENT bits and compute its
            // checksum by taking first ENT / 32 bits of its SHA256 hash.
            val hash = Hashing.sha256(entropy)
            val hashBits = bytesToBits(hash)
            val entropyBits = bytesToBits(entropy)
            val checksumLengthBits = entropyBits.size / 32

            // We append these bits to the end of the initial entropy.
            val concatBits = BooleanArray(entropyBits.size + checksumLengthBits)
            entropyBits.copyInto(concatBits)
            hashBits.copyInto(
                concatBits,
                destinationOffset = entropyBits.size,
                startIndex = 0,
                endIndex = checksumLengthBits,
            )

            // Next we take these concatenated bits and split them into
            // groups of 11 bits. Each group encodes number from 0-2047
            // which is a position in a wordlist.  We convert numbers into
            // words and use joined words as mnemonic sentence.
            val words = ArrayList<String>()
            val nwords = concatBits.size / 11
            for (i in 0 until nwords) {
                var index = 0
                for (j in 0..10) {
                    index = index shl 1
                    if (concatBits[(i * 11) + j]) {
                        index = index or 0x1
                    }
                }
                words.add(wordList[index])
            }

            return MnemonicCode(words)
        }

        private fun bytesToBits(data: ByteArray): BooleanArray {
            val bits = BooleanArray(data.size * 8)
            for (i in data.indices) {
                for (j in 0..7) {
                    bits[(i * 8) + j] = (data[i].toInt() and (1 shl (7 - j))) != 0
                }
            }
            return bits
        }
    }
}
