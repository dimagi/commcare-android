package org.commcare.android.framework;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.javarosa.core.services.locale.Localization;

import java.lang.reflect.Field;

/**
 * Functions for managing the setup and persistence of activity fields with
 * '@UiElement' annotations.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class ManagedUiFramework {

    public static boolean isManagedUi(Class c) {
        return c.isAnnotationPresent(ManagedUi.class);
    }

    public static void setContentView(CommCareActivity activity) {
        if (activity.usesUIController()) {
            activity.setContentView(
                    activity.getUIController().getClass().getAnnotation(ManagedUi.class).value());
        } else {
            activity.setContentView(
                    activity.getClass().getAnnotation(ManagedUi.class).value());
        }
    }

    /**
     * Set text for activity's UiElement annotated fields
     */
    public static void loadUiElements(CommCareActivity activity) {

        Class classHoldingFields = getClassHoldingFields(activity);

        for (Field f : classHoldingFields.getDeclaredFields()) {
            if (f.isAnnotationPresent(UiElement.class)) {
                UiElement element = f.getAnnotation(UiElement.class);
                try {
                    f.setAccessible(true);

                    try {
                        View v = activity.findViewById(element.value());
                        if (activity.usesUIController()) {
                            f.set(activity.getUIController(), v);
                        } else {
                            f.set(activity, v);
                        }

                        String localeString = element.locale();
                        if (!"".equals(localeString)) {
                            if (v instanceof EditText) {
                                ((EditText)v).setHint(Localization.get(localeString));
                            } else if (v instanceof TextView) {
                                ((TextView)v).setText(Localization.get(localeString));
                            } else {
                                throw new RuntimeException("Can't set the text for a " + v.getClass().getName() + " View!");
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                        throw new RuntimeException("Bad Object type for field " + f.getName());
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Couldn't access the activity field for some reason");
                    }
                } finally {
                    f.setAccessible(false);
                }
            }
        }
    }

    /**
     * Restore activity UiElement annotated fields with text, visibility, and
     * enabled settings stored in bundle
     */
    public static void restoreUiElements(CommCareActivity activity,
                                         Bundle savedInstanceState) {

        Class classHoldingFields = getClassHoldingFields(activity);

        for (Field f : classHoldingFields.getDeclaredFields()) {
            if (f.isAnnotationPresent(UiElement.class)) {
                UiElement element = f.getAnnotation(UiElement.class);
                try {
                    f.setAccessible(true);

                    try {
                        View v = activity.findViewById(element.value());
                        if (activity.usesUIController()) {
                            f.set(activity.getUIController(), v);
                        } else {
                            f.set(activity, v);
                        }

                        restoredFromSaved(v, f, element, savedInstanceState);
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                        throw new RuntimeException("Bad Object type for field " + f.getName());
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Couldn't access the activity field for some reason");
                    }
                } finally {
                    f.setAccessible(false);
                }
            }
        }
    }

    @SuppressWarnings("ResourceType")
    private static void restoredFromSaved(View v, Field f, UiElement element, Bundle bundle) {
        if (bundle != null) {
            if (v != null) {
                final String elementKey = getElementKey(element);
                if (isFieldInBundle(elementKey, bundle)) {
                    v.setVisibility(bundle.getInt(elementKey + "_visibility"));
                    v.setEnabled(bundle.getBoolean(elementKey + "_enabled"));
                    if (v instanceof TextView) {
                        ((TextView)v).setText(bundle.getString(elementKey + "_text"));
                    }
                }
            } else {
                Log.d("loadFields", "NullPointerException when trying to find view with id: " +
                        element.value() + ", element is: " + f + " (" + f.getName() + ")");
            }
        }
    }

    private static Class getClassHoldingFields(CommCareActivity activity) {
        CommCareActivityUIController uiController = activity.getUIController();
        if (uiController != null) {
            return uiController.getClass();
        } else {
            return activity.getClass();
        }
    }

    private static String getElementKey(UiElement element) {
        return String.valueOf(element.value());
    }

    private static boolean isFieldInBundle(String elementKey, Bundle bundle) {
        return bundle.containsKey(elementKey + "_visibility");
    }

    /**
     * Store the state of the activity's UiElement annotated fields.
     */
    public static Bundle saveUiStateToBundle(CommCareActivity activity) {

        Bundle bundle = new Bundle();
        Class classHoldingFields = getClassHoldingFields(activity);

        for (Field f : classHoldingFields.getDeclaredFields()) {
            if (f.isAnnotationPresent(UiElement.class)) {
                UiElement element = f.getAnnotation(UiElement.class);
                try {
                    f.setAccessible(true);
                    try {
                        View v;
                        if (activity.usesUIController()) {
                            v = (View)f.get(activity.getUIController());
                        } else {
                            v = (View)f.get(activity);
                        }
                        String elementKey = getElementKey(element);
                        int vis = v.getVisibility();
                        bundle.putInt(elementKey + "_visibility", vis);
                        boolean enabled = v.isEnabled();
                        bundle.putBoolean(elementKey + "_enabled", enabled);
                        if (v instanceof TextView) {
                            bundle.putString(elementKey + "_text", ((TextView)v).getText().toString());
                        }
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                        throw new RuntimeException("Bad Object type for field " + f.getName());
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Couldn't access the activity field for some reason");
                    }
                } finally {
                    f.setAccessible(false);
                }
            }
        }
        return bundle;
    }
}
