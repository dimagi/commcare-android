package org.odk.collect.android.utilities;

import org.joda.time.DateTime;

import java.sql.Timestamp;

/**
 * Created by willpride on 10/13/15.
 */
public class SqlUtils {
    // Converts a DateTime object into a SQL-format friendly string.
    // Return format looks like this: 2014-01-22 10:05:34.546
    // http://stackoverflow.com/questions/3780120/how-do-i-convert-a-joda-time-datetime-object-into-a-string-in-sql-server-format
    public static String datetimeToSqlString(DateTime dateTime) {
        return new Timestamp( dateTime.getMillis() ).toString();
    }
}
