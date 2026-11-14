# Program log corpus

Program log messages of 1,000 finalized mainnet transactions, captured on 2026-09-26 with
`scripts/collect_log_corpus.py` from `https://api.mainnet-beta.solana.com`. Tests run offline in `commonTest`
on every target; the `generateSolanaLogCorpus` task embeds `mainnet-logs.jsonl` as `logCorpus()`.

Each line holds one transaction: its `signature`, `slot`, transaction `version`, the `err` it reported, the program
of each top-level instruction (`topLevelPrograms`), its `logMessages`, and the `sample` it was drawn for.

## Sampling

For each of 38 programs, the collector takes up to 18 successful and 10 failed transactions from the program's most
recent 1,000 signatures, skipping signatures already taken for another program. The programs cover DEX aggregators
and AMMs, lending and perpetuals, NFT marketplaces and Metaplex, Squads, Wormhole, oracles, and the native System,
Token, Token-2022, Associated Token, Stake, Vote, Memo, Address Lookup Table and Compute Budget programs, so the logs
include Anchor `emit!` data, deep CPI chains, custom and built-in errors, return data and consumption lines.
`manifest.json` records the per-program counts.

| | Count |
| --- | ---: |
| Transactions | 1,000 |
| Log lines | 32,001 |
| Failed | 318 |
| Custom program errors | 262 |
| Transactions with `Program data:` | 324 (913 lines) |
| Transactions with `Program return:` | 305 |
| Transactions with `Program consumption:` | 40 |
| Deepest invocation | 5 |
| Legacy / v0 / v1 | 234 / 617 / 149 |

The collector also searched 600 further Jupiter and Meteora DLMM transactions for truncated logs and found none, so
the corpus has no `Log truncated` line; `ProgramLogsTest` covers truncation.

## Refreshing

```
python3 scripts/collect_log_corpus.py --cache <response cache> --output src/commonTest/resources/program-logs
```

Responses are cached, so an interrupted run resumes. The collector rewrites `mainnet-logs.jsonl`, `manifest.json` and
`SHA256SUMS`; update this README from the new manifest.
