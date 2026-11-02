"""Build offline monster locations and conservative bindings to EXISTING drops.

Uses the wiki's revision API (batched, throttled, cached, bounded retries).
No drop records are added, removed or rewritten. See docs/monster-locations.md.
"""
import argparse
from collections import Counter, defaultdict
from datetime import datetime, timezone
import hashlib
import html
import json
from pathlib import Path
import re
import time
import urllib.error
import urllib.parse
import urllib.request

from build_shop_sources import split_top_level_pipes
from build_wiki_drops import parse_drop_line

ROOT = Path(__file__).resolve().parents[1]
RESOURCE = ROOT / "src/main/resources/monster_locations.json"
REPORT = ROOT / "docs/data/monster_locations_report.json"
OVERRIDES = ROOT / "tools/data/monster_location_overrides.json"
API = "https://oldschool.runescape.wiki/api.php"
WIKI = "https://oldschool.runescape.wiki/w/"
USER_AGENT = "ItemDropLookupLocationBuilder/1.0 (https://github.com/vz6h2pvt78-afk/runelite-item-drop-lookup)"
CITATION_TEMPLATES = {"citeforum", "citeweb", "citenews", "citetwitter", "citereddit", "citevideo"}


def norm(value):
    return re.sub(r"\s+", " ", value.replace("_", " ").replace("\u2019", "'")).strip().casefold()


def wiki_url(title):
    return WIKI + urllib.parse.quote(title.replace(" ", "_"), safe="()'/:#")


def without_comments(text):
    return re.sub(r"<!--.*?-->", "", text, flags=re.S)


def templates(text):
    """Yield complete top-level templates with offsets; never accept broken braces.

    Top-level traversal avoids treating example/nested DropsLine as live records.
    The shared shop splitter handles nested template/link pipes.
    """
    i = 0
    while i < len(text):
        start = text.find("{{", i)
        if start < 0:
            return
        depth, end = 1, start + 2
        while end < len(text) and depth:
            two = text[end:end + 2]
            if two == "{{":
                depth += 1
                end += 2
            elif two == "}}":
                depth -= 1
                end += 2
            else:
                end += 1
        if depth:
            return
        parts = split_top_level_pipes(text[start + 2:end - 2])
        name = norm(parts[0]).removeprefix("template:")
        params, positional = {}, []
        for part in parts[1:]:
            if "=" in part:
                key, value = part.split("=", 1)
                if re.fullmatch(r"[\w -]+", key.strip()):
                    if key.strip().isdigit():
                        positional.append(value.strip())
                    else:
                        params[key.strip().lower()] = value.strip()
                    continue
            if part.strip():
                positional.append(part.strip())
        yield start, end, name, params, positional
        i = end


def clean(value, unknown=None):
    """Render the small, reviewed set of display templates; report other templates."""
    value = without_comments(value or "")
    value = re.sub(r"<ref\b[^>]*>.*?</ref\s*>|<ref\b[^>]*/>", "", value, flags=re.S | re.I)
    # Replace inside out, preserving known text but never guessing unknown output.
    def replace(match):
        body = match.group()[2:-2]
        parts = split_top_level_pipes(body)
        name = norm(parts[0])
        args = [p.strip() for p in parts[1:] if "=" not in p]
        named = dict(p.split("=", 1) for p in parts[1:] if "=" in p)
        if name in ("floornumber", "floor"):
            floor = named.get("uk", "").strip()
            if re.fullmatch(r"-?\d+", floor):
                n = int(floor)
                return "ground floor" if n == 0 else ("basement" if n == -1 else f"floor {n} (UK)")
        if name in ("fairycode", "fairy ring") and args:
            return "fairy ring " + args[0].upper()
        if name in ("nowrap", "small", "smaller", "tooltip") and args:
            return args[0]
        if name in ("colour", "color") and len(args) >= 2:
            return args[1]
        if name == "scp" and len(args) >= 2:
            return args[1] + " " + args[0]
        if name in ("refn", "efn", "namedref"):
            return ""  # Extracted separately as access notes, not part of the name.
        if name == "br":
            return "; "
        if unknown is not None:
            unknown.add(name)
        return ""
    while re.search(r"\{\{[^{}]*\}\}", value):
        value = re.sub(r"\{\{[^{}]*\}\}", replace, value)
    value = re.sub(r"\[\[(?:File|Image):[^]]*\]\]", "", value, flags=re.I)
    value = re.sub(r"\[\[([^]|]+)(?:\|([^]]+))?\]\]", lambda m: m[2] if m[2] is not None else m[1], value)
    value = re.sub(r"<br\s*/?>", "; ", value, flags=re.I)
    value = re.sub(r"<[^>]+>", "", value)
    value = re.sub(r"\[https?://\S+\s+([^]]+)\]", r"\1", value)
    value = html.unescape(value).replace("'''", "").replace("''", "")
    value = re.sub(r"\(\s*\)", "", value)
    return re.sub(r"\s+", " ", value).strip()


