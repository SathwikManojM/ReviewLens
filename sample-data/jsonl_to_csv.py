"""
Convert an Amazon Reviews 2023 (McAuley Lab) JSONL file to a ReviewLens-ready CSV.

  python jsonl_to_csv.py Cell_Phones_and_Accessories.jsonl phones.csv --limit 20000 [--meta meta_Cell_Phones_and_Accessories.jsonl]

The optional --meta file adds real product titles (otherwise the ASIN is used as the name).
Works with plain .jsonl or .jsonl.gz files.
"""
import argparse
import csv
import gzip
import json
from datetime import datetime, timezone


def open_any(path):
    return gzip.open(path, "rt", encoding="utf-8") if path.endswith(".gz") else open(path, encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("reviews")
    ap.add_argument("out")
    ap.add_argument("--meta")
    ap.add_argument("--limit", type=int, default=20000)
    a = ap.parse_args()

    rows = []
    with open_any(a.reviews) as f:
        for line in f:
            r = json.loads(line)
            if not r.get("text"):
                continue
            rows.append(r)
            if len(rows) >= a.limit:
                break

    titles = {}
    if a.meta:
        wanted = {r.get("parent_asin") or r.get("asin") for r in rows}
        with open_any(a.meta) as f:
            for line in f:
                m = json.loads(line)
                if m.get("parent_asin") in wanted:
                    titles[m["parent_asin"]] = m.get("title") or m["parent_asin"]

    with open(a.out, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["asin", "product_name", "rating", "title", "text", "date", "user_id"])
        for r in rows:
            asin = r.get("parent_asin") or r.get("asin")
            ts = r.get("timestamp")
            date = datetime.fromtimestamp(ts / 1000, tz=timezone.utc).date().isoformat() if ts else ""
            w.writerow([asin, titles.get(asin, asin), r.get("rating"), r.get("title", ""), r["text"], date, r.get("user_id", "")])
    print(f"wrote {len(rows)} reviews -> {a.out}")


if __name__ == "__main__":
    main()
