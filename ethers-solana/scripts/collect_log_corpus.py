#!/usr/bin/env python3
"""Read-only collector for program log messages of finalized mainnet transactions. Python 3 standard library only.

Samples recent transactions touching a broad set of programs, keeping successes and failures of each, then tops up
with heavy transactions until enough truncated logs are found. Each record keeps what log parsing is checked against:
the logs, the transaction error, and the program of each top-level instruction. Never builds or submits transactions.
"""
import argparse
import collections
import hashlib
import http.client
import json
import time
import urllib.error
import urllib.request
from pathlib import Path

ENDPOINT = "https://api.mainnet-beta.solana.com"

# label -> program; chosen for different log shapes: Anchor emit!/emit_cpi! events, deep CPI chains, native programs,
# custom and built-in errors, return data, and very long aggregator logs
PROGRAMS = {
    "jupiter-v6": "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4",
    "jupiter-limit": "jupoNjAxXgZ4rjzxzPMP4oxduvQsQtZzyknqvzYNrNu",
    "jupiter-dca": "DCA265Vj8a9CEuX1eb1LWRnDT7uK6q1xMipnNyatn23M",
    "raydium-amm-v4": "675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8",
    "raydium-clmm": "CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK",
    "raydium-cpmm": "CPMMoo8L3F4NbTegBCKVNunggL7H1ZpdTHKxQB5qKP1C",
    "orca-whirlpool": "whirLbMiicVdio4qvUfM5KAg6Ct8VwpYzGff3uctyCc",
    "meteora-dlmm": "LBUZKhRxPF3XUpBCjp4YzTKgLccjZhTSDM9YuVaPwxo",
    "meteora-damm": "Eo7WjKq67rjJQSZxS6z3YkapzY3eMj6Xy8X5EQVn5UaB",
    "meteora-damm-v2": "cpamdpZCGKUy5JxQXB4dcpGPiikHawvSWAd6mEn1sGG",
    "pump-fun": "6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P",
    "pump-swap": "pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA",
    "lifinity-v2": "2wT8Yq49kHgDzXuPxZSaeLaH1qbmGXtEyPy64bL7aD3c",
    "phoenix": "PhoeNiXZ8ByJGLkxNfZRnkUfjvmuYqLR89jjFHGqdXY",
    "openbook-v2": "opnb2LAfJYbRMAHHvqjCwQxanZn7ReEHp1k81EohpZb",
    "drift-v2": "dRiftyHA39MWEi3m9aunc5MzRF1JYuBsbn6VPcn33UH",
    "marginfi-v2": "MFv2hWf31Z9kbCa1snEPYctwafyhdvnV7FZnsebVacA",
    "kamino-lend": "KLend2g3cP87fffoy8q1mQqGKjrxjC8boSyAYavgmjD",
    "solend": "So1endDq2YkqhipRh3WViPa8hdiSpxWy6z3Z6tMCpAo",
    "mango-v4": "4MangoMjqJ2firMokCjjGgoK8d4MXcrgL7XJaL3w6fVg",
    "tensor-swap": "TSWAPaqyCSx2KABk68Shruf4rp7CxcNi8hAsbdwmHbN",
    "magic-eden-m2": "M2mx93ekt1fmXSVkTrUL9xVFHkmME8HTUi5Cyc5aF7K",
    "token-metadata": "metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s",
    "metaplex-core": "CoREENxT6tW1HoK8ypY1SxRMZTcVPm7R94rH4PZNhX7d",
    "bubblegum": "BGUMAp9Gq7iTEuizy4pqaxsTyUCBK68MDfK752saRPUY",
    "squads-v4": "SQDS4ep65T869zMMBKyuUq6aD6EgTu8psMjkvj52pCf",
    "wormhole-core": "worm2ZoG2kUd4vFXhvjh93UUH596ayRfgQ2MgjNMTth",
    "pyth-receiver": "rec5EKMGg6MxZYaMdyBfgwp4d5rB9T1VQH5pJv5LtFJ",
    "switchboard-on-demand": "SBondMDrcV3K4kxZR1HNVT7osZxAHVHgYXL5Ze1oMUv",
    "token": "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
    "token-2022": "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb",
    "associated-token": "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL",
    "system": "11111111111111111111111111111111",
    "stake": "Stake11111111111111111111111111111111111111",
    "vote": "Vote111111111111111111111111111111111111111",
    "memo": "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr",
    "address-lookup-table": "AddressLookupTab1e1111111111111111111111111",
    "compute-budget": "ComputeBudget111111111111111111111111111111",
}

