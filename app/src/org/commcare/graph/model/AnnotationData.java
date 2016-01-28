package org.commcare.graph.model;

/**
 * Data for an annotation, which is text drawn at a specified x, y coordinate on a graph.
 *
 * @author jschweers
 */
public class AnnotationData extends XYPointData {
    private String mAnnotation;

    public AnnotationData(String x, String y, String annotation) {
        super(x, y);
        mAnnotation = annotation;
    }

    public String getAnnotation() {
        return mAnnotation;
    }
}
