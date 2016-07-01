package org.commcare.utils;

import org.javarosa.core.util.DataUtil;
import org.junit.Test;

import java.util.Vector;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class AndroidUtilTest {
    @Test
    public void intersectionTest() {
        DataUtil.IntersectionLambda intersectionLambda = new AndroidUtil.AndroidIntersectionLambda();
        Vector<String> setOne = new Vector<>();
        setOne.add("one");
        setOne.add("two");

        Vector<String> setTwo = new Vector<>();
        setTwo.add("one");
        setTwo.add("three");
        Vector<String> unionSet = intersectionLambda.intersection(setOne, setTwo);

        // for safety, we want to return a whole new vector
        assertFalse(unionSet == setOne);
        assertFalse(unionSet == setTwo);

        // for safety, don't modify ingoing vector arguments
        assertTrue(setOne.contains("one"));
        assertTrue(setOne.contains("two"));

        assertTrue(setTwo.contains("one"));
        assertTrue(setTwo.contains("three"));

        // make sure proper intersection is computed
        assertTrue(unionSet.contains("one"));

        assertFalse(unionSet.contains("two"));
        assertFalse(unionSet.contains("three"));
    }
}
