package org.commcare.print;

import org.commcare.cases.entity.Entity;
import org.commcare.graph.model.GraphData;
import org.commcare.graph.util.GraphException;
import org.commcare.suite.model.DetailField;
import org.commcare.util.DetailFieldPrintInfo;

import java.io.Serializable;

/**
 * Created by amstone326 on 3/2/17.
 */

public class PrintableDetailField implements Serializable {

    private String displayString;
    private String fieldForm;

    public PrintableDetailField(DetailFieldPrintInfo printInfo) {
        DetailField field = printInfo.field;
        Entity entity = printInfo.entity;
        int fieldIndex = printInfo.index;

        this.fieldForm = field.getTemplateForm();
        String fieldAsString = entity.getFieldString(fieldIndex);

        if ("".equals(fieldAsString) || fieldAsString == null) {
            // this field can't be represented as a string
            if (isGraphDetailField()) {
                try {
                    Object evaluatedField = entity.getField(fieldIndex);
                    this.displayString = ((GraphData)evaluatedField).getGraphHTML("");
                } catch (GraphException e) {

                }
            } else {

            }
        } else {
            this.displayString = fieldAsString;
        }
    }

    private boolean isGraphDetailField() {
        return "graph".equals(fieldForm);
    }

}
