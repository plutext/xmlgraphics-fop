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
 * Apache FOP 2.12: a restart after a Word line's leading reads the next block (fop/CR-031 section 9). See
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

import org.junit.AfterClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.apache.fop.apps.MimeConstants;

/**
 * A Word line's leading goes behind the break possibility after its paragraph ({@link LeadingGlue}), and a restart
 * at that break, after an obstacle's edge or before a page of another width, looked for the block to read again at
 * the next element with a position: the leading, whose position is the paragraph's own block, so the paragraph was
 * laid a second time (14_es-DO 4253's client line down the gap beside its table; fop/CR-031 section 9).
 */
public class LeadingGlueRestartTestCase {

    @AfterClass
    public static void restoreFopPairTable() {
        WordLayoutTestSupport.restoreFopPairTable();
    }

    private static final String WORD_PARA = "<fo:block docx4j:line-box=\"19.531pt\" docx4j:baseline=\"15.234pt\" "
            + "docx4j:line-rule=\"auto\" font-size=\"16pt\" line-height=\"22.461pt\" space-after=\"10pt\">";

    private static final String ROOT = "<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\" "
            + "xmlns:fox=\"http://xmlgraphics.apache.org/fop/extensions\" "
            + "xmlns:docx4j=\"http://docx4j.org/fop/word-layout\">";

    static String obstacleFo() {
        return ROOT
            + "<fo:layout-master-set><fo:simple-page-master master-name=\"P\" page-width=\"612pt\" "
            + "page-height=\"792pt\" margin=\"72pt\"><fo:region-body/></fo:simple-page-master></fo:layout-master-set>"
            + "<fo:page-sequence master-reference=\"P\"><fo:flow flow-name=\"xsl-region-body\">"
            + "<fo:block-container start-indent=\"0pt\" end-indent=\"0pt\">"
            + "<fo:block-container absolute-position=\"fixed\" fox:wrap=\"square\" fox:wrap-sides=\"both\" "
            + "fox:wrap-distances=\"0pt 0pt 7pt 7pt\" left=\"72pt\" top=\"110pt\" width=\"400pt\" height=\"80pt\">"
            + "<fo:block>OBST</fo:block></fo:block-container></fo:block-container>"
            + WORD_PARA + "Cliente: Universidad</fo:block>"
            + WORD_PARA + "p2a p2b p2c p2d p2e p2f p2g p2h p2i p2j p2k p2l p2m p2n p2o p2p p2q p2r p2s p2t p2u "
            + "p2v</fo:block>"
            + "</fo:flow></fo:page-sequence></fo:root>";
    }

    static String ipdChangeFo() {
        StringBuilder sb = new StringBuilder(ROOT
            + "<fo:layout-master-set>"
            + "<fo:simple-page-master master-name=\"wide\" page-width=\"612pt\" page-height=\"300pt\" "
            + "margin=\"72pt\">"
            + "<fo:region-body/></fo:simple-page-master>"
            + "<fo:simple-page-master master-name=\"narrow\" page-width=\"500pt\" page-height=\"300pt\" "
            + "margin=\"72pt\">"
            + "<fo:region-body/></fo:simple-page-master>"
            + "<fo:page-sequence-master master-name=\"M\"><fo:single-page-master-reference master-reference=\"wide\"/>"
            + "<fo:repeatable-page-master-reference master-reference=\"narrow\"/></fo:page-sequence-master>"
            + "</fo:layout-master-set>"
            + "<fo:page-sequence master-reference=\"M\"><fo:flow flow-name=\"xsl-region-body\">");
        for (int i = 1; i <= 14; i++) {
            sb.append(WORD_PARA).append("q").append(i).append(" text</fo:block>");
        }
        return sb.append("</fo:flow></fo:page-sequence></fo:root>").toString();
    }

    static Document layOut(String fo, boolean word) throws Exception {
        FopFactoryBuilder b = new FopFactoryBuilder(new File(".").toURI());
        if (word) {
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

    private static String text(Element e) {
        NodeList ws = e.getElementsByTagName("word");
        StringBuilder t = new StringBuilder();
        for (int k = 0; k < ws.getLength(); k++) {
            t.append(ws.item(k).getTextContent()).append(' ');
        }
        return t.toString().trim();
    }

    private static int count(Document doc, String word) {
        int n = 0;
        NodeList ws = doc.getElementsByTagName("word");
        for (int k = 0; k < ws.getLength(); k++) {
            if (word.equals(ws.item(k).getTextContent())) {
                n++;
            }
        }
        return n;
    }

    @Test
    public void theParagraphBeforeAnObstaclesEdgeIsLaidOnce() throws Exception {
        Document doc = layOut(obstacleFo(), true);
        assertEquals(1, count(doc, "Cliente:"));
        NodeList lines = doc.getElementsByTagName("lineArea");
        for (int i = 0; i < lines.getLength(); i++) {
            Element l = (Element) lines.item(i);
            if (text(l).startsWith("p2a")) {
                // the next paragraph is the first beside the obstacle, in the 61pt on its right
                assertEquals("61000", l.getAttribute("ipd"));
                return;
            }
        }
        fail("no line starts with p2a");
    }

    @Test
    public void theParagraphBeforeAChangeOfWidthIsLaidOnce() throws Exception {
        Document doc = layOut(ipdChangeFo(), true);
        assertEquals(1, count(doc, "q5"));
        NodeList pages = doc.getElementsByTagName("pageViewport");
        assertEquals(3, pages.getLength());
        Element first = (Element) ((Element) pages.item(1)).getElementsByTagName("lineArea").item(0);
        assertEquals("q6 text", text(first));
        assertEquals("356000", first.getAttribute("ipd"));
    }
}
