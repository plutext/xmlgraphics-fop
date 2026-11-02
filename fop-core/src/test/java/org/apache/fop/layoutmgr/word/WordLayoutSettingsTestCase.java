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
 * Apache FOP 2.12: Word mode's switch and settings, tested (fop/CR-031, docx4j CR-071). See README.md, "Changes
 * from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.layoutmgr.word;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.apache.fop.apps.MimeConstants;
import org.apache.fop.events.Event;
import org.apache.fop.events.EventListener;

/**
 * Word mode's switch and settings (fop/CR-031): off by default, on with a map of docx4j's resolved properties, read
 * through the user agent; docx4j's defaults and parsing; and the {@code docx4j:} namespace accepted on an FO.
 */
public class WordLayoutSettingsTestCase {

    private static final String P = WordLayoutSettings.PREFIX;

    @Test
    public void testOffByDefault() {
        FopFactory factory = new FopFactoryBuilder(new File(".").toURI()).build();
        assertNull(factory.newFOUserAgent().getWordLayout());
        factory = new FopFactoryBuilder(new File(".").toURI()).setWordLayout(null).build();
        assertNull(factory.newFOUserAgent().getWordLayout());
    }

    @Test
    public void testOnWithAMap() {
        Map<String, String> settings = new HashMap<String, String>();
        settings.put(P + "seamBreak", "false");
        FopFactory factory = new FopFactoryBuilder(new File(".").toURI()).setWordLayout(settings).build();
        WordLayoutSettings word = factory.newFOUserAgent().getWordLayout();
        assertNotNull(word);
        assertFalse(word.seamBreak());
        settings.put(P + "seamBreak", "true");
        assertFalse("the settings are copied when the factory is built", word.seamBreak());
    }

    @Test
    public void testDocx4jDefaults() {
        WordLayoutSettings word = new WordLayoutSettings(new HashMap<String, String>());
        assertNull(word.configuredMaxSpaceShrink());
        assertEquals(0.245, word.maxSpaceShrink(), 0);
        assertEquals(0.10, word.maxHyphenSpaceShrink(), 0);
        assertEquals(0, word.minStretchToCompress(), 0);
        assertEquals(0.5, word.compressionToStretch(), 0);
        assertFalse(word.enforceHyphenationZone());
        assertTrue(word.labelAscentAgainstBaseline());
        assertTrue(word.lineBaselineFromBlock());
        assertTrue(word.leaderGrid());
        assertTrue(word.widowsAcrossSoftReturns());
        assertTrue(word.tabSpaces());
        assertTrue(word.leaderCharacters());
        assertTrue(word.labelSuffixSpace());
        assertTrue(word.emergencyBreak());
        assertEquals(0.05, word.emergencyBreakTolerance(0.05), 0);
        assertEquals(0.05, word.cellEmergencyBreakTolerance(0.05), 0);
        assertEquals(0, word.cellEmergencyBreakToleranceRatio(0), 0);
        assertEquals("000", word.pageNumberPlaceholder("000"));
        assertTrue(word.seamBreak());
        assertTrue(word.breakOpportunities());
        assertTrue(word.boundKeepChains());
        assertEquals(900, word.keepChainPenalty());
        assertEquals(3.0, word.keepChainTolerance(), 0);
        assertEquals(1.0, word.keepChainRowTolerance(), 0);
    }

    @Test
    public void testParsedAsDocx4jParsedThem() {
        Map<String, String> settings = new HashMap<String, String>();
        settings.put(P + "maxSpaceShrink", " 0.3 ");
        settings.put(P + "maxHyphenSpaceShrink", "not a number");
        settings.put(P + "hyphenationZone", "TRUE");
        settings.put(P + "leaderGrid", "no");
        settings.put(P + "pageNumberPlaceholder", " 00 ");
        settings.put(P + "keepChainPenalty", "5000");
        settings.put(P + "keepChainTolerance", "0.5");
        settings.put("docx4j.convert.out.fo.someWriterSetting", "x");
        WordLayoutSettings word = new WordLayoutSettings(settings);
        assertEquals(Double.valueOf(0.3), word.configuredMaxSpaceShrink());
        assertEquals(0.10, word.maxHyphenSpaceShrink(), 0);
        assertTrue(word.enforceHyphenationZone());
        assertFalse(word.leaderGrid());
        assertEquals("00", word.pageNumberPlaceholder("000"));
        assertEquals(999, word.keepChainPenalty());
        assertEquals(1.0, word.keepChainTolerance(), 0);
    }

    @Test
    public void testTheNamespaceIsAccepted() throws Exception {
        String fo = "<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\" xmlns:docx4j=\""
                + WordLayoutElementMapping.URI + "\" docx4j:space-shrink=\"0.2\">"
                + "<fo:layout-master-set><fo:simple-page-master master-name=\"p\" page-width=\"200pt\""
                + " page-height=\"200pt\"><fo:region-body/></fo:simple-page-master></fo:layout-master-set>"
                + "<fo:page-sequence master-reference=\"p\"><fo:flow flow-name=\"xsl-region-body\">"
                + "<fo:block docx4j:line-box=\"13.8pt\" docx4j:line-rule=\"auto\">Word</fo:block>"
                + "</fo:flow></fo:page-sequence></fo:root>";
        FopFactory factory = new FopFactoryBuilder(new File(".").toURI()).build();
        FOUserAgent ua = factory.newFOUserAgent();
        final List<String> events = new ArrayList<String>();
        ua.getEventBroadcaster().addEventListener(new EventListener() {
            public void processEvent(Event event) {
                events.add(event.getEventKey());
            }
        });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Fop fop = factory.newFop(MimeConstants.MIME_FOP_AREA_TREE, ua, out);
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.transform(new StreamSource(new ByteArrayInputStream(fo.getBytes(StandardCharsets.UTF_8))),
                new SAXResult(fop.getDefaultHandler()));
        assertFalse(events.toString(), events.contains("invalidProperty"));
        assertTrue(new String(out.toByteArray(), StandardCharsets.UTF_8).contains("Word"));
    }
}
