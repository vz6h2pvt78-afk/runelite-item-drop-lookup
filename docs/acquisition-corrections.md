# Reviewed acquisition corrections

## Lyre and Temple Supplies — November 3 evening

Lyre facts apply only to the exact Lyre item paired with Borrokar, Freidir, Lanzig
or Lensa. Speak to Olaf to start his trial first. This is not a quest-completion
requirement, nor a condition on their other items. The five original drop records,
including unreviewed Jennella, keep their raw identities, rates and location
bindings. Evidence: [The Fremennik Trials revision 15374585](https://oldschool.runescape.wiki/w/The_Fremennik_Trials?oldid=15374585),
Olaf the Bard section, cross-checked with the named sources in
[Lyre revision 15361204](https://oldschool.runescape.wiki/w/Lyre?oldid=15361204).

Death talisman at Temple Supplies costs 80 Abyssal pearls and appears only after
Mourning's End Part II completion. The curated offer supersedes only the exact
generated item/shop pair and is classified as a Reward Shop. Keep the conditional
source: its inability to help with first-time quest completion does not make the
offer invalid. Do not propagate this item-specific unlock to other talismans.
Evidence: [Temple Supplies revision 15318800](https://oldschool.runescape.wiki/w/Temple_Supplies?oldid=15318800),
shop description, currency header and Death talisman row/footnote. The generated
shop file is preserved, and the misleading stock-depletion note is not reused.

Service regressions check visible exact conditions, retained sources, unchanged
raw Lyre records, one corrected shop offer and non-propagation to unrelated items.
The requirements validator checks real item/source pairs and pinned provenance.
These summaries adapt OSRS Wiki contributors' material under CC BY-NC-SA 3.0;
see THIRD_PARTY_NOTICES.txt. Full revision snapshots are preserved in the local
November 3 evidence archive.

## Bounded Temple Supplies patch

The separately reviewed offline patch covers all eighteen existing generated
Temple Supplies records. It changes only cost and scoped notes, asserting the
previous values and preserving record order, identity, type and count. The same
pinned page explicitly establishes Abyssal pearls for its one store table; the
Law, Death and Blood talisman unlocks remain attached to their individual rows.
The existing curated Death talisman offer still supersedes its generated record.

Run `python tools/patch_temple_supplies.py --check` to verify the reviewed state;
`--apply` permits only the pinned before/after values. Evidence, amount mapping
and original values are in `tools/fixtures/temple_supplies_patch.json`. This is
not a full shop regeneration. Abyssal needle and Abyssal lantern are absent from
the existing dataset and are not silently added. Generated source categories
remain unchanged in this cost/condition-only patch.

## Named forms and the Ent direct-reward boundary

The Raw bear meat card for Alexis identifies her as a Canifis citizen in human
form and states that Wolfbane prevents transformation. The distinct Werewolf
record describes the werewolf form and notes that human-form loot differs. Neither
identity, quantity, probability nor location signature is changed. This is not a
family-wide equipment rule; other named citizens remain unannotated until reviewed.
Evidence: [Alexis revision 15317213](https://oldschool.runescape.wiki/w/Alexis?oldid=15317213)
and [Werewolf revision 15319526](https://oldschool.runescape.wiki/w/Werewolf?oldid=15319526),
introductory form explanations and their separate food-table rows.

The exact Ent / Slayer's enchantment pair retains direct-kill semantics and its
Wilderness/Krystilia task condition. It must not inherit the axe, Woodcutting or
harvest rules for logs, nor spread its task condition to those logs. Evidence:
[Ent revision 15388968](https://oldschool.runescape.wiki/w/Ent?oldid=15388968),
introductory drop-versus-harvest distinction and Wilderness Slayer tertiary section.

## Harvest and ordinary-loot event slices

`acquisition_events.json` separates harvest facts from ordinary loot tables and
their lookup coverage. Runtime validates supported fields, typed requirement
scopes, identities, quantities and evidence URLs. Display sections are built from
these records; the plugin does not parse note prose to decide how a method works.

The six known Ent/log pairs are suppressed from ordinary NPC presentation and
project once into Gathering. This does not rewrite raw drops. Magic logs has a
reviewed harvest: kill an Ent, chop with a usable axe, and have 75 base Woodcutting
for that output. The two explicit Wilderness locations have level 101 Ents and
two noted logs per successful chop; the Guild cavern has level 86 Ents and one.
The selected log is not guaranteed, and neither the encounter level nor the yield
is a player combat requirement or quantity per kill. Yew logs uses the same three
reviewed locations, with the Ent-specific threshold of 61 base Woodcutting (not
the ordinary tree's level 60).

Ordinary Logs lists only the two Wilderness associations. Its output condition
is a scoped statement: below 45 base Woodcutting, uncertain at exactly 45, and
unavailable above 45. The article excludes Logs at 45, but
[Ent calculator revision 14807078](https://oldschool.runescape.wiki/w/Module:Ent_calculator?oldid=14807078)
includes Plain in its 31..45 bin. The conflict is recorded in
`tools/fixtures/ent_boundary_conflict.json` and checked independently; no hard
exclusive maximum or invented lower bound replaces this uncertainty. The
[calculator template revision 14807076](https://oldschool.runescape.wiki/w/Template:Ent_Calculator?oldid=14807076)
links the calculation. The matrix also depends on axe offsets and has boundary
behaviour unsuitable for a quick translation into gameplay probabilities.

Oak, Willow and Maple details remain unavailable, as does ordinary Logs in the
Guild. Their normal-tree unlock levels are not inferred for Ent outputs. This
bounded pass does not implement a probability calculator or resolve Wiki conflicts.

There are no legacy Ent drop bindings. The new association chain is Ent revision
15388968 -> reviewed harvest -> explicit location IDs -> exact existing location
records. Names, monster identities, combat levels and availability are checked.
No fallback to all monster locations or fabricated legacy binding is permitted.
The five-field raw signature independently identifies the presentation being
replaced. A small six-pair guard keeps malformed/missing event data from restoring
misleading kill quantities; it produces an honest harvesting-details-unavailable
card instead. `LocationMatch.isVerified` keeps its original drop-only meaning.

Dragon impling's 19 ordinary outcomes are preserved, including two distinct
Dragonstone bolt-tip quantity rows and the Monkey Madness II javelin-tip outcome
that rerolls when unavailable. `lootCoverage` exposes sixteen distinct reviewed
outputs: Mystic robe bottom, Amulet of glory, Dragonstone amulet, Dragon arrow,
Dragonstone bolts, Dragon longsword, Dragon dagger(p++), Dragon dart, Dragonstone,
Dragon dart tip, Dragon arrowtips, Babydragon bones, Dragon bones, Magic seed,
Snapdragon seed and Summer pie. The two bolt-tip outcomes and gated javelin tips
remain in the full table but are not exposed as lookup results. Quantity ranges
and noting belong to each outcome; seeds are unnoted. Coverage is never used as
a probability denominator.
The displayed 1/19 is labelled Wiki-listed, with the quest-dependent reroll caveat
adjacent. No effective 1/18 or 1/16 chance is invented. Empty-jar returns and extra
clue rewards are outside this ordinary-loot feature.

Opening an existing Dragon impling jar has no capture requirement. Capture uses
83 Hunter with a butterfly/magic butterfly net, or 93 Hunter bare-handed. A catch
with an empty jar stores the impling even when bare-handed; outside Puro-Puro a
catch without a jar gives immediate loot. Puro-Puro requires a jar. These routes
lead to one loot event, not separate chances. Exact jar searches stay about the
jar; charged glory searches do not inherit the uncharged outcome.

Evidence: [Dragon impling 15371528](https://oldschool.runescape.wiki/w/Dragon_impling?oldid=15371528),
[Dragon impling jar 15371529](https://oldschool.runescape.wiki/w/Dragon_impling_jar?oldid=15371529),
[Impling 15360701](https://oldschool.runescape.wiki/w/Impling?oldid=15360701),
and the Ent revision above. The offline evidence validator independently compares
the full loot table to pinned Wiki rows. Java tests exercise route separation,
counts, method filters, identity, unavailable data and changed location records.
One malformed event collection cannot invalidate the other collection or ordinary
shops/NPCs. The standard/larger preview checks real card bounds at sidebar width.

## Night beast roll context and missing Zulrah Dragonstone

Night beast now has a MONSTER / DROP_CONTEXT / CONTEXT fact, shown once per source
card and in details. This is explanatory context, not an access gate. It describes
three additional item rolls, at most one superior unique, regular-table rates and
quantities per roll, and separate guaranteed/tertiary drops. It does not assert
three independent regular rolls, multiply quantities, or alter any raw Night beast
record/binding. It is not inherited by Dark beast or other superior monsters.
Evidence: [Night beast 15373912](https://oldschool.runescape.wiki/w/Night_beast?oldid=15373912),
drop-table introduction; full attributed text is in the offline fixture.

The bounded Zulrah repair appends only Dragonstone / Zulrah / quantity 1 / rate
5/7968 with explicit per-roll/two-roll notes. `patch_zulrah_dragonstone.py` defaults
to read-only, validates the pinned call/template/module/rendered-row chain, rejects
an existing conflicting exact source, and leaves every unrelated record in order.
The derivation is 10/249 × 2/128 = 5/7968 = 1/1593.6 per roll. Both source and detail
rate lines retain “per roll; 2 rolls per kill”; no combined per-kill odds are shown.
The formatter binds that wording to the exact reviewed five-field signature.

Evidence: [Zulrah 15385964](https://oldschool.runescape.wiki/w/Zulrah?oldid=15385964),
[RareDropTable 15104778](https://oldschool.runescape.wiki/w/Template:RareDropTable?oldid=15104778),
and [RareDropLines 15343549](https://oldschool.runescape.wiki/w/Module:RareDropLines?oldid=15343549).
The page's rendered row confirms quantity 1 and 2 × 1/1,593.6; this checks extraction
against another representation of the same source, not independent corroboration.

The current dataset has no Zulrah locations or bindings. None are fabricated.
The source appears with All NPC areas selected, and details retain the honest
unknown-location state and Zulrah Wiki button. Specific location filters retain
their established exclusion of unverified sources. Tests distinguish Dragonstone
from Uncut dragonstone, bolt tips and amulets and preserve exact search counts.
