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

/* Added by Plutext Pty Ltd for the docx4j FO renderer (docx4j-fo-renderer), a modified distribution derived
 * from Apache FOP 2.11: initial-page-number="0" under the page-number-zero hook (fop/CR-012). See README.md,
 * "Changes from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.fo.pagination;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;

/**
 * initial-page-number="0" (Word's cover page numbered 0) is kept as 0 only where the user agent
 * allows it; by default FOP makes it 1, the XSL error recovery. Both readers of the property, the
 * sequence's starting number and the previous sequence's force-page-count, see the same value.
 */
public class PageNumberZeroTestCase {

    private static final String MASTERS = "<fo:layout-master-set><fo:simple-page-master master-name=\"p\""
            + " page-width=\"300pt\" page-height=\"200pt\" margin=\"20pt\"><fo:region-body/>"
            + "</fo:simple-page-master></fo:layout-master-set>";

    private static String fo(String sequences) {
        return "<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\">" + MASTERS + sequences + "</fo:root>";
    }

    private static String sequence(String attributes, String... pages) {
        StringBuilder sb = new StringBuilder("<fo:page-sequence master-reference=\"p\" " + attributes + ">"
                + "<fo:flow flow-name=\"xsl-region-body\">");
        for (int i = 0; i < pages.length; i++) {
            sb.append("<fo:block").append(i > 0 ? " break-before=\"page\"" : "").append(">")
                    .append(pages[i]).append("</fo:block>");
        }
        return sb.append("</fo:flow></fo:page-sequence>").toString();
    }

    private static byte[] render(String fo, String mime, boolean zeroAllowed) throws Exception {
        FopFactory factory = FopFactory.newInstance(new File(".").toURI());
        FOUserAgent ua = factory.newFOUserAgent();
        ua.setPageNumberZeroAllowed(zeroAllowed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Fop fop = factory.newFop(mime, ua, out);
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.transform(new StreamSource(new ByteArrayInputStream(fo.getBytes(StandardCharsets.UTF_8))),
                new SAXResult(fop.getDefaultHandler()));
        return out.toByteArray();
    }

    private static List<String> pageNumbers(String fo, boolean zeroAllowed) throws Exception {
        String areaTree = new String(render(fo, MimeConstants.MIME_FOP_AREA_TREE, zeroAllowed),
                StandardCharsets.UTF_8);
        List<String> numbers = new ArrayList<String>();
        Matcher m = Pattern.compile("<pageViewport [^>]*formatted-nr=\"([^\"]*)\"").matcher(areaTree);
        while (m.find()) {
            numbers.add(m.group(1));
        }
        return numbers;
    }

    @Test
    public void testZeroIsOneByDefault() throws Exception {
        String fo = fo(sequence("initial-page-number=\"0\"", "cover", "first"));
        assertEquals("[1, 2]", pageNumbers(fo, false).toString());
    }

    @Test
    public void testZeroIsKeptWhenAllowed() throws Exception {
        String fo = fo(sequence("initial-page-number=\"0\"", "cover", "first"));
        assertEquals("[0, 1]", pageNumbers(fo, true).toString());
    }

    /**
     * force-page-count="auto" ends a sequence so the next one's first page has its own parity: the
     * next starting at 1 (the default) pads the one-page sequence to two pages, the next starting at
     * 0 (allowed) does not.
     */
    @Test
    public void testForcePageCountSeesTheSameValue() throws Exception {
        String fo = fo(sequence("force-page-count=\"auto\"", "one")
                + sequence("initial-page-number=\"0\"", "cover", "first"));
        assertEquals("[1, 2, 1, 2]", pageNumbers(fo, false).toString());
        assertEquals("[1, 0, 1]", pageNumbers(fo, true).toString());
    }

    /** A PDF with a page numbered 0 is written, with that page labelled as a prefix. */
    @Test
    public void testPdfWithPageNumberedZero() throws Exception {
        String fo = fo(sequence("initial-page-number=\"0\"", "cover", "first"));
        String pdf = new String(render(fo, MimeConstants.MIME_PDF, true), StandardCharsets.ISO_8859_1);
        assertTrue(pdf, pdf.contains("/PageLabels"));
        assertTrue(pdf, pdf.contains("/P (0)"));
    }
}
