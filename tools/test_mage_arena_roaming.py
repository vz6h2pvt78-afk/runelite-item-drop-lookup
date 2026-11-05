import hashlib
import json
import unittest
import build_mage_arena_roaming as source


class MageArenaRoamingTest(unittest.TestCase):
    def test_six_exact_existing_rows_reproduce(self):
        data = source.build()
        self.assertEqual(data, json.loads(source.OUT.read_text(encoding="utf8")))
        self.assertEqual(6, len(data["encounters"]))
        self.assertEqual(set(source.MONSTERS), {row["monsterName"] for row in data["encounters"]})
        for row in data["encounters"]:
            self.assertEqual("ROAMING_ENCOUNTER", row["kind"])
            self.assertTrue(row["wilderness"])
            self.assertTrue(row["membersOnly"])
            self.assertEqual("Deep Wilderness", row["area"])
            self.assertEqual(2, len(row["evidence"]))
            self.assertNotIn("locationIds", row)
            self.assertNotIn("wildernessLevel", row)

    def test_shared_context_requires_exact_revision_and_explicit_support(self):
        pin = source.read_pin(source.QUEST)
        for field, value in (("revisionId", source.QUEST_REVISION + 1), ("title", "Mage Arena I"),
                             ("wikitextSha256", "0" * 64)):
            with self.subTest(field=field), self.assertRaises(ValueError):
                source.validate_shared(dict(pin, **{field: value}))
        for fragment in source.SHARED_SUPPORT:
            changed = dict(pin)
            changed["wikitext"] = changed["wikitext"].replace(fragment, "")
            changed["wikitextSha256"] = hashlib.sha256(changed["wikitext"].encode()).hexdigest()
            with self.subTest(fragment=fragment), self.assertRaises(ValueError):
                source.validate_shared(changed)

    def test_individual_identity_membership_and_revision_are_checked(self):
        for monster, (revision, _) in source.MONSTERS.items():
            pin = source.read_pin(monster)
            for field, value in (("revisionId", revision + 1), ("title", monster + " (historical)")):
                with self.subTest(monster=monster, field=field), self.assertRaises(ValueError):
                    source.validate_primary(dict(pin, **{field: value}), monster, revision, "infobox monster")
            changed = dict(pin)
            changed["wikitext"] = changed["wikitext"].replace("|members = Yes", "|members = No")
            self.assertNotEqual(pin["wikitext"], changed["wikitext"])
            changed["wikitextSha256"] = hashlib.sha256(changed["wikitext"].encode()).hexdigest()
            with self.subTest(monster=monster), self.assertRaises(ValueError):
                source.validate_primary(changed, monster, revision, "infobox monster")
