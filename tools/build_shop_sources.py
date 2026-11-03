# Shop / non-NPC source generator (SPIKE).
#
# Produces src/main/resources/wiki_shop_sources.json from OSRS Wiki shop pages,
# using the MediaWiki API only (same approach as tools/build_wiki_drops.py). It
# does NOT scrape arbitrary websites, and it does NOT overwrite the hand-curated
# item_sources.json.
#
# Item rows come from each shop page's RENDERED store table (action=parse), which
# supplies the listed purchase price. Location/members come from the
# {{Infobox Shop}} in the page wikitext.
#
# What it extracts:
#   - itemName   : the "Item" column of the rendered store table
#   - sourceName : the shop page title
#   - sourceType : "Reward Shop" when the page title is in the curated
#                  REWARD_SHOP_SOURCE_NAMES allowlist (reward/minigame/currency
#                  exchanges), otherwise exactly "Shop". The allowlist is an
#                  explicit, exact-match set -- no fuzzy/keyword classification --
#                  so a normal coin shop is never reclassified by accident.
#   - location   : best-effort from {{Infobox Shop|location=...}}, blank if absent
#   - cost       : the table's "Price sold at" value, formatted as "16,640 coins",
#                  with explicit currency from the price cell or matching table
#                  metadata. GE value never identifies the purchase currency.
#                  Unknown/conflicting currency, composite price or additional
#                  purchase costs leave cost BLANK; no partial total is invented.
#   - notes      : "Members shop." / "Default stock: X." / "Restock: Y." and, only
#                  when an explicit positive table delta supports it,
#                  "Cost increases as stock depletes."
#
# If shops cannot be parsed (e.g. network failure, or no store tables found), the
# script writes NOTHING and exits non-zero, rather than fabricating.

import json
import html as html_lib
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]

API_URL = "https://oldschool.runescape.wiki/api.php"

OUTPUT_FILE = PROJECT_ROOT / "src" / "main" / "resources" / "wiki_shop_sources.json"
REPORT_FILE = PROJECT_ROOT / "tools" / "data" / "wiki_shop_sources_report.json"

HEADERS = {
    "User-Agent": "ItemDropLookupPluginDataBuilder/0.1 local-dev"
}

REQUEST_DELAY_SECONDS = 0.08

SHOP_CATEGORY = "Category:Shops"

# Full run: inspect all discovered shop pages. Set to an int (e.g. 60) to cap for a
# quick reviewable spike.
MAX_SHOPS = None

SOURCE_TYPE_SHOP = "Shop"
SOURCE_TYPE_REWARD_SHOP = "Reward Shop"

# Curated allowlist of shop *page titles* that are actually reward / minigame /
# currency exchanges rather than coin shops. Pages whose title is in this set are
# generated with sourceType "Reward Shop" so the plugin's "Reward Shops" filter
# surfaces them, and they no longer pollute the "Shops" filter.
#
# This is an EXACT, explicit match against the wiki page title (which becomes the
# record's sourceName). There is deliberately no fuzzy or keyword matching: a name
# must appear here verbatim to be reclassified, so normal coin shops are never
# affected. To add a reward shop, add its exact page title below.
REWARD_SHOP_SOURCE_NAMES = frozenset({
    "Grace's Graceful Clothing",
    "Castle Wars Ticket Exchange",
    "Barbarian Assault Reward Shop",
    "Commander Connad",
    "Void Knights' Reward Options",
    "Prospector Percy's Nugget Shop",
    "Mahogany Homes Reward Shop",
    "Mining Guild Mineral Exchange",
    "Dusuri's Star Shop",
    "Giants' Foundry Reward Shop",
    "Soul Wars Reward Shop",
    "PvP Arena Rewards",
    "Slayer Rewards",
    "Alry the Angler's Angling Accessories",
    "Forestry Shop",
    "Mysterious Hallowed Goods",
    "Farmer Gricoller's Rewards",
    "Ranging Guild Ticket Exchange",
    "Brimhaven Agility Arena Ticket Exchange",
    "Agility Arena Store",
    "Speedrunning Reward Shop",
    "Events Reward Shop",
    "Dom Onion's Reward Shop",
    "Vale Research Exchange",
})


