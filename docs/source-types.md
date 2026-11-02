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

## Current state (informational)

- `item_sources.json` is **hand-curated**, with three `Reward Shop` records.
- `wiki_shop_sources.json` is **generated** by `tools/build_shop_sources.py`.
  Do not hand-edit it. Lookup excludes Theatre of Blood resupply and zero-default-
  stock shops before pack expansion; the raw evidence is retained.
- `item_packs.json` associates verified fixed contents with actual pack offers.
  Derived `itemName` is the contained item; `packName`, `packItemName`, `packQuantity`
  and `packNoted` preserve purchase identity and quantity. Cost is the whole pack.
  Counts represent purchase options, not distinct shops. Random packs are excluded.
