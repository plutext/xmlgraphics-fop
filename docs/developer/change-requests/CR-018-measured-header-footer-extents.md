# CR-018: header and footer extents measured in FOP

Status: PROPOSED 2026-10-05; design and estimate, not started. Registry key `fop/CR-018`. Capability
`measured-region-extents`. A docx4j hook (Word's rule for where the body starts, which FO leaves to the producer),
so not upstream-bound. Requested by the docx4j session for docx4j CR-031 phase 5, the companion hook at the end of
CR-031 §4.2b, which Jason asked it to take up with CR-031's carried-over items. Gated separately from CR-017, since
it changes every conversion that asks for it.

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
same".

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

Per page-sequence and master, on the master's first use (a page made or measured with it):
- the page-sequence's `fo:static-content` for the region's name is laid out with a `StaticContentLayoutManager`
  into a scratch block of the region's width (the footnote-separator pattern), and its height read;
- the result is cached by (page-sequence, master, region). It is per page-sequence, since two page-sequences can
  give one region-name different static content.

What it measures is the static content as FOP lays it out, which is the real header, not a copy:
- **Absolutely positioned block-containers** (floating drawings) take no height, as in a real layout.
- **Page-dependent content** is laid out as on a page with no markers:
  - `fo:page-number` and citations take their width, not their height, so the height is right;
  - `fo:retrieve-marker` retrieves nothing.

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

`area.Page` gets the measured extents when a page is made:
- region-before's and region-after's viewport rectangles take the measured heights;
- the body's rectangle takes the larger margins.

The `CTM`s are computed as now, from the adjusted rectangles. Every place `PageProvider` makes a page for a
master goes through one method, which supplies the extents:
- `cacheNextPage`;
- the fresh-page heights of CR-017 (`getFreshPageBPD`);
- `getLastPageIPD`.

So the breaker sees each page's real body height, with no other change to the breaking.

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
2. The same at the foot.
3. Two masters with different headers: each measured on its own, and the page breaker uses each page's body height
   (with CR-017's per-part masters).
4. One master in two page-sequences with different static content: measured per page-sequence.
5. A region with the attribute and no static content: its stated extent.
6. A header with an absolutely positioned block-container and a page number: the block-container takes no height,
   and the page number its line.
7. No attribute: the whole layout suite unchanged.

## 6. Not addressed

- **Per-page measurement.** A STYLEREF header varies from page to page, and measuring it per page is circular: its
  markers come from the page's body, whose height depends on the header. Per master first, as the pre-pass does,
  and per page later, once a Word probe shows what Word does.
- `fo:region-start` and `fo:region-end`, which docx4j does not write.

## 7. Estimate

About three to four days in the fork to a gated snapshot:
- the measurement with its cache: about a day;
- the extents in `area.Page` and the one page-making method in `PageProvider`: about a day;
- the seven tests: about a day;
- the build and the hand-off: half a day.

The docx4j side writes the attribute instead of running the pre-pass, with its exceptions kept by not asking; that
is the docx4j session's. The gate it describes: every master's extents equal the pre-pass's to the point, except
where the pre-pass's doctored copy differs from the real header; render time measured; nothing else moving.
