package io.ethers.solana.idl

/**
 * Checks the references between the elements of an [IdlProgram], which each element's own
 * constructor cannot: duplicate names or error codes, overlapping discriminators, unresolved or
 * mis-applied types, alias cycles, accounts and events without a layout, and instruction account
 * references that do not resolve.
 *
 * The layout itself is not checked for support: non-Borsh serialization is valid schema.
 */
object IdlValidator {
    /**
     * Validate [program].
     *
     * @throws IllegalArgumentException describing the first problem found, naming the element it is in.
     */
    @JvmStatic
    fun validate(program: IdlProgram) = Validation(program).run()
}

private class Validation(private val program: IdlProgram) {
    private val types = program.types.associateBy { it.name }

    fun run() {
        unique(program.instructions, "instruction") { it.name }
        unique(program.accounts, "account") { it.name }
        unique(program.events, "event") { it.name }
        unique(program.types, "type") { it.name }
        unique(program.errors, "error") { it.name }
        unique(program.constants, "constant") { it.name }
        errorCodes()

        discriminators(program.instructions, "instruction", { it.name }, { it.discriminator })
        discriminators(program.accounts, "account", { it.name }, { it.discriminator })
        discriminators(program.events, "event", { it.name }, { it.discriminator })
        for (account in program.accounts) require(account.name in types) { "account '${account.name}': no type definition with its name" }
        for (event in program.events) require(event.name in types) { "event '${event.name}': no type definition with its name" }

        program.instructions.forEach(::instruction)
        program.types.forEach(::typeDef)
        program.constants.forEach { type(it.type, "constant '${it.name}'", emptyList()) }
        aliasCycles()
    }

    private inline fun <T> unique(items: List<T>, kind: String, name: (T) -> String) {
        val seen = HashSet<String>()
        for (item in items) require(seen.add(name(item))) { "duplicate $kind '${name(item)}'" }
    }

    private fun errorCodes() {
        val seen = HashSet<Long>()
        for (error in program.errors) require(seen.add(error.code)) { "error '${error.name}': duplicate code ${error.code}" }
    }

    private inline fun <T> discriminators(
        items: List<T>,
        kind: String,
        name: (T) -> String,
        discriminator: (T) -> IdlDiscriminator,
    ) {
        items.forEachIndexed { i, item ->
            for (j in 0 until i) {
                require(!discriminator(items[j]).overlaps(discriminator(item))) {
                    "$kind '${name(item)}': discriminator ${discriminator(item)} cannot be told apart from $kind '${name(items[j])}' ${discriminator(items[j])}"
                }
            }
        }
    }

    private fun instruction(instruction: IdlInstruction) {
        val where = "instruction '${instruction.name}'"
        val accountNames = HashSet<String>()
        fun collect(items: List<IdlAccountItem>) {
            for (item in items) {
                accountNames.add(item.name)
                if (item is IdlAccountItem.Group) collect(item.accounts)
            }
        }
        collect(instruction.accounts)
        val argNames = instruction.args.mapTo(HashSet()) { it.name }

        accountItems(instruction.accounts, where, accountNames, argNames)
        unique(instruction.args, "$where argument") { it.name }
        instruction.args.forEach { type(it.type, "$where argument '${it.name}'", emptyList()) }
        instruction.returns?.let { type(it, "$where return type", emptyList()) }
    }

    private fun accountItems(items: List<IdlAccountItem>, where: String, accounts: Set<String>, args: Set<String>) {
        unique(items, "$where account") { it.name }
        for (item in items) {
            val itemWhere = "$where account '${item.name}'"
            when (item) {
                is IdlAccountItem.Group -> accountItems(item.accounts, itemWhere, accounts, args)
                is IdlAccountItem.Single -> {
                    for (relation in item.relations) require(relation in accounts) { "$itemWhere: relation to unknown account '$relation'" }
                    item.pda?.let { pda ->
                        (pda.seeds + listOfNotNull(pda.program)).forEach { seed(it, "$itemWhere PDA seed", args) }
                    }
                }
            }
        }
    }

