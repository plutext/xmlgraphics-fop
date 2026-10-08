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
 * Apache FOP 2.11: hook page-master-by-content, a page's height asked for with the part of the page-sequence that
 * owns it, carried on the node that starts it (fop/CR-017); and a side float ends at the break before the first
 * line lying below its foot, the space between paragraphs counted, and not inside a table, which cannot be read
 * again from there (fop/CR-020); and a block-level FO whose clear names the side a float is on ends the float at the
 * break before it, its content starting at the float's foot (fop/CR-022); and hook float-offset, a float's start edge
 * at the first break after which a line would cross the float's top, `fox:float-offset` below its anchor block's top
 * (fop/CR-023), and a line that does not fit beside a float ends it, the line set at its foot (fop/CR-023 §4.2).
 * See README.md, "Changes from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.layoutmgr;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.fop.fo.Constants;
import org.apache.fop.fo.FONode;
import org.apache.fop.fo.FObj;
import org.apache.fop.fo.flow.Block;
import org.apache.fop.fo.flow.BlockContainer;
import org.apache.fop.fo.flow.ListBlock;
import org.apache.fop.fo.flow.table.Table;
import org.apache.fop.layoutmgr.AbstractBreaker.FloatPosition;
import org.apache.fop.layoutmgr.AbstractBreaker.PageBreakPosition;
import org.apache.fop.layoutmgr.WhitespaceManagementPenalty.Variant;
import org.apache.fop.layoutmgr.inline.FloatLayoutManager;
import org.apache.fop.layoutmgr.inline.LineLayoutManager;
import org.apache.fop.traits.MinOptMax;
import org.apache.fop.util.ListUtil;

// CSOFF: InnerAssignment

class PageBreakingAlgorithm extends BreakingAlgorithm {

    /** the logger for the class */
    private static Log log = LogFactory.getLog(PageBreakingAlgorithm.class);

    private final LayoutManager topLevelLM;
    private final PageProvider pageProvider;
    private final PageBreakingLayoutListener layoutListener;
    /** List of PageBreakPosition elements. */
    private LinkedList<PageBreakPosition> pageBreaks;

    /** Footnotes which are cited between the currently considered active node (previous
     * break) and the current considered break. Its type is
     * List&lt;List&lt;KnuthElement&gt;&gt;, it contains the sequences of KnuthElement
     * representing the footnotes bodies.
     */
    private List<List<KnuthElement>> footnotesList;
    /** Cumulated bpd of unhandled footnotes. */
    private List<Integer> lengthList;
    /** Length of all the footnotes which will be put on the current page. */
    private int totalFootnotesLength;
    /**
     * Length of all the footnotes which have already been inserted, up to the currently
     * considered element. That is, footnotes from the currently considered page plus
     * footnotes from its preceding pages.
     */
    private int insertedFootnotesLength;

    /** True if footnote citations have been met since the beginning of the page sequence. */
    private boolean footnotesPending;
    /** True if the elements met after the previous break point contain footnote citations. */
    private boolean newFootnotes;
    /** Index of the first footnote met after the previous break point. */
    private int firstNewFootnoteIndex;
    /** Index of the last footnote inserted on the current page. */
    private int footnoteListIndex;
    /** Index of the last element of the last footnote inserted on the current page. */
    private int footnoteElementIndex = -1;

    // demerits for a page break that splits a footnote
    private final int splitFootnoteDemerits = 5000;
    // demerits for a page break that defers a whole footnote to the following page
    private final int deferredFootnoteDemerits = 10000;
    private MinOptMax footnoteSeparatorLength;

    // the method noBreakBetween(int, int) uses these variables
    // to store parameters and result of the last call, in order
    // to reuse them and take less time
    private int storedPrevBreakIndex = -1;
    private int storedBreakIndex = -1;
    private boolean storedValue;

    //Controls whether overflows should be warned about or not
    private boolean autoHeight;

    //Controls whether a single part should be forced if possible (ex. block-container)
    private boolean favorSinglePart;

    private int ipdDifference;
    private KnuthNode bestNodeForIPDChange;
    public KnuthNode bestNodeForLastPage;

    //Used to keep track of switches in keep-context
    private int currentKeepContext = Constants.EN_AUTO;
    private KnuthNode lastBeforeKeepContextSwitch;

    // just one float for now...
    private boolean handlingStartOfFloat;
    private boolean handlingEndOfFloat;
    private int floatHeight;
    /** The content height at the float's edge, up to the first box below the float (fop/CR-020). */
    private int floatEdgeWidth;
    private int floatClearance; // fop/CR-022
    /** fop/CR-023: an offset float's top in list coordinates while its intrusion waits for it, else -1. */
    private int pendingFloatTop = -1;
    /** fop/CR-023: the height of the anchor's first line, the one the float's area is placed before. */
    private int floatAnchorLineHeight;
    /** fop/CR-023: the anchor block's top, in list coordinates, for the floats whose start edge is being taken. */
    private int floatAnchorListTop = -1;
    /** fop/CR-023: the foot of the offset float whose start edge is being taken, in list coordinates. */
    private int floatStartListBottom;
    /** fop/CR-023: whether that float, with its offset, fits a page of its own; if not, no page is refused for it. */
    private boolean floatFitsAnEmptyPage;
    /** fop/CR-023: the top of the float whose start edge is being taken, in list coordinates, else -1. */
    private int floatStartListTop = -1;
    /** fop/CR-023: the anchor block's top in the page when an offset float's start edge is taken; -1 otherwise. */
    private int floatAnchorTopInPage = -1;
    private int edgeElementIdx = -1; // fop/CR-022: the legal break under consideration while a float is on
    private KnuthNode bestFloatEdgeNode;
    private FloatPosition floatPosition;
    private int previousFootnoteListIndex = -2;
    private int previousFootnoteElementIndex = -2;
    private boolean relayingFootnotes;
    private LayoutContext childLC;

    /**
     * Construct a page breaking algorithm.
     * @param topLevelLM the top level layout manager
     * @param pageProvider the page provider
     * @param layoutListener the layout listener
     * @param alignment     alignment of the paragraph/page. One of {@link Constants#EN_START},
     *                  {@link Constants#EN_JUSTIFY}, {@link Constants#EN_CENTER},
     *                  {@link Constants#EN_END}.
     *                  For pages, {@link Constants#EN_BEFORE} and {@link Constants#EN_AFTER}
     *                  are mapped to the corresponding inline properties,
     *                  {@link Constants#EN_START} and {@link Constants#EN_END}.
     * @param alignmentLast alignment of the paragraph's last line
     * @param footnoteSeparatorLength length of footnote separator
     * @param partOverflowRecovery  {@code true} if too long elements should be moved to
     *                              the next line/part
     * @param autoHeight true if auto height
     * @param favorSinglePart true if favoring single part
     * @see BreakingAlgorithm
     */
    public PageBreakingAlgorithm(LayoutManager topLevelLM,
                                 PageProvider pageProvider,
                                 PageBreakingLayoutListener layoutListener,
                                 int alignment, int alignmentLast,
                                 MinOptMax footnoteSeparatorLength,
                                 boolean partOverflowRecovery, boolean autoHeight,
                                 boolean favorSinglePart, LayoutContext childLC) {
        super(alignment, alignmentLast, true, partOverflowRecovery, 0);
        this.topLevelLM = topLevelLM;
        this.pageProvider = pageProvider;
        this.layoutListener = layoutListener;
        best = new BestPageRecords();
        this.footnoteSeparatorLength = footnoteSeparatorLength;
        this.autoHeight = autoHeight;
        this.favorSinglePart = favorSinglePart;
        this.childLC = childLC;
    }

    /**
     * This class represents a feasible breaking point
     * with extra information about footnotes.
     */
    protected class KnuthPageNode extends KnuthNode {

        /** Additional length due to already inserted footnotes. */
        public int insertedFootnotes;

        /** Total length of the footnotes. */
        public int totalFootnotes;

        /** Index of the last inserted footnote. */
        public int footnoteListIndex;

        /** Index of the last inserted element of the last inserted footnote. */
        public int footnoteElementIndex;

        /**
         * Hook page-master-by-content: the part owning the page the part starting at this node lies on, and
         * whether that page is the part's first; null without parts.
         */
        PageOwnership.Part pageOwner;
        boolean opensOwner;

        /**
         * Pending variants of dynamic contents that were evaluated WRT this node.
         * When computing page difference for a break element, the total width of these variants
         * will be added to 'actualWidth'.
         */
        private final List<Variant> pendingVariants = new ArrayList<Variant>();
        private int totalVariantsWidth;

        public KnuthPageNode(int position,
                             int line, int fitness,
                             int totalWidth, int totalStretch, int totalShrink,
                             int insertedFootnotes, int totalFootnotes,
                             int footnoteListIndex, int footnoteElementIndex,
                             double adjustRatio, int availableShrink, int availableStretch,
                             int difference, double totalDemerits, KnuthNode previous) {
            super(position, line, fitness,
                  totalWidth, totalStretch, totalShrink,
                  adjustRatio, availableShrink, availableStretch,
                  difference, totalDemerits, previous);
            this.totalFootnotes = totalFootnotes;
            this.insertedFootnotes = insertedFootnotes;
            this.footnoteListIndex = footnoteListIndex;
            this.footnoteElementIndex = footnoteElementIndex;
        }

