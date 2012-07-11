/**
 * 
 */
package org.commcare.android.models;

import org.commcare.android.util.SessionUnavailableException;

/**
 * @author ctsims
 *
 */
public abstract class EntityFactory<T> {
	
	public abstract Entity<T> getEntity(T data) throws SessionUnavailableException;
//		Text[] templates = detail.getTemplates();
//		String[] outcomes = new String[templates.length];
//		for(int i = 0 ; i < templates.length ; ++i ) {
//			outcomes[i] = templates[i].evaluate(new EvaluationContext(instance)).trim();
//		}
//		return new Entity<T>(outcomes, data);
}
