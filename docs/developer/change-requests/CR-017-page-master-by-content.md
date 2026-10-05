# CR-017: page masters chosen by the content a page starts with

Status: DONE 2026-10-05, gated PASS by the docx4j session (§10) and merged to `2.11-docx4j.5` by fast-forward;
unreleased. Implemented at Jason's word on branch `CR-017-page-master-by-content` (code f0d2717fa, the 12802 fix
7c6f43b07); the full build passes, 3804 tests, 0 failures, checkstyle and spotbugs clean. Designed and revised after
two reviews (§8). Registry key
`fop/CR-017`. Capability `page-master-by-content`. A docx4j hook, for Word compatibility: XSL chooses a page master
by position, parity and blankness only. So it is not upstream-bound. Requested by the docx4j session for docx4j
CR-031 phase 2 (registered as `docx4j/CR-031.2`). That follows Jason's decision 1 there (2026-10-05): the fork
extension, CR-031 §4.2b, rather than extra layout passes in docx4j.

docx4j CR-031 (`../docx4j/docs/developer/change-requests/CR-031-continuous-sections-paginated-as-word.md`) §2
records Word's rule, measured on six phase-0 goldens. A page takes its top and bottom margins, its header and
footer distances, and its header and footer text from the section that owns its first line. A section starting
mid-page changes nothing on that page, and modes 14 and 15 agree. docx4j merges a run of continuous sections into
one page-sequence, and FO gives that one set of masters. So every page has the first section's vertical margins:
see corpus document 12802, and pages 5 to 8 of the section-continuous-geometry golden, which are 71pt low and
220pt long at the foot. §4.2b sketches this extension, read from this tree. This CR is the fork's design of it, and
replaces §4.2b's sketch where the two differ.

**Enterprise CR-001 §6.6 items that bear on it:**
- **18.** Its proposed hook (space-after kept against the footnote area) edits the same three `computeDifference`
  lines (§3.3). The two must compose: whichever lands second takes the other's form of those lines.
- **24.** The `AssertionError` in `BalancingColumnBreakingAlgorithm.getInitialBreaks` with `span="all"` in a
  multi-column sequence has not been reproduced. Test 3 (§5) runs in exactly that code under `-ea`. If it trips,
  that is item 24 reproduced, and recorded there.
- **21.** The NPE with a block-container in a multi-column flow has not been reproduced either. It records that
  docx4j stopped emitting block-containers in multi-column flows (indents applied per block). That bears on where
  docx4j puts the marker (§3.1, and §7 for the docx4j session).

The companion hook in CR-031 §4.2b's last paragraph (header and footer extents measured in FOP, retiring docx4j's
extent pre-pass) is **not** in this CR. It changes every conversion, where this changes about 34 documents, so it
gets its own CR, gate and number when docx4j's phase 5 is taken up. It will reuse the `PageProvider` seam described
here (§3.4).

## 1. What FOP does now

- **Which master a page gets.** `PageProvider.cacheNextPage` asks
  `PageSequence.getNextSimplePageMaster(index, first, last, blank)`. That walks the page-sequence-master's
  sub-sequences with state (`PageSequenceMaster.currentSubSequence`); `goToPreviousSimplePageMaster` undoes a step
  when a cached page is discarded.
- **How tall the breaker thinks a page is.** `PageBreakingAlgorithm.getLineWidth(int line)` returns
  `PageProvider.getAvailableBPD(line)`. That walks the parts (columns) from the start of the current element list
  over cached `Page` objects, which `getPage` creates on demand with the position-chosen master. It skips a page
  whose region-body is not named for the flow (line 157), and returns `BodyRegion.getRemainingBPD()`: the body's
  height less the spans already on the page. A one-entry cache (`lastRequestedIndex`, `lastReportedBPD`) is keyed
  by the part index alone.
- **Who asks.** All five page-height requests are in `PageBreakingAlgorithm`, and each has a node in hand:
  `computeDifference` at lines 576, 588 and 618 (`activeNode`), and `createFootnotePages` at lines 969 and 1001
  (`lastNode`, `node`).
