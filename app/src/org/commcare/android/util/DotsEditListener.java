/**
 * 
 */
package org.commcare.android.util;

import org.commcare.android.util.DotsData.DotsDay;

import android.graphics.Rect;

/**
 * @author ctsims
 *
 */
public interface DotsEditListener {
    public void editDotsDay(int i, Rect datRect);
    public void editDose(int dayIndex, int regimenIndex, DotsDay day, Rect hitRect);
    public void doneWithDOTS();
    public void cancelDayEdit(int day);
    public void cancelDoseEdit();
    public void dayEdited(int i, DotsDay day);
    public void shiftDay(int delta);
}
