package io.ethers.core.types.tracers

import io.ethers.core.Jackson
import io.ethers.core.types.AccountOverride
import io.ethers.core.types.Address
import io.ethers.core.types.BlockOverride
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec

class TracerTest : FunSpec({
    context("config serialization") {
        test("StructTracer config") {
            val structTracer = StructTracer(
                enableMemory = true,
                disableStack = true,
                disableStorage = true,
                enableReturnData = true,
                debug = true,
                limit = 1,
                overrides = hashMapOf(
                    "override_1" to "value_1",
                    "override_2" to "value_2",
                    "override_3" to "value_3",
                ),
            )

            val config = TracerConfig(
                structTracer,
                10L,
                1L,
                hashMapOf(
                    Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5") to AccountOverride {
                        nonce(5)
                    },
                ),
                blockOverride = BlockOverride {
                    number(1L)
                },
            )

            Jackson.MAPPER.writeValueAsString(config) shouldEqualJson """
                {
                  "enableMemory": ${structTracer.enableMemory},
                  "disableStack": ${structTracer.disableStack},
                  "disableStorage": ${structTracer.disableStorage},
                  "enableReturnData": ${structTracer.enableReturnData},
                  "debug": ${structTracer.debug},
                  "limit": ${structTracer.limit},
                  "overrides": ${Jackson.MAPPER.writeValueAsString(structTracer.overrides)},
                  "timeout": "${config.timeoutMs}ms",
                  "reexec": ${config.reexec},
                  "stateOverride": ${Jackson.MAPPER.writeValueAsString(config.stateOverride)},
                  "blockOverride": ${Jackson.MAPPER.writeValueAsString(config.blockOverride)}
                }
            """
        }

        test("non-struct tracer config") {
            val config = TracerConfig(
                FourByteTracer,
                10L,
                1L,
                hashMapOf(
                    Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5") to AccountOverride {
                        nonce(5)
                    },
                ),
                blockOverride = BlockOverride {
                    number(1L)
                },
            )

            Jackson.MAPPER.writeValueAsString(config) shouldEqualJson """
                {
                  "tracer": "${config.tracer.name}",
                  "tracerConfig": {},
                  "timeout": "${config.timeoutMs}ms",
                  "reexec": ${config.reexec},
                  "stateOverride": ${Jackson.MAPPER.writeValueAsString(config.stateOverride)},
                  "blockOverride": ${Jackson.MAPPER.writeValueAsString(config.blockOverride)}
                }
            """
        }
    }
})
