package org.commcare.graph.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.LinearLayout;

import org.commcare.dalvik.BuildConfig;
import org.commcare.graph.model.GraphData;
import org.commcare.graph.util.GraphUtil;

/**
 * View containing a graph. Note that this does not derive from View; call renderView to get a view for adding to other views, etc.
 *
 * @author jschweers
 */
public class GraphView {

    public static final String HTML = "html";
    public static final String TITLE = "title";

    private final Context mContext;
    private final String mTitle;
    private final boolean mIsFullScreen;

    public String myHTML;

    public GraphView(Context context, String title, boolean isFullScreen) {
        mContext = context;
        mTitle = title;
        mIsFullScreen = isFullScreen;
    }

    public Intent getIntent(String html, Class className) {
        Intent intent = new Intent(mContext, className);
        intent.putExtra(HTML, html);
        intent.putExtra(TITLE, mTitle);
        return intent;
    }

    /*
     * Get a View object that will display this graph. This should be called after making
     * any changes to graph's configuration, title, etc.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public WebView getView(String html) {
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        WebView webView = new WebView(mContext);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);

        webView.setClickable(true);
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);

        settings.setBuiltInZoomControls(mIsFullScreen);
        settings.setSupportZoom(mIsFullScreen);
        settings.setDisplayZoomControls(mIsFullScreen);

        // Improve performance
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        this.myHTML = html;
        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null);
        return webView;
    }

    /*
     * Get layout params for this graph, which assume that graph will fill parent
     * unless dimensions have been provided via setWidth and/or setHeight.
     */
    public static LinearLayout.LayoutParams getLayoutParams() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
    }
    
    /**
     * Get graph's desired aspect ratio.
     * Most graphs are drawn with aspect ratio 2:1, which is fairly arbitrary
     * and happened to look nice for partographs. Bar graphs are drawn square - 
     * again, arbitrary, happens to look nice for mobile UCR. Expect to revisit
     * this eventually (make all graphs square? user-configured aspect ratio?).
     *
     * @return Ratio, expressed as a double: width / height.
     */
    public double getRatio(GraphData data) {
        if (GraphUtil.TYPE_BAR.equals(data.getType())) {
            return 1;
        }
        return 2;
    }
}