def classify_source_type(title: str) -> str:
    """Exact-match classification: reward shop allowlist, else a normal shop."""
    return SOURCE_TYPE_REWARD_SHOP if title in REWARD_SHOP_SOURCE_NAMES else SOURCE_TYPE_SHOP


# Narrow, explicit per-record corrections, keyed by (sourceName, itemName) exactly as
# they appear on the wiki. The generic parser reads the rendered store table's coin
# column, but some reward shops list a NON-coin currency (golden nuggets / Volcanic
# Mine reward points) that the parser misreads as coins. We hard-correct only those
# specific rows here -- type, cost, and/or notes -- rather than guessing.
#
# Deliberately limited to the Prospector outfit pieces from the two shops below; their
# other items (e.g. Petrified Pete's ores) are out of scope and left as parsed.
PROSPECTOR_PETE_NOTE = (
    "Bought with Volcanic Mine reward points. Pieces bought from Petrified Pete "
    "cannot be sold back to Prospector Percy for golden nuggets."
)

RECORD_OVERRIDES = {
    # Prospector Percy's Nugget Shop -- already Reward Shop; only the (blank) cost is
    # wrong because the currency is golden nuggets, not coins.
    ("Prospector Percy's Nugget Shop", "Prospector boots"): {"cost": "30 golden nuggets"},
    ("Prospector Percy's Nugget Shop", "Prospector helmet"): {"cost": "40 golden nuggets"},
    ("Prospector Percy's Nugget Shop", "Prospector legs"): {"cost": "50 golden nuggets"},
    ("Prospector Percy's Nugget Shop", "Prospector jacket"): {"cost": "60 golden nuggets"},
    # Petrified Pete's Ore Shop -- these pieces cost Volcanic Mine reward points, so the
    # parsed "N coins" is misleading and the type should be Reward Shop, not Shop.
    ("Petrified Pete's Ore Shop", "Prospector boots"): {
        "sourceType": SOURCE_TYPE_REWARD_SHOP,
        "cost": "21,000 Volcanic Mine reward points",
        "notes": PROSPECTOR_PETE_NOTE,
    },
    ("Petrified Pete's Ore Shop", "Prospector helmet"): {
        "sourceType": SOURCE_TYPE_REWARD_SHOP,
        "cost": "26,000 Volcanic Mine reward points",
        "notes": PROSPECTOR_PETE_NOTE,
    },
    ("Petrified Pete's Ore Shop", "Prospector legs"): {
        "sourceType": SOURCE_TYPE_REWARD_SHOP,
        "cost": "34,000 Volcanic Mine reward points",
        "notes": PROSPECTOR_PETE_NOTE,
    },
    ("Petrified Pete's Ore Shop", "Prospector jacket"): {
        "sourceType": SOURCE_TYPE_REWARD_SHOP,
        "cost": "39,000 Volcanic Mine reward points",
        "notes": PROSPECTOR_PETE_NOTE,
    },
}


def apply_record_override(record: dict) -> dict:
    """Apply a narrow (sourceName, itemName) correction in place, if one exists."""
    override = RECORD_OVERRIDES.get((record["sourceName"], record["itemName"]))
    if override:
        record.update(override)
    return record

# Non-live / aggregate / cut-content pages that live in Category:Shops but are not
# real in-game shops. Matched case-insensitively against the page title. Trailing-
# period titles (e.g. "Armour store.") are genuine canonical pages and are kept.
SHOP_PAGE_BLACKLIST = {
    "unused shops",   # cut/test/unused shop content
    "farming shops",  # plural overview/listing page; real farming shops have own pages
    "bounty hunter shop (historical)",  # "(historical)" = removed/cut content, not live
}

# Pages whose title clearly marks them as mode-specific (Deadman / Leagues /
# seasonal / tournament-only). Excluded from the default generated shop file so it
# reflects the normal game. Kept narrow to avoid dropping normal-game shops.
MODE_SPECIFIC_PATTERN = re.compile(r"\b(deadman|leagues?|seasonal|tournament)\b", re.I)


def is_blacklisted_title(title: str) -> bool:
    if title.strip().lower() in SHOP_PAGE_BLACKLIST:
        return True
    return MODE_SPECIFIC_PATTERN.search(title) is not None


