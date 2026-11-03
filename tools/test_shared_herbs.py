from copy import deepcopy
from fractions import Fraction
import json
import unittest

from build_shared_herbs import (RESOURCES, build_proposal, digest, expected_result,
                                find_calls, herb_weights, load_fixture, rate_text)


class SharedHerbTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture = load_fixture()
        cls.drops = json.loads((RESOURCES/'wiki_monster_drops.json').read_text(encoding='utf-8'))[:cls.fixture['baseDropCount']]
        cls.locations = json.loads((RESOURCES/'monster_locations.json').read_text(encoding='utf-8'))
        cls.locations['dropBindings'] = cls.locations['dropBindings'][:cls.fixture['baseBindingCount']]
        cls.locations['summary'] = cls.fixture['baseLocationSummary']
        cls.proposal = build_proposal(cls.fixture, cls.drops, cls.locations)

    def test_nested_human_expands_once_with_exact_probability(self):
        rows = [p for p in self.proposal['provenance'] if p['record']['monsterName'] == 'Man']
        self.assertEqual(11, len(rows))
        harralander = next(r for r in rows if r['record']['itemName'] == 'Grimy harralander')
        self.assertEqual('161/8192', harralander['effectiveProbability'])
        self.assertEqual('1/50.9', harralander['record']['dropRate'])
        self.assertEqual(['Man', 'Template:Human drop table', 'Template:HerbDropLines'], harralander['call']['path'])
        self.assertEqual([15374703, 15374758, 15325751], [s['revisionId'] for s in harralander['sourceChain']])

    def test_abyssal_rates_and_locations_stay_separate(self):
        rows = [r for r in self.proposal['bindings'] if r['monsterName'] == 'Abyssal demon' and r['itemName'] == 'Grimy harralander']
        self.assertEqual({'1/61.6', '1/32.7'}, {r['dropRate'] for r in rows})
        standard = next(r for r in rows if r['dropRate'] == '1/61.6')
        wild = next(r for r in rows if r['dropRate'] == '1/32.7')
        self.assertEqual(['ec346dbbf5098d24ef88'], wild['locationIds'])
        self.assertEqual(4, len(standard['locationIds']))
        self.assertTrue(set(standard['locationIds']).isdisjoint(wild['locationIds']))

    def test_goblin_only_table_two_without_lumbridge(self):
        bindings = [r for r in self.proposal['bindings'] if r['monsterName'] == 'Goblin']
        for row in bindings:
            self.assertEqual(['Drop table 2'], row['dropVersions'])
            self.assertNotIn('1b37fd40efb415e153fb', row['locationIds'])
            self.assertIn('e1575f3f4ce223e9e170', row['locationIds'])

    def test_f2p_argument_produces_members_only_herbs_and_no_coins(self):
        self.assertTrue(all(r['membersOnly'] is True and r['notes'].startswith('Members-world drop.') for r in self.proposal['records']))
        self.assertTrue(all(r['itemName'].startswith('Grimy ') and r['quantity'] == '1' for r in self.proposal['records']))

    def test_all_44_rows_cross_checked_and_existing_data_unchanged(self):
        drops, locations = expected_result(self.fixture, self.drops, self.locations, self.proposal)
        self.assertEqual(44, len(drops)-len(self.drops))
        self.assertEqual(self.drops, drops[:len(self.drops)])
        self.assertEqual(self.locations['locations'], locations['locations'])
        self.assertEqual(self.locations['dropBindings'], locations['dropBindings'][:len(self.locations['dropBindings'])])
        self.assertEqual(44, len({r['pathId'] for r in self.proposal['provenance']}))
        self.assertTrue(all(r['renderedCrossCheck']['dropRate'].replace(',', '') == r['record']['dropRate'] for r in self.proposal['provenance']))

    def test_unknown_arguments_rolls_quantities_and_numbered_parameters_fail(self):
        for suffix in ['|rolls=2', '|Rolls=2', '|unknown=yes', '|1-2', '|2', '|2=1', '|f2p=no']:
            with self.subTest(suffix=suffix):
                sources = {'Sample': {'wikitext': '{{DropsTableHead}}{{HerbDropLines|23/128'+suffix+'}}{{DropsTableBottom}}'}}
                with self.assertRaises(ValueError):
                    find_calls('Sample', sources)

    def test_invalid_entry_probabilities_fail(self):
        for rate in ['2/1', '1/0', '0/128', '1/128 * 2', '{{#expr:1/128}}']:
            with self.subTest(rate=rate), self.assertRaises(ValueError):
                find_calls('Sample', {'Sample': {'wikitext': '{{DropsTableHead}}{{HerbDropLines|'+rate+'}}{{DropsTableBottom}}'}})

    def test_unexpected_weights_fail(self):
        text = self.fixture['sources']['Template:HerbDropLines']['wikitext']
        with self.assertRaisesRegex(ValueError, 'weights'):
            herb_weights(text.replace('(32*', '(33*'))

    def test_rendered_mismatch_stops_proposal(self):
        fixture = deepcopy(self.fixture)
        fixture['sources']['Man']['renderedHerbs'][0]['dropRate'] = '1/1'
        with self.assertRaisesRegex(ValueError, 'Rendered'):
            build_proposal(fixture, self.drops, self.locations)

    def test_duplicate_transclusion_is_rejected_not_silently_deduplicated(self):
        fixture = deepcopy(self.fixture)
        fixture['sources']['Man']['wikitext'] += '\n{{Human drop table}}'
        with self.assertRaisesRegex(ValueError, 'call structure'):
            build_proposal(fixture, self.drops, self.locations)

    def test_existing_item_collision_is_rejected_even_with_different_notes(self):
        fixture, drops = deepcopy(self.fixture), deepcopy(self.drops)
        drops.append({'itemName':'Grimy harralander','monsterName':'Man','quantity':'1','dropRate':'1/50.9','notes':'A different direct roll'})
        fixture['baseDropSha256'] = digest(drops)
        with self.assertRaisesRegex(ValueError, 'collision'):
            build_proposal(fixture, drops, self.locations)

    def test_baseline_and_location_revision_drift_fail(self):
        drops = deepcopy(self.drops)
        drops[0]['dropRate'] = '1/999'
        with self.assertRaisesRegex(ValueError, 'Starting drop'):
            build_proposal(self.fixture, drops, self.locations)
        fixture = deepcopy(self.fixture)
        fixture['sources']['Man']['revisionId'] += 1
        with self.assertRaisesRegex(ValueError, 'location evidence differs'):
            build_proposal(fixture, self.drops, self.locations)

    def test_exact_math_and_rounding_are_separate(self):
        self.assertEqual('1/32.7', rate_text(Fraction(133, 4352)))
        self.assertEqual('1/256', rate_text(Fraction(1, 256)))
        self.assertEqual('1/1.3', rate_text(Fraction(4, 5)))
        self.assertEqual(Fraction(133, 4352), Fraction('19/68') * Fraction('14/128'))


if __name__ == '__main__':
    unittest.main()
