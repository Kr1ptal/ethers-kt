package io.ethers.solana

import io.ethers.core.FastHex
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.SolanaAddress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Device smoke coverage for the TweetNaCl backend, including devices without JCA Ed25519. */
class AndroidCryptoTest {
    @Test
    fun rfc8032AndProgramAddress() {
        val signer = KeypairSigner.fromSeed(FastHex.decode("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"))
        val signature = signer.signMessage(byteArrayOf())
        assertArrayEquals(FastHex.decode("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"), signature.toByteArray())
        assertTrue(signer.publicKey.verify(signature, byteArrayOf()))
        assertFalse(signer.publicKey.verify(signature, byteArrayOf(1)))
        assertEquals(signer.publicKey, KeypairSigner.fromSecretKey(signer.toSecretKey()).publicKey)
        val owner = SolanaAddress("CYLdTZhP8d1GDGeeNapgPdUcPiux1U9B26315x38TtbQ")
        assertEquals("3W9cYxjkWXUPAsfGJ1GNdFiZsGEwcoopwMz4S8eAkkXd", SolanaAddress.findAssociatedTokenAddress(owner, owner).address.toString())
    }
}
