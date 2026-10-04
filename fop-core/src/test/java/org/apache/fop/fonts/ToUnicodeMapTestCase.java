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
 * Apache FOP 2.11: the to-unicode-map hook, from configuration to ToUnicode text (fop/CR-014). See README.md,
 * "Changes from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.fonts;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

import org.apache.fop.configuration.Configuration;
import org.apache.fop.configuration.DefaultConfigurationBuilder;

/**
 * A symbol font's private-use text (Wingdings U+F04A, a smiling face) can be published in the
 * ToUnicode CMap as its Unicode equivalent, through {@code <to-unicode>} entries in the font's
 * configuration. Only the text the CMap carries changes.
 */
public class ToUnicodeMapTestCase {

    private static final String SMILE = "☺";

    private static final String NEUTRAL = "😐";

    @Test
    public void testConfigurationEntriesAreParsed() throws Exception {
        String xml = "<renderer><fonts><font embed-url=\"symbol.ttf\">"
                + "<font-triplet name=\"Sym\" style=\"normal\" weight=\"normal\"/>"
                + "<to-unicode code-point=\"F04A\" unicode=\"263A\"/>"
                + "<to-unicode code-point=\"F04B\" unicode=\"1F610\"/>"
                + "<to-unicode code-point=\"F041\" unicode=\"0041 0301\"/>"
                + "</font></fonts></renderer>";
        Configuration cfg = new DefaultConfigurationBuilder().build(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        DefaultFontConfig config = (DefaultFontConfig) new DefaultFontConfig.DefaultFontConfigParser()
                .parse(cfg, null, true, null);
        Map<Integer, String> map = config.getFonts().get(0).getToUnicode();
        assertEquals(3, map.size());
        assertEquals(SMILE, map.get(0xF04A));
        assertEquals(NEUTRAL, map.get(0xF04B));
        assertEquals("Á", map.get(0xF041));
    }

    @Test
    public void testOverridesReplaceTheToUnicodeText() {
        MultiByteFont font = new MultiByteFont(null, null);
        font.setCMap(new CMapSegment[] {
            new CMapSegment(0xF04A, 0xF04A, 1),
            new CMapSegment(0xF04B, 0xF04B, 2),
            new CMapSegment(0x41, 0x41, 3),
            new CMapSegment(Typeface.NOT_FOUND, Typeface.NOT_FOUND, 0)
        });
        CIDSubset subset = new CIDSubset(font);
        subset.mapCodePoint(1, 0xF04A);
        subset.mapCodePoint(2, 0xF04B);
        subset.mapCodePoint(3, 0x41);
        assertEquals("[\uFFFF, \uF04A, \uF04B, A]", Arrays.asList(subset.getUnicodeSequences()).toString());

        Map<Integer, String> overrides = new HashMap<Integer, String>();
        overrides.put(0xF04A, SMILE);
        overrides.put(0xF04B, NEUTRAL);
        font.setToUnicodeOverrides(overrides);
        List<String> after = Arrays.asList(subset.getUnicodeSequences());
        assertEquals(Arrays.asList("\uFFFF", SMILE, NEUTRAL, "A"), after);
    }
}
