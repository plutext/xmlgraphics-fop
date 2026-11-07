# CR-038: an anchored box beside a float and at a page's top; a tab past a cell's edge

- **Status:** BUILT 2026-11-05 on branch `CR-038-box-and-tab`, cut from `2.12-docx4j.1` at 561571f14, code 45594ce31, drop r70
  (`~/fop-renderers/r70-CR-038-45594ce31/`, sha256 20232efb...). **Gated PASS 2026-11-05 by docx4j-67 (§8) and MERGED to `2.12-docx4j.1` by fast-forward, for `.1`, on
  Jason's ruling through docx4j-67 ("r70 (CR-038) goes into 2.12-docx4j.1"); RELEASED in `2.12-docx4j.1` (Maven Central 2026-11-07, version commit c69e89ffb). b369 on r70 was the fork's baseline for later
  gates until b372 on the same jars.**
  Ordered through docx4j-67, under Jason's "do 4083's remaining X marks and row heights".
- **Serves:** corpus 4083's marks and row heights. Enterprise CR-001 §6.6 items 58 to 61; item 57 gains a second shape (§6); item 62 came of the gate.
- **Capabilities:** `box-x-from-column`, `box-top-space`, `tab-past-cell`, the thirty-second to thirty-fourth. The float's end edge (§2) is
  a fix, not a hook.

Word's readings are docx4j-67's goldens of 2026-11-05, read here too with `lines.py`: `vml-box-float-edge`, `vml-box-page-top` and
`tab-trailing-narrow-cell`, modes 12 and 15. Tools, overlays and comparisons: `~/fop-session-tools/2026-11-05-box-float-edge/`.

## 1. What Word does

