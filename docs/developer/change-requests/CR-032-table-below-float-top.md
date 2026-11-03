# CR-032: a table that would cross a side float's top goes wholly below the float

- **Status:** built 2026-11-03 on branch `CR-032-table-below-float-top`, cut from `2.12-docx4j.1` at f57728c3e, code
  at 9b9f63f91; full suite 4092/0 (5 skipped), checkstyle and spotbugs clean under CI's command. Handed to docx4j-12
  the same day as r58 (`~/fop-renderers/r58-CR-032-9b9f63f91/`, core sha256 98070af9...) for a gate against b314 on
  r57. **Gated PASS 2026-11-03 (docx4j-12), §6, and MERGED to `2.12-docx4j.1` by fast-forward at 252b4e246;
  unreleased until `2.12-docx4j.1` ships. b321 on r58 is the fork's baseline for later gates.**
- **Serves:** Enterprise CR-001 §6.6 item 8, the second of the three renderer defects that blocked docx4j's step 2
  (floating wide tables) on r57 (gate b317, 2026-11-03). Ordered by Jason on 2026-11-03 ("please go ahead"), the first
  of three: this (B), then A1 (CR-023's move with the anchor for every side float that cannot split), then a probe for
  T (splitting a floating table).
- **Capability:** none new. docx4j gates its step 2 on `wide-block-below-float` (fop/CR-031 §11), whose rule this
  carries to the float's start edge. A separate name can be added if docx4j needs to tell the two apart.
- **Modes:** FOP's own layout and Word mode alike, as CR-023 §4.2, CR-024 §11.20 and CR-031 §11 are.

## 1. Word's rule

docx4j-12's probe `float-top-crossing-table` (docx4j 99ac75955; Jason's golden, read here with mutool and agreed
with docx4j-12 on 2026-11-03; recorded in Enterprise CR-001 §6.6 item 8 at 377517c). A 150pt-wide floating table at
the right margin, anchored on a one-line paragraph with `tblpY` 24 to 54pt, and a six-row table straight after the
anchor, full width in cases 1, 2 and 4 and about 300pt in case 3.

- **In all four cases Word sets the whole table below the float**, at x 72, the margin, starting at the float's foot.
  That includes row 1, which in cases 1 to 3 would fit above the float's top.
- A keep-with-next heading between the anchor and the table (case 2) stays where it is, directly after the anchor
  and full width; the table alone goes below.
- The paragraph after the table runs full width after it.
- The rule is the one Word applies to a table beside a floating table (`float-gap-tables`, CR-031 §11): a table that
  does not fit the room beside the float goes below it. Here the table's first rows lie above the float's top, and
  Word still sends the whole table below.

Not measured: a table that fits the room beside the float and crosses its top. CR-031 §11's rule says such a table
goes beside; whether Word starts it beside the float from its first row, or above the top and narrows it at the
crossing row, the probe does not say.

## 2. What the fork did, and why

The fork drew the probe's four tables full width under the float (b319), and corpus 4083's page 2 (B.6's heading,
keep-with-next, then a 358.5pt date table crossing the options float's top, offset 47pt) the same; with no keep,
the minimal FOs under `~/fop-session-tools/2026-11-03-float-defects/p2/` put the table beside the float, 358pt in
a 95pt room, at x 451.

The mechanism is CR-023's pending intrusion. An offset float is placed when its anchor's part is added; the lines
after the anchor are read full width, and the breaking algorithm takes the float's **start edge** at the first legal
break after which a line's text would cross the float's top, or after which a table (a layout manager that cannot
be read again from inside, CR-020) would reach the top with its elements (`PageBreakingAlgorithm.considerLegalBreak`,
`startPendingFloat`). `PageBreaker.resolvePendingIntrusion` then switches the intrusion on, and the next list is
read beside the float from the edge.

- **Without a keep** the edge is the break before the table, and the table is laid in the room beside the float.
  CR-031 §11's rule (`overflowsBesideFloatAtNextBox`, a block whose FO fixes a width greater than the room ends the
  float before it) is tested at the legal breaks of the list read beside the float, and the table is that list's
  first box: no break precedes it there, so the rule never saw it. The table ran past the margin.
