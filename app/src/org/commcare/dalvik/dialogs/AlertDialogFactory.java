package org.commcare.dalvik.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.javarosa.core.services.locale.Localization;

/**
 * Created by amstone326 on 10/9/15.
 */
public class AlertDialogFactory {

    private AlertDialog dialog;
    private View view;

    public static void showBasicAlertDialog(Activity context, String title, String msg,
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
        factory.showDialog();
    }

    public static void showBasicAlertWithIcon(Activity context, String title, String msg, int iconResId,
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
        factory.showDialog();
    }

    public AlertDialogFactory(Activity context, String title, String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        view = LayoutInflater.from(context).inflate(R.layout.custom_alert_dialog, null);

        TextView titleView = (TextView) view.findViewById(R.id.dialog_title);
        titleView.setText(title);
        TextView messageView = (TextView) view.findViewById(R.id.dialog_message);
        messageView.setText(msg);

        this.dialog = builder.create();
        dialog.setCancelable(false); // false by default, can change using makeCancelable()
    }

    public void showDialog() {
        dialog.setView(this.view);
        dialog.show();
    }

    public AlertDialog getDialog() {
        return this.dialog;
    }

    public void makeCancelable() {
        dialog.setCancelable(true);
    }

    public void setOnCancelListener(DialogInterface.OnCancelListener cancelListener) {
        dialog.setOnCancelListener(cancelListener);
    }

    public void setIcon(int resId) {
        dialog.setIcon(resId);
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


}
