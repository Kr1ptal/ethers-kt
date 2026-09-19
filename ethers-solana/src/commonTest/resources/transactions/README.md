# Transaction corpus

Captured from finalized public-chain and local-validator RPC data on 2026-09-05, with the mainnet v1
samples added 2026-09-19. Tests run offline in `commonTest` on JVM, Android and Kotlin/Native; there are
no live RPC requests during normal tests.

## Coverage

The corpus contains **1,340 transactions**: **320 mainnet legacy + 320 mainnet v0 + 500 mainnet v1
+ 200 local-validator v1**. The local v1 fixtures were generated and submitted to an isolated Agave 4.2.2
validator; they are labeled `cluster: localnet` and `origin: generated-local-validator`, and are not
counted as public-chain samples. `SolanaTxV1Test` additionally covers constructed codec fixtures.

The v1 feature gate `txv1aq4pp281K9um3tnPgkfX8UqtFT6wcVW3hNezGLL` activated on mainnet at slot
447,742,080, read from its Feature account at capture time and recorded in `mainnet-v1-manifest.json`.
No v1 transaction can predate that slot, so unlike legacy and v0 the v1 samples span no historical
windows; they come from 22 blocks in slots 448,515,494..448,516,308. The earlier
`*-discovery.json` files record the bounded testnet/devnet scans that predated activation and found none.

| Samples | Legacy | V0 | V1 |
| --- | ---: | ---: | ---: |
| Total | 320 | 320 | 500 |
| Failed executions | 43 | 59 | 243 |
| Multiple signatures | 45 | 49 | 5 |
| Address lookup tables | 0 | 159 | 0 |
| Inner instructions | 100 | 134 | 110 |
| Return data | 9 | 11 | 27 |
| Distinct top-level programs | 80 | 111 | 53 |
| Largest wire envelope (bytes) | 1229 | 1230 | 3370 |

None of the 500 carries an address lookup table, which matches the v1 message layout as implemented
here: `SolanaTxV1` inherits the empty `addressLookupTables` default and its body encodes none. 229 v1
envelopes exceed the 1,232-byte legacy ceiling that v1 raises to 4,096, and every one states its budget
in the inline config rather than as ComputeBudget instructions.

Five nonetheless carry explicit ComputeBudget instructions alongside that config, and in every one the
instruction restates what the config already says. The fifth encodes limit 164,417, price 1,106,229 and
data-size limit 28,666,650; `ceil(164417 * 1106229 / 1e6)` is 181,883, its inline `priorityFee` exactly.
The other four use a 12-byte discriminant-2 payload whose leading five bytes are a well-formed
`setComputeUnitLimit` carrying that transaction's own inline limit, followed by seven trailing bytes
that are byte-identical across all four and match no ComputeBudget layout; all four come from one
client. The runtime logs every one of these as `success`, because v1 takes its budget from the inline
config and does not parse ComputeBudget instructions as configuration - on legacy and v0, where the
instruction is the configuration, a trailing-byte payload would fail the transaction instead.

The fifth is what showed `compileV1` translating recognised ComputeBudget instructions into config and
compiling only the remainder, so a decompiled v1 transaction did not recompile to the same message. The
other four escaped only because the width check left their longer payloads alone. `compileV1` now takes
its config from the request fields and compiles every instruction as given, so all five round-trip.

The initial samples came from ten mainnet blocks (slots 444610345..444610678). The expansion adds
40 transactions of each version from each of three historical windows ending at slots 300000000,
400000000 and 440000000 (two blocks per window). The 16 blocks span 2024-11-07 through 2026-09-05 UTC.
Tests enforce the per-version historical quotas, uniqueness and block diversity. Within each block,
the collector groups candidates by programs, success/failure, signer count, lookups and CPI, then
round-robins groups to avoid selecting only vote transactions. This is a regression corpus, not a
statistically representative sample of chain activity.

## Files and fidelity

- `mainnet-legacy.jsonl`, `mainnet-0.jsonl`, `mainnet-1.jsonl` and `mainnet-{300,400,440}m-{legacy,0}.jsonl`: one transaction per line.
- `mainnet*-manifest.json`: public endpoint, genesis hash, RPC version at capture, capture time and source blocks.
  `mainnet-v1-manifest.json` additionally records the v1 Feature account response proving activation.
- `localnet-1.jsonl` and `localnet-manifest.json`: 200 finalized local v1 transactions and their validator provenance.
- `*-discovery.json`: unsuccessful v1 discovery observations; not transaction fixtures.
- `checksums.json`: SHA-256 hashes of the JSONL files, counts and collection target.
- `SHA256SUMS`: build-verified JSONL checksum inventory; unexpected files or modified bytes fail generation.

