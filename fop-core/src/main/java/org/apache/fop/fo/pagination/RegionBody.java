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
 * Apache FOP 2.11: hook column-widths, the fox:column-widths and fox:column-gaps properties (fop/CR-026). See
 * README.md, "Changes from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.fo.pagination;

// Java
import java.awt.Rectangle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.fop.apps.FOPException;
import org.apache.fop.datatypes.FODimension;
import org.apache.fop.datatypes.Length;
import org.apache.fop.datatypes.LengthBase;
import org.apache.fop.datatypes.Numeric;
import org.apache.fop.datatypes.PercentBaseContext;
import org.apache.fop.fo.Constants;
import org.apache.fop.fo.FONode;
import org.apache.fop.fo.PropertyList;
import org.apache.fop.fo.properties.CommonMarginBlock;
import org.apache.fop.fo.properties.FixedLength;

/**
 * Class modelling the <a href="http://www.w3.org/TR/xsl/#fo_region-body">
 * <code>fo:region-body</code></a> object.
 */
public class RegionBody extends Region {
    // The value of properties relevant for fo:region-body.
    private CommonMarginBlock commonMarginBlock;
    private Numeric columnCount;
    private Length columnGap;
    private String columnWidthsText; // fox:column-widths (hook column-widths, fop/CR-026)
    private String columnGapsText; // fox:column-gaps
    // End of property values

    private int[] columnWidths; // fop/CR-026: per column, millipoints, or null for equal columns
    private int[] columnGaps; // per pair of columns
    private boolean columnWidthsWarned;

    private static final Pattern LENGTH = Pattern.compile("([-+]?[0-9]*\\.?[0-9]+)\\s*([a-z]+)");

    /**
     * Create a RegionBody instance that is a child of the
     * given parent {@link FONode}.
     * @param parent    the {@link FONode} that is to be the parent
     */
    public RegionBody(FONode parent) {
        super(parent);
    }

    /** {@inheritDoc} */
    public void bind(PropertyList pList) throws FOPException {
        super.bind(pList);
        commonMarginBlock = pList.getMarginBlockProps();
        columnCount = pList.get(PR_COLUMN_COUNT).getNumeric();
        columnGap = pList.get(PR_COLUMN_GAP).getLength();
        columnWidthsText = pList.get(PR_X_COLUMN_WIDTHS).getString(); // fop/CR-026
        columnGapsText = pList.get(PR_X_COLUMN_GAPS).getString();
        parseColumnWidths();

        if ((getColumnCount() > 1) && (getOverflow() == EN_SCROLL)) {
            /* This is an error (See XSL Rec, fo:region-body description).
             * The Rec allows for acting as if "1" is chosen in
             * these cases, but we will need to be able to change Numeric
             * values in order to do this.
             */
            getFOValidationEventProducer().columnCountErrorOnRegionBodyOverflowScroll(this,
                    getName(), getLocator());
        }
    }

    /**
     * Return the {@link CommonMarginBlock} instance attached to
     * this instance.
     * @return the {@link CommonMarginBlock} instance
     */
    public CommonMarginBlock getCommonMarginBlock() {
        return commonMarginBlock;
    }

    /**
     * Return the value of the <code>column-count</code> property.
     * @return the "column-count" property.
     */
    public int getColumnCount() {
        return columnCount.getValue();
    }

    /**
     * Return the value of the <code>column-gap</code> property.
     * @return the "column-gap" property.
     */
    public int getColumnGap() {
        return columnGap.getValue();
    }

    /**
     * Hook column-widths (fop/CR-026): reads fox:column-widths and fox:column-gaps, a length per column and one
     * per pair of columns, space-separated. The gaps may be absent, each gap then being column-gap. The lists
     * must agree with column-count; otherwise they are ignored with a warning and the columns stay equal.
     */
    private void parseColumnWidths() {
        columnWidths = null;
        columnGaps = null;
        if (isAbsent(columnWidthsText)) {
            if (!isAbsent(columnGapsText)) {
                warnColumnWidthsIgnored("fox:column-gaps given without fox:column-widths");
            }
            return;
        }
        int count = getColumnCount();
        int[] widths = parseLengths(columnWidthsText);
        if (widths == null || widths.length != count || count < 2) {
            warnColumnWidthsIgnored("column-count is " + count + ", so " + count
                    + " widths are needed, each a length");
            return;
        }
        int[] gaps;
        if (isAbsent(columnGapsText)) {
            gaps = new int[count - 1];
            java.util.Arrays.fill(gaps, getColumnGap());
        } else {
            gaps = parseLengths(columnGapsText);
            if (gaps == null || gaps.length != count - 1) {
                warnColumnWidthsIgnored("column-count is " + count + ", so " + (count - 1)
                        + " gaps are needed, each a length");
                return;
            }
        }
        for (int w : widths) {
            if (w <= 0) {
                warnColumnWidthsIgnored("a width is not positive");
                return;
            }
        }
        for (int g : gaps) {
            if (g < 0) {
                warnColumnWidthsIgnored("a gap is negative");
                return;
            }
        }
        columnWidths = widths;
        columnGaps = gaps;
    }

