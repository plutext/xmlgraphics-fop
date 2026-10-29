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
 * from Apache FOP 2.11: the option svg-embedded-resources-only (fop/CR-028). See README.md,
 * "Changes from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.svg;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.apache.fop.apps.MimeConstants;
import org.apache.fop.events.Event;
import org.apache.fop.events.EventListener;

/**
 * The option {@code svg-embedded-resources-only} (fop/CR-028). An FO laid out from a file base URI carries five
 * inline SVGs and five external SVG graphics, each 100 by 20 points, stacked in rows: a plain rectangle, an image
 * embedded as a {@code data:} URL, a {@code use} of a fragment in the same document, an image read from a
 * {@code file:} URL and a {@code use} of a fragment in another file. By default the two file references are refused
 * and their graphics are not drawn, the other three rows are; with the option off Batik's same-host rule admits the
 * files, since a {@code file:} URL has no host on either side. Both the PDF path (FOP's own image bridge, which had
 * applied no rule) and the Java2D path (Batik's bridges and the SVG-to-Java2D converter) are read back as pixels.
 */
public class SVGExternalResourceSecurityTestCase {

    private static final int WIDTH = 100;
    private static final int ROW = 20;
    private static final int CASES = 5;
    private static final String SVG_NS = "http://www.w3.org/2000/svg";
    private static final String XLINK_NS = "http://www.w3.org/1999/xlink";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File dir;
    private File fo;

    @Before
    public void writeDocuments() throws IOException {
        dir = folder.getRoot();
        BufferedImage bitmap = new BufferedImage(WIDTH, ROW, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bitmap.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, WIDTH, ROW);
        g.dispose();
        File png = new File(dir, "secret.png");
        ImageIO.write(bitmap, "png", png);
        ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
        ImageIO.write(bitmap, "png", pngBytes);
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes.toByteArray());

        String rect = "<rect width=\"100\" height=\"20\" fill=\"#0000ff\"/>";
        File secretSvg = new File(dir, "secret.svg");
        write(secretSvg, svg("<g id=\"frag\">" + rect + "</g>"));