def tokens(value):
    return [v.strip() for v in clean(value).split(",") if v.strip() and norm(v) != "default"]


def boolean(value):
    value = norm(value)
    if value in ("yes", "y", "true", "1"):
        return True
    if value in ("no", "n", "false", "0"):
        return False
    return None


def drop_key(record):
    return tuple(norm(record.get(k, "")) for k in ("monsterName", "itemName", "quantity", "dropRate", "notes"))


def nested_templates(text):
    for entry in templates(text):
        yield entry
        start, end = entry[:2]
        yield from nested_templates(text[start + 2:end - 2])


def reference_definitions(text):
    definitions = {}
    for start, end, name, params, positional in nested_templates(text):
        if name in ("refn", "efn") and params.get("name") and positional:
            definitions[(params.get("group", ""), params["name"])] = positional[0]
        # Offsets from nested traversal are relative to their containing template;
        # reconstruct citation bodies from parameters rather than slice the page.
        if name in CITATION_TEMPLATES and params.get("name"):
            definitions[(params.get("group", ""), params["name"])] = "{{" + name + "}}"
    for match in re.finditer(r"<ref\b([^>]*)>(.*?)</ref\s*>", text, re.I | re.S):
        name = re.search(r'''\bname\s*=\s*["']?([^"'\s>]+)''', match[1])
        group = re.search(r'''\bgroup\s*=\s*["']?([^"'\s>]+)''', match[1])
        if name:
            definitions[(group[1] if group else "", name[1])] = match[2]
    return definitions


def access_notes(raw, definitions, unknown):
    notes = []
    for ref in re.findall(r"<ref\b[^>]*>(.*?)</ref\s*>", raw, re.I | re.S):
        if not re.search(r"https?://|\{\{cite", ref, re.I):
            notes.append(clean(ref, unknown))
    for match in re.finditer(r"<ref\b([^>]*)/>", raw, re.I):
        name = re.search(r'''\bname\s*=\s*["']?([^"'\s/>]+)''', match[1])
        group = re.search(r'''\bgroup\s*=\s*["']?([^"'\s/>]+)''', match[1])
        key = (group[1] if group else "", name[1] if name else "")
        if key in definitions:
            notes.append(clean(definitions[key], unknown))
        else:
            unknown.add("unresolved named reference")
    for _, _, name, params, positional in templates(raw):
        if name in ("refn", "efn") and positional:
            notes.append(clean(positional[0], unknown))
        if name == "namedref":
            key = (params.get("group", ""), params.get("name", positional[0] if positional else ""))
            if key in definitions:
                notes.append(clean(definitions[key], unknown))
            else:
                unknown.add("unresolved named reference")
    return notes


def citation_only(value, definitions, visited=None):
    """A citation provides provenance, not a gameplay condition. Fail closed."""
    visited = set() if visited is None else visited
    value = re.sub(r"</?ref\b[^>]*>", "", value, flags=re.I).strip()
    entries = list(templates(value))
    remaining, last = [], 0
    for start, end, name, params, positional in entries:
        remaining.append(value[last:start])
        last = end
        if name in CITATION_TEMPLATES:
            continue
        if name == "namedref":
            key = (params.get("group", ""), params.get("name", positional[0] if positional else ""))
            if key not in visited and key in definitions and citation_only(definitions[key], definitions, visited | {key}):
                continue
        return False
    remaining.append(value[last:])
    return bool(entries) and not "".join(remaining).strip()


