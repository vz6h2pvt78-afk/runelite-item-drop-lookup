import ast
import json
import operator
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]

API_URL = "https://oldschool.runescape.wiki/api.php"

OUTPUT_FILE = PROJECT_ROOT / "src" / "main" / "resources" / "wiki_drops_test.json"

HEADERS = {
    "User-Agent": "ItemDropLookupPluginDataBuilder/0.1 local-dev"
}

REQUEST_DELAY_SECONDS = 0.2

MONSTER_PAGES = [
    "Abyssal demon",
    "Spiritual mage",
    "Ancient Wyvern",

    "General Graardor",
    "Kree'arra",
    "K'ril Tsutsaroth",
    "Commander Zilyana",

    "Kraken",
    "Thermonuclear smoke devil",
    "Gargoyle",
    "Nechryael",
    "Dust devil",

    "Dagannoth Rex",
    "Dagannoth Prime",
    "Dagannoth Supreme",

    "Vorkath",
    "Zulrah",
    "Alchemical Hydra",
    "Cerberus",

    "Demonic gorilla",
    "Lizardman shaman",
    "Cave horror",
    "Basilisk Knight",
    "Vyrewatch Sentinel",

    "Tormented Demon",
    "Araxxor",
    "Muspah",
    "The Nightmare",
    "Phosani's Nightmare",

    "King Black Dragon",
    "Kalphite Queen",
    "Corporeal Beast",
    "Chaos Elemental",
    "Giant Mole",

    "Greater Nechryael",
    "Smoke devil",
    "Abyssal Sire",
    "Skotizo",
    "Sarachnis",
]


