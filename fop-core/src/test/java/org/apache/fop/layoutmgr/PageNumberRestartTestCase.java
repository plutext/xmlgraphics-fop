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
 * Apache FOP 2.11: a test of hook page-number-restart at 0 (fop/CR-017.2). See README.md, "Changes from Apache FOP
 * 2.11". */

/* $Id$ */

package org.apache.fop.layoutmgr;

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
 * A part restarting its page numbers at 0 (fox:page-number-restart="0") opens page 2 here: the page prints 0
 * where the user agent allows a page numbered 0 (hook page-number-zero), and 1 otherwise, as
 * initial-page-number's error recovery gives.
 */
public class PageNumberRestartTestCase {

    private static final String FO = "<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\""
            + " xmlns:fox=\"http://xmlgraphics.apache.org/fop/extensions\"><fo:layout-master-set>"
            + "<fo:simple-page-master master-name=\"p\" page-width=\"300pt\" page-height=\"200pt\" margin=\"20pt\">"
            + "<fo:region-body/></fo:simple-page-master>"
            + "<fo:page-sequence-master master-name=\"m\"><fo:repeatable-page-master-alternatives>"
            + "<fo:conditional-page-master-reference master-reference=\"p\"/>"
            + "</fo:repeatable-page-master-alternatives></fo:page-sequence-master></fo:layout-master-set>"
            + "<fo:page-sequence master-reference=\"m\"><fo:flow flow-name=\"xsl-region-body\">"
            + "<fo:block>cover</fo:block>"
            + "<fo:block break-before=\"page\" fox:page-sequence-master-reference=\"m\""
            + " fox:page-number-restart=\"0\">first</fo:block>"
            + "<fo:block break-before=\"page\">second</fo:block>"
            + "</fo:flow></fo:page-sequence></fo:root>";

    private static List<String> pageNumbers(boolean zeroAllowed) throws Exception {
        FopFactory factory = FopFactory.newInstance(new File(".").toURI());
        FOUserAgent ua = factory.newFOUserAgent();
        ua.setPageNumberZeroAllowed(zeroAllowed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Fop fop = factory.newFop(MimeConstants.MIME_FOP_AREA_TREE, ua, out);
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.transform(new StreamSource(new ByteArrayInputStream(FO.getBytes(StandardCharsets.UTF_8))),
                new SAXResult(fop.getDefaultHandler()));
        List<String> numbers = new ArrayList<String>();
        Matcher m = Pattern.compile("<pageViewport [^>]*formatted-nr=\"([^\"]*)\"")
                .matcher(new String(out.toByteArray(), StandardCharsets.UTF_8));
        while (m.find()) {
            numbers.add(m.group(1));
        }
        return numbers;
    }

    @Test
    public void testRestartAtZeroIsOneByDefault() throws Exception {
        assertEquals("[1, 1, 2]", pageNumbers(false).toString());
    }

    @Test
    public void testRestartAtZeroIsKeptWhenAllowed() throws Exception {
        assertEquals("[1, 0, 1]", pageNumbers(true).toString());
    }
}
