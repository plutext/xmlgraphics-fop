# CR-033: a side float the page cannot hold moves to the next page with its anchor, whatever its offset

- **Status:** built 2026-11-03 on branch `CR-033-float-move-any-offset`, cut from `2.12-docx4j.1` at 5ec0c21e4, code
  at d972d78fe; full suite 4092/0 (5 skipped), checkstyle and spotbugs clean under CI's command. Handed to docx4j-12
  the same day as r59 (`~/fop-renderers/r59-CR-033-d972d78fe/`, core sha256 5c060dd6...) for a gate against b321 on
  r58. **Gated PASS 2026-11-03 (docx4j-12), §6, and MERGED to `2.12-docx4j.1` by fast-forward at 2e4f3b2a6;
  unreleased until `2.12-docx4j.1` ships. b322 on r59 is the fork's baseline for later gates.**
- **Serves:** Enterprise CR-001 §6.6 item 8, the first of the three renderer defects that blocked docx4j's step 2 on
  r57 (gate b317, 2026-11-03), its case (a): a side float with no `fox:float-offset`, or 0, anchored too low for the
  page. Ordered by Jason on 2026-11-03 as A1, after B (fop/CR-032) and before the probe for T.
- **Capability:** none new. docx4j already writes such floats; the move needs nothing written.
- **Modes:** FOP's own layout and Word mode alike.

## 1. Word's rule

docx4j-12's probe `float-foot-crossing` (docx4j ac66f5518; Jason's golden, read here with mutool and agreed with
docx4j-12 on 2026-11-03; recorded in Enterprise CR-001 §6.6 item 8 at bf44357). In each case a 240pt float is
anchored 504pt down a full page and would end 46pt past the body's foot.

- A square-wrapped picture at offset 0 (case 4) or 1pt (case 6): **Word moves it whole to the next page's top with
  its anchor**; the anchor goes beside it and the page before holds the filler only.
- A floating table at tblpY 0 or 1pt (cases 1 and 2): Word splits it as it would in the flow, rows 1 to 12 from the
  anchor's top and rows 13 to 15 at the next page's top, the anchor beside the continuation. That is item T, not
  this.
- A top-and-bottom picture (case 5): moved with its anchor; docx4j writes it as a block, which FOP keeps with its
  anchor already.

So CR-023's move to the next page with the anchor is Word's rule for a float that cannot split, offset 0 included.
Until T exists, floating tables keep the move: a table moved whole with its anchor is on the page, where before its
rows past the foot were lost.

## 2. What the fork did, and why

`PageBreakingAlgorithm.handleBox` armed the move only for a float with an offset above 0 (`floatOffsetOf(box) > 0`):
the anchor's top, the float's top and foot in the list, and whether the float fits an empty page, which
`computeDifference` and `createForcedNodes` read to make a page that cannot hold the float's foot too long at the
float's start edge, so that the page breaks before the anchor and the anchor and float start the next page together
(CR-023 §4.1 as built, r20). With no offset nothing was armed, the edge was taken at the first break after the
anchor's line, and the float was drawn from the anchor's top past the body's foot, Apache FOP 2.12's own binary
doing the same on the same FO (`~/fop-session-tools/2026-11-03-float-defects/p1/`, `apache-low-plain.pdf`). The
lines beside it ran into the margin, and what lay past the page's edge was lost with no event.

## 3. As built

`handleBox` arms the move for every anchor box that carries a side float. The placement is unchanged at offset 0:
the float's target top is set for an offset above 0 only (`PageBreaker.addAreasForFloats`), so a no-offset float is
still placed at its anchor's top by Apache's path, and its intrusion starts at the anchor's line as before. What
changes is only the refusal: a page that cannot hold the float's foot below its anchor is too long at the float's
start edge, the page breaks before the anchor, and the anchor and its float open the next page. A float taller than
an empty page is not refused (`floatFitsAnEmptyPage`), as before; that is item T.

