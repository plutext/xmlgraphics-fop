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
 * Apache FOP 2.11: hook page-master-by-content, which part of a page-sequence owns each page (fop/CR-017). See
 * README.md, "Changes from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.layoutmgr;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.xmlgraphics.util.QName;

import org.apache.fop.area.BodyRegion;
import org.apache.fop.fo.FONode;
import org.apache.fop.fo.FObj;
import org.apache.fop.fo.extensions.ExtensionElementMapping;
import org.apache.fop.fo.flow.Block;
import org.apache.fop.fo.flow.BlockContainer;
import org.apache.fop.fo.flow.ListBlock;
import org.apache.fop.fo.flow.table.Table;
import org.apache.fop.fo.pagination.Flow;
import org.apache.fop.fo.pagination.PageSequence;
import org.apache.fop.fo.pagination.PageSequenceMaster;
import org.apache.fop.fo.pagination.Region;
import org.apache.fop.fo.pagination.RepeatablePageMasterAlternatives;
import org.apache.fop.fo.pagination.SimplePageMaster;

/**
 * docx4j-fo-renderer hook {@code page-master-by-content} (fop/CR-017): a block-level FO carrying
 * {@code fox:page-sequence-master-reference} begins a part of its page-sequence, and the pages whose first
 * line lies in that part take their masters from the page-sequence-master it names, as a word processor
 * takes a page's margins and header from the section that owns its first line. This class finds the parts
 * in each element list and answers which part owns the content after a break. Without such an attribute
 * in the page-sequence it is inactive and changes nothing.
 */
public final class PageOwnership {

    private static final Log LOG = LogFactory.getLog(PageOwnership.class);

    /** The attribute that begins a part. */
    public static final QName ATTRIBUTE = new QName(ExtensionElementMapping.URI,
            "fox:page-sequence-master-reference");

    /**
     * A part: the content from a marked FO up to the next, and the alternatives its pages take their
     * masters from. Parts are told apart by their FO, so two parts naming one master are two parts.
     */
    public static final class Part {

        private final FObj fo;

        private final RepeatablePageMasterAlternatives alternatives;

        private Part(FObj fo, RepeatablePageMasterAlternatives alternatives) {
            this.fo = fo;
            this.alternatives = alternatives;
        }

        /**
         * The master a page of this part takes.
         * @param pageNumber the page's number in the page-sequence's numbering
         * @param first whether the page is the part's first: its first box opens the page
         * @param blank whether the page is blank
         * @return the master
         */
        SimplePageMaster masterFor(int pageNumber, boolean first, boolean blank) {
            boolean odd = (pageNumber % 2) != 0;
            SimplePageMaster spm = alternatives.getPageMasterFor(odd, first, blank);
            if (spm == null && blank) {
                spm = alternatives.getPageMasterFor(odd, first, false);
            }
            if (spm == null && first) {
                spm = alternatives.getPageMasterFor(odd, false, blank);
            }
            return spm;
        }

        /** {@inheritDoc} */
        public String toString() {
            return "Part[" + fo.getName() + ", " + fo.getForeignAttributes().get(ATTRIBUTE) + "]";
        }
    }

    private final PageSequence pageSequence;

    /** The valid marked FOs and their parts. */
    private Map<FObj, Part> parts;

    private boolean active;

    /** The part in force at the last committed break, where the next element list starts. */
    private Part partInForce;

    private List<? extends ListElement> list;
    private Part listStartPart;
    private final List<Integer> transitionIndex = new ArrayList<Integer>();
    private final List<Part> transitionPart = new ArrayList<Part>();

    /**
     * @param pageSequence the page-sequence laid out
     */
    PageOwnership(PageSequence pageSequence) {
        this.pageSequence = pageSequence;
    }

    /**
     * Finds and checks the marked FOs of the page-sequence's main flow, once.
     * @param referencePage the page-sequence's first page, whose body every part's masters must match
     */
    void initialize(Page referencePage) {
        if (parts != null) {
            return;
        }
        parts = new IdentityHashMap<FObj, Part>();
        Flow flow = pageSequence.getMainFlow();
        if (flow == null || referencePage == null) {
            return;
        }
        BodyRegion reference = referencePage.getPageViewport().getBodyRegion();
        collect(flow, flow, true, reference);
        active = !parts.isEmpty();
        if (active && LOG.isDebugEnabled()) {
            LOG.debug("page-master-by-content: " + parts.size() + " part(s) in the page-sequence");
        }
    }

    private void collect(FONode node, Flow flow, boolean ancestorsAccepted, BodyRegion reference) {
        FONode.FONodeIterator it = node.getChildNodes();
        if (it == null) {
            return;
        }
        while (it.hasNext()) {
            FONode child = it.next();
            if (!(child instanceof FObj)) {
                continue;
            }
            FObj fo = (FObj) child;
            String name = (String) fo.getForeignAttributes().get(ATTRIBUTE);
            if (name != null) {
                Part part = check(fo, name, ancestorsAccepted, reference);
                if (part != null) {
                    parts.put(fo, part);
                }
            }
            collect(fo, flow, ancestorsAccepted && (fo instanceof Block || fo instanceof BlockContainer),
                    reference);
        }
    }

