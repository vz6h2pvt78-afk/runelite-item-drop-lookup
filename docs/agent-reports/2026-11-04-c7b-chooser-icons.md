# C7b — optional native item icons in the matching-items chooser

Base: C5b `50bd59136408d8d21c88ae987e9cd5fbdf12f2d1`.
Branch: `development/2026-11-04-post-c3m`.
Build/tag: `development-2026-11-04-c7b-chooser-icons`.
Local prototype checkpoint, pending Apollo's live acceptance. No source push or Hub change.

## Boundary and behavior

Canonical search identity is decided before artwork is requested. Only the Matching
items chooser gains optional images. The existing vertical list, Atkinson type,
buttons, ordering, selection actions and reports are retained. Every chooser row has
the same 36 x 32 image slot; absent/uncertain artwork stays silently blank and clickable.
No icons were added to overview, source cards, details or no-source state.

RuneLite ItemManager.search reads its existing in-memory price index. The icon layer
filters by case-insensitive exact canonical name, requires one distinct ID, then on
ClientThread verifies the actual ItemComposition name and note/placeholder template
flags. It never canonicalizes IDs, strips qualifiers or chooses a first fuzzy match.
Missing and duplicate IDs, mismatched definitions and bank-note/placeholder forms
are rejected. The legitimate Rune pouch note item is accepted as a normal definition.

Only visible chooser rows request work; painting performs no lookup. RuneLite's
native async image cache owns sprites. Callbacks carry a generation and disposed
guard, check again on Swing EDT, and cannot change an old/new query or a closed panel.
Back rebuilds the current chooser. Shutdown disposes its listeners and stops the
existing source-search timer. No custom downloads, persistent cache, executor or
runtime file writes were introduced. ID never feeds search/source/report logic.

## Verification

- 151 Java tests pass normally and against the official packaged JAR; 120 Python
  tests and all validators/aggregate Gathering reproduction pass.
- Official Java 11 / RuneLite 1.13.5 package: 69 classes, 78 API references,
  zero missing/disallowed APIs, exact resources and Atkinson font/license.
- JAR SHA-256: `dc91327e393e5a69738d75cf384b6c65ec1c02861c58f4eb10ce36be000eecf7`.
- Dataset SHA-256 remains `347d491a8ec3fac29e05facd0c856dd5f906264f103f29040e16a4fea60fa111`.
- C5b comparisons: all 39 full-source-object queries and 60 identity-corpus cases
  identical; 38 Swing flows and 30 detail/Back restorations pass.
- Tests cover exact poison/charge/dose identities, ambiguity/missing/definition
  rejection, 500-row visible-only requests, painting without requests, hidden views,
  late callbacks after query/page/disposal, fixed fallback sizing, nine-item Back
  restoration, focusable buttons and byte-identical Copy reports across icon loads.
- Real login-screen feasibility probe: 24/29 exact identities validated, five safe
  text fallbacks; nine uncut native images loaded. A second probe exercises actual
  production ItemIconService and actual Swing chooser with all nine native sprites.
  Offscreen 242px rendering inspected: correct labels/images, compact aligned list.
  A mixed pouch chooser probe also rendered actual Rune pouch note and Clothes pouch
  blueprint images beside six aligned blank rows, including the distinct rune pouch.
- Text-only chooser rendering and bounds checked for uncut (9), rune (47), dragon
  (55) and pouch (8). Warm construction + repeated-layout medians C5b -> C7b in ms:
  14 -> 12, 19 -> 17, 19 -> 23, 2 -> 3. These are bounded offscreen checks, not a
  native gameplay performance claim. Native async artwork does not block Swing.

Computer-use capture of the isolated probe window remained blank after one refresh;
this is recorded as a capture limitation. API/sprite/offscreen evidence does not
constitute human visual, gameplay, keyboard or actual clipboard acceptance. No game
input or player data was used; probe processes are closed by their verified identity.

## Recovery and remaining decisions

Capture source ZIP, full bundle, independent clone/fsck and generator/identity
reproduction in `C7b - Chooser icons`. C5b remains the fully verified non-icon
fallback. Frozen C3m `26c9f8a`, human-tested C3g `d6d105a` and accepted Hub `8a38a025`
are unchanged. Optional partial icon coverage is intentional. Native cache readiness
or an unavailable image can leave a blank slot; it does not imply missing sources.

Apollo should check uncut labels/icons, mixed Rune pouch / Rune pouch note fallback,
poison/charge identities, rapid query changes, scrolling, selection and Back, then
confirm visual comfort in his own client. The underlying acquisition gaps, Ent
threshold contradictions, Raw anchovies hold and legacy bundle projection remain.
GPT reviewed the bounded implementation and approved checkpointing after the clean
comparisons; next scope is C8 private/manual feedback readiness only.