def fetch_json(params: dict) -> dict:
    query = {**params, "format": "json"}
    url = API_URL + "?" + urllib.parse.urlencode(query)
    request = urllib.request.Request(url, headers=HEADERS)

    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def fetch_shop_pages(limit) -> list:
    """Discover shop page titles from Category:Shops (mainspace pages only)."""
    titles = []
    cmcontinue = None

    while True:
        params = {
            "action": "query",
            "list": "categorymembers",
            "cmtitle": SHOP_CATEGORY,
            "cmtype": "page",
            "cmnamespace": 0,
            "cmlimit": 500,
        }
        if cmcontinue:
            params["cmcontinue"] = cmcontinue

        data = fetch_json(params)
        for member in data.get("query", {}).get("categorymembers", []):
            title = member["title"]
            if is_blacklisted_title(title):
                continue
            titles.append(title)
            if limit is not None and len(titles) >= limit:
                return titles

        cmcontinue = data.get("continue", {}).get("cmcontinue")
        if not cmcontinue:
            break
        time.sleep(REQUEST_DELAY_SECONDS)

    return titles


def fetch_shop_snapshot(page_title: str):
    """Fetch rendered prices and metadata together, retaining the source revision."""
    data = fetch_json({"action": "parse", "page": page_title,
                       "prop": "text|wikitext|revid", "redirects": 1})["parse"]
    return {"title": data["title"], "revisionId": data["revid"],
            "wikitext": data["wikitext"]["*"], "html": data["text"]["*"]}


# ---- rendered store-table parsing ------------------------------------------

def _strip_tags(cell: str) -> str:
    cell = re.sub(r"<br\s*/?>", " ", cell, flags=re.I)
    return re.sub(r"\s+", " ", html_lib.unescape(re.sub(r"<[^>]+>", "", cell))).strip()


def _parse_amount(text: str):
    """A single positive integer only; ranges, notes and composite costs are unknown."""
    if not re.fullmatch(r"(?:[1-9]\d*|[1-9]\d{0,2}(?:,\d{3})+)", text or ""):
        return None
    return int(text.replace(",", ""))


def _currency(value: str, title: str = "") -> str:
    value = html_lib.unescape(strip_wiki_markup(value or "")).strip()
    # Unexpanded templates, lists and choices are not one verified currency.
    if not re.fullmatch(r"[A-Za-z][A-Za-z '\-()]{0,79}", value) or " or " in value.lower():
        return ""
    key = value.casefold()
    if key in {"coin", "coins"}:
        return "Coins"
    if key in {"point", "points"}:
        # Reviewed alias, not a shop-name heuristic: Slayer Rewards rev15373095
        # explicitly links its rewards to Slayer reward points; its table says Points.
        return "Slayer points" if title == "Slayer Rewards" else ""
    known = {"tokkul": "Tokkul", "golden nugget": "Golden nugget",
             "brimhaven voucher": "Brimhaven voucher", "agility arena ticket": "Agility arena ticket",
             "agility arena ticket (discontinued)": "Agility arena ticket (discontinued)",
             "slayer reward point": "Slayer points", "slayer points": "Slayer points"}
    return known.get(key, value)


def _store_metadata(wikitext: str) -> list:
    """Ordered direct StoreTable blocks, retaining exact displayed row identities."""
    text = re.sub(r"<!--.*?-->", "", wikitext or "", flags=re.S)
    blocks, current = [], None
    pattern = re.compile(r"\{\{\s*(StoreTableHead|StoreTableBottom|StoreLine|Tzhaar shop row)\s*(?=[|}])", re.I)
    position = 0
    while True:
        match = pattern.search(text, position)
        if not match:
            break
        depth, end = 1, match.start() + 2
        while end < len(text) and depth:
            pair = text[end:end + 2]
            if pair == "{{":
                depth += 1
                end += 2
            elif pair == "}}":
                depth -= 1
                end += 2
            else:
                end += 1
        if depth:
            return []
        params = parse_template_params(text[match.start() + 2:end - 2])
        name = match.group(1).lower()
        if name == "storetablehead":
            current = {"params": params, "rows": []}
            blocks.append(current)
        elif name == "storetablebottom":
            current = None
        elif current is not None:
            current["rows"].append(params)
        position = end
    return blocks


def _row_name(params):
    return _strip_tags(strip_wiki_markup(params.get("displayname", params.get("name", ""))))


