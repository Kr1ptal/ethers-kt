package io.ethers.solana.idl.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.BYTE_ARRAY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.joinToCode
import io.ethers.crypto.Base58
import io.ethers.solana.idl.IdlAccountItem
import io.ethers.solana.idl.IdlArrayLength
import io.ethers.solana.idl.IdlConstant
import io.ethers.solana.idl.IdlDiscriminator
import io.ethers.solana.idl.IdlErrorCode
import io.ethers.solana.idl.IdlEvent
import io.ethers.solana.idl.IdlFields
import io.ethers.solana.idl.IdlInstruction
import io.ethers.solana.idl.IdlPaths
import io.ethers.solana.idl.IdlPda
import io.ethers.solana.idl.IdlProgram
import io.ethers.solana.idl.IdlRepr
import io.ethers.solana.idl.IdlSeed
import io.ethers.solana.idl.IdlSerialization
import io.ethers.solana.idl.IdlType
import io.ethers.solana.idl.IdlTypeBody
import io.ethers.solana.idl.IdlTypeDef
import io.ethers.solana.idl.IdlValidator
import java.io.File
import java.math.BigInteger

/**
 * Generates Kotlin bindings for [program] as one file holding an object named [programName] in
 * [packageName].
 *
 * The object contains the program address, constants, a data class or sealed class with a codec
 * companion for each type definition, an account factory for each account, an event hierarchy, an
 * error hierarchy, and for each instruction its arguments, account roles, descriptor, builder and
 * decoded form within an instruction hierarchy, plus program-level dispatch over accounts, events,
 * errors and instructions.
 *
 * Aliases are resolved to their target at each use. Zero-copy types follow their C or transparent
 * layout on Solana's on-chain target, with the padding an unpacked C layout has. Generic type
 * definitions, zero-copy types with a Rust layout and custom serialization are not supported yet and
 * fail generation.
 *
 * Constants of any supported type are generated: primitives as `const val` where Kotlin allows, everything else as a
 * `@JvmField val` constructing the value. A value must be a literal of its declared type in the form Rust's `Debug`
 * writes it, as Anchor records it; expressions, casts and references to other constants fail generation.
 *
 * Each program derived account gets a `find` function deriving its address from its seeds. Integer seeds are
 * encoded in the byte order [seedByteOrders] gives, since IDLs do not record it.
 *
 * When changing the generated output, bump [OUTPUT_VERSION].
 *
 * @throws IllegalArgumentException if [program] is invalid, uses an unsupported layout, or its names
 * produce colliding declarations, or [seedByteOrders] names a seed that is not an integer PDA seed.
 */
