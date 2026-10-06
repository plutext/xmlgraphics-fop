# CR-020: a side float ends at the break before the first line below its foot, keeps the space there, and never ends inside a table

Status: IMPLEMENTED 2026-10-07 on branch `CR-020-float-edge-space` (from `2.11-docx4j.5` at 09c7bc857), not
merged, not gated. The fop-core suite passes on the branch (3853 tests, 0 failures; checkstyle clean), and the
new layout test fails without the change. Registry key `fop/CR-020`. A fix, not a hook, so upstream-bound: two
JIRA drafts, `docs/upstream/float-edge-after-space.txt` and `docs/upstream/float-edge-inside-table.txt`, not
filed. No capability: docx4j has no workaround on either item, and the fix changes nothing unless a side
float's end falls at a space or inside a table.

Requested by the docx4j session (docx4j-13) on 2026-10-07, two findings measured there on r13 and reproduced
here on plain FOP without docx4j's layout managers:

- **Enterprise CR-001 §6.6 item 44** (written there, uncommitted at the time): a side float narrows the first
  line after a space that carries that line below the float.
- **Item 45** (to be written there): a side float whose height reaches into a following `fo:table` throws
  `NoSuchElementException`. This is the signature item 20 recorded in 2026-09 (`NoSuchElementException` from
  `LMiter.next` in `PageBreaker.handleFloatLayout`) and the bounded pass of 2026-10-03 could not reproduce; the
  table is the shape it needed. The two entries should say so.

Why it matters to docx4j: it tried an empty band float to set text beside wide wrapped text boxes, as Word
does (`WordLayoutFixups.textBesideBand`, gate b167; probes `vml-box-beside`; corpus 561, 10855), and withdrew it
on these two items (10855 hit the crash; a heading beside a 31pt band, then its rubric table) and on 561, where
a 393pt band left page 15's lines unnarrowed. Item 44 cost one line in the paragraph after such a box (probe
Pa: Word 4 lines, docx4j 5).

## 1. The mechanism, read and measured

Reproductions in `docs/developer/repro/float-edge-after-space.fo` and `float-edge-inside-table.fo`.

**Item 44.** FOP ends a side float in `PageBreakingAlgorithm.handleBox`: the first line box at which the
accumulated height reaches the float's height sets `handlingEndOfFloat`, and the next legal break is the edge,
after which the list is read again at full width (`PageBreaker.handleFloatLayout`). `handleGlueAt` adds glue to
the height but never tests it. The breaker's element list for a paragraph boundary is `box, penalty (the
SpaceResolver's break position), glue (the resolved space), box`. With a 16pt heading line, a 10pt space and a
20pt float: box 16, penalty considered (16 < 20), glue to 26 untested, next box to 42 ends the float, so the
edge is the break *after* that line, which was laid out narrowed (x 372, y 98 to 114, below the foot at 92).

Measured on the way, a second defect at the same edge: with a 20pt heading line the float ends at the line
itself and the edge is the paragraph boundary, and the 10pt space is lost (the next paragraph at 92, not 102).
`addAreasForFloats` notified the SpaceResolver with the edge penalty as the end of the part
(`performConditionalsNotification`), so the conditional space was discarded as at a page break; the restarted
list begins at the next box and never sees the glue. The space-before of the paragraph after the float is lost
the same way: `FlowLayoutManager` resets the restarted block, `BlockLayoutManager.resetSpaces` clears what the
first list had resolved for it, and its leading space is resolved again as at the start of a page.

**Item 45.** A 40pt float outlasting a 14pt heading and its 10pt space ends after the table's second row
(14 + 10 + 14.9 + 14.9 = 53.8 ≥ 40). The re-layout from that break reaches `TableLayoutManager` through the
generic restart in `BlockStackingLayoutManager.getNextKnuthElements`, which pops the table's child off the
stack and asks `setCurrentChildLM` for it; the table's child iterator is exhausted and `LMiter.next` throws.
`TableLayoutManager.isRestartable()` is false, which the IPD-change restart honours
(`AbstractBreaker.containsNonRestartableLM`) and the float restart did not.

## 2. The change

1. `PageBreakingAlgorithm.considerLegalBreak`: while a float is in force, a legal break ends it if the content
   height before the break plus the glue up to the next box (`widthUpToNextBox`, as `forceNode` measures a
   node) reaches the float's height. That height is kept (`floatEdgeWidth`) and `deactivateNode` records it as
   the float's effective height, so the next list's accumulated height starts below the space. `handleBox`'s
   test stays, and the two agree where a line reaches the foot.
