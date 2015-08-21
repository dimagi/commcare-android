package org.odk.collect.android.jr.extensions;

import org.javarosa.core.model.QuestionDataExtension;
import org.javarosa.xform.parse.QuestionExtensionParser;
import org.javarosa.xform.parse.XFormParser;
import org.kxml2.kdom.Element;

/**
 * Created by amstone326 on 8/20/15.
 */
public class ImageRestrictionExtensionParser extends QuestionExtensionParser {

    public ImageRestrictionExtensionParser(String elementName) {
        super(elementName);
    }

    @Override
    public QuestionDataExtension parse(Element e) {
        String s = e.getAttributeValue(XFormParser.NAMESPACE_JAVAROSA,
                "imageDimensionScaledMax");
        if (s != null) {
            // Parse off the "px" and cast to int
            int maxDimens = Integer.parseInt(s.substring(0, s.length() - 2));
            return new ImageRestrictionExtension(maxDimens);
        }
        return null;
    }
}
