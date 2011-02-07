/**
 * 
 */
package org.commcare.android.util;

import java.io.File;

import android.util.Log;

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

	public static boolean cleanFilePath(String fullPath, String extendedPath) {
		//There are actually a few things that can go wrong here, should be careful
		
		//No extended path, life is good.
		if(extendedPath == null) { return true;}
		
		//Something's weird, bail!
		if(!fullPath.contains(extendedPath)) { return true;}
		
		//Get the root that we should stop at
		File terminal = new File(fullPath.replace(extendedPath, ""));
		
		File walker = new File(fullPath);
		
		//technically we shouldn't ever hit the first case here, but also don't wanna get stuck by a weird equality bug. 
		while(walker != null && !terminal.equals(walker)) {
			if(walker.isDirectory()) {
				//only wipe out empty directories.
				if(walker.list().length == 0) {
					if(!walker.delete()) {
						//I don't think we actually want to fail here, it's not a showstopper.
						Log.w("cleanup", "couldn't delete directory " + walker.getAbsolutePath() + " while cleaning up file paths");
						//throw an exception/false here if we care.
					}
				}
			}
			walker = walker.getParentFile();
		}
		return true;
	}
}
