package io.ethers.core.types.transactions

import io.ethers.core.Jackson
import io.ethers.core.types.AccessList
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.Signature
import io.ethers.core.types.transaction.TransactionSigned
import io.ethers.core.types.transaction.TxAccessList
import io.ethers.core.types.transaction.TxBlob
import io.ethers.core.types.transaction.TxDynamicFee
import io.ethers.core.types.transaction.TxLegacy
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.WithDataTestName
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import java.io.File

class TransactionSignedTest : FunSpec({
    test("rlp encode/decode TxLegacy without chain ID") {
        // based on: 0x8c09b3738ff35f814d1549dad5cb0a6d5858a4cd66a3ac8abec903a8ec7acf4f
        val tx = TxLegacy(
            to = Address("0x32be343b94f860124dc4fee278fdcbd38c102d88"),
            value = "53940392390000001024".toBigInteger(),
            nonce = 71,
            gas = 21000,
            gasPrice = "60317759056".toBigInteger(),
            data = null,
            chainId = -1L,
        )

        val signature = Signature(
            "19421212088719815271344666575303211260201938119335252342119094927553198774356".toBigInteger(),
            "31544167366976575860499615173798475590035610996395232018037175896014426317714".toBigInteger(),
            28,
        )

        val signed = TransactionSigned(tx, signature)
        val rlp = signed.toRlp()
        rlp.toHexString() shouldBeEqualIgnoringCase "f86d47850e0b37f6508252089432be343b94f860124dc4fee278fdcbd38c102d888902ec92c5171b224000801ca02af0043955354a8dcceefc492f464977d6eac51d7c94b41d86dd11ab5d0b5854a045bd5db428b629807524f4b973f820a4591b59055a6669f346039517f9fda392"

        val decoded = TransactionSigned.rlpDecode(rlp)
        decoded shouldBe signed
    }

    test("rlp encode/decode TxLegacy with chain ID") {
        val tx = TxLegacy(
            to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
            value = "1000000000".toBigInteger(),
            nonce = 0,
            gas = 2000000,
            gasPrice = "21000000000".toBigInteger(),
            data = null,
            chainId = 1L,
        )

        val signature = Signature(
            "79440387067578648643008012733832802856903723450045762070555193125330762627918".toBigInteger(),
            "24830859692978839778370969737964141525080645226171071268096642880992234125891".toBigInteger(),
            38,
        )

        val signed = TransactionSigned(tx, signature)
        val rlp = signed.toRlp()
        rlp.toHexString() shouldBeEqualIgnoringCase "f869808504e3b29200831e848094f0109fc8df283027b6285cc889f5aa624eac1f55843b9aca008026a0afa1aa6b3d92db3e76e0573281b81eee269a5d8ce864ab4e0e8b288658c87b4ea036e5c4bf00230dd93364784ca4e853ebff45d1b074c2539006f97c742af73a43"

        val decoded = TransactionSigned.rlpDecode(rlp)
        decoded shouldBe signed
    }

    test("rlp encode/decode TxAccessList") {
        val tx = TxAccessList(
            to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
            value = "1000000000".toBigInteger(),
            nonce = 0,
            gas = 2000000,
            gasPrice = "21000000000".toBigInteger(),
            data = Bytes("0x1214abcdef12445980"),
            chainId = 1L,
            accessList = listOf(
                AccessList.Item(
                    Address("0x2f62f2b4c5fcd7570a709dec05d68ea19c82a9ec"),
                    listOf(
                        Hash("0x9c2c23028bf4f085740a3671821db14e440561f617ea5532ee805d7f054741f6"),
                        Hash("0x000000000000000000000000000000000000000000000000000000000000000b"),
                        Hash("0x000000000000000000000000000000000000000000000000000000000000000a"),
                    ),
                ),
            ),
        )

        val signature = Signature(
            "79440387067578648643008012733832802856903723450045762070555193125330762627918".toBigInteger(),
            "24830859692978839778370969737964141525080645226171071268096642880992234125891".toBigInteger(),
            1,
        )

        val signed = TransactionSigned(tx, signature)
        val rlp = signed.toRlp()
        rlp.toHexString() shouldBeEqualIgnoringCase "01f8f101808504e3b29200831e848094f0109fc8df283027b6285cc889f5aa624eac1f55843b9aca00891214abcdef12445980f87cf87a942f62f2b4c5fcd7570a709dec05d68ea19c82a9ecf863a09c2c23028bf4f085740a3671821db14e440561f617ea5532ee805d7f054741f6a0000000000000000000000000000000000000000000000000000000000000000ba0000000000000000000000000000000000000000000000000000000000000000a01a0afa1aa6b3d92db3e76e0573281b81eee269a5d8ce864ab4e0e8b288658c87b4ea036e5c4bf00230dd93364784ca4e853ebff45d1b074c2539006f97c742af73a43"

        val decoded = TransactionSigned.rlpDecode(rlp)
        decoded shouldBe signed
    }

    test("rlp encode/decode TxDynamicFee") {
        val tx = TxDynamicFee(
            to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
            value = "1000000000".toBigInteger(),
            nonce = 12425132,
            gas = 2000000,
            gasFeeCap = "210000000000".toBigInteger(),
            gasTipCap = "21000000000".toBigInteger(),
            data = Bytes("0x1214abcdef12445980"),
            chainId = 1L,
            accessList = listOf(
                AccessList.Item(
                    Address("0x2f62f2b4c5fcd7570a709dec05d68ea19c82a9ec"),
                    listOf(
                        Hash("0x9c2c23028bf4f085740a3671821db14e440561f617ea5532ee805d7f054741f6"),
                        Hash("0x000000000000000000000000000000000000000000000000000000000000000b"),
                        Hash("0x000000000000000000000000000000000000000000000000000000000000000a"),
                    ),
                ),
            ),
        )

        val signature = Signature(
            "79440387067578648643008012733832802856903723450045762070555193125330762627918".toBigInteger(),
            "24830859692978839778370969737964141525080645226171071268096642880992234125891".toBigInteger(),
            1,
        )

        val signed = TransactionSigned(tx, signature)
        val rlp = signed.toRlp()
        rlp.toHexString() shouldBeEqualIgnoringCase "02f8fa0183bd97ac8504e3b292008530e4f9b400831e848094f0109fc8df283027b6285cc889f5aa624eac1f55843b9aca00891214abcdef12445980f87cf87a942f62f2b4c5fcd7570a709dec05d68ea19c82a9ecf863a09c2c23028bf4f085740a3671821db14e440561f617ea5532ee805d7f054741f6a0000000000000000000000000000000000000000000000000000000000000000ba0000000000000000000000000000000000000000000000000000000000000000a01a0afa1aa6b3d92db3e76e0573281b81eee269a5d8ce864ab4e0e8b288658c87b4ea036e5c4bf00230dd93364784ca4e853ebff45d1b074c2539006f97c742af73a43"

        val decoded = TransactionSigned.rlpDecode(rlp)
        decoded shouldBe signed
    }

    test("rlp encode/decode TxBlob without sidecar") {
        val tx = TxBlob(
            to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
            value = "1000000000".toBigInteger(),
            nonce = 12425132,
            gas = 2000000,
            gasFeeCap = "210000000000".toBigInteger(),
            gasTipCap = "21000000000".toBigInteger(),
            data = Bytes("0x1214abcdef12445980"),
            chainId = 1L,
            accessList = emptyList(),
            blobFeeCap = "21000000000".toBigInteger(),
            blobVersionedHashes = listOf(Hash("0x010657f37554c781402a22917dee2f75def7ab966d7b770905398eba3c444014")),
        )

        val signature = Signature(
            "35405165784662159660510809353169567992201622993236193183395232175930194673093".toBigInteger(),
            "9775730991967298881365724626956341373758990245808092194442283197729299157756".toBigInteger(),
            0,
        )

        val signed = TransactionSigned(tx, signature)
        signed.hash shouldBe Hash("0x5b51360854253b6308208ea86423cbba471240f473f8ec811e8c95806714a14e")

        val rlp = signed.toRlp()
        rlp.toHexString() shouldBeEqualIgnoringCase "03f8a50183bd97ac8504e3b292008530e4f9b400831e848094f0109fc8df283027b6285cc889f5aa624eac1f55843b9aca00891214abcdef12445980c08504e3b29200e1a0010657f37554c781402a22917dee2f75def7ab966d7b770905398eba3c44401480a04e469d1af21dcdacf8ac456ec12138d441c1747a31540573d3626d3731523dc5a0159cde1f3a8c937d64a1de03e3150d6cd5f07c2e94c76e9836379e54abfad2fc"

        val decoded = TransactionSigned.rlpDecode(rlp)
        decoded shouldBe signed
    }

    test("rlp encode/decode TxBlob with sidecar") {
        val tx = TxBlob(
            to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
            value = "1000000000".toBigInteger(),
            nonce = 12425132,
            gas = 2000000,
            gasFeeCap = "210000000000".toBigInteger(),
            gasTipCap = "21000000000".toBigInteger(),
            data = Bytes("0x1214abcdef12445980"),
            chainId = 1L,
            accessList = emptyList(),
            blobFeeCap = "21000000000".toBigInteger(),
            sidecar = TxBlob.Sidecar(
                blobs = listOf(Bytes(ByteArray(TxBlob.Sidecar.BLOB_LENGTH))),
                commitments = listOf(Bytes("c00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")),
                proofs = listOf(Bytes("c00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")),
            ),
        )

        val signature = Signature(
            "35405165784662159660510809353169567992201622993236193183395232175930194673093".toBigInteger(),
            "9775730991967298881365724626956341373758990245808092194442283197729299157756".toBigInteger(),
            0,
        )

        val signed = TransactionSigned(tx, signature)
        signed.hash shouldBe Hash("0x5b51360854253b6308208ea86423cbba471240f473f8ec811e8c95806714a14e")

        val rlp = signed.toRlp()
        rlp.toHexString() shouldBeEqualIgnoringCase TransactionSignedTest::class.java
            .getResource("/testdata/tx_zeroed_blob_with_sidecar.rlp")!!
            .readText()

        val decoded = TransactionSigned.rlpDecode(rlp)
        decoded shouldBe signed
    }

    context("RLP roundtrip encode/decode test") {
        val reader = Jackson.MAPPER.readerFor(RoundtripCase::class.java)
        val rlpBatches = TransactionSignedTest::class.java.getResource("/testdata/txsigned")!!
            .file
            .let(::File)
            .walkTopDown()
            .filter { it.isFile && it.name.endsWith(".json") }
            .map { it.name to reader.readValues<RoundtripCase>(it).readAll() }

        rlpBatches.forEach { (dumpName, cases) ->
            context(dumpName) {
                withData(cases) {
                    val decoded = TransactionSigned.rlpDecode(it.rlp.asByteArray())

                    // it's enough to check that "hash" and "from" have expected values:
                    // - if any value was different from original tx, the "hash" will be different
                    // - if signature is different from original tx, the "from" will fail or will be different
                    decoded shouldNotBe null
                    decoded!!.hash shouldBe it.hash
                    decoded.from shouldBe it.from

                    val rlpEncoded = Bytes(decoded.toRlp())
                    rlpEncoded shouldBe it.rlp
                }
            }
        }
    }
}) {
    data class RoundtripCase(val hash: Hash, val from: Address, val rlp: Bytes) : WithDataTestName {
        override fun dataTestName() = hash.toString()
    }
}

// Used to dump additional roundtrip test cases, need to run from providers module
/*fun main() {
    val provider = Provider(HttpClient("RPC_URL"))
    val block = provider.getBlockWithTransactions(19111477).sendAwait().unwrap()
    val encoded = block.transactions.map { TransactionSignedTest.RoundtripCase(it.hash, it.from, Bytes(it.toSignedTransaction().toRlp())) }

    // copy the result to resource folder
    Jackson.MAPPER.writeValue(File("./transactions-${block.number}.json"), encoded)
}*/
