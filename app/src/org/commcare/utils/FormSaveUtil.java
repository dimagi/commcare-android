package org.commcare.utils;

import android.content.Context;

import org.commcare.CommCareApplication;
import org.commcare.android.database.app.models.FormDefRecord;

import java.util.Hashtable;
import java.util.NoSuchElementException;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class FormSaveUtil {

    /**
     * @param context used to query form instances through android's content provider framework
     * @return A mapping from an installed form's namespace to its install path.
     */
    public static Hashtable<String, String> getNamespaceToFilePathMap(Context context) {
        Hashtable<String, String> formNamespaces = new Hashtable<>();

        for (String xmlns : CommCareApplication.instance().getCommCarePlatform().getInstalledForms()) {
            int formDefId = CommCareApplication.instance().getCommCarePlatform().getFormDefId(xmlns);
            try {
                FormDefRecord formDefRecord = FormDefRecord.getFormDef(formDefId);
                formNamespaces.put(xmlns, formDefRecord.getFilePath());
            } catch (NoSuchElementException e) {
                throw new RuntimeException("No form registered for xmlns with id: " + formDefId);
            }
        }
        return formNamespaces;
    }
}
