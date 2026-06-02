import json
import re
import urllib.parse
import urllib.request
from pathlib import Path


API_URL = "https://oldschool.runescape.wiki/api.php"

HEADERS = {
    "User-Agent": "ItemDropLookupPluginDataBuilder/0.1 local-dev"
}

TEST_MONSTERS = [
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

        # Newer MediaWiki format.
        slots = revision.get("slots", {})
        main_slot = slots.get("main", {})
        if "*" in main_slot:
            return main_slot["*"]

        # Older fallback format.
        if "*" in revision:
            return revision["*"]

    return ""


def extract_drop_related_lines(wikitext: str) -> list[str]:
    lines = wikitext.splitlines()
    useful_lines = []

    keywords = [
        "drop",
        "drops",
        "rarity",
        "quantity",
        "coins",
        "gemw",
        "smw",
        "DropTable",
        "DropsLine",
        "DropsTable",
        "MonsterDrop",
    ]

    for line in lines:
        stripped = line.strip()

        if not stripped:
            continue

        if any(keyword.lower() in stripped.lower() for keyword in keywords):
            useful_lines.append(stripped)

    return useful_lines


def extract_template_names(wikitext: str) -> list[str]:
    # Rough template finder: grabs the first word after {{
    names = set()

    for match in re.finditer(r"\{\{\s*([A-Za-z0-9 _/-]+)", wikitext):
        name = match.group(1).strip()
        if name:
            names.add(name)

    return sorted(names, key=str.lower)


def print_section(title: str) -> None:
    print()
    print("=" * 80)
    print(title)
    print("=" * 80)


def main() -> None:
    for monster in TEST_MONSTERS:
        print_section(monster)

        try:
            wikitext = fetch_wikitext(monster)
        except Exception as ex:
            print(f"FAILED: {ex}")
            continue

        print(f"Fetched {len(wikitext):,} characters of wikitext.")

        templates = extract_template_names(wikitext)
        dropish_templates = [
            template for template in templates
            if "drop" in template.lower()
        ]

        print()
        print("Drop-ish templates found:")
        if dropish_templates:
            for template in dropish_templates:
                print(f"  - {template}")
        else:
            print("  None found.")

        print()
        print("First 80 drop-related lines:")
        useful_lines = extract_drop_related_lines(wikitext)

        if not useful_lines:
            print("  No obvious drop-related lines found.")
            continue

        for line in useful_lines[:80]:
            print(line)


if __name__ == "__main__":
    main()