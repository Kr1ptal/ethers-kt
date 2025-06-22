package fixtures

import io.ethers.core.types.Address
import io.ethers.core.types.Authorization
import java.math.BigInteger

object AuthorizationFactory {
    fun create(
        chainId: Long = 1L,
        address: Address = Address("0x1234567890123456789012345678901234567890"),
        nonce: Long = 0L,
        yParity: Long = 0L,
        r: BigInteger = BigInteger("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef", 16),
        s: BigInteger = BigInteger("7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF5D576E7357A4501DDFE92F46681B20A0", 16),
    ): Authorization {
        return Authorization(chainId, address, nonce, yParity, r, s)
    }
}