def _cell_currency(cell, title):
    # Only currency icon links in the PURCHASE cell, never GE/buyback columns.
    links = re.findall(r'<a\b[^>]*href="/w/([^"#?]+)"[^>]*>\s*<img\b', cell, re.I)
    currencies = {_currency(urllib.parse.unquote(link).replace("_", " "), title) for link in links}
    if not links:
        return ""
    return next(iter(currencies)) if len(currencies) == 1 and "" not in currencies else None


def _format_cost(amount, currency):
    if currency == "Coins":
        unit = "coin" if amount == 1 else "coins"
    elif currency in {"Golden nugget", "Brimhaven voucher", "Agility arena ticket"}:
        unit = currency.lower() if currency == "Golden nugget" else currency
        unit += "" if amount == 1 else "s"
    elif currency == "Agility arena ticket (discontinued)":
        unit = "historical Agility arena ticket" + ("" if amount == 1 else "s")
    elif currency == "Slayer points":
        unit = "Slayer point" + ("" if amount == 1 else "s")
    else:
        unit = currency
    return f"{amount:,} {unit}"


def parse_store_tables(html: str, wikitext: str = "", title: str = "") -> list:
    """Extract item rows from a page's rendered store table(s).

    A price needs explicit, non-conflicting currency evidence. Metadata is paired
    only when all direct tables and their ordered row identities match rendering.
    Unsupported/transcluded layouts may still expose explicit per-cell currency.
    """
    rows = []
    if not html:
        return rows

    tables = []
    for table in re.findall(r"<table\b.*?</table>", html, re.S | re.I):
        headers = [_strip_tags(h).lower() for h in re.findall(r"<th[^>]*>(.*?)</th>", table, re.S)]
        if headers and any("sold at" in h for h in headers):
            tables.append((headers, table))
    metadata = _store_metadata(wikitext)
    parsed_tables = []
    for headers, table in tables:

        def header_index(*needles):
            for i, h in enumerate(headers):
                if any(n in h for n in needles):
                    return i
            return None

        idx_item = header_index("item")
        idx_stock = header_index("in stock", "stock")
        idx_restock = header_index("restock")
        idx_sold = header_index("sold at")
        idx_extra = header_index("logs required", "additional cost", "items required")
        table_rows = []

        for row_html in re.findall(r"<tr[^>]*>(.*?)</tr>", table, re.S):
            cells = re.findall(r"<td[^>]*>(.*?)</td>", row_html, re.S)
            if not cells:
                continue
            # Body rows carry a leading icon cell not present in the header row;
            # align by skipping that offset so header indices map to data cells.
            offset = len(cells) - len(headers)
            if offset < 0:
                continue

            def cell(i):
                if i is None:
                    return ""
                j = i + offset
                return cells[j] if 0 <= j < len(cells) else ""

            item_name = _strip_tags(cell(idx_item))
            if not item_name:
                continue

            table_rows.append({
                "itemName": item_name,
                "stock": _strip_tags(cell(idx_stock)),
                "restock": _strip_tags(cell(idx_restock)),
                "soldAt": _parse_amount(_strip_tags(cell(idx_sold))),
                "isFree": _strip_tags(cell(idx_sold)).casefold() == "free",
                "currency": _cell_currency(cell(idx_sold), title),
                "extraCost": _strip_tags(cell(idx_extra)),
            })
        parsed_tables.append(table_rows)

    aligned = len(metadata) == len(parsed_tables) and all(
        [row["itemName"] for row in parsed] == [_row_name(row) for row in block["rows"]]
        for parsed, block in zip(parsed_tables, metadata))
    for table_index, parsed in enumerate(parsed_tables):
        block = metadata[table_index] if aligned else {"params": {}, "rows": []}
        for row_index, row in enumerate(parsed):
            params = block["rows"][row_index] if aligned else {}
            raw_currency = params.get("currency", block["params"].get("currency", ""))
            declared = _currency(raw_currency, title)
            observed = row["currency"] or ""
            conflict = row["currency"] is None or bool(raw_currency and
                    (not declared or (observed and observed.casefold() != declared.casefold())))
            row["currency"] = "" if conflict else observed or declared
            row["hasExtraCost"] = row["extraCost"].lower() not in {"", "none", "n/a", "—", "-"}
            row["priceVaries"] = (bool(row["currency"]) and
                    _parse_amount(block["params"].get("delta", "")) is not None)
            row["tableNote"] = strip_wiki_markup(block["params"].get("namenotes", ""))
            row["currencyStatus"] = "conflict" if conflict else "explicit" if row["currency"] else "unknown"
            rows.append(row)

    return rows


