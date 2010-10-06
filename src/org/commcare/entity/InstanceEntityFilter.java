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
	Text xpathText; 
	
	public InstanceEntityFilter(Filter filter) {
		this.filter = filter;
		try {
			xpathText = Text.XPathText("if(" + filter.getRaw() + ",'t','f')",new Hashtable<String, Text>());
		} catch (XPathSyntaxException ex) {
			ex.printStackTrace();
		}
	}
	
	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.EntityFilter#matches(java.lang.Object)
	 */
	public boolean matches(FormInstance instance) {
		if(filter.getRaw() != null) {
			return "t".equals(xpathText.evaluate(instance, null));
		}
		return true;
	}
}
