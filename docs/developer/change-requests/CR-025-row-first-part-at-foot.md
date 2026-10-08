# CR-025: a table row's first part at a page's foot has room for the margin it gives up

Status: IN PROGRESS 2026-10-08, on branch `CR-025-row-first-part` off `2.11-docx4j.6` (after `fop/CR-023`, 3339fc1cf);
measured on the command line (§4), the suite running, the docx4j gate pending (§8). Registry key `fop/CR-025`.
Hook `row-first-part-room` (`FOUserAgent.setRowFirstPartRoom`, off by default), the twenty-third capability. Enterprise CR-001 §6.6 item 48. Found by the docx4j session on
corpus document 11657 (2026-10-08, gates b205 and b206), reproduced here with a probe on the fork and on Apache
`main` 5be8c69b6. Started on Jason's word ("let's do this row rule now").

## 1. Word's rule

Word refuses to start a table row at a page's foot when the row's first line would fit only without the cell's
bottom margin, and moves the whole row to the next page; where the first line fits with the margin, it starts the
row and may split it after that line, giving the margin up at the split. Both sides are on one document (the docx4j
session's reading of 11657, 2393 one-line rows with 28-twip cell margins and a 0.5pt border, a body foot at 549.8pt):
row 34 on page 29, a multi-line row whose first line ends at 547.3pt with the margin and border, is started and split
after line one; row 54 on page 30, whose first line reaches 550.1pt with the margin, is moved whole. So the condition
is on starting the row's first part, "the first line plus padding-after and border-after fit", and not on the
split's end: retaining the padding-after at every split (`padding-after.conditionality="retain"`) is not the rule,
and cost 55 lines on 11657 and 8 on 12363 when tried (gate b206). docx4j writes `padding-before.conditionality=
"retain"` on every cell, which is Word's for a continued row (gate b205), and `padding-after` without.

## 2. FOP today

`TableStepper` turns a row group into steps, each a `KnuthBox` of the height added since the last step and a
`BreakElement` after it; a step's height for a cell (`ActiveCell`) is the before border and padding, the content up to
the next legal break, and `bpAfterTrailing`, the padding and border kept at a split, where the padding is the
conditional `padding-after` (discarded by default) and the border the `REST` one. So a row's first part is measured
without its padding-after, and the page breaker starts the row wherever the first line alone fits. Measured with the
probe of §4: twelve one-line rows fill 232.56pt; a thirteenth row's first line ends at 250.25pt and with its
padding-after and border-after at 251.94pt; on a body of 251pt FOP starts a three-line row and splits it after the
first line, where Word moves it whole. Apache `main` at 5be8c69b6 does the same.

## 3. The change

A break's width counts only when the break is taken there (`BreakingAlgorithm.computeDifference` adds a penalty's
width). So the first `BreakElement` inside a row (`TableStepper`, `firstBreakInRow`, set at the row group's start
and in `switchToNextRow`, cleared after the first break element of the row, and only while the row is not finished)
is given the largest, over the row's active cells, of what the cell would give up at a split:
`ActiveCell.getAfterSpaceGivenUpAtSplit()`, the normal after padding and border (`bpAfterNormal`) less the trailing
ones (`bpAfterTrailing`). A page break at that element then needs the first line plus the margin to fit; a break
anywhere else is as before, the row's later parts and its end unchanged, and the first part, when the row is split
there, is still drawn without the margin, which is Word's drawing too. A row with one line, or one its widows and
orphans keep from splitting, has no break inside it and needed its whole height already.

Off by default, a hook: `FOUserAgent.setRowFirstPartRoom(true)` turns it on, and `TableStepper` reads it once per row
group through the table's user agent. On by default it failed Apache's `table_empty-cells.xml`, whose element-list
check expects a penalty of width 0 after a row's first step where the rule gives the cell's 1pt border; the rule is a
word processor's, not XSL's, so FOP's own behaviour stays and docx4j asks for it. Four files: `FOUserAgent`,
`TableStepper` and `ActiveCell` in `layoutmgr/table`, and the capability in `Docx4jFop`. docx4j writes nothing in the
FO; it sets the switch on its user agent when `FopCapabilities` reports the hook.

## 4. Measured on the command line (plain fork, no docx4j)

A probe (`table_row-first-part-at-foot.xml`'s FO): twelve one-line rows (a 16pt line, 1.44pt of padding before and
after, a 0.5pt collapsed border; 19.38pt a row, 232.56pt), then a three-line row (orphans and widows 1, so it may
split after its first line) or a one-line row, on bodies from 250.4 to 253pt. The thirteenth row's first line ends
at 250.25pt; with the padding-after and the border-after at 251.94pt.

| body | three-line row, before | after | one-line row, before and after |
|---|---|---|---|
| 250.4 to 251.9 | started, split after line one (page 1 ends at 250.25) | moved whole (page 1 ends at 232.56, the row opens page 2) | moved whole |
| 252 and 253 | started, split after line one | the same | on the page (251.94) |

Apache `main` 5be8c69b6 (the worktree's jar): the "before" column.

## 5. Tests

- `RowFirstPartRoomTestCase` (`layoutmgr/table`): the probe as a Java test, each case rendered with the switch off
  and on (a layout-engine test case cannot set the user agent); three tests, the first run of the rule on by default
  having failed `table_empty-cells.xml`.
- `Docx4jHooksTestCase`: twenty-three capabilities.
- The full `fop-core` suite and checkstyle: see Status.

## 6. docx4j

`FOUserAgent.setRowFirstPartRoom(true)` where the user agent is made, gated on the hook; nothing in the FO. A pass: 11657's page 30 ending at row 53 with row 54 opening page 31 whole, row 34 still split after
its first line, and the document's line count toward Word's; 12363 unmoved; nothing moving on documents whose rows
do not reach a page's foot within a margin's height.

## 7. Upstream

A rule beyond XSL, which says a discarded conditional space is not there at a break and nothing about the room a
row's first part must have. It is a word processor's rule, the fork's; not sent.

## 8. Gate (the docx4j session)

Pending.