- **Where pages are made.** There are several places, not one:
  - `PageBreaker.getNextBlockList` (lines 164-176) makes the page a new element list starts on, through
    `handleBreakTrait`, before the list is built.
  - `AbstractBreaker.addAreas` reaches `PageBreaker.startPart`, which makes a page for each later part.
  - `redoLayout` (lines 386-410, column balancing and last-page masters) adds areas up to the last page, makes
    that page itself with `handleBreakTrait(EN_PAGE)`, and runs a fresh `BalancingColumnBreakingAlgorithm` there.
    That algorithm has no node chain back to the main run.
  - `handleFloatLayout` (line 786) adds areas, then re-reads elements from a position inside the list, skipping
    `getNextBlockList`'s preamble.

  - Outside the breaker: the sequence's first page, in `PageSequenceLayoutManager.activateLayout` (line 139),
    before the breaker exists. The blank pages of `force-page-count`, in `AbstractPageSequenceLayoutManager`
    (lines 372-411), after the breaker is done. The blank page of an odd- or even-page break, in
    `PageBreaker.handleBreakBeforeFollowingPage`.

  `PageProvider.setStartOfNextElementList`, which tells the provider where a list starts, is called four times:
  - `getNextBlockList` (176);
  - `prepareToRedoLayout` (363), for last-page masters only, which are out of scope (§3.5);
  - `redoLayout` (412);
  - `startPart` (521).

  **`handleFloatLayout` makes no such call.** It sets `pageBreakHandled`, so `getNextBlockList`'s preamble and
  `startPart`'s call are both skipped, and the re-read list runs against the old list's start in the provider.
  `PageProvider.getPage` already replaces a cached page whose blank, last-page or span condition no longer
  matches: `newPageVP.replace(oldPageVP)` and `IDTracker.replacePageViewPort`.

FOP already lays out pages of different body heights, such as a taller first-page region-body, as long as the
height is a function of the page's number. This CR lets it depend on what the page starts with.

## 2. Why it fits the algorithm, and where it is not exact

FOP's page breaker is Knuth's total-fit. A candidate part is measured from an active node, whose `position` is the
element where the part before ended. On a page's first column, the part owning the first box after that position
owns the page's first line, which is Word's rule.

- **Single-column flows: the search stays exact.** Candidates ending at the same element start the next page at
  the same place, so they agree on its owner and height. Every candidate page is measured with the master it would
  really get, in one pass, and nothing oscillates.
- **Multi-column pages: consistent, not guaranteed optimal.** The height of a later column depends on where its
  page started, and FOP merges nodes by position, part number (`line`) and fitness class only. Two candidates ending
  column 2 at the same element can have page-start nodes on either side of a part boundary, so different owners and
  heights. The merge keeps one of them without regard to the owner. The result is still consistent, since areas
  follow the chosen chain and every page is laid out with its own owner's height. But a pruned alternative might
  have given a better total.
- **Odd and even masters of different body heights.** The height also depends on the folio, which the part number
  determines, so it is still a function of the node chain.

## 3. The change

### 3.1 The attribute, and what is accepted

`fox:page-sequence-master-reference="name"` names a `fo:page-sequence-master` in the layout-master-set, by its
`master-name`. Pages whose first line lies at or after the start of the marked FO, up to the next marked FO, take
their masters from it instead of from the page-sequence's `master-reference`. Pages before the first marked FO keep
the sequence's own masters. It is read through `FObj`'s foreign attributes, or registered as an extension property
as `fox:continuation-display-align` was (fop/CR-013), whichever keeps validation quiet.

Each condition below is reported once, as a warning in FOP's log, and the marker is then ignored, so the
sequence's own master applies:
- **Where the marker sits.** A block-level FO (`fo:block`, `fo:block-container`, `fo:list-block`, `fo:table`)
  whose ancestors up to the `fo:flow` are blocks or block-containers. A marker inside a table cell, a list item,
  a footnote, a float or an inline is reported and ignored. FOP has no rule for which page such content opens.
- **What the named master is.** One unbounded `fo:repeatable-page-master-alternatives`, which is what docx4j
  writes. Its conditional references are tested per page (`ConditionalPageMasterReference.isValid`), with no walk
  state.
- **What every master it can choose has.** The reference part's region-body width and column count, and a
  region-body whose `region-name` is the flow's `flow-name`. `getAvailableBPD` skips pages without the latter.
  Equal width and columns mean no change of master reaches FOP's IPD-change restart (`restartAtLM`). Side floats
  reach it regardless, as they do today.

### 3.2 Where each part begins

When an element list is built, it is scanned once. For each element in order, the breaker walks its position chain
from the flow's position down (`Position.getPosition()`, each `getLM().getFObj()`) to the outermost marked FO, if
any. A part begins where that FO is a different FO from the part in force. FOs are compared by identity, so two
consecutive parts naming the same master are still two parts.

