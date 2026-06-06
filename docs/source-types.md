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
- **cost** — the **currency** when known (e.g. `Coins`, `Points`). For generated shop
  rows the exact GP amount is intentionally omitted because shop prices are derived
  from each item's base value × a multiplier, which is not present on the shop page.
- **notes** — caveats only: stock/restock, members requirement, uncertainty. Blank if none.

## Current state (informational)

- `item_sources.json` is **hand-curated**. As of this spike it contains a legacy
  `Reward` value (3 rows) that predates this taxonomy. It maps to **`Reward Shop`**.
  Migrating those rows is a separate, approval-gated edit — the validator flags them
  until then.
- `wiki_shop_sources.json` is **generated** by `tools/build_shop_sources.py` and uses
  `Shop` exclusively. Do not hand-edit it.
