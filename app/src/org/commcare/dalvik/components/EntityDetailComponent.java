package org.commcare.dalvik.components;

import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.models.NodeEntityFactory;
import org.commcare.android.view.TabbedDetailView;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.suite.model.Detail;
import org.commcare.util.CommCareSession;
import org.commcare.util.SessionFrame;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.locale.Localization;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;

public class EntityDetailComponent {

    public static final String IS_DEAD_END = "eda_ide";
    public static final String DETAIL_ID = "eda_detail_id";
    
    private final RelativeLayout container;
    private final Button buttonNext;
    private final TabbedDetailView detailView;
    
    private final CommCareSession session;
    private final AndroidSessionWrapper asw;
    private final NodeEntityFactory factory;
    
    private TreeReference selection;
    private int detailIndex;
    private final boolean hasDetailCalloutListener;
    
    public EntityDetailComponent(
            final Activity activity,
            ViewGroup root,
            final Intent selectedIntent,
            TreeReference selection,
            int detailIndex,
            boolean hasDetailCalloutListener) {
        
        this.selection = selection;
        this.detailIndex = detailIndex;
        this.hasDetailCalloutListener = hasDetailCalloutListener;
        
        if (root == null) {
            activity.setContentView(R.layout.entity_detail);
            container = (RelativeLayout) activity.findViewById(R.id.entity_detail);
        } else {
            container = (RelativeLayout) View.inflate(
                    activity,
                    R.layout.entity_detail,
                    root
            ).findViewById(R.id.entity_detail);
        }
        
        buttonNext = (Button) container.findViewById(R.id.entity_select_button);
        buttonNext.setText(Localization.get("select.detail.confirm"));
        buttonNext.setOnClickListener(new OnClickListener() {
            
            public void onClick(View view) {
                Intent i = new Intent(activity.getIntent());
                
                i.putExtra(SessionFrame.STATE_DATUM_VAL, selectedIntent.getStringExtra(SessionFrame.STATE_DATUM_VAL));
                activity.setResult(Activity.RESULT_OK, i);
                
                activity.finish();
            }
            
        });
        
        if(activity.getIntent().getBooleanExtra(IS_DEAD_END, false)) {
            buttonNext.setText("Done");
        }
        
        detailView = (TabbedDetailView) container.findViewById(R.id.entity_detail_tabs);
        
        detailView.setRoot((ViewGroup) container.findViewById(R.id.entity_detail_tabs));
        
        asw = CommCareApplication._().getCurrentSessionWrapper();
        session = asw.getSession();
        
        factory = new NodeEntityFactory(session.getDetail(selectedIntent.getStringExtra(DETAIL_ID)), asw.getEvaluationContext());
        
        detailView.setDetail(factory.getDetail());
        
        refresh();
    }
    
    public Detail getDetail(String detailId) {
        return session.getDetail(detailId);
    }
    
    public boolean isCompound() {
        return factory.getDetail().isCompound();
    }
    
    public void refresh(TreeReference selection, int detailIndex) {
        this.selection = selection;
        this.detailIndex = detailIndex;
        refresh();
    }
    
    public void refresh() {
        detailView.refresh(factory.getDetail(), selection, detailIndex, hasDetailCalloutListener);
    }

}
