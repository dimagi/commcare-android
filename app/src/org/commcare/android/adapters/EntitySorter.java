package org.commcare.android.adapters;

import org.commcare.android.models.Entity;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.suite.model.DetailField;
import org.javarosa.core.model.Constants;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.xpath.XPathTypeMismatchException;
import org.javarosa.xpath.expr.XPathFuncExpr;

import java.util.Comparator;

public class EntitySorter implements Comparator<Entity<TreeReference>> {
    private final DetailField[] detailFields;
    private final boolean reverseSort;
    private final int[] currentSort;
    private boolean hasWarned;

    public EntitySorter(DetailField[] detailFields, boolean reverseSort, int[] currentSort) {
        this.detailFields = detailFields;
        this.currentSort = currentSort;
        this.reverseSort = reverseSort;
    }

    @Override
    public int compare(Entity<TreeReference> object1, Entity<TreeReference> object2) {
        for (int aCurrentSort : currentSort) {
            boolean reverseLocal = (detailFields[aCurrentSort].getSortDirection() == DetailField.DIRECTION_DESCENDING) ^ reverseSort;
            int cmp = (reverseLocal ? -1 : 1) * getCmp(object1, object2, aCurrentSort);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private int getCmp(Entity<TreeReference> object1, Entity<TreeReference> object2, int index) {
        int sortType = detailFields[index].getSortType();

        String a1 = object1.getSortField(index);
        String a2 = object2.getSortField(index);

        // If one of these is null, we need to get the field in the same index, not the field in SortType
        if (a1 == null) {
            a1 = object1.getFieldString(index);
        }
        if (a2 == null) {
            a2 = object2.getFieldString(index);
        }

        //TODO: We might want to make this behavior configurable (Blanks go first, blanks go last, etc);
        //For now, regardless of typing, blanks are always smaller than non-blanks
        if (a1.equals("")) {
            if (a2.equals("")) {
                return 0;
            } else {
                return -1;
            }
        } else if (a2.equals("")) {
            return 1;
        }

        Comparable c1 = applyType(sortType, a1);
        Comparable c2 = applyType(sortType, a2);

        if (c1 == null || c2 == null) {
            //Don't do something smart here, just bail.
            return -1;
        }

        return c1.compareTo(c2);
    }

    private Comparable applyType(int sortType, String value) {
        try {
            if (sortType == Constants.DATATYPE_TEXT) {
                return value.toLowerCase();
            } else if (sortType == Constants.DATATYPE_INTEGER) {
                //Double int compares just fine here and also
                //deals with NaN's appropriately

                double ret = XPathFuncExpr.toInt(value);
                if (Double.isNaN(ret)) {
                    String[] stringArgs = new String[3];
                    stringArgs[2] = value;
                    if (!hasWarned) {
                        CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(NotificationMessageFactory.StockMessages.Bad_Case_Filter, stringArgs));
                        hasWarned = true;
                    }
                }
                return ret;
            } else if (sortType == Constants.DATATYPE_DECIMAL) {
                double ret = XPathFuncExpr.toDouble(value);
                if (Double.isNaN(ret)) {

                    String[] stringArgs = new String[3];
                    stringArgs[2] = value;
                    if (!hasWarned) {
                        CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(NotificationMessageFactory.StockMessages.Bad_Case_Filter, stringArgs));
                        hasWarned = true;
                    }
                }
                return ret;
            } else {
                //Hrmmmm :/ Handle better?
                return value;
            }
        } catch (XPathTypeMismatchException e) {
            //So right now this will fail 100% silently, which is bad.
            return null;
        }
    }
}
