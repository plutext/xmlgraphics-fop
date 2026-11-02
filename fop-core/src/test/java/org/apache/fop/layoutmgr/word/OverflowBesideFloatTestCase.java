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
 * Apache FOP 2.12: CR-023 section 4.2's mark under Word mode (fop/CR-031 section 6, docx4j CR-071 D11). See
 * README.md, "Changes from Apache FOP 2.11". */

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
 * CR-023 section 4.2 under Word mode (fop/CR-031 section 6, docx4j CR-071 D11): a line that does not fit beside a
 * side float, but would fit the column, is set at the float's foot with the rest of its paragraph, full width, as in
 * FOP's own layout ({@code float_overflow-below.xml}). The line here holds a box no break can split; Word mode's
 * emergency break splits a word too wide for the room beside the float, so a word's line does not overflow. A line
 * of nothing but white space is not marked: empty paragraphs stay beside two floats that fill the column, as Word
 * lays them (8985, CR-031 section 6.2).
 */
public class OverflowBesideFloatTestCase {

    private static Document areaTree;

    private final XPath xpath = XPathFactory.newInstance().newXPath();

    private static final String SEQ = "<fo:page-sequence master-reference=\"A4\">"
            + "<fo:flow flow-name=\"xsl-region-body\" font-size=\"12pt\" line-height=\"16pt\">";

    private static final String FLOAT = "<fo:block space-after=\"10pt\"><fo:float float=\"left\">"
            + "<fo:block-container width=\"300pt\" height=\"60pt\"><fo:block font-size=\"8pt\" line-height=\"8pt\">"
            + "FLOATTOP</fo:block></fo:block-container></fo:float>Heading</fo:block>";

    private static final String EMPTY = "<fo:block white-space-treatment=\"preserve\"> </fo:block>";

    private static final String FO = "<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\">"
            + "<fo:layout-master-set><fo:simple-page-master master-name=\"A4\" page-width=\"595pt\" "
            + "page-height=\"842pt\" margin=\"72pt\"><fo:region-body/></fo:simple-page-master>"
            + "</fo:layout-master-set>"
            // 1: a box the emergency break cannot split, too wide for the room beside the float, not for the column
            + SEQ + FLOAT
            + "<fo:block>a0 a1 <fo:instream-foreign-object>"
            + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"228pt\" height=\"10pt\"/>"
            + "</fo:instream-foreign-object> a0 a1 a2 a3 a4 a5 a6 a7 a8 "
            + "a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19 a20 a21 a22 a23 a24 a25 a26 a27 a28 a29</fo:block>"
            + "</fo:flow></fo:page-sequence>"
            // 2: a word wider than the column
            + SEQ + FLOAT
            + "<fo:block>b0 b1 WWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWW b0 b1 b2 b3 b4 b5 b6 b7 "
            + "b8 b9 b10 b11 b12 b13 b14 b15 b16 b17 b18 b19 b20 b21 b22 b23 b24 b25 b26 b27 b28 b29</fo:block>"
            + "</fo:flow></fo:page-sequence>"
            // 3: two floats that fill the column, and empty paragraphs after them, as 8985's page 13
            + SEQ + "<fo:block><fo:float float=\"left\"><fo:block-container width=\"230pt\" height=\"60pt\">"
            + "<fo:block>LEFT</fo:block></fo:block-container></fo:float><fo:float float=\"right\">"
            + "<fo:block-container width=\"221pt\" height=\"60pt\"><fo:block>RIGHT</fo:block></fo:block-container>"
            + "</fo:float></fo:block>" + EMPTY + EMPTY + EMPTY + EMPTY + EMPTY
            + "<fo:block>c-after</fo:block>"
            + "</fo:flow></fo:page-sequence>"
            + "</fo:root>";

    @BeforeClass
    public static void layOut() throws Exception {
        FopFactoryBuilder b = new FopFactoryBuilder(new File(".").toURI());
        b.setWordLayout(WordLayoutTestSupport.settings());
        FopFactory factory = b.build();
        FOUserAgent ua = factory.newFOUserAgent();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Fop fop = factory.newFop(MimeConstants.MIME_FOP_AREA_TREE, ua, out);
        TransformerFactory.newInstance().newTransformer().transform(
                new StreamSource(new ByteArrayInputStream(FO.getBytes("UTF-8"))),
                new SAXResult(fop.getDefaultHandler()));
        areaTree = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new ByteArrayInputStream(out.toByteArray()));
    }

    /** FOP's own pair table back for the tests that follow in this JVM (fop/CR-031 §3.5). */
    @AfterClass
    public static void restoreFopPairTable() {
        WordLayoutTestSupport.restoreFopPairTable();
    }

    private String eval(String expression) throws Exception {
        return xpath.evaluate(expression, areaTree);
    }

    @Test
    public void aBoxThatFitsTheColumnGoesBelowTheFloat() throws Exception {
        assertEquals("151000", eval("(//pageViewport)[1]//word[.='a0']/ancestor::lineArea[1]/@ipd"));
        assertEquals("451000", eval("(//pageViewport)[1]//lineArea[.//viewport]/@ipd"));
        assertEquals("451000", eval("(//pageViewport)[1]//word[.='a12']/ancestor::lineArea[1]/@ipd"));
        // the clearance from the first line's foot to the float's
        assertEquals("1", eval("count((//pageViewport)[1]//flow/block[@bpd='18000'])"));
        assertEquals("2", eval("count((//pageViewport)[1]//lineArea[@ipd='151000'])"));
    }

    @Test
    public void aWordWiderThanTheColumnStaysBesideTheFloat() throws Exception {
        assertEquals("151000", eval("//word[starts-with(., 'WWWW')]/ancestor::lineArea[1]/@ipd"));
        assertEquals("0", eval("count((//pageViewport)[2]//flow/block[@bpd='18000'])"));
    }

    @Test
    public void emptyParagraphsStayBesideFloatsThatFillTheColumn() throws Exception {
        // the anchor's line and the four empty paragraphs above the floats' foot; FOP's own layout keeps one
        assertEquals("5", eval("count((//pageViewport)[3]//lineArea[@ipd='0'])"));
        assertEquals("451000", eval("//word[.='c-after']/ancestor::lineArea[1]/@ipd"));
    }
}
