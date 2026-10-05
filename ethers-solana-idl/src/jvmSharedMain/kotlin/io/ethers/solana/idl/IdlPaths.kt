package io.ethers.solana.idl

/**
 * Resolves the dotted paths of PDA seeds: the instruction account an account seed names, and the field a path
 * reaches within an argument or an account's data.
 */
internal object IdlPaths {
    /** The instruction account at [role] that an account seed names, with the rest of its path in [fields]. */
    class AccountPath(val role: List<String>, val account: IdlAccountItem.Single, val fields: List<String>)

    /**
     * The account [path] names, descending into account groups, in the first of [scopes] that contains it. Null
     * when none does, or the path ends at a group.
     */
    fun account(scopes: List<List<IdlAccountItem>>, path: String): AccountPath? {
        val segments = path.split('.')
        for (scope in scopes) {
            var items = scope
            for (i in segments.indices) {
                when (val item = items.firstOrNull { it.name == segments[i] }) {
                    null -> break
                    is IdlAccountItem.Group -> items = item.accounts
                    is IdlAccountItem.Single -> return AccountPath(segments.subList(0, i + 1), item, segments.subList(i + 1, segments.size))
                }
            }
        }
        return null
    }

    /**
     * The type [fields] reach within [type], following aliases and named struct fields, or null when one does not
     * resolve, or names more than one field. With no [fields], [type] itself.
     */
    fun field(type: IdlType, fields: List<String>, types: Map<String, IdlTypeDef>): IdlType? {
        var current = type
        for (name in fields) {
            val struct = resolve(current, types) as? IdlType.Defined ?: return null
            val body = types[struct.name]?.body as? IdlTypeBody.Struct ?: return null
            current = (body.fields as? IdlFields.Named)?.fields?.singleOrNull { it.name == name }?.type ?: return null
        }
        return current
    }

    /** [type] with aliases resolved, stopping at an unknown type or after as many steps as there are [types]. */
    fun resolve(type: IdlType, types: Map<String, IdlTypeDef>): IdlType {
        var current = type
        repeat(types.size) {
            val defined = current as? IdlType.Defined ?: return current
            current = (types[defined.name]?.body as? IdlTypeBody.Alias)?.type ?: return current
        }
        return current
    }
}
