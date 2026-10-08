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

/* Added by Plutext Pty Ltd for the docx4j FO renderer (docx4j-fo-renderer), a modified distribution derived from
 * Apache FOP 2.11: hook column-widths, the columns of unequal width before a span="all" block balanced by trial
 * (fop/CR-026 phase B). See README.md, "Changes from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.layoutmgr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.fop.area.Span;
import org.apache.fop.fo.Constants;
import org.apache.fop.layoutmgr.AbstractBreaker.BlockSequence;
import org.apache.fop.layoutmgr.inline.LineLayoutManager;

/**
 * Balances columns of unequal width before a {@code span="all"} block (hook column-widths, fop/CR-026 phase B).
 *
 * <p>With columns of one width FOP balances by dividing the content's height by the column count
 * ({@link BalancingColumnBreakingAlgorithm}). With columns of unequal width the content reflows at each column
 * boundary, and FOP's changing-IPD restart commits a column's areas before the next column is laid out, so the
 * balancing algorithm never sees the columns together. This class finds, by trial, the smallest height such that
 * the list laid out column by column with every column held to that height still ends on the page; the page
 * breaker then lays the columns out for real under that cap ({@link PageBreakingAlgorithm#setBalancingCap}).</p>
 *
 * <p>A trial lays out nothing on the page breaker's own layout managers: it reads the list again on a throwaway
 * flow layout manager made for the same {@code fo:flow}, positioned where the breaker's list began (the same
 * flow child, or the same restart inside a paragraph, replayed), and restarts that tree at each column boundary
 * the trial breaker finds. The breaker's managers are left as they were, so the areas of the columns are added
 * from them afterwards as usual, and a consumer's own line manager needs nothing of its state exposed.</p>
 */
final class UnequalColumnBalancer {

    private static final Log log = LogFactory.getLog(UnequalColumnBalancer.class);

    /** The search stops when the feasible and the infeasible height are this close, in millipoints. */
    private static final int TOLERANCE = 100;

    /** The most trials a search runs after the uncapped one. */
    private static final int MAX_TRIALS = 14;

    /** The most calls it takes a block to give its elements (a break-before is served first). */
    private static final int MAX_PRIMING_CALLS = 4;

    /**
     * How the page breaker read a list: at a flow child, or again from inside the list before (an inline-size
     * change, or the remainder of a non-restartable manager).
     */
    static final class ListOrigin {

        private final int flowChildIndex;
        private final Position position;
        private final LayoutManager restartLM;
        private final List<ListElement> firstElements;

        /** @param flowChildIndex the index of the flow child the list began at */
        ListOrigin(int flowChildIndex) {
            this.flowChildIndex = flowChildIndex;
            this.position = null;
            this.restartLM = null;
            this.firstElements = null;
        }

        /**
         * @param position the position the list was read again from
         * @param restartLM the manager it was read again from, if the restart was between managers
         * @param firstElements the elements of non-restartable managers carried over
         */
        ListOrigin(Position position, LayoutManager restartLM, List<ListElement> firstElements) {
            this.flowChildIndex = -1;
            this.position = position;
            this.restartLM = restartLM;
            this.firstElements = firstElements;
        }

        boolean isRestart() {
            return flowChildIndex < 0;
        }
    }

    /** What a trial found: whether the list ended on the page, and its parts' heights. */
    private static final class Trial {
        private boolean fits;
        private int maxHeight;
        private int sumHeight;
        private int parts;
    }

    private final PageBreaker breaker;
    private final PageSequenceLayoutManager pslm;
    private final PageProvider pageProvider;
    private final FlowLayoutManager flowLM;
    private final Map<LayoutManager, List<LeafPosition>> lineRestarts;
    private final LayoutContext childLC;
    private final int flowBPD;
    private final int alignment;
    private final int page;
    private final Span span;
    private final Set<LayoutManager> initialized = Collections.newSetFromMap(
            new IdentityHashMap<LayoutManager, Boolean>());

