/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* Modified by Plutext Pty Ltd for the docx4j FO renderer (docx4j-fo-renderer), a modified distribution derived from
 * Apache FOP 2.11: hook page-master-by-content, each page made with the master of the part that owns it, and the
 * height of a page asked for with its owner (fop/CR-017); and hook page-number-restart, each page numbered by a
 * running count that a part may restart (fop/CR-017.2); and hook measured-region-extents, each page cached or
 * measured for its height made through one method, with its master's header and footer extents measured where the
 * master asks (fop/CR-018); and columns of unequal width, a part's inline size compared with its neighbour
 * column's (fop/CR-026). See README.md, "Changes from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.layoutmgr;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.area.AreaTreeHandler;
import org.apache.fop.area.BodyRegion;
import org.apache.fop.area.MeasuredExtents;
import org.apache.fop.area.PageViewport;
import org.apache.fop.area.Span;
import org.apache.fop.fo.Constants;
import org.apache.fop.fo.pagination.PageProductionException;
import org.apache.fop.fo.pagination.PageSequence;
import org.apache.fop.fo.pagination.Region;
import org.apache.fop.fo.pagination.RegionBody;
import org.apache.fop.fo.pagination.SimplePageMaster;

/**
 * <p>This class delivers Page instances. It also caches them as necessary.
 * </p>
 * <p>Additional functionality makes sure that surplus instances that are requested by the
 * page breaker are properly discarded, especially in situations where hard breaks cause
 * blank pages. The reason for that: The page breaker sometimes needs to preallocate
 * additional pages since it doesn't know exactly until the end how many pages it really needs.
 * </p>
 */
public class PageProvider implements Constants {

    private Log log = LogFactory.getLog(PageProvider.class);

    /** Indices are evaluated relative to the first page in the page-sequence. */
    public static final int RELTO_PAGE_SEQUENCE = 0;
    /** Indices are evaluated relative to the first page in the current element list. */
    public static final int RELTO_CURRENT_ELEMENT_LIST = 1;

    private int startPageOfPageSequence;
    private int startPageOfCurrentElementList;
    private int startColumnOfCurrentElementList;
    private boolean spanAllForCurrentElementList;
    private List<Page> cachedPages = new java.util.ArrayList<Page>();

    private int lastPageIndex = -1;
    private int indexOfCachedLastPage = -1;

    //Cache to optimize getAvailableBPD() calls
    private int lastRequestedIndex = -1;
    private int lastReportedBPD = -1;
    private PageOwnership.Part lastRequestedOwner;
    private boolean lastRequestedOwnerFirst;

    /*
     * docx4j-fo-renderer hook page-master-by-content (fop/CR-017): the part of the page-sequence that owns
     * each page handed out for areas, by page number; the owner the next page is to be made for; the body
     * height of a fresh page by master; and the column of each part of the current element list.
     */
    private PageOwnership ownership;
    private final Map<Integer, OwnerRecord> owners = new HashMap<Integer, OwnerRecord>();
    private OwnerRecord pendingOwner;
    private final Map<SimplePageMaster, Integer> freshPageBPDs = new IdentityHashMap<SimplePageMaster, Integer>();
    private final List<Column> partColumns = new java.util.ArrayList<Column>();
    /** Hook page-number-restart: the number each page handed out prints, by page index, where parts restart it. */
    private final Map<Integer, Integer> printedNumbers = new HashMap<Integer, Integer>();

    /**
     * Hook measured-region-extents (fop/CR-018): measures a master's region-before and region-after from the
     * page-sequence's static content. The page-sequence layout manager, which owns the current page and the
     * static content, installs it.
     */
    interface RegionMeasurer {
        /**
         * @param spm the master
         * @param pageNumber the number of the page the master is first used for
         * @param pageNumberString that page's printed number
         * @param blank whether that page is blank
         * @return the measured extents
         */
        MeasuredExtents measure(SimplePageMaster spm, int pageNumber, String pageNumberString, boolean blank);
    }

    private RegionMeasurer regionMeasurer;
    /** Hook measured-region-extents: each master's measured extents, measured on its first use. */
    private final Map<SimplePageMaster, MeasuredExtents> measuredExtents
            = new IdentityHashMap<SimplePageMaster, MeasuredExtents>();