**A box beside a float** (x of the box's text; E1, E2 and E6 measured from the text):

| Case | Word 12 | Word 15 |
|---|---|---|
| E1, E2, E6 from the text | 305.6 | 101.8 |
| E3 from the margin | 101.8 | 101.8 |
| E4 from the page | 29.8 | 29.8 |
| E5 from the text, float on the right | 101.8 | 101.8 |

A box measured from the margin or the page never takes the float's intrusion. One measured from the text takes it at mode 12, with its
line, and not at mode 15. Mode 14 is not measured.

**A box in a paragraph that opens a page** hangs from the paragraph's top, above its space-before, whatever happened to that space. A box at
margin-top 10pt, body top 72: its text at 91.7 in K0 to K4 and K6 to K9, both modes, with the space kept (K0), dropped (K1, K2, K3, K7),
partly kept (K8) or under a border (K4). K5 at 13.1: 94.8. K10, second on its page: 111.6.

**A tab past a cell's edge** (row-to-marker pitch; one line is 18.0): a tab reaching a custom left stop past the edge is honoured at mode 12
(T1, T3, T9 18.0) and breaks at mode 15 (31.4, 40.3, 31.5). On the default grid it breaks at both (T2 31.5, 31.4). T3's text past the edge
is not in Word's text layer at all.

## 2. The float's end edge

The trace was asked for on 4083's B.3: its X stood 231.8pt right of Word's, the float's width.

docx4j writes an anchored box as a zero-height `fo:block-container` first in its paragraph's block. The float's end edge fell between that
holder and the line, inside the block, so the block was two areas: the first, with the holder, in the part beside the float, taking
`setXOffset(startIntrusion)` in `FlowLayoutManager.addChildArea`, and the line in the next part, taking none.

The holder is not beside the float: on E1 its top lies 8pt below the foot. The edge could not be taken before it, for two reasons:

1. `nextBoxHasContent` stops at the holder, a box of width 0, and CR-024 §11.12 allows no edge before a box without content.
2. On 4083 that break is also the keep-with-next of the paragraph above, an infinite penalty of class line, and `handlePenaltyAt` offered
   such a keep as an end edge only for an obstacle. The break after the holder is a keep-together of class page, which CR-022 §3.5 lets be
   the edge.

The second is a defect of its own, with no box involved: with B.3's holder removed from the FO, its heading goes to x 274.3, beside a float
whose foot it is past. A paragraph kept with the one above and wholly below a float's foot was set beside the float.

**The change** (`PageBreakingAlgorithm`): `nextBoxHasContent` reads past an empty block-container's box (`isEmptyContainerBox`), and
`handlePenaltyAt` offers a class-line keep as an end edge for every side float, as it did for an obstacle since §11.19. The first serves all four
places the test is made, so at a start edge too a holder goes with its line; only the end edge is measured.

**`widthUpToNextBox`, left as it is.** On 4083 it stops at the auxiliary box before B.3's border, missing 3.48pt of border and padding, as
first noted to docx4j-67. That is not a gap: the same measure serves a bordered paragraph with no box, testing the foot against the border's
top. A foot inside that border and padding is unmeasured either way, and the height at the edge stays consistent, since the restarted list
holds the border and padding itself. Nothing was changed.

## 3. `box-x-from-column`

`fox:x-reference="column"` on an absolutely positioned `fo:block-container`: `BlockContainerLayoutManager.intrusionLeftOut` takes the block's
intrusion back out of the viewport's x. Default `auto`, the box moving with its block. docx4j writes it for a margin or page reference, and
for a text reference from mode 15. Not for a box in a table cell, which Word measures from the cell.

## 4. `box-top-space`

`fox:top-space` on the same container names the part of `top` that stands for the anchor's space-before. docx4j's proposal was to give back
`static - min(static, actual)`, `actual` the space the block kept, and asked whether `actual` is at least `static` mid-page.

It is not safe to rest on that. The space resolver gives a contested space to one side: where the space-after above wins, the block records
none. docx4j's `textRelativeTop` nets off the space-after above, so its `static` is 0 there, except after a table, for which
`previousSpaceAfter` returns 0. So the rule applies only where the block opens the column (`topSpaceGivenBack`): nothing with height before
it in its enclosing areas or the flow, a side float apart. Further down nothing is dropped, and it gives back nothing. Layout case h pins it:
without that test, a mid-page box stood 18pt low.

`actual` is the sum of `effSpaceBefore` up the enclosing blocks, so a space on an outer block counts (K4, K5). The walk gives up at anything
but a block or an in-flow block-container: a box in a list item or a cell is left as it is.

The arithmetic against Word, `top` as docx4j writes it: K0 18 of 18 kept, K1 18 of 0, K3 12 of 0, K4 18 of 0 (the border's part of `top`
not named, so kept), K5 12 of 0, K6 8 of 0, K8 18 of 8, K10 mid-page. K9 names nothing.

## 5. `tab-past-cell`

`docx4j:tab-past-cell="true"` on `fo:root`, written below mode 15. `noStopReachable` then answers no for a custom left stop in a table cell.
It tells a custom stop from a grid one: `nextTabStop` sets `stopIsCustom` when the stop came from `docx4j:tabs`, and clears it for the default
grid and a hanging indent's implicit stop. Cells only, by the existing `inCell`: `tab-clamp-right`, the body's case, is mode 15.

## 6. Left

- **KNOWN LIMIT, accepted by Jason ("accept the known limit (but document it)"): text after a tab past a cell's edge is drawn over the next
  cell.** Below mode 15, with `docx4j:tab-past-cell`, the row is Word's height, but where text follows the tab Word draws none of it past the edge
  and the fork draws it: T3's `1` at x 108, over the `T3 row` text of the next cell at 95.8. Hiding that text is not built. Limiting the rule to a
  tab with nothing after it was the alternative, one condition, keeping today's two lines; not taken. No corpus document has a custom stop past a
  cell, so nothing in the corpora shows it.
- **E1 is item 57.** Word sets E1's line beside the float, its foot inside the line's own space-before, at both modes; the fork sets it at the
  margin. At mode 12 the box now follows that line, to 101.7, against Word's 305.6: right on r69 by accident, its line wrong all the same.
  This is item 57's second shape, after `float-foot-edges` X. 4083's B.3 stands against a simple reading: docx4j-67 reads B.2's foot inside
  B.3's 18pt space-before, and Word's line is at the margin. B.3 is bordered, so its space lies outside the border, and "the line's own space
  counts, a border's outside does not" would fit E1, X, B.3 and item 44 alike. Partial, not measured; a bordered case in a probe would
  separate it.
- **K6 and K9 are docx4j's.** K6 at mode 12 keeps 8pt of space in Word and none here, the FO not retaining it. K9, a box parted from its line
  by a page break, is FOP doing what the FO allows; a keep-with-next on the holder cures it (measured), and docx4j-67 takes it. That keep does
  not help E1: the break stays an edge.
- **T2's second line** stands 2pt short of Word's (29.5 to 31.5), unchanged, not part of this.
- A holder in a list item is not given space back (§4).

## 7. Measured

Through RenderFo, docx4j-67's FOs on b362 with the attributes written by `hookfo.py` as the writer would. Lines off Word's by more than 1pt:

| Probe | r69 | This |
|---|---|---|
| `vml-box-float-edge` mode 12, 54 lines | 3 (E1 text, E3, E4) | 2 (E1 text, E1 box) |
| `vml-box-float-edge` mode 15, 54 lines | 6 | 1 (E1 text) |
| `vml-box-page-top` mode 12, 273 lines | 12 | 4 (K9, K6 and the two lines after it) |
| `vml-box-page-top` mode 15, 273 lines | 9 | 1 (K9) |

`tab-trailing-narrow-cell` mode 12 pitch, Word, r69, this: T1 18.0, 29.4, 17.9; T3 18.0, 41.0, 18.0; T9 18.0, 29.5, 18.0; T2 31.5, 29.5, 29.5;
the rest unchanged. Mode 15, attribute unwritten: nothing moves.

4083 on b362's FO, no attribute written: the X to 73.7, Word's, one line of 391. With `docx4j:tab-past-cell` and `fox:top-space="18pt"` on B.7's
box, written by hand: B.7's X at 75.0, Word's 74.9, 57.0 on r69; page 1's A.4 at 742.3, Word's 744.0, 753.8 on r69, the 1.7pt left unread.
All of this was read again on r70's jar, not only on loose classes.

Layout tests `float_end-edge-before-box.xml`, `block-container_fox-x-reference.xml`, `block-container_fox-top-space.xml`; `TabPastCellTestCase`.
Each part reverted or removed in turn fails its test: the edge code, the read past alone, the keep alone, the column test. CI's command 4118/0,
checkstyle 0, spotbugs 0, no existing layout expectation changed.

## 8. Gate

PASS 2026-11-05, docx4j-67, docx4j on 7f6162ea2, its side uncommitted in the second run.

**Control, b368 on r70 against b367 on r69, docx4j writing nothing new:** 4 movers, all expected. 4083 +1 (0.9739 to 0.9772), its one moved line
closer, B.3's X. `vml-box-float-edge` mode 12 -1, E1's box (§6); mode 15 +1. So the wider end edge moved no other corpus document. Events equal
but `bcovi` 652 to 656, read here: 4083 alone, 286 to 288 in each of its two corpus copies, one warning more a pass, at the same FO position as
one already counted, B.3's box being laid out again when its block is read again.

**With the three attributes, b369 against b368:** 6 movers, all up, 4083 the only corpus one: +3, 0.9772 to 0.9870, 63 of 63 moved lines closer,
mean |dy| 9.13 to 0.91, in both copies. `vml-box-float-edge` mode 12 0.9412 to 0.9804, mode 15 0.9231 to 1.0000; `vml-box-page-top` mode 12 0.9774 to
0.9962, mode 15 0.9737 to 1.0000; `tab-trailing-narrow-cell` mode 12 36 of 36 paired closer. Events equal but `line` 8496 to 8497, read here: that
probe alone, 0 to 1, the tab past the edge overrunning its line. All 18 of 4083's X marks within 1.2pt of Word's but page 5's at 1.9pt.

T3's overprint: docx4j-67 keeps it, no corpus document having the shape, and puts it to Jason as a known limit.

**A correction of the gate's side note.** docx4j-67 reported an absolute box lost at a page's foot where its paragraph has no text (1616's caption,
561's three lines), cured by the keep it now writes on every holder. Traced here: nothing is lost. The box is drawn past the page's edge, and the
reader shows only what is on the page. Enterprise §6.6 item 62, partial.
