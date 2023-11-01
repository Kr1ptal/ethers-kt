package io.ethers.core.types

import io.ethers.core.Jackson
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class LogFilterTest : FunSpec({
    test("LogFilter serialization") {
        val logFilter = LogFilter {
            blockRange(18283547, 18284258)
            address(
                listOf(
                    Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5"),
                    Address("0xC4356aF40cc379b15925Fc8C21e52c00F474e8e9"),
                ),
            )

            // Dummy topic values
            topic0(Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c"))
            topic1(Hash("0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b"))
            topic2(
                Hash("0xdcbb85a830f7fdd245f448152507f1864a34de12b6b6511f419f8a47afb4b54d"),
                Hash("0xd634e03a494263d2fbc47bfb89d8748b10fd294e8f92f07ac067e32753372da3"),
            )
        }

        Jackson.MAPPER.writeValueAsString(logFilter) shouldEqualJson """
            {
              "fromBlock": "${((logFilter.blocks!! as BlockSelector.Range).from as BlockId.Number).id}",
              "toBlock": "${((logFilter.blocks!! as BlockSelector.Range).to as BlockId.Number).id}",
              "address": [
                "${logFilter.address!![0]}",
                "${logFilter.address!![1]}"
              ],
              "topics": [
                "${(logFilter.topics!![0] as Topic.Single).hash}",
                "${(logFilter.topics!![1] as Topic.Single).hash}",
                [
                  "${(logFilter.topics!![2] as Topic.Array).hashes[0]}",
                  "${(logFilter.topics!![2] as Topic.Array).hashes[1]}"
                ]
              ]
            }
        """
    }

    context("LogFilter.blockRange() serialization") {
        withData(
            LogFilter { blockRange(BlockId.Number(1L), BlockId.Number(100L)) },
            LogFilter { blockRange(BlockId.EARLIEST, BlockId.FINALIZED) },
            LogFilter { blockRange(BlockId.Number(100L), BlockId.LATEST) },
            LogFilter { blockRange(BlockId.EARLIEST, BlockId.Number(100L)) },
        ) { logFilter ->
            Jackson.MAPPER.writeValueAsString(logFilter) shouldEqualJson """
                {
                  "fromBlock": "${(logFilter.blocks!! as BlockSelector.Range).from.id}",
                  "toBlock": "${(logFilter.blocks!! as BlockSelector.Range).to.id}"
                }
            """
        }
    }

    context("LogFilter.atBlock() serialization") {
        withData(
            LogFilter { atBlock(Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c")) },
            LogFilter { atBlock(BlockId.Hash(Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c"))) },
        ) { logFilter ->
            Jackson.MAPPER.writeValueAsString(logFilter) shouldEqualJson """
                {
                  "blockHash": "${(logFilter.blocks as BlockSelector.Hash).hash}"
                }
            """
        }
    }

    context("LogFilter.address() serialization") {
        withData(
            LogFilter { address(Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5")) },
            LogFilter {
                address(
                    listOf(
                        Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5"),
                        Address("0xC4356aF40cc379b15925Fc8C21e52c00F474e8e9"),
                    ),
                )
            },
        ) { logFilter ->
            Jackson.MAPPER.writeValueAsString(logFilter) shouldEqualJson """
                {
                  "address": ${Jackson.MAPPER.writeValueAsString(logFilter.address)}
                }
            """
        }
    }

    context("LogFilter.topics() serialization") {
        context("single topic") {
            withData(
                LogFilter { topic0(Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c")) },
                LogFilter { topic1(Hash("0xdcbb85a830f7fdd245f448152507f1864a34de12b6b6511f419f8a47afb4b54d")) },
                LogFilter { topic2(Hash("0xd634e03a494263d2fbc47bfb89d8748b10fd294e8f92f07ac067e32753372da3")) },
                LogFilter { topic3(Hash("0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b")) },
            ) { logFilter ->
                val indexOfTopic = logFilter.topics!!.indexOfFirst { it != null }
                val expectedResult = logFilter.topics!!.slice(0..indexOfTopic)
                Jackson.MAPPER.writeValueAsString(logFilter) shouldEqualJson """
                    {
                      "topics": ${Jackson.MAPPER.writeValueAsString(expectedResult.map { (it as Topic.Single?)?.hash })}
                    }
                """
            }
        }

        context("multiple topics") {
            withData(
                LogFilter {
                    topic0(
                        Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c"),
                        Hash("0xdcbb85a830f7fdd245f448152507f1864a34de12b6b6511f419f8a47afb4b54d"),
                    )
                },
                LogFilter {
                    topic1(
                        Hash("0xdcbb85a830f7fdd245f448152507f1864a34de12b6b6511f419f8a47afb4b54d"),
                        Hash("0xd634e03a494263d2fbc47bfb89d8748b10fd294e8f92f07ac067e32753372da3"),
                    )
                },
                LogFilter {
                    topic2(
                        Hash("0xd634e03a494263d2fbc47bfb89d8748b10fd294e8f92f07ac067e32753372da3"),
                        Hash("0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b"),
                    )
                },
                LogFilter {
                    topic3(
                        Hash("0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b"),
                        Hash("0xd634e03a494263d2fbc47bfb89d8748b10fd294e8f92f07ac067e32753372da3"),
                    )
                },
            ) { logFilter ->
                val indexOfTopic = logFilter.topics!!.indexOfFirst { it != null }
                val expectedResult = logFilter.topics!!.slice(0..indexOfTopic)
                Jackson.MAPPER.writeValueAsString(logFilter) shouldEqualJson """
                    {
                      "topics": ${Jackson.MAPPER.writeValueAsString(expectedResult.map { (it as Topic.Array?)?.hashes })}
                    }
                """
            }
        }
    }

    context("add address filter on the fly") {
        val logFilter = LogFilter()

        test("no address filter") {
            logFilter.address shouldBe null
        }

        test("add addresses filter") {
            val addresses = listOf(
                "0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5",
                "0xC4356aF40cc379b15925Fc8C21e52c00F474e8e9",
                "0x8c33e6ed63886BF3288B53dDaf12B3E0B99D8aB9",
                "0xfbBE64481ABC0CEFbA9c41C82944Dd91Cda5bA3F",
            )
            addresses.forEach { logFilter.address(Address(it)) }

            logFilter.address shouldBe addresses.map { Address(it) }
        }
    }
})