        String[] bodies = new String[] {
            rect,
            "<image width=\"100\" height=\"20\" xlink:href=\"" + dataUrl + "\"/>",
            "<defs><g id=\"local\">" + rect + "</g></defs><use xlink:href=\"#local\"/>",
            "<image width=\"100\" height=\"20\" xlink:href=\"" + png.toURI() + "\"/>",
            "<use xlink:href=\"" + secretSvg.toURI() + "#frag\"/>",
        };
        StringBuilder sb = new StringBuilder();
        sb.append("<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\" xmlns:svg=\"" + SVG_NS + "\""
                + " xmlns:xlink=\"" + XLINK_NS + "\">"
                + "<fo:layout-master-set><fo:simple-page-master master-name=\"p\" page-width=\"100pt\""
                + " page-height=\"200pt\" margin=\"0pt\"><fo:region-body/></fo:simple-page-master>"
                + "</fo:layout-master-set><fo:page-sequence master-reference=\"p\">"
                + "<fo:flow flow-name=\"xsl-region-body\">");
        for (int i = 0; i < CASES; i++) {
            write(new File(dir, "ext-" + i + ".svg"), svg(bodies[i]));
        }
        for (int row = 0; row < 2 * CASES; row++) {
            sb.append("<fo:block-container absolute-position=\"absolute\" top=\"" + (row * ROW) + "pt\""
                    + " left=\"0pt\" width=\"100pt\" height=\"20pt\"><fo:block>");
            if (row < CASES) {
                sb.append("<fo:instream-foreign-object>").append(svg(bodies[row]))
                        .append("</fo:instream-foreign-object>");
            } else {
                sb.append("<fo:external-graphic src=\"ext-" + (row - CASES) + ".svg\"/>");
            }
            sb.append("</fo:block></fo:block-container>");
        }
        sb.append("</fo:flow></fo:page-sequence></fo:root>");
        fo = new File(dir, "probe.fo");
        write(fo, sb.toString());
    }

    private static String svg(String body) {
        return "<svg xmlns=\"" + SVG_NS + "\" xmlns:xlink=\"" + XLINK_NS + "\" width=\"100\" height=\"20\">"
                + body + "</svg>";
    }

    private static void write(File f, String text) throws IOException {
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void defaultRefusesFilesOnThePDFPath() throws Exception {
        List<String> events = new ArrayList<String>();
        BufferedImage page = render(factory(true), MimeConstants.MIME_PDF, events);
        assertRows(page, false);
        assertEquals("one svgNotBuilt event per refused graphic", 4, count(events, "svgNotBuilt"));
    }

    @Test
    public void defaultRefusesFilesOnTheJava2DPath() throws Exception {
        List<String> events = new ArrayList<String>();
        BufferedImage page = render(factory(true), MimeConstants.MIME_PNG, events);
        assertRows(page, false);
    }

    @Test
    public void optionOffAdmitsFilesOnThePDFPath() throws Exception {
        List<String> events = new ArrayList<String>();
        BufferedImage page = render(factory(false), MimeConstants.MIME_PDF, events);
        assertRows(page, true);
        assertEquals(0, count(events, "svgNotBuilt"));
    }

    @Test
    public void optionOffAdmitsFilesOnTheJava2DPath() throws Exception {
        List<String> events = new ArrayList<String>();
        BufferedImage page = render(factory(false), MimeConstants.MIME_PNG, events);
        assertRows(page, true);
    }

    @Test
    public void userAgentReturnsEmbeddedSecurityOnlyWhenSet() {
        SimpleSVGUserAgent ua = new SimpleSVGUserAgent(new java.awt.geom.AffineTransform(),
                org.apache.batik.bridge.DefaultFontFamilyResolver.SINGLETON, 72f);
        assertFalse(ua.isEmbeddedResourcesOnly());
        org.apache.batik.util.ParsedURL doc = new org.apache.batik.util.ParsedURL(dir.toURI().toString());
        org.apache.batik.util.ParsedURL file = new org.apache.batik.util.ParsedURL(fo.toURI().toString());
        org.apache.batik.util.ParsedURL data = new org.apache.batik.util.ParsedURL("data:image/png;base64,AA==");
        ua.checkLoadExternalResource(file, doc); // Batik's rule: a file beside a file document
        ua.checkLoadExternalResource(data, doc);
        ua.setEmbeddedResourcesOnly(true);
        ua.checkLoadExternalResource(data, doc);
        try {
            ua.checkLoadExternalResource(file, doc);
            assertTrue("a file: resource must be refused", false);
        } catch (SecurityException expected) {
            // the rule
        }
    }

    private FopFactory factory(boolean embeddedOnly) {
        return new FopFactoryBuilder(dir.toURI()).setSvgEmbeddedResourcesOnly(embeddedOnly).build();
    }

    private BufferedImage render(FopFactory factory, String mime, final List<String> events) throws Exception {
        FOUserAgent ua = factory.newFOUserAgent();
        ua.getEventBroadcaster().addEventListener(new EventListener() {
            public void processEvent(Event event) {
                events.add(event.getEventID());
            }
        });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStream os = out;
        Fop fop = factory.newFop(mime, ua, os);
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.transform(new StreamSource(fo), new SAXResult(fop.getDefaultHandler()));
        os.close();
        if (MimeConstants.MIME_PDF.equals(mime)) {
            PDDocument pdf = Loader.loadPDF(out.toByteArray());
            try {
                return new PDFRenderer(pdf).renderImage(0, 1f);
            } finally {
                pdf.close();
            }
        }
        return ImageIO.read(new ByteArrayInputStream(out.toByteArray()));
    }

    private static int count(List<String> events, String suffix) {
        int n = 0;
        for (String id : events) {
            if (id.endsWith("." + suffix)) {
                n++;
            }
        }
        return n;
    }

    /** Rows 0 to 2 and 5 to 7 are drawn whatever the option; rows 3, 4, 8 and 9 only when files are admitted. */
    private static void assertRows(BufferedImage page, boolean filesAdmitted) {
        for (int row = 0; row < 2 * CASES; row++) {
            boolean drawn = drawn(page, row);
            boolean external = (row % CASES) >= 3;
            String what = "row " + row + (row < CASES ? " (inline)" : " (external graphic)");
            if (external && !filesAdmitted) {
                assertFalse(what + " must not be drawn", drawn);
            } else {
                assertTrue(what + " must be drawn", drawn);
            }
        }
    }

    private static boolean drawn(BufferedImage page, int row) {
        int rgb = page.getRGB(WIDTH / 2, row * ROW + ROW / 2) & 0xffffff;
        return rgb != 0xffffff;
    }
}