    private fun seed(seed: IdlSeed, where: String, args: Set<String>) {
        when (seed) {
            is IdlSeed.Arg -> {
                val arg = seed.path.substringBefore('.')
                require(arg in args) { "$where: no argument named '$arg'" }
            }
            // account paths may be nested in account groups or reach into account data, which later
            // resolution checks against the account's type
            is IdlSeed.Account, is IdlSeed.Const -> Unit
        }
    }

    private fun typeDef(def: IdlTypeDef) {
        val where = "type '${def.name}'"
        unique(def.generics, "$where generic parameter") { it.name }
        val scope = def.generics
        when (val body = def.body) {
            is IdlTypeBody.Struct -> fields(body.fields, where, scope)
            is IdlTypeBody.Enum -> {
                unique(body.variants, "$where variant") { it.name }
                body.variants.forEach { fields(it.fields, "$where variant '${it.name}'", scope) }
            }
            is IdlTypeBody.Alias -> type(body.type, where, scope)
        }
    }

    private fun fields(fields: IdlFields, where: String, scope: List<IdlGenericParam>) {
        when (fields) {
            IdlFields.None -> Unit
            is IdlFields.Named -> {
                unique(fields.fields, "$where field") { it.name }
                fields.fields.forEach { type(it.type, "$where field '${it.name}'", scope) }
            }
            is IdlFields.Tuple -> fields.types.forEachIndexed { i, type -> type(type, "$where field $i", scope) }
        }
    }

    /** Check that [type] resolves, with generic references bound by [scope]. */
    private fun type(type: IdlType, where: String, scope: List<IdlGenericParam>) {
        when (type) {
            is IdlType.Primitive -> Unit
            is IdlType.Option -> type(type.inner, where, scope)
            is IdlType.Vec -> type(type.element, where, scope)
            is IdlType.Array -> {
                type(type.element, where, scope)
                when (val length = type.length) {
                    is IdlArrayLength.Fixed -> Unit
                    is IdlArrayLength.Generic -> require(scope.any { it is IdlGenericParam.Const && it.name == length.name }) {
                        "$where: no const generic parameter named '${length.name}' in scope"
                    }
                }
            }
            is IdlType.Generic -> require(scope.any { it is IdlGenericParam.Type && it.name == type.name }) {
                "$where: no type generic parameter named '${type.name}' in scope"
            }
            is IdlType.Defined -> defined(type, where, scope)
        }
    }

    private fun defined(type: IdlType.Defined, where: String, scope: List<IdlGenericParam>) {
        val def = requireNotNull(types[type.name]) { "$where: no type definition named '${type.name}'" }
        require(type.generics.size == def.generics.size) {
            "$where: '${type.name}' takes ${def.generics.size} generic arguments, got ${type.generics.size}"
        }
        type.generics.forEachIndexed { i, arg ->
            when (val param = def.generics[i]) {
                is IdlGenericParam.Type -> {
                    require(arg is IdlGenericArg.Type) { "$where: generic parameter '${param.name}' of '${type.name}' takes a type" }
                    type(arg.type, where, scope)
                }
                is IdlGenericParam.Const -> when (arg) {
                    is IdlGenericArg.Const -> Unit
                    is IdlGenericArg.ConstParam -> require(scope.any { it is IdlGenericParam.Const && it.name == arg.name }) {
                        "$where: no const generic parameter named '${arg.name}' in scope"
                    }
                    is IdlGenericArg.Type -> throw IllegalArgumentException("$where: generic parameter '${param.name}' of '${type.name}' takes a const value")
                }
            }
        }
    }

    /** Reject any chain of aliases that leads back to itself. */
    private fun aliasCycles() {
        for (def in program.types) {
            val chain = ArrayList<String>()
            var current: IdlTypeDef? = def
            while (current != null && current.body is IdlTypeBody.Alias) {
                require(current.name !in chain) {
                    val cycle = chain.subList(chain.indexOf(current.name), chain.size) + current.name
                    "type '${current.name}': alias cycle ${cycle.joinToString(" -> ")}"
                }
                chain.add(current.name)
                current = ((current.body as IdlTypeBody.Alias).type as? IdlType.Defined)?.let { types[it.name] }
            }
        }
    }
}
