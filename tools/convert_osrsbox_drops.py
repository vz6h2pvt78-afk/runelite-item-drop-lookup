# NOTE: This is an offline OSRSBox converter. Its output, drops.json, is NOT the
# runtime drop dataset and is not loaded by the plugin. The plugin loads
# wiki_monster_drops.json (produced by build_wiki_drops.py). Do not use drops.json
# when debugging plugin search results.

import json
from fractions import Fraction
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]

SOURCE_FILE = PROJECT_ROOT / "tools" / "monsters-complete.json"
OUTPUT_FILE = PROJECT_ROOT / "src" / "main" / "resources" / "drops.json"


def get_first(record: dict, *keys: str, default: str = "") -> str:
    for key in keys:
        value = record.get(key)
        if value is not None:
            return str(value)
    return default


def format_rarity(value: str) -> str:
    if value is None:
        return ""

    text = str(value).strip()

    if not text:
        return ""

    # Already human-readable.
    if "/" in text or text.lower() in {"always", "varies", "unknown"}:
        return text

    try:
        number = float(text)

        if number <= 0:
            return text

        # OSRSBox often stores rarity as probability, like 0.0078125 for 1/128.
        if number < 1:
            fraction = Fraction(number).limit_denominator(1000000)

            if fraction.numerator == 1:
                return f"1/{fraction.denominator}"

            return f"{fraction.numerator}/{fraction.denominator}"

        # Some data may already be a denominator, like 128.
        if number.is_integer():
            return f"1/{int(number)}"

        return text
    except ValueError:
        return text


def clean_quantity(value: str) -> str:
    text = str(value).strip()

    if not text:
        return ""

    return text


def main() -> None:
    if not SOURCE_FILE.exists():
        raise FileNotFoundError(
            f"Missing source file: {SOURCE_FILE}\n"
            "Place the OSRSBox monster database JSON there as monsters-complete.json."
        )

    with SOURCE_FILE.open("r", encoding="utf-8") as file:
        monsters = json.load(file)

    output_rows = []
    seen_rows = set()

    if isinstance(monsters, dict):
        monster_records = monsters.values()
    else:
        monster_records = monsters

    for monster in monster_records:
        monster_name = get_first(monster, "name", "monsterName", default="Unknown monster").strip()
        drops = monster.get("drops") or []

        for drop in drops:
            item_name = get_first(drop, "name", "itemName").strip()
            if not item_name:
                continue

            quantity = clean_quantity(get_first(drop, "quantity", "qty", default=""))
            rarity = format_rarity(get_first(drop, "rarity", "dropRate", "rate", default=""))

            requirements = drop.get("drop_requirements") or drop.get("requirements") or ""

            if isinstance(requirements, list):
                notes = "; ".join(str(requirement) for requirement in requirements)
            else:
                notes = str(requirements).strip() if requirements else ""

            row = {
                "itemName": item_name.lower(),
                "monsterName": monster_name,
                "quantity": quantity,
                "dropRate": rarity,
                "notes": notes,
            }

            # Collapse exact duplicates from monster variants.
            row_key = (
                row["itemName"],
                row["monsterName"].lower(),
                row["quantity"],
                row["dropRate"],
                row["notes"],
            )

            if row_key in seen_rows:
                continue

            seen_rows.add(row_key)
            output_rows.append(row)

    output_rows.sort(key=lambda row: (row["itemName"], row["monsterName"]))

    OUTPUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT_FILE.open("w", encoding="utf-8") as file:
        json.dump(output_rows, file, indent=2, ensure_ascii=False)

    print(f"Wrote {len(output_rows)} cleaned drop records to {OUTPUT_FILE}")


if __name__ == "__main__":
    main()