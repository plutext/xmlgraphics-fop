# CR-026: columns of unequal width in the region body

Status: PHASE A DONE 2026-10-08, gated PASS (b211 on r24; b212 on r25, 0 movers) and merged to `2.11-docx4j.6` by
fast-forward, unreleased; phase B (balancing before a `span="all"` block, about a week, §3 and §5) not started,
Jason's ordering. Registry key `fop/CR-026`. Enterprise CR-001 §6.6 item 49, measured by the docx4j session on four
corpus documents (10598, 6116, 1137, 11092). §7 says what phase A built, §8 how it was gated.

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

One fix found by the gate's corpus reading (§8, 11126): a list read again after an inline-size change between
columns keeps its span (`AbstractBreaker.getNextBlockList` restores it with `LayoutContext.restoreSpan` after the
reset of the span signal, which had made the span the list before ended on current, so the flow reported a span
change on the first block read again, returned nothing, and the page breaker opened a new two-column span on the
page for every remaining block: thirty spans and as many body-overflow events on 11126). Layout test
`region-body_column-widths_span.xml` (seventy one-line blocks overflowing the first column, then a `span="all"`
block: 66 and 4 lines in the columns, the block on page 2, two spans on page 1). A guard that laid a list
restarted in a later column unbalanced instead of redoing it (`getStartingPartIndexForLastPage` −1, §5) was tried
and withdrawn: it was not the loop, and Apache's last-page redo relies on that path (`basic_link_to_last_page`).

Measured as the probe was (§5): the same figures through the attributes instead of the system property, the second
column at x=213.95pt in the PDF. Not changed: `BalancingColumnBreakingAlgorithm` (phase B); the IF and other renderers
inherit the stepping from `AbstractRenderer`. docx4j's layout managers use none of the members touched
(`docx4j-export-fo` reads `BodyRegion.getBPD()` only).

## 8. Gate

**r24 (872d45020), 2026-10-08: FAIL on 10598, by §4, in the docx4j session's path; not reproduced through FOP's command
line.** The docx4j session wrote the attributes (`fox:column-widths="125.45pt 360.8pt" fox:column-gaps="51.25pt"`, no
`columnWidthsIgnored`), the second column landed at Word's x, and the document went from 10 pages to 9 against Word's
7; its reading: the columns do not fill as Word's, page 3's second column empty but for one line. Measured here on
its PDF, Word's and a render of its FO through FOP's command line with the gate's fonts (Trebuchet MS, Cousine, Symbol,
Wingdings added to the config; `~/fop-session-tools/2026-10-08-cr026-probe/`), lines with text per column, split at
x=200: the command-line render is Word's page for page (p1 33/42 to Word's 32/54, p2 31/63 to 32/66 both ending
'OPManager', p3 32/52 to 32/65 with the same first and last words in every column, p4 39/71 to 39/75, p5 8/57 to 8/64,
p6 5/51 to 5/57, p7 0/10 to 0/16; the line counts differ by bullets and wrapping, the flow does not). The gate's PDF
agrees through page 2, then its page 3 has column 2 empty and the text block that should open it (break-before="column"
after the labels) opens page 4's narrow column instead, everything after shifted a column; its page 1 column 1 already
ends a line earlier than the command line's (foot 789.6 to 800.4). So the fault is in the gate's path: docx4j's own
layout managers (`WordLineLayoutManager` carries its own restart), the two-pass measured extents, or the user agent's
hooks; a bisect was asked of the docx4j session 2026-10-08 (stock managers; single pass; full path).

Two readings withdrawn on the way: "the labels carried into column 2 and column 2 overfilled" was the docx4j
session's count including blank lines plus this session's render without Trebuchet MS, which wraps more; and the
lines wider than column 1 in the area tree are empty inline-block parents (docx4j's `w:br` as an `fo:inline` holding
a `fo:block` with a newline), sized at the width the paragraph was first laid at and drawn as nothing. The mechanism
seen in the breaker's log, which the fix must respect: the page breaker's parts have no stretch, so every candidate
column break is "too short" (r=1000), the break is found by the forced path (`createForcedNodes`, `restartFrom`),
and `addNode` then diverts the restarted node, demerits zeroed, to `bestNodeForIPDChange`; that gives the right
break on the command line.