def parse_locations(monster, page, text, rules, issues):
    rows, seen = [], set()
    stripped = without_comments(text)
    references = reference_definitions(stripped)
    for start, end, name, p, positional in templates(stripped):
        if name != "locline":
            continue
        unknown = set()
        raw_location = p.get("location", "")
        location = clean(raw_location, unknown)
        if not location or location in ("?", "N/A") or any(v in location for v in ("{{", "}}", "[[", "]]")):
            issues["malformedRecords"].append({"page": monster, "reason": "missing/unresolved location", "raw": raw_location})
            continue
        raw_levels = clean(p.get("levels", ""), unknown)
        levels = []
        if re.fullmatch(r"\d+(?:\s*,\s*\d+)*", raw_levels):
            levels = sorted(set(map(int, re.findall(r"\d+", raw_levels))))
        elif raw_levels and raw_levels not in ("?", "N/A"):
            issues["unparsedLevels"].append({"page": monster, "location": location, "levels": raw_levels})
        # Missing membership stays unknown, even though wiki rendering defaults to No.
        members = boolean(p.get("members", ""))
        versions = tokens(p.get("dropversion", ""))
        regions = sorted({v.title() for v in tokens(p.get("leagueregion", "").replace("&", ","))
                          if norm(v) not in ("n/a", "no", "general", "all", "yes")})
        coordinate_tokens = []
        for value in positional:
            pair = re.fullmatch(r"\s*(\d+)\s*,\s*(\d+)\s*", value)
            x = re.search(r"(?:^|,)\s*x\s*:\s*(\d+)(?:,|$)", value)
            y = re.search(r"(?:^|,)\s*y\s*:\s*(\d+)(?:,|$)", value)
            if pair:
                coordinate_tokens.append((int(pair[1]), int(pair[2])))
            elif x and y:
                coordinate_tokens.append((int(x[1]), int(y[1])))
        spawns, spawn_basis = None, "unknown"
        if re.fullmatch(r"\d+", p.get("spawns", "")):
            spawns, spawn_basis = int(p["spawns"]), "wiki count"
        elif "spawns" not in p and norm(p.get("mtype", "pin")) == "pin" and coordinate_tokens:
            spawns, spawn_basis = len(coordinate_tokens), "map pins (approximate)"
        notes = [clean(p.get("notes", ""), unknown)]
        requirements = clean(p.get("requirements", ""), unknown)
        notes.extend(access_notes(raw_location, references, unknown))
        availability_text = " ".join([location, *notes])
        unavailable = bool(re.search(r"\b(unreachable|inaccessible|unattackable|cannot actually be fought|cannot be attacked)\b", availability_text, re.I))
        if re.search(r"both.*free-to-play.*members", availability_text, re.I):
            members = None
            notes.append("This row spans F2P and members areas; membership is not assigned to individual spawns.")
        if unavailable:
            notes.append("Marked unreachable or inaccessible by the wiki; not a usable source.")
        wilderness = "UNKNOWN"
        evidence = "No reviewed Wilderness classification"
        explicit = boolean(p.get("wilderness", ""))
        if explicit is not None:
            wilderness = "YES" if explicit else "NO"
            evidence = "Explicit LocLine wilderness parameter"
        rule = rules.get((norm(monster), norm(location))) or rules.get(("", norm(location)))
        if rule:
            wilderness, evidence = rule["wilderness"], rule["source"]
            if rule.get("note"):
                notes.append(rule["note"])
        if unknown:
            notes.append("Some wiki formatting was omitted; see the source page for full context.")
            issues["unsupportedTemplates"].append({"page": monster, "location": location, "templates": sorted(unknown)})
        record = {
            "monsterName": monster, "sourcePage": page["title"], "revisionId": page["revisionId"],
            "npcName": clean(p.get("name", page["title"])), "location": location,
            "levels": levels, "levelsText": raw_levels, "members": members,
            "leagueRegions": regions, "wilderness": wilderness, "wildernessEvidence": evidence,
            "available": not unavailable, "spawns": spawns, "spawnCountBasis": spawn_basis,
            "notes": " ".join(dict.fromkeys(n for n in notes if n)), "requirements": requirements,
            "dropVersions": versions, "version": clean(p.get("version", "")),
            "mapId": p.get("mapid", ""), "plane": p.get("plane", ""),
            "sourceUrl": wiki_url(page["title"]),
            # Source section contains the wiki-maintained maps; no invented map URLs.
            "mapLink": wiki_url(page["title"]) + ("#Locations" if re.search(r"^==\s*Locations\s*==", stripped, re.M | re.I)
                                                     else "#Location" if re.search(r"^==\s*Location\s*==", stripped, re.M | re.I) else ""),
        }
        identity = json.dumps([record, coordinate_tokens], sort_keys=True, ensure_ascii=False)
        if identity in seen:
            issues["suspectedDuplicates"].append({"page": monster, "location": location, "reason": "identical row removed"})
            continue
        seen.add(identity)
        record["id"] = hashlib.sha256(identity.encode()).hexdigest()[:20]
        rows.append(record)
    if stripped.lower().count("{{locline") > len(rows):
        # Count discrepancies are reported even if broken braces prevented extraction.
        issues["templateCountDiscrepancies"].append({"page": monster, "markers": stripped.lower().count("{{locline"), "rows": len(rows)})
    return rows


