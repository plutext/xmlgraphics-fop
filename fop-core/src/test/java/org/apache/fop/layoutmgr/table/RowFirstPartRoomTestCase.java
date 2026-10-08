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

package org.apache.fop.layoutmgr.table;

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

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;

/**
 * Hook row-first-part-room (fop/CR-025): a table row's first part at a page's foot must have room for the after
 * padding and border its cells give up at the split. Twelve one-line rows of 19.38pt (a 16pt line, 1.44pt of padding
 * before and after, a 0.5pt collapsed border) fill 232.56pt; a thirteenth row's first line ends at 250.25pt and with
 * its padding-after and border-after at 251.94pt. On a body of 251pt a three-line row's first line fits only without
 * the margin: with the hook the whole row goes to the next page, without it the row is started and split after its
 * first line. On a body of 252pt the first line fits with the margin and the row is started and split either way.
 * A one-line row, which cannot split, is moved whole on 251pt and kept on 252pt either way.
 */
public class RowFirstPartRoomTestCase {

    private static final String CELL_START = "<fo:table-cell padding-before=\"1.44pt\" padding-after=\"1.44pt\""
            + " padding-start=\"2pt\" padding-end=\"2pt\" border=\"0.5pt solid black\">"
            + "<fo:block line-height=\"16pt\" orphans=\"1\" widows=\"1\">";
    private static final String CELL_END = "</fo:block></fo:table-cell>";

    private static String fo(int bodyHeight, String thirteenthRow) {
        StringBuilder sb = new StringBuilder("<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\">"
                + "<fo:layout-master-set><fo:simple-page-master master-name=\"p\" page-width=\"595pt\""
                + " page-height=\"" + (bodyHeight + 144) + "pt\" margin=\"72pt\"><fo:region-body/>"
                + "</fo:simple-page-master></fo:layout-master-set>"
                + "<fo:page-sequence master-reference=\"p\"><fo:flow flow-name=\"xsl-region-body\" font-size=\"12pt\">"
                + "<fo:table table-layout=\"fixed\" width=\"451pt\" border-collapse=\"collapse\">"
                + "<fo:table-column column-width=\"451pt\"/><fo:table-body>");
        for (int i = 0; i < 12; i++) {
            sb.append("<fo:table-row>").append(CELL_START).append("r").append(i).append(CELL_END)
                    .append("</fo:table-row>");
        }
        sb.append("<fo:table-row>").append(CELL_START).append(thirteenthRow).append(CELL_END)
                .append("</fo:table-row>");
        for (int i = 0; i < 3; i++) {
            sb.append("<fo:table-row>").append(CELL_START).append("after").append(i).append(CELL_END)
                    .append("</fo:table-row>");
        }
        return sb.append("</fo:table-body></fo:table></fo:flow></fo:page-sequence></fo:root>").toString();
    }

    private static String threeLines() {
        StringBuilder sb = new StringBuilder("A1 first line of the three-line row ");
        for (int i = 0; i < 20; i++) {
            sb.append("word ");
        }
        sb.append("A2 second line ");
        for (int i = 0; i < 20; i++) {
            sb.append("word ");
        }
        return sb.append("A3 third line").toString();
    }

    /** The lines on each page of the area tree, as the first word of each line. */
    private static List<List<String>> linesPerPage(String fo, boolean room) throws Exception {
        FopFactory factory = FopFactory.newInstance(new File(".").toURI());
        FOUserAgent ua = factory.newFOUserAgent();
        ua.setRowFirstPartRoom(room);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Fop fop = factory.newFop(MimeConstants.MIME_FOP_AREA_TREE, ua, out);
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.transform(new StreamSource(new ByteArrayInputStream(fo.getBytes(StandardCharsets.UTF_8))),
                new SAXResult(fop.getDefaultHandler()));
        String areaTree = new String(out.toByteArray(), StandardCharsets.UTF_8);
        List<List<String>> pages = new ArrayList<List<String>>();
        for (String page : areaTree.split("<pageViewport ")) {
            if (!page.contains("<flow")) {
                continue;
            }
            List<String> lines = new ArrayList<String>();
            Matcher m = Pattern.compile("<lineArea [^>]*>.*?<word[^>]*>([^<]*)</word>", Pattern.DOTALL)
                    .matcher(page);
            while (m.find()) {
                lines.add(m.group(1));
            }
            pages.add(lines);
        }
        return pages;
    }

    private static String firstWords(List<List<String>> pages) {
        StringBuilder sb = new StringBuilder();
        for (List<String> page : pages) {
            sb.append(page.size()).append(':').append(page.get(0)).append("..").append(page.get(page.size() - 1))
                    .append(' ');
        }
        return sb.toString().trim();
    }

    @Test
    public void testThreeLineRowWhoseFirstLineFitsOnlyWithoutTheMarginGoesWhole() throws Exception {
        String fo = fo(251, threeLines());
        assertEquals("13:r0..A1 6:word..after2", firstWords(linesPerPage(fo, false)));
        assertEquals("12:r0..r11 7:A1..after2", firstWords(linesPerPage(fo, true)));
    }

    @Test
    public void testThreeLineRowWhoseFirstLineFitsWithTheMarginIsSplit() throws Exception {
        String fo = fo(252, threeLines());
        assertEquals("13:r0..A1 6:word..after2", firstWords(linesPerPage(fo, false)));
        assertEquals("13:r0..A1 6:word..after2", firstWords(linesPerPage(fo, true)));
    }

    @Test
    public void testOneLineRowIsUnchanged() throws Exception {
        assertEquals("12:r0..r11 4:B1..after2", firstWords(linesPerPage(fo(251, "B1 one-line row"), true)));
        assertEquals("13:r0..B1 3:after0..after2", firstWords(linesPerPage(fo(252, "B1 one-line row"), true)));
    }
}
