#!/usr/bin/env python3
"""Read-only collector for finalized Solana transactions. Python 3 standard library only.

Preserves RPC JSON number/string lexemes by slicing transaction entries from the response,
not by roundtripping them through Python floats. Never builds or submits transactions.
"""
import argparse
import base64
import collections
import datetime
import hashlib
import json
from pathlib import Path
import time
import urllib.error
import urllib.request

ENDPOINTS = {"mainnet": "https://api.mainnet-beta.solana.com", "testnet": "https://api.testnet.solana.com", "devnet": "https://api.devnet.solana.com"}
FEATURE = "txv1aq4pp281K9um3tnPgkfX8UqtFT6wcVW3hNezGLL"
DECODER = json.JSONDecoder()


def members(raw):
    """Return raw values of an object, preserving every JSON numeric literal."""
    pos = 1
    result = {}
    while True:
        while raw[pos].isspace() or raw[pos] == ',':
            pos += 1
        if raw[pos] == '}':
            return result
        key, pos = DECODER.raw_decode(raw, pos)
        while raw[pos].isspace() or raw[pos] == ':':
            pos += 1
        start = pos
        _, pos = DECODER.raw_decode(raw, pos)
        result[key] = raw[start:pos]


def elements(raw):
    pos = 1
    while True:
        while raw[pos].isspace() or raw[pos] == ',':
            pos += 1
        if raw[pos] == ']':
            return
        start = pos
        _, pos = DECODER.raw_decode(raw, pos)
        yield raw[start:pos]


def compact(raw):
    """Remove only insignificant whitespace, without changing literals or string contents."""
    result, quoted, escaped = [], False, False
    for char in raw:
        if quoted or not char.isspace():
            result.append(char)
        if quoted:
            if escaped:
                escaped = False
            elif char == '\\':
                escaped = True
            elif char == '"':
                quoted = False
        elif char == '"':
            quoted = True
    return ''.join(result)


def base58(data):
    alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    value, result = int.from_bytes(data, 'big'), ''
    while value:
        value, digit = divmod(value, 58)
        result = alphabet[digit] + result
    return '1' * (len(data) - len(data.lstrip(b'\0'))) + result


def wire_signature(encoded):
    wire = base64.b64decode(encoded, validate=True)
    if wire[0] == 129:
        return base58(wire[-64 * wire[1]:][:64])
    pos = 0
    while wire[pos] & 128:
        pos += 1
    return base58(wire[pos + 1:pos + 65])


class Rpc:
    def __init__(self, endpoint, cache):
        self.endpoint, self.cache, self.last = endpoint, cache, 0.0
        cache.mkdir(parents=True, exist_ok=True)

    def call(self, method, params=()):
        assert method in {'getSlot', 'getGenesisHash', 'getVersion', 'getAccountInfo', 'getBlocks', 'getBlock', 'getSignaturesForAddress', 'getTransaction'}
        key = hashlib.sha256(json.dumps([method, params]).encode()).hexdigest()
        cached = self.cache / (key + '.json')
        if cached.exists():
            return cached.read_text()
        body = json.dumps({'jsonrpc': '2.0', 'id': 1, 'method': method, 'params': params}).encode()
        for attempt in range(6):
            time.sleep(max(0.0, 2.0 - (time.monotonic() - self.last)))
            try:
                self.last = time.monotonic()
                req = urllib.request.Request(self.endpoint, body, {'Content-Type': 'application/json'})
                with urllib.request.urlopen(req, timeout=45) as response:
                    raw = response.read().decode()
                fields = members(raw)
                if 'error' in fields:
                    raise RuntimeError(fields['error'])
                result = fields['result']
                cached.write_text(result)
                return result
            except (urllib.error.URLError, TimeoutError, RuntimeError) as error:
                if attempt == 5:
                    raise
                print(f'retry {method}: {error}', flush=True)
                time.sleep(min(20, 2 ** attempt))


def shape(tx):
    msg, meta = tx['transaction']['message'], tx['meta'] or {}
    programs = tuple(sorted({msg['accountKeys'][ix['programIdIndex']] for ix in msg['instructions']}))
    return (programs, meta.get('err') is not None, len(tx['transaction']['signatures']), bool(msg.get('addressTableLookups')), bool(meta.get('innerInstructions')))