- **The transition sits on the first box at or after that element.** A marked FO whose first element is a glue or
  penalty (its space-before, a break) therefore still takes effect. **A marked FO that produces no box starts its
  part at the next box**, rather than being lost:
  - **An empty `fo:block`** is not that case. `BlockStackingLayoutManager` (lines 359-364) gives it an auxiliary
    zero-width box, unless it carries a forced break after, and the transition sits on that box.
  - **An absolutely positioned block-container** (a floating table or picture of height 0) is not checked yet;
    test 9 settles what it emits. If it emits no element at all, the marker cannot be seen in the list. Then its
    layout manager notes itself to the breaker when it produces its (empty) contribution, and the transition sits
    on the next box.

  A marker on an `fo:float` is reported and ignored, since a float is not one of the FO kinds §3.1 accepts.
- **There is no "already seen" set.** The part in force at the start of a list is the part in force at the last
  committed break: the last break whose areas were added. So it is the same whether the list follows a forced
  break, a span change, a redo, or a float restart that re-reads elements from mid-list (§3.4).
- **The owner of the content after a break at element `p`** is the part of the last transition at or before the
  first box after `p`. The cost is one scan per list.

### 3.3 The owner travels with the node

`KnuthPageNode` gains two fields, set in `PageBreakingAlgorithm.createNode`: the owner of the part that starts at
this node, and whether that part opens the owner's first page. They are set once, when the node is created:
- the part starts a new page (column 0): the owner of the content after `node.position` (§3.2). It is the
  owner's first page when the owner's transition is that page's first box.
- a later column on the same page: copied from the previous node, so every column of a page has its page's owner.
- the list's first page, when that page already holds content (§3.4): the page's recorded owner.

`getLineWidth(KnuthNode node)` replaces `getLineWidth(node.line)` at the five call sites. It reads the two fields,
with no walk back through `previous` inside `computeDifference`'s loop.

**A page holding only footnote bodies** takes the owner of the page before it. That is not measured in Word
(CR-031 §8). It is not what the general rule gives: `createFootnotePages` creates its nodes at `lastNode.position`
(lines 992 and 1007), so the column-0 rule would give the part in force at the end of the list. The two differ when
a part starts part-way down the last content page. So those two `createNode` calls copy the owner from the previous
node, with "not its first page" (test 5).

Without the attribute anywhere in the sequence, the fields are unset and `getLineWidth(node)` is
`getLineWidth(node.line)`, so FOP's behaviour is unchanged.

### 3.4 Pages: every page is made with its owner

`PageProvider` keeps a record of the owner of every page it has handed out. **Every place in the breaker that makes
a page tells the provider the page's owner first**, so the page is made, or replaced, with that owner's master.
**When the provider has not been told, its default is the previous page's owner, not its first page.** That covers
the pages made outside the breaker without a hook at each site: the `force-page-count` blanks, and the blank page
of an odd- or even-page break. §3.5 follows from this default. The breaker's places are these:
- **`startPart` during `addAreas`**: the owner of the content after the chosen break, by the same function the
  breaker used, so the two agree.
- **`getNextBlockList`** makes the list's first page before the list exists. The page is made with the owner in
  force at the last committed break, which covers a hard break inside a part.

  When the list's first box is itself a transition, the new part begins right after a page break (9539's shape:
  a part ending in a page break, then the next part). Then, once the list is built, that page is replaced with the
  new owner's master. The page is still empty: no areas, no markers, no static content yet. So the replacement goes
  through the provider's existing replace path: the cache, `IDTracker`, then `pslm.setCurrentPage`. Whether a
  page is fresh is read as `PageBreaker` already does (`getPageViewport().getPage().isEmpty()`).

  The replacement must keep the span `handleBreakTrait` gave the page, `span="all"` included. The same rule
  covers a marker on the flow's very first block: that page is made in `activateLayout` before the breaker exists,
  and is still empty when the first list shows its first box. docx4j writes no such marker, but it is harmless.
- **`redoLayout`**: the page made with `handleBreakTrait(EN_PAGE)` takes the owner of the first box after the
  restart break (`newStartPos`). The balancing algorithm then starts its list on that page and reads its recorded
  owner (§3.3), although it has no chain back to the main run.
