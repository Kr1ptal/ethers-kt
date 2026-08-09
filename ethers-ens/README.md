# ethers-ens

Support for [ENS](https://docs.ens.domains/) name resolution, reverse resolution, text records, and avatars —
including [wildcard resolution (ENSIP-10)](https://docs.ens.domains/ens-improvement-proposals/ensip-10-wildcard-resolution)
and [CCIP-Read offchain resolution (ERC-3668)](https://eips.ethereum.org/EIPS/eip-3668).

The module has two entry points.

## `EnsResolver` — explicit resolution

Use this when you want to resolve something and care about *why* it failed. Errors are typed as
`EnsResolver.Error`.

```kotlin
val provider = Provider.builder("<URL>").buildAwait().unwrap()
val ens = EnsResolver(provider)

val address = ens.resolveAddress("vitalik.eth").sendAwait().unwrap()
val twitter = ens.resolveText("vitalik.eth", "com.twitter").sendAwait().unwrap()
val name = ens.resolveEnsName(address).sendAwait().unwrap()
val avatar = ens.resolveAvatar("vitalik.eth").sendAwait().unwrap()
```

It also offers ENS-name overloads for the account-scoped RPC methods, which take a bare `Address` and so cannot
be intercepted:

```kotlin
val balance = ens.getBalance("vitalik.eth", BlockId.LATEST).sendAwait().unwrap()
val code = ens.getCode("vitalik.eth", BlockId.LATEST).sendAwait().unwrap()
```

Resolutions are cached for 5 minutes by default; pass a different `EnsNameCache` to change the ttl.

## `EnsMiddleware` — ENS names in call requests

Use this when you want to pass a name anywhere a call request is accepted. It is a real `Middleware` layer, so
it composes with other layers and can be used as a drop-in provider.

```kotlin
val provider = EnsMiddleware(Provider.builder("<URL>").buildAwait().unwrap())

val result = provider
    .call(EnsCallRequest("vitalik.eth").data(callData), BlockId.LATEST)
    .sendAwait()
    .unwrap()
```

`EnsCallRequest` carries the same chaining builders as `CallRequest`, and can also be built with a lambda or by
wrapping a request you already have:

```kotlin
EnsCallRequest("vitalik.eth").data(callData).value(amount)   // chained
EnsCallRequest("vitalik.eth") { data = callData }            // builder lambda
CallRequest().data(callData).toEns("vitalik.eth")            // wrap an existing request
```

There is deliberately no `to(Address)` builder — the recipient of an `EnsCallRequest` is always its ENS name. Use
a plain `CallRequest` to target a raw address.

Supported on `call`, `estimateGas`, `createAccessList`, `fillTransaction`, `traceCall`, `callMany` and
`traceCallMany`. Requests that are not an `EnsCallRequest` pass through untouched.

Two things to know:

- `Middleware` fixes the error type to `RpcError`, so an ENS failure arrives as an `RpcError` with code
  `EnsMiddleware.CODE_ENS_RESOLUTION_FAILED` and the typed `EnsResolver.Error` as its `cause`. Reach for
  `EnsResolver` directly when you need the typed error.
- In the batch methods (`callMany`, `traceCallMany`) every name resolves concurrently — a batch exists to save
  round trips, and resolving names one after another in front of it would cost more than it saves. A name that
  cannot be resolved fails the **whole batch**, rather than becoming one failed entry: per-entry failures are
  reported as `CallFailedError`, which carries only a string, and an unresolvable name is bad input rather than
  an execution result.

Passing an `EnsCallRequest` to a provider that is not wrapped in `EnsMiddleware` throws an `IllegalStateException`
naming the fix, rather than silently sending a call with no recipient.
