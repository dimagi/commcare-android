package org.commcare.xml;

import org.kxml2.io.KXmlParser;

/**
 * Created by jschweers on 1/28/2016.
 */
public class AndroidDetailParser extends DetailParser {
    public AndroidDetailParser(KXmlParser parser) {
        super(parser);
    }

    @Override
    protected DetailParser getDetailParser() {
        return new AndroidDetailParser(parser);
    }

    @Override
    protected GraphParser getGraphParser() {
        return new AndroidGraphParser(parser);
    }
}
