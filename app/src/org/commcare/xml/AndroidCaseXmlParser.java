/**
 * 
 */
package org.commcare.xml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.crypto.Cipher;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.net.HttpRequestGenerator;
import org.commcare.android.references.JavaHttpReference;
import org.commcare.android.util.AndroidStreamUtil;
import org.commcare.android.util.FileUtil;
import org.commcare.cases.model.Case;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.util.PropertyUtils;
import org.kxml2.io.KXmlParser;

import android.net.Uri;
import android.util.Pair;

/**
 * @author ctsims
 *
 */
public class AndroidCaseXmlParser extends CaseXmlParser {
	Cipher attachmentCipher;
	Cipher userCipher;
	File folder;
	boolean processAttachments = true;
	HttpRequestGenerator generator;
	
	public AndroidCaseXmlParser(KXmlParser parser, IStorageUtilityIndexed storage) {
		super(parser, storage);
	}
	
	
	//TODO: Sync the following two constructors!
	public AndroidCaseXmlParser(KXmlParser parser, IStorageUtilityIndexed storage, Cipher attachmentCipher, Cipher userCipher, File folder) {
		this(parser, storage);
		this.attachmentCipher = attachmentCipher;
		this.userCipher = userCipher;
		this.folder = folder;
		processAttachments = true;
	}

	public AndroidCaseXmlParser(KXmlParser parser, int[] tallies, boolean b, SqlStorage<ACase> storage, HttpRequestGenerator generator) {
		super(parser, tallies, b, storage);
		this.generator = generator;
	}
	
	@Override
	protected void removeAttachment(Case caseForBlock, String attachmentName) {
		if(!processAttachments) { return;}
		
		//TODO: All of this code should really be somewhere else, too, since we also need to remove attachments on
		//purge.
		String source = caseForBlock.getAttachmentSource(attachmentName);
		
		//TODO: Handle remote reference download?
		if(source == null) { return;}

		//Handle these cases better later.
		try {
			ReferenceManager._().DeriveReference(source).remove();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InvalidReferenceException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	protected String processAttachment(String src, String from, String name, KXmlParser parser) {
		if(!processAttachments) { return null;}
		
		//Parse from the local environment
		if(CaseXmlParser.ATTACHMENT_FROM_LOCAL.equals(from)) {
			if(folder == null) { return null; }
			File source = new File(folder, src);

			Pair<File, String> dest = getDestination(source.getName());
			
			try {
				FileUtil.copyFile(source, dest.first, null, null);
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
			
			return dest.second;
		} else if(CaseXmlParser.ATTACHMENT_FROM_REMOTE.equals(from)) { 
			try {
				Reference remote = ReferenceManager._().DeriveReference(src);
				
				//TODO: Awful.
				if(remote instanceof JavaHttpReference) {
					((JavaHttpReference)remote).setHttpRequestor(generator);
				}
				
				
				//TODO: Proper URL here
				Pair<File, String> dest = getDestination(src);
				
				if(dest.first.exists()) { dest.first.delete(); }
				dest.first.createNewFile();
				AndroidStreamUtil.writeFromInputToOutput(remote.getStream(), new FileOutputStream(dest.first));
				
				return dest.second;
				//TODO:  Don't Pass code review without fixing this exception handling
			} catch(Exception e) {
				e.printStackTrace();
			}
			return null;
		}
		return null;
	}
	
	private Pair<File, String> getDestination(String source) {
		File storagePath = new File(CommCareApplication._().getCurrentApp().fsPath(GlobalConstants.FILE_CC_ATTACHMENTS));
		String dest = PropertyUtils.genUUID().replace("-", "");
		
		//add an extension
		//TODO: getLastPathSegment could be null
		String fileName = Uri.parse(source).getLastPathSegment();
		if(fileName != null) {
			int lastDot = fileName.lastIndexOf(".");
			if(lastDot != -1) {
				dest += fileName.substring(lastDot);
			}
		}
		
		return new Pair<File, String>(new File(storagePath, dest), GlobalConstants.ATTACHMENT_REF + dest);
	}


	/* (non-Javadoc)
	 * @see org.commcare.xml.CaseXmlParser#CreateCase(java.lang.String, java.lang.String)
	 */
	@Override
	protected Case CreateCase(String name, String typeId) {
		return new ACase(name, typeId);
	}
}
