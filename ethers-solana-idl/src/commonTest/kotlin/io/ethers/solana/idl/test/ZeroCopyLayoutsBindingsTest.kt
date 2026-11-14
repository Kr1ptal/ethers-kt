package io.ethers.solana.idl.test

import io.ethers.core.FastHex
import io.ethers.solana.codec.SolanaDecodeError
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private fun hex(value: String): ByteArray = FastHex.decode(value.replace(" ", ""))

private fun zeros(count: Int) = "00".repeat(count)

private fun ones(count: Int) = "ff".repeat(count)

/**
 * The expected bytes and offsets here are derived by hand from Rust's layout rules, not taken from the generator:
 *
 * - `repr(C)`: fields in declaration order, each at the next multiple of its alignment; the struct is aligned to the
 *   largest field alignment, or `align(N)` if larger, and its size rounded up to a multiple of that.
 * - `repr(C, packed)`: fields back to back, alignment 1.
 * - `repr(transparent)`: the layout of the single field.
 *
 * Alignments are those of Solana's on-chain target, whose LLVM data layout is `e-m:e-p:64:64-i64:64-n32:64-S128`:
 * u8 1, u16 2, u32 4, u64 8, and u128/i128 8, as rustc gives 128-bit integers the alignment of the largest
 * integer the data layout names when it names none of 128 bits. A public key is `[u8; 32]`, aligned to 1.
 * Integers are little-endian.
 */
