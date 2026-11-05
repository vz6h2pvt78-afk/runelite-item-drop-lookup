"""Reproduce one exact Sentinel spawn context from two pinned primary pages."""
import argparse
import hashlib
import json
from pathlib import Path
from build_monster_locations import templates

ROOT = Path(__file__).resolve().parents[1]
PIN = ROOT / "tools/fixtures/basilisk_sentinel_spawn.json"
PARENT = ROOT / "tools/fixtures/slayer_access/Basilisk_Knight.json"
OUT = ROOT / "src/main/resources/sentinel_spawn_context.json"
TEXT = "May spawn after killing a Basilisk Knight while on a Basilisk Slayer task with Bigger and Badder unlocked."
SENTINEL_SUPPORT = [
    "is a [[superior]] variant of the [[basilisk knight]]",
    "It has a chance of spawning after purchasing the unlock ''Bigger and Badder''",
]
PARENT_SUPPORT = ("While on a basilisk Slayer task, there is a 1/200 chance that a [[Basilisk Sentinel]] "
                  "will spawn after you kill a Basilisk Knight (if you have [[Slayer Rewards|Bigger and Badder]] unlocked).")


def extract(sentinel, parent):
    evidence = []
    for pin, name, revision in ((sentinel, "Basilisk Sentinel", 15373808),
                                (parent, "Basilisk Knight", 15340714)):
        raw = pin["wikitext"]
        if (pin["title"] != name or pin["revisionId"] != revision
                or hashlib.sha256(raw.encode()).hexdigest() != pin["wikitextSha256"]):
            raise ValueError("Changed spawn primary for " + name)
        boxes = [p for _, _, kind, p, _ in templates(raw) if kind == "infobox monster"]
        if len(boxes) != 1 or boxes[0].get("name") != name:
            raise ValueError("Unclear exact monster identity")
        evidence.append({"page": name, "revisionId": revision,
                         "sourceUrl": "https://oldschool.runescape.wiki/w/" + name.replace(" ", "_") + "?oldid=" + str(revision)})
    if not all(fragment in sentinel["wikitext"] for fragment in SENTINEL_SUPPORT):
        raise ValueError("Missing Sentinel identity or unlock evidence")
    if PARENT_SUPPORT not in parent["wikitext"]:
        raise ValueError("Missing eligible Knight kill, task or unlock condition")
    fact = {"id": "basilisk-sentinel-spawn", "monsterName": "Basilisk Sentinel",
            "scope": "MONSTER", "appliesTo": "ENCOUNTER_CONTEXT", "type": "CONTEXT",
            "text": TEXT, "evidence": evidence}
    return {"schemaVersion": 1, "checkedAt": "2026-11-05T05:17:10Z", "facts": [fact]}


def build():
    return extract(json.loads(PIN.read_text(encoding="utf8")), json.loads(PARENT.read_text(encoding="utf8")))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = json.dumps(build(), indent=2, ensure_ascii=False) + "\n"
    if args.check:
        if OUT.read_text(encoding="utf8") != expected:
            raise SystemExit("Sentinel spawn context differs from its primaries")
    else:
        OUT.write_text(expected, encoding="utf8")
    print("1 exact Basilisk Sentinel encounter fact")
