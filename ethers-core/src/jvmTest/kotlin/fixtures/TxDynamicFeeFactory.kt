package fixtures

import io.ethers.core.types.AccessList
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.transaction.TxDynamicFee
import java.math.BigInteger

object TxDynamicFeeFactory {
    fun create(
        chainId: Long,
        to: Address? = null,
        value: BigInteger = BigInteger.ZERO,
        nonce: Long = 0,
        gas: Long = 0,
        gasFeeCap: BigInteger = BigInteger.ZERO,
        gasTipCap: BigInteger = BigInteger.ZERO,
        data: Bytes? = null,
        accessList: List<AccessList.Item> = emptyList(),
    ): TxDynamicFee {
        return TxDynamicFee(to, value, nonce, gas, gasFeeCap, gasTipCap, data, chainId, accessList)
    }
}
