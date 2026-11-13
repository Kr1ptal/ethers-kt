package io.ethers.solana.idl.test

import io.ethers.core.FastHex
import io.ethers.solana.codec.SolanaOption
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
import io.github.artificialpb.bignum.BigInteger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private fun hex(value: String): ByteArray = FastHex.decode(value.replace(" ", ""))

private val RENT = SolanaAddress("SysvarRent111111111111111111111111111111111")

// Expected encodings are built by hand from the Borsh rules, independently of the generated codecs.
class ConstantsBindingsTest : FunSpec({
    test("struct, tuple struct and fieldless struct constants") {
        Constants.ORIGIN shouldBe Constants.Point(x = 0, y = -5)
        Constants.PAIR shouldBe Constants.Duo(255, Long.MIN_VALUE)
        Constants.MARKER shouldBe Constants.Marker
    }

    test("enum constants of each variant form") {
        Constants.KIND_PLAIN shouldBe Constants.Kind.Plain
        Constants.KIND_TUPLE shouldBe Constants.Kind.Tuple(7, -300)
        Constants.KIND_NAMED shouldBe Constants.Kind.Named(lo = BigInteger("18446744073709551615"), hi = null)
    }

    test("a struct constant holding every kind of field") {
        val limits = Constants.LIMITS
        limits.maxAmount shouldBe BigInteger("18446744073709551615")
        limits.big shouldBe BigInteger("-170141183460469231731687303715884105728")
        limits.small shouldBe Byte.MIN_VALUE
        limits.short shouldBe Short.MAX_VALUE
        limits.word shouldBe 4294967295L
        limits.ratio shouldBe 0.5f
        limits.scale shouldBe -1e-7
        limits.name shouldBe "fee \"bps\"\n"
        limits.owner shouldBe RENT
        limits.seed shouldBe SolanaBytes.fromHex("01020304")
        limits.raw shouldBe SolanaBytes.fromHex("ff00")
        limits.tags shouldBe listOf("a", "")
        limits.points shouldBe listOf(Constants.Point(1, -1), Constants.Point(Int.MIN_VALUE, Int.MAX_VALUE))
        limits.cap shouldBe 65535
        limits.nested shouldBe SolanaOption.Some(null)
        limits.kind shouldBe Constants.Kind.Named(lo = BigInteger("7"), hi = 9L)
        limits.marker shouldBe Constants.Marker
        limits.pair shouldBe Constants.Duo(1, Long.MIN_VALUE)
        limits.enabled shouldBe true
        limits.amount shouldBe BigInteger("42")
    }

    test("constants encode to the bytes their Rust values have") {
        Constants.Point.encode(Constants.ORIGIN) shouldBe hex("00000000 fbffffff")
        Constants.Duo.encode(Constants.PAIR) shouldBe hex("ff 0000000000000080")
        Constants.Kind.encode(Constants.KIND_PLAIN) shouldBe hex("00")
        Constants.Kind.encode(Constants.KIND_TUPLE) shouldBe hex("01 07 d4fe")
        Constants.Kind.encode(Constants.KIND_NAMED) shouldBe hex("02 ffffffffffffffff 00")
        // a C layout pads the u8 to the u32's alignment
        Constants.Reading.encode(Constants.READING) shouldBe hex("01 000000 ffffffff")
        Constants.Limits.encode(Constants.LIMITS) shouldBe hex(
            "ffffffffffffffff 00000000000000000000000000000080 80 ff7f ffffffff 0000003f 48afbc9af2d77abe " +
                "0a000000 6665652022627073220a " +
                "06a7d517192c5c51218cc94c3d4af17f58daee089ba1fd44e3dbd98a00000000 " +
                "01020304 02000000ff00 02000000 0100000061 00000000 " +
                "01000000ffffffff 00000080ffffff7f 01ffff 0100 02 0700000000000000 01 09000000 " +
                "01 0000000000000080 01 2a00000000000000",
        )
        Constants.Limits.decode(Constants.Limits.encode(Constants.LIMITS)).unwrap() shouldBe Constants.LIMITS
    }

    test("array and vec constants") {
        Constants.PRIMES shouldBe listOf(2, 3, 5, 7)
        Constants.SEED shouldBe SolanaBytes.fromHex("01020304")
        Constants.MATRIX shouldBe listOf(listOf<Byte>(-1, 2), listOf<Byte>(3, -128))
        Constants.NAMES shouldBe listOf("a\"b", "😀", "tab\t", "ünï", "\\\$x")
        Constants.EMPTY shouldBe emptyList()
        Constants.WORDS shouldBe listOf(0L, 4294967295L)
        Constants.LONGS shouldBe listOf(Long.MIN_VALUE, Long.MAX_VALUE)
        Constants.SHORTS shouldBe listOf(Short.MIN_VALUE, Short.MAX_VALUE)
        Constants.ADDRESSES shouldBe listOf(SolanaAddress("11111111111111111111111111111111"), RENT)
        Constants.AMOUNTS shouldBe listOf(BigInteger("1"), BigInteger("2"))
        Constants.KINDS shouldBe listOf(Constants.Kind.Plain, Constants.Kind.Tuple(1, 2))
        Constants.BLOBS shouldBe listOf(SolanaBytes.fromHex(""), SolanaBytes.fromHex("0102"))
    }

    test("float constants keep their exact values, signed zero and the special values") {
        val doubles = Constants.DOUBLES
        doubles.size shouldBe 7
        doubles.subList(0, 4) shouldBe listOf(1.0, -0.0, 1e-7, 1e16)
        (1.0 / doubles[1]) shouldBe Double.NEGATIVE_INFINITY
        doubles[4] shouldBe Double.POSITIVE_INFINITY
        doubles[5] shouldBe Double.NEGATIVE_INFINITY
        doubles[6].isNaN() shouldBe true
        Constants.FLOATS shouldBe listOf(0.1f, Float.MAX_VALUE, Float.NEGATIVE_INFINITY)
    }

    test("option constants, nested ones keeping Some(None) apart from None") {
        Constants.MAYBE shouldBe BigInteger("5")
        Constants.NOTHING.shouldBeNull()
        Constants.MAYBE_STRING shouldBe "abc"
        Constants.MAYBE_ARRAY shouldBe SolanaBytes.fromHex("01020304")
        Constants.MAYBE_POINT shouldBe Constants.Point(3, 4)
        Constants.SOME_NONE shouldBe SolanaOption.Some(null)
        Constants.SOME_SOME shouldBe SolanaOption.Some(false)
        Constants.NONE_NESTED shouldBe SolanaOption.None
    }

    test("a legacy IDL's token text, with spaces between tokens and literal suffixes, reads the same") {
        Constants.TOKEN_SPACED shouldBe listOf(1, 2000)
    }
})
