package io.ethers.abi

import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.Log
import io.ethers.providers.middleware.Middleware
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import java.math.BigInteger

class AbiEventTest : FunSpec({
    context("topic abi type") {
        test("values are represented as actual value") {
            Exhaustive.of(
                AbiType.Address,
                AbiType.FixedBytes(32),
                AbiType.FixedBytes(20),
                AbiType.Int(160),
                AbiType.UInt(256),
                AbiType.Bool,
            ).checkAll {
                AbiEvent.getTopicAbiType(it) shouldBe it
            }
        }

        test("dynamic/array types are represented as hash of value") {
            Exhaustive.of(
                AbiType.Bytes,
                AbiType.String,
                AbiType.Array(AbiType.Bool),
                AbiType.FixedArray(20, AbiType.Address),
                AbiType.Tuple(AbiType.Address, AbiType.Bool),
                AbiType.Tuple(AbiType.Address, AbiType.Bytes),
            ).checkAll {
                AbiEvent.getTopicAbiType(it) shouldBe AbiEvent.NON_VALUE_INDEXED_TYPE
            }
        }
    }

    context("decode") {
        test("anonymous event") {
            val log = Log(
                address = Address("0x197e90f9fad81970ba7976f33cbd77088e5d7cf7"),
                topics = listOf(
                    Hash("0x9f678cca00000000000000000000000000000000000000000000000000000000"),
                    Hash("0x00000000000000000000000083f20f44975d03b1b09e64809b757c47f942beea"),
                    Hash("0x0000000000000000000000000000000000000000000000000000000000000000"),
                    Hash("0x0000000000000000000000000000000000000000000000000000000000000000"),
                ),
                data = Bytes("0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000e09f678cca00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"),
                blockHash = Hash("0x8bbd497a03cf0a0690bbb91b38afc539e8552da391cff2d5861abb28a24d3129"),
                blockNumber = 18293121,
                blockTimestamp = 1713693012,
                transactionHash = Hash("0x24ab1ac3496270b0f7719c23e32aa1bac92e15c8f00682a3dd00ebe88d89a9c8"),
                transactionIndex = 143,
                logIndex = 309,
                removed = false,
            )

            val event = LogNote.decode(log)
            event shouldBe LogNote(
                sig = Bytes("0x9f678cca"),
                usr = Address("0x83f20f44975d03b1b09e64809b757c47f942beea"),
                arg1 = Bytes("0x0000000000000000000000000000000000000000000000000000000000000000"),
                arg2 = Bytes("0x0000000000000000000000000000000000000000000000000000000000000000"),
                data = Bytes("0x0000000000000000000000000000000000000000000000000000000000000020"),
                log,
            )
        }

        test("anonymous event with different topics size returns null") {
            val log = Log(
                address = Address("0x197e90f9fad81970ba7976f33cbd77088e5d7cf7"),
                topics = listOf(
                    Hash("0x9f678cca00000000000000000000000000000000000000000000000000000000"),
                    Hash("0x00000000000000000000000083f20f44975d03b1b09e64809b757c47f942beea"),
                ),
                data = Bytes("0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000e09f678cca00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"),
                blockHash = Hash("0x8bbd497a03cf0a0690bbb91b38afc539e8552da391cff2d5861abb28a24d3129"),
                blockNumber = 18293121,
                blockTimestamp = 1713693012,
                transactionHash = Hash("0x24ab1ac3496270b0f7719c23e32aa1bac92e15c8f00682a3dd00ebe88d89a9c8"),
                transactionIndex = 143,
                logIndex = 309,
                removed = false,
            )

            LogNote.decode(log) shouldBe null
        }

        test("anonymous event with no data returns null") {
            val log = Log(
                address = Address("0x197e90f9fad81970ba7976f33cbd77088e5d7cf7"),
                topics = listOf(
                    Hash("0x9f678cca00000000000000000000000000000000000000000000000000000000"),
                    Hash("0x00000000000000000000000083f20f44975d03b1b09e64809b757c47f942beea"),
                    Hash("0x0000000000000000000000000000000000000000000000000000000000000000"),
                    Hash("0x0000000000000000000000000000000000000000000000000000000000000000"),
                ),
                data = Bytes.EMPTY,
                blockHash = Hash("0x8bbd497a03cf0a0690bbb91b38afc539e8552da391cff2d5861abb28a24d3129"),
                blockNumber = 18293121,
                blockTimestamp = 1713693012,
                transactionHash = Hash("0x24ab1ac3496270b0f7719c23e32aa1bac92e15c8f00682a3dd00ebe88d89a9c8"),
                transactionIndex = 143,
                logIndex = 309,
                removed = false,
            )

            LogNote.decode(log) shouldBe null
        }

        test("non-anonymous event") {
            val log = Log(
                address = Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"),
                topics = listOf(
                    Hash("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"),
                    Hash("0x000000000000000000000000855f02967ee16e9f18d388b07b4c75211e73e8c2"),
                    Hash("0x000000000000000000000000ed12310d5a37326e6506209c4838146950166760"),
                ),
                data = Bytes("0x000000000000000000000000000000000000000000000000011dcf6b1af3d3b2"),
                blockHash = Hash("0x32f8ce74b282a9dc63df4bdde2f5a1fc36efc95cf6e5e6e77d5e772b3f32e7da"),
                blockNumber = 18351281,
                blockTimestamp = 1713693012,
                transactionHash = Hash("0xd91f8e2ada54a4ca4c5c78265f916a1e6ef7b318838a32eb967918de0832878a"),
                transactionIndex = 97,
                logIndex = 350,
                removed = false,
            )

            val event = Transfer.decode(log)
            event shouldBe Transfer(
                from = Address("0x855f02967ee16e9f18d388b07b4c75211e73e8c2"),
                to = Address("0xed12310d5a37326e6506209c4838146950166760"),
                amount = BigInteger("80448427283174322"),
                log = log,
            )
        }

        test("non-anonymous event with different topics size returns null") {
            val log = Log(
                address = Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"),
                topics = listOf(
                    Hash("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"),
                    Hash("0x000000000000000000000000855f02967ee16e9f18d388b07b4c75211e73e8c2"),
                    Hash("0x000000000000000000000000ed12310d5a37326e6506209c4838146950166760"),
                    Hash("0x000000000000000000000000ed12310d5a37326e6506209c4838146950166760"),
                ),
                data = Bytes("0x000000000000000000000000000000000000000000000000011dcf6b1af3d3b2"),
                blockHash = Hash("0x32f8ce74b282a9dc63df4bdde2f5a1fc36efc95cf6e5e6e77d5e772b3f32e7da"),
                blockNumber = 18351281,
                blockTimestamp = 1713693012,
                transactionHash = Hash("0xd91f8e2ada54a4ca4c5c78265f916a1e6ef7b318838a32eb967918de0832878a"),
                transactionIndex = 97,
                logIndex = 350,
                removed = false,
            )

            Transfer.decode(log) shouldBe null
        }

        test("non-anonymous event with no data returns null") {
            val log = Log(
                address = Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"),
                topics = listOf(
                    Hash("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"),
                    Hash("0x000000000000000000000000855f02967ee16e9f18d388b07b4c75211e73e8c2"),
                    Hash("0x000000000000000000000000ed12310d5a37326e6506209c4838146950166760"),
                ),
                data = Bytes.EMPTY,
                blockHash = Hash("0x32f8ce74b282a9dc63df4bdde2f5a1fc36efc95cf6e5e6e77d5e772b3f32e7da"),
                blockNumber = 18351281,
                blockTimestamp = 1713693012,
                transactionHash = Hash("0xd91f8e2ada54a4ca4c5c78265f916a1e6ef7b318838a32eb967918de0832878a"),
                transactionIndex = 97,
                logIndex = 350,
                removed = false,
            )

            Transfer.decode(log) shouldBe null
        }

        test("non-anonymous event with different topicId returns null") {
            val log = Log(
                address = Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"),
                topics = listOf(
                    Hash("0xd91f8e2ada54a4ca4c5c78265f916a1e6ef7b318838a32eb967918de0832878a"),
                    Hash("0x000000000000000000000000855f02967ee16e9f18d388b07b4c75211e73e8c2"),
                    Hash("0x000000000000000000000000ed12310d5a37326e6506209c4838146950166760"),
                ),
                data = Bytes("0x000000000000000000000000000000000000000000000000011dcf6b1af3d3b2"),
                blockHash = Hash("0x32f8ce74b282a9dc63df4bdde2f5a1fc36efc95cf6e5e6e77d5e772b3f32e7da"),
                blockNumber = 18351281,
                blockTimestamp = 1713693012,
                transactionHash = Hash("0xd91f8e2ada54a4ca4c5c78265f916a1e6ef7b318838a32eb967918de0832878a"),
                transactionIndex = 97,
                logIndex = 350,
                removed = false,
            )

            Transfer.decode(log) shouldBe null
        }
    }

    context("isLogValid") {
        context("non-anonymous event") {
            test("valid log returns true") {
                val log = Log(
                    address = Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"),
                    topics = listOf(
                        Transfer.abi.topicId,
                        Hash("0x000000000000000000000000855f02967ee16e9f18d388b07b4c75211e73e8c2"),
                        Hash("0x000000000000000000000000ed12310d5a37326e6506209c4838146950166760"),
                    ),
                    data = Bytes("0x000000000000000000000000000000000000000000000000011dcf6b1af3d3b2"),
                    blockHash = Hash.ZERO,
                    blockNumber = 1,
                    blockTimestamp = 1,
                    transactionHash = Hash.ZERO,
                    transactionIndex = 0,
                    logIndex = 0,
                    removed = false,
                )

                Transfer.isLogValid(log) shouldBe true
            }

            test("wrong topic count returns false") {
                val log = Log(
                    address = Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"),
                    topics = listOf(Transfer.abi.topicId),
                    data = Bytes("0x000000000000000000000000000000000000000000000000011dcf6b1af3d3b2"),
                    blockHash = Hash.ZERO,
                    blockNumber = 1,
                    blockTimestamp = 1,
                    transactionHash = Hash.ZERO,
                    transactionIndex = 0,
                    logIndex = 0,
                    removed = false,
                )

                Transfer.isLogValid(log) shouldBe false
            }

            test("empty data when data expected returns false") {
                val log = Log(
                    address = Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"),
                    topics = listOf(
                        Transfer.abi.topicId,
                        Hash("0x000000000000000000000000855f02967ee16e9f18d388b07b4c75211e73e8c2"),
                        Hash("0x000000000000000000000000ed12310d5a37326e6506209c4838146950166760"),
                    ),
                    data = Bytes.EMPTY,
                    blockHash = Hash.ZERO,
                    blockNumber = 1,
                    blockTimestamp = 1,
                    transactionHash = Hash.ZERO,
                    transactionIndex = 0,
                    logIndex = 0,
                    removed = false,
                )

                Transfer.isLogValid(log) shouldBe false
            }

            test("wrong topicId returns false") {
                val log = Log(
                    address = Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"),
                    topics = listOf(
                        Hash("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
                        Hash("0x000000000000000000000000855f02967ee16e9f18d388b07b4c75211e73e8c2"),
                        Hash("0x000000000000000000000000ed12310d5a37326e6506209c4838146950166760"),
                    ),
                    data = Bytes("0x000000000000000000000000000000000000000000000000011dcf6b1af3d3b2"),
                    blockHash = Hash.ZERO,
                    blockNumber = 1,
                    blockTimestamp = 1,
                    transactionHash = Hash.ZERO,
                    transactionIndex = 0,
                    logIndex = 0,
                    removed = false,
                )

                Transfer.isLogValid(log) shouldBe false
            }
        }

        context("anonymous event") {
            test("valid log returns true") {
                val log = Log(
                    address = Address("0x197e90f9fad81970ba7976f33cbd77088e5d7cf7"),
                    topics = listOf(
                        Hash("0x9f678cca00000000000000000000000000000000000000000000000000000000"),
                        Hash("0x00000000000000000000000083f20f44975d03b1b09e64809b757c47f942beea"),
                        Hash("0x0000000000000000000000000000000000000000000000000000000000000000"),
                        Hash("0x0000000000000000000000000000000000000000000000000000000000000000"),
                    ),
                    data = Bytes("0x0000000000000000000000000000000000000000000000000000000000000020"),
                    blockHash = Hash.ZERO,
                    blockNumber = 1,
                    blockTimestamp = 1,
                    transactionHash = Hash.ZERO,
                    transactionIndex = 0,
                    logIndex = 0,
                    removed = false,
                )

                LogNote.isLogValid(log) shouldBe true
            }

            test("wrong topic count returns false") {
                val log = Log(
                    address = Address("0x197e90f9fad81970ba7976f33cbd77088e5d7cf7"),
                    topics = listOf(
                        Hash("0x9f678cca00000000000000000000000000000000000000000000000000000000"),
                        Hash("0x00000000000000000000000083f20f44975d03b1b09e64809b757c47f942beea"),
                    ),
                    data = Bytes("0x0000000000000000000000000000000000000000000000000000000000000020"),
                    blockHash = Hash.ZERO,
                    blockNumber = 1,
                    blockTimestamp = 1,
                    transactionHash = Hash.ZERO,
                    transactionIndex = 0,
                    logIndex = 0,
                    removed = false,
                )

                LogNote.isLogValid(log) shouldBe false
            }

            test("empty data when data expected returns false") {
                val log = Log(
                    address = Address("0x197e90f9fad81970ba7976f33cbd77088e5d7cf7"),
                    topics = listOf(
                        Hash("0x9f678cca00000000000000000000000000000000000000000000000000000000"),
                        Hash("0x00000000000000000000000083f20f44975d03b1b09e64809b757c47f942beea"),
                        Hash("0x0000000000000000000000000000000000000000000000000000000000000000"),
                        Hash("0x0000000000000000000000000000000000000000000000000000000000000000"),
                    ),
                    data = Bytes.EMPTY,
                    blockHash = Hash.ZERO,
                    blockNumber = 1,
                    blockTimestamp = 1,
                    transactionHash = Hash.ZERO,
                    transactionIndex = 0,
                    logIndex = 0,
                    removed = false,
                )

                LogNote.isLogValid(log) shouldBe false
            }
        }
    }

    context("isEvent extension") {
        val validTransferLog = Log(
            address = Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"),
            topics = listOf(
                Transfer.abi.topicId,
                Hash("0x000000000000000000000000855f02967ee16e9f18d388b07b4c75211e73e8c2"),
                Hash("0x000000000000000000000000ed12310d5a37326e6506209c4838146950166760"),
            ),
            data = Bytes("0x000000000000000000000000000000000000000000000000011dcf6b1af3d3b2"),
            blockHash = Hash.ZERO,
            blockNumber = 1,
            blockTimestamp = 1,
            transactionHash = Hash.ZERO,
            transactionIndex = 0,
            logIndex = 0,
            removed = false,
        )

        val invalidLog = Log(
            address = Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"),
            topics = listOf(Hash("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")),
            data = Bytes("0x01"),
            blockHash = Hash.ZERO,
            blockNumber = 1,
            blockTimestamp = 1,
            transactionHash = Hash.ZERO,
            transactionIndex = 0,
            logIndex = 0,
            removed = false,
        )

        test("isEvent with single factory") {
            validTransferLog.isEvent(Transfer) shouldBe true
            invalidLog.isEvent(Transfer) shouldBe false
        }

        test("isEvent with list of factories") {
            validTransferLog.isEvent(listOf(Transfer as EventFactory<*>)) shouldBe true
            invalidLog.isEvent(listOf(Transfer as EventFactory<*>)) shouldBe false
        }

        test("isEvent with iterable of factories") {
            val factories: Iterable<EventFactory<*>> = setOf(Transfer as EventFactory<*>)
            validTransferLog.isEvent(factories) shouldBe true
            invalidLog.isEvent(factories) shouldBe false
        }

        test("isEvent with vararg factories") {
            validTransferLog.isEvent(Transfer, LogNote) shouldBe true
            invalidLog.isEvent(Transfer, LogNote) shouldBe false
        }
    }

    context("toEventOrNull extension") {
        val validTransferLog = Log(
            address = Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"),
            topics = listOf(
                Transfer.abi.topicId,
                Hash("0x000000000000000000000000855f02967ee16e9f18d388b07b4c75211e73e8c2"),
                Hash("0x000000000000000000000000ed12310d5a37326e6506209c4838146950166760"),
            ),
            data = Bytes("0x000000000000000000000000000000000000000000000000011dcf6b1af3d3b2"),
            blockHash = Hash.ZERO,
            blockNumber = 1,
            blockTimestamp = 1,
            transactionHash = Hash.ZERO,
            transactionIndex = 0,
            logIndex = 0,
            removed = false,
        )

        val invalidLog = Log(
            address = Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"),
            topics = listOf(Hash("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")),
            data = Bytes("0x01"),
            blockHash = Hash.ZERO,
            blockNumber = 1,
            blockTimestamp = 1,
            transactionHash = Hash.ZERO,
            transactionIndex = 0,
            logIndex = 0,
            removed = false,
        )

        test("toEventOrNull with list of factories matches first") {
            val factories = listOf(Transfer, LogNote)
            val event = validTransferLog.toEventOrNull(factories)
            event shouldBe Transfer(
                from = Address("0x855f02967ee16e9f18d388b07b4c75211e73e8c2"),
                to = Address("0xed12310d5a37326e6506209c4838146950166760"),
                amount = BigInteger("80448427283174322"),
                log = validTransferLog,
            )
        }

        test("toEventOrNull with list of factories returns null when none match") {
            val factories = listOf(Transfer, LogNote)
            invalidLog.toEventOrNull(factories) shouldBe null
        }

        test("toEventOrNull with iterable of factories") {
            val factories: Iterable<EventFactory<out ContractEvent>> = setOf(Transfer, LogNote)
            val event = validTransferLog.toEventOrNull(factories)
            (event is Transfer) shouldBe true
        }

        test("toEventOrNull with iterable returns null when none match") {
            val factories: Iterable<EventFactory<out ContractEvent>> = setOf(Transfer, LogNote)
            invalidLog.toEventOrNull(factories) shouldBe null
        }

        test("toEventOrNull with vararg factories") {
            val event = validTransferLog.toEventOrNull(Transfer, LogNote)
            (event is Transfer) shouldBe true
        }

        test("toEventOrNull with vararg returns null when none match") {
            invalidLog.toEventOrNull(Transfer, LogNote) shouldBe null
        }
    }

    context("List/Array EventFactory decode extension") {
        val validTransferLog = Log(
            address = Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"),
            topics = listOf(
                Transfer.abi.topicId,
                Hash("0x000000000000000000000000855f02967ee16e9f18d388b07b4c75211e73e8c2"),
                Hash("0x000000000000000000000000ed12310d5a37326e6506209c4838146950166760"),
            ),
            data = Bytes("0x000000000000000000000000000000000000000000000000011dcf6b1af3d3b2"),
            blockHash = Hash.ZERO,
            blockNumber = 1,
            blockTimestamp = 1,
            transactionHash = Hash.ZERO,
            transactionIndex = 0,
            logIndex = 0,
            removed = false,
        )

        val invalidLog = Log(
            address = Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"),
            topics = listOf(Hash("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")),
            data = Bytes("0x01"),
            blockHash = Hash.ZERO,
            blockNumber = 1,
            blockTimestamp = 1,
            transactionHash = Hash.ZERO,
            transactionIndex = 0,
            logIndex = 0,
            removed = false,
        )

        test("List<EventFactory>.decode matches") {
            val factories = listOf(Transfer, LogNote)
            val event = factories.decode(validTransferLog)
            (event is Transfer) shouldBe true
        }

        test("List<EventFactory>.decode returns null when none match") {
            val factories = listOf(Transfer, LogNote)
            factories.decode(invalidLog) shouldBe null
        }

        test("Array<EventFactory>.decode matches") {
            val factories = arrayOf<EventFactory<out ContractEvent>>(Transfer, LogNote)
            val event = factories.decode(validTransferLog)
            (event is Transfer) shouldBe true
        }

        test("Array<EventFactory>.decode returns null when none match") {
            val factories = arrayOf<EventFactory<out ContractEvent>>(Transfer, LogNote)
            factories.decode(invalidLog) shouldBe null
        }

        test("Iterable<EventFactory>.decode matches") {
            val factories: Iterable<EventFactory<out ContractEvent>> = setOf(Transfer, LogNote)
            val event = factories.decode(validTransferLog)
            (event is Transfer) shouldBe true
        }

        test("Iterable<EventFactory>.decode returns null when none match") {
            val factories: Iterable<EventFactory<out ContractEvent>> = setOf(Transfer, LogNote)
            factories.decode(invalidLog) shouldBe null
        }
    }
}) {
    private data class LogNote(
        val sig: Bytes,
        val usr: Address,
        val arg1: Bytes,
        val arg2: Bytes,
        val `data`: Bytes,
        override val log: Log,
    ) : ContractEvent {
        companion object : EventFactory<LogNote> {
            override val abi: AbiEvent = AbiEvent(
                "LogNote",
                listOf(
                    AbiEvent.Token(AbiType.FixedBytes(4), true),
                    AbiEvent.Token(AbiType.Address, true),
                    AbiEvent.Token(AbiType.FixedBytes(32), true),
                    AbiEvent.Token(AbiType.FixedBytes(32), true),
                    AbiEvent.Token(AbiType.FixedBytes(32), false),
                ),
                true,
            )

            override fun filter(provider: Middleware): AnonymousEventFilter<LogNote> = AnonymousEventFilter(provider, this)

            override fun decode(log: Log, data: List<Any>): LogNote {
                return LogNote(
                    data[0] as Bytes,
                    data[1] as Address,
                    data[2] as Bytes,
                    data[3] as Bytes,
                    data[4] as Bytes,
                    log,
                )
            }
        }
    }

    private data class Transfer(
        val from: Address,
        val to: Address,
        val amount: BigInteger,
        override val log: Log,
    ) : ContractEvent {
        companion object : EventFactory<Transfer> {
            override val abi: AbiEvent = AbiEvent(
                "Transfer",
                listOf(
                    AbiEvent.Token(AbiType.Address, true),
                    AbiEvent.Token(AbiType.Address, true),
                    AbiEvent.Token(AbiType.UInt(256), false),
                ),
                false,
            )

            override fun filter(provider: Middleware): EventFilter<Transfer> {
                return EventFilter(provider, this)
            }

            override fun decode(log: Log, data: List<Any>): Transfer {
                return Transfer(data[0] as Address, data[1] as Address, data[2] as BigInteger, log)
            }
        }
    }
}
