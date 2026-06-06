# Project Structure

A short guide to how the Java code is organized and what belongs where.
The goal is simple, one-directional layering: **plugin → ui → service → model**.

## `com.itemdroplookup` (root)

- `ItemDropLookupPlugin` — the plugin entry point. Handles RuneLite wiring:
  `@PluginDescriptor`, `startUp()`/`shutDown()`, the sidebar navigation button,
  and constructing the panel.
- `ItemDropLookupConfig` — the config interface (`@ConfigGroup("itemdroplookup")`)
  and config wiring via the `@Provides` method.

This layer wires everything together; it does not contain lookup logic or UI
rendering itself.

## `com.itemdroplookup.model`

- `DropSource`, `DropSourceGroup`, `ItemPrice`, `ItemSource`
- Plain data objects (POJOs) that carry results between layers.
- **Should not** load files, search, or build UI. They know nothing about
  services or the UI.
- `DropSourceGroup` is a **display grouping model**: it holds a `parentName` and
  a list of `members`. The raw `DropSource` members are preserved as-is — nothing
  is merged or rewritten.

## `com.itemdroplookup.service`

- `DropLookupService`, `MonsterFamilyGrouper`, `ItemPriceLookupService`,
  `ItemSourceLookupService`
- Loads JSON resources and performs all lookup/search/price/source logic.
- May use `model` objects (returns them as results).
- **Should not** create Swing UI components.
- `MonsterFamilyGrouper` is a **pure helper**: it groups parenthetical monster
  variants (e.g. `Skeleton (Barrows)` under `Skeleton`) for display only. It does
  **not** merge rates, **does not** average rates, and **does not** modify raw
  `DropSource` records.

## `com.itemdroplookup.ui`

- `ItemDropLookupPanel`
- Builds the sidebar UI and renders search results.
- Handles grouped monster display and shows parsed Variant/context lines derived
  from monster names (e.g. `Skeleton (Tarn's Lair)` → `Variant: Tarn's Lair`).
- Calls services and displays the `model` data they return.
- **Should avoid** owning data-loading or matching rules — that logic lives in
  `service`.

## Dependency direction

```
plugin root  ──wires──▶  ui  ──calls──▶  service  ──returns──▶  model
```

- The plugin root wires things together.
- `ui` calls `service`.
- `service` returns `model` objects.
- `model` knows nothing about `service` or `ui`.

Keep arrows pointing one way. If a `model` class ever needs a service, or a
service starts building Swing components, the layering has been crossed.

## Data warning

- **Do not hand-edit generated JSON.** The drop data is produced by the tools in
  `tools/` (e.g. `build_wiki_drops.py`); regenerate it instead of editing by hand.
- The runtime NPC-drop source is **`wiki_monster_drops.json`**, not `drops.json`.
  `drops.json` is an unused converter output and is not loaded by the plugin.
  When debugging search results, inspect `wiki_monster_drops.json`.
- **Combat level and structured monster location are not available** in the
  current runtime drop data. Variant/context is only inferable from the trailing
  parenthetical inside `monsterName`.