This is a change to FOP's own behaviour for a plain `fo:float`, and Apache's defect too. Upstream-bound, not drafted
(Jason's call), as CR-031 §11's forced-break fix is.

**Apache's `float_11.xml` changes expectation.** Its right float, 36pt tall, is anchored 228pt down a 252pt body.
Apache FOP keeps the anchor's first lines on page 1 beside the float, draws the float above its anchor (the clamp in
`FloatContentLayoutManager.addChildArea`, item 8's 1(b)), and narrows page 2's first line as if the float were still
beside it (span 252000; page 2's first line "lacus efficitur ultrices. Etiam id" at ipd 162000, end-indent 27520).
With the move the anchor paragraph and the float open page 2 together: span 228000, page 2's first line "Etiam"
at ipd 62000, end-indent 36440. The expectations are updated with a change notice, as `float_overflow-below.xml`'s
were for CR-024 §11.3. The other five float tests and the fork's own are unchanged.

Files: `PageBreakingAlgorithm.java`; layout tests `float_11.xml` (above) and `float_offset.xml`, case 10 (a 100pt float with no offset after 38
lines of a 698pt body: the page keeps its 38 lines and no float, the next page opens with the anchor's line beside
the float, which is drawn there as Apache's no-offset float is, a block in the flow with no top-offset).

## 4. Measured here (2026-11-03)

**The minimal FOs** (`p1/`, FOP's command line, FOP mode; a 400pt page with 50pt margins, a 300pt body):

| case | before | after |
|---|---|---|
| `low-plain`: a 15-row table (195pt) floated at offset 0 after 18 lines | 1 page; rows 12 to 15 past the page's edge, lost | 2 pages; page 2 opens with rows 1 to 15 at the margin and "anchor text" beside them at y 51.3 |
| `low-offset`: the same at offset 0pt written | as `low-plain` | as `low-plain` |
| `low-offset1`: the same at 1pt (CR-023's move) | 2 pages | 2 pages, unchanged |
| `plain`: a 30-row table taller than the body, anchored at the top | rows 27 to 30 lost | unchanged (item T) |
| `offset`: a float that fits | 1 page | unchanged |

No warnings.

**Word's probe**, through docx4j's own path (`render.sh` with the changed classes as an overlay on b319's
classpath, docx4j's FO of ac66f5518, one pass; 13 pages, Word's 13):

| | b319 | this | Word |
|---|---|---|---|
| case 4, square picture, offset 0 | anchor at y 578.9 on page 7, beside a picture drawn to about 816 | page 8 opens with "P178 anchor 4." at 74.9, x 323.0, beside the picture | page 8, 74.7, x 323.2 |
| case 6, square picture, 1pt | page 13 at Word's | unchanged | 74.7 |
| case 1, no-offset table, 200pt beside | drawn from the anchor into the margin | moved whole: page 2 opens with rows 1 to 15 and "P37 anchor 1." at 74.9, x 323.3 beside them | split: rows 1 to 12 on page 1, row 13 at page 2's top, the anchor at 74.7, x 323.9 beside the rest |
| case 2, 1pt table | moved whole (CR-023) | unchanged | split |
| case 3, full-width table, in the flow | split, Word's pages | unchanged | split |
| case 5, top-and-bottom picture, a block | Word's pages | unchanged | |

Case 4 is at Word's lines; cases 1 and 2 wait for T.

## 5. Pass criteria for the gate (docx4j-12, against b321 on r58)

- `float-foot-crossing`: case 4 at Word's page 8 and lines; case 1 moved whole to page 2 (not Word's split, but
  no rows lost); nothing else moving. The probe's score rises from 0.9620.
- `float-tall-beside` unchanged at 0.8743 (its floats are taller than the page: T).
- Corpus: any no-offset side float anchored too low for its page now moves to the next page with its anchor.
  docx4j writes such floats for pictures, text-box bands, grouped drawings and text-beside bands (and floating
  tables with no or a non-positive offset). Each mover is to be read against Word: a picture or band that moves is
  Word's rule; a table that moves whole is the interim until T.
- No document losing lines; events equal or falling.

## 6. The gate (docx4j-12, 2026-11-03)

**b322, r59's core on cp-b321, against b321: PASS.** One mover, the probe `float-foot-crossing`, +6 lines, 0.9620 to
0.9734, 13 pages as Word's. `float-tall-beside` unchanged at 0.8743. 0 corpus movers; events equal (line 8380, clip
1552, bcov 1276, region 73, body 18, glyph 63). None of the four corpora has a no-offset side float anchored too low
for its page, so on the corpus the change is inert and the probe is the only measure; the defect still reaches
shipped output in principle. The case readings of §4 (case 4 at Word's page 8 and line; case 1 moved whole, the
interim) went into the register as given. Recorded in the Enterprise register (attempts.md and
causes/floating-table-offset-and-sides.md, 6fb323b). Script `~/fidelity-cr030/gate-b321-b322.sh`, classpath
`~/fidelity-cr030/cp-b322.txt`.

Merged to `2.12-docx4j.1` by fast-forward at 2e4f3b2a6 on the pass. **b322 on r59 is the fork's baseline for later
gates;** T is gated against it.

## 7. Open

- A floating table that crosses the page foot should split (Word's cases 1 and 2): item T, probed next.
- A float taller than the page body less its offset (6705, `float-tall-beside`): item T's other half; the float is
  still cut at the page's foot, and `FloatContentLayoutManager.addChildArea` still drops the offset for it (item 8's
  1(b)).
- 10747's banner, pulled up to the paper's edge in Word with its paragraph's line kept: one document, not reached
  by the probe, unchanged here (such a float moves now).
