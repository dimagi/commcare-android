package org.commcare.activities.components;

import android.view.Menu;

public class FormEntryConstants {
    // Defines for FormEntryActivity
    public static final boolean EXIT = true;
    public static final boolean DO_NOT_EXIT = false;
    public static final boolean EVALUATE_CONSTRAINTS = true;
    public static final boolean DO_NOT_EVALUATE_CONSTRAINTS = false;

    // Request codes for returning data from specified intent.
    public static final int IMAGE_CAPTURE = 1;
    public static final int AUDIO_VIDEO_FETCH = 3;
    public static final int LOCATION_CAPTURE = 5;
    public static final int HIERARCHY_ACTIVITY = 6;
    public static final int IMAGE_CHOOSER = 7;
    public static final int FORM_PREFERENCES_KEY = 8;
    public static final int INTENT_CALLOUT = 10;
    public static final int HIERARCHY_ACTIVITY_FIRST_START = 11;
    public static final int SIGNATURE_CAPTURE = 12;
    public static final int INTENT_COMPOUND_CALLOUT = 13;

    public static final String NAV_STATE_NEXT = "next";
    public static final String NAV_STATE_DONE = "done";
    public static final String NAV_STATE_QUIT = "quit";
    public static final String NAV_STATE_BACK = "back";

    public static final int MENU_LANGUAGES = Menu.FIRST + 1;
    public static final int MENU_HIERARCHY_VIEW = Menu.FIRST + 2;
    public static final int MENU_SAVE = Menu.FIRST + 3;
    public static final int MENU_PREFERENCES = Menu.FIRST + 4;

    // Extra returned from gp activity
    public static final String LOCATION_RESULT = "LOCATION_RESULT";

    /**
     * Intent extra flag to track if this form is an archive. Used to trigger
     * return logic when this activity exits to the home screen, such as
     * whether to redirect to archive view or sync the form.
     */
    public static final String IS_ARCHIVED_FORM = "is-archive-form";

    public final static String HIERARCHY_ACTIVITY_LAUNCH_MODE = "hierarchy-activity-launch-mode";

}
