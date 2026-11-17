package io.ethers.solana.idl.generator

import io.ethers.solana.idl.IdlArrayLength
import io.ethers.solana.idl.IdlEnumVariant
import io.ethers.solana.idl.IdlField
import io.ethers.solana.idl.IdlFields
import io.ethers.solana.idl.IdlGenericArg
import io.ethers.solana.idl.IdlGenericParam
import io.ethers.solana.idl.IdlProgram
import io.ethers.solana.idl.IdlType
import io.ethers.solana.idl.IdlTypeBody
import io.ethers.solana.idl.IdlTypeDef

/**
 * Monomorphizes a validated [source]: each distinct instantiation of a generic type definition becomes a concrete type
 * definition of its own, and every reference to one names it instead.
 *
 * Generic definitions are not generated as Kotlin generics, since a const argument determines a layout, such as the
 * length of `[T; N]`, and a Borsh codec needs a concrete one. An instantiation is named in Rust syntax in the schema,
 * such as `Pair<u64, [u8; 4]>`, and in Kotlin after its definition and arguments, such as `PairU64Array4U8`. A const
 * argument is written as its parameter's name and value, so `Buffer<8>` with `const N` becomes `BufferN8`.
 *
 * Arguments are compared as Rust compares types, with aliases resolved, so `Pair<Alias>` and `Pair<u64>` for
 * `type Alias = u64` are one instantiation. A generic alias is inlined at each use rather than instantiated.
 *
 * Instantiations are listed in place of their generic definition, in the order the program first uses them: its type
 * definitions in order, then instruction arguments and return types, then constants. A generic definition the program
 * never instantiates is left out, as it has no layout. Generic definitions an account or event is laid out as are
 * rejected, since the IDL does not say which instantiation the program stores.
 *
 * @param baseNames the Kotlin name of each type definition of [source].
 */
internal class GenericInstances(private val source: IdlProgram, private val baseNames: Map<String, String>) {
    private val types = source.types.associateBy { it.name }

    /** A concrete generic argument. */
    private sealed interface Bound {
        data class Type(val type: IdlType) : Bound

        data class Const(val value: ULong) : Bound
    }

    private class Pending(val def: IdlTypeDef, val name: String, val env: Map<String, Bound>)

    /** Instantiation names of each generic definition, in order of first use. */
    private val instancesOf = HashMap<String, MutableList<String>>()
    private val instances = HashMap<String, IdlTypeDef>()
    private val pending = ArrayDeque<Pending>()

    /** Generic aliases being inlined, to reject one that refers to itself. */
    private val inlining = ArrayList<String>()

    /** The Kotlin name of each instantiation, by its name in the schema. */
    val kotlinNames = LinkedHashMap<String, String>()

    /** The generic definition each instantiation instantiates, by the instantiation's name in the schema. */
    val definitions = HashMap<String, String>()

    /** [source] with each generic definition replaced by its instantiations. */
    val program: IdlProgram = if (source.types.none { it.generics.isNotEmpty() }) source else expand()

    private fun expand(): IdlProgram {
        for (account in source.accounts) {
            require(types.getValue(account.name).generics.isEmpty()) {
                "account '${account.name}': its type definition is generic, and the IDL does not say which instantiation the account holds"
            }
        }
        for (event in source.events) {
            require(types.getValue(event.name).generics.isEmpty()) {
                "event '${event.name}': its type definition is generic, and the IDL does not say which instantiation the event holds"
            }
        }
        checkFiniteExpansion()

        val concrete = HashMap<String, IdlTypeDef>()
        for (def in source.types) {
            if (def.generics.isNotEmpty()) continue
            concrete[def.name] = def.copy(body = body(def.body, emptyMap(), "type '${def.name}'"))
            drain()
        }
        val instructions = source.instructions.map { ix ->
            val where = "instruction '${ix.name}'"
            ix.copy(
                args = ix.args.map { it.copy(type = type(it.type, emptyMap(), "$where argument '${it.name}'")) },
                returns = ix.returns?.let { type(it, emptyMap(), "$where return type") },
            ).also { drain() }
        }
        val constants = source.constants.map { it.copy(type = type(it.type, emptyMap(), "constant '${it.name}'")).also { drain() } }

        val expanded = source.types.flatMap { def ->
            if (def.generics.isEmpty()) listOf(concrete.getValue(def.name)) else instancesOf[def.name].orEmpty().map(instances::getValue)
        }
        return source.copy(types = expanded, instructions = instructions, constants = constants)
    }

