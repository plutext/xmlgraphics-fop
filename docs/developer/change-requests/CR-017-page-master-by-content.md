# CR-017: page masters chosen by the content a page starts with

Status: PROPOSED 2026-10-05; design and estimate, not started. Registry key `fop/CR-017`. Capability
`page-master-by-content`. A docx4j hook (Word compatibility: XSL chooses a page master by position, parity and
blankness only), so not upstream-bound. Requested by the docx4j session for docx4j CR-031 phase 2, after Jason's
decision 1 there (2026-10-05): the fork extension, CR-031 §4.2b, rather than extra layout passes in docx4j.

docx4j CR-031 (`../docx4j/docs/developer/change-requests/CR-031-continuous-sections-paginated-as-word.md`) §2
records Word's rule, measured on six phase-0 goldens. A page takes its top and bottom margins, its header and
footer distances, and its header and footer text from the section that owns its first line. A section starting
mid-page changes nothing on that page. Modes 14 and 15 agree. docx4j merges a run of continuous sections into
one page-sequence, which FO gives one set of masters, so every page has the first section's vertical margins
(corpus document 12802; the section-continuous-geometry golden, pages 5 to 8: 71pt low and 220pt long at the
foot). §4.2b sketches this extension, read from this tree; this CR is the fork's design of it, and replaces
§4.2b's sketch where the two differ.

The companion hook in CR-031 §4.2b's last paragraph (header and footer extents measured in FOP, retiring
docx4j's extent pre-pass) is **not** in this CR. It changes every conversion, where this changes about 34
documents, so it gets its own CR, gate and number when docx4j's phase 5 is taken up. It will reuse the
`PageProvider` seam described here (§3.4).

## 1. What FOP does now

- **Which master a page gets.** `PageProvider.cacheNextPage` asks
  `PageSequence.getNextSimplePageMaster(index, first, last, blank)`, which walks the page-sequence-master's
  sub-sequences with state (`PageSequenceMaster.currentSubSequence`; `goToPreviousSimplePageMaster` undoes a
  step when a cached page is discarded).
- **How tall the breaker thinks a page is.** `PageBreakingAlgorithm.getLineWidth(int line)` returns
  `PageProvider.getAvailableBPD(line)`. That walks the parts (columns) from the start of the current element
  list over cached `Page` objects, which `getPage` creates on demand with the position-chosen master, and returns
  `BodyRegion.getRemainingBPD()`: the body's height less the spans already on the page. A one-entry cache
  (`lastRequestedIndex`, `lastReportedBPD`) is keyed by part index alone.
- **Who asks.** All five page-height requests are in `PageBreakingAlgorithm`, and each has the active node in
  hand: `computeDifference` three times, `createFootnotePages` twice (§3.3 gives the line numbers on
  `2.11-docx4j.5`).
- **Adding areas.** `AbstractBreaker.addAreas` walks the chosen breaks. For each part, `PageBreaker.startPart`
  and `handleBreakTrait` call `pslm.makeNewPage`, which takes the page from `PageProvider.getPage`. That method
  already replaces a cached page whose blank, last-page or span condition no longer matches:
  `newPageVP.replace(oldPageVP)` and `IDTracker.replacePageViewPort`.

FOP already lays out pages of different body heights, such as a taller first-page region-body, as long as the
height is a function of the page's number. This CR lets it depend on what the page starts with.

## 2. Why it fits the algorithm

FOP's page breaker is Knuth's total-fit. A candidate page is measured from an active node, whose `position` is
the element where the page before ended. The part owning the first box after that position owns the page's first
line, which is Word's rule. Candidates ending at the same element start the next page at the same place, so they
agree on its owner and its height. The search stays exact in one pass: every candidate page is measured with the
master it would really get, and nothing oscillates. FOP already keeps nodes apart by part number (`line`), so a
height that also depends on the folio (odd and even masters of different heights) is a function of the node chain
too.

## 3. The change

### 3.1 The attribute

