package org.odk.collect.android.views.media;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Used to represent the unique id of each view in a ListAdapter
 * 
 * @author amstone326
 *
 */

public class ViewId {
    
    private long rowId;
    private long colId;
    private boolean isDetail;
    
    public ViewId(long a, long b, boolean isDetail) {
        rowId = a;
        colId = b;
        this.isDetail = isDetail;
    }
    
    @NonNull
    @Override
    public String toString() {
        return "(" + getRow() + "," + getCol() + "," + getDetailBool() + ")";  
    }

    
    public boolean getDetailBool() {
        return isDetail;
    }
    
    public long getRow() {
        return rowId;
    }
    
    public long getCol() {
        return colId;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (colId ^ (colId >>> 32));
        result = prime * result + (isDetail ? 1231 : 1237);
        result = prime * result + (int) (rowId ^ (rowId >>> 32));
        return result;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ViewId other = (ViewId) obj;
        if (colId != other.colId)
            return false;
        if (isDetail != other.isDetail)
            return false;
        if (rowId != other.rowId)
            return false;
        return true;
    }
    
    


}
