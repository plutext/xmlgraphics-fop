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

/* $Id$ */

package org.apache.fop.complexscripts.scripts;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertSame;

/**
 * How a fox:gsub-features delta is applied to a script processor's feature list.
 *
 * <p>Order matters: the assembled lookups are applied in list order, so these tests pin
 * the two rules the implementation follows. Removals leave the survivors in their original
 * order, and additions go in sorted position because every processor's list is
 * alphabetical.</p>
 */
public class GsubDeltaTestCase {

    /** The default script processor's list, and the shape of the others. */
    private static final String[] BASE = {"ccmp", "liga", "locl"};

    @Test
    public void testNoDeltaReturnsTheListItself() {
        assertArrayEquals(new String[] {"mark", "mkmk"},
                ScriptProcessor.applyFeatureDelta(new String[] {"kern", "mark", "mkmk"}, new String[] {"-kern"}));
        assertSame(BASE, ScriptProcessor.applyGsubDelta(BASE, null));
        assertSame(BASE, ScriptProcessor.applyGsubDelta(BASE, new String[0]));
    }

    @Test
    public void testRemovalLeavesTheSurvivorsInOrder() {
        assertArrayEquals(new String[] {"ccmp", "locl"},
                ScriptProcessor.applyGsubDelta(BASE, new String[] {"-liga"}));
    }

    @Test
    public void testAdditionGoesInSortedPosition() {
        assertArrayEquals(new String[] {"ccmp", "clig", "liga", "locl"},
                ScriptProcessor.applyGsubDelta(BASE, new String[] {"+clig"}));
    }

    @Test
    public void testRemovalAndAdditionTogether() {
        assertArrayEquals(new String[] {"ccmp", "clig", "locl"},
                ScriptProcessor.applyGsubDelta(BASE, new String[] {"-liga", "+clig"}));
    }

    @Test
    public void testAddingSomethingAlreadyThereIsNotADuplicate() {
        assertArrayEquals(BASE, ScriptProcessor.applyGsubDelta(BASE, new String[] {"+liga"}));
    }

    @Test
    public void testRemovingSomethingAbsentChangesNothing() {
        assertArrayEquals(BASE, ScriptProcessor.applyGsubDelta(BASE, new String[] {"-kern"}));
    }

    @Test
    public void testMalformedTokensAreIgnored() {
        assertArrayEquals(BASE,
                ScriptProcessor.applyGsubDelta(BASE, new String[] {null, "", "x", "liga", "?liga"}));
    }

    /**
     * The Arabic processor keeps the shaping features and required ligatures next to the
     * optional ones, so a delta must never be able to disturb them.
     */
    @Test
    public void testArabicShapingFeaturesSurviveRemovingLiga() {
        String[] arabic = {"calt", "ccmp", "fina", "init", "isol", "liga", "medi", "rlig"};
        assertArrayEquals(new String[] {"calt", "ccmp", "fina", "init", "isol", "medi", "rlig"},
                ScriptProcessor.applyGsubDelta(arabic, new String[] {"-liga"}));
    }
}
