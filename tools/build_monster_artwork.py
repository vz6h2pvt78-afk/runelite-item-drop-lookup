"""Validate reviewed offline portraits and deterministically build the runtime mapping.
No network or image-generation dependency. Preparation/review evidence is retained separately.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
REVIEW = ROOT / "tools/data/monster_artwork_review.json"
ART = ROOT / "src/main/resources/artwork"
MANIFEST = ART / "monsters.tsv"


def png_dimensions(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("Not PNG")
    offset, dimensions, end = 8, None, False
    while offset + 12 <= len(data):
        size = struct.unpack(">I", data[offset:offset + 4])[0]
        kind = data[offset + 4:offset + 8]
        body = data[offset + 8:offset + 8 + size]
        if len(body) != size or offset + size + 12 > len(data):
            raise ValueError("Truncated PNG")
        crc = struct.unpack(">I", data[offset + 8 + size:offset + 12 + size])[0]
        if zlib.crc32(kind + body) & 0xffffffff != crc:
            raise ValueError("Bad PNG checksum")
        if dimensions is None:
            if kind != b"IHDR" or size != 13:
                raise ValueError("Missing IHDR")
            dimensions = struct.unpack(">II", body[:8])
        offset += 12 + size
        if kind == b"IEND":
            end = True
            break
    if not end or offset != len(data):
        raise ValueError("Missing IEND or trailing data")
    return dimensions


def build(review, asset_root):
    if review["schemaVersion"] != 1:
        raise ValueError("Unsupported artwork schema")
    sources = review["sources"]
    if len(sources) != review["expectedSources"] or len({r["source"] for r in sources}) != len(sources):
        raise ValueError("Incomplete or duplicate source inventory")
    assets = {}
    for asset in review["assets"]:
        key = asset["asset"]
        if not re.fullmatch(r"[a-f0-9]{24}", key) or key in assets:
            raise ValueError("Invalid or duplicate asset id")
        path = asset_root / (key + ".png")
        data = path.read_bytes()
        if (hashlib.sha256(data).hexdigest() != asset["sha256"]
                or len(data) != asset["bytes"] or png_dimensions(data) != (36, 40)
                or asset["dimensions"] != [36, 40]):
            raise ValueError("Asset mismatch: " + key)
        for field in ("filePageUrl", "originalUrl", "thumbnailUrl"):
            if not asset[field].startswith("https://oldschool.runescape.wiki/"):
                raise ValueError("Missing Wiki provenance")
        if not asset.get("originalSha1") or not asset.get("downloadSha256"):
            raise ValueError("Missing image provenance hash")
        assets[key] = asset
    if {p.stem for p in asset_root.glob("*.png")} != set(assets):
        raise ValueError("Untracked or missing portrait files")
    lines = ["# Reviewed decorative portraits v1: S=exact source; G=representative group"]
    by_name = {r["source"]: r for r in sources}
    used, seen_groups = set(), set()
    for kind, records, field in (("S", sources, "source"), ("G", review["groups"], "group")):
        for record in sorted(records, key=lambda r: r[field]):
            name = record[field]
            if not name or any(ord(c) < 32 or ord(c) == 127 for c in name):
                raise ValueError("Invalid mapping name")
            if record["status"] not in ("reviewed", "held") or not record.get("reason"):
                raise ValueError("Missing review outcome")
            if kind == "G":
                if name in seen_groups or not set(record["members"]) <= set(by_name):
                    raise ValueError("Invalid group inventory")
                seen_groups.add(name)
            if record["status"] == "held":
                if "asset" in record:
                    raise ValueError("Held mapping has an asset")
                continue
            asset = record["asset"]
            if asset not in assets:
                raise ValueError("Unknown asset")
            if kind == "S":
                if not record.get("sourceRevision") or not record.get("selectedInfobox"):
                    raise ValueError("Missing own-page evidence")
            else:
                representative = record["representativeSource"]
                if representative not in record["members"] or by_name[representative].get("asset") != asset:
                    raise ValueError("Invalid group representative")
            line = "\t".join((kind, name, asset))
            if len(line) > 512:
                raise ValueError("Mapping too long")
            lines.append(line)
            used.add(asset)
    if used != set(assets) or len(lines) > 2048:
        raise ValueError("Unreferenced assets or runtime catalogue limit exceeded")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    data = json.loads(REVIEW.read_text(encoding="utf-8"))
    expected = build(data, ART / "monsters")
    if args.write:
        MANIFEST.write_text(expected, encoding="utf-8", newline="\n")
    elif not MANIFEST.exists() or MANIFEST.read_text(encoding="utf-8") != expected:
        raise SystemExit("Artwork manifest differs from reviewed inputs; run --write after review")
    print(json.dumps({"sources": len(data["sources"]),
        "portraits": sum(r["status"] == "reviewed" for r in data["sources"]),
        "held": [r["source"] for r in data["sources"] if r["status"] == "held"],
        "groups": sum(r["status"] == "reviewed" for r in data["groups"]),
        "assets": len(data["assets"]), "bytes": sum(r["bytes"] for r in data["assets"]),
        "manifestSha256": hashlib.sha256(expected.encode()).hexdigest()}))


if __name__ == "__main__":
    main()
