package io.ethers.core.types.tracers

import io.ethers.core.Kotlinx
import io.ethers.core.types.Hash
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.ListSerializer

class TxTraceResultTest : FunSpec({
    val serializer = ListSerializer(TxTraceResult.serializer(FourByteTracer.resultSerializer))
    val hash = "0x0000000000000000000000000000000000000000000000000000000000000001"

    test("decodes each transaction's hash, result and error, leaving out what is missing") {
        val json = """
            [
              {"txHash":"$hash","result":{"0x27dc297e-128":1}},
              {"result":{},"txHash":null,"extra":[1]},
              {"txHash":"$hash","error":"execution reverted"},
              {"txHash":"$hash","result":null,"error":"timeout"}
            ]
        """

        val expected = listOf(
            TxTraceResult(Hash(hash), FourByteTracer.Result(mapOf("0x27dc297e-128" to 1)), null),
            TxTraceResult(null, FourByteTracer.Result(emptyMap()), null),
            TxTraceResult(Hash(hash), null, "execution reverted"),
            TxTraceResult(Hash(hash), null, "timeout"),
        )
        Kotlinx.DEFAULT.decodeFromString(serializer, json) shouldBe expected
        Kotlinx.DEFAULT.decodeFromJsonElement(serializer, Kotlinx.DEFAULT.parseToJsonElement(json)) shouldBe expected
    }
})