    /** The part owning a page, and whether the page is that part's first. */
    static final class OwnerRecord {
        final PageOwnership.Part part;
        final boolean first;

        OwnerRecord(PageOwnership.Part part, boolean first) {
            this.part = part;
            this.first = first;
        }
    }

    /**
     * AreaTreeHandler which activates the PSLM and controls
     * the rendering of its pages.
     */
    private AreaTreeHandler areaTreeHandler;

    /**
     * fo:page-sequence formatting object being
     * processed by this class
     */
    private PageSequence pageSeq;

    protected boolean skipPagePositionOnly;
    protected FOUserAgent foUserAgent;

    /**
     * Main constructor.
     * @param ath the area tree handler
     * @param ps The page-sequence the provider operates on
     */
    public PageProvider(AreaTreeHandler ath, PageSequence ps) {
        this.areaTreeHandler = ath;
        this.pageSeq = ps;
        this.startPageOfPageSequence = ps.getStartingPageNumber();
        foUserAgent = ath.getUserAgent();
        this.ownership = new PageOwnership(ps);
    }

    public void initialize() {
        cachedPages.clear();
        ownership = new PageOwnership(pageSeq);
        owners.clear();
        pendingOwner = null;
        freshPageBPDs.clear();
        partColumns.clear();
        printedNumbers.clear();
        measuredExtents.clear();
    }

    /**
     * Installs the measurer of masters' header and footer extents (hook measured-region-extents).
     * @param measurer the measurer
     */
    void setRegionMeasurer(RegionMeasurer measurer) {
        this.regionMeasurer = measurer;
    }

    /**
     * Makes a page of a master: every page this provider caches, and every fresh page whose height it measures, is
     * made here, so a master asking for its header and footer extents to be measured (fox:extent="measured", hook
     * measured-region-extents) has them measured on its first use and applied to every page made with it.
     * {@link #getLastPageIPD()} makes its own, since it reads only a column's width, which they do not change.
     */
    private Page makePage(SimplePageMaster spm, int pageNumber, String pageNumberString, boolean blank,
            boolean spanAll, boolean isPagePositionOnly) {
        MeasuredExtents extents = null;
        if (regionMeasurer != null && MeasuredExtents.isRequested(spm)) {
            extents = measuredExtents.get(spm);
            if (extents == null) {
                extents = regionMeasurer.measure(spm, pageNumber, pageNumberString, blank);
                measuredExtents.put(spm, extents);
            }
        }
        return new Page(spm, pageNumber, pageNumberString, blank, spanAll, isPagePositionOnly, extents);
    }

    /**
     * Hook page-number-restart: the number a page prints, which a part restarting its page numbers sets apart
     * from the page's index.
     * @param index a page's index
     * @return the number it prints
     */
    public int getPrintedPageNumber(int index) {
        Integer printed = printedNumbers.get(index);
        return (printed == null) ? index : printed;
    }

    /** @return which part of the page-sequence owns each page (hook page-master-by-content) */
    PageOwnership getOwnership() {
        return ownership;
    }

    /**
     * The owner the next page handed out for areas is made for, unless it is blank; null to clear it, after
     * which a page takes the owner of the page before it (hook page-master-by-content).
     * @param part the owning part, or null for the page-sequence's own masters
     * @param first whether the page is the part's first
     */
    void setPendingOwner(PageOwnership.Part part, boolean first) {
        this.pendingOwner = new OwnerRecord(part, first);
    }

    /** Clears the pending owner. */
    void clearPendingOwner() {
        this.pendingOwner = null;
    }

    /**
     * @param pageNumber a page number
     * @return the owner recorded for the page when it was handed out for areas, or null
     */
    OwnerRecord getOwnerRecord(int pageNumber) {
        return owners.get(pageNumber);
    }

    /**
     * Records the owner of a page handed out before the parts were found (the page-sequence's first page,
     * made before its page breaking starts), as any page handed out later is recorded.
     * @param pageNumber the page's number
     */
    void recordHandedOutPage(int pageNumber) {
        if (ownership.isActive() && !owners.containsKey(pageNumber)) {
            recordOwner(false, pageNumber);
        }
    }

