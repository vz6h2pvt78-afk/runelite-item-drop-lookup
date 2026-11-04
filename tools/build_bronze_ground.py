"""Project the three reviewed Bronze bar spawn locations through the existing ground-row extractor."""
import argparse
import hashlib
import json
from pathlib import Path
from build_ground_sources import extract_row, require

ROOT = Path(__file__).resolve().parents[1]
PIN = ROOT / "tools/fixtures/bronze_production.json"
OUT = ROOT / "src/main/resources/wiki_ground_additions.json"
EVIDENCE = ROOT / "docs/data/bronze_ground.json"
ROWS = {
    "Brimhaven Dungeon (task-only area)": "d4177b84abcf863faecec57a55f464b13b4cc814e130d3928481e210bd7f8633",
    "Salius' Armour Shop": "073cbfe6d672650c9e7016fd6e8deece659654b8e6b0f8e8175cb4d19352367f",
    "Cam Torum": "903219760bc7a0114d3948be05f719e5cbe597d639b2751eb3be85d1a3129a34",
}

def build(pin):
    require(pin["revisionId"] == 15315107 and hashlib.sha256(pin["wikitext"].encode()).hexdigest() ==
            "722c1841621c30fc066dc6c66c354e5e0734761bb683e2b36046b6063392174e", "Unreviewed Bronze bar article")
    source = dict(pin, sourceUrl="https://oldschool.runescape.wiki/w/Bronze_bar",
                  rawSnapshotSha256="4055a32bd3d93a591b62c2a77c22b4bbebddeb6e1d6692298e855599e9ebb185")
    records, evidence = [], []
    for place, row_hash in ROWS.items():
        review = {"itemId":2349, "location":place, "membersOnly":True, "spawnPointCount":1,
                  "section":["Item sources","Spawns"], "templateSha256":row_hash}
        record, provenance = extract_row(source, review, {"Bronze bar":(2349,3)})
        if place == "Brimhaven Dungeon (task-only area)":
            provenance["verifiedRestrictions"] = ["Task-only area"]
        records.append(record)
        evidence.append(provenance)
    require(len({r["id"] for r in records}) == 3,"Duplicate spawn identity")
    return records, evidence

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check",action="store_true")
    args = parser.parse_args()
    records, provenance = build(json.loads(PIN.read_text(encoding="utf8")))
    for path, value in ((OUT, records), (EVIDENCE, provenance)):
        serialized = json.dumps(value,ensure_ascii=False,indent=2)+"\n"
        if args.check:
            require(path.read_text(encoding="utf8") == serialized,"Ground output differs from the pinned rows")
        else:
            path.parent.mkdir(parents=True,exist_ok=True)
            path.write_text(serialized,encoding="utf8")
    print("3 reviewed Bronze bar ground locations")
