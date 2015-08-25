package org.commcare.android.framework;

import android.app.Activity;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.commcare.android.adapters.EntityDetailAdapter;
import org.commcare.android.adapters.EntityListAdapter;
import org.commcare.android.adapters.EntitySubnodeListAdapter;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.models.Entity;
import org.commcare.android.models.NodeEntityFactory;
import org.commcare.android.tasks.EntityLoaderListener;
import org.commcare.android.tasks.EntityLoaderTask;
import org.commcare.android.util.AndroidUtil;
import org.commcare.android.util.DetailCalloutListener;
import org.commcare.android.util.SerializationUtil;
import org.commcare.android.view.EntityView;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.suite.model.Detail;
import org.javarosa.core.model.instance.TreeReference;

import java.util.List;
import java.util.Vector;

/**
 * Fragment to display Detail content. Not meant for handling nested Detail objects.
 *
 * @author jschweers
 */
public class EntityDetailFragment extends Fragment implements EntityLoaderListener {
    public static final String CHILD_DETAIL_INDEX = "edf_child_detail_index";
    public static final String DETAIL_ID = "edf_detail_id";
    public static final String DETAIL_INDEX = "edf_detail_index";
    public static final String CHILD_REFERENCE = "edf_detail_reference";

    private AndroidSessionWrapper asw;
    private ListAdapter adapter;
    private EntityDetailAdapter.EntityDetailViewModifier modifier;

    private EntityLoaderTask loader;

    public EntityDetailFragment() {
        super();
        this.asw = CommCareApplication._().getCurrentSessionWrapper();
    }

    public void setEntityDetailModifier(EntityDetailAdapter.EntityDetailViewModifier edvm) {
        this.modifier = edvm;
        if (adapter != null) {
            ((EntityDetailAdapter) adapter).setModifier(edvm);
        }
    }

    public static final String MODIFIER_KEY = "modifier";

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (modifier instanceof Parcelable) {
            outState.putParcelable(MODIFIER_KEY, (Parcelable) modifier);
        } else {
            throw new IllegalArgumentException(modifier + " must implement Parcelable!");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            this.modifier = savedInstanceState.getParcelable(MODIFIER_KEY);
        }

        // Note that some of this setup could be moved into onAttach if it would help performance
        Bundle args = getArguments();

        final Detail detail = asw.getSession().getDetail(args.getString(DETAIL_ID));
        Detail childDetail = detail;
        final int thisIndex = args.getInt(CHILD_DETAIL_INDEX, -1);
        final boolean detailCompound = thisIndex != -1;
        if (detailCompound) {
            childDetail = detail.getDetails()[thisIndex];
        }

        NodeEntityFactory factory = new NodeEntityFactory(childDetail, asw.getEvaluationContext());
        final Entity entity = factory.getEntity(SerializationUtil.deserializeFromBundle(
                        args, CHILD_REFERENCE, TreeReference.class)
        );

        View rootView = inflater.inflate(R.layout.entity_detail_list, container, false);
        final Activity thisActivity = getActivity();
        final DetailCalloutListener detailCalloutListener =
                thisActivity instanceof DetailCalloutListener ? ((DetailCalloutListener) thisActivity) : null;

        final ListView listView = ((ListView) rootView.findViewById(R.id.screen_entity_detail_list));
        final LinearLayout headerLayout = ((LinearLayout) rootView.findViewById(R.id.entity_detail_header));
        if (childDetail.getNodeset() != null && !"".equals(childDetail.getNodeset())) {
            TreeReference nodeset = childDetail.getNodeset();
            TreeReference contextualizedNodeset = nodeset.contextualize(SerializationUtil.deserializeFromBundle(args, CHILD_REFERENCE, TreeReference.class));
            Vector<TreeReference> references = asw.getEvaluationContext().expandReference(contextualizedNodeset);
            adapter = new EntitySubnodeListAdapter(thisActivity, childDetail, factory, references);

            // stolen from EntitySelectActivity, make DRYer
            String[] headers = new String[childDetail.getFields().length];
            for (int i = 0; i < headers.length; ++i) {
                headers[i] = childDetail.getFields()[i].getHeader().evaluate();
            }
            int[] colors = AndroidUtil.getThemeColorIDs(thisActivity, new int[]{R.attr.entity_view_header_background_color, R.attr.entity_view_header_text_color});
            EntityView headerView = new EntityView(thisActivity, childDetail, headers, colors[1]);
            headerView.setBackgroundColor(colors[0]);
            headerLayout.removeAllViews();
            headerLayout.addView(headerView);
            headerLayout.setVisibility(View.VISIBLE);
        } else {
            Vector<TreeReference> references = null;
            adapter = new EntityDetailAdapter(
                    thisActivity, asw.getSession(), childDetail, entity,
                    detailCalloutListener, args.getInt(DETAIL_INDEX)
            );
            ((EntityDetailAdapter) adapter).setModifier(modifier);
            headerLayout.setVisibility(View.GONE);
        }
        listView.setAdapter(adapter);
        return rootView;
    }

    @Override
    public void attach(EntityLoaderTask task) {
        this.loader = task;
    }

    @Override
    public void deliverResult(List<Entity<TreeReference>> entities, List<TreeReference> references, NodeEntityFactory factory) {
        Bundle args = getArguments();
        final Detail detail = asw.getSession().getDetail(args.getString(DETAIL_ID));
        Detail childDetail = detail;
        final int thisIndex = args.getInt(CHILD_DETAIL_INDEX, -1);
        final boolean detailCompound = thisIndex != -1;
        if (detailCompound) {
            childDetail = detail.getDetails()[thisIndex];
        }

        loader = null;
        int[] order = childDetail.getSortOrder();
        adapter = new EntityListAdapter(getActivity(), childDetail, references, entities, order, null, factory);
    }

    @Override
    public void deliverError(Exception e) {
        ((CommCareActivity) getActivity()).displayException(e);
    }
}