    private Part check(FObj fo, String name, boolean ancestorsAccepted, BodyRegion reference) {
        if (!ancestorsAccepted || !(fo instanceof Block || fo instanceof BlockContainer
                || fo instanceof ListBlock || fo instanceof Table)) {
            LOG.warn("fox:page-sequence-master-reference=\"" + name + "\" on " + fo.getName()
                    + " is ignored: it applies to a block-level FO whose ancestors up to the flow are blocks"
                    + " or block-containers");
            return null;
        }
        PageSequenceMaster psm = pageSequence.getRoot().getLayoutMasterSet().getPageSequenceMaster(name);
        RepeatablePageMasterAlternatives alternatives = (psm == null) ? null : psm.getSoleUnboundedAlternatives();
        if (alternatives == null) {
            LOG.warn("fox:page-sequence-master-reference=\"" + name + "\" is ignored: it must name a"
                    + " page-sequence-master that is one unbounded repeatable-page-master-alternatives");
            return null;
        }
        String flowName = pageSequence.getMainFlow().getFlowName();
        for (SimplePageMaster spm : alternatives.getAlternativeMasters()) {
            Region body = spm.getRegion(FO_REGION_BODY);
            if (body == null || !flowName.equals(body.getRegionName())) {
                LOG.warn("fox:page-sequence-master-reference=\"" + name + "\" is ignored: master \""
                        + spm.getMasterName() + "\" has no region-body named for the flow \"" + flowName + "\"");
                return null;
            }
            BodyRegion candidate = new Page(spm, 1, "", false, false, false).getPageViewport().getBodyRegion();
            if (candidate.getColumnCount() != reference.getColumnCount()
                    || candidate.getColumnIPD() != reference.getColumnIPD()) {
                LOG.warn("fox:page-sequence-master-reference=\"" + name + "\" is ignored: master \""
                        + spm.getMasterName() + "\" differs from the page-sequence's in its body's width or"
                        + " column count");
                return null;
            }
        }
        return new Part(fo, alternatives);
    }

    private static final int FO_REGION_BODY = org.apache.fop.fo.Constants.FO_REGION_BODY;

    /** @return whether the page-sequence has any part, and so whether pages are owned at all */
    boolean isActive() {
        return active;
    }

    /**
     * Records where each part begins in a new element list. A part begins at the first box whose
     * position chain reaches its marked FO, or, for a marked FO that produces no box, at the next box
     * after its elements. The list starts in the part in force at the last committed break.
     * @param elements the element list
     */
    void scan(List<? extends ListElement> elements) {
        if (!active) {
            return;
        }
        list = elements;
        listStartPart = partInForce;
        transitionIndex.clear();
        transitionPart.clear();
        Part current = partInForce;
        Part pending = null;
        for (int i = 0; i < elements.size(); i++) {
            ListElement element = elements.get(i);
            Part found = partOf(element.getPosition());
            if (element.isBox()) {
                Part next = (found != null) ? found : pending;
                if (next != null && next != current) {
                    transitionIndex.add(i);
                    transitionPart.add(next);
                    current = next;
                }
                pending = null;
            } else if (found != null && found != current) {
                pending = found;
            }
        }
        partInForce = current;
    }

    /** The outermost part whose marked FO the position chain reaches, if any. */
    private Part partOf(Position position) {
        for (Position p = position; p != null; ) {
            LayoutManager lm = p.getLM();
            if (lm != null) {
                FObj fo = lm.getFObj();
                if (fo != null) {
                    Part part = parts.get(fo);
                    if (part != null) {
                        return part;
                    }
                }
            }
            Position inner = p.getPosition();
            if (inner == p) {
                break;
            }
            p = inner;
        }
        return null;
    }

    private int nextBox(int position) {
        int i = position + 1;
        while (i < list.size() && !list.get(i).isBox()) {
            i++;
        }
        return i;
    }

    private int lastTransitionAtOrBefore(int index) {
        int found = -1;
        for (int t = 0; t < transitionIndex.size() && transitionIndex.get(t) <= index; t++) {
            found = t;
        }
        return found;
    }

    /**
     * The part owning the content after a break at an element of the current list.
     * @param position the break's element index; -1 for the list's start
     * @return the part, or null where no part is in force (the page-sequence's own masters)
     */
    Part ownerAfter(int position) {
        if (!active || list == null) {
            return null;
        }
        int t = lastTransitionAtOrBefore(nextBox(position));
        return (t >= 0) ? transitionPart.get(t) : listStartPart;
    }

    /**
     * Whether the content after a break at an element of the current list is the first of its part,
     * so the page it opens is the part's first page.
     * @param position the break's element index; -1 for the list's start
     * @return true if a part begins exactly there
     */
    boolean opensPart(int position) {
        if (!active || list == null) {
            return false;
        }
        int box = nextBox(position);
        int t = lastTransitionAtOrBefore(box);
        return t >= 0 && transitionIndex.get(t) == box;
    }

    /**
     * Commits the content of the current list up to an element: the next list, read again from after
     * it (a float's restart), starts in the part in force there.
     * @param position the last committed element's index
     */
    void commitTo(int position) {
        if (!active || list == null) {
            return;
        }
        int t = lastTransitionAtOrBefore(position);
        partInForce = (t >= 0) ? transitionPart.get(t) : listStartPart;
    }
}