2. The same method: a break whose previous and next boxes lie inside the same non-restartable layout manager
   is not taken as the edge (`handlingEndOfFloat` is held off for that break and restored after). The edge
   waits for the first legal break after the table. The rows below the float keep the table's width, which
   the table set once for all its rows (`updateContentAreaIPDwithOverconstrainedAdjust`), so nothing is drawn
   differently from the re-layout at the row that FOP cannot do.
3. `PageBreaker.addAreasForFloats`: the SpaceResolver notification runs to the last glue before the next box,
   so the edge's break position is told it is no break and the space is resolved into the layout managers
   (the paragraph beside the float gets its space-after, the one below its space-before, the larger of the
   two as the resolver's rules give).
4. `LayoutContext.FLOAT_RESTART`: `PageBreaker.getNextBlockList` sets it on the breaker's context for a float
   restart; `FlowLayoutManager` passes it to the one block the list is read again from; that block keeps its
   resolved space-before (`BlockLayoutManager`) and emits none (`BlockStackingLayoutManager.addFirstVisibleMarks`,
   which clears the flag so the block's children do not inherit it). No signature changes.

Six files: `PageBreakingAlgorithm`, `PageBreaker`, `FlowLayoutManager`, `BlockStackingLayoutManager`,
`BlockLayoutManager`, `LayoutContext`, each with its change notice.

## 3. Measured on the command line (plain fork, no docx4j)

Glyph tops from `pdftotext -bbox`; the line box is 2.6pt above. Before and after:

| case | before | after |
|---|---|---|
| space-after 10pt (item 44) | first line x 372, line top 98 | x 72, top 98 |
| space-after 0pt (the overlapping line is narrowed, right) | x 372, top 88; next line x 72 | unchanged |
| 20pt heading line, space-after 10pt (edge at the boundary) | next paragraph at 92 | 102 |
| space-before 10pt on the next paragraph | 88 | 98 |
| space-after 6pt and space-before 4pt | 88 | 94 (the larger) |
| a paragraph before the heading, heading space-before 20pt, next space-before 10pt | heading 108, next 124 | heading 108, next 134 |
| 1,000 words after the float (page fill) | page 1's last line top 754, w641 | top 754, w651 (one line regained) |
| 40pt float into a five-row table (item 45) | `NoSuchElementException`, no PDF | five rows at the table's width from 96, "after" full width at 170.5 |

## 4. Tests

`fop/test/layoutengine/standard-testcases/float_side-edge-space.xml`, five page-sequences: the space-after case
(line full width, space kept), the space-before case, the edge at the paragraph boundary, the float into a
table (five rows, the paragraph after full width), and the no-space case (the overlapping line narrowed, as
before). It fails on `2.11-docx4j.5` without the change and passes with it; the full fop-core suite passes.

## 5. docx4j

No docx4j change is needed; the fix reaches docx4j's documents through the fork. The `FLOAT_RESTART` hand-off
is in `FlowLayoutManager.getNextKnuthElements(context, alignment, restartPosition, restartLM)`; if docx4j's
`WordFlowLayoutManager` overrides that method, the space-before half of item 44 (step 4 of §2) does not reach
its path, and the other three steps do. The docx4j session should say which. The gate: the `vml-box-beside`
probes and 561, 10855 with `textBesideBand` back on, against b167; rasters elsewhere unchanged.

## 6. Upstream

Two JIRAs, one per defect, drafted and not filed; posting needs Jason's OK on the text. Branch `FOP-####`
against `trunk` in `../fop-upstream-wt` once the numbers exist; the float code is the same on Apache `main`.

## 7. Not addressed

- A float anchored inside a table cell: the *start* of the float restarts inside the table and would fail the
  same way. Not measured; docx4j does not write it.
- `clear` on the block after a float, which would place a table below the float's foot rather than beside it.
  FOP ignores it on blocks; the docx4j session confirmed `clear="both"` makes no difference. With this change a
  table beside a float keeps its own width and may overlap the float; that is the FO's geometry, not a crash.
- 561's 393pt band leaving page 15's lines unnarrowed: a third shape, not yet reproduced here.
