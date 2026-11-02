import collections
import json
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch

from build_monster_locations import (WikiClient, access_notes, bind_drops, clean,
                                     drop_key, parse_locations, reference_definitions, templates)
from validate_monster_locations import validate


class LocationParserTest(unittest.TestCase):
    def rows(self, text, monster="Example", rules=None):
        self.issues = collections.defaultdict(list)
        return parse_locations(monster, {"title": monster, "revisionId": 123}, text, rules or {}, self.issues)

    def test_nested_templates_comments_and_spawn_count(self):
        rows = self.rows("""<!-- {{LocLine|location=Wrong}} -->
        {{LocLine|location=[[Place]] ({{FloorNumber|uk=1}})|levels=104,109|members=Y
        |x:123,y:456|x:124,y:457<!--|x:0,y:0-->|mtype=pin|dropversion=One, Two}}""")
        self.assertEqual(1, len(rows))
        self.assertEqual("Place (floor 1 (UK))", rows[0]["location"])
        self.assertEqual([104, 109], rows[0]["levels"])
        self.assertEqual(2, rows[0]["spawns"])
        self.assertTrue(rows[0]["members"])
        self.assertEqual(["One", "Two"], rows[0]["dropVersions"])

    def test_unknown_values_are_not_guessed(self):
        row = self.rows("{{LocLine|location=Unknown place|levels=10-20|leagueRegion=Wilderness}}")[0]
        self.assertEqual([], row["levels"])
        self.assertEqual("10-20", row["levelsText"])
        self.assertIsNone(row["members"])
        self.assertIsNone(row["spawns"])
        self.assertEqual("UNKNOWN", row["wilderness"])

    def test_polygon_coordinates_are_not_spawn_counts(self):
        row = self.rows("{{LocLine|location=Place|mtype=polygon|100,200|110,220}}")[0]
        self.assertIsNone(row["spawns"])

    def test_explicit_spawn_count_takes_precedence(self):
        row = self.rows("{{LocLine|location=Place|spawns=7|100,200}}")[0]
        self.assertEqual(7, row["spawns"])
        self.assertEqual("wiki count", row["spawnCountBasis"])

    def test_f2p_and_multiple_regions(self):
        row = self.rows("{{LocLine|location=Place|members=n|leagueRegion=misthalin&asgarnia}}")[0]
        self.assertFalse(row["members"])
        self.assertEqual(["Asgarnia", "Misthalin"], row["leagueRegions"])

    def test_unreachable_footnotes_and_named_references(self):
        text = """{{LocLine|location=[[Island]]{{Refn|Inaccessible as the island cannot be moored on|name=a}}}}
        {{LocLine|location=Other island{{NamedRef|name=a}}}}"""
        rows = self.rows(text)
        self.assertTrue(all(not row["available"] for row in rows))
        self.assertIn("cannot be moored", rows[1]["notes"])

    def test_access_requirement_survives_markup(self):
        row = self.rows("{{LocLine|location=[[Cave]]<ref>Requires {{SCP|Agility|70|link=yes}} or a [[dusty key]].</ref>}}")[0]
        self.assertEqual("Cave", row["location"])
        self.assertIn("70 Agility or a dusty key", row["notes"])

    def test_ambiguous_shared_location_uses_page_specific_override(self):
        rules = {("example", "edgeville dungeon"): {"wilderness": "YES", "source": "reviewed"}}
        self.assertEqual("YES", self.rows("{{LocLine|location=Edgeville Dungeon}}", rules=rules)[0]["wilderness"])
        self.assertEqual("UNKNOWN", self.rows("{{LocLine|location=Edgeville Dungeon}}", monster="Other", rules=rules)[0]["wilderness"])

    def test_malformed_and_duplicate_rows_reported(self):
        rows = self.rows("{{LocLine|levels=2}}{{LocLine|location=Place}}{{LocLine|location=Place}}")
        self.assertEqual(1, len(rows))
        self.assertEqual(1, len(self.issues["malformedRecords"]))
        self.assertEqual(1, len(self.issues["suspectedDuplicates"]))
        self.assertEqual([], list(templates("{{LocLine|location=Never closed")))

    def test_unknown_formatting_does_not_leave_empty_parentheses(self):
        self.assertEqual("Place", clean("[[Place]] ({{Unknown|ignored}})"))


