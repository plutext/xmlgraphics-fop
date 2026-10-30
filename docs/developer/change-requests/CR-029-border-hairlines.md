# CR-029: a solid border is drawn without a clip where its corners need none

Status: BUILT 2026-10-29 on branch `CR-029-border-hairlines` off `2.11-docx4j.8` (code 8d73851e9, full suite
3929/0, checkstyle and spotbugs clean under CI's command; r48 at `~/fop-renderers/r48-CR-029-8d73851e9/`, sha256
c3fcc19d...), at Jason's word ("yes please", after the investigation in §1). No option and no capability: docx4j
writes nothing for it. Gated PASS 2026-10-29 on r48 (§7). MERGED 2026-10-29 to `2.11-docx4j.8` by fast-forward
on Jason's word (tip a14709f7b). RELEASED in `2.11-docx4j.8` (Maven Central 2026-10-30, version commit b7f6ad8bf;
`docs/release-notes/2.11-docx4j.8.md`). JIRA and pull request drafted,
not filed (§6). Registry key `fop/CR-029` (portfolio bfbaeb0). Enterprise CR-001 §6.6 item 52
(Enterprise 7de78cd, §8).

## 1. The symptom

Jason, 2026-10-29: in Okular the vertical cell borders of `Plutext_Docx4j-Enterprise-Edition_Pricing_202610_USD.docx`
rendered through docx4j (`fo/..._USD-fo.pdf` beside it in `docx4j-portfolio/pricing`, produced by `2.11-docx4j.7`)
are missing until you zoom in; Word's PDF of the same document shows them at every zoom. Reproduced with poppler's
`pdftoppm`, which is Okular's renderer (Splash, vector anti-aliasing on, as Okular's default). The darkness of the
five vertical borders in the header row of page 2's first table, 0 invisible, 255 black:

| dpi | the fork's PDF | Word's PDF | the fork's PDF, its 599 border clips removed |
|---|---|---|---|
| 60 | 0 0 0 111 0 | 255 x5 | 255 x5 |
| 72 | 0 0 0 0 0 | 255 x5 | 255 x5 |
| 80 | 0 197 90 32 166 | 255 x5 | 255 x5 |
| 96 | 166 197 197 0 0 | 255 x5 | 255 x5 |
| 110 | 255 197 197 90 166 | 255 x5 | 255 x5 |
| 144 | 0 166 0 166 255 | 255 x5 | 255 x5 |

