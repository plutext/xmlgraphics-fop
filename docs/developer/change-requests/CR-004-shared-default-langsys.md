# CR-004: a default language system shared with a named one is dropped by the reader

Status: DONE ON BRANCH 2026-09-30, `CR-004-shared-default-langsys` off `2.11-docx4j.2`; not gated, not
merged. Registry key `fop/CR-004`. Upstream-bound: a defect in Apache FOP with no docx4j specificity;
JIRA draft `docs/upstream/shared-default-langsys.txt`. No capability proposed: docx4j has no
workaround to drop (item 32 records "Workaround: none"); Jason decides whether it wants one.

Enterprise CR-001 §6.6 item 32, with the mechanism corrected: not PairPos format 2.

## 1. The defect

`OTFAdvancedTypographicTableReader.readScriptTable` compares each named LangSys record's offset
with the default LangSys offset, and when they are equal drops the default and aliases the script's
default-language tag to that language. `constructLookups` then registers the features under
`(script, language)` only; nothing exists under `(script, dflt)`. FontForge shares one table between
the default and every language whose features equal the default's, and 252 of the 1356 OpenType
fonts installed here have the shape: every DejaVu face, Cousine, Inconsolata, Adwaita Sans.

Whether anything is lost depends on where the fallback lands. DejaVu Sans's GPOS `latn` default
(shared with `ROM` and the eight Sami systems) lists kern lookups 14 and 15; `DFLT/dflt` lists 15
alone, a 20-glyph subtable with no Latin letter. So DejaVu Sans has no Latin kerning under any default
language, with `kerning="true"` and whatever the fallback order. DejaVu Serif shares the same way but
its `DFLT/dflt` lists the same lookup as `latn`, so it kerns. `DejaVuLGCSerif` in FOP's own test tree
shares `latn` with `AZE` and has no `DFLT` script in GPOS at all.

## 2. Measured

Command line, 14pt "AVATAR To Ye", `kerning="true"`, line width from `mutool stext` (branch tip
33dc82b45 of CR-003, whose fallback reaches `(latn, dflt)` first):

| font | `en` | none | `ro` | `se` | kerning off |
|---|---|---|---|---|---|
| DejaVu Sans | 99.68 | 99.68 | 91.55 | 91.55 | 99.68 |
| DejaVu Serif | 99.01 | 99.01 | 99.01 | 99.01 | 104.43 |

DejaVu Sans kerns only under a language that names the shared table; DejaVu Serif kerns under all,
and the flag turns it off. Font level, same fonts through `FontLoader`: DejaVu Sans
`matchLookupSpecs("latn", "dflt", "kern")` empty, `("latn", "ROM", "kern")` not;
`performPositioning("AV", "latn", "ISM")` -63/1000 em, so the format 2 subtables work once reached.

This corrects CR-003 §10 ("why format 2 yields nothing is not traced") and item 32's claim for DejaVu
Serif, which kerns on the CR-003 tip (99.01 on, 104.43 off). Item 32's "under any setting" for DejaVu
Serif was measured before the kerning flag was honoured, or on a path this session has not
reproduced; the retraction is sent to the docx4j session, which holds the file.

## 3. The change

The three aliasing lines are removed; the default LangSys table is read and registered under `dflt`
whether or not a named record shares it. A named language sharing the table is still read under its
own tag. `SharedDefaultLanguageSystemTestCase` on `DejaVuLGCSerif`: `(latn, dflt, kern)` must match,
`hasFeature` must be true, and "AV" must kern; the first assertion fails on the previous code.

## 4. What moves, and why this is not in CR-003's gate

Alone, on stock fallback, the fix reaches only runs with no language (docx4j writes one on every
block) and runs whose language the font names. With CR-003, every `+kern` run in DejaVu Sans, DejaVu
Sans Mono and Cousine (monospace, no pairs) starts kerning under `language="en"`; plain runs stay
unkerned by the flag. That is a new mover class ("kern-gained, shared-default fonts") and wants its
own gate after CR-003's, so the branch is not merged into `CR-003-script-fallback`. Order is Jason's.

## 5. Reach beyond kerning

The same aliasing affects GSUB where a font shares its default there: DejaVu Sans Bold and
Condensed share `arab` with `KUR` in both tables. On this machine GSUB sharing is rarer than GPOS
sharing (kerning is seldom language-specific; ligatures often are); not measured further.

## 6. Gate 2026-10-01: PASS, merged

Run by the docx4j session on 322a12e20 (the fix on the merged tip, installed by Jason 07:20),
`cr004-cand` against `cr007-cand`, 597 documents, `mutool trace`. Still set 586 glyph-identical; the
five other stills are three documents losing their bidi marks (CR-007's narrowing, the baseline being
the first-cut jar) and two TIME fields crossing the hour. Movers, all predicted by content:

- **12_en-US_num_tbl_11334, Arabic in DejaVu Sans Regular.** On the tip every lam was glyph 4 and every
  alef glyph 11 whatever its position, drawn isolated and unjoined; with the fix lam is 4, 21 or 38 and
  alef 11 or 27 by position, joined, the shapes Word's Arial has. 83 glyphs differ, 30 moved, the two
  Arabic lines re-break because the joined forms are narrower. Toward Word; scoreboard unchanged. The
  mechanism is §1's for `arab`: DejaVu Sans shares its `arab` default with `KUR` in both tables, and its
  `DFLT` script lists no contextual form, no required ligature and no mark lookup, so Arabic in that face
  had never been shaped on the tip. A probe (`fonts-hebrew-no-cs`) holding Arabic in the same face moved
  the same way; its Hebrew did not, since `hebr` is not shared.
- **Kerning.** 11782 moved its 11 `+kern` glyphs; 5936's six Greek characters had no pair and stayed,
  recorded.
- **14_en-US_tbl_394, the one the partition missed.** One justified Tinos line with a wider word-space
  adjustment (-3.7778 to -8.6667 per gap at 9pt). Not Tinos, which shares nothing and has no `DFLT`
  script: the line holds "क्या हाल है", which docx4j's glyph fallback sets in Noto Sans Devanagari
  because the document asks for Mangal. That font (as installed here, md5 f4ae6809bd8c) shares its
  `deva` and `dev2` defaults with Marathi in GPOS, and its `DFLT` script lists `dist` lookups 3, 4 and 5
  where the script default lists 2, 3, 4 and 5. On the tip the word was one TJ string at bare advances;
  with the fix it takes the per-glyph path with a `dist` adjustment of -0.666 pt between ka and ya, the
  word narrows, and the justified line spreads the difference over its spaces. Same glyph ids, so
  positioning only; toward Word, which shapes Devanagari fully. Droid Sans Devanagari, also installed,
  shares the same way and has no `DFLT` script at all, so on the tip it got no Devanagari shaping.

Scoreboards: none worse, three better (the bidi-mark documents, CR-007's). Merged at the commit that
follows this section.

**For the partition method** (the docx4j session's, recorded here because the miss was a reader
finding): the fonts to scan for a shared default are not only the declared faces but every face the
FO's `font-family` names, glyph-fallback faces included (Noto Sans Devanagari, Noto Sans, Droid Sans,
P052 for Greek). What decides movement is the difference between the script's default and the `DFLT`
script, feature by feature, not the sharing alone.