# long multi-hop routes are where the node's log limit truncates logs
TRUNCATION_SOURCES = ["jupiter-v6", "meteora-dlmm", "orca-whirlpool", "raydium-clmm", "kamino-lend", "drift-v2"]


class Rpc:
    """Paces requests adaptively: throttling widens the spacing, each success narrows it again."""

    def __init__(self, cache, spacing):
        self.cache, self.spacing, self.minimum, self.last = cache, spacing, spacing, 0.0
        cache.mkdir(parents=True, exist_ok=True)

    def call(self, method, params):
        assert method in {'getSignaturesForAddress', 'getTransaction'}
        key = hashlib.sha256(json.dumps([method, params]).encode()).hexdigest()
        cached = self.cache / (key + '.json')
        if cached.exists():
            return json.loads(cached.read_text())
        body = json.dumps({'jsonrpc': '2.0', 'id': 1, 'method': method, 'params': params}).encode()
        for attempt in range(12):
            time.sleep(max(0.0, self.spacing - (time.monotonic() - self.last)))
            try:
                self.last = time.monotonic()
                req = urllib.request.Request(ENDPOINT, body, {'Content-Type': 'application/json'})
                with urllib.request.urlopen(req, timeout=45) as response:
                    reply = json.loads(response.read().decode())
                if 'error' in reply:
                    raise RuntimeError(reply['error'])
                cached.write_text(json.dumps(reply['result']))
                self.spacing = max(self.minimum, self.spacing * 0.9)
                return reply['result']
            except urllib.error.HTTPError as error:
                if error.code != 429 or attempt == 11:
                    raise
                self.spacing = min(5.0, self.spacing * 1.5)
                time.sleep(float(error.headers.get('Retry-After') or self.spacing))
            except (OSError, http.client.HTTPException, RuntimeError) as error:
                if attempt == 11:
                    raise
                print(f'retry {method}: {error}', flush=True)
                time.sleep(min(30, 2 ** min(attempt, 5)))


def record(label, signature, tx):
    msg, meta = tx['transaction']['message'], tx['meta'] or {}
    return {
        'signature': signature,
        'slot': tx['slot'],
        'sample': label,
        'version': tx.get('version', 'legacy'),
        'err': meta.get('err'),
        # program ids are always among the static account keys, never loaded from a lookup table
        'topLevelPrograms': [msg['accountKeys'][ix['programIdIndex']] for ix in msg['instructions']],
        'logMessages': meta.get('logMessages'),
    }


def features(entry):
    logs = entry['logMessages'] or []
    depth = max((int(line.rsplit('[', 1)[1][:-1]) for line in logs if ' invoke [' in line), default=0)
    return {
        'failed': entry['err'] is not None,
        'custom_error': isinstance(entry['err'], dict) and 'InstructionError' in entry['err']
        and isinstance(entry['err']['InstructionError'][1], dict) and 'Custom' in entry['err']['InstructionError'][1],
        'truncated': 'Log truncated' in logs,
        'data': sum(line.startswith('Program data: ') for line in logs),
        'return': any(line.startswith('Program return: ') for line in logs),
        'consumption': any(line.startswith('Program consumption: ') for line in logs),
        'depth': depth,
        'lines': len(logs),
    }