    /**
     * The page breaker notifies the provider about the page number an element list starts
     * on so it can later retrieve PageViewports relative to this first page.
     * @param startPage the number of the first page for the element list.
     * @param startColumn the starting column number for the element list.
     * @param spanAll true if the current element list is for a column-spanning section
     */
    public void setStartOfNextElementList(int startPage, int startColumn, boolean spanAll) {
        if (log.isDebugEnabled()) {
            log.debug("start of the next element list is:"
                    + " page=" + startPage + " col=" + startColumn
                    + (spanAll ? ", column-spanning" : ""));
        }
        this.startPageOfCurrentElementList = startPage - startPageOfPageSequence + 1;
        this.startColumnOfCurrentElementList = startColumn;
        this.spanAllForCurrentElementList = spanAll;
        //Reset Cache
        this.lastRequestedIndex = -1;
        this.lastReportedBPD = -1;
        this.partColumns.clear();
    }

    /**
     * Sets the index of the last page. This is done as soon as the position of the last page
     * is known or assumed.
     * @param index the index relative to the first page in the page-sequence
     */
    public void setLastPageIndex(int index) {
        this.lastPageIndex = index;
    }

    /**
     * Returns the available BPD for the part/page indicated by the index parameter.
     * The index is the part/page relative to the start of the current element list.
     * This method takes multiple columns into account.
     * @param index zero-based index of the requested part/page
     * @return the available BPD
     */
    public int getAvailableBPD(int index) {
        return getAvailableBPD(index, null, false);
    }

    /**
     * The available BPD for a part, where the page it lies on is owned by a part of the page-sequence
     * (hook page-master-by-content). A page already handed out for areas has its own height; any other
     * is measured as a fresh page of the owner's master.
     * @param index zero-based index of the requested part/page
     * @param owner the part owning the page, or null for the page-sequence's own masters
     * @param first whether the page is the owner's first
     * @return the available BPD
     */
    int getAvailableBPD(int index, PageOwnership.Part owner, boolean first) {
        //Special optimization: There may be many equal calls by the BreakingAlgorithm
        if (this.lastRequestedIndex == index && this.lastRequestedOwner == owner
                && this.lastRequestedOwnerFirst == first) {
            if (log.isTraceEnabled()) {
                log.trace("getAvailableBPD(" + index + ") -> (cached) " + lastReportedBPD);
            }
            return this.lastReportedBPD;
        }
        int pageIndexTmp = index;
        int pageIndex = 0;
        int colIndex = startColumnOfCurrentElementList;
        Page page = getPage(
                false, pageIndex, RELTO_CURRENT_ELEMENT_LIST);
        while (pageIndexTmp > 0) {
            colIndex++;
            if (colIndex >= page.getPageViewport().getCurrentSpan().getColumnCount()) {
                colIndex = 0;
                pageIndex++;
                page = getPage(false, pageIndex, RELTO_CURRENT_ELEMENT_LIST);
                BodyRegion br = page.getPageViewport().getBodyRegion();
                if (!pageSeq.getMainFlow().getFlowName().equals(br.getRegionName())) {
                    pageIndexTmp++;
                }
            }
            pageIndexTmp--;
        }
        this.lastRequestedIndex = index;
        this.lastRequestedOwner = owner;
        this.lastRequestedOwnerFirst = first;
        this.lastReportedBPD = page.getPageViewport().getBodyRegion().getRemainingBPD();
        // the page the element list starts on already exists, with its own master: its height is its own
        if (owner != null && pageIndex > 0) {
            int pageNumber = startPageOfCurrentElementList + pageIndex + startPageOfPageSequence - 1;
            if (!owners.containsKey(pageNumber)) {
                SimplePageMaster spm = owner.masterFor(pageNumber, first, false);
                if (spm != null && spm != page.getSimplePageMaster()) {
                    this.lastReportedBPD = getFreshPageBPD(spm, pageNumber);
                }
            }
        }
        if (log.isTraceEnabled()) {
            log.trace("getAvailableBPD(" + index + ") -> " + lastReportedBPD);
        }
        return this.lastReportedBPD;
    }

