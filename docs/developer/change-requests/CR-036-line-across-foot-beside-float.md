# CR-036: a line beside a side float that crosses the page's foot

- **Status:** BUILT 2026-11-05 on branch `CR-036-line-across-foot-beside-float`, cut from `2.12-docx4j.1` at 5c63b6ade, code
  cc06737cd, drop r67. **Gated PASS 2026-11-05 by docx4j-67 (b349 on r67 against b347 on r66, §5) and MERGED to `2.12-docx4j.1` by
  fast-forward; unreleased.** Ordered 2026-11-05 by Jason ("fix Item 55, the foot defect in .1").
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
shapes to a probe; not ordered.

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
