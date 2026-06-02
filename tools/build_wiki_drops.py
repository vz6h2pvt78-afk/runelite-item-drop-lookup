import json
import re
import urllib.parse
import urllib.request
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]

API_URL = "https://oldschool.runescape.wiki/api.php"

OUTPUT_FILE = PROJECT_ROOT / "src" / "main" / "resources" / "wiki_drops_test.json"

HEADERS = {
    "User-Agent": "ItemDropLookupPluginDataBuilder/0.1 local-dev"
}

MONSTER_PAGES = [
    "Abyssal demon",
    "Spiritual mage",
    "Ancient Wyvern",
]


def fetch_wikitext(page_title: str) -> str:
    params = {
        "action": "query",
        "format": "json",
        "prop": "revisions",
        "titles": page_title,
        "rvprop": "content",
        "rvslots": "main",
    }

    url = API_URL + "?" + urllib.parse.urlencode(params)
    request = urllib.request.Request(url, headers=HEADERS)

    with urllib.request.urlopen(request, timeout=30) as response:
        data = json.loads(response.read().decode("utf-8"))

    pages = data.get("query", {}).get("pages", {})

    for page in pages.values():
        if "missing" in page:
            raise ValueError(f"Page not found: {page_title}")

        revisions = page.get("revisions", [])

        if not revisions:
            return ""

        revision = revisions[0]
        slots = revision.get("slots", {})
        main_slot = slots.get("main", {})

        if "*" in main_slot:
            return main_slot["*"]

        if "*" in revision:
            return revision["*"]

    return ""


def clean_template_value(value: str) -> str:
    value = value.strip()

    value = re.sub(r"<ref[^>]*>.*?</ref>", "", value, flags=re.IGNORECASE | re.DOTALL)
    value = re.sub(r"<ref[^/]*/>", "", value, flags=re.IGNORECASE)

    value = re.sub(r"\{\{[^{}]*}}", "", value)

    value = re.sub(r"\[\[[^|\]]+\|([^\]]+)]]", r"\1", value)
    value = re.sub(r"\[\[([^\]]+)]]", r"\1", value)

    return value.strip()


def parse_template_parameters(template_body: str) -> dict[str, str]:
    params = {}

    parts = template_body.split("|")

    for part in parts[1:]:
        if "=" not in part:
            continue

        key, value = part.split("=", 1)
        params[key.strip().lower()] = clean_template_value(value)

    return params


def find_drop_records_from_wikitext(monster_name: str, wikitext: str) -> list[dict]:
    records = []

    for line in wikitext.splitlines():
        stripped = line.strip()

        if not stripped.startswith("{{DropsLine|"):
            continue

        record = parse_drop_line(monster_name, stripped)

        if record is not None:
            records.append(record)

    return records


def parse_drop_line(monster_name: str, template_text: str) -> dict | None:
    body = template_text.strip()

    if body.startswith("{{"):
        body = body[2:]

    if body.endswith("}}"):
        body = body[:-2]

    params = parse_template_parameters(body)

    item_name = params.get("name", "")
    quantity = params.get("quantity", "")
    rarity = params.get("rarity", "")

    if not item_name:
        return None

    return {
        "itemName": item_name,
        "monsterName": monster_name,
        "quantity": quantity,
        "dropRate": rarity,
        "notes": "",
    }


def dedupe_records(records: list[dict]) -> list[dict]:
    seen = set()
    deduped = []

    for record in records:
        key = (
            record.get("itemName", "").lower(),
            record.get("monsterName", "").lower(),
            record.get("quantity", "").lower(),
            record.get("dropRate", "").lower(),
        )

        if key in seen:
            continue

        seen.add(key)
        deduped.append(record)

    return deduped


def build_records() -> list[dict]:
    records = []

    for monster_name in MONSTER_PAGES:
        print(f"Fetching {monster_name}...")

        try:
            wikitext = fetch_wikitext(monster_name)
        except Exception as ex:
            print(f"  FAILED: {ex}")
            continue

        monster_records = find_drop_records_from_wikitext(monster_name, wikitext)
        print(f"  Found {len(monster_records)} simple DropsLine records.")

        records.extend(monster_records)

    records = dedupe_records(records)

    records.sort(key=lambda record: (
        record["itemName"].lower(),
        record["monsterName"].lower(),
        record["dropRate"].lower(),
    ))

    return records


def main() -> None:
    records = build_records()

    OUTPUT_FILE.parent.mkdir(parents=True, exist_ok=True)

    with OUTPUT_FILE.open("w", encoding="utf-8") as file:
        json.dump(records, file, indent=2, ensure_ascii=False)

    print(f"Wrote {len(records)} wiki drop records to {OUTPUT_FILE}")


if __name__ == "__main__":
    main()