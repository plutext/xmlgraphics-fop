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
 * ends at a forced page break (fop/CR-031 section 11); a table that would cross an offset float's top goes wholly
 * below the float, through a keep-with-next (fop/CR-032). See README.md, "Changes from Apache FOP 2.11". */

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

    private static final String EMPTY = "<fo:block white-space-treatment=\"preserve\"> </fo:block>";

    /** An anchor whose left float lies 40pt below the anchor block's top (fop/CR-032). */
    private static final String OFFSET_ANCHOR = "<fo:block space-after=\"8pt\"><fo:float float=\"left\" "
            + "fox:float-offset=\"40pt\"><fo:block-container width=\"307pt\" height=\"100pt\"><fo:block>FLOAT"
            + "</fo:block></fo:block-container></fo:float>anchor</fo:block>";

    private static String table(String name, int width) {
        return "<fo:block><fo:table table-layout=\"fixed\" width=\"" + width + "pt\" border-collapse=\"collapse\" "
                + "border=\"0.48pt solid black\"><fo:table-column column-width=\"" + (width / 2) + "pt\"/>"
                + "<fo:table-column column-width=\"" + (width / 2) + "pt\"/><fo:table-body><fo:table-row>"
                + "<fo:table-cell><fo:block>" + name + "</fo:block></fo:table-cell><fo:table-cell><fo:block>x"
                + "</fo:block></fo:table-cell></fo:table-row></fo:table-body></fo:table></fo:block>";
    }

    private static final String FO = "<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\" "
            + "xmlns:fox=\"http://xmlgraphics.apache.org/fop/extensions\">"
            + "<fo:layout-master-set><fo:simple-page-master master-name=\"A4\" page-width=\"595pt\" "
            + "page-height=\"842pt\" margin=\"72pt\"><fo:region-body/></fo:simple-page-master>"
            + "</fo:layout-master-set>"
            + SEQ + ANCHOR + table("FITS", 100) + "<fo:block>after-fits</fo:block>" + END
            + SEQ + ANCHOR + table("WIDE", 200) + "<fo:block>after-wide</fo:block>" + END
            + SEQ + ANCHOR + "<fo:block>before-break</fo:block>"
            + "<fo:block break-before=\"page\">after-break one two three four five six seven eight nine ten eleven "
            + "twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty twenty-one</fo:block>" + END
            // two floats that overlap by 4pt, a negative room between them, and empty paragraphs after (8985's page 13)
            + SEQ + "<fo:block><fo:float float=\"left\"><fo:block-container width=\"230pt\" height=\"60pt\">"
            + "<fo:block>LEFT</fo:block></fo:block-container></fo:float><fo:float float=\"right\">"
            + "<fo:block-container width=\"225pt\" height=\"60pt\"><fo:block>RIGHT</fo:block></fo:block-container>"
            + "</fo:float></fo:block>" + EMPTY + EMPTY + EMPTY + EMPTY + EMPTY
            + "<fo:block>after-overlap</fo:block>" + END
            // a float leaving 0.05pt, and its anchor's own first word (9832 under docx4j's step 2)
            + SEQ + "<fo:block><fo:float float=\"left\"><fo:block-container width=\"450.95pt\" height=\"60pt\">"
            + "<fo:block>NARROW</fo:block></fo:block-container></fo:float>August 2015-November 2015 Trail My Car "
            + "Solutions one two three four five six seven eight nine ten</fo:block>" + END
            // a table whose second row would cross the top of an offset float, without and with a keep-with-next
            // heading before it (Word's float-top-crossing-table probe, fop/CR-032)
            + SEQ + OFFSET_ANCHOR + tallTable("CROSS") + "<fo:block>after-cross</fo:block>" + END
            + SEQ + OFFSET_ANCHOR + "<fo:block keep-with-next=\"always\">kept-heading</fo:block>" + tallTable("KEPT")
            + "<fo:block>after-kept</fo:block>" + END
            // a floating table taller than the page, anchored at the page's top: split as Word splits it, the rows
            // that fit on the first page with nothing beside them, the anchor beside the rest on the next
            // (Word's float-tall-beside and float-three-pages probes, fop/CR-034)
            + SEQ + "<fo:block space-after=\"8pt\"><fo:float float=\"left\">" + splitTable(30)
            + "</fo:float>split-anchor one two three four five six seven eight nine ten eleven twelve</fo:block>"
            + "<fo:block>after-split</fo:block>" + END
            + "</fo:root>";

    /** A 200pt table of that many 30pt rows, cells s1 to sN (fop/CR-034). */
    private static String splitTable(int rows) {
        StringBuilder b = new StringBuilder("<fo:block><fo:table table-layout=\"fixed\" width=\"200pt\" "
                + "border-collapse=\"collapse\" border=\"0.48pt solid black\"><fo:table-column column-width=\"100pt\"/>"
                + "<fo:table-column column-width=\"100pt\"/><fo:table-body>");
        for (int i = 1; i <= rows; i++) {
            b.append("<fo:table-row height=\"30pt\"><fo:table-cell><fo:block>s").append(i)
                    .append("</fo:block></fo:table-cell><fo:table-cell><fo:block>x</fo:block></fo:table-cell>"
                            + "</fo:table-row>");
        }
        return b.append("</fo:table-body></fo:table></fo:block>").toString();
    }

    /** A 200pt table of two 30pt rows: wider than the 144pt beside the float, its second row across the float's top. */
    private static String tallTable(String name) {
        return "<fo:block><fo:table table-layout=\"fixed\" width=\"200pt\" border-collapse=\"collapse\" "
                + "border=\"0.48pt solid black\"><fo:table-column column-width=\"100pt\"/>"
                + "<fo:table-column column-width=\"100pt\"/><fo:table-body><fo:table-row height=\"30pt\">"
                + "<fo:table-cell><fo:block>" + name + "</fo:block></fo:table-cell><fo:table-cell><fo:block>x"
                + "</fo:block></fo:table-cell></fo:table-row><fo:table-row height=\"30pt\">"
                + "<fo:table-cell><fo:block>two</fo:block></fo:table-cell><fo:table-cell><fo:block>y"
                + "</fo:block></fo:table-cell></fo:table-row></fo:table-body></fo:table></fo:block>";
    }

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

    @Test
    public void aBlockOfNoFixedWidthStaysBesideFloatsThatOverlap() throws Exception {
        // the room between the floats is -4pt; a paragraph fixes no width, so it is never wider than the room, and the
        // anchor's line and the four empty paragraphs above the floats' foot stay beside them (8985, gate b312). Word
        // mode only: FOP's own layout marks a line of white space that overflows the room (CR-023 §4.2), and sends
        // the empty paragraphs below (fop/CR-031 §6.2)
        assertEquals("5", xpath.evaluate("count((//pageViewport)[5]//lineArea[@ipd='0'])", wordLayout));
        assertBoth("451000", "//word[.='after-overlap']/ancestor::lineArea[1]/@ipd");
    }

    /**
     * The first line of a float's own anchor block stays beside the float, though it fits neither the room (0.05pt)
     * nor any width beside the float: the float's start edge is the break before that line, and no break precedes
     * it in the list read beside the float, so CR-023's mark cannot move it; the rest of the paragraph goes below.
     * Word sets the line below the float (9832's golden). Open, fop/CR-031 §11; pinned so that a fix changes it.
     */
    @Test
    public void theAnchorsFirstWordStaysInASliverBesideTheFloat() throws Exception {
        assertBoth("50", "//word[.='August']/ancestor::lineArea[1]/@ipd");
        assertBoth("451000", "//word[starts-with(., '2015-November')]/ancestor::lineArea[1]/@ipd");
    }

    /**
     * A table wider than the room beside an offset float, whose second row would cross the float's top, goes wholly
     * below the float, its first row included, and nothing is set beside the float (Word's float-top-crossing-table
     * probe, fop/CR-032). The float's foot is 140pt below the anchor's top; the anchor's line and space-after take
     * 24pt, so the clearance before the table is 116pt. Before this the start edge before the table set the table
     * beside the float, 200pt in a 144pt room.
     */
    @Test
    public void aTableCrossingAnOffsetFloatsTopGoesBelowTheFloat() throws Exception {
        assertBoth("451000", "(//pageViewport)[7]//word[.='CROSS']/ancestor::block[4]/@ipd");
        assertBoth("1", "count((//pageViewport)[7]//flow/block[@bpd='116000'])");
        assertBoth("451000", "(//pageViewport)[7]//word[.='after-cross']/ancestor::lineArea[1]/@ipd");
    }

    /**
     * The same through a keep-with-next heading: the heading stays where it is, full width above the float's top,
     * and the table alone goes below the float (the probe's case 2; corpus 4083's date table, which the keep had left
     * full width over the float, no edge being offered before it). The clearance is 100pt, the heading's 16pt less.
     */
    /**
     * A 900pt floating table on a 698pt body, anchored at the page's top: rows 1 to 23 fill the first page with
     * nothing beside them, the anchor and rows 24 to 30 open the next page, the anchor's lines beside the rows in
     * the 251pt room, as is the paragraph after it, the rows being 210pt tall (fop/CR-034).
     */
    @Test
    public void aFloatingTableTallerThanThePageIsSplitWithTheAnchorOnItsLastPage() throws Exception {
        assertBoth("1", "count((//pageViewport)[9]//word[.='s23'])");
        assertBoth("0", "count((//pageViewport)[9]//word[.='s24'])");
        assertBoth("0", "count((//pageViewport)[9]//word[.='split-anchor'])");
        assertBoth("1", "count((//pageViewport)[10]//word[.='s24'])");
        assertBoth("1", "count((//pageViewport)[10]//word[.='s30'])");
        assertBoth("251000", "(//pageViewport)[10]//word[.='split-anchor']/ancestor::lineArea[1]/@ipd");
        assertBoth("251000", "(//pageViewport)[10]//word[.='after-split']/ancestor::lineArea[1]/@ipd");
    }

    @Test
    public void aKeptTableCrossingAnOffsetFloatsTopGoesBelowTheFloatAlone() throws Exception {
        assertBoth("451000", "(//pageViewport)[8]//word[.='kept-heading']/ancestor::lineArea[1]/@ipd");
        assertBoth("100000",
                "(//pageViewport)[8]//word[.='kept-heading']/ancestor::block[1]/following-sibling::block[1]/@bpd");
        assertBoth("451000", "(//pageViewport)[8]//word[.='KEPT']/ancestor::block[4]/@ipd");
        assertBoth("451000", "(//pageViewport)[8]//word[.='after-kept']/ancestor::lineArea[1]/@ipd");
    }
}
