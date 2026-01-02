package io.ethers.abi

import com.ditchoom.buffer.AllocationZone
import com.ditchoom.buffer.ByteOrder
import com.ditchoom.buffer.PlatformBuffer
import com.ditchoom.buffer.allocate
import com.ditchoom.buffer.wrap
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Prototype test to verify DitchOoM/buffer API compatibility for ByteBuffer migration.
 * These tests verify all the critical operations needed for AbiCodec, RlpEncoder, etc.
 */
class BufferApiTest : FunSpec({

    test("sequential write and read") {
        val buffer = PlatformBuffer.allocate(32, AllocationZone.Heap, ByteOrder.BIG_ENDIAN)
        buffer.write(0x42.toByte())
        buffer.write(byteArrayOf(1, 2, 3, 4))
        buffer.write(12345) // Int

        buffer.resetForRead()
        buffer.readByte() shouldBe 0x42.toByte()
        buffer.readByteArray(4) shouldBe byteArrayOf(1, 2, 3, 4)
        buffer.readInt() shouldBe 12345
    }

    test("position manipulation - read position") {
        val buffer = PlatformBuffer.allocate(32, AllocationZone.Heap, ByteOrder.BIG_ENDIAN)
        buffer.write(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        buffer.resetForRead()

        // Can we get/set position?
        buffer.position() shouldBe 0
        buffer.position(4)
        buffer.readByte() shouldBe 5.toByte()
    }

    test("indexed write - CRITICAL for RlpEncoder") {
        val buffer = PlatformBuffer.allocate(8, AllocationZone.Heap, ByteOrder.BIG_ENDIAN)
        buffer.write(byteArrayOf(0, 0, 0, 0))

        // Can we write at specific index without changing write position?
        buffer.set(0, 0xAA.toByte())
        buffer.set(2, 0xBB.toByte())

        buffer.resetForRead()
        buffer.readByte() shouldBe 0xAA.toByte()
        buffer.readByte() shouldBe 0.toByte()
        buffer.readByte() shouldBe 0xBB.toByte()
    }

    test("indexed read") {
        val buffer = PlatformBuffer.allocate(8, AllocationZone.Heap, ByteOrder.BIG_ENDIAN)
        buffer.write(byteArrayOf(1, 2, 3, 4))
        buffer.resetForRead()

        // Can we read at specific index?
        buffer.get(0) shouldBe 1.toByte()
        buffer.get(2) shouldBe 3.toByte()
        buffer.get(3) shouldBe 4.toByte()
    }

    test("wrap existing array") {
        val data = byteArrayOf(10, 20, 30, 40)
        val buffer = PlatformBuffer.wrap(data, ByteOrder.BIG_ENDIAN)

        buffer.readByte() shouldBe 10.toByte()
        buffer.readByte() shouldBe 20.toByte()
        buffer.readByte() shouldBe 30.toByte()
        buffer.readByte() shouldBe 40.toByte()
    }

    test("capacity and limit") {
        val buffer = PlatformBuffer.allocate(100, AllocationZone.Heap, ByteOrder.BIG_ENDIAN)
        buffer.capacity shouldBe 100
        buffer.write(byteArrayOf(1, 2, 3, 4))

        // Before resetForRead, limit is capacity
        buffer.limit() shouldBe 100

        // After resetForRead, limit becomes the written amount
        buffer.resetForRead()
        buffer.limit() shouldBe 4
    }

    test("get result as ByteArray") {
        val buffer = PlatformBuffer.allocate(4, AllocationZone.Heap, ByteOrder.BIG_ENDIAN)
        buffer.write(byteArrayOf(1, 2, 3, 4))
        buffer.resetForRead()

        val result = buffer.readByteArray(4)
        result shouldBe byteArrayOf(1, 2, 3, 4)
    }

    test("write partial byte array with offset") {
        val buffer = PlatformBuffer.allocate(8, AllocationZone.Heap, ByteOrder.BIG_ENDIAN)
        val source = byteArrayOf(0, 1, 2, 3, 4, 5)

        // Write bytes 2,3,4 (offset=2, length=3)
        buffer.write(source, 2, 3)

        buffer.resetForRead()
        buffer.readByteArray(3) shouldBe byteArrayOf(2, 3, 4)
    }

    test("big endian int write/read") {
        val buffer = PlatformBuffer.allocate(4, AllocationZone.Heap, ByteOrder.BIG_ENDIAN)
        buffer.write(0x12345678)

        buffer.resetForRead()
        // First byte should be 0x12 (big endian)
        buffer.get(0) shouldBe 0x12.toByte()
        buffer.get(1) shouldBe 0x34.toByte()
        buffer.get(2) shouldBe 0x56.toByte()
        buffer.get(3) shouldBe 0x78.toByte()
    }
})