    /** The body height of a fresh page of a master, measured without caching the page. */
    private int getFreshPageBPD(SimplePageMaster spm, int pageNumber) {
        Integer bpd = freshPageBPDs.get(spm);
        if (bpd == null) {
            Page page = makePage(spm, pageNumber, pageSeq.makeFormattedPageNumber(getPrintedPageNumber(pageNumber)),
                    false, spanAllForCurrentElementList, false);
            bpd = page.getPageViewport().getBodyRegion().getRemainingBPD();
            freshPageBPDs.put(spm, bpd);
        }
        return bpd;
    }

    /**
     * @param index the index of a part of the current element list
     * @return whether the part is the first column of its page (hook page-master-by-content)
     */
    boolean partOpensPage(int index) {
        return getPartColumn(index).colIndex == 0;
    }

    /**
     * @param index the index of a part of the current element list
     * @return the number of the page the part lies on (hook page-master-by-content)
     */
    int pageNumberOfPart(int index) {
        return startPageOfCurrentElementList + getPartColumn(index).pageIndex + startPageOfPageSequence - 1;
    }

    private Column getPartColumn(int index) {
        while (partColumns.size() <= index) {
            partColumns.add(null);
        }
        Column column = partColumns.get(index);
        if (column == null) {
            column = getColumn(index);
            partColumns.set(index, column);
        }
        return column;
    }

    private static class Column {

        final Page page;

        final int pageIndex;

        final int colIndex;

        final int columnCount;

        Column(Page page, int pageIndex, int colIndex, int columnCount) {
            this.page = page;
            this.pageIndex = pageIndex;
            this.colIndex = colIndex;
            this.columnCount = columnCount;
        }

    }

    private Column getColumn(int index) {
        int columnCount = 0;
        int colIndex = startColumnOfCurrentElementList + index;
        int pageIndex = -1;
        Page page;
        do {
            colIndex -= columnCount;
            pageIndex++;
            page = getPage(false, pageIndex, RELTO_CURRENT_ELEMENT_LIST);
            if (page.getPageViewport().getPage() != null) {
                columnCount = page.getPageViewport().getCurrentSpan().getColumnCount();
            }
        } while (colIndex >= columnCount);
        return new Column(page, pageIndex, colIndex, columnCount);
    }

    /**
     * Compares the IPD of the given part with the following one.
     *
     * @param index index of the current part
     * @return a negative integer, zero or a positive integer as the current IPD is less
     * than, equal to or greater than the IPD of the following part
     */
    public int compareIPDs(int index) {
        Column column = getColumn(index);
        PageViewport pv = column.page.getPageViewport();
        if (column.colIndex + 1 < column.columnCount) {
            // CR-026: the columns of one page may differ in width, so a column is compared with its neighbour
            Span span = pv.getCurrentSpan();
            return span.getColumnWidth(column.colIndex) - span.getColumnWidth(column.colIndex + 1);
        } else {
            PageViewport nextPV = getPage(false, column.pageIndex + 1, RELTO_CURRENT_ELEMENT_LIST)
                    .getPageViewport();
            if (spansAllColumns(pv) || spansAllColumns(nextPV)) {
                // a span="all" list, or a page replaced under it: as Apache FOP compares
                return pv.getBodyRegion().getColumnIPD() - nextPV.getBodyRegion().getColumnIPD();
            }
            return pv.getCurrentSpan().getColumnWidth(column.colIndex)
                    - nextPV.getCurrentSpan().getColumnWidth(0);
        }
    }

    /** @return whether the page's current span is a span="all" over a multi-column body (CR-026) */
    private static boolean spansAllColumns(PageViewport pv) {
        return pv.getCurrentSpan().getColumnCount() != pv.getBodyRegion().getColumnCount();
    }

    /**
     * Checks if a break at the passed index would start a new page
     * @param index the index of the element before the break
     * @return  {@code true} if the break starts a new page
     */
    boolean startPage(int index) {
        return getColumn(index).colIndex == 0;
    }

