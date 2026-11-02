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
 * Apache FOP 2.12: a table that does not fit the room beside a side float goes below it, and a float's intrusion
 * ends at a forced page break (fop/CR-031 section 11). See README.md, "Changes from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.layoutmgr.word;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import static org.junit.Assert.assertEquals;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.apache.fop.apps.MimeConstants;

/**
 * A side float and what follows it, in Word mode and in FOP's own (fop/CR-031 section 11): a table whose FO fixes a
 * width greater than the room beside the float goes below the float, as Word sets a table that does not fit beside a
 * floating table (its float-gap-tables probe), and one that fits stays beside it; and a float whose foot lies below a
 * forced page break narrows nothing on the next page (the floating-table probe's page 3, gate b270).
 */
public class TableBesideFloatTestCase {

    private static final String SEQ = "<fo:page-sequence master-reference=\"A4\">"
            + "<fo:flow flow-name=\"xsl-region-body\" font-size=\"12pt\" line-height=\"16pt\">";

    private static final String END = "</fo:flow></fo:page-sequence>";

    private static final String ANCHOR = "<fo:block space-after=\"8pt\"><fo:float float=\"left\">"
            + "<fo:block-container width=\"307pt\" height=\"100pt\"><fo:block>FLOAT</fo:block></fo:block-container>"
            + "</fo:float>anchor</fo:block>";

    private static String table(String name, int width) {
        return "<fo:block><fo:table table-layout=\"fixed\" width=\"" + width + "pt\" border-collapse=\"collapse\" "
                + "border=\"0.48pt solid black\"><fo:table-column column-width=\"" + (width / 2) + "pt\"/>"
                + "<fo:table-column column-width=\"" + (width / 2) + "pt\"/><fo:table-body><fo:table-row>"
                + "<fo:table-cell><fo:block>" + name + "</fo:block></fo:table-cell><fo:table-cell><fo:block>x"
                + "</fo:block></fo:table-cell></fo:table-row></fo:table-body></fo:table></fo:block>";
    }

    private static final String FO = "<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\">"
            + "<fo:layout-master-set><fo:simple-page-master master-name=\"A4\" page-width=\"595pt\" "
            + "page-height=\"842pt\" margin=\"72pt\"><fo:region-body/></fo:simple-page-master>"
            + "</fo:layout-master-set>"
            + SEQ + ANCHOR + table("FITS", 100) + "<fo:block>after-fits</fo:block>" + END
            + SEQ + ANCHOR + table("WIDE", 200) + "<fo:block>after-wide</fo:block>" + END
            + SEQ + ANCHOR + "<fo:block>before-break</fo:block>"
            + "<fo:block break-before=\"page\">after-break one two three four five six seven eight nine ten eleven "
            + "twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty twenty-one</fo:block>" + END
            + "</fo:root>";

    private static Document wordLayout;

    private static Document fopLayout;

    private final XPath xpath = XPathFactory.newInstance().newXPath();

    @BeforeClass
    public static void layOutAll() throws Exception {
        wordLayout = layOut(FO, true);
        fopLayout = layOut(FO, false);
    }

    static Document layOut(String fo, boolean wordMode) throws Exception {
        FopFactoryBuilder b = new FopFactoryBuilder(new File(".").toURI());
        if (wordMode) {
            b.setWordLayout(WordLayoutTestSupport.settings());
        }
        FopFactory factory = b.build();
        FOUserAgent ua = factory.newFOUserAgent();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Fop fop = factory.newFop(MimeConstants.MIME_FOP_AREA_TREE, ua, out);
        TransformerFactory.newInstance().newTransformer().transform(
                new StreamSource(new ByteArrayInputStream(fo.getBytes("UTF-8"))),
                new SAXResult(fop.getDefaultHandler()));
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new ByteArrayInputStream(out.toByteArray()));
    }

    /** FOP's own pair table back for the tests that follow in this JVM (fop/CR-031 §3.5). */
    @AfterClass
    public static void restoreFopPairTable() {
        WordLayoutTestSupport.restoreFopPairTable();
    }

    private void assertBoth(String expected, String expression) throws Exception {
        assertEquals("Word mode: " + expression, expected, xpath.evaluate(expression, wordLayout));
        assertEquals("FOP mode: " + expression, expected, xpath.evaluate(expression, fopLayout));
    }

    @Test
    public void aTableThatFitsTheRoomStaysBesideTheFloat() throws Exception {
        assertBoth("144000", "(//pageViewport)[1]//word[.='FITS']/ancestor::block[4]/@ipd");
        assertBoth("144000", "(//pageViewport)[1]//word[.='after-fits']/ancestor::lineArea[1]/@ipd");
    }

    @Test
    public void aTableWiderThanTheRoomGoesBelowTheFloat() throws Exception {
        // the anchor's space-after and the table's border-before put an auxiliary box between the break and the
        // table's first row: the break before the table is the edge all the same, and the table starts at the
        // float's foot, 100pt less the anchor's line and space-after below the anchor
        assertBoth("451000", "(//pageViewport)[2]//word[.='WIDE']/ancestor::block[4]/@ipd");
        assertBoth("1", "count((//pageViewport)[2]//flow/block[@bpd='76000'])");
        assertBoth("451000", "(//pageViewport)[2]//word[.='after-wide']/ancestor::lineArea[1]/@ipd");
    }

    @Test
    public void aFloatEndsAtAForcedPageBreak() throws Exception {
        assertBoth("144000", "(//pageViewport)[3]//word[.='before-break']/ancestor::lineArea[1]/@ipd");
        assertBoth("451000", "(//pageViewport)[4]//word[.='after-break']/ancestor::lineArea[1]/@ipd");
        assertBoth("451000", "(//pageViewport)[4]//word[.='fifteen']/ancestor::lineArea[1]/@ipd");
    }
}
