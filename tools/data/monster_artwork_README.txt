S44 offline monster artwork
==========================

Runtime:
  src/main/resources/artwork/monsters.tsv contains explicit S (exact source) and
  G (reviewed group representative) mappings. No prefix/family fallback is used.
  979 bundled 36x40 PNGs cover 1,008 exact source names and 64 group headers.
  These are decorative representative forms, not mechanics or variant evidence.
  Group children deliberately have no portraits. Selected item artwork is unchanged.

Review inputs:
  monster_artwork_review.json records all 1,011 attempted sources, including holds,
  separate group decisions, own article revisions/infobox fields, contact-sheet
  references, original image SHA-1, downloaded-thumbnail SHA-256, and bundled SHA-256.
  Each source independently names its own infobox file even when assets are shared.
  Goblin retains the accepted S43 pixels with transparent centering.

Validation:
  python tools/build_monster_artwork.py --check
  python -m unittest discover -s tools -p test_monster_artwork.py
  After an explicit artwork review, --write regenerates the runtime manifest.
  This validator has no network or Pillow dependency.

Retrieval and visual review:
  Preparation scripts, cached image metadata, downloads, selected candidates,
  choice overrides, and all sixteen reviewed contact sheets are retained in the
  S44 checkpoint verification/pipeline-scripts and adjacent evidence folders.
  The preparation pipeline uses Pillow only offline; it is not a runtime dependency.
  Do not turn candidate discovery into approval. New/multiple-image choices need
  own-source evidence and visual review, or an explicit held outcome.
  Do not use artwork availability to suppress or change an acquisition source.

Held exact sources:
  Goblin raider: blank own infobox image.
  Shark (Sailing): exact page missing; Shark (ocean) is a different, non-interactive NPC.
  Wild dog (Last Man Standing): own page references a missing image.

Held group representatives:
  Guard: includes human, dwarf, and goblin guards.
  Mogre: land/sea forms differ.
  Wild dog: historical LMS artwork unverified.
  A held group does not suppress independently reviewed standalone portraits.

Separate data follow-up:
  The Wild dog (Last Man Standing) article is marked removed in 2019.
  S44 intentionally makes no acquisition, historical-exclusion, or identity edits.

Game-media attribution is in THIRD_PARTY_NOTICES.md and artwork/NOTICE.txt.