def bind_drops(monster, text, rows, existing, issues):
    """Exact tuple matches only, using explicit table/row drop versions.

    Any unmatched/unknown occurrence invalidates the binding. This is deliberately
    stricter than showing the union of page locations for every historical drop.
    """
    found = defaultdict(list)
    table_versions = None
    stripped = without_comments(text)
    references = reference_definitions(stripped)
    has_versioned_tables = any(name == "dropstablehead" and tokens(p.get("dropversion", ""))
                               for _, _, name, p, _ in templates(stripped))
    for start, end, name, params, positional in templates(stripped):
        if name == "dropstablehead":
            table_versions = tokens(params.get("dropversion", ""))
        elif name == "dropstablebottom":
            table_versions = None
        elif name == "dropsline":
            record, reason = parse_drop_line(monster, stripped[start:end])
            if not record or drop_key(record) not in existing:
                continue
            versions = tokens(params["dropversion"]) if "dropversion" in params else table_versions
            # The older drop generator discards these qualifications. An equal
            # rate is not proof of equal availability when a row has conditions.
            conditional = any(params.get(k) for k in ("altrarity", "altraritynotes", "rolls", "leagueregion"))
            conditional = conditional or any(params.get(k) and not citation_only(params[k], references)
                                            for k in ("raritynotes", "quantitynotes", "namenotes", "notes"))
            if versions:
                # Every requested version must have explicit location evidence.
                covered = all(any(norm(v) in {norm(x) for x in row["dropVersions"]} for row in rows) for v in versions)
                matching = [row["id"] for row in rows if {norm(v) for v in versions} & {norm(v) for v in row["dropVersions"]}]
            else:
                covered = versions is not None and bool(rows) and (
                    all(not row["dropVersions"] for row in rows) or not has_versioned_tables)
                matching = [row["id"] for row in rows] if covered else []
            if conditional:
                covered = False
            found[drop_key(record)].append((covered, matching, versions or []))
    bindings = []
    for key, occurrences in found.items():
        if not all(o[0] for o in occurrences):
            issues["variantMatchingIssues"].append({"monsterName": monster, "itemName": existing[key]["itemName"],
                "dropRate": existing[key]["dropRate"], "reason": "conditional, unversioned or unmatched drop/location context"})
            continue
        if len(occurrences) > 1:
            issues["sharedDropSignatures"].append({"monsterName": monster, "itemName": existing[key]["itemName"],
                "dropRate": existing[key]["dropRate"], "occurrences": len(occurrences), "action": "union of all verified associations retained"})
        base = existing[key]
        bindings.append({**{k: base.get(k, "") for k in ("monsterName", "itemName", "quantity", "dropRate", "notes")},
            "locationIds": sorted({i for o in occurrences for i in o[1]}),
            "dropVersions": sorted({v for o in occurrences for v in o[2]}),
            "evidence": "Exact existing drop tuple matched to wiki table/row versions or common unversioned table"})
    return bindings


