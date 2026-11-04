# C3h — Morning feedback candidate

Baseline: C3g `d6d105a51cdffd216eebe0add080ae5a2f702d83`, preserved separately.
Build/tag: `development-2026-11-04-c3h-morning-feedback`.
Status: implemented and offline verified; Apollo has not accepted this candidate.

## Changes

- Source cards and NPC details share one rate formatter. Ordinary fractions use the same reciprocal wording; reviewed Zulrah retains two-roll context and unresolved special events retain listed rates/context.
- A last-valid navigation snapshot restores the submitted query, selected canonical item, method, NPC area, source finder, page and scroll. Broad/no-match screens omit selected-item report fields. Fresh invalid searches do not invent history. Matching-items Back has the same drawn arrow as other Back controls.
- NPC detail headings and event-card headings show Source → Item.
- Verified Wilderness NPC location cards use an original drawn skull plus WILDERNESS / PvP danger text. Negative/unknown Wilderness labels are omitted; unknown values still remain unknown in filtering/data.
- Dragon impling cards have a shorter practical summary. The complete nineteen-outcome event, sixteen covered items, three validated routes, capture alternatives, opening-without-Hunter exception, and eligibility/reroll model remain intact. Summary still shows the Wiki-listed rate and Monkey Madness II caveat.
- Ent quantities explicitly apply when the selected log type is rolled. Ordinary Logs show three separate base-level states, with level 45 unresolved. No threshold or location changes.
- Exact, case-insensitive `Amulet of glory (uncharged)` alias resolves to canonical `Amulet of glory`; charged/other suffixes are not stripped. Copy report retains the actual submitted query.
- Copy report still performs one manual clipboard write, no reads, automatic sending, client data or telemetry.

## Evidence re-audit

Existing four Lyre prerequisites remain unchanged. [Fremennik Trials, revision 15374585](https://oldschool.runescape.wiki/w/The_Fremennik_Trials?oldid=15374585) explicitly names Borrokar, Freidir, Lanzig and Lensa and requires starting Olaf's trial.

[Jennella, revision 15351176](https://oldschool.runescape.wiki/w/Jennella?oldid=15351176) attaches the Heroes' Quest footnote to Blamish oil, not Lyre. No Jennella Lyre requirement was added.

[Amulet of glory, revision 15386867](https://oldschool.runescape.wiki/w/Amulet_of_glory?oldid=15386867) labels its base version Uncharged and uses the explicit uncharged output wording. [Dragon impling jar, revision 15371529](https://oldschool.runescape.wiki/w/Dragon_impling_jar?oldid=15371529) retains canonical Amulet of glory as a three-noted-item outcome.

The three morning API snapshots and their revision/hash metadata are archived beside the checkpoint. Unique acquisition evidence grows from 19 to 21 pins; no broad crawl. GPT accepted this bounded re-audit and restricted C3i to existing Ent evidence plus the rendered calculator.

## Verification

123 Java tests in the normal build and against the official packaged JAR; 103 Python tests. Access, acquisition, location, non-NPC and report-identity validators pass. Official Java 11/RuneLite 1.13.5 package: 63 classes, 62 API references, no missing/disallowed calls; every runtime resource matches. No test code/dependencies in plugin package.

Data diff: all JSON except acquisition_events is unchanged from C3g. The only acquisition JSON change is the ordinary Logs condition's display wording, preserving the conflict and structural schema. Full event routes/outcomes, drop/location bindings and all access facts are unchanged.

Actual Swing previews rendered and inspected. Standard impling card is 209×558 pixels; Standard/Larger card bounds pass. Lyre, Dust devil, Gargoyle and Zulrah detail previews are saved. Navigation regression includes an event-queue-settled check of restored scroll and per-source membership filter.

Human acceptance still needed for visual comfort, keyboard navigation, gameplay and the new build's actual clipboard. Apollo's successful C3g manual reports are evidence for C3g only. No Hub change, publication, push or reset performed for this checkpoint.

## Next

Consult GPT on the checkpoint, then inspect the existing Ent calculator graph (C3i). Preserve C3g as fallback. Acquisition hierarchy/location consistency and broader identity hardening remain separate roadmap work.
