package org.odk.collect.android.views.media;

import org.javarosa.core.services.Logger;

public class AudioDebugUtils {
    public static void logAction(String action, String URI) {
        String message = action + " " + URI;
        Integer progress = AudioDebugUtils.getProgress();
        Integer duration = getDuration();
        if (progress != null && duration != null) {
            message += " " + formatTime(progress) + "/" + formatTime(duration);
        }
        Logger.log("media", message);
    }

    private static Integer getDuration() {
        MediaEntity currentEntity = AudioControllerSingleton.INSTANCE.getCurrMedia();
        if (currentEntity != null) {
            return currentEntity.getPlayer().getDuration();
        }
        return null;
    }

    private static Integer getProgress() {
        MediaEntity currentEntity = AudioControllerSingleton.INSTANCE.getCurrMedia();
        if (currentEntity != null) {
            return currentEntity.getPlayer().getCurrentPosition();
        }
        return null;
    }

    private static String formatTime(Integer milliseconds) {
        if (milliseconds == null) {
            return "";
        }
        int numSeconds = Math.round(milliseconds);
        int hours = (numSeconds / 3600);
        int minutes = (numSeconds / 60);
        int seconds = numSeconds % 60;
        String returnValue = "";
        returnValue += seconds;
        if (seconds < 10) {
            returnValue = "0" + returnValue;
        }
        returnValue = minutes + ":" + returnValue;
        if (hours > 0) {
            if (minutes < 10) {
                returnValue += "0" + returnValue;
            }
            returnValue += hours + ":" + returnValue;
        }
        return returnValue;
    }
}
