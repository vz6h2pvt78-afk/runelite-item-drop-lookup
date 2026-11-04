# C5b — ordinary-tree Logs, separate from Ent harvesting

Base: C5a `00c2632d49400a9b442af8f652b4e5c159a1850b`.
Branch: `development/2026-11-04-post-c3m`.
Build/tag: `development-2026-11-04-c5b-woodcutting`.
Local review checkpoint; not human acceptance, source push or a Hub change.

## Scope and evidence

One new ordinary Gathering record: Logs (1511), Chop ordinary trees, Draynor Village
near the bank, Woodcutting 1 and Axe. Saved Logs 15322883 and Tree 15226767 establish
the output identity, skill/tool, action/output and named-area prose. No new pages
were fetched. Coordinates, map/plane, active tree count, location membership,
boostability and output quantity remain null/unasserted. No rates, XP, timers,
double-yield, banking/training advice or account eligibility is supplied.

The Raw anchovies sibling was held after consultation: saved Small-net output and
chart `req1=15` suggest Fishing 15, but the field semantics are not explicitly
defined in currently pinned evidence. No threshold was inferred or fetch forced.

Logs has two separate Gathering acquisition methods. Ordinary trees do not inherit
Ent combat, Wilderness warnings, level-45 uncertainty or conditional noted yields.
Ents do not inherit Draynor, Woodcutting 1 or the ordinary-tree requirement string.
Other log families, NPC Drops, C5a Fishing and exact-catalog identity stay unchanged.

Runtime changes are one additional Gathering JSON row, build metadata, and source
attribution text. No runtime Java change since C5a. Earlier Mining/Fishing records,
fixtures/manifests and ten other runtime JSON resources match their baseline.
The bounded offline adapter fails closed on changed/missing evidence and does not
discover other trees, locations or products. The Logs Firemaking template remains
a negative acquisition-direction case; Woodcutting is sourced from Tree instead.

## Verification

- 143 Java tests pass normally and against the official packaged JAR.
- 120 Python tests pass, including eight new Woodcutting evidence/identity tests.
- All relevant validators and aggregate Gathering reproduction pass.
- Official Java 11 / RuneLite 1.13.5 package: 64 classes, 62 API references,
  zero missing/disallowed APIs, exact runtime resources and Atkinson font/license.
- Final JAR SHA-256: `5645253d8dbd79f84e2a4ff576b05db62eaf13de39d9adc66a75e41df389906f`.
- Dataset SHA-256: `347d491a8ec3fac29e05facd0c856dd5f906264f103f29040e16a4fea60fa111`;
  runtime schemas remain 1.
- 39-query full-object C5a comparison: only Logs gains one record. Original Ent
  event objects and all other choices, drops, sources, costs and access facts match.
- All 60 existing identity-corpus cases match C5a exactly. The C5a 4,569-query
  characterization is retained; no shared identity code changed in C5b.
- Actual Swing audit: 38 full flows and 30 detail/Back restorations pass.
- Ordinary-tree and Ent cards rendered together at 242px and visually inspected;
  separate titles, requirements and danger context remain clear. Draynor finder
  isolates the ordinary method. No game input or real clipboard test was automated.

The first feature build passed before adding the complete Gathering source-credit
block; the final `c5b2` evidence directory supersedes that preliminary artifact and
reruns the full gates with the bundled notices included.

## Recovery and review

Capture a new source ZIP, full bundle, independent recovery clone/fsck and generator/
identity reproduction in `C5b - Ordinary tree Logs`. C5a remains recoverable by its
tag; frozen C3m `26c9f8a`, human-tested C3g `d6d105a` and accepted Hub `8a38a025` remain
unchanged. Later work must not be presented as accepted by Apollo.

Human test focus: Logs → Gathering (two methods), Draynor finder and clear recovery,
ordinary-tree versus Ent requirements and Wilderness labels, Raw shrimps unchanged,
Shrimps recognized-zero-source Back/report behavior, and the prior C3g→C3m test set.
Live visual comfort, gameplay, keyboard and actual clipboard acceptance remain pending.
