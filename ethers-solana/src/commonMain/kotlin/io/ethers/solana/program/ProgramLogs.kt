package io.ethers.solana.program

import io.ethers.core.FastHex
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
import kotlin.io.encoding.Base64

/** What a transaction's logs report: the data its programs logged, the program that failed, and truncation. */
internal class ProgramLogs(val data: List<ProgramLogs.Data>, val failure: ProgramLogs.Failure?, val truncated: Boolean) {
    /** Data a program logged with `sol_log_data`, which Anchor's `emit!` uses for events. */
    class Data(
        val programId: SolanaAddress,
        val data: SolanaBytes,
        val instructionIndex: Int,
        val logIndex: Int,
        val stackHeight: Int,
    )

    /** The first program that reported failing, which is the one that raised the error. */
    class Failure(
        val programId: SolanaAddress,
        /** The custom error code, or null when the failure was not a custom program error. */
        val code: Long?,
        val instructionIndex: Int,
        val stackHeight: Int,
    )

    companion object {
        /**
         * Follow the runtime's invocation log lines to attribute each `Program data:` line to the program running
         * at that point and to its top-level instruction. Lines this does not recognise are ignored.
         *
         * Lines are classified in place without splitting them, and each program id is decoded at most once, only
         * when it is needed. Without [collectData], data lines are skipped entirely; with [onlyProgram], only the
         * data that program logged is decoded.
         */
        fun parse(logs: List<String>, collectData: Boolean = true, onlyProgram: SolanaAddress? = null): ProgramLogs {
            val only = onlyProgram?.toBase58()
            val addresses = HashMap<String, SolanaAddress>()
            val stack = ArrayList<String>(8)
            var data: ArrayList<Data>? = null
            var failure: Failure? = null
            var truncated = false
            var instructionIndex = -1

            for (logIndex in logs.indices) {
                val line = logs[logIndex]
                if (!line.startsWith(PROGRAM)) {
                    if (line == LOG_TRUNCATED) truncated = true
                    continue
                }
                // the most frequent lines first
                if (line.startsWith(LOG, PROGRAM.length)) continue
                if (line.startsWith(DATA, PROGRAM.length)) {
                    if (!collectData) continue
                    val program = stack.lastOrNull() ?: continue
                    if (only != null && program != only) continue
                    val bytes = decodeBase64Fields(line, PROGRAM.length + DATA.length) ?: continue
                    val address = addresses.address(program) ?: continue
                    (data ?: ArrayList<Data>().also { data = it }).add(Data(address, bytes, instructionIndex, logIndex, stack.size))
                    continue
                }

                // "Program <id> <event>", where the other "Program <word>: ..." lines match no event below
                val idEnd = line.indexOf(' ', PROGRAM.length)
                if (idEnd < 0) continue
                val event = idEnd + 1
                when {
                    line.startsWith(INVOKE, event) -> {
                        val depth = parseDecimal(line, event + INVOKE.length)
                        if (depth <= 0) continue
                        if (depth == 1) instructionIndex++
                        // resynchronise with the reported depth, in case a line was lost
                        while (stack.size >= depth) stack.removeAt(stack.lastIndex)
                        stack.add(line.substring(PROGRAM.length, idEnd))
                    }
                    line.length == event + SUCCESS.length && line.startsWith(SUCCESS, event) -> stack.removeLastOrNull()
                    line.startsWith(FAILED, event) -> {
                        if (failure == null) {
                            val reason = event + FAILED.length
                            val code = if (line.startsWith(CUSTOM_ERROR, reason)) parseHex(line, reason + CUSTOM_ERROR.length) else null
                            val address = addresses.address(line.substring(PROGRAM.length, idEnd))
                            if (address != null) failure = Failure(address, code, instructionIndex, stack.size)
                        }
                        stack.removeLastOrNull()
                    }
                }
            }
            return ProgramLogs(data ?: emptyList(), failure, truncated)
        }

        /** Decode [id] once per parse; a malformed id, which the runtime never logs, decodes to null. */
        private fun HashMap<String, SolanaAddress>.address(id: String): SolanaAddress? {
            get(id)?.let { return it }
            val address = try {
                SolanaAddress(id)
            } catch (e: IllegalArgumentException) {
                return null
            }
            put(id, address)
            return address
        }

        /**
         * `sol_log_data` logs each field as base64, separated by spaces; the fields are joined back together.
         * Anchor logs a single field, which decodes straight from the line.
         */
        private fun decodeBase64Fields(line: String, start: Int): SolanaBytes? = try {
            val space = line.indexOf(' ', start)
            if (space < 0) {
                SolanaBytes.fromBytes(Base64.decode(line, start, line.length))
            } else {
                var joined = ByteArray(0)
                var from = start
                while (from < line.length) {
                    val end = line.indexOf(' ', from).let { if (it < 0) line.length else it }
                    if (end > from) joined += Base64.decode(line, from, end)
                    from = end + 1
                }
                SolanaBytes.fromBytes(joined)
            }
        } catch (e: IllegalArgumentException) {
            null
        }

        /** The decimal number at [start], ending at the first non-digit, or -1 when there is none. */
        private fun parseDecimal(line: String, start: Int): Int {
            var value = 0
            var i = start
            while (i < line.length && i - start < 4) {
                val digit = line[i] - '0'
                if (digit !in 0..9) break
                value = value * 10 + digit
                i++
            }
            return if (i == start) -1 else value
        }

        /** The hexadecimal u32 running from [start] to the end of the line, or null when it is not one. */
        private fun parseHex(line: String, start: Int): Long? {
            val length = line.length - start
            if (length !in 1..8) return null
            val bytes = try {
                FastHex.decode(line, start, length)
            } catch (e: IllegalArgumentException) {
                return null
            } catch (e: IndexOutOfBoundsException) {
                // FastHex indexes its tables by character code, which a non-ASCII character exceeds
                return null
            }
            var value = 0L
            for (byte in bytes) value = (value shl 8) or (byte.toLong() and 0xff)
            return value
        }

        private const val PROGRAM = "Program "
        private const val LOG = "log: "
        private const val DATA = "data: "
        private const val INVOKE = "invoke ["
        private const val SUCCESS = "success"
        private const val FAILED = "failed: "
        private const val CUSTOM_ERROR = "custom program error: 0x"
        private const val LOG_TRUNCATED = "Log truncated"
    }
}
