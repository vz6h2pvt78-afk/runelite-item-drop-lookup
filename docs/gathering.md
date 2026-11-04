# Reviewed gathering slices

Two bounded activities now share the existing Gathering presentation: the original
Iron ore mine below and Raw shrimps at one reviewed fishing area. This is not complete
gathering coverage. No runtime model, NPC area-filter semantics or schema changed.

One output, method and named area: Iron ore (440), mining iron rocks at South-west
Varrock mine. Requires Mining 15 and a Pickaxe. Three resource points are separately
corroborated by the mine page. This is F2P location availability, not a claim that
every iron-mining location is F2P. The nearby mugger is a caution, never an eligibility
condition. Output quantity, boostability, raw plane and unverified access remain
unknown. No quantity/rate/timer, account evaluation or navigation is supplied.

`tools/build_gathering_sources.py` reproduces both reviewed fixtures offline.
`--check` compares the two runtime records and separate structured manifests, then
validates all unrelated runtime JSON resources against the C3m baseline byte hashes.
Default writes a proposal. `--apply` can append the reviewed fishing slice once,
only when the exact original mining record/manifest remains present. Source or
allowlist changes need explicit review and a new pin. Do not automatically repin
changed evidence. The original mining fixture and manifest remain unchanged.

The fixture includes Iron ore revision 15182636, Iron rocks 15372456 and South-west
Varrock mine 15127886. Logs 15322883 is retained only as a negative test: its Firemaking
template consumes logs, so it cannot generate a Logs acquisition source. Full page
pins cover surrounding prose; template checks also guard identities, requirements,
resource/location association, membership, points and independently stated count.

The manifest separates the stable method identity, occurrence, output, skills, tool
category, nullable method membership, verified location membership, hazard context,
and revision/section evidence. Revision changes do not change the stable identity.
The existing `ItemSource` view receives derived strings only. Newlines in Gathering
notes separate requirements, resource count/membership, and context; other types'
presentation is unchanged. Three rocks are not three guaranteed ores per action.

Gathering appears in All and Non-NPC Sources and its dedicated filter. A one-click
shortcut respects Find source. NPC Drops, Shops, Reward Shops and Ground pickups
exclude gathering. Missing dataset coverage is explicitly distinguished from a
filter hiding an existing record. Source counts are acquisition records, not rocks.

This is an allowlisted adapter, not broad discovery. Do not add Woodcutting, more
Fishing, production, additional mines, recursive ingredients or ranking without review.
Skill output tables may use NPC-backed fishing resources and must never be assumed
to represent combat. Uses/Products describes consuming an item, not obtaining it.

Full original snapshots and research are archived separately in the Phase 4
feasibility deliverable. The fixture and manifest retain sufficient evidence for
offline regeneration. Existing complete generators do not automatically preserve
this separately reviewed slice; review their resource changes before regeneration.

## C5a: Raw shrimps / Small net / Lumbridge Swamp

Saved Wiki pins Raw shrimps 15331265 and Fishing spot (small net, bait) 15288182
support item 317, Fishing level 1, Small fishing net, no bait, and Lumbridge Swamp
near the Fishing tutor. Only the Small net interaction is projected. The adjacent
Bait table and other fish do not generate sources. The Wiki's NPC-backed fishing
spot is an activity, never a combat drop source.

`tools/build_fishing_sources.py` checks the item identity, skill/tool/bait evidence,
Small net section and output, exact location/variant/membership, and independent
location prose. `tools/fixtures/fishing_sources.json` retains the full saved article
text, original revision URLs and snapshot hashes. `docs/data/fishing_sources.json`
keeps the derived facts and provenance separate from the unchanged mining manifest.
Missing, malformed or changed evidence fails validation; there is no generic source
fallback or live discovery. No additional Wiki pages were fetched for C5a.

The five map points are retained as evidence only. They do not mean five simultaneous
fishing spots, five methods, guaranteed yields, or a navigable route. Resource count,
per-attempt quantity and boostability remain unasserted. F2P is scoped to this area;
access coverage stays incomplete. The offline Fishing manifest explicitly records
`baitRequired: false`; it does not enter a new runtime schema.

One new source appears under All, Non-NPC Sources and Gathering. Find source matches
the method and Lumbridge Swamp text. NPC location filters retain their existing NPC
scope. Raw shrimps stays distinct from cooked Shrimps and other raw fish. No rates,
XP, timers, banking/training advice, ranking or cooking chain are supplied.
