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
 * Apache FOP 2.11: the option svg-embedded-resources-only, an SVG graphic loading only the resources embedded in it
 * (fop/CR-028). See README.md,
 * "Changes from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.apps;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.apache.xmlgraphics.image.loader.ImageManager;
import org.apache.xmlgraphics.image.loader.impl.AbstractImageSessionContext.FallbackResolver;
import org.apache.xmlgraphics.io.ResourceResolver;

import org.apache.fop.apps.io.InternalResourceResolver;
import org.apache.fop.configuration.Configuration;
import org.apache.fop.fonts.FontManager;
import org.apache.fop.layoutmgr.LayoutManagerMaker;

/**
 * The configuration data for a {@link FopFactory} instance.
 */
// TODO: Make this interface and any implementations of this interface package private. Though
// they are used by classes that are considered the public API, this object doesn't need to be a
// part of the API. Why would a user care how the internal objects are passed around? They shouldn't.
public interface FopFactoryConfig {

    /**
     * Defines if FOP should allow breaks at positions deemed invalid
     */
    boolean DEFAULT_LEGACY_INVALID_BREAK_POSITION = false;

    /** Defines if FOP should use an alternative rule to determine text indents */
    boolean DEFAULT_BREAK_INDENT_INHERITANCE = false;

    /** Defines if FOP should validate the user config strictly */
    boolean DEFAULT_STRICT_USERCONFIG_VALIDATION = true;

    /** Defines if FOP should use strict validation for FO and user config */
    boolean DEFAULT_STRICT_FO_VALIDATION = true;

    /** Defines the default page-width */
    String DEFAULT_PAGE_WIDTH = "8.26in";

    /** Defines the default page-height */
    String DEFAULT_PAGE_HEIGHT = "11in";

    /** Defines the default source resolution (72dpi) for FOP */
    float DEFAULT_SOURCE_RESOLUTION = 72.0f; //dpi

    /** Defines the default target resolution (72dpi) for FOP */
    float DEFAULT_TARGET_RESOLUTION = 72.0f; //dpi

    /**
     * Defines if FOP should use the parent's layout manager for the content area IPD
     */
    boolean DEFAULT_USE_PARENT_IPD_IMAGE_SCALING = false;

    /**
     * Whether an SVG graphic may load only the external resources embedded in it (fop/CR-028). On by default in
     * docx4j-fo-renderer; Apache FOP applies Batik's same-host rule.
     */
    boolean DEFAULT_SVG_EMBEDDED_RESOURCES_ONLY = true;

    /**
     * Whether accessibility features are switched on.
     *
     * @return true if accessibility features have been requested
     */
    boolean isAccessibilityEnabled();

    boolean isStaticRegionsPerPageForAccessibility();

    boolean isKeepEmptyTags();

    boolean isImageCacheEnabled();

    /**
     * Returns the overriding LayoutManagerMaker instance, if any.
     * @return the overriding LayoutManagerMaker or null
     */
    LayoutManagerMaker getLayoutManagerMakerOverride();

    /**
     * The URI resolver used through-out FOP for controlling all file access.
     *
     * @return the URI resolver
     */
    ResourceResolver getResourceResolver();

    /**
     * The base URI from which URIs are resolved against.
     *
     * @return the base URI
     */
    URI getBaseURI();

    /**
     * Returns whether FOP is strictly validating input XSL
     * @return true of strict validation turned on, false otherwise
     */
    boolean validateStrictly();

    /**
     * Is the user configuration to be validated?
     * @return if the user configuration should be validated
     */
    boolean validateUserConfigStrictly();

    /**
     * @return true if the indent inheritance should be broken when crossing reference area
     * boundaries (for more info, see the javadoc for the relative member variable)
     */
    boolean isBreakIndentInheritanceOnReferenceAreaBoundary();

    /** @return the resolution for resolution-dependent input */
    float getSourceResolution();

    /** @return the resolution for resolution-dependent output */
    float getTargetResolution();

    /**
     * Gets the default page-height to use as fallback,
     * in case page-height="auto"
     *
     * @return the page-height, as a String
     */
    String getPageHeight();

    /**
     * Gets the default page-width to use as fallback,
     * in case page-width="auto"
     *
     * @return the page-width, as a String
     */
    String getPageWidth();

    /** @return the set of namespaces that are ignored by FOP */
    Set<String> getIgnoredNamespaces();

    /**
     * Indicates whether a namespace URI is on the ignored list.
     * @param namespace the namespace URI
     * @return true if the namespace is ignored by FOP
     */
    boolean isNamespaceIgnored(String namespace);

    /**
     * Returns the Avalon {@link Configuration} object.
     *
     * @return the Avalon config object
     */
    Configuration getUserConfig();

    /** @see org.apache.fop.render.RendererFactory#isRendererPreferred() */
    boolean preferRenderer();

    /**
     * Returns the font manager.
     * @return the font manager
     */
    FontManager getFontManager();

    /**
     * Returns the image manager.
     * @return the image manager
     */
    ImageManager getImageManager();

    boolean isComplexScriptFeaturesEnabled();

    boolean isTableBorderOverpaint();

    /**
     * Whether an SVG graphic, inline or external, may load only the external resources embedded in it as
     * {@code data:} URLs (fop/CR-028): a {@code file:}, {@code http:} or other reference from {@code image},
     * {@code use}, {@code tref}, a font or a stylesheet is refused and the graphic is not drawn. When false, Batik's
     * own rule applies: a resource on the document's host, and any resource at all for a document whose base URI
     * is a file, since both hosts are then null.
     * @return true if SVG external resources are restricted to {@code data:} URLs
     */
    boolean isSvgEmbeddedResourcesOnly();

    boolean isSimpleLineBreaking();

    boolean isSkipPagePositionOnlyAllowed();

    boolean isLegacySkipPagePositionOnly();

    boolean isLegacyLastPageChangeIPD();

    boolean isLegacyFoWrapper();

    boolean isLegacyInvalidBreakPosition();

    boolean isUseParentIPDImageScaling();

    /** @return the hyphenation pattern names */
    Map<String, String> getHyphenationPatternNames();

    InternalResourceResolver getHyphenationResourceResolver();

    /**
     * Controls the mechanisms that are used in the event that {@link javax.xml.transform.Source}
     * used for resources couldn't be read.
     * @return the fallback resolver
     */
    FallbackResolver getFallbackResolver();
}