- **`handleFloatLayout`**: the areas it adds go through `startPart` as above. The elements it re-reads are scanned
  as a new list, from the part in force at the last committed break (§3.2), so a marker in the discarded tail is
  seen again. It makes no `setStartOfNextElementList` call (§1), so nothing on this path may key "the list's first
  page" on that call. The re-read list's pages take their owners from the provider's record and its default. Test
  13 decides whether that is enough.
- **The list's first page, when it already holds content.** After a span change, FOP starts the next list on a
  page that already exists. Its height is that page's `getRemainingBPD()`, as now, and **every column of that page
  keeps that page's recorded owner, not only the list's first node**. Ownership is decided afresh only from the
  list's next page. This is the common case, not a corner: docx4j changes span at every part boundary where the
  column count changes, so the part after the boundary starts mid-page on a page the part before owns.

**Heights for later pages.** `getAvailableBPD(index, owner, firstOfOwner)` returns the body height of a page made
from the owner's master for that page. The master is chosen by:
- the page's parity, from its folio, as `PageSequence` computes it;
- blankness;
- whether this is the owner's first page: true only when the owner's transition is the page's first box. So a part
  starting mid-page never uses its first-page master (CR-031 P4), and one starting at a page top does (P2).

That page is built as `getLastPageIPD` already builds one (`new Page(spm, index, ...)`). It is measured, not added
to `cachedPages`, and its height is cached by (master, parity, first, blank). The one-entry cache is keyed by index
and owner, so one part's height cannot be returned for another.

The sequence's own master walk keeps its state, since `cacheNextPage` still calls `getNextSimplePageMaster` and
only the chosen `SimplePageMaster` is substituted. The pages `getColumn` makes for column counts and IPDs during
breaking have the sequence's masters. That is harmless, because every master has the same width and column count
(§3.1).

### 3.5 Blank pages, the last page, restarts

- A blank page (`force-page-count`, or the blank of an odd- or even-page break) takes the owner of the page before
  it, and that owner's blank alternative where it has one. This is the provider's default (§3.4); see test 11.
- `page-position="last"` alternatives in an owner's master are out of scope, since docx4j writes none. They are
  reported and ignored.
- No restart offset in the first step. **The restart step is designed in §11**, after probe P7. CR-031 §2 D3 (items 2 and 5) has Word's folios at a
  continuous restart:
  - **P3, without `w:evenAndOddHeaders`:** 1, 2, 2, 3. The section's page 1 is the page it starts on, mid-page,
    which shows the section before's header and folio.
  - **P6, the same shape with `w:evenAndOddHeaders`:** 1, 2, 1, 2, 3. The section's page 1 is the first page it
    owns.

  So "start number plus pages since the start page" holds only without odd and even headers. Odd/even headers and
  mirrored margins follow one parity together, but P6 does not separate folio parity from physical parity, and two
  readings fit:
  - H1: with odd and even headers, the count starts at the first page the section owns.
  - H2: Word puts the origin where folio parity and physical parity agree.

  Probe P7 (CR-031 §5) separates them and waits on a Word run. The step is designed once it is read (CR-031 phase
  3); nothing else in this CR depends on it.

### 3.6 Capability

`Docx4jFop.PAGE_MASTER_BY_CONTENT = "page-master-by-content"`. docx4j writes the attribute and the per-part masters
only where the capability is present; on Apache FOP it writes today's FO.

## 4. What is unchanged

FOP's output for any FO without the attribute. On the docx4j side, every document without a merged run whose
parts' vertical margins differ: 32 across the corpora's 550 have one. CR-031 counted 34, but 9539 and 11741 change
the page size at the margin change, so they are separate sequences already.

## 5. Tests (FOP layout tests, before any docx4j gate)

Each is a layout-engine test (`fop/test/layoutengine/standard-testcases`) checking the page viewports' masters and
the body's position. The suite runs with assertions on.
1. Two parts in one sequence, the second starting mid-page: that page keeps the first part's master, and the next
   page takes the second's (CR-031 P1's shape).
2. The second part starting exactly at a page top: that page takes the second part's master, and its first-page
   master where it has one (P2, P4).
3. **A span change at the boundary.** A two-column part, then a one-column part starting mid-page, then the reverse,
   one column to two. Every column of the continuing page keeps the first part's owner and the remaining height,
   and the next page takes the second part's master. Run under `-ea`, which is item 24's code.
4. Odd and even masters of different body heights in the second part: the heights follow the folio.
5. A footnote whose body carries over to a page holding only footnotes, with a part boundary part-way down the
   last content page: the footnote page takes that page's owner, not the part in force at the end of the list.
