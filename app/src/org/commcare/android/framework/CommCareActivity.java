/**
 * 
 */
package org.commcare.android.framework;

import java.lang.reflect.Field;

import org.commcare.dalvik.R;
import org.javarosa.core.services.locale.Localization;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Base class for CommCareActivities to simplify 
 * common localization and workflow tasks
 * 
 * @author ctsims
 *
 */
public abstract class CommCareActivity extends Activity {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		//TODO: We can really handle much of this framework without needing to 
		//be a superclass.
		super.onCreate(savedInstanceState);
		
		this.setContentView(getContentView());
		
		loadFields();
	}
	
	private void loadFields() {
		Class c = this.getClass();
		for(Field f : c.getDeclaredFields()) {
			if(f.isAnnotationPresent(UiElement.class)) {
				UiElement element = f.getAnnotation(UiElement.class);
				try{
					f.setAccessible(true);
					
					try {
						View v = this.findViewById(element.value());
						f.set(this, v);
						
						if(element.locale() != "") {
							if(v instanceof TextView) {
								((TextView)v).setText(Localization.get(element.locale()));
							} else {
								throw new RuntimeException("Can't set the text for a " + v.getClass().getName() + " View!");
							}
						}
					} catch (IllegalArgumentException e) {
						throw new RuntimeException("Bad Object type for field " + f.getName());
					} catch (IllegalAccessException e) {
						throw new RuntimeException("Couldn't access the activity field for some reason");
					}
				} finally {
					f.setAccessible(false);
				}
			}
		}
	}
	
	protected abstract int getContentView();

}
