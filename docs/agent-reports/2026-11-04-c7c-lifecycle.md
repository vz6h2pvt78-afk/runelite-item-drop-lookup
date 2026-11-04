# C7c — separate lifecycle cleanup after frozen C7b

Base: C7b `4467866efef07324114c3b91774080588c2e9576`.
Branch: `development/2026-11-04-post-c7b`.
Build/tag: `development-2026-11-04-c7c-lifecycle`.
Experimental successor; C7b remains Apollo's tonight-test candidate.

## Change and scope

Shutdown previously disposed the panel and removed its toolbar entry, but the
disabled plugin still held NavigationButton, which held the last disposed panel.
This was bounded retention until re-enable, not demonstrated unbounded leakage or
a user-visible failure. GPT approved clearing this avoidable reference separately.

Shutdown now captures the button locally, clears the field, and passes the captured
button through the existing ClientToolbar removal path. Panel disposal and nulling
are retained. No cache clearing, weak references, forced GC, new lifecycle framework,
source/identity/UI redesign or data changes were introduced.

## Verification and limits

- 155 Java tests pass normally and against the official package; 120 Python tests
  and all relevant validators/reproduction checks pass.
- Four additional tests cover 12 mixed query/detail/Back/pouch/zero-source/recreation
  sequences, late callbacks from a non-EDT thread, 20 rounds of 500-row visible-only
  chooser lifecycle/listener cleanup, pending debounce cancellation, and one short
  failure response per locked-clipboard click in chooser/detail/no-source states.
- Installed PluginManager bytecode requires EDT lifecycle. ItemIconService confines
  client calls to ClientThread, and ChooserIcons marshals final Swing updates to EDT.
- Source and compiled shutdown control-flow audit verifies panel/navButton null
  writes and removal of the captured button. No reflective runtime inspection or
  GC collection assertion was used.
- Actual native PluginManager at LOGIN_SCREEN completed five stop/start cycles with
  inactive/active transitions on EDT. No plugin lifecycle exception occurred. No
  game input, player data or persistent enabled-setting edits were performed.
- Native toolbar discovery was unobservable in two bounded attempts (immediate,
  then 90-second readiness/normalized-tooltip check). Both ran zero cycles. GPT
  authorized only the final five-cycle API check; lifecycle diagnostics then ended.
  Toolbar removal, visual restoration and human usability remain human-unverified.
- Official Java 11 / RuneLite 1.13.5 package: 69 classes, 78 API references, no
  missing/disallowed APIs, exact resources/font/license. C7b-to-C7c JAR comparison
  changes only ItemDropLookupPlugin.class and lookup_build.properties. All other
  classes/resources and the dataset digest are identical; no probe/test/local-path
  artifacts appear in the production JAR.
- C7c JAR SHA-256: `016ef6c6d40b2e55e3924ccc761567187ea74c2a06e791bcfd01b8d0b6529f86`.
- Dataset SHA-256: `347d491a8ec3fac29e05facd0c856dd5f906264f103f29040e16a4fea60fa111`.

## Independent C7b hardening

A fresh clone from the saved C7b bundle passed the full 151-Java/120-Python package,
API, resource and validator gates. It reproduced the exact C7b JAR bytes/SHA, not
just semantically equivalent ZIP entries. This work did not change C7b.

The isolated Hub dry run points only to C7b with one commit-line diff and unchanged
repository URL. Official manifest parsing passed. The full wrapper's corrected
production verification metadata is traceable to the clean RuneLite 1.13.5 Hub
worktree (blob f018027251378851fbc91f6c4f400b0e26e28c2f); no trust entries were
generated or verification disabled. Its compile/package/dependency-verification
steps passed, but the later Git-timestamp check failed because the dry-run fixture
was extracted from a source ZIP without .git. Full-wrapper validation is therefore
not claimed. Attempts and logs are preserved; no silent further retries are made.
Remote SHA reachability and actual Hub CI remain pending separately authorized
publication after Apollo's review.

## Recovery and candidate policy

Save a new local source ZIP/bundle/independent recovery checkout and reproduction
checks for C7c. Preserve all previous source/artifact identities. C7b remains the
preferred human-test build, C5b the non-icon fallback, C3m/C3g frozen, and the accepted
Hub release unchanged. The existing Start here pointer must continue to target C7b.
No push, merge, Hub change, public pilot or reset was used. Local-data inventory
continues separately; no new Wiki research was used for this cleanup.
