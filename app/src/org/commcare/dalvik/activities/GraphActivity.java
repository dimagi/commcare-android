package org.commcare.dalvik.activities;

import org.commcare.android.models.Entity;
import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.models.NodeEntityFactory;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.services.CommCareSessionService;
import org.commcare.util.CommCareSession;
import org.commcare.util.SessionFrame;
import org.javarosa.core.model.instance.TreeReference;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

public class GraphActivity extends Activity {
	
	private GraphicalView mChart;
    private XYMultipleSeriesDataset mDataset = new XYMultipleSeriesDataset();
    private XYMultipleSeriesRenderer mRenderer = new XYMultipleSeriesRenderer();
    private XYSeries mCurrentSeries;
    private XYSeriesRenderer mCurrentRenderer;

    private void initChart() {
        mCurrentSeries = new XYSeries("Sample Data");
        mDataset.addSeries(mCurrentSeries);
        mCurrentRenderer = new XYSeriesRenderer();
        mRenderer.addSeriesRenderer(mCurrentRenderer);
    }

    private void addSampleData() {
        mCurrentSeries.add(1, 2);
        mCurrentSeries.add(2, 2);
        mCurrentSeries.add(3, 2);
        mCurrentSeries.add(4, 5);
        mCurrentSeries.add(5, 4);
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.graph);
        
        Intent i = this.getIntent();
        System.out.println("[jls] case id = " + i.getStringExtra(SessionFrame.STATE_DATUM_VAL));
        
        AndroidSessionWrapper asw = CommCareApplication._().getCurrentSessionWrapper();
        NodeEntityFactory factory = new NodeEntityFactory(asw.getSession().getDetail(i.getStringExtra(EntityDetailActivity.DETAIL_ID)), asw.getEvaluationContext());
        Entity<TreeReference> entity = factory.getEntity(CommCareApplication._().deserializeFromIntent(getIntent(), EntityDetailActivity.CONTEXT_REFERENCE, TreeReference.class));
        
        System.out.println("[jls] entity = " + entity.toString());

        String xProperty = "x";
        String yProperty = "y";
    }

    protected void onResume() {
        super.onResume();
        LinearLayout layout = (LinearLayout) findViewById(R.id.chart);
        if (mChart == null) {
            initChart();
            addSampleData();
            mChart = ChartFactory.getCubeLineChartView(this, mDataset, mRenderer, 0.3f);
            layout.addView(mChart);
        } else {
            mChart.repaint();
        }
    }

}
