/**
 * 
 */
package org.commcare.android.util;

import android.os.Bundle;

/**
 * @author ctsims
 *
 */
public class CommCarePlatformProvider {
	static AndroidCommCarePlatform platform;
	
	public static AndroidCommCarePlatform unpack(Bundle incoming) {
		return platform;
	}
	
	public static void pack(Bundle outgoing, AndroidCommCarePlatform platform) {
		CommCarePlatformProvider.platform = platform;
	}
}