def fetch_wikitext(page_title: str) -> str:
    params = {
        "action": "query",
        "format": "json",
        "prop": "revisions",
        "titles": page_title,
        "rvprop": "content",
        "rvslots": "main",
        "redirects": "1",
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


def extract_outer_template(line: str) -> str:
    start = line.find("{{DropsLine|")

    if start < 0:
        return ""

    template_depth = 0
    index = start

    while index < len(line):
        two_chars = line[index:index + 2]

        if two_chars == "{{":
            template_depth += 1
            index += 2
            continue

        if two_chars == "}}":
            template_depth -= 1
            index += 2

            if template_depth == 0:
                return line[start:index]

            continue

        index += 1

    return ""


def split_template_parts(template_body: str) -> list[str]:
    parts = []
    current = []

    template_depth = 0
    link_depth = 0
    index = 0

    while index < len(template_body):
        two_chars = template_body[index:index + 2]
        char = template_body[index]

        if two_chars == "{{":
            template_depth += 1
            current.append(two_chars)
            index += 2
            continue

        if two_chars == "}}":
            if template_depth > 0:
                template_depth -= 1
            current.append(two_chars)
            index += 2
            continue

        if two_chars == "[[":
            link_depth += 1
            current.append(two_chars)
            index += 2
            continue

        if two_chars == "]]":
            if link_depth > 0:
                link_depth -= 1
            current.append(two_chars)
            index += 2
            continue

        if char == "|" and template_depth == 0 and link_depth == 0:
            parts.append("".join(current))
            current = []
            index += 1
            continue

        current.append(char)
        index += 1

    parts.append("".join(current))

    return parts


def remove_html_comments(value: str) -> str:
    return re.sub(r"<!--.*?-->", "", value, flags=re.DOTALL).strip()


def remove_refs(value: str) -> str:
    value = re.sub(r"<ref[^>]*>.*?</ref>", "", value, flags=re.IGNORECASE | re.DOTALL)
    value = re.sub(r"<ref[^/]*/>", "", value, flags=re.IGNORECASE)
    return value.strip()


def convert_wiki_links(value: str) -> str:
    value = re.sub(r"\[\[[^|\]]+\|([^\]]+)]]", r"\1", value)
    value = re.sub(r"\[\[([^\]]+)]]", r"\1", value)
    return value.strip()


def safe_eval_math_expression(expression: str) -> float:
    allowed_operators = {
        ast.Add: operator.add,
        ast.Sub: operator.sub,
        ast.Mult: operator.mul,
        ast.Div: operator.truediv,
        ast.USub: operator.neg,
    }

    def eval_node(node):
        if isinstance(node, ast.Expression):
            return eval_node(node.body)

        if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
            return node.value

        if isinstance(node, ast.BinOp) and type(node.op) in allowed_operators:
            return allowed_operators[type(node.op)](
                eval_node(node.left),
                eval_node(node.right)
            )

        if isinstance(node, ast.UnaryOp) and type(node.op) in allowed_operators:
            return allowed_operators[type(node.op)](eval_node(node.operand))

        raise ValueError("Unsupported expression")

    if not re.fullmatch(r"[0-9+\-*/().\s]+", expression):
        raise ValueError("Unsafe or unsupported expression")

    parsed = ast.parse(expression, mode="eval")
    return float(eval_node(parsed))


def format_number(value: float) -> str:
    if value.is_integer():
        return str(int(value))

    return str(round(value, 4)).rstrip("0").rstrip(".")


def replace_expr_templates(value: str) -> str:
    while "{{#expr:" in value:
        start = value.find("{{#expr:")
        depth = 0
        end = -1
        index = start

        while index < len(value):
            two_chars = value[index:index + 2]

            if two_chars == "{{":
                depth += 1
                index += 2
                continue

            if two_chars == "}}":
                depth -= 1
                index += 2

                if depth == 0:
                    end = index
                    break

                continue

            index += 1

        if end == -1:
            return value

        full_template = value[start:end]
        expression = full_template[len("{{#expr:"):-2].strip()

        # Page variables need a separate parser later.
        if "#var" in expression.lower():
            return value

        round_places = None
        round_match = re.match(r"^(.*?)\s+round\s+(\d+)$", expression, flags=re.IGNORECASE)

        if round_match:
            expression = round_match.group(1).strip()
            round_places = int(round_match.group(2))

        try:
            result = safe_eval_math_expression(expression)

            if round_places is not None:
                result = round(result, round_places)

            replacement = format_number(result)
        except Exception:
            return value

        value = value[:start] + replacement + value[end:]

    return value


def replace_brimstone_rarity_templates(value: str) -> str:
    pattern = re.compile(
        r"\{\{Brimstone rarity\|([^|}]+)(?:\|[^}]*)?}}",
        flags=re.IGNORECASE
    )

    return pattern.sub(lambda match: "1/" + match.group(1).strip(), value)


def strip_simple_templates(value: str) -> str:
    previous = None

    while previous != value:
        previous = value
        value = re.sub(r"\{\{[^{}]*}}", "", value)

    return value.strip()


def clean_template_value(value: str, *, preserve_unsupported_templates: bool = False) -> str:
    value = value.strip()

    value = remove_html_comments(value)
    value = remove_refs(value)
    value = convert_wiki_links(value)
    value = replace_brimstone_rarity_templates(value)
    value = replace_expr_templates(value)

    if not preserve_unsupported_templates:
        value = strip_simple_templates(value)

    value = value.replace("&nbsp;", " ")
    value = re.sub(r"\s+", " ", value)

    return value.strip()


def parse_template_parameters(template_body: str) -> dict[str, str]:
    params = {}

    parts = split_template_parts(template_body)

    for part in parts[1:]:
        if "=" not in part:
            continue

        key, value = part.split("=", 1)
        key = key.strip().lower()

        preserve = key == "rarity"

        params[key] = clean_template_value(
            value,
            preserve_unsupported_templates=preserve
        )

    return params


def find_drop_records_from_wikitext(monster_name: str, wikitext: str) -> tuple[list[dict], list[dict]]:
    records = []
    skipped = []

    for line_number, line in enumerate(wikitext.splitlines(), start=1):
        stripped = line.strip()

        if not stripped.startswith("{{DropsLine|"):
            continue

        template_text = extract_outer_template(stripped)

        if not template_text:
            skipped.append({
                "monsterName": monster_name,
                "lineNumber": line_number,
                "reason": "could not extract outer DropsLine template",
                "line": stripped,
            })
            continue

        record, skip_reason = parse_drop_line(monster_name, template_text)

        if record is not None:
            records.append(record)
        else:
            skipped.append({
                "monsterName": monster_name,
                "lineNumber": line_number,
                "reason": skip_reason,
                "line": stripped,
            })

    return records, skipped


def parse_drop_line(monster_name: str, template_text: str) -> tuple[dict | None, str]:
    body = template_text.strip()

    if body.startswith("{{"):
        body = body[2:]

    if body.endswith("}}"):
        body = body[:-2]

    params = parse_template_parameters(body)

    item_name = clean_item_name(params.get("name", ""))
    quantity = clean_quantity(params.get("quantity", ""))
    rarity = clean_drop_rate(params.get("rarity", ""))

    reason = validate_record(item_name, quantity, rarity)

    if reason:
        return None, reason

    return {
        "itemName": item_name,
        "monsterName": monster_name,
        "quantity": quantity,
        "dropRate": rarity,
        "notes": "",
    }, ""


def clean_item_name(item_name: str) -> str:
    item_name = clean_template_value(item_name)
    item_name = item_name.replace("'''", "")
    item_name = item_name.replace("''", "")
    item_name = re.sub(r"\s+", " ", item_name)

    return item_name.strip()


def clean_quantity(quantity: str) -> str:
    quantity = clean_template_value(quantity)
    quantity = remove_html_comments(quantity)
    quantity = re.sub(r"\s+", " ", quantity)

    return quantity.strip()


def clean_drop_rate(drop_rate: str) -> str:
    drop_rate = clean_template_value(
        drop_rate,
        preserve_unsupported_templates=True
    )
    drop_rate = remove_html_comments(drop_rate)
    drop_rate = re.sub(r"\s+", " ", drop_rate)

    return drop_rate.strip()


def validate_record(item_name: str, quantity: str, rarity: str) -> str:
    if not item_name:
        return "missing item name"

    if contains_raw_wiki_markup(item_name):
        return "raw wiki markup in item name"

    if contains_raw_wiki_markup(rarity):
        return "raw wiki markup in rarity"

    if item_name.endswith("}}") or "}}" in item_name or "{{" in item_name:
        return "broken template leaked into item name"

    if rarity.endswith("}}") or "{{" in rarity or "}}" in rarity:
        return "broken template leaked into rarity"

    if looks_like_reference_garbage(item_name):
        return "reference/template garbage item name"

    if rarity == "1/":
        return "incomplete rarity"

    if rarity.endswith("/"):
        return "incomplete rarity"

    return ""


def contains_raw_wiki_markup(value: str) -> bool:
    raw_tokens = [
        "{{",
        "}}",
        "[[",
        "]]",
        "<ref",
        "</ref>",
        "#expr:",
    ]

    return any(token in value for token in raw_tokens)


def looks_like_reference_garbage(item_name: str) -> bool:
    lowered = item_name.lower().strip()

    garbage_words = [
        "drop rates",
        "droprates",
        "confirmed droprates",
        "ash tweet",
        "namedref",
        "cite",
        "dv2",
        "sotd",
        "sara",
        "spear",
        "dagger",
        "uniques",
        "armour+rings",
        "gs shard",
        "piece",
        "shards",
        "muta",
        "steam bstaff",
        "corprateash",
        "forthos dungeon droprates",
        "potions rate",
    ]

    if lowered in garbage_words:
        return True

    if lowered.endswith("}}"):
        return True

    if len(lowered) <= 3 and lowered not in {"nid"}:
        return True

    return False


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


def build_records() -> tuple[list[dict], list[dict]]:
    records = []
    skipped_records = []
    failed_pages = []

    for index, monster_name in enumerate(MONSTER_PAGES, start=1):
        print(f"[{index}/{len(MONSTER_PAGES)}] Fetching {monster_name}...")

        try:
            wikitext = fetch_wikitext(monster_name)
        except Exception as ex:
            print(f"  FAILED: {ex}")
            failed_pages.append({
                "monsterName": monster_name,
                "error": str(ex),
            })
            continue

        monster_records, skipped = find_drop_records_from_wikitext(monster_name, wikitext)
        print(f"  Found {len(monster_records)} valid simple DropsLine records.")
        print(f"  Skipped {len(skipped)} suspicious DropsLine records.")

        records.extend(monster_records)
        skipped_records.extend(skipped)

        time.sleep(REQUEST_DELAY_SECONDS)

    before_dedupe = len(records)
    records = dedupe_records(records)
    after_dedupe = len(records)

    print()
    print(f"Records before dedupe: {before_dedupe}")
    print(f"Records after dedupe:  {after_dedupe}")
    print(f"Removed by dedupe:     {before_dedupe - after_dedupe}")

    if failed_pages:
        print()
        print("Failed pages:")
        for failed_page in failed_pages:
            print(f"  - {failed_page['monsterName']}: {failed_page['error']}")

    return records, skipped_records


def write_skipped_report(skipped_records: list[dict]) -> None:
    report_file = OUTPUT_FILE.with_name("wiki_drops_skipped_report.json")

    with report_file.open("w", encoding="utf-8") as file:
        json.dump(skipped_records, file, indent=2, ensure_ascii=False)

    print(f"Wrote {len(skipped_records)} skipped-record reports to {report_file}")


def main() -> None:
    records, skipped_records = build_records()

    records.sort(key=lambda record: (
        record["itemName"].lower(),
        record["monsterName"].lower(),
        record["dropRate"].lower(),
    ))

    OUTPUT_FILE.parent.mkdir(parents=True, exist_ok=True)

    with OUTPUT_FILE.open("w", encoding="utf-8") as file:
        json.dump(records, file, indent=2, ensure_ascii=False)

    write_skipped_report(skipped_records)

    print()
    print(f"Wrote {len(records)} wiki drop records to {OUTPUT_FILE}")


if __name__ == "__main__":
    main()