    /**
     * @param breaker the page breaker
     * @param flowLM the page breaker's flow layout manager
     * @param lineRestarts the restarts each of its line managers has taken, in order
     * @param childLC the breaker's layout context
     * @param flowBPD the height available to a part
     * @param alignment the block-progression alignment
     */
    UnequalColumnBalancer(PageBreaker breaker, FlowLayoutManager flowLM,
            Map<LayoutManager, List<LeafPosition>> lineRestarts, LayoutContext childLC, int flowBPD,
            int alignment) {
        this.breaker = breaker;
        this.pslm = breaker.pslm;
        this.pageProvider = breaker.getPageProvider();
        this.flowLM = flowLM;
        this.lineRestarts = lineRestarts;
        this.childLC = childLC;
        this.flowBPD = flowBPD;
        this.alignment = alignment;
        this.page = pslm.getCurrentPageNum();
        this.span = pslm.getCurrentPV().getCurrentSpan();
    }

    /**
     * Finds the smallest height the page's columns can be held to with the list still ending on the page.
     * @param origin how the page breaker read the list
     * @param list the list, laid out at the width of the column it begins in
     * @return the height in millipoints, or -1 where the list does not end on the page or no trial can be run
     */
    int findCap(ListOrigin origin, BlockSequence list) {
        int startColumn = span.getCurrentFlowIndex();
        if (hasAnchors(list)) {
            return -1;
        }
        try {
            Trial full = layTrial(origin, list, startColumn, Integer.MAX_VALUE);
            if (full == null) {
                log.debug("column balancing by trial: no trial could be run");
                return -1;
            }
            if (!full.fits) {
                log.debug("column balancing by trial: the list does not end on the page");
                return -1;
            }
            int hi = full.maxHeight;
            int lo = 0;
            int cap = full.sumHeight / span.getColumnCount();
            for (int i = 0; i < MAX_TRIALS && hi - lo > TOLERANCE; i++) {
                if (cap <= lo || cap >= hi) {
                    cap = (lo + hi) / 2;
                }
                Trial trial = layTrial(origin, list, startColumn, cap);
                if (trial == null) {
                    return -1;
                }
                if (trial.fits && trial.maxHeight <= cap) {
                    hi = Math.max(lo + 1, trial.maxHeight);
                } else {
                    lo = cap;
                }
                cap = (lo + hi) / 2;
            }
            if (log.isDebugEnabled()) {
                log.debug("column balancing by trial: cap " + hi + " (uncapped " + full.maxHeight + ")");
            }
            return hi;
        } finally {
            span.setCurrentFlowIndex(startColumn);
            pageProvider.setStartOfNextElementList(page, startColumn, false);
        }
    }

    /**
     * Lays the list out again on a throwaway tree, column by column under a cap on each column's height.
     * @return what was found, or null where the trial could not be run at all
     */
    private Trial layTrial(ListOrigin origin, BlockSequence list, int startColumn, int cap) {
        FlowLayoutManager fresh = pslm.getLayoutManagerMaker().makeFlowLayoutManager(pslm,
                pslm.getPageSequence().getMainFlow());
        int column = startColumn;
        List<ListElement> elements = replayOrigin(fresh, origin, column);
        if (elements == null) {
            return null;
        }
        BlockSequence seq = breaker.toBlockSequence(elements, Constants.EN_ANY);
        if (seq == null || seq.size() != list.size()) {
            if (log.isDebugEnabled()) {
                log.debug("column balancing by trial: the list read again differs ("
                        + (seq == null ? 0 : seq.size()) + " elements against " + list.size() + ")");
            }
            return null;
        }
        Trial trial = new Trial();
        int columnCount = span.getColumnCount();
        while (true) {
            if (hasAnchors(seq)) {
                return null;
            }
            PageBreakingAlgorithm alg = breaker.createPageBreakingAlgorithm(trialContext(column), false, null);
            alg.setConstantLineWidth(flowBPD);
            alg.setBalancingCap(cap, true);
            int parts = alg.findBreakingPoints(seq, 1, true, BreakingAlgorithm.ALL_BREAKS);
            addHeights(trial, alg, seq, parts);
            boolean ipdChange = alg.getIPDdifference() != 0;
            if (log.isDebugEnabled()) {
                log.debug("column balancing trial: cap " + cap + ", column " + column + ": " + parts
                        + " part(s), ipd change " + ipdChange + ", max height so far " + trial.maxHeight);
            }
            if (column + parts > columnCount || (ipdChange && column + parts >= columnCount)) {
                trial.fits = false;
                return trial;
            }
            if (!ipdChange) {
                trial.fits = true;
                return trial;
            }
            column += parts;
            seq = readAgain(fresh, alg, seq, column);
            if (seq == null) {
                trial.fits = false;
                return trial;
            }
        }
    }

