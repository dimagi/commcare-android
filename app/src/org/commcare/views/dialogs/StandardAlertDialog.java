package org.commcare.views.dialogs;

import android.content.Context;
import android.content.DialogInterface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import org.commcare.dalvik.R;
import org.javarosa.core.services.locale.Localization;

/**
 * An implementation of CommCareAlertDialog that utilizes a pre-set view template, with the ability
 * to customize basic fields (title, message, buttons, listeners, etc.)
 *
 * @author amstone
 */
public class StandardAlertDialog extends CommCareAlertDialog {

    private final String title;
    private final String msg;

    @Nullable
    private DialogInterface.OnClickListener positiveButtonListener;
    private int iconResId;
    @Nullable
    private CharSequence positiveButtonDisplayText;

    @Nullable
    private DialogInterface.OnClickListener negativeButtonListener;
    @Nullable
    private CharSequence negativeButtonDisplayText;
    private boolean usePositiveButtonStyleForNegativeButton = false;

    @Nullable
    private DialogInterface.OnClickListener neutralButtonListener;
    @Nullable
    private CharSequence neutralButtonDisplayText;
    @Nullable
    private String emphasizedMessage;
    @Nullable
    private CharSequence checkboxDisplayText;
    @Nullable
    private CompoundButton.OnCheckedChangeListener checkboxListener;


    public StandardAlertDialog(String title, String msg,
            @Nullable DialogInterface.OnClickListener positiveButtonListener, int iconResId) {
        this.title = title;
        this.msg = msg;
        this.positiveButtonListener = positiveButtonListener;
        this.iconResId = iconResId;
    }

    public StandardAlertDialog(String title, String msg) {
        this(title, msg, null, -1);
    }

    /**
     * A shortcut method that will generate an alert dialog in one method call; to be used for
     * dialogs that have a title, message, and one button with display text "OK"
     *
     * @param positiveButtonListener - the onClickListener to apply to the positive button. If
     *                               null, applies a default listener of just dismissing the dialog
     */
    public static StandardAlertDialog getBasicAlertDialog(String title, String msg,
                                                          DialogInterface.OnClickListener positiveButtonListener) {
       return getBasicAlertDialogWithIcon(title, msg, -1, positiveButtonListener);
    }

    /**
     * A shortcut method that will generate and show an alert dialog in one method call; to be
     * used for dialogs that have a title, message, an icon to be displayed to the left of the
     * title, and one button with display text "OK"
     *
     * @param iconResId              - the id of the icon to be displayed
     * @param positiveButtonListener - the onClickListener to apply to the positive button. If
     *                               null, applies a default listener of just dismissing the dialog
     */
    public static StandardAlertDialog getBasicAlertDialogWithIcon(String title, String msg, int iconResId,
            DialogInterface.OnClickListener positiveButtonListener) {
        StandardAlertDialog d = new StandardAlertDialog(title, msg);
        if (positiveButtonListener == null) {
            positiveButtonListener = (dialog, which) -> dialog.dismiss();
        }
        return new StandardAlertDialog(title, msg, positiveButtonListener, iconResId);
    }

    @Override
    protected void initView(Context context) {
        super.initView(context);
        view = LayoutInflater.from(context).inflate(R.layout.custom_alert_dialog, null);
        TextView titleView = view.findViewById(R.id.dialog_title).findViewById(R.id.dialog_title_text);
        titleView.setText(title);
        TextView messageView = view.findViewById(R.id.dialog_message);
        messageView.setText(msg);
        setUpIconView();
        setUpPositiveButton();
        setUpNegativeButton();
        setUpNeutralButton();
        addEmphasizedMessage();
        setUpCheckbox();
    }

    private void setUpPositiveButton() {
        Button positiveButton = this.view.findViewById(R.id.positive_button);
        if (positiveButtonListener != null) {
            if (positiveButtonDisplayText == null) {
                positiveButtonDisplayText = Localization.get("dialog.ok");
            }
            positiveButton.setText(positiveButtonDisplayText);
            positiveButton.setOnClickListener(
                    v -> positiveButtonListener.onClick(dialog, AlertDialog.BUTTON_POSITIVE));
            positiveButton.setVisibility(View.VISIBLE);
        } else {
            positiveButton.setVisibility(View.GONE);
        }
    }

