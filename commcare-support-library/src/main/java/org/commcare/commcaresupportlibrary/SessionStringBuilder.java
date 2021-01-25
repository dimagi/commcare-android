package org.commcare.commcaresupportlibrary;

import android.content.Intent;

import static org.commcare.commcaresupportlibrary.Constants.SESSION_ACTION;
import static org.commcare.commcaresupportlibrary.Constants.SESSION_REQUEST_KEY;

/**
 * Created by willpride on 3/27/18.
 */

public class SessionStringBuilder {

    private StringBuilder stringBuilder;

    public SessionStringBuilder() {
        this.stringBuilder = new StringBuilder();
    }

    public void makeModuleSelection(String commandId) {
        makeCommandSelection(commandId);
    }

    public void makeFormSelection(String commandId) {
        makeCommandSelection(commandId);
    }

    public void makeCommandSelection(String commandId) {
        stringBuilder.append(String.format("COMMAND_ID %s ", commandId));
    }

    public void makeCaseSelection(String value) {
        makeCaseSelection("case_id", value);
    }

    public void makeCaseSelection(String key, String value) {
        stringBuilder.append(String.format("CASE_ID %s %s ", key, value));
    }

    public String getBuiltString() {
        return stringBuilder.toString();
    }

    public static Intent getModuleFormSelection(String module, String form) {
        Intent intent = new Intent();
        intent.setAction(SESSION_ACTION);
        String sessionString = buildSessionString(module, form);
        intent.putExtra(SESSION_REQUEST_KEY, sessionString);
        return intent;
    }

    public static Intent getModuleCaseFormSelection(String module, String caseId, String form) {
        Intent intent = new Intent();
        intent.setAction(SESSION_ACTION);
        String sessionString = buildSessionString(module, form, caseId);
        intent.putExtra(SESSION_REQUEST_KEY, sessionString);
        return intent;
    }

    private static String buildSessionString(String module, String form) {
        return String.format("COMMAND_ID %s COMMAND_ID %s", module, form);
    }

    private static String buildSessionString(String module, String form, String caseId) {
        return String.format("COMMAND_ID %s CASE_ID case_id %s COMMAND_ID %s", module, caseId, form);
    }
}
