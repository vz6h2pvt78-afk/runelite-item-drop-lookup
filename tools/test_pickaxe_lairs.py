import json
import unittest
import build_pickaxe_lairs as source


class PickaxeLairTest(unittest.TestCase):
    def test_four_exact_unbound_pickaxe_rows_keep_their_own_rates(self):
        data=source.build()
        self.assertEqual(data,json.loads(source.OUTPUT.read_text(encoding="utf8")))
        expected={"Artio":("1/358",21), "Callisto":("1/256",40), "Vet'ion":("1/256",35), "Venenatis":("1/256",35)}
        self.assertEqual(expected,{r["monsterName"]:(r["dropRate"],r["wildernessLevel"]) for r in data["encounters"]})
        for row in data["encounters"]:
            self.assertEqual(("Dragon pickaxe","1",""),(row["itemName"],row["quantity"],row["notes"]))