6. An `id` on a block that lands on a replaced page, cited by `fo:page-number-citation`: the citation gives the
   right page.
7. Each rejected case in §3.1 is reported, and the sequence's master applies:
   - a master not in the required form;
   - a different body width;
   - a region-body not named for the flow;
   - a marker inside a table cell or a footnote.
8. No attribute: the whole layout suite is unchanged (the full build).
9. A marked empty block, then content; and a marked absolutely positioned block-container, then content: in both,
   the part takes over at that content's first box.
10. **Page breaks:**
    - a `break-before="page"` inside a later part: the new page keeps that part's owner;
    - a marked block directly after a page break, at the top of its list: the fresh page is replaced with the
      marked part's master, its first-page master included;
    - in a multi-column sequence, a marked `span="all"` block directly after a page break: the replaced page keeps
      the span `handleBreakTrait` gave it;
    - a marker on the flow's first block: the first page, made in `activateLayout`, is replaced while empty.
11. An odd/even blank page from `force-page-count`, and one from `break-before="odd-page"`: each takes the
    previous page's owner and that owner's blank alternative.
12. **Balancing beyond the list's first page.** A two-column part that runs past the page it starts on, with a
    part boundary before it. The balanced last page has its recorded owner, and the balancing algorithm sees that
    owner's remaining height.
13. **A side float** (`fo:float`, which docx4j writes) in a merged run, with a marker after it in the re-read tail:
    the part takes over where it should. If this cannot be made right, the fallback is to report and ignore the
    markers in a sequence with side floats. This test decides between the two, and the CR records which.
    **Decided (§9): honoured.** The marker in the re-read tail is seen, so 719 and 11256 get the fix.

## 6. Risks

- **Page replacement.** Pages made during breaking carry position-chosen masters, and ids are registered against
  them. Tests 6 and 10 cover this; the replacement path itself is FOP's own.
- **The places pages are made** (§3.4). Every one tells the provider the owner first. Tests 10, 12 and 13 reach the
  three that the first version of this CR missed.
- **Multi-column optimality** (§2). Consistent, not guaranteed optimal; the corpus gate will show whether it
  matters.
- **Balanced columns at a span change.** The balanced page has its recorded owner (§3.4). Tests 3 and 12 cover it,
  and if item 24 appears it is recorded there.
- **Footnotes and before-floats.** Their code reads the page height through the same `getLineWidth`, so they see
  the owner's height. Test 5 covers footnotes; before-floats are rare in docx4j's FO and not tested at first.
