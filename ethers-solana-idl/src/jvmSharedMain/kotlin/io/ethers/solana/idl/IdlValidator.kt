package io.ethers.solana.idl

/**
 * Checks the references between the elements of an [IdlProgram], which each element's own
 * constructor cannot: duplicate names or error codes, overlapping discriminators, unresolved or
 * mis-applied types, alias cycles, accounts and events without a layout, instruction account
 * references that do not resolve, and PDA seeds that do not resolve or cannot fit a derivation.
 * Field names of a struct may repeat, but a seed path through a repeated name does not resolve.
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
    private companion object {
        /** Seeds a derivation takes, besides the bump. */
        const val MAX_SEEDS = 15
        const val MAX_SEED_SIZE = 32
    }

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

        program.types.forEach(::typeDef)
        aliasCycles()
        program.instructions.forEach(::instruction)
        program.constants.forEach { type(it.type, "constant '${it.name}'", emptyList()) }
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

        unique(instruction.args, "$where argument") { it.name }
        instruction.args.forEach { type(it.type, "$where argument '${it.name}'", emptyList()) }
        instruction.returns?.let { type(it, "$where return type", emptyList()) }
        accountItems(instruction.accounts, where, accountNames, instruction)
    }

    private fun accountItems(items: List<IdlAccountItem>, where: String, accounts: Set<String>, instruction: IdlInstruction) {
        unique(items, "$where account") { it.name }
        for (item in items) {
            val itemWhere = "$where account '${item.name}'"
            when (item) {
                is IdlAccountItem.Group -> accountItems(item.accounts, itemWhere, accounts, instruction)
                is IdlAccountItem.Single -> {
                    for (relation in item.relations) require(relation in accounts) { "$itemWhere: relation to unknown account '$relation'" }
                    item.pda?.let { pda(it, itemWhere, listOf(items, instruction.accounts).distinct(), instruction.args) }
                }
            }
        }
    }

    /** Check that [pda]'s seeds resolve among the accounts in [scopes] and [args], and fit a derivation. */
    private fun pda(pda: IdlPda, where: String, scopes: List<List<IdlAccountItem>>, args: List<IdlField>) {
        require(pda.seeds.size <= MAX_SEEDS) { "$where: ${pda.seeds.size} PDA seeds, but at most $MAX_SEEDS fit beside the bump" }
        pda.seeds.forEachIndexed { i, seed -> seed(seed, "$where PDA seed $i", scopes, args) }
        pda.program?.let { program ->
            if (program is IdlSeed.Const) {
                require(program.bytes.size == 32) { "$where PDA program: a program id has 32 bytes, got ${program.bytes.size}" }
            } else {
                seed(program, "$where PDA program", scopes, args)
            }
        }
    }

    private fun seed(seed: IdlSeed, where: String, scopes: List<List<IdlAccountItem>>, args: List<IdlField>) {
        when (seed) {
            is IdlSeed.Const -> require(seed.bytes.size <= MAX_SEED_SIZE) { "$where: ${seed.bytes.size} bytes, but a seed has at most $MAX_SEED_SIZE" }
            is IdlSeed.Arg -> {
                val path = seed.path.split('.')
                val arg = requireNotNull(args.firstOrNull { it.name == path[0] }) { "$where: no argument named '${path[0]}'" }
                require(IdlPaths.field(arg.type, path.drop(1), types) != null) {
                    "$where: argument path '${seed.path}' does not resolve to a single field"
                }
            }
            is IdlSeed.Account -> {
                val path = requireNotNull(IdlPaths.account(scopes, seed.path)) { "$where: no account named by '${seed.path}'" }
                // the account type may be declared by another program, in which case its fields cannot be checked
                val type = seed.account?.takeIf { it in types }
                if (path.fields.isNotEmpty() && type != null) {
                    require(IdlPaths.field(IdlType.Defined(type), path.fields, types) != null) {
                        "$where: account path '${seed.path}' does not resolve to a single field of '$type'"
                    }
                }
            }
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
            // a struct's field names may repeat, as legacy IDLs merge `_padding` and `padding`; paths through
            // a repeated name are rejected where they are resolved
            is IdlFields.Named -> fields.fields.forEach { type(it.type, "$where field '${it.name}'", scope) }
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
                current = (current.body.type as? IdlType.Defined)?.let { types[it.name] }
            }
        }
    }
}
