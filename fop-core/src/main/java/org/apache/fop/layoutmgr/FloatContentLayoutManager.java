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
 * Apache FOP 2.11: hook float-offset, the float placed at its target top below the flow's height (fop/CR-023). See
 * README.md, "Changes from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.layoutmgr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

import org.apache.fop.area.Area;
import org.apache.fop.area.SideFloat;
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
    private int targetTop = -1; // fop/CR-023: the float's top in the page, set by the page breaker before the areas
    private int yShift; // fop/CR-023: how far below the flow's height the float was placed

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
        yOffset = lm.getParentArea(floatContentArea).getBPD();
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

    /** @return fox:float-offset, how far below the top of its anchor block the float begins (fop/CR-023) */
    public int getFloatOffset() {
        return floatOffset;
    }

    /** @param top the float's top in the page, from the page breaker, before the areas are made (fop/CR-023) */
    public void setTargetTop(int top) {
        targetTop = top;
    }

    /** @return how far below the flow's height at its insertion the float was placed (fop/CR-023) */
    public int getFloatYShift() {
        return yShift;
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