    /**
     * Checks if a break at the passed index would end a page
     * @param index the index of the element before the break
     * @return  {@code true} if the break ends a page
     */
    boolean endPage(int index) {
        Column column = getColumn(index);
        return column.colIndex == column.columnCount - 1;
    }

    /**
     * Obtain the applicable column-count for the element at the
     * passed index
     * @param index the index of the element
     * @return  the number of columns
     */
    int getColumnCount(int index) {
        return getColumn(index).columnCount;
    }

    /**
     * Returns the part index (0&lt;x&lt;partCount) which denotes the first part on the last page
     * generated by the current element list.
     * @param partCount Number of parts determined by the breaking algorithm
     * @return the requested part index
     */
    public int getStartingPartIndexForLastPage(int partCount) {
        int lastPartIndex = partCount - 1;
        return lastPartIndex - getColumn(lastPartIndex).colIndex;
    }

    Page getPageFromColumnIndex(int columnIndex) {
        return getColumn(columnIndex).page;
    }

    /**
     * Returns a Page.
     * @param isBlank true if this page is supposed to be blank.
     * @param index Index of the page (see relativeTo)
     * @param relativeTo Defines which value the index parameter should be evaluated relative
     * to. (One of PageProvider.RELTO_*)
     * @return the requested Page
     */
    public Page getPage(boolean isBlank, int index, int relativeTo) {
        if (relativeTo == RELTO_PAGE_SEQUENCE) {
            if (ownership.isActive()) {
                recordOwner(isBlank, index);
            }
            return getPage(isBlank, index);
        } else if (relativeTo == RELTO_CURRENT_ELEMENT_LIST) {
            int effIndex = startPageOfCurrentElementList + index;
            effIndex += startPageOfPageSequence - 1;
            return getPage(isBlank, effIndex);
        } else {
            throw new IllegalArgumentException(
                    "Illegal value for relativeTo: " + relativeTo);
        }
    }

    /**
     * Records the owner of a page handed out for areas: the pending owner where one is set and the page is
     * not blank, else the owner already recorded for it, else the owner of the page before it, the page not
     * being its first (hook page-master-by-content).
     */
    private void recordOwner(boolean isBlank, int index) {
        OwnerRecord record;
        if (pendingOwner != null && !isBlank) {
            record = pendingOwner;
        } else if (owners.containsKey(index)) {
            return;
        } else {
            OwnerRecord previous = owners.get(index - 1);
            record = new OwnerRecord(previous == null ? null : previous.part, false);
        }
        owners.put(index, record);
        recordPrintedNumber(index, record);
    }

    /**
     * Hook page-number-restart (fop/CR-017.2): the number a page handed out prints, one more than the page
     * before's, except where a part restarting its numbers at S begins its count, as Word counts a continuous
     * section that restarts. A part opening its page prints S there. A part starting part-way down a page leaves
     * that page its owner's number, and its count begins on that page, so its first own page prints S + 1;
     * with parity kept, the count begins there only where S has the parity of the number that page prints,
     * else on the first own page, which then prints S.
     */
    private void recordPrintedNumber(int index, OwnerRecord record) {
        Integer before = printedNumbers.get(index - 1);
        int printed = (before != null) ? before + 1 : index;
        PageOwnership.Part part = record.part;
        if (part != null && part.getRestart() >= 0) {
            OwnerRecord previous = owners.get(index - 1);
            boolean firstOwnPage = previous == null || previous.part != part;
            int restart = part.getRestart();
            if (record.first) {
                printed = restart;
            } else if (firstOwnPage) {
                int startPagePrints = (before != null) ? before : index - 1;
                boolean sameParity = (restart % 2) == (startPagePrints % 2);
                printed = (!part.keepsParity() || sameParity) ? restart + 1 : restart;
            }
        }
        if (printed != index || !printedNumbers.isEmpty()) {
            printedNumbers.put(index, printed);
        }
    }

    /** The master the owner recorded for a page gives it, or null for the page-sequence's own. */
    private SimplePageMaster getOwnerMaster(boolean isBlank, int index) {
        OwnerRecord record = owners.get(index);
        if (record == null || record.part == null) {
            return null;
        }
        return record.part.masterFor(index, record.first, isBlank);
    }

