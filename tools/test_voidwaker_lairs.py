import json
import unittest

import build_voidwaker_lairs as source


class VoidwakerLairTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin = json.loads(source.PINS.read_text(encoding="utf8"))[0]
        cls.locations = json.loads((source.DATA/"monster_locations.json").read_text(encoding="utf8"))
        cls.drops = json.loads((source.DATA/"wiki_monster_drops.json").read_text(encoding="utf8"))

    def test_only_four_existing_reward_signatures_are_packaged(self):
        self.assertEqual(source.build(), json.loads(source.OUTPUT.read_text(encoding="utf8")))
        self.assertEqual({(r["itemName"], r["monsterName"], r["wildernessLevel"]) for r in source.build()["encounters"]},
                         {("Voidwaker hilt","Artio",21), ("Voidwaker hilt","Callisto",40),
                          ("Voidwaker blade","Vet'ion",35), ("Voidwaker gem","Venenatis",35)})

    def test_stale_lair_or_duplicate_reward_cannot_generate_context(self):
        for patch in ({"revisionId": 1}, {"locationId":"other"},
                      {"primaryEncounterLead":"Artio has a Wilderness connection."}):
            with self.subTest(patch=patch), self.assertRaises(ValueError):
                source.reviewed(dict(self.pin, **patch), self.locations, self.drops)
        duplicate = next(d for d in self.drops if d["monsterName"] == "Artio" and d["itemName"] == "Voidwaker hilt")
        with self.assertRaises(ValueError):
            source.reviewed(self.pin, self.locations, self.drops + [duplicate])
