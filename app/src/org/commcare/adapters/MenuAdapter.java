package org.commcare.adapters;

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
import org.commcare.dalvik.R;
import org.commcare.logging.XPathErrorLogger;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.suite.model.EntityDatum;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;
import org.commcare.suite.model.MenuDisplayable;
import org.commcare.suite.model.MenuLoader;
import org.commcare.suite.model.SessionDatum;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.LogTypes;
import org.commcare.util.LoggerInterface;
import org.commcare.utils.MediaUtil;
import org.commcare.views.UserfacingErrorHandling;
import org.commcare.views.media.AudioPlaybackButton;
import org.commcare.views.media.ViewId;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.locale.Localizer;
import org.javarosa.xpath.XPathException;
import org.javarosa.xpath.parser.XPathSyntaxException;

import java.io.File;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Load module menu items
 *
 * @author wspride
 */
public class MenuAdapter extends BaseAdapter {

    protected final AndroidSessionWrapper asw;
    private Exception loadError;
    private String errorMessage = "";
    final CommCareActivity context;
    final MenuDisplayable[] displayableData;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    class MenuLogger implements LoggerInterface {

        @Override
        public void logError(String message, Exception cause) {
            if (cause instanceof XPathSyntaxException) {
                errorMessage = Localization.get("app.menu.display.cond.bad.xpath", new String[]{message, cause.getMessage()});
                logError(errorMessage);
            } else if (cause instanceof XPathException) {
                errorMessage = Localization.get("app.menu.display.cond.xpath.err", new String[]{message, cause.getMessage()});
                XPathErrorLogger.INSTANCE.logErrorToCurrentApp(((XPathException)cause).getSource(), message);
                Logger.log(LogTypes.TYPE_ERROR_CONFIG_STRUCTURE, message);
            }
        }

        @Override
        public void logError(String message) {
            XPathErrorLogger.INSTANCE.logErrorToCurrentApp(message);
            Logger.log(LogTypes.TYPE_ERROR_CONFIG_STRUCTURE, message);
        }
    }

    public MenuAdapter(CommCareActivity context, CommCarePlatform platform, String menuID) {
        this.context = context;
        asw = CommCareApplication.instance().getCurrentSessionWrapper();
        MenuLoader menuLoader = new MenuLoader(platform, asw, menuID, new MenuLogger());
        this.displayableData = menuLoader.getMenus();
        this.errorMessage = menuLoader.getErrorMessage();
        this.loadError = menuLoader.getLoadException();
    }

    public void showAnyLoadErrors(CommCareActivity activity) {
        if (loadError != null) {
            UserfacingErrorHandling.createErrorDialog(activity, errorMessage, true);
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
                audioPlaybackButton.modifyButtonForNewView(viewId, audioURI, false);
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
        setupImageView(mIconView, menuDisplayable, (int)context.getResources().getDimension(R.dimen.list_icon_bounding_dimen));
    }


    public void setupImageView(ImageView mIconView, MenuDisplayable menuDisplayable, int defaultBoundingBox) {
        if (mIconView != null) {
            int iconDimension = defaultBoundingBox;
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
        compositeDisposable.add(
                menuDisplayable.getTextForBadge(asw.getEvaluationContext(menuDisplayable.getCommandID()))
                        .subscribeOn(Schedulers.computation())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(badgeText -> updateBadgeView(badgeView, badgeText),
                                throwable -> UserfacingErrorHandling.createErrorDialog(context, throwable.getLocalizedMessage(), true)
                        )
        );
    }

    private void updateBadgeView(View badgeView, String badgeText) {
        if (badgeText != null && !"".equals(badgeText) && !"0".equals(badgeText)) {
            if (badgeText.length() > 2) {
                // A badge can only fit up to 2 characters
                badgeText = badgeText.substring(0, 2);
            }
            TextView badgeTextView = (TextView)badgeView.findViewById(R.id.badge_text);
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

    /**
     * Must be called in the activity/fragment containing the adapter onDestroy
     * to clear Rx subscriptions
     */
    public void onDestory() {
        compositeDisposable.clear();
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
