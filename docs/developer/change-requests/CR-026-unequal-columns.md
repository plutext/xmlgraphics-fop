# CR-026: columns of unequal width in the region body

Status: SIZING 2026-10-08, not started; Jason's word to begin. Registry key `fop/CR-026`. Enterprise CR-001 §6.6
item 49, measured by the docx4j session on four corpus documents (10598, 6116, 1137, 11092). Sized from a reading of
the code, not yet from a probe: §5 names the one experiment that would firm the estimate.

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

**Phase B, balancing (three to five days).** With unequal widths the balanced height is not the content height over
the count: the content reflows per column. Balance by area: a first pass at the heights the equal rule gives, then
the restart path, then one correction from the measured surplus; or disable balancing for an unequal body and let the
last page fill column by column (Word balances a continuous section's unequal columns, so this is phase B's
measurement, not its default).

**Phase C, docx4j (its side).** Write the attributes for a section whose columns are unequal, gated on the
capability; stop sending such a stretch to the one-row table; `FopCapabilities`.

## 4. Size

| phase | fork | gate |
|---|---|---|
| A, the widths | about one week: the properties and area model two days, the breaker's per-column comparison and the renderers two, the layout tests and the suite one | the four documents and the `columns-unequal` probe on the share |
| B, balancing | three to five days | 6116 (nine sections) is the balancing case |

Total about two weeks, the first week giving the four documents their column origins and line breaks and the second
their last pages and section ends.

## 5. Before the estimate is trusted

One afternoon: on a branch, return the width difference from `compareIPDs` for two columns of a page whose `Span`
is given two widths by hand, and see the restart path lay the second column at its width without a new page. If
it does, phase A is as sized; if the path assumes a page boundary somewhere (`startPage`, `lastPageHasIPDChange`,
the last-page replacement in `PageBreaker`), phase A grows by the days it takes to teach it a column.

## 6. Upstream

Beyond XSL 1.1 (`column-count` and `column-gap` are the model). An extension Apache might take as `fox:`
properties, as it took `fox:disable-column-balancing`; to offer once it works, with the layout tests.
