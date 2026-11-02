"""Read-only validation of bundled monster locations and drop associations."""
import argparse
from collections import Counter
import json
from pathlib import Path
import sys

from build_monster_locations import ROOT, RESOURCE, drop_key, norm


def validate(data, drops):
    errors = []
    def require(condition, message):
        if not condition:
            errors.append(message)
    require(data.get("schemaVersion") == 1, "Unsupported schema version")
    locations = data.get("locations", [])
    bindings = data.get("dropBindings", [])
    require(bool(locations), "Dataset contains no locations")
    by_id = {}
    known_regions = {"Asgarnia", "Desert", "Fremennik", "Kandarin", "Karamja", "Kourend", "Misthalin", "Morytania", "Tirannwn", "Varlamore", "Wilderness"}
    for row in locations:
        prefix = f"{row.get('monsterName')} / {row.get('location')}"
        for key in ("id", "monsterName", "sourcePage", "location", "sourceUrl", "wildernessEvidence"):
            require(isinstance(row.get(key), str) and bool(row[key].strip()), f"{prefix}: invalid {key}")
        require(row.get("id") not in by_id, f"{prefix}: duplicate id")
        by_id[row.get("id")] = row
        require(row.get("wilderness") in ("YES", "NO", "UNKNOWN"), f"{prefix}: invalid Wilderness state")
        require(type(row.get("members")) in (bool, type(None)), f"{prefix}: invalid membership")
        require(type(row.get("available")) is bool, f"{prefix}: invalid availability")
        require(type(row.get("revisionId")) is int and row["revisionId"] > 0, f"{prefix}: missing revision provenance")
        require(isinstance(row.get("levels"), list) and all(type(v) is int and v > 0 for v in row["levels"]), f"{prefix}: invalid levels")
        require(isinstance(row.get("leagueRegions"), list) and set(row["leagueRegions"]) <= known_regions, f"{prefix}: invalid League regions")
        require(isinstance(row.get("dropVersions"), list) and all(isinstance(v, str) and v.strip() for v in row["dropVersions"]), f"{prefix}: invalid drop versions")
        require(row.get("spawns") is None or (type(row["spawns"]) is int and row["spawns"] >= 0), f"{prefix}: invalid spawn count")
        require(row.get("sourceUrl", "").startswith("https://oldschool.runescape.wiki/w/"), f"{prefix}: invalid source URL")
        for field in ("location", "notes", "requirements", "levelsText"):
            value = row.get(field, "")
            require(isinstance(value, str) and not any(marker in value for marker in ("{{", "}}", "[[", "]]", "<ref", "()")), f"{prefix}: residual markup in {field}")
    drop_keys = {drop_key(d) for d in drops}
    seen = set()
    for binding in bindings:
        key = drop_key(binding)
        require(key in drop_keys, f"Unknown existing drop signature: {key}")
        require(key not in seen, f"Duplicate binding signature, union required: {key}")
        seen.add(key)
        require(bool(binding.get("locationIds")), f"Empty verified binding: {key}")
        for identity in binding.get("locationIds", []):
            row = by_id.get(identity)
            require(row is not None, f"Dangling location id: {identity}")
            if row:
                require(norm(row["monsterName"]) == norm(binding["monsterName"]), f"Cross-monster variant binding: {key}")
    summary = data.get("summary", {})
    require(summary.get("locationRecords") == len(locations), "Location count differs from report")
    require(summary.get("verifiedDropBindings") == len(bindings), "Binding count differs from report")
    require(summary.get("wildernessCounts") == dict(Counter(r.get("wilderness") for r in locations)), "Wilderness counts differ from report")
    # Product acceptance examples and known dangerous classification exceptions.
    for monster, location, wilderness, members in (
        ("Fire giant", "Deep Wilderness Dungeon", "YES", True),
        ("Goblin", "Lumbridge", "NO", False),
        ("King Black Dragon", "King Black Dragon Lair (Wilderness)", "NO", True),
        ("Rat", "Ferox Enclave", "NO", False),
        ("Deadly red spider", "Edgeville Dungeon", "YES", True),
        ("Skeleton", "Edgeville Dungeon", "NO", False),
    ):
        matches = [r for r in locations if r["monsterName"] == monster and r["location"] == location]
        require(bool(matches), f"Missing acceptance example: {monster} / {location}")
        require(all(r["wilderness"] == wilderness and r["members"] == members for r in matches), f"Incorrect acceptance classification: {monster} / {location}")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("path", nargs="?", type=Path, default=RESOURCE)
    args = parser.parse_args()
    try:
        data = json.loads(args.path.read_text(encoding="utf-8"))
        drops = json.loads((ROOT / "src/main/resources/wiki_monster_drops.json").read_text(encoding="utf-8"))
        errors = validate(data, drops)
    except (ValueError, OSError, KeyError, TypeError) as exc:
        print(f"Invalid dataset: {exc}")
        return 1
    for error in errors[:50]:
        print("ERROR:", error)
    print(f"Validated {len(data['locations'])} locations and {len(data['dropBindings'])} drop bindings: {len(errors)} error(s)")
    print("Coverage:", json.dumps(data["summary"], sort_keys=True))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
