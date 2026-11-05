import json
import unittest
import build_drake_task_rates as drakes


class DrakeRateTest(unittest.TestCase):
    def test_two_reviewed_rates_reproduce_without_new_sources_or_conditions(self):
        data = drakes.build()
        self.assertEqual(data,json.loads(drakes.OUT.read_text(encoding="utf8")))
        self.assertEqual({drakes.TOOTH,drakes.CLAW},{r["itemName"] for r in data["rates"]})
        self.assertTrue(all(r["quantity"]=="1" and r["dropRate"]=="1/2560" and r["onTaskRate"]=="1/512"
                            and r["taskName"]=="Drake" and r["notes"]=="" for r in data["rates"]))

    def test_ambiguous_reference_or_raw_row_withholds_the_projection(self):
        pin = json.loads(drakes.PIN.read_text(encoding="utf8"))
        drops = json.loads((drakes.DATA/"wiki_monster_drops.json").read_text(encoding="utf8"))
        with self.assertRaisesRegex(ValueError, "reference"):
            drakes.extract(pin["wikitext"].replace(drakes.DEFINITION,drakes.REUSE),drops)
        matching = next(r for r in drops if r["monsterName"]=="Drake" and r["itemName"]==drakes.CLAW)
        with self.assertRaisesRegex(ValueError,"ambiguous"):
            drakes.extract(pin["wikitext"],drops+[matching])