    /** Reads the list again from the trial breaker's break, at the next column's width, on the throwaway tree. */
    private BlockSequence readAgain(FlowLayoutManager fresh, PageBreakingAlgorithm alg, BlockSequence seq,
            int column) {
        BreakingAlgorithm.KnuthNode node = alg.getBestNodeBeforeIPDChange();
        Position atBreak = positionAfter(alg, node.position);
        if (atBreak == null || breaker.containsNonRestartableLM(atBreak)) {
            // the break lies in a table or a list: not restarted in a trial (production would carry the
            // manager's remaining elements over); this cap counts as infeasible
            log.debug("column balancing trial: the break is in a non-restartable manager");
            return null;
        }
        breaker.firstElementsForRestart = null;
        RestartAtLM restartAtLMClass = new RestartAtLM();
        LayoutManager restartLM = restartAtLMClass.getRestartAtLM(breaker, alg, true, false, false, seq, 1);
        if (restartAtLMClass.invalidPosition) {
            return null;
        }
        if (restartLM == null || restartLM.getChildLMs().isEmpty()) {
            breaker.firstElementsForRestart = null;
            LayoutManager restartLM2 = new RestartAtLM().getRestartAtLM(breaker, alg, true, false, false, seq, 0);
            if (restartLM2 != null) {
                restartLM = restartLM2;
            }
        }
        Position position = breaker.positionAtBreak;
        List<ListElement> first = breaker.firstElementsForRestart;
        if (position == null || !onTree(fresh, position.getLM())
                || (restartLM != null && !onTree(fresh, restartLM))) {
            if (log.isDebugEnabled()) {
                log.debug("column balancing trial: the restart position is not on the trial tree: " + position
                        + ", " + restartLM);
            }
            return null;
        }
        LayoutContext lc = trialContext(column);
        List<ListElement> elements = fresh.getNextKnuthElements(lc, alignment, position, restartLM);
        if (elements == null) {
            log.debug("column balancing trial: nothing read again");
            return null;
        }
        if (first != null) {
            elements.addAll(0, first);
        }
        return breaker.toBlockSequence(elements, Constants.EN_COLUMN);
    }

    /** The original position of the first element at or after an index that carries one, as RestartAtLM finds it. */
    private static Position positionAfter(PageBreakingAlgorithm alg, int index) {
        for (int i = index; i < alg.par.size(); i++) {
            KnuthElement element = alg.getElement(i);
            if (element.getPosition() == null) {
                element = alg.getElement(0);
            }
            Position position = element.getPosition();
            if (position != null && position.getPosition() != null) {
                return position.getPosition();
            }
        }
        return null;
    }

