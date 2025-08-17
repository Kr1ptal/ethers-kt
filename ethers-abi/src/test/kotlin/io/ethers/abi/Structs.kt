package io.ethers.abi

import io.ethers.core.types.Address

data class Inbox(val name: String, val mails: List<Mail>) : ContractStruct {
    override val tuple: List<Any> = listOf(name, mails)
    override val abiType: AbiType.Struct<*>
        get() = abi

    companion object : StructFactory<Inbox> {
        override val abi: AbiType.Struct<Inbox> = AbiType.Struct(
            Inbox::class.java,
            ::fromTuple,
            AbiType.Struct.Field("name", AbiType.String),
            AbiType.Struct.Field("mails", AbiType.Array(Mail)),
        )

        @Suppress("UNCHECKED_CAST")
        override fun fromTuple(data: List<Any>): Inbox {
            return Inbox(
                data[0] as String,
                data[1] as List<Mail>,
            )
        }
    }
}

data class Mail(val from: Person, val to: Person, val contents: String, val header: Header) : ContractStruct {
    override val tuple: List<Any> = listOf(from, to, contents)
    override val abiType: AbiType.Struct<*>
        get() = abi

    companion object : StructFactory<Mail> {
        override val abi: AbiType.Struct<Mail> = AbiType.Struct(
            Mail::class.java,
            ::fromTuple,
            AbiType.Struct.Field("from", Person),
            AbiType.Struct.Field("to", Person),
            AbiType.Struct.Field("contents", AbiType.String),
            AbiType.Struct.Field("header", Header),
        )

        override fun fromTuple(data: List<Any>): Mail {
            return Mail(
                data[0] as Person,
                data[1] as Person,
                data[2] as String,
                data[3] as Header,
            )
        }
    }
}

data class Header(val header: String) : ContractStruct {
    override val tuple: List<Any> = listOf(header)
    override val abiType: AbiType.Struct<*>
        get() = abi

    companion object : StructFactory<Header> {
        override val abi: AbiType.Struct<Header> = AbiType.Struct(
            Header::class,
            ::fromTuple,
            AbiType.Struct.Field("header", AbiType.String),
        )

        override fun fromTuple(data: List<Any>): Header {
            return Header(data[0] as String)
        }
    }
}

data class Person(val wallet: Address, val name: String) : ContractStruct {
    override val tuple: List<Any> = listOf(wallet, name)
    override val abiType: AbiType.Struct<*>
        get() = abi

    companion object : StructFactory<Person> {
        override val abi: AbiType.Struct<Person> = AbiType.Struct(
            Person::class.java,
            ::fromTuple,
            AbiType.Struct.Field("wallet", AbiType.Address),
            AbiType.Struct.Field("name", AbiType.String),
        )

        override fun fromTuple(data: List<Any>): Person {
            return Person(data[0] as Address, data[1] as String)
        }
    }
}