**Bisected by the docx4j session (2026-10-08): the fork is clear; the fault is docx4j's managers' height of the
column.** On r24 with the same FO: FOP's stock managers (`wordLayout=false`) give Word's columns (33/58, 31/78, 32/69,
39/81, 8/69 against Word's 32/58, 31/79, 32/70, 39/81, 8/70) and 8 pages; docx4j's `WordLayoutManagerMaker` managers
give 9 pages with page 3's column 2 empty, single pass or two; the measured extents are not it. Its breaker trace
(`~/fidelity-cr030/repro/column-widths-10598-breaker.log`, the page-3 cut `-p3.log`) and the stock area tree here show
the mechanism: page 3's first column is nearly full in every path, its lower half blank paragraphs down to the foot
with the forced break after the last of them; the stock managers' list for the column is 795229mpt against the
799600 column and fits, docx4j's is 808040, 8.4pt over, so the breaker's last too-short node (199, 796541) is before
the last blank line, that line is re-laid in column 2, and the forced break ends column 2, which is XSL's answer and
Word's once a line has spilled. The extra is at the column's head: the first line at y=76.1 in the gate's PDF, 63.8 in
Word's, 61.5 in the stock render; a space-before or leading glue kept at the head of a restarted list is the suspect
(FOP's `BlockLayoutManager` resets its spaces on a restart and keeps them only under the float-restart flag, CR-020).
Sent to the docx4j session 2026-10-08 to fix on its side; r24 stands for the gate.

**Measured further (2026-10-08): the head of page 3's column 1 holds the blank paragraph that spilled past column 2
of page 2, which FOP's stock restart loses and docx4j's managers keep.** At the page-2 boundary both paths break at
the last too-short node one blank line before the forced break (docx4j's node 230 at 792028 with the next at 803527
against the 799600 column; stock 141 at 788721, next 800220), both laying the text column about 11pt taller than
Word, where the blank fits. Reduced probes (`spill2-text.fo`, `spill2-blank.fo`, `pagewidth-*.fo` with the tools):
sixty one-line blocks fill column 1, one more block spills, then a block with `break-before="column"`. A text spill is
laid at the head of column 2 and the break honoured, the next block opening page 2's column 1 (XSL's answer); a blank
`<fo:block> </fo:block>` spill is dropped and the break consumed as the restarted list's start condition, the next
block opening column 2. The same at Apache's own page-width change with `break-before="page"`: the text spill gets a
page of its own, the blank spill vanishes. So Apache's changing-IPD restart loses a blank one-line block at the
restart, a defect that under equal columns never ran and under unequal columns runs at every column boundary;
docx4j's paragraph shape (a line's leading in a glue after the break possibility, not in the box) keeps the blank,
and its path consumes the break too, so the blank costs page 3's column 1 the 8.4pt that spills the labels' last
line. Neither path is Word's, whose shorter text column holds the blank. Not changed in the fork for now: keeping the
blank would be XSL-correct and would move the stock output away from Word on this document; it is Apache's own
behaviour, recorded here with the probes, for upstream if it matters. Asked of the docx4j session: which paragraph
carries each `w:br type="column"` (an empty paragraph holding the break mapped to an 11.5pt block is the mapping to
look at), and the line pitch that makes the column 11pt taller than Word's, §6.6 territory rather than columns.

**The docx4j session's answer (2026-10-08).** Six of the seven column breaks are paragraphs of their own (an empty
`w:p` whose only run holds the `w:br`), mapped as `break-before="column"` on the block whose own line opens the next
column, which is Word's placement of the mark; the blanks before each break are the author's paragraphs, so the
mapping stands. The list ends are identical in both paths, and the "11pt taller" reading above was the command-line
render's, not docx4j's: against Word's baselines its text lines run 1.0 to 1.5pt above Word's down the column, and
the overshoot (803.5 wanted of a 799.6 column, 3.9pt; stock 0.6pt) is in the three blank boxes after the last text
line, which Word fits above its foot and docx4j's path does not, by about 4pt. It takes the fidelity fix, finding
those 4pt on its side, over making its restart drop the blank as Apache's does; r24 stays the gate renderer.

**Where it stands (2026-10-08, evening).** The three blanks are the author's paragraphs (one with a 523 auto line
spacing, written as a 25pt line-height whose extra is droppable leading, one of spaces, one empty), the same three
11499 boxes in both paths' lists; the 1 to 4pt Word gives them past our column's foot (Word's last baseline at 792.0,
three 11.5pt pitches to 826.5 against a foot of 828, the last line's descent allowed past the margin, or the blanks
sized smaller) is docx4j's pitch question, §6.6 territory, open in its register. The word on the restart, this
session's: the fork keeps Apache's behaviour (the spilled blank dropped at an IPD restart) until the pitch is settled;
a change there is a fix of Apache's own defect and would go upstream first. The docx4j session is reading the whole
corpus on r24 meanwhile (b209 the control without the column code, b210 with the attributes written and the table
route off) so that Jason has the four documents and the probe beside the 10598 reading; its side stays uncommitted
until then.

