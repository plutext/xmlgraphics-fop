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
 * Apache FOP 2.11: hook float-offset, the float placed at its target top below the flow's height, and never in the
 * flow (fop/CR-023); and a float goes in the flow only when its content is as wide as the width available to it
 * (FOP-3353, a regression of FOP-3331 on Apache main); and hook float-table-split: a float's table is placed by
 * ranges of its element list, one per page, as a word processor splits a floating table across pages (fop/CR-034).
 * See README.md, "Changes from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.layoutmgr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

import org.apache.fop.area.Area;
import org.apache.fop.area.Block;
import org.apache.fop.area.BlockParent;
import org.apache.fop.area.BlockViewport;
import org.apache.fop.area.LineArea;
import org.apache.fop.area.SideFloat;
import org.apache.fop.area.Trait;
import org.apache.fop.fo.Constants;
import org.apache.fop.fo.flow.Float;
import org.apache.fop.fo.properties.CommonBorderPaddingBackground;
import org.apache.fop.layoutmgr.inline.FloatLayoutManager;
import org.apache.fop.layoutmgr.inline.KnuthInlineBox;
import org.apache.fop.layoutmgr.table.TableLayoutManager;

public class FloatContentLayoutManager extends SpacedBorderedPaddedBlockLayoutManager {

    private SideFloat floatContentArea;
    private int side;
    private int yOffset;
    private final int floatOffset; // fop/CR-023: fox:float-offset
    private final boolean floatOffsetGiven; // fop/CR-023: fox:float-offset written, even as 0
    private int targetTop = -1; // fop/CR-023: the float's top in the page, set by the page breaker before the areas
    private int yShift; // fop/CR-023: how far below the flow's height the float was placed
    /** fop/CR-034: the float content's element list, space-resolved, owned here so that it survives a restart. */
    private List<ListElement> contentElements;
    /** fop/CR-034: the index in the content's list where the part still to be placed begins. */
    private int rangeStart;

    /**
     * {Add info}
     *
     * @param node the {@link Float} associated with this instance
     */
    public FloatContentLayoutManager(Float node) {
        super(node);
        generatesReferenceArea = true;
        side = node.getFloat();
        floatOffset = node.getFloatOffset() == null ? 0 : node.getFloatOffset().getValue();
        floatOffsetGiven = node.hasFloatOffset();
    }

    @Override
    public Keep getKeepTogether() {
        return getParentKeepTogether();
    }

    @Override
    public Keep getKeepWithNext() {
        return Keep.KEEP_AUTO;
    }

    @Override
    public Keep getKeepWithPrevious() {
        return Keep.KEEP_ALWAYS;
    }

    @Override
    public void addAreas(PositionIterator parentIter, LayoutContext layoutContext) {
        floatContentArea = new SideFloat();
        AreaAdditionUtil.addAreas(this, parentIter, layoutContext);
        flush();
    }

