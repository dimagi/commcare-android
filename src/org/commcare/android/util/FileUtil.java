/**
 * 
 */
package org.commcare.android.util;

import java.io.File;

/**
 * @author ctsims
 *
 */
public class FileUtil {
	
	public static boolean deleteFile(File f) {
		if(!f.exists()) { return true; }
		if(!f.isDirectory()) {
			return f.delete();
		} else {
			for(File child : f.listFiles()) {
				if(!deleteFile(child)) {
					return false;
				}
			}
			return f.delete();
		}
	}
}
