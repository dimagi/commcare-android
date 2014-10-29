/**
 * 
 */
package org.commcare.android.models;

import java.util.Enumeration;
import java.util.Hashtable;

import org.commcare.suite.model.DetailField;
import org.commcare.suite.model.Text;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.xpath.XPathException;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathFuncExpr;



/**
 * @author ctsims
 *
 */
public class AsyncEntity<T> extends Entity<T>{
    
    DetailField[] fields;
    Object[] data;
    String[] sortData;
    EvaluationContext context;
    Hashtable<String, XPathExpression> mVariableDeclarations;
    boolean mVariableContextLoaded = false;
    
    public AsyncEntity(DetailField[] fields, EvaluationContext ec, T t, Hashtable<String, XPathExpression> variables) {
        super(t);
        this.fields = fields;
        this.data = new Object[fields.length];
        this.sortData = new String[fields.length];
        this.context = ec;
        this.mVariableDeclarations = variables;
    }
    
    private void loadVariableContext() {
        if(!mVariableContextLoaded) {
            //These are actually in an ordered hashtable, so we can't just get the keyset, since it's
            //in a 1.3 hashtable equivalent
            for(Enumeration<String> en = mVariableDeclarations.keys(); en.hasMoreElements();) {
                String key = en.nextElement();
                context.setVariable(key, XPathFuncExpr.unpack(mVariableDeclarations.get(key).eval(context)));
            }
            mVariableContextLoaded = true;
        } 
    }
    
    public Object getField(int i) {
        loadVariableContext();
        if(data[i] == null) {
            try {
                data[i] = fields[i].getTemplate().evaluate(context);
            } catch(XPathException xpe) {
                xpe.printStackTrace();
                data[i] = "<invalid xpath: " + xpe.getMessage() + ">";
            }
        }
        return data[i];
    }
    
    public String getSortField(int i) {
        loadVariableContext();
        if(sortData[i] == null) {
            try {
                Text sortText = fields[i].getSort();
                if(sortText == null) {
                    sortData[i] = getFieldString(i);
                } else {
                    sortData[i] = sortText.evaluate(context);
                }
            } catch(XPathException xpe) {
                xpe.printStackTrace();
                sortData[i] = "<invalid xpath: " + xpe.getMessage() + ">";
            }
        }
        return sortData[i];
    }

    public int getNumFields() {
        return fields.length;
    }
    
    /**
     * @param i index of field
     * @return True iff the given field is relevant and has a non-blank value.
     */
    public boolean isValidField(int i) {
        //FIX THIS
        return true;
    }
    
    public T getElement() {
        return t;
    }
    
    public Object[] getData(){
        for(int i = 0; i < this.getNumFields() ; ++i){
            this.getField(i);
        }
        return data;
    }
    
    public String [] getBackgroundData(){
        String[] backgroundData = new String[this.getNumFields()];
        for(int i = 0; i < this.getNumFields() ; ++i){ 
            backgroundData[i] = "";
        }
        return backgroundData;
    }
}
