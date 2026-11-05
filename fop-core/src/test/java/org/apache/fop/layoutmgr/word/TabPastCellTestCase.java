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
 * Apache FOP 2.12: docx4j:tab-past-cell, hook tab-past-cell (fop/CR-038). See README.md, "Changes from Apache FOP
 * 2.11". */

/* $Id$ */

package org.apache.fop.layoutmgr.word;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import org.junit.AfterClass;
import org.junit.Test;
import org.w3c.dom.Document;
import static org.junit.Assert.assertEquals;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.apache.fop.apps.MimeConstants;

/**
 * A tab reaching a stop past a table cell's edge, measured on the {@code tab-trailing-narrow-cell} probe: below
 * compatibility mode 15 a custom left stop past the edge is honoured and the line does not break, where a stop of the
 * default grid past it still breaks. At mode 15, with no {@code docx4j:tab-past-cell}, both break.
 */
public class TabPastCellTestCase {

    /** FOP's own pair table back for the tests that follow in this JVM (fop/CR-031 §3.5). */
    @AfterClass
    public static void restoreFopPairTable() {
        WordLayoutTestSupport.restoreFopPairTable();
    }

    private static final String TAB = "<fo:leader docx4j:tab=\"1\" leader-length=\"0pt\" leader-pattern=\"space\"/>";

    /** A 23pt cell, or the same width of body, holding "0" and a tab, against the stops given. */
    private static String fo(boolean pastCell, boolean inCell, String stops) {
        String block = "<fo:block font-family=\"Courier\" font-size=\"12pt\" line-height=\"14pt\""
                + " docx4j:tabs=\"" + stops + "\" docx4j:tab-default=\"720\" docx4j:tab-ind=\"0:0:.\">0" + TAB
                + "</fo:block>";
        String content = inCell
                ? "<fo:table table-layout=\"fixed\" width=\"23pt\"><fo:table-column column-width=\"23pt\"/>"
                + "<fo:table-body><fo:table-row><fo:table-cell padding=\"0pt\">" + block
                + "</fo:table-cell></fo:table-row></fo:table-body></fo:table>"
                : block;
        return "<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\" xmlns:docx4j=\""
                + WordLayoutElementMapping.URI + "\"" + (pastCell ? " docx4j:tab-past-cell=\"true\"" : "") + ">"
                + "<fo:layout-master-set><fo:simple-page-master master-name=\"m\" page-width=\""
                + (inCell ? "400pt" : "23pt") + "\" page-height=\"400pt\" margin=\"0pt\">"
                + "<fo:region-body/></fo:simple-page-master></fo:layout-master-set>"
                + "<fo:page-sequence master-reference=\"m\"><fo:flow flow-name=\"xsl-region-body\">" + content
                + "</fo:flow></fo:page-sequence></fo:root>";
    }

    private static int lines(String fo) throws Exception {
        FopFactoryBuilder b = new FopFactoryBuilder(new File(".").toURI());
        b.setWordLayout(WordLayoutTestSupport.settings());
        FopFactory factory = b.build();
        FOUserAgent ua = factory.newFOUserAgent();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Fop fop = factory.newFop(MimeConstants.MIME_FOP_AREA_TREE, ua, out);
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.transform(new StreamSource(new ByteArrayInputStream(fo.getBytes("UTF-8"))),
                new SAXResult(fop.getDefaultHandler()));
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document area = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(out.toByteArray()));
        return area.getElementsByTagName("lineArea").getLength();
    }

    private static final String CUSTOM = "720:left:none";

    @Test
    public void aCustomStopPastTheCellIsHonoured() throws Exception {
        assertEquals(1, lines(fo(true, true, CUSTOM)));
    }

    @Test
    public void withoutTheAttributeTheLineBreaks() throws Exception {
        assertEquals(2, lines(fo(false, true, CUSTOM)));
    }

    @Test
    public void aStopOfTheDefaultGridStillBreaks() throws Exception {
        assertEquals(2, lines(fo(true, true, "")));
    }

    @Test
    public void onlyInACell() throws Exception {
        assertEquals(2, lines(fo(true, false, CUSTOM)));
    }
}
