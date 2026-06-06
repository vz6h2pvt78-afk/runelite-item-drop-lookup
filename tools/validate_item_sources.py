# Read-only validator for non-NPC source data.
#
# Validates the curated item_sources.json and the generated wiki_shop_sources.json
# (if present) against the sourceType taxonomy (see docs/source-types.md). It only
# READS the files and prints a report; it never writes or edits them.
#
# Checks per file:
#   - sourceType is one of the allowed taxonomy values
#   - itemName is non-blank
#   - sourceName is non-blank
#   - duplicate rows (same itemName + sourceName + sourceType)
#   - malformed records (not an object / missing expected keys / wrong types)
#   - summary counts by sourceType
#
# Exit code is non-zero if any errors (not just warnings) are found, so it can be
# used in a pre-commit / CI check later.

import json
import sys
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
RESOURCES = PROJECT_ROOT / "src" / "main" / "resources"

CURATED_FILE = RESOURCES / "item_sources.json"
GENERATED_FILE = RESOURCES / "wiki_shop_sources.json"

ALLOWED_SOURCE_TYPES = {
    "Shop",
    "Reward Shop",
    "Minigame",
    "Skilling",
    "Clue",
    "Quest",
    "Spawn",
    "Reward Chest",
    "Other",
}

EXPECTED_KEYS = {"itemName", "sourceType", "sourceName", "location", "cost", "notes"}


def load(path: Path):
    if not path.exists():
        return None, [f"file not present: {path.name}"]
    try:
        with path.open("r", encoding="utf-8") as file:
            data = json.load(file)
    except Exception as exc:
        return None, [f"could not parse {path.name}: {type(exc).__name__}: {exc}"]
    if not isinstance(data, list):
        return None, [f"{path.name}: top-level JSON is not a list"]
    return data, []


def validate_file(path: Path):
    print(f"\n=== {path.name} ===")
    data, load_errors = load(path)
    errors = list(load_errors)
    warnings = []

    if data is None:
        for message in load_errors:
            print(f"  [skip] {message}")
        return len(load_errors), 0 if load_errors and "not present" in load_errors[0] else len(load_errors)

    type_counts = {}
    seen_rows = set()

    for index, record in enumerate(data):
        where = f"row {index}"
        if not isinstance(record, dict):
            errors.append(f"{where}: not a JSON object")
            continue

        missing = EXPECTED_KEYS - set(record.keys())
        extra = set(record.keys()) - EXPECTED_KEYS
        if missing:
            errors.append(f"{where}: missing keys {sorted(missing)}")
        if extra:
            warnings.append(f"{where}: unexpected keys {sorted(extra)}")

        item_name = str(record.get("itemName", "") or "").strip()
        source_name = str(record.get("sourceName", "") or "").strip()
        source_type = str(record.get("sourceType", "") or "").strip()

        if not item_name:
            errors.append(f"{where}: blank itemName")
        if not source_name:
            errors.append(f"{where}: blank sourceName")
        if source_type not in ALLOWED_SOURCE_TYPES:
            errors.append(f"{where}: invalid sourceType {source_type!r} "
                          f"(allowed: {sorted(ALLOWED_SOURCE_TYPES)})")

        type_counts[source_type] = type_counts.get(source_type, 0) + 1

        row_key = (item_name.lower(), source_name.lower(), source_type.lower())
        if row_key in seen_rows:
            warnings.append(f"{where}: duplicate row (itemName+sourceName+sourceType) "
                            f"-> {item_name} / {source_name} / {source_type}")
        else:
            seen_rows.add(row_key)

    print(f"  records: {len(data)}")
    print("  counts by sourceType:")
    for source_type in sorted(type_counts):
        marker = "" if source_type in ALLOWED_SOURCE_TYPES else "  <-- NOT IN TAXONOMY"
        print(f"    {source_type or '(blank)'}: {type_counts[source_type]}{marker}")

    if warnings:
        print(f"  warnings: {len(warnings)}")
        for message in warnings[:20]:
            print(f"    [warn] {message}")
        if len(warnings) > 20:
            print(f"    ... and {len(warnings) - 20} more warnings")
    else:
        print("  warnings: 0")

    if errors:
        print(f"  errors: {len(errors)}")
        for message in errors[:30]:
            print(f"    [error] {message}")
        if len(errors) > 30:
            print(f"    ... and {len(errors) - 30} more errors")
    else:
        print("  errors: 0")

    return len(errors), len(warnings)


def main() -> None:
    print("Validating non-NPC source data (read-only)...")

    total_errors = 0
    total_warnings = 0

    for path in (CURATED_FILE, GENERATED_FILE):
        if path is GENERATED_FILE and not path.exists():
            print(f"\n=== {path.name} ===")
            print("  [skip] generated file not present (run tools/build_shop_sources.py first)")
            continue
        errors, warnings = validate_file(path)
        total_errors += errors
        total_warnings += warnings

    print(f"\nTOTAL: {total_errors} error(s), {total_warnings} warning(s)")
    sys.exit(1 if total_errors else 0)


if __name__ == "__main__":
    main()
