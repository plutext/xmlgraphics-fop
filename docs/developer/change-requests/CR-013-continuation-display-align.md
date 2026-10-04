# CR-013: the display-align of a table cell's continuation parts

Status: DONE 2026-10-04, gated PASS by the docx4j session (below) and merged to `2.11-docx4j.5`; unreleased. Registry key `fop/CR-013`. Capability `continuation-display-align`. A docx4j hook (Word compatibility,
not an XSL defect), so not upstream-bound.

The docx4j session's batch 51 (probe table-rowsplit-3, variant F), taken to the fork at Jason's word on
2026-10-04; to be Enterprise CR-001 §6.6 item 39. When Word breaks a table cell across pages, it sets the
cell's later part from the top whatever its vertical alignment. FOP applies `display-align` to every part.
In the probe:
- the row's cells are all `w:vAlign` bottom (`display-align="after"`) and break 1+1, 1+1 and 1+2;
- Word sets every continuation at y = 86.53 on the next page;
- FOP sets the one-line continuations level with the three-line cell's last line, 14pt lower.

A cell's first part keeps its alignment in Word as well (variant F2 is identical on both sides). docx4j
measured the cost at 1 or 2 lines per split boundary in bottom- or centre-aligned tables, 37 boundaries in
corpus document 11657.

FO cannot express it: `display-align` applies to every area a cell generates.

## The change

- **The property.** `fox:continuation-display-align` on `fo:table-cell` (`PR_X_CONTINUATION_DISPLAY_ALIGN =
  297`, `PROPERTY_COUNT` 297). Values `before`, `center`, `after` and `auto`; `auto`, the default, keeps
  `display-align`. Not inherited. Registered in `ExtensionElementMapping`, like `fox:gsub-features`.
- **The cell.** `TableCell.getContinuationDisplayAlign()`.
- **The layout.** `TableCellLayoutManager.addAreas` uses it, where it is not `auto`, for a part whose
  before-border is `ConditionalBorder.REST`, which `RowPainter` sets exactly when the part does not begin
  the cell (`!CellPart.isFirstPart()`). The header and footer re-layout path for `retrieve-table-marker` is
  unchanged.
- **The capability.** `Docx4jFop.CONTINUATION_DISPLAY_ALIGN = "continuation-display-align"`, the twelfth.

Absent the attribute, nothing changes: every part takes `display-align` as before.

## Test

`table-cell_continuation-display-align.xml`: two `display-align="after"` cells break across pages, 1+1 and
1+2, once without the extension and once with `before`. Without it, the first cell's continuation sits below
an empty 12pt block. With it, the continuation is the cell's first block. The first parts are the same either
way. The test fails without the layout change.

Measured on the command line, same FO: the one-line continuation "two" is at y = 23.88 on the second page
without the extension and at 11.88 with it, level with the other cell's first continuation line. The first
page is unchanged.

## What docx4j does with it

With `continuation-display-align` present, docx4j writes `fox:continuation-display-align="before"` on cells
whose `w:vAlign` is center or bottom.

## The docx4j gate, 2026-10-04: PASS

Run by the docx4j session on Jason's install of 49e805835, copied aside as r6. The CR-012 snapshot (r5) was
the base, so this CR is isolated. Coverage: the three corpora, real-c2 and the probes, with the Windows VM's
faces. docx4j's side writes `fox:continuation-display-align="before"` on cells with `display-align` center
or after when the capability is present.
- **Base, r5 against `.4`:** only the documents numbering a page 0 move, all up: 13347 +10 lines, 2189 +2,
  6083 +18, 4899 +71, 6251 +3. That is CR-012, as its own gate found, with 2189 newly moving.
- **Control, r6 without the attribute:** 0 movers against r5.
- **Measurement, r6 with the attribute:** 10 movers, none down.
  - 13102 +12 lines, and its real-c2 copy +11;
  - 11741 +4, 11657 +3, 3310 +1, 4025 +1;
  - the four table-rowsplit-3 probe modes (compat 11, 11 for 11657, 12, 14): 0.998 to 1.0000.

