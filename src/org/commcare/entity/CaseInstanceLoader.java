///**
// * 
// */
//package org.commcare.entity;
//
//import java.util.Hashtable;
//
//import org.commcare.android.models.Case;
//import org.commcare.suite.model.Filter;
//import org.javarosa.core.model.instance.FormInstance;
//import org.javarosa.core.services.storage.EntityFilter;
//
///**
// * @author ctsims
// *
// */
//public class CaseInstanceLoader extends FormInstanceLoader<Case> {
//	Case c;
//	CasePreloadHandler p;
//	
//	public CaseInstanceLoader(Hashtable<String,String> references) {
//		super(references);
//	}
//	public void prepare(Case c) {
//		this.c = c;
//		p = new CasePreloadHandler(c);
//	}
//	
//	protected Object resolveReferenceData(String reference, String key) {
//		if(references.get(reference).toLowerCase().equals("case")) {
//			return p.handlePreload(key);
//		} else {
//			return null;
//		}
//	}
//	
//	protected EntityFilter<Case> resolveFilter(final Filter filter, final FormInstance template) {
//		
//		return new EntityFilter<Case> () {
//			
//			public int preFilter(int id, Hashtable<String, Object> metaData) {
//				// this apparently isn't supported yet
//				if(metaData == null) {
//					return EntityFilter.PREFILTER_FILTER;
//				}
//				if(filter.isEmpty()) {
//					return EntityFilter.PREFILTER_FILTER;
//				} else {
//					if(filter.paramSet(Filter.TYPE)) {
//						if(!metaData.get("case-type").equals(filter.getParam(Filter.TYPE))) {
//							return EntityFilter.PREFILTER_EXCLUDE;
//						}
//					}
//				}
//				return EntityFilter.PREFILTER_FILTER;
//			}
//
//			public boolean matches(Case c) {
//				
//				if(filter.isEmpty()) {
//					return !c.isClosed();
//				} else {
//					
//					//Apparently meta data isn't supported yet, so we have to do this here, too...
//					if(filter.paramSet(Filter.TYPE)) {
//						if(!c.getTypeId().equals(filter.getParam(Filter.TYPE))) {
//							return false;
//						}
//					}
//					
//					if(c.isClosed()) {
//						if(filter.paramSet(Filter.SHOW_CLOSED)) {
//							return new Boolean(true).toString().equals(filter.getParam(Filter.SHOW_CLOSED));
//						} else {
//							return false;
//						}
//					}
//					
//					//TODO: Only admin or user
//					return true;
//				}
//			}
//		};
//	}
//
//}