class DropBindingTest(unittest.TestCase):
    def bind(self, wiki, existing=None):
        rows = [{"id": "normal", "dropVersions": ["Standard"]}, {"id": "wild", "dropVersions": ["Wilderness"]}]
        self.drop = existing or {"monsterName": "Example", "itemName": "Item", "quantity": "1", "dropRate": "1/10", "notes": ""}
        self.issues = collections.defaultdict(list)
        return bind_drops("Example", wiki, rows, {drop_key(self.drop): self.drop}, self.issues)

    def test_specific_table_does_not_leak_into_other_variant(self):
        bindings = self.bind("{{DropsTableHead|dropversion=Standard}}{{DropsLine|name=Item|quantity=1|rarity=1/10}}{{DropsTableBottom}}")
        self.assertEqual(["normal"], bindings[0]["locationIds"])
        self.assertEqual(["explicit-variant"], bindings[0]["matchMethods"])

    def test_identical_signatures_retain_all_verified_associations(self):
        bindings = self.bind("{{DropsTableHead|dropversion=Standard}}{{DropsLine|name=Item|quantity=1|rarity=1/10}}{{DropsTableBottom}}"
                             "{{DropsTableHead|dropversion=Wilderness}}{{DropsLine|name=Item|quantity=1|rarity=1/10}}{{DropsTableBottom}}")
        self.assertEqual(["normal", "wild"], bindings[0]["locationIds"])
        self.assertEqual(["Standard", "Wilderness"], bindings[0]["dropVersions"])

    def test_common_table_can_match_all_levels(self):
        bindings = self.bind("{{DropsTableHead}}{{DropsLine|name=Item|quantity=1|rarity=1/10}}{{DropsTableBottom}}")
        self.assertEqual(["normal", "wild"], bindings[0]["locationIds"])
        self.assertEqual(["common-unversioned-table"], bindings[0]["matchMethods"])

    def test_unresolved_occurrence_invalidates_entire_signature(self):
        bindings = self.bind("{{DropsTableHead|dropversion=Standard}}{{DropsLine|name=Item|quantity=1|rarity=1/10}}{{DropsTableBottom}}"
                             "{{DropsTableHead|dropversion=Unknown}}{{DropsLine|name=Item|quantity=1|rarity=1/10}}{{DropsTableBottom}}")
        self.assertEqual([], bindings)
        self.assertEqual(1, len(self.issues["variantMatchingIssues"]))

    def test_conditional_or_changed_drop_is_never_verified(self):
        for extra in ("|raritynotes=Only in Wilderness", "|altrarity=1/5", "|leagueRegion=Wilderness", "|rolls=2"):
            self.assertEqual([], self.bind("{{DropsTableHead}}{{DropsLine|name=Item|quantity=1|rarity=1/10" + extra + "}}"))
        self.assertEqual([], self.bind("{{DropsTableHead}}{{DropsLine|name=Item|quantity=1|rarity=1/20}}"))

    def test_citations_are_distinct_from_drop_conditions(self):
        bindings = self.bind("{{DropsTableHead|dropversion=Standard}}{{DropsLine|name=Item|quantity=1|rarity=1/10|raritynotes={{CiteForum|name=source|url=https://example.test}}}}{{DropsTableBottom}}"
                             "{{DropsTableHead|dropversion=Wilderness}}{{DropsLine|name=Item|quantity=1|rarity=1/10|raritynotes={{NamedRef|source}}}}{{DropsTableBottom}}")
        self.assertEqual(["normal", "wild"], bindings[0]["locationIds"])
        self.assertEqual([], self.bind("{{DropsTableHead}}{{DropsLine|name=Item|quantity=1|rarity=1/10|raritynotes=Only during a quest {{CiteForum|url=https://example.test}}}}"))

    def test_no_cross_page_or_rate_matching(self):
        self.assertEqual([], self.bind("{{DropsTableHead}}{{DropsLine|name=Item|quantity=2|rarity=1/10}}"))
        other = {"monsterName": "Example (variant)", "itemName": "Item", "quantity": "1", "dropRate": "1/10", "notes": ""}
        self.assertEqual([], self.bind("{{DropsTableHead}}{{DropsLine|name=Item|quantity=1|rarity=1/10}}", other))


class WikiClientTest(unittest.TestCase):
    def test_offline_cache_and_missing_pages(self):
        with tempfile.TemporaryDirectory() as directory:
            client = WikiClient(Path(directory), offline=True)
            value = {"title": "Example", "wikitext": "text", "revisionId": 123}
            client.path("Example").write_text(json.dumps(value), encoding="utf-8")
            with patch.object(client, "request", side_effect=AssertionError("network used")):
                result = client.fetch_batch(["Example", "Missing"])
            self.assertEqual(value, result["Example"])
            self.assertEqual("not cached", result["Missing"]["error"])

    def test_retries_are_bounded(self):
        import urllib.error
        with tempfile.TemporaryDirectory() as directory, patch("time.sleep"), patch("urllib.request.urlopen", side_effect=urllib.error.URLError("unavailable")) as request:
            with self.assertRaises(urllib.error.URLError):
                WikiClient(Path(directory)).request(["Example"])
            self.assertEqual(4, request.call_count)


if __name__ == "__main__":
    unittest.main()
