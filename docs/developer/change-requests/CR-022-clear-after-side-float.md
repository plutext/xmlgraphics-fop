# CR-022: honour `clear` on a block-level FO after a side float

Status: IN PROGRESS 2026-10-08, on branch `CR-022-clear-after-side-float` off `2.11-docx4j.6`; measured on the command
line (§4); full `fop-core` suite 3864 tests, 0 failures, 4 skipped, checkstyle clean (§5); the docx4j gate pending (§9). Registry key `fop/CR-022`. Capability
`clear-after-side-float` (§3.4). Decided by Jason on 2026-10-07 as the first item after `2.11-docx4j.5` shipped
(CR-020 §7), and started on his word on 2026-10-08.

Enterprise CR-001 §6.6 item 45's text records the defect (`clear="both"` on the table "makes no difference"), CR-020
§7 the decision, and docx4j's corpus document 10855 the cost: its rubric table is drawn over its text box, where Word
puts it below the box.

## 1. The defect

A side float is set beside the lines that follow its anchor, until a line lies wholly below its foot (`fop/CR-020`).
XSL-FO has one property that says "not beside a float": `clear` (XSL 1.1 §7.19.2). FOP's property maker accepts it
on any FO and `fo:float` binds it, but nothing in the layout reads it: a block-level FO that follows a float is set
beside the float whatever its `clear` says.

Measured by the docx4j session on r14 (CR-020 §7): `clear` on an `fo:table` after a 60pt float, and on a block wrapped
round the table, changes nothing in four variants (rows at 106, 121 and 136pt either way). In 10855 the rubric table
starts right below its heading at 121.82pt and is drawn over the text box; Word has it below the box at 155.35pt.
docx4j therefore makes no text-box band where a table follows the anchor paragraph, and the band is withheld from
the documents where it would matter most.

## 2. The semantics taken

XSL 1.1 lists `clear` on `fo:float` only, where it means a float placed below earlier floats. CSS gives the same
property to any block-level box, with "clearance": the box's before edge is moved down to the foot of the floats on
the named side, and the lines above it keep their shortened width. That is what a word processor does with a table
that follows a wrapped text box, and what docx4j needs, so the fork reads `clear` on the block-level FOs that can
follow a float: `fo:block`, `fo:block-container`, `fo:table` and `fo:list-block`. The values are XSL's: `start`,
`end`, `left`, `right` (`left` and `right` map to `start` and `end`, as FOP's maker already does), `both`, `none`.

The rule: at a legal break while a side float is on, if the next box starts a block-level FO whose `clear` names the
side the float is on, that break is the float's edge, and the content from it on is laid out at the float's foot, full
width. An FO whose `clear` names the other side, or `none`, is set beside the float as before. Content between the
anchor and the cleared FO (a paragraph with no `clear`) stays beside the float.

## 3. The change

### 3.1 The FO classes read the property

`Block`, `BlockContainer`, `Table` and `ListBlock` bind `PR_CLEAR` and expose `getClear()`. Nothing else in the FO
tree changes; the property's maker and its enum values are FOP's.

### 3.2 The edge forced by `clear` (`PageBreakingAlgorithm.considerLegalBreak`)

In the pass where the float is on (`floatHeight != 0`), CR-020 made the float's edge the first legal break after
which the content and the glue up to the next box reach the float's foot. This change adds a second condition at the
same place: the break is also the edge when `clearsFloatAtNextBox(elementIdx)`. That method:

- reads which side the float is on from the page sequence's intrusion adjustments (`getStartIntrusionAdjustment()`
  and `getEndIntrusionAdjustment()`, which `FloatContentLayoutManager.addChildArea` sets when the float's area is
  placed and the end-of-float handling zeroes);
