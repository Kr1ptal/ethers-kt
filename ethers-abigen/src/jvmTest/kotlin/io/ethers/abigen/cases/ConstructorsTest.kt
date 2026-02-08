package io.ethers.abigen.cases

import io.ethers.abi.ContractStruct
import io.ethers.abi.StructFactory
import io.ethers.abi.call.ConstructorCall
import io.ethers.abi.call.PayableConstructorCall
import io.ethers.abigen.AbigenCompiler
import io.ethers.abigen.ArgDescriptor
import io.ethers.abigen.getDeclaredFunctions
import io.ethers.abigen.nestedClass
import io.ethers.abigen.parametrizedBy
import io.ethers.providers.middleware.Middleware
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.isSubclassOf

class ConstructorsTest : FunSpec({
    context("abigen validation: Constructors") {
        val noExplicitConstructor = AbigenCompiler.getContract(
            "ConstructorNoExplicit",
        )
        val explicitNoArgsConstructor = AbigenCompiler.getContract(
            "ConstructorExplicitEmpty",
        )
        val payableConstructorWithArgs = AbigenCompiler.getContract(
            "ConstructorArgumentsPayable",
        )
        val complexArgsConstructor = AbigenCompiler.getContract(
            "ConstructorComplexArguments",
        )

        context("constructors") {
            test("no explicit constructor with bytecode generates deploy function") {
                val deploy = noExplicitConstructor.companionObject!!
                    .getDeclaredFunctions()
                    .single { it.name == "deploy" }

                deploy.arguments shouldBe listOf(ArgDescriptor("provider", Middleware::class))
                deploy.returnType shouldBe ConstructorCall::class.parametrizedBy(noExplicitConstructor)
            }

            test("explicit constructor with no arguments") {
                val deploy = explicitNoArgsConstructor.companionObject!!
                    .getDeclaredFunctions()
                    .single { it.name == "deploy" }

                deploy.arguments shouldBe listOf(ArgDescriptor("provider", Middleware::class))
                deploy.returnType shouldBe ConstructorCall::class.parametrizedBy(explicitNoArgsConstructor)
            }

            test("explicit constructor with payable arguments") {
                val deploy = payableConstructorWithArgs.companionObject!!
                    .getDeclaredFunctions()
                    .single { it.name == "deploy" }

                deploy.arguments shouldBe listOf(
                    ArgDescriptor("provider", Middleware::class),
                    ArgDescriptor("value", BigInteger::class),
                    ArgDescriptor("description", String::class),
                )
                deploy.returnType shouldBe PayableConstructorCall::class.parametrizedBy(payableConstructorWithArgs)
            }

            test("explicit constructor with complex arguments") {
                val deploy = complexArgsConstructor.companionObject!!
                    .getDeclaredFunctions()
                    .single { it.name == "deploy" }

                val detailsStruct = complexArgsConstructor.nestedClass("Details")
                deploy.arguments shouldBe listOf(
                    ArgDescriptor("provider", Middleware::class),
                    ArgDescriptor("number", BigInteger::class),
                    ArgDescriptor("details", detailsStruct),
                )

                detailsStruct.isSubclassOf(ContractStruct::class) shouldBe true
                detailsStruct.companionObject?.isSubclassOf(StructFactory::class) shouldBe true
                deploy.returnType shouldBe ConstructorCall::class.parametrizedBy(complexArgsConstructor)
            }
        }
    }
})
