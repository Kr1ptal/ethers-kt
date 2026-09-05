# Live transaction corpus

Captured from finalized public RPC data on 2026-09-05. Tests run offline in `commonTest` on JVM,
Android and Kotlin/Native; there are no live RPC requests during normal tests.

## Coverage and outstanding v1 capture

The requested target is **200 legacy + 200 v0 + 200 v1**. The committed live corpus currently contains
**200 legacy + 200 v0 (400 total)**. **The 200 live v1 samples are still missing.** No constructed,
locally submitted, or relabeled transactions have been substituted for them. `SolanaTxV1Test` separately
covers constructed v1 fixtures; those are not counted as live samples.

Both public testnet and devnet RPC reported the v1 feature active, but the sampled finalized application
histories did not return any v1 transactions. Discovery manifests record the completed secondary scans,
including feature account responses, node versions, slots and observed version counts. An additional
initial scan of 68 recent System Program history blocks on testnet also found no v1 before switching to
broader Ed25519 history. This is a bounded search, not evidence that no v1 transactions exist on either
cluster. A known live v1 signature/address or an indexed RPC source is needed to target further collection.

| Samples | Legacy | V0 |
| --- | ---: | ---: |
| Total | 200 | 200 |
| Failed executions | 32 | 44 |
| Multiple signatures | 29 | 29 |
| Address lookup tables | 0 | 95 |
| Inner instructions | 53 | 63 |
| Return data | 3 | 9 |
| Distinct top-level programs | 52 | 73 |
| Largest wire envelope (bytes) | 1226 | 1227 |

Both versions were sampled from ten mainnet blocks (slots 444610345..444610678). Within each block,
the collector groups candidates by programs, success/failure, signer count, lookups and CPI, then
round-robins groups to avoid selecting only vote transactions. This is a regression corpus, not a
statistically representative sample of chain activity.

## Files and fidelity

- `mainnet-legacy.jsonl` and `mainnet-0.jsonl`: one transaction per line.
- `mainnet-manifest.json`: public endpoint, genesis hash, RPC version, capture time and source blocks.
- `*-discovery.json`: unsuccessful v1 discovery observations; not transaction fixtures.
- `checksums.json`: SHA-256 hashes of the JSONL files, counts and collection target.
- `SHA256SUMS`: build-verified JSONL checksum inventory; unexpected files or modified bytes fail generation.

Each record contains `cluster`, `slot`, containing `blockhash`, transaction `signature`, base64 `wire`,
and a compiled-JSON `rpc` transaction. The collector calls `getBlock` twice at the same finalized slot,
once with `encoding=json` and once with `encoding=base64`. It checks blockhashes, versions, signatures
and metadata agree. The `rpc` object projects the JSON block transaction into the `getTransaction`
shape by adding the containing block's `slot` and `blockTime`; it is not advertised as a raw
`getTransaction` response. JSON numeric/string lexemes are retained, with only insignificant whitespace
removed. Wire bytes come from the RPC, not from this library's encoder.

Gradle embeds these files verbatim as bounded generated Kotlin strings so `commonTest` does not need a
platform-specific resource loader. Generated sources live under `build/` and are not committed.

## Checks performed for every transaction

- Exact binary envelope and signed-message encode/decode roundtrips, including base64.
- Ed25519 verification of every required signature and signature ordering/transaction ID.
- Independent reconstruction from typed RPC fields, compared byte-for-byte with the RPC wire payload.
- Partial-signature import/completion and rejection of corrupt signatures, truncation and trailing bytes.
- JSON encode/decode stability and preservation of every captured value, including unknown fields and
  exact numeric values (no floating-point comparison).
- Static/loaded account counts, lookup-table ordering through wire reconstruction, token balance indices,
  instruction indices, CPI indices and pre/post balance lengths.
- Explicit corpus size, uniqueness, source-block diversity and feature-coverage assertions.

## Reproduction / extension

Requires Python 3 with only the standard library. Capture into a fresh directory outside the repository:

```sh
python3 ethers-solana/scripts/collect_transaction_corpus.py \
  --cluster mainnet --versions legacy 0 --count 200 --per-block 20 --max-blocks 30 \
  --output /private/tmp/solana-corpus-capture

python3 ethers-solana/scripts/collect_transaction_corpus.py \
  --cluster testnet --versions 1 --count 200 --per-block 200 --application-history \
  --address <known-v1-program-or-account> --output /private/tmp/solana-corpus-capture

python3 -m unittest discover -s ethers-solana/scripts -p 'test_*.py'
./gradlew :ethers-solana:jvmKotest :ethers-solana:macosArm64Test :ethers-solana:iosSimulatorArm64Test
```

The collector is read-only, caches public responses, rate-limits/retries RPC requests, supports resuming
its own output directory, and exits nonzero when it cannot collect the requested count. `--before` and
`--history-stride` support broader/paginated application-history discovery; `--start-slot` can repeat the
mainnet block selection. Import only the reviewed JSONL and manifests, never its response cache. When
v1 data is found, update the completeness assertion to require 200 v1 samples and remove the pending
status here. The per-transaction tests already support v1 reconstruction and inline config.
