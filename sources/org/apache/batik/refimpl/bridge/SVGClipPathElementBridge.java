/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included with this distribution in  *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.batik.refimpl.bridge;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.io.StringReader;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.BridgeMutationEvent;
import org.apache.batik.bridge.ClipBridge;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.ObjectBoundingBoxViewport;
import org.apache.batik.bridge.Viewport;

import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.gvt.filter.Clip;
import org.apache.batik.gvt.filter.Filter;
import org.apache.batik.gvt.filter.GraphicsNodeRableFactory;

import org.apache.batik.parser.AWTTransformProducer;
import org.apache.batik.refimpl.gvt.AffineTransformSourceBoundingBox;
import org.apache.batik.refimpl.gvt.filter.ConcreteClipRable;

import org.apache.batik.util.SVGConstants;
import org.apache.batik.util.SVGUtilities;
import org.apache.batik.util.UnitProcessor;
import org.apache.batik.util.awt.geom.AffineTransformSource;
import org.apache.batik.util.awt.geom.CompositeAffineTransformSource;
import org.apache.batik.util.awt.geom.DefaultAffineTransformSource;
import org.apache.batik.util.awt.geom.TransformedShape;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.css.CSSPrimitiveValue;
import org.w3c.dom.css.CSSStyleDeclaration;
import org.w3c.dom.css.ViewCSS;

/**
 * A factory for the &lt;clipPath&gt; SVG element.
 *
 * @author <a href="mailto:Thierry.Kormann@sophia.inria.fr">Thierry Kormann</a>
 * @version $Id$
 */
public class SVGClipPathElementBridge implements ClipBridge, SVGConstants {

    /**
     * Returns the <tt>Shape</tt> referenced by the input element's
     * <tt>clip-path</tt> attribute.
     */
    public Clip createClip(BridgeContext bridgeContext,
                            GraphicsNode gn,
                            Element clipElement,
                            Element clipedElement) {
        CSSStyleDeclaration decl
            = bridgeContext.getViewCSS().getComputedStyle(clipElement, null);

        // Build the GVT tree that represents the clip path
        //
        // The silhouettes of the child elements are logically OR'd
        // together to create a single silhouette which is then used to
        // restrict the region onto which paint can be applied.
        //
        // The 'clipPath' element or any of its children can specify
        // property 'clip-path'.
        //
        Area area = new Area();
        GVTBuilder builder = bridgeContext.getGVTBuilder();
        Viewport oldViewport = bridgeContext.getCurrentViewport();
        bridgeContext.setCurrentViewport(new ObjectBoundingBoxViewport());
        for(Node child=clipElement.getFirstChild();
            child != null;
            child = child.getNextSibling()){
            if(child.getNodeType() == child.ELEMENT_NODE){
                Element e = (Element)child;
                GraphicsNode node
                    = builder.build(bridgeContext, e) ;
                if(node != null){
                      // Apply the child clip if any...
                    Area outline = new Area(node.getOutline());
                    Clip clip = node.getClip();
                    if (clip != null) {
                        Shape clipPath = clip.getClipPath();
                        if (clipPath != null) {
                            outline.subtract(new Area(clipPath));
                        }
                    }
                    area.add(outline);
                }
            }
        }

        // Compute the global matrix of this clipPath Element
        AffineTransform at = AWTTransformProducer.createAffineTransform
            (new StringReader(clipElement.getAttributeNS(null, ATTR_TRANSFORM)),
             bridgeContext.getParserFactory());

        String units = clipElement.getAttributeNS(null, ATTR_CLIP_PATH_UNITS);
        if (units.length() == 0) {
            units = VALUE_OBJECT_BOUNDING_BOX;
        }
        AffineTransformSource ats =
            SVGUtilities.convertAffineTransformSource(at, gn, units);
        Shape childrenClipPath = new TransformedShape(area, ats);

        //
        // Now clipPath represents the current clip path defined by the
        // children of the clipPath element in user space.
        //
        Shape clipPath = childrenClipPath;

        // Get the clip-path property of this clipPath Element in user space
        Clip clipElementClipPath =
            CSSUtilities.convertClipPath(clipElement, gn, bridgeContext);
        if (clipElementClipPath != null) {
            Area merge = new Area(clipPath);
            merge.subtract(new Area(clipElementClipPath.getClipPath()));
            clipPath = merge;
        }

        // Convert the Area to a path and apply the winding rule
        GeneralPath clip = new GeneralPath(clipPath);
        CSSPrimitiveValue v;
        v = (CSSPrimitiveValue)decl.getPropertyCSSValue(CLIP_RULE_PROPERTY);
        int wr = (CSSUtilities.rule(v) == CSSUtilities.RULE_NONZERO)
            ? GeneralPath.WIND_NON_ZERO
            : GeneralPath.WIND_EVEN_ODD;

        clip.setWindingRule(wr);
        bridgeContext.setCurrentViewport(oldViewport); // restore the viewport

        // OTHER PROBLEM: SHOULD TAKE MASK REGION INTO ACCOUNT
        Filter filter = gn.getFilter();
        if (filter == null) {
              // Make the initial source as a RenderableImage
            GraphicsNodeRableFactory gnrFactory
                = bridgeContext.getGraphicsNodeRableFactory();
            filter = gnrFactory.createGraphicsNodeRable(gn);
        }
        return new ConcreteClipRable(filter, clip);
    }

    public void update(BridgeMutationEvent evt) {
        // <!> FIXME : TODO
    }
}
