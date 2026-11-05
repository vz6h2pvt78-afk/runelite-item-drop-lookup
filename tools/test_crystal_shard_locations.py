import copy
import hashlib
import json
import unittest

import build_crystal_shard_locations as source


class CrystalShardLocationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.drops = json.loads((source.DATA / "wiki_monster_drops.json").read_text(encoding="utf8"))
        cls.locations = json.loads((source.DATA / "monster_locations.json").read_text(encoding="utf8"))

    def pin(self, name):
        return json.loads((source.conditions.PINS / (name.replace(" ", "_") + ".json")).read_text(encoding="utf8"))

    def test_five_exact_associations_reproduce_without_superior_rows(self):
        data = source.build()
        self.assertEqual(data, json.loads(source.OUT.read_text(encoding="utf8")))
        self.assertEqual(set(source.TARGETS), {row["monsterName"] for row in data["bindings"]})
        self.assertEqual(5, len(data["bindings"]))
        for row in data["bindings"]:
            self.assertEqual(("Crystal shard", "3-5", "1/24", ""),
                             tuple(row[key] for key in ("itemName", "quantity", "dropRate", "notes")))

    def test_record_identity_metadata_and_duplicate_ids_fail_closed(self):
        for name, (location_id, _, _) in source.TARGETS.items():
            for field, value in {
                    "monsterName": "Other", "sourcePage": "Other", "npcName": "Other", "revisionId": 1,
                    "location": "Other", "levels": [1], "levelsText": "1", "members": False,
                    "available": False, "wilderness": "YES", "wildernessEvidence": "Other",
                    "dropVersions": ["Other"], "version": "Other", "sourceUrl": "Other"}.items():
                with self.subTest(name=name, field=field):
                    locations = copy.deepcopy(self.locations)
                    next(row for row in locations["locations"] if row["id"] == location_id)[field] = value
                    with self.assertRaises(ValueError):
                        source.extract(self.pin(name), self.drops, locations)
            locations = copy.deepcopy(self.locations)
            locations["locations"].append(copy.deepcopy(next(row for row in locations["locations"] if row["id"] == location_id)))
            with self.assertRaisesRegex(ValueError, "ambiguous"):
                source.extract(self.pin(name), self.drops, locations)

    def test_raw_bindings_including_normalized_dangling_bindings_take_precedence(self):
        for name in source.TARGETS:
            locations = copy.deepcopy(self.locations)
            locations["dropBindings"].append({
                "monsterName": name.upper().replace(" ", "_"), "itemName": "CRYSTAL_SHARD",
                "quantity": "3-5", "dropRate": "1/24", "notes": " ", "locationIds": ["missing"]})
            with self.assertRaisesRegex(ValueError, "precedence"):
                source.extract(self.pin(name), self.drops, locations)

    def test_own_locline_and_table_version_must_agree(self):
        pin = self.pin("Greater Nechryael")
        pin["wikitext"] = pin["wikitext"].replace("|dropversion=Regular", "|dropversion=Unreviewed")
        pin["wikitextSha256"] = hashlib.sha256(pin["wikitext"].encode()).hexdigest()
        with self.assertRaises(ValueError):
            source.extract(pin, self.drops, self.locations)


if __name__ == "__main__":
    unittest.main()
