package org.commcare.activities;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.google.services.ads.AdLocation;
import org.commcare.google.services.ads.AdMobManager;
import org.commcare.google.services.analytics.GoogleAnalyticsUtils;
import org.commcare.logic.DetailCalloutListenerDefaultImpl;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.session.CommCareSession;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.CalloutData;
import org.commcare.suite.model.Detail;
import org.commcare.utils.DetailCalloutListener;
import org.commcare.utils.SerializationUtil;
import org.commcare.utils.SessionStateUninitException;
import org.commcare.views.ManagedUi;
import org.commcare.views.TabbedDetailView;
import org.commcare.views.UiElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.xpath.expr.XPathExpression;

import java.util.HashMap;
import java.util.Hashtable;

/**
 * @author ctsims
 */
@ManagedUi(R.layout.entity_detail)
public class EntityDetailActivity
        extends SessionAwareCommCareActivity
        implements DetailCalloutListener {

    // reference id of selected element being detailed
    public static final String CONTEXT_REFERENCE = "eda_crid";
    public static final String DETAIL_ID = "eda_detail_id";
    public static final String DETAIL_PERSISTENT_ID = "eda_persistent_id";

    private static final int MENU_PRINT_DETAIL = Menu.FIRST;

    private static final int PRINT_DETAIL = 1;

    private Detail detail;
    private Pair<Detail, TreeReference> mEntityContext;
    private TreeReference mTreeReference;
    private int detailIndex;

    // controls whether swiping can toggle exit from case detail screen
    private boolean isFinalSwipeActionEnabled = false;

    @UiElement(value = R.id.entity_detail)
    private RelativeLayout container;

    @UiElement(value = R.id.entity_select_button, locale = "select.detail.confirm")
    private Button next;

    @UiElement(value = R.id.entity_detail_tabs)
    private TabbedDetailView mDetailView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent i = getIntent();

        AndroidSessionWrapper asw;
        CommCareSession session;
        try {
            asw = CommCareApplication.instance().getCurrentSessionWrapper();
            session = asw.getSession();
        } catch (SessionStateUninitException sue) {
            // The user isn't logged in! bounce this back to where we came from
            this.setResult(RESULT_CANCELED, this.getIntent());
            this.finish();
            return;
        }
        String passedCommand = getIntent().getStringExtra(SessionFrame.STATE_COMMAND_ID);

        // Is the detail screen for showing entities, without option for moving
        // forward on to form manipulation?
        boolean viewMode;
        if (passedCommand != null) {
            viewMode = session.isViewCommand(passedCommand);
        } else {
            viewMode = session.isViewCommand(session.getCommand());
        }

        detail = session.getDetail(getIntent().getStringExtra(EntityDetailActivity.DETAIL_ID));

        mTreeReference =
                SerializationUtil.deserializeFromIntent(getIntent(),
                        EntityDetailActivity.CONTEXT_REFERENCE,
                        TreeReference.class);
        String shortDetailId = getIntent().getStringExtra(EntityDetailActivity.DETAIL_PERSISTENT_ID);
        if (shortDetailId != null) {
            Detail shortDetail = session.getDetail(shortDetailId);
            this.mEntityContext = new Pair<>(shortDetail, mTreeReference);
        }

        super.onCreate(savedInstanceState);
        
        /* Caution: The detailIndex field comes from EntitySelectActivity, which is the 
         * source of this intent. In some instances, the detailIndex may not have been assigned,
         * in which case it will take on a value of -1. If making use of the detailIndex, it may
         * be useful to include the debugging print statement below.
         */
        this.detailIndex = i.getIntExtra("entity_detail_index", -1);

        if (this.getString(R.string.panes).equals("two")) {
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                //this occurs when the screen was rotated to be vertical on the select activity. We
                //want to navigate back to that screen now.
                this.setResult(RESULT_CANCELED, this.getIntent());
                this.finish();
                return;
            }
        }

        next.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportEntityDetailContinue(false, mDetailView.getTabCount() == 1);
                select();
            }
        });

        if (viewMode) {
            next.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            next.setText(Localization.get("select.detail.bypass"));
        }

        mDetailView.setRoot((ViewGroup)container.findViewById(R.id.entity_detail_tabs));
        mDetailView.refresh(detail, mTreeReference, detailIndex);

        mDetailView.showMenu();
        isFinalSwipeActionEnabled = DeveloperPreferences.isDetailTabSwipeActionEnabled();

        AdMobManager.requestBannerAdForView(this, (FrameLayout)this.findViewById(R.id.ad_container),
                AdLocation.EntityDetail);
    }

    @Override
    public Pair<Detail, TreeReference> requestEntityContext() {
        return mEntityContext;
    }

    @Override
    protected boolean isTopNavEnabled() {
        return true;
    }

    @Override
    public String getActivityTitle() {
        return null;
    }

    private void loadOutgoingIntent(Intent i) {
        i.putExtra(SessionFrame.STATE_DATUM_VAL, this.getIntent().getStringExtra(SessionFrame.STATE_DATUM_VAL));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case DetailCalloutListenerDefaultImpl.CALL_OUT:
                if (resultCode == RESULT_CANCELED) {
                    mDetailView.refresh(detail, mTreeReference, detailIndex);
                    return;
                } else {
                    long duration = intent.getLongExtra(CallOutActivity.CALL_DURATION, 0);

                    Intent i = new Intent(EntityDetailActivity.this.getIntent());
                    loadOutgoingIntent(i);
                    i.putExtra(CallOutActivity.CALL_DURATION, duration);
                    setResult(RESULT_OK, i);

                    finish();
                    return;
                }
            default:
                super.onActivityResult(requestCode, resultCode, intent);
        }
    }

    @Override
    public void callRequested(String phoneNumber) {
        DetailCalloutListenerDefaultImpl.callRequested(this, phoneNumber);
    }

    @Override
    public void addressRequested(String address) {
        DetailCalloutListenerDefaultImpl.addressRequested(this, address);
    }

    @Override
    public void playVideo(String videoRef) {
        DetailCalloutListenerDefaultImpl.playVideo(this, videoRef);
    }

    @Override
    public void performCallout(CalloutData callout, int id) {
        DetailCalloutListenerDefaultImpl.performCallout(this, callout, id);
    }

    @Override
    protected boolean onForwardSwipe() {
        // Move along, provided we're on the last tab of tabbed case details
        if (isFinalSwipeActionEnabled &&
                mDetailView.getCurrentTab() >= mDetailView.getTabCount() - 1) {
            select();
            GoogleAnalyticsUtils.reportEntityDetailContinue(true, mDetailView.getTabCount() == 1);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onBackwardSwipe() {
        // Move back, provided we're on the first screen of tabbed case details
        if (isFinalSwipeActionEnabled &&
                mDetailView.getCurrentTab() < 1) {
            finish();
            GoogleAnalyticsUtils.reportEntityDetailExit(true, mDetailView.getTabCount() == 1);
            return true;
        }
        return false;
    }

    /**
     * Move along to form entry.
     */
    private void select() {
        announceCaseSelect();

        Intent i = new Intent(EntityDetailActivity.this.getIntent());
        loadOutgoingIntent(i);
        setResult(RESULT_OK, i);
        finish();
    }

    private void announceCaseSelect() {
        Intent selectIntentBroadcast = new Intent("org.commcare.dalvik.api.action.case.selected");
        selectIntentBroadcast.putExtra("case_id", getIntent().getStringExtra(SessionFrame.STATE_DATUM_VAL));
        sendBroadcast(selectIntentBroadcast, "org.commcare.dalvik.provider.cases.read");
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        GoogleAnalyticsUtils.reportEntityDetailExit(false, mDetailView.getTabCount() == 1);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_PRINT_DETAIL, 0, Localization.get("print.detail.button"));
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(MENU_PRINT_DETAIL).setVisible(detail.isPrintEnabled());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case MENU_PRINT_DETAIL:
                printDetailV2();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /*private void printDetailV1() {
        Hashtable<String, XPathExpression> refsMap = detail.getKeyValueMapForPrint();
        IntentCallout printActivityCallout =
                IntentCallout.CalloutForOutsideFormEntry("org.commcare.dalvik.action.PRINT", refsMap);
        this.startActivityForResult(printActivityCallout.generate(null), PRINT_DETAIL);
    }*/

    private void printDetailV2() {
        HashMap<String, String> keyValueMap = detail.getKeyValueMapForPrint(
                CommCareApplication.instance().getCurrentSessionWrapper().getEvaluationContext());
        Intent i = new Intent(this, TemplatePrinterActivity.class);
        for (String key : keyValueMap.keySet()) {
            i.putExtra(key, keyValueMap.get(key));
        }
    }

}
