/**
 * 
 */
package org.commcare.android.references;

import java.util.HashMap;

import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceFactory;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.util.PropertyUtils;


/**
 * @author wspride
 *
 */
public class ArchiveFileRoot implements ReferenceFactory {
	
	private HashMap<String, String> guidToFolderMap = new HashMap<String, String>();
	
	private final int guidLength = 10;
	// length of "jr://archive/"
	private final int jrLength = 13;

	public ArchiveFileRoot() {
	}
	
	public Reference derive(String guidPath) throws InvalidReferenceException {
		return new ArchiveFileReference(guidToFolderMap.get(getGUID(guidPath)),getGUID(guidPath), getPath(guidPath));
	}

	public Reference derive(String URI, String context) throws InvalidReferenceException {
		if(context.lastIndexOf('/') != -1) {
			context = context.substring(0,context.lastIndexOf('/') + 1);
		}
		return ReferenceManager._().DeriveReference(context + URI);
	}

	public boolean derives(String URI) {
		return URI.toLowerCase().startsWith("jr://archive/");
	}
	
	public String addArchiveFile(String filepath){
		String mGUID = PropertyUtils.genGUID(guidLength);
		guidToFolderMap.put(mGUID, filepath);
		return mGUID;
	}
	
	public String getGUID(String jrpath){
		return jrpath.substring(jrLength, jrLength+10);
	}
	
	public String getPath(String jrpath){
		return jrpath.substring(jrLength+10);
	}
}
