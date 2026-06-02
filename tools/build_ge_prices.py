import json
import urllib.request
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]

MAPPING_URL = "https://prices.runescape.wiki/api/v1/osrs/mapping"
LATEST_URL = "https://prices.runescape.wiki/api/v1/osrs/latest"

OUTPUT_FILE = PROJECT_ROOT / "src" / "main" / "resources" / "ge_prices.json"

HEADERS = {
    "User-Agent": "ItemDropLookupPluginDataBuilder/0.1 local-dev"
}


def fetch_json(url: str) -> dict | list:
    request = urllib.request.Request(url, headers=HEADERS)

    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def format_price(value) -> str:
    if value is None:
        return ""

    return str(value)


def main() -> None:
    print("Fetching OSRS Wiki item mapping...")
    mapping = fetch_json(MAPPING_URL)

    print("Fetching latest OSRS Wiki GE prices...")
    latest_response = fetch_json(LATEST_URL)
    latest_prices = latest_response.get("data", {})

    records = []

    for item in mapping:
        item_id = str(item.get("id", ""))
        item_name = item.get("name", "")

        if not item_id or not item_name:
            continue

        price_data = latest_prices.get(item_id, {})

        high_price = price_data.get("high")
        low_price = price_data.get("low")

        selected_price = high_price if high_price is not None else low_price

        records.append({
            "itemName": item_name,
            "price": format_price(selected_price),
            "highAlch": format_price(item.get("highalch")),
            "tradeable": selected_price is not None,
            "notes": "Generated from OSRS Wiki real-time price API."
        })

    records.sort(key=lambda record: record["itemName"].lower())

    OUTPUT_FILE.parent.mkdir(parents=True, exist_ok=True)

    with OUTPUT_FILE.open("w", encoding="utf-8") as file:
        json.dump(records, file, indent=2, ensure_ascii=False)

    print(f"Wrote {len(records)} price records to {OUTPUT_FILE}")


if __name__ == "__main__":
    main()