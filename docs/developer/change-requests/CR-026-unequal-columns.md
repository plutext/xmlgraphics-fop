# CR-026: columns of unequal width in the region body

Status: PHASE A BUILT 2026-10-08, on branch `CR-026-unequal-columns` (Jason's word to start, 2026-10-08), not yet gated;
phase B (balancing) not started. Registry key `fop/CR-026`. Enterprise CR-001 §6.6 item 49, measured by the docx4j
session on four corpus documents (10598, 6116, 1137, 11092). §5's probe of the restart path was run first: phase A as
sized, phase B a week. §7 says what phase A built and measured.

## 1. The need

XSL-FO's `fo:region-body` lays every column at one width from `column-count` and `column-gap`. Word's sections with
`w:cols/@w:equalWidth="0"` give each column its own width and each gap its own; docx4j renders such a section as a
one-row table, which cannot flow across pages, and leaves a stretch longer than a page to the region body at equal
widths, where every column origin and line break is off (item 49's measurements: 10598 at 125.45 / 51.25 / 360.8pt
over eight pages, Word's 8 pages to ours 10, parity 0.21; 6116 at 0.64; 1137 and 11092 a page each).

Wanted, gated on a capability: `fox:column-widths="125.45pt 360.8pt"` and `fox:column-gaps="51.25pt"` on
`fo:region-body` (n widths, n−1 gaps, summing to the body's inline size), `column-count` kept so that Apache FOP falls
back to equal columns and docx4j keeps its table route there.

## 2. Where columns live in FOP

- `fo/pagination/RegionBody`: `column-count`, `column-gap`. `fox:disable-column-balancing` is the precedent for a
  `fox:` attribute here (`ExtensionElementMapping.PROPERTY_ATTRIBUTES`, `PR_X_...`, as `fox:float-offset` in CR-023).
- `area/BodyRegion` (count, gap, `getColumnIPD()` = one width), `area/MainReference.createSpan`, `area/Span`
  (`colWidth = (ipd − (n−1)·gap) / n`, one `NormalFlow` per column at that width; `getColumnWidth()`).
- The flow's inline size: `PageSequenceLayoutManager.getCurrentColumnWidth()` reads the current span's one width,
  less the side-float intrusions; every block's element list is built at it.
- Rendering: `AbstractRenderer.renderMainReference` steps `currentIPPosition` by `columnWidth + columnGap` per column
  (both directions); `XMLRenderer` writes `columnCount` and `columnGap` on the body region and the span, and
  `AreaTreeParser` reads them back; the IF renderer inherits the stepping.
- Breaking: the page breaker treats each column as a part. `PageProvider.getColumn(index)` maps a part to (page,
  column), `compareIPDs(index)` compares a part's inline size with the next part's and returns 0 for "a column on the
  same page"; a non-zero difference is FOP's changing-IPD path (`PageBreakingAlgorithm.ipdDifference`,
  `AbstractBreaker.doLayout` → `RestartAtLM`: add the areas up to the break, note the committed position, restart
  the element list from the layout manager at the break at the new inline size). That path is how FOP already lays a
  page sequence whose pages differ in width, and `PageProvider.setStartOfNextElementList(page, column, spanAll)`
  already restarts at a column.
- Balancing: `BalancingColumnBreakingAlgorithm` (the last page, and before a `span="all"` block) divides the content
  height by the column count, assuming equal widths.

## 3. The design

**Phase A, the widths (one week).** Two `fox:` properties on `fo:region-body`, parsed as lists of lengths (a
`StringProperty` split in `RegionBody.bind`, validated: n widths, n−1 gaps, their sum the content inline size within
a point, else ignored with a FOP event and the equal columns kept). `BodyRegion` and `Span` carry a width and a start
offset per column (`getColumnWidth(int)`, `getColumnStart(int)`; the one-argument forms stay for the equal case and
for docx4j's `inline-access` members). `PageSequenceLayoutManager.getCurrentColumnWidth()` reads the current
column's. `PageProvider.compareIPDs` returns the difference between consecutive columns' widths, so the changing-IPD
path reflows the remainder at the next column's width, column by column; `BodyRegion.getColumnIPD()` takes a column.
`AbstractRenderer` steps by each column's width and gap; `XMLRenderer` and `AreaTreeParser` carry the lists
(`columnWidths`, `columnGaps`), so layout tests can check them. Capability `column-widths`.

Known limits, FOP's own for a changing inline size: a layout manager that cannot restart (`isRestartable()` false:
tables, list blocks) at a column boundary keeps the previous column's width for its remainder, as it does across
pages today; footnotes and side floats on the same page as a width change take the float path first in
`doLayout` (`alg.handlingFloat()` is tested before `ipdChangesOnNextPage`), untested together. Word's own
column breaks (`break-before="column"` from `w:br w:type="column"`) already exist in FOP.

**Phase B, balancing (a week; §5 measured why).** With unequal widths the balanced height is not the content height
over the count: the content reflows per column, and the restart path commits the first column before the second is
laid, so FOP's `BalancingColumnBreakingAlgorithm` never sees the first column at all (§5). Balance by trial: a
candidate break in the first column, the remainder re-laid from it at the second column's width, the two heights
compared, a bounded search over the first column's lines (each step one restart), with the first column's areas added
only when the search ends. Word balances a continuous section's unequal columns, so this is phase B's measurement,
not its default; where docx4j writes a section change as a new page-sequence the last page is not balanced in FOP
today either (§5), and phase B serves only the span="all" case.

**Phase C, docx4j (its side).** Write the attributes for a section whose columns are unequal, gated on the
capability; stop sending such a stretch to the one-row table; `FopCapabilities`.

## 4. Size

| phase | fork | gate |
|---|---|---|
| A, the widths | about one week: the properties and area model two days, the breaker's per-column comparison and the renderers two, the layout tests and the suite one | the four documents and the `columns-unequal` probe on the share |
| B, balancing | about a week (trial restarts, §5) | 6116 (twenty sections, span="all" blocks throughout), then 1137 and 11092 at their span blocks |

Total about two weeks, the first week giving the four documents their column origins and line breaks and the second
their last pages and section ends.

## 5. The probe (2026-10-08)

§5 asked, before the estimate was trusted, for one afternoon on a branch: give a page's `Span` two widths by hand,
return their difference from `compareIPDs`, and see whether the restart path lays the second column at its width
without a new page. Run 2026-10-08 on `CR-026-unequal-columns`, cut from `2.11-docx4j.6` at d9df4ed82.

**What was changed.** `Span` takes a width per column from the system property `fop.probe.columnWidths` (points,
one per column; to be replaced by `fox:column-widths` in phase A), `getColumnWidth()` returns the current flow's
width and `getColumnWidth(int)` any column's. `PageProvider.compareIPDs` returns the difference between a column
and its neighbour on the page, and between a page's last column and the next page's first; under `span="all"` (the
span's column count differing from the body's) it keeps Apache's body-region comparison. Nothing else: the renderer
already steps by each flow's own inline size, the restart's `handleBreakTrait(EN_COLUMN)` already moves to the next
flow, and `updateLayoutContext` already reads the current flow's width through `getCurrentColumnWidth()`.

**Result: phase A is as sized.** The restart path lays the second column at its own width on the same page; nothing
in `startPage`, `lastPageHasIPDChange` or the last-page replacement assumes a page boundary. Measured with Helvetica
10pt on a 12pt line, a letter page with 37.25pt side margins (body 537.5pt), `column-count="2"`,
`column-gap="51.25pt"`, widths 125.45pt and 360.8pt (10598's), so the second column begins at x=213.95pt; the area
tree read per flow, the words of every flow checked continuous against the source (probe FOs and readers at
`~/fop-session-tools/2026-10-08-cr026-probe/`):

| FO | laid |
|---|---|
| `one-page` (eight paragraphs of sixty words) | column 1: 58 lines at ipd 125450; column 2: 16 lines at 360800, same page; in the PDF 58 lines at x=37.25 and 16 at x=213.95, right edge 573.56 of the body's 574.75 |
| `two-pages` (thirty paragraphs) | 58/54 on page 1, 58/44 on page 2, each column at its width on both pages |
| the same, widths reversed (360.8, 125.45) | 54/57 then 53/29: a negative difference restarts the same |
| `three` (81/58/336pt, 1137's, gap 20pt) | 58/59/50 then 54/24/0 |
| `table2` (two paragraphs, then a thirty-row table) | the table begins in column 1 (rows 1 to 11) and continues in column 2 (rows 12 to 30) at column 1's width: cell ipds 41316 and 83132 in both columns, the rows at x=214.2pt; the paragraphs after it at 360.8pt. §3's known limit, measured: a table is not restartable, so its remainder keeps the previous column's width. Equal control: rows 1 to 22 then 23 to 30, cells 80541 and 161582 throughout |

Checkstyle clean; `LayoutEngineTestSuite` 778 of 778. The first form of `compareIPDs` compared the current spans on
both sides of a page boundary and failed six of them (`page-sequence_two-column_last-page_7`, `_8`, `_9`,
`footnote_column_span`, `keep_within-page_multi-column_overflow`, `page-master-by-content_span-change`: an extra page,
or the wrong master on the last page), since under `span="all"` the current span is one column over a multi-column
body and Apache compares the body regions' column widths there. The span="all" guard above is the fix.

**Phase B is harder than sized.** `span-all` (a `span="all"` heading after nine paragraphs, balancing on): the equal
control balances the columns before the heading (30/29 lines) and the heading follows on page 1 with 25/25 below it.
With the probe widths column 1 is full (58 lines), column 2 holds the remainder (21), an empty full-width span is left
on page 1 and the heading opens page 2. That is exactly Apache's own output when `fox:disable-column-balancing="true"`
is on the heading (equal widths: 56/3, the empty span, the heading on page 2). The mechanism, from the breaker's
debug log: the IPD-change restart adds column 1's areas and notes the committed position before column 2 is laid,
and the restarted list begins in column 2, so `getStartingPartIndexForLastPage` returns -1 ("Restarting at -1") and
`BalancingColumnBreakingAlgorithm` sees column 2's content alone; nothing can pull column 1 up, the columns' span
keeps the page's full height, and the heading finds no room. So balancing by dividing the content height cannot
work for unequal columns; phase B must choose column 1's break by trial (§3), a week's work. Two things learnt on
the way: `fox:disable-column-balancing` is a property of the spanning block, not of `fo:region-body` (on the region
it is inert), and FOP does not balance the last page of a page-sequence at all without a `span="all"` block (the
equal control of `one-page` lays 52 lines in column 1 and none in column 2).

**Answered by the docx4j session (2026-10-08).** docx4j writes a run of continuous sections as one page-sequence,
at the run's largest column count, each narrower part wrapped in an `fo:block span="all"` (its
`ConversionSectionWrapperFactory`; rules §7); only a next-page section break starts a new page-sequence. So a
continuous change out of an unequal-column section is a `span="all"` block within the sequence, and phase B's
balancing applies at each. The four documents: 10598 is two next-page sections, its unequal section ending in a
next-page break, so phase A alone serves it; 6116 is twenty sections, nearly all continuous, two-column and
one-column alternating, span blocks throughout, phase B's measurement; 1137's unequal section is continuous into a
one-column one, and 11092's two unequal sections are continuous, span blocks each. Phase A gives 10598 its pages and
the other three their column origins; phase B settles their balancing at each span block.

## 6. Upstream

Beyond XSL 1.1 (`column-count` and `column-gap` are the model). An extension Apache might take as `fox:`
properties, as it took `fox:disable-column-balancing`; to offer once it works, with the layout tests.

## 7. Phase A as built (2026-10-08)

Capability `column-widths`, the twenty-fourth. `fox:column-widths` and `fox:column-gaps` on `fo:region-body`,
string properties (`PR_X_COLUMN_WIDTHS` 299, `PR_X_COLUMN_GAPS` 300) parsed in `RegionBody.bind` into millipoints
(space- or comma-separated lengths, any FOP unit): n widths for `column-count` n of at least 2, n−1 gaps, or no gaps,
each then `column-gap`; a width must be positive, a gap not negative. Their sum is checked against the body's content
inline size when a page is made (`Page` → `BodyRegion.resolveColumnWidths` → `RegionBody.resolveColumnWidths`), within
a point. Any failure fires `FOValidationEventProducer.columnWidthsIgnored` (WARN, once per region-body) and the columns
stay equal.

`BodyRegion` holds the lists (`setColumnWidths`, `getColumnWidth(int)`, `getColumnGap(int)`; `getColumnIPD()` is now
the first column's width, which is what `PageProvider`'s last-page comparisons want). `MainReference.createSpan` makes
a `Span(int[] widths, int[] gaps, int ipd)` for a multi-column span of such a body; `Span.getColumnWidth()` is the
current flow's width, `getColumnWidth(int)`, `getColumnGap(int)` and `getColumnStart(int)` any column's. The flow's
inline size reaches the layout managers through `PageSequenceLayoutManager.getCurrentColumnWidth()` unchanged.
`PageProvider.compareIPDs` returns the difference between a part's column and the next (the probe's form, §5).
`AbstractRenderer.renderMainReference` steps by each flow's inline size and the gap after it, in both directions;
`XMLRenderer` writes `columnWidths` and `columnGaps` on the body region and the span and `AreaTreeParser` reads them
back. Layout test `region-body_column-widths.xml` (five sequences: the two corpus widths on one page, three columns
over two pages, the gaps omitted, a bad sum and a bad count, with the warning's event checks).

Measured as the probe was (§5): the same figures through the attributes instead of the system property, the second
column at x=213.95pt in the PDF. Not changed: `BalancingColumnBreakingAlgorithm` (phase B); the IF and other renderers
inherit the stepping from `AbstractRenderer`. docx4j's layout managers use none of the members touched
(`docx4j-export-fo` reads `BodyRegion.getBPD()` only).
