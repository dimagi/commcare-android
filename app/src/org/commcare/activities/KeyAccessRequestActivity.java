package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.android.database.global.models.AndroidSharedKeyRecord;
import org.commcare.dalvik.R;
import org.commcare.util.EncryptionKeyHelper;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import androidx.appcompat.app.AppCompatActivity;

/**
 * @author ctsims
 */
@ManagedUi(R.layout.screen_permission_request)
public class KeyAccessRequestActivity extends CommCareActivity<KeyAccessRequestActivity> {

    @UiElement(value = R.id.screen_permission_grant_text_message, locale = "app.key.request.message")
    TextView message;

    @UiElement(value = R.id.screen_permission_request_button_grant, locale = "app.key.request.grant")
    Button grantButton;

    @UiElement(value = R.id.screen_permission_request_button_deny, locale = "app.key.request.deny")
    Button denyButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        grantButton.setOnClickListener(v -> {
            Intent response = new Intent(getIntent());
            AndroidSharedKeyRecord record = null;
            try {
                record = AndroidSharedKeyRecord.generateNewSharingKey();
            } catch (EncryptionKeyHelper.EncryptionKeyException e) {
                Toast.makeText(this, Localization.get("app.key.request.encryption.key.error"), Toast.LENGTH_LONG).show();
                Logger.exception("Exception while generating encryption key ", e);
                return;
            }
            CommCareApplication.instance().getGlobalStorage(AndroidSharedKeyRecord.class).write(record);
            record.writeResponseToIntent(response);
            setResult(AppCompatActivity.RESULT_OK, response);
            finish();
        });

        denyButton.setOnClickListener(v -> {
            setResult(AppCompatActivity.RESULT_CANCELED);
            finish();
        });
    }
}