def diverse(entries, count):
    groups = collections.defaultdict(list)
    for entry in entries:
        groups[shape(json.loads(entry))].append(entry)
    groups = list(groups.values())
    result = []
    while groups and len(result) < count:
        remaining = []
        for group in groups:
            if len(result) < count:
                result.append(group.pop(0))
            if group:
                remaining.append(group)
        groups = remaining
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cluster', choices=ENDPOINTS, required=True)
    parser.add_argument('--versions', nargs='+', choices=['legacy', '0', '1'], required=True)
    parser.add_argument('--count', type=int, default=200)
    parser.add_argument('--max-blocks', type=int, default=200)
    parser.add_argument('--per-block', type=int, default=20)
    parser.add_argument('--start-slot', type=int)
    parser.add_argument('--application-history', action='store_true')
    parser.add_argument('--history-stride', type=int, default=1)
    parser.add_argument('--before', help='Paginate application history before this signature')
    parser.add_argument('--address', default='11111111111111111111111111111111')
    parser.add_argument('--output', type=Path, required=True, help='New/previous collector-owned output directory; supports resuming')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    rpc = Rpc(ENDPOINTS[args.cluster], args.output / 'cache' / args.cluster)
    source = {
        'endpoint': rpc.endpoint, 'cluster': args.cluster,
        'genesisHash': json.loads(rpc.call('getGenesisHash')),
        'nodeVersion': json.loads(rpc.call('getVersion')),
        'capturedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'commitment': 'finalized',
    }
    manifest_path = args.output / f'{args.cluster}-manifest.json'
    if manifest_path.exists():
        previous = json.loads(manifest_path.read_text())
        assert previous['genesisHash'] == source['genesisHash']
        source['firstCapturedAt'] = previous.get('firstCapturedAt', previous['capturedAt'])
        source['scannedBlocks'] = previous.get('scannedBlocks', [])
    if '1' in args.versions:
        source['v1Feature'] = json.loads(rpc.call('getAccountInfo', [FEATURE, {'encoding': 'base64', 'commitment': 'finalized'}]))
    records = {version: [] for version in args.versions}
    seen = set()
    for version in records:
        path = args.output / f'{args.cluster}-{version}.jsonl'
        if path.exists():
            records[version] = path.read_text().splitlines()
            seen.update(json.loads(line)['signature'] for line in records[version])
    if args.application_history:
        options = {'limit': 1000, 'commitment': 'finalized'}
        if args.before:
            options['before'] = args.before
        history = json.loads(rpc.call('getSignaturesForAddress', [args.address, options]))
        slots = list(dict.fromkeys(entry['slot'] for entry in history))[::args.history_stride]
    else:
        end = args.start_slot or json.loads(rpc.call('getSlot', [{'commitment': 'finalized'}]))
        available = json.loads(rpc.call('getBlocks', [end - 10000, end, {'commitment': 'finalized'}]))
        slots = list(reversed(available))[::37]
    scanned = source.get('scannedBlocks', [])
    def checkpoint():
        source['scannedBlocks'] = scanned
        source['counts'] = {version: len(values) for version, values in records.items()}
        manifest_path.write_text(json.dumps(source, indent=2) + '\n')
    block_config = {'encoding': 'json', 'transactionDetails': 'full', 'rewards': False, 'commitment': 'finalized', 'maxSupportedTransactionVersion': 255}
    for slot in slots[:args.max_blocks]:
        if all(len(values) >= args.count for values in records.values()):
            break
        # Accounts projection makes sparse-v1 discovery far cheaper than downloading logs for every vote.
        if args.versions == ['1']:
            probe = json.loads(rpc.call('getBlock', [slot, dict(block_config, transactionDetails='accounts')]))
            counts = collections.Counter(str(tx.get('version', 'legacy')) for tx in probe['transactions'])
            scanned.append({'slot': slot, 'versions': dict(counts)})
            checkpoint()
            if not counts.get('1'):
                print(f'{args.cluster} {slot}: {dict(counts)}; no v1 ({len(scanned)} blocks scanned)', flush=True)
                continue
        raw_block = rpc.call('getBlock', [slot, block_config])
        block = members(raw_block)
        entries = list(elements(block['transactions']))
        candidates = {version: [] for version in records}
        counts = collections.Counter()
        for entry in entries:
            tx = json.loads(entry)
            version = str(tx.get('version', 'legacy'))
            counts[version] += 1
            if version in candidates and tx['transaction']['signatures'][0] not in seen:
                candidates[version].append(entry)
        selected = []
        for version, values in candidates.items():
            selected.extend((version, entry) for entry in diverse(values, min(args.per_block, args.count - len(records[version]))))
        if not selected:
            continue
        binary_block = json.loads(rpc.call('getBlock', [slot, dict(block_config, encoding='base64')]))
        assert binary_block['blockhash'] == json.loads(block['blockhash'])
        wires = {wire_signature(tx['transaction'][0]): tx for tx in binary_block['transactions']}
        for version, entry in selected:
            tx = json.loads(entry)
            signature = tx['transaction']['signatures'][0]
            binary = wires[signature]
            assert binary['transaction'][1] == 'base64'
            assert binary.get('version', 'legacy') == tx.get('version', 'legacy')
            assert binary['meta'] == tx['meta']
            record = {
                'cluster': args.cluster, 'slot': slot,
                'blockhash': json.loads(block['blockhash']),
                'signature': signature, 'wire': binary['transaction'][0],
            }
            # getBlock contains the same transaction/meta/version fields as getTransaction.
            # Add slot/blockTime from the containing block, without reserializing the entry.
            rpc_json = '{"slot":' + str(slot) + ',"blockTime":' + block['blockTime'] + ',' + entry[1:]
            line = json.dumps(record, separators=(',', ':'))[:-1] + ',"rpc":' + compact(rpc_json) + '}'
            records[version].append(line)
            seen.add(signature)
        if args.versions != ['1']:
            scanned.append({'slot': slot, 'versions': dict(counts)})
        for version, values in records.items():
            (args.output / f'{args.cluster}-{version}.jsonl').write_text('\n'.join(values) + '\n')
        checkpoint()
        print(f'{args.cluster} {slot}: {dict(counts)}; collected ' + str({v: len(r) for v, r in records.items()}), flush=True)
    checkpoint()
    print(json.dumps(source['counts']), flush=True)
    if any(len(values) != args.count for values in records.values()):
        raise SystemExit('Incomplete corpus: no synthetic replacements were created; resume or supply another history address.')


if __name__ == '__main__':
    main()
