"""Offline allowlisted Raw shrimps / Small net / Lumbridge Swamp projection.

No discovery, combat-drop inference, catch rates, spot counts or recipe expansion.
The aggregate Gathering builder checks and writes the shared runtime resource.
"""
import hashlib
import json
from pathlib import Path
import re

from build_monster_locations import templates
from build_ground_sources import digest, require, section_at

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tools/fixtures/fishing_sources.json'
MANIFEST = ROOT / 'docs/data/fishing_sources.json'
FIXTURE_PIN = '8339bb057d38d8053b10f1f57cc7024c390fad75018a26e57a3d3c3dc4dcddd5'


def load_fixture():
    fixture = json.loads(FIXTURE.read_text(encoding='utf-8'))
    require(digest(fixture) == FIXTURE_PIN, 'Reviewed fishing evidence or scope changed; explicit review required')
    return fixture


def reviewed_template(source, name, section, predicate=lambda p: True):
    matches = [t for t in templates(source['wikitext']) if t[2] == name and predicate(t[3])
               and section_at(source['wikitext'], t[0]) == section]
    require(len(matches) == 1, 'Expected one reviewed ' + name + ' in ' + '/'.join(section))
    return matches[0]


def build_proposal(fixture):
    sources, review = fixture['sources'], fixture['review']
    resource_name = 'Fishing spot (small net, bait)'
    require(set(sources) == {'Raw shrimps', resource_name}, 'Fishing source inventory changed')
    require(review == {'itemId': 317, 'itemName': 'Raw shrimps', 'skill': 'Fishing', 'level': 1,
                       'tool': 'Small fishing net', 'bait': None, 'variant': 'Small net',
                       'resource': resource_name, 'location': 'Lumbridge Swamp',
                       'contextNote': 'Near the Fishing tutor.', 'membersOnly': False},
            'Approved fishing scope changed')
    item, resource = sources['Raw shrimps'], sources[resource_name]
    box = reviewed_template(item, 'infobox item', [])[3]
    require(box.get('name') == 'Raw shrimps' and box.get('id') == '317', 'Canonical output identity changed')
    start, end, _, info, positions = reviewed_template(item, 'fishing info', ['Fishing'])
    require(not positions and set(info) == {'name', 'level', 'xp', 'spot', 'tool', 'bait'},
            'Unexpected fishing skill field; review required')
    require(info['name'] == 'Raw shrimps' and info['spot'] == '[[Fishing spot (small net, bait)]]',
            'Fishing output/resource association changed')
    require(info['level'] == '1', 'Required Fishing level changed')
    require(info['tool'] == 'Small fishing net', 'Required fishing tool changed')
    require(info['bait'] == 'None', 'Bait requirement changed')
    skill_raw = item['wikitext'][start:end]
    require('*[[Lumbridge Swamp]], near the [[Fishing tutor]].' in item['wikitext'],
            'Independent item location/context changed')

    npc = reviewed_template(resource, 'infobox npc', [])[3]
    require(npc.get('name') == 'Fishing spot' and npc.get('options') == 'Small net/Net, Bait',
            'Resource interaction changed')
    # This NPC-backed activity is Fishing, never a kill/drop source. Bind the
    # interaction and output to the Small net section, not the adjacent Bait table.
    net = reviewed_template(resource, 'fishing info', ['Small net'])[3]
    require(net == {'name': 'Fishing spot', 'level': '1', 'xp': 'Varies',
                    'tool': '{{plink|Small fishing net}}', 'bait': 'Nothing',
                    'bucket': 'No', 'dropversion': 'Small net'}, 'Small net method changed')
    table = reviewed_template(resource, 'dropstablehead', ['Small net', 'Main drops'])[3]
    require(table == {'dropversion': 'Small net'}, 'Output table variant changed')
    output = reviewed_template(resource, 'dropslineskill', ['Small net', 'Main drops'],
                               lambda p: p.get('name') == 'Raw shrimps')[3]
    require(output == {'name': 'Raw shrimps', 'quantity': '1', 'rarity': 'Common', 'skill': 'Fishing'},
            'Fishing output evidence changed')

    start, end, _, location, points_raw = reviewed_template(resource, 'objectlocline', ['Locations'],
            lambda p: p.get('location') == '[[Lumbridge Swamp]]')
    require(location == {'name': resource_name, 'location': '[[Lumbridge Swamp]]', 'members': 'No',
                         'mapid': '0', 'plane': '0', 'mtype': 'pin', 'dropversion': 'Small net, Bait',
                         'leagueregion': 'misthalin'}, 'Location membership, variant or fields changed')
    points = []
    for value in points_raw:
        match = re.fullmatch(r'x:(\d+),y:(\d+)', value)
        require(match is not None, 'Unreviewed fishing coordinate annotation')
        points.append({'x': int(match[1]), 'y': int(match[2])})
    require(points == [{'x': 3246, 'y': 3155}, {'x': 3240, 'y': 3147}, {'x': 3240, 'y': 3146},
                       {'x': 3244, 'y': 3150}, {'x': 3245, 'y': 3152}], 'Reviewed map points changed')
    location_raw = resource['wikitext'][start:end]

    identity = {'outputItemId': 317, 'kind': 'GATHERING', 'skill': 'Fishing',
                'resourcePage': resource_name, 'interactionVariant': 'Small net'}
    method_id = digest(identity)[:20]
    occurrence_identity = {'methodId': method_id, 'location': 'Lumbridge Swamp', 'coordinates': points,
                           'plane': 0, 'rawMapId': '0'}
    method = dict(identity, methodId=method_id, actionLabel='Small-net fishing',
                  output={'itemId': 317, 'itemName': 'Raw shrimps', 'quantity': None,
                          'quantityBasis': 'not asserted; output is not guaranteed per attempt'},
                  skills=[{'skill': 'Fishing', 'level': 1, 'boostable': None}],
                  tools=[{'kind': 'CATEGORY', 'name': 'Small fishing net', 'itemId': None}],
                  baitRequired=False, membersOnly=None, accessCoverage='incomplete', verifiedConditions=[])
    occurrence = dict(occurrence_identity, occurrenceId=digest(occurrence_identity)[:20], membersOnly=False,
                      resourceCount=None, resourceCountBasis='not asserted; map points are not an active spot count',
                      accessCoverage='incomplete', verifiedConditions=[],
                      contextNotes=[{'kind': 'LOCATION', 'text': review['contextNote'], 'eligibilityGate': False}])
    record = {'id': occurrence['occurrenceId'], 'itemId': 317, 'itemName': 'Raw shrimps',
              'sourceType': 'Gathering', 'sourceName': method['actionLabel'], 'location': occurrence['location'],
              'cost': '', 'notes': 'Fishing level 1 · Small fishing net required\nNo bait needed · F2P\nNear the Fishing tutor.'}
    provenance = [{k: source[k] for k in ['title', 'revisionId', 'sourceUrl', 'historyUrl', 'rawSnapshotSha256']}
                  | {'wikitextSha256': hashlib.sha256(source['wikitext'].encode()).hexdigest()}
                  for source in (item, resource)]
    return {'baseCommit': fixture['baseCommit'], 'records': [record], 'method': method, 'occurrence': occurrence,
            'provenance': provenance, 'rawSkillTemplate': skill_raw, 'rawLocationTemplate': location_raw,
            'limitations': ['One output, Small net method and named area only; other fishing locations exist.',
                           'Map coordinates are evidence, not simultaneous active spots or navigation.',
                           'No catch quantity, rate, XP, timers, ranking, cooking chain or account eligibility.',
                           'F2P describes this location; access coverage remains explicitly incomplete.']}


def check_unchanged(fixture, previous_records):
    require(previous_records == fixture['previousGatheringRecords'], 'Earlier gathering records changed')
    for name, expected in fixture['unchangedResourceSha256'].items():
        actual = hashlib.sha256((ROOT / 'src/main/resources' / name).read_bytes()).hexdigest()
        require(actual == expected, 'Unrelated runtime resource changed: ' + name)