    private void setUpIconView() {
        if (iconResId != -1) {
            ImageView icon = view.findViewById(R.id.dialog_title).findViewById(R.id.dialog_title_icon);
            icon.setImageResource(iconResId);
            icon.setVisibility(View.VISIBLE);
        }
    }

    private void setUpNegativeButton() {
        Button negativeButton = view.findViewById(R.id.negative_button);
        if (negativeButtonListener != null) {
            if (usePositiveButtonStyleForNegativeButton) {
                negativeButton.setTextAppearance(view.getContext(), R.style.Commcare_Button_Primary_Rounded);
                negativeButton.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        view.getContext().getResources().getDimensionPixelSize(R.dimen.font_size_medium));
                negativeButton.setBackgroundColor(view.getResources().getColor(R.color.cc_brand_color));
            }
            if (negativeButtonDisplayText != null) {
                negativeButton.setText(negativeButtonDisplayText);
            }
            negativeButton.setOnClickListener(
                    v -> negativeButtonListener.onClick(dialog, AlertDialog.BUTTON_NEGATIVE));
            negativeButton.setVisibility(View.VISIBLE);
        } else {
            negativeButton.setVisibility(View.GONE);
        }
    }

    private void setUpNeutralButton() {
        Button neutralButton = this.view.findViewById(R.id.neutral_button);
        if (neutralButtonListener != null) {
            neutralButton.setText(neutralButtonDisplayText);
            neutralButton.setOnClickListener(
                    v -> neutralButtonListener.onClick(dialog, AlertDialog.BUTTON_NEUTRAL));
            neutralButton.setVisibility(View.VISIBLE);
        } else {
            neutralButton.setVisibility(View.GONE);
        }
    }

    private void addEmphasizedMessage() {
        TextView tv = this.view.findViewById(R.id.emphasized_message);
        if (emphasizedMessage != null) {
            tv.setVisibility(View.VISIBLE);
            tv.setText(emphasizedMessage);
        } else {
            tv.setVisibility(View.GONE);
        }
    }

    private void setUpCheckbox() {
        CheckBox checkbox = this.view.findViewById(R.id.dialog_checkbox);
        if (checkboxListener != null) {
            checkbox.setText(checkboxDisplayText);
            checkbox.setOnCheckedChangeListener(checkboxListener);
            checkbox.setVisibility(View.VISIBLE);
        } else {
            checkbox.setVisibility(View.GONE);
        }
    }

    public void setPositiveButton(CharSequence displayText, final DialogInterface.OnClickListener buttonListener) {
        positiveButtonListener = buttonListener;
        positiveButtonDisplayText = displayText;
    }

    public void setNegativeButton(CharSequence displayText, final DialogInterface.OnClickListener buttonListener) {
        setNegativeButton(displayText, buttonListener, false);
    }

    public void setNegativeButton(CharSequence displayText,
                                  final DialogInterface.OnClickListener buttonListener,
                                  boolean usePositiveButtonStyle) {
        negativeButtonDisplayText = displayText;
        negativeButtonListener = buttonListener;
        usePositiveButtonStyleForNegativeButton = usePositiveButtonStyle;
    }

    public void setNeutralButton(CharSequence displayText, final DialogInterface.OnClickListener buttonListener) {
        neutralButtonDisplayText = displayText;
        neutralButtonListener = buttonListener;
    }

    public void setEmphasizedMessage(String text) {
        emphasizedMessage = text;
    }

    public void setCheckbox(CharSequence displayText, CompoundButton.OnCheckedChangeListener checkboxListener) {
        checkboxDisplayText = displayText;
        this.checkboxListener = checkboxListener;
    }
    public static StandardAlertDialog getBasicAlertDialogWithDisablingCheckbox(String title, String msg,
                                                                               CompoundButton.OnCheckedChangeListener checkboxListener){
        StandardAlertDialog d = new StandardAlertDialog(title, msg);
        d.setCheckbox(Localization.get("dialog.do.not.show"), checkboxListener);
        return d;
    }
}
