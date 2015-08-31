/**
 *
 */
package org.commcare.android.database;

import android.database.Cursor;

import org.javarosa.core.services.storage.Persistable;

/**
 * A index spanning iterator is a special kind of iterator that is used on densely packed tables
 * in order to not have to maintain a specific value for each ID.
 * <p/>
 * If you have the ID set
 * [1, 2, 3, 5, 6, 7, 10, 11]
 * <p/>
 * in your table, this iterator produces those id's from the return set
 * [4, 8, 9, 12]
 * <p/>
 * which is notable less verbose for densely packed datasets.
 * <p/>
 * This cursor is not threadsafe.
 *
 * @author ctsims
 */
public class IndexSpanningIterator<T extends Persistable> extends SqlStorageIterator<T> {

    Cursor mCursor;
    SqlStorage<T> storage;
    boolean isClosedByProgress = false;

    /**
     * Total expected records *
     */
    int count;

    /**
     * The largest integer that is _not_ included in the walk *
     */
    int end;

    /**
     * The walker. Must be an integer which is included in the result set
     * while the iterator is still valid/open.
     */
    int current;

    /**
     * The next integer which will _not_ be included in the result set
     */
    int nextGap;

    /**
     * Create an iterator that will walk and return Id's between minValue (inclusive) and max value (exclusive)
     * <p/>
     * The input cursor of gaps should contain an entry for the last ID to be excluded, and should be ordered
     * <p/>
     * if the cursor is empty, no ids are iterated over.
     *
     * @param c          - A cursor set with one column of integer values, which are gaps between minValue and maxValue.
     * @param storage    - The storage being iterated over
     * @param minValue   - The first (lowest) id value to be included in the result set. Must be smaller than the first gap
     * @param maxValue   - The last (largest) id value to be included in the result set
     * @param countValue - The number of ID's in the set
     */
    public IndexSpanningIterator(Cursor c, SqlStorage<T> storage, int minValue, int maxValue, int countValue) {
        current = minValue;

        end = maxValue;
        count = countValue;
        this.c = c;
        this.storage = storage;

        //If there's no input, there's no values to iterate over
        if (!c.moveToNext()) {
            current = end = nextGap = -1;
            isClosedByProgress = true;
            c.close();
        } else {
            //Otherwise our next gap is the first cursor record
            nextGap = c.getInt(0);
        }
    }

    @Override
    public boolean hasMore() {
        //See whether we're ahead of the next gap. If we are, there are valid
        //ids remaining
        return nextGap > current;
    }

    /**
     * When currently on a gap, move the current point up to another valid
     * entry if possible.
     * <p/>
     * This method will either result in the iterator pointing to a new valid
     * record, or being closed.
     */
    private void expandToGap() {
        //If this is closed, we can't grow anymore
        if (isClosedByProgress) {
            return;
        }

        //otherwise we need to assume that we're at the gap, 
        //otherwise we shouldn't be growing
        if (nextGap != current) {
            return;
        }

        //Assume the current id is invalid (IE: points at a gap)
        boolean currentIsValid = false;

        //current points at a gap now, which means that it must 
        //be invalid. Either we will get current into a valid
        //state or the iterator needs to close
        while (c.moveToNext()) {
            int upcomingGap = c.getInt(0);
            if (nextGap + 1 == upcomingGap) {
                //Adjacent gaps with no valid records, we just 
                //want to keep going
                nextGap = upcomingGap;
            } else {
                //Otherwise we know that the next record must be valid unless
                //the next gap is larger than the end
                if (upcomingGap > end) {
                    nextGap = upcomingGap;
                    break;
                }

                //the step after next gap isn't the upcoming gap so it
                //must be valid
                current = nextGap + 1;

                //Set the next gap to be the upcoming one (after current)
                nextGap = upcomingGap;

                //Mark the iterator's progress as valid, since we know the current
                //record exists and there is a gap set
                currentIsValid = true;
                break;
            }
        }

        //If we didn't end up in a valid state it means that this iterator is done,
        //so we should clean up the cursor and signal that.
        if (!currentIsValid) {
            c.close();
            this.isClosedByProgress = true;
            current = nextGap;
        }
    }

    @Override
    public int nextID() {
        int ret = current;
        current++;
        //If we just hit the gap we need
        //to either grow it or we need the iterator
        //to terminate
        if (current == nextGap) {
            expandToGap();
        }
        return ret;
    }

    @Override
    public T nextRecord() {
        T t = storage.read(this.peekID());
        nextID();

        return t;
    }

    @Override
    public int numRecords() {
        return count;
    }

    public boolean hasNext() {
        return hasMore();
    }

    public T next() {
        return nextRecord();
    }

    public void remove() {
        throw new UnsupportedOperationException("Remove() is unsupported by IndexSpanningIterator objects");
    }

    public int peekID() {
        return current;
    }

    private Cursor getRawCursor() {
        throw new RuntimeException("Raw cursor unavailable for cover index iterator");
    }

    public String getPrimaryId() {
        throw new RuntimeException("Primary ID Not requested by this iterator");
    }
}
