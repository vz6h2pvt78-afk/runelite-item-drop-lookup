# Non-NPC Source Types

Controlled vocabulary for the `sourceType` field of non-NPC item sources
(`item_sources.json` and generated source files such as `wiki_shop_sources.json`).
Keep `sourceType` to exactly one of these values so the data stays groupable and
the validator (`tools/validate_item_sources.py`) can enforce it.

| sourceType | Meaning | Examples |
|------------|---------|----------|
| `Shop` | A standard in-game store that sells the item for coins (or a stated currency). | General stores, Lowe's Archery Emporium |
| `Reward Shop` | A points/token shop where items are exchanged for a non-coin currency. | Slayer reward shop, LMS shop, Pest Control / Barbarian Assault reward shops |
| `Minigame` | Obtained directly from a minigame (not via a points shop). | Minigame completion rewards |
| `Skilling` | Produced or gathered through a skill. | Fishing, mining, crafting outputs |
| `Clue` | Treasure Trail / clue scroll reward. | Reward casket items |
| `Quest` | Quest reward. | One-off quest item rewards |
| `Spawn` | Spawns on the ground / respawns at a fixed location. | Item spawns |
| `Ground pickup` | Reviewed ordinary ground item; excludes scenery interactions. | Twelve verified rows in `wiki_ground_sources.json` |
| `Gathering` | Direct resource interaction yielding an item; distinct from ingredient-based production and NPC combat. | Iron ore at South-west Varrock mine |
| `Reward Chest` | Boss or raid reward chest. Catalogued **without drop rates** for now. | Barrows chest, raid loot chests |
| `Other` | Anything not yet classified. Use sparingly. | — |

## Field conventions

- **itemName** — the item, matching how it appears in lookups. Required, non-blank.
- **sourceName** — the specific source (shop/minigame/quest name). Required, non-blank.
- **location** — best-effort area; blank if not confidently known.
- **cost** — recorded amount and currency when verified, otherwise currency only.
  Bundled generated rows include numeric prices, but the generator's GE-column
  heuristic can mistake reward currencies for coins. Runtime exact-shop corrections
  cover verified cases; unverified numeric coin claims in Reward Shop cards are
  withheld. Do not infer currency from the presence of a GE price column.
- **notes** — caveats only: stock/restock, members requirement, uncertainty. Blank if none.
  The reviewed Gathering projection also derives separate skill/tool, resource-count,
  and caution lines from its structured manifest; these are not manually edited.

## Current state (informational)

- `wiki_gathering_sources.json` adds exactly one reviewed Mining method and location.
  Its action label and requirement lines are generated from structured evidence in
  `docs/data/gathering_sources.json`. See `docs/gathering.md`. The older `Skilling`
  vocabulary remains reserved; no existing records are reclassified.

- `wiki_ground_sources.json` is a separate twelve-row generated slice. `Ground pickup`
  is its runtime type; the older reserved `Spawn` type is not used by this slice.
  IDs, membership and point counts are checked by `tools/build_ground_sources.py --check`.
  Unknown pickup quantity remains null. Coordinates and revision evidence are in
  `docs/data/ground_sources.json`. See `docs/ground-pickups.md` for scope and limitations.

- `item_sources.json` is **hand-curated**, with four `Reward Shop` records.
- `wiki_shop_sources.json` is **generated** by `tools/build_shop_sources.py`.
  Do not hand-edit it. Lookup excludes Theatre of Blood resupply and zero-default-
  stock shops before pack expansion; the raw evidence is retained.
- `item_packs.json` associates verified fixed contents with actual pack offers.
  Derived `itemName` is the contained item; `packName`, `packItemName`, `packQuantity`
  and `packNoted` preserve purchase identity and quantity. Cost is the whole pack.
  Counts represent purchase options, not distinct shops. Random packs are excluded.

### Rune pouch verification (2026-11-02)

The three direct rewards are 750 Slayer reward points, 75 LMS points, and Mage
Training Arena's four-part cost: 150 telekinetic, 150 graveyard, 1,500 enchantment,
and 200 alchemist pizazz points. All four Arena amounts are required together.

Evidence: [Slayer Rewards revision 15373095](https://oldschool.runescape.wiki/w/Special:PermanentLink/15373095),
[Justine's shop revision 15367074](https://oldschool.runescape.wiki/w/Special:PermanentLink/15367074),
and [Rune pouch revision 15384874](https://oldschool.runescape.wiki/w/Special:PermanentLink/15384874).
Justine's store table lists both the direct pouch and the distinct tradeable note
at 75 points. Do not remove the direct source based only on the item's prose
description of exchanging notes at a bank. Exact pouch searches preserve the
untradeable item identity and must not use the note's GE price.
