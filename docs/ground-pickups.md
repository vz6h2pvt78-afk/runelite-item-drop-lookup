# Ground pickups — reviewed vertical slice

The first slice contains exactly twelve ordinary ground-pickup records for five
items: Spade 952 (3), Knife 946 (3), Bucket 1925 (3), Snape grass 231 (1), and
Red spiders' eggs 223 (2). The records contain sixteen coordinate points. A point
count is not a quantity-per-pickup claim. All pickup quantities are unknown/null;
no respawn times, routes, rates or account eligibility are supplied.

`tools/build_ground_sources.py` reads the pinned `tools/fixtures/ground_spawns.json`.
Its default mode writes a proposal under `.cache`. `--apply` creates the new
runtime JSON and provenance manifest only if neither exists. `--check` verifies
both complete generated outputs and the canonical JSON hashes of all eight
pre-existing resources. No network fetch or broad page discovery runs implicitly.

The allowlist pins exact page/section/template hashes and canonical item IDs.
Unknown membership, new parameters, coordinate annotations, referenced or inline
pickup restrictions, scenery templates/sections and ambiguous identities fail
closed. The overall fixture hash protects surrounding prose as well as selected
rows. Source revisions and full snapshot hashes remain in the manifest. Row IDs
derive from item/method/location/coordinates/plane/map ID, not revision ID.
Missing plane/map ID remains unknown instead of adopting Wiki rendering defaults.

Wiki `ItemSpawnLine` is not by itself proof of ground acquisition: the Small
fishing net page uses it for eight scenery locations. All nets are excluded
because current prose and historical change entries conflict about Ironman
pickup restrictions. Also excluded: all scenery interactions, Telekinetic Grab
spawns, and the conditional Misthalin Mystery bucket. See the separately archived
Phase 3 feasibility report for the six-page investigation.

The existing ItemSource pipeline appends the separate resource. Shop filtering,
pack expansion and NPC matching retain their prior logic. Display notes derive
from explicit membership and point counts; runtime identity metadata is retained
in JSON even where the current card only needs display fields. No generalized
acquisition hierarchy is introduced for this small slice.

All and Non-NPC Sources include these records. Ground pickups has its own filter
and a one-click shortcut whose count is acquisition records, not map points. The
coverage/access caveat appears once per result view, not on every card. Empty
pickup results describe missing verified data, never absence of all OSRS spawns.
NPC Wilderness controls remain NPC-only. League data is absent from the runtime
resource and UI. No automatic navigation, account tracking or gameplay input.

Validation: full Java/Python suites, existing validators, shared-herb `--check`
and ground-source `--check`. At login, inspect narrow cards, shortcut counts,
bucket point counts, missing net coverage, Shops exclusion and Non-NPC inclusion.
In-game availability and player acceptance still require Apollo's manual test.

Future expansion requires renewed source review, new pins and regression checks.
Do not edit the runtime records by hand, remove unknown-value checks or widen the
allowlist merely to reach a larger count. Existing full shop/drop generators do
not own or overwrite this independent file.
