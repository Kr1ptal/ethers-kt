package io.ethers.providers.middleware

import io.ethers.core.types.Address
import io.ethers.core.types.TxpoolContent
import io.ethers.core.types.TxpoolContentFromAddress
import io.ethers.core.types.TxpoolInspectResult
import io.ethers.core.types.TxpoolStatus
import io.ethers.providers.RpcError
import io.ethers.providers.types.RpcRequest

interface TxpoolApi {
    /**
     * Get pending and queued transactions in transaction pool.
     */
    fun txpoolContent(): RpcRequest<TxpoolContent, RpcError>

    /**
     * Get pending and queued transactions in transaction pool for given [address].
     */
    fun txpoolContentFrom(address: Address): RpcRequest<TxpoolContentFromAddress, RpcError>

    /**
     * Get number of pending and queued transactions in transactions pool.
     */
    fun txpoolStatus(): RpcRequest<TxpoolStatus, RpcError>

    /**
     * Get flattened transaction pool content for easy inspection.
     */
    fun txpoolInspect(): RpcRequest<TxpoolInspectResult, RpcError>
}
