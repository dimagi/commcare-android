package org.commcare.android.util;

import android.graphics.Rect;

import org.commcare.android.util.DotsData.DotsDay;

/**
 * @author ctsims
 *
 */
public interface DotsEditListener {
    void editDotsDay(int i, Rect datRect);
    void editDose(int dayIndex, int regimenIndex, DotsDay day, Rect hitRect);
    void doneWithDOTS();
    void cancelDayEdit(int day);
    void cancelDoseEdit();
    void dayEdited(int i, DotsDay day);
    void shiftDay(int delta);
}
