/**
 *
 */
package org.commcare.android.database;

import android.util.Pair;

import org.commcare.android.storage.framework.MetaField;
import org.commcare.android.storage.framework.Table;
import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.services.storage.Persistable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

/**
 * @author ctsims
 */
public class TableBuilder {

    private String name;
    private Class c;

    private Vector<String> cols;
    private Vector<String> rawCols;

    public TableBuilder(Class c) {
        this.c = c;
        Table t = (Table) c.getAnnotation(Table.class);
        this.name = t.value();

        cols = new Vector<String>();
        rawCols = new Vector<String>();

        addData(c);
    }

    public void addData(Class c) {
        cols.add(DbUtil.ID_COL + " INTEGER PRIMARY KEY");
        rawCols.add(DbUtil.ID_COL);

        for (Field f : c.getDeclaredFields()) {
            if (f.isAnnotationPresent(MetaField.class)) {
                MetaField mf = f.getAnnotation(MetaField.class);
                addMetaField(mf);
            }
        }

        for (Method m : c.getDeclaredMethods()) {
            if (m.isAnnotationPresent(MetaField.class)) {
                MetaField mf = m.getAnnotation(MetaField.class);
                addMetaField(mf);
            }
        }

        cols.add(DbUtil.DATA_COL + " BLOB");
        rawCols.add(DbUtil.DATA_COL);
    }


    private void addMetaField(MetaField mf) {
        String key = mf.value();
        String columnName = scrubName(key);
        rawCols.add(columnName);
        String columnDef;
        columnDef = columnName;

        //Modifiers
        if (unique.contains(columnName) || mf.unique()) {
            columnDef += " UNIQUE";
        }
        cols.add(columnDef);
    }

    //Option Two - For models not made natively
    public TableBuilder(String name) {
        this.name = name;
        cols = new Vector<String>();
        rawCols = new Vector<String>();
    }

    public void addData(Persistable p) {
        cols.add(DbUtil.ID_COL + " INTEGER PRIMARY KEY");
        rawCols.add(DbUtil.ID_COL);

        if (p instanceof IMetaData) {
            String[] keys = ((IMetaData) p).getMetaDataFields();
            for (String key : keys) {
                String columnName = scrubName(key);
                rawCols.add(columnName);
                String columnDef = columnName;

                //Modifiers
                if (unique.contains(columnName)) {
                    columnDef += " UNIQUE";
                }
                cols.add(columnDef);
            }
        }

        cols.add(DbUtil.DATA_COL + " BLOB");
        rawCols.add(DbUtil.DATA_COL);
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

    //TODO: Read this from SQL, not assume from context
    private static final int MAX_SQL_ARGS = 950;

    public static List<Pair<String, String[]>> sqlList(List<Integer> input) {
        return sqlList(input, MAX_SQL_ARGS);
    }

    public static List<Pair<String, String[]>> sqlList(List<Integer> input, int maxArgs) {

        List<Pair<String, String[]>> ops = new ArrayList<Pair<String, String[]>>();

        //figure out how many iterations we'll need
        int numIterations = (int) Math.ceil(((double) input.size()) / maxArgs);

        for (int currentRound = 0; currentRound < numIterations; ++currentRound) {

            int startPoint = currentRound * maxArgs;
            int lastIndex = Math.min((currentRound + 1) * maxArgs, input.size());

            String ret = "(";
            for (int i = startPoint; i < lastIndex; ++i) {
                ret += "?" + ",";
            }

            String[] array = new String[lastIndex - startPoint];
            int count = 0;
            for (int i = startPoint; i < lastIndex; ++i) {
                array[count++] = String.valueOf(input.get(i));
            }

            ops.add(new Pair<String, String[]>(ret.substring(0, ret.length() - 1) + ")", array));

        }
        return ops;
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