- **With a keep-with-next** on the block before the table, the penalty between them is infinite and of class
  `line`: `Keep.getKeep` applies the within-page, within-column and within-line components in that order, so the
  `always` shorthand leaves the context at `line`. `handlePenaltyAt` offers such a penalty as a start edge only when
  the next line's text crosses the top (CR-024 §11.4's rule for an orphan-kept boundary). A table whose first row
  lies above the top has no line crossing it, so no edge was offered before the table; inside the table none can be
  (CR-020); and the table was set full width over the float, the intrusion starting at the first line after it.
  Where the table's first row did cross the top (`k14`), the edge was taken through the keep already and the table
  went beside, as without the keep.

Beside an obstacle's band (hook `obstacles-in-the-line`) the start-edge case was built in CR-024 §11.20:
`PageBreaker.continueBesideObstacles` clears the column for a first box after the edge whose fixed width exceeds the
room. `fo:float` had no such clearance (CR-031 §11's open item on the anchor's first word says so).

## 3. As built

1. **A keep-with-next before a table that would reach the float's top is a start edge.**
   `PageBreakingAlgorithm.handlePenaltyAt`: an infinite penalty of class `line` with a float's top pending is
   offered to `considerLegalBreak` when the next line's text crosses the top (as before) **or** when a table starting
   after the break would reach it (`nonRestartableReaches`, the test `considerLegalBreak` already made, factored
   out). The heading before the break stays where it is; the keep is overridden as a word processor overrides it.
   This holds for an obstacle's band as well as for a float, since both use the pending top.
