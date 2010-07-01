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
	public void doneWithWeek();
	public void cancelDayEdit();
	public void dayEdited(int i, DotsDay day);
}
