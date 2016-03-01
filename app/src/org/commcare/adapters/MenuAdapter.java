package org.commcare.adapters;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.dalvik.R;
import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.commcare.logging.AndroidLogger;
import org.commcare.logging.XPathErrorLogger;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.FormEntry;
import org.commcare.suite.model.Menu;
import org.commcare.suite.model.MenuDisplayable;
import org.commcare.suite.model.SessionDatum;
import org.commcare.suite.model.Suite;
import org.commcare.util.CommCarePlatform;
import org.commcare.utils.MediaUtil;
import org.commcare.views.media.AudioButton;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.locale.Localizer;
import org.javarosa.xpath.XPathException;
import org.javarosa.xpath.XPathTypeMismatchException;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathFuncExpr;
import org.javarosa.xpath.parser.XPathSyntaxException;

import java.io.File;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Adapter class to handle both Menu and Entry items
 *
 * @author wspride
 */
public class MenuAdapter implements ListAdapter {

    private final AndroidSessionWrapper asw;
    final Context context;
    final MenuDisplayable[] displayableData;

    public MenuAdapter(Context context, CommCarePlatform platform, String menuID) {
        this.context = context;
        String menuTitle = null;

        Vector<MenuDisplayable> items = new Vector<>();

        Hashtable<String, Entry> map = platform.getMenuMap();
        asw = CommCareApplication._().getCurrentSessionWrapper();
        EvaluationContext ec;
        for (Suite s : platform.getInstalledSuites()) {
            for (Menu m : s.getMenus()) {
                String xpathExpression = "";
                try {
                    XPathExpression relevance = m.getMenuRelevance();
                    if (m.getMenuRelevance() != null) {
                        xpathExpression = m.getMenuRelevanceRaw();
                        ec = asw.getEvaluationContext(m.getId());
                        if (!XPathFuncExpr.toBoolean(relevance.eval(ec))) {
                            continue;
                        }
                    }
                    if (m.getId().equals(menuID)) {

                        if (menuTitle == null) {
                            //TODO: Do I need args, here?
                            try {
                                menuTitle = m.getName().evaluate();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        for (String command : m.getCommandIds()) {
                            xpathExpression = "";
                            XPathExpression mRelevantCondition = m.getCommandRelevance(m.indexOfCommand(command));
                            if (mRelevantCondition != null) {
                                xpathExpression = m.getCommandRelevanceRaw(m.indexOfCommand(command));
                                ec = asw.getEvaluationContext();
                                Object ret = mRelevantCondition.eval(ec);
                                try {
                                    if (!XPathFuncExpr.toBoolean(ret)) {
                                        continue;
                                    }
                                } catch (XPathTypeMismatchException e) {
                                    final String msg = "relevancy condition for menu item returned non-boolean value : " + ret;
                                    XPathErrorLogger.INSTANCE.logErrorToCurrentApp(e.getSource(), msg);
                                    Logger.log(AndroidLogger.TYPE_ERROR_CONFIG_STRUCTURE, msg);
                                    throw new RuntimeException(msg);
                                }
                                if (!XPathFuncExpr.toBoolean(ret)) {
                                    continue;
                                }
                            }

                            Entry e = map.get(command);
                            if (e.isView()) {
                                //If this is a "view", not an "entry"
                                //we only want to display it if all of its 
                                //datums are not already present
                                if (asw.getSession().getNeededDatum(e) == null) {
                                    continue;
                                }
                            }

                            items.add(e);
                        }
                        continue;
                    }
                    if (menuID.equals(m.getRoot())) {
                        //make sure we didn't already add this ID
                        boolean idExists = false;
                        for (Object o : items) {
                            if (o instanceof Menu) {
                                if (((Menu) o).getId().equals(m.getId())) {
                                    idExists = true;
                                    break;
                                }
                            }
                        }
                        if (!idExists) {
                            items.add(m);
                        }
                    }
                } catch (XPathSyntaxException xpse) {
                    XPathErrorLogger.INSTANCE.logErrorToCurrentApp(xpathExpression, xpse.getMessage());
                    CommCareApplication._().triggerHandledAppExit(context, Localization.get("app.menu.display.cond.bad.xpath", new String[]{xpathExpression, xpse.getMessage()}));
                    displayableData = new MenuDisplayable[0];
                    return;
                } catch (XPathException xpe) {
                    XPathErrorLogger.INSTANCE.logErrorToCurrentApp(xpe);
                    CommCareApplication._().triggerHandledAppExit(context, Localization.get("app.menu.display.cond.xpath.err", new String[]{xpathExpression, xpe.getMessage()}));
                    displayableData = new MenuDisplayable[0];
                    return;
                }
            }
        }

        displayableData = new MenuDisplayable[items.size()];
        items.copyInto(displayableData);
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public boolean isEnabled(int arg0) {
        return true;
    }

    @Override
    public int getCount() {
        return (displayableData.length);
    }

    @Override
    public Object getItem(int i) {
        return displayableData[i];
    }

    @Override
    public long getItemId(int i) {
        Object tempItem = displayableData[i];

        if (tempItem instanceof Menu) {
            return ((Menu) tempItem).getId().hashCode();
        } else {
            return ((FormEntry) tempItem).getCommandId().hashCode();
        }
    }

    @Override
    public int getItemViewType(int i) {
        return 0;
    }

    private enum NavIconState {
        NONE, NEXT, JUMP
    }

    @Override
    public View getView(int i, View v, ViewGroup vg) {
        MenuDisplayable menuDisplayable = displayableData[i];

        // inflate view
        View menuListItem = v;

        if (menuListItem == null) {
            // inflate it and do not attach to parent, or we will get the 'addView not supported' exception
            menuListItem = LayoutInflater.from(context).inflate(R.layout.menu_list_item_modern, vg, false);
        }

        TextView rowText = (TextView) menuListItem.findViewById(R.id.row_txt);
        setupTextView(rowText, menuDisplayable);

        AudioButton mAudioButton = (AudioButton) menuListItem.findViewById(R.id.row_soundicon);
        setupAudioButton(mAudioButton, menuDisplayable);

        // set up the image, if available
        ImageView mIconView = (ImageView) menuListItem.findViewById(R.id.row_img);
        setupImageView(mIconView, menuDisplayable);
        return menuListItem;
    }

    public void setupAudioButton(AudioButton mAudioButton, MenuDisplayable menuDisplayable){

        // set up audio
        final String audioURI = menuDisplayable.getAudioURI();
        String audioFilename = "";
        if (audioURI != null && !audioURI.equals("")) {
            try {
                audioFilename = ReferenceManager._().DeriveReference(audioURI).getLocalURI();
            } catch (InvalidReferenceException e) {
                Log.e("AVTLayout", "Invalid reference exception");
                e.printStackTrace();
            }
        }

        File audioFile = new File(audioFilename);
        // First set up the audio button
        if (!"".equals(audioFilename) && audioFile.exists()) {
            // Set not focusable so that list onclick will work
            mAudioButton.setFocusable(false);
            mAudioButton.setFocusableInTouchMode(false);

            mAudioButton.resetButton(audioURI, true);
        } else {
            if (mAudioButton != null) {
                mAudioButton.resetButton(audioURI, false);
                ((LinearLayout) mAudioButton.getParent()).removeView(mAudioButton);
            }
        }
    }

    public void setupTextView(TextView textView, MenuDisplayable menuDisplayable){
        // set up text
        String mQuestionText = textViewHelper(menuDisplayable);

        //Final change, remove any numeric context requests. J2ME uses these to
        //help with numeric navigation.
        if (mQuestionText != null) {
            mQuestionText = Localizer.processArguments(mQuestionText, new String[]{""}).trim();
        }
        textView.setText(mQuestionText);
    }

    public void setupImageView(ImageView mIconView, MenuDisplayable menuDisplayable){
        NavIconState iconChoice = NavIconState.NEXT;

        //figure out some icons
        if (menuDisplayable instanceof FormEntry) {
            SessionDatum datum = asw.getSession().getNeededDatum((FormEntry) menuDisplayable);
            if (datum == null || datum.getNodeset() == null) {
                iconChoice = NavIconState.JUMP;
            }
        }
        if (!DeveloperPreferences.isNewNavEnabled()) {
            iconChoice = NavIconState.NONE;
        }

        if (mIconView != null) {
            switch (iconChoice) {
                case NEXT:
                    mIconView.setImageResource(R.drawable.avatar_module);
                    break;
                case JUMP:
                    mIconView.setImageResource(R.drawable.avatar_form);
                    break;
                case NONE:
                    mIconView.setVisibility(View.GONE);
                    break;
            }
        }
        String imageURI = menuDisplayable.getImageURI();
        Bitmap image = MediaUtil.inflateDisplayImage(context, imageURI);
        if (image != null && mIconView != null) {
            mIconView.setImageBitmap(image);
            mIconView.setAdjustViewBounds(true);
        }
    }

    /*
     * Helper to build the TextView for the HorizontalMediaView constructor
     */
    String textViewHelper(MenuDisplayable e) {
        return e.getDisplayText();
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public void registerDataSetObserver(DataSetObserver arg0) {

    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver arg0) {

    }
}