        public void addVariant(Variant variant) {
            pendingVariants.add(variant);
            totalVariantsWidth += variant.width;
        }

    }

    /**
     * this class stores information about how the nodes
     * which could start a line ending at the current element
     */
    protected class BestPageRecords extends BestRecords {

        private final int[] bestInsertedFootnotesLength = new int[4];
        private final int[] bestTotalFootnotesLength = new int[4];
        private final int[] bestFootnoteListIndex = new int[4];
        private final int[] bestFootnoteElementIndex = new int[4];

        @Override
        public void addRecord(double demerits, KnuthNode node, double adjust,
                              int availableShrink, int availableStretch,
                              int difference, int fitness) {
            super.addRecord(demerits, node, adjust,
                            availableShrink, availableStretch,
                            difference, fitness);
            bestInsertedFootnotesLength[fitness] = insertedFootnotesLength;
            bestTotalFootnotesLength[fitness] = totalFootnotesLength;
            bestFootnoteListIndex[fitness] = footnoteListIndex;
            bestFootnoteElementIndex[fitness] = footnoteElementIndex;
        }

        public int getInsertedFootnotesLength(int fitness) {
            return bestInsertedFootnotesLength[fitness];
        }

        public int getTotalFootnotesLength(int fitness) {
            return bestTotalFootnotesLength[fitness];
        }

        public int getFootnoteListIndex(int fitness) {
            return bestFootnoteListIndex[fitness];
        }

        public int getFootnoteElementIndex(int fitness) {
            return bestFootnoteElementIndex[fitness];
        }


    }

    /** {@inheritDoc} */
    @Override
    protected void initialize() {
        super.initialize();
        insertedFootnotesLength = 0;
        footnoteListIndex = 0;
        footnoteElementIndex = -1;
        if (topLevelLM instanceof PageSequenceLayoutManager) {
            PageSequenceLayoutManager pslm = (PageSequenceLayoutManager) topLevelLM;
            boolean spanAll = childLC != null && childLC.getCurrentSpan() == Constants.EN_ALL;
            if (pslm.handlingStartOfFloat() || pslm.handlingEndOfFloat() || spanAll) {
                pslm.retrieveFootnotes(this);
            }
            if (pslm.handlingStartOfFloat()) {
                if (pslm.intrusionPending()) {
                    // hook float-offset (fop/CR-023): the float's area is placed, its top the shift below this
                    // list's start; the lines keep the full width until one would cross it, where the
                    // intrusion starts (considerLegalBreak, startPendingFloat)
                    pendingFloatTop = pslm.getFloatYShift();
                } else {
                    // the foot is the float's height below its top, which lies the shift below the break that
                    // started the intrusion, where this list starts (fop/CR-023)
                    floatHeight = Math.min(pslm.getFloatHeight() + pslm.getFloatYShift(),
                            lineWidth - pslm.getFloatYOffset());
                }
            }
            if (pslm.handlingEndOfFloat()) {
                totalWidth += pslm.getOffsetDueToFloat() + insertedFootnotesLength;
            } else if (spanAll) {
                totalWidth += insertedFootnotesLength;
            }
        }
    }

    /**
     * Overridden to defer a part to the next page, if it
     * must be kept within one page, but is too large to fit in
     * the last column.
     * {@inheritDoc}
     */
    @Override
    protected KnuthNode recoverFromTooLong(KnuthNode lastTooLong) {

        if (log.isDebugEnabled()) {
            log.debug("Recovering from too long: " + lastTooLong);
            log.debug("\tlastTooShort = " + getLastTooShort());
            log.debug("\tlastBeforeKeepContextSwitch = " + lastBeforeKeepContextSwitch);
            log.debug("\tcurrentKeepContext = "
                      + AbstractBreaker.getBreakClassName(currentKeepContext));
        }

        if (lastBeforeKeepContextSwitch == null
                || currentKeepContext == Constants.EN_AUTO) {
            return super.recoverFromTooLong(lastTooLong);
        }

        KnuthNode node = lastBeforeKeepContextSwitch;
        lastBeforeKeepContextSwitch = null;
        // content would overflow, insert empty page/column(s) and try again
        while (!pageProvider.endPage(node.line - 1)) {
            log.trace("Adding node for empty column");
            node = createNode(
                    node.position,
                    node.line + 1, 1,
                    0, 0, 0,
                    0, 0, 0,
                    0, 0, node);
        }
        return node;
    }

    /**
     * Compare two KnuthNodes and return the node with the least demerit.
     *
     * @param node1 The first knuth node.
     * @param node2 The other knuth node.
     * @return the node with the least demerit.
     */
    @Override
    protected KnuthNode compareNodes(KnuthNode node1, KnuthNode node2) {

        /* if either node is null, return the other one */
        if (node1 == null || node2 == null) {
            return (node1 == null) ? node2 : node1;
        }

        /* if either one of the nodes corresponds to a mere column-break,
         * and the other one corresponds to a page-break, return the page-break node
         */
        if (pageProvider != null) {
            if (pageProvider.endPage(node1.line - 1)
                    && !pageProvider.endPage(node2.line - 1)) {
                return node1;
            } else if (pageProvider.endPage(node2.line - 1)
                    && !pageProvider.endPage(node1.line - 1)) {
                return node2;
            }
        }

        /* all other cases: use superclass implementation */
        return super.compareNodes(node1, node2);
    }

    /** {@inheritDoc} */
    @Override
    protected KnuthNode createNode(int position,
                                   int line, int fitness,
                                   int totalWidth, int totalStretch, int totalShrink,
                                   double adjustRatio, int availableShrink, int availableStretch,
                                   int difference, double totalDemerits, KnuthNode previous) {
        KnuthPageNode node = new KnuthPageNode(position, line, fitness,
                                 totalWidth, totalStretch, totalShrink,
                                 insertedFootnotesLength, totalFootnotesLength,
                                 footnoteListIndex, footnoteElementIndex,
                                 adjustRatio, availableShrink, availableStretch,
                                 difference, totalDemerits, previous);
        assignPageOwner(node, previous);
        return node;
    }

    /** {@inheritDoc} */
    @Override
    protected KnuthNode createNode(int position, int line, int fitness,
                                   int totalWidth, int totalStretch, int totalShrink) {
        KnuthPageNode node = new KnuthPageNode(position, line, fitness,
                                 totalWidth, totalStretch, totalShrink,
                                 ((BestPageRecords) best).getInsertedFootnotesLength(fitness),
                                 ((BestPageRecords) best).getTotalFootnotesLength(fitness),
                                 ((BestPageRecords) best).getFootnoteListIndex(fitness),
                                 ((BestPageRecords) best).getFootnoteElementIndex(fitness),
                                 best.getAdjust(fitness), best.getAvailableShrink(fitness),
                                 best.getAvailableStretch(fitness), best.getDifference(fitness),
                                 best.getDemerits(fitness), best.getNode(fitness));
        assignPageOwner(node, best.getNode(fitness));
        return node;
    }

    /** Whether the nodes being created are those of pages holding only footnote bodies. */
    private boolean creatingFootnotePages;

    /**
     * Hook page-master-by-content (fop/CR-017): the part owning the page a new node's part lies on. A page
     * already handed out for areas keeps the owner recorded for it; the first column of any other page is
     * owned by the part of its first box; a later column, and a page holding only footnote bodies, by the
     * owner of the node before.
     */
    private void assignPageOwner(KnuthPageNode node, KnuthNode previous) {
        if (pageProvider == null || !pageProvider.getOwnership().isActive()) {
            return;
        }
        KnuthPageNode previousPageNode = (previous instanceof KnuthPageNode) ? (KnuthPageNode) previous : null;
        if (creatingFootnotePages && previousPageNode != null) {
            node.pageOwner = previousPageNode.pageOwner;
            node.opensOwner = false;
            return;
        }
        PageProvider.OwnerRecord record = pageProvider.getOwnerRecord(pageProvider.pageNumberOfPart(node.line));
        if (record != null) {
            node.pageOwner = record.part;
            node.opensOwner = record.first;
        } else if (previousPageNode == null || pageProvider.partOpensPage(node.line)) {
            PageOwnership ownership = pageProvider.getOwnership();
            node.pageOwner = ownership.ownerAfter(node.position);
            node.opensOwner = ownership.opensPart(node.position);
        } else {
            node.pageOwner = previousPageNode.pageOwner;
            node.opensOwner = previousPageNode.opensOwner;
        }
    }

    /**
     * Page-breaking specific handling of the given box. Currently it adds the footnotes
     * cited in the given box to the list of to-be-handled footnotes.
     * {@inheritDoc}
     */
    @Override
    protected void handleBox(KnuthBox box) {
        super.handleBox(box);
        if (box instanceof KnuthBlockBox
            && ((KnuthBlockBox) box).hasAnchors()) {
            handleFootnotes(((KnuthBlockBox) box).getElementLists());
            if (!newFootnotes) {
                newFootnotes = true;
                firstNewFootnoteIndex = footnotesList.size() - 1;
            }
        }
        if (box instanceof KnuthBlockBox && ((KnuthBlockBox) box).hasFloatAnchors()) {
            int offset = floatOffsetOf((KnuthBlockBox) box);
            if (offset > 0) {
                // hook float-offset (fop/CR-023): the float's top is `offset` below the top of its anchor
                // block including the block's own space-before, which is the box's position less that space,
                // where the resolved gap before the box also holds the previous block's space-after (a word
                // processor measures from the anchor's own spacing; the docx4j session's probes, 2026-10-08).
                // The float's area is still placed at this break; its intrusion into the lines waits for the top.
                int anchorTop = totalWidth - Math.min(glueBefore(box), anchorSpaceBefore((KnuthBlockBox) box));
                floatAnchorListTop = anchorTop;
                floatStartListTop = anchorTop + offset;
                floatAnchorLineHeight = nextContentBoxWidth(box);
                floatStartListBottom = anchorTop + floatBottomOf((KnuthBlockBox) box);
                floatFitsAnEmptyPage = floatBottomOf((KnuthBlockBox) box) <= lineWidth;
            }
            handlingStartOfFloat = true;
        }
        if (floatHeight != 0 && totalWidth >= floatHeight) {
            handlingEndOfFloat = true;
        }
    }