class SolanaProgramBuilder @JvmOverloads constructor(
    private val program: IdlProgram,
    private val packageName: String,
    private val programName: String = program.name.toPascalCase(),
    private val seedByteOrders: SeedByteOrders = SeedByteOrders(),
) {
    private val programAddress = requireNotNull(program.address) {
        "program '${program.name}': the IDL declares no address, so one must be given, such as with the Gradle plugin's programAddress"
    }
    private val programClass = ClassName(packageName, programName)
    private val accountMarker = programClass.nestedClass("Account")
    private val eventInterface = programClass.nestedClass("Event")
    private val errorClass = programClass.nestedClass("Error")
    private val instructionHierarchy = programClass.nestedClass("Instruction")

    private val typeDefs = program.types.associateBy { it.name }
    private val accountTypes = program.accounts.mapTo(HashSet()) { it.name }
    private val eventTypes = program.events.mapTo(HashSet()) { it.name }
    private val kotlinTypeNames = kotlinTypeNames()

    /** Computed zero-copy layouts, by type name. */
    private val zeroCopyLayouts = HashMap<String, ZeroCopyLayout>()

    /** A private constant for each distinct constant seed, in order of first use. */
    private val seedConstantNames: Map<List<Int>, String> by lazy {
        val names = LinkedHashMap<List<Int>, String>()
        forEachPda { _, _, pda, _ ->
            pda.seeds.filterIsInstance<IdlSeed.Const>().forEach { names.getOrPut(it.bytes) { "PDA_SEED_${names.size}" } }
        }
        names
    }

    /** A private constant for each distinct constant program id a PDA derives under, in order of first use. */
    private val programConstantNames: Map<List<Int>, String> by lazy {
        val names = LinkedHashMap<List<Int>, String>()
        forEachPda { _, _, pda, _ ->
            (pda.program as? IdlSeed.Const)?.let { names.getOrPut(it.bytes) { "PDA_PROGRAM_${names.size}" } }
        }
        names
    }

    private val pdaConstants: List<PropertySpec> by lazy {
        val seeds = seedConstantNames.map { (bytes, name) ->
            val initializer = if (bytes.isNotEmpty() && bytes.all { it in 0x20..0x7e }) {
                CodeBlock.of("%S.encodeToByteArray()", String(ByteArray(bytes.size) { bytes[it].toByte() }, Charsets.US_ASCII))
            } else {
                CodeBlock.of("byteArrayOf(%L)", bytes.joinToString(", ") { it.toByte().toString() })
            }
            PropertySpec.builder(name, BYTE_ARRAY, KModifier.PRIVATE).initializer(initializer).build()
        }
        val programs = programConstantNames.map { (bytes, name) ->
            val address = Base58.encode(ByteArray(bytes.size) { bytes[it].toByte() })
            PropertySpec.builder(name, Runtime.SOLANA_ADDRESS, KModifier.PRIVATE).initializer("%T(%S)", Runtime.SOLANA_ADDRESS, address).build()
        }
        seeds + programs
    }

    init {
        IdlValidator.validate(program)
        checkSupported()
        checkCollisions()
        checkSeedByteOrders()
    }

    fun build(): FileSpec {
        val programType = TypeSpec.objectBuilder(programClass)
            .addKdoc("Bindings for the %L program, version %L.\n", program.name, program.version)
        program.docs.forEach { programType.addKdoc("\n%L", it) }

        programType.addProperty(
            PropertySpec.builder("PROGRAM_ID", Runtime.SOLANA_ADDRESS)
                .addAnnotation(Runtime.JVM_FIELD)
                .initializer("%T(%S)", Runtime.SOLANA_ADDRESS, programAddress)
                .build(),
        )
        program.constants.forEach { programType.addProperty(constant(it)) }
        pdaConstants.forEach { programType.addProperty(it) }

        for (def in program.types) {
            if (def.body is IdlTypeBody.Alias) continue
            // an event's type is generated as the event unless something else uses it as a plain value
            if (def.name in eventTypes && def.name !in eventTypesUsedAsValues) continue
            programType.addType(typeDefinition(def))
        }

        if (program.accounts.isNotEmpty()) addAccounts(programType)
        if (program.events.isNotEmpty()) addEvents(programType)
        if (program.errors.isNotEmpty()) addErrors(programType)
        addInstructions(programType)

        return FileSpec.builder(programClass)
            .indent("    ")
            // the generated accessors share these names, which would otherwise hide the runtime extensions
            .addAliasedImport(Runtime.FETCH, FETCH_ALIAS)
            .addAliasedImport(Runtime.WATCH, WATCH_ALIAS)
            .addFileComment("Generated from the %L IDL. Do not edit.", program.name)
            .addAnnotation(
                AnnotationSpec.builder(Suppress::class)
                    .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
                    .addMember("%S, %S, %S, %S", "RedundantVisibilityModifier", "RemoveRedundantQualifierName", "unused", "PropertyName")
                    .build(),
            )
            .addType(programType.build())
            .apply { sliceProperties().forEach(::addProperty) }
            .build()
    }

    /**
     * Write the bindings to [directory], under the directories of [packageName].
     *
     * @return the canonical name of the generated program object.
     */
    fun writeTo(directory: File): String {
        build().writeTo(directory)
        return programClass.canonicalName
    }

    // --- checks ---

    private fun checkSupported() {
        for (def in program.types) {
            require(def.generics.isEmpty()) { "type '${def.name}': generic type definitions are not supported yet" }
            when (val serialization = def.serialization) {
                IdlSerialization.Borsh -> Unit
                IdlSerialization.Bytemuck, IdlSerialization.BytemuckUnsafe -> checkZeroCopy(def)
                is IdlSerialization.Custom -> throw IllegalArgumentException("type '${def.name}': $serialization serialization is not supported")
            }
        }
        checkPaddingOnlyInZeroCopy()
    }

    /**
     * Borsh has no padding, so a zero-copy type whose layout has some can only be used within zero-copy types and as an
     * account, never where Borsh encodes it.
     */
    private fun checkPaddingOnlyInZeroCopy() {
        fun check(type: IdlType, where: String) {
            when (val resolved = resolve(type)) {
                is IdlType.Option -> check(resolved.inner, where)
                is IdlType.Vec -> check(resolved.element, where)
                is IdlType.Array -> check(resolved.element, where)
                is IdlType.Defined -> require(!isZeroCopy(resolved.name) || !hasPadding(resolved.name)) {
                    "$where: '${resolved.name}' has padding in its zero-copy layout, so cannot be Borsh-encoded"
                }
                is IdlType.Primitive, is IdlType.Generic -> Unit
            }
        }
        fun check(fields: IdlFields, where: String) = when (fields) {
            IdlFields.None -> Unit
            is IdlFields.Named -> fields.fields.forEach { check(it.type, "$where field '${it.name}'") }
            is IdlFields.Tuple -> fields.types.forEachIndexed { i, type -> check(type, "$where field $i") }
        }
        for (def in program.types) {
            if (isZeroCopy(def.name)) continue
            when (val body = def.body) {
                is IdlTypeBody.Struct -> check(body.fields, "type '${def.name}'")
                is IdlTypeBody.Enum -> body.variants.forEach { check(it.fields, "type '${def.name}' variant '${it.name}'") }
                is IdlTypeBody.Alias -> Unit
            }
        }
        for (event in program.events) {
            require(!isZeroCopy(event.name) || !hasPadding(event.name)) { "event '${event.name}': has padding in its zero-copy layout, so cannot be Borsh-encoded" }
        }
        for (ix in program.instructions) {
            ix.args.forEach { check(it.type, "instruction '${ix.name}' argument '${it.name}'") }
            ix.returns?.let { check(it, "instruction '${ix.name}' return type") }
        }
    }

    /**
     * Require zero-copy [def] to have a C or transparent layout of fixed-size fields. Its codec follows that layout,
     * including the padding an unpacked C layout places before fields and after the last one.
     */
    private fun checkZeroCopy(def: IdlTypeDef) {
        val where = "type '${def.name}'"
        val repr = def.repr
        val supported = when (repr) {
            IdlRepr.Transparent -> true
            is IdlRepr.C -> !repr.packed || (repr.align ?: 1) <= 1
            is IdlRepr.Rust, null -> false
        }
        require(supported) {
            "$where: zero-copy types are only supported with a C or transparent layout, without both packed and align; " +
                "got ${repr ?: "the default Rust layout"}"
        }
        val body = def.body as? IdlTypeBody.Struct ?: throw IllegalArgumentException("$where: zero-copy enums are not supported")
        val aligned = repr is IdlRepr.C && !repr.packed
        when (val fields = body.fields) {
            IdlFields.None -> Unit
            is IdlFields.Named -> fields.fields.forEach { checkZeroCopyField(it.type, "$where field '${it.name}'", aligned) }
            is IdlFields.Tuple -> fields.types.forEachIndexed { i, type -> checkZeroCopyField(type, "$where field $i", aligned) }
        }
    }

    /**
     * Whether [name] is an enum whose variants have no fields and fit a byte, which Rust lays out as that byte, as
     * Borsh writes its tag.
     */
    private fun isByteEnum(name: String): Boolean {
        val body = typeDefs.getValue(name).body as? IdlTypeBody.Enum ?: return false
        return body.variants.size <= 256 && body.variants.all { it.fields.isEmpty() }
    }

    /**
     * Zero-copy fields have one fixed size: no length prefixes or option tags, and only zero-copy structs nested. An
     * [aligned] layout also needs each field's alignment.
     */
    private fun checkZeroCopyField(type: IdlType, where: String, aligned: Boolean) {
        when (val resolved = resolve(type)) {
            IdlType.Primitive.STRING, IdlType.Primitive.BYTES -> throw IllegalArgumentException("$where: a zero-copy type cannot contain $resolved, which varies in size")
            IdlType.Primitive.U256, IdlType.Primitive.I256 -> require(!aligned) { "$where: $resolved has no alignment, so is only supported in packed zero-copy types" }
            is IdlType.Primitive -> Unit
            is IdlType.Array -> checkZeroCopyField(resolved.element, where, aligned)
            is IdlType.Option -> throw IllegalArgumentException("$where: a zero-copy type cannot contain an option")
            is IdlType.Vec -> throw IllegalArgumentException("$where: a zero-copy type cannot contain a vec, which varies in size")
            is IdlType.Defined -> {
                val nested = typeDefs.getValue(resolved.name)
                require(isByteEnum(resolved.name) || nested.serialization == IdlSerialization.Bytemuck || nested.serialization == IdlSerialization.BytemuckUnsafe) {
                    "$where: a zero-copy type can only contain zero-copy types and enums without fields, but '${nested.name}' is ${nested.serialization}"
                }
            }
            is IdlType.Generic -> throw IllegalArgumentException("$where: generic fields are not supported yet")
        }
    }

    /** Declarations in one scope must have distinct names once converted to Kotlin. */
    private fun checkCollisions() {
        val types = HashMap<String, String>()
        fun type(name: String, source: String) {
            val previous = types.put(name, source)
            require(previous == null) { "generated type '$name' is produced by both $previous and $source" }
        }
        kotlinTypeNames.forEach { (idl, kotlin) -> if (typeDefs.getValue(idl).body !is IdlTypeBody.Alias) type(kotlin, "type '$idl'") }
        type("Account", "the account hierarchy")
        type("Event", "the event hierarchy")
        type("Error", "the error hierarchy")
        type("Instruction", "the instruction hierarchy")
        for (ix in program.instructions) {
            val name = ix.name.toPascalCase()
            type("${name}Args", "instruction '${ix.name}'")
            type("${name}Accounts", "instruction '${ix.name}'")
        }

        val members = HashMap<String, String>()
        fun member(name: String, source: String) {
            val previous = members.put(name, source)
            require(previous == null) { "generated member '$name' is produced by both $previous and $source" }
        }
        listOf("PROGRAM_ID", "ACCOUNTS", "EVENTS", "ERRORS", "INSTRUCTIONS").forEach { member(it, "the program") }
        listOf("decodeAccount", "decodeEvent", "decodeError", "decodeInstruction").forEach { member(it, "the program") }
        program.constants.forEach { member(it.name, "constant '${it.name}'") }
        (seedConstantNames.values + programConstantNames.values).forEach { member(it, "the PDA seeds") }
        for (ix in program.instructions) {
            member(ix.name.toUpperSnakeCase(), "instruction '${ix.name}'")
            member(ix.name.toCamelCase(), "instruction '${ix.name}'")
        }
    }

    /** The last path segment of each type, or the whole path where the last segment is ambiguous. */
    private fun kotlinTypeNames(): Map<String, String> {
        val short = program.types.groupBy { it.name.substringAfterLast("::").toPascalCase() }
        return program.types.associate { def ->
            val name = def.name.substringAfterLast("::").toPascalCase()
            def.name to if (short.getValue(name).size == 1) name else def.name.toPascalCase()
        }
    }

    /** Event types that something other than the event itself uses as a value, so also need a plain type. */
    private val eventTypesUsedAsValues: Set<String> by lazy {
        val used = HashSet<String>(accountTypes)
        val pending = ArrayDeque<IdlType>()
        fun enqueue(fields: IdlFields) = when (fields) {
            IdlFields.None -> Unit
            is IdlFields.Named -> fields.fields.forEach { pending.add(it.type) }
            is IdlFields.Tuple -> pending.addAll(fields.types)
        }
        fun enqueue(def: IdlTypeDef) = when (val body = def.body) {
            is IdlTypeBody.Struct -> enqueue(body.fields)
            is IdlTypeBody.Enum -> body.variants.forEach { enqueue(it.fields) }
            is IdlTypeBody.Alias -> pending.add(body.type)
        }

        program.instructions.forEach { ix ->
            ix.args.forEach { pending.add(it.type) }
            ix.returns?.let(pending::add)
        }
        program.constants.forEach { pending.add(it.type) }
        program.types.filter { it.name !in eventTypes || it.name in accountTypes }.forEach(::enqueue)
        while (pending.isNotEmpty()) {
            when (val type = pending.removeFirst()) {
                is IdlType.Defined -> if (used.add(type.name) && type.name in eventTypes) enqueue(typeDefs.getValue(type.name))
                is IdlType.Option -> pending.add(type.inner)
                is IdlType.Vec -> pending.add(type.element)
                is IdlType.Array -> pending.add(type.element)
                is IdlType.Primitive, is IdlType.Generic -> Unit
            }
        }
        used.intersect(eventTypes)
    }

    // --- types ---

    /** [type] with aliases replaced by their targets, at this level only. */
    private fun resolve(type: IdlType): IdlType {
        var current = type
        while (current is IdlType.Defined) {
            val body = typeDefs.getValue(current.name).body as? IdlTypeBody.Alias ?: break
            current = body.type
        }
        return current
    }

    private fun classOf(name: String): ClassName = programClass.nestedClass(kotlinTypeNames.getValue(name))

    private fun isByteArray(type: IdlType.Array) = resolve(type.element) == IdlType.Primitive.U8

    private fun arrayLength(type: IdlType.Array): Int = (type.length as IdlArrayLength.Fixed).size

    /** The encoded size of [type] when every value has the same size, or null. */
    private fun fixedSize(type: IdlType): Int? = when (val resolved = resolve(type)) {
        is IdlType.Primitive -> PRIMITIVE_SIZES[resolved]
        is IdlType.Option, is IdlType.Vec, is IdlType.Generic -> null
        is IdlType.Array -> fixedSize(resolved.element)?.times(arrayLength(resolved))
        is IdlType.Defined -> when (val body = typeDefs.getValue(resolved.name).body) {
            is IdlTypeBody.Struct -> if (isZeroCopy(resolved.name)) zeroCopyLayout(resolved.name).size else fixedSize(body.fields)
            // an enum's size depends on its variant, unless no variant carries data
            is IdlTypeBody.Enum -> if (body.variants.all { it.fields.isEmpty() }) 1 else null
            is IdlTypeBody.Alias -> fixedSize(body.type)
        }
    }

    private fun isZeroCopy(name: String): Boolean = typeDefs.getValue(name).serialization.let {
        it == IdlSerialization.Bytemuck || it == IdlSerialization.BytemuckUnsafe
    }

    /**
     * The C layout of zero-copy struct [name] on Solana's on-chain target: the padding before each field, in field
     * order, and after the last one. A packed or transparent layout has none.
     */
    private class ZeroCopyLayout(val padding: List<Int>, val trailing: Int, val offsets: List<Int>, val size: Int, val align: Int)

    private fun zeroCopyLayout(name: String): ZeroCopyLayout = zeroCopyLayouts[name] ?: run {
        val def = typeDefs.getValue(name)
        val repr = def.repr
        val packed = repr !is IdlRepr.C || repr.packed
        val types = when (val fields = (def.body as IdlTypeBody.Struct).fields) {
            IdlFields.None -> emptyList()
            is IdlFields.Named -> fields.fields.map { it.type }
            is IdlFields.Tuple -> fields.types
        }
        val padding = ArrayList<Int>(types.size)
        val offsets = ArrayList<Int>(types.size)
        var offset = 0
        var align = 1
        for (type in types) {
            val (size, fieldAlign) = zeroCopySizeAlign(type)
            val effective = if (packed) 1 else fieldAlign
            val pad = (effective - offset % effective) % effective
            padding.add(pad)
            offset += pad
            offsets.add(offset)
            offset += size
            align = maxOf(align, if (repr == IdlRepr.Transparent) fieldAlign else effective)
        }
        if (repr is IdlRepr.C) align = maxOf(align, repr.align ?: 1)
        val trailing = if (packed) 0 else (align - offset % align) % align
        ZeroCopyLayout(padding, trailing, offsets, offset + trailing, align).also { zeroCopyLayouts[name] = it }
    }

    /** The size and alignment of a zero-copy field, where 128-bit integers are 8-byte aligned, as on-chain. */
    private fun zeroCopySizeAlign(type: IdlType): Pair<Int, Int> = when (val resolved = resolve(type)) {
        IdlType.Primitive.PUBKEY -> 32 to 1
        IdlType.Primitive.U128, IdlType.Primitive.I128 -> 16 to 8
        // only reachable in packed layouts, which ignore alignment
        IdlType.Primitive.U256, IdlType.Primitive.I256 -> 32 to 1
        is IdlType.Primitive -> PRIMITIVE_SIZES.getValue(resolved).let { it to it }
        is IdlType.Array -> zeroCopySizeAlign(resolved.element).let { (size, align) -> size * arrayLength(resolved) to align }
        is IdlType.Defined -> if (isByteEnum(resolved.name)) 1 to 1 else zeroCopyLayout(resolved.name).let { it.size to it.align }
        is IdlType.Option, is IdlType.Vec, is IdlType.Generic -> throw IllegalStateException("unchecked zero-copy field $resolved")
    }

    /** Whether zero-copy struct [name] has padding, itself or in a zero-copy struct it contains. */
    private fun hasPadding(name: String): Boolean {
        val layout = zeroCopyLayout(name)
        if (layout.trailing > 0 || layout.padding.any { it > 0 }) return true
        val fields = (typeDefs.getValue(name).body as IdlTypeBody.Struct).fields
        val types = when (fields) {
            IdlFields.None -> emptyList()
            is IdlFields.Named -> fields.fields.map { it.type }
            is IdlFields.Tuple -> fields.types
        }
        fun contains(type: IdlType): Boolean = when (val resolved = resolve(type)) {
            is IdlType.Array -> contains(resolved.element)
            is IdlType.Defined -> !isByteEnum(resolved.name) && hasPadding(resolved.name)
            else -> false
        }
        return types.any(::contains)
    }

    private fun fixedSize(fields: IdlFields): Int? = when (fields) {
        IdlFields.None -> 0
        is IdlFields.Named -> fields.fields.fold(0 as Int?) { total, field -> total?.let { t -> fixedSize(field.type)?.plus(t) } }
        is IdlFields.Tuple -> fields.types.fold(0 as Int?) { total, type -> total?.let { t -> fixedSize(type)?.plus(t) } }
    }

    private fun IdlFields.isEmpty() = when (this) {
        IdlFields.None -> true
        is IdlFields.Named -> fields.isEmpty()
        is IdlFields.Tuple -> types.isEmpty()
    }

    private fun typeName(type: IdlType): TypeName = when (val resolved = resolve(type)) {
        is IdlType.Primitive -> PRIMITIVE_TYPES.getValue(resolved)
        is IdlType.Option -> {
            val inner = typeName(resolved.inner)
            if (inner.isNullable) Runtime.SOLANA_OPTION.parameterizedBy(inner) else inner.copy(nullable = true)
        }
        is IdlType.Vec -> LIST.parameterizedBy(typeName(resolved.element))
        is IdlType.Array -> if (isByteArray(resolved)) Runtime.SOLANA_BYTES else LIST.parameterizedBy(typeName(resolved.element))
        is IdlType.Defined -> classOf(resolved.name)
        is IdlType.Generic -> throw IllegalStateException("unsupported generic reached generation")
    }

    /** An expression reading [type], with a `BorshReader` receiver. */
    private fun read(type: IdlType): CodeBlock = when (val resolved = resolve(type)) {
        is IdlType.Primitive -> CodeBlock.of("%L()", "read" + PRIMITIVE_CODEC.getValue(resolved))
        is IdlType.Option -> if (typeName(resolved.inner).isNullable) {
            CodeBlock.of("readExplicitOption { %L }", read(resolved.inner))
        } else {
            CodeBlock.of("readOption { %L }", read(resolved.inner))
        }
        is IdlType.Vec -> CodeBlock.of("readVec { %L }", read(resolved.element))
        is IdlType.Array -> if (isByteArray(resolved)) {
            CodeBlock.of("readFixedBytes(%L)", arrayLength(resolved))
        } else {
            CodeBlock.of("readArray(%L) { %L }", arrayLength(resolved), read(resolved.element))
        }
        is IdlType.Defined -> CodeBlock.of("%T.read(this)", classOf(resolved.name))
        is IdlType.Generic -> throw IllegalStateException("unsupported generic reached generation")
    }

    /** A statement writing [value] of [type], with a `BorshWriter` receiver. */
    private fun write(type: IdlType, value: CodeBlock): CodeBlock = when (val resolved = resolve(type)) {
        is IdlType.Primitive -> CodeBlock.of("%L(%L)", "write" + PRIMITIVE_CODEC.getValue(resolved), value)
        is IdlType.Option -> if (typeName(resolved.inner).isNullable) {
            CodeBlock.of("writeExplicitOption(%L) { %L }", value, write(resolved.inner, CodeBlock.of("it")))
        } else {
            CodeBlock.of("writeOption(%L) { %L }", value, write(resolved.inner, CodeBlock.of("it")))
        }
        is IdlType.Vec -> CodeBlock.of("writeVec(%L) { %L }", value, write(resolved.element, CodeBlock.of("it")))
        is IdlType.Array -> if (isByteArray(resolved)) {
            CodeBlock.of("writeFixedBytes(%L, %L)", value, arrayLength(resolved))
        } else {
            CodeBlock.of("writeArray(%L, %L) { %L }", value, arrayLength(resolved), write(resolved.element, CodeBlock.of("it")))
        }
        is IdlType.Defined -> CodeBlock.of("%T.write(this, %L)", classOf(resolved.name), value)
        is IdlType.Generic -> throw IllegalStateException("unsupported generic reached generation")
    }

    /** A codec value for [type]: the class companion for a defined type, otherwise built inline. */
    private fun codec(type: IdlType): CodeBlock {
        val resolved = resolve(type)
        if (resolved is IdlType.Defined) return CodeBlock.of("%T", classOf(resolved.name))
        return CodeBlock.of("%T.of({ %L }, { %L })", Runtime.SOLANA_CODEC, read(resolved), write(resolved, CodeBlock.of("it")))
    }

    private class Field(val name: String, val type: IdlType, val docs: List<String>)

    /**
     * The fields of [fields] with Kotlin names. Fields whose names coincide keep their order: the last keeps the name and
     * the earlier ones are numbered from 1, skipping numbers another field uses, as legacy IDLs name both `_padding` and
     * `padding` as `padding`. Names in [reserved] are suffixed with `Field`.
     */
    private fun fields(fields: IdlFields, reserved: Set<String> = emptySet()): List<Field> = when (fields) {
        IdlFields.None -> emptyList()
        is IdlFields.Named -> {
            val names = fields.fields.map { field -> field.name.toCamelCase().let { if (it in reserved) "${it}Field" else it } }
            val taken = names.toHashSet()
            val renamed = names.toMutableList()
            for ((name, indices) in names.indices.groupBy { names[it] }) {
                if (indices.size == 1) continue
                var number = 1
                for (index in indices.dropLast(1)) {
                    while ("$name$number" in taken) number++
                    renamed[index] = "$name$number"
                    taken.add(renamed[index])
                }
            }
            fields.fields.mapIndexed { i, field ->
                val docs = if (renamed[i] == names[i]) {
                    field.docs
                } else {
                    field.docs + "Named `${field.name}` in the IDL, like a later field, which keeps the name."
                }
                Field(renamed[i], field.type, docs)
            }
        }
        is IdlFields.Tuple -> fields.types.mapIndexed { i, type -> Field("field$i", type, emptyList()) }
    }

    /** A data class with [fields] as properties, or a data object when there are none. */
    private fun valueClass(name: ClassName, fields: List<Field>, extraProperties: List<PropertySpec> = emptyList()): TypeSpec.Builder {
        if (fields.isEmpty() && extraProperties.isEmpty()) return TypeSpec.objectBuilder(name).addModifiers(KModifier.DATA)
        val constructor = FunSpec.constructorBuilder()
        val builder = TypeSpec.classBuilder(name).addModifiers(KModifier.DATA)
        for (field in fields) {
            val type = typeName(field.type)
            constructor.addParameter(field.name, type)
            builder.addProperty(
                PropertySpec.builder(field.name, type).initializer(field.name).apply { field.docs.forEach { addKdoc("%L\n", it) } }.build(),
            )
        }
        for (property in extraProperties) {
            constructor.addParameter(property.name, property.type)
            builder.addProperty(property.toBuilder().initializer(property.name).build())
        }
        return builder.primaryConstructor(constructor.build())
    }

    /** `Name(a = readX(), ...)` or `Name` for a value without fields, skipping the padding of a zero-copy [layout]. */
    private fun construct(name: ClassName, fields: List<Field>, extra: List<CodeBlock> = emptyList(), layout: ZeroCopyLayout? = null): CodeBlock {
        val trailing = layout?.trailing ?: 0
        if (fields.isEmpty() && extra.isEmpty()) {
            return if (trailing > 0) CodeBlock.of("%T.also { skipPadding(%L) }", name, trailing) else CodeBlock.of("%T", name)
        }
        val arguments = fields.mapIndexed { i, field ->
            val padding = layout?.padding?.get(i) ?: 0
            if (padding > 0) {
                CodeBlock.of("%N = run {\n⇥skipPadding(%L)\n%L\n⇤}", field.name, padding, read(field.type))
            } else {
                CodeBlock.of("%N = %L", field.name, read(field.type))
            }
        } + extra
        val value = CodeBlock.of("%T(\n⇥%L,\n⇤)", name, arguments.joinToCode(",\n"))
        return if (trailing > 0) CodeBlock.of("%L.also { skipPadding(%L) }", value, trailing) else value
    }

    private fun writes(fields: List<Field>, owner: String, layout: ZeroCopyLayout? = null): CodeBlock {
        val statements = fields.flatMapIndexed { i, field ->
            val padding = layout?.padding?.get(i) ?: 0
            listOfNotNull(
                if (padding > 0) CodeBlock.of("writePadding(%L)", padding) else null,
                write(field.type, CodeBlock.of("%L.%N", owner, field.name)),
            )
        }
        val trailing = layout?.trailing ?: 0
        return (if (trailing > 0) statements + CodeBlock.of("writePadding(%L)", trailing) else statements).joinToCode("\n")
    }

    private fun readFunction(returns: TypeName, body: CodeBlock) = FunSpec.builder("read")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("reader", Runtime.BORSH_READER)
        .returns(returns)
        .addCode("return with(reader) {\n⇥%L\n⇤}\n", body)
        .build()

    private fun writeFunction(type: TypeName, body: CodeBlock) = FunSpec.builder("write")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("writer", Runtime.BORSH_WRITER)
        .addParameter("value", type)
        .apply { if (body.isNotEmpty()) addCode("with(writer) {\n⇥%L\n⇤}\n", body) }
        .build()

    private fun typeDefinition(def: IdlTypeDef): TypeSpec {
        val name = classOf(def.name)
        val builder = when (val body = def.body) {
            is IdlTypeBody.Struct -> struct(name, fields(body.fields), if (isZeroCopy(def.name)) zeroCopyLayout(def.name) else null)
            is IdlTypeBody.Enum -> enum(name, def, body)
            is IdlTypeBody.Alias -> throw IllegalStateException("aliases are resolved at use")
        }
        def.docs.forEach { builder.addKdoc("%L\n", it) }
        program.accounts.firstOrNull { it.name == def.name }?.let { account ->
            builder.addSuperinterface(accountMarker)
            addToCodecScope(builder, accountFactory(name, account.name, account.discriminator))
            val fields = (def.body as? IdlTypeBody.Struct)?.let { fields(it.fields) }.orEmpty()
            val offsets = fieldOffsets(fields, if (isZeroCopy(def.name)) zeroCopyLayout(def.name) else null)
            builder.addType(accountFilter(name, fields, offsets))
            accountAccessors(name).forEach { addToCodecScope(builder, it) }
            val sliceable = sliceableFields(def)
            if (sliceable.isNotEmpty()) {
                builder.addType(accountFields(name, sliceable))
                accountSelect(name).forEach { addToCodecScope(builder, it) }
            }
        }
        return builder.build()
    }

    /** A struct and its codec, which skips and writes the padding of a zero-copy [layout]. */
    private fun struct(name: ClassName, fields: List<Field>, layout: ZeroCopyLayout? = null): TypeSpec.Builder {
        val builder = valueClass(name, fields)
        val codec = listOf(readFunction(name, construct(name, fields, layout = layout)), writeFunction(name, writes(fields, "value", layout)))
        return if (fields.isEmpty()) {
            builder.addSuperinterface(Runtime.SOLANA_CODEC.parameterizedBy(name)).addFunctions(codec)
        } else {
            builder.addType(TypeSpec.companionObjectBuilder().addSuperinterface(Runtime.SOLANA_CODEC.parameterizedBy(name)).addFunctions(codec).build())
        }
    }

    private fun enum(name: ClassName, def: IdlTypeDef, body: IdlTypeBody.Enum): TypeSpec.Builder {
        val builder = TypeSpec.classBuilder(name).addModifiers(KModifier.SEALED)
        val reads = CodeBlock.builder().add("when (val variant = readU8()) {\n⇥")
        val writes = CodeBlock.builder().add("when (value) {\n⇥")
        body.variants.forEachIndexed { index, variant ->
            val variantClass = name.nestedClass(variant.name.toPascalCase())
            val fields = fields(variant.fields)
            builder.addType(valueClass(variantClass, fields).superclass(name).build())
            reads.add("%L -> %L\n", index, construct(variantClass, fields))
            if (fields.isEmpty()) {
                writes.add("%T -> writeU8(%L)\n", variantClass, index)
            } else {
                writes.add("is %T -> {\n⇥writeU8(%L)\n%L\n⇤}\n", variantClass, index, writes(fields, "value"))
            }
        }
        reads.add("else -> invalidVariant(%S, variant)\n⇤}", def.name)
        writes.add("⇤}")
        return builder.addType(
            TypeSpec.companionObjectBuilder()
                .addSuperinterface(Runtime.SOLANA_CODEC.parameterizedBy(name))
                .addFunction(readFunction(name, reads.build()))
                .addFunction(writeFunction(name, writes.build()))
                .build(),
        )
    }

    /** Add [member] to the object that holds [owner]'s codec: its companion, or itself when it is an object. */
    private fun addToCodecScope(owner: TypeSpec.Builder, member: Any) {
        val companion = owner.typeSpecs.indexOfFirst { it.isCompanion }
        fun TypeSpec.Builder.add() = when (member) {
            is PropertySpec -> addProperty(member)
            is FunSpec -> addFunction(member)
            else -> throw IllegalArgumentException("Unsupported member $member")
        }
        if (companion == -1) {
            owner.add()
        } else {
            owner.typeSpecs[companion] = owner.typeSpecs[companion].toBuilder().add().build()
        }
    }

    /**
     * Each field's offset into the payload, while every field before it has a fixed size, then null; a zero-copy
     * [layout] knows every offset.
     */
    private fun fieldOffsets(fields: List<Field>, layout: ZeroCopyLayout?): List<Int?> {
        var offset: Int? = 0
        return fields.mapIndexed { i, field ->
            val at = layout?.offsets?.get(i) ?: offset
            offset = at?.let { start -> fixedSize(field.type)?.plus(start) }
            at
        }
    }

    /** A field of an account at a fixed [offset] into its payload, with a fixed [size]. */
    private class SliceableField(val field: Field, val offset: Int, val size: Int)

    /** The fields of account type [def] at a fixed offset with a fixed size, which a slice can select. */
    private fun sliceableFields(def: IdlTypeDef): List<SliceableField> {
        val fields = (def.body as? IdlTypeBody.Struct)?.let { fields(it.fields) }.orEmpty()
        val offsets = fieldOffsets(fields, if (isZeroCopy(def.name)) zeroCopyLayout(def.name) else null)
        val sliceable = ArrayList<SliceableField>()
        for ((i, field) in fields.withIndex()) {
            val offset = offsets[i] ?: break
            val size = fixedSize(field.type) ?: continue
            sliceable.add(SliceableField(field, offset, size))
        }
        return sliceable
    }

    /** The account's field descriptors, one for each of its [sliceable] fields. */
    private fun accountFields(account: ClassName, sliceable: List<SliceableField>): TypeSpec {
        val builder = TypeSpec.objectBuilder(account.nestedClass("Fields"))
            .superclass(Runtime.ACCOUNT_PROJECTION_SCOPE.parameterizedBy(account))
            .addKdoc("Fields of %L accounts at a fixed offset and size, to read a slice of the account data with [select].\n", account.simpleName)
        for (it in sliceable) {
            builder.addProperty(
                PropertySpec.builder(it.field.name, Runtime.ACCOUNT_FIELD.parameterizedBy(account, typeName(it.field.type)))
                    .addAnnotation(Runtime.JVM_FIELD)
                    .initializer("%T(%S, %L, %L) { with(it) { %L } }", Runtime.ACCOUNT_FIELD, it.field.name, it.offset, it.size, read(it.field.type))
                    .build(),
            )
        }
        return builder.build()
    }

    /** `select`, reading some of an account type's fields as a slice of the account data, by descriptor or within [Fields]. */
    private fun accountSelect(account: ClassName): List<FunSpec> {
        val field = Runtime.ACCOUNT_FIELD.parameterizedBy(account, STAR)
        val result = TypeVariableName("R")
        val selection = Runtime.ACCOUNT_SELECTION.parameterizedBy(account, Runtime.ACCOUNT_SLICE.parameterizedBy(account))
        val fieldsClass = account.nestedClass("Fields")
        return listOf(
            FunSpec.builder("select")
                .addKdoc("Select [fields] of the %L accounts the program owns, read as one slice of their data.\n", account.simpleName)
                .addAnnotation(Runtime.JVM_STATIC)
                .addParameter("fields", field, KModifier.VARARG)
                .returns(selection)
                .addStatement("return %T(ACCOUNT, PROGRAM_ID, %T.fields(*fields))", Runtime.ACCOUNT_SELECTION, fieldsClass)
                .build(),
            FunSpec.builder("select")
                .addKdoc(
                    "Select a slice or a mapped result of the %L accounts the program owns using [%T.fields] or [%T.combine].\n",
                    account.simpleName,
                    fieldsClass,
                    fieldsClass,
                )
                .addModifiers(KModifier.INLINE)
                .addTypeVariable(result)
                .addParameter("fields", LambdaTypeName.get(receiver = fieldsClass, returnType = Runtime.ACCOUNT_PROJECTION.parameterizedBy(account, result)))
                .returns(Runtime.ACCOUNT_SELECTION.parameterizedBy(account, result))
                .addStatement("return %T(ACCOUNT, PROGRAM_ID, %T.fields())", Runtime.ACCOUNT_SELECTION, fieldsClass)
                .build(),
        )
    }

    /**
     * An extension property on slices of each account type for each of its sliceable fields, reading the field from the
     * slice. A field named like a member of the slice takes a `Field` suffix, and each property its own JVM name, since
     * every account's slices share one JVM type.
     */
    private fun sliceProperties(): List<PropertySpec> = program.accounts.flatMap { account ->
        val def = typeDefs.getValue(account.name)
        val accountClass = classOf(def.name)
        sliceableFields(def).map {
            val name = if (it.field.name in SLICE_MEMBERS) "${it.field.name}Field" else it.field.name
            PropertySpec.builder(name, typeName(it.field.type))
                .receiver(Runtime.ACCOUNT_SLICE.parameterizedBy(accountClass))
                .addKdoc("The [%T.%N] of this slice, which must have selected it.\n", accountClass, it.field.name)
                .addAnnotation(
                    AnnotationSpec.builder(Runtime.JVM_NAME)
                        .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
                        .addMember("%S", "get${accountClass.simpleName}${it.field.name.replaceFirstChar(Char::uppercaseChar)}")
                        .build(),
                )
                .getter(FunSpec.getterBuilder().addStatement("return this[%T.%N]", accountClass.nestedClass("Fields"), it.field.name).build())
                .build()
        }
    }

    /**
     * The account's filter class: a method per field at a known offset, where every field before it has a
     * fixed size. There is no data size method, since the IDL does not say how much space an account is
     * allocated.
     */
    private fun accountFilter(account: ClassName, fields: List<Field>, offsets: List<Int?>): TypeSpec {
        val filterClass = account.nestedClass("Filter")
        val builder = TypeSpec.classBuilder(filterClass)
            .addKdoc("Filter over the %L accounts the program owns.\n", account.simpleName)
            .superclass(Runtime.ACCOUNT_FILTER_BASE.parameterizedBy(account, filterClass))
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("api", Runtime.SOLANA_API).build())
            .addSuperclassConstructorParameter("api, ACCOUNT, PROGRAM_ID")
            .addProperty(
                PropertySpec.builder("self", filterClass, KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder().addStatement("return this").build())
                    .build(),
            )
        for ((i, field) in fields.withIndex()) {
            val at = offsets[i] ?: break
            val method = if (field.name in FILTER_BASE_MEMBERS) "${field.name}Field" else field.name
            builder.addFunction(
                FunSpec.builder(method)
                    .addKdoc("Select accounts whose [%T.%N] equals [value], at payload offset %L.\n", account, field.name, at)
                    .addParameter("value", typeName(field.type))
                    .returns(filterClass)
                    .addStatement("return field(%L) { %L }", at, write(field.type, CodeBlock.of("value")))
                    .build(),
            )
        }
        return builder.build()
    }

    /** `filter`, `fetch` and `watch` for an account type, bound to the program address by default. */
    private fun accountAccessors(account: ClassName): List<FunSpec> {
        val programId = ParameterSpec.builder("programId", Runtime.SOLANA_ADDRESS).defaultValue("PROGRAM_ID").build()
        val commitment = ParameterSpec.builder("commitment", Runtime.COMMITMENT).defaultValue("api.defaultCommitment").build()
        val decodedAccount = Runtime.SOLANA_ACCOUNT.parameterizedBy(account)
        val accountResult = Runtime.RESULT.parameterizedBy(decodedAccount, Runtime.SOLANA_ACCOUNT_ERROR)
        return listOf(
            FunSpec.builder("filter")
                .addKdoc("Filter over the %L accounts the program owns.\n", account.simpleName)
                .addAnnotation(Runtime.JVM_STATIC)
                .addParameter("api", Runtime.SOLANA_API)
                .returns(account.nestedClass("Filter"))
                .addStatement("return Filter(api)")
                .build(),
            FunSpec.builder("fetch")
                .addKdoc("Fetch the %L account at [address], failing when missing or undecodable.\n", account.simpleName)
                .addAnnotation(Runtime.JVM_STATIC)
                .addAnnotation(Runtime.JVM_OVERLOADS)
                .addParameter("api", Runtime.SOLANA_API)
                .addParameter("address", Runtime.SOLANA_ADDRESS)
                .addParameter(programId)
                .addParameter(commitment)
                .returns(Runtime.RPC_REQUEST.parameterizedBy(decodedAccount, Runtime.SOLANA_ACCOUNT_ERROR))
                .addStatement("return ACCOUNT.%L(api, address, programId, commitment)", FETCH_ALIAS)
                .build(),
            FunSpec.builder("fetch")
                .addKdoc("Fetch the %L accounts at [addresses] in order, retaining missing and undecodable accounts as failed results.\n", account.simpleName)
                .addAnnotation(Runtime.JVM_STATIC)
                .addAnnotation(Runtime.JVM_OVERLOADS)
                .addParameter("api", Runtime.SOLANA_API)
                .addParameter("addresses", LIST.parameterizedBy(Runtime.SOLANA_ADDRESS))
                .addParameter(programId)
                .addParameter(commitment)
                .returns(Runtime.RPC_REQUEST.parameterizedBy(LIST.parameterizedBy(accountResult), Runtime.RPC_ERROR))
                .addStatement("return ACCOUNT.%L(api, addresses, programId, commitment)", FETCH_ALIAS)
                .build(),
            FunSpec.builder("watch")
                .addKdoc("Subscribe to the %L account at [address], emitting missing and undecodable accounts as failed results.\n", account.simpleName)
                .addAnnotation(Runtime.JVM_STATIC)
                .addAnnotation(Runtime.JVM_OVERLOADS)
                .addParameter("api", Runtime.SOLANA_API)
                .addParameter("address", Runtime.SOLANA_ADDRESS)
                .addParameter(programId)
                .addParameter(commitment)
                .returns(Runtime.RPC_SUBSCRIBE.parameterizedBy(accountResult, Runtime.RPC_ERROR))
                .addStatement("return ACCOUNT.%L(api, address, programId, commitment)", WATCH_ALIAS)
                .build(),
        )
    }

    // --- accounts ---

    private fun discriminator(discriminator: IdlDiscriminator) = CodeBlock.of("%T.of(%L)", Runtime.DISCRIMINATOR, discriminator.bytes.joinToString(", "))

    private fun accountFactory(type: ClassName, name: String, discriminator: IdlDiscriminator) = PropertySpec.builder("ACCOUNT", Runtime.ACCOUNT_FACTORY.parameterizedBy(type))
        .addAnnotation(Runtime.JVM_FIELD)
        .initializer("%T(%S, %L, this)", Runtime.ACCOUNT_FACTORY, name, discriminator(discriminator))
        .build()

    private fun addAccounts(programType: TypeSpec.Builder) {
        programType.addType(
            TypeSpec.interfaceBuilder(accountMarker).addModifiers(KModifier.SEALED)
                .addKdoc("An account owned by the %L program.\n", program.name)
                .build(),
        )
        val factories = program.accounts.map { CodeBlock.of("%T.ACCOUNT", classOf(it.name)) }
        programType.addProperty(
            PropertySpec.builder("ACCOUNTS", LIST.parameterizedBy(Runtime.ACCOUNT_FACTORY.parameterizedBy(WildcardTypeName.producerOf(accountMarker))))
                .addAnnotation(Runtime.JVM_FIELD)
                .initializer("listOf(%L)", factories.joinToCode(", "))
                .build(),
        )
        programType.addFunction(
            FunSpec.builder("decodeAccount")
                .addKdoc("Decode account [data] as whichever account type its discriminator identifies.\n")
                .addAnnotation(Runtime.JVM_STATIC)
                .addParameter("data", ByteArray::class)
                .returns(Runtime.RESULT.parameterizedBy(accountMarker, Runtime.DECODE_ERROR))
                .addCode(
                    """
                    for (factory in ACCOUNTS) if (factory.matches(data)) return factory.decode(data)
                    return %M(%T(%T.fromBytes(data), %S))
                    """.trimIndent() + "\n",
                    Runtime.FAILURE,
                    Runtime.UNRECOGNIZED,
                    Runtime.SOLANA_BYTES,
                    "Unknown ${program.name} account",
                )
                .build(),
        )
    }

    // --- events ---

    private fun addEvents(programType: TypeSpec.Builder) {
        val events = TypeSpec.interfaceBuilder(eventInterface)
            .addModifiers(KModifier.SEALED)
            .addSuperinterface(Runtime.EVENT)
            .addKdoc("An event emitted by the %L program.\n", program.name)
        program.events.forEach { events.addType(event(it)) }
        programType.addType(events.build())

        programType.addProperty(
            PropertySpec.builder("EVENTS", LIST.parameterizedBy(Runtime.EVENT_FACTORY.parameterizedBy(WildcardTypeName.producerOf(eventInterface))))
                .addAnnotation(Runtime.JVM_FIELD)
                .initializer("listOf(%L)", program.events.map { CodeBlock.of("%T", eventClass(it)) }.joinToCode(", "))
                .build(),
        )
        programType.addFunction(
            FunSpec.builder("decodeEvent")
                .addKdoc("Decode event [data], discriminator included, as whichever event its discriminator identifies.\n")
                .addAnnotation(Runtime.JVM_STATIC)
                .addParameter("data", ByteArray::class)
                .addParameter("context", Runtime.EVENT_CONTEXT)
                .returns(Runtime.RESULT.parameterizedBy(eventInterface, Runtime.DECODE_ERROR))
                .addCode(
                    """
                    for (factory in EVENTS) if (factory.matches(data)) return factory.decode(data, context)
                    return %M(%T(%T.fromBytes(data), %S))
                    """.trimIndent() + "\n",
                    Runtime.FAILURE,
                    Runtime.UNRECOGNIZED,
                    Runtime.SOLANA_BYTES,
                    "Unknown ${program.name} event",
                )
                .build(),
        )
    }

    private fun eventClass(event: IdlEvent) = eventInterface.nestedClass(event.name.substringAfterLast("::").toPascalCase())

    private fun event(event: IdlEvent): TypeSpec {
        val name = eventClass(event)
        val def = typeDefs.getValue(event.name)
        val body = def.body as? IdlTypeBody.Struct ?: throw IllegalArgumentException("event '${event.name}': only struct events are supported")
        val fields = fields(body.fields, reserved = setOf("context"))
        val context = PropertySpec.builder("context", Runtime.EVENT_CONTEXT, KModifier.OVERRIDE).build()
        val companion = TypeSpec.companionObjectBuilder()
            .addSuperinterface(Runtime.EVENT_FACTORY.parameterizedBy(name))
            .addProperty(PropertySpec.builder("name", STRING, KModifier.OVERRIDE).initializer("%S", event.name).build())
            .addProperty(PropertySpec.builder("discriminator", Runtime.DISCRIMINATOR, KModifier.OVERRIDE).initializer(discriminator(event.discriminator)).build())
            .addFunction(
                FunSpec.builder("read")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("reader", Runtime.BORSH_READER)
                    .addParameter("context", Runtime.EVENT_CONTEXT)
                    .returns(name)
                    .addCode("return with(reader) {\n⇥%L\n⇤}\n", construct(name, fields, listOf(CodeBlock.of("context = context"))))
                    .build(),
            )
            .addFunction(
                FunSpec.builder("write")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("writer", Runtime.BORSH_WRITER)
                    .addParameter("event", name)
                    .apply { if (fields.isNotEmpty()) addCode("with(writer) {\n⇥%L\n⇤}\n", writes(fields, "event")) }
                    .build(),
            )
            .addFunction(
                FunSpec.builder("filter")
                    .addKdoc("Filter over the %L events the program emits to its logs.\n", event.name)
                    .addAnnotation(Runtime.JVM_STATIC)
                    .addParameter("api", Runtime.SOLANA_API)
                    .returns(Runtime.EVENT_FILTER.parameterizedBy(name))
                    .addStatement("return %T(api, this, PROGRAM_ID)", Runtime.EVENT_FILTER)
                    .build(),
            )
        return valueClass(name, fields, listOf(context))
            .addSuperinterface(eventInterface)
            .apply { def.docs.forEach { addKdoc("%L\n", it) } }
            .addType(companion.build())
            .build()
    }

    // --- errors ---

    private fun addErrors(programType: TypeSpec.Builder) {
        val errors = TypeSpec.classBuilder(errorClass)
            .addModifiers(KModifier.SEALED)
            .addKdoc("A custom error reported by the %L program.\n", program.name)
            .addSuperinterface(Runtime.PROGRAM_ERROR)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("code", LONG)
                    .addParameter("name", STRING)
                    .addParameter("message", STRING.copy(nullable = true))
                    .build(),
            )
            .addProperty(PropertySpec.builder("code", LONG, KModifier.OVERRIDE).initializer("code").build())
            .addProperty(PropertySpec.builder("name", STRING, KModifier.OVERRIDE).initializer("name").build())
            .addProperty(PropertySpec.builder("message", STRING.copy(nullable = true), KModifier.OVERRIDE).initializer("message").build())
        program.errors.forEach { errors.addType(error(it)) }
        programType.addType(errors.build())

        programType.addProperty(
            PropertySpec.builder("ERRORS", LIST.parameterizedBy(errorClass))
                .addAnnotation(Runtime.JVM_FIELD)
                .initializer("listOf(%L)", program.errors.map { CodeBlock.of("%T", errorClass.nestedClass(it.name.toPascalCase())) }.joinToCode(", "))
                .build(),
        )
        programType.addProperty(
            PropertySpec.builder("ERROR_DECODER", Runtime.PROGRAM_ERROR_DECODER, KModifier.PRIVATE)
                .initializer("%T.of(ERRORS)", Runtime.PROGRAM_ERROR_DECODER)
                .build(),
        )
        programType.addFunction(
            FunSpec.builder("decodeError")
                .addKdoc("The error this program declares for custom error [code], or null.\n")
                .addAnnotation(Runtime.JVM_STATIC)
                .addParameter("code", LONG)
                .returns(errorClass.copy(nullable = true))
                .addStatement("return ERROR_DECODER.decode(code) as %T?", errorClass)
                .build(),
        )
        programType.addFunction(
            FunSpec.builder("decodeError")
                .addKdoc(
                    "The error this program declares for [failure], or null when [failure] was not attributed to " +
                        "[programId] or the program declares no error with its code.\n",
                )
                .addAnnotation(Runtime.JVM_STATIC)
                .addAnnotation(Runtime.JVM_OVERLOADS)
                .addParameter("failure", Runtime.PROGRAM_FAILURE)
                .addParameter(ParameterSpec.builder("programId", Runtime.SOLANA_ADDRESS).defaultValue("PROGRAM_ID").build())
                .returns(errorClass.copy(nullable = true))
                .addStatement("return if (failure.programId == programId) decodeError(failure.code) else null")
                .build(),
        )
    }

    private fun error(error: IdlErrorCode): TypeSpec = TypeSpec.objectBuilder(errorClass.nestedClass(error.name.toPascalCase()))
        .addModifiers(KModifier.DATA)
        .superclass(errorClass)
        .addSuperclassConstructorParameter("%LL", error.code)
        .addSuperclassConstructorParameter("%S", error.name)
        .addSuperclassConstructorParameter(if (error.message == null) "null" else "%S", *listOfNotNull(error.message).toTypedArray())
        .build()

    // --- instructions ---

    private fun addInstructions(programType: TypeSpec.Builder) {
        val hierarchy = TypeSpec.interfaceBuilder(instructionHierarchy)
            .addModifiers(KModifier.SEALED)
            .addKdoc("An instruction of the %L program, decoded from its data and accounts.\n", program.name)
            .addProperty("remainingAccounts", LIST.parameterizedBy(Runtime.ACCOUNT_META))
        program.instructions.forEach { hierarchy.addType(addInstruction(programType, it)) }
        programType.addType(hierarchy.build())

        programType.addProperty(
            PropertySpec.builder("INSTRUCTIONS", LIST.parameterizedBy(Runtime.INSTRUCTION_DESCRIPTOR.parameterizedBy(STAR)))
                .addAnnotation(Runtime.JVM_FIELD)
                .initializer("listOf(%L)", program.instructions.map { CodeBlock.of("%N", it.name.toUpperSnakeCase()) }.joinToCode(", "))
                .build(),
        )
        val dispatch = CodeBlock.builder().add("return when {\n⇥")
        program.instructions.forEach { ix ->
            dispatch.add("%N.matches(instruction.data) -> %T.decode(instruction, checkSigners)\n", ix.name.toUpperSnakeCase(), instructionHierarchy.nestedClass(ix.name.toPascalCase()))
        }
        dispatch.add("else -> %M(%T(instruction.data, %S))\n⇤}\n", Runtime.FAILURE, Runtime.UNRECOGNIZED, "Unknown ${program.name} instruction")
        programType.addFunction(
            FunSpec.builder("decodeInstruction")
                .addKdoc(
                    "Decode [instruction] as whichever instruction its data identifies.\n\n" +
                        "Its program id is not checked, so an alternate deployment decodes as well. Programs can share " +
                        "discriminators, so filter by `instruction.programId` first where other programs' instructions " +
                        "may be present. Pass [checkSigners] false for an inner instruction, whose accounts carry the " +
                        "transaction's signer flags rather than the ones it was invoked with.\n",
                )
                .addAnnotation(Runtime.JVM_STATIC)
                .addAnnotation(Runtime.JVM_OVERLOADS)
                .addParameter("instruction", Runtime.INSTRUCTION)
                .addParameter(ParameterSpec.builder("checkSigners", BOOLEAN).defaultValue("true").build())
                .returns(Runtime.RESULT.parameterizedBy(instructionHierarchy, Runtime.DECODE_ERROR))
                .addCode(dispatch.build())
                .build(),
        )
    }

    /** Add [ix]'s arguments, accounts, descriptor and builder to [programType], and return its decoded form. */
    private fun addInstruction(programType: TypeSpec.Builder, ix: IdlInstruction): TypeSpec {
        val baseName = ix.name.toPascalCase()
        val descriptorName = ix.name.toUpperSnakeCase()
        val argsFields = fields(IdlFields.Named(ix.args))
        val argsClass = programClass.nestedClass("${baseName}Args")
        val argsType: TypeName = if (argsFields.isEmpty()) UNIT else argsClass
        val accountsClass = programClass.nestedClass("${baseName}Accounts")
        val hasAccounts = ix.accounts.isNotEmpty()
        val decodedClass = instructionHierarchy.nestedClass(baseName)

        if (argsFields.isNotEmpty()) {
            programType.addType(struct(argsClass, argsFields).addKdoc("Arguments of the %L instruction.\n", ix.name).build())
        }
        if (hasAccounts) {
            programType.addType(accounts(accountsClass, ix.accounts, ix).addKdoc("Accounts of the %L instruction.\n", ix.name).build())
        }

        // descriptor
        val argsCodec = if (argsFields.isEmpty()) CodeBlock.of("%T.UNIT", Runtime.SOLANA_CODEC) else CodeBlock.of("%T", argsClass)
        val descriptor = ix.returns?.let { returns ->
            PropertySpec.builder(descriptorName, Runtime.RETURNING_DESCRIPTOR.parameterizedBy(argsType, typeName(returns)))
                .initializer("%T(%S, %L, %L, %L)", Runtime.RETURNING_DESCRIPTOR, ix.name, discriminator(ix.discriminator), argsCodec, codec(returns))
        } ?: PropertySpec.builder(descriptorName, Runtime.INSTRUCTION_DESCRIPTOR.parameterizedBy(argsType))
            .initializer("%T(%S, %L, %L)", Runtime.INSTRUCTION_DESCRIPTOR, ix.name, discriminator(ix.discriminator), argsCodec)
        programType.addProperty(descriptor.addAnnotation(Runtime.JVM_FIELD).build())

        // builder
        val builder = FunSpec.builder(ix.name.toCamelCase())
            .addAnnotation(Runtime.JVM_STATIC)
            .addAnnotation(Runtime.JVM_OVERLOADS)
            .returns(Runtime.INSTRUCTION)
        ix.docs.forEach { builder.addKdoc("%L\n", it) }
        if (hasAccounts) builder.addParameter("accounts", accountsClass)
        if (argsFields.isNotEmpty()) builder.addParameter("args", argsClass)
        builder.addParameter(
            ParameterSpec.builder("remainingAccounts", LIST.parameterizedBy(Runtime.ACCOUNT_META)).defaultValue("emptyList()").build(),
        )
        builder.addParameter(ParameterSpec.builder("programId", Runtime.SOLANA_ADDRESS).defaultValue("PROGRAM_ID").build())
        builder.addStatement(
            "return %T(programId, %L, %N.encodeData(%L))",
            Runtime.INSTRUCTION,
            if (hasAccounts) CodeBlock.of("accounts.toAccountMetas(programId) + remainingAccounts") else CodeBlock.of("remainingAccounts"),
            descriptorName,
            if (argsFields.isNotEmpty()) "args" else "Unit",
        )
        programType.addFunction(builder.build())

        // decoded form
        val properties = buildList {
            if (hasAccounts) add(PropertySpec.builder("accounts", accountsClass).build())
            if (argsFields.isNotEmpty()) add(PropertySpec.builder("args", argsClass).build())
            add(PropertySpec.builder("remainingAccounts", LIST.parameterizedBy(Runtime.ACCOUNT_META), KModifier.OVERRIDE).build())
        }
        val construct = CodeBlock.of(
            "%T(%L)",
            decodedClass,
            buildList {
                if (hasAccounts) add(CodeBlock.of("%T.read(reader)", accountsClass))
                if (argsFields.isNotEmpty()) add(CodeBlock.of("args"))
                add(CodeBlock.of("reader.remaining()"))
            }.joinToCode(", "),
        )
        val decode = FunSpec.builder("decode")
            .addKdoc("Decode [instruction] as %L, without checking its program id.\n", ix.name)
            .addAnnotation(Runtime.JVM_STATIC)
            .addAnnotation(Runtime.JVM_OVERLOADS)
            .addParameter("instruction", Runtime.INSTRUCTION)
            .addParameter(ParameterSpec.builder("checkSigners", BOOLEAN).defaultValue("true").build())
            .returns(Runtime.RESULT.parameterizedBy(decodedClass, Runtime.DECODE_ERROR))
            .addStatement(
                "return %N.decodeData(instruction.data).andThen { %L -> %T.decode(instruction, checkSigners) { reader -> %L } }",
                descriptorName,
                if (argsFields.isNotEmpty()) "args" else "_",
                Runtime.ACCOUNTS_READER,
                construct,
            )
            .build()
        return valueClass(decodedClass, emptyList(), properties)
            .addKdoc("The %L instruction, decoded.\n", ix.name)
            .addSuperinterface(instructionHierarchy)
            .addType(TypeSpec.companionObjectBuilder().addFunction(decode).build())
            .build()
    }

    /**
     * An account roles class of [ix]: one property per account, nested classes for account groups, and a `find`
     * function for each program derived account.
     */
    private fun accounts(name: ClassName, items: List<IdlAccountItem>, ix: IdlInstruction): TypeSpec.Builder {
        val constructor = FunSpec.constructorBuilder()
        val finders = mutableListOf<FunSpec>()
        val builder = TypeSpec.classBuilder(name).addModifiers(KModifier.DATA)
        val metas = CodeBlock.builder().add("return buildList {\n⇥")
        val reads = mutableListOf<CodeBlock>()
        for (item in items) {
            val property = item.name.toCamelCase()
            val type: TypeName
            val parameter: ParameterSpec
            when (item) {
                is IdlAccountItem.Group -> {
                    val groupClass = name.nestedClass(item.name.toPascalCase())
                    builder.addType(accounts(groupClass, item.accounts, ix).build())
                    type = groupClass
                    parameter = ParameterSpec.builder(property, type).build()
                    metas.addStatement("addAll(%N.toAccountMetas(programId))", property)
                    reads.add(CodeBlock.of("%N = %T.read(reader)", property, groupClass))
                }
                is IdlAccountItem.Single -> {
                    type = Runtime.SOLANA_ADDRESS.copy(nullable = item.optional)
                    val finder = item.pda?.let { pdaFinder(ix, item, it, listOf(items, ix.accounts).distinct()) }
                    finder?.let(finders::add)
                    parameter = ParameterSpec.builder(property, type).apply {
                        item.docs.forEach { addKdoc("%L\n", it) }
                        finder?.let { addKdoc("A program derived address, see [%N].\n", it) }
                        when {
                            item.optional -> defaultValue("null")
                            item.address != null -> defaultValue("%T(%S)", Runtime.SOLANA_ADDRESS, item.address)
                        }
                    }.build()
                    if (item.optional) {
                        metas.addStatement(
                            "add(if (%N != null) %T(%N, %L, %L) else %T(programId))",
                            property,
                            Runtime.ACCOUNT_META,
                            property,
                            item.signer,
                            item.writable,
                            Runtime.ACCOUNT_META,
                        )
                    } else {
                        metas.addStatement("add(%T(%N, %L, %L))", Runtime.ACCOUNT_META, property, item.signer, item.writable)
                    }
                    reads.add(
                        CodeBlock.of(
                            "%N = reader.%L(%S, %L, %L)",
                            property,
                            if (item.optional) "optional" else "required",
                            item.name,
                            item.signer,
                            item.writable,
                        ),
                    )
                }
            }
            constructor.addParameter(parameter)
            builder.addProperty(PropertySpec.builder(property, type).initializer(property).build())
        }
        metas.add("⇤}\n")
        return builder
            .primaryConstructor(constructor.build())
            .addFunction(
                FunSpec.builder("toAccountMetas")
                    .addKdoc("These accounts in instruction order, with [programId] in place of each absent optional account.\n")
                    .addParameter("programId", Runtime.SOLANA_ADDRESS)
                    .returns(LIST.parameterizedBy(Runtime.ACCOUNT_META))
                    .addCode(metas.build())
                    .build(),
            )
            .addType(
                TypeSpec.companionObjectBuilder()
                    .addFunction(
                        FunSpec.builder("read")
                            .addKdoc("Read these accounts from [reader], checking the privileges each role requires.\n")
                            .addAnnotation(Runtime.JVM_STATIC)
                            .addParameter("reader", Runtime.ACCOUNTS_READER)
                            .returns(name)
                            .addStatement("return %T(\n⇥%L,\n⇤)", name, reads.joinToCode(",\n"))
                            .build(),
                    )
                    .addFunctions(finders)
                    .build(),
            )
    }

    // --- program derived addresses ---

    /** Call [action] with every PDA account of every instruction, and the account lists its seeds resolve in. */
    private fun forEachPda(action: (IdlInstruction, IdlAccountItem.Single, IdlPda, List<List<IdlAccountItem>>) -> Unit) {
        fun visit(ix: IdlInstruction, items: List<IdlAccountItem>, action: (IdlInstruction, IdlAccountItem.Single, IdlPda, List<List<IdlAccountItem>>) -> Unit) {
            for (item in items) {
                when (item) {
                    is IdlAccountItem.Group -> visit(ix, item.accounts, action)
                    is IdlAccountItem.Single -> item.pda?.let { action(ix, item, it, listOf(items, ix.accounts).distinct()) }
                }
            }
        }
        program.instructions.forEach { visit(it, it.accounts, action) }
    }

    /** Reject byte orders for seeds that are not integer PDA seeds, which would otherwise have no effect. */
    private fun checkSeedByteOrders() {
        val integerSeeds = HashSet<SeedByteOrders.Seed>()
        forEachPda { ix, item, pda, scopes ->
            for (seed in pda.seeds) {
                val type = seedType(ix, seed, scopes)?.let(::resolve)
                if (type is IdlType.Primitive && type in INTEGER_TYPES && PRIMITIVE_SIZES.getValue(type) > 1) {
                    integerSeeds.add(SeedByteOrders.Seed(item.name, (seed as? IdlSeed.Arg)?.path ?: (seed as IdlSeed.Account).path))
                }
            }
        }
        for (seed in seedByteOrders.seeds.keys) {
            require(seed in integerSeeds) { "seed byte order for account '${seed.account}' seed '${seed.path}': no PDA account has an integer seed at that path" }
        }
    }

    /**
     * The type of the value [seed] takes: an argument's field, or an account's data field. Null for an account's
     * address, a constant, or a field of an account type the program does not declare.
     */
    private fun seedType(ix: IdlInstruction, seed: IdlSeed, scopes: List<List<IdlAccountItem>>): IdlType? = when (seed) {
        is IdlSeed.Const -> null
        is IdlSeed.Arg -> {
            val path = seed.path.split('.')
            IdlPaths.field(ix.args.first { it.name == path[0] }.type, path.drop(1), typeDefs)
        }
        is IdlSeed.Account -> {
            val fields = IdlPaths.account(scopes, seed.path)!!.fields
            if (fields.isEmpty()) null else seed.account?.takeIf { it in typeDefs }?.let { IdlPaths.field(IdlType.Defined(it), fields, typeDefs) }
        }
    }

    /**
     * `find<Account>`: derives [item]'s address from [pda]'s seeds, taking a parameter for each seed that is not a
     * constant. A seed whose encoding the IDL does not determine takes its bytes.
     */
    private fun pdaFinder(ix: IdlInstruction, item: IdlAccountItem.Single, pda: IdlPda, scopes: List<List<IdlAccountItem>>): FunSpec {
        val where = "instruction '${ix.name}' account '${item.name}'"
        // parameter name to the seed it is for, so each seed path takes one parameter
        val parameters = LinkedHashMap<String, Pair<String, ParameterSpec>>()
        fun parameter(name: String, type: TypeName, source: String, doc: String? = null): String {
            parameters[name]?.let { (existing, spec) ->
                require(existing == source && spec.type == type) { "$where: PDA seeds $existing and $source both take a parameter named '$name'" }
                return name
            }
            require(name != "programId") { "$where: PDA seed $source takes a parameter named 'programId', which the program it derives under takes" }
            parameters[name] = source to ParameterSpec.builder(name, type).apply { doc?.let { addKdoc("%L\n", it) } }.build()
            return name
        }

        val byteOrders = LinkedHashMap<String, SeedByteOrder>()
        fun encoded(seed: IdlSeed, path: String): CodeBlock {
            val source = "${if (seed is IdlSeed.Arg) "argument" else "account"} '$path'"
            val name = path.replace('.', '_').toCamelCase()
            val type = seedType(ix, seed, scopes)?.let(::resolve)
            return when {
                type == IdlType.Primitive.STRING -> CodeBlock.of("%N.encodeToByteArray()", parameter(name, STRING, source))
                type == IdlType.Primitive.PUBKEY -> CodeBlock.of("%N.asByteArray()", parameter(name, Runtime.SOLANA_ADDRESS, source))
                type == IdlType.Primitive.BYTES || (type is IdlType.Array && isByteArray(type)) ->
                    CodeBlock.of("%N.asByteArray()", parameter(name, Runtime.SOLANA_BYTES, source))
                type is IdlType.Primitive && type in INTEGER_TYPES -> {
                    val size = PRIMITIVE_SIZES.getValue(type)
                    val bytes = CodeBlock.of("%T(%L).apply { write%L(%N) }.toByteArray()", Runtime.BORSH_WRITER, size, type.name, parameter(name, typeName(type), source))
                    val order = seedByteOrders.of(item.name, path)
                    if (size > 1) byteOrders[path] = order
                    if (size > 1 && order == SeedByteOrder.BIG_ENDIAN) CodeBlock.of("%L.also { it.reverse() }", bytes) else bytes
                }
                else -> CodeBlock.of(
                    "%N",
                    parameter("${name}Seed", BYTE_ARRAY, source, "The bytes of the `$path` seed, whose encoding the IDL does not determine."),
                )
            }
        }
        fun address(seed: IdlSeed): CodeBlock = when (seed) {
            is IdlSeed.Const -> CodeBlock.of("%N", programConstantNames.getValue(seed.bytes))
            is IdlSeed.Arg -> CodeBlock.of("%N", parameter(seed.path.replace('.', '_').toCamelCase(), Runtime.SOLANA_ADDRESS, "argument '${seed.path}'"))
            is IdlSeed.Account -> {
                val path = IdlPaths.account(scopes, seed.path)!!
                val name = (if (path.fields.isEmpty()) path.role else path.role + path.fields).joinToString("_").toCamelCase()
                CodeBlock.of("%N", parameter(name, Runtime.SOLANA_ADDRESS, "account '${seed.path}'"))
            }
        }

        val seeds = pda.seeds.map { seed ->
            when (seed) {
                is IdlSeed.Const -> CodeBlock.of("%N", seedConstantNames.getValue(seed.bytes))
                is IdlSeed.Arg -> encoded(seed, seed.path)
                is IdlSeed.Account -> {
                    val path = IdlPaths.account(scopes, seed.path)!!
                    if (path.fields.isEmpty()) {
                        CodeBlock.of("%N.asByteArray()", parameter(path.role.joinToString("_").toCamelCase(), Runtime.SOLANA_ADDRESS, "account '${seed.path}'"))
                    } else {
                        encoded(seed, seed.path)
                    }
                }
            }
        }
        val programId = pda.program?.let(::address)

        val finder = FunSpec.builder("find${item.name.toPascalCase()}")
            .addKdoc("Find the address of the %L account, a program derived address with seeds %L.\n", item.name, pda.seeds.joinToString(", ", transform = ::describe))
            .addAnnotation(Runtime.JVM_STATIC)
        byteOrders.entries.groupBy({ it.value }, { "`${it.key}`" }).forEach { (order, paths) ->
            val described = if (order == SeedByteOrder.LITTLE_ENDIAN) "little-endian" else "big-endian"
            val integers = if (paths.size == 1) "a $described integer" else "$described integers"
            finder.addKdoc("\nEncodes %L as %L. IDLs do not record the byte order a program uses.\n", paths.joinToString(", "), integers)
        }
        parameters.values.forEach { finder.addParameter(it.second) }
        if (programId == null) {
            finder.addAnnotation(Runtime.JVM_OVERLOADS)
            finder.addParameter(ParameterSpec.builder("programId", Runtime.SOLANA_ADDRESS).defaultValue("PROGRAM_ID").build())
        }
        return finder
            .returns(Runtime.PROGRAM_DERIVED_ADDRESS)
            .addStatement("return %T.findProgramAddress(listOf(%L), %L)", Runtime.SOLANA_ADDRESS, seeds.joinToCode(", "), programId ?: CodeBlock.of("programId"))
            .build()
    }

    private fun describe(seed: IdlSeed): String = when (seed) {
        is IdlSeed.Const -> if (seed.bytes.isNotEmpty() && seed.bytes.all { it in 0x20..0x7e }) {
            "`\"${String(ByteArray(seed.bytes.size) { seed.bytes[it].toByte() }, Charsets.US_ASCII)}\"`"
        } else if (seed.bytes.size == 32) {
            "`${Base58.encode(ByteArray(32) { seed.bytes[it].toByte() })}`"
        } else {
            "`0x${seed.bytes.joinToString("") { "%02x".format(it) }}`"
        }
        is IdlSeed.Arg -> "`${seed.path}`"
        is IdlSeed.Account -> "`${seed.path}`"
    }

    // --- constants ---

    private fun constant(constant: IdlConstant): PropertySpec {
        val builder = when (val type = resolve(constant.type)) {
            is IdlType.Primitive -> primitiveConstant(constant.name, type, constant.value.trim())
            else -> compositeConstant(constant)
        }
        constant.docs.forEach { builder.addKdoc("%L\n", it) }
        return builder.build()
    }

    private fun primitiveConstant(name: String, type: IdlType.Primitive, raw: String): PropertySpec.Builder {
        fun const(kotlinType: TypeName, format: String, value: Any) = PropertySpec.builder(name, kotlinType, KModifier.CONST).initializer(format, value)
        fun field(kotlinType: TypeName, format: String, vararg values: Any) = PropertySpec.builder(name, kotlinType).addAnnotation(Runtime.JVM_FIELD).initializer(format, *values)
        fun ranged(): String {
            val value = raw.replace("_", "").replace(INTEGER_SUFFIX, "").toBigInteger()
            val (min, max) = INTEGER_RANGES.getValue(type)
            require(value >= min && value <= max) { "constant '$name': $raw is outside the range of $type" }
            return value.toString()
        }
        return when (type) {
            IdlType.Primitive.BOOL -> const(BOOLEAN, "%L", raw.toBooleanStrict())
            IdlType.Primitive.U8, IdlType.Primitive.U16, IdlType.Primitive.I32 -> const(INT, "%L", ranged())
            IdlType.Primitive.U32 -> const(LONG, "%LL", ranged())
            IdlType.Primitive.I8 -> const(BYTE, "%L", ranged())
            IdlType.Primitive.I16 -> const(SHORT, "%L", ranged())
            IdlType.Primitive.I64 -> {
                val value = ranged()
                // Long.MIN_VALUE has no literal form
                if (value == Long.MIN_VALUE.toString()) const(LONG, "%L", "Long.MIN_VALUE") else const(LONG, "%LL", value)
            }
            IdlType.Primitive.U64, IdlType.Primitive.U128, IdlType.Primitive.U256, IdlType.Primitive.I128, IdlType.Primitive.I256 ->
                field(Runtime.BIG_INTEGER, "%T(%S)", Runtime.BIG_INTEGER, ranged())
            IdlType.Primitive.F32 -> const(FLOAT, "%Lf", raw.removeSuffix("f32").toFloat())
            IdlType.Primitive.F64 -> const(DOUBLE, "%L", raw.removeSuffix("f64").toDouble())
            IdlType.Primitive.STRING -> const(STRING, "%S", raw.removeSurrounding("\""))
            IdlType.Primitive.PUBKEY -> {
                val address = raw.removeSurrounding("\"")
                requireAddress(address) { throw IllegalArgumentException("constant '$name': $it") }
                field(Runtime.SOLANA_ADDRESS, "%T(%S)", Runtime.SOLANA_ADDRESS, address)
            }
            IdlType.Primitive.BYTES -> bytesConstant(name, raw)
        }
    }

    /**
     * A constant of an array, vec, option or defined type, as a `@JvmField val` constructing the value. Its text is the
     * Rust `Debug` output of the value, which [RustValueReader] reads against the declared type.
     */
    private fun compositeConstant(constant: IdlConstant): PropertySpec.Builder {
        val reader = RustValueReader(constant.value.trim(), "constant '${constant.name}'")
        val value = constantValue(reader, constant.type)
        reader.end()
        return PropertySpec.builder(constant.name, typeName(constant.type))
            .addAnnotation(Runtime.JVM_FIELD)
            .initializer(value)
    }

    /** An expression for the value of [type] that [reader] reads next. */
    private fun constantValue(reader: RustValueReader, type: IdlType): CodeBlock = when (val resolved = resolve(type)) {
        is IdlType.Primitive -> primitiveValue(reader, resolved)
        is IdlType.Option -> {
            // a nullable inner type needs the explicit option, as in typeName
            val explicit = typeName(resolved.inner).isNullable
            when (val word = reader.word("'Some(…)' or 'None'")) {
                "None" -> if (explicit) CodeBlock.of("%T", Runtime.SOLANA_OPTION.nestedClass("None")) else CodeBlock.of("null")
                "Some" -> {
                    reader.punct('(')
                    val inner = constantValue(reader, resolved.inner)
                    reader.punct(')')
                    if (explicit) CodeBlock.of("%T<%T>(%L)", Runtime.SOLANA_OPTION.nestedClass("Some"), typeName(resolved.inner), inner) else inner
                }
                else -> reader.fail("'$word' is not 'Some(…)' or 'None'")
            }
        }
        is IdlType.Vec -> listValue(reader, resolved.element, null)
        is IdlType.Array -> if (isByteArray(resolved)) {
            bytesValue(reader, arrayLength(resolved))
        } else {
            listValue(reader, resolved.element, arrayLength(resolved))
        }
        is IdlType.Defined -> definedValue(reader, typeDefs.getValue(resolved.name))
        is IdlType.Generic -> throw IllegalStateException("unsupported generic reached generation")
    }

    private fun primitiveValue(reader: RustValueReader, type: IdlType.Primitive): CodeBlock = when (type) {
        IdlType.Primitive.BOOL -> when (val word = reader.word("'true' or 'false'")) {
            "true", "false" -> CodeBlock.of("%L", word)
            else -> reader.fail("'$word' is not 'true' or 'false'")
        }
        IdlType.Primitive.U8, IdlType.Primitive.U16, IdlType.Primitive.I32 -> {
            val value = integerValue(reader, type)
            if (value == Int.MIN_VALUE.toString()) CodeBlock.of("Int.MIN_VALUE") else CodeBlock.of("%L", value)
        }
        // the parameter or element type the value is given for makes the literal a Byte or Short
        IdlType.Primitive.I8, IdlType.Primitive.I16 -> CodeBlock.of("%L", integerValue(reader, type))
        IdlType.Primitive.U32, IdlType.Primitive.I64 -> {
            val value = integerValue(reader, type)
            if (value == Long.MIN_VALUE.toString()) CodeBlock.of("Long.MIN_VALUE") else CodeBlock.of("%LL", value)
        }
        IdlType.Primitive.U64, IdlType.Primitive.U128, IdlType.Primitive.U256, IdlType.Primitive.I128, IdlType.Primitive.I256 ->
            CodeBlock.of("%T(%S)", Runtime.BIG_INTEGER, integerValue(reader, type))
        IdlType.Primitive.F32 -> when (val text = floatText(reader, type)) {
            in FLOAT_SPECIALS -> CodeBlock.of("Float.%L", FLOAT_SPECIALS.getValue(text))
            else -> {
                val value = text.removeSuffix("f32").toFloat()
                if (value.isInfinite()) reader.fail("'$text' is outside the range of $type")
                CodeBlock.of("%Lf", value.toString())
            }
        }
        IdlType.Primitive.F64 -> when (val text = floatText(reader, type)) {
            in FLOAT_SPECIALS -> CodeBlock.of("Double.%L", FLOAT_SPECIALS.getValue(text))
            else -> {
                val value = text.removeSuffix("f64").toDouble()
                if (value.isInfinite()) reader.fail("'$text' is outside the range of $type")
                CodeBlock.of("%L", value.toString())
            }
        }
        IdlType.Primitive.STRING -> CodeBlock.of("%S", reader.string())
        IdlType.Primitive.PUBKEY -> {
            // Debug writes a pubkey as its base58 form, unquoted
            val address = reader.word("a base58 address")
            requireAddress(address) { reader.fail(it) }
            CodeBlock.of("%T(%S)", Runtime.SOLANA_ADDRESS, address)
        }
        IdlType.Primitive.BYTES -> bytesValue(reader, null)
    }

    /** The decimal value of the [type] integer that [reader] reads next, checked against the range of [type]. */
    private fun integerValue(reader: RustValueReader, type: IdlType.Primitive): String {
        val word = reader.word("a $type integer literal")
        val digits = word.replace("_", "").replace(INTEGER_SUFFIX, "")
        if (!DECIMAL_INTEGER.matches(digits)) reader.fail("'$word' is not a decimal $type integer literal")
        val value = digits.toBigInteger()
        val (min, max) = INTEGER_RANGES.getValue(type)
        if (value < min || value > max) reader.fail("'$word' is outside the range of $type")
        return value.toString()
    }

    /** The float literal [reader] reads next, without `_`, either decimal with an optional suffix or a special value. */
    private fun floatText(reader: RustValueReader, type: IdlType.Primitive): String {
        val word = reader.word("a $type literal")
        if (word in FLOAT_SPECIALS) return word
        val text = word.replace("_", "")
        if (!DECIMAL_FLOAT.matches(text.removeSuffix(if (type == IdlType.Primitive.F32) "f32" else "f64"))) {
            reader.fail("'$word' is not a decimal $type literal")
        }
        return text
    }

    /** Bytes written as a list of `u8`, `[1, 2, 3]`, with [length] elements when fixed. */
    private fun bytesValue(reader: RustValueReader, length: Int?): CodeBlock {
        val hex = StringBuilder()
        val size = listItems(reader) { hex.append("%02x".format(integerValue(reader, IdlType.Primitive.U8).toInt())) }
        if (length != null && size != length) reader.fail("expected $length elements for [u8; $length], got $size")
        return CodeBlock.of("%T.fromHex(%S)", Runtime.SOLANA_BYTES, hex.toString())
    }

    /** A list of [element] values, `[a, b]`, with [length] elements when fixed. */
    private fun listValue(reader: RustValueReader, element: IdlType, length: Int?): CodeBlock {
        val items = ArrayList<CodeBlock>()
        listItems(reader) { items.add(constantValue(reader, element)) }
        if (length != null && items.size != length) reader.fail("expected $length elements for an array of length $length, got ${items.size}")
        // explicit type arguments give integer literals their type, and an empty list its element type
        val elementType = typeName(element)
        return if (items.isEmpty()) CodeBlock.of("emptyList<%T>()", elementType) else CodeBlock.of("listOf<%T>(%L)", elementType, items.joinToCode(", "))
    }

    /** Read `[item, item]`, calling [item] for each, and return the number of items. */
    private inline fun listItems(reader: RustValueReader, item: () -> Unit): Int {
        reader.punct('[')
        if (reader.tryPunct(']')) return 0
        var count = 0
        do {
            item()
            count++
        } while (reader.tryPunct(','))
        reader.punct(']')
        return count
    }

    /**
     * A value of [def], which Debug writes with its Rust name: `Name { a: 1 }`, `Name(1, 2)` or `Name` for a struct, and
     * the same forms with a variant name for an enum.
     */
    private fun definedValue(reader: RustValueReader, def: IdlTypeDef): CodeBlock {
        val type = classOf(def.name)
        return when (val body = def.body) {
            is IdlTypeBody.Struct -> {
                val rustName = def.name.substringAfterLast("::")
                val word = reader.word("'$rustName'")
                if (word != rustName) reader.fail("'$word' is not a value of struct '${def.name}', which Debug writes as '$rustName'")
                fieldValues(reader, type, body.fields, "struct '${def.name}'")
            }
            is IdlTypeBody.Enum -> {
                val word = reader.word("a variant of enum '${def.name}'")
                val variant = body.variants.firstOrNull { it.name == word }
                    ?: reader.fail("'$word' is not a variant of enum '${def.name}', which has ${body.variants.joinToString { "'${it.name}'" }}")
                fieldValues(reader, type.nestedClass(variant.name.toPascalCase()), variant.fields, "variant '${variant.name}' of enum '${def.name}'")
            }
            is IdlTypeBody.Alias -> throw IllegalStateException("aliases are resolved at use")
        }
    }

    /** The [fields] of [owner] that follow its name, every one in declaration order, constructing [type]. */
    private fun fieldValues(reader: RustValueReader, type: ClassName, fields: IdlFields, owner: String): CodeBlock {
        val kotlinFields = fields(fields)
        if (kotlinFields.isEmpty()) {
            if (reader.atPunct('{') || reader.atPunct('(')) reader.fail("$owner has no fields, so Debug writes only its name")
            return CodeBlock.of("%T", type)
        }
        val named = fields as? IdlFields.Named
        val (open, close) = if (named != null) '{' to '}' else '(' to ')'
        reader.punct(open)
        val values = kotlinFields.mapIndexed { i, field ->
            if (i > 0) {
                if (reader.atPunct(close)) reader.fail("$owner has ${kotlinFields.size} fields, but the value gives $i")
                reader.punct(',')
            }
            if (named != null) {
                val expected = named.fields[i].name
                val word = reader.word("field '$expected'")
                if (word != expected) reader.fail("expected field '$expected' of $owner, got '$word'; every field must be given, in declaration order")
                reader.punct(':')
                CodeBlock.of("%N = %L", field.name, constantValue(reader, field.type))
            } else {
                constantValue(reader, field.type)
            }
        }
        if (reader.atPunct(',')) reader.fail("the value gives more than the ${kotlinFields.size} fields of $owner")
        reader.punct(close)
        // one argument per line once a value is too wide to read on one
        return if (values.size > 3) CodeBlock.of("%T(\n⇥%L,\n⇤)", type, values.joinToCode(",\n")) else CodeBlock.of("%T(%L)", type, values.joinToCode(", "))
    }

    /** Require [address] to be the base58 form of 32 bytes, calling [fail] with the reason otherwise. */
    private inline fun requireAddress(address: String, fail: (String) -> Nothing) {
        val bytes = try {
            Base58.decode(address)
        } catch (e: IllegalArgumentException) {
            fail("'$address' is not a base58 address")
        }
        if (bytes.size != 32) fail("'$address' decodes to ${bytes.size} bytes, but an address has 32")
    }

    /** Bytes written as a Rust array literal, `[1, 2, 3]`. */
    private fun bytesConstant(name: String, raw: String): PropertySpec.Builder {
        val bytes = raw.removePrefix("[").removeSuffix("]").split(',').map(String::trim).filter(String::isNotEmpty).map {
            val byte = it.toIntOrNull()
            require(byte != null && byte in 0..255) { "constant '$name': '$it' is not a byte" }
            byte
        }
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return PropertySpec.builder(name, Runtime.SOLANA_BYTES)
            .addAnnotation(Runtime.JVM_FIELD)
            .initializer("%T.fromHex(%S)", Runtime.SOLANA_BYTES, hex)
    }

    companion object {
        /**
         * Version of the generated output. Bump it whenever the output changes, so build tools invalidate
         * previously generated bindings.
         */
        const val OUTPUT_VERSION = "4"

        private const val FETCH_ALIAS = "fetchProgramAccount"
        private const val WATCH_ALIAS = "watchProgramAccount"

        /** Properties of an account slice, which an extension property of the same name cannot be read through. */
        private val SLICE_MEMBERS = setOf("address", "context", "info", "selection")

        /** Members of the account filter base class that a field method must not shadow. */
        private val FILTER_BASE_MEMBERS = setOf("program", "commitment", "memcmp", "dataSize", "field", "query", "subscribe", "accountFilters", "self")

        /** A Rust integer literal's type suffix, such as `u64` in `1_000u64`. */
        private val INTEGER_SUFFIX = Regex("[ui](8|16|32|64|128|256|size)$")

        private val DECIMAL_INTEGER = Regex("-?[0-9]+")

        /** A float as Rust's `Debug` writes it, such as `1.5`, `-0.0` or `1e-7`. */
        private val DECIMAL_FLOAT = Regex("-?[0-9]+(\\.[0-9]+)?([eE]-?[0-9]+)?")

        /** The float values `Debug` writes as words, and their Kotlin constants on `Float` and `Double`. */
        private val FLOAT_SPECIALS = mapOf("NaN" to "NaN", "inf" to "POSITIVE_INFINITY", "-inf" to "NEGATIVE_INFINITY")

        /** The inclusive range of each integer primitive. */
        private val INTEGER_RANGES: Map<IdlType.Primitive, Pair<BigInteger, BigInteger>> = mapOf(
            IdlType.Primitive.U8 to unsigned(8),
            IdlType.Primitive.U16 to unsigned(16),
            IdlType.Primitive.U32 to unsigned(32),
            IdlType.Primitive.U64 to unsigned(64),
            IdlType.Primitive.U128 to unsigned(128),
            IdlType.Primitive.U256 to unsigned(256),
            IdlType.Primitive.I8 to signed(8),
            IdlType.Primitive.I16 to signed(16),
            IdlType.Primitive.I32 to signed(32),
            IdlType.Primitive.I64 to signed(64),
            IdlType.Primitive.I128 to signed(128),
            IdlType.Primitive.I256 to signed(256),
        )

        private fun unsigned(bits: Int) = BigInteger.ZERO to BigInteger.ONE.shiftLeft(bits) - BigInteger.ONE

        private fun signed(bits: Int) = -BigInteger.ONE.shiftLeft(bits - 1) to BigInteger.ONE.shiftLeft(bits - 1) - BigInteger.ONE

        private val PRIMITIVE_TYPES: Map<IdlType.Primitive, TypeName> = mapOf(
            IdlType.Primitive.BOOL to BOOLEAN,
            IdlType.Primitive.U8 to INT,
            IdlType.Primitive.U16 to INT,
            IdlType.Primitive.U32 to LONG,
            IdlType.Primitive.U64 to Runtime.BIG_INTEGER,
            IdlType.Primitive.U128 to Runtime.BIG_INTEGER,
            IdlType.Primitive.U256 to Runtime.BIG_INTEGER,
            IdlType.Primitive.I8 to BYTE,
            IdlType.Primitive.I16 to SHORT,
            IdlType.Primitive.I32 to INT,
            IdlType.Primitive.I64 to LONG,
            IdlType.Primitive.I128 to Runtime.BIG_INTEGER,
            IdlType.Primitive.I256 to Runtime.BIG_INTEGER,
            IdlType.Primitive.F32 to FLOAT,
            IdlType.Primitive.F64 to DOUBLE,
            IdlType.Primitive.BYTES to Runtime.SOLANA_BYTES,
            IdlType.Primitive.STRING to STRING,
            IdlType.Primitive.PUBKEY to Runtime.SOLANA_ADDRESS,
        )

        /** Integers, which a seed encodes like Borsh does, in a byte order of its own. */
        private val INTEGER_TYPES = setOf(
            IdlType.Primitive.U8,
            IdlType.Primitive.U16,
            IdlType.Primitive.U32,
            IdlType.Primitive.U64,
            IdlType.Primitive.U128,
            IdlType.Primitive.U256,
            IdlType.Primitive.I8,
            IdlType.Primitive.I16,
            IdlType.Primitive.I32,
            IdlType.Primitive.I64,
            IdlType.Primitive.I128,
            IdlType.Primitive.I256,
        )

        /** The encoded size of each fixed-size primitive. */
        private val PRIMITIVE_SIZES: Map<IdlType.Primitive, Int> = mapOf(
            IdlType.Primitive.BOOL to 1,
            IdlType.Primitive.U8 to 1,
            IdlType.Primitive.I8 to 1,
            IdlType.Primitive.U16 to 2,
            IdlType.Primitive.I16 to 2,
            IdlType.Primitive.U32 to 4,
            IdlType.Primitive.I32 to 4,
            IdlType.Primitive.F32 to 4,
            IdlType.Primitive.U64 to 8,
            IdlType.Primitive.I64 to 8,
            IdlType.Primitive.F64 to 8,
            IdlType.Primitive.U128 to 16,
            IdlType.Primitive.I128 to 16,
            IdlType.Primitive.U256 to 32,
            IdlType.Primitive.I256 to 32,
            IdlType.Primitive.PUBKEY to 32,
        )

        /** The suffix of each primitive's `BorshReader.readX` and `BorshWriter.writeX`. */
        private val PRIMITIVE_CODEC: Map<IdlType.Primitive, String> = mapOf(
            IdlType.Primitive.BOOL to "Bool",
            IdlType.Primitive.U8 to "U8",
            IdlType.Primitive.U16 to "U16",
            IdlType.Primitive.U32 to "U32",
            IdlType.Primitive.U64 to "U64",
            IdlType.Primitive.U128 to "U128",
            IdlType.Primitive.U256 to "U256",
            IdlType.Primitive.I8 to "I8",
            IdlType.Primitive.I16 to "I16",
            IdlType.Primitive.I32 to "I32",
            IdlType.Primitive.I64 to "I64",
            IdlType.Primitive.I128 to "I128",
            IdlType.Primitive.I256 to "I256",
            IdlType.Primitive.F32 to "F32",
            IdlType.Primitive.F64 to "F64",
            IdlType.Primitive.BYTES to "Bytes",
            IdlType.Primitive.STRING to "String",
            IdlType.Primitive.PUBKEY to "Address",
        )
    }
}