2. **At the start edge, a first box wider than the room goes below the float.** `PageBreaker.resolvePendingIntrusion`:
   when the first box after the edge fixes an inline size greater than the room beside the float (the column less the
   float's saved intrusions; `firstBoxAfterEdgeFixedIPD`, `fixedIPDOf`: a table's width or the sum of its fixed
   columns, a block-container's width), the intrusion is not switched on. The content after the edge starts at the
   float's foot (the clearance from the edge to the foot, as after an edge forced by `clear`, CR-022), and the float
   is over, with nothing beside it. A paragraph fixes no width and is never wider: the paragraph probes `para40` and
   `anchor40` render byte for byte as before.
3. **`firstBoxAfterEdgeFixedIPD` reads past an auxiliary box.** The space resolver puts a box with no position
   between a break and a table's border-before after a space-after; it has a width, so the first-box read stopped
   at it and found no fixed size. CR-031 §11 fixed the same thing for the end edge
   (`positionOfNextNonAuxiliaryBox`). The read now skips an auxiliary box. By the code this also reaches CR-024
   §11.20's clearance beside an obstacle, where a table after a paragraph with space-after was read as fixing no
   width; not measured on a document.

A table that fits the room beside the float is unchanged: the edge is taken before it and it is laid beside the
float from its first row, as before this change (unmeasured against Word, §1).

Files: `PageBreakingAlgorithm.java`, `PageBreaker.java`, `TableBesideFloatTestCase.java` (two cases added, Word mode
and FOP's own: the table below with a 116pt clearance; through a keep, the heading full width above and the table
below with a 100pt clearance).

## 4. Measured here (2026-11-03)

**The minimal FOs** (`p2/`, FOP's command line, FOP mode, base-14 fonts; a 595pt page with 50pt margins, a left
float of 400pt by 60pt, the room 95pt, a 358pt table of two 17pt rows after a one-line anchor; `g` without a keep,
`k` with a keep-with-next heading, the number the offset in points). Row one's glyph top, x, and the paragraph after:

| case | before | after |
|---|---|---|
| g12 | row 1 beside the float (x 451) | row 1 at y 136.5, x 50.2 (the float's foot 135.5); "after one" 170.7 full width |
| g14 | row 1 beside, x 451 | row 1 at 138.5, x 50.2; after 172.7 |
| g20 | beside, x 451 | 144.5, x 50.2; after 178.7 |
| g30 | beside, x 451 | 154.5, x 50.2; after 188.7 |
| k14 | beside, x 451 (the first row crossed the top, so the keep was overridden already) | 138.5, x 50.2; heading at 63.1 unchanged |
| k30 | full width over the float, x 50.2 at y 75.5, "after one" beside the float at x 451 | 154.5, x 50.2; heading unchanged; after 188.7 full width |
| para40, anchor40 | a paragraph crossing the top: full width above, narrowed from the crossing line | identical (pdftotext md5 equal) |

In every table case the float's foot is the anchor block's top plus the offset plus 60.5pt, and row 1 starts there.
No warnings.

**Word's probe**, through docx4j's own path (`render.sh` with the changed classes as an overlay on b319's
classpath, docx4j's FO of 99ac75955, one pass). Glyph tops, the fork against Word's golden:

| | fork | Word |
|---|---|---|
| case 1: T1 row 1 | 228.4, x 77.6 | 229.1, x 77.8 |
| case 1: P03 after the table | 348.2 | 349.0 |
| case 2: heading, kept with the table | 138.3, full width | 138.4 |
| case 2: T2 row 1 | 242.4 | 243.2 |
| case 2: P10 after | 362.2 | 363.2 |
| case 3: T3 row 1 | 228.4 | 229.1 |
| case 4: T4 row 1 | 212.4 | 213.3 |
| case 4: P22 after | 332.2 | 333.2 |

Every case at Word's lines within 1pt, four pages as Word's; b319 had every table full width under the float.

## 5. Pass criteria for the gate (docx4j-12, against b314 on r57)

- `float-top-crossing-table`: the four tables below their floats at Word's lines, the case 2 heading in place; the
  probe at or near 1.0.
- 4083, page 2: the date table below the options float at the margin; B.7's heading no longer in the 85pt room at
  the page's foot. Pages 3 and 4 of 4083 are untraced (item 8) and may move either way; read, not assumed.
- No corpus document losing lines; events equal or falling. docx4j writes `fox:float-offset` on floating tables,
  text-box bands, pictures and frames (CR-032 phase 2), and a table after any of them that crosses the offset's top
  and is wider than the room moves below. Each such mover is to be read against Word.
- The b319 FOs of 9832, 1616, 6705, 8236 and 3229 are docx4j's step 2 question, not this gate's.

## 6. The gate (docx4j-12, 2026-11-03)

Run against a fresh control rather than b314: the harness had taken on the three float probes of 2026-11-03, and
b319 (r57 plus docx4j's 937 fix) was the current baseline.

- **b320, the control:** cp-b319 (r57) with the harness's current classes. Equal to b319, 0 movers, events equal.
- **b321, r58's core on cp-b319, against b320: PASS.** One mover, the probe `float-top-crossing-table`, +18 lines,
  0.9006 to 1.0000, four pages as Word's, page parity 1.0000. 0 corpus movers. Events equal (line 8380, clip 1552,
  bcov 1276, region 73, body 18, glyph 63). 29 hooks; docx4j-12 needs no separate name for step 2,
  `wide-block-below-float` being the test it reads.
- Unchanged, and the baselines for A1 and T: `float-tall-beside` 0.8743 (13 pages against Word's 16),
  `float-foot-crossing` 0.9620.
- 4083's date table arises only under step 2's FO, which this gate does not carry; docx4j-12 reads it when step 2 is
  gated again.
- Recorded in the Enterprise register (attempts.md and causes/floating-table-offset-and-sides.md, 315789f). Script
  `~/fidelity-cr030/gate-b320-b321.sh`, classpath `~/fidelity-cr030/cp-b321.txt`.

Merged to `2.12-docx4j.1` by fast-forward at 252b4e246 on the pass. **b321 on r58 is the fork's baseline for later
gates.**

## 7. Open

- A table that fits the room and crosses the float's top: set beside the float from its first row, which is FOP's
  behaviour before this change; Word unmeasured (§1).
- A keep-with-next before a wide table beside a float whose intrusion is already on (an offset of 0, or within the
  anchor's first line): the end edge through a keep is offered beside an obstacle only (CR-031 §10's
  `handlePenaltyAt` branch asks `handlingObstacle()`), not beside an `fo:float`, so such a table stays beside the
  float. Not in Word's probe; not measured.
- CR-031 §11's open items stand: the anchor's first word in a sliver; a second floating table beside a first; a
  float taller than the page (item T).
