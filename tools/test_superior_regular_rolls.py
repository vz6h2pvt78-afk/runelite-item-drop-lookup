import copy
from collections import Counter
import hashlib
import json
import unittest

import build_superior_regular_rolls as source
from validate_access_requirements import validate


class SuperiorRegularRollTest(unittest.TestCase):
    def inputs(self, monster):
        pin = json.loads((source.PINS / (monster.replace(" ", "_") + ".json")).read_text(encoding="utf8"))
        drops = json.loads((source.DATA / "wiki_monster_drops.json").read_text(encoding="utf8"))
        return pin, drops

    def test_exact_category_counts_and_packaged_resource(self):
        for name, expected in source.EXPECTED.items():
            result = source.classify(*self.inputs(name))
            self.assertEqual(expected, Counter(row["category"] for row in result))
        facts = source.build()
        self.assertEqual(facts, json.loads(source.OUT.read_text(encoding="utf8")))
        self.assertEqual(Counter({"Cave abomination": 14, "Marble gargoyle": 19, "Choke devil": 23}),
                         Counter(fact["monsterName"] for fact in facts["facts"]))
        locations = json.loads((source.DATA / "monster_locations.json").read_text(encoding="utf8"))
        self.assertEqual([], validate(facts, locations, self.inputs("Cave abomination")[1]))

    def test_each_revision_provenance_and_explicit_roll_statement_are_required(self):
        for name in source.REVISIONS:
            for field in ("revisionId", "wikitextSha256", "wikitext"):
                pin, drops = self.inputs(name)
                if field == "revisionId":
                    pin[field] += 1
                elif field == "wikitextSha256":
                    pin[field] = "0" * 64
                else:
                    pin[field] = pin[field].replace(source.SUPPORT, "Unverified roll basis")
                    pin["wikitextSha256"] = hashlib.sha256(pin[field].encode()).hexdigest()
                with self.assertRaisesRegex(ValueError, "primary"):
                    source.classify(pin, drops)

    def test_regular_roll_marker_section_and_variant_metadata_fail_closed(self):
        changes = [("|rolls=3", ""), ("===Weapons and armour===", "===Unreviewed==="),
                   ("|rolls=3", "|rolls=3|altrarity=1/2")]
        for name in source.REVISIONS:
            for old, new in changes:
                pin, drops = self.inputs(name)
                start = pin["wikitext"].index("===Weapons and armour===")
                pin["wikitext"] = pin["wikitext"][:start] + pin["wikitext"][start:].replace(old, new, 1)
                pin["wikitextSha256"] = hashlib.sha256(pin["wikitext"].encode()).hexdigest()
                with self.assertRaises(ValueError):
                    source.classify(pin, drops)

    def test_changed_raw_signature_notes_or_duplicate_withholds_build(self):
        for name in source.REVISIONS:
            for field, value in (("quantity", "999"), ("dropRate", "1/999"), ("notes", "Changed")):
                pin, drops = self.inputs(name)
                row = next(drop for drop in drops if drop["monsterName"] == name and drop["itemName"].startswith("Adamant"))
                row[field] = value
                with self.assertRaises(ValueError):
                    source.classify(pin, drops)
            pin, drops = self.inputs(name)
            drops.append(copy.deepcopy(next(drop for drop in drops if drop["monsterName"] == name)))
            with self.assertRaisesRegex(ValueError, "ambiguous"):
                source.classify(pin, drops)

    def test_pre_roll_guaranteed_and_tertiary_rows_are_excluded(self):
        facts = source.build()["facts"]
        for fact in facts:
            self.assertTrue(fact["perRoll"])
            self.assertEqual(("ITEM", "DROP_CONTEXT", "CONTEXT"),
                             tuple(fact[key] for key in ("scope", "appliesTo", "type")))
            self.assertNotEqual("Always", fact["dropSignature"]["dropRate"])
            self.assertNotIn(fact["itemName"], {"Big bones", "Bones", "Brimstone key", "Ensouled horror head",
                                              "Long bone", "Curved bone", "Brittle key"})
            if fact["monsterName"] == "Marble gargoyle":
                self.assertNotIn(fact["itemName"], {"Granite maul", "Mystic robe top (dark)"})
        nature = [fact for fact in facts if fact["monsterName"] == "Cave abomination" and fact["itemName"] == "Nature rune"]
        self.assertEqual(3, len(nature))
        self.assertEqual(3, len({fact["id"] for fact in nature}))


if __name__ == "__main__":
    unittest.main()
