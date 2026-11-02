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
 * Apache FOP 2.12: Word mode's test LetterSpacingWidthTestCase, moved from docx4j-export-fo's
  * LetterSpacingWidthTest (fop/CR-031, docx4j
 * CR-071). See README.md, "Changes from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.layoutmgr.inline;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopConfParser;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.apache.fop.apps.MimeConstants;
import org.apache.fop.layoutmgr.word.WordLayoutTestSupport;

/**
 * FOP 2.11 leaves the letter spaces out of a word's width on its complex-script
 * text path (fonts with GSUB/GPOS, i.e. every embedded OpenType font while
 * complex-script features are on), so letter-spaced lines overflow; the line
 * manager puts them back (CR-001 §6.6 item 16).  The plain path (complex-script
 * features off) is FOP's own correct reference: with the fix both must break
 * the same lines.
 */
public class LetterSpacingWidthTestCase {

    /** FOP's own pair table back for the tests that follow in this JVM (fop/CR-031 §3.5). */
    @AfterClass
    public static void restoreFopPairTable() {
        WordLayoutTestSupport.restoreFopPairTable();
    }

    private static File fontFile;

    @BeforeClass
    public static void font() throws Exception {
        // docx4j's test took Liberation Serif from its fonts module; any TrueType font with GSUB and GPOS tables
        // takes FOP's complex-script path, and fop-core's tests carry DejaVu LGC Serif (fop/CR-031)
        fontFile = new File("test/resources/fonts/ttf/DejaVuLGCSerif.ttf");
        Assume.assumeTrue("DejaVu LGC Serif in fop-core's test resources", fontFile.isFile());
    }

    private static String fo(String letterSpacing) {
        StringBuilder words = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            words.append(i == 0 ? "" : " ").append("abcde");
        }
        return "<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\">"
                + "<fo:layout-master-set><fo:simple-page-master master-name=\"m\" page-width=\"595pt\" "
                + "page-height=\"842pt\" margin=\"72pt\">"
                + "<fo:region-body/></fo:simple-page-master></fo:layout-master-set>"
                + "<fo:page-sequence master-reference=\"m\"><fo:flow flow-name=\"xsl-region-body\">"
                + "<fo:block font-family=\"Test Serif\" font-size=\"12pt\" line-height=\"14pt\" letter-spacing=\""
                + letterSpacing + "\">"
                + "<fo:inline letter-spacing=\"" + letterSpacing + "\">" + words + "</fo:inline></fo:block>"
                + "</fo:flow></fo:page-sequence></fo:root>";
    }

    private static List<String> lines(String fo, boolean complexScripts, boolean wordLayout) throws Exception {
        File conf = File.createTempFile("fop", ".xml");
        conf.deleteOnExit();
        Files.write(conf.toPath(),
                ("<fop version=\"1.0\"><use-cache>false</use-cache><renderers><renderer "
                        + "mime=\"application/pdf\"><fonts>"
                + "<font embed-url=\"" + fontFile.toURI()
                + "\" kerning=\"false\"><font-triplet name=\"Test Serif\" style=\"normal\" weight=\"normal\"/></font>"
                + "</fonts></renderer></renderers></fop>").getBytes("UTF-8"));
        FopFactoryBuilder b = new FopConfParser(conf).getFopFactoryBuilder();
        b.setComplexScriptFeatures(complexScripts);
        if (wordLayout) {
            b.setWordLayout(WordLayoutTestSupport.settings());
        }
        FopFactory factory = b.build();
        FOUserAgent ua = factory.newFOUserAgent();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Fop fop = factory.newFop(MimeConstants.MIME_FOP_AREA_TREE, ua, out);
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.transform(new StreamSource(new ByteArrayInputStream(fo.getBytes("UTF-8"))),
                new SAXResult(fop.getDefaultHandler()));
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(out.toByteArray()));
        List<String> lines = new ArrayList<>();
        NodeList las = doc.getElementsByTagName("lineArea");
        for (int i = 0; i < las.getLength(); i++) {
            StringBuilder sb = new StringBuilder();
            collect((Element) las.item(i), sb);
            lines.add(sb.toString().trim().replaceAll(" +", " "));
        }
        return lines;
    }

    private static void collect(Element el, StringBuilder sb) {
        NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element)) {
                continue;
            }
            Element c = (Element) n;
            if ("word".equals(c.getLocalName())) {
                sb.append(c.getTextContent());
            } else if ("space".equals(c.getLocalName())) {
                sb.append(' ');
            } else {
                collect(c, sb);
            }
        }
    }

    @Test
    public void expandedTextBreaksAsOnThePlainPath() throws Exception {
        List<String> reference = lines(fo("3pt"), false, false);
        List<String> fixed = lines(fo("3pt"), true, true);
        assertEquals(reference, fixed);
    }

    @Test
    public void condensedTextBreaksAsOnThePlainPath() throws Exception {
        assertEquals(lines(fo("-1pt"), false, false), lines(fo("-1pt"), true, true));
    }

    @Test
    public void fopItselfStillHasTheDefect() throws Exception {
        // documents the FOP 2.11 behaviour the fix exists for (reproduced on the
        // classpath; under surefire's module path FOP's complex-script path is not
        // taken, so the case is skipped rather than failed there).  When it fails on
        // the classpath, FOP has fixed GlyphMapping.processWordMapping and
        // fixLetterSpaces can go.
        List<String> plain = lines(fo("3pt"), false, false);
        List<String> complex = lines(fo("3pt"), true, false);
        Assume.assumeTrue("FOP's complex-script text path not in use here", !plain.equals(complex));
        assertNotEquals(plain, complex);
    }

    @Test
    public void countedSpacesInTheWidthDependOnThePath() {
        // the plain path has always added its counted letter spaces to the width
        assertEquals(4, WordLineLayoutManager.lettersInWidth(false, false, 4));
        assertEquals(4, WordLineLayoutManager.lettersInWidth(false, true, 4));
        // the complex-script path: FOP 2.11 counts none; a FOP carrying Apache's
        // FOP-2722 alone counts them and leaves them out of the width
        assertEquals(0, WordLineLayoutManager.lettersInWidth(true, false, 0));
        assertEquals(0, WordLineLayoutManager.lettersInWidth(true, false, 4));
        // the docx4j FO renderer with letter-space-width puts them in, as the plain path does
        assertEquals(4, WordLineLayoutManager.lettersInWidth(true, true, 4));
    }
}
