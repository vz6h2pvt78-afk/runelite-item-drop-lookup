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

- `DropSource`, `ItemPrice`, `ItemSource`
- Plain data objects (POJOs) that carry results between layers.
- **Should not** load files, search, or build UI. They know nothing about
  services or the UI.

## `com.itemdroplookup.service`

- `DropLookupService`, `ItemPriceLookupService`, `ItemSourceLookupService`
- Loads JSON resources and performs all lookup/search/price/source logic.
- May use `model` objects (returns them as results).
- **Should not** create Swing UI components.

## `com.itemdroplookup.ui`

- `ItemDropLookupPanel`
- Builds the sidebar UI and renders search results.
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
