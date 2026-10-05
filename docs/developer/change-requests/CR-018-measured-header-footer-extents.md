# CR-018: header and footer extents measured in FOP

Status: IMPLEMENTED 2026-10-05 on branch `CR-018-measured-region-extents` (7ae4c950a, §9); gate pending, not merged.
Designed and revised the same day after two reviews (§8). Registry key `fop/CR-018`. Capability
`measured-region-extents`. A docx4j hook (Word's rule for where the body starts, which FO leaves to the producer),
so not upstream-bound. Requested by the docx4j session for docx4j CR-031 phase 5, the companion hook at the end of
CR-031 §4.2b, which Jason asked it to take up with CR-031's carried-over items. Gated separately from CR-017, since
it changes every conversion that asks for it.

No Enterprise CR-001 §6.6 item covers the extent pre-pass; the list ends at 42. Item 25 bears on it: FOP ignores
indents on `fo:region-before` and `fo:region-after`, so docx4j puts a header's indents on the static content's own
blocks (`WordLayoutFixups.headerFooterPartIndent`). That is why measuring at the region's full width is right.

## 1. What docx4j does now, and what it costs

On every PDF conversion docx4j runs an extent pre-pass (`LayoutMasterSetBuilder.fixExtents`, `FOPAreaTreeHelper`):
1. It makes a partial deep copy of the document:
   - the body replaced by filler, with a break before each continuous section so each owns a page;
   - floating drawings removed from the headers and footers;
   - STYLEREF painted from its cached results.
2. It converts the copy to FO and renders it to an area tree.
3. It reads each simple-page-master's region-before and region-after heights from that tree.
4. It writes Word's rule into every master:
   - **at the head:** body top = max(top margin, header distance + header height), where the master's `margin-top`
     is the header distance, `extent` is the measured height, and the region-body's `margin-top` is body top less
     `margin-top`;
   - **at the foot:** the same rule mirrored.

docx4j's exceptions:
- a header or footer docx4j invents for `w:titlePg` or `w:evenAndOddHeaders` reserves nothing;
- a negative top margin fixes the body top;
- an empty footer part reserves its distance and its empty line, while an absent one reserves nothing;
- the quarter-page clamp on a footer distance stays only on a single-master merged run.

That is one extra FOP render per conversion, with the copy and its FO conversion. The pre-pass also measures a
doctored copy, which has to reach every master the real pass uses: the 1,429-page episode that 17.2.0 fixed
(`followedByPageStart`) came from a filler that did not.

## 2. What FOP does

A region's rectangle comes from its master alone. `area.Page`'s constructor asks each region for
`getViewportRectangle`:
- region-before and region-after are as tall as their `extent`;
- region-body is placed by its own margins.

The page breaker learns a page's body height from that page (`PageProvider`, `BodyRegion.getRemainingBPD`), so
the body's position must be known when the page is made, before any of its content is laid out. Static content is
laid out when a page is finished (`StaticContentLayoutManager.doLayout`), much later.

FOP already has a precedent for measuring static content early: `PageBreaker.handleFootnoteSeparator` lays out
the `xsl-footnote-separator` static content once, into a scratch block, to learn its height before breaking. Its
own comment notes that the content "could be different from page to page, but its bpd would likely be always the
same". The precedent runs while element lists are collected, when a current page exists. It is not reusable as it
stands: `StaticContentLayoutManager` picks the scratch block over the page's region only for the flow name
`xsl-footnote-separator`, in `addChildArea`, `getParentArea` and `doLayout`.

**A page is made with no current page.** `AbstractPageSequenceLayoutManager.makeNewPage` calls `finishPage`, which
sets the current page to null, and only then `createPage`; the sequence's first page is made with none too. Yet
laying out static content reads the current page in several places:
- `fo:page-number` (`PageNumberLayoutManager`, lines 77 and 115);
- `fo:retrieve-marker` (`resolveRetrieveMarker`, line 237), which then walks back over the pages already in the
  area tree;
- id registration;
- an unresolved citation such as NUMPAGES's `fo:page-number-citation-last`;
- the overflow event (`StaticContentLayoutManager`, line 143).

So the measurement needs a page of its own (§3.2).

## 3. The change

### 3.1 The attribute

`fox:extent="measured"` on `fo:region-before` or `fo:region-after`. The region is as tall as its static content
laid out at the region's width. The region-body's margin on that side becomes the larger of its stated margin and
that height, so the body clears the region, as Word's rule has it.

Without the attribute, the region keeps its stated `extent` and the body its stated margins, as now. docx4j keeps
its exceptions by not asking:
- **nothing reserved** (an invented header, an absent footer): the stated values, as today;
- **a negative top margin:** fixed values, as today;
- **an empty footer part:** asked, so that its empty line is measured.

The stated `extent` stays where no static content is given for the region in the page-sequence. One attribute,
not two: Word always clears the header, so there is no case for a measured region the body does not clear.

### 3.2 The measurement

The measurement lives in the page-sequence layout manager, which owns the current page and the static content.
`PageProvider`, which is built with only the area tree handler and the page-sequence, asks it through a callback
the manager installs. So the provider keeps to its one page-making method and the cache.

Per page-sequence and master, on the master's first use (a page made, or a height measured, with it):
1. **The measuring page.** The page is built with the stated extents and the formatted number of the page it is
   measured for: the page's printed number where the page is being made, and the number its index gives where only
   a height is wanted (CR-017's fresh pages).
2. **Its context.** It is made the page-sequence layout manager's current page for the measurement, and the page
   that was current, or none, is restored afterwards. So `fo:page-number` and everything else that reads the
   current page has one.
3. **The layout.** The page-sequence's `fo:static-content` for the region's name is laid out with a
   `StaticContentLayoutManager` into a scratch block of the region's width (the footnote-separator pattern), and
   its height read. `StaticContentLayoutManager` chooses the scratch block whenever it is given one, rather than
   by the separator's flow name.
4. **The result.** It is cached by (page-sequence, master, region). `PageProvider` is already per page-sequence,
   so CR-017's fresh-page heights need no new key.

**The measurement has no side effects.** It runs in a measuring mode, consulted where static content touches
anything beyond its own areas:
- `resolveRetrieveMarker` retrieves nothing. Otherwise a master first used mid-sequence would retrieve the last
  marker before it, and its measured height would depend on when the master was first touched. It also keeps
  `bindMarker` from cloning a marker subtree into the FO during a measurement.
- **No id or unresolved reference is registered.** They all pass through three methods of
  `AbstractPageSequenceLayoutManager`, which the mode short-circuits:
  - `associateLayoutManagerID` and `notifyEndOfLayout` for ids;
  - `addUnresolvedArea` for citations, NUMPAGES, and an `fo:basic-link` with an internal destination alike.

  The implementation confirms that nothing else registers around them.
- **No event is reported.** The user agent's event broadcaster is muted for the measurement, so line-overflow,
  missing-glyph and region-overflow events from the scratch layout are not reported a second time per master.

What it measures is the static content as FOP lays it out, which is the real header, not a copy:
- **Absolutely positioned block-containers** (floating drawings) take no height, as in a real layout.
- **Page-dependent content:**
  - `fo:page-number` takes the measuring page's number, and a citation its placeholder. Only the height is used,
    and `PageNumberLayoutManager` sets the area's height from the font, whatever the text;
  - `fo:retrieve-marker` retrieves nothing, deterministically.

  A STYLEREF header therefore measures without its text, unless the block keeps its line height. The docx4j
  session's answer, measured on the four corpora (2026-10-05):
  - five documents have a STYLEREF in a header or footer;
  - in most the field shares its line with other text, so the line's height is there anyway (9623, 2907, 12502's
    footers);
  - it stands alone on its line in 8695's three headers, 12502's header2 and probably 278's header, which rely on
    the pre-pass's painted result today.

  Where the capability is in use, docx4j gives a header or footer block whose only content is the retrieve-marker
  a zero-length `fo:leader` as a strut: a line box of the block's own font and line height, with no width, so
  alignment is unchanged. The residual is a STYLEREF whose text would wrap to a second line, which measures one
  line, per master (8695's long error text, perhaps). That waits on per-page measurement (§6).

### 3.3 Where it takes effect

`area.Page` gets the measured extents when a page is made. Its rectangles are computed in
`area.Page(SimplePageMaster)`, reached through `layoutmgr.Page` and `PageViewport` (line 107), so the extents travel
through three constructors. Each gets an overload; signatures are not changed, since `PageViewport` is public.
- region-before's and region-after's viewport rectangles take the measured heights;
- the body's rectangle takes the larger margins.

The `CTM`s are computed as now, from the adjusted rectangles. Every place `PageProvider` makes a page for a
master goes through one method, which supplies the extents:
- `cacheNextPage`;
- the fresh-page heights of CR-017 (`getFreshPageBPD`);
- `getLastPageIPD`.

So the breaker sees each page's real body height, with no other change to the breaking. The fourth `new Page(` in
`PageOwnership` (line 222) reads only the body's width and column count, which measured extents do not change.
So it stays as it is.

**Classes changed:**
- `area.Page`, `PageViewport` and `layoutmgr.Page`: the overloads;
- `PageProvider`: the one page-making method and the cache;
- `AbstractPageSequenceLayoutManager` and `PageSequenceLayoutManager`: the measurement, the callback, the measuring
  context and mode, `resolveRetrieveMarker` and the id and reference methods;
- `FOUserAgent`: the broadcaster's mute;
- `StaticContentLayoutManager`: the scratch block, and no overflow event while measuring;
- `Docx4jFop`.

Supported: the `lr-tb` and `rl-tb` writing modes and `reference-orientation` 0, which is what docx4j writes.
Elsewhere the attribute is reported once and the stated values apply.

### 3.4 Capability

`Docx4jFop.MEASURED_REGION_EXTENTS = "measured-region-extents"`. docx4j writes the attribute, and drops its
pre-pass, only where the capability is present. On Apache FOP, or a fork without it, it keeps the pre-pass, as
now.

## 4. What is unchanged

Any FO without the attribute. The layout of static content on each page, which still happens when the page is
finished.

## 5. Tests (FOP layout tests)

1. A header of three lines, `fox:extent="measured"`: region-before is three lines tall, and the body starts below
   it where the stated margin is smaller, at the stated margin where it is larger.
2. The same at the foot. Region-after is anchored at the page's foot (`reldims.bpd - extent`), so the test also
   asserts its top edge, which a measured extent moves.
3. Two masters with different headers: each measured on its own, and the page breaker uses each page's body height
   (with CR-017's per-part masters).
4. One master in two page-sequences with different static content: measured per page-sequence.
5. A region with the attribute and no static content: its stated extent.
6. A header with an absolutely positioned block-container and a page number: the block-container takes no height,
   and the page number its line.
7. No attribute: the whole layout suite unchanged.
8. A header with `fo:page-number`, one with `fo:page-number-citation-last` (NUMPAGES), and a footer whose only
   content is `fo:page-number` (the commonest footer), on the sequence's first page: measured, nothing thrown, the
   citation resolved on the real pages, and no event reported twice.
9. A retrieve-marker header on a master first used after pages that carry markers: measured as with none, and the
   real header on its page still retrieves the marker.
10. An id on a header block, cited from the body: the citation gives the real page, not the measuring page.
11. A blank page whose master has a measured region.

## 6. Not addressed

- **Per-page measurement.** A STYLEREF header varies from page to page, and measuring it per page is circular: its
  markers come from the page's body, whose height depends on the header. Per master first, as the pre-pass does,
  and per page later, once a Word probe shows what Word does.
- `fo:region-start` and `fo:region-end`, which docx4j does not write.

## 7. Estimate

About four to five days in the fork to a gated snapshot:
- the measuring context and mode, and the measurement with its cache: about two days (the review's findings 1 to 3
  are the real work);
- the extents through the three constructors, and the one page-making method in `PageProvider`: about a day;
- the eleven tests: about a day and a half;
- the build and the hand-off: half a day.

The docx4j side writes the attribute instead of running the pre-pass, with its exceptions kept by not asking; that
is the docx4j session's. The gate it describes: every master's extents equal the pre-pass's to the point, except
where the pre-pass's doctored copy differs from the real header; render time measured; nothing else moving.

## 8. Review, 2026-10-05

A review of the first version (a reading against `2.11-docx4j.5`, nothing run) found the rule and the seam right,
and the measurement unable to run where §3.3 put it. Each finding was checked against the code, and each holds:
1. **No current page when a page is made** (`finishPage` sets it to null before `createPage`), and static content
   reads it in five places. A PAGE field, the commonest header, would throw on page 1. Now §2 and §3.2: the
   measuring page is made the current page for the measurement.
2. **`resolveRetrieveMarker` does not retrieve nothing.** It walks back over earlier pages, so the height would
   depend on when a master was first touched. Now §3.2: retrieval is suppressed while measuring.
3. **Side effects.** An id registered against the measuring page, and an unresolved citation attached to it. Now
   §3.2's measuring mode, with tests 8 and 10.
4. **The footnote-separator pattern keys on its flow name** in three places of `StaticContentLayoutManager`. Now §2
   and §3.2, and the class is listed.
5. **The rectangles are made in `area.Page`, through three constructors.** Now §3.3, with overloads. `PageOwnership`'s
   `new Page(` is noted as unaffected.
6. **No §6.6 item was cited.** None covers the pre-pass; item 25 bears on it. Now the header.
7. **The estimate was light.** Three to four days became four to five.

The review's added tests are tests 8 to 11, and test 2's footer edge. It confirmed and this revision keeps:
- the arithmetic (max of the stated body margin and the measured height is Word's rule);
- caching per page-sequence;
- keeping the extents out of the FO tree, in `area.Page`;
- the registry entry.

### 8.1 Second review, 2026-10-05

It found the revision answers all seven findings and CR-018 ready to start, with four points to settle. Each was
checked against the code, and each holds:
1. **`PageProvider` cannot reach the layout manager.** It is built with the area tree handler and the page-sequence
   only (`PageSequenceLayoutManager` line 78). Now §3.2: the measurement lives in the layout manager, and the
   provider asks through a callback.
2. **The fresh-page path builds its page with an empty number string.** Now §3.2: the measuring page carries a
   formatted number, and only the height is used, which `PageNumberLayoutManager` takes from the font.
3. **The mode should cover every unresolved reference and every event.** A basic link with an internal destination
   uses `addUnresolvedArea` too, and line-overflow or missing-glyph events would be reported twice. Now §3.2: the
   three methods ids and references pass through, and a muted broadcaster.
4. **Test 8 should include a footer whose only content is the page number.** Now it does.

It confirmed:
- setting and restoring the current page works both inside `makeNewPage`, where it is null, and inside the
  breaker;
- suppressing retrieval also keeps `bindMarker` from cloning marker subtrees;
- the constructor overloads;
- the estimate of four to five days.

## 9. Implementation, 2026-10-05

Branch `CR-018-measured-region-extents`, cut from `2.11-docx4j.5` at 44b7cf5a6; the change is 7ae4c950a. The full
build passes on it: the fop-core suite gives 3840 tests and 0 failures, and checkstyle and spotbugs are clean.

It is §3 as designed, with these differences, each found while building it:
- **`getLastPageIPD` still makes its page directly.** It reads the body's width, which measured extents do not
  change in the supported writing modes. So `PageProvider`'s one page-making method serves `cacheNextPage` and
  `getFreshPageBPD`. The measuring page itself is made with `new Page(` in the layout manager, so a measurement
  never asks for itself.
- **`StaticContentLayoutManager` has no event change.** The muted broadcaster (`FOUserAgent.setEventsMuted`, which
  returns the previous state) silences its region-overflow event with the rest. Its one change is the scratch
  block, used whenever it is given one.
- **The mode short-circuits four methods, not three, and marker retrieval.** `addIDToPage` registers an id as well
  as `associateLayoutManagerID` and `notifyEndOfLayout`. Nothing else in the layout managers registers an id or a
  reference. The other registrations are the page-sequence's own id, an external document's, and the area tree
  handler's document-level ones (bookmarks and the like), none of which static content reaches.
- **The cache** holds each master's extents, both regions together, in a map `PageProvider` clears with its other
  per-sequence state.
- **The unsupported case.** A master in another writing mode or at a nonzero reference-orientation gets a log
  warning when it is measured (so once per master and page-sequence), and its stated extents. A region with its
  own nonzero reference-orientation keeps its stated extent without a warning.

What building it found:
- **Without the mode, a link into a header fails the rendering.** An `fo:basic-link` to an id in a header resolves
  to the measuring page, which is never rendered and has no key, and writing the area tree throws ("No page key
  set on the PageViewport"). So the mode prevents a failure, not only a wrong page number.
- **Within one page-sequence, a master is first measured while the page breaker runs**, before any of the
  sequence's pages reach the area tree. There, retrieval left on would find no marker either, so test 9 as §5
  describes it could not tell the mode from its absence. The mode decides where earlier pages are already in the
  area tree: a marker from an earlier page-sequence under `retrieve-boundary="document"`, which test 9 now uses,
  or, by reading the code, a master first used after the sequence's content pages, as a blank page's is.

**Tests.**

| §5 | test |
|---|---|
| 1, 4, 5 | `measured-region-extents_header.xml` |
| 2 | `measured-region-extents_footer.xml` |
| 3 | `measured-region-extents_parts.xml` |
| 6 | `measured-region-extents_absolute.xml` |
| 7 | the layout suite, unchanged |
| 8 | `measured-region-extents_page-fields.xml`; events in `MeasuredRegionExtentsTestCase`, since a layout test can check that an event occurs, not how often |
| 9 | `measured-region-extents_retrieve-marker.xml`, the header's marker retrieved from an earlier page-sequence |
| 10 | `measured-region-extents_id.xml`, a link as well as a citation |
| 11 | `measured-region-extents_blank.xml` |

Every layout test fails without the change. With the change and the measuring mode switched off, the id test
cannot write its area tree and the retrieve-marker test measures two lines where it should measure one. With the
broadcaster not muted, `MeasuredRegionExtentsTestCase` counts four line overflows where FOP reports three.
`Docx4jHooksTestCase` counts 17 capabilities.

**The gate** is the docx4j session's, as §7 describes. First the snapshot without the attribute: nothing moves.
Then docx4j writes the attribute and drops its pre-pass where the capability is present. Each master's extents
should equal the pre-pass's to the point, except where the pre-pass's doctored copy differs from the real header.
Render time is measured, and nothing else should move.
