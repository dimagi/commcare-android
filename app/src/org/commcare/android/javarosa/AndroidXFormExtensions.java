package org.commcare.android.javarosa;

import android.location.LocationManager;

import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.XFormExtension;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapMap;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Hashtable;

/**
 * @author ctsims
 */
public class AndroidXFormExtensions implements XFormExtension {
    private Hashtable<String, IntentCallout> intents = new Hashtable<>();
    private LocationManager locationManager;

    public AndroidXFormExtensions() {
    }

    public void setLocationManager(LocationManager locationManager) {
        this.locationManager = locationManager;
    }

    public LocationManager getLocationManager() {
        return locationManager;
    }

    public void registerIntent(String id, IntentCallout callout) {
        intents.put(id, callout);
    }

    public IntentCallout getIntent(String id, FormDef form) {
        IntentCallout callout = intents.get(id);
        if (callout == null) {
            throw new IllegalArgumentException("No registered intent callout for id : " + id);
        }
        callout.attachToForm(form);
        return callout;
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        intents = (Hashtable<String, IntentCallout>)ExtUtil.read(in, new ExtWrapMap(String.class, IntentCallout.class), pf);
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.write(out, new ExtWrapMap(intents));
    }
}