# ---- light wikitext helpers ------------------------------------------------

def strip_wiki_markup(value: str) -> str:
    if value is None:
        return ""
    text = value
    # [[a|b]] -> b ; [[a]] -> a
    text = re.sub(r"\[\[(?:[^\[\]|]*\|)?([^\[\]|]+)\]\]", r"\1", text)
    text = text.replace("'''", "").replace("''", "")
    text = re.sub(r"<[^>]+>", "", text)          # html tags / refs
    text = re.sub(r"\{\{[^{}]*\}\}", "", text)   # simple templates
    return text.strip()


def split_top_level_pipes(body: str) -> list:
    """Split a template body on top-level '|', honoring {{}} and [[]] nesting."""
    parts = []
    depth = 0
    current = []
    i = 0
    while i < len(body):
        two = body[i:i + 2]
        if two in ("{{", "[["):
            depth += 1
            current.append(two)
            i += 2
            continue
        if two in ("}}", "]]"):
            depth = max(0, depth - 1)
            current.append(two)
            i += 2
            continue
        ch = body[i]
        if ch == "|" and depth == 0:
            parts.append("".join(current))
            current = []
            i += 1
            continue
        current.append(ch)
        i += 1
    parts.append("".join(current))
    return parts


def parse_template_params(body: str) -> dict:
    params = {}
    for part in split_top_level_pipes(body):
        if "=" not in part:
            continue
        key, _, val = part.partition("=")
        params[key.strip().lower()] = val.strip()
    return params


def find_templates(wikitext: str, template_name: str) -> list:
    """Return the inner bodies of all {{template_name|...}} occurrences (order preserved)."""
    bodies = []
    needle = "{{" + template_name
    idx = 0
    while True:
        start = wikitext.find(needle, idx)
        if start == -1:
            break
        # walk to the matching closing braces
        depth = 0
        j = start
        while j < len(wikitext):
            two = wikitext[j:j + 2]
            if two == "{{":
                depth += 1
                j += 2
                continue
            if two == "}}":
                depth -= 1
                j += 2
                if depth == 0:
                    break
                continue
            j += 1
        inner = wikitext[start + 2:j - 2]
        bodies.append(inner)
        idx = j
    return bodies


def extract_location(wikitext: str) -> str:
    for body in find_templates(wikitext, "Infobox Shop"):
        params = parse_template_params(body)
        loc = params.get("location", "")
        if loc:
            return strip_wiki_markup(loc)
    return ""


def is_members_shop(wikitext: str) -> bool:
    for body in find_templates(wikitext, "Infobox Shop"):
        params = parse_template_params(body)
        return params.get("members", "").strip().lower() in ("yes", "y", "true")
    return False


def build_notes(stock: str, restock: str, members: bool, has_coin_price: bool) -> str:
    bits = []
    if members:
        bits.append("Members shop.")
    stock = (stock or "").strip()
    restock = (restock or "").strip()
    if stock:
        if stock.lower() in ("inf", "infinite", "∞", "unlimited"):
            bits.append("Stock: unlimited.")
        else:
            bits.append(f"Default stock: {stock}.")
    if restock:
        bits.append(f"Restock: {restock}.")
    if has_coin_price:
        bits.append("Cost increases as stock depletes.")
    return " ".join(bits)


def parse_shop_page(title: str, wikitext: str, html: str) -> list:
    """Build source rows from the rendered store table; location/members from wikitext."""
    location = extract_location(wikitext)
    members = is_members_shop(wikitext)
    source_type = classify_source_type(title)

    records = []
    for row in parse_store_tables(html, wikitext, title):
        item_name = row["itemName"]
        if not item_name:
            continue

        # Never present a known component as the total price of a composite purchase.
        cost = ""
        if row["currency"] and row["soldAt"] is not None and not row["hasExtraCost"]:
            cost = _format_cost(row["soldAt"], row["currency"])
        elif row["isFree"] and not row["hasExtraCost"]:
            cost = "Free"
        notes = build_notes(row["stock"], row["restock"], members, bool(cost) and row["priceVaries"])
        if row["tableNote"]:
            notes += " " + row["tableNote"]
        if row["hasExtraCost"]:
            notes += " Additional purchase costs apply; see the Wiki."

        records.append(apply_record_override({
            "itemName": item_name,
            "sourceType": source_type,
            "sourceName": title,
            "location": location,
            "cost": cost,
            "notes": notes.strip(),
        }))

    return records


