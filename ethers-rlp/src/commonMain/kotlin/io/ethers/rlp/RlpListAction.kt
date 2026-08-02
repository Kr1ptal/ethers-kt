package io.ethers.rlp

/**
 * Supplies the body of an RLP list to [RlpEncoder.encodeList].
 *
 * Replaces `java.lang.Runnable`, which resolved without an import only because `java.lang` is auto-imported on
 * the JVM. A Kotlin `fun interface` keeps the same lambda syntax for Java callers and works on every platform.
 */
fun interface RlpListAction {
    fun run()
}