def fetch(rpc, label, signatures):
    entries = []
    for signature in signatures:
        tx = rpc.call('getTransaction', [signature, {'encoding': 'json', 'commitment': 'finalized', 'maxSupportedTransactionVersion': 1}])
        if tx is not None and (tx['meta'] or {}).get('logMessages') is not None:
            entries.append(record(label, signature, tx))
    return entries


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--count', type=int, default=1000)
    parser.add_argument('--successes', type=int, default=18, help='Successful transactions sampled per program')
    parser.add_argument('--failures', type=int, default=10, help='Failed transactions sampled per program, when available')
    parser.add_argument('--truncated', type=int, default=15, help='Truncated logs to find before stopping the top-up')
    parser.add_argument('--spacing', type=float, default=0.3, help='Seconds between requests')
    parser.add_argument('--cache', type=Path, required=True, help='Response cache; reruns resume from it')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    rpc = Rpc(args.cache, args.spacing)

    entries, seen = [], set()
    for label, program in PROGRAMS.items():
        listed = rpc.call('getSignaturesForAddress', [program, {'limit': 1000, 'commitment': 'finalized'}])
        failed = [s['signature'] for s in listed if s['err'] is not None][:args.failures]
        succeeded = [s['signature'] for s in listed if s['err'] is None][:args.successes]
        picked = [s for s in failed + succeeded if s not in seen]
        seen.update(picked)
        fetched = fetch(rpc, label, picked)
        entries += fetched
        print(f'{label}: {len(fetched)} ({len(failed)} failed)', flush=True)

    # top up with heavy transactions until enough truncated logs are found
    for label in TRUNCATION_SOURCES:
        if sum(features(e)['truncated'] for e in entries) >= args.truncated:
            break
        listed = rpc.call('getSignaturesForAddress', [PROGRAMS[label], {'limit': 1000, 'commitment': 'finalized'}])
        for signature in [s['signature'] for s in listed if s['signature'] not in seen][:300]:
            seen.add(signature)
            for entry in fetch(rpc, label, [signature]):
                if features(entry)['truncated']:
                    entries.append(entry)
            if sum(features(e)['truncated'] for e in entries) >= args.truncated:
                break
        print(f'truncated after {label}: {sum(features(e)["truncated"] for e in entries)}', flush=True)

    # keep every truncated log, then round-robin over program and outcome for the rest
    truncated = [e for e in entries if features(e)['truncated']]
    groups = collections.defaultdict(list)
    for entry in entries:
        if not features(entry)['truncated']:
            groups[(entry['sample'], features(entry)['failed'])].append(entry)
    selected, queues = list(truncated), [groups[k] for k in sorted(groups)]
    while queues and len(selected) < args.count:
        remaining = []
        for queue in queues:
            if len(selected) < args.count:
                selected.append(queue.pop(0))
            if queue:
                remaining.append(queue)
        queues = remaining
    selected.sort(key=lambda e: (e['sample'], e['slot'], e['signature']))

    args.output.mkdir(parents=True, exist_ok=True)
    lines = ''.join(json.dumps(e, separators=(',', ':')) + '\n' for e in selected)
    (args.output / 'mainnet-logs.jsonl').write_text(lines)
    stats = [features(e) for e in selected]
    manifest = {
        'endpoint': ENDPOINT,
        'captured': time.strftime('%Y-%m-%d', time.gmtime()),
        'transactions': len(selected),
        'logLines': sum(s['lines'] for s in stats),
        'programs': collections.Counter(e['sample'] for e in selected),
        'failed': sum(s['failed'] for s in stats),
        'customErrors': sum(s['custom_error'] for s in stats),
        'truncated': sum(s['truncated'] for s in stats),
        'withData': sum(s['data'] > 0 for s in stats),
        'dataLines': sum(s['data'] for s in stats),
        'withReturn': sum(s['return'] for s in stats),
        'withConsumption': sum(s['consumption'] for s in stats),
        'maxDepth': max(s['depth'] for s in stats),
        'versions': collections.Counter(str(e['version']) for e in selected),
        'slots': [min(e['slot'] for e in selected), max(e['slot'] for e in selected)],
    }
    (args.output / 'manifest.json').write_text(json.dumps(manifest, indent=2, sort_keys=True) + '\n')
    digest = hashlib.sha256(lines.encode()).hexdigest()
    (args.output / 'SHA256SUMS').write_text(f'{digest}  mainnet-logs.jsonl\n')
    print(json.dumps(manifest, indent=2, sort_keys=True))


if __name__ == '__main__':
    main()
