"""Reproduce twelve reviewed ground pickups offline; no automatic coverage expansion.

Default writes a proposal. --apply writes the new resource/manifest after validation;
--check verifies them and all eight pre-existing JSON resources without writing.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re

from build_monster_locations import templates, clean, boolean, access_notes, reference_definitions

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tools/fixtures/ground_spawns.json'
RESOURCES = ROOT / 'src/main/resources'
RESOURCE = RESOURCES / 'wiki_ground_sources.json'
MANIFEST = ROOT / 'docs/data/ground_sources.json'
FIXTURE_PIN = '0902992a4089345279195322ca84902ea4a125707be1dd570a1cfa4748f28390'
ITEMS = {'Spade': (952, 3), 'Knife': (946, 3), 'Bucket': (1925, 3),
         'Snape grass': (231, 1), "Red spiders' eggs": (223, 2)}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(',', ':')).encode()).hexdigest()


def load_fixture():
    fixture = json.loads(FIXTURE.read_text(encoding='utf-8'))
    require(digest(fixture) == FIXTURE_PIN, 'Reviewed source/allowlist changed; explicit review required')
    return fixture


def section_at(source, offset):
    ancestry = []
    for match in re.finditer(r'^(={2,6})\s*(.*?)\s*\1\s*$', source, re.M):
        if match.start() >= offset:
            break
        while ancestry and ancestry[-1][0] >= len(match[1]):
            ancestry.pop()
        ancestry.append((len(match[1]), match[2]))
    return [entry[1] for entry in ancestry]


def extract_row(source, review):
    title, wikitext = source['title'], source['wikitext']
    require(title in ITEMS, 'Item is outside approved ground slice')
    boxes = [p for _, _, name, p, _ in templates(wikitext) if name == 'infobox item']
    require(len(boxes) == 1, 'Expected one item infobox, not scenery/object identity')
    require(boxes[0].get('name') == title and boxes[0].get('id') == str(ITEMS[title][0]), 'Unexpected canonical item identity')
    require(review['itemId'] == ITEMS[title][0], 'Reviewed item ID changed')
    candidates = [t for t in templates(wikitext) if t[2] in ('itemspawnline', 'objectlocline')
                  and clean(t[3].get('location', '')) == review['location']]
    require(len(candidates) == 1, 'Expected one exact reviewed location')
    start, end, template, params, positional = candidates[0]
    section = section_at(wikitext, start)
    require(template == 'itemspawnline', 'Scenery/object interactions are excluded')
    require(section == review['section'] and section[-1] in ('Spawns', 'Item spawns')
            and not any('scenery' in s.lower() for s in section), 'Unreviewed or non-ground section')
    require(set(params) <= {'name', 'location', 'members', 'mapid', 'plane', 'leagueregion', 'zoom'},
            'Unexpected spawn parameter or count override')
    require(params.get('name', title) == title, 'Spawn item name differs from item identity')
    unknown = set()
    location = clean(params['location'], unknown)
    restrictions = access_notes(params['location'], reference_definitions(wikitext), unknown)
    require(not unknown and not restrictions and not re.search(r'requir|telekinetic|only if', location, re.I),
            'Pickup/access restriction needs explicit review')
    members = boolean(params.get('members', ''))
    require(members is not None, 'Unknown membership is not confirmed F2P')
    require(members == review['membersOnly'], 'Membership changed')
    points = []
    for value in positional:
        match = re.fullmatch(r'(\d+),(\d+)', value)
        require(match is not None, 'Unknown coordinate or quantity annotation')
        points.append({'x': int(match[1]), 'y': int(match[2])})
    require(len(points) == review['spawnPointCount'] and len(points) > 0, 'Spawn-point count changed')
    require(len({(p['x'], p['y']) for p in points}) == len(points), 'Duplicate coordinate')
    raw_template = wikitext[start:end]
    require(hashlib.sha256(raw_template.encode()).hexdigest() == review['templateSha256'], 'Reviewed template changed')
    raw_plane, raw_map = params.get('plane'), params.get('mapid')
    require(raw_plane is None or re.fullmatch(r'[0-3]', raw_plane), 'Unrecognized plane')
    require(raw_map is None or re.fullmatch(r'-?\d+', raw_map), 'Unrecognized map ID')
    identity = {'itemId': ITEMS[title][0], 'method': 'GROUND', 'location': location,
                'coordinates': points, 'plane': int(raw_plane) if raw_plane is not None else None,
                'mapId': int(raw_map) if raw_map is not None else None}
    record = {'id': digest(identity)[:20], 'itemId': ITEMS[title][0], 'itemName': title,
              'sourceType': 'Ground pickup', 'sourceName': location, 'location': location,
              'cost': '', 'notes': ('Members' if members else 'F2P') + ' · '
              + str(len(points)) + (' spawn point' if len(points) == 1 else ' spawn points'),
              'membersOnly': members, 'spawnPointCount': len(points), 'pickupQuantity': None}
    provenance = dict(identity, id=record['id'], itemName=title, membersOnly=members,
                      spawnPointCount=len(points), pickupQuantity=None, accessCoverage='incomplete',
                      verifiedRestrictions=[], sourcePage=title, revisionId=source['revisionId'],
                      sourceUrl=source['sourceUrl'], historyUrl=source['historyUrl'], section=section,
                      template=template, templateSha256=review['templateSha256'], rawTemplate=raw_template,
                      sourceWikitextSha256=hashlib.sha256(wikitext.encode()).hexdigest(),
                      rawSnapshotSha256=source['rawSnapshotSha256'])
    return record, provenance


def build_proposal(fixture):
    require(set(fixture['sources']) == set(ITEMS) | {'Small fishing net'}, 'Source inventory changed')
    require(len(fixture['reviewedRows']) == 12, 'Expected exactly twelve reviewed rows')
    records, provenance = [], []
    for review in fixture['reviewedRows']:
        require(review['page'] in ITEMS, 'Unapproved item or excluded net')
        record, evidence = extract_row(fixture['sources'][review['page']], review)
        records.append(record)
        provenance.append(evidence)
    require(Counter(r['itemName'] for r in records) == Counter({k: v[1] for k, v in ITEMS.items()}), 'Approved per-item counts changed')
    require(len({r['id'] for r in records}) == 12, 'Duplicate source identity')
    require(sum(r['spawnPointCount'] for r in records) == 16, 'Expected sixteen points')
    return {'baseCommit': fixture['baseCommit'], 'recordCount': 12, 'pointCount': 16,
            'records': records, 'provenance': provenance,
            'limitations': ['Only twelve reviewed ground rows; coverage incomplete.',
                           'Access requirements not fully checked; no eligibility inference.',
                           'Pickup quantities and respawn times not supplied.',
                           'Scenery, Small fishing net, conditional and Telekinetic Grab pickups excluded.']}


def run(apply=False, check=False, output=None):
    fixture = load_fixture()
    proposal = build_proposal(fixture)
    for name, expected in fixture['unchangedResourceSha256'].items():
        require(digest(json.loads((RESOURCES / name).read_text(encoding='utf-8'))) == expected,
                'Existing resource changed: ' + name)
    if check:
        require(json.loads(RESOURCE.read_text(encoding='utf-8')) == proposal['records'], 'Runtime ground records differ')
        require(json.loads(MANIFEST.read_text(encoding='utf-8')) == proposal, 'Ground provenance differs')
    elif apply:
        require(not RESOURCE.exists() and not MANIFEST.exists(), 'Slice already exists; use --check')
        RESOURCE.write_text(json.dumps(proposal['records'], ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        MANIFEST.write_text(json.dumps(proposal, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    else:
        output = output or ROOT / '.cache/ground_sources_proposal.json'
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(proposal, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'records': 12, 'points': 16, 'unchangedResources': 8,
                      'mode': 'check' if check else 'apply' if apply else 'proposal'}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--apply', action='store_true')
    mode.add_argument('--check', action='store_true')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    run(args.apply, args.check, args.output)
