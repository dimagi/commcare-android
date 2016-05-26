package org.commcare.models;

import org.commcare.android.javarosa.AndroidXFormExtensions;
import org.commcare.android.javarosa.IntentCallout;
import org.commcare.android.javarosa.PollSensorAction;
import org.javarosa.core.util.PrefixTree;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

/**
 * This class overrides the core PrototypeFactory class primarily because we
 * can store our Android hashes as an int and thus store them in a map for faster lookups. Most
 * other functionality is the same, except we override how we store and retrieve hashes
 * so that we can use the Map.
 *
 * @author ctsims
 * @author wspride
 */
public class AndroidPrototypeFactory extends PrototypeFactory {

    private Hashtable<Integer, Class> prototypes;
    private static final HashMap<String, Class> migratedClasses = new HashMap<>();

    static {
        // These class names were changed in CommCare 2.28; this migration
        // mapping can be removed when we are sure no pre-2.28 device with
        // saved forms is upgrading to 2.28 or higher
        migratedClasses.put("org.odk.collect.android.jr.extensions.AndroidXFormExtensions",
                AndroidXFormExtensions.class);
        migratedClasses.put("org.odk.collect.android.jr.extensions.IntentCallout",
                IntentCallout.class);
        migratedClasses.put("org.odk.collect.android.jr.extensions.PollSensorAction",
                PollSensorAction.class);
    }

    public AndroidPrototypeFactory(PrefixTree classNames) {
        super(AndroidClassHasher.getInstance(), classNames);
    }

    @Override
    protected void lazyInit() {
        initialized = false;
        prototypes = new Hashtable<>();
        super.lazyInit();
    }

    private Integer hashAsInteger(byte[] hash) {
        return (hash[3]) + (hash[2] << 8) + (hash[1] << 16) + (hash[0] << 24);
    }

    @Override
    public Class getClass(byte[] hash) {
        if (!initialized) {
            lazyInit();
        }
        return prototypes.get(hashAsInteger(hash));
    }

    @Override
    protected void storeHash(Class c, byte[] hash) {
        prototypes.put(hashAsInteger(hash), c);
    }

    @Override
    protected void addMigratedClasses() {
        // map old classname to new class. Needed to load data serialized with
        // old classname. Subsequent writes should use the class's new name
        for (Map.Entry<String, Class> c : migratedClasses.entrySet()) {
            addMigratedClass(c.getKey(), c.getValue());
        }
    }

    /**
     * @param oldClassName Assumes the class name was stored in the prototype
     *                     factory on earlier versions of CommCare. Hence
     *                     don't check for hash collisions.
     */
    private void addMigratedClass(String oldClassName, Class newClass) {
        if (!initialized) {
            lazyInit();
        }

        byte[] hashForOldClass = AndroidClassHasher.getInstance().getClassnameHash(oldClassName);
        prototypes.put(hashAsInteger(hashForOldClass), newClass);
    }

    /**
     * For testing purposes
     */
    public static Set<String> getMigratedClassNames() {
        return migratedClasses.keySet();
    }
}
