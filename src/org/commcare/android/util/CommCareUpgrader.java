/**
 * 
 */
package org.commcare.android.util;

import android.content.Context;

/**
 * 
 * This class exists in order to handle all of the logic associated with upgrading from one version
 * of CommCare ODK to another. It is going to get big and annoying.
 * 
 * @author ctsims
 */
public class CommCareUpgrader {
	
	Context context;
	
	public CommCareUpgrader(Context c) {
		this.context = c;
	}
	
	public boolean doUpgrade(int from, int to) {
		if(from == 1) {
			if(upgradeOneTwo()) {
				from = 2;
			} else { return false;}
		}
		
		return from == to; 
	}
	
	public boolean upgradeOneTwo() {
		return true;
	}
}
