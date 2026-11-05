# CR-036: a line beside a side float that crosses the page's foot

- **Status:** BUILT 2026-11-05 on branch `CR-036-line-across-foot-beside-float`, cut from `2.12-docx4j.1` at 5c63b6ade, code
  cc06737cd, drop r67. **Gated PASS 2026-11-05 by docx4j-67 (b349 on r67 against b347 on r66, §5) and MERGED to `2.12-docx4j.1` by
  fast-forward; unreleased.** Ordered 2026-11-05 by Jason ("fix Item 55, the foot defect in .1"). **A defect in `firstBoxAfterEdgeCrossesPageFoot`
  was found after the merge and is NOT fixed: §6.**
- **Serves:** Enterprise CR-001 §6.6 item 55: beside a side float, a line that crosses the page's foot by a few points stays on
  the page, drawn past the body's foot, where Word moves it.
- **Capability:** none. A fix, not a hook.

## 1. The defect

Found by docx4j-67 on Word's `float-foot-empties` probe (19 cases, 2026-11-05): ten short lines beside a floating table that
reaches almost to the foot, the last line crossing the foot by a set amount, with and without text, with and without a float.
Word moves a line that crosses the foot, the same beside a float or with none. The renderer did so with no float (cases N, right
on every drop) and beside a float only where the line began below the foot: S, V and K at +1 and +4 were wrong, on r62, r64, r65
and r66 alike; V+4 drew E10 at y 540.9 against a 538.6 foot. S+10 and V+10 were right.

## 2. The mechanism

The heights of a list beside a float start at the float's edge, so `computeDifference` measures them from there and never finds
the page too long. The page's foot is known in that list only as the cap on the float's foot, `Math.min(height, lineWidth -
yOffset)`, Apache's own line. The float's end edge is taken at the first break after which the next box lies wholly below that
height (fop/CR-020). So a line beginning below the foot takes the edge before it, and goes to the next page; a line beginning above
it and crossing it does not: it is read as a line beside the float, and it stays. The threshold docx4j-67 saw between 4pt and 10pt
is the line's own height, 9.2pt: at +10 the line begins below the foot.

Reproduced on a plain FO through `org.apache.fop.cli.Main`, stock managers, so it is no Word-mode matter:
`~/fop-session-tools/2026-11-05-cr036-foot/`, `v4.fo`, E10 at 539.9 on page 1. Not shown on Apache `main`: its build (branch
`FOP-text-indent-after-block`, on 1301bcf74) lays `noff2.fo`, a no-offset float reaching the foot, with every line on page 2 and none
beside the float, so the crossing line does not arise there. Apache has the cap; whether it has this defect is not measured.

## 3. The change

- `PageBreakingAlgorithm.nextBoxCrossesPageFoot`: in the end-edge test of `considerLegalBreak`, a next box that would cross the
  page's foot, read against the page's own height less the list's start, is an event as a foot is. The edge is taken before it, and
  the content after it starts past the room and goes to the next page.
- `firstBoxAfterEdgeCrossesPageFoot`: a list has no break before its first box, so a box crossing the foot straight after a float's
  start edge is met in `PageBreaker.resolvePendingIntrusion`, which takes CR-032's way: nothing beside the float, the content after
  the edge at its foot. Found checking the fix against a nearby shape (`first.fo`).
