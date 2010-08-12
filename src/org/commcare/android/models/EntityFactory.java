/**
 * 
 */
package org.commcare.android.models;

import java.util.Stack;

import org.commcare.android.preloaders.CasePreloader;
import org.commcare.android.preloaders.UserPreloader;
import org.commcare.entity.CaseEntityFilter;
import org.commcare.entity.InstanceEntityFilter;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.Text;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.utils.IPreloadHandler;
import org.javarosa.core.services.storage.Persistable;

/**
 * @author ctsims
 *
 */
public class EntityFactory<T extends Persistable> {
	Detail detail;
	FormInstance instance;
	User current;
	
	public EntityFactory(Detail d, User current) {
		this.detail = d;
		this.current = current;
	}
	
	public Detail getDetail() {
		return detail;
	}
	
	public Entity<T> getEntity(T data) {
		loadData(data);
		
		if(!meetsFilter(data, instance)) {
			return null;
		}
		Text[] templates = detail.getTemplates();
		String[] outcomes = new String[templates.length];
		for(int i = 0 ; i < templates.length ; ++i ) {
			outcomes[i] = templates[i].evaluate(instance, null);
		}
		return new Entity<T>(outcomes, data);
	}
	
	protected void loadData(T data) {
		instance = detail.getInstance();
		Stack<TreeElement> elements = new Stack<TreeElement>();
		elements.push(instance.getRoot());
		while(elements.size() > 0 ) {
			TreeElement element = elements.pop();
			for(int i = 0 ; i < element.getNumChildren() ; ++i) {
				elements.push(element.getChildAt(i));
			}
			String ref = element.getAttributeValue(null,"reference");
			if(ref != null) {
				IPreloadHandler preloader = this.getPreloader(ref, data);
				if(preloader != null) {
					IAnswerData loaded = preloader.handlePreload(element.getAttributeValue(null, "field"));
					element.setValue(loaded);
				}
			}
		}
	}
	
	private boolean meetsFilter(T t, FormInstance instance) {
		if(detail.getFilter() == null) {
			return true;
		}
		if(t instanceof Case) {
			CaseEntityFilter caseFilter = new CaseEntityFilter(detail.getFilter());
			if(!caseFilter.matches((Case)t)) {
				return false;
			}
		}
		
		InstanceEntityFilter filter = new InstanceEntityFilter(detail.getFilter());
		if(!filter.matches(instance)) {
				return false;
		}
		
		return true;
	}
	
	private IPreloadHandler getPreloader(String preloader, T t) {
		if("case".equals(preloader)) {
			return new CasePreloader((Case)t);
		} else if ("user".equals(preloader)) {
			return new UserPreloader(current);
		} else {
			return null;
		}
	}
}
