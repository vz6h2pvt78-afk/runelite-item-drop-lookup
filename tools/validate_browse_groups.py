"""Validate reviewed browse membership without changing item identity or source data."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"


def normalize(value):
    return re.sub(r"\s+", " ", value.strip().lower().replace("\u2019", "'").replace("-", " ").replace("_", " "))


def compact(value):
    return re.sub("[^a-z0-9+]", "", normalize(value))


def catalog_names(resources=RESOURCES):
    names = set()
    def visit(value):
        if isinstance(value, dict):
            if isinstance(value.get("itemName"), str):
                names.add(value["itemName"])
            for child in value.values():
                visit(child)
        elif isinstance(value, list):
            for child in value:
                visit(child)
    for path in resources.glob("*.json"):
        if path.name != "item_browse_groups.json":
            visit(json.loads(path.read_text(encoding="utf-8-sig")))
    return names


def validate(data, names=None, families=None, aliases=None):
    names = catalog_names() if names is None else set(names)
    families = set(json.loads((RESOURCES / "potion_families.json").read_text())["families"]) if families is None else set(families)
    if aliases is None:
        java = (ROOT / "src/main/java/com/itemdroplookup/service/ItemNameMatching.java").read_text()
        aliases = {normalize(a): b for a, b in re.findall(r'Map.entry\("([^"]+)", "([^"]+)"\)', java)}
    if data.get("schemaVersion") != 1 or not data.get("groups"):
        raise ValueError("Missing browse schema/groups")
    ids, terms = set(), set()
    identities = names | families
    canonical_keys = {compact(n) for n in identities}
    for group in data["groups"]:
        gid = group.get("id")
        if not gid or gid in ids:
            raise ValueError("Duplicate/missing group id")
        ids.add(gid)
        members = group.get("members", [])
        synonyms = group.get("terms", [])
        if not members or not synonyms or not group.get("scope") or not group.get("sources"):
            raise ValueError("Empty group or missing review evidence")
        kind = group.get("memberKind")
        allowed = families if kind == "potionFamily" else names if kind == "item" else set()
        seen = set()
        for member in members:
            if member not in allowed or normalize(member) in seen:
                raise ValueError("Unknown/duplicate member: " + member)
            seen.add(normalize(member))
            target = aliases.get(normalize(member))
            if target is not None and compact(target) != compact(member):
                raise ValueError("Member resolves to a different identity: " + member)
        if type(group.get("overridesCanonical")) is not bool:
            raise ValueError("Canonical override must be explicit")
        for term in synonyms:
            key = normalize(term)
            if not key or key in terms or key in aliases:
                raise ValueError("Duplicate/alias browse term: " + term)
            terms.add(key)
            if compact(term) in canonical_keys and not group["overridesCanonical"]:
                raise ValueError("Unapproved canonical override: " + term)
    return {g["id"]: len(g["members"]) for g in data["groups"]}


if __name__ == "__main__":
    print(json.dumps(validate(json.loads((RESOURCES / "item_browse_groups.json").read_text()))))
