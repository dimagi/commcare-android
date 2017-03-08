package org.commcare.print;

import org.apache.commons.lang3.StringUtils;
import org.commcare.cases.entity.Entity;
import org.commcare.graph.model.GraphData;
import org.commcare.graph.util.GraphException;
import org.commcare.suite.model.DetailField;
import org.commcare.util.DetailFieldPrintInfo;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.Serializable;

/**
 * Created by amstone326 on 3/2/17.
 */
public class PrintableDetailField implements Serializable {

    private String valueString;
    private String fieldForm;

    public PrintableDetailField(DetailFieldPrintInfo printInfo) {
        DetailField field = printInfo.field;
        Entity entity = printInfo.entity;
        int fieldIndex = printInfo.index;

        this.fieldForm = field.getTemplateForm();
        String fieldAsString = entity.getFieldString(fieldIndex);
        if ("".equals(fieldAsString) || fieldAsString == null) {
            // this field can't be automatically represented as a string
            if (isGraphDetailField()) {
                parseGraphPrintData(entity, fieldIndex);
            } else {
                // this field is of some other form for which printing is currently not supported
                this.valueString = "";
            }
        } else {
            this.valueString = fieldAsString;
        }
    }

    private void parseGraphPrintData(Entity entity, int fieldIndex) {
        try {
            Object evaluatedField = entity.getField(fieldIndex);
            String fullGraphHtml = ((GraphData)evaluatedField).getGraphHTML("");
            this.valueString = fullGraphHtml;
        } catch (GraphException e) {
            this.valueString = "";
        }
    }

    /*private static String getHtmlStringForBody(String html) {
        return StringUtils.substringBetween(html, "<body>", "</body>");
    }*/

    public String getFormattedValueString() {
        if (isGraphDetailField()) {
            return createIframeForGraphHtml(valueString);
        } else {
            return valueString;
        }
    }

    private static String createIframeForGraphHtml(String fullGraphHtml) {
        return "<iframe srcdoc=\"" + scrubHtmlStringForUseAsAttribute(fullGraphHtml)
                + "\" height=\"500\" width=\"500\"></iframe>";
    }

    private static String scrubHtmlStringForUseAsAttribute(String htmlString) {
        return htmlString.replace("\"", "\'").replace("&lt;", "<").replace("&gt;", ">").replace("<br>", "");
    }

    private boolean isGraphDetailField() {
        return "graph".equals(fieldForm);
    }

    public boolean isPrintable() {
        return !"".equals(valueString);
    }

    /*public Elements getHtmlHeadElementsToAppend() {
        if (isGraphDetailField()) {
            Document graphDoc = Jsoup.parse(this.fullGraphHtml);
            Element graphHead = graphDoc.getElementsByTag("head").get(0);
            return graphHead.children();
        } else {
            return new Elements();
        }
    }*/

}