    /**
     * Returns a Page.
     * @param isBlank true if the Page should be a blank one
     * @param index the Page's index
     * @return a Page instance
     */
    protected Page getPage(boolean isBlank, int index) {
        boolean isLastPage = (lastPageIndex >= 0) && (index == lastPageIndex);
        if (log.isTraceEnabled()) {
            log.trace("getPage(" + index + " " + (isBlank ? "blank" : "non-blank")
                    + (isLastPage ? " <LAST>" : "") + ")");
        }
        int intIndex = index - startPageOfPageSequence;
        if (log.isTraceEnabled()) {
            if (isBlank) {
                log.trace("blank page requested: " + index);
            }
            if (isLastPage) {
                log.trace("last page requested: " + index);
            }
        }
        if (intIndex > cachedPages.size()) {
            throw new UnsupportedOperationException("Cannot handle holes in page cache");
        }
        SimplePageMaster ownerMaster = getOwnerMaster(isBlank, index);
        if (intIndex == cachedPages.size()) {
            if (log.isTraceEnabled()) {
                log.trace("Caching " + index);
            }
            cacheNextPage(index, isBlank, isLastPage, this.spanAllForCurrentElementList, ownerMaster);
        }
        Page page = cachedPages.get(intIndex);
        boolean replace = false;
        if (ownerMaster != null && page.getSimplePageMaster() != ownerMaster) {
            log.debug("master doesn't match the page's owner. Replacing PageViewport.");
            replace = true;
        }
        if (printedNumbers.containsKey(index) && !pageSeq.makeFormattedPageNumber(getPrintedPageNumber(index))
                .equals(page.getPageViewport().getPageNumberString())) {
            log.debug("page number doesn't match the page's count. Replacing PageViewport.");
            replace = true;
        }
        if (page.getPageViewport().isBlank() != isBlank) {
            log.debug("blank condition doesn't match. Replacing PageViewport.");
            replace = true;
        }
        if (page.getPageViewport().getPage() != null
                && page.getPageViewport().getCurrentSpan().getColumnCount() == 1
                && !this.spanAllForCurrentElementList) {
            RegionBody rb = (RegionBody)page.getSimplePageMaster().getRegion(Region.FO_REGION_BODY);
            int colCount = rb.getColumnCount();
            if (colCount > 1) {
                log.debug("Span doesn't match. Replacing PageViewport.");
                replace = true;
            }
        }
        if ((isLastPage && indexOfCachedLastPage != intIndex)
                || (!isLastPage && indexOfCachedLastPage >= 0)) {
            log.debug("last page condition doesn't match. Replacing PageViewport.");
            replace = true;
            indexOfCachedLastPage = (isLastPage ? intIndex : -1);
        }
        if (replace) {
            discardCacheStartingWith(intIndex);
            PageViewport oldPageVP = page.getPageViewport();
            page = cacheNextPage(index, isBlank, isLastPage, this.spanAllForCurrentElementList, ownerMaster);
            PageViewport newPageVP = page.getPageViewport();
            newPageVP.replace(oldPageVP);
            this.areaTreeHandler.getIDTracker().replacePageViewPort(oldPageVP, newPageVP);
        }
        return page;
    }

    protected void discardCacheStartingWith(int index) {
        while (index < cachedPages.size()) {
            this.cachedPages.remove(cachedPages.size() - 1);
            if (!pageSeq.goToPreviousSimplePageMaster()) {
                log.warn("goToPreviousSimplePageMaster() on the first page called!");
            }
        }
        forgetPagesAfter(index + startPageOfPageSequence);
    }

    /**
     * Hook page-master-by-content: forgets the owners and printed numbers recorded for the pages after one, which
     * are to be made again. The page itself keeps its record, since a replacement in {@link #getPage(boolean, int)}
     * discards the page just recorded. Pages are handed out in order, and both discards start at the current page
     * or later, so no record lies there today; this keeps it so.
     */
    private void forgetPagesAfter(int pageNumber) {
        for (Iterator<Integer> it = owners.keySet().iterator(); it.hasNext();) {
            if (it.next() > pageNumber) {
                it.remove();
            }
        }
        for (Iterator<Integer> it = printedNumbers.keySet().iterator(); it.hasNext();) {
            if (it.next() > pageNumber) {
                it.remove();
            }
        }
    }

