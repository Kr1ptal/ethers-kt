# ethers-providers

Contains abstractions and implementations for connecting to the EVM blockchain network. A `Provider` wraps a generic
data transport through which all JSON-RPC API calls are routed, e.g. HTTP or WebSocket. It also implements
the `Middleware` interface, where it acts as the bottom-most middleware in the stack. `Middleware` provides a way to
customize the functionality of supported RPC calls. For example, you can write your own middleware to change the gas
oracle used for returning current optimal gas price.

All JSON-RPC API requests are **asynchronous** and coroutine-based: sending a request `suspend`s rather than blocking
the calling thread. On JVM and Android, every terminal also has a blocking (`await` suffix) and a
`CompletableFuture` (`async` suffix) variant, so Java callers need no wrapping. Those variants are inherited members
and exist only on those two platforms - from common code, use the suspending form.

A `Provider` is created through its builder, which picks the transport from the URL protocol (`http`/`https` gives
an `HttpClient`, `ws`/`wss` a `WsClient`):

```kotlin
// resolves the chain id via an "eth_chainId" call
val provider = Provider.builder(rpcUrl).build().unwrap()

// or, with a known chain id - makes no RPC call at all, so it neither suspends nor blocks
val provider = Provider.builder(rpcUrl).build(chainId = 1L).unwrap()
```

Each `Provider` RPC function returns an `RpcRequest`, which can then be either sent individually or added to a batch:

```kotlin
// creates the RpcRequest, but does not send it yet
val request = provider.getBlockNumber()

// send individually, awaiting and unwrapping the result
val blockNum = request.send().unwrap()

// send multiple requests in a batch

// 1. manually
val batch = BatchRpcRequest()
val response1 = request.batch(batch)
val response2 = provider.txpoolStatus().batch(batch)
batch.send()
val blockNum2 = response1.await().unwrap()

// 2. simplified - the batch is dispatched by "batchRequest" itself
val (blockNum3, txpoolStatus) = batchRequest(
    provider.getBlockNumber(),
    provider.txpoolStatus(),
).awaitSuspend().unwrap()
```

Once the request is sent, the result is returned as a `Result`, which holds either the value or the error that
occurred while processing the call. This means that an RPC request never throws an exception, and leaves it
up to the consumer to decide how to handle errors. Errors implement the `ThrowableError` type, and each service
can implement its own subclasses, containing custom data specific to the failure. This allows you to have
fine-grained control over how your application reacts to errors:

```kotlin
provider.getBlockNumber().send().onFailure { err ->
    // "RpcError" exposes the JSON-RPC error code, plus named checks for the standard ones
    if (err.isMethodNotFound) {
        println(err.message)
    }

    // or convert it to an exception at the boundary of your own code
    throw err.toException()
}
```

`unwrap()` is the shortcut for "give me the value or throw"; `unwrapOrNull()`, `unwrapElse(default)` and
`unwrapOrReturn { }` cover the cases where you would rather not.

## PubSub Functionality

Subscriptions are served over `eth_subscribe`, which requires a transport that can push. `WsClient` supports it;
`HttpClient` fails every subscription call with a "subscriptions unsupported" error rather than throwing. WebSocket
clients automatically reconnect on connection drop and, unless `resubscribeOnReconnect(false)` is set on the
builder, resubscribe to all active subscriptions transparently.

A subscription is delivered as a `ChannelReceiver`, which is **blocking by default**: `forEach` consumes on the
calling thread. Its non-blocking counterpart, `forEachAsync`, spawns a background thread and so exists on JVM and
Android only - you can pass it a custom thread factory, for example to spawn a virtual thread. From common code,
run the consumer on a dispatcher of your choosing instead.

There are also `watch`-prefixed functions which mimic the behavior of subscriptions over any transport, but instead
create a filter on the server, which is polled intermittently for new values. They return a `FilterPoller`, whose
polling interval is set with `withInterval`.

### 💻 Code Examples

- Request different block data over HTTP.

    ```kotlin
    val provider = Provider.builder("<http-url>").build().unwrap()

    val blockNumber = 18433374L

    val blockHeader = provider.getBlockHeader(blockNumber).send().unwrap()
    val blockWithTransactions = provider.getBlockWithTransactions(blockNumber).send().unwrap()
    ```

- Execute a batch request.

    ```kotlin
    val provider = Provider.builder("<http-url>").build().unwrap()

    val (blockNumber, transaction, logs) = batchRequest(
        provider.getBlockNumber(),
        provider.getTransactionByHash(Hash("0x8d6f9d0d94b84d6be19b70ac812ff291eceece6ad7ba390599a654e4c52603b4")),
        provider.getLogs(LogFilter {
            blockRange(18433370, 18433375) // both bounds inclusive
            topic0(
                Hash("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"), // ERC20 transfer topic
                Hash("0xd78ad95fa46c994b6551d0da85fc275fe613ce37657fb8d5e3d130840159d822"), // Uniswap V2 swap topic
            )
        }),
    ).awaitSuspend().unwrap()
    ```

- Subscribe to new pending transactions via `subscribe` or `watch`.

  ```kotlin
  import kotlin.time.Duration.Companion.seconds

  // subscribe, over a WebSocket transport
  val provider = Provider.builder("<ws-url>").build().unwrap()
  val stream = provider.subscribeNewPendingTransactions().send().unwrap()

  // or watch, which works over HTTP too
  val provider = Provider.builder("<http-url>").build().unwrap()
  val stream = provider.watchNewPendingTransactions().send().unwrap().withInterval(1.seconds)

  stream.filter { it.gas > 21000L }.forEach { println("New pending TX: $it") }
  ```

More examples can be found in [tests](src/commonTest/kotlin/io/ethers/providers).
