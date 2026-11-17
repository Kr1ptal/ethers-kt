package io.ethers.solana.idl.generator

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
private class ProgramOverridesJson(
    val customCodecs: Map<String, CustomCodecJson> = emptyMap(),
    val typeSubstitutions: Map<String, TypeSubstitutionJson> = emptyMap(),
    val renames: Map<String, String> = emptyMap(),
    val accountDefaults: Map<String, String> = emptyMap(),
)

@Serializable
private class CustomCodecJson(val codec: String, val size: Int? = null, val align: Int? = null)

@Serializable
private class TypeSubstitutionJson(val type: String, val codec: String, val size: Int? = null, val align: Int? = null)

/** Parse [json], see [GeneratorOverrides.parse]. */
internal fun parseGeneratorOverrides(json: String): Map<String, GeneratorOverrides> {
    val programs = try {
        // the default configuration rejects unknown keys, so a misspelled option fails rather than being ignored
        Json.decodeFromString<Map<String, ProgramOverridesJson>>(json)
    } catch (e: SerializationException) {
        throw IllegalArgumentException("Invalid generator overrides: ${e.message}", e)
    }
    return programs.mapValues { (program, it) ->
        fun <T> entry(kind: String, target: String, create: () -> T): T = try {
            create()
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid generator overrides for program '$program', $kind '$target': ${e.message}", e)
        }
        GeneratorOverrides(
            customCodecs = it.customCodecs.mapValues { (type, codec) ->
                entry("custom codec", type) { GeneratorOverrides.CustomCodec(codec.codec, codec.size, codec.align) }
            },
            typeSubstitutions = it.typeSubstitutions.mapValues { (type, sub) ->
                entry("type substitution", type) { GeneratorOverrides.TypeSubstitution(sub.type, sub.codec, sub.size, sub.align) }
            },
            renames = it.renames,
            accountDefaults = it.accountDefaults,
        )
    }.toSortedMap()
}