The horizontal borders are thinned too (32 to 197 at the same zooms) but at those zooms mostly survive. With
`pdftoppm -aaVector no` the fork's borders are solid at every zoom but one (60 dpi, the left edge). MuPDF draws
both PDFs' borders grey, the fork's lighter (17 to 161 against Word's 89 to 241).

## 2. The mechanism

Word draws each border as one filled rectangle (`x y 0.48 h re f*`), its corners as separate 0.48pt squares.

FOP's `BorderPainter.drawBorderSegment` draws each side of each border rectangle as a line of the border's width
inside a clip: a trapezoid that gives the side its share of the two corners (the mitre), and, for a collapsed
border between two cells (`BorderProps.Mode.COLLAPSE_INNER`), only the half of the border on the cell's own side,
since the neighbouring cell draws the other half (`getClippedWidth`, width / 2). A 0.48pt border between two cells
is so drawn twice, each time through a clip 0.24pt wide; at the table's edge (`COLLAPSE_OUTER`) the clip spans
the whole width.

Poppler widens a thin line, and a filled path that is a single rectangle, to at least one whole device pixel,
snapping its edges to the pixel grid ("stroke adjustment"; for the rectangle fill, its source says this matches
Acrobat). It does not adjust a clip path. At screen zoom the clip is a fraction of a pixel: where the snapped line
falls in a pixel the clip barely reaches, nothing is painted, and where it shares a pixel the clip lets through a
fraction of the anti-aliasing samples. Measured: the clips removed from the fork's PDF (the paths and `W n` before
each border line deleted, nothing else) give solid borders at every zoom (§1); the clips kept with the line
widened to 3pt, so the snapping cannot move it off the clip, give no zeros but greys of 32 to 255, the clip's own
sub-pixel coverage. Zooming moves the borders against the pixel grid and widens the clip in pixels, so they come
and go. Even the full-width clip of an outer border loses the line (the table's left edge at 72 dpi).

The clip matters only where the two borders meeting at a corner differ: in colour, in style (a dash pattern, a
double border), with a radius, or with a translucent colour that would darken where painted twice. Where the
neighbouring sides are solid in the same colour, or absent, the corners are covered with the same paint whether
each side takes its mitred share or the whole corner, and for a collapsed border both cells resolve to the same
border and draw the same line.

Apache's `table-border-overpaint` (FOP-2536, FOP 2.6), which merges a table's border segments into long ones for
viewers that draw polygons badly, does not cure this: it keeps the half-width clip. On a minimal table (scratch
`t.fo`, the pricing table's column widths) the vertical borders still vanished at 60, 72, 80 and 110 dpi with it on.

## 3. The change

`BorderPainter.drawBorderSegment`: a side is drawn without the clip, as the same line over the same extent
(`drawBorderLine` from the start to the end of the side, across its whole width, out to the outer corner where
both meeting borders are a table's outer edge, as the clipped path draws it), when `isDrawnWithoutClip`: the side
is solid with an opaque colour, no radius at either of its corners, and each neighbouring side is absent (width 0)
or solid, opaque and the same colour (`ColorUtil.isSameColor`). Otherwise the clip is set as before.

The renderers that reach it are the PDF and PostScript painters, whose solid border line has butt caps (PostScript
sets `0 setlinecap`; PDF sets no cap, and every cap FOP's PDF painter sets is inside a saved state, so the initial
butt cap holds). The Java2D painter's `BorderPainter` is unreachable: `Java2DPainter.drawBorderRect` has five
parameters, so the `IFPainter` method with six is `AbstractIFPainter`'s, which fills rectangles; PNG and TIFF output
are unchanged (checked at 600 dpi). AFP overrides `drawBorders`.

One difference remains, by construction: where a collapsed border's two cells see different borders at a shared
corner (say a vertical border with a red border above one cell and a black one above the other), the cell whose
corner is all one colour now draws its line across the whole width, into a corner quarter of the border's width
that the other cell's mitre would have split. The area is at most half the border's width square.

## 4. Measurements

The minimal table rendered through the fork (core r47 with the changed class first), poppler, the five vertical
borders:

| dpi | before | after |
|---|---|---|
| 60 | 14 23 15 197 0 | 255 x5 |
| 72 | 16 29 16 14 0 | 255 x5 |
| 96 | 166 197 197 90 255 | 255 x5 |
| 110 | 166 197 18 177 0 | 255 x5 |
| 144 | 19 166 166 177 166 | 255 x5 |

(A reading near 15 is the cell's text inside the measuring window: the border is gone.) Every zoom of 60, 72, 80,
90, 96, 100, 110, 120 and 144 dpi is 255 after. In MuPDF the borders go from 17 to 161 before to 61 to 252 after.
The page's clip operators go from 32 to 0. Drawn without anti-aliasing at 1200 dpi the table is the same but for one
pixel at each of the two outer top corners (0.06pt); the PostScript, drawn by Ghostscript at 1200 dpi, is pixel for
pixel the same, its clips gone (16093 bytes to 14163). The measuring script, `vborders.py`, and the probe are in
`~/fop-session-tools/2026-10-29-cr029-border-hairlines/`.

## 5. Tests

`BorderPainterTestCase.drawSolidBordersWithoutClip`: solid black borders all round in each of the three modes draw
four lines and no clip, each over its whole side and width (`0, 0, 1000, 10` separate; `0, -5, 990, 5` collapsed
inner; `-5, -5, 995, 5` collapsed outer). `drawBordersWithClipWhereCornersNeedIt`: a red before border among black
ones clips three sides (itself and the two it meets) and not the after side; dashed, translucent and rounded
borders clip all four. The existing tests use style 0 with no colour, so they keep the clipped path. With the
change the full suite is 3929/0, and checkstyle and spotbugs are clean under CI's command.

## 6. Upstream

The clip is Apache's, unchanged in the fork, and the same on Apache `main`, so the fix goes upstream first: branch
`FOP-border-hairlines` in `../fop-upstream-wt`, one commit (533c0c0e4) on Apache `main` 7b96ab1a4, the fop-core suite
3721/0 and checkstyle clean. Measured on `main` itself (its build, its `BorderPainter` compiled first for the
before): the probe's readings are §4's to the digit, before and after, and the 1200 dpi PDF and PostScript
comparisons the same. The FO is `docs/upstream/repro/FOP-border-hairlines.fo`. JIRA and pull request texts in
`docs/upstream/border-hairlines.txt` and `../fop-upstream-prs/FOP-border-hairlines.md`, filed and pushed only on
Jason's OK. Related: FOP-2434 (varying table border thickness, closed as a duplicate) and FOP-2536 (its fix, the
`table-border-overpaint` option above).

## 7. Gate

Nothing moves in layout, so the docx4j gate should read 0 movers and every event count equal; the change is in the
painted borders, which the scoreboard does not see. The measure is §1's table on the pricing document rendered by the
candidate: every vertical border 255 at every zoom.

PASS 2026-10-29 (the docx4j session, its register's attempts.md 1bf9827): control b285 (docx4j bc842ac66 on the
released `.7` core, the `.8` head without CR-028) against candidate b286 (the same jars on r48, sha256 checked, 26
hooks in both), over real, real2, real3, real-c2 and the probes: 0 movers, every event count equal (linebig 3241,
line 17612, clip 1552, and the rest). The pricing document rendered by docx4j on r48 (PDF and FO under
`~/fop-renderers/r48-CR-029-8d73851e9/`, the `.7` control's in `control-2.11-docx4j.7/` beside them; the two FOs
differ only in a temporary image name): page 2's five vertical rules read 255 at every zoom of 60, 72, 80, 90, 96,
100, 110, 120 and 144 dpi (re-measured here), where the control read 0 0 0 0 0 at 72 dpi, and page 2 has no clip.

## 8. Enterprise CR-001 §6.6

Read before proposing (2026-10-29): no item covers how a border is painted. Item 7 (the separate model's half gap
at the table edge) is layout. Proposed to the docx4j session as item 52 and agreed with the gate's pass; added by
this session as Enterprise 7de78cd (that file alone), in the Open tally until this CR merges.