`fox:page-sequence-master-reference="name"` on a block-level FO in the main flow (`fo:block`,
`fo:block-container`, `fo:list-block`, `fo:table`). The value is the `master-name` of a
`fo:page-sequence-master` in the layout-master-set. Pages whose first line lies at or after the start of this FO,
up to the next FO carrying the attribute, take their masters from it instead of from the page-sequence's
`master-reference`. Pages before the first marked FO keep the sequence's own masters. docx4j puts it on each merged
part's outermost block: the `span="all"` wrapper (`XSLT_Cols`) or the indent block-container (`XSLT_Ind`) where
the part has one, otherwise its first block. Read through `FObj`'s foreign attributes or registered as an
extension property (as `fox:continuation-display-align` was in fop/CR-013), whichever keeps unknown-attribute
validation quiet.

The named page-sequence-master must be what docx4j writes: one unbounded `fo:repeatable-page-master-alternatives`.
Its conditional references are tested per page (§3.4), with no walk state. Any other form, or a name that is not a
page-sequence-master, is reported once through the event producer and ignored; the sequence's own master
applies. Every part's masters must have the reference part's region-body width and column count (docx4j's merged
masters do), so FOP's IPD-change restart (`restartAtLM`) is never reached. A part whose masters differ in width is
reported and ignored.

### 3.2 Where each part begins in the element list

When `PageBreaker` builds an element list (`getNextBlockList`), it scans the list once. For each box it walks the
position chain from the flow's position down (`Position.getPosition()`, each position's `getLM().getFObj()`). The
first box whose chain reaches a marked FO not yet seen records a transition: (element index, master). FOP splits a
flow into several element lists at forced breaks and span changes, so the part in force at the end of one list
carries into the next. The owner of the content after a break at element `p` is the master of the last transition
at or before the first box after `p`. A transition sits on a box, so that is the transition at or before `p`, or
one exactly at that first box. The cost is one scan per list.

### 3.3 The breaker asks with the node

`PageBreakingAlgorithm` gains `getLineWidth(KnuthNode node)`, and the five call sites pass the node they hold:
- `computeDifference`, lines 576, 588 and 618: `activeNode`;
- `createFootnotePages`, lines 969 and 1001: `lastNode` and `node`.

The owner of the page a node's part lies on:
- **The part opens its page** (column 0): the owner of the content after `node.position`, from §3.2.
- **A later column on the page**: follow `node.previous` back by the column index (`PageProvider.getColumn(line)`)
  to the node that started the page, and take its owner. Every column of a page has the same owner.
- **The page the list started on already holds content.** After a span change, FOP starts the next element list
  on a page that already exists (`startColumnOfCurrentElementList`), and its height is that page's
  `getRemainingBPD()`, as now. That page's owner was decided when it was started, so **every column of that page
  keeps that owner, not only the list's first node**, and ownership is decided afresh only from the list's next
  page. This is the common case, not a corner: docx4j changes span at every part boundary where the column count
  changes, so the part after the boundary always starts mid-page on a page the part before owns. The first tests
  are for it (§5).
- **A page holding only footnote bodies** (`createFootnotePages`) takes the owner of the page before it. This is
  not measured in Word (CR-031 §8); a probe can confirm it if a corpus document shows the shape.

Not every page has an owner. Without the attribute anywhere in the sequence, `getLineWidth(node)` is
`getLineWidth(node.line)`, so FOP's behaviour is unchanged.

### 3.4 Heights and pages per owner

`PageProvider`:
- **`getAvailableBPD(index, owner, firstOfOwner)`.** On the page the list started on, it returns the remaining
  height as now. On any later page, it returns the body height of a page made from the owner's master for that
  page:
  - the page's parity, from its folio, as `PageSequence` computes it;
  - blankness;
  - whether this is the owner's first page, which is true only when the owner's first box opens the page. A part
    starting mid-page therefore never uses its first-page master (CR-031 P4), and one starting at a page top does
    (P2).

  The page is built as `getLastPageIPD` already builds one (`new Page(spm, index, ...)`), measured, and not
  cached in `cachedPages`. Its height is cached by (master, parity, first, blank). The one-entry cache is keyed
  by index and owner, so one part's height cannot be returned to a candidate asking about another (CR-031
  review finding 2).
