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
 * Apache FOP 2.11: a test of hook measured-region-extents, events (fop/CR-018). See README.md, "Changes from Apache
 * FOP 2.11". */

/* $Id$ */

package org.apache.fop.layoutmgr;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.apache.fop.events.Event;
import org.apache.fop.events.EventListener;

/**
 * Hook measured-region-extents: measuring a header reports no event of its own, and events are reported again once
 * the measurement is done. Layout tests can check that an event occurs, not how often.
 */
public class MeasuredRegionExtentsTestCase {

    private static final String WORD = "Wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww";

    /** A header and a body line each too wide for the page, on two pages. */
    private static String fo(boolean measured) {
        String extent = measured ? " fox:extent=\"measured\"" : "";
        StringBuilder flow = new StringBuilder("<fo:block>" + WORD + "</fo:block>");
        for (int i = 0; i < 30; i++) {
            flow.append("<fo:block>a").append(i).append("</fo:block>");
        }
        return "<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\""
                + " xmlns:fox=\"http://xmlgraphics.apache.org/fop/extensions\"><fo:layout-master-set>"
                + "<fo:simple-page-master master-name=\"p\" page-width=\"300pt\" page-height=\"300pt\" margin=\"20pt\">"
                + "<fo:region-body margin-top=\"30pt\"/><fo:region-before extent=\"10pt\"" + extent + "/>"
                + "</fo:simple-page-master></fo:layout-master-set>"
                + "<fo:page-sequence master-reference=\"p\">"
                + "<fo:static-content flow-name=\"xsl-region-before\"><fo:block>" + WORD + "</fo:block>"
                + "</fo:static-content><fo:flow flow-name=\"xsl-region-body\" font-size=\"10pt\" line-height=\"12pt\">"
                + flow + "</fo:flow></fo:page-sequence></fo:root>";
    }

    private static Map<String, Integer> events(boolean measured) throws Exception {
        final Map<String, Integer> counts = new TreeMap<String, Integer>();
        FopFactory factory = FopFactory.newInstance(new File(".").toURI());
        FOUserAgent ua = factory.newFOUserAgent();
        ua.getEventBroadcaster().addEventListener(new EventListener() {
            public void processEvent(Event event) {
                Integer count = counts.get(event.getEventKey());
                counts.put(event.getEventKey(), count == null ? 1 : count + 1);
            }
        });
        Fop fop = factory.newFop(MimeConstants.MIME_FOP_AREA_TREE, ua, new ByteArrayOutputStream());
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.transform(new StreamSource(new ByteArrayInputStream(fo(measured).getBytes(StandardCharsets.UTF_8))),
                new SAXResult(fop.getDefaultHandler()));
        return counts;
    }

    /**
     * The header's line overflow is reported once for each page and the body's once, with the measurement as
     * without it; the stated 10pt header also overflows its region on each page, and the measured one does not.
     * @throws Exception if the rendering fails
     */
    @Test
    public void testMeasurementReportsNoEventOfItsOwn() throws Exception {
        Map<String, Integer> plain = events(false);
        Map<String, Integer> measured = events(true);
        assertEquals(plain.toString(), Integer.valueOf(3), plain.get("lineOverflows"));
        assertEquals(measured.toString(), Integer.valueOf(3), measured.get("lineOverflows"));
        assertEquals(plain.toString(), Integer.valueOf(2), plain.get("staticRegionOverflow"));
        assertNull(measured.toString(), measured.get("staticRegionOverflow"));
    }
}
