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
 * Apache FOP 2.12: Word mode's line manager, moved from docx4j-export-fo's org.docx4j.fop.wordlayout (fop/CR-031,
 * docx4j CR-071). See README.md, "Changes from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.layoutmgr.inline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.fop.area.LineArea;
import org.apache.fop.area.inline.InlineArea;
import org.apache.fop.datatypes.Length;
import org.apache.fop.fo.Constants;
import org.apache.fop.fo.flow.Block;
import org.apache.fop.fonts.Font;
import org.apache.fop.fonts.FontInfo;
import org.apache.fop.fonts.FontTriplet;
import org.apache.fop.layoutmgr.BreakingAlgorithm;
import org.apache.fop.layoutmgr.KnuthBox;
import org.apache.fop.layoutmgr.KnuthElement;
import org.apache.fop.layoutmgr.KnuthGlue;
import org.apache.fop.layoutmgr.KnuthPenalty;
import org.apache.fop.layoutmgr.KnuthSequence;
import org.apache.fop.layoutmgr.LayoutContext;
import org.apache.fop.layoutmgr.LayoutManager;
import org.apache.fop.layoutmgr.LeafPosition;
import org.apache.fop.layoutmgr.ListElement;
import org.apache.fop.layoutmgr.NonLeafPosition;
import org.apache.fop.layoutmgr.Position;
import org.apache.fop.layoutmgr.word.WordBreakOpportunities;
import org.apache.fop.layoutmgr.word.WordLayoutElementMapping;
import org.apache.fop.layoutmgr.word.WordLayoutSettings;
import org.apache.fop.traits.MinOptMax;

/**
 * The line manager of Word mode (fop/CR-031, docx4j CR-071): FOP's {@link LineLayoutManager} with Word's greedy
 * line breaking and the line rules around it, made by {@code BlockLayoutManager} where the user agent has Word mode
 * on ({@code FOUserAgent.getWordLayout()}). Moved from docx4j-export-fo, where it was a copy of FOP 2.11's line
 * manager: here it holds only Word's members, and FOP's own are its superclass's, whose seams it overrides (each
 * marked "Word mode's seam" there) and whose state is package-private for it.
 *
 * <p>Word breaks lines greedily: each line takes every word that fits and breaks at the last opportunity before
 * the first that does not ({@link WordLineBreakingAlgorithm}). Around that: the line box and its leading below the
 * text ({@code docx4j:line-box}, {@code docx4j:baseline}, {@code docx4j:line-rule}), tab stops and leaders
 * ({@code docx4j:tabs}), the hyphenation zone, letter spacing as Word measures it, the emergency break inside a
 * word too long for any line, breaks at the seam of two runs and after a solidus, and widows and orphans counted
 * across soft returns. Each rule's history and measurement is in its comment; the @since tags are docx4j's
 * releases.</p>
 */
public class WordLineLayoutManager extends LineLayoutManager {

    private static final Log log = LogFactory.getLog(WordLineLayoutManager.class);

    /** One 1/300 inch twips: Word's default hyphenation zone, 0.25in (docx4j's HyphenationSettings). */
    private static final int DEFAULT_HYPHENATION_ZONE_TWIPS = 360;

    /** The {@code docx4j:tab} values of a positional tab ({@code w:ptab}), as docx4j writes them. */
    private static final String TAB_PTAB_RIGHT = "ptab-right";
    private static final String TAB_PTAB_CENTER = "ptab-center";

    /** Word mode's settings, which the initializers below read: declared first. */
    private final WordLayoutSettings settings = settingsOf(fobj);

    /** The leader shaping, with the settings' leader grid. */
    private final WordLeaders leaders = new WordLeaders(settings.leaderGrid());

    private static WordLayoutSettings settingsOf(Block block) {
        WordLayoutSettings s = block.getUserAgent().getWordLayout();
        return s != null ? s : new WordLayoutSettings(null);
    }

    /**
     * Word's greedy (first-fit) line breaking, in FOP's line breaking algorithm, whose state and line positions it
     * keeps: {@link #findBreakingPoints} commits each line at the last legal break that fits.
     */
    class WordLineBreakingAlgorithm extends LineBreakingAlgorithm {

        WordLineBreakingAlgorithm(int pageAlign, int textAlign, int textAlignLast, int indent, int fillerWidth,
                int lh, int ld, int fl, boolean first, int maxFlagCount, LineLayoutManager llm) {
            super(pageAlign, textAlign, textAlignLast, indent, fillerWidth, lh, ld, fl, first, maxFlagCount, llm);
        }

        @Override
        int lineTextAlign(KnuthNode node, KnuthSequence par, int textAlign) {
            int from = node.line > 1 ? node.previous.position + 1 : 0;
            return lineHasTab(par, from, node.position) ? alignmentForTabLine(textAlign, par, from, node.position)
                    : textAlign;
        }

        /** the width the line is aligned by: computeDifference adds the tab overhang, so a line running on to a
         *  stop past the available width would otherwise be shifted further still (see alignDifference) */
        @Override
        int lineAlignDifference(KnuthNode node, KnuthSequence par, int difference) {
            int from = node.line > 1 ? node.previous.position + 1 : 0;
            return lineHasTab(par, from, node.position) ? alignDifference(node, difference) : difference;
        }

