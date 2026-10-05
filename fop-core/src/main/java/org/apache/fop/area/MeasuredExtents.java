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
 * Apache FOP 2.11: hook measured-region-extents, a page's header and footer extents measured from their static
 * content (fop/CR-018). See README.md, "Changes from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.area;

import java.awt.Rectangle;

import org.apache.xmlgraphics.util.QName;

import org.apache.fop.datatypes.FODimension;
import org.apache.fop.fo.Constants;
import org.apache.fop.fo.extensions.ExtensionElementMapping;
import org.apache.fop.fo.pagination.Region;
import org.apache.fop.fo.pagination.RegionBA;
import org.apache.fop.fo.pagination.SimplePageMaster;

/**
 * docx4j-fo-renderer hook {@code measured-region-extents} (fop/CR-018): the heights of a page's region-before and
 * region-after measured from their static content, for a master whose region carries
 * {@code fox:extent="measured"}. A page made with them has those regions that tall, and its region-body's margin on
 * each side the larger of its stated margin and the measured height, so the body clears the region, as a word
 * processor places the body below its header and above its footer.
 */
public final class MeasuredExtents {

    /** The attribute asking for a region's extent to be measured. */
    public static final QName ATTRIBUTE = new QName(ExtensionElementMapping.URI, "fox:extent");

    /** The attribute's value. */
    public static final String MEASURED = "measured";

    private int before = -1;

    private int after = -1;

    /**
     * @param region a region of a simple-page-master
     * @return whether the region asks for its extent to be measured
     */
    public static boolean isRequested(Region region) {
        return region != null && MEASURED.equals(region.getForeignAttributes().get(ATTRIBUTE));
    }

    /**
     * @param spm a simple-page-master
     * @return whether its region-before or region-after asks for its extent to be measured
     */
    public static boolean isRequested(SimplePageMaster spm) {
        return isRequested(spm.getRegion(Constants.FO_REGION_BEFORE))
                || isRequested(spm.getRegion(Constants.FO_REGION_AFTER));
    }

    /**
     * Records a region's measured extent.
     * @param regionId {@link Constants#FO_REGION_BEFORE} or {@link Constants#FO_REGION_AFTER}
     * @param extent the extent in millipoints
     */
    public void setExtent(int regionId, int extent) {
        if (regionId == Constants.FO_REGION_BEFORE) {
            before = extent;
        } else if (regionId == Constants.FO_REGION_AFTER) {
            after = extent;
        }
    }

    /**
     * @param regionId {@link Constants#FO_REGION_BEFORE} or {@link Constants#FO_REGION_AFTER}
     * @return the region's measured extent, or -1 where it was not measured
     */
    public int getExtent(int regionId) {
        return (regionId == Constants.FO_REGION_BEFORE) ? before
                : (regionId == Constants.FO_REGION_AFTER) ? after : -1;
    }

    /**
     * A region's viewport rectangle, in the page's relative coordinates, with the measured extents applied.
     * @param region the region
     * @param rect its rectangle from its own properties
     * @param reldims the page reference area's relative dimensions
     * @return the rectangle to use
     */
    Rectangle adjust(Region region, Rectangle rect, FODimension reldims) {
        switch (region.getNameId()) {
        case Constants.FO_REGION_BEFORE:
            return (before >= 0) ? new Rectangle(rect.x, rect.y, rect.width, before) : rect;
        case Constants.FO_REGION_AFTER:
            return (after >= 0) ? new Rectangle(rect.x, reldims.bpd - after, rect.width, after) : rect;
        case Constants.FO_REGION_BODY:
            int top = rect.y;
            int bottom = reldims.bpd - rect.y - rect.height;
            int newTop = (before >= 0) ? Math.max(top, before) : top;
            int newBottom = (after >= 0) ? Math.max(bottom, after) : bottom;
            if (newTop == top && newBottom == bottom) {
                return rect;
            }
            return new Rectangle(rect.x, newTop, rect.width, Math.max(0, reldims.bpd - newTop - newBottom));
        case Constants.FO_REGION_START:
        case Constants.FO_REGION_END:
            return adjustSide(region, rect, reldims);
        default:
            return rect;
        }
    }

    /**
     * Region-start and region-end lie between region-before and region-after where those have precedence
     * (XSL 1.1, the precedence property), so they move by the difference between a measured extent and the stated one.
     */
    private Rectangle adjustSide(Region region, Rectangle rect, FODimension reldims) {
        SimplePageMaster spm = (SimplePageMaster) region.getParent();
        int top = 0;
        int bottom = 0;
        RegionBA regionBefore = (RegionBA) spm.getRegion(Constants.FO_REGION_BEFORE);
        if (before >= 0 && regionBefore != null && regionBefore.getPrecedence() == Constants.EN_TRUE) {
            top = before - regionBefore.getViewportRectangle(reldims).height;
        }
        RegionBA regionAfter = (RegionBA) spm.getRegion(Constants.FO_REGION_AFTER);
        if (after >= 0 && regionAfter != null && regionAfter.getPrecedence() == Constants.EN_TRUE) {
            bottom = after - regionAfter.getViewportRectangle(reldims).height;
        }
        if (top == 0 && bottom == 0) {
            return rect;
        }
        return new Rectangle(rect.x, rect.y + top, rect.width, Math.max(0, rect.height - top - bottom));
    }

    /** {@inheritDoc} */
    public String toString() {
        return "MeasuredExtents[before=" + before + ", after=" + after + "]";
    }
}
