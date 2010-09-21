/**
 * 
 */
package org.commcare.android.util;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.TableBuilder;
import org.commcare.android.models.Case;
import org.commcare.resources.model.Resource;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

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
	
	public boolean doUpgrade(SQLiteDatabase database, int from, int to) {
		if(from == 1) {
			if(upgradeOneTwo(database)) {
				from = 2;
			} else { return false;}
		}
		
		database.close();
		
		return from == to; 
	}
	
	public boolean upgradeOneTwo(SQLiteDatabase database) {
		database.beginTransaction();
		TableBuilder builder = new TableBuilder("UPGRADE_RESOURCE_TABLE");
		builder.addData(new Resource());
		database.execSQL(builder.getTableCreateString());
		
		database.setVersion(2);
		database.setTransactionSuccessful();
		database.endTransaction();
		return true;
	}
}
