package io.ethers.solana.idl.test

import io.ethers.solana.codec.BorshReader
import io.ethers.solana.codec.BorshWriter
import io.ethers.solana.codec.SolanaCodec

// Handwritten codecs and types the ledger IDL's generator overrides (src/commonTest/overrides.json) name.

/** A LEB128 varint, so a value's size depends on it. */
object CompressedCodec : SolanaCodec<Ledger.Compressed> {
    override fun read(reader: BorshReader): Ledger.Compressed {
        var value = 0L
        var shift = 0
        while (true) {
            val byte = reader.readU8()
            value = value or ((byte and 0x7f).toLong() shl shift)
            if (byte and 0x80 == 0) return Ledger.Compressed(value)
            shift += 7
        }
    }

    override fun write(writer: BorshWriter, value: Ledger.Compressed) {
        var remaining = value.value
        while (remaining >= 0x80) {
            writer.writeU8(((remaining and 0x7f) or 0x80).toInt())
            remaining = remaining ushr 7
        }
        writer.writeU8(remaining.toInt())
    }
}

/** A big-endian u32: always 4 bytes, aligned to 4. */
object StampCodec : SolanaCodec<Ledger.Stamp> {
    override fun read(reader: BorshReader): Ledger.Stamp {
        var value = 0L
        repeat(4) { value = (value shl 8) or reader.readU8().toLong() }
        return Ledger.Stamp(value)
    }

    override fun write(writer: BorshWriter, value: Ledger.Stamp) {
        for (shift in 24 downTo 0 step 8) writer.writeU8(((value.seconds ushr shift) and 0xff).toInt())
    }
}

/** An enum with a u32 tag, where Borsh writes a u8. */
object SideCodec : SolanaCodec<Ledger.Side> {
    override fun read(reader: BorshReader): Ledger.Side = when (val tag = reader.readU32()) {
        0L -> Ledger.Side.Bid
        1L -> Ledger.Side.Sell(reader.readU8())
        else -> reader.invalidVariant("Side", tag.toInt(), reader.position - 4)
    }

    override fun write(writer: BorshWriter, value: Ledger.Side) {
        when (value) {
            Ledger.Side.Bid -> writer.writeU32(0)
            is Ledger.Side.Sell -> {
                writer.writeU32(1)
                writer.writeU8(value.maxPrice)
            }
        }
    }
}

/** The label before the tag, the reverse of the IDL's field order. */
object TaggedCodec : SolanaCodec<Ledger.TaggedRecord> {
    override fun read(reader: BorshReader): Ledger.TaggedRecord {
        val label = reader.readString()
        return Ledger.TaggedRecord(tag = reader.readU8(), label = label)
    }

    override fun write(writer: BorshWriter, value: Ledger.TaggedRecord) {
        writer.writeString(value.label)
        writer.writeU8(value.tag)
    }
}

/** A token amount in place of the IDL's `u64` alias, held in a signed Long's bits. */
data class TokenAmount(val raw: Long) {
    object Codec : SolanaCodec<TokenAmount> {
        override fun read(reader: BorshReader) = TokenAmount(reader.readI64())

        override fun write(writer: BorshWriter, value: TokenAmount) = writer.writeI64(value.raw)
    }
}

/** A price in place of the IDL's `Price` struct, laid out alike. */
data class PythPrice(val mantissa: Long, val expo: Int)

object PythPriceCodec : SolanaCodec<PythPrice> {
    override fun read(reader: BorshReader) = PythPrice(reader.readI64(), reader.readI32())

    override fun write(writer: BorshWriter, value: PythPrice) {
        writer.writeI64(value.mantissa)
        writer.writeI32(value.expo)
    }
}