    /**
     * Reads the list the page breaker holds again on the throwaway tree, from where the breaker's list began.
     * @return the elements, or null where the origin cannot be replayed
     */
    private List<ListElement> replayOrigin(FlowLayoutManager fresh, ListOrigin origin, int column) {
        LayoutContext lc = trialContext(column);
        if (!origin.isRestart()) {
            LayoutManager child = freshChild(fresh, origin.flowChildIndex);
            if (child == null) {
                return null;
            }
            init(child);
            fresh.setCurrentChildLM(child);
            List<ListElement> elements = fresh.getNextKnuthElements(lc, alignment);
            if (elements != null && isForcedBreakAlone(elements)) {
                // a block serves its break-before on its first call and its content on the second: the
                // breaker's list came from the second
                elements = fresh.getNextKnuthElements(lc, alignment);
            }
            return elements;
        }
        if (origin.position == null || origin.position.getLM() == null) {
            // the list was the remainder of a non-restartable manager alone
            return null;
        }
        boolean resetPath = origin.restartLM != null && origin.restartLM.getParent() == flowLM;
        LayoutManager freshRestartLM = null;
        if (origin.restartLM != null) {
            freshRestartLM = map(fresh, origin.restartLM);
            if (freshRestartLM == null) {
                return null;
            }
        }
        LayoutManager freshPositionLM = map(fresh, origin.position.getLM());
        if (freshPositionLM == null) {
            return null;
        }
        LayoutManager freshFlowChild = flowChildOf(fresh, resetPath ? freshRestartLM : freshPositionLM);
        if (freshFlowChild == null) {
            return null;
        }
        init(freshFlowChild);
        if (!resetPath) {
            // the block is laid out once, as the breaker's was, so that its line managers hold their
            // paragraphs; then the line manager at the position takes the restarts the breaker's took
            if (!prime(fresh, freshFlowChild, lc)) {
                return null;
            }
            if (freshPositionLM instanceof LineLayoutManager
                    && !replayLineRestarts((LineLayoutManager) freshPositionLM, origin.position.getLM(), lc)) {
                return null;
            }
        }
        Position freshPosition;
        if (origin.position instanceof LeafPosition) {
            LeafPosition leaf = (LeafPosition) origin.position;
            freshPosition = new LeafPosition(freshPositionLM, leaf.getLeafPos(), leaf.getIndex());
        } else {
            freshPosition = new Position(freshPositionLM, origin.position.getIndex());
        }
        List<ListElement> elements = fresh.getNextKnuthElements(lc, alignment, freshPosition, freshRestartLM);
        if (elements == null) {
            return null;
        }
        if (origin.firstElements != null) {
            elements.addAll(0, origin.firstElements);
        }
        return elements;
    }

    /** Lays a flow child out once, discarding the elements. */
    private boolean prime(FlowLayoutManager fresh, LayoutManager child, LayoutContext lc) {
        LayoutContext blockLC = fresh.makeChildLayoutContext(lc);
        for (int i = 0; i < MAX_PRIMING_CALLS && !child.isFinished(); i++) {
            child.getNextKnuthElements(blockLC, alignment);
        }
        return child.isFinished();
    }

    /**
     * Takes the fresh line manager through the restarts the breaker's line manager took before the one that
     * began the breaker's list, so that its paragraphs are cut as the breaker's are.
     */
    private boolean replayLineRestarts(LineLayoutManager fresh, LayoutManager original, LayoutContext lc) {
        List<LeafPosition> restarts = lineRestarts.get(original);
        if (restarts == null || restarts.isEmpty()) {
            return false;
        }
        LayoutContext lineLC = LayoutContext.copyOf(lc);
        for (int i = 0; i < restarts.size() - 1; i++) {
            LeafPosition restart = restarts.get(i);
            LeafPosition at = restart == null ? null : new LeafPosition(fresh, restart.getLeafPos(),
                    restart.getIndex());
            fresh.getNextKnuthElements(lineLC, alignment, at);
        }
        return true;
    }

