///**
// * 
// */
//package org.commcare.entity;
//
//import java.util.Hashtable;
//
//import org.commcare.suite.model.Filter;
//import org.commcare.suite.model.Text;
//import org.javarosa.core.model.instance.FormInstance;
//import org.javarosa.core.services.storage.EntityFilter;
//import org.javarosa.core.services.storage.Persistable;
//import org.javarosa.xpath.parser.XPathSyntaxException;
//
///**
// * @author ctsims
// *
// */
//public class InstanceEntityFilter<E extends Persistable> extends EntityFilter<E> {
//
//	private FormInstanceLoader loader; 
//	private Filter filter;
//	private FormInstance template;
//	
//	public InstanceEntityFilter(FormInstanceLoader<E> loader, Filter filter, FormInstance template) {
//		this.loader = loader;
//		this.filter = filter;
//		this.template = template;
//	}
//	
//	/* (non-Javadoc)
//	 * @see org.javarosa.core.services.storage.EntityFilter#matches(java.lang.Object)
//	 */
//	public boolean matches(E e) {
//		if(filter.getRaw() != null) {
//			loader.prepare(e);
//				
//			try {
//				return "t".equals(Text.XPathText("if(" + filter.getRaw() + ",'t','f')",new Hashtable<String, Text>()).evaluate(loader.loadInstance(template), null));
//			} catch (XPathSyntaxException ex) {
//				ex.printStackTrace();
//				return true;
//			}
//		}
//		return true;
//	}
//
//}
