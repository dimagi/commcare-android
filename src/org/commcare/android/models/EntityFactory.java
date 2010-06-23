/**
 * 
 */
package org.commcare.android.models;

import java.util.Stack;

import org.commcare.android.preloaders.CasePreloader;
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
	
	public EntityFactory(Detail d) {
		this.detail = d;
	}
	
	public Detail getDetail() {
		return detail;
	}
	
	public Entity<T> getEntity(T data) {
		loadData(data);
		Text[] templates = detail.getTemplates();
		String[] outcomes = new String[templates.length];
		for(int i = 0 ; i < templates.length ; ++i ) {
			outcomes[i] = templates[i].evaluate(instance, null);
		}
		return new Entity<T>(outcomes, data);
	}
	
	protected void loadData(T data) {
		IPreloadHandler preloader = getPreloader(data);
		instance = detail.getInstance();
		Stack<TreeElement> elements = new Stack<TreeElement>();
		elements.push(instance.getRoot());
		while(elements.size() > 0 ) {
			TreeElement element = elements.pop();
			for(int i = 0 ; i < element.getNumChildren() ; ++i) {
				elements.push(element.getChildAt(i));
			}
			String ref = element.getAttributeValue(null,"reference");
			if(ref != null && preloader.preloadHandled().equals(ref)) {
				IAnswerData loaded = preloader.handlePreload(element.getAttributeValue(null, "field"));
				element.setValue(loaded);
			}
		}
	}
	
	private IPreloadHandler getPreloader(T t) {
		if(t instanceof Case) {
			return new CasePreloader((Case)t);
		} else {
			return null;
		}
	}
}
