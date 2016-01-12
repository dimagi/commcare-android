package org.odk.collect.android.jr.extensions;

import android.content.Context;

import org.commcare.dalvik.R;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.condition.IFunctionHandler;
import org.javarosa.xpath.XPathUnsupportedException;
import org.javarosa.xpath.expr.XPathFuncExpr;
import org.odk.collect.android.utilities.EthiopianDateHelper;
import org.odk.collect.android.utilities.NepaliDateUtilities;

import java.util.Date;
import java.util.Vector;

/**
 * @author ctsims
 *
 */
public class CalendaredDateFormatHandler implements IFunctionHandler {
    
    private final Context context;
    
    public CalendaredDateFormatHandler(Context context) {
        this.context = context;
    }
        @Override
        public String getName() {
            return "format-date-for-calendar";
        }

        @Override
        public Vector getPrototypes() {
            Vector v = new Vector();
            v.add(new Class[] {Date.class, String.class});
            return v;
        }

        @Override
        public boolean rawArgs() {
            return false;
        }

        @Override
        public Object eval(Object[] args, EvaluationContext ec) {
            if("".equals(args[0])) { return "";}
            Date d = (Date)XPathFuncExpr.toDate(args[0]);
            String calendar = (String)args[1];
            if("ethiopian".equals(calendar)) {
                return EthiopianDateHelper.ConvertToEthiopian(context, d);
            } else if ("nepali".equals(calendar)) {
                return NepaliDateUtilities.convertToNepaliString(
                        context.getResources().getStringArray(R.array.nepali_months), d);
            } else {
                throw new XPathUnsupportedException("Unsupported calendar type: " + calendar);
            }
        }
}