    @Override
    public void addChildArea(Area childArea) {
        floatContentArea.addChildArea(childArea);
        floatContentArea.setBPD(childArea.getAllocBPD());
        int effectiveContentIPD = getContentAreaIPD(childLMs, childArea);
        int contentIPD = childArea.getIPD();
        int xOffset = childArea.getBorderAndPaddingWidthStart();
        // The float goes in the flow when its content is as wide as the space it could take (FOP-3331): the width
        // available to the float, not its own area's, which for a block-container child is the container's width,
        // so every such float went in the flow (FOP-3353). A float with fox:float-offset keeps its side-float
        // placement whatever its width: in the flow it could not be set below its anchor's top as the hook
        // promises, and a word processor places a column-wide floating table by its offset (fop/CR-023).
        int availableIPD = getContentAreaIPD();
        if (!floatOffsetGiven && availableIPD > 0 && getEffectiveAllocIPD(childArea) >= availableIPD) {
            addAreaInFlow(contentIPD, xOffset);
            return;
        }
        floatContentArea.setIPD(effectiveContentIPD);
        childArea.activateEffectiveIPD();
        if (side == Constants.EN_END || side == Constants.EN_RIGHT) {
            xOffset += getStartIndent();
            floatContentArea.setXOffset(xOffset + contentIPD - effectiveContentIPD);
        } else if (side == Constants.EN_START || side == Constants.EN_LEFT) {
            floatContentArea.setXOffset(xOffset);
        }
        LayoutManager lm = parentLayoutManager;
        while (!lm.getGeneratesReferenceArea()) {
            lm = lm.getParent();
        }
        if ((side == Constants.EN_START || side == Constants.EN_LEFT)
                && !(lm instanceof FlowLayoutManager)) {
            floatContentArea.setXOffset(xOffset - effectiveContentIPD);
        }
        yOffset = lm.getParentArea(floatContentArea).getBPD();
        if (!(lm instanceof FlowLayoutManager)) {
            yOffset += lm.getPSLM().getCurrentPV().getCurrentFlow().getBPD();
        }
        // hook float-offset (fop/CR-023): the float begins at its target top, below the flow's height here
        int lowest = lm.getContentAreaBPD() - floatContentArea.getAllocBPD();
        int top = Math.min(targetTop, lowest);
        yShift = (top > yOffset) ? top - yOffset : 0;
        if (yShift > 0) {
            floatContentArea.setYOffset(yShift);
        }
        lm.addChildArea(floatContentArea);
        if (side == Constants.EN_END || side == Constants.EN_RIGHT) {
            lm.getPSLM().setEndIntrusionAdjustment(effectiveContentIPD);
        } else if (side == Constants.EN_START || side == Constants.EN_LEFT) {
            lm.getPSLM().setStartIntrusionAdjustment(effectiveContentIPD);
        }
    }

    /**
     * Adds the float content area in the normal flow, so the content that follows goes below it,
     * at full width, no intrusion adjustment being needed.
     *
     * @param contentIPD The IPD available in the containing block.
     * @param xOffset    The x-offset of the content, its start border and padding.
     */
    private void addAreaInFlow(int contentIPD, int xOffset) {
        floatContentArea.setPositioning(Block.STACK);
        floatContentArea.addTrait(Trait.IS_REFERENCE_AREA, Boolean.FALSE);
        floatContentArea.setIPD(contentIPD);
        floatContentArea.setXOffset(xOffset);
        LayoutManager lm = parentLayoutManager;
        while (!lm.getGeneratesReferenceArea()) {
            lm = lm.getParent();
        }
        // The float takes its own space in the flow, so the space already used on the page, that
        // PageBreaker.getOffsetDueToFloat() computes from this offset, ends below the float.
        yOffset = lm.getParentArea(floatContentArea).getBPD() + floatContentArea.getAllocBPD();
        if (!(lm instanceof FlowLayoutManager)) {
            // The closest reference area may be a block-container, see CH-1104.
            yOffset += lm.getPSLM().getCurrentPV().getCurrentFlow().getBPD();
        }
        lm.addChildArea(floatContentArea);
    }

    /**
     * Computes the IPD that the content of the float occupies, borders and padding included.
     * <p>
     * {@link Area#getEffectiveIPD()} returns the content IPD of the widest descendant of an
     * area, so it misses the borders and the padding of that descendant and of its ancestors:
     * the float looks narrower than it is. This method walks the widest of the blocks holding
     * the content and adds them.
     * </p>
     *
     * @param area An area holding float content.
     * @return The allocation IPD of the given area, computed from its widest block descendant.
     */
    private static int getEffectiveAllocIPD(Area area) {
        int ipd = 0;
        int lines = 0;
        if (area instanceof BlockParent && !(area instanceof BlockViewport)
                && !((BlockParent) area).isEmpty()) {
            for (Object child : ((BlockParent) area).getChildAreas()) {
                if (child instanceof Block) {
                    ipd = Math.max(ipd, getEffectiveAllocIPD((Area) child));
                } else if (child instanceof LineArea) {
                    lines++;
                }
            }
        }
        if (ipd == 0) {
            // Content broken on several lines asked for more width than it got, so its block
            // fills the containing block, as the shrink-to-fit width of CSS does.
            ipd = lines > 1 ? area.getIPD() : area.getEffectiveIPD();
        }
        return area.getBorderAndPaddingWidthStart() + ipd + area.getBorderAndPaddingWidthEnd();
    }

