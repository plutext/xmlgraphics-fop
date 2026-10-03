# CR-010: a word's letter spaces are in its width on the complex-script path

Status: DONE 2026-10-03 on `2.11-docx4j.4` (ab8fcaa48), unreleased, not yet gated. Capability
`letter-space-width`. Registry key `fop/CR-010`. Upstream-bound: JIRA drafted, not filed
(`docs/upstream/letter-space-width.txt`). Branch `FOP-letter-space-width` in `../fop-upstream-wt` is
stacked on `FOP-3344` (#113) and not pushed; the pull request text is in
`../fop-upstream-prs/FOP-letter-space-width.md`.

Enterprise CR-001 §6.6 item 16, the defect itself, in FOP. docx4j has worked around it since 2026-09-04
(`WordLineLayoutManager.fixLetterSpaces`). CR-009 §3.3 and §8 set this change up.

## 1. The mechanism

`GlyphMapping.doGlyphMapping` takes one of two paths:
- **The plain path.** For a font without substitution or positioning tables,
  `processWordNoMapping` counts a word's letter spaces and adds `letterSpaceIPD × count` to its width.
- **The complex-script path.** For a font with GSUB or GPOS tables (`font.performsSubstitution() ||
  font.performsPositioning()`), `processWordMapping` is used instead. In 2.11 it returned a count of 0
  and no width. Since Apache `main`'s FOP-2722, which the fork took with CR-009, it returns the plain
  path's count but still no width; it is not even passed `letterSpaceIPD`.

The painter spaces every glyph on either path (the DP path since CR-005). `TextLayoutManager` breaks
lines on the mapping's width, so letter-spaced text in any OpenType font was measured short and overran
the line. `TextLayoutManager.addMappingAreas` already assumes the counted spaces are in the width: it
subtracts one `letterSpaceIPD` when a line ends at a `/` or `-`.

## 2. The change

`processWordMapping` takes `letterSpaceIPD` from `doGlyphMapping` and adds
`letterSpaceIPD × calculateLetterSpaces(...)` to the width, as `processWordNoMapping` does. The SVG text
path (`FOPGVTGlyphVector`) passes `MinOptMax.ZERO` and does not change.

`GlyphMappingTestCase.testLetterSpacesInTheWidthOfAWordInAFontWithLayoutTables` lays out "word" in
DejaVuLGCSerif (GSUB and GPOS) with no letter spacing and with 3pt. The widths must differ by 3 × 3pt.
Before the change they differ by 0, and the test fails with exactly that ("expected:<9000> but
was:<0>").

Capability `letter-space-width` (the tenth): a consumer may take `GlyphMapping.letterSpaceCount` as the
letter spaces already in `areaIPD`, on both paths.

## 3. Measured on the command line

The sample: Carlito 12pt, `letter-spacing="3pt"`, a 260pt line (the line ends at x = 280), and a paragraph
of 29 words. Line ends are from `mutool draw -F stext`.

| build | complex-script path | `-nocs` (plain path) |
|---|---|---|
| fork `2.11-docx4j.4`, before (kerning off) | 4 lines, ending 297.1, 297.7, 303.0; words off the page | 5 lines: 266.1, 251.8, 275.3, 255.4 |
| fork `2.11-docx4j.4`, after | 5 lines: 266.1, 251.8, 275.3, 255.4 | identical to before, glyph for glyph |
| Apache `main` | 4 lines, painted unspaced (FOP-3344): 218.9, 228.9, 233.9 | 5 lines, as the fork |
| `main` with FOP-3344 | 4 lines: 302.3, 297.3, 302.7 | |
| `main` with FOP-3344 and this change | 5 lines: 265.6, 251.3, 275.2, 254.9 (the 0.4pt is GPOS kerning `main` applies to a `kerning="false"` font: FOP-3343) | |
| `main` with this change only | 5 lines, painted unspaced: 161 to 188 | |

The last row is why the upstream branch is stacked on FOP-3344. In each "after", the last line differs
from `-nocs` by Carlito's `ft` ligature.

Full build on the fork: 3739 tests, 0 failures, 4 skipped; checkstyle and spotbugs clean. On the upstream
branch against `main`: 3660, likewise clean (before stacking it on FOP-3344).

## 4. What moves

**FOP alone:** every letter-spaced word in a font that takes the complex-script path. It is now
measured with its letter spaces, as it is painted.

**Through docx4j: nothing, by reading.** docx4j's `fixLetterSpaces` adds `wordLength − letterSpaceCount`
letter spaces to the width and assumes the counted ones are already in it:
- On `2.11-docx4j.2` the count was 0 on this path, so it added n.
- On `.4` with this change the count's n−1 are in the width and it adds 1, so the total is again n.
- A word whose count equals its length (a CJK character, a `-` or `/` break) is skipped on `.4` and
  already holds n.

So docx4j 17.3.0 measures letter-spaced runs on `.4` as it did on `.2`. Without this change, on the bare
merge, it would add 1 where n−1 are missing (CR-009 §3.3).

The docx4j session's path-aware `fixLetterSpaces` reads `letter-space-width` and agrees on every renderer.
It is still needed for an Apache release that carries FOP-2722 without this change. The gate: the
`spacing-char` and `kern-title` probes and the corpus's `w:spacing` runs, expected identical to
`2.11-docx4j.2`.

## 5. Not addressed

The count is in characters; the painter spaces glyphs. A word in which a ligature forms is therefore
measured one letter space wider per ligature than it is painted. The plain path forms no ligatures and
does not meet this. Word applies no ligature unless `w14:ligatures` asks, and docx4j expresses that
through `gsub-features`, so it should be rare there. This is noted, not measured on the corpus.
