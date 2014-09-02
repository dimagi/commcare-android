/**
 * 
 */
package org.commcare.android.models;

import org.commcare.suite.model.DetailField;
import org.commcare.suite.model.Text;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.xpath.XPathException;



/**
 * @author ctsims
 *
 */
public class AsyncEntity<T> extends Entity<T>{
	
	DetailField[] fields;
	Object[] data;
	String[] sortData;
	EvaluationContext context;
	
	public AsyncEntity(DetailField[] fields, EvaluationContext ec, T t) {
		super(t);
		this.fields = fields;
		this.data = new Object[fields.length];
		this.sortData = new String[fields.length];
		this.context = ec;
	}
	
	public Object getField(int i) {
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
}
