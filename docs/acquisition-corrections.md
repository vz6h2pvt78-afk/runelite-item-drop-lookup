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
