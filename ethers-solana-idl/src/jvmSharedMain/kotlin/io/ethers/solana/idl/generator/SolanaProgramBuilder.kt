package io.ethers.solana.idl.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE
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
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.joinToCode
import io.ethers.solana.idl.IdlAccountItem
import io.ethers.solana.idl.IdlArrayLength
import io.ethers.solana.idl.IdlConstant
import io.ethers.solana.idl.IdlDiscriminator
import io.ethers.solana.idl.IdlErrorCode
import io.ethers.solana.idl.IdlEvent
import io.ethers.solana.idl.IdlFields
import io.ethers.solana.idl.IdlInstruction
import io.ethers.solana.idl.IdlProgram
import io.ethers.solana.idl.IdlSerialization
import io.ethers.solana.idl.IdlType
import io.ethers.solana.idl.IdlTypeBody
import io.ethers.solana.idl.IdlTypeDef
import io.ethers.solana.idl.IdlValidator
import java.io.File

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
 * Aliases are resolved to their target at each use. Generic type definitions and non-Borsh
 * serialization are not supported yet and fail generation.
 *
 * @throws IllegalArgumentException if [program] is invalid, uses an unsupported layout, or its names
 * produce colliding declarations.
 */
class SolanaProgramBuilder @JvmOverloads constructor(
    private val program: IdlProgram,
    private val packageName: String,
    private val programName: String = program.name.toPascalCase(),
) {
    private val programClass = ClassName(packageName, programName)
    private val accountMarker = programClass.nestedClass("Account")
    private val eventInterface = programClass.nestedClass("Event")
    private val errorClass = programClass.nestedClass("Error")
    private val instructionHierarchy = programClass.nestedClass("Instruction")

    private val typeDefs = program.types.associateBy { it.name }
    private val accountTypes = program.accounts.mapTo(HashSet()) { it.name }
    private val eventTypes = program.events.mapTo(HashSet()) { it.name }
    private val kotlinTypeNames = kotlinTypeNames()

    init {
        IdlValidator.validate(program)
        checkSupported()
        checkCollisions()
    }

    fun build(): FileSpec {
        val programType = TypeSpec.objectBuilder(programClass)
            .addKdoc("Bindings for the %L program, version %L.\n", program.name, program.version)
        program.docs.forEach { programType.addKdoc("\n%L", it) }

        programType.addProperty(
            PropertySpec.builder("PROGRAM_ID", Runtime.SOLANA_ADDRESS)
                .addAnnotation(Runtime.JVM_FIELD)
                .initializer("%T(%S)", Runtime.SOLANA_ADDRESS, program.address)
                .build(),
        )
        program.constants.forEach { programType.addProperty(constant(it)) }

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
            .build()
    }

    fun writeTo(directory: File) = build().writeTo(directory)

    // --- checks ---

    private fun checkSupported() {
        for (def in program.types) {
            require(def.generics.isEmpty()) { "type '${def.name}': generic type definitions are not supported yet" }
            require(def.serialization == IdlSerialization.Borsh) {
                "type '${def.name}': ${def.serialization} serialization is not supported yet"
            }
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
            is IdlTypeBody.Struct -> fixedSize(body.fields)
            // an enum's size depends on its variant, unless no variant carries data
            is IdlTypeBody.Enum -> if (body.variants.all { it.fields.isEmpty() }) 1 else null
            is IdlTypeBody.Alias -> fixedSize(body.type)
        }
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

    private fun fields(fields: IdlFields, reserved: Set<String> = emptySet()): List<Field> = when (fields) {
        IdlFields.None -> emptyList()
        is IdlFields.Named -> fields.fields.map { field ->
            val name = field.name.toCamelCase()
            Field(if (name in reserved) "${name}Field" else name, field.type, field.docs)
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

    /** `Name(a = readX(), ...)` or `Name` for a value without fields. */
    private fun construct(name: ClassName, fields: List<Field>, extra: List<CodeBlock> = emptyList()): CodeBlock {
        if (fields.isEmpty() && extra.isEmpty()) return CodeBlock.of("%T", name)
        val arguments = fields.map { CodeBlock.of("%N = %L", it.name, read(it.type)) } + extra
        return CodeBlock.of("%T(\n⇥%L,\n⇤)", name, arguments.joinToCode(",\n"))
    }

    private fun writes(fields: List<Field>, owner: String): CodeBlock = fields
        .map { write(it.type, CodeBlock.of("%L.%N", owner, it.name)) }
        .joinToCode("\n")

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
            is IdlTypeBody.Struct -> struct(name, fields(body.fields))
            is IdlTypeBody.Enum -> enum(name, def, body)
            is IdlTypeBody.Alias -> throw IllegalStateException("aliases are resolved at use")
        }
        def.docs.forEach { builder.addKdoc("%L\n", it) }
        program.accounts.firstOrNull { it.name == def.name }?.let { account ->
            builder.addSuperinterface(accountMarker)
            addToCodecScope(builder, accountFactory(name, account.name, account.discriminator))
            val fields = (def.body as? IdlTypeBody.Struct)?.let { fields(it.fields) }.orEmpty()
            builder.addType(accountFilter(name, fields))
            accountAccessors(name).forEach { addToCodecScope(builder, it) }
        }
        return builder.build()
    }

    private fun struct(name: ClassName, fields: List<Field>): TypeSpec.Builder {
        val builder = valueClass(name, fields)
        val codec = listOf(readFunction(name, construct(name, fields)), writeFunction(name, writes(fields, "value")))
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
     * The account's filter class: a method per field at a known offset, where every field before it has a
     * fixed size. There is no data size method, since the IDL does not say how much space an account is
     * allocated.
     */
    private fun accountFilter(account: ClassName, fields: List<Field>): TypeSpec {
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
        var offset: Int? = 0
        for (field in fields) {
            val at = offset ?: break
            val method = if (field.name in FILTER_BASE_MEMBERS) "${field.name}Field" else field.name
            builder.addFunction(
                FunSpec.builder(method)
                    .addKdoc("Select accounts whose [%T.%N] equals [value], at payload offset %L.\n", account, field.name, at)
                    .addParameter("value", typeName(field.type))
                    .returns(filterClass)
                    .addStatement("return field(%L) { %L }", at, write(field.type, CodeBlock.of("value")))
                    .build(),
            )
            offset = fixedSize(field.type)?.plus(at)
        }
        return builder.build()
    }

    /** `filter`, `fetch` and `watch` for an account type, bound to the program address by default. */
    private fun accountAccessors(account: ClassName): List<FunSpec> {
        val programId = ParameterSpec.builder("programId", Runtime.SOLANA_ADDRESS).defaultValue("PROGRAM_ID").build()
        val commitment = ParameterSpec.builder("commitment", Runtime.COMMITMENT).defaultValue("api.defaultCommitment").build()
        val maybeAccount = Runtime.MAYBE_SOLANA_ACCOUNT.parameterizedBy(account)
        return listOf(
            FunSpec.builder("filter")
                .addKdoc("Filter over the %L accounts the program owns.\n", account.simpleName)
                .addAnnotation(Runtime.JVM_STATIC)
                .addParameter("api", Runtime.SOLANA_API)
                .returns(account.nestedClass("Filter"))
                .addStatement("return Filter(api)")
                .build(),
            FunSpec.builder("fetch")
                .addKdoc("Fetch the %L account at [address], or a missing account when none exists there.\n", account.simpleName)
                .addAnnotation(Runtime.JVM_STATIC)
                .addAnnotation(Runtime.JVM_OVERLOADS)
                .addParameter("api", Runtime.SOLANA_API)
                .addParameter("address", Runtime.SOLANA_ADDRESS)
                .addParameter(programId)
                .addParameter(commitment)
                .returns(Runtime.RPC_REQUEST.parameterizedBy(maybeAccount, Runtime.SOLANA_ACCOUNT_ERROR))
                .addStatement("return ACCOUNT.%L(api, address, programId, commitment)", FETCH_ALIAS)
                .build(),
            FunSpec.builder("fetch")
                .addKdoc("Fetch the %L accounts at [addresses] in order, with a missing account for each that does not exist.\n", account.simpleName)
                .addAnnotation(Runtime.JVM_STATIC)
                .addAnnotation(Runtime.JVM_OVERLOADS)
                .addParameter("api", Runtime.SOLANA_API)
                .addParameter("addresses", LIST.parameterizedBy(Runtime.SOLANA_ADDRESS))
                .addParameter(programId)
                .addParameter(commitment)
                .returns(Runtime.RPC_REQUEST.parameterizedBy(LIST.parameterizedBy(maybeAccount), Runtime.SOLANA_ACCOUNT_ERROR))
                .addStatement("return ACCOUNT.%L(api, addresses, programId, commitment)", FETCH_ALIAS)
                .build(),
            FunSpec.builder("watch")
                .addKdoc("Subscribe to the %L account at [address]; notifications are a missing account once it no longer exists.\n", account.simpleName)
                .addAnnotation(Runtime.JVM_STATIC)
                .addAnnotation(Runtime.JVM_OVERLOADS)
                .addParameter("api", Runtime.SOLANA_API)
                .addParameter("address", Runtime.SOLANA_ADDRESS)
                .addParameter(programId)
                .addParameter(commitment)
                .returns(Runtime.RPC_SUBSCRIBE.parameterizedBy(maybeAccount, Runtime.RPC_ERROR))
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
            programType.addType(accounts(accountsClass, ix.accounts).addKdoc("Accounts of the %L instruction.\n", ix.name).build())
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

    /** An account roles class: one property per account, nested classes for account groups. */
    private fun accounts(name: ClassName, items: List<IdlAccountItem>): TypeSpec.Builder {
        val constructor = FunSpec.constructorBuilder()
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
                    builder.addType(accounts(groupClass, item.accounts).build())
                    type = groupClass
                    parameter = ParameterSpec.builder(property, type).build()
                    metas.addStatement("addAll(%N.toAccountMetas(programId))", property)
                    reads.add(CodeBlock.of("%N = %T.read(reader)", property, groupClass))
                }
                is IdlAccountItem.Single -> {
                    type = Runtime.SOLANA_ADDRESS.copy(nullable = item.optional)
                    parameter = ParameterSpec.builder(property, type).apply {
                        item.docs.forEach { addKdoc("%L\n", it) }
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
                    .build(),
            )
    }

    // --- constants ---

    private fun constant(constant: IdlConstant): PropertySpec {
        val type = resolve(constant.type)
        val raw = constant.value.trim()
        val builder = when (type) {
            is IdlType.Primitive -> primitiveConstant(constant.name, type, raw)
            is IdlType.Array -> if (isByteArray(type)) bytesConstant(constant.name, raw) else null
            else -> null
        } ?: throw IllegalArgumentException("constant '${constant.name}': ${constant.type} constants are not supported yet")
        constant.docs.forEach { builder.addKdoc("%L\n", it) }
        return builder.build()
    }

    private fun primitiveConstant(name: String, type: IdlType.Primitive, raw: String): PropertySpec.Builder? {
        fun const(kotlinType: TypeName, format: String, value: Any) = PropertySpec.builder(name, kotlinType, KModifier.CONST).initializer(format, value)
        fun field(kotlinType: TypeName, format: String, vararg values: Any) = PropertySpec.builder(name, kotlinType).addAnnotation(Runtime.JVM_FIELD).initializer(format, *values)
        val integer by lazy { raw.replace("_", "").replace(INTEGER_SUFFIX, "") }
        fun ranged(min: String, max: String): String {
            val value = integer.toBigInteger()
            require(value >= min.toBigInteger() && value <= max.toBigInteger()) { "constant '$name': $raw is outside the range of $type" }
            return value.toString()
        }
        return when (type) {
            IdlType.Primitive.BOOL -> const(BOOLEAN, "%L", raw.toBooleanStrict())
            IdlType.Primitive.U8 -> const(INT, "%L", ranged("0", "255"))
            IdlType.Primitive.U16 -> const(INT, "%L", ranged("0", "65535"))
            IdlType.Primitive.U32 -> const(LONG, "%LL", ranged("0", "4294967295"))
            IdlType.Primitive.I8 -> const(BYTE, "%L", ranged("-128", "127"))
            IdlType.Primitive.I16 -> const(SHORT, "%L", ranged("-32768", "32767"))
            IdlType.Primitive.I32 -> const(INT, "%L", ranged(Int.MIN_VALUE.toString(), Int.MAX_VALUE.toString()))
            IdlType.Primitive.I64 -> {
                val value = ranged(Long.MIN_VALUE.toString(), Long.MAX_VALUE.toString())
                // Long.MIN_VALUE has no literal form
                if (value == Long.MIN_VALUE.toString()) const(LONG, "%L", "Long.MIN_VALUE") else const(LONG, "%LL", value)
            }
            IdlType.Primitive.U64 -> field(Runtime.BIG_INTEGER, "%T(%S)", Runtime.BIG_INTEGER, ranged("0", "18446744073709551615"))
            IdlType.Primitive.U128 -> field(Runtime.BIG_INTEGER, "%T(%S)", Runtime.BIG_INTEGER, ranged("0", "340282366920938463463374607431768211455"))
            IdlType.Primitive.U256 -> field(Runtime.BIG_INTEGER, "%T(%S)", Runtime.BIG_INTEGER, ranged("0", "115792089237316195423570985008687907853269984665640564039457584007913129639935"))
            IdlType.Primitive.I128 -> field(
                Runtime.BIG_INTEGER,
                "%T(%S)",
                Runtime.BIG_INTEGER,
                ranged("-170141183460469231731687303715884105728", "170141183460469231731687303715884105727"),
            )
            IdlType.Primitive.I256 -> field(
                Runtime.BIG_INTEGER,
                "%T(%S)",
                Runtime.BIG_INTEGER,
                ranged(
                    "-57896044618658097711785492504343953926634992332820282019728792003956564819968",
                    "57896044618658097711785492504343953926634992332820282019728792003956564819967",
                ),
            )
            IdlType.Primitive.F32 -> const(FLOAT, "%Lf", raw.removeSuffix("f32").toFloat())
            IdlType.Primitive.F64 -> const(DOUBLE, "%L", raw.removeSuffix("f64").toDouble())
            IdlType.Primitive.STRING -> const(STRING, "%S", raw.removeSurrounding("\""))
            IdlType.Primitive.PUBKEY -> field(Runtime.SOLANA_ADDRESS, "%T(%S)", Runtime.SOLANA_ADDRESS, raw.removeSurrounding("\""))
            IdlType.Primitive.BYTES -> bytesConstant(name, raw)
        }
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

    private companion object {
        const val FETCH_ALIAS = "fetchProgramAccount"
        const val WATCH_ALIAS = "watchProgramAccount"

        /** Members of the account filter base class that a field method must not shadow. */
        val FILTER_BASE_MEMBERS = setOf("program", "commitment", "memcmp", "dataSize", "field", "query", "subscribe", "accountFilters", "self")

        /** A Rust integer literal's type suffix, such as `u64` in `1_000u64`. */
        val INTEGER_SUFFIX = Regex("[ui](8|16|32|64|128|256|size)$")

        val PRIMITIVE_TYPES: Map<IdlType.Primitive, TypeName> = mapOf(
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

        /** The encoded size of each fixed-size primitive. */
        val PRIMITIVE_SIZES: Map<IdlType.Primitive, Int> = mapOf(
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
        val PRIMITIVE_CODEC: Map<IdlType.Primitive, String> = mapOf(
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