    private int getContentAreaIPD(List<LayoutManager> childLMs, Area childArea) {
        int ipd = getContentAreaIPD(childLMs);
        if (ipd == 0) {
            return childArea.getEffectiveAllocIPD();
        }
        return ipd;
    }

    private int getContentAreaIPD(List<LayoutManager> childLMs) {
        int ipd = 0;
        for (LayoutManager childLM : childLMs) {
            if (childLM instanceof TableLayoutManager) {
                ipd += childLM.getContentAreaIPD();
            } else if (childLM.getContentAreaBPD() != -1) {
                ipd += getContentAreaIPD(childLM.getChildLMs());
            }
        }
        return ipd;
    }

    /**
     * {Add info}
     *
     * @param elemenList
     * @param startIndex
     * @param endIndex
     * @return
     */
    public static List<FloatContentLayoutManager> checkForFloats(List<ListElement> elemenList,
            int startIndex, int endIndex) {
        ListIterator<ListElement> iter = elemenList.listIterator(startIndex);
        List<FloatContentLayoutManager> floats = new ArrayList<FloatContentLayoutManager>();
        while (iter.nextIndex() <= endIndex) {
            ListElement element = iter.next();
            if (element instanceof KnuthInlineBox && ((KnuthInlineBox) element).isFloatAnchor()) {
                floats.add(((KnuthInlineBox) element).getFloatContentLM());
            } else if (element instanceof KnuthBlockBox && ((KnuthBlockBox) element).hasFloatAnchors()) {
                floats.addAll(((KnuthBlockBox) element).getFloatContentLMs());
            }
        }
        if (floats.isEmpty()) {
            return Collections.emptyList();
        } else {
            return floats;
        }
    }

    @Override
    protected CommonBorderPaddingBackground getCommonBorderPaddingBackground() {
        return null;
    }

    /**
     * {Add info}
     *
     * @param layoutContext
     */
    public void processAreas(LayoutContext layoutContext) {
        if (getParent() instanceof FloatLayoutManager) {
            FloatLayoutManager flm = (FloatLayoutManager) getParent();
            flm.processAreas(layoutContext);
        }
    }

    /**
     * @return the height of the float content area
     */
    public int getFloatHeight() {
        return floatContentArea.getAllocBPD();
    }

    /**
     * @return the y-offset of the float content
     */
    public int getFloatYOffset() {
        return yOffset;
    }

    /**
     * @return fox:float-offset, how far below the top of its anchor block the float begins (fop/CR-023); 0 for the
     * continuation of a split table, which opens its page at the body's top (fop/CR-034)
     */
    public int getFloatOffset() {
        return rangeStart > 0 ? 0 : floatOffset;
    }

    /** @param top the float's top in the page, from the page breaker, before the areas are made (fop/CR-023) */
    public void setTargetTop(int top) {
        targetTop = top;
    }

    /** @return how far below the flow's height at its insertion the float was placed (fop/CR-023) */
    public int getFloatYShift() {
        return yShift;
    }

    // hook float-table-split (fop/CR-034): a float's table placed by ranges of its element list, one per page

    /** @param elements the float content's element list, space-resolved, which this manager then owns */
    public void setContentElements(List<ListElement> elements) {
        contentElements = elements;
    }

    /** @return the float content's element list */
    public List<ListElement> getContentElements() {
        return contentElements;
    }

    /** @return the index in the content's list where the part still to be placed begins; 0 before any split */
    public int getRangeStart() {
        return rangeStart;
    }

    /**
     * Whether the float's content can be split across pages: it is a table, which breaks between its rows, or
     * within a row where the row allows it, as it would in the flow. Any other content (a picture, a text box) is
     * one area and moves whole with its anchor (fop/CR-033).
     * @return true when the content is a table with a break the page breaking may take
     */
    public boolean contentSplittable() {
        if (contentElements == null || tableOf(childLMs) == null) {
            return false;
        }
        for (ListElement e : contentElements) {
            if (e instanceof KnuthPenalty && ((KnuthPenalty) e).getPenalty() < KnuthElement.INFINITE) {
                return true;
            }
        }
        return false;
    }

