import hashlib
import json
import unittest

import build_shrimp_cooking as cooking


class CookingEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.cooked = json.loads(cooking.PIN.read_text(encoding="utf8"))
        self.raw = json.loads(cooking.RAW_PIN.read_text(encoding="utf8"))["sources"]["Raw shrimps"]

    def test_reproduce_conditional_output_without_probability(self):
        data = cooking.build(self.cooked, self.raw)
        self.assertEqual(data, json.loads(cooking.OUTPUT.read_text(encoding="utf8")))
        recipe, = data["recipes"]
        self.assertEqual("ON_SUCCESS", recipe["outputBasis"])
        self.assertEqual("BURN", recipe["failure"])
        self.assertEqual("Cooking range", recipe["facility"])
        self.assertEqual({"itemName": "Shrimps", "quantity": 1}, recipe["output"])

    def test_missing_rendered_quantities_are_withheld(self):
        self.cooked["facilityRenderedHtml"] = "<table></table>"
        self.cooked["facilityHtmlSha256"] = hashlib.sha256(b"<table></table>").hexdigest()
        with self.assertRaisesRegex(ValueError, "Rendered recipe"):
            cooking.build(self.cooked, self.raw)
