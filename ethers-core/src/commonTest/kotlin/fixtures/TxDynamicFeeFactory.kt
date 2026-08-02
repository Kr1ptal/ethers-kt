package fixtures

import io.ethers.core.types.AccessList
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.transaction.TxDynamicFee
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf

object TxDynamicFeeFactory {
    fun create(
        chainId: Long,
        to: Address? = null,
        value: BigInteger = bigIntegerOf(0),
        nonce: Long = 0,
        gas: Long = 0,
        gasFeeCap: BigInteger = bigIntegerOf(0),
        gasTipCap: BigInteger = bigIntegerOf(0),
        data: Bytes? = null,
        accessList: List<AccessList.Item> = emptyList(),
    ): TxDynamicFee {
        return TxDynamicFee(to, value, nonce, gas, gasFeeCap, gasTipCap, data, chainId, accessList)
    }
}
