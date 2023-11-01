package io.ethers.abigen

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonAbi(
    private val abi: List<JsonAbiItem>,
    val bytecode: String? = null,
) {
    val functions: List<JsonAbiFunction>
    val events: List<JsonAbiEvent>
    val errors: List<JsonAbiError>
    val constructor: JsonAbiConstructor?
    val receive: JsonAbiReceive?
    val fallback: JsonAbiFallback?

    init {
        val functions = ArrayList<JsonAbiFunction>()
        val events = ArrayList<JsonAbiEvent>()
        val errors = ArrayList<JsonAbiError>()
        var constructor: JsonAbiConstructor? = null
        var receive: JsonAbiReceive? = null
        var fallback: JsonAbiFallback? = null

        abi.forEach {
            when (it.type) {
                JsonAbiItem.Type.FUNCTION -> functions.add(
                    JsonAbiFunction(
                        name = it.name!!,
                        inputs = it.inputs!!,
                        outputs = it.outputs!!,
                        isPayable = it.isPayable,
                        isReadOnly = it.isReadOnly,
                    ),
                )

                JsonAbiItem.Type.EVENT -> events.add(
                    JsonAbiEvent(
                        name = it.name!!,
                        inputs = it.inputs!!,
                        anonymous = it.anonymous!!,
                    ),
                )

                JsonAbiItem.Type.ERROR -> errors.add(
                    JsonAbiError(
                        name = it.name!!,
                        inputs = it.inputs!!,
                    ),
                )

                JsonAbiItem.Type.CONSTRUCTOR -> constructor = JsonAbiConstructor(
                    inputs = it.inputs!!,
                    isPayable = it.isPayable,
                )

                JsonAbiItem.Type.RECEIVE -> receive = JsonAbiReceive
                JsonAbiItem.Type.FALLBACK -> fallback = JsonAbiFallback(it.isPayable)
            }
        }

        this.functions = functions
        this.events = events
        this.errors = errors
        this.constructor = constructor
        this.receive = receive
        this.fallback = fallback
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonAbiItem(
    val type: Type,
    val name: String?,
    val inputs: List<Component>? = null,
    val outputs: List<Component>? = null,
    val anonymous: Boolean? = null,

    private val stateMutability: Mutability = Mutability.NONPAYABLE,
    private val constant: Boolean = false, // legacy field since v0.5.0 - true if function is view or pure
    private val payable: Boolean = false, // legacy field since v0.5.0 - true if function is payable
) {
    val isPayable: Boolean
        get() = stateMutability == Mutability.PAYABLE || payable

    val isReadOnly: Boolean
        get() = stateMutability == Mutability.PURE || stateMutability == Mutability.VIEW || constant

    enum class Type {
        FUNCTION,
        CONSTRUCTOR,
        RECEIVE,
        FALLBACK,
        EVENT,
        ERROR,
    }

    enum class Mutability {
        PURE,
        VIEW,
        NONPAYABLE,
        PAYABLE,
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Component(
        val type: String,
        val internalType: String?,
        val name: String = "",
        val indexed: Boolean = false,
        val components: List<Component> = emptyList(),
    )
}

data class JsonAbiFunction(
    val name: String,
    val inputs: List<JsonAbiItem.Component>,
    val outputs: List<JsonAbiItem.Component>,
    val isPayable: Boolean,
    val isReadOnly: Boolean,
)

data class JsonAbiEvent(
    val name: String,
    val inputs: List<JsonAbiItem.Component>,
    val anonymous: Boolean,
)

data class JsonAbiError(
    val name: String,
    val inputs: List<JsonAbiItem.Component>,
)

data class JsonAbiConstructor(
    val inputs: List<JsonAbiItem.Component>,
    val isPayable: Boolean,
)

data object JsonAbiReceive

data class JsonAbiFallback(val isPayable: Boolean)