    /**
     * While a float is being handled, a legal break that is the float's edge ends the algorithm there: the node
     * that would be activated is recorded as the edge node instead, and nothing stays active. Before this, an
     * edge at a break the page could also take (its ratio within the threshold) was passed over, every later
     * break deactivated the nodes before it, and the edge became the forced break at the page's end, inside
     * whatever was there: a table in corpus document 4083, where the deferral of fop/CR-020 then left no edge
     * node and handleFloat threw NullPointerException (fop/CR-022 §3.5). A break the page cannot take at all
     * (too long or too short) still reaches createForcedNodes, which records the edge as before.
     */
    @Override
    protected void activateNode(KnuthNode node, int difference, double r, double demerits, int fitnessClass,
            int availableShrink, int availableStretch) {
        if (handlingFloat() && edgeElementIdx >= 0) {
            noteFloatTargetTop(node);
            int[] glue = glueUpToNextBox(edgeElementIdx);
            if (bestFloatEdgeNode == null || demerits <= bestFloatEdgeNode.totalDemerits) {
                bestFloatEdgeNode = createNode(edgeElementIdx, node.line + 1, fitnessClass, totalWidth + glue[0],
                        totalStretch + glue[1], totalShrink + glue[2], r, availableShrink, availableStretch,
                        difference, demerits, node);
            }
            return;
        }
        super.activateNode(node, difference, r, demerits, fitnessClass, availableShrink, availableStretch);
    }

    /** The width, stretch and shrink of the glue from the break up to the next box, as forceNode counts them. */
    private int[] glueUpToNextBox(int elementIdx) {
        int[] glue = new int[3];
        for (int i = elementIdx; i < par.size(); i++) {
            KnuthElement e = getElement(i);
            if (e.isBox()) {
                break;
            } else if (e.isGlue()) {
                glue[0] += e.getWidth();
                glue[1] += e.getStretch();
                glue[2] += e.getShrink();
            } else if (e.isForcedBreak() && i != elementIdx) {
                break;
            }
        }
        return glue;
    }

    /** The content height as if the break were just before the next box, as forceNode measures a node. */
    private int widthUpToNextBox(int elementIdx) {
        int width = totalWidth;
        for (int i = elementIdx; i < par.size(); i++) {
            KnuthElement tempElement = getElement(i);
            if (tempElement.isBox()) {
                break;
            } else if (tempElement.isGlue()) {
                width += tempElement.getWidth();
            } else if (tempElement.isForcedBreak() && i != elementIdx) {
                break;
            }
        }
        return width;
    }

    /**
     * Overridden to consider penalties with value {@link KnuthElement#INFINITE}
     * as legal break-points, if the current keep-context allows this
     * (a keep-*.within-page="always" constraint still permits column-breaks)
     * {@inheritDoc}
     */
    @Override
    protected void handlePenaltyAt(KnuthPenalty penalty, int position,
                                   int allowedBreaks) {
        super.handlePenaltyAt(penalty, position, allowedBreaks);
        /* if the penalty had value INFINITE, default implementation
         * will not have considered it a legal break, but it could still
         * be one.
         */
        if (penalty.getPenalty() == KnuthPenalty.INFINITE) {
            int breakClass = penalty.getBreakClass();
            if (breakClass == Constants.EN_PAGE
                    || breakClass == Constants.EN_COLUMN) {
                considerLegalBreak(penalty, position);
            }
        }
    }

    /**
     * Handles the footnotes cited inside a block-level box. Updates footnotesList and the
     * value of totalFootnotesLength with the lengths of the given footnotes.
     * @param elementLists list of KnuthElement sequences corresponding to the footnotes
     * bodies
     */
    private void handleFootnotes(List<List<KnuthElement>> elementLists) {
        // initialization
        if (!footnotesPending) {
            footnotesPending = true;
            footnotesList = new ArrayList<List<KnuthElement>>();
            lengthList = new ArrayList<Integer>();
            totalFootnotesLength = 0;
        }
        if (!newFootnotes) {
            newFootnotes = true;
            firstNewFootnoteIndex = footnotesList.size();
        }

        // compute the total length of the footnotes
        for (List<KnuthElement> noteList : elementLists) {
            int noteLength = 0;
            footnotesList.add(noteList);
            for (KnuthElement element : noteList) {
                if (element.isBox() || element.isGlue()) {
                    noteLength += element.getWidth();
                }
            }
            int prevLength = (lengthList == null || lengthList.isEmpty())
                    ? 0
                    : ListUtil.getLast(lengthList);
            if (lengthList != null) {
                lengthList.add(prevLength + noteLength);
            }
            totalFootnotesLength += noteLength;
        }
    }

    /** {@inheritDoc} */
    @Override
    protected int restartFrom(KnuthNode restartingNode, int currentIndex) {
        if (handlingStartOfFloat && bestFloatEdgeNode == null) {
            // the float's start was refused (createForcedNodes): the anchor box is handled again from the restart
            handlingStartOfFloat = false;
            floatStartListTop = -1;
            floatAnchorListTop = -1;
            floatStartListBottom = 0;
        }

        int returnValue = super.restartFrom(restartingNode, currentIndex);
        newFootnotes = false;
        if (footnotesPending) {
            // remove from footnotesList the note lists that will be met
            // after the restarting point
            for (int j = currentIndex; j >= restartingNode.position; j--) {
                final KnuthElement resetElement = getElement(j);
                if (resetElement instanceof KnuthBlockBox
                        && ((KnuthBlockBox) resetElement).hasAnchors()) {
                    resetFootnotes(((KnuthBlockBox) resetElement).getElementLists());
                }
            }
            assert restartingNode instanceof KnuthPageNode;
            KnuthPageNode restartingPageNode = (KnuthPageNode) restartingNode;
            footnoteElementIndex = restartingPageNode.footnoteElementIndex;
            footnoteListIndex = restartingPageNode.footnoteListIndex;
            totalFootnotesLength = restartingPageNode.totalFootnotes;
            insertedFootnotesLength = restartingPageNode.insertedFootnotes;
        }
        return returnValue;
    }

