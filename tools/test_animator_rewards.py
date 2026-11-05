import hashlib
import json
import unittest
import build_animator_rewards as animator


class AnimatorEvidenceTest(unittest.TestCase):
    def test_explicit_amounts_and_required_suits_match_the_packaged_records(self):
        data = animator.build()
        self.assertEqual(data, json.loads(animator.OUT.read_text(encoding="utf8")))
        expected = {"Bronze":(5,10,15), "Iron":(10,20,30), "Steel":(15,30,45), "Black":(20,40,60),
                    "Mithril":(25,50,75), "Adamant":(30,60,90), "Rune":(40,80,120)}
        self.assertEqual(expected,{r["metal"]:(int(r["quantity"]), r["easyTokens"], r["mediumTokens"]) for r in data["rewards"]})
        for row in data["rewards"]:
            self.assertEqual([row["metal"] + " " + piece for piece in ("platebody","platelegs","full helm")], row["items"])

    def test_cross_tier_piece_and_missing_tier_footnote_are_rejected(self):
        path = animator.PINS / "Adamant.json"
        drops = json.loads((animator.DATA / "wiki_monster_drops.json").read_text(encoding="utf8"))
        for before, after in (("[[adamant platelegs]]", "[[rune platelegs]]"), ("or 90 after unlocking", "after unlocking")):
            pin = json.loads(path.read_text(encoding="utf8"))
            pin["wikitext"] = pin["wikitext"].replace(before, after)
            pin["wikitextSha256"] = hashlib.sha256(pin["wikitext"].encode()).hexdigest()
            with self.assertRaises(ValueError):
                animator.extract(pin, "Adamant", drops)