class ZeroCopyLayoutsBindingsTest : FunSpec({
    val u128 = BigInteger("1339673755198158349044581307228491536") // 0x0102030405060708090a0b0c0d0e0f10

    test("an unpacked C layout pads 128-bit integers to 8 bytes, and its size to its 8-byte alignment") {
        val value = ZeroCopyLayouts.WideC(a = 0x11, b = u128, c = 0x2233, d = bigIntegerOf(-2), e = 0x44)
        val data = hex(
            // a: u8 at 0, then 7 bytes to the next multiple of 8
            "11" + zeros(7) +
                // b: u128 at 8, 16 bytes
                "100f0e0d0c0b0a090807060504030201" +
                // c: u16 at 24, then 6 bytes from 26 to the next multiple of 8
                "3322" + zeros(6) +
                // d: i128 at 32, -2 in two's complement
                "fe" + ones(15) +
                // e: u8 at 48, then 7 bytes to a size of 56, a multiple of the struct's alignment of 8
                "44" + zeros(7),
        )
        ZeroCopyLayouts.WideC.encode(value) shouldBe data
        ZeroCopyLayouts.WideC.decode(data).unwrap() shouldBe value
        ZeroCopyLayouts.WideC.ACCOUNT.encode(value) shouldBe hex("0100000000000000") + data

        val fields = ZeroCopyLayouts.WideC.Fields
        listOf(fields.a.offset, fields.b.offset, fields.c.offset, fields.d.offset, fields.e.offset) shouldBe listOf(0, 8, 24, 32, 48)
    }

    test("the trailing padding is part of the layout, so data without it does not decode") {
        val data = ZeroCopyLayouts.WideC.encode(ZeroCopyLayouts.WideC(0, bigIntegerOf(0), 0, bigIntegerOf(0), 0))
        data.size shouldBe 56
        ZeroCopyLayouts.WideC.decode(data.copyOf(49)).unwrapError().shouldBeInstanceOf<SolanaDecodeError.Malformed>()
    }

    test("align(N) raises a struct's alignment, padding its size and its offset within another struct") {
        val inner = ZeroCopyLayouts.AlignedC(x = 0x0201, y = bigIntegerOf(578437695752307201)) // y = 0x0807060504030201
        // x: u16 at 0, 6 bytes to y: u64 at 8, then 16 bytes to a size of 32, as align(32) requires
        val innerData = "0102" + zeros(6) + "0102030405060708" + zeros(16)
        ZeroCopyLayouts.AlignedC.encode(inner) shouldBe hex(innerData)

        val outer = ZeroCopyLayouts.Outer(a = 1, inner = inner, b = 2)
        // a at 0, 31 bytes to inner at 32, b at 64, then 31 bytes to a size of 96, a multiple of 32
        val outerData = hex("01" + zeros(31) + innerData + "02" + zeros(31))
        ZeroCopyLayouts.Outer.encode(outer) shouldBe outerData
        ZeroCopyLayouts.Outer.decode(outerData).unwrap() shouldBe outer
    }

    test("a packed layout has no padding, and a transparent one is aligned like its field") {
        val value = ZeroCopyLayouts.Holder(
            tag = 0xaa,
            wrapped = ZeroCopyLayouts.Wrapper(bigIntegerOf(0x99)),
            packed = ZeroCopyLayouts.PackedC(a = 1, b = bigIntegerOf(2), c = 3, d = bigIntegerOf(4)),
            tail = 0x55667788,
        )
        val data = hex(
            // tag: u8 at 0, then 7 bytes to the 8-byte aligned wrapper of a u128
            "aa" + zeros(7) +
                // wrapped at 8, 16 bytes
                "99" + zeros(15) +
                // packed at 24, aligned to 1: a at +0, b at +1, c at +17, d at +19, 27 bytes in all
                "01" + "02" + zeros(15) + "0300" + "04" + zeros(7) +
                // 51 is no multiple of 4, so 1 byte to tail: u32 at 52, ending at 56, a multiple of 8
                "00" + "88776655",
        )
        ZeroCopyLayouts.Holder.encode(value) shouldBe data
        ZeroCopyLayouts.Holder.decode(data).unwrap() shouldBe value
        ZeroCopyLayouts.PackedC.encode(value.packed).size shouldBe 27

        val fields = ZeroCopyLayouts.Holder.Fields
        listOf(fields.tag.offset, fields.wrapped.offset, fields.packed.offset, fields.tail.offset) shouldBe listOf(0, 8, 24, 52)
        // nested paths follow the same layouts: the transparent field at 8 + 0, and the packed fields at 24 + 0, 1, 17, 19
        fields.wrapped.inner.offset shouldBe 8
        listOf(fields.packed.a.offset, fields.packed.b.offset, fields.packed.c.offset, fields.packed.d.offset) shouldBe listOf(24, 25, 41, 43)
        fields.packed.d.size shouldBe 8
    }

    test("arrays of a padded struct repeat it with its padding") {
        val value = ZeroCopyLayouts.Grid(cells = listOf(ZeroCopyLayouts.Cell(bigIntegerOf(1), 2), ZeroCopyLayouts.Cell(bigIntegerOf(3), 4)), n = 5)
        // each Cell: v: u64 at 0, f: u8 at 8, then 7 bytes to its size of 16; n at 32, then 7 bytes to 40
        val data = hex("0100000000000000" + "02" + zeros(7) + "0300000000000000" + "04" + zeros(7) + "05" + zeros(7))
        ZeroCopyLayouts.Grid.encode(value) shouldBe data
        ZeroCopyLayouts.Grid.decode(data).unwrap() shouldBe value
    }

    test("256-bit integers sit back to back in Pod and packed layouts, which have no padding to place") {
        // derive(Pod) only compiles without padding, so each field directly follows the previous one
        val pod = ZeroCopyLayouts.PodWide(a = bigIntegerOf(1), b = bigIntegerOf(2), c = bigIntegerOf(3), d = bigIntegerOf(-1), e = bigIntegerOf(5))
        val podData = hex("01" + zeros(7) + "02" + zeros(15) + "03" + zeros(31) + ones(32) + "05" + zeros(7))
        ZeroCopyLayouts.PodWide.encode(pod) shouldBe podData
        ZeroCopyLayouts.PodWide.decode(podData).unwrap() shouldBe pod
        val fields = ZeroCopyLayouts.PodWide.Fields
        listOf(fields.a.offset, fields.b.offset, fields.c.offset, fields.d.offset, fields.e.offset) shouldBe listOf(0, 8, 24, 56, 88)

        val packed = ZeroCopyLayouts.PackedWide(a = 7, b = bigIntegerOf(1), c = bigIntegerOf(-1))
        val packedData = hex("07" + "01" + zeros(31) + ones(32))
        ZeroCopyLayouts.PackedWide.encode(packed) shouldBe packedData
        ZeroCopyLayouts.PackedWide.decode(packedData).unwrap() shouldBe packed
    }
})
