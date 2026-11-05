import json
import unittest
import build_basilisk_task_rate as source


class BasiliskTaskRateTest(unittest.TestCase):
    def test_only_exact_basilisk_knight_jaw_gets_the_explicit_conditional_rate(self):
        data=source.build()
        self.assertEqual(data,json.loads(source.OUT.read_text(encoding="utf8")))
        self.assertEqual(1,len(data["rates"]))
        row=data["rates"][0]
        self.assertEqual(("Basilisk Knight","Basilisk jaw","1","1/5000","","Basilisk","1/1000"),
                         tuple(row[k] for k in ("monsterName","itemName","quantity","dropRate","notes","taskName","onTaskRate")))

    def test_missing_task_definition_or_ambiguous_base_drop_is_withheld(self):
        pin=json.loads(source.PIN.read_text(encoding="utf8"))
        drops=json.loads((source.DATA/"wiki_monster_drops.json").read_text(encoding="utf8"))
        for raw in (pin["wikitext"].replace(source.NOTE,"Unverified task"),
                    pin["wikitext"].replace("while on task grants a drop rate five times higher (1/1000)","while on task")):
            with self.assertRaises(ValueError):
                source.extract(raw,drops)
        jaw=next(r for r in drops if r["monsterName"]=="Basilisk Knight" and r["itemName"]=="Basilisk jaw")
        with self.assertRaisesRegex(ValueError,"ambiguous"):
            source.extract(pin["wikitext"],drops+[jaw])
