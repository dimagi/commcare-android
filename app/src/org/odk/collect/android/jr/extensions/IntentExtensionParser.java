package org.odk.collect.android.jr.extensions;

import android.util.Log;

import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.model.xform.XPathReference;
import org.javarosa.xform.parse.IElementHandler;
import org.javarosa.xform.parse.XFormParseException;
import org.javarosa.xform.parse.XFormParser;
import org.javarosa.xpath.XPathParseTool;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.parser.XPathSyntaxException;
import org.kxml2.kdom.Element;

import java.util.Hashtable;
import java.util.Vector;

/**
 * @author ctsims
 *
 */
public class IntentExtensionParser implements IElementHandler {

    private static final String TAG = IntentExtensionParser.class.getSimpleName();
    private static final String RESPONSE = "response";
    private static final String EXTRA = "extra";

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

        Hashtable<String, XPathExpression> extras = new Hashtable<>();
        Hashtable<String, Vector<TreeReference>> response = new Hashtable<>();

        for(int i = 0; i < e.getChildCount(); ++i) {
            if(e.getType(i) == Element.ELEMENT) {
                Element child = (Element)e.getChild(i);
                try{
                    if(child.getName().equals(EXTRA)) {
                        String key = child.getAttributeValue(null, "key");
                        String ref = child.getAttributeValue(null, "ref");
                        XPathExpression expr = XPathParseTool.parseXPath(ref);

                        extras.put(key, expr);

                    } else if(child.getName().equals(RESPONSE)) {
                        String key = child.getAttributeValue(null, "key");
                        String ref = child.getAttributeValue(null, "ref");
                        if (response.get(key) == null) {
                            response.put(key, new Vector<TreeReference>());
                        }
                        response.get(key).add((TreeReference) new XPathReference(ref).getReference());

                    }
                }
                catch(XPathSyntaxException xptm){
                    throw new XFormParseException("Error parsing Intent Extra: " + xptm.getMessage(), e);
                }
            }
        }

        form.getExtension(AndroidXFormExtensions.class).registerIntent(id, new IntentCallout(className, extras, response, type, component, data, label, appearance));
    }

}