- `PageBreaker.edgeAtPageFoot`: such an edge is a page break, so the block read again keeps no space-before (CR-020 keeps it because
  a float's edge is no break in the flow). Without this the moved line opened its page 6pt low where the same line with no float
  does not (`sb.fo` against `sbn.fo`, 12.5 against 6.5). Found the same way.

Not covered: a break inside a table, which is not restartable and takes no edge (CR-020).

Only the first of these is Word's measured rule. The second and third rest on FOP's own behaviour: Word's `float-foot-empties` does
not reach them and no corpus document does, so `float_page-foot.xml` pins what FOP now does there, not what Word was seen to do.
They are the consistent reading of the first, and may well be right, but they are unmeasured. docx4j-67 offered to add the two
shapes to a probe; ordered by Jason 2026-11-05 ("do the Probe for the two unmeasured shapes"), §6.

## 4. Measured

Through `RenderFo` on b347's FOs and `cp-b347.txt`, this change as a javac overlay: `float-foot-empties` 19 of 19 at Word's AFTER line
(13 of 19 on r66), 4083 and 6293 0 lines from b347. The plain FOs: V+1 and V+4 move, V-1 stays, V+10 as before.

Layout test `float_page-foot.xml`, five sequences: the line that fits stays; the line that crosses opens the next page at the full
width; the same with no float; its space-before discarded there; the first line after a start edge. It fails without the change.
CI's command 4102/0, checkstyle 0, spotbugs 0.

## 5. Gate

Against b347 on r66. Reach: any box beside a float that crossed the page's foot. Pass: `float-foot-empties` 19 of 19 at Word's; 4083
and 6293 unchanged from b347; no loser; events equal or falling.

Gate b349 on r67 (cc06737cd) against b347 on r66, docx4j-67, docx4j b267f24ce: PASS on every item, no mover, events equal. Of 863
PDFs only the probe and 11875's clock differ: no corpus document moved and no page count rose, where this session had expected
movers. `float-foot-empties` 19 of 19 at Word's AFTER line (13 on r66), its 6 moved lines all closer, mean distance from Word
15.30pt to 0.10pt. 4083 and 6293 no line from b347. docx4j-67 agreed the first part's mechanism, the 9.2pt threshold included,
and has not checked the internals independently. The code merged differs from r67 in two comments only (§3's caveat written into
them; CI's command 4102/0, checkstyle 0, spotbugs 0 again), so r67 stands. b349 on r67 is the fork's baseline for later gates.

## 6. Counted twice: a defect found after the merge

Found 2026-11-05 checking on plain FOs, before asking for Word's probe of §3's two shapes, that each proposed case reached its branch.
`firstBoxAfterEdgeCrossesPageFoot` starts from the edge node's `totalWidth`, which already holds the glue after the break
(`BreakingAlgorithm` adds it when it makes the node), and adds that glue again. So a box with a space before it is read as lower than
it is by that space, and one that fits within the space of the foot is taken for crossing it. `nextBoxCrossesPageFoot`, the part
Word's probe measured, is not affected: it reads `widthUpToNextBox`.

Two effects, both shown against the same FO with no float, body 481.9pt, lines of 9.2pt, 6pt before:

- At a start edge, the line moves. Case R-1, a line that fits by 1pt: r67 opens the next page with it, and with no float it stays.
- At an end edge, the line stays and loses its space. Cases X-1, X-3 and X-5: r67 draws it at y 529.0, 527.0 and 525.0, and r66 and
  the no-float case at 535.0, 533.0 and 531.0. **This is a regression from r66**: `edgeAtPageFoot` drops the kept space on a page
  that was not broken.

`float_page-foot.xml` and `float-foot-empties` do not reach it, and neither did b349's corpus (no mover). This session's check of "a
nearby shape" in §3 tried a space-before only on a line that did cross. Measured first on plain FOs through
`org.apache.fop.cli.Main`, then on docx4j's FO of Word's probe `float-foot-edges` through `RenderFo`, r67, modes 15 and 12, where it
holds and reaches a space-after as well (F-1, the spacer's 4pt after).

A candidate, in a scratch overlay and not in this tree (`~/fop-session-tools/2026-11-05-cr036-probe/overlay-src/`), adds only glue lying
past an auxiliary box, which the node's total stops short of: R-1 and X-5 to X-1 then equal the no-float case, X+1 and X+3 stay at
r67's, and of the 31 first cases only R-1 changes. On docx4j's FO it changes five of the probe's 47 cases in each mode: F-1, R-1, X-5,
X-3, X-1. The fix is not ordered; it needs Jason's word, a drop and a gate. The probe's F, R and X series measure both shapes against
Word.

Separately, and older than this CR: a paragraph with `w:br` line breaks whose third line meets a float's edge is drawn twice. Series B
through docx4j's writer has M1 and M2 on page 1, then M1 to M4 on page 2, on r67 and on r66; a `linefeed-treatment="preserve"` paragraph
does the same on a plain FO. Its cause is not traced. docx4j-67 finds 8 corpus documents with a `w:br` paragraph within six blocks after
a text-anchored floating table, and no line of 9 characters or more repeated beyond Word's in b358: no corpus document has hit it, which
does not say the shape is absent. No fix is ordered.

Word's probe `float-foot-edges` (47 cases a mode, modes 15 and 12) was recut once for this session's geometry and re-traced clean: every
case reaches its branch under docx4j's writer. It waits on Jason's golden run. Tools and traces: `~/fop-session-tools/2026-11-05-cr036-probe/`.
