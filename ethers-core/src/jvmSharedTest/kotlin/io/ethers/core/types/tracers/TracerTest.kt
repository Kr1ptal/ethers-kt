package io.ethers.core.types.tracers

import io.ethers.core.Kotlinx
import io.ethers.core.types.AccountOverride
import io.ethers.core.types.Address
import io.ethers.core.types.BlockOverride
import io.ethers.core.types.StateOverride
import io.ethers.core.types.StateOverrideSerializer
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

private object CustomDefaultDecodeTracer : Tracer<CustomDefaultDecodeResult> {
    override val name: String = "customDefaultDecodeTracer"
    override val resultType: KClass<CustomDefaultDecodeResult> = CustomDefaultDecodeResult::class
    override val config: Map<String, Any?> = mapOf(
        "enabled" to true,
        "steps" to listOf(1, 2, 3),
        "nested" to mapOf("labels" to arrayOf("alpha", "beta")),
        "payload" to byteArrayOf(0xde.toByte(), 0xad.toByte()),
    )
}

@Serializable
private data class CustomDefaultDecodeResult(
    val data: String,
    val count: Int,
)

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
                StateOverride(
                    Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5") to AccountOverride {
                        nonce(5)
                    },
                ),
                blockOverrides = BlockOverride {
                    number(1L)
                },
            )

            Kotlinx.DEFAULT.encodeToString(config) shouldEqualJson """
                {
                  "enableMemory": ${structTracer.enableMemory},
                  "disableStack": ${structTracer.disableStack},
                  "disableStorage": ${structTracer.disableStorage},
                  "enableReturnData": ${structTracer.enableReturnData},
                  "debug": ${structTracer.debug},
                  "limit": ${structTracer.limit},
                  "overrides": {"override_1":"value_1","override_2":"value_2","override_3":"value_3"},
                  "timeout": "${config.timeoutMs}ms",
                  "reexec": ${config.reexec},
                  "stateOverrides": ${Kotlinx.DEFAULT.encodeToString(StateOverrideSerializer, config.stateOverrides!!)},
                  "blockOverrides": ${Kotlinx.DEFAULT.encodeToString(config.blockOverrides!!)}
                }
            """
        }

        test("non-struct tracer config") {
            val config = TracerConfig(
                FourByteTracer,
                10L,
                1L,
                StateOverride(
                    Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5") to AccountOverride {
                        nonce(5)
                    },
                ),
                blockOverrides = BlockOverride {
                    number(1L)
                },
            )

            Kotlinx.DEFAULT.encodeToString(config) shouldEqualJson """
                {
                  "tracer": "${(config.tracer as Tracer<*>).name}",
                  "tracerConfig": {},
                  "timeout": "${config.timeoutMs}ms",
                  "reexec": ${config.reexec},
                  "stateOverrides": ${Kotlinx.DEFAULT.encodeToString(StateOverrideSerializer, config.stateOverrides!!)},
                  "blockOverrides": ${Kotlinx.DEFAULT.encodeToString(config.blockOverrides!!)}
                }
            """
        }

        test("custom tracer config serializes nested JSON-like values") {
            Kotlinx.DEFAULT.encodeToString(TracerConfig(CustomDefaultDecodeTracer)) shouldEqualJson """
                {
                  "tracer": "customDefaultDecodeTracer",
                  "tracerConfig": {
                    "enabled": true,
                    "steps": [1, 2, 3],
                    "nested": {
                      "labels": ["alpha", "beta"]
                    },
                    "payload": "0xdead"
                  }
                }
            """
        }
    }

    context("result deserialization") {
        test("custom tracer can rely on default resultType decoder") {
            val result = CustomDefaultDecodeTracer.decodeResult(
                Kotlinx.DEFAULT,
                Kotlinx.DEFAULT.parseToJsonElement("""{"data":"ok","count":2}"""),
            )

            result shouldBe CustomDefaultDecodeResult("ok", 2)
        }
    }
})
