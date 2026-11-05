import json
import unittest
import build_echo_exclusions as source


class EchoExclusionTest(unittest.TestCase):
    def test_fourteen_exact_sources_reproduce(self):
        data = source.build()
        self.assertEqual(data, json.loads(source.OUT.read_text(encoding="utf8")))
        self.assertEqual(set(source.REVISIONS), {row["monsterName"] for row in data["sources"]})
        self.assertEqual(14, len(data["sources"]))
        self.assertTrue(all(row["monsterName"].endswith(" (Echo)") for row in data["sources"]))

    def test_each_source_requires_its_own_revision_and_league_proof(self):
        for file in source.PINS.glob("*.json"):
            pin = json.loads(file.read_text(encoding="utf8"))
            for field, value in (("revisionId", pin["revisionId"] + 1), ("support", "Echoes exist in the ordinary game"),
                                 ("league", "Ordinary game"), ("title", pin["title"].replace(" (Echo)", ""))):
                changed = dict(pin, **{field: value})
                with self.subTest(source=pin["title"], field=field), self.assertRaises(ValueError):
                    source.exclusion(changed)

    def test_raw_source_rows_and_ordinary_counterparts_are_retained(self):
        raw = json.loads((source.ROOT / "src/main/resources/wiki_monster_drops.json").read_text(encoding="utf8"))
        selected = [row for row in raw if row["monsterName"] in source.REVISIONS]
        self.assertEqual(20, len(selected))
        self.assertEqual(15, len({row["itemName"] for row in selected}))
        other_recorded_sources = {row["monsterName"] for row in raw if row["monsterName"] not in source.REVISIONS}
        for name in ("Kalphite Queen", "King Black Dragon", "Cerberus", "Dagannoth Prime", "Dagannoth Rex",
                     "Dagannoth Supreme", "Corrupted Hunllef", "Hespori", "Thermonuclear smoke devil"):
            self.assertIn(name, other_recorded_sources)
