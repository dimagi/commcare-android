package org.commcare.android.resource.installers;

import android.support.v4.util.Pair;

import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceLocation;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import java.io.File;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class FileSystemUtils {

    //TODO: Put files into an arbitrary name and keep the reference. This confuses things too much
    protected static Pair<String, String> getResourceName(Resource r, ResourceLocation loc) {
        String input = loc.getLocation();
        String extension = "";
        int lastDot = input.lastIndexOf(".");
        if (lastDot != -1) {
            extension = input.substring(lastDot);
        }
        return new Pair<>(r.getResourceId(), extension(extension));
    }

    //Hate this
    private static final String validExtChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static String extension(String input) {
        int invalid = -1;
        //we wanna go from the last "." to the next non-alphanumeric character.
        for (int i = 1; i < input.length(); ++i) {
            if (validExtChars.indexOf(input.charAt(i)) == -1) {
                invalid = i;
                break;
            }
        }
        if (invalid == -1) {
            return input;
        }
        return input.substring(0, invalid);
    }

    protected static boolean moveFrom(String oldLocation, String newLocation, boolean force) throws InvalidReferenceException {
        File newFile = new File(ReferenceManager._().DeriveReference(newLocation).getLocalURI());
        File oldFile = new File(ReferenceManager._().DeriveReference(oldLocation).getLocalURI());

        if (!oldFile.exists()) {
            //Nothing should be allowed to exist in the new location except for the incoming file
            //due to the staging rules. If there's a file there, it's this one.
            return newFile.exists();
        }

        if (oldFile.exists() && newFile.exists()) {
            //There's a destination file where this file is
            //trying to move to. Something might have failed to unstage
            if (force) {
                //If we're recovering or something, wipe out the destination.
                //we've gotta recover!
                if (!newFile.delete()) {
                    return false;
                } else {
                    //new file is gone. Let's get ours in there!
                }
            } else {
                //can't copy over an existing file. An unstage might have failed.
                return false;
            }
        }

        return oldFile.renameTo(newFile);
    }
}
