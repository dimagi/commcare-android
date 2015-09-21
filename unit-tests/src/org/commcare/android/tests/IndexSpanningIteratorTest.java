package org.commcare.android.tests;

import static junit.framework.Assert.assertEquals;

import java.util.Arrays;
import java.util.Vector;

import org.commcare.android.database.IndexSpanningIterator;
import org.commcare.android.mocks.ExtendedTestCursor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class IndexSpanningIteratorTest {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }
    
    private int[] i(int...inputs) {
        return inputs;
    }
    
    @Test
    public void testGapWalking() {
        //Empty inputs
        testSpans(i(), i(), 0, 0, 0);
        
        //Multiple Gaps
        testSpans(i(5, 7, 9), i(1, 2, 3, 4, 6, 8));
        
        //No gap
        testSpans(i(5), i(1,2,3,4));
        
        //No Gap, late start
        testSpans(i(5), i(2,3,4));
        
        //endpoint gaps
        testSpans(i(2,3,6), i(1,4,5));
        
        //No results
        testSpans(i(3,4), i(), 3, 4, 0);
        
        //N gap
        testSpans(i(3,4,5,8), i(1,2,6,7));
        
        //Multiple split gaps
        testSpans(i(5,6,8,9,11), i(2,3,4,7,10));
        
        //From the iterator documentation
        testSpans(i(4,8,9,12), i(1, 2, 3, 5, 6, 7, 10, 11));
    }
    
    public void testSpans(int[] inputs, int[] expected) {
        testSpans(inputs, expected,expected[0], inputs[inputs.length - 1], expected.length);
    }
    
    public void testSpans(int[] inputs, int[] expected, int min, int max, int count) {
        Integer[][] master = new Integer[inputs.length][1];
        for(int i = 0 ; i < inputs.length; ++i ) {
            master[i] = new Integer[] {inputs[i]};
        }
        ExtendedTestCursor c = new ExtendedTestCursor();
        c.setResults(master);
        
        Vector<Integer> vals = new Vector<Integer>();
        
        IndexSpanningIterator iterator = new IndexSpanningIterator(c, null, min, max, count);
        
        while(iterator.hasMore()) {
            vals.add(iterator.nextID());
        }
        
        assertEquals("Index Size for: " + Arrays.toString(expected) +" | "+ vals.toString(), expected.length, vals.size());
        for(int i = 0; i < expected.length; ++i) {
            assertEquals("Mismatched value: " + Arrays.toString(expected) +" | "+ vals.toString(), expected[i], (int)vals.elementAt(i));
        }

    }

}
