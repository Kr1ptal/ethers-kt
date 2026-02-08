package fixtures

import io.ethers.core.types.AccessList
import io.ethers.core.types.Address
import io.ethers.core.types.Authorization
import io.ethers.core.types.Bytes
import io.ethers.core.types.transaction.TxSetCode
import java.math.BigInteger

object TxSetCodeFactory {
    fun create(
        to: Address = Address("0x1234567890123456789012345678901234567890"),
        value: BigInteger = BigInteger.ZERO,
        nonce: Long = 0,
        gas: Long = 21000,
        gasFeeCap: BigInteger = BigInteger("20000000000"),
        gasTipCap: BigInteger = BigInteger("1000000000"),
        data: Bytes? = null,
        chainId: Long = 1L,
        accessList: List<AccessList.Item> = emptyList(),
        authorizationList: List<Authorization> = listOf(AuthorizationFactory.create()),
    ): TxSetCode {
        return TxSetCode(to, value, nonce, gas, gasFeeCap, gasTipCap, data, chainId, accessList, authorizationList)
    }
}