    private static TableLayoutManager tableOf(List<LayoutManager> lms) {
        if (lms == null) {
            return null;
        }
        for (LayoutManager lm : lms) {
            if (lm instanceof TableLayoutManager) {
                return (TableLayoutManager) lm;
            } else if (lm instanceof BlockStackingLayoutManager) {
                // the table inside the block the float wraps it in, before any area exists
                TableLayoutManager t = tableOf(lm.getChildLMs());
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * The height of the content still to be placed, from the range start to the list's end: the boxes and glues,
     * as the whole content's height was read before any split (fop/CR-023).
     * @return the height in millipoints, 0 before the elements exist
     */
    public int remainingContentHeight() {
        int height = 0;
        if (contentElements != null) {
            for (int i = rangeStart; i < contentElements.size(); i++) {
                ListElement e = contentElements.get(i);
                if (e instanceof KnuthElement && (e.isBox() || e.isGlue())) {
                    height += ((KnuthElement) e).getWidth();
                }
            }
        }
        return height;
    }

    /**
     * Where the part placed on a page with this much room below the float's top ends: the last legal break in the
     * content's list, from the range start, up to which the boxes and glues, and the break penalty's own width
     * (a repeated table header and footer), fit the room, with at least one box of content before it. A break at
     * a penalty is included in the part, as the page breaker includes it; a break at a glue excludes the glue.
     * @param room the height available below the float's top on the page, in millipoints
     * @return the index after the part's last element, or -1 when no row fits
     */
    public int splitIndexFor(int room) {
        if (contentElements == null) {
            return -1;
        }
        int height = 0;
        int best = -1;
        boolean contentSeen = false;
        boolean previousIsBox = false;
        for (int i = rangeStart; i < contentElements.size(); i++) {
            ListElement le = contentElements.get(i);
            if (!(le instanceof KnuthElement)) {
                continue;
            }
            KnuthElement e = (KnuthElement) le;
            if (e.isBox()) {
                height += e.getWidth();
                contentSeen |= e.getWidth() > 0;
                previousIsBox = true;
            } else if (e.isGlue()) {
                if (previousIsBox && contentSeen && height <= room) {
                    best = i;
                }
                height += e.getWidth();
                previousIsBox = false;
            } else {
                KnuthPenalty p = (KnuthPenalty) e;
                if (p.getPenalty() < KnuthElement.INFINITE && !p.isForcedBreak() && contentSeen
                        && height + p.getWidth() <= room) {
                    best = i + 1;
                }
                previousIsBox = false;
            }
            if (height > room) {
                break;
            }
        }
        return best;
    }

    /**
     * Places the part of the content from the range start up to the given index as a side float area at the
     * target top, and keeps the rest for the next page: the content's layout managers add the areas of a range of
     * their own list as they add a page's range of the flow's, a repeated header, a split row and the borders the
     * parts close with included. The float's FO keeps this manager, since the anchor's block is read again on the
     * next page with new layout managers.
     * @param layoutContext the layout context
     * @param to the index after the part's last element, from {@link #splitIndexFor(int)}
     */
    public void processRange(LayoutContext layoutContext, int to) {
        addAreas(new KnuthPossPosIter(contentElements, rangeStart, to), layoutContext);
        int next = to;
        while (next < contentElements.size() && !(contentElements.get(next) instanceof KnuthElement
                && ((KnuthElement) contentElements.get(next)).isBox())) {
            next++;
        }
        rangeStart = next;
        ((Float) fobj).setLayoutContinuation(this);
    }

    /**
     * Places what is left of the content, the whole of it when nothing was split off, as a side float area, and
     * clears the state kept on the FO.
     * @param layoutContext the layout context
     */
    public void processRemainder(LayoutContext layoutContext) {
        addAreas(new KnuthPossPosIter(contentElements, rangeStart, contentElements.size()), layoutContext);
        rangeStart = contentElements.size();
        ((Float) fobj).setLayoutContinuation(null);
    }

    private int getStartIndent() {
        int startIndent;
        LayoutManager lm = getParent();
        while (!(lm instanceof BlockLayoutManager)) {
            lm = lm.getParent();
        }
        startIndent = ((BlockLayoutManager) lm).startIndent;
        return startIndent;
    }
}
