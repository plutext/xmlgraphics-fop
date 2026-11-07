# CR-035: text-indent on a block's first line after a block-level child

- **Status:** BUILT 2026-11-04 on branch `CR-035-text-indent-after-block`, cut from `2.12-docx4j.1` at e096fb41f,
  code ad739af01. **Gated PASS 2026-11-04 by docx4j-12 (b333 on r62 against b330, §5.1) and MERGED to
  `2.12-docx4j.1` by fast-forward; RELEASED in `2.12-docx4j.1` (Maven Central 2026-11-07, version commit c69e89ffb).** Ordered 2026-11-04 by Jason through docx4j-12 ("please fix CR-001 §6.6
  item 53 in the fork"), the same day docx4j-12 recorded the item and its own workaround.
- **Serves:** Enterprise CR-001 §6.6 item 53: a block's first line loses its `text-indent` where a block-level
  child precedes its inline content. Corpus: 4083's question headings (style 2Question, `w:ind` left 426 hanging
  426; a check-mark text box at each start), "B.6" and "B.7" drawn at x 63.8 where Word has 42.5; 14236, a
  letterhead heading led by an anchored logo with a 107.45pt hanging indent, wrapping and costing a page.
- **Capability:** `first-line-indent-after-block` (new, the thirty-first), on which docx4j retires
  `WordLayoutFixups.hangingIndentAfterLeadingBlock` (docx4j a330e5da3, a nested block for a negative indent) and
  `firstLineIndentAfterLeadingBlock` (since docx4j 17.1.0, a leader for a positive one).

## 1. The defect

docx4j writes a text box or picture anchored at a paragraph's start as a zero-height `fo:block-container`, holding
the absolutely positioned box, that is the first child of the paragraph's `fo:block`; an empty `fo:block` first
is the other shape. `LineLayoutManager.initialize` set `isFirstInBlock = (this == getParent().getChildLMs().get(0))`,
and both the indent element at the paragraph's start (`Paragraph.startSequence`) and the first line's start-indent
(`LineBreakingAlgorithm.updateData2`) are conditional on it. A block-level child before the inline content is an
earlier child manager, so the line manager was not first and the block's first line took no indent.

