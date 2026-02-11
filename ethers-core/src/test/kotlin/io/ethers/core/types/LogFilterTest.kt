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
              "fromBlock": "${logFilter.blocks.from.id}",
              "toBlock": "${logFilter.blocks.to.id}",
              "address": [
                "${logFilter.addresses!![0]}",
                "${logFilter.addresses!![1]}"
              ],
              "topics": [
                "${logFilter.topic0?.get(0)}",
                "${logFilter.topic1?.get(0)}",
                [
                  "${logFilter.topic2?.get(0)}",
                  "${logFilter.topic2?.get(1)}"
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
                  "fromBlock": "${logFilter.blocks.from.id}",
                  "toBlock": "${logFilter.blocks.to.id}"
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
                  "fromBlock": "latest",
                  "toBlock": "latest",
                  "address": ${Jackson.MAPPER.writeValueAsString(logFilter.addresses)}
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
                      "fromBlock": "latest",
                      "toBlock": "latest",
                      "topics": ${Jackson.MAPPER.writeValueAsString(expectedResult.map { it?.get(0) })}
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
                      "fromBlock": "latest",  
                      "toBlock": "latest",  
                      "topics": ${Jackson.MAPPER.writeValueAsString(expectedResult)}
                    }
                """
            }
        }
    }

    context("add address filter") {
        val logFilter = LogFilter()

        test("no address filter") {
            logFilter.addresses shouldBe null
        }

        test("add addresses filter") {
            val addresses = listOf(
                "0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5",
                "0xC4356aF40cc379b15925Fc8C21e52c00F474e8e9",
                "0x8c33e6ed63886BF3288B53dDaf12B3E0B99D8aB9",
                "0xfbBE64481ABC0CEFbA9c41C82944Dd91Cda5bA3F",
            ).map(::Address)

            logFilter.address(addresses)
            logFilter.addresses shouldBe addresses
        }
    }

    context("LogFilter copying") {
        test("copy with all fields set") {
            val original = LogFilter {
                blockRange(18283547, 18284258)
                address(
                    listOf(
                        Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5"),
                        Address("0xC4356aF40cc379b15925Fc8C21e52c00F474e8e9"),
                    ),
                )

                topic0(Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c"))
                topic1(Hash("0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b"))
                topic2(
                    Hash("0xdcbb85a830f7fdd245f448152507f1864a34de12b6b6511f419f8a47afb4b54d"),
                    Hash("0xd634e03a494263d2fbc47bfb89d8748b10fd294e8f92f07ac067e32753372da3"),
                )
            }

            LogFilter(original) shouldBe original
        }

        test("copy with null addresses and null topics") {
            val original = LogFilter()
            original.addresses shouldBe null
            original.topics shouldBe null

            val copy = LogFilter(original)
            copy shouldBe original
            copy.addresses shouldBe null
            copy.topics shouldBe null
        }
    }

    context("atBlock overloads") {
        test("atBlock(Long) creates range with same from/to") {
            val filter = LogFilter { atBlock(12345L) }
            val blocks = filter.blocks as BlockSelector.Range
            blocks.from shouldBe BlockId.Number(12345L)
            blocks.to shouldBe BlockId.Number(12345L)
        }

        test("atBlock(BlockId.Number) creates range with same from/to") {
            val filter = LogFilter { atBlock(BlockId.Number(99L) as BlockId) }
            val blocks = filter.blocks as BlockSelector.Range
            blocks.from shouldBe BlockId.Number(99L)
            blocks.to shouldBe BlockId.Number(99L)
        }

        test("atBlock(BlockId.Name) creates range with same from/to") {
            val filter = LogFilter { atBlock(BlockId.LATEST as BlockId) }
            val blocks = filter.blocks as BlockSelector.Range
            blocks.from shouldBe BlockId.LATEST
            blocks.to shouldBe BlockId.LATEST
        }

        test("atBlock(Long) serialization") {
            val filter = LogFilter { atBlock(12345L) }
            Jackson.MAPPER.writeValueAsString(filter) shouldEqualJson """
                {
                  "fromBlock": "0x3039",
                  "toBlock": "0x3039"
                }
            """
        }
    }

    context("address vararg overload") {
        test("address(vararg) with multiple addresses") {
            val a1 = Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5")
            val a2 = Address("0xC4356aF40cc379b15925Fc8C21e52c00F474e8e9")
            val filter = LogFilter { address(a1, a2) }
            filter.addresses!!.size shouldBe 2
            filter.addresses!![0] shouldBe a1
            filter.addresses!![1] shouldBe a2
        }

        test("address(vararg) serialization") {
            val a1 = Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5")
            val a2 = Address("0xC4356aF40cc379b15925Fc8C21e52c00F474e8e9")
            val filter = LogFilter { address(a1, a2) }
            Jackson.MAPPER.writeValueAsString(filter) shouldEqualJson """
                {
                  "fromBlock": "latest",
                  "toBlock": "latest",
                  "address": ["$a1", "$a2"]
                }
            """
        }
    }

    context("topic Collection overloads") {
        val h1 = Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c")
        val h2 = Hash("0xdcbb85a830f7fdd245f448152507f1864a34de12b6b6511f419f8a47afb4b54d")

        test("topic0(Collection)") {
            val filter = LogFilter { topic0(listOf(h1, h2)) }
            filter.topic0!!.size shouldBe 2
            filter.topic0!![0] shouldBe h1
            filter.topic0!![1] shouldBe h2
        }

        test("topic1(Collection)") {
            val filter = LogFilter { topic1(listOf(h1, h2)) }
            filter.topic1!!.size shouldBe 2
            filter.topic1!![0] shouldBe h1
        }

        test("topic2(Collection)") {
            val filter = LogFilter { topic2(listOf(h1, h2)) }
            filter.topic2!!.size shouldBe 2
            filter.topic2!![0] shouldBe h1
        }

        test("topic3(Collection)") {
            val filter = LogFilter { topic3(listOf(h1, h2)) }
            filter.topic3!!.size shouldBe 2
            filter.topic3!![0] shouldBe h1
        }
    }

    context("topic getters") {
        test("topic getters return null when topics is null") {
            val filter = LogFilter()
            filter.topic0 shouldBe null
            filter.topic1 shouldBe null
            filter.topic2 shouldBe null
            filter.topic3 shouldBe null
        }

        test("topic3 getter returns value when set") {
            val h = Hash("0x21a92b9ac209df2b952dcbe85dad7355ce3d9389692e7ebc6372a7cc1bc23f9b")
            val filter = LogFilter { topic3(h) }
            filter.topic3!!.size shouldBe 1
            filter.topic3!![0] shouldBe h
        }
    }

    context("serialization edge cases") {
        test("serialization with no topics omits topics field") {
            val addr = Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5")
            val filter = LogFilter { address(addr) }
            Jackson.MAPPER.writeValueAsString(filter) shouldEqualJson """
                {
                  "fromBlock": "latest",
                  "toBlock": "latest",
                  "address": ["$addr"]
                }
            """
        }

        test("serialization with no addresses omits address field") {
            val filter = LogFilter {
                blockRange(1L, 100L)
            }
            Jackson.MAPPER.writeValueAsString(filter) shouldEqualJson """
                {
                  "fromBlock": "0x1",
                  "toBlock": "0x64"
                }
            """
        }

        test("serialization with topic gap includes null placeholder") {
            val h = Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c")
            val filter = LogFilter { topic2(h) }
            Jackson.MAPPER.writeValueAsString(filter) shouldEqualJson """
                {
                  "fromBlock": "latest",
                  "toBlock": "latest",
                  "topics": [null, null, "$h"]
                }
            """
        }
    }

    context("equals") {
        test("identity") {
            val filter = LogFilter()
            (filter == filter) shouldBe true
        }

        test("equal filters with same blocks") {
            val a = LogFilter { blockRange(1L, 100L) }
            val b = LogFilter { blockRange(1L, 100L) }
            a shouldBe b
        }

        test("different blocks") {
            val a = LogFilter { blockRange(1L, 100L) }
            val b = LogFilter { blockRange(1L, 200L) }
            (a == b) shouldBe false
        }

        test("one has addresses other does not") {
            val a = LogFilter { address(Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5")) }
            val b = LogFilter()
            (a == b) shouldBe false
            (b == a) shouldBe false
        }

        test("different addresses") {
            val a = LogFilter { address(Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5")) }
            val b = LogFilter { address(Address("0xC4356aF40cc379b15925Fc8C21e52c00F474e8e9")) }
            (a == b) shouldBe false
        }

        test("one has topics other does not") {
            val h = Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c")
            val a = LogFilter { topic0(h) }
            val b = LogFilter()
            (a == b) shouldBe false
            (b == a) shouldBe false
        }

        test("different topics") {
            val a = LogFilter { topic0(Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c")) }
            val b = LogFilter { topic0(Hash("0xdcbb85a830f7fdd245f448152507f1864a34de12b6b6511f419f8a47afb4b54d")) }
            (a == b) shouldBe false
        }

        test("not equal to null or different type") {
            val filter = LogFilter()
            filter.equals(null) shouldBe false
            filter.equals("string") shouldBe false
        }
    }

    context("hashCode") {
        test("equal filters have equal hashCodes") {
            val h = Hash("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c")
            val a = LogFilter {
                blockRange(1L, 100L)
                address(Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5"))
                topic0(h)
            }
            val b = LogFilter {
                blockRange(1L, 100L)
                address(Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5"))
                topic0(h)
            }
            a.hashCode() shouldBe b.hashCode()
        }

        test("empty filter hashCode is consistent") {
            val a = LogFilter()
            val b = LogFilter()
            a.hashCode() shouldBe b.hashCode()
        }
    }

    context("BlockSelector.Range equals and hashCode") {
        test("equal ranges") {
            val a = BlockSelector.Range(BlockId.Number(1L), BlockId.Number(100L))
            val b = BlockSelector.Range(BlockId.Number(1L), BlockId.Number(100L))
            a shouldBe b
            a.hashCode() shouldBe b.hashCode()
        }

        test("different from") {
            val a = BlockSelector.Range(BlockId.Number(1L), BlockId.Number(100L))
            val b = BlockSelector.Range(BlockId.Number(2L), BlockId.Number(100L))
            (a == b) shouldBe false
        }

        test("different to") {
            val a = BlockSelector.Range(BlockId.Number(1L), BlockId.Number(100L))
            val b = BlockSelector.Range(BlockId.Number(1L), BlockId.Number(200L))
            (a == b) shouldBe false
        }

        test("identity") {
            val range = BlockSelector.Range(BlockId.Number(1L), BlockId.Number(100L))
            (range == range) shouldBe true
        }

        test("not equal to null or different type") {
            val range = BlockSelector.Range(BlockId.Number(1L), BlockId.Number(100L))
            range.equals(null) shouldBe false
            range.equals("string") shouldBe false
        }
    }
})
