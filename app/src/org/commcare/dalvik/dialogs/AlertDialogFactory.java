package org.commcare.dalvik.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.javarosa.core.services.locale.Localization;

/**
 * Created by amstone326 on 10/9/15.
 */
public class AlertDialogFactory {

    private final AlertDialog dialog;
    private final View view;
    private DialogInterface.OnCancelListener cancelListener;
    private boolean isCancelable = false;

    public AlertDialogFactory(Context context, String title, String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        view = LayoutInflater.from(context).inflate(R.layout.custom_alert_dialog, null);

        TextView titleView = (TextView) view.findViewById(R.id.dialog_title).findViewById(R.id.dialog_title_text);
        titleView.setText(title);
        TextView messageView = (TextView) view.findViewById(R.id.dialog_message);
        messageView.setText(msg);

        this.dialog = builder.create();
        dialog.setCancelable(false); // false by default, can change using makeCancelable()
    }

    /**
     * A shortcut method that will generate an alert dialog in one method call; to be used for
     * dialogs that have a title, message, and one button with display text "OK"
     *
     * @param positiveButtonListener - the onClickListener to apply to the positive button. If
     *                          null, applies a default listener of just dismissing the dialog
     */
    public static AlertDialogFactory getBasicAlertFactory(Context context, String title, String msg,
                                            DialogInterface.OnClickListener positiveButtonListener) {
        AlertDialogFactory factory = new AlertDialogFactory(context, title, msg);
        if (positiveButtonListener == null) {
            positiveButtonListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            };
        }
        factory.setPositiveButton(Localization.get("dialog.ok"), positiveButtonListener);
        return factory;
    }

    /**
     * A shortcut method that will generate and show an alert dialog in one method call; to be
     * used for dialogs that have a title, message, an icon to be displayed to the left of the
     * title, and one button with display text "OK"
     *
     * @param iconResId - the id of the icon to be displayed
     * @param positiveButtonListener - the onClickListener to apply to the positive button. If
     *                          null, applies a default listener of just dismissing the dialog
     */
    public static AlertDialogFactory getBasicAlertFactoryWithIcon(Context context, String title, String msg, int iconResId,
                                              DialogInterface.OnClickListener positiveButtonListener) {
        AlertDialogFactory factory = new AlertDialogFactory(context, title, msg);
        if (positiveButtonListener == null) {
            positiveButtonListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            };
        }
        factory.setPositiveButton(Localization.get("dialog.ok"), positiveButtonListener);
        factory.setIcon(iconResId);
        return factory;
    }

    public AlertDialog getDialog() {
        finalizeView();
        return this.dialog;
    }

    public void showDialog() {
        finalizeView();
        dialog.show();
    }

    private void finalizeView() {
        dialog.setView(this.view);
    }

    private void makeCancelable() {
        isCancelable = true;
        dialog.setCancelable(true);
    }

    public void setOnCancelListener(DialogInterface.OnCancelListener cancelListener) {
        makeCancelable();
        this.cancelListener = cancelListener;
        dialog.setOnCancelListener(cancelListener);
    }

    public void performCancel(DialogInterface dialog) {
        if (cancelListener != null) {
            cancelListener.onCancel(dialog);
        }
    }

    public void setIcon(int resId) {
        ImageView icon = (ImageView) view.findViewById(R.id.dialog_title).findViewById(R.id.dialog_title_icon);
        icon.setImageResource(resId);
        icon.setVisibility(View.VISIBLE);
    }

    public void setPositiveButton(CharSequence displayText, final DialogInterface.OnClickListener buttonListener) {
        Button positiveButton = (Button) this.view.findViewById(R.id.positive_button);
        positiveButton.setText(displayText);
        positiveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buttonListener.onClick(dialog, AlertDialog.BUTTON_POSITIVE);
            }
        });
        positiveButton.setVisibility(View.VISIBLE);
    }

    public void setNegativeButton(CharSequence displayText, final DialogInterface.OnClickListener buttonListener) {
        Button negativeButton = (Button) this.view.findViewById(R.id.negative_button);
        negativeButton.setText(displayText);
        negativeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buttonListener.onClick(dialog, AlertDialog.BUTTON_NEGATIVE);
            }
        });
        negativeButton.setVisibility(View.VISIBLE);
    }

    public void setNeutralButton(CharSequence displayText, final DialogInterface.OnClickListener buttonListener) {
        Button neutralButton = (Button) this.view.findViewById(R.id.neutral_button);
        neutralButton.setText(displayText);
        neutralButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buttonListener.onClick(dialog, AlertDialog.BUTTON_NEUTRAL);
            }
        });
        neutralButton.setVisibility(View.VISIBLE);
    }

    public boolean isCancelable() {
        return isCancelable;
    }
}
