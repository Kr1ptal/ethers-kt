package io.ethers.core.types

import io.ethers.core.Jackson
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.style.FunSpec
import java.math.BigInteger

class CallRequestTest : FunSpec({
    test("CallRequest serialization") {
        val callRequest = CallRequest {
            from(Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5"))
            to(Address("0xC4356aF40cc379b15925Fc8C21e52c00F474e8e9"))
            gas(27_527L)
            gasPrice(BigInteger("6439232586"))
            gasFeeCap(BigInteger("6439232587"))
            gasTipCap(BigInteger("1"))
            value(BigInteger("11650662055314012"))
            nonce(455_628L)
            data(Bytes("0x01"))
            accessList(
                listOf(
                    AccessList.Item(
                        Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5"),
                        listOf(
                            Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c"),
                            Hash("0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b"),
                            Hash("0xdcbb85a830f7fdd245f448152507f1864a34de12b6b6511f419f8a47afb4b54d"),
                            Hash("0xd634e03a494263d2fbc47bfb89d8748b10fd294e8f92f07ac067e32753372da3"),
                        ),
                    ),
                ),
            )
            chainId(1)
        }

        Jackson.MAPPER.writeValueAsString(callRequest) shouldEqualJson """
            {
              "from": "${callRequest.from!!}",
              "to": "${callRequest.to!!}",
              "gas": "0x${callRequest.gas.toString(16)}",
              "gasPrice": "0x${callRequest.gasPrice!!.toString(16)}",
              "maxFeePerGas": "0x${callRequest.gasFeeCap!!.toString(16)}",
              "maxPriorityFeePerGas": "0x${callRequest.gasTipCap!!.toString(16)}",
              "value": "0x${callRequest.value!!.toString(16)}",
              "nonce": "0x${callRequest.nonce.toString(16)}",
              "data": "${callRequest.data!!}",
              "accessList": [
                {
                  "address": "${callRequest.accessList[0].address}",
                  "storageKeys": [
                    "${callRequest.accessList[0].storageKeys[0]}",
                    "${callRequest.accessList[0].storageKeys[1]}",
                    "${callRequest.accessList[0].storageKeys[2]}",
                    "${callRequest.accessList[0].storageKeys[3]}"
                  ]
                }
              ],
              "chainId": "0x${callRequest.chainId.toString(16)}"
            }
        """
    }

    test("throws on negative BigInteger assignment") {
        val request = CallRequest()

        shouldThrowUnit<IllegalArgumentException> { request.gasPrice = BigInteger.ONE.negate() }
        shouldThrowUnit<IllegalArgumentException> { request.gasPrice(BigInteger.ONE.negate()) }

        shouldThrowUnit<IllegalArgumentException> { request.gasFeeCap = BigInteger.ONE.negate() }
        shouldThrowUnit<IllegalArgumentException> { request.gasFeeCap(BigInteger.ONE.negate()) }

        shouldThrowUnit<IllegalArgumentException> { request.gasTipCap = BigInteger.ONE.negate() }
        shouldThrowUnit<IllegalArgumentException> { request.gasTipCap(BigInteger.ONE.negate()) }

        shouldThrowUnit<IllegalArgumentException> { request.value = BigInteger.ONE.negate() }
        shouldThrowUnit<IllegalArgumentException> { request.value(BigInteger.ONE.negate()) }

        shouldThrowUnit<IllegalArgumentException> { request.blobFeeCap = BigInteger.ONE.negate() }
        shouldThrowUnit<IllegalArgumentException> { request.blobFeeCap(BigInteger.ONE.negate()) }
    }
})