- **Adding areas.** Before each page is made, `PageBreaker` tells the provider the owner and first-page flag of
  that part. These are computed from the chosen break nodes, by the same function the breaker used, so the two
  agree. `getPage` replaces a cached page whose master differs from the owner's, through the existing
  replacement path, so `IDTracker` follows the page. The sequence's own master walk keeps its state, since
  `cacheNextPage` still calls `getNextSimplePageMaster` and only the chosen `SimplePageMaster` is substituted.
  The pages made during breaking for column counts and IPDs (`getColumn`) have the sequence's masters. That is
  harmless, because every part has the same width and column count (§3.1).

### 3.5 Blank pages, the last page, restarts

- A blank page (`force-page-count`, odd/even padding) takes the owner of the page before it, and its master is
  the owner's blank alternative, where it has one.
- `page-position="last"` alternatives in an owner's master are out of scope, since docx4j writes none. They are
  reported and ignored.
- No restart offset. CR-031 §2 measured Word's folios at a continuous restart (P3: 1, 2, 2, 3). Which master,
  odd or even, the page after such a restart takes is not measured: probe P6 is waiting for a Word run. It is a
  later step of this CR, designed once P6 is read (CR-031 phase 3).

### 3.6 Capability

`Docx4jFop.PAGE_MASTER_BY_CONTENT = "page-master-by-content"`. docx4j writes the attribute and the per-part masters
only where the capability is present; on Apache FOP it writes today's FO.

## 4. What is unchanged

FOP's output for any FO without the attribute. On the docx4j side, every document without a merged run whose
parts' vertical margins differ: CR-031 counts 34 that have one, across the corpora's 550.

## 5. Tests (FOP layout tests, before any docx4j gate)

Each is a layout-engine test (`fop/test/layoutengine/standard-testcases`), checking the page viewports' masters
and the body's position:
1. Two parts in one sequence, the second starting mid-page: that page keeps the first part's master, and the next
   page takes the second's (CR-031 P1's shape).
2. The second part starting exactly at a page top: that page takes the second part's master, and its first-page
   master where it has one (P2, P4).
3. **A span change at the boundary**: a two-column part, then a one-column part starting mid-page, both columns
   included. Every column of the continuing page keeps the first part's owner and the remaining height, and the
   next page takes the second part's master. Then the reverse, one column to two.
4. Odd and even masters of different body heights in the second part: the heights follow the folio.
5. A footnote whose body carries over to a page holding only footnotes: that page takes the owner of the page
   before it.
6. An `id` on a block that lands on a replaced page, cited by `fo:page-number-citation`: the citation gives the
   right page.
7. A part whose master is not a single unbounded alternatives, or whose body width differs: reported, and the
   sequence's master applies.
8. No attribute: the whole layout suite is unchanged (the full build).

## 6. Risks

- **Page replacement.** Pages made during breaking carry position-chosen masters, and ids are registered against
  them. Test 6 covers this; the replacement path itself is FOP's own.
- **Lists starting mid-page.** Covered by §3.3 and test 3.
- **The height cache.** Keyed by index and owner (§3.4).
- **Balanced columns at a span change** (`BalancingColumnBreakingAlgorithm`). It balances within the page the
  span change ends on, which already has its owner, so it sees the remaining height as now. Covered by test 3.
- **Footnotes and before-floats.** Their code reads the page height through the same `getLineWidth`, so they see
  the owner's height. Test 5 covers footnotes; before-floats are rare in docx4j's FO and not tested at first.
- **Odd and even masters of different heights.** The height depends on the folio as well as the owner (test 4).
- **Upkeep.** The change lives in `PageBreakingAlgorithm`, `PageProvider`, `PageBreaker` and `AbstractBreaker`,
  which change little upstream. It is carried through each merge of Apache `main` (fop/CR-009's pattern).

## 7. Estimate

About four days in the fork, give or take one, to a gated snapshot:
- the attribute, the transitions, and the node-based heights with the column and mid-page rules: about a day and
  a half;
- `PageProvider`'s per-owner heights and the area-adding replacement: about a day;
- the eight tests and what they find: about a day;
- the full build, the README and the hand-off: half a day.

The docx4j side (per-part masters, the attribute, the pre-pass measuring per-part masters, `FopCapabilities`) is
the docx4j session's, built against the name and capability in §3.1 and §3.6. The restart step (§3.5) is extra,
once P6 is read: about a day.
