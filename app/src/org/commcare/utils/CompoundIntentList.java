package org.commcare.utils;

import android.content.Intent;
import android.text.Spannable;

import java.util.ArrayList;

/**
 * A wrapper object which compounds multiple intent callouts into a single "compound" intent which
 * contains the relevant bundle data associated with each question.
 *
 * Only intents which share a common action are capable of being compounded.
 *
 * Created by ctsims on 7/18/2016.
 */
public class CompoundIntentList {
    public static final String EXTRA_COMPOUND_DATA_INDICES = "cc:compound_extra_indices";

    Intent intent;
    private String title;

    public CompoundIntentList(Intent baseIntent, String index) {
        copyFromIntent(baseIntent, index);
        addIntentIfCompatible(baseIntent, index);
    }

    /**
     * @return true if the intent was compatible and it was added to this compound set, false if it
     * was not compatible with this set
     */
    public boolean addIntentIfCompatible(Intent toAdd, String index) {
        if(!toAdd.getAction().equals(intent.getAction())) {
            return false;
        }
        ArrayList<String>  indices = intent.getStringArrayListExtra(EXTRA_COMPOUND_DATA_INDICES);

        indices.add(index);
        intent.putExtra(index, toAdd.getExtras());
        intent.putExtra(EXTRA_COMPOUND_DATA_INDICES, indices);
        return true;
    }

    private void copyFromIntent(Intent template, String index) {
        intent = new Intent(template.getAction(), template.getData());
        intent.setType(template.getType());
        intent.setComponent(template.getComponent());
        intent.setFlags(template.getFlags());
        intent.putStringArrayListExtra(EXTRA_COMPOUND_DATA_INDICES, new ArrayList<String>());
    }

    public int getNumberOfCallouts() {
        return intent.getStringArrayListExtra(EXTRA_COMPOUND_DATA_INDICES).size();
    }

    public Intent getCompoundedIntent() {
        return intent;
    }

    public static boolean isIntentCompound(Intent intent) {
        return intent.getStringArrayListExtra(EXTRA_COMPOUND_DATA_INDICES) != null;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return this.title;
    }
}
