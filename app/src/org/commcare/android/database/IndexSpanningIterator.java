/**
 * 
 */
package org.commcare.android.database;

import org.javarosa.core.services.storage.Persistable;

import android.database.Cursor;

/**
 * A index spanning iterator is a special kind of iterator that is used on densely packed tables
 * in order to not have to maintain a specific value for each ID.
 * 
 * If you have the ID set
 * [1, 2, 3, 5, 6, 7, 10, 11]
 * 
 * in your table, this iterator produces those id's from the return set
 * [4, 8, 9]
 * 
 * which is notable less verbose for densely packed datasets.
 * 
 * @author ctsims
 *
 */
public class IndexSpanningIterator<T extends Persistable> extends SqlStorageIterator<T> {

    Cursor mCursor;
    SqlStorage<T> storage;
    boolean isClosedByProgress = false;
    int count;
    
    int start;
    int end;
    
    int nextGap;
    
    int current;
    
    public IndexSpanningIterator(Cursor c, SqlStorage<T> storage, int minValue, int maxValue, int countValue) {
        start = minValue;
        current = start;
        end = maxValue;
        count = countValue;
        this.c = c;
        this.storage = storage;
        
        //if there's no next value, it means there are no gaps, and we can just walk to the end
        if(!c.moveToNext()) {
            nextGap = end +1;
            isClosedByProgress = true;
            c.close();
        } else {
            //Otherwise our next gap is the first cursor record
            nextGap = c.getInt(0);
        }
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.services.storage.IStorageIterator#hasMore()
     */
    public boolean hasMore() {
        //base case, we don't see the next gap yet
        if(nextGap > current) {
            return true;
        }

        //If current isn't a valid element we don't have any more
        return false;
    }
    
    private void expandToGap() {
        //If this is closed, we can't grow anymore
        if(isClosedByProgress) {
            return;
        }
        
        //otherwise we need to assume that we're at the gap, 
        //otherwise we shouldn't be growing
        if(nextGap != current) {
            return;
        }
        
        boolean currentIsValid = false;
        
        //current points at a gap now, which means that it must 
        //be invalid. Either we will get current into a valid
        //state or the iterator needs to close
        while(c.moveToNext()) {
            int upcomingGap = c.getInt(0);
            if(nextGap + 1 == upcomingGap) {
                //Adjacent gaps with no valid records, we just 
                //want to keep going
                nextGap = upcomingGap;
            } else {
                //Otherwise we know that the next record must be valid unless
                //the next gap is larger than the end
                if(upcomingGap > end) {
                    nextGap = upcomingGap;
                    break;
                }
                
                //the step after next gap isn't the upcoming gap so it
                //must be valid
                current = nextGap + 1;
                
                //Set the next gap to be the upcoming one (after current)
                nextGap = upcomingGap;
            }
        }
        
        //If we didn't end up in a valid state it means that this iterator is done,
        //so we should clean up the cursor and signal that.
        if(!currentIsValid) {
            c.close();
            this.isClosedByProgress = true;
            current = nextGap;
        }
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.services.storage.IStorageIterator#nextID()
     */
    public int nextID() {
        int ret = current;
        current++;
        //If we just hit the gap we need
        //to either grow it or we need the iterator
        //to terminate
        if(current == nextGap) {
            expandToGap();
        }
        return ret;
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.services.storage.IStorageIterator#nextRecord()
     */
    public T nextRecord() {
        T t = storage.read(this.peekID());
        nextID();
        
        return t;
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.services.storage.IStorageIterator#numRecords()
     */
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
