package org.commcare.views.media;

/**
 * Used to represent the unique id of each view in a ListAdapter
 *
 * @author amstone326
 */

public class ViewId {

    private final long rowId;
    private final long colId;
    private final boolean isDetail;

    public ViewId(long rowId, long colId, boolean isDetail) {
        this.rowId = rowId;
        this.colId = colId;
        this.isDetail = isDetail;
    }

    @Override
    public String toString() {
        return "(" + rowId + "," + colId + "," + isDetail + ")";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int)(colId ^ (colId >>> 32));
        result = prime * result + (isDetail ? 1231 : 1237);
        result = prime * result + (int)(rowId ^ (rowId >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ViewId other = (ViewId)obj;

        return colId == other.colId
                && isDetail == other.isDetail
                && rowId == other.rowId;
    }
}
