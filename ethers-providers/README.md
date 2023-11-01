# ethers-providers

Contains abstractions and implementations for connecting to the EVM blockchain network. A `Provider` wraps a generic
data transport through which all JSON-RPC API calls are routed, e.g. HTTP or WebSocket. It also implements
the `Middleware` interface, where it acts as the bottom-most middleware in the stack. `Middleware` provides a way to
customize the functionality of supported RPC calls. For example, you can write your own middleware to change the gas
oracle used for returning current optimal gas price.

All JSON-RPC API requests are **asynchronous**, and they return a `CompletableFuture`. There is also support for
synchronous calls using functions ending with `await` suffix, which simply await the returned future until it completes.

Each `Provider` RPC function returns a `RpcRequest`, which can then be either sent individually or added to a batch:

```kotlin
val provider = Provider(HttpClient(rpcUrl))

// creates the RpcRequest class, but does not send it yet    
val request = provider.getBlockNumber()

// send individually, awaiting and unwrapping the result
val blockNum = request.sendAwait().resultOrThrow()

// send multiple requests in a batch

// 1. manually
val batch = BatchRpcRequest()
val future1 = request.batch(batch)
val future2 = provider.txpoolStatus().batch(batch)
batch.sendAwait()

// 2. simplified
val (blockNum2, txpoolStatus) = batchRequest(
    provider.getBlockNumber(),
    provider.txpoolStatus()
).await().resultOrThrow()
```

Once the request is sent, the result is returned via `RpcResponse` class, which wraps either the result of the call, or
any errors that happened while processing it. This means that an RPC request never throws an exception, and leaves it
up to the consumer to decide how to handle errors. All errors implement the `RpcRequest.Error` type, and each service
can implement its own subclasses, containing custom data specific to the failure. This allows you to have fine-grained
control over how your application reacts to errors:

```kotlin
val result = provider.getBlockNumber().sendAwait()

if (result.isError) {
    val rpcError = result.error!!.asTypeOrNull<RpcResponse.RpcError>()

    // method not found error code
    if (rpcError != null && rpcError.code == -32601) {
        println(rpcError.message)
    }

    val clientError = result.error.asTypeOrNull<RpcClientError>()
    when (clientError) {
        is CallFailedError -> {}
        CallTimeoutError -> {}
        null -> {}
    }
}
```

## PubSub Functionality

The `PubSubClient` interface provides a way to subscribe to events on the blockchain. If the underlying transport
of the `Provider` does not implement the `PubSubClient` interface, all subscription calls will fail with an exception.
All `PubSubClient` implementations automatically reconnect on connection drop and resubscribe to all active
subscriptions transparently.

The returned `SubscriptionStream` is blocking by default. This means that **iteration will block the calling thread**.
In case you want to process the stream asynchronously, you can use the `forEachAsync` function, which will spawn a new
thread in the background. You can pass a custom thread factory to this method, for example to spawn a virtual thread.

In case the underlying transport does implement the `PubSubClient` interface, there are also `watch`-prefixed functions
which mimic the behavior of subscriptions, but instead create a filter on the server, which is polled intermittently for
new values.

### 💻 Code Examples

- Request different block data in sync and async mode via `HttpClient`.

    ```kotlin
    val rpcUrl = "<url>"
    val httpClient = HttpClient(rpcUrl)
    val provider = Provider(httpClient)
    
    val blockNumber = 18433374L
    
    // Sync
    val blockHeader = provider.getBlockHeader(blockNumber).sendAwait().resultOrThrow()
    val blockWithTransactions = provider.getBlockWithTransactions(blockNumber).sendAwait().resultOrThrow()
    
    // Async
    val blockHeaderFuture = provider.getBlockHeader(blockNumber).sendAsync()
    val blockWithTransactionsFuture = provider.getBlockWithTransactions(blockNumber).sendAsync()
    ```

- Execute batch request via `HttpClient`.

    ```kotlin
    val rpcUrl = "<url>"
    val httpClient = HttpClient(rpcUrl)
    val provider = Provider(httpClient)
    
    val (r1, r2, r3) = batchRequest(
        provider.getBlockNumber(),
        provider.getTransactionByHash(Hash("0x8d6f9d0d94b84d6be19b70ac812ff291eceece6ad7ba390599a654e4c52603b4")),
        provider.getLogs(LogFilter {
            blockRange(18433370, 18433375) // From 18433370 inclusive to 18433375 exclusive
            topic0(
                Hash("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"), // ERC20 transfer topic
                Hash("0xd78ad95fa46c994b6551d0da85fc275fe613ce37657fb8d5e3d130840159d822"), // Uniswap V2 swap topic
            )
        })
    ).await()

    val blockNumber = r1.resultOrThrow()
    val transaction = r2.resultOrThrow()
    val logs = r3.resultOrThrow()
    ```

- Subscribe to new pending transactions via `subscribe` or `watch`

  ```kotlin
  val wsUrl = "<url>"
  val wsClient = WsClient(wsUrl)
  val provider = Provider(wsClient)
  
  val stream: SubscriptionStream<RPCTransaction>
  if (provider.isPubSub) {
      stream = provider.subscribeNewPendingTransactions().sendAwait().resultOrThrow()
  } else {
      stream = provider.watchNewPendingTransactions().sendAwait().resultOrThrow().withInterval(Duration.ofSeconds(1))
  }

  stream.filter { it.gas > 21000L }.forEach { println("New pending TX: $it") }
  ```

More example can be found in [tests](src/test/kotlin/io/ethers/providers).
