# CR-034: split a floating table that crosses the page foot (item T)

- **Status:** PROBE DONE and DESIGN, 2026-11-03; not ordered, not built. Jason's order of 2026-11-03 was B
  (fop/CR-032), A1 (fop/CR-033), then "a one-day probe for T" and a size for him. This document is the probe's
  result and the size.
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
