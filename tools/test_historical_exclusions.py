import hashlib
import json
import unittest
import build_historical_exclusions as source


class HistoricalExclusionTest(unittest.TestCase):
    def test_exact_reviewed_source_reproduces(self):
        data = source.build()
        self.assertEqual(data, json.loads(source.OUT.read_text(encoding="utf8")))
        self.assertEqual(set(source.REVISIONS), {row["monsterName"] for row in data["sources"]})
        self.assertEqual(6, len(data["sources"]))

    def test_revision_identity_and_explicit_prose_are_required(self):
        pin = json.loads(next(source.PINS.glob("*.json")).read_text(encoding="utf8"))
        for field, value in (("revisionId", 15204802), ("title", "Bloodthirsty rockslug"),
                             ("support", "{{Gone}}"), ("wikitextSha256", "0" * 64)):
            with self.subTest(field=field), self.assertRaises(ValueError):
                source.exclusion(dict(pin, **{field: value}))
        changed = dict(pin)
        changed["wikitext"] = changed["wikitext"].replace(pin["support"], "{{Gone}}")
        changed["wikitextSha256"] = hashlib.sha256(changed["wikitext"].encode()).hexdigest()
        with self.assertRaises(ValueError):
            source.exclusion(changed)

    def test_each_primary_requires_its_own_revision_and_prose(self):
        for file in source.PINS.glob("*.json"):
            pin = json.loads(file.read_text(encoding="utf8"))
            for field, value in (("revisionId", pin["revisionId"] + 1), ("support", "{{Gone}}")):
                with self.subTest(title=pin["title"], field=field), self.assertRaises(ValueError):
                    source.exclusion(dict(pin, **{field: value}))

    def test_raw_rows_and_ordinary_counterparts_remain(self):
        raw = json.loads((source.ROOT / "src/main/resources/wiki_monster_drops.json").read_text(encoding="utf8"))
        reviewed = [row for row in raw if row["monsterName"] == "Bloodthirst rockslug"]
        self.assertEqual(13, len(reviewed))
        self.assertEqual(12, len({row["itemName"] for row in reviewed}))
        for name in ("Rockslug", "Giant rockslug"):
            self.assertTrue(any(row["monsterName"] == name for row in raw), name)
