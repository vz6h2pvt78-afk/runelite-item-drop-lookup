import copy
import hashlib
import unittest

from build_ground_sources import load_fixture, build_proposal, extract_row, section_at
from build_monster_locations import templates, boolean, clean


class GroundSourceTests(unittest.TestCase):
    def setUp(self):
        self.fixture = load_fixture()
        self.review = copy.deepcopy(self.fixture['reviewedRows'][0])
        self.source = copy.deepcopy(self.fixture['sources']['Spade'])

    def replace_row(self, transform, repin=False):
        raw = next(self.source['wikitext'][a:b] for a, b, n, p, _ in templates(self.source['wikitext'])
                   if n == 'itemspawnline' and clean(p.get('location', '')) == self.review['location'])
        replacement = transform(raw)
        self.source['wikitext'] = self.source['wikitext'].replace(raw, replacement)
        if repin:
            self.review['templateSha256'] = hashlib.sha256(replacement.encode()).hexdigest()

    def test_exact_slice_preserves_ids_points_and_unknown_pickup_quantity(self):
        proposal = build_proposal(self.fixture)
        self.assertEqual(12, len(proposal['records']))
        self.assertEqual({952, 946, 1925, 231, 223}, {r['itemId'] for r in proposal['records']})
        self.assertEqual(16, sum(r['spawnPointCount'] for r in proposal['records']))
        self.assertTrue(all(r['pickupQuantity'] is None for r in proposal['records']))
        bucket = [r for r in proposal['records'] if r['itemName'] == 'Bucket']
        self.assertEqual(3, len(bucket))
        self.assertEqual(4, sum(r['spawnPointCount'] for r in bucket))
        self.assertTrue(all('respawn' not in str(r).lower() for r in proposal['records']))

    def test_scenery_template_is_rejected_even_at_an_allowed_location(self):
        self.replace_row(lambda r: r.replace('ItemSpawnLine', 'ObjectLocLine'), repin=True)
        with self.assertRaisesRegex(ValueError, 'Scenery'):
            extract_row(self.source, self.review)

    def test_scenery_section_is_rejected_even_with_itemspawnline(self):
        self.source['wikitext'] = self.source['wikitext'].replace('===Item spawns===', '===Scenery object spawns===')
        self.review['section'][-1] = 'Scenery object spawns'
        with self.assertRaisesRegex(ValueError, 'non-ground section'):
            extract_row(self.source, self.review)

    def test_real_net_page_proves_itemspawnline_is_not_a_method_classifier(self):
        source = self.fixture['sources']['Small fishing net']
        scenery = [t for t in templates(source['wikitext']) if t[2] == 'itemspawnline'
                   and 'Scenery object spawns' in section_at(source['wikitext'], t[0])]
        self.assertEqual(8, len(scenery))
        with self.assertRaisesRegex(ValueError, 'outside approved'):
            extract_row(source, self.review)

    def test_missing_membership_stays_unknown_and_blocks_this_reviewed_slice(self):
        self.assertIsNone(boolean(''))
        self.replace_row(lambda r: r.replace('members=No', 'members='), repin=True)
        with self.assertRaisesRegex(ValueError, 'Unknown membership'):
            extract_row(self.source, self.review)

    def test_object_id_cannot_be_used_as_item_identity(self):
        self.source['wikitext'] = self.source['wikitext'].replace('|id = 952', '|id = 9662')
        with self.assertRaisesRegex(ValueError, 'canonical item identity'):
            extract_row(self.source, self.review)

    def test_scenery_infobox_is_not_an_item_infobox(self):
        self.source['wikitext'] = self.source['wikitext'].replace('Infobox Item', 'Infobox Scenery')
        with self.assertRaisesRegex(ValueError, 'item infobox'):
            extract_row(self.source, self.review)

    def test_referenced_pickup_restriction_is_not_silently_stripped(self):
        self.replace_row(lambda r: r.replace('[[Draynor Manor]]', '[[Draynor Manor]]{{Refn|Requires Telekinetic Grab.}}'), repin=True)
        with self.assertRaisesRegex(ValueError, 'restriction needs'):
            extract_row(self.source, self.review)

    def test_unknown_display_template_blocks_generation(self):
        self.replace_row(lambda r: r.replace('[[Draynor Manor]]', '[[Draynor Manor]]{{Unreviewed pickup condition}}'), repin=True)
        with self.assertRaisesRegex(ValueError, 'restriction needs'):
            extract_row(self.source, self.review)

    def test_coordinate_qty_annotation_is_not_assumed_to_be_pickup_quantity(self):
        self.replace_row(lambda r: r.replace('3120,3359', '3120,3359,qty:3'), repin=True)
        with self.assertRaisesRegex(ValueError, 'quantity annotation'):
            extract_row(self.source, self.review)

    def test_explicit_spawn_override_is_not_counted_as_quantity(self):
        self.replace_row(lambda r: r[:-2] + '|spawns=3}}', repin=True)
        with self.assertRaisesRegex(ValueError, 'count override'):
            extract_row(self.source, self.review)

    def test_unreviewed_template_change_is_detected(self):
        self.replace_row(lambda r: r[:-2] + ' }}')
        with self.assertRaisesRegex(ValueError, 'template changed'):
            extract_row(self.source, self.review)

    def test_revision_is_provenance_not_stable_row_identity(self):
        original, before = extract_row(self.source, self.review)
        self.source['revisionId'] += 1
        revised, after = extract_row(self.source, self.review)
        self.assertEqual(original['id'], revised['id'])
        self.assertNotEqual(before['revisionId'], after['revisionId'])
        self.assertIsNone(after['plane'])

    def test_duplicate_review_cannot_inflate_coverage(self):
        self.fixture['reviewedRows'][1] = copy.deepcopy(self.fixture['reviewedRows'][0])
        with self.assertRaisesRegex(ValueError, 'Duplicate source'):
            build_proposal(self.fixture)


if __name__ == '__main__':
    unittest.main()
