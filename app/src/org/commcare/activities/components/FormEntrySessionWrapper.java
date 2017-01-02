package org.commcare.activities.components;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.commcare.activities.CommCareActivity;
import org.commcare.views.UserfacingErrorHandling;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.form.api.FormEntrySession;
import org.javarosa.form.api.FormEntrySessionReplayer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.commcare.activities.FormEntryActivity.mFormController;

/**
 * Wrapper for the form entry action recorder, allowing for saving and
 * restoring to the form entry activity.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class FormEntrySessionWrapper {
    public static final String KEY_RECORD_FORM_ENTRY_SESSION = "record_form_entry_session";
    private static final String TAG = FormEntrySessionWrapper.class.getSimpleName();

    public static final String KEY_FORM_ENTRY_SESSION = "form_entry_session";

    private FormEntrySession formEntryRestoreSession = null;
    private boolean recordEntrySession;

    public void restoreFormEntrySession(Bundle savedInstanceState, PrototypeFactory prototypeFactory) {
        recordEntrySession = savedInstanceState.getBoolean(KEY_RECORD_FORM_ENTRY_SESSION, false);

        byte[] serializedObject = savedInstanceState.getByteArray(KEY_FORM_ENTRY_SESSION);
        if (serializedObject != null) {
            formEntryRestoreSession = new FormEntrySession();
            DataInputStream objectInputStream = new DataInputStream(new ByteArrayInputStream(serializedObject));
            try {
                formEntryRestoreSession.readExternal(objectInputStream, prototypeFactory);
            } catch (IOException | DeserializationException e) {
                Log.e(TAG, "failed to deserialize form entry session during saved instance restore");
            } finally {
                try {
                    objectInputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "failed to close deserialization stream for form entry session during saved instance restore");
                }
            }
        }
    }

    public boolean isRecording() {
        return recordEntrySession;
    }

    public void saveFormEntrySession(Bundle outState) {
        outState.putBoolean(KEY_RECORD_FORM_ENTRY_SESSION, recordEntrySession);
        if (formEntryRestoreSession != null) {
            ByteArrayOutputStream objectSerialization = new ByteArrayOutputStream();
            try {
                formEntryRestoreSession.writeExternal(new DataOutputStream(objectSerialization));
                outState.putByteArray(FormEntrySessionWrapper.KEY_FORM_ENTRY_SESSION, objectSerialization.toByteArray());
            } catch (IOException e) {
                outState.putByteArray(FormEntrySessionWrapper.KEY_FORM_ENTRY_SESSION, null);
            } finally {
                try {
                    objectSerialization.close();
                } catch (IOException e) {
                    Log.w(TAG, "failed to store form entry session in instance bundle");
                }
            }
        }
    }

    public void replaySession(CommCareActivity activity) {
        try {
            FormEntrySessionReplayer.tryReplayingFormEntry(mFormController.getFormEntryController(),
                    formEntryRestoreSession);
            formEntryRestoreSession = null;
        } catch (FormEntrySessionReplayer.ReplayError e) {
            UserfacingErrorHandling.createErrorDialog(activity, e.getMessage(), FormEntryConstants.EXIT);
        }

    }

    public void loadFromIntent(Intent intent) {
        if (intent.hasExtra(KEY_FORM_ENTRY_SESSION)) {
            formEntryRestoreSession =
                    FormEntrySession.fromString(intent.getStringExtra(KEY_FORM_ENTRY_SESSION));
        }
        recordEntrySession = intent.getBooleanExtra(KEY_RECORD_FORM_ENTRY_SESSION, false);
    }
}