    private void resetFootnotes(List<List<KnuthElement>> elementLists) {
        for (int i = 0; i < elementLists.size(); i++) {
            ListUtil.removeLast(footnotesList);
            ListUtil.removeLast(lengthList);
        }
        // update footnotesPending;
        if (footnotesList.size() == 0) {
            footnotesPending = false;
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void considerLegalBreak(KnuthElement element, int elementIdx) {
        if (element.isPenalty()) {
            int breakClass = ((KnuthPenalty) element).getBreakClass();
            switch (breakClass) {
            case Constants.EN_PAGE:
                if (this.currentKeepContext != breakClass) {
                    this.lastBeforeKeepContextSwitch = getLastTooShort();
                }
                this.currentKeepContext = breakClass;
                break;
            case Constants.EN_COLUMN:
                if (this.currentKeepContext != breakClass) {
                    this.lastBeforeKeepContextSwitch = getLastTooShort();
                }
                this.currentKeepContext = breakClass;
                break;
            case Constants.EN_AUTO:
                this.currentKeepContext = breakClass;
                break;
            default:
                //nop
            }
        }
        boolean edgeDeferred = false;
        edgeElementIdx = elementIdx;
        if (pendingFloatTop >= 0 && !handlingFloat() && !insideNonRestartable(elementIdx)) {
            // the intrusion cannot start inside a table, which is not restarted from a row (CR-020): a table
            // starting at this break that reaches the float's top takes the start here, before it
            LayoutManager starting = nonRestartableLMOfNextBox(elementIdx);
            if (nextLineCrosses(elementIdx, pendingFloatTop)
                    || (starting != null && widthUpToNextBox(elementIdx) + nonRestartableExtent(elementIdx, starting)
                            > pendingFloatTop)) {
                startPendingFloat();
            }
        }
        if (floatHeight != 0) {
            // A side float ends at the first legal break after which the content lies wholly below the
            // float's foot: the content height before the break plus the glue up to the next box, the space
            // between two paragraphs included. Without this, a space carrying the next line below the foot
            // left that line laid out beside the float, since only handleBox tested the foot. The height at
            // the edge is kept for deactivateNode, so the content after the float starts below that space
            // (fop/CR-020).
            int edgeWidth = widthUpToNextBox(elementIdx);
            // A block-level FO whose clear names the float's side must not sit beside it: the break before its
            // first box is the float's edge too, and deactivateNode keeps the float's foot as the height the
            // content after the edge starts at, so the FO is laid out below the float (fop/CR-022).
            if (!handlingEndOfFloat && (edgeWidth >= floatHeight || clearsFloatAtNextBox(elementIdx)
                    || overflowsBesideFloatAtNextBox(elementIdx)
                    || startsNonRestartableThePageCannotHold(elementIdx, edgeWidth))) {
                handlingEndOfFloat = true;
            }
            if (handlingEndOfFloat) {
                floatEdgeWidth = edgeWidth;
                // The content after the edge is read again from the layout manager there, which a table
                // cannot do (it is not restartable): a break inside one is not the edge, which waits for the
                // first legal break after it. The rows below the float keep the table's width, which the
                // table set once for all its rows, so nothing is drawn differently (fop/CR-020).
                LayoutManager inside = nonRestartableLMOfNextBox(elementIdx);
                edgeDeferred = inside != null && inside == nonRestartableLMOfPreviousBox(elementIdx);
            }
        }
        if (edgeDeferred) {
            handlingEndOfFloat = false;
        }
        super.considerLegalBreak(element, elementIdx);
        if (edgeDeferred) {
            handlingEndOfFloat = true;
            if (activeNodeCount == 0 && bestFloatEdgeNode == null) {
                // a forced break inside the table, made with float handling off: no edge can be taken here (a
                // table is not restarted from a row) and none was taken before it. The float is given up and the
                // page breaks here; the lines after keep the width they were set to beside the float (fop/CR-022)
                log.warn("A side float whose edge falls inside a table the page cannot hold: the float is given up"
                        + " at the page break inside the table, and the lines after it keep their narrowed width.");
                disableFloatHandling();
            }
        }
        edgeElementIdx = -1;
        newFootnotes = false;
    }

    private LayoutManager nonRestartableLMOfNextBox(int elementIdx) {
        for (int i = elementIdx; i < par.size(); i++) {
            KnuthElement e = getElement(i);
            if (e.isBox()) {
                return nonRestartableLM(e.getPosition());
            }
        }
        return null;
    }

    /**
     * Whether the next box after the break starts a block-level FO whose clear names the side the current float
     * is on (fop/CR-022). The FO starts there when a layout manager in the box's position chain carrying clear is
     * absent from the previous box's chain; at the head of the list there is no previous box and nothing clears.
     */
    private boolean clearsFloatAtNextBox(int elementIdx) {
        if (!(topLevelLM instanceof PageSequenceLayoutManager)) {
            return false;
        }
        PageSequenceLayoutManager pslm = (PageSequenceLayoutManager) topLevelLM;
        boolean floatAtStart = pslm.getStartIntrusionAdjustment() > 0;
        boolean floatAtEnd = pslm.getEndIntrusionAdjustment() > 0;
        if (!floatAtStart && !floatAtEnd) {
            return false;
        }
        Position previous = positionOfPreviousBox(elementIdx);
        if (previous == null) {
            return false;
        }
        for (Position p = positionOfNextBox(elementIdx); p != null; p = nextInChain(p)) {
            LayoutManager lm = p.getLM();
            if (lm == null) {
                continue;
            }
            int clear = clearOf(lm.getFObj());
            if (clear == Constants.EN_NONE || chainHasLM(previous, lm)) {
                continue;
            }
            if (clear == Constants.EN_BOTH || (clear == Constants.EN_START && floatAtStart)
                    || (clear == Constants.EN_END && floatAtEnd)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the next box starts a non-restartable layout manager (a table's) whose elements would carry the
     * content past the end of the page while the float is on. The edge is then taken here, before the table, and
     * the table starts at the float's foot (the clearance) and breaks across the page as any table does. Deferring
     * the edge into such a table (fop/CR-020) ended in a forced break inside it made with float handling off, no
     * edge node, and a NullPointerException in handleFloat (corpus document 4083, 2026-10-08; fop/CR-022 §3.5).
     */
    private boolean startsNonRestartableThePageCannotHold(int elementIdx, int edgeWidth) {
        LayoutManager starting = nonRestartableLMOfNextBox(elementIdx);
        if (starting == null || starting == nonRestartableLMOfPreviousBox(elementIdx)) {
            return false;
        }
        return edgeWidth + nonRestartableExtent(elementIdx, starting) > lineWidth;
    }

    /** The height of the elements of a non-restartable layout manager starting after this break (CR-022, CR-023). */
    private int nonRestartableExtent(int elementIdx, LayoutManager starting) {
        int extent = 0;
        for (int i = elementIdx; i < par.size(); i++) {
            KnuthElement e = getElement(i);
            if (e.isBox() && nonRestartableLM(e.getPosition()) != starting) {
                break;
            }
            if (e.isBox() || e.isGlue()) {
                extent += e.getWidth();
            }
        }
        return extent;
    }

    /** Whether this break lies inside a non-restartable layout manager, a table's (fop/CR-023). */
    private boolean insideNonRestartable(int elementIdx) {
        LayoutManager inside = nonRestartableLMOfNextBox(elementIdx);
        return inside != null && inside == nonRestartableLMOfPreviousBox(elementIdx);
    }

    /**
     * Whether the next box is a line that did not fit beside the float but would fit without it (marked by
     * LineLayoutManager): the edge is here, and the clearance sets the line, with the rest of its paragraph, at
     * the float's foot, as a word processor moves a word that does not fit beside a table (fop/CR-023 §4.2).
     */
    private boolean overflowsBesideFloatAtNextBox(int elementIdx) {
        for (Position p = positionOfNextBox(elementIdx); p != null; p = nextInChain(p)) {
            if (p instanceof LineLayoutManager.LineBreakPosition
                    && ((LineLayoutManager.LineBreakPosition) p).overflowsBesideFloat()) {
                return true;
            }
        }
        return false;
    }

    private static int clearOf(FObj fo) {
        if (fo instanceof Block) {
            return ((Block) fo).getClear();
        } else if (fo instanceof Table) {
            return ((Table) fo).getClear();
        } else if (fo instanceof BlockContainer) {
            return ((BlockContainer) fo).getClear();
        } else if (fo instanceof ListBlock) {
            return ((ListBlock) fo).getClear();
        }
        return Constants.EN_NONE;
    }

    /** The position wrapped by this one, or null at the end of the chain: a TableContentPosition returns itself. */
    private static Position nextInChain(Position p) {
        Position next = p.getPosition();
        return (next == p) ? null : next;
    }

    private static boolean chainHasLM(Position position, LayoutManager lm) {
        for (Position p = position; p != null; p = nextInChain(p)) {
            if (p.getLM() == lm) {
                return true;
            }
        }
        return false;
    }

    private Position positionOfNextBox(int elementIdx) {
        for (int i = elementIdx; i < par.size(); i++) {
            KnuthElement e = getElement(i);
            if (e.isBox()) {
                return e.getPosition();
            }
        }
        return null;
    }

    private Position positionOfPreviousBox(int elementIdx) {
        for (int i = elementIdx - 1; i >= 0; i--) {
            KnuthElement e = getElement(i);
            if (e.isBox()) {
                return e.getPosition();
            }
        }
        return null;
    }

    private LayoutManager nonRestartableLMOfPreviousBox(int elementIdx) {
        for (int i = elementIdx - 1; i >= 0; i--) {
            KnuthElement e = getElement(i);
            if (e.isBox()) {
                return nonRestartableLM(e.getPosition());
            }
        }
        return null;
    }

    /** The outermost layout manager in a position's chain that cannot be restarted, or null. */
    private static LayoutManager nonRestartableLM(Position position) {
        for (Position p = position; p != null; p = p.getPosition()) {
            LayoutManager lm = p.getLM();
            if (lm != null && !lm.isRestartable()) {
                return lm;
            }
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    protected boolean elementCanEndLine(KnuthElement element, int line, int difference) {
        if (handlingFloat()) {
            // While a side float is being handled, a legal break ends the algorithm as the float's start or
            // edge, which is no page break: a keep's infinite penalty (keep-together, keep-with-next; INFINITE
            // is 1000) must not stop the node loop here, or nothing is deactivated, no edge node is made, and
            // the algorithm walks on to the next unkept break, inside whatever follows (fop/CR-022 §3.5).
            return true;
        }
        if (!(element.isPenalty()) || pageProvider == null) {
            return true;
        } else {
            KnuthPenalty p = (KnuthPenalty) element;
            if (p.getPenalty() <= 0) {
                return true;
            } else {
                int context = p.getBreakClass();
                switch (context) {
                case Constants.EN_LINE:
                case Constants.EN_COLUMN:
                    return p.getPenalty() < KnuthPenalty.INFINITE;
                case Constants.EN_PAGE:
                    return p.getPenalty() < KnuthPenalty.INFINITE
                            || !pageProvider.endPage(line - 1);
                case Constants.EN_AUTO:
                    log.debug("keep is not auto but context is");
                    return true;
                default:
                    if (p.getPenalty() < KnuthPenalty.INFINITE) {
                        log.debug("Non recognized keep context:" + context);
                        return true;
                    } else {
                        return false;
                    }
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    protected int computeDifference(KnuthNode activeNode, KnuthElement element,
                                    int elementIndex) {
        KnuthPageNode pageNode = (KnuthPageNode) activeNode;
        if (handlingStartOfFloat && !handlingEndOfFloat && floatStartListTop >= 0 && floatFitsAnEmptyPage
                && floatStartListBottom - pageNode.totalWidth > getLineWidth(activeNode)) {
            // hook float-offset (fop/CR-023): at an offset float's start edge, a page that cannot hold the
            // float below its anchor is too long here, so the page breaks before the anchor and the anchor
            // and its float start the next page together, as a word processor keeps them
            return -getLineWidth(activeNode) - 1;
        }
        int actualWidth = totalWidth - pageNode.totalWidth;
        int footnoteSplit;
        boolean canDeferOldFN;
        actualWidth += pageNode.totalVariantsWidth;
        if (element instanceof WhitespaceManagementPenalty) {
            actualWidth += handleWhitespaceManagementPenalty(pageNode,
                    (WhitespaceManagementPenalty) element, elementIndex);
        } else if (element.isPenalty()) {
            actualWidth += element.getWidth();
        }
        if (footnotesPending) {
            // compute the total length of the footnotes not yet inserted
            int allFootnotes = totalFootnotesLength - pageNode.insertedFootnotes;
            if (allFootnotes > 0) {
                // this page contains some footnote citations
                // add the footnote separator width
                actualWidth += footnoteSeparatorLength.getOpt();
                if (actualWidth + allFootnotes <= getLineWidth(activeNode)) {
                    // there is enough space to insert all footnotes:
                    // add the whole allFootnotes length
                    actualWidth += allFootnotes;
                    insertedFootnotesLength = pageNode.insertedFootnotes + allFootnotes;
                    footnoteListIndex = footnotesList.size() - 1;
                    footnoteElementIndex
                        = getFootnoteList(footnoteListIndex).size() - 1;
                } else if (((canDeferOldFN = canDeferOldFootnotes(
                             pageNode, elementIndex))
                            || newFootnotes)
                           && (footnoteSplit = getFootnoteSplit(
                               pageNode, getLineWidth(activeNode) - actualWidth,
                                canDeferOldFN)) > 0) {
                    // it is allowed to break or even defer footnotes if either:
                    //  - there are new footnotes in the last piece of content, and
                    //    there is space to add at least a piece of the first one
                    //  - or the previous page break deferred some footnote lines, and
                    //    this is the first feasible break; in this case it is allowed
                    //    to break and defer, if necessary, old and new footnotes
                    actualWidth += footnoteSplit;
                    insertedFootnotesLength = pageNode.insertedFootnotes + footnoteSplit;
                    // footnoteListIndex has been set in getFootnoteSplit()
                    // footnoteElementIndex has been set in getFootnoteSplit()
                } else {
                    // there is no space to add the smallest piece of footnote,
                    // or we are trying to add a piece of content with no footnotes and
                    // it does not fit in the page, because of previous footnote bodies
                    // that cannot be broken:
                    // add the whole allFootnotes length, so this breakpoint will be discarded
                    actualWidth += allFootnotes;
                    insertedFootnotesLength = pageNode.insertedFootnotes + allFootnotes;
                    footnoteListIndex = footnotesList.size() - 1;
                    footnoteElementIndex
                        = getFootnoteList(footnoteListIndex).size() - 1;
                }
            } else {
                // all footnotes have already been placed on previous pages
            }
        } else {
            // there are no footnotes
        }
        int diff = getLineWidth(activeNode) - actualWidth;
        if (autoHeight && diff < 0) {
            //getLineWidth() for auto-height parts return 0 so the diff will be negative
            return 0; //...but we don't want to shrink in this case. Stick to optimum.
        } else {
            return diff;
        }
    }

    /**
     * Evaluates the variants corresponding to the given penalty until one that
     * leads to an acceptable adjustment ratio is found. That variant will
     * be added to the list of pending variants in the given active node.
     */
    private int handleWhitespaceManagementPenalty(KnuthPageNode activeNode,
            WhitespaceManagementPenalty penalty, int elementIndex) {
        for (Variant var : penalty.getVariants()) {
            int difference = computeDifference(activeNode, var.getPenalty(), elementIndex);
            double r = computeAdjustmentRatio(activeNode, difference);
            if (r >= -1.0) {
                activeNode.addVariant(var);
                return var.width;
            }
        }
        return 0;
    }

    /**
     * Checks whether footnotes from preceding pages may be deferred to the page after
     * the given element.
     * @param node active node for the preceding page break
     * @param contentElementIndex index of the Knuth element considered for the
     * current page break
     * @return  true if footnotes can be deferred
     */
    private boolean canDeferOldFootnotes(KnuthPageNode node, int contentElementIndex) {
        return (noBreakBetween(node.position, contentElementIndex)
                && deferredFootnotes(node.footnoteListIndex,
                        node.footnoteElementIndex, node.insertedFootnotes));
    }

    /**
     * Returns true if there may be no breakpoint between the two given elements.
     * @param prevBreakIndex index of the element from the currently considered active
     * node
     * @param breakIndex index of the currently considered breakpoint
     * @return true if no element between the two can be a breakpoint
     */
    private boolean noBreakBetween(int prevBreakIndex, int breakIndex) {
        // this method stores the parameters and the return value from previous calls
        // in order to avoid scanning the element list unnecessarily:
        //  - if there is no break between element #i and element #j
        //    there will not be a break between #(i+h) and #j too
        //  - if there is a break between element #i and element #j
        //    there will be a break between #(i-h) and #(j+k) too
        if (storedPrevBreakIndex != -1
            && ((prevBreakIndex >= storedPrevBreakIndex
                 && breakIndex == storedBreakIndex
                 && storedValue)
                || (prevBreakIndex <= storedPrevBreakIndex
                    && breakIndex >= storedBreakIndex
                    && !storedValue))) {
            // use the stored value, do nothing
        } else {
            // compute the new value
            int index;
            // ignore suppressed elements
            for (index = prevBreakIndex + 1;
                    !par.getElement(index).isBox();
                    index++) {
                //nop
            }
            // find the next break
            for (;
                 index < breakIndex;
                 index++) {
                if (par.getElement(index).isGlue() && par.getElement(index - 1).isBox()
                    || par.getElement(index).isPenalty()
                       && ((KnuthElement) par
                           .getElement(index)).getPenalty() < KnuthElement.INFINITE) {
                    // break found
                    break;
                }
            }
            // update stored parameters and value
            storedPrevBreakIndex = prevBreakIndex;
            storedBreakIndex = breakIndex;
            storedValue = (index == breakIndex);
        }
        return storedValue;
    }

    /**
     * Returns true if their are (pieces of) footnotes to be typeset on the current page.
     * @param listIndex index of the last inserted footnote for the currently considered
     * active node
     * @param elementIndex index of the last element of the last inserted footnote
     * @param length total length of all footnotes inserted so far
     */
    private boolean deferredFootnotes(int listIndex, int elementIndex, int length) {
        return ((newFootnotes
                 && firstNewFootnoteIndex != 0
                 && (listIndex < firstNewFootnoteIndex - 1
                     || elementIndex < getFootnoteList(listIndex).size() - 1))
                || length < totalFootnotesLength);
    }

    /**
     * Tries to split the flow of footnotes to put one part on the current page.
     * @param activeNode currently considered previous page break
     * @param availableLength available space for footnotes
     * @param canDeferOldFootnotes
     * @return ...
     */
    private int getFootnoteSplit(KnuthPageNode activeNode, int availableLength,
                boolean canDeferOldFootnotes) {
        return getFootnoteSplit(activeNode.footnoteListIndex,
                                activeNode.footnoteElementIndex,
                                activeNode.insertedFootnotes,
                                availableLength, canDeferOldFootnotes);
    }

    /**
     * Tries to split the flow of footnotes to put one part on the current page.
     * @param prevListIndex index of the last footnote on the previous page
     * @param prevElementIndex index of the last element of the last footnote
     * @param prevLength total length of footnotes inserted so far
     * @param availableLength available space for footnotes on this page
     * @param canDeferOldFootnotes
     * @return ...
     */
    private int getFootnoteSplit(int prevListIndex, int prevElementIndex, int prevLength,
                                 int availableLength, boolean canDeferOldFootnotes) {
        if (availableLength <= 0) {
            return 0;
        } else {
            // the split should contain a piece of the last footnote
            // together with all previous, not yet inserted footnotes;
            // but if this is not possible, try adding as much content as possible
            int splitLength = 0;
            ListIterator<KnuthElement> noteListIterator;
            KnuthElement element;
            boolean somethingAdded = false;

            // prevListIndex and prevElementIndex points to the last footnote element
            // already placed in a page: advance to the next element
            int listIndex = prevListIndex;
            int elementIndex = prevElementIndex;
            if (elementIndex == getFootnoteList(listIndex).size() - 1) {
                listIndex++;
                elementIndex = 0;
            } else {
                elementIndex++;
            }

            // try adding whole notes
            if (footnotesList.size() - 1 > listIndex) {
                // add the previous footnotes: these cannot be broken or deferred
                if (!canDeferOldFootnotes && newFootnotes && firstNewFootnoteIndex > 0) {
                    splitLength = lengthList.get(firstNewFootnoteIndex - 1) - prevLength;
                    listIndex = firstNewFootnoteIndex;
                    elementIndex = 0;
                }
                // try adding the new footnotes
                while (lengthList.get(listIndex) - prevLength
                       <= availableLength) {
                    splitLength = lengthList.get(listIndex) - prevLength;
                    somethingAdded = true;
                    listIndex++;
                    elementIndex = 0;
                }
                // as this method is called only if it is not possible to insert
                // all footnotes, at this point listIndex and elementIndex points to
                // an existing element, the next one we will try to insert
            }

            // try adding a split of the next note
            noteListIterator = getFootnoteList(listIndex).listIterator(elementIndex);

            int prevSplitLength = 0;
            int prevIndex = -1;
            int index = -1;

            while (splitLength <= availableLength) {
                if (somethingAdded) {
                    prevSplitLength = splitLength;
                    prevIndex = index;
                }
                // get a sub-sequence from the note element list
                boolean boxPreceding = false;
                while (noteListIterator.hasNext()) {
                    // as this method is called only if it is not possible to insert
                    // all footnotes, and we have already tried (and failed) to insert
                    // this whole footnote, the while loop will never reach the end
                    // of the note sequence
                    element = noteListIterator.next();
                    if (element.isBox()) {
                        // element is a box
                        splitLength += element.getWidth();
                        boxPreceding = true;
                        if (splitLength > prevSplitLength) {
                            // and it is non-empty
                            somethingAdded = true;
                        }
                    } else if (element.isGlue()) {
                        // element is a glue
                        if (boxPreceding) {
                            // end of the sub-sequence
                            index = noteListIterator.previousIndex();
                            break;
                        }
                        boxPreceding = false;
                        splitLength += element.getWidth();
                    } else {
                        // element is a penalty
                        if (element.getPenalty() < KnuthElement.INFINITE) {
                            // end of the sub-sequence
                            index = noteListIterator.previousIndex();
                            break;
                        }
                        boxPreceding = false;
                    }
                }
            }

            // if prevSplitLength is 0, this means that the available length isn't enough
            // to insert even the smallest split of the last footnote, so we cannot end a
            // page here
            // if prevSplitLength is > 0 we can insert some footnote content in this page
            // and insert the remaining in the following one
            if (!somethingAdded) {
                // there was not enough space to add a piece of the first new footnote
                // this is not a good break
                prevSplitLength = 0;
            } else if (prevSplitLength > 0) {
                // prevIndex is -1 if we have added only some whole footnotes
                footnoteListIndex = (prevIndex != -1) ? listIndex : listIndex - 1;
                footnoteElementIndex = (prevIndex != -1)
                    ? prevIndex
                    : getFootnoteList(footnoteListIndex).size() - 1;
            }
            return prevSplitLength;
        }
    }

    /** {@inheritDoc} */
    @Override
    protected double computeAdjustmentRatio(KnuthNode activeNode, int difference) {
        // compute the adjustment ratio
        if (difference > 0) {
            int maxAdjustment = totalStretch - activeNode.totalStretch;
            // add the footnote separator stretch if some footnote content will be added
            if (((KnuthPageNode) activeNode).insertedFootnotes < totalFootnotesLength) {
                maxAdjustment += footnoteSeparatorLength.getStretch();
            }
            if (maxAdjustment > 0) {
                return (double) difference / maxAdjustment;
            } else {
                return INFINITE_RATIO;
            }
        } else if (difference < 0) {
            int maxAdjustment = totalShrink - activeNode.totalShrink;
            // add the footnote separator shrink if some footnote content will be added
            if (((KnuthPageNode) activeNode).insertedFootnotes < totalFootnotesLength) {
                maxAdjustment += footnoteSeparatorLength.getShrink();
            }
            if (maxAdjustment > 0) {
                return (double) difference / maxAdjustment;
            } else {
                return -INFINITE_RATIO;
            }
        } else {
            return 0;
        }
    }

    /** {@inheritDoc} */
    @Override
    protected double computeDemerits(KnuthNode activeNode, KnuthElement element,
                                    int fitnessClass, double r) {
        double demerits = 0;
        // compute demerits
        double f = Math.abs(r);
        f = 1 + 100 * f * f * f;
        if (element.isPenalty()) {
            double penalty = element.getPenalty();
            if (penalty >= 0) {
                f += penalty;
                demerits = f * f;
            } else if (!element.isForcedBreak()) {
                demerits = f * f - penalty * penalty;
            } else {
                demerits = f * f;
            }
        } else {
            demerits = f * f;
        }

        if (element.isPenalty() && ((KnuthPenalty) element).isPenaltyFlagged()
            && getElement(activeNode.position).isPenalty()
            && ((KnuthPenalty) getElement(activeNode.position)).isPenaltyFlagged()) {
            // add demerit for consecutive breaks at flagged penalties
            demerits += repeatedFlaggedDemerit;
        }
        if (Math.abs(fitnessClass - activeNode.fitness) > 1) {
            // add demerit for consecutive breaks
            // with very different fitness classes
            demerits += incompatibleFitnessDemerit;
        }

        if (footnotesPending) {
            if (footnoteListIndex < footnotesList.size() - 1) {
                // add demerits for the deferred footnotes
                demerits += (footnotesList.size() - 1 - footnoteListIndex)
                                * deferredFootnoteDemerits;
            }
            if (footnoteListIndex < footnotesList.size()) {
                if (footnoteElementIndex
                        < getFootnoteList(footnoteListIndex).size() - 1) {
                    // add demerits for the footnote split between pages
                    demerits += splitFootnoteDemerits;
                }
            } else {
                //TODO Why can this happen in the first place? Does anybody know? See #44160
            }
        }
        demerits += activeNode.totalDemerits;
        return demerits;
    }

    /** {@inheritDoc} */
    @Override
    protected void finish() {
        for (int i = startLine; i < endLine; i++) {
            // @SuppressFBWarnings("BC_UNCONFIRMED_CAST_OF_RETURN_VALUE")
            for (KnuthPageNode node = (KnuthPageNode) getNode(i);
                 node != null;
                 node = (KnuthPageNode) node.next) {
                if (node.insertedFootnotes < totalFootnotesLength) {
                    // layout remaining footnote bodies
                    createOwnedFootnotePages(node);
                }
            }
        }
    }

    /**
     * Hook page-master-by-content (fop/CR-017): creates the pages holding only footnote bodies, which take the
     * owner of the page before them, not the part in force at the end of the list.
     */
    private void createOwnedFootnotePages(KnuthPageNode lastNode) {
        if (pageProvider != null && pageProvider.getOwnership().isActive()
                && lastNode.previous instanceof KnuthPageNode) {
            lastNode.pageOwner = ((KnuthPageNode) lastNode.previous).pageOwner;
            lastNode.opensOwner = false;
        }
        creatingFootnotePages = true;
        try {
            createFootnotePages(lastNode);
        } finally {
            creatingFootnotePages = false;
        }
    }

    private void createFootnotePages(KnuthPageNode lastNode) {

        insertedFootnotesLength = lastNode.insertedFootnotes;
        footnoteListIndex = lastNode.footnoteListIndex;
        footnoteElementIndex = lastNode.footnoteElementIndex;
        int availableBPD = getLineWidth(lastNode);
        int split = 0;
        KnuthPageNode prevNode = lastNode;

        // create pages containing the remaining footnote bodies
        while (insertedFootnotesLength < totalFootnotesLength) {
            if (totalFootnotesLength - insertedFootnotesLength <= availableBPD) {
                // All the remaining footnotes fit
                insertedFootnotesLength = totalFootnotesLength;
                footnoteListIndex = lengthList.size() - 1;
                footnoteElementIndex = getFootnoteList(footnoteListIndex).size() - 1;
            } else if ((split = getFootnoteSplit(
                        footnoteListIndex, footnoteElementIndex,
                         insertedFootnotesLength, availableBPD, true)) > 0) {
                // add a piece of a footnote
                availableBPD -= split;
                insertedFootnotesLength += split;
                // footnoteListIndex has already been set in getFootnoteSplit()
                // footnoteElementIndex has already been set in getFootnoteSplit()
            } else {
                // cannot add any content: create a new node and start again
                // @SuppressFBWarnings("BC_UNCONFIRMED_CAST_OF_RETURN_VALUE")
                KnuthPageNode node = (KnuthPageNode)
                                     createNode(lastNode.position, prevNode.line + 1, 1,
                                                insertedFootnotesLength - prevNode.insertedFootnotes,
                                                0, 0,
                                                0, 0, 0,
                                                0, 0, prevNode);
                addNode(node.line, node);
                removeNode(prevNode.line, prevNode);

                prevNode = node;
                availableBPD = getLineWidth(node);
            }
        }
        // create the last node
        // @SuppressFBWarnings("BC_UNCONFIRMED_CAST_OF_RETURN_VALUE")
        KnuthPageNode node = (KnuthPageNode)
                             createNode(lastNode.position, prevNode.line + 1, 1,
                                        totalFootnotesLength - prevNode.insertedFootnotes, 0, 0,
                                        0, 0, 0,
                                        0, 0, prevNode);
        addNode(node.line, node);
        removeNode(prevNode.line, prevNode);
    }

    /**
     * @return a list of {@link PageBreakPosition} elements
     *          corresponding to the computed page- and column-breaks
     */
    public LinkedList<PageBreakPosition> getPageBreaks() {
        return pageBreaks;
    }

    /**
     * Insert the given {@link PageBreakPosition} as the first
     * element in the list of page-breaks
     *
     * @param pageBreak the position to insert
     */
    public void insertPageBreakAsFirst(PageBreakPosition pageBreak) {
        if (pageBreaks == null) {
            pageBreaks = new LinkedList<PageBreakPosition>();
        }
        pageBreaks.addFirst(pageBreak);
    }

    /**
     * Removes all page breaks from the result list. This is used by block-containers and
     * static-content when it is only desired to know where there is an overflow but later the
     * whole content should be painted as one part.
     */
    public void removeAllPageBreaks() {
        if (pageBreaks == null || pageBreaks.isEmpty()) {
            return;
        }
        pageBreaks.subList(0, pageBreaks.size() - 1).clear();
    }

    /** {@inheritDoc} */
    @Override
    public void updateData1(int total, double demerits) {
    }

    /** {@inheritDoc} */
    @Override
    public void updateData2(KnuthNode bestActiveNode,
                            KnuthSequence sequence,
                            int total) {
        //int difference = (bestActiveNode.line < total)
        //      ? bestActiveNode.difference : bestActiveNode.difference + fillerMinWidth;
        // Check if the given node has an attached variant of a dynamic content
        KnuthPageNode pageNode = (KnuthPageNode) bestActiveNode;
        KnuthPageNode previousPageNode = ((KnuthPageNode) pageNode.previous);
        for (Variant var : previousPageNode.pendingVariants) {
            WhitespaceManagementPenalty penalty = var.getWhitespaceManagementPenalty();
            // A WMPenalty should not be activated more than once. The reason is simply
            // because a dynamic content cannot occupy multiple pages at the same time.
            if (!penalty.hasActiveVariant()) {
                penalty.setActiveVariant(var);
            }
        }
        int difference = bestActiveNode.difference;
        if (difference + bestActiveNode.availableShrink < 0) {
            if (!autoHeight) {
                if (layoutListener != null) {
                    layoutListener.notifyOverflow(bestActiveNode.line - 1, -difference, getFObj());
                }
            }
        }
        boolean isNonLastPage = (bestActiveNode.line < total);
        int blockAlignment = isNonLastPage ? alignment : alignmentLast;
        // it is always allowed to adjust space, so the ratio must be set regardless of
        // the value of the property display-align; the ratio must be <= 1
        double ratio = bestActiveNode.adjustRatio;
        if (ratio < 0) {
            // page break with a negative difference:
            // spaces always have enough shrink
            difference = 0;
        } else if (ratio <= 1 && isNonLastPage) {
            // not-last page break with a positive difference smaller than the available stretch:
            // spaces can stretch to fill the whole difference
            difference = 0;
        } else if (ratio > 1) {
            // not-last page with a positive difference greater than the available stretch
            // spaces can stretch to fill the difference only partially
            ratio = 1;
            difference -= bestActiveNode.availableStretch;
        } else {
            // last page with a positive difference:
            // spaces do not need to stretch
            if (blockAlignment != Constants.EN_JUSTIFY) {
                ratio = 0;
            } else {
                //Stretch as much as possible on last page
                difference = 0;
            }
        }

        // add nodes at the beginning of the list, as they are found
        // backwards, from the last one to the first one
        if (log.isDebugEnabled()) {
            log.debug("BBA> difference=" + difference + " ratio=" + ratio
                    + " position=" + bestActiveNode.position);
        }
        if (handlingFloat() && floatPosition == null) {
            floatPosition = new FloatPosition(this.topLevelLM, bestActiveNode.position, ratio, difference);
        } else {
            boolean useRelayedFootnotes = relayingFootnotes && bestActiveNode.previous.position == 0;
            // compute the indexes of the first footnote list and the first element in that list
            int firstListIndex = (useRelayedFootnotes) ? previousFootnoteListIndex
                    : ((KnuthPageNode) bestActiveNode.previous).footnoteListIndex;
            int firstElementIndex = (useRelayedFootnotes) ? previousFootnoteElementIndex
                    : ((KnuthPageNode) bestActiveNode.previous).footnoteElementIndex;
            if (useRelayedFootnotes) {
                previousFootnoteListIndex = -2;
                previousFootnoteElementIndex = -2;
                relayingFootnotes = false;
            }
            if (footnotesList != null && firstElementIndex == getFootnoteList(firstListIndex).size() - 1) {
                // advance to the next list
                firstListIndex++;
                firstElementIndex = 0;
            } else {
                firstElementIndex++;
            }
            PageBreakPosition pbp = new PageBreakPosition(this.topLevelLM, bestActiveNode.position,
                    firstListIndex, firstElementIndex, ((KnuthPageNode) bestActiveNode).footnoteListIndex,
                    ((KnuthPageNode) bestActiveNode).footnoteElementIndex, ratio, difference);
            // hook page-master-by-content: the owner of the part ending here, from the node that started it
            pbp.pageOwner = previousPageNode.pageOwner;
            pbp.opensOwner = previousPageNode.opensOwner;
            insertPageBreakAsFirst(pbp);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected int filterActiveNodes() {
        // leave only the active node with fewest total demerits
        KnuthNode bestActiveNode = null;
        for (int i = startLine; i < endLine; i++) {
            for (KnuthNode node = getNode(i); node != null; node = node.next) {
                if (favorSinglePart
                        && node.line > 1
                        && bestActiveNode != null
                        && Math.abs(bestActiveNode.difference) < bestActiveNode.availableShrink) {
                    //favor current best node, so just skip the current node because it would
                    //result in more than one part
                } else {
                    bestActiveNode = compareNodes(bestActiveNode, node);
                }
                if (node != bestActiveNode) {
                    removeNode(i, node);
                }
            }
        }
        assert (bestActiveNode != null);
        return bestActiveNode.line;
    }

    /**
     * Obtain the element-list corresponding to the footnote at the given index.
     *
     * @param index the index in the list of footnotes
     * @return  the element-list
     */
    protected final List<KnuthElement> getFootnoteList(int index) {
        return footnotesList.get(index);
    }

    /** @return the associated top-level formatting object. */
    public FObj getFObj() {
        return topLevelLM.getFObj();
    }

    /**
     * The height of the part starting at a node: of its page under the part of the page-sequence owning
     * it where there is one (hook page-master-by-content), else as {@link #getLineWidth(int)}.
     * @param node the node the part starts at
     * @return the available height
     */
    protected int getLineWidth(KnuthNode node) {
        if (pageProvider != null && node instanceof KnuthPageNode && ((KnuthPageNode) node).pageOwner != null) {
            KnuthPageNode pageNode = (KnuthPageNode) node;
            return pageProvider.getAvailableBPD(node.line, pageNode.pageOwner, pageNode.opensOwner);
        }
        return getLineWidth(node.line);
    }

    /** {@inheritDoc} */
    @Override
    protected int getLineWidth(int line) {
        int bpd;
        if (pageProvider != null) {
            bpd = pageProvider.getAvailableBPD(line);
        } else {
            bpd = super.getLineWidth(line);
        }
        if (log.isTraceEnabled()) {
            log.trace("getLineWidth(" + line + ") -> " + bpd);
        }
        return bpd;
    }

    /**
     * Interface to notify about layout events during page breaking.
     */
    public interface PageBreakingLayoutListener {

        /**
         * Issued when an overflow is detected
         * @param part the number of the part (page) this happens on
         * @param amount the amount by which the area overflows (in mpt)
         * @param obj the root FO object where this happens
         */
        void notifyOverflow(int part, int amount, FObj obj);

    }

    @Override
    protected KnuthNode recoverFromOverflow() {
        if (compareIPDs(getLastTooLong().line - 1) != 0) {
            /**
             * If the IPD of the next page changes, disable the recovery mechanism as the
             * inline content has to be re-laid out according to the new IPD anyway.
             */
            return getLastTooLong();
        } else {
            return super.recoverFromOverflow();
        }
    }

    /** {@inheritDoc} */
    @Override
    protected int getIPDdifference() {
        return ipdDifference;
    }

    /** {@inheritDoc} */
    @Override
    protected int handleIpdChange() {
        log.trace("Best node for ipd change:" + bestNodeForIPDChange);
        // TODO finish()
        /*
         * The third parameter is used to determine if this is the last page, so
         * if the content must be vertically justified or not. If we are here
         * this means that there is further content and the next page has a
         * different ipd. So tweak the parameter to fall into the non-last-page
         * case.
         */
        calculateBreakPoints(bestNodeForIPDChange, par, bestNodeForIPDChange.line + 1);
        activeLines = null;
        return bestNodeForIPDChange.line;
    }

    /**
     * Add a node at the end of the given line's existing active nodes.
     * If this is the first node in the line, adjust endLine accordingly.
     * @param line number of the line ending at the node's corresponding breakpoint
     * @param node the active node to add
     */
    @Override
    protected void addNode(int line, KnuthNode node) {
        if (node.position < par.size() - 1 && line > 0
                && (ipdDifference = compareIPDs(line - 1)) != 0) {
            log.trace("IPD changes at page " + line);
            if (bestNodeForIPDChange == null
                    || node.totalDemerits < bestNodeForIPDChange.totalDemerits) {
                bestNodeForIPDChange = node;
            }
        } else {
            if (node.position == par.size() - 1) {
                /*
                 * The whole sequence could actually fit on the last page before
                 * the IPD change. No need to do any special handling.
                 */
                ipdDifference = 0;
            } else if (line > 0 /*&& (bestNodeForLastPage == null
                     || node.totalDemerits < bestNodeForLastPage.totalDemerits)*/) {
                bestNodeForLastPage = node;
            }
            super.addNode(line, node);
        }
    }

    KnuthNode getBestNodeBeforeIPDChange() {
        return bestNodeForIPDChange;
    }

    private int compareIPDs(int line) {
        if (pageProvider == null) {
            return 0;
        }
        return pageProvider.compareIPDs(line);
    }

    KnuthNode getBestNodeForLastPage() {
        return bestNodeForLastPage;
    }

    protected boolean handlingFloat() {
        return (handlingStartOfFloat || handlingEndOfFloat);
    }

    protected void createForcedNodes(KnuthNode node, int line, int elementIdx, int difference, double r,
            double demerits, int fitnessClass, int availableShrink, int availableStretch, int newWidth,
            int newStretch, int newShrink) {
        if (pendingFloatTop >= 0 && !handlingFloat() && r <= -1 && !insideNonRestartable(elementIdx)) {
            // the page ends before an offset float's top: the float starts here, at the page's end (fop/CR-023)
            startPendingFloat();
        }
        if (handlingFloat() && r <= -1 && pageCannotHoldFloat(node)) {
            // hook float-offset (fop/CR-023): a page that cannot hold the float below its anchor is no start for
            // it; the node is too long as usual, and with no edge the algorithm restarts from the last too-short
            // break, the one before the anchor, where the anchor is handled again on the next page
            super.createForcedNodes(node, line, elementIdx, difference, r, demerits, fitnessClass, availableShrink,
                    availableStretch, newWidth, newStretch, newShrink);
            return;
        }
        if (handlingFloat()) {
            noteFloatTargetTop(node);
            if (bestFloatEdgeNode == null || demerits <= bestFloatEdgeNode.totalDemerits) {
                bestFloatEdgeNode = createNode(elementIdx, line + 1, fitnessClass, newWidth, newStretch,
                        newShrink, r, availableShrink, availableStretch, difference, demerits, node);
            }
        } else {
            super.createForcedNodes(node, line, elementIdx, difference, r, demerits, fitnessClass,
                    availableShrink, availableStretch, newWidth, newStretch, newShrink);
        }
    }

    protected int handleFloat() {
        calculateBreakPoints(bestFloatEdgeNode, par, bestFloatEdgeNode.line);
        activeLines = null;
        return bestFloatEdgeNode.line - 1;
    }

    protected KnuthNode getBestFloatEdgeNode() {
        return bestFloatEdgeNode;
    }

    protected FloatPosition getFloatPosition() {
        return floatPosition;
    }

    protected int getFloatHeight() {
        return floatHeight;
    }

    /** {@inheritDoc} */
    @Override
    protected boolean floatEdgeFound() {
        return bestFloatEdgeNode != null;
    }

    /** Whether the page starting at the node cannot hold an offset float below its anchor (fop/CR-023). */
    private boolean pageCannotHoldFloat(KnuthNode pageStart) {
        return handlingStartOfFloat && !handlingEndOfFloat && floatStartListTop >= 0 && floatFitsAnEmptyPage
                && floatStartListBottom - pageStart.totalWidth > lineWidth;
    }

    /** fop/CR-023: the largest fox:float-offset of the floats anchored in a box. */
    private static int floatOffsetOf(KnuthBlockBox box) {
        int offset = 0;
        for (FloatContentLayoutManager fclm : box.getFloatContentLMs()) {
            offset = Math.max(offset, fclm.getFloatOffset());
        }
        return offset;
    }

    /** The width of the first box with content after a box: the anchor's first line, for the anchor box (CR-023). */
    private int nextContentBoxWidth(KnuthBox box) {
        for (int i = par.indexOf(box) + 1; i < par.size(); i++) {
            KnuthElement e = getElement(i);
            if (e.isBox() && e.getWidth() > 0) {
                return e.getWidth();
            }
        }
        return 0;
    }

    /** @return the height of the first line of the anchor of the float whose start edge was just taken (fop/CR-023) */
    protected int getFloatAnchorLineHeight() {
        return floatAnchorLineHeight;
    }

    /** The lowest foot of the floats anchored in a box, below the anchor block's top: offset plus height (CR-023). */
    private static int floatBottomOf(KnuthBlockBox box) {
        int bottom = 0;
        for (FloatContentLayoutManager fclm : box.getFloatContentLMs()) {
            int height = fclm.getParent() instanceof FloatLayoutManager
                    ? ((FloatLayoutManager) fclm.getParent()).getFloatContentHeight() : 0;
            bottom = Math.max(bottom, fclm.getFloatOffset() + height);
        }
        return bottom;
    }

    /** The anchor block's own space-before (its optimum), for the floats anchored in a box (fop/CR-023). */
    private static int anchorSpaceBefore(KnuthBlockBox box) {
        for (FloatContentLayoutManager fclm : box.getFloatContentLMs()) {
            for (FONode node = fclm.getFObj(); node != null; node = node.getParent()) {
                if (node instanceof Block) {
                    return ((Block) node).getCommonMarginBlock().spaceBefore.getOptimum(null).getLength().getValue();
                }
            }
        }
        return 0;
    }

    /** The glue immediately before a box in the list: the resolved space before its block (fop/CR-023). */
    private int glueBefore(KnuthBox box) {
        int glue = 0;
        for (int i = par.indexOf(box) - 1; i >= 0; i--) {
            KnuthElement e = getElement(i);
            if (e.isBox()) {
                break;
            } else if (e.isGlue()) {
                glue += e.getWidth();
            }
        }
        return glue;
    }

    /** Whether the first line with content after this break would reach below the given height (fop/CR-023). */
    private boolean nextLineCrosses(int elementIdx, int top) {
        int bottom = totalWidth;
        for (int i = elementIdx; i < par.size(); i++) {
            KnuthElement e = getElement(i);
            if (e.isBox()) {
                if (e.getWidth() > 0) {
                    return bottom + e.getWidth() > top;
                }
            } else if (e.isGlue()) {
                bottom += e.getWidth();
            } else if (e.isForcedBreak() && i != elementIdx) {
                return true;
            }
        }
        return true;
    }

    /** The intrusion of an offset float starts at this break (fop/CR-023). */
    private void startPendingFloat() {
        handlingStartOfFloat = true;
        pendingFloatTop = -1;
    }

    /** The anchor block's top in the page: its list height less the page's start node's (fop/CR-023). */
    private void noteFloatTargetTop(KnuthNode pageStart) {
        if (handlingStartOfFloat && floatStartListTop >= 0) {
            floatAnchorTopInPage = floatAnchorListTop - pageStart.totalWidth;
        }
    }

    /** @return the anchor block's top in the page when an offset float's start edge was just taken, else -1 */
    protected int getFloatAnchorTopInPage() {
        return floatAnchorTopInPage;
    }

    /** The space between a float's edge forced by clear and the float's foot, else 0 (fop/CR-022). */
    protected int getFloatClearance() {
        return floatClearance;
    }

    protected boolean handlingStartOfFloat() {
        return handlingStartOfFloat;
    }

    protected boolean handlingEndOfFloat() {
        return handlingEndOfFloat;
    }

    /**
     * Deactivate the given node
     *
     * @param node  the node
     * @param line  the line number
     */
    protected void deactivateNode(KnuthNode node, int line) {
        super.deactivateNode(node, line);
        if (handlingEndOfFloat) {
            // the height at the edge, the glue before the next box included (fop/CR-020), and never above
            // the float's foot, which an edge forced by clear lies before: the difference is the clearance the
            // content after the edge is laid out below (fop/CR-022)
            int edge = Math.max(totalWidth, floatEdgeWidth);
            floatClearance = Math.max(0, floatHeight - edge);
            floatHeight = Math.max(floatHeight, edge);
        }
    }

    protected void disableFloatHandling() {
        handlingEndOfFloat = false;
        handlingStartOfFloat = false;
    }

    public void loadFootnotes(List fl, List<Integer> ll, int tfl, int ifl, boolean fp, boolean nf, int fnfi, int fli,
            int fei, MinOptMax fsl, int pfli, int pfei) {
        footnotesList = fl;
        lengthList = ll;
        totalFootnotesLength = tfl;
        insertedFootnotesLength = ifl;
        footnotesPending = fp;
        newFootnotes = nf;
        firstNewFootnoteIndex = fnfi;
        footnoteListIndex = fli;
        footnoteElementIndex = fei;
        footnoteSeparatorLength = fsl;
        previousFootnoteListIndex = pfli;
        previousFootnoteElementIndex = pfei;
        relayingFootnotes = !(previousFootnoteListIndex == -2 && previousFootnoteElementIndex == -2);
    }

    public void relayFootnotes(PageSequenceLayoutManager pslm) {
        if (!relayingFootnotes && bestFloatEdgeNode != null) {
            previousFootnoteListIndex = ((KnuthPageNode) bestFloatEdgeNode.previous).footnoteListIndex;
            previousFootnoteElementIndex = ((KnuthPageNode) bestFloatEdgeNode.previous).footnoteElementIndex;
        }
        pslm.holdFootnotes(footnotesList, lengthList, totalFootnotesLength, insertedFootnotesLength,
                footnotesPending, newFootnotes, firstNewFootnoteIndex, footnoteListIndex,
                footnoteElementIndex, footnoteSeparatorLength, previousFootnoteListIndex,
                previousFootnoteElementIndex);
    }
}
