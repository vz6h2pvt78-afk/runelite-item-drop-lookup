import hashlib
import json
from pathlib import Path
import unittest
from build_monster_locations import templates


class KamilEncounterTest(unittest.TestCase):
    def test_only_the_exact_nightmare_zone_cake_row_is_reviewed(self):
        root = Path(__file__).resolve().parents[1]
        pin = json.loads((root / "tools/fixtures/kamil_nightmare_zone.json").read_text(encoding="utf8"))
        raw = pin["wikitext"]
        self.assertEqual(("Kamil (Nightmare Zone)", 15200663), (pin["title"], pin["revisionId"]))
        self.assertEqual("9851a80b949eb0ad4c1e0d28535773ac36bd98d3d8021fa70f04a1fa229c424b", hashlib.sha256(raw.encode()).hexdigest())
        self.assertIn("a boss that can be encountered in the [[Nightmare Zone]]", raw)
        rows = [p for _, _, kind, p, _ in templates(raw) if kind == "dropsline" and p.get("name") == "Chocolate cake"]
        self.assertEqual([{"name":"Chocolate cake","quantity":"1","rarity":"Always"}], rows)
        data = json.loads((root / "src/main/resources/wiki_monster_drops.json").read_text(encoding="utf8"))
        matches = [(d["quantity"],d["dropRate"],d["notes"]) for d in data
                   if d["monsterName"] == pin["title"] and d["itemName"] == "Chocolate cake"]
        self.assertEqual([("1","Always","")], matches)
