///**
// * 
// */
//package org.commcare.entity;
//
//import java.util.Vector;
//
//import org.commcare.suite.model.Detail;
//import org.commcare.suite.model.Entry;
//import org.commcare.suite.model.Text;
//import org.javarosa.core.model.instance.FormInstance;
//import org.javarosa.core.services.locale.Localization;
//import org.javarosa.core.services.storage.EntityFilter;
//import org.javarosa.core.services.storage.Persistable;
//import org.javarosa.entity.model.Entity;
//import org.javarosa.entity.util.StackedEntityFilter;
//
///**
// * @author ctsims
// *
// */
//public class CommCareEntity<E extends Persistable> extends Entity<E> {
//
//	Entry e;
//	Detail shortDetail;
//	Detail longDetail;
//	FormInstanceLoader<E> loader;
//	String[] shortText;
//	
//	public CommCareEntity(Entry e, Detail shortDetail, Detail longDetail, FormInstanceLoader<E> loader) {
//		this.e = e;
//		this.shortDetail = shortDetail;
//		this.longDetail = longDetail;
//		this.loader = loader;
//	}
//
//	/* (non-Javadoc)
//	 * @see org.javarosa.entity.model.Entity#entityType()
//	 */
//	public String entityType() {
//		return shortDetail.getTitle().evaluate();
//	}
//
//	/* (non-Javadoc)
//	 * @see org.javarosa.entity.model.Entity#factory()
//	 */
//	public Entity<E> factory() {
//		return new CommCareEntity<E>(e,shortDetail,longDetail, loader);
//	}
//
//	/* (non-Javadoc)
//	 * @see org.javarosa.entity.model.Entity#getHeaders(boolean)
//	 */
//	public String[] getHeaders(boolean detailed) {
//		Text[] text;
//		if(!detailed) {
//			text = shortDetail.getHeaders();
//		} else{
//			text = longDetail.getHeaders();
//		}
//		
//		String[] output = new String[text.length];
//		for(int i = 0 ; i < output.length ; ++i) {
//			output[i] = text[i].evaluate();
//		}
//		return output;
//	}
//	
//	/* (non-Javadoc)
//	 * @see org.javarosa.patient.select.activity.IEntity#matchID(java.lang.String)
//	 */
//	public boolean match (String key) {
//		key = key.toLowerCase();
//		String[] fields = this.getShortFields();
//		for(int i = 0; i < fields.length; ++i) {
//			//Skip sorting by this key if it's not a normal string
//			if("image".equals(shortDetail.getTemplateForms()[i])) {
//				continue;
//			}
//			if(fields[i].toLowerCase().startsWith(key)) {
//				return true;
//			}
//		}
//		return false;
//	}
//	
//	/*
//	 * (non-Javadoc)
//	 * @see org.javarosa.entity.model.Entity#getForms(boolean)
//	 */
//	public String[] getForms(boolean header) {
//		return header ? shortDetail.getHeaderForms() : shortDetail.getTemplateForms();
//	}
//
//	/* (non-Javadoc)
//	 * @see org.javarosa.entity.model.Entity#getLongFields(org.javarosa.core.services.storage.Persistable)
//	 */
//	public String[] getLongFields(E e) {
//		loader.prepare(e);
//		FormInstance specificInstance = loader.loadInstance(longDetail.getInstance());
//		Text[] text = longDetail.getTemplates();
//		String[] output = new String[text.length];
//		for(int i = 0 ; i < output.length ; ++i) {
//			output[i] = text[i].evaluate(specificInstance, null);
//		}
//		return output;
//	}
//
//	/* (non-Javadoc)
//	 * @see org.javarosa.entity.model.Entity#getShortFields()
//	 */
//	public String[] getShortFields() {
//		return shortText;
//	}
//	
//	public int[] getStyleHints (boolean header) {
//		if(header) {
//			return shortDetail.getHeaderSizeHints();
//		} else {
//			return shortDetail.getTemplateSizeHints();
//		}
//	}
//
//	/* (non-Javadoc)
//	 * @see org.javarosa.entity.model.Entity#loadEntity(org.javarosa.core.services.storage.Persistable)
//	 */
//	protected void loadEntity(E entity) {
//		loader.prepare(entity);
//		FormInstance instance = loader.loadInstance(shortDetail.getInstance());
//		loadShortText(instance);
//	}
//	
//	public EntityFilter<? super E> getFilter () {
//		return new StackedEntityFilter<E>(loader.resolveFilter(shortDetail.getFilter(), shortDetail.getInstance()), new InstanceEntityFilter<E>(loader, shortDetail.getFilter(), shortDetail.getInstance()));
//	}
//	
//	private void loadShortText(FormInstance instance) {
//		Text[] text = shortDetail.getTemplates();
//		shortText = new String[text.length];
//		for(int i = 0 ; i < shortText.length ; ++i) {
//			shortText[i] = text[i].evaluate(instance, null);
//		}
//	}
//	
//	/*
//	 * (non-Javadoc)
//	 * @see org.javarosa.entity.model.Entity#getSortFields()
//	 */
//	public String[] getSortFields () {
//		String[] names = getSortFieldNames();
//		String[] ret = new String[names.length];
//		ret[0] = "DEFAULT";
//		for(int i = 1 ; i < ret.length ; ++i ) {
//			ret[i] = String.valueOf(i);
//		}
//		return ret;
//	}
//	
//	/*
//	 * (non-Javadoc)
//	 * @see org.javarosa.entity.model.Entity#getSortFieldNames()
//	 */
//	public String[] getSortFieldNames () {
//		Vector<String> fields = new Vector<String>();
//		fields.addElement(Localization.get("case.id"));
//		for(String s : getHeaders(false)) {
//			if(s == null || "".equals(s)) {
//				continue;
//			}
//			fields.addElement(s);
//		}
//		String[] ret = new String[fields.size()];
//		for(int i = 0 ; i < ret.length ; ++i) {
//			ret[i] = fields.elementAt(i);
//		}
//		return ret;
//	}
//	
//	/*
//	 * (non-Javadoc)
//	 * @see org.javarosa.entity.model.Entity#getSortKey(java.lang.String)
//	 */
//	public Object getSortKey (String fieldKey) {
//		if (fieldKey.equals("DEFAULT")) {
//			return new Integer(this.getRecordID());
//		} else {
//			try{
//				return getShortFields()[Integer.valueOf(fieldKey).intValue()];
//			} catch(NumberFormatException nfe) {
//				nfe.printStackTrace();
//				throw new RuntimeException("Invalid sort key in CommCare Entity: " + fieldKey);
//			}
//		}
//	}
//}
