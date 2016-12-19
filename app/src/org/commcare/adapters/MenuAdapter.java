package org.commcare.adapters;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.core.process.CommCareInstanceInitializer;
import org.commcare.dalvik.R;
import org.commcare.logging.AndroidLogger;
import org.commcare.logging.XPathErrorLogger;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.suite.model.EntityDatum;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;
import org.commcare.suite.model.MenuDisplayable;
import org.commcare.suite.model.SessionDatum;
import org.commcare.suite.model.Suite;
import org.commcare.util.CommCarePlatform;
import org.commcare.utils.MediaUtil;
import org.commcare.views.UserfacingErrorHandling;
import org.commcare.views.media.AudioPlaybackButton;
import org.commcare.views.media.ViewId;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.locale.Localizer;
import org.javarosa.xpath.XPathException;
import org.javarosa.xpath.XPathTypeMismatchException;
import org.javarosa.xpath.expr.FunctionUtils;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.parser.XPathSyntaxException;

import java.io.File;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Load module menu items
 *
 * @author wspride
 */
public class MenuAdapter extends BaseAdapter {

    protected final AndroidSessionWrapper asw;
    private Exception loadError;
    private String errorXpathException = "";
    final Context context;
    final MenuDisplayable[] displayableData;

    public MenuAdapter(Context context, CommCarePlatform platform, String menuID) {
        this.context = context;

        Vector<MenuDisplayable> items = new Vector<>();

        Hashtable<String, Entry> map = platform.getMenuMap();
        asw = CommCareApplication.instance().getCurrentSessionWrapper();
        for (Suite s : platform.getInstalledSuites()) {
            for (Menu m : s.getMenus()) {
                errorXpathException = "";
                try {
                    if (m.getId().equals(menuID)) {
                        if (menuIsRelevant(m)) {
                            addRelevantCommandEntries(m, items, map);
                        }
                    } else {
                        addUnaddedMenu(menuID, m, items);
                    }
                } catch (CommCareInstanceInitializer.FixtureInitializationException
                        | XPathSyntaxException | XPathException xpe) {
                    loadError = xpe;
                    displayableData = new MenuDisplayable[0];
                    return;
                }
            }
        }

        displayableData = new MenuDisplayable[items.size()];
        items.copyInto(displayableData);
    }

    public void showAnyLoadErrors(CommCareActivity activity) {
        if (loadError != null) {
            String errorMessage = loadError.getMessage();

            if (loadError instanceof XPathSyntaxException) {
                XPathErrorLogger.INSTANCE.logErrorToCurrentApp(errorXpathException, loadError.getMessage());
                errorMessage = Localization.get("app.menu.display.cond.bad.xpath", new String[]{errorXpathException, loadError.getMessage()});
            } else if (loadError instanceof XPathException) {
                XPathErrorLogger.INSTANCE.logErrorToCurrentApp((XPathException)loadError);
                errorMessage = Localization.get("app.menu.display.cond.xpath.err", new String[]{errorXpathException, loadError.getMessage()});
            }
            UserfacingErrorHandling.createErrorDialog(activity, errorMessage, true);
        }
    }

    private boolean menuIsRelevant(Menu m) throws XPathSyntaxException {
        XPathExpression relevance = m.getMenuRelevance();
        if (m.getMenuRelevance() != null) {
            errorXpathException = m.getMenuRelevanceRaw();
            EvaluationContext ec = asw.getEvaluationContext(m.getId());
            return FunctionUtils.toBoolean(relevance.eval(ec));
        }
        return true;
    }

