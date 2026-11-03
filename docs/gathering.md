# Reviewed gathering slice

One output, method and named area: Iron ore (440), mining iron rocks at South-west
Varrock mine. Requires Mining 15 and a Pickaxe. Three resource points are separately
corroborated by the mine page. This is F2P location availability, not a claim that
every iron-mining location is F2P. The nearby mugger is a caution, never an eligibility
condition. Output quantity, boostability, raw plane and unverified access remain
unknown. No quantity/rate/timer, account evaluation or navigation is supplied.

`tools/build_gathering_sources.py` reproduces a reviewed fixture offline. `--check`
compares the one runtime record and complete structured manifest, then validates
that all nine earlier JSON resources remain unchanged. Default writes a proposal;
`--apply` only creates an absent slice. Source or allowlist changes need explicit
review and a new pin. Do not automatically repin changed evidence.

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

This is an allowlisted adapter, not broad discovery. Do not add Woodcutting, Fishing,
production, additional mines, recursive ingredients or ranking without review.
Skill output tables may use NPC-backed fishing resources and must never be assumed
to represent combat. Uses/Products describes consuming an item, not obtaining it.

Full original snapshots and research are archived separately in the Phase 4
feasibility deliverable. The fixture and manifest retain sufficient evidence for
offline regeneration. Existing complete generators do not automatically preserve
this separately reviewed slice; review their resource changes before regeneration.
