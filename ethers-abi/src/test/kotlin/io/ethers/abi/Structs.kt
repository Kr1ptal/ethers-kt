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

data class Mail(val from: Person, val to: Person, val contents: String) : ContractStruct {
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
        )

        override fun fromTuple(data: List<Any>): Mail {
            return Mail(
                data[0] as Person,
                data[1] as Person,
                data[2] as String,
            )
        }
    }
}

data class Person(val name: String, val wallet: Address) : ContractStruct {
    override val tuple: List<Any> = listOf(name, wallet)
    override val abiType: AbiType.Struct<*>
        get() = abi

    companion object : StructFactory<Person> {
        override val abi: AbiType.Struct<Person> = AbiType.Struct(
            Person::class.java,
            ::fromTuple,
            AbiType.Struct.Field("name", AbiType.String),
            AbiType.Struct.Field("wallet", AbiType.Address),
        )

        override fun fromTuple(data: List<Any>): Person {
            return Person(data[0] as String, data[1] as Address)
        }
    }
}
