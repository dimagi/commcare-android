/**
 * 
 */
package org.commcare.android.util;

import org.commcare.android.application.CommCareApplication;

import android.content.Context;
import android.os.Bundle;

/**
 * @author ctsims
 *
 */
public class CommCarePlatformProvider {
	
	public static AndroidCommCarePlatform unpack(Bundle incoming, Context c) {
//		int[] commcareVersion = CommCareApplication._().getCommCareVersion();
//		AndroidCommCarePlatform platform = new AndroidCommCarePlatform(commcareVersion[0], commcareVersion[1], c);
//		
//		platform.unpack(incoming);
		return CommCareApplication._().getCommCarePlatform();
	}
	
	public static void pack(Bundle outgoing, AndroidCommCarePlatform platform) {
//		platform.pack(outgoing);
		//return CommCareApplication._().getCommCarePlatform();
	}
}