- walks the position chain of the next box after the break and, for each layout manager in it whose FO carries a
  `clear` other than `none`, checks that the manager is absent from the previous box's chain. That is how "the FO
  starts here" is told from "a later box of the same FO": a block's second line has the same `BlockLayoutManager`
  in both chains. At the head of the list there is no previous box and nothing clears, so an FO the restart itself
  begins with (the float's own anchor) never ends the float;
- returns true when that FO's `clear` is `both`, or names the side the float is on.

**A position chain can end in itself.** `TableContentPosition.getPosition()` returns `this`, so a walk up the chain
that does not stop at the table's layout manager spins for ever. CR-020's walk (`nonRestartableLM`) returns at the
first non-restartable manager, which a table's is, and so never met it; the first build of this change hung
CR-020's layout test (the fourth sequence, a float reaching into a table) at 100% CPU in `clearsFloatAtNextBox`,
found with a thread dump. Both walks here go through `nextInChain`, which ends the chain where a position wraps
itself.

The table deferral of CR-020 still applies at the same break and is unaffected: the cleared FO's own non-restartable
manager (a table's) is not the previous box's, so the edge before a table is not deferred.

### 3.3 The content after the edge starts at the float's foot

CR-020's `deactivateNode` recorded the height at the edge as the float's effective height, so the restarted content
started below the space at the edge. An edge forced by `clear` lies above the foot, so the effective height is now
the greater of the foot and the edge, and the difference is kept as `floatClearance`. `PageBreaker` reads it when it
records the end of the float and holds it as `pendingFloatClearance`; `AbstractBreaker.addAreas` takes it once
(`takeFloatClearance()`, 0 in the base class) for the first part laid out after the edge and adds it to the layout
context's space-before, the same path `display-align="center"` and `"after"` use to push a part down, which every
block-level layout manager's `addAreas` honours through `addBlockSpacing`. The page breaking arithmetic needed no
change: `PageBreakingAlgorithm.initialize` already counts the restarted content from the float's foot
(`getOffsetDueToFloat()`); only the areas lacked the gap.

### 3.4 The capability

`Docx4jFop.CLEAR_AFTER_SIDE_FLOAT = "clear-after-side-float"`, the twentieth. Apache FOP accepts `clear` on these FOs
and ignores it, so docx4j may write it unconditionally; the capability tells docx4j whether writing it will keep a
table off a text-box band, and so whether the band may be made where a table follows.

## 4. Measured on the command line (plain fork, no docx4j)

`clear.fo` (six page sequences; the FO is the layout test's), read from the area tree and from the PDF with
`pdftotext -bbox`. Body top at 72pt; a 12pt font, 16pt lines; the heading has `space-after="10pt"`; the float is 300pt
wide and anchored in the heading's line.

| case | before (base, = Apache FOP) | after |
|---|---|---|
| A. 40pt left float, then `fo:block clear="both"` | first line beside the float at 26pt, 151pt wide (3 lines) | block at 40pt, 451pt wide (2 lines); PDF y 114.45 = 72 + 40 + 2.45 |
| B. the same, `fo:table clear="both"` (200pt wide) | rows at 26pt | rows at 40pt; the block after at 73pt |
| C. left float, `clear="end"` | beside, 151pt | unchanged |
| D. right float, `clear="right"` | beside, 151pt | block at 40pt, 451pt wide |
| E. no `clear` | beside, 151pt | unchanged |
| H. 60pt float; a paragraph with no `clear`, then `clear="both"` | both beside (26 and 42pt) | first beside at 26pt, 151pt; the cleared block at 60pt, 451pt |

The clearance shows in the area tree as a block of height 14pt (A, B, D) or 18pt (H) and no width between the
heading and the cleared FO, as `display-align` produces. The flow's height on page 1 goes from 90pt (16 + 10 + 48 +
16) to 88pt (40 + 32 + 16). No warning in any case.

## 5. Tests

- `fop/test/layoutengine/standard-testcases/float_clear.xml`: the six cases above, 16 checks (line widths, line
  counts, flow heights, the kept space-after). Green.
- `float_side-edge-space.xml` (CR-020's): green, the edge logic there unchanged.
- `Docx4jHooksTestCase` and `Docx4jFopTestCase`: twenty capabilities.
- Full `fop-core` suite: 3864 tests, 0 failures, 0 errors, 4 skipped; checkstyle 0 findings (2026-10-08).

## 6. docx4j

docx4j writes `clear` on the block-level FO that follows a text-box band where the band's content must not sit
beside it: Word puts a table below a wrapped text box when the table's anchor paragraph follows the box's. With the
capability present it keeps the band for 10855 and the documents like it; without it (Apache FOP) it withholds the
band where a table follows, as today (CR-020 §7). A pass: 10855's rubric table at Word's 155.35pt below the text
box, the band on; nothing else moving on the corpora with the band withheld, since `clear` is inert unless a float
is on.

## 7. Upstream

An extension of XSL's `clear` to block-level FOs, as CSS has it, not a fix of a defect in what XSL asks for; so it
is an improvement for Apache to take or not. The code depends on CR-020's edge logic (`floatEdgeWidth`), so an
upstream branch would stack on FOP-3355 (#124) and FOP-3354 (#123). Draft JIRA text in
`docs/upstream/clear-after-side-float.txt`, unfiled, to be measured on Apache `main` before filing and filed only on
Jason's word.

## 8. Not addressed

- `clear` on `fo:float` itself (XSL's own meaning, a float below earlier floats): `Float` binds the property and
  nothing consumes it, in FOP and here.
- A float whose foot lies past the end of the page: `floatHeight` is clamped to the page, so a cleared FO goes to the
  page's end and the next page. Not measured.
- A cleared FO inside the float's own anchor paragraph, or inside a table cell beside a float: the first is the
  list-head case (nothing clears), the second not measured.
- Both-sides wrap and the offset float (docx4j CR-032 §4.2) are `fop/CR-023`, not this.

## 9. Gate (the docx4j session)

Pending: renderer r16 from this branch, against b180 (docx4j 17.3.1's code on `2.11-docx4j.5`).
