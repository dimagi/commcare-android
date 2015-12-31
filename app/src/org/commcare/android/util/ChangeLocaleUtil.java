package org.commcare.android.util;

import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.locale.Localizer;
import org.javarosa.core.util.NoLocalizedTextException;

public class ChangeLocaleUtil{
    
    public static String[] removeDefault(String[] raw){
        String[] output = new String[raw.length -1];
        int index = 0;
        for(int i=0; i<raw.length; i++){
            String rawInput = raw[i];
            if(!rawInput.equals("default")){
                output[index] = rawInput;
                index++;
            }
        }
        return output;
    }
    
    public static String[] translateLocales(String[] raw){
        String[] translated = new String[raw.length];
        for(int i=0;i<raw.length;i++){
            try{
                translated[i] = Localization.get(raw[i]);
            } catch(NoLocalizedTextException e){
                translated[i] = raw[i];
            }
        }
        return translated;
    }
    
    public static String[] getLocaleCodes(){
        Localizer lizer = Localization.getGlobalLocalizerAdvanced();
        String[] rawLocales = lizer.getAvailableLocales();
        return removeDefault(rawLocales);
    }
    
    public static String[] getLocaleNames(){
        String[] rawDefaultRemoved = getLocaleCodes();
        return translateLocales(rawDefaultRemoved);
    }

}
