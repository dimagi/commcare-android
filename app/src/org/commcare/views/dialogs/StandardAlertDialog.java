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

    public StandardAlertDialog(Context context, String title, String msg) {
       this.title = title;
       this.msg = msg;
    }

    @Override
    protected void initView(Context context) {
        super.initView(context);
        view = LayoutInflater.from(context).inflate(R.layout.custom_alert_dialog, null);
        TextView titleView = view.findViewById(R.id.dialog_title).findViewById(R.id.dialog_title_text);
        titleView.setText(title);
        TextView messageView = view.findViewById(R.id.dialog_message);
        messageView.setText(msg);
    }

    /**
     * A shortcut method that will generate an alert dialog in one method call; to be used for
     * dialogs that have a title, message, and one button with display text "OK"
     *
     * @param positiveButtonListener - the onClickListener to apply to the positive button. If
     *                               null, applies a default listener of just dismissing the dialog
     */
    public static StandardAlertDialog getBasicAlertDialog(Context context, String title, String msg,
                                                          DialogInterface.OnClickListener positiveButtonListener) {
        StandardAlertDialog d = new StandardAlertDialog(context, title, msg);
        if (positiveButtonListener == null) {
            positiveButtonListener = (dialog, which) -> dialog.dismiss();
        }
        d.setPositiveButton(Localization.get("dialog.ok"), positiveButtonListener);
        return d;
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
    public static StandardAlertDialog getBasicAlertDialogWithIcon(Context context, String title, String msg, int iconResId,
                                                                  DialogInterface.OnClickListener positiveButtonListener) {
        StandardAlertDialog d = new StandardAlertDialog(context, title, msg);
        if (positiveButtonListener == null) {
            positiveButtonListener = (dialog, which) -> dialog.dismiss();
        }
        d.setPositiveButton(Localization.get("dialog.ok"), positiveButtonListener);
        d.setIcon(iconResId);
        return d;
    }

    public void setIcon(int resId) {
        ImageView icon = view.findViewById(R.id.dialog_title).findViewById(R.id.dialog_title_icon);
        icon.setImageResource(resId);
        icon.setVisibility(View.VISIBLE);
    }

    public void setPositiveButton(CharSequence displayText, final DialogInterface.OnClickListener buttonListener) {
        Button positiveButton = this.view.findViewById(R.id.positive_button);
        positiveButton.setText(displayText);
        positiveButton.setOnClickListener(v -> buttonListener.onClick(dialog, AlertDialog.BUTTON_POSITIVE));
        positiveButton.setVisibility(View.VISIBLE);
    }

    public void setNegativeButton(CharSequence displayText, final DialogInterface.OnClickListener buttonListener) {
        setNegativeButton(displayText, buttonListener, false);
    }

    public void setNegativeButton(CharSequence displayText,
                                  final DialogInterface.OnClickListener buttonListener,
                                  boolean usePositiveButtonStyle) {
        Button negativeButton = view.findViewById(R.id.negative_button);
        if (usePositiveButtonStyle) {
            negativeButton.setTextAppearance(view.getContext(), R.style.Commcare_Button_Primary_Rounded);
            negativeButton.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    view.getContext().getResources().getDimensionPixelSize(R.dimen.font_size_medium));
            negativeButton.setBackgroundColor(view.getResources().getColor(R.color.cc_brand_color));
        }
        negativeButton.setText(displayText);
        negativeButton.setOnClickListener(v -> buttonListener.onClick(dialog, AlertDialog.BUTTON_NEGATIVE));
        negativeButton.setVisibility(View.VISIBLE);
    }

    public void setNeutralButton(CharSequence displayText, final DialogInterface.OnClickListener buttonListener) {
        Button neutralButton = this.view.findViewById(R.id.neutral_button);
        neutralButton.setText(displayText);
        neutralButton.setOnClickListener(v -> buttonListener.onClick(dialog, AlertDialog.BUTTON_NEUTRAL));
        neutralButton.setVisibility(View.VISIBLE);
    }

    public void addEmphasizedMessage(String text) {
        TextView tv = this.view.findViewById(R.id.emphasized_message);
        tv.setVisibility(View.VISIBLE);
        tv.setText(text);
    }

    public void setCheckbox(CharSequence displayText, CompoundButton.OnCheckedChangeListener checkboxListener) {
        CheckBox checkbox = this.view.findViewById(R.id.dialog_checkbox);
        checkbox.setText(displayText);
        if(checkboxListener != null) {
            checkbox.setOnCheckedChangeListener(checkboxListener);
        }
        checkbox.setVisibility(View.VISIBLE);
    }
    public static StandardAlertDialog getBasicAlertDialogWithDisablingCheckbox(Context context, String title, String msg,
                                                                               CompoundButton.OnCheckedChangeListener checkboxListener){
        StandardAlertDialog d = new StandardAlertDialog(context, title, msg);
        d.setCheckbox(Localization.get("dialog.do.not.show"), checkboxListener);
        return d;
    }
}
