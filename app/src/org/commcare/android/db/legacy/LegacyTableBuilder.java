package org.commcare.android.db.legacy;

import android.util.Pair;

import org.commcare.modern.database.DatabaseHelper;
import org.commcare.modern.models.EncryptedModel;
import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.services.storage.Persistable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Vector;

/**
 * @author ctsims
 */
public class LegacyTableBuilder {

    private String name;

    private Vector<String> cols;
    private Vector<String> rawCols;

    public LegacyTableBuilder(String name) {
        this.name = name;
        cols = new Vector<String>();
        rawCols = new Vector<String>();
    }

    public void addData(Persistable p) {
        cols.add(DatabaseHelper.ID_COL + " INTEGER PRIMARY KEY");
        rawCols.add(DatabaseHelper.ID_COL);

        if (p instanceof IMetaData) {
            String[] keys = ((IMetaData) p).getMetaDataFields();
            for (String key : keys) {
                String columnName = scrubName(key);
                rawCols.add(columnName);
                String columnDef;
                if (p instanceof EncryptedModel && ((EncryptedModel) p).isEncrypted(key)) {
                    columnDef = columnName + " BLOB";
                } else {
                    columnDef = columnName;
                }

                //Modifiers
                if (unique.contains(columnName)) {
                    columnDef += " UNIQUE";
                }
                cols.add(columnDef);
            }
        }

        cols.add(DatabaseHelper.DATA_COL + " BLOB");
        rawCols.add(DatabaseHelper.DATA_COL);
    }

    HashSet<String> unique = new HashSet<String>();

    public void setUnique(String columnName) {
        unique.add(scrubName(columnName));
    }

    public String getTableCreateString() {

        String built = "CREATE TABLE " + scrubName(name) + " (";
        for (int i = 0; i < cols.size(); ++i) {
            built += cols.elementAt(i);
            if (i < cols.size() - 1) {
                built += ",";
            }
        }
        built += ");";
        return built;
    }

    public static String scrubName(String input) {
        //Scrub
        return input.replace("-", "_");
    }

    public static Pair<String, String[]> sqlList(Collection<Integer> input) {
        //I want list comprehensions so bad right now.
        String ret = "(";
        for (int i : input) {
            ret += "?" + ",";
        }

        String[] array = new String[input.size()];
        int count = 0;
        for (Integer i : input) {
            array[count++] = String.valueOf(i);
        }
        return new Pair<String, String[]>(ret.substring(0, ret.length() - 1) + ")", array);
    }

    public String getColumns() {
        String columns = "";
        for (int i = 0; i < rawCols.size(); ++i) {
            columns += rawCols.elementAt(i);
            if (i < rawCols.size() - 1) {
                columns += ",";
            }
        }
        return columns;
    }
}
