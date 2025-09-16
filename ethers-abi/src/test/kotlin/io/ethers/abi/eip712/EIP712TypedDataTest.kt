package io.ethers.abi.eip712

import io.ethers.abi.Inbox
import io.ethers.abi.Mail
import io.ethers.abi.Person
import io.ethers.core.Jackson
import io.ethers.core.types.Address
import io.ethers.core.types.Hash
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigInteger

class EIP712TypedDataTest : FunSpec({
    context("EIP712TypedData.from") {
        test("creates complete typed data from simple struct") {
            val person = Person("Alice", Address.ZERO)
            val domain = EIP712Domain(name = "TestDApp", version = "1.0")

            val typedData = EIP712TypedData.from(person, domain)

            typedData.primaryType shouldBe "Person"
            typedData.domain shouldBe domain
            typedData.message shouldBe mapOf(
                "wallet" to "0x0000000000000000000000000000000000000000",
                "name" to "Alice",
            )
            typedData.types shouldBe EIP712Codec.toTypeMap(person) + EIP712Codec.toTypeMap(domain)
        }

        test("creates complete typed data from nested struct") {
            val from = Person("Bob", Address.ZERO)
            val to = Person("Alice", Address("0x1111111111111111111111111111111111111111"))
            val mail = Mail(from, to, "Hello")
            val domain = EIP712Domain(name = "MailDApp")

            val typedData = EIP712TypedData.from(mail, domain)

            typedData.primaryType shouldBe "Mail"
            typedData.domain shouldBe domain
            typedData.types shouldBe EIP712Codec.toTypeMap(mail) + EIP712Codec.toTypeMap(domain)
            typedData.message shouldBe mapOf(
                "from" to mapOf(
                    "wallet" to "0x0000000000000000000000000000000000000000",
                    "name" to "Bob",
                ),
                "to" to mapOf(
                    "wallet" to "0x1111111111111111111111111111111111111111",
                    "name" to "Alice",
                ),
                "contents" to "Hello",
            )
        }

        test("creates complete typed data from struct with arrays") {
            val person = Person("Alice", Address.ZERO)
            val mail = Mail(person, person, "Test")
            val inbox = Inbox("Test Inbox", listOf(mail))
            val domain = EIP712Domain(name = "InboxDApp")

            val typedData = EIP712TypedData.from(inbox, domain)

            typedData.primaryType shouldBe "Inbox"
            typedData.domain shouldBe domain
            typedData.types shouldBe EIP712Codec.toTypeMap(inbox) + EIP712Codec.toTypeMap(domain)
            typedData.message shouldBe mapOf(
                "name" to "Test Inbox",
                "mails" to listOf(
                    mapOf(
                        "from" to mapOf(
                            "wallet" to "0x0000000000000000000000000000000000000000",
                            "name" to "Alice",
                        ),
                        "to" to mapOf(
                            "wallet" to "0x0000000000000000000000000000000000000000",
                            "name" to "Alice",
                        ),
                        "contents" to "Test",
                    ),
                ),
            )
        }

        test("creates typed data from EIP712Domain itself") {
            val domain = EIP712Domain(
                name = "TestDApp",
                version = "1.0",
                chainId = BigInteger.valueOf(1),
            )

            val typedData = EIP712TypedData.from(domain, domain)

            typedData.primaryType shouldBe "EIP712Domain"
            typedData.domain shouldBe domain
            typedData.message shouldBe mapOf(
                "name" to "TestDApp",
                "version" to "1.0",
                "chainId" to "1",
            )
        }
    }

    context("JSON serialization") {
        val mapper = Jackson.MAPPER

        test("roundtrip serialization of simple typed data") {
            val person = Person("Alice", Address.ZERO)
            val domain = EIP712Domain(name = "TestDApp", version = "1.0")
            val typedData = EIP712TypedData.from(person, domain)

            val json = mapper.writeValueAsString(typedData)
            val deserialized = mapper.readValue(json, EIP712TypedData::class.java)

            deserialized shouldBe typedData
        }

        test("roundtrip serialization of nested typed data") {
            val from = Person("Bob", Address.ZERO)
            val to = Person("Alice", Address("0x1111111111111111111111111111111111111111"))
            val mail = Mail(from, to, "Hello")
            val domain = EIP712Domain(
                name = "MailDApp",
                version = "2.0",
                chainId = BigInteger.valueOf(1),
                verifyingContract = Address("0x2222222222222222222222222222222222222222"),
            )
            val typedData = EIP712TypedData.from(mail, domain)

            val json = mapper.writeValueAsString(typedData)
            val deserialized = mapper.readValue(json, EIP712TypedData::class.java)

            deserialized shouldBe typedData
        }

        test("roundtrip serialization of typed data with arrays") {
            val person = Person("Alice", Address.ZERO)
            val mail1 = Mail(person, person, "Test 1")
            val mail2 = Mail(person, person, "Test 2")
            val inbox = Inbox("Test Inbox", listOf(mail1, mail2))
            val domain = EIP712Domain(name = "InboxDApp", chainId = BigInteger.TEN)
            val typedData = EIP712TypedData.from(inbox, domain)

            val json = mapper.writeValueAsString(typedData)
            val deserialized = mapper.readValue(json, EIP712TypedData::class.java)

            deserialized shouldBe typedData
        }

        test("roundtrip serialization of EIP712Field") {
            val field = EIP712Field("testName", "uint256")

            val json = mapper.writeValueAsString(field)
            val deserialized = mapper.readValue(json, EIP712Field::class.java)

            deserialized shouldBe field
        }

        test("JSON structure of serialized EIP712TypedData") {
            val person = Person("Test", Address.ZERO)
            val domain = EIP712Domain(name = "Test", version = "1")
            val typedData = EIP712TypedData.from(person, domain)

            val json = mapper.writeValueAsString(typedData)
            val jsonNode = mapper.readTree(json)

            jsonNode.has("primaryType") shouldBe true
            jsonNode.has("types") shouldBe true
            jsonNode.has("message") shouldBe true
            jsonNode.has("domain") shouldBe true

            jsonNode["primaryType"].asText() shouldBe "Person"
            jsonNode["types"]["Person"].isArray shouldBe true
            jsonNode["types"]["Person"].size() shouldBe 2
            jsonNode["types"]["EIP712Domain"].isArray shouldBe true
        }

        test("handles null values in domain correctly") {
            val domain = EIP712Domain(name = "Test") // Only name is set
            val person = Person("Alice", Address.ZERO)
            val typedData = EIP712TypedData.from(person, domain)

            val json = mapper.writeValueAsString(typedData)
            val deserialized = mapper.readValue(json, EIP712TypedData::class.java)

            deserialized.domain shouldBe domain
        }

        test("numeric values are formatted as decimal strings") {
            val domain = EIP712Domain(
                name = "NumericTest",
                version = "1.0",
                chainId = BigInteger("12345678901234567890"), // Large number to test decimal formatting
                verifyingContract = Address("0x1234567890abcdef1234567890abcdef12345678"),
            )
            val typedData = EIP712TypedData.from(domain, domain)

            val json = mapper.writeValueAsString(typedData)
            val jsonNode = mapper.readTree(json)

            // Verify chainId is decimal string, not hex
            val chainIdInMessage = jsonNode["message"]["chainId"].asText()
            chainIdInMessage shouldBe "12345678901234567890"

            // Verify chainId in domain is hex string as per EIP712Domain serialization
            val chainIdInDomain = jsonNode["domain"]["chainId"].asText()
            chainIdInDomain shouldBe "0xab54a98ceb1f0ad2"
        }
    }

    context("EIP712 encoding") {
        test("Seaport 1.1 OrderComponents encodes and hashes correctly") {
            val types = mapOf(
                "OrderComponents" to listOf(
                    EIP712Field("offerer", "address"),
                    EIP712Field("zone", "address"),
                    EIP712Field("offer", "OfferItem[]"),
                    EIP712Field("startTime", "uint256"),
                    EIP712Field("endTime", "uint256"),
                    EIP712Field("zoneHash", "bytes32"),
                    EIP712Field("salt", "uint256"),
                    EIP712Field("conduitKey", "bytes32"),
                    EIP712Field("counter", "uint256"),
                ),
                "OfferItem" to listOf(
                    EIP712Field("token", "address"),
                ),
                "ConsiderationItem" to listOf(
                    EIP712Field("token", "address"),
                    EIP712Field("identifierOrCriteria", "uint256"),
                    EIP712Field("startAmount", "uint256"),
                    EIP712Field("endAmount", "uint256"),
                    EIP712Field("recipient", "address"),
                ),
            )

            val message = mapOf(
                "offerer" to "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
                "zone" to "0x004C00500000aD104D7DBd00e3ae0A5C00560C00",
                "offer" to listOf(
                    mapOf(
                        "token" to "0xA604060890923Ff400e8c6f5290461A83AEDACec",
                    ),
                ),
                "startTime" to "1658645591",
                "endTime" to "1659250386",
                "zoneHash" to "0x0000000000000000000000000000000000000000000000000000000000000000",
                "salt" to "16178208897136618",
                "conduitKey" to "0x0000007b02230091a7ed01230072f7006a004d60a8d4e71d599b8104250f0000",
                "totalOriginalConsiderationItems" to "2",
                "counter" to "0",
            )

            val domain = EIP712Domain(
                "Seaport",
                "1.1",
                BigInteger.ONE,
                Address("0x00000000006c3852cbEf3e08E8dF289169EdE581"),
            )

            val typeData = EIP712TypedData("OrderComponents", types, message, domain)

            val sigHash = typeData.signatureHash()
            Hash(sigHash) shouldBe Hash("0x0b8aa9f3712df0034bc29fe5b24dd88cfdba02c7f499856ab24632e2969709a8")

            // Verify type encoding includes all dependent types
            val encodedType = EIP712Codec.encodeType(typeData.primaryType, typeData.types)
            encodedType shouldBe "OrderComponents(address offerer,address zone,OfferItem[] offer,uint256 startTime,uint256 endTime,bytes32 zoneHash,uint256 salt,bytes32 conduitKey,uint256 counter)OfferItem(address token)"

            val json = Jackson.MAPPER.writeValueAsString(typeData)
            Jackson.MAPPER.readValue(json, EIP712TypedData::class.java) shouldBe typeData
        }

        test("Seaport 1.6 OrderComponents encodes and hashes correctly") {
            val types = mapOf(
                "OrderComponents" to listOf(
                    EIP712Field("offerer", "address"),
                    EIP712Field("zone", "address"),
                    EIP712Field("offer", "OfferItem[]"),
                    EIP712Field("consideration", "ConsiderationItem[]"),
                    EIP712Field("orderType", "uint8"),
                    EIP712Field("startTime", "uint256"),
                    EIP712Field("endTime", "uint256"),
                    EIP712Field("zoneHash", "bytes32"),
                    EIP712Field("salt", "uint256"),
                    EIP712Field("conduitKey", "bytes32"),
                    EIP712Field("counter", "uint256"),
                ),
                "OfferItem" to listOf(
                    EIP712Field("itemType", "uint8"),
                    EIP712Field("token", "address"),
                    EIP712Field("identifierOrCriteria", "uint256"),
                    EIP712Field("startAmount", "uint256"),
                    EIP712Field("endAmount", "uint256"),
                ),
                "ConsiderationItem" to listOf(
                    EIP712Field("itemType", "uint8"),
                    EIP712Field("token", "address"),
                    EIP712Field("identifierOrCriteria", "uint256"),
                    EIP712Field("startAmount", "uint256"),
                    EIP712Field("endAmount", "uint256"),
                    EIP712Field("recipient", "address"),
                ),
            )

            val message = mapOf(
                "offerer" to "0x06Dcd83c991f83E2F8d5a43e62286f70c735EED5",
                "zone" to "0x004C00500000aD104D7DBd00e3ae0A5C00560C00",
                "orderType" to "0",
                "offer" to listOf(
                    mapOf(
                        "itemType" to "2",
                        "token" to "0xA604060890923Ff400e8c6f5290461A83AEDACec",
                        "identifierOrCriteria" to "2",
                        "startAmount" to "1000000000000000000",
                        "endAmount" to "3000000000000000000",
                    ),
                ),
                "consideration" to listOf(
                    mapOf(
                        "itemType" to "1",
                        "token" to "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
                        "identifierOrCriteria" to "1",
                        "startAmount" to "100000000000",
                        "endAmount" to "300000000000",
                        "recipient" to "0x6225dd7302a5aa91116a057a03722a2b12b87337",
                    ),
                ),
                "startTime" to "1757856850",
                "endTime" to "2757856850",
                "zoneHash" to "0x0000000000000000000000000000000000000000000000000000000000000000",
                "salt" to "16178208897136618",
                "conduitKey" to "0x0000007b02230091a7ed01230072f7006a004d60a8d4e71d599b8104250f0000",
                "counter" to "0",
            )

            val domain = EIP712Domain(
                "Seaport",
                "1.6",
                BigInteger.ONE,
                Address("0x0000000000000068F116a894984e2DB1123eB395"),
            )

            val typeData = EIP712TypedData("OrderComponents", types, message, domain)

            val sigHash = typeData.signatureHash()
            Hash(sigHash) shouldBe Hash("0x820e4aeecf9a354e7831a0e4f3c943766641380ed70b0b8ee1eee3d9431d28f7")

            // Verify type encoding includes all dependent types
            val encodedType = EIP712Codec.encodeType(typeData.primaryType, typeData.types)
            encodedType shouldBe "OrderComponents(address offerer,address zone,OfferItem[] offer,ConsiderationItem[] consideration,uint8 orderType,uint256 startTime,uint256 endTime,bytes32 zoneHash,uint256 salt,bytes32 conduitKey,uint256 counter)ConsiderationItem(uint8 itemType,address token,uint256 identifierOrCriteria,uint256 startAmount,uint256 endAmount,address recipient)OfferItem(uint8 itemType,address token,uint256 identifierOrCriteria,uint256 startAmount,uint256 endAmount)"

            val json = Jackson.MAPPER.writeValueAsString(typeData)
            Jackson.MAPPER.readValue(json, EIP712TypedData::class.java) shouldBe typeData
        }
    }
})
