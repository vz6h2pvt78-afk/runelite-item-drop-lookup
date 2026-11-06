import copy
import json
import unittest
import build_zenyte_production as recipes


class ZenyteEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.pin = json.loads(recipes.PIN.read_text(encoding="utf-8"))

    def test_exact_recipes_reproduce_from_both_primary_forms(self):
        data = recipes.build(self.pin)
        self.assertEqual(data, json.loads(recipes.OUTPUT.read_text(encoding="utf-8")))
        rows = data["recipes"]
        self.assertEqual(["Uncut zenyte", "Zenyte"], [r["output"]["itemName"] for r in rows])
        self.assertEqual(["Zenyte shard", "Onyx"], [r["itemName"] for r in rows[0]["inputs"]])
        self.assertEqual("Find Zenyte shard sources", rows[0]["relatedItemActions"][0]["label"])
        self.assertIn("Crafting 70 (cannot be boosted).", rows[0]["requirements"])
        self.assertIn("Crafting 89 (boosts allowed).", rows[1]["requirements"])

    def test_changed_or_missing_primary_evidence_cannot_generate_routes(self):
        for key in ["wikitext", "renderedHtml"]:
            data = copy.deepcopy(self.pin)
            data["pages"][0][key] = ""
            with self.assertRaises(ValueError):
                recipes.build(data)
        self.pin["pages"][1]["revisionId"] += 1
        with self.assertRaises(ValueError):
            recipes.build(self.pin)

    def test_shard_source_rows_are_not_duplicated_or_replaced(self):
        raw = json.loads((recipes.ROOT / "src/main/resources/wiki_monster_drops.json").read_text())
        rows = [r for r in raw if r["itemName"] in ["Zenyte", "Uncut zenyte", "Zenyte shard"]]
        self.assertEqual([
            {"itemName": "Zenyte shard", "monsterName": "Demonic gorilla", "quantity": "1", "dropRate": "1/300", "notes": ""},
            {"itemName": "Zenyte shard", "monsterName": "Tortured gorilla", "quantity": "1", "dropRate": "1/3000", "notes": ""}
        ], rows)