    private static boolean isAbsent(String text) {
        return text == null || text.trim().length() == 0 || "none".equals(text.trim());
    }

    private static int[] parseLengths(String text) {
        String[] tokens = text.trim().split("[\\s,]+");
        int[] values = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            Matcher m = LENGTH.matcher(tokens[i]);
            if (!m.matches()) {
                return null;
            }
            try {
                values[i] = FixedLength.getInstance(Double.parseDouble(m.group(1)), m.group(2)).getValue();
            } catch (RuntimeException e) {
                return null;
            }
        }
        return values;
    }

    private void warnColumnWidthsIgnored(String reason) {
        columnWidths = null;
        columnGaps = null;
        if (!columnWidthsWarned) {
            columnWidthsWarned = true;
            getFOValidationEventProducer().columnWidthsIgnored(this, getName(), columnWidthsText,
                    columnGapsText, reason, getLocator());
        }
    }

    /**
     * Hook column-widths (fop/CR-026): the widths of the body's columns for a page whose body has the given
     * content inline size, checked once against it: the widths and gaps must sum to it within a point.
     * @param contentIPD the body region's content inline size in millipoints
     * @return the widths, one per column, or null for equal columns
     */
    public int[] resolveColumnWidths(int contentIPD) {
        if (columnWidths == null) {
            return null;
        }
        int sum = 0;
        for (int w : columnWidths) {
            sum += w;
        }
        for (int g : columnGaps) {
            sum += g;
        }
        if (Math.abs(sum - contentIPD) > 1000) {
            warnColumnWidthsIgnored("the widths and gaps sum to " + sum + "mpt where the body is " + contentIPD
                    + "mpt wide");
            return null;
        }
        return columnWidths;
    }

    /**
     * Hook column-widths (fop/CR-026).
     * @return the gaps between the body's columns, one per pair, or null for equal columns
     */
    public int[] getColumnGaps() {
        return columnGaps;
    }

    /** {@inheritDoc} */
    public Rectangle getViewportRectangle(FODimension reldims) {
        /* Special rules apply to resolving margins in the page context.
         * Contrary to normal margins in this case top and bottom margin
         * are resolved relative to the height. In the property subsystem
         * all margin properties are configured to using BLOCK_WIDTH.
         * That's why we 'cheat' here and setup a context for the height but
         * use the LengthBase.BLOCK_WIDTH.
         * Also the values are resolved relative to the page size
         * and reference orientation.
         */
        PercentBaseContext pageWidthContext
            = getPageWidthContext(LengthBase.CONTAINING_BLOCK_WIDTH);
        PercentBaseContext pageHeightContext
            = getPageHeightContext(LengthBase.CONTAINING_BLOCK_WIDTH);

        int start;
        int end;
        // [TBD] WRITING MODE ALERT
        switch (getWritingMode().getEnumValue()) {
        case Constants.EN_RL_TB:
            start = commonMarginBlock.marginRight.getValue(pageWidthContext);
            end = commonMarginBlock.marginLeft.getValue(pageWidthContext);
            break;
        case Constants.EN_TB_LR:
        case Constants.EN_TB_RL:
            start = commonMarginBlock.marginTop.getValue(pageWidthContext);
            end = commonMarginBlock.marginBottom.getValue(pageWidthContext);
            break;
        case Constants.EN_LR_TB:
        default:
            start = commonMarginBlock.marginLeft.getValue(pageWidthContext);
            end = commonMarginBlock.marginRight.getValue(pageWidthContext);
            break;
        }
        int before = commonMarginBlock.spaceBefore.getOptimum(pageHeightContext)
                        .getLength().getValue(pageHeightContext);
        int after = commonMarginBlock.spaceAfter.getOptimum(pageHeightContext)
                        .getLength().getValue(pageHeightContext);
        return new Rectangle(start, before,
                    reldims.ipd - start - end,
                    reldims.bpd - before - after);
    }

    /** {@inheritDoc} */
    public String getDefaultRegionName() {
        return "xsl-region-body";
    }

    /** {@inheritDoc} */
    public String getLocalName() {
        return "region-body";
    }

    /**
     * {@inheritDoc}
     * @return {@link org.apache.fop.fo.Constants#FO_REGION_BODY}
     */
    public int getNameId() {
        return FO_REGION_BODY;
    }
}