class WikiClient:
    def __init__(self, cache, delay=.5, refresh=False, offline=False):
        self.cache, self.delay, self.refresh, self.offline = cache, delay, refresh, offline
        cache.mkdir(parents=True, exist_ok=True)
        self.last_request = 0

    def path(self, title):
        return self.cache / (hashlib.sha256(title.encode()).hexdigest() + ".json")

    def request(self, titles):
        params = {"action": "query", "format": "json", "formatversion": 2, "prop": "revisions",
                  "rvprop": "ids|timestamp|content", "rvslots": "main", "redirects": 1,
                  "maxlag": 5, "titles": "|".join(titles)}
        url = API + "?" + urllib.parse.urlencode(params)
        for attempt in range(4):
            time.sleep(max(0, self.delay - (time.monotonic() - self.last_request)))
            self.last_request = time.monotonic()
            try:
                request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
                with urllib.request.urlopen(request, timeout=45) as response:
                    data = json.load(response)
                if "error" in data:
                    if data["error"].get("code") in ("maxlag", "ratelimited", "readonly"):
                        raise RuntimeError(data["error"].get("code"))
                    raise ValueError(str(data["error"]))
                return data
            except (urllib.error.URLError, TimeoutError, RuntimeError) as exc:
                if isinstance(exc, urllib.error.HTTPError) and exc.code not in (429, 500, 502, 503, 504):
                    raise
                if attempt == 3:
                    raise
                retry = exc.headers.get("Retry-After", "") if isinstance(exc, urllib.error.HTTPError) else ""
                time.sleep(min(60, max(2 ** (attempt + 1), int(retry) if retry.isdigit() else 0)))

    def fetch_batch(self, titles):
        result, needed = {}, []
        for title in titles:
            path = self.path(title)
            if path.exists() and not self.refresh:
                result[title] = json.loads(path.read_text(encoding="utf-8"))
            else:
                needed.append(title)
        if needed and self.offline:
            for title in needed:
                result[title] = {"error": "not cached"}
            return result
        if not needed:
            return result
        data = self.request(needed).get("query", {})
        aliases = {p["from"]: p["to"] for k in ("normalized", "redirects") for p in data.get(k, [])}
        pages = {p["title"]: p for p in data.get("pages", [])}
        for title in needed:
            resolved, visited = title, set()
            while resolved in aliases and resolved not in visited:
                visited.add(resolved)
                resolved = aliases[resolved]
            page = pages.get(resolved, {})
            revisions = page.get("revisions", [])
            if not revisions:
                value = {"error": "missing page or revision", "title": resolved, "missing": bool(page.get("missing"))}
            else:
                rev = revisions[0]
                value = {"title": resolved, "revisionId": rev["revid"], "revisionTimestamp": rev["timestamp"],
                         "fetchedAt": datetime.now(timezone.utc).isoformat(), "wikitext": rev["slots"]["main"]["content"]}
            self.path(title).write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")
            result[title] = value
        return result


def atomic_json(path, data, compact=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(data, ensure_ascii=False, indent=None if compact else 2,
                                   separators=(",", ":") if compact else None) + "\n", encoding="utf-8")
    temporary.replace(path)


