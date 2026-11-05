import json
import unittest

from build_encounter_lairs import OUTPUT, PAGES, PINS, build, encounter


class ReviewedEncounterTest(unittest.TestCase):
    def test_only_the_two_pinned_fighting_lairs_are_packaged(self):
        self.assertEqual(json.loads(OUTPUT.read_text(encoding="utf8")), build())
        self.assertEqual([(r["monsterName"], r["lair"], r["wildernessLevel"]) for r in build()["lairs"]],
                         [("Spindel", "Web Chasm", 29), ("Calvar'ion", "Skeletal Tomb", 21)])

    def test_secondary_mentions_are_insufficient(self):
        key, name, revision = PAGES[0]
        original = json.loads((PINS / (key + ".json")).read_text(encoding="utf8"))
        for patch in ({"lead": original["lead"].replace("who is fought in", "who sometimes travels near")},
                      {"title": "Spindel (quest)"}, {"revisionId": revision + 1}, {"membersField": ""}):
            with self.subTest(patch=patch), self.assertRaises(ValueError):
                encounter(dict(original, **patch), name, revision)