    private void addRelevantCommandEntries(Menu m, Vector<MenuDisplayable> items,
                                           Hashtable<String, Entry> map)
            throws XPathSyntaxException {
        EvaluationContext ec = asw.getEvaluationContext();
        for (String command : m.getCommandIds()) {
            errorXpathException = "";
            XPathExpression mRelevantCondition = m.getCommandRelevance(m.indexOfCommand(command));
            if (mRelevantCondition != null) {
                errorXpathException = m.getCommandRelevanceRaw(m.indexOfCommand(command));
                Object ret = mRelevantCondition.eval(ec);
                try {
                    if (!FunctionUtils.toBoolean(ret)) {
                        continue;
                    }
                } catch (XPathTypeMismatchException e) {
                    final String msg = "relevancy condition for menu item returned non-boolean value : " + ret;
                    XPathErrorLogger.INSTANCE.logErrorToCurrentApp(e.getSource(), msg);
                    Logger.log(AndroidLogger.TYPE_ERROR_CONFIG_STRUCTURE, msg);
                    throw new RuntimeException(msg);
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
    }

    private void addUnaddedMenu(String menuID, Menu m, Vector<MenuDisplayable> items) throws XPathSyntaxException {
        if (menuID.equals(m.getRoot())) {
            //make sure we didn't already add this ID
            boolean idExists = false;
            for (Object o : items) {
                if (o instanceof Menu) {
                    if (((Menu)o).getId().equals(m.getId())) {
                        idExists = true;
                        break;
                    }
                }
            }
            if (!idExists) {
                if (menuIsRelevant(m)) {
                    items.add(m);
                }
            }
        }
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
        return displayableData.length;
    }

    @Override
    public Object getItem(int i) {
        return displayableData[i];
    }

    @Override
    public long getItemId(int i) {
        Object tempItem = displayableData[i];

        if (tempItem instanceof Menu) {
            return ((Menu)tempItem).getId().hashCode();
        } else {
            return ((Entry)tempItem).getCommandId().hashCode();
        }
    }

    @Override
    public int getItemViewType(int i) {
        return 0;
    }

    enum NavIconState {
        NONE, NEXT, JUMP
    }

    @Override
    public View getView(int i, View menuListItem, ViewGroup vg) {
        MenuDisplayable menuDisplayable = displayableData[i];

        if (menuListItem == null) {
            // inflate it and do not attach to parent, or we will get the 'addView not supported' exception
            menuListItem = LayoutInflater.from(context).inflate(R.layout.menu_list_item_modern, vg, false);
        }

        TextView rowText = (TextView)menuListItem.findViewById(R.id.row_txt);
        setupTextView(rowText, menuDisplayable);

        AudioPlaybackButton audioPlaybackButton = (AudioPlaybackButton)menuListItem.findViewById(R.id.row_soundicon);
        setupAudioButton(i, audioPlaybackButton, menuDisplayable);

        // set up the image, if available
        ImageView mIconView = (ImageView)menuListItem.findViewById(R.id.row_img);
        setupImageView(mIconView, menuDisplayable);
        setupBadgeView(menuListItem, menuDisplayable);
        return menuListItem;
    }

    private void setupAudioButton(int rowId, AudioPlaybackButton audioPlaybackButton, MenuDisplayable menuDisplayable) {
        final String audioURI = menuDisplayable.getAudioURI();
        String audioFilename = "";
        if (audioURI != null && !audioURI.equals("")) {
            try {
                audioFilename = ReferenceManager.instance().DeriveReference(audioURI).getLocalURI();
            } catch (InvalidReferenceException e) {
                Log.e("AVTLayout", "Invalid reference exception");
                e.printStackTrace();
            }
        }

        File audioFile = new File(audioFilename);
        // First set up the audio button
        ViewId viewId = ViewId.buildListViewId(rowId);
        if (!"".equals(audioFilename) && audioFile.exists()) {
            audioPlaybackButton.modifyButtonForNewView(viewId, audioURI, true);
        } else {
            if (audioPlaybackButton != null) {
                audioPlaybackButton.modifyButtonForNewView(viewId,audioURI, false);
                ((LinearLayout)audioPlaybackButton.getParent()).removeView(audioPlaybackButton);
            }
        }
    }

    public void setupTextView(TextView textView, MenuDisplayable menuDisplayable) {
        String mQuestionText = menuDisplayable.getDisplayText();

        //Final change, remove any numeric context requests. J2ME uses these to
        //help with numeric navigation.
        if (mQuestionText != null) {
            mQuestionText = Localizer.processArguments(mQuestionText, new String[]{""}).trim();
        }
        textView.setText(mQuestionText);
    }

    public void setupImageView(ImageView mIconView, MenuDisplayable menuDisplayable) {
        if (mIconView != null) {
            int iconDimension = (int)context.getResources().getDimension(R.dimen.menu_icon_size);
            Bitmap image = MediaUtil.inflateDisplayImage(context, menuDisplayable.getImageURI(),
                    iconDimension, iconDimension);
            if (image != null) {
                mIconView.setImageBitmap(image);
                mIconView.setAdjustViewBounds(true);
            } else {
                setupDefaultIcon(mIconView, getIconState(menuDisplayable));
            }
        }
    }

    protected void setupBadgeView(View menuListItem, MenuDisplayable menuDisplayable) {
        View badgeView = menuListItem.findViewById(R.id.badge_view);
        String badgeText = menuDisplayable.getTextForBadge(
                asw.getEvaluationContext(menuDisplayable.getCommandID()));
        if (badgeText != null && !"".equals(badgeText) && !"0".equals(badgeText)) {
            if (badgeText.length() > 2) {
                // A badge can only fit up to 2 characters
                badgeText = badgeText.substring(0, 2);
            }
            TextView badgeTextView = (TextView)menuListItem.findViewById(R.id.badge_text);
            badgeTextView.setText(badgeText);
            badgeView.setVisibility(View.VISIBLE);
        } else {
            badgeView.setVisibility(View.GONE);
        }
    }

    private NavIconState getIconState(MenuDisplayable menuDisplayable) {
        NavIconState iconChoice = NavIconState.NEXT;

        //figure out some icons
        if (menuDisplayable instanceof Entry) {
            SessionDatum datum = asw.getSession().getNeededDatum((Entry)menuDisplayable);
            if (datum == null || !(datum instanceof EntityDatum)) {
                iconChoice = NavIconState.JUMP;
            }
        }
        if (!DeveloperPreferences.isNewNavEnabled()) {
            iconChoice = NavIconState.NONE;
        }
        return iconChoice;
    }

    protected void setupDefaultIcon(ImageView mIconView, NavIconState iconChoice) {
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
