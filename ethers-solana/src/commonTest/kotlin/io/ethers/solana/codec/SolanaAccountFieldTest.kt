package io.ethers.solana.codec

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

private class Holder

private fun u16(name: String, offset: Int) = SolanaAccountField<Holder, Int>(name, offset, 2, { it.readU16() }, { writeU16(it) })

/** A `[[u16; 3]; 2]` field at [offset], whose elements are arrays of three u16 elements. */
private fun grid(offset: Int) = SolanaAccountArrayField<Holder, List<List<Int>>, SolanaAccountArrayField<Holder, List<Int>, SolanaAccountField<Holder, Int>>>(
    "grid",
    offset,
    2,
    6,
    { reader -> reader.readArray(2) { readArray(3) { readU16() } } },
    { writeArray(it, 2) { row -> writeArray(row, 3) { writeU16(it) } } },
) { rowName, rowOffset ->
    SolanaAccountArrayField(rowName, rowOffset, 3, 2, { reader -> reader.readArray(3) { readU16() } }, { writeArray(it, 3) { writeU16(it) } }) { name, offset ->
        u16(name, offset)
    }
}

class SolanaAccountFieldTest : FunSpec({
    test("descriptors with the same name, offset and size are equal, so a path built twice is one field") {
        u16("a", 4) shouldBe u16("a", 4)
        u16("a", 4).hashCode() shouldBe u16("a", 4).hashCode()
        u16("a", 4) shouldNotBe u16("b", 4)
        u16("a", 4) shouldNotBe u16("a", 6)
        grid(0)[1][2] shouldBe grid(0)[1][2]
        setOf(grid(0)[1][2], grid(0)[1][2]).size shouldBe 1
    }

    test("array elements are named by index, at the array's offset plus the index times the element size") {
        val grid = grid(10)
        grid.size shouldBe 12
        grid[0].offset shouldBe 10
        grid[1].offset shouldBe 16
        grid[1].size shouldBe 6
        grid[1][2].offset shouldBe 20
        grid[1][2].size shouldBe 2
        grid[1][2].name shouldBe "grid[1][2]"
    }

    test("array elements read and write the bytes the whole array holds at their offsets") {
        val grid = grid(0)
        val bytes = BorshWriter().apply { grid.write(this, listOf(listOf(1, 2, 3), listOf(4, 5, 6))) }.toByteArray()
        bytes.size shouldBe grid.size
        BorshReader.decode(bytes, 0, true, grid::read).unwrap() shouldBe listOf(listOf(1, 2, 3), listOf(4, 5, 6))
        for (row in 0 until 2) {
            for (column in 0 until 3) {
                val element = grid[row][column]
                BorshReader.decode(bytes, element.offset, false, element::read).unwrap() shouldBe row * 3 + column + 1
            }
        }
        val row = grid[1]
        BorshReader.decode(bytes, row.offset, false, row::read).unwrap() shouldBe listOf(4, 5, 6)
    }

    test("an index outside the array fails") {
        val grid = grid(0)
        shouldThrow<IndexOutOfBoundsException> { grid[2] }
        shouldThrow<IndexOutOfBoundsException> { grid[-1] }
        shouldThrow<IndexOutOfBoundsException> { grid[0][3] }
    }

    test("descriptors take no bytes, nor start before the payload") {
        shouldThrow<IllegalArgumentException> { u16("a", -1) }
        shouldThrow<IllegalArgumentException> { SolanaAccountField<Holder, Int>("a", 0, 0, { it.readU8() }, { writeU8(it) }) }
        shouldThrow<IllegalArgumentException> {
            SolanaAccountArrayField<Holder, List<Int>, SolanaAccountField<Holder, Int>>("a", 0, 0, 2, { emptyList() }, {}) { name, offset -> u16(name, offset) }
        }
        shouldThrow<IllegalArgumentException> {
            SolanaAccountArrayField<Holder, List<Int>, SolanaAccountField<Holder, Int>>("a", 0, 3, 0, { emptyList() }, {}) { name, offset -> u16(name, offset) }
        }
    }
})