    private Page cacheNextPage(int index, boolean isBlank, boolean isLastPage, boolean spanAll,
            SimplePageMaster ownerMaster) {
        String pageNumberString = pageSeq.makeFormattedPageNumber(getPrintedPageNumber(index));
        boolean isFirstPage = (startPageOfPageSequence == index);
        boolean skipPagePositionOnlyCheck = skipPagePositionOnly && foUserAgent.isSkipPagePositionOnlyAllowed();
        SimplePageMaster spm = pageSeq.getNextSimplePageMaster(
                index, isFirstPage, isLastPage, isBlank, skipPagePositionOnlyCheck);
        boolean isPagePositionOnly = pageSeq.hasPagePositionOnly() && !skipPagePositionOnly;
        if (isPagePositionOnly) {
            spm = pageSeq.getNextSimplePageMaster(index, isFirstPage, true, isBlank, false);
        }
        if (ownerMaster != null) {
            // hook page-master-by-content: the master of the part owning the page; the page-sequence's
            // own masters were still walked, so their state stays as FOP keeps it
            spm = ownerMaster;
        }
        Page page = makePage(spm, index, pageNumberString, isBlank, spanAll, isPagePositionOnly);
        //Set unique key obtained from the AreaTreeHandler
        page.getPageViewport().setKey(areaTreeHandler.generatePageViewportKey());
        page.getPageViewport().setForeignAttributes(spm.getForeignAttributes());
        page.getPageViewport().setWritingModeTraits(pageSeq);
        cachedPages.add(page);
        if (isLastPage) {
            pageSeq.getRoot().setLastSeq(pageSeq);
        } else if (!isFirstPage) {
            pageSeq.getRoot().setLastSeq(null);
        }
        return page;
    }

    public int getIndexOfCachedLastPage() {
        return indexOfCachedLastPage;
    }

    public int getLastPageIndex() {
        return lastPageIndex;
    }

    public int getLastPageIPD() {
        int index = this.cachedPages.size();
        boolean isFirstPage = (startPageOfPageSequence == index);
        SimplePageMaster spm = pageSeq.getLastSimplePageMaster(index, isFirstPage, false);
        // not through makePage: a column's width does not depend on measured extents (hook measured-region-extents)
        Page page = new Page(spm, index, "", false, false, false);
        if (pageSeq.getRoot().getLastSeq() != null && pageSeq.getRoot().getLastSeq() != pageSeq) {
            return -1;
        }
        return page.getPageViewport().getBodyRegion().getColumnIPD();
    }

    public int getCurrentIPD() {
        if (startPageOfCurrentElementList == 0) {
            return -1;
        }
        Page page = getPageFromColumnIndex(startColumnOfCurrentElementList);
        return page.getPageViewport().getBodyRegion().getColumnIPD();
    }

    public int getNextIPD() {
        pageSeq.setOnlyTryInfinite(true);
        try {
            int oldSize = cachedPages.size();
            Page page = getPageFromColumnIndex(startColumnOfCurrentElementList + 1);
            if (oldSize != cachedPages.size()) {
                cachedPages.remove(cachedPages.size() - 1);
            }
            return page.getPageViewport().getBodyRegion().getColumnIPD();
        } catch (PageProductionException e) {
            return getCurrentIPD();
        } finally {
            pageSeq.setOnlyTryInfinite(false);
        }
    }

    public int getCurrentColumnCount() {
        Page page = getPageFromColumnIndex(startColumnOfCurrentElementList);
        return page.getPageViewport().getCurrentSpan().getColumnCount();
    }

    /**
     * Indicates whether the column/page at the given index is on the first page of the page sequence.
     *
     * @return {@code true} if the given part is on the first page of the sequence
     */
    boolean isOnFirstPage(int partIndex) {
        Column column = getColumn(partIndex);
        return startPageOfCurrentElementList + column.pageIndex == startPageOfPageSequence;
    }

}
