package org.commcare.views.widgets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

/**
 * Created by Saumya on 7/29/2016.
 */
public class DateListHelper {

    //Android Calendar class is awful, so this method takes day or month names from that class and sorts them in chronological order
    public static void sortCalendarItems(final Map<String, Integer> monthMap, ArrayList<String> monthList) {
        Collections.sort(monthList, new Comparator<String>(){
            @Override
            public int compare(String a, String b){
                return monthMap.get(a) - monthMap.get(b);
            }
        });
    }
}
