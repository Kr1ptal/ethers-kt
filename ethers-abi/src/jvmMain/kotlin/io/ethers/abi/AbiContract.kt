package io.ethers.abi

import io.ethers.core.types.Address
import io.ethers.providers.middleware.Middleware

abstract class AbiContract(val provider: Middleware, val address: Address)
