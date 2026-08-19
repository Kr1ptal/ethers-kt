package io.ethers.abigen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName
import kotlin.reflect.KClass

/**
 * [ClassName] of the multiplatform `BigInteger`, used for all `int`/`uint` ABI types in generated code.
 * */
internal val BIG_INTEGER = ClassName("io.github.artificialpb.bignum", "BigInteger")

/**
 * [ClassName] of the multiplatform `BigDecimal`.
 * */
internal val BIG_DECIMAL = ClassName("io.github.artificialpb.bignum", "BigDecimal")

/**
 * The bignum-kt types are `actual typealias`es of the `java.math` types on JVM/Android, so reflecting over a
 * [KClass] of one of them yields the `java.math` class and, in turn, a `java.math` import in the generated code.
 * Native targets have their own implementation and no such alias, so generated sources must reference the
 * multiplatform declarations instead. This maps the aliased classes back to them.
 * */
internal fun KClass<*>.asApiClassName(): ClassName = when (this.qualifiedName) {
    "java.math.BigInteger" -> BIG_INTEGER
    "java.math.BigDecimal" -> BIG_DECIMAL
    else -> asClassName()
}
