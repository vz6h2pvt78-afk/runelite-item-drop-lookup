"""Offline build-label/data guard. Never obtains Git state at plugin runtime."""
import argparse
from hashlib import sha256
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
METADATA = RESOURCES / "lookup_build.properties"
REQUIRED = {"access_requirements.json", "acquisition_events.json", "monster_locations.json", "wiki_monster_drops.json",
            "item_sources.json", "wiki_shop_sources.json", "wiki_ground_sources.json", "wiki_gathering_sources.json"}


def dataset_digest(resources=RESOURCES):
    paths = sorted(resources.glob("*.json"))
    if not REQUIRED <= {p.name for p in paths}:
        raise ValueError("Required datasets missing")
    digest = sha256()
    for path in paths:
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        canonical = json.dumps(data, sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        digest.update(path.name.encode() + b"\0" + canonical + b"\n")
    return digest.hexdigest()


def expected(build, resources=RESOURCES):
    if not re.fullmatch(r"[a-zA-Z0-9][a-zA-Z0-9._/-]{1,100}", build):
        raise ValueError("Invalid build label")
    result = {"build": build, "datasetSha256": dataset_digest(resources)}
    for key, name in [("accessSchema", "access_requirements"), ("acquisitionSchema", "acquisition_events"),
                      ("locationsSchema", "monster_locations")]:
        schema = json.loads((resources / (name + ".json")).read_text(encoding="utf-8-sig"))["schemaVersion"]
        if schema != 1:
            raise ValueError("Report schema summary needs review")
        result[key] = str(schema)
    return result


def check(path=METADATA, resources=RESOURCES, build=None):
    lines = path.read_text(encoding="ascii").splitlines()
    values = dict(line.split("=", 1) for line in lines if line and not line.startswith("#"))
    if len(values) != len([line for line in lines if line and not line.startswith("#")]):
        raise ValueError("Duplicate metadata fields")
    if build is not None and values.get("build") != build:
        raise ValueError("Checkpoint/tag label mismatch")
    if values != expected(values.get("build", ""), resources):
        raise ValueError("Build metadata does not match bundled datasets")
    return values


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", metavar="BUILD_LABEL", help="Deliberately update build label and digest")
    parser.add_argument("--expected-build")
    args = parser.parse_args()
    if args.write:
        METADATA.write_text("".join(f"{k}={v}\n" for k, v in expected(args.write).items()), encoding="ascii")
    print(json.dumps(check(build=args.expected_build)))