def main() -> None:
    print(f"Discovering shop pages from {SHOP_CATEGORY} (cap={MAX_SHOPS})...")
    try:
        shop_pages = fetch_shop_pages(MAX_SHOPS)
    except Exception as exc:
        print(f"ABORT: could not discover shop pages: {type(exc).__name__}: {exc}", file=sys.stderr)
        sys.exit(2)

    print(f"  Found {len(shop_pages)} shop pages to inspect.")

    all_records = []
    inspected = 0
    failed = []
    no_store_line = []
    revisions = []

    for title in shop_pages:
        inspected += 1
        try:
            snapshot = fetch_shop_snapshot(title)
            wikitext, html = snapshot["wikitext"], snapshot["html"]
            revisions.append({"requestedPage": title, "page": snapshot["title"], "revisionId": snapshot["revisionId"]})
        except Exception as exc:
            failed.append({"page": title, "reason": f"{type(exc).__name__}: {exc}"})
            continue
        time.sleep(REQUEST_DELAY_SECONDS)

        if not wikitext or not html:
            failed.append({"page": title, "reason": "no wikitext/html"})
            continue

        records = parse_shop_page(title, wikitext, html)
        if not records:
            no_store_line.append(title)
            continue
        all_records.extend(records)

    if not all_records:
        print("ABORT: no shop table records could be parsed. Writing nothing "
              "(refusing to fabricate data).", file=sys.stderr)
        sys.exit(3)

    # Dedupe on (itemName, sourceName); keep first occurrence.
    seen = set()
    deduped = []
    duplicate_count = 0
    for record in all_records:
        key = (record["itemName"].lower(), record["sourceName"].lower())
        if key in seen:
            duplicate_count += 1
            continue
        seen.add(key)
        deduped.append(record)

    deduped.sort(key=lambda r: (r["itemName"].lower(), r["sourceName"].lower()))

    cost_nonblank = sum(1 for r in deduped if r["cost"].strip())
    cost_blank = len(deduped) - cost_nonblank

    OUTPUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT_FILE.open("w", encoding="utf-8") as file:
        json.dump(deduped, file, indent=2, ensure_ascii=False)

    report = {
        "shopsInspected": inspected,
        "recordsBeforeDedupe": len(all_records),
        "recordsGenerated": len(deduped),
        "duplicatesCollapsed": duplicate_count,
        "recordsWithCost": cost_nonblank,
        "recordsWithBlankCost": cost_blank,
        "pagesWithNoStoreTable": len(no_store_line),
        "failedPages": failed,
        "sourceRevisions": revisions,
        "sampleNoStoreTablePages": no_store_line[:15],
        "sampleRecords": deduped[:10],
    }
    REPORT_FILE.parent.mkdir(parents=True, exist_ok=True)
    with REPORT_FILE.open("w", encoding="utf-8") as file:
        json.dump(report, file, indent=2, ensure_ascii=False)

    # ---- diagnostics to stdout ----
    print("\n=== Shop source generation report ===")
    print(f"Shops inspected           : {inspected}")
    print(f"Records before dedupe     : {len(all_records)}")
    print(f"Records generated         : {len(deduped)}")
    print(f"Duplicates collapsed      : {duplicate_count}")
    print(f"Records with known cost   : {cost_nonblank}")
    print(f"Records with blank cost   : {cost_blank}")
    print(f"Pages with no store table : {len(no_store_line)}")
    print(f"Failed pages              : {len(failed)}")
    print(f"\nWrote {len(deduped)} shop source records to {OUTPUT_FILE}")
    print(f"Wrote diagnostics report to {REPORT_FILE}")
    print("\nSample records:")
    for record in deduped[:10]:
        print("  " + json.dumps(record, ensure_ascii=False))


if __name__ == "__main__":
    main()