        /**
         * CR-023 section 4.2's mark, but not on a line that holds nothing but white space: Word lays an empty
         * paragraph beside a float however little room it leaves, where the mark would set it below the float
         * (12_es-ES 8985: ten empty paragraphs between two floating tables that fill the column; fop/CR-031 §6.2).
         */
        @Override
        boolean marksOverflowBesideFloat(KnuthSequence par, int from, int to) {
            for (int i = Math.max(0, from); i <= to && i < par.size(); i++) {
                Object e = par.get(i);
                if (e instanceof KnuthBox && !((KnuthBox) e).isAuxiliary() && ((KnuthBox) e).getWidth() > 0) {
                    org.apache.fop.fonts.GlyphMapping m = mappingOf(e);
                    if (m == null || !m.isSpace) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        boolean hasWordLineBox() {
            return wordLineBox > 0;
        }

        @Override
        LineBreakPosition makeWordLineBreakPosition(KnuthSequence par, int firstElementIndex,
                int lastElementIndex, int availableShrink, int availableStretch, int difference, double ratio,
                int startIndent, int endIndent) {
            int[] line = wordLine(par, firstElementIndex, lastElementIndex);
            return new LineBreakPosition(thisLLM, knuthParagraphs.indexOf(par), firstElementIndex,
                    lastElementIndex, availableShrink, availableStretch, difference, ratio, 0, startIndent,
                    endIndent, line[0], ipd, 0, line[1], line[2]);
        }

        // ---- Word-style greedy (first-fit) line breaking ------------------------

        /** The single active node: the last committed break. */
        private KnuthNode active;
        /** The last legal break at which the line still fitted, and the totals then. */
        private int candIdx = -1;
        private int candDifference;
        private double candRatio;
        private int candShrink;
        private int candStretch;
        private int candTotalWidth;
        private int candTotalStretch;
        private int candTotalShrink;
        private boolean restartRequested;
        /** The last flagged (hyphenation) penalty at which the line still fitted, and
         *  the totals then.  It always lies inside the word that follows candIdx: a
         *  whole-word break supersedes the hyphenation points before it. */
        private int hyphIdx = -1;
        private int hyphDifference;
        private double hyphRatio;
        private int hyphShrink;
        private int hyphStretch;
        private int hyphTotalWidth;
        private int hyphTotalStretch;
        private int hyphTotalShrink;
        /** How many committed lines in a row ended in a hyphen (w:consecutiveHyphenLimit). */
        private int consecutiveHyphens;
        /** How far past the available width a tab stop on the line being built reaches.
         *  Word honours a stop beyond the paragraph's right indent (measured: a footer
         *  with w:ind right=360 and a right stop at the full text width puts its page
         *  number on the stop, in the indent, and does not wrap), so the line is allowed
         *  that much more. */
        private int tabOverhang;
        /** The tabOverhang each committed line ended with, which updateData2 takes back
         *  off the node's difference before aligning the line (alignDifference). */
        private final java.util.IdentityHashMap<KnuthNode, Integer> nodeTabOverhang
                = new java.util.IdentityHashMap<KnuthNode, Integer>();

        @Override
        public int findBreakingPoints(KnuthSequence par, int startIndex,
                                      double threshold, boolean force, int allowedBreaks) {
            this.par = par;
            initialize();
            boolean previousIsBox = false;
            int previousPosition = startIndex;
            if (alignment != Constants.EN_CENTER) {
                int firstBoxIndex = startIndex;
                while (firstBoxIndex < par.size() && !((KnuthElement) par.get(firstBoxIndex)).isBox()) {
                    firstBoxIndex++;
                }
                previousPosition = (firstBoxIndex >= par.size()) ? startIndex : firstBoxIndex - 1;
            }
            previousPosition = Math.max(0, previousPosition);
            active = createNode(previousPosition, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, null);
            addNode(0, active);
            candIdx = -1;
            hyphIdx = -1;
            consecutiveHyphens = 0;
            restartRequested = false;
            tabOverhang = 0;
            nodeTabOverhang.clear();
            if (WordLineLayoutManager.log.isTraceEnabled()) {
                StringBuilder sb = new StringBuilder("greedy paragraph, " + par.size() + " elements, lineWidth="
                        + getLineWidth() + ":");
                for (int i = 0; i < par.size(); i++) {
                    KnuthElement e = (KnuthElement) par.get(i);
                    sb.append("\n  [").append(i).append("] ").append(e.isBox() ? "box " : e.isGlue() ? "glue " : "pen ")
                      .append("w=").append(e.getWidth());
                    if (e.isGlue()) {
                        sb.append(" st=").append(e.getStretch()).append(" sh=").append(e.getShrink());
                    }
                    if (e.isPenalty()) {
                        sb.append(" p=").append(e.getPenalty()).append(e.isForcedBreak() ? " FORCED" : "");
                    }
                    if (e.isAuxiliary()) {
                        sb.append(" aux");
                    }
                }
                WordLineLayoutManager.log.trace(sb.toString());
            }
            for (int elementIndex = startIndex; elementIndex < par.size(); elementIndex++) {
                previousIsBox = handleElementAt(elementIndex, previousIsBox, allowedBreaks).isBox();
                if (restartRequested) {
                    restartRequested = false;
                    elementIndex = restartFrom(active, elementIndex);
                }
            }
            finish();
            KnuthNode last = active;
            updateData1(last.line, last.totalDemerits);
            greedyBreakPoints(last, par, last.line);
            activeLines = null;
            return last.line;
        }

        /** As BreakingAlgorithm.calculateBreakPoints: from the last line backwards,
         *  which is the order LineLayoutPossibilities.addBreakPosition expects. */
        private void greedyBreakPoints(KnuthNode node, KnuthSequence par, int total) {
            KnuthNode n = node;
            for (int i = node.line; i > 0; i--) {
                updateData2(n, par, total);
                n = n.previous;
            }
        }

        @Override
        protected void considerLegalBreak(KnuthElement element, int elementIdx) {
            if (active == null || active.position == elementIdx) {
                return;
            }
            int difference = computeDifference(active, element, elementIdx);
            if (!elementCanEndLine(element, endLine, difference)) {
                return;
            }
            double r = computeAdjustmentRatio(active, difference);
            int availableShrink = totalShrink - active.totalShrink;
            int availableStretch = totalStretch - active.totalStretch;
            if (WordLineLayoutManager.log.isTraceEnabled()) {
                WordLineLayoutManager.log.trace("greedy idx=" + elementIdx + " diff=" + difference + " lineWidth="
                        + getLineWidth()
                        + " active.pos=" + active.position + " cand=" + candIdx + " forced=" + element.isForcedBreak()
                        + " align=" + alignment + " justify=" + Constants.EN_JUSTIFY + " maxShrink=" + maxSpaceShrink
                        + " el=" + element);
            }
            boolean fits = difference >= 0
                    || (fitsByShrinkingSpaces(elementIdx, difference) && worthCompressing(elementIdx, difference));
            // a break inside an over-long word is Word's last resort, and only once the
            // word has a line to itself; it must still be measured, though, or the line
            // holding it would never be found too long (see emergencyUsable)
            boolean usable = emergencyUsable(element, elementIdx);
            if (!fits && commitLastFitting()) {
                // too long now (whether or not this is the paragraph's own forced break):
                // break at the last break that fitted - hyphenating the word that did not
                // fit where Word would - and rescan from there; a forced break is met
                // again after the rescan
                return;
            }
            if (element.isForcedBreak()) {
                commit(elementIdx, difference, r, availableShrink, availableStretch,
                        totalWidth, totalStretch, totalShrink, false);
                return;
            }
            if (fits) {
                if (!usable) {
                    return;
                }
                // fits at its natural width, or (justified) with the spaces compressed
                // within Word's limit: remember, keep going
                if (isHyphenationPoint(element)) {
                    if (!hyphenFits(elementIdx, difference)) {
                        // Word pays much less to take a longer hyphenation fragment than
                        // it pays to pull a whole word on; the last fragment that was
                        // within maxHyphenSpaceShrink stays the candidate
                        return;
                    }
                    hyphIdx = elementIdx;
                    hyphDifference = difference;
                    hyphRatio = r;
                    hyphShrink = availableShrink;
                    hyphStretch = availableStretch;
                    hyphTotalWidth = totalWidth;
                    hyphTotalStretch = totalStretch;
                    hyphTotalShrink = totalShrink;
                    return;
                }
                candIdx = elementIdx;
                candDifference = difference;
                candRatio = r;
                candShrink = availableShrink;
                candStretch = availableStretch;
                candTotalWidth = totalWidth;
                candTotalStretch = totalStretch;
                candTotalShrink = totalShrink;
                // any hyphenation point before a whole word that fits is of no interest
                hyphIdx = -1;
                return;
            }
            if (!usable) {
                return;
            }
            // nothing fitted since the last break: an overlong word; break here, overfull
            commit(elementIdx, difference, r, availableShrink, availableStretch,
                    totalWidth, totalStretch, totalShrink, false);
        }

        /**
         * Whether a break inside an over-long word may be taken here.
         *
         * <p>Word's emergency break is a last resort in two senses: it breaks a word only
         * when the word cannot fit on any line, and only once the word has a line to
         * itself.  Measured on a corpus golden, the line before such a word ends 85pt
         * short of the measure rather than taking the word's head, so the ordinary break
         * before the word has to win while anything else is on the line.
         *
         * @since 17.1.0
         */
        private boolean emergencyUsable(KnuthElement element, int elementIdx) {
            Object word = emergencyElement.get(element);
            if (word == null) {
                return true;
            } // an ordinary break
            // inclusive: the first line's node is the paragraph's first box, not a break
            for (int i = active.position; i < elementIdx; i++) {
                KnuthElement el = getElement(i);
                if (emergencyElement.get(el) == word) {
                    continue;
                }
                // only a box holds text; the glue a line begins with is its discarded
                // leading space, and penalties stand for nothing
                if (el.isBox() && !el.isAuxiliary() && el.getWidth() > 0) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Word compresses the spaces of a justified line to pull in a word that does
         * not fit at natural width (measured, Word 365: glyphs keep their width, the
         * gaps go down to about 71-80% of the space width, depending on the font).
         * The limit is a fraction of the natural width of the line's spaces, set by
         * settings.maxSpaceShrink (default 0.24).
         */
        private boolean fitsByShrinkingSpaces(int elementIdx, int difference) {
            return fitsByShrinkingSpaces(elementIdx, difference, maxSpaceShrink);
        }

        private boolean fitsByShrinkingSpaces(int elementIdx, int difference, double maxShrink) {
            if (alignment != Constants.EN_JUSTIFY || difference >= 0) {
                return difference >= 0;
            }
            if (maxShrink <= 0) {
                return false;
            }
            return -difference <= maxShrink * spacesWidth(elementIdx);
        }

        /**
         * The natural width of the spaces between the last break and elementIdx, after the
         * line's last tab.  A tab reaches the breaker as glue (its leader's), and was counted
         * as space: on a numbered paragraph's first line ("3.6&lt;tab&gt;To purchase...",
         * ind left 567 hanging 567, the tab about 12pt) the compression worked out over tab
         * and spaces came to some 77% of what the spaces alone took, inside the cap, where the
         * spaces were really compressed 26-31%.  Word measures after the tab (rules §4.2, the
         * justified-compression-decision probe's numbered lines) and refused those lines:
         * corpus document 1035, four first lines and their re-wraps, 17 lines (ledger9 part C
         * §49).  @since 17.3.1 for the tab
         */
        private int spacesWidth(int elementIdx) {
            int spaces = 0;
            for (int i = active.position + 1; i < elementIdx; i++) {
                KnuthElement e = getElement(i);
                if (!e.isGlue()) {
                    continue;
                }
                if (elementLM(e) instanceof org.apache.fop.layoutmgr.inline.LeaderLayoutManager) {
                    spaces = 0; // a tab: only the spaces after it are compressed
                    continue;
                }
                if (e.getWidth() > 0) {
                    spaces += e.getWidth();
                }
            }
            return spaces;
        }

        /**
         * Whether Word would compress this line rather than break at the last
         * opportunity that fitted.  Being inside {@link #maxSpaceShrink} is not enough:
         * Word compresses only where the compression, as a fraction of this line's
         * natural space width, is less than {@link #compressionToStretch} (0.5) of the
         * stretch the alternative line would take, as a fraction of its own.  Measured on
         * the justified-compression-decision probe (CR-001 batch 50, word-layout-rules
         * §4.2): 159 lines in three faces, 6 to 36 spaces, numbered first lines among
         * them - the boundary is c = s/2 in every face, whatever the number of spaces.
         * {@link #minStretchToCompress}, the 17.1.0 rule, is a floor under it (default 0).
         *
         * @since 17.1.0, 17.3.1 the ratio
         */
        private boolean worthCompressing(int elementIdx, int difference) {
            if (candIdx < 0) {
                return true;
            } // nothing has fitted on this line yet
            int spaces = spacesWidth(candIdx);
            if (spaces <= 0) {
                return true;
            }
            if (minStretchToCompress > 0 && candDifference <= minStretchToCompress * spaces) {
                return false;
            }
            if (compressionToStretch <= 0) {
                return true;
            }
            int compressedSpaces = spacesWidth(elementIdx);
            if (compressedSpaces <= 0) {
                return true;
            }
            // c < ratio x s, where c = -difference / compressedSpaces and s = candDifference / spaces
            return (double) -difference * spaces < compressionToStretch * candDifference * compressedSpaces;
        }

        /**
         * Whether Word would take a hyphenation fragment this long.  It compresses a
         * justified line's spaces by up to {@link #maxSpaceShrink} to pull a whole word
         * onto the line, but by much less to take a longer piece of a word it is
         * hyphenating anyway: measured against the goldens of the two hyphenation
         * probes, Word accepted fragments costing 1.2% and 6.0% of the line's spaces and
         * rejected 13.5%, 14.5%, 22.0% and 25.6% (see
         * {@code docx4j.convert.out.fo.wordLayout.maxHyphenSpaceShrink}).
         *
         * @since 17.1.0
         */
        private boolean hyphenFits(int elementIdx, int difference) {
            return difference >= 0
                    || fitsByShrinkingSpaces(elementIdx, difference, maxHyphenSpaceShrink);
        }

        // ---- Word's hyphenation -------------------------------------------------

        /** A flagged penalty is a hyphenation point: findHyphenationPoints put one at
         *  every place the patterns allow the word to be divided. */
        private boolean isHyphenationPoint(KnuthElement element) {
            return element.isPenalty() && ((KnuthPenalty) element).isPenaltyFlagged();
        }

        /**
         * The word being tried does not fit.  Break at the last opportunity that did,
         * as Word does; where the hyphenation zone allows it, that opportunity is a
         * hyphenation point inside the word rather than the space before it.
         *
         * @return false where nothing at all has fitted since the last break (an
         *         overlong word, which the caller then breaks overfull)
         */
        private boolean commitLastFitting() {
            if (hyphIdx > active.position && hyphenationAllowed()) {
                commit(hyphIdx, hyphDifference, hyphRatio, hyphShrink, hyphStretch,
                        hyphTotalWidth, hyphTotalStretch, hyphTotalShrink, true);
                return true;
            }
            if (candIdx > active.position) {
                commit(candIdx, candDifference, candRatio, candShrink, candStretch,
                        candTotalWidth, candTotalStretch, candTotalShrink, true);
                return true;
            }
            return false;
        }

        /**
         * Whether the word that did not fit may be hyphenated.
         *
         * <p>w:consecutiveHyphenLimit caps how many lines in a row may end in a hyphen
         * (0 = no limit); it is counted within the paragraph, which is as far as one
         * line manager sees.
         *
         * <p>w:hyphenationZone - the largest gap Word is said to tolerate at a line end
         * before it hyphenates - is <b>not</b> applied: measured on the two hyphenation
         * probes, whose zones are 18pt and 36pt, Word broke every line of the same prose
         * identically, and hyphenated lines whose gap without the hyphen was 16.71pt to
         * 34.09pt, well inside the 36pt zone.  The 17.0.5 behaviour is kept behind
         * {@code docx4j.convert.out.fo.wordLayout.enforceHyphenationZone}.
         */
        private boolean hyphenationAllowed() {
            if (hyphenLimit > 0 && consecutiveHyphens >= hyphenLimit) {
                return false;
            }
            if (!enforceHyphenationZone) {
                return true;
            }
            return candIdx <= active.position || candDifference > hyphenationZone;
        }

        // ---- Word's tab stops ---------------------------------------------------

        /**
         * A tab is an fo:leader of no length until we get here: only now is the x it
         * starts at known.  Its width is the distance from that x to the tab stop it
         * reaches, so the following text lands on the stop (the stops measured against
         * Word's own PDFs; see {@link WordLineLayoutManager#nextTabStop}).  The leader's
         * Knuth glue is replaced with one of that width, and the leader's layout
         * manager told it too, since its area's IPD comes from its own record.
         */
        @Override
        protected void handleGlueAt(KnuthGlue glue, int position, boolean previousIsBox, int allowedBreaks) {
            LayoutManager tab = tabLeader(glue);
            if (tab == null) {
                super.handleGlueAt(glue, position, previousIsBox, allowedBreaks);
                return;
            }
            // Word does not break a line at a tab whose stop it can reach, but a tab that
            // can reach none breaks it (see noStopReachable)
            boolean unreachable = noStopReachable(position, tab);
            if (unreachable && totalWidth > active.totalWidth) {
                // (with nothing before it on the line there is no break to make: the tab
                // would reach no more from a line of its own)
                int breakAt = leaderFirstIndex(position, tab) - 1;
                // and the tab's break gives way to the ordinary one: where what precedes
                // the tab does not itself fit, the line breaks at the last opportunity
                // that did (the greedy loop's own, at the next legal break past the tab)
                // and the tab is measured again on the line it lands on
                if (breakAt >= active.position && fitsAt(breakAt)) {
                    commitAt(breakAt);          // the tab starts the next line
                    unreachable = noStopReachable(position, tab);   // measured again there
                } else if (!fitsAt(breakAt) && commitLastFitting()) {
                    /* What precedes the tab does not fit either: break at the last break that
                     * did, as the greedy loop does for any element, and the rescan meets this
                     * tab again on the line it lands on.  Until 17.3.1 this branch gave up the
                     * break altogether, and the text ran past the cell: measured on corpus
                     * document 12301, "Configuración de Avance del Proyecto<tab>" in a 162.7pt
                     * cell measure was drawn to 7.5pt past the cell edge where Word breaks
                     * before "Proyecto"; 128 such cells cost 3 pages and 300 lines (ledger8
                     * item 5). */
                    return;
                } else {
                    unreachable = false;
                }
            }
            int width = tabWidth(position, tab);
            WordLeaders.setLeafIPD(tab, width);
            par.set(position, new KnuthGlue(width, 0, 0, glue.getPosition(), false));
            totalWidth += width;
            if (unreachable && hasFollowingContent(position)) {
                // no stop from the start of the line either: the tab has that line to
                // itself and what follows begins the next one.  (A trailing tab's line is
                // the paragraph's last: closing it here gave the paragraph an empty line
                // after it.  @since 17.2.0)
                commitAt(leaderLastIndex(position, tab));
            }
        }

        /**
         * Whether the tab at this element index can reach no tab stop on what is left of
         * the line, which is where Word breaks it.
         *
         * <p>Measured on the {@code tab-clamp-right} probe (A4, 1in margins, so a text
         * column ending at 523.35pt): a left stop at 9355 twips, 539.75pt from the page's
         * left edge, is past the column, and Word puts the text before the tab on one line
         * and the text after it on the next, at the left indent, with the tab re-measured
         * from there - and, where it reaches nothing from there either, on a line of its
         * own (the empty line the golden shows between them, 13.4pt of the tab run's own
         * font).  On the {@code tab-leader-trailing} probe the second of two trailing tabs
         * cannot reach a stop, so "12" goes to the next line and lands on the first stop
         * (360 twips = 90.05pt) the re-measured tab reaches there.  Beyond the paragraph's
         * right <em>indent</em> but inside the column the stop is still honoured and the
         * line runs into the indent (see {@link #tabOverhang}); the edge of the reference
         * area - the text column, or the table cell - is where Word gives up.
         *
         * <p>Only a left stop: a centre, right or decimal stop past the column is clamped
         * instead, so that the text it aligns ends on the right indent (§4.4), which the
         * same golden shows on its centre and right lines.
         *
         * @since 17.1.0
         */
        private boolean noStopReachable(int position, LayoutManager leader) {
            if (isPtabRight(leader)) {
                return false;
            }
            int x = totalWidth - active.totalWidth;      // from the line's start
            int stop = nextTabStop(tabLeftMpt + x);      // sets stopAlignment/stopLeader
            if (stopAlignment != TAB_LEFT) {
                return false;
            }
            // nor a stop which draws a leader: that runs to the end of the line instead.
            // Measured on a corpus prospectus whose table-of-contents entries sit in cells
            // 443pt wide against a 12000-twip left stop with dots, 150pt past the cell:
            // Word draws each entry's dots to the cell's edge on the entry's own line
            // (x=651.0 on every one of them), where breaking there put the dots on a line
            // of their own and cost a page of 23.
            if (stopLeader != WordLeaders.LEADER_NONE) {
                return false;
            }
            // A tab with nothing after it: in a header, Word gives it no line - measured
            // on a corpus header whose paragraph is an image and seven tabs, the last two
            // of which reach nothing (the grid past the last custom stop is 14.5pt past
            // the header's width), Word's header is shorter than one line of it;
            // breaking there made the header 38pt taller and pushed the document onto a
            // second page.  In the flow it takes a line of its own: measured on the
            // tab-trailing-cell probe, "Avance esperado del proyecto<tab>" (145pt of
            // 12pt serif) in cells 149 and 169pt wide, whose next default stop is past
            // the cell, is two lines tall in Word and one without the tab; in a 189pt
            // cell, where the stop fits, one line.  A 311-page corpus report has that
            // row 128 times (word-layout-rules.md §4.4).  @since 17.2.0
            if (!hasFollowingContent(position) && inStaticContent) {
                return false;
            }
            // the reference area's end edge, from the line's start: the available width
            // plus the paragraph's right indent
            return (stop - tabLeftMpt) > getLineWidth() + endIndentMpt();
        }

        /** Whether anything is left of the paragraph after this tab for a break at it to
         *  move to the next line. */
        private boolean hasFollowingContent(int position) {
            for (int i = position + 1; i < par.size(); i++) {
                KnuthElement e = getElement(i);
                if (e.isPenalty() && e.isForcedBreak()) {
                    break;
                }
                if (e.isBox() && !e.isAuxiliary() && e.getWidth() > 0) {
                    return true;
                }
            }
            return false;
        }

        /** Whether the line would still fit if it ended at this element.  The allowance an
         *  earlier tab's stop past the available width bought the line (tabOverhang) is not
         *  room for more text, so it does not count here. */
        private boolean fitsAt(int idx) {
            int difference = computeDifference(active, getElement(idx), idx) - tabOverhang;
            return difference >= 0 || fitsByShrinkingSpaces(idx, difference);
        }

        /** Commit the line ending at this element, as the greedy loop's own breaks do. */
        private void commitAt(int idx) {
            int difference = computeDifference(active, getElement(idx), idx);
            commit(idx, difference, computeAdjustmentRatio(active, difference),
                    totalShrink - active.totalShrink, totalStretch - active.totalStretch,
                    totalWidth, totalStretch, totalShrink, false);
        }

        /** The first element of the four an fo:leader contributes (an auxiliary box, an
         *  infinite penalty, the glue, another auxiliary box): breaking before the glue
         *  alone would leave the leader's area to be added to both lines. */
        private int leaderFirstIndex(int position, LayoutManager leader) {
            int i = position;
            while (i > 0 && elementLM(getElement(i - 1)) == leader) {
                i--;
            }
            return i;
        }

        /** The last of the elements an fo:leader contributes; see {@link #leaderFirstIndex}. */
        private int leaderLastIndex(int position, LayoutManager leader) {
            int i = position;
            while (i + 1 < par.size() && elementLM(getElement(i + 1)) == leader) {
                i++;
            }
            return i;
        }

        /**
         * The width of the tab at this element index, in millipoints.  A left stop puts
         * the following text on the stop; a right stop its end there, a centre stop its
         * middle, a decimal stop its decimal separator (the text up to the next tab or
         * the end of the paragraph).  Word cannot move backwards, so a tab whose text
         * does not fit before the stop collapses to nothing; a stop beyond the
         * paragraph's right indent is still honoured and the line runs into the indent.
         */
        private int tabWidth(int position, LayoutManager leader) {
            int x = totalWidth - active.totalWidth;      // from the line's start
            int stop;
            int align;
            if (isPtabRight(leader)) {
                // a right w:ptab: a right tab stop at the end of the line, whatever the
                // paragraph's own stops are
                stop = tabLeftMpt + getLineWidth();
                align = TAB_RIGHT;
                stopLeader = WordLeaders.LEADER_NONE;
            } else if (isPtabCenter(leader)) {
                // a centre w:ptab: a centre tab stop at the middle of the line, whatever
                // the paragraph's own stops are.  Measured on a corpus document's running
                // head, the three-field "text <ptab center> text <ptab right> text" shape
                // Word's header gallery writes: Word centres the middle field on x=297.75,
                // the exact centre of the page.  @since 17.2.0
                stop = tabLeftMpt + getLineWidth() / 2;
                align = TAB_CENTER;
                stopLeader = WordLeaders.LEADER_NONE;
            } else {
                stop = nextTabStop(tabLeftMpt + x);      // sets stopAlignment/stopLeader
                align = stopAlignment;
            }
            int width = stop - (tabLeftMpt + x);
            boolean clamped = false;
            if (align != TAB_LEFT) {
                width -= followingWidth(position, align);
                // Word clamps a centre, right or decimal stop so that the text it aligns
                // ends on the right indent: unlike a left stop (below), such a stop does
                // not take the line past the indent.  Measured on a centred footer whose
                // centre stop lies 11.3pt past the point at which its text fills the
                // content width: Word draws one line filling it, where the unclamped tab
                // overflowed and wrapped.
                int cap = getLineWidth() - x - followingWidth(position, TAB_LEFT);
                if (cap < 0) {
                    cap = 0;
                }
                if (width > cap) {
                    width = cap;
                    clamped = true;
                }
            }
            if (width < 0) {
                // Word cannot move backwards: a stop the text has already passed, or one
                // whose right/centre text does not fit before it, advances nothing
                width = 0;
            } else if (!clamped) {
                // a stop past the paragraph's right indent is still honoured; the line is
                // allowed to run on to it (see tabOverhang)
                tabOverhang = Math.max(tabOverhang, (stop - tabLeftMpt) - getLineWidth());
            }
            if (WordLineLayoutManager.log.isDebugEnabled()) {
                WordLineLayoutManager.log.debug("tab at x=" + x + " (" + (tabLeftMpt + x)
                        + " from the margin) -> stop " + stop + " align=" + align + " width=" + width);
            }
            // the FO could not know which stop the tab would reach, so the leader of the
            // stop it did reach is set on the area now (blanked where that stop has none,
            // and written as a space character rather than a jump - see tabSpaces)
            boolean spaces = settings.tabSpaces();
            leaders.setLeaderPattern(leader, stopLeader, width, spaces);
            // Word's leader characters sit on a fixed grid, whatever the character, so a
            // leader opens with a gap of less than one cell (see dotLeaderPhase).  That
            // gap is what a PDF text extractor reads as the space between the text and the
            // run, on Word's side and now on ours; Word writes no glyph for it.
            WordLeaders.setLeaderPhase(leader, WordLeaders.isCharacterLeader(stopLeader)
                    ? dotLeaderPhase(tabLeftMpt + x, leader, width) : 0, width, spaces);
            // a right/centre/decimal stop measured an unresolved page number as FOP's
            // "MMM" placeholder; the width it loses when it resolves is the tab's, not
            // the line's (see pageNumberTabs)
            if (align != TAB_LEFT && followingHasUnresolvedPageNumber(position)) {
                pageNumberTabs.add(leader);
            }
            return width;
        }

        /**
         * Whether the text this tab aligns holds a page number FOP has not resolved yet:
         * {@code AbstractPageNumberCitationLayoutManager} measures one as the placeholder
         * "MMM", so {@link #followingWidth} subtracted a width the line will not have.
         *
         * @since 17.1.0
         */
        private boolean followingHasUnresolvedPageNumber(int position) {
            for (int i = position + 1; i < par.size(); i++) {
                KnuthElement e = getElement(i);
                if (e.isPenalty() && e.isForcedBreak()) {
                    break;
                }
                if (e.isGlue() && tabLeader((KnuthGlue) e) != null) {
                    break;
                }
                if (isPageNumberCitation(e)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * The width of the text between this tab and the next one (or the end of the
         * paragraph), which is what a right, centre or decimal stop aligns.  A centre
         * stop takes half of it; a decimal stop the part before the decimal separator
         * (and, where there is none, all of it, as Word right-aligns it then).
         */
        private int followingWidth(int position, int align) {
            int width = 0;
            int trailingSpace = 0;   // Word lets the trailing space hang past the stop
            for (int i = position + 1; i < par.size(); i++) {
                KnuthElement e = getElement(i);
                if (e.isPenalty() && e.isForcedBreak()) {
                    break;
                }
                if (e.isGlue() && tabLeader((KnuthGlue) e) != null) {
                    break;
                }
                if (e.isPenalty()) {
                    continue;
                }
                if (align == TAB_DECIMAL) {
                    int upTo = widthUpToDecimal(e);
                    if (upTo >= 0) {
                        return width + upTo;
                    }
                }
                width += e.getWidth();
                if (e.isGlue()) {
                    trailingSpace += e.getWidth();
                } else if (e.getWidth() > 0) {
                    trailingSpace = 0;
                }
            }
            width -= trailingSpace;
            if (width < 0) {
                width = 0;
            }
            if (align == TAB_CENTER) {
                return width / 2;
            }
            return width;
        }

        /** The width of the part of this box before the decimal separator, or -1 when
         *  it holds none. */
        private int widthUpToDecimal(KnuthElement e) {
            if (!(e instanceof KnuthInlineBox)) {
                return -1;
            }
            Position leaf = e.getPosition();
            while (leaf != null && !(leaf instanceof LeafPosition)) {
                leaf = leaf.getPosition();
            }
            if (leaf == null || !(leaf.getLM() instanceof org.apache.fop.layoutmgr.inline.TextLayoutManager)) {
                return -1;
            }
            org.apache.fop.layoutmgr.inline.TextLayoutManager tlm
                    = (org.apache.fop.layoutmgr.inline.TextLayoutManager) leaf.getLM();
            List<org.apache.fop.fonts.GlyphMapping> mappings = tlm.getMappings();
            int idx = ((LeafPosition) leaf).getLeafPos();
            if (idx < 0 || idx >= mappings.size()) {
                return -1;
            }
            org.apache.fop.fonts.GlyphMapping m = mappings.get(idx);
            if (m.font == null) {
                return -1;
            }
            org.apache.fop.fo.FOText foText = tlm.getFOText();
            for (int c = m.startIndex; c < m.endIndex && c < foText.length(); c++) {
                if (foText.charAt(c) != decimalSeparator) {
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                for (int k = m.startIndex; k < c; k++) {
                    sb.append(foText.charAt(k));
                }
                return m.font.getWordWidth(sb.toString());
            }
            return -1;
        }

        /** The line may run on to a tab stop that lies past the available width. */
        @Override
        protected int computeDifference(KnuthNode activeNode, KnuthElement element, int elementIndex) {
            return super.computeDifference(activeNode, element, elementIndex) + tabOverhang;
        }

        /**
         * The slack a line holding a tab is aligned by: its stored difference less the
         * tab overhang computeDifference added, and never negative.  A line whose tab
         * reaches past the available width fills it, so w:jc cannot move it any further
         * (Word cannot move backwards; see tabWidth).
         */
        private int alignDifference(KnuthNode node, int difference) {
            Integer overhang = nodeTabOverhang.get(node);
            int d = overhang == null ? difference : difference - overhang.intValue();
            return d > 0 ? d : 0;
        }

        private void commit(int idx, int difference, double r, int availableShrink, int availableStretch,
                int tw, int ts, int tsh, boolean restart) {
            int fitness = r < -0.5 ? 0 : (r <= 0.5 ? 1 : (r <= 1 ? 2 : 3));
            KnuthNode node = createNode(idx, active.line + 1, fitness, tw, ts, tsh,
                    r, availableShrink, availableStretch, difference, 0, active);
            removeNode(active.line, active);
            active = node;
            consecutiveHyphens = isHyphenationPoint(getElement(idx)) ? consecutiveHyphens + 1 : 0;
            candIdx = -1;
            hyphIdx = -1;
            if (tabOverhang != 0) {
                nodeTabOverhang.put(node, Integer.valueOf(tabOverhang));
            }
            tabOverhang = 0;
            if (restart) {
                restartRequested = true; // restartFrom() re-adds the node and resets the totals
            } else {
                addNode(node.line, node);
            }
        }

        /**
         * A line as Word makes it: {box height, extra leading below, baseline}, in
         * millipoints.  Word sizes a line from the runs on it: the ascent is the
         * largest run ascent (usWinAscent + external leading, at the run's size) and
         * the descent the largest run descent, whatever the run's vertical
         * alignment (a raised superscript still counts its full height, the shift
         * itself not).  The block carries those for its dominant run
         * (docx4j:line-box, docx4j:baseline); every other run's span carries Word's
         * pitch as its line-height, which FOP hands over as the alignment context's
         * height, split here in the block font's ascent:descent ratio (exact for the
         * same font at another size, within a few percent across fonts).  A box
         * whose bottom sits on the baseline (a picture) keeps FOP's altitude.
         * Then the spacing rule: "auto" multiplies the natural line by the block's
         * factor and puts the extra below as leading; "atLeast" puts any shortfall
         * above; "exact" clips to the given height.
         */
        private int[] wordLine(KnuthSequence par, int firstElementIndex, int lastElementIndex) {
            int blockAscent = wordBaseline > 0 ? wordBaseline : lead;
            int blockDescent = wordLineBox - blockAscent;
            double ratio = (double) blockAscent / wordLineBox;
            // from the runs on the line only: a line holding just a picture has no
            // descent in Word (its bottom is the picture's), text runs bring theirs
            int ascent = 0;
            int descent = 0;
            if (wordLineRule != RULE_EXACT && fobj.getLineStackingStrategy() != EN_FONT_HEIGHT) {
                ListIterator inlineIterator = par.listIterator(firstElementIndex);
                AlignmentContext lastAC = null;
                for (int j = firstElementIndex; j <= lastElementIndex; j++) {
                    KnuthElement element = (KnuthElement) inlineIterator.next();
                    if (!(element instanceof KnuthInlineBox)) {
                        continue;
                    }
                    // an fo:leader's own boxes carry the rule thickness, not a font's
                    // height: a line Word broke at a tab and which holds that tab alone
                    // takes its height from the block's font, not from a hairline
                    // (@since 17.1.0)
                    if (elementLM(element) instanceof org.apache.fop.layoutmgr.inline.LeaderLayoutManager) {
                        continue;
                    }
                    AlignmentContext ac = ((KnuthInlineBox) element).getAlignmentContext();
                    if (ac == null || lastAC == ac) {
                        continue;
                    }
                    lastAC = ac;
                    if (ac.usesInitialBaselineTable()
                            && (ac.getAlignmentBaselineIdentifier() == EN_BEFORE_EDGE
                                || ac.getAlignmentBaselineIdentifier() == EN_AFTER_EDGE)) {
                        continue;
                    }
                    int h = ac.getHeight();
                    if (h <= 0) {
                        continue;
                    }
                    /* A small-caps run's glyphs are drawn at 80% of the run's size, but
                     * Word's line is the declared size's: measured on a table cell whose
                     * only content is such a span, Word puts it on the same baseline as
                     * the two cells beside it (123.2) where FOP took the ascent from the
                     * 8pt inline and put it 3pt high, splitting one row into three.  The
                     * span says what it was scaled by, so the line reads its height at
                     * the size the run declares.  @since 17.1.0 */
                    String smallCaps = foreignAttribute(element.getLayoutManager().getFObj(),
                            WordLayoutElementMapping.SMALL_CAPS);
                    if (smallCaps != null) {
                        try {
                            double scale = Double.parseDouble(smallCaps);
                            if (scale > 0 && scale < 1) {
                                h = (int) Math.round(h / scale);
                            }
                        } catch (NumberFormatException e) { /* leave h alone */ }
                    }
                    if (ac.getDepth() == 0) {
                        // bottom on the baseline (a picture): FOP's altitude is the height
                        ascent = Math.max(ascent, ac.getAltitude());
                        continue;
                    }
                    // Word's pitch for the run: its fo:inline's own line-height (docx4j puts
                    // Word's single-spacing pitch on every run's span); an inherited value
                    // is the block's line-height, in which case FOP's content height for
                    // the run is scaled by the block font's pitch : content ratio
                    int pitch = ac.getLineHeight();
                    /* A span naming its document font (docx4j:font) carries Word's pitch for
                     * it explicitly, so a pitch equal to the block's is the block's and not
                     * an inherited value: the run's natural pitch is the block's box, and
                     * FOP's content height does not come into it.  It differs by face where
                     * the font's typo metrics do: Liberation Serif Bold's sTypoAscender is
                     * 1387 to the Regular's 1420, so a line of bold runs only was 1829/1862
                     * of the block's box.  Measured on the line-box-bold-run probe (CR-001
                     * batch 53; corpus documents 4025, 13321): Liberation Serif 12pt lines
                     * all bold pitch 13.80 in Word as regular ones do (15.84 at 1.15), where
                     * docx4j's were 13.55 (15.59).  Auto spacing only: atLeast keeps FOP's.
                     * @since 17.3.1 */
                    if (pitch == lineHeight && wordLineRule == RULE_AUTO && wordLineBox > 0
                            && innermostForeignAttribute(element, "font") != null) {
                        pitch = wordLineBox;
                    } else if (pitch <= 0 || pitch == lineHeight) {
                        pitch = (int) Math.round(h * (double) wordLineBox / (lead + follow));
                    } else if (wordLineRule == RULE_AUTO && wordLineBox > 0 && lineHeight > wordLineBox) {
                        // the span's pitch carries the paragraph's auto multiple, applied
                        // once below to the whole line: take the run's natural pitch
                        pitch = (int) Math.round(pitch * (double) wordLineBox / lineHeight);
                    }
                    // the run font's own ascent share when it can be read, else the block's
                    double r = ratio;
                    org.apache.fop.fonts.Font f = WordLeaders.inlineFont(element.getLayoutManager());
                    if (f != null && f.getFontMetrics() != null) {
                        // the span's docx4j:font names the document font a substitute renders.  Read off
                        // the innermost inline: the element's own manager is the outermost inline's once
                        // positions are wrapped, and the run's span is usually inside another, so until
                        // 17.3.1 this found nothing and took the substitute's own ascent share.
                        String docFont = innermostForeignAttribute(element, "font");
                        /* An element naming no document font in the block's own font is the
                         * block's text (or a bookmark's empty inline), and the block's declared
                         * baseline is already Word's split for it.  The substitute's own share
                         * is not: Nimbus Sans Narrow's is 0.7917 where Arial Narrow's is 0.8166,
                         * so beside a run naming Arial Narrow the line took one's ascent and the
                         * other's descent, 0.51pt over Word's box at 18pt (corpus document 9623,
                         * its headings and running head).  Not on a picture-only line, whose
                         * baseline is its box.  @since 17.3.1 */
                        boolean blockText = docFont == null && wordBaseline > 0 && wordBaseline < wordLineBox
                                && f.getFontMetrics().getFullName().equals(blockFontName());
                        // docx4j's answer (its WordLineMetrics for the document font, else the physical one's):
                        // WordLayoutSettings.ascentShare, through FopFactoryBuilder.setWordLayoutFontMetrics
                        Double share = blockText ? null
                                : settings.ascentShare(docFont, f.getFontMetrics().getFullName());
                        if (share != null) {
                            r = share;
                        }
                    }
                    int alt = (int) Math.round(pitch * r);
                    if (WordLineLayoutManager.log.isDebugEnabled()) {
                        WordLineLayoutManager.log.debug("wordLine inline: fop height=" + h + " pitch=" + pitch
                                + " ascent=" + alt
                                + " shift=" + ac.getBaselineShiftValue() + " font=" + (f == null ? "-"
                                : f.getFontMetrics().getFullName() + "/" + f.getFontSize()));
                    }
                    ascent = Math.max(ascent, alt);
                    descent = Math.max(descent, pitch - alt);
                }
            }
            if (ascent + descent == 0) {
                // nothing measurable on the line (or exact spacing): the block's font
                ascent = blockAscent;
                descent = blockDescent;
            } else if (wordLineBox > 0 && wordBaseline == wordLineBox) {
                // WordLayoutFixups.imageOnlyLineBox marks a paragraph whose only content
                // is an inline picture by putting the baseline at the foot of the box:
                // Word's line is then the picture's height with no descent at all
                // (docx4j:line-box == docx4j:baseline is written nowhere else).  The
                // run the picture sits in still contributes its font's descent here,
                // which put every line of one document a flat 2.6pt below Word's.
                // @since 17.1.0
                descent = 0;
            }
            if (lineBaselineFromBlock) {
                int[] fromBlock = declaredBaselineLine(wordLineBox, wordBaseline, ascent, descent);
                ascent = fromBlock[0];
                descent = fromBlock[1];
            }
            /* The list label shares the item's first line: Word adds what its ascent
             * exceeds the text's by, and does not multiply that by the auto factor
             * (a Symbol bullet on Calibri 11pt at 1.15: 16.04 = 15.44 + 0.59, not 16.11).
             *
             * The text's ascent here is the paragraph's own (docx4j:baseline, which the
             * exporter measured from the document font and measured the label against,
             * WordLayoutFixups.listLabelLines), never a lower one the line's runs report:
             * a run rendered by a substitute whose document font the span does not name
             * gets the substitute's share of the pitch, which is smaller than Word's for
             * the font it stands in for (Arimo 0.728, Arial 0.815), and a label in the
             * very font of the text then looked 1pt taller than its line.  Only where the
             * line is the paragraph's own size: a line of smaller runs is genuinely
             * shorter than the paragraph's ascent, and the label does raise it.
             * @since 17.2.0 */
            int labelExtra = 0;
            boolean fullSizeLine = wordLineBox > 0 && Math.abs(ascent + descent - wordLineBox) <= 1;
            int labelAgainst = labelAscentAgainstBaseline && fullSizeLine && wordBaseline > 0
                    ? Math.max(ascent, wordBaseline) : ascent;
            if (labelAscent > labelAgainst && wordLineRule != RULE_EXACT && knuthParagraphs.indexOf(par) == 0
                    && firstElementIndex <= ((Paragraph) par).ignoreAtStart) {
                labelExtra = labelAscent - labelAgainst;
            }
            if (WordLineLayoutManager.log.isDebugEnabled()) {
                WordLineLayoutManager.log.debug("wordLine: box=" + wordLineBox + " baseline=" + wordBaseline
                        + " lineHeight=" + lineHeight
                        + " rule=" + wordLineRule + " -> ascent=" + ascent + " descent=" + descent);
            }
            int box;
            int leading = 0;
            int baseline;
            switch (wordLineRule) {
                case RULE_EXACT:
                    box = wordLineBox;
                    baseline = wordBaseline > 0 ? wordBaseline : lead;
                    break;
                case RULE_AT_LEAST:
                    ascent += labelExtra;
                    box = Math.max(ascent + descent, lineHeight);
                    baseline = ascent + Math.max(0, lineHeight - (ascent + descent));
                    break;
                default:
                    box = ascent + descent;
                    baseline = ascent;
                    if (lineHeight > wordLineBox) {
                        double factor = (double) lineHeight / wordLineBox;
                        leading = (int) Math.round(box * factor) - box;
                    }
                    box += labelExtra;
                    baseline += labelExtra;
            }
            return new int[] { box, leading, baseline };
        }

    }


    /**
     * Create a new Word-mode Line Layout Manager.
     * This is used by the block layout manager to create
     * line managers for handling inline areas flowing into line areas, in Word mode.
     * @param block the block formatting object
     * @param lh the default line height
     * @param l the default lead, from top to baseline
     * @param f the default follow, from baseline to bottom
     */
    public WordLineLayoutManager(Block block, Length lh, int l, int f) {
        super(block, lh, l, f);
        inCell = inTableCell(block);
        inStaticContent = inStaticContent(block);
        wordLineBox = foreignLength(block, WordLayoutElementMapping.LINE_BOX);
        wordBaseline = foreignLength(block, WordLayoutElementMapping.BASELINE);
        String rule = foreignAttribute(block, WordLayoutElementMapping.LINE_RULE);
        wordLineRule = "exact".equals(rule) ? RULE_EXACT : "atLeast".equals(rule) ? RULE_AT_LEAST : RULE_AUTO;
        labelAscent = foreignLength(block, WordLayoutElementMapping.LABEL_ASCENT);
        maxSpaceShrink = documentSpaceShrink(block);
        maxHyphenSpaceShrink = documentHyphenSpaceShrink(maxSpaceShrink);

        // the document's hyphenation settings (docx4j:hyphenation-zone, -hyphen-limit,
        // -hyphenate-caps on fo:root; WordLayoutFixups.lineBoxAttributes)
        int zone = twipsToMpt(rootAttribute(block, WordLayoutElementMapping.HYPHENATION_ZONE));
        hyphenationZone = zone > 0 ? zone
                : DEFAULT_HYPHENATION_ZONE_TWIPS * 50;
        hyphenLimit = intAttribute(rootAttribute(block, WordLayoutElementMapping.HYPHEN_LIMIT));
        hyphenateCaps = !"false".equals(rootAttribute(block, WordLayoutElementMapping.HYPHENATE_CAPS));

        // the tab stops the block's tabs are laid out against
        String stops = foreignAttribute(block, WordLayoutElementMapping.TABS);
        hasTabStops = stops != null;
        String[] parts = (stops == null || stops.length() == 0) ? new String[0] : stops.split(";");
        tabStops = new int[parts.length];
        tabStopAlign = new int[parts.length];
        tabStopLeader = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String[] stopFields = parts[i].split(":", -1);
            tabStops[i] = twipsToMpt(stopFields[0]);
            tabStopAlign[i] = tabAlignment(stopFields.length > 1 ? stopFields[1] : "left");
            tabStopLeader[i] = tabLeaderKind(stopFields.length > 2 ? stopFields[2] : "none");
        }
        int def = twipsToMpt(foreignAttribute(block, WordLayoutElementMapping.TAB_DEFAULT));
        tabDefaultMpt = def > 0 ? def : 720 * 50;
        noTabHangInd = "true".equals(rootAttribute(block, WordLayoutElementMapping.NO_TAB_HANG_IND));
        justifySoftReturn = foreignAttribute(block, WordLayoutElementMapping.JUSTIFY_SOFT_RETURN) != null;
        String ind = foreignAttribute(block, WordLayoutElementMapping.TAB_IND);
        String[] indParts = ind == null ? new String[0] : ind.split(":", -1);
        tabLeftMpt = indParts.length > 0 ? twipsToMpt(indParts[0]) : 0;
        tabHanging = indParts.length > 1 && twipsToMpt(indParts[1]) < 0;
        decimalSeparator = (indParts.length > 2 && indParts[2].length() > 0) ? indParts[2].charAt(0) : '.';
    }

    private static int twipsToMpt(String twips) {
        return intAttribute(twips) * 50;
    }

    private static int intAttribute(String v) {
        if (v == null || v.length() == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** A docx4j foreign attribute on fo:root: a document-level setting.  @since 17.1.0 */
    private static String rootAttribute(org.apache.fop.fo.FObj block, String name) {
        org.apache.fop.fo.FONode node = block;
        while (node != null && node.getParent() != null) {
            node = node.getParent();
        }
        if (!(node instanceof org.apache.fop.fo.FObj)) {
            return null;
        }
        return foreignAttribute((org.apache.fop.fo.FObj) node, name);
    }

    // ---- Word's hyphenation (docx4j:hyphenation-zone and friends) ---------------

    /** w:hyphenationZone in millipoints: the largest gap Word is said to tolerate at the
     *  end of a line before it hyphenates the next word.  Measured against Word 365 it
     *  never fires, so it is applied only when
     *  {@code docx4j.convert.out.fo.wordLayout.enforceHyphenationZone} is set.  @since 17.1.0 */
    private final int hyphenationZone;

    /** Whether {@link #hyphenationZone} is applied at all (default false).  @since 17.1.0 */
    private final boolean enforceHyphenationZone = settings.enforceHyphenationZone();
    /** w:consecutiveHyphenLimit: how many lines in a row may end in a hyphen; 0 = no
     *  limit.  @since 17.1.0 */
    private final int hyphenLimit;
    /** w:doNotHyphenateCaps inverted: false means a word in all capitals is left
     *  whole.  @since 17.1.0 */
    private final boolean hyphenateCaps;

    /** Whether this word may be hyphenated at all: with w:doNotHyphenateCaps, a word
     *  written entirely in capitals is not.  Measured against Word's definition: at
     *  least one letter, and no lower-case one (digits and punctuation, as in
     *  "ISO-9001", do not make a word mixed case).  @since 17.1.0 */
    @Override
    boolean hyphenatable(CharSequence word) {
        if (hyphenateCaps) {
            return true;
        }
        boolean anyLetter = false;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (Character.isLowerCase(c)) {
                return true;
            }
            if (Character.isLetter(c)) {
                anyLetter = true;
            }
        }
        return !anyLetter;
    }


    /**
     * How far this line's spaces may be compressed to pull in a word that does not
     * fit at natural width: the configured limit
     * ({@code maxSpaceShrink}), capped by the document's own
     * docx4j:space-shrink on fo:root, which docx4j sets to 0 for a document whose
     * compatibility mode is below 15 (Word only compresses from its 2013 engine on).
     */
    private final double maxSpaceShrink;

    /**
     * How far this line's spaces may be compressed to take a longer hyphenation
     * fragment - a much tighter limit than {@link #maxSpaceShrink}, which is what Word
     * will pay to pull a whole word onto the line
     * ({@code maxHyphenSpaceShrink}, default 0.10).  Capped by
     * the document's own docx4j:space-shrink, like maxSpaceShrink.
     *
     * @since 17.1.0
     */
    private final double maxHyphenSpaceShrink;

    /**
     * How loose the alternative line has to be, at least, before Word compresses this one
     * to pull one more word on ({@code minStretchToCompress}, a
     * floor; default 0).
     *
     * @since 17.1.0
     */
    private final double minStretchToCompress = settings.minStretchToCompress();

    /**
     * Word compresses a line to pull one more word on only where the compression is less
     * than this fraction of the alternative line's stretch
     * ({@code compressionToStretch}, default 0.5).
     *
     * @since 17.3.1
     */
    private final double compressionToStretch = settings.compressionToStretch();

    private double documentHyphenSpaceShrink(double spaceShrink) {
        return Math.min(settings.maxHyphenSpaceShrink(), spaceShrink);
    }

    private double documentSpaceShrink(org.apache.fop.fo.FObj block) {
        Double explicit = settings.configuredMaxSpaceShrink();
        if (explicit != null) {
            return explicit.doubleValue();
        }
        double configured = WordLayoutSettings.DEFAULT_MAX_SPACE_SHRINK;
        String v = rootAttribute(block, WordLayoutElementMapping.SPACE_SHRINK);
        if (v == null) {
            return configured;
        }
        try {
            return Math.min(configured, Double.parseDouble(v));
        } catch (NumberFormatException e) {
            return configured;
        }
    }

    /** a list label's natural ascent (millipoints), on the item's first line; Word
     *  counts the label's ascent, not its descent (WordLayoutFixups.listLabelLines) */
    private final int labelAscent;

    /** whether the label's excess ascent is measured against the paragraph's own ascent
     *  (docx4j:baseline) rather than the line's runtime ascent;
     *  {@code docx4j.convert.out.fo.wordLayout.labelAscentAgainstBaseline}.  @since 17.2.0 */
    private final boolean labelAscentAgainstBaseline
            = settings.labelAscentAgainstBaseline();

    /**
     * Where a line comes out the box {@code docx4j:line-box} declares, it takes the
     * baseline {@code docx4j:baseline} declares with it.
     *
     * <p>The two are a pair the FO exporter measured together from the document font.  A
     * line's ascent here is a run-level measurement - {@code round(pitch x share)}, where
     * the share comes from the <em>run's</em> own metrics - and its descent the rest of
     * the pitch.  For a face whose line box was grown, an East Asian one taking 1.3 times
     * its usWin box (CR-001 batch 43), that share is read off a winAscent which was not
     * grown with it, so the line keeps the declared height and loses the declared
     * baseline.
     *
     * <p>Measured on a generated reproduction of a corpus document's shape - a bulleted
     * item whose Symbol label this machine substitutes with DejaVu Serif and whose 9pt
     * body run names Meiryo, which it lacks.  The body block declares
     * {@code docx4j:line-box="17.55pt" docx4j:baseline="9.541pt"} and its line came out
     * {@code ascent=13689 descent=3861} - the box exactly, the baseline 4.148pt low -
     * where the label block beside it, whose run is measurable, came out
     * {@code ascent=9540}, its own declared baseline.  So the bullet sat 4.15pt ABOVE its
     * text, which is the offset that corpus document shows over 854 of its labels.
     *
     * <p>Only where the line <em>is</em> the declared box: a line of smaller runs is
     * genuinely shorter than the paragraph and has a baseline of its own, which is the
     * shape {@code labelAscentAgainstBaseline} is about.
     *
     * @param wordLineBox the block's {@code docx4j:line-box}, millipoints; 0 where none
     * @param wordBaseline the block's {@code docx4j:baseline}, millipoints; 0 where none
     * @param ascent the line's ascent as its runs measured it, millipoints
     * @param descent the line's descent as its runs measured it, millipoints
     * @return the ascent and descent the line takes, the arguments unchanged where the
     *         line is not the declared box
     * @since 17.2.0
     */
    static int[] declaredBaselineLine(int wordLineBox, int wordBaseline, int ascent, int descent) {
        if (wordBaseline > 0 && wordLineBox > 0 && wordBaseline < wordLineBox
                && Math.abs(ascent + descent - wordLineBox) <= 1 && ascent != wordBaseline) {
            return new int[] { wordBaseline, wordLineBox - wordBaseline };
        }
        return new int[] { ascent, descent };
    }

    /** whether a line which comes out the box the exporter declared takes the baseline it
     *  declared with it (CR-001 batch 44, M10) */
    private final boolean lineBaselineFromBlock
            = settings.lineBaselineFromBlock();

    // ---- Word's tab stops (docx4j:tabs, docx4j:tab-default, docx4j:tab-ind) ------

    static final int TAB_LEFT = 0;
    static final int TAB_CENTER = 1;
    static final int TAB_RIGHT = 2;
    static final int TAB_DECIMAL = 3;
    static final int TAB_CLEAR = 4;

    /** whether this paragraph carries tab stops, i.e. holds a tab we place */
    private final boolean hasTabStops;
    /** the custom stops, in millipoints from the left margin, with their alignment
     *  (TAB_*) and whether they draw a leader; cleared stops are kept as TAB_CLEAR
     *  because they clear the default stops before them too. */
    private final int[] tabStops;
    private final int[] tabStopAlign;
    /** what each stop's w:leader draws (WordLeaders.LEADER_NONE/-DOTS/-RULE) */
    private final int[] tabStopLeader;
    /** the default interval (w:defaultTabStop) in millipoints */
    private final int tabDefaultMpt;
    /** the paragraph's left indent in millipoints: the line manager's x is measured
     *  from there, the stops from the left margin */
    private final int tabLeftMpt;
    /** a hanging indent (a negative first-line offset) makes an implicit stop at the
     *  left indent */
    private final boolean tabHanging;
    /** w:compat/w:noTabHangInd: a hanging indent makes no implicit tab stop.  @since 17.1.0 */
    private final boolean noTabHangInd;
    /** the paragraph's line that ends at a soft return is justified (&#xa7;4.2;
     *  w:compat/w:doNotExpandShiftReturn is not set).  @since 17.1.0 */
    private final boolean justifySoftReturn;
    /** whether this line sequence is followed by a soft return, i.e. the break's block is
     *  the next block-level child of the paragraph.  That is the XSLT pathway's shape; in
     *  the visitor pathway the break is nested in the run's fo:inline and splits this
     *  manager's own knuthParagraphs instead.  Set by
     *  {@link WordBlockLayoutManager}.  @since 17.1.0 */
    private boolean followedBySoftReturn;
    /** the decimal separator a decimal stop aligns (w:decimalSymbol; "." by default) */
    private final char decimalSeparator;

    /** @see #followedBySoftReturn  @since 17.1.0 */
    public void setFollowedBySoftReturn(boolean b) {
        this.followedBySoftReturn = b;
    }

    /** Whether this block's soft returns end a justified line (&#xa7;4.2).  @since 17.1.0 */
    public boolean isJustifySoftReturn() {
        return justifySoftReturn;
    }

    /** set by {@link #nextTabStop}: the alignment of the stop it found, and what
     *  leader that stop draws (WordLeaders.LEADER_NONE/-DOTS/-RULE) */
    private int stopAlignment;
    private int stopLeader;

    /**
     * The first tab stop after x (millipoints from the left margin), as Word finds
     * it: a custom stop, or the implicit stop a hanging indent makes at the left
     * indent, and only where there is none of those the default interval — a custom
     * stop clears the default stops before it.  Mirrors
     * XsltFOFunctions.nextTabStop, which does the same for a tab that begins a
     * paragraph.
     */
    private int nextTabStop(int x) {
        int best = Integer.MAX_VALUE;
        stopAlignment = TAB_LEFT;
        stopLeader = WordLeaders.LEADER_NONE;
        for (int i = 0; i < tabStops.length; i++) {
            if (tabStopAlign[i] == TAB_CLEAR) {
                continue;
            }
            if (tabStops[i] > x && tabStops[i] < best) {
                best = tabStops[i];
                stopAlignment = tabStopAlign[i];
                stopLeader = tabStopLeader[i];
            }
        }
        if (tabHanging && !noTabHangInd && tabLeftMpt > x && tabLeftMpt < best) {
            best = tabLeftMpt;
            stopAlignment = TAB_LEFT;
            stopLeader = WordLeaders.LEADER_NONE;
        }
        if (best < Integer.MAX_VALUE) {
            return best;
        }
        if (x < 0) {
            return 0;
        }
        return (x / tabDefaultMpt + 1) * tabDefaultMpt;
    }

    /**
     * The blank a dot leader opens with, so that its dots land on Word's grid.
     *
     * <p>Word draws a tab's leader dots on a grid fixed to the reference area rather than
     * from the end of the text: measured on the {@code tab-leader-trailing} and
     * {@code tab-leader-resolved} goldens, whose dots step 3.121pt, every one of the seven
     * leader runs across the two documents begins and ends on the same grid, anchored
     * 71.80pt from the page's left edge - the 72.02pt left margin, to within the 0.05pt the
     * PDF's own rounding allows - and each holds exactly the whole cells of that grid which
     * fall inside the tab.  So a leader opens with a gap of between nothing and one dot
     * ("1. Scope ....." in the golden against our "1. Scope.....") and ends short of the
     * stop by the same kind of remainder.
     *
     * <p>{@code leader-alignment="reference-area"} is the XSL FO property for exactly this
     * and docx4j writes it, but FOP 2.11 reads it in its RTF renderer alone (§10), so the
     * phase is applied here: the leader area becomes a blank of this width followed by the
     * dots, which FOP then starts on the grid.
     *
     * @param from the tab's start, in millipoints from the left margin
     * @return the blank's width in millipoints, 0 where the dots already start on the grid
     * @since 17.1.0
     */
    private int dotLeaderPhase(int from, LayoutManager leader, int width) {
        int period = WordLeaders.leaderUnitWidth(leader);
        if (period <= 0 || width <= period) {
            return 0;
        }
        int phase = settings.leaderGrid()
                ? WordLeaders.gridPhase(from + pageOffsetMpt(), period)
                : (period - (from % period)) % period;
        return phase < width ? phase : 0;
    }

    /**
     * How far the left margin is from the <b>page's own left edge</b>, in millipoints, or
     * 0 where that cannot be had.
     *
     * <p>Word's leader grid is anchored on the page edge, not on the text: measured on the
     * {@code tab-leader-kinds} golden, whose margin is 72.024pt, every run begins on an
     * exact multiple of its pitch from x=0 - the hyphen runs at 151.27 and 258.84 are
     * 45 and 77 pitches of 3.3616, the underscore runs at 165.67 and 176.71 are 30 and 32
     * of 5.5222, the dot runs at 129.65 and 221.86 are 45 and 77 of 2.8816 - and on a
     * corpus document whose margin is 50.4pt the same holds.  {@link #tabLeftMpt} is
     * measured from the margin, so the margin's own offset has to be added before the
     * grid is taken.</p>
     *
     * <p>It is the body region's start edge, which is the margin for ordinary text.  A tab
     * inside a table cell is still short by the cell's own offset; that is nearer than the
     * whole margin, which is what it was before.</p>
     *
     * <p>A caveat to record: it is read from the page viewport <b>current while the line is
     * laid out</b> and cached for the manager's life, so a paragraph laid out while page N is
     * current but placed on page N+1 with a different body start - mirrored margins, or an
     * odd/even section whose margins differ - is gridded against the wrong edge, by the
     * difference between the two margins.  No probe has that shape, and no document in the
     * three corpora moved for it; it is not worth a lookup per line until one does.</p>
     *
     * @since 17.2.0
     */
    private int pageOffsetMpt() {
        if (pageOffsetMpt != Integer.MIN_VALUE) {
            return pageOffsetMpt;
        }
        int off = 0;
        try {
            org.apache.fop.area.PageViewport pv = getPSLM() == null ? null : getPSLM().getCurrentPV();
            org.apache.fop.area.Page page = pv == null ? null : pv.getPage();
            org.apache.fop.area.RegionViewport rv = page == null ? null
                    : page.getRegionViewport(org.apache.fop.fo.Constants.FO_REGION_BODY);
            if (rv != null && rv.getViewArea() != null) {
                off = (int) Math.round(rv.getViewArea().getX());
            }
        } catch (RuntimeException e) {
            log.debug("no page viewport for the leader grid: " + e.getMessage());
        }
        pageOffsetMpt = off;
        return off;
    }

    private int pageOffsetMpt = Integer.MIN_VALUE;

    /**
     * The paragraph's right indent in millipoints: the distance from the end of a line to
     * the end edge of the reference area (the text column, or the table cell).  FOP's
     * end-indent is measured from that edge and the line width is what is left after both
     * indents, so their sum is that distance.  A tab stop inside it is still honoured
     * (the line breaking algorithm's tabOverhang); one past it reaches nothing
     * ({@code noStopReachable}).
     *
     * @since 17.1.0
     */
    private int endIndentMpt() {
        try {
            if (fobj == null || fobj.getCommonMarginBlock() == null
                    || fobj.getCommonMarginBlock().endIndent == null) {
                return 0;
            }
            int v = fobj.getCommonMarginBlock().endIndent.getValue(this);
            return v > 0 ? v : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** The leaf layout manager a Knuth element came from, or null. */
    private static LayoutManager elementLM(KnuthElement e) {
        Position leaf = e.getPosition();
        while (leaf != null && !(leaf instanceof LeafPosition)) {
            leaf = leaf.getPosition();
        }
        return leaf == null ? null : leaf.getLM();
    }

    /** The layout manager of the fo:leader standing in for a w:tab, or null.  The
     *  leader is usually inside the run's fo:inline, whose manager wraps its positions,
     *  so unwrap to the leaf as fixLetterSpaces does. */
    private LayoutManager tabLeader(KnuthGlue glue) {
        if (!hasTabStops) {
            return null;
        }
        Position leaf = glue.getPosition();
        while (leaf != null && !(leaf instanceof LeafPosition)) {
            leaf = leaf.getPosition();
        }
        LayoutManager lm = leaf == null ? null : leaf.getLM();
        if (!(lm instanceof org.apache.fop.layoutmgr.inline.LeaderLayoutManager)) {
            return null;
        }
        String kind = foreignAttribute(lm.getFObj(), WordLayoutElementMapping.TAB);
        return (kind != null && kind.length() > 0) ? lm : null;
    }

    // ---- a right tab whose text ends in an unresolved page number ---------------

    /**
     * The tab leaders whose right, centre or decimal stop measured a page number FOP
     * had not resolved.
     *
     * <p>FOP measures an unresolved {@code fo:page-number-citation} as the placeholder
     * "MMM" (AbstractPageNumberCitationLayoutManager), so the width the tab subtracted
     * for the text it aligns is one the line will not have: once
     * {@code UnresolvedPageNumber.resolveIDRef} puts the real number in, the number
     * ends width("MMM") - width(number) short of the stop - measured at 10.7pt on one
     * document's every TOC line (DejaVu Sans 8pt: "MMM" 20.71pt, "61" 10.18pt) and at
     * 25pt on another's.  Word puts the number on the stop.
     *
     * <p>The width the number loses belongs to the tab, not to the line's right end, so
     * {@link #addInlineArea} pairs each of these tabs with the unresolved number that
     * follows it and a {@link TabPageNumberWidth} moves the width across when the
     * number resolves.
     *
     * @since 17.1.0
     */
    private final java.util.Set<LayoutManager> pageNumberTabs
            = Collections.newSetFromMap(new java.util.IdentityHashMap<LayoutManager, Boolean>());

    /** whether this Knuth element is an fo:page-number-citation, which FOP may not
     *  have resolved when the line is laid out. */
    private static boolean isPageNumberCitation(KnuthElement e) {
        Position leaf = e.getPosition();
        while (leaf != null && !(leaf instanceof LeafPosition)) {
            leaf = leaf.getPosition();
        }
        return leaf != null
                && leaf.getLM() instanceof AbstractPageNumberCitationLayoutManager;
    }

    /**
     * Hand the width an unresolved page number gives up to the tab that aligns it, so
     * that the number still ends on its stop; see {@link #pageNumberTabs}.  The area's
     * own notification puts the line back the width the number's did take off it.
     *
     * @since 17.1.0
     */
    private static final class TabPageNumberWidth implements org.apache.fop.area.Resolvable {

        private final InlineArea tab;
        private final org.apache.fop.area.inline.UnresolvedPageNumber number;
        private final int placeholderWidth;
        private boolean resolved;

        TabPageNumberWidth(InlineArea tab, org.apache.fop.area.inline.UnresolvedPageNumber number) {
            this.tab = tab;
            this.number = number;
            this.placeholderWidth = number.getIPD();
        }

        public String[] getIDRefs() {
            return number.getIDRefs();
        }

        public boolean isResolved() {
            return resolved;
        }

        public void resolveIDRef(String id, List<org.apache.fop.area.PageViewport> pages) {
            if (resolved) {
                return;
            }
            resolved = true;
            int delta = placeholderWidth - number.getIPD();
            if (delta != 0) {
                tab.handleIPDVariation(delta);
            }
        }
    }

    /**
     * Pair each of this line's {@link #pageNumberTabs} with the unresolved page number
     * it aligns, once the line's areas exist.
     */
    private void fixPageNumberTabs(KnuthSequence seq, int from, int to, LineArea lineArea) {
        if (getPSLM() == null) {
            return;
        }
        List<InlineArea> flat = new ArrayList<InlineArea>();
        flatten(lineArea.getInlineAreas(), flat);
        for (int i = Math.max(0, from); i <= to && i < seq.size(); i++) {
            Object o = seq.get(i);
            if (!(o instanceof KnuthGlue)) {
                continue;
            }
            LayoutManager lm = tabLeader((KnuthGlue) o);
            if (lm == null || !pageNumberTabs.contains(lm)) {
                continue;
            }
            // a dot leader is wrapped in the blank Word's grid starts it with: the dots
            // themselves are what has to grow, and the wrapper and the line follow
            InlineArea tabArea = WordLeaders.phasedLeader(WordLeaders.leafArea(lm));
            if (tabArea == null) {
                continue;
            }
            int at = -1;
            for (int j = 0; j < flat.size(); j++) {
                if (flat.get(j) == tabArea) {
                    at = j;
                    break;
                }
            }
            if (at < 0) {
                continue;
            }
            for (int j = at + 1; j < flat.size(); j++) {
                if (!(flat.get(j) instanceof org.apache.fop.area.inline.UnresolvedPageNumber)) {
                    continue;
                }
                org.apache.fop.area.inline.UnresolvedPageNumber number
                        = (org.apache.fop.area.inline.UnresolvedPageNumber) flat.get(j);
                String[] ids = number.getIDRefs();
                if (ids != null && ids.length > 0 && ids[0] != null) {
                    getPSLM().addUnresolvedArea(ids[0], new TabPageNumberWidth(tabArea, number));
                    pageNumberTabs.remove(lm);   // this tab's area is placed; never twice
                }
                break;
            }
        }
    }

    /**
     * Put a table-of-contents entry's <b>stretching</b> leader on Word's grid.
     *
     * <p>Word draws a leader run's characters on a step of the character's advance
     * <b>rounded to 1/300 inch</b>, and begins the run on a whole multiple of that step
     * measured from the <b>page's left edge</b> - {@link WordLeaders#gridPhase} has the rule and
     * the readings it was taken from.  Measured on the {@code tab-leader-kinds} golden
     * (batch 45 &#xa7;C.3): Word's hyphens sit at 151.270, 154.630 and 157.990, a step of
     * <b>3.36</b> = 14 cells of 240 millipoints, where ours sat at 149.418 and 152.784 on
     * the raw advance of <b>3.366</b>, anchored on the line rather than the page.  The
     * anchor is what the reader sees: Word's run opens <b>3.214pt</b> after the text and
     * ours opened <b>1.422pt</b>, under the 0.25 em a reader needs, so no space was read
     * there and the entry's text and its leader ran together in the text layer.
     *
     * <p>A <em>tab's</em> leader is gridded where the tab is settled
     * ({@link #dotLeaderPhase}), because that is where its x is known.  A
     * table-of-contents entry's leader is not a tab this manager lays out - the FO makes
     * it a stretching {@code fo:leader} and the justification gives it its width
     * (XsltFOFunctions.tabToFO) - and nothing positional reaches a leaf layout manager
     * either, so it is done here, once the line's areas exist and the x can be counted
     * off them.
     *
     * <p>Which leaders: those the FO marked {@link WordLayoutElementMapping#TOC_LEADER},
     * which XsltFOFunctions writes on exactly the stretching leader it makes for a
     * {@code w:tab} carrying a {@code w:leader}.  A leader the document did not ask for,
     * and a rule leader, carry no mark and are left alone - the mark is the test, not the
     * area's shape, because by this point the area is only a {@code FilledArea} and says
     * nothing about where it came from.
     *
     * <p>The line's width is unchanged: the run gives up the phase and the blank in front
     * of it takes exactly that (see {@link WordLeaders#gridPlacedLeader}), which matters because
     * the line is already justified and this leader is what absorbed its slack.
     *
     * @since 17.2.0
     */
    private void gridTocLeaders(KnuthSequence seq, int from, int to, LineArea lineArea) {
        if (!settings.leaderGrid()) {
            return;
        }
        /* A numbering label's leader is phased by WordListItemLayoutManager instead: the
         * origin Word measures from is the page's edge, and a label block's own distance
         * from it is not known until the list item has placed the label area
         * (FOP sets its x offset there), which happens after this line exists.  Measured
         * on numbering-leader-kinds: the true origin is 72000 (the region body) + 771
         * (the label area's x offset) + 18000 (the label block's start-indent) + the areas
         * before the leader, and the reconstruction here has only the first and the last of
         * those, so it was 18.771pt short and every such run opened 0.768pt to the left of
         * Word's.  (CR-001 batch 48 item 7) */
        if (inListItemLabel()) {
            return;
        }
        /* A leader whose pattern is use-content generates one element per element of its
         * own content, so the same manager is reported more than once and the phase would
         * be applied twice.  (CR-001 batch 48 item 2) */
        java.util.Set<LayoutManager> done = new java.util.HashSet<LayoutManager>();
        for (int i = Math.max(0, from); i <= to && i < seq.size(); i++) {
            Object o = seq.get(i);
            if (!(o instanceof KnuthElement)) {
                continue;
            }
            LayoutManager lm = tocLeader((KnuthElement) o);
            if (lm == null || !done.add(lm)) {
                continue;
            }
            InlineArea area = WordLeaders.leafArea(lm);
            if (!(area instanceof org.apache.fop.area.inline.FilledArea)) {
                continue;
            }
            int[] x = { 0 };
            if (!xOf(lineArea.getInlineAreas(), area, x)) {
                continue;
            }
            InlineArea phased = leaders.gridPlacedLeader((org.apache.fop.area.inline.FilledArea) area,
                    x[0] + lineArea.getStartIndent() + pageOffsetMpt(), null);
            if (phased != null) {
                replaceArea(lineArea.getInlineAreas(), area, phased);
            }
        }
    }

    /**
     * This element's layout manager where it is a table-of-contents entry's marked
     * stretching leader, else null.
     *
     * <p>Through the element's <b>position</b> chain, as {@link #tabLeader} does, and not
     * through {@code KnuthElement.getLayoutManager()}: an entry's leader sits inside the
     * {@code fo:inline}s that carry the entry's colour, size and hyperlink, and the
     * element then reports the outermost of those managers.
     *
     * <p>The <b>whole</b> chain is walked, and not only its innermost {@code LeafPosition}.
     * A leader of {@code leader-pattern="dots"} is a leaf - FOP builds its run itself - but
     * one of {@code leader-pattern="use-content"} lays its content out with a manager of
     * its own, so the innermost leaf names the <em>content</em>'s manager and the leader's
     * sits above it. A numbering tab's hyphen, underscore and middle-dot leaders are written
     * that way (XsltFOFunctions.numberingLeaderGlyph), and without this they were never
     * gridded: their characters stepped on the raw advance (3.663pt for a hyphen where Word
     * steps 3.60) and the run was anchored on the line instead of the page.  Widening the
     * walk cannot change a {@code dots} leader, whose manager is the leaf already.
     * (CR-001 batch 48 item 2)</p>
     */
    /** Whether this line's block is the label of an {@code fo:list-item} - a numbering
     *  label, whose leader {@link WordListItemLayoutManager} phases.
     *  @since 17.2.0 (CR-001 batch 48 item 7) */
    private boolean inListItemLabel() {
        for (org.apache.fop.fo.FONode n = fobj; n != null; n = n.getParent()) {
            if (n instanceof org.apache.fop.fo.flow.ListItemLabel) {
                return true;
            }
            if (n instanceof org.apache.fop.fo.flow.ListItemBody) {
                return false;
            }
        }
        return false;
    }

    private LayoutManager tocLeader(KnuthElement element) {
        for (Position p = element.getPosition(); p != null; p = p.getPosition()) {
            LayoutManager lm = p.getLM();
            if (!(lm instanceof org.apache.fop.layoutmgr.inline.LeaderLayoutManager)) {
                continue;
            }
            String kind = foreignAttribute(lm.getFObj(), WordLayoutElementMapping.TOC_LEADER);
            if (kind != null && kind.length() > 0) {
                return lm;
            }
        }
        return null;
    }

    /**
     * How far into the line {@code target} begins, in millipoints, adding up the areas
     * painted before it; true where it was found.
     *
     * <p>Not {@link #flatten}, which is for order and not for width: it lists a nested
     * inline <b>and</b> its children, so adding those IPDs up counts the nested ones
     * twice.  Here a parent is either descended into or counted, never both, and a
     * {@code FilledArea} is counted whole - {@code FilledArea.getChildAreas()} does not
     * return its children at all but its repeating unit repeated to fill its width,
     * computed on the call.
     */
    private static boolean xOf(List<?> areas, InlineArea target, int[] x) {
        for (Object o : areas) {
            if (!(o instanceof InlineArea)) {
                continue;
            }
            InlineArea a = (InlineArea) o;
            if (a == target) {
                return true;
            }
            if (a instanceof org.apache.fop.area.inline.InlineParent
                    && !(a instanceof org.apache.fop.area.inline.FilledArea)) {
                if (xOf(((org.apache.fop.area.inline.InlineParent) a).getChildAreas(), target, x)) {
                    return true;
                }
            } else {
                x[0] += a.getIPD();
            }
        }
        return false;
    }

    /** Put {@code fresh} where {@code old} sits in this line's areas, at whatever depth. */
    @SuppressWarnings("unchecked")
    private static boolean replaceArea(List<?> areas, InlineArea old, InlineArea fresh) {
        for (int i = 0; i < areas.size(); i++) {
            Object a = areas.get(i);
            if (a == old) {
                ((List<InlineArea>) areas).set(i, fresh);
                return true;
            }
            if (a instanceof org.apache.fop.area.inline.InlineParent
                    && !(a instanceof org.apache.fop.area.inline.FilledArea)
                    && replaceArea(((org.apache.fop.area.inline.InlineParent) a).getChildAreas(),
                            old, fresh)) {
                return true;
            }
        }
        return false;
    }

    /** This line's inline areas in the order they are painted; a FilledArea (a dot
     *  leader) repeats its children to fill its width, so it is left whole. */
    private static void flatten(List<InlineArea> areas, List<InlineArea> out) {
        for (InlineArea a : areas) {
            out.add(a);
            if (a instanceof org.apache.fop.area.inline.InlineParent
                    && !(a instanceof org.apache.fop.area.inline.FilledArea)) {
                flatten(((org.apache.fop.area.inline.InlineParent) a).getChildAreas(), out);
            }
        }
    }

    /** whether this tab leader stands in for a right {@code w:ptab} rather than a
     *  {@code w:tab} (XsltFOFunctions.ptabToFO).  @since 17.0.5 */
    private boolean isPtabRight(LayoutManager lm) {
        return TAB_PTAB_RIGHT
                .equals(foreignAttribute(lm.getFObj(), WordLayoutElementMapping.TAB));
    }

    /** whether this tab leader stands in for a centre {@code w:ptab}.  @since 17.2.0 */
    private boolean isPtabCenter(LayoutManager lm) {
        return TAB_PTAB_CENTER
                .equals(foreignAttribute(lm.getFObj(), WordLayoutElementMapping.TAB));
    }

    /**
     * How a line holding a tab is aligned.  The tabs on it are <em>sized</em> as if the
     * line began at the paragraph's left indent, whatever the alignment - the stops are
     * absolute (measured on a w:jc="right" footer whose tabs Word placed from the left
     * margin; see tabWidth) - and Word then aligns the whole line, the tabs' widths
     * counted in, by the paragraph's <code>w:jc</code> (measured on the
     * <code>tab-jc</code> probe; §4.4).
     *
     * <p>A justified line is the exception, and 17.1.0 first made every such line
     * start-aligned.  Measured, Word draws the distinction at what follows the
     * <em>last</em> tab: where a tab is the last thing on the line, or the words after it
     * do not reach the end, the tab absorbs the slack and the line is laid out from the
     * start (the <code>tab-jc</code> probe, whose trailing tab Word leaves at x=72); but
     * where text follows the tab, Word justifies that text to the right indent as it
     * would on any other line.  Measured on a numbered clause reading
     * <code>1.1.&lt;tab&gt;Настоящий договор ...</code> in a w:jc="both" paragraph: the
     * words and the tab position are ours exactly (both start the text at the same x),
     * and Word stretches the spaces to x1=560.1 where the start-aligned line stopped at
     * 530.8 - 29.3pt of stretch lost, on every such line of the document.  784 blocks in
     * 83 documents of three corpora carry both a tab and w:jc="both".</p>
     *
     * <p>The stretch is FOP's, applied to every stretchable area of the line, so it may
     * be kept only where nothing stretchable sits <em>before</em> the last tab - which is
     * the ordinary shape, a label or a number and then the tab.  Otherwise the tab's
     * settled width would no longer put the text on its stop, and the line goes back to
     * being laid out from the start.</p>
     *
     * @since 17.1.0
     */
    private int alignmentForTabLine(int textAlign, KnuthSequence par, int from, int to) {
        if (textAlign != Constants.EN_JUSTIFY) {
            return textAlign;
        }
        return justifiableTabLine(par, from, to) ? Constants.EN_JUSTIFY : Constants.EN_START;
    }

    /** Whether a justified line holding a tab is one Word stretches: text after the last
     *  tab, and nothing stretchable before it. */
    private boolean justifiableTabLine(KnuthSequence par, int from, int to) {
        int lastTab = -1;
        int start = Math.max(0, from);
        int end = Math.min(to, par.size() - 1);
        for (int i = start; i <= end; i++) {
            Object e = par.get(i);
            if (e instanceof KnuthGlue && tabLeader((KnuthGlue) e) != null) {
                lastTab = i;
            }
        }
        if (lastTab < 0) {
            return false;
        }
        boolean textAfter = false;
        for (int i = lastTab + 1; i <= end; i++) {
            Object e = par.get(i);
            if (e instanceof KnuthElement && ((KnuthElement) e).isBox()
                    && ((KnuthElement) e).getWidth() > 0) {
                textAfter = true;
                break;
            }
        }
        if (!textAfter) {
            return false;
        }
        for (int i = start; i < lastTab; i++) {
            Object e = par.get(i);
            if (e instanceof KnuthGlue && tabLeader((KnuthGlue) e) == null
                    && ((KnuthGlue) e).getStretch() > 0) {
                return false;
            }
        }
        return true;
    }

    /** whether the line between these element indexes holds a tab */
    private boolean lineHasTab(KnuthSequence par, int from, int to) {
        if (!hasTabStops) {
            return false;
        }
        for (int i = Math.max(0, from); i <= to && i < par.size(); i++) {
            Object e = par.get(i);
            if (e instanceof KnuthGlue && tabLeader((KnuthGlue) e) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * What {@code w:leader} draws: nothing, or a run of a character.
     *
     * <p>Word draws <b>every</b> leader as characters, in the paragraph mark's font and
     * size, and the {@code tab-leader-kinds} golden says which: {@code dot} a full stop,
     * {@code middleDot} U+00B7, {@code hyphen} a hyphen, and <b>both</b> {@code underscore}
     * and {@code heavy} an underscore - its page carries no stroked or filled path at all.
     * XSL FO offers no repeating glyph but the dot, so ours asked FOP for a
     * <em>rule</em> for the other three: a drawn path with nothing in the text layer, and
     * for {@code heavy} that is all it ever was ({@code docx4j.convert.out.fo.wordLayout.leaderCharacters}).</p>
     *
     * @since 17.1.0; the character kinds 17.2.0
     */
    private int tabLeaderKind(String v) {
        if (settings.leaderCharacters()) {
            if ("middleDot".equals(v)) {
                return WordLeaders.LEADER_MIDDLE_DOT;
            }
            if ("underscore".equals(v) || "heavy".equals(v)) {
                return WordLeaders.LEADER_UNDERSCORE;
            }
            if ("hyphen".equals(v)) {
                return WordLeaders.LEADER_HYPHEN;
            }
        }
        if ("dot".equals(v) || "middleDot".equals(v)) {
            return WordLeaders.LEADER_DOTS;
        }
        if ("hyphen".equals(v) || "underscore".equals(v) || "heavy".equals(v)) {
            return WordLeaders.LEADER_RULE;
        }
        return WordLeaders.LEADER_NONE;   // none, and anything unknown
    }

    private static int tabAlignment(String v) {
        if ("center".equals(v)) {
            return TAB_CENTER;
        }
        if ("right".equals(v) || "end".equals(v)) {
            return TAB_RIGHT;
        }
        if ("decimal".equals(v)) {
            return TAB_DECIMAL;
        }
        if ("clear".equals(v)) {
            return TAB_CLEAR;
        }
        return TAB_LEFT; // left, start, bar, num
    }

    private static final int RULE_AUTO = 0;
    private static final int RULE_EXACT = 1;
    private static final int RULE_AT_LEAST = 2;
    private final int wordLineRule;

    /**
     * Word's text box for a line of this paragraph and the baseline within it,
     * in millipoints, from the block's docx4j:line-box / docx4j:baseline
     * attributes ({@link WordLayoutElementMapping}); 0 when absent, in which
     * case lines are made as FOP makes them.  The block's line-height less the
     * box is the extra leading, emitted as {@link LeadingGlue} after each line
     * so that it is dropped at the bottom of a page, as Word drops it.
     */
    private final int wordLineBox;
    private final int wordBaseline;

    public static String foreignAttribute(org.apache.fop.fo.FObj block, String localName) {
        if (block == null) {
            return null;
        }
        java.util.Map<?, ?> attrs = block.getForeignAttributes();
        if (attrs == null) {
            return null;
        }
        for (java.util.Map.Entry<?, ?> e : attrs.entrySet()) {
            org.apache.xmlgraphics.util.QName name = (org.apache.xmlgraphics.util.QName) e.getKey();
            if (WordLayoutElementMapping.URI.equals(name.getNamespaceURI())
                    && localName.equals(name.getLocalName())) {
                return String.valueOf(e.getValue()).trim();
            }
        }
        return null;
    }

    /** A foreign attribute of the innermost inline this element comes from which has it: the
     *  element's own layout manager is the outermost inline's once positions are wrapped, and
     *  docx4j's run span (docx4j:font) is usually inside another.  @since 17.3.1 */
    static String innermostForeignAttribute(KnuthElement element, String localName) {
        java.util.List<org.apache.fop.layoutmgr.LayoutManager> lms =
                new java.util.ArrayList<org.apache.fop.layoutmgr.LayoutManager>();
        for (org.apache.fop.layoutmgr.Position p = element.getPosition(); p != null; p = p.getPosition()) {
            if (p.getLM() != null) {
                lms.add(p.getLM());
            }
            if (p.getPosition() == p) {
                break;
            }
        }
        for (int i = lms.size() - 1; i >= 0; i--) {
            org.apache.fop.fo.FObj fobj = lms.get(i).getFObj();
            if (fobj instanceof Block) {
                break;
            }
            String v = foreignAttribute(fobj, localName);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static int foreignLength(Block block, String localName) {
        String v = foreignAttribute(block, localName);
        if (v == null) {
            return 0;
        }
        if (v.endsWith("pt")) {
            v = v.substring(0, v.length() - 2);
        }
        try {
            return (int) Math.round(Double.parseDouble(v) * 1000);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String blockFontName;

    /** The full name of the block's own font, which {@link #wordLine} tells the block's
     *  text by.  @since 17.3.1 */
    private String blockFontName() {
        if (blockFontName == null) {
            FontInfo fi = fobj.getFOEventHandler().getFontInfo();
            FontTriplet[] fontkeys = fobj.getCommonFont().getFontState(fi);
            Font fs = fi.getFontInstance(fontkeys[0], fobj.getCommonFont().fontSize.getValue(this));
            blockFontName = fs.getFontMetrics() == null ? "" : fs.getFontMetrics().getFullName();
        }
        return blockFontName;
    }

    /**
     * Get a sequence of KnuthElements representing the content
     * of the node assigned to the LM.
     * @param context   the LayoutContext used to store layout information
     * @param alignment the desired text alignment
     * @param restartPosition position at restart
     * @return the list of KnuthElements
     * @see LayoutManager#getNextKnuthElements(LayoutContext,int)
     */
    @Override
    void processInlineElements(InlineLevelLayoutManager curLM, List<KnuthSequence> inlineElements) {
        fixLetterSpaces(curLM, inlineElements);
        fixSpaceLetterSpaces(inlineElements);
        suppressWordBreaks(inlineElements);
        solidusLeadingBreaks(inlineElements);
    }

    @Override
    void endingParagraph(Paragraph par, boolean atBlockSequence) {
        /* Where it ends at a block sequence, that block sequence is a soft return where the paragraph is one
         * whose soft returns Word justifies (docx4j:justify-soft-return is written only for a justified paragraph
         * whose sole block-level content is soft returns), so the line this sequence ends is justified like any
         * other of the paragraph.  @since 17.1.0
         * Where the manager's own content ends it: the XSLT pathway's shape, the break being the paragraph's next
         * block-level child, so this manager's own last line is the one that ends at it. */
        if (justifySoftReturn && textAlignment == EN_JUSTIFY && (atBlockSequence || followedBySoftReturn)) {
            par.justifyLastLine();
        }
    }

    /** A Word line's leading (its spaceAfter) goes into a LeadingGlue after the next break possibility. */
    @Override
    boolean hasLeadingBelowLines() {
        return wordLineBox > 0;
    }

    @Override
    int lineAlignment(KnuthSequence seq, int from, int to, int alignment) {
        return lineHasTab(seq, from, to) ? alignmentForTabLine(alignment, seq, from, to) : alignment;
    }

    /** A line ending in a tab keeps it: the tab is not a space, and dropping it would take the leader of a line
     *  broken at the tab after it; and a line Word broke at a tab begins with that tab, which is measured from the
     *  line's start.  @since 17.1.0 */
    @Override
    boolean isTab(KnuthGlue glue) {
        return tabLeader(glue) != null;
    }

    @Override
    void lineAreasAdded(KnuthSequence seq, int from, int to, LineArea lineArea) {
        // a right/centre/decimal tab measured an unresolved page number as "MMM": give
        // the width back to the tab when it resolves (@since 17.1.0)
        if (!pageNumberTabs.isEmpty()) {
            fixPageNumberTabs(seq, from, to, lineArea);
        }
        // a table-of-contents entry's stretching leader, which no tab settled, goes on
        // Word's grid here - after the page-number pass, which finds its tabs by identity
        // (@since 17.2.0)
        gridTocLeaders(seq, from, to, lineArea);
    }

    @Override
    public List<ListElement> getNextKnuthElements(LayoutContext context, int alignment,
            LeafPosition restartPosition) {
        /* Nothing to restart: a paragraph with no content made no Knuth paragraphs (or was
         * never asked for its elements), and a restart at it - a block read again from its start
         * after a forced page break, fork CR-024 r33's restart at an obstacle's page, with a
         * position of index -1 - threw here, and corpus 3475's last page lost its text.
         * @since 17.3.3 */
        if (knuthParagraphs == null || knuthParagraphs.isEmpty()) {
            setFinished(true);
            return null;
        }
        return super.getNextKnuthElements(context, alignment, restartPosition);
    }

    /**
     * Phase 1 of Knuth algorithm: Collect all inline Knuth elements before determining line breaks.
     * @param context the LayoutContext
     */
    /**
     * FOP 2.11's GlyphMapping.processWordMapping (the path taken for fonts with
     * GSUB or GPOS tables, so every embedded OpenType font while complex-script
     * features are on) never adds the letter spaces to a word's width, while the
     * renderer still spaces every glyph (TextArea's letter-space adjust); so
     * lines with letter-spacing overflow the margin (measured, CR-001 §6.6 item
     * 16: 3pt spacing, 95pt over; 0.25pt, one word too many).  The plain path,
     * processWordNoMapping, counts wordLength - 1 spaces plus one when a break
     * opportunity that is not a space follows - one short of Word, which spaces
     * the word's last character too.  Both are brought here to Word's count, one
     * letter space after every character of the word (CR-001 §4.6).
     *
     * <p>How many of the counted spaces the width already holds depends on the
     * FOP: see {@link #lettersInWidth}.  Runs while each child's elements are
     * collected, so before {@link #emergencyBreaks} builds mappings of its own
     * (which count their letter spaces and include them).</p>
     */
    private void fixLetterSpaces(InlineLevelLayoutManager lm, List<KnuthSequence> seqs) {
        if (seqs == null) {
            return;
        }
        for (KnuthSequence seq : seqs) {
            for (int i = 0; i < seq.size(); i++) {
                Object o = seq.get(i);
                if (!(o instanceof KnuthInlineBox)) {
                    continue;
                }
                KnuthInlineBox box = (KnuthInlineBox) o;
                // the child is usually an fo:inline's manager, whose elements wrap the
                // text manager's positions: unwrap to the text manager's leaf position
                Position pos = box.getPosition();
                Position leaf = pos;
                while (leaf != null && !(leaf instanceof LeafPosition)) {
                    leaf = leaf.getPosition();
                }
                if (leaf == null || !(leaf.getLM() instanceof org.apache.fop.layoutmgr.inline.TextLayoutManager)) {
                    continue;
                }
                org.apache.fop.layoutmgr.inline.TextLayoutManager tlm =
                        (org.apache.fop.layoutmgr.inline.TextLayoutManager) leaf.getLM();
                MinOptMax ls = tlm.getLetterSpaceIPD();
                if (ls == null || (ls.getOpt() == 0 && ls.getStretch() == 0 && ls.getShrink() == 0)) {
                    continue;
                }
                List<org.apache.fop.fonts.GlyphMapping> mappings = tlm.getMappings();
                int idx = ((LeafPosition) leaf).getLeafPos();
                if (idx < 0 || idx >= mappings.size()) {
                    continue;
                }
                org.apache.fop.fonts.GlyphMapping m = mappings.get(idx);
                if (m.isSpace || m.font == null) {
                    continue;
                }
                int wordLength = m.getWordLength();
                int inWidth = lettersInWidth(m.font.performsSubstitution() || m.font.performsPositioning(),
                        true, m.letterSpaceCount);
                if (wordLength <= 0 || inWidth == wordLength) {
                    continue;
                }
                org.apache.fop.fo.FOText foText = tlm.getFOText();
                // Word puts one character space after every character, the word's last
                // included, and one after a space (see fixSpaceLetterSpaces).  Measured
                // on the spacing-char probe's golden, Times New Roman 12pt, w:spacing 20
                // (1pt): "expanded" advances 6.228 6.948 7.068 6.228 6.948 7.068 6.228
                // 7.068 - the final "d" is 6.0 + 1.068, and the space that follows is
                // 3.0 + 0.948.  (Until 17.1.0 this counted wordLength - 1, which was
                // right only while FOP's doubled word space, now corrected, made up the
                // difference.)  FOP paints one after every glyph, so measure and paint
                // now agree.
                boolean breakOpp = m.breakOppAfter && m.endIndex < foText.length()
                        && !Character.isWhitespace(foText.charAt(m.endIndex));
                int spaces = wordLength;
                int added = spaces - inWidth;
                m.breakOppAfter = breakOpp;
                m.letterSpaceCount = spaces;
                m.areaIPD = m.areaIPD.plus(ls.mult(added));
                boolean suppressible = breakOpp && !m.isHyphenated;
                int width = ls.isStiff()
                        ? (suppressible ? m.areaIPD.getOpt() - ls.getOpt() : m.areaIPD.getOpt())
                        : m.areaIPD.getOpt() - spaces * ls.getOpt();
                if (WordLineLayoutManager.log.isDebugEnabled()) {
                    WordLineLayoutManager.log.debug("letter spaces restored: word " + m.startIndex + "-" + m.endIndex
                            + " +" + spaces + " x " + ls.getOpt() + " -> box " + width);
                }
                seq.set(i, new KnuthInlineBox(width, box.getAlignmentContext(), pos, box.isAuxiliary()));
            }
        }
    }

    /**
     * How many of a word's counted letter spaces ({@code GlyphMapping.letterSpaceCount}) its
     * width already holds.  The plain path ({@code processWordNoMapping}) has always added them.
     * The complex-script path ({@code processWordMapping}, the one {@code GlyphMapping.doGlyphMapping}
     * takes when the font performs substitution or positioning) counted none in FOP 2.11; Apache's
     * FOP-2722 gives it the count but not the width, so on a FOP carrying FOP-2722 alone the count
     * must not be trusted, or every letter-spaced word is measured n - 1 letter spaces short
     * (docx4j's own width fixups letter-space ordinary text too: on the fork's merge of Apache main,
     * fop/CR-009, 51 documents regressed before its CR-010 added the width).  The fork with that
     * fix declares {@code letter-space-width}; there, as on the plain path, the count is in the width.
     *
     * @param mappingPath whether the word took the complex-script path
     * @param widthFix whether the renderer declares {@code letter-space-width}
     * @param letterSpaceCount the mapping's count
     * @since 17.3.1
     */
    static int lettersInWidth(boolean mappingPath, boolean widthFix, int letterSpaceCount) {
        return mappingPath && !widthFix ? 0 : letterSpaceCount;
    }

    /**
     * Word gives a space the same character spacing as any other character - one
     * {@code w:spacing w:val} after it, so its advance is the font's space plus
     * val/20 pt.  FOP's {@code SpaceVal.makeWordSpacing} adds the letter space
     * <em>twice</em> to the word space (its own TODO: "Adding 2 letter spaces here
     * is not 100% correct"), so every space in a letter-spaced run is measured
     * val/20 pt too wide, and lines break early.
     *
     * <p>Measured (CR-001 §4.6, corpus batch 1): Times New Roman 11pt, a run of one
     * space carrying {@code w:spacing w:val="19"} (0.95pt).  The natural space is
     * 2.75pt; Word's advance is 3.87pt and ours was 4.65 = 2.75 + 2 x 0.95.  In one
     * document 620 such runs made a 447.4pt line 458.5pt.
     *
     * <p>FOP's <em>painting</em> already follows Word: {@code addMappingAreas} sets
     * the word-space adjust to {@code wordSpaceIPD - spaceCharIPD - 2 x letterSpace},
     * so the glyph advance comes out at space + one letter space.  Only the measure
     * is wrong.  The correction therefore goes on the glyph mapping (which is what
     * both the Knuth element and the text area's width are taken from) and leaves
     * FOP's {@code wordSpaceIPD} alone, so that the painted advance is unchanged.
     */
    private void fixSpaceLetterSpaces(List<KnuthSequence> seqs) {
        if (seqs == null) {
            return;
        }
        java.util.Map<org.apache.fop.fonts.GlyphMapping, int[]> fixed = new java.util.IdentityHashMap<>();
        for (KnuthSequence seq : seqs) {
            for (int i = 0; i < seq.size(); i++) {
                Object o = seq.get(i);
                if (!(o instanceof KnuthGlue) && !(o instanceof KnuthInlineBox)) {
                    continue;
                }
                KnuthElement el = (KnuthElement) o;
                Position pos = el.getPosition();
                Position leaf = pos;
                while (leaf != null && !(leaf instanceof LeafPosition)) {
                    leaf = leaf.getPosition();
                }
                if (leaf == null || !(leaf.getLM() instanceof org.apache.fop.layoutmgr.inline.TextLayoutManager)) {
                    continue;
                }
                int idx = ((LeafPosition) leaf).getLeafPos();
                if (idx < 0) {
                    continue;
                } // the auxiliary positions, which carry no width
                org.apache.fop.layoutmgr.inline.TextLayoutManager tlm =
                        (org.apache.fop.layoutmgr.inline.TextLayoutManager) leaf.getLM();
                MinOptMax ls = tlm.getLetterSpaceIPD();
                if (ls == null || ls.getOpt() == 0) {
                    continue;
                }
                List<org.apache.fop.fonts.GlyphMapping> mappings = tlm.getMappings();
                if (idx >= mappings.size()) {
                    continue;
                }
                org.apache.fop.fonts.GlyphMapping m = mappings.get(idx);
                if (!m.isSpace || m.wordSpaceCount <= 0) {
                    continue;
                }
                int[] delta = fixed.get(m);
                if (delta == null) {
                    int spaceChar = tlm.getSpaceCharIPD();
                    int doubled = m.wordSpaceCount * (spaceChar + 2 * ls.getOpt());
                    if (m.areaIPD.getOpt() != doubled) {
                        continue;
                    } // not the doubled form
                    int once = m.wordSpaceCount * (spaceChar + ls.getOpt());
                    delta = new int[] {doubled - once};
                    fixed.put(m, delta);
                    m.areaIPD = MinOptMax.getInstance(once - m.areaIPD.getShrink(), once,
                            once + m.areaIPD.getStretch());
                    if (WordLineLayoutManager.log.isDebugEnabled()) {
                        WordLineLayoutManager.log.debug("space letter-spacing counted once: "
                                + doubled + " -> " + once);
                    }
                }
                // exactly one element per space mapping carries the mapping's own
                // (non-auxiliary) position, and its width includes the mapping's optimum
                int width = (o instanceof KnuthGlue)
                        ? ((KnuthGlue) o).getWidth() : ((KnuthInlineBox) o).getWidth();
                if (width < delta[0]) {
                    continue;
                }
                if (o instanceof KnuthGlue) {
                    KnuthGlue g = (KnuthGlue) o;
                    seq.set(i, new KnuthGlue(width - delta[0], g.getStretch(), g.getShrink(),
                            g.getPosition(), g.isAuxiliary()));
                } else {
                    KnuthInlineBox b = (KnuthInlineBox) o;
                    seq.set(i, new KnuthInlineBox(width - delta[0], b.getAlignmentContext(),
                            b.getPosition(), b.isAuxiliary()));
                }
            }
        }
    }

    /**
     * Break opportunities inside a token which UAX #14, as FOP's text managers apply
     * it, offers and Word does not take ({@link WordBreakOpportunities#noBreakBetween})
     * are made infinite penalties here.  Three so far.  Word does not break a line after
     * a solidus: measured on the Getting Started guide (CR-001 §6.10),
     * "http://schemas.openxmlformats.org/..." and "OpenOffice/jodconverter" go whole
     * to the next line where FOP breaks after the "/".  And it does not break between
     * a letter and a backslash, where FOP's pair table, generated from a Unicode
     * version before 8.0's LB24, does: measured on a 311-page corpus document, Word sets
     * {@code Quejas\Clientes\Minoristas} whole on a line where ours broke it before each
     * backslash (17.2.0; until then only the solidus was suppressed).  Only the
     * backslash: Word breaks between a letter and a dollar sign, as FOP does.  The third
     * is the same defect in the same table for the other half of LB24's pair: Word does
     * not break between a letter and a per-cent sign, where FOP holds {@code AL x PO} as
     * a direct break and sets {@code VAT|%} (17.2.0, CR-001 batch 48 item 3; a per-cent
     * sign after a <em>digit</em> never broke, {@code NU x PO} being indirect).  The one
     * correction which goes the other way - Word breaks <b>after</b> a hyphen followed by
     * digits, where the table's {@code HY x NU} is indirect and so no break inside a word
     * - cannot be made here, there being no penalty to relax: it is
     * {@link WordBreakOpportunities#applyWordPairTable}.
     */
    private void suppressWordBreaks(List<KnuthSequence> seqs) {
        if (seqs == null) {
            return;
        }
        for (KnuthSequence seq : seqs) {
            for (int i = 1; i < seq.size(); i++) {
                Object o = seq.get(i);
                if (!(o instanceof KnuthPenalty)) {
                    continue;
                }
                KnuthPenalty p = (KnuthPenalty) o;
                if (p.getPenalty() >= KnuthElement.INFINITE || p.isForcedBreak()) {
                    continue;
                }
                // back to the word box the opportunity follows: a text manager puts
                // "box, aux penalty, glue, penalty, glue" for a break after a character
                int j = i - 1;
                while (j > 0 && (seq.get(j) instanceof KnuthPenalty
                        || (seq.get(j) instanceof KnuthGlue && ((KnuthGlue) seq.get(j)).getWidth() == 0)
                        || (seq.get(j) instanceof KnuthInlineBox && ((KnuthInlineBox) seq.get(j)).isAuxiliary()
                            && ((KnuthInlineBox) seq.get(j)).getWidth() == 0))) {
                    j--;
                }
                Object before = seq.get(j);
                if (!(before instanceof KnuthInlineBox)) {
                    continue;
                }
                Position leaf = ((KnuthInlineBox) before).getPosition();
                while (leaf != null && !(leaf instanceof LeafPosition)) {
                    leaf = leaf.getPosition();
                }
                if (leaf == null || !(leaf.getLM() instanceof org.apache.fop.layoutmgr.inline.TextLayoutManager)) {
                    continue;
                }
                org.apache.fop.layoutmgr.inline.TextLayoutManager tlm =
                        (org.apache.fop.layoutmgr.inline.TextLayoutManager) leaf.getLM();
                List<org.apache.fop.fonts.GlyphMapping> mappings = tlm.getMappings();
                int idx = ((LeafPosition) leaf).getLeafPos();
                if (idx < 0 || idx >= mappings.size()) {
                    continue;
                }
                org.apache.fop.fonts.GlyphMapping m = mappings.get(idx);
                org.apache.fop.fo.FOText foText = tlm.getFOText();
                if (m.endIndex <= 0 || m.endIndex > foText.length()) {
                    continue;
                }
                // and forward to the box the opportunity precedes, for its first character
                int after = -1;
                for (int k = i + 1; k < seq.size(); k++) {
                    Object next = seq.get(k);
                    if (next instanceof KnuthInlineBox && !((KnuthInlineBox) next).isAuxiliary()) {
                        org.apache.fop.fonts.GlyphMapping nm = mappingOf(next);
                        org.apache.fop.layoutmgr.inline.TextLayoutManager ntlm = tlmOf(next);
                        if (nm != null && ntlm != null && nm.startIndex < ntlm.getFOText().length()) {
                            after = ntlm.getFOText().charAt(nm.startIndex);
                        }
                        break;
                    }
                    if (next instanceof KnuthInlineBox && ((KnuthInlineBox) next).getWidth() != 0) {
                        break;
                    }
                    if (next instanceof KnuthGlue && ((KnuthGlue) next).getWidth() != 0) {
                        break;
                    }
                }
                if (!WordBreakOpportunities.noBreakBetween(foText.charAt(m.endIndex - 1), after)) {
                    continue;
                }
                KnuthPenalty suppressed = new KnuthPenalty(p.getWidth(), KnuthElement.INFINITE, false,
                        p.getPosition(), p.isAuxiliary());
                suppressedBreaks.put(suppressed, Boolean.TRUE);
                seq.set(i, suppressed);
            }
        }
    }

    /** The glyph mapping a Knuth element belongs to, or null for an auxiliary one. */
    private static org.apache.fop.fonts.GlyphMapping mappingOf(Object element) {
        if (!(element instanceof KnuthElement)) {
            return null;
        }
        Position leaf = ((KnuthElement) element).getPosition();
        while (leaf != null && !(leaf instanceof LeafPosition)) {
            leaf = leaf.getPosition();
        }
        if (leaf == null || !(leaf.getLM() instanceof org.apache.fop.layoutmgr.inline.TextLayoutManager)) {
            return null;
        }
        org.apache.fop.layoutmgr.inline.TextLayoutManager tlm =
                (org.apache.fop.layoutmgr.inline.TextLayoutManager) leaf.getLM();
        int idx = ((LeafPosition) leaf).getLeafPos();
        List<org.apache.fop.fonts.GlyphMapping> mappings = tlm.getMappings();
        if (idx < 0 || idx >= mappings.size()) {
            return null;
        }
        return mappings.get(idx);
    }

    /** The text manager a Knuth element belongs to, or null. */
    private static org.apache.fop.layoutmgr.inline.TextLayoutManager tlmOf(Object element) {
        if (!(element instanceof KnuthElement)) {
            return null;
        }
        Position leaf = ((KnuthElement) element).getPosition();
        while (leaf != null && !(leaf instanceof LeafPosition)) {
            leaf = leaf.getPosition();
        }
        if (leaf == null || !(leaf.getLM() instanceof org.apache.fop.layoutmgr.inline.TextLayoutManager)) {
            return null;
        }
        return (org.apache.fop.layoutmgr.inline.TextLayoutManager) leaf.getLM();
    }

    /**
     * Word breaks <em>before</em> a word which begins with a solidus; UAX #14, which
     * FOP's text managers follow, does not (rule LB13 forbids a break before class SY),
     * so the space and the slash-led word become one unbreakable unit.
     *
     * <p>Measured: a header cell 84.2pt wide holding "Roll Number /Registration Number"
     * - all ordinary U+0020 - was painted "Roll" then a 93.5pt
     * "Number /Registration" running 6pt outside the table, where Word sets
     * "Roll Number " and "/Registration ". FOP's elements for it are
     * {@code box("Number") box(" ") box("/") penalty ...}: the space is a
     * <em>non-breaking</em> box, so a zero penalty is inserted after it.  Where the
     * paragraph is justified the space is a glue behind an infinite penalty, and that
     * penalty is relaxed instead.  &#xa7;4.3's rule that Word does not break
     * <em>after</em> a solidus is unchanged.</p>
     *
     * @since 17.1.0
     */
    private void solidusLeadingBreaks(List<KnuthSequence> seqs) {
        if (seqs == null) {
            return;
        }
        for (KnuthSequence seq : seqs) {
            for (int i = seq.size() - 2; i >= 0; i--) {
                Object o = seq.get(i);
                org.apache.fop.fonts.GlyphMapping space = mappingOf(o);
                if (space == null || !space.isSpace) {
                    continue;
                }
                // the next element carrying a mapping: is it a word starting with "/"?
                org.apache.fop.fonts.GlyphMapping next = null;
                int nextIdx = -1;
                for (int j = i + 1; j < seq.size() && j <= i + 4; j++) {
                    next = mappingOf(seq.get(j));
                    if (next != null) {
                        nextIdx = j;
                        break;
                    }
                }
                if (next == null || next == space || next.isSpace) {
                    continue;
                }
                org.apache.fop.layoutmgr.inline.TextLayoutManager tlm = tlmOf(seq.get(nextIdx));
                if (tlm == null) {
                    continue;
                }
                org.apache.fop.fo.FOText foText = tlm.getFOText();
                // the same predicate the autofit sizer measures with (WordBreakOpportunities):
                // until 17.2.0 this read the character after the mapping, so "/123" - a
                // solidus UAX #14 keeps with the digits after it - was not a solidus-led word
                if (!WordBreakOpportunities.startsSolidusLedWord(foText, next.startIndex)) {
                    continue;
                }
                if (o instanceof KnuthGlue) {
                    // justified: relax the infinite penalty in front of the space glue
                    if (i > 0 && seq.get(i - 1) instanceof KnuthPenalty
                            && ((KnuthPenalty) seq.get(i - 1)).getPenalty() >= KnuthElement.INFINITE) {
                        KnuthPenalty inf = (KnuthPenalty) seq.get(i - 1);
                        seq.set(i - 1, new KnuthPenalty(inf.getWidth(), 0, false,
                                inf.getPosition(), inf.isAuxiliary()));
                    }
                } else if (o instanceof KnuthInlineBox) {
                    Position aux = auxiliaryPosition(seq, i);
                    if (aux == null) {
                        continue;
                    }
                    seq.add(i + 1, new KnuthPenalty(0, 0, false, aux, true));
                }
            }
        }
    }

    // ---- a break opportunity at the seam of two runs ----------------------------------

    private final boolean seamBreakEnabled = settings.seamBreak();

    /**
     * A word split across two runs at a break opportunity - {@code foo-} in one
     * {@code fo:inline}, {@code bar} in the next - does not break at the seam in FOP, and
     * Word breaks after the hyphen whichever run it is in.  FOP does see the seam: when
     * the line manager joins two boxes from consecutive text managers
     * ({@code Paragraph.addALetterSpace}), a text ending in one of its
     * {@code BREAK_CHARS} ({@code -} and {@code /}) gets a break penalty after it - but a
     * <em>flagged</em> one, {@code KnuthPenalty.FLAGGED_PENALTY} as for a hyphenation
     * point, and with hyphenation off the breaking algorithm runs with
     * {@code NO_FLAGGED_PENALTIES} and never considers it; with hyphenation on it would
     * be taken after a solidus too, which Word does not do.  Any other seam inside a word
     * gets an infinite penalty and a letter-space glue, or nothing.  Measured on FOP
     * 2.11's area tree, {@code <fo:inline>aaaa bbbb-</fo:inline><fo:inline>cccc
     * dddd</fo:inline>} on a 62pt measure sets {@code aaaa} / {@code bbbb-cccc} /
     * {@code dddd}, where the same text in one inline sets {@code aaaa bbbb-} /
     * {@code cccc dddd} (word-layout-rules.md &#xa7;10).  A {@code w:r} boundary falls
     * inside a hyphenated word wherever an author edited one half of it: over the FO
     * emitted for three corpora, 608 such seams in 128 documents, at 107 of which Word's
     * PDF breaks the line and at 45 of which (17 documents) ours did not.
     *
     * <p>Where a non-auxiliary box from one text manager is followed by one from another
     * with nothing between them but what FOP puts inside a word - that flagged penalty,
     * an infinite penalty, a letter-space glue, an auxiliary box - the seam is decided by
     * {@link WordBreakOpportunities#breakAtSeam} on the last character of the one and the
     * first of the other: allowed, the flagged penalty is unflagged (or an infinite one
     * relaxed to zero, or a zero penalty inserted where there is none); not allowed, a
     * flagged penalty is made infinite and recorded with {@link #suppressedBreaks}, so
     * that neither hyphenation nor the emergency break reads it as a break.  A space on
     * either side, a penalty the line manager itself suppressed
     * ({@link #suppressWordBreaks}) and a seam FOP can already break at are left alone.
     * Runs before {@link #emergencyBreaks}, which would otherwise read the two halves as
     * one over-long word.</p>
     *
     * @since 17.2.0
     */
    private void seamBreaks(Paragraph par) {
        if (!seamBreakEnabled || par == null) {
            return;
        }
        for (int i = 0; i + 1 < par.size(); i++) {
            KnuthElement a = (KnuthElement) par.get(i);
            if (!a.isBox() || a.isAuxiliary()) {
                continue;
            }
            org.apache.fop.fonts.GlyphMapping ma = mappingOf(a);
            org.apache.fop.layoutmgr.inline.TextLayoutManager ta = tlmOf(a);
            if (ma == null || ta == null || ma.isSpace) {
                continue;
            }
            int j = i + 1;
            int infiniteAt = -1;
            int flaggedAt = -1;
            boolean seam = true;
            for (; j < par.size(); j++) {
                KnuthElement e = (KnuthElement) par.get(j);
                org.apache.fop.fonts.GlyphMapping m = mappingOf(e);
                if (m != null && m.isSpace) {
                    seam = false;
                    break;
                }
                if (e.isBox()) {
                    if (!e.isAuxiliary()) {
                        break;
                    } // the next word box
                    if (e.getWidth() != 0) {
                        seam = false;
                        break;
                    }
                    continue;
                }
                if (e instanceof KnuthPenalty) {
                    KnuthPenalty p = (KnuthPenalty) e;
                    if (suppressedBreaks.containsKey(p)) {  // by design no break
                        seam = false;
                        break;
                    }
                    if (p.getPenalty() >= KnuthElement.INFINITE) {
                        if (infiniteAt < 0) {
                            infiniteAt = j;
                        }
                        continue;
                    }
                    if (p.isPenaltyFlagged() && p.getWidth() == 0 && !p.isAuxiliary() && flaggedAt < 0) {
                        flaggedAt = j;                        // FOP's own, flagged, seam penalty
                        continue;
                    }
                    seam = false;                             // a legal break of FOP's own
                    break;
                }
                if (e.isGlue()) {
                    if (infiniteAt < 0 && flaggedAt < 0 && !e.isAuxiliary()) {
                        seam = false;
                        break;
                    }
                    continue;
                }
                seam = false;
                break;
            }
            if (!seam || j >= par.size()) {
                continue;
            }
            KnuthElement b = (KnuthElement) par.get(j);
            org.apache.fop.fonts.GlyphMapping mb = mappingOf(b);
            org.apache.fop.layoutmgr.inline.TextLayoutManager tb = tlmOf(b);
            if (mb == null || tb == null || tb == ta || mb.isSpace) {
                continue;
            }
            org.apache.fop.fo.FOText fa = ta.getFOText();
            org.apache.fop.fo.FOText fb = tb.getFOText();
            if (fa == null || fb == null || ma.endIndex <= 0 || ma.endIndex > fa.length()
                    || mb.startIndex < 0 || mb.startIndex >= fb.length()) {
                continue;
            }
            char last = fa.charAt(ma.endIndex - 1);
            char first = fb.charAt(mb.startIndex);
            boolean allowed = WordBreakOpportunities.breakAtSeam(last, first);
            if (flaggedAt >= 0) {
                KnuthPenalty f = (KnuthPenalty) par.get(flaggedAt);
                if (allowed) {
                    par.set(flaggedAt, new KnuthPenalty(0, 0, false, f.getPosition(), f.isAuxiliary()));
                } else {
                    // a break char Word does not break after (a solidus): no break, with
                    // hyphenation on either, and one word to the emergency break
                    KnuthPenalty suppressed = new KnuthPenalty(0, KnuthElement.INFINITE, false, f.getPosition(),
                            f.isAuxiliary());
                    suppressedBreaks.put(suppressed, Boolean.TRUE);
                    par.set(flaggedAt, suppressed);
                }
            } else if (allowed) {
                if (infiniteAt >= 0) {
                    KnuthPenalty inf = (KnuthPenalty) par.get(infiniteAt);
                    par.set(infiniteAt, new KnuthPenalty(inf.getWidth(), 0, false, inf.getPosition(),
                            inf.isAuxiliary()));
                } else {
                    Position aux = auxiliaryPosition(par, i);
                    if (aux == null) {
                        continue;
                    }
                    par.add(i + 1, new KnuthPenalty(0, 0, false, aux, true));
                    i++;
                }
            }
            if (allowed && log.isDebugEnabled()) {
                log.debug("break opportunity added at the seam of two runs after '" + last + "'");
            }
        }
    }

    // ---- Word's emergency break (E10) --------------------------------------------

    /**
     * Every element of a word this manager has broken inside, mapped to a token
     * standing for that word.  {@link LineBreakingAlgorithm#emergencyUsable} needs it to
     * tell an emergency break from an ordinary one, and to tell whether the line it
     * would end holds nothing but that word.
     *
     * @since 17.1.0
     */
    /** The penalties {@link #suppressWordBreaks} made infinite: break opportunities inside
     *  a word that Word does not take, which {@link #emergencyBreaks} must read as part of
     *  the word rather than its end.  @since 17.2.0 */
    private final java.util.IdentityHashMap<Object, Object> suppressedBreaks
            = new java.util.IdentityHashMap<Object, Object>();

    /** Whether the next non-auxiliary element after a glue is a penalty of
     *  {@link #suppressedBreaks}: the glue is then the first half of that join. */
    private boolean suppressedBreakFollows(Paragraph par, int glueIdx) {
        for (int k = glueIdx + 1; k < par.size(); k++) {
            KnuthElement el = (KnuthElement) par.get(k);
            if (el.isAuxiliary()) {
                continue;
            }
            return el instanceof KnuthPenalty && suppressedBreaks.containsKey(el);
        }
        return false;
    }

    private final java.util.IdentityHashMap<Object, Object> emergencyElement
            = new java.util.IdentityHashMap<Object, Object>();

    private final boolean emergencyBreakEnabled = settings.emergencyBreak();

    /** Whether this block is set directly in a table cell ({@link #inTableCell}), where
     *  the overrun tolerance is {@link #cellOverrunTolerance(int)} rather than
     *  {@link #overrunTolerance}.  @since 17.2.0 */
    private final boolean inCell;
    /** Whether this block is set in a header or footer (an fo:static-content), where a
     *  trailing tab which reaches no stop takes no line.  @since 17.2.0 */
    private final boolean inStaticContent;

    /** How far past the measure {@code available} a word of this block may run before
     *  it is broken, in millipoints.  @since 17.2.0 */
    private int overrunTolerance(int available) {
        /* The cell's own tolerance stands on its own since 17.2.0: the two defaults are the
         * same twip now (the body's was an inch, and the min kept a raised cell tolerance
         * from exceeding it), so capping the cell by the general value would only stop that
         * property being raised.  @since 17.2.0 (CR-001 batch 47 item 4) */
        return inCell ? cellOverrunTolerance(available) : overrunTolerance;
    }

    /**
     * Word's last resort: a word too long for a line of its own is broken inside it, at
     * the last character that fits.
     *
     * <p>UAX #14, which FOP's text managers follow, offers no break inside a word like
     * {@code KONS_ADATOK_SZERZODO_ADATAI_TERM_SZEMELY_LAKCIM_VAROS} or a rule of
     * underscores, so FOP paints the whole of it however narrow the measure: over the
     * three corpora 1959 lines are painted outside their page in 73 documents, against
     * Word's 207 in 20 - and Word's are deliberate overhangs.
     *
     * <p>Measured on a corpus golden, an insurance template of long placeholder tokens
     * in a 279pt cell.  Word does two things.  It moves such a word to a line of its
     * own: the line before {@code tartóval):ELSEENDIFIF_csak_az_uzembentartoval_THEN(...}
     * ends at 453.9 with 85pt of the measure unused, so Word did not fill it with the
     * word's head.  Then it breaks the word wherever the measure falls, mid-token and
     * with no hyphen: {@code ...THEN(m} to the cell edge at 538.8, then
     * {@code egegyezik...} on the next line.
     *
     * <p>So a word wider than the whole measure is split here into one glyph mapping per
     * character, with a zero-width break between each pair, and
     * {@link LineBreakingAlgorithm#emergencyUsable} lets the greedy loop take one only
     * once the line holds nothing but that word - which is what makes Word's first step
     * happen, since until then the ordinary break before the word is the last one that
     * fitted.  Kerning and glyph positioning within the word are lost, which is the
     * price of breaking it; the word's total width is preserved exactly.
     *
     * <p>Only a word which is one FOP glyph mapping is split (one run of text in one
     * font, which is what these tokens are), and only where that mapping carries no
     * substituted glyph sequence: a word FOP shaped through GSUB/GPOS cannot be rebuilt
     * from its characters, so it is left whole.
     *
     * @param available the widest line the paragraph can have, in millipoints: a word
     *        narrower than that can always be moved to a line where it fits
     * @since 17.1.0
     */
    private void emergencyBreaks(Paragraph par, int available) {
        if (!emergencyBreakEnabled || available <= 0 || par == null) {
            return;
        }
        int overrunTolerance = overrunTolerance(available);
        // collect first, split from the end backwards, so the indices stay valid
        List<int[]> overlong = null;
        for (int i = 0; i < par.size(); i++) {
            KnuthElement e = (KnuthElement) par.get(i);
            if (!e.isBox() || e.isAuxiliary()) {
                continue;
            }
            int width = e.getWidth();
            int boxes = 1;
            int j = i + 1;
            boolean afterSuppressed = false;
            for (; j < par.size(); j++) {
                KnuthElement el = (KnuthElement) par.get(j);
                if (el.isBox() && !el.isAuxiliary()) {
                    width += el.getWidth();
                    boxes++;
                    afterSuppressed = false;
                    continue;
                }
                if (el.isAuxiliary()) {
                    continue;
                }
                if (el instanceof KnuthPenalty) {
                    if (suppressedBreaks.containsKey(el)) {
                        afterSuppressed = true;
                        continue;
                    }
                    if (((KnuthPenalty) el).getPenalty() >= KnuthElement.INFINITE) {
                        continue;
                    }
                    break;  // a real break opportunity: the word ends here
                }
                if (el.isGlue() && (afterSuppressed || suppressedBreakFollows(par, j))) {
                    // the join FOP builds around a break opportunity inside a word -
                    // "box, penalty(INF), glue, penalty, glue, box" - whose penalty
                    // suppressWordBreaks has made infinite: neither glue is a break
                    // (Knuth: a glue breaks only after a box), so the word goes on past
                    // it.  Until 17.2.0 the word ended at the first glue, and the two
                    // halves were split as two words whose emergency breaks blocked each
                    // other (emergencyUsable): a 20pt cell set "s\Chicos" on one line,
                    // overflowing, where every other line held two characters.  Only
                    // that join: FOP puts the same "penalty(INF), glue" before other
                    // things - a non-breaking space in justified text, a list label's
                    // tab - and running on past those merged "1." with what followed it
                    // and broke it "1" / "." (measured, a corpus checklist 1.000 -> 0.906).
                    width += el.getWidth();
                    continue;
                }
                break;      // a glue: the word ends here
            }
            if (boxes >= 1 && width > available + overrunTolerance) {
                if (overlong == null) {
                    overlong = new ArrayList<int[]>();
                }
                overlong.add(new int[] { i, j });
                if (log.isDebugEnabled()) {
                    log.debug("emergency break: a word of " + width / 1000.0 + "pt exceeds a"
                            + (inCell ? " cell" : "") + " measure of " + available / 1000.0
                            + "pt by " + (width - available) / 1000.0 + "pt (tolerance "
                            + overrunTolerance / 1000.0 + "pt)");
                }
            }
            i = j - 1;
        }
        if (overlong == null) {
            return;
        }
        for (int k = overlong.size() - 1; k >= 0; k--) {
            int[] range = overlong.get(k);
            // one token for the whole word, however many boxes it is: emergencyUsable
            // asks whether the line holds anything but this word, and the head of a
            // punctuation-led token or of a URL is part of it
            Object word = new Object();
            for (int idx = range[1] - 1; idx >= range[0]; idx--) {
                KnuthElement el = (KnuthElement) par.get(idx);
                if (!el.isBox() || el.isAuxiliary()) {
                    continue;
                }
                if (!splitForEmergencyBreak(par, idx, word)) {
                    // not splittable (a shaped mapping, say), but still this word's:
                    // a break inside one of its neighbours must not be blocked by it
                    emergencyElement.put(el, word);
                }
            }
        }
    }

    /**
     * How far past the measure a word may run before it is broken rather than left to
     * overflow: <b>one twip</b> (millipoints), Word's own unit of layout - the same as a
     * table cell's, because the golden says the body is no different.
     *
     * <p>Measured on the {@code break-longword} golden, Liberation Serif 12pt on a 481.0pt
     * body measure with every digit and underscore 6.0pt wide:</p>
     * <ul>
     * <li>a token of 88 characters (528.0pt, <b>48.0pt over</b>): Word sets 80 of them to
     *     x=537.14, the measure exactly, and puts {@code 01234567} on the next line;
     * <li>a token of 81 characters (486.0pt, <b>5.0pt over</b>): Word sets 80 and puts the
     *     last one on the next line;
     * <li>a token of 80 characters (480.0pt, 1.0pt <em>inside</em>): one line, unbroken;
     * <li>a token which fits the measure but not the space left on its line: Word moves it
     *     down whole, and does not break it.
     * </ul>
     * <p>So Word's rule is the measure itself, in the body as in a cell: 5.0pt over is
     * broken and 1.0pt inside is not. The golden's same eleven cases in a 205pt cell come
     * out identically, and docx4j already matched those - because a cell's tolerance was
     * already a twip.</p>
     *
     * <p><b>What the inch was for, and what it cost.</b> Until 17.2.0 the body's tolerance
     * was 72pt, on the argument that a word which does not fit is often a measure
     * <em>we</em> got wrong rather than a word Word breaks - "BALES" by 2.6pt in a
     * certificate whose columns Word autofits a fraction wider than ours, "CANTIDAD" by
     * 20pt in a cell whose text Word turns on its side - while the words Word does break
     * overflow by 78 to 345pt. That argument is refuted for the body by the golden above:
     * Word breaks at 5.0pt over.
     *
     * <p>What remains true is the risk the inch named, and the corpora show both sides of
     * it. Over the three (191, 156 and 102 documents), measured together with this batch's
     * page-number placeholder ({@link WordPageNumberCitationLayoutManager}, which accounts
     * for the largest single gain), the means rise 0.9258 -&gt; 0.9261, 0.9085 -&gt; 0.9089
     * and 0.9438 -&gt; 0.9443, with 111 more lines matched and no corpus losing any;
     * documents of long placeholder tokens in narrow columns gain 7 to 14 lines each and
     * one reaches 1.0000. Against that, three documents lose 3 to 5 lines apiece
     * and they are all one shape - <b>a rotated or vertical text box</b>, whose measure is
     * ours and not Word's: a {@code reference-orientation="90"} container whose
     * inline-progression-dimension is 15pt for a word of 36pt, and a VML text box of
     * {@code layout-flow:vertical} which docx4j does not rotate at all, so its measure is
     * the box's 16.5pt width less indent and padding - 2.1pt - where Word lays the text
     * along the box's 93.75pt height. A twip in a measure that holds no character shatters
     * the word one character to a line (measured: 4 overflowing pieces before, 15 lines
     * after) where the inch let it overhang. Two documents also gain a page, one on
     * URL break opportunities and one on a substitute face 1.5% wider than the document's
     * own; neither is this tolerance.
     *
     * <p>Measured at 8pt as well - the value this Javadoc used to recommend - and it is
     * worse on every count: it fails the golden's 5.0pt case, and the narrow-measure
     * documents fall further still.
     *
     * <p>The tolerance is a property ({@code docx4j.convert.out.fo.wordLayout.emergencyBreakTolerance}),
     * so the inch can be restored without a build while those documents' measures are
     * chased.</p>
     *
     * @since 17.1.0; the twip 17.2.0 (CR-001 batch 47 item 4)
     */
    private final int overrunTolerance
            = (int) Math.round(1000 * settings.emergencyBreakTolerance(0.05));

    /**
     * The tolerance inside a table cell: one twip, Word's own unit of layout (millipoints).
     *
     * <p>Word breaks a word as soon as it exceeds the cell it is in, at whatever character
     * reaches the edge.  Measured on a 311-page corpus document, a table whose
     * {@code w:tblGrid} Word wrote and kept on re-save - 431 twips then 35 columns of 255 -
     * has Word drawing the first column 21.55pt wide, about 10.75pt of content after the
     * cell margins, and breaking {@code Categorizador/Período} down 15 lines as
     * {@code C / at / e / g / or / iz / a / d / or / / / P / er / ío / d / o}, a 157pt
     * header row; the 255-twip columns hold one character a line.  With the inch above
     * we set the word on one line running to x=559 where the text column ends at 540 -
     * text printed past the right margin - and every one of those columns' words across
     * its neighbours: that table took Word 8.4 pages and us 2.3.  The inch is what stopped
     * the general rule firing there - the word overruns our 39pt column by 31pt.  The 8pt
     * experiment above read a narrow column that shatters a word as a column we had sized
     * wrongly; Word's kept grid says the column is that narrow, and the word is shattered
     * in Word too.
     *
     * <p>The tolerance is not zero because the cell's measure and the word's width come
     * from two measurements of ours (the column sizer's and FOP's), which disagree in the
     * last place: the {@code table-autofit} probe's 23.976pt column holds a 23.988pt word
     * Word set on one line.  A twip is below what Word can lay out.
     *
     * <p><b>What the scope is now.</b>  Body text takes the same twip from 17.2.0 (the
     * golden refuted the inch, see {@link #overrunTolerance}), so the cell rule differs
     * from the general one only where one of the two properties is set.  The walk that
     * finds the cell goes through {@code fo:block-container}s from 17.2.0 as well
     * ({@link #inTableCell}), so a rotated cell (w:textDirection) and a positioned frame
     * or text box inside a cell are in the cell; a footnote, a flow and a static content
     * still end the walk.  What the old inch was protecting against is still real and
     * still unanswered: a measure <em>we</em> got wrong - a rotated cell's measure is a
     * row height docx4j only bounds, and a vertical text box's is the box's width - and
     * the corpora show it as a word shattered one character to a line.
     *
     * <p>All of this is a workaround for FOP, which offers no break inside a word at all
     * (§10 of word-layout-rules.md); when FOP grows an emergency break of its own the
     * per-character split goes, and this tolerance with it.
     *
     * <p>Read per paragraph rather than once, so that the properties can be set and
     * cleared within one JVM (as {@link #emergencyBreakEnabled} is).
     *
     * @param available the cell's measure, in millipoints
     * @since 17.2.0
     */
    private int cellOverrunTolerance(int available) {
        int absolute = (int) Math.round(1000 * settings.cellEmergencyBreakTolerance(0.05));
        int relative = (int) Math.round(available * settings.cellEmergencyBreakToleranceRatio(0));
        return Math.max(absolute, relative);
    }

    /**
     * Whether this block is set in a table cell: an {@code fo:table-cell} is reached
     * walking up through blocks and {@code fo:block-container}s, with no footnote, flow or
     * static content in between.  Such a block's measure is the cell's content width (a
     * block-container in a cell is written with the cell's own width), which is the measure
     * Word breaks a word against.
     *
     * <p>A {@code block-container} used to end the walk, on the reading that its measure
     * is its own rather than the cell's.  That is not so for the containers docx4j writes
     * inside a cell - a rotated cell's, a frame's, a text box's wrapper - each of which is
     * given the cell's content width or a width taken from it, so the block inside is
     * broken against the same measure as a bare cell block would be, and Word breaks a
     * word as soon as it exceeds the cell it is in.
     *
     * <p>With both tolerances at a twip (17.2.0) the classification decides nothing on its
     * own: {@link #overrunTolerance} reaches the same number either way.  It decides what
     * a set {@code docx4j.convert.out.fo.wordLayout.emergencyBreakTolerance} or
     * {@code docx4j.convert.out.fo.wordLayout.cellEmergencyBreakTolerance} applies to, which is why
     * it is worth having right ({@code CellEmergencyBreakTest}).
     *
     * @since 17.2.0; the container walk CR-001 batch 47 item 4c
     */
    static boolean inTableCell(org.apache.fop.fo.FONode block) {
        for (org.apache.fop.fo.FONode n = block == null ? null : block.getParent();
                n != null; n = n.getParent()) {
            if (n instanceof org.apache.fop.fo.flow.table.TableCell) {
                return true;
            }
            // a BlockContainer was in this list until 17.2.0; see the javadoc
            if (n instanceof org.apache.fop.fo.flow.Footnote
                    || n instanceof org.apache.fop.fo.flow.FootnoteBody
                    || n instanceof org.apache.fop.fo.pagination.Flow
                    || n instanceof org.apache.fop.fo.pagination.StaticContent) {
                return false;
            }
        }
        return false;
    }

    /** Whether this block is set in an fo:static-content (a header or footer). */
    static boolean inStaticContent(org.apache.fop.fo.FONode block) {
        for (org.apache.fop.fo.FONode n = block == null ? null : block.getParent();
                n != null; n = n.getParent()) {
            if (n instanceof org.apache.fop.fo.pagination.StaticContent) {
                return true;
            }
            if (n instanceof org.apache.fop.fo.pagination.Flow) {
                return false;
            }
        }
        return false;
    }

    /** Split the single-mapping box at this index into one mapping and one box per
     *  character, with a zero-width break between each pair, all of them marked as
     *  belonging to {@code word}.
     *
     *  @return whether the box was split; a caller which is told no must mark the box
     *          as this word's itself, so that a break inside a neighbouring box of the
     *          same word is not refused because this one is on the line */
    private boolean splitForEmergencyBreak(Paragraph par, int index, Object word) {
        KnuthElement box = (KnuthElement) par.get(index);
        if (!(box instanceof KnuthInlineBox)) {
            return false;
        }
        org.apache.fop.layoutmgr.inline.TextLayoutManager tlm = tlmOf(box);
        if (tlm == null) {
            return false;
        }
        org.apache.fop.fonts.GlyphMapping m = mappingOf(box);
        if (m == null || m.isSpace || m.font == null) {
            return false;
        }
        // a mapping which carries a substituted glyph sequence (a complex script, or an
        // OpenType feature FOP applied) cannot be rebuilt from the characters, and the
        // fragments would render unshaped: leave such a word whole
        if (m.mapping != null || m.associations != null || m.gposAdjustments != null) {
            return false;
        }
        List<org.apache.fop.fonts.GlyphMapping> mappings = tlm.getMappings();
        int idx = leafPositionOf(box).getLeafPos();
        if (idx < 0 || idx >= mappings.size() || mappings.get(idx) != m) {
            return false;
        }
        org.apache.fop.fo.FOText foText = tlm.getFOText();
        if (m.endIndex > foText.length() || m.endIndex - m.startIndex < 2) {
            return false;
        }
        MinOptMax letterSpace = tlm.getLetterSpaceIPD();
        if (letterSpace == null || !letterSpace.isStiff()) {
            return false;
        } // adjustable: leave it to FOP
        // the breaks need a position which stands for no glyph, or the text manager
        // would build the word's areas twice
        // the break's position must stand for no glyph, and must be wrapped exactly as
        // the word's own is, or the inline managers it sits in are given a position they
        // did not make and never build their area
        Position aux = auxiliaryLike(box.getPosition(), tlm);
        if (aux == null) {
            return false;
        }

        // one fragment per character (a surrogate pair stays whole)
        List<int[]> spans = new ArrayList<int[]>();
        for (int c = m.startIndex; c < m.endIndex; ) {
            int cp = Character.codePointAt(foText, c);
            int next = c + Character.charCount(cp);
            if (next > m.endIndex) {
                next = m.endIndex;
            }
            spans.add(new int[] { c, next, cp });
            c = next;
        }
        if (spans.size() < 2) {
            return false;
        }

        List<org.apache.fop.fonts.GlyphMapping> fragments
                = new ArrayList<org.apache.fop.fonts.GlyphMapping>(spans.size());
        int total = 0;
        for (int j = 0; j < spans.size(); j++) {
            boolean last = j == spans.size() - 1;
            int letterSpaceCount = last ? 0 : 1;
            int ipd = m.font.getCharWidth(spans.get(j)[2]) + letterSpaceCount * letterSpace.getOpt();
            total += ipd;
            fragments.add(new org.apache.fop.fonts.GlyphMapping(spans.get(j)[0], spans.get(j)[1], 0,
                    letterSpaceCount, MinOptMax.getInstance(ipd), false, false,
                    last && m.breakOppAfter, m.font, m.level, null));
        }
        // the word's own width wins: kerning, which the per-character widths do not
        // carry, goes onto the last fragment so the whole word measures as it did
        int residual = m.areaIPD.getOpt() - total;
        org.apache.fop.fonts.GlyphMapping tail = fragments.get(fragments.size() - 1);
        tail.areaIPD = MinOptMax.getInstance(Math.max(0, tail.areaIPD.getOpt() + residual));

        // the mappings after this one move along by as many places as we have added
        int added = fragments.size() - 1;
        mappings.set(idx, fragments.get(0));
        mappings.addAll(idx + 1, fragments.subList(1, fragments.size()));
        shiftLeafPositions(tlm, idx, added);

        // and the box becomes a box per fragment, with a break between each pair
        List<KnuthElement> replacement = new ArrayList<KnuthElement>(2 * fragments.size());
        for (int j = 0; j < fragments.size(); j++) {
            if (j > 0) {
                KnuthPenalty p = new KnuthPenalty(0, 0, false, aux, true);
                emergencyElement.put(p, word);
                replacement.add(p);
            }
            KnuthInlineBox b = new KnuthInlineBox(fragments.get(j).areaIPD.getOpt(),
                    ((KnuthInlineBox) box).getAlignmentContext(),
                    positionLike(box.getPosition(), tlm, idx + j), false);
            emergencyElement.put(b, word);
            replacement.add(b);
        }
        par.remove(index);
        par.addAll(index, replacement);
        if (log.isDebugEnabled()) {
            log.debug("emergency break: " + foText.subSequence(m.startIndex, m.endIndex)
                    + " (" + m.areaIPD.getOpt() + "mpt) split into " + fragments.size() + " characters");
        }
        return true;
    }

    /** Every LeafPosition of this text manager at or past {@code from} moves on by
     *  {@code by} places; the paragraphs are the only place they live at this stage. */
    private void shiftLeafPositions(org.apache.fop.layoutmgr.inline.TextLayoutManager tlm,
            int from, int by) {
        if (by == 0) {
            return;
        }
        // one position object can be shared by several elements: shift each once
        java.util.IdentityHashMap<Position, Position> done = new java.util.IdentityHashMap<Position, Position>();
        for (KnuthSequence seq : knuthParagraphs) {
            for (int i = 0; i < seq.size(); i++) {
                Object o = seq.get(i);
                if (!(o instanceof KnuthElement)) {
                    continue;
                }
                LeafPosition leaf = leafPositionOf((KnuthElement) o);
                if (leaf == null || leaf.getLM() != tlm) {
                    continue;
                }
                if (done.put(leaf, leaf) != null) {
                    continue;
                }
                int pos = leaf.getLeafPos();
                if (pos > from) {
                    leaf.setLeafPos(pos + by);
                }
            }
        }
    }

    /**
     * A position shaped like this one but standing for no glyph: the same chain of
     * wrappers, ending in the text manager's own "no mapping" leaf.  A paragraph which
     * is nothing but the over-long word holds no auxiliary element to borrow a position
     * from, and a bare leaf would be routed past the inline managers the word sits in.
     *
     * @since 17.1.0
     */
    private static Position auxiliaryLike(Position p, org.apache.fop.layoutmgr.inline.TextLayoutManager tlm) {
        return positionLike(p, tlm, -1);
    }

    /**
     * A position shaped like this one - the same chain of wrappers - but naming a
     * different glyph mapping of the same text manager, or the manager's "no mapping"
     * leaf for {@code leafPos} -1.  The wrappers matter: an unwrapped leaf is routed
     * straight to the text manager, past the inline managers the text sits in, and they
     * then have no area for it to be added to.
     *
     * @since 17.1.0
     */
    private static Position positionLike(Position p, org.apache.fop.layoutmgr.inline.TextLayoutManager tlm,
            int leafPos) {
        if (p instanceof LeafPosition) {
            LeafPosition leaf = new LeafPosition(tlm, leafPos);
            leaf.setIndex(p.getIndex());
            return leaf;
        }
        if (p instanceof NonLeafPosition) {
            Position inner = positionLike(p.getPosition(), tlm, leafPos);
            if (inner == null) {
                return null;
            }
            NonLeafPosition wrapper = new NonLeafPosition(p.getLM(), inner);
            wrapper.setIndex(p.getIndex());
            return wrapper;
        }
        return null;
    }

    /** The LeafPosition an element's position chain ends in, or null. */
    private static LeafPosition leafPositionOf(KnuthElement e) {
        for (Position p = e.getPosition(); p != null; p = p.getPosition()) {
            if (p instanceof LeafPosition) {
                return (LeafPosition) p;
            }
        }
        return null;
    }

    /** The position of a nearby auxiliary element (one which stands for no glyph). */
    private static Position auxiliaryPosition(KnuthSequence seq, int from) {
        for (int d = 1; d < seq.size(); d++) {
            for (int j : new int[] {from - d, from + d}) {
                if (j < 0 || j >= seq.size()) {
                    continue;
                }
                Object o = seq.get(j);
                if (!(o instanceof KnuthElement)) {
                    continue;
                }
                if (mappingOf(o) != null) {
                    continue;
                } // stands for a glyph
                Position pos = ((KnuthElement) o).getPosition();
                if (pos != null) {
                    return pos;
                }
            }
        }
        return null;
    }

    /**
     * Find the optimal linebreaks for a paragraph
     * @param alignment alignment of the paragraph
     * @param currPar the Paragraph for which the linebreaks are found
     * @param isLastPar flag indicating whether currPar is the last paragraph
     * @return the line layout possibilities for the paragraph
     */
    /**
     * Word breaks lines greedily (first fit): a line takes every word that fits and
     * breaks at the last opportunity before the first word that does not, with
     * no look-ahead.  FOP's total-fit (Knuth-Plass) search chooses breaks that
     * minimise badness over the whole paragraph, so even ragged-right text
     * breaks differently in about a quarter of lines with identical fonts and
     * widths (CR-001 harness, break-ragged).  This runs the greedy algorithm
     * once; there is no threshold ladder because it always succeeds.
     * Where the block asks for hyphenation, FOP's findHyphenationPoints inserts a
     * flagged penalty at every point the patterns allow, and the greedy loop takes
     * one only where Word would: the gap the next whole word would leave is bigger
     * than w:hyphenationZone, and w:consecutiveHyphenLimit is not reached
     * (see considerLegalBreak / hyphenationAllowed).
     */
    @Override
    LineLayoutPossibilities findOptimalBreakingPoints(int alignment, Paragraph currPar,
                                                              boolean isLastPar) {
        lineLayouts = new LineLayoutPossibilities();
        /* The paragraph's own text-align-last, which is the block's except where the
         * paragraph ends at a soft return: Word justifies that line (§4.2), and
         * collectInlineKnuthElements has marked it.  @since 17.1.0 */
        LineBreakingAlgorithm alg = new WordLineBreakingAlgorithm(alignment,
                                        textAlignment, currPar.textAlignmentLast(),
                                        textIndent.getValue(this), currPar.lineFiller.getOpt(),
                                        lineHeight.getValue(this), lead, follow,
                                        (knuthParagraphs.indexOf(currPar) == 0),
                                        hyphenationLadderCount.getEnum() == EN_NO_LIMIT
                                            ? 0 : hyphenationLadderCount.getValue(),
                                        this);
        alg.setConstantLineWidth(ipd);
        boolean canWrap = (wrapOption != EN_NO_WRAP);
        boolean canHyphenate = (canWrap && hyphenationProperties.hyphenate.getEnum() == EN_TRUE);
        if (canHyphenate && !hyphenationPerformed) {
            hyphenationPerformed = isLastPar;
            findHyphenationPoints(currPar);
        }
        // the opportunity FOP lacks at the seam of two runs (see seamBreaks), before the
        // emergency break, which must not see a seam it could break at as an over-long word
        if (canWrap) {
            seamBreaks(currPar);
        }
        // Word's last resort, after every legal break the document itself offers:
        // a word too long for any line is broken inside it (see emergencyBreaks)
        if (canWrap) {
            emergencyBreaks(currPar, ipd);
        }
        int allowedBreaks = !canWrap ? BreakingAlgorithm.ONLY_FORCED_BREAKS
                : (canHyphenate ? BreakingAlgorithm.ALL_BREAKS : BreakingAlgorithm.NO_FLAGGED_PENALTIES);
        alg.findBreakingPoints(currPar, 1, true, allowedBreaks);
        return lineLayouts;
    }

    /**
     * Word's widow control counts a paragraph's lines across its soft returns; FOP's counts
     * each run of lines between them on its own.  BrWriter writes a {@code w:br} as a
     * nested {@code fo:block}, which splits the block's content into separate Knuth
     * paragraphs here, so a break between any two of them was always allowed and widows
     * and orphans were applied to each part alone: a paragraph of a line of text and a
     * trailing {@code w:br} - two lines to Word, which widow control will not divide - was
     * set one line on each page.  Measured on corpus document 2065, whose article entries
     * end in a {@code w:br}: Word moves such an entry to the next page whole, where docx4j
     * left its text at the foot of the page, and the next page started 16pt high.
     *
     * <p>Where every block-level part of this block is a soft return's block (an
     * {@code fo:block} with {@code linefeed-treatment="preserve"} and no break), the lines
     * of the inline parts are counted together: the result holds, for each Knuth paragraph,
     * the number of lines before it, and the total last.  Null where widow control is off
     * (widows and orphans of 1), where the block has no soft return, or where a part is
     * some other block, which keeps FOP's counting.</p>
     *
     * @since 17.3.1
     */
    @Override
    int[] linesAcrossSoftReturns() {
        if (!settings.widowsAcrossSoftReturns()) {
            return null;
        }
        if (relaxedForFloat() || knuthParagraphs.size() < 2) {
            return null;
        }
        if (fobj.getOrphans() <= 1 && fobj.getWidows() <= 1) {
            return null;
        }
        int n = knuthParagraphs.size();
        int[] before = new int[n + 1];
        boolean softReturn = false;
        for (int p = 0; p < n; p++) {
            KnuthSequence seq = knuthParagraphs.get(p);
            before[p + 1] = before[p];
            if (seq.isInlineSequence()) {
                before[p + 1] += lineLayoutsList[p].getChosenLineCount();
            } else if (isSoftReturn(seq, this)) {
                softReturn = true;
            } else {
                return null;
            }
        }
        if (log.isDebugEnabled() && softReturn) {
            log.debug("linesAcrossSoftReturns: " + java.util.Arrays.toString(before));
        }
        return softReturn ? before : null;
    }

    /** Whether this block-level Knuth sequence is a soft return's nested block: the innermost
     *  block on each element's position chain, the line manager's own position passed by.  Its
     *  first reading wraps each element's position in one of its own (postProcessLineBreaks), so
     *  on a second reading - the re-read after a band's edge (fork CR-024) - the chain began with
     *  the paragraph's block and the soft return went uncounted (3955: a list item's body split
     *  2+1 at it).  @since 17.3.1 */
    private static boolean isSoftReturn(KnuthSequence seq, LayoutManager self) {
        boolean found = false;
        for (Object o : seq) {
            // the element reports the fo:inline the block is nested in; the block's own
            // manager is further down its position chain
            // (a TableContentPosition answers itself as its own sub-position, so the walk
            // stops where the chain does not advance)
            // the innermost block on the chain: positions an outer manager wrapped round the
            // element (the line manager's own, or its parents' on a re-read) come first
            org.apache.fop.fo.flow.Block b = null;
            for (Position pos = ((ListElement) o).getPosition(); pos != null; ) {
                LayoutManager lm = pos.getLM();
                if (lm != null && lm != self && lm.getFObj() instanceof org.apache.fop.fo.flow.Block) {
                    b = (org.apache.fop.fo.flow.Block) lm.getFObj();
                }
                Position sub = pos.getPosition();
                pos = sub == pos ? null : sub;
            }
            if (b == null) {
                if (((ListElement) o).isBox()) {
                    return false;
                }
                continue;
            }
            if (b.getLinefeedTreatment() != Constants.EN_PRESERVE || b.getBreakBefore() != Constants.EN_AUTO) {
                return false;
            }
            found = true;
        }
        return found;
    }

    /**
     * Creates the element list in BP direction for the broken lines.
     * @param alignment the currently applicable vertical alignment
     * @param context the layout context
     * @return the newly built element list
     */
    /** Whether widows and orphans are relaxed to 1 for a float FOP is handling: not for an
     *  obstacle's band ({@link #handlingObstacle}).  @since 17.3.3 */
    private boolean relaxedForFloat() {
        return handlingFloat() && !handlingObstacle();
    }

    /**
     * The word as the TeX patterns want to see it: lower case.  FOP's pattern lookup
     * folds case with the pattern file's own class table, which is lossy for words in
     * capitals - measured with fop-hyph's en_GB patterns, APPROPRIATIONS came back as
     * AP-PRO-PRIATIONS (Word breaks it APPROPRI-ATIONS) and DEPARTMENTS gained a
     * spurious DEPARTMEN-TS.  Lowercasing first gives the points the same word in lower
     * case gets.  The result is only used to look the points up, so a mapping that
     * changed the length (Turkish dotted I, German sharp S in some locales) is
     * discarded, leaving the offsets valid.
     *
     * @since 17.1.0
     */
    @Override
    String forPatternLookup(StringBuffer sbChars) {
        String word = sbChars.toString();
        String lower = word.toLowerCase(java.util.Locale.ROOT);
        return lower.length() == word.length() ? lower : word;
    }

    // --------- Property Resolution related functions --------- //

}
