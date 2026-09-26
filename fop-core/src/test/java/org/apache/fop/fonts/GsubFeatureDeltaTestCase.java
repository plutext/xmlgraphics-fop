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

package org.apache.fop.fonts;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.fop.apps.io.InternalResourceResolver;
import org.apache.fop.apps.io.ResourceResolverFactory;

/**
 * A delta over the GSUB features a script processor would apply, carried by
 * {@code fox:gsub-features} and threaded down to the substitution.
 *
 * <p>DejaVuLGCSerif declares a {@code liga} feature and maps the f-ligatures to their
 * Unicode presentation forms, so whether a ligature formed is visible in the substituted
 * sequence: {@code fi} is either two characters or the single U+FB01.</p>
 */
public class GsubFeatureDeltaTestCase {

    private static final String SCRIPT = "latn";
    private static final String LANGUAGE = "dflt";

    private MultiByteFont font;

    @Before
    public void setUp() throws Exception {
        InternalResourceResolver resolver =
                ResourceResolverFactory.createDefaultInternalResourceResolver(new File(".").toURI());
        File file = new File("test/resources/fonts/ttf/DejaVuLGCSerif.ttf");
        // useAdvanced must be true or the font carries no GSUB and the test proves nothing
        CustomFont loaded = FontLoader.loadFont(new FontUris(file.toURI(), null), "", true,
                EmbeddingMode.AUTO, EncodingMode.AUTO, false, true, resolver, false, false, true);
        assertTrue("expected a MultiByteFont", loaded instanceof MultiByteFont);
        font = (MultiByteFont) loaded;
        assertTrue("font must carry GSUB for this test to mean anything", font.performsSubstitution());
    }

    /**
     * With no delta the substitution is exactly what it was before this property existed:
     * the ligature still forms. This is the inertness half of the contract.
     */
    @Test
    public void testNoDeltaLeavesSubstitutionUnchanged() {
        assertEquals("ﬁ", substitute(null).toString());
    }

    /** Removing a feature suppresses the substitution that feature performs. */
    @Test
    public void testSubtractingLigaSuppressesTheLigature() {
        assertEquals("fi", substitute(new String[] {"-liga"}).toString());
    }

    /** A feature the font's rules do not apply to this text changes nothing. */
    @Test
    public void testAddingAnUnrelatedFeatureChangesNothing() {
        assertEquals("ﬁ", substitute(new String[] {"+dlig"}).toString());
    }

    /** Removing a feature the processor does not apply changes nothing. */
    @Test
    public void testSubtractingAnAbsentFeatureChangesNothing() {
        assertEquals("ﬁ", substitute(new String[] {"-kern"}).toString());
    }

    private CharSequence substitute(String[] delta) {
        return font.performSubstitution("fi", SCRIPT, LANGUAGE, null, false, delta);
    }
}
