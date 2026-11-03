import json
from pathlib import Path
import unittest
from build_shop_sources import parse_shop_page


def synthetic_html(price, name="Test item", extra_header="", extra_cell=""):
    return ("<table><tr><th colspan='2'>Item</th><th>Price sold at</th><th>GE price</th>"
            + extra_header + "</tr><tr><td></td><td>" + name + "</td><td>" + price
            + "</td><td>999</td>" + extra_cell + "</tr></table>")


def metadata(currency, name="Test item", row_currency=""):
    return ("{{StoreTableHead|currency=" + currency + "}}\n{{StoreLine|name=" + name
            + ("|currency=" + row_currency if row_currency else "") + "}}\n{{StoreTableBottom}}")


def icon(currency):
    return '<a href="/w/' + currency.replace(" ", "_") + '"><img/></a>'


class ShopCurrencyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        data = json.loads((Path(__file__).parent / "fixtures/shop_currency_samples.json").read_text(encoding="utf-8"))
        cls.samples = {sample["page"]: sample for sample in data["samples"]}

    def rows(self, page, item):
        sample = self.samples[page]
        return [row for row in parse_shop_page(page, sample["wikitext"], sample["html"]) if row["itemName"] == item]

    def test_coin_prices_and_pack_units_are_preserved(self):
        page = "Betty's Magic Emporium"
        self.assertEqual("4 coins", self.rows(page, "Air rune")[0]["cost"])
        self.assertEqual("430 coins", self.rows(page, "Air rune pack")[0]["cost"])
        self.assertEqual("300 coins", self.rows(page, "Eye of newt pack")[0]["cost"])

    def test_slayer_points_are_not_coins_despite_ge_column(self):
        row = self.rows("Slayer Rewards", "Rune pouch")[0]
        self.assertEqual("750 Slayer points", row["cost"])
        self.assertNotIn("stock depletes", row["notes"])
        self.assertEqual("35 Slayer points", self.rows("Slayer Rewards", "Broad bolts (x250)")[0]["cost"])

    def test_tokkul_and_glove_discount_remain_distinct(self):
        rows = self.rows("TzHaar-Hur-Lek's Ore and Gem Store", "Uncut onyx")
        self.assertEqual(["300,000 Tokkul", "260,000 Tokkul"], [row["cost"] for row in rows])
        self.assertNotIn("Karamja gloves", rows[0]["notes"])
        self.assertIn("Karamja gloves", rows[1]["notes"])

    def test_current_brimhaven_tables_use_different_currencies(self):
        self.assertEqual("3 Brimhaven vouchers", self.rows("Agility Arena Store", "Toadflax")[0]["cost"])
        self.assertEqual("60 Brimhaven vouchers", self.rows("Agility Arena Store", "Amylase pack")[0]["cost"])
        self.assertEqual("1 Agility arena ticket", self.rows("Agility Arena Store", "Agility XP")[0]["cost"])

    def test_discontinued_ticket_not_conflated_with_current_voucher(self):
        self.assertEqual("3 historical Agility arena tickets",
                         self.rows("Brimhaven Agility Arena Ticket Exchange", "Toadflax")[0]["cost"])

    def test_golden_nuggets_without_a_ge_column(self):
        self.assertEqual("100 golden nuggets", self.rows("Prospector Percy's Nugget Shop", "Coal bag")[0]["cost"])

    def test_forestry_does_not_present_bark_only_as_complete_cost(self):
        row = self.rows("Forestry Shop", "Log basket")[0]
        self.assertEqual("", row["cost"])
        self.assertIn("Additional purchase costs", row["notes"])
        self.assertEqual("2,500 Anima-infused bark", self.rows("Forestry Shop", "Cape pouch")[0]["cost"])
        self.assertEqual("Free", self.rows("Forestry Shop", "Forestry kit")[0]["cost"])

    def test_ge_column_alone_is_not_currency_evidence(self):
        self.assertEqual("", parse_shop_page("Unknown shop", "", synthetic_html("75"))[0]["cost"])

    def test_conflicting_cell_and_table_currency_remain_unknown(self):
        self.assertEqual("", parse_shop_page("Example", metadata("Tokkul"), synthetic_html(icon("Coins") + "75"))[0]["cost"])

    def test_generic_points_need_reviewed_context(self):
        self.assertEqual("", parse_shop_page("Unknown rewards", metadata("Points"), synthetic_html("75"))[0]["cost"])

    def test_unmatched_tables_do_not_inherit_neighboring_currency(self):
        wiki = metadata("Brimhaven voucher", "A") + metadata("Agility arena ticket", "B")
        html = synthetic_html("1", "B") + synthetic_html("2", "A")
        self.assertEqual(["", ""], [r["cost"] for r in parse_shop_page("Example", wiki, html)])

    def test_explicit_row_currency_override_is_respected(self):
        wiki = metadata("Coins", row_currency="Tokkul")
        self.assertEqual("75 Tokkul", parse_shop_page("Example", wiki, synthetic_html(icon("Tokkul") + "75"))[0]["cost"])

    def test_multiple_currency_icons_are_ambiguous_even_with_table_default(self):
        price = icon("Coins") + icon("Tokkul") + "75"
        self.assertEqual("", parse_shop_page("Example", metadata("Coins"), synthetic_html(price))[0]["cost"])

    def test_ranges_and_composite_numbers_are_never_concatenated(self):
        for value in ("3-5", "10 + 20", "1.5", "10[1]", "0", "N/A"):
            with self.subTest(value=value):
                self.assertEqual("", parse_shop_page("Example", metadata("Coins"), synthetic_html(value))[0]["cost"])
        self.assertEqual("16,640 coins", parse_shop_page("Example", metadata("Coins"), synthetic_html("16,640"))[0]["cost"])


if __name__ == "__main__":
    unittest.main()
