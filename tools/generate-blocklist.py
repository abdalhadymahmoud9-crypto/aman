#!/usr/bin/env python3
"""Generate all blocklist-derived build assets from porn-hosts.txt.

Run from the repository root after tools/fetch-blocklist.sh:

    python3 tools/generate-blocklist.py

Produces:
  domains-clean.txt                              stage files (kept for inspection)
  domains-all.txt, domains-collapsed.txt
  android-source/app/src/main/assets/blocklist.txt.gz
  extension/rules/rules_1.json .. rules_4.json

No dependencies beyond the Python 3 standard library.
"""
import gzip
import json
import re
import os

BASELINE = 28000          # rules_1: always enabled, within Chrome's 30k guaranteed quota
RULE_ID_BASE = 1          # ids restart at 1 inside each ruleset file

def read_hosts(path="porn-hosts.txt"):
    doms = []
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            if line.startswith("0.0.0.0 "):
                parts = line.split()
                if len(parts) >= 2:
                    d = parts[1].strip().lower()
                    # skip the literal "0.0.0.0 0.0.0.0" row and junk rows
                    # whose "domain" still starts with 0.0.0.0.
                    if d and not d.startswith("0.0.0.0"):
                        doms.append(d)
    return sorted(set(doms))

def collapse(doms):
    """Drop a domain if any parent suffix is also in the set
    (the parent already matches it)."""
    S = set(doms)
    def has_parent(d):
        parts = d.split(".")
        for i in range(1, len(parts) - 1):
            if ".".join(parts[i:]) in S:
                return True
        return False
    return [d for d in doms if not has_parent(d)]

LABEL = re.compile(r"^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")

def clean(doms):
    """Drop entries that are not valid DNS names (e.g. contain underscores)."""
    return [d for d in doms if all(LABEL.match(p) for p in d.split("."))]

def write_lines(path, lines):
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")

def main():
    all_d = read_hosts()
    write_lines("domains-all.txt", all_d)
    collapsed = collapse(all_d)
    write_lines("domains-collapsed.txt", collapsed)
    final = clean(collapsed)
    write_lines("domains-clean.txt", final)
    print(f"all={len(all_d)} collapsed={len(collapsed)} clean={len(final)}")

    # Android asset: gzipped plain domain list, one per line (mtime=0 for
    # reproducible bytes).
    os.makedirs("android-source/app/src/main/assets", exist_ok=True)
    payload = ("\n".join(final) + "\n").encode("utf-8")
    with open("android-source/app/src/main/assets/blocklist.txt.gz", "wb") as f:
        with gzip.GzipFile(fileobj=f, mode="wb", mtime=0) as gz:
            gz.write(payload)

    # Chrome MV3 declarativeNetRequest rulesets. Baseline stays within the
    # 30k guaranteed static-ruleset quota; the extended sets are enabled at
    # runtime from the remaining global pool (the extension shows x/4).
    os.makedirs("extension/rules", exist_ok=True)
    chunks = [final[:BASELINE]]
    rest = final[BASELINE:]
    per = -(-len(rest) // 3)
    for i in range(3):
        chunks.append(rest[i * per:(i + 1) * per])
    for n, chunk in enumerate(chunks, 1):
        rules = [{
            "id": RULE_ID_BASE + i,
            "priority": 1,
            "action": {"type": "block"},
            "condition": {"urlFilter": f"||{d}^"},
        } for i, d in enumerate(chunk)]
        with open(f"extension/rules/rules_{n}.json", "w", encoding="utf-8") as f:
            f.write(json.dumps(rules, separators=(",", ":")))
        print(f"rules_{n}.json: {len(chunk)}")

if __name__ == "__main__":
    main()
