package io.ethers.crypto.bip39

import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.jcajce.provider.digest.SHA256
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Collections

/**
 * Mnemonic code for generating deterministic keys.
 *
 * Implementation of [BIP-0039](https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki).
 * */
class MnemonicCode(val words: List<String>) {
    /**
     * Create a mnemonic code from a string of words, separated by provided [MnemonicWordList.separator].
     *
     * @param wordString String of words, separated by provided [MnemonicWordList.separator].
     * @param wordList Word list to use. Default is [MnemonicWordListEnglish].
     * */
    constructor(wordString: String, wordList: MnemonicWordList = MnemonicWordListEnglish) : this(wordString.split(wordList.separator))

    init {
        if (words.isEmpty()) {
            throw IllegalArgumentException("Word list is empty.")
        }
        if (words.size % 3 > 0) {
            throw IllegalArgumentException("Word list size must be multiple of three words.")
        }
    }

    /**
     * Create a binary seed from the mnemonic, we use the PBKDF2 function with a mnemonic sentence (in UTF-8 NFKD) used
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
        val salt = String.format("mnemonic%s", passphrase)

        return PKCS5S2ParametersGenerator(SHA512Digest()).run {
            init(
                words.joinToString(separator = " ").toByteArray(StandardCharsets.UTF_8),
                salt.toByteArray(StandardCharsets.UTF_8),
                SEED_ITERATIONS,
            )

            (generateDerivedParameters(SEED_KEY_SIZE) as KeyParameter).key
        }
    }

    /**
     * Convert mnemonic word list to original entropy value.
     */
    fun getEntropy(wordList: MnemonicWordList): ByteArray {
        // Look up all the words in the list and construct the
        // concatenation of the original entropy and the checksum.
        val concatLenBits = words.size * 11
        val concatBits = BooleanArray(concatLenBits)
        for ((wordIndex, word) in words.withIndex()) {
            // Find the words index in the wordlist.
            val ndx = Collections.binarySearch(wordList.words, word)
            if (ndx < 0) {
                throw RuntimeException("Unknown mnemonic word for provided word list: $word")
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
        val hash = SHA256.Digest().digest(entropy)
        val hashBits = bytesToBits(hash)

        // Check all the checksum bits.
        for (i in 0 until checksumLengthBits) {
            if (concatBits[entropyLengthBits + i] != hashBits[i]) {
                throw RuntimeException("Checksum mismatch, invalid mnemonic")
            }
        }
        return entropy
    }

    companion object {
        private const val SEED_KEY_SIZE = 512
        private const val SEED_ITERATIONS = 2048

        /**
         * Create a new mnemonic code from random entropy, using [SecureRandom].
         * */
        @JvmStatic
        @JvmOverloads
        fun fromRandomEntropy(bitsOfEntropy: Int = 256, wordList: MnemonicWordList = MnemonicWordListEnglish): MnemonicCode {
            val rand = SecureRandom()
            val entropy = ByteArray(bitsOfEntropy / 8)
            rand.nextBytes(entropy)

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
            val hash = SHA256.Digest().digest(entropy)
            val hashBits = bytesToBits(hash)
            val entropyBits = bytesToBits(entropy)
            val checksumLengthBits = entropyBits.size / 32

            // We append these bits to the end of the initial entropy.
            val concatBits = BooleanArray(entropyBits.size + checksumLengthBits)
            System.arraycopy(entropyBits, 0, concatBits, 0, entropyBits.size)
            System.arraycopy(hashBits, 0, concatBits, entropyBits.size, checksumLengthBits)

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
