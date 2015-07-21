package org.odk.collect.android.jr.extensions;

import java.util.Hashtable;
import android.util.Log;

import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.model.xform.XPathReference;
import org.javarosa.xform.parse.IElementHandler;
import org.javarosa.xform.parse.XFormParseException;
import org.javarosa.xform.parse.XFormParser;
import org.javarosa.xpath.XPathException;
import org.kxml2.kdom.Element;

/**
 * @author ctsims
 *
 */
public class IntentExtensionParser implements IElementHandler {

    private static final String TAG = IntentExtensionParser.class.getSimpleName();
    private static String RESPONSE = "response";
    private static String EXTRA = "extra";

    @Override
    public void handle(XFormParser p, Element e, Object parent) {
        if(!(parent instanceof FormDef)) {
            throw new RuntimeException("Intent extension improperly registered.");
        }
        FormDef form = (FormDef)parent;

        String id = e.getAttributeValue(null, "id");
        String className = e.getAttributeValue(null, "class");
        
        String component = e.getAttributeValue(null, "component");
        String type = e.getAttributeValue(null, "type");
        String data = e.getAttributeValue(null, "data");
        String appearance = e.getAttributeValue(null, "appearance");
        
        Log.d(TAG, "0123 extention parser appearance is: " + appearance);
        
        String label = e.getAttributeValue(null, "button-label");

        Hashtable<String, TreeReference> extras = new Hashtable<String, TreeReference>();
        Hashtable<String, TreeReference> response = new Hashtable<String, TreeReference>();

        for(int i = 0; i < e.getChildCount(); ++i) {
            if(e.getType(i) == Element.ELEMENT) {
                Element child = (Element)e.getChild(i);
                try{
                    if(child.getName().equals(EXTRA)) {
                        String key = child.getAttributeValue(null, "key");
                        String ref = child.getAttributeValue(null, "ref");
                        extras.put(key, (TreeReference)new XPathReference(ref).getReference());

                    } else if(child.getName().equals(RESPONSE)) {
                        String key = child.getAttributeValue(null, "key");
                        String ref = child.getAttributeValue(null, "ref");
                        response.put(key, (TreeReference)new XPathReference(ref).getReference());

                    } 
                }
                catch(XPathException xptm){
                    throw new XFormParseException("Error parsing Intent Extra: " + xptm.getMessage(), e);
                }
            }
        }

        form.getExtension(AndroidXFormExtensions.class).registerIntent(id, new IntentCallout(className, extras, response, type, component, data, label, appearance));
    }

}
