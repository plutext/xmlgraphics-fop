# CR-020: a side float ends at the break before the first line below its foot, keeps the space there, and never ends inside a table

Status: IMPLEMENTED 2026-10-07 on branch `CR-020-float-edge-space`, gated PASS by the docx4j session (b170, and
b172 for the text-box band; §8), not merged. The branch was cut at 09c7bc857, the head of `CR-017-018-review`,
not of `2.11-docx4j.5` (still e0361ee62): the review session's commits were made on that branch, checked out in
the shared tree, as if on `.5`. So this branch also carries the review fixes and the hooks' warnings as events
(00cd6a23e, b467cdb37: CR-017 §13, CR-018 §10) and the CR-016 §7 and CR-019 documents, and a merge brings them
too. The fop-core suite passes on the branch (3853 tests, 0 failures; checkstyle clean), and the new layout test
fails without the change. The full CI build, re-run by the fork session on c38749555 before a merge: 3855 tests,
0 failures, checkstyle and spotbugs clean. Registry key `fop/CR-020`. A fix, not a hook, so upstream-bound: two
JIRA drafts, `docs/upstream/float-edge-after-space.txt` and `docs/upstream/float-edge-inside-table.txt`, not
filed. The fix changes nothing unless a side float's end falls at a space or inside a table. Capability
`side-float-edges`, added 2026-10-07 at the docx4j session's request: docx4j has no workaround on either item, but
its text-box band, which sets content beside a float, depends on both (without item 45's fix a table after the
band crashes FOP), so it gates the band on the capability rather than on the version.

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

No docx4j change is needed; the fix reaches docx4j's documents through the fork. The docx4j session checked its
managers against the diff (2026-10-07): `WordFlowLayoutManager` overrides only the two-argument
`getNextKnuthElements`, calling super, so the restart form and the `FLOAT_RESTART` hand-off are inherited;
`WordBlockLayoutManager` overrides only `initialize()` and `createNextChildLMs()`, so the `BlockLayoutManager` and
`BlockStackingLayoutManager` changes are inherited; `WordListItemLayoutManager` overrides the restart form but
calls super first; `WordLineLayoutManager` is a copy of `LineLayoutManager`, which this change does not touch (a
later revision touching it would need porting there). Enterprise CR-001 items 44 and 45 name this branch and
the drafts; 45 is marked as item 20 reproduced.

The gate (docx4j session): renderer r14 built from this branch at 850f8a8c5, a fop-core jar outside `~/.m2` at
`~/fop-renderers/r14-CR-020-850f8a8c5/docx4j-fo-renderer-core-2.11-docx4j.5-SNAPSHOT.jar` (sha256 9b4022960f1e...),
with `textBesideBand` back on, the corpora and probes against b169 on r13: the `vml-box-beside` probes, 561 and
10855 read; everything else checked for unchanged rasters.

## 6. Upstream

Two JIRAs, one per defect, drafted and not filed; posting needs Jason's OK on the text. Branch `FOP-####`
against `trunk` in `../fop-upstream-wt` once the numbers exist; the float code is the same on Apache `main`.

## 7. Not addressed

- A float anchored inside a table cell: the *start* of the float restarts inside the table and would fail the
  same way. Not measured; docx4j does not write it.
- `clear` on the block after a float, which would place a table below the float's foot rather than beside it.
  FOP ignores it on blocks; the docx4j session confirmed on r14 that `clear` on an `fo:table`, and on a block
  wrapped round it, changes nothing (rows at 106/121/136 against a 60pt float in four variants). With this
  change a table beside a float keeps its own width and may overlap the float; that is the FO's geometry, not
  a crash, and it is what 10855 now shows (gate b172, §8): its rubric table starts right below the heading at
  121.82 and is drawn over the text box, where Word puts it below the box at 155.35. **Follow-up requested by
  the docx4j session (low priority):** honour `clear` on a block-level FO after a side float, or never let a
  table share lines with a side float. The recommendation from here is `clear`: it is the XSL property for
  exactly this (§7.19.2, on the FO that must not sit beside the float), it leaves a table beside a float where
  the FO allows it, and it is the shape upstream can take; a "never beside" rule would be the fork's own. Its own
  CR when Jason takes it up; docx4j would then set `clear` on a table that follows a band and keep the band for
  10855, and until then makes no band where a table follows the anchor paragraph.
- 561's 393pt band leaving page 15's lines unnarrowed: a third shape, not yet reproduced here.

## 8. Gates (the docx4j session)

**b170, 2026-10-07, PASS on the fix alone.** r14 (this branch at 850f8a8c5, the jar of §5) against b169 on r13,
cand84 both sides, no text-box band: one document moves and nothing breaks.
- 5075, in real2 and real-c2 alike: 0.7660 to 0.9362. Its one float is a floating table's band. On r13 the EPS
  table after it went to page 2 (Word's page 1 has 42 lines; r13's had 27); on r14 page 1 has 43 lines and page
  2 has 5, Word's distribution. Its stray four-line page 3 is there as before.
- No other document moved in the four corpora, no probe moved, no render error on either renderer. The
  scoreboard reads line parity and pages, so "unchanged" is at that resolution, not a pixel compare.
- r14 also carries 00cd6a23e and b467cdb37 (the review fixes and the hooks' warnings as FOP events, CR-017 §13
  and CR-018 §10), which are on this branch's base, not on `2.11-docx4j.5` and not in r13 (3e5381d6f): nothing
  moved for them either.

**b172, 2026-10-07, the text-box band back on, on r14, against b170: the fix does what it should.** No render
error in any corpus (on r13 the band made 10855 throw). The six `vml-box-beside` probes match Word line for
line: each heading beside its box at 83.20 (Word 83.30), Pa's at x 388.00 (Word 388.70), and the paragraph after
each box no longer narrowed one line too many (Pa: Word's 4 lines; r13 had 5). 10855 at Word's 9 pages (from
10), its heading at Word's 81.38, but its rubric table drawn over the text box (above, §7): line parity 0.9693
to 0.9622. 561 is docx4j's: Figure C's box is positioned relative to a paragraph docx4j lays out near the foot of
page 14, so the box is drawn at y 1070, off the page, where Word keeps it on page 15 with the text beside it; a
positioned box needs a page-fit rule in docx4j first (page parity 0.9078 to 0.8656). So the band stays out of
docx4j for now, for 10855's `clear` (FOP) and 561's page fit (docx4j), and nothing on this branch changes for
this gate. Recorded on the docx4j side in the Enterprise register.
