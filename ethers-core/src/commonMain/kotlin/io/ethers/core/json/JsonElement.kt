package io.ethers.core.json

/**
 * Library-agnostic representation of a raw JSON value, stored as a [String].
 */
data class JsonElement(private val json: String) {
    /**
     * Whether this element is a JSON object (e.g. `{"key": "value"}`).
     */
    val isObject: Boolean
        get() = json.startsWith("{") && json.endsWith("}")

    /**
     * Whether this element is a JSON array (e.g. `[1, 2, 3]`).
     */
    val isArray: Boolean
        get() = json.startsWith("[") && json.endsWith("]")

    /**
     * Whether this element is a JSON string (e.g. `"hello"`).
     */
    val isString: Boolean
        get() = json.startsWith("\"") && json.endsWith("\"")

    override fun toString(): String = json
}
