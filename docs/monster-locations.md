# Monster locations

NPC cards open an offline location view. Back retains query, source filter, GE
setting, expanded groups, results and scroll position. The old 100-source display
limit has been removed; filters cleared means every source remains accessible.

## Regeneration

Python 3.10+ and the standard library are sufficient. From the repository root:

```powershell
py -3 tools/build_monster_locations.py --monster "Fire giant" --monster Goblin
py -3 tools/build_monster_locations.py --all
py -3 tools/validate_monster_locations.py
py -3 -m unittest discover -s tools -p test_monster_locations.py
```

Individual runs write `.cache/monster_locations_sample.json`, so debugging cannot
replace the full resource by accident. `--output` and `--report` choose other paths.
Full runs use `wiki_drops_monster_pages.json`; `--worklist` accepts another JSON
array of exact wiki titles. The API is queried in batches of 20 titles, at most one
request every 0.5 seconds, with a descriptive User-Agent, `maxlag=5`, 45-second
timeouts and at most four attempts. HTTP 429 / transient server errors use bounded
backoff and Retry-After. Permanent failures are not retried.

Responses are cached in `.cache/monster-locations/`. `--offline` regenerates from
those revisions; `--refresh` fetches new revisions. Confirmed missing wiki pages
are coverage gaps. A transport/API failure prevents replacing the dataset; a
diagnostic report is still written. Cache and build files are ignored by Git.

Outputs:

- `src/main/resources/monster_locations.json`: compact runtime dataset, schema 1.
- `docs/data/monster_locations_report.json`: coverage, revision manifest, missing
  pages, duplicates, uncertain classifications and unresolved drop joins.
- `tools/data/monster_location_overrides.json`: separately curated exact
  classifications and access notes; never overwritten by the generator.

## Schema and identity

Each location retains requested monster page, canonical source page, revision ID,
LocLine NPC name, location, combat levels/text, nullable membership, League regions,
Wilderness state/evidence, availability, spawn count/basis, access notes,
requirements, version/dropversion and wiki/map-section links. Row IDs preserve
coordinate distinctions. Same-name rows with different levels, floors or
coordinates are retained and audited.

Unambiguous integer lists become `levels`; ranges and conditional levels remain
in `levelsText`. Map pins yield **approximate** spawn counts; polygons do not.
Floor, fairy-ring and skill templates are rendered. Footnotes/named references
carry access notes. Explicit unreachable/inaccessible statements prevent a row
qualifying for item filters but leave it visible in details.

The Java service uses **normalized exact monster-page names**. Case, whitespace,
underscores and curly apostrophes are normalized; parentheses remain intact.
Missing variants never borrow a parent's locations. `npcName` is provenance,
not an alias index: many variant pages use a generic name, and some wiki rows
contain apparent label mistakes. No live wiki requests occur during searches.

## Binding to existing drops

The existing drop JSON and search algorithm are unchanged. `dropBindings` is an
additive association keyed by exact existing monster, item, quantity, rate and
notes. The generator reuses the existing drop parser and joins explicit row/table
`dropversion` to LocLine versions. Common unversioned tables can apply across
a page's levels when the page has no explicitly versioned drop tables.
Unversioned occurrences on mixed-table pages remain unresolved.

The key is a signature, not unique variant identity. All occurrences must resolve;
their location IDs and variants are combined. One unresolved occurrence invalidates
the binding. Quantities, rates, notes and parenthetical monster pages never
cross-match. Conditional notes, alternate rates, rolls and row-specific League
restrictions cannot establish verified availability. Citation-only notes are
distinguished from gameplay conditions. Prose restrictions outside these fields
are not comprehensively parsed, so details retain a caveat and wiki link.

Acceptance examples:

- Fire giant / Rune scimitar / 1/128 has multiple levels and both Wilderness choices.
- Goblin mail / 5/128 includes Lumbridge; 10/128 uses its other table.
- Abyssal demon / Black sword: 4/128 excludes Wilderness Slayer Cave; 4/68 includes it.
- Abyssal whip / 1/512 retains both verified table contexts.

Unresolved bindings show labelled **monster-level context**. “All monster
locations” allows inspecting other tables from a verified view, with an explicit
warning that the selected item/rate may not apply at every location.

## Filters

Wilderness is `YES`, `NO` or `UNKNOWN`. Neither missing Wilderness text nor a
League region implies `NO`. Only reviewed exact rules or an explicit LocLine
parameter classify it. Ambiguous labels such as Edgeville Dungeon use
monster-page-specific rules. KBD/Corp lairs, the Abyss and Ferox Enclave have safe
location exceptions, with journey notes where applicable.

Wilderness-only requires `YES`; Exclude Wilderness requires `NO`; unknown fails
both. An item-result source needs **at least one available qualifying location in
its verified drop binding**. Monsters with both kinds of location can appear in
both filters. Clearing filters restores all sources, including missing data.
Shops and other non-NPC sources are explicitly outside the NPC location filter.

Details also filter membership and **League region**, labelled as the wiki's
grouping rather than exact geography. Unknown membership/region cannot pass a
specific filter. Mixed F2P/members rows retain a note and unknown classification.
Wilderness describes the kill location, not the journey or account access.

## Validation and limits

```powershell
.\gradlew.bat clean test build
py -3 tools/validate_item_sources.py
py -3 tools/validate_monster_locations.py
py -3 -m unittest discover -s tools -p test_monster_locations.py
.\gradlew.bat previewSidebar
```

`previewSidebar` renders Swing components alone in `build/previews`, using
RuneLite's normal 242-pixel outer width and a simplified theme. It does not launch
RuneScape and does not substitute for in-client testing.

All 1,614 worklist titles were checked. Many pages have prose-only locations,
unsupported structures or no LocLine; five titles no longer exist. Missing rows
are not evidence of absence. Exact coverage statistics reside in the resource and
report. Most Wilderness classifications remain unknown pending curation, and
remain visible with filters cleared. No runtime updater, travel-time estimator,
quest/account check, game-mode model or fastest-route recommender is included.
Existing drop/price/shop snapshots and their limitations remain unchanged.

See [manual-testing.md](manual-testing.md) before release work. Passing automated
checks does not establish in-game behavior or Plugin Hub acceptance.
