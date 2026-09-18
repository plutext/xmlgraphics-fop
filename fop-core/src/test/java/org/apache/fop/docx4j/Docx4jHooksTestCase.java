/*
 * Copyright 2026, Plutext Pty Ltd.
 *
 * This file is part of the docx4j FO renderer (docx4j-fo-renderer), a modified
 * distribution derived from Apache FOP 2.11. It is not part of Apache FOP.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.fop.docx4j;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.fop.area.inline.FilledArea;
import org.apache.fop.area.inline.Space;
import org.apache.fop.layoutmgr.LeafPosition;
import org.apache.fop.layoutmgr.inline.LineLayoutManager.LineBreakPosition;
import org.apache.fop.text.linebreak.LineBreakUtils;

/**
 * The hooks docx4j reads reflectively (CR-020 phase 1): each is named in
 * {@link Docx4jFop#capabilities()} and behaves as its contract says.
 */
public class Docx4jHooksTestCase {

    @Test
    public void testCapabilityNames() {
        assertTrue(Docx4jFop.has(Docx4jFop.PAIR_TABLE));
        assertTrue(Docx4jFop.has(Docx4jFop.LEADER_PLACEMENT));
        assertTrue(Docx4jFop.has(Docx4jFop.INLINE_ACCESS));
        assertTrue(Docx4jFop.has(Docx4jFop.GLYF_EMPTY_GLYPH));
        assertEquals(4, Docx4jFop.capabilities().size());
    }

    @Test
    public void testPairTableOverride() {
        int hy = LineBreakUtils.LINE_BREAK_PROPERTY_HY;
        int nu = LineBreakUtils.LINE_BREAK_PROPERTY_NU;
        byte was = LineBreakUtils.getLineBreakPairProperty(hy, nu);
        assertEquals("HY x NU is an indirect break in the table as shipped", LineBreakUtils.INDIRECT_BREAK, was);
        try {
            assertEquals(was, LineBreakUtils.setLineBreakPairProperty(hy, nu, LineBreakUtils.DIRECT_BREAK));
            assertEquals(LineBreakUtils.DIRECT_BREAK, LineBreakUtils.getLineBreakPairProperty(hy, nu));
        } finally {
            LineBreakUtils.setLineBreakPairProperty(hy, nu, was);
        }
        assertEquals(was, LineBreakUtils.getLineBreakPairProperty(hy, nu));
    }

    @Test
    public void testPairTableOverrideRejectsBadArguments() {
        try {
            LineBreakUtils.setLineBreakPairProperty(0, 1, LineBreakUtils.DIRECT_BREAK);
            fail("property 0 is outside the table");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            LineBreakUtils.setLineBreakPairProperty(1, 1, (byte) 9);
            fail("9 is not a break class");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void testFilledAreaUnit() {
        FilledArea run = new FilledArea();
        Space unit = new Space();
        unit.setIPD(100);
        run.addChildArea(unit);
        run.setUnitWidth(100);
        run.setIPD(550);
        assertEquals(5, run.getChildAreas().size());
        assertEquals(1, run.getUnitAreas().size());
        assertSame(unit, run.getUnitAreas().get(0));
    }

    @Test
    public void testLeafPosSetter() {
        LeafPosition p = new LeafPosition(null, 3);
        p.setLeafPos(7);
        assertEquals(7, p.getLeafPos());
    }

    @Test
    public void testLineBreakPositionGetters() {
        LineBreakPosition p = new LineBreakPosition(null, 1, 2, 3, 4, 5, 6, 0.5, 0.25, 7, 8, 9, 10, 11, 12, 13);
        assertEquals(1, p.getParIndex());
        assertEquals(2, p.getStartIndex());
        assertEquals(3, p.getLeafPos());
        assertEquals(4, p.getAvailableShrink());
        assertEquals(5, p.getAvailableStretch());
        assertEquals(6, p.getDifference());
        assertEquals(0.5, p.getIpdAdjust(), 0);
        assertEquals(0.25, p.getDAdjust(), 0);
        assertEquals(7, p.getStartIndent());
        assertEquals(8, p.getEndIndent());
        assertEquals(9, p.getLineHeight());
        assertEquals(10, p.getLineWidth());
        assertEquals(11, p.getSpaceBefore());
        assertEquals(12, p.getSpaceAfter());
        assertEquals(13, p.getBaseline());
    }
}