    private fun drain() {
        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            val where = "type '${next.name}'"
            val docs = next.def.docs + "Instantiation `${next.name}` of the generic `${next.def.name}`."
            instances[next.name] = IdlTypeDef(next.name, body(next.def.body, next.env, where), next.def.serialization, next.def.repr, emptyList(), docs)
        }
    }

    private fun body(body: IdlTypeBody, env: Map<String, Bound>, where: String): IdlTypeBody = when (body) {
        is IdlTypeBody.Struct -> IdlTypeBody.Struct(fields(body.fields, env, where))
        is IdlTypeBody.Enum -> IdlTypeBody.Enum(body.variants.map { IdlEnumVariant(it.name, fields(it.fields, env, "$where variant '${it.name}'")) })
        is IdlTypeBody.Alias -> IdlTypeBody.Alias(type(body.type, env, where))
    }

    private fun fields(fields: IdlFields, env: Map<String, Bound>, where: String): IdlFields = when (fields) {
        IdlFields.None -> fields
        is IdlFields.Named -> IdlFields.Named(fields.fields.map { IdlField(it.name, type(it.type, env, "$where field '${it.name}'"), it.docs) })
        is IdlFields.Tuple -> IdlFields.Tuple(fields.types.mapIndexed { i, type -> type(type, env, "$where field $i") })
    }

    /** [type] with the parameters [env] binds substituted, and generic definitions it uses instantiated. */
    private fun type(type: IdlType, env: Map<String, Bound>, where: String): IdlType = when (type) {
        is IdlType.Primitive -> type
        is IdlType.Option -> IdlType.Option(type(type.inner, env, where))
        is IdlType.Vec -> IdlType.Vec(type(type.element, env, where))
        is IdlType.Array -> {
            val length = when (val length = type.length) {
                is IdlArrayLength.Fixed -> length
                is IdlArrayLength.Generic -> {
                    val value = (env.getValue(length.name) as Bound.Const).value
                    require(value <= Int.MAX_VALUE.toULong()) { "$where: array length $value, from const generic parameter '${length.name}', is too large" }
                    IdlArrayLength.Fixed(value.toInt())
                }
            }
            IdlType.Array(type(type.element, env, where), length)
        }
        is IdlType.Generic -> (env.getValue(type.name) as Bound.Type).type
        is IdlType.Defined -> {
            val def = types.getValue(type.name)
            if (def.generics.isEmpty()) {
                type
            } else {
                val args = def.generics.mapIndexed { i, param -> bound(type.generics[i], param, def.name, env, where) }
                val bindings = def.generics.mapIndexed { i, param -> param.name to args[i] }.toMap()
                val body = def.body
                if (body is IdlTypeBody.Alias) {
                    require(def.name !in inlining) { "$where: generic alias '${def.name}' refers to itself through ${(inlining.subList(inlining.indexOf(def.name), inlining.size) + def.name).joinToString(" -> ")}" }
                    inlining.add(def.name)
                    try {
                        type(body.type, bindings, where)
                    } finally {
                        inlining.removeLast()
                    }
                } else {
                    IdlType.Defined(instance(def, args, bindings, where))
                }
            }
        }
    }

    /** The concrete argument [arg] gives [param] of [owner]; the validator has checked that its kind matches. */
    private fun bound(arg: IdlGenericArg, param: IdlGenericParam, owner: String, env: Map<String, Bound>, where: String): Bound = when (arg) {
        is IdlGenericArg.Type -> Bound.Type(canonical(type(arg.type, env, where), where))
        is IdlGenericArg.ConstParam -> env.getValue(arg.name)
        is IdlGenericArg.Const -> {
            val value = arg.value.toULong()
            val max = CONST_MAX[(param as IdlGenericParam.Const).type]
                ?: throw IllegalArgumentException("$where: const generic parameter '${param.name}' of '$owner' has type '${param.type}', which is not supported")
            require(value <= max) { "$where: const generic argument ${arg.value} is outside the range of '${param.name}: ${param.type}' of '$owner'" }
            Bound.Const(value)
        }
    }

    /** [type] with every non-generic alias at every level resolved, as Rust identifies types. */
    private fun canonical(type: IdlType, where: String): IdlType = when (type) {
        is IdlType.Primitive, is IdlType.Generic -> type
        is IdlType.Option -> IdlType.Option(canonical(type.inner, where))
        is IdlType.Vec -> IdlType.Vec(canonical(type.element, where))
        is IdlType.Array -> IdlType.Array(canonical(type.element, where), type.length)
        is IdlType.Defined -> {
            val body = types[type.name]?.body
            if (body is IdlTypeBody.Alias) {
                require(type.name !in inlining) { "$where: alias '${type.name}' refers to itself" }
                inlining.add(type.name)
                try {
                    canonical(type(body.type, emptyMap(), where), where)
                } finally {
                    inlining.removeLast()
                }
            } else {
                type
            }
        }
    }

    /** The name of the instantiation of [def] with [args], queued for generation when first used. */
    private fun instance(def: IdlTypeDef, args: List<Bound>, env: Map<String, Bound>, where: String): String {
        val name = "${def.name}<${args.joinToString(", ") { render(it) }}>"
        if (name in kotlinNames) return name
        require(name !in types) { "$where: instantiation '$name' has the name of a declared type definition" }
        definitions[name] = def.name
        kotlinNames[name] = baseNames.getValue(def.name) + def.generics.indices.joinToString("") { i ->
            when (val arg = args[i]) {
                is Bound.Type -> fragment(arg.type)
                is Bound.Const -> def.generics[i].name.toPascalCase() + arg.value
            }
        }
        instancesOf.getOrPut(def.name) { ArrayList() }.add(name)
        pending.add(Pending(def, name, env))
        return name
    }

    private fun render(bound: Bound): String = when (bound) {
        is Bound.Const -> bound.value.toString()
        is Bound.Type -> render(bound.type)
    }

    private fun render(type: IdlType): String = when (type) {
        is IdlType.Primitive -> type.name.lowercase()
        is IdlType.Option -> "Option<${render(type.inner)}>"
        is IdlType.Vec -> "Vec<${render(type.element)}>"
        is IdlType.Array -> "[${render(type.element)}; ${(type.length as IdlArrayLength.Fixed).size}]"
        is IdlType.Defined -> type.name
        is IdlType.Generic -> type.name
    }

    /** The part of an instantiation's Kotlin name for type argument [type]. Each part starts with a capital letter. */
    private fun fragment(type: IdlType): String = when (type) {
        is IdlType.Primitive -> type.name.lowercase().toPascalCase()
        is IdlType.Option -> "Option" + fragment(type.inner)
        is IdlType.Vec -> "Vec" + fragment(type.element)
        is IdlType.Array -> "Array" + (type.length as IdlArrayLength.Fixed).size + fragment(type.element)
        is IdlType.Defined -> kotlinNames[type.name] ?: baseNames.getValue(type.name)
        is IdlType.Generic -> throw IllegalStateException("unbound generic ${type.name}")
    }

    // --- finite expansion ---

    /** A type parameter of a generic definition. */
    private data class Param(val def: String, val name: String) {
        override fun toString() = "$def.$name"
    }

    /** [from] flows into [to] through an argument, which is [growing] when it wraps [from] in more than itself. */
    private class Flow(val from: Param, val to: Param, val growing: Boolean, val argument: IdlType)

    /**
     * Reject generic definitions whose instantiation never ends, which happens when a type parameter flows back into
     * itself through an argument that wraps it, such as `struct List<T> { next: Option<List<Vec<T>>> }`. Only checks
     * definitions the program can reach, so one it never uses does not fail generation.
     */
    private fun checkFiniteExpansion() {
        val reachable = reachableTypes()
        val flows = HashMap<Param, MutableList<Flow>>()
        for (def in source.types) {
            if (def.generics.isEmpty() || def.name !in reachable) continue
            forEachType(def.body) { type ->
                forEachDefined(type) { defined ->
                    val target = types.getValue(defined.name)
                    defined.generics.forEachIndexed { i, arg ->
                        if (arg !is IdlGenericArg.Type) return@forEachIndexed
                        forEachGeneric(arg.type) { name ->
                            val from = Param(def.name, name)
                            flows.getOrPut(from) { ArrayList() }.add(Flow(from, Param(target.name, target.generics[i].name), arg.type != IdlType.Generic(name), arg.type))
                        }
                    }
                }
            }
        }
        for (flow in flows.values.flatten()) {
            if (!flow.growing) continue
            val path = path(flow.to, flow.from, flows) ?: continue
            throw IllegalArgumentException(
                "type '${flow.from.def}': instantiating it never ends, as generic parameter ${flow.from} flows back into itself " +
                    "through ${(listOf(flow.from) + path).joinToString(" -> ")}, growing at '${render(flow.argument)}'",
            )
        }
    }

    /** The parameters from [from] to [to] through [flows], or null when [to] cannot be reached. */
    private fun path(from: Param, to: Param, flows: Map<Param, List<Flow>>): List<Param>? {
        val previous = HashMap<Param, Param?>()
        previous[from] = null
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == to) return generateSequence(current) { previous[it] }.toList().asReversed()
            for (flow in flows[current].orEmpty()) {
                if (flow.to !in previous) {
                    previous[flow.to] = current
                    queue.add(flow.to)
                }
            }
        }
        return null
    }

    /** Type definitions the program's concrete types, instructions and constants refer to, directly or not. */
    private fun reachableTypes(): Set<String> {
        val seen = HashSet<String>()
        val queue = ArrayDeque<IdlType>()
        fun visit(type: IdlType) = forEachDefined(type) { if (seen.add(it.name)) forEachType(types.getValue(it.name).body, queue::add) }
        for (def in source.types) if (def.generics.isEmpty()) forEachType(def.body, queue::add)
        for (ix in source.instructions) {
            ix.args.forEach { queue.add(it.type) }
            ix.returns?.let(queue::add)
        }
        source.constants.forEach { queue.add(it.type) }
        while (queue.isNotEmpty()) visit(queue.removeFirst())
        return seen
    }

    private fun forEachType(body: IdlTypeBody, action: (IdlType) -> Unit) {
        fun fields(fields: IdlFields) = when (fields) {
            IdlFields.None -> Unit
            is IdlFields.Named -> fields.fields.forEach { action(it.type) }
            is IdlFields.Tuple -> fields.types.forEach(action)
        }
        when (body) {
            is IdlTypeBody.Struct -> fields(body.fields)
            is IdlTypeBody.Enum -> body.variants.forEach { fields(it.fields) }
            is IdlTypeBody.Alias -> action(body.type)
        }
    }

    /** Call [action] with each defined type in [type], including those within generic arguments. */
    private fun forEachDefined(type: IdlType, action: (IdlType.Defined) -> Unit) {
        when (type) {
            is IdlType.Primitive, is IdlType.Generic -> Unit
            is IdlType.Option -> forEachDefined(type.inner, action)
            is IdlType.Vec -> forEachDefined(type.element, action)
            is IdlType.Array -> forEachDefined(type.element, action)
            is IdlType.Defined -> {
                action(type)
                type.generics.forEach { if (it is IdlGenericArg.Type) forEachDefined(it.type, action) }
            }
        }
    }

    /** Call [action] with the name of each type parameter [type] refers to. */
    private fun forEachGeneric(type: IdlType, action: (String) -> Unit) {
        when (type) {
            is IdlType.Primitive -> Unit
            is IdlType.Generic -> action(type.name)
            is IdlType.Option -> forEachGeneric(type.inner, action)
            is IdlType.Vec -> forEachGeneric(type.element, action)
            is IdlType.Array -> forEachGeneric(type.element, action)
            is IdlType.Defined -> type.generics.forEach { if (it is IdlGenericArg.Type) forEachGeneric(it.type, action) }
        }
    }

    private companion object {
        /** The largest value of each supported const generic parameter type; `usize` is 64-bit on Solana. */
        val CONST_MAX: Map<String, ULong> = mapOf(
            "u8" to UByte.MAX_VALUE.toULong(),
            "u16" to UShort.MAX_VALUE.toULong(),
            "u32" to UInt.MAX_VALUE.toULong(),
            "u64" to ULong.MAX_VALUE,
            "usize" to ULong.MAX_VALUE,
            "i8" to Byte.MAX_VALUE.toULong(),
            "i16" to Short.MAX_VALUE.toULong(),
            "i32" to Int.MAX_VALUE.toULong(),
            "i64" to Long.MAX_VALUE.toULong(),
            "isize" to Long.MAX_VALUE.toULong(),
        )
    }
}
