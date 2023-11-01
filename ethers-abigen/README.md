# ethers-abigen

Module containing code for generating contract wrappers from JSON-ABI. It builds upon the `ethers-abi` module as it uses
its types for the generated code. In most cases, this module should not be used directly, but rather through the Gradle
plugin from the `ethers-abigen-plugin` module.

There are two main parts to the module:

- `JsonAbiReader`: implements a parser for different formats of JSON-ABI files. By default, it
  supports `Foundry`, `Hardhat`, and `Etherscan` formats. The `JsonAbiReaderRegistry` should be used to manage the
  readers and to read the actual ABI's. New formats can be added by implementing the `JsonAbiReader` interface and
  adding it to the registry.
- `AbiContractBuilder`: contains the main ABI-gen logic, which takes the parsed ABI and generates the contract wrapper
  code.

## Supported types

- Receive function
- Fallback function (payable)
- Functions (payable/view/pure + overloads)
- Constructors (payable)
- Events (anonymous + overloads)
- Structs (+ overloads)
- Custom errors

All `Events` and `Errors` in a contract are grouped into a sealed class hierarchy, which allows to easily handle all
events/errors of a particular contract. The generated code also contains a static `filter` function on each event type,
which allows to fetch events without creating a contract instance and supports collecting all events from multiple
contracts.

For each function there also exists an `AbiFunction` static constant, which can be used to manually encode/decode the
function calls. A similar constant exists for constructors. All other types have static ABI definitions defined on
its `Factory` companion objects.

For functions and constructors, depending on its abi modifiers, a different subclass of `ContractCall` is returned. E.g.
for `payable` functions a `PayableFunctionCall` is returned. Different subclasses expose different parameters which can
be changed by the consumer.

## 💻 Code Examples

- Generate contract wrappers from JSON-ABI file:
  ```kotlin
  val file = File("src/main/abi/TestContract.json")
  val abi = JsonAbiReaderRegistry.readAbi(file.toURI().toURL()) ?: throw Exception("Failed to read ABI")
  
  AbiContractBuilder(
      contractName = file.nameWithoutExtension,
      packageName = "io.ethers.abigen.test",
      destination = File("ethers-abigen/src/main/kotlin/"),
      artifact = abi,
      functionRenames = emptyMap(),
  ).run()
  ```


