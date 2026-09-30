# CR-005: letter spacing is lost on the position-adjustments paint path

Status: DONE ON BRANCH 2026-09-30, `CR-005-letter-spacing-dp` off `2.11-docx4j.2`; also merged into
`CR-003-script-fallback`, with which it gates. Registry key `fop/CR-005`. Upstream-bound: a defect in
Apache FOP with no docx4j specificity; JIRA draft `docs/upstream/letter-spacing-position-adjustments.txt`.
No capability: docx4j has no workaround to drop, and nothing to gate on.

Enterprise CR-001 §6.6 item 33 (the docx4j session's, 2026-09-30).

## 1. How it was found

CR-003's first gate (CR-003 §11) failed on four `+kern` documents where the space after a comma or
full stop closed so far that `pdftotext` merged the words ("repair, if" to "repair,if"). Nothing
reproduced on the fork's command line because the samples had no letter spacing. The docx4j session
found the line: bold Arimo 11pt with Word character spacing on every run, `letter-spacing="-0.417pt"`
on "repair,", and read the per-glyph steps with `mutool trace`: on the released core every step was
the advance less 0.417; on CR-003 every step was the bare advance, the kern applied, and the gap after
the comma 0.139 pt, because the layout box still held the seven letter spaces. It also found the
same on the released core for P052, a CFF font whose `DFLT` kern applied before CR-003, and for the
Greek document 8371, where the letter spacing of every kerned word was painted as one lump after its
last glyph. So it is an upstream defect that CR-003 exposes on every font it makes kern.

## 2. The mechanism

`PDFPainter.drawText` takes the TJ path (`drawTextWithDX`) when the word has no position adjustments
or DX-only ones, and the per-glyph path (`drawTextWithDP`) otherwise. A GPOS kern is an x-advance
adjustment, so `IFUtil.isDPOnlyDX` is false and the word takes the second path. There each glyph is
placed by its own `Td` and the advance is `xa + pa[2]`, glyph width plus adjustment. The `Tc`
character spacing is set on both paths, but it acts only between glyphs inside one TJ array; with
one glyph per `Tj` it never reaches the next glyph.

The layout is right and consistent with the TJ path: `TextLayoutManager` puts the letter spaces into
the word's Knuth elements and area, and `addMappingAreas` computes the word-space adjustment on the
stated assumption that the renderer adds the letter spacing after every glyph, spaces and the last
glyph included. The `letterAdjust` array that the docx4j session first suspected is not it: at
`TextAreaBuilder.build` time the area's letter-space adjust is still zero, so that array carries only
the legacy kern-table values, and `IFRenderer.renderWord` dropping it for a word with a `dp` loses
nothing for a GPOS font, whose kerning is in the `dp` already.

`Java2DPainter` and `PCLPainter` add `letterSpacing` per glyph in their dp loops; `PSPainter` applies
it through `ATJ`; `AFPPainter` converts dp to dx. Only the PDF painter was missing it. Apache `main`
has the same line.

## 3. The change

One line in `PDFPainter.drawTextWithDP`: `xc += xa + pa[2] + letterSpacing`, for every glyph. Test
`PDFPainterTestCase.testDrawDpTextKeepsLetterSpacing`: two zero-width glyphs, a kern of -100 and a
letter spacing of 500; the second `Td` must be 0.4, not -0.1.

## 4. Measured

Command line, Arimo 11pt, "During repair, if however" with `letter-spacing="-0.417pt"` on "repair,",
no `language` so that Arimo kerns on stock code too, per-glyph steps from `mutool draw -F stext`:

| | r>e | e>p | p>a | a>i | i>r | r>, | comma>space |
|---|---|---|---|---|---|---|---|
| before, kerning on | 3.663 | 6.116 | 6.116 | 6.116 | 2.442 | 3.058 | 3.047 |
| before, kerning off (TJ path) | 3.246 | 5.699 | 5.699 | 5.699 | 2.025 | 3.246 | 5.549 |
| after, kerning on | 3.246 | 5.699 | 5.699 | 5.699 | 2.025 | 2.641 | 5.549 |

After: each step is the advance less 0.417, `r>,` carries the kern (-55/1000 em = 0.605 pt) as well,
and the gap after the comma is the one the TJ path always painted.

## 5. What moves

Every letter-spaced span in a font that positions: glyph positions move, layout boxes do not. Through
docx4j that is every run with Word character spacing (`w:spacing`) in a `+kern` family, and, before
CR-003's flag fix, every such run in a font whose `DFLT` kern applied (P052, Nimbus Sans Narrow, DejaVu
Serif). The docx4j session adds the class to the partition and re-gates CR-003 and CR-005 together
against `cr002b-cand`.
