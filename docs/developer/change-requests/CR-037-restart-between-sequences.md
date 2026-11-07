# CR-037: a paragraph of several sequences read again from a break between them

- **Status:** BUILT 2026-11-05 on branch `CR-037-restart-at-soft-return`, cut from `2.12-docx4j.1` at 69b8b2993, code a260b3bd3, drop r69.
  **Gated PASS 2026-11-05 by docx4j-67 (b362 on r69 against b360 on r68, §6) and MERGED to `2.12-docx4j.1` by fast-forward; RELEASED in `2.12-docx4j.1` (Maven Central 2026-11-07, version commit c69e89ffb).**
  Ordered 2026-11-05 by Jason through docx4j-67 ("take the doubled w:br paragraph (item 56)").
- **Serves:** Enterprise CR-001 §6.6 item 56: a paragraph with `w:br` line breaks is drawn twice where a float's edge falls inside it.
- **Capability:** none. A fix, not a hook.

## 1. The defect

A paragraph that a preserved linefeed or a nested block divides is several sequences in `LineLayoutManager.knuthParagraphs`, and docx4j
writes each `w:br` as a nested block. Read again from a break between two of them, it started over from its first. Word's
`float-foot-edges` series B showed it (M1 and M2 on page 1, then M1 to M4 on page 2), Word drawing each line once.

It is wider than that series. A sweep of 228 plain FOs through stock managers, a restart at each boundary of four paragraph forms,
`~/fop-session-tools/2026-11-05-cr037-restart/` (`sweep.txt`), every token to appear once and in order:

| Restart | r68 right | r69 right |
|---|---|---|
| a plain float's end edge | 19 of 76 | 76 of 76 |
| an offset float's start edge | 0 of 48 | 48 of 48 |
| an obstacle's band | 0 of 56 | 56 of 56 |
| a change of page width | 16 of 48 | 44 of 48 |

At a change of page width a line was **lost** with no event: `T01 T02 T03 T04 T06`. Elsewhere lines were repeated. No case right on r68
goes wrong on r69, and no line of r69 overlaps another or passes the foot.

**Apache's too.** On Apache `main` (1301bcf74 with this repository's one text-indent commit), the same FOs for the end edge and the
change of width: the same repeated and lost lines, and on 27 of the 124 FOs a `NullPointerException` in place of a PDF. Not sent upstream.

## 2. The mechanism

Traced by docx4j-67 on r68. The break between sequences carried a bare `Position(this)`, index -1. `BlockLayoutManager.getNextChildElements`
hands a line manager a restart only if it is a `LeafPosition`, and otherwise null, so the line manager read from sequence 0. The `self`
branch of `PageBreaker.handleFloatLayout`, whose comment said the manager "reads itself again from the break", did not do that. At a change of
width, `RestartAtLM` built `new LeafPosition(surroundingLM, positionIndex + 1)`, the index in the block list where the line manager reads a
sequence's: so sequences were dropped.

## 3. The change

- `LineLayoutManager.positionBetweenSequences(p)`: those breaks carry `new LeafPosition(this, p, 0)`, the start of sequence p, the form the
  restart reads, as the breaks after a line already do. All three such breaks in `postProcessLineBreaks`.
- The restart reads past the first box, so a nested block's sequence of one box looked emptied and was dropped: it is now dropped only if
  it is an inline sequence or truly empty. docx4j-67's finding: without this the fix alone loses a line.
- Under `legacy-invalid-break-position` the break keeps its bare `Position`. Apache's `flow_changing-ipd_7_legacy.xml` pins that setting's
  page count, and docx4j-67's candidate, gated on the corpus alone, failed it: FOP's own suite found this, the gate could not. The setting
  is off by default, and the old behaviour with it, defects included, is what it asks for.

Comments in `handleFloatLayout` and `edgeRestartable` no longer name a line manager as the `self` case. That branch stays: across the
whole layout suite it no longer fires, but a block's own box can still reach it, and it is CR-024 §11.12's. `getChangedKnuthElements` keeps its
bare positions: it replaces elements after the breaks are chosen, and no restart reads them.

## 4. Left

- **A break inside a multi-line nested block at a change of page width**: 4 of the 12 cases of that form still repeat lines (9 on r68). The
  restart position is the nested line manager's, read as the outer one's. docx4j's `w:br` is a zero-height block, so its form is not this.
  Not measured against Word; not ordered.
- **B-1 at mode 15**: M3 stays beside the float where Word moves it with M4; widow control across `w:br`, §6.6 item 19. Not this fix.

## 5. Measured

Layout test `block_restart-between-sequences.xml`, seven sequences; it fails on bare positions and, at sequence d, without the one-box
case. CI's command 4105/0, checkstyle 0, spotbugs 0. Through `RenderFo` on cp-b360, r69 in place of r68, both modes: `float-foot-edges`
changes exactly B-1, B+1 and B+3, each line once; `float-foot-empties` the same as b349's reading; docx4j-67's `c56t/real.fo` draws every
line once.

## 6. Gate

Against b360 on r68, docx4j 7f6162ea2. Reach: any restart at a break between two sequences of a paragraph. Pass: B-1, B+1, B+3 each line
once in both modes; no line of the real-block FO lost or repeated; mode 12's B right on page and y; every other `float-foot-edges` case
unchanged from r68; `float-foot-empties` 19 of 19; no loser; events equal.

Gate b362 on r69 (sha256 checked) against b360 on r68, docx4j 7f6162ea2 in both, docx4j-67: PASS on every item. Corpora 0 movers, events
equal, all four means unchanged; of 870 PDFs only the two probes and 11875's clock differ. `float-foot-edges`: exactly B-1, B+1 and B+3
changed in both modes, the other 44 cases line for line the same, M1 to M4 once in all six, all 8 moved lines a mode closer to Word. Wrong
on page and y: none at mode 12, B-1 alone at mode 15 (§4). Right on page, x and y: 44 of 47 at mode 12, 43 of 47 at mode 15 (41 each on
r68). `float-foot-empties` 19 of 19, largest difference 0.10pt. `c56t/real.fo` every line once. Same output as docx4j-67's overlay b361 on
every document but 11875's clock, so this CR's two additions do not move docx4j.

No corpus document changed, so none reaches the restart this repairs on the forms swept, though the shape is present: of 450 distinct
documents 140 have a `w:br`, 36 a `w:br` and a width change between sections, 2 of those unequal columns. That says nothing of lines lost
some other way. b362 on r69 is the fork's baseline for later gates.
