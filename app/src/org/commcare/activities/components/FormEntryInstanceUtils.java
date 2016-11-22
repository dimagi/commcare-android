package org.commcare.activities.components;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Pair;
import android.view.View;

import org.commcare.activities.FormEntryActivity;
import org.commcare.dalvik.R;
import org.commcare.logging.analytics.GoogleAnalyticsFields;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.commcare.provider.FormsProviderAPI;
import org.commcare.provider.InstanceProviderAPI;
import org.commcare.utils.StringUtils;
import org.commcare.views.dialogs.DialogChoiceItem;
import org.commcare.views.dialogs.PaneledChoiceDialog;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class FormEntryInstanceUtils {
    /**
     * Checks the database to determine if the current instance being edited has already been
     * 'marked completed'. A form can be 'unmarked' complete and then resaved.
     *
     * @return true if form has been marked completed, false otherwise.
     */
    public static boolean isInstanceComplete(Context context, Uri instanceProviderContentURI) {
        // default to false if we're mid form
        boolean complete = false;

        // Then see if we've already marked this form as complete before
        String selection = InstanceProviderAPI.InstanceColumns.INSTANCE_FILE_PATH + "=?";
        String[] selectionArgs = {
                FormEntryInstanceState.mInstancePath
        };

        Cursor c = null;
        try {
            c = context.getContentResolver().query(instanceProviderContentURI, null, selection, selectionArgs, null);
            if (c != null && c.getCount() > 0) {
                c.moveToFirst();
                String status = c.getString(c.getColumnIndex(InstanceProviderAPI.InstanceColumns.STATUS));
                if (InstanceProviderAPI.STATUS_COMPLETE.compareTo(status) == 0) {
                    complete = true;
                }
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return complete;
    }

    public static Pair<Uri, Boolean> getInstanceUri(Context context, Uri uri,
                                                    Uri formProviderContentURI,
                                                    FormEntryInstanceState instanceState)
            throws FormEntryActivity.FormQueryException {
        Cursor instanceCursor = null;
        Cursor formCursor = null;
        Boolean isInstanceReadOnly = false;
        Uri formUri = null;
        try {
            instanceCursor = context.getContentResolver().query(uri, null, null, null, null);
            if (instanceCursor == null) {
                throw new FormEntryActivity.FormQueryException("Bad URI: resolved to null");
            } else if (instanceCursor.getCount() != 1) {
                throw new FormEntryActivity.FormQueryException("Bad URI: " + uri);
            } else {
                instanceCursor.moveToFirst();
                FormEntryInstanceState.mInstancePath =
                        instanceCursor.getString(instanceCursor
                                .getColumnIndex(InstanceProviderAPI.InstanceColumns.INSTANCE_FILE_PATH));

                final String jrFormId =
                        instanceCursor.getString(instanceCursor
                                .getColumnIndex(InstanceProviderAPI.InstanceColumns.JR_FORM_ID));


                //If this form is both already completed
                if (InstanceProviderAPI.STATUS_COMPLETE.equals(instanceCursor.getString(instanceCursor.getColumnIndex(InstanceProviderAPI.InstanceColumns.STATUS)))) {
                    if (!Boolean.parseBoolean(instanceCursor.getString(instanceCursor.getColumnIndex(InstanceProviderAPI.InstanceColumns.CAN_EDIT_WHEN_COMPLETE)))) {
                        isInstanceReadOnly = true;
                    }
                }
                final String[] selectionArgs = {
                        jrFormId
                };
                final String selection = FormsProviderAPI.FormsColumns.JR_FORM_ID + " like ?";

                formCursor = context.getContentResolver().query(formProviderContentURI, null, selection, selectionArgs, null);
                if (formCursor == null || formCursor.getCount() < 1) {
                    throw new FormEntryActivity.FormQueryException("Parent form does not exist");
                } else if (formCursor.getCount() == 1) {
                    formCursor.moveToFirst();
                    instanceState.setFormPath(
                            formCursor.getString(formCursor
                                    .getColumnIndex(FormsProviderAPI.FormsColumns.FORM_FILE_PATH)));
                    formUri = ContentUris.withAppendedId(formProviderContentURI, formCursor.getLong(formCursor.getColumnIndex(FormsProviderAPI.FormsColumns._ID)));
                } else if (formCursor.getCount() > 1) {
                    throw new FormEntryActivity.FormQueryException("More than one possible parent form");
                }
            }
        } finally {
            if (instanceCursor != null) {
                instanceCursor.close();
            }
            if (formCursor != null) {
                formCursor.close();
            }
        }
        return new Pair<>(formUri, isInstanceReadOnly);
    }

    /**
     * Get the default title for ODK's "Form title" field
     */
    public static String getDefaultFormTitle(Context context, Intent intent) {
        String saveName = FormEntryActivity.mFormController.getFormTitle();
        if (InstanceProviderAPI.InstanceColumns.CONTENT_ITEM_TYPE.equals(context.getContentResolver().getType(intent.getData()))) {
            Uri instanceUri = intent.getData();

            Cursor instance = null;
            try {
                instance = context.getContentResolver().query(instanceUri, null, null, null, null);
                if (instance != null && instance.getCount() == 1) {
                    instance.moveToFirst();
                    saveName =
                            instance.getString(instance
                                    .getColumnIndex(InstanceProviderAPI.InstanceColumns.DISPLAY_NAME));
                }
            } finally {
                if (instance != null) {
                    instance.close();
                }
            }
        }
        return saveName;
    }

}
