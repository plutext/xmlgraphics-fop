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

/* Modified by Plutext Pty Ltd for the docx4j FO renderer (docx4j-fo-renderer), a modified distribution derived from
 * Apache FOP 2.11: the option svg-embedded-resources-only, external resources other than data: URLs refused
 * (fop/CR-028). See README.md,
 * "Changes from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.svg;

import java.awt.Dimension;
import java.awt.geom.AffineTransform;
import java.awt.geom.Dimension2D;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import org.apache.batik.bridge.EmbededExternalResourceSecurity;
import org.apache.batik.bridge.ExternalResourceSecurity;
import org.apache.batik.bridge.FontFamilyResolver;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.util.ParsedURL;

import org.apache.xmlgraphics.util.UnitConv;

/**
 * A simple SVG user agent.
 * This is an implementation of the Batik SVG user agent. It ignores any message output sent
 * by Batik.
 */
public class SimpleSVGUserAgent extends UserAgentAdapter {

    private AffineTransform currentTransform;

    private final FontFamilyResolver fontFamilyResolver;

    /** Option svg-embedded-resources-only (fop/CR-028): see {@link #setEmbeddedResourcesOnly(boolean)}. */
    private boolean embeddedResourcesOnly;

    /**
     * Creates a new user agent.
     * @param at the current transform
     */
    public SimpleSVGUserAgent(AffineTransform at, FontFamilyResolver fontFamilyResolver,
                              float sourceResolution) {
        this.fontFamilyResolver = fontFamilyResolver;
        currentTransform = at;
        setSourceResolution(sourceResolution);
    }

    /**
     * Returns a customized the pixel to mm factor.
     * @return the pixel unit to millimeter conversion factor
     */
    public float getPixelUnitToMillimeter() {
        return UnitConv.IN2MM / getSourceResolution();
    }

    /**
     * @return medium font size adapted to the resolution
     */
    public float getMediumFontSize() {
        return 12f * UnitConv.IN2MM / (UnitConv.IN2PT * getPixelUnitToMillimeter());
    }

    /**
     * Returns the language settings.
     * @return the languages supported
     */
    public String getLanguages() {
        return "en"; // userLanguages;
    }

    /**
     * Returns the media type for this rendering.
     * @return the media for FO documents is "print"
     */
    public String getMedia() {
        return "print";
    }

    /**
     * Returns the user stylesheet URI.
     * @return null if no user style sheet was specified.
     */
    public String getUserStyleSheetURI() {
        return null; // userStyleSheetURI;
    }


    private static final String XML_PARSER_CLASS_NAME;

    static {
        String result;
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            result = factory.newSAXParser().getXMLReader().getClass().getName();
        } catch (SAXException e) {
            result = null;
        } catch (ParserConfigurationException e) {
            result = null;
        }
        XML_PARSER_CLASS_NAME = result;
    }

    /**
     * Returns the class name of the XML parser.
     * @return the XML parser class name
     */
    public String getXMLParserClassName() {
        return XML_PARSER_CLASS_NAME;
    }

    /**
     * Is the XML parser validating.
     * @return true if the XML parser is validating
     */
    public boolean isXMLParserValidating() {
        return false;
    }

    /**
     * Get the transform of the SVG document.
     * @return the transform
     */
    public AffineTransform getTransform() {
        return currentTransform;
    }

    /** {@inheritDoc} */
    public void setTransform(AffineTransform at) {
        this.currentTransform = at;
    }

    /**
     * Get the default viewport size for an SVG document.
     * This returns a default value of 100x100.
     * @return the default viewport size
     */
    public Dimension2D getViewportSize() {
        return new Dimension(100, 100);
    }

    @Override
    public FontFamilyResolver getFontFamilyResolver() {
        return fontFamilyResolver;
    }

    /**
     * Sets whether the SVG may load only the external resources embedded in it as {@code data:} URLs
     * (fop/CR-028). Batik's default rule, applied otherwise, admits any resource on the document's host, and for a
     * document whose URL is a file that is any file on the machine, since the host of a {@code file:} URL is null
     * on both sides: an {@code image}, {@code use} or {@code tref} naming {@code file:///etc/passwd} is read into
     * the output.
     * @param embeddedOnly true to refuse every external resource that is not a {@code data:} URL
     */
    public void setEmbeddedResourcesOnly(boolean embeddedOnly) {
        this.embeddedResourcesOnly = embeddedOnly;
    }

    /**
     * @return whether external resources are restricted to {@code data:} URLs (fop/CR-028)
     */
    public boolean isEmbeddedResourcesOnly() {
        return embeddedResourcesOnly;
    }

    /** {@inheritDoc} */
    @Override
    public ExternalResourceSecurity getExternalResourceSecurity(ParsedURL resourceURL, ParsedURL docURL) {
        if (embeddedResourcesOnly) {
            return new EmbededExternalResourceSecurity(resourceURL);
        }
        return super.getExternalResourceSecurity(resourceURL, docURL);
    }

}

