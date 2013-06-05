/**
 * 
 */
package org.commcare.dalvik.test;

import java.util.ArrayList;
import java.util.List;

import org.commcare.android.database.TableBuilder;

import android.util.Pair;

import junit.framework.TestCase;

/**
 * @author ctsims
 *
 */
public class TableBuilderTests extends TestCase {
	
	public TableBuilderTests(String s) {
		super(s);
	}
	int splitSize = 4;
	public void testArgumentList() {
		List<Integer> input = new ArrayList<Integer>();
		input.add(1);
		compare(TableBuilder.sqlList(input, 4), new Pair<String, String[]>( "(?)", new String[] {"1"}));
		input.add(2);
		input.add(3);
		
		compare(TableBuilder.sqlList(input, 4), new Pair<String, String[]>( "(?,?,?)", new String[] {"1", "2", "3"}));
		
		compare(TableBuilder.sqlList(input, 3), new Pair<String, String[]>( "(?,?,?)", new String[] {"1", "2", "3"}));
		
		compare(TableBuilder.sqlList(input, 2), new Pair<String, String[]>( "(?,?)", new String[] {"1", "2"}), new Pair<String, String[]>( "(?)", new String[] {"3"}));
		
		input.add(4);
		
		compare(TableBuilder.sqlList(input, 4), new Pair<String, String[]>( "(?,?,?,?)", new String[] {"1", "2", "3","4"}));
		
		compare(TableBuilder.sqlList(input, 2), new Pair<String, String[]>( "(?,?)", new String[] {"1", "2"}), new Pair<String, String[]>( "(?,?)", new String[] {"3","4"}));
		
		input.add(5);
		
		compare(TableBuilder.sqlList(input, 2), new Pair<String, String[]>( "(?,?)", new String[] {"1", "2"}), new Pair<String, String[]>( "(?,?)", new String[] {"3","4"}), new Pair<String, String[]>( "(?)", new String[] {"5"}));
	}
	private void compare(List<Pair<String, String[]>> val, Pair<String, String[]> ... pairs) {
		assertEquals(val.size(), pairs.length);
		
		for(int i = 0 ; i < pairs.length ; ++i) {
			assertEquals(val.get(i).first, pairs[i].first);
			for(int j = 0 ; j < pairs[i].second.length ; ++j) {
				assertEquals(val.get(i).second[j], pairs[i].second[j]);
			}
		}
		
	}
}
