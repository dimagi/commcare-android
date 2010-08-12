/**
 * 
 */
package org.commcare.entity;

import java.util.Hashtable;

import org.commcare.suite.model.Filter;
import org.commcare.suite.model.Text;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.storage.EntityFilter;
import org.javarosa.xpath.parser.XPathSyntaxException;

/**
 * @author ctsims
 *
 */
public class InstanceEntityFilter extends EntityFilter<FormInstance> {

	private Filter filter;
	
	public InstanceEntityFilter(Filter filter) {
		this.filter = filter;
	}
	
	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.EntityFilter#matches(java.lang.Object)
	 */
	public boolean matches(FormInstance instance) {
		if(filter.getRaw() != null) {
			try {
				return "t".equals(Text.XPathText("if(" + filter.getRaw() + ",'t','f')",new Hashtable<String, Text>()).evaluate(instance, null));
			} catch (XPathSyntaxException ex) {
				ex.printStackTrace();
				return true;
			}
		}
		return true;
	}
}
