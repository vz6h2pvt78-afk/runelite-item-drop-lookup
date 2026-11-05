import copy
import hashlib
import json
import unittest
import build_dragon_warhammer as source


class DragonWarhammerEvidenceTest(unittest.TestCase):
    def inputs(self):
        read = lambda path: json.loads(path.read_text(encoding="utf8"))
        return [read(source.PINS / "parent.json"), read(source.PINS / "unique.json"),
                read(source.ROOT / "src/main/resources/wiki_monster_drops.json")]

    def change(self, pin, old, new):
        self.assertIn(old, pin["wikitext"])
        pin["wikitext"] = pin["wikitext"].replace(old, new)
        pin["wikitextSha256"] = hashlib.sha256(pin["wikitext"].encode()).hexdigest()

    def test_exact_source_reproduces_and_has_both_primaries(self):
        data = source.build()
        self.assertEqual(data, json.loads(source.OUT.read_text(encoding="utf8")))
        row, = data["sources"]
        self.assertEqual(("Lizardman shaman", "Dragon warhammer", "1", "1/3000", "Pre-roll"),
                         tuple(row[k] for k in ("monsterName", "itemName", "quantity", "dropRate", "dropTable")))
        self.assertEqual([15371945, 14669471], [p["revisionId"] for p in row["evidence"]])
        self.assertNotIn("locationId", row)

    def test_unique_rate_quantity_and_new_condition_require_review(self):
        for old, new in (("rarity=1/3000", "rarity=1/5000"), ("quantity=1", "quantity=2"),
                         ("rarity=1/3000", "rarity=1/3000|raritynotes=On task")):
            parent, unique, raw = self.inputs()
            self.change(unique, old, new)
            with self.assertRaisesRegex(ValueError, "exact unique row"):
                source.extract(parent, unique, raw)

    def test_variant_or_parameterized_transclusion_requires_review(self):
        for old, new in (("version2 = Lizardman Temple", "version2 = Chambers of Xeric"),
                         ("{{Uniques/Lizardman shaman}}", "{{Uniques/Lizardman shaman|boosted=yes}}")):
            parent, unique, raw = self.inputs()
            self.change(parent, old, new)
            with self.assertRaises(ValueError):
                source.extract(parent, unique, raw)

    def test_existing_source_conflict_requires_reconciliation(self):
        parent, unique, raw = self.inputs()
        raw.append({"monsterName": "Lizardman shaman", "itemName": "Dragon warhammer"})
        with self.assertRaisesRegex(ValueError, "already recorded"):
            source.extract(parent, unique, raw)
