///**
// * 
// */
//package org.commcare.entity;
//
//import java.util.Hashtable;
//import java.util.Stack;
//
//import org.commcare.suite.model.Filter;
//import org.javarosa.core.model.instance.FormInstance;
//import org.javarosa.core.model.instance.TreeElement;
//import org.javarosa.core.model.utils.PreloadUtils;
//import org.javarosa.core.services.storage.EntityFilter;
//import org.javarosa.core.services.storage.Persistable;
//
///**
// * @author ctsims
// *
// */
//public abstract class FormInstanceLoader<E extends Persistable> {
//	
//	protected Hashtable<String,String> references; 
//	
//	public FormInstanceLoader(Hashtable<String,String> references) {
//		this.references = references; 
//	}
//	
//	public abstract void prepare(E e);
//	
//	public FormInstance loadInstance(FormInstance instance) {
//		Stack<TreeElement> stack = new Stack<TreeElement>();
//		stack.addElement(instance.getRoot());
//		while(!stack.empty()) {
//			TreeElement element = stack.pop();
//			
//			//Think of the children
//			for(int i = 0 ; i < element.getNumChildren(); ++i ){
//				stack.push(element.getChildAt(i));
//			}
//			
//			String reference = element.getAttributeValue(null, "reference");
//			if(reference!= null) {
//				String key = element.getAttributeValue(null, "field");
//				Object o = resolveReferenceData(reference, key);
//				element.setValue(PreloadUtils.wrapIndeterminedObject(o));
//			}
//		}
//		return instance;
//	}
//	
//	protected abstract EntityFilter<E> resolveFilter(final Filter filter, final FormInstance template);
//	
//	protected abstract Object resolveReferenceData(String reference, String key);
//}
