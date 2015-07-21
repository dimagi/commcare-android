/**
 * 
 */
package org.commcare.android.util;

import android.support.annotation.Nullable;

import java.util.Vector;

import org.javarosa.core.services.IPropertyManager;
import org.javarosa.core.services.properties.IPropertyRules;

/**
 * @author ctsims
 *
 */
public class ODKPropertyManager implements IPropertyManager {

    /* (non-Javadoc)
     * @see org.javarosa.core.services.IPropertyManager#addRules(org.javarosa.core.services.properties.IPropertyRules)
     */
    public void addRules(IPropertyRules rules) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see org.javarosa.core.services.IPropertyManager#getProperty(java.lang.String)
     */
    @Nullable
    public Vector getProperty(String propertyName) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.services.IPropertyManager#getRules()
     */
    @Nullable
    public Vector getRules() {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.services.IPropertyManager#getSingularProperty(java.lang.String)
     */
    @Nullable
    public String getSingularProperty(String propertyName) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.services.IPropertyManager#setProperty(java.lang.String, java.lang.String)
     */
    public void setProperty(String propertyName, String propertyValue) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see org.javarosa.core.services.IPropertyManager#setProperty(java.lang.String, java.util.Vector)
     */
    public void setProperty(String propertyName, Vector propertyValue) {
        // TODO Auto-generated method stub

    }

}
