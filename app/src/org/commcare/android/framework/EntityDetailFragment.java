package org.commcare.android.framework;

import org.commcare.android.adapters.EntityDetailAdapter;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.models.Entity;
import org.commcare.android.models.NodeEntityFactory;
import org.commcare.android.util.AndroidUtil;
import org.commcare.android.util.DetailCalloutListener;
import org.commcare.android.util.SerializationUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.suite.model.Detail;
import org.javarosa.core.model.instance.TreeReference;
import org.odk.collect.android.views.media.AudioController;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Fragment to display Detail content. Not meant for handling nested Detail objects.
 * @author jschweers
 *
 */
public class EntityDetailFragment extends Fragment {
    public static final String CHILD_DETAIL_INDEX = "edf_child_detail_index";
    public static final String DETAIL_ID = "edf_detail_id";
    public static final String DETAIL_INDEX = "edf_detail_index";
    public static final String CHILD_REFERENCE = "edf_detail_reference";

    private AndroidSessionWrapper asw;
    private NodeEntityFactory factory;
    private EntityDetailAdapter adapter;
    private EntityDetailAdapter.EntityDetailViewModifier modifier;

    private boolean tabbedDetailHeader = true;


    View.OnClickListener onLeftClick;
    View.OnClickListener onRightClick;

    public void setOnLeftClick(View.OnClickListener onLeftClick) {
        this.onLeftClick = onLeftClick;
    }

    public void setOnRightClick(View.OnClickListener rightClick){
        this.onRightClick = rightClick;
    }

    public EntityDetailFragment() {
        super();
        this.asw = CommCareApplication._().getCurrentSessionWrapper();
    }

    public void setEntityDetailModifier(EntityDetailAdapter.EntityDetailViewModifier edvm){
        this.modifier = edvm;
        if(adapter != null) {
            adapter.setModifier(edvm);
        }
    }
    
    /*
     * (non-Javadoc)
     * @see android.support.v4.app.Fragment#onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Note that some of this setup could be moved into onAttach if it would help performance
        Bundle args = getArguments();

        final Detail detail = asw.getSession().getDetail(args.getString(DETAIL_ID));
        Detail childDetail = detail;
        final int thisIndex = args.getInt(CHILD_DETAIL_INDEX, -1);
        final boolean detailCompound = thisIndex != -1;
        if (detailCompound) {
            childDetail = detail.getDetails()[thisIndex];
        }

        factory = new NodeEntityFactory(childDetail, asw.getEvaluationContext());
        final Entity entity = factory.getEntity(SerializationUtil.deserializeFromBundle(
            args, CHILD_REFERENCE, TreeReference.class)
        );

        View rootView = inflater.inflate(R.layout.entity_detail_list, container, false);
        final Activity thisActivity = getActivity();
        final AudioController audioController = thisActivity instanceof AudioController ? ((AudioController)thisActivity) : null;
        final DetailCalloutListener detailCalloutListener =
                thisActivity instanceof DetailCalloutListener ? ((DetailCalloutListener)thisActivity) : null;

        final ListView listView = ((ListView) rootView.findViewById(R.id.screen_entity_detail_list));
        adapter = new EntityDetailAdapter(
            thisActivity, asw.getSession(), childDetail, entity, 
            detailCalloutListener, audioController, args.getInt(DETAIL_INDEX)
        );
        adapter.setModifier(modifier);
        View headerView;
        if(this.tabbedDetailHeader){
            headerView = inflater.inflate(R.layout.tabbed_detail_header_modern, null);
            final TextView tabtitle = (TextView) headerView.findViewById(R.id.header_title);
            ImageView left = (ImageView) headerView.findViewById(R.id.tab_left);
            ImageView right = (ImageView) headerView.findViewById(R.id.tab_right);
            if(onLeftClick != null) left.setOnClickListener(onLeftClick);
            if(onRightClick != null) right.setOnClickListener(onRightClick);
            tabtitle.setText(childDetail.getTitle().getText().evaluate());
        } else {
            final TextView header = (TextView) inflater.inflate(R.layout.entity_detail_header, null);
            header.setText(detail.getTitle().getText().evaluate());
            headerView = header;
        }
        int[] color = AndroidUtil.getThemeColorIDs(this.getActivity(), new int[]{ R.attr.drawer_pulldown_even_row_color});
        headerView.setBackgroundColor(color[0]);
        listView.addHeaderView(headerView);
        listView.setAdapter(adapter);
        return rootView;
    }

}