- **Odd and even masters of different heights.** The height depends on the folio as well as the owner (test 4).
- **Item 18's hook** edits the same `computeDifference` lines (see the header).
- **Upkeep.** The change lives in `PageBreakingAlgorithm`, `PageProvider`, `PageBreaker` and `AbstractBreaker`,
  which change little upstream. It is carried through each merge of Apache `main` (fop/CR-009's pattern).

## 7. Estimate, and what the docx4j side must know

**Five to six days in the fork** to a gated snapshot:
- the attribute, its checks, the transitions, and the node fields: about a day and a half;
- the four page-making places, the replacement of a fresh page, and `PageProvider`'s per-owner heights: about two
  days;
- the thirteen tests and what they find: about two days;
- the full build, the README and the hand-off: half a day.

The restart step (§3.5), once P6 is read, is about a day more.

**For the docx4j session**, since the docx4j side is built against §3.1 and §3.6. Its answers, 2026-10-05:
- **Marker placement.** Every marker is a child of `fo:flow`: the part's `span="all"` block where the part has
  fewer columns, otherwise its first in-flow block-level FO. docx4j skips an absolutely positioned block-container
  and an `fo:float` when choosing it. §3.2 also starts a part at the next box if a marked FO has none.
- **Region-body.** docx4j's region-body states no `region-name`, so it is `xsl-region-body`, as is every flow's
  `flow-name`. The per-part masters keep the reference part's region-body width and column count, and change only
  the vertical margins, the before and after extents, and the region names.
- **Item 21.** `XSLT_Ind` writes no container in either pathway, multi-column or not. Since 17.0.5
  `XsltFOFunctions.shiftIndents` adds the margin difference to the part's own paragraphs and tables, because of the
  balancing exception item 21 records. The factory javadoc, and CR-031 §4.2b after it, still described a
  block-container; both are corrected (docx4j 75af3b3aa).
- **Side floats.** Two of the 32 documents have side `fo:float`s in a merged run: 719 and 11256, one Turkish
  template, six floats each. All six are in part 1, before the run's one part boundary. Part 1's top margin is 0
  and part 2's 70.85pt. Both documents are off Word's page count today (719 28 against 29, 11256 27 against 25). So
  test 13's outcome decides them: honoured, they get the fix; reported and ignored, they keep today's margins. No
  other of the 32 has a side float in its merged run.

## 8. Review, 2026-10-05

A review of the first version (design only, read against CR-031 and the breaker code on `2.11-docx4j.5`) found the
core sound and seven problems. Each was checked against the code before this revision, and each holds:
1. **A list's first page is made before its owner is known.** `getNextBlockList` calls `handleBreakTrait` before
   the list is built. The first version said every page is made after the owner is decided from the chosen nodes.
   It missed a marker on the first box of a list after a page break. Now §3.4: the page takes the owner in force at
   the last committed break, and is replaced while still empty if the list's first box is a transition (test 10).
2. **Column balancing makes its own page** in `redoLayout` and runs a fresh algorithm with no chain back. Now §3.4:
   the provider records every page's owner, and the redo page takes the owner of the first box after the restart
   break (test 12).
3. **Floats re-enter a list mid-way** (`handleFloatLayout`), and an "already seen" set would lose a marker in the
   discarded tail. Now §3.2: no seen set; parts are compared by identity, from the part in force at the last
   committed break. Test 13 decides between handling this and reporting markers with side floats.
4. **§2 claimed exactness for every flow.** It holds for single-column flows only; multi-column pages are
   consistent, not guaranteed optimal. Now §2. The owner is stored on `KnuthPageNode` at creation (§3.3), which
   also takes the walk back through `previous` out of `computeDifference`'s loop.
5. **Gaps in §3.1 and §3.2.** A marked FO yielding no box, a marker inside a table cell or footnote, and owners'
   region-bodies not named for the flow. Now §3.1 and §3.2, with tests 7 and 9.
6. **No §6.6 item was cited.** Items 18, 21 and 24 bear on this; now in the header and §7.
7. **The estimate was light.** Four days became five to six.

The review confirmed and this revision keeps: the five call sites and their lines; testing an unbounded
alternatives per page without walk state; that docx4j-export-fo subclasses none of the four classes, so no
signature hazard; that per-part static-content names validate against the whole layout-master-set; and the
single-column exactness argument.

### 8.1 Second review, 2026-10-05

The second review found the revision answers all seven, and the design ready to start. It raised six corrections,
none structural. Each was checked against the code, and each holds:
1. **§1 was wrong about floats.** `handleFloatLayout` makes no `setStartOfNextElementList` call, since it sets
   `pageBreakHandled`. And `prepareToRedoLayout` (363) is a fourth caller, last-page only. Now §1 and §3.4.
2. **Footnote-only pages are created at `lastNode.position`** (lines 992 and 1007), so the general rule would give
   the wrong owner when a part starts on the last content page. Now §3.3, with test 5 tightened.
3. **Blank pages are made outside the breaker** (`force-page-count`; an odd- or even-page break's blank). Now the
   provider's default, the previous page's owner, not its first page (§3.4), with test 11 widened.
4. **An empty `fo:block` gets an auxiliary zero-width box**, so the fallback is not needed for blocks. Now §3.2.
5. **Test 10 needs a `span="all"` variant, and the flow's first block needs a rule.** Now both are in §3.4 and
   test 10.
6. **§3.1's wording on the IPD-change restart.** Now "no change of master reaches it".

This review predates probe P6. §3.5's restart step is parked on P6 and P7 independently of it.

## 9. Implemented, 2026-10-05

On branch `CR-017-page-master-by-content` off `2.11-docx4j.5`, at Jason's word.

**Files.**
- `PageOwnership` (new): finds the marked FOs once per page-sequence, by walking the main flow's FO subtree. That
  is also where misplaced markers and unacceptable masters are reported. It records each element list's
  transitions and answers which part owns the content after a break. With no valid marker it is inactive, and
  nothing below scans or asks anything.
- `PageProvider`:
  - records the owner of every page handed out for areas (`createPage`, relative to the page-sequence): the
    pending owner where one is set, else the previous page's, not as its first;
  - makes or replaces the page with that owner's master, through the existing replacement path;
  - `getAvailableBPD(index, owner, first)` measures a fresh page of the owner's master (cached by master) for
    pages not yet handed out;
  - caches each part's column per list.
- `PageBreakingAlgorithm`:
  - the owner and first-page flag on `KnuthPageNode`, set in both `createNode`s;
  - `getLineWidth(KnuthNode)` at the five call sites;
  - footnote-only pages copy the owner of the page before;
  - each `PageBreakPosition` carries the owner of its part.
- `AbstractBreaker`: the owner fields on `PageBreakPosition`, and two no-op hooks, around `startPart` and at the
  IPD restart's committed break.
- `PageBreaker`:
  - scans each new list;
  - replaces the list's still-empty first page where its owner differs;
  - sets the pending owner around `startPart` and before `redoLayout`'s page;
  - commits at a float's restart.
- `PageSequenceMaster.getSoleUnboundedAlternatives` and
  `RepeatablePageMasterAlternatives.getPageMasterFor` / `getAlternativeMasters`: the master for a page without
  consuming a repeat.
- `Docx4jFop.PAGE_MASTER_BY_CONTENT`, the fifteenth capability.

**Tests**: twelve layout tests, `page-master-by-content_*.xml`, covering §5's thirteen, all with assertions on:

| test file | §5 | what it shows |
|---|---|---|
| `mid-page` | 1 | page 2 keeps A where B starts mid-page; page 3 takes B, body 30pt lower |
| `page-top` | 2 | B opening page 3 takes its first-page master Bf, then B |
| `span-change` | 3 | two columns to one and one to two: page 2 keeps A2 in every column, page 3 takes B2 |
| `odd-even` | 4 | odd and even masters of different heights alternate |
| `footnote-pages` | 5 | footnote-only pages take A, the page before's owner, not B |
| `citation` | 6 | a citation to a block on a replaced page gives page 3 |
| `ignored` | 7, 8 | four rejected markers, then none at all: every page keeps the sequence's master |
| `empty-and-absolute` | 9 | an empty marked block, and a marked absolute block-container, start their part |
| `page-breaks` | 10 | a break inside a part; a marker after a break, its first master included; the span="all" form; a marker on the flow's first block |
| `blank-pages` | 11 | blanks from force-page-count and from break-before="odd-page" take Cb, the owner's blank master |
| `balancing` | 12 | the balanced page beyond the list's first is B2's |
| `float` | 13 | the marker after a side float is honoured |
| `space-before` | 10 | a marked block with space-before after a page break, in two columns and one, and at a natural page top (§10) |

Without the change, ten fail and two pass. `ignored` passes either way by design. `footnote-pages` also gives A
on stock FOP; it fails, with B, when only the footnote-page rule is removed. §6.6 item 24's assertion did not
trip in `span-change` or `balancing`.

## 10. The first gate, 2026-10-05

The docx4j session's gate on install r10 (code f0d2717fa):
- **Control** (b114: r10, docx4j writing no attribute, against b113): 0 movers in the four corpora and the
  probes.
- **Measurement** (b115: markers and per-part masters on, against b114): no exceptions, every status ok.
  - P1 (both modes): page parity 0.9172 to 0.9586. continuous-margins-at-top 0.9200 to 0.9467.
  - 12802: pages 3 and 4 now start at 41.9 and 42.5 (Word 43.3 and 43.4; before 34.9 and 35.5).
  - 7235: +22 lines; 5507 +1.

**One miss, 12802 page 5**: a marked `span="all"` block with `break-before="page"` opened page 5, but the page
kept the previous part's master. The cause was not the span or the break. The block has `space-before`, and
FOP's space resolver puts a box of its own at the start of the element list, with a `SpaceHandlingPosition` and
no layout manager. §3.2's scan took that box as the list's first box, so the part's transition fell one box later,
and the list's first page was given the previous part. Fix: only a box some layout manager produced counts, both
as a part's first box and as the first content of a page (`PageOwnership.isContentBox`). 12802's page 5 now
takes `s1-p5-simple` (top 30.85pt).

Regression test `space-before` (four shapes). It fails without the fix, with A2 on the page the marked block
opens. The earlier tests are unchanged.

Also found by that gate, and docx4j's own: section-continuous-geometry pages 5 and 6 now take S3's top but run to
the foot, where Word ends at 508.9. That is a docx4j rule (a footer distance past a quarter of the page over an
empty footer part is ignored), which the docx4j session is correcting.

**The second gate, 2026-10-05, PASS.** On install r11 (d5a7616b9, with the fix), 12802's page 5 starts at 29.2
(Word 31.1; r10 41.4), and pages 3 to 6 are within 2pt of Word. b118 (docx4j cand43 on r11) against b116 (the same on
r10): 0 movers in all four corpora and the probes, no exceptions, so everything b115 moved holds. The net from b112 to
b118:
- 5507 at Word's 4 pages, line parity 1.0000 (with docx4j's footer-distance correction, 1ee6dfa8f);
- 7235 +22 lines;
- 12802 pages 3 to 6 within 2pt of Word;
- P1 page parity 0.917 to 0.959.

section-continuous-geometry stays a page short (8 against Word's 9) until docx4j's phase 3, which gives S3 its own
empty footer.

## 11. The restart step, designed after P7 (2026-10-05; proposed, not started)

**Word's rule**, from docx4j CR-031 §2 D3 items 2 and 5 (docx4j dbd38d4f7). The four restart readings:

| probe | `w:evenAndOddHeaders` | restart at | the part starts | folios |
|---|---|---|---|---|
| P3 | no | 1 | mid page 2 | 1, 2, 2, 3 |
| P6 | yes | 1 | mid page 2 | 1, 2, 1, 2, 3 |
| P7 start2 | yes | 2 | mid page 2 | 1, 2, 3, 4, 5 |
| P7 oddstart | yes | 1 | mid page 1 | 1, 2, 3, 4 |

- The page a restarting continuous part starts on prints its owner's folio. When the part starts mid-page, the owner
  is the part before, so that page keeps the old count.
- The part's count, with its page 1 numbered at the restart number S, begins at:
  - without odd and even headers: the page the part starts on, so the next page prints S + 1;
  - with odd and even headers: the page the part starts on if S has that page's parity, else the next page, which
    then prints S itself.
- So with odd and even headers a folio's parity always equals its physical page's. Odd and even masters, and
  mirrored margins, follow both, and the two never differ. It is the continuous counterpart of the blank page Word
  inserts before a next-page restart.
- **Assumption:** "that page's parity" is taken as the parity of the owner's folio on the start page. In every
  probe it equals the physical page's, since there was no earlier restart. This is not measured.
- **Open:** a restarting part that opens its page (no mid-page start) under odd and even headers, where S has the
  wrong parity. By the rule the count would begin on the next page, leaving the opening page one below S. No probe
  has it, so the step treats that page as S's page and records the case as unmeasured, until a probe reads it.

**The attributes**, on the marked FO, alongside `fox:page-sequence-master-reference`:
- `fox:page-number-restart="S"`: an integer, the part's first page number. 0 only where the user agent allows a
  page numbered 0 (`page-number-zero`, fop/CR-012).
- `fox:page-number-restart-parity="keep"`: present where the document has odd and even headers. The count then
  begins on the start page only if S has that page's parity.

**The mechanism.** A page's printed number, which headers, `fo:page-number`, citations and the PDF page labels all
read, is the `PageViewport`'s page-number string, made when the page is made (`makeFormattedPageNumber(index)`).
`PageProvider` already records each handed-out page's owner (§3.4), and pages are handed out in order. It now
also keeps a running folio:
- the first page is numbered as FOP numbers it now;
- each later page is numbered one more than the page before;
- except the page where a restarting part's count begins, which is numbered S. That page is the start page or the
  one after it, by the rule above.

It builds each page with `makeFormattedPageNumber(folio)`, in the page-sequence's own format, and the replacement
path makes the page again where a page cached during breaking carries a different string. The integer page index,
`force-page-count` and odd/even master selection stay physical; with odd and even headers they agree with the
folio by Word's rule. The start page is known from the owner records: it is the page before the part's first
owned page when that page is not the part's first (a mid-page start), else that page itself.

**Not covered:** a number format that changes at a continuous break (`w:pgNumType w:fmt`, roman to arabic, say).
That would be a third attribute, if the corpus has such a document; the docx4j session to say.

**Tests**: the four probes' shapes as layout tests, each checking every page's printed number in a header and
in a citation; plus a restart at 0 with and without `page-number-zero`, and a part with no restart after a
restarting one, which continues the count.

**Estimate**: about a day and a half: the folio in `PageProvider`, the attributes, and the tests. Registry: a
step of this CR, `fop/CR-017.2`, for docx4j CR-031 phase 3 to depend on.
