package org.commcare.util.externalizable;

import org.javarosa.core.util.PrefixTree;
import org.javarosa.core.util.externalizable.PrototypeFactory;

/**
 * @author ctsims
 *
 */
public class AndroidPrototypeFactory extends PrototypeFactory {
    public AndroidPrototypeFactory(PrefixTree classNames) {
        super(classNames, new AndroidClassHasher());
    }
}