Reproducer (docx4j-12's, `docs/upstream/repro/FOP-text-indent-after-block.fo`; page margin 72pt, Helvetica 10pt),
rendered through `org.apache.fop.cli.Main` on `2.12-docx4j.1` at e096fb41f before the change:

| case | shape | first line x before | expected |
|---|---|---|---|
| H1 | start-indent 21.3pt, text-indent -21.3pt, text only | 72.0 | 72.0 |
| H2 | the same, led by a zero-height `fo:block-container` holding an absolute one | 93.3 | 72.0 |
| P1 | start-indent 3pt, text-indent 18pt, text only | 93.0 | 93.0 |
| P2 | the same, led by an empty `fo:block` | 75.0 | 93.0 |

## 2. The specification, read honestly

docx4j-12's request, and item 53's first text, cite XSL 1.1 §7.16.11 as applying the indent to the block's first
line-area whatever block-areas precede it. The Recommendation's words are narrower: *"The text-indent property
specifies an adjustment to the start-indent of the first child L of the first block-area generated and returned by
the formatting object, provided L is a line-area."* The zero-height block-container's area is the block-area's first
child and is not a line-area, so by the letter no line of H2 is indented, and Apache FOP's behaviour is conformant.
(An empty `fo:block` generates a zero-height block-area in FOP, so P2 reads the same way.)

The CSS 2 definition XSL amends says what the author means: *"the indentation of the first box that flows into the
block's first line box"*, and XSL's own gloss is that *"a negative value specifies a hanging indent (outdent) on the
first line."* No author of a hanging-indent heading means the indent to vanish because a text box is anchored at
its start, and Word, whose paragraphs these are, indents the first line. So this is a rule beyond the letter, as
CR-025's row room was (§6.6 item 48, "Word's rule beyond XSL"), and the README's upstream column and the JIRA draft
(§6) say so rather than claiming conformance. Item 53's sentence on the specification should be corrected on the
Enterprise side (told to docx4j-12).

## 3. As built

`LineLayoutManager.initialize` sets `isFirstInBlock` from `noLineManagerBefore()`: true when no earlier child
manager of the block is a `LineLayoutManager`. A block-container, a float's wrapper or an empty block before the
inline content no longer changes it; a nested block after inline content still takes no indent from the
enclosing block, since a line manager precedes it (Apache's `block_text-indent.xml`, third case, unchanged); a
nested block's own first line keeps the inherited indent as before. `WordLineLayoutManager` inherits the field,
so Word mode follows. Nothing else changes: the restart path that clears `isFirstInBlock` on a re-read inside the
paragraph is untouched.

Rendered after the change: H1 72.0, H2 72.0, P1 93.0, P2 93.0; the second lines at 93.3, 93.3, 75.0, 75.0.

Layout test `block_text-indent_after-block.xml`, six cases: the two controls, the block-container, the empty
block, a nested block with content first (its own first line and the enclosing block's first line both indented,
the rule's consequence pinned), and inline content, a nested block, inline content (the second run unindented).
`Docx4jHooksTestCase` counts 31 capabilities.

## 4. What docx4j changes

Under `first-line-indent-after-block`, docx4j leaves the paragraph's `text-indent` on its block and skips both
writer workarounds. Nothing else: the text box's own block still inherits the paragraph's `text-indent` (the "X"
line of the reproducer carries start-indent -21.3pt before and after), as it always has; docx4j's writer resets it
where it matters.

## 5. Gate

docx4j-12 gates the renderer drop against b329 with the two workarounds out, keyed on the capability. Pass: 4083's
B.2 to B.7 headings at Word's x 42.5 and 14236 at Word's 2 pages without the workarounds, as b329 had them with;
no corpus loser; events equal.

### 5.1 Gate result (docx4j-12, 2026-11-04): PASS

The baseline had moved to b330 (b329 plus docx4j 8260e843b, a text-box positioning fix, no mover) before r62
arrived; r62 (ad739af01, sha256 verified) is r61 plus this change.

- **b331:** r62 with docx4j's two workarounds keyed off (docx4j c559809dc, `FopCapabilities.FIRST_LINE_INDENT_AFTER_BLOCK`).
  No mover, but four new events, all from the inheritance §4 names: the leader workaround used to remove a
  paragraph's positive `text-indent`, and with the paragraph keeping it the anchored content's blocks inherited it.
  4083: a floating table anchored in a paragraph indented 36pt, its blank cell's line over by 25.2pt (+2 line
  events); 6625: a zero-width picture container's line over by 1.7pt (+1). Text boxes were fine (docx4j's
  `resetTextBox` already sets `text-indent="0pt"` on the box).
- **docx4j's fix** (in c559809dc): a new writer pass, `anchoredContentTakesNoTextIndent`, gives a paragraph's
  `fo:block-container` and `fo:float` children `text-indent="0pt"` wherever the paragraph keeps a non-zero indent.
  Nothing in the writer had relied on the nested block to shield the box.
- **b332:** r61 with that pass alone: no mover, events equal (a hanging indent had been inherited the same way,
  unseen), so the pass is neutral on its own.
- **b333:** r62, workarounds off, with the pass: no mover, events equal to b330; 4083's B.2 to B.7 at Word's x 42.5
  and 14236 on Word's 2 pages, from the renderer alone. **PASS.** b333 on r62 is the fork's baseline for later gates.

Enterprise item 53 corrected by docx4j-12 at 92f3fe7 (the letter of §7.16.11 as §2 reads it); the register records
the attempt under `hanging-indent-after-leading-block`.

## 6. Upstream

JIRA text drafted, `docs/upstream/text-indent-after-block.txt`, for Jason to decide whether to file: the draft
states the letter of §7.16.11 and offers the change as the CSS meaning the clause amends, with the layout test.
The fix is cut on `FOP-text-indent-after-block` in `../fop-upstream-wt` against Apache `main` (1301bcf74), one
commit, 297234f16 (Apache's form: no change notice; the fop-core suite 3732 tests, 0 failures, checkstyle clean;
the reproducer gives H2 93.3 and P2 75.0 on `main`'s own build before it). Apache may decline it as conformant; the
fork carries it either way.