    /** The manager of the throwaway tree in the place of one of the breaker's, children made as needed. */
    private LayoutManager map(FlowLayoutManager fresh, LayoutManager original) {
        List<Integer> path = new ArrayList<Integer>();
        LayoutManager lm = original;
        while (lm != flowLM) {
            LayoutManager parent = lm.getParent();
            if (parent == null) {
                return null;
            }
            int index = parent.getChildLMs().indexOf(lm);
            if (index < 0) {
                return null;
            }
            path.add(0, index);
            lm = parent;
        }
        LayoutManager at = fresh;
        for (int index : path) {
            at = child(at, index);
            if (at == null) {
                return null;
            }
        }
        return at;
    }

    /**
     * A manager's child by index, made if it does not exist yet. The parent is initialized first, since a block
     * makes its line managers with the measures its initialization reads; the child is initialized by its
     * parent's own iteration when laid out, or here when it is made a parent in turn.
     */
    private LayoutManager child(LayoutManager parent, int index) {
        init(parent);
        List<LayoutManager> children = parent.getChildLMs();
        if (children.size() <= index && !parent.createNextChildLMs(index)) {
            return null;
        }
        children = parent.getChildLMs();
        return children.size() > index ? children.get(index) : null;
    }

    private LayoutManager freshChild(FlowLayoutManager fresh, int index) {
        return child(fresh, index);
    }

    /** Initializes a manager of the throwaway tree once. */
    private void init(LayoutManager lm) {
        if (initialized.add(lm)) {
            lm.initialize();
        }
    }

    /** The flow child a manager of the throwaway tree lies under. */
    private static LayoutManager flowChildOf(FlowLayoutManager fresh, LayoutManager lm) {
        LayoutManager at = lm;
        while (at != null && at.getParent() != fresh) {
            at = at.getParent();
        }
        return at;
    }

    /** Whether a manager is the throwaway flow or lies under it (a restart between flow children names the flow). */
    private static boolean onTree(FlowLayoutManager fresh, LayoutManager lm) {
        return lm == fresh || flowChildOf(fresh, lm) != null;
    }

    /** A layout context for a trial list in a column, the breaker's with the column's width and span. */
    private LayoutContext trialContext(int column) {
        span.setCurrentFlowIndex(column);
        pageProvider.setStartOfNextElementList(page, column, false);
        LayoutContext lc = LayoutContext.copyOf(childLC);
        lc.restoreSpan(childLC.getCurrentSpan());
        lc.setRefIPD(pslm.getCurrentColumnWidth());
        return lc;
    }

    private static void addHeights(Trial trial, PageBreakingAlgorithm alg, KnuthSequence par, int parts) {
        List<AbstractBreaker.PageBreakPosition> breaks = alg.getPageBreaks();
        int start = par.getFirstBoxIndex(0);
        for (int p = 0; p < parts && p < breaks.size(); p++) {
            int end = breaks.get(p).getLeafPos();
            int height = ElementListUtils.calcContentLength(par, start, end);
            KnuthElement last = (KnuthElement) par.get(end);
            if (last.isPenalty()) {
                height += last.getWidth();
            }
            trial.maxHeight = Math.max(trial.maxHeight, height);
            trial.sumHeight += height;
            trial.parts++;
            start = par.getFirstBoxIndex(end + 1);
        }
    }

    /** Whether a list is a forced break and nothing else (a block's break-before, served ahead of its content). */
    private static boolean isForcedBreakAlone(List<ListElement> elements) {
        if (!ElementListUtils.endsWithForcedBreak(elements)) {
            return false;
        }
        for (ListElement element : elements) {
            if (element.isBox()) {
                return false;
            }
        }
        return true;
    }

    /** Whether a list carries footnotes or floats, which a trial does not lay out. */
    private static boolean hasAnchors(List<ListElement> list) {
        for (ListElement element : list) {
            if (element instanceof KnuthBlockBox) {
                KnuthBlockBox box = (KnuthBlockBox) element;
                if (box.hasAnchors() || box.hasFloatAnchors()) {
                    return true;
                }
            }
        }
        return false;
    }
}
