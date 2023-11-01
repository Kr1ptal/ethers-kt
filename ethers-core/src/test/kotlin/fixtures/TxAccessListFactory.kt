package fixtures

import io.ethers.core.types.AccessList
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.transaction.TxAccessList
import java.math.BigInteger

object TxAccessListFactory {
    fun create(
        chainId: Long,
        to: Address? = null,
        value: BigInteger = BigInteger.ZERO,
        nonce: Long = 0,
        gas: Long = 0,
        gasPrice: BigInteger = BigInteger.ZERO,
        data: Bytes? = null,
        accessList: List<AccessList.Item>? = null,
    ): TxAccessList {
        return TxAccessList(to, value, nonce, gas, gasPrice, data, chainId, accessList)
    }
}
