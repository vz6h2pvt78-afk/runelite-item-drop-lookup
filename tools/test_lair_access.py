import json
import unittest

import build_lair_access as source
from validate_access_requirements import validate


class LairEntranceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pins = json.loads(source.PINS.read_text(encoding="utf8"))
        cls.places = json.loads((source.RESOURCES/"monster_locations.json").read_text(encoding="utf8"))
        cls.drops = json.loads((source.RESOURCES/"wiki_monster_drops.json").read_text(encoding="utf8"))

    def test_exact_entrance_conditions_reproduce(self):
        built = source.build()
        self.assertEqual(built, json.loads(source.OUTPUT.read_text(encoding="utf8")))
        self.assertEqual([], validate(built, self.places, self.drops))
        for pin, fact in zip(self.pins, built["facts"]):
            self.assertEqual("LOCATION", fact["scope"])
            self.assertEqual(pin["locationId"], fact["locationId"])
            self.assertIn(pin["diaryTier"] + " Wilderness Diary or", fact["text"])
            self.assertIn(pin["bossTask"] + " boss Slayer task", fact["text"])

    def test_do_not_substitute_task_tier_location_or_primary(self):
        pin = self.pins[0]
        for patch in ({"bossTask": "Artio"}, {"diaryTier": "medium"}, {"ordinaryTask": "spider"},
                      {"locationId": self.pins[1]["locationId"]}, {"sourceRevision": 1},
                      {"excerpt": pin["excerpt"].replace("'''OR'''", "'''AND'''")}):
            with self.subTest(patch=patch), self.assertRaises(ValueError):
                source.entrance(dict(pin, **patch), self.places["locations"])