def group_drop_issues(records):
    grouped = defaultdict(list)
    for record in records:
        grouped[record["monsterName"]].append(record)
    return [{"monsterName": name, "count": len(values),
             "examples": [{"itemName": v["itemName"], "dropRate": v["dropRate"]} for v in values[:3]]}
            for name, values in sorted(grouped.items(), key=lambda pair: norm(pair[0]))]


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    scope = parser.add_mutually_exclusive_group(required=True)
    scope.add_argument("--monster", action="append", help="Exact wiki title; repeat for a sample")
    scope.add_argument("--all", action="store_true", help="Use the existing monster-page worklist")
    parser.add_argument("--worklist", type=Path, default=ROOT / "src/main/resources/wiki_drops_monster_pages.json")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--report", type=Path)
    parser.add_argument("--cache", type=Path, default=ROOT / ".cache/monster-locations")
    parser.add_argument("--overrides", type=Path, default=OVERRIDES)
    parser.add_argument("--delay", type=float, default=.5)
    parser.add_argument("--refresh", action="store_true")
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args(argv)
    if args.refresh and args.offline:
        parser.error("--refresh cannot be used with --offline")
    if args.delay < .5:
        parser.error("--delay must be at least 0.5 seconds")
    titles = sorted(set(args.monster or json.loads(args.worklist.read_text(encoding="utf-8"))), key=str.casefold)
    output = args.output or (RESOURCE if args.all else ROOT / ".cache/monster_locations_sample.json")
    report_path = args.report or (REPORT if args.all else ROOT / ".cache/monster_locations_sample_report.json")
    overrides = json.loads(args.overrides.read_text(encoding="utf-8"))
    rules = {(norm(r.get("monsterName", "")), norm(r["location"])): r for r in overrides["locations"]}
    if len(rules) != len(overrides["locations"]):
        raise ValueError("Duplicate curated location rule")
    drops = json.loads((ROOT / "src/main/resources/wiki_monster_drops.json").read_text(encoding="utf-8"))
    existing = {drop_key(d): d for d in drops}
    client = WikiClient(args.cache, args.delay, args.refresh, args.offline)
    rows, bindings, missing, failed, checked, page_revisions = [], [], [], [], [], []
    issues = defaultdict(list)
    for offset in range(0, len(titles), 20):
        batch = titles[offset:offset + 20]
        try:
            pages = client.fetch_batch(batch)
        except (urllib.error.URLError, TimeoutError, RuntimeError, ValueError) as exc:
            failed.extend({"page": t, "reason": str(exc)} for t in batch)
            print(f"Fetch failed for batch {offset}: {exc}", flush=True)
            continue
        for title in batch:
            page = pages[title]
            if "error" in page:
                if page.get("missing"):
                    checked.append(title)
                    missing.append(title)
                    issues["missingWikiPages"].append(title)
                    continue
                failed.append({"page": title, "reason": page["error"]})
                continue
            checked.append(title)
            page_revisions.append({"requestedTitle": title, **{k: page[k] for k in ("title", "revisionId", "revisionTimestamp", "fetchedAt")}})
            locations = parse_locations(title, page, page["wikitext"], rules, issues)
            rows.extend(locations)
            if not locations:
                missing.append(title)
            bindings.extend(bind_drops(title, page["wikitext"], locations, existing, issues))
        print(f"Checked {min(offset + 20, len(titles))}/{len(titles)}; {len(rows)} locations; {len(bindings)} verified drop bindings", flush=True)
    by_location = Counter((r["monsterName"], r["location"], tuple(r["levels"])) for r in rows)
    for (monster, location, levels), count in by_location.items():
        if count > 1:
            issues["suspectedDuplicates"].append({"page": monster, "location": location, "count": count, "reason": "same location/levels; distinct coordinates or metadata retained"})
    bound_keys = {drop_key(b) for b in bindings}
    unbound = [d for d in drops if d["monsterName"] in set(checked) and drop_key(d) not in bound_keys]
    now = datetime.now(timezone.utc).isoformat()
    summary = {"pagesRequested": len(titles), "pagesChecked": len(checked),
               "pagesWithLocations": len(checked) - len(missing), "pagesWithoutLocations": len(missing),
               "failedPages": len(failed), "locationRecords": len(rows), "verifiedDropBindings": len(bindings),
               "unboundExistingDrops": len(unbound), "unavailableLocations": sum(not r["available"] for r in rows),
               "wildernessCounts": dict(Counter(r["wilderness"] for r in rows)),
               "membershipUnknown": sum(r["members"] is None for r in rows),
               **{k: len(issues[k]) for k in ("malformedRecords", "suspectedDuplicates", "variantMatchingIssues", "sharedDropSignatures", "unsupportedTemplates", "unparsedLevels", "templateCountDiscrepancies")}}
    report = {"generatedAt": now, "summary": summary, "missingLocationPages": missing,
              "failedPages": failed, "issues": dict(issues), "pageRevisions": page_revisions,
              "wildernessUncertainty": [{"page": r["monsterName"], "location": r["location"]} for r in rows if r["wilderness"] == "UNKNOWN"],
              "unboundExistingDrops": unbound}
    # Keep complete per-drop diagnostics locally, while the committed report
    # stays reviewable instead of repeating thousands of existing drop records.
    atomic_json(args.cache / "last-run-diagnostics.json", report)
    report["issues"] = dict(issues)
    report["issues"]["variantMatchingIssues"] = group_drop_issues(issues["variantMatchingIssues"])
    report["unboundExistingDrops"] = group_drop_issues(unbound)
    report["pageRevisions"] = {p["requestedTitle"]: p["revisionId"] for p in page_revisions}
    report["redirects"] = {p["requestedTitle"]: p["title"] for p in page_revisions if p["requestedTitle"] != p["title"]}
    report["diagnosticsNote"] = "Drop issues grouped by monster with up to three examples. Complete diagnostics are written to the ignored cache directory."
    atomic_json(report_path, report)
    # A network outage must not replace a good bundled dataset with a partial one.
    if failed or not rows:
        print(f"Output NOT replaced: {len(failed)} failed pages / {len(rows)} rows. Report: {report_path}")
        return 1
    dataset = {"schemaVersion": 1, "generatedAt": now, "source": API, "summary": summary,
               "locations": sorted(rows, key=lambda r: (norm(r["monsterName"]), norm(r["location"]), r["id"])),
               "dropBindings": sorted(bindings, key=drop_key)}
    atomic_json(output, dataset, compact=True)
    print(json.dumps(summary, indent=2))
    print(f"Wrote {output} ({output.stat().st_size:,} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
