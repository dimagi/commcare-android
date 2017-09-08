package org.commcare.android.nfc;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.dalvik.R;
import org.javarosa.core.services.locale.Localization;

/**
 * Created by amstone326 on 9/7/17.
 */

public abstract class NfcActivity extends Activity {

    protected static final String NFC_PAYLOAD_TYPE_ARG = "type";
    protected static final String NFC_DOMAIN_ARG = "domain";

    protected static final String CHARSET_ENCODING = "UTF-8";

    protected NfcManager nfcManager;
    protected PendingIntent pendingNfcIntent;
    protected String userSpecifiedType;
    protected String userSpecifiedDomain;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            initFields();

            createPendingRestartIntent();
            setContentView(R.layout.nfc_instructions_view);
            ((TextView)findViewById(R.id.nfc_instructions_text_view)).
                    setText(Localization.get(getInstructionsTextKey()));
        }
    }

    protected void initFields() {
        this.nfcManager = new NfcManager(this);

        // These will have been supplied by the user via the intent callout extras
        this.userSpecifiedType = getIntent().getStringExtra(NFC_PAYLOAD_TYPE_ARG);
        this.userSpecifiedDomain = getIntent().getStringExtra(NFC_DOMAIN_ARG);
    }

    /**
     * Create an intent for restarting this activity, which will be passed to enableForegroundDispatch(),
     * thus instructing Android to start the intent when the device detects a new NFC tag. Adding
     * FLAG_ACTIVITY_SINGLE_TOP makes it so that onNewIntent() can be called in this activity when
     * the intent is started.
     **/
    protected void createPendingRestartIntent() {
        Intent i = new Intent(this, getClass());
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        this.pendingNfcIntent = PendingIntent.getActivity(this, 0, i, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            finishWithErrorToast("nfc.min.version.message");
            return;
        }

        try {
            nfcManager.checkForNFCSupport();
            if (requiredFieldsMissing()) {
                return;
            }
            setReadyToHandleTag();
        } catch (NfcManager.NfcNotEnabledException e) {
            finishWithErrorToast("nfc.not.enabled");
        } catch (NfcManager.NfcNotSupportedException e) {
            finishWithErrorToast("nfc.not.supported");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        this.nfcManager.disableForegroundDispatch(this);
    }

    protected boolean requiredFieldsMissing() {
        if (this.userSpecifiedType == null || this.userSpecifiedType.equals("")) {
            finishWithErrorToast("nfc.no.type");
            return true;
        }
        if (!isWellKnownType(this.userSpecifiedType) &&
                (this.userSpecifiedDomain == null || this.userSpecifiedDomain.equals(""))) {
            finishWithErrorToast("nfc.missing.domain");
            return true;
        }
        return false;
    }

    /**
     * Make it so that this activity will be the default to handle a new tag when it is discovered
     */
    private void setReadyToHandleTag() {
        // TODO: For read activity, specify how to only accept tags whose Ndef message has a specific tag

        IntentFilter ndefDiscoveredFilter = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        IntentFilter tagDiscoveredFilter = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);

        IntentFilter[] intentFilters = new IntentFilter[]{ ndefDiscoveredFilter, tagDiscoveredFilter };
        this.nfcManager.enableForegroundDispatch(this, this.pendingNfcIntent, intentFilters, null);
    }

    /**
     * Once setReadyToHandleTag() has been called in this activity, Android will pass any
     * discovered tags to this activity through this method
     * @param intent
     */
    @Override
    protected void onNewIntent(Intent intent) {
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        dispatchActionOnTag(tag);
    }

    protected abstract void dispatchActionOnTag(Tag tag);

    protected void finishWithErrorToast(String errorMessageKey) {
        finishWithToast(errorMessageKey, false);
    }

    protected void finishWithToast(String messageKey, boolean success) {
        Toast.makeText(this, Localization.get(messageKey), Toast.LENGTH_SHORT).show();

        Intent i = new Intent(getIntent());
        setResultExtrasBundle(i, success);
        setResultValue(i);

        setResult(RESULT_OK, i);
        finish();
    }

    protected abstract void setResultExtrasBundle(Intent i, boolean success);

    protected abstract void setResultValue(Intent i);

    protected abstract String getInstructionsTextKey();

    protected static boolean isWellKnownType(String type) {
        return "text".equals(type) || "uri".equals(type);
    }

}