**Corpus reading of phase A, b210 against b209 (the docx4j session, 2026-10-08; r24; docx4j writing widths on every
section with `w:col` widths and never tabling; b209 is HEAD without the column code, 0 movers against r23).** Gains:
10598 +203 lines (0.2128 to 0.7527, 10 pages to 9 against Word's 8), 394 +49 (0.48 to 0.76), 6116 +25 (0.64 to 0.71,
5 pages to 7: the span-block balancing, phase B). Losses: 11126 −73 (0.9560 to 0.1538, 1 page to 2, with 25
body-overflow events where there were none), 13753 −9 (from 1.0), 330 −4, 1432 −3 (from 1.0), 5639 −2 (1 page to 3),
2299 −1; the probe `columns-unequal` 0.7949 to 0.7692. Two kinds, both routed on docx4j's side (cand117, b211 against
b209): stretches that fit a page, which the one-row table had right and the renderer lays unbalanced until phase B
(13753, 1432); and near-equal columns within 5% (11126 at 5313 / 240 / 5219 twips; 5639 three and four columns),
Word's own rounding of equal ones, left to `column-count` as before. 11126's 25 overflow events and second page on
near-equal widths (265.65 / 12 / 260.95pt on 538.6pt) are the one item for the fork: a probe here of two tables
crossing that boundary (`neareq-table.fo` with the tools) paginates as equal columns do, with one warning (the table
continuing into the narrower column at its old width, the known limit, a 4.7pt overhang) and no overflow events, so
11126 carries something else; its FO and the event key are asked of the docx4j session (a table written at an
absolute width that no longer fits the narrower column is the guess).

**11126's loop, found and fixed (2026-10-08).** Its FO (`~/fidelity-cr030/repro/column-widths-11126.fo`) has a
two-column section whose content overflows the first column and then a `span="all"` block (the 480pt table). At the
restart in column 2 the breaker's reset of the span signal (`signalSpanChange(NOT_SET)`) made the span the list
before ended on, ALL, the current one, though the content read again is still in the columns; the flow then
reported a span change NONE on the first block read again and returned nothing, the breaker took NONE for the next
list's start and opened a new two-column span on the same page, and so on for every remaining block: thirty spans,
thirty body-overflow events, a second page. Reduced to `over-span-70.fo` (seventy one-line blocks over a 66-line
column, then a span="all" block; `fit-span.fo`, which fits the column, did not loop). Fixed by restoring the span of
the list before on a restart (`LayoutContext.restoreSpan`); a guard in `redoLayout` for a restart point before the
list's start (§5's −1) was tried first, was not the loop, failed Apache's `basic_link_to_last_page`, and is out:
11126 renders without events, the reduced case lays 66 and 4 lines and the block on page
2, 10598 is unchanged. Layout test `region-body_column-widths_span.xml`. The hook page-master-by-content was
suspected first (its warning differed between the equal and the widths run) and cleared by a render without it.

**Gate PASS on r24, b211 (the docx4j session, 2026-10-08), by §4 as far as phase A reaches.** cand117 (widths only
where the stretch stays in the flow and the columns differ by more than 5%, the one-row table kept where it fits a
page) against b209: 10598 +203 lines (0.2128 to 0.7527, 10 pages to 9 against Word's 8, its right column at x=210
as Word's), 6116 +14 (0.6366 to 0.6773, at Word's 5 pages), nothing else moved, probes and errors unchanged. 1137
and 11092 carry their widths (80.85 / 335.8pt and 174.95 / 208.25pt) and are unmoved: the balancing before their
span="all" blocks, phase B. Two limits in docx4j's rules §7: 10598's ninth page is the three blanks at the column
foot (3.9pt over, Word fits them; open on its side); and a run of continuous sections is one page-sequence with one
master set, so it carries one set of widths (6116's nine divisions get the one section's widths the sequence was
built from), a docx4j refinement to write them per part master when phase B makes it worth measuring. docx4j's side
is committed (5eb37c818, docs d98317526), gated on `column-widths`. r24 passed before the fix above; r25
carries it for a confirmation run (0 movers expected on the four, 11126 with the widths forced without events).

**Confirmed on r25, b212 (the docx4j session, 2026-10-08):** cand117 unchanged on r25 against b211 on r24: 0 movers
on the four corpora, probes and errors unchanged; 11126 with the widths forced: 0 region-body overflow events
against 25 on r24, its second page the documented limit (the 480pt table overhanging the 265pt column). Phase A
confirmed; the fork's baseline for later gates is b212 on r25. Merged to `2.11-docx4j.6` by fast-forward.

