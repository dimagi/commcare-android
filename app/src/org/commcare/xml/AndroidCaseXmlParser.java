/**
 * 
 */
package org.commcare.xml;

import java.io.File;
import java.io.IOException;

import javax.crypto.Cipher;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.util.FileUtil;
import org.commcare.cases.model.Case;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.util.PropertyUtils;
import org.kxml2.io.KXmlParser;

/**
 * @author ctsims
 *
 */
public class AndroidCaseXmlParser extends CaseXmlParser {
	Cipher attachmentCipher;
	Cipher userCipher;
	File folder;
	
	public AndroidCaseXmlParser(KXmlParser parser, IStorageUtilityIndexed storage) {
		super(parser, storage);
	}
	
	public AndroidCaseXmlParser(KXmlParser parser, IStorageUtilityIndexed storage, Cipher attachmentCipher, Cipher userCipher, File folder) {
		super(parser, storage);
		this.attachmentCipher = attachmentCipher;
		this.userCipher = userCipher;
		this.folder = folder;
	}

	public AndroidCaseXmlParser(KXmlParser parser, int[] tallies, boolean b, SqlStorage<ACase> storage) {
		super(parser, tallies, b, storage);
	}
	
	@Override
	protected String processAttachment(String src) {
		if(folder == null) { return null; } 
		File storagePath = new File(CommCareApplication._().getCurrentApp().fsPath(GlobalConstants.FILE_CC_ATTACHMENTS));
		File source = new File(folder, src);
		String dest = PropertyUtils.genUUID().replace("-", "");
		
		//add an extension
		int lastDot = source.getName().lastIndexOf(".");
		if(lastDot != -1) {
			dest += source.getName().substring(lastDot);
		}
		
		try {
			FileUtil.copyFile(source, new File(storagePath, dest), null, null);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		
		return GlobalConstants.ATTACHMENT_REF + dest;
	}


	/* (non-Javadoc)
	 * @see org.commcare.xml.CaseXmlParser#CreateCase(java.lang.String, java.lang.String)
	 */
	@Override
	protected Case CreateCase(String name, String typeId) {
		return new ACase(name, typeId);
	}
}
