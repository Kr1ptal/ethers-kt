package fixtures

import io.ethers.core.types.AccessList
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.transaction.TxAccessList
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf

object TxAccessListFactory {
    fun create(
        chainId: Long,
        to: Address? = null,
        value: BigInteger = bigIntegerOf(0),
        nonce: Long = 0,
        gas: Long = 0,
        gasPrice: BigInteger = bigIntegerOf(0),
        data: Bytes? = null,
        accessList: List<AccessList.Item> = emptyList(),
    ): TxAccessList {
        return TxAccessList(to, value, nonce, gas, gasPrice, data, chainId, accessList)
    }
}
