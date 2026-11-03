# CR-034: split a floating table that crosses the page foot (item T)

- **Status:** BUILT 2026-11-04 on branch `CR-034-floating-table-split`, cut from `2.12-docx4j.1` at 59f249323 (§3.1
  as built, §3.2 measured: Word's three probes at Word's rows and lines through docx4j's path); handed to docx4j-12
  for the gate against b322 on r59 (§5). ORDERED 2026-11-04 (Jason, in the fork session: "`.1` waits for T. default
  on. In-row splits in version 1."; §6). Before that: probe done and design 2026-11-03, after Jason's order of B
  (fop/CR-032), A1 (fop/CR-033) and "a one-day probe for T" with a size.
- **Serves:** Enterprise CR-001 §6.6 item 8, the first renderer defect of 2026-11-03 for floating tables: case (a),
  a table that fits a page but crosses the foot, which Word splits and the fork now moves whole with its anchor
  (CR-033's interim); and case (b), a table taller than the page body less its offset, which the fork cuts at the
  foot with its rows past it lost and its offset dropped (6705; Word's `float-tall-beside`).
- **Capability:** `float-table-split` (new), so that docx4j can lift its step 2 guard against floating a table
  taller than a page.

## 1. Word's rule

From docx4j-12's two goldens (Enterprise item 8 at 377517c and bf44357):

- `float-foot-crossing` cases 1 and 2: a 240pt floating table at tblpY 0 or 1pt, anchored 504pt down a full page,
  **splits as it would in the flow**: rows 1 to 12 from the anchor's top on page 1, rows 13 to 15 at the next page's
  top. **Nothing is set beside the table on page 1.** The anchor paragraph begins on page 2 beside the continuation
  (after it, for the full-width table), and the text after runs full width below the table's end.
- `float-tall-beside`: a 60-row table (960pt) on A4 splits across pages and **keeps its offset** (row 1 12.4pt lower
  with tblpY 12.45 than with 0). Nothing beside it on page 1; the anchor and what follows start at the top of the
  next page beside the remaining rows, then full width below the table's end.
- `float-three-pages` (cut by docx4j-12 at Jason's word on 2026-11-03 to close the question below, docx4j ad51efa67;
  Word's 21 pages read by docx4j-12, Enterprise item 8 at 593b5b1): a floating table that splits more than once **sets
  its anchor on the table's last page**, beside the last rows at the body top (x 323.9, the table's right edge plus
  its 9pt distance), and **nothing is set beside the table on the first page or on any middle page**; the 200pt beside
  stay empty. The rows split as in the flow, the full body each page (100 rows at tblpY 0: 1 to 33, 34 to 76, 77 to
  100; 150 rows to a fourth page: 1 to 33, 34 to 76, 77 to 119, 120 to 150, the anchor on page 4). Every continuation
  opens at the body top. The anchor and the text after it run beside the last rows; the first line whose top is at or
  below the table's foot goes full width. **The offset is kept on page 1 only**: at tblpY 12pt row 1 is 12pt lower and
  page 1 holds 32 rows, pages 2 and 3 open at the body top. **A `w:tblHeader` row is repeated at the top of each
  continuation**, as in the flow, and the anchor's first line sits beside the repeated header. b322 (r59) scores the
  probe 0.8927 at 12 pages to Word's 21, each float moved whole and cut at the foot (CR-033's interim); it is in the
  share's probe set, so the gate reads it.
- So the two earlier goldens showed one rule, not two: their "next page" was also the table's last. The rule is: the
  table's parts fill whole pages with nothing beside them until the part that fits, and the anchor begins beside that
  last part.

## 2. The probe: a float's table adds its areas by position range

**Yes.** `FloatLayoutManager` builds its content's Knuth list once (`floatContentKnuthElements`, space-resolved) and
`processAreas` adds all of it through `floatContentLM.addAreas(new KnuthPossPosIter(list, 0, size), context)`, which
makes one `SideFloat` area. For the probe, `processAreas` was patched (not committed) to add two ranges, split at
the first legal penalty past half the height, each as its own `SideFloat`:

```java
floatContentLM.addAreas(new KnuthPossPosIter(floatContentKnuthElements, 0, split + 1), context);
floatContentLM.setTargetTop(firstTop + firstHeight + 20000);
floatContentLM.addAreas(new KnuthPossPosIter(floatContentKnuthElements, split + 1, size), context);
```

On `~/fop-session-tools/2026-11-03-float-defects/p1/offset.fo` (a 30-row table of 375pt floated at offset 5pt) the
table drew rows 1 to 16 as the first area, 200pt, and rows 17 to 30 as the second, 175pt, each a `SideFloat` with the
table's rows painted by `TableContentLayoutManager.addAreas` from the partial iterator (`t-probe/offset-split.pdf`,
README there). The table's list is a 12.5pt box (`NonLeafPosition`) and a 0 penalty (`SpaceHandlingBreakPosition`) per
row, 59 elements. So the table layout managers accept a range of the float's own list, as they accept a page's range
of the flow's list; the second area's placement in the probe is meaningless (the clamp of item 8's 1(b) pulled it up).

Not probed in the fork: a header row repeated at the split (`TableHFPenaltyPosition` as the range's last position is
the in-flow mechanism and should carry over; Word does repeat it, `float-three-pages` case 3, so the build must), a row
split inside itself (the stepper's row parts), and the borders the parts close with (the in-flow painter's
first/last-part flags come from the iterator, so they should hold).

## 3. Design

The breaker already does the two halves separately: CR-023's refusal (CR-033 for every offset) breaks the page
before the anchor and lays the anchor and its float at the next page's top with the anchor beside; and the float's
content adds areas by range (§2). T joins them.

1. **Where the split is decided.** `PageBreakingAlgorithm.handleBox`, at the anchor box, when the float's foot lies
   past the page's foot (`floatStartListBottom - pageStart.totalWidth > lineWidth`) and the float's content is a
   table (its content LM's first child a `TableLayoutManager`, as FOP-3331's width test already walks it): walk the
   float's list from its start, summing box and glue widths, to the last legal penalty whose height fits the room on
   this page below the float's top (`anchorTop + offset`, the offset kept as Word keeps it), and record the split
   index and the first part's height on the algorithm. At least one row must fit, else the whole table moves
   (CR-033). A float taller than an empty page (`floatFitsAnEmptyPage` false), which the refusal leaves alone
   today, takes the split too: that is case (b).
2. **Page 1: the refusal, plus the first part.** The page is too long at the float's start edge as in CR-033, so it
   breaks before the anchor; the restart (`restartFrom`) keeps the split state where it clears the float's start
   today. When the page's part is added (`PageBreaker.addAreasForFloats` for the part that ends at that break), the
   float's first range is drawn as a `SideFloat` at the flow's height plus the offset, with nothing beside it:
   `FloatLayoutManager.processAreas(context, 0, split)` with the target top. The part's content ends above it, so
   no intrusion is set for page 1.
3. **The next page: the same decision again, from the range's start.** The anchor box is handled again on the next
   page with the float's range now `[split, end)` and no offset (Word keeps the offset on page 1 only). If the
   remainder's foot lies past this page's foot too, §3.1 and §3.2 repeat: the page is refused at the float's start
   edge, breaks before the anchor, and the next range is drawn from the body top with nothing beside it, a middle
   page. The split state is a range start that advances, not a single index.
4. **The last page: the remainder with the anchor beside.** When the remaining range fits below the page's top, the
   float LMs process it: the `SideFloat` at the body top, the anchor's lines beside it, the intrusion's height the
   remainder's (`getFloatContentHeight` and `floatBottomOf` made range-aware). The rest is CR-023's and CR-020's
   end-edge path as today: the first line whose top is at or below the table's foot goes full width. This is Word's
   rule from `float-three-pages`: the anchor begins on the table's last page, whatever the count (§1). A header row
   repeats at each range's start by the in-flow mechanism (§2), which the layout test of §3.7 must show.
5. **Several floats on one anchor** (CR-023): split only when the anchor carries one float; two floats keep the
   move.
6. **Both modes**, as CR-032 and CR-033. **Capability `float-table-split`**, the thirtieth, read by docx4j to lift
   its step 2 guard against tables taller than a page; the guard against a float anchored beside an earlier float
   stays (CR-031 §11's open item).
7. **Tests:** layout test cases in `float_offset.xml` or a new `float_split.xml` (a table crossing the foot at offset
   0 and at 12pt; one taller than the page; one with a repeating header; a row that splits inside itself), and
   `TableBesideFloatTestCase` for the anchor beside the continuation.

### 3.1 As built (2026-11-04)

Built in one day on branch `CR-034-floating-table-split`. The mechanism differs from §3's sketch in one respect: the
page before the anchor is not made by the breaker's refusal and restart (§3.1, §3.2) but by taking the float's
**start edge before the anchor**, which the edge machinery already knows how to commit and restart from, and the
middle pages are made by the page breaker itself, not by the breaker handling the anchor again on each page. The
refusal could not have made a page that holds nothing but a table part: when the anchor opens its page there is no
break before it to restart from, and a middle page is exactly that shape.

1. **Arming** (`PageBreakingAlgorithm.handleBox`). At an anchor box with one float whose content is a table with a
   break to take (`FloatContentLayoutManager.contentSplittable`), and whose paragraph can be read again from its
   start (`splitRestartLM`: the flow's child the anchor's first line lies in, with no content of that child before
   the anchor in the list), the float is the split candidate. The foot is the anchor's top plus the offset plus the
   content still to be placed (`remainingContentHeight`, from the range start); "fits an empty page" now also holds
   when at least one row fits an empty page below the offset, so a table taller than a page is refused where before
   it was drawn cut. The last legal break seen before the anchor is remembered (`considerLegalBreak`).
2. **The edge** (`createForcedNodes`). Where CR-023's refusal fires for a page start, and a part of the table fits
   below the float's top on that page (`splitIndexFor(room)`), the edge node is made at the last legal break before
   the anchor, or at the page start itself when the anchor opens the page, and `splitEdge` is set; the node is still
   too long, so every page start dies at this break and `handleFloat` commits the edge. No restart of the breaker's
   own state is involved, which was the risk §4 named.
3. **The parts** (`PageBreaker.handleFloatLayout`, `addAreasForFloats`, `drawSplitParts`). The parts up to the edge
   are added. Then, on the edge's page, the table's first range is drawn as a `SideFloat` at the anchor's top plus
   the offset (`FloatContentLayoutManager.processRange`, `setTargetTop`), the intrusion it set is cleared and the
   page is marked non-empty; while what is left is taller than the next page's body, a new page is made
   (`makeNewPage`) and the next range drawn at its top; the page where the rest fits is left open with the float
   over (`handleEndOfFloat(0)`, offset 0) and the page provider told the next list starts there. The next list is
   the anchor's block read again from its start (`getNextBlockList` with the flow's child as the restart manager),
   on that page: the anchor box is handled again, the remaining range fits, and CR-023's path places it with the
   anchor's lines beside, the offset now 0 (`getFloatOffset` returns 0 once a range was placed).
4. **The state across the re-read.** A restart of the anchor's block makes new layout managers, the float's
   included, which would lay the table out afresh and duplicate it. The `FloatContentLayoutManager` that holds the
   table's element list, its range start and the table's own state (the header to repeat, the row painter) is kept
   on the `fo:float` FO (`Float.setLayoutContinuation`) and the new `FloatLayoutManager` adopts it
   (`getNextKnuthElements`); the last `processAreas` clears it.
5. **The split index** (`splitIndexFor`): from the range start, the last legal break (a penalty below INFINITE and
   not forced, or a glue after a box) up to which the boxes and glues, plus the penalty's own width (a repeated
   header and footer), fit the room, with at least one box of content before it. A row with `keep-together` has
   infinite penalties inside and moves whole; a row without them splits between its lines as the stepper laid it.
   A part ends with its break penalty included and the next begins at the first box after it, as the flow's parts
   do, which is what makes the table content manager repeat the header and close the borders.
6. **Not split, as before:** two floats on one anchor; a float whose content is no table (a picture, a text box;
   CR-033's move, or cut at the foot when taller than a page); an anchor inside a table or whose paragraph is not
   the first content of its flow child; a row taller than the page on its own (the rest is placed whole and cut).
   Multi-column bodies take the same path with pages for columns, unmeasured.

Files: `PageBreakingAlgorithm.java`, `PageBreaker.java`, `FloatContentLayoutManager.java`,
`inline/FloatLayoutManager.java`, `fo/flow/Float.java`, `docx4j/Docx4jFop.java`; layout test `float_split.xml`
(eight sequences: offset 0, offset 12pt, a header row, the anchor opening the flow, 100 rows over five pages, a row
split inside itself, a kept row, a float that is no table); `TableBesideFloatTestCase` (a 900pt table on A4 in both
modes); `Docx4jHooksTestCase` (30 capabilities).

### 3.2 Measured (2026-11-04)

**Minimal FOs** (`~/fop-session-tools/2026-11-03-float-defects/p1/`, FOP's command line, 300pt body, 12.5pt rows):

| case | before | now |
|---|---|---|
| `plain`: 30 rows anchored after two lines | 1 page, rows 27 to 30 lost | 2 pages: rows 1 to 22 from the anchor's top (row 1 at 75.6pt, the anchor's top at 74), nothing beside; rows 23 to 30 at page 2's top with "anchor text" beside at x 300 |
| `low-plain`: 15 rows after 18 lines | 2 pages, moved whole (CR-033) | rows 1 to 6 below the 18 lines, rows 7 to 15 at page 2's top with the anchor beside |
| 100 rows after two lines | | 5 pages, 22/24/24/24/6, the anchor beside row 95 on page 5 |
| 100 rows with a header row | | HEAD at the top of every page, 21/23/23/23/10 |
| 100 rows at offset 12pt | | 21 rows on page 1 (row 1 12pt lower), 24 on each middle page, the continuations at the top |
| 100 rows, the anchor the flow's first block | | 24/24/24/24/4, the edge at the list's start |

**Word's probes through docx4j's path** (`RenderDocx` on cp-b319 with the changed classes first, the gate's own
method, against the goldens with `t1/cmp.py`):

| probe | pages, fork / Word | rows per page | anchor, fork / Word |
|---|---|---|---|
| `float-three-pages` case 1 (100 rows) | 21 / 21 | 1 to 33, 34 to 76, 77 to 100, both | page 3 at y 74.9, x 323.3 / 75.0, 323.9 |
| case 2 (tblpY 12pt) | | 1 to 32, 33 to 75, 76 to 100, both; row 1 at 255.0 / 255.2 | 74.9, 323.3 / 75.0, 323.9 |
| case 3 (header row) | | header + 1 to 33, header + 34 to 75, header + 76 to 100, both | 74.9 / 75.0 |
| case 4 (150 rows) | | 33, 43, 43, 31, both | page 4, 74.9 / 75.0 |
| `float-tall-beside` cases 1, 2, 4 | 16 / 16 | 40/20, 39/21, 40/20, both | 74.9, 323.3 / 74.7, 323.9 |
| `float-tall-beside` case 3 (a wide table, 44pt beside) | | 39/21, both | Word sets the anchor's words in the 44pt sliver beside the last rows (8 lines at x 479); the fork sets the paragraph below the table at y 410.9: CR-031 §11's open sliver item, not this change's |
| `float-foot-crossing` cases 1, 2 | 13 / 13 | 1 to 12 on the anchor's page, 13 to 15 at the next page's top, both | 74.9, 323.3 / 74.7, 323.9 |
| `float-foot-crossing` cases 3 to 6 | | unchanged | unchanged |

Nothing is set beside the table on any first or middle page in either. Every row position agrees with Word's
within 0.5pt. Tests: the layout suite 785/0 before the new file, `float_split.xml` added, `TableBesideFloatTestCase`
8/8; CI's command as recorded in the status line.

## 4. Size

Five to eight working days, call it one to one and a half weeks:

| piece | days |
|---|---|
| the split decision and its state through the restart, repeated per page (§3.1 to §3.3) | 1 to 2 |
| the ranges' areas, target tops and the last part's intrusion height (§3.2, §3.4) | 1 to 2 |
| header repetition, in-row splits and borders at the split, with layout tests | 1 |
| measurement against `float-foot-crossing` cases 1 and 2, `float-tall-beside` and `float-three-pages` on docx4j's path | 1 |
| gates with docx4j-12 and the fixes they bring (6705 is the corpus case) | 1 to 2 |

The three-page golden (read 2026-11-03) does not change the size: a middle page is the page-1 shape repeated with an
advancing range start, and the anchor's page is the one where the decision of §3.1 first says the remainder fits.
It removes the version-1 cut that §3.4 had planned.

Risks: the breaker's restart path, which CR-033's refusal reuses and which mutates state the split must survive;
a page whose part ends with a float area but no intrusion and no anchor (new shape for `addAreasForFloats`, once per
middle page); a short anchor paragraph beside a tall last part (the end edge is CR-020's, so it should hold); and the
clamp in `FloatContentLayoutManager.addChildArea`, which must not pull a continuation up (item 8's 1(b) is fixed on
the way).

## 5. Pass criteria for the gate (docx4j-12, against b322 on r59)

- `float-foot-crossing` cases 1 and 2 at Word's lines: rows 1 to 12 on the first page, nothing beside, row 13 at the
  next page's top with the anchor beside it.
- `float-tall-beside` from 0.8743 toward 1.0: rows 1 to 45 on page 1 with the offset kept, the rest at page 2's top
  with the anchor beside, then full width.
- `float-three-pages` from 0.8927 (12 pages) to Word's 21 pages: rows 1 to 33, 34 to 76, 77 to 100 with nothing
  beside the first two parts and the anchor at the third page's top beside row 77; the 150-row case to a fourth page
  with the anchor there; the 12pt case with 32 rows on page 1 and the continuations at the body top; the header case
  with the header row at the top of each continuation and the anchor's first line beside it.
- 6705 under docx4j's step 2 (its first table about 661pt on a 628pt body) no longer losing 17 lines.
- 0 corpus movers away from Word; events equal or falling.

## 6. Decisions at the order (Jason, 2026-11-04)

1. **`2.12-docx4j.1` waits for T.** The release carries CR-032, CR-033 and T together; docx4j 17.3.6 follows it.
2. **Default on.** Every `fo:float` whose content is a table and whose foot crosses the page splits, in FOP's own
   layout and in Word mode, as CR-033's move applies to every float; no attribute opts in. It fires only where today's
   output is cut at the foot or moved whole. The capability `float-table-split` tells docx4j to lift its step 2 guard;
   it switches nothing in the fork.
3. **In-row splits in version 1.** A row that does not fit the room below the float's top on its page is split
   inside itself as the stepper splits it in the flow, unless its FO forbids it (`keep-together`, docx4j's
   `w:cantSplit`). No Word golden measures the in-row case; the between-rows cases of §1 are the measure, and the
   in-row case is pinned by a layout test on the flow's own behaviour.

## 7. Open

- A wide floating table leaving a sliver beside it: Word sets the anchor's words in the sliver beside the last
  rows, the fork sets the paragraph below the table (`float-tall-beside` case 3). CR-031 §11's open item, now
  reached on the table's last page where before the table was cut.
- An anchor inside a table cell, or a paragraph that is not the first content of its flow child, keeps CR-033's
  move: the block cannot be read again from its start. Two floats on one anchor likewise. Not met in the probes or
  the corpus so far.
- A single row taller than the page is placed whole and cut at the foot, as the whole table was. The clamp in
  `FloatContentLayoutManager.addChildArea` (item 8's 1(b)) still pulls such a float up by its offset; untouched,
  since every split part fits its page by construction.
- A floating table in a multi-column body: the parts take pages, not columns. Unmeasured.
- A continuation's anchor block keeps the space-before the list before resolved (`FLOAT_RESTART`, fop/CR-020); on
  Word's probes the anchor's line is at Word's y, so nothing to correct yet.
