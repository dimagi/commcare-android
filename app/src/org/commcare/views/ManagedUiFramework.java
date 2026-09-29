package org.commcare.views;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.commcare.activities.CommCareActivity;
import org.javarosa.core.services.locale.Localization;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

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
        String layoutName = activity.getUIManager().getClass().getAnnotation(ManagedUi.class).value();
        int layoutId = activity.getResources().getIdentifier(layoutName, "layout", activity.getPackageName());
        activity.setContentView(layoutId);
    }

    private static List<Field> getAnnotatedFields(Class<?> cls) {
        List<Field> fields = new ArrayList<>();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.isAnnotationPresent(UiElement.class)) {
                    fields.add(f);
                }
            }
            cls = cls.getSuperclass();
        }
        return fields;
    }

    private static int resolveViewId(CommCareActivity activity, String idName) {
        return activity.getResources().getIdentifier(idName, "id", activity.getPackageName());
    }

    /**
     * Set text for activity's UiElement annotated fields
     */
    public static void loadUiElements(CommCareActivity activity) {
        for (Field f : getAnnotatedFields(activity.getUIManager().getClass())) {
            UiElement element = f.getAnnotation(UiElement.class);
            try {
                f.setAccessible(true);

                try {
                    View v = activity.findViewById(resolveViewId(activity, element.value()));
                    f.set(activity.getUIManager(), v);

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

    /**
     * Restore activity UiElement annotated fields with text, visibility, and
     * enabled settings stored in bundle
     */
    public static void restoreUiElements(CommCareActivity activity,
                                         Bundle savedInstanceState) {
        for (Field f : getAnnotatedFields(activity.getUIManager().getClass())) {
            UiElement element = f.getAnnotation(UiElement.class);
            try {
                f.setAccessible(true);

                try {
                    View v = activity.findViewById(resolveViewId(activity, element.value()));
                    f.set(activity.getUIManager(), v);
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

    @SuppressWarnings("ResourceType")
    private static void restoredFromSaved(View v, Field f, UiElement element, Bundle bundle) {
        if (bundle != null) {
            if (v != null) {
                final String elementKey = element.value();
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

    private static boolean isFieldInBundle(String elementKey, Bundle bundle) {
        return bundle.containsKey(elementKey + "_visibility");
    }

    /**
     * Store the state of the activity's UiElement annotated fields.
     */
    public static Bundle saveUiStateToBundle(CommCareActivity activity) {

        Bundle bundle = new Bundle();
        Object objectHoldingFields = activity.getUIManager();

        for (Field f : getAnnotatedFields(objectHoldingFields.getClass())) {
            UiElement element = f.getAnnotation(UiElement.class);
            try {
                f.setAccessible(true);
                try {
                    View v = (View)f.get(objectHoldingFields);
                    String elementKey = element.value();
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
        return bundle;
    }
}
