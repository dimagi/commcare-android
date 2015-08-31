package org.commcare.android.util;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;

public class Stylizer {
    private HashMap<String, String> globalStyleTable;
    private String globalStyleString;

    public Stylizer(Context c) {

        globalStyleString = "";

        if (globalStyleTable == null) {
            globalStyleTable = new HashMap<String, String>();
        }

        ArrayList<String> mStyles = new ArrayList<String>();

        try {
            BufferedReader bReader = new BufferedReader(new InputStreamReader(c.getAssets().open("app_styles.txt")));
            ArrayList<String> values = new ArrayList<String>();
            String line = bReader.readLine();
            while (line != null) {
                values.add(line);
                line = bReader.readLine();
            }
            bReader.close();
            for (String v : values) {
                mStyles.add(v);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < mStyles.size(); i++) {
            String style = mStyles.get(i);
            String key = style.substring(0, style.indexOf("="));
            String val = style.substring(style.indexOf('=') + 1);
            globalStyleTable.put(key, val);
            globalStyleString += MarkupUtil.formatKeyVal(key, val);
        }
    }

    public String getStyle(String key) {
        return globalStyleTable.get(key);
    }

    public String getStyleString() {
        return globalStyleString;
    }
}
