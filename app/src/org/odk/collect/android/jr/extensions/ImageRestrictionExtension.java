package org.odk.collect.android.jr.extensions;

import org.javarosa.core.model.QuestionDataExtension;

/**
 * Created by amstone326 on 8/20/15.
 */
public class ImageRestrictionExtension implements QuestionDataExtension {

    private int maxDimen;

    public ImageRestrictionExtension(int maxDimen) {
        this.maxDimen = maxDimen;
    }

    public int getMaxDimen() {
        return this.maxDimen;
    }
}
