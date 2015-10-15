/**
 *
 */
package org.commcare.android.database;

import android.util.Pair;

import org.commcare.android.storage.framework.Table;
import org.commcare.modern.database.TableBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Some additional table methods we need only on Androidp
 *
 * @author ctsims
 * @author wspride
 *
 */
public class AndroidTableBuilder extends TableBuilder {
    public AndroidTableBuilder(Class c) {
        super(c, ((Table) c.getAnnotation(Table.class)).value());
    }

    public AndroidTableBuilder(String name) {
        super(name);
    }

    //TODO: Read this from SQL, not assume from context
    private static final int MAX_SQL_ARGS = 950;

    public static List<Pair<String, String[]>> sqlList(List<Integer> input) {
        return sqlList(input, MAX_SQL_ARGS);
    }

    /**
     * Given a list of integer params to insert and a maximum number of args, return the
     * String containing (?, ?,...) to be used in the SQL query and the array of args
     * to replace them with
     */
    public static List<Pair<String, String[]>> sqlList(List<Integer> input, int maxArgs) {

        List<Pair<String, String[]>> ops = new ArrayList<Pair<String, String[]>>();

        //figure out how many iterations we'll need
        int numIterations = (int)Math.ceil(((double)input.size()) / maxArgs);

        for(int currentRound = 0 ; currentRound < numIterations ; ++currentRound) {

            int startPoint = currentRound * maxArgs;
            int lastIndex = Math.min((currentRound + 1) * maxArgs, input.size());

            String ret = "(";
            for(int i = startPoint ; i < lastIndex ; ++i) {
                ret += "?" + ",";
            }

            String[] array = new String[lastIndex - startPoint];
            int count = 0 ;
            for(int i = startPoint ; i < lastIndex ; ++i) {
                array[count++] = String.valueOf(input.get(i));
            }

            ops.add(new Pair<String, String[]>(ret.substring(0, ret.length()-1) + ")", array));

        }
        return ops;
    }
}