Each record contains `cluster`, `slot`, containing `blockhash`, transaction `signature`, base64 `wire`,
and a compiled-JSON `rpc` transaction. The public-chain collector calls `getBlock` twice at the same finalized slot,
once with `encoding=json` and once with `encoding=base64`. It checks blockhashes, versions, signatures
and metadata agree. The `rpc` object projects the JSON block transaction into the `getTransaction`
shape by adding the containing block's `slot` and `blockTime`; it is not advertised as a raw
`getTransaction` response. JSON numeric/string lexemes are retained, with only insignificant whitespace
removed. Wire bytes come from the RPC, not from this library's encoder.

Gradle embeds these files verbatim as bounded generated Kotlin strings so `commonTest` does not need a
platform-specific resource loader. Generated sources live under `build/` and are not committed.

## Local v1 coverage and provenance

`LocalValidatorCorpusTest` generates ephemeral signers in memory, funds them using the local faucet,
simulates every transaction, and submits through the provider's normal signed-transaction API.
It retrieves each finalized transaction twice using `getTransaction` with JSON and base64 encoding,
compares metadata and slot, and verifies RPC wire bytes match the submitted transaction. Unlike the
mainnet records, `rpc` here is the actual `getTransaction` result, not a block projection. JSON is handled
as Kotlinx JSON elements to preserve numeric literals, not converted through floating-point values.
Only public transaction data is exported; private keys and the temporary ledger are never committed.

The 200 fixtures cover 17 blocks, every signer count from 1 to 12, up to 60 static accounts and 64
instructions, and envelopes from 228 to 3,902 bytes (132 exceed 1,232 bytes). They vary compute/data
limits, absent/zero/nonzero priority fees, and absent/32 KiB/64 KiB/256 KiB heap requests.
All execute successful System Program transfers: they do **not** cover v1 CPI, token instructions,
return data, execution failures, or organic public-chain traffic. They establish compatibility with
Agave 4.2.2 for the generated shapes, not independent instruction compilation or exhaustive runtime coverage.
The shared common tests check wire/JSON reconstruction, all signatures, partial signing, metadata,
and v1 fees against validator results; local-specific assertions enforce the advertised shape coverage.

To reproduce, start Agave 4.2+ with a fresh temporary ledger and loopback RPC, then run:

```sh
SOLANA_VALIDATOR_HTTP=http://127.0.0.1:18899 \
SOLANA_VALIDATOR_V1=true \
SOLANA_VALIDATOR_CORPUS_OUTPUT=/private/tmp/solana-v1-new-capture \
./gradlew :ethers-solana:jvmKotest --rerun-tasks
```

The capture output directory must not exist. The test only accepts literal `127.0.0.1` HTTP endpoints;
capture is disabled in ordinary offline test runs. Review the exported JSONL/manifest and update both
checksum files before importing a replacement corpus. Normal tests never generate or submit transactions.

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

`TransactionBoundaryTest` complements the live corpus with all 65,536 compact-u16 values checked
against independently calculated bytes, malformed/overlong lengths, the packet-reachable data-length
transition at 128 bytes, loaded account index 255 and account-count overflow. Larger data lengths are
rejected at transaction construction. Exact 1,232-byte legacy/v0 envelopes with 1, 2 and 12 signers
are accepted; adding one byte fails construction and decoding, including partial-signature import.
Seeded legacy/v0/v1 messages
exercise every message/envelope truncation point and a single-bit mutation at every signed-envelope
byte. These are constructed codec tests, not additional live fixtures. Existing v1 tests additionally cover config masks, integer maxima,
signature/account/instruction limits and the 4,096-byte envelope boundary.

Requires Python 3 with only the standard library. Capture into a fresh directory outside the repository:

```sh
python3 ethers-solana/scripts/collect_transaction_corpus.py \
  --cluster mainnet --versions legacy 0 --count 200 --per-block 20 --max-blocks 30 \
  --output /private/tmp/solana-corpus-capture

# Repeat with 400000000 and 440000000, using a fresh output directory for each window.
python3 ethers-solana/scripts/collect_transaction_corpus.py \
  --cluster mainnet --versions legacy 0 --count 40 --per-block 20 --max-blocks 5 \
  --start-slot 300000000 --output /private/tmp/solana-corpus-300m

# v1 is dense in recent mainnet blocks since activation, so a plain block scan finds it
python3 ethers-solana/scripts/collect_transaction_corpus.py \
  --cluster mainnet --versions 1 --count 500 --per-block 20 --max-blocks 200 \
  --output /private/tmp/solana-corpus-v1

python3 -m unittest discover -s ethers-solana/scripts -p 'test_*.py'
./gradlew :ethers-solana:jvmKotest :ethers-solana:macosArm64Test :ethers-solana:iosSimulatorArm64Test
```

The collector is read-only, caches public responses, rate-limits/retries RPC requests, supports resuming
its own output directory, and exits nonzero when it cannot collect the requested count. `--before` and
`--history-stride` support broader/paginated application-history discovery; `--start-slot` can repeat the
mainnet block selection. Import only the reviewed JSONL and manifests, never its response cache. When
public-chain v1 data is found, add it with its actual cluster provenance and update the public-chain
coverage assertions and counts. Do not relabel the local fixtures as public-chain samples.
