package org.commcare.android.util;

import java.util.Iterator;
import java.util.Set;


public class DatastructureUtil {
	
	public static Object getOne(Set set) {
		Iterator it = set.iterator();
		return it.next();
	}

}
